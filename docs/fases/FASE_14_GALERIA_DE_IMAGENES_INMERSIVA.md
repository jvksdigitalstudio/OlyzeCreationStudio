# FASE 14 — Galería de imágenes inmersiva en el visor de archivos

> Origen: pedido explícito del usuario/product owner, con capturas de
> referencia de Google Fotos y ES Explorador de Archivos, sobre el visor
> de imagen de FASE 13 (`FilePreviewDialog.kt` → `ImagePreviewContent`):
> el visor solo mostraba UNA imagen suelta, sin ninguna noción de que
> podía haber más imágenes en la misma carpeta. Pedido puntual: si la
> carpeta tiene varias imágenes, verlas como una galería real —
> filmstrip de miniaturas en el pie, deslizar entre fotos o tocar una
> miniatura para saltar a ella, la miniatura seleccionada un poco más
> grande que el resto, y un modo inmersivo real (un toque oculta
> encabezado + filmstrip + barras del sistema del teléfono a la vez;
> otro toque las devuelve) — "como funcionan las apps profesionales,
> top de marca, comercial de empresa". Este documento cubre únicamente
> lo que se **implementó por inspección de código** en esta sesión — ver
> "Estado real / pendiente" al final.

## Resumen ejecutivo

| # | Decisión / problema | Resolución | Archivo(s) |
|---|---|---|---|
| 1 | `FilePreviewDialog` solo aceptaba UN archivo (`fileName`/`file`/`sizeBytes`/`onPrepareForShare` sueltos) — no había forma de que el visor supiera que existían más imágenes en la misma carpeta | Nueva API: `FilePreviewDialog(items: List<FilePreviewItem>, initialIndex, onDismiss)`. `FilePreviewItem` reemplaza los cuatro parámetros sueltos por archivo, cada uno con su propio `onPrepareForShare` cerrado sobre su propia entrada — el componente sigue sin conocer `TrashedProjectEntry` (mismo principio de FASE 13, sección 1 de su documento) | `FilePreviewDialog.kt` |
| 2 | El visor de imagen (`ImagePreviewContent`) no tenía ningún concepto de "carrusel" — una imagen a la vez, sin filmstrip, sin swipe entre fotos | `ImageGalleryDialogContent` (nuevo): `HorizontalPager` real entre todas las imágenes de la carpeta + `GalleryFilmstrip` (miniaturas navegables en el pie, solo si hay más de una imagen). Tocar una miniatura salta a esa foto (`pagerState.animateScrollToPage`). Reemplaza a `ImagePreviewContent`, que quedó eliminado — todo lo que antes se llamaba solo para una imagen ahora pasa por acá | `FilePreviewDialog.kt` |
| 3 | Miniatura seleccionada debía "verse un poco más grande" y el filmstrip debía mantenerla visible mientras se navega | `GalleryFilmstripThumbnail` anima su tamaño con `animateDpAsState` (52dp → 68dp) + borde de acento; `GalleryFilmstrip` recalcula, en cada cambio de página, un `scrollOffset` negativo para centrar la miniatura activa dentro del ancho visible (`BoxWithConstraints` + `LazyListState.animateScrollToItem`) | `FilePreviewDialog.kt` |
| 4 | Pedido explícito: un toque en la imagen oculta a la vez el encabezado, el filmstrip **y las barras del propio sistema operativo** (status bar / navegación) — no solo la UI del visor | Un solo estado (`chromeVisible`) gobierna las tres cosas juntas. Las barras del sistema se controlan sobre la `Window` real del `Dialog` de Compose (`DialogWindowProvider` → `WindowInsetsControllerCompat`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), restauradas al cerrar el visor (`DisposableEffect`) | `FilePreviewDialog.kt` |
| 5 | El pinch-zoom/paneo ya existente (FASE 13, `detectTransformGestures`) consumía CUALQUIER arrastre de un solo dedo, incluso a 1x sin zoom — puesto dentro de un `HorizontalPager`, eso habría bloqueado el swipe entre fotos por completo (bug clásico "zoomable image en un pager") | Gesto propio (`detectZoomAwarePanGesture`, reimplementación selectiva de `detectTransformGestures`): con dos dedos (pinch real) o con la imagen ya en zoom (>1x) consume el gesto; con un dedo y la imagen a 1x, deliberadamente NO lo consume — el arrastre sigue de largo hacia el `HorizontalPager`, que es quien lo necesita para cambiar de foto. Además `userScrollEnabled = !currentPageIsZoomed` en el `HorizontalPager` como segunda capa de seguridad | `FilePreviewDialog.kt` |
| 6 | El código de "compartir/abrir con" (`FileProvider` + `Intent.createChooser`) estaba duplicado en potencia entre la ficha de archivo único y la futura galería | Extraído a `shareOrOpenWith(context, action, chooserTitle, fileName, onPrepareForShare)`, función `private suspend` compartida por `SingleFilePreviewScaffold` e `ImageGalleryDialogContent` — una sola implementación, un solo lugar donde corregir un bug de intents el día de mañana | `FilePreviewDialog.kt` |
| 7 | El único call site (`ProjectsTrashScreen.kt`) necesitaba construir la lista de imágenes hermanas de la carpeta actual en el momento del tap | `onOpen` del `TrashedEntryRow` ahora arma un `FilePreviewRequest` (nueva `data class` local): si el archivo tocado es imagen, filtra `uiState.currentEntries` a solo imágenes y calcula el índice del archivo tocado dentro de esa lista; si no, construye una lista de un solo elemento — igual que antes de esta fase | `ProjectsTrashScreen.kt` |
| 8 | El estado de qué mostrar (`previewEntry: TrashedProjectEntry?`) no debía recalcularse de forma reactiva contra `uiState.currentEntries` mientras el visor está abierto | `previewRequest` se congela una única vez, en el momento del tap — si se borra otra imagen de la carpeta mientras la galería está abierta, el índice/tamaño de la galería ya abierta no se corre bajo los dedos del usuario a mitad de un swipe | `ProjectsTrashScreen.kt` |

