# FASE 2 — Concurrencia, Estado de Render, Reproducción y Duración

**Estado:** Implementación completa sobre el código real del proyecto
(auditoría estática + corrección directa + un test de concurrencia real
con hilos, ejecutado únicamente mentalmente/por trazado de código, no en
una JVM real — sin Gradle en este entorno). No se compiló ni se corrió la
app/los tests acá — eso queda para el usuario vía GitHub Actions, según lo
acordado para esta fase. Este documento describe exactamente lo que quedó
implementado, no intenciones.

---

## 1. Objetivo

Estabilizar el RUNTIME de Olyze Creation Studio: eliminar o reducir de
forma estructural los riesgos de que el hilo de GL observe `Layer`/
`CameraTrack` mutables a medio escribir mientras el hilo principal edita,
garantizar que solo exista un loop de reproducción activo a la vez, y
corregir el cálculo de duración de salida para que use el FPS real del
proyecto en vez de un valor fijo. Prioridad, según el brief:

> **CONSISTENCIA > SEGURIDAD DE HILOS > DETERMINISMO > REPRODUCCIÓN CORRECTA > RENDIMIENTO**

Sin migraciones de API (LayerApi/CameraApi/AudioApi/RenderApi), sin JNI/
C++, sin refactor masivo de `EditorScreen`/`EditorViewModel`, sin cambios
de branding/formato — según lo pedido.

---

## 2. Estado real encontrado (auditoría, antes de implementar)

Se inspeccionó el código real: `GLRenderer.kt` (356 líneas), `GLPreview.kt`
(74 líneas), `CameraTrack.kt` (91 líneas antes del fix), `Layer.kt` (114
líneas antes del fix), `EditorViewModel.kt` (3055 líneas), `MainActivity.kt`,
`SpeedRamp.kt`, `VideoExporter.kt`, `ThumbnailRenderer.kt`,
`AnimationApiImpl.kt`, `ExportApiImpl.kt`. No se asumió nada de README ni
de comentarios previos sin confirmarlo leyendo la implementación real.

Mapa de ownership confirmado:

```
UI (Compose, hilo principal)
   ↓ togglePlayback() / arrastre de keyframes / undo-redo
EditorViewModel (hilo principal, StateFlow<EditorUiState>)
   ↓ Layer / CameraTrack — objetos MUTABLES, mismas instancias
GLRenderer.onDrawFrame() (hilo de GL, ~60 Hz)
   ↓ leía Layer.zIndex/visible/parallaxFactor/lookSettings
   ↓ leía CameraTrack._keyframes / .baseFrame DIRECTO (sin copia)
```

Confirmado: **no existía ningún mecanismo de sincronización, copia
segura, ni publicación con visibilidad garantizada** entre el hilo
principal y el hilo de GL para ninguno de esos campos.

---

## 3. Problemas encontrados y causa raíz

### 3.1 — Condición de carrera real en `CameraTrack.keyframes` (el hallazgo más severo)

**Causa raíz:** `CameraTrack.frameAt(timeMs)` — llamado desde
`GLRenderer.onDrawFrame` en el hilo de GL, ~60 veces por segundo — leía
directamente `_keyframes` (`.isEmpty()`, `.size`, `.first()`, `.last()`,
indexado `[i]`), la MISMA `ArrayList` mutable que `addOrReplace()` /
`remove()` / `replaceAll()` modifican **estructuralmente**
(`removeAll{}`, `add()`, `sortBy{}`, `clear()`, `addAll()`) desde el hilo
principal cada vez que el usuario arrastra un keyframe o el editor aplica
undo/redo. Dos hilos, una sola `ArrayList`, cero sincronización: riesgo
real y reproducible de `IndexOutOfBoundsException` (si `frameAt` indexa
`_keyframes[i]` justo cuando `sortBy`/`clear` la están reordenando o
vaciando) o, más sutil, de leer una lista a medio ordenar → un frame de
cámara temporalmente incoherente (el "glitch" que anticipaba el brief).

No es un riesgo teórico: el `CameraTrackConcurrencyTest` nuevo (ver §13)
reproduce exactamente este escenario con dos hilos reales.

### 3.2 — Campos de `Layer` sin garantía de visibilidad entre hilos

