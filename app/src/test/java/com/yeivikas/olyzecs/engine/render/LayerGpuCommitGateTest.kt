package com.yeivikas.olyzecs.engine.render

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * FASE 3.1.3-R3 — Real Atomic GPU Commit Gate.
 *
 * A diferencia de [LayerTextureRegistryScenarioTest] (que reproduce el
 * protocolo completo de `GLRenderer` con un fake DELIBERADAMENTE
 * secuencial, porque no se puede levantar un contexto EGL/GLES20 real en
 * JUnit puro), este archivo SÍ usa hilos JVM reales
 * (`java.lang.Thread`/`CountDownLatch`) contra la instancia REAL de
 * [LayerGpuCommitGate] (no una copia ni una simulación de su lógica).
 * Es, a propósito, el único archivo de toda la Fase 3.1.3 que puede
 * demostrar exclusión mutua verdadera en vez de un orden de eventos
 * simulado — ver la sección 15 ("Limitaciones de tests JVM") de
 * `docs/fases/FASE_3_1_3_R3_COMMIT_GATE.md` para el alcance exacto de lo
 * que SÍ y NO se puede probar sin Robolectric/instrumentación.
 *
 * Todos los tests usan un timeout explícito al esperar en latches — un
 * defecto real en el gate (por ejemplo, un `withGate` que no libera en
 * el camino de excepción) se manifiesta acá como un test que CUELGA o
 * falla por timeout, nunca como un falso verde.
 */
class LayerGpuCommitGateTest {

    private val awaitTimeoutSeconds = 5L

    // ------------------------------------------------------------------
    // Comportamiento básico del gate en sí (sin modelar ningún protocolo
    // de Layer/GPU todavía) — la base sobre la que se apoyan los tests
    // de protocolo de más abajo.
    // ------------------------------------------------------------------

    @Test
    fun `un segundo hilo no puede adquirir el gate mientras el primero lo mantiene`() {
        val gate = LayerGpuCommitGate()
        val holderReady = CountDownLatch(1)
        val releaseHolder = CountDownLatch(1)
        val secondThreadResult = AtomicReference<Boolean>()

        val holder = Thread {
            gate.acquire()
            holderReady.countDown()
            // Mantiene el gate tomado hasta que el test lo autorice a soltarlo.
            releaseHolder.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
            gate.release()
        }
        holder.start()
        assertTrue("el primer hilo no llegó a adquirir el gate a tiempo", holderReady.await(awaitTimeoutSeconds, TimeUnit.SECONDS))

        val second = Thread {
            secondThreadResult.set(gate.tryAcquire())
        }
        second.start()
        second.join(awaitTimeoutSeconds * 1000)

        assertEquals("un segundo hilo NO debe poder adquirir el gate mientras el primero lo mantiene", false, secondThreadResult.get())

        releaseHolder.countDown()
        holder.join(awaitTimeoutSeconds * 1000)

        // Tras liberar, el gate vuelve a estar disponible para cualquiera.
        assertTrue("tras liberar, el gate debe volver a estar disponible", gate.tryAcquire())
        gate.release()
    }

