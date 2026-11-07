package com.yeivikas.olyzecs.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Contrato de color del botón REV de la cabecera de la ventana del audio: apagado, SOLO el ícono toma el color
 * de la capa (`accent`), como el resto de botones de la cabecera; etiqueta, fondo y borde quedan neutros.
 */
class DetailToggleColorsTest {

    private val green = Color(0xFF00B140)
    private val purple = Color(0xFF7C4DFF)

    @Test
    fun `apagado el icono usa el color de la capa`() {
        assertEquals(green, detailToggleColors(checked = false, accent = green).iconTint)
        assertEquals(purple, detailToggleColors(checked = false, accent = purple).iconTint)
    }

    @Test
    fun `apagado la letra, el fondo y el borde no dependen de la capa`() {
        val a = detailToggleColors(checked = false, accent = green)
        val b = detailToggleColors(checked = false, accent = purple)
        assertEquals(a.labelColor, b.labelColor)
        assertEquals(a.fillTop, b.fillTop)
        assertEquals(a.fillBottom, b.fillBottom)
        assertEquals(a.border, b.border)
    }

    @Test
    fun `encendido rellena con el color de la capa y contenido oscuro`() {
        val c = detailToggleColors(checked = true, accent = green)
        assertEquals(green, c.fillTop)
        assertEquals(green, c.border)
        assertNotEquals(green, c.iconTint)
        assertEquals(c.iconTint, c.labelColor)
    }

    @Test
    fun `los dos estados se distinguen entre si`() {
        assertNotEquals(
            detailToggleColors(checked = true, accent = green),
            detailToggleColors(checked = false, accent = green)
        )
    }
}
