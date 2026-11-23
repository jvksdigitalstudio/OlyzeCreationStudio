package com.yeivikas.olyzecs.engine.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [SpeedRampEngine] — motor puro (sin Android, sin estado propio
 * más allá del que se le pasa explícitamente), calculados a partir de la
 * fórmula REAL implementada, no de una fórmula supuesta.
 */
class SpeedRampEngineTest {

    // ---- speedAt ----------------------------------------------------

    @Test
    fun `speedAt sin keyframes devuelve velocidad normal 1x`() {
        assertEquals(1f, SpeedRampEngine.speedAt(emptyList(), 5_000L))
    }

    @Test
    fun `speedAt antes del primer keyframe usa su velocidad`() {
        val keyframes = listOf(SpeedKeyframe(timeMs = 1_000L, speed = 2f))
        assertEquals(2f, SpeedRampEngine.speedAt(keyframes, 0L))
    }

    @Test
    fun `speedAt despues del ultimo keyframe usa su velocidad`() {
        val keyframes = listOf(SpeedKeyframe(timeMs = 0L, speed = 0.5f), SpeedKeyframe(timeMs = 1_000L, speed = 3f))
        assertEquals(3f, SpeedRampEngine.speedAt(keyframes, 5_000L))
    }

    @Test
    fun `speedAt interpola linealmente entre dos keyframes`() {
        val keyframes = listOf(SpeedKeyframe(timeMs = 0L, speed = 1f), SpeedKeyframe(timeMs = 1_000L, speed = 3f))
        // Punto medio exacto -> promedio exacto
        assertEquals(2f, SpeedRampEngine.speedAt(keyframes, 500L))
        // Un cuarto del camino -> 1 + (3-1)*0.25 = 1.5
        assertEquals(1.5f, SpeedRampEngine.speedAt(keyframes, 250L))
    }

    @Test
    fun `speedAt satura fuera del rango 0_1x a 4x aunque el keyframe pida mas`() {
        val fast = listOf(SpeedKeyframe(timeMs = 0L, speed = 999f))
        assertEquals(4f, SpeedRampEngine.speedAt(fast, 0L))

        val slow = listOf(SpeedKeyframe(timeMs = 0L, speed = -5f))
        assertEquals(0.1f, SpeedRampEngine.speedAt(slow, 0L))
    }

    // ---- step ---------------------------------------------------------

    @Test
    fun `step con velocidad normal avanza el tiempo base segun el tick`() {
        val (next, freeze) = SpeedRampEngine.step(
            currentBaseMs = 0L,
            tickMs = 100L,
            freezeState = FreezeRuntimeState(),
            baseDurationMs = 10_000L,
            speedKeyframes = emptyList(),
            freezeFrames = emptyList()
        )
        assertEquals(100L, next)
        assertEquals(0L, freeze.remainingMs)
    }

    @Test
    fun `step nunca se pasa del final del proyecto`() {
        val (next, _) = SpeedRampEngine.step(
            currentBaseMs = 9_950L,
            tickMs = 100L,
            freezeState = FreezeRuntimeState(),
            baseDurationMs = 10_000L,
            speedKeyframes = emptyList(),
            freezeFrames = emptyList()
        )
        assertEquals(10_000L, next)
    }

