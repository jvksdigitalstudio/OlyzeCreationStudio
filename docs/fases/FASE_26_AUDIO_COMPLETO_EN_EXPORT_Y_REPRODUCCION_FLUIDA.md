# FASE 26 — Audio completo en el export y reproducción sin parones

**Origen:** reporte en dispositivo tras FASE 24/25 — *"exporto un audio largo y el video termina 28 s antes de
que acabe la música; además noto cortes/parones en la reproducción del audio dentro del proyecto"*.
**Método:** lectura del código real de todo el subsistema de audio (importación, timeline, reproducción en vivo,
mezcla, codificación, muxing y persistencia), reproducción matemática de ambos síntomas y verificación con
**compilador Kotlin real y JUnit** para todo lo que no depende del SDK de Android (281 tests ejecutados; ver
"Verificación"). Auditoría previa: [`AUDITORIA_PROFESIONAL_2026-10-08_R2.md`](../AUDITORIA_PROFESIONAL_2026-10-08_R2.md).

## Síntoma 1 — El export termina antes que la música (28 s)

### Causa raíz (verificada en el código)
1. La duración del proyecto (`projectDurationMs`) arranca en 60 s y **crece de a tramos de 1 minuto, solo cuando
   el cabezal se acerca al final** (`TimelineDurationManager.growIfApproachingEnd` / `ensureCapacityFor`).
2. `EditorViewModel.importAudio` crea el clip con la duración **completa del archivo** (`defaultClipLengthMs`) pero
   **nunca ampliaba el timeline**. Lo mismo ocurría al pegar/duplicar clips, moverlos y en `setAudioClipDirect`.
3. El export construye el audio **solo hasta `settings.durationMs` = `projectDurationMs`**
   (`AudioProcessor.buildProjectSamples`: `writableFrames = min(targetFrames − startFrame, clipLengthFrames)`),
   por lo que todo lo que el clip tuviera más allá se descartaba **sin ningún aviso**.

Ejemplo exacto del reporte: canción de 3:28 (208 s) en un timeline que había crecido a 3:00 (180 s) → el video y el
audio terminan 28 s antes que la música.

### Corrección (código real, un único punto de decisión)
| Archivo | Cambio |
|---|---|
| `EditorViewModel.ensureTimelineCoversAudio` (nuevo, privado) | Amplía el timeline hasta el final del último clip si lo sobrepasa. Única regla; reutiliza `ensureTimelineCapacityFor` (misma política de crecimiento que el cabezal). |
| `EditorViewModel.importAudio`, `addAudioClipCopy` (pegar/duplicar), `setAudioTimelineStart`, `setAudioClipDirect` (EliNer API) | Llaman a `ensureTimelineCoversAudio` **antes** de escribir el estado (se copia desde `_uiState.value`, no desde un `state` leído antes, para no pisar `projectDurationMs`). |
| `EditorViewModel.init` (carga) | **Repara proyectos ya guardados** cuyo audio sobrepasa el timeline: restaura la duración y la amplía si hace falta. Sin esto, los proyectos existentes seguirían exportando cortados. |
| `AudioProcessor.warnIfClipExceedsProject` (nuevo) | Si un clip sobrepasara igualmente la duración (p. ej. techo de 180 min), el recorte **ya no es silencioso**: queda en el log técnico con los ms descartados. |

El video resultante dura el timeline completo (múltiplo de minuto, como cualquier proyecto); el audio sale íntegro.
Ver [ADR-014](../adr/ADR-014-timeline-cubre-el-audio-y-reloj-de-reproduccion-sin-perdida.md).

## Síntoma 2 — Cortes/parones al reproducir el audio en el editor

### Causa raíz (verificada)
El bucle de reproducción calculaba el avance del cabezal con `(ahora − anterior) / 1_000_000` **en enteros**,
descartando la fracción de milisegundo de **cada tick**. Con ticks de ~16,4 ms (60 fps) se avanzaban 16 ms: el cabezal
iba **~2–3 % más lento que el reloj real** (y ~6–12 % a 120 fps). El audio del preview (`MediaPlayer`) corre en
tiempo real, así que se adelantaba al cabezal; al superar `DRIFT_TOLERANCE_MS` (300 ms), `AudioPreviewPlayer.sync`
corregía con un `seekTo` — **un salto audible cada pocos segundos** (cada ~10 s a 60 fps, ~5 s a 120 fps).

Segunda causa, menor: al pasar de un clip a otro (p. ej. las dos mitades de un clip dividido) `sync` hacía un seek
**forzado** aunque el audio ya sonara exactamente donde el clip nuevo lo necesitaba → un corte en cada unión.

