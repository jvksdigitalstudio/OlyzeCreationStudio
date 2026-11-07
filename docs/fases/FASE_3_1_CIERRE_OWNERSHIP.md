# FASE 3.1 — Cierre y hardening de Render/GL lifecycle: GPU resource ownership + Bitmap ownership

> Continúa directamente sobre `docs/fases/FASE_3_RENDER_GL_LIFECYCLE.md`.
> Esta fase NO reabre nada de esa fase salvo las dos afirmaciones de
> ownership que la auditoría de cierre encontró falsas (§15/§19 de ese
> documento, ya corregidas con un puntero a este informe). No se tocó
> `RenderSnapshot`/playback (Fase 2), ni concurrencia general (Fase 1/2),
> ni se adelantó ninguna migración de API futura.

## 1. Problema original

El brief de esta fase pedía auditar el código real (no solo la
documentación) porque `Layer.glTextureId`/`Layer.pendingBitmap` estaban
documentados como "propiedad del GL thread", pero se sospechaba que
componentes de UI/ViewModel todavía podían escribirlos directamente.
También se pedía revisar el Bitmap de la textura de grid, por un
`bitmap.recycle()` potencialmente inseguro sobre una referencia que otro
componente podía seguir usando.

## 2. Por qué la implementación anterior no garantizaba ownership real

`@Volatile` (usado en ambos campos) solo garantiza **visibilidad**: que
un hilo eventualmente vea la escritura de otro. No impide que **dos
hilos distintos escriban el mismo campo**, ni convierte en atómica una
secuencia de varias operaciones. Documentar un campo como "GL thread
ownership" mientras sigue siendo un `var` público no crea ninguna
garantía real — es una convención que el compilador no puede hacer
cumplir y que, en este proyecto, el propio código no respetaba.

## 3. Qué se encontró en el código (auditoría real, Paso 0)

Búsqueda completa de `glTextureId`, `pendingBitmap`, `recycle()`,
`getGridBitmap()`/`updateGridTextureIfNeeded()` y toda la cadena de
`Layer`/`EditorViewModel`/`GLRenderer`/`LayerDrawer`/`GridTextureCacheState`/
`EditorScreen` (rasterización de la cuadrícula):

### 3.1 — BUG REAL: `Layer.glTextureId` escrito directamente por el hilo principal

`Layer.glTextureId` era un `@Volatile var Int` que **sí** escribía
`GLRenderer` (hilo de GL, en `uploadTextureIfNeeded`/`onSurfaceCreated`),
pero que `EditorViewModel` (hilo principal) **también** escribía
directamente, sin pasar nunca por el hilo de GL, en:

- `LayerContentState.applyTo(...)` (usado por `discardChangesAndExit`) —
  `layer.glTextureId = -1` al detectar que `sourceUri` cambió.
- `restoreSnapshot(...)` (undo/redo) — mismo patrón, mismo motivo.
- `replaceLayer(...)`/`replaceLayers(...)` — transferían
  `updated.glTextureId = old.glTextureId` en cada `.copy()`, para que el
  handle GL "sobreviviera" al hecho de que `.copy()` de Kotlin resetea a
  su valor por defecto cualquier propiedad declarada fuera del
  constructor primario.
- Un bloque de aplicación de degradado a varias capas marcadas
  ("Multicolor") con el mismo patrón de transferencia manual.

Es decir: la documentación de Fase 3 (§15/§19 de ese informe) afirmaba
"GL thread ownership", pero el código real permitía —y de hecho
practicaba regularmente— escrituras desde el hilo principal.

### 3.2 — BUG REAL: `Layer.pendingBitmap` con una ventana de *lost update*

`Layer.pendingBitmap` era un `@Volatile var Bitmap?`.
`GLRenderer.uploadTextureIfNeeded` lo consumía así:

```kotlin
val original = layer.pendingBitmap   // (1) lee
// ... clampForTexture, uploadTexture ...
layer.pendingBitmap = null           // (2) limpia, en un finally SEPARADO
```

