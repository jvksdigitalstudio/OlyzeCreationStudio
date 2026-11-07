package com.yeivikas.olyzecs.engine.audio.fx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// REVERB ALGORÍTMICO DE ESTUDIO — núcleo DSP. Kotlin puro: sin Android, sin estado global, sin asignaciones por muestra.
//
// El MISMO código procesa la exportación y la vista previa (ver `AudioProcessor.mixTracks`): lo que se oye es lo que se
// exporta. Es determinista (mismas entradas ⇒ mismas muestras) y no depende del hilo, así que se prueba con JUnit.
//
// Cadena de señal (estéreo, simple precisión en las líneas y doble en los filtros):
//
//   entrada ─► pasa-altos (Low cut) ─► pre-delay ─┬─► reflexiones tempranas (8 tomas por canal) ──────────┐
//                                                 └─► difusión (4 pasa-todo por canal) ─► FDN de 8 líneas ┤
//                                                                                                         ▼
//                          seco ───────────────────────────────────────────────►  suma ◄── ancho (M/S) ◄─ pasa-bajos (High cut)
//
// PARÁMETROS EN VIVO: mix, ancho, nivel de tempranas, difusión, modulación, decaimiento, amortiguamiento y los dos cortes se
// pueden cambiar mientras suena (`updateParams`, con suavizado). `size` y `preDelayMs` definen la ESTRUCTURA (largos de las
// líneas y del pre-delay): cambiarlos exige una instancia nueva (`updateParams` devuelve `false` y no aplica nada); el
// motor de reproducción hace el crossfade entre ambas instancias.
//
// La cola tardía es una red de retardos realimentados (FDN, Jot): 8 líneas de largos primos entre sí, mezcladas por una
// matriz de Householder (ortogonal: no crea ni destruye energía), con ganancia por línea ajustada para que la cola caiga
// 60 dB en `decaySeconds`, un pasa-bajos de un polo por línea (el amortiguamiento de agudos) y un vibrato lento por línea
// con interpolación cúbica (rompe el timbre metálico de las colas largas). Calibrada midiendo la respuesta al impulso.
// ============================================================================

private const val LINES = 8
private const val DIFFUSER_STAGES = 4

/** Largos de las líneas de la cola a 44.1 kHz y sala "mediana" (muestras): 23–68 ms. Se reescalan por tasa y tamaño. */
private val BASE_LINE_SAMPLES_44K = intArrayOf(1031, 1327, 1523, 1801, 2063, 2371, 2677, 2999)

/** Largos de los difusores pasa-todo a 44.1 kHz (izquierdo y derecho, ligeramente distintos para descorrelacionar). */
private val DIFFUSER_SAMPLES_44K_LEFT = intArrayOf(210, 158, 561, 410)
private val DIFFUSER_SAMPLES_44K_RIGHT = intArrayOf(221, 167, 577, 419)

/** Reflexiones tempranas: tiempo (ms, sala mediana) y nivel de cada toma. Distintas por canal: imagen estéreo de la sala. */
private val EARLY_TAP_MS_LEFT = doubleArrayOf(7.1, 13.3, 19.7, 29.3, 37.9, 51.7, 61.1, 73.9)
private val EARLY_TAP_MS_RIGHT = doubleArrayOf(9.7, 16.1, 23.9, 33.1, 43.3, 47.9, 67.7, 79.3)
private val EARLY_TAP_GAINS = doubleArrayOf(0.78, 0.66, 0.58, 0.49, 0.42, 0.34, 0.28, 0.22)

/** Frecuencia (Hz) del vibrato de cada línea: todas distintas para que no se sincronicen. */
private val LFO_HZ = doubleArrayOf(0.31, 0.43, 0.53, 0.67, 0.79, 0.97, 1.13, 1.31)

/** Filas ortogonales de Hadamard: cada canal de salida mezcla las 8 líneas con signos distintos ⇒ salidas descorrelacionadas. */
private val OUTPUT_SIGNS_LEFT = doubleArrayOf(1.0, 1.0, 1.0, 1.0, -1.0, -1.0, -1.0, -1.0)
private val OUTPUT_SIGNS_RIGHT = doubleArrayOf(1.0, 1.0, -1.0, -1.0, 1.0, 1.0, -1.0, -1.0)

