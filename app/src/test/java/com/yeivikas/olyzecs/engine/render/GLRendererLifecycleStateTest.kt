package com.yeivikas.olyzecs.engine.render

import com.yeivikas.olyzecs.engine.render.GLRendererLifecycleState.SURFACE_READY
import com.yeivikas.olyzecs.engine.render.GLRendererLifecycleState.UNINITIALIZED
import com.yeivikas.olyzecs.engine.render.GLRendererLifecycleState.VIEWPORT_READY
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 3 — Render / GL Lifecycle.
 *
 * Cubre el criterio de aceptación "GLRenderer conoce claramente sus
 * estados" / "evita estados contradictorios": valida el mapa de
 * transiciones legales de [GLRendererLifecycleState] contra el contrato
 * real de `GLSurfaceView.Renderer` (onSurfaceCreated → onSurfaceChanged
 * → onDrawFrame, con onSurfaceCreated pudiendo repetirse en cualquier
 * momento por una recreación de contexto EGL).
 */
class GLRendererLifecycleStateTest {

    @Test
    fun `flujo normal - de UNINITIALIZED a SURFACE_READY a VIEWPORT_READY`() {
        assertTrue(UNINITIALIZED.canTransitionTo(SURFACE_READY))
        assertTrue(SURFACE_READY.canTransitionTo(VIEWPORT_READY))
    }

    @Test
    fun `resize repetido se queda en VIEWPORT_READY - transicion legal a si mismo`() {
        assertTrue(VIEWPORT_READY.canTransitionTo(VIEWPORT_READY))
    }

    @Test
    fun `no se puede saltar directo de UNINITIALIZED a VIEWPORT_READY`() {
        assertFalse(UNINITIALIZED.canTransitionTo(VIEWPORT_READY))
    }

    @Test
    fun `SURFACE_READY no puede permanecer en SURFACE_READY sin pasar por onSurfaceChanged`() {
        assertFalse(SURFACE_READY.canTransitionTo(SURFACE_READY))
    }

    @Test
    fun `una recreacion de contexto EGL siempre puede volver a UNINITIALIZED desde cualquier estado`() {
        assertTrue(UNINITIALIZED.canTransitionTo(UNINITIALIZED))
        assertTrue(SURFACE_READY.canTransitionTo(UNINITIALIZED))
        assertTrue(VIEWPORT_READY.canTransitionTo(UNINITIALIZED))
    }

    @Test
    fun `VIEWPORT_READY no puede retroceder a SURFACE_READY directamente`() {
        // Un resize nunca "desconoce" el viewport — para volver a
        // SURFACE_READY hace falta pasar por UNINITIALIZED primero (un
        // contexto EGL nuevo real), no un simple onSurfaceChanged.
        assertFalse(VIEWPORT_READY.canTransitionTo(SURFACE_READY))
    }
}
