package com.yeivikas.olyzecs.ui

import kotlin.math.abs

/**
 * Matemática pura de UN rango de la selección de la ventana ampliada del audio ([AudioClipDetailSelectionState]).
 * Sin Compose ni Android, para cubrirla con JUnit. Las reglas del CONJUNTO de rangos están en
 * `AudioClipDetailSelectionSetMath.kt` y las de la cantidad máxima (x1 … x7) en `AudioClipDetailSelectCountMath.kt`.
 *
 * Modelo (referencia: WavePad, guía oficial de NCH Software, «Main Screen» y «Selected Region»): arrastrar sobre la onda
 * crea una selección que va del punto donde se apoyó el dedo al punto donde está ahora; la selección se mide sobre el
 * ARCHIVO (ms), igual que la regla y el cabezal, así que sobrevive al zoom y al desplazamiento.
 */

/**
 * Semiancho (dp) de la zona que "agarra" un borde de la selección ya hecha: un arrastre que empieza dentro de ella
 * ajusta ESE borde en vez de crear una selección nueva (en la captura de WavePad los dos bordes se mueven así).
 */
internal const val DETAIL_SELECTION_EDGE_HIT_HALF_WIDTH_DP = 22

/** Rango seleccionado del ARCHIVO, en ms continuos: `startMs < endMs`, dentro de `0 … duración`. */
internal data class DetailSelection(val startMs: Double, val endMs: Double) {
    val durationMs: Double get() = endMs - startMs
}

/** Borde de una selección. */
internal enum class DetailSelectionEdge { START, END }

/**
 * Hueco `[startMs, endMs]` del archivo donde puede vivir UN rango: el que dejan libre los demás rangos (entre sus
 * vecinos, o hasta los extremos del archivo). Un arrastre nunca sale de él, así los rangos no se solapan.
 */
internal data class DetailFreeSpan(val startMs: Double, val endMs: Double)

/**
 * Selección de un arrastre: de [anchorMs] (donde se apoyó el dedo, o el borde OPUESTO si se ajusta un borde) a
 * [currentMs] (donde está el dedo), ambos acotados a [span]. Se ordena sola (arrastrar hacia la izquierda también
 * selecciona; pasar el borde opuesto da la vuelta a la selección).
 *
 * @return `null` si no hay rango (el hueco es vacío o el dedo volvió al punto de partida): sin selección.
 */
internal fun detailSelectionFromDragIn(span: DetailFreeSpan, anchorMs: Double, currentMs: Double): DetailSelection? {
    val a = anchorMs.coerceIn(span.startMs, span.endMs)
    val c = currentMs.coerceIn(span.startMs, span.endMs)
    val start = minOf(a, c)
    val end = maxOf(a, c)
    return if (end > start) DetailSelection(start, end) else null
}

/**
 * Selección que, tras invertir el audio (REV), abarca el MISMO fragmento de señal: la onda se refleja respecto al centro
 * del archivo, así que `[inicio, fin)` pasa a `[total − fin, total − inicio)`. Acotada a `0 … totalMs`; `null` si la
 * duración no es válida o el reflejo no deja rango. Es su propia inversa (reflejar dos veces devuelve el original).
 */
internal fun mirrorDetailSelection(selection: DetailSelection, totalMs: Double): DetailSelection? {
    if (totalMs <= 0.0) return null
    val start = (totalMs - selection.endMs).coerceIn(0.0, totalMs)
    val end = (totalMs - selection.startMs).coerceIn(0.0, totalMs)
    return if (end > start) DetailSelection(start, end) else null
}

/**
 * Qué borde de [selection] agarra un toque en [downAxisXPx] (X del eje de tiempo): el MÁS CERCANO si cae a
 * ≤ [halfWidthPx] de él; `null` si el toque cae lejos de ambos (entonces empieza una selección nueva). Con una selección
 * tan corta que sus dos zonas se solapan gana el borde más cercano al dedo.
 */
internal fun detailSelectionEdgeHit(
    downAxisXPx: Float,
    selection: DetailSelection,
    scrollPx: Float,
    pxPerMs: Float,
    halfWidthPx: Float
): DetailSelectionEdge? {
    if (pxPerMs <= 0f) return null
    val toStart = detailSelectionEdgeDistancePx(downAxisXPx, selection, DetailSelectionEdge.START, scrollPx, pxPerMs)
    val toEnd = detailSelectionEdgeDistancePx(downAxisXPx, selection, DetailSelectionEdge.END, scrollPx, pxPerMs)
    val edge = if (toStart <= toEnd) DetailSelectionEdge.START else DetailSelectionEdge.END
    return if (minOf(toStart, toEnd) <= halfWidthPx) edge else null
}

/** Distancia (px) en el eje de tiempo entre un toque en [downAxisXPx] y el borde [edge] de [selection]. */
internal fun detailSelectionEdgeDistancePx(
    downAxisXPx: Float,
    selection: DetailSelection,
    edge: DetailSelectionEdge,
    scrollPx: Float,
    pxPerMs: Float
): Float = abs(downAxisXPx - detailSourceMsToX(detailSelectionEdgeMs(selection, edge), scrollPx, pxPerMs))

/** Instante (ms) del borde [edge] de [selection]. */
internal fun detailSelectionEdgeMs(selection: DetailSelection, edge: DetailSelectionEdge): Double =
    if (edge == DetailSelectionEdge.START) selection.startMs else selection.endMs

/** Instante (ms) del borde OPUESTO a [edge]: el que queda fijo mientras se arrastra [edge]. */
internal fun detailSelectionOppositeMs(selection: DetailSelection, edge: DetailSelectionEdge): Double =
    if (edge == DetailSelectionEdge.START) selection.endMs else selection.startMs
