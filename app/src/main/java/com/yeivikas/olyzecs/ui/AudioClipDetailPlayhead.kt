package com.yeivikas.olyzecs.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.sourcePositionAt

private val HeadTop = Color(0xFFFF6B75)
private val HeadBottom = Color(0xFFE12A3D)
private val HeadEdge = Color.Black.copy(alpha = 0.38f)
private val HeadGlow = Color(0xFFFF4D5A)
private val GuideColor = Color.White.copy(alpha = 0.95f)
private val GuideHalo = Color.White.copy(alpha = 0.10f)
private val PillBackground = Color(0xFF0B0A16).copy(alpha = 0.80f)
private val PillBorder = Color.White.copy(alpha = 0.16f)

private const val HEAD_WIDTH_DP = 20f
private const val HEAD_HEIGHT_DP = 28f

/** Opacidad del cabezal cuando el cursor del proyecto está FUERA del clip (sigue ahí, para poder agarrarlo). */
private const val HEAD_OUTSIDE_CLIP_ALPHA = 0.5f

// ---------------------------------------------------------------------------
// Estado
// ---------------------------------------------------------------------------

/**
 * Estado del arrastre del cabezal. [active] y [sourceMs] son estado observable A PROPÓSITO: se leen solo
 * dentro del bloque de dibujo, así que arrastrar repinta únicamente la capa del cabezal, sin recomponer.
 */
@Stable
private class DetailScrubState {
    /** `true` mientras el dedo sostiene el cabezal: es lo que despliega la línea hacia abajo. */
    var active by mutableStateOf(false)

    /** Posición del cabezal en el ARCHIVO (ms, continua) mientras se arrastra. */
    var sourceMs by mutableDoubleStateOf(0.0)

    /** X (px, del eje de tiempo) donde el dedo quiere el cabezal (dedo + desfase de agarre): la usa el autodesplazamiento. */
    var headTargetX = 0f

    /** Último instante (ms de proyecto) enviado al cursor: evita repetir el mismo `seek` en cuadros seguidos. */
    var lastSentProjectMs = Long.MIN_VALUE
}

/** Lo que el cabezal necesita saber del panel, en un solo valor para leerlo siempre fresco desde los gestos. */
private data class DetailPlayheadInputs(
    val clip: AudioClip,
    val playheadMs: Long,
    val totalMs: Double,
    val zoom: Float,
    val scrollPx: Float,
    val pxPerMs: Float,
    val viewportPx: Float
) {
    /**
     * Dónde vive el cabezal en reposo ([detailRestingHead]); `null` solo si el clip no tiene tramo audible.
     * Fuera del clip no desaparece: se queda en el borde más cercano, atenuado.
     */
    fun restingHead(): DetailRestingHead? = detailRestingHeadOf(clip, playheadMs, totalMs)
}

/**
 * Dónde está el cabezal de [clip] en reposo ([detailRestingHead]) para el cursor [playheadMs] del proyecto y la duración
 * [totalMs] del archivo; `null` si el clip no tiene tramo audible. ÚNICA fuente: la usan el cabezal de la regla y la
 * barra de vista general, para que marquen siempre el mismo instante.
 */
internal fun detailRestingHeadOf(clip: AudioClip, playheadMs: Long, totalMs: Double): DetailRestingHead? {
    val (windowStart, windowEnd) = detailAudibleWindowMs(clip.trimStartMs, clip.clipLengthMs, totalMs) ?: return null
    return detailRestingHead(
        playheadMs, clip.timelineStartMs, clip.clipLengthMs, windowStart, windowEnd, clip.sourcePositionAt(playheadMs)
    )
}

/** Forma del cabezal en píxeles, calculada una vez por densidad de pantalla. */
private class DetailHeadShape(density: Density) {
    val halfWidth = with(density) { (HEAD_WIDTH_DP / 2f).dp.toPx() }
    val height = with(density) { HEAD_HEIGHT_DP.dp.toPx() }

