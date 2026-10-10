package com.yeivikas.olyzecs.ui

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Matemática pura de la ventana ampliada de un clip de audio
 * ([AudioClipDetailPanel]). Sin Compose ni `Uri`, para cubrirla con JUnit.
 */

/** Zoom horizontal mínimo: el archivo entero ocupa todo el ancho. */
internal const val DETAIL_MIN_ZOOM = 1f

/** Tope absoluto del zoom horizontal (el real depende de la resolución de los datos, ver [detailMaxZoom]). */
internal const val DETAIL_ABSOLUTE_MAX_ZOOM = 8192f

/** Zoom vertical (amplitud): 1× = escala completa ocupa ~92 % de la altura. */
internal const val DETAIL_MIN_AMP_ZOOM = 1f
internal const val DETAIL_MAX_AMP_ZOOM = 32f

/** Zoom al que salta el doble toque cuando se está en 1×. */
internal const val DETAIL_DOUBLE_TAP_ZOOM = 6f

/**
 * Pasos "redondos" (ms) candidatos para las líneas de la cuadrícula de tiempo,
 * desde 0,01 ms (zoom a nivel de muestra) hasta 10 min (archivo entero).
 */
private val DETAIL_GRID_STEPS_MS = doubleArrayOf(
    0.01, 0.02, 0.05, 0.1, 0.2, 0.5,
    1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
    1_000.0, 2_000.0, 5_000.0, 10_000.0, 30_000.0, 60_000.0, 120_000.0, 300_000.0, 600_000.0
)

/**
 * Menor paso redondo (ms) cuyas líneas quedan separadas al menos [minSpacingPx]
 * con la escala [pxPerMs]; si ninguno alcanza, el mayor.
 */
internal fun detailGridStepMs(pxPerMs: Float, minSpacingPx: Float): Double {
    if (pxPerMs <= 0f) return DETAIL_GRID_STEPS_MS.last()
    return DETAIL_GRID_STEPS_MS.firstOrNull { it * pxPerMs >= minSpacingPx } ?: DETAIL_GRID_STEPS_MS.last()
}

/** `m:ss` o, con [withMillis], `m:ss.mmm`. Negativos se tratan como 0. */
internal fun formatDetailTimeMs(ms: Long, withMillis: Boolean): String {
    val total = ms.coerceAtLeast(0L)
    val minutes = total / 60_000
    val seconds = (total / 1_000) % 60
    val base = "$minutes:${seconds.toString().padStart(2, '0')}"
    return if (withMillis) "$base.${(total % 1_000).toString().padStart(3, '0')}" else base
}

/**
 * Etiqueta de tiempo para una línea de la cuadrícula: la cantidad de decimales
 * sigue al [stepMs] para que cada línea sea distinguible (`m:ss` si el paso es de
 * segundos; `m:ss.mmm` si es de ms; hasta 5 decimales con pasos sub-milisegundo).
 */
internal fun formatDetailTimeAt(ms: Double, stepMs: Double): String {
    val total = if (ms > 0.0) ms else 0.0
    val decimals = when {
        stepMs >= 1_000.0 -> 0
        stepMs >= 1.0 -> 3
        stepMs >= 0.1 -> 4
        else -> 5
    }
    var scale = 1L
    repeat(decimals) { scale *= 10L }
    val units = Math.round(total / 1_000.0 * scale)
    val whole = units / scale
    val minutes = whole / 60
    val seconds = whole % 60
    val head = "$minutes:${seconds.toString().padStart(2, '0')}"
    return if (decimals == 0) head else "$head.${(units % scale).toString().padStart(decimals, '0')}"
}

/**
 * Zoom horizontal máximo: el necesario para que una "unidad" de datos (una
 * muestra, o una cubeta de la forma de onda gruesa) ocupe [pxPerUnitAtMax] píxeles.
 *
 * @param totalMs duración del archivo (ms).
 * @param unitsPerSecond resolución de los datos: sample rate (forma de onda fina)
 *   o cubetas por segundo (forma de onda gruesa).
 */
