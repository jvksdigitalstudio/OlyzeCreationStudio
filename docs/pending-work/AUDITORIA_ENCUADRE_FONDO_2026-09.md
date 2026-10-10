# Auditoría — Guía de encuadre/posicionamiento del fondo en el canvas

**Fecha:** 13 de septiembre de 2026
**Alcance revisado:** `AlignmentGuides.kt`, integración en `EditorScreen.kt`
(drag/resize/rotate), `LayerDrawer.kt` (cámara de perspectiva y
compensación de profundidad, ADR-007), `BackgroundAdjustDialog.kt` /
`CoverAdjustDialog.kt` / `ImageFitDialog.kt`, `BackgroundPickerSection.kt`,
`MainActivity.kt` (wiring de "Importar fondo"), `EditorViewModel.importAsBackground`,
ADR-006/ADR-007/ADR-009 y `docs/pending-work/PROJECT_AUDIT.md`.

**Evidencia de partida:** las 3 capturas del proyecto "Cr7" muestran el
verde chroma-key (`ChromaKeyGreen`, `0xFF00B140`) asomando alrededor del
fondo recién importado; el usuario lo termina tapando a mano, acercando
la imagen con el gesto de pellizco hasta cubrir el lienzo.

---

## 1. Diagnóstico

### 1.1 Causa raíz confirmada (bug real, corregido)

`EditorViewModel.importAsBackground(uri)` tiene **dos** puertas de
entrada:

| Camino | Antes de esta auditoría |
|---|---|
| "Elige un fondo" en **"Nuevo proyecto"** (`CreateProjectDialog`, `ProjectsScreen.kt`) | Correcto desde ADR-006: el `Uri` pasa por `BackgroundAdjustDialog` (encuadre obligatorio contra el `CanvasSpec`) antes de llegar a `importAsBackground`. |
| **"Importar fondo" del editor** (proyecto ya abierto, `MainActivity.kt` → `onImportBackgroundClick`) | ❌ Llamaba a `importAsBackground(uri)` **directo** con el `Uri` crudo del picker — el mismo bug que ADR-006 cerró para el otro camino, nunca cerrado acá. |

`LayerRepository.importAsLayers` sigue tomando el tamaño **natural** del
bitmap decodificado, sin relación con el lienzo del proyecto. Y
`LayerDrawer` ajusta cada capa con un fit tipo **"contain"** (no
"cover"): si `imageAspect ≠ viewportAspect`, dependiendo del eje, el
fondo se ve más chico que el lienzo y el verde de fondo (`ChromaKeyGreen`)
queda asomando — exactamente lo que muestran las capturas. Esto es
**independiente** del fix de profundidad de ADR-007: ahí el problema es
un paso *después* (una capa que YA cubre el 100% del lienzo se veía
encogida por la cámara de perspectiva); acá el bitmap nunca llegaba a
cubrir el 100% del lienzo desde el origen.

**Fix aplicado** (`MainActivity.kt`): el `Uri` que entrega el picker de
"Importar fondo" ya no llega directo a `importAsBackground` — se guarda
como pendiente y se abre `BackgroundAdjustDialog` contra el `CanvasSpec`
real del proyecto abierto (`viewModel.uiState.value.canvas`), mismo
patrón que ya usa "Nuevo proyecto". Solo al confirmar el encuadre, el
bitmap ya recortado/escalado al 100% del lienzo se persiste a disco
(`LayerRepository.saveBitmapAsLocalUri`, mismo mecanismo existente) y
*ese* `Uri` local llega a `importAsBackground`. Documentado en
**ADR-010** (nuevo), con referencia cruzada agregada en ADR-006 y en
`docs/pending-work/PROJECT_AUDIT.md`.

**Fuera de alcance, sin cambios (gap ya aceptado desde ADR-006):**
"Reemplazar imagen" sobre la capa de fondo (doble-tap) sigue sin forzar
encuadre — mismo comportamiento que cualquier otra capa. Documentado
explícitamente en ADR-010 como decisión consciente, no como bug nuevo.

### 1.2 `AlignmentGuides.kt` (la guía de encuadre en sí — arrastre/resize/rotación)

Auditoría completa de `computeAlignmentSnapForDrag`,
`computeAlignmentSnapForResize`, `snapRotationToFifteenDegrees`,
`solveScaleMagnitudeForTargetHalfExtent` y su integración en
`EditorScreen.kt` (los 3 bucles de gesto — drag, resize, rotate — y el
`Canvas` que dibuja las líneas). **No se encontró ningún bug**: la
matemática de snap a centro/bordes es correcta, el estado de guía activa
(`activeSnapGuideXPx`/`activeSnapGuideYPx`/`activeRotationSnapDeg`) se
resetea de forma consistente en cada punto de salida del gesto (incluida
la limpieza al soltar el dedo), y no hay fugas entre los distintos modos
de gesto. **Hallazgo de proceso:** no tenía ningún test a pesar de ser
100% función pura, sin dependencia de Android — se agregó
`AlignmentGuidesTest.kt` (18 casos: snap de centro/borde en drag, guard
de `parallaxFactor=0`/viewport sin medir, conservación de signo en
resize, snap/no-snap de rotación en ambos signos, resolución de escala
por interpolación lineal).

### 1.3 `LayerDrawer.kt` — compensación de profundidad (ADR-007)

