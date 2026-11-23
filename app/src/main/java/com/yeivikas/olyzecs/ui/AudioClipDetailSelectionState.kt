package com.yeivikas.olyzecs.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Estado de la selección de rangos de la ventana ampliada del audio. Es estado de SESIÓN de la ventana: no se guarda,
 * no entra al undo/redo ni toca el audio (solo SELECCIONA: con el bucle activo acota la vista previa; las acciones
 * sobre la selección son una fase aparte).
 *
 * Una selección es un CONJUNTO de rangos del archivo (ver `AudioClipDetailSelectionSetMath.kt`): hasta [maxCount]
 * (botón "Seleccionar", x1 por defecto), ordenados y sin solaparse. Este estado solo CONSERVA los rangos y la cantidad
 * máxima, y orquesta el gesto; las reglas (hueco libre, bordes, espejo, límite) son funciones puras aparte, y
 * pintarlos es cosa de [AudioClipDetailSelectionLayer].
 *
 * [ranges] es observable A PROPÓSITO y solo se lee dentro de bloques de dibujo y de `derivedStateOf`: arrastrar
 * repinta únicamente la capa de selección, sin recomponer la ventana (mismo patrón que el cabezal).
 */
@Stable
internal class AudioClipDetailSelectionState {
    /** Rangos seleccionados (ms del ARCHIVO), ordenados por inicio y sin solaparse; vacío = sin selección. */
    var ranges by mutableStateOf<List<DetailSelection>>(emptyList())
        private set

    /** Cuántos rangos se pueden seleccionar a la vez en este clip (x1 … x7). */
    var maxCount by mutableIntStateOf(DETAIL_SELECT_DEFAULT_COUNT)
        private set

    // Gesto en curso: el rango que se arrastra (uno nuevo o un borde de uno existente) vive en `dragSpan`, entre los
    // demás rangos (`dragOthers`), que no se tocan durante el arrastre.
    private var dragOthers: List<DetailSelection> = emptyList()
    private var dragSpan: DetailFreeSpan? = null
    private var anchorMs = 0.0
    private var grabOffsetPx = 0f

    /** Lo que había antes de un toque simple: un doble toque (zoom) lo restituye, porque el primer toque ya lo borró. */
    private var beforeTap: List<DetailSelection> = emptyList()

    /**
     * Empieza un arrastre en [downAxisXPx] (X del eje de tiempo). Si cae sobre un borde de CUALQUIER rango existente,
     * ese borde se mueve (el opuesto queda fijo; se conserva el punto por el que se agarró, sin salto) aunque ya se
     * haya llegado a [maxCount]; si no, nace un rango nuevo anclado en ese punto, pero SOLO si queda cupo y el punto
     * cae en un hueco libre (no dentro de otro rango). Si no puede empezar nada, el arrastre no hace nada.
     */
    fun beginDrag(downAxisXPx: Float, scrollPx: Float, pxPerMs: Float, totalMs: Double, edgeHitHalfWidthPx: Float) {
        beforeTap = emptyList()
        val grab = detailSelectionEdgeHitIn(downAxisXPx, ranges, scrollPx, pxPerMs, edgeHitHalfWidthPx)
        if (grab != null) {
            val grabbed = ranges[grab.index]
            dragOthers = ranges.filterIndexed { index, _ -> index != grab.index }
            anchorMs = detailSelectionOppositeMs(grabbed, grab.edge)
            grabOffsetPx = detailSourceMsToX(detailSelectionEdgeMs(grabbed, grab.edge), scrollPx, pxPerMs) - downAxisXPx
        } else {
            dragOthers = ranges
            anchorMs = detailXToSourceMs(downAxisXPx, scrollPx, pxPerMs, totalMs)
            grabOffsetPx = 0f
        }
        val canStart = grab != null || ranges.size < maxCount
        dragSpan = if (canStart) detailFreeSpanAt(dragOthers, anchorMs, totalMs) else null
    }

    /** El dedo está ahora en [axisXPx]: el rango arrastrado va del ancla al dedo, sin salir de su hueco libre. */
    fun drag(axisXPx: Float, scrollPx: Float, pxPerMs: Float, totalMs: Double) {
        val span = dragSpan ?: return
        val currentMs = detailXToSourceMs(axisXPx + grabOffsetPx, scrollPx, pxPerMs, totalMs)
        ranges = detailSelectionsWith(dragOthers, detailSelectionFromDragIn(span, anchorMs, currentMs))
    }

    /**
     * Cambia cuántos rangos se pueden seleccionar (acotado a x1 … x7). Si baja por debajo de los que ya hay, se
     * conservan los primeros de izquierda a derecha (ver [limitDetailSelections]).
     */
    fun changeMaxCount(count: Int) {
        maxCount = count.coerceIn(DETAIL_SELECT_MIN_COUNT, DETAIL_SELECT_MAX_COUNT)
        ranges = limitDetailSelections(ranges, maxCount)
        beforeTap = emptyList()
    }

    /**
     * El audio se invirtió (REV): los rangos siguen sobre los MISMOS fragmentos de señal, que ahora están reflejados
     * (ver [mirrorDetailSelections]). Sin selección no hace nada.
     */
    fun mirror(totalMs: Double) {
        if (ranges.isEmpty()) return
        ranges = mirrorDetailSelections(ranges, totalMs)
        beforeTap = emptyList()
    }

    /** Descarta la selección sin dejar nada que un doble toque pueda restituir (tras aplicarle una acción, p. ej. Recorte). */
    fun clear() {
        ranges = emptyList()
        beforeTap = emptyList()
    }

    /** Toque simple sobre la onda: deselecciona todos los rangos (como tocar en WavePad, que solo sitúa la posición). */
    fun clearForTap() {
        beforeTap = ranges
        ranges = emptyList()
    }

    /** Doble toque: era el zoom, no una deselección; devuelve lo que borró su primer toque. */
    fun restoreAfterDoubleTap() {
        if (beforeTap.isNotEmpty()) ranges = beforeTap
        beforeTap = emptyList()
    }
}
