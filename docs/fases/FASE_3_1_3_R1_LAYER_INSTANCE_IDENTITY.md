# FASE 3.1.3-R1 — Cierre de identidad de instancia de Layer durante el
# Atomic GPU Commit

> Continuación directa de Fase 3.1.3 — no la rehace, cierra un defecto
> puntual que quedó abierto en su validación final.
>
> **ADDENDUM (Fase 3.1.3-R2):** esta fase capturaba `sourceUri`/
> `contentRevision` como dos campos separados y validaba en una sola
> pasada. Fase 3.1.3-R2 (`docs/fases/FASE_3_1_3_R2_COMMIT_GATE.md`)
> reemplaza esa captura por un único token atómico
> (`Layer.ContentIdentity`) y agrega una segunda validación, adyacente al
> commit — cierra dos defectos más sutiles que esta fase no cubría. El
> mecanismo de identidad REFERENCIAL descrito acá (secciones 3-5) sigue
> vigente sin cambios.

## 1. El defecto real

La validación final de Fase 3.1.3 (`uploadTextureIfNeeded`) comparaba:

```kotlin
val stillCurrent = layer.sourceUri == capturedSourceUri &&
    layer.contentRevision == capturedRevision &&
    contextGeneration.value == capturedContextGeneration
```

`layer` acá es la MISMA referencia que se capturó al principio de la
función — nunca se volvía a preguntar si esa instancia **sigue siendo la
capa viva del proyecto**. Esto era suficiente para detectar cambios de
`sourceUri`/`contentRevision` cuando el código **muta esa misma
instancia** (undo/redo, `revertLayerEditSession`, resolución de URIs
locales — todos estos flujos, auditados, mutan el objeto `Layer` en el
lugar), pero era **insuficiente** para un caso real y distinto:
`removeLayer()` no muta ningún campo del objeto que elimina, simplemente
lo saca de `_uiState.value.layers`. Si `GLRenderer` ya tenía una
referencia capturada a ese objeto (por un upload en curso), comparar
`layer.sourceUri`/`layer.contentRevision` contra sí mismos siempre iba a
dar **verdadero** — la validación pasaba trivialmente para una capa
fantasma, ya eliminada del proyecto.

Un caso más sutil, también real: reemplazar la capa por una instancia
**distinta** (vía `.copy()`) que por coincidencia tuviera el mismo
`sourceUri` y la misma `contentRevision` numérica. La comparación por
valores, sola, tampoco distingue esto de "nada cambió".

## 2. Por qué `layerId + sourceUri + contentRevision + contextGeneration` no alcanzaba

Esos cuatro valores describen **qué versión de qué contenido** representa
una capa — pero no responden "¿este objeto en particular sigue siendo
_el_ objeto vivo?". Un objeto eliminado sigue teniendo, para siempre, los
mismos valores que tenía en el momento de eliminarse — comparar valores
contra sí mismo no puede detectar la propia eliminación.

## 3. Solución aplicada: identidad referencial (opción A del prompt)

Se adoptó la solución preferida explícitamente por el encargo: comparación
de identidad **referencial** (`===`) entre la instancia capturada y la
instancia viva, obtenida con una **búsqueda fresca** por `layerId` — nunca
reutilizando la referencia capturada para leer sus propios campos.

`GLRenderer.currentLayerIfStillRequested` (ya existía desde Fase 3.1.2,
usada por el redecode de `onSurfaceCreated` y el redecode perezoso) se
extendió con un parámetro opcional `expectedInstance: Layer?`:

```kotlin
private fun currentLayerIfStillRequested(
    layerId: String,
    requestedUri: Uri,
    requestedRevision: Int,
    expectedInstance: Layer? = null
): Layer? =
    getLayers().firstOrNull { it.id == layerId }
        ?.takeIf { it.sourceUri == requestedUri && it.contentRevision == requestedRevision }
        ?.takeIf { expectedInstance == null || it === expectedInstance }
```

Se usa, con `expectedInstance = layer` (la instancia capturada), en los
**tres** puntos donde el código produce o comete un recurso a partir de
una capa:

1. El redecode de `onSurfaceCreated`.
2. El redecode perezoso (`performLazyRedecodeIfNeeded`).
3. **La validación final del commit atómico** (`uploadTextureIfNeeded`) —
   el punto que realmente le faltaba, y el objetivo central de esta fase.

## 4. Por qué esto es compatible con undo/redo

`EditorViewModel.restoreSnapshot` (el código real, verificado línea por
línea) **muta la misma instancia en el lugar**:

