package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reverse por clip: inversión de PCM, recálculo del recorte y escritura del WAV de preview. */
class AudioReverseTest {

    @Test
    fun `mono se invierte muestra a muestra`() {
        assertArrayEquals(
            shortArrayOf(4, 3, 2, 1),
            AudioProcessor.reverseFrames(shortArrayOf(1, 2, 3, 4), 1)
        )
    }

    @Test
    fun `estereo invierte frames y NO intercambia canales`() {
        // frames: (1,10) (2,20) (3,30)  ->  (3,30) (2,20) (1,10)
        assertArrayEquals(
            shortArrayOf(3, 30, 2, 20, 1, 10),
            AudioProcessor.reverseFrames(shortArrayOf(1, 10, 2, 20, 3, 30), 2)
        )
    }

    @Test
    fun `invertir dos veces devuelve el original`() {
        val original = shortArrayOf(1, 10, 2, 20, 3, 30, 4, 40)
        val twice = AudioProcessor.reverseFrames(AudioProcessor.reverseFrames(original, 2), 2)
        assertArrayEquals(original, twice)
    }

    @Test
    fun `no modifica el array de entrada`() {
        val input = shortArrayOf(1, 2, 3)
        AudioProcessor.reverseFrames(input, 1)
        assertArrayEquals(shortArrayOf(1, 2, 3), input)
    }

    @Test
    fun `resto no alineado a frame se conserva al final`() {
        assertArrayEquals(
            shortArrayOf(3, 4, 1, 2, 9),
            AudioProcessor.reverseFrames(shortArrayOf(1, 2, 3, 4, 9), 2)
        )
    }

    @Test
    fun `arrays vacios no fallan`() {
        assertEquals(0, AudioProcessor.reverseFrames(ShortArray(0), 2).size)
    }

    // ---------------- reversedTrimStartMs ----------------

    @Test
    fun `el mismo tramo audible queda espejado dentro del archivo`() {
        // Archivo 10 s, suena [2000, 5000). Invertido ese tramo es [5000, 8000).
        assertEquals(5_000L, reversedTrimStartMs(10_000L, 2_000L, 3_000L))
    }

    @Test
    fun `alternar dos veces restituye el recorte original`() {
        val once = reversedTrimStartMs(10_000L, 2_000L, 3_000L)
        assertEquals(2_000L, reversedTrimStartMs(10_000L, once, 3_000L))
    }

    @Test
    fun `clip que excede el archivo suena entero - recorte cero`() {
        assertEquals(0L, reversedTrimStartMs(10_000L, 2_000L, 20_000L))
    }

    @Test
    fun `el recorte nunca deja menos de 100 ms de audio`() {
        // tramo [0, 10] -> espejo empezaria en 9990: se acota a D - 100.
        assertEquals(0L, reversedTrimStartMs(10_000L, 0L, 10_000L))
        assertEquals(9_900L, reversedTrimStartMs(10_000L, 0L, 10L))
    }

    // ---------------- writeWav16 ----------------

    @Test
    fun `cabecera WAV correcta para estereo 44100`() {
        val out = ByteArrayOutputStream()
        writeWav16(out, shortArrayOf(1, -1, 2, -2), 44_100, 2)
        val bytes = out.toByteArray()
        assertEquals(WAV_HEADER_BYTES + 8, bytes.size)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + 8, bb.getInt(4))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals(1.toShort(), bb.getShort(20))          // PCM
        assertEquals(2.toShort(), bb.getShort(22))          // canales
        assertEquals(44_100, bb.getInt(24))                 // sample rate
        assertEquals(44_100 * 2 * 2, bb.getInt(28))         // byte rate
        assertEquals(4.toShort(), bb.getShort(32))          // block align
        assertEquals(16.toShort(), bb.getShort(34))         // bits
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        assertEquals(8, bb.getInt(40))                      // bytes de datos
    }

    @Test
    fun `las muestras se escriben little endian en orden`() {
        val out = ByteArrayOutputStream()
        writeWav16(out, shortArrayOf(0x0102, -2), 8_000, 1)
        val bb = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x0102.toShort(), bb.getShort(WAV_HEADER_BYTES))
        assertEquals((-2).toShort(), bb.getShort(WAV_HEADER_BYTES + 2))
    }

    @Test
    fun `audios mas largos que un bloque se escriben completos`() {
        val samples = ShortArray(100_000) { (it % 1000).toShort() }
        val out = ByteArrayOutputStream()
        writeWav16(out, samples, 22_050, 1)
        val bb = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(WAV_HEADER_BYTES + samples.size * 2, bb.capacity())
        assertEquals(samples[99_999], bb.getShort(WAV_HEADER_BYTES + 99_999 * 2))
    }
}
