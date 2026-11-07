package com.yeivikas.olyzecs.api.render

import com.yeivikas.olyzecs.engine.core.PixelColorSource

/**
 * Contrato público de EliNer para el dominio Render — Fase 4.3 (RENDER
 * BOUNDARY, intervención "Render Contract").
 *
 * ## Por qué existe esta interfaz y no simplemente `EliNerApi.render: PixelColorSource`
 *
 * Antes de esta intervención, `EliNerApi.render` ERA literalmente
 * [PixelColorSource] — una única capacidad ("leer el color exacto de un
 * pixel del preview en vivo, para el cuentagotas"), no un dominio Render
 * completo. Eso funcionaba, pero mezclaba dos cosas distintas: "el
 * contrato del dominio Render" y "una capacidad puntual del dominio
 * Render". Si mañana aparece una segunda capacidad real de Render
 * (p. ej. consultar capacidades del backend — versión de OpenGL ES,
 * límites de textura — ver `R4.3.15` del prompt maestro de Fase 4.3),
 * no habría dónde agregarla sin romper el tipo de `EliNerApi.render` o
 * sin convertir `PixelColorSource` en algo que ya no es ("leer un
 * pixel" dejaría de ser una interfaz de una sola responsabilidad).
 *
 * `RenderApi` es ese contenedor: HOY solo tiene una propiedad real
 * ([pixelColor]) porque es la única capacidad de Render con respaldo
 * real en el código (ver [com.yeivikas.olyzecs.engine.render.GLRenderer],
 * que implementa [PixelColorSource] directo). No se inventaron
 * `RenderRequest`/`RenderResult`/`RenderCapabilities` sin necesidad —
 * ver la auditoría completa en `docs/fases/FASE_4_3_RENDER_BOUNDARY.md`
 * (sección de esta intervención) para por qué esos conceptos NO están
 * justificados todavía por el código real: el render de un frame en
 * este proyecto es un bucle PULL (`GLRenderer.onDrawFrame` llama a un
 * `getRenderSnapshot: () -> RenderSnapshot` cada frame), no una llamada
 * PUSH tipo `render(request): result` — forzar esa forma acá sería
 * inventar una arquitectura que el código real no tiene.
 *
 * ## Qué NO es esta interfaz
 *
 * NO es un espejo de `GLRenderer` — no expone `onDrawFrame`,
 * `onSurfaceCreated`, ni ningún método de ciclo de vida GL/EGL/Android
 * (eso pertenece al Render Host — `GLPreview`/`GLSurfaceView` — fuera de
 * alcance de esta intervención, ver R4.3.14 del prompt maestro:
 * "no tocar GLPreview profundamente todavía"). NO reemplaza
 * [com.yeivikas.olyzecs.engine.render.RenderSnapshot] (que sigue
 * siendo interno — ver su propio KDoc para la decisión completa, no se
 * movió ni se expuso acá: mezclaría el mecanismo interno de "estado
 * preparado para el bucle GL" con la frontera pública). NO incluye
 * `ThumbnailRenderCapability` — esa es una capacidad de render DISTINTA
 * (su propio contexto EGL aislado tipo pbuffer, sin relación con la
 * superficie de preview en vivo) con su propio boundary ya cerrado
 * (Fase 4.3, R4.3.7/R4.3.8); fusionarla acá sin una razón real sería
 * sobrearquitectura, no una simplificación.
 */
interface RenderApi {
    /**
     * Capacidad de leer el color exacto de un pixel del preview en vivo
     * (cuentagotas). Es, hoy, la ÚNICA capacidad de este dominio con
     * consumidor real (`EditorScreen`, a través de `GLPreview` — fuera
     * de esta interfaz, ver [com.yeivikas.olyzecs.api.EliNerApi] para el
     * motivo de por qué `EliNerApi` en sí sigue sin una instancia
     * productiva de este dominio conectada).
     */
    val pixelColor: PixelColorSource
}