/** Profundidad del vibrato (muestras) con `modulation = 1` a 44.1 kHz: ~1.5 cents, sutil pero suficiente. */
private const val MODULATION_DEPTH_SAMPLES_AT_44K = 6.0

/**
 * Ajuste fino del RT60: `decaySeconds` es el tiempo de caída de las frecuencias graves y medias (donde el amortiguamiento
 * de agudos no actúa), medido sobre la respuesta al impulso en la banda 250–700 Hz. Con 1.04 sale entre 0.97× y 1.06× el
 * valor pedido a 22.05, 44.1 y 48 kHz con 0.8, 2 y 4 s. Sin ajuste (1.0) salía ~5 % corto: el RT60 de un FDN con largos
 * distintos y matriz de Householder cae algo más rápido que el de la fórmula ideal por línea.
 */
private const val DECAY_CALIBRATION = 1.04

private const val MIN_LINE_SAMPLES = 64

/**
 * ACTUALIZACIÓN EN VIVO ([StereoReverb.updateParams]): los parámetros continuos no saltan al nuevo valor, lo persiguen
 * con un filtro de un polo evaluado cada [CONTROL_BLOCK_FRAMES] muestras (tasa de control). Constante de tiempo de
 * [SMOOTHING_SECONDS]: suficiente para que arrastrar una perilla no produzca clics ni "zipper noise", y corta para que se
 * sienta inmediato. Cuenta MUESTRAS, no llamadas: el resultado no depende de cómo se parta el audio en bloques.
 */
private const val CONTROL_BLOCK_FRAMES = 32
private const val SMOOTHING_SECONDS = 0.03

/** Distancia (absoluta en magnitudes lineales, relativa en frecuencias/tiempos) a la que el suavizado se da por llegado y salta al destino. */
private const val SNAP_ABSOLUTE = 1e-6
private const val SNAP_RELATIVE = 1e-6

/** El reverb nunca amplifica el nivel en régimen (se atenúa con colas largas); ver [levelCompensation]. */
private const val MIN_WET_NORMALIZATION = 0.25

private const val DENORMAL_THRESHOLD_DOUBLE = 1e-25
private const val LFO_RENORMALIZE_INTERVAL = 4096

/** Escala de sala: multiplica los largos y los tiempos de las reflexiones. `size` 0.1..1 ⇒ 0.51..1.5 (la sala mediana, 0.6, ≈ 1.06). */
internal fun roomScale(size: Float): Double = 0.4 + 1.1 * size

/**
 * Largos (muestras) de las 8 líneas de la cola para [sampleRate] y [size]: los de referencia reescalados y ajustados hacia
 * arriba hasta ser PRIMOS ENTRE SÍ (largos con factores comunes concentran la energía en resonancias: sonido "hueco").
 */
internal fun reverbLineLengths(sampleRate: Int, size: Float): IntArray {
    val scale = roomScale(size) * sampleRate / 44_100.0
    val lengths = IntArray(LINES)
    for (i in 0 until LINES) {
        var candidate = max(MIN_LINE_SAMPLES, Math.round(BASE_LINE_SAMPLES_44K[i] * scale).toInt())
        while ((0 until i).any { gcd(candidate, lengths[it]) != 1 }) candidate++
        lengths[i] = candidate
    }
    return lengths
}

private fun gcd(a: Int, b: Int): Int {
    var x = a
    var y = b
    while (y != 0) {
        val t = x % y
        x = y
        y = t
    }
    return x
}

