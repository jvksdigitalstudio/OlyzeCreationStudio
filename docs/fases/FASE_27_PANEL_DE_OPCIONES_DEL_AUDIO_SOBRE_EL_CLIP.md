# FASE 27 — Panel de opciones del clip de audio por encima del clip

**Origen:** reporte con capturas — el panel de opciones (íconos de volumen, recorte, bucle, etc.) que se despliega
junto a la cabecera del clip de audio se dibujaba **detrás** del clip en lugar de encima.
**Alcance:** solo el carril de audio (`AudioTrackRow`). Las filas de capas de imagen no cambian.

## Causa raíz
Dentro del `Box` del carril, Compose ordena el dibujado **primero por `zIndex` y, a igualdad, por orden de
declaración**. El panel (`RowSideActionsPanelHost`) era el último hijo, pero sin `zIndex` (0f), mientras que:
- el clip seleccionado (`AudioClipBlock`) usa `zIndex(1f)` (para que sus manijas de recorte/loop no queden tapadas),
- la selección de tiempo usa `zIndex(2f)`.

Estar al final del `Box` no bastaba: el clip seleccionado (1f) se pintaba sobre el panel (0f), que nace pegado al
borde izquierdo, justo donde empieza el clip.

## Corrección
| Archivo | Cambio |
|---|---|
| `ui/AudioTrackRow.kt` | Constante `AUDIO_ACTIONS_PANEL_Z = 3f` documentando la jerarquía de apilado del carril (clips 0f < clip seleccionado 1f < selección de tiempo 2f < panel 3f). El panel se invoca con `Modifier.zIndex(AUDIO_ACTIONS_PANEL_Z)`. |
| `ui/TimelineView.kt` — `RowSideActionsPanelHost` | Nuevo parámetro opcional `modifier: Modifier = Modifier` (antes fijo). Una sola implementación compartida; `TimelineRow` no pasa nada y su comportamiento queda idéntico. |

Se mantiene el absorbedor de toques del panel (`detectTapGestures {}`): al estar encima, los toques en los huecos
entre íconos no llegan al clip ni al carril. El `shadow` del panel también queda por encima del clip.

## Verificación
- Cambio de UI en Compose: **no compilable ni ejecutable aquí** (sin SDK de Android). Revisado contra todos los
  `zIndex` del archivo y contra el único otro llamador del host. **CI debe confirmar `assembleDebug`.**

## Validación en dispositivo
1. Seleccionar el clip de audio y abrir su panel de opciones: el panel debe verse completo, **sobre** la cabecera
   del clip, con su sombra.
2. Tocar un ícono del panel y el hueco entre íconos: no debe seleccionar/mover el clip de abajo.
3. Con selección de tiempo activa y panel abierto: el panel sigue encima.
4. Panel de una capa de imagen: sin cambios.
