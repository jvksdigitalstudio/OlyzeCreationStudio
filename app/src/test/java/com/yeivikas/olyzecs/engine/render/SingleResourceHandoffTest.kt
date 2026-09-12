package com.yeivikas.olyzecs.engine.render

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 3.1 / 3.1.2 — Cierre y hardening de ownership (GPU resources /
 * bitmaps) y de rechazo de resultados obsoletos (content revision).
 *
 * Cubre, en su forma más pura y sin depender de `android.graphics.Bitmap`
 * (ver KDoc de [SingleResourceHandoff] sobre por qué la clase es genérica:
 * para poder testear esto con JUnit normal, sin Robolectric):
 *
 * - Fase 3.1: "Pending Bitmap handoff: Publicación → GL consume → no
 *   doble consumo".
 * - Fase 3.1.2: "ningún resultado con una revisión vieja puede ganarle a
 *   uno con una revisión más nueva" (TEST 1, TEST 2, TEST 8, TEST 10 del
 *   informe de esa fase) y "reemplazar un pendiente sin consumir nunca lo
 *   pierde en silencio" (TEST 3, PROBLEMA 2 del mismo informe).
 */
class SingleResourceHandoffTest {

    // ------------------------------------------------------------------
    // Comportamiento base: publicar / consumir / limpiar
    // ------------------------------------------------------------------

    @Test
    fun `sin publicar nada, takeIfCurrent devuelve Empty`() {
        val handoff = SingleResourceHandoff<String>()
        assertEquals(SingleResourceHandoff.Consumption.Empty, handoff.takeIfCurrent(currentRevision = 0))
    }

    @Test
    fun `publicar y consumir con la misma revision devuelve Ready y deja el slot vacio`() {
        val handoff = SingleResourceHandoff<String>()
        handoff.publish("frame-1", revision = 5)

        val consumption = handoff.takeIfCurrent(currentRevision = 5)
        assertEquals(SingleResourceHandoff.Consumption.Ready("frame-1"), consumption)
        // Una sola operación de toma: el segundo intento encuentra el slot vacío.
        assertEquals(SingleResourceHandoff.Consumption.Empty, handoff.takeIfCurrent(currentRevision = 5))
    }

    @Test
    fun `peek no toma posesion del recurso`() {
        val handoff = SingleResourceHandoff<String>()
        handoff.publish("frame-1", revision = 1)

        assertEquals("frame-1", handoff.peek())
        assertEquals("frame-1", handoff.peek()) // repetible: no consume

        // Sigue disponible para una toma real después de solo mirar.
        assertEquals(SingleResourceHandoff.Consumption.Ready("frame-1"), handoff.takeIfCurrent(currentRevision = 1))
    }

    @Test
    fun `clear devuelve el pendiente sin publicar uno nuevo`() {
        val handoff = SingleResourceHandoff<String>()
        handoff.publish("frame-1", revision = 1)

        assertEquals("frame-1", handoff.clear())
        assertNull(handoff.clear())
        assertEquals(SingleResourceHandoff.Consumption.Empty, handoff.takeIfCurrent(currentRevision = 1))
    }

    @Test
    fun `takeRaw toma sin validar revision`() {
        val handoff = SingleResourceHandoff<String>()
        handoff.publish("frame-1", revision = 99) // revisión completamente distinta a lo que se compare después

        assertEquals("frame-1", handoff.takeRaw())
        assertNull(handoff.takeRaw())
    }

    // ------------------------------------------------------------------
    // PROBLEMA 2 (Fase 3.1.2) — reemplazar un pendiente sin consumir
    // nunca debe perderlo en silencio: publish() devuelve el anterior.
    // ------------------------------------------------------------------

    @Test
    fun `TEST 3 - publish devuelve el valor anterior sin consumir para que el llamador lo disponga`() {
        val handoff = SingleResourceHandoff<String>()
        handoff.publish("A", revision = 1)

        val discardedA = handoff.publish("B", revision = 2)

        assertEquals("A", discardedA) // "A" nunca se pierde en silencio: vuelve al llamador
        assertEquals(SingleResourceHandoff.Consumption.Ready("B"), handoff.takeIfCurrent(currentRevision = 2)) // "B" sigue pendiente
    }

    @Test
    fun `publish sobre un slot vacio no devuelve nada`() {
        val handoff = SingleResourceHandoff<String>()
        assertNull(handoff.publish("A", revision = 1))
    }

    // ------------------------------------------------------------------
    // PROBLEMA 1 (Fase 3.1.2) — ningún resultado obsoleto puede
    // convertirse en un recurso válido.
    // ------------------------------------------------------------------

    @Test
    fun `TEST 1 - un resultado etiquetado con una revision vieja se rechaza como Stale`() {
        val handoff = SingleResourceHandoff<String>()
        // Layer en revisión 1, arranca un decode (tarda), se etiqueta con esa revisión.
        handoff.publish("contenido-revision-1", revision = 1)

        // Mientras tanto la capa avanzó a revisión 2 (cambió sourceUri de nuevo).
        val consumption = handoff.takeIfCurrent(currentRevision = 2)

        assertTrue(consumption is SingleResourceHandoff.Consumption.Stale)
        assertEquals("contenido-revision-1", (consumption as SingleResourceHandoff.Consumption.Stale).discarded)
        // Se retiró del slot igual (nunca queda dando vueltas para siempre).
        assertEquals(SingleResourceHandoff.Consumption.Empty, handoff.takeIfCurrent(currentRevision = 2))
    }

