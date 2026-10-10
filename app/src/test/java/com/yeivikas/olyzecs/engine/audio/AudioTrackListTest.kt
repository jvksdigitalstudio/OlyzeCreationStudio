package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas puras de la lista de carriles de audio. Los carriles se arman SIN
 * clips (`AudioClip` lleva un `android.net.Uri`, que no existe en JUnit puro):
 * estas reglas solo dependen de los ids y del orden.
 */
class AudioTrackListTest {

    private fun track(id: String, order: Float = 0f) = AudioTrack(id = id, trackOrder = order)

    // ---------- nextAudioTrackOrder ----------

    @Test
    fun `sin capas ni carriles el primer carril de audio recibe -1`() {
        assertEquals(-1f, nextAudioTrackOrder(emptyList()), 0f)
    }

    @Test
    fun `un carril nuevo queda por debajo de la capa mas baja`() {
        assertEquals(-3f, nextAudioTrackOrder(listOf(5f, -2f, 0f)), 0f)
    }

    @Test
    fun `un carril nuevo queda tambien por debajo de los carriles de audio existentes`() {
        val orders = listOf(3f, 1f) + listOf(track("a", order = -4f)).map { it.trackOrder }
        assertEquals(-5f, nextAudioTrackOrder(orders), 0f)
    }

    // ---------- búsqueda por id ----------

    @Test
    fun `track devuelve el carril con ese id`() {
        val tracks = listOf(track("a"), track("b"))
        assertSame(tracks[1], tracks.track("b"))
    }

    @Test
    fun `track devuelve null si el id no existe`() {
        assertNull(listOf(track("a")).track("zzz"))
    }

    // ---------- withTrack ----------

    @Test
    fun `withTrack con un carril sin clips quita ese carril`() {
        val tracks = listOf(track("a"), track("b"))
        assertEquals(listOf("a"), tracks.withTrack(track("b")).map { it.id })
    }

    @Test
    fun `withTrack con un carril sin clips y de id desconocido no cambia nada`() {
        val tracks = listOf(track("a"))
        assertEquals(tracks, tracks.withTrack(track("nuevo")))
    }

    // ---------- endMs ----------

    @Test
    fun `sin clips el final del audio es cero`() {
        assertEquals(0L, listOf(track("a"), track("b")).endMs())
        assertEquals(0L, emptyList<AudioTrack>().endMs())
    }

    // ---------- withUniqueIds ----------

    @Test
    fun `ids ya unicos quedan intactos`() {
        val tracks = listOf(track("a"), track("b"))
        assertEquals(tracks, tracks.withUniqueIds())
    }

    @Test
    fun `un id repetido recibe uno nuevo y el primero se conserva`() {
        val fixed = listOf(track("a", order = 1f), track("a", order = 2f)).withUniqueIds()
        assertEquals("a", fixed[0].id)
        assertNotEquals("a", fixed[1].id)
        assertEquals(2f, fixed[1].trackOrder, 0f)
        assertEquals(2, fixed.map { it.id }.toSet().size)
    }

    @Test
    fun `los ids nuevos tampoco colisionan entre si`() {
        val fixed = listOf(track("a"), track("a"), track("a")).withUniqueIds()
        assertTrue(fixed.map { it.id }.toSet().size == 3)
    }
}
