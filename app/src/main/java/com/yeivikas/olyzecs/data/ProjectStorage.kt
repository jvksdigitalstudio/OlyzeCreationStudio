package com.yeivikas.olyzecs.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.AudioProcessor
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.CameraTrack
import com.yeivikas.olyzecs.engine.camera.Keyframe
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.scene.AspectRatioPreset
import com.yeivikas.olyzecs.engine.scene.CanvasLimits
import com.yeivikas.olyzecs.engine.scene.CanvasSpec
import com.yeivikas.olyzecs.engine.scene.LegacyAspectMigration
import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.engine.core.ThumbnailRenderCapability
import com.yeivikas.olyzecs.engine.timeline.ThumbnailRenderer
import com.yeivikas.olyzecs.engine.timeline.TimelineLimits
import com.yeivikas.olyzecs.debug.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Tope de decodificaciones de imagen a resolución completa corriendo AL
 * MISMO TIEMPO al reabrir un proyecto (ver el comentario grande junto a
 * `layerDecodeSemaphore` en [ProjectStorage]). 3 es un valor conservador:
 * suficiente para seguir siendo notablemente más rápido que decodificar
 * una por una, sin acercarse al pico de memoria de "todas las capas a la
 * vez" que causaba el OutOfMemoryError intermitente.
 */
private const val MAX_PARALLEL_LAYER_DECODES = 3

/** Resumen liviano de un proyecto guardado, para pintar "Mis proyectos" sin decodificar nada pesado. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val description: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val layerCount: Int,
    val projectDurationMs: Long,
    val aspectRatio: String,
    val fps: Int,
    val thumbnailFile: File?,
    // Portada elegida a mano por el usuario (ver [ProjectStorage.setCoverImage]).
    // La tarjeta debe preferir SIEMPRE esta imagen sobre [thumbnailFile]
    // cuando esté disponible.
    val coverImageFile: File?,
    val orderIndex: Long,
    // Tamaño total en disco de la carpeta del proyecto (json + imágenes +
    // audio + miniatura/portada), para el panel de info (ícono ⓘ). Se
    // calcula al listar, no es carísimo porque son proyectos chicos, pero
    // si en el futuro se vuelve un problema de rendimiento se puede cachear.
    val sizeBytes: Long,
    // Año y categoría de la ficha del proyecto (ver
    // ProjectInfoPanel/EditorBottomBar.kt) — se muestran en el panel de
    // info (ícono ⓘ) de "Mis proyectos", junto al resto de detalles.
    val releaseYear: Int? = null,
    val genre: String? = null,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    // ADR-005, auditoría de Fases F/G: el Canvas real del proyecto, para
    // que "Mis proyectos" (`ProjectInfoOverlay` en `ProjectsScreen.kt`)
    // pueda mostrar el aspecto REAL (ej. "4:3", un Custom, etc.) en vez
    // de derivarlo del `aspectRatio: String` legacy (que solo puede
    // representar 3 categorías: REELS/SQUARE/WIDESCREEN — ver
    // `AspectRatioPreset`). `null` = proyecto sin Canvas propio todavía
    // (formato viejo, `canvasSchemaVersion == 0`) o con datos corruptos —
    // en ese caso la UI cae al `aspectRatio` legacy, igual que hacía
    // antes de este campo. Ya vienen saneados (ver [sanitizeProjectData]
    // en [listProjects]): nunca hay que reconstruir un [CanvasSpec] a
    // partir de estos campos sin volver a validar `> 0`.
    val canvasWidthPx: Int? = null,
    val canvasHeightPx: Int? = null,
    val canvasOriginPresetId: String? = null
) {
    /** La imagen que la tarjeta debe mostrar: portada elegida a mano si existe, si no la miniatura auto-generada. */
    val displayImageFile: File? get() = coverImageFile ?: thumbnailFile
}

/** Dirección para reordenar manualmente un proyecto en "Mis proyectos". */
enum class MoveDirection { UP, DOWN }

/**
 * Verifica que el nombre de una entrada de ZIP sea seguro para extraer,
 * cubriendo las DOS variantes conocidas de Zip Slip:
 *
 * 1. Recorrido con "..": una entrada como "../../evil.txt" o
 *    "subdir/../../evil.txt" contiene ".." en su ruta ya normalizada.
 * 2. Ruta absoluta: una entrada como "/data/data/<paquete>/evil.txt" NO
 *    contiene ".." en ningún lado — pasaría la regla 1 como "segura" — pero
 *    `File(destDir, childPath)` en Java/Android, cuando `childPath` YA es
 *    una ruta absoluta, **ignora `destDir` por completo** y resuelve
 *    directo a esa ruta absoluta. Es una variante distinta, igual de real,
 *    documentada en el aviso original de Zip Slip (Snyk/OWASP) — y NO
 *    estaba cubierta acá hasta esta revisión. HALLAZGO DE ESTA AUDITORÍA:
 *    ningún test existente (`ProjectStorageZipSlipTest`) armaba una entrada
 *    de ruta absoluta, así que el hueco pasaba en verde en CI.
 *
 * Esta función queda como el filtro RÁPIDO (sin tocar disco, para
 * descartar la enorme mayoría de casos maliciosos antes de gastar I/O).
 * La garantía DEFINITIVA — la que de verdad importa si algún día aparece
 * una tercera variante que ninguna de las dos reglas de acá arriba
 * contempla — es la verificación de ruta canónica en
 * [extractZipEntriesSafely], más abajo: esta función es una optimización,
 * no el único punto de defensa.
 *
 * Función pura (sin I/O, sin `Context` de Android) EXTRAÍDA A PROPÓSITO de
 * [extractZipEntriesSafely]/[ProjectStorage.importProjectZip] en la Fase A
 * de tests: es el único cambio de producción de esa fase, y existe
 * exclusivamente para poder probar la regla de seguridad en un test JVM
 * puro (`ProjectStorageZipSlipTest`), sin necesitar un `Context` Android ni
 * un dispositivo/emulador.
 */
internal fun isSafeZipEntryName(rawName: String): Boolean {
    val normalized = rawName.replace('\\', '/')
    return !normalized.contains("..") && !normalized.startsWith("/")
}

/**
 * FASE 1 (AUDITORÍA P0/P1) — SEGURIDAD DEL MANIFEST, path traversal.
 *
 * [isSafeZipEntryName]/[extractZipEntriesSafely] protegen la EXTRACCIÓN
 * del `.olycs` (el momento en que se escriben los archivos del zip a
 * disco). Pero eso NO protege lo que pasa DESPUÉS: `project.json` puede
 * declarar, para cualquier recurso (`imageFileName` de una capa,
 * `audioFileName`, `coverImageFileName`, nombres de fotos de elenco...),
 * un nombre de archivo arbitrario — y ese valor nunca pasaba por
 * `isSafeZipEntryName` ni por ninguna otra validación antes de usarse en
 * `File(directorioEsperado, nombreDelManifest)` para LEER el archivo.
 *
 * HALLAZGO REAL: un `.olycs` armado a mano (no necesariamente con una
 * entrada de zip maliciosa — el zip en sí puede ser 100% "legítimo") con
 * un `project.json` que declare, por ejemplo,
 * `"imageFileName": "../../../../data/data/com.yeivikas.olyzecs/shared_prefs/algo.xml"`
 * hace que `loadProject` intente abrir y decodificar ESE archivo como si
 * fuera la imagen de una capa — fuera por completo de `images/` del
 * proyecto. La extracción del zip nunca tocó esa ruta (no hace falta: el
 * ataque vive enteramente en el CONTENIDO de `project.json`, un archivo
 * de texto cuya extracción en sí es perfectamente legítima), así que las
 * protecciones de Zip Slip no alcanzan a cubrir este caso.
 *
 * Esta función es el segundo punto de defensa, para cualquier ruta que
 * venga del manifest: resuelve [fileName] dentro de [expectedDir] y
 * exige, por RUTA CANÓNICA (sigue symlinks, normaliza `.`/`..`), que el
 * resultado siga estando adentro de [expectedDir]. Se llama SIEMPRE
 * DESPUÉS de resolver la ruta y ANTES de tocar el archivo (abrir,
 * decodificar, listar existencia hacia la UI, etc.) — nunca se confía
 * solamente en que la extracción del zip haya sido segura.
 *
 * Devuelve `null` (en vez de lanzar) para que un único campo corrupto/
 * malicioso de un proyecto por lo demás legítimo no tire abajo la carga
 * completa — el llamador trata `null` igual que "archivo faltante" (ya
 * un caso manejado en todos los call sites existentes).
 */
internal fun resolveManifestFile(expectedDir: File, fileName: String?): File? {
    if (fileName.isNullOrBlank()) return null
    // Filtro rápido, mismo criterio que isSafeZipEntryName: descarta la
    // enorme mayoría de intentos sin tocar el sistema de archivos.
    if (!isSafeZipEntryName(fileName)) {
        AppLogger.w("ProjectStorage", "Nombre de archivo del manifest rechazado por inseguro: '$fileName'")
        return null
    }
    val expectedCanonical = expectedDir.canonicalFile
    val resolved = File(expectedDir, fileName)
    val resolvedCanonical = resolved.canonicalFile
    val confined = resolvedCanonical == expectedCanonical ||
        resolvedCanonical.path.startsWith(expectedCanonical.path + File.separator)
    if (!confined) {
        AppLogger.w(
            "ProjectStorage",
            "Ruta del manifest rechazada por resolver fuera del directorio esperado: '$fileName' -> '${resolvedCanonical.path}'"
        )
        return null
    }
    return resolved
}

/**
 * FASE 1 (AUDITORÍA P0/P1) — LÍMITES CONTRA ZIP BOMBS.
 *
 * Un `.olycs` es un zip normal: nada le impide a un archivo malicioso
 * declarar millones de entradas, o una sola entrada que descomprime a
 * gigabytes de datos desde apenas unos kilobytes comprimidos. La
 * protección Zip Slip (nombres/rutas de entrada) no cubre esto — cubre
 * A DÓNDE se escribe, no CUÁNTO.
 *
 * Los valores están pensados para un proyecto real y grande en este
 * formato (varias capas en resolución completa + audio + miniatura/
 * portada + fotos de elenco) sin ser tan laxos que un abuso obvio pase
 * desapercibido:
 *  - Una capa PNG a resolución completa puede pesar varias decenas de MB
 *    sin comprimir; con margen para varias capas + audio, 300 MB
 *    descomprimidos por entrada es generoso para cualquier proyecto
 *    legítimo y, a la vez, suficientemente acotado para no dejar
 *    reservar memoria/disco arbitrariamente.
 *  - El total descomprimido cubre un proyecto grande (decenas de capas)
 *    con margen amplio.
 *  - El número de entradas de un `.olycs` real es chico (unas pocas
 *    imágenes + audio + json + miniatura/portada + hasta 4 fotos de
 *    elenco) — unas pocas decenas como mucho; 500 es var veces ese
 *    número real sin abrir la puerta a un zip con millones de entradas
 *    vacías.
 *  - `MAX_COMPRESSION_RATIO` es la defensa específica contra "bombas"
 *    clásicas (una entrada minúscula comprimida que se infla a un
 *    tamaño absurdo): si una única entrada reporta expandirse muy por
 *    encima de lo que su tamaño comprimido explicaría, se corta la
 *    extracción antes de terminar de escribirla — no hace falta esperar
 *    a tocar el límite absoluto de tamaño por entrada para detectar el
 *    patrón.
 */
internal object ZipExtractionLimits {
    const val MAX_ENTRIES = 500
    const val MAX_UNCOMPRESSED_ENTRY_BYTES = 300L * 1024 * 1024 // 300 MB por archivo
    const val MAX_UNCOMPRESSED_TOTAL_BYTES = 1024L * 1024 * 1024 // 1 GB total
    const val MAX_COMPRESSION_RATIO = 200L // ratio descomprimido/comprimido
}

/** Se lanza cuando una extracción de ZIP excede alguno de los [ZipExtractionLimits] — ver [extractZipEntriesSafely]. */
internal class ZipBombSuspectedException(message: String) : java.io.IOException(message)

/**
 * FASE 1 (AUDITORÍA P0/P1) — VALIDACIÓN DE VALORES DEL MANIFEST.
 *
 * Satura los campos numéricos/estructurales de [ProjectData] (y de cada
 * [LayerData]) que el resto del código asume implícitamente dentro de un
 * rango razonable, para que un `project.json` corrupto o editado a mano
 * con valores absurdos (fps=0, fps=999999, duración negativa, columnas de
 * cuadrícula en 0, ángulo de degradado NaN/Infinity...) no produzca
 * crashes ni estados imposibles al cargar. NO reescribe el modelo ni
 * valida "todo" — deliberadamente acotado a los valores concretos que
 * pueden llegar a un divisor, un rango de UI, o un encoder de video.
 * Los proyectos legítimos existentes, con valores ya dentro de rango,
 * quedan bit a bit iguales (coerceIn de un valor ya válido es un no-op).
 */
internal fun sanitizeProjectData(data: ProjectData): ProjectData {
    val safeFps = data.fps.coerceIn(1, 240)
    val safeDurationMs = data.projectDurationMs.coerceIn(1_000L, TimelineLimits.MAX_DURATION_MS)
    val safePlayheadMs = data.playheadMs.coerceIn(0L, safeDurationMs)
    val safeGridColumns = data.gridColumns.coerceIn(1, 64)
    val safeGridRows = data.gridRows.coerceIn(1, 64)
    val safeGridLineThickness = if (data.gridLineThicknessDp.isFinite()) data.gridLineThicknessDp.coerceIn(0.1f, 32f) else 1f
    val safeGridLineOpacity = if (data.gridLineOpacity.isFinite()) data.gridLineOpacity.coerceIn(0f, 1f) else 0.4f
    val safeGridLineHue = if (data.gridLineHue.isFinite()) ((data.gridLineHue % 360f) + 360f) % 360f else 70f
    val safeLayers = data.layers.map { layer ->
        layer.copy(
            zIndex = layer.zIndex.coerceIn(-100_000, 100_000),
            parallaxFactor = if (layer.parallaxFactor.isFinite()) layer.parallaxFactor.coerceIn(-10f, 10f) else 1f,
            widthPx = layer.widthPx.coerceAtLeast(0),
            heightPx = layer.heightPx.coerceAtLeast(0),
            colorIndex = layer.colorIndex?.let { it.coerceAtLeast(0) },
            gradientAngleDegrees = layer.gradientAngleDegrees?.let { if (it.isFinite()) it else 90f }
        )
    }
    // Ver ADR-005, Fase C: un project.json corrupto o editado a mano
    // podría declarar `canvasSchemaVersion = 1` con un ancho/alto en 0 o
    // negativo — `CanvasSpec` rechaza esos valores en su `init` (ver
    // CanvasSpec.kt), así que sin este saneo `loadProject` reventaría por
    // completo en vez de simplemente re-migrar desde `aspectRatio`. Se
    // trata igual que cualquier otro campo numérico corrupto de acá
    // arriba: en vez de confiar ciegamente en el valor guardado, se
    // vuelve al estado "sin Canvas todavía" (`null` + schemaVersion 0)
    // para que `loadProject` migre de nuevo, en vez de propagar un
    // tamaño inválido o crashear.
    //
    // AUDITORÍA (corregido): el rango antes solo exigía `> 0` — un
    // project.json corrupto con, por ejemplo, `canvasWidthPx = 999999999`
    // pasaba el saneo intacto (`CanvasSpec.init` no rechaza valores
    // grandes, solo los `<= 0`), llegando después como Canvas real a
    // `hitTestBoxSize`/`GLPreview`/al encoder de exportación. Ahora se
    // usa el mismo rango que ya exige cualquier Canvas Custom creado
    // desde la UI (ver `CanvasLimits`/`DefaultCanvasFactory.custom`) —
    // un valor guardado que ya no lo cumple (por corrupción, o por venir
    // de una versión futura con límites distintos) se trata igual que
    // cualquier otro dato inválido: se re-migra desde `aspectRatio` en
    // vez de propagarlo.
    val canvasRangoValido = CanvasLimits.MIN_DIMENSION_PX..CanvasLimits.MAX_DIMENSION_PX
    val canvasWidthValido = data.canvasWidthPx?.takeIf { it in canvasRangoValido }
    val canvasHeightValido = data.canvasHeightPx?.takeIf { it in canvasRangoValido }
    val canvasEsValido = canvasWidthValido != null && canvasHeightValido != null

    return data.copy(
        fps = safeFps,
        projectDurationMs = safeDurationMs,
        playheadMs = safePlayheadMs,
        gridColumns = safeGridColumns,
        gridRows = safeGridRows,
        gridLineThicknessDp = safeGridLineThickness,
        gridLineOpacity = safeGridLineOpacity,
        gridLineHue = safeGridLineHue,
        layers = safeLayers,
        canvasWidthPx = if (canvasEsValido) canvasWidthValido else null,
        canvasHeightPx = if (canvasEsValido) canvasHeightValido else null,
        canvasOriginPresetId = if (canvasEsValido) data.canvasOriginPresetId else null,
        canvasSchemaVersion = if (canvasEsValido) data.canvasSchemaVersion else 0
    )
}

