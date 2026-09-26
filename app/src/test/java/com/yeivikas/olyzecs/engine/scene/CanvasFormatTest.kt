package com.yeivikas.olyzecs.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests de [CanvasFormat] y [Orientation] — Fase A (ver ADR-005).
 * [Orientation] se prueba acá porque solo se deriva a través de
 * [CanvasFormat]/[CanvasSpec], nunca se instancia sola con datos
 * arbitrarios.
 */
class CanvasFormatTest {

    @Test
    fun `orientation es PORTRAIT cuando el alto es mayor al ancho`() {
        val format = CanvasFormat(widthPx = 1080, heightPx = 1920)
        assertEquals(Orientation.PORTRAIT, format.orientation)
    }

    @Test
    fun `orientation es LANDSCAPE cuando el ancho es mayor al alto`() {
        val format = CanvasFormat(widthPx = 1920, heightPx = 1080)
        assertEquals(Orientation.LANDSCAPE, format.orientation)
    }

    @Test
    fun `orientation es SQUARE cuando ancho y alto son iguales`() {
        val format = CanvasFormat(widthPx = 1080, heightPx = 1080)
        assertEquals(Orientation.SQUARE, format.orientation)
    }

    @Test
    fun `orientation cambia correctamente en el borde exacto entre cuadrado y rectangular`() {
        // Un solo píxel de diferencia ya debe dejar de ser SQUARE.
        assertEquals(Orientation.SQUARE, CanvasFormat(1080, 1080).orientation)
        assertEquals(Orientation.LANDSCAPE, CanvasFormat(1081, 1080).orientation)
        assertEquals(Orientation.PORTRAIT, CanvasFormat(1080, 1081).orientation)
    }

    @Test
    fun `aspect delega correctamente en AspectRatio_of`() {
        val format = CanvasFormat(widthPx = 1920, heightPx = 1080)
        assertEquals(AspectRatio(16, 9), format.aspect)
    }

    @Test
    fun `constructor rechaza dimensiones no positivas`() {
        assertThrows(IllegalArgumentException::class.java) { CanvasFormat(0, 1080) }
        assertThrows(IllegalArgumentException::class.java) { CanvasFormat(1920, 0) }
        assertThrows(IllegalArgumentException::class.java) { CanvasFormat(-1, 1080) }
    }
}
