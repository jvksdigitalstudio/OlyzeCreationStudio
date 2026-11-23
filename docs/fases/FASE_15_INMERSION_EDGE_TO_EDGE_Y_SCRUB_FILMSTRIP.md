# FASE 15 — Inmersión borde a borde real y scrub del filmstrip

> Origen: pedido explícito del usuario/product owner, con capturas de
> referencia de su propia app y, por comparación, de Google Fotos, sobre
> `ImageGalleryDialogContent` de FASE 14 (`FilePreviewDialog.kt`). Dos
> pedidos puntuales:
> 1. El modo inmersivo de FASE 14 (un toque oculta encabezado + filmstrip
>    + barras del sistema) dejaba, en el build real del usuario, una
>    franja negra fija arriba y otra abajo — la pantalla no quedaba
>    verdaderamente de borde a borde.
> 2. El filmstrip de miniaturas solo se navegaba tocando una miniatura a
>    la vez (FASE 14, sección 3). Pedido: sumar una segunda forma de
>    navegar — presionar y arrastrar sobre el filmstrip para recorrer las
>    fotos de forma continua y rápida, sin soltar el dedo, sin perder la
>    primera (el tap directo sigue funcionando igual).
>
> Este documento cubre únicamente lo que se **implementó por inspección
> de código** en esta sesión — ver "Estado real / pendiente" al final.
> Mismo criterio de honestidad que FASE 14: sin compilador disponible en
> este entorno de trabajo (sin red para resolver dependencias Gradle),
> todo lo de acá está razonado a mano contra la API documentada de
> Compose/Android, no confirmado contra un build real.

## Resumen ejecutivo

| # | Decisión / problema | Resolución | Archivo(s) |
|---|---|---|---|
| 1 | El `Dialog` de la galería (`ImageGalleryDialogContent`) solo hacía `setDecorFitsSystemWindows(false)` + ocultar barras del sistema — insuficiente en la práctica: la `Window` propia del `Dialog` puede quedar en alto `WRAP_CONTENT` según el fabricante, dejando una franja fija que ningún cambio de insets corrige porque ni siquiera es parte del área dibujable de la `Window` | `window.setLayout(MATCH_PARENT, MATCH_PARENT)` explícito, agregado al mismo bloque `DisposableEffect` que ya fijaba `setDecorFitsSystemWindows` | `FilePreviewDialog.kt` |
| 2 | `statusBarColor` / `navigationBarColor` de esa `Window` seguían con el scrim opaco heredado del tema — visible como franja apenas las barras vuelven a mostrarse (p. ej. al deslizar desde el borde en modo inmersivo) | Ambos puestos explícitamente transparentes (`AndroidColor.TRANSPARENT`) en el mismo bloque | `FilePreviewDialog.kt` |
| 3 | En pantallas con notch/cámara perforada, sin `layoutInDisplayCutoutMode`, el sistema reserva una franja negra fija alrededor del recorte incluso con las barras ya ocultas | `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`, aplicado condicionado a `Build.VERSION.SDK_INT >= P` (API 28) — `minSdk` del proyecto es 26, así que este atributo no existe en 26/27 y hay que guardar el `if` | `FilePreviewDialog.kt` |
| 4 | El filmstrip (FASE 14) solo tenía una forma de navegar: tap directo en una miniatura (`clickable` + `pagerState.animateScrollToPage`) | Segunda forma agregada, sin tocar la primera: arrastrar en cualquier punto del filmstrip recorre las fotos de forma continua (`detectFilmstripScrubGesture`, nuevo) — cada `FILMSTRIP_SCRUB_STEP` (36dp) de arrastre horizontal avanza o retrocede una foto, sin soltar el dedo | `FilePreviewDialog.kt` |
| 5 | El scrub y el tap de cada miniatura no podían "pisarse": un arrastre que empieza sobre una miniatura no debía cancelar la posibilidad de que fuera, en realidad, un tap corto | `detectFilmstripScrubGesture` no consume nada hasta superar el touch slop del sistema (mismo criterio ya usado en FASE 14 para `detectZoomAwarePanGesture`) — un toque corto sigue llegando intacto al `clickable` de la miniatura; recién al superar el slop, el scrub pasa a consumir el resto del gesto | `FilePreviewDialog.kt` |
| 6 | El `LazyRow` del filmstrip tenía su propio scroll táctil (heredado de FASE 14), que competiría por el mismo gesto que el nuevo scrub | `userScrollEnabled = false` en el `LazyRow` — el desplazamiento de la fila pasa a estar 100% conducido por el scrub + el autocentrado ya existente (`LaunchedEffect(currentIndex, viewportPx)`), nunca por un scroll libre independiente del índice actual | `FilePreviewDialog.kt` |
| 7 | El gesto de scrub vive en un `pointerInput` de vida larga (key = cantidad de ítems, estable durante toda la sesión del visor) — leer el parámetro `currentIndex` directamente ahí dentro lo dejaría fijo en el valor que tenía el día que el gesto arrancó | `rememberUpdatedState(currentIndex)` — el gesto siempre lee el índice más reciente, sin importar cuántas veces haya cambiado desde que el `pointerInput` se instaló | `FilePreviewDialog.kt` |

## 1. Por qué la franja no se solucionaba solo ocultando las barras

FASE 14 ya controlaba la visibilidad de las barras del sistema
(`WindowInsetsControllerCompat.hide`/`show`) sobre la `Window` real del
`Dialog` (vía `DialogWindowProvider`, no la `Window` de la `Activity`).
Eso es necesario pero no alcanza: un `Dialog` de Compose con
`DialogProperties(usePlatformDefaultWidth = false)` pide ancho completo
explícitamente, pero **no** fuerza el alto — en ciertas combinaciones de
versión de Compose/fabricante, la `Window` puede terminar dimensionada
en `WRAP_CONTENT` de alto, y ningún control de insets puede "estirar"
contenido más allá del área real de la `Window` que lo contiene. La
franja que el usuario reportó (arriba: la propia barra de estado del
teléfono; abajo: espacio vacío que no llegaba al borde real) es
consistente con esa hipótesis.

