# ADR-012 — Reactivación de "Audio": fila propia en la playlist de capas + 5 módulos flotantes de efectos (Volumen/Silencio/Recorte/Loop/Fade)

**Estado:** Decidido e implementado.
**Referencia:** Bloque comentado de 368 líneas en `EditorScreen.kt`
(líneas ~5521-5889 antes de este ADR) que envolvía las pestañas fijas
Cámara/Look cinematográfico/Audio/Tiempo de las primeras versiones de la
app — Cámara y Look ya habían migrado al sistema `FloatingToolWindow`
(Recolor/Color Básico/3D Básico/Zoom X); Audio y Tiempo nunca se
migraron. Pedido explícito del usuario (24 de septiembre de 2026) de
reactivar específicamente Audio — no Tiempo, que sigue comentado — como
fila real en el timeline (respetando el orden de carga) más sus
controles como módulos flotantes independientes, mismo lenguaje visual
que Recolor/3D/Zoom X.

## Contexto

Antes de este ADR, `state.audioClip` (`AudioClip`, `engine/audio/`) era
un dato de proyecto completamente invisible en el timeline: se importaba
vía `onImportAudioClick` (sin botón real que lo disparara — el diálogo
"+" solo ofrecía "Imagen"), y sus controles (`AudioPanel`, en
`AudioAndExportPanels.kt`: volumen, mute, recorte de inicio, loop,
fade-in/out) vivían en un panel de pestaña fija ya comentado junto con
Cámara/Look/Tiempo. La lógica de fondo (`onImportAudioClick` →
`pickAudioLauncher` en `MainActivity` → `viewModel.importAudio`, y los 5
setters `setAudioVolume`/`toggleAudioMute`/`setAudioTrimStart`/
`setAudioLoop`/`setAudioFade` en `EditorViewModel`) nunca se rompió — no
había ningún botón ni fila que la disparara.

Dos restricciones explícitas del usuario, cumplidas en la decisión de
abajo: (1) el audio NUNCA se modela como `Layer` — `Layer`
(`engine/scene/Layer.kt`) es específicamente de renderizado GPU
(bitmap/textura OpenGL/cámara/look), y el audio no se dibuja ni tiene
cámara; (2) nada de parches ni responsabilidades mezcladas — cada pieza
nueva en su propio archivo, sin tocar la matemática de arrastre-
reordenamiento vertical existente de las capas de imagen.

## Decisión

### 1. Orden real en la playlist (`AudioClip.trackOrder`)

`AudioClip` gana dos campos nuevos: `trackOrder: Float` (posición en la
playlist, mismo espacio de valores que `Layer.zIndex`, comparable pero
NUNCA usado para renderizado GPU) y `timelineStartMs: Long` (punto DEL
PROYECTO donde arranca a sonar — antes inexistente; el audio siempre
arrancaba en t=0 del proyecto sin excepción).

`EditorViewModel.importAudio` fija `trackOrder = layers.size` en el
primer import (mismo criterio que usa `LayerRepository.importAsLayers`
para capas nuevas — la más reciente obtiene el `zIndex` más alto, que
ordena descendente = aparece primero, justo debajo de "Master"). Al
reemplazar el archivo (mismo clip lógico, otro `Uri`), se conserva
`trackOrder` y `timelineStartMs`: cambiar de archivo no reordena ni
reposiciona la pista.

`TimelineView.kt` construye `renderTracks`: una lista mezclada de
`Layer` + (opcionalmente) el `AudioClip`, ordenada por ese mismo criterio
(`zIndex`/`trackOrder` descendente). La matemática de arrastre-
reordenamiento vertical (`draggingIndex`, offsets de arrastre) sigue
mirando exclusivamente `sortedLayers`, sin ningún cambio — es exclusiva
de capas de imagen; el audio no participa de ESE gesto.

### 2. Fila de audio con arrastre horizontal propio (`AudioTrackRow.kt`)

Archivo nuevo, responsabilidad única: pinta la fila de audio (ícono,
nombre de archivo, botón quitar) y expone un arrastre HORIZONTAL propio,
dentro de su propio carril, para fijar `timelineStartMs` — gesto
completamente independiente del arrastre vertical de reordenamiento de
capas.

### 3. Diálogo "+" con la opción "Audio" (`AddTrackDialog`, `EditorScreen.kt`)

Se agrega la fila "Audio" (ícono `ic_music_note`, mismo que el
reproductor flotante) junto a "Imagen" (que además pasa de usar el "+"
genérico a su ícono real, `ic_image_placeholder`). Dispara el
`onImportAudioClick` que ya existía como parámetro de `EditorScreen`, sin
tocar `MainActivity`.

### 4. Cinco módulos de efectos como ventanas flotantes independientes

