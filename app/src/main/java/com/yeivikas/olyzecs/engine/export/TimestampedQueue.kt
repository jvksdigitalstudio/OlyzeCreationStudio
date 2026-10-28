package com.yeivikas.olyzecs.engine.export

/**
 * Cola de lectura secuencial de muestras ya ordenadas por tiempo de presentación,
 * usada para INTERCALAR la pista de audio (pre-codificada en memoria) con el video
 * durante el muxing: [drainUpTo] entrega, en orden, todas las muestras cuyo
 * timestamp es `<=` al límite dado y recuerda dónde quedó.
 *
 * Es una clase pura (sin dependencias de Android) a propósito: el contrato del
 * intercalado se verifica con tests unitarios JVM, sin necesidad de un dispositivo.
 *
 * Precondición: [items] está ordenada de forma no decreciente por [timestampUs]
 * (los chunks AAC salen del encoder con timestamps monótonos).
 */
internal class TimestampedQueue<T>(
    private val items: List<T>,
    private val timestampUs: (T) -> Long
) {
    private var cursor = 0

    /** Cantidad de muestras que todavía no se entregaron. */
    val remaining: Int get() = items.size - cursor

    /** Entrega a [sink], en orden, cada muestra pendiente con timestamp `<= limitUs`. */
    fun drainUpTo(limitUs: Long, sink: (T) -> Unit) {
        while (cursor < items.size && timestampUs(items[cursor]) <= limitUs) {
            sink(items[cursor++])
        }
    }
}