/**
 * Extrae hacia [destDir] todas las entradas de [zipIn] que sean archivos
 * (no directorios) y cuyo nombre sea seguro según [isSafeZipEntryName].
 * Devuelve `true` si se extrajo al menos un archivo.
 *
 * DEFENSA EN PROFUNDIDAD (hallazgo de esta auditoría, añadido junto con la
 * regla de ruta absoluta en [isSafeZipEntryName]): además del filtro rápido
 * de arriba, cada archivo resuelto se verifica por RUTA CANÓNICA —
 * `outFile.canonicalFile` debe seguir estando dentro de
 * `destDir.canonicalFile` — antes de escribir un solo byte. Esta es la
 * mitigación "definitiva" recomendada en el aviso original de Zip Slip: no
 * importa qué truco de nombre de entrada exista hoy o se descubra mañana
 * (".."., rutas absolutas, symlinks dentro del propio zip, mezcla de
 * separadores, etc.) — si el archivo resuelto no queda adentro del
 * directorio destino, la extracción de ESA entrada puntual se aborta y se
 * continúa con las demás (una entrada corrupta/maliciosa no debe tirar
 * abajo la importación completa de un proyecto legítimo).
 *
 * Función pura de I/O de archivos (`java.io.File`/`java.util.zip`, sin
 * `Context` de Android) EXTRAÍDA A PROPÓSITO de [ProjectStorage.importProjectZip]
 * en la Fase A de tests, por el mismo motivo que [isSafeZipEntryName]: es
 * el núcleo de seguridad de la importación de proyectos, y separarlo del
 * método de la clase (que sí necesita `Context` para resolver el `Uri` de
 * origen) permite construir un ZIP malicioso a mano en un JVM unit test y
 * verificar que ningún archivo termina fuera de [destDir] — no solo que se
 * "rechaza" la entrada, sino la propiedad de seguridad real.
 */
internal fun extractZipEntriesSafely(zipIn: ZipInputStream, destDir: File): Boolean {
    val destCanonical = destDir.canonicalFile
    var entry = zipIn.nextEntry
    var any = false
    var entryCount = 0
    var totalUncompressedBytes = 0L
    // Buffer chico y reutilizado: copiamos manualmente (en vez de
    // `copyTo` directo) para poder CONTAR bytes reales a medida que se
    // escriben y cortar apenas se supera un límite — sin esto, una
    // entrada-bomba podría escribir gigabytes a disco ANTES de que
    // cualquier chequeo posterior a `copyTo` tuviera oportunidad de
    // reaccionar.
    val buffer = ByteArray(64 * 1024)
    while (entry != null) {
        entryCount++
        if (entryCount > ZipExtractionLimits.MAX_ENTRIES) {
            throw ZipBombSuspectedException(
                "El archivo .olycs tiene demasiadas entradas (> ${ZipExtractionLimits.MAX_ENTRIES}) — se rechaza la importación"
            )
        }
        if (!entry.isDirectory && isSafeZipEntryName(entry.name)) {
            val outFile = File(destDir, entry.name.replace('\\', '/'))
            val outCanonical = outFile.canonicalFile
            if (outCanonical == destCanonical || outCanonical.path.startsWith(destCanonical.path + File.separator)) {
                val compressedSize = entry.compressedSize // -1 si el formato no lo declara (STORED puede no traerlo en algunos casos)
                outFile.parentFile?.mkdirs()
                var entryBytes = 0L
                outFile.outputStream().use { out ->
                    while (true) {
                        val read = zipIn.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        totalUncompressedBytes += read
                        if (entryBytes > ZipExtractionLimits.MAX_UNCOMPRESSED_ENTRY_BYTES) {
                            throw ZipBombSuspectedException(
                                "Una entrada del .olycs supera el tamaño descomprimido máximo permitido (${ZipExtractionLimits.MAX_UNCOMPRESSED_ENTRY_BYTES} bytes): '${entry.name}'"
                            )
                        }
                        if (totalUncompressedBytes > ZipExtractionLimits.MAX_UNCOMPRESSED_TOTAL_BYTES) {
                            throw ZipBombSuspectedException(
                                "El .olycs supera el tamaño total descomprimido máximo permitido (${ZipExtractionLimits.MAX_UNCOMPRESSED_TOTAL_BYTES} bytes)"
                            )
                        }
                        if (compressedSize > 0 &&
                            entryBytes > compressedSize * ZipExtractionLimits.MAX_COMPRESSION_RATIO
                        ) {
                            throw ZipBombSuspectedException(
                                "Ratio de compresión sospechoso en '${entry.name}' — posible zip bomb"
                            )
                        }
                        out.write(buffer, 0, read)
                    }
                }
                any = true
            } else {
                AppLogger.w(
                    "ProjectStorage",
                    "Entrada de ZIP rechazada por resolver fuera del directorio destino: '${entry.name}' -> '${outCanonical.path}'"
                )
            }
        }
        zipIn.closeEntry()
        entry = zipIn.nextEntry
    }
    return any
}

/**
 * Límite de caracteres para la descripción corta editable desde "Mis
 * proyectos" (diálogo de renombrar). Se cuenta en code points (no en
 * unidades UTF-16) para que un emoji fuera del BMP (la gran mayoría de los
 * emojis modernos: 😀🎬🚀...) cuente como UN carácter y nunca se corte a la
 * mitad de su par subrogado — ver [takeCodePoints].
 */
const val DESCRIPTION_MAX_LENGTH = 200

/**
 * Recorta [this] a lo sumo a [maxCodePoints] caracteres "reales" (code
 * points), a diferencia de `String.take(n)` que cuenta unidades UTF-16 y
 * puede partir un emoji compuesto por un par subrogado (o por
 * emoji+modificador de tono de piel, banderas de dos letras, ZWJ, etc.) por
 * la mitad, dejando un carácter "roto" (�) al final del texto guardado.
 */
fun String.takeCodePoints(maxCodePoints: Int): String {
    if (this.codePointCount(0, this.length) <= maxCodePoints) return this
    val end = this.offsetByCodePoints(0, maxCodePoints)
    return this.substring(0, end)
}

/**
 * Una capa en la papelera, ya resuelta para la UI (mismo criterio que
 * [LoadedProject.castPhotoFiles]: `ProjectStorage` resuelve rutas de
 * archivo con [resolveManifestFile], la UI nunca construye un `File` a
 * mano a partir de un nombre del manifest).
 *
 * `sizeBytes` viaja precalculado (en vez de que la UI lo calcule leyendo
 * `imageFile.length()` ella misma) para que la papelera pueda mostrar el
 * peso total sumando esta lista sin volver a tocar el disco.
 */
data class TrashedLayerInfo(
    val id: String,
    val name: String,
    val imageFile: File?,
    val deletedAtMs: Long,
    val sizeBytes: Long
)

/** Contenido completo de la papelera de un proyecto — ver [TrashedLayerInfo]. */
data class TrashSummary(
    val items: List<TrashedLayerInfo>,
    val totalSizeBytes: Long
)

/** Resultado de cargar un proyecto: metadata + capas + audio ya reconstruidos y listos para el ViewModel. */
data class LoadedProject(
    val id: String,
    val name: String,
    val projectDurationMs: Long,
    val layers: List<Layer>,
    val audioClip: AudioClip?,
    val speedKeyframes: List<SpeedKeyframe> = emptyList(),
    val freezeFrames: List<FreezeFrame> = emptyList(),
    // Deprecado (ver ADR-005, Fase C). Hasta la Fase G, `EditorViewModel`
    // todavía lo leía para restaurar `exportAspect` (usado solo para la
    // etiqueta "9:16" del diálogo de exportar) — desde la Fase G, esa
    // etiqueta se deriva directo de [canvas] (ver `ExportQualityPanel`),
    // así que este campo quedó puramente como frontera de compatibilidad
    // de lectura de `project.json` viejos (ver `ProjectData.aspectRatio`),
    // sin ningún consumidor que dependa de su VALOR en la app en vivo.
    @Suppress("DEPRECATION") val aspect: AspectRatioPreset = AspectRatioPreset.REELS,
    // El Canvas real del proyecto (ver ADR-005) — ya sea leído directo
    // de un project.json de esta versión, o migrado on-the-fly desde
    // [aspect] si el proyecto es de una versión anterior (ver
    // `LegacyAspectMigration` y el comentario en `ProjectData.aspectRatio`).
    // Siempre coherente con [aspect]: NUNCA puede haber acá un tamaño que
    // no corresponda al aspecto de arriba, porque ambos se derivan del
    // mismo dato en el momento de cargar.
    val canvas: CanvasSpec = CanvasSpec(widthPx = 1080, heightPx = 1920),
    val fps: Int = 30,
    // --- Metadata del panel "Información del proyecto" (ver comentario en
    // ProjectData) ---
    val releaseYear: Int? = null,
    val genre: String? = null,
    val infoDurationMinutes: Int? = null,
    // Siempre 4 elementos (uno por casilla), null = casilla vacía.
    val castPhotoFiles: List<File?> = listOf(null, null, null, null),
    // --- Guías de composición (cuadrícula) — ver comentario en ProjectData.
    // BUG REAL corregido: estos campos SÍ se guardaban en project.json
    // (ver saveProject/ProjectData más abajo), pero LoadedProject nunca
    // los exponía de vuelta al ViewModel — por eso la cuadrícula volvía
    // a los defaults cada vez que se reabría el proyecto.
    val gridEnabled: Boolean = false,
    val gridShapeName: String = "RECTANGLE",
    val gridColumns: Int = 3,
    val gridRows: Int = 3,
    val gridLineColorEnabled: Boolean = false,
    val gridLineHue: Float = 70f,
    val gridLineThicknessDp: Float = 1f,
    val gridLineOpacity: Float = 0.4f,
    val gridSnapEnabled: Boolean = false,
    // Ver comentario en ProjectData sobre el bug real que esto corrige.
    val handleOrderGlobal: Map<String, String> = emptyMap(),
    val handleOrderPerLayer: Map<String, Map<String, String>> = emptyMap(),
    // Ver comentario grande en ProjectData.playheadMs sobre el bug real
    // que esto corrige: dónde estaba parado el playhead del timeline
    // cuando se guardó el proyecto por última vez, para poder continuar
    // ahí mismo al reabrirlo en vez de siempre volver al segundo 0.
    val playheadMs: Long = 0L,
    // --- Papelera de capas (ver DeletedLayerData/TrashedLayerInfo) ---
    val trashedLayers: List<TrashedLayerInfo> = emptyList(),
    val trashSizeBytes: Long = 0L,
    // Regla completa (episodio nuevo vs. ya reconocido) resuelta ACÁ, en
    // la capa de datos, leyendo directo el flag persistido — así queda en
    // un único lugar, no repetida en el ViewModel (ver el comentario
    // grande de `trashBannerAcknowledged` en ProjectData).
    val showTrashRecoveryBanner: Boolean = false
)

/**
 * FASE 1 (AUDITORÍA P0) — CONSISTENCIA DEL GUARDADO.
 *
 * Snapshot INMUTABLE y estable de todo lo persistible de una capa,
 * construido por el LLAMADOR (ver
 * [com.yeivikas.olyzecs.viewmodel.EditorViewModel.persistNow]) ANTES de
 * cruzar a [Dispatchers.IO].
 *
 * BUG REAL corregido acá: `saveProject` recibía antes `layers: List<Layer>`
 * — los mismos objetos [Layer] mutables que el editor sigue usando en
 * vivo. Como `saveProject` hace `withContext(Dispatchers.IO)`, el
 * corrutine que llama queda suspendido pero el hilo de UI/Main SIGUE
 * libre para procesar otros eventos mientras el hilo de IO recorre esa
 * misma lista para serializarla — y algunos de esos eventos (undo/redo,
 * "Salir sin guardar") mutan esos MISMOS objetos `Layer` in-place (`var`
 * zIndex/lookSettings/etc., y el contenedor mutable de keyframes de
 * `CameraTrack`). El resultado es una condición de carrera real: el hilo
 * de IO puede leer un `Layer` a medio mutar — un campo ya actualizado por
 * el undo y otro todavía con el valor viejo — y escribir esa mezcla
 * inconsistente a `project.json`.
 *
 * La solución sigue el patrón pedido: ESTADO VIVO → CAPTURA CONTROLADA →
 * SNAPSHOT ESTABLE → IO/SERIALIZACIÓN. `saveProject` ahora solo acepta
 * este DTO inmutable para construir el manifest — nunca vuelve a leer un
 * campo `var` de un `Layer` en vivo desde el hilo de IO. La única
 * excepción deliberada es `liveLayersForThumbnail` (ver más abajo en
 * `saveProject`): renderizar la miniatura sí necesita objetos `Layer`
 * reales (usa `CameraTrack.frameAt`, el motor GL vía `LayerDrawer`, etc.
 * — no es "solo datos", es comportamiento del motor de render, fuera del
 * alcance de esta fase migrar). Ese camino conserva, a sabiendas, un
 * residuo mucho más chico del mismo riesgo: en el peor caso la MINIATURA
 * puede quedar con un frame visual ligeramente desactualizado si hay una
 * mutación justo en el instante del render — nunca una escritura
 * inconsistente de `project.json`, que es lo que de verdad importa para
 * la integridad del proyecto. Ver el informe técnico de FASE 1, sección
 * "Riesgos restantes".
 */
data class LayerSaveSnapshot(
    val id: String,
    val sourceUri: Uri,
    val name: String,
    val zIndex: Int,
    val parallaxFactor: Float,
    val locked: Boolean,
    val orderLocked: Boolean,
    val visible: Boolean,
    val lookSettings: LookSettings,
    val keyframes: List<Keyframe>,
    val baseFrame: CameraFrame,
    val widthPx: Int,
    val heightPx: Int,
    val colorIndex: Int,
    val customColorArgb: Int?,
    val importedDefaultColorArgb: Int?,
    val customGradientStartArgb: Int?,
    val customGradientEndArgb: Int?,
    val useGradientColor: Boolean,
    val gradientAngleDegrees: Float,
    val gradientIsRadial: Boolean,
    val useBlackAndWhiteMode: Boolean
)

