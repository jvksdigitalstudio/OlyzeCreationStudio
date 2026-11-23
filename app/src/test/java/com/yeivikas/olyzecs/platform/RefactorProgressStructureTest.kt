package com.yeivikas.olyzecs.platform

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Presupuesto de la partición de `EditorScreen.kt` (Fase 3, ver docs/adr/ADR-022-particion-de-editorscreen.md).
 *
 * El objetivo es que el archivo SOLO pueda bajar: cada corte rebaja [EDITOR_SCREEN_MAX_LINES] en el mismo cambio, y mientras tanto
 * nadie puede volver a engordarlo sin que esta prueba lo avise. Es una métrica de estructura, no de comportamiento.
 */
class RefactorProgressStructureTest {

    @Test
    fun `EditorScreen no supera su presupuesto de lineas`() {
        val lines = ProjectFiles.main("ui/EditorScreen.kt").lines().size
        assertTrue(
            "EditorScreen.kt tiene $lines líneas (presupuesto $EDITOR_SCREEN_MAX_LINES). Mueve código a ui/editor/ o, si el corte " +
                "rebaja el archivo, baja el presupuesto en esta prueba.",
            lines <= EDITOR_SCREEN_MAX_LINES
        )
    }

    @Test
    fun `los archivos de ui editor mantienen el paquete ui`() {
        // La carpeta es solo organización: cambiar el paquete renombraría símbolos y obligaría a tocar imports en todo el proyecto.
        val files = ProjectFiles.editorFiles()
        assertTrue("ui/editor/ debería contener los cortes ya hechos", files.isNotEmpty())
        for ((name, src) in files) {
            assertEquals("$name debe seguir en el paquete ui", "package com.yeivikas.olyzecs.ui", src.lineSequence().first { it.startsWith("package ") })
        }
    }

    @Test
    fun `ninguna declaracion de nivel superior esta repetida entre EditorScreen y ui editor`() {
        // El compilador también lo rechazaría; esta prueba lo dice antes y con un mensaje que apunta al corte.
        val declaration = Regex("""(?m)^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public)\s+)?(?:(?:inline|data|sealed|enum|const|suspend)\s+)*(?:fun|class|object|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)""")
        val seen = HashMap<String, String>()
        val sources = mapOf("EditorScreen.kt" to ProjectFiles.main("ui/EditorScreen.kt")) + ProjectFiles.editorFiles()
        for ((file, src) in sources) {
            for (m in declaration.findAll(src)) {
                val name = m.groupValues[1]
                val previous = seen.put(name, file)
                assertTrue("'$name' está declarado en $previous y en $file", previous == null || previous == file)
            }
        }
    }

    private companion object {
        /** Corte 3: 18 160 → 15 684 → 14 714 → 11 392 líneas. Margen de ~18 para no romperse por un comentario; bajar en cada corte. */
        const val EDITOR_SCREEN_MAX_LINES = 11_410
    }
}