    @Test
    fun `tras liberar el gate un segundo hilo puede completar su seccion critica`() {
        val gate = LayerGpuCommitGate()
        val firstEnteredGate = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondCompleted = CountDownLatch(1)
        // FASE 3.1.3-R3.1.2 — misma debilidad detectada en el informe de
        // esta fase para el test de `persistNow`, corregida acá con el
        // mismo criterio: sin esta señal, la comprobación negativa
        // justo después de `second.start()` no distingue entre "el gate
        // está bloqueando correctamente al segundo hilo" y "el segundo
        // hilo todavía ni fue planificado por el SO" — dos causas
        // distintas con el mismo resultado observable.
        val secondAttemptingGate = CountDownLatch(1)

        val first = Thread {
            gate.withGate {
                firstEnteredGate.countDown()
                releaseFirst.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
            }
        }
        first.start()
        assertTrue(firstEnteredGate.await(awaitTimeoutSeconds, TimeUnit.SECONDS))

        val second = Thread {
            secondAttemptingGate.countDown()
            gate.withGate {
                secondCompleted.countDown()
            }
        }
        second.start()
        // Confirma, con una señal explícita — no por casualidad de
        // scheduling — que el segundo hilo efectivamente llegó al punto
        // de adquisición del gate antes de la comprobación negativa.
        assertTrue(secondAttemptingGate.await(awaitTimeoutSeconds, TimeUnit.SECONDS))

        // FASE 3.1.3-R3.1.3 — El primer hilo sigue reteniendo el gate acá
        // (todavía no se liberó `releaseFirst`, ver más abajo). Eso, por
        // la propia semántica de exclusión mutua de `ReentrantLock`, hace
        // LÓGICAMENTE IMPOSIBLE que cualquier otro hilo esté ejecutando
        // (ni haya podido terminar de ejecutar) su propia sección crítica
        // en este instante — no es algo que dependa de cuánto tiempo se
        // espere, es una garantía del lock mismo. `tryAcquire()` expone
        // esa garantía sin bloquear ni esperar ni un milisegundo: o el
        // gate está libre (lo cual sería un bug real de exclusión) o no
        // lo está — la respuesta es inmediata, nunca "todavía no lo sé".
        val gateAppearedFreeWhileFirstHeldIt = gate.tryAcquire()
        if (gateAppearedFreeWhileFirstHeldIt) {
            // Si esto llegara a pasar sería un bug real del gate — se
            // libera igual para no dejar el lock tomado por el hilo de
            // test y enmascarar además el fallo de la assertion de abajo
            // con un deadlock posterior.
            gate.release()
        }
        assertFalse(
            "el gate no debe estar disponible mientras el primer hilo lo mantiene retenido",
            gateAppearedFreeWhileFirstHeldIt
        )
        // Consecuencia directa de lo anterior, no una observación aparte:
        // si el gate está tomado por el primer hilo, el segundo no pudo
        // haber pasado por `gate.withGate { secondCompleted.countDown() }`
        // todavía. Se lee el latch de forma inmediata (sin esperar nada)
        // porque el propio lock ya garantizó el resultado.
        assertEquals(
            "el segundo hilo no puede haber completado su sección crítica mientras el gate sigue tomado por el primero",
            1L,
            secondCompleted.count
        )

        releaseFirst.countDown()
        first.join(awaitTimeoutSeconds * 1000)

        assertTrue("el segundo hilo debió completar apenas el primero liberó el gate", secondCompleted.await(awaitTimeoutSeconds, TimeUnit.SECONDS))
    }

    @Test
    fun `withGate libera el gate incluso si el bloque lanza una excepcion`() {
        val gate = LayerGpuCommitGate()

        try {
            gate.withGate { throw IllegalStateException("commit fallido simulado") }
            fail("se esperaba que la excepción se propagara")
        } catch (expected: IllegalStateException) {
            // esperado
        }

        // Si el gate hubiera quedado tomado, esto bloquearía para siempre
        // — por eso se usa tryAcquire, no acquire().
        assertTrue("una excepción dentro de withGate NO debe dejar el gate tomado para siempre", gate.tryAcquire())
        gate.release()
    }

    @Test
    fun `el mismo hilo puede readquirir el gate sin bloquearse a si mismo (reentrancia)`() {
        // Necesario en producción: `discardChangesAndExit` adquiere el
        // gate alrededor de la reconciliación completa Y `applyTo` (que
        // corre DENTRO de esa misma reconciliación, en el mismo hilo)
        // adquiere el gate otra vez para su propia mutación de identidad.
        val gate = LayerGpuCommitGate()
        var innerRan = false

        gate.withGate {
            assertTrue(gate.isHeldByCurrentThread)
            gate.withGate {
                innerRan = true
                assertTrue(gate.isHeldByCurrentThread)
            }
        }

        assertTrue(innerRan)
        assertFalse("tras salir de ambos withGate, el gate no debe seguir tomado", gate.isLocked)
    }

