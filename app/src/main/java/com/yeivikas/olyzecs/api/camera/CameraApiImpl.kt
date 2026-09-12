package com.yeivikas.olyzecs.api.camera

import com.yeivikas.olyzecs.api.project.ActiveProjectMutator
import com.yeivikas.olyzecs.api.project.ActiveProjectReader
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.Keyframe

/**
 * Implementación real de [CameraApi].
 *
 * Las lecturas (`getKeyframes`/`frameAt`) consultan la capa vía [reader]
 * y delegan el cálculo a `Layer.cameraTrack`/`CameraTrack.frameAt`
 * directo — sin duplicar la interpolación (misma lógica que ya usaba
 * `EditorViewModel.frameAt`). Las escrituras van por [mutator], que a su
 * vez usa las 3 funciones nuevas y mínimas que Fase 1.4 agregó a
 * `EditorViewModel` (no existía un equivalente con `layerId`/`timeMs`
 * explícitos antes de esta fase — ver Fase 1.3, corrección inicial).
 */
class CameraApiImpl(
    private val reader: ActiveProjectReader,
    private val mutator: ActiveProjectMutator
) : CameraApi {

    override suspend fun setKeyframe(layerId: String, keyframe: Keyframe) {
        mutator.setCameraKeyframe(layerId, keyframe)
    }

    override suspend fun removeKeyframe(layerId: String, timeMs: Long) {
        mutator.removeCameraKeyframe(layerId, timeMs)
    }

    override suspend fun setBaseFrame(layerId: String, frame: CameraFrame) {
        mutator.setCameraBaseFrame(layerId, frame)
    }

    override fun getKeyframes(layerId: String): List<Keyframe> =
        reader.getLayer(layerId)?.cameraTrack?.keyframes ?: emptyList()

    /** Frame por defecto si la capa no existe: mismo valor "neutro" que usa `CameraTrack(initialBaseFrame = CameraFrame(0f, 0f, 1f, 0f, 1f))`. */
    override fun frameAt(layerId: String, timeMs: Long): CameraFrame =
        reader.getLayer(layerId)?.cameraTrack?.frameAt(timeMs) ?: CameraFrame(0f, 0f, 1f, 0f, 1f)
}
