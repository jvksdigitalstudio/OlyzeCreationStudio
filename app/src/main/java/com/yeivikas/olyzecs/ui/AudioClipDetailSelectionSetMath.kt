package com.yeivikas.olyzecs.ui

/**
 * Matemática pura del CONJUNTO de rangos seleccionados de la ventana ampliada del audio. Sin Compose ni Android, para
 * cubrirla con JUnit. Las reglas de UN rango (arrastre, borde, reflejo) viven en `AudioClipDetailSelectionMath.kt`; la
 * cantidad máxima de rangos, en `AudioClipDetailSelectCountMath.kt`; el estado que las une, en
 * [AudioClipDetailSelectionState].
 *
 * Un conjunto es una `List<DetailSelection>` ORDENADA por inicio y SIN SOLAPARSE (`fin(i) <= inicio(i + 1)`). Todas
 * las funciones de este archivo reciben y devuelven conjuntos con esa forma.
 */

/** Un borde concreto agarrado: el de la selección número [index] del conjunto. */
internal data class DetailEdgeGrab(val index: Int, val edge: DetailSelectionEdge)

/**
 * Hueco libre del archivo que contiene [pointMs] dado el conjunto [selections]: va del fin del rango anterior (o del
 * inicio del archivo) al inicio del siguiente (o al final del archivo). `null` si el punto cae DENTRO de un rango
 * (ahí no se puede empezar otro) o la duración no es válida.
 *
 * Sirve igual para empezar un rango nuevo (el ancla es el punto del dedo) que para ajustar un borde (el ancla es el
 * borde opuesto, y [selections] ya NO incluye el rango que se ajusta): en ambos casos el hueco acota el arrastre.
 */
internal fun detailFreeSpanAt(selections: List<DetailSelection>, pointMs: Double, totalMs: Double): DetailFreeSpan? {
    if (totalMs <= 0.0) return null
    val point = pointMs.coerceIn(0.0, totalMs)
    if (selections.any { point > it.startMs && point < it.endMs }) return null
    val before = selections.filter { it.endMs <= point }.maxOfOrNull { it.endMs } ?: 0.0
    val after = selections.filter { it.startMs >= point }.minOfOrNull { it.startMs } ?: totalMs
    return DetailFreeSpan(before, after)
}

/**
 * Borde, de CUALQUIER rango de [selections], que agarra un toque en [downAxisXPx]: el más cercano a ≤ [halfWidthPx]
 * (ver [detailSelectionEdgeHit]); `null` si el toque cae lejos de todos (entonces empieza un rango nuevo).
 */
internal fun detailSelectionEdgeHitIn(
    downAxisXPx: Float,
    selections: List<DetailSelection>,
    scrollPx: Float,
    pxPerMs: Float,
    halfWidthPx: Float
): DetailEdgeGrab? {
    var best: DetailEdgeGrab? = null
    var bestDistancePx = Float.MAX_VALUE
    for ((index, selection) in selections.withIndex()) {
        val edge = detailSelectionEdgeHit(downAxisXPx, selection, scrollPx, pxPerMs, halfWidthPx) ?: continue
        val distancePx = detailSelectionEdgeDistancePx(downAxisXPx, selection, edge, scrollPx, pxPerMs)
        if (distancePx < bestDistancePx) {
            best = DetailEdgeGrab(index, edge)
            bestDistancePx = distancePx
        }
    }
    return best
}

/** [others] más [dragged] (si hay), ordenado por inicio: el conjunto resultante de un arrastre en curso. */
internal fun detailSelectionsWith(others: List<DetailSelection>, dragged: DetailSelection?): List<DetailSelection> =
    if (dragged == null) others else (others + dragged).sortedBy { it.startMs }

/**
 * Conjunto que, tras invertir el audio (REV), abarca los MISMOS fragmentos de señal: cada rango se refleja con
 * [mirrorDetailSelection] y se reordena (el reflejo invierte el orden). Es su propia inversa.
 */
internal fun mirrorDetailSelections(selections: List<DetailSelection>, totalMs: Double): List<DetailSelection> =
    selections.mapNotNull { mirrorDetailSelection(it, totalMs) }.sortedBy { it.startMs }

/**
 * Conjunto acotado a [maxCount] rangos al BAJAR la cantidad permitida: se conservan los primeros de izquierda a
 * derecha (el mismo orden en que suenan) y se descartan los sobrantes de la derecha.
 */
internal fun limitDetailSelections(selections: List<DetailSelection>, maxCount: Int): List<DetailSelection> =
    selections.take(maxCount)

/**
 * Rangos sobre los que actúa Recorte: los seleccionados cuya parte que SUENA en el clip abierto (su intersección con
 * `[audibleStartMs, audibleEndMs]`) dura al menos [minLengthMs] (un recorte no puede dejar un clip más corto que el
 * mínimo de un clip), en el mismo orden del conjunto. Con un rango el clip se acorta a él; con varios se reparte en un
 * clip por rango. Lista vacía si no hay nada que recortar (sin selección, todos demasiado cortos o sobre una parte que
 * este clip no usa, p. ej. la de otro trozo del mismo archivo): entonces el botón Recorte se ve apagado.
 *
 * Es la misma medida que aplica `trimClipToSelections`, así que el botón nunca se habilita para un recorte que luego no hace nada.
 */
internal fun detailTrimSelections(
    selections: List<DetailSelection>,
    minLengthMs: Double,
    audibleStartMs: Double,
    audibleEndMs: Double
): List<DetailSelection> =
    selections.filter { minOf(it.endMs, audibleEndMs) - maxOf(it.startMs, audibleStartMs) >= minLengthMs }
