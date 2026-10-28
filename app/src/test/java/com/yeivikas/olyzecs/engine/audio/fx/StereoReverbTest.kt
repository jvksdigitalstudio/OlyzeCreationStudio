package com.yeivikas.olyzecs.engine.audio.fx

import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.bandPass
import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.decayDb
import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.impulse
import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.noise
import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.peak
import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.rms
import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.rt60Seconds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * El reverb de estudio ([StereoReverb]) medido como se mide un reverb: sobre su respuesta al impulso y con ruido. Los
 * umbrales salen de medir el algoritmo (ver los comentarios de `StereoReverb.kt`) y dejan margen amplio: las pruebas
 * detectan una regresión del sonido, no una variación de redondeo.
 */
class StereoReverbTest {
    private val rate = 44_100

    /** Solo la parte húmeda (`mix = 1`), sin filtros de entrada, pre-delay ni reflexiones salvo lo que pida la prueba. */
    private fun wet(
        decay: Float = 1.2f, preDelayMs: Float = 0f, size: Float = 0.6f, damping: Float = 8_000f, lowCut: Float = 20f,
        highCut: Float = 14_000f, diffusion: Float = 0.75f, modulation: Float = 0.35f, width: Float = 1f, early: Float = 0.5f
    ) = ReverbParams(
        mix = 1f, decaySeconds = decay, preDelayMs = preDelayMs, size = size, dampingHz = damping, lowCutHz = lowCut,
        highCutHz = highCut, diffusion = diffusion, modulation = modulation, width = width, earlyLevel = early
    )

    private fun render(params: ReverbParams, left: FloatArray, right: FloatArray = left.copyOf(), sampleRate: Int = rate): Pair<FloatArray, FloatArray> {
        val outL = FloatArray(left.size)
        val outR = FloatArray(right.size)
        StereoReverb(sampleRate, params).process(left, right, outL, outR, left.size)
        return outL to outR
    }

    @Test
    fun `con mix cero la senal sale identica bit a bit`() {
        val left = noise(20_000, 0.3f, seed = 1)
        val right = noise(20_000, 0.3f, seed = 2)
        val (outL, outR) = render(ReverbParams(mix = 0f), left, right)
        assertArrayEquals(left, outL, 0f)
        assertArrayEquals(right, outR, 0f)
    }

    @Test
    fun `el RT60 de graves y medios coincide con el decaimiento pedido a 44_1 y 48 kHz`() {
        for (sampleRate in listOf(44_100, 48_000)) {
            for (decay in listOf(0.8f, 2.0f)) {
                val frames = (sampleRate * (decay * 1.6 + 0.6)).toInt()
                val (left, _) = render(wet(decay = decay), impulse(frames), sampleRate = sampleRate)
                val measured = rt60Seconds(bandPass(left, 250.0, 700.0, sampleRate), sampleRate)
                assertEquals("RT60 a $sampleRate Hz, decay $decay s", decay.toDouble(), measured, decay * 0.10)
            }
        }
    }

    @Test
    fun `nada suena antes del pre-delay`() {
        val preDelaySamples = Math.round(20.0 * rate / 1000.0).toInt()
        val (left, right) = render(wet(preDelayMs = 20f), impulse(rate))
        val first = minOf(left.indexOfFirst { abs(it) > 1e-9f }, right.indexOfFirst { abs(it) > 1e-9f })
        assertTrue("Sonó en la muestra $first, antes del pre-delay ($preDelaySamples)", first >= preDelaySamples)
        // ...y la primera reflexión temprana llega poco después (la sala más chica de la prueba es la mediana).
        assertTrue("La primera reflexión llegó demasiado tarde: $first", first <= preDelaySamples + 500)
    }

    @Test
    fun `con parametros extremos es estable, finito y decae`() {
        val params = wet(decay = 15f, size = 1f, modulation = 1f, diffusion = 1f)
        val frames = rate * 6
        val (left, right) = render(params, impulse(frames))
        assertTrue("NaN o infinito en la salida", left.all { it.isFinite() } && right.all { it.isFinite() })
        assertTrue("Pico fuera de control: ${peak(left)}", peak(left) < 2f && peak(right) < 2f)
        val firstSecond = rms(left, 0, rate)
        val lastSecond = rms(left, frames - rate, frames)
        assertTrue("La cola no decae: $lastSecond vs $firstSecond", lastSecond < 0.35 * firstSecond)
    }

    @Test
    fun `el amortiguamiento hace caer los agudos mucho mas rapido que los graves`() {
        val (left, _) = render(wet(decay = 2f, damping = 2_500f), impulse(rate * 3))
        val lowDrop = decayDb(bandPass(left, 150.0, 400.0, rate), rate, 0.2, 1.2, 1.5, 2.5)
        val highDrop = decayDb(bandPass(left, 4_000.0, 8_000.0, rate), rate, 0.2, 1.2, 1.5, 2.5)
        assertTrue("Graves $lowDrop dB, agudos $highDrop dB", lowDrop - highDrop > 20.0)
    }

