package com.yeivikas.olyzecs.ui

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sign

/**
 * Matemática pura del eje de tiempo de la ventana ampliada del audio
 * ([AudioClipDetailRuler] y [AudioClipDetailPlayhead]): regla, cabezal arrastrable y su autodesplazamiento.
 * Sin Compose ni Android, para cubrirla con JUnit.
 */

/** Alto (dp) de la regla de tiempo: el cabezal vive dentro de ella y la onda empieza justo debajo. */
internal const val DETAIL_RULER_HEIGHT_DP = 36

/**
 * Margen (dp) a cada lado del eje de tiempo: el inicio (0:00) y el final de la señal quedan a esta distancia del
 * borde de la pantalla en vez de pegados a él. Sin él, el cabezal en 0:00 —el estado normal al abrir la ventana—
 * se dibujaba cortado por la mitad y tapaba el primer número; con él se ve entero. Mayor que el semiancho del
 * cabezal (10 dp, ×1,08 al sostenerlo). La onda, la cuadrícula y los gestos miden desde dentro de este margen.
 */
internal const val DETAIL_TIME_GUTTER_DP = 16

/**
 * Semiancho (dp) de la zona que "agarra" el cabezal: mucho mayor que el dibujo (22 dp) para que
 * un dedo lo atrape sin tener que apuntar con precisión (el mínimo táctil recomendado es 48 dp).
 */
internal const val DETAIL_HEAD_HIT_HALF_WIDTH_DP = 28

/** Ancho (dp) de la franja de cada borde donde, mientras se arrastra el cabezal, la vista se desplaza sola. */
internal const val DETAIL_EDGE_AUTOSCROLL_ZONE_DP = 56

/** Velocidad máxima (dp/s) del autodesplazamiento, a ras del borde. */
internal const val DETAIL_EDGE_AUTOSCROLL_MAX_DP_PER_S = 1_400

/**
 * Posición que el cabezal toma al arrastrarlo: el punto del ARCHIVO ([sourceMs], continuo, sin
 * redondear) y el instante del PROYECTO ([projectMs], entero, como mucho el último ms del clip) al que hay que mover
 * el cursor.
 */
internal data class DetailScrubTarget(val sourceMs: Double, val projectMs: Long)

/**
 * Tramo del ARCHIVO (ms) que suena en el clip: `[recorte, recorte + largo]`, sin pasar del final del
 * archivo ([totalMs]) ni de la primera pasada (el loop no se mapea). `null` si la duración no es válida o si el
 * recorte cae fuera del archivo (no hay tramo audible).
 */
internal fun detailAudibleWindowMs(trimStartMs: Long, clipLengthMs: Long, totalMs: Double): Pair<Double, Double>? {
    if (totalMs <= 0.0) return null
    val start = trimStartMs.coerceAtLeast(0L).toDouble()
    val end = minOf(totalMs, start + clipLengthMs.coerceAtLeast(0L).toDouble())
    return if (start <= end) start to end else null
}

/**
 * Dónde se dibuja el cabezal cuando NO se está arrastrando: [sourceMs] en el archivo y si el cursor del proyecto
 * cae realmente dentro del clip ([insideClip]). Fuera, se muestra pegado al borde más cercano del tramo audible
 * (atenuado), para que el cabezal SIEMPRE exista en la regla y se pueda agarrar y arrastrar hacia dentro.
 */
internal data class DetailRestingHead(val sourceMs: Double, val insideClip: Boolean)

/**
 * @param windowStartMs inicio y @param windowEndMs fin del tramo audible ([detailAudibleWindowMs]).
 * @param sourcePositionMs posición del cursor en el archivo (`sourcePositionAt`), `null` si el clip no emite ahí.
 */
internal fun detailRestingHead(
    playheadMs: Long,
    timelineStartMs: Long,
    clipLengthMs: Long,
    windowStartMs: Double,
    windowEndMs: Double,
    sourcePositionMs: Long?
): DetailRestingHead = when {
    playheadMs < timelineStartMs -> DetailRestingHead(windowStartMs, insideClip = false)
    playheadMs > timelineStartMs + clipLengthMs -> DetailRestingHead(windowEndMs, insideClip = false)
    sourcePositionMs != null -> DetailRestingHead(sourcePositionMs.toDouble(), insideClip = true)
    else -> DetailRestingHead(windowEndMs, insideClip = false)
}

