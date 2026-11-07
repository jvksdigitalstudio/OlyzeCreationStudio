# FASE 8 — Papelera de capas

> **Nota (posterior a esta fase):** la presentación descrita acá —
> `LayerTrashDialog`, un `Dialog` a pantalla completa — fue reemplazada
> por un panel anclado al ícono de papelera (`LayerTrashPanel`, un
> `Popup`) en una fase posterior. Ver
> [`FASE_8_R1_PAPELERA_PANEL_ANCLADO.md`](./FASE_8_R1_PAPELERA_PANEL_ANCLADO.md).
> Modelo de datos, persistencia y las 5 operaciones de `ProjectStorage`
> descritas acá siguen vigentes sin cambios — la R1 es puramente de
> presentación. Este documento se conserva tal cual para mantener el
> registro histórico de esa fase.
>
> Origen: pedido explícito del usuario/product owner, co-diseñado en
> conversación iterativa (no un "prompt maestro" de auditoría como las
> fases anteriores). El punto de partida fue un reporte de build (ver
> `docs/fases/` anteriores para esa parte, ajena a esta) y una pregunta de
> diseño concreta: "si elimino la última capa de un proyecto ya guardado,
> ¿qué pasa?". Este documento cubre únicamente lo que se **implementó y
> verificó por inspección de código** en esta sesión — ver "Estado real /
> pendiente" al final.

## Resumen ejecutivo

| # | Decisión / problema | Resolución | Archivo(s) |
|---|---|---|---|
| 1 | `saveProject`/`persistNow` ya tenían (de una sesión previa a esta fase) un guard `if (layers.isEmpty()) return` que vuelve un no-op silencioso al autoguardado cuando el proyecto queda en 0 capas — pero el diálogo de confirmación de borrado no lo sabía, y dejaba "eliminar" la última capa igual, prometiendo "esta acción no se puede deshacer" sin poder cumplirlo | Bloquear el borrado de la última capa en el punto de decisión (el diálogo), con un mensaje distinto que explica por qué y ofrece la vía real ("Mis proyectos" → eliminar el proyecto entero) | `EditorScreen.kt` |
| 2 | Al eliminar una capa, no había ninguna forma de arrepentirse — la imagen y sus datos se perdían para siempre en el próximo guardado | Papelera de capas persistida en `project.json`: eliminar ya no destruye nada al instante, mueve la capa a una lista de espera con snapshot completo (imagen, keyframes, look) | `ProjectModels.kt`, `ProjectStorage.kt` |
| 3 | El usuario pidió enterarse proactivamente, pero solo en el caso extremo (quedó con 1 sola capa), sin interrumpir en cada apertura normal del proyecto | Banner no bloqueante, condicionado a `layers.size == 1 && papelera no vacía`, con un flag persistido para que se muestre una sola vez por episodio | `ProjectData.trashBannerAcknowledged`, `ProjectStorage.saveProject`/`loadProject` |
| 4 | Pantalla de gestión con selección múltiple, dos acciones independientes ("restaurar/eliminar seleccionadas" vs. "vaciar todo"), sin límite de retención automático pero con el peso en disco siempre visible | `LayerTrashDialog` (pantalla completa) + `TrashRecoveryBanner` (aviso flotante) | `LayerTrashScreen.kt` (nuevo) |
| 5 | Auditoría posterior de esta misma fase encontró 2 inconsistencias menores (ninguna es un bug con impacto visible para el usuario) | Corregidas en la misma sesión — ver sección 7 | `ProjectStorage.kt`, `LayerTrashScreen.kt` |

## 1. Contexto previo: bloqueo de la última capa

Antes de que existiera la papelera, ya se había cerrado un hueco distinto
en una sesión anterior a esta fase: `EditorViewModel.persistNow` y
`ProjectStorage.saveProject` comparten, cada uno por su cuenta, el mismo
guard `if (layers.isEmpty()) return` — un proyecto nunca se guarda con 0
capas. Pero el diálogo "¿Eliminar esta capa?" no distinguía si la capa que
se iba a borrar era la última, así que dejaba avanzar el borrado igual,
mostrando "esta acción no se puede deshacer" — una promesa que en ese caso
puntual la app no podía cumplir (el autoguardado con 0 capas es un no-op
silencioso: la capa "vuelve" al reabrir el proyecto, sin ningún aviso de
que eso iba a pasar).

