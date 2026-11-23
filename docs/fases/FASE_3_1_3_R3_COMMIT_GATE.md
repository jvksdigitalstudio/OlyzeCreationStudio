# FASE 3.1.3-R3 — Real Atomic GPU Commit Gate

> Cierre definitivo de la última ventana TOCTOU entre la validación final
> de identidad de una Layer y el commit de su textura en el GPU registry.
> Corrección estructural sobre R2 — no la rehace, la reemplaza en el
> único punto donde R2 era, por diseño propio y documentado, insuficiente.

## 1. Por qué R2 no era atomicidad real

R2 (ver `FASE_3_1_3_R2_COMMIT_GATE.md`, sección 4) fue honesta desde el
principio: describía su propia "compuerta en dos etapas" como una
defensa **optimista** — dos lecturas consecutivas de
`stillAuthorizedToCommit()`, la segunda como la última expresión antes de
escribir `layerTextures[layer.id] = ...`, sin ninguna sentencia
intermedia. Eso reduce la ventana de tiempo entre "validar" y "escribir"
al mínimo estructural posible (cero instrucciones de por medio), pero
sigue siendo, literalmente, **dos lecturas sin ninguna primitiva de
exclusión mutua entre ellas**. Nada del lado del lenguaje ni del runtime
le impedía al hilo principal ejecutar una mutación relevante de `Layer`
— un `replaceLayer`, un `removeLayer`, un `updateContentIdentity` — en el
instante exacto entre esa segunda lectura y la escritura real del
registro. R2 nunca afirmó "race-free" y documentó esa limitación
explícitamente (ver la sección 4 de ese archivo) — la ventana formal
seguía abierta, solo que reducida a su expresión más pequeña posible.

## 2. La ventana exacta: `validation → mutation → commit`

Escenario concreto que R2 no podía cerrar (y que R3 sí cierra — ver TEST
25 de `LayerTextureRegistryScenarioTest`, que ya cubría este caso
exacto contra el modelo de dos lecturas, y `LayerGpuCommitGateTest` para
la demostración con hilos JVM reales):

```
GL THREAD                              MAIN THREAD

validate() → true
(capa sigue siendo la instancia
 esperada, misma revisión, mismo
 contexto EGL)

                                        replaceLayer()/removeLayer()/
                                        updateContentIdentity()
                                        ← la capa YA cambió acá

layerTextures["A"] = nuevoRegistro     (mutación ya terminada)
```

Con R2, la segunda lectura de `stillAuthorizedToCommit()` reduce la
probabilidad de este escenario a un margen ínfimo (cero sentencias entre
leer y escribir), pero no la elimina formalmente: en un sistema real,
sin ninguna sincronización, el planificador de hilos del sistema
operativo puede, en principio, interrumpir el hilo de GL exactamente
entre esas dos operaciones. R3 hace que ese escenario sea, por
construcción, imposible — no "improbable".

## 3. El mecanismo elegido: `LayerGpuCommitGate`

Una única instancia de `LayerGpuCommitGate` (envoltorio explícito sobre
un `java.util.concurrent.locks.ReentrantLock` — ver el KDoc completo en
`engine/render/LayerGpuCommitGate.kt` para la justificación de esa
elección frente a `synchronized`, `Mutex` de corrutinas, o un lock
global), creada una sola vez por `EditorViewModel`
(`val layerGpuCommitGate = LayerGpuCommitGate()`) y compartida por
**referencia** con `GLRenderer` (inyectada como parámetro de
constructor obligatorio, sin valor por defecto — ver `GLPreview.kt` y
`EditorScreen.kt` para el cableado exacto).

Protocolo real, del lado del hilo de GL
(`GLRenderer.uploadTextureIfNeeded`):

```
CPU/Bitmap work (decode, clamp)
      ↓
GPU upload (drawer.uploadTexture — fuera del gate)
      ↓
rollback temprano opcional (barato, fuera del gate)
      ↓
acquire commit gate
      ↓
final validation (stillAuthorizedToCommit(), DENTRO del gate)
      ↓
commit (layerTextures[layer.id] = ..., DENTRO del gate)
      ↓
release commit gate
      ↓
efectos secundarios sin relevancia de identidad (borrar textura vieja,
widthPx/heightPx) — fuera del gate
```

Del lado del hilo principal (`EditorViewModel`), cada mutación relevante
de identidad de Layer adquiere la MISMA instancia del gate, exactamente
alrededor de la operación que podría volver obsoleta una validación en
curso — nunca alrededor de trabajo no relacionado (IO, autosave,
decode).

## 4. Qué operaciones adquieren el gate

