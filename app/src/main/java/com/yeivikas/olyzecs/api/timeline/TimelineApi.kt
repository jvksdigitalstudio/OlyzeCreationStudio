package com.yeivikas.olyzecs.api.timeline

import android.graphics.Bitmap
import com.yeivikas.olyzecs.engine.timeline.TimelineEvent
import kotlinx.coroutines.flow.SharedFlow

/**
 * Contrato público de EliNer para Timeline.
 *
 * Respaldo real: `engine.timeline.TimelineDurationManager`
 * (`currentDurationMs`/`growIfApproachingEnd`/`ensureCapacityFor`/
 * `events`) y, para [generateThumbnail],
 * `engine.core.ThumbnailRenderCapability` — NO
 * `engine.timeline.ThumbnailRenderer` directo (ver corrección de Fase
 * 4.3, R4.3.7/R4.3.8: [TimelineApiImpl] depende hoy de esa interfaz, con
 * `ThumbnailRenderer` como su única implementación real inyectada por
 * defecto — el `object` concreto que abre su propio contexto EGL ya no
 * es un tipo del que Timeline necesite saber nada más allá de "algo que
 * implementa `ThumbnailRenderCapability`").
 *
 * IMPLEMENTADO como PUENTE TEMPORAL (actualizado en Fase 4.1):
 * [TimelineApiImpl] ya está conectado de verdad vía
 * `ActiveProjectReader`/`ActiveProjectMutator`, pero [generateThumbnail]
 * sigue siendo, a propósito, un puente y no un contrato final:
 * `ThumbnailRenderCapability.render` necesita `List<engine.scene.Layer>`
 * (el modelo interno, con recursos GL vivos), así que
 * [com.yeivikas.olyzecs.api.project.ActiveProjectReader.getLayers] se
 * usa directo ahí, sin pasar por `LayerSnapshot` (el modelo público del
 * resto de esta API). Construir un adaptador que reconstruya el estado
 * interno del motor a partir de `LayerSnapshot` arriesgaría romper
 * recursos GL o semántica interna sin necesidad real (ver ADR-004): la
 * decisión de esta fase es dejar este puente como está — es la opción
 * A de las 3 evaluadas (permanecer temporalmente), no una fuga
 * arquitectónica nueva. La fuga de tipo GL/EGL concreto que SÍ existía
 * (depender de `ThumbnailRenderer` en vez de una interfaz) ya se cerró
 * en Fase 4.3 — lo que queda pendiente es únicamente el acoplamiento a
 * `List<Layer>` en vez de `LayerSnapshot`, documentado arriba. Migrar a
 * un contrato de render de thumbnail más apropiado (opción B) o mover
 * la responsabilidad de adaptación a otro punto (opción C) queda
 * pendiente para una fase posterior, sin evidencia hoy de que sea
 * necesario. Sin consumidor externo real todavía (misma situación que
 * `LayerApi`/`CameraApi`/`AudioApi`).
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
