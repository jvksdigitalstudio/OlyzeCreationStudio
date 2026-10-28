package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El aviso de "este clip aún no suena" (preparando / reintentando / falló): el cableado cajón → pantalla → fila → clip y sus textos.
 * No se puede ejercitar sin un dispositivo, así que se verifica sobre el código fuente (igual que [ReverbModuleStructureTest]). La lógica
 * de estados que hay detrás sí se prueba con ejecución real en `SourcePrepQueueTest`, `SourcePrepSchedulerTest` y
 * `AudioSourceStateMappingTest`.
 */
class AudioSourceStatusWiringTest {

    @Test
    fun `la pantalla colecta el estado de las fuentes y lo entrega a las acciones del clip`() {
        val screen = ProjectFiles.main("ui/EditorScreen.kt")
        assertTrue(screen.contains("viewModel.audioSourceStates.collectAsState()"))
        assertTrue(screen.contains("sourceStates = audioSourceStates"))
        assertTrue(screen.contains("onRetrySource = { clipId -> viewModel.retryAudioSource(clipId) }"))
    }

    @Test
    fun `el clip dibuja la insignia con el estado de SU fuente y reintenta con su id`() {
        val row = ProjectFiles.main("ui/AudioTrackRow.kt")
        assertTrue(row.contains("AudioSourceStatusBadge("))
        assertTrue(row.contains("actions.sourceStates[AudioSourceId.of(clip)]"))
        assertTrue(row.contains("currentActions.onRetrySource(currentClip.id)"))
    }

    @Test
    fun `reintentar no es una edicion y no toca el proyecto ni el historial de deshacer`() {
        val vm = ProjectFiles.main("viewmodel/EditorViewModel.kt")
        val body = vm.substringAfter("fun retryAudioSource(clipId: String) {").substringBefore("\n    }\n")
        assertTrue(body.contains("EngineSourceCache.retry"))
        assertFalse(body.contains("pushUndoCheckpoint"))
        assertFalse(body.contains("replaceAudioClip"))
    }

    @Test
    fun `el estado de las fuentes no vive en el estado guardable del editor`() {
        // Cambia por trabajo de fondo, no por ediciones: meterlo en el estado de la UI lo mezclaría con deshacer y guardado.
        val vm = ProjectFiles.main("viewmodel/EditorViewModel.kt")
        assertTrue(vm.contains("val audioSourceStates: StateFlow<Map<AudioSourceId, AudioSourceState>> get() = EngineSourceCache.states"))
    }

    @Test
    fun `los textos de la insignia estan en espanol e ingles con los mismos marcadores de formato`() {
        val es = sourceStrings(ProjectFiles.res("values/strings.xml"))
        val en = sourceStrings(ProjectFiles.res("values-en/strings.xml"))
        assertTrue("faltan textos de la insignia", es.size >= 7)
        assertEquals("mismas claves en ambos idiomas", es.keys, en.keys)
        for ((key, spanish) in es) {
            assertEquals("marcadores de formato de $key", placeholders(spanish), placeholders(en.getValue(key)))
        }
    }

    @Test
    fun `todo texto que usa la insignia existe como recurso`() {
        val badge = ProjectFiles.main("ui/AudioSourceStatusBadge.kt")
        val used = Regex("""R\.string\.(audio_source_[a-z_]+)""").findAll(badge).map { it.groupValues[1] }.toSet()
        val defined = sourceStrings(ProjectFiles.res("values/strings.xml")).keys
        assertTrue("la insignia no usa ningún texto de recurso", used.isNotEmpty())
        assertTrue("textos usados pero no definidos: ${used - defined}", defined.containsAll(used))
    }

    private fun sourceStrings(xml: String): Map<String, String> =
        Regex("""<string name="(audio_source_[a-z_]+)">(.*?)</string>""").findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }

    private fun placeholders(text: String): List<String> = Regex("""%\d+\$[a-z]""").findAll(text).map { it.value }.sorted().toList()
}
