# FASE 1 — Estabilización P0/P1: Estado, Persistencia, Integridad y Seguridad

**Estado:** Implementación completa sobre el código real del proyecto
(auditoría estática + corrección directa). No se compiló ni se corrió la
app/los tests en este entorno (sin SDK de Android, sin red) — eso queda
para el usuario vía GitHub Actions, según lo acordado para esta fase. Este
documento describe exactamente lo que quedó implementado, no intenciones.

---

## 1. Objetivo de la fase

Corregir defectos P0/P1 de integridad y seguridad en el sistema de
proyectos de Olyze Creation Studio, con la prioridad:

> **INTEGRIDAD DEL PROYECTO > SEGURIDAD > CONSISTENCIA > ROBUSTEZ > COMPATIBILIDAD**

Alcance: estado mutable del editor (snapshots/undo-redo), consistencia del
guardado, integridad de copia de imagen/audio/portada, seguridad de rutas
del manifest, límites contra ZIP bombs, y validación básica de valores del
manifest. Sin migraciones de API, sin refactors de motor/render, sin
cambios de branding/formato público — según lo pedido.

---

## 2. Estado encontrado antes de la implementación

El código real (no la documentación previa) tenía los siguientes defectos,
confirmados leyendo `EditorViewModel.kt` (2733 líneas) y `ProjectStorage.kt`
(1214 líneas) directamente:

- `ProjectContentSnapshot` guardaba `layers: List<Layer>` y
  `audioClip: AudioClip?` tal cual — los mismos objetos mutables en vivo
  del editor, no una copia.
- `LayerEditState` (undo/redo) no incluía ninguna de las propiedades de
  color/degradado/blanco y negro de `Layer`.
- `saveProject()` recibía `layers: List<Layer>` (objetos mutables en vivo)
  y los serializaba desde `Dispatchers.IO`, mientras el hilo de UI seguía
  libre para mutarlos (undo/redo, "salir sin guardar").
- `ensureLocalImage()`/`ensureLocalAudio()` trataban un `InputStream` nulo
  como "copia exitosa" (`runCatching` no atrapa un `null`, solo
  excepciones).
- `loadProject()`/`listProjects()` resolvían `imageFileName`/
  `audioFileName`/`coverImageFileName`/fotos de elenco directo desde
  `project.json` con `File(dir, nombreDelManifest)`, sin validar que el
  resultado quedara confinado al directorio esperado.
- `extractZipEntriesSafely()` ya protegía contra Zip Slip (nombres de
  entrada), pero no tenía ningún límite de cantidad de entradas, tamaño
  por entrada, tamaño total descomprimido, ni ratio de compresión.
- `project.json` se decodificaba sin ningún saneo de valores numéricos
  (fps, duración, cuadrícula, ángulo de degradado, etc.).

---

## 3. Problemas detectados y causa raíz

| # | Problema | Causa raíz |
|---|----------|------------|
| 1 | `hasUnsavedChanges()` no detectaba cambios de capa | El "snapshot" y el estado en vivo eran **literalmente los mismos objetos** `Layer` (aliasing) — comparar un objeto contra sí mismo siempre da igual, sin importar qué haya cambiado en el medio. |
| 2 | `discardChangesAndExit()` no revertía color/transform/keyframes de capa | Por el mismo aliasing: `layers = snapshot.layers` asignaba una lista que apuntaba a los objetos YA mutados. |
| 3 | Undo/Redo no revertía color/degradado/B&N | `LayerEditState` nunca capturaba esos campos, aunque sí eran editables desde la rueda de color. |
| 4 | `project.json` podía quedar inconsistente durante un guardado concurrente a una edición | `saveProject` leía campos `var` de `Layer`/`AudioClip` en vivo desde `Dispatchers.IO`, mientras el hilo de UI podía seguir mutando esos mismos objetos (undo/redo, discard). |
| 5 | Una capa podía quedar con una referencia de imagen a un archivo que nunca se copió | `openInputStream(uri)?.use{}` devuelve `null` sin lanzar excepción si el stream es nulo; `runCatching` solo atrapa excepciones, así que ese `null` se veía como éxito. |
| 6 | Igual que el #5 pero para audio de fondo | Mismo patrón exacto en `ensureLocalAudio()`. |
| 7 | Un `.olycs` con `project.json` manipulado podía hacer que la app leyera/decodificara archivos fuera de la carpeta del proyecto | Zip Slip protege la EXTRACCIÓN del zip (nombres de entrada), pero los VALORES de texto dentro de `project.json` (ya extraído, archivo 100% legítimo) nunca se revalidaban antes de usarse para resolver una ruta de archivo. |
| 8 | Un `.olycs` malicioso podía agotar disco/memoria al importar | Sin límites de cantidad de entradas, tamaño por entrada, tamaño total, ni ratio de compresión en `extractZipEntriesSafely`. |
| 9 | Un `project.json` corrupto/manipulado podía producir crashes o estados imposibles (fps=0, duración negativa, cuadrícula 0×0, ángulo NaN) | Cero validación de valores numéricos al decodificar el manifest. |

