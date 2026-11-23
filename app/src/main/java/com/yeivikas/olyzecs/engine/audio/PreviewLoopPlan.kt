package com.yeivikas.olyzecs.engine.audio

/**
 * Tramos del PROYECTO (ms) que la vista previa de un clip reproduce EN ORDEN, de izquierda a derecha, y en bucle:
 * al terminar el último vuelve al primero. Con un solo tramo es el bucle de siempre de ese tramo.
 *
 * Invariantes (las garantiza `init`): hay al menos un tramo, están ordenados por inicio y no se solapan
 * (`fin(i) <= inicio(i + 1)`). Para construirlo desde tramos en cualquier orden o solapados, usar [of].
 *
 * Es lo único que decide DÓNDE puede estar el cursor durante la vista previa ([confine]); no sabe nada de
 * selecciones, clips ni carriles: de eso se ocupa [resolvedIn], que lo construye.
 */
data class PreviewLoopPlan(val ranges: List<PreviewLoopRange>) {

    init {
        require(ranges.isNotEmpty()) { "Un plan de bucle necesita al menos un tramo" }
        require(ranges.zipWithNext().all { (a, b) -> a.endMs <= b.startMs }) {
            "Los tramos del plan deben estar ordenados y sin solaparse: $ranges"
        }
    }

    /**
     * Lleva [ms] a un instante que SUENA, siguiendo el orden de los tramos:
     *  - antes del primer tramo → el inicio del primero (p. ej. se pulsó Play con el cursor antes de todo);
     *  - dentro de un tramo → sin cambios;
     *  - en el hueco entre dos tramos, o pasado el último → salta al SIGUIENTE tramo (al primero tras el último)
     *    CONSERVANDO el sobrepaso del cuadro: `inicio(siguiente) + (ms − fin(actual)) mod largo(siguiente)`. Así el
     *    reloj no acumula el desfase de un tick por salto y, si un tick fuera más largo que el tramo, sigue cayendo
     *    en el lugar correcto.
     *
     * Con un solo tramo equivale exactamente a un bucle simple: `inicio + (ms − inicio) mod largo` pasado el final.
     * El resultado siempre cae dentro de algún tramo.
     */
    fun confine(ms: Long): Long {
        val first = ranges.first()
        if (ms < first.startMs) return first.startMs
        // Último tramo que ya empezó (existe: ms >= inicio del primero).
        val index = ranges.indexOfLast { it.startMs <= ms }
        val current = ranges[index]
        if (ms < current.endMs) return ms
        val next = ranges[(index + 1) % ranges.size]
        return next.startMs + (ms - current.endMs) % next.lengthMs
    }

    companion object {
        /**
         * Plan con los [ranges] dados en cualquier orden: los ordena y une los que se solapan o se tocan (para
         * la reproducción son un único tramo continuo). `null` si no hay ninguno.
         */
        fun of(ranges: Collection<PreviewLoopRange>): PreviewLoopPlan? {
            if (ranges.isEmpty()) return null
            val merged = ArrayList<PreviewLoopRange>(ranges.size)
            for (range in ranges.sortedBy { it.startMs }) {
                val last = merged.lastOrNull()
                if (last == null || range.startMs > last.endMs) {
                    merged.add(range)
                } else if (range.endMs > last.endMs) {
                    merged[merged.lastIndex] = PreviewLoopRange(last.startMs, range.endMs)
                }
            }
            return PreviewLoopPlan(merged)
        }
    }
}
