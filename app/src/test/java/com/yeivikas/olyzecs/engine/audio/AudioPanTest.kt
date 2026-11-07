package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Balance estéreo por clip: ley de [panGains] y su aplicación al PCM de exportación. */
class AudioPanTest {

    @Test
    fun `centro no atenua ningun canal`() {
        assertEquals(1f to 1f, panGains(0f))
    }

    @Test
    fun `extremos silencian el canal opuesto`() {
        assertEquals(0f to 1f, panGains(1f))
        assertEquals(1f to 0f, panGains(-1f))
    }

    @Test
    fun `valor intermedio atenua linealmente solo el canal opuesto`() {
        assertEquals(0.5f to 1f, panGains(0.5f))
        assertEquals(1f to 0.25f, panGains(-0.75f))
    }

    @Test
    fun `fuera de rango se acota`() {
        assertEquals(0f to 1f, panGains(7f))
        assertEquals(1f to 0f, panGains(-7f))
    }

    @Test
    fun `pan a la derecha deja el canal derecho intacto y apaga el izquierdo`() {
        val pcm = shortArrayOf(1000, 2000, -1000, -2000)
        AudioProcessor.applyBalancePan(pcm, 0, 2, 1f)
        assertArrayEquals(shortArrayOf(0, 2000, 0, -2000), pcm)
    }

    @Test
    fun `solo se procesa el rango pedido - el silencio previo y posterior no se toca`() {
        val pcm = shortArrayOf(100, 100, 100, 100, 100, 100)
        AudioProcessor.applyBalancePan(pcm, 1, 2, -1f) // solo el frame 1: R -> 0
        assertArrayEquals(shortArrayOf(100, 100, 100, 0, 100, 100), pcm)
    }

    @Test
    fun `pan cero no modifica el buffer`() {
        val pcm = shortArrayOf(5, -5, 7, -7)
        AudioProcessor.applyBalancePan(pcm, 0, 2, 0f)
        assertArrayEquals(shortArrayOf(5, -5, 7, -7), pcm)
    }

    @Test
    fun `rango mas alla del buffer se acota sin excepcion`() {
        val pcm = shortArrayOf(10, 10, 10, 10)
        AudioProcessor.applyBalancePan(pcm, -5, 99, 1f)
        assertArrayEquals(shortArrayOf(0, 10, 0, 10), pcm)
    }
}
