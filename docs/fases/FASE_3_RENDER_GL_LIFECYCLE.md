# FASE 3 — Render / GL Lifecycle

> Continúa directamente sobre lo estabilizado en
> `docs/fases/FASE_2_CONCURRENCIA_RENDER_PLAYBACK.md` (estado de render,
> `RenderSnapshot`, concurrencia, playback, duración/FPS). Esta fase NO
> vuelve a tocar nada de eso — se apoya en ello como fuente estable.

## 1. Objetivo

Estabilizar el ciclo de vida gráfico completo de Olyze Creation Studio:
`Activity → GLPreview → GLSurfaceView → GLRenderer → EGL/GPU`, de forma
que el renderer sea **determinista + seguro + recuperable + compatible
con el lifecycle real de Android**, sin adelantar ninguna fase futura
(EliNerApi, RenderApi, C++/JNI, Vulkan, refactors masivos).

Concepto fundamental que gobierna toda la fase:

```
PROJECT STATE  ≠  GPU STATE
```

El estado lógico del proyecto (`Layer`, `CameraTrack`, `RenderSnapshot`
de Fase 2) debe sobrevivir a la destrucción de la `Surface`, a la
recreación del contexto EGL y a la recreación de la `Activity`. Los
recursos GPU (texturas, shader programs) son, por definición,
**recreables** y nunca la fuente de verdad de nada.

## 2. Estado encontrado antes

El pipeline real (confirmado por lectura directa del código, no por la
documentación previa — que no mencionaba nada de esto) es:

```
MainActivity (ComponentActivity)
       │  setContent { ... }
       ▼
EditorScreen (Compose)
       │  GLPreview(...)
       ▼
GLPreview.kt  — @Composable, AndroidView { factory = { GLSurfaceView(...) } }
       │  setRenderer(renderer); renderMode = RENDERMODE_CONTINUOUSLY
       ▼
GLSurfaceView (android.opengl, sin subclasificar)
       │  administra EGL internamente (versión 2, config 8/8/8/8/16/0)
       ▼
GLRenderer : GLSurfaceView.Renderer
       │  onSurfaceCreated / onSurfaceChanged / onDrawFrame
       ▼
LayerDrawer (shader program propio, texturas por capa + textura de grid)
       ▼
GPU
```

Puntos confirmados en la auditoría (PASO 0-6 del brief):

- `GLPreview` crea la `GLSurfaceView` **una sola vez**, dentro del
  `factory` de `AndroidView` (Compose garantiza esto — el `factory` no
  se vuelve a ejecutar en recomposiciones). La misma instancia de
  `GLSurfaceView` y de `GLRenderer` sobrevive a rotaciones de estado de
  Compose, apertura/cierre de paneles, etc., mientras el `GLPreview`
  siga en composición.
- `renderMode = RENDERMODE_CONTINUOUSLY` — no hay ni un solo
  `requestRender()` en toda la base de código (`grep` confirmado). Es la
  decisión correcta para este proyecto: el preview necesita reflejar
  animación de cámara/efectos en marcha (`RenderSnapshot` cambia solo
  por el paso del tiempo, no por un evento discreto de UI), así que
  "cuando hace falta" es, en la práctica, "siempre". No se cambia en
  esta fase.
- **`GLRenderer` es la misma instancia de siempre** — `LayerDrawer`
  también. `onSurfaceCreated()` puede llamarse más de una vez sobre la
  MISMA instancia de `GLRenderer`/`LayerDrawer` (pérdida y recreación de
  contexto EGL con la `Activity` y la `View` sin destruirse — apagar
  pantalla, superponer otra app pesada de GPU encima, volver de segundo
  plano tras un rato largo). Esto es exactamente lo que la Fase 3 tenía
  que auditar con lupa.
- `MainActivity` maneja `configChanges="orientation|screenSize|keyboardHidden"`
  y está fijada a `screenOrientation="portrait"` — la Activity casi
  nunca se recrea por rotación. Sí puede recrearse por otros motivos
  (cambio de idioma/tema del sistema, o el proceso restaurado por
  Android tras ser matado en segundo plano) — el código ya contempla
  esto para el ID del proyecto abierto (`rememberSaveable`), pero nada
  del lado de GL/EGL lo contemplaba.

