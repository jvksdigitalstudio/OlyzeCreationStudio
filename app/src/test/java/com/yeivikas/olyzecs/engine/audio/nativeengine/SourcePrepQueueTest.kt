package com.yeivikas.olyzecs.engine.audio.nativeengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La máquina de estados de las fuentes pendientes, comprobada SIN hilos ni reloj real: el tiempo entra como parámetro, así que cada
 * transición (espera, reintento, agotamiento, cancelación) se verifica al milisegundo y sin esperas.
 *
 * La espera entre reintentos se fija aquí en 100 ms × fallos acumulados para que la aritmética de los tests sea evidente.
 */
class SourcePrepQueueTest {

    private fun newQueue(maxAttempts: Int = 3) = SourcePrepQueue<String>(maxAttempts) { failures -> 100L * failures }

    private fun SourcePrepQueue<String>.add(key: String, lane: PrepLane = PrepLane.SHORT, nowMs: Long = 0L) =
        request(key, "payload-$key", lane, nowMs)

    // ------------------------------------------------------------------ orden y carriles

    @Test
    fun `las fuentes de un mismo carril salen por orden de peticion`() {
        val q = newQueue()
        q.add("a"); q.add("b"); q.add("c")
        assertEquals("a", q.next(PrepLane.SHORT, 0)?.key)
        assertEquals("b", q.next(PrepLane.SHORT, 0)?.key)
        assertEquals("c", q.next(PrepLane.SHORT, 0)?.key)
        assertNull(q.next(PrepLane.SHORT, 0))
    }

    @Test
    fun `el carril corto nunca toma una fuente larga`() {
        val q = newQueue()
        q.add("largo", PrepLane.LONG)
        assertNull(q.next(PrepLane.SHORT, 0))
        assertEquals("largo", q.next(PrepLane.LONG, 0)?.key)
    }

    @Test
    fun `el carril largo prefiere las largas y ayuda con las cortas cuando no hay`() {
        val q = newQueue()
        q.add("corta", PrepLane.SHORT)
        q.add("larga", PrepLane.LONG) // llegó DESPUÉS, pero el carril largo la prefiere
        assertEquals("larga", q.next(PrepLane.LONG, 0)?.key)
        assertEquals("corta", q.next(PrepLane.LONG, 0)?.key)
    }

    @Test
    fun `una fuente corta no espera a una larga que ya esta en curso`() {
        val q = newQueue()
        q.add("larga", PrepLane.LONG)
        assertEquals("larga", q.next(PrepLane.LONG, 0)?.key) // en curso, ocupando el carril largo
        q.add("corta", PrepLane.SHORT)
        assertEquals("corta", q.next(PrepLane.SHORT, 0)?.key) // el carril corto sigue libre
    }

    @Test
    fun `pedir otra vez una clave existente no la reordena ni la duplica`() {
        val q = newQueue()
        assertTrue(q.add("a"))
        assertTrue(q.add("b"))
        assertFalse(q.add("a")) // la vista previa pregunta sin parar: no debe alterar el orden
        assertEquals("a", q.next(PrepLane.SHORT, 0)?.key)
        assertEquals("b", q.next(PrepLane.SHORT, 0)?.key)
        assertNull(q.next(PrepLane.SHORT, 0))
    }

    @Test
    fun `el payload viaja intacto hasta la tarea`() {
        val q = newQueue()
        q.add("a")
        assertEquals("payload-a", q.next(PrepLane.SHORT, 0)?.payload)
    }

    // ------------------------------------------------------------------ fallos y reintentos

    @Test
    fun `un fallo transitorio espera, reintenta y al agotar los intentos queda fallida`() {
        val q = newQueue(maxAttempts = 3)
        q.add("a")

        assertEquals(1, q.next(PrepLane.SHORT, 0)?.attempt)
        q.complete("a", PrepOutcome.Transient("disco"), nowMs = 0)
        assertEquals(SourcePrepStatus.Retrying(attempt = 2, retryAtMs = 100, reason = "disco"), q.status("a", 0))
        assertNull(q.next(PrepLane.SHORT, 50)) // aún en espera
        assertEquals(50L, q.nextWakeDelayMs(PrepLane.SHORT, 50))

        assertEquals(2, q.next(PrepLane.SHORT, 100)?.attempt)
        q.complete("a", PrepOutcome.Transient("disco"), nowMs = 100)
        assertEquals(SourcePrepStatus.Retrying(attempt = 3, retryAtMs = 300, reason = "disco"), q.status("a", 100)) // 100 + 200

        assertEquals(3, q.next(PrepLane.SHORT, 300)?.attempt)
        q.complete("a", PrepOutcome.Transient("disco"), nowMs = 300)
        assertEquals(SourcePrepStatus.Failed(attempts = 3, reason = "disco"), q.status("a", 300))
        assertNull(q.next(PrepLane.SHORT, 1_000_000)) // fallida: no se entrega nunca más
        assertNull(q.nextWakeDelayMs(PrepLane.SHORT, 1_000_000))
    }

