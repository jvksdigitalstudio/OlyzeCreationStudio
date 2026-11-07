package com.yeivikas.olyzecs.engine.audio.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rangos, valores de fábrica, acotado de datos corruptos y catálogo de presets del Reverb. */
class ReverbParamsAndPresetsTest {

    private fun inRanges(p: ReverbParams) =
        p.mix in ReverbRanges.MIX && p.decaySeconds in ReverbRanges.DECAY_SECONDS && p.preDelayMs in ReverbRanges.PRE_DELAY_MS &&
            p.size in ReverbRanges.SIZE && p.dampingHz in ReverbRanges.DAMPING_HZ && p.lowCutHz in ReverbRanges.LOW_CUT_HZ &&
            p.highCutHz in ReverbRanges.HIGH_CUT_HZ && p.diffusion in ReverbRanges.DIFFUSION &&
            p.modulation in ReverbRanges.MODULATION && p.width in ReverbRanges.WIDTH && p.earlyLevel in ReverbRanges.EARLY_LEVEL

    @Test
    fun `los valores de fabrica ya estan dentro de rango y no cambian al acotar`() {
        assertTrue(inRanges(ReverbParams.DEFAULT))
        assertEquals(ReverbParams.DEFAULT, ReverbParams.DEFAULT.coerced())
        assertEquals(ReverbParams(), ReverbParams.DEFAULT)
    }

    @Test
    fun `coerced acota cada valor a su rango`() {
        val wild = ReverbParams(
            mix = 5f, decaySeconds = -3f, preDelayMs = 9_999f, size = 0f, dampingHz = 1f, lowCutHz = 99_999f,
            highCutHz = 0f, diffusion = 2f, modulation = -1f, width = 7f, earlyLevel = 100f
        ).coerced()
        assertTrue(inRanges(wild))
        assertEquals(ReverbRanges.MIX.endInclusive, wild.mix, 0f)
        assertEquals(ReverbRanges.DECAY_SECONDS.start, wild.decaySeconds, 0f)
        assertEquals(ReverbRanges.LOW_CUT_HZ.endInclusive, wild.lowCutHz, 0f)
    }

    @Test
    fun `un NaN o un infinito corruptos no llegan al DSP`() {
        val corrupt = ReverbParams(mix = Float.NaN, decaySeconds = Float.POSITIVE_INFINITY, size = Float.NEGATIVE_INFINITY, width = Float.NaN).coerced()
        assertTrue(inRanges(corrupt))
        assertEquals(ReverbParams.DEFAULT.mix, corrupt.mix, 0f)
        assertEquals(ReverbRanges.DECAY_SECONDS.endInclusive, corrupt.decaySeconds, 0f)
        assertEquals(ReverbRanges.SIZE.start, corrupt.size, 0f)
        assertEquals(ReverbParams.DEFAULT.width, corrupt.width, 0f)
    }

    @Test
    fun `la cola es pre-delay mas decaimiento mas el tramo de reflexiones tempranas`() {
        val p = ReverbParams(preDelayMs = 100f, decaySeconds = 3f)
        assertEquals(0.1 + 3.0 + 0.12, p.tailSeconds(), 1e-9)
        assertTrue(ReverbParams(preDelayMs = 250f, decaySeconds = 15f).tailSeconds() < 16.0)
    }

    @Test
    fun `hay un preset por cada id y los ids no se repiten`() {
        assertEquals(ReverbPresetId.values().toSet(), ReverbPresets.all.map { it.id }.toSet())
        assertEquals(ReverbPresets.all.size, ReverbPresets.all.map { it.id }.distinct().size)
    }

    @Test
    fun `todos los presets estan dentro de rango y suenan distinto entre si`() {
        for (preset in ReverbPresets.all) {
            assertTrue("${preset.id} fuera de rango", inRanges(preset.params))
            assertEquals("${preset.id} cambia al acotar", preset.params, preset.params.coerced())
            assertTrue("${preset.id} no deja pasar nada de reverb", preset.params.mix > 0f)
        }
        assertEquals(ReverbPresets.all.size, ReverbPresets.all.map { it.params }.distinct().size)
    }

    @Test
    fun `los presets ordenan el tamano de la sala de menor a mayor decaimiento`() {
        val decay = ReverbPresets.all.associate { it.id to it.params.decaySeconds }
        assertTrue(decay.getValue(ReverbPresetId.ROOM) < decay.getValue(ReverbPresetId.CHAMBER))
        assertTrue(decay.getValue(ReverbPresetId.HALL) < decay.getValue(ReverbPresetId.CATHEDRAL))
    }

    @Test
    fun `indexOf encuentra el preset exacto y devuelve null si se modifico un valor`() {
        ReverbPresets.all.forEachIndexed { index, preset -> assertEquals(index, ReverbPresets.indexOf(preset.params)) }
        val edited = ReverbPresets.all[2].params.copy(mix = ReverbPresets.all[2].params.mix + 0.01f)
        assertNull(ReverbPresets.indexOf(edited))
        assertNotEquals(ReverbPresets.all[2].params, edited)
    }
}
