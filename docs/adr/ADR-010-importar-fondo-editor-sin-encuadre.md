# ADR-010 — "Importar fondo" del editor saltaba el encuadre obligatorio (segunda puerta de entrada al bug del verde chroma-key)

**Estado:** Decidido e implementado.
**Referencia:** ADR-006 (encuadre obligatorio para "Elige un fondo" en
"Nuevo proyecto"), ADR-007 (compensación de profundidad en `LayerDrawer`),
auditoría de la guía de encuadre/posicionamiento de fondo pedida el 13 de
septiembre de 2026 (capturas del cliente sobre el proyecto "Cr7": verde
chroma-key asomando alrededor del fondo recién importado, cubierto recién
tras acercar manualmente la imagen con el gesto de pellizco).

## Contexto

ADR-006 cerró el bug del verde chroma-key asomando alrededor del fondo
para **un** camino de entrada a `EditorViewModel.importAsBackground()`: el
selector "Elige un fondo" del diálogo **"Nuevo proyecto"**
(`CreateProjectDialog`, en `ProjectsScreen.kt`). Ese diálogo intercepta el
`Uri` elegido con Imagen/Cámara, lo pasa por `BackgroundAdjustDialog`
(encuadre obligatorio contra el `CanvasSpec` del proyecto) y solo llama a
`importAsBackground` con el resultado ya recortado/escalado al 100% del
lienzo.

`importAsBackground` tiene una **segunda** puerta de entrada, separada de
esa: el botón **"Importar fondo" del editor**, disponible en cualquier
momento sobre un proyecto ya creado y abierto (cableado en
`MainActivity.kt`, consumido por `EditorScreen` vía
`onImportBackgroundClick`). Antes de este ADR, ese camino llamaba a
`viewModel.importAsBackground(uri)` **directo** con el `Uri` crudo que
devolvía el picker de archivos (`pickBackgroundLauncher`) — exactamente el
mismo defecto que ADR-006 documentó y arregló para el otro camino, nunca
cerrado acá. `LayerRepository.importAsLayers` sigue tomando
`widthPx`/`heightPx` directo del bitmap decodificado, sin relación alguna
con el `CanvasSpec` del proyecto — así que cualquier imagen con una
proporción distinta a la del lienzo entra como capa de fondo sin cubrirlo
al 100%, dejando ver el `ChromaKeyGreen` de zona vacía del canvas
alrededor. Este es el bug de las capturas del cliente sobre "Cr7": el
usuario terminó tapando el verde a mano, acercando el fondo con el gesto
de pellizco hasta que la imagen (ya desproporcionada respecto al lienzo)
alcanzó a cubrirlo — un workaround manual, no el comportamiento esperado
de "Importar fondo".

Esto es independiente del fix de ADR-007 (compensación de profundidad por
perspectiva en `LayerDrawer.drawLayer`): ese fix garantiza que un bitmap
que YA cubre el 100% del lienzo por construcción no se vea encogido por la
cámara de perspectiva. Acá el bitmap nunca cubría el 100% del lienzo desde
el origen — el problema está un paso antes, en la importación, no en el
dibujo 3D de una capa ya bien construida.

## Decisión

Aplicar el mismo patrón que ya usa `CreateProjectDialog` para este segundo
camino: el `Uri` que entrega `pickBackgroundLauncher` ya no llega directo
a `importAsBackground`. Se guarda como pendiente
(`pendingLiveBackgroundUriToAdjust`, en `MainActivity.kt`) y se abre
`BackgroundAdjustDialog` contra `viewModel.uiState.value.canvas` — el
`CanvasSpec` real del proyecto ya abierto (fijo desde su creación, no
hace falta observarlo con `collectAsState()`). Recién al confirmar ese
diálogo, el bitmap ya ajustado se persiste a un archivo local
(`LayerRepository.saveBitmapAsLocalUri`, mismo mecanismo que ya usa
"Nuevo proyecto") y ESE `Uri` local es el que finalmente llega a
`importAsBackground`.

