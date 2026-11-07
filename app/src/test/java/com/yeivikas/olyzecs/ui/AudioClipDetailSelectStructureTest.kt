package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda el cableado de "Seleccionar" (cantidad de rangos x1 … x7) en la ventana ampliada del audio, de la cabecera al
 * reloj de reproducción, y que cada responsabilidad siga en su archivo.
 *
 * Test ESTRUCTURAL sobre los archivos reales (JVM puro), como [ClipTrimPlacementStructureTest].
 */
class AudioClipDetailSelectStructureTest {

    @Test
    fun `Seleccionar esta en la cabecera a la izquierda de Recorte`() {
        val header = ProjectFiles.main("ui/AudioClipDetailHeader.kt")
        val select = header.indexOf("DetailSelectButton(count = selectCount")
        val trim = header.indexOf("DetailTrimButton(enabled = canTrim")
        assertTrue("Falta el botón Seleccionar en la cabecera", select in 0 until trim)
    }

    @Test
    fun `el boton Seleccionar comparte el marco y no lo copia`() {
        val header = ProjectFiles.main("ui/AudioClipDetailHeader.kt")
        val select = ProjectFiles.main("ui/AudioClipDetailSelectButton.kt")
        assertTrue(select.contains("DetailTileFrame("))
        assertFalse("El marco vive en AudioClipDetailTile.kt", header.contains("fun DetailActionTile("))
        assertFalse("El marco vive en AudioClipDetailTile.kt", select.contains("RoundedCornerShape(10.dp)"))
    }

    @Test
    fun `la ventana conecta la cantidad con el estado y avisa al editor`() {
        val panel = ProjectFiles.main("ui/AudioClipDetailPanel.kt")
        assertTrue(panel.contains("selectCount = selection.maxCount"))
        assertTrue(panel.contains("selection.changeMaxCount(count)"))
        assertTrue(panel.contains("commitSelection()"))
    }

    @Test
    fun `el reloj de reproduccion usa el plan de tramos del editor`() {
        val viewModel = ProjectFiles.main("viewmodel/EditorViewModel.kt")
        assertTrue(viewModel.contains("fun setAudioClipPreviewSelections("))
        assertTrue(viewModel.contains("resolvedIn(state.audioTracks)?.plan"))
        assertFalse("Quedó la API de un solo rango", viewModel.contains("setAudioClipPreviewSelection("))
    }

    @Test
    fun `el estado no dibuja y la capa no guarda estado de seleccion`() {
        val state = ProjectFiles.main("ui/AudioClipDetailSelectionState.kt")
        val layer = ProjectFiles.main("ui/AudioClipDetailSelectionLayer.kt")
        assertFalse("El estado no debe dibujar", state.contains("Canvas("))
        assertFalse("La capa solo dibuja", layer.contains("mutableStateOf"))
    }
}
