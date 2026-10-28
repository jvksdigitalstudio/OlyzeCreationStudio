package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de [JoystickLayerMover] — motor de física puro (sin Compose, sin
 * Android), tal como promete el comentario grande de la clase ("se puede
 * escribir un test unitario común que empuje setDirection(...), llame
 * onFrame(...) a mano... algo imposible de hacer hoy con la lógica mezclada
 * adentro del composable gigante de EditorScreen"). Antes de este archivo
 * esa promesa no tenía ningún test real detrás — quedaba solo en el
 * comentario. Estos tests la sostienen de verdad y protegen contra
 * regresiones futuras en la física (velocidad, clamp, throttle, commit
 * final) sin necesitar Robolectric ni un dispositivo/emulador.
 *
 * Arnés mínimo: en vez de un mock framework, variables locales mutables que
 * hacen de "capa" (translateX/Y) y un contador de commits — exactamente lo
 * que las lambdas de inyección del constructor esperan.
 */
class JoystickLayerMoverTest {

    private class Harness(
        speedUnitsPerSec: Float = 2f,
        commitThrottleMs: Long = 100L,
        private var panLimitValue: Float = 2f,
        private var editable: Boolean = true
    ) {
        var translateX = 0f
        var translateY = 0f
        var commitCount = 0
            private set

        val mover = JoystickLayerMover(
            getTranslateX = { translateX },
            setTranslateX = { translateX = it },
            getTranslateY = { translateY },
            setTranslateY = { translateY = it },
            panLimit = { panLimitValue },
            layerIsEditable = { editable },
            commitFrame = { commitCount++ },
            speedUnitsPerSec = speedUnitsPerSec,
            commitThrottleMs = commitThrottleMs
        )

        fun setEditable(value: Boolean) { editable = value }
        fun setPanLimit(value: Float) { panLimitValue = value }
    }

    // ---- Reposo -------------------------------------------------------

    @Test
    fun `sin direccion y sin commit pendiente, onFrame no mueve ni commitea nada`() {
        val h = Harness()
        h.mover.onFrame(dtSeconds = 0.5f, nowMs = 1_000L)
        assertEquals(0f, h.translateX)
        assertEquals(0f, h.translateY)
        assertEquals(0, h.commitCount)
    }

    // ---- Movimiento e integración de velocidad -------------------------

    @Test
    fun `stick a fondo en +X mueve translateX en linea con velocidad por dt`() {
        val h = Harness(speedUnitsPerSec = 2f, commitThrottleMs = 10_000L) // throttle largo: no interfiere con este assert
        h.mover.setDirection(1f, 0f)
        h.mover.onFrame(dtSeconds = 0.25f, nowMs = 1_000L)
        // 2 unidades/seg * 0.25s = 0.5
        assertEquals(0.5f, h.translateX, 0.0001f)
        assertEquals(0f, h.translateY, 0.0001f)
    }

    @Test
    fun `el eje Y se invierte respecto del vector del stick, igual que el paneo tactil`() {
        // Empujar el stick "hacia abajo" (dirY positivo, misma convención
        // que Joystick.kt) debe DISMINUIR translateY — igual que arrastrar
        // el dedo hacia abajo en el paneo táctil del preview (ver
        // EditorScreen.kt: rawTranslateY = rawTranslateY - panDy/boxHeight*2f
        // cuando panDy es positivo hacia abajo).
        val h = Harness(speedUnitsPerSec = 2f, commitThrottleMs = 10_000L)
        h.mover.setDirection(0f, 1f)
        h.mover.onFrame(dtSeconds = 0.25f, nowMs = 1_000L)
        assertEquals(-0.5f, h.translateY, 0.0001f)
    }

    @Test
    fun `movimiento continuo a lo largo de varios frames se acumula`() {
        val h = Harness(speedUnitsPerSec = 1f, commitThrottleMs = 10_000L)
        h.mover.setDirection(1f, 0f)
        repeat(4) { h.mover.onFrame(dtSeconds = 0.1f, nowMs = 1_000L + it) }
        // 1 unidad/seg * 0.1s * 4 frames = 0.4
        assertEquals(0.4f, h.translateX, 0.0001f)
    }

    // ---- Clamp a panLimit ----------------------------------------------

    @Test
    fun `el movimiento nunca supera el panLimit vigente, aunque el dt sea grande`() {
        val h = Harness(speedUnitsPerSec = 5f, commitThrottleMs = 10_000L)
        h.setPanLimit(1f)
        h.mover.setDirection(1f, 0f)
        h.mover.onFrame(dtSeconds = 1f, nowMs = 1_000L) // 5 unidades pedidas, límite = 1
        assertEquals(1f, h.translateX, 0.0001f)
    }

    @Test
    fun `el panLimit se consulta en cada frame, un cambio de zoom a mitad de gesto se respeta al instante`() {
        val h = Harness(speedUnitsPerSec = 10f, commitThrottleMs = 10_000L)
        h.setPanLimit(2f)
        h.mover.setDirection(1f, 0f)
        h.mover.onFrame(dtSeconds = 1f, nowMs = 1_000L)
        assertEquals(2f, h.translateX, 0.0001f) // tope viejo

        h.setPanLimit(5f) // el usuario hace zoom in a mitad de gesto
        h.mover.onFrame(dtSeconds = 1f, nowMs = 1_100L)
        assertEquals(5f, h.translateX, 0.0001f) // el nuevo tope se respeta de inmediato
    }

    // ---- layerIsEditable ------------------------------------------------

    @Test
    fun `con la capa no editable el joystick no mueve nada, aunque el stick este activo`() {
        val h = Harness()
        h.setEditable(false)
        h.mover.setDirection(1f, 1f)
        h.mover.onFrame(dtSeconds = 0.5f, nowMs = 1_000L)
        assertEquals(0f, h.translateX)
        assertEquals(0f, h.translateY)
        assertEquals(0, h.commitCount)
    }

    // ---- Throttle de commits --------------------------------------------

    @Test
    fun `el primer frame en movimiento commitea de inmediato`() {
        val h = Harness(commitThrottleMs = 120L)
        h.mover.setDirection(1f, 0f)
        h.mover.onFrame(dtSeconds = 0.016f, nowMs = 1_000L)
        assertEquals(1, h.commitCount)
    }

    @Test
    fun `frames siguientes dentro de la ventana de throttle NO generan un commit nuevo`() {
        val h = Harness(commitThrottleMs = 120L)
        h.mover.setDirection(1f, 0f)
        h.mover.onFrame(dtSeconds = 0.016f, nowMs = 1_000L) // dispara el primer commit
        assertEquals(1, h.commitCount)

        h.mover.onFrame(dtSeconds = 0.016f, nowMs = 1_050L) // +50ms, todavia dentro de los 120ms
        h.mover.onFrame(dtSeconds = 0.016f, nowMs = 1_100L) // +100ms, todavia dentro
        assertEquals(1, h.commitCount)

        h.mover.onFrame(dtSeconds = 0.016f, nowMs = 1_121L) // +121ms, ya pasó el throttle
        assertEquals(2, h.commitCount)
    }

    // ---- Commit final garantizado al soltar el stick --------------------

    @Test
    fun `al soltar el stick con un commit pendiente sin descargar, se commitea una ultima vez`() {
        val h = Harness(commitThrottleMs = 120L)
        h.mover.setDirection(1f, 0f)
        h.mover.onFrame(dtSeconds = 0.016f, nowMs = 1_000L) // commit inmediato #1
        h.mover.onFrame(dtSeconds = 0.016f, nowMs = 1_050L) // se movió, pero throttle todavía no vence
        assertEquals(1, h.commitCount)

        h.mover.setDirection(0f, 0f) // el usuario suelta el stick
        h.mover.onFrame(dtSeconds = 0.016f, nowMs = 1_060L)
        // La posición final (del frame de 1_050L, sin commitear todavía)
        // SIEMPRE se guarda al soltar, no queda flotando sin persistir.
        assertEquals(2, h.commitCount)
    }

    @Test
    fun `soltar el stick sin ningun movimiento pendiente no genera un commit extra`() {
        val h = Harness()
        h.mover.setDirection(0f, 0f)
        h.mover.onFrame(dtSeconds = 0.016f, nowMs = 1_000L)
        assertEquals(0, h.commitCount)
    }

    // ---- Protecciones de entrada -----------------------------------------

    @Test
    fun `dtSeconds no positivo no mueve la capa`() {
        val h = Harness()
        h.mover.setDirection(1f, 1f)
        h.mover.onFrame(dtSeconds = 0f, nowMs = 1_000L)
        assertEquals(0f, h.translateX)
        assertEquals(0f, h.translateY)

        h.mover.onFrame(dtSeconds = -0.1f, nowMs = 1_001L)
        assertEquals(0f, h.translateX)
        assertEquals(0f, h.translateY)
    }

    @Test
    fun `movimiento diagonal normalizado no viaja mas rapido que un eje solo (sin bug de diagonal veloz)`() {
        // Igual que Joystick.kt ya entrega (dirX, dirY) como vector
        // unitario (normalizado antes de aplicar la curva), empujar el
        // stick en diagonal con magnitud 1 no debería moverse más rápido
        // que empujarlo en un solo eje con la misma magnitud 1 — este test
        // documenta que JoystickLayerMover no reintroduce ese bug clásico
        // al aplicar cada eje por separado.
        val h1 = Harness(speedUnitsPerSec = 2f, commitThrottleMs = 10_000L)
        h1.mover.setDirection(1f, 0f)
        h1.mover.onFrame(dtSeconds = 0.25f, nowMs = 1_000L)
        val straightDistance = kotlin.math.abs(h1.translateX)

        val diag = 0.70710678f // sqrt(2)/2, vector unitario en diagonal
        val h2 = Harness(speedUnitsPerSec = 2f, commitThrottleMs = 10_000L)
        h2.mover.setDirection(diag, diag)
        h2.mover.onFrame(dtSeconds = 0.25f, nowMs = 1_000L)
        val diagDistance = kotlin.math.sqrt(h2.translateX * h2.translateX + h2.translateY * h2.translateY)

        assertEquals(straightDistance, diagDistance, 0.001f)
    }
}
