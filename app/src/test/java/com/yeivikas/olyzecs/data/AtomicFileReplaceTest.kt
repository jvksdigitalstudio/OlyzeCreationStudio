package com.yeivikas.olyzecs.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Test OBLIGATORIO de la Fase 4.2 — puntos 4.2/4.3 y 25.A/25.B del prompt
 * maestro: verifica [copyToFileAtomically], la pieza de integridad real
 * que ahora comparten [ProjectStorage.ensureLocalImage] y
 * [ProjectStorage.ensureLocalAudio].
 *
 * Corre como JVM unit test puro (sin Robolectric): [copyToFileAtomically]
 * no toca `Context`/`Uri` de Android a propósito, precisamente para que
 * esta propiedad de integridad se pueda verificar sin depender del
 * framework de Android — ver su KDoc en ProjectStorage.kt.
 */
class AtomicFileReplaceTest {

    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        tempRoot = File.createTempFile("atomic-replace-test", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun `copia exitosa reemplaza el destino y no deja temporales`() {
        val destFile = File(tempRoot, "asset.png")
        val ok = copyToFileAtomically(destFile) { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }

        assertTrue(ok)
        assertTrue(destFile.exists())
        assertEquals(4L, destFile.length())
        // FASE 4.2-R4 (Parte 15 del prompt maestro R4 — bug real detectado
        // en este mismo test): la aserción original acá era
        // `assertEquals(0, tempRoot.listFiles()?.size)`, que contradice la
        // aserción de arriba (`destFile.exists()` es `true` — ese archivo
        // SÍ está dentro de `tempRoot`, así que el directorio no puede
        // tener 0 archivos). Confundía "no quedan temporales huérfanos"
        // con "no queda ningún archivo". La verificación correcta es: el
        // directorio tiene EXACTAMENTE un archivo, y es el destino final
        // (nada de temporales `.tmp-*` sobrevivientes).
        val filesInDir = tempRoot.listFiles().orEmpty()
        assertEquals("debe quedar solo el archivo destino, ningún temporal huérfano", 1, filesInDir.size)
        assertEquals(destFile.name, filesInDir.single().name)
    }

    @Test
    fun `si la copia nueva falla, el archivo destino anterior VALIDO permanece intacto`() {
        // Este es el escenario exacto de la AUDITORÍA (4.2/4.3): ya existe
        // una copia local válida (de un guardado anterior) y la copia
        // NUEVA falla a mitad de camino. Antes de esta fase, el archivo
        // destino se truncaba/vaciaba en el momento de abrir el stream de
        // salida, ANTES de saber si la copia nueva iba a tener éxito —
        // así que un fallo transitorio destruía contenido válido.
        val destFile = File(tempRoot, "asset.png")
        destFile.writeBytes(byteArrayOf(9, 9, 9, 9, 9)) // "copia anterior válida"

        val ok = copyToFileAtomically(destFile) {
            throw IOException("fallo simulado de red/SAF a mitad de copia")
        }

        assertFalse(ok)
        assertTrue("el archivo anterior debe seguir existiendo", destFile.exists())
        assertEquals(
            "el contenido anterior NO debe haberse tocado",
            listOf<Byte>(9, 9, 9, 9, 9),
            destFile.readBytes().toList()
        )
        // Ningún temporal parcial debe quedar rondando en el directorio.
        assertEquals(1, tempRoot.listFiles()?.size)
    }

    @Test
    fun `si la copia nueva devuelve un stream vacio, el destino anterior permanece intacto`() {
        val destFile = File(tempRoot, "asset.png")
        destFile.writeBytes(byteArrayOf(7, 7, 7))

        val ok = copyToFileAtomically(destFile) { ByteArrayInputStream(ByteArray(0)) }

        assertFalse(ok)
        assertTrue(destFile.exists())
        assertEquals(listOf<Byte>(7, 7, 7), destFile.readBytes().toList())
        assertEquals(1, tempRoot.listFiles()?.size) // sin temporales huérfanos
    }

    @Test
    fun `si no existia destino previo y la copia falla, no se crea ningun archivo`() {
        val destFile = File(tempRoot, "asset.png")

        val ok = copyToFileAtomically(destFile) { throw IOException("no hay datos") }

        assertFalse(ok)
        assertFalse(destFile.exists())
        assertEquals(0, tempRoot.listFiles()?.size)
    }

    @Test
    fun `openInput que devuelve null vía excepcion explicita tambien preserva el destino anterior`() {
        // Refleja el patrón real de uso en ProjectStorage: cuando
        // `contentResolver.openInputStream(uri)` devuelve `null`, el
        // llamador lo convierte en una IOException explícita ANTES de
        // llegar acá — este test verifica que ese caso se comporta igual
        // que cualquier otro fallo de `openInput`.
        val destFile = File(tempRoot, "audio.m4a")
        destFile.writeBytes(byteArrayOf(5, 5, 5, 5))
        val openInput: () -> InputStream = {
            throw IOException("openInputStream() devolvió null — no hay datos que copiar")
        }

        val ok = copyToFileAtomically(destFile, openInput)

        assertFalse(ok)
        assertEquals(listOf<Byte>(5, 5, 5, 5), destFile.readBytes().toList())
    }

    @Test
    fun `reemplazo exitoso sobre un destino previo cambia el contenido por completo`() {
        val destFile = File(tempRoot, "asset.png")
        destFile.writeBytes(byteArrayOf(1, 1, 1))

        val ok = copyToFileAtomically(destFile) { ByteArrayInputStream(byteArrayOf(2, 2, 2, 2, 2)) }

        assertTrue(ok)
        assertEquals(listOf<Byte>(2, 2, 2, 2, 2), destFile.readBytes().toList())
        assertEquals(1, tempRoot.listFiles()?.size) // el viejo temporal no debe sobrevivir
    }
}
