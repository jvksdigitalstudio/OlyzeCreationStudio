package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reglas puras de mover un clip: tope de inicio e imán al soltar. `AudioClip` lleva un
 * `android.net.Uri` (no existe en JUnit puro), así que se prueban los núcleos con primitivos y el
 * envoltorio de carril con carriles SIN clips, como [AudioTrackListTest].
 */
class AudioClipMoveTest {

    // ---------- maxClipStartMs ----------

    @Test
    fun `el inicio maximo deja el clip entero dentro del proyecto`() {
        assertEquals(7_000L, maxClipStartMs(clipLengthMs = 3_000L, projectDurationMs = 10_000L))
    }

    @Test
    fun `un clip mas largo que el proyecto solo puede empezar en 0`() {
        assertEquals(0L, maxClipStartMs(clipLengthMs = 12_000L, projectDurationMs = 10_000L))
    }

    // ---------- clipDropStartMs (núcleo) ----------

    private fun drop(
        raw: Long,
        length: Long = 1_000L,
        duration: Long = 10_000L,
        targets: List<Long> = emptyList(),
        threshold: Long = 50L,
        grid: Double = 0.0
    ) = clipDropStartMs(raw, length, duration, targets, threshold, grid)

    @Test
    fun `sin imanes el inicio pedido se respeta`() {
        assertEquals(4_321L, drop(raw = 4_321L))
    }

    @Test
    fun `un inicio negativo se acota a 0`() {
        assertEquals(0L, drop(raw = -800L))
    }

    @Test
    fun `un inicio pasado el final se acota para que el clip quepa entero`() {
        assertEquals(9_000L, drop(raw = 9_800L))
    }

    @Test
    fun `el borde izquierdo se pega al objetivo cercano`() {
        assertEquals(2_000L, drop(raw = 2_030L, targets = listOf(2_000L)))
    }

    @Test
    fun `el borde derecho tambien se pega a un objetivo cercano`() {
        // Borde derecho = 4_970 + 1_000 = 5_970, a 30 ms del objetivo 6_000.
        assertEquals(5_000L, drop(raw = 4_970L, targets = listOf(6_000L)))
    }

    @Test
    fun `fuera del umbral del iman no se mueve`() {
        assertEquals(2_200L, drop(raw = 2_200L, targets = listOf(2_000L)))
    }

    @Test
    fun `se pega a la linea de la rejilla`() {
        // Rejilla de 500 ms: 3_020 está a 20 ms de la línea 3_000.
        assertEquals(3_000L, drop(raw = 3_020L, grid = 500.0))
    }

    @Test
    fun `el iman nunca saca el clip del proyecto`() {
        // Objetivo en 9_990: pegar el borde derecho (10_000) deja el inicio en 9_000, dentro del tope.
        assertEquals(9_000L, drop(raw = 8_990L, targets = listOf(10_000L)))
    }

    // ---------- AudioTrack.clipDropStartMs ----------

    @Test
    fun `el envoltorio de carril usa la rejilla del carril`() {
        val track = AudioTrack(id = "a", gridBpm = 120f, gridStep = AudioGridStep.BEAT) // 1 pulso = 500 ms
        val start = track.clipDropStartMs(
            clipId = "c", clipLengthMs = 1_000L, rawStartMs = 1_980L,
            projectDurationMs = 10_000L, playheadMs = 7_777L, snapThresholdMs = 50L
        )
        assertEquals(2_000L, start)
    }

    @Test
    fun `el envoltorio de carril se pega al cursor`() {
        val track = AudioTrack(id = "a")
        val start = track.clipDropStartMs(
            clipId = "c", clipLengthMs = 1_000L, rawStartMs = 4_040L,
            projectDurationMs = 10_000L, playheadMs = 4_000L, snapThresholdMs = 50L
        )
        assertEquals(4_000L, start)
    }
}
