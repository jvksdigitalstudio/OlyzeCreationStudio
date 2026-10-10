# ADR — Varias capas (carriles) de audio

**Fecha:** 2026-10-09 · **Estado:** Aplicado · **Módulos:** `engine/audio`, `data`, `viewmodel`, `ui`, `api`

## Decisión
El proyecto admite **N carriles de audio**; cada carril sostiene N clips. "+" → "Audio" crea un carril nuevo; los clips
de una capa existente se cargan desde su carril (`ADR-AUDIO-CLIP-EN-CARRIL.md`).

## Modelo
- `AudioTrack.id` (UUID). `EditorUiState.audioTracks: List<AudioTrack>`; un carril sin clips no existe.
- Reglas puras en `engine/audio/AudioTrackList.kt` (búsqueda por id/clip, `withTrack`, `endMs`, `withUniqueIds`,
  `nextAudioTrackOrder`, `moduleTargetClip` multicarril) y `AudioTrackColor.kt` (color/degradado/restablecer, compartido
  por el cambio de un carril y "Multicolor").
- Selección de tiempo: `AudioTrackTimeSelection(trackId, range)` (una sola, en un carril).
- Los ids de carril comparten espacio con `Layer.id` (reordenar, expandir, Multicolor): ya no hay sentinela.

## Guardado y migración
- `ProjectData.audioTracks` es la forma vigente; `AudioTrackData.id` se escribe siempre.
- `ProjectData.audioTrack` queda **solo lectura**: un proyecto anterior se abre como lista de un carril con id
  `LEGACY_AUDIO_TRACK_ID`; el primer guardado lo reescribe en `audioTracks` y deja `audioTrack = null`.
- Los archivos de audio se guardan por clip (`clip.id`), así que no cambió su layout en disco; el fallback de copia previa
  y la limpieza de archivos huérfanos recorren todos los carriles.

## Reproducción y exportación
- Preview: `AudioPreviewPlayers` = un `AudioPreviewPlayer` por carril → los carriles suenan a la vez; libera los de carriles
  borrados.
- Export: `AudioProcessor.buildEncodedTrackForProject` mezcla todos los clips de todos los carriles (los carriles son
  organización; no cambian lo que se oye).

## EliNer API
Contrato sin cambios: ve **un** audio, el del carril principal (el primero). `getAudioClip` = primer clip del primer
carril; `setAudioClip`/`clearAudioClip` operan sobre ese carril. `getAudioTracks()` (interno, export) expone todos.
Ampliar el contrato público a multi-carril es una decisión aparte.

## Pruebas
`AudioTrackListTest`, `AudioTrackColorTest`, `ProjectDataSerializationTest` (incluye JSON anterior con `audioTrack`).
No cubiertas en JVM puro (requieren `Uri`/Android): migración en `loadProject`, `AudioPreviewPlayers`, flujos de UI.
