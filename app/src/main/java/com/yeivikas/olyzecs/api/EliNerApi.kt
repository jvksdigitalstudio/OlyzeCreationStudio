package com.yeivikas.olyzecs.api

import com.yeivikas.olyzecs.api.animation.AnimationApi
import com.yeivikas.olyzecs.api.audio.AudioApi
import com.yeivikas.olyzecs.api.camera.CameraApi
import com.yeivikas.olyzecs.api.distortion.DistortionApi
import com.yeivikas.olyzecs.api.export.ExportApi
import com.yeivikas.olyzecs.api.mesh3d.Mesh3DApi
import com.yeivikas.olyzecs.api.render.RenderApi
import com.yeivikas.olyzecs.api.scene.LayerApi
import com.yeivikas.olyzecs.api.timeline.TimelineApi

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
 * ## [render] — Fase 4.3 (RENDER BOUNDARY, intervención "Render Contract")
 *
 * [render] dejó de ser directamente
 * [com.yeivikas.olyzecs.engine.core.PixelColorSource] — ahora es
 * [RenderApi], un contrato de dominio propio que HOY expone esa misma
 * capacidad como su única propiedad real ([RenderApi.pixelColor]). El
 * motivo del cambio (no cosmético — ver el KDoc completo de [RenderApi]
 * para el razonamiento y qué se evaluó y se descartó): `PixelColorSource`
 * es una ÚNICA capacidad puntual ("leer un pixel"), no un dominio Render
 * completo — usarla directo como el tipo de `render` no dejaba dónde
 * crecer sin romper el contrato el día que aparezca una segunda
 * capacidad real de Render (p. ej. capacidades del backend). `RenderApi`
 * es ese contenedor extensible; no se inventaron `RenderRequest`/
 * `RenderResult` porque el código real no los justifica todavía (el
 * render de un frame es un bucle PULL sobre `RenderSnapshot`, no una
 * llamada `render(request): result` — ver KDoc de `RenderApi`).
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
 * dependiendo del tipo concreto `EditorViewModel`.
 *
 * FASE 4.3 (RENDER BOUNDARY — "wiring productivo de RenderApi"):
 * [render] SÍ tiene ya un consumidor real y productivo — `EditorScreen`
 * construye un [com.yeivikas.olyzecs.api.render.RenderApiImpl] real en
 * el momento en que `GLPreview` entrega la instancia viva de
 * [com.yeivikas.olyzecs.engine.core.PixelColorSource] (respaldada por
 * `GLRenderer`), y el cuentagotas de color ya lo usa a través de
 * `renderApi.pixelColor.requestPixelColor(...)`, nunca hablando con
 * `GLRenderer` directo. Lo que SIGUE sin existir es una instancia de
 * [EliNerApiImpl] con los 9 dominios juntos en producción: ese
 * `RenderApiImpl` real vive con el ciclo de vida de la Composable
 * `EditorScreen` (nace/muere con la superficie GL), mientras que los
 * otros 8 dominios se instancian en `MainActivity` — unificarlos en un
 * solo `EliNerApiImpl` exigiría sacar esa instancia del Composable hacia
 * `MainActivity` de alguna forma (p. ej. un callback de vuelta, similar a
 * `onRendererReady`), lo cual no era necesario para que `render` dejara
 * de ser scaffolding y quedó fuera de esta intervención a propósito (no
 * se fuerza una integración artificial solo para tener un diagrama con
 * los 9 dominios en un único objeto — ver regla 15 del prompt maestro de
 * esta fase). Ver [EliNerApiImpl] — el agregador real, que sigue sin ser
 * "wrapper mecánico sin justificación" porque ahora tiene 8 de los 9
 * dominios contractuales genuinamente conectados y componibles detrás,
 * y el noveno ([render]) también tiene una implementación real y
 * consumida — solo que todavía en un objeto `RenderApiImpl` suelto, no
 * dentro de una instancia de `EliNerApiImpl`.
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
    val render: RenderApi
    val audio: AudioApi
    val export: ExportApi
    val mesh3d: Mesh3DApi
    val distortion: DistortionApi
}
