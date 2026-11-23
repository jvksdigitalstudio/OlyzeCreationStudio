package com.yeivikas.olyzecs.engine.effects.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class EffectResultCacheTest {

    /** Valor opaco con un tamaño conocido, como lo sería una textura. */
    private data class Fake(val name: String, val bytes: Long)

    private val evicted = mutableListOf<Fake>()

    private fun cache(budget: Long = 100L) = EffectResultCache<Fake>(budget, { it.bytes }, { evicted += it })

    private fun sig(sourceVersion: Long = 1L, contrast: Float = 1.2f, w: Int = 10, h: Int = 10) =
        EffectCacheSignature(sourceVersion, LayerEffectParams(ColorBasicParams(contrast = contrast)), w, h)

    @Test
    fun `devuelve el resultado solo con la firma exacta`() {
        val c = cache()
        val a = Fake("a", 10)
        c.put("L1", sig(), a)
        assertSame(a, c.get("L1", sig()))
        assertNull("otra versión de la textura de origen", c.get("L1", sig(sourceVersion = 2L)))
        assertNull("otros parámetros", c.get("L1", sig(contrast = 1.5f)))
        assertNull("otro tamaño", c.get("L1", sig(w = 20)))
        assertNull("otra capa", c.get("L2", sig()))
    }

    @Test
    fun `guardar un resultado nuevo de la misma capa libera el anterior`() {
        val c = cache()
        val old = Fake("old", 10)
        val new = Fake("new", 30)
        c.put("L1", sig(sourceVersion = 1L), old)
        c.put("L1", sig(sourceVersion = 2L), new)
        assertEquals(listOf(old), evicted)
        assertEquals(1, c.size)
        assertEquals(30L, c.totalBytes)
        assertNull(c.get("L1", sig(sourceVersion = 1L)))
        assertSame(new, c.get("L1", sig(sourceVersion = 2L)))
    }

    @Test
    fun `al exceder el presupuesto expulsa la capa menos usada`() {
        val c = cache(budget = 100)
        val a = Fake("a", 40); val b = Fake("b", 40); val d = Fake("d", 40)
        c.put("A", sig(), a)
        c.put("B", sig(), b)
        c.put("D", sig(), d)
        assertEquals(listOf(a), evicted)
        assertNull(c.get("A", sig()))
        assertEquals(80L, c.totalBytes)
    }

    @Test
    fun `usar una entrada la protege de la expulsion`() {
        val c = cache(budget = 100)
        val a = Fake("a", 40); val b = Fake("b", 40); val d = Fake("d", 40)
        c.put("A", sig(), a)
        c.put("B", sig(), b)
        c.get("A", sig())
        c.put("D", sig(), d)
        assertEquals("se expulsa B, no A", listOf(b), evicted)
        assertSame(a, c.get("A", sig()))
    }

    @Test
    fun `el resultado recien guardado no se expulsa a si mismo aunque exceda el presupuesto`() {
        val c = cache(budget = 50)
        val big = Fake("big", 500)
        c.put("L1", sig(), big)
        assertSame(big, c.get("L1", sig()))
        assertEquals(500L, c.totalBytes)
        assertEquals(emptyList<Fake>(), evicted)
    }

    @Test
    fun `un resultado enorme expulsa a todos los demas pero no a si mismo`() {
        val c = cache(budget = 100)
        val a = Fake("a", 30); val b = Fake("b", 30); val big = Fake("big", 500)
        c.put("A", sig(), a)
        c.put("B", sig(), b)
        c.put("BIG", sig(), big)
        assertEquals(setOf(a, b), evicted.toSet())
        assertEquals(1, c.size)
    }

    @Test
    fun `retainOnly libera las capas que ya no existen`() {
        val c = cache()
        val a = Fake("a", 10); val b = Fake("b", 10)
        c.put("A", sig(), a)
        c.put("B", sig(), b)
        c.retainOnly(setOf("B"))
        assertEquals(listOf(a), evicted)
        assertEquals(10L, c.totalBytes)
    }

    @Test
    fun `clear libera todo y deja el total en cero`() {
        val c = cache()
        c.put("A", sig(), Fake("a", 10))
        c.put("B", sig(), Fake("b", 20))
        c.clear()
        assertEquals(2, evicted.size)
        assertEquals(0, c.size)
        assertEquals(0L, c.totalBytes)
    }

    @Test
    fun `abandon olvida todo sin notificar porque tras perder el contexto liberar fallaria`() {
        val c = cache()
        c.put("A", sig(), Fake("a", 10))
        c.abandon()
        assertEquals(emptyList<Fake>(), evicted)
        assertEquals(0, c.size)
        assertEquals(0L, c.totalBytes)
    }

    @Test
    fun `presupuesto invalido y tamano negativo se rechazan`() {
        assertThrows(IllegalArgumentException::class.java) { EffectResultCache<Fake>(0, { it.bytes }, {}) }
        val c = EffectResultCache<Fake>(100, { -1L }, {})
        assertThrows(IllegalArgumentException::class.java) { c.put("A", sig(), Fake("a", 1)) }
    }

    @Test
    fun `una textura de origen nueva con la misma capa, parametros y tamano invalida el resultado`() {
        // Caso real: las vistas previas suben una textura distinta con la misma revisión de contenido y el mismo tamaño.
        val c = cache()
        val stale = Fake("calculado sobre la vista previa anterior", 10)
        c.put("L1", sig(sourceVersion = 7L), stale)
        assertNull(c.get("L1", sig(sourceVersion = 8L)))
        assertSame(stale, c.get("L1", sig(sourceVersion = 7L)))
    }
}

