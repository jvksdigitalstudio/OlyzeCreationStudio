# ADR-006 — Ajuste de encuadre para el fondo de "Nuevo proyecto" (fix del chroma-key verde asomando)

**Estado:** Decidido e implementado.
**Referencia:** `auditoria-crear-proyecto.md` (13 de septiembre de 2026).

## Contexto

El canvas del editor pinta cualquier zona sin capa en verde chroma-key
(`CHROMA_KEY_GREEN_ARGB`, `BackgroundPickerSection.kt`) — comportamiento
intencional, es el fondo por defecto de todo proyecto nuevo. El bug
reportado por el cliente no era ese verde en sí, sino que, al elegir
**Imagen** o **Cámara** como fondo en "Elige un fondo", la imagen se
importaba a su tamaño natural (`LayerRepository.importAsLayers` toma
`widthPx`/`heightPx` directo del bitmap decodificado, sin relación con
`CanvasSpec` del proyecto). Si la imagen no cubría el 100% del lienzo, el
verde de fondo quedaba asomando alrededor.

El camino de **Color** nunca tuvo este problema: `CreateProjectDialog`
genera el bitmap ya al tamaño exacto del canvas antes de confirmarlo.

## Decisión

1. **Reordenar el diálogo "Nuevo proyecto":** "Formato del lienzo" pasa a
   ir ANTES que "Elige un fondo". No es solo un cambio visual: hace falta
   conocer `canvas.widthPx`/`canvas.heightPx` para poder ofrecer un
   ajuste de encuadre correcto contra el lienzo.

2. **Nuevo paso de ajuste obligatorio para Imagen/Cámara:** al elegir
   cualquiera de las dos, el `Uri` resultante ya no se asigna directo a
   `backgroundChoice`. Se intercepta con `BackgroundAdjustDialog`, que
   obliga a encuadrar (arrastrar + pellizcar) la imagen contra la
   proporción exacta del lienzo elegido. Solo el resultado ya ajustado
   —que cubre el 100% del lienzo por construcción— se guarda a disco
   (`LayerRepository.saveBitmapAsLocalUri`, mismo mecanismo que ya usaba
   Color) y se asigna como fondo.

3. **Extracción de `ImageFitDialog`:** `CoverAdjustDialog.kt` ya
   implementaba exactamente el patrón de interacción necesario (gestos de
   pan/zoom con zoom mínimo "cubre el marco", overlay oscurecido fuera
   del marco, recorte exacto de lo que se ve en pantalla), pero atado en
   duro a la proporción de portada (`PROJECT_CARD_ASPECT_RATIO`, 9:14) y
   a una salida fija de 1000px de ancho. Se extrajo toda esa lógica a
   `ImageFitDialog.kt`, parametrizada por `targetAspectRatio`,
   `outputWidthPx`/`outputHeightPx` y `confirmButtonText`.
   `CoverAdjustDialog` quedó como wrapper delgado sobre `ImageFitDialog`
   fijando los valores de portada — **misma firma pública, mismo
   comportamiento, ningún llamador externo cambia**. `BackgroundAdjustDialog`
   es el segundo wrapper, con `targetAspectRatio = canvas.widthPx /
   canvas.heightPx` y `outputWidthPx/outputHeightPx = canvas.widthPx/heightPx`
   — a diferencia de portada, el fondo puede necesitar hasta la
   resolución completa del lienzo (ej. 1920×1342 en un formato IMAX).

4. **Selector de imagen del fondo de "Nuevo proyecto": SAF → Photo
   Picker.** `pickNewProjectBackgroundImageLauncher` (`MainActivity.kt`)
   pasa de `ActivityResultContracts.OpenDocument()` a
   `ActivityResultContracts.PickVisualMedia()`. Cambio acotado a ESE
   único launcher — los otros 7 launchers `OpenDocument()`/
   `OpenMultipleDocuments()` de la Activity no se tocan.

## Por qué no hay riesgo real de `content://media/picker` en `LayerRepository.decode()`

El Uri que entrega el Photo Picker nunca llega a
`LayerRepository.decode()` directamente: `ImageFitDialog` lo lee una sola
vez (vía `ImageDecoding.decodeSampledFromUri`, lectura inmediata sobre el
`ContentResolver`, dentro de la ventana de acceso transitorio que
garantiza el Photo Picker) y produce un bitmap ya ajustado, que se
persiste como archivo local (`file://`) antes de seguir camino hacia
`importAsBackground`. El `Uri` que finalmente llega a
`LayerRepository.decode()` es siempre local — mismo caso ya cubierto hoy
por el flujo de Color, con el mismo `catch (SecurityException)` ya
existente. Verificado igualmente en dispositivo real como parte de la
auditoría final (ver checklist de la sección 9 del documento de
auditoría).

## Fuera de alcance (sin cambios)

- `engine/scene/StaticFormatCatalog.kt` — los 6 formatos (Reels,
  TikTok, Stories, Shorts, Feed cuadrado, YouTube horizontal) ya estaban
  correctamente definidos; no había ningún trabajo pendiente ahí.
- Firmas públicas de `EditorViewModel.importAsBackground` /
  `LayerRepository.importAsLayers` — el fix ocurre antes de llegar a
  ellas, así que siguen funcionando igual para cualquier otro uso
  existente (importar capa, reemplazar imagen, etc.).
- Flujo de Color como fondo — no tenía el bug, no requería ajuste.
- Los demás 7 launchers de tipo `OpenDocument()`/`OpenMultipleDocuments()`
  de `MainActivity.kt`.

## Punto abierto, no implementado en esta ronda — RESUELTO, ver ADR-007

Este punto quedó registrado acá como un riesgo únicamente *durante la
animación*. La auditoría posterior (13 de septiembre de 2026, sesión de
soporte con capturas del cliente) encontró que el problema era más grave
de lo anotado: el verde aparecía **en reposo, en el frame 0, sin
animación corriendo** — no era un tema de margen de recorte insuficiente
para el desplazamiento del parallax, sino un bug de escala en la cámara
de perspectiva de `LayerDrawer.kt` que afectaba a CUALQUIER capa con
`parallaxFactor < 1`, incluso quieta. Ver ADR-007 para el diagnóstico y
el fix real (compensación de profundidad en `drawLayer`), que además
vuelve moot el problema de margen que este punto planteaba.

## Ver también — misma causa raíz, segunda puerta de entrada nunca cerrada (ADR-010)

Todo lo de este documento (encuadre obligatorio contra el lienzo) se
aplicó ÚNICAMENTE al camino "Elige un fondo" del diálogo "Nuevo
proyecto". `EditorViewModel.importAsBackground()` tiene una segunda
puerta de entrada, completamente separada, en el botón "Importar fondo"
del editor (proyecto ya abierto) — esa seguía llamando a
`importAsBackground` con el `Uri` crudo del picker, sin ningún encuadre,
con el mismo síntoma exacto (verde chroma-key asomando). Ver ADR-010 para
el diagnóstico y el fix de ese segundo camino.

## Ver también — bug hermano, causa raíz distinta (Fase 4)

Todo lo de este documento (y de ADR-007) trata el verde chroma-key
asomando en los BORDES de un fondo mal encuadrado o mal escalado. Existe
un bug hermano, con el mismo verde pero cubriendo el lienzo COMPLETO y de
forma intermitente (a veces el fondo carga bien, a veces no, al reabrir
un proyecto ya guardado) — causa raíz totalmente distinta: presión de
memoria durante la decodificación en paralelo al reabrir, no un problema
de encuadre. Ver `docs/fases/FASE_4_MEMORIA_Y_CARGA_PROYECTO.md`.
