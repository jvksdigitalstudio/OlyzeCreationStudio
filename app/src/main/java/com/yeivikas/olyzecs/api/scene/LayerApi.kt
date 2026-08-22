package com.yeivikas.olyzecs.api.scene

import android.net.Uri
import com.yeivikas.olyzecs.api.model.LayerSnapshot
import com.yeivikas.olyzecs.engine.effects.LookSettings

/**
 * Contrato público de EliNer para Scene/Layer.
 *
 * Respaldo real (ver ELINER_API_V1_FASE1_DISENO.txt sección 22, matriz
 * de trazabilidad): `engine.scene.Layer` +
 * `data.LayerRepository.importAsLayers` para creación.
 *
 * NO implementado todavía (ver informe de Etapa 1): conectar esto a
 * `EditorViewModel`/`LayerRepository` es trabajo de migración de
 * consumidores, explícitamente fuera de alcance de esta etapa
 * ("Paso 11 — Compatibilidad": las migraciones se harán
 * posteriormente, de forma progresiva y controlada).
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
