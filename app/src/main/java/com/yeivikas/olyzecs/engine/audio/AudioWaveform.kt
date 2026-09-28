package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Forma de onda de un archivo de audio, resumida en cubetas de tiempo fijo,
 * independiente del zoom del timeline: se calcula UNA vez por archivo y
 * después se remuestrea a las columnas de píxeles que haga falta en cada
 * nivel de zoom (ver [sampleColumns]) sin volver a tocar el audio.
 *
 * Por cada cubeta se guardan dos valores, ambos normalizados a 0..1 contra
 * el pico global del archivo (así un audio grabado bajo igual muestra su
 * rango dinámico completo, en vez de una línea casi plana):
 *  - [peaks]: amplitud máxima absoluta de cualquier canal — el contorno
 *    exterior de la señal (lo que dibuja FL Studio).
 *  - [rms]: energía media de la cubeta — el "cuerpo" de la señal. La
 *    diferencia entre pico y RMS es justamente lo que deja ver la
 *    dinámica (transitorios contra sostenido).
 *
 * @property bucketDurationMs duración de cada cubeta, en ms (fraccionaria:
 *   depende del sample rate del archivo).
 * @property durationMs duración total del audio decodificado.
 */
class AudioWaveform(
    val peaks: FloatArray,
    val rms: FloatArray,
    val bucketDurationMs: Double,
    val durationMs: Long
) {
    val bucketCount: Int get() = peaks.size

    /** `true` si no hay nada que dibujar (archivo vacío o ilegible). */
    val isEmpty: Boolean get() = peaks.isEmpty() || bucketDurationMs <= 0.0

    /**
     * Remuestrea la forma de onda a [columns] columnas consecutivas de
     * [msPerColumn] ms cada una, arrancando en [startMs] del archivo
     * original (típicamente `AudioClip.trimStartMs`: el recorte de inicio
     * desplaza qué tramo del archivo cae dentro del clip).
     *
     * Cada columna toma el MÁXIMO de pico de las cubetas que cubre (para
     * que un transitorio nunca desaparezca al alejar el zoom) y la energía
     * RMS combinada de esas mismas cubetas. Las columnas que caen más allá
     * del final del archivo quedan en 0.
     *
     * Escribe en [peakOut]/[rmsOut] (deben tener al menos [columns]
     * posiciones) en vez de devolver arreglos nuevos, para poder reusar los
     * buffers entre recomposiciones.
     */
    fun sampleColumns(
        startMs: Double,
        msPerColumn: Double,
        columns: Int,
        peakOut: FloatArray,
        rmsOut: FloatArray
    ) {
        require(peakOut.size >= columns && rmsOut.size >= columns) { "buffers de salida más chicos que 'columns'" }
        val n = peaks.size
        for (col in 0 until columns) {
            val t0 = startMs + col * msPerColumn
            val t1 = t0 + msPerColumn
            if (n == 0 || bucketDurationMs <= 0.0 || t0 >= durationMs) {
                peakOut[col] = 0f
                rmsOut[col] = 0f
                continue
            }
            val first = floor(t0 / bucketDurationMs).toInt().coerceIn(0, n - 1)
            val last = (ceil(t1 / bucketDurationMs).toInt() - 1).coerceIn(first, n - 1)
            var peak = 0f
            var sumSq = 0.0
            for (i in first..last) {
                if (peaks[i] > peak) peak = peaks[i]
                val r = rms[i].toDouble()
                sumSq += r * r
            }
            peakOut[col] = peak
            rmsOut[col] = sqrt(sumSq / (last - first + 1)).toFloat()
        }
    }
}

/**
 * Calcula (y cachea) la [AudioWaveform] de un archivo de audio.
 *
 * Decodifica con el MISMO pipeline que el export ([AudioProcessor],
 * parser WAV manual incluido), así todo audio que se puede exportar
 * también se puede dibujar. El PCM completo vive en memoria solo durante
 * el análisis (mismo orden de magnitud que ya asume el export: audios de
 * fondo de unos pocos minutos) y se descarta enseguida; lo que queda en
 * caché son dos `FloatArray` de a lo sumo [MAX_BUCKETS] posiciones.
 */
object AudioWaveformAnalyzer {

    /** Resolución objetivo: una cubeta cada ~5 ms — más fino que 1 px hasta niveles de zoom muy altos. */
    private const val TARGET_BUCKET_MS = 5.0

    /**
     * Tope de cubetas por archivo (~480 KB entre pico y RMS). En audios muy
     * largos la cubeta se ensancha en vez de crecer sin límite.
     */
    private const val MAX_BUCKETS = 60_000