/** Construye la foto inmutable de esta capa para pasar a [ProjectStorage.saveProject] — ver [LayerSaveSnapshot]. */
fun Layer.toSaveSnapshot() = LayerSaveSnapshot(
    id = id,
    sourceUri = sourceUri,
    name = name,
    zIndex = zIndex,
    parallaxFactor = parallaxFactor,
    locked = locked,
    orderLocked = orderLocked,
    visible = visible,
    lookSettings = lookSettings,
    keyframes = cameraTrack.keyframes.toList(),
    baseFrame = cameraTrack.baseFrame,
    widthPx = widthPx,
    heightPx = heightPx,
    colorIndex = colorIndex,
    customColorArgb = customColorArgb,
    importedDefaultColorArgb = importedDefaultColorArgb,
    customGradientStartArgb = customGradientStartArgb,
    customGradientEndArgb = customGradientEndArgb,
    useGradientColor = useGradientColor,
    gradientAngleDegrees = gradientAngleDegrees,
    gradientIsRadial = gradientIsRadial,
    useBlackAndWhiteMode = useBlackAndWhiteMode
)

/** Mismo criterio que [LayerSaveSnapshot] pero para el clip de audio de fondo (también mutable, mismo riesgo de aliasing). */
data class AudioSaveSnapshot(
    val sourceUri: Uri,
    val displayName: String,
    val sourceDurationMs: Long,
    val volume: Float,
    val muted: Boolean,
    val trimStartMs: Long,
    val loop: Boolean,
    val fadeInMs: Long,
    val fadeOutMs: Long
)

fun AudioClip.toSaveSnapshot() = AudioSaveSnapshot(
    sourceUri = sourceUri,
    displayName = displayName,
    sourceDurationMs = sourceDurationMs,
    volume = volume,
    muted = muted,
    trimStartMs = trimStartMs,
    loop = loop,
    fadeInMs = fadeInMs,
    fadeOutMs = fadeOutMs
)

/**
 * Resultado de [ProjectStorage.saveProject]. Además del nombre efectivo
 * (ver el KDoc original de `saveProject`), devuelve las referencias de
 * archivo local RECIÉN resueltas para imágenes/audio que se acaban de
 * copiar — para que el llamador (EditorViewModel) pueda aplicar ese
 * `sourceUri` local de vuelta a los `Layer`/`AudioClip` EN VIVO, pero
 * recién DESPUÉS de que la IO terminó (secuencial, sin carrera) y solo si
 * ese campo no cambió mientras tanto (ver `persistNow`) — así los
 * próximos autoguardados no vuelven a copiar desde el Uri original de SAF
 * en cada tick, sin reintroducir el aliasing que esta misma fase corrige.
 */
data class SaveProjectResult(
    val effectiveName: String,
    val resolvedLayerImageUris: Map<String, Uri>,
    val resolvedAudioUri: Uri?
)

/**
 * FASE 4.2 (AUDITORÍA P0 — corrección real, ver puntos 4.1/4.3/5 del prompt
 * maestro de esta fase). Se lanza desde [ProjectStorage.saveProject] cuando
 * un asset (imagen de una capa o audio de fondo) no pudo asegurar una copia
 * local válida en este intento de guardado Y tampoco existe ninguna copia
 * previa válida a la que volver.
 *
 * Antes de esta fase, ese escenario NO lanzaba nada: la capa (o el audio)
 * simplemente se omitía del `project.json` que se terminaba escribiendo —
 * un fallo transitorio de I/O (permiso SAF revocado, storage lleno, red
 * lenta si el origen es remoto) se convertía en pérdida PERSISTENTE y
 * silenciosa de contenido del usuario.
 *
 * Con esta excepción, `saveProject` aborta ANTES de escribir ningún byte de
 * `project.json` nuevo — el archivo en disco queda exactamente como estaba
 * en el último guardado válido (ver `EditorViewModel.persistNow`, que
 * atrapa esta excepción, la reporta vía `SaveState.Error` y nunca ejecuta
 * el callback de éxito de `saveNow`).
 */
class ProjectAssetIntegrityException(message: String) : Exception(message)

/**
 * FASE 4.2 (AUDITORÍA P0 — puntos 4.2/4.3 del prompt maestro): pieza de
 * integridad de archivo compartida por [ProjectStorage.ensureLocalImage] y
 * [ProjectStorage.ensureLocalAudio]. Copia lo que devuelva [openInput] a un
 * archivo TEMPORAL junto a [destFile], verifica que la copia esté completa
 * y no vacía, y SOLO ENTONCES reemplaza [destFile] mediante `renameTo`
 * (atómico dentro del mismo directorio/volumen).
 *
 * Si algo falla en cualquier paso — [openInput] lanza, la copia queda
 * vacía, el rename falla incluso tras reintentar — devuelve `false` y
 * [destFile] (si ya existía) queda exactamente como estaba antes de la
 * llamada: nunca se destruye un archivo destino válido a partir de un
 * fallo de la copia nueva. Nunca deja un archivo temporal parcial
 * rondando.
 *
 * Deliberadamente sin ninguna dependencia de `Context`/`Uri` de Android:
 * es la propiedad de integridad real bajo prueba, y así se puede verificar
 * con un unit test JVM puro (sin Robolectric) — ver
 * `AtomicFileReplaceTest` en `src/test`.
 */
internal fun copyToFileAtomically(destFile: File, openInput: () -> InputStream): Boolean {
    val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp-${System.nanoTime()}")
    val copied = runCatching {
        openInput().use { inp -> tmpFile.outputStream().use { out -> inp.copyTo(out) } }
    }
    if (copied.isFailure || !tmpFile.exists() || tmpFile.length() == 0L) {
        runCatching { if (tmpFile.exists()) tmpFile.delete() }
        return false
    }
    if (!tmpFile.renameTo(destFile)) {
        // Un solo reintento — mismo criterio que la escritura atómica de
        // `project.json` en `saveProject`. Nunca se borra `destFile` a mano
        // para "forzar" el rename: si la copia anterior seguía ahí, sigue
        // siendo mejor que ningún archivo.
        if (!tmpFile.renameTo(destFile)) {
            runCatching { if (tmpFile.exists()) tmpFile.delete() }
            return false
        }
    }
    return true
}

/**
 * FASE 4.2 (AUDITORÍA P0 — puntos 4.1/5 del prompt maestro): la regla de
 * decisión "reutilizar la copia previa válida o abortar el guardado",
 * aislada como función pura para poder testearla directamente (ver
 * `AssetFallbackOrAbortTest`) sin depender de `Context`/`Uri` de Android.
 * Usada por [ProjectStorage.saveProject] tanto para capas como para el
 * audio de fondo: nunca hay una tercera opción ("omitir en silencio") — o
 * se usa la copia nueva, o se usa una copia previa que sigue siendo
 * válida, o se aborta el guardado entero lanzando
 * [ProjectAssetIntegrityException].
 */
internal fun <T> resolveAssetOrAbort(fresh: T?, previousValid: T?, abortMessage: () -> String): T {
    if (fresh != null) return fresh
    if (previousValid != null) return previousValid
    throw ProjectAssetIntegrityException(abortMessage())
}

/**
 * Persistencia de proyectos. Cada proyecto vive en su propia carpeta dentro
 * del almacenamiento privado de la app —
 * `/data/data/<pkg>/files/projects/<projectId>/` — con cuatro cosas:
 *
 * - `project.json`: metadata del proyecto + todas las capas (transform,
 *   keyframes, look cinematográfico por capa) + el clip de audio si hay uno
 * - `images/<layerId>.png`: copia LOCAL de cada imagen de capa
 * - `audio/<archivo>`: copia LOCAL del audio de fondo, si el proyecto tiene uno
 * - `thumbnail.jpg`: miniatura ya "graduada" (con el look aplicado),
 *   generada con [ThumbnailRenderer]
 *
 * ## Por qué se copia cada imagen/audio en vez de guardar solo su Uri original
 * Un Uri de Storage Access Framework puede dejar de ser válido si el
 * usuario mueve, borra o desinstala la app que compartió el archivo
 * originalmente, o si Android revoca el permiso persistente — y ahí el
 * proyecto quedaría con capas o audio "rotos" e irrecuperables. Copiando el
 * archivo dentro de la carpeta del propio proyecto, éste queda 100%
 * autocontenido: sobrevive reinicios, cambios en la galería del usuario, e
 * incluso se podría exportar la carpeta entera a otro dispositivo.
 *
 * Todas las operaciones son suspend y corren en [Dispatchers.IO].
 */
/**
 * HALLAZGO DE ESTA AUDITORÍA — escritura atómica incompleta: la Fase 1
 * había corregido `project.json.tmp` + `renameTo` ÚNICAMENTE dentro de
 * [ProjectStorage.saveProject] (ver el comentario original, ahora movido
 * acá), pero [ProjectStorage] tiene otros 7 sitios que TAMBIÉN escriben
 * `project.json` directamente — `renameProject`, `setCoverImage`,
 * `setCoverImageBitmap`, `removeCoverImage`, `setCastPhoto`,
 * `removeCastPhoto` y `moveProject` (dos escrituras, una por cada
 * proyecto involucrado) — y todos seguían usando `file.writeText(...)`
 * directo, sin ninguna de las dos protecciones que motivaron la Fase 1:
 * ni atomicidad frente a un lector concurrente, ni (la más grave)
 * atomicidad frente a que el sistema operativo mate el proceso a mitad
 * de la escritura (memoria baja, "forzar detención", actualización de la
 * app). Con `writeText` directo, esa muerte a mitad de camino puede dejar
 * `project.json` truncado — y un JSON truncado no es "el cambio que se
 * estaba guardando se perdió", es `loadProject` fallando el parseo por
 * completo y devolviendo `null`: EL PROYECTO ENTERO queda inaccesible la
 * próxima vez que el usuario intenta abrirlo, no solo el rename/portada/
 * foto de elenco/reorden que estaba en curso. Ahora los 9 sitios de
 * escritura de `project.json` de esta clase (los 7 de arriba + los 2 de
 * `saveProject`) pasan por esta única función.
 *
 * Debe llamarse siempre bajo `mutexFor(projectId).withLock { }` (todos
 * los llamadores ya lo hacen) — esta función solo se ocupa de la
 * atomicidad a nivel de sistema de archivos frente a un crash/kill, no de
 * la exclusión mutua entre corrutinas de la propia app (eso ya lo
 * garantiza el mutex).
 */
