# FASE 3.1.2 — Cierre definitivo de GPU resource ownership, Bitmap
# ownership, stale resource races y content revision

> Auditoría real del código (no de la documentación previa, por mandato
> explícito de esta fase) sobre el estado dejado por Fase 3.1 / 3.1.1.

## 1. Resumen técnico

Fase 3.1 resolvió la corrupción de datos del *pending bitmap handoff*
(lectura+escritura separadas) y sacó el `glTextureId` de `Layer`. Fase
3.1.1 corrigió tres regresiones funcionales reales (undo de reemplazo de
imagen, capas transparentes al editar, memory leak simulado en modo
edición aislado). Pero ninguna de las dos cerraba el problema de fondo
que pedía esta fase: **nada impedía que un resultado de decode OBSOLETO
— llegado tarde, de una versión de contenido que ya no es la vigente —
terminara convirtiéndose en la textura GPU visible de una capa.**

Esta fase introduce una identidad de versión real (`Layer.contentRevision`)
y la usa como condición de validación en el único punto donde un
resultado de decode se convierte en un recurso GPU (`SingleResourceHandoff.takeIfCurrent`
+ `GLRenderer`), además de cerrar cuatro fugas de Bitmap reales
encontradas en la auditoría (Problema 2 y 4 del checklist original).

## 2. Archivos modificados

- `app/src/main/java/com/yeivikas/olyzecs/engine/render/SingleResourceHandoff.kt` — reescrito.
- `app/src/main/java/com/yeivikas/olyzecs/engine/scene/Layer.kt` — agregado `contentRevision`.
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GLRenderer.kt` — `LayerTextureRecord`, `currentLayerIfStillRequested`, `performLazyRedecodeIfNeeded`, `onSurfaceCreated`, `onDrawFrame`, `uploadTextureIfNeeded`.
- `app/src/main/java/com/yeivikas/olyzecs/viewmodel/EditorViewModel.kt` — `LayerEditState`/`LayerContentState` con `contentRevision`; `restoreSnapshot`; `applyTo`; `replaceLayer`/`replaceLayers` + nuevo helper `transferPendingResourceOwnership`; `previewLayerRecolor`; `commitLayerRecolor`; `revertLayerEditSession`; `revertLayerToUri`; `replaceLayerImage` (+ nuevo `pendingImageReplaceSequence`); `removeLayer`; el bloque de degradado múltiple (`applyGradientColorTo…`/línea ~2568).
- `app/src/main/java/com/yeivikas/olyzecs/data/LayerRepository.kt` — `publish()` con revisión.
- `app/src/main/java/com/yeivikas/olyzecs/data/ProjectStorage.kt` — `publish()` con revisión.
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/SingleResourceHandoffTest.kt` — reescrito.
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/LayerTextureRegistryScenarioTest.kt` — reescrito.
- `app/src/test/java/com/yeivikas/olyzecs/engine/scene/LayerGpuOwnershipStructureTest.kt` — un test nuevo agregado, nada eliminado.

## 3. Archivos nuevos

Ninguno — el diseño se integró en las clases existentes, siguiendo la
instrucción explícita de no introducir arquitectura nueva innecesaria.

## 4. Problemas del informe: estado de cada uno

| # | Problema | Estado |
|---|---|---|
| 1 | Stale redecode/stale texture | **Cerrado** — `contentRevision` + `SingleResourceHandoff.takeIfCurrent` |
| 2 | Bitmap sin dueño en `SingleResourceHandoff` | **Cerrado** — `publish()` devuelve el anterior, nunca lo descarta en silencio |
| 3 | `onSurfaceCreated` vs reemplazo de Layer | **Cerrado** — `currentLayerIfStillRequested` valida antes de publicar |
| 4 | `removeLayer` con pending bitmap | **Cerrado** — se dispone explícitamente en `removeLayer` |
| 5 | `replaceLayer` (preserveRenderState true/false) | **Cerrado** — `transferPendingResourceOwnership` + disposal explícito del `old` en el camino `false` |
| 6 | GPU texture identity | **Cerrado** — `LayerTextureRecord(glId, contentRevision)`, segunda línea de defensa en `onDrawFrame` |
| 7 | Context recreation | **Sin cambios de diseño** (ya estaba bien: `layerTextures.clear()` sin `delete()`) — reforzado con `failedRedecodeIds.clear()` y el nuevo TEST 6 |
| 8 | Grid bitmap ownership | **Sin cambios** (ya era correcto, no usa `SingleResourceHandoff`) — verificado que sigue intacto |
| 9 | Bitmap ownership global | Ver matriz en la sección 6 |
| 10 | `@Volatile` como solución de arquitectura | Se evitó a propósito: `contentRevision` es la estructura que faltaba, no un `@Volatile` más |

## 5. Diseño final de ownership

```
UI / ViewModel (Main thread)
      │
      │  Layer inmutable-por-copia + contentRevision
      │  (sourceUri, contentRevision) es la IDENTIDAD DE VERSIÓN
      ▼