    // ------------------------------------------------------------------
    // TEST OBLIGATORIO — sección 26 del informe de esta fase:
    // "validation → mutation attempts → commit", demostrando que la
    // mutación NO puede interponerse DENTRO de la sección crítica real
    // (no solo "mutation before validation", que ya cubría R2).
    // ------------------------------------------------------------------

    /**
     * Modelo mínimo y fiel de la interacción real entre
     * `GLRenderer.uploadTextureIfNeeded` y una mutación de
     * `EditorViewModel` — usando el `LayerGpuCommitGate` REAL, no una
     * copia de su lógica. `liveInstanceId` simula `Layer` (una
     * referencia mutable compartida); "GL" valida por identidad de
     * instancia exactamente como `currentLayerIfStillRequested`.
     */
    private class FakeProject {
        @Volatile var liveInstanceId = "instance-1"
    }

    @Test
    fun `test de exclusion real - la mutacion no puede interponerse entre validacion y commit`() {
        val gate = LayerGpuCommitGate()
        val project = FakeProject()
        val capturedInstanceId = project.liveInstanceId // lo que "GL" capturó al empezar el upload

        val glEnteredGate = CountDownLatch(1)
        val glPausedAtCommit = CountDownLatch(1)
        val allowGlToCommit = CountDownLatch(1)
        val commitResult = AtomicReference<Boolean>()
        val mutationAttemptStarted = CountDownLatch(1)
        // FASE 3.1.3-R3.1.1 — CountDownLatch en vez de AtomicBoolean a
        // propósito: permite reemplazar "Thread.sleep(200) + leer el
        // flag" por una espera ACOTADA sobre el latch mismo
        // (`await(timeout)`), que retorna EN EL INSTANTE en que la
        // mutación realmente se completa (si es que llega a completarse
        // dentro del bound) en vez de muestrear a ciegas después de un
        // tiempo fijo — ver el mismo patrón ya usado más arriba en
        // `tras liberar el gate un segundo hilo puede completar su
        // seccion critica`.
        val mutationCompleted = CountDownLatch(1)

        // "Hilo de GL": acquire → validate → PAUSA dentro de la sección
        // crítica (simulando el instante justo antes de escribir el
        // registro) → commit → release.
        val glThread = Thread {
            gate.withGate {
                glEnteredGate.countDown()
                val stillValid = project.liveInstanceId == capturedInstanceId
                glPausedAtCommit.countDown()
                // Le da tiempo al hilo B a INTENTAR mutar mientras seguimos
                // dentro de la sección crítica — si el gate funciona, B no
                // puede completar su mutación todavía en este punto.
                allowGlToCommit.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
                commitResult.set(stillValid)
            }
        }

        // "Hilo Main": intenta mutar la identidad viva (equivalente a
        // replaceLayer/removeLayer) usando EXACTAMENTE el mismo gate.
        val mutationThread = Thread {
            mutationAttemptStarted.countDown()
            gate.withGate {
                project.liveInstanceId = "instance-2"
                mutationCompleted.countDown()
            }
        }

        glThread.start()
        assertTrue(glEnteredGate.await(awaitTimeoutSeconds, TimeUnit.SECONDS))
        assertTrue(glPausedAtCommit.await(awaitTimeoutSeconds, TimeUnit.SECONDS))

        mutationThread.start()
        assertTrue(mutationAttemptStarted.await(awaitTimeoutSeconds, TimeUnit.SECONDS))

        // FASE 3.1.3-R3.1.3 — GL sigue reteniendo el gate acá (todavía no
        // liberó `allowGlToCommit`, más abajo). En vez de darle a la
        // mutación una ventana acotada de tiempo para "demostrar" que no
        // puede colarse (R3.1.1/R3.1.2), se usa `tryAcquire()`: no
        // bloqueante, no depende de ningún reloj. Si el gate estuviera
        // realmente libre (bug de exclusión), esto lo revela en el acto;
        // si no lo está, la respuesta también es inmediata.
        val gateAppearedFreeWhileGlHeldIt = gate.tryAcquire()
        if (gateAppearedFreeWhileGlHeldIt) {
            // Ver la misma nota en el test de arriba: se libera para no
            // dejar el lock tomado por el hilo de test.
            gate.release()
        }
        assertFalse(
            "el gate no debe estar disponible mientras el commit GL lo mantiene retenido",
            gateAppearedFreeWhileGlHeldIt
        )
        // Consecuencia directa, no una segunda observación independiente:
        // si el gate sigue tomado por GL, la mutación no pudo haber
        // pasado todavía por `gate.withGate { mutationCompleted.countDown() }`.
        assertEquals(
            "la mutación no debe poder completarse mientras el commit gate sigue adquirido por el commit en curso",
            1L,
            mutationCompleted.count
        )

        // Se autoriza a GL a terminar su commit y liberar el gate.
        allowGlToCommit.countDown()
        glThread.join(awaitTimeoutSeconds * 1000)
        assertTrue(
            "la mutación debió completarse apenas el commit liberó el gate",
            mutationCompleted.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
        )
        mutationThread.join(awaitTimeoutSeconds * 1000)

        // El commit vio la identidad SIN mutar (la mutación estaba
        // bloqueada esperando el gate) — se autorizó correctamente.
        assertEquals(true, commitResult.get())
        // Y la mutación, bloqueada mientras tanto, se completó recién
        // DESPUÉS de que el gate quedó libre.
        assertEquals("instance-2", project.liveInstanceId)
    }

