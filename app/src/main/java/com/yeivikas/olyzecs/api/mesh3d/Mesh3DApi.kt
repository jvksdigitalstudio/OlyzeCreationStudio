package com.yeivikas.olyzecs.api.mesh3d

import android.graphics.Bitmap
import com.yeivikas.olyzecs.engine.mesh3d.Extrude3D

/**
 * Contrato público de EliNer para el efecto de extrusión 3D.
 *
 * Respaldo real: `engine.mesh3d.Extrude3D.render`. [Extrude3D.Params]
 * se reutiliza directo (ya es un DTO plano: 3 rotaciones + profundidad
 * + bisel + opacidad).
 *
 * Primer dominio migrado de punta a punta: `EditorViewModel.renderExtrude3D`
 * (viewmodel/EditorViewModel.kt) ya delega en [Mesh3DApiImpl] en vez de
 * llamar `Extrude3D.render` directo (ver tarea "Mesh3D → EliNer"). Es el
 * primer caso real de un consumidor de la aplicación usando `EliNer API`
 * como frontera — sirvió de plantilla para migrar el resto de los
 * dominios (Layer, Camera, Animation, Audio, Timeline, Export), que
 * siguen teniendo su `*ApiImpl` conectado pero sin consumidor real
 * todavía.
 */
interface Mesh3DApi {

    /** Aplica extrusión 3D a [source] según [params]. Operación pesada: suspende. */
    suspend fun extrude(source: Bitmap, params: Extrude3D.Params, highQuality: Boolean = true): Bitmap
}
