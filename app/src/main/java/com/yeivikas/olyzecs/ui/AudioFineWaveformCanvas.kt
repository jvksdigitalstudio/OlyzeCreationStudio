package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.engine.audio.FineWaveform
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Cuántas columnas como máximo se calculan por cuadro (1 por píxel; con pantallas más anchas cada columna abarca más). */
private const val MAX_FINE_COLUMNS = 4096

/** Fracción de la media altura que ocupa la escala completa (0 dBFS) con zoom vertical 1×. */
private const val FINE_HEIGHT_FRACTION = 0.92f

/** A partir de cuántos píxeles por muestra se dibuja la señal como curva muestra a muestra (en vez de columnas). */
private const val SAMPLE_MODE_MIN_PX_PER_FRAME = 2.5f

/** A partir de cuántos píxeles por muestra se dibuja además el palito hasta el eje. */
private const val SAMPLE_STEM_MIN_PX_PER_FRAME = 6f

/** A partir de cuántos píxeles por muestra se marca cada muestra con un punto. */
private const val SAMPLE_DOT_MIN_PX_PER_FRAME = 10f

private const val MIN_FINE_HALF_HEIGHT_PX = 0.5f

/** Líneas de la escala de amplitud: nivel lineal (0..1 de escala completa) y su rótulo en dBFS. */
private val FINE_DB_LINES = listOf(
    1.0f to "0 dB",
    0.5012f to "-6",
    0.2512f to "-12"
)

private class FineColumnBuffers {
    val min = FloatArray(MAX_FINE_COLUMNS)
    val max = FloatArray(MAX_FINE_COLUMNS)
    val rms = FloatArray(MAX_FINE_COLUMNS)
}

/**
 * Dibuja la señal ORIGINAL de [fine] (amplitud real, lineal, con signo) en la
 * ventana ampliada del clip. A diferencia de `AudioWaveformCanvas` (la del carril,
 * normalizada y con curva de realce), acá cada columna usa los mínimos/máximos
 * reales de las muestras que cubre (ver [FineWaveform]) y el desplazamiento es
 * continuo (en ms con decimales, sin saltos aunque el zoom llegue a nivel de muestra).
 *
 * Modos según la escala:
 *  - **Columnas** (< [SAMPLE_MODE_MIN_PX_PER_FRAME] px por muestra): silueta mín/máx
 *    translúcida + núcleo RMS más intenso, como la onda del carril pero con datos reales.
 *  - **Muestra a muestra** (≥ [SAMPLE_MODE_MIN_PX_PER_FRAME] px/muestra): curva que une
 *    cada muestra, con palitos hasta el eje y puntos al ampliar más (referencia: los
 *    editores de audio profesionales al zoom máximo).
 *
 * También dibuja la escala de amplitud (0 / −6 / −12 dBFS) y el eje central.
 *
 * @param scrollMs instante del archivo (ms, con decimales) en el borde izquierdo.
 *   Con [reversed], en tiempo del archivo invertido (el que usa el recorte del clip).
 * @param ampZoom zoom vertical (1× = escala completa al 92 % de la media altura).
 */
