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
import com.yeivikas.olyzecs.engine.scene.AspectRatioPreset
import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.engine.timeline.ThumbnailRenderer
import com.yeivikas.olyzecs.debug.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val canMoveDown: Boolean = false
) {
    /** La imagen que la tarjeta debe mostrar: portada elegida a mano si existe, si no la miniatura auto-generada. */
    val displayImageFile: File? get() = coverImageFile ?: thumbnailFile
}

/** Dirección para reordenar manualmente un proyecto en "Mis proyectos". */
enum class MoveDirection { UP, DOWN }

/**
 * Verifica que el nombre de una entrada de ZIP sea seguro para extraer:
 * ninguna entrada puede contener ".." en su ruta ya normalizada (protección
 * Zip Slip — evita que una entrada maliciosa como "../../evil.txt" o
 * "subdir/../../evil.txt" escriba fuera del directorio destino).
 *
 * Función pura (sin I/O, sin `Context` de Android) EXTRAÍDA A PROPÓSITO de
 * [extractZipEntriesSafely]/[ProjectStorage.importProjectZip] en la Fase A
 * de tests: es el único cambio de producción de esa fase, y existe
 * exclusivamente para poder probar la regla de seguridad en un test JVM
 * puro (`ProjectStorageZipSlipTest`), sin necesitar un `Context` Android ni
 * un dispositivo/emulador. El comportamiento es idéntico al que había
 * antes inline dentro de `importProjectZip`.
 */
internal fun isSafeZipEntryName(rawName: String): Boolean {
    val normalized = rawName.replace('\\', '/')
    return !normalized.contains("..")
}

/**
 * Extrae hacia [destDir] todas las entradas de [zipIn] que sean archivos
 * (no directorios) y cuyo nombre sea seguro según [isSafeZipEntryName].
 * Devuelve `true` si se extrajo al menos un archivo.
 *
 * Función pura de I/O de archivos (`java.io.File`/`java.util.zip`, sin
 * `Context` de Android) EXTRAÍDA A PROPÓSITO de [ProjectStorage.importProjectZip]
 * en la Fase A de tests, por el mismo motivo que [isSafeZipEntryName]: es
 * el núcleo de seguridad de la importación de proyectos, y separarlo del
 * método de la clase (que sí necesita `Context` para resolver el `Uri` de
 * origen) permite construir un ZIP malicioso a mano en un JVM unit test y
 * verificar que ningún archivo termina fuera de [destDir] — no solo que se
 * "rechaza" la entrada, sino la propiedad de seguridad real. Comportamiento
 * idéntico al bucle que había antes inline dentro de `importProjectZip`.
 */