/**
 * Reverb estéreo de estudio (ver el comentario de cabecera de este archivo). Una instancia procesa UN flujo: guarda las
 * colas de los retardos, así que se crea una por carril/render y se descarta. Entrada y salida pueden ser los mismos
 * arreglos (procesa en el sitio: cada muestra de entrada se lee antes de escribir la de salida).
 *
 * Los parámetros continuos se pueden cambiar con [updateParams] mientras se procesa (reproducción en tiempo real). Sin
 * ninguna actualización el resultado es idéntico, muestra a muestra, al de un reverb de parámetros fijos.
 *
 * Un hilo a la vez: ni [process] ni [updateParams] son seguros entre hilos; el motor de reproducción entrega los
 * parámetros nuevos al hilo de audio y es ese hilo quien llama a [updateParams] entre bloques.
 *
 * @param sampleRate tasa de la señal (Hz); todos los largos y frecuencias se reescalan a ella.
 * @param params parámetros iniciales; se acotan a sus rangos ([ReverbParams.coerced]).
 */
internal class StereoReverb(private val sampleRate: Int, params: ReverbParams) {
    /** Parámetros con los que se construyó: de ellos salen `size` y `preDelayMs`, que no cambian en vida de la instancia. */
    private val p = params.coerced()

    /** Destino de los parámetros continuos (lo último que pidió [updateParams]). */
    private var target = p

    // Valores vigentes de los continuos: persiguen a [target] (ver [advanceControl]). Magnitudes lineales y, para
    // frecuencias y tiempos, el valor físico (se suaviza de forma geométrica: una octava cuesta lo mismo en cualquier zona).
    private var mix = p.mix.toDouble()
    private var width = p.width.toDouble()
    private var earlyLevel = p.earlyLevel.toDouble()
    private var diffusion = p.diffusion.toDouble()
    private var modulation = p.modulation.toDouble()
    private var decaySeconds = p.decaySeconds.toDouble()
    private var dampingHz = p.dampingHz.toDouble()
    private var lowCutHz = p.lowCutHz.toDouble()
    private var highCutHz = p.highCutHz.toDouble()

    private var controlPending = false
    private var framesUntilControl = 0
    private val smoothingAlpha = 1.0 - exp(-CONTROL_BLOCK_FRAMES / (sampleRate * SMOOTHING_SECONDS))

    private val lineLengths = reverbLineLengths(sampleRate, p.size)

    /** Profundidad máxima del vibrato (modulation = 1): las líneas se dimensionan para ella, así `modulation` se mueve en vivo sin reasignar. */
    private val maxModulationDepth = MODULATION_DEPTH_SAMPLES_AT_44K * sampleRate / 44_100.0
    private var modulationDepth = modulation * maxModulationDepth
    private val lines = Array(LINES) { DelayLine(lineLengths[it] + ceil(maxModulationDepth).toInt() + 8) }
    private val lineGain = DoubleArray(LINES)
    private var dampingCoefficient = 0.0
    private val dampingState = DoubleArray(LINES)
    private val lineOutput = DoubleArray(LINES)
    private val injectGain = 1.0 / sqrt(LINES.toDouble())
    private val outputGain = 1.0 / sqrt(LINES.toDouble())
    private var wetScale = 1.0

    // Vibrato de las líneas: osciladores por rotación de fase (sin llamar a sin() por muestra).
    private val lfoSin = DoubleArray(LINES)
    private val lfoCos = DoubleArray(LINES)
    private val lfoStepSin = DoubleArray(LINES) { sin(2.0 * PI * LFO_HZ[it] / sampleRate) }
    private val lfoStepCos = DoubleArray(LINES) { cos(2.0 * PI * LFO_HZ[it] / sampleRate) }
    private var framesSinceLfoRenormalize = 0

    private val lowCutL = Biquad(BiquadKind.HIGH_PASS, lowCutHz, sampleRate)
    private val lowCutR = Biquad(BiquadKind.HIGH_PASS, lowCutHz, sampleRate)
    private val highCutL = Biquad(BiquadKind.LOW_PASS, highCutHz, sampleRate)
    private val highCutR = Biquad(BiquadKind.LOW_PASS, highCutHz, sampleRate)

    private val preDelaySamples = Math.round(p.preDelayMs * sampleRate / 1000.0).toInt()
    private val preDelayL: DelayLine? = if (preDelaySamples > 0) DelayLine(preDelaySamples) else null
    private val preDelayR: DelayLine? = if (preDelaySamples > 0) DelayLine(preDelaySamples) else null

