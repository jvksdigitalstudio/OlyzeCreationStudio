# Auditoría de código real — 2026-10-08

Alcance: `app/` completo (193 archivos .kt), manifest, Gradle, CI. Verificación por análisis estático
(no hubo compilador ni SDK en el entorno de auditoría): llaves/paréntesis balanceados, 0 referencias
rotas a símbolos eliminados, manifest XML válido. **La compilación y los tests los confirma el CI.**

## Correcciones
| Sev. | Archivo | Problema | Corrección |
|---|---|---|---|
| Alta | `VideoExporter.kt` | `Done` se emitía antes de `muxer.stop()`: la UI podía usar un MP4 sin finalizar | `stop()` ocurre antes de `Done`; si falla, se reporta `Failed` |
| Alta | `VideoExporter.kt` | Tras un fallo quedaba un `.mp4` truncado en disco | Se borra también tras fallo, no solo al cancelar |
| Alta | `ExportApiImpl.kt` | `trySend` con buffer finito podía descartar el evento terminal | `conflate()`: se conserva siempre el último evento |
| Media | `VideoExporter.kt` | Retorno de `drainEncoder` ignorado en EOS; `stop()` sobre muxer no iniciado | Estado del muxer a nivel de función |
| Media | `VideoExporter.kt` | Espera infinita si el encoder no emite fin de stream | ~~Falla tras ~30 s~~ → rediseñado en FASE 24 / ADR-013: sin plazo arbitrario, salida por cancelación cooperativa |
| Media | `VideoExporter.kt` | Capa visible no decodificable se omitía en silencio | El export aborta con error explícito |
| Media | `AndroidManifest.xml` | Filtro `*/*` sin `host`: Android ignora `pathPattern` y la app se ofrecía para cualquier archivo | `android:host="*"` |
| Media | `ProjectStorage.kt` | `DESCRIPTION_MAX_LENGTH` no se aplicaba en ningún sitio | Se aplica en `sanitizeProjectData` y `renameProject` |

## Código muerto eliminado
- 2 bloques comentados en `EditorScreen.kt` (136 + 392 líneas).
- `AudioPanel`, `TimeRampPanel`, `formatTimecode`, `formatTimelineSeconds`, `selectedPanel`.
- `AVAILABLE_PROJECT_FPS`, `runCatchingLogged`, `layerTrackColorStrong`, `BrandBlueDeep`, `OlyzeGradientSubtle`, local `trackColor`.
- 35 imports sin uso; KDoc huérfano/corrupto.

## Pendiente (no modificado; requiere decisión o pruebas en dispositivo)
- `ProjectsViewModel.renameProject` no tiene llamador en la UI: la descripción no es editable.
- Funciones del `EditorViewModel` sin consumidor tras retirar el panel (velocidad/freeze).
- ProGuard: `-keep` de `engine.**` y `data.**` anula buena parte de R8; el CI no compila `release`.
- `allowBackup="true"` sin reglas de backup.
- `AudioProcessor` decodifica todos los clips a PCM antes de mezclar (pico de memoria; ya se acumula en un solo buffer con `mixInto`, la decodificación de las fuentes sigue siendo simultánea).
- `LayerSaveSnapshot` y `LayerContentState` duplican 22 campos; `EditorScreen.kt` tiene 18 k líneas.
