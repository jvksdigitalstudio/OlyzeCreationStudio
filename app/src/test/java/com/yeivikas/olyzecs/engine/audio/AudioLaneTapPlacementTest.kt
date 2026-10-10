package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas puras del carril de audio al TOCAR su espacio vacío: qué es "vacío",
 * contra qué se imanta el punto de "+Clip" y dónde termina naciendo el clip
 * (junto con [firstFreeStartMs]). Sin Android: núcleo con primitivos.
 */
class AudioLaneTapPlacementTest {

    // Dos clips: 1.000..3.000 y 6.000..8.000.
    private val clips = listOf(1_000L to 3_000L, 6_000L to 8_000L)

    // ---------- isTimeOnAnyClip ----------

    @Test
    fun `un punto en el hueco entre clips es espacio vacio`() {
        assertFalse(isTimeOnAnyClip(clips, 4_500L))
    }

    @Test
    fun `un punto dentro de un clip no es espacio vacio`() {
        assertTrue(isTimeOnAnyClip(clips, 2_000L))
    }

    @Test
    fun `los bordes del clip cuentan como clip`() {
        assertTrue(isTimeOnAnyClip(clips, 1_000L))
        assertTrue(isTimeOnAnyClip(clips, 3_000L))
    }

    @Test
    fun `antes del primer clip y tras el ultimo es espacio vacio`() {
        assertFalse(isTimeOnAnyClip(clips, 500L))
        assertFalse(isTimeOnAnyClip(clips, 9_000L))
    }

    @Test
    fun `un carril sin clips es todo espacio vacio`() {
        assertFalse(isTimeOnAnyClip(emptyList(), 0L))
    }

    // ---------- audioSnapTargetsMs ----------

    @Test
    fun `los objetivos incluyen inicio, fin del proyecto, cursor y bordes de cada clip`() {
        val targets = audioSnapTargetsMs(clips, projectDurationMs = 10_000L, playheadMs = 4_000L)
        assertEquals(setOf(0L, 10_000L, 4_000L, 1_000L, 3_000L, 6_000L, 8_000L), targets.toSet())
    }

    // ---------- snapNewClipStartMs ----------

    private val targets = audioSnapTargetsMs(clips, projectDurationMs = 10_000L, playheadMs = 4_000L)

    @Test
    fun `un toque cerca del cursor se imanta al cursor`() {
        assertEquals(4_000L, snapNewClipStartMs(4_120L, targets, thresholdMs = 200L, gridMs = 0.0, projectDurationMs = 10_000L))
    }

    @Test
    fun `un toque cerca del borde de un clip se imanta a ese borde`() {
        assertEquals(3_000L, snapNewClipStartMs(3_090L, targets, thresholdMs = 200L, gridMs = 0.0, projectDurationMs = 10_000L))
    }

    @Test
    fun `un toque lejos de todo objetivo conserva el punto exacto`() {
        assertEquals(5_000L, snapNewClipStartMs(5_000L, targets, thresholdMs = 200L, gridMs = 0.0, projectDurationMs = 10_000L))
    }

    @Test
    fun `con rejilla el toque se imanta a la linea mas cercana`() {
        assertEquals(5_000L, snapNewClipStartMs(5_040L, targets, thresholdMs = 100L, gridMs = 500.0, projectDurationMs = 10_000L))
    }

    @Test
    fun `el resultado nunca sale del rango del proyecto`() {
        assertEquals(0L, snapNewClipStartMs(-50L, emptyList(), thresholdMs = 0L, gridMs = 0.0, projectDurationMs = 10_000L))
        assertEquals(10_000L, snapNewClipStartMs(12_000L, emptyList(), thresholdMs = 0L, gridMs = 0.0, projectDurationMs = 10_000L))
    }

    // ---------- punto de "+Clip" + colocación final ----------

    @Test
    fun `un clip que cabe en el hueco nace exactamente donde se toco`() {
        val tapped = snapNewClipStartMs(4_500L, targets, thresholdMs = 100L, gridMs = 0.0, projectDurationMs = 10_000L)
        assertEquals(4_500L, firstFreeStartMs(clips, tapped, lengthMs = 1_000L))
    }

    @Test
    fun `un clip largo que choca con el siguiente se coloca a continuacion de el`() {
        val tapped = snapNewClipStartMs(4_500L, targets, thresholdMs = 100L, gridMs = 0.0, projectDurationMs = 10_000L)
        assertEquals(8_000L, firstFreeStartMs(clips, tapped, lengthMs = 3_000L))
    }
}