```kotlin
val layer = current.find { it.id == edit.id } ?: return@forEach
...
layer.sourceUri = edit.sourceUri
layer.contentRevision = edit.contentRevision
```

No hace `.copy()`. Por lo tanto, para undo/redo, `liveLayer === capturedLayer`
sigue siendo **verdadero** (es el mismo objeto) — la identidad referencial
no rompe nada acá. Lo que sí puede rechazar la operación es la
comparación por VALORES (`sourceUri`/`contentRevision`), exactamente como
antes de esta fase. Esta distinción — **identidad** (`===`, cambia con
`removeLayer`/reemplazo real) vs. **valores** (`==`, cambia con
undo/redo/edición) — es la propiedad central que esta fase formaliza y
prueba con tests.

## 5. Propiedad documentada, no asumida: revisión numérica que "vuelve"

El encargo pide explícitamente no asumir que un regreso numérico a una
revisión vieja es automáticamente inválido. Se verificó (TEST 26): si el
undo deja a la MISMA instancia con el `sourceUri` y la `contentRevision`
**exactamente** iguales a los capturados al principio del upload, el
sistema **acepta** el commit — porque, con la identidad disponible
(`Layer instance` + `sourceUri` + `contentRevision`), es indistinguible de
"nunca cambió". Esto es correcto y deseado: `EditorViewModel.restoreSnapshot`
restaura la `contentRevision` **exacta** de cada punto del historial (ver
`LayerEditState.contentRevision`), nunca un contador monotónico global sin
retorno — volver al mismo punto del historial es, legítimamente, volver
al mismo estado.

## 6. Otro defecto encontrado en la misma auditoría (Problema 17 del informe original)

Al revisar `GpuTextureLimits.clampForTexture()` (pedido explícito del
encargo, sección 17) se encontró que la llamada corría **fuera** del
bloque `try` de `uploadTextureIfNeeded` — una excepción ahí (p. ej. sin
memoria en `Bitmap.createScaledBitmap`) escapaba de la función SIN
reciclar el bitmap ya consumido (`original`, único dueño desde
`takeIfCurrent`) y rompía la garantía documentada de "cada capa se sube
de forma aislada, si una falla las demás se siguen dibujando" (la
excepción escapaba hasta el llamador de `onDrawFrame`, no se quedaba
contenida en esta capa). Se corrigió envolviendo también esa llamada,
con el mismo criterio de disposición explícita.

## 7. Archivos modificados

- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GLRenderer.kt` —
  `currentLayerIfStillRequested` (parámetro `expectedInstance`), sus tres
  call sites, la validación final de `uploadTextureIfNeeded` (ahora
  reusa ese helper en vez de releer `layer` directamente), y el fix de
  `clampForTexture` fuera del `try`.
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/LayerTextureRegistryScenarioTest.kt`
  — reescrito: el fake ahora distingue **instancia** (`FakeLayerInstance`,
  identidad referencial real) de **proyecto** (`FakeProjectState`, qué
  instancia es la viva por id) — antes solo existía un `LiveLayerState`
  mutable sin distinción de identidad, incapaz de modelar el bug real.

## 8. Tests agregados

| Test | Qué prueba |
|---|---|
| TEST 21 | **El caso central**: mismo `layerId`, instancia reemplazada por otra con los MISMOS valores — se rechaza solo gracias a la identidad referencial |
| TEST 22 | Misma instancia, revisión viva distinta a la capturada — rechazo por valor |
| TEST 23 | Misma instancia, mismo número de revisión, `sourceUri` distinto — rechazo por valor |
| TEST 26 (acepta) | Undo que restaura EXACTAMENTE el estado capturado (misma instancia) — se acepta, documentado como propiedad conocida del diseño |
| TEST H | Undo/redo con uploads en curso, revisiones nuevas en cada paso — todo el registro queda consistente |
| TEST 27 | Rollback con textura anterior existente — se preserva intacta |
| TEST 28 | Rollback sin textura anterior — registro queda vacío |
| TEST 29 | Camino feliz: la corrección de identidad no bloquea un upload legítimo |
| TEST 16 | `widthPx`/`heightPx` (`FakeLayerInstance.dimensions`) nunca quedan contaminados por un upload que terminó en rollback — solo reflejan el último commit válido |
| TEST E (actualizado) | El escenario de `removeLayer()` durante upload — ahora explícitamente vía `project.liveLayers.remove(id)`, no solo una bandera booleana ficticia |

Ninguno de estos tests simula una función ficticia devolviendo
`true`/`false` sin relación con el código real: `FakeLayerTextureRegistry.processLayer`
reproduce, línea por línea, la misma secuencia y las mismas comparaciones
que `GLRenderer.uploadTextureIfNeeded` + `currentLayerIfStillRequested`.

