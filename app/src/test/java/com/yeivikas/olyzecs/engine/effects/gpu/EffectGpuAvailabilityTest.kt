package com.yeivikas.olyzecs.engine.effects.gpu

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectGpuAvailabilityTest {

    private val colorPlan = EffectPassPlan(listOf(EffectPassKind.COLOR_BASIC))

    private fun caps(
        implemented: Set<EffectPassKind> = setOf(EffectPassKind.COLOR_BASIC),
        enabled: Set<EffectPassKind> = setOf(EffectPassKind.COLOR_BASIC),
        failed: Set<EffectPassKind> = emptySet(),
        fbo: Boolean = true,
        highp: Boolean = true
    ) = GpuEffectCapabilities(implemented, enabled, failed, fbo, highp)

    @Test
    fun `un plan vacio siempre es GPU aunque no haya ninguna capacidad`() {
        val nothing = caps(emptySet(), emptySet(), emptySet(), fbo = false, highp = false)
        assertEquals(EffectGpuDecision.UseGpu, EffectGpuAvailability.decide(EffectPassPlan.EMPTY, nothing))
    }

    @Test
    fun `con todo disponible se usa la GPU`() {
        assertEquals(EffectGpuDecision.UseGpu, EffectGpuAvailability.decide(colorPlan, caps()))
    }

    @Test
    fun `un pase sin shader cae a CPU`() {
        assertEquals(
            EffectGpuDecision.UseCpuFallback(EffectFallbackReason.PASS_NOT_IMPLEMENTED, EffectPassKind.COLOR_BASIC),
            EffectGpuAvailability.decide(colorPlan, caps(implemented = emptySet()))
        )
    }

    @Test
    fun `un shader que no compilo cae a CPU`() {
        assertEquals(
            EffectGpuDecision.UseCpuFallback(EffectFallbackReason.SHADER_COMPILE_FAILED, EffectPassKind.COLOR_BASIC),
            EffectGpuAvailability.decide(colorPlan, caps(failed = setOf(EffectPassKind.COLOR_BASIC)))
        )
    }

    @Test
    fun `un pase desactivado por su indicador cae a CPU`() {
        assertEquals(
            EffectGpuDecision.UseCpuFallback(EffectFallbackReason.FEATURE_DISABLED, EffectPassKind.COLOR_BASIC),
            EffectGpuAvailability.decide(colorPlan, caps(enabled = emptySet()))
        )
    }

    @Test
    fun `sin FBO cae a CPU`() {
        assertEquals(
            EffectGpuDecision.UseCpuFallback(EffectFallbackReason.FBO_UNAVAILABLE),
            EffectGpuAvailability.decide(colorPlan, caps(fbo = false))
        )
    }

    @Test
    fun `sin precision alta cae a CPU en vez de mostrar bandas`() {
        assertEquals(
            EffectGpuDecision.UseCpuFallback(EffectFallbackReason.NO_HIGH_PRECISION_FRAGMENT),
            EffectGpuAvailability.decide(colorPlan, caps(highp = false))
        )
    }

    @Test
    fun `el motivo se reporta en el orden documentado`() {
        // Sin implementar gana a shader fallido, que gana a desactivado, que gana a las capacidades globales.
        val everythingWrong = caps(
            implemented = emptySet(), enabled = emptySet(), failed = setOf(EffectPassKind.COLOR_BASIC), fbo = false, highp = false
        )
        assertEquals(
            EffectFallbackReason.PASS_NOT_IMPLEMENTED,
            (EffectGpuAvailability.decide(colorPlan, everythingWrong) as EffectGpuDecision.UseCpuFallback).reason
        )
        val failedAndDisabled = caps(enabled = emptySet(), failed = setOf(EffectPassKind.COLOR_BASIC), fbo = false)
        assertEquals(
            EffectFallbackReason.SHADER_COMPILE_FAILED,
            (EffectGpuAvailability.decide(colorPlan, failedAndDisabled) as EffectGpuDecision.UseCpuFallback).reason
        )
        val disabledAndNoFbo = caps(enabled = emptySet(), fbo = false)
        assertEquals(
            EffectFallbackReason.FEATURE_DISABLED,
            (EffectGpuAvailability.decide(colorPlan, disabledAndNoFbo) as EffectGpuDecision.UseCpuFallback).reason
        )
        val noFboNoHighp = caps(fbo = false, highp = false)
        assertEquals(
            EffectFallbackReason.FBO_UNAVAILABLE,
            (EffectGpuAvailability.decide(colorPlan, noFboNoHighp) as EffectGpuDecision.UseCpuFallback).reason
        )
    }
}
