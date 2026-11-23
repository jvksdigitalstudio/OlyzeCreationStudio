package com.yeivikas.olyzecs.engine.effects.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectPaddingCalculatorTest {

    @Test
    fun `un plan vacio no anade margen`() {
        assertEquals(EffectPadding.ZERO, EffectPaddingCalculator.calculate(EffectPassPlan.EMPTY, 800, 600))
    }

    @Test
    fun `ningun tipo de pase declara hoy un margen negativo ni lanza`() {
        // Recorre TODOS los tipos: si se añade uno nuevo, esta prueba lo ejercita automáticamente.
        for (kind in EffectPassKind.entries) {
            val padding = EffectPaddingCalculator.calculate(EffectPassPlan(listOf(kind)), 1000, 500)
            assertTrue("$kind: ${padding}", padding.left >= 0 && padding.top >= 0 && padding.right >= 0 && padding.bottom >= 0)
        }
    }

    @Test
    fun `los pases de color e identidad no extienden la imagen`() {
        assertTrue(EffectPaddingCalculator.calculate(EffectPassPlan(listOf(EffectPassKind.COLOR_BASIC)), 640, 480).isZero)
        assertTrue(EffectPaddingCalculator.calculate(EffectPassPlan.IDENTITY_ONLY, 640, 480).isZero)
    }

    @Test
    fun `dimensiones invalidas se rechazan`() {
        assertThrows(IllegalArgumentException::class.java) { EffectPaddingCalculator.calculate(EffectPassPlan.EMPTY, 0, 100) }
        assertThrows(IllegalArgumentException::class.java) { EffectPaddingCalculator.calculate(EffectPassPlan.EMPTY, 100, -1) }
    }

    @Test
    fun `un margen negativo no se puede construir`() {
        assertThrows(IllegalArgumentException::class.java) { EffectPadding(-1, 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { EffectPadding(0, 0, 0, -5) }
    }

    @Test
    fun `combinar margenes toma el maximo por lado`() {
        val merged = EffectPadding(10, 0, 4, 7).maxWith(EffectPadding(3, 9, 4, 2))
        assertEquals(EffectPadding(10, 9, 4, 7), merged)
        assertFalse(merged.isZero)
    }
}
