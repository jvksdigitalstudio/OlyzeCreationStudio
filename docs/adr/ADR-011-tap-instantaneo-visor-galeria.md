# ADR-011 — Tap simple instantáneo en el visor de galería (delay perceptible al mostrar/ocultar chrome)

**Estado:** Decidido e implementado.
**Referencia:** Reporte del cliente sobre el visor de imágenes (`FilePreviewDialog.kt`
→ `ImageGalleryDialogContent`), 22 de septiembre de 2026: "al dar click en
la pantalla... demora unos milisegundos en aparecer y desaparecer el menú
de mini imágenes... se ve como bug, la experiencia es fea, mucho delay."

## Contexto

El visor de galería (carrusel de imágenes de un proyecto, abierto desde
la Papelera de proyectos u otros orígenes que usan `FilePreviewDialog`)
alterna un "chrome" — encabezado + filmstrip de miniaturas al pie +
barras del sistema — con un solo toque en la imagen (`chromeVisible` en
`ImageGalleryDialogContent`). Ese toque simple convive, sobre la misma
imagen, con un doble-tap que hace zoom 1x↔2.5x (`ZoomableGalleryImage`).

Ambos gestos estaban implementados con un único
`detectTapGestures(onTap = ..., onDoubleTap = ...)` de
`androidx.compose.foundation.gestures`.

## Causa raíz

Cuando `detectTapGestures` recibe **ambos** callbacks (`onTap` y
`onDoubleTap`) a la vez, Compose retiene deliberadamente cada toque
durante la ventana estándar de doble-tap de Android (300ms —
`android.view.ViewConfiguration.getDoubleTapTimeout()`) antes de decidir
que se trata de un toque simple: necesita ese margen para poder
distinguirlo de la primera mitad de un doble-tap. Es un comportamiento
documentado y esperado de la librería, no un bug de Compose.

Ese retraso de ~300ms, sumado a los ~220ms de la animación de
aparición/desaparición del chrome (`CHROME_FADE_MS`, vía
`AnimatedVisibility` + `fadeIn`/`fadeOut`/`slideInVertically`/
`slideOutVertically`), resultaba en un retardo total perceptible de
~520ms entre el toque físico y la reacción visual — exactamente el
síntoma reportado ("se ve como bug... mucho delay").

## Decisión

Reemplazar `detectTapGestures` (solo en `ZoomableGalleryImage`, su único
punto de uso en el archivo) por una detección de gestos propia,
`detectTapAndDoubleTapImmediate`, agregada en `FilePreviewDialog.kt`
junto a los otros gestos custom del archivo (`detectZoomAwarePanGesture`,
`detectFilmstripScrubGesture`), con el mismo estilo:
`awaitEachGesture` + `awaitFirstDown` + loop manual de
`awaitPointerEvent()`.

Comportamiento de la función nueva:

- **`onTap` se dispara en el instante en que el dedo se levanta** —
  nunca espera a ver si viene un segundo toque. Cero retraso artificial.
- El doble-tap se reconoce por separado, comparando el `uptimeMillis` y
  la posición del levantamiento actual contra los del levantamiento
  anterior: si cae dentro de la misma ventana de 300ms
  (`DOUBLE_TAP_TIMEOUT_MILLIS`, constante local que documenta de dónde
  sale ese valor) y suficientemente cerca en pantalla, se dispara
  `onDoubleTap` **además** del `onTap` que ya se disparó con el primer
  toque.
- Un arrastre que supera el touch slop del sistema (swipe del pager,
  pinch-zoom ya consumido por `detectZoomAwarePanGesture`) no cuenta
  como tap ni como doble-tap — mismo criterio que el resto de los
  gestos del archivo.

Este es el mismo patrón que usan los visores de fotos profesionales
(Google Fotos, Apple Fotos): el primer toque siempre reacciona al
instante; un segundo toque rápido simplemente agrega la acción de zoom
encima, sin que el primero se vea retenido a la espera del segundo.

## Por qué no se usó `waitForUpOrCancellation`

Una implementación alternativa, más corta, hubiera sido reutilizar
`waitForUpOrCancellation` (usada internamente por `detectTapGestures`)
en lugar de reimplementar el loop de espera del `up`. Se descartó porque
esa función es `internal` en `androidx.compose.foundation.gestures`
(paquete distinto al de este proyecto) y por lo tanto no es invocable
desde aquí sin recurrir a artificios (reflexión, copiar el paquete,
etc.) — ninguno aceptable para código de producción. El loop manual
implementado es, además, exactamente el mismo patrón ya usado y probado
en este mismo archivo para `detectZoomAwarePanGesture` y
`detectFilmstripScrubGesture`, así que no introduce una técnica nueva
al código base.

## Alcance del fix

Cambios en un solo archivo, `FilePreviewDialog.kt`:

1. Import de `detectTapGestures` eliminado (sin más usos en el archivo).
2. Import de `PointerInputChange` agregado (tipo explícito usado en la
   función nueva).
3. Constante `DOUBLE_TAP_TIMEOUT_MILLIS = 300L` agregada junto a
   `DOUBLE_TAP_SCALE`.
4. Función `detectTapAndDoubleTapImmediate` agregada junto a los demás
   gestos custom del archivo.
5. `ZoomableGalleryImage`: el `pointerInput(file)` que llamaba a
   `detectTapGestures(...)` ahora llama a
   `detectTapAndDoubleTapImmediate(...)` con los mismos callbacks
   (`onTap`/`onDoubleTap`) sin cambios en su lógica interna (el zoom
   1x↔2.5x se mantiene intacto).
6. KDoc de `ZoomableGalleryImage` actualizado para documentar el fix.

No se tocó ningún otro gesto (`detectZoomAwarePanGesture`,
`detectFilmstripScrubGesture`, el `clickable` de cada miniatura del
filmstrip), ni la animación de fade del chrome (`CHROME_FADE_MS` se deja
igual — el problema no era la duración de la animación en sí, sino el
retraso ANTES de que empezara).

## Verificación pendiente en dispositivo real

Cambio hecho sobre el código fuente sin Android SDK/Gradle disponible en
esta sesión. Antes de cerrar:

1. Compilar y confirmar que no rompe ningún gesto existente del visor
   (swipe entre fotos, pinch-zoom, pan con zoom activo, scrub del
   filmstrip, tap en cada miniatura).
2. Abrir la galería, tocar la imagen una vez: el chrome debe
   aparecer/ocultarse de inmediato (solo el fade de `CHROME_FADE_MS`,
   sin ningún retraso previo perceptible).
3. Hacer doble-tap rápido sobre la imagen: debe hacer zoom a 2.5x (o
   volver a 1x si ya estaba en zoom) — confirmar que el zoom sigue
   funcionando igual que antes.
4. Con varias fotos en el proyecto, repetir 2 y 3 en más de una imagen
   del carrusel (el estado de doble-tap se resetea por imagen, ya que el
   `pointerInput` usa `key = file`).
