package com.yeivikas.olyzecs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.ui.theme.effectiveAudioColorStrong
import kotlinx.coroutines.delay

/** Cuánto tiempo (ms) se traga un "atrás" residual tras cerrar la ventana con "atrás". */
private const val BACK_GUARD_MS = 800L

/**
 * Estado de la ventana ampliada de un clip de audio: cuál clip está abierto
 * (`null` = cerrada) y la guardia de "atrás". Vive en un objeto aparte (y no en
 * variables sueltas de `TimelineView`/`EditorScreen`) porque esas funciones son tan
 * grandes que cada variable local nueva las acerca al límite de registros que
 * acepta el verificador de Android (VerifyError).
 */
@Stable
internal class AudioDetailController {
    var openClipId: String? by mutableStateOf(null)
        private set

    /** `true` durante [BACK_GUARD_MS] tras cerrar con "atrás": se traga un segundo "atrás" casi simultáneo. */
    var backGuard: Boolean by mutableStateOf(false)
        private set

    fun open(clipId: String) {
        openClipId = clipId
    }

    fun close() {
        openClipId = null
    }

    /** Cierra por "atrás" y arma la guardia. */
    fun closeFromBack() {
        openClipId = null
        backGuard = true
    }

    fun endBackGuard() {
        backGuard = false
    }
}

/**
 * Capa que muestra [AudioClipDetailPanel] sobre el área de capas del timeline
 * cuando [controller] tiene un clip abierto, y se ocupa de:
 *  - cerrarla sola si el clip deja de existir (borrado, cortado, alta deshecha);
 *  - la guardia de "atrás": con un pellizco con los dos pulgares en los bordes Android puede
 *    disparar DOS gestos "atrás" casi a la vez; el primero cierra la ventana y, sin guardia,
 *    el segundo cerraba el editor. Mientras la guardia está activa se registra un
 *    `BackHandler` fresco (el último registrado tiene prioridad) que no hace nada.
 */
@Composable
internal fun AudioClipDetailHost(
    controller: AudioDetailController,
    audioTracks: List<AudioTrack>,
    playheadMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val openId = controller.openClipId
    val track = openId?.let { id -> audioTracks.firstOrNull { it.clip(id) != null } }
    val clip = openId?.let { id -> track?.clip(id) }

    LaunchedEffect(openId, audioTracks) {
        if (openId != null && clip == null) controller.close()
    }

    if (controller.backGuard) {
        BackHandler(enabled = true) { /* se traga el "atrás" residual */ }
        LaunchedEffect(Unit) {
            delay(BACK_GUARD_MS)
            controller.endBackGuard()
        }
    }

    if (track != null && clip != null) {
        val signalColor = remember(
            track.customColorArgb, track.useGradientColor,
            track.customGradientStartArgb, track.customGradientEndArgb
        ) {
            lerp(effectiveAudioColorStrong(track, Color(0xFF26A69A)), Color.White, 0.55f)
        }
        AudioClipDetailPanel(
            clip = clip,
            signalColor = signalColor,
            playheadMs = playheadMs,
            onSeek = onSeek,
            onClose = controller::close,
            onBackClose = controller::closeFromBack,
            modifier = modifier
        )
    }
}