SingleResourceHandoff<Bitmap>  (frontera de traspaso)
      │
      │  publish(bitmap, revision) — nunca pierde el anterior
      │  takeIfCurrent(revision)   — único punto de validación
      ▼
GLRenderer (GL thread)
      │
      ├── decode (síncrono, en este hilo)
      ├── validar: currentLayerIfStillRequested(id, uri, revision)
      ├── validar: takeIfCurrent(layer.contentRevision) → Ready/Stale/Empty
      └── upload → LayerTextureRecord(glId, contentRevision)
             │
             ▼
       layerTextures (registro privado de GLRenderer)
```

```
Layer ID  (identidad de la CAPA — estable de por vida)
   +
Content Revision  (identidad del CONTENIDO — sube con cada sourceUri nuevo)
   +
Context Generation  (identidad del CONTEXTO EGL — GpuContextGeneration, sin cambios)
   ↓
Validez del recurso: solo si las tres coinciden con lo vigente
```

## 6. Matriz de ownership de Bitmap (Problema 9)

| Recurso | Owner | Thread que lo crea | Transferencia | Disposal |
|---|---|---|---|---|
| Bitmap decodificado en `replaceLayerImage`/`LayerRepository.decode` | El decoder hasta `publish()` | IO (coroutine) | `pendingBitmap.publish(bitmap, revision)` → GL | `takeIfCurrent` → GLRenderer recicla en `uploadTextureIfNeeded` (finally) o al descartar `Stale` |
| Bitmap de preview en `previewLayerRecolor` | El caller hasta `publish()` | Main (UI, cada frame de arrastre) | `pendingBitmap.publish(bitmap, revision)` | Igual que arriba; el anterior sin consumir se recicla en el propio `previewLayerRecolor` vía el valor de retorno de `publish` |
| Bitmap "reemplazado" en cualquier `publish()` (Problema 2) | Quien lo publicó originalmente, hasta que alguien llama `publish`/`clear`/`takeRaw`/`takeIfCurrent` | El que corresponda | Ninguna — se dispone donde cae | El caller que lo desplaza (recibe el valor de retorno) |
| Bitmap decodificado en `onSurfaceCreated`/`performLazyRedecodeIfNeeded` | GL thread hasta validar | GL | Solo si `currentLayerIfStillRequested` lo aprueba | Se recicla ahí mismo si la validación falla (`Stale` conceptual, aunque acá el chequeo es previo al publish, no via `takeIfCurrent`) |
| Bitmap externo (Grid — Compose) | Compose/UI, SIEMPRE | Main | `GridTextureCacheState` lee, nunca posee | GLRenderer JAMÁS lo recicla (ver Problema 8, sin cambios) |
| Bitmap ya subido a GPU (`uploadTextureIfNeeded`) | GLRenderer, transitoriamente | GL | N/A — vive y muere en la misma llamada | `finally { bitmap.recycle() }`, siempre, éxito o error |

No queda ningún Bitmap relacionado con render sin una fila de esta tabla.

## 7. Diseño de `ContentRevision`

- Vive en `Layer.contentRevision: Int`, `@Volatile`, parámetro normal del
  constructor (no `@Transient`) — así todo `.copy()` que no lo menciona
  explícitamente lo PRESERVA automáticamente.
- Se incrementa a mano (`it.contentRevision + 1`) únicamente en los
  sitios que representan un cambio REAL de la imagen: `replaceLayerImage`,
  `commitLayerRecolor`, `revertLayerEditSession`, `revertLayerToUri`.
- Se RESTAURA (no se incrementa) al valor exacto capturado en el
  historial en `restoreSnapshot` (undo/redo) y `LayerContentState.applyTo`
  (descartar cambios) — deshacer un cambio de imagen debe devolver la
  identidad de versión real de ese punto, no inventar una nueva.
- NO se toca en la re-resolución de URIs relativas a copias locales
  (`EditorViewModel` líneas ~1521/1528): mismo contenido, mismo puntero
  relocalizado, no amerita nueva versión — y de hecho tampoco se pide
  invalidación de textura ahí, coherente.

## 8. Rechazo de resultados obsoletos (stale-result rejection)

Dos mecanismos independientes, cada uno cerrando una mitad del problema:

1. **`pendingImageReplaceSequence`** (en `EditorViewModel`, solo para
   `replaceLayerImage`): un contador por capa, reservado SINCRÓNICAMENTE
   en el momento del tap, antes de arrancar el decode asíncrono.
   Garantiza que gane el ÚLTIMO PEDIDO (orden de negocio correcto),
   independientemente del orden en que terminen los decodes en el pool de
   IO.
2. **`SingleResourceHandoff.takeIfCurrent` + `Layer.contentRevision`**
   (en el límite de render, `GLRenderer`): garantiza que NINGÚN resultado
   pueda convertirse en textura GPU si su revisión etiquetada no coincide
   con la vigente — funciona para CUALQUIER camino que publique un
   bitmap, no solo `replaceLayerImage`, incluyendo casos que (1) no cubre
   (redecodes lanzados por el propio `GLRenderer`).

## 9. Política de disposal de Bitmap

`SingleResourceHandoff.publish(value, revision)` y `.clear()` **siempre**
devuelven cualquier valor previo sin consumir. Ningún call site del
proyecto ignora ese valor de retorno sin encadenar `?.recycle()` — se
verificó uno por uno en la auditoría final (sección 12).

## 10. Política de disposal de GPU resource

Sin cambios de fondo respecto a Fase 3.1: sigue siendo exclusiva de
`GLRenderer.layerTextures`, nunca de `Layer`. Se agregó una segunda vía
de invalidación (revisión desincronizada, sin pedido explícito) como red
de seguridad, no como reemplazo del pedido explícito normal.

## 11. Política de context recreation

Sin cambios de diseño (`GpuContextGeneration`/`GpuHandle`/
`GridTextureCacheState` se preservan intactos, según instrucción
explícita). Se reforzó: `failedRedecodeIds` se limpia también en cada
`onSurfaceCreated`, y el nuevo TEST 6 (`LayerTextureRegistryScenarioTest`)
confirma que un resultado del contexto viejo nunca puede "colgarse" del
registro del contexto nuevo.

## 12. Auditoría final (grep obligatorio)

Se repitió, sobre el código YA modificado, la búsqueda de:
`pendingBitmap`, `glTextureId`, `layerTextures`, `sourceUri`,
`contentRevision`, `revision`, `recycle()`, `SingleResourceHandoff`,
`replaceLayer`, `removeLayer`, `restore`, `undo`, `redo`,
`onSurfaceCreated`, `onDrawFrame`. Resultado: **cero** llamadas a
`.publish(` con la firma vieja de un solo argumento, **cero** llamadas a
`.takeForConsumption()` (eliminado de la API), **cero** asignaciones
directas a un campo `glTextureId` en `Layer` (no existe el campo), y
todos los `.sourceUri = ` directos identificados (`EditorViewModel`
líneas 270/1174 con bump de revisión, líneas 1521/1528 sin bump —
justificado en la sección 7) están auditados y documentados.

## 13. Tests agregados

- `SingleResourceHandoffTest`: TEST 1, TEST 2, TEST 3, TEST 8, TEST 10
  del checklist (pura lógica de `SingleResourceHandoff`), más un test de
  concurrencia real multi-hilo (invariante: la suma de "descartados por
  publish" + "lo que queda al final" siempre es igual a la cantidad de
  publicaciones).
- `LayerTextureRegistryScenarioTest`: TEST 5, TEST 6, TEST 7, TEST 8
  (integrados con el registro de texturas, no solo el handoff aislado).
- `LayerGpuOwnershipStructureTest`: un test estructural nuevo confirmando
  que `contentRevision` existe como `Int` `@Volatile`.

TEST 4 (remove while pending) y TEST 9 (grid bitmap) ya estaban cubiertos
— el primero de forma indirecta por la lógica de `removeLayer` (no hay
infraestructura de test de ViewModel con Robolectric en este proyecto
para instanciar `EditorViewModel` real; se dejó documentado el
razonamiento en el código de `removeLayer` en vez de duplicar
infraestructura de test nueva, fuera del alcance de "no refactorizar
masivamente"), el segundo por `GridTextureCacheStateTest` ya existente
(no tocado).

## 14. Tests modificados

`SingleResourceHandoffTest` y `LayerTextureRegistryScenarioTest`
reescritos a la nueva firma (`revision` obligatorio en `publish`,
`takeIfCurrent` en vez de `takeForConsumption`). Ningún test existente
fue eliminado — todos los escenarios de Fase 3.1 (subida, reemplazo,
poda por capa eliminada, poda por contexto nuevo, invalidación por pedido
de UI, no doble consumo) se preservaron y se re-expresaron con la API
nueva.

## 15. Riesgos residuales (no se declara "100% solucionado")

1. **`performLazyRedecodeIfNeeded`/`onSurfaceCreated` decodifican de
   forma SÍNCRONA en el hilo de GL.** Para una sola capa invalidada esto
   es aceptable, pero con muchas capas grandes invalidándose a la vez
   (poco común, pero posible: p. ej. "Multicolor" con degradado sobre
   muchas capas simultáneamente si alguna quedara sin bitmap pendiente)
   podría introducir un frame drop perceptible. No se movió a una
   corrutina de IO en esta fase por estar fuera del alcance explícito
   ("no rompas la fluidez, pero tampoco hace falta resolver performance
   todavía") — queda señalado para una fase futura si se mide un
   problema real.
2. **`pendingImageReplaceSequence` es un mapa que crece con cada capa
   ÚNICA que alguna vez tuvo un reemplazo de imagen en la sesión**, y se
   poda solo cuando esa capa se elimina (`removeLayer`). No hay una vía
   de fuga real (el número de capas de un proyecto es acotado y
   pequeño), pero es una estructura más para tener en cuenta si en el
   futuro se audita memoria del ViewModel.
3. **No se agregó un test de integración real contra `EditorViewModel`**
   (por ejemplo, para TEST 4/TEST 5 tal como los describe el checklist,
   con un `EditorViewModel` real end-to-end) porque el proyecto no tiene
   hoy infraestructura de test con Robolectric/coroutines-test para
   `EditorViewModel` (que depende de `android.net.Uri`, `Bitmap`,
   `Context`) — agregar esa infraestructura sería, en sí mismo, un
   cambio de alcance mayor al de esta fase ("no refactorices masivamente
   EditorViewModel/EditorScreen"). Los mismos escenarios están cubiertos
   a nivel de la lógica pura que los sostiene
   (`SingleResourceHandoff`/`LayerTextureRegistryScenarioTest`).
4. **`glFinish()` en `uploadTexture()` no se tocó**, tal como pedía la
   instrucción explícita de priorizar correctness sobre performance en
   esta fase.

## 16. Estado final de Fase 3.1.2

**Cerrada**, con los riesgos residuales de la sección 15 señalados
explícitamente (ninguno afecta la garantía central: ningún resultado de
decode obsoleto puede convertirse en un recurso GPU visible — eso sí
está garantizado, y con tests que lo verifican, no solo documentado).

No se avanza a Fase 4.