## 1. Por qué `FilePreviewDialog` pasó a recibir una lista, no un archivo

El pedido es, en esencia, "el visor de imagen tiene que saber que hay más
fotos al lado". La única forma correcta de resolver eso sin acoplar el
componente a `TrashedProjectEntry` (ver FASE 13, sección 1: el
componente debe seguir siendo reusable desde cualquier otro lugar que
liste archivos) era generalizar su entrada de "un archivo" a "una lista
de archivos navegables + cuál se abre primero". `FilePreviewItem` es
exactamente el mismo contrato mínimo que antes tenían los cuatro
parámetros sueltos (`fileName`, `file`, `sizeBytes`, `onPrepareForShare`),
solo que ahora agrupado para poder vivir dentro de una `List`.

La decisión de **qué archivos van en esa lista** la sigue tomando
siempre quien llama (`ProjectsTrashScreen.kt`), nunca `FilePreviewDialog`
por su cuenta — el componente en sí no sabe leer carpetas ni filtrar por
tipo, solo recibe la lista ya armada. Esto mantiene la misma separación
de responsabilidades que ya tenía FASE 13.

## 2. Contrato entre el dispatcher y las dos vistas internas

`FilePreviewDialog` es ahora, en esencia, un *dispatcher* de dos líneas:
clasifica el archivo que se pidió abrir (`items[initialIndex]`) y, si es
una imagen, enruta a `ImageGalleryDialogContent`; para cualquier otro
tipo, enruta a `SingleFilePreviewScaffold` (la ficha única de FASE 13,
sin cambios de comportamiento — JSON, texto, audio, video,
`UNSUPPORTED`).

Esta decisión se toma mirando **solo** el archivo inicial, no toda la
lista — por contrato del único call site existente: si el archivo
tocado es una imagen, la lista entera son imágenes de la misma carpeta;
si no lo es, la lista es un único archivo no-imagen. `IMAGE` nunca debe
llegar a `SingleFilePreviewScaffold`; si de algún modo llegara (un futuro
call site que rompa el contrato), su rama de `when` lo hace explícito con
un `error(...)` en vez de fallar en silencio o mostrar una pantalla en
blanco — falla ruidosa y clara sobre una violación de invariante, no un
bug silencioso.

## 3. `HorizontalPager` + filmstrip — por qué son dos piezas independientes

