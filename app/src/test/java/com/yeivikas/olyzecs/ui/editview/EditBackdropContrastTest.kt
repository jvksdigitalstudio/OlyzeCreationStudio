package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.engine.render.CANVAS_CHROMA_KEY_ARGB
import com.yeivikas.olyzecs.engine.render.PreviewBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditBackdropContrastTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun solid(argb: Int) = PreviewBackdrop.Solid(argb)

    @Test
    fun `negro sobre blanco es el contraste maximo de 21 a 1`() {
        assertEquals(21.0, EditBackdropContrast.contrastRatio(black, white), 0.01)
        assertEquals(1.0, EditBackdropContrast.contrastRatio(white, white), 1e-9)
    }

    @Test
    fun `la luminancia de negro y blanco son los extremos`() {
        assertEquals(0.0, EditBackdropContrast.relativeLuminance(black), 1e-9)
        assertEquals(1.0, EditBackdropContrast.relativeLuminance(white), 1e-9)
    }

    @Test
    fun `sobre un fondo oscuro el icono es blanco`() {
        assertEquals(white, EditBackdropContrast.iconArgbOver(solid(EditBackdropPalette.BLACK)))
        assertEquals(white, EditBackdropContrast.iconArgbOver(solid(0xFF1E1E1E.toInt())))
    }

    @Test
    fun `sobre un fondo claro el icono es negro`() {
        assertEquals(black, EditBackdropContrast.iconArgbOver(solid(EditBackdropPalette.WHITE)))
        assertEquals(black, EditBackdropContrast.iconArgbOver(solid(EditBackdropPalette.GRAY)))
    }

    @Test
    fun `sobre el verde croma del lienzo el icono es negro`() {
        assertEquals(black, EditBackdropContrast.iconArgbOver(PreviewBackdrop.CanvasColor))
        assertEquals(CANVAS_CHROMA_KEY_ARGB, EditBackdropContrast.representativeArgb(PreviewBackdrop.CanvasColor))
    }

    @Test
    fun `un tablero se juzga por el promedio de sus casillas`() {
        val light = PreviewBackdrop.Checkerboard(EditBackdropPalette.CHECKER_LIGHT_A, EditBackdropPalette.CHECKER_LIGHT_B, 12)
        val dark = PreviewBackdrop.Checkerboard(EditBackdropPalette.CHECKER_DARK_A, EditBackdropPalette.CHECKER_DARK_B, 12)
        assertEquals(0xFFE5E5E5.toInt(), EditBackdropContrast.representativeArgb(light))
        assertEquals(0xFF404040.toInt(), EditBackdropContrast.representativeArgb(dark))
        assertEquals(black, EditBackdropContrast.iconArgbOver(light))
        assertEquals(white, EditBackdropContrast.iconArgbOver(dark))
    }

    @Test
    fun `el icono elegido siempre contrasta al menos 4,5 a 1 con cualquier fondo`() {
        // Barrido de la cuña de color: el peor caso de "negro o blanco" ronda 4,58 (en el punto de cruce).
        val steps = (0..255 step 15)
        for (r in steps) for (g in steps) for (b in steps) {
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val icon = EditBackdropContrast.iconArgbOver(solid(argb))
            val ratio = EditBackdropContrast.contrastRatio(icon, argb)
            assertTrue("poco contraste ($ratio) sobre #%06X".format(argb and 0xFFFFFF), ratio >= 4.5)
        }
    }

    @Test
    fun `el alfa del color no altera la decision`() {
        assertEquals(
            EditBackdropContrast.iconArgbOver(solid(0xFF336699.toInt())),
            EditBackdropContrast.iconArgbOver(solid(0x80336699.toInt()))
        )
    }
}
