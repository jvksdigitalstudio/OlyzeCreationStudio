package com.yeivikas.olyzecs.engine.audio.decode

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavStreamingTest {

    private class CountingAccumulator : PcmSink {
        val inner = PcmAccumulator()
        var calls = 0
        var formatCalls = 0
        override fun onFormat(sampleRateHz: Int, channelCount: Int) { formatCalls++ }
        override fun onSamples(samples: ShortArray, count: Int) { calls++; inner.onSamples(samples, count) }
    }

    private fun stereoSamples(frames: Int) = ShortArray(frames * 2) { (it * 31 - 20_000).toShort() }

    private fun wavBytes(samples: ShortArray, rate: Int, channels: Int): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it) }
        return wavHeaderBytes(samples.size * 2L, rate, channels) + data.array()
    }

    @Test
    fun `un WAV largo se entrega por bloques sin cambiar ni una muestra`() {
        val samples = stereoSamples(150_000) // ~600 KB: varios bloques de 64 KB
        val sink = CountingAccumulator()
        val info = WavStreamDecoder.decode(ByteArrayInputStream(wavBytes(samples, 44_100, 2)), sink) { _, _ -> }
        assertNotNull(info)
        assertEquals(44_100, info!!.sampleRateHz)
        assertEquals(2, info.channelCount)
        assertEquals(1, sink.formatCalls)
        assertTrue("debe llegar en varios bloques, no de una vez (llegó en ${sink.calls})", sink.calls > 5)
        assertArrayEquals(samples, sink.inner.toShortArray())
    }

    @Test
    fun `mono de 16 bits conserva su cantidad de canales`() {
        val samples = ShortArray(1000) { it.toShort() }
        val sink = PcmAccumulator()
        val info = WavStreamDecoder.decode(ByteArrayInputStream(wavBytes(samples, 22_050, 1)), sink) { _, _ -> }
        assertEquals(1, info!!.channelCount)
        assertArrayEquals(samples, sink.toShortArray())
    }

    @Test
    fun `lo que no es un WAV devuelve null sin quejarse`() {
        var reported = false
        val info = WavStreamDecoder.decode(ByteArrayInputStream(ByteArray(64)), PcmAccumulator()) { _, _ -> reported = true }
        assertNull(info)
        assertTrue("otro formato no es un fallo: lo intentará otro decodificador", !reported)
    }

    @Test
    fun `un WAV sin bloque data informa el motivo`() {
        val header = wavHeaderBytes(0, 48_000, 2).copyOf(36) // RIFF + fmt, sin "data"
        var reason: String? = null
        val info = WavStreamDecoder.decode(ByteArrayInputStream(header), PcmAccumulator()) { r, _ -> reason = r }
        assertNull(info)
        assertTrue(reason!!.contains("header incompleto"))
    }

    @Test
    fun `el escritor corrige la cabecera con el largo real al cerrar`() {
        val file = File.createTempFile("wav-stream-writer", ".wav")
        try {
            val samples = stereoSamples(70_000)
            WavStreamWriter(file, 48_000, 2).use { writer ->
                var i = 0
                while (i < samples.size) { // llega en trozos de tamaño irregular
                    val n = minOf(samples.size - i, 4_096 + (i % 7) * 100)
                    writer.write(samples.copyOfRange(i, i + n), n)
                    i += n
                }
                assertEquals(70_000L, writer.framesWritten)
            }
            val bytes = file.readBytes()
            assertEquals(WAV_HEADER_BYTES + samples.size * 2, bytes.size)
            assertArrayEquals(wavHeaderBytes(samples.size * 2L, 48_000, 2), bytes.copyOf(WAV_HEADER_BYTES))

            // Y lo que escribió es un WAV que el decodificador lee idéntico.
            val sink = PcmAccumulator()
            WavStreamDecoder.decode(ByteArrayInputStream(bytes), sink) { _, _ -> }
            assertArrayEquals(samples, sink.toShortArray())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `la cabecera canonica tiene los campos de un WAV PCM de 16 bits`() {
        val bb = ByteBuffer.wrap(wavHeaderBytes(1000, 44_100, 2)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44, bb.capacity())
        assertEquals(1036, bb.getInt(4))
        assertEquals(1, bb.getShort(20).toInt())
        assertEquals(2, bb.getShort(22).toInt())
        assertEquals(44_100, bb.getInt(24))
        assertEquals(44_100 * 4, bb.getInt(28))
        assertEquals(4, bb.getShort(32).toInt())
        assertEquals(16, bb.getShort(34).toInt())
        assertEquals(1000, bb.getInt(40))
    }
}