Son dos operaciones distintas, no una. Si el hilo principal publicaba un
bitmap **nuevo** justo entre (1) y (2) — posible, por ejemplo, si el
usuario suelta el dedo de la rueda de color justo cuando GL está en medio
de subir el frame anterior — el paso (2) pisaba ese bitmap nuevo con
`null` sin que nadie lo hubiera consumido nunca: un *lost update* real,
que dejaba la capa sin actualizar en pantalla hasta el próximo evento que
volviera a marcarla como pendiente.

### 3.3 — BUG REAL (el más serio): `GLRenderer` reciclaba un Bitmap que **Compose seguía considerando suyo**

`EditorScreen` rasteriza la cuadrícula de composición a un
`android.graphics.Bitmap` dentro de un `remember(...)` con keys puntuales
(forma, columnas/filas, color, grosor, tamaño de lienzo) — **deliberadamente**
para reutilizar la MISMA instancia mientras nada de eso cambie, y así no
volver a rasterizarla en cada uno de los ~60 frames por segundo. Ese
mismo objeto se pasaba a `GLRenderer` vía `getGridBitmap()`.

`GLRenderer.updateGridTextureIfNeeded()`, tras subir la textura, llamaba
a `bitmap.recycle()` — el mismo criterio (correcto para el caso de una
capa) que usa `uploadTextureIfNeeded` para los bitmaps de capa, pero
**incorrecto** acá: el dueño real de esa instancia es Compose, no
`GLRenderer`.

**Bug concreto reproducible:** al recrearse el contexto EGL (volver de
segundo plano, reabrir un proyecto desde "Mis proyectos" — el mismo
escenario que ya cubría la Fase 3 para shader/grid-texture-cache),
`updateGridTextureIfNeeded()` vuelve a ejecutar con el **mismo objeto**
`bitmap` que entrega `getGridBitmap()` (el `remember` de Compose no
cambió) — pero esa instancia **ya había sido reciclada** la vez anterior.
`drawer.uploadTexture(bitmap)` sobre un bitmap reciclado lanza
`IllegalStateException: Can't use a recycled bitmap`, dentro del hilo de
GL — un crash real, no solo un problema teórico de estilo. Es
exactamente el escenario que anticipa la sección "Context loss y grid"
del brief: el Bitmap CPU debe seguir disponible para reconstruir la
textura tras una recreación de contexto.

### 3.4 — Hallazgo adicional (memory leak preexistente, dentro del alcance de "no crear memory leaks")

`EditorViewModel.removeLayer(...)` simplemente filtra la capa de la
lista de `_uiState`. Nunca había, hasta esta fase, ningún punto donde se
llamara `glDeleteTextures` para la textura GPU que esa capa tenía
subida — el objeto `Layer` se vuelve basura para el GC, pero el GC no
sabe nada de recursos GPU. Cada capa eliminada durante una sesión de
edición dejaba una textura huérfana en la GPU hasta que el contexto EGL
se destruía por completo (cerrar la app, o volver a "Mis proyectos").

### 3.5 — Revisado y descartado como correcto (sin cambios)

- `GpuHandle`/`GpuContextGeneration` (Fase 3): revisados a fondo — sí
  garantizan invalidación por generación de forma correcta. Se
  **reutiliza el mismo patrón conceptual** (aunque no la clase en sí,
  ver §7) para el registro de texturas de capa.
- `GridTextureCacheState`: revisado — la lógica de reconciliación
  (identidad de bitmap O generación de contexto) sigue siendo correcta;
  solo se corrigió el bug de `recycle()` en el método que la usa
  (`GLRenderer.updateGridTextureIfNeeded`), no la clase en sí.
- `RenderSnapshot`: confirmado que no contiene ningún handle
  GPU/GL/EGL — sigue siendo puramente datos de render (frame, look,
  parallax, zIndex, visibilidad). Sin cambios.

## 4. Qué se cambió

### 4.1 — `Layer.glTextureId` eliminado por completo de `Layer`

