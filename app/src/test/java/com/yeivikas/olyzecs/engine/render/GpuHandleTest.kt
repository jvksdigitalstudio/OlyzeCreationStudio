package com.yeivikas.olyzecs.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 3 — Render / GL Lifecycle.
 *
 * Cubre el criterio de aceptación "los recursos GPU no se consideran
 * persistentes entre contexts" en su forma más pura: [GpuHandle] es la
 * pieza que decide, sin ninguna llamada a GLES, si un id de OpenGL
 * pertenece o no al contexto EGL vigente.
 */
class GpuHandleTest {

    @Test
    fun `un handle es valido solo bajo la generacion exacta en la que fue creado`() {
        val handle = GpuHandle(id = 7, generation = 3)

        assertTrue(handle.isValid(currentGeneration = 3))
        assertFalse(handle.isValid(currentGeneration = 4))
        assertFalse(handle.isValid(currentGeneration = 2))
    }

    @Test
    fun `INVALID nunca es valido sin importar la generacion actual`() {
        assertFalse(GpuHandle.INVALID.isValid(currentGeneration = 0))
        assertFalse(GpuHandle.INVALID.isValid(currentGeneration = 1))
        assertFalse(GpuHandle.INVALID.isValid(currentGeneration = -1))
    }

    @Test
    fun `un id negativo nunca es valido aunque la generacion coincida`() {
        val handle = GpuHandle(id = -5, generation = 3)

        assertFalse(handle.isValid(currentGeneration = 3))
    }

    @Test
    fun `GpuContextGeneration arranca en 0 y avanza de a uno por cada contexto nuevo`() {
        val generation = GpuContextGeneration()

        assertEquals(0, generation.value)
        assertEquals(1, generation.advance())
        assertEquals(1, generation.value)
        assertEquals(2, generation.advance())
        assertEquals(2, generation.value)
    }

    @Test
    fun `un handle creado en una generacion queda invalido automaticamente tras recrear el contexto`() {
        val generation = GpuContextGeneration()
        val handleFromOldContext = GpuHandle(id = 42, generation = generation.value)
        assertTrue(handleFromOldContext.isValid(generation.value))

        // Simula onSurfaceCreated() con un contexto EGL nuevo.
        generation.advance()

        assertFalse(
            "El handle del contexto viejo NO debe seguir pareciendo válido después de recrear el contexto",
            handleFromOldContext.isValid(generation.value)
        )
    }
}