    @Test
    fun `step dispara un freeze al llegar a su instante y lo mantiene fijo mientras dura`() {
        val freezeFrames = listOf(FreezeFrame(id = "f1", atMs = 500L, holdMs = 200L))

        // Tick 1: currentBaseMs llega exactamente al freeze -> se dispara
        val (base1, freeze1) = SpeedRampEngine.step(
            currentBaseMs = 500L, tickMs = 100L, freezeState = FreezeRuntimeState(),
            baseDurationMs = 10_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames
        )
        assertEquals(500L, base1)
        assertEquals(200L, freeze1.remainingMs)
        assertEquals(setOf("f1"), freeze1.consumedFreezeIds)

        // Tick 2: sigue congelado, el tiempo base NO avanza, remainingMs baja
        val (base2, freeze2) = SpeedRampEngine.step(
            currentBaseMs = base1, tickMs = 100L, freezeState = freeze1,
            baseDurationMs = 10_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames
        )
        assertEquals(500L, base2)
        assertEquals(100L, freeze2.remainingMs)

        // Tick 3: se acaba el hold -> remainingMs llega a 0, sigue fijo este tick
        val (base3, freeze3) = SpeedRampEngine.step(
            currentBaseMs = base2, tickMs = 100L, freezeState = freeze2,
            baseDurationMs = 10_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames
        )
        assertEquals(500L, base3)
        assertEquals(0L, freeze3.remainingMs)

        // Tick 4: remainingMs ya es 0 y el freeze quedó consumido -> retoma el avance normal
        val (base4, freeze4) = SpeedRampEngine.step(
            currentBaseMs = base3, tickMs = 100L, freezeState = freeze3,
            baseDurationMs = 10_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames
        )
        assertEquals(600L, base4)
        assertEquals(setOf("f1"), freeze4.consumedFreezeIds)
    }

    @Test
    fun `step no vuelve a disparar el mismo freeze ya consumido`() {
        val freezeFrames = listOf(FreezeFrame(id = "f1", atMs = 100L, holdMs = 50L))
        val alreadyConsumed = FreezeRuntimeState(remainingMs = 0L, consumedFreezeIds = setOf("f1"))
        val (next, freeze) = SpeedRampEngine.step(
            currentBaseMs = 100L, tickMs = 100L, freezeState = alreadyConsumed,
            baseDurationMs = 10_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames
        )
        assertEquals(200L, next) // avanza normal, no se re-congela
        assertEquals(setOf("f1"), freeze.consumedFreezeIds)
    }

    // ---- buildTimeMapping / computeOutputDurationMs --------------------

