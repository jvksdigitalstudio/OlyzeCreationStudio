package com.yeivikas.olyzecs.engine.audio

import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.engine.timeline.nextTopOrder
import java.util.UUID

// ============================================================
// Lista de carriles de audio del proyecto (`EditorUiState.audioTracks`):
// orden de creación de un carril nuevo y búsqueda/actualización por id.
// Reglas puras (sin Android ni estado): las comparten el ViewModel, la
// carga de proyectos y la UI.
// ============================================================

/**
 * Calcula el [AudioTrack.trackOrder] que debe recibir un carril de audio
 * NUEVO (la primera vez que se crea, o al crear otro carril con "+" →
 * "Audio") para quedar como ÚLTIMA fila de la playlist — después de todas las
 * imágenes y carriles ya cargados —, la misma regla que las imágenes (ver
 * `engine/timeline/PlaylistOrder.kt`).
 * Agregar clips a un carril que YA existe no vuelve a llamar a esta
 * función: el carril conserva su `trackOrder` de siempre.
 *
 * ÚNICA función que debe calcular este valor: los dos puntos de entrada
 * que pueden crear un [AudioTrack] nuevo (`EditorViewModel.importAudio`,
 * flujo manual desde la UI, y `AudioApiImpl.setAudioClip`, flujo
 * programático de la EliNer API) llaman acá en vez de repetir la cuenta
 * cada uno por su lado.
 *
 * `TimelineView` ordena la playlist en forma ASCENDENTE por `trackOrder`/`zIndex`
 * (el valor más alto es la última fila), así que "después de la última fila"
 * significa un valor MAYOR que el máximo entre el `zIndex` de [existingLayers]
 * y el `trackOrder` de [existingAudioTracks]. Pura (sin Android) para poder
 * testearla con JUnit.
 */
fun nextAudioTrackOrder(existingLayers: List<Layer>, existingAudioTracks: List<AudioTrack>): Float =
    nextAudioTrackOrder(
        existingLayers.map { it.zIndex.toFloat() } + existingAudioTracks.map { it.trackOrder }
    )

/** Núcleo con primitivos de [nextAudioTrackOrder]: un valor mayor que el máximo de [existingOrders] (`0` si no hay ninguno). */
fun nextAudioTrackOrder(existingOrders: List<Float>): Float = nextTopOrder(existingOrders)

// ============================================================
// Lista de carriles de audio — búsqueda y actualización por id
// ============================================================

/** El carril con este [trackId], o `null` si no existe (ya se borró, o nunca existió). */
fun List<AudioTrack>.track(trackId: String): AudioTrack? = firstOrNull { it.id == trackId }

/** El carril que contiene el clip [clipId], o `null`. Los ids de clip son UUID únicos en todo el proyecto. */
fun List<AudioTrack>.trackOfClip(clipId: String): AudioTrack? = firstOrNull { track -> track.clips.any { it.id == clipId } }

/** El clip [clipId] de cualquier carril, o `null`. */
fun List<AudioTrack>.clip(clipId: String): AudioClip? = trackOfClip(clipId)?.clip(clipId)

/**
 * Lista con [newTrack] en lugar del carril de su mismo [AudioTrack.id] (misma
 * posición); si no existía, se agrega al final. Un carril SIN clips deja de
 * existir: se quita de la lista (un carril de audio vacío no se muestra ni se
 * guarda — mismo criterio de siempre cuando se borraba el último clip).
 */
fun List<AudioTrack>.withTrack(newTrack: AudioTrack): List<AudioTrack> = when {
    newTrack.clips.isEmpty() -> filterNot { it.id == newTrack.id }
    any { it.id == newTrack.id } -> map { if (it.id == newTrack.id) newTrack else it }
    else -> this + newTrack
}

/** Instante (ms) en que termina el último clip de todos los carriles; `0` si no hay clips. */
fun List<AudioTrack>.endMs(): Long = maxOfOrNull { it.endMs() } ?: 0L

/**
 * Misma lista pero con ids de carril ÚNICOS: ante un id repetido (JSON
 * corrupto o editado a mano) el duplicado recibe uno nuevo. Dos carriles con
 * el mismo id se pisarían en la selección, el reordenamiento y el guardado.
 */
fun List<AudioTrack>.withUniqueIds(): List<AudioTrack> {
    val seen = HashSet<String>()
    return map { track ->
        if (seen.add(track.id)) track
        else track.copy(id = UUID.randomUUID().toString()).also { seen.add(it.id) }
    }
}
