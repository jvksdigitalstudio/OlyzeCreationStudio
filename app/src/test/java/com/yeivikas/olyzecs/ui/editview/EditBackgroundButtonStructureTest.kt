package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vigila que el botón de fondo siga siendo reconocible con cualquier fondo y que cada pieza conserve su única
 * responsabilidad (estilo puro / dibujo de la miniatura / gestos y menú). Estructural sobre los archivos reales.
 */
class EditBackgroundButtonStructureTest {

    private fun stripComments(src: String): String =
        src.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?m)//.*$"""), "")

    private val button get() = stripComments(ProjectFiles.main("ui/editview/EditBackgroundButton.kt"))
    private val swatch get() = stripComments(ProjectFiles.main("ui/editview/BackdropSwatch.kt"))
    private val contrast get() = stripComments(ProjectFiles.main("ui/editview/EditBackdropContrast.kt"))

    @Test
    fun `el area tactil del boton es de al menos 48 dp`() {
        val size = Regex("""private val ButtonSize = (\d+)\.dp""").find(button)
        assertTrue("no se encontró ButtonSize", size != null)
        assertTrue("el botón debe medir al menos 48 dp", size!!.groupValues[1].toInt() >= 48)
    }

    @Test
    fun `la miniatura lleva marco de dos tonos para distinguirse sobre cualquier fondo`() {
        assertTrue(button.contains(".border(SwatchOuterRing, Color.White, SwatchShape)"))
        assertTrue(button.contains(".border(SwatchInnerLine, SwatchInnerLineColor, SwatchShape)"))
    }

    @Test
    fun `el icono de cambiar toma su color del contraste y no de un valor fijo`() {
        assertTrue(button.contains("EditBackdropContrast.iconArgbOver(backdrop)"))
    }

    @Test
    fun `el boton anuncia la pulsacion larga con una marca de esquina`() {
        assertTrue(button.contains("MenuHintCorner("))
    }

    @Test
    fun `el boton solo presenta y la miniatura vive en su propio archivo`() {
        assertFalse("el botón no debe volver a declarar la miniatura", button.contains("fun BackdropSwatch("))
        assertTrue(swatch.contains("fun BackdropSwatch("))
    }

    @Test
    fun `las piezas de presentacion no conocen OpenGL ni el almacenamiento`() {
        for ((name, source) in listOf("EditBackgroundButton" to button, "BackdropSwatch" to swatch)) {
            assertFalse("$name no debe tocar OpenGL", source.contains("GLES20") || source.contains("BackdropDrawer"))
            assertFalse("$name no debe tocar el almacenamiento", source.contains("SharedPreferences") || source.contains("EditBackgroundPrefs"))
        }
    }

    @Test
    fun `el calculo de contraste es puro y no depende de Compose ni de Android`() {
        assertFalse(contrast.contains("import android."))
        assertFalse(contrast.contains("import androidx."))
    }
}
