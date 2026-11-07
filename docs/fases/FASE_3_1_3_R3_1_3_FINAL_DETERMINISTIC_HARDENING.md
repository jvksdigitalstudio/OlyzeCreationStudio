# FASE 3.1.3-R3.1.3 — Final Deterministic Concurrency Test Hardening

> Iteración quirúrgica sobre R3.1.2. Ningún archivo de producción fue
> modificado. Todo el cambio es de tests y documentación.

## 1. El problema de determinismo encontrado

R3.1.2 ya había eliminado `Thread.sleep()` y había añadido señales
explícitas (`persistAttemptingGate`, `secondAttemptingGate`) para
demostrar que el hilo bloqueado efectivamente había llegado al punto de
adquisición del gate antes de la comprobación negativa. Eso resolvió el
problema de "¿el hilo ya arrancó?", pero dejó sin resolver una segunda
debilidad, más sutil, presente en **las tres** comprobaciones negativas
del archivo:

```kotlin
assertFalse(
    persistNowMutationCompleted.await(300, TimeUnit.MILLISECONDS)
)
```

Aunque ya no dependía de si el hilo había arrancado, esta línea seguía
siendo una **prueba por ausencia dentro de una ventana de tiempo**: el
test afirma "la mutación no ocurrió" únicamente porque no ocurrió
*dentro de los 300ms que el test decidió esperar*. Eso es correcto en
la práctica — 300ms es una eternidad comparado con la nanosegundos que
tarda un `ReentrantLock.lock()` en bloquear un hilo — pero no es una
prueba *estructural*. Formalmente, sigue habiendo dos explicaciones
posibles para que el `await` devuelva `false`:

1. El gate está bloqueando correctamente a la mutación (lo que el test
   quiere demostrar).
2. La mutación, por cualquier motivo de scheduling del entorno de
   ejecución (una JVM de CI muy cargada, un hipervisor con jitter, un
   `Thread` que tardó en ser planificado), simplemente no llegó a
   completarse dentro de esa ventana — sin que eso implique que el gate
   esté haciendo nada.

En la práctica (2) es extremadamente improbable con una ventana de
300ms, pero "extremadamente improbable" no es lo mismo que "imposible
por construcción" — y **un test de concurrencia correcto debe demostrar
lo segundo**, no apoyarse en lo primero. Esta es, literalmente, "la
última dependencia temporal innecesaria" a la que se refiere el encargo
de esta fase.

Se encontraron tres instancias exactas de este patrón en
`LayerGpuCommitGateTest.kt`:

- `tras liberar el gate un segundo hilo puede completar su seccion critica`
- `test de exclusion real - la mutacion no puede interponerse entre validacion y commit`
- `persistNow - la normalizacion de sourceUri no puede interponerse dentro de un commit GPU en curso`
  (el test explícitamente señalado en el encargo de esta fase)

## 2. La solución utilizada

`LayerGpuCommitGate` ya expone, a propósito y solo para tests
(`acquire`/`release`/`tryAcquire`, ver su KDoc), una adquisición **no
bloqueante**: `tryAcquire()`, respaldada por `ReentrantLock.tryLock()`.
Esta llamada no espera absolutamente nada — retorna en el acto `true`
si el lock estaba libre (y en ese caso lo adquiere) o `false` si algún
otro hilo lo tenía.

En cada uno de los tres sitios, en el instante exacto en que
anteriormente se abría la ventana de 300ms, el hilo que mantiene el
gate **todavía no lo ha liberado** (la señal de liberación —
`releaseFirst`, `allowGlToCommit`, `allowGlToFinish` según el test — se
dispara varias líneas después). Eso significa que, en ese instante,
`gate.tryAcquire()` llamado desde el hilo de test **debe** devolver
`false` — no "probablemente", sino por la propia semántica de exclusión
mutua de `ReentrantLock`, verificable sin esperar nada:

```kotlin
val gateAppearedFreeWhileGlHeldIt = gate.tryAcquire()
if (gateAppearedFreeWhileGlHeldIt) {
    gate.release() // solo se ejecutaría si hubiera un bug real
}
assertFalse(
    "el gate no debe estar disponible mientras el commit GPU lo mantiene retenido",
    gateAppearedFreeWhileGlHeldIt
)
assertEquals(
    "la actualización de sourceUri de persistNow no debe poder completarse mientras el commit GPU sigue dentro del gate",
    1L,
    persistNowMutationCompleted.count
)
```

Y la segunda `assertEquals` no es una observación independiente: es una
**consecuencia lógica** de la primera. Si el gate está efectivamente
tomado por el hilo de GL en este instante (demostrado por
`tryAcquire()`), entonces el hilo de `persistNow` — que ya confirmó
(vía `persistAttemptingGate`) que está, como mínimo, a punto de invocar
`lock()` sobre ese mismo gate — **no puede** haber ejecutado ya
`gate.withGate { ...; persistNowMutationCompleted.countDown() }`, sin
importar cuánto tiempo real haya transcurrido. Por eso el latch se lee
con `.count` (lectura inmediata del estado actual), no con `.await(...)`
(que introduciría de nuevo una espera).

## 3. Por qué esta prueba ya no depende del scheduling accidental

La diferencia estructural con R3.1.1/R3.1.2 es la siguiente:

- **Antes**: "esperé N milisegundos y no pasó nada" → depende de que N
  sea mayor que el tiempo que el sistema operativo tarda en planificar
  y ejecutar el hilo bloqueado — una suposición sobre el entorno de
  ejecución, aunque razonable.
