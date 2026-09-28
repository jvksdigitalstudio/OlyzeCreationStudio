# ADR-004 — La frontera de EliNer se define por RESULTADO, no por clase implementadora

**Estado:** Decidido (Fase C.1). Principio rector — sin acción de código
hasta el diseño de EliNer API v1.

## Contexto
Hoy `GLRenderer`/`MediaCodec`/`Mesh3D`/`EGL` son detalles internos ya
razonablemente encapsulados en algunos casos: `PixelColorSource` es el
precedente exitoso de exponer un *resultado* (un color de píxel) sin
exponer `GLRenderer` mismo — `EditorScreen` conoce la interfaz, nunca la
implementación concreta (`GLPreview` es el único wrapper que la conoce).

## Problema
Decidir qué expone EliNer API sin caer en "una función por cada clase
actual del Engine" (lo que expondría GLES/MediaCodec/Mesh3D en la
superficie pública).

## Opciones consideradas
- **A) Exponer 1:1 cada clase de `engine/*`** como parte de la API.
  Mecánico y rápido, pero expondría implementaciones internas de
  Android/GLES/MediaCodec en el contrato público — descartada.
- **B) Definir la frontera por dominio y por resultado** — Scene,
  Camera, Animation, Timeline, Render, Audio, Effects, Mesh3D, Export
  exponen lo que PRODUCEN, nunca las clases internas que lo producen.
- **C) Exponer solo un subconjunto mínimo** y agregar el resto
  reactivamente, sin un principio guía.

## Decisión
**Opción B.**

## Mapa (dentro / fuera de EliNer)

**Dentro de EliNer** (por resultado, no por clase): Scene/Layers,
Camera, Animation, Timeline, Render (el frame renderizado, no
`GLRenderer`), Audio (control/resultado, no `MediaCodec` crudo),
Effects, Mesh3D (el resultado, no las clases de malla), Export
(progreso/resultado, no `EGL`/`MediaMuxer` crudo).

**Fuera de EliNer**: `ProjectStorage` (persistencia de proyecto — la
app llama a EliNer para obtener/aplicar datos, no al revés),
`LayerRepository` (gestión de archivos importados),
`platform/DisplayRefreshRate` (capacidad de dispositivo), toda la UI,
navegación, ViewModels (consumidores de EliNer, no parte de ella).

## Justificación
Es la generalización directa de un patrón que ya funciona en el
proyecto (`PixelColorSource`) — no es una idea nueva, es aplicar lo que
ya se demostró correcto una vez a los demás dominios.

## Consecuencias
Ninguna acción de código en esta fase — se aplica al diseñar EliNer
API v1 como criterio de cada función pública: "¿esto expone un
resultado o una implementación interna?".

## Impacto futuro en EliNer
Define el criterio de diseño de cada función pública de la API.

## Impacto futuro en C++
Al no exponer clases concretas de Android/GLES/MediaCodec en la
superficie pública, migrar la implementación interna a C++ no rompe el
contrato público — el objetivo central de toda esta preparación.
