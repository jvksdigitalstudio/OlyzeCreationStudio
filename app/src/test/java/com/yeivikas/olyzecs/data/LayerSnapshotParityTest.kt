package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los retratos de una capa deben cubrir TODOS sus campos persistentes.
 *
 * `Layer` tiene dos retratos inmutables casi idénticos: `LayerSaveSnapshot` (lo que se guarda en disco) y `LayerContentState` (lo que
 * restaura deshacer/rehacer). Añadir un campo a `Layer` y olvidarlo en uno de ellos NO da error de compilación (los `copy(...)` toman el
 * valor por defecto) y se nota tarde y de lejos: "no se guarda" o "deshacer no lo restaura". Esta prueba lo convierte en un fallo
 * inmediato con el nombre del campo olvidado.
 *
 * Fuera de alcance a propósito: `api.LayerSnapshot`, que es una vista pública deliberadamente distinta de `Layer` (ver su KDoc).
 * Se verifica sobre el código fuente porque `EditorViewModel` (donde vive `LayerContentState`, privado) no se puede construir en JVM.
 */
class LayerSnapshotParityTest {

    /** Existen en `Layer` solo en ejecución (recursos de GPU y bitmap transitorio): no se guardan ni se restauran. */
    private val runtimeOnly = setOf("pendingBitmap", "textureInvalidationRequest", "contentIdentity")

    private fun withoutComments(text: String): String =
        text.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\n]*"""), "")

    private fun source(path: String): String = withoutComments(ProjectFiles.main(path))

    /** El contenido entre los paréntesis que abre [header] (que debe terminar en `(`). */
    private fun parenBlock(text: String, header: String): String {
        val at = text.indexOf(header)
        assertTrue("No se encontró `$header`: si se renombró, actualiza esta prueba.", at >= 0)
        val open = at + header.length - 1
        var depth = 0
        for (i in open until text.length) {
            if (text[i] == '(') depth++
            if (text[i] == ')') {
                depth--
                if (depth == 0) return text.substring(open + 1, i)
            }
        }
        error("Paréntesis sin cerrar en `$header`")
    }

    private fun constructorFields(block: String): Set<String> =
        Regex("""\bva[lr]\s+(\w+)\s*:""").findAll(block).map { it.groupValues[1] }.toSet()

    private fun layerFields(): Set<String> {
        val layer = source("engine/scene/Layer.kt")
        val fromConstructor = constructorFields(parenBlock(layer, "class Layer("))
        val fromBody = Regex("""(?m)^ {4}(?:@Volatile\s+)?(?:(?:internal|private|override)\s+)?va[lr]\s+(\w+)\s*[:=]""")
            .findAll(layer.substring(layer.indexOf("class Layer(")))
            .map { it.groupValues[1] }
            .toSet()
        return fromConstructor + fromBody
    }

    /**
     * Campos persistentes de `Layer` tal como los exponen sus retratos: la pista de cámara se aplana en `baseFrame` y `keyframes`, y los
     * recursos de ejecución no cuentan.
     */
    private fun expectedPersistentFields(): Set<String> =
        (layerFields() - runtimeOnly - "cameraTrack") + setOf("baseFrame", "keyframes")

    private fun assertCovers(snapshot: String, actual: Set<String>, expected: Set<String>) {
        val missing = expected - actual
        val extra = actual - expected
        assertTrue(
            "$snapshot no coincide con Layer. Le faltan: $missing. Le sobran (no existen en Layer): $extra. " +
                "Añade el campo al retrato, o márcalo como solo de ejecución en esta prueba.",
            missing.isEmpty() && extra.isEmpty()
        )
    }

    @Test
    fun `el retrato de guardado cubre todos los campos persistentes de Layer`() {
        val actual = constructorFields(parenBlock(source("data/ProjectStorage.kt"), "data class LayerSaveSnapshot("))
        // contentRevision es el contador interno de versión del contenido: no se guarda (se reinicia al cargar).
        assertCovers("LayerSaveSnapshot", actual, expectedPersistentFields() - "contentRevision")
    }

    @Test
    fun `el retrato de deshacer cubre todos los campos persistentes de Layer`() {
        val actual = constructorFields(parenBlock(source("viewmodel/EditorViewModel.kt"), "private data class LayerContentState("))
        assertCovers("LayerContentState", actual, expectedPersistentFields())
    }

    @Test
    fun `la lista de campos solo de ejecucion no guarda nombres que ya no existen`() {
        val stale = runtimeOnly - layerFields()
        assertTrue("Campos marcados como solo de ejecución que ya no existen en Layer: $stale", stale.isEmpty())
    }
}
