package com.yeivikas.olyzecs.api

import com.yeivikas.olyzecs.api.animation.AnimationApi
import com.yeivikas.olyzecs.api.audio.AudioApi
import com.yeivikas.olyzecs.api.camera.CameraApi
import com.yeivikas.olyzecs.api.export.ExportApi
import com.yeivikas.olyzecs.api.mesh3d.Mesh3DApi
import com.yeivikas.olyzecs.api.scene.LayerApi
import com.yeivikas.olyzecs.api.timeline.TimelineApi
import com.yeivikas.olyzecs.engine.core.PixelColorSource

/**
 * Punto de entrada público de EliNer API v1 — la frontera entre
 * Olyze Creation Studio (aplicación) y el motor de edición/render/export:
 *
 * ```
 * UI / Application
 *        │
 *        ▼
 *   EliNer API   (esta interfaz)
 *        │
 *        ▼
 *  EliNer Engine  (paquete engine, actual, Kotlin)
 * ```
 *
 * Cada propiedad representa un DOMINIO del motor, respaldado 1:1 en
 * código real — ver ELINER_API_V1_FASE1_DISENO.txt (diseño aprobado) y
 * ELINER_API_V1_AUDITORIA_DISENO.txt (auditoría que lo cerró) para el
 * detalle y la matriz de trazabilidad completa.
 *
 * [render] reutiliza directamente
 * [com.yeivikas.olyzecs.engine.core.PixelColorSource] — YA
 * es una frontera pública real (implementada por `GLRenderer`, usada
 * hoy por `EditorScreen` a través de `GLPreview`), no se duplica acá.
 *
 * IMPORTANTE (Etapa 2 — Fase 1.4, "conexión de dominios"): 7 de los 8
 * dominios ya tienen una implementación real conectada al estado real
 * del proyecto ([com.yeivikas.olyzecs.api.scene.LayerApiImpl],
 * [com.yeivikas.olyzecs.api.camera.CameraApiImpl],
 * [com.yeivikas.olyzecs.api.animation.AnimationApiImpl],
 * [com.yeivikas.olyzecs.api.timeline.TimelineApiImpl],
 * [com.yeivikas.olyzecs.api.audio.AudioApiImpl],
 * [com.yeivikas.olyzecs.api.export.ExportApiImpl],
 * [com.yeivikas.olyzecs.api.mesh3d.Mesh3DApiImpl]), a través de
 * `ActiveProjectReader`/`ActiveProjectMutator` — NUNCA dependiendo del
 * tipo concreto `EditorViewModel`. El único dominio sin conectar es
 * [render]: su respaldo real ([PixelColorSource]/`GLRenderer`) requiere
 * una superficie GL viva que hoy solo existe dentro de un Composable de
 * la capa de UI, fuera de alcance de esta fase (no se modifica UI). Ver
 * [EliNerApiImpl] — el agregador real, que sigue sin ser "wrapper
 * mecánico sin justificación" porque ahora SÍ tiene 7/8 dominios
 * genuinamente conectados detrás.
 *
 * Nota (tareas "Mesh3D → EliNer" / "Animation → EliNer" /
 * "Timeline+Export → EliNer"): "conectada" arriba significa que el
 * wiring existe y compila — no que la app ya la use. **Mesh3D,
 * Animation y Export son, por ahora, los dominios con consumidor
 * real**: `EditorViewModel` inyecta/construye [Mesh3DApi]/
 * [AnimationApi]/[ExportApi] y los usa de verdad. Timeline tuvo una
 * limpieza de código interno (reusa una función que ya era parte del
 * propio `ActiveProjectMutator`) pero [TimelineApi]/`TimelineApiImpl`
 * en sí sigue sin consumidor externo real. Layer, Camera y Audio
 * siguen esperando — se auditaron y se encontró que migrarlos con el
 * mismo patrón mecánico requiere antes una decisión de diseño (riesgo
 * de llamada circular / incompatibilidad de sincronía), no es trabajo
 * puramente mecánico como los 3 ya migrados.
 */
interface EliNerApi {
    val scene: LayerApi
    val camera: CameraApi
    val animation: AnimationApi
    val timeline: TimelineApi
    val render: PixelColorSource
    val audio: AudioApi
    val export: ExportApi
    val mesh3d: Mesh3DApi
}