`ImageGalleryDialogContent` no es un solo componente monolítico: separa
el carrusel (`HorizontalPager`, ocupa toda la pantalla, gestos táctiles)
del filmstrip (`GalleryFilmstrip`, franja del pie, solo lectura +
tocable). Los dos leen y escriben el mismo `pagerState.currentPage` —
deslizar en el carrusel mueve la miniatura seleccionada del filmstrip;
tocar una miniatura mueve el carrusel (`pagerState.animateScrollToPage`).
Ninguno de los dos "sabe" del otro más allá de ese estado compartido —
se podría, el día de mañana, ocultar el filmstrip por completo (p. ej.
en una pantalla muy angosta) sin tocar el carrusel.

El filmstrip solo se renderiza si `items.size > 1` — con una sola imagen
en la carpeta, no tiene sentido mostrar una franja con una única
miniatura; el visor se comporta como una imagen suelta, pero conserva el
modo inmersivo (ver sección 5), que aplica sin importar cuántas fotos
haya.

## 4. Miniatura seleccionada más grande y centrada — mecánica real

Dos efectos independientes logran el resultado pedido ("que se vea
lijeramente más grande… para saber en qué imagen está seleccionada"):

- **Tamaño animado**: cada `GalleryFilmstripThumbnail` anima su propio
  tamaño con `animateDpAsState` entre `FILMSTRIP_THUMB_SIZE` (52dp,
  miniaturas normales) y `FILMSTRIP_THUMB_SIZE_SELECTED` (68dp, la
  actual), más un borde de acento (`BrandPurpleLight`) y una atenuación
  (`alpha = 0.38`) sobre las miniaturas NO seleccionadas — tres señales
  visuales independientes (tamaño, borde, brillo) apuntando a la misma
  miniatura, no solo una.
- **Autocentrado**: `GalleryFilmstrip` calcula, dentro de un
  `BoxWithConstraints` (para conocer el ancho real disponible en
  píxeles), un `scrollOffset` negativo — `-((viewportPx -
  selectedThumbPx) / 2)` — y llama a
  `listState.animateScrollToItem(index = currentIndex, scrollOffset =
  …)` cada vez que `currentIndex` cambia. Un `scrollOffset` negativo
  "retrocede" desde el inicio de la miniatura objetivo lo suficiente
  para que termine centrada en vez de pegada al borde izquierdo — la
  franja de miniaturas sigue siempre al visor principal, nunca al revés.

## 5. Modo inmersivo — encabezado + filmstrip + barras del sistema, las tres juntas

Un solo estado (`chromeVisible: Boolean`) gobierna las tres piezas: el
encabezado (`FilePreviewTopBar`, con `AnimatedVisibility` + fundido),
el filmstrip (`AnimatedVisibility` + fundido y deslizamiento) y las
barras del propio sistema operativo del teléfono. Un tap simple sobre la
imagen (`ZoomableGalleryImage`, `onTap`) alterna `chromeVisible` — nunca
un swipe entre fotos, que deja el modo inmersivo intacto.

Ocultar las barras del sistema (status bar / navegación), no solo la UI
del visor, requiere llegar a la `Window` real detrás del `Dialog` de
Compose — un `Dialog` de Compose crea su propia `Window`, separada de la
de la `Activity`. La vía oficial para llegar a ella es
`DialogWindowProvider` (`(LocalView.current.parent as?
DialogWindowProvider)?.window`), documentada precisamente para este caso
de uso (personalizar flags/insets de la ventana de un diálogo). Sobre
esa `Window`:

- `WindowCompat.setDecorFitsSystemWindows(window, false)` — contenido de
  borde a borde, para que la foto pueda ocupar el área completa de la
  pantalla incluso detrás de donde estarían las barras una vez ocultas.
- `WindowInsetsControllerCompat.hide/show(WindowInsetsCompat.Type.systemBars())`
  en un `LaunchedEffect(dialogWindow, chromeVisible)` — se ejecuta cada
  vez que `chromeVisible` cambia.
- `systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` — deslizar
  desde el borde de la pantalla revela las barras momentáneamente sin
  necesidad de tocar la imagen, mismo comportamiento que cualquier visor
  de fotos/video inmersivo del sistema operativo.
- Restauración explícita en `DisposableEffect`/`onDispose` (mostrar
  barras, volver `decorFitsSystemWindows` a `true`), envuelta en
  `runCatching` — la `Window` del diálogo puede estar ya en proceso de
  desmontaje cuando corre el `onDispose`, mismo criterio defensivo que ya
  usa `AudioPreviewContent.onDispose` (FASE 13) para `MediaPlayer.release()`.

Este modo inmersivo es exclusivo del visor de imagen — la ficha única de
JSON/texto/audio/video/`UNSUPPORTED` conserva su encabezado siempre
visible, sin tocar las barras del sistema, tal como pidió el usuario
explícitamente ("el visor de imagen", no todos los tipos de archivo).

## 6. El bug clásico de "imagen con zoom dentro de un pager" — y cómo se evitó

El pinch-zoom/paneo de FASE 13 usaba
`androidx.compose.foundation.gestures.detectTransformGestures`, que
consume (`PointerInputChange.consume()`) el arrastre de un solo dedo
apenas supera el *touch slop*, sin importar si la imagen está en zoom o
no. Puesto tal cual dentro de un `HorizontalPager`, ese consumo
temprano le habría robado al `Pager` el arrastre horizontal que necesita
para deslizar entre fotos — el swipe entre imágenes simplemente nunca
habría funcionado, sin ningún error visible, solo una galería que se
siente "trabada".

`detectZoomAwarePanGesture` (nuevo, en `FilePreviewDialog.kt`) es una
reimplementación deliberada y selectiva del mismo algoritmo interno de
`detectTransformGestures` (mismo uso de `awaitEachGesture`,
`calculateZoom`/`calculatePan`/`calculateCentroidSize`, mismo cálculo de
*touch slop*), con una única diferencia: antes de consumir el evento,
comprueba si el gesto es multitouch (`event.changes.size > 1`, pinch
real) o si la imagen ya está en zoom (`scaleProvider() > 1x`). Si
ninguna de las dos se cumple — un solo dedo, imagen a 1x — el evento
**no se consume**, y sigue de largo hacia el `HorizontalPager` ancestro.
Como segunda capa de seguridad (no la única), `userScrollEnabled =
!currentPageIsZoomed` en el propio `HorizontalPager` refuerza lo mismo a
nivel de estado, no solo a nivel de gesto.

Cada imagen abre siempre a 1x, nunca hereda el zoom de la foto anterior:
un `LaunchedEffect(pagerState.currentPage) { currentPageIsZoomed = false
}` en `ImageGalleryDialogContent` fuerza el reseteo del flag compartido
en cada cambio de página, además de que cada `ZoomableGalleryImage` ya
resetea su propio `scale`/`offset` al recomponerse con un `file` nuevo
(`remember(file) { … }`).

## 7. `shareOrOpenWith` — de código potencialmente duplicado a una sola función

Antes de esta fase, "compartir" y "abrir con" (construir el `Intent`,
resolver el `content://` vía `FileProvider`, lanzar el *chooser*, y
manejar el caso "no hay ninguna app instalada") vivían enteramente
dentro de la ficha de archivo único. Al agregar la galería, ese mismo
flujo hacía falta también para compartir la foto que esté actualmente en
pantalla del carrusel — copiarlo y pegarlo una segunda vez habría dejado
dos lugares para corregir el mismo bug el día de mañana. Se extrajo una
única función `private suspend fun shareOrOpenWith(...)`, usada tanto por
`SingleFilePreviewScaffold` como por `ImageGalleryDialogContent`
(`shareCurrent()`, que siempre opera sobre `items[pagerState.currentPage]`
— la foto que el usuario está viendo en ese momento, no la que se tocó
originalmente para abrir el visor).

## 8. `ProjectsTrashScreen.kt` — construcción de la galería en el momento del tap

`onOpen` del `TrashedEntryRow`, dentro del `LazyColumn` del navegador de
carpetas, ahora distingue tres casos (antes eran solo dos: carpeta vs.
archivo):

1. **Carpeta** → `viewModel.navigateInto(entry)`, sin cambios.
2. **Imagen** → filtra `uiState.currentEntries` (la carpeta que se está
   viendo en ese momento) a solo entradas no-carpeta cuyo
   `classifyFilePreviewKind` sea `IMAGE`, mapea cada una a un
   `FilePreviewItem` con su propio `onPrepareForShare` cerrado sobre esa
   entrada puntual, y calcula el índice del archivo tocado dentro de esa
   lista filtrada (`indexOfFirst { it.relativePath == entry.relativePath
   }`, con `.coerceAtLeast(0)` como defensa si por alguna razón no se
   encontrara).
3. **Cualquier otro archivo** → mismo comportamiento que FASE 13, ahora
   expresado como una lista de un único `FilePreviewItem`.

El resultado de cualquiera de los tres casos (2 o 3) se guarda en
`previewRequest`, una `data class FilePreviewRequest(items, initialIndex)`
local a este archivo — deliberadamente **no** reactiva a cambios
posteriores de `uiState.currentEntries`: se calcula una única vez, en el
instante del tap, y de ahí en más el visor abierto no se entera de
altas/bajas posteriores en la carpeta (p. ej. si el usuario, sin cerrar
el visor, hubiera podido borrar otra imagen desde algún otro lado — hoy
no es posible desde la UI actual, pero el diseño ya lo contempla para
cuando lo sea).

## Checklist de calidad

- [x] `FilePreviewDialog` sigue sin conocer `TrashedProjectEntry` ni
      ningún modelo de dominio de proyectos (sección 1).
- [x] El filmstrip y el modo inmersivo son exclusivos del visor de
      imagen — JSON/texto/audio/video/`UNSUPPORTED` no cambiaron de
      comportamiento respecto a FASE 13.
- [x] Ningún gesto de arrastre de una sola foto sin zoom le roba el
      swipe al `HorizontalPager` — verificado por diseño (sección 6),
      con doble capa de protección (no-consumo del gesto +
      `userScrollEnabled`).
- [x] Cero código de "compartir/abrir con" duplicado entre la ficha
      única y la galería (sección 7).
- [x] Las barras del sistema se restauran siempre al cerrar el visor,
      incluso si la ventana ya está en desmontaje (`runCatching`).
- [x] `previewRequest` se congela en el momento del tap — sin
      recomputar la galería de forma reactiva mientras está abierta
      (sección 8).
- [x] Sin dependencias nuevas — `HorizontalPager`/`rememberPagerState`
      son parte estable de `androidx.compose.foundation.pager` ya
      disponible en el BOM de Compose que ya usa el proyecto; el gesto
      de zoom-consciente-del-pager se construyó a mano sobre APIs
      estables de `androidx.compose.foundation.gestures` en vez de
      incorporar una librería de terceros cuya API exacta no se podía
      verificar sin acceso a red en este entorno de trabajo.
- [x] Balance de llaves/paréntesis verificado sobre cada archivo tocado.
- [ ] **No se compiló con Gradle en esta sesión** (mismo motivo que
      FASE 13: entorno de trabajo sin toolchain de Android ni acceso de
      red) — ver "Estado real / pendiente".
- [ ] Sin verificación en dispositivo real todavía — pendiente del lado
      del usuario, como el resto de las fases de este proyecto.

## 9. R1 — Bug real encontrado en CI: `PagerState` experimental sin `@OptIn`

Primera corrida real de `./gradlew` sobre esta fase (GitHub Actions,
`Android CI Build #525`, posterior a la entrega inicial) encontró lo que
ninguna revisión manual en este entorno podía atrapar sin un compilador:
`:app:compileDebugKotlin` falló con nueve errores del tipo

```
e: file:///…/FilePreviewDialog.kt:407:22 This foundation API is
experimental and is likely to change or be removed in the future.
```

repetidos en cada línea que tocaba `pagerState` (líneas 407, 421, 425,
484, 513, 514 de la primera entrega). Causa raíz: `PagerState`,
`rememberPagerState`, `HorizontalPager` y `PagerState.animateScrollToPage`
siguen marcados `@ExperimentalFoundationApi` (nivel de opt-in **ERROR**,
no solo una advertencia) en la versión de Compose Foundation que resuelve
el BOM `2024.06.00` de este proyecto — sin un `@OptIn` explícito, el
código ni siquiera compila, más allá de que la lógica en sí fuera
correcta.

Corrección: `@file:OptIn(ExperimentalFoundationApi::class)` agregado al
principio de `FilePreviewDialog.kt` (antes del `package`), en vez de un
`@OptIn` acotado solo a `ImageGalleryDialogContent` — deliberadamente a
nivel de archivo: sin poder compilar en este entorno para confirmar si
`awaitEachGesture`/`calculateZoom`/`calculatePan`/`calculateCentroidSize`
(usados en `detectZoomAwarePanGesture`) comparten la misma marca en esta
versión exacta, acotar el `@OptIn` a una sola función y quedarse corto
habría significado arriesgar una segunda corrida de CI fallida por el
mismo motivo. Mismo criterio que FASE 13 sección 11 (`withStyle` sin
importar): un error real de CI que ninguna revisión manual podía
predecir con certeza total sin acceso a un compilador, corregido apenas
se tuvo el log real.

**Archivo:** `FilePreviewDialog.kt`.

## 10. R2 — Bug real reportado en dispositivo: franjas negras arriba/abajo en el visor de imagen

Tras la corrección de la sección 9 (CI en verde a nivel de compilación),
la primera prueba real en dispositivo reportó el visor de imagen con
franjas negras/oscuras fijas arriba y abajo — la imagen **no** ocupaba
la pantalla completa pese a que la sección 5 (arriba) ya forzaba
`setLayout(MATCH_PARENT, MATCH_PARENT)` sobre la `Window` real del
`Dialog`.

**Causa raíz:** `Dialog` de Compose hereda, por defecto, el tema de
diálogo flotante de Android (`windowIsFloating`). El `windowBackground`
de ese tema es un *9-patch* con relleno (padding) propio, pensado para
dibujar la sombra y las esquinas redondeadas de un diálogo normal
(tipo `AlertDialog`). Ese relleno es **interno al propio drawable de
fondo de la ventana** — `setLayout(MATCH_PARENT, MATCH_PARENT)` hace
que la `Window` ocupe la pantalla física completa, pero el *contenido*
(nuestro `Surface`) se sigue dibujando encogido dentro de ese relleno
heredado del tema, exactamente como si el drawable de fondo tuviera un
margen invisible. Ningún ajuste de `WindowInsetsControllerCompat` ni de
`statusBarColor`/`navigationBarColor` corrige esto, porque el relleno
no es un inset del sistema — es geometría propia del `windowBackground`
del tema de diálogo.

**Corrección:** `window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))`
reemplaza ese `windowBackground` heredado por un color sólido sin
relleno, más `window.decorView.setPadding(0, 0, 0, 0)` como defensa
adicional contra cualquier padding que algún fabricante aplique también
a nivel de `decorView`. Con esto la `Window` MATCH_PARENT y su
contenido dibujable coinciden exactamente — borde a borde real, sin
ninguna franja residual, en cualquier dispositivo. Mismo criterio
técnico que usa cualquier visor de fotos inmersivo real (Google Fotos,
Apple Fotos) para lograr pantalla completa desde un diálogo/overlay.

**Archivo:** `FilePreviewDialog.kt`, dentro del mismo `DisposableEffect`
de `ImageGalleryDialogContent` que ya configuraba el modo inmersivo
(sección 5) — no se creó ningún mecanismo nuevo ni paralelo.

## 11. R3 — Segunda causa real, independiente de la sección 10: scrim de contraste del sistema (API 29+)

Aun con el `windowBackground` ya reemplazado (sección 10) y
`statusBarColor`/`navigationBarColor` en `TRANSPARENT`, Android 10+
(API 29, `Build.VERSION_CODES.Q`) dibuja **por encima de la app**, sin
que el código lo pida, un scrim semitransparente propio detrás de la
barra de estado y la barra de navegación — pensado para que los iconos
del sistema (hora, batería, flechas de navegación gestual) se sigan
viendo legibles sobre cualquier contenido, incluida una foto oscura de
borde a borde. Ese scrim es indistinguible a simple vista de una
"franja" residual: se ve como una zona apenas más oscura que el resto
de la imagen, en las mismas franjas horizontales donde vivirían las
barras del sistema — exactamente el síntoma reportado, incluso después
de la corrección de la sección 10.

**Corrección:** `window.isStatusBarContrastEnforced = false` y
`window.isNavigationBarContrastEnforced = false` (guardado tras
`Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`, ya que esas
propiedades de `Window` no existen en versiones anteriores de la API).
Con las dos correcciones (sección 10 + esta) combinadas, no debería
quedar ningún origen conocido de franja/tinte ajeno a la imagen sobre
la `Window` del `Dialog`.

**Archivo:** `FilePreviewDialog.kt`, mismo bloque que la sección 10.

## 12. R4 — Fondo difuminado de relleno: eliminar la franja negra plana cuando la foto no llena la pantalla

Con las secciones 10 y 11 corregidas, el visor queda realmente borde a
borde — pero para una foto cuya relación de aspecto no coincide con la
de la pantalla (p. ej. una foto panorámica abierta en un teléfono
vertical), `ContentScale.Fit` sigue dejando, matemáticamente, espacio
sobrante en algún eje: es la única forma de mostrar la foto **completa,
sin recortar nada** (requisito explícito de diseño desde el origen de
esta fase) cuando el aspecto no calza. Antes de esta sección, ese
espacio sobrante se veía como una franja negra plana — visualmente
pobre, aunque técnicamente correcta.

**Corrección:** `ZoomableGalleryImage` ahora dibuja, detrás de la foto
nítida, una segunda copia de la misma foto (`ContentScale.Crop`,
`Modifier.blur(48.dp)`, `alpha = 0.65f`) más un scrim negro adicional
(`alpha = 0.45f`) — mismo criterio visual que usan Instagram, Spotify o
Apple Fotos para este caso exacto: el espacio sobrante deja de ser
negro plano y pasa a ser la propia imagen, difuminada y oscurecida lo
suficiente para que la foto nítida de encima siga siendo, sin
ambigüedad, la protagonista. El fondo se omite mientras la foto está
en zoom (`scale > 1x`): ampliada, el fondo ya no llega a verse por
debajo, así que difuminarlo en cada frame sería costo de composición
sin ningún beneficio visual — el propio `LaunchedEffect(scale)` que ya
existía para avisar al carrusel del estado de zoom (sección de diseño
original) se reutiliza acá como condición de pintado.

**Archivo:** `FilePreviewDialog.kt`, `ZoomableGalleryImage`.

## Estado real / pendiente

- **La primera corrida real de CI (`Android CI Build #525`) SÍ ocurrió**
  (a diferencia de lo que decía la primera versión de este documento) y
  encontró el bug real de la sección 9 (`PagerState` experimental sin
  `@OptIn`) — corregido. **Todavía no hay una corrida de CI en verde
  posterior a esa corrección** confirmando que no queda nada más suelto;
  esa es la que falta para cerrar esta fase del todo. El riesgo más alto
  que sigue pendiente de esa confirmación es el gesto manual
  `detectZoomAwarePanGesture`: su lógica se razonó con cuidado contra el
  comportamiento documentado de `androidx.compose.foundation.gestures`
  (mismas funciones que ya usa `detectTransformGestures` internamente),
  pero un gesto táctil combinado con un `HorizontalPager` es, por
  naturaleza, el tipo de interacción que más conviene probar a mano en
  un dispositivo real antes de darla por cerrada — pellizcar para hacer
  zoom, deslizar entre fotos a 1x, y el caso límite de soltar el
  pellizco a mitad de gesto.
- **No hay ADR dedicado.** La decisión de reimplementar el gesto de
  zoom a mano en vez de incorporar una librería de terceros
  (`telephoto` u otra especializada en "imagen con zoom dentro de un
  pager") está registrada acá, sección 6 y checklist — no en
  `docs/adr/`, mismo criterio que FASE 12/13 en su momento. El motivo
  central: sin acceso a red en este entorno de trabajo no había forma de
  verificar la superficie exacta de API de una librería externa antes de
  comprometerla al código — un build roto por una API mal recordada es
  peor que una implementación algo más artesanal pero construida sobre
  APIs estables y ya verificadas contra el resto del proyecto.
