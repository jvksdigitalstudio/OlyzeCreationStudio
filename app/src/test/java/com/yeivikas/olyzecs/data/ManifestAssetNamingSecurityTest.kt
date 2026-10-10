package com.yeivikas.olyzecs.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Seguridad del manifest de un `.olycs` importado: los identificadores y nombres que vienen de
 * `project.json` NUNCA pueden decidir una ruta fuera de la carpeta del proyecto, y el propio
 * `project.json` no puede ser lo bastante grande como para agotar la memoria al leerlo.
 *
 * Ejercita el MISMO código que usa producción ([assetFileStem], [safeAudioExtension],
 * [extractZipEntriesSafely]); JVM puro, sin Android.
 */
class ManifestAssetNamingSecurityTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("olyze-manifest-sec").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    // ---- assetFileStem ----

    @Test
    fun `un UUID legitimo conserva su nombre para no huerfanar archivos existentes`() {
        val id = UUID.randomUUID().toString()
        assertEquals(id, assetFileStem(id))
    }

    @Test
    fun `ids con salto de directorio o separadores se reemplazan por un hash sin caracteres de ruta`() {
        val hostile = listOf("../../x", "..", "a/b", "a\\b", "/etc/passwd", "x\u0000y", "id con espacio", "")
        for (id in hostile) {
            val stem = assetFileStem(id)
            assertTrue("'$id' -> '$stem' debe ser un nombre plano", stem.all { it.isLetterOrDigit() || it == '_' || it == '-' })
            assertFalse(stem.contains(".."))
            // El archivo resultante queda SIEMPRE dentro de la carpeta destino.
            val resolved = File(root, "$stem.png").canonicalFile
            assertEquals(root.canonicalFile, resolved.parentFile)
        }
    }

    @Test
    fun `el hash es determinista y distingue ids distintos`() {
        assertEquals(assetFileStem("../../x"), assetFileStem("../../x"))
        assertNotEquals(assetFileStem("../../x"), assetFileStem("../../y"))
    }

    @Test
    fun `un id demasiado largo se reemplaza por un hash de largo acotado`() {
        val stem = assetFileStem("a".repeat(5_000))
        assertTrue(stem.length <= 64)
    }

    // ---- safeAudioExtension ----

    @Test
    fun `extensiones validas se conservan en minuscula`() {
        assertEquals("mp3", safeAudioExtension("tema.MP3"))
        assertEquals("flac", safeAudioExtension("a.flac"))
        assertEquals("wav", safeAudioExtension("voz.wav"))
    }

    @Test
    fun `extensiones con separador de ruta o caracteres raros caen al valor por defecto`() {
        assertEquals("m4a", safeAudioExtension("x.a/b"))
        assertEquals("m4a", safeAudioExtension("x./.."))
        assertEquals("m4a", safeAudioExtension("x.m 4"))
        assertEquals("m4a", safeAudioExtension("sin_extension"))
        assertEquals("m4a", safeAudioExtension("x.demasiadolarga"))
    }

    // ---- tope de project.json ----

    @Test
    fun `un project json mas grande que el tope se rechaza durante la extraccion`() {
        val zip = zipWithStreamedEntry("project.json", ZipExtractionLimits.MAX_PROJECT_JSON_BYTES + 1)
        val dest = File(root, "dst").apply { mkdirs() }
        try {
            extractZipEntriesSafely(ZipInputStream(ByteArrayInputStream(zip)), dest)
            fail("Debía lanzar ZipBombSuspectedException")
        } catch (_: ZipBombSuspectedException) {
            // esperado
        }
    }

    @Test
    fun `un project json normal se extrae`() {
        val zip = zipWithStreamedEntry("project.json", 4L * 1024)
        val dest = File(root, "dst").apply { mkdirs() }
        assertTrue(extractZipEntriesSafely(ZipInputStream(ByteArrayInputStream(zip)), dest))
        assertEquals(4L * 1024, File(dest, "project.json").length())
    }

    /** ZIP con una sola entrada de [size] bytes, generada por bloques (nunca se materializa entera en memoria). */
    private fun zipWithStreamedEntry(name: String, size: Long): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            val block = ByteArray(64 * 1024) { 'a'.code.toByte() }
            var remaining = size
            while (remaining > 0) {
                val n = minOf(remaining, block.size.toLong()).toInt()
                zip.write(block, 0, n)
                remaining -= n
            }
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