    private val roomScaleFactor = roomScale(p.size)
    private val earlyTapsL = earlyTapSamples(EARLY_TAP_MS_LEFT)
    private val earlyTapsR = earlyTapSamples(EARLY_TAP_MS_RIGHT)
    private val earlyLineL = DelayLine(earlyTapsL.max())
    private val earlyLineR = DelayLine(earlyTapsR.max())
    private val earlyNormalization = 1.0 / sqrt(EARLY_TAP_GAINS.sumOf { it * it })

    private var diffuserGain = diffuserGainFor(diffusion)
    private val diffusersL = Array(DIFFUSER_STAGES) { AllpassDiffuser(diffuserSamples(DIFFUSER_SAMPLES_44K_LEFT[it])) }
    private val diffusersR = Array(DIFFUSER_STAGES) { AllpassDiffuser(diffuserSamples(DIFFUSER_SAMPLES_44K_RIGHT[it])) }

    private var dryGain = 1.0
    private var wetGain = 0.0

    init {
        applyDecay()
        applyDamping()
        applyMix()
        // Fases iniciales repartidas en el círculo: las 8 líneas no vibran a la vez.
        for (k in 0 until LINES) {
            val phase = 2.0 * PI * k / LINES
            lfoSin[k] = sin(phase)
            lfoCos[k] = cos(phase)
        }
    }

    /**
     * Cambia en vivo los parámetros continuos: el reverb los persigue con suavizado, sin clics y sin perder la cola que ya suena.
     *
     * @return `true` si se aplicó. `false` si [newParams] cambia `size` o `preDelayMs` (estructura de la instancia): no se
     *   aplica NADA y quien llama debe crear un reverb nuevo con los parámetros completos.
     */
    fun updateParams(newParams: ReverbParams): Boolean {
        val coerced = newParams.coerced()
        if (coerced.size != p.size || coerced.preDelayMs != p.preDelayMs) return false
        target = coerced
        controlPending = true
        framesUntilControl = 0
        return true
    }

