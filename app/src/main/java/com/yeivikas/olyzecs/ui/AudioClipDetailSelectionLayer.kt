package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val SelectionFill = Color.White.copy(alpha = 0.16f)
private val SelectionRulerFill = Color.White.copy(alpha = 0.14f)
private val SelectionEdge = Color.White.copy(alpha = 0.92f)

/**
 * Capa que pinta los rangos seleccionados ([AudioClipDetailSelectionState.ranges]): relleno claro sobre la onda (la
 * zona seleccionada se ve más clara que el resto, como en WavePad), una banda tenue sobre la REGLA (como en los
 * editores profesionales, para leer el rango contra el tiempo) y una línea en cada borde que arranca en seco en el filo
 * inferior de la regla (sin cruzar sus marcas). Se pinta ENCIMA de la regla y es translúcida: no tapa números ni
 * marcas. El cabezal va por encima de todo. Solo dibuja: no conoce los gestos ni los límites de cantidad.
 *
 * @param viewportPx ancho del eje de tiempo (sin los márgenes); @param gutterPx margen lateral.
 */
@Composable
internal fun AudioClipDetailSelectionLayer(
    state: AudioClipDetailSelectionState,
    scrollPx: Float,
    pxPerMs: Float,
    viewportPx: Float,
    gutterPx: Float,
    modifier: Modifier = Modifier
) {
    // `graphicsLayer`: capa propia, para que repintar la selección al arrastrar no obligue a re-grabar la onda ni la regla.
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer()) {
        for (selection in state.ranges) {
            drawSelection(selection, scrollPx, pxPerMs, viewportPx, gutterPx)
        }
    }
}

private fun DrawScope.drawSelection(
    selection: DetailSelection,
    scrollPx: Float,
    pxPerMs: Float,
    viewportPx: Float,
    gutterPx: Float
) {
    if (pxPerMs <= 0f || viewportPx <= 0f) return
    val top = DETAIL_RULER_HEIGHT_DP.dp.toPx()
    val height = (size.height - top).coerceAtLeast(0f)
    val startX = detailSourceMsToX(selection.startMs, scrollPx, pxPerMs)
    val endX = detailSourceMsToX(selection.endMs, scrollPx, pxPerMs)
    val left = startX.coerceIn(0f, viewportPx)
    val right = endX.coerceIn(0f, viewportPx)
    if (right > left) {
        drawRect(SelectionRulerFill, Offset(gutterPx + left, 0f), Size(right - left, top))
        drawRect(SelectionFill, Offset(gutterPx + left, top), Size(right - left, height))
    }
    // Línea de cada borde, solo si ese borde está a la vista (con zoom puede quedar fuera).
    val stroke = 2.dp.toPx()
    if (startX in 0f..viewportPx) {
        drawLine(SelectionEdge, Offset(gutterPx + startX, top), Offset(gutterPx + startX, size.height), strokeWidth = stroke)
    }
    if (endX in 0f..viewportPx) {
        drawLine(SelectionEdge, Offset(gutterPx + endX, top), Offset(gutterPx + endX, size.height), strokeWidth = stroke)
    }
}