---

## 4. Archivos afectados

### Modificados
- `app/src/main/java/com/yeivikas/olyzecs/data/ProjectStorage.kt` — núcleo de la fase.
- `app/src/main/java/com/yeivikas/olyzecs/viewmodel/EditorViewModel.kt` — snapshot/undo-redo/persistNow.
- `README.md` — actualizado el bullet de Undo/Redo (Fase 5) para reflejar la cobertura de color/degradado/B&N.
- `docs/pending-work/PROJECT_AUDIT.md` — nota apuntando a este informe en la sección de Seguridad.

### Creados
- `docs/fases/FASE_1_ESTABILIZACION_P0_P1.md` — este informe.
- `app/src/test/java/com/yeivikas/olyzecs/data/ProjectStorageManifestSecurityTest.kt`
- `app/src/test/java/com/yeivikas/olyzecs/data/ProjectDataSanitizationTest.kt`
- `app/src/test/java/com/yeivikas/olyzecs/engine/camera/CameraTrackSnapshotIndependenceTest.kt`

### Eliminados
Ninguno.

---

## 5. Clases/métodos afectados

- `EditorViewModel`: `ProjectContentSnapshot`, `LayerContentState` (nueva),
  `AudioContentState` (nueva), `LayerEditState`, `toContentSnapshot()`,
  `captureSnapshot()`, `restoreSnapshot()`, `discardChangesAndExit()`,
  `persistNow()`.
- `ProjectStorage`: `saveProject()`, `ensureLocalImage()`,
  `ensureLocalAudio()`, `setCoverImage()`, `setCastPhoto()`,
  `loadProject()`, `listProjects()`, `extractZipEntriesSafely()`, más las
  funciones/tipos nuevos `LayerSaveSnapshot`, `AudioSaveSnapshot`,
  `SaveProjectResult`, `resolveManifestFile()`, `ZipExtractionLimits`,
  `ZipBombSuspectedException`, `sanitizeProjectData()`,
  `copyUriToFileOrFail()`.

---

## 6. Solución implementada

### 6.1 Snapshot inmutable real (`ProjectContentSnapshot`)

Se introdujo `LayerContentState` (DTO inmutable, solo `val`) capturando
**todos** los campos persistibles de `Layer`: transform (`zIndex`,
`parallaxFactor`, `locked`, `orderLocked`, `visible`), look
(`lookSettings`), cámara (`keyframes`, `baseFrame`), tamaño (`widthPx`,
`heightPx`) y **todos** los campos de color/degradado/B&N (`colorIndex`,
`customColorArgb`, `importedDefaultColorArgb`,
`customGradientStartArgb/EndArgb`, `useGradientColor`,
`gradientAngleDegrees`, `gradientIsRadial`, `useBlackAndWhiteMode`).
Deliberadamente **excluye** `glTextureId`/`pendingBitmap` (recursos
GL/CPU de runtime, no estado persistible).

La captura de `keyframes` usa `cameraTrack.keyframes.toList()`. Esto
alcanza para independizarla del contenedor mutable interno de
`CameraTrack` porque `Keyframe` en sí mismo ya es inmutable (`data class`
de solo `val`) — no hace falta clonar cada elemento, solo el contenedor.
Mismo razonamiento para `CameraFrame` (baseFrame) y `LookSettings`: ambos
ya son `data class` inmutables, así que compartir la referencia es seguro.

