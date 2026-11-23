# Auditoría profesional del código real — Olyze Creation Studio (R2)

**Fecha:** 2026-10-08 · **Alcance:** `app/` (≈74 600 líneas Kotlin, 193 archivos main + tests), Gradle, manifest, reglas de backup, CI.
**Método:** lectura y análisis estático del código fuente (no de README/ADR como fuente de verdad); escaneos programáticos de símbolos huérfanos, imports, `!!`, excepciones tragadas y patrones de I/O; verificación manual de cada hallazgo antes de tocar nada.

## Límites de esta auditoría (honestidad técnica)
- **No hubo compilador Kotlin/Gradle ni SDK Android en el entorno** (sin red). Los cambios se validaron por lectura, diff y balance sintáctico; **la compilación y los tests los debe confirmar el CI** (`gradle testDebugUnitTest assembleDebug`).
- **Nada se probó en dispositivo.** Los cambios de exportación requieren una exportación real (con y sin audio) en teléfono y tablet.

---
## 1. Correcciones aplicadas en esta ronda

| # | Sev. | Archivo | Problema verificado en el código | Corrección |
|---|---|---|---|---|
| F1 | Media | `VideoExporter.kt` | Las capas **ocultas** se decodificaban a resolución completa y se subían a GPU aunque nunca se dibujan (memoria de textura y arranque desperdiciados). | Se omiten antes de decodificar. Una capa visible no decodificable sigue abortando el export. |
| F2 | Baja | `VideoExporter.kt` | La cancelación no se comprobaba mientras se decodificaban/subían las capas (con muchas capas pesadas, la cancelación tardaba). | `isCancelled()` por capa. |
| F3 | Alta | `VideoExporter.kt` | No se validaba que el dispositivo pudiera codificar la resolución/fps/bitrate pedidos (4K a 80 Mbps, etc.): fallaba con una excepción críptica de `MediaCodec.configure()`. | `resolveEncoderBitRate()` consulta `MediaCodecList`: error claro si ningún encoder H.264 soporta tamaño+fps, y acota el bitrate al rango real. |
| F4 | Media | `VideoExporter.kt` | El retorno de `eglSwapBuffers` se ignoraba: un fallo EGL producía un video vacío/corrupto sin error. | Se verifica y se reporta con código EGL. |
| F5 | Media | `VideoExporter.kt` + `TimestampedQueue.kt` | El audio se escribía **entero después** del video (MP4 válido pero no intercalado: peor seek/reproducción progresiva en reproductores y redes sociales). | Intercalado por `presentationTimeUs` mediante una cola pura `TimestampedQueue` **con 6 tests unitarios**. |
| F6 | Baja | `GLRenderer.kt`, `VideoExporter.kt`, `LookSettings.kt` | El literal `33L` (ventana del motion blur) estaba duplicado en preview y export. | Constante única `MOTION_BLUR_LOOKBACK_MS`. (Se descartó cambiarlo a `1000/fps`: el valor fijo da el mismo desenfoque independientemente del fps y coincide con la vista previa.) |
| F7 | Baja | `AudioProcessor.kt` | Dos `!!` en la mezcla multi-clip. | Errores explícitos y diagnosticables vía `fail(...)`. |
| F8 | Baja | `ProjectsScreen.kt` | `catalog.findById(...)!!` sin mensaje. | `requireNotNull` con mensaje. |
| F9 | Baja | `EditorViewModel.kt`, `EditorScreen.kt`, `TimelineView.kt` | **Código muerto confirmado (0 llamadores en main y test):** `moveLayerUp`, `moveLayerDown` (sustituidos por reordenamiento por arrastre + `setLayerZIndex`), `setLayerColorIndex` (sustituido por rueda de color), `setParallaxFactor`, `removeKeyframeAtPlayhead` (existe `CameraApi.removeKeyframe`); `val effectsTopCategories` (lista `remember` nunca leída). | Eliminados; comentarios obsoletos actualizados; el mapa de índices de categorías queda documentado en su lugar. |

**Descartado tras verificar (para que no se repita):** `FilePreviewOverflowMenu` parecía código muerto en un escaneo pero **sí se usa**; `GpuTextureLimits.clampForTexture` **no** pierde memoria (recicla el original); `AppLogger` **sí** rota el archivo; la importación `.olycs` **sí** tiene límites anti zip-bomb (500 entradas, 300 MB/entrada, 1 GB total, JSON 32 MB); `depthCompensationFor` no está en uso pero tampoco hay duplicación: `LayerDrawer` y el overlay usan la misma función de nivel inferior.

---
## 2. Hallazgos pendientes (no aplicados) — por prioridad

### P0 — Bloquea publicar en Google Play
**`targetSdk = 34` / `compileSdk = 34`.** Según la página oficial de Google Play (*Target API level requirements*), desde el **31-ago-2026** las apps nuevas y las actualizaciones deben apuntar a **Android 16 (API 36)**. El plazo ya pasó: hoy no se podría subir el build. Migrar exige AGP/Gradle/Compose BOM más recientes (hoy AGP 8.3.2, Kotlin 2.0.21, BOM 2024.06) y **no se hizo a ciegas** porque no puedo compilar. Puntos de riesgo del salto a API 36: edge-to-edge obligatorio, back predictivo, y la restricción `screenOrientation="portrait"` deja de respetarse en pantallas grandes (relevante porque pruebas en tablet). Debe hacerse como fase propia con CI verde en cada paso.

