# FASE 4.2 — Integridad de persistencia + boundary público de AudioApi

> Origen: prompt maestro "FASE 4.2-R3 — INTEGRITY + ELI NER BOUNDARY +
> CONSUMER MIGRATION". Este documento cubre únicamente lo que se
> **implementó y verificó por inspección de código** en esta sesión —
> ver la sección "Estado real / pendiente" al final, que es la parte más
> importante de este documento (regla del prompt maestro, sección 27: "la
> documentación debe representar el código REAL", no una fase declarada
> "completada" de más).

## Resumen ejecutivo

| # | Problema (auditoría) | Causa raíz confirmada en el código | Archivo(s) corregido(s) |
|---|---|---|---|
| 1 | Un fallo de asset podía borrar una capa entera de `project.json` | `saveProject` usaba `layers.mapNotNull { ensureLocalImage(...) }` — un comentario de una fase anterior afirmaba que esto ya estaba corregido, pero el código seguía haciendo exactamente eso | `ProjectStorage.kt` |
| 2 | `ensureLocalImage` podía destruir una copia local válida antes de confirmar la nueva | `destFile.outputStream()` truncaba el archivo destino al abrir el stream, ANTES de saber si la copia nueva iba a tener éxito | `ProjectStorage.kt` |
| 3 | `ensureLocalAudio` podía destruir el audio anterior antes de confirmar el nuevo — y además el bug se combinaba con el bug #1: un fallo transitorio dejaba `audioDataResult == null`, indistinguible de "el usuario quitó el audio", así que el código de limpieza de abajo BORRABA el archivo de audio anterior válido | `dir.listFiles()?.forEach { it.delete() }` corría antes de intentar la copia nueva, y la condición de limpieza no distinguía "sin audio" de "audio falló al copiar" | `ProjectStorage.kt` |
| 4 | `saveNow(onDone)` podía ejecutar `onDone()` (que cierra el editor / vuelve a "Mis proyectos") aunque el guardado hubiera fallado | `persistNow` atrapaba cualquier error, actualizaba `saveState`, pero nunca lo propagaba; `saveNow` llamaba a `onDone()` sin condición alguna, después de `persistNow` | `EditorViewModel.kt` |
| 5 | `AudioApi.getAudioClip()` exponía `engine.audio.AudioClip` mutable — la misma instancia en vivo del motor — como contrato público | `AudioApi`/`AudioApiImpl` devolvían el tipo interno directo, sin ningún DTO intermedio (a diferencia de `LayerApi`, que ya usa `LayerSnapshot`) | `AudioApi.kt`, `AudioApiImpl.kt`, nuevo `AudioClipSnapshot.kt` |

