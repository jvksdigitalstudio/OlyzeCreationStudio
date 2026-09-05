# Arquitectura de Olyze Creation Studio

> Generado en la Fase D (post Fase C.1 — ver `docs/adr/` para las decisiones
> que llevaron a esta estructura). Este documento describe el estado REAL
> del código, verificado, no un ideal aspiracional. Si algo de acá deja de
> ser cierto, corregir este archivo en el mismo cambio que lo desactualice.

## Mapa de capas

```
UI                ui/*.kt (Compose) — interacción, estado visual
Presentation      viewmodel/*.kt — EditorViewModel, ProjectsViewModel
Application        MainActivity — composition root (única clase que
                    instancia ProjectStorage/LayerRepository/Factories)
Data/Infra         data/*.kt — ProjectStorage, LayerRepository,
                    ProjectModels, ColorExtraction
Platform           platform/DisplayRefreshRate.kt — infraestructura
                    Android de bajo nivel, no motor
Engine             engine/{core,scene,camera,render,animation,audio,
                    effects,timeline,export,mesh3d}
Debug              debug/AppLogger.kt — logging transversal
```

Flujo real hoy:

```
UI → EditorViewModel → engine.*                 (edición en vivo — mayoría de dominios)
UI → EditorViewModel → EliNer API (Mesh3DApi) → engine.mesh3d
                                                  (extrusión 3D — ver tarea "Mesh3D → EliNer")
UI → EditorViewModel → EliNer API (AnimationApi) → engine.animation
                                                  (velocidad variable + freeze frame — ver
                                                  tarea "Animation → EliNer")
UI → EditorViewModel → EliNer API (ExportApi) → engine.export
                                                  (exportación de video — ver tarea
                                                  "Timeline+Export → EliNer"; Mesh3D, Animation
                                                  y Export son, por ahora, los 3 dominios con
                                                  consumidor real de EliNer API)
UI → EditorViewModel → ProjectStorage → engine.audio/engine.timeline
                                                  (persistencia)
MainActivity → platform.DisplayRefreshRate       (arranque)
UI          → platform.DisplayRefreshRate        (consulta de capacidad)
```

`EditorViewModel` es el único componente de la capa de **presentación**
(UI + ViewModels) que le habla directo a `engine.*` — ver su KDoc de
clase para el detalle y la excepción documentada de `ProjectStorage`.
Excepción adicional, real desde las tareas "Mesh3D → EliNer",
"Animation → EliNer" y "Timeline+Export → EliNer": para los dominios
`Mesh3D`, `Animation` y `Export` específicamente, `EditorViewModel` ya
NO habla directo con `engine.mesh3d`/`engine.animation`/`engine.export`
— pasa por `EliNer API` (`Mesh3DApi`, `AnimationApi`, `ExportApi`
respectivamente). Son los únicos 3 dominios migrados así por ahora; el
resto sigue con el patrón original de este párrafo — con la salvedad de
que 2 de las funciones de `Timeline` (`seekTo`/`retimeKeyframe`) fueron
limpiadas para reusar una función ya existente de `ActiveProjectMutator`
(`ensureTimelineCapacityFor`), sin que eso implique que `TimelineApi`
tenga un consumidor externo real todavía.

## Qué es el "Engine" (EliNer Core futuro)

Todo lo que vive bajo `engine/*` transforma datos del proyecto (capas,
cámara, audio, timeline) en un resultado (píxeles, un archivo de video,
una miniatura) sin conocer Compose/ViewModel/navegación. Subpaquetes:

- `engine/core` — contratos mínimos compartidos (`PixelColorSource`).
- `engine/scene` — `Layer`, `AspectRatioPreset`.
- `engine/camera` — `CameraFrame`, `Keyframe`.
- `engine/render` — `GLRenderer`, `LayerDrawer`, `ShaderProgram` (OpenGL ES),
  `RenderSnapshot` (Fase 2), `GpuTextureLimits`. FASE 3 agregó
  `GLRendererLifecycleState`, `GpuHandle`/`GpuContextGeneration` y
  `GridTextureCacheState` — lógica PURA (sin GLES) de ciclo de vida y
  ownership de recursos GPU, ver `docs/fases/FASE_3_RENDER_GL_LIFECYCLE.md`.
- `engine/animation` — `Easing`, `SpeedRampEngine`, `SpeedKeyframe`, `FreezeFrame`.
- `engine/audio` — `AudioProcessor`, `AudioPreviewPlayer`, `AudioClip`.
- `engine/effects` — `LookSettings`.
- `engine/timeline` — `ThumbnailRenderer`, `TimelineExpansionPolicy`,
  `TimelineDurationManager`, `TimelineLimits`, `TimelineState`,
  `TimelineEvents` (movidos acá en la Fase D — ver ADR-001).
- `engine/export` — `VideoExporter`, `ExportSettings`, `ExportQuality`.
- `engine/mesh3d` — `Mesh3D`, `Camera3D`, `MeshRasterizer`, `Vec3`,
  `Extrude3D` (movido acá en la Fase B).

## DAG de `engine/*` (sin ciclos, verificado en Fases B/C/D)

```
core, animation, audio, effects, mesh3d   (hojas)
        │
camera ─┴─ animation
        │
scene ──┴─ camera, effects
        │
render ─┴─ camera, core, scene, effects
        │
timeline ─┴─ render, scene
export    ─┴─ scene, animation, audio, render
```

## Qué queda fuera del Engine (y por qué)

- **`ProjectStorage`/`LayerRepository`** (`data/*`): persistencia de
  proyecto (JSON, ZIP, assets, thumbnails, autosave). Es
  Application/Infrastructure, no motor — decisión confirmada en 3
  auditorías independientes (Fase B, Fase C, Fase C.1/ADR-004).
- **`platform/DisplayRefreshRate`**: consulta de una capacidad del
  dispositivo (tasa de refresco de pantalla), no procesa nada del
  proyecto.
- **`ui/*`, `viewmodel/*`**: consumidores del motor, no parte de él.

## Documentos relacionados

- `docs/adr/ADR-001-timeline.md` — por qué Timeline vive en `engine/timeline/`.
- `docs/adr/ADR-002-android-context.md` — por qué no se abstrae `Context`.
- `docs/adr/ADR-003-error-handling.md` — categorización de errores.
- `docs/adr/ADR-004-eliner-boundary.md` — principio rector de la frontera de EliNer API.
- `ENGINE_OPERATIONS.md` — inventario de operaciones del motor (base de
  la futura superficie de EliNer API).