    // ------------------------------------------------------------------
    // TEST REMOVE — sección 28: ambos órdenes válidos (remove-before-commit
    // y commit-before-remove), ninguno produce un commit obsoleto.
    // ------------------------------------------------------------------

    private class FakeRegistry {
        val committed = AtomicReference<String?>(null)
        val rolledBack = AtomicInteger(0)
    }

    private fun runRemoveRace(removeWinsFirst: Boolean): FakeRegistry {
        val gate = LayerGpuCommitGate()
        val project = FakeProject()
        val registry = FakeRegistry()
        val capturedInstanceId = project.liveInstanceId

        val firstReady = CountDownLatch(1)
        val letSecondGo = CountDownLatch(1)

        // Quien deba ganar la carrera adquiere el gate primero y lo
        // mantiene hasta que el test autoriza al segundo a intentar.
        val firstAction: () -> Unit
        val secondAction: () -> Unit

        if (removeWinsFirst) {
            firstAction = {
                gate.withGate {
                    project.liveInstanceId = "removed"
                    firstReady.countDown()
                    letSecondGo.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
                }
            }
            secondAction = {
                gate.withGate {
                    if (project.liveInstanceId == capturedInstanceId) {
                        registry.committed.set(capturedInstanceId)
                    } else {
                        registry.rolledBack.incrementAndGet()
                    }
                }
            }
        } else {
            firstAction = {
                gate.withGate {
                    if (project.liveInstanceId == capturedInstanceId) {
                        registry.committed.set(capturedInstanceId)
                    } else {
                        registry.rolledBack.incrementAndGet()
                    }
                    firstReady.countDown()
                    letSecondGo.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
                }
            }
            secondAction = {
                gate.withGate {
                    project.liveInstanceId = "removed"
                }
            }
        }

        val t1 = Thread(firstAction)
        val t2Started = CountDownLatch(1)
        val t2 = Thread {
            t2Started.countDown()
            secondAction()
        }

        t1.start()
        assertTrue(firstReady.await(awaitTimeoutSeconds, TimeUnit.SECONDS))
        t2.start()
        assertTrue(t2Started.await(awaitTimeoutSeconds, TimeUnit.SECONDS))
        // FASE 3.1.3-R3.1.1 — SIN Thread.sleep acá: no hace falta. `t1`
        // YA tiene el gate adquirido (confirmado arriba por
        // `firstReady.await`, que solo se libera DESDE DENTRO de
        // `gate.withGate { ... }`) desde ANTES de que `t2` siquiera
        // arranque — por construcción, `t2` no puede completar su acción
        // hasta que `t1` libere el gate vía `letSecondGo`, sin importar
        // en qué momento el scheduler del SO decida ejecutar `t2`. No
        // hay ninguna ventana de tiempo que "esperar": el orden ya está
        // determinado por la posesión del lock, no por el reloj.
        letSecondGo.countDown()
        t1.join(awaitTimeoutSeconds * 1000)
        t2.join(awaitTimeoutSeconds * 1000)

        return registry
    }

