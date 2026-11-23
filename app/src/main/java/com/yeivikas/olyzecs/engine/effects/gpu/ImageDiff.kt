package com.yeivikas.olyzecs.engine.effects.gpu

/**
 * Diferencia entre dos imágenes RGBA de 8 bits por canal, en niveles de 0 a 255.
 *
 * @property meanAbsError error medio absoluto sobre todos los canales.
 * @property p99 el error que no supera el 99 % de los canales (ignora unos pocos píxeles atípicos).
 * @property maxError el peor canal.
 */
data class ImageDiffMetrics(val meanAbsError: Double, val p99: Int, val maxError: Int, val comparedChannels: Int)

/** Compara dos imágenes. Pura: sirve igual para el autotest en el dispositivo y para las pruebas en JVM. */
object ImageDiff {

    private const val LEVELS = 256
    private const val P99_QUANTILE = 0.99

    /** Las dos deben tener el mismo tamaño (en bytes) y no estar vacías. */
    fun compare(expected: ByteArray, actual: ByteArray): ImageDiffMetrics {
        require(expected.size == actual.size) { "Tamaños distintos: ${expected.size} vs ${actual.size}" }
        require(expected.isNotEmpty()) { "No hay nada que comparar" }
        val histogram = LongArray(LEVELS)
        var sum = 0L
        var max = 0
        for (i in expected.indices) {
            val diff = kotlin.math.abs((expected[i].toInt() and 0xFF) - (actual[i].toInt() and 0xFF))
            histogram[diff]++
            sum += diff
            if (diff > max) max = diff
        }
        val total = expected.size.toLong()
        val threshold = kotlin.math.ceil(total * P99_QUANTILE).toLong()
        var cumulative = 0L
        var p99 = 0
        for (level in 0 until LEVELS) {
            cumulative += histogram[level]
            if (cumulative >= threshold) { p99 = level; break }
        }
        return ImageDiffMetrics(sum.toDouble() / total, p99, max, expected.size)
    }
}
