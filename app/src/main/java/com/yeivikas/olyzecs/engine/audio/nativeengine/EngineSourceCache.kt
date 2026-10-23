package com.yeivikas.olyzecs.engine.audio.nativeengine

import android.content.Context
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.audio.AudioProcessor
import com.yeivikas.olyzecs.engine.audio.PreviewWavCache
import com.yeivikas.olyzecs.engine.audio.decode.GaplessWindow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Prepara las FUENTES de audio del motor nativo. Cada archivo del proyecto pasa por un pipeline en STREAMING:
 *
 *  1. se decodifica con el decodificador único del proyecto ([AudioProcessor.streamForAnalysis], el mismo que la exportación)
 *     y se escribe a un WAV estéreo intermedio a medida que llega ([StereoWavFileSink]): el audio nunca está entero en la JVM;
 *  2. el motor nativo recorta el retardo/relleno del codificador ([GaplessWindow]), remuestrea a la tasa fija del motor (sinc
 *     de alta calidad) y deja el WAV final en una caché en disco, que el motor MAPEA en memoria.
 *
 * Es el equivalente a las "sources" de Ardour: el clip nunca es el archivo, solo una referencia a esta fuente ya lista.
 *
 * Todo el trabajo pesado va en UN hilo de fondo (los decodificadores compiten por CPU y memoria con la UI y la exportación;
 * encolarlos evita picos con varios archivos nuevos a la vez).
 */
internal object EngineSourceCache {
    private const val TAG = "EngineSourceCache"
    private const val MAX_FILES = 64

    private val wavs = PreviewWavCache("elinerSources", MAX_FILES)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "eliner-source-prep").apply { isDaemon = true } }
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /**
     * Identidad de una fuente. Incluye la tasa del motor: si algún día cambia, las cachés viejas no se reutilizan mal.
     * (`uri#duración` es la misma convención de `ReversedAudioCache`.)
     */
    fun keyOf(uri: Uri, durationMs: Long, engineRateHz: Int): String = "$uri#$durationMs@$engineRateHz"

    /** El WAV listo (a la tasa del motor), o `null` si aún no existe. */
    fun peek(context: Context, key: String): File? = wavs.peek(context.applicationContext, key)

    fun hasFailed(key: String): Boolean = key in failed

    /**
     * Prepara [uri] en segundo plano si hace falta. [onDone] se invoca (en el hilo de fondo) al terminar, con `true` si la
     * fuente quedó lista. No hace nada si ya existe o ya está en curso. [protectedKeys]: fuentes aún en uso, que la purga
     * LRU no debe borrar.
     */
    fun request(
        context: Context,
        uri: Uri,
        durationMs: Long,
        engineRateHz: Int,
        protectedKeys: Collection<String>,
        onDone: (key: String, ok: Boolean) -> Unit
    ) {
        val app = context.applicationContext
        val key = keyOf(uri, durationMs, engineRateHz)
        if (key in failed) return
        if (wavs.peek(app, key) != null || !inFlight.add(key)) return
        executor.execute {
            var ok = false
            try {
                ok = prepare(app, uri, key, protectedKeys)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "No se pudo preparar la fuente de audio: $uri", t)
            } finally {
                if (!ok) failed.add(key)
                inFlight.remove(key)
            }
            onDone(key, ok)
        }
    }

    private fun prepare(context: Context, uri: Uri, key: String, protectedKeys: Collection<String>): Boolean {
        val target = wavs.targetFile(context, key)
        val intermediate = File(target.parentFile, target.name + ".src.wav")
        try {
            val sink = StereoWavFileSink(intermediate)
            val info = sink.use { AudioProcessor.streamForAnalysis(context, uri, it) }
            if (info == null) {
                AppLogger.w(TAG, "No se pudo decodificar el audio: $uri")
                return false
            }
            val window = GaplessWindow.of(sink.framesWritten, info.encoderDelayFrames, info.encoderPaddingFrames)
            val error = NativeEngineBindings.nativeConvertWavToEngineRate(
                intermediate.path, target.path, window.startFrame, window.frameCount
            )
            if (error != null) {
                AppLogger.w(TAG, "Conversión de tasa fallida para $uri: $error")
                return false
            }
        } finally {
            intermediate.delete()
        }
        wavs.finishExternalWrite(context, key, protectedKeys)
        return true
    }
}