## 3. Problemas detectados

### 3.1 — BUG REAL: el shader program no se reconstruye tras recrear el contexto EGL

`GLRenderer.onSurfaceCreated()` llamaba a `LayerDrawer.ensureInitialized()`,
cuya única condición de reconstrucción era `if (shaderProgram == null)`.
Esa condición es correcta la primera vez que el contexto EGL se crea,
pero deja de cumplirse a partir de la segunda vez que `onSurfaceCreated`
corre sobre la misma instancia de `LayerDrawer` — el `programId` que
queda en memoria pertenece a un contexto EGL ya destruido. Usarlo contra
el contexto nuevo es, según el driver/GPU, un no-op silencioso (nada se
dibuja) o, en el peor caso, un id que por pura casualidad coincide con
otro recurso real y produce dibujo corrupto.

### 3.2 — BUG REAL: la textura de la cuadrícula de composición no se invalida tras recrear el contexto EGL

`GLRenderer.updateGridTextureIfNeeded()` decidía si hacía falta volver a
subir la textura de la cuadrícula comparando **solo** la identidad del
`Bitmap` (`bitmap === lastGridBitmapIdentity`). Es la optimización
correcta mientras el contexto EGL no cambia (evita resubir la misma
textura ~60 veces por segundo), pero con un contexto EGL nuevo el
`Bitmap` de Compose sigue siendo el mismo objeto de siempre — la
comparación seguía diciendo "no hace falta resubir" mientras el
`gridTextureId` que se pensaba reutilizar pertenecía al contexto viejo,
ya destruido. A diferencia de esto, las texturas de capa (`Layer.glTextureId`)
**sí** se invalidaban correctamente (`= -1` en cada `onSurfaceCreated`,
código ya presente antes de esta fase). Resultado observable: la
cuadrícula quedaba invisible o mostrando basura de memoria de GPU
reciclada después de cualquier recreación de contexto, mientras las
capas normales se recuperaban bien.

### 3.3 — BUG REAL: ningún puente entre el lifecycle de Android y el de GLSurfaceView

No existía llamada alguna a `GLSurfaceView.onPause()` / `.onResume()` en
todo el proyecto (`grep` confirmado). Compose **no** administra esto
automáticamente — es responsabilidad explícita de quien integra la
vista, documentada así por Android desde siempre. Con
`RENDERMODE_CONTINUOUSLY`, esto significa que el hilo de render de GL
seguía dibujando ~60 veces por segundo aunque la app pasara a segundo
plano: consumo de batería/GPU innecesario y una ventana real (aunque
angosta) de llamar a GLES contra una `Surface` que Android ya invalidó
por detrás.

Relacionado: `GLSurfaceView` tampoco tiene un método `destroy()` propio.
Cuando `GLPreview` sale de composición para siempre (navegar de "Editor"
a "Mis proyectos" — esta app reemplaza pantallas, no las apila, ver
`MainActivity.kt`), no había ningún `onRelease` en el `AndroidView` que
frenara el hilo de GL — quedaba corriendo indefinidamente contra una
`Surface` que Compose ya había desconectado, hasta que (si acaso) el
recolector de basura decidiera actuar; un `Thread` en ejecución activa
es en sí mismo una raíz de GC, así que en la práctica era una fuga real
de hilo, no solo de memoria.

### 3.4 — Estados de renderer implícitos, sin representación explícita

`GLRenderer` no tenía ninguna variable que representara en qué punto del
ciclo de vida estaba (¿ya corrió `onSurfaceCreated`? ¿ya se conoce el
viewport?). `onDrawFrame()` asumía siempre que sí. No se encontró ningún
crash atribuible a esto en el código (GLSurfaceView normalmente respeta
el orden de callbacks), pero es exactamente el tipo de suposición
implícita que el criterio de aceptación de esta fase pide eliminar.

## 4. Causas raíz

Las tres primeras (3.1, 3.2, 3.3) comparten una misma causa raíz: el
código fue escrito asumiendo, implícitamente, que **"contexto EGL
nuevo" y "arranque de la app" son lo mismo**. Esa suposición es correcta
la primera vez y falsa cualquier otra vez que Android decida recrear el
contexto sin destruir la `Activity` ni la `View` — algo que el propio
sistema operativo puede hacer en cualquier momento y que el proyecto
nunca ejercitó explícitamente (no hay, ni había, ningún test ni flujo
manual que fuerce una recreación de contexto).