    /** Procesa [frames] muestras: `out = seco·cos(mix·π/2) + reverb·sin(mix·π/2)`. Con `mix = 0` la salida es la entrada, bit a bit. */
    fun process(inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray, frames: Int) {
        require(frames >= 0 && frames <= inL.size && frames <= inR.size && frames <= outL.size && frames <= outR.size) {
            "Buffers más cortos que $frames muestras"
        }
        for (n in 0 until frames) {
            if (controlPending && framesUntilControl-- <= 0) {
                advanceControl()
                framesUntilControl = CONTROL_BLOCK_FRAMES - 1
            }
            val dryL = inL[n]
            val dryR = inR[n]

            // 1) Entrada del reverb: pasa-altos y pre-delay.
            var wetInL = lowCutL.process(dryL.toDouble()).toFloat()
            var wetInR = lowCutR.process(dryR.toDouble()).toFloat()
            if (preDelayL != null && preDelayR != null) {
                val delayedL = preDelayL.read(preDelaySamples)
                val delayedR = preDelayR.read(preDelaySamples)
                preDelayL.write(wetInL)
                preDelayR.write(wetInR)
                wetInL = delayedL
                wetInR = delayedR
            }

            // 2) Reflexiones tempranas: tomas de un retardo corto.
            var earlyL = 0.0
            var earlyR = 0.0
            for (k in EARLY_TAP_GAINS.indices) {
                earlyL += EARLY_TAP_GAINS[k] * earlyLineL.read(earlyTapsL[k])
                earlyR += EARLY_TAP_GAINS[k] * earlyLineR.read(earlyTapsR[k])
            }
            earlyLineL.write(wetInL)
            earlyLineR.write(wetInR)
            earlyL *= earlyNormalization
            earlyR *= earlyNormalization

            // 3) Difusión: la entrada se reparte en una nube densa antes de entrar a la cola.
            var diffusedL = wetInL
            var diffusedR = wetInR
            for (s in 0 until DIFFUSER_STAGES) {
                diffusedL = diffusersL[s].process(diffusedL, diffuserGain)
                diffusedR = diffusersR[s].process(diffusedR, diffuserGain)
            }

            // 4) Cola tardía: leer las 8 líneas (retardo modulado), amortiguar agudos y aplicar la ganancia de caída.
            var sum = 0.0
            for (k in 0 until LINES) {
                val tapped = lines[k].readFractional(lineLengths[k] + modulationDepth * lfoSin[k]).toDouble()
                val damped = (1.0 - dampingCoefficient) * tapped + dampingCoefficient * dampingState[k]
                dampingState[k] = if (abs(damped) < DENORMAL_THRESHOLD_DOUBLE) 0.0 else damped
                lineOutput[k] = lineGain[k] * damped
                sum += lineOutput[k]
            }

            // 5) Matriz de Householder (v − 2/N·Σv) + inyección de la entrada; las salidas se mezclan con filas de Hadamard.
            val reflected = 2.0 / LINES * sum
            var lateL = 0.0
            var lateR = 0.0
            for (k in 0 until LINES) {
                val injected = injectGain * (if (k % 2 == 0) diffusedL else diffusedR)
                lines[k].write(flushDenormal((lineOutput[k] - reflected + injected).toFloat()))
                lateL += OUTPUT_SIGNS_LEFT[k] * lineOutput[k]
                lateR += OUTPUT_SIGNS_RIGHT[k] * lineOutput[k]
                advanceLfo(k)
            }
            if (++framesSinceLfoRenormalize >= LFO_RENORMALIZE_INTERVAL) renormalizeLfo()

            // 6) Suma cola + tempranas, ancho estéreo (M/S), pasa-bajos de salida y mezcla seco/húmedo.
            var wetL = wetScale * (outputGain * lateL + earlyLevel * earlyL)
            var wetR = wetScale * (outputGain * lateR + earlyLevel * earlyR)
            val mid = 0.5 * (wetL + wetR)
            val side = 0.5 * (wetL - wetR) * width
            wetL = highCutL.process(mid + side)
            wetR = highCutR.process(mid - side)
            outL[n] = (dryGain * dryL + wetGain * wetL).toFloat()
            outR[n] = (dryGain * dryR + wetGain * wetR).toFloat()
        }
    }

    /**
     * Un paso de la tasa de control: acerca cada parámetro continuo a su destino y recalcula SOLO lo derivado de los que
     * se movieron (coeficientes de filtro, ganancias de línea…). Cuando todos llegaron deja de ejecutarse.
     */
    private fun advanceControl() {
        val t = target

        val nextMix = smoothLinear(mix, t.mix.toDouble())
        if (nextMix != mix) {
            mix = nextMix
            applyMix()
        }
        width = smoothLinear(width, t.width.toDouble())
        earlyLevel = smoothLinear(earlyLevel, t.earlyLevel.toDouble())

        val nextDiffusion = smoothLinear(diffusion, t.diffusion.toDouble())
        if (nextDiffusion != diffusion) {
            diffusion = nextDiffusion
            diffuserGain = diffuserGainFor(diffusion)
        }
        val nextModulation = smoothLinear(modulation, t.modulation.toDouble())
        if (nextModulation != modulation) {
            modulation = nextModulation
            modulationDepth = modulation * maxModulationDepth
        }

        val nextDecay = smoothGeometric(decaySeconds, t.decaySeconds.toDouble())
        if (nextDecay != decaySeconds) {
            decaySeconds = nextDecay
            applyDecay()
        }
        val nextDamping = smoothGeometric(dampingHz, t.dampingHz.toDouble())
        if (nextDamping != dampingHz) {
            dampingHz = nextDamping
            applyDamping()
        }
        val nextLowCut = smoothGeometric(lowCutHz, t.lowCutHz.toDouble())
        if (nextLowCut != lowCutHz) {
            lowCutHz = nextLowCut
            lowCutL.setCutoff(lowCutHz)
            lowCutR.setCutoff(lowCutHz)
        }
        val nextHighCut = smoothGeometric(highCutHz, t.highCutHz.toDouble())
        if (nextHighCut != highCutHz) {
            highCutHz = nextHighCut
            highCutL.setCutoff(highCutHz)
            highCutR.setCutoff(highCutHz)
        }

        controlPending = mix != t.mix.toDouble() || width != t.width.toDouble() || earlyLevel != t.earlyLevel.toDouble() ||
            diffusion != t.diffusion.toDouble() || modulation != t.modulation.toDouble() ||
            decaySeconds != t.decaySeconds.toDouble() || dampingHz != t.dampingHz.toDouble() ||
            lowCutHz != t.lowCutHz.toDouble() || highCutHz != t.highCutHz.toDouble()
    }

