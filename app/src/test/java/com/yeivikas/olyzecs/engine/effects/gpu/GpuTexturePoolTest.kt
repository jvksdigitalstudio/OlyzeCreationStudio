package com.yeivikas.olyzecs.engine.effects.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GpuTexturePoolTest {

    private class FakeAllocator : TextureAllocator {
        private var next = 1
        val created = mutableListOf<Int>()
        val deleted = mutableListOf<Int>()
        var failNext = false
        override fun create(width: Int, height: Int): Int {
            if (failNext) { failNext = false; error("sin memoria") }
            return next++.also { created += it }
        }
        override fun delete(textureId: Int) { deleted += textureId }
    }

    private val alloc = FakeAllocator()
    private fun pool(budget: Long = 10_000L) = GpuTexturePool(alloc, budget)

    // 10 x 10 RGBA = 400 bytes
    @Test
    fun `reserva crea una textura nueva y cuenta sus bytes`() {
        val p = pool()
        val t = p.acquire(10, 10)
        assertEquals(400L, t.bytes)
        assertEquals(1, p.inUseCount)
        assertEquals(400L, p.totalBytes)
    }

    @Test
    fun `una textura liberada del mismo tamano se reutiliza sin crear otra`() {
        val p = pool()
        val first = p.acquire(10, 10)
        p.release(first)
        val second = p.acquire(10, 10)
        assertEquals(first.id, second.id)
        assertEquals(1, alloc.created.size)
    }

    @Test
    fun `un tamano distinto no reutiliza`() {
        val p = pool()
        p.release(p.acquire(10, 10))
        val other = p.acquire(10, 11)
        assertEquals(2, alloc.created.size)
        assertNotEquals(1, other.id)
    }

    @Test
    fun `liberar dos veces o una textura ajena es un error`() {
        val p = pool()
        val t = p.acquire(10, 10)
        p.release(t)
        assertThrows(IllegalStateException::class.java) { p.release(t) }
        assertThrows(IllegalStateException::class.java) { p.release(PooledTexture(999, 1, 1)) }
    }

    @Test
    fun `antes de crear expulsa las libres mas antiguas para respetar el presupuesto`() {
        val p = pool(budget = 1_000L)
        val a = p.acquire(10, 10) // 400
        val b = p.acquire(10, 11) // 440
        p.release(a)
        p.release(b)              // libres: a (antigua), b -> total 840
        p.acquire(20, 10)         // necesita 800: debe expulsar a (400) y luego b (440)
        assertEquals(listOf(a.id, b.id), alloc.deleted)
        assertEquals(0, p.freeCount)
    }

    @Test
    fun `las texturas en uso nunca se expulsan aunque se supere el presupuesto`() {
        val p = pool(budget = 500L)
        p.acquire(10, 10)
        p.acquire(10, 10)
        assertEquals(emptyList<Int>(), alloc.deleted)
        assertEquals(2, p.inUseCount)
        assertEquals(800L, p.totalBytes)
    }

    @Test
    fun `si la creacion falla el pool queda consistente y propaga el error`() {
        val p = pool()
        alloc.failNext = true
        assertThrows(IllegalStateException::class.java) { p.acquire(10, 10) }
        assertEquals(0, p.inUseCount)
        assertEquals(0L, p.totalBytes)
        val ok = p.acquire(10, 10)
        assertEquals(1, p.inUseCount)
        assertEquals(400L, ok.bytes)
    }

    @Test
    fun `dispose destruye todas las texturas, libres y en uso`() {
        val p = pool()
        val a = p.acquire(10, 10)
        val b = p.acquire(10, 10)
        p.release(a)
        p.dispose()
        assertEquals(setOf(a.id, b.id), alloc.deleted.toSet())
        assertEquals(0L, p.totalBytes)
    }

    @Test
    fun `onContextLost olvida todo sin destruir porque las texturas ya no existen`() {
        val p = pool()
        p.acquire(10, 10)
        p.onContextLost()
        assertEquals(emptyList<Int>(), alloc.deleted)
        assertEquals(0, p.inUseCount)
        assertEquals(0L, p.totalBytes)
    }

    @Test
    fun `dimensiones y presupuesto invalidos se rechazan`() {
        assertThrows(IllegalArgumentException::class.java) { pool().acquire(0, 5) }
        assertThrows(IllegalArgumentException::class.java) { pool().acquire(5, -1) }
        assertThrows(IllegalArgumentException::class.java) { GpuTexturePool(alloc, 0) }
    }
}
