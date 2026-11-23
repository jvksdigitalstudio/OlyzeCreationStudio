# FASE 25 — Auditoría de seguridad del código real

**Alcance:** `app/src/main` (manifest, recursos XML, Kotlin), `app/src/test`, Gradle, CI.
**Método:** revisión estática del código fuente (sin compilador ni dispositivo en el entorno de
auditoría): superficie exportada, entradas no confiables (`.olycs` importado, intents), E/S de
archivos, rutas derivadas de datos externos, intents salientes/FileProvider, red, logging,
backup y cadena de suministro. **Compilación y tests: CI.**

**Modelo de amenaza principal:** un `.olycs` hostil (se recibe por mensajería/correo y se importa)
y apps de terceros que envían intents a la `MainActivity` exportada. La app **no declara
`INTERNET`**, no usa `WebView`, no ejecuta procesos ni carga código dinámico.

## Hallazgos corregidos
| Sev. | Archivo | Hallazgo | Corrección |
|---|---|---|---|
| Alta | `ProjectStorage.ensureLocalImage` / `ensureLocalAudio` | `LayerData.id` / `AudioClipData.id` (vienen de `project.json`) se interpolaban sin validar en `File(dir, "$id.png")` y `"audio_$id.ext"`: un id `../../x` hacía que el **guardado escribiera fuera de la carpeta del proyecto** | `assetFileStem(id)`: UUID legítimo intacto; cualquier otro id → hash SHA-256 determinista. Archivos existentes siguen resolviéndose |
| Media | `ProjectStorage.extractZipEntriesSafely` | `project.json` se lee entero a memoria (`readText()`); con el tope genérico de 300 MB un `.olycs` hostil provocaba `OutOfMemoryError` (DoS) | Tope propio `MAX_PROJECT_JSON_BYTES` = 32 MB para `project.json` |
| Media | `AndroidManifest.xml` | Filtros `VIEW` con `BROWSABLE` (una web podía disparar la importación vía `intent://`) y esquema `file://` | Se quitan `BROWSABLE` y `file`; `content://` (Gmail/WhatsApp/Drive) sigue funcionando |
| Media | `AndroidManifest.xml` | `allowBackup="true"` sin reglas: respaldaba el registro de errores (rutas/Uri/nombres del usuario) y la papelera | `backup_rules.xml` + `data_extraction_rules.xml`: nube solo preferencias; transferencia entre dispositivos incluye proyectos, excluye log, papelera y caché |
| Media | `.github/workflows/android-build.yml` | Sin bloque `permissions`: el `GITHUB_TOKEN` heredaba permisos amplios | `permissions: contents: read` |
| Baja | `ProjectStorage` (fallbacks de guardado) | `File(imgDir, prev.imageFileName)` y `File(audioDir, prev.audioFileName)` sin `resolveManifestFile` | Pasan por `resolveManifestFile` (defensa en profundidad; antes inalcanzable en la práctica) |
| Baja | `ProjectStorage.guessAudioExtension` | Extensión de 1–4 caracteres cualquiera: `x.a/b` generaba un destino con separador de ruta | `safeAudioExtension`: solo `a-z0-9` |
| Baja | `EditorViewModel.sanitizeFileName` | Nombre de export sin tope de largo ni filtrado de caracteres de control (fallo del export, no explotable) | Filtra controles y acota a 100 code points |
| Baja | `MainActivity` | Fotos de cámara acumuladas en `cache/camera_captures/` (ver FASE 24) | Purga de capturas superadas |

Tests nuevos: `ManifestAssetNamingSecurityTest` (ids hostiles, hash determinista, extensiones, tope
de `project.json` en la extracción real).

## Verificado sin hallazgos
- Zip Slip y zip bombs: `isSafeZipEntryName`, confinamiento por ruta canónica, límites de entradas/bytes/ratio.
- Rutas del manifest de capas, audio, portada y fotos de elenco: pasan por `resolveManifestFile`.
- `FileProvider`: `exported=false`, `grantUriPermissions`, rutas acotadas a `exports/` y `camera_captures/`.
- Todo el I/O de import/export corre en `Dispatchers.IO`; sin `SharedPreferences` world-readable, sin criptografía propia.
- Sin `INTERNET`, `WebView`, `Runtime.exec`, `Class.forName`, bibliotecas nativas ni SQL.

## Riesgo residual / recomendaciones (requieren decisión o verificación fuera del repo)
1. **Confirmación antes de importar:** un intent `VIEW` de cualquier app importa de inmediato (crea un proyecto nuevo, nunca pisa uno existente). Un diálogo de confirmación es decisión de producto.
2. **Cadena de suministro CI:** las acciones están fijadas por etiqueta (`@v4`, `@v3`); fijarlas por SHA de commit y añadir `distributionSha256Sum` al wrapper y `gradle/verification-metadata.xml` requiere acceso a red para obtener los valores oficiales (no se inventan aquí).
3. **ProGuard:** `-keep` de `engine.**`/`data.**` reduce el valor de R8 (ofuscación); el CI no compila `release`.
4. **Exportador:** `VideoExporter` decodifica cada capa a resolución completa (`decodeStream` sin muestreo) antes de acotarla a la textura máxima de la GPU; una imagen enorme dispara `OutOfMemoryError` capturado como `Failed`. Pasar a decodificación muestreada altera la calidad exportada: queda para una fase propia con pruebas en dispositivo.
5. **Exports en `getExternalFilesDir`:** los `.mp4`/`.olycs` exportados persisten hasta que el usuario los borre.

## Validación a cargo del equipo
`gradle testDebugUnitTest assembleDebug` (CI). En dispositivo: importar un `.olycs` legítimo
(abre y guarda), abrir un adjunto `.olycs` desde Gmail/WhatsApp (el filtro `content://` sigue
activo) y verificar que la app aparece en "Abrir con" solo para `.olycs`.
