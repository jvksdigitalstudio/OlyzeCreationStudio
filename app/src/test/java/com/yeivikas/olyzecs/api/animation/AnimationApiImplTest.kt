package com.yeivikas.olyzecs.api.animation

import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.FreezeRuntimeState
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.animation.SpeedRampEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica que [AnimationApiImpl] DELEGA fielmente en [SpeedRampEngine]
 * en vez de reimplementar la lógica — es exactamente lo que el diseño
 * aprobado exige ("EliNer debe delegar hacia el Engine", sección 14).
 *
 * No repite la batería completa de [SpeedRampEngineTest] (esa ya prueba
 * que la fórmula del motor es correcta) — solo prueba que, para las
 * mismas entradas, `AnimationApiImpl` devuelve EXACTAMENTE lo mismo que
 * `SpeedRampEngine` directo, para uno de cada operación. Corre como JVM
 * unit test puro: cero Android en toda la cadena.
 */
class AnimationApiImplTest {

    private val api: AnimationApi = AnimationApiImpl()

    @Test
    fun `speedAt delega sin alterar el resultado del motor`() {
        val keyframes = listOf(SpeedKeyframe(timeMs = 0L, speed = 1f), SpeedKeyframe(timeMs = 1_000L, speed = 3f))
        val expected = SpeedRampEngine.speedAt(keyframes, 500L)
        assertEquals(expected, api.speedAt(keyframes, 500L))
    }

    @Test
    fun `step delega sin alterar el resultado del motor`() {
        val freezeFrames = listOf(FreezeFrame(id = "f1", atMs = 500L, holdMs = 200L))
        val expected = SpeedRampEngine.step(
            currentBaseMs = 500L, tickMs = 100L, freezeState = FreezeRuntimeState(),
            baseDurationMs = 10_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames
        )
        val actual = api.step(
            currentBaseMs = 500L, tickMs = 100L, freezeState = FreezeRuntimeState(),
            baseDurationMs = 10_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `computeOutputDurationMs delega sin alterar el resultado del motor`() {
        val speedKeyframes = listOf(SpeedKeyframe(timeMs = 0L, speed = 2f))
        val expected = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 1_000L, speedKeyframes = speedKeyframes, freezeFrames = emptyList(), fps = 10
        )
        val actual = api.computeOutputDurationMs(
            baseDurationMs = 1_000L, speedKeyframes = speedKeyframes, freezeFrames = emptyList(), fps = 10
        )
        assertEquals(expected, actual)
    }
}