    @Test
    fun `con ancho cero el reverb es mono exacto`() {
        val (left, right) = render(wet(width = 0f), impulse(rate))
        assertArrayEquals(left, right, 0f)
    }

    @Test
    fun `con entrada mono y ancho completo la cola es estereo descorrelacionada`() {
        val (left, right) = render(wet(decay = 1.5f), impulse(rate * 2))
        val from = (0.3 * rate).toInt()
        var sumLR = 0.0
        var sumLL = 0.0
        var sumRR = 0.0
        for (i in from until left.size) {
            sumLR += left[i].toDouble() * right[i]
            sumLL += left[i].toDouble() * left[i]
            sumRR += right[i].toDouble() * right[i]
        }
        val correlation = sumLR / Math.sqrt(sumLL * sumRR)
        assertTrue("Correlación L/R: $correlation", abs(correlation) < 0.2)
    }

    @Test
    fun `es determinista - las mismas entradas dan exactamente las mismas muestras`() {
        val input = noise(30_000, 0.2f, seed = 5)
        val params = ReverbParams(mix = 0.5f, modulation = 1f)
        val (first, _) = render(params, input)
        val (second, _) = render(params, input)
        assertArrayEquals(first, second, 0f)
    }

    @Test
    fun `procesar en el sitio da lo mismo que procesar a otro buffer`() {
        val input = noise(25_000, 0.25f, seed = 11)
        val params = ReverbParams(mix = 0.4f)
        val (expectedL, expectedR) = render(params, input, input.copyOf())

        val inPlaceL = input.copyOf()
        val inPlaceR = input.copyOf()
        StereoReverb(rate, params).process(inPlaceL, inPlaceR, inPlaceL, inPlaceR, input.size)
        assertArrayEquals(expectedL, inPlaceL, 0f)
        assertArrayEquals(expectedR, inPlaceR, 0f)
    }

    @Test
    fun `el estado se conserva entre bloques - procesar por partes da lo mismo que de una vez`() {
        val input = noise(10_000, 0.3f, seed = 13)
        val params = ReverbParams(mix = 0.6f, modulation = 0.8f)
        val (whole, _) = render(params, input)

        val reverb = StereoReverb(rate, params)
        val pieces = FloatArray(input.size)
        var offset = 0
        while (offset < input.size) {
            val length = minOf(777, input.size - offset)
            val chunk = input.copyOfRange(offset, offset + length)
            val out = FloatArray(length)
            reverb.process(chunk, chunk.copyOf(), out, FloatArray(length), length)
            out.copyInto(pieces, offset)
            offset += length
        }
        assertArrayEquals(whole, pieces, 0f)
    }

    @Test
    fun `el nivel en regimen no se dispara con colas largas`() {
        val input = noise(rate * 3, 0.1f, seed = 7)
        val levels = listOf(0.3f, 1f, 3f, 10f).map { decay ->
            val (left, _) = render(wet(decay = decay), input, noise(rate * 3, 0.1f, seed = 8))
            rms(left, rate * 2, rate * 3) / 0.1
        }
        for (level in levels) assertTrue("Nivel fuera de rango: $levels", level in 0.25..0.8)
        assertTrue("El nivel varía demasiado entre decaimientos: $levels", levels.max() / levels.min() < 1.7)
    }

    @Test
    fun `la salida es silencio si la entrada es silencio`() {
        val (left, right) = render(ReverbParams(mix = 0.7f), FloatArray(8_000))
        assertFalse(left.any { it != 0f } || right.any { it != 0f })
    }

    @Test
    fun `los largos de las lineas son primos entre si, no pasan del minimo y crecen con el tamano`() {
        for (sampleRate in listOf(22_050, 44_100, 48_000, 96_000)) {
            var previousTotal = 0
            for (size in listOf(0.1f, 0.6f, 1f)) {
                val lengths = reverbLineLengths(sampleRate, size)
                assertEquals(8, lengths.size)
                assertTrue(lengths.all { it >= 64 })
                for (i in lengths.indices) for (j in i + 1 until lengths.size) {
                    assertEquals("gcd(${lengths[i]}, ${lengths[j]}) a $sampleRate Hz, size $size", 1, gcd(lengths[i], lengths[j]))
                }
                assertTrue(lengths.sum() >= previousTotal)
                previousTotal = lengths.sum()
            }
        }
    }

    @Test
    fun `la escala de sala va de 0_51 a 1_5`() {
        assertEquals(0.51, roomScale(0.1f), 1e-6)
        assertEquals(1.5, roomScale(1f), 1e-6)
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
