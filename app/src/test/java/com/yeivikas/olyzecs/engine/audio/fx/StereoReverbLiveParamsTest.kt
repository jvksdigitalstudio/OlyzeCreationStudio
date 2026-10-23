package com.yeivikas.olyzecs.engine.audio.fx

import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.noise
import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.peak
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Actualización EN VIVO de los parámetros del reverb ([StereoReverb.updateParams]): lo que exige un motor de reproducción
 * en tiempo real. Garantías que se prueban: sin cambios no cambia ni una muestra; el resultado no depende de cómo se parta
 * el audio en bloques; los parámetros llegan a su destino exacto; moverlos no produce saltos; ningún barrido produce
 * valores no finitos ni explosiones; y lo estructural (size, pre-delay) se rechaza en vez de aplicarse a medias.
 */
class StereoReverbLiveParamsTest {
    private val rate = 44_100
    private val base = ReverbParams(mix = 0.3f)

    private fun renderInChunks(reverb: StereoReverb, left: FloatArray, right: FloatArray, chunk: Int): Pair<FloatArray, FloatArray> {
        val outL = FloatArray(left.size)
        val outR = FloatArray(right.size)
        var from = 0
        while (from < left.size) {
            val to = minOf(from + chunk, left.size)
            val l = left.copyOfRange(from, to)
            val r = right.copyOfRange(from, to)
            reverb.process(l, r, l, r, to - from)
            l.copyInto(outL, from)
            r.copyInto(outR, from)
            from = to
        }
        return outL to outR
    }

    @Test
    fun `actualizar con los mismos parametros no cambia ni una muestra`() {
        val left = noise(30_000, 0.3f, seed = 11)
        val right = noise(30_000, 0.3f, seed = 12)
        val reference = renderInChunks(StereoReverb(rate, base), left, right, chunk = 30_000)
        val updated = StereoReverb(rate, base).also { check(it.updateParams(base)) }
        val result = renderInChunks(updated, left, right, chunk = 1_000)
        assertArrayEquals(reference.first, result.first, 0f)
        assertArrayEquals(reference.second, result.second, 0f)
    }

    @Test
    fun `el resultado no depende de como se parta el audio en bloques`() {
        val left = noise(40_000, 0.3f, seed = 21)
        val right = noise(40_000, 0.3f, seed = 22)
        val moved = base.copy(mix = 0.9f, decaySeconds = 6f, dampingHz = 3_000f, lowCutHz = 250f, highCutHz = 6_000f, width = 0.2f)
        fun run(chunk: Int): Pair<FloatArray, FloatArray> {
            val reverb = StereoReverb(rate, base)
            check(reverb.updateParams(moved))
            return renderInChunks(reverb, left, right, chunk)
        }
        val whole = run(40_000)
        for (chunk in listOf(1, 37, 64, 1_023)) {
            val parts = run(chunk)
            assertArrayEquals("chunk $chunk (izquierdo)", whole.first, parts.first, 0f)
            assertArrayEquals("chunk $chunk (derecho)", whole.second, parts.second, 0f)
        }
    }

    @Test
    fun `size y pre-delay son estructurales y se rechazan sin aplicar nada`() {
        val moved = base.copy(mix = 0.5f, decaySeconds = 4f)
        val rejected = StereoReverb(rate, base)
        assertFalse(rejected.updateParams(base.copy(size = 0.9f)))
        assertFalse(rejected.updateParams(base.copy(preDelayMs = 120f)))
        assertFalse(rejected.updateParams(moved.copy(size = 0.9f)))
        // Un rechazo no deja la instancia a medias: después de rechazos suena exactamente igual que una que nunca los recibió.
        assertTrue(rejected.updateParams(moved))
        val clean = StereoReverb(rate, base).also { check(it.updateParams(moved)) }
        val left = noise(20_000, 0.3f, seed = 31)
        val right = noise(20_000, 0.3f, seed = 32)
        val a = renderInChunks(rejected, left, right, 20_000)
        val b = renderInChunks(clean, left, right, 20_000)
        assertArrayEquals(b.first, a.first, 0f)
        assertArrayEquals(b.second, a.second, 0f)
    }