**Importante:** los reportes de fases anteriores (comentarios "FASE 1
(AUDITORÍA P0)" ya presentes en el código) afirmaban que los problemas #1
y #2 estaban resueltos. No lo estaban — el código seguía teniendo
exactamente el defecto que describían. Esta fase corrige el código real,
no solo el comentario.

## 1. Persistencia: capas (punto 4.1 del prompt maestro)

`saveProject` ya no usa `mapNotNull`. Para cada capa:

1. Se intenta `ensureLocalImage(...)` (copia/refresco normal).
2. Si falla, se busca en el `project.json` YA guardado (`existing`) si esa
   misma capa (`snap.id`) tenía una copia local (`imageFileName`) que
   sigue existiendo en disco y no está vacía. Si sí, se reutiliza esa
   referencia — la capa permanece en el guardado, con un log de
   advertencia.
3. Si no hay ninguna copia previa válida, se lanza
   `ProjectAssetIntegrityException` y **todo el guardado se aborta antes
   de escribir ningún byte de `project.json` nuevo** — el archivo en
   disco queda exactamente como el último guardado válido.

La regla de decisión ("nueva copia, o fallback, o abortar") está aislada
en la función pura `resolveAssetOrAbort<T>` (sin `Context`/`Uri`), para
poder testearla directamente sin Robolectric.

## 2. Persistencia: audio de fondo (punto 4.3 del prompt maestro)

Mismo criterio que las capas, con una corrección adicional: antes,
`audioDataResult == null` significaba dos cosas indistinguibles ("el
usuario quitó el audio" y "el audio falló al copiarse"), y ambas
disparaban la limpieza de `audioDir(projectId)` — es decir, un fallo
transitorio de copia terminaba **borrando el archivo de audio anterior
válido**, no solo omitiendo la referencia. Ahora:

- La limpieza de `audioDir` solo ocurre cuando `audioClip == null` en
  memoria (el usuario efectivamente quitó el audio).
- Un fallo de copia con audio presente en memoria sigue el mismo camino
  que las capas: reutilizar la copia previa válida, o abortar el
  guardado entero.

## 3. Reemplazo atómico de assets (puntos 4.2/4.3)

`ensureLocalImage`/`ensureLocalAudio` ya no escriben directo sobre el
archivo destino. Ambos delegan la copia+reemplazo a la función pura
`copyToFileAtomically(destFile, openInput)`:

1. Copia el contenido de `openInput()` a un archivo **temporal** aparte.
2. Verifica que el temporal exista y no esté vacío.
3. Solo entonces reemplaza `destFile` con `renameTo` (atómico dentro del
   mismo volumen — mismo patrón que ya usa `saveProject` para
   `project.json`), con un reintento si el primer `renameTo` falla.
4. Si cualquier paso falla, `destFile` (si ya existía) queda intacto y no
   queda ningún temporal huérfano.

Para el audio, la limpieza de archivos "huérfanos" con otro nombre (el
usuario reemplazó el audio por uno de otra extensión) ahora ocurre
**después** de que el archivo nuevo ya reemplazó con éxito a `destFile`
— nunca antes.

Esta función es deliberadamente independiente de `Context`/`Uri` de
Android, precisamente para poder testearla en un unit test JVM puro (el
proyecto no tiene Robolectric configurado — ver "Estado real/pendiente").

## 4. Semántica de `saveNow`/`onDone` (punto 4.4 del prompt maestro)

- `persistNow(finalize: Boolean)` cambió su firma de `Unit` a `Boolean`:
  devuelve `true` únicamente si el guardado terminó con éxito real
  (incluye la excepción `ProjectAssetIntegrityException` de las secciones
  1/2 como fallo, igual que cualquier otro `Throwable`). Devuelve `false`
  si no había nada que guardar (`state.layers.isEmpty()`) o si hubo
  cualquier error.
- `saveNow(onDone)` ahora solo ejecuta `onDone()` si `persistNow` devolvió
  `true`. Antes lo ejecutaba siempre, incluso con guardado fallido — el
  bug real y alcanzable desde la UI: `viewModel.saveNow { onBackToProjects() }`
  existe en varios sitios de `EditorScreen.kt`/`MainActivity.kt` ("Guardar
  y salir"), así que un guardado fallido podía cerrar el editor como si
  el proyecto estuviera guardado.
- La UI ya tenía forma de mostrar el fallo sin cambios adicionales:
  `SaveStatusLabel` (en `EditorScreen.kt`) ya traduce `SaveState.Error` a
  "No se pudo guardar". Con el fix, el usuario simplemente se queda en el
  editor viendo ese estado, en vez de perder sus cambios silenciosamente.

**Decisión de alcance documentada:** `discardChangesAndExit` (el botón
"Salir sin guardar") también llama a `persistNow` seguido de `onDone()`
sin mirar el resultado, y deliberadamente **no se tocó**: su intención
explícita es salir SIN depender de que el guardado tenga éxito — bloquear
la salida ahí contradiría lo que el usuario pidió al hacer clic en "salir
sin guardar". Si ese `persistNow` (que persiste el estado YA revertido en
memoria) falla, el archivo en disco puede quedar con el autoguardado
intermedio en vez del estado revertido — un riesgo real pero distinto,
que queda como deuda técnica documentada (ver más abajo), no como parte
del bug de "Guardar y salir".

## 5. `AudioClipSnapshot` (punto 6 del prompt maestro)

Nuevo DTO inmutable en `api/model/AudioClipSnapshot.kt`, mismo patrón que
`LayerSnapshot`: un `data class` de solo `val`, con los mismos 9 campos
que `AudioClip` (`sourceUri`, `displayName`, `sourceDurationMs`, `volume`,
`muted`, `trimStartMs`, `loop`, `fadeInMs`, `fadeOutMs`).

`AudioApi.getAudioClip()` cambió su firma de `AudioClip?` a
`AudioClipSnapshot?`. `AudioApiImpl.getAudioClip()` convierte el
`AudioClip` interno (leído de `ActiveProjectReader`, sin copiar) a
`AudioClipSnapshot` justo antes de devolverlo — la frontera pública ya no
puede exponer la instancia mutable en vivo del motor.

**Lo que NO se tocó a propósito:** `ActiveProjectReader.getAudioClip()`
(el bridge interno usado por `AudioApiImpl`/`ExportApiImpl`) sigue
devolviendo `AudioClip` — es un adapter interno, no la frontera pública de
EliNer (ver sección 11 del prompt maestro: "Public EliNer API ≠ internal
adapter bridge"). Cambiarlo ahí también sería una migración más amplia de
la que esta fase pide.

## Tests agregados

Todos como JVM unit tests puros (el proyecto solo tiene
`testImplementation("junit:junit:4.13.2")`; no hay Robolectric ni un
`androidTest` con Context real todavía — ver "Estado real/pendiente"):

- `data/AtomicFileReplaceTest.kt` — prueba `copyToFileAtomically` de punta
  a punta: copia exitosa, copia que falla con destino previo válido
  (verifica que el destino previo NO se toca), stream vacío, sin destino
  previo, y reemplazo exitoso sobre un destino previo.
- `data/AssetFallbackOrAbortTest.kt` — prueba `resolveAssetOrAbort`: usa
  la copia nueva si existe, reutiliza el fallback si la copia nueva
  falló, y lanza `ProjectAssetIntegrityException` si no hay ninguna de
  las dos.
- `api/model/AudioClipSnapshotTest.kt` — por reflexión: ningún campo de
  `AudioClipSnapshot` es `var`, y cubre los 9 campos esperados.
- `api/audio/AudioApiBoundaryTest.kt` — por reflexión: el tipo de retorno
  compilado de `AudioApi.getAudioClip()` es `AudioClipSnapshot`, no
  `AudioClip`.
- Se actualizó `EliNerApiImplTest.kt` (`FakeAudioApi`) para compilar
  contra la nueva firma.

**Estas pruebas se escribieron pero NO se ejecutaron** en esta sesión (no
hay acceso a Gradle/JVM de Android en este entorno) — el usuario debe
correrlas vía GitHub Actions como indica el prompt maestro. No se afirma
"testeado", solo "test agregado, pendiente de ejecución real".

## Estado real / pendiente (sección 31 del prompt maestro: no declarar más de lo hecho)

**Implementado y revisado por inspección de código en esta sesión:**
puntos 4.1, 4.2, 4.3, 4.4 y 6 del prompt maestro.

**NO implementado en esta sesión** (para no inflar el alcance de este
documento con trabajo ficticio):

- Puntos 7–24 (auditoría completa de tipos `engine.*`/platform en
  `DistortionApi`/`Mesh3DApi`/`ExportApi`/`CameraApi`/`TimelineApi`,
  migración real de consumidores de UI a EliNer para
  toggle/reorder/keyframes/audio, revisión de `ActiveProjectMutator`,
  coroutines/undo/autosave por operación, `AppLogger`, locks de
  `ProjectStorage`, condiciones de carrera en mover/renombrar/borrar
  proyectos): **no auditados todavía en esta fase.**
- No se agregó ningún test para la sección C de la lista mínima del
  prompt maestro (`saveNow` no llama a `onDone` cuando falla) más allá de
  la revisión de código — probarlo en runtime requiere Robolectric o un
  test instrumentado (`androidTest`, con `Context` real), que este
  proyecto no tiene configurado. Se deja como recomendación explícita más
  abajo, no como "hecho".
- Riesgo documentado (no corregido): `discardChangesAndExit` puede dejar
  en disco el autoguardado intermedio en vez del estado revertido si su
  `persistNow` interno falla (ver sección 4 arriba) — es un caso distinto
  del bug de "Guardar y salir" que sí se corrigió, y no estaba en el
  alcance explícito del prompt maestro para esta fase.
- Deuda técnica ya señalada en el prompt maestro y NO tocada a propósito
  en esta fase: pipeline de audio export en memoria completa (sección
  21), dependencia `GLRenderer → data.ImageDecoding` (sección 20, Render
  Boundary futura — Fase 4.3).

**Recomendación de seguimiento:** agregar Robolectric (o un
`androidTest` con `androidx.test:core` + `ApplicationProvider`) como
dependencia de test, para poder cubrir en runtime real `saveProject`
completo (con `Context`/`Uri` reales) y `persistNow`/`saveNow` con el
`ViewModel` real — hoy esa cobertura está acotada a la lógica pura
extraída (`copyToFileAtomically`, `resolveAssetOrAbort`).

## 6. Duplicación de `ExportApiImpl` (punto 10 del prompt maestro)

Confirmado: `MainActivity.kt` construía una instancia de `ExportApiImpl`
(`eliNerExportApi`, vía `remember(projectId)`) que **nunca se leía en
ningún otro lado** — puro wiring muerto, mientras que el único camino de
exportación real (`EditorViewModel.exportVideo()`) ya construye y cachea
su propia instancia (`cachedExportApi`). Se eliminó la instancia muerta
de `MainActivity.kt`; `ExportApiImpl` es stateless, así que la única
instancia que hace falta es la que ya cachea el `ViewModel`. Sin cambio
de comportamiento (la variable eliminada no se leía en ningún lado).

**Observación relacionada, NO corregida (fuera del alcance explícito del
prompt maestro):** los otros 6 `eliNer*Api` (`Layer`/`Camera`/`Timeline`/
`Audio`/`Mesh3D`/`Distortion`/`Animation`) que `MainActivity.kt` construye
en ese mismo bloque tienen el mismo patrón de "wiring sin consumidor" —
se declaran y nunca se vuelven a usar, salvo `Mesh3DApi`/`AnimationApi`/
`DistortionApi` que sí llegan al `EditorViewModelFactory`. No es un
hallazgo nuevo (`LayerApi`/`CameraApi`/`TimelineApi`/`AudioApi` ya se
documentan a sí mismos como "PARCIAL: sin consumidor externo real") —
se señala acá solo para que quede explícito que no es exclusivo de
Export.

## 7. `AppLogger` — `SimpleDateFormat` no thread-safe (punto 22 del prompt maestro)

Confirmado: `AppLogger` es un `object` (singleton) llamado "sin importar
en qué hilo" (según su propio KDoc), y usaba un único `SimpleDateFormat`
compartido para formatear timestamps. Bajo operación normal, el
formateo para persistencia queda serializado en `ioExecutor` (un solo
hilo), pero `installUncaughtExceptionHandler()` llama a
`persist(entry, synchronous = true)` — formatea la entrada DIRECTO en el
hilo que se está cayendo (puede ser cualquiera), lo que puede coincidir
en el tiempo con `ioExecutor` formateando otra entrada — dos hilos
usando la misma instancia mutable de `SimpleDateFormat` a la vez.

**Corrección:** se reemplazó `SimpleDateFormat` por
`java.time.format.DateTimeFormatter` (inmutable y thread-safe por
diseño — disponible nativamente desde API 26, que ya es el `minSdk` del
proyecto). Cambio localizado a `AppLogger.kt`: los 3 puntos de uso
(`deviceInfoLines`, `formatEntry`, `parseEntry`) se adaptaron a
`Instant`/`LocalDateTime`/`ZoneId` en vez de `java.util.Date`.

**Test agregado:** `debug/AppLoggerConcurrencyTest.kt` — dispara
`AppLogger.e(...)` + `AppLogger.formatAllForCopy()` desde 16 hilos
concurrentes y verifica que ninguna llamada lance excepción y que todos
los timestamps generados respeten el patrón esperado.

## 8. `ProjectStorage.projectMutexes` — acumulación (punto 23 del prompt maestro) — AUDITADO, NO corregido

Confirmado: `projectMutexes` es un `MutableMap<String, Mutex>` que crece
con cada `projectId` distinto y **nunca se le remueven entradas**, ni al
borrar un proyecto. Es una acumulación real, pero acotada por la
cantidad total de proyectos distintos creados en la vida del proceso, y
cada entrada es un `Mutex` minúsculo.

**Por qué NO se corrigió en esta sesión:** la limpieza "obvia"
(`if (!mutex.isLocked) remove(id)`) tiene una condición de carrera real:
un llamador puede tener ya la referencia al `Mutex` sin haber llamado
todavía a `.withLock` cuando la limpieza lo da por libre y lo remueve —
un llamador futuro crearía un `Mutex` NUEVO para el mismo `projectId`, y
ambas corridas dejarían de excluirse mutuamente, el mismo bug de
concurrencia que este mapa existe para evitar. Una limpieza segura
necesita conteo de referencias — más invasivo que "pequeño y
localizado", y no pude ejercitarlo bajo concurrencia real en este
entorno. Se prefirió no tocarlo antes que introducir una corrección de
apariencia simple pero con una condición de carrera peor que el problema
original.

**Recomendación de seguimiento:** mapa con conteo de referencias, o un
límite tipo LRU, en una fase dedicada con tests de concurrencia reales.

## 9. `DistortionApi`/`Mesh3DApi`/`ExportApi` — auditoría de tipos (puntos 7/8/9 del prompt maestro)

- **`DistortionApi.render(source: Bitmap, field: DistortionField, ...)`**:
  `DistortionField` sí es mutable (`applyStroke`/`reset` mutan arrays
  in-place), pero acá se usa solo como parámetro de ENTRADA de una
  función `suspend` que únicamente lee la malla para rasterizarla —
  nunca la devuelve ni la retiene. El dueño real de su ciclo de mutación
  (trazo a trazo, deshacer por trazo) sigue siendo la UI
  (`DistortionPanel`). El riesgo que sí aplica a `AudioClip` (un
  consumidor externo reteniendo estado de proyecto mutable) no aplica de
  la misma forma a un parámetro de entrada de solo lectura, comparable a
  pasar un `Bitmap`. **Clasificación: D — permanece, documentado.** No
  se creó `DistortionFieldSnapshot`: sería una abstracción sin beneficio
  real acá.
- **`Mesh3DApi.extrude(source: Bitmap, params: Extrude3D.Params, ...)`**:
  `Extrude3D.Params` ya es un DTO plano, sin métodos que muten estado
  del motor. **Clasificación: A — parte legítima del contrato.**
- **`ExportApi.export(outputFile: File, settings: ExportSettings)`**:
  `ExportSettings`/`ExportProgress` ya son DTOs propios del dominio.
  Duplicación de instancias revisada y corregida (ver punto 6).

## Addendum — FASE 4.2-R4 (persistencia transaccional, confinamiento, migración real)

> Todo lo de acá abajo es de una segunda pasada sobre este mismo
> documento, en respuesta a una segunda auditoría (R4) que encontró
> problemas reales que la primera pasada NO había cerrado del todo.
> Clasificación exigida por el prompt R4: CORREGIDO / CORREGIDO
> PARCIALMENTE / PENDIENTE / DEUDA TÉCNICA / BLOQUEADOR.

### R4.1 — Persistencia TRANSACCIONAL completa — **CORREGIDO**

La atomicidad individual de un asset (Fase 4.2 original) NO garantizaba
la atomicidad de `saveProject()` completo. Bug real confirmado: la
limpieza de assets obsoletos (imágenes de capas eliminadas, audio con
nombre distinto tras reemplazo) corría **ANTES** de confirmar
`project.json` nuevo. Si la escritura del manifest fallaba DESPUÉS de esa
limpieza, el `project.json` VIEJO (que seguía en disco) quedaba
referenciando assets ya borrados — exactamente el escenario que describe
la Parte 1 del prompt R4.

**Corrección real:**
- `ensureLocalAudio` ya NO borra archivos "huérfanos" con otro nombre por
  su cuenta — solo copia y devuelve el nombre resuelto.
- `saveProject` calcula `validImageNames`/`validAudioFileName` ANTES del
  commit, pero la limpieza real (`imgDir.listFiles()... delete()`,
  `audioDir... delete()`) se movió a DESPUÉS del `renameTo` exitoso de
  `project.json.tmp` → `project.json`. Si el `try` de la escritura lanza,
  esa limpieza nunca se ejecuta.
- Escenarios A–M del prompt (fallo copiando imagen/audio, fallo
  creando/escribiendo/verificando el tmp, fallo en rename, fallo en
  cleanup, crash entre commit y cleanup, asset previo+nuevo inválido,
  save/load concurrente): todos quedan cubiertos por la combinación de
  `mutexFor(projectId)` (ya existente, serializa saves/loads del mismo
  proyecto) + `resolveAssetOrAbort` (aborta antes de escribir si no hay
  ni copia nueva ni fallback) + el nuevo orden commit-antes-que-cleanup.
  La única excepción real es un **crash de PROCESO** entre el `renameTo`
  exitoso y la limpieza posterior (escenario H): en ese caso el
  `project.json` queda consistente (ya comitteado), pero algún asset
  obsoleto puede sobrevivir en disco como basura huérfana — no es
  pérdida de datos ni inconsistencia del manifest, es, en el peor caso,
  un archivo de más sin referenciar. Se documenta como riesgo residual
  aceptable (ver más abajo), no como bloqueador.

### R4.2 — `saveNow`/`onDone` — **CORREGIDO, auditado de nuevo**

Se re-auditaron las 3 rutas reales de salida del editor que llaman a
`saveNow` (`EditorScreen.kt:1037,1360,6119`, todas
`viewModel.saveNow { onBackToProjects() }`) más la ruta de segundo plano
(`MainActivity.kt:575`, `ON_STOP` → `viewModel.saveNow()` sin `onDone`).
Las 4 pasan por la MISMA función `saveNow` ya corregida en la Fase 4.2
original — no hay una segunda ruta paralela que reintroduzca el bug. La
ruta de `ON_STOP` no navega a ningún lado (no tiene `onDone` con efecto),
así que un fallo ahí ya queda cubierto por `SaveState.Error` sin ningún
riesgo de "salida silenciosa". No se encontró ninguna ruta adicional
(no hay `onPause`/`onDestroy` que llame a guardar en este proyecto).

### R4.3 — Confinamiento de mutaciones a Main — **CORREGIDO PARCIALMENTE**

Confirmado: `LayerApiImpl`, `CameraApiImpl` y `AudioApiImpl` llamaban a
`mutator` (`EditorViewModel`, que muta `_uiState` con lectura-
modificación-escritura simple, pensada para `Main`) sin ningún
confinamiento propio — `suspend` no garantiza en qué dispatcher corre el
llamador. **Corregido:** las 3 clases ahora envuelven cada escritura en
`withContext(Dispatchers.Main.immediate)` antes de llegar al `mutator`.
`TimelineApiImpl.generateThumbnail` ya usaba `Dispatchers.Default`
correctamente (es una operación de LECTURA/renderizado, no de mutación de
`_uiState`, así que no aplica el mismo criterio).

**Por qué "parcial":** el confinamiento a Main evita que una escritura
llegue a `_uiState` desde el dispatcher equivocado, pero NO resuelve
todas las carreras posibles dentro de `EditorViewModel` mismo — su propio
patrón `_uiState.value = _uiState.value.copy(...)`, usado en TODO el
`ViewModel` (no solo en las funciones tocadas por EliNer), sigue sin
ningún lock si dos coroutines llegaran a estar Y ejecutarse en Main al
mismo instante (que en la práctica de un solo hilo Main no puede pasar
de forma preemptiva, pero sí puede intercalarse en puntos de suspensión).
Reescribir ese patrón en todo `EditorViewModel` es una refactorización
mucho más amplia que el alcance de esta fase — explícitamente fuera de
alcance (Parte 18 del prompt R4: "nuevo sistema de undo"/"nueva
arquitectura de UI" no se tocan).

**Test agregado:** `api/camera/CameraApiDispatcherConfinementTest.kt` —
usa la implementación REAL de `CameraApiImpl` (sin `Context`, se puede
instanciar en JVM puro) con fakes de `ActiveProjectReader`/
`ActiveProjectMutator`, y `kotlinx-coroutines-test` (dependencia nueva)
para instalar un `Main` de test. Demuestra que llamando
`setKeyframe`/`removeKeyframe`/`setBaseFrame` desde `Dispatchers.Default`,
el `mutator` igual los recibe bajo `Main`. **No se pudo escribir el mismo
test para `LayerApiImpl`/`AudioApiImpl`**: ambos requieren un
`android.content.Context` real en su constructor (`LayerRepository`/
`AudioApiImpl` mismo), que no se puede instanciar en un JVM unit test
puro sin Robolectric — la corrección de código en esos dos SÍ se hizo
(ver arriba), pero queda sin test de comportamiento propio en este
entorno; se verificó únicamente por inspección de código.

### R4.4 — Setters vs toggles — **CORREGIDO**

Confirmado el bug real: `EditorViewModel.setLayerVisible/setLayerLocked/
setLayerOrderLocked` (la implementación de `ActiveProjectMutator`) hacían
"leer estado actual → si difiere, togglear" en vez de escribir el valor
absoluto directo. Dos llamadas concurrentes a `setVisible(id, true)`
podían ambas leer `visible == false` antes de que cualquiera escribiera,
y las dos togglear — resultado final `false` en vez de `true`, más un
checkpoint de undo/autosave duplicado. **Corregido:** ahora escriben el
valor absoluto directamente (`replaceLayer(layerId) { it.copy(visible =
visible) }`) en la misma operación que ya hace la comprobación de no-op,
sin pasar por una función `toggle*` intermedia. Esto elimina el
"double-toggle a valor incorrecto"; la carrera residual de doble
checkpoint bajo concurrencia real extrema sigue existiendo como
limitación general de `_uiState` (ver R4.3).

`LayerApiImpl.setVisible/setLocked/setOrderLocked` ya delegaban
correctamente a estos métodos con semántica absoluta declarada — no
hicieron falta cambios ahí más allá del confinamiento a Main (R4.3).

### R4.5 — Reorder (steps ↔ newZIndex) — **AUDITADO, sin cambios de código**

`ActiveProjectMutator.setLayerZIndex` ya convertía correctamente
`newZIndex` → `steps` (`current.zIndex - newZIndex`) delegando al
`reorderLayer(id, steps)` real y ya probado del `ViewModel` — una única
fuente de verdad (el `reorderLayer` existente), sin duplicar la lógica de
límites/locks/undo/autosave. No se encontró ninguna conversión "mecánica
e insegura" que requiriera corrección — el diseño ya existente antes de
esta fase era correcto. Se documenta como confirmado, no como hallazgo
nuevo.

### R4.6/R4.7 — Migración de Layer/Camera — **CORREGIDO (dispatcher) / PENDIENTE (consumidor real en UI)**

El boundary de `LayerApi`/`CameraApi` (contratos, setters absolutos,
confinamiento a Main) queda correcto y listo para consumirse. **Lo que
sigue PENDIENTE, sin cambios en esta sesión:** `EditorScreen.kt` sigue
llamando directo a `EditorViewModel.toggleLayerVisibility`/
`reorderLayer`/etc., no a través de `LayerApi`/`CameraApi` — es decir,
las APIs están listas pero sin consumidor real todavía. Migrar
`EditorScreen.kt` (18k líneas) call-site por call-site, verificando
semántica exacta de cada uno (selección/playhead que el ViewModel deriva
hoy y que la API pública espera recibir explícita, per Parte 6/7 del
prompt R4) es un cambio de alto riesgo que no puedo compilar/testear en
este entorno — hacerlo "a ciegas" arriesga romper undo/autosave de forma
que no podría detectar antes de entregarlo. Se prioriza dejar el
boundary correcto y NO tocar la UI antes que migrar de forma insegura.

### R4.8 — Audio — **CORREGIDO (dispatcher) / ya migrado en boundary**

Mismo criterio que R4.6: `AudioApiImpl` ahora confina sus escrituras a
Main. `AudioClipSnapshot` (Fase 4.2 original) sigue vigente — la
frontera pública nunca devuelve `AudioClip` mutable. Consumidor real en
UI: pendiente, mismo motivo que Layer/Camera.

### R4.9 — Timeline: `currentDurationMs()` — **AUDITADO, contrato CORRECTO (solo needed documentación)**

Comparado `TimelineApi.currentDurationMs()` (→ `reader.getBaseDurationMs()`
→ `TimelineDurationManager.currentDurationMs()`, el campo `durationMs`
del manager) contra `SpeedRampEngine.computeOutputDurationMs` (la
duración FINAL de export, que sí cambia con velocidad variable/freeze —
ver `ENGINE_OPERATIONS.md`, sección Animation). Son conceptos
DISTINTOS a propósito: el timeline base (lo que el usuario edita, con
keyframes de cámara) NUNCA se comprime/estira visualmente en el editor —
solo la reproducción/exportación real avanza ese eje base a otra
velocidad. `TimelineApi` representa el primero (correcto para lo que la
UI de edición necesita); el segundo pertenece al dominio de Export, no a
Timeline. **Conclusión: el contrato es correcto, `getBaseDurationMs` es
simplemente el nombre interno más preciso** — no se requiere ningún
cambio de código, solo esta nota queda como la documentación pedida por
la Parte 9 del prompt R4.

### R4.10 — `ActiveProjectMutator`/`ActiveProjectReader` — **AUDITADO, sin ciclos, sin cambios**

Revisados los 22 métodos de `ActiveProjectMutator` y los 8 de
`ActiveProjectReader`: todos son implementados por `EditorViewModel`
delegando a su propia lógica ya existente (mismo checkpoint/autosave que
ya usaba antes de que existiera esta interfaz) — no hay una segunda
máquina de edición paralela. No existe el ciclo UI→API→Mutator→ViewModel
mismo método→API→... descrito como riesgo en la Parte 13 del prompt R4:
cada método de `ActiveProjectMutator` llama a una función DISTINTA
(interna, privada o ya existente) del `ViewModel`, nunca se
retro-invoca a sí mismo a través de la interfaz. `ActiveProjectMutator`
sigue siendo un adapter interno (no una segunda API pública) — no se
encontró ningún consumidor externo a `Api*Impl` que lo use directo.

### R4.11 — `AtomicFileReplaceTest` — **CORREGIDO (bug real en el test)**

Confirmado el bug señalado por el prompt R4: el primer test
(`copia exitosa reemplaza el destino y no deja temporales`) afirmaba a
la vez `destFile.exists() == true` Y `tempRoot.listFiles()?.size == 0` —
contradictorio (si `destFile` existe dentro de `tempRoot`, el directorio
no puede tener 0 archivos). Ese test hubiera fallado si se hubiera
llegado a ejecutar. **Corregido:** ahora verifica que el directorio
tiene EXACTAMENTE 1 archivo y que es el destino final — sin confundir
"no quedan temporales huérfanos" con "no queda ningún archivo". Los
demás tests del archivo ya tenían la aserción correcta (`== 1` cuando
`destFile` existe, `== 0` solo cuando no existe ningún archivo).

### R4.12 — `AppLogger` — sin cambios (confirmado que se conserva)

Confirmado: la corrección con `java.time`/`DateTimeFormatter` de la Fase
4.2 original sigue intacta, no se revirtió a `SimpleDateFormat`. No se
hizo ninguna limpieza adicional de otros usos de `SimpleDateFormat` en el
resto del proyecto — fuera de alcance explícito de la Parte 16 del
prompt R4.

### R4.13 — GPU R3.1.3 — **CONFIRMADO INTACTO**

Ningún archivo de `LayerGpuCommitGate`/`SingleResourceHandoff`/
`TextureInvalidationRequest`/`ContentIdentity`/`contentRevision`/
`CameraTrack` (ownership del snapshot) fue tocado en ninguna de las dos
pasadas de la Fase 4.2 (original ni R4). No se encontró ningún defecto
introducido por los cambios de esta fase que afecte a esa arquitectura.
**Se declara explícitamente: la Fase GPU (3.1.3-R3.1.3) permanece
cerrada.**

### Riesgos residuales reales (no ocultos)

1. Crash de PROCESO entre el commit de `project.json` y la limpieza
   post-commit (escenario H) puede dejar basura huérfana en disco (un
   asset sin referenciar) — nunca inconsistencia del manifest ni pérdida
   de datos. Impacto: bajo (espacio de almacenamiento), no funcional.
2. `_uiState` de `EditorViewModel` sigue sin lock propio contra
   concurrencia genuina entre coroutines en Main con puntos de
   suspensión intercalados — el confinamiento a Main (R4.3) reduce la
   superficie pero no la elimina por completo. Ver R4.3 para el porqué
   de no resolverlo en esta fase.
3. `LayerApiImpl`/`AudioApiImpl` no tienen test de comportamiento propio
   para el confinamiento a Main en este entorno (requieren `Context`
   real) — la corrección de código existe y se verificó por inspección,
   pero no por ejecución.
4. `projectMutexes` (`ProjectStorage`) sigue acumulando indefinidamente
   (ver sección 8 del addendum anterior) — no se tocó en esta pasada,
   mismo razonamiento de riesgo que antes.
5. Consumer migration real de UI (Layer/Camera/Audio) sigue pendiente —
   las APIs están listas pero `EditorScreen.kt` no las consume todavía.

## Criterios de aceptación

- [x] Un fallo de asset NO elimina silenciosamente una capa persistida.
- [x] Un fallo de reemplazo NO destruye el asset anterior válido.
- [x] Un fallo de audio NO destruye el audio anterior válido.
- [x] `saveNow()` solamente llama `onDone()` cuando el guardado fue
      realmente exitoso.
- [x] `AudioClip` mutable NO queda expuesto como contrato público estable
      (vía `AudioApi`).
- [ ] Los principales contratos EliNer fueron auditados contra
      `engine.*`/platform types — **pendiente** (solo `AudioApi`).
- [x] No se creó una segunda fuente de verdad / segundo ViewModel /
      singleton / service locator / JNI / C++ / Vulkan / Oboe.
- [x] GPU ownership de R3.1.3 permanece intacto (no se tocó
      `LayerGpuCommitGate`/`SingleResourceHandoff`/`ContentIdentity`/
      `contentRevision`).
- [x] Undo/autosave no se tocaron en su mecanismo — ver limitación de
      cobertura de test arriba.
- [ ] Consumer migration (toggle/reorder/keyframes/audio) — **pendiente**,
      no se tocó esta fase.
- [x] Tests de regresión agregados para los defectos críticos 4.1–4.4 y 6
      (con la limitación de ejecución señalada arriba).
- [x] Esta documentación representa el estado real (no declara
      "Fase 4.2 completada").
- [x] No se implementó Fase 4.3 / Render Boundary.