class ProjectStorage(
    context: Context,
    // FASE 4.3 (RENDER BOUNDARY, R4.3.7/R4.3.8 del prompt maestro de Fase
    // 4.3): antes esta clase (Infrastructure/Data) llamaba directo a
    // `ThumbnailRenderer.render(...)` — el `object` concreto que conoce
    // EGL/GLES — exactamente el problema que describe R4.3.8:
    // "Infrastructure → GL/EGL renderer" en vez de
    // "Infrastructure → resultado Render". Ahora depende de la interfaz
    // `ThumbnailRenderCapability` (`engine.core`, mismo criterio que
    // `PixelColorSource`), con `ThumbnailRenderer` como valor por
    // defecto — mismo objeto, mismo comportamiento en runtime, cero
    // cambios para el único sitio de construcción real
    // (`MainActivity.kt`). `ProjectStorage` sigue sin conocer CÓMO se
    // genera la miniatura, solo que algo que implementa esta interfaz
    // se la puede dar.
    private val thumbnailRenderer: ThumbnailRenderCapability = ThumbnailRenderer
) {

    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true // tolerante a versiones futuras del formato
        prettyPrint = true
        encodeDefaults = true
    }

    private val projectsRoot: File
        get() = File(appContext.filesDir, "projects").apply { mkdirs() }

    private val TAG = "ProjectStorage"

    // Un Mutex por projectId: evita que dos escrituras concurrentes al MISMO
    // proyecto (p. ej. un autoguardado con debounce que dispara justo cuando
    // el usuario navega hacia atrás y fuerza un guardado inmediato) se pisen
    // entre sí y corrompan project.json. Proyectos distintos no se bloquean
    // entre sí — cada uno vive en su propia carpeta, no hay nada que proteger
    // ahí.
    private val projectMutexes = mutableMapOf<String, Mutex>()

    private fun mutexFor(projectId: String): Mutex = synchronized(projectMutexes) {
        projectMutexes.getOrPut(projectId) { Mutex() }
    }

    /**
     * HALLAZGO DE ESTA AUDITORÍA — escritura atómica incompleta: la Fase 1
     * había corregido `project.json.tmp` + `renameTo` ÚNICAMENTE dentro de
     * [saveProject] (patrón ahora extraído acá), pero esta clase tiene
     * otros 7 sitios que TAMBIÉN escriben `project.json` directamente —
     * [renameProject], [setCoverImage], [setCoverImageBitmap],
     * [removeCoverImage], [setCastPhoto], [removeCastPhoto] y
     * [moveProject] (dos escrituras, una por cada proyecto involucrado) —
     * y todos seguían usando `file.writeText(...)` directo, sin ninguna
     * de las dos protecciones que motivaron la Fase 1: ni atomicidad
     * frente a un lector concurrente, ni (la más grave) atomicidad frente
     * a que el sistema operativo mate el proceso a mitad de la escritura
     * (memoria baja, "forzar detención", actualización de la app). Con
     * `writeText` directo, esa muerte a mitad de camino puede dejar
     * `project.json` truncado — y un JSON truncado no es "el cambio que
     * se estaba guardando se perdió", es [loadProject] fallando el
     * parseo por completo y devolviendo `null`: EL PROYECTO ENTERO queda
     * inaccesible la próxima vez que el usuario intenta abrirlo, no solo
     * el rename/portada/foto de elenco/reorden que estaba en curso. Ahora
     * los 9 sitios de escritura de `project.json` de esta clase (los 7 de
     * arriba + los 2 de [saveProject]) pasan por esta única función.
     *
     * Debe llamarse siempre bajo `mutexFor(projectId).withLock { }` (todos
     * los llamadores ya lo hacen) — esta función solo se ocupa de la
     * atomicidad a nivel de sistema de archivos frente a un crash/kill,
     * no de la exclusión mutua entre corrutinas de la propia app (eso ya
     * lo garantiza el mutex).
     */
    private fun writeProjectDataAtomically(target: File, data: ProjectData) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(json.encodeToString(data))
        if (!tmp.renameTo(target)) {
            // No debería pasar dentro del mismo directorio/volumen, pero
            // por las dudas: reintento explícito antes de rendirse, en vez
            // de dejar el archivo temporal huérfano y la escritura
            // silenciosamente perdida.
            AppLogger.w(TAG, "Rename atómico de '${target.name}' falló en el primer intento, reintentando: '${target.parentFile?.name}'")
            if (!tmp.renameTo(target)) {
                throw java.io.IOException("No se pudo renombrar '${tmp.name}' sobre '${target.name}' tras reintentar")
            }
        }
    }

    // AUDITORÍA — "fondo verde intermitente al reabrir un proyecto": antes,
    // `loadProject()` lanzaba un `async` por cada capa y las decodificaba
    // TODAS en paralelo sin ningún límite (ver el comentario grande ahí
    // sobre por qué se paralelizó). Con pocas capas livianas eso es
    // inofensivo, pero el pico de memoria real es la SUMA de todas las
    // decodificaciones corriendo a la vez, a resolución completa — con
    // proyectos de varias capas grandes (típicamente el fondo es la más
    // pesada, cubre el lienzo entero), ese pico podía superar la memoria
    // libre del dispositivo en ese instante y hacer que `ImageDecoding`
    // tirara `OutOfMemoryError` en una o más capas (ver el KDoc de
    // `ImageDecoding` para el resto de esa cadena de causas). Este
    // semáforo acota cuántas decodificaciones de resolución completa
    // corren AL MISMO TIEMPO — sigue siendo paralelo (más rápido que
    // secuencial), pero el pico de memoria pasa a ser, como mucho,
    // [MAX_PARALLEL_LAYER_DECODES] capas a la vez en vez de todas juntas.
    private val layerDecodeSemaphore = Semaphore(MAX_PARALLEL_LAYER_DECODES)

    private fun projectDir(projectId: String) = File(projectsRoot, projectId).apply { mkdirs() }
    private fun imagesDir(projectId: String) = File(projectDir(projectId), "images").apply { mkdirs() }
    private fun audioDir(projectId: String) = File(projectDir(projectId), "audio").apply { mkdirs() }
    private fun projectFile(projectId: String) = File(projectDir(projectId), "project.json")
    private fun thumbnailFile(projectId: String) = File(projectDir(projectId), "thumbnail.jpg")
    private fun coverFile(projectId: String) = File(projectDir(projectId), "cover.jpg")
    // Fotos de elenco/personajes del panel "Información del proyecto" —
    // mismo patrón que `cover.jpg`, pero hasta 4 archivos, uno por casilla.
    private fun castDir(projectId: String) = File(projectDir(projectId), "cast").apply { mkdirs() }
    private fun castPhotoFileForSlot(projectId: String, slotIndex: Int) =
        File(castDir(projectId), "cast_$slotIndex.jpg")

    fun newProjectId(): String = UUID.randomUUID().toString()

    /** Ruta pública en disco de la foto de una casilla de elenco (exista o no todavía) — ver [setCastPhoto]. */
    fun castPhotoFile(projectId: String, slotIndex: Int): File = castPhotoFileForSlot(projectId, slotIndex)

    /**
     * Clave de orden efectiva de un proyecto: usa la posición manual
     * ([ProjectData.orderIndex]) si ya se asignó una (distinta de 0L), y si
     * no cae de nuevo a `-updatedAtMs` — misma escala (negativo de un
     * timestamp en ms), así un proyecto recién movido y uno viejo sin mover
     * todavía conviven en el mismo orden sin saltos raros. Ordenar
     * ASCENDENTE por esta clave da el resultado deseado: más reciente (o lo
     * último reordenado a mano) primero.
     */
    private fun orderKey(data: ProjectData): Long = if (data.orderIndex != 0L) data.orderIndex else -data.updatedAtMs

    /** Carga y ordena todos los [ProjectData] crudos — base compartida por [listProjects] y [moveProject]. */
    private fun loadAllProjectDataSorted(): List<Pair<File, ProjectData>> =
        projectsRoot.listFiles { file -> file.isDirectory }
            ?.mapNotNull { dir ->
                val file = File(dir, "project.json")
                if (!file.exists()) return@mapNotNull null
                runCatching { dir to json.decodeFromString<ProjectData>(file.readText()) }
                    .onFailure { AppLogger.e(TAG, "No se pudo leer project.json de la carpeta '${dir.name}' — ese proyecto no va a aparecer en la lista", it) }
                    .getOrNull()
            }
            ?.sortedBy { (_, data) -> orderKey(data) }
            ?: emptyList()

    /** Suma recursiva del tamaño de todos los archivos dentro de una carpeta. */
    private fun File.totalSizeBytes(): Long =
        walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // Prefijo/ancho de dígitos derivados de DEFAULT_PROJECT_NAME ("Project01"
    // -> prefijo "Project", 2 dígitos) en vez de hardcodearlos de nuevo acá:
    // si el default cambia algún día, esto se sigue generando solo.
    private val defaultNamePrefix: String = DEFAULT_PROJECT_NAME.trimEnd { it.isDigit() }
    private val defaultNamePadLength: Int =
        (DEFAULT_PROJECT_NAME.length - defaultNamePrefix.length).coerceAtLeast(1)

    /**
     * Próximo nombre por defecto LIBRE con el patrón de [DEFAULT_PROJECT_NAME]
     * (Project01, Project02, Project03, ...) — nunca pisa el nombre de OTRO
     * proyecto que ya exista en disco. Se usa tanto al crear un proyecto
     * nuevo sin título (ver ProjectsScreen/MainActivity) como al guardar un
     * proyecto existente cuyo título quedó vacío (ver el fallback acá abajo
     * en [saveProject]) — un único lugar que decide el default, para que
     * nunca puedan quedar dos proyectos guardados como "Project01" al
     * mismo tiempo.
     *
     * [excludingProjectId] se ignora a sí mismo en la comparación: así, un
     * proyecto que YA se llama "Project01" de un guardado anterior (porque
     * en ese momento tampoco tenía título) no se "sube" a Project02 solo
     * por chocar contra su propio nombre viejo.
     */
    suspend fun nextAvailableDefaultName(excludingProjectId: String? = null): String =
        withContext(Dispatchers.IO) {
            val takenNames = loadAllProjectDataSorted()
                .filter { (dir, _) -> dir.name != excludingProjectId }
                .map { (_, data) -> data.name.trim() }
                .toSet()
            var n = 1
            var candidate = defaultNamePrefix + n.toString().padStart(defaultNamePadLength, '0')
            while (candidate in takenNames) {
                n++
                candidate = defaultNamePrefix + n.toString().padStart(defaultNamePadLength, '0')
            }
            candidate
        }

    /** Lista todos los proyectos guardados, en el orden manual del usuario (o el más reciente primero por defecto). */
    suspend fun listProjects(): List<ProjectSummary> = withContext(Dispatchers.IO) {
        val ordered = loadAllProjectDataSorted()
        ordered.mapIndexed { index, (dir, rawData) ->
            // Mismo saneo que ya aplica `loadProject` antes de construir
            // cualquier `CanvasSpec` (ver `sanitizeProjectData`) — acá no
            // se llega a construir un `CanvasSpec` de verdad, pero un
            // `canvasWidthPx`/`canvasHeightPx` corrupto (0, negativo) NO
            // debe filtrarse igual hacia `ProjectSummary`, porque
            // `ProjectInfoOverlay` lo usaría para armar un texto de
            // formato sin sentido. Reusar la misma función evita
            // duplicar esa validación en dos lugares.
            val data = sanitizeProjectData(rawData)
            ProjectSummary(
                id = data.id,
                name = data.name,
                description = data.description,
                createdAtMs = data.createdAtMs,
                updatedAtMs = data.updatedAtMs,
                layerCount = data.layers.size,
                projectDurationMs = data.projectDurationMs,
                aspectRatio = data.aspectRatio,
                fps = data.fps,
                thumbnailFile = File(dir, "thumbnail.jpg").takeIf { it.exists() },
                // FASE 1 (AUDITORÍA P0/P1) — `coverImageFileName` viene del
                // manifest (`project.json`), así que se resuelve con
                // [resolveManifestFile] (confinado a `dir`) en vez de
                // `File(dir, name)` a ciegas — ver el comentario grande en
                // esa función sobre el riesgo real de path traversal acá.
                coverImageFile = resolveManifestFile(dir, data.coverImageFileName)?.takeIf { it.exists() },
                orderIndex = data.orderIndex,
                sizeBytes = dir.totalSizeBytes(),
                releaseYear = data.releaseYear,
                genre = data.genre,
                // ADR-005, auditoría de Fases F/G: `sanitizeProjectData` ya
                // deja `canvasWidthPx`/`canvasHeightPx` en `null` cuando
                // son inválidos o el proyecto es de formato viejo
                // (`canvasSchemaVersion == 0`) — acá simplemente se
                // reenvían tal cual, sin repetir esa lógica.
                canvasWidthPx = data.canvasWidthPx,
                canvasHeightPx = data.canvasHeightPx,
                canvasOriginPresetId = data.canvasOriginPresetId,
                canMoveUp = index > 0,
                canMoveDown = index < ordered.size - 1
            )
        }
    }

    /**
     * Guarda el proyecto completo: JSON con todas las capas y sus
     * keyframes, el clip de audio si lo hay, copia local de cualquier
     * imagen/audio nuevo o reemplazado, y una miniatura fiel (con grading
     * real aplicado) para "Mis proyectos". No hace nada si el proyecto
     * todavía no tiene ninguna capa — evita crear proyectos vacíos en
     * disco antes de que el usuario importe algo.
     */
    suspend fun saveProject(
        projectId: String,
        name: String,
        projectDurationMs: Long,
        playheadMs: Long,
        layers: List<LayerSaveSnapshot>,
        // Objetos Layer EN VIVO, usados EXCLUSIVAMENTE para renderizar la
        // miniatura (ver el comentario grande en [LayerSaveSnapshot] sobre
        // por qué esto NO participa de la construcción del manifest).
        liveLayersForThumbnail: List<Layer>,
        audioClip: AudioSaveSnapshot? = null,
        speedKeyframes: List<SpeedKeyframe> = emptyList(),
        freezeFrames: List<FreezeFrame> = emptyList(),
        @Suppress("DEPRECATION") aspect: AspectRatioPreset = AspectRatioPreset.REELS,
        // Ver ADR-005, Fase E: el Canvas real del proyecto ahora se recibe
        // como parámetro propio en vez de derivarse siempre de [aspect] —
        // [aspect] queda como fallback SOLO para quien todavía no pasa
        // [canvas] explícito (compatibilidad de firma), pero
        // `EditorViewModel` (el único caller real) siempre lo manda desde
        // `state.canvas`, que puede ser cualquier preset del catálogo
        // (incluida la categoría GENERAL) o un Canvas Custom — ninguno de
        // los dos representable por el enum legacy de 3 valores.
        canvas: CanvasSpec? = null,
        fps: Int = 30,
        // Metadata del panel "Información del proyecto" — null = "no lo
        // toques, dejá lo que ya estaba guardado" (así el autoguardado
        // normal del editor, que no sabe nada de estos campos hasta que el
        // usuario abre ese panel, nunca los borra por accidente).
        releaseYear: Int? = null,
        genre: String? = null,
        infoDurationMinutes: Int? = null,
        // Guías de composición (cuadrícula) — a diferencia de
        // releaseYear/genre/infoDurationMinutes, estos SIEMPRE viajan con
        // el valor real actual (no nullable, no "no lo toques"): son
        // estado normal del editor, como aspect/fps, y EditorViewModel
        // los manda en cada autoguardado sin excepción.
        gridEnabled: Boolean = false,
        gridShapeName: String = "RECTANGLE",
        gridColumns: Int = 3,
        gridRows: Int = 3,
        gridLineColorEnabled: Boolean = false,
        gridLineHue: Float = 70f,
        gridLineThicknessDp: Float = 1f,
        gridLineOpacity: Float = 0.4f,
        gridSnapEnabled: Boolean = false,
        // Igual criterio que gridEnabled/etc.: viajan siempre con el valor
        // real actual en cada autoguardado, sin excepción.
        handleOrderGlobal: Map<String, String> = emptyMap(),
        handleOrderPerLayer: Map<String, Map<String, String>> = emptyMap()
        // Devuelve [SaveProjectResult]: el nombre EFECTIVO que quedó
        // guardado — normalmente `name` tal cual, pero si vino vacío/solo
        // espacios (el usuario no escribió título en el panel
        // "Información del proyecto") acá se aplica el único default de
        // todo el proyecto (DEFAULT_PROJECT_NAME = "Project01") — y las
        // referencias locales recién resueltas de imagen/audio, ver
        // [SaveProjectResult].
    ): SaveProjectResult = withContext(Dispatchers.IO) {
        if (layers.isEmpty()) {
            return@withContext SaveProjectResult(name.ifBlank { DEFAULT_PROJECT_NAME }, emptyMap(), null)
        }
        mutexFor(projectId).withLock {

        val imgDir = imagesDir(projectId)
        val existingFile = projectFile(projectId)
        val existing = if (existingFile.exists()) {
            // Solo si el archivo YA EXISTE tiene sentido loguear un fallo acá —
            // si todavía no existe es sencillamente un proyecto nuevo (primer
            // guardado), comportamiento 100% normal, no una falsa alarma.
            runCatching { json.decodeFromString<ProjectData>(existingFile.readText()) }
                .onFailure { AppLogger.e(TAG, "project.json existente está dañado/ilegible, no se pudo leer antes de guardar: $projectId", it) }
                .getOrNull()
        } else null

        // FASE 4.2 (AUDITORÍA P0 — corrección REAL de la Fase 1 anterior,
        // que solo dejaba un comentario pero seguía usando `mapNotNull`,
        // ver punto 4.1 del prompt maestro de Fase 4.2): un fallo al
        // asegurar la copia local de una capa ya NO puede hacer que esa
        // capa desaparezca silenciosamente de `project.json`.
        //
        // Orden de resolución (ver sección 5 del prompt maestro):
        //   1. reutilizar la copia local ya persistida con éxito en el
        //      guardado anterior de ESA MISMA capa, si sigue existiendo en
        //      disco y no está vacía — el usuario no pierde la capa por un
        //      fallo puntual (transitorio) de refresco de su imagen;
        //   2. si no hay ninguna copia previa válida a la que volver (p.
        //      ej. capa nueva que nunca se copió con éxito), se aborta
        //      TODO este guardado lanzando [ProjectAssetIntegrityException]
        //      — nunca se persiste un proyecto con una capa menos.
        val resolvedImageUris = mutableMapOf<String, Uri>()
        val existingLayersById = existing?.layers?.associateBy { it.id } ?: emptyMap()
        val layerDataList = layers.map { snap ->
            val copy = ensureLocalImage(snap.id, snap.sourceUri, imgDir)
            val fallback = existingLayersById[snap.id]?.let { prev ->
                val prevFile = File(imgDir, prev.imageFileName)
                if (prevFile.exists() && prevFile.length() > 0L) prev.imageFileName to Uri.fromFile(prevFile) else null
            }
            if (copy == null && fallback != null) {
                AppLogger.w(TAG, "Capa '${snap.name}' (${snap.id}): no se pudo refrescar su copia local en este guardado, se reutiliza la copia previa válida en su lugar")
            }
            val (fileName, resolvedUri) = resolveAssetOrAbort(copy, fallback) {
                "No se pudo asegurar la imagen local de la capa '${snap.name}' (${snap.id}) y no hay ninguna copia previa válida a la que volver — se aborta este guardado para no perder la capa"
            }
            if (resolvedUri != snap.sourceUri) resolvedImageUris[snap.id] = resolvedUri
            LayerData(
                id = snap.id,
                imageFileName = fileName,
                name = snap.name,
                zIndex = snap.zIndex,
                parallaxFactor = snap.parallaxFactor,
                locked = snap.locked,
                orderLocked = snap.orderLocked,
                visible = snap.visible,
                lookSettings = snap.lookSettings,
                keyframes = snap.keyframes,
                baseFrame = snap.baseFrame,
                widthPx = snap.widthPx,
                heightPx = snap.heightPx,
                colorIndex = snap.colorIndex,
                customColorArgb = snap.customColorArgb,
                importedDefaultColorArgb = snap.importedDefaultColorArgb,
                customGradientStartArgb = snap.customGradientStartArgb,
                customGradientEndArgb = snap.customGradientEndArgb,
                useGradientColor = snap.useGradientColor,
                gradientAngleDegrees = snap.gradientAngleDegrees,
                gradientIsRadial = snap.gradientIsRadial,
                useBlackAndWhiteMode = snap.useBlackAndWhiteMode
            )
        }

        // FASE 4.2-R4: la limpieza de imágenes de capas eliminadas se movió
        // DESPUÉS del commit atómico de `project.json` (ver más abajo,
        // justo después del `renameTo` exitoso) — antes corría acá, ANTES
        // de escribir el manifest nuevo, así que un fallo posterior de
        // escritura dejaba el `project.json` VIEJO (que todavía referencia
        // esas imágenes) apuntando a archivos ya borrados. Ver Parte 1 del
        // prompt maestro R4 para el escenario completo.
        //
        // --- Papelera de capas ---
        // Antes de esta función, "la capa ya no está en `layers`" y "borrar
        // su imagen para siempre" eran la MISMA cosa — pasaban en el mismo
        // paso, sin ningún punto intermedio de arrepentimiento. Ahora se
        // separan: acá solo se detecta la diferencia (qué id de capa estaba
        // en el `project.json` anterior y ya no está en este guardado), y
        // esas capas pasan a `trashedLayers` con su `LayerData` COMPLETO —
        // recién `restoreLayersFromTrash`/`deleteLayersFromTrashPermanently`/
        // `emptyTrash` (más abajo en esta clase) deciden si vuelven al
        // lienzo o se borran de verdad. La imagen de una capa recién
        // eliminada NO se mueve de directorio: sigue en `images/` con el
        // mismo nombre de siempre — pasar a formar parte de `trashedLayers`
        // ya alcanza para que `validImageNames` (unas líneas más abajo) la
        // siga considerando válida y la limpieza de huérfanos post-commit
        // no la toque.
        val currentLayerIds = layerDataList.map { it.id }.toSet()
        val newlyTrashed = existing?.layers.orEmpty()
            .filter { it.id !in currentLayerIds }
            .map { DeletedLayerData(layer = it, deletedAtMs = System.currentTimeMillis()) }
        val trashedLayersList = (existing?.trashedLayers.orEmpty() + newlyTrashed)
        // Ver el comentario grande de `trashBannerAcknowledged` en
        // ProjectData: un episodio nuevo (recién cayó algo a la papelera en
        // ESTE guardado) reinicia el aviso, para que el usuario se entere
        // de ESTE arrepentimiento puntual y no del anterior que ya vio.
        val trashBannerAcknowledged = if (newlyTrashed.isNotEmpty()) false else (existing?.trashBannerAcknowledged ?: true)

        val validImageNames = layerDataList.map { it.imageFileName }.toSet() +
            trashedLayersList.map { it.layer.imageFileName }.toSet()

        // FASE 4.2 (AUDITORÍA P0 — corrección REAL, ver punto 4.3 del
        // prompt maestro): mismo criterio que las capas arriba, aplicado
        // al audio de fondo. El bug anterior era en realidad DOBLE: (1) un
        // fallo transitorio de `ensureLocalAudio` hacía que el proyecto se
        // guardara sin audio, y (2) como `audioDataResult == null` es
        // EXACTAMENTE la misma condición que "el usuario quitó el audio a
        // propósito", la limpieza de `audioDir` que sigue abajo terminaba
        // BORRANDO el archivo de audio anterior — que sí era válido — como
        // consecuencia directa de ese fallo transitorio. Es decir: no solo
        // se perdía la referencia en `project.json`, se destruía el propio
        // archivo. Ahora se distinguen expresamente los dos casos.
        var resolvedAudioUri: Uri? = null
        val audioDataResult: AudioTrackData? = if (audioClip == null) {
            null
        } else {
            val clip = audioClip
            val copy = ensureLocalAudio(clip.sourceUri, clip.displayName, audioDir(projectId))
            val fallback = existing?.audioTrack?.let { prev ->
                val prevFile = File(audioDir(projectId), prev.audioFileName)
                if (prevFile.exists() && prevFile.length() > 0L) prev.audioFileName to Uri.fromFile(prevFile) else null
            }
            if (copy == null && fallback != null) {
                AppLogger.w(TAG, "Audio de fondo: no se pudo refrescar su copia local en este guardado, se reutiliza la copia previa válida en su lugar: $projectId")
            }
            val (fileName, resolvedUri) = resolveAssetOrAbort(copy, fallback) {
                "No se pudo asegurar la copia local del audio de fondo y no hay ninguna copia previa válida a la que volver — se aborta este guardado para no perder el audio"
            }
            if (resolvedUri != clip.sourceUri) resolvedAudioUri = resolvedUri
            AudioTrackData(
                audioFileName = fileName,
                displayName = clip.displayName,
                sourceDurationMs = clip.sourceDurationMs,
                volume = clip.volume,
                muted = clip.muted,
                trimStartMs = clip.trimStartMs,
                loop = clip.loop,
                fadeInMs = clip.fadeInMs,
                fadeOutMs = clip.fadeOutMs
            )
        }
        // FASE 4.2-R4: la limpieza real del directorio de audio (tanto para
        // "el usuario quitó el audio" como para "quedó un archivo con otro
        // nombre/extensión tras reemplazarlo") se movió DESPUÉS del commit
        // atómico de `project.json` — ver el bloque de limpieza post-commit
        // más abajo. Acá solo queda registrado, para ese bloque, cuál es el
        // nombre de archivo válido a conservar (`null` si el proyecto ya no
        // tiene audio).
        val validAudioFileName: String? = audioDataResult?.audioFileName

        // OJO — acá estuvo el bug real de "el título borrado se restaura
        // solo": la primera versión de este fallback caía a
        // `existing?.name` (el nombre VIEJO que ya estaba guardado en
        // disco) cuando `name` venía vacío. La regla, sin excepción: vacío
        // es SIEMPRE un default "ProjectNN" — pero tiene que ser uno LIBRE
        // (ver nextAvailableDefaultName): si ya existe otro proyecto
        // guardado como "Project01", este cae a "Project02", y así, para
        // que nunca queden dos proyectos con el mismo nombre por defecto.
        val trimmedName = name.trim()
        val effectiveName = if (trimmedName.isNotBlank()) trimmedName
            else nextAvailableDefaultName(excludingProjectId = projectId)

        // Ver ADR-005, Fase C + Fase E: todo guardado (incluso el
        // autoguardado normal) escribe siempre el Canvas real con
        // `canvasSchemaVersion = 1`. Desde la Fase E ese Canvas ya NO se
        // deriva de `aspect` — se recibe directo como parámetro (arriba),
        // porque puede venir de un preset GENERAL o de un tamaño Custom,
        // ninguno de los dos expresable con el enum legacy de 3 valores.
        // `LegacyAspectMigration` queda como fallback exclusivo de quien
        // todavía no pasa `canvas` explícito (compatibilidad de firma) y
        // para la migración real de proyectos viejos en `loadProject`.
        val canvasSpec = canvas ?: LegacyAspectMigration.migrate(aspect)

        // BUG REAL corregido acá (ver ADR-005, Fase G): antes esta línea
        // escribía `aspect.name` directo — el parámetro `aspect` de esta
        // función, que `EditorViewModel` (el único caller real) manda
        // siempre desde `EditorUiState.exportAspect`, un campo que desde
        // la Fase E ya NO se elige al crear el proyecto y por eso queda
        // SIEMPRE en su default (`REELS`). Resultado: todo proyecto
        // creado después de la Fase E guardaba `aspectRatio = "REELS"` en
        // su `project.json` sin importar el Canvas real (Cuadrado,
        // Custom 4:5, etc.), y ese dato falso se auto-perpetuaba en cada
        // re-guardado (`loadProject` lo vuelve a leer como `exportAspect`
        // la próxima vez que se abre el proyecto). Ahora se deriva
        // siempre de `canvasSpec` (arriba: el Canvas real, sea el que
        // mandó el caller o el migrado desde `aspect` como fallback) —
        // así el `project.json` queda coherente aunque una versión
        // anterior de la app (downgrade) sea la única que lo lea alguna
        // vez.
        @Suppress("DEPRECATION")
        val legacyAspectRatio = LegacyAspectMigration.closestLegacyAspectFor(canvasSpec).name

        val data = ProjectData(
            id = projectId,
            name = effectiveName,
            createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis(),
            projectDurationMs = projectDurationMs,
            layers = layerDataList,
            audioTrack = audioDataResult,
            speedKeyframes = speedKeyframes,
            freezeFrames = freezeFrames,
            aspectRatio = legacyAspectRatio,
            canvasWidthPx = canvasSpec.widthPx,
            canvasHeightPx = canvasSpec.heightPx,
            canvasOriginPresetId = canvasSpec.originPresetId,
            canvasSchemaVersion = 1,
            fps = fps,
            // El autoguardado del editor no toca descripción/portada/orden
            // manual — esos campos sólo cambian desde los diálogos de "Mis
            // proyectos" (renombrar, portada, mover arriba/abajo).
            description = existing?.description ?: "",
            coverImageFileName = existing?.coverImageFileName,
            // Proyecto nuevo (sin `existing`): se le asigna una posición al
            // tope de la lista, igual que "más reciente primero" de antes.
            orderIndex = existing?.orderIndex ?: -System.currentTimeMillis(),
            // El autoguardado normal del editor no conoce estos campos
            // hasta que el usuario los toca desde ProjectInfoPanel — por
            // eso null cae siempre a lo que ya estaba guardado, nunca a
            // "borrar". Las fotos de elenco tienen su propio camino de
            // guardado directo (ver setCastPhoto/removeCastPhoto) y nunca
            // se tocan acá.
            releaseYear = releaseYear ?: existing?.releaseYear,
            genre = genre ?: existing?.genre,
            infoDurationMinutes = infoDurationMinutes ?: existing?.infoDurationMinutes,
            castPhotoFileNames = existing?.castPhotoFileNames ?: listOf(null, null, null, null),
            gridEnabled = gridEnabled,
            gridShapeName = gridShapeName,
            gridColumns = gridColumns,
            gridRows = gridRows,
            gridLineColorEnabled = gridLineColorEnabled,
            gridLineHue = gridLineHue,
            gridLineThicknessDp = gridLineThicknessDp,
            gridLineOpacity = gridLineOpacity,
            gridSnapEnabled = gridSnapEnabled,
            handleOrderGlobal = handleOrderGlobal,
            handleOrderPerLayer = handleOrderPerLayer,
            // ARREGLADO: antes `playheadMs` solo se usaba unas líneas más
            // abajo para renderizar la miniatura, nunca se guardaba en el
            // JSON — ver comentario grande en ProjectData.playheadMs.
            playheadMs = playheadMs,
            trashedLayers = trashedLayersList,
            trashBannerAcknowledged = trashBannerAcknowledged
        )
        try {
            // AUDITORÍA — corregido: antes esto escribía directo sobre
            // `project.json` con `writeText`, que NO es atómico a nivel de
            // sistema de archivos — un lector concurrente (antes de esta
            // misma auditoría, `loadProject` ni siquiera tomaba el mutex de
            // este proyecto, ver el comentario ahí) podía llegar a abrir el
            // archivo a mitad de la escritura y leer JSON truncado/inválido,
            // viendo el proyecto como "corrupto" por una simple carrera de
            // tiempo, no por daño real de datos.
            //
            // Patrón estándar de escritura atómica, ahora compartido por
            // TODOS los puntos de escritura de project.json — ver
            // [writeProjectDataAtomically].
            writeProjectDataAtomically(projectFile(projectId), data)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error crítico guardando project.json — el proyecto podría no haberse guardado: $projectId", t)
            throw t
        }

        // FASE 4.2-R4 (Parte 1 del prompt maestro R4 — persistencia
        // TRANSACCIONAL completa): recién ACÁ, con `project.json` ya
        // reemplazado atómicamente por el contenido nuevo (el `try` de
        // arriba terminó sin lanzar), es seguro borrar assets obsoletos.
        // Si el `try` de arriba hubiera lanzado, esta línea nunca se
        // ejecuta — el manifest viejo sigue en disco y ningún asset que
        // todavía referencia queda tocado. Esta es la garantía central de
        // esta fase: "nunca eliminar definitivamente un asset que el
        // último estado persistente CONFIRMADO todavía puede referenciar".
        runCatching {
            imgDir.listFiles()?.forEach { f -> if (f.name !in validImageNames) f.delete() }
        }.onFailure { AppLogger.w(TAG, "No se pudo limpiar alguna imagen de capa obsoleta (no crítico, no afecta la consistencia del manifest ya confirmado): $projectId", it) }
        runCatching {
            val audioD = audioDir(projectId)
            if (validAudioFileName == null) {
                // El proyecto ya no tiene audio (el usuario lo quitó): se
                // limpia todo el directorio.
                audioD.listFiles()?.forEach { it.delete() }
            } else {
                // Se conserva únicamente el archivo que el manifest recién
                // confirmado referencia — cualquier otro (p. ej. el archivo
                // de una extensión anterior tras reemplazar el audio) queda
                // obsoleto recién ahora que el nuevo ya está confirmado.
                audioD.listFiles()?.forEach { f -> if (f.name != validAudioFileName) f.delete() }
            }
        }.onFailure { AppLogger.w(TAG, "No se pudo limpiar el audio obsoleto (no crítico, no afecta la consistencia del manifest ya confirmado): $projectId", it) }

        // Miniatura de "Mis proyectos": se renderiza en la proporción REAL
        // del canvas del proyecto (canvasSpec, ya resuelto arriba), no en
        // un tamaño fijo — antes esto llamaba a ThumbnailRenderer.render
        // sin pasar widthPx/heightPx, así que SIEMPRE usaba el default de
        // la función (360×640, vertical). Para cualquier proyecto que no
        // fuera vertical (ej. formatos de Cine como IMAX/Cinemascope,
        // horizontales), el contenido quedaba "contenido" dentro de ese
        // viewport vertical que no le correspondía, dejando barras vacías
        // arriba/abajo que el motor limpia con el verde chroma-key —
        // mismo síntoma reportado por el cliente, pero acá NO es el fix
        // de profundidad de LayerDrawer (ver ADR-007): la miniatura usa
        // el mismo LayerDrawer.drawLayer, así que con viewport correcto
        // hereda automáticamente esa corrección — el problema real era
        // el propio tamaño de viewport pasado a `render`, previo a esto.
        //
        // Con el viewport ya en la proporción del canvas, el fondo (que
        // BackgroundAdjustDialog ya ajusta a esa misma proporción, ver
        // ADR-006) llena el cuadro por completo — cero verde. El límite
        // de 640px de lado más largo es solo presupuesto de memoria/disco
        // para una miniatura de lista, nunca el tamaño real del proyecto
        // (que sigue viviendo, sin tocar, en canvasSpec.widthPx/heightPx).
        val thumbnailMaxDimensionPx = 640
        val thumbnailWidthPx: Int
        val thumbnailHeightPx: Int
        if (canvasSpec.widthPx >= canvasSpec.heightPx) {
            thumbnailWidthPx = thumbnailMaxDimensionPx
            thumbnailHeightPx = (thumbnailMaxDimensionPx.toFloat() * canvasSpec.heightPx / canvasSpec.widthPx)
                .toInt().coerceAtLeast(1)
        } else {
            thumbnailHeightPx = thumbnailMaxDimensionPx
            thumbnailWidthPx = (thumbnailMaxDimensionPx.toFloat() * canvasSpec.widthPx / canvasSpec.heightPx)
                .toInt().coerceAtLeast(1)
        }

        val thumbnail = try {
            thumbnailRenderer.render(
                appContext,
                liveLayersForThumbnail,
                timeMs = playheadMs,
                widthPx = thumbnailWidthPx,
                heightPx = thumbnailHeightPx
            )
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error generando la miniatura al guardar el proyecto: $projectId", t)
            null
        }
        if (thumbnail != null) {
            runCatching {
                thumbnailFile(projectId).outputStream().use { out ->
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }.onFailure { AppLogger.w(TAG, "No se pudo guardar el archivo de miniatura en disco: $projectId", it) }
            thumbnail.recycle()
        }
        SaveProjectResult(
            effectiveName = data.name,
            resolvedLayerImageUris = resolvedImageUris,
            resolvedAudioUri = resolvedAudioUri
        )
        }
    }

    /**
     * FASE 1 (AUDITORÍA P0) — INTEGRIDAD DE COPIA.
     *
     * Asegura que exista una copia LOCAL y REALMENTE ESCRITA de la imagen
     * de la capa. Si `sourceUri` ya apunta a esa copia (proyecto recién
     * cargado, imagen sin cambios), no vuelve a copiar nada. Si apunta a
     * un Uri de SAF fresco (importación nueva o "reemplazar imagen"),
     * copia el contenido una vez.
     *
     * BUG REAL corregido acá: la versión anterior hacía
     * `openInputStream(uri)?.use { ... }` dentro de un `runCatching`. Si
     * `openInputStream` devuelve `null` (Uri de SAF revocado, provider
     * caído, permiso perdido...), el operador `?.use` simplemente NO
     * ejecuta el bloque y devuelve `null` — sin lanzar ninguna excepción.
     * `runCatching` solo atrapa EXCEPCIONES, así que ese `null` se veía
     * como un resultado "exitoso" (`Result.success(null)`), y el
     * `.onSuccess { layer.sourceUri = Uri.fromFile(destFile) }` corría
     * igual — apuntando `sourceUri` a un archivo que NUNCA se llegó a
     * crear (ni siquiera se abrió `destFile.outputStream()`, porque el
     * `?.use` externo cortocircuita TODO el bloque, incluida la apertura
     * del archivo destino). El proyecto quedaba con una capa cuya imagen
     * "existe" según `project.json` pero no existe en disco — recién se
     * notaba, mucho después, al reabrir el proyecto ("Falta el archivo de
     * imagen de la capa").
     *
     * Esta versión:
     *  1. Exige explícitamente que el `InputStream` no sea nulo (lo
     *     convierte en una excepción real si lo es, para que quede
     *     atrapada por el mismo `runCatching` de forma explícita).
     *  2. Solo se considera éxito si además el archivo destino existe
     *     DESPUÉS de la copia y tiene contenido real (`length() > 0`).
     *  3. Si algo falla, NUNCA actualiza ninguna referencia y NUNCA deja
     *     un archivo parcial a medio escribir (se borra si quedó algo).
     *  4. Devuelve `null` en caso de fallo — el llamador (`saveProject`)
     *     omite esa capa del guardado en vez de persistir una referencia
     *     a un archivo inexistente (ver `mapNotNull` en `saveProject`).
     *
     * @return (nombreDeArchivo, uriLocalResuelto) si la copia (o la copia
     * ya existente) es válida, o `null` si no se pudo asegurar.
     */
    private fun ensureLocalImage(layerId: String, sourceUri: Uri, imgDir: File): Pair<String, Uri>? {
        val fileName = "$layerId.png"
        val destFile = File(imgDir, fileName)
        val alreadyLocalCopy = sourceUri.scheme == "file" && sourceUri.path == destFile.absolutePath
        if (alreadyLocalCopy && destFile.exists() && destFile.length() > 0L) {
            return fileName to sourceUri
        }
        // FASE 4.2 (AUDITORÍA P0 — corrección real, ver punto 4.2 del
        // prompt maestro): REEMPLAZO ATÓMICO, delegado a
        // [copyToFileAtomically] — antes, `destFile.outputStream()`
        // truncaba el archivo destino INMEDIATAMENTE al abrir el stream de
        // salida; si `destFile` ya tenía una copia local válida de una
        // edición anterior y la copia nueva fallaba a mitad de camino (I/O,
        // permiso SAF revocado, storage lleno), esa copia anterior válida
        // ya estaba destruida para ese momento. `copyToFileAtomically`
        // nunca toca `destFile` hasta confirmar la copia completa en un
        // temporal aparte.
        val ok = copyToFileAtomically(destFile) {
            appContext.contentResolver.openInputStream(sourceUri)
                ?: throw java.io.IOException("openInputStream() devolvió null para $sourceUri — no hay datos que copiar")
        }
        if (!ok) {
            AppLogger.e(TAG, "No se pudo copiar la imagen de la capa '$layerId' a almacenamiento local — se conserva la copia anterior si existía")
            return null
        }
        return fileName to Uri.fromFile(destFile)
    }

    /**
     * Mismo patrón e integridad que [ensureLocalImage] pero para el audio
     * de fondo. El nombre de archivo local conserva la extensión original
     * (mp3, m4a, wav...) porque algunos decodificadores del sistema la
     * usan como pista adicional para elegir el demuxer correcto.
     */
    private fun ensureLocalAudio(sourceUri: Uri, displayName: String, dir: File): Pair<String, Uri>? {
        val extension = guessAudioExtension(displayName)
        val fileName = "audio.$extension"
        val destFile = File(dir, fileName)
        val alreadyLocalCopy = sourceUri.scheme == "file" && sourceUri.path == destFile.absolutePath
        if (alreadyLocalCopy && destFile.exists() && destFile.length() > 0L) {
            return fileName to sourceUri
        }
        // FASE 4.2 (AUDITORÍA P0 — corrección real, ver punto 4.3 del
        // prompt maestro): REEMPLAZO ATÓMICO, mismo criterio que
        // [ensureLocalImage]. La versión anterior hacía
        // `dir.listFiles()?.forEach { it.delete() }` ANTES de copiar nada
        // nuevo — destruía el/los archivo(s) de audio anteriores (válidos)
        // por el solo hecho de haber abierto con éxito el `InputStream` de
        // origen, sin ninguna garantía todavía de que la copia completa
        // fuera a tener éxito.
        val ok = copyToFileAtomically(destFile) {
            appContext.contentResolver.openInputStream(sourceUri)
                ?: throw java.io.IOException("openInputStream() devolvió null para $sourceUri — no hay datos que copiar")
        }
        if (!ok) {
            AppLogger.e(TAG, "No se pudo copiar el audio '$displayName' a almacenamiento local — se conserva el audio anterior si existía")
            return null
        }
        // FASE 4.2-R4 (AUDITORÍA — corrección real, ver Parte 1 del prompt
        // maestro R4): la limpieza de archivos "huérfanos" con otro nombre
        // (p. ej. reemplazar el audio por uno de distinta extensión) se
        // movió FUERA de acá — antes se hacía inmediatamente después de
        // copiar, es decir, ANTES de que `saveProject` confirmara el nuevo
        // `project.json`. Si esa escritura fallaba DESPUÉS de este borrado,
        // el `project.json` VIEJO (que seguía en disco, referenciando el
        // archivo de audio anterior) quedaba apuntando a un archivo ya
        // eliminado — el mismo problema de fondo que el punto 4.1 de la
        // fase anterior ya había corregido para capas, pero que goteaba acá
        // por un costado distinto. Ahora esta función NUNCA borra nada:
        // solo copia y devuelve el nombre de archivo resuelto. La limpieza
        // de cualquier archivo de audio con otro nombre queda a cargo de
        // `saveProject`, y corre recién DESPUÉS del commit atómico exitoso
        // de `project.json` (ver el bloque de limpieza post-commit ahí).
        return fileName to Uri.fromFile(destFile)
    }

    private fun guessAudioExtension(displayName: String): String {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return if (ext.isNotBlank() && ext.length <= 4) ext else "m4a"
    }

    /** Carga un proyecto guardado y reconstruye sus capas + audio (con bitmap/uri ya listos), para el ViewModel. */
    /**
     * Lectura liviana: solo el `name` guardado en `project.json`, SIN
     * decodificar ninguna imagen de capa (a diferencia de [loadProject],
     * que decodifica el proyecto entero). Pensada para poder resincronizar
     * el nombre en memoria de un ViewModel reciclado sin pagar el costo de
     * volver a cargar todas las capas — ver
     * [com.yeivikas.olyzecs.viewmodel.EditorViewModel.refreshProjectNameFromDisk].
     */
    suspend fun peekProjectName(projectId: String): String? = withContext(Dispatchers.IO) {
        val file = projectFile(projectId)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<ProjectData>(file.readText()).name }
            .onFailure { AppLogger.w(TAG, "No se pudo leer el nombre del proyecto (peekProjectName): $projectId", it) }
            .getOrNull()
    }

    /**
     * Reconstruye una [Layer] EN VIVO (imagen decodificada, CameraTrack
     * armado, colores resueltos) a partir de su [LayerData] persistido.
     *
     * Extraída de `loadProject` para que [restoreLayersFromTrash] pueda
     * reconstruir exactamente de la misma forma una capa que vuelve de la
     * papelera — antes de esta extracción, hacer eso hubiera significado
     * copiar y pegar esta misma lógica de ~50 líneas en dos lugares, con el
     * riesgo real de que un cambio futuro (un campo nuevo de [LayerData],
     * un fallback distinto) se aplicara en un lado y se olvidara en el
     * otro, dejando una capa restaurada de la papelera sutilmente distinta
     * a una cargada normalmente.
     *
     * Devuelve `null` si la imagen falta o no se puede decodificar —
     * mismo criterio de "esta capa puntual no se muestra" que ya usaba
     * `loadProject`, no aborta la operación completa por una imagen mala.
     */
    private suspend fun layerDataToLiveLayer(imgDir: File, layerData: LayerData, colorIndexFallback: Int): Layer? {
        // FASE 1 (AUDITORÍA P0/P1) — `imageFileName` viene del
        // manifest; se resuelve confinado a `imgDir` en vez de
        // `File(imgDir, layerData.imageFileName)` a ciegas (ver
        // [resolveManifestFile]).
        val imageFile = resolveManifestFile(imgDir, layerData.imageFileName)
        if (imageFile == null || !imageFile.exists()) {
            AppLogger.e(TAG, "Falta o es inválido el archivo de imagen de la capa '${layerData.name}' (${layerData.imageFileName}) — esa capa no se va a mostrar")
            return null
        }
        // Igual que en la importación: resolución completa del
        // archivo guardado, sin límite artificial (ver [ImageDecoding]).
        // `withPermit` acota cuántas de estas corren en paralelo al
        // mismo tiempo (ver `layerDecodeSemaphore`) — sigue siendo
        // async/paralelo, pero con un tope real de memoria pico.
        val decoded = layerDecodeSemaphore.withPermit {
            runCatching {
                ImageDecoding.decodeSampledFromFile(imageFile, maxDimension = ImageDecoding.NO_LIMIT)
            }
                .onFailure { AppLogger.e(TAG, "Error decodificando la imagen de la capa '${layerData.name}'", it) }
                .getOrNull()
        } ?: return null

        return Layer(
            id = layerData.id,
            sourceUri = Uri.fromFile(imageFile),
            name = layerData.name,
            zIndex = layerData.zIndex,
            parallaxFactor = layerData.parallaxFactor,
            locked = layerData.locked,
            orderLocked = layerData.orderLocked,
            visible = layerData.visible,
            lookSettings = layerData.lookSettings,
            cameraTrack = CameraTrack(
                initialKeyframes = layerData.keyframes,
                initialBaseFrame = layerData.baseFrame ?: CameraFrame(0f, 0f, 1f, 0f, 1f)
            ),
            widthPx = layerData.widthPx.takeIf { it > 0 } ?: decoded.width,
            heightPx = layerData.heightPx.takeIf { it > 0 } ?: decoded.height,
            // Proyectos guardados antes de esta mejora no tienen
            // colorIndex en el JSON (queda null) — se usa la posición
            // en la lista como color de respaldo, así igual se ven
            // capas distintas con colores distintos en vez de todas
            // iguales al reabrir un proyecto viejo.
            colorIndex = layerData.colorIndex ?: colorIndexFallback,
            customColorArgb = layerData.customColorArgb,
            // Compatibilidad con proyectos guardados ANTES de
            // este campo (ver doc en LayerData/Layer): si no
            // viene en el JSON, se recalcula el color
            // dominante de la imagen ACÁ MISMO, sobre el
            // bitmap que ya se acaba de decodificar — mismo
            // cálculo que usa LayerRepository al importar. Así
            // "Restablecer" también queda arreglado para
            // proyectos abiertos de antes, no solo para capas
            // importadas de ahora en más.
            importedDefaultColorArgb = layerData.importedDefaultColorArgb
                ?: ColorExtraction.dominantColor(decoded),
            customGradientStartArgb = layerData.customGradientStartArgb,
            customGradientEndArgb = layerData.customGradientEndArgb,
            useGradientColor = layerData.useGradientColor,
            gradientAngleDegrees = layerData.gradientAngleDegrees ?: 90f,
            gradientIsRadial = layerData.gradientIsRadial,
            useBlackAndWhiteMode = layerData.useBlackAndWhiteMode
        ).apply {
            // El motor de preview sube esto a GPU una sola vez y lo libera (ver GLRenderer);
            // hay que dejarlo listo acá igual que hace LayerRepository al importar.
            // FASE 3.1 — publish() en vez de una asignación directa: ver
            // SingleResourceHandoff/Layer.kt para el traspaso de ownership real.
            // FASE 3.1.2 — se etiqueta con `contentRevision`
            // (0, el default de una capa recién construida)
            // en vez de una publicación sin versión — ver
            // SingleResourceHandoff.takeIfCurrent.
            pendingBitmap.publish(decoded, contentRevision)
        }
    }

    suspend fun loadProject(projectId: String): LoadedProject? = withContext(Dispatchers.IO) {
        // AUDITORÍA — corregido: esta función leía `project.json` SIN tomar
        // `mutexFor(projectId)`, mientras que `saveProject`/`deleteProject`/
        // etc. sí lo toman. Eso dejaba una ventana de carrera real: un
        // autoguardado en curso (disparado por el ciclo de vida del editor)
        // y una apertura manual del mismo proyecto podían solaparse, y el
        // lector podía toparse con el archivo a mitad de escribir. Ahora
        // que la escritura en sí ya es atómica (ver el comentario en
        // `saveProject`), tomar el mismo mutex acá es la segunda mitad de
        // la corrección: sin el mutex, un lector podía alcanzar a leer
        // igual el archivo COMPLETO pero de la versión VIEJA justo antes de
        // que el rename lo reemplazace por la nueva — no es un dato
        // corrupto, pero sí una foto desactualizada del proyecto en el
        // peor momento posible (justo cuando se lo está guardando). El
        // mutex hace que un `loadProject` en curso espere a que cualquier
        // `saveProject`/`deleteProject` de ESE MISMO proyecto termine antes
        // de empezar a leer, y viceversa.
        mutexFor(projectId).withLock {
        val file = projectFile(projectId)
        if (!file.exists()) return@withLock null
        val rawData = runCatching { json.decodeFromString<ProjectData>(file.readText()) }
            .onFailure { AppLogger.e(TAG, "No se pudo leer/parsear project.json — el proyecto no se puede abrir: $projectId", it) }
            .getOrNull() ?: return@withLock null
        // FASE 1 (AUDITORÍA P0/P1) — VALIDACIÓN DEL MANIFEST: saneo de
        // valores numéricos/estructurales que, si vienen corruptos o
        // manipulados a mano en un `.olycs` (fps absurdo, duración
        // negativa, cuadrícula con 0 columnas, etc.), podrían producir
        // crashes o estados imposibles más adelante (división por cero en
        // el layout de la cuadrícula, un encoder de exportación rechazando
        // un fps fuera de rango, un timeline con duración negativa...).
        // Deliberadamente ACOTADO: no reescribe el modelo de proyecto,
        // solo satura (`coerceIn`) los campos concretos que ya se sabe que
        // el resto del código asume dentro de un rango — mismo criterio
        // que el fallback ya existente de `aspectRatio`.
        val data = sanitizeProjectData(rawData)
        val imgDir = imagesDir(projectId)

        // MEJORA PROFESIONAL: antes esto decodificaba las imágenes de las
        // capas UNA POR UNA, en secuencia — con 7 capas, el tiempo total de
        // reabrir el proyecto era la SUMA de los 7 tiempos de decodificación,
        // uno atrás del otro, aunque decodificar un archivo no tiene nada
        // que ver con decodificar el siguiente (no hay ninguna dependencia
        // entre capas). Con `async` + `awaitAll` las 7 decodificaciones
        // corren EN PARALELO sobre el pool de hilos de Dispatchers.IO — el
        // tiempo total pasa a ser (aproximadamente) el de la imagen más
        // lenta, no la suma de todas. Con proyectos de varias capas esto es
        // justo la demora perceptible al reabrir que se reportó.
        val layers = coroutineScope {
            data.layers.mapIndexed { index, layerData ->
                async { layerDataToLiveLayer(imgDir, layerData, colorIndexFallback = index) }
            }.awaitAll().filterNotNull()
        }

        val audioClip = data.audioTrack?.let { audioData ->
            // FASE 1 (AUDITORÍA P0/P1) — mismo criterio que las imágenes:
            // `audioFileName` viene del manifest, se confina a `audioDir`.
            val audioFile = resolveManifestFile(audioDir(projectId), audioData.audioFileName)
            if (audioFile == null || !audioFile.exists()) {
                AppLogger.w(TAG, "Falta o es inválido el archivo de audio del proyecto (${audioData.audioFileName}) — se carga sin audio")
                return@let null
            }
            AudioClip(
                sourceUri = Uri.fromFile(audioFile),
                displayName = audioData.displayName,
                sourceDurationMs = audioData.sourceDurationMs,
                volume = audioData.volume,
                muted = audioData.muted,
                trimStartMs = audioData.trimStartMs,
                loop = audioData.loop,
                fadeInMs = audioData.fadeInMs,
                fadeOutMs = audioData.fadeOutMs
            )
        }

        // Fallback seguro: si el valor guardado no coincide con ningún
        // preset conocido (formato viejo, o un valor de una versión
        // futura), se usa REELS en vez de que decodeFromString explote
        // y deje el proyecto entero inaccesible.
        @Suppress("DEPRECATION")
        val legacyAspect = runCatching { AspectRatioPreset.valueOf(data.aspectRatio) }
            .onFailure { AppLogger.w(TAG, "Aspect ratio guardado no reconocido ('${data.aspectRatio}'), se usa REELS por defecto", it) }
            .getOrDefault(AspectRatioPreset.REELS)

        // Ver ADR-005, Fase C: `canvasSchemaVersion >= 1` con ambas
        // dimensiones presentes significa "este project.json ya tiene un
        // Canvas real, usalo tal cual" — cualquier otro caso (proyecto
        // guardado ANTES de esta mejora, o un project.json corrupto al
        // que le falta alguno de los 2 campos pese a declarar
        // schemaVersion 1) migra desde `legacyAspect` en vez de arriesgar
        // un Canvas con dimensiones a medias.
        val canvas = if (data.canvasSchemaVersion >= 1 && data.canvasWidthPx != null && data.canvasHeightPx != null) {
            CanvasSpec(
                widthPx = data.canvasWidthPx,
                heightPx = data.canvasHeightPx,
                originPresetId = data.canvasOriginPresetId
            )
        } else {
            LegacyAspectMigration.migrate(legacyAspect)
        }

        // --- Papelera de capas ---
        // Se resuelve acá, no en el ViewModel, por el mismo criterio que
        // `castPhotoFiles` un poco más abajo: la capa de datos es la única
        // que sabe cómo confinar un nombre de archivo del manifest a su
        // directorio real (ver [resolveManifestFile]) y es la dueña del
        // flag persistido `trashBannerAcknowledged` que decide si corresponde
        // avisar. La resolución de archivos/tamaño en sí vive en
        // [buildTrashSummary], compartida con [trashInfo].
        val trashSummary = buildTrashSummary(imgDir, data)
        // Condición completa acordada: solo se avisa cuando el proyecto
        // quedó al límite (una sola capa viva) Y hay algo pendiente de
        // revisar en la papelera Y todavía no se le avisó de ESTE episodio
        // puntual (ver el comentario grande de `trashBannerAcknowledged`
        // en ProjectData sobre cuándo se reinicia a `false`).
        val showTrashRecoveryBanner = layers.size == 1 && trashSummary.items.isNotEmpty() && !data.trashBannerAcknowledged

        LoadedProject(
            id = data.id,
            name = data.name,
            projectDurationMs = data.projectDurationMs,
            layers = layers,
            audioClip = audioClip,
            speedKeyframes = data.speedKeyframes,
            freezeFrames = data.freezeFrames,
            aspect = legacyAspect,
            canvas = canvas,
            fps = data.fps,
            releaseYear = data.releaseYear,
            genre = data.genre,
            infoDurationMinutes = data.infoDurationMinutes,
            // FASE 1 (AUDITORÍA P0/P1) — mismo criterio: nombres de fotos
            // de elenco vienen del manifest, se confinan a `castDir`.
            castPhotoFiles = List(4) { index ->
                data.castPhotoFileNames.getOrNull(index)
                    ?.let { name -> resolveManifestFile(castDir(projectId), name)?.takeIf { it.exists() } }
            },
            gridEnabled = data.gridEnabled,
            gridShapeName = data.gridShapeName,
            gridColumns = data.gridColumns,
            gridRows = data.gridRows,
            gridLineColorEnabled = data.gridLineColorEnabled,
            gridLineHue = data.gridLineHue,
            gridLineThicknessDp = data.gridLineThicknessDp,
            gridLineOpacity = data.gridLineOpacity,
            gridSnapEnabled = data.gridSnapEnabled,
            handleOrderGlobal = data.handleOrderGlobal,
            handleOrderPerLayer = data.handleOrderPerLayer,
            // ARREGLADO: se restaura el frame donde se dejó el proyecto
            // (ver ProjectData.playheadMs) en vez de perderlo siempre al
            // reabrir.
            playheadMs = data.playheadMs,
            trashedLayers = trashSummary.items,
            trashSizeBytes = trashSummary.totalSizeBytes,
            showTrashRecoveryBanner = showTrashRecoveryBanner
        )
        }
    }

    /**
     * Resuelve [ProjectData.trashedLayers] a [TrashSummary] (archivos
     * confinados a `imgDir` + tamaño en disco ya sumado) — compartida entre
     * `loadProject` (arma [LoadedProject.trashedLayers] al abrir) y
     * [trashInfo] (refresco puntual desde el ViewModel después de
     * restaurar/eliminar/vaciar, sin tener que releer y reconstruir un
     * `LoadedProject` completo solo para esto).
     */
    private fun buildTrashSummary(imgDir: File, data: ProjectData): TrashSummary {
        val items = data.trashedLayers.map { deleted ->
            val file = resolveManifestFile(imgDir, deleted.layer.imageFileName)?.takeIf { it.exists() }
            TrashedLayerInfo(
                id = deleted.layer.id,
                name = deleted.layer.name,
                imageFile = file,
                deletedAtMs = deleted.deletedAtMs,
                sizeBytes = file?.length() ?: 0L
            )
        }
        return TrashSummary(items = items, totalSizeBytes = items.sumOf { it.sizeBytes })
    }

    /** Papelera actual de un proyecto, ya resuelta para la UI — ver [buildTrashSummary]. */
    suspend fun trashInfo(projectId: String): TrashSummary = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            if (!file.exists()) return@withLock TrashSummary(emptyList(), 0L)
            val data = runCatching { json.decodeFromString<ProjectData>(file.readText()) }.getOrNull()
                ?: return@withLock TrashSummary(emptyList(), 0L)
            buildTrashSummary(imagesDir(projectId), data)
        }
    }

    /**
     * Restaura de la papelera las capas cuyo id esté en [layerIds],
     * devolviéndolas ya reconstruidas EN VIVO (ver [layerDataToLiveLayer])
     * para que `EditorViewModel` las agregue de vuelta a
     * `EditorUiState.layers` — esta función NO toca el estado en memoria
     * del editor, solo el `project.json` (saca las entradas restauradas de
     * `trashedLayers`) y devuelve el material para que el ViewModel arme
     * el resto.
     *
     * Un id pedido que ya no está en la papelera (p. ej. otra pestaña/
     * sesión la restauró o la vació primero) se ignora en silencio — no es
     * un error, es una carrera benigna resuelta a favor de "lo que de
     * verdad hay en disco ahora".
     */
    suspend fun restoreLayersFromTrash(projectId: String, layerIds: Set<String>): List<Layer> = withContext(Dispatchers.IO) {
        if (layerIds.isEmpty()) return@withContext emptyList()
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            val data = runCatching { json.decodeFromString<ProjectData>(file.readText()) }
                .onFailure { AppLogger.e(TAG, "No se pudo leer project.json para restaurar de la papelera: $projectId", it) }
                .getOrNull() ?: return@withLock emptyList()

            val toRestore = data.trashedLayers.filter { it.layer.id in layerIds }
            if (toRestore.isEmpty()) return@withLock emptyList()

            val remaining = data.trashedLayers - toRestore.toSet()
            runCatching {
                writeProjectDataAtomically(file, data.copy(trashedLayers = remaining))
            }.onFailure {
                AppLogger.e(TAG, "No se pudo guardar project.json tras restaurar capas de la papelera: $projectId", it)
                return@withLock emptyList()
            }

            val imgDir = imagesDir(projectId)
            toRestore.mapNotNull { deleted -> layerDataToLiveLayer(imgDir, deleted.layer, colorIndexFallback = 0) }
        }
    }

    /**
     * Elimina PARA SIEMPRE de la papelera las capas cuyo id esté en
     * [layerIds]: saca las entradas de `trashedLayers` y borra sus
     * imágenes de `images/` — a partir de acá no hay forma de recuperarlas
     * (ver el diálogo de confirmación en LayerTrashScreen.kt, que es donde
     * corresponde advertirle esto al usuario, no acá).
     */
    suspend fun deleteLayersFromTrashPermanently(projectId: String, layerIds: Set<String>): TrashSummary = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            val data = runCatching { json.decodeFromString<ProjectData>(file.readText()) }
                .onFailure { AppLogger.e(TAG, "No se pudo leer project.json para eliminar de la papelera: $projectId", it) }
                .getOrNull() ?: return@withLock TrashSummary(emptyList(), 0L)

            val toDelete = data.trashedLayers.filter { it.layer.id in layerIds }
            val remaining = data.trashedLayers - toDelete.toSet()
            runCatching {
                writeProjectDataAtomically(file, data.copy(trashedLayers = remaining))
            }.onFailure { AppLogger.e(TAG, "No se pudo guardar project.json tras eliminar capas de la papelera: $projectId", it) }

            // Igual criterio que el resto de la clase: los archivos se
            // borran DESPUÉS de confirmar el manifest nuevo, nunca antes —
            // si el guardado de arriba hubiera fallado, esta línea no debe
            // ejecutarse dejando el manifest viejo apuntando a nada.
            val imgDir = imagesDir(projectId)
            toDelete.forEach { deleted ->
                runCatching { resolveManifestFile(imgDir, deleted.layer.imageFileName)?.delete() }
                    .onFailure { AppLogger.w(TAG, "No se pudo borrar la imagen de una capa eliminada de la papelera (no crítico): $projectId", it) }
            }
            buildTrashSummary(imgDir, data.copy(trashedLayers = remaining))
        }
    }

    /** Vacía la papelera completa de un proyecto — ver [deleteLayersFromTrashPermanently], mismo criterio aplicado a TODAS las entradas. */
    suspend fun emptyTrash(projectId: String): TrashSummary = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            val data = runCatching { json.decodeFromString<ProjectData>(file.readText()) }
                .onFailure { AppLogger.e(TAG, "No se pudo leer project.json para vaciar la papelera: $projectId", it) }
                .getOrNull() ?: return@withLock TrashSummary(emptyList(), 0L)
            if (data.trashedLayers.isEmpty()) return@withLock TrashSummary(emptyList(), 0L)

            val toDelete = data.trashedLayers
            runCatching {
                writeProjectDataAtomically(file, data.copy(trashedLayers = emptyList()))
            }.onFailure { AppLogger.e(TAG, "No se pudo guardar project.json tras vaciar la papelera: $projectId", it) }

            val imgDir = imagesDir(projectId)
            toDelete.forEach { deleted ->
                runCatching { resolveManifestFile(imgDir, deleted.layer.imageFileName)?.delete() }
                    .onFailure { AppLogger.w(TAG, "No se pudo borrar la imagen de una capa al vaciar la papelera (no crítico): $projectId", it) }
            }
            TrashSummary(emptyList(), 0L)
        }
    }

    /**
     * Marca como "ya visto" el aviso de recuperación de la papelera (ver
     * [ProjectData.trashBannerAcknowledged]) para que no se lo vuelva a
     * mostrar por ESTE MISMO episodio la próxima vez que se abra el
     * proyecto.
     */
    suspend fun acknowledgeTrashBanner(projectId: String): Unit = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            val data = runCatching { json.decodeFromString<ProjectData>(file.readText()) }
                .onFailure { AppLogger.e(TAG, "No se pudo leer project.json para reconocer el aviso de papelera: $projectId", it) }
                .getOrNull() ?: return@withLock
            if (data.trashBannerAcknowledged) return@withLock
            runCatching {
                writeProjectDataAtomically(file, data.copy(trashBannerAcknowledged = true))
            }.onFailure { AppLogger.e(TAG, "No se pudo guardar project.json al reconocer el aviso de papelera: $projectId", it) }
        }
    }

    suspend fun deleteProject(projectId: String): Unit = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            try {
                val deleted = projectDir(projectId).deleteRecursively()
                if (!deleted) AppLogger.w(TAG, "deleteRecursively no pudo borrar todos los archivos del proyecto: $projectId")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Error borrando la carpeta del proyecto: $projectId", t)
            }
            Unit
        }
    }

    /** Duplica un proyecto entero (json + imágenes + audio + miniatura) bajo un id nuevo, como "Copia de...". */
    suspend fun duplicateProject(projectId: String, newName: String): String? = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val srcDir = projectDir(projectId)
            if (!File(srcDir, "project.json").exists()) {
                AppLogger.w(TAG, "No se puede duplicar: no existe project.json de origen: $projectId")
                return@withLock null
            }
            val newId = newProjectId()
            val dstDir = projectDir(newId)
            try {
                srcDir.copyRecursively(dstDir, overwrite = true)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Error copiando los archivos al duplicar el proyecto: $projectId", t)
                return@withLock null
            }

            val srcData = runCatching { json.decodeFromString<ProjectData>(File(dstDir, "project.json").readText()) }
                .onFailure { AppLogger.e(TAG, "No se pudo leer el project.json copiado al duplicar: $projectId", it) }
                .getOrNull()
                ?: return@withLock null
            val updated = srcData.copy(
                id = newId,
                name = newName,
                updatedAtMs = System.currentTimeMillis(),
                orderIndex = -System.currentTimeMillis()
            )
            writeProjectDataAtomically(File(dstDir, "project.json"), updated)
            newId
        }
    }

    /**
     * Actualiza nombre y/o descripción del proyecto en un solo guardado
     * (mismo diálogo en la UI: "Renombrar" ahora también edita la
     * descripción corta). [newDescription] ya debe venir recortada a
     * [DESCRIPTION_MAX_LENGTH] — la UI se encarga de eso mientras el
     * usuario tipea, acá simplemente se persiste tal cual llega.
     */
    suspend fun renameProject(projectId: String, newName: String, newDescription: String? = null): Unit =
        withContext(Dispatchers.IO) {
            mutexFor(projectId).withLock {
                val file = projectFile(projectId)
                if (!file.exists()) return@withLock
                runCatching {
                    val data = json.decodeFromString<ProjectData>(file.readText())
                    writeProjectDataAtomically(
                        file,
                        data.copy(
                            name = newName,
                            description = newDescription ?: data.description,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    )
                }.onFailure { AppLogger.e(TAG, "Error renombrando el proyecto: $projectId", it) }
            }
        }

    /**
     * Copia [uri] como portada personalizada del proyecto (`cover.jpg`,
     * reemplazando cualquier portada anterior) y la deja activa. A
     * diferencia de la miniatura auto-generada (que siempre refleja el
     * look real del proyecto en el momento de guardar), esta es una
     * elección manual del usuario y el autoguardado del editor nunca la
     * pisa — ver [saveProject].
     */
    /**
     * FASE 1 (AUDITORÍA P0/P1) — mismo criterio de integridad que
     * [ensureLocalImage]: copia [uri] a [dest] y solo devuelve `true` si
     * el `InputStream` de origen existía de verdad y el archivo destino
     * quedó escrito con contenido real. Se usa desde [setCoverImage] y
     * [setCastPhoto] — antes, ambos hacían
     * `openInputStream(uri)?.use { ... }` dentro de un `runCatching` (el
     * mismo patrón con el mismo hueco: un stream `null` no lanza
     * excepción, así que `runCatching` lo veía como éxito). Ya tenían una
     * segunda comprobación (`dest.exists()`) que amortiguaba el caso más
     * grave para una portada NUEVA (el archivo nunca se llega a crear), pero
     * no cubría el caso de reemplazar una portada YA existente con un Uri
     * inválido: `dest` seguía existiendo (la copia VIEJA), así que
     * `dest.exists()` daba `true` aunque la copia nueva jamás hubiera
     * pasado — el método terminaba "confirmando" una portada que en
     * realidad no cambió, sin avisar del fallo. Acá se borra [dest] ANTES
     * de intentar copiar, así `exists()` después solo puede ser cierto si
     * la copia nueva realmente se escribió.
     */
    private fun copyUriToFileOrFail(uri: Uri, dest: File): Boolean {
        runCatching { if (dest.exists()) dest.delete() }
        val copied = runCatching {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("openInputStream() devolvió null para $uri — no hay datos que copiar")
            input.use { inp -> dest.outputStream().use { out -> inp.copyTo(out) } }
        }
        if (copied.isFailure || !dest.exists() || dest.length() == 0L) {
            runCatching { if (dest.exists()) dest.delete() }
            return false
        }
        return true
    }

    suspend fun setCoverImage(projectId: String, uri: Uri): Unit = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            if (!file.exists()) return@withLock
            val dest = coverFile(projectId)
            if (!copyUriToFileOrFail(uri, dest)) {
                AppLogger.e(TAG, "No se pudo copiar la portada elegida (origen inválido o sin datos): $projectId")
                return@withLock
            }
            runCatching {
                val data = json.decodeFromString<ProjectData>(file.readText())
                writeProjectDataAtomically(
                    file,
                    data.copy(coverImageFileName = dest.name, updatedAtMs = System.currentTimeMillis())
                )
            }.onFailure { AppLogger.e(TAG, "No se pudo guardar la referencia a la nueva portada en project.json: $projectId", it) }
        }
    }

    /**
     * Guarda [bitmap] YA recortado/encuadrado (salida del editor "Ajustar
     * portada" — ver [com.yeivikas.olyzecs.ui.CoverAdjustDialog])
     * como portada personalizada del proyecto. A diferencia de
     * [setCoverImage] (que copia el archivo elegido tal cual, a ciegas),
     * este método persiste exactamente el encuadre que el usuario centró a
     * mano, con la misma relación de aspecto que la tarjeta de "Mis
     * proyectos".
     */
    suspend fun setCoverImageBitmap(projectId: String, bitmap: Bitmap): Unit = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            if (!file.exists()) return@withLock
            val dest = coverFile(projectId)
            val saved = runCatching {
                dest.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            }.onFailure { AppLogger.e(TAG, "No se pudo guardar el bitmap de portada recortada: $projectId", it) }
                .isSuccess
            if (!saved || !dest.exists()) return@withLock
            runCatching {
                val data = json.decodeFromString<ProjectData>(file.readText())
                writeProjectDataAtomically(
                    file,
                    data.copy(coverImageFileName = dest.name, updatedAtMs = System.currentTimeMillis())
                )
            }.onFailure { AppLogger.e(TAG, "No se pudo guardar la referencia a la portada recortada en project.json: $projectId", it) }
        }
    }

    /** Quita la portada personalizada y vuelve a mostrar la miniatura auto-generada de siempre. */
    suspend fun removeCoverImage(projectId: String): Unit = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            if (!file.exists()) return@withLock
            coverFile(projectId).delete()
            runCatching {
                val data = json.decodeFromString<ProjectData>(file.readText())
                writeProjectDataAtomically(
                    file,
                    data.copy(coverImageFileName = null, updatedAtMs = System.currentTimeMillis())
                )
            }.onFailure { AppLogger.e(TAG, "No se pudo quitar la portada personalizada en project.json: $projectId", it) }
        }
    }

    /**
     * Copia [uri] como la foto de una casilla de elenco/personajes
     * ([slotIndex] 0..3) del panel "Información del proyecto" —
     * `cast/cast_<slotIndex>.jpg`, reemplazando cualquier foto anterior en
     * esa misma casilla. Mismo patrón que [setCoverImage]: guardado directo
     * e inmediato, no pasa por el autoguardado normal del editor.
     */
    suspend fun setCastPhoto(projectId: String, slotIndex: Int, uri: Uri): Unit = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            if (!file.exists() || slotIndex !in 0..3) return@withLock
            val dest = castPhotoFileForSlot(projectId, slotIndex)
            if (!copyUriToFileOrFail(uri, dest)) {
                AppLogger.e(TAG, "No se pudo copiar la foto de elenco (casilla $slotIndex, origen inválido o sin datos): $projectId")
                return@withLock
            }
            runCatching {
                val data = json.decodeFromString<ProjectData>(file.readText())
                val names = List(4) { i -> if (i == slotIndex) dest.name else data.castPhotoFileNames.getOrNull(i) }
                writeProjectDataAtomically(
                    file,
                    data.copy(castPhotoFileNames = names, updatedAtMs = System.currentTimeMillis())
                )
            }.onFailure { AppLogger.e(TAG, "No se pudo guardar la referencia a la foto de elenco en project.json: $projectId", it) }
        }
    }

    /** Quita la foto de una casilla de elenco/personajes, dejándola vacía. */
    suspend fun removeCastPhoto(projectId: String, slotIndex: Int): Unit = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            if (!file.exists() || slotIndex !in 0..3) return@withLock
            castPhotoFileForSlot(projectId, slotIndex).delete()
            runCatching {
                val data = json.decodeFromString<ProjectData>(file.readText())
                val names = List(4) { i -> if (i == slotIndex) null else data.castPhotoFileNames.getOrNull(i) }
                writeProjectDataAtomically(
                    file,
                    data.copy(castPhotoFileNames = names, updatedAtMs = System.currentTimeMillis())
                )
            }.onFailure { AppLogger.e(TAG, "No se pudo quitar la foto de elenco en project.json: $projectId", it) }
        }
    }

    /**
     * Reordena manualmente un proyecto un lugar hacia arriba o abajo en
     * "Mis proyectos", intercambiando su [ProjectData.orderIndex] con el del
     * vecino inmediato en ese sentido. No hace nada si el proyecto ya está
     * en la punta correspondiente (primero para UP, último para DOWN).
     * Bloquea ambos proyectos afectados, siempre en el mismo orden (por id)
     * para no arriesgar un deadlock si dos movimientos se disparan a la vez.
     */
    suspend fun moveProject(projectId: String, direction: MoveDirection): Unit = withContext(Dispatchers.IO) {
        val ordered = loadAllProjectDataSorted()
        val index = ordered.indexOfFirst { (_, data) -> data.id == projectId }
        if (index < 0) return@withContext
        val neighborIndex = if (direction == MoveDirection.UP) index - 1 else index + 1
        if (neighborIndex !in ordered.indices) return@withContext

        val (currentDir, currentData) = ordered[index]
        val (neighborDir, neighborData) = ordered[neighborIndex]
        val currentKey = orderKey(currentData)
        val neighborKey = orderKey(neighborData)

        // Se bloquean los dos proyectos afectados siempre en el mismo orden
        // (comparando id como String) para que dos movimientos disparados a
        // la vez no puedan llegar a tomar los mutexes al revés y deadlockear
        // entre sí.
        val (firstId, secondId) = if (currentData.id < neighborData.id) {
            currentData.id to neighborData.id
        } else {
            neighborData.id to currentData.id
        }

        mutexFor(firstId).withLock {
            mutexFor(secondId).withLock {
                runCatching {
                    writeProjectDataAtomically(File(currentDir, "project.json"), currentData.copy(orderIndex = neighborKey))
                }.onFailure { AppLogger.e(TAG, "Error guardando el nuevo orden del proyecto: ${currentData.id}", it) }
                runCatching {
                    writeProjectDataAtomically(File(neighborDir, "project.json"), neighborData.copy(orderIndex = currentKey))
                }.onFailure { AppLogger.e(TAG, "Error guardando el nuevo orden del proyecto vecino: ${neighborData.id}", it) }
            }
        }
    }

    /** Duración total del archivo de audio, usada al importar para poblar [AudioClip.sourceDurationMs]. */
    fun probeAudioDurationMs(uri: Uri): Long = AudioProcessor.probeDurationMs(appContext, uri)

    // ------------------------------------------------------------------
    // Compartir / colaborar: exportar un proyecto entero como un único
    // archivo ".olycs" (un zip renombrado) que otra persona puede
    // abrir con Olyze Creation Studio en SU propio teléfono — mismas capas,
    // keyframes, look, audio e imágenes, listo para seguir editando. Vive
    // en `getExternalFilesDir()/exports/`, la carpeta que ya declara
    // [file_paths.xml] para el FileProvider (necesario para poder
    // compartirlo con cualquier otra app vía Intent.ACTION_SEND).
    // ------------------------------------------------------------------

    private fun exportsDir(): File =
        File(appContext.getExternalFilesDir(null), "exports").apply { mkdirs() }

    /** Nombre de archivo seguro a partir del nombre del proyecto (sin caracteres que rompan rutas). */
    private fun safeFileName(name: String): String {
        val cleaned = name.trim().ifBlank { "proyecto" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(60)
        return cleaned.ifBlank { "proyecto" }
    }

    /**
     * Empaqueta la carpeta completa del proyecto (`project.json` + `images/`
     * + `audio/` + `thumbnail.jpg` + `cover.jpg`) en un único archivo
     * `<nombre>.olycs` dentro de `exports/`, listo para compartir. La
     * extensión es solo cosmética — internamente es un `.zip` estándar, así
     * que si alguien lo abre con cualquier descompresor común igual va a
     * poder ver su contenido.
     */
    suspend fun exportProjectZip(projectId: String): File? = withContext(Dispatchers.IO) {
        val dir = projectDir(projectId)
        val jsonFile = File(dir, "project.json")
        if (!jsonFile.exists()) return@withContext null

        val data = runCatching { json.decodeFromString<ProjectData>(jsonFile.readText()) }
            .onFailure { AppLogger.w(TAG, "No se pudo leer el nombre del proyecto al exportar, se usa el id: $projectId", it) }
            .getOrNull()
        // Mismo default que en saveProject: si por lo que sea el nombre
        // guardado quedó vacío (proyectos viejos de antes de esta mejora,
        // o el .olycs todavía no se guardó ni una vez), se comparte como
        // "Project01.olycs" en vez de caer en el id (UUID) del proyecto.
        val outFile = File(exportsDir(), "${safeFileName(data?.name?.ifBlank { DEFAULT_PROJECT_NAME } ?: DEFAULT_PROJECT_NAME)}.olycs")
        runCatching { outFile.delete() }
            .onFailure { AppLogger.w(TAG, "No se pudo borrar un .olycs previo con el mismo nombre antes de exportar", it) }

        runCatching {
            ZipOutputStream(FileOutputStream(outFile).buffered()).use { zipOut ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val relativePath = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                    zipOut.putNextEntry(ZipEntry(relativePath))
                    f.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }.onFailure {
            AppLogger.e(TAG, "Error exportando el proyecto a .olycs: $projectId", it)
            return@withContext null
        }

        outFile.takeIf { it.exists() && it.length() > 0 }
            ?: run { AppLogger.e(TAG, "El .olycs exportado no quedó creado o quedó vacío: $projectId"); null }
    }

    /**
     * Contraparte de [exportProjectZip]: importa un `.olycs` recibido de
     * otra persona (o de otro dispositivo propio) como un proyecto NUEVO e
     * independiente — nunca pisa un proyecto existente, incluso si viene del
     * mismo `id` original, para evitar que abrir por error el mismo archivo
     * dos veces borre trabajo propio. Devuelve el id del proyecto ya
     * importado y listo para abrir, o `null` si el archivo no es un
     * `.olycs` válido.
     */
    suspend fun importProjectZip(uri: Uri): String? = withContext(Dispatchers.IO) {
        val newId = newProjectId()
        val dstDir = projectDir(newId)

        // Núcleo de seguridad (validación Zip Slip + extracción) delegado a
        // [extractZipEntriesSafely] — extraído a una función pura, sin
        // Context, en la Fase A de tests, para poder probarlo con un ZIP
        // malicioso armado a mano en un JVM unit test (ver
        // ProjectStorageZipSlipTest). Comportamiento idéntico al de antes.
        val extracted = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zipIn -> extractZipEntriesSafely(zipIn, dstDir) }
            } ?: false
        }.onFailure { AppLogger.e(TAG, "Error extrayendo el archivo .olycs importado", it) }
            .getOrDefault(false)

        if (!extracted || !File(dstDir, "project.json").exists()) {
            AppLogger.e(TAG, "El archivo importado no es un .olycs válido (sin project.json tras extraer)")
            dstDir.deleteRecursively()
            return@withContext null
        }

        // Se reescribe el id interno para que coincida con la carpeta nueva
        // (`newId`) y se reubica al tope de "Mis proyectos", como cualquier
        // proyecto recién creado.
        runCatching {
            val jsonFile = File(dstDir, "project.json")
            val data = json.decodeFromString<ProjectData>(jsonFile.readText())
            writeProjectDataAtomically(
                jsonFile,
                data.copy(
                    id = newId,
                    updatedAtMs = System.currentTimeMillis(),
                    orderIndex = -System.currentTimeMillis()
                )
            )
        }.onFailure {
            AppLogger.e(TAG, "Error reescribiendo el id interno del proyecto importado", it)
            dstDir.deleteRecursively()
            return@withContext null
        }

        newId
    }
}
