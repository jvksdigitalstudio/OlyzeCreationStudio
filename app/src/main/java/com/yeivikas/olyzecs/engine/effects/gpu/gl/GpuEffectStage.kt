package com.yeivikas.olyzecs.engine.effects.gpu.gl

import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.effects.gpu.EffectCacheSignature
import com.yeivikas.olyzecs.engine.effects.gpu.EffectGpuAvailability
import com.yeivikas.olyzecs.engine.effects.gpu.EffectGpuDecision
import com.yeivikas.olyzecs.engine.effects.gpu.EffectPaddingCalculator
import com.yeivikas.olyzecs.engine.effects.gpu.EffectPassKind
import com.yeivikas.olyzecs.engine.effects.gpu.EffectPassPlan
import com.yeivikas.olyzecs.engine.effects.gpu.EffectPassPlanner
import com.yeivikas.olyzecs.engine.effects.gpu.EffectResultCache
import com.yeivikas.olyzecs.engine.effects.gpu.GpuEffectCapabilities
import com.yeivikas.olyzecs.engine.effects.gpu.GpuTexturePool
import com.yeivikas.olyzecs.engine.effects.gpu.LayerEffectParams
import com.yeivikas.olyzecs.engine.effects.gpu.PooledTexture

/**
 * La etapa de efectos GPU de una capa (ADR-019), tal como la ven los renderers: les da el identificador de la textura
 * con la que dibujar. El tamaño LÓGICO de la imagen (para la relación de aspecto) sigue siendo el de la capa: la textura que
 * se devuelve puede tener otro tamaño y el dibujo no debe depender de él.
 *
 * - **Capa sin efectos** (parámetros neutros, que es lo habitual): devuelve la textura de entrada tal cual, sin ningún
 *   coste ni cambio de comportamiento. Es el camino de TODAS las capas hoy.
 * - **Capa con efectos**: si la GPU puede ([EffectGpuAvailability]) renderiza los pases a una textura propia y la
 *   cachea por `(contenido, parámetros, tamaño)`, de modo que no se recalcula mientras nada cambie. Si no puede, devuelve
 *   la textura original y registra el motivo UNA vez (el efecto caerá al camino CPU; nunca una imagen rota).
 *
 * Una instancia por contexto GL (vista previa, exportador, miniaturas). Todos sus métodos, en el hilo de ese contexto.
 *
 * @param enabledPasses pases activados por su indicador. Vacío por defecto: ninguno supera aún la verificación en el
 *   dispositivo (ADR-019 §7), así que en producción todos caen al camino CPU hasta activarlos uno a uno.
 */
