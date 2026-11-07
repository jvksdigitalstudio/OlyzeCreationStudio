package com.yeivikas.olyzecs.engine.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Regresión de "importar audio en el cabezal": pasar a segundo plano (p. ej.
 * al abrir el selector de archivos) debe PAUSAR sin mover el cabezal; solo
 * entrar/salir del editor rebobina. JUnit puro, sin Android.
 */
class PlaybackTransitionsTest {

    private val playing = PlaybackSnapshot(
        playheadMs = 79_000L, isPlaying = true, isRecording = true, isCapturing = true
    )

    @Test
    fun `pausar conserva el cabezal`() {
        assertEquals(79_000L, playing.paused().playheadMs)
    }

    @Test
    fun `pausar apaga reproduccion grabacion y captura`() {
        val p = playing.paused()
        assertFalse(p.isPlaying)
        assertFalse(p.isRecording)
        assertFalse(p.isCapturing)
    }

    @Test
    fun `pausar con cabezal detenido en mitad del timeline no lo rebobina`() {
        val idle = PlaybackSnapshot(79_000L, isPlaying = false, isRecording = false, isCapturing = false)
        assertEquals(idle, idle.paused())
    }

    @Test
    fun `pausar es idempotente`() {
        assertEquals(playing.paused(), playing.paused().paused())
    }

    @Test
    fun `rebobinar pausa y lleva el cabezal a cero`() {
        val r = playing.rewound()
        assertEquals(0L, r.playheadMs)
        assertFalse(r.isPlaying)
        assertFalse(r.isRecording)
        assertFalse(r.isCapturing)
    }

    @Test
    fun `rebobinar un estado ya en cero no cambia nada`() {
        val zero = PlaybackSnapshot(0L, false, false, false)
        assertEquals(zero, zero.rewound())
    }

    @Test
    fun `el cabezal tras pausar es el que importAudio usa como inicio del clip`() {
        // Flujo real: cabezal en 1:19 -> "+" Audio -> la Activity va a
        // ON_STOP (pausa) -> el picker devuelve el archivo -> importAudio
        // lee el cabezal y lo pasa a firstFreeStartMs. Carril con un clip
        // anterior que termina antes del cabezal.
        val headAfterStop = playing.paused().playheadMs
        val occupied = listOf(0L to 40_000L)
        assertEquals(79_000L, com.yeivikas.olyzecs.engine.audio.firstFreeStartMs(occupied, headAfterStop, 10_000L))
    }
}