Se corrigió en el punto de decisión, no en el guardado: en
`EditorScreen.kt`, el diálogo compartido de borrado de capa (disparado
desde el ícono de basurero de la fila de la capa y desde la X del marco de
selección en el lienzo) ahora rama según `state.layers.size`:

- Si es la única capa que queda: diálogo informativo ("No se puede
  eliminar la última capa — Un proyecto necesita al menos una capa. Si
  querés vaciarlo por completo, eliminá el proyecto entero desde 'Mis
  proyectos'"), sin opción de eliminar.
- En cualquier otro caso: el diálogo de siempre, sin cambios.

Esta decisión es la que hace posible, más abajo, que la condición de
disparo del banner de la papelera (sección 4) sea simplemente
`layers.size == 1` — es el único estado límite alcanzable por el usuario
antes de chocar contra este bloqueo.

## 2. Modelo de datos (`ProjectModels.kt`)

Nuevo tipo:

```kotlin
@Serializable
data class DeletedLayerData(
    val layer: LayerData,
    val deletedAtMs: Long
)
```

Reutiliza `LayerData` completo (no un subconjunto de campos) a propósito:
restaurar tiene que devolver la capa EXACTAMENTE como estaba —
keyframes de cámara, look, transform, todo — no una versión degradada.

Dos campos nuevos en `ProjectData`:

- `trashedLayers: List<DeletedLayerData> = emptyList()` — la papelera en
  sí. Sin límite de retención automático (decisión de producto explícita):
  se acumula hasta que el usuario mismo decide vaciar.
- `trashBannerAcknowledged: Boolean = true` — si ya se le avisó al usuario
  de ESTE episodio puntual de "me quedé con una sola capa". Default
  `true` para que la ausencia de este campo en un `project.json` guardado
  antes de esta fase nunca se interprete como "hay que avisar" (esos
  proyectos nunca tuvieron papelera, no hay nada que avisar).

## 3. Detección y persistencia (`ProjectStorage.saveProject`)

Antes de esta fase, "la capa ya no está en `layers`" y "borrar su imagen
para siempre" eran la misma cosa, en el mismo paso. Ahora se separan:

1. Al guardar, se compara `existing.layers` (lo que había en
   `project.json` ANTES de este guardado, leído fresco de disco bajo el
   mutex del proyecto) contra los ids del guardado actual.
2. Toda capa que estaba antes y ya no está ahora pasa a
   `trashedLayers` con su `LayerData` completo y un `deletedAtMs` nuevo.
3. La imagen de la capa recién eliminada **no se mueve de directorio** —
   sigue en `images/` con el mismo nombre de siempre. Pasar a formar
   parte de `trashedLayers` ya alcanza para que `validImageNames` (el
   set que decide qué archivos sobreviven a la limpieza de huérfanos
   post-commit, ver Fase 4.2-R4) la siga considerando válida.
4. `trashBannerAcknowledged` se reinicia a `false` cada vez que cae algo
   NUEVO a la papelera en este guardado — un episodio nuevo de "me quedé
   con una sola capa" merece un aviso nuevo, no reutilizar el "ya lo vi"
   de un episodio anterior. Si no cayó nada nuevo, se conserva el valor
   que ya tenía.

Se verificó a mano, trazando el escenario completo (borrar → guardar →
restaurar → guardar de nuevo), que esta separación en dos pasos nunca
duplica ni pierde una capa — el detalle completo está en la sección 7
(hallazgos de la auditoría), no porque se haya encontrado un bug ahí, sino
porque es el punto más fácil de razonar mal en todo el diseño y se
documenta el razonamiento completo para que quede trazable.

## 4. Operaciones de la papelera

Cinco funciones nuevas en `ProjectStorage`, todas bajo
`mutexFor(projectId).withLock` (misma exclusión mutua que ya protegía
`saveProject`/`loadProject`) y usando `writeProjectDataAtomically`
(tmp-file + rename, la misma protección contra un proceso muerto a mitad
de escritura que ya se había generalizado a los otros 9 puntos de
escritura de `project.json` en una sesión anterior a esta fase):

- **`trashInfo(projectId)`** — lee la papelera actual, resuelta para la
  UI (archivos confinados a `images/` vía `resolveManifestFile`, tamaño
  en disco ya sumado). Usada para refrescar el estado tras cualquier
  mutación, sin tener que reconstruir un `LoadedProject` completo.
- **`restoreLayersFromTrash(projectId, layerIds)`** — saca las entradas
  pedidas de `trashedLayers`, las reconstruye EN VIVO
  (`layerDataToLiveLayer`, ver sección 5) y las devuelve para que el
  ViewModel las agregue de vuelta al lienzo. Un id que ya no está en la
  papelera se ignora en silencio (carrera benigna).
- **`deleteLayersFromTrashPermanently(projectId, layerIds)`** — saca las
  entradas de `trashedLayers` y borra sus imágenes de `images/`. Sin
  vuelta atrás a partir de ahí.
- **`emptyTrash(projectId)`** — mismo criterio que la anterior, aplicado
  a TODAS las entradas de una vez.
- **`acknowledgeTrashBanner(projectId)`** — marca
  `trashBannerAcknowledged = true`, para que el aviso no reaparezca por
  este mismo episodio.

`layerDataToLiveLayer` (la reconstrucción `LayerData → Layer` en vivo) se
extrajo de `loadProject` a una función privada compartida — antes vivía
duplicada inline dentro del loop de carga; ahora tanto abrir un proyecto
como restaurar una capa de la papelera pasan por el mismo código, sin
riesgo de que un cambio futuro (un campo nuevo de `LayerData`, un
fallback distinto) se aplique en un lado y se olvide en el otro.

## 5. Capa de presentación (`EditorViewModel`)

`EditorUiState` gana 3 campos: `trashedLayers`, `trashSizeBytes`,
`showTrashRecoveryBanner` — copiados 1:1 desde `LoadedProject` al cargar
el proyecto (la regla de "cuándo mostrar el banner" vive enteramente en
`ProjectStorage.loadProject`, no se repite en el ViewModel).

Cuatro funciones nuevas, todas con el mismo patrón que el resto de "agregar/
quitar una capa" de esta clase (ver `addLayers`, `removeLayer`):
`restoreLayersFromTrash`, `deleteLayersFromTrashPermanently`, `emptyTrash`,
`dismissTrashBanner`. Deliberadamente, **`removeLayer` no cambió**: sigue
sin saber nada de la papelera — solo saca la capa del estado en memoria.
Es el próximo autoguardado (disparado por el `scheduleAutosave()` que
`removeLayer` ya llamaba) el que, comparando contra el `project.json`
anterior, decide que esa capa pasa a la papelera. Una sola
responsabilidad por función, sin acoplar el gesto de la UI a la política
de persistencia.

## 6. UI (`LayerTrashScreen.kt`, nuevo archivo)

Archivo autocontenido a propósito — ni un import de `EditorViewModel`,
mismo patrón que `ProjectInfoPanel`/`ErrorLogScreen`: recibe datos y
callbacks, el acoplamiento con el ViewModel vive únicamente en el call
site (`EditorScreen.kt`).

- **`TrashRecoveryBanner`** — pastilla flotante centrada, del ancho de su
  propio contenido (no de punta a punta), con color de identidad propio
  (el mismo ámbar "AVISO" que ya usa `ErrorLogScreen` para su badge de
  warning, reutilizado a propósito con el mismo significado). Ajustado a
  este diseño en una segunda pasada — la primera versión era una franja
  completa con el gradiente genérico de los demás headers de la app, y se
  confundía visualmente con "un header más".
- **`LayerTrashDialog`** — pantalla completa: header con tamaño total en
  disco, "Seleccionar todo", "Vaciar papelera" (acción de un toque,
  independiente de la selección), lista con checkbox + miniatura real
  (Coil `AsyncImage`) + fecha + peso individual, y una barra inferior con
  "Eliminar (N)"/"Restaurar (N)" que reaccionan a la cantidad
  seleccionada. Al restaurar, el diálogo se cierra automáticamente
  (ajustado a pedido explícito: restaurar es "quiero volver a trabajar
  con esto ya", el destino natural es el lienzo — mismo criterio que
  Google Drive/Google Fotos al restaurar de su papelera). "Eliminar
  seleccionadas" y "Vaciar papelera" NO cierran el diálogo — son acciones
  de limpieza donde tiene sentido seguir revisando el resto.
- **`EmptyTrashConfirmDialog`/`DeleteSelectedFromTrashConfirmDialog`** —
  confirmaciones con la frase acordada explícitamente ("Esta acción es
  irreversible... se eliminarán de forma permanente y no podrán
  recuperarse"), mismo patrón visual que el resto de diálogos
  destructivos de la app.

Reutiliza `formatFileSize`/`formatFullDate` (ya existentes en
`ProjectsScreen.kt` para el panel de info del proyecto, promovidos de
`private` a `internal`) en vez de reinventar el formateo de tamaño/fecha
en el archivo nuevo.

Enganchado en `EditorScreen.kt`: ícono con badge de cantidad en la barra
superior (mismo criterio de visibilidad que Información/Cuadrícula —
oculto en modo edición de imagen aislada), el banner como primer elemento
del `Column` principal (justo arriba del preview), y el diálogo hospedado
junto al resto de diálogos de la pantalla (`RenameProjectDialog`, etc.).

## 7. Auditoría posterior — hallazgos y correcciones

Tras completar la función, se hizo una segunda pasada dedicada
exclusivamente a este trabajo (no una auditoría general del proyecto).
Se encontraron y corrigieron 2 inconsistencias — ninguna con impacto
visible para el usuario, ambas de calidad/consistencia interna:

1. **`deleteLayersFromTrashPermanently` sin el mismo corte temprano que
   sus dos funciones hermanas.** `restoreLayersFromTrash` y `emptyTrash`
   ya evitaban una escritura atómica innecesaria cuando no había nada que
   hacer (`toRestore.isEmpty()` / `trashedLayers.isEmpty()`);
   `deleteLayersFromTrashPermanently` no tenía ese mismo guard y
   reescribía `project.json` con el mismo contenido de siempre ante una
   selección vacía o desactualizada. Se agregó el mismo corte.
2. **Encapsulamiento débil en `LayerTrashScreen.kt`.**
   `EmptyTrashConfirmDialog` y `DeleteSelectedFromTrashConfirmDialog`
   estaban declarados `fun` (público) cuando solo se usan dentro del
   propio archivo, llamados únicamente desde `LayerTrashDialog`. Se
   marcaron `private`, igual que `TrashLayerRow`.

Se verificó además, sobre el código final: cero imports sin usar en todo
`app/src/main` (barrido completo, no solo los archivos tocados), cero
clases/funciones duplicadas, cero TODO/FIXME pendientes reales, y balance
de llaves/paréntesis correcto en los 6 archivos de esta fase.

## 8. Decisiones de diseño acordadas (registro, no solo el código)

Para que quede trazable el "por qué" detrás de cada elección, no solo el
"qué":

- **Sin límite de retención automático.** La papelera se acumula hasta
  que el usuario mismo decide vaciarla — a cambio, la UI muestra siempre
  el tamaño en disco que ocupa (header de `LayerTrashDialog`), para que
  esa decisión sea informada, no a ciegas.
- **El aviso proactivo (banner) solo en el caso límite.** No se muestra
  ante cualquier borrado — solo cuando el proyecto queda con exactamente
  1 capa viva y hay algo en la papelera. Se descartó una versión anterior
  que se mostraba en cada apertura mientras la situación siguiera vigente
  (se consideró intrusivo); la versión final se muestra una sola vez por
  episodio.
- **Restaurar ≠ Guardar y salir.** Se descartó explícitamente la idea de
  que restaurar la última capa desde cero SOLO fuera posible al confirmar
  la salida del editor, y más aún la idea de que "Guardar y salir"
  borrara el proyecto entero si quedaba vacío — un botón de uso rutinario
  nunca debe esconder una acción destructiva e irreversible como efecto
  secundario. La vía para vaciar un proyecto por completo sigue siendo,
  a propósito, una acción explícita y separada desde "Mis proyectos".
- **Restaurar cierra el diálogo; eliminar/vaciar no.** Ver sección 6.
- **Color de identidad propio para el aviso, reutilizando semántica ya
  existente** (el ámbar "AVISO" de `ErrorLogScreen`) en vez de inventar
  un tono nuevo sin relación con el resto de la app.

## Archivos de esta fase

| Archivo | Tipo de cambio |
|---|---|
| `data/ProjectModels.kt` | `DeletedLayerData` + 2 campos nuevos en `ProjectData` |
| `data/ProjectStorage.kt` | Detección de capas eliminadas en `saveProject`; 5 funciones nuevas; extracción de `layerDataToLiveLayer`/`buildTrashSummary` |
| `viewmodel/EditorViewModel.kt` | 3 campos nuevos en `EditorUiState`; 4 funciones nuevas |
| `ui/LayerTrashScreen.kt` | **Nuevo** — banner + pantalla completa + 2 confirmaciones |
| `ui/EditorScreen.kt` | Bloqueo de última capa (sección 1); ícono con badge; banner; diálogo hospedado |
| `ui/ProjectsScreen.kt` | `formatFileSize`/`formatFullDate` promovidos de `private` a `internal` |

## Criterios de aceptación

- [x] Eliminar una capa nunca destruye su imagen/datos al instante.
- [x] Un proyecto restaurado desde la papelera recupera la capa
      exactamente como estaba (imagen, keyframes, look).
- [x] La papelera persiste en `project.json` — sobrevive cerrar la app y
      matar el proceso, no solo la sesión de edición en memoria.
- [x] Ninguna de las 5 operaciones nuevas puede correr concurrentemente
      con un guardado del mismo proyecto (mismo mutex).
- [x] Ninguna de las 5 operaciones puede dejar `project.json` a medio
      escribir ante un crash (misma escritura atómica ya generalizada).
- [x] El aviso proactivo se muestra solo en el caso acordado (1 capa
      viva + papelera no vacía) y solo una vez por episodio.
- [x] No se puede llegar a un proyecto con 0 capas desde la UI de
      borrado normal.
- [x] Restaurar seleccionadas cierra el diálogo; eliminar
      seleccionadas/vaciar papelera no.
- [x] Cero imports sin usar, cero duplicados, balance sintáctico
      correcto — verificado sobre el código final, no solo sobre el
      diff.
- [ ] Sin tests automatizados nuevos para esta fase — ver "Estado real /
      pendiente".

## Estado real / pendiente

- **No se agregaron tests unitarios** para las 5 funciones nuevas de
  `ProjectStorage` ni para la lógica de detección en `saveProject`. Esta
  fase se verificó por inspección de código y por prueba manual del
  usuario en dispositivo real (capturas de pantalla revisadas en
  conversación), no por test automatizado — deuda técnica real, no
  ficticia.
- **No hay ADR dedicado.** Las decisiones de diseño de la sección 8 están
  registradas acá, no en `docs/adr/`. Si esta política de papelera
  (sin límite de retención, restaurar cierra el diálogo) se considera
  una decisión de suficiente peso arquitectónico como para necesitar su
  propio ADR, queda pendiente crearlo — no se creó en esta fase para no
  inflar el alcance más allá de lo pedido.
- **El zIndex de una capa restaurada se preserva tal cual estaba al
  eliminarla**, sin renumerar contra el estado actual del lienzo. En el
  caso (poco común) de que otras capas se hayan reordenado mientras esta
  estaba en la papelera, la capa restaurada podría aparecer en una
  posición de stacking distinta a la esperada — cosmético, no una
  corrupción de datos (mismo argumento de riesgo/beneficio que otros
  casos de `zIndex` ya documentados en fases anteriores). No se corrigió
  por no haber sido reportado como problema real.
- **No se actualizó `ARCHITECTURE.md`.** El flujo `UI → EditorViewModel →
  ProjectStorage` que ya describe ese documento cubre esta fase sin
  necesidad de ningún cambio — la papelera no introduce ninguna capa,
  dominio de `engine/*` ni frontera de EliNer API nueva. Se corrigió,
  en cambio, `README.md` (la sección de funciones del panel de capas
  tenía una línea desactualizada por esta fase) — ver el changelog ahí.
