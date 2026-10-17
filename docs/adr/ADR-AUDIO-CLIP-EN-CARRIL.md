# ADR — Cargar clips de audio desde el carril (mecánica FL Studio Mobile)

**Fecha:** 2026-10-09 · **Estado:** Aplicado · **Módulos:** `AudioTrackRow`, `AudioAddClipButton`, `EditorScreen`, `MainActivity`, `EditorViewModel`, `engine/audio`

## Decisión
El "+" de la playlist crea **capas nuevas**; agregar otro clip a una capa de audio existente se hace **desde su carril**:

| Gesto en espacio vacío del carril | Resultado |
|---|---|
| Un toque | Muestra el botón "+Clip" (ícono `ic_add_audio_clip`) en el punto tocado, con imán a cursor/bordes/rejilla. Al pulsarlo abre el selector y el clip arranca en ese punto. |
| Doble toque | Abre el selector directo; el clip arranca en el **cursor** (el usuario lo ubica antes). |
| Mantener presionado | Menú existente (pegar / seleccionar rango / rejilla), sin cambios. |

"+" → "Audio" crea una **capa de audio nueva** cada vez (primer clip en el cursor); ver
`ADR-AUDIO-MULTIPLES-CAPAS.md`. Los gestos de arriba cargan siempre en la capa donde se tocó.

## Diseño
- `importAudio(context, uri, desiredStartMs, targetTrackId)`: la UI decide el punto y el carril destino (`null` = capa
  nueva) y la closure de `MainActivity` los conserva mientras el selector está abierto.
- `AudioClipEditActions.onImportClipAt(startMs)`: única acción de la UI hacia el selector desde el carril; `EditorScreen`
  la liga al id de cada carril.
- Reglas puras testeadas (`AudioLaneTapPlacementTest`, `AudioAddClipButtonTest`): `isTimeOnAnyClip`, `audioSnapTargetsMs`,
  `snapNewClipStartMs`, `addClipButtonLeftPx`, `laneTimeMsAt`. `audioSnapTargetsMs` reemplaza dos listas idénticas
  que estaban inline (arrastre de clips y selección de tiempo).
- `pointerInput` del carril lee cursor/rejilla/duración vía `rememberUpdatedState` (el bloque no se relanza con ellos).
- "+Clip" se retira al cambiar los clips, seleccionar un clip, deseleccionar la capa o abrir el menú.

## Toque simple vs. doble toque (decisión vigente)
Respuesta inmediata: el toque simple se entrega al soltar el dedo (`detectImmediateTapGestures`, `ui/ImmediateTapGestures.kt`,
regla pura `isDoubleTap` testeada) y "+Clip" aparece AL INSTANTE con un fundido/escala de 110 ms. Un doble toque abre el
selector en el cursor y retira el botón.

Historia y por qué: `detectTapGestures` con `onDoubleTap` retrasa el toque simple ~300 ms (espera un segundo toque posible).
Se probó esa variante para que el doble toque nunca mostrara el botón, pero el retardo de ~0,3 s en el toque simple se sintió
lento (inaceptable para un editor). El primer toque de un doble toque es indistinguible de un toque simple, así que mostrar el
botón al instante implica que un doble toque lo insinúe un instante; el fundido corto lo reduce a casi nada.

El botón no captura toques durante la ventana del doble toque (`doubleTapTimeoutMillis` desde que aparece): el segundo toque
cae sobre el propio botón y debe llegar al carril para abrir el selector en el cursor. Pasada la ventana responde normal.

## Reubicar "+Clip" arrastrándolo (solo entre carriles de audio)
Mantener presionado el botón "+Clip" y arrastrarlo lo reubica; al soltar queda fijo y un toque posterior abre el selector
(el arrastre nunca abre el selector). Puede recorrer su carril y pasar a **cualquier otro carril de audio**; sobre capas de
imagen u otras filas se queda en el último punto válido. No se ubica sobre un clip y se pega con el mismo imán que el toque
(cursor, bordes de clips, rejilla). Al soltar, el carril donde quedó pasa a ser el seleccionado.

Diseño:
- El botón vive en una capa superpuesta de `TimelineView` (recortada a la lista), no en la fila: un mismo gesto sobrevive al
  cambio de carril. Un solo punto a la vez (`AddClipHandle`: carril + ms).
- Cada `AudioTrackRow` reporta los límites de su carril en ventana (`onLaneBoundsChange`) y solo propone/retira el punto
  (`onAddClipAnchorChange`); solo el carril dueño retira el punto ante cambios de clips/selección.
- El botón informa por dónde va el dedo y `TimelineView` resuelve el carril (reglas puras `addClipLaneIndexAtY`,
  `laneLocalX`, testeadas en `AudioAddClipButtonTest`). El punto se retira si el carril desaparece o se reordenan filas.
