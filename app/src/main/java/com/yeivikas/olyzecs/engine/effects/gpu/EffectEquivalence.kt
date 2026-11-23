package com.yeivikas.olyzecs.engine.effects.gpu

/** Resultado de comparar unas métricas con los criterios: si pasa y, si no, por qué. */
data class EquivalenceVerdict(val passed: Boolean, val failures: List<String>)

/**
 * Cuánta diferencia se tolera entre dos resultados, en niveles de 0 a 255 (criterio de aceptación del ADR-019 §7).
 * Pura.
 */
data class EquivalenceCriteria(val maxMeanAbsError: Double, val maxP99: Int, val maxError: Int) {

    fun evaluate(metrics: ImageDiffMetrics): EquivalenceVerdict {
        val failures = buildList {
            if (metrics.meanAbsError > maxMeanAbsError) add("error medio ${format(metrics.meanAbsError)} > $maxMeanAbsError")
            if (metrics.p99 > maxP99) add("percentil 99 ${metrics.p99} > $maxP99")
            if (metrics.maxError > maxError) add("error máximo ${metrics.maxError} > $maxError")
        }
        return EquivalenceVerdict(failures.isEmpty(), failures)
    }

    private fun format(value: Double): String = "%.4f".format(java.util.Locale.ROOT, value)

    companion object {
        /**
         * Un pase de copia debe devolver la imagen tal cual. Se tolera 1 nivel de diferencia por el redondeo que algunos
         * controladores aplican al muestrear; más que eso significa que la tubería altera la imagen.
         */
        val IDENTITY = EquivalenceCriteria(maxMeanAbsError = 0.05, maxP99 = 1, maxError = 1)

        /**
         * Equivalencia perceptual entre el camino GPU y el CPU (ADR-019 §7): error medio ≤ 2/255 y percentil 99 ≤ 8/255.
         * Es un punto de partida razonable, NO un dato medido: se ratifica con el primer efecto portado (fase 5.1).
         * El máximo no se limita porque los bordes pueden diferir más sin que se note.
         */
        val PERCEPTUAL = EquivalenceCriteria(maxMeanAbsError = 2.0, maxP99 = 8, maxError = 255)
    }
}
