# Fase — Nombres del carril de audio y de cada clip

**Alcance:** el audio pasa a tener **dos niveles de nombre independientes**, como las capas de imagen tienen el suyo:

| Nivel | Campo | Se cambia con | Se ve en |
|---|---|---|---|
| **Carril** (la «capa» de audio) | `AudioTrack.name` | El **lápiz** del panel de acciones de la fila | La propia fila, abajo a la izquierda (mismo estilo que el nombre de una capa de imagen) |
| **Clip** (uno concreto) | `AudioClip.customName` | **Mantener presionado** el clip → **Renombrar** | La cabecera del clip, la ventana ampliada y el aviso de borrado |

Quedan fuera: el nombre del **archivo fuente** (`AudioClip.displayName`), que sigue sin poder editarse (ver §2), y el desbordamiento del
nombre del clip hacia la derecha cuando el clip es más corto que su texto (la cabecera sigue recortando con «…»; queda como fase aparte).

## 1. Problema que se corrigió

- El carril **no tenía nombre propio**: el lápiz de la fila renombraba un *clip* (el seleccionado o el que estaba bajo el cabezal)
  mientras el diálogo, titulado «Renombrar capa», se rellenaba con el nombre del **primer** clip. Con varios clips podía mostrar un
  nombre y cambiar otro.
- El carril de audio **no mostraba ningún nombre** en la fila (las capas de imagen sí): solo el ícono de nota y, dentro del clip, un
  «Test 0…» recortado.
- El menú del clip **no tenía «Renombrar»**.

## 2. Decisión de diseño: el nombre visible NO es el nombre del archivo

`displayName` hace de nombre de archivo: `ProjectStorage` deriva de él la extensión de la copia local del audio
(`safeAudioExtension`, con respaldo `m4a`). Si renombrar editara `displayName`, quitar la extensión dejaría un `.wav` guardado como
`.m4a`. Por eso el nombre que el usuario ve y cambia es un campo **aparte** y opcional; `displayName` no se toca nunca.

Cuando no hay nombre personalizado (`null`) se muestra el nombre del archivo, de modo que **los proyectos existentes se ven exactamente
igual** y no hace falta migración (los campos nuevos son opcionales en el `project.json`).

## 3. Reglas (código en `engine/audio/AudioNaming.kt`, todo puro y probado)

- **Normalización** (`normalizeAudioName`): los caracteres de control pasan a espacio, se colapsan los espacios, se recortan los
  extremos y se acota a `AUDIO_NAME_MAX_LENGTH = 60` **code points** (nunca parte un emoji). Sin contenido → `null` («sin personalizar»).
- **Nombre visible del clip:** `customName` o, si no hay, `displayName`.
- **Nombre visible del carril:** `AudioTrack.name` o, si no hay, el **archivo** del primer clip (no el nombre personalizado de ese clip:
  renombrar un clip no debe cambiar el carril), o «Audio» como último recurso.
- **Pedir el nombre de fábrica = no personalizar** (`resolveCustomAudioName`): abrir el diálogo y pulsar «Guardar» sin editar no crea un
  nombre «propio» idéntico al del archivo ni un paso de deshacer vacío.
- **Restablecer:** el diálogo ofrece «Restablecer» (solo si hay un nombre propio) y vuelve al nombre del archivo.
- Renombrar un nivel **jamás** modifica el otro. Duplicar y dividir un clip conservan su nombre (`copy`), y unir conserva el del clip izquierdo.

## 4. Dónde está cada cosa

| Capa | Cambio |
|---|---|
| Modelo (`engine/audio/AudioClip.kt`) | `AudioClip.customName: String? = null`, `AudioTrack.name: String? = null`. |
| Reglas (`engine/audio/AudioNaming.kt`, nuevo) | Normalización, etiquetas, `withCustomName`, `withName`. |
| Persistencia (`data/ProjectModels.kt`, `data/ProjectStorage.kt`) | `AudioClipData.customName`, `AudioTrackData.name`; snapshots de guardado; lectura y escritura normalizadas; **saneo en `sanitizeProjectData`** (un `project.json` corrupto o un `.olycs` importado no puede traer nombres sin acotar ni con saltos de línea). |
| Deshacer/rehacer (`EditorViewModel`) | Los nombres entran en `AudioClipContentState`/`AudioTrackContentState`; sin esto, deshacer cualquier otra edición habría **borrado** los nombres. |
| ViewModel | `renameAudioClip(clipId, newName)` y `renameAudioTrack(trackId, newName)` (reemplazan a `renameAudio`); cada uno es un paso de historial y no hace nada si el nombre no cambia. |
| API pública (`AudioClipSnapshot`, `AudioApiImpl`) | `customName` (con valor por defecto, compatible con los consumidores actuales) y `label`. |
| UI (`AudioTrackRow`, `LayerDialogs`, `TimelineView`, `EditorScreen`) | Nombre del carril dibujado en la fila; lápiz → carril; «Renombrar» en el menú del clip; `RenameLayerDialog` gana `title`, `maxLength` y `onReset`; el panel de detalle, el aviso de borrado de clip y de carril usan el nombre visible. |

**Nombre del carril dentro del carril.** Se dibuja abajo a la izquierda, pero solo en el tramo **libre** anterior al primer clip (mínimo
48 dp): el cuerpo de un clip es semitransparente y un texto debajo se transparentaría sobre la forma de onda. Con un clip en 0:00 no se
dibuja: ahí manda el nombre del propio clip. Es solo dibujo (sin gestos), no compite con los toques del carril.

## 5. Tests

- `AudioNamingTest` — normalización (controles, espacios, tope, emojis, idempotencia), etiquetas, regla de «nombre de fábrica», `withName`
  (identidad, restablecer, no toca otros datos).
- `ProjectDataAudioNamesTest` — vuelta de serialización, proyecto anterior sin nombres, saneo del `project.json`.

## 6. Verificación pendiente en dispositivo

Esta fase se escribió sin poder compilar ni ejecutar la app. Antes de darla por cerrada: compilar, correr los tests y probar a mano:
lápiz de la fila (nombre del carril visible en la fila, «Restablecer»), mantener presionado un clip → «Renombrar» (solo ese clip), deshacer/rehacer
de ambos, cerrar y reabrir el proyecto, y un proyecto guardado antes de esta fase.
