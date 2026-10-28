# FASE 20 — Auditoría completa del módulo de audio

> Alcance: `engine/audio/*`, integración en `EditorViewModel`, export
> (`AudioProcessor` + `VideoExporter`). **Sin compilador en esta sesión:**
> verificado por lectura completa y balance de llaves; la verificación real es
> `gradle testDebugUnitTest assembleDebug` (CI) y prueba en dispositivo.

## Hallazgos corregidos

| # | Sev. | Archivo | Problema | Corrección |
|---|------|---------|----------|------------|
| 1 | Alta | `AudioProcessor.decodeToPcm` | Se usaba el sample rate/canales de la PISTA, no los de salida real del decoder (HE-AAC/SBR/PS): el export sonaba a otra velocidad/tono. | Se lee `decoder.outputFormat` en `INFO_OUTPUT_FORMAT_CHANGED` (también `KEY_PCM_ENCODING`, float → 16-bit). |
| 2 | Alta | `AudioProcessor` (WAV) | `ByteArray(chunkSize)` con cabecera corrupta ≈2 GB lanzaba `OutOfMemoryError` (es `Error`: el `catch (Exception)` no lo atrapaba → cierre de la app). `data` de 0xFFFFFFFF (WAV en streaming) se leía como negativo y se descartaba. | Tamaños sin signo, lectura por bloques sin reservar según lo declarado, `data` 0/0xFFFFFFFF = hasta EOF, tope al bloque `fmt `, alineado a frames; `catch (OutOfMemoryError)` en decode/encode. Núcleo extraído a `decodeWavStream` (puro, testeado). |
| 3 | Media | `AudioProcessor` (decode y encode AAC) | Tras el EOS se esperaban solo 50×10 ms; en equipos lentos la cola del audio se cortaba en silencio. | Plazo por tiempo real sin salida (`DRAIN_TIMEOUT_NS` = 5 s). |
| 4 | Alta | `AudioPreviewPlayer` | `prepare()` SÍNCRONO en el hilo principal (el tick de reproducción): cada cambio de archivo congelaba cabezal/UI y un archivo ilegible repetía el bloqueo en cada tick. | `prepareAsync()` + estado `prepared`; silencio hasta estar listo; enfriamiento de 3 s tras un fallo. |
| 5 | Alta | `AudioPreviewPlayer` | El preview ignoraba los fundidos (volumen constante) mientras el export los aplica (por defecto 400/600 ms): lo que se oía no era lo exportado. | `fadeGainAt` (pura, misma curva que `applyVolumeAndFades`, con test de paridad) aplicada en cada `sync`. |
| 6 | Media | `AudioPreviewPlayer` | `seekTo(int)` usa `SEEK_PREVIOUS_SYNC`: en MP3/AAC/M4A caía en el fotograma anterior y disparaba la corrección de deriva repetidamente. | `seekTo(pos, SEEK_CLOSEST)` (minSdk 26). |
| 7 | Baja | `AudioPreviewPlayer` | `LoudnessEnhancer.setTargetGain` (binder) a ~20 Hz aunque no cambiara; sin `AudioAttributes`. | Solo si el refuerzo cambia; `USAGE_MEDIA`/`CONTENT_TYPE_MUSIC`. |
| 8 | Media | `EditorViewModel` | Importar, borrar, cortar, dividir, combinar, duplicar y pegar clips NO eran deshacibles (un toque accidental perdía el clip). | `pushUndoCheckpoint(force = true)` en cada operación estructural (el estado se relee después del checkpoint para no pisar `undoAvailable`). Volumen/pan/mute/fade siguen fuera del undo por diseño (ADR-012). |
| 9 | Media | `AudioProcessor.buildProjectSamples` | Un `arraycopy` por frame (millones de llamadas en proyectos largos). | Copia por tramos contiguos; mismo resultado. |
| 10 | Baja | `ReversedAudioCache` | Nombre de caché por `hashCode` de 32 bits (colisión → audio invertido de otro archivo); `.tmp` huérfanos nunca se borraban. | SHA-256 truncado; limpieza de `.tmp`. |
| 11 | Media | `AudioProcessor` (export multi-clip) | Se armaba un buffer del largo COMPLETO del proyecto por clip y se conservaban todos a la vez, más copias remuestreadas y la mezcla (~2N+1 buffers): en proyectos largos con varios clips agotaba la memoria. | Se decodifica solo la FUENTE de cada clip; luego clip por clip se lleva a formato común, se arma su porción y se suma en UN acumulador (`mixInto`). Mismo resultado (suma saturada en el mismo orden). |
| 12 | Media | `AudioTrackRow` (`ClipFadeOverlay`) | Las rampas de fundido dibujadas usaban el valor pedido sin acotar y terminaban en el borde del clip: no coincidían con lo que suena (el export acota cada fundido a la mitad del tramo audible y, sin loop, mide desde el final del audio). | Regla única `effectiveFadesMs` compartida por export, preview y UI; el overlay dibuja el tramo audible real. |
| 13 | Media | `EditorViewModel.importAudio` y `AudioApiImpl.setAudioClip` | Todo clip importado heredaba fade-in 400 ms / fade-out 600 ms y loop activo del modelo: efectos que el usuario no pidió (en un editor profesional son opcionales). | Los clips NUEVOS se importan ORIGINALES: sin fundidos y sin loop; se activan desde los módulos "Fade" y "Loop". Los clips ya guardados conservan sus valores. |

## Tests nuevos
`AudioFadeEnvelopeTest` (paridad preview/export y fundidos efectivos), `AudioWavStreamTest` (10 casos de cabeceras reales), `AudioMixIntoTest` (el acumulador equivale a `mixAdditive`).

## Límites conocidos (NO tocados — requieren fase propia)
- **Memoria del export multi-clip (reducida, no eliminada):** ya no se conserva un
  buffer de proyecto por clip, pero siguen en memoria las fuentes decodificadas de
  todos los clips mientras se mezcla. Solución de fondo: decodificación y mezcla en
  streaming por tramos.
- **Mezcla por saturación dura** (`mixAdditive`): clips solapados fuertes recortan.
- **Preview con un solo `MediaPlayer`:** con clips solapados suena solo el último.
- Un clip ilegible aborta TODO el audio del export (se informa al usuario).
- Sin manejo de foco de audio.
- Fundidos del preview se actualizan a ~20 Hz (aproximación; el export es exacto).