No se comparte estado de Compose entre los dos diálogos de ajuste (el de
`CreateProjectDialog` y este nuevo de `MainActivity`): son dos ciclos de
vida distintos — uno ocurre antes de que exista un proyecto persistido,
el otro sobre un proyecto ya abierto con su propio `EditorViewModel` — así
que cada uno mantiene su propio par `pending*UriToAdjust` /
`show*AdjustDialog` (o, en este caso, un único estado nullable que cumple
ambos roles), sin acoplar un archivo a la existencia interna del otro.

## Alcance del fix

Un solo punto de cambio: `MainActivity.kt`, el lambda de
`onImportBackgroundClick` y el bloque nuevo que renderiza
`BackgroundAdjustDialog` junto al resto del wiring de `EditorScreen`. No
se tocó:

- `BackgroundAdjustDialog.kt` / `ImageFitDialog.kt` / `CoverAdjustDialog.kt`
  — ya correctos desde ADR-006, reutilizados tal cual.
- `EditorViewModel.importAsBackground()` — sigue recibiendo un `Uri` único
  y no necesita saber de dónde vino ni si pasó por un ajuste de encuadre.
- El flujo de "Nuevo proyecto" (`CreateProjectDialog`,
  `pendingProjectBackgroundUri`) — ya funcionaba bien, intacto.
- `LayerDrawer.kt` / ADR-007 — la compensación de profundidad sigue
  siendo necesaria y correcta para cualquier capa con `parallaxFactor <
  1`, independientemente de este fix.

## Fuera de alcance (sin cambios, gap ya aceptado desde ADR-006)

**"Reemplazar imagen" sobre la capa de fondo** (`onReplaceImageClick`,
doble-tap para sustituir la imagen de una capa ya existente) sigue sin
forzar ningún encuadre — mismo comportamiento que para cualquier otra
capa (una foto normal importada como capa intermedia tampoco está
obligada a cubrir el 100% del lienzo; el usuario la encuadra a mano con
los gestos normales de arrastre/pellizco/manijas). ADR-006 ya documentó
esto como fuera de alcance ("cualquier otro uso existente... reemplazar
imagen") para la capa de fondo específicamente creada a través de
`importAsBackground`; este ADR no cambia ese criterio. Si en el futuro se
decide que reemplazar la imagen de la capa de fondo en particular (no de
cualquier capa) también debería forzar cobertura del 100% del lienzo,
hace falta una decisión de producto explícita — hoy `replaceLayerImage`
no distingue "esta capa es la de fondo" de ninguna otra.

## Verificación pendiente en dispositivo real

Mismo estado que ADR-007/ADR-008/ADR-009: cambio hecho sobre el código
fuente sin Android SDK/Gradle disponible en esta sesión. Antes de cerrar:

1. Compilar (`./gradlew assembleDebug`) y confirmar que no rompe ningún
   test existente.
2. Abrir un proyecto ya creado, tocar "Importar fondo", elegir una imagen
   con una proporción claramente distinta a la del lienzo del proyecto, y
   confirmar que `BackgroundAdjustDialog` se abre ANTES de que la imagen
   se vea en el canvas — no debe aparecer ningún fondo sin ajustar en
   ningún momento intermedio.
3. Confirmar que, tras aceptar el encuadre, el fondo cubre el 100% del
   lienzo en reposo (frame 0, sin animación de cámara corriendo) sin
   ningún verde `ChromaKeyGreen` asomando en ningún borde — reproducir
   exactamente el caso de las capturas del cliente ("Cr7").
4. Confirmar que tocar "Cancelar" en `BackgroundAdjustDialog` en este
   camino (a diferencia de "Nuevo proyecto", acá YA existe un proyecto
   con o sin fondo previo) deja el proyecto exactamente como estaba antes
   de tocar "Importar fondo" — sin capa nueva, sin archivo local huérfano
   en disco.
