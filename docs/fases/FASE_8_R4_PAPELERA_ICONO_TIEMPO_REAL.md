# FASE 8-R4 — Papelera de capas: el ícono del toolbar no se encendía en caliente

## Estado
Corregido. Bug real reportado por el cliente con capturas (elimina una
capa, el ícono de papelera con su badge no aparece), sobre el trabajo de
FASE_8_PAPELERA_DE_CAPAS.md y FASE_8_R3_PAPELERA_CRITERIO_BANNER.md.

## Síntoma reportado
Al eliminar una capa desde el diálogo "¿Eliminar esta capa?", el ícono de
papelera del toolbar (`EditorScreen.kt`, condicionado a
`state.trashedLayers.isNotEmpty()`) no aparecía. Capturas del cliente
muestran el lienzo con una capa menos y ningún ícono de papelera/badge
visible en la franja superior.

Esto contradice directamente lo documentado en
`FASE_8_R3_PAPELERA_CRITERIO_BANNER.md`, que daba por sentado — sin
haberlo verificado de punta a punta en esa fase — que "el ícono... vive
en `EditorScreen.kt`... refleja el estado en tiempo real... aparece
desde la primera capa que se elimina". Esta fase corrige tanto el bug
como esa afirmación.

## Causa raíz
`EditorViewModel.removeLayer` nunca tocó `trashedLayers` — por diseño
explícito (ver FASE_8_PAPELERA_DE_CAPAS.md, sección 5): solo saca la capa
del estado en memoria y dispara `scheduleAutosave()`. La decisión de que
esa capa pasa a la papelera la toma `ProjectStorage.saveProject`,
comparando el `project.json` anterior contra el guardado actual.

El eslabón que faltaba: **ese veredicto nunca volvía al `EditorViewModel`.**
`SaveProjectResult` (lo que `saveProject` le devuelve a
`EditorViewModel.persistNow`) solo cargaba `effectiveName`,
`resolvedLayerImageUris` y `resolvedAudioUri` — nada sobre la papelera. Y
la actualización de `_uiState` al final de `persistNow` (rama de éxito)
tampoco tocaba `trashedLayers`/`trashSizeBytes`.

Resultado: esos dos campos del `EditorUiState` solo se escribían en 4
puntos — `loadProject` (abrir el proyecto) y las 3 operaciones explícitas
de la papelera (`restoreLayersFromTrash`, `deleteLayersFromTrashPermanently`,
`emptyTrash`) — nunca como consecuencia de un autoguardado de rutina
disparado por `removeLayer`. El ícono solo podía "despertar" cerrando y
reabriendo el proyecto, nunca en medio de la sesión activa — exactamente
el síntoma reportado.

## Corrección

### `ProjectStorage.kt`
- `SaveProjectResult` gana un campo nuevo, `trashSummary: TrashSummary`
  — la papelera YA VIGENTE en el momento en que ese guardado termina.
- Retorno principal de `saveProject`: se calcula con
  `buildTrashSummary(imgDir, data)`, reutilizando el mismo camino que ya
  usa `trashInfo(projectId)` para refrescar la UI tras restaurar/eliminar/
  vaciar — sin una segunda vuelta a disco.
- Camino borde `layers.isEmpty()` (el guard que evita persistir un
  proyecto con 0 capas): no escribe nada, así que la papelera no cambió
  en este intento — se lee la que ya está en disco (bajo el mismo mutex
  del proyecto) en vez de devolver una vacía a ciegas, que hubiera hecho
  que ese autoguardado no-op le pisara el badge a 0 en la UI.

### `EditorViewModel.kt`
- `persistNow`, rama de éxito: la copia final de `_uiState` ahora incluye
  `trashedLayers = result.trashSummary.items` y
  `trashSizeBytes = result.trashSummary.totalSizeBytes`. Aplica tanto al
  autoguardado de rutina (`finalize = false`, disparado por
  `scheduleAutosave()` tras `removeLayer`) como al guardado final
  (`finalize = true`, `saveNow`).

Ningún otro archivo cambia. `removeLayer` sigue sin saber nada de la
papelera — el diseño de responsabilidad única de FASE 8 se mantiene
intacto; lo que se cierra es el eslabón que le impedía al resultado de
`saveProject` volver a alcanzar el `EditorUiState`.

## Por qué no es un parche
No se agregó una actualización optimista/paralela de `trashedLayers` en
`removeLayer` (lo que sí hubiera sido un parche: dos fuentes de verdad
que podrían desincronizarse — por ejemplo, si el autoguardado fallara o
detectara una capa distinta a la que el ViewModel cree que eliminó). La
única fuente de verdad de "qué hay en la papelera" sigue siendo
`ProjectStorage`, vía `project.json`; la corrección simplemente completa
el circuito para que ese veredicto real, ya calculado en cada guardado,
llegue de vuelta a la UI en el mismo ciclo que lo confirma.

## Verificación pendiente en dispositivo real
1. Con 3+ capas en el lienzo, eliminar una (no la última) → tras el
   debounce del autoguardado (`AUTOSAVE_DEBOUNCE_MS`), el ícono de
   papelera debe aparecer con badge "1", **sin cerrar el proyecto**.
2. Eliminar una segunda capa → el badge debe pasar a "2" en el mismo
   ciclo de autoguardado siguiente, en caliente.
3. Restaurar una capa desde el panel de papelera → el badge baja a "1"
   (comportamiento ya existente, no debería haberse alterado).
4. Cerrar el proyecto y reabrirlo con la papelera no vacía → sigue
   apareciendo el banner ámbar (criterio de FASE_8_R3, sin cambios) y el
   ícono sigue reflejando el conteo correcto desde `loadProject`.
5. Intentar eliminar la única capa restante → sigue bloqueado por el
   diálogo "No se puede eliminar la última capa" (sin cambios); en ese
   camino `saveProject` nunca corre, así que este fix no aplica ahí.
