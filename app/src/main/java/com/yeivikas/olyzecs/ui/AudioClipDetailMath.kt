package com.yeivikas.olyzecs.ui

import kotlin.math.abs
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
 * desde 0,01 ms (zoom a nivel de muestra) hasta 1 h (archivo entero): el proyecto admite hasta 3 h
 * ([com.yeivikas.olyzecs.engine.timeline.TimelineLimits.MAX_DURATION_MS]) y el archivo de un clip puede ser aún más
 * largo; con el tope anterior (10 min) un audio de 2-3 h acababa con los números de la regla pisándose entre sí.
 */
private val DETAIL_GRID_STEPS_MS = doubleArrayOf(
    0.01, 0.02, 0.05, 0.1, 0.2, 0.5,
    1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
    1_000.0, 2_000.0, 5_000.0, 10_000.0, 30_000.0, 60_000.0, 120_000.0, 300_000.0, 600_000.0,
    900_000.0, 1_800_000.0, 3_600_000.0
)

/**
 * Menor paso redondo (ms) cuyas líneas mayores quedan separadas lo que EXIGE ese paso ([requiredSpacingPx], que puede
 * depender del paso: una etiqueta `m:ss.mmmmm` es mucho más ancha que una `m:ss`) con la escala [pxPerMs]; si ninguno
 * alcanza, el mayor. Es lo que mantiene los números de la regla sin pisarse ni tocar la marca siguiente, sea cual sea
 * la densidad de pantalla, el tamaño de fuente o el nivel de zoom.
 */
internal fun detailRulerStepMs(pxPerMs: Float, requiredSpacingPx: (stepMs: Double) -> Float): Double {
    if (pxPerMs <= 0f) return DETAIL_GRID_STEPS_MS.last()
    return DETAIL_GRID_STEPS_MS.firstOrNull { it * pxPerMs >= requiredSpacingPx(it) } ?: DETAIL_GRID_STEPS_MS.last()
}

/** Como [detailRulerStepMs] con una separación fija en px para todos los pasos. */
internal fun detailGridStepMs(pxPerMs: Float, minSpacingPx: Float): Double =
    detailRulerStepMs(pxPerMs) { minSpacingPx }

/** `m:ss` o, con [withMillis], `m:ss.mmm`. Negativos se tratan como 0. */
internal fun formatDetailTimeMs(ms: Long, withMillis: Boolean): String {
    val total = ms.coerceAtLeast(0L)
    val minutes = total / 60_000
    val seconds = (total / 1_000) % 60
    val base = "$minutes:${seconds.toString().padStart(2, '0')}"
    return if (withMillis) "$base.${(total % 1_000).toString().padStart(3, '0')}" else base
}

/** Máximo de decimales de segundo de una etiqueta (pasos sub-milisegundo, hasta 0,01 ms). */
private const val DETAIL_LABEL_MAX_DECIMALS = 5

/**
 * Decimales de segundo MÍNIMOS con los que se escribe exactamente cada múltiplo de [stepMs]: 0 si el paso es de segundos
 * enteros, 1 para 100/200/500 ms, 2 para 10/20/50 ms, 3 para 1/2/5 ms… Los programas de audio profesionales no rellenan
 * con ceros que no aportan (`0:00.5`, no `0:00.500`): el número es más corto, se lee antes y deja más aire en la regla.
 */
internal fun detailLabelDecimals(stepMs: Double): Int {
    if (stepMs >= 1_000.0) return 0
    if (!(stepMs > 0.0)) return DETAIL_LABEL_MAX_DECIMALS
    val stepSeconds = stepMs / 1_000.0
    var scale = 1.0
    for (decimals in 0..DETAIL_LABEL_MAX_DECIMALS) {
        val scaled = stepSeconds * scale
        if (scaled >= 1.0 && abs(scaled - Math.round(scaled).toDouble()) < 1e-6 * scaled) return decimals
        scale *= 10.0
    }
    return DETAIL_LABEL_MAX_DECIMALS
}

/**
 * Etiqueta de tiempo para una marca de la regla o de la cuadrícula: `m:ss` (y `h:mm:ss` desde 1 hora), con los decimales
 * justos que pide el [stepMs] ([detailLabelDecimals]) para que cada marca sea distinguible: `0:03` si el paso es de
 * segundos, `0:00.5` a 500 ms, `0:00.05` a 50 ms, `0:00.005` a 5 ms, hasta 5 decimales con pasos sub-milisegundo.
 * Negativos se tratan como 0.
 */
internal fun formatDetailTimeAt(ms: Double, stepMs: Double): String {
    val total = if (ms > 0.0) ms else 0.0
    val decimals = detailLabelDecimals(stepMs)
    var scale = 1L
    repeat(decimals) { scale *= 10L }
    val units = Math.round(total / 1_000.0 * scale)
    val whole = units / scale
    val seconds = whole % 60
    val head = if (whole >= 3_600L) {
        "${whole / 3_600}:${((whole % 3_600) / 60).toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${whole / 60}:${seconds.toString().padStart(2, '0')}"
    }
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