A pedido explícito del usuario: cada control que tenía el viejo
`AudioPanel` pasa a ser SU PROPIO módulo flotante, categoría nueva
"Audio" en el cajón de Módulos (`ModulesDrawerPanel`, `EditorBottomBar.kt`,
oculta por completo mientras `audioClip == null`). Con 5 módulos
necesitando el mismo mecanismo "tocar la fila abre su ventana flotante"
a la vez, se generaliza con `AudioModuleId` (enum) + un solo
`onAudioModuleClick(AudioModuleId)` — el propio comentario histórico de
`onZoomXClick` ya anticipaba que ese sería el momento correcto de
generalizar, no antes.

Tres archivos nuevos, reutilizados entre los 5 módulos según su forma
real de control (no uno por módulo — eso hubiese sido 5 copias casi
idénticas del mismo chrome):

- `AudioSliderFloatingWindow.kt` — un control de rango. Comparten:
  **Volumen** (`0f..1.5f` → `setAudioVolume`) y **Recorte**
  (`0f..sourceDurationMs` → `setAudioTrimStart`).
- `AudioToggleFloatingWindow.kt` — un interruptor de dos estados.
  Comparten: **Silencio** (→ `toggleAudioMute`) y **Loop** (→
  `setAudioLoop`).
- `AudioFadeFloatingWindow.kt` — dedicado, el único módulo con DOS
  controles a la vez (fade-in + fade-out → `setAudioFade`), igual que ya
  se presentaban juntos en el viejo panel.

Los tres reutilizan `FloatingToolWindow` (mismo chrome que Recolor/Color
Básico/3D Básico/Zoom X) y se instancian en `EditorScreen.kt` con el
mismo patrón de offset escalonado / `zIndex` compartido que las 9
ventanas ya existentes (ahora 14 en total; `floatingWindowZOrderCounter`
arranca en `14f`, no `9f`). A diferencia de Zoom X (que integra con
`commitFrame`/keyframes de una capa), cada `onValueChange`/
`onCheckedChange` llama DIRECTO al setter real del ViewModel — el audio
es un dato único de proyecto, sin keyframes propios.

### 5. Persistencia real (no solo en memoria)

`trackOrder` y `timelineStartMs` se agregan a `AudioTrackData`
(`ProjectModels.kt`, con default `0f`/`0L` para que un proyecto guardado
ANTES de este ADR siga cargando sin romperse) y a `AudioSaveSnapshot`
(`ProjectStorage.kt`), con ambos mapeos reales (guardar y cargar)
actualizados.

### 6. Motor de mezclado y preview en vivo respetan `timelineStartMs`

`AudioProcessor.buildProjectSamples` ya no escribe el audio siempre desde
el frame 0 de salida: calcula `startFrame` a partir de
`timelineStartMs` y deja los frames previos en silencio real.
`applyVolumeAndFades` gana el parámetro `audibleStartFrame` (default `0`,
compatible con el único test existente, `AudioProcessorFadeTest`) para
que el fade-in/fade-out se calcule relativo al tramo audible real, no
desde el frame 0 absoluto.

`AudioPreviewPlayer.playFrom`/`seekToProjectTime` se corrigen para no
sonar antes de `timelineStartMs` y mapear la posición correctamente —
ver la limitación aceptada en "Fuera de alcance" abajo.

## Alcance del fix

Archivos nuevos: `AudioTrackRow.kt`, `AudioSliderFloatingWindow.kt`,
`AudioToggleFloatingWindow.kt`, `AudioFadeFloatingWindow.kt`,
`ic_volume.xml`, `ic_mute.xml`, `ic_trim.xml`, `ic_loop.xml`,
`ic_fade.xml`.

Archivos editados: `AudioClip.kt`, `AudioProcessor.kt`,
`AudioPreviewPlayer.kt`, `EditorViewModel.kt`, `EditorScreen.kt`,
`TimelineView.kt`, `EditorBottomBar.kt`, `ProjectModels.kt`,
`ProjectStorage.kt`.

No se tocó: `Layer.kt` (el audio nunca se modela como capa GPU),
`AudioAndExportPanels.kt` (el `AudioPanel` viejo queda intacto, comentado
junto con Cámara/Look/Tiempo — no se reactivó ese panel, se construyó el
reemplazo modular), ni la matemática de arrastre-reordenamiento vertical
de capas en `TimelineView.kt`.

## Fuera de alcance (gap aceptado, documentado a propósito)

**Arranque automático a mitad de reproducción en vivo.** El preview
(`AudioPreviewPlayer`) solo evalúa `timelineStartMs` al ARRANCAR la
reproducción (mismo criterio que ya usaba para trim/loop/mute). Si el
usuario aprieta Play antes de `timelineStartMs` y lo deja correr, el
audio no arranca solo al cruzar ese punto — hace falta atarlo al tick del
playhead, no implementado en este ADR. La EXPORTACIÓN real
(`AudioProcessor`) sí respeta `timelineStartMs` con precisión de sample
siempre; esto es solo una limitación del monitor de edición en vivo.

