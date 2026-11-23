package com.yeivikas.olyzecs.engine.audio.fx

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Señales y medidas de las pruebas del DSP: un solo lugar para no repetirlas en cada archivo de prueba. */
internal object DspTestSignals {

    fun impulse(frames: Int): FloatArray = FloatArray(frames).also { it[0] = 1f }

    /** Ruido blanco determinista (mismas muestras en cada ejecución). */
    fun noise(frames: Int, amplitude: Float, seed: Long): FloatArray {
        val random = java.util.Random(seed)
        return FloatArray(frames) { (random.nextGaussian() * amplitude).toFloat() }
    }

    fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return sqrt(sum / (to - from))
    }

    fun peak(x: FloatArray): Float = x.maxOf { abs(it) }

    /** Pasa-banda de 4.º orden por lado: dos pasa-altos y dos pasa-bajos Butterworth en cascada. */
    fun bandPass(x: FloatArray, lowHz: Double, highHz: Double, sampleRate: Int): FloatArray {
        val stages = listOf(
            Biquad(BiquadKind.HIGH_PASS, lowHz, sampleRate), Biquad(BiquadKind.HIGH_PASS, lowHz, sampleRate),
            Biquad(BiquadKind.LOW_PASS, highHz, sampleRate), Biquad(BiquadKind.LOW_PASS, highHz, sampleRate)
        )
        return FloatArray(x.size) { i -> stages.fold(x[i].toDouble()) { value, stage -> stage.process(value) }.toFloat() }
    }

    /**
     * RT60 de una respuesta al impulso por integración inversa de Schroeder: tiempo que tarda la energía en caer de −5 a
     * −25 dB (T20), multiplicado por 3 para extrapolarlo a −60 dB.
     */
    fun rt60Seconds(impulseResponse: FloatArray, sampleRate: Int): Double {
        val energy = DoubleArray(impulseResponse.size)
        var accumulated = 0.0
        for (i in impulseResponse.indices.reversed()) {
            accumulated += impulseResponse[i].toDouble() * impulseResponse[i]
            energy[i] = accumulated
        }
        fun firstIndexBelow(db: Double): Int =
            energy.indices.first { 10.0 * log10(energy[it] / energy[0] + 1e-30) <= db }
        return 3.0 * (firstIndexBelow(-25.0) - firstIndexBelow(-5.0)) / sampleRate
    }

    /** Caída de energía (dB) entre la ventana [earlyFrom, earlyTo] y la [lateFrom, lateTo] (segundos); negativa si decae. */
    fun decayDb(x: FloatArray, sampleRate: Int, earlyFrom: Double, earlyTo: Double, lateFrom: Double, lateTo: Double): Double {
        fun energy(from: Double, to: Double): Double {
            var sum = 1e-30
            for (i in (from * sampleRate).toInt() until (to * sampleRate).toInt()) sum += x[i].toDouble() * x[i]
            return sum
        }
        return 10.0 * log10(energy(lateFrom, lateTo) / energy(earlyFrom, earlyTo))
    }
}
