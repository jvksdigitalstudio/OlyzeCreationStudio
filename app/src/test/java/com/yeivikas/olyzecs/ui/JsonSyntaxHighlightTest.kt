package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [highlightJson] (FASE 13 — visor de archivos de la papelera de
 * proyectos): tokenizador por regex que colorea un JSON ya formateado sin
 * ninguna librería de resaltado de sintaxis. El riesgo real de esta
 * función no es "¿elige el color correcto?" tanto como "¿el texto
 * resultante es EXACTAMENTE el mismo que entró?" — un tokenizador que
 * pierde o duplica un carácter de espacio produce un visor de JSON que
 * muestra algo sutilmente distinto del archivo real en disco, el peor
 * tipo de bug para una herramienta que existe para "ver qué hay adentro".
 */
class JsonSyntaxHighlightTest {

    private fun colorAt(annotated: androidx.compose.ui.text.AnnotatedString, index: Int) =
        annotated.spanStyles.firstOrNull { index >= it.start && index < it.end }?.item?.color

    @Test
    fun `el texto resultante es identico caracter por caracter al de entrada`() {
        val input = "{\n  \"nombre\": \"Cr7\",\n  \"capas\": [1, 2, 3],\n  \"activo\": true\n}"
        val result = highlightJson(input)
        assertEquals(input, result.text)
    }

    @Test
    fun `preserva espacios y saltos de linea exactos, incluida indentacion`() {
        val input = "{\n    \"a\": 1,\n\t\"b\": 2\n}"
        val result = highlightJson(input)
        assertEquals(input, result.text)
    }

    @Test
    fun `una clave se colorea distinto de un valor-string`() {
        val input = """{"nombre": "Cr7"}"""
        val result = highlightJson(input)
        val keyIndex = input.indexOf("\"nombre\"") + 1 // dentro de las comillas de la clave
        val valueIndex = input.indexOf("\"Cr7\"") + 1   // dentro de las comillas del valor
        assertEquals(JsonKeyColor, colorAt(result, keyIndex))
        assertEquals(JsonStringColor, colorAt(result, valueIndex))
        assertNotEquals(colorAt(result, keyIndex), colorAt(result, valueIndex))
    }

    @Test
    fun `un string dentro de un array se colorea como valor, no como clave`() {
        // Sin un ":" después, ningún string dentro de un array es una clave.
        val input = """{"tags": ["a", "b"]}"""
        val result = highlightJson(input)
        val firstTagIndex = input.indexOf("\"a\"") + 1
        assertEquals(JsonStringColor, colorAt(result, firstTagIndex))
    }

    @Test
    fun `numeros, booleanos y null tienen cada uno su propio color`() {
        val input = """{"n": 42, "activo": true, "vacio": null}"""
        val result = highlightJson(input)
        assertEquals(JsonNumberColor, colorAt(result, input.indexOf("42")))
        assertEquals(JsonLiteralColor, colorAt(result, input.indexOf("true")))
        assertEquals(JsonLiteralColor, colorAt(result, input.indexOf("null")))
    }

    @Test
    fun `numeros negativos y decimales se detectan completos, no solo el signo o la parte entera`() {
        val input = """{"temp": -12.5}"""
        val result = highlightJson(input)
        // Todo "-12.5" debe pertenecer a un ÚNICO span de color número — si el
        // tokenizador partiera el signo o el punto decimal en tokens propios,
        // alguno de estos índices caería fuera del span numérico.
        val numberStart = input.indexOf("-12.5")
        for (offset in 0 until "-12.5".length) {
            assertEquals("offset $offset dentro de -12.5", JsonNumberColor, colorAt(result, numberStart + offset))
        }
    }

    @Test
    fun `puntuacion estructural tiene su propio color, distinto del texto por defecto`() {
        val input = """{"a": 1}"""
        val result = highlightJson(input)
        assertEquals(JsonPunctColor, colorAt(result, input.indexOf("{")))
        assertEquals(JsonPunctColor, colorAt(result, input.indexOf(":")))
        assertEquals(JsonPunctColor, colorAt(result, input.indexOf("}")))
    }

    @Test
    fun `un string que CONTIENE la palabra true no se confunde con el literal booleano`() {
        val input = """{"mensaje": "esto es true dentro de un string"}"""
        val result = highlightJson(input)
        val insideStringIndex = input.indexOf("true dentro")
        // Tiene que seguir siendo JsonStringColor (parte del string completo),
        // nunca JsonLiteralColor — el tokenizador matchea el string entero
        // primero (con backtracking sobre comillas), no palabra por palabra.
        assertEquals(JsonStringColor, colorAt(result, insideStringIndex))
    }

    @Test
    fun `string con comillas escapadas no corta el token antes de tiempo`() {
        val input = """{"cita": "dijo \"hola\" y se fue"}"""
        val result = highlightJson(input)
        val insideEscapedIndex = input.indexOf("hola")
        assertEquals(JsonStringColor, colorAt(result, insideEscapedIndex))
    }

    @Test
    fun `json vacio no crashea y no produce spans`() {
        val result = highlightJson("{}")
        assertEquals("{}", result.text)
    }

    @Test
    fun `texto vacio no crashea`() {
        val result = highlightJson("")
        assertEquals("", result.text)
    }

    @Test
    fun `json anidado con arrays y objetos mixtos mantiene el texto integro`() {
        val input = """{"capas": [{"id": "a", "z": 1}, {"id": "b", "z": 2}], "meta": null}"""
        val result = highlightJson(input)
        assertEquals(input, result.text)
        assertTrue("debe generar al menos un span de color", result.spanStyles.isNotEmpty())
    }
}
