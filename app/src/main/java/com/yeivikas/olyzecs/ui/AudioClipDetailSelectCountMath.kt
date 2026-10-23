package com.yeivikas.olyzecs.ui

/**
 * Matemática pura de la CANTIDAD de rangos que se pueden seleccionar en un clip (el botón "Seleccionar" de la cabecera
 * de la ventana ampliada del audio: x1 … x7). Sin Compose ni Android, para cubrirla con JUnit. El control que la usa es
 * `DetailSelectButton`; quién aplica el límite al conjunto de rangos es [AudioClipDetailSelectionState].
 */

/** Menos de un rango no tiene sentido: x1 es el mínimo. */
internal const val DETAIL_SELECT_MIN_COUNT = 1

/** Cantidad con la que nace la ventana de cada clip: un solo rango. */
internal const val DETAIL_SELECT_DEFAULT_COUNT = 1

/** Máximo de rangos por clip por ahora (x7). */
internal const val DETAIL_SELECT_MAX_COUNT = 7

/** Arrastre vertical (dp) sobre el número que equivale a cambiar la cantidad en una unidad. */
internal const val DETAIL_SELECT_DRAG_STEP_DP = 14

/** [count] más [delta] (las flechas suman o restan 1), acotado a `x1 … x7`. */
internal fun stepDetailSelectCount(count: Int, delta: Int): Int =
    (count + delta).coerceIn(DETAIL_SELECT_MIN_COUNT, DETAIL_SELECT_MAX_COUNT)

/**
 * Cantidad tras arrastrar el número [dragUpPx] píxeles HACIA ARRIBA (negativo = hacia abajo) desde que el dedo se apoyó,
 * con [startCount] la cantidad en ese instante y [stepPx] los píxeles por unidad. Arriba suma y abajo resta; se mide
 * siempre contra el punto de partida (no se acumula), así volver con el dedo al sitio de origen devuelve la cantidad
 * original. Las fracciones de paso no cuentan (se trunca hacia cero). Acotada a `x1 … x7`.
 */
internal fun detailSelectCountFromDrag(startCount: Int, dragUpPx: Float, stepPx: Float): Int {
    val steps = if (stepPx > 0f) (dragUpPx / stepPx).toInt() else 0
    return stepDetailSelectCount(startCount, steps)
}

/** Texto del número del botón: `x1`, `x2`, … */
internal fun detailSelectCountLabel(count: Int): String = "x$count"