- Sensación del arrastre (auditoría de latencia): el botón sigue al dedo 1:1 (antes se dibujaba pegado al imán y "saltaba");
  umbral de arranque de 3 dp con detección propia (el de `detectDragGestures`, ~8 dp, hacía esperar y luego saltar); sin ripple al
  presionar; una marca fina indica el destino con imán; al soltar se asienta 130 ms en el punto válido y recién entonces se
  selecciona el carril (seleccionar recompone el timeline y trababa la animación).
- Rendimiento: el estado (`AddClipState`) no se lee al componer `TimelineView`; el botón se posiciona en la fase de layout
  (`offset { }`) y se anima en la capa gráfica, así que arrastrar no recompone el timeline. Las acciones por carril se piden
  una vez por arrastre.
- Límites conocidos: el arrastre no hace autoscroll vertical; ambos carriles deben estar visibles en pantalla.

## Lápiz de renombrar fuera de la fila de audio
Se quitó el botón de lápiz que había a la derecha del carril: "Renombrar audio" ya existe en el panel de opciones de la capa
(`ic_layer_rename`), así que era un atajo duplicado. Efecto colateral correcto: el carril ahora ocupa todo el ancho
`trackWidthPx` (antes quedaba 32 dp más angosto que la escala de tiempo, que no reservaba ese espacio), de modo que el final
del proyecto y el cursor coinciden con el borde del carril.

## Confirmación al eliminar un clip
"Eliminar" en el menú del clip ya no borra directo: marca el clip (`audioClipPendingDelete` en `EditorScreen`) y muestra un
diálogo Eliminar/Cancelar con el mismo patrón que `layerPendingDelete` y `audioTrackPendingDelete`. El texto avisa si es el
único clip de la capa (la capa también desaparece) y que se puede recuperar con Deshacer (`removeAudioClip` hace checkpoint
de undo). Si el clip ya no existe al abrir el diálogo, este se descarta solo.

### Revisión (hardening)
- El diálogo resuelve el clip con `trackOfClip`/`clip` (antes había un acceso a `pendingTrack` nullable sin smart cast que no compilaba).
- El texto vive en la función pura `audioClipDeleteMessage` (`ui/AudioClipDeletePrompt.kt`), cubierta por `AudioClipDeletePromptTest`.
- Al confirmar, si el clip era el seleccionado (`selectedAudioClipId`) se limpia la selección antes de borrar.
- Cancelar o tocar fuera del diálogo no modifica nada.

## Ventana ampliada del clip (doble toque)
Referencia: FL Studio Mobile. Doble toque sobre la onda de cualquier clip de cualquier carril de audio abre una ventana
con la forma de onda a todo el alto y ancho.

- **Dónde:** cubre solo la zona de capas/carriles (de debajo del Master hasta la barra inferior Módulos/Control/Keyframes).
  Barra superior, vista previa, regla con cursor, Master y barra inferior siguen visibles. Es una capa dentro de `TimelineView`
  (no una pantalla nueva), por lo que el scroll, la selección y el zoom del timeline no se tocan: al cerrar queda todo igual.
- **Estado:** `audioDetailClipId` en `EditorScreen` (solo UI). Se cierra con la X, con "atrás" del sistema (antes que salir del
  proyecto) y sola si el clip deja de existir.
- **Gestos:** el toque simple sigue seleccionando al instante (`detectImmediateTapGestures`); el doble toque selecciona y abre
  (`AudioClipEditActions.onOpenDetail`). Dentro: pellizco = zoom 1×–64× anclado al dedo, arrastre = desplazar, toque = mover el
  cursor al instante tocado, doble toque = volver a 1×.
- **Contenido:** la SEÑAL ORIGINAL del archivo completo, una sola vez (sin repeticiones de loop ni recorte), con el mismo dibujo
  y color que la onda del carril (`AudioWaveformCanvas`), pero con AMPLITUD REAL y lineal (`trueAmplitude`, ver
  `waveformDisplayLevel`): el carril normaliza cada archivo contra su pico y realza los niveles bajos (gamma), lo que hace que
  audios de volumen distinto se vean parecidos; la ventana vuelve a multiplicar por `AudioWaveform.sourcePeak` y no aplica curva. Lo que queda fuera del tramo que suena en el clip se ve atenuado.
  Con Reverse se ve invertida (el recorte se mide sobre esa versión). Cuadrícula de tiempo adaptativa y cursor. Sin franja de
  cabecera: botón de cierre circular flotante arriba a la derecha y nombre/duración en pequeño abajo a la izquierda. Solo
  lectura: no modifica el proyecto (salvo mover el cursor, y solo a puntos del archivo que suenan en el clip).
