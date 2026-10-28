package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reglas puras del clip de audio: dónde se ubica un clip recién cargado y
 * qué punto del archivo corresponde a un instante del timeline.
 *
 * Solo primitivos (sin `android.net.Uri`) — mismo criterio que
 * `AudioProcessorFadeTest`: JUnit puro, sin Robolectric.
 */
class AudioClipPlacementAndPositionTest {

    // ---------------- firstFreeStartMs ----------------

    @Test
    fun `carril vacio - nace exactamente en el cabezal`() {
        assertEquals(2_500L, firstFreeStartMs(emptyList(), 2_500L, 1_000L))
    }

    @Test
    fun `cabezal en hueco libre - no se mueve`() {
        val occupied = listOf(0L to 1_000L, 5_000L to 6_000L)
        assertEquals(2_000L, firstFreeStartMs(occupied, 2_000L, 1_000L))
    }

    @Test
    fun `cabezal dentro de un clip - se coloca justo al final de ese clip`() {
        val occupied = listOf(0L to 3_000L)
        assertEquals(3_000L, firstFreeStartMs(occupied, 1_000L, 500L))
    }

    @Test
    fun `no cabe en el hueco - salta al final del clip siguiente`() {
        // Hueco [1000, 1500) mide 500; el clip nuevo mide 800.
        val occupied = listOf(0L to 1_000L, 1_500L to 2_500L)
        assertEquals(2_500L, firstFreeStartMs(occupied, 1_000L, 800L))
    }

    @Test
    fun `clips encadenados - salta toda la cadena`() {
        val occupied = listOf(0L to 1_000L, 1_000L to 2_000L, 2_000L to 3_000L)
        assertEquals(3_000L, firstFreeStartMs(occupied, 0L, 500L))
    }

    @Test
    fun `lista desordenada da el mismo resultado`() {
        val occupied = listOf(2_000L to 3_000L, 0L to 1_000L, 1_000L to 2_000L)
        assertEquals(3_000L, firstFreeStartMs(occupied, 0L, 500L))
    }

    @Test
    fun `clip largo que contiene a otro - no queda dentro`() {
        val occupied = listOf(0L to 5_000L, 1_000L to 2_000L)
        assertEquals(5_000L, firstFreeStartMs(occupied, 0L, 100L))
    }

    @Test
    fun `inicio deseado negativo se lleva a cero`() {
        assertEquals(0L, firstFreeStartMs(emptyList(), -50L, 100L))
    }

    @Test
    fun `borde exacto - pegado al final de otro clip no cuenta como solape`() {
        val occupied = listOf(0L to 1_000L)
        assertEquals(1_000L, firstFreeStartMs(occupied, 1_000L, 500L))
    }

    // ---------------- sourcePositionAtProjectMs ----------------

    @Test
    fun `antes del inicio del clip - posicion = trim`() {
        assertEquals(
            200L,
            sourcePositionAtProjectMs(10_000L, trimStartMs = 200L, loop = true, timelineStartMs = 5_000L, projectTimeMs = 1_000L)
        )
    }

    @Test
    fun `primera pasada - trim mas tiempo transcurrido`() {
        assertEquals(
            1_700L,
            sourcePositionAtProjectMs(10_000L, 200L, true, 5_000L, 6_500L)
        )
    }

    @Test
    fun `con loop - la segunda vuelta arranca desde el frame 0 del archivo y no desde el trim`() {
        // Archivo de 1000 ms, trim 200: primera pasada dura 800 ms.
        // A los 900 ms dentro del clip: raw = 1100 -> (1100-1000) % 1000 = 100.
        assertEquals(
            100L,
            sourcePositionAtProjectMs(1_000L, 200L, true, 0L, 900L)
        )
    }

    @Test
    fun `con loop - varias vueltas`() {
        // raw = 0 + 3500 -> (3500 - 1000) % 1000 = 500
        assertEquals(
            500L,
            sourcePositionAtProjectMs(1_000L, 0L, true, 0L, 3_500L)
        )
    }

    @Test
    fun `sin loop - archivo agotado devuelve null (silencio)`() {
        assertNull(sourcePositionAtProjectMs(1_000L, 0L, false, 0L, 1_000L))
        assertNull(sourcePositionAtProjectMs(1_000L, 0L, false, 0L, 5_000L))
    }

    @Test
    fun `sin loop - ultimo ms todavia suena`() {
        assertEquals(999L, sourcePositionAtProjectMs(1_000L, 0L, false, 0L, 999L))
    }

    @Test
    fun `duracion invalida devuelve null`() {
        assertNull(sourcePositionAtProjectMs(0L, 0L, true, 0L, 100L))
        assertNull(sourcePositionAtProjectMs(-5L, 0L, true, 0L, 100L))
    }
}

/**
 * Unión de clips ("Combine"): inverso exacto de `splitAtTimelineMs`.
 * Solo primitivos — ver nota de la clase anterior.
 */
class AudioClipMergeRuleTest {

    @Test
    fun `dos mitades de una division simple son continuacion exacta`() {
        // Archivo 10 s, clip entero 0..4000 sin trim; division en 1500:
        // A = [0,1500) trim 0 ; B = [1500, 4000) trim 1500.
        assertTrue(isExactContinuation(10_000L, 0L, true, 0L, 1_500L, 1_500L, 1_500L))
    }

    @Test
    fun `con trim inicial la continuacion suma el trim`() {
        // A arranca en trim 2000: tras 1000 ms de clip lee la posicion 3000.
        assertTrue(isExactContinuation(10_000L, 2_000L, true, 500L, 1_000L, 1_500L, 3_000L))
    }

    @Test
    fun `hueco en el timeline no es continuacion`() {
        assertFalse(isExactContinuation(10_000L, 0L, true, 0L, 1_500L, 1_600L, 1_500L))
    }

    @Test
    fun `solape en el timeline no es continuacion`() {
        assertFalse(isExactContinuation(10_000L, 0L, true, 0L, 1_500L, 1_400L, 1_500L))
    }

    @Test
    fun `salto en el archivo no es continuacion`() {
        assertFalse(isExactContinuation(10_000L, 0L, true, 0L, 1_500L, 1_500L, 2_500L))
    }

    @Test
    fun `con loop la continuacion cruza la vuelta del archivo`() {
        // Archivo 1000 ms, trim 200, loop. A dura 1500 ms desde 0:
        // al final lee (200+1500-1000) % 1000 = 700.
        assertTrue(isExactContinuation(1_000L, 200L, true, 0L, 1_500L, 1_500L, 700L))
    }

    @Test
    fun `sin loop y archivo agotado nunca hay continuacion`() {
        assertFalse(isExactContinuation(1_000L, 0L, false, 0L, 1_000L, 1_000L, 0L))
    }

    @Test
    fun `resultado de dividir y volver a unir coincide`() {
        // Reproduce la aritmetica de splitAtTimelineMs para clip con loop:
        // archivo 1000, trim 200, clip 0..2500, corte en offset 1700.
        val sourceDur = 1_000L; val trim = 200L; val offset = 1_700L
        val firstPass = sourceDur - trim                       // 800
        val secondTrim = (offset - firstPass) % sourceDur      // 900
        assertTrue(isExactContinuation(sourceDur, trim, true, 0L, offset, offset, secondTrim))
    }
}
