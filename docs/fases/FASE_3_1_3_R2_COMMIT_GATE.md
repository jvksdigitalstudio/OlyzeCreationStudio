# FASE 3.1.3-R2 — Atomic GPU Commit / Commit Identity Finalization

> Corrección quirúrgica sobre Fase 3.1.3-R1. No la rehace.

> **ACTUALIZACIÓN — FASE 3.1.3-R3 (ver `FASE_3_1_3_R3_COMMIT_GATE.md`):**
> este documento describe con precisión lo que R2 SÍ cerraba (identidad
> de contenido/instancia/contexto EGL consistente en una lectura atómica)
> y es honesto sobre lo que NO cerraba (sección 4 más abajo, punto por
> punto). Pero para evitar cualquier lectura ambigua a futuro se deja
> constancia explícita, en este mismo lugar, de la corrección que pide la
> auditoría de R3: **la "compuerta en dos etapas" de R2 (dos lecturas
> consecutivas de `stillAuthorizedToCommit()`) NO es, y nunca fue,
> sincronización formal ni atomicidad real** — es una defensa optimista
> que reduce la ventana `validation → commit` al mínimo estructural
> (cero sentencias intermedias entre la segunda lectura y la escritura),
> pero sigue siendo, literalmente, dos lecturas sin ninguna primitiva de
> exclusión mutua entre ellas. R2 nunca afirmó "race-free" — ver la
> sección 4 de abajo, que ya lo dejaba explícito — pero si alguna lectura
> futura de este documento llegara a interpretar "compuerta"/"commit
> gate" como sinónimo de un lock real, esa lectura sería incorrecta: el
> "gate" de R2 es una figura de lenguaje para el patrón de doble
> validación, no un objeto de sincronización. El cierre real, con un
> `LayerGpuCommitGate` (`ReentrantLock`) compartido entre el hilo de GL y
> el hilo principal, es responsabilidad exclusiva de R3.

## 1. Auditoría del modelo de concurrencia real (obligatoria antes de tocar código)

Antes de diseñar nada, se confirmó explícitamente:

- `GLRenderer.onSurfaceCreated()`/`onDrawFrame()` — siempre en el hilo de
  GL. `GLSurfaceView.Renderer` garantiza, por contrato de Android, que
  estos callbacks corren siempre en el MISMO hilo y nunca se solapan
  entre sí (confirmado ya en Fase 3.1.3 para el análisis de
  `contextGeneration`).
- `EditorViewModel` — todas las mutaciones de estado (`replaceLayerImage`,
  `removeLayer`, `restoreSnapshot`, `LayerContentState.applyTo`) corren en
  el hilo Main (dispatcher por defecto de `viewModelScope.launch`), salvo
  el propio `decode()` (que sí usa `Dispatchers.IO`, pero el resultado se
  aplica de vuelta en Main).
- `getLayers()` devuelve `_uiState.value.layers` — una lista inmutable
  (snapshot congelado en el instante de la lectura). Los objetos `Layer`
  DENTRO de esa lista, sin embargo, pueden tener campos `var` mutables
  que SÍ cambian en el lugar después de esa lectura (`restoreSnapshot`,
  `LayerContentState.applyTo` — confirmado por auditoría directa del
  código, no asumido).
- `layerTextures` — mapa privado de `GLRenderer`, tocado EXCLUSIVAMENTE
  desde el hilo de GL. No hay ninguna escritura concurrente sobre el
  mapa en sí; el riesgo nunca fue el mapa, fue la DECISIÓN de qué
  escribir en él, basada en leer campos mutables de `Layer` desde el
  hilo de GL mientras el hilo Main podía escribirlos en paralelo real.

Con esta auditoría confirmada, el problema real no es "dos hilos tocan
el mismo mapa" (no lo hacen) — es "el hilo de GL toma una decisión
basada en el estado de un `Layer` compartido, y ese estado puede cambiar
entre que se lee y que se usa esa lectura".

## 2. El defecto exacto que Fase 3.1.3-R1 dejaba abierto

La validación de R1 comparaba:

```kotlin
val stillCurrent = layer.sourceUri == capturedSourceUri &&
    layer.contentRevision == capturedRevision && ...
```

Antes de esta fase eso ya se había corregido para usar una búsqueda
fresca por id + identidad referencial. Pero quedaban DOS problemas
distintos, ambos reales:

