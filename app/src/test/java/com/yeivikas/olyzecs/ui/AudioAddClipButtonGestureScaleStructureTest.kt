package com.yeivikas.olyzecs.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda que el arrastre del botón "+Clip" siga al dedo 1:1.
 *
 * Dentro de una capa gráfica ESCALADA, Compose entrega las posiciones del dedo sin escalar (÷ escala). El botón se
 * "levanta" ×1.2 mientras se arrastra y su gesto integra `positionChange()`; si el gesto vive en la capa escalada,
 * el botón avanza 1/1.2 de lo que avanza el dedo y se queda atrás un ~17 % del recorrido (imperceptible en carriles
 * cercanos, muy visible al cruzar muchos). La escala debe ir SOLO en una capa interior de dibujo, nunca en la que
 * aloja `clickable` / `pointerInput`.
 *
 * Test ESTRUCTURAL sobre el archivo real (JVM puro, sin Robolectric), como [StereoEarIconsStructureTest].
 */
class AudioAddClipButtonGestureScaleStructureTest {

    private val moduleDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "src/main/java").isDirectory }

    private val source: String by lazy {
        val f = File(moduleDir, "src/main/java/com/yeivikas/olyzecs/ui/AudioAddClipButton.kt")
        assertTrue("Falta ${f.path}", f.isFile)
        f.readText()
    }

    /** Cuerpo `{ ... }` del bloque que empieza en [openIndex] (el índice de su `{`), con llaves balanceadas. */
    private fun blockAt(openIndex: Int): String {
        var depth = 0
        for (i in openIndex until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(openIndex, i + 1)
            }
        }
        error("Llaves sin balancear")
    }

    @Test
    fun `solo una capa escala y es posterior a los gestos`() {
        val layers = Regex("""\.graphicsLayer\s*\{""").findAll(source).map { blockAt(it.range.last) to it.range.first }.toList()
        val scaling = layers.filter { (body, _) -> "scaleX" in body || "scaleY" in body }
        assertEquals("Debe haber exactamente UNA capa que escale (la del dibujo)", 1, scaling.size)

        val gestureStart = source.indexOf(".clickable(interactionSource")
        assertTrue("No se encontró el gesto de pulsar", gestureStart > 0)
        assertTrue(
            "La capa que escala debe estar DENTRO (después) del gesto, no por encima de él",
            scaling.single().second > gestureStart
        )
    }

    @Test
    fun `la capa del recorte y los gestos no escala`() {
        val clipLayer = Regex("""\.graphicsLayer\s*\{""").findAll(source)
            .map { blockAt(it.range.last) }
            .single { "clip = true" in it }
        assertFalse("scaleX en la capa que aloja los gestos", "scaleX" in clipLayer)
        assertFalse("scaleY en la capa que aloja los gestos", "scaleY" in clipLayer)
    }
}