    @Test
    fun `caso A - remove gana el gate primero, el commit hace rollback`() {
        val registry = runRemoveRace(removeWinsFirst = true)
        assertNull("un commit posterior a un remove ganador no debe registrar nada", registry.committed.get())
        assertEquals(1, registry.rolledBack.get())
    }

    @Test
    fun `caso B - el commit gana el gate primero, se compromete con exito`() {
        val registry = runRemoveRace(removeWinsFirst = false)
        assertEquals("instance-1", registry.committed.get())
        assertEquals(0, registry.rolledBack.get())
    }

    // ------------------------------------------------------------------
    // FASE 3.1.3-R3.1 — Mutation Path Closure.
    //
    // Los tests de acá abajo cubren específicamente el bypass real que
    // esta fase cierra: `EditorViewModel.persistNow()` mutando
    // `sourceUri` de una capa LIVE fuera de `LayerGpuCommitGate`. No se
    // instancia `EditorViewModel` real (necesita Android/`viewModelScope`/
    // `ProjectStorage`, no disponibles en JUnit puro sin Robolectric —
    // mismo criterio que el resto de esta suite) ni la clase `Layer` real
    // (su constructor pide `android.net.Uri`, que en JUnit puro sin
    // Robolectric lanza en cuanto se invoca cualquier método real de la
    // clase — ver la nota de la clase para más detalle). En cambio,
    // [FakePersistableLayer] reproduce EXACTAMENTE la forma real del
    // problema (un objeto mutable con `sourceUri`/`contentRevision`,
    // compartido entre un hilo "GL" y un hilo "Main") usando tipos de la
    // librería estándar — y usa la instancia REAL de [LayerGpuCommitGate],
    // no una copia de su lógica.
    // ------------------------------------------------------------------

    /** Reproduce, con tipos simples, la forma real de `Layer.sourceUri`/`Layer.contentRevision`. */
    private class FakePersistableLayer(@Volatile var sourceUri: String, @Volatile var contentRevision: Int)