**2.1 — Lectura entrecortada de un par de campos independientes.**
`sourceUri` y `contentRevision` son dos campos `@Volatile`
INDEPENDIENTES. `LayerContentState.applyTo`/`EditorViewModel.restoreSnapshot`
los actualizaban con DOS asignaciones separadas. Cada una, por separado,
es inmediatamente visible entre hilos — pero nada impedía que el hilo de
GL leyera `sourceUri` DESPUÉS de la primera escritura y `contentRevision`
ANTES de la segunda (o viceversa): un par que nunca existió como estado
lógico real, pero que el hilo de GL sí podía llegar a observar.

**2.2 — La decisión de commit se basaba en un booleano calculado con
antelación.** El código validaba UNA vez (`val stillCurrent = ...`) y
después ramificaba sobre ese valor ya calculado. Si el estado cambiaba
DESPUÉS de esa validación pero ANTES de la escritura real al registro
(una ventana estructuralmente pequeñísima, pero no nula), el commit
podía proceder sobre una autorización que ya no reflejaba el estado más
reciente.

## 3. Solución aplicada

### 3.1 — `Layer.ContentIdentity` (cierra 2.1)

```kotlin
data class ContentIdentity(val sourceUri: Uri, val revision: Int)

@Transient
@Volatile
var contentIdentity: ContentIdentity = ContentIdentity(sourceUri, contentRevision)
    private set

fun updateContentIdentity(newSourceUri: Uri, newRevision: Int) {
    sourceUri = newSourceUri
    contentRevision = newRevision
    contentIdentity = ContentIdentity(newSourceUri, newRevision)   // se escribe AL FINAL
}
```

`contentIdentity` se escribe EN ÚLTIMO LUGAR, después de `sourceUri` y
`contentRevision`. Por la semántica de publicación segura del Java
Memory Model (una escritura `volatile` establece happens-before con una
lectura `volatile` posterior de la misma variable en otro hilo, y
arrastra consigo todas las escrituras previas del mismo hilo), cualquier
lector que solo lea `contentIdentity` obtiene, en una única operación
atómica (una referencia de objeto no puede leerse "a medias"), un par
`(sourceUri, revision)` que sí existió como unidad lógica coherente en
algún instante real — nunca una mezcla de dos instantes distintos.

`sourceUri`/`contentRevision` SIGUEN existiendo tal cual (parámetros del
constructor primario, imprescindibles para que `.copy(sourceUri = ...)`,
usado en decenas de sitios, siga funcionando sin tocarlos). Los caminos
basados en `.copy()` no necesitan `updateContentIdentity` en absoluto:
`contentIdentity` es `@Transient` (mismo patrón que `pendingBitmap`), así
que se recalcula fresco y correcto en CADA instancia nueva —
automáticamente. Solo los DOS caminos que mutan una capa viva en el
lugar (`LayerContentState.applyTo`, `EditorViewModel.restoreSnapshot` —
auditados, son los únicos) necesitaban (y ya fueron actualizados a) usar
`updateContentIdentity`.

`GLRenderer.currentLayerIfStillRequested` ahora recibe un
`Layer.ContentIdentity` ya capturado, en vez de `sourceUri`/`contentRevision`
por separado, en los tres puntos donde valida algo: el redecode de
`onSurfaceCreated`, el redecode perezoso, y el commit atómico.

### 3.2 — "Commit gate" en dos etapas (reduce, no elimina, 2.2)

```kotlin
fun stillAuthorizedToCommit(): Boolean =
    currentLayerIfStillRequested(layer.id, capturedIdentity, expectedInstance = layer) != null &&
        contextGeneration.value == capturedContextGeneration

if (!stillAuthorizedToCommit()) { rollback(); return }   // (1) barata, apenas termina el upload

// ... nada en el medio ...

if (!stillAuthorizedToCommit()) { rollback(); return }   // (2) la ÚLTIMA operación antes de escribir

layerTextures[layer.id] = LayerTextureRecord(...)        // commit
```

La segunda llamada a `stillAuthorizedToCommit()` es LITERALMENTE la
sentencia inmediatamente anterior a la escritura del registro — cero
sentencias intermedias (ni logging, ni ninguna otra operación).

## 4. Qué garantiza esto realmente — y qué NO (sección 36/38 del encargo)

**Se declara explícitamente, sin exagerar:**

- El defecto 2.1 (lectura entrecortada de dos campos independientes)
  queda **cerrado por construcción**: es matemáticamente imposible, con
  `contentIdentity` como única fuente de verdad leída atómicamente,
  observar un par `(sourceUri, revision)` que no haya existido como tal
  en algún instante real del hilo escritor.
