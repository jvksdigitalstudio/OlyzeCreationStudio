package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger

private const val TAG = "AudioPreviewPlayer"

/**
 * Reproductor de audio para el PREVIEW en vivo del editor (botón Play),
 * separado por completo del pipeline de exportación (`AudioProcessor` +
 * `VideoExporter`), que es el que realmente mezcla el audio en el `.mp4`
 * final con precisión de sample. Este reproductor es una aproximación en
 * tiempo real con `MediaPlayer` para poder MONITOREAR cómo va a sonar
 * mientras se edita — no pretende ser frame-accurate como el export.
 *
 * Se reposiciona ("seekea") solo en dos momentos: al arrancar la
 * reproducción y al cambiar de audio/mute — nunca en cada tick del
 * playhead visual (eso generaría un tartamudeo audible); una vez arrancado,
 * corre con su propio reloj interno en paralelo al loop visual.
 */
class AudioPreviewPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var loadedUri: Uri? = null

    private fun ensureLoaded(clip: AudioClip): Boolean {
        if (loadedUri == clip.sourceUri && mediaPlayer != null) return true
        release()
        return try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, clip.sourceUri)
                isLooping = false // el loop del proyecto se maneja "a mano" en seekToProjectTime
                prepare()
            }
            loadedUri = clip.sourceUri
            true
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo preparar el preview de audio", t)
            mediaPlayer = null
            loadedUri = null
            false
        }
    }

    /** Arranca (o reanuda) la reproducción posicionada en [projectTimeMs] del timeline del proyecto. */
    fun playFrom(clip: AudioClip, projectTimeMs: Long) {
        // A PEDIDO EXPLÍCITO DEL USUARIO: el clip puede arrancar a sonar en
        // cualquier punto del proyecto, no solo en t=0 (ver
        // `AudioClip.timelineStartMs`, fijado arrastrando la pista en
        // `AudioTrackRow`). Si el playhead todavía no llegó a ese punto, no
        // hay nada que reproducir todavía.
        //
        // LÍMITE CONOCIDO, no un olvido: esto solo se evalúa acá, al
        // ARRANCAR la reproducción (mismo criterio que ya documentaba esta
        // clase para trim/loop/mute — ver el comentario de la clase). Si el
        // usuario aprieta Play ANTES de `timelineStartMs` y lo deja correr,
        // el audio NO arranca solo al cruzar ese punto durante la
        // reproducción en vivo — esa parte (arranque automático a mitad de
        // reproducción) queda pendiente de una fase siguiente, atada al
        // tick del playhead. La EXPORTACIÓN real (`AudioProcessor`) sí
        // respeta `timelineStartMs` con precisión de sample siempre, este
        // preview es solo una aproximación para monitorear mientras se
        // edita.
        if (clip.muted || projectTimeMs < clip.timelineStartMs) {
            pause()
            return
        }
        if (!ensureLoaded(clip)) return
        val player = mediaPlayer ?: return
        seekToProjectTime(clip, projectTimeMs)
        val vol = clip.volume.coerceIn(0f, 1f) // MediaPlayer no admite boost >1.0 como sí hace el export
        player.setVolume(vol, vol)
        try {
            if (!player.isPlaying) player.start()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo iniciar el preview de audio", t)
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) player.pause()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo pausar el preview de audio", t)
        }
    }

    /**
     * Reposiciona el audio para que coincida con [projectTimeMs] del
     * timeline, aplicando el mismo criterio de recorte/loop/posición que usa
     * [AudioProcessor.buildProjectSamples] en la exportación real: primero
     * se descuenta `timelineStartMs` (dónde arranca el clip DENTRO del
     * proyecto); la primera vuelta del archivo arranca en `trimStartMs`; si
     * hace falta más audio del que queda y `loop=true`, las vueltas
     * siguientes reinician desde el comienzo del archivo completo.
     */
    fun seekToProjectTime(clip: AudioClip, projectTimeMs: Long) {
        val player = mediaPlayer ?: return
        val sourceDurationMs = clip.sourceDurationMs
        if (sourceDurationMs <= 0L) return

        val timeIntoClip = (projectTimeMs - clip.timelineStartMs).coerceAtLeast(0L)
        val rawPos = clip.trimStartMs + timeIntoClip
        val sourcePos = if (rawPos < sourceDurationMs) {
            rawPos
        } else if (clip.loop) {
            val overflow = rawPos - sourceDurationMs
            overflow % sourceDurationMs
        } else {
            sourceDurationMs - 1
        }

        try {
            player.seekTo(sourcePos.coerceAtLeast(0L).toInt())
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo reposicionar el preview de audio", t)
        }
    }

    fun updateVolume(volume: Float) {
        val vol = volume.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(vol, vol)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo actualizar el volumen del preview de audio", t)
        }
    }

    fun release() {
        try {
            mediaPlayer?.release()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo liberar el MediaPlayer del preview de audio", t)
        }
        mediaPlayer = null
        loadedUri = null
    }
}
