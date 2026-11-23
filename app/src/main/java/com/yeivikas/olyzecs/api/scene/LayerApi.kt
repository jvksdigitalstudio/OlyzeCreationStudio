package com.yeivikas.olyzecs.api.scene

import android.net.Uri
import com.yeivikas.olyzecs.api.model.LayerSnapshot
import com.yeivikas.olyzecs.engine.effects.LookSettings

/**
 * Contrato público de EliNer para Scene/Layer.
 *
 * Respaldo real (ver ELINER_API_V1_FASE1_DISENO.txt — documento de
 * proceso histórico, no versionado en este repositorio — sección 22,
 * matriz de trazabilidad): `engine.scene.Layer` +
 * `data.LayerRepository.importAsLayers` para creación.
 *
 * IMPLEMENTADO (actualizado en Fase 4.1): [LayerApiImpl] ya está
 * conectado de verdad a `EditorViewModel`/`LayerRepository` a través de
 * `ActiveProjectReader`/`ActiveProjectMutator` (ver [LayerApiImpl]) y
 * `MainActivity` ya construye esa instancia real (composition root).
 * PARCIAL: todavía sin consumidor externo real — la UI sigue llamando
 * a `EditorViewModel` directo para operaciones de capa, no a través de
 * esta fachada (misma situación que `CameraApi`/`AudioApi`/
 * `TimelineApi`; distinto de `Mesh3DApi`/`AnimationApi`/`ExportApi`/
 * `DistortionApi`, que sí tienen consumidor real). Migrar esos
 * consumidores de UI es trabajo posterior, fuera de alcance de esta
 * fase.
 */
interface LayerApi {

    /** Crea una o más capas a partir de una imagen importada. */
    suspend fun createLayers(sourceUri: Uri): List<LayerSnapshot>

    /** Elimina una capa por id. */
    suspend fun deleteLayer(layerId: String)

    /** Cambia el orden (zIndex) de una capa. */
    suspend fun reorderLayer(layerId: String, newZIndex: Int)

    /** Fija visibilidad de una capa. */
    suspend fun setVisible(layerId: String, visible: Boolean)

    /** Fija el bloqueo de edición (canvas) de una capa. */
    suspend fun setLocked(layerId: String, locked: Boolean)

    /** Fija el bloqueo de reordenamiento (independiente de [setLocked]). */
    suspend fun setOrderLocked(layerId: String, orderLocked: Boolean)

    /** Aplica un [LookSettings] (grading) a una capa. */
    suspend fun setLookSettings(layerId: String, look: LookSettings)

    /** Consulta el estado actual de una capa. */
    fun getLayer(layerId: String): LayerSnapshot?

    /** Consulta todas las capas del proyecto activo, en orden de [LayerSnapshot.zIndex]. */
    fun getLayers(): List<LayerSnapshot>
}
