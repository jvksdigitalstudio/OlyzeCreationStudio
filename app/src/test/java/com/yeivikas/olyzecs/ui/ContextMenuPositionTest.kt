package com.yeivikas.olyzecs.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [computeContextMenuOffset] — regla de posición del menú
 * contextual de clip/carril de audio (`ClipPopupMenu`): al costado del
 * ancla, nunca fuera de la ventana. Regresión del bug "el menú ocupa todo
 * el ancho de la pantalla y sale lejos del clip".
 */
class ContextMenuPositionTest {

    private val window = IntSize(1000, 2000)
    private val menu = IntSize(300, 800)
    private val gap = 10
    private val margin = 20

    @Test
    fun `entra a la derecha - se pega al costado derecho y alinea con el tope del clip`() {
        val clip = IntRect(100, 500, 300, 560)
        val o = computeContextMenuOffset(clip, window, menu, gap, margin)
        assertEquals(IntOffset(310, 500), o)
    }

    @Test
    fun `no entra a la derecha - pasa al costado izquierdo`() {
        val clip = IntRect(600, 500, 900, 560)
        val o = computeContextMenuOffset(clip, window, menu, gap, margin)
        assertEquals(IntOffset(600 - gap - menu.width, 500), o)
    }

    @Test
    fun `el menu no se sale por abajo`() {
        val clip = IntRect(100, 1900, 300, 1960)
        val o = computeContextMenuOffset(clip, window, menu, gap, margin)
        assertEquals(window.height - menu.height - margin, o.y)
    }

    @Test
    fun `ancla de ancho completo - se centra debajo`() {
        val lane = IntRect(0, 400, 1000, 460)
        val o = computeContextMenuOffset(lane, window, menu, gap, margin)
        assertEquals(IntOffset(350, 470), o)
    }

    @Test
    fun `ancla de ancho completo sin lugar debajo - va arriba`() {
        val lane = IntRect(0, 1500, 1000, 1560)
        val o = computeContextMenuOffset(lane, window, menu, gap, margin)
        assertEquals(1500 - gap - menu.height, o.y)
    }

    @Test
    fun `clip parcialmente fuera de pantalla usa solo su parte visible`() {
        // Clip desde -500 hasta 150: visible 0..150 -> entra a la derecha.
        val clip = IntRect(-500, 500, 150, 560)
        val o = computeContextMenuOffset(clip, window, menu, gap, margin)
        assertEquals(IntOffset(160, 500), o)
    }

    @Test
    fun `resultado siempre dentro de la ventana`() {
        val anchors = listOf(
            IntRect(-300, -50, 100, 10),
            IntRect(900, 1990, 1400, 2100),
            IntRect(0, 0, 1000, 2000),
            IntRect(450, 900, 550, 960)
        )
        for (a in anchors) {
            val o = computeContextMenuOffset(a, window, menu, gap, margin)
            assertTrue("x=${o.x} fuera para $a", o.x >= margin && o.x + menu.width <= window.width - margin)
            assertTrue("y=${o.y} fuera para $a", o.y >= margin && o.y + menu.height <= window.height - margin)
        }
    }
}
