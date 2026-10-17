# FASE — Orden único de la playlist: todo lo nuevo entra como última fila

## Bug reportado
Al agregar elementos, unos caían arriba (imágenes) y otros abajo (audio).

## Causa raíz (dos reglas distintas)
- Imágenes: `zIndex = layers.size` (un conteo, no el máximo real) con lista DESCENDENTE → arriba;
  además con huecos por capas borradas podía caer ENTRE capas existentes.
- Audio: `nextAudioTrackOrder = min - 1` → abajo.

## Solución
- `engine/timeline/PlaylistOrder.kt`: ÚNICA regla (`nextTopOrder`, `nextLayerStartingZIndex`):
  todo elemento nuevo = valor mayor que todo lo existente.
- La lista se muestra en orden ASCENDENTE de zIndex/trackOrder: última fila = lo más nuevo
  = lo más al frente del canvas (el motor ya pintaba ascendente) → lo recién agregado siempre se ve.
- Sin migración: no cambia el render ni los datos guardados, solo el orden de la lista.
- Aplicado en: `importImages`, `LayerApiImpl.createLayers`, restauración desde papelera,
  `nextAudioTrackOrder`, `reorderTrack` (reasigna 0..N-1 ascendente), Multicolor (degradado
  de arriba a abajo), `TimelineView` (`sortedBy`). Hit-test de canvas sigue frente→fondo.
- Fondo (`importAsBackground`): sigue con valor menor que todo → primera fila, al fondo.
- Futuro (texto, modelos 3D, video): usar `nextTopOrder`; nunca calcular su propio orden.

## Corrección de regresión detectada en revisión
`setLayerZIndex` (EliNer API) convertía zIndex→pasos con la dirección vieja (`actual - deseado`) y
asumía zIndex consecutivos; con el orden ascendente movía al revés. Ahora usa
`stepsToAbsoluteOrder` sobre la misma lista (`playlistIdsInOrder`) que `reorderTrack`.
El criterio de orden vive SOLO en `PlaylistOrder.kt` (`playlistOrder`, `playlistIdsInOrder`);
UI, ViewModel y API lo reutilizan.

## Tests
`PlaylistOrderTest` (nuevo), `AudioTrackListTest` (actualizado a la regla nueva).

## Verificar en dispositivo (no se pudo compilar aquí)
Importar imagen, audio, otra imagen, audio: cada uno debe quedar como última fila y verse al frente.
Arrastrar para reordenar: la fila soltada debe conservar su posición. Proyectos viejos: misma imagen
en canvas, lista invertida respecto a antes.
