package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val TAG = "ReversedAudioCache"

/**
 * Caché en disco de versiones INVERTIDAS de un archivo de audio, para el
 * PREVIEW en vivo de un clip con `reversed = true`.
 *
 * Por qué existe: el preview usa `MediaPlayer`, que no puede reproducir
 * hacia atrás. La exportación NO usa esto (invierte el PCM en memoria,
 * ver `AudioProcessor.reverseFrames`); aquí se renderiza UNA vez por
 * archivo un WAV PCM 16-bit con los frames en orden inverso, y el
 * reproductor lo trata como si fuera el archivo fuente del clip — así el
 * recorte, el loop y la posición dentro del archivo funcionan con la misma
 * aritmética de siempre.
 *
 * Diseño:
 *  - Un único hilo de fondo (un render a la vez: cada uno ocupa el PCM
 *    completo en memoria, igual que el análisis de forma de onda).
 *  - El guardado en disco (nombre por hash, escritura atómica, purga) es de
 *    [PreviewWavCache], compartido con [DryTrackCache].
 */
object ReversedAudioCache {
    private val wavs = PreviewWavCache(dirName = "reversed_audio", maxFiles = 6)

    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    // Claves cuyo render ya falló en esta sesión — ver [ensureAsync].
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ReversedAudioCache").apply { isDaemon = true }
    }

    private fun keyOf(uri: Uri, durationMs: Long) = "$uri#$durationMs"

    /** El WAV invertido ya renderizado de [uri], o `null` si todavía no existe. */
    fun peek(context: Context, uri: Uri, durationMs: Long): File? =
        wavs.peek(context.applicationContext, keyOf(uri, durationMs))

    /**
     * Pide (sin bloquear) que se renderice la versión invertida de [uri] si
     * aún no existe ni se está generando. Idempotente: llamarla en cada tick
     * mientras falte no multiplica el trabajo.
     */
    fun ensureAsync(context: Context, uri: Uri, durationMs: Long, retryFailed: Boolean = false) {
        val app = context.applicationContext
        val key = keyOf(uri, durationMs)
        // Un render que ya falló (archivo no decodificable, sin memoria...) NO
        // se reintenta solo: el preview llama acá a ~20 Hz mientras reproduce,
        // y sin este freno cada llamada relanzaba una decodificación completa
        // del archivo en bucle (CPU/batería/log). Solo un pedido explícito del
        // usuario (`retryFailed = true`, al alternar Reverse) lo vuelve a intentar.
        if (retryFailed) failed.remove(key)
        if (key in failed) return
        if (wavs.peek(app, key) != null || !inFlight.add(key)) return
        executor.execute {
            var rendered = false
            try {
                rendered = render(app, uri, key)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "No se pudo renderizar la versión invertida del audio", t)
            } finally {
                if (!rendered) failed.add(key)
                inFlight.remove(key)
            }
        }
    }

    /** `true` si el WAV invertido quedó escrito; `false` si no se pudo decodificar el audio. */
    private fun render(context: Context, uri: Uri, key: String): Boolean {
        val decoded = AudioProcessor.decodeForAnalysis(context, uri) ?: run {
            AppLogger.w(TAG, "No se pudo decodificar el audio para invertirlo: $uri")
            return false
        }
        val reversed = AudioProcessor.reverseFrames(decoded.samples, decoded.channelCount)
        wavs.store(context, key, reversed, decoded.sampleRateHz, decoded.channelCount)
        return true
    }
}
