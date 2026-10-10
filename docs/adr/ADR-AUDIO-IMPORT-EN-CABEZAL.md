# ADR — Importar audio en el cabezal (pausar ≠ rebobinar)

**Fecha:** 2026-10-09 · **Estado:** Aplicado · **Módulos:** `MainActivity`, `EditorViewModel`, `engine/timeline`

## Síntoma
"+" → Audio cargaba siempre el clip al inicio del carril, ignorando dónde estaba el cabezal.

## Causa raíz
`importAudio` ya calculaba la posición con `playheadMs` + `firstFreeStartMs`. El problema era previo:
abrir el selector de archivos del sistema lleva la Activity a `ON_STOP`; el observador de `MainActivity`
llamaba a `resetPlaybackState()`, que además de frenar **rebobinaba `playheadMs = 0`**. Al volver el archivo,
`importAudio` leía 0.

## Solución
- `engine/timeline/PlaybackTransitions.kt`: transiciones puras `paused()` (conserva cabezal) y `rewound()`.
- `EditorViewModel`: nueva `pausePlayback()` (pausa también el preview de audio, porque con la Activity detenida Compose suspende la recomposición); `pausePlayback` y `resetPlaybackState` comparten
  `applyPlaybackTransition` (un solo punto de escritura). `pausePlayback` también pausa el preview de audio.
- `MainActivity` `ON_STOP`: `pausePlayback()` + `saveNow()`. Rebobinar queda solo para entrar/salir del editor.
- Tests: `PlaybackTransitionsTest`.

## Alcance / efectos
Mismo beneficio para importar imágenes y fondo (también usan selector). Sin cambios de formato de proyecto.
Fuera de alcance: `AudioApiImpl.setAudioClip` (EliNer API) conserva su contrato de "reemplazar carril".

## Endurecimiento de `importAudio` (revisión posterior)
- (Superado por `ADR-AUDIO-CLIP-EN-CARRIL.md`) El punto de inicio ya no se lee dentro de `importAudio`: lo decide
  la UI al abrir el selector y llega como `desiredStartMs`.
- `queryDisplayName` captura fallos del proveedor (antes podía abortar la corrutina con `isImportingAudio` encendido).
- Corregido comentario obsoleto de `setAudioClipDirect` (contrato EliNer: reemplaza el carril; `importAudio` agrega).

## Límite conocido
Si Android mata el proceso mientras el selector está abierto, el callback `onAudioPicked` (campo de la Activity)
y el cabezal (no persistido) se pierden. `configChanges` + `portrait` evitan la recreación por rotación.
