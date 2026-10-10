package com.yeivikas.olyzecs.api.scene

import android.net.Uri
import com.yeivikas.olyzecs.api.model.LayerSnapshot
import com.yeivikas.olyzecs.api.project.ActiveProjectMutator
import com.yeivikas.olyzecs.api.project.ActiveProjectReader
import com.yeivikas.olyzecs.data.LayerRepository
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.scene.Layer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación real de [LayerApi].
 *
 * Lee/escribe a través de [reader]/[mutator] — nunca guarda su propia
 * copia de las capas (`getLayers()` consulta el estado actual en cada
 * llamada, sin caché). [layerRepository] se usa exclusivamente para
 * [createLayers] (I/O de importación de imagen — decodificar/leer un
 * archivo externo — deliberadamente fuera de [ActiveProjectMutator], ver
 * Fase 1.3 sección 6).
 *
 * FASE 4.2-R4 (AUDITORÍA — Parte 3 del prompt maestro R4, "confinamiento
 * de mutaciones"): [mutator] (`EditorViewModel` en producción) mantiene
 * su estado (`_uiState`, `MutableStateFlow`) con lectura-modificación-
 * escritura simple (`_uiState.value = _uiState.value.copy(...)`), sin
 * ningún lock propio — el mismo patrón que usa el resto del `ViewModel`
 * para todas sus mutaciones síncronas, pensado para ejecutarse desde el
 * dispatcher del `ViewModel` (`Main`, vía `viewModelScope`). Que un
 * método de esta API sea `suspend` NO garantiza por sí solo que quien lo
 * llama esté en `Main` — un consumidor futuro podría lanzar la
 * corrutina desde `Dispatchers.Default`/`IO` sin saberlo. Por eso cada
 * método que MUTA pasa explícitamente por `withContext(Dispatchers.Main.immediate)`
 * acá, en el adapter: así el `ViewModel` recibe la mutación siempre en el
 * mismo hilo que ya asume, sin importar desde dónde la haya disparado el
 * consumidor real. Los métodos de solo LECTURA (`getLayer`/`getLayers`)
 * no lo necesitan: no son `suspend`, y leer `StateFlow.value` es
 * seguro desde cualquier hilo.
 */
class LayerApiImpl(
    private val reader: ActiveProjectReader,
    private val mutator: ActiveProjectMutator,
    private val layerRepository: LayerRepository
) : LayerApi {

    override suspend fun createLayers(sourceUri: Uri): List<LayerSnapshot> {
        // La decodificación de la imagen SÍ conviene dejarla en el
        // dispatcher que traiga el llamador (I/O pesado, no toca estado del
        // ViewModel) — solo el `mutator.addLayers` final, que sí escribe
        // `_uiState`, se confina a Main.
        val startingIndex = reader.getLayers().size
        val created = layerRepository.importAsLayers(
            uris = listOf(sourceUri),
            startingZIndex = startingIndex,
            startingColorIndex = startingIndex
        )
        withContext(Dispatchers.Main.immediate) { mutator.addLayers(created) }
        return created.map { it.toSnapshot() }
    }

    override suspend fun deleteLayer(layerId: String) = withContext(Dispatchers.Main.immediate) {
        mutator.deleteLayer(layerId)
    }

    override suspend fun reorderLayer(layerId: String, newZIndex: Int) = withContext(Dispatchers.Main.immediate) {
        mutator.setLayerZIndex(layerId, newZIndex)
    }

    override suspend fun setVisible(layerId: String, visible: Boolean) = withContext(Dispatchers.Main.immediate) {
        mutator.setLayerVisible(layerId, visible)
    }

    override suspend fun setLocked(layerId: String, locked: Boolean) = withContext(Dispatchers.Main.immediate) {
        mutator.setLayerLocked(layerId, locked)
    }

    override suspend fun setOrderLocked(layerId: String, orderLocked: Boolean) = withContext(Dispatchers.Main.immediate) {
        mutator.setLayerOrderLocked(layerId, orderLocked)
    }

    override suspend fun setLookSettings(layerId: String, look: LookSettings) = withContext(Dispatchers.Main.immediate) {
        mutator.setLayerLookSettings(layerId, look)
    }

    override fun getLayer(layerId: String): LayerSnapshot? =
        reader.getLayer(layerId)?.toSnapshot()

    override fun getLayers(): List<LayerSnapshot> =
        reader.getLayers().sortedBy { it.zIndex }.map { it.toSnapshot() }
}

/**
 * Conversión Layer → LayerSnapshot (dirección de lectura, ver Fase 1.1
 * sección 4.3): mapeo directo de campos, excluyendo a propósito el
 * texture id GL (desde la Fase 3.1 vive en un registro privado de
 * `GLRenderer`, ni siquiera es un campo de `Layer`)/`pendingBitmap`
 * (recurso CPU transitorio) y
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
