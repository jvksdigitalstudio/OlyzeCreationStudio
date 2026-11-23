package com.yeivikas.olyzecs.engine.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Forma de onda de ALTA resolución para la ventana ampliada de un clip
 * ([com.yeivikas.olyzecs.ui.AudioClipDetailPanel]): conserva las muestras
 * reales (mono) y una PIRÁMIDE de mínimos/máximos/energía por niveles, igual
 * que los editores de audio profesionales (Audacity, Reaper, Logic…). Así el
 * zoom nunca "adivina": cada columna de pantalla se calcula con los picos
 * reales de las muestras que cubre, desde el archivo completo hasta una sola
 * muestra, en tiempo constante por columna (no recorre todo el audio en cada
 * cuadro del gesto).
 *
 * Diferencia con [AudioWaveform] (la del carril): esa guarda un pico/RMS cada
 * ~5 ms normalizado contra el pico del archivo, ideal para dibujar clips
 * chicos pero insuficiente para ampliar (se vería en bloques). Esta guarda
 * amplitud REAL (−1..1 de escala completa, sin normalizar) con signo.
 *
 * Reducción a mono: por frame se toma la muestra de mayor magnitud entre los
 * canales (con su signo), coherente con el pico por-canal que usa [AudioWaveform].
 *
 * Memoria: ~2 bytes por frame + ~14 % de la pirámide. [MAX_FRAMES] acota el
 * costo (≈ 3 min a 44,1 kHz); más allá, [from] devuelve `null` y la ventana usa
 * la forma de onda del carril con un zoom máximo acorde a su resolución.
 */
