# Auditoría técnica — Ventana ampliada de audio (2ª pasada)

**Fecha:** 2026-10-13 · **Continúa a:** `AUDITORIA_VENTANA_AUDIO_2026-10-11.md`
**Alcance:** `AudioClipDetailPanel/Gestures/Host/Playhead/Ruler/Selection/SelectionMath/TimeAxisMath/Math`, `AudioClipQuickBar`
(Nivel, Pan, Velocidad, Tono, REV, Cargar audio), `AudioFineWaveformCanvas`, `AudioWaveformView`, `FineWaveform`,
`AudioWaveform`, `AudioClipPreview`, `AudioPreviewPlayer(s)`, `AudioTimePitch`, `ReversedAudioCache`, ruta de export
(`AudioProcessor.buildProjectSamples/prepareDecoded`), setters de audio y undo de `EditorViewModel`,
`replaceAudioClipSource`, cableado en `TimelineView`/`EditorScreen`.

> **Estado de verificación.** El entorno de auditoría no tiene acceso a Google/Maven, por lo que **no se pudo compilar el APK**.
> Sí se compiló con `kotlinc 2.0.21` y se ejecutó con JUnit 4.13.2 toda la parte pura del audio (motor, matemática de la
> ventana, selección, undo de gestos): **233 pruebas, todas en verde**, antes y después de los cambios.
> Los cambios de Compose (UI) se verificaron por revisión estática y diff; **hay que compilar con GitHub Actions y probar en
> dispositivo** (ver §4).

## 1. Defectos corregidos

| # | Severidad | Defecto | Corrección |
|---|-----------|---------|------------|
| 1 | **Alta** | Los gestos de la onda (`pointerInput`) se declaraban con claves `(clip.id, viewportPx)`, pero su cierre captura los objetos de estado `zoom`, `scrollPx`, `ampZoom` y `selection`, que se **recrean** al cambiar el archivo (`clip.sourceUri`) o la reversa. Consecuencias: (a) tras **«Cargar o cambiar el audio»** el pellizco, el desplazamiento con dos dedos, la inercia y el doble toque escribían en estados descartados: la ventana dejaba de responder hasta cerrarla y reabrirla; (b) tras pulsar **REV**, arrastrar para seleccionar actuaba sobre una selección ya descartada: no se veía nada pero se enviaba al editor y acotaba la reproducción en bucle a un rango invisible. | Los dos `pointerInput` usan ahora `(gestureStateKey, viewportPx, gutterPx)`. `gestureStateKey` es la identidad de `selection`, cuyas claves son superconjunto de las de los otros estados (invariante documentado en el código). También se incluye `gutterPx` (capturado en el cierre). |
| 2 | Media | Con audios **demasiado largos para la onda fina** (> `FineWaveform.MAX_FRAMES` ≈ 3 min a 44,1 kHz, es decir canciones) —y mientras la fina carga— la ventana usa la onda del carril, que **ignoraba el zoom de amplitud**: el pellizco vertical solo cambiaba el rótulo «Amp». | `AudioWaveformCanvas` admite `amplitudeZoom` (1× por defecto: el carril no cambia) y la ventana le pasa `ampZoom`. |
| 3 | Baja | Esa misma onda recibía el desplazamiento como `scrollMs.toLong()`: la vista saltaba hasta ~1 px (1,2 px a zoom máximo) al desplazar. | `trimStartMs` de `AudioWaveformCanvas` pasa a `Double` (desplazamiento continuo). Se actualiza el único otro llamador (`AudioTrackRow`, `.toDouble()`). |
| 4 | Baja (limpieza) | Dos lambdas vacías `onLongPress = {}` obligatorias en `detectImmediateTapGestures`. | `onLongPress` pasa a tener valor por defecto `{}` y se eliminan las dos de relleno (Panel y QuickBar). |

