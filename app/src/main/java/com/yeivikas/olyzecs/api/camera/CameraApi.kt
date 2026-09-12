package com.yeivikas.olyzecs.api.camera

import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.Keyframe

/**
 * Contrato público de EliNer para Camera.
 *
 * Respaldo real: `engine.camera.CameraTrack` (interpolación,
 * `frameAt`/`addOrReplace`/`remove`) — la clase en sí queda INTERNA a
 * propósito (es un contenedor mutable con lógica, no un dato — ver
 * ELINER_API_V1_FASE1_DISENO.txt, documento de proceso histórico no
 * versionado en este repositorio, sección 12, "queda pendiente de
 * implementación, no bloquea el diseño"). Esta interfaz es exactamente
 * esa resolución: expone la CAPACIDAD (fijar/leer keyframes, consultar
 * el frame interpolado) sin exponer `CameraTrack` como tipo público.
 *
 * [CameraFrame]/[Keyframe] se reutilizan directos (ya son DTOs planos,
 * decisión del diseño aprobado, sección 6).
 *
 * IMPLEMENTADO (actualizado en Fase 4.1): igual que `LayerApi` —
 * [CameraApiImpl] ya está conectado de verdad, `MainActivity` ya
 * construye esa instancia real, pero sigue PARCIAL en el sentido de que
 * todavía no tiene consumidor externo real (la UI sigue llamando a
 * `EditorViewModel` directo).
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

    /**
     * Frame de cámara interpolado de una capa en un instante dado.
     *
     * Decisión de contrato (ADR-003, revisada en Fase 4.1 — sin cambio
     * de comportamiento): si [layerId] no existe, devuelve el frame
     * "neutro" (`CameraFrame(0f, 0f, 1f, 0f, 1f)`, sin transformación)
     * en vez de lanzar o devolver null. Es una clasificación RECUPERABLE
     * deliberada, no un error crítico silenciado: pedir el frame de una
     * capa que no existe (p. ej. una carrera entre un `deleteLayer` y un
     * `frameAt` en vuelo) es una situación de lectura donde el valor
     * neutro es una respuesta segura y utilizable por el consumidor
     * (equivalente a "sin transformación aplicada"), no una condición
     * de fallo que deba propagarse. Se conserva sin cambios porque no
     * existe evidencia en el contrato maestro que exija otra semántica.
     */
    fun frameAt(layerId: String, timeMs: Long): CameraFrame
}