- El defecto 2.2 se **reduce al mínimo estructural alcanzable sin
  sincronización explícita**: la ventana entre "leer `contentIdentity`"
  y "escribir `layerTextures[id]`" pasa de "varias líneas de código, con
  trabajo de por medio" a "cero sentencias intermedias, una comparación
  y una asignación consecutivas". Esto **no es una prueba formal de
  ausencia de toda condición de carrera**: en un modelo de memoria de
  JVM sin un lock real, sigue existiendo, en términos absolutos, una
  ventana de tiempo distinta de cero (el tiempo que tarda el hilo de GL
  en ejecutar esas pocas instrucciones) durante la cual, TEÓRICAMENTE,
  el hilo principal podría escribir. Cerrar esa ventana a CERO
  matemático exigiría sincronización real entre el hilo de GL y CADA
  sitio de mutación de `EditorViewModel` (`_uiState.value = ...`,
  `layer.sourceUri = ...`, etc.) — el encargo mismo prohíbe
  explícitamente introducir un lock global o bloquear el hilo de UI para
  lograrlo, y hacerlo de forma acotada (solo un lock por capa) no
  protegería las escrituras vía `_uiState.value = ...copy()`, que no
  están naturalmente asociadas al lock de una sola capa (una
  actualización de `_uiState` puede tocar varias capas a la vez, p. ej.
  un undo que restaura varios cambios). Por eso NO se introdujo un lock:
  no habría cerrado el problema por completo de todas formas, a costa de
  complejidad y de las restricciones explícitas del encargo.
- **No se afirma "commit 100% libre de condiciones de carrera".** Se
  afirma: "el defecto demostrable de lectura entrecortada está cerrado
  por construcción; la ventana residual entre validación y escritura se
  redujo al mínimo estructural posible sin lock, y el mecanismo real
  (no solo el test) hace exactamente esa doble validación."

## 5. Archivos modificados

- `app/src/main/java/com/yeivikas/olyzecs/engine/scene/Layer.kt` —
  `ContentIdentity`, `contentIdentity`, `updateContentIdentity()`.
- `app/src/main/java/com/yeivikas/olyzecs/viewmodel/EditorViewModel.kt` —
  `LayerContentState.applyTo`, `restoreSnapshot` (usan
  `updateContentIdentity` en vez de dos asignaciones separadas).
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GLRenderer.kt` —
  `currentLayerIfStillRequested` (firma con `Layer.ContentIdentity`),
  sus tres call sites, `uploadTextureIfNeeded` (commit gate en dos
  etapas), `performLazyRedecodeIfNeeded`.
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/LayerTextureRegistryScenarioTest.kt`
  — `processLayer` reproduce la compuerta en dos etapas; TEST 25 nuevo.
- `app/src/test/java/com/yeivikas/olyzecs/engine/scene/LayerGpuOwnershipStructureTest.kt`
  — dos tests estructurales nuevos para `contentIdentity`/`updateContentIdentity`.

## 6. Tests agregados/modificados

- **TEST 25** (nuevo, el central de esta fase): validación inicial
  positiva → mutación → segunda validación → commit rechazado. Modela,
  con un hook de test explícito (`afterFirstValidationBeforeCommit`),
  exactamente el escenario "validation passed → mutation → commit
  attempted" que pedía el encargo — algo que la producción real no tiene
  forma de reproducir de manera determinística (no hay un punto de
  extensión natural entre dos sentencias adyacentes sin instrumentación),
  pero cuya LÓGICA DE DECISIÓN sí se verifica: el fake reproduce, línea
  por línea, la misma función `stillAuthorizedToCommit()` que el código
  real, llamada dos veces en los mismos puntos.
- Todos los tests de R1 (TEST A–I, 16, 21–29, H) se conservaron sin
  eliminar ninguno; se re-verificaron uno por uno contra el `processLayer`
  reestructurado (la segunda validación, con el hook por defecto en
  no-op, da el mismo resultado que la primera cuando nada cambia en el
  medio — ningún test existente cambia de comportamiento).
- Dos tests estructurales nuevos confirman que `contentIdentity` existe,
  es `@Volatile`, y que su setter es privado — que la única vía de
  actualización es `updateContentIdentity()`.

## 7. Limitaciones de los tests JVM/fake frente a GL real (sección 36)