### Corrección
| Archivo | Cambio |
|---|---|
| `engine/timeline/PlaybackClock.kt` (nuevo, puro) | Acumula la fracción sub-milisegundo entre ticks (`carryNanos`): el avance total converge exactamente al tiempo real. Mantiene el tope `MAX_TICK_MS` ante bloqueos del hilo. |
| `EditorViewModel.startPlaybackLoop` | Usa `PlaybackClock` en lugar de la división entera. |
| `engine/audio/AudioPreviewSeekPolicy.kt` (nuevo, puro) | `previewNeedsSeek(...)`: reposiciona solo si hace falta (arranque/seek explícito/reproductor detenido, o desvío > tolerancia). Al cambiar de clip rige una tolerancia estricta (`CLIP_JOIN_TOLERANCE_MS` = 80 ms): una unión continua no se toca; un tramo distinto del archivo se corrige al instante. |
| `AudioPreviewPlayer.sync` | Delega la decisión en `previewNeedsSeek`; elimina el seek forzado por `clipChanged`. |

## Limpieza (código muerto, duplicados y comentarios obsoletos)
| Elemento | Motivo |
|---|---|
| `AudioWaveform.sampleColumns` | Superada por `sampleColumnsLooped` (única usada en producción; con `loop=false` es idéntica). KDoc unificado y corregido (citaba `buildClipSamples`, inexistente). Tests migrados. |
| `DistortionField.reset` (+ su test) | Sin ningún llamador en producción. |
| `ZoomXLevels.nearestLevel` | Envoltorio trivial de `levels[nearestIndex(...)]` sin uso en producción. Tests migrados a `nearestIndex`. |
| `Vertex3D.boneIndices/boneWeights`, `Vec3.ZERO` | Campos "reservados" sin uso ni lectura (además `IntArray` en un `data class` daba `equals` por referencia). |
| `EditorViewModel`: `currentOutputDurationMs`, `speedAtPlayhead`, `addOrReplaceSpeedKeyframe`, `removeSpeedKeyframeAtPlayhead`, `addFreezeFrameAtPlayhead`, `removeFreezeFrame` | Entradas de edición de rampas/freeze **sin ningún llamador** (la UI que las usaba ya no existe). El motor (`SpeedRampEngine`), la persistencia y el export de rampas existentes **se conservan intactos**. Recuperables desde el historial si se decide restaurar el panel. |
| KDoc de `AnimationApiImpl` y `EditorViewModel` | Referencias a las funciones eliminadas, actualizadas. |

**Revisado y conservado a propósito:** `LayerGpuCommitGate.acquire/tryAcquire` (costuras de las pruebas
deterministas de contención), `PerspectiveCameraMath.depthCompensationFor/mapUvToPoint` (par matemático documentado),
`TimelineApiImpl`/`EliNerApiImpl` (frontera EliNer, ADR-004, documentada como sin consumidor externo).

## Tests nuevos / modificados
- `PlaybackClockTest` (7): sin deriva a 60/120 fps, acumulación sub-ms, tope por bloqueo, tiempo negativo, contrato.
- `AudioPreviewSeekPolicyTest` (5): arranque, deriva en régimen, unión continua sin seek, tramo distinto con seek.
- `TimelineDurationManagerTest` (+2): canción de 208 s ⇒ timeline de 240 s; reparación de proyecto guardado en 180 s.
- `AudioWaveformAnalyzerTest`, `ZoomXLevelsTest`, `DistortionFieldTest`: migrados/ajustados por la limpieza.

## Verificación
- **Ejecutado aquí (kotlinc 2.0.21 + JUnit 4.13.2):** todas las fuentes y tests sin dependencia del SDK de Android
  (81 archivos, **281 tests; 274 OK**). Las 7 fallas son `ProjectDataSerializationTest`, que exige el plugin del
  compilador de kotlinx-serialization (no aplicado en esta compilación ad-hoc; en Gradle sí lo está).
- **No verificable aquí (SDK de Android ausente):** `EditorViewModel`, `AudioProcessor`, `AudioPreviewPlayer`,
  `AudioWaveform` y los tests que los usan. **CI debe confirmar `gradle testDebugUnitTest assembleDebug`.**

## Validación a cargo del equipo (dispositivo)
1. Importar una canción **más larga que 1 min** (p. ej. 3:28) y exportar: el audio debe sonar completo y terminar
   sincronizado con el final del video; abrir el MP4 en otro reproductor y hacer seek.
2. Abrir un proyecto **guardado antes** con audio más largo que su timeline: debe exportar completo sin tocarlo.
3. Reproducir 2–3 min seguidos a 30/60/120 fps: sin saltos ni repeticiones audibles.
4. Dividir un clip en el cursor y reproducir cruzando el corte: sin corte audible en la unión.
5. Pegar/duplicar un clip hasta pasar el final del timeline: el timeline debe crecer solo.

## Pendiente (sin cambios, ver auditoría R2)
`targetSdk 34` (bloquea Google Play), monolitos `EditorScreen`/`EditorViewModel`, export sin servicio en primer plano,
pico de memoria al decodificar/mezclar audio largo o multi-clip (decodificación en streaming), EXIF, R8/`release` en CI.