- **Botón de cierre:** icono premium `ic_close_premium` (SVG provisto: círculo rojo #CC0000 con X calada, `evenodd`), dibujo de 28 dp con
  área táctil de 40 dp, ubicado bajo la fila de etiquetas de tiempo para no taparlas; se apoya sobre un disco blanco para que la X
  (un hueco del vector) se vea blanca y no deje pasar la onda.
- **Dónde se dispara el doble toque:** en el CUERPO del clip (donde está la señal), no en la cabecera de 12 dp, que solo
  selecciona y arrastra. La zona de arrastre (16 dp) se come los primeros 4 dp del cuerpo.
- **Código:** `AudioClipDetailPanel.kt` (UI), `AudioClipDetailMath.kt` (matemática pura, con `AudioClipDetailMathTest`).
- **Cursor del timeline:** mientras la ventana está abierta, la línea del cursor termina en el Master (la ventana usa su
  propia escala de tiempo y su propio cursor).
- **Pendiente (v2):** funciones de edición dentro de la ventana (reproducir solo el clip, recorte, etc.) según la 2.ª referencia.

### Incidente: VerifyError en `EditorScreenKt.EditorScreen` (2026-10-10)
- **Síntoma:** al abrir el editor la app se cerraba con `java.lang.VerifyError: Verifier rejected class ...EditorScreenKt ... copy-cat1 v2<-v302 type=Reference: androidx.compose.runtime.MutableState`.
- **Causa:** `EditorScreen` es una función de ~6.000 líneas con cientos de registros; agregar una variable de estado más (`audioDetailClipId`) la empujó más allá de lo que el verificador de ART acepta.
- **Corrección:** el estado de la ventana ampliada vive en `TimelineView` (`audioDetailClipId`, `BackHandler`, auto-cierre), no en `EditorScreen`; el diálogo de eliminar clip se extrajo a `AudioClipDeleteDialog` (`AudioClipDeletePrompt.kt`). `EditorScreen` quedó con menos locales que antes del cambio.
- **Regla desde ahora:** NO agregar `remember`/`val` locales nuevos a `EditorScreen`; todo estado nuevo va a un composable propio o al ViewModel.

### Zoom profesional de la ventana ampliada (2026-10-10)
**Problemas detectados**
1. *Cierre al hacer zoom.* El registro mostraba una salida NORMAL del editor (se liberó la vista previa y el guardado salió con
   `JobCancellationException`), no un crash. Causa más probable: al pellizcar con los pulgares cerca de ambos bordes, Android lo toma
   por dos gestos "atrás"; el primero cerraba la ventana y el segundo cerraba el editor (el `BackHandler` del editor sale al listado).
2. *Zoom de baja calidad.* La onda del carril guarda un pico cada ~5 ms normalizado; al ampliar se veía en bloques, y el desplazamiento
   iba en saltos de 1 ms (`Long`), que a zoom alto son decenas de píxeles.

**Solución**
- **`FineWaveform`** (`engine/audio`): muestras reales (mono, mayor magnitud con signo) + pirámide de mín/máx/energía (bloques de 16,
  64, 256, 1024, 4096 y 16384 frames). Cada columna se calcula en tiempo ~constante con los picos reales, desde el archivo completo hasta
  una sola muestra; los transitorios nunca desaparecen al alejar. Tope `MAX_FRAMES` = 8 M (~3 min a 44,1 kHz): más allá se usa la onda
  del carril con un zoom máximo acorde a su resolución. Se pide solo al abrir la ventana (`AudioWaveformAnalyzer.loadFine`, caché de 2).
- **`AudioFineWaveformCanvas`**: columnas con silueta mín/máx + núcleo RMS (amplitud REAL y lineal); a ≥ 2,5 px por muestra pasa a curva
  muestra a muestra (palitos desde 6 px, puntos desde 10 px); escala de amplitud 0 / −6 / −12 dBFS; desplazamiento continuo en `Double`.
- **Gestos** (`detectDetailGestures`): arrastre con inercia (`exponentialDecay`, frena en los bordes), pellizco horizontal = zoom de
  tiempo anclado a los dedos, pellizco vertical = zoom de amplitud (1×–32×; el eje se fija al apoyar el 2.º dedo), toque = mover cursor,
  doble toque = zoom animado (geométrico, 260 ms) hacia el punto (6×) o de vuelta al archivo completo.
- **Zoom máximo según los datos** (`detailMaxZoom`): una muestra ocupa 14 px (onda fina) o una cubeta 6 px (onda gruesa). Cuadrícula de tiempo
  desde 0,01 ms hasta 10 min, con etiquetas de la precisión que corresponde.
- **Cierre robusto:** `systemGestureExclusion` (franja central de 200 dp en cada borde) para que pellizcar junto al borde no dispare "atrás";
  `BackHandler` propio del panel (registrado al abrir => máxima prioridad); y una guardia de 800 ms (`AudioClipDetailHost`) que traga un
  segundo "atrás" casi simultáneo tras cerrar la ventana con "atrás".
- **Estado fuera de `TimelineView`/`EditorScreen`:** `AudioDetailController` (clip abierto + guardia). Regla del incidente VerifyError: no
  agregar variables locales a esas funciones gigantes.

**Cobertura:** `FineWaveformTest` (pirámide vs. fuerza bruta, rangos no alineados, transitorios, bloque final incompleto, estéreo, límites),
`AudioClipDetailMathTest` (zoom máximo, interpolación, anclaje, pellizco, cuadrícula sub-ms y etiquetas).

