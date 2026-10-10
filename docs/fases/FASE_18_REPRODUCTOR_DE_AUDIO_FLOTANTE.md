# FASE 18 — Reproductor de audio flotante y arrastrable en el visor de archivos

> Origen: pedido explícito de diseño, con referencia visual entregada
> (captura de una pastilla delgada tipo "mini-reproductor" de sistema
> operativo: nota musical, barra de progreso, tiempo, play, cerrar — todo
> en una sola fila angosta) sobre el visor de archivos de la papelera de
> proyectos (ver FASE 13 — `FilePreviewDialog.kt`).
>
> Mismo criterio de honestidad que fases anteriores: sin compilador
> disponible en este entorno de trabajo, lo de acá está verificado A MANO
> (balance de llaves/paréntesis del bloque nuevo aislado con un script,
> firmas de `detectDragGestures`/`detectHorizontalDragGestures` contra la
> API documentada de Compose Foundation, referencias cruzadas a imports y
> a otros símbolos del archivo) — falta compilar y probar en dispositivo
> real, tal como el usuario ya viene haciendo con cada entrega.

## Pedido

Al previsualizar un archivo de audio desde el navegador de la papelera de
proyectos, el reproductor debía dejar de estar "clavado" en el centro de
la pantalla — la ficha de pantalla completa que ya existía desde FASE 13
— y convertirse en una pastilla flotante, delgada, que el usuario pueda
arrastrar a cualquier punto de la pantalla, con el mismo lenguaje visual
que la referencia entregada (ligeramente adaptado a la identidad de marca
de esta app, no una copia pixel a pixel).

## Diagnóstico

`AudioPreviewContent` (el único composable de audio que existía antes de
esta fase) mezclaba dos responsabilidades en una sola función que
además vivía DENTRO del `Box` de contenido de `SingleFilePreviewScaffold`
(el que tiene `weight(1f)`, debajo de la barra superior):

1. El motor de reproducción (`MediaPlayer`, `LaunchedEffect`/
   `DisposableEffect` de su ciclo de vida).
2. TODA la interfaz — ícono grande, nombre, `Slider` de Material a todo
   el ancho, botón de play/pausa grande — dibujada siempre en el mismo
   lugar, sin ninguna forma de moverse.

Vivir adentro de ese `Box` acotado es, además, la razón de fondo por la
que "flotante y arrastrable por TODA la pantalla" no era alcanzable sin
mover la pieza de lugar primero: ese `Box` nunca ocupa la franja de la
barra superior (título, atrás, compartir, opciones), así que cualquier
intento de arrastre hacia arriba se hubiese topado con ese límite antes
de tiempo.

## Solución aplicada

Todo en `FilePreviewDialog.kt` — sin archivos nuevos de código (sí un
recurso nuevo, ver más abajo), sin capas de compatibilidad ni wrappers:
se reemplazó la función vieja por dos piezas nuevas y se reubicó el
punto donde se instancia una de ellas.

1. **`AudioPreviewBackdrop(fileName)`** — el reemplazo, puertas para
   adentro, de lo que antes dibujaba `AudioPreviewContent` en el `Box` de
   contenido: solo el glifo grande (ahora una nota musical propia, ver
   más abajo) y el nombre de archivo, decorativo, sin ningún control.
   Ocupa exactamente el mismo lugar que antes.

