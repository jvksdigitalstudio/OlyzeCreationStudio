package com.yeivikas.olyzecs.engine.audio

import android.net.Uri

/**
 * Identidad de la FUENTE de audio de un clip tal como la ve la interfaz: el archivo ([uri]) y su duración. Es la misma pareja con la
 * que el motor nativo decide qué fuente prepara (sin la tasa del motor, que la interfaz no conoce ni necesita): dos clips del mismo
 * archivo comparten fuente y, por tanto, estado. Tipos simples a propósito (texto y número, no `Uri`) para poder construirla y compararla
 * en pruebas sin Android.
 */
data class AudioSourceId(val uri: String, val durationMs: Long) {
    companion object {
        fun of(clip: AudioClip): AudioSourceId = AudioSourceId(clip.sourceUri.toString(), clip.sourceDurationMs)

        fun of(uri: Uri, durationMs: Long): AudioSourceId = AudioSourceId(uri.toString(), durationMs)
    }
}

/**
 * Qué le pasa al audio de un clip mientras NO está listo para sonar. Una fuente lista no aparece (la ausencia de estado ES "lista", o el
 * clip está silenciado y el motor no la pide). Es lo único que la interfaz necesita para explicar un clip mudo en vez de dejarlo así sin
 * motivo.
 *
 * Público (a diferencia del estado interno de la cola de preparación) porque viaja por `AudioClipEditActions`, que es público.
 */
sealed interface AudioSourceState {
    /** En cola o decodificándose por primera vez. Casi siempre dura una fracción de segundo; la interfaz no lo muestra hasta pasado un rato. */
    object Preparing : AudioSourceState

    /** El intento anterior falló por algo que puede pasar solo; se vuelve a intentar sin intervención. [attempt] de [maxAttempts]. */
    data class Retrying(val attempt: Int, val maxAttempts: Int) : AudioSourceState

    /** Agotó sus intentos (o falló de forma que repetir no ayuda). El clip no sonará hasta que el usuario pida reintentar. */
    data class Failed(val reason: String) : AudioSourceState
}
