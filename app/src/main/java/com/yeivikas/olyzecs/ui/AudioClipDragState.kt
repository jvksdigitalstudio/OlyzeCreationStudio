package com.yeivikas.olyzecs.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.AudioWaveform

/**
 * Lo que un carril de audio necesita saber del arrastre de sus clips entre carriles.
 *
 * El carril solo DETECTA el gesto sobre la cabecera de un clip y lo reporta en
 * coordenadas de ventana; no conoce a los demás carriles. Quien tiene la vista
 * completa de la playlist ([AudioClipDragState], propiedad del timeline) decide a
 * qué carril y a qué instante corresponde, dibuja la vista previa y confirma el
 * movimiento — mismo reparto que el reordenamiento vertical de filas.
 */
internal interface AudioClipDragHost {
    /**
     * `true` si [clipId] es el clip que se está arrastrando Y la vista previa puede dibujarlo (hay destino): su bloque
     * original se oculta solo entonces, así el clip nunca desaparece sin su réplica en el overlay.
     */
    fun isDragSource(clipId: String): Boolean

    /**
     * El dedo agarró [clip] a [grabOffsetPx] de su borde izquierdo, en [fingerWindow].
     * [waveform] es la forma de onda ya decodificada del clip (`null` si aún no está) para la vista previa.
     */
    fun onDragStart(clip: AudioClip, waveform: AudioWaveform?, grabOffsetPx: Float, fingerWindow: Offset)

    /** El dedo que arrastra [clipId] se movió a [fingerWindow]. Se ignora si [clipId] no es el clip en arrastre. */
    fun onDrag(clipId: String, fingerWindow: Offset)

    /** El dedo que arrastraba [clipId] se soltó: se confirma el movimiento al destino vigente. Se ignora si no es el clip en arrastre. */
    fun onDragEnd(clipId: String)

    /** El gesto de [clipId] se abortó (o su bloque dejó de existir): el clip se queda donde estaba. Se ignora si no es el clip en arrastre. */
    fun onDragCancel(clipId: String)
}

/** Clip en arrastre: su estado al agarrarlo, su forma de onda y dónde lo agarró el dedo. */
internal class AudioClipDragSession(
    val clip: AudioClip,
    val waveform: AudioWaveform?,
    /** Distancia (px) del dedo al borde izquierdo del clip: el clip se mueve con el dedo SIN saltar. */
    val grabOffsetPx: Float
)

/**
 * Estado del arrastre de un clip entre carriles de audio: el clip agarrado, la
 * posición del dedo y el destino vigente. Es de UI pura y vive en el timeline; no
 * decide ni aplica nada por sí mismo:
 *
 *  - [resolveTarget] traduce la posición del dedo a un destino ([resolveClipDropTarget]).
 *  - [commit] confirma el movimiento al soltar (un solo evento por gesto, no uno por píxel).
 *
 * Mientras el dedo no esté sobre ningún carril de audio se conserva el último destino
 * válido, así soltar sobre un hueco o una capa de imagen nunca pierde el clip.
 *
 * Sus propiedades se leen en las fases de layout y dibujo del overlay
 * ([AudioClipDragOverlay]): moverse no recompone el timeline.
 */
@Stable
internal class AudioClipDragState(
    private val resolveTarget: (session: AudioClipDragSession, fingerWindow: Offset) -> AudioClipDropTarget?,
    private val commit: (clipId: String, target: AudioClipDropTarget) -> Unit
) : AudioClipDragHost {

    var session by mutableStateOf<AudioClipDragSession?>(null)
        private set

    var fingerWindow by mutableStateOf(Offset.Zero)
        private set

    var target by mutableStateOf<AudioClipDropTarget?>(null)
        private set

    /**
     * `true` entre soltar el dedo y recibir la lista de carriles ya movida: el overlay sigue dibujando el clip en su
     * destino y el original sigue oculto, así nunca hay un cuadro con el clip en su sitio viejo (destello).
     * [finishSettling] la cierra; si el bloque origen deja de existir antes, lo hace [onDragCancel].
     */
    var settling by mutableStateOf(false)
        private set

    /** Hay un arrastre con el dedo puesto (no cuenta la fase de asentado tras soltar). */
    val isActive: Boolean get() = session != null && !settling

    override fun isDragSource(clipId: String): Boolean = session?.clip?.id == clipId && target != null

    /**
     * Un solo clip a la vez (la selección es de un clip): si ya hay un arrastre en curso, un segundo dedo sobre otra
     * cabecera no lo pisa. Sus eventos posteriores se descartan por no coincidir el id del clip.
     */
    override fun onDragStart(clip: AudioClip, waveform: AudioWaveform?, grabOffsetPx: Float, fingerWindow: Offset) {
        if (settling) clear()
        if (session != null) return
        val started = AudioClipDragSession(clip, waveform, grabOffsetPx)
        this.fingerWindow = fingerWindow
        target = resolveTarget(started, fingerWindow)
        session = started
    }

    override fun onDrag(clipId: String, fingerWindow: Offset) {
        if (settling || session?.clip?.id != clipId) return
        this.fingerWindow = fingerWindow
        refreshTarget()
    }

    /** Recalcula el destino con el dedo donde está (la lista pudo desplazarse bajo él). */
    fun refreshTarget() {
        val current = session ?: return
        resolveTarget(current, fingerWindow)?.let { target = it }
    }

    override fun onDragEnd(clipId: String) {
        val current = session?.takeIf { it.clip.id == clipId && !settling } ?: return
        val destination = target
        if (destination == null) {
            clear()
            return
        }
        // El clip sigue dibujado en su destino hasta que la lista nueva llegue a la pantalla ([finishSettling]).
        settling = true
        commit(current.clip.id, destination)
    }

    /** Cierra la fase de asentado: la lista ya refleja el movimiento y el clip real ocupa el lugar del overlay. */
    fun finishSettling() {
        if (settling) clear()
    }

    override fun onDragCancel(clipId: String) {
        if (session?.clip?.id == clipId) clear()
    }

    private fun clear() {
        settling = false
        session = null
        target = null
    }
}
