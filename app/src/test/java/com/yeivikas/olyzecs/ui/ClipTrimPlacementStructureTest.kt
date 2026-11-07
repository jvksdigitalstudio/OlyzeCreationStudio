package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda que "Recorte" viva en la CABECERA de la ventana ampliada del audio (junto a Reversa) y no en el cajón "Módulos".
 *
 * Regresiones cubiertas:
 *  - que la categoría "Audio" / la fila "Recorte" / su ventana flotante vuelvan al cajón (sería un duplicado);
 *  - que el botón de la cabecera pierda su cableado hasta el ViewModel;
 *  - que el ícono premium multicolor se dibuje con tinte (lo aplanaría a un solo color).
 *
 * Test ESTRUCTURAL sobre los archivos reales (JVM puro), como [ClipMenuLoopPlacementStructureTest].
 */
class ClipTrimPlacementStructureTest {

    @Test
    fun `el cajon de modulos ya no ofrece Recorte`() {
        val bar = ProjectFiles.main("ui/EditorBottomBar.kt")
        assertFalse(bar.contains("label = \"Recorte\""))
        assertFalse(bar.contains("AudioModuleId"))
        assertFalse(bar.contains("onAudioModuleClick"))
        assertFalse(bar.contains("hasAudioClip"))
    }

    @Test
    fun `el editor ya no tiene la ventana flotante de Recorte`() {
        val editor = ProjectFiles.main("ui/EditorScreen.kt")
        assertFalse(editor.contains("audioTrimWindowOpen"))
        assertFalse(editor.contains("audioTrimWindowZIndex"))
        assertFalse(editor.contains("onOpenAudioModule"))
        assertFalse(
            "AudioSliderFloatingWindow quedó sin consumidores y se eliminó",
            ProjectFiles.mainFile("ui/AudioSliderFloatingWindow.kt").exists()
        )
        assertFalse(
            "El ícono viejo de Recorte quedó sin consumidores y se eliminó",
            ProjectFiles.resFile("drawable/ic_trim.xml").exists()
        )
    }

    @Test
    fun `la cabecera de la ventana del audio tiene Recorte antes de Reversa y sin tinte`() {
        val header = ProjectFiles.main("ui/AudioClipDetailHeader.kt")
        val trim = header.indexOf("DetailTrimButton(enabled = canTrim")
        val reverse = header.indexOf("DetailReverseButton(checked = reversed")
        assertTrue("Falta el botón Recorte en la cabecera", trim in 0 until reverse)
        assertTrue(header.contains("R.drawable.ic_trim_premium"))
        assertTrue("El ícono multicolor no debe llevar tinte", header.contains("iconTint = null"))
    }

    @Test
    fun `Recorte esta cableado desde la ventana hasta el ViewModel`() {
        assertTrue(ProjectFiles.main("ui/AudioClipDetailPanel.kt").contains("actions.onTrimToSelections(clip.id,"))
        assertTrue("El botón debe respetar el largo mínimo del clip", ProjectFiles.main("ui/AudioClipDetailPanel.kt").contains("MIN_AUDIO_CLIP_LENGTH_MS"))
        assertTrue(
            ProjectFiles.main("ui/EditorScreen.kt")
                .contains("onTrimToSelections = { clipId, selections -> viewModel.trimAudioClipToSelections(clipId, selections) }")
        )
        assertTrue(ProjectFiles.main("viewmodel/EditorViewModel.kt").contains("fun trimAudioClipToSelections("))
    }

    @Test
    fun `Recorte no exige un unico rango - se habilita con todos los que alcanzan el minimo`() {
        val panel = ProjectFiles.main("ui/AudioClipDetailPanel.kt")
        assertTrue(panel.contains("detailTrimSelections(selection.ranges"))
        assertFalse("Quedó la regla de un solo rango", panel.contains("detailTrimSelection("))
        assertFalse("Quedó la API de un solo rango", ProjectFiles.main("viewmodel/EditorViewModel.kt").contains("fun trimAudioClipToSelection("))
    }
}