internal fun detailMaxZoom(totalMs: Double, viewportPx: Float, unitsPerSecond: Double, pxPerUnitAtMax: Float): Float {
    if (viewportPx <= 0f || totalMs <= 0.0 || unitsPerSecond <= 0.0 || pxPerUnitAtMax <= 0f) return DETAIL_MIN_ZOOM
    val zoom = (pxPerUnitAtMax * unitsPerSecond / 1_000.0 * totalMs / viewportPx).toFloat()
    return zoom.coerceIn(DETAIL_MIN_ZOOM, DETAIL_ABSOLUTE_MAX_ZOOM)
}

/** Interpolación GEOMÉTRICA de zoom (`t` de 0 a 1): el zoom se siente parejo en todo el recorrido. */
internal fun detailInterpolateZoom(from: Float, to: Float, t: Float): Float {
    if (from <= 0f || to <= 0f) return to
    return from * (to / from).pow(t.coerceIn(0f, 1f))
}

/** Acota el desplazamiento horizontal (px) a [0, ancho zoomeado − ancho visible]. */
internal fun clampDetailScrollPx(scrollPx: Float, viewportPx: Float, zoom: Float): Float =
    scrollPx.coerceIn(0f, (viewportPx * zoom - viewportPx).coerceAtLeast(0f))

/**
 * Nuevo desplazamiento (px) tras un gesto de pellizco/arrastre que cambia el
 * zoom de [oldZoom] a [newZoom], manteniendo bajo el dedo ([focusPx]) el mismo
 * instante, y aplicando además el arrastre [panPx].
 */
internal fun detailScrollAfterZoom(
    scrollPx: Float,
    oldZoom: Float,
    newZoom: Float,
    focusPx: Float,
    panPx: Float,
    viewportPx: Float
): Float {
    if (oldZoom <= 0f) return 0f
    val anchored = (scrollPx + focusPx) * (newZoom / oldZoom) - focusPx - panPx
    return clampDetailScrollPx(anchored, viewportPx, newZoom)
}

/** `true` si el pellizco es más horizontal que vertical (zoom de tiempo); si no, es zoom de amplitud. */
internal fun detailPinchIsHorizontal(spanX: Float, spanY: Float): Boolean = abs(spanX) >= abs(spanY)

/**
 * Razón de escala de un pellizco entre dos cuadros (`actual / previo`), acotada para
 * que un cuadro ruidoso no dé saltos. Con dedos casi juntos ([minSpan] px) el cálculo
 * es inestable y devuelve 1.
 */
internal fun detailPinchRatio(prevSpan: Float, curSpan: Float, minSpan: Float = 24f): Float {
    if (prevSpan < minSpan || curSpan < 1f) return 1f
    return (curSpan / prevSpan).coerceIn(0.5f, 2f)
}

/** Posición (ms dentro del archivo) bajo [xPx], acotada a [0, totalMs]; `null` si la escala no es válida. */
internal fun detailClipMsAtX(xPx: Float, scrollPx: Float, pxPerMs: Float, totalMs: Long): Long? {
    if (pxPerMs <= 0f) return null
    return floor((scrollPx + xPx) / pxPerMs).toLong().coerceIn(0L, totalMs.coerceAtLeast(0L))
}

/**
 * Instante del PROYECTO (ms) que corresponde a [sourceMs] del archivo, o `null`
 * si ese punto del archivo no suena en el clip (antes del recorte o pasado el
 * final del clip). Solo cuenta la primera pasada (las repeticiones por loop no
 * se mapean). Se usa para mover el cursor al tocar la onda original.
 */
internal fun detailProjectMsForSourceMs(sourceMs: Long, trimStartMs: Long, timelineStartMs: Long, clipLengthMs: Long): Long? {
    val intoClipMs = sourceMs - trimStartMs.coerceAtLeast(0L)
    if (intoClipMs < 0L || intoClipMs > clipLengthMs) return null
    return timelineStartMs + intoClipMs
}