    /**
     * Escudo apuntando hacia abajo, en coordenadas locales: origen arriba al centro, `x` de −halfWidth a +halfWidth,
     * `y` de 0 a [height] (la punta). Esquinas superiores redondeadas y punta suavizada.
     */
    val path: Path = with(density) {
        val hw = halfWidth
        val hh = height
        val corner = 4.5f.dp.toPx()
        val tipRound = 2.5f.dp.toPx()
        val tipHalf = tipRound * 0.8f
        val shoulder = hh * 0.50f
        Path().apply {
            moveTo(-hw + corner, 0f)
            lineTo(hw - corner, 0f)
            quadraticBezierTo(hw, 0f, hw, corner)
            lineTo(hw, shoulder)
            lineTo(tipHalf, hh - tipRound)
            // Control a +tipRound bajo la punta: el vértice de la curva cae exactamente en y = hh.
            quadraticBezierTo(0f, hh + tipRound, -tipHalf, hh - tipRound)
            lineTo(-hw, shoulder)
            lineTo(-hw, corner)
            quadraticBezierTo(-hw, 0f, -hw + corner, 0f)
            close()
        }
    }
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

/**
 * Cabezal de reproducción de la ventana ampliada del audio: vive en la regla ([AudioClipDetailRuler]), entre los
 * números. La regla es la ÚNICA superficie que mueve el cursor del proyecto: tocar en cualquier punto de ella lleva ahí
 * el cabezal, y arrastrar lo recorre.
 *
 * ## Comportamiento
 *  - **En reposo** solo existe el cabezal, en la posición del cursor. No hay línea permanente cruzando la onda. Si el
 *    cursor cae FUERA de este clip no desaparece: se queda pegado al borde más cercano del tramo audible, atenuado, y
 *    arrastrarlo lo lleva dentro del clip.
 *  - **Al tocar la regla** (sin retardo: apenas toca, en cualquier punto) el cabezal salta bajo el dedo y se DESPLIEGA la
 *    línea guía hacia abajo con la lectura del tiempo exacto; arrastrando mueve el cursor al instante. Si el dedo cae
 *    sobre el propio cabezal no salta: se conserva el punto por el que se agarró. Al soltar la línea se repliega.
 *  - **Un toque en la onda NO lo mueve**: la zona táctil es solo la franja de la regla.
 *  - **Bordes**: con zoom, llevarlo a menos de [DETAIL_EDGE_AUTOSCROLL_ZONE_DP] de un borde desplaza la vista sola (más
 *    rápido cuanto más pegado), de modo que se recorre todo el archivo sin soltar.
 *  - Se detiene en los extremos del clip (no desaparece ni salta fuera del tramo que suena).
 *
 * ## Fluidez
 *  - La posición visual sale directamente del dedo (estado local), sin esperar el viaje de ida y vuelta por el
 *    ViewModel: sigue al dedo sin retardo. El `seek` se envía por evento y solo si el milisegundo cambió.
 *  - Su capa de dibujo es independiente de la regla (`graphicsLayer`): arrastrar repinta solo el cabezal.
 *  - Los gestos leen lo más reciente del panel vía `rememberUpdatedState`: cambiar zoom o desplazamiento en pleno
 *    arrastre no reinicia ni cancela el gesto.
 *
 * ## Gestos
 * La zona táctil cubre TODA la regla y consume el toque: es el control de transporte del cursor (como la regla de un
 * editor de audio). Fuera de la regla (la onda) no consume nada y el panel sigue recibiendo su arrastre, pellizco y doble
 * toque. [DETAIL_HEAD_HIT_HALF_WIDTH_DP] solo decide si el toque agarra el cabezal por donde está (sin salto) o lo trae
 * bajo el dedo. Reclama además los gestos de borde del sistema en la franja de la regla, para que arrastrar el cabezal
 * cerca de un borde no se confunda con el "atrás" de Android.
 *
 * @param viewportPx ancho del eje de tiempo (sin los márgenes); @param gutterPx margen lateral ([DETAIL_TIME_GUTTER_DP]):
 *   el eje empieza en esa X del lienzo.
 * @param onSeek mueve el cursor del proyecto (ms de proyecto).
 * @param onScrubStart al agarrar el cabezal pausa la reproducción; @param onScrubEnd al soltarlo la retoma si sonaba.
 * @param onPlay toque simple SOBRE el icono del cabezal (rápido y sin mover el dedo, [detailIsHeadTap]): inicia la
 *   reproducción desde donde está el cursor. Nunca pausa: pausar es del botón principal. Mantenerlo presionado o
 *   arrastrarlo sigue siendo el recorrido de la línea de tiempo, sin reproducir al soltar.
 * @param onScrollPxChange el autodesplazamiento pide un nuevo `scrollPx` (el panel es su dueño).
 */
@Composable
internal fun AudioClipDetailPlayhead(
    clip: AudioClip,
    playheadMs: Long,
    totalMs: Double,
    zoom: Float,
    scrollPx: Float,
    pxPerMs: Float,
    viewportPx: Float,
    gutterPx: Float,
    onSeek: (Long) -> Unit,
    onScrollPxChange: (Float) -> Unit,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    onPlay: () -> Unit = {},
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val pillStyle = remember {
        TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    }
    val headShape = remember(density.density) { DetailHeadShape(density) }
    val scrub = remember(clip.id) { DetailScrubState() }

    val inputs = rememberUpdatedState(DetailPlayheadInputs(clip, playheadMs, totalMs, zoom, scrollPx, pxPerMs, viewportPx))
    val seekNow = rememberUpdatedState(onSeek)
    val scrollNow = rememberUpdatedState(onScrollPxChange)
    val scrubStartNow = rememberUpdatedState(onScrubStart)
    val scrubEndNow = rememberUpdatedState(onScrubEnd)
    val playNow = rememberUpdatedState(onPlay)

    val scrubbing = scrub.active
    val unfold = animateFloatAsState(
        targetValue = if (scrubbing) 1f else 0f,
        animationSpec = tween(durationMillis = if (scrubbing) 130 else 90, easing = FastOutSlowInEasing),
        label = "detailPlayheadUnfold"
    )

    // Glifo del cabezal sincronizado con el botón principal: 0 = ❚❚ (reproduciendo), 1 = ▶ (en pausa).
    // Fundido corto entre ambos en vez de un salto seco.
    val playGlyph = animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "detailPlayheadGlyph"
    )

