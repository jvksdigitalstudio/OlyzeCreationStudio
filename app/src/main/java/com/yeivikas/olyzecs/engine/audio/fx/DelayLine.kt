package com.yeivikas.olyzecs.engine.audio.fx

import kotlin.math.abs

/**
 * Anula los valores subnormales (< 1e-20): al decaer una cola de reverb llegan a ellos y los procesadores los operan
 * entre 10 y 100 veces más lento. 1e-20 está ~400 dB por debajo de la escala completa: inaudible.
 */
internal fun flushDenormal(value: Float): Float = if (abs(value) < 1e-20f) 0f else value

/**
 * Línea de retardo circular de simple precisión. Convención "leer antes de escribir": en cada muestra se hace
 * `read(d)` (lo escrito hace `d` muestras, `d ≥ 1`) y luego `write(x)`.
 *
 * @param maxDelaySamples retardo máximo que se va a pedir (entero o fraccionario).
 */
internal class DelayLine(maxDelaySamples: Int) {
    private val mask: Int
    private val buffer: FloatArray
    private var position = 0

    init {
        require(maxDelaySamples >= 1) { "Retardo máximo inválido: $maxDelaySamples" }
        var size = 1
        while (size < maxDelaySamples + GUARD_SAMPLES) size = size shl 1
        buffer = FloatArray(size)
        mask = size - 1
    }

    /** Lo escrito hace [delay] muestras (1 ≤ delay ≤ máximo). */
    fun read(delay: Int): Float = buffer[(position - delay) and mask]

    /**
     * Retardo fraccionario (≥ 2 muestras; un valor menor se sube a 2) por interpolación cúbica de Hermite
     * (Catmull-Rom): sin la pérdida de agudos de la lineal, que dentro de un lazo de realimentación se acumula.
     */
    fun readFractional(delay: Double): Float {
        val d = if (delay < MIN_FRACTIONAL_DELAY) MIN_FRACTIONAL_DELAY else delay
        val whole = d.toInt()
        val t = (1.0 - (d - whole)).toFloat()
        val older = read(whole + 2)
        val p0 = read(whole + 1)
        val p1 = read(whole)
        val newer = read(whole - 1)
        val c1 = 0.5f * (p1 - older)
        val c2 = older - 2.5f * p0 + 2f * p1 - 0.5f * newer
        val c3 = 0.5f * (newer - older) + 1.5f * (p0 - p1)
        return ((c3 * t + c2) * t + c1) * t + p0
    }

    fun write(value: Float) {
        buffer[position] = value
        position = (position + 1) and mask
    }

    private companion object {
        const val MIN_FRACTIONAL_DELAY = 2.0

        /** Holgura sobre el retardo máximo: la interpolación lee hasta 2 muestras más atrás. */
        const val GUARD_SAMPLES = 4
    }
}
