package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.audibleSourceRangeMs
import kotlin.math.floor

private val RulerTop = Color(0xFF1C1936)
private val RulerBottom = Color(0xFF131126)
private val RulerHairline = Color.White.copy(alpha = 0.10f)
private val TickMajor = Color.White.copy(alpha = 0.62f)
private val TickMid = Color.White.copy(alpha = 0.36f)
private val TickMinor = Color.White.copy(alpha = 0.22f)
private val LabelColor = Color.White.copy(alpha = 0.66f)
private val GridColor = Color.White.copy(alpha = 0.07f)
private val OutsideClipShade = Color.Black.copy(alpha = 0.55f)
/** Sombra de lo que suena en OTRO clip del carril sobre el mismo archivo: más suave que la de lo que nadie usa. */
private val SiblingClipShade = Color.Black.copy(alpha = 0.24f)
/** Barra bajo la regla de los tramos de los clips hermanos: la misma idea que la del clip abierto, más tenue. */
private const val SIBLING_BAR_ALPHA = 0.26f

/**
 * Separación mínima (dp) entre las líneas MAYORES de la regla, aunque las etiquetas fueran más cortas: con 64 dp una regla
 * se lee de un vistazo en cualquier pantalla (en un teléfono estrecho pasa de 500 ms a 1 s en vez de apretar los números).
 */
private const val MAJOR_MIN_SPACING_DP = 64f

/**
 * Aire mínimo (dp) entre el final de un número y el comienzo del siguiente: una holgura de lectura, no apenas la que
 * evita que se toquen (el criterio de las reglas de los editores de audio: los números nunca se pisan y el paso se
 * adapta al zoom).
 */
private const val LABEL_GAP_DP = 24f

/** Capacidad de la caché de textos medidos: más que las etiquetas visibles a la vez, para no re-medirlas en cada cuadro. */
private const val RULER_TEXT_CACHE_SIZE = 96

/**
 * Regla de tiempo de la ventana ampliada del audio: franja superior de [DETAIL_RULER_HEIGHT_DP] con marcas mayores
 * (con su tiempo), medias y menores, más —bajo ella— la cuadrícula tenue y lo que queda fuera del tramo que suena.
 * Una barra del color del clip, en el filo inferior de la regla, marca el tramo que suena; los tramos que suenan en otros
 * clips del mismo carril sobre el mismo archivo ([siblingSpans]) llevan una barra más tenue y una sombra suave, y solo lo
 * que nadie usa queda con la sombra fuerte.
 *
 * Es ESTÁTICA: solo se repinta al cambiar el zoom, el desplazamiento o el clip. El cabezal que se mueve por ella y
 * su arrastre viven aparte ([AudioClipDetailPlayhead]) precisamente para que arrastrarlo no repinte esto.
 *
 * Las etiquetas siguen al zoom (de `m:ss` a sub-milisegundos) y están desplazadas 11 dp a la derecha de su marca:
 * con el cabezal en 0:00 (el estado normal al abrir) queda justo a su izquierda sin taparlas.
 *
 * La separación entre números se calcula con el ANCHO MEDIDO de cada etiqueta (no con un valor fijo en píxeles), así que
 * dos números vecinos conservan siempre un aire mínimo ([LABEL_GAP_DP]) con cualquier densidad de pantalla, escala de
 * fuente o profundidad de zoom, sin dejar huecos de más.
 * Marcas y números siguen existiendo en los márgenes laterales y se desplazan hasta el borde de la pantalla: no se
 * cortan en seco al cruzar el borde del eje de tiempo, y un número cuya marca ya salió por la izquierda sigue visible
 * mientras le quede texto en pantalla. Por la derecha, en cambio, un número solo aparece cuando CABE entero: nunca
 * queda un trozo ("0:") pegado al borde de la pantalla, ni en reposo ni al desplazar.
 *
 * @param pxPerMs escala (px por ms de archivo); @param scrollPx desplazamiento horizontal (px).
 * @param viewportPx ancho del eje de tiempo (sin los márgenes); @param gutterPx margen lateral ([DETAIL_TIME_GUTTER_DP]).
 */
@Composable
internal fun AudioClipDetailRuler(
    clip: AudioClip,
    signalColor: Color,
    totalMs: Double,
    scrollPx: Float,
    pxPerMs: Float,
    viewportPx: Float,
    gutterPx: Float,
    siblingSpans: List<DetailSourceSpan> = emptyList(),
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer(cacheSize = RULER_TEXT_CACHE_SIZE)
    val labelStyle = remember {
        TextStyle(color = LabelColor, fontSize = 10.sp, fontFeatureSettings = "tnum")
    }
    // `graphicsLayer`: capa propia, para que repintar el cabezal (otra capa) no obligue a re-grabar esta.
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer()) {
        drawRuler(clip, signalColor, totalMs, scrollPx, pxPerMs, viewportPx, gutterPx, siblingSpans, measurer, labelStyle)
    }
}

