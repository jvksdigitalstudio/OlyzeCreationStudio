# FASE 4 — Memoria en la decodificación de imágenes y sincronización real UI↔GPU al reabrir un proyecto

> Reportado por el usuario: "reabro un proyecto con fondo ya guardado y
> aplicado, y a veces se ve el fondo bien, y otras veces se ve todo el
> lienzo en verde chroma-key" — captura de pantalla adjunta como
> evidencia (canvas 100% verde, timeline vacío). Reporte adicional en el
> mismo hilo: incluso cuando el fondo termina cargando bien, se percibe
> un destello verde de milisegundos justo al abrir.

## Resumen ejecutivo

| # | Síntoma reportado | Causa raíz | Archivo(s) corregido(s) |
|---|---|---|---|
| 1 | El lienzo entero queda en verde chroma-key al reabrir un proyecto, de forma intermitente (a veces sí, a veces no) | `ImageDecoding` atrapaba `OutOfMemoryError` con un `catch (t: Throwable)` genérico y descartaba la capa en silencio; `ProjectStorage.loadProject` decodificaba TODAS las capas del proyecto en paralelo a resolución completa, multiplicando el pico de memoria en el instante exacto de reabrir | `ImageDecoding.kt`, `ProjectStorage.kt` |
| 2 | Destello verde de milisegundos al reabrir, incluso cuando el fondo termina cargando correctamente | El overlay de carga de `EditorScreen` se ocultaba apenas terminaba el decode en CPU (`isLoadingProject = false`), no cuando la textura ya estaba subida a GPU y pintada — dejaba una ventana corta pero real donde se veía el `glClearColor` verde de "lienzo vacío" por detrás del overlay ya retirado | `GLRenderer.kt`, `GLPreview.kt`, `EditorScreen.kt` |

Ambos bugs comparten el mismo verde visible en pantalla (`CHROMA_KEY_GREEN_ARGB`,
ver `LayerDrawer.ensureInitialized` y `docs/adr/ADR-006-fondo-ajustado-al-lienzo.md`
para el bug hermano — asomo de verde en los BORDES de un fondo mal encuadrado,
ya cerrado antes de esta fase) pero son dos causas raíz completamente
distintas: acá el verde cubre el lienzo COMPLETO, no los bordes, y no
depende del encuadre de la imagen sino del estado de memoria/tiempo del
dispositivo en el instante de reabrir.

## 1. Bug real: `OutOfMemoryError` silencioso durante la decodificación al reabrir

`ImageDecoding.decodeSampledFromFile`/`decodeSampledFromUri` envolvían la
decodificación completa en `catch (t: Throwable)`. En Kotlin/JVM,
`OutOfMemoryError` es un `Error`, y `Error` hereda de `Throwable` — ese
`catch` también lo atrapaba, indistinguible de "el archivo está corrupto".
El resultado en ambos casos era el mismo: se registraba un log interno
(`AppLogger.e`, nunca visible para el usuario dentro de la app salvo que
abra el Registro de errores) y la función devolvía `null` — la capa
simplemente no se dibujaba, sin ningún aviso en pantalla.

Esto por sí solo ya era un riesgo latente, pero `ProjectStorage.loadProject`
lo convertía en un bug real y reproducible: para acelerar la apertura de
proyectos con varias capas, decodifica TODAS las capas en `async` +
`awaitAll`, corriendo en paralelo sobre el pool de `Dispatchers.IO` — a
propósito, documentado como mejora de rendimiento (el tiempo total pasa a
ser el de la imagen más lenta, no la suma de todas). El costo no
documentado de esa paralelización: el PICO de memoria en el instante de
reabrir es la SUMA de todas las decodificaciones corriendo a la vez, no
una por una — con el fondo típicamente siendo la capa más pesada (cubre
el lienzo completo a su resolución real). Si el dispositivo tenía poca
RAM libre en ese instante exacto (otra app en segundo plano, heap
fragmentado, etc.), una decodificación —incluida la del fondo— podía
superar el heap disponible y lanzar `OutOfMemoryError`. Como esa capa
quedaba descartada sin dejar ninguna textura, y no quedaba ninguna otra
capa cubriendo el lienzo, se veía el verde chroma-key por defecto de
"zona vacía" — el síntoma reportado. Al ser una condición de memoria del
dispositivo en un instante puntual, era intermitente por naturaleza: el
mismo proyecto podía abrir bien la mayoría de las veces y fallar
puntualmente, sin relación con el contenido del proyecto ni con cómo se
guardó.