El campo **ya no existe** como propiedad de `Layer`. El texture id GL
vive exclusivamente en `GLRenderer.layerTextures: MutableMap<String, Int>`
— un `private val` de esa clase, indexado por `Layer.id` (estable a
través de cualquier `.copy()`, porque `id` no es un parámetro que las
transformaciones de `EditorViewModel` toquen nunca). Ningún código fuera
de `GLRenderer` tiene, siquiera, una referencia a un texture id real —
la garantía de "GL thread ownership" queda dada por construcción
(scoping de Kotlin: el mapa es `private`), no por disciplina ni por un
modificador como `@Volatile`. Esto es, literalmente, el "GPU RESOURCE
REGISTRY / CACHE" que describe la arquitectura preferida del brief.

Como el mapa está indexado por `id` (no por instancia de `Layer`), un
`.copy()` con `preserveRenderState = true` **ya no necesita ningún
traspaso manual** del texture id — simplemente "sigue encontrando sola"
la entrada existente. Esto elimina, de raíz, toda una clase de bugs de
"se me olvidó trasladar el campo en este `.copy()` nuevo" que existía
antes (ver el historial de comentarios "BUG REAL" ya presentes en
`EditorViewModel.replaceLayer`/el bloque de degradado multicolor).

### 4.2 — `Layer.requestTextureInvalidation()` / `consumeTextureInvalidationRequest()`

El hilo principal sigue necesitando poder decir "esta capa cambió de
fuente, olvidate de la textura vieja" (reemplazar imagen, deshacer/
rehacer un cambio de `sourceUri`, forzar recarga con
`preserveRenderState = false`). Para eso se agregó
`TextureInvalidationRequest` — una bandera atómica de un solo pedido
(`AtomicBoolean.getAndSet`), envuelta por dos métodos en `Layer`:

- `requestTextureInvalidation()` — productor, cualquier hilo: deja
  pedida una invalidación. **No borra nada.**
- `consumeTextureInvalidationRequest()` — consumidor único, pensado
  para el hilo de GL: toma el pedido de forma atómica. Solo
  `GLRenderer.onDrawFrame` lo llama, y es el único lugar donde se decide
  liberar (`glDeleteTextures`) el recurso real.

El hilo principal nunca vuelve a tocar un handle GPU — solo puede
*pedir*.

### 4.3 — `Layer.pendingBitmap` migrado a `SingleResourceHandoff<Bitmap>`

Nueva clase genérica `SingleResourceHandoff<T>` (en
`engine/render/SingleResourceHandoff.kt`), que envuelve un
`AtomicReference<T?>`:

- `publish(value)` — productor, cualquier hilo.
- `takeForConsumption()` — consumidor único: `getAndSet(null)`, **una
  sola operación atómica** — sin la ventana de "leer, luego limpiar en
  un paso separado" que tenía el `@Volatile var` original. Resuelve el
  *lost update* de §3.2 de raíz, no con más sincronización sino con la
  primitiva atómica correcta para un handoff de un solo slot.
- `peek()` — lectura sin consumir, para el único caso legítimo que la
  necesita (`onSurfaceCreated`, decidir si hace falta re-decodificar).

Es genérica (no depende de `android.graphics.Bitmap`) a propósito, para
poder testearla con JUnit puro — ver §15.

### 4.4 — Grid bitmap: GL deja de reciclarlo (estrategia A del brief)

`GLRenderer.updateGridTextureIfNeeded()` ya no llama a `bitmap.recycle()`
después de subir la textura. Compose/UI sigue siendo el dueño real de la
instancia (la crea y la conserva vía `remember`); GL solo la **usa** para
subir la textura. No hay ninguna copia extra de por medio — sigue siendo
la misma única instancia, solo que ya no se destruye del lado
equivocado. Esto es exactamente la opción A que describe el brief
("UI/Compose owns Bitmap → GL solo lo utiliza → GL NO hace recycle()"),
elegida porque:

- El bitmap de la cuadrícula es chico (una guía vectorial rasterizada,
  no una foto de cámara) — el ahorro de memoria de reciclarlo
  inmediatamente es marginal comparado con el riesgo real de un crash.
