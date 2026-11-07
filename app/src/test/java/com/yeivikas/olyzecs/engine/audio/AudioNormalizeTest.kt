package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Normalizar" por clip: cálculo de ganancia, tramo audible y pico real del archivo. */
class AudioNormalizeTest {

    @Test
    fun `audio flojo sube hasta el pico objetivo`() {
        val gain = normalizeGainForPeak(0.25f)!!
        assertEquals(NORMALIZE_TARGET_PEAK / 0.25f, gain, 1e-5f)
        assertEquals(NORMALIZE_TARGET_PEAK, 0.25f * gain, 1e-5f)
    }

    @Test
    fun `audio ya fuerte baja la ganancia por debajo de 1`() {
        assertTrue(normalizeGainForPeak(1.0f)!! < 1f)
    }

    @Test
    fun `la ganancia esta acotada por el tope`() {
        assertEquals(NORMALIZE_MAX_GAIN, normalizeGainForPeak(0.001f)!!, 1e-5f)
    }

    @Test
    fun `silencio casi absoluto no se normaliza`() {
        assertNull(normalizeGainForPeak(0f))
        assertNull(normalizeGainForPeak(0.00005f))
    }

    @Test
    fun `sin loop el tramo audible es trim hasta trim mas largo`() {
        assertEquals(1_000L to 3_000L, audibleSourceRangeMs(10_000L, 1_000L, false, 2_000L))
    }

    @Test
    fun `sin loop el tramo se acota al final del archivo`() {
        assertEquals(8_000L to 10_000L, audibleSourceRangeMs(10_000L, 8_000L, false, 5_000L))
    }

    @Test
    fun `con loop que no llega a repetir se comporta como sin loop`() {
        assertEquals(1_000L to 3_000L, audibleSourceRangeMs(10_000L, 1_000L, true, 2_000L))
    }

    @Test
    fun `con loop que repite suena el archivo entero`() {
        assertEquals(0L to 10_000L, audibleSourceRangeMs(10_000L, 8_000L, true, 5_000L))
    }

    @Test
    fun `el analizador conserva el pico absoluto real del archivo`() {
        // Mono, 1 kHz, 1000 frames; el pico real es 16384 de 32768 = 0.5.
        val samples = ShortArray(1_000) { if (it == 500) 16_384 else 100 }
        val waveform = AudioWaveformAnalyzer.analyze(DecodedPcm(samples, 1_000, 1))
        assertEquals(0.5f, waveform.sourcePeak, 1e-3f)
        // La forma de onda dibujada sigue normalizada a 1.
        assertEquals(1f, waveform.peaks.maxOrNull()!!, 1e-5f)
    }

    @Test
    fun `pico por rango devuelve el absoluto y respeta el rango`() {
        val samples = ShortArray(2_000) { if (it in 1_500..1_509) 16_384 else 100 }
        val waveform = AudioWaveformAnalyzer.analyze(DecodedPcm(samples, 1_000, 1))
        // El transitorio cae en la segunda mitad (1500–1510 ms).
        assertTrue(waveform.absolutePeakInRange(1_400L, 2_000L) > 0.45f)
        assertTrue(waveform.absolutePeakInRange(0L, 1_000L) < 0.05f)
        assertEquals(0f, waveform.absolutePeakInRange(500L, 500L), 0f)
    }
}
