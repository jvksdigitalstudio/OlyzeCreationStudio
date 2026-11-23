# FASE 11 — El lienzo cambiaba de forma solo al reabrir un proyecto ("carga chica → carga grande")

## Estado
Corregido. Bug real, no de percepción — reportado con capturas por el
cliente: al abrir un proyecto guardado en un formato distinto del
default (su ejemplo: "IMAX"), el rectángulo del lienzo se ve primero con
una forma, y un instante después "salta" a la forma correcta.

## Causa raíz
`EditorUiState.canvas` arranca en un valor por defecto:
```kotlin
val canvas: CanvasSpec = CanvasSpec(widthPx = 1080, heightPx = 1920) // REELS
```
Ese default vive en memoria desde el primer frame en que se compone el
editor — **antes** de que `EditorViewModel.init` termine de leer
`ProjectStorage.loadProject(projectId)` (asíncrono) y recién ahí
reemplace `canvas` por el Canvas real que el proyecto tiene guardado
(p. ej. el preset "IMAX" elegido al crearlo).

El contenedor del lienzo, en `EditorScreen.kt`, leía ese `state.canvas`
sin ninguna condición:
```kotlin
Box(
    modifier = Modifier
        .aspectRatio(state.canvas.aspect.ratio)
        ...
```
Ya existía un overlay verde con spinner que tapa el CONTENIDO mientras
carga (fix de una auditoría anterior sobre el "flash verde") — pero ese
overlay usa `Modifier.fillMaxSize()`, así que toma la forma de lo que
sea que mida este Box padre en cada momento. Como el Box padre se seguía
midiendo con `state.canvas` **default** durante toda la carga, el
CONTORNO del lienzo (no su contenido) ya tenía la forma equivocada desde
el primer frame — y recién tomaba la forma correcta cuando `loadProject`
terminaba y `state.canvas` se actualizaba al valor real. Ese cambio de
forma, con el overlay ya puesto encima, es exactamente el "primero
chico, después grande" reportado.

## Corrección
`EditorScreen.kt`, el mismo `Box` del lienzo:
```kotlin
modifier = Modifier
    .let { base ->
        if (state.isLoadingProject) base.fillMaxSize()
        else base.aspectRatio(state.canvas.aspect.ratio)
    }
    .background(ChromaKeyGreen)
    ...
```
Mientras `state.isLoadingProject` sigue en `true`, el Box no adopta
NINGUNA forma específica de lienzo — llena el slot disponible completo,
sin sugerir ningún formato en particular (ni el default equivocado ni
uno inventado a ciegas). `isLoadingProject` y `canvas` se actualizan
juntos, en el mismo `copy()` de `EditorViewModel.init` (confirmado
leyendo ese bloque) — nunca llega uno sin el otro. Así que el único
cambio de forma que el Box puede llegar a mostrar es UNO solo, directo
al formato final y correcto del proyecto guardado, nunca "el chico,
después el grande".

## Por qué no es un parche
No se agregó un delay artificial, ni una animación que disimule el
salto, ni una segunda bandera de "ya casi" — se corrigió la única causa:
el Box no debe comprometerse con una forma de lienzo hasta que el dato
que la determina (`state.canvas`) sea el real. La condición usa el mismo
flag (`isLoadingProject`) que ya gobierna el resto del comportamiento de
carga en esta misma pantalla (overlay verde, `canvasFullyPainted`,
restauración de `handleOrderGlobal`/`handleOrderPerLayer`) — es
consistente con el criterio ya establecido, no uno nuevo.

## Verificación pendiente en dispositivo real
1. Crear un proyecto con un formato bien distinto al default vertical
   (p. ej. un preset ancho/cuadrado tipo "IMAX"), guardarlo, volver a
   "Mis proyectos".
2. Reabrirlo prestando atención al instante exacto en que aparece el
   lienzo: debe mostrarse SIEMPRE con la forma final correcta desde el
   primer frame visible — nunca una forma angosta/vertical que después
   se ensancha.
3. Repetir con un proyecto en el formato default (vertical 1080×1920,
   REELS) para confirmar que, al coincidir con el default, tampoco se ve
   ningún salto (el caso ya funcionaba bien y debe seguir así).
4. Repetir el mismo ciclo de FASE 10 (entrar/salir/reentrar al mismo
   proyecto varias veces sin cerrar la app) para confirmar que la
   corrección se sostiene también en reaperturas dentro del mismo
   proceso, no solo en la primera apertura tras iniciar la app.
