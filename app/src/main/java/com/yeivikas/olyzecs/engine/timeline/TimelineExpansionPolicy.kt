package com.yeivikas.olyzecs.engine.timeline

/**
 * Política PURA de crecimiento de la línea de tiempo.
 *
 * Dado cuánto dura el proyecto ahora mismo, decide de cuánto es el
 * próximo salto y hasta dónde puede llegar. No conoce Compose, ViewModels,
 * StateFlow ni nada de Android — son funciones puras sobre [Long], fáciles
 * de cubrir con un test unitario común y corriente y completamente
 * independientes de cómo [TimelineDurationManager] las orqueste.
 */
object TimelineExpansionPolicy {

    /** Incremento a aplicar en el próximo salto, dado que la duración actual es [currentDurationMs]. */
    fun stepFor(currentDurationMs: Long): Long = when {
        currentDurationMs < TimelineLimits.TIER_1_CEILING_MS -> TimelineLimits.STEP_TIER_1_MS
        currentDurationMs < TimelineLimits.TIER_2_CEILING_MS -> TimelineLimits.STEP_TIER_2_MS
        else -> TimelineLimits.STEP_TIER_3_MS
    }

    /** Duración resultante tras UN solo salto de expansión, sin pasarse nunca del techo. */
    fun nextDuration(currentDurationMs: Long): Long {
        if (currentDurationMs >= TimelineLimits.MAX_DURATION_MS) return TimelineLimits.MAX_DURATION_MS
        val next = currentDurationMs + stepFor(currentDurationMs)
        return next.coerceAtMost(TimelineLimits.MAX_DURATION_MS)
    }

    /** true si [durationMs] ya alcanzó (o superó, por datos viejos) el techo de 180 minutos. */
    fun isAtMax(durationMs: Long): Boolean = durationMs >= TimelineLimits.MAX_DURATION_MS

    /**
     * true si [playheadMs] está lo bastante cerca del final de
     * [currentDurationMs] como para justificar expandir un tramo.
     */
    fun isApproachingEnd(playheadMs: Long, currentDurationMs: Long): Boolean =
        !isAtMax(currentDurationMs) &&
            (currentDurationMs - playheadMs) <= TimelineLimits.EXPANSION_TRIGGER_WINDOW_MS

    /**
     * Expande [currentDurationMs] tramo por tramo (siguiendo [nextDuration])
     * hasta que [targetMs] quede fuera de la ventana de disparo, o hasta
     * chocar con el techo — lo que pase primero.
     *
     * Se usa para saltos DIRECTOS más allá del final actual (soltar el
     * playhead lejos de un arrastre, mover un keyframe al fondo del
     * timeline), a diferencia de [isApproachingEnd], pensada para el avance
     * gradual tick a tick de la reproducción/grabación.
     */
    fun expandToFit(currentDurationMs: Long, targetMs: Long): Long {
        var duration = currentDurationMs
        // Cota de seguridad: nunca hacen falta más de un puñado de saltos
        // para ir del mínimo (1 min) al máximo (180 min), pero un límite
        // explícito evita cualquier loop infinito si el día de mañana
        // TimelineLimits cambia a números inconsistentes entre sí.
        var guard = 0
        while (duration < TimelineLimits.MAX_DURATION_MS &&
            (duration - targetMs) <= TimelineLimits.EXPANSION_TRIGGER_WINDOW_MS &&
            guard < 10_000
        ) {
            duration = nextDuration(duration)
            guard++
        }
        return duration
    }
}