/**
 * Convierte la X del cabezal ([xPx], ya con el desfase de agarre aplicado) al instante al que debe ir el cursor.
 *
 * A diferencia de tocar la onda (que ignora los puntos que no suenan), arrastrar nunca "se pierde": el
 * resultado se ACOTA al tramo que sí suena en el clip — `[recorte, recorte + largo]` del archivo,
 * sin pasar del final del archivo ni de la primera pasada (el loop no se mapea) —, así el cabezal se queda en
 * el borde del clip en lugar de desaparecer o saltar.
 *
 * @return `null` solo si la escala no es válida, o el recorte cae fuera del archivo.
 */
internal fun detailScrubTarget(
    xPx: Float,
    scrollPx: Float,
    pxPerMs: Float,
    totalMs: Double,
    trimStartMs: Long,
    timelineStartMs: Long,
    clipLengthMs: Long
): DetailScrubTarget? {
    if (pxPerMs <= 0f) return null
    val (lo, hi) = detailAudibleWindowMs(trimStartMs, clipLengthMs, totalMs) ?: return null
    // `lo..hi` está dentro de `0..totalMs`, así que acotar primero al archivo y luego al tramo equivale a acotar al tramo.
    val sourceMs = detailXToSourceMs(xPx, scrollPx, pxPerMs, totalMs).coerceIn(lo, hi)
    val intoClipMs = sourceMs - lo
    // El clip ocupa el intervalo SEMIABIERTO [inicio, fin) del proyecto (igual que `PreviewLoopRange`): el último
    // instante alcanzable es `fin − 1`. En `fin` exacto el cursor ya está fuera del clip (el cabezal se vería atenuado)
    // y `seekTo` ampliaría el proyecto si el clip termina donde termina el proyecto (el último clip, el caso común).
    val lastProjectMs = (timelineStartMs + clipLengthMs - 1L).coerceAtLeast(timelineStartMs)
    return DetailScrubTarget(sourceMs, (timelineStartMs + Math.round(intoClipMs)).coerceAtMost(lastProjectMs))
}

/**
 * ÚNICA conversión X → tiempo del eje (la usan el arrastre del cabezal y la selección de rango).
 * Instante del ARCHIVO (ms) bajo una X del eje de tiempo ([axisXPx]: 0 = inicio de la vista, ya sin el margen lateral),
 * acotado a `0 … totalMs`: arrastrar más allá de un extremo del archivo se queda en el extremo.
 * Con escala o duración no válidas devuelve 0.
 */
internal fun detailXToSourceMs(axisXPx: Float, scrollPx: Float, pxPerMs: Float, totalMs: Double): Double {
    if (pxPerMs <= 0f || totalMs <= 0.0) return 0.0
    return ((scrollPx + axisXPx).toDouble() / pxPerMs).coerceIn(0.0, totalMs)
}

/** ÚNICA conversión tiempo → X del eje (cabezal, regla y selección): inversa de [detailXToSourceMs] sin acotar. */
internal fun detailSourceMsToX(sourceMs: Double, scrollPx: Float, pxPerMs: Float): Float =
    (sourceMs * pxPerMs - scrollPx).toFloat()

/**
 * `true` si el gesto fue un TOQUE SIMPLE sobre el icono del cabezal (el que inicia la reproducción): empezó sobre el
 * cabezal ([onHead]), terminó levantando el dedo ([completed], no se canceló), no se movió más que el umbral de toque
 * ([maxMovePx] ≤ [slopPx]) y fue RÁPIDO ([pressMs] < [longPressMs]). Mantenerlo presionado o arrastrarlo es el recorrido
 * de la línea de tiempo y no reproduce al soltar.
 */
internal fun detailIsHeadTap(
    onHead: Boolean,
    completed: Boolean,
    maxMovePx: Float,
    slopPx: Float,
    pressMs: Long,
    longPressMs: Long
): Boolean = onHead && completed && maxMovePx <= slopPx && pressMs < longPressMs