    @Test
    fun `computeOutputDurationMs sin rampas ni freezes devuelve la duracion base intacta`() {
        val result = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 12_345L, speedKeyframes = emptyList(), freezeFrames = emptyList(), fps = 30
        )
        assertEquals(12_345L, result)
    }

    @Test
    fun `buildTimeMapping y computeOutputDurationMs reflejan un freeze de 300ms sumado al final`() {
        // baseDurationMs=1000, fps=10 (tickMs=100), freeze en 500ms que
        // retiene 300ms -> el freeze agrega 3 "frames" extra pegados en
        // t=500 antes de retomar el avance normal hasta 1000.
        val freezeFrames = listOf(FreezeFrame(id = "f1", atMs = 500L, holdMs = 300L))
        val mapping = SpeedRampEngine.buildTimeMapping(
            baseDurationMs = 1_000L, fps = 10, speedKeyframes = emptyList(), freezeFrames = freezeFrames
        )
        assertEquals(0L, mapping.first())
        assertEquals(1_000L, mapping.last())
        assertEquals(5, mapping.count { it == 500L }) // el instante congelado se repite

        val outputMs = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 1_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames, fps = 10
        )
        assertEquals(mapping.size * 100L, outputMs)
    }

    @Test
    fun `una rampa a 2x reduce la cantidad de tiempo base recorrido por frame de salida`() {
        val speedKeyframes = listOf(SpeedKeyframe(timeMs = 0L, speed = 2f))
        val outputAt1x = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 1_000L, speedKeyframes = emptyList(), freezeFrames = emptyList(), fps = 10
        )
        val outputAt2x = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 1_000L, speedKeyframes = speedKeyframes, freezeFrames = emptyList(), fps = 10
        )
        // A doble velocidad, el mismo tramo de timeline base se recorre en
        // (aproximadamente) la mitad del tiempo real de salida.
        assertEquals(1_000L, outputAt1x)
        assertEquals(600L, outputAt2x)
        assertTrue(outputAt2x < outputAt1x)
    }

    // ---- FASE 2: fps real del proyecto, no un hardcode de 30 ----------
    //
    // Estos tests documentan y protegen el bug real encontrado en la
    // auditoría de Fase 2: `EditorViewModel.currentOutputDurationMs()` y
    // `EditorViewModel.exportVideo()` llamaban a esta misma función con
    // `fps = 30` fijo, sin importar `state.projectFps`. Como
    // `buildTimeMapping` avanza tick por tick (`1000/fps` por tick), el
    // resultado de `computeOutputDurationMs` SÍ depende del fps cuando hay
    // al menos una rampa de velocidad o un freeze frame activo — con un
    // proyecto a 24/60/90/120fps, usar 30 fijo daba una duración de
    // salida DISTINTA de la que el propio exportador terminaba generando
    // (que sí usaba el fps real para contar frames de video) — un
    // desincronismo real entre la duración de audio calculada y la
    // duración real de video, no solo un número mal mostrado en la UI.

    @Test
    fun `computeOutputDurationMs con freeze frame da resultados distintos segun el fps, con la MISMA rampa`() {
        val freezeFrames = listOf(FreezeFrame(id = "f1", atMs = 500L, holdMs = 300L))

        val outputAt24fps = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 1_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames, fps = 24
        )
        val outputAt30fps = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 1_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames, fps = 30
        )
        val outputAt60fps = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 1_000L, speedKeyframes = emptyList(), freezeFrames = freezeFrames, fps = 60
        )

        // Los tres fps producen una duración de salida distinta para el
        // MISMO freeze frame — la prueba directa de por qué un `fps=30`
        // fijo es incorrecto para un proyecto que en realidad es a 24 o
        // 60fps: el resultado a 30fps NO sirve como aproximación válida
        // de los otros dos.
        assertTrue("24fps y 30fps deben dar duraciones distintas", outputAt24fps != outputAt30fps)
        assertTrue("30fps y 60fps deben dar duraciones distintas", outputAt30fps != outputAt60fps)
    }

    @Test
    fun `computeOutputDurationMs con rampa de velocidad da resultados distintos segun el fps`() {
        val speedKeyframes = listOf(SpeedKeyframe(timeMs = 0L, speed = 1.7f))

        val outputAt24fps = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 2_000L, speedKeyframes = speedKeyframes, freezeFrames = emptyList(), fps = 24
        )
        val outputAt30fps = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 2_000L, speedKeyframes = speedKeyframes, freezeFrames = emptyList(), fps = 30
        )
        val outputAt120fps = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 2_000L, speedKeyframes = speedKeyframes, freezeFrames = emptyList(), fps = 120
        )

        assertTrue("24fps y 30fps deben dar duraciones distintas con una rampa activa", outputAt24fps != outputAt30fps)
        assertTrue("30fps y 120fps deben dar duraciones distintas con una rampa activa", outputAt30fps != outputAt120fps)
    }

    @Test
    fun `sin rampas ni freezes el fps no afecta la duracion de salida (short-circuit correcto)`() {
        // Caso SIN rampas/freezes: `computeOutputDurationMs` devuelve la
        // duración base intacta sin importar el fps (short-circuit ya
        // existente) — por eso el bug de fps=30 fijo NUNCA se manifestaba
        // en proyectos simples, solo en los que combinan fps custom +
        // rampas/freezes, exactamente lo que documenta el informe.
        val outputAt24fps = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 5_000L, speedKeyframes = emptyList(), freezeFrames = emptyList(), fps = 24
        )
        val outputAt60fps = SpeedRampEngine.computeOutputDurationMs(
            baseDurationMs = 5_000L, speedKeyframes = emptyList(), freezeFrames = emptyList(), fps = 60
        )
        assertEquals(5_000L, outputAt24fps)
        assertEquals(5_000L, outputAt60fps)
    }
}
