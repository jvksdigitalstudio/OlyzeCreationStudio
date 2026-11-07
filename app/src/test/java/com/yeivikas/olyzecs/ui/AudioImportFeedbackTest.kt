package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Importar un audio que no se puede leer NO puede fallar en silencio: antes se escribía una línea en el registro y no pasaba nada en
 * pantalla, y el usuario no podía distinguir "no hizo nada" de "falló". No se puede ejercitar sin un dispositivo, así que se verifica
 * sobre el código fuente (igual que [AudioSourceStatusWiringTest]).
 */
class AudioImportFeedbackTest {

    private val viewModel = ProjectFiles.main("viewmodel/EditorViewModel.kt")

    @Test
    fun `importar un audio ilegible avisa al usuario`() {
        val branch = viewModel.substringAfter("No se pudo importar el audio '\$displayName'").take(700)
        assertTrue(branch.contains("_messages.tryEmit(EditorMessage.AudioImportFailed(displayName))"))
    }

    @Test
    fun `reemplazar el audio de un clip por un archivo ilegible tambien avisa`() {
        val branch = viewModel.substringAfter("No se pudo reemplazar el audio por '\$displayName'").take(700)
        assertTrue(branch.contains("_messages.tryEmit(EditorMessage.AudioImportFailed(displayName))"))
    }

    @Test
    fun `ambas rutas siguen sin crear ni tocar clips al fallar`() {
        // El aviso se añadió ANTES del return: el fallo sigue sin dejar un clip a medias ni un punto de deshacer.
        for (marker in listOf("No se pudo importar el audio '\$displayName'", "No se pudo reemplazar el audio por '\$displayName'")) {
            val branch = viewModel.substringAfter(marker).substringBefore("return@launch")
            assertTrue("el aviso debe ir antes de salir", branch.contains("AudioImportFailed"))
            assertTrue("fallar no debe crear punto de deshacer", !branch.contains("pushUndoCheckpoint"))
        }
    }

    @Test
    fun `la pantalla muestra el aviso con un texto de recurso`() {
        val screen = ProjectFiles.main("ui/EditorScreen.kt")
        assertTrue(screen.contains("viewModel.messages.collect"))
        assertTrue(screen.contains("is EditorMessage.AudioImportFailed"))
        assertTrue(screen.contains("context.getString(R.string.audio_import_failed, message.displayName)"))
    }

    @Test
    fun `el texto del aviso existe en espanol e ingles con los mismos marcadores`() {
        val pattern = Regex("""<string name="audio_import_failed">(.*?)</string>""")
        val es = pattern.find(ProjectFiles.res("values/strings.xml"))?.groupValues?.get(1)
        val en = pattern.find(ProjectFiles.res("values-en/strings.xml"))?.groupValues?.get(1)
        assertTrue("falta en español", es != null)
        assertTrue("falta en inglés", en != null)
        fun placeholders(text: String) = Regex("""%\d+\$[a-z]""").findAll(text).map { it.value }.toList()
        assertEquals(listOf("%1\$s"), placeholders(es.orEmpty()))
        assertEquals(placeholders(es.orEmpty()), placeholders(en.orEmpty()))
    }
}
