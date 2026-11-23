package com.yeivikas.olyzecs.engine.audio.nativeengine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * El planificador con HILOS REALES y trabajos de mentira. La lógica de estados ya está comprobada sin hilos en [SourcePrepQueueTest];
 * aquí se comprueba lo que solo existe con hilos: que una larga no bloquea a una corta, que un reintento ocurre solo, que cancelar
 * suelta una preparación en curso y que un oyente roto no mata al trabajador.
 *
 * Toda espera usa cerrojos (CountDownLatch) con tope de 10 s: una prueba que falla lo hace rápido y con mensaje, no se cuelga.
 */
class SourcePrepSchedulerTest {

    private val created = ArrayList<SourcePrepScheduler<String>>()

    @After
    fun stopThreads() {
        created.forEach { it.close() }
    }

    private fun scheduler(
        maxAttempts: Int = 3,
        work: (SourcePrepQueue.Task<String>) -> PrepOutcome,
        onSettled: (String, PrepOutcome) -> Unit = { _, _ -> },
        onChanged: () -> Unit = {},
        onInternalError: (String, Throwable) -> Unit = { _, _ -> }
    ): SourcePrepScheduler<String> =
        SourcePrepScheduler(
            maxAttempts = maxAttempts,
            backoffMs = { 1L }, // reintentos casi inmediatos: la prueba no debe esperar
            work = work,
            onSettled = onSettled,
            onChanged = onChanged,
            onInternalError = onInternalError
        ).also { created += it }

    private fun CountDownLatch.awaitOrFail(message: String) = assertTrue(message, await(10, TimeUnit.SECONDS))

    @Test
    fun `una fuente corta termina aunque una larga siga en curso`() {
        val longStarted = CountDownLatch(1)
        val releaseLong = CountDownLatch(1)
        val shortDone = CountDownLatch(1)
        val s = scheduler(
            work = { task ->
                if (task.key == "larga") {
                    longStarted.countDown()
                    releaseLong.await(10, TimeUnit.SECONDS)
                }
                PrepOutcome.Success
            },
            onSettled = { key, _ -> if (key == "corta") shortDone.countDown() }
        )

        s.request("larga", "p", PrepLane.LONG)
        longStarted.awaitOrFail("la larga no llegó a empezar")
        s.request("corta", "p", PrepLane.SHORT)

        shortDone.awaitOrFail("la corta quedó esperando a la larga")
        assertEquals(SourcePrepStatus.Preparing(attempt = 1, running = true), s.status("larga"))
        releaseLong.countDown()
    }

    @Test
    fun `nunca hay dos preparaciones largas a la vez`() {
        val running = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        val done = CountDownLatch(3)
        val s = scheduler(
            work = {
                val now = running.incrementAndGet()
                maxSeen.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                Thread.sleep(30)
                running.decrementAndGet()
                PrepOutcome.Success
            },
            onSettled = { _, _ -> done.countDown() }
        )
        s.request("l1", "p", PrepLane.LONG)
        s.request("l2", "p", PrepLane.LONG)
        s.request("l3", "p", PrepLane.LONG)
        done.awaitOrFail("las largas no terminaron")
        assertEquals(1, maxSeen.get())
    }

    @Test
    fun `un fallo transitorio se reintenta solo y termina bien`() {
        val calls = AtomicInteger(0)
        val outcomes = Collections.synchronizedList(ArrayList<PrepOutcome>())
        val finished = CountDownLatch(1)
        val s = scheduler(
            work = {
                if (calls.incrementAndGet() < 3) PrepOutcome.Transient("disco") else PrepOutcome.Success
            },
            onSettled = { _, outcome ->
                outcomes += outcome
                if (outcome is PrepOutcome.Success) finished.countDown()
            }
        )

        s.request("a", "p", PrepLane.SHORT)
        finished.awaitOrFail("no llegó a prepararse tras los reintentos")

        assertEquals(3, calls.get())
        assertEquals(listOf(PrepOutcome.Transient("disco"), PrepOutcome.Transient("disco"), PrepOutcome.Success), outcomes.toList())
        assertNull(s.status("a")) // lista: ya no es una fuente pendiente
    }