    private fun smoothLinear(current: Double, goal: Double): Double {
        val next = current + (goal - current) * smoothingAlpha
        return if (abs(goal - next) < SNAP_ABSOLUTE) goal else next
    }

    private fun smoothGeometric(current: Double, goal: Double): Double {
        val next = current * (goal / current).pow(smoothingAlpha)
        return if (abs(goal - next) < goal * SNAP_RELATIVE) goal else next
    }

    /** Ganancia de cada línea para que la cola caiga 60 dB en `decaySeconds`, y la compensación de nivel que de ella depende. */
    private fun applyDecay() {
        for (k in 0 until LINES) {
            lineGain[k] = 10.0.pow(-3.0 * lineLengths[k] / (sampleRate * decaySeconds * DECAY_CALIBRATION))
        }
        wetScale = levelCompensation(lineGain)
    }

    private fun applyDamping() {
        dampingCoefficient = exp(-2.0 * PI * dampingHz / sampleRate)
    }

    private fun applyMix() {
        dryGain = cos(mix * PI / 2.0)
        wetGain = sin(mix * PI / 2.0)
    }

    private fun diffuserGainFor(diffusion: Double): Float = (0.2 + 0.55 * diffusion).toFloat()

    private fun earlyTapSamples(tapMs: DoubleArray): IntArray =
        IntArray(tapMs.size) { Math.round(tapMs[it] * roomScaleFactor * sampleRate / 1000.0).toInt().coerceAtLeast(1) }

    private fun diffuserSamples(base44k: Int): Int = Math.round(base44k * sampleRate / 44_100.0).toInt().coerceAtLeast(2)

    private fun advanceLfo(k: Int) {
        val c = lfoCos[k]
        val s = lfoSin[k]
        lfoCos[k] = c * lfoStepCos[k] - s * lfoStepSin[k]
        lfoSin[k] = s * lfoStepCos[k] + c * lfoStepSin[k]
    }

    /** Un oscilador por rotación acumula error de redondeo: cada cierto tiempo se devuelve su amplitud a exactamente 1. */
    private fun renormalizeLfo() {
        for (k in 0 until LINES) {
            val magnitude = sqrt(lfoCos[k] * lfoCos[k] + lfoSin[k] * lfoSin[k])
            lfoCos[k] /= magnitude
            lfoSin[k] /= magnitude
        }
        framesSinceLfoRenormalize = 0
    }

    private companion object {
        /**
         * Compensa el nivel en régimen de colas largas: con una matriz ortogonal la potencia de salida para un ruido continuo
         * es `1 / (N·(1 − ḡ²))`, que crece con el RT60. Se escala por `√(N·(1 − ḡ²))` limitado a [0.25, 1]: nunca sube el
         * nivel y sí evita que 10 s de reverb suenen mucho más fuertes que 1 s. Medido: 0.49–0.66 entre RT60 0.3 y 10 s.
         */
        fun levelCompensation(lineGain: DoubleArray): Double {
            val meanSquare = lineGain.sumOf { it * it } / lineGain.size
            return sqrt(lineGain.size * (1.0 - meanSquare)).coerceIn(MIN_WET_NORMALIZATION, 1.0)
        }
    }
}
