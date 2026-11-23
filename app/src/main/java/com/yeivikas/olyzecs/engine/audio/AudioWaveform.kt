package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
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
 * nivel de zoom (ver [sampleColumnsLooped]) sin volver a tocar el audio.
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
    val durationMs: Long,
    /**
     * Pico ABSOLUTO real del archivo (0..1 de escala completa), ANTES de
     * normalizar [peaks]/[rms] contra él. Es lo que permite calcular una
     * ganancia de normalización real (ver [absolutePeakInRange]).
     */
    val sourcePeak: Float = 1f
) {
    val bucketCount: Int get() = peaks.size

    /**
     * Pico absoluto real (0..1 de escala completa) dentro de
     * `[startMs, endMs)` del archivo, con la resolución de las cubetas
     * (conservador: cada cubeta que toca el rango cuenta entera). 0 si el
     * rango no cubre nada.
     */
    fun absolutePeakInRange(startMs: Long, endMs: Long): Float {
        if (isEmpty || endMs <= startMs) return 0f
        val lo = kotlin.math.floor(startMs.coerceAtLeast(0L) / bucketDurationMs).toInt().coerceIn(0, peaks.size)
        val hi = kotlin.math.ceil(endMs / bucketDurationMs).toInt().coerceIn(lo, peaks.size)
        var peak = 0f
        for (i in lo until hi) if (peaks[i] > peak) peak = peaks[i]
        return peak * sourcePeak
    }

    /** La misma forma de onda recorrida al revés — la de un clip invertido (Reverse). */
    fun reversedCopy(): AudioWaveform = AudioWaveform(
        peaks = peaks.reversedArray(),
        rms = rms.reversedArray(),
        bucketDurationMs = bucketDurationMs,
        durationMs = durationMs,
        sourcePeak = sourcePeak
    )

    /** `true` si no hay nada que dibujar (archivo vacío o ilegible). */
    val isEmpty: Boolean get() = peaks.isEmpty() || bucketDurationMs <= 0.0

    /**
     * Remuestrea la forma de onda a [columns] columnas consecutivas de
     * [msPerColumn] ms cada una, con la MISMA regla de reproducción que
     * `AudioProcessor.buildProjectSamples` (export): la primera pasada arranca
     * en [trimStartMs] (el recorte de inicio desplaza qué tramo del archivo
     * cae dentro del clip) y llega hasta el final del archivo; si [loop] es
     * `true`, las vueltas siguientes reinician desde el frame 0 del archivo
     * completo (el recorte NO se vuelve a aplicar). Con [loop] en `false`, lo
     * que sobra después de la primera pasada queda en silencio.
     *
     * Cada columna toma el MÁXIMO de pico de las cubetas que cubre (para que
     * un transitorio nunca desaparezca al alejar el zoom) y la energía RMS
     * combinada de esas mismas cubetas. `column 0` corresponde al borde
     * izquierdo del clip (instante 0 del clip), así lo que se dibuja coincide
     * con lo que suena.
     *
     * Escribe en [peakOut]/[rmsOut] (deben tener al menos [columns]
     * posiciones) en vez de devolver arreglos nuevos, para poder reusar los
     * buffers entre recomposiciones.
     */
    fun sampleColumnsLooped(
        trimStartMs: Double,
        loop: Boolean,
        msPerColumn: Double,
        columns: Int,
        peakOut: FloatArray,
        rmsOut: FloatArray
    ) {
        require(peakOut.size >= columns && rmsOut.size >= columns) { "buffers de salida más chicos que 'columns'" }
        val total = durationMs.toDouble()
        val firstPassMs = (total - trimStartMs).coerceAtLeast(0.0)
        for (col in 0 until columns) {
            val clipT0 = col * msPerColumn
            val srcT0 = when {
                clipT0 < firstPassMs -> trimStartMs + clipT0
                loop && total > 0.0 -> (clipT0 - firstPassMs) % total
                else -> Double.NaN
            }
            if (srcT0.isNaN()) {
                peakOut[col] = 0f
                rmsOut[col] = 0f
            } else {
                sampleRangeInto(srcT0, srcT0 + msPerColumn, col, peakOut, rmsOut)
            }
        }
    }

    /** Pico máximo y RMS combinado de las cubetas que cubren [t0, t1) ms del archivo, escritos en la columna [col]. */
    private fun sampleRangeInto(t0: Double, t1: Double, col: Int, peakOut: FloatArray, rmsOut: FloatArray) {
        val n = peaks.size
        if (n == 0 || bucketDurationMs <= 0.0 || t0 >= durationMs) {
            peakOut[col] = 0f
            rmsOut[col] = 0f
            return
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

    // Caché aparte (y más corta) para la forma de onda de alta resolución: ocupa
    // ~2 bytes por frame, bastante más que las cubetas de [AudioWaveform].
    private const val FINE_CACHE_ENTRIES = 2
    private val fineCache = object : LinkedHashMap<String, FineWaveform>(FINE_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FineWaveform>?): Boolean =
            size > FINE_CACHE_ENTRIES
    }

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
                // `isActive` es el de este withContext: si la corrutina que pidió la forma
                // de onda se cancela (la fila salió de pantalla, se cerró el proyecto), la
                // decodificación se corta en la próxima vuelta en vez de seguir ocupando
                // el candado (un solo decodificador a la vez) hasta terminar de balde.
                val decoded = AudioProcessor.decodeForAnalysis(context, uri) { !isActive } ?: return@withLock null
                val waveform = analyze(decoded)
                synchronized(cache) { cache[key] = waveform }
                waveform
            }
        }
    }

    /**
     * Forma de onda de ALTA resolución (muestras reales + pirámide de picos) para el
     * zoom profundo de la ventana ampliada, o `null` si no se pudo decodificar o el
     * audio supera [FineWaveform.MAX_FRAMES]. Misma política que [load] (clave de
     * caché, un solo decodificador a la vez, cancelable); se pide solo al abrir la
     * ventana, no por cada clip del carril.
     */
    suspend fun loadFine(context: Context, uri: Uri, sourceDurationMs: Long): FineWaveform? {
        val key = "$uri#$sourceDurationMs"
        synchronized(fineCache) { fineCache[key] }?.let { return it }
        return withContext(Dispatchers.Default) {
            decodeMutex.withLock {
                synchronized(fineCache) { fineCache[key] }?.let { return@withLock it }
                val decoded = AudioProcessor.decodeForAnalysis(context, uri) { !isActive } ?: return@withLock null
                val fine = FineWaveform.from(decoded) ?: return@withLock null
                synchronized(fineCache) { fineCache[key] = fine }
                fine
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
            durationMs = durationMs,
            sourcePeak = globalPeak
        )
    }
}
