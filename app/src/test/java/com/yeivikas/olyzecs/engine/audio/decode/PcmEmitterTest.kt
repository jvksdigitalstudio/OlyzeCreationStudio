package com.yeivikas.olyzecs.engine.audio.decode

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PcmEmitterTest {

    /** Sumidero que junta todo lo recibido y registra el formato y el tamaño de cada bloque. */
    private class RecordingSink : PcmSink {
        val all = ArrayList<Short>()
        val blockSizes = ArrayList<Int>()
        val formats = ArrayList<Pair<Int, Int>>()
        override fun onFormat(sampleRateHz: Int, channelCount: Int) {
            formats += sampleRateHz to channelCount
            assertTrue("onFormat debe llegar antes de cualquier muestra", all.isEmpty())
        }
        override fun onSamples(samples: ShortArray, count: Int) {
            for (i in 0 until count) all += samples[i]
            blockSizes += count
        }
    }

    private fun stereo16(frames: Int): ByteArray {
        val out = ByteArray(frames * 4)
        for (i in out.indices) out[i] = (i * 7 + 3).toByte()
        return out
    }

    @Test
    fun `trocear la entrada en cualquier tamano da exactamente lo mismo que entregarla de una vez`() {
        val bytes = stereo16(5000)
        val reference = RecordingSink().also { PcmEmitter(it, 48_000, 2, SampleFormat.S16).write(bytes, bytes.size) }

        val rng = Random(42)
        repeat(30) {
            val sink = RecordingSink()
            val emitter = PcmEmitter(sink, 48_000, 2, SampleFormat.S16)
            var pos = 0
            while (pos < bytes.size) {
                val n = minOf(bytes.size - pos, 1 + rng.nextInt(997)) // incluye cortes de 1 byte: parten muestras y frames
                emitter.write(bytes.copyOfRange(pos, pos + n), n)
                pos += n
            }
            assertEquals(reference.all, sink.all)
            assertEquals(5000L, emitter.framesEmitted)
        }
    }

    @Test
    fun `solo se entregan frames completos y el formato se anuncia una vez`() {
        val sink = RecordingSink()
        val emitter = PcmEmitter(sink, 44_100, 2, SampleFormat.S24) // frame de 6 bytes
        val bytes = ByteArray(6 * 100) { it.toByte() }
        var pos = 0
        val rng = Random(7)
        while (pos < bytes.size) {
            val n = minOf(bytes.size - pos, 1 + rng.nextInt(50))
            emitter.write(bytes.copyOfRange(pos, pos + n), n)
            pos += n
        }
        assertEquals(listOf(44_100 to 2), sink.formats)
        assertTrue(sink.blockSizes.all { it % 2 == 0 }) // nunca medio frame estéreo
        assertEquals(200, sink.all.size)
    }

    @Test
    fun `un frame incompleto al final se descarta`() {
        val sink = RecordingSink()
        val emitter = PcmEmitter(sink, 48_000, 2, SampleFormat.S16)
        val bytes = stereo16(10) + byteArrayOf(1, 2, 3) // 3 bytes de un frame de 4
        emitter.write(bytes, bytes.size)
        assertEquals(10L, emitter.framesEmitted)
        assertEquals(20, sink.all.size)
    }

    @Test
    fun `sin audio no se anuncia ningun formato`() {
        val sink = RecordingSink()
        PcmEmitter(sink, 48_000, 2, SampleFormat.S16).write(ByteArray(3), 3)
        assertTrue(sink.formats.isEmpty())
    }

    @Test
    fun `el acumulador une los bloques en orden`() {
        val acc = PcmAccumulator()
        acc.onSamples(shortArrayOf(1, 2, 3, 99), 3) // el 99 está fuera de `count`
        acc.onSamples(shortArrayOf(4, 5), 2)
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5), acc.toShortArray())
        assertTrue(acc.isEmpty)
    }

    @Test
    fun `el sumidero observado cuenta lo que recibe`() {
        val observed = ObservedSink(PcmAccumulator())
        assertEquals(0L, observed.samplesReceived)
        observed.onFormat(48_000, 2)
        observed.onSamples(ShortArray(8), 8)
        observed.onSamples(ShortArray(4), 4)
        assertEquals(12L, observed.samplesReceived)
    }
}
