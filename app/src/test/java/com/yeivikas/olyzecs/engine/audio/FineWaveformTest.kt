package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

/**
 * Cubre la forma de onda de alta resolución ([FineWaveform]) como JVM unit test puro,
 * con [DecodedPcm] sintético. Con sampleRate = 1000 Hz, 1 frame = 1 ms.
 */
class FineWaveformTest {

    private val rate = 1000

    private fun mono(vararg values: Int) =
        DecodedPcm(ShortArray(values.size) { values[it].toShort() }, rate, channelCount = 1)

    private class Out {
        val min = FloatArray(1)
        val max = FloatArray(1)
        val rms = FloatArray(1)
    }

    private fun FineWaveform.column(start: Double, end: Double): Out =
        Out().also { sampleColumn(start, end, 0, it.min, it.max, it.rms) }

    @Test
    fun `lee min max y rms reales de muestras crudas`() {
        val fine = FineWaveform.from(mono(0, 1000, -2000, 500))!!
        val o = fine.column(0.0, 4.0)
        assertEquals(-2000 / 32768f, o.min[0], 1e-6f)
        assertEquals(1000 / 32768f, o.max[0], 1e-6f)
        val expectedRms = sqrt((0.0 + 1_000_000.0 + 4_000_000.0 + 250_000.0) / 4) / 32768.0
        assertEquals(expectedRms.toFloat(), o.rms[0], 1e-6f)
    }

    @Test
    fun `la duracion sale del sample rate`() {
        val fine = FineWaveform.from(DecodedPcm(ShortArray(2_500), 44_100, 1))!!
        assertEquals(2_500 * 1000.0 / 44_100, fine.durationMs, 1e-9)
    }

    @Test
    fun `en estereo se toma la muestra de mayor magnitud con su signo`() {
        val pcm = DecodedPcm(shortArrayOf(100, -300, 50, 20), rate, channelCount = 2)
        val fine = FineWaveform.from(pcm)!!
        assertEquals(2, fine.frames)
        assertEquals(-300 / 32768f, fine.sampleAt(0), 1e-6f)
        assertEquals(50 / 32768f, fine.sampleAt(1), 1e-6f)
    }

    @Test
    fun `fuera del archivo todo es cero`() {
        val fine = FineWaveform.from(mono(100, 200, 300))!!
        listOf(5.0 to 9.0, -9.0 to -1.0).forEach { (a, b) ->
            val o = fine.column(a, b)
            assertEquals(0f, o.min[0], 0f)
            assertEquals(0f, o.max[0], 0f)
            assertEquals(0f, o.rms[0], 0f)
        }
        assertEquals(0f, fine.sampleAt(-1), 0f)
        assertEquals(0f, fine.sampleAt(3), 0f)
    }

    @Test
    fun `la piramide coincide con la fuerza bruta en un rango alineado`() {
        val rnd = Random(7)
        val values = ShortArray(4096) { (rnd.nextInt(60_001) - 30_000).toShort() }
        val fine = FineWaveform.from(DecodedPcm(values, rate, 1))!!

        val o = fine.column(0.0, 4096.0) // elige un nivel grueso de la piramide
        var mn = Int.MAX_VALUE
        var mx = Int.MIN_VALUE
        var sq = 0.0
        for (v in values) {
            val i = v.toInt()
            if (i < mn) mn = i
            if (i > mx) mx = i
            val n = i / 32768.0
            sq += n * n
        }
        assertEquals(mn / 32768f, o.min[0], 0f)
        assertEquals(mx / 32768f, o.max[0], 0f)
        assertEquals(sqrt(sq / values.size).toFloat(), o.rms[0], 1e-4f)
    }

    @Test
    fun `un rango no alineado es conservador y nunca pierde un pico`() {
        val values = ShortArray(4096) { 100 }
        values[2500] = 30_000 // transitorio de una sola muestra
        val fine = FineWaveform.from(DecodedPcm(values, rate, 1))!!
        val o = fine.column(2400.0, 2600.0) // 200 frames: pirámide gruesa
        assertEquals(30_000 / 32768f, o.max[0], 0f)
        assertTrue(o.min[0] <= 100 / 32768f)
    }

    @Test
    fun `un transitorio sobrevive al alejar el zoom`() {
        val values = ShortArray(100_000)
        values[77_777] = -20_000
        val fine = FineWaveform.from(DecodedPcm(values, rate, 1))!!
        val o = fine.column(0.0, 100_000.0)
        assertEquals(-20_000 / 32768f, o.min[0], 0f)
    }

    @Test
    fun `el ultimo bloque incompleto no distorsiona el rms`() {
        // 20 frames constantes: el bloque de 16 queda completo y el segundo tiene 4. RMS debe ser el de la constante.
        val fine = FineWaveform.from(DecodedPcm(ShortArray(20) { 16_384 }, rate, 1))!!
        val o = fine.column(0.0, 40.0)
        assertEquals(0.5f, o.rms[0], 1e-4f)
    }

    @Test
    fun `audio vacio o demasiado largo no genera onda fina`() {
        assertNull(FineWaveform.from(DecodedPcm(ShortArray(0), rate, 1)))
        assertNull(FineWaveform.from(DecodedPcm(ShortArray(10), 0, 1)))
        assertNull(FineWaveform.from(DecodedPcm(ShortArray(FineWaveform.MAX_FRAMES + 1), rate, 1)))
        assertNotNull(FineWaveform.from(DecodedPcm(ShortArray(FineWaveform.MAX_FRAMES), rate, 1)))
    }
}