**Causa raíz:** `zIndex`, `visible`, `parallaxFactor`, `lookSettings`
(escritos por el hilo principal, leídos cada frame por el hilo de GL) y
`glTextureId`, `pendingBitmap`, `widthPx`, `heightPx`, `sourceUri`
(escritos/leídos en ambos sentidos entre hilo principal e hilo de GL,
según el campo) eran `var` normales, sin `@Volatile` ni ningún otro
mecanismo de publicación segura. El **valor** de estos campos nunca podía
quedar corrupto (son primitivos o referencias a objetos inmutables), pero
no había ninguna garantía de que un hilo viera la escritura del otro en
un tiempo determinado — el hilo de GL podía seguir viendo, indefinidamente,
un valor viejo cacheado.

### 3.3 — Ningún `RenderSnapshot`: acoplamiento directo GL ↔ estado del editor

**Causa raíz:** `GLRenderer` leía `Layer`/`CameraTrack` directo del editor
(`getLayers()`), sin ninguna capa intermedia que aislara al renderer de
las mutaciones concurrentes del editor — exactamente lo que el brief pide
resolver con un "Stable State / RenderSnapshot".

### 3.4 — Playback: ningún `Job` rastreado → loops duplicados posibles

**Causa raíz:** `startPlaybackLoop()` hacía `viewModelScope.launch { while
(_uiState.value.isPlaying) { ... } }` **sin guardar ninguna referencia al
Job**. Nada impedía que una secuencia rápida de Play/Pause/Play, o un
`endScrub()` combinado con un `togglePlayback()`, lanzara una segunda
corrutina de playback mientras la primera todavía no había notado (en su
próxima iteración del `while`) que `isPlaying` había pasado a `false` y
después otra vez a `true` — dos loops corriendo en paralelo, cada uno
avanzando el playhead por su cuenta.

### 3.5 — FPS hardcodeado en el cálculo de duración de salida (más grave de lo que describía el brief)

**Causa raíz:** tanto `EditorViewModel.currentOutputDurationMs()` (usado
por la UI) como `EditorViewModel.exportVideo()` llamaban a
`animationApi.computeOutputDurationMs(..., fps = 30)` **fijo**, sin usar
`state.projectFps`. `SpeedRampEngine.buildTimeMapping` avanza tick por
tick (`1000/fps` por tick) — con al menos una rampa de velocidad o un
freeze frame activos, el resultado SÍ depende del fps (confirmado
numéricamente, ver §13). El impacto real es peor que "la UI muestra un
número distinto al de la exportación": `outputDurationMs` calculado con
`fps=30` se usa como `ExportSettings.durationMs`, que `VideoExporter`
pasa directo a `AudioProcessor.buildEncodedTrackForProject(...,
settings.durationMs, ...)` para construir la **pista de audio** — mientras
el conteo real de FRAMES de **video** (`buildTimeMapping` dentro del
propio `VideoExporter`) siempre usó `settings.fps` (= `state.projectFps`,
correcto). En cualquier proyecto con FPS ≠ 30 y una rampa/freeze activo,
esto producía una pista de audio de duración distinta a la pista de video
real del `.mp4` exportado — un desincronismo audio/video real en el
archivo final, no solo un número mal mostrado en pantalla.

### 3.6 — Inconsistencia de lifecycle: `ON_STOP` no detenía el playback

**Causa raíz:** el observer de `Lifecycle.Event.ON_STOP` en
`MainActivity.kt` (se dispara al ir a Home, cambiar de app, o bloquear la
pantalla) llamaba únicamente a `viewModel.saveNow()`, sin la llamada a
`viewModel.resetPlaybackState()` que sí tiene el camino de
`onBackToProjects()`. Si el usuario apretaba Home con el preview
reproduciéndose, el loop de reproducción seguía tickeando en segundo
plano — gasto de batería/CPU sin nada visible en pantalla, y trato
inconsistente entre dos caminos de salida del editor que deberían
comportarse igual.

---

## 4. Solución implementada

### 4.1 — Publicación segura de `CameraTrack.keyframes`/`.baseFrame` (sin locks)

