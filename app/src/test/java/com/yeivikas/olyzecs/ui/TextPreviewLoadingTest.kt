package com.yeivikas.olyzecs.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests de [loadTextPreview] (FASE 13 — visor de archivos de la papelera
 * de proyectos): I/O pura de disco (sin Compose, sin Android), corre como
 * JVM unit test — mismo criterio que [com.yeivikas.olyzecs.data.AtomicFileReplaceTest]
 * para no depender de Robolectric. Cubre las tres ramas reales: texto
 * plano tal cual, JSON válido re-formateado con pretty-print, y JSON
 * corrupto/truncado cayendo con gracia al texto crudo en vez de dejar al
 * usuario sin nada que leer.
 */
class TextPreviewLoadingTest {

    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        tempRoot = File.createTempFile("text-preview-test", "").apply { delete(); mkdirs() }
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    private fun fileWith(name: String, content: String): File =
        File(tempRoot, name).apply { writeText(content) }

    @Test
    fun `texto plano se devuelve tal cual, sin ningun procesamiento`() {
        val content = "línea 1\nlínea 2 con acentos: ñ, á, é\nlínea 3"
        val file = fileWith("notas.txt", content)

        val result = loadTextPreview(file, file.length(), isJson = false)

        assertEquals(content, result.rawText)
        // FASE 20: `displayText` (un único AnnotatedString con el archivo
        // entero) se reemplazó por `displayLines` (una entrada por línea,
        // virtualizada en el visor real — ver el comentario de cabecera de
        // TextualPreviewContent en FilePreviewDialog.kt). Reconstruir con
        // "\n" debe reproducir exactamente el texto original.
        assertEquals(content, result.displayLines.joinToString("\n") { it.text })
        assertEquals(false, result.truncated)
        assertNull(result.error)
    }

    @Test
    fun `json compacto en una sola linea se re-formatea con pretty-print legible`() {
        val compact = """{"nombre":"Cr7","capas":[1,2,3]}"""
        val file = fileWith("project.json", compact)

        val result = loadTextPreview(file, file.length(), isJson = true)

        assertNull("un JSON válido no debe reportar error", result.error)
        assertTrue("el resultado formateado debe tener más de una línea", result.rawText.lines().size > 1)
        assertTrue(result.rawText.contains("\"nombre\""))
        assertTrue(result.rawText.contains("\"Cr7\""))
    }

    @Test
    fun `json invalido cae con gracia al texto crudo, con aviso de error`() {
        val broken = """{"nombre": "Cr7", "capas": [1, 2,"""  // truncado a mano / corrupto
        val file = fileWith("project.json", broken)

        val result = loadTextPreview(file, file.length(), isJson = true)

        assertEquals("sin JSON parseable, se muestra el crudo tal cual", broken, result.rawText)
        assertTrue(
            "debe avisar que el JSON es inválido en vez de fallar en silencio",
            result.error?.contains("inválido", ignoreCase = true) == true
        )
    }

    @Test
    fun `archivo mas grande que el limite se trunca y se marca como truncado`() {
        val big = "x".repeat((MAX_TEXT_PREVIEW_BYTES + 5_000).toInt())
        val file = fileWith("log_gigante.txt", big)

        val result = loadTextPreview(file, file.length(), isJson = false)

        assertTrue(result.truncated)
        assertEquals(MAX_TEXT_PREVIEW_BYTES, result.rawText.length.toLong())
        assertNull(result.error)
    }

    @Test
    fun `json truncado por tamano no intenta parsear un JSON a medias`() {
        // Si sizeBytes reportado supera el límite, la función debe truncar
        // ANTES de intentar parsear — parsear un JSON cortado a la mitad
        // siempre falla, pero la ruta de código correcta es "ni lo intenta",
        // no "lo intenta, falla, y encima el error confunde con un JSON real
        // inválido" cuando en realidad es solo un archivo grande.
        val hugeJsonPrefix = "{\"data\": [" + "1,".repeat((MAX_TEXT_PREVIEW_BYTES / 2).toInt())
        val file = fileWith("data.json", hugeJsonPrefix)

        val result = loadTextPreview(file, file.length(), isJson = true)

        assertTrue(result.truncated)
        assertEquals(MAX_TEXT_PREVIEW_BYTES, result.rawText.length.toLong())
    }

    @Test
    fun `archivo vacio no crashea`() {
        val file = fileWith("vacio.txt", "")
        val result = loadTextPreview(file, 0L, isJson = false)
        assertEquals("", result.rawText)
        assertEquals(false, result.truncated)
    }

    @Test
    fun `json anidado valido se reconstruye con sus valores intactos`() {
        val nested = """{"proyecto":{"id":"abc","capas":[{"tipo":"imagen","z":1},{"tipo":"texto","z":2}]}}"""
        val file = fileWith("project.json", nested)

        val result = loadTextPreview(file, file.length(), isJson = true)

        assertNull(result.error)
        listOf("\"id\"", "\"abc\"", "\"tipo\"", "\"imagen\"", "\"texto\"").forEach {
            assertTrue("debe conservar $it", result.rawText.contains(it))
        }
    }
}