Igual que en R1: estos son tests de PROTOCOLO (lógica de decisión de
commit), no tests de integración con un contexto GL/EGL real ni pruebas
de una condición de carrera real del hilo de GL de Android. TEST 25 en
particular usa un hook de test que no tiene equivalente exacto en
producción (no hay forma de "inyectar" una pausa entre dos sentencias
Kotlin adyacentes sin instrumentación) — lo que se verifica es que la
FUNCIÓN DE DECISIÓN (`stillAuthorizedToCommit`), llamada dos veces en los
mismos puntos que en el código real, rechaza correctamente un estado que
cambió entre ambas llamadas. No se afirma que este test reproduzca una
carrera real del driver OpenGL ni del hilo de GL de Android.

## 8. Respuestas a la auditoría específica del commit (sección 42 del encargo)

1. **¿Qué autoriza un commit?** `stillAuthorizedToCommit()`: capa viva
   con la misma identidad referencial, el mismo `ContentIdentity`
   (sourceUri+revision como unidad), y la misma generación de contexto.
2. **¿Dónde se captura esa identidad?** Al principio de
   `uploadTextureIfNeeded`, en una única lectura de `layer.contentIdentity`
   + `contextGeneration.value`, antes de tocar la GPU.
3. **¿Dónde se valida?** Dos veces: apenas termina el upload, y
   literalmente antes de escribir el registro (sección 3.2).
4. **¿Qué impide una mutación relevante entre validación y commit?**
   Nada de forma absoluta (ver sección 4) — se redujo la ventana al
   mínimo estructural, no se eliminó con un lock.
5. **¿Qué ocurre si la mutación ocurre?** La segunda validación la
   detecta y fuerza un rollback — ver TEST 25.
6. **¿Cómo se elimina la textura nueva?** `drawer.deleteTexture(newTextureId)`
   explícito, en cualquiera de las dos ramas de rollback.
7. **¿Cómo se conserva la anterior?** Nunca se toca `previousRecord`
   hasta después de confirmar el commit de la nueva (sección 3.2 de
   Fase 3.1.3, sin cambios en R2).
8. **¿Cómo se evita metadata incorrecta?** La metadata del registro sale
   de `capturedIdentity.revision` (capturado al principio), nunca de una
   relectura de `layer.contentRevision` en el punto del commit.
9. **¿Cómo se evita Bitmap leak?** Sin cambios respecto a R1: cada
   bitmap tiene un único dueño en cada instante, disposición garantizada
   en cada rama (éxito, rollback, excepción).
10. **¿Cómo se evita que una instancia vieja con el mismo layerId vuelva
    a registrarse?** Identidad referencial (`expectedInstance === layer`,
    Fase 3.1.3-R1), sin cambios.
11. **¿Cómo se comporta undo/redo?** Igual que en R1 (sección 5 de ese
    documento) — `restoreSnapshot` muta la instancia en el lugar, ahora
    vía `updateContentIdentity`, preservando exactamente la misma
    semántica de "revisión restaurada = identidad restaurada".
12. **¿Cómo se comporta la recreación de contexto?** Sin cambios de
    diseño respecto a Fase 3.1.3 (`layerTextures.clear()` en cada
    `onSurfaceCreated`, sin `contextGeneration` como campo del registro
    — sigue siendo redundante por el mismo motivo documentado ahí).
13. **¿Qué garantiza el código productivo?** Todo lo descrito en la
    sección 4 (primer párrafo) de este documento.
14. **¿Qué garantizan únicamente los tests?** La verificación exacta de
    que, DADA una interrupción en el punto que el test fuerza
    artificialmente, la lógica de decisión responde correctamente — no
    que esa interrupción sea reproducible o siquiera observable en
    producción con la frecuencia/timing que el test simula.

## 9. Riesgos residuales

1. La ventana entre la segunda validación y la escritura real al mapa
   NO es matemáticamente cero (sección 4) — es la más angosta alcanzable
   sin sincronización, dado el árbol de restricciones del encargo (sin
   lock global, sin bloquear UI, sin refactor masivo de
   `EditorViewModel`).
2. Los riesgos residuales ya señalados en R1/Fase 3.1.3
   (`layer.widthPx`/`heightPx` mutados directamente por `GLRenderer`;
   `persistNow()` sin verificación de identidad referencial; redecode
   perezoso síncrono en el hilo de GL; ausencia de tests de integración
   con Robolectric) siguen vigentes, sin cambios en esta fase.

## 10. Estado final de Fase 3.1.3-R2

**Cerrada**, con las garantías exactas descritas en la sección 4 — ni
más, ni menos — y los riesgos residuales de la sección 9 señalados
explícitamente.

No se avanza a Fase 4.
