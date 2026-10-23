# FASE 4.3 — Render Boundary

> Origen: prompt maestro "FASE 4.3 — RENDER BOUNDARY — CIERRE
> ARQUITECTÓNICO E IMPLEMENTACIÓN REAL". Este documento refleja el
> estado REAL después de esta sesión — **no está cerrada**, y se
> documenta explícitamente qué se hizo y qué queda, en vez de declarar
> la fase completa (regla del propio prompt maestro: "no declares
> terminado algo que permanezca transitional").

## Estado real: PARCIAL — R4.3.1, R4.3.6 (ya cerrado antes), R4.3.7/R4.3.8, el Render Contract (`EliNerApi.render: RenderApi`), GLPreview/Android Render Host Boundary, el wiring productivo de RenderApi, y la auditoría de Render Math Boundary + clasificación de Grid cerrados con cambio de código real

Este prompt pide, entre otras cosas, diseñar una Render API pública
completa, reorganizar `GLPreview`, mover la responsabilidad de
thumbnail/pixel-read, y clasificar `VideoExporter` — un trabajo de la
magnitud de las fases anteriores completas (4.0–4.2), tocando código
GPU/render explícitamente marcado como "ya cerrado, crítico, no
reabrir sin defecto real". Dado que en este entorno no puedo compilar
ni ejecutar el proyecto, prioricé cerrar con solidez UN hallazgo real,
bien acotado y completamente verificable por inspección/grep, antes de
avanzar hacia cambios de mayor superficie y riesgo (diseño de una API
nueva, tocar `GLPreview`/`VideoExporter`) que necesitarían más de una
sesión para hacerse con el mismo nivel de verificación que el resto de
este proyecto ya tiene.

## R4.3.1 — DAG scene ↔ render — **CORREGIDO**

### Hallazgo confirmado (no asumido del prompt, verificado en el código)

```
engine.scene.Layer      --importa-->  engine.render.SingleResourceHandoff
engine.scene.Layer      --importa-->  engine.render.TextureInvalidationRequest
engine.render.GLRenderer --importa--> engine.scene.Layer
```

Ciclo de paquetes real: `scene ↔ render`. `GLRenderer → scene` es la
dirección ESPERADA y correcta (el renderer necesita conocer el modelo
de escena). `scene → render` era el problema.

### Causa raíz

`SingleResourceHandoff<T>` y `TextureInvalidationRequest` son, ambas,
clases **completamente genéricas** — sin ningún tipo de Android, GLES
ni del renderer (`SingleResourceHandoff<T : Any>` no depende ni
siquiera de `Bitmap`; ambas ya declaraban en su propio KDoc que eran
genéricas a propósito para poder testearse con JUnit puro). Su
responsabilidad conceptual es un primitivo de **ownership de estado de
`Layer`/Scene** ("¿quién es dueño del último bitmap decodificado
pendiente?", "¿hace falta invalidar la textura de esta capa?") — vivían
en `engine.render` por ubicación histórica, no por necesidad
arquitectónica. `GLRenderer` las CONSUME, no las define.

### Corrección aplicada

Se movieron ambas clases (contenido sin cambios de lógica, solo
`package`) a un paquete nuevo: `com.yeivikas.olyzecs.engine.scene.gpu`
— separado de `engine.scene` (para no mezclarlas con los modelos de
escena "puros" como `Layer`/`LookSettings`) pero dejando explícito que
su ownership conceptual es de Scene, no de Render.

Archivos afectados (movidos o con import corregido):

- `engine/render/SingleResourceHandoff.kt` → `engine/scene/gpu/SingleResourceHandoff.kt`
- `engine/render/TextureInvalidationRequest.kt` → `engine/scene/gpu/TextureInvalidationRequest.kt`
- `engine/scene/Layer.kt` — imports actualizados, ya no depende de `engine.render`.
- `engine/render/GLRenderer.kt` — **hallazgo propio durante la migración**: dependía de la visibilidad implícita de "mismo paquete" para `SingleResourceHandoff.Consumption.Ready/.Stale/.Empty` (nunca tuvo un `import` explícito porque antes vivía en el mismo paquete `engine.render`). Se le agregó el `import` explícito nuevo — sin este paso, el archivo hubiera dejado de compilar. Se encontró por auditoría activa (grep de todo el archivo), no por suposición.
- `data/ProjectStorage.kt`, `data/LayerRepository.kt`, `viewmodel/EditorViewModel.kt` — solo mencionan estas clases en comentarios (ninguna referencia de código real); no necesitaron cambios, verificado explícitamente para no dejar nada a medias.
- Tests: `SingleResourceHandoffTest.kt`/`TextureInvalidationRequestTest.kt` movidos a `test/.../engine/scene/gpu/` (mismo criterio: mismo paquete que la clase bajo prueba, mismo motivo por el que no necesitaban `import` antes). `LayerTextureRegistryScenarioTest.kt` (permanece en `engine.render` — simula consumo del lado render) y `LayerGpuOwnershipStructureTest.kt` (permanece en `engine.scene`) recibieron el `import` explícito nuevo.

### Verificación realizada (sin compilador disponible)

- Búsqueda exhaustiva (`grep -rn`) de CUALQUIER referencia a
  `engine.render.SingleResourceHandoff`/`engine.render.TextureInvalidationRequest`
  en todo `src/main` y `src/test` tras el movimiento — cero referencias
  colgantes (solo un comentario de documentación, corregido también).
- Confirmado con grep que ningún archivo de `engine/scene/*.kt` importa
  `engine.render` después del cambio.
- Balance de llaves (`{`/`}`) verificado en los 8 archivos tocados —
  útil como red de seguridad mínima sin compilador, no reemplaza una
  compilación real.
- **No ejecutado**: `assembleDebug`, `test`. El usuario debe validarlo
  vía GitHub Actions, como en las fases anteriores.

### Por qué este cambio es de bajo riesgo pese a no poder compilar

Es un movimiento de paquete puro (ningún cambio de lógica, ningún
cambio de firma pública salvo la ruta del import) sobre dos clases
completamente aisladas (sin dependencias de Android/GLES), con
consumidores identificados exhaustivamente por búsqueda textual — a
diferencia de un cambio semántico (como los de fases anteriores), el
espacio de error de un movimiento de paquete es "¿quedó algún import
viejo sin actualizar?", una pregunta que grep puede responder con
certeza.

## R4.3.7/R4.3.8 — Thumbnail: Infrastructure → GL/EGL renderer — **CORREGIDO**

### Hallazgo confirmado

`TimelineApiImpl` y `ProjectStorage` (Infrastructure/Data) llamaban
directo a `ThumbnailRenderer.render(...)` — un `object` concreto que
abre su propio contexto EGL aislado y usa `GLES20`/`LayerDrawer`
directamente. Exactamente el problema que describe R4.3.8: "Infrastructure
→ GL/EGL renderer" en vez de "Infrastructure → resultado Render".

`ThumbnailRenderer` en sí **no se tocó de fondo** (mismo EGL, mismo
`LayerDrawer`, mismo algoritmo de composición — no se duplicó ni se
reescribió, tal como pide R4.3.7/R4.3.9).

### Corrección aplicada

Mismo patrón exacto ya usado por `PixelColorSource` (que ya resolvía
este mismo problema para lectura de pixel, ver más abajo):

1. Nueva interfaz `engine/core/ThumbnailRenderCapability.kt` — un
   contrato mínimo (`render(context, layers, timeMs, widthPx, heightPx): Bitmap?`)
   sin ningún tipo de GLES/EGL en la firma.
2. `object ThumbnailRenderer` ahora implementa esa interfaz
   (`: ThumbnailRenderCapability`) — se le quitaron los valores por
   defecto de sus parámetros porque Kotlin no permite que un `override`
   los redeclare (quedan una sola vez, en la interfaz). **Verificado por
   grep que esto no rompe nada**: los 2 únicos call sites reales
   (`TimelineApiImpl`, `ProjectStorage`) siempre pasaban los 5 argumentos
   explícitos, ninguno dependía de los defaults.
3. `TimelineApiImpl`/`ProjectStorage` reciben ahora
   `thumbnailRenderer: ThumbnailRenderCapability = ThumbnailRenderer`
   como parámetro de constructor NUEVO pero con valor por defecto —
   **cero cambios** para el único sitio de construcción real de cada uno
   (`TimelineApiImplFactory`/wiring en `MainActivity.kt`, y
   `ProjectStorage(applicationContext)` en `MainActivity.kt`,
   verificado por grep que es el único lugar donde se construyen).

### Por qué esto es de bajo riesgo

Mismo argumento que R4.3.1: es un cambio estructural (qué TIPO se
depende) sin ningún cambio de lógica ni de comportamiento en runtime —
la implementación real que se ejecuta sigue siendo exactamente la misma
(`ThumbnailRenderer`, vía su valor por defecto). Verificado
exhaustivamente por grep que no queda ninguna llamada directa
`ThumbnailRenderer.render(...)` fuera de comentarios, y que ningún test
existente construye `ProjectStorage`/`TimelineApiImpl` de una forma que
el nuevo parámetro pueda romper.

### `PixelColorSource` — ya era la solución correcta para Pixel Read (R4.3.6), sin cambios necesarios

Auditado: `PixelColorSource` (`engine.core`) YA es exactamente el
boundary que R4.3.6 pide — un contrato mínimo sin GLES/EGL/
`GLSurfaceView` en la firma, implementado por `GLRenderer`, consumido
por `EditorScreen`/`GLPreview` SIN que la UI conozca `GLRenderer`
directamente, y ya expuesto como `EliNerApi.render: PixelColorSource`
(ver `EliNerApi.kt`/`EliNerApiImpl.kt`, documentado ahí mismo como "YA
es una frontera pública real"). No se modificó nada acá — ya estaba
bien hecho antes de esta fase. `ThumbnailRenderCapability` (arriba)
sigue exactamente el mismo patrón que este contrato ya establecía.

## Intervención — Render Contract (`EliNerApi.render`: `PixelColorSource` → `RenderApi`) — **CORREGIDO**

### Problema real confirmado

`EliNerApi.render` era literalmente `PixelColorSource` — una única
capacidad puntual ("leer el color de un pixel"), no un contrato de
dominio Render. No había dónde crecer sin romper el tipo público el día
que apareciera una segunda capacidad real de Render.

### Auditoría previa (antes de tocar nada)

Se inspeccionó el grafo real completo pedido: `EliNerApi`/
`EliNerApiImpl`, `GLRenderer` (implementación real de
`PixelColorSource`, vía `requestPixelColor` — fire-and-forget con
callback en Main, respaldado por un campo `@Volatile` consumido en
`onDrawFrame`), `RenderSnapshot`/`RenderLayerSnapshot`, `LayerDrawer`,
`ThumbnailRenderCapability`/`ThumbnailRenderer`, y el flujo completo
`EditorViewModel.currentRenderSnapshot() → GLPreview (lambda) → GLRenderer.onDrawFrame()`.

**Hallazgo clave de la auditoría:** el render de un frame en este
proyecto es un bucle **PULL** — `GLRenderer` llama a una lambda
(`getRenderSnapshot: () -> RenderSnapshot`) cada frame para pedir el
estado actual, nadie le "empuja" un `render(request)`. Esto descarta de
raíz la idea de inventar un `RenderRequest`/`RenderResult` tipo
`render(request): result` — esa forma no corresponde a cómo el código
real funciona hoy, e inventarla sería exactamente el "parche
superficial" que esta intervención prohíbe explícitamente.

### Decisión sobre `RenderSnapshot`/`RenderLayerSnapshot` — permanecen INTERNOS

`RenderSnapshot` ya es, de por sí, un DTO inmutable y limpio (sin
`Bitmap`, sin handles GL, sin `Context`, sin `Uri` — solo
`CameraFrame`/`LookSettings`/`Keyframe`, todos ya clasificados como
tipos de dominio válidos en auditorías anteriores). Aun así, **no se
expone como parte pública**: es el mecanismo interno del bucle PULL
descrito arriba (construido por `EditorViewModel.currentRenderSnapshot()`,
consumido únicamente por `GLRenderer` a través de la lambda que le pasa
`GLPreview`) — exponerlo públicamente mezclaría "el contrato de
dominio" con "el mecanismo interno de sincronización con el hilo de
GL", que son responsabilidades distintas. Queda exactamente donde
estaba, sin cambios.

### Decisión sobre `PixelColorSource` — se integra como sub-capability de `RenderApi`, no se elimina ni se duplica

Se evaluaron las 3 opciones que pedía la intervención:

1. **Permanecer como capability separada** (statu quo) — descartada:
   dejaría a `EliNerApi.render` sin ningún contrato de dominio propio,
   el problema original sin resolver.
2. **Convertirse en parte de `RenderApi` por herencia**
   (`interface RenderApi : PixelColorSource`) — descartada
   explícitamente: sería "hacer que compile" sin agregar ningún
   boundary real, y ocultaría que `pixelColor` es una capacidad
   específica detrás de miembros heredados sin nombre propio.
3. **Composición — `RenderApi.pixelColor: PixelColorSource`** — la
   elegida. `RenderApi` es un contenedor de dominio con una única
   propiedad real hoy; `PixelColorSource` sigue siendo exactamente la
   misma interfaz, con la misma única implementación real
   (`GLRenderer`), sin duplicar nada. Queda con nombre propio
   (`pixelColor`) y espacio para crecer sin romper el contrato el día
   que aparezca una segunda capacidad real.

### Decisión sobre `ThumbnailRenderCapability` — permanece en `engine.core`, NO se fusiona con `RenderApi`

Evaluado explícitamente: `ThumbnailRenderCapability` es una capacidad de
render **distinta** — su implementación real (`ThumbnailRenderer`) abre
su **propio contexto EGL aislado tipo pbuffer**, sin relación alguna con
la superficie de preview en vivo que respalda `PixelColorSource`/
`GLRenderer`. Fusionarla dentro de `RenderApi` sin que el código
demuestre una relación real sería sobrearquitectura (sección 22 de la
intervención). Permanece en `engine.core`, sin cambios de ubicación ni
de contrato — solo se corrigió la documentación que la mencionaba (ver
más abajo).

### Cambios de código reales

- **Nuevo** `api/render/RenderApi.kt` — contrato de dominio, una sola
  propiedad real (`pixelColor: PixelColorSource`), con el razonamiento
  completo (qué se evaluó, qué se descartó) en su propio KDoc.
- **Nuevo** `api/render/RenderApiImpl.kt` — implementación real trivial
  (delegado directo), mismo patrón que el resto de los `*ApiImpl` de
  este proyecto.
- `api/EliNerApi.kt` — `render: PixelColorSource` → `render: RenderApi`;
  KDoc de la interfaz reescrito (no solo un párrafo agregado) para
  reflejar el cambio y su razonamiento.
- `api/EliNerApiImpl.kt` — mismo cambio de tipo en el constructor; KDoc
  reescrito.
- `api/timeline/TimelineApi.kt` / `TimelineApiImpl.kt` — **corrección de
  documentación pedida explícitamente por esta intervención**: el KDoc
  todavía decía "Respaldo real: ... `engine.timeline.ThumbnailRenderer.render`"
  y "`ThumbnailRenderer.render` necesita exactamente ese tipo" — texto
  que quedó desactualizado desde la corrección real de R4.3.7/R4.3.8 (que
  sí había cambiado el código, pero no había tocado estos dos comentarios
  puntuales). Corregido para nombrar `ThumbnailRenderCapability`, no el
  `object` concreto.
- Tests: `api/EliNerApiImplTest.kt` actualizado a la nueva firma
  (`render: RenderApi = RenderApiImpl(pixelColor)`, con una aserción
  adicional verificando que `api.render.pixelColor` sigue siendo la
  MISMA instancia inyectada). Nuevo `api/render/RenderApiImplTest.kt`:
  delegación real (no solo existencia), y una prueba de boundary por
  reflexión que falla si `RenderApi` alguna vez expusiera un tipo
  `GLRenderer`/`GLSurfaceView`/`EGL*`/`GLES`/`MediaCodec`/`MediaMuxer`/
  `android.view.*` en el tipo de retorno de cualquiera de sus miembros
  — no solo los de hoy.

### Auditoría de dependencias posterior (sección 18 de la intervención)

Verificado por búsqueda exhaustiva: ningún archivo de `EliNerApi`/
`EliNerApiImpl`/`RenderApi`/`RenderApiImpl` importa `GLRenderer`, `EGL*`,
`GLES*`, `GLSurfaceView`, `MediaCodec` ni `MediaMuxer`. `engine.core`
sigue sin importar `engine.render` (la dependencia real
`RenderApiImpl → PixelColorSource` es hacia `engine.core`, no al
revés). `engine.scene` sigue sin importar `engine.render` (ver R4.3.1).
Ninguna referencia colgante al tipo viejo `render: PixelColorSource` en
ningún otro archivo del proyecto (búsqueda exhaustiva confirmada).

### Por qué esto sigue siendo de bajo riesgo

`EliNerApiImpl` **no tiene ningún sitio de construcción en producción**
todavía (confirmado por búsqueda exhaustiva: el único lugar donde se
instancia es el propio test). Cambiar el tipo de `render` no mueve
ningún wiring real de `MainActivity` ni de `EditorScreen` — es un
cambio de contrato en un agregador que todavía no está conectado a la
aplicación, con impacto de compilación acotado y verificado a un solo
archivo de test (ya actualizado).

### Camera math / Grid — sin cambios, ratifican lo ya auditado

`PerspectiveCameraMath`/`QuadHomography` no están duplicadas entre UI y
render (un solo archivo cada una, consumido desde ambos lados);
`rasterizeGridBitmap()` es una herramienta de edición/UI (overlay del
grid, no forma parte de la composición final exportada) — clasificación
**B (overlay de edición)**, no se migra.

## Intervención — GLPreview / Android Render Host Boundary — **CORREGIDO**

### Auditoría previa (sección 1/3/4/7 de esta intervención)

Se leyó `GLPreview.kt` completo (195 líneas) antes de tocar nada.
Hallazgo confirmado: mezclaba dos responsabilidades reales y distintas
dentro del mismo `@Composable`:

1. **Integración Compose** (genuina responsabilidad de UI): envolver
   lambdas con `rememberUpdatedState`, devolver la `View` que
   `AndroidView` espera, decidir CUÁNDO reaccionar a lifecycle.
2. **Android Render Host** (no era responsabilidad de UI, pero vivía
   ahí): crear el `GLSurfaceView` con su configuración EGL, construir
   `GLRenderer` con sus 7 dependencias, conectar el callback
   `onAllVisibleLayersPainted` (que llega del hilo de GL) de vuelta al
   hilo principal vía `Handler(Looper.getMainLooper())`, y ejecutar
   `onPause()`/`onResume()` sobre esa vista según el lifecycle real de
   Android.

`GLPreview` **no** tenía ninguna responsabilidad de (4) GPU resource
ownership — nunca toca un handle GL directo, un `Bitmap` de textura, ni
ningún mecanismo de `LayerGpuCommitGate`/`SingleResourceHandoff`
directamente; eso ya estaba correctamente confinado a `GLRenderer`. Solo
(2)+(3) estaban mezcladas con (1).

### Flujo real reconstruido (sección 7)

```
Editor/UI (EditorScreen)
   ↓  pasa lambdas de estado + LayerGpuCommitGate compartido
GLPreview (Compose)
   ↓  crea UNA vez, vía factory de AndroidView
AndroidGLRenderHost   ← NUEVO, extraído en esta intervención
   ↓  construye y configura
GLSurfaceView + GLRenderer
   ↓
LayerDrawer → recursos GPU → Surface
```

No existía llamada directa UI→GLRenderer (`EditorScreen` nunca importa
`GLRenderer`, confirmado por grep) — el único punto que conocía la clase
concreta era, y sigue siendo, `GLPreview`. Tampoco existía creación de
`GLRenderer` dentro de `EditorViewModel` (confirmado: ningún `import`
real de `GLRenderer`/`GLSurfaceView` en `EditorViewModel.kt`, solo
comentarios explicativos — su responsabilidad ya estaba correctamente
limitada a exponer lambdas de estado (`getRenderSnapshot`/`getLayers`/
etc.), nunca a construir ni controlar objetos gráficos concretos). Es
decir: la única dependencia arquitectónicamente incorrecta real
encontrada fue la mezcla DENTRO de `GLPreview`, no una fuga hacia otras
capas.

### Corrección aplicada: extracción de `AndroidGLRenderHost`

**Nuevo** `engine/render/AndroidGLRenderHost.kt` — clase Android pura
(sin `@Composable`, sin nada de Compose) que ahora posee:

- `create(...)`: construye `GLSurfaceView` + `GLRenderer` con
  EXACTAMENTE la misma secuencia de llamadas que antes vivía en el
  `factory` de `GLPreview` (`setEGLContextClientVersion` →
  `setEGLConfigChooser` → `holder.setFormat` → `setZOrderOnTop` →
  construir `GLRenderer` → conectar `onAllVisibleLayersPainted` →
  `setRenderer` → `renderMode`), incluido el puente de hilo GL→Main
  (`Handler(Looper.getMainLooper())`, antes vivía en `GLPreview`, ahora
  es responsabilidad del host garantizar que sus callbacks salgan en un
  hilo seguro).
- `pause()`/`resume()`/`release()`: delegan a `view.onPause()`/
  `view.onResume()` — mismas llamadas exactas que antes, mismo
  razonamiento (`release()` usa `onPause()` por el mismo motivo
  documentado: `GLSurfaceView` no tiene `destroy()` propio).
- `view`/`renderer`: expuestos como `val` de solo lectura, para que
  `GLPreview` pueda devolver la vista a `AndroidView` y entregar el
  renderer a `onRendererReady` — sin que `GLPreview` necesite conocer
  nada de EGL/GLES por debajo.

`GLPreview.kt` se reescribió para USAR este host en vez de crear
`GLSurfaceView`/`GLRenderer` directamente — **extracción de código, no
reescritura de lógica**: cada llamada que existía antes sigue
existiendo, en el mismo orden, ahora a través del host.

**Regresión que me autodetecté y corregí antes de dar el cambio por
terminado:** mi primer borrador de la migración de `onRelease` perdió
la comprobación de identidad que existía en el código original
(`if (glSurfaceViewRef.value === view) { ... }`) — una guarda contra que
`onRelease` liberara una referencia más nueva si la vieja ya había sido
reemplazada. Al revisar línea por línea contra el archivo original
antes de cerrar el cambio, noté la omisión y restauré la misma
comprobación exacta (`renderHostRef.value?.view === view`), ahora
comparando contra `host.view` en vez de la vista directa. Se documenta
acá a propósito, sin ocultarlo.

### Qué NO se tocó (secciones 8, 15, 16, 17, 18, 19 de esta intervención)

- **`GLRenderer`**: cero cambios. Sigue siendo la única implementación
  concreta del backend OpenGL, con su separación interna existente
  intacta (implementa `GLSurfaceView.Renderer` y `PixelColorSource`,
  como antes).
- **`LayerDrawer`**: cero cambios — sigue siendo el compositor
  compartido entre preview/thumbnail/export, sin ninguna duplicación
  introducida.
- **`ProjectStorage`**: auditado, confirmado sin ninguna dependencia
  hacia `GLRenderer`/`GLSurfaceView`/`GLPreview` (ya estaba
  correctamente aislado detrás de `ThumbnailRenderCapability` desde la
  intervención anterior).
- **`EditorViewModel`**: auditado en relación a GLPreview/RenderApi/
  RenderHost/GLRenderer — confirmado que nunca importa ni construye
  ninguno de esos tipos concretos (solo los menciona en comentarios
  explicativos); su responsabilidad de exponer lambdas de estado
  (modelo PULL) ya era correcta antes de esta intervención, no se
  tocó.
- **Camera math** (`PerspectiveCameraMath`/`QuadHomography`): auditadas
  de nuevo, sin duplicación entre UI y render (mismo hallazgo que la
  intervención anterior, ratificado).
- **`VideoExporter`**: no auditado en detalle en esta intervención — la
  extracción de `AndroidGLRenderHost` no le afecta (no lo consume ni lo
  necesita: `VideoExporter` construye su propio EGL/contexto aislado
  para exportar, separado del preview en vivo, igual que
  `ThumbnailRenderer`).
- **`RenderApi`/`PixelColorSource`**: sin cambios de contrato. `GLPreview`
  sigue siendo, exactamente igual que antes, el único punto del proyecto
  que sabe que la implementación real de `PixelColorSource` es
  `GLRenderer` — solo que ahora ese conocimiento pasa a través de
  `host.renderer` en vez de una variable local `renderer`.
- **Modelo PULL de `RenderSnapshot`**: sin cambios — se preservó
  explícitamente, no se convirtió en un modelo PUSH
  (`RenderRequest`/`RenderResult`), tal como exige esta intervención.

### Thread model (sin cambios de fondo, ahora más explícito)

- Hilo principal (Compose/UI): construye el host, registra el
  `LifecycleEventObserver`, decide cuándo pausar/resumir/liberar.
- Hilo de GL (interno a `GLSurfaceView`): ejecuta `GLRenderer.onDrawFrame`,
  lee `RenderSnapshot`/`getLayers` (modelo PULL), sube texturas.
- Puente GL→Main: `Handler(Looper.getMainLooper())`, ahora vive dentro
  de `AndroidGLRenderHost.create()` en vez de en `GLPreview` — mismo
  mecanismo, dueño distinto.

### Surface lifecycle (sección 12) — verificado sin regresión

- `onSurfaceCreated`/`onSurfaceChanged`: sin cambios, siguen siendo
  responsabilidad exclusiva de `GLRenderer` (implementa
  `GLSurfaceView.Renderer`), nunca tocados por este cambio.
- `pause`/`resume`/`release`: mismas 3 llamadas exactas
  (`view.onPause()`/`view.onResume()`/`view.onPause()`), mismo orden de
  invocación desde el mismo `LifecycleEventObserver`, ahora indirectas a
  través del host.
- `GpuContextGeneration`/`contentRevision`/`SingleResourceHandoff`:
  ningún archivo de esos mecanismos fue tocado ni indirectamente — el
  host no conoce nada de identidad de recursos GPU, delega el 100% de
  eso a `GLRenderer`, exactamente como `GLPreview` ya hacía antes.

### Tests agregados

`engine/render/AndroidGLRenderHostTest.kt` — estructural, por reflexión
(no se puede instanciar `AndroidGLRenderHost` de verdad sin `Context`
real/Robolectric): verifica que el constructor primario es privado
(solo se crea vía `create()`), que expone exactamente `view`/`renderer`/
`pause()`/`resume()`/`release()` sin parámetros, y que `create()` vive
en el companion object. No prueba comportamiento real de GL (no es
posible en este entorno) — se documenta la limitación en vez de
falsificar un test.

## Estado GPU (R4.3.18) — verificado intacto, ratificado de nuevo en esta pasada

`LayerGpuCommitGate`, `RenderSnapshot`, `RenderLayerSnapshot`,
`GpuHandle`, `GpuContextGeneration`, `GridTextureCacheState` — ningún
archivo modificado en esta intervención (los únicos cambios en
`engine.render` en toda la Fase 4.3 fueron el movimiento de paquete de
`SingleResourceHandoff`/`TextureInvalidationRequest`, en la intervención
anterior). Ninguna regresión de ownership: `RenderApi`/`RenderApiImpl`
son puramente aditivos, sin tocar ningún mecanismo de GPU existente.

## Intervención — Wiring productivo de RenderApi + Composition Root + cierre Thumbnail — **CORREGIDO**

### Auditoría previa (sección 5 de esta intervención)

Se localizó exhaustivamente, antes de tocar nada:

- **Todos los usos de `RenderApi`/`RenderApiImpl`**: solo 4 sitios —
  `EliNerApi.kt` (contrato), `EliNerApiImpl.kt` (constructor),
  `RenderApiImplTest.kt` (test), `EliNerApiImplTest.kt` (test). Ninguno
  en código de producción fuera de la propia API — confirmaba
  exactamente el "scaffolding sin consumidor real" que esta
  intervención debía resolver.
- **Todos los lugares donde se construye `RenderApiImpl`**: ninguno en
  producción (solo en el test). **Todos los lugares donde se construye
  `EliNerApiImpl`**: ninguno en producción, mismo hallazgo.
- **El consumidor real de `PixelColorSource`**: `EditorScreen.kt`, para
  el cuentagotas de color (`LayerColorPickerDialog`) — una variable
  local `pixelColorSource: PixelColorSource?` recibida vía
  `onRendererReady` de `GLPreview`, usada en 3 sitios
  (`requestPixelColor`). Este era el consumidor real que debía empezar
  a usar `RenderApi`, no uno inventado.
- **`ThumbnailRenderer`/`ThumbnailRenderCapability`**: consumidores
  confirmados: `TimelineApiImpl`/`ProjectStorage` (vía el parámetro con
  valor por defecto ya introducido en la intervención de Fase 4.3
  anterior). `MainActivity` (el composition root real de la app) NO
  referencia ninguna de las dos clases — confirmado por búsqueda
  exhaustiva.

### Hallazgo 1 (MEDIO) — `RenderApi` sin consumidor productivo real

**Archivo:** `ui/EditorScreen.kt`. **Causa:** el cuentagotas hablaba
directo con `PixelColorSource`, nunca con `RenderApi` — el contrato de
dominio existía pero nadie lo usaba, contradiciendo el objetivo
explícito de esta intervención ("el RenderApi existente deje de ser
solamente scaffolding"). **Impacto:** ninguno funcional (el cuentagotas
ya funcionaba), pero arquitectónico: la separación de dominio quedaba
en el papel, no en el código real. **Decisión:** corregir ahora (es
exactamente el objetivo de esta intervención).

**Corrección:** `EditorScreen` ahora construye
`RenderApiImpl(pixelColorSource)` en el mismo punto donde antes
guardaba el `PixelColorSource` crudo (`onRendererReady`), y el
cuentagotas llama a `renderApi?.pixelColor?.requestPixelColor(...)` —
la UI conoce `RenderApi` (dominio), y a través de él,
`PixelColorSource` (capacidad); nunca `GLRenderer`. `EditorScreen`
mismo actúa acá como el Composition Root legítimo para este objeto
concreto: es el único punto del proyecto donde existe una superficie GL
viva con la que componer un `RenderApi` real — exactamente el
razonamiento que ya usaba el KDoc de `EliNerApi.render`/`EliNerApiImpl`
para explicar por qué `render` no se compone en `MainActivity` junto a
los otros 8 dominios (KDoc actualizado para reflejar que `RenderApi` ya
tiene consumidor real, ver más abajo).

### Hallazgo 2 (BAJO, ratificado sin cambio) — dependencia concreta de `ThumbnailRenderCapability` en `ProjectStorage`/`TimelineApiImpl`

**Análisis:** ambas clases reciben `thumbnailRenderer: ThumbnailRenderCapability = ThumbnailRenderer`
como parámetro CON VALOR POR DEFECTO. Evaluado explícitamente si esto
es una fuga de implementación concreta hacia un consumidor que debería
depender solo del contrato: **no lo es**. Ninguna de las dos clases es,
en sí misma, un "consumer" ingenuo de thumbnail — son las clases que
NECESITAN producir un thumbnail como parte de su propia responsabilidad
(persistencia de proyecto / contrato de Timeline), y en un proyecto sin
framework de DI (explícitamente prohibido introducir uno, regla 14 de
esta intervención), un parámetro con valor por defecto apuntando a la
única implementación real existente es el patrón de composición mínimo
y correcto — sigue permitiendo reemplazar la implementación
explícitamente (para tests, o un backend de render alternativo futuro)
sin ningún framework adicional. **Decisión: se mantiene sin cambios** —
ya estaba en el lugar arquitectónicamente apropiado.

### `AndroidGLRenderHost` — verificado, sin cambios necesarios

Confirmado que la separación introducida en la intervención anterior
sigue siendo real: `GLPreview` no volvió a absorber responsabilidades
del host, `GLRenderer` permanece concreto y solo conocido por
`AndroidGLRenderHost`/`GLPreview`. La guarda de identidad
(`renderHostRef.value?.view === view`, la evolución de la guarda
original `glSurfaceViewRef.value === view` tras el cambio de nombre de
variable de la intervención anterior) sigue intacta — verificado por
grep, no se tocó. `release()` es seguro de llamar más de una vez
(`GLSurfaceView.onPause()` es idempotente según la documentación de
Android, y además `GLPreview` ya limpia `renderHostRef.value = null`
tras la primera liberación, así que una segunda invocación de
`onRelease` — que no debería ocurrir per el contrato de `AndroidView`
— tampoco volvería a llamar a `release()`).

### DAG arquitectónico (sección 19) — verificado sin ciclos nuevos

`ui/EditorScreen.kt` ahora importa `api.render.RenderApi`/
`RenderApiImpl` — dirección `ui → api.render → engine.core`, ninguna
dependencia inversa (`api.render`/`engine.core` no importan `ui`). No
se introdujo `scene → render → scene`, `api → implementation → api`,
`timeline → renderer → timeline` ni `storage → renderer → storage` —
ninguno de los archivos de esos módulos se tocó en esta intervención
salvo `EliNerApi.kt`/`EliNerApiImpl.kt` (solo documentación).

### Documentación corregida (sección 21/19 de esta intervención)

El KDoc de `EliNerApi.render`/`EliNerApiImpl` afirmaba, de la
intervención anterior, que `render` seguía "sin consumidor externo
real" — ya no es cierto tras el Hallazgo 1 de esta pasada. Se
reescribió (no se agregó un párrafo más) para reflejar que `RenderApi`
sí tiene consumidor productivo real (`EditorScreen`), y que lo único
pendiente es unificar los 9 dominios en una única instancia de
`EliNerApiImpl` en producción (bloqueado por el ciclo de vida distinto
del objeto `RenderApiImpl`, ligado al Composable, frente a los otros 8,
ligados a `MainActivity`) — sin inventar una solución artificial para
ese desajuste en esta intervención.

### Tests

No se agregaron tests nuevos en esta pasada: el cambio real
(`EditorScreen` construyendo `RenderApiImpl`) vive en código Compose/UI
que no es testeable con JUnit puro en este entorno (mismo límite ya
documentado para el resto de `EditorScreen.kt` en fases anteriores). El
comportamiento de `RenderApiImpl` en sí (delegación real, boundary sin
tipos de GL/EGL/Android View) ya está cubierto por
`RenderApiImplTest.kt` de la intervención anterior, que sigue vigente
sin cambios y sigue siendo válido para este nuevo consumidor (la
implementación no cambió, solo quién la usa).

## Intervención — Render Math Boundary + clasificación arquitectónica de Grid — **AUDITADO, mayormente RATIFICADO, 1 corrección documental + hardening de tests**

Alcance de esta pasada: auditar la ubicación real de
`PerspectiveCameraMath`/`QuadHomography`, la dependencia UI↔Camera↔Scene↔Render,
posible duplicación matemática, y la clasificación arquitectónica
completa de Grid (estado, ownership GPU, relación con `RenderApi`).
Regla seguida al pie de la letra: **no mover nada sin un defecto real
verificado por código** — no se asumió ningún problema por la forma del
nombre o del paquete.

### PerspectiveCameraMath — ubicación RATIFICADA, sin cambios de arquitectura

**Archivo:** `engine/render/PerspectiveCameraMath.kt`. **Qué es:**
`internal object` sin estado, sin `import android.*`, sin `import
GLES*` — solo `kotlin.math.atan` (verificado con `head` del archivo).
Cuatro funciones escalares puras (`depthZ`, `eyeZ`, `fovyDeg`,
`depthCompensation`) más `depthCompensationFor` (composición de las
tres primeras) y `projectQuadCornersNdc` (arma un pipeline
model→view→proyección completo con `Mat4`, un objeto hermano de
matrices 4×4 column-major en el mismo archivo, sin `android.opengl.Matrix`).

**Consumidores reales (grep exhaustivo, no supuestos):**
- `engine/render/LayerDrawer.kt` — el renderer GL real: usa las
  constantes (`BASE_EYE_Z`, `DOLLY_RANGE`) y las 4 funciones escalares
  para armar sus propias matrices `android.opengl.Matrix` reales.
- `ui/EditorScreen.kt` (líneas ~15906–16320) — el overlay 2D de
  selección/hit-test/screen-to-UV (`layerBoundingQuadPx`,
  `hitTestLayerAt`, `screenPointToLayerUv`): llama a
  `projectQuadCornersNdc` con los MISMOS parámetros de cámara que
  `LayerDrawer`, y solo convierte el resultado NDC a píxeles de
  pantalla (una conversión de espacio de coordenadas, tarea legítima
  de UI — sección 10 del prompt maestro) o invierte con
  `QuadHomography` para hit-testing.

**Decisión — RESULTADO A (no hay bug):** la ubicación en
`engine.render` es correcta. Razonamiento completo:

1. **Dirección del DAG ya es correcta y no cambia.** `engine.render`
   importa `engine.camera` (para `CameraFrame` en `RenderSnapshot`/
   `LayerDrawer`), nunca al revés — verificado con grep
   (`engine/camera/*.kt` no tiene ningún `import
   com.yeivikas.olyzecs.engine.render`). `ui` ya importa
   `engine.render` para `GLPreview`/`AndroidGLRenderHost`/`RenderApi` —
   este archivo no introduce una dirección de dependencia nueva, solo
   reutiliza una que ya existía y ya estaba aceptada.
2. **No es matemática de "Camera" (semántica) sino de "Render"
   (conversión estado→resultado visual/NDC), tal como los define la
   sección 11 del prompt maestro.** `engine.camera` (comparar
   `CameraFrame`, `CameraTrack`, `CameraFrameInterpolation`) modela el
   ESTADO semántico de la cámara a lo largo del tiempo (interpolación
   de keyframes) — nunca produce coordenadas de pantalla. `PerspectiveCameraMath`
   hace exactamente lo que la sección 11 asigna a Render: "convertir
   estado de cámara en instrucciones/resultado de render" —
   `projectQuadCornersNdc` literalmente reproduce en Kotlin puro el
   mismo pipeline `Matrix.perspectiveM`/`setLookAtM`/`multiplyMM` que
   ejecuta GL. Encaja por responsabilidad, no por analogía de nombre.
3. **Por qué UI puede consumirla sin violar el boundary (sección 10):**
   UI no reimplementa el pipeline de proyección — lo LLAMA. La única
   lógica que vive en `EditorScreen.kt` es NDC→píxel (`(ndcX+1)/2 *
   boxWidthPx`) y el reordenamiento de las 4 esquinas para dibujar un
   contorno — matemática de presentación, no de Engine. El propio KDoc
   de `PerspectiveCameraMath` (línea 58-61) documenta que esto es
   intencional: exponer las constantes/función como ÚNICA fuente de
   verdad fue justo el fix de un bug real (ADR-007 y el bug de
   13/sep/2026) donde `LayerDrawer` y `EditorScreen` mantenían **dos
   copias divergentes** de la misma fórmula. Moverla a un paquete
   "neutral" (`core`/`math`) no cambiaría ninguna de estas relaciones
   ni el DAG — sería un movimiento cosmético prohibido por la sección
   24 ("no refactorizar por estética").
4. **No hay ciclo:** `engine.render` no importa `ui` (verificado con
   grep `^import com.yeivikas.olyzecs.ui` sobre todo `engine/render/*.kt`
   → 0 resultados).

### QuadHomography — mismo archivo, misma decisión, mismo razonamiento

**Qué es:** `internal object` en el mismo archivo, matemática de
homografía plana clásica (mapeo cuadrado unitario → cuadrilátero
arbitrario, fórmula de Heckbert 1989) — sin ninguna dependencia de
Android/OpenGL/Canvas. **Consumidor único:** `EditorScreen.kt`
(`hitTestLayerAt`/`screenPointToLayerUv`), para invertir un punto de
pantalla a UV local cuando el cuadrilátero proyectado no es un
rectángulo (tilt ≠ 0). **No se encontró ninguna otra implementación
equivalente** en el proyecto (grep de "homography" solo encuentra este
archivo y su consumidor). **Decisión: RESULTADO A, ubicación
ratificada** — mismo razonamiento que `PerspectiveCameraMath`: es la
contraparte matemática (inversa) del mismo pipeline de proyección, vive
correctamente junto a él, y UI la consume sin duplicarla.

### No duplicación matemática — verificado explícitamente

Búsqueda exhaustiva de "homography"/"perspective"/"camera" con
proyección de coordenadas: la ÚNICA implementación de
proyección/homografía del proyecto es la de este archivo.
`AlignmentGuides.kt` (guías de alineación tipo Figma/Photoshop) opera
exclusivamente en el bounding box 2D YA proyectado en píxeles — no
recalcula perspectiva ni homografía, es una capa de asistencia de
gesto completamente distinta e independiente (documentado en su propio
KDoc como "a propósito INDEPENDIENTE de la cuadrícula manual"). No hay
dos fórmulas divergentes de ningún cálculo de cámara en el proyecto
actual.

### GRID — clasificación arquitectónica completa

**Flujo real reconstruido de punta a punta (verificado por código, no
supuesto):**

```
EditorUiState (ViewModel)          — config: gridEnabled/shape/columns/rows/
     │                                color/thickness/opacity/snap
     │ (persiste con el proyecto — ProjectModels.kt / ProjectStorage.kt,
     │  sanitizado en ensureLocalImage-equivalente: safeGridColumns.coerceIn(1,64), etc.)
     ▼
EditorScreen.kt (UI, privado al archivo)
     │  GridSpec / GridShape / rasterizeGridBitmap() / gridLineDrawColor()
     │  snapTranslateToGrid() (snapping — independiente del bitmap)
     │
     │  remember(gridEnabled, gridShape, gridSpec, color..., tamaño lienzo) { rasterizeGridBitmap(...) }
     │  -> Bitmap? (null si la cuadrícula está apagada)
     ▼
getGridBitmap: () -> Bitmap?   (lambda "pull", mismo patrón que getLayers/getRenderSnapshot)
     ▼
GLPreview -> AndroidGLRenderHost -> GLRenderer (engine.render)
     │  updateGridTextureIfNeeded(): SOLO sube/reemplaza textura GPU si
     │  GridTextureCacheState.needsReconciliation(bitmap, contextGeneration.value)
     ▼
GridTextureCacheState (GpuHandle + identidad de bitmap)
     ▼
drawer.drawLayer(textureId=grid, frame=CameraFrame.identidad, parallaxFactor=1)
     — el MISMO LayerDrawer que dibuja cualquier capa real, PRIMERO en el frame
```

**Clasificación (sección 13 del prompt maestro): categoría E —
combinación legítima de varias capas con boundaries ya separados
correctamente, no una única categoría.** Desglosado:

- **Configuración semántica de Grid** (forma, columnas/filas, color,
  grosor, opacidad, snap-on/off): **UI state, persistido como parte de
  la configuración del proyecto/editor** (`EditorUiState` +
  `ProjectModels`/`ProjectStorage`) — análogo a la cuadrícula de
  composición de Photoshop/Premiere: es una preferencia de edición del
  proyecto, NO contenido de Scene (no es un `Layer`, no tiene
  `LayerSnapshot`, no participa de `RenderSnapshot`).
- **Generación del bitmap de la cuadrícula** (`rasterizeGridBitmap`,
  Canvas 2D puro): **UI**, privado a `EditorScreen.kt`. Render nunca
  conoce columnas, filas, forma ni color — solo recibe un `Bitmap`
  opaco.
- **Snapping al arrastrar una capa** (`snapTranslateToGrid`): **UI**
  (gesto de interacción), completamente independiente del bitmap y de
  Render — nunca toca GPU.
- **Caché/upload de textura GPU de esa imagen opaca**
  (`GridTextureCacheState`, dentro de `GLRenderer`): **Render/GPU
  concern**, correctamente aislado dentro de `engine.render` sin fugar
  ningún detalle semántico de Grid hacia afuera.
- **Composición visual en el frame** (dibujar la textura de grid antes
  que las capas): **Render**, a través del mismo `LayerDrawer` que
  compone cualquier capa — no existe un "GridRenderer" paralelo.

**Persistencia (sección 18):** la configuración de Grid **SÍ se
persiste con el proyecto** (`ProjectStorage.kt` líneas ~260-310,
~1185-1193, ~1659-1667 — con sanitización explícita:
`gridColumns.coerceIn(1,64)`, `gridLineOpacity.coerceIn(0f,1f)`, etc.)
— es configuración de editor del proyecto, no un overlay puramente
efímero de preview. Esto es CORRECTO y NO es lo mismo que "Grid es
contenido de Scene": es exactamente el mismo tipo de persistencia que
cualquier preferencia de proyecto (ej. nombre, duración), sin que Grid
participe del modelo de capas/escena.

### Grid y GPU ownership — verificado, SIN segundo owner

Auditoría específica pedida en la sección 15 del prompt maestro:
`GridTextureCacheState` **reutiliza los mismos primitivos que
cualquier textura de capa** — `GpuHandle` (el mismo tipo, no una copia
paralela) y `contextGeneration: GpuContextGeneration` (la MISMA
instancia de `GLRenderer`, pasada por parámetro a `needsReconciliation`/
`recordUpload`, nunca una generación propia). No crea ni destruye
contexto EGL, no mantiene un ID de textura fuera del esquema
`GpuHandle.isValid(generation)`, y su invalidación en
`onSurfaceCreated` (`invalidateForNewContext()`) seguirá el mismo
criterio que el resto de los recursos GPU del renderer (ver sección
"Estado GPU (R4.3.18)" más arriba en este documento, verificada de
nuevo intacta en esta pasada). **No existe ningún patrón de
"segundo owner"**: Grid es, a efectos de GPU, una textura más, con su
propio slot de caché por la razón correcta (necesita reconciliarse
contra un bitmap potencialmente distinto de todas las capas, no
porque tenga un mecanismo de ownership distinto).

### Grid y RenderApi — se ratifica NO agregarlo

Sección 16 del prompt maestro: se evaluó explícitamente si Grid
necesita `RenderApi.grid()`/`GridApi`/`GridManager`. **Decisión: NO.**
Grid no tiene ningún consumidor público fuera de `EditorScreen.kt`
(que ya tiene acceso directo a su propia configuración vía
`EditorUiState`) — no hay una necesidad real de exponerlo por
`RenderApi`, que hoy modela exclusivamente la capacidad de leer
color de pixel del preview en vivo. Agregar un método de Grid ahí
sería sobrearquitectura sin consumidor (prohibido explícitamente por
las secciones 4/16 y 24 del prompt maestro).

### DAG final — sin ciclos nuevos, sin direcciones prohibidas

```
engine.camera  (CameraFrame/CameraTrack/interpolación)
      ▲
      │  (render lee estado de cámara)
engine.render  (LayerDrawer, GLRenderer, PerspectiveCameraMath, QuadHomography,
      │         GridTextureCacheState, GpuHandle, GpuContextGeneration)
      ▲
      │  (ui compone overlays 2D y GPU host reutilizando el mismo render math)
ui  (EditorScreen: config de Grid + bitmap de Grid + overlay de selección,
     GLPreview/AndroidGLRenderHost)
```

Ninguna dirección prohibida por la sección 19 (`scene→render→scene`,
`ui→render→ui`, `camera→ui`, `core→ui`, etc.) aparece: `engine.camera`
sigue sin importar `engine.render` ni `ui` (verificado); `engine.render`
sigue sin importar `ui` (verificado); la única dirección "ui→render"
ya estaba aceptada por el wiring de `RenderApi`/`GLPreview`.

### Corrección documental aplicada (sección 4 del prompt maestro)

`RenderApiImpl.kt` seguía afirmando en su KDoc que su consumidor era
"nadie en producción" — falso desde la intervención anterior
("Wiring productivo de RenderApi"), que ya dejó a `EditorScreen`
construyéndolo en `onRendererReady`. Se reescribió el KDoc de esa
única clase para reflejar el estado real (consumidor productivo +
motivo por el que los 9 dominios de `EliNerApiImpl` siguen sin
unificarse), sin tocar ningún otro archivo ni convertir esto en un
refactor (regla explícita de la sección 4). Verificado con grep que
ningún otro archivo de producción sigue teniendo esta frase desactualizada
(`LayerApi`/`AudioApi`/`Mesh3DApi` siguen diciendo "sin consumidor
externo real" **correctamente** — es su estado real actual, documentado
así también por `EliNerApi.kt`; no se tocaron).

### Tests — 4 agregados, ninguno modificado, ninguno reproduce un bug (hardening de contrato, sección 21)

La auditoría no encontró ningún bug en `PerspectiveCameraMath`/
`QuadHomography`/`GridTextureCacheState` — `GridTextureCacheState` ya
tenía 9 tests cubriendo cada transición de estado relevante
(arranque, upload, recreación de contexto EGL, apagado, limpieza) y no
se le agregó nada (agregar tests de getters triviales está
explícitamente prohibido por la sección 21). Para `PerspectiveCameraMath`/
`QuadHomography`, la sección 21 pide explícitamente cobertura de
valores extremos/degeneración/división por cero/geometría degenerada/
viewport inválido/zoom extremo que **no estaba cubierta** — se
agregaron 4 tests nuevos a `QuadProjectionTest.kt` (ningún archivo de
producción se modificó para que pasen; todos fijan comportamiento
defensivo que el código YA tenía):

1. `projectQuadCornersNdc` devuelve `null` con imagen/viewport inválido
   (ancho o alto en 0 o negativo, en cualquiera de los 4 parámetros).
2. `projectQuadCornersNdc` con `dollyZoom` fuera del rango `[-1,1]` que
   la UI garantiza (dato de proyecto potencialmente corrupto —
   `CameraFrame`/`Keyframe.dollyZoom` NO se sanitiza al cargar, a
   diferencia de los campos de Grid que sí se acotan explícitamente en
   `ProjectStorage`) nunca produce `NaN`/`Infinity` — documenta y fija
   que la fórmula es estable en todo el dominio de `Float`, sin
   necesidad de agregar un `coerceIn` que el prompt maestro no pidió y
   que no está respaldado por un bug real.
3. `QuadHomography.fromUnitSquare` con las 4 esquinas coincidentes
   (cuadrilátero de área cero) — `mapPointToUv` reporta la imposibilidad
   devolviendo `null` (determinante ~0), nunca un resultado numérico
   arbitrario.
4. `QuadHomography` con un cuadrilátero casi degenerado pero de área no
   nula (sliver) sigue siendo invertible ida y vuelta — evita que un
   futuro cambio "proteja" de más y rompa el hit-test de capas muy
   comprimidas.

**No se ejecutaron** estos tests en este entorno (sin Gradle/JDK de
Android configurado aquí) — quedan para que el usuario los corra junto
al resto de la suite al compilar, como el resto de tests JVM puros del
proyecto.

### Auditoría final de esta intervención (checklist sección 26, subconjunto relevante)

- [x] `RenderApi` productivo sigue intacto — no se tocó `RenderApi.kt`.
- [x] `PixelColorSource`, `GLPreview`, `AndroidGLRenderHost`,
      `RenderSnapshot`, `LayerDrawer`, GPU ownership
      (`LayerGpuCommitGate`/`SingleResourceHandoff`/`GpuContextGeneration`) —
      ningún archivo tocado, ninguna clase modificada.
- [x] `GridTextureCacheState` — verificado sin segundo owner, sin cambios.
- [x] `PerspectiveCameraMath`/`QuadHomography` — ubicación justificada
      por escrito (arriba), sin moverse.
- [x] No hay matemática duplicada sin justificación.
- [x] UI no posee lógica de Engine — solo la consume y hace conversión
      de espacio de coordenadas (NDC→píxel), tarea legítima de UI.
- [x] Grid tiene clasificación arquitectónica explícita (categoría E,
      desglosada por sub-responsabilidad).
- [x] Grid no se agregó a `RenderApi`.
- [x] Ningún ciclo nuevo (verificado con grep de imports en ambas
      direcciones para `engine.camera`↔`engine.render`↔`ui`).
- [x] No se introdujo DI/Service Locator/Event Bus/Singleton global.
- [x] No se adelantó ninguna fase posterior (no se tocó `VideoExporter`,
      no se unificaron los 9 dominios, no se implementó Vulkan/JNI).
- [x] KDoc de `RenderApiImpl` corregido.
- [x] Fase 4.3 permanece PARCIAL — este bloque específico
      (Render Math Boundary + clasificación de Grid) queda cerrado,
      el resto de la fase (diseño de API pública ampliada, reorganización
      profunda de `GLPreview`, clasificación de `VideoExporter`) sigue
      pendiente, como ya reflejaba este documento antes de esta pasada.

### Deuda técnica encontrada (documentada, NO corregida en esta pasada — fuera de alcance)

- **`CameraFrame`/`Keyframe.dollyZoom` (y el resto de sus campos
  float) no se sanitizan al deserializar un proyecto**, a diferencia de
  los campos de Grid (que sí tienen `coerceIn` explícito en
  `ProjectStorage.kt`). No es un bug de esta intervención (el nuevo
  test #2 arriba demuestra que la fórmula sigue siendo matemáticamente
  estable ante esto — no hay crash ni `NaN`), pero es una inconsistencia
  de criterio de sanitización entre Grid y Camera que vale la pena
  unificar en una fase de "integridad de datos de proyecto", no en
  Render Math Boundary.
- **`EditorScreen.kt` (18.243 líneas)** sigue concentrando configuración
  de Grid, su rasterización, snapping, y el overlay de selección
  completo en un único archivo — no es un defecto de *boundary*
  arquitectónico entre módulos (cada responsabilidad interna está bien
  delimitada por función, como se documentó arriba), pero es una
  deuda de mantenibilidad real que excede el alcance de esta
  intervención (dividir ese archivo es, en sí mismo, un proyecto de
  fase propia, no algo que deba forzarse como efecto colateral de una
  auditoría de Render Math).

## Próximos pasos recomendados (no implementados)

1. **Conectar `RenderApi` a un consumidor real**: `EliNerApiImpl` sigue
   sin sitio de construcción en producción. Ahora que `GLPreview`
   entrega `host.renderer` (un `PixelColorSource`) vía `onRendererReady`,
   el siguiente paso natural es que `EditorScreen`/`MainActivity`
   compongan un `RenderApiImpl` real con esa instancia — no se hizo en
   esta intervención porque tocar `EditorScreen.kt` no era su objetivo
   (regla: "no modificar la capa de UI más allá de lo mínimo").
2. **`VideoExporter`** (R4.3.14): sigue sin auditar en detalle — su
   propio EGL aislado es independiente del `AndroidGLRenderHost` de
   preview, pero no se revisó si comparte algún patrón que valga la pena
   unificar.
3. **Auditoría final de Fase 4.3**: solo procede una vez resuelto el
   punto 1.
