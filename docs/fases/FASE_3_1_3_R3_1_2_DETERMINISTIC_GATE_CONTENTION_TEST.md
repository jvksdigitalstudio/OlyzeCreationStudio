# FASE 3.1.3-R3.1.2 — Deterministic Gate-Contention Test

> Iteración quirúrgica sobre R3.1.1. Ningún archivo de producción fue
> modificado. Todo el cambio es de tests y documentación.

## 1. La debilidad encontrada

El test `persistNow - la normalizacion de sourceUri no puede
interponerse dentro de un commit GPU en curso` (R3.1) eliminó
`Thread.sleep` en R3.1.1, reemplazándolo por una espera acotada sobre un
latch:

```kotlin
persistNowThread.start()
assertFalse(
    persistNowMutationCompleted.await(300, TimeUnit.MILLISECONDS)
)
```

Esto ya no era un sleep, pero seguía sin ser una prueba completamente
determinista de lo que decía probar: **nada confirmaba que el hilo de
`persistNow` hubiera llegado siquiera al punto de intentar adquirir el
gate** antes de esa comprobación negativa. Un `await(300ms)` que vence
sin liberarse es exactamente el mismo resultado observable tanto si (a)
el gate está bloqueando correctamente al hilo, como si (b) el hilo
todavía ni fue planificado por el sistema operativo — dos causas
completamente distintas, indistinguibles desde afuera sin una señal
adicional. La ventana de 300ms hacía esta segunda posibilidad
extremadamente improbable en la práctica, pero "improbable" no es
"determinista".

## 2. La corrección

Se agregó una señal nueva, `persistAttemptingGate: CountDownLatch(1)`,
liberada por el hilo de `persistNow` **inmediatamente antes** de
`gate.withGate { ... }` — ni después, ni dentro de la sección crítica,
ni después de la mutación:

```kotlin
val persistNowThread = Thread {
    persistAttemptingGate.countDown()
    gate.withGate {
        if (layer.sourceUri == originalUriAtSaveStart) {
            layer.sourceUri = "file:///local/normalized.png"
        }
        persistNowMutationCompleted.countDown()
    }
}
...
persistNowThread.start()
assertTrue(persistAttemptingGate.await(awaitTimeoutSeconds, TimeUnit.SECONDS))
assertFalse(persistNowMutationCompleted.await(300, TimeUnit.MILLISECONDS))
```

Con `persistAttemptingGate.await(...)` confirmado ANTES de la
comprobación negativa, la única explicación posible para que
`persistNowMutationCompleted` no se libere dentro de la ventana de
300ms es que el gate efectivamente está bloqueando al hilo — la
posibilidad (b) de la sección 1 queda eliminada por construcción, no
por probabilidad.

Se aplicó exactamente el mismo criterio a un segundo test que tenía la
misma debilidad estructural, no señalado explícitamente en el encargo
de esta fase pero encontrado por la auditoría (`REVISAR EL RESTO DE
LayerGpuCommitGateTest`): `tras liberar el gate un segundo hilo puede
completar su seccion critica` — se agregó `secondAttemptingGate` con el
mismo patrón.

## 3. Las cuatro propiedades demostradas

El test de `persistNow` ahora demuestra, en este orden, con una señal
explícita para cada paso:

1. **PROPERTY 1** — GL mantiene el gate durante su sección crítica
   (`glEnteredGate`, sin cambios respecto de R3.1.1).
2. **PROPERTY 2** — `persistNow` alcanza el punto de adquisición del
   gate (`persistAttemptingGate`, nuevo en esta fase) pero no puede
   entrar mientras GL lo posee (`persistNowMutationCompleted.await(300ms)`
   == `false`, ahora una prueba real de la propiedad, no una coincidencia
   de scheduling).
3. **PROPERTY 3** — al liberar GL el gate (`allowGlToFinish.countDown()`),
   `persistNow` finalmente entra (`persistNowMutationCompleted.await(...)`
   == `true`).
4. **PROPERTY 4** — la mutación de `sourceUri` solo se ejecuta después de
   obtener el gate: el commit de GL, que capturó su propia identidad
   antes de entrar al gate, se compromete con el URI VIEJO (todavía sin
   normalizar) — nunca una mezcla a mitad de escritura.

## 4. Por qué esto sigue sin ser `Thread.sleep`

