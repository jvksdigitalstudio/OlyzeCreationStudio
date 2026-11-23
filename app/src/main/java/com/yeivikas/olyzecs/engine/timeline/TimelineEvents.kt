package com.yeivikas.olyzecs.engine.timeline

/**
 * Eventos puntuales ("de un solo disparo") del motor de la línea de
 * tiempo — a diferencia de [TimelineState], que es el estado vigente en
 * cualquier momento, esto son avisos que la UI consume una única vez
 * (por ejemplo, para mostrar un mensaje) y no algo que deba quedar
 * reflejado de forma persistente en ninguna pantalla.
 */
sealed interface TimelineEvent {
    /**
     * Se alcanzó el techo de 180 minutos: la línea de tiempo dejó de
     * expandirse. La UI puede usar esto para mostrar un aviso elegante,
     * una única vez por cada transición hacia el límite (no en bucle
     * mientras el playhead siga pegado al final).
     */
    data object MaxDurationReached : TimelineEvent
}
