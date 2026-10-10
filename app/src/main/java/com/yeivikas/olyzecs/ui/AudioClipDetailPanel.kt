package com.yeivikas.olyzecs.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.AudioWaveform
import com.yeivikas.olyzecs.engine.audio.AudioWaveformAnalyzer
import com.yeivikas.olyzecs.engine.audio.FineWaveform
import com.yeivikas.olyzecs.engine.audio.audibleSourceRangeMs
import com.yeivikas.olyzecs.engine.audio.sourcePositionAt
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val DetailBackground = Color(0xFF101010)
private val DetailGridColor = Color.White.copy(alpha = 0.08f)
private val DetailGridLabelColor = Color.White.copy(alpha = 0.45f)
private val DetailPlayheadColor = Color.White
private val DetailOutsideClipShade = Color.Black.copy(alpha = 0.55f)
private const val DETAIL_GRID_MIN_SPACING_PX = 96f

/** Ancho mínimo (px) que debe quedar a la derecha de una línea de la cuadrícula para dibujar su etiqueta. */
private const val DETAIL_GRID_LABEL_MIN_WIDTH_PX = 12f

/** Píxeles por muestra a los que llega el zoom máximo con datos finos / con la forma de onda gruesa del carril. */
private const val DETAIL_FINE_PX_PER_SAMPLE_AT_MAX = 14f
private const val DETAIL_COARSE_PX_PER_BUCKET_AT_MAX = 6f

/** Velocidad mínima (px/s) para que soltar un arrastre dispare inercia. */
private const val DETAIL_FLING_MIN_VELOCITY = 250f

/** Alto (dp) de la franja de los bordes donde se reclaman los gestos al sistema (límite de Android: 200 dp por borde). */
private const val DETAIL_GESTURE_EXCLUSION_HEIGHT_DP = 200

/** Guarda el trabajo (inercia / animación de zoom) en curso para poder cancelarlo al tocar de nuevo. */
private class DetailMotion {
    var job: Job? = null
}

/**
 * Ventana ampliada de UN clip de audio (referencia: FL Studio Mobile, doble
 * toque sobre la onda de un clip). Ocupa el área de capas/carriles del
 * timeline —la regla, el Master y la barra inferior quedan visibles— y muestra
 * la SEÑAL ORIGINAL del archivo a todo el ancho y alto, con zoom de nivel
 * profesional.
 *
 * ## Datos
 *  - **Forma de onda fina** ([FineWaveform]): muestras reales + pirámide de picos,
 *    cargada al abrir la ventana. Permite llegar hasta nivel de muestra con picos
 *    exactos (sin bloques ni desaparición de transitorios). Mientras carga (y si el
 *    audio es muy largo para conservarla) se usa la forma de onda del carril, con un
 *    zoom máximo acorde a su resolución.
 *  - Siempre el archivo COMPLETO, una sola vez, sin las repeticiones del loop ni el
 *    recorte del clip; lo que queda fuera del tramo que suena se ve atenuado. Con
 *    Reverse se ve invertida (el recorte se mide sobre esa versión).
 *
 * ## Gestos (sobre la onda)
 *  - **Pellizco horizontal:** zoom de tiempo anclado a los dedos, hasta nivel de muestra.
 *  - **Pellizco vertical:** zoom de amplitud (1×–[DETAIL_MAX_AMP_ZOOM]×).
 *  - **Arrastre:** desplaza la vista, con inercia al soltar.
 *  - **Tocar:** mueve el cursor a ese punto (si suena en el clip).
 *  - **Doble toque:** zoom animado hacia ese punto ([DETAIL_DOUBLE_TAP_ZOOM]×) o, si ya
 *    hay zoom, vuelve animado al archivo completo.
 *
 * ## Robustez
 *  - Reclama al sistema los gestos de borde en una franja central de cada lado
 *    (`systemGestureExclusion`): pellizcar con los pulgares cerca de los bordes ya no
 *    dispara el gesto "atrás" de Android (que cerraba la ventana y luego el editor).
 *  - Árbitro de "atrás" ([GuardedBackHandler] + `MultiTouchBackGuard`): un "atrás" que llega
 *    dentro de 2 s de un pellizco se ignora (la ventana NO se cierra ni se sale del editor).
 *    Cubre los bordes que `systemGestureExclusion` no puede reclamar (límite de 200 dp por borde)
 *    y los gestos "atrás" repetidos de un pellizco largo.
 *  - Su propio `BackHandler` se registra al abrir la ventana, así que siempre tiene
 *    prioridad sobre el del editor: "atrás" cierra primero la ventana ([onBackClose]).
 *
 * Es solo de LECTURA: no modifica el clip ni el proyecto (salvo mover el cursor), así
 * que cerrarla deja todo como estaba.
 *
 * @param signalColor color de la señal (el mismo del clip en el carril).
 * @param playheadMs cursor del proyecto (ms); se dibuja si cae dentro del clip.
 * @param onSeek mueve el cursor del proyecto a ese instante (ms de proyecto).
 * @param onClose cierra la ventana (botón X).
 * @param onBackClose cierra la ventana por "atrás" del sistema (el llamador puede además
 *   tragarse un "atrás" residual).
 */