    @Test
    fun `TEST 2 - multiples resultados obsoletos, solo el ultimo publicado con la revision vigente gana`() {
        val handoff = SingleResourceHandoff<String>()

        // Tres decodes "compiten" por el mismo slot, en cualquier orden de llegada.
        // A revision 1 llega primero...
        val discardedNone = handoff.publish("A", revision = 1)
        assertNull(discardedNone)
        // ...pero antes de que nadie lo consuma, llega B revision 2...
        val discardedA = handoff.publish("B", revision = 2)
        assertEquals("A", discardedA)
        // ...y después C revision 3, la vigente.
        val discardedB = handoff.publish("C", revision = 3)
        assertEquals("B", discardedB)

        // Solo C (la última publicación, que además coincide con la revisión vigente) puede ganar.
        assertEquals(SingleResourceHandoff.Consumption.Ready("C"), handoff.takeIfCurrent(currentRevision = 3))
    }

    @Test
    fun `TEST 8 - una textura pendiente de una revision vieja se rechaza frente a la revision vigente`() {
        val handoff = SingleResourceHandoff<String>()
        handoff.publish("textura-v1", revision = 1)
        assertEquals(SingleResourceHandoff.Consumption.Ready("textura-v1"), handoff.takeIfCurrent(currentRevision = 1))

        // La capa avanza a revisión 2, pero justo ahora llega (tarde) un
        // resultado todavía etiquetado con la revisión 1 vieja — p. ej. un
        // redecode que había arrancado antes del cambio de `sourceUri`.
        // Nunca debe poder convertirse en la textura visible de la revisión 2.
        handoff.publish("resultado-tardio-v1", revision = 1)
        val consumption = handoff.takeIfCurrent(currentRevision = 2)

        assertTrue(consumption is SingleResourceHandoff.Consumption.Stale)
        assertEquals("resultado-tardio-v1", (consumption as SingleResourceHandoff.Consumption.Stale).discarded)
    }

    @Test
    fun `TEST 10 - reemplazos concurrentes rapidos, solo el ultimo puede convertirse en recurso valido`() {
        val handoff = SingleResourceHandoff<String>()
        val discarded = mutableListOf<String>()

        // A -> B -> C -> D, simulando 4 decodes que compiten por el mismo slot.
        discarded += listOfNotNull(handoff.publish("A", revision = 1))
        discarded += listOfNotNull(handoff.publish("B", revision = 2))
        discarded += listOfNotNull(handoff.publish("C", revision = 3))
        discarded += listOfNotNull(handoff.publish("D", revision = 4))

        assertEquals(listOf("A", "B", "C"), discarded) // ninguno se perdió en silencio
        assertEquals(SingleResourceHandoff.Consumption.Ready("D"), handoff.takeIfCurrent(currentRevision = 4))
    }

    // ------------------------------------------------------------------
    // Concurrencia real (multi-hilo) — mismo espíritu que el test de la
    // Fase 3.1 original: nunca doble consumo, nunca un recurso perdido.
    // ------------------------------------------------------------------

    @Test
    fun `bajo concurrencia real ningun publish se consume dos veces ni se pierde sin pasar por publish o takeIfCurrent`() {
        val handoff = SingleResourceHandoff<Int>()
        val producerCount = 8
        val readyLatch = CountDownLatch(producerCount)
        val goLatch = CountDownLatch(1)
        val consumedCount = AtomicInteger(0)
        val discardedByPublishCount = AtomicInteger(0)

        val threads = (0 until producerCount).map { i ->
            Thread {
                readyLatch.countDown()
                goLatch.await()
                // Todos publican "a la vez", cada uno con su propia revisión creciente.
                val discarded = handoff.publish(i, revision = i)
                if (discarded != null) discardedByPublishCount.incrementAndGet()
            }
        }
        threads.forEach { it.start() }
        readyLatch.await()
        goLatch.countDown()
        threads.forEach { it.join() }

        // Consumidor final: toma lo que haya quedado, sea lo que sea (no importa el
        // orden real de llegada de los hilos, que no está garantizado).
        var totalReadyOrStale = 0
        for (rev in 0 until producerCount) {
            when (handoff.takeIfCurrent(rev)) {
                is SingleResourceHandoff.Consumption.Ready -> {
                    consumedCount.incrementAndGet()
                    totalReadyOrStale++
                }
                is SingleResourceHandoff.Consumption.Stale -> totalReadyOrStale++
                SingleResourceHandoff.Consumption.Empty -> Unit
            }
        }

        // Invariante real, independiente del orden de ejecución de los hilos:
        // cada publish() exitoso deja EXACTAMENTE un valor en el slot en un
        // momento dado — al final queda como máximo UNO por consumir, y la suma
        // de "descartados por publish" + "lo que se pudo tomar al final" debe
        // ser igual a la cantidad de publicaciones realizadas.
        assertEquals(producerCount, discardedByPublishCount.get() + totalReadyOrStale)
        assertTrue("nunca se consume más de un valor final del slot", consumedCount.get() <= 1)
    }
}
