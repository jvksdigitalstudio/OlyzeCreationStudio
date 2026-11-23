package com.yeivikas.olyzecs.engine.audio.fx

import com.yeivikas.olyzecs.engine.audio.fx.DspTestSignals.noise
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** El modelo de efectos ([AudioEffect]) y la cadena que los aplica a PCM de 16 bits ([applyEffectChain]). */
class AudioEffectChainTest {
    private val rate = 44_100

    private fun pcm(frames: Int, amplitude: Float = 0.3f, seed: Long = 1): ShortArray {
        val l = noise(frames, amplitude, seed)
        val r = noise(frames, amplitude, seed + 1)
        return ShortArray(frames * 2) { i -> (((if (i % 2 == 0) l[i / 2] else r[i / 2]) * 32767f).toInt()).toShort() }
    }

    // ---------------------------------------------------------------- modelo

    @Test
    fun `un efecto esta activo solo si esta habilitado y deja el reverb audible`() {
        assertTrue(ReverbEffect().isActive)
        assertFalse(ReverbEffect(enabled = false).isActive)
        assertFalse(ReverbEffect(params = ReverbParams(mix = 0f)).isActive)
    }

    @Test
    fun `cada instancia nueva lleva un id propio`() {
        assertNotEquals(ReverbEffect().id, ReverbEffect().id)
    }

    @Test
    fun `la cola de un efecto es la de sus parametros`() {
        val effect = ReverbEffect(params = ReverbParams(preDelayMs = 50f, decaySeconds = 4f))
        assertEquals(effect.params.tailSeconds(), effect.tailSeconds, 0.0)
    }

    // ---------------------------------------------------------------- cadena

    @Test
    fun `sin efectos activos devuelve el mismo arreglo, sin tocarlo`() {
        val input = pcm(5_000)
        assertSame(input, applyEffectChain(input, rate, emptyList()))
        assertSame(input, applyEffectChain(input, rate, listOf(ReverbEffect(enabled = false))))
        assertSame(input, applyEffectChain(input, rate, listOf(ReverbEffect(params = ReverbParams(mix = 0f)))))
    }

    @Test
    fun `con un reverb activo cambia la senal pero conserva el largo`() {
        val input = pcm(20_000)
        val output = applyEffectChain(input, rate, listOf(ReverbEffect()))
        assertEquals(input.size, output.size)
        assertFalse(input.contentEquals(output))
    }

    @Test
    fun `la cola del reverb sigue sonando sobre el silencio que sigue a la senal`() {
        val signal = pcm(4_000, amplitude = 0.5f)
        val withTail = ShortArray(signal.size + 2 * rate)
        signal.copyInto(withTail)
        val output = applyEffectChain(withTail, rate, listOf(ReverbEffect(params = ReverbParams(mix = 0.5f, decaySeconds = 3f))))
        val tailStart = signal.size + rate / 2
        assertTrue("La cola se cortó en seco", (tailStart until tailStart + rate / 4).any { output[it].toInt() != 0 })
    }

    @Test
    fun `el silencio de entrada da silencio de salida`() {
        val output = applyEffectChain(ShortArray(10_000), rate, listOf(ReverbEffect()))
        assertTrue(output.all { it.toInt() == 0 })
    }

    @Test
    fun `la conversion a 16 bits redondea y satura en vez de desbordar`() {
        assertEquals(0, floatToPcm16(0f).toInt())
        assertEquals(16_384, floatToPcm16(0.5f).toInt())
        assertEquals(-16_384, floatToPcm16(-0.5f).toInt())
        assertEquals(Short.MAX_VALUE.toInt(), floatToPcm16(1.5f).toInt())
        assertEquals(Short.MIN_VALUE.toInt(), floatToPcm16(-1.5f).toInt())
        assertEquals(Short.MAX_VALUE.toInt(), floatToPcm16(1e9f).toInt())
        assertEquals(0, floatToPcm16(Float.NaN).toInt())
    }

    @Test
    fun `es determinista`() {
        val input = pcm(15_000)
        val chain = listOf(ReverbEffect(params = ReverbParams(modulation = 1f)))
        assertArrayEquals(applyEffectChain(input, rate, chain), applyEffectChain(input, rate, chain))
    }

    @Test
    fun `un PCM de tamano impar no es estereo y se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) { applyEffectChain(ShortArray(5), rate, listOf(ReverbEffect())) }
    }

    @Test
    fun `con mix al maximo la senal seca desaparece y queda solo el reverb`() {
        val input = pcm(8_000, amplitude = 0.5f)
        val wetOnly = applyEffectChain(input, rate, listOf(ReverbEffect(params = ReverbParams(mix = 1f, preDelayMs = 250f))))
        // Con 250 ms de pre-delay, los primeros 100 ms del reverb son silencio y no queda nada de la señal seca.
        assertTrue((0 until rate / 10 * 2).all { kotlin.math.abs(wetOnly[it].toInt()) <= 1 })
    }
}