    @Test
    fun `una fuente que agota sus intentos queda fallida, no se ejecuta mas y retry la reabre`() {
        val calls = AtomicInteger(0)
        val firstRound = CountDownLatch(2)
        val reopened = CountDownLatch(3)
        val s = scheduler(
            maxAttempts = 2,
            work = {
                calls.incrementAndGet()
                firstRound.countDown()
                reopened.countDown()
                PrepOutcome.Transient("roto")
            }
        )

        s.request("a", "p", PrepLane.SHORT)
        firstRound.awaitOrFail("no hizo los dos intentos")
        waitUntil("no quedó fallida") { s.status("a") is SourcePrepStatus.Failed }
        assertEquals(SourcePrepStatus.Failed(attempts = 2, reason = "roto"), s.status("a"))

        // Pedirla otra vez NO la reabre (eso solo lo hace el usuario con retry).
        s.request("a", "p", PrepLane.SHORT)
        Thread.sleep(150)
        assertEquals(2, calls.get())

        assertTrue(s.retry("a"))
        reopened.awaitOrFail("retry no volvió a ejecutarla")
        assertTrue(calls.get() >= 3)
    }

    @Test
    fun `retain cancela una preparacion en curso`() {
        val started = CountDownLatch(1)
        val settled = CountDownLatch(1)
        val outcome = arrayOfNulls<PrepOutcome>(1)
        val s = scheduler(
            work = { task ->
                started.countDown()
                while (!task.isCancelled()) Thread.sleep(2)
                PrepOutcome.Cancelled
            },
            onSettled = { _, o ->
                outcome[0] = o
                settled.countDown()
            }
        )

        s.request("a", "p", PrepLane.SHORT)
        started.awaitOrFail("no empezó")
        s.retain(emptySet()) // el clip se borró

        settled.awaitOrFail("la preparación cancelada no se soltó")
        assertEquals(PrepOutcome.Cancelled, outcome[0])
        assertNull(s.status("a"))
    }

    @Test
    fun `un trabajo que lanza una excepcion se clasifica y no mata al hilo`() {
        val outcomes = Collections.synchronizedList(ArrayList<Pair<String, PrepOutcome>>())
        val both = CountDownLatch(2)
        val s = scheduler(
            maxAttempts = 1,
            work = { task -> if (task.key == "io") throw java.io.IOException("E/S") else PrepOutcome.Success },
            onSettled = { key, o ->
                outcomes += key to o
                both.countDown()
            }
        )
        s.request("io", "p", PrepLane.SHORT)
        s.request("ok", "p", PrepLane.SHORT) // si el hilo hubiera muerto, esta nunca terminaría
        both.awaitOrFail("el hilo murió con la excepción")
        assertTrue(outcomes.any { it.first == "io" && it.second is PrepOutcome.Transient })
        assertTrue(outcomes.any { it.first == "ok" && it.second is PrepOutcome.Success })
    }

    @Test
    fun `un oyente que falla se informa y no mata al hilo`() {
        val internalErrors = Collections.synchronizedList(ArrayList<String>())
        val second = CountDownLatch(1)
        val s = scheduler(
            work = { PrepOutcome.Success },
            onSettled = { key, _ ->
                if (key == "mala") throw IllegalStateException("oyente roto")
                second.countDown()
            },
            onInternalError = { key, _ -> internalErrors += key }
        )
        s.request("mala", "p", PrepLane.SHORT)
        s.request("buena", "p", PrepLane.SHORT)
        second.awaitOrFail("el hilo murió por el fallo del oyente")
        assertEquals(listOf("mala"), internalErrors.toList())
    }

