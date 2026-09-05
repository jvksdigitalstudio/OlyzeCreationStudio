package com.yeivikas.olyzecs.api.project

import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.engine.timeline.TimelineEvent
import kotlinx.coroutines.flow.SharedFlow

/**
 * Lectura de solo consulta del proyecto activo en memoria.
 *
 * Decisión aprobada en Fase 1.2/1.3 (ver
 * OLYZE_Documento_Maestro_EliNerAPI.md, Partes IV y V): el estado del
 * proyecto NO se mueve de [com.yeivikas.olyzecs.viewmodel.EditorViewModel]
 * — esta interfaz no almacena nada, no cachea nada, no es un segundo
 * owner. Cada método, en la implementación real, lee directo del mismo
 * `StateFlow` que ya existe — es la puerta de acceso, no una copia.
 *
 * Kotlin puro: sin `Context`, sin `Activity`, sin tipos de Compose/Android
 * UI, sin depender de `viewmodel.*` (la implementación de esta interfaz
 * vive en `viewmodel/`, no al revés).
 *
 * Implementada por `EditorViewModel`. Cada `*ApiImpl` que la necesita la
 * recibe por constructor.
 *
 * Cada método está justificado por un consumidor real (ver tabla
 * "Métodos: tipo, motivo, consumidor" de la Fase 1.3):
 * - [getLayers]/[getLayer]: `LayerApi`, `CameraApi` (para ubicar la capa
 *   antes de tocar su `cameraTrack`), `TimelineApi.generateThumbnail`,
 *   `ExportApi.export`.
 * - [getAudioClip]: `AudioApi`, `ExportApi.export`.
 * - [getSpeedKeyframes]/[getFreezeFrames]/[getBaseDurationMs]:
 *   `ExportApi.export` (los 3 datos que `VideoExporter.export` necesita
 *   y que `ExportSettings` no representa).
 * - [isAtMaxDurationLimit]/[timelineEventsFlow]: `TimelineApi.isAtMaxLimit`/
 *   `TimelineApi.events` — lecturas puras, seguras de exponer sin pasar
 *   por el mutator (no cambian nada). NO se expone el
 *   `TimelineDurationManager` real: sus métodos que SÍ mutan
 *   (`growIfApproachingEnd`/`ensureCapacityFor`) siempre actualizan
 *   `_uiState.projectDurationMs`/`isAtMaxDuration` en el mismo paso
 *   dentro de `EditorViewModel` — exponer el objeto crudo permitiría
 *   mutarlo sin esa sincronización (hallazgo real de Fase 1.4); esas 2
 *   operaciones viven en `ActiveProjectMutator`, no acá.
 */
interface ActiveProjectReader {

    /** Todas las capas del proyecto activo, en el orden real — el mismo objeto que usa el motor, no una copia. */
    fun getLayers(): List<Layer>

    /** Una capa puntual, o null si no existe. */
    fun getLayer(layerId: String): Layer?

    /** Clip de audio del proyecto activo, o null si no tiene. */
    fun getAudioClip(): AudioClip?

    /** Rampas de velocidad vigentes del proyecto activo. */
    fun getSpeedKeyframes(): List<SpeedKeyframe>

    /** Freeze frames vigentes del proyecto activo. */
    fun getFreezeFrames(): List<FreezeFrame>

    /** Duración base del proyecto (antes de aplicar rampas/freezes), en milisegundos. */
    fun getBaseDurationMs(): Long

    /** True si el proyecto ya llegó al techo máximo de duración (espejo siempre sincronizado de TimelineDurationManager.isAtMaxLimit). */
    fun isAtMaxDurationLimit(): Boolean

    /** Eventos de timeline (p. ej. techo máximo alcanzado) — mismo Flow que ya expone TimelineDurationManager, solo lectura. */
    fun timelineEventsFlow(): SharedFlow<TimelineEvent>
}