class FineWaveform private constructor(
    val sampleRateHz: Int,
    val frames: Int,
    private val raw: ShortArray,
    private val blockSizes: IntArray,
    private val levelMin: Array<ShortArray>,
    private val levelMax: Array<ShortArray>,
    private val levelSq: Array<FloatArray>
) {
    /** Duración total del audio en ms (con decimales). */
    val durationMs: Double get() = frames * 1000.0 / sampleRateHz

    /** Amplitud (−1..1) de la muestra [frame]; 0 fuera del archivo. */
    fun sampleAt(frame: Int): Float = if (frame in 0 until frames) raw[frame] / 32768f else 0f

    /**
     * Calcula mínimo, máximo y RMS (−1..1 de escala completa) de las muestras que
     * cubre `[startFrame, endFrame)` y los escribe en la posición [col] de los
     * tres arreglos de salida. Fuera del archivo (o rango vacío) escribe ceros.
     *
     * Elige el nivel de la pirámide más grueso cuyos bloques entran al menos dos
     * veces en el rango (así el error de borde es < 50 % de una columna y el costo
     * por columna queda acotado); con rangos menores a 32 muestras lee las
     * muestras crudas. Los bordes de bloque se incluyen enteros (conservador: un
     * transitorio jamás desaparece al alejar el zoom).
     */
    fun sampleColumn(
        startFrame: Double,
        endFrame: Double,
        col: Int,
        outMin: FloatArray,
        outMax: FloatArray,
        outRms: FloatArray
    ) {
        if (frames <= 0 || endFrame <= 0.0 || startFrame >= frames) {
            outMin[col] = 0f
            outMax[col] = 0f
            outRms[col] = 0f
            return
        }
        val span = max(endFrame - startFrame, 0.0)
        var level = -1
        for (i in blockSizes.indices) {
            if (blockSizes[i] * 2.0 <= span) level = i else break
        }

        var mn = Int.MAX_VALUE
        var mx = Int.MIN_VALUE
        var sumSq = 0.0
        var count = 0L
        if (level < 0) {
            val first = floor(startFrame).toInt().coerceIn(0, frames - 1)
            val last = (ceil(endFrame).toInt() - 1).coerceIn(first, frames - 1)
            for (f in first..last) {
                val v = raw[f].toInt()
                if (v < mn) mn = v
                if (v > mx) mx = v
                val n = v / 32768.0
                sumSq += n * n
            }
            count = (last - first + 1).toLong()
        } else {
            val block = blockSizes[level]
            val mins = levelMin[level]
            val maxs = levelMax[level]
            val sqs = levelSq[level]
            val size = mins.size
            val first = floor(startFrame / block).toInt().coerceIn(0, size - 1)
            val last = (ceil(endFrame / block).toInt() - 1).coerceIn(first, size - 1)
            for (b in first..last) {
                val lo = mins[b].toInt()
                val hi = maxs[b].toInt()
                if (lo < mn) mn = lo
                if (hi > mx) mx = hi
                sumSq += sqs[b].toDouble()
                // El último bloque puede estar incompleto.
                count += if (b == size - 1) (frames - b * block).toLong() else block.toLong()
            }
        }
        outMin[col] = mn / 32768f
        outMax[col] = mx / 32768f
        outRms[col] = if (count > 0L) sqrt(sumSq / count).toFloat() else 0f
    }

    companion object {
        /** Tamaños de bloque (en frames) de la pirámide; cada nivel agrupa 4 del anterior (el primero, 16 muestras crudas). */
        private val BLOCK_SIZES = intArrayOf(16, 64, 256, 1024, 4096, 16384)

        /** Tope de frames que se conservan (≈ 3 min a 44,1 kHz): ~16 MB de muestras + pirámide. */
        const val MAX_FRAMES = 8_000_000

        /**
         * Construye la forma de onda fina desde PCM decodificado; `null` si está
         * vacío, tiene sample rate inválido o supera [MAX_FRAMES].
         */
        internal fun from(decoded: DecodedPcm): FineWaveform? {
            val channels = max(1, decoded.channelCount)
            val sampleRate = decoded.sampleRateHz
            val frames = decoded.samples.size / channels
            if (frames <= 0 || sampleRate <= 0 || frames > MAX_FRAMES) return null

            val samples = decoded.samples
            val mono = ShortArray(frames)
            if (channels == 1) {
                System.arraycopy(samples, 0, mono, 0, frames)
            } else {
                for (f in 0 until frames) {
                    val base = f * channels
                    var best = samples[base]
                    for (c in 1 until channels) {
                        val s = samples[base + c]
                        // toInt() antes de abs(): abs(Short.MIN_VALUE) en Short desborda.
                        if (abs(s.toInt()) > abs(best.toInt())) best = s
                    }
                    mono[f] = best
                }
            }
            return fromMono(mono, sampleRate)
        }

        /** Construye la pirámide sobre muestras mono ya reducidas. */
        internal fun fromMono(mono: ShortArray, sampleRateHz: Int): FineWaveform {
            val frames = mono.size
            val usable = BLOCK_SIZES.count { it <= frames }
            val sizes = IntArray(usable) { BLOCK_SIZES[it] }
            val mins = ArrayList<ShortArray>(usable)
            val maxs = ArrayList<ShortArray>(usable)
            val sqs = ArrayList<FloatArray>(usable)

            for (i in 0 until usable) {
                val block = sizes[i]
                val count = ceil(frames.toDouble() / block).toInt()
                val mn = ShortArray(count)
                val mx = ShortArray(count)
                val sq = FloatArray(count)
                if (i == 0) {
                    for (b in 0 until count) {
                        val start = b * block
                        val end = min(frames, start + block)
                        var lo = Int.MAX_VALUE
                        var hi = Int.MIN_VALUE
                        var acc = 0.0
                        for (f in start until end) {
                            val v = mono[f].toInt()
                            if (v < lo) lo = v
                            if (v > hi) hi = v
                            val n = v / 32768.0
                            acc += n * n
                        }
                        mn[b] = lo.toShort()
                        mx[b] = hi.toShort()
                        sq[b] = acc.toFloat()
                    }
                } else {
                    val ratio = block / sizes[i - 1]
                    val prevMin = mins[i - 1]
                    val prevMax = maxs[i - 1]
                    val prevSq = sqs[i - 1]
                    for (b in 0 until count) {
                        val from = b * ratio
                        val to = min(prevMin.size, from + ratio)
                        var lo = Int.MAX_VALUE
                        var hi = Int.MIN_VALUE
                        var acc = 0.0
                        for (j in from until to) {
                            val a = prevMin[j].toInt()
                            val c = prevMax[j].toInt()
                            if (a < lo) lo = a
                            if (c > hi) hi = c
                            acc += prevSq[j].toDouble()
                        }
                        mn[b] = lo.toShort()
                        mx[b] = hi.toShort()
                        sq[b] = acc.toFloat()
                    }
                }
                mins.add(mn)
                maxs.add(mx)
                sqs.add(sq)
            }
            return FineWaveform(
                sampleRateHz = sampleRateHz,
                frames = frames,
                raw = mono,
                blockSizes = sizes,
                levelMin = mins.toTypedArray(),
                levelMax = maxs.toTypedArray(),
                levelSq = sqs.toTypedArray()
            )
        }
    }
}
