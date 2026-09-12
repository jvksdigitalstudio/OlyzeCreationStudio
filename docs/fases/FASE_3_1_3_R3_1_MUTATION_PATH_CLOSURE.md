# FASE 3.1.3-R3.1 — Mutation Path Closure

> Corrección quirúrgica sobre Fase 3.1.3-R3. No introduce una nueva
> arquitectura ni rediseña `LayerGpuCommitGate` — cierra un bypass real
> encontrado en la auditoría y fortalece la encapsulación para que ese
> bypass no pueda repetirse por descuido.

## 1. El bypass real encontrado

`EditorViewModel.persistNow()`, después de que el guardado a disco
terminaba, aplicaba la normalización de un `sourceUri` de SAF a su copia
local ya resuelta con una asignación **directa**:

```kotlin
if (liveLayer.sourceUri == originalUriById[layerId]) {
    liveLayer.sourceUri = resolvedUri
}
```

Esto era, en realidad, **dos violaciones a la vez**, no solo una:

1. **Sin sincronización con `LayerGpuCommitGate`.** Esta asignación podía
   ejecutarse en cualquier instante respecto del commit de GL — nada
   impedía que corriera exactamente en medio de la sección crítica de
   `GLRenderer.uploadTextureIfNeeded`.
2. **Sin pasar por `Layer.updateContentIdentity()`.** `sourceUri` se
   actualizaba, pero `Layer.contentIdentity` — el ÚNICO campo que
   `GLRenderer` realmente lee para decidir si un recurso sigue vigente
   (ver `currentLayerIfStillRequested`, `requestedIdentity = layer.contentIdentity`)
   — se quedaba con el valor VIEJO. Esto era, en la práctica, un defecto
   más serio que la falta de sincronización: incluso en ausencia total de
   concurrencia, el redecode perezoso y la validación de identidad
   seguirían usando el `sourceUri` de SAF original, nunca la copia local
   recién resuelta — exactamente lo que la normalización buscaba evitar
   (evitar depender de un permiso persistente de SAF que puede haberse
   revocado).

## 2. La corrección

```kotlin
if (result.resolvedLayerImageUris.isNotEmpty()) {
    val originalUriById = layerSnapshots.associate { it.id to it.sourceUri }
    layerGpuCommitGate.withGate {
        val liveLayersById = _uiState.value.layers.associateBy { it.id }
        result.resolvedLayerImageUris.forEach { (layerId, resolvedUri) ->
            val liveLayer = liveLayersById[layerId] ?: return@forEach
            if (liveLayer.sourceUri == originalUriById[layerId]) {
                liveLayer.updateContentIdentity(resolvedUri, liveLayer.contentRevision)
            }
        }
    }
}
```

Tres cambios, cada uno cerrando una parte distinta del problema:

