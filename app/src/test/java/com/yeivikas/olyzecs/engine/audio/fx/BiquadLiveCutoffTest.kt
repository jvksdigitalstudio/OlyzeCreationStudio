package com.yeivikas.olyzecs.engine.audio.fx

import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.noise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Biquad.setCutoff]: mover el corte en vivo equivale a haber construido el filtro con ese corte, y no desestabiliza el filtro. */
class BiquadLiveCutoffTest {
    private val rate = 44_100

    @Test
    fun `un filtro recien movido suena igual que uno construido con ese corte`() {
        val input = noise(10_000, 0.5f, seed = 7)
        for (kind in BiquadKind.values()) {
            val built = Biquad(kind, 1_000.0, rate)
            val moved = Biquad(kind, 300.0, rate).also { it.setCutoff(1_000.0) }
            for (x in input) assertEquals(built.process(x.toDouble()), moved.process(x.toDouble()), 0.0)
        }
    }

    @Test
    fun `barrer el corte de extremo a extremo mantiene la salida finita y acotada`() {
        val input = noise(rate * 4, 0.5f, seed = 8)
        for (kind in BiquadKind.values()) {
            val filter = Biquad(kind, 1_000.0, rate)
            var peak = 0.0
            for ((i, x) in input.withIndex()) {
                if (i % 32 == 0) {
                    // Barrido logarítmico 20 Hz ↔ 20 kHz, ida y vuelta, 2 veces por segundo.
                    val phase = (i.toDouble() / rate * 2.0) % 1.0
                    val octaves = if (phase < 0.5) phase * 2.0 else (1.0 - phase) * 2.0
                    filter.setCutoff(20.0 * Math.pow(1_000.0, octaves))
                }
                val y = filter.process(x.toDouble())
                assertTrue(y.isFinite())
                peak = maxOf(peak, Math.abs(y))
            }
            assertTrue("pico $peak", peak < 10.0)
        }
    }
}