    @Test
    fun `mix a cero converge a la senal original exacta`() {
        val frames = rate * 3
        val left = noise(frames, 0.3f, seed = 41)
        val right = noise(frames, 0.3f, seed = 42)
        val reverb = StereoReverb(rate, base.copy(mix = 0.8f))
        val firstHalf = renderInChunks(reverb, left.copyOfRange(0, rate), right.copyOfRange(0, rate), 512)
        check(firstHalf.first.isNotEmpty())
        check(reverb.updateParams(base.copy(mix = 0f)))
        val rest = renderInChunks(reverb, left.copyOfRange(rate, frames), right.copyOfRange(rate, frames), 512)
        // Pasados ~1.5 s (50 constantes de tiempo) el seco es exactamente la entrada.
        val from = rate * 3 / 2
        val expectedL = left.copyOfRange(rate + from, frames)
        val expectedR = right.copyOfRange(rate + from, frames)
        assertArrayEquals(expectedL, rest.first.copyOfRange(from, rest.first.size), 0f)
        assertArrayEquals(expectedR, rest.second.copyOfRange(from, rest.second.size), 0f)
    }

    @Test
    fun `subir el mix de golpe no produce saltos en la salida`() {
        val frames = rate * 2
        val sine = FloatArray(frames) { (0.5 * sin(2.0 * PI * 440.0 * it / rate)).toFloat() }
        val reverb = StereoReverb(rate, base.copy(mix = 0f))
        val before = renderInChunks(reverb, sine.copyOfRange(0, rate), sine.copyOfRange(0, rate), 256)
        check(reverb.updateParams(base.copy(mix = 1f)))
        val after = renderInChunks(reverb, sine.copyOfRange(rate, frames), sine.copyOfRange(rate, frames), 256)
        val out = before.first + after.first
        var largestStep = 0f
        for (i in 1 until out.size) largestStep = maxOf(largestStep, abs(out[i] - out[i - 1]))
        // Todo el contenido está cerca de 440 Hz: un seno de ese tono avanza como máximo 2π·440/44100 ≈ 0.063 de su pico por muestra.
        // Un clic (cambio brusco de ganancia) lo superaría ampliamente.
        val limit = 0.12f * peak(out)
        assertTrue("salto máximo $largestStep con pico ${peak(out)}", largestStep < limit)
    }

    @Test
    fun `un barrido brusco de todos los continuos queda finito y acotado`() {
        val frames = rate * 8
        val left = noise(frames, 0.3f, seed = 51)
        val right = noise(frames, 0.3f, seed = 52)
        val random = java.util.Random(99)
        fun pick(range: ClosedFloatingPointRange<Float>, extremesOnly: Boolean): Float =
            if (extremesOnly) (if (random.nextBoolean()) range.start else range.endInclusive)
            else range.start + random.nextFloat() * (range.endInclusive - range.start)

        val reverb = StereoReverb(rate, base)
        val outL = FloatArray(frames)
        val outR = FloatArray(frames)
        var from = 0
        while (from < frames) {
            val to = minOf(from + 256, frames)
            val extremes = (from / 256) % 2 == 0
            check(
                reverb.updateParams(
                    ReverbParams(
                        mix = pick(ReverbRanges.MIX, extremes),
                        decaySeconds = pick(ReverbRanges.DECAY_SECONDS, extremes),
                        preDelayMs = base.preDelayMs,
                        size = base.size,
                        dampingHz = pick(ReverbRanges.DAMPING_HZ, extremes),
                        lowCutHz = pick(ReverbRanges.LOW_CUT_HZ, extremes),
                        highCutHz = pick(ReverbRanges.HIGH_CUT_HZ, extremes),
                        diffusion = pick(ReverbRanges.DIFFUSION, extremes),
                        modulation = pick(ReverbRanges.MODULATION, extremes),
                        width = pick(ReverbRanges.WIDTH, extremes),
                        earlyLevel = pick(ReverbRanges.EARLY_LEVEL, extremes)
                    )
                )
            )
            val l = left.copyOfRange(from, to)
            val r = right.copyOfRange(from, to)
            reverb.process(l, r, l, r, to - from)
            l.copyInto(outL, from)
            r.copyInto(outR, from)
            from = to
        }
        assertTrue("valores no finitos", outL.all { it.isFinite() } && outR.all { it.isFinite() })
        assertTrue("pico ${peak(outL)}", peak(outL) < 8f && peak(outR) < 8f)
    }
}