    @Test
    fun `tras close no se acepta trabajo nuevo`() {
        val calls = AtomicInteger(0)
        val s = scheduler(work = { calls.incrementAndGet(); PrepOutcome.Success })
        s.close()
        s.request("a", "p", PrepLane.SHORT)
        Thread.sleep(100)
        assertEquals(0, calls.get())
        assertNull(s.status("a"))
    }

    @Test
    fun `onChanged avisa al pedir, al terminar un intento y al descartar, y pending ya refleja el cambio`() {
        val gate = CountDownLatch(1)
        val observed = Collections.synchronizedList(ArrayList<Int>()) // cuántas pendientes veía quien fue avisado
        val s = arrayOfNulls<SourcePrepScheduler<String>>(1)
        val changes = AtomicInteger(0)
        val finished = CountDownLatch(1)
        s[0] = scheduler(
            work = { gate.await(10, TimeUnit.SECONDS); PrepOutcome.Success },
            onChanged = {
                changes.incrementAndGet()
                s[0]?.let { observed += it.pending().size }
                if (s[0]?.pending()?.isEmpty() == true) finished.countDown()
            }
        )

        s[0]?.request("a", "p", PrepLane.SHORT)
        waitUntil("no avisó de la petición") { changes.get() >= 1 }
        assertEquals(1, observed.first()) // al avisar de la petición, la fuente ya está pendiente
        assertEquals(listOf("a"), s[0]?.pending()?.map { it.key })

        gate.countDown() // el trabajo termina bien
        finished.awaitOrFail("no avisó de que terminó")
        assertTrue(s[0]?.pending()?.isEmpty() == true)
    }

    @Test
    fun `onChanged avisa al pedir retry y al descartar con retain`() {
        val changes = AtomicInteger(0)
        val failedOnce = CountDownLatch(1)
        val s = scheduler(
            maxAttempts = 1,
            work = { PrepOutcome.Permanent("x") },
            onSettled = { _, _ -> failedOnce.countDown() },
            onChanged = { changes.incrementAndGet() }
        )
        s.request("a", "p", PrepLane.SHORT)
        failedOnce.awaitOrFail("no falló")
        waitUntil("no quedó fallida") { s.status("a") is SourcePrepStatus.Failed }

        val beforeRetry = changes.get()
        assertTrue(s.retry("a"))
        assertTrue("retry debe avisar", changes.get() > beforeRetry)

        waitUntil("no volvió a fallar") { s.status("a") is SourcePrepStatus.Failed }
        val beforeRetain = changes.get()
        s.retain(emptySet()) // el clip se borró
        assertTrue("descartar una fallida debe avisar", changes.get() > beforeRetain)
        assertTrue(s.pending().isEmpty())
    }

    @Test
    fun `pedir una clave que ya existe no genera avisos de mas`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val changes = AtomicInteger(0)
        val s = scheduler(
            work = { started.countDown(); release.await(10, TimeUnit.SECONDS); PrepOutcome.Success },
            onChanged = { changes.incrementAndGet() }
        )
        s.request("a", "p", PrepLane.SHORT)
        started.awaitOrFail("no empezó")
        val before = changes.get()
        repeat(20) { s.request("a", "p", PrepLane.SHORT) } // la vista previa pregunta sin parar
        assertEquals("no debe haber avisos por peticiones repetidas", before, changes.get())
        release.countDown()
    }

    @Test
    fun `un oyente de cambios que falla se informa y no rompe al planificador`() {
        val internalErrors = Collections.synchronizedList(ArrayList<String>())
        val done = CountDownLatch(1)
        val s = scheduler(
            work = { PrepOutcome.Success },
            onSettled = { _, _ -> done.countDown() },
            onChanged = { throw IllegalStateException("oyente roto") },
            onInternalError = { key, _ -> internalErrors += key }
        )
        s.request("a", "p", PrepLane.SHORT)
        done.awaitOrFail("el planificador dejó de funcionar por el fallo del oyente")
        assertTrue(internalErrors.isNotEmpty())
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition()) {
            assertTrue(message, System.nanoTime() < deadline)
            Thread.sleep(2)
        }
    }
}