    /**
     * TEST D del informe de FASE 3.1.3-R3.1 ("concurrent GPU commit vs
     * identity mutation"), aplicado específicamente a la forma de
     * `persistNow`: un "commit GPU" (hilo de GL) captura la identidad,
     * entra al gate, revalida, y — mientras sigue DENTRO de esa sección
     * crítica — un hilo "Main" corriendo la lógica de `persistNow`
     * (comprobar `current == original` y, si corresponde, actualizar
     * `sourceUri`) intenta adquirir el MISMO gate.
     *
     * FASE 3.1.3-R3.1.2 — endurecido para demostrar, con una señal
     * explícita (`persistAttemptingGate`, ver más abajo) y no por
     * coincidencia de scheduling, las cuatro propiedades del informe de
     * esta fase:
     *
     * - PROPERTY 1: GL puede mantener el gate durante una sección crítica.
     * - PROPERTY 2: persistNow alcanza el punto de adquisición del gate,
     *   pero NO puede entrar mientras GL lo posee.
     * - PROPERTY 3: cuando GL libera el gate, persistNow finalmente entra.
     * - PROPERTY 4: la mutación de `sourceUri` se ejecuta solamente
     *   después de obtener el gate — nunca antes, nunca a medio escribir
     *   (el commit de GL ve la identidad SIN la normalización aplicada
     *   todavía: se compromete con el URI viejo, consistente).
     */
    @Test
    fun `persistNow - la normalizacion de sourceUri no puede interponerse dentro de un commit GPU en curso`() {
        val gate = LayerGpuCommitGate()
        val layer = FakePersistableLayer(sourceUri = "content://saf/original", contentRevision = 5)
        val originalUriAtSaveStart = layer.sourceUri // lo que persistNow "capturó" antes del IO

        val glEnteredGate = CountDownLatch(1)
        val allowGlToFinish = CountDownLatch(1)
        val committedUri = AtomicReference<String>()
        val committedRevision = AtomicInteger(-1)

        val glThread = Thread {
            val capturedUri = layer.sourceUri
            val capturedRevision = layer.contentRevision
            gate.withGate {
                // revalidación DENTRO del gate, igual que GLRenderer.
                if (layer.sourceUri == capturedUri && layer.contentRevision == capturedRevision) {
                    glEnteredGate.countDown()
                    allowGlToFinish.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
                    committedUri.set(layer.sourceUri)
                    committedRevision.set(layer.contentRevision)
                }
            }
        }

        // FASE 3.1.3-R3.1.1 — CountDownLatch en vez de AtomicBoolean, mismo
        // criterio que el test de exclusión de más arriba: permite
        // reemplazar "Thread.sleep(200) + leer el flag" por una espera
        // ACOTADA sobre el latch mismo.
        val persistNowMutationCompleted = CountDownLatch(1)
        // FASE 3.1.3-R3.1.2 — señal NUEVA: confirma, de forma explícita y
        // determinista, que el hilo de `persistNow` efectivamente LLEGÓ
        // al punto de adquisición del gate — se dispara INMEDIATAMENTE
        // ANTES de `gate.withGate { ... }`, nunca después (ni dentro de
        // la sección crítica, ni después de la mutación). Sin esta señal,
        // la comprobación negativa de más abajo (`assertFalse(...await...)`)
        // no demuestra nada por sí sola: una ventana acotada que vence
        // sin que el latch se libere es indistinguible entre "el gate
        // está correctamente bloqueando a persistNow" y "el hilo de
        // persistNow todavía ni arrancó" — dos causas completamente
        // distintas que, sin esta señal, producían el mismo resultado
        // observable. Ver PROPERTY 2 del informe de esta fase.
        val persistAttemptingGate = CountDownLatch(1)
        val persistNowThread = Thread {
            persistAttemptingGate.countDown()
            gate.withGate {
                // misma forma exacta que el fix real: comprobar identidad
                // capturada al empezar el guardado, y solo entonces mutar.
                if (layer.sourceUri == originalUriAtSaveStart) {
                    layer.sourceUri = "file:///local/normalized.png"
                    // contentRevision NUNCA se toca acá — Objetivo 4.
                }
                persistNowMutationCompleted.countDown()
            }
        }

        glThread.start()
        assertTrue(glEnteredGate.await(awaitTimeoutSeconds, TimeUnit.SECONDS))

        persistNowThread.start()
        // FASE 3.1.3-R3.1.2 — se espera la señal de "llegué al punto de
        // adquisición" ANTES de la comprobación negativa. Esto es lo que
        // convierte la comprobación de abajo en una prueba real de
        // PROPERTY 2 (persistencia alcanza el punto de adquisición pero
        // no puede entrar mientras GL posee el gate) en vez de una
        // coincidencia de scheduling: en este punto sabemos, con
        // certeza — no por suerte de temporización — que el hilo de
        // persistNow ya ejecutó `persistAttemptingGate.countDown()`
        // (la línea INMEDIATAMENTE anterior a `gate.withGate { ... }`)
        // y está, como mínimo, a punto de invocar `lock()` sobre el
        // mismo gate que GL sigue reteniendo.
        assertTrue(
            "persistNow debió llegar al punto de adquisición del gate antes de esta comprobación",
            persistAttemptingGate.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
        )
        // FASE 3.1.3-R3.1.3 — recién ACÁ tiene sentido la comprobación de
        // bloqueo: ya está demostrado que persistNow llegó al punto de
        // adquisición (arriba) Y que GL sigue reteniendo el gate (no se
        // llamó `allowGlToFinish.countDown()` todavía, más abajo).
        //
        // R3.1.1/R3.1.2 resolvían esto con una ventana acotada de 300ms
        // sobre `persistNowMutationCompleted.await(...)`: ya no era un
        // sleep, pero seguía siendo una PRUEBA POR AUSENCIA DENTRO DE UNA
        // VENTANA — si el scheduler del entorno de CI algún día fuera lo
        // bastante lento o estuviera lo bastante saturado, un `await` de
        // 300ms podría vencer sin haberse liberado el latch por una razón
        // completamente distinta de "el gate está bloqueando
        // correctamente" (falso verde) — y, a la inversa, en una máquina
        // muy rápida esos 300ms sencillamente nunca se llegan a ejecutar
        // en la práctica salvo que el gate falle, lo cual es correcto
        // pero sigue sin ser una prueba, es una casualidad de temporización
        // que hasta ahora resultó confiable.
        //
        // La corrección real: `LayerGpuCommitGate.tryAcquire()` (ver su
        // KDoc — expuesto explícitamente para tests de exclusión) permite
        // consultar el estado del lock de forma NO BLOQUEANTE. Como GL ya
        // tiene el gate adquirido (glEnteredGate, arriba) y todavía no lo
        // liberó, `tryAcquire()` debe devolver `false` en el acto — sin
        // esperar nada, sin ventana, sin importar cuán rápido o lento
        // sea el hilo de test o el de persistNow. Que el gate esté
        // efectivamente tomado por GL en este instante hace LÓGICAMENTE
        // IMPOSIBLE que persistNow ya haya ejecutado
        // `gate.withGate { ...; persistNowMutationCompleted.countDown() }`
        // — es una garantía de la propia exclusión mutua del
        // `ReentrantLock`, no una inferencia estadística sobre cuánto
        // tardó el hilo en ser planificado.
        val gateAppearedFreeWhileGlHeldIt = gate.tryAcquire()
        if (gateAppearedFreeWhileGlHeldIt) {
            // Si esto llegara a ocurrir sería un bug real de exclusión en
            // el gate — se libera de todas formas para no dejar el lock
            // tomado por el hilo de test y enmascarar el fallo de la
            // assertion de abajo con un deadlock posterior (glThread
            // nunca podría re-suscribir su propia sección crítica).
            gate.release()
        }
        assertFalse(
            "el gate no debe estar disponible mientras el commit GPU lo mantiene retenido",
            gateAppearedFreeWhileGlHeldIt
        )
        // Consecuencia directa de la comprobación anterior — no una
        // segunda observación independiente ni otra oportunidad para que
        // el scheduling decida el resultado: se lee el latch de forma
        // inmediata (count, sin await) porque el propio lock ya
        // garantizó lo que este valor tiene que ser.
        assertEquals(
            "la actualización de sourceUri de persistNow no debe poder completarse mientras el commit GPU sigue dentro del gate",
            1L,
            persistNowMutationCompleted.count
        )

        allowGlToFinish.countDown()
        glThread.join(awaitTimeoutSeconds * 1000)
        assertTrue(
            "la actualización de persistNow debió completarse apenas el commit liberó el gate",
            persistNowMutationCompleted.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
        )
        persistNowThread.join(awaitTimeoutSeconds * 1000)

        // El commit vio el URI viejo (todavía no normalizado) y se
        // comprometió con ESE valor — consistente, nunca a medio mutar.
        assertEquals("content://saf/original", committedUri.get())
        assertEquals(5, committedRevision.get())
        // Y recién DESPUÉS de que el gate quedó libre, persistNow aplicó
        // la normalización.
        assertEquals("file:///local/normalized.png", layer.sourceUri)
        // OBJETIVO 4 — la normalización NUNCA incrementa contentRevision.
        assertEquals(5, layer.contentRevision)
    }