@Composable
internal fun AudioFineWaveformCanvas(
    fine: FineWaveform,
    reversed: Boolean,
    scrollMs: Double,
    pxPerMs: Float,
    ampZoom: Float,
    signalColor: Color,
    dimmed: Boolean,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp) }
    val buffers = remember { FineColumnBuffers() }

    Canvas(modifier = modifier) {
        if (pxPerMs <= 0f || size.width < 1f || size.height < 1f) return@Canvas
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val maxHalf = centerY * FINE_HEIGHT_FRACTION * ampZoom
        val alpha = if (dimmed) 0.40f else 1f
        val frames = fine.frames
        val framesPerMs = fine.sampleRateHz / 1000.0
        val startFrame = scrollMs * framesPerMs
        val pxPerFrame = (pxPerMs / framesPerMs).toFloat()

        // --- Escala de amplitud (dBFS) ---
        for ((level, label) in FINE_DB_LINES) {
            val dy = level * maxHalf
            val yUp = centerY - dy
            val yDown = centerY + dy
            if (yUp in 0f..h) drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, yUp), Offset(w, yUp), strokeWidth = 1f)
            if (yDown in 0f..h) drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, yDown), Offset(w, yDown), strokeWidth = 1f)
            if (yUp in 16f..(h - 16f)) {
                drawText(
                    textMeasurer = measurer,
                    text = label,
                    style = labelStyle,
                    topLeft = Offset(4f, yUp + 2f),
                    softWrap = false
                )
            }
        }
        // --- Eje central ---
        drawLine(signalColor.copy(alpha = 0.22f * alpha), Offset(0f, centerY), Offset(w, centerY), strokeWidth = 1f)

        if (pxPerFrame >= SAMPLE_MODE_MIN_PX_PER_FRAME) {
            // ---------- Muestra a muestra ----------
            val firstF = floor(startFrame).toInt() - 1
            val lastF = ceil(startFrame + w / pxPerFrame).toInt() + 1
            val curve = Path()
            var started = false
            for (f in firstF..lastF) {
                val srcIndex = if (reversed) frames - 1 - f else f
                val v = fine.sampleAt(srcIndex)
                val x = ((f - startFrame) * pxPerFrame).toFloat()
                val y = centerY - v * maxHalf
                if (!started) {
                    curve.moveTo(x, y)
                    started = true
                } else {
                    curve.lineTo(x, y)
                }
                if (pxPerFrame >= SAMPLE_STEM_MIN_PX_PER_FRAME && f in 0 until frames) {
                    drawLine(signalColor.copy(alpha = 0.35f * alpha), Offset(x, centerY), Offset(x, y), strokeWidth = 1.5f)
                }
                if (pxPerFrame >= SAMPLE_DOT_MIN_PX_PER_FRAME && f in 0 until frames) {
                    drawCircle(signalColor.copy(alpha = alpha), radius = 3.5f, center = Offset(x, y))
                }
            }
            drawPath(curve, signalColor.copy(alpha = 0.96f * alpha), style = Stroke(width = 2f))
        } else {
            // ---------- Columnas con mín/máx/RMS reales ----------
            val columns = ceil(w).toInt().coerceIn(1, MAX_FINE_COLUMNS)
            val step = w / columns
            val framesPerColumn = (step / pxPerFrame).toDouble()
            for (c in 0 until columns) {
                val s = startFrame + c * framesPerColumn
                val e = s + framesPerColumn
                if (reversed) {
                    fine.sampleColumn(frames - e, frames - s, c, buffers.min, buffers.max, buffers.rms)
                } else {
                    fine.sampleColumn(s, e, c, buffers.min, buffers.max, buffers.rms)
                }
            }
            val peak = Path()
            for (c in 0 until columns) {
                val x = c * step + step / 2f
                var top = centerY - buffers.max[c] * maxHalf
                var bottom = centerY - buffers.min[c] * maxHalf
                if (bottom - top < 1f) {
                    val mid = (top + bottom) / 2f
                    top = mid - 0.5f
                    bottom = mid + 0.5f
                }
                if (c == 0) peak.moveTo(x, top) else peak.lineTo(x, top)
            }
            for (c in columns - 1 downTo 0) {
                val x = c * step + step / 2f
                var top = centerY - buffers.max[c] * maxHalf
                var bottom = centerY - buffers.min[c] * maxHalf
                if (bottom - top < 1f) {
                    val mid = (top + bottom) / 2f
                    top = mid - 0.5f
                    bottom = mid + 0.5f
                }
                peak.lineTo(x, bottom)
            }
            peak.close()

            val core = Path()
            for (c in 0 until columns) {
                val x = c * step + step / 2f
                val half = max(MIN_FINE_HALF_HEIGHT_PX, min(buffers.rms[c] * maxHalf, maxHalf))
                if (c == 0) core.moveTo(x, centerY - half) else core.lineTo(x, centerY - half)
            }
            for (c in columns - 1 downTo 0) {
                val x = c * step + step / 2f
                val half = max(MIN_FINE_HALF_HEIGHT_PX, min(buffers.rms[c] * maxHalf, maxHalf))
                core.lineTo(x, centerY + half)
            }
            core.close()

            drawPath(peak, signalColor.copy(alpha = 0.50f * alpha))
            drawPath(core, signalColor.copy(alpha = 0.96f * alpha))
        }
    }
}
