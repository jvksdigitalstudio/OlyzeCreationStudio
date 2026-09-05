package com.yeivikas.olyzecs.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeivikas.olyzecs.api.animation.AnimationApi
import com.yeivikas.olyzecs.api.animation.AnimationApiImpl
import com.yeivikas.olyzecs.api.distortion.DistortionApi
import com.yeivikas.olyzecs.api.distortion.DistortionApiImpl
import com.yeivikas.olyzecs.api.mesh3d.Mesh3DApi
import com.yeivikas.olyzecs.api.mesh3d.Mesh3DApiImpl
import com.yeivikas.olyzecs.api.project.ActiveProjectMutator
import com.yeivikas.olyzecs.api.project.ActiveProjectReader
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.data.ColorExtraction
import com.yeivikas.olyzecs.data.DEFAULT_PROJECT_NAME
import com.yeivikas.olyzecs.data.LayerRepository
import com.yeivikas.olyzecs.data.ProjectStorage
import com.yeivikas.olyzecs.data.toSaveSnapshot
import com.yeivikas.olyzecs.engine.animation.EasingType
import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.FreezeRuntimeState
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.AudioPreviewPlayer
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.CameraTrack
import com.yeivikas.olyzecs.engine.camera.Keyframe
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.render.RenderLayerSnapshot
import com.yeivikas.olyzecs.engine.render.RenderSnapshot
import com.yeivikas.olyzecs.api.export.ExportApiImpl
import com.yeivikas.olyzecs.engine.export.ExportProgress
import com.yeivikas.olyzecs.engine.export.ExportQuality
import com.yeivikas.olyzecs.engine.export.ExportSettings
import com.yeivikas.olyzecs.engine.export.computeExportDimensions
import com.yeivikas.olyzecs.engine.distortion.DistortionField
import com.yeivikas.olyzecs.engine.mesh3d.Extrude3D
import com.yeivikas.olyzecs.engine.scene.AspectRatioPreset
import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.engine.timeline.TimelineDurationManager
import com.yeivikas.olyzecs.engine.timeline.TimelineEvent
import com.yeivikas.olyzecs.engine.timeline.TimelineLimits
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/**
 * La duración del proyecto ya NO la elige el usuario: la administra por
 * completo [TimelineDurationManager] siguiendo la política de
 * [com.yeivikas.olyzecs.engine.timeline.TimelineExpansionPolicy] —
 * arranca en [TimelineLimits.INITIAL_DURATION_MS] (1 minuto, oculto) y
 * crece sola a medida que el playhead se acerca al final, hasta
 * [TimelineLimits.MAX_DURATION_MS] (180 minutos). Este ViewModel solo
 * consulta ese módulo; toda la lógica de "cuánto" y "cuándo" vive ahí.
 */

/**
 * FPS "de catálogo" — valores estándar de la industria que SIEMPRE se
 * ofrecen sin importar el dispositivo. La pantalla de creación de
 * proyecto usa en cambio `DisplayRefreshRate.availableProjectFps(context)`,
 * que agrega dinámicamente los fps altos que la pantalla real del equipo
 * soporte (ver ese archivo para el porqué). Se mantiene este nombre/lista
 * acá por compatibilidad con el resto del código y con proyectos ya
 * guardados que la referencian directamente.
 * con el código y proyectos ya guardados que la referencian directamente;
 * para la UI de creación de proyecto se usa la versión consciente del
 * dispositivo.
 */
val AVAILABLE_PROJECT_FPS = listOf(24, 30, 60, 90, 120)
const val DEFAULT_PROJECT_FPS = 30

// --- Antes esto era un tick FIJO de 16ms (~60fps) sin importar el fps
// configurado en el proyecto: si el usuario elegía 120fps, el playhead
// igual solo avanzaba ~60 veces por segundo, así que ninguna animación
// podía verse más fluida que 60fps por más "120" que dijera el selector
// — de ahí la sensación de "no es tan fluido, apesar que le meto 120".
// Ahora el intervalo objetivo se calcula a partir de `projectFps` real
// del proyecto abierto (ver `startPlaybackLoop`), y como paso extra
// contra el jitter normal de las corrutinas (delay() no es un temporizador
// de tiempo real exacto), el avance del playhead usa el tiempo REAL
// transcurrido entre iteraciones (System.nanoTime) en vez de sumar
// siempre el mismo intervalo nominal — así no se acumula desfase aunque
// algún tick tarde un poco más o menos de lo pedido.
private fun targetTickMsFor(fps: Int): Long =
    (1000.0 / fps.coerceIn(1, 240)).roundToLong().coerceAtLeast(4L)

// Si el tick real tarda anormalmente más que lo esperado (la app pasó a
// segundo plano, el hilo se bloqueó un instante, el debugger pausó todo,
// etc.) no hay que "saltar" el playhead ese tramo entero de una — se limita
// a un máximo razonable para que reanudar reproducción nunca se sienta como
// un salto brusco hacia adelante.
private const val MAX_TICK_MS = 200L

// Tiempo de "silencio" tras el último cambio antes de escribir a disco.
private const val AUTOSAVE_DEBOUNCE_MS = 900L

// Ventana de fusión para el historial de undo: ajustes continuos (arrastrar
// un slider, mover la imagen con el dedo) que ocurren dentro de esta
// ventana desde el último checkpoint se consideran "el mismo gesto" y NO
// generan un paso nuevo de undo — así diez frames de un mismo arrastre son
// un solo Ctrl+Z, no diez. Las acciones discretas (bloquear, reordenar,
// quitar keyframe) siempre fuerzan su propio checkpoint sin importar esto.
private const val UNDO_MERGE_WINDOW_MS = 600L
private const val MAX_UNDO_STEPS = 50

/** Estado del autoguardado, para mostrar un indicador discreto en la barra superior. */
sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data class Saved(val atMs: Long) : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * A PEDIDO DEL USUARIO — foto exacta de todo el CONTENIDO persistible del
 * proyecto (los mismos campos que recibe `ProjectStorage.saveProject`, ni
 * uno más) tomada apenas termina de cargar (o de crearse, si es nuevo).
 * Sirve para dos cosas nuevas que pidió el usuario al volver atrás desde
 * el editor:
 *  1. Saber si de verdad hubo cambios en esta sesión (comparando esto
 *     contra el estado actual) para no molestar con un diálogo de "¿guardar?"
 *     cuando no se tocó nada.
 *  2. Poder "Salir sin guardar": volver el estado a ESTO y recién ahí
 *     persistirlo, pisando cualquier autoguardado intermedio que ya
 *     hubiera escrito a disco durante la sesión.
 *
 * Nunca incluye estado transitorio de UI (selectedLayerId, playhead,
 * isPlaying, saveState, undo/redo, etc.) a propósito: esas cosas no son
 * "cambios" que el usuario reconocería como tales al pensar en "guardar o
 * no guardar" — solo lo que de verdad se escribe en project.json.
 */
/**
 * FASE 1 (AUDITORÍA P0) — foto INDEPENDIENTE del contenido persistible de
 * una capa, sin ningún alias hacia el objeto [Layer] vivo del editor.
 *
 * BUG REAL corregido acá: la versión anterior de [ProjectContentSnapshot]
 * guardaba `layers: List<Layer>` a secas. Como [Layer] es una clase con
 * campos `var` (y su `cameraTrack` es, a su vez, un contenedor mutable de
 * keyframes), esa lista NO era una foto — era la MISMA lista y los MISMOS
 * objetos que seguía usando el editor en vivo. `layers.toList()` copia el
 * contenedor `List`, pero cada elemento adentro sigue siendo el mismo
 * objeto `Layer` mutable: mover un slider, tocar un candado o cambiar un
 * color mutaba esos campos en el sitio, y como el "snapshot" apuntaba a
 * esos mismos objetos, el snapshot cambiaba SOLO cuando cambiaba el
 * editor — dos referencias al mismo estado, nunca dos estados distintos.
 * Consecuencias reales observadas en el código:
 *  - `hasUnsavedChanges()` comparaba (por `equals()` estructural de data
 *    class) un objeto contra SÍ MISMO en la práctica, así que cualquier
 *    cambio de capa (color, candado, transform, keyframes) era invisible
 *    para la detección de cambios.
 *  - `discardChangesAndExit()` asignaba `layers = snapshot.layers`, pero
 *    esa lista ya contenía las capas YA mutadas — "descartar cambios" no
 *    revertía nada a nivel de capa, solo los campos de proyecto que sí
 *    vivían en `EditorUiState` (nombre, duración, grid, etc.).
 *
 * [LayerContentState] es un DTO inmutable: todos sus campos son `val` de
 * tipos inmutables (String/Int/Float/Boolean/Uri, más [LookSettings] y
 * [CameraFrame], que ya son `data class` inmutables, y una `List<Keyframe>`
 * — [Keyframe] también es inmutable, así que una copia superficial de la
 * lista alcanza para independizarla de la lista mutable interna de
 * [com.yeivikas.olyzecs.engine.camera.CameraTrack]). Una vez construido,
 * ningún cambio posterior sobre el [Layer] vivo puede tocar esta instancia.
 *
 * Deliberadamente EXCLUYE `glTextureId`/`pendingBitmap` (recursos GL/CPU
 * de runtime — ver comentario de exclusión análogo en
 * [com.yeivikas.olyzecs.api.model.LayerSnapshot]).
 */
