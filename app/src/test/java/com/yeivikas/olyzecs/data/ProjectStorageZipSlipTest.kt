package com.yeivikas.olyzecs.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Test OBLIGATORIO de la Fase A: verifica la protección Zip Slip de
 * [ProjectStorage.importProjectZip] de punta a punta, contra el núcleo real
 * de extracción ([extractZipEntriesSafely]/[isSafeZipEntryName]) — no una
 * reimplementación de la regla, sino EL MISMO código que usa producción.
 *
 * No alcanza con comprobar que se "rechaza" la entrada maliciosa: se
 * verifica la propiedad de seguridad real — que, tras extraer un ZIP
 * armado a mano con entradas maliciosas, NINGÚN archivo aparece fuera del
 * directorio destino, en ningún lugar del sistema de archivos temporal de
 * este test.
 *
 * Corre como JVM unit test puro: [extractZipEntriesSafely] no usa
 * `Context` de Android, así que no hace falta Robolectric ni un
 * dispositivo/emulador.
 */
class ProjectStorageZipSlipTest {

    private lateinit var tempRoot: File
    private lateinit var destDir: File

    @Before
    fun setUp() {
        tempRoot = createTempTestDir()
        destDir = File(tempRoot, "project_dest").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    // ---- isSafeZipEntryName: la regla en aislamiento -------------------

    @Test
    fun `nombres con doble punto se consideran inseguros`() {
        assertFalse(isSafeZipEntryName("../evil.txt"))
        assertFalse(isSafeZipEntryName("../../evil.txt"))
        assertFalse(isSafeZipEntryName("subdir/../../evil.txt"))
        assertFalse(isSafeZipEntryName("a/b/../../../evil.txt"))
        assertFalse(isSafeZipEntryName("..\\evil.txt")) // separador estilo Windows
    }

    @Test
    fun `nombres con ruta absoluta se consideran inseguros`() {
        // HALLAZGO DE ESTA AUDITORÍA: esta variante NO contiene ".." en
        // ningún lado, así que la regla original (solo contains("..")) la
        // dejaba pasar como "segura". El riesgo real: `File(destDir, child)`
        // en Java/Android, cuando `child` YA es una ruta absoluta, ignora
        // `destDir` por completo y resuelve directo a esa ruta absoluta.
        assertFalse(isSafeZipEntryName("/data/data/com.yeivikas.olyzecs/evil.txt"))
        assertFalse(isSafeZipEntryName("/etc/evil.txt"))
        // Mismo caso con separador estilo Windows, que la función normaliza
        // a "/" antes de evaluar:
        assertFalse(isSafeZipEntryName("\\evil.txt"))
    }

    @Test
    fun `nombres normales dentro del proyecto se consideran seguros`() {
        assertTrue(isSafeZipEntryName("project.json"))
        assertTrue(isSafeZipEntryName("images/layer1.png"))
        assertTrue(isSafeZipEntryName("audio/track.m4a"))
        // Un nombre de archivo que simplemente CONTIENE dos puntos sin ser
        // un salto de directorio (p. ej. "im..age.png") también cae del
        // lado conservador y se rechaza — comportamiento real de la regla
        // actual (contains(".."), no un parser de rutas), documentado acá
        // a propósito para que quede visible si el día de mañana se
        // decide relajar la regla.
        assertFalse(isSafeZipEntryName("im..age.png"))
    }

    // ---- extractZipEntriesSafely: la propiedad de seguridad end-to-end ----

    @Test
    fun `un zip malicioso con salto de directorio no escribe ningun archivo fuera del destino`() {
        val maliciousZip = buildZip(
            "project.json" to "{\"id\":\"abc\"}",
            "../evil.txt" to "PWNED",
            "../../evil2.txt" to "PWNED2",
            "subdir/../../evil3.txt" to "PWNED3"
        )

        val extracted = extractZipEntriesSafely(ZipInputStream(ByteArrayInputStream(maliciousZip)), destDir)

        assertTrue("Debe extraer al menos la entrada legitima", extracted)
        // La entrada legítima SÍ se extrajo, dentro del destino:
        assertTrue(File(destDir, "project.json").exists())
        // Ninguna entrada maliciosa debe existir en ningún lado dentro del
        // árbol de test, ni dentro de destDir ni afuera (en tempRoot):
        assertNoFileNamed(tempRoot, "evil.txt")
        assertNoFileNamed(tempRoot, "evil2.txt")
        assertNoFileNamed(tempRoot, "evil3.txt")
        // Y en particular, no aparecio nada por fuera de destDir dentro de tempRoot:
        val filesOutsideDest = tempRoot.walkTopDown()
            .filter { it.isFile && !it.path.startsWith(destDir.path) }
            .toList()
        assertTrue(
            "No debe haber archivos escritos fuera del directorio destino: $filesOutsideDest",
            filesOutsideDest.isEmpty()
        )
    }

    @Test
    fun `un zip malicioso con entrada de ruta absoluta no escribe fuera del destino`() {
        // Reproduce en un JVM unit test puro la variante de Zip Slip que
        // esta auditoría encontró sin cobertura: una entrada de ruta
        // absoluta, creada apuntando DENTRO del propio árbol temporal del
        // test (no a una ruta real del sistema, para no tocar disco fuera
        // del sandbox de test) — lo que importa es probar que
        // `extractZipEntriesSafely` nunca escribe ahí, sin importar si la
        // ruta absoluta en cuestión existe o no en la máquina que corre el
        // test.
        val outsideTarget = File(tempRoot, "outside_absolute_evil.txt")
        val maliciousZip = buildZip(
            "project.json" to "{\"id\":\"abc\"}",
            outsideTarget.absolutePath to "PWNED_ABSOLUTE"
        )

        val extracted = extractZipEntriesSafely(ZipInputStream(ByteArrayInputStream(maliciousZip)), destDir)

        assertTrue("Debe extraer al menos la entrada legitima", extracted)
        assertTrue(File(destDir, "project.json").exists())
        assertFalse(
            "La entrada de ruta absoluta NO debe haberse escrito en su destino real",
            outsideTarget.exists()
        )
        val filesOutsideDest = tempRoot.walkTopDown()
            .filter { it.isFile && !it.path.startsWith(destDir.path) }
            .toList()
        assertTrue(
            "No debe haber archivos escritos fuera del directorio destino: $filesOutsideDest",
            filesOutsideDest.isEmpty()
        )
    }

    @Test
    fun `un zip 100 por ciento malicioso no extrae ningun archivo`() {
        val maliciousZip = buildZip(
            "../evil.txt" to "PWNED",
            "../../evil2.txt" to "PWNED2"
        )
        val extracted = extractZipEntriesSafely(ZipInputStream(ByteArrayInputStream(maliciousZip)), destDir)
        assertFalse("Ningun archivo legitimo -> extracted debe ser false", extracted)
        assertTrue(destDir.listFiles()?.isEmpty() != false)
    }

    @Test
    fun `un zip legitimo con subcarpetas se extrae completo dentro del destino`() {
        val zip = buildZip(
            "project.json" to "{}",
            "images/layer1.png" to "fake-png-bytes",
            "audio/track.m4a" to "fake-audio-bytes"
        )
        val extracted = extractZipEntriesSafely(ZipInputStream(ByteArrayInputStream(zip)), destDir)
        assertTrue(extracted)
        assertEquals("{}", File(destDir, "project.json").readText())
        assertEquals("fake-png-bytes", File(destDir, "images/layer1.png").readText())
        assertEquals("fake-audio-bytes", File(destDir, "audio/track.m4a").readText())
    }

    // ---- helpers --------------------------------------------------------

    private fun buildZip(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zipOut ->
            for ((name, content) in entries) {
                zipOut.putNextEntry(ZipEntry(name))
                zipOut.write(content.toByteArray())
                zipOut.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun assertNoFileNamed(root: File, fileName: String) {
        val found = root.walkTopDown().any { it.isFile && it.name == fileName }
        assertFalse("No deberia existir ningun archivo llamado '$fileName' bajo $root", found)
    }

    private fun createTempTestDir(): File {
        val dir = File.createTempFile("olyze_zipslip_test_", "").apply {
            delete()
            mkdirs()
        }
        return dir
    }
}
