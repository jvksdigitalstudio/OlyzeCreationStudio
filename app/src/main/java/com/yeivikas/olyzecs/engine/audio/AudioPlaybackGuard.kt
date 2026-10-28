package com.yeivikas.olyzecs.engine.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Qué interrupciones del sistema detienen la reproducción del editor. Es una decisión de producto y vive aparte de Android para
 * poder probarla: un editor NO reanuda solo (el usuario puede estar en otra cosa), y una pérdida transitoria "con atenuación" (una
 * notificación) no justifica cortar el audio del proyecto.
 */
internal object AudioInterruptionPolicy {
    fun pausesPlayback(focusChange: Int): Boolean =
        focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
}

/**
 * Protege una sesión de reproducción frente al resto del sistema: pide el foco de audio mientras suena (una llamada, otra app de
 * música o un asistente lo reclaman) y escucha la desconexión de los auriculares (`ACTION_AUDIO_BECOMING_NOISY`, para que el audio
 * no salte de golpe al altavoz). Ante cualquiera de las dos avisa con [onInterrupted] —siempre en el hilo principal— y el dueño decide
 * (el ViewModel pausa).
 *
 * [start] y [stop] son idempotentes: pueden llamarse en cada sincronización.
 */
internal class AudioPlaybackGuard(context: Context, private val onInterrupted: () -> Unit) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null

    /** Empieza a proteger la sesión. Si el sistema niega el foco (p. ej. hay una llamada en curso) se informa como una interrupción. */
    fun start() {
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { change -> if (AudioInterruptionPolicy.pausesPlayback(change)) onInterrupted() }
            .build()
        focusRequest = request
        registerNoisyReceiver()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Se avisa DESPUÉS de volver: quien llama a `start` está en mitad de una sincronización de estado.
            mainHandler.post(onInterrupted)
        }
    }

    /** Termina la sesión: devuelve el foco y deja de escuchar los auriculares. */
    fun stop() {
        val request = focusRequest ?: return
        focusRequest = null
        audioManager.abandonAudioFocusRequest(request)
        unregisterNoisyReceiver()
    }

    private fun registerNoisyReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onInterrupted()
            }
        }
        noisyReceiver = receiver
        ContextCompat.registerReceiver(
            appContext, receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterNoisyReceiver() {
        val receiver = noisyReceiver ?: return
        noisyReceiver = null
        try {
            appContext.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // ya estaba dado de baja: no hay nada que deshacer
        }
    }
}