**Pestaña "Tiempo"** del bloque viejo comentado — sigue comentada, sin
ningún cambio. Solo "Audio" se reactivó en este ADR, a pedido explícito.

**Arrastre de la fila de audio entre capas de imagen** (reordenar
verticalmente audio respecto a imágenes después de cargado) — no
implementado. El orden de audio se fija una sola vez, al importar
(`trackOrder = layers.size` en ese momento), no es arrastrable como sí lo
son las capas entre sí.

## Correcciones posteriores (auditoría del 24 de septiembre de 2026)

A pedido explícito del usuario, se auditó todo lo implementado en este
ADR buscando bugs, inconsistencias y código basura. Se encontraron y
corrigieron 3 problemas reales, quirúrgicamente, sin ampliar el alcance:

1. **`TimelineView.kt` — `contentHeight` no contaba la fila de audio.**
   La línea separadora y el área de arrastre del playhead se calculaban
   con `sortedLayers.size` únicamente, ignorando la fila de audio. Con
   audio cargado y pocas o ninguna capa de imagen, ambas quedaban cortas,
   sin cubrir la fila de audio. Corregido: `totalTrackRowCount =
   sortedLayers.size + (si hay audioClip, 1)`.
2. **`AudioTrackRow.kt` — el valor confirmado al soltar el arrastre no
   coincidía con el límite visual.** `onDragEnd` recalculaba la posición
   desde el acumulado crudo del gesto (`baseStartPx + dragOffsetPx`), sin
   el límite superior que sí aplicaba `displayStartPx` para dibujar el
   bloque. Si el dedo seguía moviéndose después de que el bloque ya había
   tocado su límite visual, se confirmaba una posición distinta a la que
   el usuario vio. Corregido: el commit ahora deriva de `displayStartPx`
   (la misma coordenada, ya clampeada) en vez de recalcular sin límite.
3. **`AudioProcessor.kt` — mensaje de error engañoso.** Si
   `timelineStartMs` quedaba en o después del final del proyecto
   (posible si la duración del proyecto se acorta DESPUÉS de haber
   posicionado el audio cerca del final — `setAudioTimelineStart` solo
   clampea contra la duración vigente en el momento del arrastre), el
   pipeline reportaba "No se pudo armar el audio para la duración del
   proyecto (trim/loop mal calculado)" — técnicamente falso, el trim y
   el loop no tienen nada que ver. Corregido: se trata como el caso
   `clip.muted` (sin audio, sin mensaje de error, exportación normal sin
   audio). De paso, se ajustó el rango visual del slider de "Recorte"
   (`EditorScreen.kt`) para que coincida exacto con el límite real de
   `setAudioTrimStart` (`sourceDurationMs - 100L`), misma clase de
   inconsistencia que el punto 2.

Verificación de la auditoría: diff de contenido completo contra la
versión anterior de este ADR (solo estos 3 archivos cambiaron, ningún
archivo nuevo, ningún archivo fuera de este ADR tocado) + balance de
llaves en los 6 archivos más grandes tocados por este ADR (limpio en los
6) + balance de paréntesis comparado línea a línea contra el
`AudioProcessorFadeTest.kt` existente (compatible, `audibleStartFrame`
por defecto en `0` reproduce el comportamiento anterior exacto).

## Verificación pendiente en dispositivo real

Mismo estado que ADR-007 a ADR-011: cambio hecho sobre el código fuente
sin Android SDK/Gradle disponible en esta sesión (revisión manual línea
por línea + diff de contenido completo contra el proyecto original, sin
build real). Antes de cerrar:

1. Compilar (`./gradlew assembleDebug`) y correr `AudioProcessorFadeTest`
   — confirmar que el parámetro nuevo `audibleStartFrame` (default `0`)
   no le cambia el resultado a ningún caso existente.
2. Tocar "+" → "Audio", elegir un archivo real, confirmar que aparece
   como fila nueva en el timeline en la posición esperada (respetando el
   orden real de capas ya cargadas).
3. Arrastrar la fila de audio a la derecha, confirmar que el bloque de
   color se mueve y que al exportar el audio efectivamente arranca
   desplazado ese tiempo (no en t=0).
4. Abrir cada uno de los 5 módulos (Volumen/Silencio/Recorte/Loop/Fade)
   desde el cajón de Módulos → categoría "Audio", confirmar que cada
   control refleja y modifica el estado real (recargar el proyecto y
   confirmar que los 5 valores + `trackOrder`/`timelineStartMs`
   sobrevivieron guardado/carga).
5. Confirmar que un proyecto guardado ANTES de este ADR (sin
   `trackOrder`/`timelineStartMs` en su JSON) sigue cargando sin
   crashear, con el audio apareciendo al tope de la playlist
   (`trackOrder` default `0f`) arrancando en t=0
   (`timelineStartMs` default `0L`).