private fun DrawScope.drawRuler(
    clip: AudioClip,
    signalColor: Color,
    totalMs: Double,
    scrollPx: Float,
    pxPerMs: Float,
    viewportPx: Float,
    gutterPx: Float,
    siblingSpans: List<DetailSourceSpan>,
    measurer: TextMeasurer,
    labelStyle: TextStyle
) {
    if (pxPerMs <= 0f || viewportPx <= 0f) return
    val rulerH = DETAIL_RULER_HEIGHT_DP.dp.toPx()

    // Fondo de la regla a TODO el ancho (incluye los márgenes laterales) y su filo inferior.
    drawRect(
        brush = Brush.verticalGradient(listOf(RulerTop, RulerBottom), startY = 0f, endY = rulerH),
        size = Size(size.width, rulerH)
    )
    drawLine(RulerHairline, Offset(0f, rulerH - 0.5f), Offset(size.width, rulerH - 0.5f), strokeWidth = 1f)

    // Todo lo demás está en coordenadas del EJE DE TIEMPO (x = 0 en 0:00 de la vista), dentro del margen.
    withTransform({ translate(left = gutterPx) }) {
        drawAxisContent(clip, signalColor, totalMs, scrollPx, pxPerMs, viewportPx, gutterPx, rulerH, siblingSpans, measurer, labelStyle)
    }
}

