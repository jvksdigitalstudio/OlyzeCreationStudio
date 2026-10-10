package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de los tiradores de LOOP del clip de audio (estirar por los dos
 * bordes, solo con loop activo). Solo primitivos (sin `android.net.Uri`):
 * JUnit puro, mismo criterio que `AudioClipPlacementAndPositionTest`.
 */
class AudioClipLoopEdgesTest {

    private val d = 4_000L // duración del archivo fuente

    // ---------------- sin loop: el clip queda en su largo original ----------------

    @Test
    fun `sin loop - el borde derecho no cambia el largo`() {
        assertEquals(
            4_000L,
            clipLengthAfterRightEdgeMs(loop = false, clipLengthMs = 4_000L, timelineStartMs = 0L, projectDurationMs = 60_000L, requestedMs = 9_000L)
        )
        assertEquals(
            4_000L,
            clipLengthAfterRightEdgeMs(loop = false, clipLengthMs = 4_000L, timelineStartMs = 0L, projectDurationMs = 60_000L, requestedMs = 500L)
        )
    }

    @Test
    fun `sin loop - el borde izquierdo no se mueve`() {
        assertEquals(0L..0L, leftEdgeDeltaRangeMs(loop = false, sourceDurationMs = d, timelineStartMs = 10_000L, clipLengthMs = 4_000L))
        val m = moveLeftEdge(false, d, 0L, 10_000L, 4_000L, deltaMs = -1_000L)
        assertEquals(LeftEdgeMove(10_000L, 0L, 4_000L), m)
    }

    @Test
    fun `loop con archivo de duracion invalida - el borde izquierdo no se mueve`() {
        assertEquals(0L..0L, leftEdgeDeltaRangeMs(loop = true, sourceDurationMs = 0L, timelineStartMs = 10_000L, clipLengthMs = 4_000L))
    }

    // ---------------- con loop: borde derecho ----------------

    @Test
    fun `loop - el borde derecho se acota entre el minimo y el final del proyecto`() {
        fun len(req: Long) = clipLengthAfterRightEdgeMs(true, 4_000L, 10_000L, 20_000L, req)
        assertEquals(MIN_AUDIO_CLIP_LENGTH_MS, len(10L))
        assertEquals(8_000L, len(8_000L))
        assertEquals(10_000L, len(99_000L)) // 20_000 - 10_000
    }

    // ---------------- con loop: borde izquierdo ----------------

    @Test
    fun `loop - rango del borde izquierdo - hasta el inicio del proyecto y hasta el minimo`() {
        val r = leftEdgeDeltaRangeMs(true, d, timelineStartMs = 2_500L, clipLengthMs = 4_000L)
        assertEquals(-2_500L, r.first)
        assertEquals(4_000L - MIN_AUDIO_CLIP_LENGTH_MS, r.last)
    }

    @Test
    fun `loop - estirar a la izquierda mueve la fase y agrega la vuelta anterior`() {
        val m = moveLeftEdge(true, d, trimStartMs = 0L, timelineStartMs = 10_000L, clipLengthMs = 4_000L, deltaMs = -1_000L)
        assertEquals(LeftEdgeMove(9_000L, 3_000L, 5_000L), m)
    }

    @Test
    fun `loop - acortar por la izquierda descarta el comienzo`() {
        val m = moveLeftEdge(true, d, 0L, 10_000L, 4_000L, deltaMs = 1_500L)
        assertEquals(LeftEdgeMove(11_500L, 1_500L, 2_500L), m)
    }

    @Test
    fun `loop - estirar mas de una vuelta envuelve la fase`() {
        val m = moveLeftEdge(true, d, 0L, 20_000L, 4_000L, deltaMs = -9_000L)
        assertEquals(LeftEdgeMove(11_000L, 3_000L, 13_000L), m) // -9000 mod 4000 = 3000
    }

    @Test
    fun `loop - estirar se acota al inicio del proyecto`() {
        val m = moveLeftEdge(true, d, 0L, timelineStartMs = 500L, clipLengthMs = 4_000L, deltaMs = -5_000L)
        assertEquals(0L, m.timelineStartMs)
        assertEquals(4_500L, m.clipLengthMs)
    }

    @Test
    fun `loop - acortar nunca deja menos del minimo`() {
        val m = moveLeftEdge(true, d, 0L, 0L, 4_000L, deltaMs = 99_000L)
        assertEquals(MIN_AUDIO_CLIP_LENGTH_MS, m.clipLengthMs)
    }

    /**
     * PROPIEDAD CLAVE: al mover el borde izquierdo el audio NO se corre de
     * lugar — en todo instante que el clip ya cubría suena el mismo punto del
     * archivo que antes (misma cuenta que preview y export).
     */
    @Test
    fun `mover el borde izquierdo no corre el audio de lugar`() {
        val cases = listOf(
            // trim, start, length, delta
            listOf(0L, 10_000L, 4_000L, -1_000L),
            listOf(0L, 10_000L, 9_500L, -9_000L),
            listOf(1_000L, 5_000L, 6_000L, -2_500L),
            listOf(1_000L, 5_000L, 6_000L, 2_500L),
            listOf(3_900L, 8_000L, 12_000L, -7_777L),
            listOf(0L, 10_000L, 4_000L, 3_999L)
        )
        for ((trim, start, length, delta) in cases) {
            val m = moveLeftEdge(true, d, trim, start, length, delta)
            val from = maxOf(start, m.timelineStartMs)
            val to = minOf(start + length, m.timelineStartMs + m.clipLengthMs)
            var t = from
            while (t < to) {
                val before = sourcePositionAtProjectMs(d, trim, true, start, t)
                val after = sourcePositionAtProjectMs(d, m.trimStartMs, true, m.timelineStartMs, t)
                assertEquals("caso $trim/$start/$length/$delta en t=$t", before, after)
                t += 137
            }
            assertTrue("la fase siempre en [0, D)", m.trimStartMs in 0 until d)
            assertEquals("el borde derecho no se mueve", start + length, m.timelineStartMs + m.clipLengthMs)
        }
    }

    @Test
    fun `sin loop la posicion fuera del archivo es silencio`() {
        // Contraste: sin loop, pasado el final del archivo no hay audio.
        assertNull(sourcePositionAtProjectMs(d, 0L, false, 0L, 5_000L))
    }
}
