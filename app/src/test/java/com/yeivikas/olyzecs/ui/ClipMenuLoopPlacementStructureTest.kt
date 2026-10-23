package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda que "Loop" viva en el MENÚ DEL CLIP (mantener presionado un clip de audio) y no en el cajón "Módulos".
 *
 * Loop es un interruptor de dos estados POR CLIP (`AudioClip.loop`), así que su sitio natural es el menú del clip,
 * junto a las demás acciones del clip (Invertir, Silenciar, Normalizar). Regresiones cubiertas:
 *  - que el módulo vuelva al cajón (`AudioModuleId.LOOP` / fila "Loop" de [ModulesDrawerPanel]);
 *  - que el menú del clip pierda el ítem o deje de estar cableado hasta el ViewModel.
 *
 * Test ESTRUCTURAL sobre los archivos reales (JVM puro, sin Robolectric), como [StereoEarIconsStructureTest].
 */
class ClipMenuLoopPlacementStructureTest {

    @Test
    fun `el cajon de modulos ya no ofrece Loop`() {
        val bar = ProjectFiles.main("ui/EditorBottomBar.kt")
        assertFalse("AudioModuleId no debe tener LOOP", Regex("""enum class AudioModuleId\s*\{[^}]*\bLOOP\b""").containsMatchIn(bar))
        assertFalse("No debe quedar la fila \"Loop\" en el cajón", bar.contains("label = \"Loop\""))
        assertFalse("No debe quedar el despacho de AudioModuleId.LOOP", bar.contains("AudioModuleId.LOOP"))
    }

    @Test
    fun `el editor ya no tiene la ventana flotante de Loop`() {
        val editor = ProjectFiles.main("ui/EditorScreen.kt")
        assertFalse(editor.contains("audioLoopWindowOpen"))
        assertFalse(editor.contains("AudioModuleId.LOOP"))
        assertFalse(
            "AudioToggleFloatingWindow quedó sin consumidores y se eliminó",
            ProjectFiles.mainFile("ui/AudioToggleFloatingWindow.kt").exists()
        )
    }

    @Test
    fun `el menu de primer nivel del clip ofrece Loop y esta cableado hasta el ViewModel`() {
        val row = ProjectFiles.main("ui/AudioTrackRow.kt")
        val menuStart = row.indexOf("items = if (!menuMore) listOf(")
        val moreStart = row.indexOf("ClipMenuItem(\"Seleccionar clip\")")
        assertTrue("No se encontró el menú del clip", menuStart in 0 until moreStart)
        val firstLevel = row.substring(menuStart, moreStart)
        assertTrue("Falta el ítem de Loop en el primer nivel", firstLevel.contains("\"Quitar loop\"") && firstLevel.contains("\"Activar loop\""))
        assertTrue(firstLevel.contains("currentActions.onToggleLoop(clip.id)"))

        assertTrue(
            "EditorScreen debe cablear onToggleLoop al ViewModel",
            ProjectFiles.main("ui/EditorScreen.kt").contains("onToggleLoop = { clipId -> viewModel.toggleAudioLoop(clipId) }")
        )
        val vm = ProjectFiles.main("viewmodel/EditorViewModel.kt")
        assertTrue("toggleAudioLoop debe delegar en setAudioLoop (único punto de escritura)", Regex("""fun toggleAudioLoop\([^)]*\)\s*\{[^}]*setAudioLoop\(clipId, !clip\.loop\)""").containsMatchIn(vm))
    }
}
