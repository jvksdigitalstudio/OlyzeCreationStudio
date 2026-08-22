package com.yeivikas.olyzecs.api.scene

import android.net.Uri
import com.yeivikas.olyzecs.api.model.LayerSnapshot
import com.yeivikas.olyzecs.api.project.ActiveProjectMutator
import com.yeivikas.olyzecs.api.project.ActiveProjectReader
import com.yeivikas.olyzecs.data.LayerRepository
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.scene.Layer

/**
 * Implementación real de [LayerApi].
 *
 * Lee/escribe a través de [reader]/[mutator] — nunca guarda su propia
 * copia de las capas (`getLayers()` consulta el estado actual en cada
 * llamada, sin caché). [layerRepository] se usa exclusivamente para
 * [createLayers] (I/O de importación de imagen — decodificar/leer un
 * archivo externo — deliberadamente fuera de [ActiveProjectMutator], ver
 * Fase 1.3 sección 6).
 */
class LayerApiImpl(
    private val reader: ActiveProjectReader,
    private val mutator: ActiveProjectMutator,
    private val layerRepository: LayerRepository
) : LayerApi {

    override suspend fun createLayers(sourceUri: Uri): List<LayerSnapshot> {
        // LayerRepository.importAsLayers decodifica pero NO agrega el
        // resultado al proyecto activo (eso es responsabilidad de
        // mutación, ver ActiveProjectMutator.addLayers) — mismo patrón
        // que ya usa EditorViewModel.importImages: startingZIndex y
        // startingColorIndex son ambos el tamaño actual de la lista.
        val startingIndex = reader.getLayers().size
        val created = layerRepository.importAsLayers(
            uris = listOf(sourceUri),
            startingZIndex = startingIndex,
            startingColorIndex = startingIndex
        )
        mutator.addLayers(created)
        return created.map { it.toSnapshot() }
    }

    override suspend fun deleteLayer(layerId: String) {
        mutator.deleteLayer(layerId)
    }

    override suspend fun reorderLayer(layerId: String, newZIndex: Int) {
        mutator.setLayerZIndex(layerId, newZIndex)
    }

    override suspend fun setVisible(layerId: String, visible: Boolean) {
        mutator.setLayerVisible(layerId, visible)
    }

    override suspend fun setLocked(layerId: String, locked: Boolean) {
        mutator.setLayerLocked(layerId, locked)
    }

    override suspend fun setOrderLocked(layerId: String, orderLocked: Boolean) {
        mutator.setLayerOrderLocked(layerId, orderLocked)
    }

    override suspend fun setLookSettings(layerId: String, look: LookSettings) {
        mutator.setLayerLookSettings(layerId, look)
    }

    override fun getLayer(layerId: String): LayerSnapshot? =
        reader.getLayer(layerId)?.toSnapshot()

    override fun getLayers(): List<LayerSnapshot> =
        reader.getLayers().sortedBy { it.zIndex }.map { it.toSnapshot() }
}

/**
 * Conversión Layer → LayerSnapshot (dirección de lectura, ver Fase 1.1
 * sección 4.3): mapeo directo de campos, excluyendo a propósito
 * `glTextureId`/`pendingBitmap` (recursos GL/CPU transitorios) y
 * `cameraTrack` completo (solo se expone `baseFrame`, la pose estática —
 * los keyframes de animación viven en el dominio Camera de esta misma
 * API, no acá; ver KDoc de [LayerSnapshot]). Esta es la salvedad ya
 * documentada: un [LayerSnapshot] con `cameraTrack.keyframes` no vacío
 * no representa la animación completa de la capa por sí solo — para eso
 * hace falta combinarlo con `CameraApi.getKeyframes(layerId)`.
 */
private fun Layer.toSnapshot(): LayerSnapshot = LayerSnapshot(
    id = id,
    sourceUri = sourceUri,
    name = name,
    zIndex = zIndex,
    parallaxFactor = parallaxFactor,
    locked = locked,
    orderLocked = orderLocked,
    visible = visible,
    lookSettings = lookSettings,
    baseFrame = cameraTrack.baseFrame,
    widthPx = widthPx,
    heightPx = heightPx,
    colorIndex = colorIndex,
    customColorArgb = customColorArgb,
    importedDefaultColorArgb = importedDefaultColorArgb,
    customGradientStartArgb = customGradientStartArgb,
    customGradientEndArgb = customGradientEndArgb,
    useGradientColor = useGradientColor,
    gradientAngleDegrees = gradientAngleDegrees,
    gradientIsRadial = gradientIsRadial,
    useBlackAndWhiteMode = useBlackAndWhiteMode
)
