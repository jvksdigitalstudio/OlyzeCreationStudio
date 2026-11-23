package com.yeivikas.olyzecs.engine.effects.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectPassPlanTest {

    @Test
    fun `parametros neutros dan un plan vacio`() {
        assertTrue(EffectPassPlanner.plan(LayerEffectParams.NEUTRAL).isEmpty)
        assertEquals(EffectPassPlan.EMPTY, EffectPassPlanner.plan(LayerEffectParams()))
    }

    @Test
    fun `un cambio de color da un unico pase de color`() {
        val plan = EffectPassPlanner.plan(LayerEffectParams(ColorBasicParams(contrast = 1.4f)))
        assertEquals(listOf(EffectPassKind.COLOR_BASIC), plan.passes)
    }

    @Test
    fun `un cambio por debajo de la tolerancia no genera ningun pase`() {
        assertTrue(EffectPassPlanner.plan(LayerEffectParams(ColorBasicParams(brightness = 0.0004f))).isEmpty)
    }

    @Test
    fun `datos corruptos se sanean antes de planificar y no producen pases`() {
        val corrupt = LayerEffectParams(ColorBasicParams(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN))
        assertTrue(EffectPassPlanner.plan(corrupt).isEmpty)
    }

    @Test
    fun `el pase identidad nunca sale de los parametros`() {
        val outputs = listOf(
            LayerEffectParams.NEUTRAL,
            LayerEffectParams(ColorBasicParams(brightness = 1f, contrast = 2f, saturation = 0f, hue = 120f, sharpen = 1f))
        ).flatMap { EffectPassPlanner.plan(it).passes }
        assertTrue(EffectPassKind.IDENTITY !in outputs)
    }

    @Test
    fun `el plan solo identidad existe para el autotest`() {
        assertEquals(listOf(EffectPassKind.IDENTITY), EffectPassPlan.IDENTITY_ONLY.passes)
    }
}
