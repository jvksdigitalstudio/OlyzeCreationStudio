package com.yeivikas.olyzecs.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests de [AspectRatio] — value type puro (Fase A, ver ADR-005).
 * Todos los valores esperados están calculados a mano a partir de las
 * dimensiones reales de entrada, no inventados.
 */
class AspectRatioTest {

    @Test
    fun `of normaliza 1920x1080 a 16 por 9`() {
        val aspect = AspectRatio.of(1920, 1080)
        assertEquals(16, aspect.widthUnits)
        assertEquals(9, aspect.heightUnits)
    }

    @Test
    fun `of normaliza 1080x1920 a 9 por 16 (vertical)`() {
        val aspect = AspectRatio.of(1080, 1920)
        assertEquals(9, aspect.widthUnits)
        assertEquals(16, aspect.heightUnits)
    }

    @Test
    fun `of normaliza un cuadrado a 1 por 1 sin importar el tamaño`() {
        val aspect = AspectRatio.of(1080, 1080)
        assertEquals(1, aspect.widthUnits)
        assertEquals(1, aspect.heightUnits)
    }

    @Test
    fun `of normaliza 1080x1350 (feed vertical) a 4 por 5`() {
        val aspect = AspectRatio.of(1080, 1350)
        assertEquals(4, aspect.widthUnits)
        assertEquals(5, aspect.heightUnits)
    }

    @Test
    fun `ratio devuelve el ancho sobre alto como float, no como string`() {
        val aspect = AspectRatio(16, 9)
        assertEquals(16f / 9f, aspect.ratio, 0.0001f)
    }

    @Test
    fun `ratio funciona para relaciones que no simplifican a un par chico (cine 2_39 por 1)`() {
        // 2.39:1 expresado en enteros grandes (239:100) — el par no es
        // "bonito", pero el ratio calculado sigue siendo el correcto.
        val aspect = AspectRatio(239, 100)
        assertEquals(2.39f, aspect.ratio, 0.001f)
    }

    @Test
    fun `toString devuelve el formato legible ancho dos puntos alto`() {
        assertEquals("16:9", AspectRatio(16, 9).toString())
        assertEquals("9:16", AspectRatio(9, 16).toString())
    }

    @Test
    fun `of rechaza ancho cero o negativo`() {
        assertThrows(IllegalArgumentException::class.java) { AspectRatio.of(0, 1080) }
        assertThrows(IllegalArgumentException::class.java) { AspectRatio.of(-100, 1080) }
    }

    @Test
    fun `of rechaza alto cero o negativo`() {
        assertThrows(IllegalArgumentException::class.java) { AspectRatio.of(1920, 0) }
        assertThrows(IllegalArgumentException::class.java) { AspectRatio.of(1920, -100) }
    }

    @Test
    fun `constructor rechaza unidades no positivas`() {
        assertThrows(IllegalArgumentException::class.java) { AspectRatio(0, 9) }
        assertThrows(IllegalArgumentException::class.java) { AspectRatio(16, 0) }
    }
}
