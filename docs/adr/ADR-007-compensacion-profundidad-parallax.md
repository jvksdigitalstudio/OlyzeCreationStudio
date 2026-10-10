# ADR-007 — Compensación de profundidad en la cámara de perspectiva (fix del verde chroma-key asomando en reposo)

**Estado:** Decidido e implementado.
**Referencia:** ADR-006 (contexto original del bug), sesión de soporte del
13 de septiembre de 2026 (capturas del cliente sobre un proyecto IMAX
1920×1342, fondo ajustado con `BackgroundAdjustDialog`).

## Contexto

ADR-006 arregló que la imagen elegida como fondo se recorte y escale
EXACTAMENTE al tamaño del lienzo (`canvas.widthPx`/`heightPx`) antes de
guardarse. Esa parte funciona bien — se verificó que `ImageFitDialog` /
`cropToOutputBitmap` producen un bitmap con la proporción y resolución
correctas.

El cliente reportó el mismo síntoma (verde asomando alrededor del fondo)
en un proyecto donde el orden de pasos fue el correcto (formato IMAX
elegido primero, imagen ajustada después contra ese formato). Eso
descartaba la causa que ADR-006 había cubierto. La causa real estaba un
paso más adelante, en el motor de dibujo (`LayerDrawer.kt`):

`EditorViewModel.importAsBackground()` asigna `parallaxFactor = 0.35f` a
la capa de fondo. `LayerDrawer.drawLayer()` usa ese valor para calcular
la profundidad Z real de la capa dentro de una cámara de perspectiva:

```kotlin
val depthZ = (1f - parallaxFactor.coerceIn(0f, 1f)) * -1.8f
```

Con `parallaxFactor = 0.35f`, `depthZ ≈ -1.17`. La proyección de
perspectiva está calibrada (`fovyDeg` en función de `baseEyeZ = 2.5f`)
para que una capa en el plano `z = 0` (el "sujeto", `parallaxFactor =
1.0`) llene el viewport exacto — esa es la garantía de "cero regresión"
que ya documentaba el comentario de cabecera de la clase. Pero el
cálculo de escala que rellena el cuadro (`fitScaleX`/`fitScaleY`) solo
tenía en cuenta esa calibración para `z = 0`. Cualquier capa más atrás
en Z (cualquier `parallaxFactor < 1`, no solo el fondo) se proyecta más
chica que el viewport por perspectiva simple — el bitmap cubre el 100%
del lienzo por construcción, pero en pantalla se ve encogido, dejando
ver el verde de zona vacía del canvas alrededor. Esto pasa en el frame
0, en reposo, sin que haya ninguna animación de cámara corriendo — no es
un problema de margen de recorte insuficiente para el desplazamiento del
parallax (como se había anotado, sin implementar, en ADR-006).

## Decisión

Compensar la escala de cada capa según cuánto más lejos está su
`depthZ` respecto al plano de foco, medido contra `baseEyeZ` (la
distancia BASE de la cámara, no el `eyeZ` ya animado por `dollyZoom`):

```kotlin
val depthCompensation = (baseEyeZ - depthZ) / baseEyeZ
// ...
Matrix.scaleM(
    modelMatrix, 0,
    fitScaleX * depthCompensation * frame.scale * frame.scaleX,
    fitScaleY * depthCompensation * frame.scale * frame.scaleY,
    1f
)
```

