package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** Distancia máxima entre los dos toques de un doble toque (el dedo no vuelve al píxel exacto). */
private val DOUBLE_TAP_POSITION_SLOP = 48.dp

private const val NO_PREVIOUS_TAP = -1L

/**
 * Toque, doble toque y mantener presionado, con el toque simple ENTREGADO AL
 * INSTANTE.
 *
 * `detectTapGestures` con `onDoubleTap` no puede avisar un toque simple hasta
 * que vence la ventana del doble toque (~300 ms), porque todavía podría venir
 * el segundo: se siente lento. Acá el toque simple se reporta apenas se suelta
 * el dedo; si luego llega un segundo toque dentro de la ventana, se reporta
 * [onDoubleTap] — en ese caso el efecto del primer toque ya se mostró y quien
 * llama debe deshacerlo (mismo modelo de las apps de edición de audio/video).
 *
 * Un toque que otro componente consume (un gesto de scroll, un hijo) no cuenta.
 * Mantener presionado ([onLongPress]) no genera toque ni cuenta como primer toque.
 */
suspend fun PointerInputScope.detectImmediateTapGestures(
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit
) {
    val slopPx = DOUBLE_TAP_POSITION_SLOP.toPx()
    var previousTapUpMs = NO_PREVIOUS_TAP
    var previousTapPosition = Offset.Zero

    awaitEachGesture {
        val down = awaitFirstDown()
        val pairsWithPreviousTap = isDoubleTap(
            previousUpMs = previousTapUpMs,
            previousX = previousTapPosition.x,
            previousY = previousTapPosition.y,
            downMs = down.uptimeMillis,
            downX = down.position.x,
            downY = down.position.y,
            minIntervalMs = viewConfiguration.doubleTapMinTimeMillis,
            timeoutMs = viewConfiguration.doubleTapTimeoutMillis,
            slopPx = slopPx
        )
        val up = try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
        } catch (_: PointerEventTimeoutCancellationException) {
            previousTapUpMs = NO_PREVIOUS_TAP
            onLongPress(down.position)
            // El soltado de un toque largo no debe leerse como un toque.
            waitForUpOrCancellation()?.consume()
            return@awaitEachGesture
        }
        if (up == null) {
            // Cancelado: otro componente tomó el gesto (p. ej. el scroll horizontal del timeline).
            previousTapUpMs = NO_PREVIOUS_TAP
            return@awaitEachGesture
        }
        up.consume()
        if (pairsWithPreviousTap) {
            previousTapUpMs = NO_PREVIOUS_TAP
            onDoubleTap(up.position)
        } else {
            previousTapUpMs = up.uptimeMillis
            previousTapPosition = up.position
            onTap(up.position)
        }
    }
}

/**
 * `true` si un toque que baja en ([downX], [downY]) a [downMs] completa un doble
 * toque con el anterior, soltado en ([previousX], [previousY]) a [previousUpMs]:
 * tras [minIntervalMs] (descarta el rebote del dedo) y dentro de [timeoutMs],
 * a no más de [slopPx] de distancia. [previousUpMs] negativo = no hubo toque
 * previo. Pura, para JUnit.
 */
internal fun isDoubleTap(
    previousUpMs: Long,
    previousX: Float,
    previousY: Float,
    downMs: Long,
    downX: Float,
    downY: Float,
    minIntervalMs: Long,
    timeoutMs: Long,
    slopPx: Float
): Boolean {
    if (previousUpMs < 0L) return false
    val intervalMs = downMs - previousUpMs
    if (intervalMs < minIntervalMs || intervalMs > timeoutMs) return false
    return hypot(downX - previousX, downY - previousY) <= slopPx
}
