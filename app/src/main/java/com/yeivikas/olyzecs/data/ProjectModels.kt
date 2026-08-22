package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.Keyframe
import com.yeivikas.olyzecs.engine.effects.LookSettings
import kotlinx.serialization.Serializable

/**
 * Nombre por defecto cuando el usuario no escribe ningún título en el
 * campo "Título" del panel "Información del proyecto" (ver
 * ProjectInfoPanel en EditorBottomBar.kt). Se usa como único fallback en
 * TODO el proyecto: el nombre guardado en `project.json`, el nombre del
 * archivo `.olycs` al compartir/exportar (ver ProjectStorage.exportProjectZip)
 * y el nombre inicial al crear un proyecto nuevo. El campo de texto en sí
 * puede quedar vacío mientras el usuario escribe (para que se vea el
 * placeholder "Título") — este default solo se aplica al momento de
 * guardar, nunca mientras se está editando.
 */
const val DEFAULT_PROJECT_NAME = "Project01"

/**
 * Representación serializable de una capa dentro de `project.json`.
 *
 * No usa [com.yeivikas.olyzecs.engine.scene.Layer] directamente porque
 * esa clase tiene un `Uri` (no serializable sin un serializer custom) y
 * campos `@Transient` de GPU (`glTextureId`, `pendingBitmap`) que no tiene
 * sentido persistir — son estado de render en memoria, no datos del
 * proyecto. `imageFileName` reemplaza al Uri original: es el nombre del
 * archivo dentro de `images/`, la copia local de la imagen que hace que el
 * proyecto sea autocontenido y no dependa de que el Uri de SAF original
 * siga siendo válido.
 */
@Serializable
data class LayerData(
    val id: String,
    val imageFileName: String,
    val name: String,
    val zIndex: Int,
    val parallaxFactor: Float = 1f,
    val locked: Boolean = false,
    // Candado independiente de [locked]: bloquea solo el reordenamiento
    // por arrastre en la columna de capas (ver Layer.orderLocked). Default
    // false para que los proyectos guardados ANTES de esta mejora sigan
    // cargando sin romperse — se leen como "sin bloqueo de orden".
    val orderLocked: Boolean = false,
    val visible: Boolean = true,
    val lookSettings: LookSettings = LookSettings(),
    val keyframes: List<Keyframe> = emptyList(),
    // --- Pose estática de la capa (ver CameraTrack.baseFrame), separada
    // de [keyframes] a propósito: es la posición/escala/rotación de la
    // capa cuando NO tiene ninguna animación armada. Nullable para que
    // los proyectos guardados ANTES de esta mejora (que no tienen este
    // campo en su project.json) sigan cargando sin romperse — null se
    // trata como "sin pose guardada todavía" (identidad) al leer.
    val baseFrame: CameraFrame? = null,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    // Nullable a propósito: proyectos guardados ANTES de esta mejora no
    // tienen este campo en su project.json. Al cargarlos, ProjectStorage
    // trata null como "no asignado" y le da un color según su posición en
    // la lista, en vez de que todas las capas viejas queden con el mismo
    // color por defecto.
    val colorIndex: Int? = null,
    // Color elegido a mano en la rueda de color (ver Layer.customColorArgb
    // en el motor) — null = la capa sigue usando el color automático de la
    // paleta fija (colorIndex), tal cual antes de esta mejora.
    val customColorArgb: Int? = null,
    // Color de fábrica: el dominante extraído de la imagen al importarla
    // (ver Layer.importedDefaultColorArgb) — adonde vuelve "Restablecer".
    // Nullable por compatibilidad con proyectos guardados ANTES de este
    // campo; ProjectStorage lo recalcula al cargar si viene null, así que
    // ni las capas de proyectos viejos se quedan sin su color de fábrica.
    val importedDefaultColorArgb: Int? = null,
    val customGradientStartArgb: Int? = null,
    val customGradientEndArgb: Int? = null,
    val useGradientColor: Boolean = false,
    val gradientAngleDegrees: Float? = null,
    val gradientIsRadial: Boolean = false,
    // Nullable/default false a propósito, mismo criterio que el resto de
    // los campos de color de acá arriba: proyectos guardados ANTES de
    // esta mejora no tienen este campo en su project.json, así que se
    // trata como "apagado" al cargarlos — comportamiento idéntico al que
    // ya tenían (nunca existió el modo persistido).
    val useBlackAndWhiteMode: Boolean = false
)

/**
 * Representación serializable del clip de audio de fondo del proyecto
 * (análogo a [LayerData] pero a nivel de proyecto entero — solo hay uno).
 * `audioFileName` es la copia local dentro de `audio/`, mismo patrón que
 * `imageFileName` en [LayerData].
 */
@Serializable
data class AudioTrackData(
    val audioFileName: String,
    val displayName: String,
    val sourceDurationMs: Long,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val trimStartMs: Long = 0L,
    val loop: Boolean = true,
    val fadeInMs: Long = 400L,
    val fadeOutMs: Long = 600L
)

