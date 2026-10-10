package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `mixInto` acumula los clips en un solo buffer (en vez de conservar uno por
 * clip). Estos tests fijan su contrato contra [referenceMix], una implementación
 * de referencia deliberadamente ingenua (suma saturada sobre un buffer nuevo,
 * todos los buffers a la vez) que vive SOLO en el test: es el oráculo, no
 * código de producción.
 */
class AudioMixIntoTest {

    /** Oráculo: suma sample a sample saturando en los límites de un `Short`; mide lo que el buffer más largo. */
    private fun referenceMix(buffers: List<ShortArray>): ShortArray {
        if (buffers.isEmpty()) return ShortArray(0)
        val out = ShortArray(buffers.maxOf { it.size })
        for (buf in buffers) {
            for (i in buf.indices) {
                val sum = out[i].toInt() + buf[i].toInt()
                out[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }

    @Test
    fun `acumular con mixInto da lo mismo que la mezcla de referencia`() {
        val a = shortArrayOf(30000, -30000, 100, 0)
        val b = shortArrayOf(30000, -30000, 200, 5)
        val c = shortArrayOf(1, 1, 1, 1)
        val expected = referenceMix(listOf(a.copyOf(), b.copyOf(), c.copyOf()))

        var acc = a.copyOf()
        acc = AudioProcessor.mixInto(acc, b)
        acc = AudioProcessor.mixInto(acc, c)

        assertArrayEquals(expected, acc)
    }

    @Test
    fun `satura en los limites de un Short en vez de desbordar`() {
        val acc = AudioProcessor.mixInto(shortArrayOf(32000, -32000), shortArrayOf(32000, -32000))
        assertArrayEquals(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), acc)
    }

    @Test
    fun `devuelve el mismo acumulador cuando los largos coinciden`() {
        val acc = shortArrayOf(1, 2, 3)
        val result = AudioProcessor.mixInto(acc, shortArrayOf(1, 1, 1))
        assertSame(acc, result)
        assertArrayEquals(shortArrayOf(2, 3, 4), result)
    }

    @Test
    fun `crece si el buffer sumado es mas largo`() {
        val result = AudioProcessor.mixInto(shortArrayOf(1, 2), shortArrayOf(1, 1, 1, 1))
        assertArrayEquals(shortArrayOf(2, 3, 1, 1), result)
    }

    @Test
    fun `no modifica el buffer que se suma`() {
        val buffer = shortArrayOf(5, 6, 7)
        AudioProcessor.mixInto(shortArrayOf(1, 1, 1), buffer)
        assertArrayEquals(shortArrayOf(5, 6, 7), buffer)
    }
}