- Ya existe un mecanismo de reemplazo correcto para cuando el bitmap
  SÍ cambia de verdad (identidad distinta): `GridTextureCacheState`
  detecta el cambio y `updateGridTextureIfNeeded` libera la textura GPU
  vieja (`drawer.deleteTexture`) antes de subir la nueva — lo único que
  cambió es que ya no se destruye, además, el Bitmap CPU de origen.

### 4.5 — Poda de texturas de capas eliminadas (corrige el leak de §3.4)

Al principio de cada `onDrawFrame`, antes de procesar cualquier capa, se
compara el conjunto de ids en `layerTextures` contra el conjunto de ids
de capas realmente vivas (`getLayers()`); cualquier entrada sin capa
viva correspondiente se libera explícitamente (`drawer.deleteTexture`) y
se quita del mapa. Esto corrige el leak de GPU de §3.4 sin agregar
ningún estado nuevo de vida indefinida (es una comparación de
conjuntos, no una cola que crece).

## 5. Ownership anterior

| Recurso | Ownership documentado | Ownership real |
|---|---|---|
| `Layer.glTextureId` | "GL thread" | Hilo de GL Y hilo principal (ambos escribían) |
| `Layer.pendingBitmap` | "GL thread consume, hilo principal publica" | Correcto en el papel, pero con una ventana de carrera real en el consumo |
| Bitmap de la cuadrícula | (no documentado explícitamente) | GLRenderer se comportaba como dueño (`recycle()`), pero Compose seguía siendo el dueño real |

## 6. Ownership nuevo

Ver tabla obligatoria completa en §20 (tabla de ownership).

## 7. Lifecycle de `glTextureId`

Ya no es un campo — es una entrada de `GLRenderer.layerTextures[layer.id]`:

```
UI pide invalidación (Layer.requestTextureInvalidation)
        │  (bandera, no escribe nada)
        ▼
GL THREAD consume el pedido (onDrawFrame)
        │
        ├── había entrada → glDeleteTextures + se quita del mapa
        │
        ▼
GL THREAD sube nueva textura (uploadTextureIfNeeded, si hay pendingBitmap)
        │
        ▼
layerTextures[layer.id] = nuevo id
```

Se vacía por completo (`layerTextures.clear()`, sin llamar
`glDeleteTextures` — los ids pertenecen a un contexto ya destruido) en
cada `onSurfaceCreated()` real. Se poda por capa (`glDeleteTextures` +
`remove`) cuando una capa deja de existir en `getLayers()`.

## 8. Lifecycle de `pendingBitmap`

```
Productor (UI/ViewModel/ProjectStorage/LayerRepository, cualquier hilo)
        │
        │ .publish(bitmap)
        ▼
SingleResourceHandoff (un solo slot, el más reciente gana)
        │
        │ .takeForConsumption() — GL thread, atómico
        ▼
GL THREAD: clampForTexture → uploadTexture → layerTextures[id] = nuevo id
        │
        ▼
bitmap.recycle() (siempre, haya salido bien o mal la subida — es de un solo uso)
```

## 9. Lifecycle de Grid Bitmap

```
Compose (EditorScreen, remember con keys de la cuadrícula)
        │  crea/conserva el Bitmap — DUEÑO REAL
        │
        │ getGridBitmap() — el MISMO objeto mientras las keys no cambien
        ▼
GL THREAD: GridTextureCacheState decide si hace falta reconciliar
        │  (identidad cambió, O generación de contexto ya no es la vigente)
        ▼
GL THREAD sube la textura — USA el Bitmap, NUNCA lo recicla
        │
        ▼
El mismo Bitmap sigue disponible para el próximo ciclo
(incluida una recreación de contexto EGL)
```

## 10. Política de `recycle()`

- **Bitmap de capa (`pendingBitmap`)**: recurso de un solo uso, creado
  específicamente para esta subida — GL sigue siendo el dueño exclusivo
  una vez que lo toma vía `takeForConsumption()`, y lo recicla siempre
  (éxito o fallo) en el mismo método que lo consume.
- **Bitmap de la cuadrícula**: Compose/UI es el dueño — GL nunca lo
  recicla (§4.4).
