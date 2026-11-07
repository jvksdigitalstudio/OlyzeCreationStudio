# Fase — Arrastrar un clip de audio a cualquier carril de audio

**Alcance:** un clip de audio se puede agarrar por su cabecera y soltar **en su carril o en cualquier otro carril de AUDIO**, en cualquier instante del timeline. Solo audio: un clip nunca cae en una capa de imagen.

Quedan fuera: arrastrar entre carriles con varios dedos o varios clips a la vez (la selección es de un clip), y mantener un carril de audio **vacío** tras mover su único clip (ver §3).

## 1. Referencia estudiada

Se revisaron los manuales oficiales de Image-Line (FL Studio Mobile — *Playlist* y *Editors* — y FL Studio — *The Playlist*):

| Qué dice el manual | Cómo se aplicó |
|---|---|
| «You can put any Clip type on any Playlist track, and even overlay Clips of any type» | Cualquier carril de audio acepta el clip y los solapes están permitidos: el clip se superpone, **no reemplaza ni descarta nada** (igual que pegar). |
| *Snap* «determines how Clips will move» respecto a la rejilla | El destino se imanta con las mismas reglas que ya tenía el arrastre horizontal (cursor, bordes de otros clips, 0, final y rejilla del carril). |
| «Left-click-and-drag to reposition the clip before releasing it» | Un solo gesto continuo; el clip se confirma al **soltar**. |

El manual de FL Studio Mobile **no documenta de forma literal** el arrastre de un clip a otra pista; el comportamiento de referencia (el clip pasa a la pista donde cae, **toma el color de esa pista** y la pista destino queda seleccionada) se tomó de la app y de las capturas aportadas.

## 2. Reglas

- **Destino:** el carril de audio cuya franja vertical contiene el dedo; el inicio sale de la X del dedo menos el punto donde se agarró el clip (el clip no «salta» al agarrarlo), acotado para que **quepa entero** en el proyecto e imantado.
- **Fuera de un carril de audio** (sobre una capa de imagen, un hueco o fuera de la lista) se conserva el **último destino válido**: soltar ahí nunca pierde el clip.
- **Mezcla:** al cambiar de carril el clip hereda volumen, balance y silencio del **carril destino** (`withClipInheritingMaster`), igual que un clip pegado o duplicado: esos controles son del carril, como el fader y el mute de una pista. Recorte, fundidos, bucle, inversión y nombre viajan con el clip.
- **Vista previa exacta:** mientras se arrastra se dibuja el clip **en el lugar donde quedará** (carril e instante ya imantados), con los colores del carril destino. La vista previa y el movimiento que se aplica salen de la misma función (`clipDropStartMs`).
- **Un evento por gesto:** `EditorViewModel.moveAudioClip` se llama una sola vez, al soltar. Es **un paso de deshacer**; si el clip ya estaba exactamente ahí no hace nada ni toca el historial.
- **Selección:** al soltar quedan seleccionados el carril destino y el clip movido.
- **Lista larga:** si el dedo se acerca al borde superior/inferior de la lista, esta se desplaza sola (proporcional a la cercanía, hasta 12 dp por cuadro) para llegar a carriles fuera de pantalla.

## 3. Decisión: el carril de origen sin clips desaparece

En FL Studio Mobile la pista de origen queda vacía y visible. En este proyecto un carril de audio sin clips **no existe** (`withTrack` lo quita; no se muestra ni se guarda): es la misma regla que ya aplican *Cortar* y *Eliminar* sobre el último clip de un carril. Mantenerlo exigiría soportar carriles vacíos en guardado, nombre, «+Clip», borrado y deshacer. Se eligió respetar la regla vigente; si se quiere el comportamiento de FL, es una fase aparte.

## 4. Dónde está cada cosa (responsabilidades separadas)

| Capa | Archivo | Responsabilidad |
|---|---|---|
| Reglas de dominio (puras) | `engine/audio/AudioClipMove.kt` | `maxClipStartMs`, `clipDropStartMs` (núcleo con primitivos + envoltorio de carril), `withClipMoved` (lista de carriles resultante o `null`). |
| Geometría (pura) | `ui/AudioClipDropTarget.kt` | `resolveClipDropTarget` (dedo en ventana → carril + instante) y `edgeAutoScrollDeltaPx`. |
| Estado del gesto | `ui/AudioClipDragState.kt` | `AudioClipDragHost` (lo único que ve el carril), `AudioClipDragSession`, `AudioClipDragState`. Recibe por inyección *cómo resolver* y *cómo confirmar*: no decide ni aplica nada. |
| Vista previa | `ui/AudioClipDragOverlay.kt` | Dibuja el clip en su destino sobre la lista; se recompone solo al empezar/terminar o al cambiar de carril, el movimiento es de layout. |
| Dibujo del clip | `ui/AudioClipSurface.kt` | Cabecera + forma de onda + rampas de fade, **una sola implementación** que usan el carril y la vista previa (antes vivía dentro de `AudioClipBlock`). |
| Detección del gesto | `ui/AudioTrackRow.kt` | La cabecera del clip detecta el arrastre libre y lo reporta en coordenadas de **ventana** al `AudioClipDragHost`. No conoce a los demás carriles. |
| Orquestación | `ui/TimelineView.kt` | Posee el estado, resuelve con los límites de carril ya medidos para «+Clip» (`AddClipState.boundsOf`), aloja la vista previa y el auto-scroll. |
| Aplicación | `ui/EditorScreen.kt`, `EditorViewModel.moveAudioClip` | Mueve (con deshacer, autosave y ampliación del timeline si hiciera falta) y fija la selección. |

