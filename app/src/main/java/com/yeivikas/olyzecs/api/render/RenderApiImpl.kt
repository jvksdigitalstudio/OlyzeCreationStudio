package com.yeivikas.olyzecs.api.render

import com.yeivikas.olyzecs.engine.core.PixelColorSource

/**
 * Implementación real de [RenderApi].
 *
 * Deliberadamente trivial — un solo campo, sin lógica propia — porque
 * [RenderApi] hoy solo tiene una capacidad real ([pixelColor]) y esta
 * clase no le agrega ningún comportamiento nuevo, solo lo expone
 * detrás del contrato de dominio: quien construye esta fachada
 * (`EditorScreen`, en `onRendererReady` de `GLPreview` — consumidor
 * productivo real desde la intervención "Wiring productivo de
 * RenderApi" de Fase 4.3, ver KDoc de
 * [com.yeivikas.olyzecs.api.EliNerApi] para el detalle completo, y
 * `docs/fases/FASE_4_3_RENDER_BOUNDARY.md` para la auditoría que lo
 * cerró) pasa la implementación real de [PixelColorSource]
 * ([com.yeivikas.olyzecs.engine.render.GLRenderer], hoy la única que
 * existe) sin que [RenderApiImpl] necesite saber que es GL por debajo.
 * Lo único que sigue pendiente (no un defecto de esta clase) es
 * unificar los 9 dominios de `EliNerApiImpl` en una única instancia de
 * producción — bloqueado por el ciclo de vida de este objeto, ligado a
 * la Composable `EditorScreen`, frente al de los otros 8, ligados a
 * `MainActivity` (mismo KDoc de [com.yeivikas.olyzecs.api.EliNerApi]).
 */
class RenderApiImpl(override val pixelColor: PixelColorSource) : RenderApi
