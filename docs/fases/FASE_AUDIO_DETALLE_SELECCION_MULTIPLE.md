# Fase — Selección de varios rangos («Seleccionar» x1 … x7) en la ventana ampliada del audio

**Alcance:** poder marcar **varios rangos** sobre la onda de un clip (hasta 7) y que la vista previa **reproduzca solo esos
tramos, en orden de izquierda a derecha**. La cantidad se elige con un botón nuevo, «Seleccionar», a la izquierda de «Recorte».
Amplía la fase `FASE_AUDIO_DETALLE_SELECCION_DE_RANGO.md` (un solo rango); no cambia el audio ni añade acciones de edición.

## 1. Comportamiento

### Botón «Seleccionar» (cabecera, a la izquierda de «Recorte»)
- Arriba, el número `x1` (por defecto) … `x7` con una flecha a cada lado; al pie, el texto «Seleccionar».
- La cantidad se cambia de **dos formas**:
  1. **Flechas:** la izquierda resta y la derecha suma de uno en uno; se atenúan y dejan de responder en `x1` / `x7`.
  2. **Arrastre vertical sobre el número:** pulsar y arrastrar sin soltar hacia **arriba suma** y hacia **abajo resta**. Cada
     14 dp de recorrido cambia una unidad, medido contra donde se apoyó el dedo (no se acumula error: volver al origen devuelve la
     cantidad original).
- Es estado de sesión de la ventana de cada clip: nace en `x1` y se descarta al cambiar de clip/archivo o cerrar.

### Reglas de la selección con N = cantidad elegida
| Situación | Resultado |
|---|---|
| Arrastrar en un hueco libre con cupo (`rangos < N`) | Nace un rango nuevo, anclado donde se apoyó el dedo. |
| Arrastrar con el cupo lleno (`rangos = N`) lejos de los bordes | **No crea otro rango.** (Con `x1`: hay que tocar la onda para deseleccionar y volver a marcar.) |
| Arrastrar empezando **dentro** de un rango existente | No crea nada (los rangos no se solapan). |
| Arrastrar empezando sobre el **borde de cualquier rango** (≤ 22 dp) | Mueve ese borde (el opuesto queda fijo, sin salto), **aunque el cupo esté lleno**. |
| Un rango que se arrastra choca con un vecino | Se detiene en el borde del vecino: nunca se solapan. |
| **Bajar** la cantidad por debajo de los rangos que hay | Se conservan los primeros de **izquierda a derecha** y se descartan los sobrantes de la derecha. |
| Subir la cantidad | No toca los rangos existentes. |
| Toque simple sobre la onda | Deselecciona **todos** los rangos; el doble toque (zoom) los restituye todos. |
| Pulsar REV | Cada rango se refleja sobre el centro del archivo (mismos fragmentos de señal) y el conjunto se reordena. |
| «Recorte» | Solo se habilita con **exactamente un** rango de largo suficiente (recortar conserva un único tramo). |

### Reproducción
Con el bucle de la ventana activo, el cursor recorre **solo los tramos seleccionados, de izquierda a derecha**: al llegar al final de
uno salta al inicio del siguiente y, tras el último, vuelve al primero. Con el bucle apagado, la selección no interviene (igual que antes).
Los tramos que no tocan nada de lo audible se descartan; si no queda ninguno, se usa el clip entero.

## 2. Arquitectura (una responsabilidad por archivo)

| Capa | Archivo | Responsabilidad |
|---|---|---|
| Motor (puro) | `engine/audio/PreviewLoopPlan.kt` | Tramos del proyecto a reproducir en orden y su confinamiento del cursor (`confine`). Invariantes: no vacío, ordenado, sin solaparse. `of(...)` normaliza (ordena y une). **Reemplaza** a `PreviewLoopRange.confine`, que se eliminó. |
| Motor | `engine/audio/AudioClipPreview.kt` | Sesión de vista previa: `selections: List<PreviewSourceSelection>`; `resolvedIn` traduce cada rango al proyecto y construye el plan (`ResolvedPreviewLoop.plan`). |
| ViewModel | `EditorViewModel.kt` | `setAudioClipPreviewSelections(...)` (estado de sesión) y el reloj `startPlaybackLoop`, que confina el cursor con el plan. |
| Math UI (puro) | `ui/AudioClipDetailSelectionMath.kt` | Reglas de **un** rango: arrastre acotado a un hueco (`detailSelectionFromDragIn`), borde agarrado, reflejo. |
| Math UI (puro) | `ui/AudioClipDetailSelectionSetMath.kt` | Reglas del **conjunto**: hueco libre, borde más cercano entre todos, orden, espejo, límite al bajar la cantidad, objetivo del recorte. |
| Math UI (puro) | `ui/AudioClipDetailSelectCountMath.kt` | Límites `x1 … x7`, paso de las flechas y arrastre vertical → cantidad. |
| Estado | `ui/AudioClipDetailSelectionState.kt` | Solo conserva `ranges` y `maxCount` y orquesta el gesto; delega las reglas en las funciones puras. |
| Dibujo | `ui/AudioClipDetailSelectionLayer.kt` | Solo dibuja todos los rangos (antes compartía archivo con el estado). |
| Vista | `ui/AudioClipDetailSelectButton.kt` | Botón «Seleccionar»: flechas + número arrastrable. No conoce rangos. |
| Vista | `ui/AudioClipDetailTile.kt` | Marco visual compartido de los botones de la cabecera (`DetailTileFrame`, `DetailActionTile`), extraído de la cabecera para no copiarlo. |
| Vista | `ui/AudioClipDetailHeader.kt` | Solo coloca los botones. |
| Vista | `ui/AudioClipDetailPanel.kt` / `…Overview.kt` | Conexión con el estado; la vista general pinta una banda por rango. |

