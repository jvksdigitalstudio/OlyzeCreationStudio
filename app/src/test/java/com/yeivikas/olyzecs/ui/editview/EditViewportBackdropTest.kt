package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.engine.render.CANVAS_CHROMA_KEY_ARGB
import com.yeivikas.olyzecs.engine.render.PreviewBackdrop
import com.yeivikas.olyzecs.engine.render.clearColorArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EditViewportBackdropTest {

    private val workBackdrops: List<PreviewBackdrop> = listOf(
        PreviewBackdrop.CanvasColor,
        PreviewBackdrop.Solid(EditBackdropPalette.BLACK),
        PreviewBackdrop.Solid(EditBackdropPalette.WHITE),
        PreviewBackdrop.Solid(0xFF1E1E1E.toInt()),
        PreviewBackdrop.Checkerboard(EditBackdropPalette.CHECKER_LIGHT_A, EditBackdropPalette.CHECKER_LIGHT_B, 12),
        PreviewBackdrop.Checkerboard(EditBackdropPalette.CHECKER_DARK_A, EditBackdropPalette.CHECKER_DARK_B, 12)
    )

    @Test
    fun `fuera del modo edicion siempre se ve el color del lienzo sea cual sea la preferencia`() {
        for (work in workBackdrops) {
            assertSame(PreviewBackdrop.CanvasColor, EditViewportBackdrop.effective(isEditing = false, editBackdrop = work))
            assertEquals(CANVAS_CHROMA_KEY_ARGB, EditViewportBackdrop.behindSurfaceArgb(isEditing = false, editBackdrop = work))
        }
    }

    @Test
    fun `en modo edicion se ve el fondo de trabajo elegido`() {
        for (work in workBackdrops) {
            assertSame(work, EditViewportBackdrop.effective(isEditing = true, editBackdrop = work))
        }
    }

    @Test
    fun `lo que hay detras de la superficie coincide con el color con que el renderer limpia el frame`() {
        // Es el contrato que evita el destello: contenedor y primer frame GL tienen exactamente el mismo color.
        for (editing in listOf(false, true)) for (work in workBackdrops) {
            assertEquals(
                "editing=$editing, fondo=$work",
                EditViewportBackdrop.effective(editing, work).clearColorArgb(),
                EditViewportBackdrop.behindSurfaceArgb(editing, work)
            )
        }
    }

    @Test
    fun `con fondo negro o blanco al entrar al modo edicion no asoma el verde del lienzo`() {
        assertEquals(EditBackdropPalette.BLACK, EditViewportBackdrop.behindSurfaceArgb(true, PreviewBackdrop.Solid(EditBackdropPalette.BLACK)))
        assertEquals(EditBackdropPalette.WHITE, EditViewportBackdrop.behindSurfaceArgb(true, PreviewBackdrop.Solid(EditBackdropPalette.WHITE)))
    }

    @Test
    fun `un tablero deja detras su casilla clara igual que el dibujante de fondo`() {
        val checker = PreviewBackdrop.Checkerboard(EditBackdropPalette.CHECKER_DARK_A, EditBackdropPalette.CHECKER_DARK_B, 12)
        assertEquals(checker.lightArgb, EditViewportBackdrop.behindSurfaceArgb(true, checker))
    }
}
