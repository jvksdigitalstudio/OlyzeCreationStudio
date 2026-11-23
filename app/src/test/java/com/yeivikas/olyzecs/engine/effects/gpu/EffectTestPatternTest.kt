package com.yeivikas.olyzecs.engine.effects.gpu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectTestPatternTest {

    @Test
    fun `tiene el tamano esperado y no es potencia de dos`() {
        val data = EffectTestPattern.rgba()
        assertEquals(EffectTestPattern.WIDTH * EffectTestPattern.HEIGHT * 4, data.size)
        assertTrue("ancho no potencia de 2", EffectTestPattern.WIDTH and (EffectTestPattern.WIDTH - 1) != 0)
        assertTrue("alto no potencia de 2", EffectTestPattern.HEIGHT and (EffectTestPattern.HEIGHT - 1) != 0)
    }

    @Test
    fun `es determinista`() {
        assertArrayEquals(EffectTestPattern.rgba(40, 30), EffectTestPattern.rgba(40, 30))
    }

    @Test
    fun `los canales varian a lo largo de la imagen para detectar desplazamientos y volteos`() {
        val w = 64; val h = 48
        val d = EffectTestPattern.rgba(w, h)
        fun px(x: Int, y: Int, c: Int) = d[(y * w + x) * 4 + c].toInt() and 0xFF
        assertNotEquals(px(0, 0, 0), px(w - 1, 0, 0))       // rojo cambia en horizontal
        assertNotEquals(px(0, 0, 1), px(0, h - 1, 1))       // verde cambia en vertical
        assertEquals(0, px(0, 0, 0)); assertEquals(255, px(w - 1, 0, 0))
        assertEquals(0, px(0, 0, 1)); assertEquals(255, px(0, h - 1, 1))
    }

    @Test
    fun `el alfa nunca es cero para que el pase no pueda ocultar un error tras la transparencia`() {
        val d = EffectTestPattern.rgba(50, 50)
        for (i in 3 until d.size step 4) assertTrue(d[i].toInt() and 0xFF >= 64)
    }

    @Test
    fun `dimensiones invalidas se rechazan`() {
        assertThrows(IllegalArgumentException::class.java) { EffectTestPattern.rgba(0, 5) }
    }
}
