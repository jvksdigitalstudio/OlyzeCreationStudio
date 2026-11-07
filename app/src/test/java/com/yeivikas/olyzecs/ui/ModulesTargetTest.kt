package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cajón de Módulos muestra la lista del tipo de capa seleccionado: audio → lista de audio (sin módulos de
 * imagen/cámara); imagen o nada seleccionado → la lista de capas de siempre, que NO se elimina.
 */
class ModulesTargetTest {

    private val tracks = listOf("t1", "t2")

    @Test
    fun `con un carril de audio existente y ninguna capa seleccionada el destino es audio`() {
        assertEquals(ModulesTarget.AUDIO, modulesTargetFor("t1", selectedLayerId = null, existingAudioTrackIds = tracks))
    }

    @Test
    fun `sin carril de audio seleccionado el destino es el de capas`() {
        assertEquals(ModulesTarget.LAYER, modulesTargetFor(null, selectedLayerId = null, existingAudioTrackIds = tracks))
    }

    @Test
    fun `con una capa seleccionada el destino es el de capas aunque quede un carril de audio elegido`() {
        // Caso real: deshacer/rehacer restaura selectedLayerId sin tocar el estado de UI del carril.
        assertEquals(ModulesTarget.LAYER, modulesTargetFor("t1", selectedLayerId = "layer-1", existingAudioTrackIds = tracks))
    }

    @Test
    fun `un carril de audio que ya no existe no cuenta como seleccion`() {
        assertEquals(ModulesTarget.LAYER, modulesTargetFor("borrado", selectedLayerId = null, existingAudioTrackIds = tracks))
    }

    @Test
    fun `el cajon elige la lista por destino y los modulos de capa siguen existiendo`() {
        val bar = ProjectFiles.main("ui/EditorBottomBar.kt")
        assertTrue(bar.contains("ModulesTarget.LAYER -> LayerModulesList(onZoomXClick = onZoomXClick)"))
        assertTrue(bar.contains("ModulesTarget.AUDIO -> AudioModulesList(onReverbClick = onReverbClick)"))
        // Nada de capas se eliminó: siguen Sombra, Reflejo, Animación, Cámara y Zoom X.
        for (label in listOf("\"Sombra\"", "\"Reflejo\"", "\"Animación\"", "\"Cámara\"", "\"Zoom X\"")) {
            assertTrue("Falta $label en la lista de capas", bar.contains(label))
        }
    }

    @Test
    fun `la lista de audio no contiene modulos de imagen ni de camara`() {
        val audio = ProjectFiles.main("ui/AudioModulesList.kt")
        for (forbidden in listOf("Sombra", "Reflejo", "Animación", "Cámara", "Zoom X")) {
            assertFalse("La lista de audio no debe incluir $forbidden", audio.contains("\"$forbidden\""))
        }
        assertFalse("La lista de audio no debe recibir el callback de Zoom X", audio.contains("onZoomXClick"))
    }

    @Test
    fun `la lista de audio es solo el punto de registro de los modulos reales, sin texto de relleno`() {
        val audio = ProjectFiles.main("ui/AudioModulesList.kt")
        assertFalse("No debe haber texto de relleno", audio.contains("Text("))
        assertFalse("No debe haber texto de relleno", audio.contains("Sin módulos"))
        assertTrue("Debe seguir siendo el punto de registro", audio.contains("fun ColumnScope.AudioModulesList(onReverbClick: () -> Unit)"))
    }

    @Test
    fun `el editor deriva el destino de la seleccion real de carril, capa y carriles existentes`() {
        val editor = ProjectFiles.main("ui/EditorScreen.kt")
        assertTrue(editor.contains("selectedAudioTrackId = selectedAudioTrackId,"))
        assertTrue(editor.contains("selectedLayerId = state.selectedLayerId,"))
        assertTrue(editor.contains("existingAudioTrackIds = state.audioTracks.map { it.id }"))
    }
}