La causa raíz de 3.4 es que el proyecto, hasta Fase 2, se concentró en
estabilizar el **estado de datos** (`RenderSnapshot`, concurrencia,
playback) — un trabajo completamente independiente del ciclo de vida de
`GLSurfaceView`/EGL en sí, que nunca había tenido su propia fase de
auditoría hasta ahora.

## 5. Soluciones implementadas

- **`GpuHandle` + `GpuContextGeneration`** (nuevos, `engine/render/GpuHandle.kt`)
  — lógica pura (sin ninguna llamada a GLES) que empareja cada handle de
  GPU con la "generación" del contexto EGL bajo la que fue creado. Un
  handle es válido si y solo si su generación coincide EXACTAMENTE con
  la generación actual del renderer. Reemplaza la idea de "un `Int` que
  no es -1 es válido" por una comprobación real y explícita.
- **`GridTextureCacheState`** (nuevo, `engine/render/GridTextureCacheState.kt`)
  — encapsula la decisión correcta de cuándo (re)subir la textura de la
  cuadrícula: identidad de bitmap cambiada, **o** generación de contexto
  cambiada. Corrige 3.2 de raíz. También libera explícitamente la
  textura vieja cuando el reemplazo es por cambio real de bitmap dentro
  del MISMO contexto (evita una fuga de memoria GPU que se habría
  introducido de otro modo al dejar de comparar solo por identidad).
- **`LayerDrawer.forceReinitialize()`** (nuevo método) — descarta la
  referencia al `ShaderProgram` viejo sin condición y reconstruye desde
  cero. Es lo que ahora llama `GLRenderer.onSurfaceCreated()` en vez de
  `ensureInitialized()`. Corrige 3.1 de raíz. `ensureInitialized()` se
  conserva sin cambios de comportamiento, ahora documentado como red de
  seguridad defensiva, no como el mecanismo de invalidación real.
- **Puente de lifecycle en `GLPreview.kt`** — `DisposableEffect` +
  `LifecycleEventObserver` sobre `LocalLifecycleOwner.current` (mismo
  patrón, ya establecido en el proyecto, que usa `MainActivity.kt` para
  el guardado en `ON_STOP`) que llama `glSurfaceView.onPause()` /
  `.onResume()` en `ON_PAUSE`/`ON_RESUME`. Corrige 3.3. Además,
  `onRelease` en el `AndroidView` llama `onPause()` una última vez y
  limpia la referencia guardada cuando la vista sale de composición para
  siempre.
- **`GLRendererLifecycleState`** (nuevo enum, `engine/render/GLRendererLifecycleState.kt`)
  — `UNINITIALIZED → SURFACE_READY → VIEWPORT_READY`, con
  `canTransitionTo` validando el mapa de transiciones legales contra el
  contrato real de `GLSurfaceView.Renderer`. `onDrawFrame()` ahora
  verifica `VIEWPORT_READY` antes de dibujar nada. Corrige 3.4.

Todo lo anterior es intencionalmente **lógica pura, separada de las
llamadas GLES en sí** — ver §26 (Tests) para el porqué.

## 6. Arquitectura anterior

```
GLRenderer (misma instancia siempre)
    │
    ├─ LayerDrawer (misma instancia siempre)
    │      shaderProgram: ShaderProgram?   ← sobrevive silenciosamente
    │                                         entre contextos EGL distintos
    │
    └─ gridTextureId: Int                  ← ídem, sin invalidar
       lastGridBitmapIdentity: Bitmap?        (comparación por identidad,
                                                 ciega a la generación de
                                                 contexto)

(sin ningún puente hacia Activity.onPause()/onResume())
```

## 7. Arquitectura actual

