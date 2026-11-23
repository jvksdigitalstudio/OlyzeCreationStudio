package com.yeivikas.olyzecs.engine.audio.fx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class BiquadKind { LOW_PASS, HIGH_PASS }

/**
 * Filtro Butterworth de 2.º orden (12 dB/oct, sin resonancia), forma directa II transpuesta en doble precisión
 * (un pasa-altos a 20 Hz con 44.1 kHz pierde la estabilidad numérica en simple). Coeficientes de las fórmulas de Robert
 * Bristow-Johnson. [cutoffHz] se limita a (0, 0.45 × sample rate] para no acercarse a Nyquist.
 *
 * El corte se puede mover EN VIVO con [setCutoff]: solo cambian los coeficientes y el estado interno (`z1`, `z2`) se
 * conserva, así que no hay clic ni reinicio. La forma transpuesta tolera bien cambios pequeños y frecuentes de
 * coeficientes (quien los mueva debe suavizar la frecuencia; ver `StereoReverb`).
 */
internal class Biquad(private val kind: BiquadKind, cutoffHz: Double, private val sampleRate: Int) {
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    init {
        setCutoff(cutoffHz)
    }

    /** Recalcula los coeficientes para [cutoffHz] sin tocar el estado del filtro. */
    fun setCutoff(cutoffHz: Double) {
        val omega = 2.0 * PI * cutoffHz.coerceIn(1.0, MAX_CUTOFF_FRACTION * sampleRate) / sampleRate
        val cosOmega = cos(omega)
        val alpha = sin(omega) / (2.0 * BUTTERWORTH_Q)
        val a0 = 1.0 + alpha
        val lowPass = kind == BiquadKind.LOW_PASS
        b0 = (if (lowPass) 1.0 - cosOmega else 1.0 + cosOmega) / 2.0 / a0
        b1 = (if (lowPass) 1.0 - cosOmega else -(1.0 + cosOmega)) / a0
        b2 = b0
        a1 = -2.0 * cosOmega / a0
        a2 = (1.0 - alpha) / a0
    }

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = flush(b1 * x - a1 * y + z2)
        z2 = flush(b2 * x - a2 * y)
        return y
    }

    private fun flush(v: Double): Double = if (abs(v) < DENORMAL_THRESHOLD) 0.0 else v

    private companion object {
        val BUTTERWORTH_Q = 1.0 / sqrt(2.0)
        const val MAX_CUTOFF_FRACTION = 0.45
        const val DENORMAL_THRESHOLD = 1e-25
    }
}
