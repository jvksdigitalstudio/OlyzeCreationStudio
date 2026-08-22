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
 * Implementación real de [EliNerApi] — puro agregador, NO owner de
 * estado (ninguna propiedad acá es `var`, ninguna almacena una copia de
 * nada; cada dominio ya resuelve su propio acceso al proyecto activo a
 * través de [com.yeivikas.olyzecs.api.project.ActiveProjectReader]/
 * `ActiveProjectMutator`, implementados por `EditorViewModel`).
 *
 * [render] se recibe por constructor porque, a diferencia de los otros 7
 * dominios, su implementación real ([PixelColorSource], respaldada por
 * `GLRenderer`) requiere una superficie GL viva — hoy solo existe dentro
 * de `ui/GLPreview.kt` (Composable), fuera de alcance de esta fase (regla
 * 16: "no modificar la capa de UI"). Quien construya esta fachada donde
 * SÍ haya
 * una superficie GL disponible (una migración de UI posterior, no esta
 * fase) provee ese valor — acá no se inventa ni se deja sin resolver
 * silenciosamente.
 */
class EliNerApiImpl(
    override val scene: LayerApi,
    override val camera: CameraApi,
    override val animation: AnimationApi,
    override val timeline: TimelineApi,
    override val render: PixelColorSource,
    override val audio: AudioApi,
    override val export: ExportApi,
    override val mesh3d: Mesh3DApi
) : EliNerApi
