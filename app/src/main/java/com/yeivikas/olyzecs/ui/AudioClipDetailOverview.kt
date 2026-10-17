package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.engine.audio.AudioWaveform

private val OverviewTop = Color(0xFF1B1B24)
private val OverviewBottom = Color(0xFF121218)
private val OverviewDim = Color.Black.copy(alpha = 0.52f)
private val OverviewWindowFill = Color.White.copy(alpha = 0.09f)
private val OverviewSelectionFill = Color.White.copy(alpha = 0.22f)
private val OverviewPlayhead = Color(0xFFFF4D5A)
private val OverviewGrip = Color.White.copy(alpha = 0.88f)

/** Margen vertical (dp) entre el borde de la barra y la onda / el recuadro. */
private const val OVERVIEW_VERTICAL_PADDING_DP = 6

/** Ancho (dp) mínimo con que se dibuja el recuadro (con zoom muy alto su ancho real sería de pocos píxeles). */
private const val OVERVIEW_MIN_DRAWN_WINDOW_DP = 6

private const val OVERVIEW_GRIP_WIDTH_DP = 4
private const val OVERVIEW_GRIP_HEIGHT_DP = 22

/**
 * Barra de vista general al pie de la ventana ampliada del audio (referencia: la ventana superior de WavePad, cuyo
 * manual la define así: «muestra la forma de onda del archivo siempre en toda su longitud» y «se puede tocar para saltar
 * rápidamente a otra zona»; la ventana inferior muestra el zoom). Aquí va debajo de la onda grande:
 *
 *  - Dibuja el archivo ENTERO reducido al ancho del eje de tiempo (la X de la barra coincide con la de la onda a 1×)
 *    y, sobre él, un **recuadro** con la parte que muestra la onda grande. Su ancho es `ancho de la ventana ÷ zoom`: se
 *    estrecha al acercar y se ensancha al alejar, y se mueve con el desplazamiento — con el pellizco, los dos dedos, la
 *    inercia, el doble toque y el cabezal, porque lee el mismo estado.
 *  - Las agarraderas de las aristas van FUERA del marco, pegadas a él (en el margen lateral cuando el recuadro llega al
 *    extremo del archivo), para no tapar la onda enmarcada.
 *  - Lo de fuera del recuadro se oscurece. Se marcan además la **selección** (banda clara) y el **cabezal** (línea roja).
 *  - **Gestos con un solo dedo** (esa es su razón de ser: en la onda grande un dedo selecciona y desplazar exige dos):
 *    arrastrar el recuadro lo desplaza; tocar fuera lo centra ahí (y seguir arrastrando continúa desde ahí); arrastrar
 *    un borde acerca o aleja manteniendo fija la otra arista (zoom). Nada de esto mueve el cabezal ni toca la selección.
 *
 * No guarda estado: [zoom] y [scrollPx] son los de la ventana principal; los cambios salen por [onViewChange]. Los
 * gestos leen siempre el valor vigente, así que no hay cierres obsoletos aunque el estado se recree (otro archivo).
 *
 * @param waveform datos gruesos del archivo (ya invertidos si el clip está en reversa), o `null` mientras cargan: solo
 *   se ve el eje central.
 * @param viewportPx ancho del eje de tiempo (sin los márgenes); @param gutterPx margen lateral (mismo que la onda).
 * @param maxZoom zoom máximo de la ventana principal: limita el redimensionado del recuadro.
 * @param playheadSourceMs posición del cabezal en el archivo, o `null` si el clip no tiene tramo audible;
 *   @param playheadInsideClip `false` si el cursor del proyecto está fuera del clip (se dibuja atenuado, como en la regla).
 * @param onGestureStart se llama al tocar la barra: la ventana cancela la inercia o la animación de zoom en curso.
 * @param onViewChange nuevo zoom y desplazamiento (px) pedidos por el gesto.
 */
