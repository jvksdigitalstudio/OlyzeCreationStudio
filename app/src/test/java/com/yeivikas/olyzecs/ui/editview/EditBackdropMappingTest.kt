package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.data.EditBackgroundChoice
import com.yeivikas.olyzecs.data.EditBackgroundSettings
import com.yeivikas.olyzecs.engine.render.PreviewBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditBackdropMappingTest {

    private fun backdropFor(choice: EditBackgroundChoice, custom: Int = EditBackgroundSettings.DEFAULT_CUSTOM_ARGB, cell: Int = 24) =
        EditBackgroundSettings(choice, custom).toBackdrop(cell)

    @Test
    fun `el color del lienzo no pinta nada propio`() {
        assertEquals(PreviewBackdrop.CanvasColor, backdropFor(EditBackgroundChoice.CANVAS))
    }

    @Test
    fun `los colores lisos usan la paleta`() {
        assertEquals(PreviewBackdrop.Solid(EditBackdropPalette.BLACK), backdropFor(EditBackgroundChoice.BLACK))
        assertEquals(PreviewBackdrop.Solid(EditBackdropPalette.GRAY), backdropFor(EditBackgroundChoice.GRAY))
        assertEquals(PreviewBackdrop.Solid(EditBackdropPalette.WHITE), backdropFor(EditBackgroundChoice.WHITE))
    }

    @Test
    fun `los tableros usan sus dos tonos y el lado de casilla pedido`() {
        assertEquals(
            PreviewBackdrop.Checkerboard(EditBackdropPalette.CHECKER_LIGHT_A, EditBackdropPalette.CHECKER_LIGHT_B, 24),
            backdropFor(EditBackgroundChoice.CHECKER_LIGHT)
        )
        assertEquals(
            PreviewBackdrop.Checkerboard(EditBackdropPalette.CHECKER_DARK_A, EditBackdropPalette.CHECKER_DARK_B, 24),
            backdropFor(EditBackgroundChoice.CHECKER_DARK)
        )
    }

    @Test
    fun `una casilla degenerada se corrige al minimo en vez de fallar`() {
        for (degenerate in listOf(0, 1, -5)) {
            val backdrop = backdropFor(EditBackgroundChoice.CHECKER_LIGHT, cell = degenerate) as PreviewBackdrop.Checkerboard
            assertEquals(PreviewBackdrop.MIN_CELL_PX, backdrop.cellPx)
        }
    }

    @Test
    fun `el color personalizado se pinta siempre opaco`() {
        val backdrop = backdropFor(EditBackgroundChoice.CUSTOM, custom = 0x00112233) as PreviewBackdrop.Solid
        assertEquals(0xFF112233.toInt(), backdrop.argb)
    }

    @Test
    fun `toda opcion tiene un fondo definido`() {
        for (choice in EditBackgroundChoice.entries) backdropFor(choice) // when exhaustivo: no lanza
    }

    @Test
    fun `la rotacion rapida recorre lienzo, tablero claro, negro y blanco`() {
        var current = EditBackgroundChoice.CANVAS
        val visited = mutableListOf(current)
        repeat(4) {
            current = current.nextInQuickCycle()
            visited += current
        }
        assertEquals(
            listOf(
                EditBackgroundChoice.CANVAS, EditBackgroundChoice.CHECKER_LIGHT,
                EditBackgroundChoice.BLACK, EditBackgroundChoice.WHITE, EditBackgroundChoice.CANVAS
            ),
            visited
        )
    }

    @Test
    fun `desde una opcion fuera de la rotacion rapida se vuelve al color del lienzo`() {
        for (choice in listOf(EditBackgroundChoice.CHECKER_DARK, EditBackgroundChoice.GRAY, EditBackgroundChoice.CUSTOM)) {
            assertTrue("$choice no debería estar en la rotación rápida", choice !in EDIT_BACKGROUND_QUICK_CYCLE)
            assertEquals(EditBackgroundChoice.CANVAS, choice.nextInQuickCycle())
        }
    }
}
