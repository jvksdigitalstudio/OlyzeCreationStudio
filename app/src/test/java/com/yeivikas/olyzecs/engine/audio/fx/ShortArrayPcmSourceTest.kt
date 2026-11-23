package com.yeivikas.olyzecs.engine.audio.fx

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortArrayPcmSourceTest {
    private val source = ShortArrayPcmSource(shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) // 4 muestras por canal

    @Test
    fun `lee desde el medio y devuelve lo copiado`() {
        val out = ShortArray(4)
        assertEquals(2, source.read(1, out, 2))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6), out)
    }

    @Test
    fun `al final devuelve menos y mas alla devuelve cero`() {
        val out = ShortArray(8)
        assertEquals(1, source.read(3, out, 4))
        assertEquals(7.toShort(), out[0])
        assertEquals(0, source.read(4, out, 4))
        assertEquals(0, source.read(-1, out, 4))
        assertEquals(4L, source.frames)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un arreglo impar no es estereo`() {
        ShortArrayPcmSource(shortArrayOf(1, 2, 3))
    }
}
