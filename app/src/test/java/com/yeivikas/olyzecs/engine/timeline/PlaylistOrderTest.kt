package com.yeivikas.olyzecs.engine.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fija por regresión el bug reportado: lo recién agregado caía a veces ARRIBA
 * (imágenes: `layers.size`) y a veces ABAJO (audio: `min - 1`). Ahora hay UNA regla:
 * todo elemento nuevo recibe un valor mayor que cualquiera existente → última fila.
 */
class PlaylistOrderTest {

    @Test
    fun `lista vacia - el primero recibe 0`() {
        assertEquals(0f, nextTopOrder(emptyList<Float>()), 0f)
    }

    @Test
    fun `siempre es mayor que el maximo, incluso con huecos por capas borradas`() {
        // El bug de layers.size: con zIndex [0,1,5] size=3 caia ENTRE capas existentes.
        val next = nextTopOrder(listOf(0f, 1f, 5f))
        assertEquals(6f, next, 0f)
        assertTrue(next > 5f)
    }

    @Test
    fun `fondo con zIndex negativo no altera la regla`() {
        assertEquals(0f, nextTopOrder(listOf(-1f)), 0f)
        assertEquals(3f, nextTopOrder(listOf(-1f, 0f, 1f, 2f)), 0f)
    }

    @Test
    fun `imagenes y audio comparten la misma regla - dos altas seguidas quedan en orden de carga`() {
        // imagen -> audio -> imagen: cada una por encima de la anterior, sin importar el tipo.
        val orders = mutableListOf(0f, 1f)
        repeat(3) { orders += nextTopOrder(orders) }
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f), orders)
    }

    @Test
    fun `un trackOrder fraccionario de proyectos viejos tampoco colisiona`() {
        assertEquals(3f, nextTopOrder(listOf(1f, 2.5f)), 0f)
    }
}

class PlaylistOrderMovesTest {
    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun `mover a una posicion absoluta da pasos hacia abajo positivos`() {
        assertEquals(2, stepsToAbsoluteOrder(ids, "a", 2))
        assertEquals(-2, stepsToAbsoluteOrder(ids, "d", 1))
    }

    @Test
    fun `fuera de rango se acota a primera o ultima fila`() {
        assertEquals(3, stepsToAbsoluteOrder(ids, "a", 99))
        assertEquals(-2, stepsToAbsoluteOrder(ids, "c", -5))
    }

    @Test
    fun `id inexistente devuelve null`() {
        assertEquals(null, stepsToAbsoluteOrder(ids, "zzz", 1))
    }
}
