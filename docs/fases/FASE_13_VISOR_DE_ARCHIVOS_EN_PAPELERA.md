# FASE 13 — Visor de archivos en la papelera de proyectos

> Origen: pedido explícito del usuario/product owner sobre el navegador de
> archivos de la papelera de proyectos (FASE 12): tocar una fila que no
> era carpeta no hacía absolutamente nada — ni una imagen, ni un
> `project.json`, ni ningún otro archivo suelto se podía ver. Pedido
> puntual: cualquier archivo tiene que ser interactuable — una imagen
> abre un visor de imagen (ni muy grande ni muy chico, ajustado a su
> contenido), un `.json`/`.txt`/`.md`/etc. muestra todo lo que contiene —
> "algo como los softwares profesionales". Este documento cubre
> únicamente lo que se **implementó por inspección de código** en esta
> sesión — ver "Estado real / pendiente" al final.

## Resumen ejecutivo

| # | Decisión / problema | Resolución | Archivo(s) |
|---|---|---|---|
| 1 | `TrashedEntryRow` solo reaccionaba al toque si `entry.isDirectory` — un archivo suelto era una fila muerta | `.clickable(enabled = entry.isDirectory, …)` → `.clickable(…)` sin condición; `onOpen` en el call site decide navegar (carpeta) o abrir el visor (archivo) | `ProjectsTrashScreen.kt` |
| 2 | Necesita una vista previa **real** por tipo de contenido, no un genérico "no se puede mostrar" | `FilePreviewDialog` (nuevo) — clasifica por extensión (`classifyFilePreviewKind`) y renderiza: imagen con zoom/paneo real, JSON con pretty-print + resaltado de sintaxis, texto plano, audio con reproductor propio, video con reproductor propio, o una ficha "Abrir con…"/"Compartir" para cualquier extensión no reconocida | `FilePreviewDialog.kt` (nuevo) |
| 3 | El archivo real vive en `filesDir/projects_trash/…` (almacenamiento interno privado) — el `FileProvider` no lo expone directamente, así que "Compartir"/"Abrir con…" no pueden generar un `content://` a partir de él sin más | `ProjectStorage.prepareFileForSharing(sourceFile, displayName)`: copia el archivo a `getExternalFilesDir()/exports/shared/` (la única carpeta que `file_paths.xml` ya expone al `FileProvider`) — mismo criterio que `exportProjectZip` usa para los `.olycs`, sin tocar `file_paths.xml` ni ampliar qué expone el `FileProvider` | `ProjectStorage.kt` |
| 4 | El componente no debe acoplarse al dominio de "papelera de proyectos" — el pedido es genérico ("cualquier archivo") | `FilePreviewDialog` recibe `fileName`/`file`/`sizeBytes` en crudo, nunca `TrashedProjectEntry` — reusable el día de mañana desde cualquier otro lugar que liste archivos | `FilePreviewDialog.kt` |
| 5 | `ProjectsTrashViewModel` necesita exponer la copia-para-compartir sin romper el patrón `UI → ViewModel → ProjectStorage` | `suspend fun prepareEntryForSharing(entry): File?` — delega directo a `ProjectStorage.prepareFileForSharing`, sin pasar por `uiState` (operación de un solo uso, no estado observable) | `ProjectsTrashViewModel.kt` |
| 6 | Vista previa de texto/JSON necesita cobertura de tests reales, no solo revisión manual | `FilePreviewClassificationTest`, `JsonSyntaxHighlightTest`, `TextPreviewLoadingTest` (nuevos) — JVM unit tests puros, sin Robolectric, mismo criterio que `AlignmentGuidesTest`/`AtomicFileReplaceTest` | `app/src/test/java/com/yeivikas/olyzecs/ui/` (nuevo) |
| 7 | `loadTextPreview` leía el archivo **entero** a memoria (`file.readBytes()`) y recién después lo truncaba — el límite de 2 MB no protegía nada frente a un archivo realmente gigante | Lectura acotada directo desde el `InputStream` (bucle manual `read(buffer, offset, len)`, sin `InputStream.readNBytes` por compatibilidad con `minSdk 26`) — nunca se materializa en memoria más de `MAX_TEXT_PREVIEW_BYTES` | `FilePreviewDialog.kt` |