Flujo de datos: `Botón → changeMaxCount → estado (recorta rangos) → commitSelection → onSelectionChange(List) → ViewModel → resolvedIn → PreviewLoopPlan → confine en cada tick`.

## 3. Decisiones de diseño
1. **Se migró el modelo a lista, sin dejar una API de un rango en paralelo.** `selection` → `selections`; `…Selection` → `…Selections`; no hay camino duplicado.
2. **`PreviewLoopPlan.confine` generaliza el bucle simple** y con un solo tramo da exactamente el resultado anterior (`inicio + (ms − inicio) mod largo`); las pruebas de `confine` del tramo único se movieron tal cual al plan. En un hueco entre tramos conserva el sobrepaso del cuadro dentro del siguiente (sin acumular desfase por salto).
3. **Un único arrastre para crear y ajustar:** ambos se reducen a «el rango arrastrado vive en el hueco que dejan los demás» (`detailFreeSpanAt`), lo que garantiza por construcción que no se solapan.
4. **Rendimiento:** `ranges` solo se lee en bloques de dibujo y en un `derivedStateOf` **booleano** (`canTrim`); arrastrar no recompone la ventana. El editor solo se entera al terminar el gesto.
5. **Recorte con varios rangos:** deshabilitado (no hay un tramo único al que recortar), en lugar de elegir uno arbitrariamente.

## 4. Cambios de comportamiento a tener presentes
1. Con el cupo lleno (por defecto `x1`), arrastrar lejos de los bordes **ya no sustituye** la selección: hay que tocar la onda para deseleccionar y volver a marcar. (Antes, con un solo rango, el arrastre lejano lo sustituía.)
2. Un toque simple deselecciona **todos** los rangos.

## 5. Límites conocidos (no incluidos a propósito)
- Un toque no deselecciona un rango suelto: solo todos a la vez.
- Dos rangos que se tocan exactamente en un punto comparten borde: se agarra el del rango anterior.
- Sin indicador visual de «cupo lleno» al intentar un rango de más (el arrastre simplemente no hace nada).

## 6. Pruebas
| Archivo | Cubre |
|---|---|
| `PreviewLoopPlanTest` (20) | Invariantes, `of`, `confine` con uno y varios tramos, secuencia tick a tick en orden y en bucle. |
| `AudioClipDetailSelectionSetMathTest` | Hueco libre, borde más cercano entre rangos, orden, espejo, límite, objetivo del recorte. |
| `AudioClipDetailSelectCountMathTest` | Límites, flechas, arrastre vertical, truncado, sin paso válido. |
| `AudioClipDetailSelectionStateTest` | Gesto completo con N rangos: cupo, orden, vecinos, bordes, bajar/subir cantidad, toque/doble toque, REV. |
| `AudioClipDetailSelectStructureTest` | Cableado: botón a la izquierda de Recorte, marco compartido, estado→editor→reloj, estado sin dibujo / capa sin estado. |
| `AudioClipDetailSelectionMathTest`, `AudioClipPreviewTest` | Siguen cubriendo un rango y la traducción al proyecto. |

## 7. Auditoría de la fase (revisión de ingeniería)

Alcance: todo lo implementado en esta fase y en la del cajón de módulos (`ModulesTarget`, `AudioModulesList`). Método: barrido
automático de símbolos sin uso, de imports, de balance de llaves y de nombres de prueba, más lectura de cada archivo tocado.

| Hallazgo | Resolución |
|---|---|
| **Código muerto:** `detailSelectionFromDrag` (arrastre sobre el archivo entero) quedó sin usos en producción tras el refactor a `detailSelectionFromDragIn`; solo lo usaban 7 pruebas. | Eliminada. Las pruebas usan la función viva con el hueco `0 … total`. |
| **Duplicado:** la fórmula «distancia de un toque al borde» estaba escrita en `detailSelectionEdgeHit` y de nuevo en `detailSelectionEdgeHitIn`. | Una sola función, `detailSelectionEdgeDistancePx`, usada por ambas. |
| **Duplicado:** la conversión `DetailSelection → PreviewSourceSelection` estaba escrita dos veces en el panel (avisar al editor y Recorte). | Una sola extensión privada en el panel, el único sitio que cruza esa frontera. |
| **Duplicado:** dos constantes de «deshabilitado» (0,4 en Recorte y 0,3 en las flechas de Seleccionar). | Una sola, `DETAIL_DISABLED_ALPHA`, junto al marco compartido. Las flechas en su límite pasan de 0,3 a 0,4. |
| **Resto:** el punto de partida del arrastre del número se inicializaba con `DETAIL_SELECT_DEFAULT_COUNT` (una constante ajena). | Se inicializa con la cantidad vigente. |
| **Comentarios desactualizados** que describían un solo rango (Recorte, encabezados del modelo y de la matemática). | Corregidos. |
| **Fallo de pruebas:** un nombre de prueba llevaba `:` (ilegal en la JVM) y rompía la compilación de tests. | Renombrado; barrido de todos los nombres del proyecto: ninguno más. |
| Comprobado sin hallazgo: imports sin usar (26 archivos), llaves/paréntesis, strings sin uso y paridad es/en, recomposición durante el arrastre (solo un `Boolean` derivado), una responsabilidad por archivo. | — |
| Deliberadamente NO cambiado: el cálculo de ids de carril en cada recomposición de `EditorScreen` (coste de microsegundos; optimizarlo añadiría código a esa función, ya al límite por el riesgo de `VerifyError`). | Sin cambio. |
