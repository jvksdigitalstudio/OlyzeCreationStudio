package com.yeivikas.olyzecs.api.animation

import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.FreezeRuntimeState
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe

/**
 * Contrato público de EliNer para Animation (velocidad variable + freeze
 * frame). Respaldo real: `engine.animation.SpeedRampEngine`
 * (`speedAt`/`step`/`computeOutputDurationMs`) — ver
 * ELINER_API_V1_FASE1_DISENO.txt sección 14 ("EliNer debe delegar hacia
 * el Engine, no reimplementar lógica").
 *
 * Único dominio, junto con Mesh3D, implementado de punta a punta en
 * esta Etapa 1 (ver `AnimationApiImpl`): es matemática pura, sin
 * `Context`/Android, sin estado propio que gestionar — cero riesgo de
 * tocar UI/ViewModel/Engine para que exista una implementación real.
 *
 * Consumidor real: `EditorViewModel` (viewmodel/EditorViewModel.kt),
 * inyectada por constructor desde `EditorViewModelFactory`/
 * `MainActivity` (ver tarea "Animation → EliNer") — junto con Mesh3D,
 * es uno de los 2 dominios que ya usa `EliNer API` de verdad, no solo
 * "conectada y sin usar".
 */
interface AnimationApi {

    /** Velocidad (1x = normal) vigente en [baseTimeMs] según las rampas configuradas. */
    fun speedAt(speedKeyframes: List<SpeedKeyframe>, baseTimeMs: Long): Float

    /**
     * Avanza un tick de reproducción, aplicando velocidad variable y
     * freeze frames. Devuelve el nuevo tiempo base y el estado de
     * freeze actualizado (para pasar al siguiente tick).
     */
    fun step(
        currentBaseMs: Long,
        tickMs: Long,
        freezeState: FreezeRuntimeState,
        baseDurationMs: Long,
        speedKeyframes: List<SpeedKeyframe>,
        freezeFrames: List<FreezeFrame>
    ): Pair<Long, FreezeRuntimeState>

    /** Duración real de salida (exportado) dado el timeline base + rampas + freezes. */
    fun computeOutputDurationMs(
        baseDurationMs: Long,
        speedKeyframes: List<SpeedKeyframe>,
        freezeFrames: List<FreezeFrame>,
        fps: Int
    ): Long
}