internal fun extractZipEntriesSafely(zipIn: ZipInputStream, destDir: File): Boolean {
    var entry = zipIn.nextEntry
    var any = false
    while (entry != null) {
        if (!entry.isDirectory && isSafeZipEntryName(entry.name)) {
            val outFile = File(destDir, entry.name.replace('\\', '/'))
            outFile.parentFile?.mkdirs()
            outFile.outputStream().use { out -> zipIn.copyTo(out) }
            any = true
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

/** Resultado de cargar un proyecto: metadata + capas + audio ya reconstruidos y listos para el ViewModel. */
data class LoadedProject(
    val id: String,
    val name: String,
    val projectDurationMs: Long,
    val layers: List<Layer>,
    val audioClip: AudioClip?,
    val speedKeyframes: List<SpeedKeyframe> = emptyList(),
    val freezeFrames: List<FreezeFrame> = emptyList(),
    val aspect: AspectRatioPreset = AspectRatioPreset.REELS,
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
    val handleOrderPerLayer: Map<String, Map<String, String>> = emptyMap()
)

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
class ProjectStorage(context: Context) {

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
        ordered.mapIndexed { index, (dir, data) ->
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
                coverImageFile = data.coverImageFileName?.let { name -> File(dir, name).takeIf { it.exists() } },
                orderIndex = data.orderIndex,
                sizeBytes = dir.totalSizeBytes(),
                releaseYear = data.releaseYear,
                genre = data.genre,
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
        layers: List<Layer>,
        audioClip: AudioClip? = null,
        speedKeyframes: List<SpeedKeyframe> = emptyList(),
        freezeFrames: List<FreezeFrame> = emptyList(),
        aspect: AspectRatioPreset = AspectRatioPreset.REELS,
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
        // Devuelve el nombre EFECTIVO que quedó guardado — normalmente
        // `name` tal cual, pero si vino vacío/solo espacios (el usuario no
        // escribió título en el panel "Información del proyecto") acá se
        // aplica el único default de todo el proyecto (DEFAULT_PROJECT_NAME
        // = "Project01"), que después usan tanto EditorViewModel (para
        // volver a mostrarlo en el campo) como exportProjectZip (para el
        // nombre del .olycs al compartir).
    ): String = withContext(Dispatchers.IO) {
        if (layers.isEmpty()) return@withContext name.ifBlank { DEFAULT_PROJECT_NAME }
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

        val layerDataList = layers.map { layer ->
            val fileName = ensureLocalImage(layer, imgDir)
            LayerData(
                id = layer.id,
                imageFileName = fileName,
                name = layer.name,
                zIndex = layer.zIndex,
                parallaxFactor = layer.parallaxFactor,
                locked = layer.locked,
                orderLocked = layer.orderLocked,
                visible = layer.visible,
                lookSettings = layer.lookSettings,
                keyframes = layer.cameraTrack.keyframes,
                baseFrame = layer.cameraTrack.baseFrame,
                widthPx = layer.widthPx,
                heightPx = layer.heightPx,
                colorIndex = layer.colorIndex,
                customColorArgb = layer.customColorArgb,
                importedDefaultColorArgb = layer.importedDefaultColorArgb,
                customGradientStartArgb = layer.customGradientStartArgb,
                customGradientEndArgb = layer.customGradientEndArgb,
                useGradientColor = layer.useGradientColor,
                gradientAngleDegrees = layer.gradientAngleDegrees,
                gradientIsRadial = layer.gradientIsRadial,
                useBlackAndWhiteMode = layer.useBlackAndWhiteMode
            )
        }

        // Limpieza: borra copias locales de capas que ya no están en el proyecto
        // (p. ej. tras eliminar una capa), para no acumular basura en disco.
        val validNames = layerDataList.map { it.imageFileName }.toSet()
        imgDir.listFiles()?.forEach { f -> if (f.name !in validNames) f.delete() }

        val audioDataResult = audioClip?.let { clip ->
            val fileName = ensureLocalAudio(clip, audioDir(projectId))
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
        // Si se quitó el audio del proyecto (audioClip == null pero antes había
        // uno), se limpia también su copia local para no dejar basura huérfana.
        if (audioDataResult == null) {
            audioDir(projectId).listFiles()?.forEach { it.delete() }
        }

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
            aspectRatio = aspect.name,
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
            handleOrderPerLayer = handleOrderPerLayer
        )
        try {
            projectFile(projectId).writeText(json.encodeToString(data))
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error crítico guardando project.json — el proyecto podría no haberse guardado: $projectId", t)
            throw t
        }

        val thumbnail = try {
            ThumbnailRenderer.render(appContext, layers, timeMs = playheadMs)
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
            data.name
        }
    }

    /**
     * Asegura que la capa tenga su copia local dentro del proyecto. Si el
     * `sourceUri` ya apunta a esa copia (proyecto recién cargado, imagen sin
     * cambios), no vuelve a copiar nada. Si apunta a un Uri de SAF fresco
     * (importación nueva o "reemplazar imagen"), copia el contenido una vez.
     */
    private fun ensureLocalImage(layer: Layer, imgDir: File): String {
        val fileName = "${layer.id}.png"
        val destFile = File(imgDir, fileName)
        val alreadyLocalCopy = layer.sourceUri.scheme == "file" && layer.sourceUri.path == destFile.absolutePath
        if (!alreadyLocalCopy || !destFile.exists()) {
            runCatching {
                appContext.contentResolver.openInputStream(layer.sourceUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure {
                AppLogger.e(TAG, "No se pudo copiar la imagen de la capa '${layer.name}' a almacenamiento local — esa capa podría perderse al reabrir el proyecto", it)
            }.onSuccess {
                // A partir de ahora esta capa "vive" localmente: los próximos
                // autoguardados no necesitan volver a copiar desde el Uri
                // original de SAF en cada slider que se mueve — y de paso la
                // capa queda protegida ante ese Uri si se invalida más tarde.
                layer.sourceUri = Uri.fromFile(destFile)
            }
        }
        return fileName
    }

    /**
     * Mismo patrón que [ensureLocalImage] pero para el audio de fondo.
     * El nombre de archivo local conserva la extensión original (mp3, m4a,
     * wav...) porque algunos decodificadores del sistema la usan como pista
     * adicional para elegir el demuxer correcto.
     */
    private fun ensureLocalAudio(clip: AudioClip, dir: File): String {
        val extension = guessAudioExtension(clip.displayName)
        val fileName = "audio.$extension"
        val destFile = File(dir, fileName)
        val alreadyLocalCopy = clip.sourceUri.scheme == "file" && clip.sourceUri.path == destFile.absolutePath
        if (!alreadyLocalCopy || !destFile.exists()) {
            // Antes de copiar, se limpia cualquier audio anterior con otro nombre
            // (p. ej. si el usuario reemplazó el audio de fondo por uno con
            // distinta extensión).
            dir.listFiles()?.forEach { it.delete() }
            runCatching {
                appContext.contentResolver.openInputStream(clip.sourceUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure {
                AppLogger.e(TAG, "No se pudo copiar el audio '${clip.displayName}' a almacenamiento local — podría perderse al reabrir el proyecto", it)
            }.onSuccess {
                clip.sourceUri = Uri.fromFile(destFile)
            }
        }
        return fileName
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

    suspend fun loadProject(projectId: String): LoadedProject? = withContext(Dispatchers.IO) {
        val file = projectFile(projectId)
        if (!file.exists()) return@withContext null
        val data = runCatching { json.decodeFromString<ProjectData>(file.readText()) }
            .onFailure { AppLogger.e(TAG, "No se pudo leer/parsear project.json — el proyecto no se puede abrir: $projectId", it) }
            .getOrNull() ?: return@withContext null
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
                async {
                    val imageFile = File(imgDir, layerData.imageFileName)
                    if (!imageFile.exists()) {
                        AppLogger.e(TAG, "Falta el archivo de imagen de la capa '${layerData.name}' (${layerData.imageFileName}) — esa capa no se va a mostrar")
                        return@async null
                    }
                    val decoded = runCatching { ImageDecoding.decodeSampledFromFile(imageFile) }
                        .onFailure { AppLogger.e(TAG, "Error decodificando la imagen de la capa '${layerData.name}'", it) }
                        .getOrNull()
                        ?: return@async null

                    Layer(
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
                        colorIndex = layerData.colorIndex ?: index,
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
                        pendingBitmap = decoded
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val audioClip = data.audioTrack?.let { audioData ->
            val audioFile = File(audioDir(projectId), audioData.audioFileName)
            if (!audioFile.exists()) {
                AppLogger.w(TAG, "Falta el archivo de audio del proyecto (${audioData.audioFileName}) — se carga sin audio")
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

        LoadedProject(
            id = data.id,
            name = data.name,
            projectDurationMs = data.projectDurationMs,
            layers = layers,
            audioClip = audioClip,
            speedKeyframes = data.speedKeyframes,
            freezeFrames = data.freezeFrames,
            // Fallback seguro: si el valor guardado no coincide con ningún
            // preset conocido (formato viejo, o un valor de una versión
            // futura), se usa REELS en vez de que decodeFromString explote
            // y deje el proyecto entero inaccesible.
            aspect = runCatching { AspectRatioPreset.valueOf(data.aspectRatio) }
                .onFailure { AppLogger.w(TAG, "Aspect ratio guardado no reconocido ('${data.aspectRatio}'), se usa REELS por defecto", it) }
                .getOrDefault(AspectRatioPreset.REELS),
            fps = data.fps,
            releaseYear = data.releaseYear,
            genre = data.genre,
            infoDurationMinutes = data.infoDurationMinutes,
            castPhotoFiles = List(4) { index ->
                data.castPhotoFileNames.getOrNull(index)
                    ?.let { name -> File(castDir(projectId), name).takeIf { it.exists() } }
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
            handleOrderPerLayer = data.handleOrderPerLayer
        )
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
            File(dstDir, "project.json").writeText(json.encodeToString(updated))
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
                    file.writeText(
                        json.encodeToString(
                            data.copy(
                                name = newName,
                                description = newDescription ?: data.description,
                                updatedAtMs = System.currentTimeMillis()
                            )
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
    suspend fun setCoverImage(projectId: String, uri: Uri): Unit = withContext(Dispatchers.IO) {
        mutexFor(projectId).withLock {
            val file = projectFile(projectId)
            if (!file.exists()) return@withLock
            val dest = coverFile(projectId)
            val copied = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure { AppLogger.e(TAG, "No se pudo copiar la portada elegida: $projectId", it) }
                .isSuccess
            if (!copied || !dest.exists()) return@withLock
            runCatching {
                val data = json.decodeFromString<ProjectData>(file.readText())
                file.writeText(
                    json.encodeToString(
                        data.copy(coverImageFileName = dest.name, updatedAtMs = System.currentTimeMillis())
                    )
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
                file.writeText(
                    json.encodeToString(
                        data.copy(coverImageFileName = dest.name, updatedAtMs = System.currentTimeMillis())
                    )
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
                file.writeText(
                    json.encodeToString(
                        data.copy(coverImageFileName = null, updatedAtMs = System.currentTimeMillis())
                    )
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
            val copied = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure { AppLogger.e(TAG, "No se pudo copiar la foto de elenco (casilla $slotIndex): $projectId", it) }
                .isSuccess
            if (!copied || !dest.exists()) return@withLock
            runCatching {
                val data = json.decodeFromString<ProjectData>(file.readText())
                val names = List(4) { i -> if (i == slotIndex) dest.name else data.castPhotoFileNames.getOrNull(i) }
                file.writeText(
                    json.encodeToString(
                        data.copy(castPhotoFileNames = names, updatedAtMs = System.currentTimeMillis())
                    )
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
                file.writeText(
                    json.encodeToString(
                        data.copy(castPhotoFileNames = names, updatedAtMs = System.currentTimeMillis())
                    )
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
                    File(currentDir, "project.json")
                        .writeText(json.encodeToString(currentData.copy(orderIndex = neighborKey)))
                }.onFailure { AppLogger.e(TAG, "Error guardando el nuevo orden del proyecto: ${currentData.id}", it) }
                runCatching {
                    File(neighborDir, "project.json")
                        .writeText(json.encodeToString(neighborData.copy(orderIndex = currentKey)))
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
            jsonFile.writeText(
                json.encodeToString(
                    data.copy(
                        id = newId,
                        updatedAtMs = System.currentTimeMillis(),
                        orderIndex = -System.currentTimeMillis()
                    )
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
