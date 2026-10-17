package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val TAG = "ReversedAudioCache"

/** Bytes de la cabecera WAV/PCM canónica de 44 bytes que escribe [writeWav16]. */
internal const val WAV_HEADER_BYTES = 44

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
 *  - Escritura atómica (archivo temporal + rename): el reproductor jamás
 *    ve un WAV a medio escribir.
 *  - Solo se conservan los [MAX_FILES] más recientes (es caché, no datos
 *    del usuario: si se pierde, se vuelve a renderizar).
 */
object ReversedAudioCache {
    private const val DIR = "reversed_audio"
    private const val MAX_FILES = 6

    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    // Claves cuyo render ya falló en esta sesión — ver [ensureAsync].
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ReversedAudioCache").apply { isDaemon = true }
    }

    private fun keyOf(uri: Uri, durationMs: Long) = "$uri#$durationMs"

    // Nombre de archivo derivado de un SHA-256 de la clave (antes: hashCode de 32
    // bits + largo): con pocos archivos la colisión era improbable pero posible, y
    // una colisión haría sonar el audio invertido de OTRO archivo.
    private fun fileFor(context: Context, key: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = digest.take(16).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, DIR), "$name.wav")
    }

    /** El WAV invertido ya renderizado de [uri], o `null` si todavía no existe. */
    fun peek(context: Context, uri: Uri, durationMs: Long): File? =
        fileFor(context.applicationContext, keyOf(uri, durationMs))
            .takeIf { it.isFile && it.length() > WAV_HEADER_BYTES }

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
        if (peek(app, uri, durationMs) != null || !inFlight.add(key)) return
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
        val target = fileFor(context, key)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            tmp.outputStream().buffered().use { writeWav16(it, reversed, decoded.sampleRateHz, decoded.channelCount) }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        purgeOld(target.parentFile, keep = target)
        return true
    }

    private fun purgeOld(dir: File?, keep: File) {
        // Temporales huérfanos: si el proceso muere a mitad de un render queda un
        // `.wav.tmp` que ningún filtro de `.wav` volvía a ver (se acumulaban para siempre).
        dir?.listFiles { f -> f.isFile && f.name.endsWith(".tmp") }?.forEach { tmp ->
            if (tmp.name != keep.name + ".tmp") tmp.delete()
        }
        val files = dir?.listFiles { f -> f.isFile && f.name.endsWith(".wav") } ?: return
        if (files.size <= MAX_FILES) return
        files.filter { it != keep }
            .sortedBy { it.lastModified() }
            .take(files.size - MAX_FILES)
            .forEach { it.delete() }
    }
}

/**
 * Escribe [samples] (16-bit, intercalado por canal) como un WAV PCM
 * canónico de 44 bytes de cabecera. Pura sobre un [OutputStream] — se
 * prueba con un `ByteArrayOutputStream`, sin archivos ni Android.
 */
internal fun writeWav16(out: OutputStream, samples: ShortArray, sampleRateHz: Int, channels: Int) {
    val dataBytes = samples.size.toLong() * 2L
    val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt((36L + dataBytes).coerceAtMost(0xFFFFFFFFL).toInt())
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)                                   // tamaño del bloque fmt
        putShort(1)                                  // PCM
        putShort(channels.toShort())
        putInt(sampleRateHz)
        putInt(sampleRateHz * channels * 2)          // byte rate
        putShort((channels * 2).toShort())           // block align
        putShort(16)                                 // bits por muestra
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(dataBytes.coerceAtMost(0xFFFFFFFFL).toInt())
    }
    out.write(header.array())
    // En bloques: evita duplicar todo el PCM en un ByteArray gigante.
    val chunkSamples = 32 * 1024
    val buf = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
    var i = 0
    while (i < samples.size) {
        val n = minOf(chunkSamples, samples.size - i)
        buf.clear()
        for (k in 0 until n) buf.putShort(samples[i + k])
        out.write(buf.array(), 0, n * 2)
        i += n
    }
    out.flush()
}