    // Autodesplazamiento en los bordes mientras se sostiene el cabezal. Un cuadro = un paso, medido con el reloj real.
    val zonePx = with(density) { DETAIL_EDGE_AUTOSCROLL_ZONE_DP.dp.toPx() }
    val maxSpeedPx = with(density) { DETAIL_EDGE_AUTOSCROLL_MAX_DP_PER_S.dp.toPx() }
    LaunchedEffect(scrubbing) {
        if (!scrubbing) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (true) {
            val nanos = withFrameNanos { it }
            val dtSeconds = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = nanos
            val now = inputs.value
            val speed = detailEdgeAutoScrollPxPerSec(scrub.headTargetX, now.viewportPx, zonePx, maxSpeedPx)
            if (speed == 0f) continue
            val next = clampDetailScrollPx(now.scrollPx + speed * dtSeconds, now.viewportPx, now.zoom)
            if (next == now.scrollPx) continue
            scrollNow.value(next)
            scrub.moveTo(scrub.headTargetX, next, now, seekNow.value)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Capa de dibujo propia: cabezal, guía y lectura. Es la única que se repinta al arrastrar.
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            drawPlayhead(
                resting = inputs.value.restingHead(),
                scrub = scrub,
                unfold = unfold,
                playGlyph = playGlyph,
                scrollPx = scrollPx,
                pxPerMs = pxPerMs,
                viewportPx = viewportPx,
                gutterPx = gutterPx,
                head = headShape,
                measurer = measurer,
                pillStyle = pillStyle
            )
        }
        // Zona táctil: TODA la regla (y solo la regla). Tocar en cualquier punto lleva el cabezal ahí al instante y
        // arrastrar lo recorre; la onda de abajo no mueve el cursor.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DETAIL_RULER_HEIGHT_DP.dp)
                .systemGestureExclusion()
                .semantics { contentDescription = "Regla de tiempo: toca o arrastra para mover el cabezal de reproducción" }
                .pointerInput(scrub, gutterPx) {
                    val hitHalfPx = DETAIL_HEAD_HIT_HALF_WIDTH_DP.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val start = inputs.value
                        val rest = start.restingHead() ?: return@awaitEachGesture
                        // Las X del gesto son del lienzo completo; el eje de tiempo empieza tras el margen.
                        val downX = down.position.x - gutterPx
                        val restX = (rest.sourceMs * start.pxPerMs - start.scrollPx).toFloat()
                        val onHead = detailHeadHit(downX, restX, hitHalfPx)
                        val grabOffset = detailRulerGrabOffsetPx(downX, restX, hitHalfPx)
                        down.consume()
                        scrub.begin(rest.sourceMs, restX, start.playheadMs)
                        // Mientras se toca o arrastra, la reproducción se PAUSA: el reloj calcula la posición siguiente a
                        // partir del cursor y, sonando, pisaría el que mueve el dedo (el cabezal "pelearía" con él).
                        // Al soltar se retoma desde la nueva posición. Es el mismo par que usa el playhead del timeline.
                        var maxMovePx = 0f
                        var completed = false
                        scrubStartNow.value()
                        try {
                            // Toque fuera del cabezal: va bajo el dedo YA, sin esperar a que el dedo se mueva.
                            if (!onHead) {
                                scrub.moveTo(downX, start.scrollPx, start, seekNow.value)
                            }
                            completed = drag(down.id) { change ->
                                change.consume()
                                maxMovePx = maxOf(maxMovePx, (change.position - down.position).getDistance())
                                val now = inputs.value
                                scrub.moveTo(change.position.x - gutterPx + grabOffset, now.scrollPx, now, seekNow.value)
                            }
                        } finally {
                            scrub.end()
                            scrubEndNow.value()
                        }
                        // Toque simple sobre el icono del cabezal = reproducir. Se decide DESPUÉS de soltar (y de retomar lo que
                        // sonaba), con el instante real en que se levantó el dedo: mantener presionado o arrastrar no reproduce.
                        val upMs = currentEvent.changes.firstOrNull { it.id == down.id }?.uptimeMillis ?: down.uptimeMillis
                        if (detailIsHeadTap(
                                onHead = onHead,
                                completed = completed,
                                maxMovePx = maxMovePx,
                                slopPx = viewConfiguration.touchSlop,
                                pressMs = upMs - down.uptimeMillis,
                                longPressMs = viewConfiguration.longPressTimeoutMillis
                            )
                        ) {
                            playNow.value()
                        }
                    }
                }
        )
    }
}