2. **`FloatingAudioPlayerPill(file, fileName, containerSizePx,
   onDismiss)`** — la pieza nueva, con TODO el motor de reproducción
   (idéntico al de antes: un `MediaPlayer` propio armado en
   `Dispatchers.IO`, sin ninguna dependencia nueva) y toda la
   interacción real:
   - Fila única y delgada (56dp de alto, 296dp de ancho): nota musical,
     barra de progreso fina (3dp visibles, con un área de toque de todo
     el alto de la fila para que arrastrarla sea cómodo), tiempo
     transcurrido, play/pausa, cerrar.
   - Superficie con degradé (`SurfaceTintedElevated` → `SurfaceTintedDark`,
     los mismos tonos de siempre de esta app, no colores inventados),
     borde de 1dp en `BrandPurpleLight` semitransparente y sombra real
     (`Modifier.shadow`) — para que se lea como un control flotando
     ENCIMA del visor, no como una tarjeta más incrustada en el layout.
   - **Arrastre de la pastilla entera**: `detectDragGestures` en el
     `Box` exterior de la pastilla, recortado contra `containerSizePx`
     (ver el punto 3) con `clampPillAxis` — mismo criterio exacto que
     `clampToSideLimits`/`clampToBottomLimit` de `FloatingToolWindow`
     (`EditorScreen.kt`, el editor): si el contenedor todavía no se
     midió, no recorta nada, nunca fuerza la pastilla a `(0,0)`.
   - **Arrastre de la barra de búsqueda**: `detectHorizontalDragGestures`
     en su propio `Box` interior — gesto totalmente separado del de
     arriba, con su propio `change.consume()`, así que buscar dentro del
     audio nunca mueve la pastilla por accidente.
   - Elevación y escala animadas (`animateDpAsState`/`animateFloatAsState`)
     al levantar la pastilla — mismo tipo de "lift" táctil que ya usa
     `FloatingToolWindow` al arrastrar sus ventanas en el editor, con una
     curva más liviana acorde al tamaño mucho menor de esta pastilla.
   - Vibración corta (`HapticFeedbackType.LongPress`, vía
     `LocalHapticFeedback`) al tomar la pastilla — confirmación táctil de
     "la agarraste", consistente con el resto de superficies arrastrables
     de la app.
   - El botón "×" llama al mismo `onDismiss` que ya cierra el visor
     completo (mismo destino que la flecha "‹" de la cabecera) — un único
     punto de salida, nunca un estado "pastilla oculta, visor todavía
     abierto detrás" colgando sin sentido.

3. **`containerSizePx`, medido en `SingleFilePreviewScaffold`** — se
   agregó `.onSizeChanged { containerSizePx = it }` al `Box` MÁS
   EXTERIOR de ese scaffold (cabecera + contenido + overlays) y
   `FloatingAudioPlayerPill` se instancia como HERMANA de la `Column` de
   cabecera+contenido, no adentro de ella — así queda libre de
   arrastrarse por TODA la superficie del visor, cabecera incluida, en
   vez de recortada bajo el `Box` de contenido de antes. Mismo criterio
   ya usado en este archivo para medir contenedores reales (ver
   `containerSize` en el pellizco de `ImageGalleryDialogContent`).

4. **Recurso nuevo**: `res/drawable/ic_music_note.xml` — un glifo de
   corcheas dobles, mismo estilo vectorial plano de 24×24 que
   `ic_play`/`ic_pause`/`ic_close` (sin librería de íconos extendida:
   este proyecto solo trae `material-icons-core`, que no incluye ningún
   ícono de nota musical). Reemplaza a `ic_film` como glifo de audio —
   `ic_film` no se tocó ni se borró: sigue en uso en `ProjectsScreen.kt`
   y en la ficha de video de este mismo archivo.

## Qué NO se tocó (alcance deliberado de esta fase)

El reproductor sigue atado al ciclo de vida de ESTE visor — se libera
con el mismo `DisposableEffect` de siempre al cerrarlo. NO sobrevive a
navegar a otra pantalla ni corre en un `Service` en segundo plano con
notificación de medios. Eso es una pieza de arquitectura bastante más
grande (foreground service, `MediaSession`, notificación con controles)
que no se pidió en esta fase y que se dejó fuera a propósito en vez de
improvisarla a medias — queda anotado acá para que quede constancia,
igual que otras fases documentan su propio alcance.

`AudioPreviewPlayer` (`engine/audio/AudioPreviewPlayer.kt`) es un motor
completamente distinto y no se tocó: es el reproductor del PREVIEW en
vivo del EDITOR (sincronizado contra el timeline del proyecto), sin
ninguna relación con el visor de archivos de la papelera.
