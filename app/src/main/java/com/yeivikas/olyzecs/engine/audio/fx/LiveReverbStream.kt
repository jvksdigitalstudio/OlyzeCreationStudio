package com.yeivikas.olyzecs.engine.audio.fx

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// ============================================================================
// REVERB EN TIEMPO REAL — el motor de la vista previa de los carriles con Reverb. Kotlin puro (sin Android): lee el audio SECO de un
// carril por bloques desde un [PcmSource], lo pasa por [StereoReverb] y entrega PCM 16-bit listo para un AudioTrack en
// streaming. Cambiar un parámetro NO vuelve a renderizar nada: se aplica al instante (continuos, con suavizado) o con un
// crossfade corto (estructurales: size y pre-delay).
//
// Usa EXACTAMENTE la misma matemática que la exportación (`applyEffectChain`): sin cambios de parámetros ni saltos, lo que
// sale de aquí es, muestra a muestra, lo que se exporta (lo verifica LiveReverbStreamTest).
//
// Hilo: todo se llama desde UN solo hilo (el hilo de audio del reproductor). Los buffers de trabajo se reservan una vez; un
// bloque no asigna memoria. Solo [seekTo] y un cambio estructural crean un [StereoReverb] nuevo (eventos, no por bloque).
// ============================================================================

/** Duración del crossfade entre dos instancias al cambiar `size` o `preDelayMs`: lineal (el seco es idéntico en ambas y debe quedar a ganancia 1). */
private const val STRUCTURAL_CROSSFADE_SECONDS = 0.08

/** Audio previo al punto de un seek que se procesa en silencio para que el reverb ya "suene" ahí (como el pre-roll de un DAW). */
private const val SEEK_PREROLL_SECONDS = 1.5

private const val PCM16_SCALE = 32768f

/**
 * @param source audio seco del carril (con volumen, balance y fundidos ya aplicados, como lo mezcla la exportación).
 * @param sampleRate tasa de [source] (Hz).
 * @param initialParams parámetros iniciales del reverb.
 * @param maxBlockFrames tamaño máximo de bloque que se pedirá a [read] (se reservan los buffers para él).
 */