// ---------------------------------------------------------------------------
// Estado: operaciones
// ---------------------------------------------------------------------------

private fun DetailScrubState.begin(restSourceMs: Double, restX: Float, playheadMs: Long) {
    sourceMs = restSourceMs
    headTargetX = restX
    lastSentProjectMs = playheadMs
    active = true
}

private fun DetailScrubState.end() {
    active = false
}

/**
 * Lleva el cabezal a [headX] (px del eje de tiempo): fija su posición visual y, si el milisegundo del proyecto cambió,
 * mueve el cursor con [seek]. [scrollPx] se pasa aparte porque el autodesplazamiento acaba de cambiarlo y el panel aún
 * no recompone con el valor nuevo.
 */
private fun DetailScrubState.moveTo(headX: Float, scrollPx: Float, inputs: DetailPlayheadInputs, seek: (Long) -> Unit) {
    headTargetX = headX
    val clip = inputs.clip
    val target = detailScrubTarget(
        xPx = headX.coerceIn(0f, inputs.viewportPx),
        scrollPx = scrollPx,
        pxPerMs = inputs.pxPerMs,
        totalMs = inputs.totalMs,
        trimStartMs = clip.trimStartMs,
        timelineStartMs = clip.timelineStartMs,
        clipLengthMs = clip.clipLengthMs
    ) ?: return
    sourceMs = target.sourceMs
    if (target.projectMs != lastSentProjectMs) {
        lastSentProjectMs = target.projectMs
        seek(target.projectMs)
    }
}