- **Ahora**: "consulté el estado del lock en este instante preciso y
  está tomado" → no depende de ningún tiempo. `tryAcquire()` no
  bloquea, no espera, no compite con ningún scheduler: lee el estado
  del `ReentrantLock` en el momento exacto de la llamada. La única
  forma de que esta comprobación diera un falso positivo (asumir
  bloqueo cuando no lo hay) sería que el gate estuviera roto — que es
  precisamente lo que el test existe para detectar.

La causalidad completa del test de `persistNow` queda así, cada paso
demostrado por una señal o una lectura inmediata, nunca por un reloj:

```
GL adquiere el gate
    ↓ (glEnteredGate)
persistNow arranca e informa que va a intentar adquirir
    ↓ (persistAttemptingGate)
tryAcquire() del hilo de test confirma: el gate sigue tomado (por GL)
    ↓ (lectura inmediata, no bloqueante)
por exclusión mutua: persistNow no pudo haber completado su mutación
    ↓ (persistNowMutationCompleted.count == 1, lectura inmediata)
GL libera el gate
    ↓ (allowGlToFinish.countDown())
persistNow completa su mutación
    ↓ (persistNowMutationCompleted.await(...) == true)
```

## 4. Confirmación — sin sleeps ni delays artificiales

- **`Thread.sleep(`**: cero ocurrencias de código real (quedan
  únicamente menciones en comentarios que documentan qué se quitó en
  fases anteriores — sin cambios respecto de R3.1.2).
- **`delay(` en tests de concurrencia**: cero.
- **`Thread.yield(`**: cero en todo el proyecto.
- **`await(<n>, TimeUnit.MILLISECONDS)` con `n` finito usado como
  evidencia de bloqueo**: cero — las tres instancias fueron
  reemplazadas por `tryAcquire()` (no bloqueante) + lectura inmediata
  de `CountDownLatch.count`. Los `await(awaitTimeoutSeconds, ...)`
  restantes en el archivo son esperas **positivas** de una señal que sí
  se espera que llegue (con timeout de seguridad de 5s para que un bug
  real cuelgue o falle el test, nunca un falso verde) — no son el
  mecanismo que demuestra exclusión, solo sincronización de setup.
- **Busy waiting / polling / random timing**: cero, sin cambios
  respecto de la auditoría de R3.1.2.

## 5. Segunda auditoría global (sección 15 del encargo)

Repetida sobre el estado final del árbol:

- **`Thread.sleep(` / `delay(` / `Thread.yield(`**: mismo resultado que
  arriba — cero en código real.
- **Busy waiting**: el único `while (!stop.get())` del proyecto sigue
  siendo el de `CameraTrackConcurrencyTest.kt` — **fuera de alcance**,
  test de estrés preexistente de otra fase (mutación concurrente de
  `CameraTrack`, sin relación con `LayerGpuCommitGate`/GPU/`persistNow`),
  con un patrón distinto y legítimo (loop acotado por `repeat(20_000)`
  + flag de parada). No se tocó.
- **`sourceUri =` / `contentRevision =` fuera de `Layer.kt`**: mismo
  inventario que R3.1.2 — constructores (`.copy(...)`, llamadas a
  constructor posicional/nombrado), la única asignación directa de
  producción real es `liveClip.sourceUri` sobre `AudioClip` dentro de
  `persistNow` (fuera de alcance de esta fase, documentado desde R3.1),
  y el resto son `FakePersistableLayer`/`FakeLayerInstance` (dobles de
  test) o comentarios. Ninguna mutación directa nueva sobre `Layer`
  fuera de `updateContentIdentity()`.
- **`updateContentIdentity(`**: los mismos 3 call sites de producción
  (`applyTo`, `restoreSnapshot`, `persistNow`), todos dentro de
  `layerGpuCommitGate.withGate { ... }`. Sin cambios.
- **`LayerGpuCommitGate()`**: una única instancia de producción
  (`EditorViewModel.layerGpuCommitGate`); el resto son instancias
  aisladas por test — correcto.

No se encontró ningún bypass productivo nuevo ni preexistente sin
documentar.

## 6. Producción

No fue necesario modificar `LayerGpuCommitGate`, `GLRenderer`, `Layer`,
`EditorViewModel`, `ProjectStorage` ni `SingleResourceHandoff`. El
único cambio de esta iteración usa una capacidad que
`LayerGpuCommitGate` ya exponía explícitamente para tests desde R3
(`tryAcquire()`) — no se agregó, cambió ni removió ningún método de la
clase.

## 7. Cobertura confirmada intacta

Revisados, sin cambios: los tests de stale `sourceUri`/`contentRevision`,
layer removal, context generation, out-of-order revision, undo/redo/
restore (`LayerTextureRegistryScenarioTest.kt`), la reentrancia del
gate, la liberación del gate ante excepción, el test REMOVE (casos A y
B), y los tests B/C de `persistNow` (edición más nueva / same-content
normalization). Ninguno fue tocado ni necesitaba cambios.

## 8. Limitaciones reales de estos tests (sin cambios respecto de fases anteriores)

- Estos son **protocol tests**: prueban exclusión mutua, identidad y
  ordering lógico usando `LayerGpuCommitGate` real y dobles simples
  (`FakePersistableLayer`, `FakeProject`, `FakeRegistry`) — NO prueban
  comportamiento de un driver GPU real, de EGL/GLES ni de
  `GLSurfaceView.Renderer` real.
- No se afirma, en ningún punto, que estos tests hayan sido ejecutados
  por esta iteración, ni que validen GPU o driver real.
- El `tryAcquire()` que sostiene la nueva prueba de exclusión solo
  demuestra que el `ReentrantLock` subyacente está tomado en el
  instante de la llamada — no reemplaza, ni pretende reemplazar, una
  prueba de instrumentación con hardware real.

## 9. Estado final

**Fase 3.1.3-R3.1.3 lista para auditoría final. No se avanzó a Fase 4.**