| Función (`EditorViewModel`) | Qué protege exactamente |
|---|---|
| `replaceLayer()` | La reasignación de `_uiState.value` que sustituye la instancia de `Layer` — **toda** sustitución de instancia, incluso por un cambio de campo cosmético (candado, zIndex), porque `GLRenderer` valida identidad de instancia por referencia (`===`). Cubre automáticamente a `revertLayerEditSession`, `revertLayerToUri`, `replaceLayerImage` y `commitLayerRecolor`, que llaman a `replaceLayer` por debajo. |
| `replaceLayers()` | Igual criterio que `replaceLayer`, para varias capas de un tirón. |
| `removeLayer()` | La reasignación de `_uiState.value` que saca la capa del estado — caso explícito de la sección "REMOVE DURING UPLOAD". |
| `restoreSnapshot()` (undo/redo) | Únicamente la llamada a `layer.updateContentIdentity(...)` — la única mutación de esa función que cambia `Layer.contentIdentity` (lo que `GLRenderer` compara). El resto de los campos que `restoreSnapshot` toca (zIndex, visible, lookSettings, etc.) no forman parte de la identidad GPU. |
| `LayerContentState.applyTo()` (usada por `discardChangesAndExit`) | Mismo criterio que `restoreSnapshot`: solo la llamada a `updateContentIdentity(...)`. |
| `discardChangesAndExit()` | Además de lo anterior, la construcción de `restoredLayers` y la reasignación de `_uiState.value` que la publica — puede reintroducir una instancia de `Layer` completamente nueva (`toFreshLayer()`) para el id de una capa eliminada durante la sesión, un caso de sustitución de instancia análogo al de `replaceLayer`. |

## 5. Qué operaciones quedan fuera del gate (a propósito)

- `importAsBackground()` — solo AGREGA una capa nueva, con un id que
  ningún commit en vuelo puede estar validando todavía.
- `scheduleAutosave()`/`persistNow()` — IO puro, nunca toca
  `layerTextures` ni la identidad de ninguna `Layer` viva.
- Cualquier mutación de campos no relacionados con identidad GPU
  (`zIndex`, `visible`, `locked`, `lookSettings`, keyframes de cámara,
  color) cuando ocurre DENTRO de `restoreSnapshot`/`applyTo` (mutación en
  el lugar, sin sustituir la instancia) — no forman parte de
  `Layer.contentIdentity`.
- El propio `decode()` (`Dispatchers.IO`) — nunca corre bajo ningún gate,
  en ningún flujo.

## 6. Por qué decode/upload no están bloqueados

La sección crítica protegida por el gate, del lado del hilo de GL, es
EXACTAMENTE: releer el estado vigente de la capa (`currentLayerIfStillRequested`,
una búsqueda en una lista + comparación de un objeto inmutable) más
escribir una entrada en un `MutableMap` en memoria. Ninguna de esas dos
operaciones hace IO, decodifica nada, ni llama a ninguna función de
GLES20 — `drawer.uploadTexture(bitmap)` (que sí incluye `glTexImage2D` +
`glFinish()`, la parte realmente costosa) ya terminó ANTES de que el
gate se adquiera. Del lado del hilo principal, la sección protegida es,
según la función, una reasignación de `StateFlow.value` (una escritura
de referencia) o las tres asignaciones de `Layer.updateContentIdentity()`
— tampoco hacen IO. Un lock sostenido durante microsegundos de trabajo
puramente en memoria no introduce jank perceptible en ningún hilo — ver
la sección 25 del encargo de esta fase, y el KDoc de `LayerGpuCommitGate`
para el detalle completo.

## 7. Qué ocurre con remove

Dos órdenes válidos, ambos correctos por construcción (ver
`LayerGpuCommitGateTest` para la demostración con hilos reales, y TEST 25
de `LayerTextureRegistryScenarioTest` para el equivalente secuencial):

- **CASO A** — `removeLayer()` adquiere el gate primero: la capa deja de
  estar viva; cuando el hilo de GL adquiere el gate después, su
  validación falla (la búsqueda por id ya no la encuentra) y hace
  rollback (borra la textura recién subida, preserva la anterior si
  había).
- **CASO B** — el hilo de GL adquiere el gate primero: el commit se
  compromete con éxito; `removeLayer()` corre justo después, y la
  entrada recién comprometida queda para la poda normal de
  `onDrawFrame` (`layerTextures.keys - liveLayersById.keys`) en el
  siguiente frame.

Lo que el gate hace **imposible**: que la validación dé positivo, que
`removeLayer` mute el estado, y que el commit se escriba igual como si
la capa siguiera viva — exactamente el escenario de la sección 2.

## 8. Qué ocurre con replace

Mismo criterio que remove, con `replaceLayer`/`replaceLayers` en el rol
de la mutación: o la sustitución de instancia ya terminó antes de que el
commit adquiera el gate (la validación por identidad referencial
`===` falla, rollback), o el commit ya se comprometió (con la instancia
vieja, que sigue siendo válida hasta ese instante) antes de que la
sustitución pueda empezar.

## 9. Qué ocurre con undo/redo

`restoreSnapshot()` sigue mutando la capa VIVA en el lugar (nunca
reemplaza la instancia — undo/redo no crea objetos `Layer` nuevos). La
única mutación relevante para el gate es `updateContentIdentity(...)`
cuando el `sourceUri` restaurado difiere del actual; el resto de los
campos restaurados (zIndex, visible, color, keyframes) no compiten con
ninguna validación de `GLRenderer`. La semántica de TEST 15
(`LayerTextureRegistryScenarioTest`, undo/redo con revisiones fuera de
orden) se mantiene intacta.

