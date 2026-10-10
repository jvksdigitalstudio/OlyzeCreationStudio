# FASE 24 — Segunda auditoría: espera de codecs sin plazo arbitrario, código de producción solo de test y fuga de caché

**Método:** análisis estático sobre el código real (sin compilador en el entorno de auditoría). Los
cambios están sobre las funciones reales, sin capas de compatibilidad. **Compilación y tests: CI.**

## Correcciones
| Sev. | Archivo | Problema | Corrección |
|---|---|---|---|
| Alta | `AudioProcessor.decodeToPcm` | A los 5 s sin salida el bucle daba el decode por terminado: PCM truncado tratado como completo | Fin solo por EOS + cancelación cooperativa (ADR-013) |
| Alta | `AudioProcessor.encodeToAac` | Mismo plazo: AAC truncado en silencio | Ídem |
| Media | `VideoExporter.drainEncoder` | Plazo de ~30 s abortaba exports válidos lentos | Ídem; cancelación → `Cancelled` y borrado del parcial |
| Media | `AudioWaveform.load` | Decodificación no cancelable reteniendo el único candado de decode | Pasa `isActive` de la corrutina |
| Media | `AudioProcessor` (llamadores) | Un `null` por cancelación se reportaba como "no se pudo leer el audio" | Se distingue cancelación de error |
| Media | `MainActivity` | `cache/camera_captures/` acumulaba fotos de fondo para siempre | Purga de capturas superadas tras una captura exitosa (conserva la vigente) |
| Baja | `AudioProcessor.mixAdditive` | Código de producción sin uso salvo un test | Movido al test como oráculo (`AudioMixIntoTest.referenceMix`); KDoc de `mixInto` autocontenido |

## Revisado sin cambios
- `MainActivity`: import/export de `.olycs` ya corren en `Dispatchers.IO`; launchers y permisos correctos.
- Liberación de `MediaExtractor`/`MediaCodec` en `decodeToPcm`/`encodeToAac`: correcta (`finally`).

## Pendiente (sin cambios respecto a la auditoría del 2026-10-08)
ProGuard `-keep` amplio, `allowBackup`, decodificación simultánea de fuentes en la mezcla,
`ReversedAudioCache` sin cancelación (ADR-013), tamaño de `EditorScreen.kt`.

## Validación a cargo del equipo
`gradle testDebugUnitTest assembleDebug` (CI) y en dispositivo: exportar con audio largo,
cancelar durante "Procesando audio…" y durante el cierre del video, y verificar que no queda `.mp4` parcial.