// ---------------------------------------------------------------------------
// Dibujo
// ---------------------------------------------------------------------------

private fun DrawScope.drawPlayhead(
    resting: DetailRestingHead?,
    scrub: DetailScrubState,
    unfold: State<Float>,
    playGlyph: State<Float>,
    scrollPx: Float,
    pxPerMs: Float,
    viewportPx: Float,
    gutterPx: Float,
    head: DetailHeadShape,
    measurer: TextMeasurer,
    pillStyle: TextStyle
) {
    if (pxPerMs <= 0f) return
    // Estas lecturas de estado ocurren en el bloque de dibujo: arrastrar repinta solo esta capa.
    val sourceMs: Double = if (scrub.active) scrub.sourceMs else resting?.sourceMs ?: return
    // Cursor fuera del clip: el cabezal sigue ahí (para poder agarrarlo) pero atenuado.
    val headAlpha = if (!scrub.active && resting != null && !resting.insideClip) HEAD_OUTSIDE_CLIP_ALPHA else 1f
    val axisX = detailSourceMsToX(sourceMs, scrollPx, pxPerMs)
    val hw = head.halfWidth
    // Visible mientras su cuerpo asome por el eje (el margen lateral lo deja entero en 0:00 y en el final).
    if (axisX < -hw || axisX > viewportPx + hw) return
    val x = gutterPx + axisX

    val u = unfold.value.coerceIn(0f, 1f)
    val rulerH = DETAIL_RULER_HEIGHT_DP.dp.toPx()
    val headCenterY = rulerH - head.height * 0.5f

    if (u > 0.001f) {
        // Línea guía: nace en la punta del cabezal y se despliega hacia abajo.
        val bottom = rulerH + (size.height - rulerH) * u
        drawLine(GuideHalo, Offset(x, rulerH), Offset(x, bottom), strokeWidth = 7.dp.toPx())
        drawLine(GuideColor, Offset(x, rulerH), Offset(x, bottom), strokeWidth = 1.5f.dp.toPx())
        drawTimePill(x, rulerH, u, formatDetailTimeAt(sourceMs, 1.0), measurer, pillStyle)
        // Resplandor tras el cabezal.
        val glowRadius = 30.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(HeadGlow.copy(alpha = 0.40f * u), Color.Transparent),
                center = Offset(x, headCenterY),
                radius = glowRadius
            ),
            radius = glowRadius,
            center = Offset(x, headCenterY)
        )
    }

    // Cabezal: crece un poco al sostenerlo, anclado en la punta para que no se despegue de la línea.
    val scale = 1f + 0.08f * u
    withTransform({
        translate(left = x, top = rulerH - head.height)
        scale(scale, scale, pivot = Offset(0f, head.height))
    }) {
        drawPath(
            head.path,
            brush = Brush.verticalGradient(listOf(HeadTop, HeadBottom), startY = 0f, endY = head.height),
            alpha = headAlpha
        )
        drawPath(head.path, color = HeadEdge, style = Stroke(width = 1.dp.toPx()), alpha = headAlpha)
        // Brillo en el borde superior.
        val inset = 5.dp.toPx()
        drawLine(
            Color.White.copy(alpha = 0.40f),
            Offset(-hw + inset, 1.5f.dp.toPx()),
            Offset(hw - inset, 1.5f.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
            alpha = headAlpha
        )
        // Glifo de estado, mismo centro y mismo tamaño en ambos estados:
        //  - reproduciendo → ❚❚ (pausa), también invita a arrastrar;
        //  - en pausa      → ▶ (reproducir).
        // Se funden entre sí con `playGlyph` (0 = ❚❚, 1 = ▶), sincronizado con el botón principal.
        val glyph = playGlyph.value.coerceIn(0f, 1f)
        val glyphColor = Color.White.copy(alpha = 0.78f)
        val glyphCenterY = head.height * 0.36f
        val glyphHalfH = head.height * 0.16f

        if (glyph < 0.999f) {
            val barX = 2.5f.dp.toPx()
            val barStroke = 1.2f.dp.toPx()
            val barAlpha = headAlpha * (1f - glyph)
            drawLine(glyphColor, Offset(-barX, glyphCenterY - glyphHalfH), Offset(-barX, glyphCenterY + glyphHalfH), strokeWidth = barStroke, cap = StrokeCap.Round, alpha = barAlpha)
            drawLine(glyphColor, Offset(barX, glyphCenterY - glyphHalfH), Offset(barX, glyphCenterY + glyphHalfH), strokeWidth = barStroke, cap = StrokeCap.Round, alpha = barAlpha)
        }
        if (glyph > 0.001f) {
            // Triángulo isósceles apuntando a la derecha, con esquinas suavizadas (relleno + trazo redondo del
            // mismo color). Se desplaza un poco a la derecha: el centro óptico de un triángulo no es su caja.
            val triH = glyphHalfH * 2f
            val triW = triH * 0.86f
            val opticalShift = triW * 0.10f
            val left = -triW / 2f + opticalShift
            val right = triW / 2f + opticalShift
            val tri = Path().apply {
                moveTo(left, glyphCenterY - glyphHalfH)
                lineTo(right, glyphCenterY)
                lineTo(left, glyphCenterY + glyphHalfH)
                close()
            }
            val triAlpha = headAlpha * glyph
            drawPath(tri, color = glyphColor, alpha = triAlpha)
            drawPath(tri, color = glyphColor, style = Stroke(width = 1.2f.dp.toPx(), join = StrokeJoin.Round), alpha = triAlpha)
        }
    }
}