`await(300, TimeUnit.MILLISECONDS)` es un límite de seguridad, no el
mecanismo de sincronización. La causalidad real (qué pasó antes de qué)
viene enteramente de los `CountDownLatch` liberándose en orden
determinado por el código, nunca del paso del tiempo — si el gate
tuviera un defecto y `persistNowMutationCompleted` se liberara en
microsegundos, el `await` lo detectaría de inmediato (retornaría `true`
casi al instante), no recién a los 300ms.

## 5. Segundo y tercer test revisados (sin cambios de semántica)

- **`persistNow - no sobrescribe una edicion mas nueva ocurrida durante
  el guardado`** — revisado; sigue demostrando `sourceUri = B`,
  `contentRevision = 2` (nunca `C`). No usa `Thread.sleep`; no tenía la
  debilidad de esta fase (no hay una comprobación negativa dependiente
  de timing — la mutación "más nueva" y la de `persistNow` corren
  secuencialmente, cada una dentro de su propio `withGate`, sin
  competencia real entre hilos). Sin cambios.
- **`persistNow - normalizacion de mismo contenido no incrementa
  contentRevision`** — revisado; sigue demostrando `contentRevision = 42`
  antes y después. Mismo criterio que el anterior: sin hilos concurrentes,
  no aplicaba la debilidad de esta fase. Sin cambios.

## 6. Auditoría global (repetida al cerrar esta fase)

```
grep -rn "Thread\.sleep("            app/src/test/java app/src/main/java
grep -rn "delay\("                   app/src/test/java
grep -rn "Thread\.yield("            app/src/test/java app/src/main/java
grep -rn "while *\( *true *\)|while *\( *!"  app/src/test/java
grep -rn "\.sourceUri\s*=\s*[^=]"    app/src/main/java
grep -rn "\.contentRevision\s*=\s*[^=]" app/src/main/java
grep -rn "updateContentIdentity\("   app/src/main/java
grep -rn "LayerGpuCommitGate\(\)"    app/src/main/java app/src/test/java
```

- **`Thread.sleep(`**: cero ocurrencias de código real (solo quedan
  menciones en comentarios, heredadas de R3.1.1, que documentan qué se
  quitó).
- **`delay(` en tests**: cero.
- **`Thread.yield(`**: cero en todo el proyecto.
- **Busy waiting**: se encontró un `while (!stop.get())` en
  `CameraTrackConcurrencyTest.kt` — **fuera de alcance**: es un test de
  estrés preexistente de "FASE 2" (mutación concurrente de
  `CameraTrack`, nada que ver con `LayerGpuCommitGate`/GPU/`persistNow`),
  con un patrón legítimo y distinto (loop de estrés acotado por
  `repeat(20_000)` del lado del escritor + flag de parada, no una espera
  de sincronización de un único evento). No se modificó — tocarlo no
  corresponde a esta fase.
- **`sourceUri =` / `contentRevision =` fuera de `Layer.kt`**: mismo
  resultado que R3.1/R3.1.1 — la única asignación directa real es
  `liveClip.sourceUri` sobre `AudioClip` en `persistNow` (fuera de
  alcance, documentado desde R3.1). El resto son comentarios,
  `.copy(sourceUri = ...)` (constructor, no mutación) o
  `FakeLayerInstance`/`FakePersistableLayer` (dobles de test).
- **`updateContentIdentity(`**: mismos 3 call sites de producción
  (`applyTo`, `restoreSnapshot`, `persistNow`), todos dentro de
  `layerGpuCommitGate.withGate { ... }`. Sin cambios.
- **`LayerGpuCommitGate()`**: una única instancia de producción
  (`EditorViewModel.layerGpuCommitGate`); el resto, instancias aisladas
  por test — correcto, ningún gate accidental conectado a `GLRenderer`.

No se encontró ningún bypass productivo. No fue necesario modificar
`LayerGpuCommitGate`, `GLRenderer`, `Layer`, `SingleResourceHandoff`,
`ProjectStorage` ni `EditorViewModel`.

## 7. Cobertura confirmada intacta

Revisada, sin cambios: `LayerTextureRegistryScenarioTest.kt` (stale
identity/sourceUri/context-generation/out-of-order/undo-redo/layer
removal) — íntegramente secuencial, sin hilos, sin timing de ningún
tipo, tal como documentaba su propio KDoc desde antes de esta fase.

## 8. Estado final

**Fase 3.1.3-R3.1.2 lista para auditoría final.**
