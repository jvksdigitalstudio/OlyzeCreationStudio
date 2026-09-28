package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.yeivikas.olyzecs.engine.audio.AudioWaveform
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

/**
 * Máximo de columnas de muestreo por clip. Un clip ancho (zoom alto) se
 * dibuja con columnas de más de 1 px en vez de miles de puntos: el ojo no
 * distingue la diferencia y el costo del `Path` queda acotado.
 */
private const val MAX_WAVEFORM_COLUMNS = 2400

/**
 * Curva de percepción aplicada a las amplitudes normalizadas (0..1).
 * < 1 levanta los niveles bajos: los pasajes suaves siguen siendo visibles
 * al lado de los picos, que es justo el "rango dinámico" que se quiere ver.
 * Es una curva de DISPLAY: no toca el audio ni el mezclado.
 */
private const val PEAK_DISPLAY_GAMMA = 0.80f
private const val RMS_DISPLAY_GAMMA = 0.70f

/** Fracción de la altura útil que ocupa la señal, para dejar un respiro arriba/abajo. */
private const val WAVEFORM_HEIGHT_FRACTION = 0.92f

/** Mitad de alto mínima (px): el silencio se ve como una línea fina, no como un hueco. */
private const val MIN_HALF_HEIGHT_PX = 0.5f

private class WaveformShapes(val peak: Path, val core: Path)

/**
 * Dibuja la forma de onda de un clip de audio al estilo de un DAW
 * profesional (FL Studio / Logic / Ableton): silueta espejada respecto al
 * eje central, con dos capas — el contorno de PICO (translúcido) y, encima,
 * el núcleo RMS (opaco y brillante).
 *
 * Es agnóstico al arrastre: la posición horizontal del clip la maneja el
 * padre con `offset`; esta vista solo depende de su propio tamaño, del
 * zoom ([pxPerMs]) y del recorte ([trimStartMs]) — así arrastrar el clip
 * NO recalcula la geometría, solo cambiar zoom/recorte/ancho.
 *
 * @param waveform datos ya calculados, o `null` mientras se decodifica (o
 *   si el archivo no se pudo leer): se muestra solo la línea central.
 * @param trimStartMs punto del archivo donde arranca el clip (el borde
 *   izquierdo de esta vista corresponde a ese instante).
 * @param pxPerMs escala del timeline: cuántos píxeles ocupa un ms.
 * @param signalColor color base de la señal (ya aclarado por el llamador).
 * @param dimmed atenúa toda la señal (audio silenciado).
 */
@Composable
internal fun AudioWaveformCanvas(
    waveform: AudioWaveform?,
    trimStartMs: Long,
    pxPerMs: Float,
    signalColor: Color,
    dimmed: Boolean,
    modifier: Modifier = Modifier
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    val shapes = remember(waveform, trimStartMs, pxPerMs, sizePx) {
        buildWaveformShapes(waveform, trimStartMs, pxPerMs, sizePx.width.toFloat(), sizePx.height.toFloat())
    }
    val alpha = if (dimmed) 0.40f else 1f

    Canvas(modifier = modifier.onSizeChanged { sizePx = it }) {
        val centerY = size.height / 2f
        // Línea central: siempre visible (también mientras carga o si el
        // audio es silencio) — ancla visual del eje de la señal.
        drawLine(
            color = signalColor.copy(alpha = 0.22f * alpha),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1f
        )
        shapes?.let {
            drawPath(path = it.peak, color = signalColor.copy(alpha = 0.50f * alpha))
            drawPath(path = it.core, color = signalColor.copy(alpha = 0.96f * alpha))
        }
    }
}

/** Arma las dos siluetas para el tamaño/zoom/recorte actuales; `null` si no hay nada que dibujar. */
private fun buildWaveformShapes(
    waveform: AudioWaveform?,
    trimStartMs: Long,
    pxPerMs: Float,
    widthPx: Float,
    heightPx: Float
): WaveformShapes? {
    if (waveform == null || waveform.isEmpty) return null
    if (widthPx < 1f || heightPx < 1f || pxPerMs <= 0f) return null

    val step = max(1f, widthPx / MAX_WAVEFORM_COLUMNS)
    val columns = ceil(widthPx / step).toInt().coerceAtLeast(1)
    val msPerColumn = step.toDouble() / pxPerMs

    val peakValues = FloatArray(columns)
    val rmsValues = FloatArray(columns)
    waveform.sampleColumns(
        startMs = trimStartMs.toDouble(),
        msPerColumn = msPerColumn,
        columns = columns,
        peakOut = peakValues,
        rmsOut = rmsValues
    )

    val centerY = heightPx / 2f
    val maxHalf = centerY * WAVEFORM_HEIGHT_FRACTION

    fun halfHeight(value: Float, gamma: Float): Float =
        max(MIN_HALF_HEIGHT_PX, value.coerceIn(0f, 1f).pow(gamma) * maxHalf)

    fun silhouette(values: FloatArray, gamma: Float): Path {
        val path = Path()
        // Borde superior de izquierda a derecha…
        for (c in 0 until columns) {
            val x = c * step + step / 2f
            val y = centerY - halfHeight(values[c], gamma)
            if (c == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // …y borde inferior de derecha a izquierda (espejo respecto al eje).
        for (c in columns - 1 downTo 0) {
            val x = c * step + step / 2f
            path.lineTo(x, centerY + halfHeight(values[c], gamma))
        }
        path.close()
        return path
    }

    return WaveformShapes(
        peak = silhouette(peakValues, PEAK_DISPLAY_GAMMA),
        core = silhouette(rmsValues, RMS_DISPLAY_GAMMA)
    )
}
