package com.yeivikas.olyzecs.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda la orientación de los oídos del panorama: ambos deben mirar hacia adentro (hacia el centro de la barra).
 *
 * Test ESTRUCTURAL sobre los archivos reales (JVM puro, sin Robolectric): el vector derecho debe ser el espejo
 * exacto del izquierdo en el eje vertical central del viewBox (x' = 24 − x), y el código no debe volver a voltear
 * el dibujo en ejecución (`scaleX = -1f`), que fue la causa de que ambos quedaran abriendo hacia afuera.
 */
class StereoEarIconsStructureTest {

    private val moduleDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "src/main/res/drawable").isDirectory }

    private fun drawable(name: String) = File(moduleDir, "src/main/res/drawable/$name.xml")

    private fun pathDataOf(name: String): List<String> {
        val f = drawable(name)
        assertTrue("Falta el recurso $name.xml", f.isFile)
        return Regex("android:pathData=\"([^\"]+)\"").findAll(f.readText()).map { it.groupValues[1] }.toList()
    }

    /** Coordenadas X de un path de solo `M`/`C` absolutos (pares x,y). */
    private fun xs(pathData: String): List<Float> =
        Regex("-?\\d+(?:\\.\\d+)?").findAll(pathData).map { it.value.toFloat() }.toList()
            .filterIndexed { index, _ -> index % 2 == 0 }

    private fun ys(pathData: String): List<Float> =
        Regex("-?\\d+(?:\\.\\d+)?").findAll(pathData).map { it.value.toFloat() }.toList()
            .filterIndexed { index, _ -> index % 2 == 1 }

    @Test
    fun `el oido derecho es el espejo exacto del izquierdo`() {
        val left = pathDataOf("ic_ear_left")
        val right = pathDataOf("ic_ear_right")
        assertEquals("Mismo número de trazos", left.size, right.size)
        left.zip(right).forEach { (l, r) ->
            assertEquals(ys(l), ys(r))
            assertEquals(xs(l).map { 24f - it }, xs(r).map { it })
        }
    }

    @Test
    fun `ambos oidos abren hacia el centro`() {
        // El hélix (primer trazo) arranca en la concha: su primer punto está más cerca del centro (x = 12) que el
        // extremo exterior del dibujo. Izquierdo: arranca a la derecha del bulto; derecho: a la izquierda.
        val left = xs(pathDataOf("ic_ear_left").first())
        val right = xs(pathDataOf("ic_ear_right").first())
        assertTrue("Izquierdo: el bulto exterior debe quedar a la izquierda", left.min() < 12f - 6f)
        assertTrue("Izquierdo: la abertura debe quedar hacia la derecha", left.first() > left.min())
        assertTrue("Derecho: el bulto exterior debe quedar a la derecha", right.max() > 12f + 6f)
        assertTrue("Derecho: la abertura debe quedar hacia la izquierda", right.first() < right.max())
    }

    @Test
    fun `el panel no voltea el oido en ejecucion`() {
        val src = File(moduleDir, "src/main/java/com/yeivikas/olyzecs/ui/AudioTrackMixSliders.kt").readText()
        assertFalse("StereoEar no debe espejar con scaleX", src.contains("scaleX"))
        assertTrue(src.contains("R.drawable.ic_ear_left") && src.contains("R.drawable.ic_ear_right"))
    }

    @Test
    fun `el oido es solo visual y absorbe sus toques sin disparar acciones vecinas`() {
        val src = File(moduleDir, "src/main/java/com/yeivikas/olyzecs/ui/AudioTrackMixSliders.kt").readText()
        val ear = src.substringAfter("private fun StereoEar(").substringBefore("\n}\n")
        assertTrue("StereoEar debe absorber toques (absorbTouches)", ear.contains("absorbTouches()"))
        assertFalse("StereoEar no debe tener acción propia", ear.contains("clickable"))
    }
}