`AudioContentState` aplica el mismo criterio para `AudioClip` (también
mutable).

`ProjectContentSnapshot.layers` pasó de `List<Layer>` a
`List<LayerContentState>`; `.audioClip` de `AudioClip?` a
`AudioContentState?`.

### 6.2 `discardChangesAndExit()` — reconciliación real

Ya no hace `layers = snapshot.layers` (no compila más: el snapshot no
tiene `Layer`s). Reconcilia por `id` contra las capas en vivo:

- Si la capa **sigue existiendo**: se le aplican los valores del snapshot
  **in-place** (`LayerContentState.applyTo()`), preservando la textura GL
  ya subida salvo que la imagen realmente haya cambiado — mismo criterio
  que ya usaba `restoreSnapshot()` para undo/redo.
- Si la capa **fue eliminada** durante la sesión: se reconstruye desde
  cero (`LayerContentState.toFreshLayer()`) a partir del snapshot; su
  copia local en disco sigue intacta, así que `sourceUri` sigue siendo
  válido. `glTextureId=-1`/`pendingBitmap=null` a propósito: el motor GL
  ya decodifica de nuevo desde `sourceUri` la primera vez que encuentra
  una capa sin textura ni bitmap pendiente (comportamiento existente de
  `GLRenderer`, no se tocó).
- Si la capa **fue creada después** del snapshot: se descarta (no
  aparece en `snapshot.layers`), porque "salir sin guardar" es volver
  exactamente al estado de apertura.

### 6.3 Undo/Redo — `LayerEditState` completo

Se agregaron a `LayerEditState`, `captureSnapshot()` y `restoreSnapshot()`
los 9 campos de color/degradado/B&N que faltaban (mismos nombres que en
`LayerContentState`, ver 6.1). Antes de esta fase, deshacer un cambio de
color/degradado/B&N no revertía nada porque esos campos nunca viajaban en
el snapshot de undo.

### 6.4 Consistencia del guardado — `persistNow()`/`saveProject()`

Patrón implementado: **ESTADO VIVO → CAPTURA CONTROLADA → SNAPSHOT
ESTABLE → IO/SERIALIZACIÓN**.

- `persistNow()` construye `layerSnapshots = state.layers.map { it.toSaveSnapshot() }`
  y `audioSnapshot = state.audioClip?.toSaveSnapshot()` **antes** de
  llamar a `saveProject` — es decir, antes de que el corrutine cruce a
  `Dispatchers.IO`. Cada campo `var` se lee una única vez, de forma
  síncrona, en el hilo que llama.
- `saveProject()` cambió su firma: `layers: List<Layer>` →
  `layers: List<LayerSaveSnapshot>` (DTO inmutable). El hilo de IO ya
  nunca vuelve a leer un campo mutable de un `Layer` en vivo mientras
  serializa — un undo/redo o un discard que ocurra en paralelo (el hilo
  Main sigue libre mientras el guardado está en IO) no puede producir un
  `project.json` a medio mutar.
- **Excepción deliberada y documentada:** `saveProject` sigue recibiendo
  `liveLayersForThumbnail: List<Layer>` (objetos en vivo) exclusivamente
  para `ThumbnailRenderer.render()`, porque ese renderizado usa
  comportamiento real del motor GL (`CameraTrack.frameAt`, `LayerDrawer`),
  no solo datos — migrar el renderer de miniaturas a un DTO es un cambio
  de arquitectura del motor de render, explícitamente fuera de alcance de
  esta fase. El residuo de riesgo que esto deja es mucho menor: en el
  peor caso, la miniatura puede reflejar un frame visual ligeramente
  desactualizado — nunca una escritura inconsistente de `project.json`,
  que es lo que importa para la integridad real del proyecto. Ver
  sección 22 (Riesgos restantes).