La corrección son las tres piezas de la tabla (setLayout MATCH_PARENT +
colores de barra transparentes + cutout mode), todas sobre la misma
`Window`, en el mismo `DisposableEffect` que ya existía — no un
mecanismo nuevo, un endurecimiento del que ya estaba.

## 2. Por qué el scrub se implementó a nivel de contenedor, no por miniatura

La primera aproximación considerada — atar el gesto de arrastre
directamente a la miniatura seleccionada (`GalleryFilmstripThumbnail`
cuando `isSelected == true`) — se descartó por una razón concreta: en
cuanto el scrub avanza un solo paso, `currentIndex` cambia, la miniatura
que tenía el foco dentro del gesto deja de ser la seleccionada, y su
`Modifier.pointerInput` (condicionado a `isSelected`) desaparece de la
composición a mitad de gesto — cancelando el arrastre en el primer paso.

La solución real: el gesto vive en el `BoxWithConstraints` que envuelve
todo el filmstrip (contenedor estable, no cambia de identidad durante la
sesión), usando el mismo patrón ya establecido en el archivo para
"gesto corto vs. gesto largo que conviven" (`detectZoomAwarePanGesture`,
FASE 14 sección 5): no consumir nada hasta superar el touch slop, para
no romper el tap de cada miniatura.

## 3. Sentido del arrastre

Arrastrar el dedo hacia la **derecha** retrocede a la foto **anterior**
— mismo sentido que deslizar el dedo hacia la derecha sobre el
`HorizontalPager` principal (contenido se desplaza a la derecha, revela
la página anterior). Deliberado, para que las dos formas de navegar
(swipe en la foto grande, arrastre en el filmstrip) se sientan
coherentes entre sí y no en sentidos opuestos.

## 4. Sensibilidad del scrub

`FILMSTRIP_SCRUB_STEP = 36.dp` — deliberadamente más chico que el ancho
real de una miniatura (52–68dp): el pedido explícito fue "desplazamiento
continuo o scroll rápido", no una selección 1:1 pixel-perfecta contra
cada miniatura. Con este valor, un solo arrastre de lado a lado de la
pantalla recorre varias fotos. Es una constante de un solo lugar
(`private val` junto a las demás constantes del filmstrip) — ajustable
sin tocar la lógica del gesto si en el uso real se siente demasiado
sensible o demasiado lento.

El acumulador de arrastre (`scrubAccumPx`) descuenta solo lo
"consumido" en pasos de índice completos y conserva el resto
(`scrubAccumPx -= steps * scrubStepPx`, no `= 0`) — así el gesto se
siente continuo en vez de resetear la sensibilidad cada vez que cruza a
la siguiente foto.

## Checklist de verificación manual (sin compilador disponible)

- [x] `window.setLayout` usa las constantes correctas de
      `WindowManager.LayoutParams` (`MATCH_PARENT` = `-1`).
- [x] `layoutInDisplayCutoutMode` queda dentro de un `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)` — no existe en API 26/27 (`minSdk` real del proyecto).
- [x] `import android.graphics.Color as AndroidColor` para no chocar con `androidx.compose.ui.graphics.Color`, ya importado y usado en todo el archivo.
- [x] `positionChange()` (`androidx.compose.ui.input.pointer.positionChange`) es la extensión correcta para el delta de un `PointerInputChange` — no confundir con `positionChanged()` (ya importado, devuelve `Boolean`), que sigue usándose sin cambios en `detectZoomAwarePanGesture`.
- [x] `rememberUpdatedState` está cubierto por el `import androidx.compose.runtime.*` ya existente en el archivo — no requiere un import nuevo.
- [x] `mutableFloatStateOf` idem — ya usado en el mismo archivo (`ZoomableGalleryImage.scale`, `AudioPreviewContent.dragPositionMs`) antes de esta sesión.
- [x] `userScrollEnabled = false` en el `LazyRow` no bloquea `listState.animateScrollToItem` — ese parámetro solo desactiva el scroll **gestual** del usuario, no el scroll programático, que sigue funcionando igual que en FASE 14.

## Estado real / pendiente

- **Nada de esto se compiló ni se corrió en un dispositivo real en esta
  sesión.** Sin red para resolver dependencias Gradle en este entorno de
  trabajo, no hay forma de correr `:app:compileDebugKotlin` ni de probar
  el gesto de scrub o el borde a borde en un teléfono real — exactamente
  el mismo límite que dejó pasar el bug de FASE 14 sección 9
  (`PagerState` experimental) hasta la corrida real de CI. La
  verificación pendiente más importante es esa: un build en verde de
  `Android CI Build`, y una prueba táctil real del scrub (¿se siente
  bien la sensibilidad de `FILMSTRIP_SCRUB_STEP`? ¿el borde a borde se
  ve completo en el dispositivo real del usuario, con su notch/gesture
  nav específicos?).
- **No hay ADR dedicado** — mismo criterio que FASE 14: esto es un
  hotfix/endurecimiento sobre una decisión ya tomada (modo inmersivo real
  vía `Window` del `Dialog`), no una decisión arquitectónica nueva.
- Si la prueba real muestra que `FILMSTRIP_SCRUB_STEP` se siente
  demasiado sensible o demasiado lento, es el único número a ajustar —
  la lógica alrededor no debería necesitar tocarse.
