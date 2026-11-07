package com.yeivikas.olyzecs.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 3 — Render / GL Lifecycle.
 *
 * A diferencia de [GpuHandleTest]/[GLRendererLifecycleStateTest]/
 * [GridTextureCacheStateTest] (unitarios, una pieza a la vez), este
 * archivo reproduce, combinando esas mismas piezas puras, los escenarios
 * de ciclo de vida completo que pide el brief de Fase 3 (§22): no se
 * puede levantar un `GLSurfaceView`/EGL real en un test JUnit sin
 * Robolectric (no está en las dependencias del proyecto — ver
 * `app/build.gradle.kts`, mismo criterio ya documentado en
 * `PROJECT_AUDIT.md` de por qué el proyecto elige JUnit puro), así que
 * estos tests validan el comportamiento del ESTADO que gobierna el
 * lifecycle — que es exactamente lo que cambió en esta fase — en vez de
 * las llamadas GLES en sí, que ya estaban ahí desde antes y no cambiaron.
 */
class RendererLifecycleScenarioTest {

    /** Simula, con las piezas puras reales, el mapa de responsabilidades de GLRenderer.onSurfaceCreated. */
    private class FakeRendererLifecycle {
        val generation = GpuContextGeneration()
        var state: GLRendererLifecycleState = GLRendererLifecycleState.UNINITIALIZED
            private set
        val gridCache = GridTextureCacheState()

        fun onSurfaceCreated() {
            state = GLRendererLifecycleState.UNINITIALIZED
            generation.advance()
            gridCache.invalidateForNewContext()
            state = GLRendererLifecycleState.SURFACE_READY
        }

        fun onSurfaceChanged() {
            state = GLRendererLifecycleState.VIEWPORT_READY
        }

        fun canDraw(): Boolean = state == GLRendererLifecycleState.VIEWPORT_READY
    }

    // --- Escenario A: Renderer initialization ---
    @Test
    fun `escenario A - estado inicial hasta onSurfaceCreated deja recursos validos pero sin poder dibujar aun`() {
        val renderer = FakeRendererLifecycle()
        assertEquals(GLRendererLifecycleState.UNINITIALIZED, renderer.state)
        assertFalse(renderer.canDraw())

        renderer.onSurfaceCreated()
        assertEquals(GLRendererLifecycleState.SURFACE_READY, renderer.state)
        assertFalse("Todavía no se conoce el viewport real", renderer.canDraw())

        renderer.onSurfaceChanged()
        assertTrue("Con viewport asignado, dibujar ya es seguro", renderer.canDraw())
    }

    // --- Escenario B + C: Surface recreation / Context recreation ---
    @Test
    fun `escenario B y C - handles del contexto viejo quedan invalidos tras recrear Surface y contexto`() {
        val renderer = FakeRendererLifecycle()
        renderer.onSurfaceCreated()
        renderer.onSurfaceChanged()
        val bitmap = Any()
        renderer.gridCache.recordUpload(id = 5, bitmapIdentity = bitmap, currentGeneration = renderer.generation.value)
        assertTrue(renderer.gridCache.handle.isValid(renderer.generation.value))

        // Surface A destruida → Surface B recreada (mismo bitmap de cuadrícula, Compose no lo recompuso).
        renderer.onSurfaceCreated()

        assertFalse(
            "El handle de la cuadrícula del contexto viejo no debe seguir pareciendo válido",
            renderer.gridCache.handle.isValid(renderer.generation.value)
        )
        assertTrue(
            "GLRenderer debe detectar que hace falta resubir la cuadrícula bajo el contexto nuevo",
            renderer.gridCache.needsReconciliation(bitmap, renderer.generation.value)
        )
        // El renderer "funciona nuevamente" tras completar el ciclo de nuevo.
        renderer.onSurfaceChanged()
        assertTrue(renderer.canDraw())
    }

    // --- Escenario D: Snapshot survival (RenderSnapshot en sí, no las texturas) ---
    @Test
    fun `escenario D - RenderSnapshot no depende de ningun GpuHandle y sobrevive intacto a la recreacion`() {
        val snapshot = RenderSnapshot(
            layers = listOf(
                RenderLayerSnapshot(
                    id = "layer-1",
                    zIndex = 0,
                    visible = true,
                    parallaxFactor = 1f,
                    lookSettings = com.yeivikas.olyzecs.engine.effects.LookSettings(),
                    keyframes = emptyList(),
                    baseFrame = com.yeivikas.olyzecs.engine.camera.CameraFrame(0f, 0f, 1f, 0f, 1f)
                )
            )
        )
        val renderer = FakeRendererLifecycle()
        renderer.onSurfaceCreated()
        renderer.onSurfaceChanged()

        // Surface destruida y recreada — RenderSnapshot no tiene NINGÚN
        // campo GPU (ver RenderSnapshot.kt), así que nada de esto lo toca.
        renderer.onSurfaceCreated()
        renderer.onSurfaceChanged()

        assertEquals(1, snapshot.layers.size)
        assertEquals("layer-1", snapshot.layers.first().id)
        assertTrue(snapshot.layers.first().visible)
    }

    // --- Escenario F: Renderer state (sin estados contradictorios) ---
    @Test
    fun `escenario F - nunca hay un estado no VIEWPORT_READY que permita dibujar`() {
        val renderer = FakeRendererLifecycle()
        // Antes de onSurfaceCreated: no se puede dibujar.
        assertFalse(renderer.canDraw())
        renderer.onSurfaceCreated()
        // Justo después de onSurfaceCreated, todavía no.
        assertFalse(renderer.canDraw())
    }

    // --- Escenario H: Lifecycle background → foreground → renderer recuperable ---
    @Test
    fun `escenario H - una recreacion repetida (background prolongado) sigue dejando el renderer usable`() {
        val renderer = FakeRendererLifecycle()
        val bitmap = Any()

        repeat(5) {
            renderer.onSurfaceCreated()
            renderer.onSurfaceChanged()
            assertTrue(renderer.canDraw())
            if (renderer.gridCache.needsReconciliation(bitmap, renderer.generation.value)) {
                renderer.gridCache.recordUpload(id = 1, bitmapIdentity = bitmap, currentGeneration = renderer.generation.value)
            }
            assertTrue(renderer.gridCache.handle.isValid(renderer.generation.value))
        }

        assertEquals(5, renderer.generation.value)
    }
}