```
GLRenderer (misma instancia siempre)
    │
    ├─ contextGeneration: GpuContextGeneration   (avanza 1 vez por onSurfaceCreated)
    ├─ lifecycleState: GLRendererLifecycleState  (UNINITIALIZED/SURFACE_READY/VIEWPORT_READY)
    │
    ├─ drawer: LayerDrawer
    │      forceReinitialize()  ← se llama SIEMPRE en onSurfaceCreated,
    │                              sin condición de "si ya existe"
    │
    └─ gridTextureCache: GridTextureCacheState
           handle: GpuHandle(id, generation)     ← inválido automáticamente
                                                     si generation ≠ actual

GLPreview.kt
    │
    ├─ glSurfaceViewRef (remember)  → referencia estable entre recomposiciones
    │
    └─ DisposableEffect(LocalLifecycleOwner.current)
           ON_PAUSE  → glSurfaceView.onPause()
           ON_RESUME → glSurfaceView.onResume()

       AndroidView(..., onRelease = { it.onPause() })
```

## 8. Lifecycle de Activity

Sin cambios de comportamiento respecto a Fase 2 — `MainActivity` sigue
fijada a `portrait` con `configChanges="orientation|screenSize|keyboardHidden"`,
así que casi nunca se recrea por rotación. Puede recrearse por cambio de
idioma/tema del sistema o por restauración de proceso; en ambos casos el
`GLPreview` de la nueva composición crea una `GLSurfaceView`/`GLRenderer`
completamente nuevos — no hay ninguna referencia estática ni singleton
que sobreviva a esto (se auditó explícitamente para descartar ese
antipatrón, punto 3 del brief). El puente de lifecycle nuevo (§5) no
depende de que la `Activity` se recree o no: reacciona a `ON_PAUSE`/
`ON_RESUME`, que ocurren siempre, se recree la `Activity` o no.

## 9. Lifecycle de GLPreview

`GLPreview` es un composable sin estado propio de lifecycle antes de
esta fase (más allá de crear la vista una vez). Ahora mantiene:

- `glSurfaceViewRef` (`remember { mutableStateOf<GLSurfaceView?>(null) }`)
  — puente hacia la instancia real para que el observer de lifecycle
  pueda actuar sobre ella sin necesitar pasar la `Activity` completa.
- El observer de `ON_PAUSE`/`ON_RESUME` (§5), añadido/quitado en
  `DisposableEffect(lifecycleOwner)`.
- `onRelease` en el `AndroidView`, para el caso "la vista sale de
  composición para siempre" (navegación), distinto del caso "la app va a
  segundo plano" (`ON_PAUSE`).

## 10. Lifecycle de GLSurfaceView

`GLSurfaceView` en sí no se subclasificó (se usa tal cual la entrega
Android) — su ciclo de vida interno (creación/destrucción de `Surface`,
manejo de su propio hilo de GL) sigue siendo responsabilidad 100% de la
clase estándar de Android. Lo que faltaba, y esta fase agregó, es
**quién le avisa** que debe pausar/reanudar ese hilo interno según el
lifecycle real de la app — ver §9.

## 11. Lifecycle de GLRenderer

Ver `GLRendererLifecycleState` (§5, §7). `onSurfaceCreated` siempre
vuelve primero a `UNINITIALIZED` (única transición "hacia atrás" legal,
representa que un contexto EGL nuevo invalida cualquier estado previo
sin excepción) y termina en `SURFACE_READY`; `onSurfaceChanged` avanza a
`VIEWPORT_READY`; `onDrawFrame` no dibuja nada real si el estado no es
`VIEWPORT_READY`.

## 12. EGL context

La creación/destrucción/recuperación del contexto EGL en el preview en
vivo es manejada enteramente por `GLSurfaceView` (versión de contexto 2,
config `8/8/8/8/16/0` vía `setEGLContextClientVersion`/`setEGLConfigChooser`,
sin cambios en esta fase). **No se implementó un EGL manager
independiente** — el brief pide explícitamente no hacerlo salvo
necesidad real, y no la hay: el problema nunca fue la gestión del
contexto en sí (`GLSurfaceView` la hace correctamente), sino que
`GLRenderer`/`LayerDrawer` no reaccionaban correctamente a que un
contexto nuevo había llegado. `VideoExporter.kt` y `ThumbnailRenderer.kt`
sí administran su propio EGL manualmente (`EGL14.eglCreateContext(...)`)
para exportar/generar miniaturas fuera de pantalla — quedan fuera del
alcance de esta fase (no son parte del preview en vivo) y se auditaron
por separado (§20).

## 13. Surface lifecycle

