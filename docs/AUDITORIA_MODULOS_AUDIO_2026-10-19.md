# Auditoría de los módulos de audio — 2026-10-19

Alcance: `engine/audio` (mezcla, exportación, preview, cachés), `engine/audio/fx` (Reverb), `data/AudioEffectCodec`,
`api/audio`, operaciones de audio del `EditorViewModel` y la UI de audio (`ui/Audio*`, `ui/Reverb*`).

## Corregido (defectos reales)

| # | Archivo | Defecto | Corrección |
|---|---------|---------|------------|
| 1 | `ui/ReverbModulePanel.kt` | `verticalScroll` anidado dentro del de `FloatingToolWindow`: altura infinita → `IllegalStateException`, la app se cerraba al abrir Reverb. | El panel ya no scrollea; el scroll es solo de la ventana (igual que Zoom X, Recolor, Sombra, etc.). |
| 2 | `viewmodel/EditorViewModel.persistNow` | `catch (Throwable)` tragaba `CancellationException`: un autoguardado reemplazado se registraba como ERROR y dejaba `SaveState.Error`. | Se re-lanza la cancelación; los fallos reales no cambian. |
| 3 | `viewmodel/EditorViewModel.replaceAudioTracks` | Leía el estado antes del checkpoint de deshacer y luego lo copiaba: el checkpoint había escrito `undoAvailable = true` y se perdía (Deshacer no se encendía al cargar/quitar un Reverb o cambiar color). | El estado se lee después del checkpoint. |
| 4 | `engine/audio/TrackBakeCache` (hoy `DryTrackCache`) + `PreviewWavCache` | La purga por antigüedad (4 archivos) borraba el WAV de otro carril con efectos; con 5+ carriles con Reverb los renders se encadenaban sin fin (CPU/batería, silencios). | La purga es LRU real (`peek` marca uso) y nunca borra la mezcla vigente de cada carril. |
| 5 | `engine/audio/TrackBakeCache` (hoy `DryTrackCache`) | Carriles borrados o editor cerrado dejaban renders pendientes, claves y fallos en memoria (`failed` crecía sin límite). | `retain(liveTrackIds)` cancela y limpia; `failed` pasa a una clave por carril. |
| 6 | `engine/audio/TrackBakeCache` (hoy `DryTrackCache`) | Un render de preview fallido (p. ej. todos los clips silenciados) dejaba su mensaje como "último error visible" y la siguiente exportación lo mostraba como propio. | Un carril sin clips audibles no se hornea; si el render falla se restaura el error previo. |
| 7 | `engine/audio/AudioProcessor` | `lastFailureReason` era una variable global: el motivo de un hilo (waveform, preview, export) aparecía en el error de otro; además nunca se reiniciaba. | `ThreadLocal`, reiniciado al decodificar y antes del encoder. |
| 8 | `engine/audio/AudioProcessor` | Plantillas rotas `"$mix.channelCount"` en dos mensajes (imprimían el objeto + ".channelCount"). | `${mix.channelCount}`. |

Pruebas de regresión nuevas en `ReverbModuleStructureTest`.

## Revisado sin hallazgos

DSP del Reverb (`StereoReverb`, `Biquad`, `DelayLine`, `AllpassDiffuser`: coeficientes, interpolación Hermite, guardas de
buffer, anti-denormales, sin asignaciones por muestra, mix=0 bit-exacto); `ReverbParams`/presets/códec (saneado y rangos);
mezcla saturada, remuestreo y canales; decodificación (liberación de codec/extractor, gapless, float→16 bit); plan de
bucle y política de seek del preview; operaciones de rango y recorte; forma de onda (caché LRU, cancelación); capturas de
lambdas en gestos (todas con `rememberUpdatedState`); imports sin uso (ninguno); sin `!!`, `TODO` ni código muerto.

## Recomendaciones (no aplicadas: cambian diseño, no son correcciones quirúrgicas)

1. **Latencia al mover sliders del Reverb**: cada re-render decodifica de nuevo todos los clips y reprocesa todo el carril.
   Cachear el bus seco (mezcla sin efectos) por carril y reprocesar solo la cadena de efectos reduciría el tiempo de
   re-render al coste del DSP; exige un tope de memoria (un bus seco ocupa ~11 MB por minuto estéreo a 48 kHz).
2. **Memoria de la mezcla**: `buildProjectSamples` reserva un buffer del largo completo del proyecto por clip; un
   mezclador por bloques lo evitaría en proyectos largos.
3. **Cola del Reverb en la exportación**: el video dura lo que el proyecto; la cola que excede el final se corta (el
   preview la muestra completa). Es una limitación del formato, conviene documentarla al usuario.
4. **`ReversedAudioCache`** (6 archivos) tiene ahora LRU pero no protege claves en uso; con más de 6 fuentes invertidas
   distintas a la vez seguiría re-renderizando.

Verificación: este entorno no tiene Gradle ni red; las correcciones están revisadas a mano y las pruebas nuevas son
estructurales. Falta compilar (`./gradlew :app:testDebugUnitTest`) y probar en el dispositivo.