- **Optimización preservada de forma segura:** cuando `ensureLocalImage`/
  `ensureLocalAudio` resuelven una copia local nueva, `saveProject`
  devuelve esas referencias en `SaveProjectResult.resolvedLayerImageUris`/
  `resolvedAudioUri`. `persistNow()` las aplica de vuelta a los objetos
  `Layer`/`AudioClip` en vivo **después** de que la IO terminó (secuencial,
  sin carrera), y **solo si** el campo no cambió desde que se capturó el
  snapshot (comparación contra el URI capturado) — así no se pisa una
  edición más nueva del usuario que haya ocurrido mientras el guardado
  anterior estaba en vuelo, y los próximos autoguardados siguen sin
  re-copiar desde el URI de SAF original en cada tick.
- La escritura atómica de `project.json` (archivo temporal + rename) ya
  existía de una auditoría previa y se conservó sin cambios.

### 6.5 Integridad de copia — `ensureLocalImage()`/`ensureLocalAudio()`

Reescritas para:
1. Exigir explícitamente que `openInputStream()` no sea nulo (se convierte
   en una excepción real si lo es, para que quede atrapada por
   `runCatching`).
2. Solo considerar éxito si el archivo destino existe **y** tiene
   contenido real (`length() > 0`) después de la copia.
3. Nunca actualizar ninguna referencia ni dejar un archivo parcial a medio
   escribir (se borra si quedó algo).
4. Devolver `null` (en vez de un nombre de archivo fantasma) si la copia
   no pudo asegurarse — `saveProject()` usa `mapNotNull` para **omitir**
   esa capa del guardado en vez de persistir una referencia a un archivo
   inexistente.

### 6.6 `setCoverImage()`/`setCastPhoto()`

Mismo patrón de integridad, extraído a `copyUriToFileOrFail()`
(compartido entre ambos métodos). Ya tenían una comprobación parcial
(`dest.exists()`), pero no cubría el caso de **reemplazar** una portada ya
existente con un URI inválido (el archivo viejo seguía existiendo, así
que `exists()` daba `true` sin que la copia nueva hubiera pasado
realmente). Ahora se borra `dest` antes de intentar copiar, así
`exists()` después solo puede ser cierto si la copia nueva se escribió de
verdad.

### 6.7 Rutas del manifest — `resolveManifestFile()`

Nueva función que resuelve un nombre de archivo del manifest dentro de un
directorio esperado y exige, por **ruta canónica**, que el resultado siga
confinado ahí — mismo criterio que ya usaba `extractZipEntriesSafely()`
para Zip Slip, aplicado ahora a un vector distinto (valores de texto
dentro de un archivo ya extraído, no nombres de entrada del zip). Se
aplicó en `loadProject()` (imágenes de capa, audio) y `listProjects()`
(portada, fotos de elenco).

### 6.8 Límites contra ZIP bombs

`ZipExtractionLimits`: `MAX_ENTRIES=500`,
`MAX_UNCOMPRESSED_ENTRY_BYTES=300MB`, `MAX_UNCOMPRESSED_TOTAL_BYTES=1GB`,
`MAX_COMPRESSION_RATIO=200`. `extractZipEntriesSafely()` ahora cuenta
bytes reales **mientras copia** (no después) y corta con
`ZipBombSuspectedException` apenas se supera cualquier límite — así una
entrada-bomba no llega a escribir gigabytes a disco antes de ser
detectada. La limpieza de la extracción parcial ya la hacía
`importProjectZip()` (borra `dstDir` si `extracted=false` o falta
`project.json`), reutilizada tal cual.

### 6.9 Validación de valores del manifest — `sanitizeProjectData()`

Satura (no reescribe el modelo) los campos concretos que el resto del
código asume dentro de rango: `fps` (1..240), `projectDurationMs`
(1s..`TimelineLimits.MAX_DURATION_MS`), `playheadMs` (0..duración),
`gridColumns`/`gridRows` (1..64), grosor/opacidad/tono de línea de
cuadrícula (finitos, en rango), y por capa: `zIndex`, `parallaxFactor`
(finito), `widthPx`/`heightPx` (≥0), `colorIndex` (≥0),
`gradientAngleDegrees` (finito). Se llama justo después de decodificar
`project.json` en `loadProject()`, antes de construir cualquier `Layer`.

---

## 7. Decisiones arquitectónicas