    @Test
    fun `un fallo transitorio con un solo intento permitido queda fallido a la primera`() {
        val q = newQueue(maxAttempts = 1)
        q.add("a")
        q.next(PrepLane.SHORT, 0)
        q.complete("a", PrepOutcome.Transient("x"), 0)
        assertEquals(SourcePrepStatus.Failed(1, "x"), q.status("a", 0))
    }

    @Test
    fun `un fallo permanente no se reintenta`() {
        val q = newQueue()
        q.add("a")
        q.next(PrepLane.SHORT, 0)
        q.complete("a", PrepOutcome.Permanent("sin permiso"), 0)
        assertEquals(SourcePrepStatus.Failed(1, "sin permiso"), q.status("a", 0))
        assertNull(q.next(PrepLane.SHORT, 1_000_000))
    }

    @Test
    fun `pedir una fuente fallida no la reabre`() {
        val q = newQueue()
        q.add("a")
        q.next(PrepLane.SHORT, 0)
        q.complete("a", PrepOutcome.Permanent("x"), 0)
        assertFalse(q.add("a", nowMs = 5_000))
        assertNull(q.next(PrepLane.SHORT, 5_000))
    }

    @Test
    fun `el exito y la cancelacion borran la clave sin dejar rastro`() {
        val q = newQueue()
        q.add("ok"); q.add("cancelada")
        q.next(PrepLane.SHORT, 0); q.next(PrepLane.SHORT, 0)
        q.complete("ok", PrepOutcome.Success, 0)
        q.complete("cancelada", PrepOutcome.Cancelled, 0)
        assertNull(q.status("ok", 0))
        assertNull(q.status("cancelada", 0))
        assertTrue(q.pending(0).isEmpty())
    }

    @Test
    fun `completar una clave que no esta en curso no cambia nada`() {
        val q = newQueue()
        q.complete("desconocida", PrepOutcome.Success, 0) // no debe lanzar
        q.add("a")
        q.complete("a", PrepOutcome.Permanent("x"), 0) // en cola, no en curso: se ignora
        assertEquals(SourcePrepStatus.Preparing(attempt = 1, running = false), q.status("a", 0))
    }

    // ------------------------------------------------------------------ reintento manual

    @Test
    fun `retry reabre una fallida y reinicia la cuenta de intentos`() {
        val q = newQueue()
        q.add("a")
        q.next(PrepLane.SHORT, 0)
        q.complete("a", PrepOutcome.Permanent("x"), 0)

        assertTrue(q.retry("a", nowMs = 500))
        assertEquals(SourcePrepStatus.Preparing(attempt = 1, running = false), q.status("a", 500))
        assertEquals(1, q.next(PrepLane.SHORT, 500)?.attempt)
    }

    @Test
    fun `retry adelanta una fuente que espera su reintento automatico`() {
        val q = newQueue()
        q.add("a")
        q.next(PrepLane.SHORT, 0)
        q.complete("a", PrepOutcome.Transient("x"), 0) // espera hasta 100
        assertNull(q.next(PrepLane.SHORT, 10))
        assertTrue(q.retry("a", nowMs = 10))
        assertEquals("a", q.next(PrepLane.SHORT, 10)?.key)
    }

    @Test
    fun `retry no hace nada con claves desconocidas, en curso o ya elegibles`() {
        val q = newQueue()
        assertFalse(q.retry("nada", 0))
        q.add("a")
        assertFalse(q.retry("a", 0)) // ya es elegible
        q.next(PrepLane.SHORT, 0)
        assertFalse(q.retry("a", 0)) // en curso
    }

    // ------------------------------------------------------------------ retain (lo que ya nadie usa)

    @Test
    fun `retain descarta lo que espera y lo fallido, y cancela lo que corre`() {
        val q = newQueue()
        q.add("espera"); q.add("corre"); q.add("falla"); q.add("viva")
        val running = q.next(PrepLane.SHORT, 0) // "espera" pasa a correr: la usamos como la que está en curso
        assertEquals("espera", running?.key)
        val failing = q.next(PrepLane.SHORT, 0)
        assertEquals("corre", failing?.key)
        q.complete("corre", PrepOutcome.Permanent("x"), 0) // "corre" queda fallida
        // Ahora: espera = EN CURSO, corre = FALLIDA, falla y viva = EN COLA

        q.retain(setOf("viva"))

        assertNull(q.status("corre", 0)) // fallida: descartada
        assertNull(q.status("falla", 0)) // en cola: descartada
        assertEquals(SourcePrepStatus.Preparing(1, running = false), q.status("viva", 0))
        // La que corre no se puede quitar a la fuerza: queda marcada para que el trabajador la suelte.
        assertTrue(running?.isCancelled() == true)
        assertEquals(SourcePrepStatus.Preparing(1, running = true), q.status("espera", 0))
    }

    @Test
    fun `una preparacion cancelada que termina con fallo no queda registrada como fallida`() {
        val q = newQueue()
        q.add("a")
        val task = q.next(PrepLane.SHORT, 0)
        q.retain(emptySet())
        assertTrue(task?.isCancelled() == true)
        q.complete("a", PrepOutcome.Transient("x"), 0)
        assertNull(q.status("a", 0)) // nadie la quiere: ni siquiera un fallo merece quedar
    }

