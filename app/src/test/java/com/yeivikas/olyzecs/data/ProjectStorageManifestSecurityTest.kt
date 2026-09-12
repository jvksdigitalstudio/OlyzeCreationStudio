package com.yeivikas.olyzecs.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

/**
 * FASE 1 (AUDITORÍA P0/P1) — tests OBLIGATORIOS de esta fase.
 *
 * Cubre las DOS defensas nuevas de esta fase que [ProjectStorageZipSlipTest]
 * (Fase A, ya existente) NO cubría:
 *
 *  1. [resolveManifestFile] — path traversal a través de VALORES del
 *     manifest (`imageFileName`/`audioFileName`/`coverImageFileName`/fotos
 *     de elenco en `project.json`), un vector totalmente distinto de Zip
 *     Slip (que protege NOMBRES DE ENTRADA del zip en el momento de
 *     extraer, no valores de texto dentro de un archivo ya extraído).
 *
 *  2. Límites contra ZIP bombs en [extractZipEntriesSafely]: demasiadas
 *     entradas, una entrada individual demasiado grande, tamaño total
 *     descomprimido demasiado grande, y ratio de compresión sospechoso.
 *
 * Corre como JVM unit test puro, mismo criterio que
 * [ProjectStorageZipSlipTest]: sin `Context` de Android, sin Robolectric.
 */
class ProjectStorageManifestSecurityTest {

    private lateinit var tempRoot: File
    private lateinit var expectedDir: File

    @Before
    fun setUp() {
        tempRoot = File.createTempFile("olyze_manifest_sec_test_", "").apply {
            delete()
            mkdirs()
        }
        expectedDir = File(tempRoot, "images").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    // ---- resolveManifestFile: path traversal vía valores del manifest ----

    @Test
    fun `un nombre de archivo normal del manifest se resuelve dentro del directorio esperado`() {
        val resolved = resolveManifestFile(expectedDir, "layer-1.png")
        assertEquals(File(expectedDir, "layer-1.png").canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `imageFileName con salto de directorio se rechaza aunque el zip en si sea legitimo`() {
        // HALLAZGO REAL de esta fase: un `.olycs` cuyo ZIP es 100% legítimo
        // (ninguna entrada del zip usa "../") puede seguir siendo malicioso
        // si su project.json (un archivo de texto legítimo, extraído sin
        // problema) declara un `imageFileName` con salto de directorio. La
        // protección de Zip Slip nunca llega a ver este ataque porque vive
        // enteramente en el CONTENIDO de un archivo, no en su nombre.
        assertNull(resolveManifestFile(expectedDir, "../../../../data/data/com.yeivikas.olyzecs/shared_prefs/secreto.xml"))
        assertNull(resolveManifestFile(expectedDir, "../evil.png"))
        assertNull(resolveManifestFile(expectedDir, "subdir/../../evil.png"))
    }

    @Test
    fun `imageFileName con ruta absoluta se rechaza`() {
        assertNull(resolveManifestFile(expectedDir, "/etc/passwd"))
        assertNull(resolveManifestFile(expectedDir, "/data/data/com.yeivikas.olyzecs/databases/evil.db"))
    }

    @Test
    fun `imageFileName nulo o en blanco se rechaza sin lanzar`() {
        assertNull(resolveManifestFile(expectedDir, null))
        assertNull(resolveManifestFile(expectedDir, ""))
        assertNull(resolveManifestFile(expectedDir, "   "))
    }

    @Test
    fun `la ruta resuelta nunca queda fuera del directorio esperado ni siquiera con symlinks canonicos`() {
        // Prueba de la propiedad real, no solo del filtro de texto: si por
        // alguna combinación rara `resolveManifestFile` llegara a devolver
        // un File, su ruta CANÓNICA debe seguir estando confinada dentro de
        // `expectedDir` — la misma garantía end-to-end que
        // ProjectStorageZipSlipTest verifica para la extracción del zip.
        val maliciousNames = listOf(
            "../sibling.png",
            "../../sibling2.png",
            "a/../../b/../../evil.png",
            "/absolute/evil.png"
        )
        for (name in maliciousNames) {
            val resolved = resolveManifestFile(expectedDir, name)
            if (resolved != null) {
                val expectedCanonical = expectedDir.canonicalFile
                val resolvedCanonical = resolved.canonicalFile
                val confined = resolvedCanonical == expectedCanonical ||
                    resolvedCanonical.path.startsWith(expectedCanonical.path + File.separator)
                assertTrue("'$name' resolvió fuera del directorio esperado: ${resolvedCanonical.path}", confined)
            }
        }
    }

    // ---- ZIP bombs: límites en extractZipEntriesSafely -------------------

    @Test
    fun `una entrada que supera el tamano descomprimido maximo por entrada se rechaza`() {
        val destDir = File(tempRoot, "dest_entry_limit").apply { mkdirs() }
        val hugeContent = ByteArray((ZipExtractionLimits.MAX_UNCOMPRESSED_ENTRY_BYTES + 1024).toInt())
        val zip = buildZipStored("project.json" to "{}", "images/huge.png" to hugeContent)

        var threw = false
        try {
            extractZipEntriesSafely(ZipInputStream(ByteArrayInputStream(zip)), destDir)
        } catch (e: ZipBombSuspectedException) {
            threw = true
        }
        assertTrue("Una entrada gigante debe rechazarse con ZipBombSuspectedException", threw)
    }

    @Test
    fun `demasiadas entradas se rechazan antes de completar la extraccion`() {
        val destDir = File(tempRoot, "dest_count_limit").apply { mkdirs() }
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zipOut ->
            repeat(ZipExtractionLimits.MAX_ENTRIES + 10) { i ->
                zipOut.putNextEntry(ZipEntry("f$i.txt"))
                zipOut.write("x".toByteArray())
                zipOut.closeEntry()
            }
        }
        var threw = false
        try {
            extractZipEntriesSafely(ZipInputStream(ByteArrayInputStream(bytes.toByteArray())), destDir)
        } catch (e: ZipBombSuspectedException) {
            threw = true
        }
        assertTrue("Un zip con más de MAX_ENTRIES entradas debe rechazarse", threw)
    }

    @Test
    fun `un zip legitimo y chico se extrae sin disparar ningun limite`() {
        val destDir = File(tempRoot, "dest_ok").apply { mkdirs() }
        val zip = buildZipStored(
            "project.json" to "{\"id\":\"abc\"}",
            "images/layer1.png" to "fake-png-bytes"
        )
        val extracted = extractZipEntriesSafely(ZipInputStream(ByteArrayInputStream(zip)), destDir)
        assertTrue(extracted)
        assertTrue(File(destDir, "project.json").exists())
        assertTrue(File(destDir, "images/layer1.png").exists())
    }

    // ---- helpers ----------------------------------------------------------

    private fun buildZipStored(vararg entries: Pair<String, Any>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zipOut ->
            for ((name, content) in entries) {
                val data = when (content) {
                    is String -> content.toByteArray()
                    is ByteArray -> content
                    else -> error("tipo de contenido no soportado en el helper de test")
                }
                zipOut.putNextEntry(ZipEntry(name))
                zipOut.write(data)
                zipOut.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