| 5 | Media (visual) | **Señal «cortada» arriba y abajo** en audios a tope. Análisis con dos archivos: *Test 007.wav* (3,4 s, picos ≈ 77 % de la escala) se dibuja entero; *Apocalipsis estrella.wav* (2:24, master limitado) llega a 0 dBFS y su meseta coincidía con los dos bordes de la zona: arriba pegada a la regla y abajo tapada por el rótulo. El decodificador (`decodeWavStream`) y la pirámide `FineWaveform` NO saturan (16 bits literal; float/24/32 bits convertidos sin recorte); el aplanado es de la fuente, pero el diseño vertical hacía que pareciera un recorte del dibujo. | `DetailWaveformPadding` reserva una franja inferior de 24 dp para el rótulo (`DETAIL_INFO_STRIP_DP`) y `FINE_HEIGHT_FRACTION` baja de 0,92 a 0,86: 0 dBFS queda dentro de la zona con margen visible en ambos lados. La amplitud sigue siendo real y lineal. |
| 6 | Mejora (UX) | Al pulsar **REV** la selección se descartaba. | La selección se **refleja** sobre el centro del archivo (`[ini, fin)` → `[total − fin, total − ini)`), de modo que sigue abarcando el mismo fragmento de señal en su nueva posición, y se avisa al editor para que el bucle siga acotado a él. `mirrorDetailSelection` (pura, es su propia inversa) + `AudioClipDetailSelectionState.mirror` + `LaunchedEffect(clip.reversed)` que refleja solo ante un cambio real. La selección ya no depende de `clip.reversed` en sus claves (mismas claves que zoom/scroll/ampZoom, invariante intacto). 3 pruebas nuevas (`AudioClipDetailSelectionMirrorTest`). |
| 7 | Rediseño (UX) | La barra inferior (cargar audio, NIVEL, PAN, VELOCIDAD, TONO, REV) ocupaba el pie y no había forma de desplazar la vista con un solo dedo (un dedo selecciona; desplazar exigía dos). | **Cabecera** (`AudioClipDetailHeader.kt`, nuevo): Cargar/cambiar audio a la izquierda; REV, bucle y cerrar a la derecha. **NIVEL/PAN/VELOCIDAD/TONO eliminados** de la ventana junto con todo su código (`AudioClipQuickBar.kt` completo, deslizador propio, curvas y lecturas de nivel/pan/velocidad, íconos `ic_qa_level/pan/speed/pitch`, 4 campos de `AudioClipEditActions` y su cableado en `EditorScreen`, pruebas huérfanas). Quedan `detailSpeedLabel`/`detailPitchLabel` (rótulo de la onda). `quickToggleColors` → `detailToggleColors`, `ic_qa_reverse` → `ic_reverse_audio`. |
| 8 | Función nueva | **Barra de vista general** al pie (`AudioClipDetailOverview.kt` + `AudioClipDetailOverviewMath.kt`), según el modelo de WavePad (manual oficial NCH: la ventana superior «muestra la forma de onda siempre en toda su longitud», se puede tocar para saltar a otra zona; la inferior sigue el zoom; el desplazamiento horizontal lo da la barra bajo ella). Archivo entero + recuadro de lo visible (ancho = ventana ÷ zoom, sincronizado con pellizco, dos dedos, inercia, doble toque y cabezal), zona fuera oscurecida, selección y cabezal marcados. **Un dedo**: arrastrar el recuadro desplaza; tocar fuera lo centra ahí y seguir arrastrando continúa; arrastrar un borde acerca/aleja con la otra arista fija. 17 pruebas nuevas. `detailRestingHeadOf` es ahora la única fuente de la posición del cabezal (regla y barra). |

### Fuentes consultadas (documentación oficial de WavePad, NCH Software)
- Manual Windows, «Basics – Main Screen Reference»: ventana superior = archivo completo, tocar para saltar; ventana inferior = zoom; barra horizontal de desplazamiento bajo la inferior; Zoom a la selección; Mostrar región completa.
- Manual Android (`help.pdf`): mismos conceptos con gestos táctiles (pellizco para zoom, tocar la ventana superior para saltar).
- El manual NO especifica cómo se arrastra ni se redimensiona el recuadro de la vista general; ese comportamiento (arrastrar, bordes = zoom con arista opuesta fija) sigue la práctica de los editores profesionales (p. ej. «Zoom tool in the Overview» de WaveLab) y la captura de referencia.

### Consecuencia a tener en cuenta
VELOCIDAD y TONO se eliminaron por completo (no solo la interfaz): ver §5.

## 2. Archivos modificados

