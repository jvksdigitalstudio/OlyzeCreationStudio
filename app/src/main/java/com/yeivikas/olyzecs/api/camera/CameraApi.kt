package com.yeivikas.olyzecs.api.camera

import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.Keyframe

/**
 * Contrato público de EliNer para Camera.
 *
 * Respaldo real: `engine.camera.CameraTrack` (interpolación,
 * `frameAt`/`addOrReplace`/`remove`) — la clase en sí queda INTERNA a
 * propósito (es un contenedor mutable con lógica, no un dato — ver
 * ELINER_API_V1_FASE1_DISENO.txt sección 12, "queda pendiente de
 * implementación, no bloquea el diseño"). Esta interfaz es exactamente
 * esa resolución: expone la CAPACIDAD (fijar/leer keyframes, consultar
 * el frame interpolado) sin exponer `CameraTrack` como tipo público.
 *
 * [CameraFrame]/[Keyframe] se reutilizan directos (ya son DTOs planos,
 * decisión del diseño aprobado, sección 6).
 *
 * NO implementado todavía — mismo motivo que `LayerApi`.
 */
interface CameraApi {

    /** Fija o reemplaza el keyframe de cámara de una capa en un instante. */
    suspend fun setKeyframe(layerId: String, keyframe: Keyframe)

    /** Elimina el keyframe de cámara de una capa en un instante exacto. */
    suspend fun removeKeyframe(layerId: String, timeMs: Long)

    /** Fija la pose estática (sin animación) de una capa. */
    suspend fun setBaseFrame(layerId: String, frame: CameraFrame)

    /** Lista los keyframes de cámara de una capa, ordenados por tiempo. */
    fun getKeyframes(layerId: String): List<Keyframe>

    /** Frame de cámara interpolado de una capa en un instante dado. */
    fun frameAt(layerId: String, timeMs: Long): CameraFrame
}