/** `true` si un toque en [downX] cae sobre el cabezal dibujado en [headX] (zona de ± [halfWidthPx]). */
internal fun detailHeadHit(downX: Float, headX: Float, halfWidthPx: Float): Boolean =
    abs(downX - headX) <= halfWidthPx

/**
 * Desfase de agarre (px) de un toque en la regla en [downX] con el cabezal dibujado en [headX]: si el dedo cae SOBRE el
 * cabezal ([detailHeadHit]) se conserva la distancia entre ambos (agarrarlo por un borde no lo hace saltar); si cae en
 * cualquier otro punto de la regla es 0, y el cabezal va directamente bajo el dedo.
 */
internal fun detailRulerGrabOffsetPx(downX: Float, headX: Float, halfWidthPx: Float): Float =
    if (detailHeadHit(downX, headX, halfWidthPx)) headX - downX else 0f

/**
 * Velocidad (px/s) a la que debe desplazarse la vista mientras el cabezal se arrastra cerca de un borde:
 * 0 fuera de las franjas de [zonePx] de cada lado; dentro, crece con el CUADRADO de lo adentrado
 * (suave al entrar, rápida pegado al borde) hasta [maxPxPerSec].
 *
 * Signo: positivo = el desplazamiento (`scrollPx`) aumenta (la vista avanza hacia el final del archivo,
 * dedo en el borde derecho); negativo = retrocede (borde izquierdo).
 */
internal fun detailEdgeAutoScrollPxPerSec(fingerX: Float, viewportPx: Float, zonePx: Float, maxPxPerSec: Float): Float {
    if (zonePx <= 0f || viewportPx <= 2f * zonePx) return 0f
    val intoLeft = zonePx - fingerX
    val intoRight = fingerX - (viewportPx - zonePx)
    val depth = when {
        intoLeft > 0f -> -(intoLeft / zonePx).coerceIn(0f, 1f)
        intoRight > 0f -> (intoRight / zonePx).coerceIn(0f, 1f)
        else -> 0f
    }
    return sign(depth) * depth * depth * maxPxPerSec
}

/**
 * `true` si la etiqueta de la regla debe dibujarse: [labelLeft] y [labelWidth] en px del eje, [leftEdgeX]/[rightEdgeX] los
 * bordes de la pantalla en ese mismo eje.
 *  - Izquierda: sigue visible mientras le quede texto a la vista (el número se desliza fuera con su marca).
 *  - Derecha: solo si cabe ENTERA (`derecha del texto ≤ borde`). Un número cortado por el borde derecho ("0:" de "0:03.400") se lee
 *    como un defecto, y en reposo (zoom 1×) se quedaba así de forma permanente.
 */
internal fun detailRulerLabelVisible(labelLeft: Float, labelWidth: Float, leftEdgeX: Float, rightEdgeX: Float): Boolean =
    labelLeft + labelWidth > leftEdgeX && labelLeft + labelWidth <= rightEdgeX

/**
 * En cuántas partes se divide cada paso mayor ([stepMs], de [detailGridStepMs]) de la regla con marcas menores,
 * de modo que las marcas menores siempre caigan en valores "redondos":
 *  - pasos 1 y 5 (×10ⁿ ms) → 5 partes (1 s → 200 ms; 5 s → 1 s); paso 2 (×10ⁿ) → 4 (2 s → 500 ms);
 *  - 30 s → 3 (10 s); 1 min → 6 (10 s); 2 min → 4 (30 s); 5 min → 5 (1 min); 10 min → 10 (1 min);
 *    15 min → 3 (5 min); 30 min → 6 (5 min); 1 h → 6 (10 min).
 * Con un valor no válido devuelve 5. Siempre ≥ 2.
 */
internal fun detailRulerSubdivisions(stepMs: Double): Int {
    if (stepMs <= 0.0 || stepMs.isNaN() || stepMs.isInfinite()) return 5
    when (stepMs) {
        30_000.0 -> return 3
        60_000.0 -> return 6
        120_000.0 -> return 4
        300_000.0 -> return 5
        600_000.0 -> return 10
        900_000.0 -> return 3
        1_800_000.0 -> return 6
        3_600_000.0 -> return 6
    }
    val mantissa = stepMs / 10.0.pow(floor(log10(stepMs)))
    return if (Math.round(mantissa).toInt() == 2) 4 else 5
}
