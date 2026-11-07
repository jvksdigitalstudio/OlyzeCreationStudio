package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cantidad de rangos del botón "Seleccionar" (x1 … x7): flechas y arrastre vertical, puras ([AudioClipDetailSelectCountMath.kt]). */
class AudioClipDetailSelectCountMathTest {

    @Test
    fun `los limites son x1 por defecto y hasta x7`() {
        assertEquals(1, DETAIL_SELECT_MIN_COUNT)
        assertEquals(1, DETAIL_SELECT_DEFAULT_COUNT)
        assertEquals(7, DETAIL_SELECT_MAX_COUNT)
    }

    @Test
    fun `las flechas suman y restan de uno en uno`() {
        assertEquals(4, stepDetailSelectCount(3, +1))
        assertEquals(2, stepDetailSelectCount(3, -1))
    }

    @Test
    fun `las flechas no pasan de x1 ni de x7`() {
        assertEquals(1, stepDetailSelectCount(1, -1))
        assertEquals(7, stepDetailSelectCount(7, +1))
    }

    @Test
    fun `arrastrar hacia arriba suma una unidad por paso`() {
        assertEquals(3, detailSelectCountFromDrag(startCount = 1, dragUpPx = 25f, stepPx = 10f))
    }

    @Test
    fun `arrastrar hacia abajo resta una unidad por paso`() {
        assertEquals(3, detailSelectCountFromDrag(startCount = 5, dragUpPx = -25f, stepPx = 10f))
    }

    @Test
    fun `un recorrido menor que un paso no cambia la cantidad`() {
        assertEquals(1, detailSelectCountFromDrag(1, 9.9f, 10f))
        assertEquals(4, detailSelectCountFromDrag(4, -9.9f, 10f))
    }

    @Test
    fun `el arrastre se acota a x1 y x7`() {
        assertEquals(7, detailSelectCountFromDrag(5, 1_000f, 10f))
        assertEquals(1, detailSelectCountFromDrag(2, -1_000f, 10f))
    }

    @Test
    fun `volver al punto de partida devuelve la cantidad original`() {
        assertEquals(4, detailSelectCountFromDrag(1, 30f, 10f)) // el dedo sube 30 px: x4
        assertEquals(1, detailSelectCountFromDrag(1, 0f, 10f))  // y vuelve al origen: x1 otra vez
    }

    @Test
    fun `sin paso valido la cantidad no cambia`() {
        assertEquals(3, detailSelectCountFromDrag(3, 500f, 0f))
    }

    @Test
    fun `el texto del numero es x seguido de la cantidad`() {
        assertEquals("x1", detailSelectCountLabel(1))
        assertEquals("x7", detailSelectCountLabel(7))
    }
}
