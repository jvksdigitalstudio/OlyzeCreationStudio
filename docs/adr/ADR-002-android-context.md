# ADR-002 — No abstraer `Context`/APIs de sistema en el Engine dependiente de plataforma

**Estado:** Decidido (Fase C.1). Sin acción de código — decisión
permanente, no deuda pendiente.

## Contexto
`AudioProcessor`, `AudioPreviewPlayer`, `VideoExporter`,
`ThumbnailRenderer` y `GLRenderer` reciben `Context`/`ContentResolver`
directo como parámetro, porque son la única vía de acceso a los
servicios de sistema Android que realmente usan (`MediaCodec`,
`MediaPlayer`, `MediaMuxer`, `GLES20`, `EGL14`).

## Problema
¿Debe la futura EliNer API abstraer `Context` para ganar portabilidad
(p. ej. de cara a una futura migración a C++)?

## Opciones consideradas
- **A) La API acepta Android directo** (`Context`, `Uri`, `Bitmap`) en
  su superficie pública. Cero trabajo, pero ata el contrato público a
  Android permanentemente.
- **B) Abstraer con interfaces propias** (p. ej. `ResourceProvider`/
  `MediaSource`) para que el Core nunca vea `Context`.
- **C) Híbrido:** el Core puro (mesh3d, animation, timeline — ver
  ADR-001) nunca ve Android, hoy ya es así; el Engine dependiente de
  plataforma (audio/render/export) sigue recibiendo `Context` directo,
  sin abstraerlo.

## Decisión
**Opción C.**

## Justificación
Abstraer `Context` en `AudioProcessor`/`VideoExporter`/`GLRenderer` no
los vuelve más portables: su dependencia real no es `Context`, es
`MediaCodec`/`GLES20`/`EGL14` — APIs de Android sin sustituto directo en
ningún otro lado. Cualquier migración futura de esos componentes va a
requerir reescribirlos enteros de todas formas (ver
`ENGINE_OPERATIONS.md`/candidatos a C++: estos componentes ya están
clasificados como "NO MIGRAR" salvo un proyecto de NDK dedicado).
Invertir esfuerzo en abstraer `Context` ahí, sin abstraer también GLES/
MediaCodec, es indirección sin beneficio real — la "abstracción
prematura" que las fases anteriores pidieron evitar explícitamente.

## Consecuencias
Ninguna acción de código. El límite queda donde ya está naturalmente:
el Core puro sigue sin ver Android jamás (ya lo cumple hoy); el Engine
dependiente de plataforma sigue recibiendo `Context`/`Uri` directo.

## Impacto futuro en EliNer
Las funciones de audio/render/export de la API van a requerir `Context`
de Android en su firma, y eso es aceptado por diseño, no deuda.

## Impacto futuro en C++
Si algún día se decide migrar render/audio a C++/NDK, ese proyecto
específico define su propia capa de interoperabilidad en ese momento —
no es tarea de EliNer API v1.
