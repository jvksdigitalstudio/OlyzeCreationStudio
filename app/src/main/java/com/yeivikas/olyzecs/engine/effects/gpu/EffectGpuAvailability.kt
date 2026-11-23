package com.yeivikas.olyzecs.engine.effects.gpu

/** Por qué un plan no se ejecuta en la GPU y se usa el camino CPU actual. */
enum class EffectFallbackReason {
    /** El pase no está activado por su indicador (todavía no superó la verificación en el dispositivo). */
    FEATURE_DISABLED,

    /** El pase existe en el modelo pero aún no tiene shader. */
    PASS_NOT_IMPLEMENTED,

    /** El shader de este pase no compiló o no enlazó en este dispositivo. */
    SHADER_COMPILE_FAILED,

    /** No se pueden crear texturas de render (FBO incompleto o no disponible). */
    FBO_UNAVAILABLE,

    /** El fragment shader no tiene `highp`: con `mediump` los efectos mostrarían bandas y bordes ajustados a píxel. */
    NO_HIGH_PRECISION_FRAGMENT
}

sealed interface EffectGpuDecision {
    /** Se ejecuta en la GPU (también cuando el plan está vacío: no hay nada que hacer). */
    data object UseGpu : EffectGpuDecision

    /** Se usa el camino CPU; [reason] se registra en el «Registro de errores». */
    data class UseCpuFallback(val reason: EffectFallbackReason, val offendingPass: EffectPassKind? = null) : EffectGpuDecision
}

/** Lo que el dispositivo y la configuración permiten hoy. Lo rellena la capa GL; esta lógica no la conoce. */
data class GpuEffectCapabilities(
    /** Pases que tienen shader. */
    val implementedPasses: Set<EffectPassKind>,
    /** Pases activados por su indicador. */
    val enabledPasses: Set<EffectPassKind>,
    /** Pases cuyo shader falló al compilar en este dispositivo. */
    val failedPasses: Set<EffectPassKind> = emptySet(),
    val fboUsable: Boolean,
    val highPrecisionFragment: Boolean
)

/**
 * Decide GPU o respaldo CPU para un plan (criterio 6 del ADR-019: nunca una imagen rota ni degradada en silencio).
 * Pura y determinista. Orden de las comprobaciones: por pase (no implementado → shader fallido → desactivado), y luego
 * las capacidades globales (FBO, precisión).
 */
object EffectGpuAvailability {

    fun decide(plan: EffectPassPlan, capabilities: GpuEffectCapabilities): EffectGpuDecision {
        if (plan.isEmpty) return EffectGpuDecision.UseGpu
        for (pass in plan.passes) {
            if (pass !in capabilities.implementedPasses) {
                return EffectGpuDecision.UseCpuFallback(EffectFallbackReason.PASS_NOT_IMPLEMENTED, pass)
            }
            if (pass in capabilities.failedPasses) {
                return EffectGpuDecision.UseCpuFallback(EffectFallbackReason.SHADER_COMPILE_FAILED, pass)
            }
            if (pass !in capabilities.enabledPasses) {
                return EffectGpuDecision.UseCpuFallback(EffectFallbackReason.FEATURE_DISABLED, pass)
            }
        }
        if (!capabilities.fboUsable) return EffectGpuDecision.UseCpuFallback(EffectFallbackReason.FBO_UNAVAILABLE)
        if (!capabilities.highPrecisionFragment) {
            return EffectGpuDecision.UseCpuFallback(EffectFallbackReason.NO_HIGH_PRECISION_FRAGMENT)
        }
        return EffectGpuDecision.UseGpu
    }
}
