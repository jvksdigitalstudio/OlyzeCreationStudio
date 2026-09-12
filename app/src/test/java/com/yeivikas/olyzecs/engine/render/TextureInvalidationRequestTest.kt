package com.yeivikas.olyzecs.engine.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 3.1 — Cierre y hardening de ownership de GPU.
 *
 * Cubre el criterio de aceptación "ningún componente fuera del hilo de GL
 * debe escribir directamente un GL texture ID": [TextureInvalidationRequest]
 * es la única vía que le queda al hilo principal para influir sobre una
 * textura GPU — un pedido binario, nunca una escritura del handle en sí.
 */
class TextureInvalidationRequestTest {

    @Test
    fun `sin pedir nada, consumir devuelve false`() {
        val request = TextureInvalidationRequest()

        assertFalse(request.consume())
    }

    @Test
    fun `un pedido se consume exactamente una vez`() {
        val request = TextureInvalidationRequest()

        request.request()

        assertTrue(request.consume())
        // No doble consumo: sin un request() nuevo de por medio, la segunda
        // llamada tiene que volver a false.
        assertFalse(request.consume())
    }

    @Test
    fun `varios pedidos seguidos antes de consumir colapsan en uno solo`() {
        val request = TextureInvalidationRequest()

        request.request()
        request.request()
        request.request()

        assertTrue(request.consume())
        assertFalse(request.consume())
    }

    @Test
    fun `se puede volver a pedir despues de haber consumido`() {
        val request = TextureInvalidationRequest()

        request.request()
        assertTrue(request.consume())

        request.request()
        assertTrue(request.consume())
    }
}