- **La comprobación Y la mutación corren dentro del mismo `withGate`.**
  Nunca `comprobar afuera → mutar adentro` (esa forma sigue siendo
  vulnerable, por la misma razón exacta que `validateOutsideGate() →
  acquireGate() → commit()` seguía siendo vulnerable en R3 — ver
  `FASE_3_1_3_R3_COMMIT_GATE.md`, sección "Qué operaciones adquieren el
  gate").
- **`_uiState.value.layers` se relee DENTRO del gate**, no antes. El IO
  real (`saveProject`) ya terminó cuando se llega a este bloque, así que
  el hilo principal estuvo libre todo ese tiempo — pudo correr un
  `removeLayer`/`replaceLayer` mientras tanto. Revalidar contra el
  estado LIVE más fresco posible, en el mismo instante en que se muta,
  es el mismo criterio que `GLRenderer.stillAuthorizedToCommit()` exige
  antes de comprometer cualquier recurso.
- **`updateContentIdentity(resolvedUri, liveLayer.contentRevision)`** en
  vez de la asignación directa — mantiene `contentIdentity` sincronizado
  en la misma operación atómica, y de paso ya no compila ninguna otra
  asignación directa (ver sección 4).

La condición `sourceUri == originalUriById[layerId]` (para no pisar una
edición más nueva del usuario que haya ocurrido mientras el guardado
anterior estaba en vuelo) se conserva exactamente igual — la garantía
funcional de "no perder una edición nueva" no cambió, solo el mecanismo
que la hace segura.

## 3. `contentRevision` no cambia por esta corrección

`persistNow` pasa `liveLayer.contentRevision` **tal cual**, sin
sumarle nada. La normalización SAF → copia local representa el MISMO
contenido — nunca contenido nuevo — así que no corresponde ningún bump
de revisión, a diferencia de `replaceLayerImage`/`commitLayerRecolor`
(esos sí bumpean la revisión porque ahí el contenido cambia de verdad).

Como referencia adicional: `contentRevision` ni siquiera forma parte de
`LayerSaveSnapshot`/`LayerData` (ver `ProjectStorage.kt`) — es
puramente un concepto de sesión en memoria, nunca se persiste a
`project.json`. Esta corrección no tiene ningún efecto sobre proyectos
ya guardados en disco.

## 4. Encapsulación reforzada: `sourceUri`/`contentRevision` con `private set`

Ambos campos de `Layer` pasan de `var` público a `var ... private set`:

```kotlin
@Volatile var sourceUri: Uri
    private set

@Volatile var contentRevision: Int = 0
    private set
```

**Qué seguía funcionando exactamente igual, sin tocar ni un call site:**

- `Layer(sourceUri = ..., ...)` — el constructor primario sigue público;
  la visibilidad del *setter* de una propiedad es independiente de la
  visibilidad del *parámetro del constructor* en Kotlin.
- `layer.copy(sourceUri = ..., contentRevision = ...)` — el `copy()`
  generado por el compilador llama al constructor primario, nunca al
  setter, así que decenas de call sites en `EditorViewModel`
  (`replaceLayerImage`, `commitLayerRecolor`, `revertLayerEditSession`,
  `revertLayerToUri`, etc.) siguen compilando sin ningún cambio.
- La lectura (`layer.sourceUri`, `layer.contentRevision`) — el *getter*
  sigue público.

**Qué deja de compilar (a propósito):** cualquier asignación directa
`layer.sourceUri = x` / `layer.contentRevision = x` desde fuera de la
clase `Layer` — exactamente la forma del bypass real que esta fase
cierra. El único punto que retiene acceso de escritura es
`Layer.updateContentIdentity()`, ya que es un método miembro de la
propia clase.

Auditoría de regresión (ver sección 6): ningún archivo de producción ni
de test, fuera de `Layer.kt`, hacía esta asignación directa salvo el
propio `persistNow()` ya corregido — no hizo falta tocar ningún otro
call site.

## 5. Rutas de mutación auditadas (clasificación completa)

| Ruta | Categoría | ¿Bajo gate? | Motivo |
|---|---|---|---|
| `LayerRepository.importAsLayers` (`Layer(...)`) | A — Layer nuevo | No | Construcción; no forma parte del estado LIVE hasta que el ViewModel lo asigna a `_uiState.value`. |
| `ProjectStorage.loadProject` (`Layer(...)`) | B — deserialización | No | Corre en `Dispatchers.IO`, construye instancias que todavía no están integradas al estado LIVE. |
| `replaceLayer()` / `replaceLayers()` | C — mutación LIVE (sustitución de instancia) | **Sí** (ya cerrado en R3) | Sustituye la instancia referenciada por `_uiState.value.layers`. |
| `removeLayer()` | C — mutación LIVE | **Sí** (ya cerrado en R3) | Saca la capa del estado LIVE. |
| `restoreSnapshot()` (undo/redo) | E — mutación LIVE en el lugar | **Sí** (ya cerrado en R3, solo la llamada a `updateContentIdentity`) | Undo/redo muta la misma instancia; solo el cambio de `sourceUri`/`contentRevision` es identity-relevant. |
| `LayerContentState.applyTo()` (`discardChangesAndExit`) | D — snapshot/restauración | **Sí** (ya cerrado en R3, solo la llamada a `updateContentIdentity`) | Igual criterio que `restoreSnapshot`. |
| `discardChangesAndExit()` (reconciliación `restoredLayers`) | D — snapshot/restauración | **Sí** (ya cerrado en R3) | Puede reintroducir una instancia nueva (`toFreshLayer()`) para un id eliminado durante la sesión. |
| **`persistNow()` (normalización de `sourceUri`)** | **F — persistencia** | **Sí (corregido en ESTA fase)** | Bypass real cerrado — ver secciones 1-2. |
| `persistNow()` (`liveClip.sourceUri = resolvedUri`, `AudioClip`) | F — persistencia | No (deliberado, ver sección 7) | `AudioClip` no participa de la identidad GPU. |
| `toContentState()` / `toFreshLayer()` / `toSaveSnapshot()` (lecturas de `sourceUri`/`contentRevision`) | H — indirecta | No (son lecturas, no mutaciones) | Construyen DTOs inmutables a partir de una lectura puntual; no escriben nada en el `Layer` original. |
| `LayerTextureRegistryScenarioTest` (`FakeLayerInstance.sourceUri = ...`) | G — test | No aplica | Es un doble de prueba (`FakeLayerInstance`, campos `String`/`Int` simples) definido dentro del propio archivo de test — no es la clase `Layer` real; no se ve afectado por el `private set`. |

## 6. Segunda auditoría global (obligatoria antes de cerrar la fase)

Búsqueda global repetida al final del trabajo:

```
grep -rn "\.sourceUri\s*=\s*[^=]" app/src/main/java app/src/test/java
grep -rn "\.contentRevision\s*=\s*[^=]" app/src/main/java app/src/test/java
grep -rn "LayerGpuCommitGate()" app/src/main/java app/src/test/java
```

Resultado: **ninguna otra mutación LIVE relevante de identidad GPU fuera
del gate**, más allá de la ya corregida en `persistNow()`. La única
asignación directa restante de `sourceUri` fuera de `Layer.kt` es
`liveClip.sourceUri = resolvedUri` sobre `AudioClip` (ver sección 7,
deliberadamente fuera de alcance) y las asignaciones sobre
`FakeLayerInstance` en `LayerTextureRegistryScenarioTest` (un tipo de
test, no la clase real).

`LayerGpuCommitGate()` (el constructor) se instancia en exactamente dos
lugares de todo el proyecto: `EditorViewModel` (`val layerGpuCommitGate = LayerGpuCommitGate()`,
una única instancia por sesión de edición, compartida por referencia con
`GLRenderer` vía `GLPreview`/`EditorScreen`) y dentro de cada test que
necesita su propia instancia aislada (`LayerGpuCommitGateTest`,
comportamiento esperado y correcto para tests independientes entre sí).
No existe una segunda instancia accidental en código de producción.

## 7. Qué queda deliberadamente fuera del gate, y por qué

- **`AudioClip.sourceUri`** (`persistNow`, `liveClip.sourceUri = resolvedUri`).
  No existe ningún lector concurrente de este campo — a diferencia de
  `Layer`, ningún hilo de GL ni ningún registro equivalente a
  `layerTextures` consulta `AudioClip` para decidir si un recurso GPU
  sigue vigente. La reproducción de audio no participa del contrato que
  `LayerGpuCommitGate` protege. Envolverlo en el gate no aportaría
  ninguna garantía real — sería sincronización sin ningún lector
  concurrente que la justifique.
- **`decode()` / `ImageDecoding.decodeSampledFromUri` / IO de disco /
  `saveProject`** — sin cambios respecto de R3: siguen completamente
  fuera del gate, tal como exige la sección "NO BLOQUEAR EL TRABAJO
  PESADO" del informe original de R3 y la restricción explícita de esta
  fase (Objetivo 12: el gate debe seguir siendo corto).
- **Categorías A y B** (construcción de `Layer`/`AudioClip` nuevos,
  deserialización desde disco) — no forman parte del estado LIVE
  compartido en el momento de la construcción; no hay ninguna validación
  de GPU en curso que puedan volver obsoleta.

## 8. Garantía real (sin ampliarla)

> La mutación de identidad de una capa LIVE relevante para GPU
> (`sourceUri`, `contentRevision` vía `updateContentIdentity`) y el
> commit del registro GPU están serializados a través del mismo
> `LayerGpuCommitGate`, para el estado LIVE protegido.

NO se afirma "race-free en todo el proyecto". `AudioClip` y cualquier
mutación de un `Layer` que todavía no es parte del estado LIVE
(categorías A/B) quedan explícitamente fuera de esta garantía, por las
razones descritas en la sección 7 — no porque se haya pasado por alto,
sino porque no hay ninguna propiedad que proteger ahí.

## 9. Tests agregados

> **ACTUALIZACIÓN — FASE 3.1.3-R3.1.1** (ver
> `FASE_3_1_3_R3_1_1_TEST_HARDENING.md`): los tres tests de concurrencia
> descritos en esta sección se escribieron originalmente con un
> `Thread.sleep(200)` como parte de la sincronización — funcionalmente
> correcto en la inmensa mayoría de las corridas, pero no determinista
> por construcción. R3.1.1 los reemplazó por espera acotada sobre
> `CountDownLatch.await(timeout)`, sin cambiar ninguna aserción ni la
> propiedad que cada test demuestra. Ver ese documento para el detalle.


Todos en `LayerGpuCommitGateTest.kt`, con la instancia REAL de
`LayerGpuCommitGate` (no una copia de su lógica):

- **`persistNow - la normalizacion de sourceUri no puede interponerse
  dentro de un commit GPU en curso`** — hilos reales; demuestra que la
  actualización de `persistNow` queda bloqueada mientras un commit GPU
  sigue dentro de su sección crítica, y que el commit siempre ve un
  estado consistente (nunca a medio mutar).
- **`persistNow - no sobrescribe una edicion mas nueva ocurrida durante
  el guardado`** — reproduce el TEST B del informe de esta fase.
- **`persistNow - normalizacion de mismo contenido no incrementa
  contentRevision`** — reproduce el TEST C del informe de esta fase.

Los tests de R3 (`LayerTextureRegistryScenarioTest`,
`LayerGpuOwnershipStructureTest`, y el resto de la suite de
`LayerGpuCommitGateTest`) no se modificaron — siguen siendo válidos tal
cual, ninguna de las correcciones de esta fase les afecta.