- Regla general aplicada (criterio #12 del brief): no se agregó ningún
  `recycle()` nuevo "por las dudas"; el único cambio de política fue
  **remover** uno que estaba en el componente equivocado.

## 11. Context generation

Sin cambios respecto de la Fase 3 para la cuadrícula
(`GpuContextGeneration`/`GridTextureCacheState`, ya correctos, ver
§3.5). Para las texturas de capa, el registro `layerTextures` no
necesita una generación propia por handle: al vaciarse por completo
(`clear()`) en cada `onSurfaceCreated()` real, cualquier entrada que
sigue presente pertenece, por construcción, siempre al contexto EGL
vigente — un invariante más simple que tageear cada entrada con una
generación, y suficiente para lo que este recurso necesita (a
diferencia de la cuadrícula, que sí necesita comparar identidad de
bitmap Y generación por separado, porque su condición de "hace falta
resubir" es más rica).

## 12. Texture recreation

Cubierto por §7/§9. Un contexto EGL nuevo deja `layerTextures` vacío y
`GridTextureCacheState` invalidado — el próximo `onDrawFrame` reconstruye
ambos desde sus fuentes de verdad persistentes (`Layer.sourceUri` +
re-decode para capas; el mismo Bitmap de Compose, todavía vivo, para la
cuadrícula).

## 13. RenderSnapshot boundary

Confirmado sin cambios (§3.5): `RenderSnapshot`/`RenderLayerSnapshot` no
contienen, y nunca contuvieron, ningún handle GL/EGL/shader/VBO/FBO.

## 14. Thread ownership

- Todo acceso a `GLRenderer.layerTextures` ocurre exclusivamente dentro
  de los callbacks de `GLRenderer` (`onSurfaceCreated`/`onDrawFrame`) —
  el mismo hilo de GL, por contrato de `GLSurfaceView.Renderer`.
- `Layer.pendingBitmap`/`Layer.requestTextureInvalidation` son las
  ÚNICAS vías de comunicación cruzando hilos que le quedan al par
  UI↔GL para estos recursos — ambas explícitamente diseñadas para eso
  (un handoff de un solo slot, y una bandera de un solo pedido), en vez
  de un campo mutable compartido sin disciplina.
- No se agregó ningún acceso concurrente nuevo fuera de estos dos
  puntos.

## 15. Tests

Restricción real del proyecto sin cambios respecto de la Fase 3 (ver
§26 de ese informe): solo JUnit puro, sin Robolectric/MockK — no se
puede instanciar un `Layer` real en un test (necesita `android.net.Uri`)
ni ejercitar un `android.graphics.Bitmap` real. Mismo criterio ya
aplicado en Fase 3: extraer la lógica de decisión a clases puras y
testear esas.

Archivos de test nuevos, todos en
`app/src/test/java/com/yeivikas/olyzecs/engine/`:

| Archivo | Qué cubre | Escenarios del brief (§16) |
|---|---|---|
| `render/SingleResourceHandoffTest.kt` | Publish/take exactamente una vez, reemplazo sin cola, `peek()` no consume, y un test de concurrencia real (dos hilos) que verifica que nunca se corrompe/duplica un valor tomado | C (Pending Bitmap handoff) |
| `render/TextureInvalidationRequestTest.kt` | Un pedido se consume una sola vez; varios pedidos seguidos colapsan en uno | A (GPU handle ownership) |
| `render/LayerTextureRegistryScenarioTest.kt` | Reproduce, con las piezas reales de esta fase, el flujo completo del registro de `GLRenderer`: subida nueva, reemplazo con borrado de la vieja, poda de capas eliminadas (leak de §3.4), vaciado total en recreación de contexto, invalidación pedida por la UI, no-doble-subida de un bitmap ya consumido | A, B, D, F |
| `scene/LayerGpuOwnershipStructureTest.kt` | Test **estructural** (reflection, sin instanciar `Layer`): confirma que no existe ningún campo `glTextureId`, que `pendingBitmap` es un `SingleResourceHandoff` (no un `Bitmap` mutable), que no hay ningún setter público de texture id, y que la invalidación es solo pedido/consumo sin exponer estado interno | G (no shared mutable GPU state) |

No se agregó un test específico para el `recycle()` de la cuadrícula
(§3.3/D del brief) más allá de lo estructural: reproducir el bug real
(un `IllegalStateException` sobre un `Bitmap` reciclado) requiere un
`android.graphics.Bitmap` real, que no es instanciable en JUnit puro sin
Robolectric (mismo límite ya documentado en Fase 3 §26). El arreglo en
sí es la eliminación de una llamada (`bitmap.recycle()`) — no hay lógica
de decisión propia que extraer y testear de forma aislada; queda
documentado acá con el razonamiento completo en vez de un test
artificial que no ejercitaría el bug real.

## 16. Archivos modificados

- `app/src/main/java/com/yeivikas/olyzecs/engine/scene/Layer.kt` —
  `glTextureId` eliminado; `pendingBitmap` migrado a
  `SingleResourceHandoff<Bitmap>`; agregados
  `requestTextureInvalidation()`/`consumeTextureInvalidationRequest()`.
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GLRenderer.kt`
  — nuevo registro privado `layerTextures`; `onSurfaceCreated` vacía el
  registro en vez de resetear campo por campo; `onDrawFrame` poda capas
  eliminadas y atiende pedidos de invalidación; `uploadTextureIfNeeded`
  reescrito sobre el registro + `takeForConsumption()`;
  `updateGridTextureIfNeeded` ya no recicla el bitmap de la cuadrícula.
- `app/src/main/java/com/yeivikas/olyzecs/viewmodel/EditorViewModel.kt`
  — todas las escrituras directas de `glTextureId`/`pendingBitmap`
  (`applyTo`, `restoreSnapshot`, `replaceLayer`, `replaceLayers`, el
  bloque de degradado "Multicolor", y los productores de
  `replaceLayerImage`/`revertLayerEditSession`/`revertLayerToUri`)
  reemplazadas por `requestTextureInvalidation()`/`pendingBitmap.publish(...)`/
  `pendingBitmap.takeForConsumption()`.
- `app/src/main/java/com/yeivikas/olyzecs/data/ProjectStorage.kt` —
  productor de `pendingBitmap` migrado a `.publish(...)`.
- `app/src/main/java/com/yeivikas/olyzecs/data/LayerRepository.kt` —
  ídem.
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GridTextureCacheState.kt`
  — comentario de cabecera actualizado (referencia a cómo se invalidan
  ahora las texturas de capa).
- `app/src/main/java/com/yeivikas/olyzecs/api/model/LayerSnapshot.kt`,
  `app/src/main/java/com/yeivikas/olyzecs/api/scene/LayerApiImpl.kt`,
  `app/src/main/java/com/yeivikas/olyzecs/data/ProjectModels.kt` —
  comentarios de exclusión actualizados (`glTextureId` ya no es un campo
  que excluir; sigue siendo cierto que `pendingBitmap` se excluye).
- `docs/fases/FASE_3_RENDER_GL_LIFECYCLE.md` — banner de corrección +
  §3.2/§15/§19 corregidos para reflejar el ownership real encontrado.

## 17. Archivos creados

- `app/src/main/java/com/yeivikas/olyzecs/engine/render/SingleResourceHandoff.kt`
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/TextureInvalidationRequest.kt`
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/SingleResourceHandoffTest.kt`
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/TextureInvalidationRequestTest.kt`
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/LayerTextureRegistryScenarioTest.kt`
- `app/src/test/java/com/yeivikas/olyzecs/engine/scene/LayerGpuOwnershipStructureTest.kt`
- `docs/fases/FASE_3_1_CIERRE_OWNERSHIP.md` — este documento.

Ningún archivo fue eliminado.

## 18. Riesgos restantes

1. El bug del `recycle()` de la cuadrícula (§3.3) no tiene un test
   automatizado que reproduzca el `IllegalStateException` real (ver
   §15 — limitación de tooling, no de diseño). Verificación manual
   recomendada: reabrir un proyecto con la cuadrícula prendida, o
   volver de segundo plano con la app en la pantalla de edición.
2. La redecodificación desde `sourceUri` (para reconstruir el bitmap
   tras un pedido de invalidación, o tras recrear el contexto EGL) sigue
   dependiendo de `onSurfaceCreated`/de que alguien vuelva a publicar un
   `pendingBitmap` — comportamiento preexistente, sin cambios en esta
   fase (no estaba en el alcance: esta fase corrige OWNERSHIP, no el
   pipeline de decodificación en sí).
3. `layerTextures` es un `MutableMap` simple, no `Thread-safe` por sí
   mismo (no es un `ConcurrentHashMap`) — es intencional: por diseño,
   **todo** acceso a este mapa ocurre exclusivamente dentro de los
   callbacks del hilo de GL (ver §14), así que no necesita
   sincronización propia. Si en el futuro algo fuera de `GLRenderer`
   necesitara leerlo, eso sería en sí mismo una violación de la
   arquitectura que esta fase estableció — no un caso a resolver con
   más locks.

## 19. Trabajo futuro

Fuera del alcance de esta fase, explícitamente no adelantado (ver
restricción de alcance del brief): una migración de `LayerApi`/`RenderApi`
que exponga este registro de texturas de forma pública y tipada, si
algún consumidor externo (EliNer API) llegara a necesitarlo; por ahora
sigue siendo un detalle de implementación interno de `GLRenderer`, que es
exactamente lo que el ownership real exige que sea.

## 20. Tabla de ownership (obligatoria)

| Recurso | Propietario | Hilo | Creación | Destrucción | Context-dependent |
|---|---|---|---|---|---|
| Bitmap CPU decodificado (antes de publicarse) | Quien lo decodifica (ViewModel/ProjectStorage/LayerRepository/GLRenderer en redecode) | Principal o GL, según el caso | `ImageDecoding.decodeSampledFromUri` | Se recicla tras subir la textura (GL) | No |
| `pendingBitmap` (handoff) | Compartido por diseño: productor publica, consumidor único (GL) toma | Publica: cualquiera. Toma: GL | `SingleResourceHandoff.publish(...)` | `takeForConsumption()` + `bitmap.recycle()` en GL | No |
| Pedido de invalidación de textura | Compartido por diseño: UI pide, GL consume | Pide: principal. Consume: GL | `Layer.requestTextureInvalidation()` | Se autolimpia al consumirse (`getAndSet(false)`) | No |
| Texture id GL de una capa | `GLRenderer` (exclusivo — `layerTextures`, `private`) | GL | `uploadTextureIfNeeded` | `deleteTexture` (poda de capa eliminada, reemplazo, o vaciado total en `onSurfaceCreated`) | Sí |
| Bitmap de la cuadrícula de composición | Compose/UI (`remember` en `EditorScreen`) | Principal (creación); GL solo lo lee | Compose, bajo demanda (keys del `remember`) | Nunca lo recicla GL — vive mientras Compose lo conserve | No |
| Texture id GL de la cuadrícula | `GLRenderer` (`GridTextureCacheState`/`GpuHandle`) | GL | `updateGridTextureIfNeeded` | `deleteTexture` si cambia de identidad en el mismo contexto; se abandona (no se borra) si el contexto murió | Sí |
| Shader | `LayerDrawer` (sin cambios en esta fase) | GL | `forceReinitialize()` | Se abandona al recrear contexto (correcto — contexto muerto) | Sí |
| VBO | No se usa (arrays del lado del cliente) | — | — | — | — |
| FBO | No se usa en el preview en vivo | — | — | — | — |

## 21. Diagrama (obligatorio)

```
CPU / UI (ViewModel, ProjectStorage, LayerRepository)
   │
   │ decodifica un Bitmap nuevo
   ▼
pendingBitmap.publish(bitmap)         ◄── único punto de entrada de un
   │                                       bitmap nuevo hacia GL
   ▼
GL THREAD (GLRenderer.onDrawFrame)
   │
   ├── consumeTextureInvalidationRequest() → si true: layerTextures.remove(id) + glDeleteTextures
   │
   ├── pendingBitmap.takeForConsumption() → si hay bitmap:
   │        │
   │        ├── clampForTexture (techo real de hardware)
   │        ├── layerTextures.remove(id) + glDeleteTextures (si había una vieja)
   │        ├── glTexImage2D → nuevo id
   │        ├── layerTextures[id] = nuevo id
   │        └── bitmap.recycle()  (bitmap de capa: de un solo uso)
   │
   └── dibuja con layerTextures[id]

PROJECT STATE (Layer: sourceUri, pendingBitmap, pedido de invalidación)
     │
     ▼
RENDER SNAPSHOT (Fase 2 — zIndex, visibilidad, parallax, look, cámara;
                 CERO handles GPU)
     │
     ▼
GL THREAD
     │
     ├──► GPU RESOURCES (layerTextures — privado de GLRenderer)
     │        │
     │        ▼
     │      TEXTURAS GL (dependen del contexto EGL)
     │
     └──► TEMP RESOURCES (pendingBitmap ya consumido → reciclado)
              │
              ▼
           (nada — vida útil de un solo frame)

Cuadrícula de composición (caso distinto — Bitmap NO es de un solo uso):
Compose (remember) ──► Bitmap CPU (dueño: Compose) ──usa, no recicla──► GL sube textura
```

Explícito, como pide el brief:

```
PROJECT STATE  ≠  RENDER STATE  ≠  GPU STATE
BITMAP CPU     ≠  GL TEXTURE
GL HANDLE      →  pertenece al GL lifecycle (GLRenderer, privado)
```

## 22. Matriz antes / después (obligatoria)

| Área | Antes | Después | Estado |
|---|---|---|---|
| `glTextureId` ownership | Campo en `Layer`, escrito por GL **y** por `EditorViewModel` (violación real) | Ya no existe en `Layer` — vive en `GLRenderer.layerTextures`, `private`, solo el hilo de GL lo toca | Corregido |
| `pendingBitmap` ownership | `@Volatile var`, consumo en dos pasos separados (lost update real) | `SingleResourceHandoff` — consumo atómico de un solo slot | Corregido |
| Grid Bitmap ownership | GL lo reciclaba pese a que Compose seguía siendo dueño — crash real tras recreación de contexto | GL solo lo usa, nunca lo recicla — Compose sigue siendo el dueño | Corregido |
| `recycle()` policy | Un solo criterio aplicado a dos casos distintos (capa vs. grid) | Diferenciado explícitamente por quién es el dueño real en cada caso | Corregido |
| Context generation | Ya correcta para grid (`GpuHandle`/`GpuContextGeneration`); ausente para texturas de capa (no la necesitaba) | Sin cambios en grid; texturas de capa usan vaciado total en vez de generación por-entrada (invariante más simple, suficiente para el caso) | Sin cambios / Verificado |
| Texture recreation | Ya correcta (Fase 3) | Sin cambios de comportamiento — reescrita sobre el nuevo registro | Verificado, sin cambios |
| RenderSnapshot boundary | Ya sin handles GPU (Fase 2) | Sin cambios — reverificado | Verificado, sin cambios |
| Thread ownership | GLES limitado al hilo de GL (correcto), pero campos de ownership "documentado" violados por escrituras cruzadas | GLES sigue limitado al hilo de GL; los dos únicos cruces de hilo (`pendingBitmap`, pedido de invalidación) son ahora primitivas explícitas de handoff/pedido, no campos mutables sin disciplina | Corregido |
| Documentation | `FASE_3_RENDER_GL_LIFECYCLE.md` afirmaba ownership que el código no cumplía | Corregida (banner + §3.2/§15/§19) + este informe nuevo | Corregido |
| Tests | Cubrían lifecycle de contexto/grid/shader (Fase 3) | + 4 archivos nuevos cubriendo el ownership de texture id/pending bitmap/estructura de `Layer` (ver §15) | Ampliado |

## 23. Inventario de archivos

Ver §16/§17 arriba (modificados/creados). Archivos eliminados: ninguno.