La destrucción/recreación de la `Surface` subyacente la administra
`GLSurfaceView` (vía su `SurfaceHolder.Callback` interno). El contrato
que le importa a esta fase es el que ya cubre §11: cada vez que hay una
`Surface`/contexto nuevo, `onSurfaceCreated` se vuelve a llamar y ahora
reconstruye todo lo que corresponde sin condiciones falsas.

## 14. Context recreation

Cubierto por §5/§7/§11. El caso explícitamente auditado (y antes roto,
§3.1/§3.2): la MISMA instancia de `GLRenderer`/`LayerDrawer` recibe un
contexto EGL nuevo sin que la `Activity` ni la `View` se hayan destruido
— ahora reconstruye shader y textura de grid correctamente en todos los
casos.

## 15. Texture lifecycle

- **Texturas de capa (`Layer.glTextureId`)**: ya eran correctas antes de
  esta fase — `GLRenderer.onSurfaceCreated` las resetea a `-1`
  incondicionalmente para cada capa vista, forzando una re-decodificación
  desde `Layer.sourceUri`/`Layer.pendingBitmap` (estado CPU persistente,
  Fase 2) y una nueva subida a GPU. Verificado, sin cambios.
- **Textura de la cuadrícula (`gridTextureCache`)**: corregida en esta
  fase, ver §3.2/§5.

## 16. Shader lifecycle

Corregido en esta fase — ver §3.1/§5. `ShaderProgram` en sí (compilación,
link, `use()`) no cambió; lo que cambió es **cuándo** `LayerDrawer` decide
reconstruirlo.

## 17. VBO lifecycle

**No aplica.** `LayerDrawer` dibuja con arrays de vértices del lado del
cliente (`FloatBuffer` + `glVertexAttribPointer` apuntando directo al
buffer, sin `glGenBuffers`/VBOs) — confirmado por lectura completa de
`LayerDrawer.kt`. No hay ningún VBO en el proyecto que gestionar en esta
fase.

## 18. FBO lifecycle

**No aplica** al preview en vivo — no hay ningún framebuffer/render
target offscreen en `GLRenderer`/`LayerDrawer` (dibuja directo al
framebuffer por defecto de la `Surface`). `VideoExporter.kt` sí usa un
`Surface`/`MediaCodec` como destino de exportación, pero eso es un
mecanismo distinto (fuera del alcance del preview en vivo que cubre esta
fase) y no cambió.

## 19. GPU resource ownership

| Recurso | Creado por | Destruido por | Recreable | Fuente de verdad persistente |
|---|---|---|---|---|
| Texturas de capa | `GLRenderer.uploadTextureIfNeeded` (hilo GL) | `LayerDrawer.deleteTexture` (hilo GL), en `onSurfaceCreated` si `glTextureId>=0` antes de resetear, o al reemplazar por una nueva | Sí (ya lo era antes de esta fase) | `Layer.sourceUri` / `Layer.pendingBitmap` (CPU) |
| Textura de la cuadrícula | `GLRenderer.updateGridTextureIfNeeded` (hilo GL) vía `drawer.uploadTexture` | `drawer.deleteTexture` si el reemplazo es por cambio de bitmap en el MISMO contexto; nunca explícitamente si es por recreación de contexto (id ya pertenece a un contexto muerto) | Sí (corregido en esta fase — antes no se recreaba correctamente) | `EditorScreen` (bitmap de Compose, recalculado a demanda) |
| Shader program (`LayerDrawer`) | `ShaderProgram(...)` dentro de `LayerDrawer.forceReinitialize()`/`ensureInitialized()` (hilo GL) | Nunca explícitamente (ver §29, riesgo 2) — se abandona sin `glDeleteProgram` al recrear contexto (correcto, contexto muerto) | Sí (corregido en esta fase) | Código fuente de los shaders (constante, no depende de estado en runtime) |
| VBO | N/A | N/A | N/A | No se usa (arrays del lado del cliente) |
| FBO | N/A | N/A | N/A | No se usa en el preview en vivo |
| `GpuTextureLimits` (tamaño máx. de textura) | `GLES20.glGetIntegerv` la primera vez que se consulta, cacheado en un `object` | N/A (valor de hardware, no depende del contexto) | N/A — verificado que NO varía entre contextos del mismo dispositivo/GPU, cachearlo globalmente es correcto | GPU/driver (constante de hardware) |