`AudioClipDetailPanel.kt`, `AudioWaveformView.kt`, `AudioTrackRow.kt`, `EditorScreen.kt` (cableado), `AudioClipDetailMath.kt`, `AudioClipDetailPlayhead.kt`, `AudioClipDetailHeader.kt` (nuevo), `AudioClipDetailOverview.kt` + `AudioClipDetailOverviewMath.kt` (nuevos), `AudioClipQuickBar.kt` (ELIMINADO),
`ImmediateTapGestures.kt`, `AudioFineWaveformCanvas.kt` (constante), `AudioClipDetailSelection.kt`, `AudioClipDetailSelectionMath.kt`, prueba nueva `AudioClipDetailSelectionMirrorTest.kt`, y este documento. No se tocó ningún otro archivo ni el modelo de datos.

## 3. Revisado y sin hallazgos

- **Matemática de ejes** (`detailXToSourceMs`/`detailSourceMsToX`, zoom anclado, scrub acotado, autodesplazamiento, regla y
  subdivisiones): consistente; cubierta por pruebas.
- **Selección** (borde agarrable, giro al cruzar el ancla, restauración tras doble toque) y su traducción a tiempo de proyecto.
- **Detector de gestos** (`detectDetailGestures`): una sola clase de gesto por contacto, inercia, orientación del pellizco fija.
- **Cabezal/regla**: consumo de toques, pausa/retoma de reproducción, dedo sobre la regla no activa selección ni doble toque.
- **Barra rápida**: curvas dB / logarítmica de velocidad, imanes, doble toque a valor de fábrica, un gesto = un paso de undo.
- **Motor**: mapeo trim/loop idéntico entre preview y export,
  reverse (se invierte antes de recorte/loop; `reversedTrimStartMs`), pan, normalización, `ReversedAudioCache`.
- **Reemplazo de audio**: conserva identidad, posición y mezcla; reinicia recorte/reversa/normalización; con undo.
- **Código muerto/duplicado**: barrido de imports y de declaraciones sin consumidores en todos los archivos de la ventana
  y del motor de audio asociado: 0 hallazgos.

## 4. Pendiente / recomendaciones (no aplicadas: fuera de alcance o decisión de producto)

1. **Validar en dispositivo** (no hay pruebas instrumentadas de Compose en el proyecto): abrir la ventana → «Cargar o cambiar
   audio» → pellizcar, desplazar con dos dedos y doble toque; seleccionar un rango → REV (la selección debe aparecer reflejada, sobre el mismo fragmento, y el bucle seguir acotado a él) → REV otra vez (vuelve a su sitio); abrir un audio > 3 min y pellizcar en vertical (debe crecer la onda).
2. `AudioWaveformAnalyzer.loadFine` decodifica el archivo completo antes de saber si supera `MAX_FRAMES`. Para audios de
   horas puede ser pesado; la forma de onda del carril ya hace esa misma decodificación, así que no es una regresión.
3. Arrastrar la selección contra el borde con zoom no desplaza la vista sola (sí lo hace el cabezal). Es una mejora de
   producto, no un error.
1b. Barra de vista general (nueva): con la ventana abierta, arrastrar el recuadro, tocar fuera, arrastrar un borde; probar a 1× (recuadro completo, sin bordes), con zoom alto (recuadro estrecho, se agarra igual) y con audio > 3 min.
4. `AudioClipDetailSelectionState` convive con su capa de dibujo en `AudioClipDetailSelection.kt`; separarla permitiría
   probarla sin Compose. Refactor opcional.
5. `EditorScreen.kt` (~18 000 líneas) sigue sin dividirse (riesgo de `VerifyError` ya documentado en el código).

## 5. Tercera pasada (2026-10-14): eliminación completa de VELOCIDAD y TONO

> Verificación: **sin compilar ni ejecutar pruebas** (no hay Gradle/SDK en el entorno de revisión). Cambios revisados por diff y por búsqueda de
> referencias; hay que compilar con GitHub Actions y ejecutar las pruebas antes de dar el cambio por bueno.

Decisión de producto: NIVEL, PAN, VELOCIDAD y TONO salen de la ventana del audio; REV y «cargar audio» pasan a la cabecera. De los cuatro
controles, **velocidad y tono se eliminan del programa entero**. Nivel y pan se conservan porque tienen otros llamadores vivos (ventanas
flotantes de volumen y balance del menú del clip).