class GpuEffectStage(
    private val enabledPasses: Set<EffectPassKind> = emptySet(),
    textureBudgetBytes: Long = DEFAULT_TEXTURE_BUDGET_BYTES
) {
    private val allocator = GlTextureAllocator()
    private val pool = GpuTexturePool(allocator, textureBudgetBytes)
    private val cache = EffectResultCache<PooledTexture>(textureBudgetBytes, { it.bytes }, { pool.release(it) })
    // Perezoso: ThumbnailRenderer crea una etapa por miniatura y casi ninguna capa tiene efectos; el ejecutor reserva
    // un búfer directo (memoria nativa) que así solo se pide cuando una capa realmente lo necesita.
    private val runnerHolder = lazy(LazyThreadSafetyMode.NONE) { EffectPassRunner() }
    private val runner: EffectPassRunner get() = runnerHolder.value
    private val reported = HashMap<String, HashSet<String>>()

    /**
     * El identificador de la textura con la que dibujar la capa [layerId]: [textureId] tal cual si no hay nada que aplicar o
     * no se puede en la GPU, o el de la textura con los efectos ya aplicados. [textureWidthPx] y [textureHeightPx] son el
     * tamaño REAL de [textureId] (puede diferir del tamaño lógico de la capa). [sourceVersion] debe cambiar cada vez que
     * cambia el CONTENIDO de [textureId] (ver [EffectCacheSignature]).
     */
    fun textureIdFor(
        layerId: String,
        sourceVersion: Long,
        params: LayerEffectParams,
        textureId: Int,
        textureWidthPx: Int,
        textureHeightPx: Int
    ): Int {
        // Sin textura válida (id <= 0) o sin tamaño no hay nada que procesar: se deja pasar, igual que una capa sin efectos.
        if (textureId <= 0 || textureWidthPx <= 0 || textureHeightPx <= 0) return textureId
        // Vía rápida SIN asignaciones: el caso habitual (capa sin efectos) se resuelve aquí en cada frame y capa, sin
        // construir objetos, planes ni copias saneadas. `isNeutral` solo lee números.
        if (params.isNeutral) return textureId
        val plan = EffectPassPlanner.plan(params)
        if (plan.isEmpty) return textureId
        return effectTextureId(layerId, sourceVersion, params, plan, textureId, textureWidthPx, textureHeightPx) ?: textureId
    }

    /** La textura con los efectos aplicados, o `null` si hay que dejar la original (el motivo ya queda registrado). */
    private fun effectTextureId(
        layerId: String,
        sourceVersion: Long,
        params: LayerEffectParams,
        plan: EffectPassPlan,
        textureId: Int,
        width: Int,
        height: Int
    ): Int? {
        plan.passes.forEach { runner.prepare(it) }
        val decision = EffectGpuAvailability.decide(plan, capabilities())
        if (decision is EffectGpuDecision.UseCpuFallback) {
            reportOnce(layerId, decision.reason.name) { "usa CPU: ${decision.reason}${decision.offendingPass?.let { " ($it)" } ?: ""}" }
            return null
        }
        if (plan.passes.size != 1) {
            reportOnce(layerId, KEY_MULTI_PASS) { "plan de ${plan.passes.size} pases: los planes de varios pases llegan en la fase 5.1" }
            return null
        }
        if (!EffectPaddingCalculator.calculate(plan, width, height).isZero) {
            reportOnce(layerId, KEY_PADDING) { "el plan añade margen y el dibujo aún no amplía el cuadro de la capa (fase 5.2)" }
            return null
        }

        val signature = EffectCacheSignature(sourceVersion, params, width, height)
        cache.get(layerId, signature)?.let { return it.id }

        val destination = try {
            pool.acquire(width, height)
        } catch (e: IllegalStateException) {
            reportOnce(layerId, KEY_NO_TEXTURE) { "no se pudo reservar la textura de destino: ${e.message}" }
            return null
        }
        if (!runner.runPass(plan.passes.single(), textureId, destination.id, width, height)) {
            pool.release(destination)
            reportOnce(layerId, KEY_PASS_FAILED) { "el pase ${plan.passes.single()} falló al ejecutarse" }
            return null
        }
        cache.put(layerId, signature, destination)
        return destination.id
    }

    private fun capabilities() = GpuEffectCapabilities(
        implementedPasses = runner.implementedPasses(),
        enabledPasses = enabledPasses,
        failedPasses = runner.failedPasses(),
        fboUsable = runner.fboUsable(),
        highPrecisionFragment = runner.hasHighPrecisionFragment()
    )

    /** Libera los resultados de las capas que ya no existen. */
    fun retainOnly(activeLayerIds: Set<String>) {
        cache.retainOnly(activeLayerIds)
    }

    /** El contexto GL se perdió o se recreó: todo lo que había en la GPU ya no existe y se olvida sin destruir. */
    fun onContextLost() {
        cache.abandon()
        pool.onContextLost()
        if (runnerHolder.isInitialized()) runner.onContextLost()
        allocator.onContextLost()
        reported.clear()
    }

    /** Cierre normal (con el contexto activo): destruye texturas, programas y framebuffer. */
    fun dispose() {
        cache.clear()
        pool.dispose()
        if (runnerHolder.isInitialized()) runner.dispose()
    }

    /**
     * Registra cada motivo ([key]) una sola vez por capa. El bucle de dibujo pasa por aquí en cada frame, así que la
     * comprobación no asigna memoria (la clave es constante) y el mensaje solo se construye la primera vez.
     */
    private inline fun reportOnce(layerId: String, key: String, message: () -> String) {
        if (reported.getOrPut(layerId) { HashSet() }.add(key)) {
            AppLogger.w(TAG, "Capa $layerId: ${message()}")
        }
    }

    companion object {
        private const val TAG = "GpuEffectStage"
        private const val KEY_MULTI_PASS = "MULTI_PASS_PLAN"
        private const val KEY_PADDING = "PADDING_NOT_SUPPORTED"
        private const val KEY_NO_TEXTURE = "NO_DESTINATION_TEXTURE"
        private const val KEY_PASS_FAILED = "PASS_FAILED"

        /**
         * Presupuesto inicial de texturas de efectos (192 MB). NO está medido en la tablet objetivo: es un punto de partida
         * conservador que se ajustará con mediciones reales (ADR-019 §6.2).
         */
        const val DEFAULT_TEXTURE_BUDGET_BYTES: Long = 192L * 1024L * 1024L
    }
}