@Composable
internal fun AudioClipDetailOverview(
    waveform: AudioWaveform?,
    totalMs: Double,
    zoom: Float,
    scrollPx: Float,
    viewportPx: Float,
    gutterPx: Float,
    maxZoom: Float,
    selection: AudioClipDetailSelectionState,
    playheadSourceMs: Double?,
    playheadInsideClip: Boolean,
    accent: Color,
    onGestureStart: () -> Unit,
    onViewChange: (zoom: Float, scrollPx: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentZoom by rememberUpdatedState(zoom)
    val currentScrollPx by rememberUpdatedState(scrollPx)
    val currentViewportPx by rememberUpdatedState(viewportPx)
    val currentMaxZoom by rememberUpdatedState(maxZoom)
    val currentOnGestureStart by rememberUpdatedState(onGestureStart)
    val currentOnViewChange by rememberUpdatedState(onViewChange)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DETAIL_OVERVIEW_HEIGHT_DP.dp)
            .background(Brush.verticalGradient(listOf(OverviewTop, OverviewBottom)))
            .semantics {
                contentDescription =
                    "Vista general del audio. Arrastra el recuadro para desplazar, toca para ir a una zona, " +
                        "arrastra un borde para acercar o alejar"
            }
            .pointerInput(gutterPx) {
                val edgeZonePx = DETAIL_OVERVIEW_EDGE_ZONE_DP.dp.toPx()
                val minGrabPx = DETAIL_OVERVIEW_MIN_GRAB_DP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    currentOnGestureStart()
                    // El recuadro y el zoom al EMPEZAR fijan el gesto: la arista opuesta (o el ancho) no deriva.
                    val startZoom = currentZoom
                    val viewport = currentViewportPx
                    val window = detailOverviewWindow(currentScrollPx, viewport, startZoom)
                        ?: return@awaitEachGesture
                    val grab = detailOverviewGrab(
                        downX = down.position.x - gutterPx,
                        window = window,
                        edgeZonePx = edgeZonePx,
                        minGrabPx = minGrabPx,
                        canResize = currentMaxZoom > DETAIL_MIN_ZOOM + 0.01f
                    )
                    fun apply(x: Float) {
                        val axisX = x - gutterPx
                        if (grab.target == DetailOverviewTarget.BODY) {
                            currentOnViewChange(
                                startZoom,
                                detailOverviewMoveScrollPx(axisX, grab.offsetPx, viewport, startZoom)
                            )
                        } else {
                            val view = detailOverviewResize(grab.target, axisX, grab.offsetPx, window, viewport, currentMaxZoom)
                            currentOnViewChange(view.zoom, view.scrollPx)
                        }
                    }
                    apply(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        apply(change.position.x)
                    }
                }
            }
    ) {
        // Divisor superior.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.09f))
        )
        // El archivo entero, reducido al ancho del eje (misma X que la onda grande a 1×).
        val pxPerMs = if (viewportPx > 0f && totalMs > 0.0) (viewportPx / totalMs).toFloat() else 0f
        AudioWaveformCanvas(
            waveform = waveform,
            trimStartMs = 0.0,
            pxPerMs = pxPerMs,
            signalColor = accent,
            dimmed = false,
            // Misma escala que la onda grande (amplitud real y lineal): la vista general es ESA señal en pequeño; con la
            // vista realzada del carril un audio bajo parecía a tope aquí y bajo allí.
            trueAmplitude = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = DETAIL_TIME_GUTTER_DP.dp, vertical = OVERVIEW_VERTICAL_PADDING_DP.dp)
        )
        // Recuadro, selección y cabezal: capa propia, para que repintarlos no obligue a re-grabar la onda.
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            if (viewportPx <= 0f || totalMs <= 0.0) return@Canvas
            val top = OVERVIEW_VERTICAL_PADDING_DP.dp.toPx()
            val height = (size.height - 2f * top).coerceAtLeast(0f)

            // Selección (la ve también la regla grande): banda clara bajo todo lo demás.
            selection.range?.let { range ->
                val l = detailOverviewX(range.startMs, totalMs, viewportPx)
                val r = detailOverviewX(range.endMs, totalMs, viewportPx)
                if (r > l) drawRect(OverviewSelectionFill, Offset(gutterPx + l, top), Size(r - l, height))
            }

            val window = detailOverviewWindow(scrollPx, viewportPx, zoom)
            if (window != null) {
                val minDrawn = OVERVIEW_MIN_DRAWN_WINDOW_DP.dp.toPx()
                val left = window.leftPx.coerceAtMost(viewportPx - minDrawn)
                val right = maxOf(window.rightPx, left + minDrawn)
                // Lo que la onda grande NO muestra, oscurecido.
                if (left > 0f) drawRect(OverviewDim, Offset(gutterPx, top), Size(left, height))
                if (right < viewportPx) drawRect(OverviewDim, Offset(gutterPx + right, top), Size(viewportPx - right, height))
                // Recuadro: relleno tenue y marco del color de la capa.
                drawRect(OverviewWindowFill, Offset(gutterPx + left, top), Size(right - left, height))
                val stroke = 1.5.dp.toPx()
                drawRect(
                    color = accent.copy(alpha = 0.92f),
                    topLeft = Offset(gutterPx + left + stroke / 2f, top + stroke / 2f),
                    size = Size((right - left - stroke).coerceAtLeast(0f), (height - stroke).coerceAtLeast(0f)),
                    style = Stroke(width = stroke)
                )
                // Agarraderas de las aristas, FUERA del marco y pegadas a él (ver detailOverviewGripLefts), solo si se
                // pueden arrastrar (hay zoom que cambiar y cabe el cuerpo).
                val canResize = maxZoom > DETAIL_MIN_ZOOM + 0.01f && window.widthPx >= 3f * DETAIL_OVERVIEW_EDGE_ZONE_DP.dp.toPx()
                if (canResize) {
                    val gripW = OVERVIEW_GRIP_WIDTH_DP.dp.toPx()
                    val gripH = minOf(OVERVIEW_GRIP_HEIGHT_DP.dp.toPx(), height)
                    val gripY = top + (height - gripH) / 2f
                    val radius = CornerRadius(gripW / 2f, gripW / 2f)
                    val grips = detailOverviewGripLefts(left, right, gripW, viewportPx, gutterPx)
                    drawRoundRect(OverviewGrip, Offset(gutterPx + grips.startLeftPx, gripY), Size(gripW, gripH), radius)
                    drawRoundRect(OverviewGrip, Offset(gutterPx + grips.endLeftPx, gripY), Size(gripW, gripH), radius)
                }
            }

            // Cabezal: línea fina (atenuada si el cursor está fuera del clip), por encima de todo.
            if (playheadSourceMs != null) {
                val x = gutterPx + detailOverviewX(playheadSourceMs, totalMs, viewportPx)
                drawLine(
                    color = OverviewPlayhead.copy(alpha = if (playheadInsideClip) 0.95f else 0.45f),
                    start = Offset(x, top),
                    end = Offset(x, top + height),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}