| Capa | Eliminado |
|------|-----------|
| Modelo | `AudioClip.speed` / `pitchSemitones`; `MIN/MAX_AUDIO_SPEED`, `MAX_AUDIO_PITCH_SEMITONES`, `clampAudioSpeed/Pitch`, `timelineMsToSourceMs`, `sourceMsToTimelineMs`, imanes, `withSpeed`, `clipLengthAfterSpeedChangeMs` |
| Cálculos del clip | parámetro `speed` de `moveLeftEdge`, `sourcePositionAtProjectMs`, `sliceTrimStartMs`, `isExactContinuation`, `audibleSourceRangeMs`, `reversedTrimStartMs`, `audibleLengthMs`, `previewSelectionRangeOf`, `detailAudibleWindowMs`, `detailScrubTarget`; `canMergeWith` ya no compara velocidad/tono |
| Motor de audio | `AudioTimePitch.kt` (WSOLA + remuestreo), `AudioProcessor.applyTimePitch` y su tope de memoria, `PlaybackParams` en `AudioPreviewPlayer` |
| ViewModel | `setAudioSpeed`, `setAudioPitch`, estado del gesto, `monotonicMs`, campos de `AudioClipContentState` |
| Guardado y API | campos de `AudioClipData`, `AudioClipSaveSnapshot`, `AudioClipSnapshot`; saneado al cargar |
| Interfaz | rótulos «1.74×» / «+3 st», `detailSpeedLabel`, `detailPitchLabel`, parámetro `speed` de `AudioWaveformCanvas` |
| Pruebas | `AudioTimePitchTest`, `AudioClipSpeedTest`; casos de velocidad en `AudioClipDetailMathTest`, `AudioClipDetailTimeAxisMathTest`, `AudioClipPreviewTest` |
| Docs | `FASE_AUDIO_VELOCIDAD_TONO.md` |

**Compatibilidad:** `ProjectStorage` lee con `ignoreUnknownKeys = true`, así que un `project.json` antiguo que traiga `speed` o
`pitchSemitones` se abre igual y esos campos se descartan (se pierde solo ese ajuste; el audio suena a su velocidad y tono originales).

Otros cambios de esta pasada: la onda de la barra de vista general usa amplitud real (`trueAmplitude = true`, igual que la onda grande) y se
reescribieron comentarios que describían la barra eliminada o un diseño anterior.

### Revisado y sin hallazgos
Matemática de la barra de vista general; gestos (`awaitEachGesture` + `drag`, sin retardo); cabecera; `detailRestingHeadOf` como única fuente del
cabezal; cableado `EditorScreen` → `AudioClipEditActions` → `AudioClipDetailHost`; ningún `AudioClipQuickBar`, `quick*`, `ic_qa_*` ni cadena
huérfana.

### Pendiente
- Con navegación por gestos de Android, la barra de vista general queda en el borde inferior: validar en dispositivo que el gesto de «inicio» no
  compita con ella (si compite, añadir inset inferior de gestos del sistema).

## 6. Cuarta pasada (2026-10-14): panorama y volumen a la barra flotante

NIVEL (ventana ampliada) y el módulo Volumen eran el mismo valor: queda uno solo. Volumen y Pan salen del cajón Módulos y pasan a deslizadores
en la barra flotante de cada capa de audio (referencia FL Studio Mobile). Detalle en `fases/FASE_AUDIO_MEZCLA_EN_BARRA_FLOTANTE.md`.

| Capa | Cambio |
|------|--------|
| Interfaz | nuevo `AudioTrackMixSliders.kt`; `AudioTrackRow` (panel lateral y acordeón del pie); `LayerActionAccordion` + `rememberRowActionsPanelPlacement` con alto extra |
| Módulos | `AudioModuleId.VOLUME/PAN`, sus filas del cajón, sus flags/ventanas/z-index en `EditorScreen` eliminados |
| API de la fila | `AudioClipEditActions.onVolumeChange` / `onPanChange` → `setAudioVolume` / `setAudioPan` |
| Pruebas | `AudioTrackMixSlidersTest` |

> Sin compilar ni ejecutar pruebas (no hay Gradle/SDK en el entorno de revisión).