private fun DrawScope.drawAxisContent(
    clip: AudioClip,
    signalColor: Color,
    totalMs: Double,
    scrollPx: Float,
    pxPerMs: Float,
    w: Float,
    gutterPx: Float,
    rulerH: Float,
    siblingSpans: List<DetailSourceSpan>,
    measurer: TextMeasurer,
    labelStyle: TextStyle
) {
    val h = size.height
    val scrollMs = scrollPx / pxPerMs.toDouble()
    val belowH = (h - rulerH).coerceAtLeast(0f)

    // 1) Mapa de uso del archivo bajo la regla: lo que nadie usa, con sombra fuerte; lo que suena solo en OTROS clips del
    //    carril sobre este archivo (los trozos de un Recorte con varios rangos), con sombra suave; el tramo del clip abierto, sin sombra.
    var rangeStartX = 0f
    var rangeEndX = w
    var openClipSpan: DetailSourceSpan? = null
    if (clip.sourceDurationMs > 0L) {
        val (audibleStartMs, audibleEndMs) =
            audibleSourceRangeMs(clip.sourceDurationMs, clip.trimStartMs, clip.loop, clip.clipLengthMs)
        rangeStartX = detailSourceMsToX(audibleStartMs.toDouble(), scrollPx, pxPerMs).coerceIn(0f, w)
        rangeEndX = detailSourceMsToX(audibleEndMs.toDouble(), scrollPx, pxPerMs).coerceIn(0f, w)
        val openSpan = DetailSourceSpan(audibleStartMs, audibleEndMs)
        openClipSpan = openSpan
        val total = clip.sourceDurationMs
        for (gap in detailUnusedSpans(listOf(openSpan) + siblingSpans, total)) {
            shadeSpan(gap, total, OutsideClipShade, scrollPx, pxPerMs, w, rulerH, belowH)
        }
        for (span in detailSiblingOnlySpans(openSpan, siblingSpans)) {
            shadeSpan(span, total, SiblingClipShade, scrollPx, pxPerMs, w, rulerH, belowH)
        }
    }

    // 2) Paso mayor de la regla (decidido por el ancho real de los números) y cuadrícula tenue a lo largo de la onda:
    //    una línea por cada marca mayor.
    val labelPad = 11.dp.toPx()
    val labelTop = 4.dp.toPx()
    val minMajorSpacing = MAJOR_MIN_SPACING_DP.dp.toPx()
    val labelGap = LABEL_GAP_DP.dp.toPx()
    // El paso mayor lo dicta el ancho real del número MÁS LARGO que llevaría (el del final del archivo): dos números
    // consecutivos nunca se pisan. Todos los números comparten el mismo desfase respecto a su marca, así que lo único que
    // separa a dos vecinos es (distancia entre marcas − ancho del texto): el desfase NO entra en la cuenta. (Las marcas
    // van abajo y los números arriba, por eso un número puede asomar sobre la marca siguiente sin tocarla.)
    val stepMs = detailRulerStepMs(pxPerMs) { candidateStepMs ->
        val widest = measurer.measure(
            text = formatDetailTimeAt(totalMs, candidateStepMs), style = labelStyle, softWrap = false, maxLines = 1
        ).size.width
        maxOf(minMajorSpacing, widest + labelGap)
    }
    var gridIndex = floor(scrollMs / stepMs).toLong()
    while (true) {
        val t = gridIndex * stepMs
        val x = ((t - scrollMs) * pxPerMs).toFloat()
        if (x > w || t > totalMs) break
        if (x >= 0f) drawLine(GridColor, Offset(x, rulerH), Offset(x, h), strokeWidth = 1f)
        gridIndex++
    }

    // 3) Barra del tramo que suena, sobre el filo inferior de la regla.
    if (rangeEndX > rangeStartX) {
        val barH = 2.dp.toPx()
        drawRect(signalColor.copy(alpha = 0.55f), Offset(rangeStartX, rulerH - barH), Size(rangeEndX - rangeStartX, barH))
    }
    openClipSpan?.let { openSpan ->
        val barH = 2.dp.toPx()
        for (span in detailSiblingOnlySpans(openSpan, siblingSpans)) {
            val x0 = detailSourceMsToX(span.startMs.toDouble(), scrollPx, pxPerMs).coerceIn(0f, w)
            val x1 = detailSourceMsToX(span.endMs.toDouble(), scrollPx, pxPerMs).coerceIn(0f, w)
            if (x1 > x0) drawRect(signalColor.copy(alpha = SIBLING_BAR_ALPHA), Offset(x0, rulerH - barH), Size(x1 - x0, barH))
        }
    }

    // 4) Marcas: mayores (con número), medias y menores. Se recorre desde antes del borde izquierdo de la PANTALLA (no solo
    //    del eje): así una marca que ya salió por la izquierda sigue dejando ver su número mientras le quede texto, y por
    //    la derecha todo llega hasta el borde. Solo se dibuja dentro del archivo (0 … totalMs).
    val subdivisions = detailRulerSubdivisions(stepMs)
    val minorStepMs = stepMs / subdivisions
    val majorLen = 12.dp.toPx()
    val midLen = 8.dp.toPx()
    val minorLen = 5.dp.toPx()
    val majorStroke = 1.5f.dp.toPx()
    val minorStroke = 1.dp.toPx()
    val longestLabelPx = measurer.measure(
        text = formatDetailTimeAt(totalMs, stepMs), style = labelStyle, softWrap = false, maxLines = 1
    ).size.width
    val leftEdgeX = -gutterPx
    val rightEdgeX = w + gutterPx
    val firstMs = scrollMs + (leftEdgeX - labelPad - longestLabelPx) / pxPerMs
    var i = maxOf(0L, floor(firstMs / minorStepMs).toLong())
    while (true) {
        val t = i * minorStepMs
        val x = ((t - scrollMs) * pxPerMs).toFloat()
        if (x > rightEdgeX || t > totalMs) break
        val major = i % subdivisions == 0L
        if (x >= leftEdgeX) {
            val mid = !major && subdivisions % 2 == 0 && i % (subdivisions / 2) == 0L
            val len = if (major) majorLen else if (mid) midLen else minorLen
            val color = if (major) TickMajor else if (mid) TickMid else TickMinor
            drawLine(color, Offset(x, rulerH - len), Offset(x, rulerH), strokeWidth = if (major) majorStroke else minorStroke)
        }
        if (major) {
            val label = measurer.measure(
                text = formatDetailTimeAt(t, stepMs), style = labelStyle, softWrap = false, maxLines = 1
            )
            val labelLeft = x + labelPad
            // Se pinta ya medido (sin `size` que pueda salir negativo junto al borde y lanzar excepción) y solo si es
            // legible: ver [detailRulerLabelVisible] (nunca un trozo cortado en el borde derecho).
            if (detailRulerLabelVisible(labelLeft, label.size.width.toFloat(), leftEdgeX, rightEdgeX)) {
                drawText(textLayoutResult = label, topLeft = Offset(labelLeft, labelTop))
            }
        }
        i++
    }
}

/**
 * Sombrea, bajo la regla, el tramo [span] del archivo. Un tramo que llega al final del archivo se extiende hasta el borde
 * del eje (lo que queda a la derecha del archivo nunca se deja sin sombra, como antes de existir el mapa de uso).
 */
private fun DrawScope.shadeSpan(
    span: DetailSourceSpan,
    totalMs: Long,
    color: Color,
    scrollPx: Float,
    pxPerMs: Float,
    w: Float,
    rulerH: Float,
    belowH: Float
) {
    val x0 = detailSourceMsToX(span.startMs.toDouble(), scrollPx, pxPerMs).coerceIn(0f, w)
    val x1 = if (span.endMs >= totalMs) w else detailSourceMsToX(span.endMs.toDouble(), scrollPx, pxPerMs).coerceIn(0f, w)
    if (x1 > x0) drawRect(color, Offset(x0, rulerH), Size(x1 - x0, belowH))
}
