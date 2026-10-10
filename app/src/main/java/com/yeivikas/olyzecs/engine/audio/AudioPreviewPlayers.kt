package com.yeivikas.olyzecs.engine.audio

import android.content.Context

/**
 * Preview en vivo de TODOS los carriles de audio: un [AudioPreviewPlayer]
 * por carril, creado a demanda y liberado cuando el carril desaparece.
 *
 * Así los carriles suenan a la vez (cada uno con su propio `MediaPlayer`),
 * y DENTRO de cada carril sigue valiendo la regla de [AudioPreviewPlayer]:
 * suena solo el clip activo bajo el cabezal. Como el reproductor individual,
 * es un monitoreo en tiempo real y no pretende ser exacto como la
 * exportación (que mezcla todos los clips de todos los carriles).
 *
 * Todo se invoca desde el hilo principal (lo hace el ViewModel).
 */
class AudioPreviewPlayers(private val context: Context) {
    private val players = HashMap<String, AudioPreviewPlayer>()

    /**
     * Sincroniza cada carril de [tracks] con el cabezal ([AudioPreviewPlayer.sync]).
     * Antes, libera los reproductores de carriles que ya no existen (se borró
     * su último clip): un `MediaPlayer` huérfano retiene recursos de audio.
     */
    fun sync(tracks: List<AudioTrack>, projectTimeMs: Long, isPlaying: Boolean, forceSeek: Boolean = false) {
        val liveIds = tracks.mapTo(HashSet()) { it.id }
        players.keys.filter { it !in liveIds }.forEach { players.remove(it)?.release() }
        tracks.forEach { track -> playerFor(track.id).sync(track, projectTimeMs, isPlaying, forceSeek) }
    }

    /** Reposiciona el clip activo de cada carril en [projectTimeMs] (arrastre del cabezal con el audio ya preparado). */
    fun seekToProjectTime(tracks: List<AudioTrack>, projectTimeMs: Long) {
        tracks.forEach { track ->
            val clip = track.activeClipAt(projectTimeMs) ?: return@forEach
            playerFor(track.id).seekToProjectTime(clip, projectTimeMs)
        }
    }

    /** Aplica en caliente volumen y balance del clip activo de cada carril (sin seek ni reinicio). */
    fun updateGains(tracks: List<AudioTrack>, projectTimeMs: Long) {
        tracks.forEach { track ->
            val clip = track.activeClipAt(projectTimeMs) ?: return@forEach
            playerFor(track.id).updateGains(clip)
        }
    }

    fun pause() {
        players.values.forEach { it.pause() }
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
    }

    private fun playerFor(trackId: String): AudioPreviewPlayer =
        players.getOrPut(trackId) { AudioPreviewPlayer(context) }
}
