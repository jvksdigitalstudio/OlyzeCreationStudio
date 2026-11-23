package com.yeivikas.olyzecs.engine.effects.gpu

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Blinda, sobre los archivos reales, la integración de la etapa de efectos GPU (ADR-019): que los tres renderers la usan
 * igual, que el núcleo sigue puro, que nada se activa en producción sin pasar la verificación y que todo pase de OpenGL
 * devuelve el estado intacto. No ejecuta OpenGL: eso lo hace el autotest dentro de la app.
 */
class GpuEffectIntegrationStructureTest {

    private fun code(relative: String): String =
        ProjectFiles.main(relative)
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""(?m)//.*$"""), "")

    private fun count(source: String, regex: String) = Regex(regex).findAll(source).count()

    @Test
    fun `los tres renderers dibujan las capas con la textura de la etapa y con el tamano logico de la capa`() {
        // Tamaño LÓGICO con el que cada renderer dibuja (para la relación de aspecto). La textura que devuelve la etapa puede
        // tener otro tamaño (recortada a la GPU, o una vista previa); el dibujo no debe depender de él.
        val logicalSize = mapOf(
            "engine/render/GLRenderer.kt" to ("liveLayer.widthPx" to "liveLayer.heightPx"),
            "engine/export/VideoExporter.kt" to ("w" to "h"),
            "engine/timeline/ThumbnailRenderer.kt" to ("w" to "h")
        )
        for ((file, size) in logicalSize) {
            val source = code(file)
            assertEquals("$file: una llamada a resolve", 1, count(source, Regex.escape("effectStage.textureIdFor(")))
            val resolveAt = source.indexOf("effectStage.textureIdFor(")
            // El drawLayer que SIGUE a resolve es el de las capas (en GLRenderer hay otro antes: el de la rejilla).
            val drawAt = source.indexOf(".drawLayer(", resolveAt)
            assertTrue("$file: debe haber un drawLayer tras resolve", drawAt > resolveAt)
            val draw = source.substring(drawAt)
            val args = draw.substring(0, draw.indexOf(")\n"))
            assertTrue("$file: dibuja con la textura que devuelve la etapa", args.contains("textureId = layerTextureId,"))
            assertTrue("$file: ancho lógico ${size.first}", args.contains("imageWidthPx = ${size.first},"))
            assertTrue("$file: alto lógico ${size.second}", args.contains("imageHeightPx = ${size.second},"))
            assertFalse("$file: el aspecto no debe salir del tamaño de la textura", args.contains("layerTextureId.") || args.contains("layerTexture."))
            // Ningún otro drawLayer posterior se salta la etapa.
            assertEquals("$file: solo un drawLayer de capas tras resolve", 1, count(source.substring(resolveAt), """\.drawLayer\("""))
        }
    }

    @Test
    fun `la etapa recibe el tamano REAL de la textura subida y no el logico de la capa`() {
        val preview = code("engine/render/GLRenderer.kt")
        assertTrue("el registro guarda el tamaño subido", preview.contains("val widthPx: Int,") && preview.contains("val heightPx: Int,"))
        assertTrue(preview.contains("widthPx = uploadedWidthPx,"))
        assertTrue(preview.contains("textureWidthPx = textureRecord?.widthPx ?: 0"))
        assertTrue(preview.contains("textureHeightPx = textureRecord?.heightPx ?: 0"))
        assertFalse("no debe pasar el tamaño lógico como tamaño de textura", preview.contains("width = liveLayer.widthPx"))
        // Exportador y miniaturas: la textura ES el bitmap subido, y se nombra para que el contrato sea legible.
        for (file in listOf("engine/export/VideoExporter.kt", "engine/timeline/ThumbnailRenderer.kt")) {
            val source = code(file)
            assertTrue("$file: ancho de textura", source.contains("textureWidthPx = w"))
            assertTrue("$file: alto de textura", source.contains("textureHeightPx = h"))
        }
    }

    @Test
    fun `la rejilla de referencia no es una capa y no pasa por la etapa de efectos`() {
        val preview = code("engine/render/GLRenderer.kt")
        val gridDraw = preview.indexOf(".drawLayer(")
        assertTrue(gridDraw in 0 until preview.indexOf("effectStage.textureIdFor("))
        val args = preview.substring(gridDraw, preview.indexOf(")\n", gridDraw))
        assertTrue("el primer drawLayer es el de la rejilla", args.contains("textureId = gridTextureId"))
    }