    private const val CACHE_ENTRIES = 8

    // LinkedHashMap en orden de acceso = LRU, sin depender de android.util
    // (así el análisis se puede probar como JVM unit test puro).
    private val cache = object : LinkedHashMap<String, AudioWaveform>(CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioWaveform>?): Boolean =
            size > CACHE_ENTRIES
    }

    // Un solo decodificador a la vez: cada uno ocupa el PCM completo en
    // memoria, y varias filas/recomposiciones pidiendo el mismo archivo no
    // deben multiplicar ese costo.
    private val decodeMutex = Mutex()

    /**
     * Devuelve la forma de onda de [uri], o `null` si no se pudo decodificar
     * (el motivo queda en el log técnico, no en el error visible al
     * usuario — ver [AudioProcessor.decodeForAnalysis]). Se ejecuta fuera
     * del hilo principal.
     *
     * [sourceDurationMs] entra en la clave de caché junto con el Uri: al
     * guardar un proyecto, el audio se copia a una ruta local que puede
     * repetirse entre reemplazos — la duración distingue el contenido nuevo
     * del viejo.
     */
    suspend fun load(context: Context, uri: Uri, sourceDurationMs: Long): AudioWaveform? {
        val key = "$uri#$sourceDurationMs"
        synchronized(cache) { cache[key] }?.let { return it }
        return withContext(Dispatchers.Default) {
            decodeMutex.withLock {
                // Otra corrutina pudo haberlo calculado mientras esperábamos el candado.
                synchronized(cache) { cache[key] }?.let { return@withLock it }
                val decoded = AudioProcessor.decodeForAnalysis(context, uri) ?: return@withLock null
                val waveform = analyze(decoded)
                synchronized(cache) { cache[key] = waveform }
                waveform
            }
        }
    }

    /** Análisis puro sobre PCM ya decodificado — separado de [load] para poder probarlo sin archivos ni Android. */
    internal fun analyze(decoded: DecodedPcm): AudioWaveform {
        val channels = max(1, decoded.channelCount)
        val sampleRate = decoded.sampleRateHz
        val frames = decoded.samples.size / channels
        if (frames <= 0 || sampleRate <= 0) {
            return AudioWaveform(FloatArray(0), FloatArray(0), TARGET_BUCKET_MS, 0L)
        }

        val durationMs = frames * 1000L / sampleRate
        val idealBucketFrames = max(1, (sampleRate * TARGET_BUCKET_MS / 1000.0).roundToInt())
        val cappedBucketFrames = ceil(frames.toDouble() / MAX_BUCKETS).toInt()
        val bucketFrames = max(idealBucketFrames, cappedBucketFrames)
        val bucketCount = ceil(frames.toDouble() / bucketFrames).toInt()

        val peaks = FloatArray(bucketCount)
        val rms = FloatArray(bucketCount)
        val samples = decoded.samples
        var globalPeak = 0f

        for (bucket in 0 until bucketCount) {
            val startFrame = bucket * bucketFrames
            val endFrame = min(frames, startFrame + bucketFrames)
            var peak = 0
            var sumSq = 0.0
            for (frame in startFrame until endFrame) {
                val base = frame * channels
                for (c in 0 until channels) {
                    // toInt() antes de abs(): abs(Short.MIN_VALUE) en Short desborda.
                    val v = kotlin.math.abs(samples[base + c].toInt())
                    if (v > peak) peak = v
                    sumSq += v.toDouble() * v
                }
            }
            val count = (endFrame - startFrame) * channels
            peaks[bucket] = peak / 32768f
            rms[bucket] = if (count > 0) (sqrt(sumSq / count) / 32768.0).toFloat() else 0f
            if (peaks[bucket] > globalPeak) globalPeak = peaks[bucket]
        }

        // Normalización contra el pico global: el audio más fuerte del
        // archivo llena la altura disponible y todo lo demás queda en
        // proporción. Silencio absoluto (globalPeak == 0) se deja en cero.
        if (globalPeak > 0f) {
            val scale = 1f / globalPeak
            for (i in 0 until bucketCount) {
                peaks[i] = min(1f, peaks[i] * scale)
                rms[i] = min(1f, rms[i] * scale)
            }
        }

        return AudioWaveform(
            peaks = peaks,
            rms = rms,
            bucketDurationMs = bucketFrames * 1000.0 / sampleRate,
            durationMs = durationMs
        )
    }
}