Por qué contra `baseEyeZ` y no contra el `eyeZ` real (que sí cambia con
`dollyZoom`): si se compensara contra el `eyeZ` animado, el dolly zoom
dejaría de tener efecto visual sobre las capas de fondo — literalmente
anularía la función que motivó tener una cámara de perspectiva real en
primer lugar (ver comentario de cabecera de `LayerDrawer`: "el warp real
ocurre en las capas de fondo"). Compensando contra la distancia BASE:

- En reposo (`dollyZoom = 0`, `eyeZ = baseEyeZ`): `depthCompensation`
  cancela exactamente el encogimiento por perspectiva de CUALQUIER capa,
  sin importar su `parallaxFactor` — todas llenan el cuadro, verde
  invisible por defecto, como se espera de cualquier fondo/capa recién
  importada.
- Al animar `dollyZoom` (`eyeZ` se aparta de `baseEyeZ`): la
  compensación sigue fija en su valor de reposo, así que la capa SÍ
  cambia de tamaño relativo al sujeto — el efecto vértigo/parallax real
  que la cámara 3D fue diseñada para producir sigue intacto.
- En `z = 0` (`parallaxFactor = 1.0`, el "sujeto"): `depthZ = 0` →
  `depthCompensation = baseEyeZ / baseEyeZ = 1` → sin cambio alguno.
  Preserva, literalmente, la garantía de "cero regresión en el plano de
  foco" ya documentada en el código antes de este fix.

## Alcance del fix

Un solo punto de cambio: `LayerDrawer.drawLayer()`. Esta función la
comparten el preview en vivo (`GLRenderer`) y el exportador de video
offline (ver comentario de cabecera de `LayerDrawer`: "para que el
resultado final exportado sea pixel-idéntico al preview") — el fix
aplica a ambos caminos automáticamente, sin tocar `GLRenderer.kt` ni el
exportador.

No se tocó nada de ADR-006 (`ImageFitDialog`, `BackgroundAdjustDialog`,
`CreateProjectDialog`): ese trabajo seguía siendo necesario y correcto
para que el bitmap de fondo tenga la resolución/proporción exactas del
lienzo. Este ADR arregla lo que pasaba DESPUÉS, en el dibujo 3D de esa
capa ya correcta.

## Fuera de alcance (sin cambios)

- El valor `parallaxFactor = 0.35f` hardcodeado para el fondo en
  `importAsBackground` — sigue siendo el valor de diseño para el efecto
  de profundidad; este fix no lo cambia, solo corrige que ese valor ya
  no rompa el llenado del cuadro en reposo.
- `baseEyeZ = 2.5f` y `dollyRange = 1.6f` — valores de calibración de
  cámara ya existentes, no se tocaron.
- Cualquier capa con `parallaxFactor = 1.0` (comportamiento observable
  idéntico al de antes del fix, ver cálculo de arriba).

## Verificación pendiente en dispositivo real

Este fix se hizo sobre el código fuente sin acceso a un entorno con
Android SDK/Gradle para compilar y correr en dispositivo o emulador en
esta sesión. Antes de dar por cerrado el fix hace falta:

1. Compilar (`./gradlew assembleDebug`) y confirmar que no rompe ningún
   test existente de `LayerDrawer`/render.
2. Reproducir en dispositivo real el caso exacto reportado (proyecto
   IMAX, fondo ajustado, abrir el editor) y confirmar que el verde ya no
   aparece en reposo.
3. Confirmar visualmente que el dolly zoom sobre un proyecto con capas
   de distinto `parallaxFactor` se sigue viendo igual que antes del fix
   (mismo warp relativo entre capas).

## Addendum (auditoría de septiembre 2026) — extracción a `PerspectiveCameraMath` + tests de regresión

Esta ronda de auditoría (ver ADR-010 para el bug relacionado que motivó
volver a revisar este fix) encontró que la matemática de este ADR
(`eyeZ`, `fovyDeg`, `depthZ`, `depthCompensation`) vivía inline dentro de
`LayerDrawer.drawLayer()`, sin ningún test — a pesar del punto 1 de
"Verificación pendiente" de arriba, que ya asumía que debían existir
tests de esto. `LayerDrawer` depende de `android.opengl.Matrix`/`GLES20`,
no disponibles en un test JVM plano sin Robolectric, así que esas cuatro
funciones (puras, sin estado ni dependencia de Android) se extrajeron tal
cual a `engine/render/PerspectiveCameraMath.kt` — mismo patrón ya
establecido en el proyecto para separar motor puro/testeable de la capa
que lo consume (ver `engine/camera/CameraFrameInterpolation.kt`,
`ui/AlignmentGuides.kt`). `LayerDrawer.drawLayer()` no cambió de
comportamiento, solo delega en esas funciones.

Se verificó matemáticamente (y ahora queda fijado por test en
`PerspectiveCameraMathTest`, reimplementando en Kotlin puro la misma
matemática que `Matrix.perspectiveM`/`setLookAtM`) que la fórmula de
`depthCompensation` de este ADR es correcta: en reposo, cualquier
`parallaxFactor` proyecta el borde de una capa ya ajustada al 100% del
lienzo exactamente en el borde del NDC (±1), para cualquier combinación
de proporción de imagen/viewport, y el plano de foco (`parallaxFactor =
1.0`) nunca cambia de tamaño sin importar cuánto se anime `dollyZoom`. El
test `sin depthCompensation el bug de ADR-007 se reproduce` deja además
documentado, de forma ejecutable, el valor exacto del bug pre-fix (borde
en ~0.681 en vez de 1.0 para el fondo por defecto, `parallaxFactor =
0.35`) — si un refactor futuro vuelve a "olvidar" aplicar
`depthCompensation`, el test de regresión de arriba es el primero en
fallar.

