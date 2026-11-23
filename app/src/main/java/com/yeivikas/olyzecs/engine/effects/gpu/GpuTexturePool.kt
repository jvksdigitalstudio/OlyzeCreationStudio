package com.yeivikas.olyzecs.engine.effects.gpu

/** Crea y destruye texturas de render. En la app lo implementa la capa GL; aquí es una interfaz para probar sin GPU. */
interface TextureAllocator {
    /** Crea una textura RGBA de [width] x [height] y devuelve su identificador. Lanza si no se puede (memoria, FBO). */
    fun create(width: Int, height: Int): Int

    fun delete(textureId: Int)
}

/** Una textura reservada del pool. Es un valor: comparar por [id]. */
data class PooledTexture(val id: Int, val width: Int, val height: Int) {
    val bytes: Long get() = width.toLong() * height.toLong() * BYTES_PER_PIXEL

    companion object {
        /** RGBA de 8 bits por canal: lo único cuya capacidad de render garantiza OpenGL ES 2.0. */
        const val BYTES_PER_PIXEL = 4L
    }
}

/**
 * Reutiliza texturas de render para no crear y destruir una por cada recálculo (la creación es lo caro).
 *
 * - [acquire] devuelve una libre del mismo tamaño exacto o crea una nueva.
 * - Antes de crear, expulsa texturas LIBRES (las más antiguas primero) si hiciera falta para respetar el presupuesto.
 *   Las texturas EN USO jamás se expulsan: el presupuesto es un límite blando para ellas.
 * - [dispose] destruye todo (cierre normal); [onContextLost] olvida todo sin destruir (las texturas ya no existen).
 *
 * Pura: no conoce GL. No es segura entre hilos: usar siempre desde el hilo GL.
 */
class GpuTexturePool(
    private val allocator: TextureAllocator,
    private val budgetBytes: Long
) {
    private val free = ArrayDeque<PooledTexture>()
    private val inUse = LinkedHashMap<Int, PooledTexture>()

    init {
        require(budgetBytes > 0) { "El presupuesto debe ser positivo: $budgetBytes" }
    }

    val totalBytes: Long get() = free.sumOf { it.bytes } + inUse.values.sumOf { it.bytes }
    val freeCount: Int get() = free.size
    val inUseCount: Int get() = inUse.size

    fun acquire(width: Int, height: Int): PooledTexture {
        require(width > 0 && height > 0) { "Dimensiones inválidas: ${width}x$height" }
        val reusable = free.firstOrNull { it.width == width && it.height == height }
        if (reusable != null) {
            free.remove(reusable)
            inUse[reusable.id] = reusable
            return reusable
        }
        val needed = width.toLong() * height.toLong() * PooledTexture.BYTES_PER_PIXEL
        while (free.isNotEmpty() && totalBytes + needed > budgetBytes) {
            allocator.delete(free.removeFirst().id)
        }
        val texture = PooledTexture(allocator.create(width, height), width, height)
        inUse[texture.id] = texture
        return texture
    }

    /** Devuelve [texture] al pool para reutilizarla. Liberar dos veces, o una que no es del pool, es un error de programación. */
    fun release(texture: PooledTexture) {
        val owned = inUse.remove(texture.id)
        check(owned != null) { "La textura ${texture.id} no está reservada en este pool" }
        free.addLast(owned)
    }

    fun dispose() {
        (free + inUse.values).forEach { allocator.delete(it.id) }
        free.clear()
        inUse.clear()
    }

    fun onContextLost() {
        free.clear()
        inUse.clear()
    }
}
