package com.yeivikas.olyzecs.api.timeline

import android.graphics.Bitmap
import com.yeivikas.olyzecs.engine.timeline.TimelineEvent
import kotlinx.coroutines.flow.SharedFlow

/**
 * Contrato público de EliNer para Timeline.
 *
 * Respaldo real: `engine.timeline.TimelineDurationManager`
 * (`currentDurationMs`/`growIfApproachingEnd`/`ensureCapacityFor`/
 * `events`) y `engine.timeline.ThumbnailRenderer.render`.
 *
 * NO implementado todavía: `ThumbnailRenderer.render` toma hoy
 * `List<engine.scene.Layer>` (el modelo interno, con recursos GL
 * vivos) — conectar esto a `LayerSnapshot` (el modelo público) necesita
 * un adaptador que reconstruya el estado interno del motor a partir de
 * capas públicas, que es exactamente el tipo de trabajo de "migración
 * de consumidores" que esta etapa deja para después (ver informe,
 * "Paso 11 — Compatibilidad").
 */
interface TimelineApi {

    /** Duración actual del proyecto, en milisegundos. */
    fun currentDurationMs(): Long

    /** True si el proyecto ya llegó al techo máximo de duración. */
    val isAtMaxLimit: Boolean

    /** Expande la duración un tramo si el playhead está por terminarse. */
    fun growIfApproachingEnd(playheadMs: Long): Long

    /** Asegura que la duración alcance para llegar a [targetMs]. */
    fun ensureCapacityFor(targetMs: Long): Long

    /** Eventos de timeline (p. ej. techo máximo alcanzado). */
    val events: SharedFlow<TimelineEvent>

    /** Genera una miniatura del estado del proyecto en [timeMs]. */
    suspend fun generateThumbnail(timeMs: Long, widthPx: Int = 360, heightPx: Int = 640): Bitmap?
}
