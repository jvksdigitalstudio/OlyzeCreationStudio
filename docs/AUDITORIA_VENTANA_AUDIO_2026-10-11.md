# Auditoría técnica — Ventana ampliada de audio y cadena asociada

**Fecha:** 2026-10-11 · **Alcance:** `AudioClipDetailHost/Panel/Gestures/Math`, `AudioClipQuickBar`,
`AudioFineWaveformCanvas`, `AudioWaveform`/`FineWaveform`, `AudioPreviewPlayer(s)`, setters de audio de
`EditorViewModel`, historial de deshacer, constantes de dominio de audio, y barrido de código muerto en todo `app/src`.

> **Estado de verificación.** Entorno de auditoría sin compilador Kotlin ni red: los cambios se verificaron por
> revisión estática (balance sintáctico, imports, símbolos huérfanos, diff completo). **Compilar y ejecutar
> `./gradlew testDebugUnitTest` antes de integrar.**

## 1. Defectos corregidos

| # | Severidad | Defecto | Corrección |
|---|-----------|---------|------------|
| 1 | **Alta** | `EditSnapshot` no incluía el audio: Velocidad, Tono, borrar/reemplazar clip, importar, etc. creaban pasos de deshacer que **no restauraban nada**; Nivel, Pan, Reverse, Mute, Loop, Fade y Normalizar ni creaban paso. | El snapshot guarda `audioTracks` (modelo inmutable → referencia segura); `restoreSnapshot` lo restaura y depura la selección de tiempo. Todos los ajustes de audio entran al historial. |
| 2 | Media | Un arrastre de fader > 0,6 s generaba varios pasos de deshacer (ventana fija `UNDO_MERGE_WINDOW_MS`). | Nueva `GestureCheckpointGate` (pura, reloj inyectable, con pruebas): **un gesto = un paso**, sin importar su duración. |
| 3 | Media | Tocar la onda para mover el cursor tenía ~300 ms de retardo (`detectTapGestures` + `onDoubleTap`). | `detectImmediateTapGestures` (ya existía en el proyecto): toque instantáneo, doble toque conserva el zoom. |
| 4 | Media | Igual retardo al tocar un deslizador de la barra rápida (Nivel/Pan/Velocidad/Tono). | Mismo cambio. |
| 5 | Media (visual) | Barra rápida: el botón **REV** quedaba pegado al borde derecho y la fila tenía scroll de sobra (el cálculo de ancho asumía 46 dp, el botón mide 54 dp). | Constante `QUICK_TOGGLE_WIDTH` única, usada por el cálculo y por el botón. |
| 6 | Media (rendimiento) | `AudioFineWaveformCanvas` creaba 2–3 `Path` nuevos por cuadro y calculaba dos veces los extremos de cada columna → presión de GC y tirones al pellizcar/arrastrar. | Buffers y `Path` reutilizados (`rewind()`); extremos calculados una sola vez. |
| 7 | Baja | Tope de volumen `1.5` duplicado en 5 sitios (VM, UI, mezcla, ventana flotante, constante UI); NaN no contemplado. | `MAX_AUDIO_VOLUME` + `clampAudioVolume()` como fuente única (maneja NaN/∞). |
| 8 | Baja | `setAudioVolume/Pan` aplicaban cambios idénticos (recomposición + autosave innecesarios). | Salida temprana si el valor no cambia. |
| 9 | Baja | `defaultClipLengthMs` conservaba 2 parámetros muertos con `@Suppress("UNUSED_PARAMETER")` "para no cambiar a los llamadores" (parche). | Parámetros eliminados; llamadores actualizados. |
| 10 | Baja | Reloj de pared (`currentTimeMillis`) para medir pausas de gesto. | Reloj monotónico. |
| 11 | Baja | 2 imports sin uso; comentarios que afirmaban "el audio queda fuera del undo" (falsos tras el fix). | Eliminados / corregidos. |

## 2. Archivos modificados

`AudioClip.kt`, `AudioProcessor.kt`, `AudioClipDetailMath.kt`, `AudioClipDetailPanel.kt`, `AudioClipQuickBar.kt`,
`AudioFineWaveformCanvas.kt`, `EditorScreen.kt` (1 línea + import), `TimelineView.kt` (import), `EditorViewModel.kt`;
nuevos: `viewmodel/GestureCheckpointGate.kt`, pruebas `GestureCheckpointGateTest`, `AudioVolumeClampTest`;
`AudioClipDetailMathTest` actualizado a la constante única.

## 3. Barrido de código muerto (todo `app/src`)

- 3 228 símbolos públicos/`internal` analizados: **0 sin consumidor** (única excepción: `LayerGpuCommitGate.tryAcquire`,
  usado solo por pruebas; se conserva).
- Símbolos `private` sin referencias: 0. Imports sin uso: 2 (corregidos).
- `GlobalScope`, `runBlocking`, `Thread.sleep`, `printStackTrace`, `println`, `Log.d/v`, `!!` en audio: 0.

## 4. Pendiente / fuera del alcance de esta pasada

1. `AudioTrackRow.kt` (1 998 líneas), `AudioProcessor.kt` (1 313), `EditorScreen.kt` (18 379) y
   `EditorViewModel.kt` (4 986) **no se auditaron línea a línea**; se revisó solo lo que toca la ventana de audio.
   `EditorScreen.kt` es un archivo monolítico (el propio código documenta riesgo de `VerifyError` por tamaño de
   funciones): se recomienda dividirlo por pantallas/paneles como trabajo planificado.
2. Tras un undo que revierte un clip invertido, el WAV invertido se regenera bajo demanda en el preview
   (silencio breve hasta que esté listo); el export no se ve afectado.
3. La ventana flotante de Volumen muestra %, la barra rápida muestra dB para el mismo valor; unificar es decisión de producto.
4. Validar en dispositivo: undo/redo de cada ajuste de la barra rápida, toque instantáneo sobre la onda,
   márgenes de la barra en teléfono y tablet.