**Corrección (`ImageDecoding.kt`):** un `OutOfMemoryError` real durante la
decodificación ya no se trata como "archivo roto" sino como "no hay
memoria AHORA para la resolución completa". Se reintenta hasta 4 veces,
duplicando `inSampleSize` (mitad de resolución) en cada intento, con
`System.gc()` entre intentos para darle al recolector una oportunidad
real de liberar memoria antes de reintentar. El peor caso posible pasa de
"la capa desaparece sin avisar" a "la capa se ve con menos resolución de
la ideal, pero se ve" — degradación elegante en vez de una capa fantasma.
Solo si ni el intento más chico (1/16 de la resolución original) entra en
memoria se devuelve `null`, con un log explícito y distinto del de
"archivo corrupto" para que el Registro de errores permita diferenciar
ambos casos.

**Corrección (`ProjectStorage.kt`):** se agregó un `Semaphore` que acota a
`MAX_PARALLEL_LAYER_DECODES = 3` cuántas decodificaciones de resolución
completa corren al mismo tiempo al reabrir un proyecto. Sigue siendo
paralelo (más rápido que decodificar una por una, la mejora que motivó el
`async`/`awaitAll` original se conserva), pero el pico de memoria pasa a
ser, como mucho, 3 capas a la vez en vez de todas juntas — el escenario
concreto que producía el `OutOfMemoryError` reportado.

## 2. Bug real: el overlay de carga se retiraba antes de que la GPU terminara de pintar

`EditorViewModel.isLoadingProject` pasa a `false` apenas
`ProjectStorage.loadProject()` termina de decodificar los bitmaps EN CPU
(ver ese archivo) — eso no significa que esas texturas ya estén subidas a
la GPU ni dibujadas en pantalla. `GLRenderer.uploadTextureIfNeeded` sube
una textura por capa por frame de forma no bloqueante desde el punto de
vista del hilo de UI, y `LayerDrawer.uploadTexture` hace un `glFinish()`
real (bloqueante para el hilo de GL, no para el de UI) que, con una
imagen de fondo pesada, puede tardar uno o varios frames de render.

`EditorScreen` ocultaba su overlay de carga (fondo verde + spinner) en el
instante en que `isLoadingProject` pasaba a `false` — el momento
equivocado: el correcto es cuando la textura real ya está lista y
pintada, no cuando el ViewModel terminó su parte. Durante esa ventana
—corta, del orden de milisegundos, pero perceptible— quedaba expuesto el
`glClearColor` verde intencional de "lienzo vacío" (ver
`LayerDrawer.ensureInitialized`) porque la textura real todavía no
estaba lista. Este es el "destello verde" reportado incluso en las
aperturas donde el proyecto termina cargando correctamente.

**Corrección (`GLRenderer.kt`):** se agregó `onAllVisibleLayersPainted`,
un callback que el renderer invoca UNA vez desde su propio hilo de GL,
recién cuando, dentro de un mismo `onDrawFrame`, TODAS las capas visibles
del snapshot vigente ya tienen una textura GL válida y se dibujaron de
verdad (se rastrea con `allVisibleLayersPaintedThisFrame` dentro del
bucle de dibujo, y con `hasSignaledAllLayersPainted` para que se dispare
una sola vez por creación de superficie — mismo criterio y mismo punto de
reset en `onSurfaceCreated` que ya usaba `hasLoggedFirstFrame`).

**Corrección (`GLPreview.kt`):** el callback de `GLRenderer` llega desde
el hilo de render de `GLSurfaceView`, nunca desde el hilo de UI — se
reenvía con un `Handler(Looper.getMainLooper())` antes de invocar la
lambda que recibe `EditorScreen`, porque tocar `MutableState` de Compose
solo es seguro desde el hilo principal.

**Corrección (`EditorScreen.kt`):** se agregó el estado
`canvasFullyPainted` (arranca en `false`, y se resetea a `false` cada vez
que arranca una carga nueva vía `LaunchedEffect(state.isLoadingProject)`
— sin este reset, reabrir un proyecto DESPUÉS de ya haber visto el primer
pintado saltaría directo a `true` y el bug reaparecería). El overlay
verde de carga ahora se mantiene puesto mientras CUALQUIERA de las dos
condiciones siga pendiente: `state.isLoadingProject` (decode en CPU) O
`!canvasFullyPainted` (pintado real en GPU) — el retiro del overlay pasa
a depender del momento correcto.

## Por qué son dos bugs distintos, no uno

El bug 1 explica por qué a veces el fondo NUNCA aparece (la capa se
descartó por falta de memoria, y ningún frame posterior la va a mostrar
hasta volver a abrir el proyecto). El bug 2 explica por qué, incluso en
una apertura EXITOSA (el bug 1 no se disparó, la capa sí se decodificó),
se alcanzaba a ver el verde por un instante antes de que apareciera el
fondo real. Corregir solo el 1 hubiera dejado el destello de milisegundos
intacto; corregir solo el 2 no hubiera evitado que el fondo desapareciera
del todo en el escenario de memoria insuficiente reportado en la captura
de pantalla original. Se corrigieron ambos.
