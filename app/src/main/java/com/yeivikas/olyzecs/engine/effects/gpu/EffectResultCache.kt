package com.yeivikas.olyzecs.engine.effects.gpu

/**
 * Qué hizo falta para obtener un resultado. Si cualquiera cambia, el resultado guardado ya no vale: otra versión de la
 * textura de origen, otros parámetros o otro tamaño.
 *
 * [sourceVersion] es un número que cambia SIEMPRE que cambia el contenido de la textura de origen. No basta la revisión de
 * contenido de la capa: las vistas previas suben una textura nueva con la MISMA revisión (y, a veces, el mismo tamaño), y
 * tampoco el id de textura de OpenGL, que se reutiliza tras liberarse. Lo asigna quien sube la textura (un contador por
 * subida).
 */
data class EffectCacheSignature(
    val sourceVersion: Long,
    val params: LayerEffectParams,
    val width: Int,
    val height: Int
)

/**
 * Caché de resultados de la etapa de efectos: guarda, por capa, el último resultado calculado para no recalcular nada
 * mientras se reproduce (solo cuando cambian los parámetros o el contenido).
 *
 * - **Una entrada por capa.** Un resultado de una versión anterior es obsoleto por definición: al guardar uno nuevo se
 *   libera el viejo. Así la caché nunca acumula versiones muertas.
 * - **Presupuesto en bytes con expulsión por uso menos reciente.** El resultado que se acaba de guardar nunca se expulsa
 *   a sí mismo, aunque por sí solo exceda el presupuesto: es el que se está usando.
 * - [onEvict] recibe cada valor que sale de la caché por cualquier motivo, para que quien lo creó lo libere (en la app,
 *   devolver la textura al [GpuTexturePool]).
 *
 * Pura (sin GL): [V] es un identificador opaco del resultado. No es segura entre hilos: usar siempre desde el hilo GL.
 */
class EffectResultCache<V : Any>(
    private val budgetBytes: Long,
    private val sizeOf: (V) -> Long,
    private val onEvict: (V) -> Unit
) {
    private class Entry<V>(val signature: EffectCacheSignature, val value: V, val bytes: Long)

    // accessOrder = true: iterar da primero la entrada menos usada recientemente.
    private val entries = LinkedHashMap<String, Entry<V>>(16, 0.75f, true)

    var totalBytes: Long = 0L
        private set

    val size: Int get() = entries.size

    init {
        require(budgetBytes > 0) { "El presupuesto debe ser positivo: $budgetBytes" }
    }

    /** El resultado de [layerId] solo si se calculó con exactamente [signature]; si no, `null`. */
    fun get(layerId: String, signature: EffectCacheSignature): V? {
        val entry = entries[layerId] ?: return null
        return if (entry.signature == signature) entry.value else null
    }

    /** Guarda el resultado de [layerId] sustituyendo (y liberando) el anterior. [value] no puede ser ya el guardado. */
    fun put(layerId: String, signature: EffectCacheSignature, value: V) {
        val bytes = sizeOf(value)
        require(bytes >= 0) { "Tamaño negativo: $bytes" }
        remove(layerId)
        entries[layerId] = Entry(signature, value, bytes)
        totalBytes += bytes
        trim(protectedLayerId = layerId)
    }

    /** Libera los resultados de las capas que ya no existen. */
    fun retainOnly(activeLayerIds: Set<String>) {
        entries.keys.filter { it !in activeLayerIds }.forEach { remove(it) }
    }

    /** Libera todo (cierre normal). */
    fun clear() {
        entries.keys.toList().forEach { remove(it) }
    }

    /** Olvida todo SIN notificar: tras perder el contexto GL las texturas ya no existen y liberarlas fallaría. */
    fun abandon() {
        entries.clear()
        totalBytes = 0L
    }

    private fun remove(layerId: String) {
        val entry = entries.remove(layerId) ?: return
        totalBytes -= entry.bytes
        onEvict(entry.value)
    }

    private fun trim(protectedLayerId: String) {
        if (totalBytes <= budgetBytes) return
        val iterator = entries.entries.iterator()
        while (iterator.hasNext() && totalBytes > budgetBytes) {
            val candidate = iterator.next()
            if (candidate.key == protectedLayerId) continue
            iterator.remove()
            totalBytes -= candidate.value.bytes
            onEvict(candidate.value.value)
        }
    }
}
