package com.yeivikas.olyzecs.engine.audio

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** Lectura aleatoria de un WAV en disco ([WavFilePcmSource]): los mismos bytes que escribió [writeWav16], sin Android. */
class WavFilePcmSourceTest {
    private val files = mutableListOf<File>()

    @After
    fun cleanUp() = files.forEach { it.delete() }

    private fun wavOf(samples: ShortArray, channels: Int = 2, rate: Int = 44_100): File =
        File.createTempFile("pcm-source", ".wav").also { file ->
            files += file
            file.outputStream().buffered().use { writeWav16(it, samples, rate, channels) }
        }

    @Test
    fun `lee de cualquier posicion exactamente lo escrito`() {
        val samples = ShortArray(2 * 5_000) { (it * 7 - 9_000).toShort() }
        WavFilePcmSource(wavOf(samples)).use { source ->
            assertEquals(5_000L, source.frames)
            val out = ShortArray(2 * 100)
            assertEquals(100, source.read(1_234, out, 100))
            assertArrayEquals(samples.copyOfRange(2 * 1_234, 2 * 1_334), out)
            // Hacia atrás y de nuevo adelante: el acceso es aleatorio.
            assertEquals(100, source.read(10, out, 100))
            assertArrayEquals(samples.copyOfRange(20, 220), out)
        }
    }

    @Test
    fun `al final devuelve menos y mas alla devuelve cero`() {
        val samples = ShortArray(2 * 50) { it.toShort() }
        WavFilePcmSource(wavOf(samples)).use { source ->
            val out = ShortArray(2 * 100)
            assertEquals(10, source.read(40, out, 100))
            assertEquals(0, source.read(50, out, 100))
        }
    }

    @Test
    fun `un wav mono se rechaza`() {
        val file = wavOf(ShortArray(100), channels = 1)
        assertFalse(runCatching { WavFilePcmSource(file).close() }.isSuccess)
    }

    @Test
    fun `un archivo que no es wav se rechaza`() {
        val file = File.createTempFile("not-wav", ".wav").also { files += it; it.writeBytes(ByteArray(200) { 1 }) }
        assertFalse(runCatching { WavFilePcmSource(file).close() }.isSuccess)
    }

    @Test
    fun `expone la tasa de muestreo que declara la cabecera`() {
        for (rate in listOf(22_050, 44_100, 48_000, 96_000)) {
            WavFilePcmSource(wavOf(ShortArray(2 * 10), rate = rate)).use { assertEquals(rate, it.sampleRateHz) }
        }
    }

    @Test
    fun `una tasa de muestreo invalida se rechaza`() {
        val file = wavOf(ShortArray(2 * 10), rate = 0)
        assertFalse(runCatching { WavFilePcmSource(file).close() }.isSuccess)
    }
}