private data class LayerContentState(
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

/** Construye la foto inmutable e independiente de esta capa — ver [LayerContentState]. */
private fun Layer.toContentState() = LayerContentState(
    id = id,
    sourceUri = sourceUri,
    name = name,
    zIndex = zIndex,
    parallaxFactor = parallaxFactor,
    locked = locked,
    orderLocked = orderLocked,
    visible = visible,
    lookSettings = lookSettings,
    // Copia superficial de la LISTA alcanza para independizarla del
    // contenedor mutable `_keyframes` de CameraTrack, porque cada
    // [Keyframe] adentro ya es, en sí mismo, inmutable (data class de
    // solo `val`) — no hay estado mutable anidado que copiar más profundo.
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

/**
 * Aplica este [LayerContentState] SOBRE un [Layer] vivo, mutándolo en el
 * lugar (nunca reemplazando el objeto) para conservar `glTextureId` salvo
 * que la imagen realmente haya cambiado — mismo criterio ya usado por
 * `restoreSnapshot`/undo-redo, reutilizado acá para `discardChangesAndExit`.
 */
private fun LayerContentState.applyTo(layer: Layer, cancelPendingCommits: (String) -> Unit) {
    layer.name = name
    layer.zIndex = zIndex
    layer.parallaxFactor = parallaxFactor
    layer.locked = locked
    layer.orderLocked = orderLocked
    layer.visible = visible
    layer.lookSettings = lookSettings
    layer.cameraTrack.replaceAll(keyframes)
    layer.cameraTrack.updateBaseFrame(baseFrame)
    layer.widthPx = widthPx
    layer.heightPx = heightPx
    layer.colorIndex = colorIndex
    layer.customColorArgb = customColorArgb
    layer.importedDefaultColorArgb = importedDefaultColorArgb
    layer.customGradientStartArgb = customGradientStartArgb
    layer.customGradientEndArgb = customGradientEndArgb
    layer.useGradientColor = useGradientColor
    layer.gradientAngleDegrees = gradientAngleDegrees
    layer.gradientIsRadial = gradientIsRadial
    layer.useBlackAndWhiteMode = useBlackAndWhiteMode
    if (layer.sourceUri != sourceUri) {
        cancelPendingCommits(id)
        layer.sourceUri = sourceUri
        layer.pendingBitmap = null
        layer.glTextureId = -1
    }
}

/**
 * Reconstruye desde cero un [Layer] a partir de este [LayerContentState] —
 * se usa cuando la capa original ya no existe en el editor en vivo (fue
 * eliminada durante la sesión) y `discardChangesAndExit` necesita
 * restaurarla. `sourceUri` sigue apuntando a la copia local ya guardada en
 * disco (ver `ProjectStorage.ensureLocalImage`), así que sigue siendo
 * válida aunque la capa haya sido borrada del estado en memoria.
 * `glTextureId = -1` / `pendingBitmap = null` a propósito: el motor GL
 * (ver `GLRenderer`) decodifica de nuevo desde `sourceUri` la primera vez
 * que encuentra una capa sin textura ni bitmap pendiente — no hace falta
 * (ni conviene) decodificar acá, fuera del hilo/ciclo de render.
 */
private fun LayerContentState.toFreshLayer(): Layer = Layer(
    id = id,
    sourceUri = sourceUri,
    name = name,
    zIndex = zIndex,
    parallaxFactor = parallaxFactor,
    locked = locked,
    orderLocked = orderLocked,
    visible = visible,
    lookSettings = lookSettings,
    cameraTrack = CameraTrack(
        initialKeyframes = keyframes,
        initialBaseFrame = baseFrame
    ),
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

/**
 * Igual criterio que [LayerContentState] pero para el clip de audio de
 * fondo: [AudioClip] también es una clase de campos `var` (mutable), así
 * que guardarlo tal cual en el snapshot tenía el mismo problema de
 * aliasing — cambiar el volumen o el trim del audio mutaba en el lugar el
 * mismo objeto referenciado por `initialContentSnapshot`.
 */
private data class AudioContentState(
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

private fun AudioClip.toContentState() = AudioContentState(
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

private fun AudioContentState.toAudioClip() = AudioClip(
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

private data class ProjectContentSnapshot(
    val projectName: String,
    val projectDurationMs: Long,
    val layers: List<LayerContentState>,
    val audioClip: AudioContentState?,
    val speedKeyframes: List<SpeedKeyframe>,
    val freezeFrames: List<FreezeFrame>,
    val exportAspect: AspectRatioPreset,
    val projectFps: Int,
    val releaseYear: Int?,
    val genre: String?,
    val infoDurationMinutes: Int?,
    val gridEnabled: Boolean,
    val gridShapeName: String,
    val gridColumns: Int,
    val gridRows: Int,
    val gridLineColorEnabled: Boolean,
    val gridLineHue: Float,
    val gridLineThicknessDp: Float,
    val gridLineOpacity: Float,
    val gridSnapEnabled: Boolean,
    val handleOrderGlobal: Map<String, String>,
    val handleOrderPerLayer: Map<String, Map<String, String>>
)

private fun EditorUiState.toContentSnapshot() = ProjectContentSnapshot(
    projectName = projectName,
    projectDurationMs = projectDurationMs,
    // Foto INDEPENDIENTE de cada capa — ver LayerContentState. Antes acá
    // se guardaba `layers` (la lista de objetos Layer mutables en vivo)
    // directo, sin copiar nada real.
    layers = layers.map { it.toContentState() },
    audioClip = audioClip?.toContentState(),
    speedKeyframes = speedKeyframes,
    freezeFrames = freezeFrames,
    exportAspect = exportAspect,
    projectFps = projectFps,
    releaseYear = releaseYear,
    genre = genre,
    infoDurationMinutes = infoDurationMinutes,
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

/**
 * FASE 2 — Concurrencia, Estado de Render, Reproducción y Duración.
 *
 * Construye el [RenderSnapshot] que consume [GLRenderer] (ver
 * RenderSnapshot.kt para el diseño completo): captura, por cada `Layer`
 * vivo, únicamente los campos LÓGICOS relevantes para dibujar
 * (zIndex/visible/parallax/look/keyframes/baseFrame) — nunca recursos
 * GPU (`glTextureId`/`pendingBitmap`/`widthPx`/`heightPx`), que el
 * renderer sigue leyendo de los `Layer` en vivo por su cuenta (son
 * responsabilidad del propio hilo de GL, ver comentario en GLRenderer).
 *
 * Es intencionalmente seguro de llamar desde CUALQUIER hilo (incluido el
 * de GL, que es quien realmente lo invoca en la práctica, una vez por
 * frame): `layer.cameraTrack.keyframes`/`.baseFrame` ya son accesos
 * publicados de forma segura (`@Volatile`, ver CameraTrack.kt), y
 * `zIndex`/`visible`/`parallaxFactor`/`lookSettings` son `@Volatile` en
 * Layer.kt — ningún campo que toca esta función puede leerse a medio
 * escribir sin importar qué esté haciendo el hilo principal con el mismo
 * `Layer` al mismo tiempo. No hace falta, por lo tanto, forzar que esta
 * conversión corra específicamente en el hilo principal.
 */
private fun EditorUiState.toRenderSnapshot(): RenderSnapshot = RenderSnapshot(
    layers = layers.map { layer ->
        RenderLayerSnapshot(
            id = layer.id,
            zIndex = layer.zIndex,
            visible = layer.visible,
            parallaxFactor = layer.parallaxFactor,
            lookSettings = layer.lookSettings,
            keyframes = layer.cameraTrack.keyframes,
            baseFrame = layer.cameraTrack.baseFrame
        )
    }
)

data class EditorUiState(
    val layers: List<Layer> = emptyList(),
    val selectedLayerId: String? = null,
    val playheadMs: Long = 0L,
    val projectDurationMs: Long = TimelineLimits.INITIAL_DURATION_MS,
    // true cuando la línea de tiempo llegó a su techo de 180 minutos y ya
    // no puede seguir expandiéndose automáticamente — ver TimelineDurationManager.
    val isAtMaxDuration: Boolean = false,
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val isCapturing: Boolean = false,
    val isImporting: Boolean = false,
    val exportProgress: ExportProgress? = null,
    // Cada mutación in-place de una capa incrementa esto, para forzar
    // recomposición aunque los objetos Layer sigan siendo los mismos.
    val revision: Int = 0,
    // --- Persistencia ---
    val projectName: String = DEFAULT_PROJECT_NAME,
    val isLoadingProject: Boolean = true,
    val saveState: SaveState = SaveState.Idle,
    // --- Undo/Redo ---
    val undoAvailable: Boolean = false,
    val redoAvailable: Boolean = false,
    // Se incrementa SOLO en undo()/redo() (nunca en una edición normal).
    // EditorScreen lo suma a la key de `remember` de los sliders de cámara
    // para forzar que vuelvan a leer el valor real del keyframe restaurado
    // en vez de quedarse con el valor que tenían en el dedo justo antes
    // de deshacer.
    val undoRedoTick: Int = 0,
    // --- Audio de fondo (Fase 6) ---
    // El audio queda fuera del sistema de undo/redo a propósito: es una
    // sola pista a nivel de proyecto (no por capa) y sus ediciones
    // (volumen, trim, fade) son ajustes de "mezcla" más que de puesta en
    // escena — mezclarlo en el mismo historial que mueve keyframes de
    // cámara complicaría los snapshots sin un beneficio claro para el
    // usuario.
    val audioClip: AudioClip? = null,
    val isImportingAudio: Boolean = false,
    // --- Exportación configurable (Fase 6) ---
    // Default en la máxima calidad disponible (4K/UHD): coherente con que
    // la app ya decodifica e importa siempre a resolución original (ver
    // ImageDecoding.NO_LIMIT) — no tiene sentido preservar el original
    // completo en la importación y después exportar por defecto en una
    // calidad menor. El usuario sigue pudiendo bajarla manualmente desde
    // ExportQualityPanel si quiere un archivo más liviano.
    val exportQuality: ExportQuality = ExportQuality.UHD_4K,
    val exportAspect: AspectRatioPreset = AspectRatioPreset.REELS,
    // Cuadros por segundo del video EXPORTADO — elegido al crear el
    // proyecto (ver CreateProjectDialog), no en el momento de exportar.
    // El preview en vivo del editor sigue corriendo a su propio tick
    // interno fijo (~60fps) sin importar este valor: FPS acá es
    // exclusivamente un parámetro del encoder de video final.
    val projectFps: Int = DEFAULT_PROJECT_FPS,
    // --- Velocidad variable y freeze frame (Fase 7) ---
    // Igual que el audio: queda fuera del undo/redo a propósito. Es una
    // sola línea de tiempo a nivel de proyecto (no por capa), y las
    // rampas de velocidad son más un ajuste de "ritmo de montaje" que de
    // puesta en escena — mezclarlo en el historial de undo por capa
    // complicaría los snapshots sin un beneficio claro.
    val speedKeyframes: List<SpeedKeyframe> = emptyList(),
    val freezeFrames: List<FreezeFrame> = emptyList(),
    // --- Panel "Información del proyecto" (lado izquierdo — ver
    // ProjectInfoPanel en EditorBottomBar.kt) ---
    val releaseYear: Int? = null,
    val genre: String? = null,
    // Duración "de ficha" en minutos (metadata informativa, no la duración
    // real del timeline — ver comentario en ProjectData.infoDurationMinutes).
    val infoDurationMinutes: Int? = null,
    // Siempre 4 elementos (uno por casilla); null = casilla vacía.
    val castPhotoFiles: List<File?> = listOf(null, null, null, null),
    // --- Guías de composición (cuadrícula) ---
    // BUG REAL corregido: antes vivían como `remember { mutableStateOf() }`
    // sueltos DENTRO de EditorScreen.kt — estado puramente de composición
    // Compose, nunca pasaba por el ViewModel ni se guardaba en
    // project.json. Por eso, al salir del proyecto (la composable se
    // descarta) y volver a entrar (se crea una instancia nueva), todo
    // volvía siempre a los defaults — la cuadrícula activada, su forma,
    // columnas/filas y color de línea se "olvidaban" aunque el proyecto
    // ya se hubiera guardado. Ahora viven acá, igual que cualquier otro
    // dato persistente del proyecto (aspecto, fps, etc.), y se guardan/
    // restauran con el resto en ProjectStorage. `gridShapeName` guarda el
    // `name` del enum GridShape (privado de EditorScreen.kt) como String,
    // para no acoplar el ViewModel/ProjectStorage a un tipo de la capa de
    // UI.
    val gridEnabled: Boolean = false,
    val gridShapeName: String = "RECTANGLE",
    val gridColumns: Int = 3,
    val gridRows: Int = 3,
    val gridLineColorEnabled: Boolean = false,
    // 70f = gridBarFractionToHue(0.5f) (ver EditorScreen.kt) — el mismo
    // matiz medio de la franja con el que arrancaba antes en el
    // `remember` local, para que el default no cambie de un vistazo.
    val gridLineHue: Float = 70f,
    // Grosor de las líneas de guía en dp — independiente de la forma y
    // del color, se aplica igual a CUALQUIER figura activa. 1f = el
    // grosor fijo de toda la vida (antes hardcodeado como `1.dp.toPx()`
    // en drawGridGuides, sin ningún control para cambiarlo).
    val gridLineThicknessDp: Float = 1f,
    // Opacidad real de las líneas de guía (0.05–1.0) — ANTES esto era un
    // 0.4f fijo a fuego en gridLineDrawColor, sin ningún control. Con un
    // fondo bien saturado (el verde chroma-key por defecto) y un matiz
    // casi-complementario elegido (magenta, violeta), 0.4 de por sí
    // mezcla demasiado con el fondo y el color elegido se ve apagado/
    // grisáceo — matemáticamente correcto (es mezcla alpha, no un bug),
    // pero nada vívido. Este slider deja subir la opacidad para esos
    // casos, sin tocar el matiz. 0.4f de default = EXACTAMENTE el mismo
    // valor fijo de antes, para que ningún proyecto ya guardado cambie
    // de aspecto solo por actualizar la app.
    val gridLineOpacity: Float = 0.4f,
    // Snap magnético al arrastrar una capa (ver snapTranslateToGrid en
    // EditorScreen.kt) — INDEPENDIENTE de `gridEnabled`: la cuadrícula
    // puede estar visible sin imantar nada (por ej. para usarla solo
    // como referencia visual mientras se mueve libre), y viceversa no
    // tiene sentido (sin cuadrícula visible no hay contra qué imantar,
    // así que el snap efectivo igual queda condicionado a
    // `gridEnabled && gridSnapEnabled` en el gesto de arrastre). Default
    // false, igual que `gridEnabled`: el usuario lo prende cuando lo
    // quiere, no viene forzado.
    val gridSnapEnabled: Boolean = false,
    // Ver comentario en ProjectData sobre el bug real que esto corrige:
    // antes vivía solo en memoria de Compose (EditorScreen.kt), se perdía
    // al salir del proyecto y volver a entrar.
    val handleOrderGlobal: Map<String, String> = emptyMap(),
    val handleOrderPerLayer: Map<String, Map<String, String>> = emptyMap()
)

/** Snapshot liviano de todo lo que el undo/redo puede deshacer: transform, look y orden de capas. */
private data class LayerEditState(
    val id: String,
    val zIndex: Int,
    val parallaxFactor: Float,
    val locked: Boolean,
    // --- BUG REAL corregido: faltaba acá. El candado de orden (Layer.
    // orderLocked, ver TimelineView.kt) se guarda y se restaura en el
    // proyecto perfectamente, pero como este campo no viajaba en el
    // snapshot de undo/redo, alternar ese candado quedaba completamente
    // invisible para Deshacer — tocarlo y después tocar "Deshacer" no lo
    // revertía, a diferencia de CUALQUIER otro cambio de capa (color,
    // candado de canvas, orden, visibilidad, etc.), que sí se deshacen
    // todos. Con esto, el candado de orden se comporta exactamente igual
    // que el resto.
    val orderLocked: Boolean,
    val visible: Boolean,
    val lookSettings: LookSettings,
    val keyframes: List<Keyframe>,
    val baseFrame: CameraFrame,
    // A PEDIDO DEL USUARIO: antes el snapshot de undo/redo no guardaba
    // `sourceUri`, así que una sesión de edición de imagen (Recolor/3D/
    // Efectos/Distorsión, ver `commitLayerRecolor`) confirmada con el ✓
    // quedaba completamente invisible para el Deshacer/Rehacer "de
    // proyecto" de la barra superior — una vez que salías del panel, no
    // había forma de deshacer el efecto entero desde ahí (solo re-
    // entrando al panel y cancelando, que es un flujo distinto: descarta
    // TODO, no "un paso atrás"). Con este campo, cada checkpoint sabe
    // qué imagen tenía la capa en ese momento — ver
    // `EditorViewModel.beginLayerEditSession` (el checkpoint que se
    // guarda ANTES de entrar al panel) y `restoreSnapshot`, más abajo
    // (que revierte también la imagen, no solo transform/keyframes).
    val sourceUri: Uri,
    // --- FASE 1 (AUDITORÍA P0) — BUG REAL corregido: faltaban acá todas
    // las propiedades VISUALES de color/degradado/blanco-y-negro de la
    // capa (ver Layer.kt: colorIndex, customColorArgb,
    // importedDefaultColorArgb, customGradientStartArgb/EndArgb,
    // useGradientColor, gradientAngleDegrees, gradientIsRadial,
    // useBlackAndWhiteMode). Como CUALQUIERA de estas se puede cambiar
    // desde el editor (rueda de color, degradado, modo B&N — ver
    // LayerDialogs.kt) y ninguna viajaba en este snapshot, tocar
    // "Deshacer" después de cambiar un color no revertía nada — mismo
    // patrón de bug ya corregido acá para `orderLocked`, extendido ahora
    // a TODO el estado visual editable, no solo transform/candados.
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

private data class EditSnapshot(
    val layers: List<LayerEditState>,
    val projectDurationMs: Long,
    val selectedLayerId: String?,
    val playheadMs: Long
)

/**
 * Orquesta el editor: es el único lugar de la capa de PRESENTACIÓN (UI +
 * ViewModels) que le habla directo a `engine.*` para construir, animar y
 * exportar el proyecto (además de `data.*` para persistirlo). La UI
 * (`ui/EditorScreen.kt` y compañía) no llama a `engine.*` por su cuenta
 * — ver Etapa 3 de la refactorización original, y FASE B, que cerró el
 * último caso real que quedaba (`EditorScreen` llamaba a
 * `engine.mesh3d.Extrude3D.render` directo; ahora pasa por
 * [renderExtrude3D] acá abajo).
 *
 * Excepción real y documentada a propósito, para que esta afirmación siga
 * siendo verdadera y no otra promesa de KDoc que el código contradiga:
 * `data.ProjectStorage` también habla directo con `engine.audio.AudioProcessor`
 * y `engine.timeline.ThumbnailRenderer` — fuera de esta clase. Es
 * intencional: decodificar audio y generar miniaturas es trabajo de
 * PERSISTENCIA (ocurre al guardar/cargar un proyecto), no de edición en
 * pantalla, y `ProjectStorage` ya vive fuera de la cadena `UI → ViewModel`
 * (ver `MainActivity` como composition root). La regla real, más precisa
 * que "único en todo el proyecto", es: *ninguna pantalla ni Composable
 * llama a `engine.*` directo — solo `EditorViewModel` (para edición en
 * vivo) y `ProjectStorage` (para persistencia) lo hacen, cada uno desde su
 * propia capa*.
 *
 * *Frontera de `EliNer API`:* hoy `Mesh3D` (ver [renderExtrude3D] más
 * abajo), `Animation` (ver [currentOutputDurationMs]/[speedAtPlayhead]/
 * `startPlaybackLoop`, más abajo) y `Export` (ver [exportVideo]) YA
 * pasan por `EliNer API` (`Mesh3DApiImpl`/`AnimationApiImpl`/
 * `ExportApiImpl` respectivamente) en vez de llamar `engine.*` directo
 * — son los primeros 3 dominios migrados de verdad. `Timeline` tuvo una
 * limpieza parcial (ver [seekTo]/`retimeKeyframe`, que ahora reusan
 * `ensureTimelineCapacityFor`, el mismo override de `ActiveProjectMutator`
 * que ya existía sin consumidor) pero **`TimelineApiImpl` en sí sigue sin
 * ningún consumidor externo real** — lo que se limpió fue una
 * duplicación interna de código, no una migración a la API en el mismo
 * sentido que los otros 3 (el loop de reproducción, hot-path a ~60Hz,
 * deliberadamente NO se tocó — ver comentario en su call site: escribir
 * `_uiState` en cada tick sin condición sería una regresión de
 * rendimiento real). El resto (Layer, Camera, Audio) sigue llamando
 * `engine.*` directo desde acá — **no por descuido**: se auditó
 * explícitamente y se encontró que migrarlos con el mismo patrón
 * mecánico crearía una llamada circular real (`ActiveProjectMutator` ya
 * delega hacia las funciones "viejas" de estos 3 dominios; hacer que las
 * funciones viejas llamen de vuelta a la API haría que se llamen entre
 * sí infinitamente) y/o una incompatibilidad de sincronía (`CameraApi`
 * es `suspend`, varias funciones viejas de cámara no lo son). Migrarlos
 * requiere una decisión de diseño previa, no solo trabajo mecánico — ver
 * informe de la tarea correspondiente. `ProjectStorage` queda fuera de
 * ese contrato a propósito (gestión de archivos de proyecto, no motor).
 */
class EditorViewModel(
    private val layerRepository: LayerRepository,
    private val projectStorage: ProjectStorage,
    private val projectId: String,
    private val initialName: String = DEFAULT_PROJECT_NAME,
    private val initialAspect: AspectRatioPreset = AspectRatioPreset.REELS,
    // Ya no se usa para fijar la duración inicial (ver TimelineDurationManager.startNewProject,
    // que SIEMPRE arranca en TimelineLimits.INITIAL_DURATION_MS): se mantiene el parámetro
    // únicamente por compatibilidad de firma con EditorViewModelFactory y quien lo instancie.
    @Suppress("UNUSED_PARAMETER") initialDurationMs: Long = TimelineLimits.INITIAL_DURATION_MS,
    private val initialFps: Int = DEFAULT_PROJECT_FPS,
    // Mesh3DApi ya está conectada de verdad (Fase Mesh3D→EliNer): renderExtrude3D
    // (más abajo) delega acá en vez de llamar Extrude3D.render directo. Con
    // default para no romper compatibilidad de firma con quien construya este
    // ViewModel sin conocer EliNer API — mismo criterio que el resto de los
    // parámetros de este constructor.
    private val mesh3DApi: Mesh3DApi = Mesh3DApiImpl(),
    // Mismo patrón y mismo motivo que mesh3DApi (ver arriba), ahora para
    // Animation (Fase Animation→EliNer): step/computeOutputDurationMs/
    // speedAt (más abajo) delegan acá en vez de llamar SpeedRampEngine
    // directo.
    private val animationApi: AnimationApi = AnimationApiImpl(),
    // Mismo patrón otra vez, ahora para Distorsión (Distortion→EliNer,
    // dominio nuevo desde el día 1 de EliNer, sin pasar primero por
    // llamar al motor directo desde la UI): renderDistortion (más abajo)
    // delega acá en vez de llamar DistortionRasterizer.render directo.
    private val distortionApi: DistortionApi = DistortionApiImpl()
) : ViewModel(), ActiveProjectReader, ActiveProjectMutator {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /**
     * FASE 2 — punto de entrada que usa [GLPreview]/[GLRenderer] para
     * obtener el estado lógico de render de este instante. Ver
     * [toRenderSnapshot] para el detalle de por qué es seguro llamarlo
     * desde el hilo de GL directamente, sin pasar por el hilo principal.
     */
    fun currentRenderSnapshot(): RenderSnapshot = _uiState.value.toRenderSnapshot()

    // BUG REAL ENCONTRADO (reportado por el usuario): cancelar el modo
    // edición (× → "Sí, salir") revertía `sourceUri` a la copia original
    // pero el efecto (Sombra, en el caso reportado) igual quedaba
    // aplicado. Causa: `commitLayerRecolor` (llamado por EffectsPanel/
    // LayerColorEditPanel/Extrude3DPanel/DistortionPanel con debounce de
    // 500ms, ver `applyLivePreviewAndScheduleCommit` en EditorScreen.kt)
    // corre en `viewModelScope`, que NO tiene relación con el ciclo de
    // vida del panel de Compose — al salir del modo edición el panel se
    // descompone y cancela SU PROPIO `coroutineScope` (el que programó el
    // `delay(500)`), pero si esos 500ms ya habían pasado y el commit
    // real ya estaba en vuelo (escribiendo el bitmap a disco en
    // `Dispatchers.IO`, que puede tardar más que el tiempo que tarda el
    // usuario en tocar × y confirmar "Sí, salir"), nada lo cancelaba: el
    // commit terminaba DESPUÉS de `revertLayerEditSession` y pisaba el
    // `sourceUri` recién revertido con el archivo del efecto que se
    // suponía cancelado. Constancia clásica de dos escrituras async
    // compitiendo por el mismo campo sin ningún control de versión/
    // cancelación entre ellas.
    //
    // Arreglo: cada capa tiene a lo sumo UN commit "en vuelo" registrado
    // acá. Si se agenda uno nuevo para la misma capa (nuevo ajuste del
    // usuario) o si se cancela la sesión de edición, el anterior se
    // cancela explícitamente antes de seguir — así nunca hay dos
    // escrituras del mismo commit corriendo a la vez, ni una escritura
    // "zombie" de una sesión ya cancelada que aparece tarde a pisar el
    // revert.
    //
    // A PEDIDO DEL USUARIO — MULTI-VENTANA (arreglo agregado junto con el
    // resto del soporte de varias ventanas de edición abiertas a la vez
    // en EditorScreen.kt): la clave de este mapa ya NO es solo
    // `layerId` — es `"$layerId::$source"`, donde `source` identifica
    // QUÉ ventana pidió el commit ("recolor", "colorBasico", "3d",
    // "effects", "distortion" — ver el parámetro `source` de
    // `commitLayerRecolor`, más abajo). Con la clave vieja (solo
    // `layerId`), si el usuario tenía dos ventanas abiertas editando la
    // MISMA capa (p. ej. "Recolor" y "Color Básico" juntas, que ahora sí
    // se puede), cada slider tocado en UNA ventana cancelaba en el acto
    // el commit todavía en vuelo de la OTRA — el usuario podía perder en
    // silencio el ajuste que acababa de hacer en la primera ventana,
    // simplemente por tocar algo en la segunda. Ahora cada ventana tiene
    // su propio "carril" de commits (se siguen cancelando entre sí los
    // de la MISMA ventana — eso sigue siendo el debounce de siempre —
    // pero ya no pisan el carril de otra ventana). Los cierres "duros"
    // de sesión (revert al cancelar, undo/redo) siguen cancelando TODOS
    // los carriles de una capa a la vez — ver `cancelAllPendingCommitsFor`
    // más abajo — porque esos casos sí necesitan barrer cualquier
    // escritura pendiente de esa capa, venga de la ventana que venga.
    private val pendingLayerCommitJobs = mutableMapOf<String, Job>()

    /** Clave compuesta que usa [pendingLayerCommitJobs] — ver el
     * comentario grande de ahí arriba. */
    private fun layerCommitJobKey(layerId: String, source: String) = "$layerId::$source"

    /**
     * Cancela TODOS los commits todavía en vuelo de [layerId], sin
     * importar de qué ventana ("carril") vinieran — usado por los cierres
     * "duros" de una sesión de edición (revert al cancelar, undo/redo)
     * donde hace falta barrer cualquier escritura pendiente de esa capa
     * antes de restaurar `sourceUri`, no solo la de una ventana puntual.
     */
    private fun cancelAllPendingCommitsFor(layerId: String) {
        val prefix = "$layerId::"
        val keysToCancel = pendingLayerCommitJobs.keys.filter { it == layerId || it.startsWith(prefix) }
        keysToCancel.forEach { key -> pendingLayerCommitJobs.remove(key)?.cancel() }
    }

    // Único dueño de la duración del proyecto — ver comentario de módulo
    // más arriba y com.yeivikas.olyzecs.engine.timeline.TimelineDurationManager.
    private val timelineDurationManager = TimelineDurationManager()

    /** Avisos puntuales del motor de timeline (por ahora, solo "se llegó al máximo") para que la UI los muestre una vez. */
    val timelineEvents: SharedFlow<TimelineEvent> = timelineDurationManager.events

    private var autosaveJob: Job? = null
    // DISEÑO — auditoría (cancelar exportación): referencia a la corrutina
    // de export en curso, para poder cancelarla desde `cancelExport()`.
    // Mismo patrón que `autosaveJob` de arriba.
    private var exportJob: Job? = null

    // FASE 2 — auditoría de concurrencia (hallazgo confirmado): ANTES no
    // existía ninguna referencia al Job de `startPlaybackLoop()` — cada
    // llamada lanzaba una corrutina nueva con `viewModelScope.launch {}`
    // sin cancelar ninguna anterior. Esto podía crear MÁS de un loop de
    // reproducción activo al mismo tiempo con una secuencia rápida de
    // toques (Play/Pause/Play, o Play seguido de un scrub que suelta con
    // `endScrub()` mientras el loop viejo todavía no notó el `isPlaying =
    // false` de un toque anterior) — cada loop activo avanza el playhead
    // por su cuenta, así que dos a la vez producen justo el
    // comportamiento errático que describe el brief de Fase 2, sección 8.
    // Con esta referencia, [startPlaybackLoop] cancela cualquier loop
    // anterior ANTES de lanzar uno nuevo — nunca puede haber dos vivos.
    private var playbackJob: Job? = null

    // --- Preview en vivo del audio de fondo (independiente del pipeline de
    // export). Antes vivía como `remember { AudioPreviewPlayer(context) }`
    // directo en EditorScreen — eso hacía que la UI decidiera play/pause/
    // seek de audio por su cuenta. Ahora la UI solo avisa "cambió isPlaying/
    // audioClip" (ver [syncAudioPreview]) y quien decide qué hacer con el
    // reproductor es el ViewModel, igual que decide todo lo demás del
    // playback. Se crea perezosamente (recién en el primer uso, con
    // [Context.applicationContext] para no retener una Activity) y se
    // libera en [onCleared].
    private var audioPreviewPlayer: AudioPreviewPlayer? = null

    private fun audioPreviewPlayer(context: Context): AudioPreviewPlayer =
        audioPreviewPlayer ?: AudioPreviewPlayer(context.applicationContext).also { audioPreviewPlayer = it }

    /**
     * Sincroniza el preview de audio con el estado actual (isPlaying, clip
     * activo, mute). Se llama desde EditorScreen cada vez que alguno de
     * esos tres valores cambia — la UI se limita a notificar el cambio;
     * decidir si eso significa "pausar" o "reproducir desde tal punto" es
     * lógica del motor y vive acá, no en el composable.
     */
    fun syncAudioPreview(context: Context) {
        val state = _uiState.value
        val clip = state.audioClip
        val player = audioPreviewPlayer(context)
        if (clip == null || clip.muted || !state.isPlaying) {
            player.pause()
        } else {
            player.playFrom(clip, state.playheadMs)
        }
    }

    /** El volumen sí se aplica en caliente, sin reiniciar la reproducción. */
    fun updateAudioPreviewVolume(context: Context) {
        val clip = _uiState.value.audioClip ?: return
        audioPreviewPlayer(context).updateVolume(clip.volume)
    }

    override fun onCleared() {
        super.onCleared()
        // FASE 2 — cancelación explícita del playback, aunque
        // `viewModelScope` ya cancela automáticamente cualquier corrutina
        // lanzada con `viewModelScope.launch` al destruirse el ViewModel
        // (la app cachea el ViewModel por projectId mientras se navega
        // entre "Mis proyectos" y el editor — ver el comentario grande de
        // [resetPlaybackState] — así que `onCleared()` normalmente solo
        // corre cuando el proceso realmente termina con el proyecto).
        // Explícito, no implícito: no depender de un detalle de
        // implementación de `viewModelScope` para una garantía que el
        // brief de Fase 2 pide como criterio de aceptación (sección 27:
        // "ViewModel destruction no deja playback coroutines activas").
        stopPlaybackLoop()
        audioPreviewPlayer?.release()
    }

    // --- Undo/Redo: pilas de snapshots livianos (sin bitmaps ni Uris) ---
    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private var lastCheckpointAtMs = 0L

    // A PEDIDO DEL USUARIO — foto tomada apenas termina de cargar (ver el
    // final de `init`, abajo). Null mientras `isLoadingProject` sigue en
    // `true`; a partir de ahí siempre tiene un valor. Ver
    // `ProjectContentSnapshot` (arriba) para el detalle de qué compara.
    private var initialContentSnapshot: ProjectContentSnapshot? = null

    init {
        viewModelScope.launch {
            val loaded = projectStorage.loadProject(projectId)
            // La duración SIEMPRE pasa por TimelineDurationManager: si el
            // proyecto ya existía, se restaura tal cual estaba guardada
            // (saneada dentro de los límites válidos); si es nuevo, arranca
            // fijo en 1 minuto sin importar nada que venga de afuera.
            if (loaded != null) {
                timelineDurationManager.restore(loaded.projectDurationMs)
            } else {
                timelineDurationManager.startNewProject()
            }
            val timeline = timelineDurationManager.state.value
            _uiState.value = if (loaded != null) {
                _uiState.value.copy(
                    layers = loaded.layers,
                    projectName = loaded.name,
                    projectDurationMs = timeline.durationMs,
                    isAtMaxDuration = timeline.isAtMaxLimit,
                    selectedLayerId = null,
                    // ARREGLADO: el proyecto siempre reabría desde el
                    // segundo 0 del timeline, sin importar dónde había
                    // quedado el playhead al guardar — ver el comentario
                    // grande en ProjectData.playheadMs sobre por qué se
                    // perdía. `coerceIn` por seguridad: si la duración del
                    // timeline se acortó entre sesiones, el playhead
                    // restaurado nunca queda apuntando más allá del final.
                    playheadMs = loaded.playheadMs.coerceIn(0L, timeline.durationMs),
                    audioClip = loaded.audioClip,
                    speedKeyframes = loaded.speedKeyframes,
                    freezeFrames = loaded.freezeFrames,
                    // Antes de esto, el formato y el fps elegidos al crear
                    // el proyecto nunca se leían de vuelta al reabrirlo —
                    // quedaban pisados por el default (REELS/30fps) en cada
                    // apertura. Ahora se restauran desde lo guardado.
                    exportAspect = loaded.aspect,
                    projectFps = loaded.fps,
                    releaseYear = loaded.releaseYear,
                    genre = loaded.genre,
                    infoDurationMinutes = loaded.infoDurationMinutes,
                    castPhotoFiles = loaded.castPhotoFiles,
                    // Ver comentario en EditorUiState.gridEnabled: se
                    // restaura tal cual quedó guardado, para que la
                    // cuadrícula (activada/apagada, forma, columnas/filas
                    // y color de línea) no vuelva a los defaults al
                    // reabrir el proyecto.
                    gridEnabled = loaded.gridEnabled,
                    gridShapeName = loaded.gridShapeName,
                    gridColumns = loaded.gridColumns,
                    gridRows = loaded.gridRows,
                    gridLineColorEnabled = loaded.gridLineColorEnabled,
                    gridLineHue = loaded.gridLineHue,
                    gridLineThicknessDp = loaded.gridLineThicknessDp,
                    gridLineOpacity = loaded.gridLineOpacity,
                    gridSnapEnabled = loaded.gridSnapEnabled,
                    handleOrderGlobal = loaded.handleOrderGlobal,
                    handleOrderPerLayer = loaded.handleOrderPerLayer,
                    isLoadingProject = false,
                    revision = _uiState.value.revision + 1
                )
            } else {
                _uiState.value.copy(
                    projectName = initialName,
                    projectDurationMs = timeline.durationMs,
                    isAtMaxDuration = timeline.isAtMaxLimit,
                    exportAspect = initialAspect,
                    projectFps = initialFps,
                    isLoadingProject = false
                )
            }
            // Recién ACÁ, con el estado ya asentado (recién cargado o
            // recién creado), queda fijada la foto de referencia contra la
            // que se va a comparar más adelante para saber si hubo
            // cambios reales en la sesión.
            initialContentSnapshot = _uiState.value.toContentSnapshot()
        }
    }

    /**
     * A PEDIDO DEL USUARIO — dice si hubo cambios de verdad en esta
     * sesión (comparado contra `initialContentSnapshot`, la foto tomada
     * al abrir/crear el proyecto). Se usa en EditorScreen para decidir si
     * corresponde mostrar el diálogo "¿Guardar los cambios?" al tocar
     * "←"/atrás — si no hay nada distinto, sale directo sin interrumpir
     * al usuario con una pregunta que no tiene sentido. Mientras el
     * proyecto todavía está cargando (`initialContentSnapshot == null`)
     * devuelve `false`: no puede haber "cambios" todavía.
     */
    fun hasUnsavedChanges(): Boolean {
        val snapshot = initialContentSnapshot ?: return false
        return _uiState.value.toContentSnapshot() != snapshot
    }

    /**
     * A PEDIDO DEL USUARIO — "Salir sin guardar" del diálogo de "¿Guardar
     * los cambios?": vuelve TODO el contenido persistible del proyecto a
     * como estaba en `initialContentSnapshot` (la foto de al abrir/crear)
     * y recién ahí persiste ese estado revertido — pisando a propósito
     * cualquier autoguardado intermedio que ya hubiera escrito a disco
     * durante la sesión, para que "sin guardar" sea cierto de verdad y no
     * solo de nombre. Seguro para los archivos de imagen de las capas:
     * cada edición (Recolor/3D/Efectos) escribe un archivo NUEVO en vez
     * de pisar el original (ver `commitLayerRecolor`), así que el
     * `sourceUri` de la foto de referencia sigue apuntando a un archivo
     * intacto en disco.
     */
    fun discardChangesAndExit(onDone: () -> Unit = {}) {
        val snapshot = initialContentSnapshot
        if (snapshot == null) {
            // Todavía no había nada cargado — no hay a qué volver, así
            // que no hace falta ni tocar el estado.
            onDone()
            return
        }
        autosaveJob?.cancel()
        // Reconciliación por id contra las capas EN VIVO — no un simple
        // `layers = snapshot.layers` (eso ya no compila: el snapshot ahora
        // guarda [LayerContentState], no [Layer]; ver el comentario grande
        // en esa clase sobre por qué). Para cada capa que sigue existiendo,
        // se aplican los valores del snapshot IN-PLACE (preserva la textura
        // GL ya subida, mismo criterio que `restoreSnapshot`); para una capa
        // borrada durante la sesión, se reconstruye desde cero a partir del
        // snapshot (su copia local en disco sigue intacta); cualquier capa
        // creada DESPUÉS del snapshot (que no estaba al abrir el proyecto)
        // se descarta, porque "salir sin guardar" significa volver
        // exactamente al estado de apertura.
        val liveById = _uiState.value.layers.associateBy { it.id }
        val restoredLayers = snapshot.layers.map { state ->
            val existing = liveById[state.id]
            if (existing != null) {
                state.applyTo(existing, ::cancelAllPendingCommitsFor)
                existing
            } else {
                state.toFreshLayer()
            }
        }
        _uiState.value = _uiState.value.copy(
            projectName = snapshot.projectName,
            projectDurationMs = snapshot.projectDurationMs,
            layers = restoredLayers,
            audioClip = snapshot.audioClip?.toAudioClip(),
            speedKeyframes = snapshot.speedKeyframes,
            freezeFrames = snapshot.freezeFrames,
            exportAspect = snapshot.exportAspect,
            projectFps = snapshot.projectFps,
            releaseYear = snapshot.releaseYear,
            genre = snapshot.genre,
            infoDurationMinutes = snapshot.infoDurationMinutes,
            gridEnabled = snapshot.gridEnabled,
            gridShapeName = snapshot.gridShapeName,
            gridColumns = snapshot.gridColumns,
            gridRows = snapshot.gridRows,
            gridLineColorEnabled = snapshot.gridLineColorEnabled,
            gridLineHue = snapshot.gridLineHue,
            gridLineThicknessDp = snapshot.gridLineThicknessDp,
            gridLineOpacity = snapshot.gridLineOpacity,
            gridSnapEnabled = snapshot.gridSnapEnabled,
            handleOrderGlobal = snapshot.handleOrderGlobal,
            handleOrderPerLayer = snapshot.handleOrderPerLayer,
            selectedLayerId = null,
            revision = _uiState.value.revision + 1
        )
        viewModelScope.launch {
            persistNow(finalize = true)
            onDone()
        }
    }

    // ============================================================
    // Undo / Redo
    // ============================================================

    private fun captureSnapshot(): EditSnapshot {
        val state = _uiState.value
        return EditSnapshot(
            layers = state.layers.map { layer ->
                LayerEditState(
                    id = layer.id,
                    zIndex = layer.zIndex,
                    parallaxFactor = layer.parallaxFactor,
                    locked = layer.locked,
                    orderLocked = layer.orderLocked,
                    visible = layer.visible,
                    lookSettings = layer.lookSettings,
                    keyframes = layer.cameraTrack.keyframes.toList(),
                    baseFrame = layer.cameraTrack.baseFrame,
                    sourceUri = layer.sourceUri,
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
            },
            projectDurationMs = state.projectDurationMs,
            selectedLayerId = state.selectedLayerId,
            playheadMs = state.playheadMs
        )
    }

    /**
     * Aplica un snapshot MUTANDO las capas existentes en su lugar (nunca
     * reemplazando los objetos [Layer]) para no perder la textura GL ya
     * subida a GPU — reemplazar el objeto forzaría un re-decode/re-upload
     * innecesario y un parpadeo visible. Si una capa del snapshot ya no
     * existe (se eliminó después de tomar el checkpoint), se la ignora sin
     * error: el undo cubre transform/look/orden, no altas ni bajas de capas.
     *
     * `sourceUri` es la ÚNICA excepción al "nunca forzar reupload": si el
     * snapshot trae una imagen distinta a la actual (deshacer/rehacer una
     * sesión de Recolor/3D/Efectos/Distorsión, ver `beginLayerEditSession`
     * y el comentario grande en `LayerEditState.sourceUri`), SÍ hay que
     * invalidar la textura GL para que el motor suba la imagen correcta
     * — dejarla en `glTextureId` viejo mostraría la imagen equivocada con
     * el ícono de "deshecho" mal aplicado. Para el resto de los campos
     * (transform/keyframes/orden/visibilidad) la textura no cambia, así
     * que se preserva tal cual siempre se hizo.
     */
    private fun restoreSnapshot(snapshot: EditSnapshot) {
        val current = _uiState.value.layers
        snapshot.layers.forEach { edit ->
            val layer = current.find { it.id == edit.id } ?: return@forEach
            layer.zIndex = edit.zIndex
            layer.parallaxFactor = edit.parallaxFactor
            layer.locked = edit.locked
            layer.orderLocked = edit.orderLocked
            layer.visible = edit.visible
            layer.lookSettings = edit.lookSettings
            layer.cameraTrack.replaceAll(edit.keyframes)
            layer.cameraTrack.updateBaseFrame(edit.baseFrame)
            layer.colorIndex = edit.colorIndex
            layer.customColorArgb = edit.customColorArgb
            layer.importedDefaultColorArgb = edit.importedDefaultColorArgb
            layer.customGradientStartArgb = edit.customGradientStartArgb
            layer.customGradientEndArgb = edit.customGradientEndArgb
            layer.useGradientColor = edit.useGradientColor
            layer.gradientAngleDegrees = edit.gradientAngleDegrees
            layer.gradientIsRadial = edit.gradientIsRadial
            layer.useBlackAndWhiteMode = edit.useBlackAndWhiteMode
            if (layer.sourceUri != edit.sourceUri) {
                // Cualquier commit pendiente de esta capa (un efecto
                // todavía escribiéndose a disco, ver `commitLayerRecolor`/
                // `pendingLayerCommitJobs`) quedaría obsoleto frente a este
                // undo/redo — cancelarlo evita que termine más tarde y
                // pise el `sourceUri` que estamos por restaurar, mismo
                // criterio que `revertLayerEditSession`. Barre TODOS los
                // "carriles" de esta capa (ver `cancelAllPendingCommitsFor`),
                // no solo uno, porque puede haber más de una ventana de
                // edición con un commit en vuelo para la misma capa.
                cancelAllPendingCommitsFor(edit.id)
                layer.sourceUri = edit.sourceUri
                layer.pendingBitmap = null
                layer.glTextureId = -1
            }
        }
        _uiState.value = _uiState.value.copy(
            layers = current.toList(),
            projectDurationMs = snapshot.projectDurationMs,
            selectedLayerId = snapshot.selectedLayerId,
            playheadMs = snapshot.playheadMs,
            revision = _uiState.value.revision + 1,
            undoRedoTick = _uiState.value.undoRedoTick + 1
        )
    }

    /**
     * Guarda el estado ACTUAL en la pila de undo, antes de que el llamador
     * aplique su cambio. [force] = true para acciones discretas (un tap:
     * bloquear, reordenar, quitar keyframe); false (default) para cambios
     * continuos (arrastrar un slider o la imagen), que se fusionan dentro
     * de [UNDO_MERGE_WINDOW_MS] para no llenar el historial de pasos
     * microscópicos.
     */
    private fun pushUndoCheckpoint(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckpointAtMs < UNDO_MERGE_WINDOW_MS) return
        lastCheckpointAtMs = now

        undoStack.addLast(captureSnapshot())
        while (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
        redoStack.clear()

        _uiState.value = _uiState.value.copy(
            undoAvailable = true,
            redoAvailable = false
        )
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(captureSnapshot())
        while (redoStack.size > MAX_UNDO_STEPS) redoStack.removeFirst()
        restoreSnapshot(previous)
        lastCheckpointAtMs = 0L
        _uiState.value = _uiState.value.copy(
            undoAvailable = undoStack.isNotEmpty(),
            redoAvailable = true
        )
        scheduleAutosave()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(captureSnapshot())
        while (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
        restoreSnapshot(next)
        lastCheckpointAtMs = 0L
        _uiState.value = _uiState.value.copy(
            undoAvailable = true,
            redoAvailable = redoStack.isNotEmpty()
        )
        scheduleAutosave()
    }

    // ============================================================
    // Persistencia
    // ============================================================

    private fun notifyLayersChanged() {
        _uiState.value = _uiState.value.copy(
            layers = _uiState.value.layers.toList(),
            revision = _uiState.value.revision + 1
        )
        scheduleAutosave()
    }

    /**
     * Reemplaza UNA capa por una copia nueva con los cambios de [transform] —
     * en vez de mutar in-place el objeto Layer existente (ver BUG REAL en
     * setLayerCustomColor/setLayerGradient/etc: mutar un `var` de un data
     * class sin cambiar la referencia del objeto es lo que hacía que la fila
     * no se viera actualizada en pantalla hasta salir y volver a entrar a la
     * pantalla — Compose no tiene forma de "enterarse" de un cambio así).
     * `.copy()` crea una instancia realmente nueva, así que la fila
     * correspondiente en TimelineView SIEMPRE recompone en el momento.
     * No llama a notifyLayersChanged() sola — quien llama decide cuándo
     * (así setLayers/replaceLayers de abajo pueden aplicar varios cambios
     * y notificar UNA sola vez).
     *
     * BUG REAL corregido acá (el que causó que las imágenes desaparecieran
     * del lienzo justo después de aplicar color/degradado): `glTextureId` y
     * `pendingBitmap` en Layer.kt son `@Transient var` declarados FUERA del
     * constructor del data class — son el puente hacia la textura ya subida
     * a la GPU. `.copy()` de Kotlin SOLO copia las propiedades del
     * constructor; todo lo declarado en el cuerpo de la clase (como estos
     * dos) vuelve a su valor por defecto (-1 / null) en la instancia nueva.
     * Sin este traspaso manual, cada `.copy()` "olvidaba" la textura ya
     * subida y el motor GL se quedaba sin nada que dibujar para esa capa —
     * la imagen desaparecía del lienzo Y de la miniatura del timeline (que
     * también depende de esta misma referencia), quedando solo el fondo de
     * color. [preserveRenderState] = false únicamente para el caso en que
     * SÍ querés forzar una recarga real de textura (reemplazar la imagen de
     * la capa por otra) — ver replaceLayerImage más abajo.
     */
    private fun replaceLayer(layerId: String, preserveRenderState: Boolean = true, transform: (Layer) -> Layer) {
        _uiState.value = _uiState.value.copy(
            layers = _uiState.value.layers.map { old ->
                if (old.id == layerId) {
                    val updated = transform(old)
                    if (preserveRenderState) {
                        updated.glTextureId = old.glTextureId
                        updated.pendingBitmap = old.pendingBitmap
                    }
                    updated
                } else old
            }
        )
        notifyLayersChanged()
    }

    /**
     * Igual que [replaceLayer] pero para VARIAS capas de un tirón (ej.
     * "Multicolor" aplicando a todas las capas marcadas) — reemplaza cada
     * capa cuyo id esté en [layerIds] por su copia transformada, en UNA
     * sola actualización de estado (una sola recomposición, un solo
     * autosave programado), en vez de una llamada de ViewModel por capa.
     * Igual que [replaceLayer], traslada `glTextureId`/`pendingBitmap` de
     * cada capa vieja a su copia nueva para no perder la textura ya subida.
     */
    private fun replaceLayers(layerIds: Collection<String>, transform: (Layer) -> Layer) {
        if (layerIds.isEmpty()) return
        val idSet = layerIds.toSet()
        _uiState.value = _uiState.value.copy(
            layers = _uiState.value.layers.map { old ->
                if (old.id in idSet) {
                    val updated = transform(old)
                    updated.glTextureId = old.glTextureId
                    updated.pendingBitmap = old.pendingBitmap
                    updated
                } else old
            }
        )
        notifyLayersChanged()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            // finalize = false: autoguardado de RUTINA (se dispara solo,
            // cada vez que se toca cualquier cosa del proyecto — no solo
            // el título). Este NO debe tocar el campo de texto en pantalla
            // mientras el usuario sigue en el panel: por eso persistNow
            // guarda el default "Project01" en el DISCO si hace falta,
            // pero deja `projectName` del estado tal cual estaba (aunque
            // esté vacío), para que el campo no se autorellene solo a los
            // 1-2 segundos.
            persistNow(finalize = false)
        }
    }

    /**
     * [finalize] separa dos momentos que antes eran el mismo, y ESE era el
     * bug: refrescar el campo "Título" con el default "Project01" ni bien
     * pasa el debounce de autoguardado (1-2 segundos), incluso con el
     * usuario todavía escribiendo/parado en el panel.
     * - `false` (autoguardado de rutina, ver [scheduleAutosave]): persiste
     *   en disco con el default si hace falta, pero NO pisa
     *   `_uiState.projectName` — el campo se queda exactamente como lo
     *   dejó el usuario (incluso vacío, mostrando el placeholder).
     * - `true` (ver [saveNow]): además de guardar, refleja el nombre
     *   efectivo de vuelta al campo. Esto solo debe pasar en un guardado
     *   FINAL — al cerrar el proyecto (volver a "Mis proyectos") o al
     *   guardar manualmente — que es cuando de verdad corresponde que el
     *   usuario vea escrito "Project01" en el campo, listo para
     *   renombrarlo la próxima vez que entre.
     */
    private suspend fun persistNow(finalize: Boolean) {
        val state = _uiState.value
        if (state.layers.isEmpty()) return
        _uiState.value = _uiState.value.copy(saveState = SaveState.Saving)
        try {
            // FASE 1 (AUDITORÍA P0) — CONSISTENCIA DEL GUARDADO.
            //
            // ESTADO VIVO → CAPTURA CONTROLADA → SNAPSHOT ESTABLE → IO.
            // Estas dos líneas son la "captura controlada": se ejecutan acá,
            // en el hilo que llama (Main, dentro de viewModelScope), ANTES
            // de que `saveProject` cruce a Dispatchers.IO. Cada campo `var`
            // de cada Layer/AudioClip se lee UNA sola vez, de forma
            // síncrona, y queda copiado en un DTO inmutable
            // (LayerSaveSnapshot/AudioSaveSnapshot) — ver el KDoc grande en
            // esos tipos sobre la corrida real que esto corrige: el hilo de
            // IO ya NUNCA vuelve a leer un campo mutable de un Layer en
            // vivo mientras serializa, así que un undo/redo o un
            // "discardChangesAndExit" que ocurra EN PARALELO (el hilo
            // Main sigue libre mientras el corrutine de guardado está
            // suspendido en IO) no puede producir un project.json a medio
            // mutar.
            val layerSnapshots = state.layers.map { it.toSaveSnapshot() }
            val audioSnapshot = state.audioClip?.toSaveSnapshot()

            // saveProject devuelve el nombre EFECTIVO que quedó guardado en
            // project.json: si `state.projectName` venía vacío o solo con
            // espacios (el usuario no escribió título), ProjectStorage lo
            // reemplaza por el default "Project01" (ver DEFAULT_PROJECT_NAME)
            // y ese es el valor que vuelve acá.
            val result = projectStorage.saveProject(
                projectId = projectId,
                name = state.projectName,
                projectDurationMs = state.projectDurationMs,
                playheadMs = state.playheadMs,
                layers = layerSnapshots,
                liveLayersForThumbnail = state.layers,
                audioClip = audioSnapshot,
                speedKeyframes = state.speedKeyframes,
                freezeFrames = state.freezeFrames,
                aspect = state.exportAspect,
                fps = state.projectFps,
                releaseYear = state.releaseYear,
                genre = state.genre,
                infoDurationMinutes = state.infoDurationMinutes,
                gridEnabled = state.gridEnabled,
                gridShapeName = state.gridShapeName,
                gridColumns = state.gridColumns,
                gridRows = state.gridRows,
                gridLineColorEnabled = state.gridLineColorEnabled,
                gridLineHue = state.gridLineHue,
                gridLineThicknessDp = state.gridLineThicknessDp,
                gridLineOpacity = state.gridLineOpacity,
                gridSnapEnabled = state.gridSnapEnabled,
                handleOrderGlobal = state.handleOrderGlobal,
                handleOrderPerLayer = state.handleOrderPerLayer
            )
            val savedName = result.effectiveName

            // Aplicación diferida y SEGURA de los sourceUri locales recién
            // resueltos (optimización: evita re-copiar desde el Uri de SAF
            // original en cada autoguardado siguiente — ver KDoc de
            // SaveProjectResult). Esto corre DESPUÉS de que la IO terminó,
            // de forma secuencial en este mismo corrutine — no hay carrera
            // acá. Y solo se aplica si el campo NO cambió desde que se
            // capturó el snapshot (comparación contra el Uri capturado en
            // `layerSnapshots`/`audioSnapshot`), para no pisar una edición
            // más nueva del usuario (p. ej. reemplazó la imagen de vuelta)
            // que haya ocurrido mientras el guardado anterior estaba en
            // vuelo.
            if (result.resolvedLayerImageUris.isNotEmpty()) {
                val liveLayersById = _uiState.value.layers.associateBy { it.id }
                val originalUriById = layerSnapshots.associate { it.id to it.sourceUri }
                result.resolvedLayerImageUris.forEach { (layerId, resolvedUri) ->
                    val liveLayer = liveLayersById[layerId] ?: return@forEach
                    if (liveLayer.sourceUri == originalUriById[layerId]) {
                        liveLayer.sourceUri = resolvedUri
                    }
                }
            }
            result.resolvedAudioUri?.let { resolvedUri ->
                val liveClip = _uiState.value.audioClip
                if (liveClip != null && liveClip.sourceUri == audioSnapshot?.sourceUri) {
                    liveClip.sourceUri = resolvedUri
                }
            }

            _uiState.value = _uiState.value.copy(
                saveState = SaveState.Saved(System.currentTimeMillis()),
                // Solo en el guardado FINAL (finalize = true) se refleja el
                // nombre efectivo de vuelta al campo. En el autoguardado de
                // rutina se deja `state.projectName` intacto — si acá se
                // usara siempre `savedName`, es lo que hacía que el campo
                // se autorellenara solo con "Project01" a los 1-2 segundos
                // aunque el usuario siguiera parado en el panel.
                projectName = if (finalize) savedName else state.projectName
            )
        } catch (t: Throwable) {
            AppLogger.e("EditorViewModel", "Error guardando el proyecto '$projectId' — los últimos cambios podrían haberse perdido", t)
            _uiState.value = _uiState.value.copy(saveState = SaveState.Error(t.message ?: "Error al guardar"))
        }
    }

    fun saveNow(onDone: () -> Unit = {}) {
        autosaveJob?.cancel()
        viewModelScope.launch {
            persistNow(finalize = true)
            onDone()
        }
    }

    fun renameProject(newName: String) {
        // BUG REAL corregido acá — antes, si el usuario borraba TODO el
        // texto del campo "Título" (ver ProjectInfoPanel), este método
        // cortaba con `if (trimmed.isEmpty()) return` y el estado se
        // quedaba pegado con el último carácter escrito (ej.: quedaba una
        // sola "C" en el campo aunque el usuario la hubiera borrado).
        // Ahora se guarda tal cual viene del campo, sin early-return, para
        // que el campo SÍ pueda quedar realmente vacío y reaparezca el
        // placeholder semitransparente "Título". El default "Project01" y
        // el trim() de espacios se aplican recién al guardar (ver
        // persistNow / ProjectStorage.saveProject), nunca mientras el
        // usuario todavía está escribiendo.
        _uiState.value = _uiState.value.copy(projectName = newName)
        scheduleAutosave()
    }

    // ============================================================
    // Panel "Información del proyecto" (lado izquierdo — ver
    // ProjectInfoPanel en EditorBottomBar.kt)
    // ============================================================

    fun updateReleaseYear(year: Int) {
        _uiState.value = _uiState.value.copy(releaseYear = year)
        scheduleAutosave()
    }

    fun updateGenre(genre: String) {
        if (genre.isBlank()) return
        _uiState.value = _uiState.value.copy(genre = genre)
        scheduleAutosave()
    }

    /** [minutes] es la duración "de ficha" (metadata), no la duración real del timeline. */
    fun updateInfoDurationMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(infoDurationMinutes = minutes.coerceAtLeast(0))
        scheduleAutosave()
    }

    // ============================================================
    // Guías de composición (cuadrícula) — ver comentario en
    // EditorUiState.gridEnabled sobre el bug que esto corrige: antes vivía
    // solo en memoria de EditorScreen.kt y se perdía al salir y volver a
    // entrar al proyecto.
    // ============================================================

    /**
     * Actualiza cualquier combinación de ajustes de la cuadrícula de una
     * sola vez (EditorScreen.kt llama esto desde el on/off del switch, el
     * carrusel de formas, los steppers de columnas/filas y el switch +
     * franja de color — cada uno pisando solo los parámetros que le
     * corresponden, el resto queda como estaba).
     */
    fun updateGridSettings(
        enabled: Boolean? = null,
        shapeName: String? = null,
        columns: Int? = null,
        rows: Int? = null,
        lineColorEnabled: Boolean? = null,
        lineHue: Float? = null,
        lineThicknessDp: Float? = null,
        lineOpacity: Float? = null,
        snapEnabled: Boolean? = null
    ) {
        val current = _uiState.value
        _uiState.value = current.copy(
            gridEnabled = enabled ?: current.gridEnabled,
            gridShapeName = shapeName ?: current.gridShapeName,
            gridColumns = columns ?: current.gridColumns,
            gridRows = rows ?: current.gridRows,
            gridLineColorEnabled = lineColorEnabled ?: current.gridLineColorEnabled,
            gridLineHue = lineHue ?: current.gridLineHue,
            gridLineThicknessDp = lineThicknessDp ?: current.gridLineThicknessDp,
            gridLineOpacity = lineOpacity ?: current.gridLineOpacity,
            gridSnapEnabled = snapEnabled ?: current.gridSnapEnabled
        )
        scheduleAutosave()
    }

    /**
     * Guarda el orden de manijas confirmado desde la mini-ventana
     * "Solo"/"Todos" (ver EditorScreen.kt, manija de reordenar). `scope`
     * decide en qué campo entra: "ONLY_HERE" pisa/agrega el override de
     * [layerId] dentro de [handleOrderPerLayer]; "ALL" reemplaza
     * [handleOrderGlobal] entero. `order` ya viene codificado como
     * `Map<String, String>` (nombre de posición → nombre de función) —
     * EditorScreen.kt es responsable de esa conversión, este ViewModel no
     * conoce los enums privados de la capa de UI.
     */
    fun updateHandleOrder(scope: String, layerId: String?, order: Map<String, String>) {
        val current = _uiState.value
        _uiState.value = when (scope) {
            "ONLY_HERE" -> if (layerId != null) {
                current.copy(handleOrderPerLayer = current.handleOrderPerLayer + (layerId to order))
            } else {
                current
            }
            "ALL" -> current.copy(handleOrderGlobal = order)
            else -> current
        }
        scheduleAutosave()
    }

    /**
     * Restablece el orden de manijas a los valores de fábrica
     * (DEFAULT_HANDLE_ORDER en EditorScreen.kt — acá simplemente se borra
     * lo guardado, ya que la ausencia de override ya se interpreta como
     * "usar el de fábrica" tanto al cargar como al resolver el efectivo).
     * `scope`: "ONLY_HERE" quita el override de [layerId] únicamente;
     * "ALL" borra el orden global entero (las capas con su propio override
     * "Solo" no se ven afectadas por esto).
     */
    fun restoreHandleOrder(scope: String, layerId: String?) {
        val current = _uiState.value
        _uiState.value = when (scope) {
            "ONLY_HERE" -> if (layerId != null) {
                current.copy(handleOrderPerLayer = current.handleOrderPerLayer - layerId)
            } else {
                current
            }
            "ALL" -> current.copy(handleOrderGlobal = emptyMap())
            else -> current
        }
        scheduleAutosave()
    }

    /**
     * Copia [uri] como foto de la casilla [slotIndex] (0..3) y refresca esa
     * única entrada en memoria — a diferencia del resto de este panel, esto
     * se guarda de inmediato en disco (ver [ProjectStorage.setCastPhoto]),
     * no a través del autoguardado con debounce.
     */
    fun setCastPhoto(slotIndex: Int, uri: Uri) {
        viewModelScope.launch {
            projectStorage.setCastPhoto(projectId, slotIndex, uri)
            val file = projectStorage.castPhotoFile(projectId, slotIndex).takeIf { it.exists() }
            val updated = _uiState.value.castPhotoFiles.toMutableList()
            if (slotIndex in updated.indices) {
                updated[slotIndex] = file
                _uiState.value = _uiState.value.copy(castPhotoFiles = updated)
            }
        }
    }

    fun removeCastPhoto(slotIndex: Int) {
        viewModelScope.launch {
            projectStorage.removeCastPhoto(projectId, slotIndex)
            val updated = _uiState.value.castPhotoFiles.toMutableList()
            if (slotIndex in updated.indices) {
                updated[slotIndex] = null
                _uiState.value = _uiState.value.copy(castPhotoFiles = updated)
            }
        }
    }

    /**
     * Vuelve a leer el nombre del proyecto desde disco y actualiza el
     * estado en memoria si cambió. Necesario porque este ViewModel puede
     * venir RECICLADO del ViewModelStore de la Activity (para no re-decodificar
     * imágenes cada vez que se reabre el mismo proyecto — ver comentario en
     * MainActivity junto al `viewModel(factory = ..., key = projectId)`):
     * si el nombre se cambió desde "Mis proyectos" (RenameProjectDialog)
     * MIENTRAS este ViewModel seguía vivo en caché, su copia en memoria
     * queda desactualizada, y el próximo autoguardado (incluso uno
     * disparado por `saveNow()` al simple hecho de entrar y salir sin tocar
     * nada) volvería a escribir ese nombre viejo, pisando el renombrado.
     * Por eso [MainActivity] llama a esto cada vez que se (re)entra al
     * editor, ANTES de que cualquier autoguardado pueda dispararse.
     */
    fun refreshProjectNameFromDisk() {
        viewModelScope.launch {
            val diskName = projectStorage.peekProjectName(projectId)
            if (diskName != null && diskName != _uiState.value.projectName) {
                _uiState.value = _uiState.value.copy(projectName = diskName)
            }
        }
    }

    // ============================================================
    // Capas
    // ============================================================

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            val startingZ = _uiState.value.layers.size
            val newLayers = layerRepository.importAsLayers(
                uris,
                startingZIndex = startingZ,
                startingColorIndex = startingZ
            )
            _uiState.value = _uiState.value.copy(
                layers = _uiState.value.layers + newLayers,
                // Nunca auto-seleccionar al importar: el marco de
                // selección solo debe aparecer cuando el usuario toca
                // una capa a propósito (canvas o timeline) — auto-
                // seleccionar acá dejaba el marco violeta puesto sobre
                // la imagen apenas se importaba, sin que nadie lo pidiera.
                isImporting = false,
                revision = _uiState.value.revision + 1
            )
            scheduleAutosave()
        }
    }

    fun selectLayer(layerId: String) {
        _uiState.value = _uiState.value.copy(selectedLayerId = layerId)
    }

    /** Deselecciona la capa actual (si había alguna). Se usa cuando el
     * usuario toca un espacio vacío del canvas — el marco de selección
     * tiene que desaparecer ahí, no solo al elegir otra capa. */
    fun clearSelection() {
        if (_uiState.value.selectedLayerId != null) {
            _uiState.value = _uiState.value.copy(selectedLayerId = null)
        }
    }

    // --- Recoloreo por color extraído (panel "Ajustes y parámetros de
    // edición" del modo edición dedicado — ver EditorScreen.kt, y
    // ColorExtraction.extractPalette/recolor para el cómo). No es lo
    // mismo que replaceLayerImage: acá la imagen sigue siendo la MISMA,
    // solo cambian algunos de sus colores, así que no se toca name,
    // customColorArgb ni importedDefaultColorArgb.

    /**
     * Único punto de acceso de la UI al motor de extrusión 3D
     * ([com.yeivikas.olyzecs.engine.mesh3d.Extrude3D],
     * FASE B — antes `EditorScreen` lo llamaba directo). Envoltorio
     * deliberadamente fino: no agrega lógica propia, solo delega a
     * [mesh3DApi] (Mesh3D→EliNer: antes llamaba `Extrude3D.render`
     * directo acá mismo; el Dispatcher lo decide `Mesh3DApiImpl`, no
     * esta función, así que ya no hace falta el propio
     * `withContext(Dispatchers.Default)` acá). El debounce/throttle de
     * cuándo llamar a esto (vista previa en vivo vs. commit final a los
     * 500ms) sigue siendo responsabilidad de la UI: es una decisión de
     * timing de interacción, no de motor.
     */
    suspend fun renderExtrude3D(
        source: Bitmap,
        params: Extrude3D.Params,
        highQuality: Boolean = true
    ): Bitmap = mesh3DApi.extrude(source, params, highQuality)

    /**
     * Único punto de acceso de la UI al motor de "Distorsión"
     * ([com.yeivikas.olyzecs.engine.distortion.DistortionRasterizer]).
     * Envoltorio deliberadamente fino, mismo criterio que
     * [renderExtrude3D]: no agrega lógica propia, solo delega a
     * [distortionApi]. [outWidth]/[outHeight] se pasan tal cual desde
     * `DistortionPanel` — permiten pedir un render sobre un bitmap chico
     * (vista previa en vivo mientras se arrastra el dedo) o sobre el
     * bitmap completo (commit final al soltar), reusando la MISMA malla
     * acumulada en ambos casos.
     */
    suspend fun renderDistortion(
        source: Bitmap,
        field: DistortionField,
        outWidth: Int = source.width,
        outHeight: Int = source.height
    ): Bitmap = distortionApi.render(source, field, outWidth, outHeight)

    /**
     * Pestaña "Efectos" (al lado de "3D", ver EditImageToolsHeader /
     * EffectsPanel en EditorScreen.kt): difuminado, saturación y sombra
     * proyectada, horneados sobre una copia del bitmap — mismo patrón que
     * [renderExtrude3D] arriba, pero sin pasar por EliNer API todavía
     * (dominio nuevo, no migrado; ver nota grande sobre la frontera de
     * EliNer API más arriba en esta clase). Corre en Dispatchers.Default
     * para no bloquear el hilo de UI mientras se arrastra un slider.
     */
    suspend fun applyImageEffects(
        source: Bitmap,
        params: com.yeivikas.olyzecs.engine.effects.ImageEffectsParams
    ): Bitmap = withContext(Dispatchers.Default) {
        com.yeivikas.olyzecs.engine.effects.ImageEffects.apply(source, params)
    }

    /**
     * Vista previa en vivo: fuerza a la capa a mostrar [bitmap] ya
     * (preserveRenderState=false re-sube la textura), pensada para
     * llamarse en cada movimiento de la rueda de color mientras se
     * arrastra — liviana, en memoria, SIN escribir nada a disco ni
     * disparar autoguardado (por eso no pasa por notifyLayersChanged/
     * scheduleAutosave: replaceLayer ya llama a notifyLayersChanged, que
     * sí agenda autoguardado, pero como ProjectStorage guarda por
     * `sourceUri` y no por el bitmap en memoria, ese autoguardado de
     * rutina no perdería nada aunque se dispare de más durante el
     * arrastre — ver commitLayerRecolor para el guardado real y
     * definitivo al soltar el dedo).
     */
    fun previewLayerRecolor(layerId: String, bitmap: Bitmap) {
        replaceLayer(layerId, preserveRenderState = false) { it.apply { pendingBitmap = bitmap } }
    }

    /**
     * Guardado definitivo del recoloreo: escribe [bitmap] (ya en alta
     * resolución) como un archivo nuevo (ver
     * LayerRepository.saveBitmapAsLocalUri) y actualiza `sourceUri` de la
     * capa para apuntar ahí — mismo mecanismo que reemplazar la imagen,
     * así el cambio sobrevive a guardar/cerrar/reabrir el proyecto y
     * queda incluido en la exportación final sin tocar ProjectStorage ni
     * el exportador: para ellos es una imagen de capa más, indistinguible
     * de cualquier otra. Se llama una sola vez al soltar el dedo de la
     * rueda (no en cada frame del arrastre — ver previewLayerRecolor para
     * eso), para no escribir decenas de archivos por segundo.
     *
     * @param source A PEDIDO DEL USUARIO — MULTI-VENTANA: identifica QUÉ
     *   ventana de edición llama (p. ej. "recolor", "colorBasico", "3d",
     *   "effects", "distortion") — ver el comentario grande de
     *   [pendingLayerCommitJobs]. Determina el "carril" de cancelación:
     *   un commit nuevo solo cancela el anterior EN VUELO de la MISMA
     *   ventana, nunca el de otra ventana editando la misma capa al
     *   mismo tiempo. Valor por defecto "default" para no romper a
     *   quien todavía no pasa este parámetro.
     */
    fun commitLayerRecolor(layerId: String, bitmap: Bitmap, source: String = "default") {
        // Cancela cualquier commit anterior TODAVÍA en vuelo de esta MISMA
        // ventana ("carril", ver `source`) para esta misma capa antes de
        // lanzar el nuevo — evita que dos escrituras de la MISMA ventana
        // terminen en cualquier orden y que la más vieja pise a la más
        // nueva (o, más grave, a un revert que haya pasado en el medio;
        // ver revertLayerEditSession, más abajo, y el comentario grande
        // en pendingLayerCommitJobs). Ya NO cancela el commit en vuelo de
        // OTRA ventana editando la misma capa — esa es la parte nueva.
        val jobKey = layerCommitJobKey(layerId, source)
        pendingLayerCommitJobs[jobKey]?.cancel()
        val job = viewModelScope.launch {
            val savedUri = withContext(Dispatchers.IO) {
                layerRepository.saveBitmapAsLocalUri(bitmap, "recolor")
            }
            if (savedUri == null) {
                // No se pudo escribir a disco: se deja la vista previa en
                // memoria tal cual (previewLayerRecolor ya la aplicó, se
                // sigue viendo bien en pantalla), simplemente no queda
                // persistida — mejor eso que perder el cambio visual.
                AppLogger.w("EditorViewModel", "No se pudo persistir el recoloreo de la capa $layerId, queda solo en memoria")
                return@launch
            }
            replaceLayer(layerId, preserveRenderState = false) {
                it.copy(sourceUri = savedUri).apply { pendingBitmap = bitmap }
            }
            scheduleAutosave()
        }
        pendingLayerCommitJobs[jobKey] = job
        job.invokeOnCompletion { pendingLayerCommitJobs.remove(jobKey, job) }
    }

    /**
     * A PEDIDO DEL USUARIO: guarda un checkpoint de Deshacer/Rehacer
     * "de proyecto" justo ANTES de entrar en modo edición aislado
     * (Recolor/3D/Efectos/Distorsión) para una capa — llamar desde
     * `enterEditModeForSelectedLayer`, del lado de EditorScreen, ANTES
     * de que cualquier slider toque `sourceUri`. Con esto, si el
     * usuario confirma (✓) la sesión, el ↩ de la barra superior queda
     * listo para deshacer la sesión ENTERA de una sola vez (mismo
     * criterio que Photoshop/Lightroom: un filtro/ajuste modal es UN
     * paso en el historial general, no uno por cada pincelada/slider
     * interno) — sin esto, confirmar una sesión de edición era
     * invisible para el Deshacer general una vez cerrado el panel.
     *
     * Si en cambio el usuario CANCELA (×), este checkpoint se descarta
     * sin dejar rastro — ver `revertLayerEditSession`, más abajo: una
     * sesión cancelada no cambió nada de forma neta, así que no debe
     * ocupar un paso del historial (mismo criterio que Photoshop: tocar
     * "Cancelar" en un diálogo de ajuste no agrega una entrada al
     * History panel).
     */
    fun beginLayerEditSession(layerId: String) {
        pushUndoCheckpoint(force = true)
    }

    /**
     * Descarta lo hecho en una sesión de modo edición (Recolor/3D/Efectos)
     * y devuelve la capa exactamente a como estaba antes de entrar: pisa
     * `sourceUri` con [originalSourceUri] (el que tenía la capa al llamar
     * a `enterEditModeForSelectedLayer`, capturado del lado de
     * EditorScreen) y limpia `pendingBitmap` para que no quede la vista
     * previa en memoria de la edición cancelada.
     *
     * BUG REAL ENCONTRADO: la × (cancelar) del modo edición y el check
     * (confirmar) llamaban a la MISMA función `exitEditMode()` del lado
     * de EditorScreen, que solo repone la posición de la capa — nunca
     * revertía el bitmap. Como `commitLayerRecolor` ya escribe el
     * recoloreo/3D/efecto a disco y actualiza `sourceUri` en cada suelte
     * de dedo (con debounce de 500ms, no recién al confirmar), cancelar
     * dejaba el cambio aplicado igual, contradiciendo el diálogo que le
     * dice al usuario "los cambios no se van a aplicar". Esta función es
     * el arreglo: solo se llama desde la rama de CANCELAR, nunca desde la
     * de confirmar.
     *
     * `preserveRenderState = false` a propósito: fuerza a GLRenderer a
     * volver a subir la textura desde el `sourceUri` original en vez de
     * seguir mostrando la textura ya recoloreada que quedó en GPU.
     */
    fun revertLayerEditSession(layerId: String, originalSourceUri: Uri) {
        // ARREGLADO junto con `commitLayerRecolor`, arriba: si un commit
        // de esta capa ya estaba en vuelo (escribiendo a disco el efecto
        // que se está por cancelar), hay que cancelarlo ACÁ, antes de
        // revertir el estado — si no, cuando esa escritura tardía
        // termine va a pisar el `sourceUri` que estamos por devolver a
        // [originalSourceUri], y "Sí, salir"/"No aplicar" va a quedar
        // aplicando el efecto igual, que es exactamente el bug
        // reportado. A PEDIDO DEL USUARIO — MULTI-VENTANA: barre TODOS
        // los "carriles" de esta capa (ver `cancelAllPendingCommitsFor`),
        // no solo uno — si el usuario tenía más de una ventana abierta
        // editando esta misma capa, cualquiera de ellas puede tener un
        // commit en vuelo que haya que cancelar acá también.
        cancelAllPendingCommitsFor(layerId)
        replaceLayer(layerId, preserveRenderState = false) {
            it.copy(sourceUri = originalSourceUri).apply { pendingBitmap = null }
        }
        // Descarta el checkpoint que `beginLayerEditSession` dejó al
        // entrar a esta sesión — cancelar significa que, de forma neta,
        // no cambió nada, así que no debe quedar un paso "fantasma" en
        // el historial de Deshacer (ver comentario grande de
        // `beginLayerEditSession`, arriba). Sólo se saca el ÚLTIMO
        // elemento de la pila, nunca más de uno: mientras el modo
        // edición aislado está abierto no hay forma de disparar otro
        // checkpoint por encima (la UI no ofrece otras acciones
        // deshacibles ahí adentro), así que el tope de la pila es,
        // siempre, ese mismo checkpoint.
        undoStack.removeLastOrNull()
        _uiState.value = _uiState.value.copy(undoAvailable = undoStack.isNotEmpty())
        scheduleAutosave()
    }

    /**
     * A PEDIDO EXPLÍCITO DEL USUARIO: soporte real de "Eliminar efecto"
     * en las tres ventanas flotantes (Recolor / Color Básico / 3D
     * Básico) — arrastrar el ícono ya minimizado a la zona de
     * "Eliminar" tiene que devolver la capa exactamente a como estaba
     * ANTES de que ESA ventana la tocara, sin dejar nada de esa sesión
     * horneado en el archivo.
     *
     * BUG REAL corregido: la primera versión de esto, del lado de
     * EditorScreen, volvía a DECODIFICAR de forma asíncrona el
     * `sourceUri` que la capa tenía al abrir la ventana, guardando el
     * Bitmap resultante para commitearlo de vuelta al momento de
     * eliminar. Esa decodificación corría en un hilo de IO aparte, sin
     * ninguna garantía de que terminara ANTES de que otro commit de esa
     * misma capa (de esta ventana o de otra abierta en paralelo)
     * sobreescribiera el archivo que estaba leyendo a mitad de
     * lectura — una carrera real que, en la práctica, terminó
     * decodificando un archivo corrupto/vacío a mitad de escritura y
     * commiteando ESO, borrando la imagen entera de la capa. Reportado
     * con captura: el lienzo quedó completamente vacío después de
     * "Eliminar".
     *
     * La corrección: en vez de decodificar nada de nuevo, cada ventana
     * ahora solo GUARDA el `Uri` (no el contenido) que la capa tenía al
     * abrirse — sin IO, sin corrutina, sin ventana de carrera posible —
     * y "Eliminar" simplemente pisa `sourceUri` de vuelta a ESE Uri
     * exacto, tal cual.
     *
     * A diferencia de [revertLayerEditSession] (pensado para el modo de
     * edición aislado de pantalla completa, con su propio checkpoint de
     * Deshacer empujado por [beginLayerEditSession] al entrar): estas
     * tres ventanas flotantes NUNCA llaman a `beginLayerEditSession` —
     * pueden convivir varias abiertas a la vez editando la MISMA capa,
     * así que no existe un único "checkpoint de sesión" que sacar del
     * historial al eliminar. Por eso esta función hace SOLO la parte
     * seguro-reutilizable de revertir el archivo (cancelar cualquier
     * commit de esta capa que siga en camino + pisar `sourceUri` +
     * programar el autoguardado) — sin tocar `undoStack` para nada, a
     * propósito: tocarlo acá sacaría del historial general un
     * checkpoint de una sesión completamente distinta que nunca lo
     * empujó.
     */
    fun revertLayerToUri(layerId: String, originalUri: Uri) {
        cancelAllPendingCommitsFor(layerId)
        replaceLayer(layerId, preserveRenderState = false) {
            it.copy(sourceUri = originalUri).apply { pendingBitmap = null }
        }
        scheduleAutosave()
    }

    fun replaceLayerImage(layerId: String, uri: Uri) {        viewModelScope.launch {
            val decoded = layerRepository.decode(uri) ?: return@launch
            // El color dominante se vuelve a calcular sobre la imagen NUEVA
            // que se está cargando en la capa — ver el pedido explícito de
            // que "la capa tome el color de su imagen o lo que se esté
            // cargando en la capa". Se pisa tanto el color ACTUAL
            // (customColorArgb, lo que se ve ahora mismo) como el default
            // de fábrica (importedDefaultColorArgb, adonde vuelve
            // "Restablecer"): reemplazar la imagen es, a todo efecto de
            // color, como si esa fuera la imagen con la que la capa se
            // hubiese importado desde el principio.
            val dominant = ColorExtraction.dominantColor(decoded.bitmap)
            // pendingBitmap es @Transient (vive fuera del constructor del data
            // class, ver Layer.kt) — .copy() NO lo traslada solo, así que hay
            // que asignarlo a mano sobre la instancia NUEVA (no la vieja) para
            // que no se pierda.
            // preserveRenderState = false A PROPÓSITO acá: esta función
            // reemplaza la imagen de la capa por otra distinta, así que SÍ
            // necesita que glTextureId vuelva a -1 (fuerza al motor GL a
            // subir una textura nueva) — es el único caso de los que usan
            // replaceLayer donde NO querés conservar la textura anterior.
            replaceLayer(layerId, preserveRenderState = false) {
                it.copy(
                    sourceUri = uri,
                    name = decoded.displayName,
                    customColorArgb = dominant,
                    importedDefaultColorArgb = dominant,
                    // Un color sólido nuevo manda por encima de cualquier
                    // degradado que hubiera quedado activo — mismo criterio
                    // que setLayerCustomColor.
                    useGradientColor = false
                ).apply { pendingBitmap = decoded.bitmap }
            }
        }
    }

    fun importAsBackground(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            val lowestZ = (_uiState.value.layers.minOfOrNull { it.zIndex } ?: 0) - 1
            val newLayers = layerRepository.importAsLayers(
                listOf(uri),
                startingZIndex = lowestZ,
                // El color sigue el orden de creación (cuántas capas ya
                // existen), no el zIndex negativo del fondo — así no repite
                // el color de la primera capa cada vez que se importa un
                // fondo nuevo.
                startingColorIndex = _uiState.value.layers.size
            )
            val backgroundLayer = newLayers.firstOrNull()?.apply { parallaxFactor = 0.35f }
            if (backgroundLayer != null) {
                _uiState.value = _uiState.value.copy(
                    layers = _uiState.value.layers + backgroundLayer,
                    isImporting = false,
                    revision = _uiState.value.revision + 1
                )
                scheduleAutosave()
            } else {
                _uiState.value = _uiState.value.copy(isImporting = false)
            }
        }
    }

    /** Elimina la capa por completo. Si era la seleccionada, queda sin selección (nada de reasignar a otra capa a ciegas). */
    fun removeLayer(layerId: String) {
        val remaining = _uiState.value.layers.filterNot { it.id == layerId }
        val newSelectedId = if (_uiState.value.selectedLayerId == layerId) {
            null
        } else {
            _uiState.value.selectedLayerId
        }
        _uiState.value = _uiState.value.copy(
            layers = remaining,
            selectedLayerId = newSelectedId,
            revision = _uiState.value.revision + 1
        )
        scheduleAutosave()
    }

    fun setParallaxFactor(layerId: String, factor: Float) {
        pushUndoCheckpoint()
        replaceLayer(layerId) { it.copy(parallaxFactor = factor) }
    }

    fun toggleLayerLock(layerId: String) {
        pushUndoCheckpoint(force = true)
        replaceLayer(layerId) { it.copy(locked = !it.locked) }
    }

    /**
     * Candado independiente del de arriba: bloquea SOLO el arrastre para
     * reordenar esa capa en la columna del timeline (ver Layer.orderLocked
     * y el gesto de arrastre en TimelineView.kt). No toca el canvas — una
     * capa puede tener este candado puesto y seguir moviéndose/editándose
     * en el preview con total normalidad; son dos bloqueos a propósito
     * separados, no el mismo interruptor.
     */
    fun toggleLayerOrderLock(layerId: String) {
        pushUndoCheckpoint(force = true)
        replaceLayer(layerId) { it.copy(orderLocked = !it.orderLocked) }
    }

    /** Muestra/oculta la capa del preview, la reproducción y la exportación (no la elimina). */
    fun toggleLayerVisibility(layerId: String) {
        pushUndoCheckpoint(force = true)
        replaceLayer(layerId) { it.copy(visible = !it.visible) }
    }

    /**
     * Reordena una capa arrastrando: recibe cuántas posiciones se cruzaron
     * de un tirón (positivo = hacia atrás/abajo, negativo = hacia el
     * frente/arriba, mismo sistema de índices que usa TimelineView) y
     * aplica el resultado final en una SOLA operación atómica.
     *
     * Esto reemplaza al viejo enfoque de moveLayerUp/moveLayerDown en un
     * loop (una llamada por cada posición cruzada): cada llamada individual
     * tomaba su propio checkpoint de undo, y ese checkpoint clona TODOS
     * los keyframes de TODAS las capas del proyecto (ver captureSnapshot).
     * Arrastrar de abajo hacia el frente varias posiciones de un tirón
     * disparaba varios de esos clones completos en la misma fracción de
     * segundo — la demora/traba que se sentía sobre todo al subir. Acá se
     * toma un único checkpoint y se reasignan los zIndex de todas las
     * capas de una sola vez, sin importar cuántas filas se hayan cruzado.
     */
    fun reorderLayer(layerId: String, steps: Int) {
        if (steps == 0) return
        // Mismo orden (descendente por zIndex, frente primero) que usa
        // TimelineView para dibujar las filas — así el "steps" que ya
        // calculó el gesto de arrastre se aplica tal cual, sin traducir
        // entre sistemas de índices distintos.
        val sorted = _uiState.value.layers.sortedByDescending { it.zIndex }.toMutableList()
        val fromIdx = sorted.indexOfFirst { it.id == layerId }
        if (fromIdx < 0) return
        val toIdx = (fromIdx + steps).coerceIn(0, sorted.size - 1)
        if (toIdx == fromIdx) return

        pushUndoCheckpoint(force = true)

        val moving = sorted.removeAt(fromIdx)
        sorted.add(toIdx, moving)
        // Reasigna zIndex consecutivos según el nuevo orden: la primera
        // fila (más al frente) recibe el zIndex más alto, coherente con
        // cómo moveLayerUp/moveLayerDown ya lo manejaban por pares.
        val topZ = sorted.size - 1
        sorted.forEachIndexed { index, layer -> layer.zIndex = topZ - index }

        notifyLayersChanged()
    }

    /** Sube la capa una posición (queda por encima, se dibuja más al frente). */
    fun moveLayerUp(layerId: String) {
        val sorted = _uiState.value.layers.sortedBy { it.zIndex }
        val idx = sorted.indexOfFirst { it.id == layerId }
        if (idx in 0 until sorted.size - 1) {
            pushUndoCheckpoint(force = true)
            val current = sorted[idx]
            val next = sorted[idx + 1]
            val tmp = current.zIndex
            current.zIndex = next.zIndex
            next.zIndex = tmp
            notifyLayersChanged()
        }
    }

    /** Baja la capa una posición (queda por debajo, se dibuja más atrás). */
    fun moveLayerDown(layerId: String) {
        val sorted = _uiState.value.layers.sortedBy { it.zIndex }
        val idx = sorted.indexOfFirst { it.id == layerId }
        if (idx > 0) {
            pushUndoCheckpoint(force = true)
            val current = sorted[idx]
            val previous = sorted[idx - 1]
            val tmp = current.zIndex
            current.zIndex = previous.zIndex
            previous.zIndex = tmp
            notifyLayersChanged()
        }
    }

    /**
     * Renombra una capa. Reemplaza la antigua flecha "bajar" del panel de
     * acciones de la fila: como el reordenamiento ahora se hace arrastrando
     * la miniatura, ese botón quedó libre para una acción que antes no
     * tenía atajo rápido — renombrar la capa sin tener que ir a buscarla
     * en otro panel.
     */
    fun renameLayer(layerId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        pushUndoCheckpoint(force = true)
        replaceLayer(layerId) { it.copy(name = trimmed) }
    }

    /**
     * Cambia el color de identidad de una capa dentro de la paleta de
     * [com.yeivikas.olyzecs.ui.theme.LayerTrackColors]. Reemplaza
     * la antigua flecha "subir" del panel de acciones de la fila, por el
     * mismo motivo que [renameLayer]: el reordenamiento por arrastre dejó
     * ese espacio libre para una acción de verdad nueva.
     */
    fun setLayerColorIndex(layerId: String, colorIndex: Int) {
        pushUndoCheckpoint(force = true)
        // BUG REAL corregido: antes esto mutaba `it.colorIndex` in-place sobre
        // el MISMO objeto Layer (data class con var), lo que Compose podía no
        // detectar como "cambió de verdad" hasta que algo más (salir/volver a
        // entrar a la pantalla) forzaba una recomposición completa desde cero.
        // Reemplazar la lista con copias NUEVAS (.copy()) para la capa
        // afectada garantiza que la referencia del objeto cambie de verdad,
        // así Compose recompone la fila en el acto, en vivo.
        replaceLayer(layerId) { it.copy(colorIndex = colorIndex) }
    }

    /**
     * Aplica un color elegido a mano en la rueda de color (ver
     * ColorWheelPicker en TimelineView.kt) — a diferencia de
     * [setLayerColorIndex], que solo puede elegir entre los 10 colores
     * fijos de [com.yeivikas.olyzecs.ui.theme.LayerTrackColors],
     * acá [colorArgb] puede ser CUALQUIER color (matiz/saturación/brillo
     * elegidos libremente por el usuario en la rueda).
     */
    fun setLayerCustomColor(layerId: String, colorArgb: Int, useBlackAndWhiteMode: Boolean = false) {
        pushUndoCheckpoint(force = true)
        replaceLayer(layerId) {
            it.copy(
                customColorArgb = colorArgb,
                // Elegir un color SÓLIDO manda por encima de cualquier
                // degradado que hubiera quedado activo antes — un color y un
                // degradado son mutuamente excluyentes como modo activo (los
                // dos extremos del degradado se conservan en memoria por si
                // el usuario vuelve a prender el switch, pero dejan de
                // pintarse).
                useGradientColor = false,
                // Se guarda el modo con el que se armó ESTE color — así el
                // switch de la ventanita queda en el mismo estado la
                // próxima vez que se reabra (ver Layer.useBlackAndWhiteMode
                // para el detalle del bug real que esto corrige).
                useBlackAndWhiteMode = useBlackAndWhiteMode
            )
        }
    }

    /**
     * Aplica un degradado de dos colores (A arriba, B abajo) elegido en el
     * panel de degradado de la rueda de color — ver ColorWheelPicker /
     * LayerColorPickerDialog en TimelineView.kt.
     */
    fun setLayerGradient(layerId: String, startArgb: Int, endArgb: Int, angleDegrees: Float, isRadial: Boolean, useBlackAndWhiteMode: Boolean = false) {
        pushUndoCheckpoint(force = true)
        replaceLayer(layerId) {
            it.copy(
                customGradientStartArgb = startArgb,
                customGradientEndArgb = endArgb,
                useGradientColor = true,
                gradientAngleDegrees = angleDegrees,
                gradientIsRadial = isRadial,
                useBlackAndWhiteMode = useBlackAndWhiteMode
            )
        }
    }

    /**
     * Vuelve a dejar el color de la capa en su valor de fábrica: el color
     * dominante extraído de la imagen/medio con el que la capa se importó
     * (ver [Layer.importedDefaultColorArgb]) — sea que estuviera en color
     * sólido personalizado o en degradado.
     *
     * BUG REAL corregido acá: antes esto ponía customColorArgb = null, lo
     * que hacía caer el color en el CÍCLICO AUTOMÁTICO de la paleta fija
     * por colorIndex — un color sin ninguna relación con la imagen real de
     * la capa, que además cambiaba solo con reordenar/agregar capas. Un
     * botón "Restablecer" tiene que volver al valor ORIGINAL, no saltar a
     * uno genérico distinto cada vez. Con importedDefaultColorArgb != null
     * (el caso normal: toda capa importada lo trae desde
     * LayerRepository/ProjectStorage), el color vuelve exactamente al que
     * la imagen tenía al cargarse. Solo si esa capa no tiene ningún
     * default guardado (caso residual, no debería pasar en la práctica)
     * se cae al viejo comportamiento cíclico como último recurso.
     */
    fun resetLayerColor(layerId: String) {
        pushUndoCheckpoint(force = true)
        replaceLayer(layerId) {
            it.copy(
                customColorArgb = it.importedDefaultColorArgb,
                customGradientStartArgb = null,
                customGradientEndArgb = null,
                useGradientColor = false,
                useBlackAndWhiteMode = false
            )
        }
    }

    // ============================================================
    // "Multicolor": aplicar color/degradado a VARIAS capas marcadas de
    // un tirón (ver TimelineView.kt — showMultiColorDialog).
    //
    // BUG REAL corregido acá: antes esto NO existía como función propia —
    // TimelineView llamaba a setLayerCustomColor/setLayerGradient UNA VEZ
    // POR CADA capa marcada, en un forEach. Dos problemas reales con eso:
    //
    // 1) Cada llamada individual pintaba el degradado COMPLETO A→B dentro
    //    de cada capa por separado (todas mostrando el mismo degradado
    //    entero, cada una por su cuenta) en vez de UN SOLO degradado
    //    repartido a lo largo de las capas marcadas — que es justo lo que
    //    muestra la columna de pistas de FL Studio de escritorio (la
    //    referencia que se pidió imitar): un tramo de color cayendo a
    //    través de TODAS las pistas seguidas, no cada pista ciclando su
    //    propio arcoíris suelto.
    //
    // 2) pushUndoCheckpoint(force = true) se disparaba UNA VEZ POR CAPA
    //    marcada — con 6 capas marcadas, "Aplicar" en Multicolor apilaba
    //    6 checkpoints de undo distintos. Un solo toque de "Deshacer"
    //    después solo revertía la ÚLTIMA capa tocada, no la acción
    //    completa — nada intuitivo para el usuario, que hizo UNA sola
    //    acción ("pintar el grupo") y esperaría UN solo Deshacer para
    //    revertirla entera.
    //
    // Acá se toma UN checkpoint para el grupo entero y se reparte el
    // degradado en un solo lerp por posición dentro del grupo marcado
    // (mismo orden que ya usa TimelineView para mostrar las filas:
    // descendente por zIndex, o sea de arriba hacia abajo tal cual se ve
    // en pantalla) — así el resultado real coincide con la vista previa
    // agrupada que TimelineView ya calculaba (labelColumnColorOverrides /
    // rowBodyBrush), en vez de quedar desalineado con lo que el usuario
    // ve en el timeline.
    // ============================================================

    /** Aplica el MISMO color sólido a todas las capas de [layerIds] de un tirón, con un solo checkpoint de undo. */
    fun setLayersCustomColor(layerIds: Collection<String>, colorArgb: Int, useBlackAndWhiteMode: Boolean = false) {
        if (layerIds.isEmpty()) return
        pushUndoCheckpoint(force = true)
        replaceLayers(layerIds) {
            it.copy(customColorArgb = colorArgb, useGradientColor = false, useBlackAndWhiteMode = useBlackAndWhiteMode)
        }
    }

    /**
     * Reparte UN SOLO degradado [startArgb]→[endArgb] entre todas las
     * capas de [layerIds], en el mismo orden en que se ven en el timeline
     * (descendente por zIndex — el orden real de [layers], no el orden en
     * que el usuario las fue tocando para marcarlas). Cada capa recibe un
     * tono SÓLIDO propio, muestreado del punto que le toca dentro del
     * degradado completo — igual algoritmo que ya usa TimelineView para
     * la vista agrupada (labelColumnColorOverrides), así lo que queda
     * GUARDADO coincide exactamente con lo que el usuario ya está viendo
     * en pantalla mientras arma el color.
     *
     * Con una sola capa marcada, no hay "grupo" que repartir: esa capa se
     * queda con el degradado real de dos colores (A arriba, B abajo)
     * dentro de su propia fila, como si se hubiera usado el diálogo
     * individual — mismo comportamiento de siempre para el caso de una
     * sola capa.
     */
    fun setLayersGradient(layerIds: Collection<String>, startArgb: Int, endArgb: Int, angleDegrees: Float, isRadial: Boolean, useBlackAndWhiteMode: Boolean = false) {
        if (layerIds.isEmpty()) return
        pushUndoCheckpoint(force = true)
        val idSet = layerIds.toSet()
        if (idSet.size == 1) {
            replaceLayers(idSet) {
                it.copy(
                    customGradientStartArgb = startArgb,
                    customGradientEndArgb = endArgb,
                    useGradientColor = true,
                    gradientAngleDegrees = angleDegrees,
                    gradientIsRadial = isRadial,
                    useBlackAndWhiteMode = useBlackAndWhiteMode
                )
            }
            return
        }
        // Orden real de pantalla: mismo criterio que TimelineView
        // (sortedByDescending { zIndex }) — el grupo se lee de arriba
        // hacia abajo tal cual el usuario lo ve, no en el orden en que
        // fue tocando cada miniatura para marcarla.
        val orderedMarked = _uiState.value.layers
            .sortedByDescending { it.zIndex }
            .filter { it.id in idSet }
        val colorA = android.graphics.Color.valueOf(startArgb)
        val colorB = android.graphics.Color.valueOf(endArgb)
        fun lerpChannel(a: Float, b: Float, t: Float) = a + (b - a) * t
        fun sampleArgb(fraction: Float): Int {
            val r = lerpChannel(colorA.red(), colorB.red(), fraction)
            val g = lerpChannel(colorA.green(), colorB.green(), fraction)
            val b = lerpChannel(colorA.blue(), colorB.blue(), fraction)
            val a = lerpChannel(colorA.alpha(), colorB.alpha(), fraction)
            return android.graphics.Color.argb(
                (a * 255f).roundToLong().toInt().coerceIn(0, 255),
                (r * 255f).roundToLong().toInt().coerceIn(0, 255),
                (g * 255f).roundToLong().toInt().coerceIn(0, 255),
                (b * 255f).roundToLong().toInt().coerceIn(0, 255)
            )
        }
        val runSize = orderedMarked.size
        val sampledById = orderedMarked.mapIndexed { index, layer ->
            val fraction = (index + 0.5f) / runSize
            layer.id to sampleArgb(fraction)
        }.toMap()
        // BUG REAL corregido (el que hacía desaparecer TODAS las capas del
        // lienzo justo al aplicar el degradado a un grupo marcado con
        // Multicolor): este bloque arma su propio `.map { layer.copy(...) }`
        // a mano, en vez de pasar por el helper `replaceLayers` — que es el
        // que ya sabe trasladar `glTextureId`/`pendingBitmap` de la capa
        // vieja a la nueva (ver el comentario largo en `replaceLayer` más
        // arriba: son `@Transient var` fuera del constructor del data
        // class, así que `.copy()` los resetea a -1/null si no se
        // trasladan a mano). Acá se aplicaba `.copy()` a las 6 capas
        // marcadas EN LA MISMA actualización de estado — las 6 perdían su
        // textura de GPU al mismo tiempo, por eso desaparecían todas
        // juntas al tocar "Aplicar" en el degradado de grupo (a diferencia
        // del color/degradado de UNA sola capa, que sí pasa por
        // `replaceLayer` y por eso no tenía este problema).
        _uiState.value = _uiState.value.copy(
            layers = _uiState.value.layers.map { layer ->
                val sampled = sampledById[layer.id]
                if (sampled != null) {
                    // El degradado GUARDADO en cada capa sigue siendo el
                    // A→B completo del grupo (para que TimelineView pueda
                    // reconocer que estas capas comparten el mismo origen
                    // y las agrupe visualmente — ver labelColumnColorOverrides
                    // en TimelineView.kt), pero además queda el tono
                    // sólido ya muestreado en customColorArgb como
                    // fallback, por si en algún lugar del código todavía
                    // se lee el color sólido en vez del degradado.
                    layer.copy(
                        customGradientStartArgb = startArgb,
                        customGradientEndArgb = endArgb,
                        useGradientColor = true,
                        gradientAngleDegrees = angleDegrees,
                        gradientIsRadial = isRadial,
                        customColorArgb = sampled,
                        useBlackAndWhiteMode = useBlackAndWhiteMode
                    ).apply {
                        glTextureId = layer.glTextureId
                        pendingBitmap = layer.pendingBitmap
                    }
                } else layer
            }
        )
        notifyLayersChanged()
    }

    /** Restablece el color de fábrica (ver doc de [resetLayerColor]) de todas las capas de [layerIds] de un tirón, con un solo checkpoint de undo. Cada capa vuelve a SU PROPIO color importado, no a uno compartido. */
    fun resetLayersColor(layerIds: Collection<String>) {
        if (layerIds.isEmpty()) return
        pushUndoCheckpoint(force = true)
        replaceLayers(layerIds) {
            it.copy(
                customColorArgb = it.importedDefaultColorArgb,
                customGradientStartArgb = null,
                customGradientEndArgb = null,
                useGradientColor = false,
                useBlackAndWhiteMode = false
            )
        }
    }

    // ============================================================
    // Audio de fondo (Fase 6)
    // ============================================================

    /**
     * Importa un archivo de audio (SAF) como pista de fondo del proyecto.
     * Reemplaza cualquier audio anterior — solo puede haber uno a la vez.
     */
    fun importAudio(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImportingAudio = true)
            val appContext = context.applicationContext
            val resolver = appContext.contentResolver
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Algunos proveedores no soportan permisos persistentes; no es fatal.
                AppLogger.i("EditorViewModel", "El proveedor del audio no soporta permiso persistente (no es grave): ${e.message}")
            }

            val displayName = withContext(Dispatchers.IO) { queryDisplayName(resolver, uri) }
                ?: "audio_${System.currentTimeMillis()}"
            val durationMs = withContext(Dispatchers.IO) { projectStorage.probeAudioDurationMs(uri) }

            if (durationMs <= 0L) {
                // No se pudo leer la duración: probablemente no es un archivo de
                // audio válido o el decoder no lo soporta. Se descarta sin romper
                // el proyecto.
                AppLogger.w("EditorViewModel", "No se pudo importar el audio '$displayName' — no se le pudo leer una duración válida (¿formato no soportado?): $uri")
                _uiState.value = _uiState.value.copy(isImportingAudio = false)
                return@launch
            }

            val clip = AudioClip(
                sourceUri = uri,
                displayName = displayName,
                sourceDurationMs = durationMs
            )
            _uiState.value = _uiState.value.copy(audioClip = clip, isImportingAudio = false)
            scheduleAutosave()
        }
    }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    fun removeAudio() {
        _uiState.value = _uiState.value.copy(audioClip = null)
        scheduleAutosave()
    }

    fun setAudioVolume(volume: Float) {
        val clip = _uiState.value.audioClip ?: return
        replaceAudioClip(clip.copy(volume = volume.coerceIn(0f, 1.5f)))
    }

    fun toggleAudioMute() {
        val clip = _uiState.value.audioClip ?: return
        replaceAudioClip(clip.copy(muted = !clip.muted))
    }

    fun setAudioTrimStart(trimStartMs: Long) {
        val clip = _uiState.value.audioClip ?: return
        val clamped = trimStartMs.coerceIn(0L, (clip.sourceDurationMs - 100L).coerceAtLeast(0L))
        replaceAudioClip(clip.copy(trimStartMs = clamped))
    }

    fun setAudioLoop(loop: Boolean) {
        val clip = _uiState.value.audioClip ?: return
        replaceAudioClip(clip.copy(loop = loop))
    }

    fun setAudioFade(fadeInMs: Long, fadeOutMs: Long) {
        val clip = _uiState.value.audioClip ?: return
        replaceAudioClip(clip.copy(fadeInMs = fadeInMs.coerceAtLeast(0L), fadeOutMs = fadeOutMs.coerceAtLeast(0L)))
    }

    /**
     * Reemplaza `audioClip` por una instancia NUEVA (no muta la existente).
     * A diferencia de las capas —donde mutar in-place evita perder la
     * textura GL ya subida a GPU—, el audio no tiene ningún recurso caro
     * atado a la identidad del objeto, así que acá conviene evitar por
     * completo la mutación in-place: reemplazar la referencia es la forma
     * más simple y a prueba de dudas de garantizar que Compose vea el
     * cambio, sin depender de ningún contador de "revision" para forzarlo.
     */
    private fun replaceAudioClip(newClip: AudioClip) {
        _uiState.value = _uiState.value.copy(audioClip = newClip, revision = _uiState.value.revision + 1)
        scheduleAutosave()
    }

    // ============================================================
    // Reproducción / timeline
    // ============================================================

    /**
     * Mueve el playhead a [timeMs]. Si el destino cae dentro de la ventana
     * de "acercándose al final" (o directamente más allá del final
     * actual), primero le pide a [timelineDurationManager] que expanda la
     * línea de tiempo lo que haga falta — así arrastrar el playhead hasta
     * el borde del timeline visible SIEMPRE encuentra más espacio, en vez
     * de topar contra un límite que el usuario nunca eligió.
     */
    fun seekTo(timeMs: Long) {
        val newDurationMs = ensureTimelineCapacityFor(timeMs)
        val clamped = timeMs.coerceIn(0L, newDurationMs)
        _uiState.value = _uiState.value.copy(
            playheadMs = clamped,
            projectDurationMs = newDurationMs,
            isAtMaxDuration = timelineDurationManager.isAtMaxLimit
        )
    }

    /**
     * Frena la reproducción (si estaba corriendo) y rebobina al inicio.
     *
     * Esta app es de una sola Activity sin NavHost: el ViewModel de cada
     * proyecto se cachea en el ViewModelStore de la Activity por su
     * projectId, así que salir al listado de proyectos NO destruye el
     * ViewModel ni cancela su corrutina de reproducción — sigue tickeando
     * en segundo plano. Si se reabre el mismo proyecto, se recibe esa
     * misma instancia todavía reproduciendo desde donde quedó. Se llama a
     * esto tanto al SALIR del editor (para parar el loop en segundo plano)
     * como al ENTRAR/reabrir un proyecto (para garantizar que siempre se
     * vea desde el principio, nunca a mitad de reproducción).
     */
    fun resetPlaybackState() {
        if (_uiState.value.isPlaying || _uiState.value.playheadMs != 0L) {
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                isRecording = false,
                isCapturing = false,
                playheadMs = 0L
            )
        }
        // Cancelación EXPLÍCITA, no solo confiar en que el loop note
        // `isPlaying = false` en su próxima iteración — ver
        // [stopPlaybackLoop] y la sección 9 del brief de Fase 2 (pause
        // debe frenar el playback de forma inmediata y determinística).
        stopPlaybackLoop()
    }

    /**
     * Cancela el loop de reproducción activo (si hay uno) de forma
     * inmediata y explícita — usado en TODOS los caminos que llevan a
     * `isPlaying = false` (pausa, scrub, detener grabación, salir del
     * editor, destrucción del ViewModel), en vez de depender únicamente
     * de que la propia corrutina note el cambio de estado en su próximo
     * tick. Llamar a esto cuando no hay ningún job activo es un no-op
     * seguro (`?.cancel()`).
     */
    private fun stopPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = null
    }

    // Recuerda si la reproducción estaba corriendo ANTES de empezar a
    // arrastrar el playhead, para poder retomarla al soltar (ver
    // beginScrub/endScrub). No es parte de EditorUiState porque es un
    // detalle interno del gesto de arrastre, no algo que la UI necesite
    // observar.
    private var wasPlayingBeforeScrub = false

    /**
     * Se llama al EMPEZAR a arrastrar el playhead. Si el proyecto estaba
     * reproduciéndose, lo pausa de inmediato — si no, mientras se arrastra
     * el loop de reproducción sigue intentando avanzar el playhead por su
     * cuenta al mismo tiempo que el dedo lo mueve, y el resultado se ve
     * trabado en vez de responder limpio al gesto.
     */
    fun beginScrub() {
        wasPlayingBeforeScrub = _uiState.value.isPlaying
        if (wasPlayingBeforeScrub) {
            _uiState.value = _uiState.value.copy(isPlaying = false)
            stopPlaybackLoop()
        }
    }

    /** Se llama al SOLTAR el playhead: si estaba reproduciendo antes de arrastrar, retoma la reproducción desde la nueva posición. */
    fun endScrub() {
        if (wasPlayingBeforeScrub) {
            wasPlayingBeforeScrub = false
            _uiState.value = _uiState.value.copy(isPlaying = true)
            startPlaybackLoop()
        }
    }

    fun togglePlayback() {
        val playing = !_uiState.value.isPlaying
        _uiState.value = _uiState.value.copy(
            isPlaying = playing,
            isRecording = if (!playing) false else _uiState.value.isRecording,
            isCapturing = if (!playing) false else _uiState.value.isCapturing
        )
        if (playing) startPlaybackLoop() else stopPlaybackLoop()
    }

    fun toggleRecording() {
        val recording = !_uiState.value.isRecording
        if (recording) {
            _uiState.value = _uiState.value.copy(isRecording = true, isCapturing = false)
        } else {
            _uiState.value = _uiState.value.copy(isRecording = false, isCapturing = false, isPlaying = false)
            stopPlaybackLoop()
        }
    }

    private fun startPlaybackLoop() {
        // Cancela cualquier loop de reproducción anterior ANTES de lanzar
        // uno nuevo — la garantía real de "un único loop activo" no puede
        // depender solo de que el `while (isPlaying)` de la corrutina
        // vieja se entere tarde o temprano de que `isPlaying` pasó a
        // `false`; con esto, sin importar cuántas veces se llame a
        // `startPlaybackLoop()` seguidas (toques rápidos de Play/Pause,
        // `endScrub()` combinado con `togglePlayback()`, etc.), el Job
        // anterior queda cancelado de forma síncrona antes de que exista
        // el nuevo, así que nunca coexisten dos.
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            // Estado de freeze propio de ESTA pasada de reproducción: se
            // reinicia cada vez que se arranca a reproducir, así un freeze
            // ya "consumido" vuelve a dispararse la próxima vez que se
            // reproduce desde el principio.
            var freezeState = FreezeRuntimeState()
            var lastTickAtNanos = System.nanoTime()
            while (_uiState.value.isPlaying) {
                val state = _uiState.value
                delay(targetTickMsFor(state.projectFps))
                val nowNanos = System.nanoTime()
                val elapsedMs = ((nowNanos - lastTickAtNanos) / 1_000_000L)
                    .coerceIn(1L, MAX_TICK_MS)
                lastTickAtNanos = nowNanos
                val (next, nextFreezeState) = animationApi.step(
                    currentBaseMs = state.playheadMs,
                    tickMs = elapsedMs,
                    freezeState = freezeState,
                    baseDurationMs = state.projectDurationMs,
                    speedKeyframes = state.speedKeyframes,
                    freezeFrames = state.freezeFrames
                )
                freezeState = nextFreezeState
                // Antes de decidir si la reproducción "llegó al final", se
                // le da a TimelineDurationManager la chance de expandir la
                // línea de tiempo si el playhead entró en la ventana de
                // acercamiento al borde — así reproducir o grabar nunca se
                // corta en seco contra un límite invisible para el usuario;
                // el timeline simplemente sigue creciendo de forma
                // transparente mientras haya margen hasta el techo de 180 min.
                val newDurationMs = timelineDurationManager.growIfApproachingEnd(next)
                if (next >= newDurationMs) {
                    _uiState.value = _uiState.value.copy(
                        playheadMs = 0L,
                        isPlaying = false,
                        isRecording = false,
                        isCapturing = false,
                        projectDurationMs = newDurationMs,
                        isAtMaxDuration = timelineDurationManager.isAtMaxLimit
                    )
                    freezeState = FreezeRuntimeState()
                } else {
                    _uiState.value = _uiState.value.copy(
                        playheadMs = next,
                        projectDurationMs = newDurationMs,
                        isAtMaxDuration = timelineDurationManager.isAtMaxLimit
                    )
                }
            }
        }
    }

    // ============================================================
    // Keyframes
    // ============================================================

    fun addKeyframeToSelectedLayer(
        translateX: Float,
        translateY: Float,
        scale: Float,
        rotationDeg: Float,
        alpha: Float,
        tiltXDeg: Float = 0f,
        tiltYDeg: Float = 0f,
        focusBlur: Float = 0f,
        dollyZoom: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        easing: EasingType = EasingType.EASE_IN_OUT
    ) {
        pushUndoCheckpoint()
        val layer = currentSelectedLayer() ?: return
        layer.cameraTrack.addOrReplace(
            Keyframe(
                timeMs = _uiState.value.playheadMs,
                translateX = translateX,
                translateY = translateY,
                scale = scale,
                rotationDeg = rotationDeg,
                alpha = alpha,
                tiltXDeg = tiltXDeg,
                tiltYDeg = tiltYDeg,
                focusBlur = focusBlur,
                dollyZoom = dollyZoom,
                scaleX = scaleX,
                scaleY = scaleY,
                easing = easing
            )
        )

        if (_uiState.value.isRecording && !_uiState.value.isCapturing) {
            _uiState.value = _uiState.value.copy(isCapturing = true, isPlaying = true)
            startPlaybackLoop()
        }

        notifyLayersChanged()
    }

    /**
     * Actualiza la pose ESTÁTICA de la capa seleccionada — NUNCA crea ni
     * modifica ningún [Keyframe]. Se usa cuando el usuario mueve/ajusta una
     * capa fuera del modo Grabar (ver commitLiveFrame en EditorScreen.kt):
     * antes esto escribía un keyframe "disfrazado" en el instante 0 para
     * que la posición no se perdiera, pero seguía siendo, técnicamente, un
     * keyframe — y no debería aparecer nada en la pista de animación de
     * una capa que el usuario nunca puso a grabar. Con
     * [com.yeivikas.olyzecs.engine.camera.CameraTrack.baseFrame] la
     * posición se guarda de verdad (sobrevive a cambiar de capa, cerrar y
     * reabrir el proyecto) sin que exista NINGÚN keyframe hasta que el
     * usuario grabe uno a propósito.
     */
    fun updateBaseFrameForSelectedLayer(
        translateX: Float,
        translateY: Float,
        scale: Float,
        rotationDeg: Float,
        alpha: Float,
        tiltXDeg: Float = 0f,
        tiltYDeg: Float = 0f,
        focusBlur: Float = 0f,
        dollyZoom: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ) {
        pushUndoCheckpoint()
        val layer = currentSelectedLayer() ?: return
        layer.cameraTrack.updateBaseFrame(
            CameraFrame(
                translateX = translateX,
                translateY = translateY,
                scale = scale,
                rotationDeg = rotationDeg,
                alpha = alpha,
                tiltXDeg = tiltXDeg,
                tiltYDeg = tiltYDeg,
                focusBlur = focusBlur,
                dollyZoom = dollyZoom,
                scaleX = scaleX,
                scaleY = scaleY
            )
        )
        notifyLayersChanged()
    }

    fun removeKeyframeAtPlayhead() {
        pushUndoCheckpoint(force = true)
        val layer = currentSelectedLayer() ?: return
        layer.cameraTrack.remove(_uiState.value.playheadMs)
        notifyLayersChanged()
    }

    /**
     * Mueve un keyframe existente a un nuevo instante del timeline
     * (arrastrarlo en la pista visual). El checkpoint se fuerza porque
     * cada arrastre de un diamante ya se confirma una sola vez, al soltar
     * el dedo — no en cada frame del gesto (ver
     * [com.yeivikas.olyzecs.ui.TimelineView]).
     */
    fun retimeKeyframe(layerId: String, oldTimeMs: Long, newTimeMs: Long) {
        // Arrastrar un keyframe hasta el borde del timeline es, para el
        // usuario, el mismo gesto que arrastrar el playhead hasta ahí: se
        // le da a TimelineDurationManager la misma oportunidad de expandir
        // antes de clampear, así nunca "se pierde" el destino real del
        // arrastre contra un límite que ya no debería existir.
        val newDurationMs = ensureTimelineCapacityFor(newTimeMs)
        val clamped = newTimeMs.coerceIn(0L, newDurationMs)
        if (clamped == oldTimeMs) return
        val layer = _uiState.value.layers.find { it.id == layerId } ?: return
        val existing = layer.cameraTrack.keyframes.find { it.timeMs == oldTimeMs } ?: return

        pushUndoCheckpoint(force = true)
        layer.cameraTrack.remove(oldTimeMs)
        layer.cameraTrack.addOrReplace(existing.copy(timeMs = clamped))
        notifyLayersChanged()
    }

    /** Actualiza el look cinematográfico (grading, viñeta, grano, glow) de UNA capa específica. */
    fun updateLookSettings(layerId: String, look: LookSettings) {
        pushUndoCheckpoint()
        replaceLayer(layerId) { it.copy(lookSettings = look) }
    }

    fun currentSelectedLayer(): Layer? =
        _uiState.value.layers.find { it.id == _uiState.value.selectedLayerId }

    /**
     * Encuadre interpolado de [layer] en el instante [timeMs] — delega en
     * [com.yeivikas.olyzecs.engine.camera.CameraTrack.frameAt]. Etapa 5 había
     * dejado esto como caso límite sin resolver: `EditorScreen` llamaba
     * `layer.cameraTrack.frameAt(...)` directo, para reposicionar overlays
     * de selección y sincronizar sliders durante gestos táctiles. Es la
     * MISMA cuenta con el MISMO costo (nada de coroutines ni StateFlow de
     * por medio, solo una llamada síncrona) — lo único que cambia es que
     * la UI ya no necesita saber que `Layer` tiene un `cameraTrack`, ni
     * que `CameraTrack` tiene un método `frameAt`.
     */
    fun frameAt(layer: Layer, timeMs: Long): CameraFrame = layer.cameraTrack.frameAt(timeMs)

    // ============================================================
    // Exportación
    // ============================================================

    fun setExportQuality(quality: ExportQuality) {
        _uiState.value = _uiState.value.copy(exportQuality = quality)
    }

    fun setExportAspect(aspect: AspectRatioPreset) {
        _uiState.value = _uiState.value.copy(exportAspect = aspect)
    }

    // ============================================================
    // Velocidad variable (speed ramping) y freeze frame
    // ============================================================
    // Ambas cosas operan sobre el mismo eje de tiempo BASE que los
    // keyframes de cámara (0..projectDurationMs) — no reordenan ni
    // comprimen ese eje visualmente. Lo que cambian es qué tan rápido
    // avanza ese tiempo durante la reproducción/exportación real, y por
    // lo tanto cuánto dura el video final. La lógica real vive en
    // engine.animation.SpeedRampEngine — acá abajo se accede a través de
    // AnimationApi (Fase Animation→EliNer), no directo.

    /**
     * Duración real del video final tras aplicar rampas de velocidad y freezes.
     *
     * FASE 2 — auditoría de duración/FPS (hallazgo confirmado, brief
     * sección 11): ANTES esta cuenta usaba `fps = 30` fijo sin importar
     * el FPS real del proyecto (`state.projectFps`). `computeOutputDurationMs`
     * construye el mapeo de tiempo tick por tick (`1000/fps` por tick,
     * ver SpeedRamp.kt), así que un proyecto a 24/60/90/120fps CON al
     * menos una rampa de velocidad o un freeze frame activo obtenía un
     * resultado distinto al que realmente se iba a exportar — la UI
     * mostraba una duración, el archivo final tenía otra. Corregido
     * usando el FPS real del proyecto, la misma fuente que ya usa
     * `exportVideo()` para el resto de los parámetros de exportación.
     */
    fun currentOutputDurationMs(): Long {
        val state = _uiState.value
        return animationApi.computeOutputDurationMs(
            state.projectDurationMs, state.speedKeyframes, state.freezeFrames, fps = state.projectFps
        )
    }

    /**
     * Ancho x alto en px del video final, según la calidad y el aspecto
     * elegidos ahora mismo. Antes esta cuenta (`computeExportDimensions`)
     * se llamaba directo desde `ExportQualityPanel` en la UI, solo para
     * mostrar la etiqueta "1080×1920px" al lado de los chips de calidad —
     * un cálculo del motor ejecutándose dentro de un composable. Ahora la
     * UI solo pide el resultado ya calculado.
     */
    fun currentExportDimensions(): Pair<Int, Int> {
        val state = _uiState.value
        return computeExportDimensions(state.exportQuality, state.exportAspect)
    }

    /**
     * Velocidad de reproducción efectiva en el instante del playhead
     * ahora mismo (1f = normal). Antes se calculaba directo en
     * `TimeRampPanel` (`SpeedRampEngine.speedAt(...)`) para precargar el
     * campo de "velocidad" con el valor que ya está aplicado en ese punto
     * del proyecto — mismo problema que [currentExportDimensions]: lógica
     * de motor viviendo en la UI.
     */
    fun speedAtPlayhead(): Float {
        val state = _uiState.value
        return if (state.speedKeyframes.isEmpty()) 1f
        else animationApi.speedAt(state.speedKeyframes, state.playheadMs)
    }

    fun addOrReplaceSpeedKeyframe(speed: Float) {
        val state = _uiState.value
        val clamped = speed.coerceIn(0.1f, 4f)
        val updated = state.speedKeyframes
            .filterNot { it.timeMs == state.playheadMs }
            .plus(SpeedKeyframe(timeMs = state.playheadMs, speed = clamped))
            .sortedBy { it.timeMs }
        _uiState.value = state.copy(speedKeyframes = updated)
        scheduleAutosave()
    }

    fun removeSpeedKeyframeAtPlayhead() {
        val state = _uiState.value
        _uiState.value = state.copy(
            speedKeyframes = state.speedKeyframes.filterNot { it.timeMs == state.playheadMs }
        )
        scheduleAutosave()
    }

    /** Congela el timeline en el instante actual del playhead durante [holdMs] de tiempo real. */
    fun addFreezeFrameAtPlayhead(holdMs: Long) {
        val state = _uiState.value
        val clamped = holdMs.coerceIn(200L, 8000L)
        // Reemplaza cualquier freeze ya existente muy cerca del mismo punto
        // (dentro de 50ms) en vez de amontonar duplicados prácticamente
        // superpuestos.
        val updated = state.freezeFrames
            .filterNot { kotlin.math.abs(it.atMs - state.playheadMs) < 50L }
            .plus(FreezeFrame(atMs = state.playheadMs, holdMs = clamped))
            .sortedBy { it.atMs }
        _uiState.value = state.copy(freezeFrames = updated)
        scheduleAutosave()
    }

    fun removeFreezeFrame(id: String) {
        val state = _uiState.value
        _uiState.value = state.copy(freezeFrames = state.freezeFrames.filterNot { it.id == id })
        scheduleAutosave()
    }

    fun exportVideo(context: Context, requestedFileName: String) {
        if (_uiState.value.layers.isEmpty()) return
        if (_uiState.value.exportProgress is ExportProgress.InProgress) return

        val appContext = context.applicationContext
        val state = _uiState.value

        // Antes esto quedaba en null hasta que exporter.export(...) llamaba a
        // onProgress por primera vez — y como el procesamiento de audio corre
        // ANTES del loop de video (y antes de cualquier onProgress), la
        // ventana de "Exportando video..." tardaba casi un minuto en
        // aparecer con audios largos, mostrando en cambio el selector de
        // calidad como si no hubiera pasado nada al tocar "Exportar". Se
        // marca "en curso" acá mismo, de forma síncrona, antes de lanzar la
        // corrutina, para que la UI cambie de inmediato.
        _uiState.value = _uiState.value.copy(exportProgress = ExportProgress.InProgress(0f))

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = File(appContext.getExternalFilesDir(null), "exports").apply { mkdirs() }
            val outputFile = uniqueOutputFile(outputDir, sanitizeFileName(requestedFileName))

            val (widthPx, heightPx) = computeExportDimensions(state.exportQuality, state.exportAspect)
            // FASE 2 — auditoría de duración/FPS (hallazgo confirmado, más
            // grave que un simple desacople de UI): este `outputDurationMs`
            // se usa más abajo como `ExportSettings.durationMs`, que a su
            // vez usa `VideoExporter` para construir la pista de AUDIO
            // (`AudioProcessor.buildEncodedTrackForProject(..., settings.durationMs, ...)`
            // — ver VideoExporter.kt). El conteo real de FRAMES de video
            // (`buildTimeMapping`, en el propio VideoExporter) SIEMPRE usó
            // `settings.fps` (= `state.projectFps`, correcto). Con
            // `fps = 30` fijo ACÁ, en cualquier proyecto con FPS distinto
            // de 30 Y al menos una rampa de velocidad o un freeze frame
            // activo, la pista de audio se construía a una duración
            // DISTINTA de la que realmente terminaba durando la pista de
            // video — un desincronismo audio/video real en el archivo
            // .mp4 final, no solo un número mal mostrado en la UI.
            // Corregido usando el mismo `state.projectFps` que ya usa el
            // resto de `ExportSettings` más abajo.
            val outputDurationMs = animationApi.computeOutputDurationMs(
                state.projectDurationMs, state.speedKeyframes, state.freezeFrames, fps = state.projectFps
            )
            // El bitrate base de cada ExportQuality está calibrado para
            // 30fps; a más cuadros por segundo, el mismo presupuesto de
            // datos se reparte entre más frames y la calidad por frame cae.
            // Se escala proporcionalmente (con piso en 1x) para que 60/90/
            // 120fps mantengan una nitidez comparable a 30fps, no una
            // versión más liviana del mismo video.
            val fpsScaledBitRate = (state.exportQuality.bitRate * (state.projectFps / 30f))
                .toInt()
                .coerceAtLeast(state.exportQuality.bitRate)
            val settings = ExportSettings(
                widthPx = widthPx,
                heightPx = heightPx,
                fps = state.projectFps,
                bitRate = fpsScaledBitRate,
                durationMs = outputDurationMs
            )
            val exportApi = ExportApiImpl(appContext, this@EditorViewModel)
            exportApi.export(outputFile, settings).collect { progress ->
                // AUDITORÍA — segunda carrera encontrada en revisión: un
                // `InProgress` puede quedar "en tránsito" por el canal del
                // Flow justo antes de que el usuario cancele, y llegar
                // ACÁ recién después de que `cancelExport()` (más abajo)
                // ya puso el estado en `Cancelled` de forma optimista —
                // pisándolo de nuevo con un progreso viejo y dejando la
                // UI mostrando "Exportando…" un instante después de haber
                // confirmado la cancelación. Nunca se retrocede desde un
                // estado ya terminal (`Cancelled`) a uno intermedio: una
                // vez cancelado, se ignora cualquier emisión tardía hasta
                // que el usuario mismo lo cierre (`clearExportState`).
                if (_uiState.value.exportProgress !is ExportProgress.Cancelled) {
                    _uiState.value = _uiState.value.copy(exportProgress = progress)
                }
            }
        }.also { exportJob = it }
    }

    /**
     * Cancela una exportación en curso a pedido explícito del usuario (ver
     * el botón "Cancelar" del [ExportDialog] mientras está en progreso).
     *
     * DISEÑO — auditoría: el estado de la UI se actualiza a
     * [ExportProgress.Cancelled] ACÁ MISMO, de inmediato, en vez de
     * esperar a que la emisión de `ExportProgress.Cancelled` que dispara
     * `VideoExporter` (ver ese archivo) complete su viaje de ida y vuelta
     * por el `Flow` de una corrutina que, para cuando esto se lee, ya está
     * en proceso de cancelarse. Ese viaje de vuelta puede perderse por una
     * carrera real entre la cancelación del `Job` y la entrega del último
     * valor del `Flow` — no es un caso hipotético, es el comportamiento
     * documentado de `trySend` contra un colector cuyo Job ya está
     * cancelado. Actualizar acá, de forma optimista, es lo que garantiza
     * que el usuario SIEMPRE ve una respuesta inmediata a haber tocado
     * "Cancelar", nunca una barra de progreso congelada esperando una
     * señal que quizás nunca termine de llegar. La limpieza real (borrar
     * el archivo parcial, liberar el codec/muxer/EGL) sigue corriendo en
     * segundo plano de todos modos — no depende de que este mensaje llegue.
     */
    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.value = _uiState.value.copy(exportProgress = ExportProgress.Cancelled)
    }

    /** Saca del nombre elegido cualquier carácter no válido para un nombre de archivo. */
    private fun sanitizeFileName(name: String): String {
        val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return cleaned.ifBlank { "olyze_${System.currentTimeMillis()}" }
    }

    /** Si ya existe un archivo con ese nombre, le agrega un sufijo numérico en vez de pisarlo. */
    private fun uniqueOutputFile(dir: File, baseName: String): File {
        var candidate = File(dir, "$baseName.mp4")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($counter).mp4")
            counter++
        }
        return candidate
    }

    fun clearExportState() {
        exportJob = null
        _uiState.value = _uiState.value.copy(exportProgress = null)
    }

    // ============================================================
    // ActiveProjectReader / ActiveProjectMutator (EliNer API — Fase 1.4)
    // ============================================================
    // Puerta de acceso para EliNer API: NO es un segundo owner del
    // estado. Cada lectura consulta _uiState directo (sin copia); cada
    // escritura delega a las mismas funciones que ya usa la UI (mismo
    // undo checkpoint, mismo autosave, mismas validaciones) — salvo las
    // 3 de Camera con layerId/timeMs explícitos, que no tenían un
    // equivalente existente con esa firma (ver Fase 1.3, corrección al
    // inicio del informe) y se agregan acá como funciones mínimas nuevas,
    // siguiendo el mismo patrón (pushUndoCheckpoint + notifyLayersChanged)
    // que ya usan addKeyframeToSelectedLayer/updateBaseFrameForSelectedLayer.

    // --- ActiveProjectReader ---

    override fun getLayers(): List<Layer> = _uiState.value.layers

    override fun getLayer(layerId: String): Layer? =
        _uiState.value.layers.find { it.id == layerId }

    override fun getAudioClip(): AudioClip? = _uiState.value.audioClip

    override fun getSpeedKeyframes(): List<SpeedKeyframe> = _uiState.value.speedKeyframes

    override fun getFreezeFrames(): List<FreezeFrame> = _uiState.value.freezeFrames

    override fun getBaseDurationMs(): Long = _uiState.value.projectDurationMs

    // --- ActiveProjectMutator: Layer ---

    /**
     * Anexa capas ya decodificadas (por `LayerRepository`, vía
     * `LayerApiImpl.createLayers`) al proyecto activo. Misma lógica que
     * usa [importImages] tras `layerRepository.importAsLayers` — sin
     * auto-seleccionar, con autosave — pero sin volver a llamar al
     * repository (esa decodificación ya la hizo el llamador).
     */
    override suspend fun addLayers(layers: List<Layer>) {
        if (layers.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            layers = _uiState.value.layers + layers,
            revision = _uiState.value.revision + 1
        )
        scheduleAutosave()
    }

    override suspend fun setLayerVisible(layerId: String, visible: Boolean) {
        val current = getLayer(layerId) ?: return
        if (current.visible != visible) toggleLayerVisibility(layerId)
    }

    override suspend fun setLayerLocked(layerId: String, locked: Boolean) {
        val current = getLayer(layerId) ?: return
        if (current.locked != locked) toggleLayerLock(layerId)
    }

    override suspend fun setLayerOrderLocked(layerId: String, orderLocked: Boolean) {
        val current = getLayer(layerId) ?: return
        if (current.orderLocked != orderLocked) toggleLayerOrderLock(layerId)
    }

    /**
     * `LayerApi`/esta interfaz trabajan con un zIndex ABSOLUTO objetivo;
     * `reorderLayer` (función existente, usada por el gesto de arrastre
     * del timeline) espera un delta relativo ("steps"). Conversión: como
     * reorderLayer siempre deja los zIndex consecutivos 0..N-1 tras cada
     * llamada (ver su propio comentario), el delta que produce el mismo
     * resultado final es simplemente `zIndex actual - zIndex deseado`.
     */
    override suspend fun setLayerZIndex(layerId: String, newZIndex: Int) {
        val current = getLayer(layerId) ?: return
        reorderLayer(layerId, current.zIndex - newZIndex)
    }

    override suspend fun setLayerLookSettings(layerId: String, look: LookSettings) {
        updateLookSettings(layerId, look)
    }

    override suspend fun deleteLayer(layerId: String) {
        removeLayer(layerId)
    }

    // --- ActiveProjectMutator: Camera (funciones nuevas y mínimas —
    // no existía un equivalente con layerId/timeMs explícitos; ver nota
    // de cabecera de esta sección) ---

    override suspend fun setCameraKeyframe(layerId: String, keyframe: Keyframe) {
        val layer = getLayer(layerId) ?: return
        pushUndoCheckpoint()
        layer.cameraTrack.addOrReplace(keyframe)
        notifyLayersChanged()
    }

    override suspend fun removeCameraKeyframe(layerId: String, timeMs: Long) {
        val layer = getLayer(layerId) ?: return
        pushUndoCheckpoint(force = true)
        layer.cameraTrack.remove(timeMs)
        notifyLayersChanged()
    }

    override suspend fun setCameraBaseFrame(layerId: String, frame: CameraFrame) {
        val layer = getLayer(layerId) ?: return
        pushUndoCheckpoint()
        layer.cameraTrack.updateBaseFrame(frame)
        notifyLayersChanged()
    }

    // --- ActiveProjectMutator: Audio (delega 1:1 a funciones existentes;
    // nombres "apply*" — ver nota de implementación en ActiveProjectMutator.kt) ---

    override suspend fun applyAudioVolume(volume: Float) {
        setAudioVolume(volume)
    }

    override suspend fun setAudioMuted(muted: Boolean) {
        val current = getAudioClip() ?: return
        if (current.muted != muted) toggleAudioMute()
    }

    override suspend fun applyAudioTrimStart(trimStartMs: Long) {
        setAudioTrimStart(trimStartMs)
    }

    override suspend fun applyAudioLoop(loop: Boolean) {
        setAudioLoop(loop)
    }

    override suspend fun applyAudioFade(fadeInMs: Long, fadeOutMs: Long) {
        setAudioFade(fadeInMs, fadeOutMs)
    }

    override suspend fun clearAudioClip() {
        removeAudio()
    }

    override suspend fun setAudioClipDirect(clip: AudioClip) {
        _uiState.value = _uiState.value.copy(audioClip = clip, isImportingAudio = false)
        scheduleAutosave()
    }

    override fun previewPlayFrom(context: Context, projectTimeMs: Long) {
        val clip = getAudioClip() ?: return
        audioPreviewPlayer(context).playFrom(clip, projectTimeMs)
    }

    override fun previewPause() {
        audioPreviewPlayer?.pause()
    }

    override fun previewSeekTo(context: Context, projectTimeMs: Long) {
        val clip = getAudioClip() ?: return
        audioPreviewPlayer(context).seekToProjectTime(clip, projectTimeMs)
    }

    override fun isAtMaxDurationLimit(): Boolean = _uiState.value.isAtMaxDuration

    /** Nombre distinto a propósito de "getTimelineEvents": ese nombre choca a nivel de JVM con el getter sintético de la propiedad `timelineEvents` (línea ~317, ya existente), ver hallazgo real de Fase 1.4 (build #250). */
    override fun timelineEventsFlow(): SharedFlow<TimelineEvent> = timelineDurationManager.events

    /** Mismo patrón de 2 pasos que ya usan los 3 puntos de llamada existentes (ver EditorViewModel.kt, líneas ~1637/1524/1771). */
    override fun growTimelineIfApproachingEnd(playheadMs: Long): Long {
        val newDurationMs = timelineDurationManager.growIfApproachingEnd(playheadMs)
        _uiState.value = _uiState.value.copy(
            projectDurationMs = newDurationMs,
            isAtMaxDuration = timelineDurationManager.isAtMaxLimit
        )
        return newDurationMs
    }

    override fun ensureTimelineCapacityFor(targetMs: Long): Long {
        val newDurationMs = timelineDurationManager.ensureCapacityFor(targetMs)
        _uiState.value = _uiState.value.copy(
            projectDurationMs = newDurationMs,
            isAtMaxDuration = timelineDurationManager.isAtMaxLimit
        )
        return newDurationMs
    }
}
