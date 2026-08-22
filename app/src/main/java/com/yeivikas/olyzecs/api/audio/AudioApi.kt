package com.yeivikas.olyzecs.api.audio

import android.net.Uri
import com.yeivikas.olyzecs.engine.audio.AudioClip

/**
 * Contrato público de EliNer para Audio.
 *
 * Respaldo real: `engine.audio.AudioPreviewPlayer`
 * (`playFrom`/`pause`/`seekToProjectTime`/`updateVolume`),
 * `engine.audio.AudioProcessor.probeDurationMs`, y los 5 setters de
 * `EditorViewModel` (`setAudioVolume`/`setAudioTrimStart`/
 * `setAudioLoop`/`setAudioFade`/mute) detectados como gap y agregados
 * en la auditoría del diseño (ELINER_API_V1_AUDITORIA_DISENO.txt,
 * sección 1).
 *
 * [AudioClip] se reutiliza directo, incluyendo `sourceUri: Uri` — ver
 * la misma auditoría, sección 3 (decisión ya cerrada, aplica ADR-002).
 *
 * NO implementado todavía — mismo motivo que `LayerApi`/`CameraApi`.
 */
interface AudioApi {

    /** Clip de audio activo del proyecto (null si no tiene). */
    fun getAudioClip(): AudioClip?

    /** Fija/reemplaza el clip de audio del proyecto. */
    suspend fun setAudioClip(sourceUri: Uri)

    /** Quita el audio del proyecto. */
    suspend fun clearAudioClip()

    suspend fun setVolume(volume: Float)
    suspend fun setMuted(muted: Boolean)
    suspend fun setTrimStart(trimStartMs: Long)
    suspend fun setLoop(loop: Boolean)
    suspend fun setFade(fadeInMs: Long, fadeOutMs: Long)

    /** Duración del archivo de audio original (no la del proyecto). */
    suspend fun probeDurationMs(sourceUri: Uri): Long

    fun playFrom(projectTimeMs: Long)
    fun pause()
    fun seekTo(projectTimeMs: Long)
}
