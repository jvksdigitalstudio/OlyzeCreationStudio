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

/** Separación mínima (px) entre divisores de repetición para dibujarlos. */
private const val MIN_LOOP_DIVIDER_SPACING_PX = 10f

/** Mitad de alto mínima (px): el silencio se ve como una línea fina, no como un hueco. */
private const val MIN_HALF_HEIGHT_PX = 0.5f

private class WaveformShapes(val peak: Path, val core: Path)

/**
 * Nivel (0..1 de la altura útil) con que se dibuja un valor de la onda.
 *
 * Los datos de [AudioWaveform] vienen normalizados contra el pico de cada
 * archivo (un audio bajo igual llena la altura) y el carril los realza con una
 * curva de percepción [gamma]. Con [trueAmplitude] se muestra la señal ORIGINAL:
 * amplitud real (el valor normalizado vuelve a multiplicarse por [sourcePeak],
 * el pico absoluto del archivo, 1.0 = escala completa) y lineal, sin curva —
 * así cada audio conserva su propia forma y su volumen relativo.
 */
internal fun waveformDisplayLevel(normalized: Float, gamma: Float, sourcePeak: Float, trueAmplitude: Boolean): Float =
    if (trueAmplitude) (normalized * sourcePeak).coerceIn(0f, 1f)
    else normalized.coerceIn(0f, 1f).pow(gamma)

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
 * @param trueAmplitude `true` = señal ORIGINAL (amplitud real y lineal, ver
 *   [waveformDisplayLevel]); `false` = la vista normalizada y realzada del carril.
 * @param loop si el clip repite el archivo para llenar su largo (misma
 *   regla que el export: la primera pasada arranca en [trimStartMs]; las
 *   siguientes, desde el inicio del archivo). Con `true` la señal se
 *   dibuja REPETIDA a lo ancho del clip y se marca cada repetición con un
 *   divisor fino, como en un DAW; con `false` lo que sobra del clip
 *   después de terminar el archivo queda vacío (silencio real).
 */
@Composable
internal fun AudioWaveformCanvas(
    waveform: AudioWaveform?,
    trimStartMs: Long,
    pxPerMs: Float,
    signalColor: Color,
    dimmed: Boolean,
    loop: Boolean = false,
    trueAmplitude: Boolean = false,
    modifier: Modifier = Modifier
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    val shapes = remember(waveform, trimStartMs, pxPerMs, sizePx, loop, trueAmplitude) {
        buildWaveformShapes(waveform, trimStartMs, loop, pxPerMs, sizePx.width.toFloat(), sizePx.height.toFloat(), trueAmplitude)
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
        // Divisores de repetición (solo con loop): una línea fina donde
        // el archivo reinicia. Si las repeticiones quedan a menos de
        // `MIN_LOOP_DIVIDER_SPACING_PX` entre sí (zoom muy alejado) se
        // omiten: serían ruido, no información.
        if (loop && waveform != null && !waveform.isEmpty && pxPerMs > 0f) {
            val periodPx = waveform.durationMs * pxPerMs
            if (periodPx >= MIN_LOOP_DIVIDER_SPACING_PX) {
                val firstPassMs = (waveform.durationMs - trimStartMs).coerceAtLeast(0L)
                var x = firstPassMs * pxPerMs
                // Si el recorte deja una primera pasada de 0 ms, la línea
                // del borde izquierdo no aporta nada.
                if (x <= 0f) x += periodPx
                while (x < size.width) {
                    drawLine(
                        color = signalColor.copy(alpha = 0.38f * alpha),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += periodPx
                }
            }
        }
    }
}

/** Arma las dos siluetas para el tamaño/zoom/recorte actuales; `null` si no hay nada que dibujar. */
private fun buildWaveformShapes(
    waveform: AudioWaveform?,
    trimStartMs: Long,
    loop: Boolean,
    pxPerMs: Float,
    widthPx: Float,
    heightPx: Float,
    trueAmplitude: Boolean = false
): WaveformShapes? {
    if (waveform == null || waveform.isEmpty) return null
    if (widthPx < 1f || heightPx < 1f || pxPerMs <= 0f) return null

    val step = max(1f, widthPx / MAX_WAVEFORM_COLUMNS)
    val columns = ceil(widthPx / step).toInt().coerceAtLeast(1)
    val msPerColumn = step.toDouble() / pxPerMs

    val peakValues = FloatArray(columns)
    val rmsValues = FloatArray(columns)
    waveform.sampleColumnsLooped(
        trimStartMs = trimStartMs.toDouble(),
        loop = loop,
        msPerColumn = msPerColumn,
        columns = columns,
        peakOut = peakValues,
        rmsOut = rmsValues
    )

    val centerY = heightPx / 2f
    val maxHalf = centerY * WAVEFORM_HEIGHT_FRACTION

    fun halfHeight(value: Float, gamma: Float): Float =
        max(MIN_HALF_HEIGHT_PX, waveformDisplayLevel(value, gamma, waveform.sourcePeak, trueAmplitude) * maxHalf)

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
