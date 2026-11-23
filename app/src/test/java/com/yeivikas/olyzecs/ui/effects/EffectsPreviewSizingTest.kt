package com.yeivikas.olyzecs.ui.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsPreviewSizingTest {

    /**
     * Modelo EXACTO de `ImageDecoding.computeSampleSize` (el menor divisor potencia de dos con `lado / k <= límite`,
     * división entera) y del tamaño que produce `BitmapFactory` (redondeo hacia arriba). Una prueba estructural vigila
     * que el decodificador real siga siendo ese.
     */
    private fun decodedSide(rawLongSide: Int, limit: Int): Int {
        var sample = 1
        while (rawLongSide / sample > limit) sample *= 2
        return (rawLongSide + sample - 1) / sample
    }

    @Test
    fun `usa el lado mayor de la pantalla por la fraccion que ocupa la imagen`() {
        assertEquals(1440, EffectsPreviewSizing.displayTargetPx(1600, 1200, 0.9f))
        assertEquals(1440, EffectsPreviewSizing.displayTargetPx(1200, 1600, 0.9f))
    }

    @Test
    fun `sin medir la pantalla devuelve el objetivo por defecto`() {
        val fallback = EffectsPreviewSizing.DISPLAY_TARGET_FALLBACK
        assertEquals(fallback, EffectsPreviewSizing.displayTargetPx(0, 0, 0.9f))
        assertEquals(fallback, EffectsPreviewSizing.displayTargetPx(1600, 1200, Float.NaN))
        assertEquals(fallback, EffectsPreviewSizing.displayTargetPx(1600, 1200, 0f))
    }

    @Test
    fun `el objetivo nunca baja del doble de la copia rapida ni supera el techo`() {
        assertEquals(EffectsPreviewSizing.QUICK_MAX_DIMENSION * 2, EffectsPreviewSizing.displayTargetPx(100, 100, 0.9f))
        assertEquals(EffectsPreviewSizing.DISPLAY_TARGET_CEILING, EffectsPreviewSizing.displayTargetPx(8000, 6000, 0.9f))
    }

    @Test
    fun `la copia de reposo siempre es mejor que la de arrastre`() {
        for (side in listOf(0, 200, 800, 1600, 3000, 9000)) {
            assertTrue(EffectsPreviewSizing.displayTargetPx(side, side, 0.9f) > EffectsPreviewSizing.QUICK_MAX_DIMENSION)
        }
    }

    @Test
    fun `el limite de decodificacion es el doble del objetivo menos uno`() {
        assertEquals(1439 * 2 + 1, EffectsPreviewSizing.decodeLimitFor(1440))
        assertEquals(1, EffectsPreviewSizing.decodeLimitFor(1))
        assertThrows(IllegalArgumentException::class.java) { EffectsPreviewSizing.decodeLimitFor(0) }
    }

    @Test
    fun `con el limite calculado la copia decodificada nunca baja del objetivo si el original lo alcanza`() {
        // La garantía central: barrido de objetivos y de tamaños de original contra el modelo exacto de muestreo.
        for (target in 520..EffectsPreviewSizing.DISPLAY_TARGET_CEILING step 13) {
            val limit = EffectsPreviewSizing.decodeLimitFor(target)
            for (raw in target..12_000 step 7) {
                val decoded = decodedSide(raw, limit)
                assertTrue("objetivo $target, original $raw → $decoded", decoded >= target)
                assertTrue("no debe superar el límite pedido (+1 por el redondeo)", decoded <= limit + 1)
            }
        }
    }

    @Test
    fun `pedir el objetivo como limite si perdia hasta la mitad - el defecto que esto corrige`() {
        // Documenta el comportamiento anterior: con límite = objetivo, una imagen de 2100 px quedaba en 1050 px.
        assertEquals(1050, decodedSide(2100, 2048))
        assertTrue(decodedSide(2100, 2048) < 2048)
        assertEquals(2100, decodedSide(2100, EffectsPreviewSizing.decodeLimitFor(2048)))
    }

    @Test
    fun `un original menor que el objetivo se decodifica entero`() {
        val limit = EffectsPreviewSizing.decodeLimitFor(1440)
        assertEquals(900, decodedSide(900, limit))
    }

    @Test
    fun `el coste en memoria queda acotado por el techo`() {
        val limit = EffectsPreviewSizing.decodeLimitFor(EffectsPreviewSizing.DISPLAY_TARGET_CEILING)
        val worst = (1..20_000 step 3).maxOf { decodedSide(it, limit) }
        assertTrue("el mayor lado posible es ${EffectsPreviewSizing.DISPLAY_TARGET_CEILING * 2}", worst <= EffectsPreviewSizing.DISPLAY_TARGET_CEILING * 2)
    }

    @Test
    fun `el limite de pantalla combina objetivo y garantia`() {
        assertEquals(
            EffectsPreviewSizing.decodeLimitFor(EffectsPreviewSizing.displayTargetPx(1600, 1200, 0.9f)),
            EffectsPreviewSizing.displayDecodeLimit(1600, 1200, 0.9f)
        )
    }
}
