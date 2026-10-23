# FASE 3.1.3 — Atomic GPU Resource Commit / Stale Texture Finalization

> Auditoría real del código dejado por Fase 3.1.2 (no de su documentación)
> — mandato explícito de esta fase.
>
> **ADDENDUM (Fase 3.1.3-R1):** la validación final descrita en este
> documento (sección 4) tenía un defecto de identidad de INSTANCIA de
> `Layer` (insuficiente frente a `removeLayer()` durante un upload en
> curso). Corregido en
> `docs/fases/FASE_3_1_3_R1_LAYER_INSTANCE_IDENTITY.md` — el diagrama de
> la sección 4 de este documento ya está actualizado para reflejar la
> validación vigente; el resto del documento (secciones 1-3, 5-16)
> sigue siendo válido tal cual.
>
> **ADDENDUM (Fase 3.1.3-R2):** la captura de identidad (`capturedSourceUri`/
> `capturedRevision` por separado) y la validación de una sola pasada
> descritas en R1 tenían, a su vez, dos defectos más sutiles (lectura
> entrecortada de un par de campos independientes, y una decisión de
> commit basada en un booleano calculado con antelación). Ambos
> corregidos en `docs/fases/FASE_3_1_3_R2_COMMIT_GATE.md` — el
> mecanismo real vigente usa `Layer.ContentIdentity` (un único token
> atómico) y una validación en dos etapas ("commit gate").


## 1. Resumen técnico

Fase 3.1.2 cerró la mitad "de entrada" del problema de recursos
obsoletos: ningún bitmap decodificado con una `contentRevision` vieja
puede convertirse en el argumento de `glTexImage2D` (ver
`SingleResourceHandoff.takeIfCurrent`). Pero quedaba abierta la mitad "de
salida": **la metadata que el registro (`layerTextures`) guarda sobre la
textura recién subida se leía de `layer.contentRevision` DESPUÉS de haber
subido el bitmap a GPU** — y `drawer.uploadTexture()` no es instantáneo
(clamp + `glTexImage2D` + `glFinish()`, varios milisegundos reales) mientras
el hilo principal corre en paralelo real y puede reemplazar el contenido
de la capa en ese lapso. Resultado posible:

```
GPU DATA     = bitmap de la revisión 10
GPU METADATA = layerTextures[id] = (textureId, contentRevision = 11)
```

Un TOCTOU (*Time Of Check vs Time Of Use*) real, no hipotético: el
"check" (`takeIfCurrent`) ocurre antes de subir; el "use" (armar la
metadata del registro) ocurría después, releyendo un campo mutable que ya
pudo haber cambiado.

Esta fase cierra ese TOCTOU con un protocolo de **commit atómico**:
capturar la identidad completa de la versión ANTES de cualquier trabajo,
nunca volver a leer el estado mutable de la capa para construir la
metadata, y hacer una **validación final** después de subir la textura,
antes de comprometerla al registro — si algo cambió mientras tanto, la
textura recién subida se **descarta explícitamente** (`glDeleteTextures`)
sin tocar el registro.

## 2. Archivos modificados

- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GLRenderer.kt` —
  `uploadTextureIfNeeded` (protocolo completo de commit atómico),
  `performLazyRedecodeIfNeeded` (gate corregido), `onDrawFrame` (borrado
  de textura vieja diferido hasta el commit — ver sección 5),
  `LayerTextureRecord` (comentario actualizado).
- `app/src/test/java/com/yeivikas/olyzecs/engine/render/LayerTextureRegistryScenarioTest.kt`
  — reescrito con un fake que modela el protocolo completo, tests A–I del
  checklist agregados, y un test de Fase 3.1.2 reemplazado porque su
  premisa quedó obsoleta a propósito (ver sección 5).

## 3. Archivos nuevos

Ninguno.

## 4. El protocolo, en concreto

```
CAPTURE VERSION
    capturedRevision       = layer.contentRevision
    capturedSourceUri      = layer.sourceUri
    capturedContextGeneration = contextGeneration.value
        ↓
CONSUME RESOURCE
    pendingBitmap.takeIfCurrent(capturedRevision)
    → Ready / Stale (se recicla, return) / Empty (return)
        ↓
PREPARE RESOURCE
    GpuTextureLimits.clampForTexture(bitmap)
        ↓
UPLOAD TO GPU
    val previousRecord = layerTextures[layer.id]   // se PEEK, no se borra todavía
    val newTextureId = drawer.uploadTexture(bitmap)
        ↓
