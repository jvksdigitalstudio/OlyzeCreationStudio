package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan de reproducción de la vista previa ([PreviewLoopPlan]): construcción, normalización y confinamiento del cursor
 * a uno o varios tramos, en orden. Puro (sin `Uri` ni carriles).
 */
class PreviewLoopPlanTest {

    private fun range(start: Long, end: Long) = PreviewLoopRange(start, end)

    private val single = PreviewLoopPlan(listOf(range(5_000L, 7_000L)))
    private val two = PreviewLoopPlan(listOf(range(1_000L, 2_000L), range(3_000L, 3_500L)))

    private fun PreviewLoopPlan.isInside(ms: Long) = ranges.any { ms >= it.startMs && ms < it.endMs }

    // ---- Construcción ----

    @Test
    fun `un plan sin tramos no se puede crear`() {
        assertThrows(IllegalArgumentException::class.java) { PreviewLoopPlan(emptyList()) }
    }

    @Test
    fun `los tramos de un plan deben ir ordenados y sin solaparse`() {
        assertThrows(IllegalArgumentException::class.java) {
            PreviewLoopPlan(listOf(range(3_000L, 3_500L), range(1_000L, 2_000L)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreviewLoopPlan(listOf(range(1_000L, 2_500L), range(2_000L, 3_000L)))
        }
    }

    @Test
    fun `dos tramos que se tocan son validos`() {
        PreviewLoopPlan(listOf(range(1_000L, 2_000L), range(2_000L, 3_000L)))
    }

    // ---- of: normaliza tramos en cualquier orden ----

    @Test
    fun `of sin tramos no da plan`() {
        assertNull(PreviewLoopPlan.of(emptyList()))
    }

    @Test
    fun `of ordena los tramos por inicio`() {
        assertEquals(
            PreviewLoopPlan(listOf(range(1_000L, 2_000L), range(3_000L, 3_500L))),
            PreviewLoopPlan.of(listOf(range(3_000L, 3_500L), range(1_000L, 2_000L)))
        )
    }

    @Test
    fun `of une los tramos que se solapan o se tocan`() {
        assertEquals(PreviewLoopPlan(listOf(range(1_000L, 3_000L))), PreviewLoopPlan.of(listOf(range(1_000L, 2_500L), range(2_000L, 3_000L))))
        assertEquals(PreviewLoopPlan(listOf(range(1_000L, 3_000L))), PreviewLoopPlan.of(listOf(range(1_000L, 2_000L), range(2_000L, 3_000L))))
    }

    @Test
    fun `of descarta un tramo contenido en otro`() {
        assertEquals(PreviewLoopPlan(listOf(range(1_000L, 3_000L))), PreviewLoopPlan.of(listOf(range(1_000L, 3_000L), range(1_500L, 2_000L))))
    }

    @Test
    fun `of con un solo tramo lo deja tal cual`() {
        assertEquals(single, PreviewLoopPlan.of(listOf(range(5_000L, 7_000L))))
    }

    // ---- confine con UN tramo: el bucle simple de siempre ----

    @Test
    fun `dentro del tramo el cursor no cambia`() {
        assertEquals(5_000L, single.confine(5_000L))
        assertEquals(6_000L, single.confine(6_000L))
        assertEquals(6_999L, single.confine(6_999L))
    }

    @Test
    fun `antes del inicio el cursor va al inicio`() {
        assertEquals(5_000L, single.confine(0L))
        assertEquals(5_000L, single.confine(4_999L))
    }

    @Test
    fun `al llegar al final vuelve al inicio conservando el sobrepaso`() {
        assertEquals(5_000L, single.confine(7_000L))   // justo en el final → inicio exacto
        assertEquals(5_016L, single.confine(7_016L))   // un tick de 16 ms de sobrepaso
        assertEquals(5_100L, single.confine(7_100L))
    }

    @Test
    fun `un tick mas largo que el tramo sigue cayendo en el lugar correcto`() {
        // Tramo de 2000 ms y un salto de 4500 ms desde el inicio: dos vueltas completas + 500 ms.
        assertEquals(5_500L, single.confine(5_000L + 4_500L))
        assertEquals(5_000L, single.confine(5_000L + 4_000L))
    }

    // ---- confine con VARIOS tramos: en orden, de izquierda a derecha ----

    @Test
    fun `antes del primer tramo el cursor va al inicio del primero`() {
        assertEquals(1_000L, two.confine(0L))
        assertEquals(1_000L, two.confine(999L))
    }

    @Test
    fun `dentro de cualquier tramo el cursor no cambia`() {
        for (ms in listOf(1_000L, 1_999L, 3_000L, 3_499L)) assertEquals(ms, two.confine(ms))
    }

    @Test
    fun `al terminar un tramo salta al siguiente conservando el sobrepaso`() {
        assertEquals(3_000L, two.confine(2_000L))   // justo en el final del primero → inicio del segundo
        assertEquals(3_016L, two.confine(2_016L))   // un tick de 16 ms de sobrepaso
    }

    @Test
    fun `un cursor en el hueco entre tramos salta al siguiente`() {
        assertEquals(3_200L, two.confine(2_200L))
    }

    @Test
    fun `al terminar el ultimo tramo vuelve al primero`() {
        assertEquals(1_000L, two.confine(3_500L))
        assertEquals(1_016L, two.confine(3_516L))
    }

    @Test
    fun `un cursor muy pasado el final cae dentro del primer tramo`() {
        assertEquals(1_500L, two.confine(10_000L))
    }

    @Test
    fun `el resultado de confine siempre cae dentro de algun tramo`() {
        for (plan in listOf(single, two)) {
            for (ms in -50L..13_000L step 7L) {
                val confined = plan.confine(ms)
                assertTrue("confine($ms) = $confined fuera de ${plan.ranges}", plan.isInside(confined))
            }
        }
    }

    @Test
    fun `avanzando tick a tick los tramos suenan en orden y en bucle`() {
        var cursor = 1_000L
        var current = 0
        val visited = mutableSetOf<Int>()
        repeat(3_000) {
            cursor = two.confine(cursor + 16L)
            val index = two.ranges.indexOfFirst { cursor >= it.startMs && cursor < it.endMs }
            assertTrue("El cursor $cursor cayó fuera de los tramos", index >= 0)
            visited += index
            if (index != current) {
                assertEquals("Salto fuera de orden", (current + 1) % two.ranges.size, index)
                current = index
            }
        }
        assertEquals(setOf(0, 1), visited)
    }
}
