package com.yeivikas.olyzecs.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [splitAnnotatedStringIntoLines] (FASE 20 — corrección real del
 * lag al navegar/scrollear un JSON en el visor de archivos): la función
 * que parte el `AnnotatedString` ya resaltado de todo el archivo en una
 * entrada por línea, la unidad real que renderiza el `LazyColumn`
 * virtualizado de `TextualPreviewContent` en vez de un único nodo de texto
 * gigante (la causa raíz real del lag reportado). El riesgo concreto acá
 * es el mismo que ya cubre [JsonSyntaxHighlightTest] para el resaltado:
 * que dividir en líneas sea EXACTAMENTE reversible (ningún carácter
 * perdido, duplicado, ni un salto de línea de más), y que cada línea
 * conserve el color que le corresponde después del recorte.
 */
class TextPreviewLineSplitTest {

    private fun colorAt(annotated: AnnotatedString, index: Int) =
        annotated.spanStyles.firstOrNull { index >= it.start && index < it.end }?.item?.color

    @Test
    fun `texto sin saltos de linea produce una sola linea identica`() {
        val input = AnnotatedString("una sola línea sin saltos")
        val lines = splitAnnotatedStringIntoLines(input)
        assertEquals(1, lines.size)
        assertEquals(input.text, lines[0].text)
    }

    @Test
    fun `texto vacio produce una unica linea vacia, sin crashear`() {
        val lines = splitAnnotatedStringIntoLines(AnnotatedString(""))
        assertEquals(1, lines.size)
        assertEquals("", lines[0].text)
    }

    @Test
    fun `reconstruir con salto de linea reproduce el texto original exacto`() {
        val original = "{\n  \"a\": 1,\n  \"b\": [\n    2,\n    3\n  ]\n}"
        val lines = splitAnnotatedStringIntoLines(AnnotatedString(original))
        assertEquals(original, lines.joinToString("\n") { it.text })
    }

    @Test
    fun `salto de linea final produce una ultima linea vacia, igual que String_lines()`() {
        val original = "primera\nsegunda\n"
        val lines = splitAnnotatedStringIntoLines(AnnotatedString(original))
        // Mismo criterio que Kotlin `String.lines()`: "a\n".lines() == ["a", ""]
        assertEquals(original.lines(), lines.map { it.text })
    }

    @Test
    fun `ninguna linea contiene el caracter de salto de linea`() {
        val original = "uno\ndos\ntres\ncuatro"
        val lines = splitAnnotatedStringIntoLines(AnnotatedString(original))
        lines.forEach { assertTrue("ninguna línea debe contener '\\n'", '\n' !in it.text) }
    }

    @Test
    fun `cada linea conserva el color de sus propios spans tras el recorte`() {
        // Dos líneas, cada una con un span de color distinto — simula lo
        // que produce highlightJson() sobre un JSON de verdad.
        val annotated = buildAnnotatedStringForTest {
            withStyle(SpanStyle(color = Color.Red)) { append("\"clave1\"") }
            append(": 1,\n")
            withStyle(SpanStyle(color = Color.Blue)) { append("\"clave2\"") }
            append(": 2")
        }

        val lines = splitAnnotatedStringIntoLines(annotated)
        assertEquals(2, lines.size)
        assertEquals(Color.Red, colorAt(lines[0], 1))
        assertEquals(Color.Blue, colorAt(lines[1], 1))
    }

    @Test
    fun `un span que cruza el salto de linea se recorta en ambas lineas sin perder color`() {
        // Un solo span de color cubriendo texto en ambos lados de un "\n"
        // (no debería pasar con JSON real —las strings de JSON no pueden
        // contener un salto de línea crudo—, pero la función debe seguir
        // siendo correcta igual si algún día se usa con otro lenguaje).
        val annotated = buildAnnotatedStringForTest {
            withStyle(SpanStyle(color = Color.Green)) { append("abc\ndef") }
        }

        val lines = splitAnnotatedStringIntoLines(annotated)
        assertEquals(2, lines.size)
        assertEquals("abc", lines[0].text)
        assertEquals("def", lines[1].text)
        assertEquals(Color.Green, colorAt(lines[0], 0))
        assertEquals(Color.Green, colorAt(lines[1], 0))
    }

    private fun buildAnnotatedStringForTest(block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit): AnnotatedString =
        androidx.compose.ui.text.buildAnnotatedString(block)
}