## 20. RenderSnapshot integration

Sin cambios en `RenderSnapshot`/`RenderLayerSnapshot` (Fase 2) — siguen
siendo la única fuente que `onDrawFrame` consume para saber qué dibujar
en cada frame, y siguen sin tener ningún campo de GPU (ver
`RenderSnapshot.kt`). El test `RendererLifecycleScenarioTest` (escenario
D) verifica explícitamente que un `RenderSnapshot` sobrevive intacto a
una simulación de recreación de contexto completa.

## 21. Thread ownership

Sin cambios de arquitectura — se re-auditó (§19 del brief) y se confirma
que todas las llamadas GLES siguen ocurriendo exclusivamente dentro de
los tres callbacks de `GLRenderer` (contrato del hilo de GL de
`GLSurfaceView`). Las nuevas piezas (`GpuContextGeneration`,
`GLRendererLifecycleState`, `GridTextureCacheState`) son leídas/escritas
únicamente desde esos mismos callbacks — no se agregó ningún acceso
concurrente nuevo. `glSurfaceView.onPause()`/`.onResume()` (llamados
desde el hilo principal/UI vía el observer de lifecycle) son parte de la
API pública de `GLSurfaceView` diseñada explícitamente para ser invocada
desde el hilo principal — no son llamadas GLES.

## 22. requestRender strategy

Sin cambios — se confirma que no hay ningún `requestRender()` en el
proyecto y que `RENDERMODE_CONTINUOUSLY` es la elección coherente dado
que el preview anima continuamente en función del tiempo
(`RenderSnapshot`/interpolación de cámara). No se introdujo ninguna
llamada nueva a `requestRender()` en esta fase.

## 23. Render mode

Sin cambios — sigue siendo `RENDERMODE_CONTINUOUSLY`, confirmado como la
decisión correcta (§22). El puente de lifecycle nuevo (§5) es
precisamente lo que hace que este modo continuo sea seguro en segundo
plano — antes de esta fase, continuo + sin pausa era la combinación que
generaba el problema de §3.3.

## 24. Background/foreground behavior

Corregido — ver §3.3/§5. `ON_PAUSE` detiene el hilo de GL antes de que
la app dejara de ser interactiva; `ON_RESUME` lo reanuda. El estado del
proyecto (`Layer`/`CameraTrack`/`RenderSnapshot`) no se toca en ningún
punto de este flujo — sigue viviendo en `EditorViewModel`, ajeno por
completo a si la GPU está renderizando o no en ese instante.

## 25. Surface recreation behavior

Corregido — ver §3.1/§3.2/§14. Una recreación de `Surface`/contexto ya
no dibuja con un shader o una textura de grid obsoletos.

## 26. Tests

**Restricción real del proyecto** (confirmada en `app/build.gradle.kts`):
solo `junit:junit:4.13.2` en `testImplementation` — sin Robolectric, sin
Mockito/MockK. Esto significa que **no es posible** levantar un
`GLSurfaceView`/contexto EGL real, ni siquiera un `android.graphics.Bitmap`
real, dentro de un test JUnit de este proyecto (el `android.jar` de test
no está mockeado — cualquier llamada real a un método Android lanza
`RuntimeException: not mocked`). Los tests de Fase 1/Fase 2 ya confirman
y respetan esta misma restricción.

Por eso el diseño de esta fase separa deliberadamente la **lógica de
decisión** (pura, sin GLES, sin `android.*`) de las **llamadas GLES en
sí** (que no cambiaron de comportamiento salvo en cuándo se invocan) —
lo primero es 100% testeable, lo segundo requiere ejecución real en
dispositivo/emulador (GitHub Actions + APK, fuera del alcance de esta
fase, ver §31 del brief).

Archivos de test nuevos, todos en
`app/src/test/java/com/yeivikas/olyzecs/engine/render/`:

