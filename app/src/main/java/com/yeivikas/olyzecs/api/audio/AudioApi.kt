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
 * en la auditoría del diseño (ELINER_API_V1_AUDITORIA_DISENO.txt —
 * documento de proceso histórico, no versionado en este repositorio —
 * sección 1).
 *
 * [AudioClip] se reutiliza directo, incluyendo `sourceUri: Uri` — ver
 * la misma auditoría, sección 3 (decisión ya cerrada, aplica ADR-002).
 *
 * IMPLEMENTADO (actualizado en Fase 4.1): igual que `LayerApi`/
 * `CameraApi` — [AudioApiImpl] ya está conectado de verdad, pero sigue
 * PARCIAL (sin consumidor externo real; la UI sigue llamando a
 * `EditorViewModel` directo).
 */
interface AudioApi {

    /** Clip de audio activo del proyecto (null si no tiene). */
    fun getAudioClip(): AudioClip?

    /**
     * Fija/reemplaza el clip de audio del proyecto a partir de
     * [sourceUri].
     *
     * Devuelve `false` (ADR-003: falla RECUPERABLE, no crítica) si no
     * se pudo determinar una duración válida para el archivo — en ese
     * caso el clip NO se fija y el proyecto queda como estaba. Revisado
     * en Fase 4.1: antes de este cambio la función devolvía `Unit`
     * incondicionalmente, así que un consumidor de esta API pública no
     * tenía forma de distinguir "audio fijado" de "operación no
     * ocurrida" — el `Boolean` es la modificación mínima que resuelve
     * eso sin introducir un `Result<T>`/`EliNerError` genérico (que
     * ADR-003 pide evitar si no es estrictamente necesario). No hay
     * consumidor real todavía que dependa del valor anterior (`Unit`),
     * así que este ajuste no rompe ningún wiring existente.
     */
    suspend fun setAudioClip(sourceUri: Uri): Boolean

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