    @Test
    fun `volver a pedir lo que se estaba cancelando anula la cancelacion`() {
        val q = newQueue()
        q.add("a")
        val task = q.next(PrepLane.SHORT, 0)
        q.retain(emptySet())
        assertTrue(task?.isCancelled() == true)
        assertFalse(q.add("a")) // el usuario deshizo el borrado del clip
        assertTrue(task?.isCancelled() == false)
    }

    // ------------------------------------------------------------------ espera y fotografía

    @Test
    fun `nextWakeDelayMs distingue lo elegible, lo que espera y lo que no hay`() {
        val q = newQueue()
        assertNull(q.nextWakeDelayMs(PrepLane.SHORT, 0)) // nada en cola
        q.add("a")
        assertEquals(0L, q.nextWakeDelayMs(PrepLane.SHORT, 0)) // elegible ya
        q.next(PrepLane.SHORT, 0)
        q.complete("a", PrepOutcome.Transient("x"), 0) // espera hasta 100
        assertEquals(100L, q.nextWakeDelayMs(PrepLane.SHORT, 0))
        assertEquals(1L, q.nextWakeDelayMs(PrepLane.SHORT, 99))
        // Una larga que espera NO es asunto del carril corto.
        assertNull(newQueue().also { it.add("l", PrepLane.LONG) }.nextWakeDelayMs(PrepLane.SHORT, 0))
    }

    @Test
    fun `pending refleja todos los estados a la vez, con lo pedido en cada clave`() {
        val q = newQueue()
        q.add("cola"); q.add("curso"); q.add("espera"); q.add("falla")
        q.next(PrepLane.SHORT, 0) // cola -> en curso (la usamos como "cola")
        val second = q.next(PrepLane.SHORT, 0)
        q.complete(second?.key ?: "", PrepOutcome.Transient("t"), 0) // curso -> espera de reintento
        val third = q.next(PrepLane.SHORT, 0)
        q.complete(third?.key ?: "", PrepOutcome.Permanent("p"), 0) // espera -> falla

        val byKey = q.pending(0).associateBy { it.key }
        assertEquals(4, byKey.size)
        assertEquals(SourcePrepStatus.Preparing(1, running = true), byKey["cola"]?.status)
        assertEquals(SourcePrepStatus.Retrying(2, 100, "t"), byKey["curso"]?.status)
        assertEquals(SourcePrepStatus.Failed(1, "p"), byKey["espera"]?.status)
        assertEquals(SourcePrepStatus.Preparing(1, running = false), byKey["falla"]?.status)
        assertEquals("payload-curso", byKey["curso"]?.payload) // lo pedido viaja con la clave
    }

    @Test
    fun `pending de una cola vacia es vacio`() {
        assertTrue(newQueue().pending(0).isEmpty())
    }

    @Test
    fun `retain informa si descarto algo y no si solo cancelo lo que corre`() {
        val q = newQueue()
        q.add("espera"); q.add("corre")
        q.next(PrepLane.SHORT, 0) // "espera" pasa a correr
        assertFalse(q.retain(setOf("espera", "corre"))) // nada que descartar
        assertTrue(q.retain(setOf("espera"))) // "corre" estaba en cola: descartada
        assertFalse(q.retain(emptySet())) // lo único que queda corre: solo se marca cancelada, su estado visible no cambia
        assertEquals(SourcePrepStatus.Preparing(1, running = true), q.status("espera", 0))
    }

    // ------------------------------------------------------------------ contrato

    @Test
    fun `un maximo de intentos invalido se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) { SourcePrepQueue<String>(maxAttempts = 0) }
    }

    @Test
    fun `la espera por defecto crece y se estabiliza`() {
        assertEquals(2_000L, defaultSourceBackoffMs(1))
        assertEquals(6_000L, defaultSourceBackoffMs(2))
        assertEquals(6_000L, defaultSourceBackoffMs(5))
    }

    // ------------------------------------------------------------------ clasificación de excepciones

    @Test
    fun `las excepciones se clasifican segun merezcan reintento`() {
        assertTrue(classifyPrepFailure(java.io.IOException("disco lleno")) is PrepOutcome.Transient)
        assertTrue(classifyPrepFailure(OutOfMemoryError()) is PrepOutcome.Transient)
        assertTrue(classifyPrepFailure(SecurityException("sin permiso")) is PrepOutcome.Permanent)
        assertTrue(classifyPrepFailure(IllegalStateException("bug")) is PrepOutcome.Permanent)
        assertEquals(PrepOutcome.Cancelled, classifyPrepFailure(java.util.concurrent.CancellationException()))
        assertEquals(PrepOutcome.Cancelled, classifyPrepFailure(InterruptedException()))
    }

    @Test
    fun `el motivo de un fallo inesperado conserva el tipo y el mensaje`() {
        val outcome = classifyPrepFailure(IllegalStateException("estado roto"))
        assertEquals("IllegalStateException: estado roto", (outcome as PrepOutcome.Permanent).reason)
    }
}
