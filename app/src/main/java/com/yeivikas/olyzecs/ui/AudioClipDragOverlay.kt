package com.yeivikas.olyzecs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.audio.track
import com.yeivikas.olyzecs.ui.theme.audioSignalColor
import com.yeivikas.olyzecs.ui.theme.effectiveAudioBrush
import com.yeivikas.olyzecs.ui.theme.effectiveAudioColorStrong
import kotlin.math.roundToInt

/**
 * Vista previa del clip que se arrastra entre carriles de audio: el clip dibujado
 * ([AudioClipSurface], el mismo diseño que dentro del carril) EXACTAMENTE donde
 * quedaría al soltar — carril destino e instante ya imantados ([AudioClipDragState.target]).
 *
 * Vive en una capa sobre la lista y no dentro de una fila para que el clip pueda cruzar
 * de un carril a otro y dibujarse por encima de las filas vecinas. Toma los colores del
 * carril DESTINO, como en FL Studio Mobile, donde el clip adopta el color de la pista
 * donde cae.
 *
 * Solo se recompone al empezar/terminar el arrastre y al cambiar de carril. Moverse es una
 * TRASLACIÓN de capa gráfica (fase de dibujo): no hay re-layout ni se vuelve a dibujar la onda
 * en cada movimiento del dedo; solo el tamaño (carril destino distinto) pasa por layout.
 *
 * @param tracks carriles de audio del proyecto (de ahí salen los colores del destino).
 * @param pxPerMs escala del timeline (px por ms).
 * @param overlayOrigin origen en ventana de esta capa (se lee en layout, no al componer).
 */
@Composable
internal fun AudioClipDragOverlay(
    state: AudioClipDragState,
    tracks: List<AudioTrack>,
    pxPerMs: Float,
    overlayOrigin: () -> Offset
) {
    val session = state.session ?: return
    val targetTrackId by remember(state) { derivedStateOf { state.target?.trackId } }
    // Tamaño del carril destino: solo cambia al cambiar de carril o con su recorte por el scroll, no al mover el dedo.
    val laneSize by remember(state) { derivedStateOf { state.target?.laneBounds?.size } }
    val targetTrack = targetTrackId?.let(tracks::track) ?: return
    val clip = session.clip
    AudioClipSurface(
        clip = clip,
        waveform = session.waveform,
        lengthMs = clip.clipLengthMs,
        trimStartMs = clip.trimStartMs,
        pxPerMs = pxPerMs,
        trackBrush = effectiveAudioBrush(targetTrack, AUDIO_TRACK_COLOR),
        waveformSignalColor = audioSignalColor(effectiveAudioColorStrong(targetTrack, AUDIO_TRACK_COLOR)),
        selected = true,
        lifted = true,
        modifier = Modifier
            .layout { measurable, _ ->
                val lane = laneSize
                if (lane == null) {
                    layout(0, 0) {}
                } else {
                    val width = (clip.clipLengthMs * pxPerMs).coerceAtMost(lane.width).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(width, lane.height.roundToInt()))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
            }
            .graphicsLayer {
                val target = state.target
                if (target == null) {
                    alpha = 0f
                } else {
                    val origin = overlayOrigin()
                    translationX = (target.laneBounds.left + target.startMs * pxPerMs - origin.x).roundToInt().toFloat()
                    translationY = (target.laneBounds.top - origin.y).roundToInt().toFloat()
                }
            }
    )
}
