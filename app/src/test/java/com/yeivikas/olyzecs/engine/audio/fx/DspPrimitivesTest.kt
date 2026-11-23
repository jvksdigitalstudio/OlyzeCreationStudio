package com.yeivikas.olyzecs.engine.audio.fx

import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.noise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/** Las piezas del reverb por separado: filtro, línea de retardo y difusor. Si una falla, el reverb entero deja de ser fiable. */
class DspPrimitivesTest {
    private val rate = 44_100

    /** Ganancia en dB de un seno de [freqHz] a través de [filter], medida en régimen (ignora el transitorio inicial). */
    private fun gainDb(filter: Biquad, freqHz: Double): Double {
        val frames = rate
        var inPower = 0.0
        var outPower = 0.0
        for (i in 0 until frames) {
            val x = sin(2.0 * PI * freqHz * i / rate)
            val y = filter.process(x)
            if (i >= frames / 2) {
                inPower += x * x
                outPower += y * y
            }
        }
        return 10.0 * log10(outPower / inPower)
    }

    // ---------------------------------------------------------------- Biquad

    @Test
    fun `el pasa-bajos deja pasar los graves y corta los agudos a 12 dB por octava`() {
        assertEquals(0.0, gainDb(Biquad(BiquadKind.LOW_PASS, 500.0, rate), 100.0), 0.2)
        assertTrue(gainDb(Biquad(BiquadKind.LOW_PASS, 500.0, rate), 10_000.0) < -40.0)
    }

    @Test
    fun `el pasa-altos deja pasar los agudos y corta los graves`() {
        assertEquals(0.0, gainDb(Biquad(BiquadKind.HIGH_PASS, 2_000.0, rate), 10_000.0), 0.2)
        assertTrue(gainDb(Biquad(BiquadKind.HIGH_PASS, 2_000.0, rate), 100.0) < -35.0)
    }

    @Test
    fun `a la frecuencia de corte la ganancia es de 3 dB (Butterworth)`() {
        assertEquals(-3.01, gainDb(Biquad(BiquadKind.LOW_PASS, 1_000.0, rate), 1_000.0), 0.15)
        assertEquals(-3.01, gainDb(Biquad(BiquadKind.HIGH_PASS, 1_000.0, rate), 1_000.0), 0.15)
    }

    @Test
    fun `un pasa-altos de 20 Hz es estable y elimina una componente continua`() {
        val filter = Biquad(BiquadKind.HIGH_PASS, 20.0, rate)
        var last = 1.0
        repeat(rate * 5) { last = filter.process(1.0) }
        assertTrue("Quedó DC: $last", abs(last) < 1e-3)
    }

    @Test
    fun `un corte fuera de rango se acota y el filtro no explota`() {
        val filter = Biquad(BiquadKind.LOW_PASS, 1e9, rate)
        val x = noise(100_000, 1f, seed = 3)
        assertTrue(x.all { filter.process(it.toDouble()).isFinite() })
    }

    // ---------------------------------------------------------------- DelayLine

    @Test
    fun `read devuelve lo escrito hace d muestras`() {
        val line = DelayLine(64)
        val written = FloatArray(200) { (it + 1).toFloat() }
        for (i in written.indices) {
            if (i >= 10) assertEquals(written[i - 10], line.read(10), 0f)
            line.write(written[i])
        }
    }

    @Test
    fun `la lectura fraccionaria en un retardo entero coincide con la entera`() {
        val line = DelayLine(64)
        val x = noise(300, 1f, seed = 4)
        for (i in x.indices) {
            if (i >= 20) assertEquals(line.read(12), line.readFractional(12.0), 1e-6f)
            line.write(x[i])
        }
    }

    @Test
    fun `la interpolacion de Hermite reproduce una rampa exactamente`() {
        val line = DelayLine(64)
        for (i in 0 until 100) line.write(i.toFloat())
        // El último valor escrito (99) está a 1 muestra; a un retardo d la rampa vale 100 - d.
        assertEquals(100.0 - 10.37, line.readFractional(10.37).toDouble(), 1e-4)
        assertEquals(100.0 - 25.5, line.readFractional(25.5).toDouble(), 1e-4)
    }

    @Test
    fun `un retardo fraccionario menor que 2 se sube a 2 en vez de leer basura`() {
        val line = DelayLine(16)
        for (i in 0 until 40) line.write(i.toFloat())
        assertEquals(line.readFractional(2.0), line.readFractional(0.3), 0f)
    }

    @Test
    fun `un retardo maximo invalido se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) { DelayLine(0) }
    }

    @Test
    fun `flushDenormal anula lo diminuto y respeta lo audible`() {
        assertEquals(0f, flushDenormal(1e-25f), 0f)
        assertEquals(0f, flushDenormal(-1e-30f), 0f)
        assertEquals(1e-6f, flushDenormal(1e-6f), 0f)
    }

    // ---------------------------------------------------------------- AllpassDiffuser

    @Test
    fun `el difusor pasa-todo conserva la energia de un impulso`() {
        val diffuser = AllpassDiffuser(200)
        var energy = 0.0
        for (i in 0 until 40_000) {
            val y = diffuser.process(if (i == 0) 1f else 0f, 0.7f)
            energy += y.toDouble() * y
        }
        assertEquals(1.0, energy, 0.01)
    }

    @Test
    fun `el difusor reparte el impulso en una rafaga de ecos`() {
        val diffuser = AllpassDiffuser(200)
        val response = FloatArray(2_000) { diffuser.process(if (it == 0) 1f else 0f, 0.7f) }
        assertTrue(response.count { abs(it) > 1e-3f } > 3)
        assertEquals(-0.7f, response[0], 1e-6f)
    }
}