## 10. Qué ocurre con context recreation

Sin cambios respecto de R1/R2: `contextGeneration` sigue siendo la
fuente de verdad para invalidar todo el registro tras un
`onSurfaceCreated()` real, y `stillAuthorizedToCommit()` sigue
comparando la generación capturada contra la vigente — DENTRO del gate,
igual que el resto de la validación. El gate no sustituye ese mecanismo,
lo reutiliza tal cual.

## 11. Rollback

Sin cambios de comportamiento observable respecto de R2: un commit no
autorizado (ya sea por la validación temprana fuera del gate, o por la
validación final dentro del gate) borra la textura recién subida
(`drawer.deleteTexture(newTextureId)`) y **nunca** toca la entrada
anterior del registro ni `widthPx`/`heightPx`. Lo único que cambia es el
mecanismo que garantiza que esa decisión de rollback/commit sea
correcta bajo concurrencia real, no solo bajo el modelo secuencial que
los tests JVM pueden ejercitar.

## 12. Previous texture preservation

Sin cambios: `previousRecord` se captura antes del upload (lectura
puramente del hilo de GL, sin necesidad de gate — nada más escribe
`layerTextures` salvo este mismo hilo), y solo se borra DESPUÉS de que
el commit dentro del gate confirme el reemplazo. Un commit no
autorizado deja `previousRecord` completamente intacto.

## 13. Bitmap ownership

Sin cambios respecto de Fase 3.1: `SingleResourceHandoff` sigue siendo
la única vía de traspaso de un `Bitmap` decodificado, con su propia
operación atómica (`takeIfCurrent`/`takeRaw`) independiente del commit
gate — son dos mecanismos de sincronización distintos, protegiendo
propiedades distintas (ownership de un recurso CPU de un solo uso, vs.
consistencia de la decisión de commit GPU). El `bitmap.recycle()` final
sigue ocurriendo en el `finally` de `uploadTextureIfNeeded`, fuera de
cualquier gate.

## 14. Tests de exclusión

`LayerGpuCommitGateTest` (nuevo) es el único archivo de esta fase que
puede demostrar exclusión mutua con **hilos JVM reales** — a diferencia
del resto de la suite de `GLRenderer` (`LayerTextureRegistryScenarioTest`
y compañía), que son fakes deliberadamente secuenciales (ver la
limitación de la sección 15). Cubre:

- Un segundo hilo no puede adquirir el gate mientras el primero lo
  mantiene (`tryLock()` devuelve `false`).
- Tras liberar, el segundo hilo adquiere sin bloquear.
- Reentrancia: el mismo hilo puede readquirir el gate sin bloquearse a
  sí mismo (necesario para `discardChangesAndExit`, que anida una
  adquisición dentro de otra — ver `applyTo`).
- Un escenario de protocolo completo con dos hilos reales: Thread A
  adquiere, valida, PAUSA dentro de la sección crítica (antes de
  escribir), Thread B intenta mutar y queda bloqueado; solo tras que
  Thread A libera, Thread B logra completar su mutación — nunca al
  revés.

## 15. Limitaciones de tests JVM

Ni `LayerGpuCommitGateTest` ni el resto de la suite pueden instanciar un
`GLRenderer` real (necesita un contexto EGL/GLES20 real, no disponible
sin Robolectric, que no está en las dependencias del proyecto) ni un
`EditorViewModel` real corriendo dentro de `viewModelScope` con un
`Dispatcher` de Android real. Lo que SÍ demuestran, con precisión:

- Que `LayerGpuCommitGate` cumple el contrato de exclusión mutua que el
  resto del sistema asume (con hilos JVM reales, no simulados).
- Que el protocolo lógico de `GLRenderer.uploadTextureIfNeeded`
  (reproducido fielmente en los fakes de `LayerTextureRegistryScenarioTest`)
  produce, secuencialmente, el resultado correcto para cada orden de
  eventos posible.

Lo que NO demuestran (y ninguna suite JVM sin Robolectric/instrumentación
podría, sin un dispositivo o emulador real): que Android efectivamente
llama a `GLSurfaceView.Renderer` desde un hilo de GL distinto del hilo
Main en producción, ni el comportamiento exacto bajo la carga real de un
dispositivo. Esa garantía depende del contrato documentado de Android
para `GLSurfaceView`, no de este proyecto.

## 16. La garantía exacta (y su límite)

> El commit GPU y las mutaciones relevantes de identidad de Layer están
> serializados mediante el mismo commit gate.

Esto NO es una afirmación de "race-free" general sobre todo el proyecto
— es específica: cualquier mutación de `Layer` que NO esté en la tabla
de la sección 4 (por ejemplo, un futuro campo nuevo que alguien agregue
a `Layer.contentIdentity` sin actualizar los call sites que lo mutan)
quedaría, otra vez, sin esta protección. La garantía es tan fuerte como
la auditoría de la sección 4 — completa hoy, pero no un invariante que
el compilador de Kotlin verifique por sí solo.
