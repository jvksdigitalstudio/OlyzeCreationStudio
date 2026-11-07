package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.data.EditBackgroundChoice
import com.yeivikas.olyzecs.data.EditBackgroundSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class EditBackgroundStateTest {

    private val saved = mutableListOf<EditBackgroundSettings>()

    private fun newState(initial: EditBackgroundSettings = EditBackgroundSettings.DEFAULT) =
        EditBackgroundState(initial) { saved += it }

    @Test
    fun `arranca con lo que se leyo de las preferencias`() {
        val initial = EditBackgroundSettings(EditBackgroundChoice.GRAY, 0xFF010203.toInt())
        assertEquals(initial, newState(initial).settings)
        assertEquals("leer no debe volver a guardar", 0, saved.size)
    }

    @Test
    fun `un toque rota y guarda cada cambio`() {
        val state = newState()
        state.cycle()
        state.cycle()
        assertEquals(EditBackgroundChoice.BLACK, state.settings.choice)
        assertEquals(listOf(EditBackgroundChoice.CHECKER_LIGHT, EditBackgroundChoice.BLACK), saved.map { it.choice })
    }

    @Test
    fun `elegir la opcion que ya esta activa no escribe en disco`() {
        val state = newState(EditBackgroundSettings(EditBackgroundChoice.WHITE))
        state.select(EditBackgroundChoice.WHITE)
        assertEquals(0, saved.size)
    }

    @Test
    fun `el color personalizado se activa, se fuerza opaco y se conserva`() {
        val state = newState()
        state.setCustom(0x00AABBCC)
        assertEquals(EditBackgroundChoice.CUSTOM, state.settings.choice)
        assertEquals(0xFFAABBCC.toInt(), state.settings.customArgb)

        state.select(EditBackgroundChoice.BLACK)
        assertEquals("cambiar de opción no debe perder el color personalizado", 0xFFAABBCC.toInt(), state.settings.customArgb)
        assertEquals(2, saved.size)
    }
}