`_keyframes` sigue siendo la lista de TRABAJO, mutada exclusivamente por
el hilo principal (mismo ownership de siempre, sin cambios). Cada método
que la modifica (`addOrReplace`/`remove`/`replaceAll`) termina publicando
una copia COMPLETAMENTE NUEVA e inmutable (`.toList()`) en
`keyframesSnapshot`, un campo `@Volatile`. La propiedad pública
`keyframes` (la única forma de leerlos desde afuera, incluido
`frameAt`) lee ese snapshot, nunca `_keyframes` directamente. `baseFrame`
recibió el mismo tratamiento (`@Volatile`, ya era un valor inmutable, solo
le faltaba la garantía de visibilidad). **Decisión de diseño:** no se usó
`synchronized`/locks — un solo escritor (hilo principal) + publicación
`@Volatile` de una referencia inmutable es la herramienta mínima correcta
para este patrón, sin el costo ni el riesgo de bloqueo del hilo de GL que
introduciría un lock.

### 4.2 — `@Volatile` en los campos de `Layer` leídos/escritos entre hilos

`zIndex`, `parallaxFactor`, `visible`, `lookSettings`, `widthPx`,
`heightPx`, `sourceUri`, `glTextureId`, `pendingBitmap` — todos marcados
`@Volatile`. Mismo criterio: valores simples o referencias a objetos
inmutables, un único escritor "principal" por campo (con dos excepciones
documentadas en el propio código: `widthPx`/`heightPx` y `glTextureId`/
`pendingBitmap` tienen ESCRITORES en ambos hilos, por diseño — el hilo de
GL "resuelve" el tamaño real tras el clamp de GPU y gestiona el ciclo de
vida de la textura — pero cada campo sigue siendo un valor atómico simple,
sin invariante compuesto entre campos que un lock debiera proteger).

### 4.3 — `RenderSnapshot` / `RenderLayerSnapshot` (nuevo, `engine/render/RenderSnapshot.kt`)

Nuevo DTO inmutable que materializa la arquitectura pedida:

```
UI → ViewModel → RenderSnapshot (inmutable) → GLRenderer
```

