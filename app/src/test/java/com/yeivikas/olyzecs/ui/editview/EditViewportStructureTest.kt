package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda las garantías de la vista de edición que NO se pueden ver en pruebas de lógica pura: que cambiar el
 * fondo o el ajuste de la imagen nunca toca el proyecto ni la exportación, y que cada responsabilidad sigue en su
 * sitio. Prueba estructural sobre los archivos reales (JVM puro).
 */
class EditViewportStructureTest {

    private fun stripComments(src: String): String =
        src.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?m)//.*$"""), "")

    private val editor get() = stripComments(ProjectFiles.main("ui/EditorScreen.kt"))

    @Test
    fun `mover o hacer zoom en modo edicion no escribe en el proyecto`() {
        val start = editor.indexOf("fun commitLiveFrame()")
        assertTrue("no se encontró commitLiveFrame", start >= 0)
        val guard = editor.indexOf("if (editModeLayerId != null) return", start)
        val write = editor.indexOf("updateBaseFrameForSelectedLayer", start)
        assertTrue("commitLiveFrame debe salir antes de escribir cuando hay modo edición", guard > start && write > guard)
    }

    @Test
    fun `resincronizar desde el modelo exige decidir que pasa con la vista de edicion`() {
        // Sin valor por defecto: cada llamador debe decidirlo (ver KDoc de syncSlidersFromModel).
        assertTrue(editor.contains("fun syncSlidersFromModel(keepEditViewTransform: Boolean)"))
        val calls = Regex("""syncSlidersFromModel\(([^)]*)\)""").findAll(editor).map { it.groupValues[1] }.toList()
        val declaration = "keepEditViewTransform: Boolean"
        for (arguments in calls) {
            if (arguments == declaration) continue
            assertTrue("llamada sin keepEditViewTransform: '$arguments'", arguments.startsWith("keepEditViewTransform ="))
        }
    }

    @Test
    fun `al cambiar de capa el modo edicion se cierra ANTES de resincronizar`() {
        val effect = editor.indexOf("editViewSession = null\n            editModeOriginalSourceUri = null")
        assertTrue(effect >= 0)
        val sync = editor.indexOf("syncSlidersFromModel(keepEditViewTransform = editModeLayerId != null)", effect)
        assertTrue("el cierre del modo edición debe ir antes de la resincronización", sync > effect)
    }

    @Test
    fun `al salir se devuelven posicion y escala originales`() {
        val exit = editor.indexOf("val session = editViewSession\n        if (session != null) {")
        assertTrue(exit >= 0)
        val block = editor.substring(exit, exit + 400)
        assertTrue(block.contains("translateX = session.originalTranslateX"))
        assertTrue(block.contains("translateY = session.originalTranslateY"))
        assertTrue(block.contains("scale = session.originalScale"))
    }

    @Test
    fun `el lienzo solo conserva su forma fuera del modo edicion`() {
        assertTrue(editor.contains("if (state.isLoadingProject || editModeLayerId != null) base.fillMaxSize()"))
        assertTrue(editor.contains("else base.aspectRatio(state.canvas.aspect.ratio)"))
    }

    @Test
    fun `el fondo de trabajo solo se pasa a la vista previa en modo edicion`() {
        // La regla vive en UN solo sitio (EditViewportBackdrop); EditorScreen no la repite.
        assertTrue(editor.contains("val viewportBackdrop = EditViewportBackdrop.effective(isEditing = editModeLayerId != null, editBackdrop = editBackdrop)"))
        assertTrue(editor.contains("getBackdrop = { viewportBackdrop }"))
        assertFalse("la regla del fondo no debe duplicarse en EditorScreen", editor.contains("else PreviewBackdrop.CanvasColor"))
    }

    @Test
    fun `la exportacion y las miniaturas no conocen el fondo de la vista previa`() {
        for (file in listOf("engine/export/VideoExporter.kt", "engine/timeline/ThumbnailRenderer.kt", "engine/render/LayerDrawer.kt")) {
            val source = stripComments(ProjectFiles.main(file))
            assertFalse("$file no debe usar PreviewBackdrop", source.contains("PreviewBackdrop"))
            assertFalse("$file no debe usar BackdropDrawer", source.contains("BackdropDrawer"))
        }
    }

    @Test
    fun `el renderer de la vista previa limpia el frame con el dibujante de fondo`() {
        val renderer = stripComments(ProjectFiles.main("engine/render/GLRenderer.kt"))
        assertTrue(renderer.contains("backdropDrawer.draw(getBackdrop())"))
        assertFalse("GLRenderer ya no debe limpiar con LayerDrawer.clear()", renderer.contains("drawer.clear()"))
        assertTrue("el programa del tablero debe reiniciarse con el contexto EGL", renderer.contains("backdropDrawer.forceReinitialize()"))
    }

    @Test
    fun `el modelo del fondo es puro y OpenGL vive solo en su dibujante`() {
        val model = stripComments(ProjectFiles.main("engine/render/PreviewBackdrop.kt"))
        assertFalse("PreviewBackdrop debe ser un modelo puro (sin Android)", model.contains("import android."))
        assertFalse("PreviewBackdrop no debe tocar OpenGL", model.contains("GLES20"))
        assertTrue("el dibujante sí es quien habla con OpenGL", ProjectFiles.main("engine/render/BackdropDrawer.kt").contains("GLES20"))
    }

    @Test
    fun `la preferencia del fondo no se guarda en el proyecto`() {
        val persistenceFiles = listOf("data/EditBackgroundSettings.kt", "data/EditBackgroundPrefs.kt")
        for (file in persistenceFiles) {
            val source = stripComments(ProjectFiles.main(file))
            assertFalse("$file no debe tocar el proyecto", source.contains("project.json") || source.contains("ProjectRepository"))
        }
        assertTrue(ProjectFiles.main("data/EditBackgroundPrefs.kt").contains("getSharedPreferences"))
    }

    @Test
    fun `el ajuste de la vista de edicion tiene en cuenta la profundidad de la capa`() {
        assertTrue("la escala al entrar debe corregirse por profundidad", editor.contains("EditViewFit.provisionalScale(layer.parallaxFactor)"))
        val call = editor.indexOf("EditViewFit.fitScale(")
        assertTrue("no se encontró la llamada a fitScale", call >= 0)
        val arguments = editor.substring(call, editor.indexOf(") ?: return@LaunchedEffect", call))
        assertTrue("fitScale debe recibir el parallax de la capa editada", arguments.contains("parallaxFactor = layer.parallaxFactor"))
    }

    @Test
    fun `el ajuste usa la misma compensacion de profundidad que el renderer`() {
        val fit = stripComments(ProjectFiles.main("ui/editview/EditViewFit.kt"))
        assertTrue(fit.contains("PerspectiveCameraMath.depthCompensationFor"))
        val drawer = stripComments(ProjectFiles.main("engine/render/LayerDrawer.kt"))
        assertTrue("LayerDrawer debe seguir usando la misma función de profundidad", drawer.contains("PerspectiveCameraMath.depthCompensation("))
    }

    @Test
    fun `el contenedor detras de la superficie GL usa la misma regla que el renderer`() {
        val anchor = editor.indexOf(".onSizeChanged { hitTestBoxSize.value = it }")
        assertTrue("no se encontró el contenedor del visor", anchor > 0)
        val chain = editor.substring(maxOf(0, anchor - 200), anchor)
        assertTrue(
            "el fondo del contenedor debe salir de EditViewportBackdrop",
            chain.contains("Color(EditViewportBackdrop.behindSurfaceArgb(editModeLayerId != null, editBackdrop))")
        )
        assertFalse("el contenedor no debe fijar el verde a mano", chain.contains(".background(ChromaKeyGreen)"))
    }

    @Test
    fun `el verde del tema y el del lienzo del renderer son el mismo`() {
        // Fuera del modo edición el contenedor pasó de ChromaKeyGreen a CANVAS_CHROMA_KEY_ARGB: deben coincidir.
        val theme = stripComments(ProjectFiles.main("ui/theme/Theme.kt"))
        val themeGreen = Regex("""val ChromaKeyGreen = Color\(0x([0-9A-Fa-f]{8})\)""").find(theme)
        assertTrue("no se encontró ChromaKeyGreen en Theme.kt", themeGreen != null)
        val renderGreen = Regex("""const val CANVAS_CHROMA_KEY_ARGB: Int = 0x([0-9A-Fa-f]{8})""")
            .find(stripComments(ProjectFiles.main("engine/render/PreviewBackdrop.kt")))
        assertTrue("no se encontró CANVAS_CHROMA_KEY_ARGB", renderGreen != null)
        assertTrue(themeGreen!!.groupValues[1].equals(renderGreen!!.groupValues[1], ignoreCase = true))
    }
}