| Archivo | Qué cubre | Escenarios del brief (§22) |
|---|---|---|
| `GpuHandleTest.kt` | Validez de un handle según generación; `GpuContextGeneration.advance()` | C (context recreation) |
| `GLRendererLifecycleStateTest.kt` | Mapa de transiciones legal/ilegal de `GLRendererLifecycleState` | A, F |
| `GridTextureCacheStateTest.kt` | Reproduce el bug real 3.2 explícitamente (mismo bitmap + generación distinta ⇒ hace falta resubir); apagar/prender cuadrícula; liberación de handle | B, E |
| `RendererLifecycleScenarioTest.kt` | Combina las piezas de arriba simulando el ciclo completo `onSurfaceCreated → onSurfaceChanged → (recreación) → onSurfaceCreated → ...` | A, B, C, D, F, H |

No se escribieron tests para `onSurfaceChanged`/viewport en píxeles
exactos (escenario E del brief) más allá de la transición de estado
(`GLRendererLifecycleStateTest`) — el cálculo de viewport en sí
(`GLES20.glViewport(0, 0, width, height)`) es una única línea sin lógica
propia que testear de forma aislada sin un contexto GL real; quedó
cubierto a nivel de integración manual (ver §29, riesgo 3).

## 27. Archivos modificados

- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GLRenderer.kt`
  — `onSurfaceCreated`/`onSurfaceChanged`/`onDrawFrame` reescritos para
    usar `GLRendererLifecycleState`, `GpuContextGeneration` y
    `GridTextureCacheState`; `forceReinitialize()` en vez de
    `ensureInitialized()`; liberación explícita de la textura de grid
    vieja al reemplazarla dentro del mismo contexto.
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/LayerDrawer.kt`
  — nuevo método `forceReinitialize()`; `ensureInitialized()` redocumentado
    como red de seguridad defensiva (sin cambio de comportamiento).
- `app/src/main/java/com/yeivikas/olyzecs/ui/GLPreview.kt` — puente de
  lifecycle (`DisposableEffect`/`LifecycleEventObserver`), `onRelease`
  en el `AndroidView`.
- `ARCHITECTURE.md` — línea de `engine/render` actualizada con los
  archivos nuevos de esta fase.
- `README.md` — árbol de archivos corregido/actualizado (de paso se
  corrigió que `DisplayRefreshRate.kt` está en `platform/`, no en
  `engine/render/` — error preexistente a esta fase).
- `docs/pending-work/PROJECT_AUDIT.md` — entrada de Fase 3 agregada.

## 28. Archivos creados

- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GpuHandle.kt`
  — `GpuHandle` + `GpuContextGeneration`.
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GLRendererLifecycleState.kt`
  — enum de estado explícito del renderer.
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GridTextureCacheState.kt`
  — lógica pura de reconciliación de la textura de grid.
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/GpuHandleTest.kt`
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/GLRendererLifecycleStateTest.kt`
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/GridTextureCacheStateTest.kt`
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/RendererLifecycleScenarioTest.kt`
- `docs/fases/FASE_3_RENDER_GL_LIFECYCLE.md` — este documento.

## 29. Archivos eliminados

Ninguno.

---

## 30. Matriz antes / después

| Área | Antes | Después | Estado |
|---|---|---|---|
| GL lifecycle (estado del renderer) | Implícito, sin variable que lo representara | `GLRendererLifecycleState` explícito (`UNINITIALIZED`/`SURFACE_READY`/`VIEWPORT_READY`) | Corregido |
| Activity lifecycle ↔ GL | Sin ningún puente | `DisposableEffect` + `LifecycleEventObserver` → `onPause()`/`onResume()` | Corregido |
| Surface lifecycle | Administrado por `GLSurfaceView`, pero el renderer no reaccionaba bien a una `Surface`/contexto nuevo | Reacciona correctamente (shader + grid se reconstruyen) | Corregido |
| EGL context | Administrado por `GLSurfaceView`, sin manager propio (correcto, sin cambios) | Igual — se documentó explícitamente el reparto de responsabilidades | Verificado, sin cambios |
| Texture lifecycle (capas) | Ya correcto | Sin cambios | Verificado, sin cambios |
| Texture lifecycle (grid) | BUG: no se invalidaba tras recrear contexto | Corregido con `GridTextureCacheState`/`GpuHandle` | Corregido |
| Shader lifecycle | BUG: no se reconstruía tras recrear contexto | Corregido con `LayerDrawer.forceReinitialize()` | Corregido |
| VBO lifecycle | No aplica (no se usan VBOs) | Sin cambios | No aplica |
| FBO lifecycle | No aplica (preview no usa FBOs) | Sin cambios | No aplica |
| RenderSnapshot | Ya no dependía de ningún estado GPU (Fase 2) | Sin cambios — verificado explícitamente con test | Verificado, sin cambios |
| Thread ownership | GLES ya limitado al hilo de GL | Sin cambios — verificado explícitamente | Verificado, sin cambios |
| requestRender | No se usa (continuo) | Sin cambios | Verificado, sin cambios |
| Background/foreground | GLThread seguía corriendo en segundo plano | Pausado explícitamente vía lifecycle bridge | Corregido |
| Recreation (contexto/Activity) | Shader y grid quedaban obsoletos | Reconstrucción correcta y determinista | Corregido |

---

## 31. Estado final de Fase 3

Los tres problemas reales de lifecycle anticipados por el brief como
posibles (shader no reconstruido, textura de grid no invalidada, sin
puente Activity↔GLSurfaceView) se confirmaron los tres como reales en el
código, con causa raíz identificada por lectura directa y tests que
reproducen el escenario exacto de cada uno donde la arquitectura del
proyecto lo permite (JUnit puro, sin Robolectric). Las correcciones
introducen dos conceptos reutilizables y 100% testeables —
`GpuHandle`/`GpuContextGeneration` (generación de contexto) y
`GLRendererLifecycleState` (estado explícito) — sin tocar el backend
gráfico (sigue siendo OpenGL ES 2 vía `GLSurfaceView`, sin EGL manager
propio, sin FBO/VBO nuevos), sin tocar `RenderSnapshot`/playback de Fase
2, y sin adelantar ninguna migración de API futura. Los riesgos
restantes están documentados explícitamente en §32, no ocultos.

## 32. Riesgos restantes

1. **`onSurfaceChanged` (viewport en píxeles) no tiene test automatizado
   propio** (§26) — solo se ejercitó manualmente por lectura de código.
   Es una única llamada a `GLES20.glViewport` sin lógica de decisión
   propia; el riesgo real es bajo, pero queda anotado como brecha de
   cobertura honesta.
2. **`ShaderProgram` nunca llama `glDeleteProgram` explícitamente**, ni
   antes ni después de esta fase — se abandona la referencia al
   recrearse el contexto (correcto: el id pertenece a un contexto
   muerto, borrarlo explícitamente no tiene efecto y podría ser
   indefinido según el driver) o al destruirse `LayerDrawer` junto con
   `GLRenderer`/`GLSurfaceView` (el proceso completo se recicla, la GPU
   libera todo). No se considera una fuga real en la práctica, pero se
   deja registrado por transparencia.
3. **`VideoExporter.kt`/`ThumbnailRenderer.kt` administran su propio EGL
   manualmente**, fuera del alcance de esta fase (no son el preview en
   vivo) — se auditaron puntualmente (§12) y se confirmó que no
   comparten el bug de esta fase (instancia de `LayerDrawer` nueva por
   cada contexto de un solo uso), pero no se les aplicó ningún cambio ni
   test nuevo.
4. **Ventana de lectura no-atómica entre `getLayers()`/`getRenderSnapshot()`
   en `onDrawFrame`** — riesgo ya documentado en Fase 2 (§18 de ese
   informe), sin cambios en esta fase, sigue vigente en los mismos
   términos.

## 33. Trabajo pospuesto (deuda técnica / fase futura)

Explícitamente fuera de alcance de esta fase, sin implementar:

- `EliNerApiImpl` como facade definitiva, `RenderApi`, `EffectsApi`,
  migración completa de `DistortionApi`.
- JNI, C++20, migración del renderer a C++.
- Migración de `LayerApi`/`CameraApi`/`AudioApi`.
- Rediseño completo de `GLRenderer`, reescritura de shaders,
  optimización de GPU.
- Refactor masivo de `EditorScreen.kt`/`EditorViewModel.kt`.
- Backend Vulkan o cualquier alternativa a OpenGL ES.

Ninguno de estos era imprescindible para corregir los problemas reales
de lifecycle encontrados — se registran como deuda técnica para fases
posteriores, tal como pide el brief.
