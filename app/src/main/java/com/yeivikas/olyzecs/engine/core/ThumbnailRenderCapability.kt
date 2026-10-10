package com.yeivikas.olyzecs.engine.core

import android.content.Context
import android.graphics.Bitmap
import com.yeivikas.olyzecs.engine.scene.Layer

/**
 * FASE 4.3 (RENDER BOUNDARY, R4.3.7/R4.3.8 del prompt maestro de Fase
 * 4.3): capacidad de render de una miniatura del proyecto — mismo
 * criterio arquitectónico que ya usa [PixelColorSource] en este mismo
 * paquete: un contrato mínimo en `engine.core`, sin ningún tipo de
 * GLES/EGL en la firma, para que quien SOLICITA una miniatura
 * (`TimelineApi`, `ProjectStorage`) no necesite conocer que la
 * implementación real hoy usa su propio contexto EGL aislado y
 * `LayerDrawer` (ver [com.yeivikas.olyzecs.engine.timeline.ThumbnailRenderer],
 * la única implementación real hoy).
 *
 * Antes de esta fase, `TimelineApiImpl`/`ProjectStorage` llamaban
 * directo al `object ThumbnailRenderer.render(...)` — es decir,
 * Infrastructure/API dependía de la implementación GL concreta, no de un
 * resultado de dominio (`Infrastructure → GL/EGL renderer`, exactamente
 * el problema que describe R4.3.8 del prompt maestro). Ahora dependen de
 * esta interfaz, con [com.yeivikas.olyzecs.engine.timeline.ThumbnailRenderer]
 * como valor por defecto — mismo comportamiento exacto en tiempo de
 * ejecución (es el mismo objeto, la única implementación que existe),
 * pero el TIPO del que dependen ya no obliga a conocer GL/EGL.
 *
 * `Context`/`List<Layer>` se mantienen en la firma a propósito: este es
 * un seam INTERNO (no una propiedad pública nueva de `EliNerApi` — eso
 * sería adelantar más diseño del que pide esta fase, ver R4.3.3: "no
 * inventes APIs innecesarias"), análogo a `ActiveProjectReader`/
 * `ActiveProjectMutator`, que también usan tipos internos del Engine
 * porque son bridges internos, no la frontera pública final.
 */
interface ThumbnailRenderCapability {
    fun render(
        context: Context,
        layers: List<Layer>,
        timeMs: Long = 0L,
        widthPx: Int = 360,
        heightPx: Int = 640
    ): Bitmap?
}
