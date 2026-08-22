package com.yeivikas.olyzecs.engine.audio

import android.net.Uri

/**
 * Clip de audio de fondo del proyecto, en memoria (análogo a [com.yeivikas.olyzecs.engine.scene.Layer]
 * pero a nivel de proyecto entero: solo puede haber uno). [sourceUri] apunta al
 * archivo ya copiado localmente por `ProjectStorage` una vez guardado, o al
 * Uri de SAF recién elegido antes del primer guardado.
 */
class AudioClip(
    var sourceUri: Uri,
    var displayName: String,
    /** Duración total del ARCHIVO de audio original (no del proyecto). */
    var sourceDurationMs: Long,
    /** 0f = silencio, 1f = volumen original, hasta 1.5f para dar algo de boost. */
    var volume: Float = 1f,
    var muted: Boolean = false,
    /** Punto del archivo original donde arranca a sonar (permite recortar el inicio). */
    var trimStartMs: Long = 0L,
    /** Si el audio es más corto que la duración del proyecto, lo repite en loop. */
    var loop: Boolean = true,
    var fadeInMs: Long = 400L,
    var fadeOutMs: Long = 600L
) {
    /**
     * Crea una copia con los campos indicados reemplazados. Se usa en el
     * ViewModel en vez de mutar esta instancia in-place: reemplazar la
     * REFERENCIA (no solo el contenido) es lo que garantiza, sin ninguna
     * ambigüedad, que Compose y el `equals()` de [EditorUiState] vean el
     * cambio como un estado genuinamente nuevo y disparen recomposición —
     * mutar un campo de una instancia ya existente puede quedar "invisible"
     * para código que compara por referencia.
     */
    fun copy(
        volume: Float = this.volume,
        muted: Boolean = this.muted,
        trimStartMs: Long = this.trimStartMs,
        loop: Boolean = this.loop,
        fadeInMs: Long = this.fadeInMs,
        fadeOutMs: Long = this.fadeOutMs
    ): AudioClip = AudioClip(
        sourceUri = sourceUri,
        displayName = displayName,
        sourceDurationMs = sourceDurationMs,
        volume = volume,
        muted = muted,
        trimStartMs = trimStartMs,
        loop = loop,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs
    )
}
