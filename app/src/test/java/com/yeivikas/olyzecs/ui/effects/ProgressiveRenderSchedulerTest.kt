package com.yeivikas.olyzecs.ui.effects

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressiveRenderSchedulerTest {

    private class Harness(scope: TestScope, val sources: EffectSources<String> = EffectSources()) {
        val previews = mutableListOf<String>()
        val finals = mutableListOf<String>()
        val rendered = mutableListOf<Pair<String, RenderTier>>()
        var gate: (suspend (String, RenderTier) -> Unit)? = null

        val scheduler = ProgressiveRenderScheduler(
            scope = scope.backgroundScope,
            sources = sources,
            render = { source, tier ->
                rendered += source to tier
                gate?.invoke(source, tier)
                "$source>$tier"
            },
            onPreview = { previews += it },
            onFinal = { finals += it },
            nowMs = { scope.testScheduler.currentTime }
        )
    }

    /**
     * Ejecuta TODO lo pendiente, también el trabajo de `backgroundScope`, que es donde corre el orquestador en estas
     * pruebas. `advanceUntilIdle()` NO sirve: la documentación de `kotlinx-coroutines-test` indica que no espera al
     * trabajo de `backgroundScope` (si solo quedan tareas de fondo, no ejecuta ninguna). `advanceTimeBy` y `runCurrent`
     * sí lo ejecutan.
     */
    private fun TestScope.settle(virtualMs: Long = 10_000L) {
        advanceTimeBy(virtualMs)
        runCurrent()
    }

    @Test
    fun `el guardado nunca se renderiza desde la copia chica ni la de pantalla`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.publishDisplay("display")
        h.scheduler.onParametersChanged()
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue("sin imagen completa no se guarda nada", h.finals.isEmpty())
        assertTrue(h.rendered.none { it.second == RenderTier.FINAL })
    }

    @Test
    fun `el guardado espera a la imagen completa y sale de ella`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.scheduler.onParametersChanged()
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(h.finals.isEmpty())
        h.sources.completeFull("full")
        settle()
        assertEquals(listOf("full>FINAL"), h.finals)
    }

    @Test
    fun `si la decodificacion de la imagen completa fallo no se guarda nada`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull(null)
        h.scheduler.onParametersChanged()
        settle()
        assertTrue(h.finals.isEmpty())
    }

    @Test
    fun `al arrastrar se ve la copia chica y al soltar la de pantalla`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.publishDisplay("display")
        h.scheduler.schedulePreview()
        runCurrent()
        assertEquals(listOf("quick>DRAG"), h.previews)
        advanceTimeBy(150)
        runCurrent()
        assertEquals(listOf("quick>DRAG", "display>SETTLE"), h.previews)
    }

    @Test
    fun `si la copia de pantalla aun no cargo el reposo usa la chica`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.scheduler.schedulePreview()
        advanceTimeBy(150)
        runCurrent()
        assertEquals("quick>SETTLE", h.previews.last())
    }

    @Test
    fun `cambios seguidos producen una sola confirmacion con el estado final`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull("full")
        repeat(5) { h.scheduler.onParametersChanged(); advanceTimeBy(100) }
        settle()
        assertEquals(1, h.finals.size)
    }

    @Test
    fun `el arrastre se limita a un render cada intervalo`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.scheduler.schedulePreview()
        runCurrent()
        advanceTimeBy(20)
        h.scheduler.schedulePreview()
        runCurrent()
        assertEquals(1, h.rendered.count { it.second == RenderTier.DRAG })
        advanceTimeBy(60)
        h.scheduler.schedulePreview()
        runCurrent()
        assertEquals(2, h.rendered.count { it.second == RenderTier.DRAG })
    }

    @Test
    fun `un arrastre lento no pisa a la vista nitida que termino antes`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.publishDisplay("display")
        val slowDrag = CompletableDeferred<Unit>()
        h.gate = { _, tier -> if (tier == RenderTier.DRAG) slowDrag.await() }
        h.scheduler.schedulePreview()
        advanceTimeBy(150)
        runCurrent()
        assertEquals(listOf("display>SETTLE"), h.previews)
        slowDrag.complete(Unit)
        runCurrent()
        assertEquals("el resultado chico llegó tarde y se descarta", listOf("display>SETTLE"), h.previews)
    }

    @Test
    fun `no se lanza otro arrastre mientras uno sigue en curso`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        val hold = CompletableDeferred<Unit>()
        h.gate = { _, tier -> if (tier == RenderTier.DRAG) hold.await() }
        h.scheduler.schedulePreview()
        runCurrent()
        advanceTimeBy(500)
        h.scheduler.schedulePreview()
        runCurrent()
        assertEquals(1, h.rendered.count { it.second == RenderTier.DRAG })
        hold.complete(Unit)
    }

    @Test
    fun `cancelar lo pendiente evita cualquier publicacion posterior`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.publishDisplay("display")
        h.sources.completeFull("full")
        h.scheduler.onParametersChanged()
        runCurrent()
        h.previews.clear()
        h.scheduler.cancelPending()
        settle()
        assertTrue(h.previews.isEmpty())
        assertTrue(h.finals.isEmpty())
    }

    @Test
    fun `commitNow guarda ya, desde la imagen completa y sin esperar el retardo`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull("full")
        h.scheduler.commitNow()
        assertEquals(listOf("full>FINAL"), h.finals)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `commitNow sin imagen completa no guarda nada`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull(null)
        h.scheduler.commitNow()
        assertTrue(h.finals.isEmpty())
    }

    @Test
    fun `una nueva peticion de vista previa permite mostrar de nuevo la copia chica`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.publishDisplay("display")
        h.scheduler.schedulePreview()
        advanceTimeBy(200)
        runCurrent()
        h.previews.clear()
        h.scheduler.schedulePreview()
        runCurrent()
        assertEquals(listOf("quick>DRAG"), h.previews)
    }

    @Test
    fun `un render que devuelve null no muestra ni guarda nada`() = runTest {
        val shown = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val sources = EffectSources<String>().apply { publishQuick("quick"); publishDisplay("display"); completeFull("full") }
        val scheduler = ProgressiveRenderScheduler<String>(
            scope = backgroundScope, sources = sources,
            render = { _, _ -> null },
            onPreview = { shown += it }, onFinal = { saved += it },
            nowMs = { testScheduler.currentTime }
        )
        scheduler.onParametersChanged()
        settle()
        scheduler.commitNow()
        assertTrue(shown.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `un render nulo solo en el guardado no impide mostrar la vista previa`() = runTest {
        val shown = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val sources = EffectSources<String>().apply { publishQuick("quick"); completeFull("full") }
        val scheduler = ProgressiveRenderScheduler<String>(
            scope = backgroundScope, sources = sources,
            render = { source, tier -> if (tier == RenderTier.FINAL) null else "$source>$tier" },
            onPreview = { shown += it }, onFinal = { saved += it },
            nowMs = { testScheduler.currentTime }
        )
        scheduler.onParametersChanged()
        settle()
        assertTrue(shown.isNotEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `un guardado programado queda pendiente hasta que se ejecuta`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull("full")
        assertTrue("sin programar no hay nada pendiente", !h.scheduler.hasPendingCommit)
        h.scheduler.scheduleCommit()
        assertTrue(h.scheduler.hasPendingCommit)
        settle()
        assertEquals(listOf("full>FINAL"), h.finals)
        assertTrue("ya se ejecutó", !h.scheduler.hasPendingCommit)
    }

    @Test
    fun `cancelar lo pendiente no borra el guardado pendiente`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.scheduler.onParametersChanged()
        h.scheduler.cancelPending()
        assertTrue("cancelar el temporizador no es renunciar al guardado", h.scheduler.hasPendingCommit)
    }

    @Test
    fun `cerrar la ventana con un guardado pendiente lo vuelca desde la imagen completa`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull("full")
        h.scheduler.onParametersChanged()
        advanceTimeBy(100)              // se cierra la ventana antes de los 500 ms
        h.scheduler.cancelPending()     // lo que hace la ventana al salir de la composición
        assertTrue(h.finals.isEmpty())
        h.scheduler.flushPendingCommit(backgroundScope)
        settle()
        assertEquals(listOf("full>FINAL"), h.finals)
        assertTrue(!h.scheduler.hasPendingCommit)
    }

    @Test
    fun `volcar sin guardado pendiente no hace nada`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull("full")
        h.scheduler.flushPendingCommit(backgroundScope)
        settle()
        assertTrue(h.finals.isEmpty())
    }

    @Test
    fun `volcar despues de un guardado ya hecho no lo duplica`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull("full")
        h.scheduler.onParametersChanged()
        settle()
        assertEquals(1, h.finals.size)
        h.scheduler.flushPendingCommit(backgroundScope)
        settle()
        assertEquals(1, h.finals.size)
    }

    @Test
    fun `volcar sin imagen completa no guarda nada y limpia la bandera`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull(null)
        h.scheduler.onParametersChanged()
        h.scheduler.cancelPending()
        h.scheduler.flushPendingCommit(backgroundScope)
        settle()
        assertTrue(h.finals.isEmpty())
        assertTrue("no queda nada por guardar", !h.scheduler.hasPendingCommit)
    }

    @Test
    fun `un cambio posterior al volcado vuelve a dejar un guardado pendiente`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull("full")
        h.scheduler.scheduleCommit()
        settle()
        assertTrue(!h.scheduler.hasPendingCommit)
        h.scheduler.scheduleCommit()
        assertTrue(h.scheduler.hasPendingCommit)
    }

    @Test
    fun `el reposo y el guardado nunca renderizan a la vez para no duplicar el pico de memoria`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.publishDisplay("display")
        h.sources.completeFull("full")
        val releaseSettle = CompletableDeferred<Unit>()
        h.gate = { _, tier -> if (tier == RenderTier.SETTLE) releaseSettle.await() }
        h.scheduler.onParametersChanged()
        advanceTimeBy(600)              // el reposo (150 ms) sigue renderizando; el guardado (500 ms) ya toca
        runCurrent()
        assertTrue("el reposo está en curso", h.rendered.any { it.second == RenderTier.SETTLE })
        assertTrue("el guardado debe esperar al reposo", h.rendered.none { it.second == RenderTier.FINAL })
        releaseSettle.complete(Unit)
        settle()
        assertEquals(listOf("full>FINAL"), h.finals)
    }

    @Test
    fun `el arrastre nunca espera al cerrojo de los renderizados pesados`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.publishDisplay("display")
        val holdSettle = CompletableDeferred<Unit>()
        h.gate = { _, tier -> if (tier == RenderTier.SETTLE) holdSettle.await() }
        h.scheduler.schedulePreview()
        advanceTimeBy(200)               // el reposo ya tiene el cerrojo y está retenido
        runCurrent()
        h.previews.clear()
        advanceTimeBy(100)
        h.scheduler.schedulePreview()
        runCurrent()
        assertEquals("el arrastre se muestra aunque el reposo siga renderizando", listOf("quick>DRAG"), h.previews)
        holdSettle.complete(Unit)
    }

    @Test
    fun `quien empieza a guardar reclama el pendiente y un volcado posterior no guarda dos veces`() = runTest {
        // Caso real: «quitar efecto» lanza commitNow() y, en el mismo gesto, se cierra la ventana y se vuelca.
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull("full")
        val holdFinal = CompletableDeferred<Unit>()
        h.gate = { _, tier -> if (tier == RenderTier.FINAL) holdFinal.await() }
        h.scheduler.onParametersChanged()                  // hay un guardado programado
        val reset = launch { h.scheduler.commitNow() }     // el usuario pulsa «quitar efecto»
        runCurrent()
        assertTrue("el guardado en curso ya reclamó el pendiente", !h.scheduler.hasPendingCommit)
        h.scheduler.cancelPending()                        // la ventana se cierra...
        h.scheduler.flushPendingCommit(backgroundScope)    // ...y vuelca
        holdFinal.complete(Unit)
        settle()
        reset.join()
        assertEquals("un único guardado", 1, h.finals.size)
    }

    @Test
    fun `dos volcados seguidos solo guardan una vez`() = runTest {
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.completeFull("full")
        h.scheduler.scheduleCommit()
        h.scheduler.cancelPending()
        h.scheduler.flushPendingCommit(backgroundScope)
        h.scheduler.flushPendingCommit(backgroundScope)
        settle()
        assertEquals(1, h.finals.size)
    }

    @Test
    fun `si la imagen completa fallo no se guarda nada aunque exista la copia de pantalla`() = runTest {
        // El caso peligroso de verdad: hay una copia reducida con la que se podría «guardar algo», y no hay que hacerlo.
        val h = Harness(this)
        h.sources.publishQuick("quick")
        h.sources.publishDisplay("display")
        h.sources.completeFull(null)
        h.scheduler.onParametersChanged()
        settle()
        assertTrue("el guardado programado no debe guardar", h.finals.isEmpty())
        h.scheduler.commitNow()
        assertTrue("el guardado inmediato tampoco", h.finals.isEmpty())
        assertTrue("jamás se renderiza un guardado", h.rendered.none { it.second == RenderTier.FINAL })
        assertTrue("la vista previa sí funciona con la copia de pantalla", h.previews.isNotEmpty())
    }
}