- **DTOs inmutables en el borde, no reescribir `Layer`/`AudioClip` como
  inmutables.** `Layer` sigue siendo mutable a propósito (el motor GL lo
  muta in-place para preservar texturas — ver README, sección "por qué el
  undo no reemplaza los objetos Layer"). La solución de esta fase pone
  DTOs inmutables **en los bordes** (snapshot de comparación, snapshot de
  undo, snapshot de guardado) sin tocar el modelo de edición en vivo.
- **Reconciliación por `id`, no reemplazo de lista completa**, tanto en
  `discardChangesAndExit()` como ya lo hacía `restoreSnapshot()` — para
  preservar identidad de objeto (y por lo tanto textura GL) donde sea
  posible.
- **`liveLayersForThumbnail` como excepción documentada**, no como
  omisión: se decidió explícitamente no migrar `ThumbnailRenderer` en esta
  fase (fuera de alcance: "no gran refactor... no migración completa de
  RenderApi"), y se dejó registrado como riesgo residual conocido y
  acotado, no oculto.
- **Sin locks de proyecto completo.** La corrección de la carrera de
  guardado no usa un lock nuevo — el `mutexFor(projectId)` que ya existía
  en `saveProject`/`loadProject` sigue sirviendo para lo que protegía
  (dos guardados concurrentes del mismo proyecto); el problema de esta
  fase era distinto (lectura de campos mutables desde otro hilo) y se
  resolvió con inmutabilidad, no con más sincronización.
- **`null` como resultado de fallo, no excepción, en las funciones de
  copia/resolución de rutas.** `ensureLocalImage`/`ensureLocalAudio`/
  `resolveManifestFile` devuelven `null` en vez de lanzar, para que un
  único recurso roto/malicioso de un proyecto por lo demás legítimo no
  tire abajo toda la carga o todo el guardado.

---

## 8. Cómo se evita el aliasing mutable

Tres capas de DTOs inmutables, cada una con su propio propósito, todas
construidas con el mismo criterio (copiar el contenedor, no cada
elemento, cuando el elemento ya es inmutable):

| DTO | Dónde vive | Para qué |
|-----|-----------|----------|
| `LayerContentState` / `AudioContentState` | `EditorViewModel` (privado) | Comparación "¿hay cambios sin guardar?" y restauración de "salir sin guardar". |
| `LayerEditState` | `EditorViewModel` (privado, ya existía) | Historial de undo/redo. |
| `LayerSaveSnapshot` / `AudioSaveSnapshot` | `ProjectStorage` (público) | Captura estable inmediatamente antes de cruzar a `Dispatchers.IO` para serializar. |

Los tres son estructuralmente casi idénticos a propósito (mismos campos,
mismo criterio de qué es "estado persistible") pero se mantienen como
tipos separados porque viven en módulos distintos y sirven a operaciones
con reglas de reconciliación distintas (undo reconcilia siempre por id
ignorando altas/bajas; discard reconstruye capas eliminadas; save nunca
reconcilia, solo serializa).

---

## 9. Cómo funciona ahora `ProjectContentSnapshot`

Ver sección 6.1. En una frase: es una foto profunda e inmutable, tomada al
abrir/crear el proyecto (`initialContentSnapshot = _uiState.value.toContentSnapshot()`),
contra la que `hasUnsavedChanges()` compara estructuralmente
(`!=` de `data class`) el estado actual (también convertido a la misma
foto inmutable en el momento de comparar). Como ninguno de los dos lados
de la comparación comparte referencias mutables con el editor en vivo, la
comparación es real.

---

## 10. Cómo funciona ahora Undo/Redo

Sin cambios de arquitectura (seguía funcionando correctamente para los
campos que sí capturaba) — se completó la lista de campos capturados. El
flujo sigue siendo: `captureSnapshot()` arma un `EditSnapshot` con un
`LayerEditState` completo por capa antes de cada cambio (o al fusionar una
ventana de 600ms de cambios continuos); `restoreSnapshot()` aplica esos
valores in-place sobre los `Layer` en vivo, preservando identidad de
objeto salvo cuando `sourceUri` cambió.

---

## 11. Cómo se garantiza la consistencia del guardado

Ver sección 6.4.

---

## 12. Cómo se garantiza la copia de imágenes

Ver sección 6.5.

---

## 13. Cómo se garantiza la copia de audio

Ver sección 6.5 (mismo patrón, función separada `ensureLocalAudio`).

---

## 14. Cómo se valida `setCoverImage`

Ver sección 6.6.

---

## 15. Cómo se protegen las rutas del manifest

Ver sección 6.7.

---

## 16. Cómo funciona la protección contra ZIP bombs

Ver sección 6.8.

---

## 17. Límites implementados y justificación

Ver `ZipExtractionLimits` (sección 6.8) — valores pensados para un
proyecto real grande en este formato (varias capas en resolución completa
+ audio + portada/miniatura + hasta 4 fotos de elenco) con margen amplio,
sin dejar pasar un abuso obvio. Justificación completa en el KDoc de
`ZipExtractionLimits` en el código.

---

## 18. Validaciones añadidas al manifest

Ver sección 6.9 (`sanitizeProjectData`).

---

## 19. Compatibilidad `.olycs`

- Extensión, branding, package/application ID: sin cambios.
- `ProjectData`/`LayerData`/`AudioTrackData`: **sin cambios de esquema.**
  No se agregó ni se quitó ningún campo serializable. `sanitizeProjectData`
  opera sobre el modelo ya decodificado, no cambia el formato en disco.
- Proyectos guardados por versiones anteriores de la app siguen
  cargando: los valores ya válidos quedan bit a bit iguales tras
  `sanitizeProjectData` (es un `coerceIn`/`isFinite` sobre valores que ya
  estaban en rango — no-op).
- `SaveProjectResult` es un tipo nuevo, pero es un cambio de **API interna
  de Kotlin** (el único llamador es `EditorViewModel`, ya actualizado) —
  no afecta el formato `.olycs` ni el `project.json` en disco.

---

## 20. Tests creados/modificados

Todos corren como JVM unit tests puros (JUnit4, sin Robolectric, mismo
criterio que los tests ya existentes del proyecto) y **no fueron
ejecutados en este entorno** (sin SDK Android/Gradle disponible aquí, tal
como se indicó en el alcance de la fase) — quedan listos para correr en
GitHub Actions.

- **`ProjectStorageManifestSecurityTest.kt`** (nuevo):
  - `resolveManifestFile` rechaza `../`, `../../`, rutas absolutas, y
    nombres nulos/en blanco (ítem F del pedido).
  - `resolveManifestFile` resuelve nombres normales dentro del directorio
    esperado.
  - Propiedad end-to-end: ninguna ruta maliciosa resuelta queda fuera del
    directorio esperado (canónico).
  - `extractZipEntriesSafely` rechaza una entrada individual demasiado
    grande, y un zip con demasiadas entradas (ítem G del pedido).
  - Un zip legítimo y chico se extrae sin disparar ningún límite.
- **`ProjectDataSanitizationTest.kt`** (nuevo):
  - Un proyecto ya válido queda exactamente igual tras `sanitizeProjectData`
    (no-op para datos legítimos — compatibilidad, ítem H del pedido).
  - `fps` fuera de rango se satura.
  - Duración negativa/absurda se satura dentro de `TimelineLimits`.
  - Playhead se recorta para nunca superar la duración saneada.
  - Columnas/filas de cuadrícula en 0 o negativas se satura a ≥1.
  - Ángulo de degradado `NaN`/`Infinity` de una capa se reemplaza por un
    valor finito.
  - `colorIndex` negativo se satura a 0.
  - `zIndex`/`parallaxFactor` absurdos quedan acotados.
- **`CameraTrackSnapshotIndependenceTest.kt`** (nuevo): prueba en
  aislamiento (sin depender de `EditorViewModel`/`Layer`, que requieren
  `android.net.Uri`) la propiedad exacta en la que se apoya
  `LayerContentState`/`LayerSaveSnapshot`/`LayerEditState`: que
  `cameraTrack.keyframes.toList()` alcanza para independizar la foto de
  keyframes del contenedor mutable de `CameraTrack`, porque `Keyframe` en
  sí mismo ya es inmutable. Cubre exactamente el escenario B pedido:
  *crear snapshot → modificar keyframes en vivo → snapshot permanece
  igual.* También prueba que `replaceAll()` preserva identidad del objeto
  `CameraTrack` (por qué el undo no fuerza re-upload de textura).

### Cobertura NO alcanzada en esta fase (ver sección 22)

Los escenarios A/C/D/E del pedido original (snapshot profundo de
`ProjectContentSnapshot` de punta a punta, undo/redo de `EditorViewModel`,
serialización estable de `persistNow`, y el caso "InputStream null" de
`ensureLocalImage`/`ensureLocalAudio`) viven en código que depende de
`android.net.Uri`/`Context`/`ContentResolver`. El proyecto **no tiene
Robolectric configurado** (solo `testImplementation("junit:junit:4.13.2")`
en `app/build.gradle.kts`) y por defecto el `android.jar` de unit tests de
AGP lanza `RuntimeException` en cualquier método de `Uri` no mockeado —
mismo límite que ya documenta `ProjectDataSerializationTest.kt` para el
resto de `ProjectStorage` ("copiar imágenes, generar miniaturas, autosave
... queda fuera del alcance de esta Fase A como JVM unit test"). No se
agregó Robolectric en esta fase para no ensanchar el alcance del cambio de
build más allá de lo quirúrgico pedido. Queda como recomendación explícita
en "Riesgos restantes".

---

## 21. Riesgos restantes

1. **Miniatura con frame visual potencialmente desactualizado.**
   `ThumbnailRenderer.render()` sigue leyendo `Layer` en vivo (ver sección
   6.4) — en el peor caso, si una mutación ocurre exactamente durante el
   render de la miniatura, la imagen resultante puede no reflejar el
   estado exacto del momento de guardado. Nunca afecta `project.json`.
2. **Sin cobertura de test JVM para el flujo Android-dependiente
   completo** (snapshot/undo-redo de `EditorViewModel`, `ensureLocalImage`/
   `ensureLocalAudio` con URI real) — ver sección 20. Recomendación:
   evaluar Robolectric (o inyectar un `ContentResolver` fake) en una fase
   posterior dedicada a testing, no como parte de esta estabilización.
3. **`sanitizeProjectData` es deliberadamente acotado**, no una validación
   exhaustiva de todo el modelo (por ejemplo, no valida
   `handleOrderGlobal`/`handleOrderPerLayer`, ni longitudes de string como
   `name`/`genre`). Cubre los campos con riesgo concreto de crash/estado
   imposible identificados en esta auditoría, no una reescritura del
   modelo de proyecto (explícitamente fuera de alcance).
4. **`setCoverImage`/`setCastPhoto`/`removeCoverImage`/`removeCastPhoto`
   siguen escribiendo `project.json` con `file.writeText()` directo**, no
   con el patrón atómico (tmp + rename) que sí usa `saveProject()`. No es
   parte de lo pedido explícitamente en el ítem 3 (that ítem se centra en
   `persistNow`/`saveProject`), y son escrituras puntuales de usuario, no
   el autoguardado de alta frecuencia — pero queda anotado como
   inconsistencia menor para una fase posterior.

---

## 22. Problemas deliberadamente pospuestos a fases posteriores

Explícitamente fuera de alcance de esta fase (según las restricciones del
pedido), no implementados:

- Migración de `LayerApi`/`CameraApi`/`AudioApi`/`RenderApi`.
- `EliNerApiImpl` definitivo, migración JNI/C++.
- Refactor grande de `EditorScreen`/`EditorViewModel`.
- Rediseño de Distortion/Effects/Plugin Rack.
- Cambios de branding/package/formato público `.olycs`.
- Migración completa del renderer (incluida la posible migración de
  `ThumbnailRenderer` a un DTO inmutable — ver riesgo #1 arriba).
- Agregar Robolectric/mocking de Android para ampliar cobertura de tests
  JVM (ver riesgo #2).
- Atomicidad de escritura para `setCoverImage`/`setCastPhoto` (ver riesgo #4).

---

## 23. Estado final real de la arquitectura

```
ESTADO VIVO (Layer/AudioClip mutables, editados por UI/GL en tiempo real)
      ↓  captura controlada (toContentState/toSaveSnapshot/captureSnapshot)
SNAPSHOT INMUTABLE (LayerContentState / LayerSaveSnapshot / LayerEditState)
      ↓
   ┌──────────────┬───────────────────┬───────────────────────┐
   │  comparación  │   undo/redo        │   IO / serialización   │
   │ hasUnsavedChanges│ restoreSnapshot │  saveProject (atómico) │
   └──────────────┴───────────────────┴───────────────────────┘
```

El editor en vivo sigue siendo mutable a propósito (preserva identidad de
objeto y texturas GL). La inmutabilidad se aplica en los tres bordes que
la necesitaban: comparación de estado, historial de undo/redo, y el cruce
a IO — sin tocar el modelo de edición ni el motor de render.

---

## 24. Matriz antes / después

| Área | Antes | Después | Estado |
|------|-------|---------|--------|
| Snapshot de contenido | `layers: List<Layer>` — mismos objetos mutables en vivo (aliasing) | `layers: List<LayerContentState>` — DTO inmutable, independiente | Corregido |
| `hasUnsavedChanges()` | Comparaba un objeto contra sí mismo; cambios de capa invisibles | Comparación estructural real entre dos fotos inmutables | Corregido |
| `discardChangesAndExit()` | No revertía color/transform/keyframes de capa | Reconcilia por id; restaura in-place o reconstruye capas eliminadas | Corregido |
| Undo/Redo | No capturaba color/degradado/B&N | `LayerEditState` completo, incluye los 9 campos que faltaban | Corregido |
| Persistencia (`saveProject`) | Leía `Layer` mutables desde `Dispatchers.IO` — condición de carrera real | Recibe `LayerSaveSnapshot` inmutable capturado antes de cruzar a IO | Corregido |
| Imagen local (`ensureLocalImage`) | `InputStream` nulo se interpretaba como éxito; podía persistir referencia inexistente | Exige stream no nulo + archivo destino con contenido real; devuelve `null` y omite la capa si falla | Corregido |
| Audio local (`ensureLocalAudio`) | Mismo bug que imagen | Misma corrección que imagen | Corregido |
| Cover / cast photo | Comprobación parcial (`dest.exists()`), vulnerable a "portada vieja sigue existiendo" | `copyUriToFileOrFail`: borra destino antes de copiar, exige contenido real | Corregido |
| Rutas del manifest | `File(dir, nombreDelManifest)` sin validar — path traversal vía `project.json` | `resolveManifestFile`: confinamiento por ruta canónica, aplicado a imagen/audio/cover/cast | Corregido |
| Zip Slip (extracción) | Ya protegido (fase previa) | Sin cambios — se conserva | Sin cambios |
| ZIP bombs | Sin límites de entradas/tamaño/ratio | `ZipExtractionLimits` + corte durante la copia (no después) | Corregido |
| Validación del manifest | Sin saneo de fps/duración/cuadrícula/ángulos | `sanitizeProjectData()` aplicado en `loadProject` | Corregido |
| Tests | Cubrían Zip Slip y serialización | + seguridad de manifest, ZIP bombs, saneo de valores, independencia de snapshot de cámara | Ampliado |

---

## 25. Resumen ejecutivo

Se corrigieron 9 problemas P0/P1 confirmados por auditoría directa del
código real (no de documentación): aliasing mutable en el snapshot de
contenido y en undo/redo, una condición de carrera real en el guardado,
dos variantes del mismo bug de integridad de copia (imagen/audio), un
vector de path traversal a través del manifest (distinto de Zip Slip, que
ya estaba cubierto), ausencia total de límites contra ZIP bombs, y falta
de saneo de valores del manifest. Todas las correcciones se hicieron con
DTOs inmutables en los bordes correctos del sistema, sin tocar el modelo
de edición en vivo ni el motor de render, y sin adelantar ninguna de las
fases posteriores explícitamente fuera de alcance. Se agregaron 3 archivos
de test JVM nuevos cubriendo lo que es alcanzable sin Android/Robolectric;
la cobertura del resto (dependiente de `Uri`/`Context`) queda documentada
como brecha conocida, no oculta.
