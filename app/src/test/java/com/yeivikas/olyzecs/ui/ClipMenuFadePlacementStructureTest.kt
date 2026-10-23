package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda que los fundidos (fade-in / fade-out) vivan en el MENÚ DEL CLIP ("Fundidos") y no en el cajón "Módulos".
 * Misma idea que [ClipMenuLoopPlacementStructureTest]. Test ESTRUCTURAL sobre los archivos reales (JVM puro).
 */
class ClipMenuFadePlacementStructureTest {

    @Test
    fun `el cajon de modulos ya no ofrece Fade`() {
        val bar = ProjectFiles.main("ui/EditorBottomBar.kt")
        assertFalse(Regex("""enum class AudioModuleId\s*\{[^}]*\bFADE\b""").containsMatchIn(bar))
        assertFalse(bar.contains("label = \"Fade\""))
        assertFalse(bar.contains("AudioModuleId.FADE"))
    }

    @Test
    fun `no quedan la ventana flotante ni su estado`() {
        val editor = ProjectFiles.main("ui/EditorScreen.kt")
        assertFalse(editor.contains("audioFadeWindowOpen"))
        assertFalse(editor.contains("audioFadeWindowZIndex"))
        assertFalse(editor.contains("AudioFadeFloatingWindow"))
        assertFalse(ProjectFiles.mainFile("ui/AudioFadeFloatingWindow.kt").exists())
    }

    @Test
    fun `el menu del clip ofrece Fundidos y llega hasta el ViewModel`() {
        val row = ProjectFiles.main("ui/AudioTrackRow.kt")
        val menuStart = row.indexOf("items = if (!menuMore) listOf(")
        val moreStart = row.indexOf("ClipMenuItem(\"Seleccionar clip\")")
        assertTrue(menuStart in 0 until moreStart)
        assertTrue("Falta \"Fundidos\" en el primer nivel", row.substring(menuStart, moreStart).contains("ClipMenuItem(\"Fundidos\")"))
        assertTrue("Falta abrir AudioClipFadeDialog", row.contains("AudioClipFadeDialog("))
        assertTrue(
            ProjectFiles.main("ui/EditorScreen.kt")
                .contains("onFadeChange = { clipId, fadeInMs, fadeOutMs -> viewModel.setAudioFade(clipId, fadeInMs, fadeOutMs) }")
        )
    }

    @Test
    fun `Fundidos aparece una sola vez en el menu del clip`() {
        val row = ProjectFiles.main("ui/AudioTrackRow.kt")
        assertTrue(Regex("""ClipMenuItem\("Fundidos"\)""").findAll(row).count() == 1)
        assertTrue(Regex("""ClipMenuItem\(\s*if \(clip\.loop\)""").findAll(row).count() == 1)
    }
}
