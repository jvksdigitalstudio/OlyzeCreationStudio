package com.yeivikas.olyzecs.engine.timeline

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [TimelineDurationManager]. No necesita Context de Android (usa
 * [kotlinx.coroutines.flow.MutableStateFlow]/[kotlinx.coroutines.flow.MutableSharedFlow]
 * puros), así que corre como JVM unit test normal.
 */
class TimelineDurationManagerTest {

    @Test
    fun `arranca con la duracion inicial de 1 minuto por defecto`() {
        val manager = TimelineDurationManager()
        assertEquals(TimelineLimits.INITIAL_DURATION_MS, manager.currentDurationMs())
        assertFalse(manager.isAtMaxLimit)
    }

    @Test
    fun `startNewProject siempre resetea a la duracion inicial`() {
        val manager = TimelineDurationManager(initialDurationMs = TimelineLimits.MAX_DURATION_MS)
        assertEquals(TimelineLimits.MAX_DURATION_MS, manager.currentDurationMs())
        manager.startNewProject()
        assertEquals(TimelineLimits.INITIAL_DURATION_MS, manager.currentDurationMs())
        assertFalse(manager.isAtMaxLimit)
    }

    @Test
    fun `restore satura valores fuera de rango en vez de aceptarlos crudos`() {
        val manager = TimelineDurationManager()
        manager.restore(savedDurationMs = -500L)
        assertEquals(1L, manager.currentDurationMs())

        manager.restore(savedDurationMs = TimelineLimits.MAX_DURATION_MS * 10)
        assertEquals(TimelineLimits.MAX_DURATION_MS, manager.currentDurationMs())
        assertTrue(manager.isAtMaxLimit)
    }

    @Test
    fun `restore no dispara expansion, solo adopta el valor guardado`() {
        val manager = TimelineDurationManager()
        manager.restore(savedDurationMs = 500_000L)
        assertEquals(500_000L, manager.currentDurationMs())
    }

    @Test
    fun `growIfApproachingEnd expande un tramo cuando el playhead entra en la ventana de disparo`() {
        val manager = TimelineDurationManager(initialDurationMs = 60_000L)
        // Dentro de la ventana de 3000ms del final (60_000 - 3_000 = 57_000)
        val result = manager.growIfApproachingEnd(playheadMs = 57_000L)
        assertEquals(120_000L, result) // tramo 1: +1 minuto
        assertEquals(120_000L, manager.currentDurationMs())
    }

    @Test
    fun `growIfApproachingEnd no expande si el playhead esta lejos del final`() {
        val manager = TimelineDurationManager(initialDurationMs = 60_000L)
        val result = manager.growIfApproachingEnd(playheadMs = 10_000L)
        assertEquals(60_000L, result)
    }

    @Test
    fun `ensureCapacityFor salta directo tantos tramos como haga falta`() {
        val manager = TimelineDurationManager(initialDurationMs = 60_000L)
        // Pide capacidad muy por delante del final actual -> debe expandir
        // de una sola vez, no esperar ticks sucesivos.
        val result = manager.ensureCapacityFor(targetMs = 5_000_000L)
        assertTrue(result >= 5_000_000L)
        assertEquals(result, manager.currentDurationMs())
    }

    @Test
    fun `ensureCapacityFor no hace nada si el objetivo ya entra en la duracion actual`() {
        val manager = TimelineDurationManager(initialDurationMs = 600_000L)
        val result = manager.ensureCapacityFor(targetMs = 10_000L)
        assertEquals(600_000L, result)
    }

    @Test
    fun `emite MaxDurationReached una sola vez al quedar pegado en el techo`() = runBlocking {
        val manager = TimelineDurationManager(initialDurationMs = TimelineLimits.MAX_DURATION_MS)

        val firstEvent = async { manager.events.first() }
        yield() // deja que el collector de arriba se suscriba antes de emitir
        manager.growIfApproachingEnd(playheadMs = TimelineLimits.MAX_DURATION_MS)
        val event = withTimeout(2_000) { firstEvent.await() }
        assertEquals(TimelineEvent.MaxDurationReached, event)

        // Un segundo tick pegado al mismo techo NO debe volver a notificar
        // (ver `maxReachedNotified` en la implementación) — no debe llegar
        // ningún evento dentro de un timeout corto.
        val secondEvent = async { manager.events.first() }
        yield()
        manager.growIfApproachingEnd(playheadMs = TimelineLimits.MAX_DURATION_MS)
        val noSecondEvent = runCatching { withTimeout(300) { secondEvent.await() } }.getOrNull()
        // Se cancela explícitamente: si nunca llega el segundo evento (el
        // resultado esperado de este test), el `async` de arriba queda
        // suspendido para siempre esperando dentro de `first()`, y
        // `runBlocking` no terminaría nunca sin este cleanup.
        secondEvent.cancel()
        assertNull(noSecondEvent)
    }
}
