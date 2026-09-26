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
 * Implementación real de [EliNerApi] — puro agregador, NO owner de
 * estado (ninguna propiedad acá es `var`, ninguna almacena una copia de
 * nada; cada dominio ya resuelve su propio acceso al proyecto activo a
 * través de [com.yeivikas.olyzecs.api.project.ActiveProjectReader]/
 * `ActiveProjectMutator`, implementados por `EditorViewModel` — o, en el
 * caso de [distortion]/[mesh3d]/[animation], sin necesitarlos porque son
 * dominios sin estado de proyecto que leer).
 *
 * [render] se recibe por constructor como [RenderApi] (Fase 4.3, RENDER
 * BOUNDARY — antes era directamente
 * [com.yeivikas.olyzecs.engine.core.PixelColorSource]; ver el KDoc de
 * [RenderApi] y de [EliNerApi.render] para el razonamiento completo del
 * cambio de contrato). A diferencia de los otros 8 dominios, su
 * implementación real (`RenderApiImpl`, envolviendo `PixelColorSource`
 * respaldado por `GLRenderer`) YA tiene un consumidor productivo real
 * (`EditorScreen`, para el cuentagotas de color — ver "wiring
 * productivo de RenderApi", Fase 4.3), pero esa instancia nace y muere
 * con el ciclo de vida de la superficie GL dentro del Composable
 * `GLPreview` — no dentro de `MainActivity`, que es donde se ensamblan
 * los otros 8 dominios. Por eso no existe todavía, en producción, una
 * única instancia de `EliNerApiImpl` con los 9 dominios juntos: hacerlo
 * exigiría sacar esa instancia de `RenderApiImpl` del Composable hacia
 * `MainActivity`, algo que esta intervención no fuerza artificialmente
 * (ver KDoc de [EliNerApi.render] para el detalle completo). Quien
 * ensamble esa fachada única en el futuro provee ese valor — acá no se
 * inventa ni se deja sin resolver silenciosamente.
 *
 * [distortion] se agrega en Fase 4.1 (hallazgo prioritario "Distortion"
 * — ver KDoc de [EliNerApi]): reutiliza la MISMA instancia de
 * [DistortionApi] que ya construye `MainActivity` e inyecta en
 * `EditorViewModel` (vía `EditorViewModelFactory`) — no se crea una
 * segunda instancia para esta fachada.
 */
class EliNerApiImpl(
    override val scene: LayerApi,
    override val camera: CameraApi,
    override val animation: AnimationApi,
    override val timeline: TimelineApi,
    override val render: RenderApi,
    override val audio: AudioApi,
    override val export: ExportApi,
    override val mesh3d: Mesh3DApi,
    override val distortion: DistortionApi
) : EliNerApi