FINAL VALIDATION
    // ACTUALIZADO por Fase 3.1.3-R1 — ver
    // docs/fases/FASE_3_1_3_R1_LAYER_INSTANCE_IDENTITY.md: la versión
    // original de esta fase comparaba `layer.sourceUri`/`layer.contentRevision`
    // releyendo la MISMA referencia capturada arriba — insuficiente para
    // detectar que esa instancia ya no es la capa viva del proyecto
    // (p. ej. `removeLayer()`, que nunca muta los campos del objeto que
    // elimina). La validación real, vigente, es:
    stillCurrent = currentLayerIfStillRequested(
            layer.id, capturedSourceUri, capturedRevision,
            expectedInstance = layer   // identidad REFERENCIAL, no solo layerId
        ) != null
        && contextGeneration.value == capturedContextGeneration
        ↓
   ┌─────────────┴─────────────┐
   │                           │
 ATOMIC COMMIT              ROLLBACK
   │                           │
   layerTextures[id] =        drawer.deleteTexture(newTextureId)
     Record(newTextureId,      (el registro NO se toca; `previousRecord`,
       capturedRevision)        si había, queda intacto y sigue siendo
   delete(previousRecord)       la textura válida vigente)
   layer.widthPx/heightPx
```

## 5. Cambio de diseño necesario: borrado de textura vieja DIFERIDO

Al implementar la validación final se encontró un problema adicional real
(no solo teórico) heredado de fases anteriores: `onDrawFrame` borraba la
textura vigente de una capa **apenas se pedía la invalidación**, antes
incluso de que el reemplazo estuviera listo (el redecode puede tardar
varios frames). Con el commit atómico nuevo, eso significa que una capa
podía quedar **sin ninguna textura válida** durante esa ventana — el
síntoma exacto de "capas transparentes" que las fases 3.1/3.1.1 ya habían
tenido que corregir en otros puntos del pipeline.

Se corrigió moviendo el borrado de la textura vieja a un único lugar: el
propio commit atómico de `uploadTextureIfNeeded`, que solo la borra
DESPUÉS de confirmar que la nueva está lista y validada. `onDrawFrame` ya
no borra nada — solo consume la bandera de invalidación (para dar una
nueva oportunidad de redecode) y delega todo lo demás.

Esto rompió la condición de disparo del redecode perezoso
(`performLazyRedecodeIfNeeded`), que antes asumía "si `layerTextures[id]`
existe, no hace falta redecodificar". Con el borrado diferido, una capa
puede tener una textura EXISTENTE pero OBSOLETA (de la revisión vieja,
todavía no reemplazada) — el gate se corrigió a: *"¿la textura que tengo,
si tengo alguna, es la de la revisión vigente?"* — si no lo es, corresponde
intentar el redecode igual.

Un test de Fase 3.1.2/3.1.1 quedó con una premisa obsoleta por este mismo
cambio ("un pedido de invalidación por sí solo borra la textura") y se
reemplazó por el comportamiento correcto y deseado: la textura vieja se
mantiene hasta tener, de verdad, un reemplazo válido.

## 6. Invariantes verificadas con tests

| # | Invariante | Test |
|---|---|---|
| 1 | La `contentRevision` de una textura GPU es exactamente la del recurso que la produjo | TEST C |
| 2 | La `contextGeneration` de una textura es exactamente la del contexto en que se creó | TEST D |
| 3 | Ningún decode obsoleto puede comprometerse | TEST A, F (heredado de 3.1.2) |
| 4 | Ningún upload obsoleto puede comprometerse | TEST A |
| 5 | Un upload obsoleto se borra si ya se había creado | TEST B, D, E |
| 6 | Una capa eliminada no puede recibir una textura comprometida | TEST E |
| 7 | Una revisión más nueva nunca es pisada por un resultado viejo | TEST F, G |
| 8 | La metadata del registro no sale de una relectura del estado mutable actual tras el upload | TEST C (diseño), verificado por construcción en el código real |
| 9 | El Bitmap externo del Grid nunca se recicla | Sin cambios — cubierto por `GridTextureCacheStateTest`, no tocado |
| 10 | Todo Bitmap temporal propiedad del renderer tiene una ruta determinística de disposal | Heredado de Fase 3.1.2, sin cambios |

TEST H (undo/redo con upload en curso) y TEST I (identidad `sourceUri` +
`revision` coherente, no solo uno de los dos) también se agregaron.
TEST J (Grid) no se duplicó: el protocolo nuevo no toca en absoluto el
camino del Grid (`updateGridTextureIfNeeded`), que sigue siendo un
`Bitmap` externo de Compose, nunca reciclado por `GLRenderer` — sin
cambios de código, sin necesidad de un test nuevo.

## 7. Sobre `contextGeneration` como parte de la identidad

El informe sugiere incluir `contextGeneration` como campo del registro
(`layerId + contentRevision + contextGeneration`). Se decidió **no**
agregarlo como campo de `LayerTextureRecord`: el mapa completo
(`layerTextures`) se vacía por completo en cada `onSurfaceCreated()` real
(generación nueva) — por construcción, toda entrada que exista en el mapa
en un momento dado pertenece siempre a la generación vigente, sin
necesitar guardarla por registro. La validación de generación SÍ se hace,
explícitamente, en la validación final del commit — es la comparación la
que importa, no dónde se la guarda después.

Dicho esto: en la arquitectura real de `GLSurfaceView.Renderer`,
`onSurfaceCreated()` y `onDrawFrame()` corren siempre en el mismo hilo de
GL y nunca se solapan entre sí (contrato de la clase). Esto significa que
`contextGeneration.value` **no puede, hoy, cambiar en medio de**
`uploadTextureIfNeeded()` — TEST D reproduce el escenario igual, como red
de seguridad explícita y documentada, no porque exista una ventana real
conocida en el código actual.

## 8. `glFinish()` — se mantiene, y sigue siendo necesario

No se tocó. Su razón de ser (documentada en `LayerDrawer.uploadTexture`)
es distinta a la de esta fase: garantizar que la copia de píxeles a GPU
terminó antes de que el bitmap se recicle del lado de CPU — un problema
de *drivers* (`GLUtils.texImage2D` puede dejar la copia pendiente/
asíncrona), no de *content revision*. El protocolo nuevo de esta fase no
cambia esa necesidad: sigue haciendo falta, con o sin validación de
versión.

## 9. `layer.widthPx`/`layer.heightPx` (Problema 21)

`GLRenderer` sigue escribiendo estos dos campos directamente sobre el
`Layer` compartido — una mutación de estado lógico hecha desde el hilo de
GL como efecto secundario de un upload. Con el protocolo nuevo, esta
escritura ahora ocurre **únicamente dentro de la rama de COMMIT
exitoso** (nunca en un rollback), así que al menos nunca corresponde a un
recurso descartado. No se corrigió la separación de ownership de fondo
(¿debería esto vivir fuera de `Layer`, en algo como `layerTextures`,
junto al resto de la metadata GPU?) por ser un cambio de alcance mayor al
de esta fase — **se documenta como riesgo residual** (ver sección 11),
tal como permite explícitamente el punto 21 del informe.

## 10. `TextureInvalidationRequest` — no se le agregó identidad de versión

El informe sugiere (sección 25) asociar la invalidación con
`layerId + revision`. Se auditó y se decidió que no hace falta: cada
`TextureInvalidationRequest` YA es un campo `@Transient` que vive DENTRO
de un `Layer` específico (no un objeto global ni compartido entre capas),
así que no existe ningún escenario donde invalidar la capa X pueda
"contaminar" la capa Y por error — la identidad de capa ya está dada por
la propia referencia al objeto. Y como la metadata del commit ya sale
siempre de la revisión CAPTURADA (nunca de una relectura), una
invalidación que se "consuma tarde" o se colapse (dos pedidos rápidos que
terminan siendo una sola bandera consumida una vez) no compromete la
corrección: el commit siempre valida contra el estado vigente en el
momento exacto de subir, sin importar cuántas invalidaciones se pidieron
en el camino.

## 11. Riesgos residuales

1. **`layer.widthPx`/`layer.heightPx`** siguen siendo mutados directamente
   por `GLRenderer` (ver sección 9) — arquitectónicamente sería más
   limpio que esta metadata viviera junto al resto del estado GPU
   (`layerTextures`), pero corregirlo es un cambio de alcance mayor,
   señalado explícitamente y no abordado en esta fase.
2. El redecode perezoso (`performLazyRedecodeIfNeeded`) sigue siendo
   síncrono en el hilo de GL — sin cambios respecto al riesgo ya señalado
   en Fase 3.1.2.
3. No se agregó infraestructura de test de integración real contra
   `GLRenderer`/`EditorViewModel` (Robolectric) por las mismas razones de
   alcance documentadas en Fase 3.1.2 — los escenarios de esta fase están
   cubiertos a nivel de la lógica pura que sostiene el protocolo real
   (misma estructura de datos, misma secuencia de operaciones, verificada
   línea por línea contra el código real de `GLRenderer.kt`).

## 12. Estado final de Fase 3.1.3

**Cerrada.** La garantía central — *ningún recurso obsoleto, ni de CPU
(bitmap) ni de GPU (textura ya subida), puede terminar comprometido como
el recurso vigente de una capa, ni siquiera con su metadata mal
etiquetada* — está garantizada por el protocolo de commit atómico
descrito arriba y verificada con los tests A–I. Los riesgos residuales de
la sección 11 quedan señalados explícitamente, ninguno compromete esa
garantía central.

No se avanza a Fase 4.
