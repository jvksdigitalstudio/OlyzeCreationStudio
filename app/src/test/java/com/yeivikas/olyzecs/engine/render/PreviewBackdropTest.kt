package com.yeivikas.olyzecs.engine.render

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewBackdropTest {

    @Test
    fun `cada fondo declara con que color se limpia el frame`() {
        assertEquals(CANVAS_CHROMA_KEY_ARGB, PreviewBackdrop.CanvasColor.clearColorArgb())
        assertEquals(0xFF123456.toInt(), PreviewBackdrop.Solid(0xFF123456.toInt()).clearColorArgb())
        // Un tablero se limpia con su tono claro: si su shader no estuviera disponible, queda un fondo liso razonable.
        assertEquals(0xFFEEEEEE.toInt(), PreviewBackdrop.Checkerboard(0xFFEEEEEE.toInt(), 0xFF111111.toInt(), 12).clearColorArgb())
    }

    @Test
    fun `un tablero no admite casillas ilegibles`() {
        assertThrows(IllegalArgumentException::class.java) { PreviewBackdrop.Checkerboard(0, 0, PreviewBackdrop.MIN_CELL_PX - 1) }
        PreviewBackdrop.Checkerboard(0, 0, PreviewBackdrop.MIN_CELL_PX) // el mínimo exacto sí vale
    }

    @Test
    fun `el verde croma coincide con el que usan la exportacion y las miniaturas`() {
        // LayerDrawer.ensureInitialized fija (0, 177, 64): si uno cambia y el otro no, la vista previa y el video
        // exportado dejarían de coincidir.
        val r = (CANVAS_CHROMA_KEY_ARGB shr 16) and 0xFF
        val g = (CANVAS_CHROMA_KEY_ARGB shr 8) and 0xFF
        val b = CANVAS_CHROMA_KEY_ARGB and 0xFF
        assertEquals(0, r)
        assertEquals(177, g)
        assertEquals(64, b)
        val drawer = ProjectFiles.main("engine/render/LayerDrawer.kt")
        assertTrue(
            "LayerDrawer ya no limpia con (0, 177, 64): sincronizar CANVAS_CHROMA_KEY_ARGB",
            Regex("""glClearColor\(\s*0f,\s*177f\s*/\s*255f,\s*64f\s*/\s*255f,\s*1f\s*\)""").containsMatchIn(drawer)
        )
    }
}
