package com.yeivikas.olyzecs.engine.audio.fx

/**
 * Difusor pasa-todo de Schroeder: deja pasar todas las frecuencias con la misma ganancia pero reparte cada impulso en
 * una ráfaga de ecos. Varios en serie convierten un golpe en la "nube" densa que alimenta la cola del reverb.
 * `w[n] = x[n] + g·w[n-D]`, `y[n] = w[n-D] − g·w[n]` (ganancia unitaria a toda frecuencia para |g| < 1).
 */
internal class AllpassDiffuser(private val delaySamples: Int) {
    private val line = DelayLine(delaySamples)

    fun process(x: Float, gain: Float): Float {
        val delayed = line.read(delaySamples)
        val w = x + gain * delayed
        line.write(flushDenormal(w))
        return delayed - gain * w
    }
}