### P1 — Alto impacto
1. **Monolitos de UI/estado.** `EditorScreen.kt` = 18 319 líneas (1 MB), con **una sola función `EditorScreen` de 6 100 líneas**; `EditorViewModel.kt` = 4 921 líneas (260 KB); `TimelineView.kt` 3 294, `ProjectStorage.kt` 3 081. Consecuencias: compilación lenta, recomposición difícil de acotar, riesgo de límite de método JVM, revisión de código impracticable. Plan sin reescritura ciega: extraer por **ventanas flotantes ya aisladas** (`RecolorFloatingWindow`, `ColorBasicoFloatingWindow`, `Basico3DFloatingWindow`, `Contorno/Resplandor/Sombra/Reflejo/DistortionFloatingWindow`, `EffectsCategory*`) a archivos propios sin cambiar firmas; luego partir `EditorViewModel` por dominio (capas, audio, cámara/keyframes, export, undo).
2. **Exportación sin servicio en primer plano.** No hay `Service`/`WakeLock`/`WorkManager` (verificado en código y manifest): si el usuario sale de la app durante un export largo, el proceso puede ser terminado y se pierde. Recomendado: `ForegroundService` tipo `mediaProcessing` con notificación de progreso.
3. **Pico de memoria en audio multi-clip.** `AudioProcessor.buildEncodedTrackForProject` decodifica a PCM **todas** las fuentes a la vez antes de mezclar (necesita conocer el sample rate máximo). Diseño: sondear sample rate con `MediaExtractor` primero y luego decodificar→remuestrear→mezclar **un clip a la vez**. Además `encodeToAac` mantiene todo el PCM y todos los chunks en RAM. No se aplicó por riesgo sin poder compilar/probar con audio real.

### P2 — Medio
4. **EXIF ignorado.** No hay ningún uso de `ExifInterface` en el proyecto; `BitmapFactory` no aplica la rotación EXIF. Fotos de cámara/galería en vertical podrían verse giradas (en editor y export). **Verificar en dispositivo con una foto de cámara**; si se confirma, normalizar al importar (ImageDecoder en API 28+, `ExifInterface` en 26–27).
5. **R8 prácticamente desactivado en release:** `-keep class ...data.** { *; }` y `...engine.** { *; }`; y el CI **nunca compila `release`**. Hay que estrechar las reglas (kotlinx.serialization trae las suyas) y añadir `assembleRelease` al CI antes de depender de ello.
6. **Acoplamiento global por mensaje de error:** `AppLogger.lastUserFacingError` (estado global mutable) se usa para pasar la razón del fallo de audio al exportador. Sustituir por un resultado tipado (`Result`/sealed class) devuelto por `AudioProcessor`.
7. **`TimelineApiImpl` nunca se instancia** (ni en producción ni en tests) y su KDoc dice "conectado de verdad"; `EliNerApiImpl` solo se usa en tests (documentado como deliberado). Decidir: cablear con test o eliminar; corregir la documentación.
8. **Speed ramp / freeze frame sin UI.** El estado se persiste y se exporta, pero no hay forma de crearlo desde la UI. Quedan sin llamadores `addOrReplaceSpeedKeyframe`, `removeSpeedKeyframeAtPlayhead`, `addFreezeFrameAtPlayhead`, `removeFreezeFrame`, `speedAtPlayhead`, `currentOutputDurationMs`. **No se borraron** porque es decisión de producto (restaurar UI o retirar la función de extremo a extremo: modelo, persistencia, export, `SpeedRamp`, tests).
9. **Exportación solo H.264, sin metadatos de color** (`KEY_COLOR_STANDARD/RANGE`) ni perfil/nivel: posibles diferencias de color entre dispositivos; HEVC como opción para 4K.

### P3 — Bajo / higiene
10. `String.format`/`"%.1f".format` sin `Locale` explícito en UI (`ProjectsScreen`, `TimelineView`, `FilePreviewDialog`, `EditorScreen`).
11. CI: acciones fijadas por tag (no por SHA), `setup-gradle@v3`; sin lint/detekt/ktlint ni análisis de dependencias.
12. Símbolos usados solo por tests (API reservada o muertos en producción): `AudioWaveform.sampleColumns`, `ZoomXLevels.nearestLevel`, `DistortionField.reset`, `LayerGpuCommitGate.acquire/tryAcquire`, `PerspectiveCameraMath.depthCompensationFor/mapUvToPoint`; sin uso alguno: `Mesh3D.boneIndices/boneWeights` (reservados), `Vec3.ZERO`.
13. `largeHeap="true"` enmascara los picos de memoria de los puntos 3 y 4.
14. (Reportado por la auditoría previa, **no re-verificado**): `LayerSaveSnapshot` y `LayerContentState` duplican ~22 campos.

---
## 3. Archivos modificados
`engine/export/VideoExporter.kt`, `engine/export/TimestampedQueue.kt` (nuevo), `test/.../engine/export/TimestampedQueueTest.kt` (nuevo), `engine/effects/LookSettings.kt`, `engine/render/GLRenderer.kt`, `engine/audio/AudioProcessor.kt`, `ui/ProjectsScreen.kt`, `ui/EditorScreen.kt`, `ui/TimelineView.kt`, `viewmodel/EditorViewModel.kt`.

## 4. Verificación requerida antes de dar por cerrado
1. CI verde: `gradle testDebugUnitTest assembleDebug` (incluye los 6 tests nuevos).
2. Exportar en dispositivo: 720p y 4K, con y sin audio, con capas ocultas, con 2+ clips de audio; confirmar sincronía A/V y que el MP4 abre/hace seek en varios reproductores.
3. En un equipo sin soporte 4K: debe verse el mensaje claro de F3, no un crash.