    @Test
    fun `cada renderer pasa los efectos de su capa a la etapa`() {
        assertTrue(code("engine/render/GLRenderer.kt").contains("params = snapLayer.effectParams"))
        assertTrue(code("engine/export/VideoExporter.kt").contains("layer.effectParams"))
        assertTrue(code("engine/timeline/ThumbnailRenderer.kt").contains("layer.effectParams"))
    }

    @Test
    fun `el ciclo de vida de la etapa sigue al de los demas recursos GPU`() {
        val preview = code("engine/render/GLRenderer.kt")
        assertTrue("al recrearse el contexto se olvida sin destruir", preview.contains("effectStage.onContextLost()"))
        assertTrue("al borrarse capas se liberan sus resultados", preview.contains("effectStage.retainOnly("))
        assertTrue(code("engine/export/VideoExporter.kt").contains("effectStage.dispose()"))
        assertTrue(code("engine/timeline/ThumbnailRenderer.kt").contains("effectStage.dispose()"))
    }

    @Test
    fun `el autotest se ejecuta en el contexto de la vista previa y en el del exportador`() {
        assertTrue(code("engine/render/GLRenderer.kt").contains("""GpuEffectSelfTest.runOnce("vista previa")"""))
        assertTrue(code("engine/export/VideoExporter.kt").contains("""GpuEffectSelfTest.runOnce("exportador")"""))
    }

    @Test
    fun `en produccion ningun pase esta activado hasta superar la verificacion en el dispositivo`() {
        val stage = code("engine/effects/gpu/gl/GpuEffectStage.kt")
        assertTrue(stage.contains("enabledPasses: Set<EffectPassKind> = emptySet()"))
        // Ningún renderer construye la etapa con pases activados.
        for (file in listOf("engine/render/GLRenderer.kt", "engine/export/VideoExporter.kt", "engine/timeline/ThumbnailRenderer.kt")) {
            assertEquals("$file", 1, count(code(file), """GpuEffectStage\(\)"""))
            assertFalse("$file", code(file).contains("GpuEffectStage(enabledPasses"))
        }
    }

    @Test
    fun `el nucleo puro del paquete gpu no importa Android ni OpenGL`() {
        val pure = File(ProjectFiles.mainFile("engine/effects/gpu").path).listFiles { f -> f.isFile && f.extension == "kt" }!!
        assertTrue("debe haber archivos puros", pure.size >= 9)
        for (file in pure) {
            val source = file.readText()
            assertFalse("${file.name} no debe importar android", source.contains("import android."))
            assertFalse("${file.name} no debe usar GLES", source.contains("GLES"))
        }
    }

    @Test
    fun `solo el subpaquete gl toca OpenGL`() {
        val gl = File(ProjectFiles.mainFile("engine/effects/gpu/gl").path).listFiles { f -> f.isFile && f.extension == "kt" }!!
        assertTrue(gl.any { it.readText().contains("GLES20") })
    }

    @Test
    fun `todo pase de OpenGL devuelve el estado intacto`() {
        val runner = code("engine/effects/gpu/gl/EffectPassRunner.kt")
        // runPass, readPixels y probeFbo cambian el framebuffer, el viewport y otros interruptores: los tres protegidos.
        assertEquals(3, count(runner, """GlStateGuard\.preserving"""))
        val selfTest = code("engine/effects/gpu/gl/GpuEffectSelfTest.kt")
        assertTrue(selfTest.contains("GlStateGuard.preserving"))
        val guard = code("engine/effects/gpu/gl/GlStateGuard.kt")
        for (state in listOf("GL_VIEWPORT", "GL_FRAMEBUFFER_BINDING", "GL_CURRENT_PROGRAM", "GL_ARRAY_BUFFER_BINDING", "GL_TEXTURE_BINDING_2D", "GL_ACTIVE_TEXTURE", "GL_COLOR_WRITEMASK", "GL_BLEND", "GL_SCISSOR_TEST", "GL_DEPTH_TEST", "GL_CULL_FACE")) {
            assertTrue("el guardián debe guardar $state", guard.contains(state))
        }
        assertTrue("restaura aunque el bloque falle", guard.contains("finally"))
    }

