package com.yeivikas.olyzecs.api.animation

import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.FreezeRuntimeState
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.animation.SpeedRampEngine

/**
 * Implementación real de [AnimationApi]: delegación DIRECTA y sin
 * lógica propia hacia [SpeedRampEngine] — cero duplicación de la
 * matemática (la fórmula sigue viviendo en un solo lugar, el motor).
 * Esto es intencional y es lo que distingue esta clase de un "wrapper
 * mecánico sin justificación": la justificación es exactamente la
 * frontera de EliNer (ADR-004) — la aplicación pasa a conocer
 * `AnimationApi`, no `SpeedRampEngine`.
 *
 * Consumidor real: `EditorViewModel.step`/`currentOutputDurationMs`/
 * `speedAtPlayhead` (viewmodel/EditorViewModel.kt), ver tarea
 * "Animation → EliNer".
 */
class AnimationApiImpl : AnimationApi {

    override fun speedAt(speedKeyframes: List<SpeedKeyframe>, baseTimeMs: Long): Float =
        SpeedRampEngine.speedAt(speedKeyframes, baseTimeMs)

    override fun step(
        currentBaseMs: Long,
        tickMs: Long,
        freezeState: FreezeRuntimeState,
        baseDurationMs: Long,
        speedKeyframes: List<SpeedKeyframe>,
        freezeFrames: List<FreezeFrame>
    ): Pair<Long, FreezeRuntimeState> = SpeedRampEngine.step(
        currentBaseMs = currentBaseMs,
        tickMs = tickMs,
        freezeState = freezeState,
        baseDurationMs = baseDurationMs,
        speedKeyframes = speedKeyframes,
        freezeFrames = freezeFrames
    )

    override fun computeOutputDurationMs(
        baseDurationMs: Long,
        speedKeyframes: List<SpeedKeyframe>,
        freezeFrames: List<FreezeFrame>,
        fps: Int
    ): Long = SpeedRampEngine.computeOutputDurationMs(
        baseDurationMs = baseDurationMs,
        speedKeyframes = speedKeyframes,
        freezeFrames = freezeFrames,
        fps = fps
    )
}