/** Proyecto completo tal como se guarda en `project.json`. */
@Serializable
data class ProjectData(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val projectDurationMs: Long = 8000L,
    val layers: List<LayerData> = emptyList(),
    val audioTrack: AudioTrackData? = null,
    // Velocidad variable y freeze frame (opcional; listas vacías = sin
    // rampas, comportamiento idéntico a como era antes de esta función).
    val speedKeyframes: List<SpeedKeyframe> = emptyList(),
    val freezeFrames: List<FreezeFrame> = emptyList(),
    // Propiedades elegidas al crear el proyecto (ver CreateProjectDialog).
    // aspectRatio se guarda como el nombre del enum AspectRatioPreset
    // (String) en vez del tipo directamente: kotlinx.serialization SÍ
    // soporta enums nativamente, pero guardarlo como String hace que un
    // valor desconocido de una versión futura/pasada del formato no rompa
    // la carga entera del proyecto — se puede resolver con un fallback
    // seguro en vez de que decodeFromString explote.
    val aspectRatio: String = "REELS",
    val fps: Int = 30,
    // Descripción corta opcional (portada/tarjeta de "Mis proyectos"). Si
    // queda vacía, la UI ofrece un resumen auto-calculado (capas · duración
    // · formato) como sugerencia, pero nunca se persiste nada hasta que el
    // usuario confirma el diálogo de renombrar/descripción.
    val description: String = "",
    // Nombre del archivo de portada personalizada dentro de la carpeta del
    // proyecto (p. ej. "cover.jpg"), o null si no se eligió ninguna y la
    // tarjeta debe mostrar la miniatura auto-generada de siempre
    // (thumbnail.jpg, con el look ya aplicado).
    val coverImageFileName: String? = null,
    // Posición manual dentro de "Mis proyectos": convención = valor negativo
    // de un timestamp en ms, así ordenar ASCENDENTE por este campo deja lo
    // más reciente (o lo último movido) primero — igual que el orden viejo
    // por updatedAtMs descendente, pero ahora reordenable a mano con
    // "Mover arriba"/"Mover abajo". 0L (el default de kotlinx.serialization
    // para proyectos guardados ANTES de esta función) se interpreta en
    // ProjectStorage como "sin posición manual todavía" y cae de nuevo a
    // ordenar por updatedAtMs para no romper el orden de proyectos viejos.
    val orderIndex: Long = 0L,
    // --- Panel "Información del proyecto" (lado izquierdo — ver
    // ProjectInfoPanel en EditorBottomBar.kt): metadata tipo ficha de
    // película (año, categoría, duración "declarada" y hasta 4 fotos de
    // elenco/personajes). Todos nullable/con default porque son
    // completamente opcionales y los proyectos guardados ANTES de esta
    // función no los tienen en su project.json.
    val releaseYear: Int? = null,
    val genre: String? = null,
    // Duración "de ficha" en minutos (p. ej. 91 = "1h 31m") — es metadata
    // informativa que el usuario tipea a mano, NO la duración real del
    // timeline (esa es [projectDurationMs], que sigue siendo la única
    // fuente de verdad para el motor de edición/exportación).
    val infoDurationMinutes: Int? = null,
    // Nombres de archivo dentro de `cast/` (uno por casilla, en orden;
    // null = casilla vacía). Longitud fija de 4 elementos.
    val castPhotoFileNames: List<String?> = listOf(null, null, null, null),
    // --- Guías de composición (cuadrícula) ---
    // BUG REAL corregido: antes este ajuste vivía SOLO en memoria de
    // Compose (EditorScreen.kt), nunca en project.json — por eso se
    // "olvidaba" al salir del proyecto y volver a entrar. Todos con
    // default porque los proyectos guardados ANTES de esta función no
    // los tienen. `gridShapeName` guarda el `name` del enum GridShape
    // (privado de EditorScreen.kt) como String, por la misma razón que
    // `aspectRatio`: un valor futuro/desconocido no rompe la carga.
    val gridEnabled: Boolean = false,
    val gridShapeName: String = "RECTANGLE",
    val gridColumns: Int = 3,
    val gridRows: Int = 3,
    val gridLineColorEnabled: Boolean = false,
    val gridLineHue: Float = 70f,
    // Grosor de las líneas de guía en dp (ver comentario en
    // EditorUiState.gridLineThicknessDp). 1f = comportamiento de toda la
    // vida, para no romper proyectos guardados ANTES de este control.
    val gridLineThicknessDp: Float = 1f,
    // Opacidad de las líneas de guía (ver comentario en
    // EditorUiState.gridLineOpacity). 0.4f = el valor fijo de toda la
    // vida, para que un proyecto guardado ANTES de este control se siga
    // viendo exactamente igual al reabrirlo.
    val gridLineOpacity: Float = 0.4f,
    // Snap magnético al arrastrar (ver comentario en
    // EditorUiState.gridSnapEnabled). false por default: los proyectos
    // guardados ANTES de esta opción siguen moviéndose libre al
    // reabrirse, tal como se comportaban hasta ahora — el usuario lo
    // prende cuando quiere.
    val gridSnapEnabled: Boolean = false,
    // --- Orden de las manijas del marco de edición (ver [HandlePosition]/
    // [LayerHandleRole], privados de EditorScreen.kt) ---
    // BUG REAL corregido: este reordenamiento (arrastrar una manija sobre
    // otra) vivía SOLO en memoria de Compose — se perdía al salir del
    // proyecto y volver a entrar, tanto el override "Solo" de una capa
    // puntual como el default "Todos" para el resto. Se guarda acá como
    // `Map<String, String>` (nombre de posición → nombre de función), no
    // con los enums directos, por la misma razón que `gridShapeName`: un
    // valor futuro/desconocido de una versión distinta de la app no rompe
    // la carga entera del proyecto, y esta capa de datos no queda acoplada
    // a un tipo privado de la capa de UI.
    // `handleOrderGlobal`: el orden "Todos" — vacío = nunca se guardó
    // ninguno, se usa el orden de fábrica (DEFAULT_HANDLE_ORDER).
    val handleOrderGlobal: Map<String, String> = emptyMap(),
    // `handleOrderPerLayer`: overrides "Solo [esta capa]", con prioridad
    // sobre `handleOrderGlobal` — clave = id de la capa.
    val handleOrderPerLayer: Map<String, Map<String, String>> = emptyMap()
)

