package com.yeivikas.olyzecs.engine.timeline

/**
 * Snapshot inmutable de todo lo que hace falta saber, desde afuera, sobre
 * la duración del proyecto en un instante dado. La UI (EditorScreen,
 * TimelineView) solo debería CONSULTAR esto — nunca calcular ni mutar una
 * duración por su cuenta.
 */
data class TimelineState(
    /** Duración total vigente del proyecto, en milisegundos. */
    val durationMs: Long = TimelineLimits.INITIAL_DURATION_MS,
    /** true cuando ya se alcanzó el techo de 180 minutos y no hay más margen para crecer. */
    val isAtMaxLimit: Boolean = false
)