## 9. Limitaciones de los tests JVM/fake frente a GL real

Estos tests corren en JVM puro (JUnit, sin Robolectric ni un contexto EGL
real) — `FakeGpuUploads` es un contador incremental, no `glGenTextures`/
`glDeleteTextures` reales, y `FakeProjectState`/`FakeLayerInstance` son
una copia deliberadamente simplificada (mismos campos relevantes, mismas
comparaciones) de `Layer`/`EditorViewModel.getLayers()`, no el código de
producción ejecutado directamente. Esto significa que estos tests
verifican la LÓGICA del protocolo (secuencia de comparaciones, qué gana y
qué se descarta) pero no ejercitan el código real de `GLRenderer.kt` línea
por línea, ni condiciones de carrera reales de hilos GLES/EGL — la
verificación de que la lógica del fake coincide con la del código real se
hizo por revisión manual, comparando ambos archivos línea por línea (ver
sección 3).

## 9.1. Re-verificación explícita de archivos sin cambios de código

El encargo pide auditar explícitamente varios archivos aunque no
requieran modificación. Se revisaron línea por línea, con estos
resultados:

- **`SingleResourceHandoff.kt`** — sin cambios desde Fase 3.1.2.
  `publish()`/`takeIfCurrent()`/`takeRaw()`/`clear()`/`peek()` siguen
  siendo consistentes con el protocolo de esta fase (`takeIfCurrent`
  sigue siendo el único punto de consumo con validación de revisión).
- **`GpuTextureLimits.clampForTexture()`** — se verificó explícitamente
  la secuencia de ownership ante una excepción: si
  `Bitmap.createScaledBitmap` lanza, el `bitmap.recycle()` interno de
  `clampForTexture` (línea siguiente a la llamada que lanzó) NUNCA se
  ejecuta — por lo tanto el `original.recycle()` agregado en el
  `catch` de `GLRenderer.uploadTextureIfNeeded` (sección 6) es el
  ÚNICO recycle de ese bitmap, sin riesgo de doble-recycle.
- **Todos los `.recycle()` de `GLRenderer.kt`** (8 llamadas en total, al
  cierre de esta fase) se rastrearon una por una para confirmar que cada
  `Bitmap` tiene exactamente un dueño en cada instante y se dispone
  exactamente una vez — ninguna ruta de excepción ni de rollback deja un
  bitmap sin reciclar ni lo recicla dos veces.
- **`updateGridTextureIfNeeded()` (ownership del Grid)** — sin cambios de
  código en esta fase; se confirmó que el protocolo nuevo de
  `uploadTextureIfNeeded` no lo toca en absoluto (son funciones
  independientes, el Grid nunca pasa por `SingleResourceHandoff` ni por
  `layerTextures`).

## 10. Riesgos residuales

1. **`persistNow()`** (resolución diferida de URIs locales,
   `EditorViewModel.kt`) hace una búsqueda fresca de la capa viva por id
   y solo escribe si el `sourceUri` capturado antes del guardado coincide
   con el vigente — pero no verifica identidad referencial. Si una capa
   fuera reemplazada por una instancia distinta con el MISMO `sourceUri`
   exacto (coincidencia posible pero de bajo impacto: la única
   consecuencia es que la ruta local resuelta se aplique sobre la
   instancia nueva en vez de la vieja, y ambas representan, por
   hipótesis, el mismo contenido) — no se corrigió, por ser un camino de
   optimización de guardado, no del pipeline de render, y estar fuera del
   alcance explícito de esta fase.
2. Los riesgos residuales ya señalados en Fase 3.1.2/3.1.3
   (`layer.widthPx`/`layer.heightPx` mutados directamente por
   `GLRenderer`; redecode perezoso síncrono en el hilo de GL; ausencia de
   tests de integración reales con Robolectric) siguen vigentes, sin
   cambios en esta fase.

## 11. Estado final de Fase 3.1.3-R1

**Cerrada.** La garantía central pasa a ser: *un recurso GPU solo puede
comprometerse cuando la instancia de `Layer` que inició el trabajo sigue
siendo, por identidad referencial Y por valores, la capa viva del
proyecto*. No se declara "100% libre de condiciones de carrera" de forma
absoluta — se documentan, explícitamente, los casos donde el diseño
actual (con la identidad disponible) no puede ni necesita distinguir un
estado de otro idéntico (sección 5), y el riesgo residual de la sección
10.

No se avanza a Fase 4.