Se verificó **matemáticamente** (simulación numérica de la proyección de
perspectiva completa, replicando `Matrix.perspectiveM`/`setLookAtM`) que
la fórmula `depthCompensation = (baseEyeZ - depthZ) / baseEyeZ` es
correcta: en reposo, **cualquier** `parallaxFactor` proyecta el borde de
una capa ya ajustada al 100% del lienzo exactamente en el borde del NDC
(±1), para cualquier combinación de proporción imagen/viewport, y el
plano de foco (`parallaxFactor=1.0`, el "sujeto") nunca cambia de tamaño
sin importar cuánto se anime el dolly zoom. **El fix de ADR-007 es
correcto y sigue vigente** — no es la causa de lo que muestran las
capturas (ver 1.1).

**Hallazgo de proceso:** esta matemática vivía inline en
`drawLayer()`, sin ningún test — a pesar de que el propio ADR-007 ya
señalaba como pendiente "confirmar que no rompe ningún test existente de
LayerDrawer/render" (no existía ninguno que romper). Se extrajeron las 4
funciones puras (`eyeZ`, `fovyDeg`, `depthZ`, `depthCompensation`) a
`engine/render/PerspectiveCameraMath.kt` — mismo patrón ya establecido en
el proyecto para separar motor puro/testeable de la capa que lo consume
(`CameraFrameInterpolation.kt`, `AlignmentGuides.kt`). `LayerDrawer`
no cambió de comportamiento, solo delega. Se agregó
`PerspectiveCameraMathTest.kt` (14 casos), incluyendo una reimplementación
en Kotlin puro del pipeline de proyección completo que fija por
regresión el bug exacto que ADR-007 corrigió — el test
`sin depthCompensation el bug de ADR-007 se reproduce` deja documentado,
de forma ejecutable, el valor numérico exacto del bug pre-fix (borde en
~0.681 en vez de 1.0 para el fondo por defecto).

### 1.4 `BackgroundAdjustDialog.kt` / `CoverAdjustDialog.kt` / `ImageFitDialog.kt`

Sin bugs encontrados. `cropToOutputBitmap` garantiza que la salida sea
**siempre** exactamente `outputWidthPx × outputHeightPx` — para
`BackgroundAdjustDialog`, eso es literalmente `canvas.widthPx` /
`canvas.heightPx` — así que, una vez que un `Uri` pasa por este diálogo,
`imageAspect == viewportAspect` por construcción y el fit "contain" de
`LayerDrawer` se vuelve, en la práctica, un "cover" exacto. Esto confirma
que cerrar la segunda puerta de entrada (1.1) es, en efecto, la
corrección completa del síntoma de las capturas.

---

## 2. Cambios aplicados

### Código
- `MainActivity.kt` — `onImportBackgroundClick` ya no llama a
  `importAsBackground` directo; enruta por `BackgroundAdjustDialog`
  contra el `CanvasSpec` del proyecto abierto.
- `engine/render/PerspectiveCameraMath.kt` (**nuevo**) — extracción pura
  y testeable de la matemática de cámara de `LayerDrawer`.
- `engine/render/LayerDrawer.kt` — refactor sin cambio de comportamiento:
  delega en `PerspectiveCameraMath`.

### Tests (nuevos, cero previos en ambas áreas)
- `app/src/test/.../ui/AlignmentGuidesTest.kt` — 18 casos.
- `app/src/test/.../engine/render/PerspectiveCameraMathTest.kt` — 14
  casos, incluida la regresión numérica completa de ADR-007.

### Documentación (actualizada para no quedar contradictoria con el código)
- `docs/adr/ADR-010-importar-fondo-editor-sin-encuadre.md` (**nuevo**) —
  diagnóstico y decisión del fix de 1.1.
- `docs/adr/ADR-006-fondo-ajustado-al-lienzo.md` — nota cruzada hacia
  ADR-010 (la puerta de entrada que ADR-006 no había cerrado).
- `docs/adr/ADR-007-compensacion-profundidad-parallax.md` — addendum
  documentando la extracción a `PerspectiveCameraMath` y los tests de
  regresión.
- `docs/pending-work/PROJECT_AUDIT.md` — entrada nueva en la sección de
  consistencia de hallazgos, mismo formato que las entradas previas de
  ADR-006/Fase 4.

## 3. Verificación pendiente (sin Android SDK/Gradle disponibles en esta sesión)

No fue posible compilar ni correr los tests en este entorno. Antes de
dar por cerrado:

1. `./gradlew testDebugUnitTest` — confirmar que `AlignmentGuidesTest` y
   `PerspectiveCameraMathTest` pasan y que nada existente se rompió.
2. `./gradlew assembleDebug` — confirmar compilación de `MainActivity.kt`
   con el nuevo wiring (imports de `BackgroundAdjustDialog`,
   `rememberCoroutineScope`, `Dispatchers`/`withContext`).
3. En dispositivo real: abrir un proyecto existente, tocar "Importar
   fondo", elegir una imagen con proporción distinta a la del lienzo, y
   confirmar que `BackgroundAdjustDialog` se abre ANTES de que la imagen
   toque el canvas — sin ningún fotograma intermedio con verde asomando.
   Reproducir el caso exacto de las capturas ("Cr7") y confirmar que ya
   no hace falta acercar el fondo a mano para taparlo.
4. Confirmar que "Cancelar" en ese diálogo deja el proyecto exactamente
   como estaba (sin capa nueva, sin archivo huérfano en disco).