    /**
     * TEST B del informe de esta fase ("persistNow no sobrescribe nueva
     * identidad"): si el Layer cambió legítimamente de URI (una edición
     * real del usuario, p. ej. reemplazar la imagen) MIENTRAS el guardado
     * seguía en vuelo, el resultado tardío de `persistNow` (`resolvedUri`,
     * calculado sobre el URI VIEJO) no debe pisar la edición nueva.
     */
    @Test
    fun `persistNow - no sobrescribe una edicion mas nueva ocurrida durante el guardado`() {
        val gate = LayerGpuCommitGate()
        val layer = FakePersistableLayer(sourceUri = "content://saf/A", contentRevision = 1)

        // persistNow captura el URI ANTES de arrancar el IO (equivalente
        // a `layerSnapshots`/`originalUriById` en el código real).
        val originalUriAtSaveStart = layer.sourceUri

        // Mientras el "IO" de persistNow está en vuelo (simulado, sin
        // sleeps — ver restricción del informe de esta fase contra
        // delays artificiales para "fingir" concurrencia), el usuario
        // reemplaza la imagen de verdad: una mutación LEGÍTIMA, protegida
        // por el mismo gate, que en la app real pasaría por `replaceLayer`.
        gate.withGate {
            layer.sourceUri = "content://saf/B"
            layer.contentRevision += 1 // reemplazo real: SÍ bumpea revisión
        }

        // El "IO" de persistNow ya terminó y calculó su resolvedUri sobre
        // el URI VIEJO (A) — llega tarde a la fiesta.
        val resolvedUriFromStaleIo = "file:///local/A-normalized.png"

        gate.withGate {
            if (layer.sourceUri == originalUriAtSaveStart) {
                layer.sourceUri = resolvedUriFromStaleIo
            }
            // si la condición es falsa (como acá), NO se toca nada — ni
            // sourceUri ni contentRevision.
        }

        assertEquals("la edición más nueva (B) debe conservarse intacta", "content://saf/B", layer.sourceUri)
        assertEquals(2, layer.contentRevision)
    }

    /**
     * TEST C del informe de esta fase ("same-content URI normalization"):
     * cuando el URI cambia SOLO por normalización de ubicación del MISMO
     * contenido (el caso feliz de `persistNow`), `contentRevision` debe
     * conservar su valor exactamente — nunca una incrementación
     * artificial "porque cambió sourceUri".
     */
    @Test
    fun `persistNow - normalizacion de mismo contenido no incrementa contentRevision`() {
        val gate = LayerGpuCommitGate()
        val layer = FakePersistableLayer(sourceUri = "content://saf/original", contentRevision = 42)
        val originalUriAtSaveStart = layer.sourceUri

        gate.withGate {
            if (layer.sourceUri == originalUriAtSaveStart) {
                layer.sourceUri = "file:///local/normalized.png"
                // A propósito: ninguna línea acá toca `contentRevision`.
            }
        }

        assertEquals("file:///local/normalized.png", layer.sourceUri)
        assertEquals("la normalización de ubicación no debe alterar la revisión de contenido", 42, layer.contentRevision)
    }
}

