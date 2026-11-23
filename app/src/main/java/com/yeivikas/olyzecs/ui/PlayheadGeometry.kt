package com.yeivikas.olyzecs.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.roundToInt

/**
 * Geometría y dibujo del playhead del timeline, separados de [TimelineView] para
 * poder probarlos sin Compose y para que la regla y TODAS las filas usen
 * exactamente el mismo código de trazo (un solo lugar donde cambiar el aspecto).
 */

/**
 * Alto (px) de las filas de pista que realmente se ven: lo que queda de las filas
 * tras descontar el scroll, acotado al viewport de la lista. Es lo que hace que la
 * línea separadora termine justo al final de la última capa (o en el borde del
 * viewport si hay scroll), sin ningún tope arbitrario.
 *
 * @param viewportPx alto del viewport de la lista; `Float.MAX_VALUE` si aún no se midió.
 */
internal fun visibleRowsHeightPx(
    totalRows: Int,
    rowHeightPx: Float,
    scrollPx: Int,
    viewportPx: Float
): Float {
    if (totalRows <= 0 || rowHeightPx <= 0f) return 0f
    return (totalRows * rowHeightPx - scrollPx).coerceIn(0f, viewportPx.coerceAtLeast(0f))
}

/**
 * Posición X del centro de la línea, alineada a píxel ENTERO. Un trazo centrado en
 * una coordenada fraccionaria se reparte entre dos columnas de píxeles y se ve
 * borroso/parpadeante mientras avanza la reproducción; alineado, se ve nítido
 * (como en los editores de escritorio). Con grosor impar el centro cae en .5.
 */
internal fun snapLineCenterPx(xPx: Float, strokePx: Float): Float {
    val snapped = xPx.roundToInt().toFloat()
    return if (strokePx.roundToInt() % 2 != 0) snapped + 0.5f else snapped
}

/**
 * Borde izquierdo (px ENTEROS, relativo al inicio del carril) de la zona del playhead de ancho [hitWidthPx], centrada
 * en [laneXPx]. Lo usan TODOS los tramos de la línea (regla y filas) para que caigan en la misma columna de píxeles.
 */
internal fun playheadHitLeftPx(laneXPx: Float, hitWidthPx: Float): Int =
    (laneXPx - hitWidthPx / 2f).roundToInt()

/**
 * Borde izquierdo (px enteros, en la ventana del timeline) de la zona del playhead sobre la REGLA. El carril de cada
 * fila empieza en `LABEL_COLUMN_WIDTH.roundToPx()` (Compose redondea las medidas en dp a píxel entero), que es
 * [labelWidthPx] redondeado; sumarle el ancho SIN redondear y redondear después podía dejar la línea de la regla 1 px
 * a un lado de la del Master (un escalón visible justo bajo la regla). Con esta función ambas coinciden siempre.
 */
internal fun playheadRulerHitLeftPx(labelWidthPx: Float, laneXPx: Float, hitWidthPx: Float): Int =
    labelWidthPx.roundToInt() + playheadHitLeftPx(laneXPx, hitWidthPx)

/** Contorno oscuro bajo la línea: la separa de clips claros u oscuros por igual. */
private const val PLAYHEAD_HALO_ALPHA = 0.38f
private const val PLAYHEAD_HALO_EXTRA_PX = 2f

/**
 * Trazo único del playhead: halo translúcido + línea del color de marca.
 * @param centerX centro horizontal en coordenadas del DrawScope (se alinea a píxel).
 */
internal fun DrawScope.drawPlayheadStroke(
    centerX: Float,
    top: Float,
    bottom: Float,
    color: Color,
    strokePx: Float
) {
    val x = snapLineCenterPx(centerX, strokePx)
    drawLine(
        color = Color.Black.copy(alpha = PLAYHEAD_HALO_ALPHA),
        start = Offset(x, top),
        end = Offset(x, bottom),
        strokeWidth = strokePx + PLAYHEAD_HALO_EXTRA_PX
    )
    drawLine(color = color, start = Offset(x, top), end = Offset(x, bottom), strokeWidth = strokePx)
}