/** Lectura del tiempo exacto junto a la guía: a la derecha de la línea, o a la izquierda si no cabe. */
private fun DrawScope.drawTimePill(
    lineX: Float,
    rulerH: Float,
    alpha: Float,
    text: String,
    measurer: TextMeasurer,
    style: TextStyle
) {
    // `skipCache`: el texto cambia en cada cuadro; cachearlo solo llenaría la caché con layouts que no se reutilizan.
    val layout = measurer.measure(text = text, style = style, softWrap = false, maxLines = 1, skipCache = true)
    val padX = 7.dp.toPx()
    val padY = 3.dp.toPx()
    val gap = 10.dp.toPx()
    val edge = 6.dp.toPx()
    val pillW = layout.size.width + padX * 2f
    val pillH = layout.size.height + padY * 2f
    var left = lineX + gap
    if (left + pillW > size.width - edge) left = lineX - gap - pillW
    left = left.coerceAtLeast(edge)
    val top = rulerH + 8.dp.toPx()
    val radius = CornerRadius(pillH / 2f, pillH / 2f)
    drawRoundRect(PillBackground, Offset(left, top), Size(pillW, pillH), radius, alpha = alpha)
    drawRoundRect(PillBorder, Offset(left, top), Size(pillW, pillH), radius, style = Stroke(width = 1.dp.toPx()), alpha = alpha)
    drawText(layout, topLeft = Offset(left + padX, top + padY), alpha = alpha)
}
