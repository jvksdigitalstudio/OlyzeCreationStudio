package com.yeivikas.olyzecs.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.audio.clipDropStartMs

// ============================================================
// Geometría del arrastre de un clip de audio entre carriles.
//
// Reglas puras (sin estado ni composición): traducen la posición del dedo, en
// coordenadas de ventana, al carril de audio y al instante del timeline donde
// quedaría el clip. La vista previa del arrastre y el movimiento que se aplica
// al soltar salen de la MISMA función, así que lo que se ve es lo que se aplica.
// ============================================================

/**
 * Destino de un clip arrastrado: el carril [trackId], el instante [startMs] donde
 * quedaría su borde izquierdo y [laneBounds], los límites de ese carril en
 * coordenadas de ventana (donde se dibuja la vista previa).
 */
internal data class AudioClipDropTarget(
    val trackId: String,
    val startMs: Long,
    val laneBounds: Rect
)

/**
 * Destino del clip [clipId] (de [clipLengthMs]) agarrado a [grabOffsetPx] de su borde
 * izquierdo cuando el dedo está en [fingerWindow]; `null` si el dedo no está sobre
 * ningún carril de AUDIO (p. ej. sobre una capa de imagen o fuera de la lista) o
 * todavía no hay escala ([pxPerMs] <= 0).
 *
 * Solo importa el eje Y para elegir el carril; en X el dedo puede pasarse de los
 * bordes (el inicio se acota al proyecto). El inicio resultante lo imanta y acota
 * [clipDropStartMs] con las reglas del carril destino.
 *
 * @param laneBoundsOf límites en ventana del carril de cada pista (`null` si aún no se midió).
 */
internal fun resolveClipDropTarget(
    tracks: List<AudioTrack>,
    laneBoundsOf: (trackId: String) -> Rect?,
    clipId: String,
    clipLengthMs: Long,
    fingerWindow: Offset,
    grabOffsetPx: Float,
    pxPerMs: Float,
    projectDurationMs: Long,
    playheadMs: Long,
    snapThresholdMs: Long
): AudioClipDropTarget? {
    val lanes = tracks.map { laneBoundsOf(it.id) }
    val index = addClipLaneIndexAtY(
        lanes.map { bounds -> bounds?.let { AddClipLaneBounds(it.left, it.top, it.right, it.bottom) } },
        fingerWindow.y
    ) ?: return null
    val lane = lanes[index] ?: return null
    val rawStartMs = laneTimeMsAt(fingerWindow.x - grabOffsetPx - lane.left, pxPerMs, projectDurationMs) ?: return null
    val startMs = tracks[index].clipDropStartMs(
        clipId = clipId,
        clipLengthMs = clipLengthMs,
        rawStartMs = rawStartMs,
        projectDurationMs = projectDurationMs,
        playheadMs = playheadMs,
        snapThresholdMs = snapThresholdMs
    )
    return AudioClipDropTarget(tracks[index].id, startMs, lane)
}

/**
 * Desplazamiento vertical (px, con signo) que la lista debe hacer en este cuadro
 * para que el dedo, a [pointerY], pueda llegar a carriles fuera de pantalla mientras
 * arrastra un clip: `0` en el interior de la zona visible
 * (`viewportTopPx`..`viewportBottomPx`), y, dentro de [edgePx] de un borde, crece
 * linealmente con la penetración hasta [maxStepPx] (negativo = hacia arriba).
 */
internal fun edgeAutoScrollDeltaPx(
    pointerY: Float,
    viewportTopPx: Float,
    viewportBottomPx: Float,
    edgePx: Float,
    maxStepPx: Float
): Float {
    if (edgePx <= 0f || viewportBottomPx <= viewportTopPx) return 0f
    val intoTop = (viewportTopPx + edgePx - pointerY) / edgePx
    val intoBottom = (pointerY - (viewportBottomPx - edgePx)) / edgePx
    return when {
        intoTop > 0f -> -maxStepPx * intoTop.coerceAtMost(1f)
        intoBottom > 0f -> maxStepPx * intoBottom.coerceAtMost(1f)
        else -> 0f
    }
}