## 5. Código reemplazado (sin parches ni duplicados)

- `EditorViewModel.setAudioTimelineStart` → **eliminada**; la sustituye `moveAudioClip` (cubre mover dentro del mismo carril y entre carriles, y ahora **sí** es deshacible).
- `AudioTrackRow`: se quitó `detectHorizontalDragGestures` del arrastre del clip, el estado local `moveDragPx`/`isMoving`, el imán duplicado `snapStart` y el parámetro `onTimelineStartChange` (→ `clipDrag`).
- `TimelineView.onAudioTimelineStartChange` → `onAudioClipMove(clipId, destTrackId, startMs)`.
- El dibujo del clip y `ClipFadeOverlay` se movieron a `AudioClipSurface.kt`.

## 6. Pruebas

- `AudioClipMoveTest` (JUnit puro): tope de inicio, acotado, imán a bordes/cursor/rejilla, que el imán nunca saque el clip del proyecto.
- `AudioClipDropTargetTest` (JUnit puro): carril por franja vertical, inicio desde el punto de agarre, bordes, carril sin medir/recortado, auto-scroll.
- `withClipMoved` requiere un `AudioClip` (que lleva `android.net.Uri`, inexistente en JUnit puro), igual que el resto de reglas de carril: se verificó con un arnés fuera del proyecto contra el código real (mismo carril, entre carriles, mezcla heredada, carril de origen que desaparece, solape, ids inexistentes). Queda como candidata a prueba instrumentada.

## 7. Cómo probarlo en el dispositivo

1. Dos o tres carriles de audio con clips. Mantener el dedo sobre la **cabecera** de un clip y arrastrar a otro carril: el clip aparece, con el color del destino, donde caerá; al soltar queda ahí y el carril destino se selecciona.
2. Soltar sobre una capa de imagen: el clip queda en el último carril válido.
3. Con la rejilla activada, comprobar que el inicio se pega a la línea; con el cursor cerca, que se pega al cursor.
4. Con más carriles que alto de pantalla, arrastrar hacia el borde inferior/superior: la lista se desplaza.
5. **Deshacer** devuelve el clip a su carril e instante originales (y recrea el carril de origen si había desaparecido).
6. Mover el único clip de un carril: el carril de origen desaparece (ver §3).

## 8. Auditoría posterior (endurecimiento del gesto)

Hallazgos corregidos tras revisar el código real:

| Hallazgo | Riesgo | Corrección |
|---|---|---|
| Si el bloque del clip origen se desmontaba a mitad del gesto (clip borrado, deshacer, carril eliminado), su corrutina se cancelaba **sin** llamar a `onDragCancel` | Sesión de arrastre eterna: overlay fantasma y auto-scroll en bucle | `DisposableEffect` en `AudioClipBlock` que cancela la sesión si ese clip era el que se arrastraba (`AudioTrackRow.kt`). |
| Un segundo dedo sobre otra cabecera pisaba la sesión en curso y su `onDragEnd` confirmaba el destino equivocado | Movimiento de un clip que no se arrastraba | `onDragStart` se ignora si ya hay sesión; `onDrag`/`onDragEnd`/`onDragCancel` reciben el `clipId` y solo actúa el dueño del gesto (`AudioClipDragState.kt`). |
| El bloque original se ocultaba aunque no hubiera destino resuelto (el overlay no dibuja sin destino) | Clip invisible durante el gesto | `isDragSource` exige además `target != null`. |
| Comentario de `TimelineView` afirmaba «solo puede haber UN carril» | Documentación contradictoria con el código | Comentario corregido. |

Pendiente de verificar en dispositivo: al soltar, el clip original reaparece en cuanto se cierra la sesión y el nuevo estado del proyecto llega por `StateFlow`; si se ve un destello en el sitio anterior, hay que mantener oculto el origen hasta recibir la lista actualizada (cuidando el caso «soltado donde estaba», que no cambia el estado).
Limitación de pruebas: `AudioClipDragState` depende de `AudioClip` (lleva `android.net.Uri`), por lo que no es testeable con JUnit puro; candidata a prueba instrumentada/Robolectric.

## 9. Fluidez del gesto (retardo al arrastrar)

| Causa del retardo | Corrección |
|---|---|
| Al pasar el umbral de arrastre se seleccionaba el clip: cambiaba estado de `EditorScreen` y se recomponía el timeline entero justo cuando el dedo empezaba a moverse | El arrastre ya no selecciona al empezar (`AudioTrackRow`). La selección de carril y clip, y la limpieza de la capa de imagen elegida, se fijan al soltar (`onAudioClipMove`). |
| La vista previa se movía cambiando su posición en layout: re-layout y re-dibujo de la onda en cada movimiento | `AudioClipDragOverlay` traslada una capa gráfica (`graphicsLayer`); layout solo al cambiar de tamaño (otro carril). |
| El auto-scroll recalculaba el destino en cada cuadro aunque nada cambiara | Solo se recalcula el cuadro siguiente a un desplazamiento real de la lista. |
| Al soltar, la vista previa desaparecía antes de que llegara la lista movida (destello del clip en su sitio viejo) | Fase `settling` en `AudioClipDragState`: el overlay se mantiene hasta que `audioTracks` cambia (máx. 4 cuadros si el movimiento no cambió nada) o el bloque origen se desmonta. |

Pendiente de medir en dispositivo (no se pudo compilar ni ejecutar aquí): sensación al empezar el arrastre. El único retardo que queda es el umbral táctil del sistema (`touchSlop`, 8 dp) antes de que el gesto arranque; reducirlo aumentaría los arrastres accidentales al tocar, por eso no se tocó.
