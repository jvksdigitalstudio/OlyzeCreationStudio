package com.yeivikas.olyzecs.engine.timeline

import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.scene.Layer
import kotlin.math.floor

// ============================================================
// Orden de la playlist (timeline) — ÚNICA fuente de verdad.
//
// Convención (como la Playlist de FL Studio / un DAW): la lista se lee de ARRIBA
// hacia ABAJO en orden ASCENDENTE de `Layer.zIndex` / `AudioTrack.trackOrder`
// (mismo espacio de valores). Consecuencias:
//   - Todo elemento NUEVO (imagen, audio, restaurado de papelera, y a futuro
//     texto, modelo 3D, video…) recibe un valor MAYOR que todo lo existente: entra
//     como ÚLTIMA fila.
//   - La última fila es también la más al FRENTE del canvas (el motor pinta en
//     orden ascendente de zIndex), así que lo recién agregado siempre se ve.
//   - El fondo (importAsBackground) usa un valor menor que todo: primera fila,
//     al fondo del canvas — su lugar natural.
//
// Antes cada tipo tenía su propia regla (imágenes: `layers.size` y lista
// descendente → arriba; audio: `min - 1` → abajo), por eso lo nuevo caía a veces
// arriba y a veces abajo. Cualquier tipo nuevo de elemento DEBE usar estas funciones.
// ============================================================

/** Posición de esta capa en la playlist (su `zIndex`), en el espacio de valores compartido con el audio. */
val Layer.playlistOrder: Float get() = zIndex.toFloat()

/** Posición de este carril de audio en la playlist (su `trackOrder`), mismo espacio que [Layer.playlistOrder]. */
val AudioTrack.playlistOrder: Float get() = trackOrder

/**
 * Ids de TODAS las filas (capas + carriles de audio) en el orden en que se ven, de arriba a abajo.
 * Criterio ÚNICO de orden: lo usan la UI, el reordenamiento y las APIs. Empates: estable
 * (capas primero, luego audio).
 */
fun playlistIdsInOrder(layers: List<Layer>, audioTracks: List<AudioTrack>): List<String> =
    (layers.map { it.id to it.playlistOrder } + audioTracks.map { it.id to it.playlistOrder })
        .sortedBy { it.second }
        .map { it.first }

/**
 * Pasos de fila (+ = hacia abajo) para llevar [id] a la posición absoluta [absoluteOrder]
 * (0 = primera fila, size-1 = última), o `null` si [id] no está. Tras cada reordenamiento los
 * valores quedan consecutivos 0..N-1, así que "posición absoluta" y "fila" coinciden; un valor
 * fuera de rango se acota a la primera/última fila.
 */
fun stepsToAbsoluteOrder(orderedIds: List<String>, id: String, absoluteOrder: Int): Int? {
    val from = orderedIds.indexOf(id)
    if (from < 0) return null
    return absoluteOrder.coerceIn(0, orderedIds.lastIndex) - from
}

/** Valor de orden para un elemento nuevo: mayor que todos los de [existingOrders] (0 si no hay ninguno). */
fun nextTopOrder(existingOrders: List<Float>): Float {
    val highest = existingOrders.maxOrNull() ?: return 0f
    return floor(highest) + 1f
}

/** Valor de orden de un elemento nuevo considerando capas y carriles de audio por igual. */
fun nextTopOrder(existingLayers: List<Layer>, existingAudioTracks: List<AudioTrack>): Float =
    nextTopOrder(existingLayers.map { it.playlistOrder } + existingAudioTracks.map { it.playlistOrder })

/**
 * `zIndex` inicial para importar un lote de capas nuevas: la primera recibe este
 * valor y las siguientes +1 cada una (así el lote conserva el orden en que se eligió).
 */
fun nextLayerStartingZIndex(existingLayers: List<Layer>, existingAudioTracks: List<AudioTrack>): Int =
    nextTopOrder(existingLayers, existingAudioTracks).toInt()
