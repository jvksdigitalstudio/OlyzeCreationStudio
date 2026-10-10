package com.yeivikas.olyzecs.engine.export

import org.junit.Assert.assertEquals
import org.junit.Test

/** Contrato del intercalado A/V: orden, no repetición y vaciado final. */
class TimestampedQueueTest {

    private fun queueOf(vararg ts: Long) = TimestampedQueue(ts.toList()) { it }

    private fun TimestampedQueue<Long>.drain(limit: Long): List<Long> =
        mutableListOf<Long>().also { out -> drainUpTo(limit) { out += it } }

    @Test
    fun entrega_solo_las_muestras_hasta_el_limite_inclusive() {
        val q = queueOf(0, 10, 20, 30)
        assertEquals(listOf(0L, 10L), q.drain(10))
        assertEquals(2, q.remaining)
    }

    @Test
    fun no_repite_muestras_ya_entregadas() {
        val q = queueOf(0, 10, 20)
        assertEquals(listOf(0L, 10L), q.drain(15))
        assertEquals(emptyList<Long>(), q.drain(15))
        assertEquals(listOf(20L), q.drain(25))
    }

    @Test
    fun un_limite_anterior_a_la_primera_muestra_no_entrega_nada() {
        val q = queueOf(100, 200)
        assertEquals(emptyList<Long>(), q.drain(50))
        assertEquals(2, q.remaining)
    }

    @Test
    fun limite_maximo_vacia_la_cola_completa() {
        val q = queueOf(0, 10, 20)
        assertEquals(listOf(0L, 10L, 20L), q.drain(Long.MAX_VALUE))
        assertEquals(0, q.remaining)
    }

    @Test
    fun cola_vacia_es_segura() {
        val q = queueOf()
        assertEquals(emptyList<Long>(), q.drain(Long.MAX_VALUE))
        assertEquals(0, q.remaining)
    }

    @Test
    fun timestamps_repetidos_se_entregan_todos_en_orden() {
        val q = queueOf(10, 10, 10, 20)
        assertEquals(listOf(10L, 10L, 10L), q.drain(10))
    }
}
