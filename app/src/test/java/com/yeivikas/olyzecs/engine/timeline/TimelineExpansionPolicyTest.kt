package com.yeivikas.olyzecs.engine.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la política PURA de expansión del timeline (Fase A —
 * ProjectStorage/TimelineDurationManager/SpeedRampEngine). Todos los
 * valores esperados están calculados a partir de las constantes reales de
 * [TimelineLimits], no inventados:
 *
 *   INITIAL_DURATION_MS = 60_000
 *   TIER_1_CEILING_MS   = 600_000   (10 min)
 *   TIER_2_CEILING_MS   = 3_600_000 (60 min)
 *   MAX_DURATION_MS     = 10_800_000 (180 min)
 *   STEP_TIER_1_MS      = 60_000   (1 min)
 *   STEP_TIER_2_MS      = 300_000  (5 min)
 *   STEP_TIER_3_MS      = 600_000  (10 min)
 *   EXPANSION_TRIGGER_WINDOW_MS = 3_000
 */
class TimelineExpansionPolicyTest {

    @Test
    fun `stepFor devuelve el paso del tramo 1 por debajo del techo de 10 minutos`() {
        assertEquals(TimelineLimits.STEP_TIER_1_MS, TimelineExpansionPolicy.stepFor(0L))
        assertEquals(TimelineLimits.STEP_TIER_1_MS, TimelineExpansionPolicy.stepFor(TimelineLimits.TIER_1_CEILING_MS - 1))
    }

    @Test
    fun `stepFor devuelve el paso del tramo 2 justo en el techo de 10 minutos y por debajo de 60`() {
        assertEquals(TimelineLimits.STEP_TIER_2_MS, TimelineExpansionPolicy.stepFor(TimelineLimits.TIER_1_CEILING_MS))
        assertEquals(TimelineLimits.STEP_TIER_2_MS, TimelineExpansionPolicy.stepFor(TimelineLimits.TIER_2_CEILING_MS - 1))
    }

    @Test
    fun `stepFor devuelve el paso del tramo 3 en y por encima del techo de 60 minutos`() {
        assertEquals(TimelineLimits.STEP_TIER_3_MS, TimelineExpansionPolicy.stepFor(TimelineLimits.TIER_2_CEILING_MS))
        assertEquals(TimelineLimits.STEP_TIER_3_MS, TimelineExpansionPolicy.stepFor(TimelineLimits.MAX_DURATION_MS))
    }

    @Test
    fun `nextDuration suma el paso correcto de cada tramo`() {
        assertEquals(120_000L, TimelineExpansionPolicy.nextDuration(60_000L)) // tramo 1: +1 min
        assertEquals(900_000L, TimelineExpansionPolicy.nextDuration(600_000L)) // tramo 2: +5 min
    }

    @Test
    fun `nextDuration nunca supera el techo maximo aunque el paso se pase`() {
        // 10_700_000 + 600_000 = 11_300_000, por encima del techo -> debe recortar a MAX
        val result = TimelineExpansionPolicy.nextDuration(10_700_000L)
        assertEquals(TimelineLimits.MAX_DURATION_MS, result)
    }

    @Test
    fun `nextDuration en el techo devuelve el mismo techo sin seguir creciendo`() {
        assertEquals(TimelineLimits.MAX_DURATION_MS, TimelineExpansionPolicy.nextDuration(TimelineLimits.MAX_DURATION_MS))
    }

    @Test
    fun `isAtMax es true exactamente en el techo y por encima, false por debajo`() {
        assertFalse(TimelineExpansionPolicy.isAtMax(TimelineLimits.MAX_DURATION_MS - 1))
        assertTrue(TimelineExpansionPolicy.isAtMax(TimelineLimits.MAX_DURATION_MS))
        assertTrue(TimelineExpansionPolicy.isAtMax(TimelineLimits.MAX_DURATION_MS + 1))
    }

    @Test
    fun `isApproachingEnd es true dentro de la ventana de disparo de 3 segundos`() {
        val current = 60_000L
        assertTrue(TimelineExpansionPolicy.isApproachingEnd(playheadMs = current - 3_000L, currentDurationMs = current))
        assertFalse(TimelineExpansionPolicy.isApproachingEnd(playheadMs = current - 3_001L, currentDurationMs = current))
    }

    @Test
    fun `isApproachingEnd es false si ya se esta en el techo maximo`() {
        val max = TimelineLimits.MAX_DURATION_MS
        assertFalse(TimelineExpansionPolicy.isApproachingEnd(playheadMs = max - 1_000L, currentDurationMs = max))
    }

    @Test
    fun `expandToFit da un solo salto cuando el objetivo ya queda fuera de la ventana tras un tramo`() {
        // 60_000 -> nextDuration = 120_000; distancia a 65_000 pasa a ser 55_000, > ventana(3_000) -> se detiene ahi
        val result = TimelineExpansionPolicy.expandToFit(currentDurationMs = 60_000L, targetMs = 65_000L)
        assertEquals(120_000L, result)
    }

    @Test
    fun `expandToFit se detiene en el techo maximo sin loop infinito para un objetivo enorme`() {
        val result = TimelineExpansionPolicy.expandToFit(currentDurationMs = 10_700_000L, targetMs = 999_999_999L)
        assertEquals(TimelineLimits.MAX_DURATION_MS, result)
    }

    @Test
    fun `expandToFit nunca reduce la duracion, solo la mantiene o la crece`() {
        val current = 600_000L
        val result = TimelineExpansionPolicy.expandToFit(currentDurationMs = current, targetMs = 0L)
        assertTrue(result >= current)
    }
}
