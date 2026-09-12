package com.yeivikas.olyzecs.api

import com.yeivikas.olyzecs.api.animation.AnimationApi
import com.yeivikas.olyzecs.api.audio.AudioApi
import com.yeivikas.olyzecs.api.camera.CameraApi
import com.yeivikas.olyzecs.api.distortion.DistortionApi
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
 * Cada propiedad representa un DOMINIO CONTRACTUAL de la API — un
 * resultado que el motor produce (ADR-004: la frontera de EliNer se
 * define por dominio/resultado, no por una correspondencia obligatoria
 * 1:1 entre propiedad pública y clase concreta del Engine). Cada
 * dominio tiene respaldo real en código, compuesto internamente a
 * partir del o los componentes del Engine que corresponda — ver
 * ELINER_API_V1_FASE1_DISENO.txt (diseño aprobado) y
 * ELINER_API_V1_AUDITORIA_DISENO.txt (auditoría que lo cerró) para el
 * detalle y la matriz de trazabilidad completa. Ambos son documentos de
 * proceso históricos y no están versionados en este repositorio.
 *
 * [render] reutiliza directamente
 * [com.yeivikas.olyzecs.engine.core.PixelColorSource] — YA
 * es una frontera pública real (implementada por `GLRenderer`, usada
 * hoy por `EditorScreen` a través de `GLPreview`), no se duplica acá.
 *
 * IMPORTANTE (Etapa 2 — Fase 1.4, "conexión de dominios"): 8 de los 9
 * dominios ya tienen una implementación real conectada al estado real
 * del proyecto ([com.yeivikas.olyzecs.api.scene.LayerApiImpl],
 * [com.yeivikas.olyzecs.api.camera.CameraApiImpl],
 * [com.yeivikas.olyzecs.api.animation.AnimationApiImpl],
 * [com.yeivikas.olyzecs.api.timeline.TimelineApiImpl],
 * [com.yeivikas.olyzecs.api.audio.AudioApiImpl],
 * [com.yeivikas.olyzecs.api.export.ExportApiImpl],
 * [com.yeivikas.olyzecs.api.mesh3d.Mesh3DApiImpl],
 * [com.yeivikas.olyzecs.api.distortion.DistortionApiImpl]), a través de
 * `ActiveProjectReader`/`ActiveProjectMutator` (o, en el caso de
 * Distortion/Mesh3D/Animation, sin estado propio que gestionar) — NUNCA
 * dependiendo del tipo concreto `EditorViewModel`. El único dominio sin
 * conectar es [render]: su respaldo real ([PixelColorSource]/
 * `GLRenderer`) requiere una superficie GL viva que hoy solo existe
 * dentro de un Composable de la capa de UI, fuera de alcance de esta
 * fase (no se modifica UI). Ver [EliNerApiImpl] — el agregador real, que
 * sigue sin ser "wrapper mecánico sin justificación" porque ahora SÍ
 * tiene 8 de los 9 dominios contractuales genuinamente conectados y
 * componibles detrás (el noveno, [render], sigue siendo parte del
 * contrato — ver arriba — solo que aún sin composición productiva).
 *
 * Nota (tareas "Mesh3D → EliNer" / "Animation → EliNer" /
 * "Timeline+Export → EliNer" / "Distortion → EliNer"): "conectada"
 * arriba significa que el wiring existe y compila — no que la app ya la
 * use a través de esta fachada. **Mesh3D, Animation, Export y
 * Distortion son, por ahora, los dominios con consumidor real**:
 * `EditorViewModel` inyecta/construye [Mesh3DApi]/[AnimationApi]/
 * [ExportApi]/[DistortionApi] y los usa de verdad
 * (`EditorViewModel.renderDistortion` delega en [DistortionApi] desde
 * su primer commit — ver KDoc de [DistortionApi]). Timeline tuvo una
 * limpieza de código interno (reusa una función que ya era parte del
 * propio `ActiveProjectMutator`) pero [TimelineApi]/`TimelineApiImpl`
 * en sí sigue sin consumidor externo real. Layer, Camera y Audio
 * siguen esperando — se auditaron y se encontró que migrarlos con el
 * mismo patrón mecánico requiere antes una decisión de diseño (riesgo
 * de llamada circular / incompatibilidad de sincronía), no es trabajo
 * puramente mecánico como los 4 ya migrados.
 *
 * Nota de composición (Fase 4.1, hallazgo prioritario "Distortion"):
 * antes de esta fase, [DistortionApi] existía como dominio real
 * (consumidor real incluido) pero NO estaba expuesto por esta fachada
 * — quedaba huérfano fuera de `EliNerApi`, aunque el resto de los
 * dominios ya estuviera acá. Se corrige incorporando [distortion] como
 * propiedad de esta interfaz, sin tocar `DistortionApiImpl` (sigue sin
 * estado propio) ni el algoritmo de `DistortionRasterizer`. Esto NO
 * implica que `MainActivity` ya construya una instancia de
 * [EliNerApiImpl] con los 9 dominios: eso sigue bloqueado únicamente
 * por [render] (ver párrafo anterior) — el resto de los 8 dominios,
 * incluido `distortion`, ya se instancian hoy en `MainActivity` como
 * piezas sueltas, listas para componerse el día que [render] deje de
 * ser el bloqueador.
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
    val distortion: DistortionApi
}