## 1. Por qué un componente nuevo, no una extensión de `ProjectsTrashScreen`

`FilePreviewDialog.kt` es un archivo aparte, con una API que no conoce
`TrashedProjectEntry` ni ningún otro modelo del dominio de proyectos —
recibe `fileName: String`, `file: File`, `sizeBytes: Long` en crudo. El
pedido explícito fue genérico ("cualquier archivo debería ser
interactuable"), no acotado a la papelera; atarlo a `TrashedProjectEntry`
hubiera significado reescribirlo entero el día que se quiera el mismo
visor en otro lugar (p. ej. un futuro explorador de `images/`/`audio/`
de un proyecto abierto, o de la papelera de capas). El único acoplamiento
con "compartir" es un lambda `suspend () -> File?` que el llamador arma
con lo que tenga a mano — en este caso, `ProjectsTrashViewModel`.

## 2. Clasificación por extensión — `classifyFilePreviewKind`

```
IMAGE  → jpg, jpeg, png, webp, gif, bmp, heic, heif
JSON   → json (caso especial: pretty-print + resaltado de sintaxis)
TEXT   → txt, md, markdown, xml, csv, log, yml, yaml, properties, srt, vtt, ini, gradle, kts
AUDIO  → mp3, wav, m4a, aac, ogg, flac, 3gp, opus
VIDEO  → mp4, mov, mkv, webm, 3gp2
(else) → UNSUPPORTED — ficha con datos del archivo + "Abrir con…"/"Compartir"
```

Ninguna rama termina en un callejón sin salida: incluso una extensión
totalmente desconocida sigue siendo interactuable — "Abrir con…" delega
la vista previa real a cualquier app que el usuario ya tenga instalada
(el mismo criterio que un explorador de archivos profesional, no una
lista de nombres inerte).

## 3. Imagen — zoom y paneo reales

`ImagePreviewContent` implementa pellizcar-para-acercar y arrastrar con
`detectTransformGestures` (rango 1x–6x) + doble tap para alternar
1x/2.5x — no un `Modifier.fillMaxSize()` estático como el que ya usaba
`TrashedEntryRow` para la miniatura de 44dp. El paneo (`offset`) se acota
a los bordes reales de la imagen ya escalada (`coerceOffset`, usando el
tamaño del contenedor medido con `Modifier.onSizeChanged`) para que no se
pueda arrastrar la imagen fuera de la vista y perderla. Estados de carga
y error de Coil (`AsyncImagePainter.State`) tienen su propio feedback
visual — spinner mientras carga, ícono + mensaje si falla — en vez de
una pantalla en blanco.

## 4. JSON — pretty-print real + resaltado de sintaxis

No es solo "mostrar el texto tal cual estaba guardado en disco": se
reparsea con `kotlinx.serialization.json.Json` (`prettyPrint = true`) y
se re-serializa formateado, para que un `project.json` compactado en una
sola línea (o editado a mano) también se vea legible. Si el archivo está
truncado (ver punto 6) o el JSON está corrupto, el `runCatching` cae con
gracia al texto crudo **igual resaltado**, con un aviso ("JSON
inválido — mostrando el contenido crudo") en vez de una pantalla de
error sin nada que leer.

El resaltado (`highlightJson`) es un tokenizador liviano por regex
(strings, números, `true`/`false`/`null`, puntuación estructural,
espacios) que distingue clave de valor-string mirando el siguiente token
no-blanco: si es `:`, es una clave. Sin dependencias nuevas — ninguna
librería de resaltado de sintaxis, todo con `AnnotatedString`/`SpanStyle`
de Compose. Por encima de `MAX_SYNTAX_HIGHLIGHT_CHARS` (300.000
caracteres) se muestra el texto plano sin resaltar — resaltar un JSON
gigantesco solo frena la UI sin agregar nada útil a la lectura.

`TextualPreviewContent` (compartido entre JSON y texto plano) agrega un
botón flotante "Copiar todo" (`ClipboardManager` de Compose) — el
`SelectionContainer` ya permite seleccionar a mano, pero copiar un JSON
de cientos de líneas arrastrando el dedo es poco práctico.

## 5. Archivos grandes — truncado explícito, nunca un freeze

`MAX_TEXT_PREVIEW_BYTES = 2 MB`: por encima de ese tamaño, la lectura se
corta ahí, con un aviso visible en la parte superior del visor ("Archivo
grande — mostrando solo los primeros 2 MB"). La lectura misma
(`loadTextPreview`) nunca materializa el archivo completo en memoria
antes de truncar — el límite acota directamente cuántos bytes se leen
del `InputStream` (bucle manual `read(buffer, offset, len)`, sin
`InputStream.readNBytes` porque esa API llegó recién en API 33 y el
`minSdk` del proyecto es 26): un log de varios GB nunca llega a ocupar
más que el límite en memoria, ni por un instante. La lectura entera
corre en `Dispatchers.IO` (llamado desde `withContext` en un
`LaunchedEffect`), nunca en el hilo de composición.

## 6. Audio y video — sin dependencias nuevas

Ni ExoPlayer ni Media3 están en las dependencias del proyecto (`coil` es
la única librería de medios). En vez de agregar una dependencia pesada
para una vista previa, se usó el mismo motor que ya usa
`AudioPreviewPlayer` (`engine/audio/AudioPreviewPlayer.kt`):
`android.media.MediaPlayer` para audio, `android.widget.VideoView` +
`MediaController` (nativos de Android) para video. El `prepare()`
síncrono de `MediaPlayer` (I/O real de disco) corre en
`Dispatchers.IO` dentro de un `LaunchedEffect`, no en el
`DisposableEffect` que arma/libera el objeto — evitar bloquear el hilo
principal de composición con I/O, incluso para archivos chicos.

Reproductor propio con play/pausa, `Slider` arrastrable (con estado
`isDragging` para no pelear la posición visual contra el `MediaPlayer`
mientras el usuario arrastra) y tiempo transcurrido/total — no solo
"reproducir automáticamente sin control".

## 7. "Compartir"/"Abrir con…" — por qué hace falta copiar el archivo primero

El archivo de un proyecto en la papelera vive en almacenamiento interno
privado (`filesDir/projects_trash/<id>/…`), que el `FileProvider` de la
app **no expone** — `file_paths.xml` solo declara
`getExternalFilesDir()/exports/` y `cache/camera_captures/`. Pedirle a
`FileProvider.getUriForFile` un `content://` sobre un archivo fuera de
esas rutas declaradas lanza `IllegalArgumentException` en tiempo de
ejecución.

En vez de ampliar `file_paths.xml` para exponer `filesDir/` entero (una
regresión de seguridad real: expondría cualquier archivo interno de la
app al `FileProvider`, no solo el que el usuario tocó), se replicó
exactamente el patrón que **ya existe** en el proyecto para este mismo
problema — `ProjectStorage.exportProjectZip` (FASE de exportar `.olycs`)
copia primero a `exports/`, la única carpeta ya declarada:

`ProjectStorage.prepareFileForSharing(sourceFile, displayName)` copia el
archivo a `getExternalFilesDir()/exports/shared/` (limpiando esa
subcarpeta en cada llamada — es una zona de paso de un único archivo a
la vez, no un historial acumulable) y devuelve el `File` ya en una
ubicación compartible. `FilePreviewDialog` arma el Intent
(`ACTION_SEND`/`ACTION_VIEW` + `FileProvider.getUriForFile` +
`FLAG_GRANT_READ_URI_PERMISSION` + `Intent.createChooser`) igual que
`MainActivity.onShareProject` ya hacía para los `.olycs` — mismo criterio
en toda la app, no una implementación paralela.

## 8. Encabezado y navegación

`FilePreviewTopBar`: ícono "←" (`ic_back`, mismo ícono que usa
`TrashBrowserHeader` para "volver un nivel") vuelve al navegador de
archivos — semántica consistente con el resto de la papelera, donde "←"
retrocede un nivel y "X" (usado en la lista raíz y en el navegador) cierra
todo. El visor se abre como un `Dialog` de pantalla completa más (mismo
criterio que `ProjectsTrashScreen`/`ErrorLogScreen`); el back del sistema
operativo lo cierra solo a él (Compose `Dialog` intercepta el evento de
atrás antes de que llegue al `BackHandler` de la pantalla de abajo — no
hizo falta tocar ese `BackHandler`).

## 9. Video — feedback de error explícito (paridad con audio)

`VideoView.setOnErrorListener` originalmente solo devolvía `true` para
suprimir el diálogo de error nativo de Android, pero no dejaba ningún
mensaje propio — un video corrupto o con códec no soportado quedaba en
una pantalla en blanco sin explicación, a diferencia de
`AudioPreviewContent` (que sí tiene su `loadError`). Corregido:
`VideoPreviewContent` ahora tiene su propio estado `loadError` y
muestra el mismo tipo de mensaje ("No se pudo reproducir este archivo de
video") que ya usa el reproductor de audio — mismo criterio en ambos.

## 10. Tests unitarios nuevos

Tres archivos JVM (sin Robolectric, mismo criterio que
`AlignmentGuidesTest`/`AtomicFileReplaceTest` — funciones puras marcadas
`internal` a propósito para ser testeables desde `app/src/test/`):

- **`FilePreviewClassificationTest`** — tabla completa de
  `classifyFilePreviewKind` (las cinco categorías reconocidas +
  mayúsculas, sin extensión, extensión compuesta, nombre vacío).
- **`JsonSyntaxHighlightTest`** — `highlightJson`: el caso crítico es que
  el texto reconstruido sea carácter por carácter idéntico al de entrada
  (espacios/indentación incluidos), más clave-vs-valor, números
  negativos/decimales como un único token, strings que contienen la
  palabra `true` sin confundirse con el literal, comillas escapadas sin
  cortar el token antes de tiempo. Validado además, fuera de Kotlin, con
  una réplica del mismo algoritmo en Python contra los mismos 9 casos
  (mismo tokenizador por regex) para confirmar el diseño antes de
  depender únicamente de la revisión manual del código Kotlin — ver el
  historial de esta sesión.
- **`TextPreviewLoadingTest`** — `loadTextPreview`: texto plano tal cual,
  JSON compacto re-formateado con pretty-print, JSON corrupto cayendo con
  gracia al crudo con aviso, truncado por tamaño (incluido el caso JSON
  truncado, que no debe ni intentar parsear), archivo vacío, JSON
  anidado.

## 11. R1 — Bug real encontrado en CI: `withStyle` sin importar

Primera corrida real de `./gradlew` (GitHub Actions, `Android CI Build
#523`, posterior a la entrega de esta fase) encontró lo que ninguna
revisión manual en este entorno podía atrapar: `:app:compileDebugKotlin`
falló con

```
FilePreviewDialog.kt:509:17 Unresolved reference 'withStyle'
```

Causa raíz: `withStyle` **no es un miembro** de `AnnotatedString.Builder`
(a diferencia de `append`, que sí lo es) — es una función de extensión de
nivel superior (`androidx.compose.ui.text.withStyle`), y las funciones de
extensión de otro paquete necesitan importarse explícitamente aunque el
receptor implícito (`AnnotatedString.Builder`, provisto por
`buildAnnotatedString { … }`) esté en alcance. La ausencia de un
`import androidx.compose.ui.text.withStyle` bastó para que compilara
"a ojo" pero no con el compilador real.

Corrección de una línea: `import androidx.compose.ui.text.withStyle`
agregado a `FilePreviewDialog.kt`. Tras encontrar este bug se hizo una
segunda pasada, más rigurosa, sobre el resto del archivo: se verificó
uno por uno que cada recurso `R.drawable.*` referenciado existe
realmente en `res/drawable/` (los nueve íconos usados), y que cada
parámetro de `SliderDefaults.colors`/`ButtonDefaults.buttonColors`/
`ButtonDefaults.outlinedButtonColors` coincide exactamente con el que ya
usa el resto del proyecto (`EditorScreen.kt`, `LayerDialogs.kt`,
`ProjectsTrashScreen.kt`, `ErrorLogScreen.kt`) — mismos nombres de
parámetro, misma versión de Material3, cero improvisación. También se
confirmó que el uso de `internal` para las funciones puras testeables
(`highlightJson`, `loadTextPreview`, los colores del resaltado JSON) es
el mismo patrón ya probado en producción por este proyecto —
`clampedHandleSlots` (`EditorScreen.kt`) es `internal` y ya se testea
hoy desde `ClampedHandleSlotsTest.kt`, mismo paquete, mismo mecanismo.

**Archivo:** `FilePreviewDialog.kt`.

## Checklist de calidad

- [x] Ninguna extensión de archivo termina en una fila muerta —
      `UNSUPPORTED` siempre ofrece "Abrir con…"/"Compartir".
- [x] Lectura de texto/JSON en `Dispatchers.IO`, nunca en el hilo de
      composición; `MediaPlayer.prepare()` también movido a `IO`.
- [x] Límite explícito de tamaño para vista previa de texto/JSON (2 MB),
      aplicado **durante la lectura del stream**, no después de cargar
      el archivo entero a memoria — ver sección 5/punto 7 de la tabla.
- [x] "Compartir"/"Abrir con…" reusa el mecanismo de `FileProvider` ya
      existente en el proyecto (`exports/`), sin ampliar qué expone
      `file_paths.xml`.
- [x] Sin dependencias nuevas — Coil (ya presente) para imágenes,
      `android.media.MediaPlayer`/`android.widget.VideoView` (ya en uso
      en el motor de audio) para audio/video, `kotlinx.serialization.json`
      (ya presente) para el pretty-print de JSON.
- [x] Audio y video tienen el mismo criterio de feedback de error
      (sección 9).
- [x] Balance de llaves/paréntesis verificado sobre cada archivo tocado.
- [x] Tests unitarios reales agregados para toda la lógica pura
      (clasificación, resaltado JSON, carga/truncado de texto) —
      sección 10.
- [ ] **No se compiló con Gradle en esta sesión** (entorno de trabajo sin
      toolchain de Android ni acceso de red) — ver "Estado real /
      pendiente".
- [ ] Sin verificación en dispositivo real todavía — pendiente del lado
      del usuario, como el resto de las fases de este proyecto.

## Estado real / pendiente

- **No se compiló con Gradle en esta sesión, salvo la corrida real de CI
  que encontró el bug de la sección 11.** El resto del código (los otros
  tres archivos tocados —
  `ProjectStorage.kt`/`ProjectsTrashViewModel.kt`/`ProjectsTrashScreen.kt`
  — y los tests nuevos) todavía no pasó por un compilador real; se
  revisaron a mano con el mismo nivel de rigor que evitó repetir el
  mismo tipo de error (imports de extensión faltantes), pero **la
  próxima corrida de CI es la que confirma si quedó algo más.**
- **No hay ADR dedicado.** La decisión de copiar a `exports/shared/` en
  vez de ampliar `file_paths.xml` está registrada acá, no en
  `docs/adr/` — mismo criterio que la papelera de proyectos (FASE 12) en
  su momento.
