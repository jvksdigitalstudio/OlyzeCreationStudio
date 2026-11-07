package com.yeivikas.olyzecs.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EditBackgroundSettingsTest {

    @Test
    fun `el valor por defecto es el color del lienzo`() {
        assertEquals(EditBackgroundChoice.CANVAS, EditBackgroundSettings.DEFAULT.choice)
        assertEquals(EditBackgroundSettings.DEFAULT, EditBackgroundSettings())
    }

    @Test
    fun `cada opcion sobrevive a guardar y leer`() {
        for (choice in EditBackgroundChoice.entries) {
            val original = EditBackgroundSettings(choice, customArgb = 0xFF336699.toInt())
            assertEquals("falló con $choice", original, EditBackgroundSettings.decode(original.encode()))
        }
    }

    @Test
    fun `el color personalizado se conserva aunque se elija otra opcion`() {
        val original = EditBackgroundSettings(EditBackgroundChoice.BLACK, customArgb = 0xFFAA5500.toInt())
        assertEquals(0xFFAA5500.toInt(), EditBackgroundSettings.decode(original.encode()).customArgb)
    }

    @Test
    fun `un valor ausente o corrupto equivale a no haber elegido nada`() {
        val corrupt = listOf(null, "", "   ", "basura", "BLACK", ":123", "BLACK:", "BLACK:abc", "NO_EXISTE:5", "black:5")
        for (raw in corrupt) {
            assertEquals("debería ser el valor por defecto con '$raw'", EditBackgroundSettings.DEFAULT, EditBackgroundSettings.decode(raw))
        }
    }

    @Test
    fun `el alfa del color personalizado se fuerza a opaco al leer`() {
        // 0x00FF0000 (rojo con alfa 0) guardado a mano o por una versión futura: un fondo nunca puede ser transparente.
        val decoded = EditBackgroundSettings.decode("CUSTOM:${0x00FF0000}")
        assertEquals(0xFFFF0000.toInt(), decoded.customArgb)
    }

    @Test
    fun `los valores negativos de Int (colores opacos) se leen bien`() {
        val argb = 0xFF123456.toInt()   // negativo como Int
        assertEquals(argb, EditBackgroundSettings.decode("CUSTOM:$argb").customArgb)
    }
}
