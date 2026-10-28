# FASE 3.1.3-R3.1.1 — Test Hardening & Deterministic Concurrency Validation

> Iteración quirúrgica sobre R3.1. No toca `LayerGpuCommitGate`, no toca
> `GLRenderer`, no toca la arquitectura de ownership GPU. El único
> cambio de código de **producción** de esta fase es la eliminación de
> un `import` que quedó sin uso al editar los tests (ver sección 4) —
> todo lo demás son tests y documentación.

## 1. Qué se encontró

`LayerGpuCommitGateTest.kt` (agregado en R3.1) tenía tres usos de
`Thread.sleep(200)`, todos con el mismo propósito: darle tiempo a un
segundo hilo para llegar a bloquearse contra el gate antes de continuar
con la aserción. Funcionalmente correcto en la práctica (200ms es
tiempo de sobra en cualquier máquina real para que un hilo recién
lanzado llegue a intentar adquirir un lock), pero no determinista por
construcción: nada garantiza formalmente ese margen bajo carga extrema
de CI, y el test no fallaría de forma clara ni informativa si el
supuesto dejara de sostenerse — silenciosamente pasaría por casualidad
de scheduling en vez de por la propiedad real que dice estar probando.

## 2. Los tres sitios corregidos

### 2.1 `test de exclusion real - la mutacion no puede interponerse entre validacion y commit`

Antes:

```kotlin
val mutationCompleted = AtomicBoolean(false)
...
Thread.sleep(200)
assertFalse(mutationCompleted.get())
```

Después:

```kotlin
val mutationCompleted = CountDownLatch(1)
...
assertFalse(mutationCompleted.await(300, TimeUnit.MILLISECONDS))
```

`CountDownLatch.await(timeout)` retorna `true` en el instante exacto en
que el latch se libera (si ocurre dentro de la ventana) o `false` recién
al agotarse el bound — a diferencia de `Thread.sleep` + lectura de un
`AtomicBoolean`, si el gate tuviera un defecto real y la mutación
lograra colarse, este `await` lo detectaría de inmediato (retornaría
`true` en microsegundos) en vez de quedar enmascarado hasta que se
cumplieran los 200ms fijos. El timeout (300ms) es puramente un límite
de seguridad para no colgar el test — nunca el mecanismo de
sincronización en sí, que sigue siendo el propio `CountDownLatch`
liberándose desde dentro de `gate.withGate { ... }`.

### 2.2 `runRemoveRace` (usado por los tests CASO A / CASO B)

Este sitio no protegía ninguna aserción — se eliminó directamente, sin
reemplazo. Por construcción del test, `t1` YA tiene el gate adquirido
(confirmado por `firstReady.await(...)`, que solo se libera **desde
dentro** de `gate.withGate { ... }`) desde antes de que `t2` siquiera
arranque. `t2` no puede completar su acción hasta que `t1` libere el
gate vía `letSecondGo`, sin importar en qué instante el scheduler del
sistema operativo decida ejecutar `t2` — el orden ya está determinado
por la posesión del lock, no por el reloj. El `Thread.sleep(200)` ahí
no aportaba ninguna garantía adicional; era tiempo de espera vestigial.

### 2.3 `persistNow - la normalizacion de sourceUri no puede interponerse dentro de un commit GPU en curso`

Mismo patrón exacto que 2.1, aplicado al escenario de `persistNow`:
`AtomicBoolean` → `CountDownLatch`, `Thread.sleep(200)` → `await(300,
TimeUnit.MILLISECONDS)`.

## 3. Qué NO se tocó (a propósito)

- **`assertFalse(secondCompleted.await(300, TimeUnit.MILLISECONDS))`**
  en `tras liberar el gate un segundo hilo puede completar su seccion
  critica` — este test YA usaba el patrón correcto desde que se escribió
  en R3 (espera acotada sobre el latch, no `Thread.sleep`). Se mantuvo
  como referencia/plantilla para las correcciones de esta fase.
- **`delay(...)` en `EditorViewModel.kt`/`EditorScreen.kt`/`TimelineView.kt`/`ErrorLogScreen.kt`**
  — auditados y confirmados como comportamiento funcional legítimo
  (debounce de autosave, cadencia de reproducción del timeline, tiempo
  de vida de un snackbar de error), no sincronización de tests. Ningún
  archivo de test del proyecto usa `delay(...)`.
- **`LayerTextureRegistryScenarioTest.kt`** — íntegramente secuencial
  (sin hilos, sin `await`, sin timing de ningún tipo), tal como
  documenta su propio KDoc. No había nada que endurecer ahí.
- **`LayerGpuCommitGate.kt`, `GLRenderer.kt`, `EditorViewModel.kt`** —
  sin cambios. Esta fase es de tests, no de arquitectura productiva.

## 4. Cambio de producción incidental

