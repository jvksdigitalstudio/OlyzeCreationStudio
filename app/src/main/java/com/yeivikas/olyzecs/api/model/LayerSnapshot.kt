package com.yeivikas.olyzecs.api.model

import android.net.Uri
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.effects.LookSettings

/**
 * Representación pública de una capa, para EliNer API — deliberadamente
 * DISTINTA de [com.yeivikas.olyzecs.engine.scene.Layer] (el
 * modelo interno del motor), no una copia 1:1.
 *
 * Excluye a propósito, respecto de `Layer`:
 *  - el texture id GL vivo (desde la Fase 3.1 ya ni siquiera es un campo
 *    de `Layer` — vive exclusivamente en un registro privado de
 *    `GLRenderer`, ver `GLRenderer.layerTextures` — pero conceptualmente
 *    seguiría sin corresponder cruzar la frontera pública aunque
 *    existiera ahí, ver ELINER_API_V1_FASE1_DISENO.txt (documento de
 *    proceso histórico, no versionado en este repositorio) sección 6/13
 *    y ADR-004).
 *  - `pendingBitmap` (bitmap decodificado transitorio, mismo motivo).
 *  - `cameraTrack` (el contenedor mutable de keyframes con lógica de
 *    interpolación — es comportamiento de motor, no dato; su
 *    capacidad real de consulta/edición vive en el dominio Camera de
 *    esta misma API, no acá. Ver `camera/CameraApi.kt`).
 *
 * Sí conserva `sourceUri: Uri` — decisión ya cerrada en la auditoría
 * del diseño para el caso equivalente de `AudioClip.sourceUri`,
 * aplicando el mismo criterio de ADR-002 (Uri es un dato de referencia
 * a archivo, no una implementación de Android que haga falta abstraer
 * acá).
 *
 * [baseFrame] reutiliza directamente
 * [com.yeivikas.olyzecs.engine.camera.CameraFrame] — ya es
 * un DTO plano de floats, sin motivo para duplicarlo (ver diseño
 * aprobado, sección 6: "SI, directo, sin cambios").
 */
data class LayerSnapshot(
    val id: String,
    val sourceUri: Uri,
    val name: String,
    val zIndex: Int,
    val parallaxFactor: Float = 1f,
    val locked: Boolean = false,
    val orderLocked: Boolean = false,
    val visible: Boolean = true,
    val lookSettings: LookSettings = LookSettings(),
    val baseFrame: CameraFrame,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val colorIndex: Int = 0,
    val customColorArgb: Int? = null,
    val importedDefaultColorArgb: Int? = null,
    val customGradientStartArgb: Int? = null,
    val customGradientEndArgb: Int? = null,
    val useGradientColor: Boolean = false,
    val gradientAngleDegrees: Float = 90f,
    val gradientIsRadial: Boolean = false,
    val useBlackAndWhiteMode: Boolean = false
)
