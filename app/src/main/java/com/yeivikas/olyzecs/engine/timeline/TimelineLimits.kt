package com.yeivikas.olyzecs.engine.timeline

/**
 * Única fuente de verdad para los números que gobiernan la duración de un
 * proyecto: con cuánto arranca, hasta dónde puede crecer solo, y a partir
 * de qué distancia del final se considera que el usuario "se está
 * acercando" y hay que expandir la línea de tiempo.
 *
 * Nada fuera de [timeline] debería hardcodear una duración o un umbral
 * propio — la UI y el ViewModel solo consultan [TimelineDurationManager],
 * que a su vez se apoya en estas constantes y en [TimelineExpansionPolicy].
 */
object TimelineLimits {

    /**
     * Duración con la que arranca SIEMPRE un proyecto nuevo: 1 minuto.
     * Este valor nunca se le muestra al usuario ni se le pide que lo elija.
     */
    const val INITIAL_DURATION_MS: Long = 60_000L

    /** Techo absoluto de duración de un proyecto: 180 minutos (3 horas). */
    const val MAX_DURATION_MS: Long = 180L * 60_000L

    /** Frontera del primer tramo de crecimiento: 1 → 10 minutos. */
    const val TIER_1_CEILING_MS: Long = 10L * 60_000L

    /** Frontera del segundo tramo de crecimiento: 10 → 60 minutos. */
    const val TIER_2_CEILING_MS: Long = 60L * 60_000L

    // El tercer tramo (60 → 180 minutos) no necesita una frontera propia:
    // es simplemente "todo lo que queda por encima de TIER_2_CEILING_MS",
    // hasta MAX_DURATION_MS.

    /** Incremento por salto mientras la duración está por debajo de [TIER_1_CEILING_MS]. */
    const val STEP_TIER_1_MS: Long = 1L * 60_000L

    /** Incremento por salto mientras la duración está entre [TIER_1_CEILING_MS] y [TIER_2_CEILING_MS]. */
    const val STEP_TIER_2_MS: Long = 5L * 60_000L

    /** Incremento por salto mientras la duración está entre [TIER_2_CEILING_MS] y [MAX_DURATION_MS]. */
    const val STEP_TIER_3_MS: Long = 10L * 60_000L

    /**
     * Qué tan cerca del final ACTUAL tiene que estar el playhead para
     * disparar una expansión. Se dispara antes de tocar el borde (no
     * exactamente al llegar) para que el crecimiento se sienta transparente
     * y nunca corte de golpe una reproducción o una grabación en curso.
     */
    const val EXPANSION_TRIGGER_WINDOW_MS: Long = 3_000L
}