internal class LiveReverbStream(
    private val source: PcmSource,
    private val sampleRate: Int,
    initialParams: ReverbParams,
    private val maxBlockFrames: Int = DEFAULT_MAX_BLOCK_FRAMES
) {
    init {
        require(sampleRate > 0) { "Tasa inválida: $sampleRate" }
        require(maxBlockFrames > 0) { "Bloque inválido: $maxBlockFrames" }
    }

    private var params = initialParams.coerced()
    private var reverb = StereoReverb(sampleRate, params)

    /** Instancia entrante durante un crossfade estructural (`null` = no hay). */
    private var incoming: StereoReverb? = null
    private var crossfadeDone = 0
    private val crossfadeFrames = max(1, Math.round(STRUCTURAL_CROSSFADE_SECONDS * sampleRate).toInt())
    private val prerollFrames = Math.round(SEEK_PREROLL_SECONDS * sampleRate)

    private val inputShorts = ShortArray(maxBlockFrames * 2)
    private val inL = FloatArray(maxBlockFrames)
    private val inR = FloatArray(maxBlockFrames)
    private val outL = FloatArray(maxBlockFrames)
    private val outR = FloatArray(maxBlockFrames)
    private val incomingL = FloatArray(maxBlockFrames)
    private val incomingR = FloatArray(maxBlockFrames)

    /** Próxima muestra (por canal) que entregará [read]. */
    var positionFrames = 0L
        private set

    /** `true` mientras dura el crossfade de un cambio estructural. */
    val isCrossfading: Boolean get() = incoming != null

    /** Largo total: el audio del origen más la cola del reverb (para que no se corte en seco). Depende de los parámetros vigentes. */
    val totalFrames: Long get() = source.frames + ceil(params.tailSeconds() * sampleRate).toLong()

    val isFinished: Boolean get() = positionFrames >= totalFrames

    /**
     * Cambia los parámetros en vivo. Los continuos se aplican con suavizado ([StereoReverb.updateParams]); si cambia `size` o
     * `preDelayMs` se crea una instancia nueva y se hace un crossfade a ella (la cola que ya suena se apaga mientras la nueva
     * se construye). Un cambio estructural en pleno crossfade sustituye a la instancia entrante.
     */
    fun setParams(newParams: ReverbParams) {
        val coerced = newParams.coerced()
        if (coerced == params) return
        val pending = incoming
        if (pending != null) {
            if (!pending.updateParams(coerced)) incoming = StereoReverb(sampleRate, coerced)
            // La saliente sigue con su estructura; si solo cambian continuos, los sigue.
            reverb.updateParams(coerced)
        } else if (!reverb.updateParams(coerced)) {
            incoming = StereoReverb(sampleRate, coerced)
            crossfadeDone = 0
        }
        params = coerced
    }

    /**
     * Salta a [frame] (acotado al largo total). El reverb se reinicia y se calienta procesando en silencio hasta 1.5 s de
     * audio previo: así la cola ya suena en el punto de llegada en vez de empezar de cero.
     *
     * Con [warmupBudgetNanos] el calentamiento tiene un tope de tiempo: en un dispositivo lento (código aún sin compilar)
     * procesar 1.5 s de audio puede tardar cientos de milisegundos, y el audio no puede empezar hasta terminar. Tras el
     * primer bloque se mide la velocidad real; si el audio previo no cabe en el presupuesto, el calentamiento se reinicia desde
     * un punto más cercano (el tramo MÁS RECIENTE que sí cabe, contiguo hasta [frame]: lo que más pesa en la cola). Sin
     * presupuesto (por defecto) el resultado es siempre el mismo, sin depender del reloj.
     */
    fun seekTo(frame: Long, warmupBudgetNanos: Long = Long.MAX_VALUE) {
        val target = frame.coerceIn(0L, totalFrames)
        reverb = StereoReverb(sampleRate, params)
        incoming = null
        crossfadeDone = 0
        var position = max(0L, target - prerollFrames)
        val startedAt = System.nanoTime()
        var budgetChecked = warmupBudgetNanos == Long.MAX_VALUE
        while (position < target) {
            val n = min(maxBlockFrames.toLong(), target - position).toInt()
            loadInput(position, n)
            reverb.process(inL, inR, outL, outR, n)
            position += n
            if (!budgetChecked) {
                budgetChecked = true
                val spentNanos = max(1L, System.nanoTime() - startedAt)
                val affordableFrames = (max(0L, warmupBudgetNanos - spentNanos).toDouble() / spentNanos * n).toLong()
                if (target - position > affordableFrames) {
                    reverb = StereoReverb(sampleRate, params)
                    position = target - affordableFrames
                }
            }
        }
        positionFrames = target
    }

    /**
     * Escribe en [out] (estéreo intercalado, desde su índice 0) hasta [frames] muestras por canal procesadas y devuelve cuántas
     * escribió: menos que [frames] solo al final (audio + cola), 0 si ya terminó.
     */
    fun read(out: ShortArray, frames: Int): Int {
        require(frames in 0..maxBlockFrames) { "Bloque de $frames muestras (máximo $maxBlockFrames)" }
        require(out.size >= frames * 2) { "Buffer de ${out.size} valores para $frames muestras estéreo" }
        val n = min(frames.toLong(), max(0L, totalFrames - positionFrames)).toInt()
        if (n == 0) return 0

        loadInput(positionFrames, n)
        reverb.process(inL, inR, outL, outR, n)
        val entering = incoming
        if (entering != null) {
            entering.process(inL, inR, incomingL, incomingR, n)
            for (i in 0 until n) {
                val weight = min(1f, (crossfadeDone + i).toFloat() / crossfadeFrames)
                outL[i] += (incomingL[i] - outL[i]) * weight
                outR[i] += (incomingR[i] - outR[i]) * weight
            }
            crossfadeDone += n
            if (crossfadeDone >= crossfadeFrames) {
                reverb = entering
                incoming = null
                crossfadeDone = 0
            }
        }
        for (i in 0 until n) {
            out[2 * i] = floatToPcm16(outL[i])
            out[2 * i + 1] = floatToPcm16(outR[i])
        }
        positionFrames += n
        return n
    }

    /** Lee [count] muestras del origen desde [start] a los buffers de entrada; lo que pasa del final del origen (la cola) es silencio. */
    private fun loadInput(start: Long, count: Int) {
        val got = source.read(start, inputShorts, count)
        for (i in 0 until got) {
            inL[i] = inputShorts[2 * i] / PCM16_SCALE
            inR[i] = inputShorts[2 * i + 1] / PCM16_SCALE
        }
        for (i in got until count) {
            inL[i] = 0f
            inR[i] = 0f
        }
    }

    companion object {
        const val DEFAULT_MAX_BLOCK_FRAMES = 1_024
    }
}
