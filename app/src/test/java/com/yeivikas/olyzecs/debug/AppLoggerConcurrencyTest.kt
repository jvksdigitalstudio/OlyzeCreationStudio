package com.yeivikas.olyzecs.debug

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test OBLIGATORIO de la Fase 4.2 — punto 22/25.I del prompt maestro:
 * [AppLogger] es un `object` (singleton) al que distintos módulos
 * llaman "sin importar en qué hilo" (ver su propio KDoc) — antes usaba un
 * único `SimpleDateFormat` compartido para formatear timestamps, que NO
 * es thread-safe. Este test dispara `AppLogger.e(...)` y
 * `AppLogger.formatAllForCopy()` (que formatea timestamps) desde muchos
 * hilos a la vez y verifica que:
 *  1. Ninguna llamada concurrente lanza una excepción no controlada
 *     (que antes sí podía pasar con `SimpleDateFormat` compartido bajo
 *     concurrencia real).
 *  2. Cada timestamp generado respeta el patrón esperado
 *     (`yyyy-MM-dd HH:mm:ss.SSS`) — un `SimpleDateFormat` corrompido por
 *     una carrera de datos puede producir texto con el patrón roto en
 *     vez de lanzar, así que se verifica también el contenido, no solo
 *     la ausencia de excepción.
 *
 * No requiere `AppLogger.init(context)`: sin `Context`, `persist()` es
 * un no-op seguro (ver `persistBlocking`, que retorna temprano si
 * `appContext == null`) y `Log.e/w/i` está envuelto en `runCatching` a
 * propósito para no depender de Robolectric — ver KDoc de `record()`.
 */
class AppLoggerConcurrencyTest {

    private val timestampPattern = Regex("""\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})]""")

    @Test
    fun `formatear timestamps desde muchos hilos a la vez no lanza y produce texto bien formado`() {
        val threadCount = 16
        val iterationsPerThread = 50
        val pool = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val failures = AtomicInteger(0)
        val malformed = AtomicInteger(0)

        repeat(threadCount) { threadIndex ->
            pool.execute {
                try {
                    startLatch.await()
                    repeat(iterationsPerThread) { i ->
                        AppLogger.e("ConcurrencyTest", "hilo=$threadIndex iter=$i")
                        val text = AppLogger.formatAllForCopy()
                        if (!timestampPattern.containsMatchIn(text)) {
                            malformed.incrementAndGet()
                        }
                    }
                } catch (_: Throwable) {
                    failures.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(30, TimeUnit.SECONDS)
        pool.shutdown()

        assertTrue("los hilos no terminaron a tiempo", finished)
        assertTrue("hubo excepciones durante el formateo concurrente de timestamps: ${failures.get()}", failures.get() == 0)
        assertTrue("se generaron timestamps con el patrón corrompido: ${malformed.get()}", malformed.get() == 0)

        AppLogger.clear()
    }
}
