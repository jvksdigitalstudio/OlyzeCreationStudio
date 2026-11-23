# FASE 3.1.1 — Hotfix de 3 regresiones reales introducidas por el cierre de Fase 3.1

> Reportadas por el usuario tras el cierre de Fase 3.1 (ownership de GPU).
> Las tres son consecuencia directa de ese cierre — ninguna es un bug
> preexistente que "se hizo más visible". Se confirma con captura de
> pantalla y con el código real, no solo con el reporte verbal.

## Resumen ejecutivo

| # | Síntoma reportado | Causa raíz | Archivo corregido |
|---|---|---|---|
| 1 | Reemplazar la imagen de una capa y luego Deshacer no vuelve a la imagen anterior — se queda en la nueva | `replaceLayerImage` nunca llamaba a `pushUndoCheckpoint()` — es la ÚNICA función que muta una capa sin hacerlo (todas las demás sí) | `EditorViewModel.kt` |
| 2 | Editando, las capas se ponen transparentes (más que antes de esta actualización) | El redecode perezoso "GL decodifica de nuevo desde `sourceUri`" que la documentación de Fase 3.1 daba por hecho **solo existía en `onSurfaceCreated`** (una vez por contexto EGL) — nunca en el `onDrawFrame` normal, que es donde de verdad hacía falta tras un `pendingBitmap.publish(null)` + invalidación (undo/redo, revertir edición, descartar cambios) | `GLRenderer.kt` |
| 3 | Borrar una capa, editar otra imagen, y al volver las DEMÁS capas quedan transparentes | Al entrar al modo edición aislado, `getLayers` (el mismo lambda que `GLRenderer` usa para saber qué texturas GPU siguen vivas) se filtraba a solo la capa en edición — `GLRenderer` interpretaba eso como "las demás capas se eliminaron" y borraba sus texturas GPU (el propio fix de memory leak de Fase 3.1 §4.5, disparado por error) | `EditorScreen.kt` |

Los tres bugs comparten familia (ownership de texturas GPU, Fase 3.1) pero
son tres causas raíz DISTINTAS — no un solo bug con tres síntomas.

## 1. Bug real: `replaceLayerImage` sin checkpoint de Deshacer

Todas las funciones que mutan una capa de un solo golpe (`setLayerCustomColor`,
`resetLayerColor`, `toggleLayerLock`, `setParallaxFactor`, etc.) llaman a
`pushUndoCheckpoint(force = true)` ANTES de aplicar el cambio — es el
patrón consistente en todo `EditorViewModel.kt`. `replaceLayerImage` era la
única excepción real: aplicaba `replaceLayer(...)` directamente, sin dejar
ningún checkpoint. Sin un estado "de antes" guardado en la pila, `undo()`
no tenía nada que restaurar para esta acción específica — la saltaba por
completo.

**Corrección:** se agregó `pushUndoCheckpoint(force = true)` al principio
de `replaceLayerImage`, antes de lanzar la corrutina de decodificación —
mismo criterio y mismo lugar relativo que el resto de las funciones
comparables.

## 2. Bug real: el redecode perezoso documentado no existía donde hacía falta

La Fase 3.1 introdujo, a propósito, un patrón donde el hilo principal
"pide" invalidar una textura (`requestTextureInvalidation()`) y publica
`pendingBitmap = null` cuando revierte una imagen (undo/redo de
`sourceUri`, `revertLayerEditSession`, `revertLayerToUri`,
`discardChangesAndExit`). El comentario de esos sitios afirmaba: *"el
motor GL decodifica de nuevo desde `sourceUri` la primera vez que
encuentra una capa sin textura ni bitmap pendiente"*.

Esa afirmación era **falsa** en la práctica: el único lugar del código que
hacía ese redecode era `GLRenderer.onSurfaceCreated` — que corre UNA vez
por cada contexto EGL nuevo (abrir el proyecto, volver de segundo plano),
nunca durante el `onDrawFrame` normal de una sesión de edición en vivo.
Resultado real: la textura se borraba (correcto), pero nada la
reemplazaba — la capa quedaba sin textura, invisible, hasta la próxima
recreación de contexto.