    @Test
    fun `los shaders usan precision alta cuando existe y el when de pases es exhaustivo`() {
        val shaders = code("engine/effects/gpu/gl/EffectShaders.kt")
        assertTrue(shaders.contains("#ifdef GL_FRAGMENT_PRECISION_HIGH"))
        assertTrue(shaders.contains("precision highp float;"))
        val fragmentFor = shaders.substring(shaders.indexOf("fun fragmentFor("))
        assertFalse("sin else: un tipo de pase nuevo debe obligar a decidir su shader", fragmentFor.contains("else ->"))
        val padding = code("engine/effects/gpu/EffectPaddingCalculator.kt")
        assertFalse("sin else: un tipo de pase nuevo debe obligar a declarar su margen", padding.substring(padding.indexOf("private fun paddingOf")).contains("else ->"))
    }

    @Test
    fun `una capa sin efectos o sin textura valida no se toca`() {
        val stage = code("engine/effects/gpu/gl/GpuEffectStage.kt")
        assertTrue(stage.contains("if (textureId <= 0 || textureWidthPx <= 0 || textureHeightPx <= 0) return textureId"))
        assertTrue(stage.contains("if (plan.isEmpty) return textureId"))
    }

    @Test
    fun `el autotest compara con el criterio de identidad y nunca lanza`() {
        val selfTest = code("engine/effects/gpu/gl/GpuEffectSelfTest.kt")
        assertTrue(selfTest.contains("EquivalenceCriteria.IDENTITY"))
        assertTrue(selfTest.contains("catch (t: Throwable)"))
    }

    @Test
    fun `la via rapida de capas neutras va antes de planificar y no asigna memoria`() {
        val stage = code("engine/effects/gpu/gl/GpuEffectStage.kt")
        val fast = stage.indexOf("if (params.isNeutral) return textureId")
        val plan = stage.indexOf("EffectPassPlanner.plan(params)")
        assertTrue("debe existir la vía rápida", fast > 0)
        assertTrue("la vía rápida debe ir ANTES de planificar (el plan crea copias en cada frame)", fast < plan)
    }

    @Test
    fun `el registro de motivos no construye texto en cada frame`() {
        val stage = code("engine/effects/gpu/gl/GpuEffectStage.kt")
        assertTrue(stage.contains("private inline fun reportOnce(layerId: String, key: String, message: () -> String)"))
        assertFalse("la clave debe ser constante, no una interpolación por frame", stage.contains("alreadyReported.add(\"\$layerId|"))
    }

    @Test
    fun `la etapa devuelve un entero y no asigna ningun objeto en el camino de las capas sin efectos`() {
        val stage = code("engine/effects/gpu/gl/GpuEffectStage.kt")
        assertFalse("ResolvedTexture ya no existe", stage.contains("ResolvedTexture"))
        val start = stage.indexOf("fun textureIdFor(")
        val fastPath = stage.substring(start, stage.indexOf("if (params.isNeutral) return textureId", start))
        assertFalse("nada se construye antes de la vía rápida", fastPath.contains("= ResolvedTexture") || Regex("""\bval \w+ = \w+\(""").containsMatchIn(fastPath))
        assertTrue(stage.contains("): Int {"))
    }

    @Test
    fun `cada subida de textura produce una version nueva de la fuente para la cache de la etapa`() {
        val preview = code("engine/render/GLRenderer.kt")
        assertTrue("el registro guarda un número de subida", preview.contains("val uploadSerial: Long"))
        assertTrue("se incrementa en cada subida", preview.contains("uploadSerial = ++textureUploadCounter"))
        assertEquals("un único punto crea registros", 1, count(preview, """(?<!class )LayerTextureRecord\("""))
        assertTrue("la etapa recibe esa versión", preview.contains("sourceVersion = textureRecord?.uploadSerial ?: 0L"))
        assertFalse("la revisión de contenido ya no es la clave: las vistas previas la repiten", preview.contains("contentRevision = textureRecord?.contentRevision"))
        assertTrue(code("engine/export/VideoExporter.kt").contains("sourceVersion = EXPORT_SOURCE_VERSION"))
        assertTrue(code("engine/timeline/ThumbnailRenderer.kt").contains("sourceVersion = 0L"))
    }
}