Al convertir `persistNowMutationCompleted`/`mutationCompleted` de
`AtomicBoolean` a `CountDownLatch`, el import
`java.util.concurrent.atomic.AtomicBoolean` quedó sin ningún uso real en
`LayerGpuCommitGateTest.kt` (ya no queda ninguna variable de ese tipo,
solo menciones en comentarios) y se eliminó. `AtomicInteger`/
`AtomicReference` siguen usándose y se mantienen.

## 5. Auditoría global repetida (Objetivo 21 del informe de esta fase)

```
grep -rn "Thread\.sleep"                 app/src/test/java app/src/main/java
grep -rn "delay\("                       app/src/test/java app/src/main/java
grep -rn "\.sourceUri\s*=\s*[^=]"        app/src/main/java app/src/test/java
grep -rn "\.contentRevision\s*=\s*[^=]"  app/src/main/java app/src/test/java
grep -rn "updateContentIdentity\("       app/src/main/java
grep -rn "LayerGpuCommitGate\(\)"        app/src/main/java app/src/test/java
```

Resultados:

- **`Thread.sleep`**: cero ocurrencias reales de código en todo el
  proyecto (solo quedan menciones dentro de comentarios que documentan
  la corrección — ver sección 2).
- **`delay(`**: solo en producción, todos funcionales y no relacionados
  con sincronización de tests (ver sección 3). Ningún test los usa.
- **`sourceUri =` / `contentRevision =`**: mismo resultado que la
  auditoría de R3.1 — la única asignación directa de `sourceUri` fuera
  de `Layer.kt` sigue siendo `liveClip.sourceUri` (`AudioClip`, fuera de
  alcance, ver `FASE_3_1_3_R3_1_MUTATION_PATH_CLOSURE.md` sección 7); el
  resto son sobre `FakeLayerInstance`/`FakePersistableLayer` (dobles de
  test, no la clase real) o `.copy(sourceUri = ...)` (constructor, no
  mutación). Ningún bypass adicional.
- **`updateContentIdentity(`**: mismos tres call sites de producción que
  R3.1 (`applyTo`, `restoreSnapshot`, `persistNow`), todos dentro de
  `layerGpuCommitGate.withGate { ... }`. Sin cambios.
- **`LayerGpuCommitGate()`**: una única instancia de producción
  (`EditorViewModel.layerGpuCommitGate`); el resto son instancias
  aisladas dentro de cada test — comportamiento esperado y correcto, no
  un segundo gate accidental conectado a `GLRenderer`.

No se encontró ningún bypass adicional. No fue necesario ningún cambio
de arquitectura productiva.

## 6. Qué prueban realmente estos tests (alcance exacto, sin exagerar)

Todos los tests de `LayerGpuCommitGateTest.kt` son **tests de
protocolo/contrato del gate**, ejecutados con hilos JVM reales
(`java.lang.Thread`) contra la instancia REAL de `LayerGpuCommitGate` —
NO son tests de integración de Android, NO instancian `GLRenderer` ni
`EditorViewModel` reales (ninguno de los dos es instanciable en JUnit
puro sin Robolectric/instrumentación — ver la sección "Limitaciones de
tests JVM" de `FASE_3_1_3_R3_COMMIT_GATE.md`, sin cambios en esta fase),
y NO validan comportamiento de un driver GPU real ni de un contexto EGL
real. Lo que demuestran, con precisión:

- Que `LayerGpuCommitGate` cumple el contrato de exclusión mutua que el
  resto del sistema asume, con hilos reales y sincronización
  determinista (sin `Thread.sleep` como mecanismo, desde esta fase).
- Que la FORMA exacta del código real de `persistNow`
  (`if (current == original) { updateContentIdentity(...) }` dentro de
  `withGate`) respeta ese mismo contrato cuando se ejecuta concurrente a
  un commit GPU simulado.

No se afirma "concurrencia GPU real validada" en ningún lugar de esta
documentación ni de los tests — sería una sobre-afirmación que esta
suite, por diseño, no puede sostener.

> **ACTUALIZACIÓN — FASE 3.1.3-R3.1.2** (ver
> `FASE_3_1_3_R3_1_2_DETERMINISTIC_GATE_CONTENTION_TEST.md`): la espera
> acotada sobre `CountDownLatch.await(300ms)` introducida en esta fase
> (sección 2) para el test de `persistNow` — y, se detectó después, para
> `tras liberar el gate un segundo hilo puede completar su seccion
> critica` — todavía no confirmaba explícitamente que el hilo contendiente
> hubiera llegado al punto de adquisición del gate antes de la
> comprobación negativa. R3.1.2 agregó una señal dedicada
> (`persistAttemptingGate`/`secondAttemptingGate`) para cerrar esa
> ambigüedad. Ver ese documento para el detalle completo.

## 7. Estado final

**Fase 3.1.3-R3.1.1 lista para validación manual.**
