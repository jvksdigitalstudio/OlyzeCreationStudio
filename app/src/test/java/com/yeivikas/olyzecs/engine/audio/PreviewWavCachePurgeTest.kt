package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * La purga de la caché de WAV sobre una carpeta REAL. Lo que más importa: con dos preparaciones a la vez (carril corto y largo) la purga de
 * una NO puede borrar el temporal ni el intermedio de la otra; antes borraba todo `.tmp` y esa preparación fallaba sin culpa.
 */
class PreviewWavCachePurgeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = 10_000_000_000L
    private val minute = 60_000L

    private fun file(name: String, ageMs: Long): File =
        File(folder.root, name).apply {
            writeText("x")
            setLastModified(now - ageMs)
        }

    private fun purge(maxFiles: Int, keep: Set<String> = emptySet()) = purgeDirectory(folder.root, keep, maxFiles, now)

    @Test
    fun `no toca el temporal ni el intermedio de una preparacion en curso`() {
        val tmp = file("a.wav.tmp", ageMs = 1 * minute)
        val intermediate = file("b.wav$INTERMEDIATE_WAV_SUFFIX", ageMs = 5 * minute)
        purge(maxFiles = 1)
        assertTrue("el temporal reciente es de otra preparación", tmp.exists())
        assertTrue("el intermedio reciente es de otra preparación", intermediate.exists())
    }

    @Test
    fun `borra los temporales e intermedios abandonados`() {
        val tmp = file("a.wav.tmp", ageMs = STALE_TEMP_MS + minute)
        val intermediate = file("b.wav$INTERMEDIATE_WAV_SUFFIX", ageMs = STALE_TEMP_MS + minute)
        purge(maxFiles = 10)
        assertFalse(tmp.exists())
        assertFalse(intermediate.exists())
    }

    @Test
    fun `los intermedios no cuentan como WAV de cache ni salen por antiguedad`() {
        val old = file("viejo.wav", ageMs = 3 * minute)
        val mid = file("medio.wav", ageMs = 2 * minute)
        val recent = file("reciente.wav", ageMs = 1 * minute)
        val intermediate = file("x.wav$INTERMEDIATE_WAV_SUFFIX", ageMs = 4 * minute) // el más antiguo de todos, pero no es de caché
        purge(maxFiles = 2)
        assertFalse("el menos usado sale", old.exists())
        assertTrue(mid.exists())
        assertTrue(recent.exists())
        assertTrue("un intermedio vivo jamás se purga por LRU", intermediate.exists())
    }

    @Test
    fun `nunca borra las fuentes protegidas aunque sean las mas antiguas`() {
        val protectedOld = file("protegida.wav", ageMs = 9 * minute)
        val a = file("a.wav", ageMs = 2 * minute)
        val b = file("b.wav", ageMs = 1 * minute)
        purge(maxFiles = 1, keep = setOf("protegida.wav"))
        assertTrue(protectedOld.exists())
        assertFalse(a.exists())
        assertFalse(b.exists())
    }

    @Test
    fun `no borra nada mientras no se supere el maximo`() {
        val a = file("a.wav", ageMs = 9 * minute)
        val b = file("b.wav", ageMs = 1 * minute)
        purge(maxFiles = 2)
        assertTrue(a.exists())
        assertTrue(b.exists())
    }

    @Test
    fun `una carpeta inexistente no lanza`() {
        purgeDirectory(File(folder.root, "no-existe"), emptySet(), 1, now)
    }
}