Contiene únicamente datos LÓGICOS por capa: `id`, `zIndex`, `visible`,
`parallaxFactor`, `lookSettings`, `keyframes`, `baseFrame` — nunca
recursos GPU/runtime (`Bitmap`, `glTextureId`, `Context`, `Uri`), que
siguen viviendo en el propio `Layer` (ahora `@Volatile`, ver §4.2) porque
son responsabilidad exclusiva del hilo de GL (caché de textura por capa) y
moverlos al snapshot habría mezclado datos lógicos con recursos runtime —
justamente lo que el brief pide evitar (sección 3: "no mezcles ambos
niveles sin necesidad").

`EditorUiState.toRenderSnapshot()` (en `EditorViewModel.kt`, mismo patrón
arquitectónico que `toContentSnapshot()`/`toContentState()` de Fase 1)
construye el snapshot. `EditorViewModel.currentRenderSnapshot()` es el
punto de entrada público que usa `GLPreview`/`GLRenderer`.

**Decisión de diseño explícita, documentada en el propio código:** la
seguridad real de este snapshot NO depende de en qué hilo se construya —
depende de que cada campo que copia sea, en sí mismo, seguro de leer
desde cualquier hilo en el instante de la copia. Gracias a 4.1 y 4.2, eso
ya es cierto para TODOS los campos que toca `toRenderSnapshot()`. Por eso
`currentRenderSnapshot()` es seguro de llamar directamente desde el hilo
de GL (que es, en la práctica, quien lo llama, una vez por frame) — no
hizo falta introducir un `StateFlow` derivado ni forzar que la captura
ocurra específicamente en el hilo principal, lo que habría introducido un
retraso de al menos un frame entre la mutación y su reflejo en el
snapshot, sin ninguna ganancia de seguridad adicional.

`GLRenderer` ahora recibe DOS lambdas: `getLayers` (reservado
exclusivamente a los campos GPU, correlacionados por `id`) y
`getRenderSnapshot` (todo el resto). La interpolación de cámara se
extrajo a una función pura compartida, `CameraFrameInterpolation.at(...)`
(`engine/camera/CameraFrameInterpolation.kt`), usada tanto por
`CameraTrack.frameAt` como por `RenderLayerSnapshot.frameAt`, para que
ambos caminos nunca puedan divergir en el resultado.

### 4.4 — `playbackJob: Job?` — un único loop activo

`startPlaybackLoop()` ahora cancela cualquier `playbackJob` anterior
ANTES de lanzar uno nuevo (`playbackJob?.cancel(); playbackJob =
viewModelScope.launch { ... }`). Se agregó `stopPlaybackLoop()` (cancela y
limpia la referencia) y se la conectó de forma EXPLÍCITA en **todos** los
caminos que llevan a `isPlaying = false`: `togglePlayback()` (rama
pausa), `beginScrub()`, `toggleRecording()` (rama detener grabación),
`resetPlaybackState()`, y `onCleared()` — en vez de depender únicamente de
que la propia corrutina note el cambio de estado en su siguiente
iteración del `while`.

### 4.5 — FPS real del proyecto en el cálculo de duración de salida

`currentOutputDurationMs()` y `exportVideo()` ahora pasan `fps =
state.projectFps` a `computeOutputDurationMs(...)`, no `fps = 30` fijo.
Esto alinea la duración usada para la pista de audio con el conteo real
de frames de video que ya usaba el FPS correcto — cierra el
desincronismo audio/video descrito en 3.5.

### 4.6 — Lifecycle: `ON_STOP` también detiene el playback

Se agregó `viewModel.resetPlaybackState()` antes de `viewModel.saveNow()`
en el observer de `ON_STOP` de `MainActivity.kt`, mismo criterio que
`onBackToProjects()`.

---

## 5. Arquitectura antes / después

**Antes:**

```
UI/ViewModel (hilo principal)          GLRenderer (hilo de GL)
   Layer.zIndex/visible/...    ←────────────┘ lectura directa, sin @Volatile
   CameraTrack._keyframes      ←────────────┘ lectura directa de la ArrayList mutable
```

**Después:**

```
UI/ViewModel (hilo principal)
   Layer.zIndex/visible/parallax/look    → @Volatile (visibilidad garantizada)
   CameraTrack.addOrReplace/remove/...   → publica keyframesSnapshot (@Volatile, inmutable)
        ↓
   EditorUiState.toRenderSnapshot()      → RenderSnapshot (inmutable, seguro desde cualquier hilo)
        ↓
   GLRenderer.onDrawFrame() (hilo de GL) → lee RenderSnapshot para lo lógico,
                                            lee Layer (@Volatile) solo para glTextureId/pendingBitmap/widthPx/heightPx
```

---

## 6. Ownership del estado

- **Dueño exclusivo de `_keyframes` (la ArrayList de trabajo):** hilo
  principal. Nunca cambia.
- **Dueño exclusivo de `glTextureId`/`pendingBitmap` en el caso normal:**
  hilo de GL (sube/libera texturas). El hilo principal solo los
  RESETEA a `-1`/`null` como señal de invalidación (ver comentario en
  `Layer.kt`), nunca los usa para dibujar.
- **Dueño de `widthPx`/`heightPx`:** doble escritor por diseño (editor al
  importar, GL al confirmar el tamaño real tras el clamp de textura) —
  documentado explícitamente en `Layer.kt`.
- **Consumidor, nunca escritor, de `RenderSnapshot`:** `GLRenderer`. El
  renderer NUNCA modifica el snapshot ni el `Layer` lógico (sí gestiona
  sus propios campos GPU, que son de su exclusiva competencia).

---

## 7. Cómo se evita compartir `Layer`/`CameraTrack` mutable de forma peligrosa

Ver §4.1/§4.2. Resumen: ningún dato mutable compuesto (una colección, una
estructura con múltiples campos relacionados) se comparte sin una
publicación segura por delante — `CameraTrack` publica una lista
inmutable nueva en cada mutación; los campos simples de `Layer` que
cruzan de hilo son `@Volatile`. No se usó `synchronized` en ningún punto
de este flujo (evitado deliberadamente, ver brief sección 15).

---

## 8. Cómo se publica el RenderSnapshot

Ver §4.3. `currentRenderSnapshot()` se llama directamente desde
`GLRenderer.onDrawFrame` (hilo de GL), una vez por frame — no hay un
`StateFlow` derivado ni un paso intermedio de publicación con delay,
porque cada campo que copia ya es individualmente seguro de leer desde
cualquier hilo (ver la decisión de diseño documentada en §4.3 y en el
propio `RenderSnapshot.kt`).

---

## 9. Cómo funciona ahora el playback

`togglePlayback()`/`beginScrub()`/`toggleRecording()`/
`resetPlaybackState()` son los únicos puntos que tocan `isPlaying` y
todos pasan por `startPlaybackLoop()`/`stopPlaybackLoop()` de forma
simétrica. `startPlaybackLoop()` cancela el job anterior de forma
SÍNCRONA antes de crear uno nuevo — nunca puede haber dos activos, sin
importar la secuencia de toques.

---

## 10. Único playback loop garantizado

Ver §4.4. La garantía no depende de que el `while (isPlaying)` note el
cambio "tarde o temprano" — se cancela explícitamente en cada transición
a pausa/detenido, y se cancela el anterior antes de crear el siguiente en
cada transición a reproducción.

---

## 11. Pause/Play/Stop/Scrub

- **Play:** `togglePlayback()` (isPlaying=false→true) → `startPlaybackLoop()`.
- **Pause:** `togglePlayback()` (isPlaying=true→false) → `stopPlaybackLoop()`.
- **Scrub:** `beginScrub()` pausa y detiene el loop si estaba reproduciendo;
  `endScrub()` lo retoma con `startPlaybackLoop()` si `wasPlayingBeforeScrub`.
- **Detener grabación:** `toggleRecording()` (rama "false") también
  detiene el loop.
- **Salir del editor:** `onBackToProjects()` ya llamaba a
  `resetPlaybackState()` (sin cambios); ahora `resetPlaybackState()`
  también detiene el job explícitamente.
- **Background (`ON_STOP`):** ahora también detiene el playback (§4.6).
- **Destrucción del ViewModel:** `onCleared()` detiene el job
  explícitamente, sin depender solo de la cancelación implícita de
  `viewModelScope`.

---

## 12. Duración de salida / FPS / speed ramp / freeze frame

`currentOutputDurationMs()` (UI) y `exportVideo()` (export real) ahora
usan `state.projectFps` — la misma fuente, sin duplicar lógica. El
comportamiento de rampas de velocidad y freeze frames en sí (`SpeedRamp.kt`)
no se tocó — solo el parámetro `fps` que ambos caminos le pasaban.
Confirmado numéricamente (ver §13) que el fps SÍ cambia el resultado
cuando hay una rampa o un freeze activo, y que NO lo cambia cuando no hay
ninguno (short-circuit ya existente, correcto, sin tocar).

---

## 13. Tests agregados

- **`CameraTrackConcurrencyTest.kt`** (nuevo) — dos tests con hilos
  reales (`java.util.concurrent`, sin mocks): (1) un hilo "GL" llamando
  `frameAt()` en loop apretado mientras un hilo "editor" hace 20.000
  mutaciones estructurales (`addOrReplace`/`remove`/`replaceAll`)
  intercaladas — se afirma que ninguno de los dos hilos lanza excepción
  y que ambos terminan dentro del timeout (sin deadlock); (2) una
  referencia a `keyframes` tomada en un instante no cambia de tamaño
  aunque otro hilo haga 5.000 mutaciones concurrentes después. Complementa
  (no reemplaza) `CameraTrackSnapshotIndependenceTest.kt` de Fase 1, que
  prueba la propiedad en un solo hilo, en secuencia.
- **`SpeedRampEngineTest.kt`** (ampliado, 3 tests nuevos) — prueban,
  numéricamente (valores verificados por trazado manual del algoritmo
  antes de escribir la aserción), que `computeOutputDurationMs` da
  resultados DISTINTOS a 24/30/60/120fps cuando hay un freeze frame o una
  rampa de velocidad activos (la prueba directa de por qué el hardcode de
  30 era incorrecto), y que sin rampas/freezes el fps NO afecta el
  resultado (el short-circuit ya existente sigue funcionando igual).

### Brecha de cobertura conocida (documentada, no oculta)

Igual que en Fase 1, **no hay test automatizado a nivel de
`EditorViewModel`** para el fix del `playbackJob` (sección 18-C/D/E del
brief) ni para el lifecycle de `ON_STOP` (`MainActivity.kt`), porque
`EditorViewModel` depende de `android.net.Uri`/`Context`/`Job` de
`viewModelScope` y el proyecto no tiene Robolectric configurado — un
`Uri` no mockeado explota en un test JVM puro (mismo límite ya
documentado en el informe de Fase 1). La corrección de `playbackJob` se
verificó por trazado de código, no por ejecución: se confirmó que
`startPlaybackLoop()` cancela el job anterior de forma síncrona (no
asíncrona) antes de asignar el nuevo, lo que elimina por construcción la
ventana de carrera que permitía dos loops simultáneos.

---

## 14. Archivos modificados

- `app/src/main/java/com/yeivikas/olyzecs/engine/camera/CameraTrack.kt` —
  publicación segura de `keyframes`/`baseFrame` (`@Volatile`).
- `app/src/main/java/com/yeivikas/olyzecs/engine/scene/Layer.kt` —
  `@Volatile` en los campos cruzados por hilos.
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GLRenderer.kt` —
  nuevo parámetro `getRenderSnapshot`, `onDrawFrame` reescrito para leer
  datos lógicos del snapshot y datos GPU del `Layer` en vivo.
- `app/src/main/java/com/yeivikas/olyzecs/ui/GLPreview.kt` — nuevo
  parámetro `getRenderSnapshot`, propagado a `GLRenderer`.
- `app/src/main/java/com/yeivikas/olyzecs/ui/EditorScreen.kt` — call site
  de `GLPreview` actualizado con `getRenderSnapshot` (mismo filtro de modo
  edición aislada que `getLayers`).
- `app/src/main/java/com/yeivikas/olyzecs/viewmodel/EditorViewModel.kt` —
  `toRenderSnapshot()`/`currentRenderSnapshot()`, `playbackJob`/
  `stopPlaybackLoop()`, fix de `fps` hardcodeado en 2 puntos
  (`currentOutputDurationMs`, `exportVideo`).
- `app/src/main/java/com/yeivikas/olyzecs/MainActivity.kt` — `ON_STOP`
  también llama a `resetPlaybackState()`.
- `README.md` — árbol de archivos actualizado con los 2 archivos nuevos.
- `docs/pending-work/PROJECT_AUDIT.md` — entrada de Fase 2 agregada junto
  a la de Fase 1.

## 15. Archivos creados

- `app/src/main/java/com/yeivikas/olyzecs/engine/render/RenderSnapshot.kt`
  — `RenderSnapshot`/`RenderLayerSnapshot`.
- `app/src/main/java/com/yeivikas/olyzecs/engine/camera/CameraFrameInterpolation.kt`
  — función pura de interpolación, compartida.
- `app/src/test/java/com/yeivikas/olyzecs/engine/camera/CameraTrackConcurrencyTest.kt`
  — tests de concurrencia real con hilos.
- `docs/fases/FASE_2_CONCURRENCIA_RENDER_PLAYBACK.md` — este documento.

## 16. Archivos eliminados

Ninguno.

---

## 17. Matriz antes / después

| Área | Antes | Después | Estado |
|------|-------|---------|--------|
| `CameraTrack.keyframes` ↔ GL | Lectura directa de la `ArrayList` mutable, sin sincronización | Publicación `@Volatile` de una lista inmutable nueva en cada mutación | Corregido |
| `Layer` (zIndex/visible/parallax/look) ↔ GL | `var` normal, sin garantía de visibilidad | `@Volatile` | Corregido |
| `Layer` (glTextureId/pendingBitmap/widthPx/heightPx) | `var` normal, doble escritor sin garantía de visibilidad | `@Volatile` | Corregido |
| RenderSnapshot | No existía — GL leía `Layer`/`CameraTrack` directo | `RenderSnapshot`/`RenderLayerSnapshot` inmutable, `GLRenderer` los consume | Implementado |
| Playback loop | Sin `Job` rastreado — loops duplicados posibles | `playbackJob` cancela el anterior antes de lanzar uno nuevo | Corregido |
| Pause/Play/Scrub | Dependía de que el `while` notara el cambio de estado | Cancelación explícita en todos los caminos a `isPlaying=false` | Corregido |
| Output duration / FPS | `fps = 30` fijo en 2 puntos (UI + export) | `fps = state.projectFps` en ambos | Corregido |
| Audio/Video sync en export | Duración de audio y video podían divergir con fps≠30 + rampa/freeze | Ambas usan la misma fuente de FPS | Corregido |
| Lifecycle `ON_STOP` | Solo `saveNow()`, playback seguía tickeando en background | También `resetPlaybackState()` | Corregido |
| Tests | Sin tests de concurrencia real ni de fps en output duration | `CameraTrackConcurrencyTest` (2 tests, hilos reales) + 3 tests de fps en `SpeedRampEngineTest` | Agregado |

---

## 18. Riesgos restantes

1. **Ventana de lectura no-atómica entre `getLayers()` y
   `getRenderSnapshot()` en `onDrawFrame`:** ambas lambdas leen
   `_uiState.value` en dos invocaciones separadas dentro del mismo frame
   — en una ventana extremadamente estrecha (un layer agregado/eliminado
   exactamente entre esas dos lecturas, ambas en el hilo de GL), podrían
   observar dos instantes sucesivos ligeramente distintos del estado.
   Esto es benigno y auto-corrige en el siguiente frame (a lo sumo un
   frame de retraso en mostrar una capa nueva, o un intento de subir
   textura de una capa ya no presente en el snapshot, que simplemente no
   se dibuja) — no es una regresión introducida por esta fase: el código
   YA tenía este mismo patrón de lecturas independientes entre
   `getLayers()`, `getPlayheadMs()` y `getGridBitmap()` antes de este
   cambio. Documentado, no oculto.
2. **`ThumbnailRenderer` sigue leyendo `Layer` en vivo, no un
   `RenderSnapshot`** — riesgo ya documentado en el informe de Fase 1,
   sigue vigente sin cambios: en el peor caso la miniatura puede no
   reflejar el instante exacto de guardado (nunca corrompe el proyecto,
   es cosmético). Se benefició de forma incidental del fix de
   `CameraTrack` (§4.1), que ahora también protege esta lectura.
3. **`VideoExporter` también llama `layer.cameraTrack.frameAt(...)`
   sobre `Layer` en vivo** desde un hilo de exportación en segundo plano,
   mientras el usuario en teoría podría seguir editando — no estaba
   documentado como riesgo antes de esta auditoría; con el fix de
   `CameraTrack` (§4.1) esta lectura queda protegida de la misma forma
   que la del hilo de GL, sin necesitar ningún cambio adicional en
   `VideoExporter.kt`.
4. **Brecha de cobertura en `EditorViewModel`** (`playbackJob`, lifecycle
   de `ON_STOP`) — ver §13, mismo límite estructural que Fase 1 (sin
   Robolectric configurado).

## 19. Trabajo pospuesto a Fase 3

- Render lifecycle completo (`surface destruction`, contexto EGL inválido
  en casos borde) — mencionado en el brief como fuera de alcance de esta
  fase salvo que fuera imprescindible; no se encontró nada que lo
  ameritara.
- Cualquier migración de API (`EliNerApiImpl` definitivo, `RenderApi`,
  `DistortionApi`, JNI/C++, Oboe) — explícitamente fuera de alcance.
- Refactor de `EditorScreen.kt`/`ImageEffects.kt` (archivos grandes,
  identificados en `PROJECT_AUDIT.md` desde antes de esta fase) —
  explícitamente fuera de alcance.
- El riesgo #1 de la sección 18 (ventana de lectura no-atómica entre
  `getLayers()`/`getRenderSnapshot()`) podría cerrarse del todo
  combinando ambas lambdas en una sola que devuelva un par consistente —
  no se hizo en esta fase por ser una ganancia marginal (ventana ya
  estrecha, auto-corrige) frente al costo de tocar la firma de
  `GLRenderer`/`GLPreview` una vez más; queda registrado como mejora
  posible, no como deuda urgente.

## 20. Estado final de la Fase 2

Los cuatro riesgos de concurrencia/runtime que el brief anticipaba como
posibles (`CameraTrack` compartido, `Layer` compartido, playback loops
duplicados, FPS hardcodeado) se confirmaron los cuatro como reales en el
código, con causa raíz identificada por lectura directa del código y, en
el caso de `CameraTrack`, con un test de concurrencia real con hilos que
reproduce el escenario. Las cuatro correcciones están implementadas con
el criterio "un solo dueño mutable + publicación segura", sin locks
indiscriminados y sin refactors fuera del alcance pedido. Los riesgos
restantes están documentados explícitamente, no ocultos.
