package com.yeivikas.olyzecs.api.timeline

import android.content.Context
import android.graphics.Bitmap
import com.yeivikas.olyzecs.api.project.ActiveProjectMutator
import com.yeivikas.olyzecs.api.project.ActiveProjectReader
import com.yeivikas.olyzecs.engine.core.ThumbnailRenderCapability
import com.yeivikas.olyzecs.engine.timeline.ThumbnailRenderer
import com.yeivikas.olyzecs.engine.timeline.TimelineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

/**
 * Implementación real de [TimelineApi].
 *
 * [generateThumbnail] resuelve el bloqueador ya identificado (Fase 1,
 * FASE1-002): llama [ActiveProjectReader.getLayers] para obtener el
 * modelo interno del motor (`engine.scene.Layer`, con recursos GL) tal
 * cual — SIN pasar por `LayerSnapshot` — porque
 * `ThumbnailRenderCapability.render` necesita exactamente ese tipo (la
 * única implementación real, `engine.timeline.ThumbnailRenderer`,
 * recibida acá por [thumbnailRenderer] con ese valor por defecto — ver
 * Fase 4.3, R4.3.7/R4.3.8). `TimelineApiImpl` nunca conoce
 * `EditorViewModel` directamente, solo [reader].
 *
 * El resto de las lecturas van por [reader] (`isAtMaxDurationLimit`/
 * `timelineEventsFlow`, ver nota "CUARTA CORRECCIÓN" en
 * `ActiveProjectMutator.kt`); las 2 mutaciones de duración van por
 * [mutator], que replica el mismo patrón de 2 pasos (mutar el manager +
 * reflejar en `_uiState`) que ya usa `EditorViewModel`.
 */
class TimelineApiImpl(
    private val context: Context,
    private val reader: ActiveProjectReader,
    private val mutator: ActiveProjectMutator,
    // FASE 4.3 (RENDER BOUNDARY, R4.3.7/R4.3.8): antes esta clase llamaba
    // directo a `ThumbnailRenderer.render(...)` (el `object` concreto, que
    // conoce EGL/GLES) — ahora depende de la interfaz
    // `ThumbnailRenderCapability`, con `ThumbnailRenderer` como valor por
    // defecto para no romper ningún wiring existente (mismo objeto, mismo
    // comportamiento en runtime).
    private val thumbnailRenderer: ThumbnailRenderCapability = ThumbnailRenderer
) : TimelineApi {

    override fun currentDurationMs(): Long = reader.getBaseDurationMs()

    override val isAtMaxLimit: Boolean
        get() = reader.isAtMaxDurationLimit()

    override fun growIfApproachingEnd(playheadMs: Long): Long =
        mutator.growTimelineIfApproachingEnd(playheadMs)

    override fun ensureCapacityFor(targetMs: Long): Long =
        mutator.ensureTimelineCapacityFor(targetMs)

    override val events: SharedFlow<TimelineEvent>
        get() = reader.timelineEventsFlow()

    override suspend fun generateThumbnail(timeMs: Long, widthPx: Int, heightPx: Int): Bitmap? =
        withContext(Dispatchers.Default) {
            thumbnailRenderer.render(context, reader.getLayers(), timeMs, widthPx, heightPx)
        }
}
