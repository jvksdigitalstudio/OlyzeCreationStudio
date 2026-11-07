package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.engine.render.PerspectiveCameraMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class EditViewFitTest {

    private companion object {
        /** Capa de sujeto: vive en el plano de foco, sin compensación de profundidad. */
        const val SUBJECT_PARALLAX = 1f

        /** Parallax con el que el editor crea el fondo importado (EditorViewModel.importBackground). */
        const val IMPORTED_BACKGROUND_PARALLAX = 0.35f
    }

    // Visor de ejemplo: 1600 x 1100 px (apaisado, ~1,45:1), parecido al de una tablet en vertical bajo la barra.
    private fun fit(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Int = 1600,
        viewportHeight: Int = 1100,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotationDeg: Float = 0f,
        parallaxFactor: Float = SUBJECT_PARALLAX
    ): Float = EditViewFit.fitScale(
        imageWidth, imageHeight, viewportWidth, viewportHeight, scaleX, scaleY, rotationDeg, parallaxFactor
    )!!

    @Test
    fun `una imagen apaisada ocupa el 90 por ciento del ancho`() {
        assertEquals(EditViewFit.FILL_FRACTION, fit(2000, 1000), 1e-4f)
    }

    @Test
    fun `una imagen cuadrada queda limitada por el alto y no por el ancho`() {
        val scale = fit(1000, 1000)
        assertEquals(EditViewFit.FILL_FRACTION, scale, 1e-4f)
        // Con scale = 0,9 la cuadrada ocupa 90 % del ALTO y solo ~62 % del ancho: nunca se sale por los lados.
        val widthFraction = scale * (1000f / 1100f) / (1600f / 1100f)
        assertTrue("el ancho no debe pasar del 90 %", widthFraction < EditViewFit.FILL_FRACTION)
    }

    @Test
    fun `una imagen vertical se limita por el alto`() {
        assertEquals(EditViewFit.FILL_FRACTION, fit(800, 1600), 1e-4f)
    }

    @Test
    fun `el estiramiento horizontal reduce la escala para que no se salga`() {
        assertEquals(EditViewFit.FILL_FRACTION / 2f, fit(2000, 1000, scaleX = 2f), 1e-4f)
    }

    @Test
    fun `girar 90 grados una imagen apaisada la limita por el alto`() {
        // Rotada 90°, el lado largo pasa a ser vertical: el visor es más ancho que alto, así que manda el alto.
        assertEquals(EditViewFit.FILL_FRACTION, fit(2000, 1000, rotationDeg = 90f), 1e-4f)
    }

    @Test
    fun `girar 45 grados reduce la escala segun el rectangulo envolvente`() {
        val half = 1.4545454f / 2f  // viewportAspect / imageAspect, con imagen 2:1 en visor 1600x1100
        val expected = EditViewFit.FILL_FRACTION / ((1f + half) * (kotlin.math.sqrt(2f) / 2f))
        assertEquals(expected, fit(2000, 1000, rotationDeg = 45f), 1e-3f)
    }

    @Test
    fun `el resultado nunca desborda el visor sea cual sea la combinacion`() {
        val sizes = listOf(2000 to 1000, 1000 to 1000, 800 to 1600, 4000 to 300, 300 to 4000)
        val viewports = listOf(1600 to 1100, 1100 to 1600, 900 to 900, 2400 to 800)
        val stretches = listOf(0.5f to 1f, 1f to 1f, 2f to 1f, 1f to 3f)
        val rotations = listOf(0f, 15f, 45f, 90f, 135f, 270f)
        for ((iw, ih) in sizes) for ((vw, vh) in viewports) for ((sx, sy) in stretches) for (rot in rotations) {
            val scale = fit(iw, ih, vw, vh, sx, sy, rot)
            // Reconstruye el rectángulo envolvente (espacio normalizado) con esa escala y comprueba que cabe.
            val imageAspect = iw.toFloat() / ih
            val viewportAspect = vw.toFloat() / vh
            val baseW = if (imageAspect > viewportAspect) 1f else imageAspect / viewportAspect
            val baseH = if (imageAspect > viewportAspect) viewportAspect / imageAspect else 1f
            val w = baseW * sx * scale
            val h = baseH * sy * scale
            val rad = Math.toRadians(rot.toDouble())
            val bw = w * abs(cos(rad)).toFloat() + h * abs(sin(rad)).toFloat()
            val bh = w * abs(sin(rad)).toFloat() + h * abs(cos(rad)).toFloat()
            assertTrue("desborda: $iw x $ih en $vw x $vh, estiramiento $sx/$sy, giro $rot", max(bw, bh) <= EditViewFit.FILL_FRACTION + 1e-3f)
            assertTrue("no debe quedar menor que el 90 %: lo más grande debe llegar a la fracción", max(bw, bh) >= EditViewFit.FILL_FRACTION - 1e-3f)
        }
    }

    @Test
    fun `devuelve null mientras falte alguna medida`() {
        assertNull(EditViewFit.fitScale(0, 1000, 1600, 1100, 1f, 1f, 0f, SUBJECT_PARALLAX))
        assertNull(EditViewFit.fitScale(1000, 0, 1600, 1100, 1f, 1f, 0f, SUBJECT_PARALLAX))
        assertNull(EditViewFit.fitScale(1000, 1000, 0, 1100, 1f, 1f, 0f, SUBJECT_PARALLAX))
        assertNull(EditViewFit.fitScale(1000, 1000, 1600, 0, 1f, 1f, 0f, SUBJECT_PARALLAX))
    }

    @Test
    fun `un estiramiento nulo no divide por cero`() {
        assertNull(EditViewFit.fitScale(1000, 1000, 1600, 1100, 0f, 0f, 0f, SUBJECT_PARALLAX))
    }

    @Test
    fun `una capa de sujeto no recibe correccion de profundidad`() {
        assertEquals(EditViewFit.FILL_FRACTION, EditViewFit.provisionalScale(SUBJECT_PARALLAX), 1e-6f)
    }

    @Test
    fun `una capa de fondo se reduce para compensar que se dibuja mas grande`() {
        val subject = fit(2000, 1000, parallaxFactor = SUBJECT_PARALLAX)
        val background = fit(2000, 1000, parallaxFactor = IMPORTED_BACKGROUND_PARALLAX)
        val compensation = PerspectiveCameraMath.depthCompensationFor(IMPORTED_BACKGROUND_PARALLAX)
        assertTrue("el fondo debe usar menos escala que el sujeto", background < subject)
        assertEquals(subject / compensation, background, 1e-4f)
    }

    @Test
    fun `lo que dibuja el renderer ocupa la fraccion pedida tambien con parallax`() {
        // Reproduce lo que hace LayerDrawer.drawLayer: ajuste contain x scale x compensación de profundidad.
        for (parallax in listOf(1f, 0.8f, IMPORTED_BACKGROUND_PARALLAX, 0f)) {
            val scale = fit(2000, 1000, parallaxFactor = parallax)
            val drawnWidthFraction = 1f * scale * PerspectiveCameraMath.depthCompensationFor(parallax)
            assertEquals("parallax $parallax", EditViewFit.FILL_FRACTION, drawnWidthFraction, 1e-4f)
        }
    }

    @Test
    fun `la escala provisional y la definitiva coinciden en una imagen que llena el ancho sin deformar`() {
        for (parallax in listOf(1f, IMPORTED_BACKGROUND_PARALLAX, 0f)) {
            assertEquals(
                "parallax $parallax",
                EditViewFit.provisionalScale(parallax),
                fit(2000, 1000, parallaxFactor = parallax),
                1e-4f
            )
        }
    }

    @Test
    fun `un parallax corrupto se trata como capa de sujeto`() {
        val corrupt = fit(2000, 1000, parallaxFactor = Float.NaN)
        assertEquals(fit(2000, 1000, parallaxFactor = SUBJECT_PARALLAX), corrupt, 1e-6f)
        assertEquals(EditViewFit.provisionalScale(SUBJECT_PARALLAX), EditViewFit.provisionalScale(Float.POSITIVE_INFINITY), 1e-6f)
    }
}