**Corrección:** `GLRenderer.uploadTextureIfNeeded` ahora hace, de verdad,
ese redecode perezoso: si una capa no tiene bitmap pendiente NI textura
GPU vigente, se decodifica desde `sourceUri` (mismo método y mismo límite
de hardware que ya usaba `onSurfaceCreated`) y se publica para subirse en
el mismo ciclo. Se agregó `failedRedecodeIds` para no reintentar sin
parar (60 veces por segundo) un decode que ya falló — se limpia en cada
invalidación nueva y en cada recreación de contexto.

## 3. Bug real: el modo edición aislado "falsificaba" una eliminación masiva de capas

`EditorScreen` filtra la capa a mostrar en el modo edición aislado
("que se centre... y que todas desaparezca que solo quede esa imagen")
usando el MISMO lambda `getLayers` que se le pasa a `GLRenderer` — y que
`GLRenderer` usa, desde la Fase 3.1 §4.5, para podar (`glDeleteTextures`)
las texturas de capas que ya no aparecen en `getLayers()` (el fix real de
memory leak para capas EFECTIVAMENTE eliminadas del proyecto).

`GLRenderer` no tiene forma de distinguir "esta capa se filtró
temporalmente para una vista aislada" de "esta capa se eliminó del
proyecto" — para ese código son, literalmente, la misma señal. Cada vez
que se entraba al modo edición aislado, todas las capas que no eran la
editada perdían su textura GPU real — y al volver a la pantalla
principal, quedaban sin textura (transparentes) hasta que algo más
disparara un redecode.

**Corrección:** `getLayers` (pasado a `GLRenderer`) ya NUNCA se filtra por
modo edición — siempre devuelve el set real y completo de capas vivas del
proyecto, que es lo único que le corresponde responder. El aislamiento
visual sigue intacto porque ya se lograba, en paralelo, filtrando
`getRenderSnapshot` — la lista que de verdad decide qué se DIBUJA en cada
frame. Son dos preguntas distintas ("¿qué existe?" vs. "¿qué se dibuja
ahora?") que compartían, por error, una sola respuesta.

## Archivos modificados

- `app/src/main/java/com/yeivikas/olyzecs/engine/render/GLRenderer.kt` —
  redecode perezoso real en `uploadTextureIfNeeded` + registro
  `failedRedecodeIds` con su poda correspondiente en `onSurfaceCreated`/
  `onDrawFrame`.
- `app/src/main/java/com/yeivikas/olyzecs/viewmodel/EditorViewModel.kt` —
  `pushUndoCheckpoint(force = true)` agregado a `replaceLayerImage`.
- `app/src/main/java/com/yeivikas/olyzecs/ui/EditorScreen.kt` — `getLayers`
  del `GLPreview` ya no se filtra por modo edición aislado; el filtro
  queda únicamente en `getRenderSnapshot`.

## Verificación manual recomendada

1. Reemplazar la imagen de una capa → Deshacer → debe volver la imagen
   anterior (no solo el nombre/color, la imagen real en pantalla).
2. Con 2+ capas: entrar al modo edición aislado de una, hacer cualquier
   cambio, confirmar o cancelar, volver a la pantalla principal → las
   demás capas deben seguir visibles, sin parpadeo ni transparencia.
3. Repetir el punto 2 después de haber borrado una capa distinta primero
   (el combo exacto reportado).
4. Recheck de la cuadrícula (Fase 3.1, ya confirmado por el usuario que
   funciona) — no debería haberse tocado nada de eso en este hotfix.

## Riesgo residual

El redecode perezoso de `uploadTextureIfNeeded` (§2) decodifica de forma
SÍNCRONA en el hilo de GL. Para una capa puntual invalidada esto es
aceptable (mismo costo que ya pagaba `onSurfaceCreated` por TODAS las
capas juntas al reabrir un proyecto), pero si en el futuro se encuentra
que introduce un frame-drop perceptible con imágenes muy grandes, la
solución de fondo sería mover ese decode a una corrutina de IO y publicar
el resultado de forma asíncrona — no está hecho en este hotfix por no
estar en el alcance de los tres bugs reportados.
