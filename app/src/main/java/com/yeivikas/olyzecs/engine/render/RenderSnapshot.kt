package com.yeivikas.olyzecs.engine.render

import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.Keyframe
import com.yeivikas.olyzecs.engine.effects.LookSettings

/**
 * FASE 2 — Concurrencia, Estado de Render, Reproducción y Duración.
 *
 * Representación INMUTABLE de todo lo que [GLRenderer] necesita para
 * dibujar un frame, separada explícitamente del estado vivo y mutable del
 * editor (`Layer`, `CameraTrack`). Es el "Stable State / RenderSnapshot"
 * de la arquitectura objetivo del brief de Fase 2:
 *
 *     UI → ViewModel → Stable State/RenderSnapshot → GL Renderer
 *
 * Contiene SOLO datos lógicos (posición, escala, rotación, visibilidad,
 * zIndex, look, keyframes) — nunca recursos de runtime/GPU (Bitmap,
 * texturas, handles GL, Context, Uri). Esos siguen viviendo en el propio
 * `Layer` (campos `@Volatile` — ver Layer.kt), porque son responsabilidad
 * exclusiva del hilo de GL (caché de textura por capa) y mezclarlos acá
 * violaría la separación que pide la sección 3 del brief ("no copies
 * indiscriminadamente Bitmaps", "no mezcles datos lógicos con recursos
 * runtime").
 *
 * DECISIÓN DE DISEÑO (documentada, ver informe de Fase 2): la seguridad
 * real ante condiciones de carrera NO depende únicamente de que este DTO
 * exista — depende de que cada campo que copia sea, en sí mismo, seguro
 * de leer desde cualquier hilo en el instante de la copia. Por eso:
 *   - `CameraTrack.keyframes`/`.baseFrame` ya publican una snapshot
 *     inmutable propia (`@Volatile`, ver CameraTrack.kt) — copiarlos acá
 *     nunca puede leer una lista a medio mutar, sin importar en qué hilo
 *     corra la conversión (ver `EditorUiState.toRenderSnapshot()` en
 *     EditorViewModel.kt).
 *   - `zIndex`/`visible`/`parallaxFactor`/`lookSettings` son `@Volatile`
 *     en `Layer.kt` por el mismo motivo.
 * Gracias a esto, capturar un [RenderSnapshot] es seguro tanto si se
 * arma en el hilo principal como si se arma en el propio hilo de GL —
 * no depende de temporizar la captura en un instante "seguro" particular,
 * a diferencia de una snapshot que copiara colecciones mutables sin
 * protección propia.
 */
data class RenderLayerSnapshot(
    val id: String,
    val zIndex: Int,
    val visible: Boolean,
    val parallaxFactor: Float,
    val lookSettings: LookSettings,
    val keyframes: List<Keyframe>,
    val baseFrame: CameraFrame
) {
    /**
     * Encuadre interpolado en [timeMs] — delega en
     * [com.yeivikas.olyzecs.engine.camera.CameraFrameInterpolation],
     * la MISMA función pura que usa `CameraTrack.frameAt`, para que
     * ambos caminos (edición en vivo vs. render desde snapshot) nunca
     * puedan divergir en el resultado.
     */
    fun frameAt(timeMs: Long): CameraFrame =
        com.yeivikas.olyzecs.engine.camera.CameraFrameInterpolation.at(keyframes, baseFrame, timeMs)
}

/** Snapshot inmutable de TODAS las capas a renderizar en un instante dado. Ver [RenderLayerSnapshot]. */
data class RenderSnapshot(
    val layers: List<RenderLayerSnapshot>
) {
    companion object {
        val EMPTY = RenderSnapshot(emptyList())
    }
}
