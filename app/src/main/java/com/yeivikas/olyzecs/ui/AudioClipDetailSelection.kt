package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * Estado de la selección de rango de la ventana ampliada del audio. Es estado de SESIÓN de la ventana: no se guarda, no
 * entra al undo/redo ni toca el audio (solo SELECCIONA: con el bucle activo acota la vista previa; las acciones sobre la selección son una fase aparte).
 *
 * [range] es observable A PROPÓSITO y solo se lee dentro del bloque de dibujo de [AudioClipDetailSelectionLayer]: arrastrar
 * repinta únicamente esa capa, sin recomponer la ventana (mismo patrón que el cabezal).
 */
@Stable
internal class AudioClipDetailSelectionState {
    /** Selección actual (en ms del ARCHIVO) o `null` si no hay. */
    var range by mutableStateOf<DetailSelection?>(null)
        private set

    private var anchorMs = 0.0
    private var grabOffsetPx = 0f

    /** Lo que había antes de un toque simple: un doble toque (zoom) lo restituye, porque el primer toque ya lo borró. */
    private var beforeTap: DetailSelection? = null

    /**
     * Empieza un arrastre en [downAxisXPx] (X del eje de tiempo). Si cae sobre un borde de la selección existente, ese
     * borde se mueve (el opuesto queda fijo; se conserva el punto por el que se agarró, sin salto); si no, nace una
     * selección nueva anclada en ese punto.
     */
    fun beginDrag(downAxisXPx: Float, scrollPx: Float, pxPerMs: Float, totalMs: Double, edgeHitHalfWidthPx: Float) {
        beforeTap = null
        val current = range
        val edge = current?.let { detailSelectionEdgeHit(downAxisXPx, it, scrollPx, pxPerMs, edgeHitHalfWidthPx) }
        if (current != null && edge != null) {
            anchorMs = detailSelectionOppositeMs(current, edge)
            grabOffsetPx = detailSourceMsToX(detailSelectionEdgeMs(current, edge), scrollPx, pxPerMs) - downAxisXPx
        } else {
            anchorMs = detailXToSourceMs(downAxisXPx, scrollPx, pxPerMs, totalMs)
            grabOffsetPx = 0f
        }
    }

    /** El dedo está ahora en [axisXPx]: la selección va del ancla al dedo. */
    fun drag(axisXPx: Float, scrollPx: Float, pxPerMs: Float, totalMs: Double) {
        val currentMs = detailXToSourceMs(axisXPx + grabOffsetPx, scrollPx, pxPerMs, totalMs)
        range = detailSelectionFromDrag(anchorMs, currentMs, totalMs)
    }

    /**
     * El audio se invirtió (REV): la selección sigue sobre el MISMO fragmento de señal, que ahora está reflejado
     * (ver [mirrorDetailSelection]). Sin selección no hace nada.
     */
    fun mirror(totalMs: Double) {
        val current = range ?: return
        range = mirrorDetailSelection(current, totalMs)
        beforeTap = null
    }

    /** Toque simple sobre la onda: deselecciona (como tocar en WavePad, que solo sitúa la posición). */
    fun clearForTap() {
        beforeTap = range
        range = null
    }

    /** Doble toque: era el zoom, no una deselección; devuelve lo que borró su primer toque. */
    fun restoreAfterDoubleTap() {
        beforeTap?.let { range = it }
        beforeTap = null
    }
}

/**
 * Capa que pinta la selección: relleno claro sobre la onda (la zona seleccionada se ve más clara que el resto, como en
 * WavePad), una banda tenue sobre la REGLA (como en los editores profesionales, para leer el rango contra el tiempo) y
 * una línea en cada borde que arranca en seco en el filo inferior de la regla (sin cruzar sus marcas). Se pinta ENCIMA de la regla y es translúcida:
 * no tapa números ni marcas. El cabezal va por encima de todo.
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
        drawSelection(state.range, scrollPx, pxPerMs, viewportPx, gutterPx)
    }
}

private fun DrawScope.drawSelection(
    selection: DetailSelection?,
    scrollPx: Float,
    pxPerMs: Float,
    viewportPx: Float,
    gutterPx: Float
) {
    if (selection == null || pxPerMs <= 0f || viewportPx <= 0f) return
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
