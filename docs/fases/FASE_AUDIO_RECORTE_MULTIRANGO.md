# Fase — Recorte con varios rangos seleccionados (2026-10-18)

## Problema
Con **Seleccionar x2 … x7** se pueden marcar varios tramos sobre la onda, pero **Recorte** quedaba apagado en cuanto había más de uno
(`detailTrimSelection` exigía `singleOrNull()`). Era una limitación de diseño, no un requisito: si se puede marcar varios tramos,
lo lógico es poder recortar con ellos.

## Comportamiento
| Rangos seleccionados | Resultado de **Recorte** |
|---|---|
| 1 | Igual que antes: el clip se acorta al rango. |
| 2 … 7 | El clip se **reparte en un clip por rango**. Cada uno queda **en su posición original del proyecto**; lo no seleccionado queda como hueco (no es un *ripple*: nada se corre). |

Reglas (todas en la regla pura, probadas con JUnit):
- Cada tramo conserva el invariante de siempre: **cada instante suena exactamente donde sonaba** (`proyecto = inicio + (archivo − recorte)`).
- Ningún tramo sale del espacio que ocupaba el clip original ⇒ no puede invadir a un vecino ni solaparse con otro tramo.
- Un rango menor a `MIN_AUDIO_CLIP_LENGTH_MS` (100 ms) o que no toca lo audible **se descarta sin invalidar a los demás**. Si no queda ninguno, no hay recorte (ni se toca el historial).
- Fundidos: describen los extremos del clip **original**. El fade-in solo lo conserva el **primer** tramo y solo si su inicio no cambió; el fade-out, el **último** y solo si su final no cambió. Los intermedios no llevan fundidos.
- Con loop, solo se recorta el primer pase audible (sin cambios). Con Reversa, selección y `trimStartMs` comparten eje (sin cambios).
- Rangos que se tocan tras el redondeo a ms se fusionan (la ventana nunca los produce; defensa para no duplicar sonido).
- El primer tramo **conserva el id** del clip original; los demás reciben UUID nuevo. Heredan volumen, pan, nombre, reversa, etc.
- **Un solo paso de deshacer**. Tras aplicarse, la ventana descarta la selección (igual que antes).

## Diseño (sin caminos paralelos: se reemplazó la API de un rango)
| Capa | Antes | Ahora |
|---|---|---|
| Regla pura `engine/audio/AudioClipTrimToSelection.kt` | `trimClipToSelection` (1 rango) | `trimClipToSelections` (N rangos → `List<ClipTrim>`). `trimClipToSelection` queda como envoltura de un rango. |
| Matemática de la ventana `ui/AudioClipDetailSelectionSetMath.kt` | `detailTrimSelection` → un rango o `null` | `detailTrimSelections` → todos los rangos ≥ mínimo |
| `ui/AudioClipDetailPanel.kt` | `trimSelectionNow()` | `trimSelectionsNow()`; `canTrim` = hay al menos uno |
| `AudioClipEditActions` (`AudioTrackRow.kt`) | `onTrimToSelection(clipId, selection)` | `onTrimToSelections(clipId, selections)` |
| `EditorViewModel` | `trimAudioClipToSelection` | `trimAudioClipToSelections` (único punto de escritura; mismo patrón que `splitAudioClipAtPlayhead`) |
| `ui/AudioClipDetailHeader.kt` | KDoc «un único rango» | KDoc actualizado |

## Pruebas
- `AudioClipTrimToSelectionTest`: +8 casos (orden por archivo, posición original por tramo, sin solapes ni salirse del clip, fundidos por extremo, descarte parcial, sin rangos válidos, orden de entrada y fusión, plural = singular con un rango).
- `AudioClipDetailSelectionSetMathTest`: `detailTrimSelections` (uno, varios, descarte de cortos, vacío).
- `ClipTrimPlacementStructureTest`: cableado actualizado + prueba de que no quedan las APIs de un solo rango.

## Verificación pendiente (sin SDK/Gradle en esta sesión)
1. `./gradlew assembleDebug testDebugUnitTest`.
2. En dispositivo (pantalla «Cr7»): Seleccionar x3, marcar 3 tramos → **Recorte** habilitado → quedan 3 clips en sus posiciones; Deshacer vuelve al clip único.
3. Con Reversa activa, con un rango muy corto entre los marcados, y con un clip vecino cercano.

## Ampliación — la ventana muestra los otros trozos y Recorte solo se habilita si aplica
Al recortar con varios rangos, la ventana sigue atada al **primer trozo** (conserva el id). Su regla oscurecía todo lo que quedaba fuera de
ese clip, así que los otros trozos —que siguen sonando— aparecían como "no usados".

- **Mapa de uso del archivo** (`ui/AudioClipDetailUsageMath.kt`, puro y probado): tres niveles bajo la regla — tramo del clip abierto **sin sombra**;
  tramos de **otros clips del mismo carril sobre el mismo archivo y misma Reversa** (`AudioTrack.detailSiblingSpans`) con **sombra suave** y barra tenue;
  lo que **nadie usa** con la sombra fuerte de siempre. Sin hermanos se ve exactamente como antes.
- La ventana sigue editando UN clip: para editar otro trozo se abre ese clip (doble toque en el timeline).
- **Defecto latente corregido:** Recorte se habilitaba por el largo de la selección, aunque cayera sobre la parte de otro trozo (y entonces no hacía nada).
  Ahora `detailTrimSelections` mide la intersección con lo que suena en el clip abierto (misma medida que `trimClipToSelections`), y `canTrim` se rehace
  cuando cambia el tramo del clip (antes `derivedStateOf` solo dependía de la selección).

## Cambio de texto — «Cargar» → «Reemplazar»
El botón de la izquierda de la cabecera reemplaza el audio del clip por otro archivo; el nombre ahora lo dice (ES «Reemplazar», EN «Replace»).
Recursos renombrados `audio_detail_load[_desc]` → `audio_detail_replace[_desc]` y `DetailLoadButton` → `DetailReplaceButton`; el ícono no cambia.

