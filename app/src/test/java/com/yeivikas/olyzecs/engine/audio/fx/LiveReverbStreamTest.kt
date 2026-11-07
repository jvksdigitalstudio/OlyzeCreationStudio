package com.yeivikas.olyzecs.engine.audio.fx

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * El motor en tiempo real ([LiveReverbStream]). La garantía central: sin cambios de parámetros ni saltos, lo que SUENA es
 * idéntico, muestra a muestra, a lo que EXPORTA ([applyEffectChain]); y moverlo en vivo (continuos, estructurales, seek) no
 * produce saltos ni valores inválidos ni depende del tamaño de bloque.
 */
class LiveReverbStreamTest {
    private val rate = 44_100
    private val params = ReverbParams(mix = 0.4f, decaySeconds = 1.5f, preDelayMs = 30f)

    private fun noisePcm(frames: Int, seed: Long): ShortArray {
        val random = java.util.Random(seed)
        return ShortArray(frames * 2) { (random.nextGaussian() * 6_000.0).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun drain(stream: LiveReverbStream, block: Int): ShortArray {
        val out = ArrayList<Short>()
        val buffer = ShortArray(block * 2)
        while (true) {
            val n = stream.read(buffer, block)
            if (n == 0) break
            for (i in 0 until n * 2) out.add(buffer[i])
        }
        return out.toShortArray()
    }

    @Test
    fun `lo que suena es identico a lo que exporta`() {
        val dry = noisePcm(20_000, seed = 1)
        val tailFrames = ceil(params.tailSeconds() * rate).toInt()
        val exported = applyEffectChain(dry + ShortArray(tailFrames * 2), rate, listOf(ReverbEffect(id = "r", params = params)))
        val live = drain(LiveReverbStream(ShortArrayPcmSource(dry), rate, params), block = 512)
        assertArrayEquals(exported, live)
    }

    @Test
    fun `el resultado no depende del tamano de bloque`() {
        val dry = noisePcm(15_000, seed = 2)
        val reference = drain(LiveReverbStream(ShortArrayPcmSource(dry), rate, params), block = 1_024)
        for (block in listOf(1, 77, 256)) {
            assertArrayEquals("bloque $block", reference, drain(LiveReverbStream(ShortArrayPcmSource(dry), rate, params), block))
        }
    }

    @Test
    fun `el largo total es el audio mas la cola y al terminar devuelve cero`() {
        val dry = noisePcm(5_000, seed = 3)
        val stream = LiveReverbStream(ShortArrayPcmSource(dry), rate, params)
        assertEquals(5_000L + ceil(params.tailSeconds() * rate).toLong(), stream.totalFrames)
        val out = drain(stream, 1_000)
        assertEquals(stream.totalFrames * 2, out.size.toLong())
        assertTrue(stream.isFinished)
        assertEquals(0, stream.read(ShortArray(2_000), 1_000))
    }

    @Test
    fun `un cambio de parametros continuo no inicia crossfade y uno estructural si`() {
        val stream = LiveReverbStream(ShortArrayPcmSource(noisePcm(40_000, seed = 4)), rate, params, maxBlockFrames = 4_000)
        stream.setParams(params.copy(mix = 0.9f, decaySeconds = 3f, dampingHz = 4_000f))
        assertFalse(stream.isCrossfading)
        stream.setParams(params.copy(size = 0.9f))
        assertTrue(stream.isCrossfading)
        // El crossfade dura 80 ms: tras 4 000 muestras (> 3 528) ya terminó.
        stream.read(ShortArray(8_000), 4_000)
        assertFalse(stream.isCrossfading)
    }

    @Test
    fun `un cambio estructural en vivo no produce saltos ni valores invalidos`() {
        val frames = rate * 2
        val tone = ShortArray(frames * 2) { i -> (12_000.0 * sin(2.0 * PI * 220.0 * (i / 2) / rate)).toInt().toShort() }
        val stream = LiveReverbStream(ShortArrayPcmSource(tone), rate, params)
        val out = ArrayList<Short>()
        val buffer = ShortArray(512 * 2)
        var block = 0
        while (true) {
            if (block == 40) stream.setParams(params.copy(size = 1f, preDelayMs = 0f))
            val n = stream.read(buffer, 512)
            if (n == 0) break
            for (i in 0 until n * 2) out.add(buffer[i])
            block++
        }
        val left = FloatArray(out.size / 2) { out[2 * it] / 32768f }
        var largestStep = 0f
        // Solo mientras suena el tono: al agotarse el origen el seco cae a cero de golpe (el tono no termina en un cruce por cero).
        for (i in 1 until frames) largestStep = maxOf(largestStep, abs(left[i] - left[i - 1]))
        val peak = left.maxOf { abs(it) }
        // Todo el contenido está cerca de 220 Hz: avanza como máximo ~0.03 de su pico por muestra; un clic lo superaría de lejos.
        assertTrue("salto $largestStep con pico $peak", largestStep < 0.2f * peak)
    }

    @Test
    fun `tras un seek la cola ya suena gracias al pre-roll`() {
        val frames = rate * 5
        val dry = noisePcm(frames, seed = 5)
        val wetOnly = ReverbParams(mix = 1f, decaySeconds = 2f, preDelayMs = 0f)
        val seekAt = rate * 3L
        val window = rate / 5

        fun rms(pcm: ShortArray): Double {
            var sum = 0.0
            for (i in 0 until pcm.size / 2) sum += (pcm[2 * i] / 32768.0).let { it * it }
            return sqrt(sum / (pcm.size / 2))
        }

        val continuous = LiveReverbStream(ShortArrayPcmSource(dry), rate, wetOnly)
        val all = drain(continuous, 1_024)
        val expected = rms(all.copyOfRange((seekAt * 2).toInt(), ((seekAt + window) * 2).toInt()))

        val sought = LiveReverbStream(ShortArrayPcmSource(dry), rate, wetOnly)
        sought.seekTo(seekAt)
        assertEquals(seekAt, sought.positionFrames)
        val buffer = ShortArray(window * 2)
        // El bloque máximo por defecto es 1 024 muestras: se lee la ventana en bloques.
        val collected = ShortArray(window * 2)
        var done = 0
        while (done < window) {
            val n = sought.read(buffer, minOf(1_024, window - done))
            System.arraycopy(buffer, 0, collected, done * 2, n * 2)
            done += n
        }
        val actual = rms(collected)
        assertTrue("RMS tras seek $actual frente a continuo $expected", actual in expected * 0.75..expected * 1.33)
    }

    @Test
    fun `un seek fuera de rango se acota y un bloque mayor al maximo se rechaza`() {
        val stream = LiveReverbStream(ShortArrayPcmSource(noisePcm(3_000, seed = 6)), rate, params, maxBlockFrames = 256)
        stream.seekTo(Long.MAX_VALUE)
        assertEquals(stream.totalFrames, stream.positionFrames)
        stream.seekTo(-5L)
        assertEquals(0L, stream.positionFrames)
        val failed = runCatching { stream.read(ShortArray(2_000), 1_000) }.isFailure
        assertTrue(failed)
    }

    // ---------------------------------------------------------------- calentamiento con presupuesto de tiempo

    /** Primer bloque de audio tras reposicionarse en [frame] con el presupuesto dado (null = calentamiento completo). */
    private fun readAfterSeek(frame: Long, budgetNanos: Long?): ShortArray {
        val stream = LiveReverbStream(ShortArrayPcmSource(noisePcm(rate * 6, seed = 11)), rate, params, maxBlockFrames = 1_024)
        if (budgetNanos == null) stream.seekTo(frame) else stream.seekTo(frame, budgetNanos)
        assertEquals(frame, stream.positionFrames)
        val out = ShortArray(1_024 * 2)
        assertEquals(1_024, stream.read(out, 1_024))
        return out
    }

    @Test
    fun `un presupuesto generoso da exactamente el mismo audio que no tenerlo`() {
        val frame = rate * 3L
        assertArrayEquals(readAfterSeek(frame, null), readAfterSeek(frame, 60_000_000_000L))
    }

    @Test
    fun `un presupuesto agotado llega igual al destino con audio valido y con menos cola que el calentamiento completo`() {
        val frame = rate * 3L
        val warm = readAfterSeek(frame, null)
        val cold = readAfterSeek(frame, 0L)
        assertFalse("Sin presupuesto el calentamiento debe recortarse", warm.contentEquals(cold))
        assertTrue(cold.any { it.toInt() != 0 })
    }

    @Test
    fun `un presupuesto agotado cerca del inicio no falla`() {
        for (frame in listOf(0L, 1L, 500L, rate.toLong())) readAfterSeek(frame, 0L)
    }
}
