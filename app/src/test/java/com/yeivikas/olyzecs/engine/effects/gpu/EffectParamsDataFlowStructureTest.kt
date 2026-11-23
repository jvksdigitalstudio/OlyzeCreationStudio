package com.yeivikas.olyzecs.engine.effects.gpu

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda, sobre los archivos reales, que `effectParams` viaja por TODO el recorrido de `lookSettings`: guardado, carga,
 * deshacer/rehacer, snapshot de render y API. Si alguien añade un camino y olvida el campo, estas pruebas fallan.
 */
class EffectParamsDataFlowStructureTest {

    private fun code(relative: String): String =
        ProjectFiles.main(relative)
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""(?m)//.*$"""), "")

    private fun count(source: String, regex: String) = Regex(regex).findAll(source).count()

    @Test
    fun `cada sitio donde viaja lookSettings viaja tambien effectParams`() {
        // Pares (archivo, patrón de lookSettings, patrón de effectParams): mismas apariciones en cada uno.
        val sites = listOf(
            Triple("engine/scene/Layer.kt", """var lookSettings\b""", """var effectParams\b"""),
            Triple("data/ProjectModels.kt", """val lookSettings\b""", """val effectParams\b"""),
            Triple("data/ProjectStorage.kt", """\blookSettings = """, """\beffectParams = """),
            Triple("data/ProjectStorage.kt", """val lookSettings\b""", """val effectParams\b"""),
            Triple("api/model/LayerSnapshot.kt", """val lookSettings\b""", """val effectParams\b"""),
            Triple("api/scene/LayerApiImpl.kt", """\blookSettings = """, """\beffectParams = """),
            Triple("engine/render/RenderSnapshot.kt", """val lookSettings\b""", """val effectParams\b"""),
            Triple("viewmodel/EditorViewModel.kt", """val lookSettings\b""", """val effectParams\b"""),
            Triple("viewmodel/EditorViewModel.kt", """layer\.lookSettings = """, """layer\.effectParams = """)
        )
        for ((file, lookRegex, effectRegex) in sites) {
            val source = code(file)
            assertEquals("$file: lookSettings y effectParams deben aparecer igual de veces", count(source, lookRegex), count(source, effectRegex))
            assertTrue("$file: debe tener effectParams", count(source, effectRegex) > 0)
        }
    }

    @Test
    fun `lo que viene de disco se sanea antes de llegar a la capa`() {
        assertTrue(code("data/ProjectStorage.kt").contains("effectParams = layerData.effectParams.sanitized()"))
    }

    @Test
    fun `el cambio de efectos es deshacible y sanea en la frontera`() {
        val vm = code("viewmodel/EditorViewModel.kt")
        val start = vm.indexOf("fun updateEffectParams(")
        assertTrue("falta updateEffectParams", start >= 0)
        val body = vm.substring(start, vm.indexOf("\n    }\n", start))
        assertTrue("debe crear un punto de deshacer", body.contains("pushUndoCheckpoint()"))
        assertTrue("debe sanear", body.contains("params.sanitized()"))
    }

    @Test
    fun `los efectos entran en el estado de deshacer y en el de rehacer`() {
        val vm = code("viewmodel/EditorViewModel.kt")
        assertTrue(vm.contains("layer.effectParams = edit.effectParams"))      // restoreSnapshot
        assertTrue(vm.contains("effectParams = layer.effectParams,"))          // captureSnapshot y snapshot de render
        assertTrue(vm.contains("layer.effectParams = effectParams"))           // LayerContentState.applyTo
    }

    @Test
    fun `el modelo de efectos es serializable y no depende de Android`() {
        val model = code("engine/effects/gpu/LayerEffectParams.kt")
        assertEquals(2, count(model, """@Serializable"""))
        assertFalse(model.contains("import android."))
    }
}