@Composable
internal fun AudioClipDetailPanel(
    clip: AudioClip,
    signalColor: Color,
    playheadMs: Long,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onBackClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Registrado al abrir la ventana => es el último en el despachador => tiene prioridad.
    GuardedBackHandler(onBack = onBackClose)

    val appContext = LocalContext.current.applicationContext
    val waveform by produceState<AudioWaveform?>(initialValue = null, clip.sourceUri, clip.sourceDurationMs, clip.reversed) {
        value = null
        value = AudioWaveformAnalyzer.load(appContext, clip.sourceUri, clip.sourceDurationMs)
            ?.let { if (clip.reversed) it.reversedCopy() else it }
    }
    val fine by produceState<FineWaveform?>(initialValue = null, clip.sourceUri, clip.sourceDurationMs) {
        value = null
        value = AudioWaveformAnalyzer.loadFine(appContext, clip.sourceUri, clip.sourceDurationMs)
    }

    var zoom by remember(clip.id) { mutableFloatStateOf(DETAIL_MIN_ZOOM) }
    var scrollPx by remember(clip.id) { mutableFloatStateOf(0f) }
    var ampZoom by remember(clip.id) { mutableFloatStateOf(DETAIL_MIN_AMP_ZOOM) }
    val scope = rememberCoroutineScope()
    val motion = remember { DetailMotion() }
    val currentClip by rememberUpdatedState(clip)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .background(DetailBackground)
            .clipToBounds()
            .systemGestureExclusion { coordinates ->
                val bandPx = with(density) { DETAIL_GESTURE_EXCLUSION_HEIGHT_DP.dp.toPx() }
                val heightPx = coordinates.size.height.toFloat()
                val bandHeight = minOf(bandPx, heightPx)
                val top = (heightPx - bandHeight) / 2f
                Rect(0f, top, coordinates.size.width.toFloat(), top + bandHeight)
            }
    ) {
        val viewportPx = with(density) { maxWidth.toPx() }
        val fineNow = fine
        val waveformNow = waveform
        // Duración del ARCHIVO completo: es lo que ocupa el ancho a 1×.
        val totalMs: Double = when {
            fineNow != null -> fineNow.durationMs
            clip.sourceDurationMs > 0L -> clip.sourceDurationMs.toDouble()
            else -> clip.clipLengthMs.coerceAtLeast(1L).toDouble()
        }
        // Zoom máximo = el que deja una muestra (o una cubeta, con la onda gruesa) en unos píxeles.
        val maxZoom = when {
            fineNow != null ->
                detailMaxZoom(totalMs, viewportPx, fineNow.sampleRateHz.toDouble(), DETAIL_FINE_PX_PER_SAMPLE_AT_MAX)
            waveformNow != null && waveformNow.bucketDurationMs > 0.0 ->
                detailMaxZoom(totalMs, viewportPx, 1_000.0 / waveformNow.bucketDurationMs, DETAIL_COARSE_PX_PER_BUCKET_AT_MAX)
            else -> detailMaxZoom(totalMs, viewportPx, 200.0, DETAIL_COARSE_PX_PER_BUCKET_AT_MAX)
        }
        val currentMaxZoom by rememberUpdatedState(maxZoom)
        val currentTotalMs by rememberUpdatedState(totalMs)

        val pxPerMs = if (viewportPx > 0f && totalMs > 0.0) (viewportPx * zoom / totalMs).toFloat() else 0f
        val scrollMs: Double = if (pxPerMs > 0f) scrollPx / pxPerMs.toDouble() else 0.0
        val measurer = rememberTextMeasurer()
        val labelStyle = remember { TextStyle(color = DetailGridLabelColor, fontSize = 10.sp) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Toques: tocar mueve el cursor; doble toque anima el zoom.
                .pointerInput(clip.id, viewportPx) {
                    detectTapGestures(
                        onTap = { offset ->
                            val c = currentClip
                            val ppm = if (viewportPx > 0f && currentTotalMs > 0.0) (viewportPx * zoom / currentTotalMs).toFloat() else 0f
                            detailClipMsAtX(offset.x, scrollPx, ppm, currentTotalMs.toLong())
                                ?.let { sourceMs -> detailProjectMsForSourceMs(sourceMs, c.trimStartMs, c.timelineStartMs, c.clipLengthMs) }
                                ?.let { projectMs -> currentOnSeek(projectMs) }
                        },
                        onDoubleTap = { offset ->
                            motion.job?.cancel()
                            val startZoom = zoom
                            val zoomingOut = startZoom > 1.05f
                            val target = if (zoomingOut) DETAIL_MIN_ZOOM else minOf(DETAIL_DOUBLE_TAP_ZOOM, currentMaxZoom)
                            if (zoomingOut) ampZoom = DETAIL_MIN_AMP_ZOOM
                            motion.job = scope.launch {
                                var last = startZoom
                                Animatable(0f).animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing)) {
                                    val z = detailInterpolateZoom(startZoom, target, value)
                                    scrollPx = detailScrollAfterZoom(scrollPx, last, z, offset.x, 0f, viewportPx)
                                    last = z
                                    zoom = z
                                }
                            }
                        }
                    )
                }
                // Arrastre, pellizco (tiempo / amplitud) e inercia.
                .pointerInput(clip.id, viewportPx) {
                    detectDetailGestures(
                        onStart = {
                            motion.job?.cancel()
                            motion.job = null
                        },
                        onTransform = { centroidX, panX, zoomX, zoomY ->
                            val oldZoom = zoom
                            val newZoom = (oldZoom * zoomX).coerceIn(DETAIL_MIN_ZOOM, currentMaxZoom)
                            scrollPx = detailScrollAfterZoom(scrollPx, oldZoom, newZoom, centroidX, panX, viewportPx)
                            zoom = newZoom
                            if (zoomY != 1f) {
                                ampZoom = (ampZoom * zoomY).coerceIn(DETAIL_MIN_AMP_ZOOM, DETAIL_MAX_AMP_ZOOM)
                            }
                        },
                        onEnd = { velocityX ->
                            if (abs(velocityX) > DETAIL_FLING_MIN_VELOCITY && zoom > DETAIL_MIN_ZOOM + 0.01f) {
                                val maxScroll = (viewportPx * zoom - viewportPx).coerceAtLeast(0f)
                                motion.job = scope.launch {
                                    val scroller = Animatable(scrollPx)
                                    scroller.updateBounds(0f, maxScroll)
                                    // El contenido sigue al dedo: dedo a la derecha => el scroll disminuye.
                                    scroller.animateDecay(-velocityX, exponentialDecay<Float>()) {
                                        scrollPx = value
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            // Señal ORIGINAL: archivo completo, sin loop, desde el inicio del archivo.
            if (fineNow != null) {
                AudioFineWaveformCanvas(
                    fine = fineNow,
                    reversed = clip.reversed,
                    scrollMs = scrollMs,
                    pxPerMs = pxPerMs,
                    ampZoom = ampZoom,
                    signalColor = signalColor,
                    dimmed = clip.muted,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Mientras carga la onda fina (o si el audio es muy largo): la onda del carril.
                AudioWaveformCanvas(
                    waveform = waveformNow,
                    trimStartMs = scrollMs.toLong(),
                    pxPerMs = pxPerMs,
                    signalColor = signalColor,
                    dimmed = clip.muted,
                    loop = false,
                    trueAmplitude = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Tramo usado por el clip, cuadrícula de tiempo y cursor.
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (pxPerMs <= 0f) return@Canvas

                // Lo que queda fuera del tramo que suena en el clip, atenuado.
                val (audibleStartMs, audibleEndMs) =
                    audibleSourceRangeMs(clip.sourceDurationMs, clip.trimStartMs, clip.loop, clip.clipLengthMs)
                if (clip.sourceDurationMs > 0L) {
                    val startX = (audibleStartMs * pxPerMs - scrollPx).coerceIn(0f, size.width)
                    val endX = (audibleEndMs * pxPerMs - scrollPx).coerceIn(0f, size.width)
                    if (startX > 0f) drawRect(DetailOutsideClipShade, Offset.Zero, Size(startX, size.height))
                    if (endX < size.width) drawRect(DetailOutsideClipShade, Offset(endX, 0f), Size(size.width - endX, size.height))
                }

                val stepMs = detailGridStepMs(pxPerMs, DETAIL_GRID_MIN_SPACING_PX)
                var index = floor(scrollMs / stepMs).toLong()
                while (true) {
                    val t = index * stepMs
                    val x = ((t - scrollMs) * pxPerMs).toFloat()
                    if (x > size.width || t > totalMs) break
                    if (x >= 0f) {
                        drawLine(DetailGridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        // Ancho disponible a la derecha de la línea. `drawText` sin `size` mide con
                        // `ancho_del_canvas - topLeft.x`: si la línea cae a menos de 4 px del borde
                        // derecho eso es NEGATIVO y la medición lanza IllegalArgumentException
                        // (crash en mitad del gesto, según dónde caiga cada línea de la cuadrícula
                        // al cambiar el zoom/scroll). Por eso se pasa un `size` explícito y se
                        // omite la etiqueta si no cabe nada legible.
                        val labelWidth = size.width - (x + 4f)
                        if (labelWidth >= DETAIL_GRID_LABEL_MIN_WIDTH_PX) {
                            drawText(
                                textMeasurer = measurer,
                                text = formatDetailTimeAt(t, stepMs),
                                style = labelStyle,
                                topLeft = Offset(x + 4f, 2f),
                                // Sin salto de línea: junto al borde derecho la etiqueta se cortaba letra por letra en vertical.
                                softWrap = false,
                                size = Size(labelWidth, (size.height - 2f).coerceAtLeast(1f))
                            )
                        }
                    }
                    index++
                }

                // Cursor: solo si cae dentro del clip (y su posición en el archivo).
                if (playheadMs >= clip.timelineStartMs && playheadMs <= clip.timelineStartMs + clip.clipLengthMs) {
                    clip.sourcePositionAt(playheadMs)?.let { sourceMs ->
                        val x = sourceMs * pxPerMs - scrollPx
                        if (x in 0f..size.width) {
                            drawLine(DetailPlayheadColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
                        }
                    }
                }
            }
        }

        // Nombre y datos, pequeños y translúcidos (abajo a la izquierda).
        Text(
            text = buildString {
                append(clip.displayName)
                append(" · ")
                append(formatDetailTimeMs(totalMs.toLong(), withMillis = true))
                if (clip.loop) append(" · Loop")
                if (clip.reversed) append(" · Reverse")
                if (clip.muted) append(" · Silenciado")
                append(" · ")
                append(if (zoom < 10f) "%.1f".format(zoom) else "%.0f".format(zoom))
                append("×")
                if (ampZoom > 1.05f) append(" · Amp %.1f×".format(ampZoom))
            },
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 6.dp, end = 64.dp)
        )

        // Cerrar: icono premium (círculo rojo con X calada), chico, abajo de la fila de
        // etiquetas de tiempo para no taparlas. El área táctil es de 40 dp; el dibujo, de 28 dp.
        // La X es un hueco del vector: se apoya sobre un disco blanco para que se vea blanca
        // y no deje pasar la onda de atrás.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 2.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Image(
                painter = painterResource(id = R.drawable.ic_close_premium),
                contentDescription = "Cerrar ventana del audio",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
