package com.yeivikas.olyzecs.ui

import android.content.Intent
import android.graphics.Bitmap
import com.yeivikas.olyzecs.api.scene.LayerApi
import com.yeivikas.olyzecs.api.camera.CameraApi
import com.yeivikas.olyzecs.api.audio.AudioApi
import com.yeivikas.olyzecs.engine.audio.gridStepMs
import com.yeivikas.olyzecs.engine.audio.label
import com.yeivikas.olyzecs.engine.audio.mergeCandidateAfter
import com.yeivikas.olyzecs.engine.audio.track
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
// NOTA: el proyecto solo trae `material-icons-core` (no `-extended`), y ese
// artefacto solo incluye el set completo de iconos en el estilo "Filled" —
// los otros estilos (Outlined, Rounded, etc.) traen apenas un puñado. Por
// eso el icono de ayuda usa `Icons.Filled.Info`, no `Icons.Outlined.Help`
// (ese no compila sin la dependencia extendida).
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import com.yeivikas.olyzecs.ui.theme.ChromaKeyGreen
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import com.yeivikas.olyzecs.ui.theme.effectiveLayerColorStrong
import com.yeivikas.olyzecs.ui.theme.layerTrackColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.animation.EasingType
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.api.render.RenderApi
import com.yeivikas.olyzecs.api.render.RenderApiImpl
import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.viewmodel.EditorMessage
import com.yeivikas.olyzecs.viewmodel.EditorViewModel
import com.yeivikas.olyzecs.viewmodel.SaveState
import com.yeivikas.olyzecs.engine.timeline.TimelineEvent
import android.widget.Toast
import com.yeivikas.olyzecs.data.ColorExtraction
import com.yeivikas.olyzecs.engine.distortion.DistortionBrush
import com.yeivikas.olyzecs.engine.distortion.DistortionField
import com.yeivikas.olyzecs.engine.distortion.DistortionFreezeMask
import com.yeivikas.olyzecs.engine.distortion.DistortionToolType
import com.yeivikas.olyzecs.engine.distortion.distortionBrushRadiusUv
import com.yeivikas.olyzecs.engine.distortion.StretchAxis
import com.yeivikas.olyzecs.engine.render.PerspectiveCameraMath
import com.yeivikas.olyzecs.engine.render.QuadHomography
import com.yeivikas.olyzecs.ui.editview.EDIT_CHECKER_CELL_DP
import com.yeivikas.olyzecs.ui.editview.EditBackgroundButton
import com.yeivikas.olyzecs.ui.effects.EffectSourceLoader
import com.yeivikas.olyzecs.ui.effects.EffectSources
import com.yeivikas.olyzecs.ui.effects.EffectsPreviewSizing
import com.yeivikas.olyzecs.ui.effects.effectsDisplayDecodeLimit
import com.yeivikas.olyzecs.ui.effects.logEffectDecodeFailure
import com.yeivikas.olyzecs.ui.effects.rememberProgressiveRenderScheduler
import com.yeivikas.olyzecs.ui.editview.EditViewFit
import com.yeivikas.olyzecs.ui.editview.EditViewportBackdrop
import com.yeivikas.olyzecs.ui.editview.EditViewSession
import com.yeivikas.olyzecs.ui.editview.rememberEditBackgroundState
import com.yeivikas.olyzecs.ui.editview.toBackdrop
import com.yeivikas.olyzecs.data.ImageDecoding
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.atan2

/**
 * Puente liviano entre el gesto de arrastre/pellizco del canvas
 * principal (definido acá arriba, en el cuerpo de [EditorScreen]) y el
 * estado de rotación de la ventana flotante "Básico"
 * ([Basico3DFloatingWindow], varios composables más abajo en el árbol).
 *
 * En vez de mover TODO el estado de edición 3D (rotaciones,
 * profundidad, bisel, bitmaps en memoria, debounce de guardado) hacia
 * arriba y rehacer la firma de [Basico3DFloatingWindow] para pasarlo de
 * nuevo hacia abajo, [Basico3DFloatingWindow] simplemente se "registra"
 * acá con dos callbacks mientras está en pantalla (ver su
 * DisposableEffect) y se da de baja (todo a null/false) al salir de
 * la pestaña "Básico" o cambiar de capa. El canvas solo necesita saber
 * SI hay alguien escuchando ([active], para decidir si dibuja el
 * marco normal de "mover/escalar/rotar" o entra en modo orbital sin
 * marco) y, si lo hay, delegarle los grados de arrastre/pellizco —
 * quién los usa y cómo (currentParams, debounce de guardado, etc.) es
 * un detalle que [Basico3DFloatingWindow] resuelve por su cuenta.
 */
internal class Extrude3DGestureBridge {
    var active by mutableStateOf(false)

    /** Arrastre de 1+ dedos: gira el cuerpo 3D (izq/der → Y, arriba/abajo → X), como orbitar una cámara. */
    var onOrbitDrag: ((dxDeg: Float, dyDeg: Float) -> Unit)? = null

    /** Giro de 2 dedos (el mismo gesto que rotaría la capa en 2D): acá gira el eje Z del cuerpo 3D. */
    var onTwistDrag: ((dzDeg: Float) -> Unit)? = null
}

/**
 * Mismo patrón y mismo motivo que [Extrude3DGestureBridge] (ver su
 * comentario grande arriba), ahora para la categoría "Distorsión" de la
 * pestaña "Efectos" ([DistortionPanel]): mientras está activo, el gesto
 * de UN dedo sobre el canvas deja de mover/escalar la capa y pasa a
 * pintar un trazo de la herramienta elegida — [onStrokeStart]/
 * [onStrokeMove]/[onStrokeEnd] reciben la posición ya convertida a
 * coordenadas UV de la capa (0..1, ver [screenPointToLayerUv]), no
 * píxeles de pantalla, porque el motor de deformación
 * ([com.yeivikas.olyzecs.engine.distortion.DistortionField]) trabaja
 * enteramente en UV — resolución-independiente entre la vista previa
 * chica y el bitmap final. Con 2 dedos, en cambio, el gesto SÍ sigue
 * moviendo/escalando/rotando la capa con total libertad (pellizco +
 * giro, igual que el resto de los modos) — solo 1 dedo pinta.
 */
internal class DistortionGestureBridge {
    var active by mutableStateOf(false)
    var onStrokeStart: ((Offset) -> Unit)? = null
    var onStrokeMove: ((Offset) -> Unit)? = null
    var onStrokeEnd: (() -> Unit)? = null

    // --- Deshacer/rehacer de Distorsión, subidos hasta acá para que la
    // barra superior (el mismo ↩/↪ que ya existía para deshacer/rehacer
    // "de proyecto" — transform/keyframes/orden/visibilidad, ver
    // `viewModel.undo()`/`redo()`) pueda reusarse también para esto, en
    // vez de duplicar los botones abajo del todo en el propio
    // DistortionPanel. A PEDIDO DEL USUARIO: tener DOS pares de flechas
    // de deshacer/rehacer en pantalla a la vez (una arriba siempre
    // visible, otra propia de Distorsión más abajo) es justo el tipo de
    // affordance duplicada/confusa que no debería pasar un review
    // profesional — con esto queda un solo control, que cambia de
    // "objetivo" según qué esté activo.
    //
    // Mismo patrón que `onStrokeStart`/`onStrokeMove`/`onStrokeEnd`
    // arriba: DistortionPanel es dueño real del historial (su propio
    // `undoStack`/`redoStack` de [DistortionField], ver ese archivo),
    // acá solo se publican los callbacks + el estado de habilitado para
    // que la barra superior los invoque sin conocer nada de mallas de
    // distorsión.
    var onUndo: (() -> Unit)? = null
    var onRedo: (() -> Unit)? = null
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    // --- Overlay visual de "congelar zona" (bloque `distortionBridge.
    // freezeModeActive` dentro del Box principal del canvas, en
    // EditorScreen) — DistortionPanel es dueño real de la máscara (vive
    // en su propio `remember`), acá solo se publica una REFERENCIA + un
    // contador de versión para que el overlay sepa cuándo volver a
    // leerla. La máscara se muta in-place (no es un MutableState en sí
    // misma, ver DistortionFreezeMask), así que sin el contador de
    // versión el overlay nunca se enteraría de un trazo nuevo.
    var freezeMask by mutableStateOf<DistortionFreezeMask?>(null)
    var freezeMaskVersion by mutableStateOf(0)
    var freezeModeActive by mutableStateOf(false)

    // --- "Eliminar" desde la ventana flotante, A PEDIDO EXPLÍCITO DEL
    // USUARIO cuando "Distorsión" pasó a tener ventana propia (ver
    // [DistortionFloatingWindow]). Mismo criterio EXACTO que
    // `onUndo`/`onRedo` de arriba: `resetAll()` es una función LOCAL
    // dentro de [DistortionPanel] (dueño real del historial/malla), y el
    // botón "Eliminar" vive en el chrome de [FloatingToolWindow], por
    // fuera de ese composable — no hay forma de llamar una función local
    // de otro composable directamente, así que se publica acá, igual que
    // ya se hace con deshacer/rehacer.
    var onResetRequested: (() -> Unit)? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBackToProjects: () -> Unit,
    onImportClick: () -> Unit,
    onImportBackgroundClick: () -> Unit,
    onReplaceImageClick: (String) -> Unit,
    // Abre el selector de audio; [startMs] es el punto del timeline donde
    // arrancará el clip cargado (cursor, o el punto tocado con "+Clip") y
    // [targetTrackId] el carril que lo recibe (`null` = capa de audio nueva).
    onImportAudioClick: (startMs: Long, targetTrackId: String?) -> Unit,
    /** Abre el selector de archivos para reemplazar el audio de un clip existente (botón de la cabecera de la ventana ampliada). */
    onReplaceAudioSourceClick: (clipId: String) -> Unit = {},
    // Elegir una foto para una casilla de elenco/personajes (0..3) del
    // panel "Información del proyecto" — mismo patrón que
    // onReplaceImageClick, pero con índice de casilla en vez de layerId.
    onPickCastPhotoClick: (Int) -> Unit = {},
    // FASE 4.2-R5 (Parte R5.7 del prompt maestro R5 — "API wiring real"):
    // dependencias explícitas hacia EliNer API para los dominios ya
    // migrados desde acá (ver R5.1/R5.2/R5.3 en
    // docs/fases/FASE_4_2_INTEGRITY_ELINER_BOUNDARY.md). Quedan
    // nullable con default `null` a propósito — NO por indecisión, sino
    // para que ningún llamador existente (tests, previews de Compose,
    // cualquier otro composable que arme un `EditorScreen` sin conocer
    // todavía estas APIs) se rompa por un parámetro nuevo obligatorio.
    // Si vienen `null`, cada call site migrado cae de vuelta al camino
    // directo a `viewModel` que ya existía antes de esta fase — nunca un
    // camino roto o silenciosamente no-op.
    layerApi: LayerApi? = null,
    cameraApi: CameraApi? = null,
    audioApi: AudioApi? = null
) {
    val state by viewModel.uiState.collectAsState()
    // Estado de preparación del audio de cada clip (preparando / reintentando / falló): llega del trabajo de fondo, no de ediciones.
    val audioSourceStates by viewModel.audioSourceStates.collectAsState()
    val context = LocalContext.current
    // Usado únicamente para lanzar las llamadas `suspend` de EliNer API
    // (LayerApi/CameraApi/AudioApi) desde callbacks de Compose que no son
    // `suspend` — mismo patrón estándar de Compose que ya se usa en el
    // resto de este archivo (`rememberCoroutineScope()`), acá centralizado
    // una sola vez arriba de todo porque hace falta desde varios call
    // sites distintos del árbol de este composable.
    val eliNerScope = rememberCoroutineScope()

    // Aviso elegante y puntual cuando la línea de tiempo deja de poder
    // expandirse sola (llegó a las 3 horas) — se dispara UNA vez por cada
    // vez que se entra en ese estado (ver TimelineDurationManager), nunca
    // en bucle mientras el playhead siga pegado al final.
    LaunchedEffect(viewModel) {
        viewModel.timelineEvents.collect { event ->
            when (event) {
                TimelineEvent.MaxDurationReached -> {
                    Toast.makeText(
                        context,
                        "Llegaste a la duración máxima del proyecto (3 horas)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Avisos puntuales del editor: algo que el usuario inició y no se pudo completar no debe fallar en silencio.
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is EditorMessage.AudioImportFailed -> Toast.makeText(
                    context,
                    context.getString(R.string.audio_import_failed, message.displayName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    // Papelera de capas — ver LayerTrashScreen.kt.
    var showLayerTrashDialog by remember { mutableStateOf(false) }
    // Ventana que abre el "+" debajo del master en el timeline — todavía
    // vacía a propósito, es el punto de entrada para agregar pistas nuevas
    // al playlist una vez que se defina qué tipos va a soportar.
    var showAddTrackDialog by remember { mutableStateOf(false) }
    // A PEDIDO EXPLÍCITO DEL USUARIO: los carriles de audio son
    // seleccionables en el timeline (ver AudioTrackRow), igual que una capa
    // — pero el audio NUNCA es un `Layer` (ver el comentario grande en
    // `AudioTrack.trackOrder`), así que su selección no vive en
    // `state.selectedLayerId` sino acá, como un id de UI aparte (`null` =
    // ningún carril), mutuamente excluyente con la selección de capas
    // (seleccionar una capa lo apaga, y viceversa — ver `selectLayerAndSync`
    // y los `viewModel.clearSelection()` de más abajo).
    var selectedAudioTrackId by remember { mutableStateOf<String?>(null) }
    // Clip de audio elegido DENTRO del carril (selección por clip: solo el
    // elegido muestra borde y, con loop, las líneas para estirarlo). Se limpia
    // al tocar la fila o el carril vacío (ver `onSelectAudioTrack`).
    var selectedAudioClipId by remember { mutableStateOf<String?>(null) }
    var layerPendingDelete by remember { mutableStateOf<Layer?>(null) }
    // Mismo patrón exacto que `layerPendingDelete` de arriba, para un
    // carril de audio (guarda su id) — ver el AlertDialog correspondiente
    // más abajo (junto al de `layerPendingDelete`) y el KDoc de
    // `onRemoveAudioRequest` sobre el bug real que esto corrige.
    var audioTrackPendingDelete by remember { mutableStateOf<String?>(null) }
    // Clip de audio (por id) a la espera de confirmar su eliminación desde el menú
    // del clip. Mismo patrón que `layerPendingDelete`/`audioTrackPendingDelete`:
    // el menú solo lo marca y el borrado real ocurre al confirmar en el diálogo.
    var audioClipPendingDelete by remember { mutableStateOf<String?>(null) }
    // Mini-ventana que abre la manija "restablecer" (arriba, centro) del
    // marco de edición: en vez de resetear todo de una — antes no había
    // forma de, por ejemplo, enderezar el ángulo sin perder un ancho
    // estirado a propósito — deja elegir EXACTAMENTE qué se restablece.
    var showRestoreOptionsMenu by remember { mutableStateOf(false) }
    // --- Reordenar manijas del marco de edición (manija nueva "lateral
    // izquierda, medio", ver [HandlePosition]/[LayerHandleRole]) ---
    // `handleOrderGlobal`: orden que usa cualquier capa sin un orden
    // propio guardado (alcance "Todos"). `handleOrderPerLayer`: overrides
    // por-capa (alcance "Solo aquí"), tienen prioridad sobre el global.
    var handleOrderGlobal by remember { mutableStateOf(DEFAULT_HANDLE_ORDER) }
    var handleOrderPerLayer by remember { mutableStateOf<Map<String, Map<HandlePosition, LayerHandleRole>>>(emptyMap()) }
    // Mini-ventana "Solo aquí" / "Todos" que abre la manija de reordenar.
    var showReorderScopeMenu by remember { mutableStateOf(false) }
    // "Armado" por la opción "Restablecer" de esa misma mini-ventana:
    // restablecer el orden a [DEFAULT_HANDLE_ORDER] necesita saber el
    // MISMO alcance que reordenar a mano (¿solo esta capa, o todas?) —
    // así que "Restablecer" no aplica nada por sí sola, solo arma este
    // flag y deja el mensaje de abajo pidiendo elegir "Solo" o "Todos"
    // para completar la acción con ese alcance. Se apaga solo al cerrar
    // la mini-ventana (ver LaunchedEffect más abajo), así nunca queda
    // "pegado" armado si el usuario cierra sin elegir nada.
    var restoringHandleOrder by remember { mutableStateOf(false) }
    LaunchedEffect(showReorderScopeMenu) {
        if (!showReorderScopeMenu) restoringHandleOrder = false
    }
    // Trae a estas variables locales lo que haya quedado guardado en el
    // proyecto (ver [EditorViewModel.handleOrderGlobal]/[handleOrderPerLayer]
    // y [ProjectStorage]) apenas termina de cargar. Antes esto nunca pasaba
    // — `handleOrderGlobal`/`handleOrderPerLayer` de acá arriba arrancaban
    // siempre en su valor por defecto y el guardado en disco (que sí existía
    // del lado de ProjectStorage/EditorViewModel) jamás se leía de vuelta.
    // `handleOrderGlobal.isEmpty()` en el estado guardado significa "nunca
    // se reordenó nada" (ver comentario en ProjectModels.kt), así que en ese
    // caso se deja el default en vez de pisarlo con un mapa vacío.
    LaunchedEffect(state.isLoadingProject) {
        if (!state.isLoadingProject) {
            handleOrderGlobal = if (state.handleOrderGlobal.isEmpty()) {
                DEFAULT_HANDLE_ORDER
            } else {
                decodeHandleOrder(state.handleOrderGlobal)
            }
            handleOrderPerLayer = decodeHandleOrderPerLayer(state.handleOrderPerLayer)
        }
    }
    // Modo reordenar activo: mientras es true, el canvas queda
    // "congelado" (nada de mover/rotar/escalar la capa ni cambiar de
    // selección) y las 7 manijas intercambiables se pueden arrastrar
    // unas sobre otras. `reorderDraftOrder` es el borrador en vivo — no
    // se aplica de verdad (a `handleOrderGlobal`/`handleOrderPerLayer`)
    // hasta tocar el ícono de check.
    var reorderMode by remember { mutableStateOf(false) }
    var reorderScope by remember { mutableStateOf<HandleReorderScope?>(null) }
    var reorderDraftOrder by remember { mutableStateOf<Map<HandlePosition, LayerHandleRole>?>(null) }
    // Mientras se arrastra una manija sobre otra: de qué posición salió
    // y dónde está el dedo ahora mismo, para dibujar una "manija fantasma"
    // que lo sigue (ver Canvas de dibujo, más abajo).
    var reorderDragFromPosition by remember { mutableStateOf<HandlePosition?>(null) }
    var reorderDragOffset by remember { mutableStateOf(Offset.Zero) }
    // A pedido: tocar la × (rol DELETE) mientras el modo reordenar está
    // activo ya NO cancela de una — solo abre este diálogo de
    // confirmación ("¿Cancelar el reordenamiento?" Sí/No). El draft
    // (`reorderDraftOrder`) se mantiene intacto hasta que el usuario
    // confirme; si toca "No"/afuera, sigue reordenando como si nada.
    var showCancelReorderConfirm by remember { mutableStateOf(false) }

    // Orden vigente (ya resuelto: por-capa si existe, si no el global) para una capa dada.
    fun effectiveHandleOrder(layerId: String): Map<HandlePosition, LayerHandleRole> =
        handleOrderPerLayer[layerId] ?: handleOrderGlobal

    // --- Mismo reemplazo de flechas subir/bajar que ya se hizo en el
    // panel de acciones del timeline (TimelineView.kt): acá abajo, en el
    // panel legado "Capas", conviven las MISMAS dos acciones nuevas
    // (renombrar / cambiar color) para que ambos paneles se comporten
    // igual — nada de que un panel tenga las flechas viejas y el otro no. ---
    var layerPendingRename by remember { mutableStateOf<Layer?>(null) }
    // --- Cuentagotas (ver LayerDialogs.kt → LayerColorPickerDialog): el
    // color que se "chupa" vive en lo que dibuja el renderer del preview,
    // así que el pedido/resultado tiene que pasar por acá.
    //
    // FASE 4.3 (RENDER BOUNDARY — "wiring productivo de RenderApi"): antes
    // esta variable era `PixelColorSource?` directo — el `RenderApi` ya
    // existía (contrato + implementación + tests) pero NINGÚN consumidor
    // real lo usaba todavía, el único (este cuentagotas) seguía hablando
    // con `PixelColorSource` sin pasar por él. Ahora `EditorScreen`
    // construye el `RenderApiImpl` real acá mismo, en el momento en que
    // `GLPreview` entrega la instancia de `PixelColorSource` (respaldada
    // por `GLRenderer`, vía `AndroidGLRenderHost` — ver sus KDoc para esa
    // parte del boundary, sin cambios) — es el Composition Root correcto
    // para esto: el único lugar del proyecto donde existe una superficie
    // GL viva para componer un `RenderApi` real (ver KDoc de
    // `EliNerApiImpl.render`, que documenta exactamente este mismo motivo
    // para por qué `EliNerApiImpl` no se compone en un lugar más "alto"
    // como `MainActivity`). La UI sigue sin conocer `GLRenderer`: conoce
    // `RenderApi` (dominio) y, a través de él, `PixelColorSource`
    // (capacidad) — nunca la clase concreta.
    var renderApi by remember { mutableStateOf<RenderApi?>(null) }
    // AUDITORÍA — "flash verde de milisegundos al reabrir un proyecto":
    // `state.isLoadingProject` (ver EditorViewModel) pasa a `false` apenas
    // termina el decode en CPU de las imágenes — NO cuando esas texturas ya
    // están subidas a la GPU y pintadas de verdad en pantalla (eso pasa
    // después, en el hilo de render de GLSurfaceView, y puede tardar uno o
    // varios frames con un fondo pesado). Ocultar el overlay verde de carga
    // apenas `isLoadingProject` se apaga dejaba una ventana corta pero real
    // donde se veía el `glClearColor` verde de "lienzo vacío" (ver
    // `LayerDrawer.ensureInitialized`) porque la textura real todavía no
    // estaba lista. `canvasFullyPainted` recién pasa a `true` con el aviso
    // real del renderer (`onAllVisibleLayersPainted`, ver GLPreview/
    // GLRenderer) — el overlay de carga se mantiene hasta ESE momento, no
    // hasta que el ViewModel termine con su parte.
    var canvasFullyPainted by remember { mutableStateOf(false) }
    // Cada vez que arranca una carga nueva (abrir este proyecto, o volver a
    // "Mis proyectos" y reabrir otro) hay que volver a esperar el primer
    // pintado real — sin este reset, reabrir un proyecto DESPUÉS de ver el
    // primero ya pintado saltaría directo a `true` y el bug reaparecería.
    LaunchedEffect(state.isLoadingProject) {
        if (state.isLoadingProject) canvasFullyPainted = false
    }
    // Qué capa está esperando un color del cuentagotas ahora mismo (null = nadie).
    var eyedropperActiveForLayerId by remember { mutableStateOf<String?>(null) }
    // Resultado listo para que la fila de esa capa lo recoja y reabra su
    // diálogo de color con este color ya cargado. Se limpia apenas la fila
    // lo consume (ver onConsumeEyedropperResult más abajo).
    var eyedropperPickedColor by remember { mutableStateOf<Pair<String, Int>?>(null) }
    // --- Cuentagotas en vivo (arrastrar para previsualizar, ver overlay
    // más abajo): posición actual del dedo sobre el preview (en px de
    // vista, coordenadas locales del Box del canvas) y el color que se
    // está leyendo AHORA MISMO en esa posición, mientras el dedo sigue
    // apoyado — todavía no es el color elegido, solo lo que se ve dentro
    // de la lupa en tiempo real. null = no hay dedo apoyado. ---
    var eyedropperTouchPos by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var eyedropperLiveArgb by remember { mutableStateOf<Int?>(null) }
    var layerPendingColorChange by remember { mutableStateOf<Layer?>(null) }

    // --- Guías inteligentes de alineación (centrado): igual que
    // Figma/Photoshop/CapCut, SIEMPRE activas (no dependen del switch
    // "Snap" de la cuadrícula manual, que es una herramienta aparte —
    // ver [snapTranslateToGrid]) mientras se arrastra o redimensiona
    // una capa. `activeSnapGuideXPx`/`activeSnapGuideYPx` son la
    // posición (en px de pantalla, dentro del Box del canvas) de la
    // línea vertical/horizontal a resaltar AHORA MISMO — null = ese eje
    // no está enganchado en este instante, no se dibuja nada. Se
    // recalculan en cada muestra del gesto (ver [computeAlignmentSnap])
    // y se limpian (= null) al soltar el dedo, para que la línea
    // desaparezca apenas termina el gesto — es un overlay TRANSITORIO,
    // nunca una cuadrícula persistente.
    var activeSnapGuideXPx by remember { mutableStateOf<Float?>(null) }
    var activeSnapGuideYPx by remember { mutableStateOf<Float?>(null) }
    // Igual que arriba pero para la manija de ROTAR: grados (0, 15, 30…)
    // al que está enganchada la rotación ahora mismo, o null si no está
    // cerca de ningún múltiplo de 15°. Alimenta el HUD numérico flotante
    // (ver el Text() junto al resto de HUDs de gesto, más abajo).
    var activeRotationSnapDeg by remember { mutableStateOf<Float?>(null) }

    val selectedLayer = viewModel.currentSelectedLayer()
    val currentFrame = selectedLayer?.let { viewModel.frameAt(it, state.playheadMs) }
    var isFullscreen by remember { mutableStateOf(false) }
    // --- Qué panel de la barra Keyframes/Control/Módulos está abierto ahora
    // mismo (null = ninguno, se ve el timeline normal). Tocar la pestaña ya
    // abierta la cierra; tocar otra distinta cambia a esa. ---
    var expandedBottomSection by remember { mutableStateOf<BottomBarSection?>(null) }
    // La ventana ampliada del audio cubre la barra Módulos/Control/Keyframes mientras está abierta.
    var audioDetailOpen by remember { mutableStateOf(false) }

    // --- Panel de "Información del proyecto" (título, sinopsis, créditos):
    // reemplaza TODA la zona de abajo (regla + capas + barra
    // Keyframes/Control/Módulos), no se superpone parcial como
    // expandedBottomSection — por eso es un booleano aparte y no un cuarto
    // valor de BottomBarSection. Ver el ícono en la barra de arriba
    // (ic_project_info, a la izquierda de la cuadrícula) y ProjectInfoPanel
    // en EditorBottomBar.kt.
    var showProjectInfoPanel by remember { mutableStateOf(false) }

    // --- Preview en vivo del audio de fondo (independiente del pipeline de export).
    // La UI ya no posee el AudioPreviewPlayer ni decide play/pause/seek —
    // solo avisa al ViewModel que uno de estos tres valores cambió; quién
    // reproduce y cómo es responsabilidad del motor (ver
    // EditorViewModel.syncAudioPreview / .updateAudioPreviewVolume).
    // FASE 1 (carril con varios clips, ver AudioClip.kt): antes observaba
    // dos campos sueltos de un único clip (`sourceUri`/`muted`); ahora
    // observa el CARRIL completo — cualquier cambio real (archivo,
    // mute, trim, agregar/quitar un clip) dispara un resync, porque
    // `syncAudioPreview` decide por sí mismo, con el estado más fresco
    // posible, cuál clip está activo bajo el cabezal en este instante
    // (ver `AudioTrack.activeClipAt`).
    LaunchedEffect(state.isPlaying, state.audioTracks) {
        viewModel.syncAudioPreview(context)
    }
    // El volumen sí se aplica en caliente sin reiniciar la reproducción.
    LaunchedEffect(state.audioTracks) {
        viewModel.updateAudioPreviewVolume(context)
    }
    // Atrás del sistema (gesto o botón físico de Android): ver el
    // BackHandler más abajo, movido después de `exitEditMode()` y compañía
    // porque los necesita — Kotlin no permite referenciar en un scope de
    // función local una variable/función declarada más adelante en el
    // mismo cuerpo, ni siquiera desde dentro de un lambda.


    // Parpadeo del ícono de grabar: solo se anima de verdad mientras
    // isCapturing es true (grabando en serio); en los otros dos estados
    // (apagado / armado en rojo fijo) el alpha se queda en 1f.
    val recordBlink by rememberInfiniteTransition(label = "recordBlink").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(animation = tween(450), repeatMode = RepeatMode.Reverse),
        label = "recordBlinkAlpha"
    )
    // Además del ícono, el FONDO del botón también pulsa en rojo — mucho
    // más notorio que solo el ícono parpadeando, para que sea inconfundible.
    val recordGlow by rememberInfiniteTransition(label = "recordGlow").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(animation = tween(450), repeatMode = RepeatMode.Reverse),
        label = "recordGlowAlpha"
    )

    // Valores del "keyframe en edición" para la capa seleccionada. Al
    // tocar cualquier capa (en el panel de arriba) esto cambia de
    // inmediato: cada capa tiene sus propios valores de cámara Y de look
    // cinematográfico, completamente independientes entre sí.
    //
    // Mientras se está GRABANDO, la key NO incluye el playhead (que
    // avanza solo) para que un gesto en curso no se reinicie a mitad de
    // camino cada 16ms; solo se reinicia al cambiar de capa o al
    // arrancar/parar la grabación. Fuera de grabación, sí se reinicia al
    // mover el playhead manualmente (comportamiento normal de edición).
    // "Ancla" de sincronización con el modelo: se recalcula EXPLÍCITAMENTE
    // solo en los momentos en que los sliders/el gesto SÍ deben "saltar" a
    // leer el keyframe actual — cambiar de capa seleccionada, un undo/redo,
    // o mover el playhead a mano estando FUERA de grabación. Activar o
    // desactivar el modo Grabar NO la toca: así la pose que se dejó
    // ajustada con los sliders (o arrastrando la imagen) antes de grabar
    // se conserva intacta al presionar el botón rojo, en vez de saltar de
    // vuelta al valor del keyframe existente en ese punto — o al neutro,
    // si todavía no había ninguno — que es justo lo que pasaba antes.
    var syncTick by remember { mutableStateOf(0) }

    var translateX by remember { mutableStateOf(currentFrame?.translateX ?: 0f) }
    var translateY by remember { mutableStateOf(currentFrame?.translateY ?: 0f) }
    var scale by remember { mutableStateOf(currentFrame?.scale ?: 1f) }
    var rotation by remember { mutableStateOf(currentFrame?.rotationDeg ?: 0f) }
    var alpha by remember { mutableStateOf(currentFrame?.alpha ?: 1f) }
    var tiltX by remember { mutableStateOf(currentFrame?.tiltXDeg ?: 0f) }
    var tiltY by remember { mutableStateOf(currentFrame?.tiltYDeg ?: 0f) }
    var focusBlur by remember { mutableStateOf(currentFrame?.focusBlur ?: 0f) }
    var dollyZoom by remember { mutableStateOf(currentFrame?.dollyZoom ?: 0f) }
    // Estirado independiente de ancho/alto — manijas "estirar ancho"
    // (lateral derecha) y "estirar alto" (inferior central) del modo
    // "Edición > Imagen". Ver el comentario completo en CameraFrame.kt.
    var scaleX by remember { mutableStateOf(currentFrame?.scaleX ?: 1f) }
    var scaleY by remember { mutableStateOf(currentFrame?.scaleY ?: 1f) }

    // Relee el keyframe real del modelo y lo vuelca a los sliders; también
    // avanza syncTick, que el pointerInput del preview usa para saber
    // cuándo debe "olvidarse" del gesto en curso (mismo criterio, un solo
    // lugar de verdad).
    //
    // [keepEditViewTransform]: dentro del modo edición, posición y escala son de la VISTA (centrada y ajustada
    // al visor), no de la capa; si deshacer/rehacer o mover el playhead las repusieran desde el modelo, la
    // imagen saltaría a su tamaño de proyecto en mitad de la edición. Sin valor por defecto a propósito: cada
    // llamador debe decidirlo explícitamente.
    fun syncSlidersFromModel(keepEditViewTransform: Boolean) {
        val frame = viewModel.currentSelectedLayer()?.let { viewModel.frameAt(it, state.playheadMs) }
        if (!keepEditViewTransform) {
            translateX = frame?.translateX ?: 0f
            translateY = frame?.translateY ?: 0f
            scale = frame?.scale ?: 1f
        }
        rotation = frame?.rotationDeg ?: 0f
        alpha = frame?.alpha ?: 1f
        tiltX = frame?.tiltXDeg ?: 0f
        tiltY = frame?.tiltYDeg ?: 0f
        focusBlur = frame?.focusBlur ?: 0f
        dollyZoom = frame?.dollyZoom ?: 0f
        scaleX = frame?.scaleX ?: 1f
        scaleY = frame?.scaleY ?: 1f
        syncTick++
    }

    // Los dos mini-menús flotantes que cuelgan de las manijas del marco de
    // edición — "Restablecer" (RESTORE) y "Solo aquí" / "Todos"
    // (reordenar) — vivían cada uno con su propio booleano independiente,
    // sin coordinarse entre sí. Resultado: si abrías uno y después tocabas
    // la manija de otro, los dos quedaban abiertos a la vez y se
    // superponían en pantalla (un mini-menú literalmente encima del otro),
    // en vez de comportarse como en cualquier editor serio — donde abrir
    // un menú cierra el que estuviera abierto antes. Esta función central
    // se llama justo antes de abrir cualquiera de los dos, para que como
    // máximo haya uno abierto en todo momento. (La manija "esquina sup.
    // izquierda", antes un mini-menú con la única opción "Editar", ahora
    // es un lápiz que entra directo a modo edición sin ventana intermedia
    // — ver [enterEditModeForSelectedLayer] — así que ya no forma parte de
    // este grupo de mini-menús flotantes.)
    fun closeFloatingLayerMenus() {
        showReorderScopeMenu = false
        showRestoreOptionsMenu = false
    }

    // --- Modo edición dedicado (por-capa) ---
    // Id de la capa que está en "modo edición" aislado: se activa desde el
    // lápiz de la manija "esquina sup. izquierda" del marco de edición
    // (ver [enterEditModeForSelectedLayer], sin ventana intermedia).
    // Mientras esté seteado (no-null) y coincida con la capa seleccionada:
    //  - el GLPreview solo dibuja ESA capa (el resto desaparece del
    //    canvas, ver `getLayers` de GLPreview más abajo).
    //  - esa capa se muestra centrada y ajustada al visor (translateX/Y a
    //    0,0 y `scale` al valor de EditViewFit). Posición y escala originales
    //    quedan en `editViewSession` y se devuelven tal cual al salir; mientras
    //    tanto son valores de la VISTA, nunca de la capa (ver `commitLiveFrame`
    //    y `syncSlidersFromModel(keepEditViewTransform)`).
    //  - aparece el panel inferior de edición (por ahora vacío — "cáscara"
    //    lista para que una futura actualización le agregue los ajustes y
    //    parámetros de edición de imagen profesional pedidos).
    var editModeLayerId by remember { mutableStateOf<String?>(null) }
    // A PEDIDO DEL USUARIO: "Efectos" (antes una pestaña más al lado de
    // "Recolor"/"3D" en EditImageToolsHeader, con su fila de chips
    // Fondo/Color/Contorno/... debajo) ahora se dispara con el texto
    // "Efecto" de ACÁ ARRIBA, al lado del título del proyecto en la barra
    // superior — ver el Row de "Izquierda" del topBar, más abajo. Como ese
    // botón vive en un composable distinto (el topBar) del que consume la
    // categoría elegida (LayerColorEditPanel → EffectsPanel, mucho más
    // abajo en este archivo), el estado tiene que vivir en el ancestro
    // común de los dos — este mismo EditorScreen — en vez de local a
    // cualquiera de las dos puntas, como estaba antes.
    // --- Ventanas de edición de imagen: MULTI-VENTANA ---
    // A PEDIDO DEL USUARIO (arreglo profesional del bug reportado con
    // captura): antes existía un único valor "activo" (`editImageToolsTab`
    // + `editImageColorOption`, ambos de un solo slot) que representaba
    // "qué ventana está abierta ahora" — como solo puede haber UN valor
    // a la vez, abrir una ventana SIEMPRE cerraba cualquier otra que
    // estuviera abierta, aunque el usuario no hubiera tocado su × para
    // cerrarla. Eso es lo que el usuario reportó: "solo permite una sola
    // ventana... y no dos o más".
    //
    // Ahora cada ventana flotante (Recolor, Color Básico, 3D Básico) y el
    // panel de Efectos tienen su PROPIO flag `Boolean`, totalmente
    // independiente de los demás. Abrir una no toca el estado de las
    // otras, así que el usuario puede tener dos, tres o las cuatro
    // abiertas al mismo tiempo — depende solo de cuántas vaya tocando —,
    // cada una se cierra por separado con su propia × sin afectar al
    // resto, y todas conviven arrastrables sobre el canvas (con
    // `initialOffset` escalonado más abajo para que no nazcan tapándose
    // una a otra exactamente en el mismo punto).
    var recolorWindowOpen by remember(editModeLayerId) { mutableStateOf(false) }
    var colorBasicoWindowOpen by remember(editModeLayerId) { mutableStateOf(false) }
    var basico3DWindowOpen by remember(editModeLayerId) { mutableStateOf(false) }
    // A PEDIDO DEL USUARIO: "Contorno" y "Resplandor" ahora tienen cada
    // una su PROPIA ventana flotante, con el mismo criterio que
    // Recolor/Color Básico/3D Básico de acá arriba — independientes
    // entre sí y de la ventana compartida de "Efecto" (que, tras las
    // sucesivas extracciones de Sombra, Reflejo y Distorsión más abajo,
    // ya no cubre NINGUNA categoría de las que este mismo menú ofrece —
    // solo sigue existiendo para Fondo/Color/Presets, inalcanzables desde
    // acá). ACLARACIÓN EXPLÍCITA DEL USUARIO (para no repetir el error de "Recolor"/
    // "Básico" con Color): esto NO saca "Contorno" ni "Resplandor" del
    // menú desplegable "Efecto" de la barra superior — siguen siendo
    // dos ítems de ESE menú (ver EditImageEffectsMenu), tal cual
    // "Recolor"/"Básico" lo son del menú "Color". Lo único que cambia es
    // qué pasa al tocarlos: en vez de compartir la ventana genérica de
    // "Efecto", cada uno dispara la suya propia.
    var contornoWindowOpen by remember(editModeLayerId) { mutableStateOf(false) }
    var resplandorWindowOpen by remember(editModeLayerId) { mutableStateOf(false) }
    // A PEDIDO EXPLÍCITO DEL USUARIO — misma idea que Contorno/Resplandor
    // de arriba, ahora para "Sombra": ventana propia, independiente de la
    // compartida (que, tras la extracción de Reflejo y Distorsión más
    // abajo, ya no cubre ninguna categoría accesible desde este menú). A
    // diferencia de Contorno/Resplandor (una sola categoría hoja cada
    // una), "Sombra" agrupa 3 variantes (Sombra / Sombra relleno / Sombra
    // contacto, ver `sombraSubCategories` en EffectsPanel) — su ventana
    // trae su PROPIO segundo nivel de sub-pestañas adentro (ver
    // [SombraFloatingWindow]), no una sola categoría hoja como las otras
    // dos.
    var sombraWindowOpen by remember(editModeLayerId) { mutableStateOf(false) }
    // Mismo criterio, para "Reflejo" — también agrupa varias variantes
    // (Reflejo / Light wrap / Luz global), así que trae su propio segundo
    // nivel de sub-pestañas adentro, igual que Sombra.
    var reflejoWindowOpen by remember(editModeLayerId) { mutableStateOf(false) }
    // Mismo criterio, para "Distorsión" — a diferencia de las otras
    // cuatro, no agrupa varias variantes de un mismo concepto (sigue
    // siendo UNA sola categoría, con su propio motor de pintura de
    // máscara/gestos), así que su ventana es más simple: solo envuelve
    // [DistortionPanel] tal cual, sin un segundo nivel de sub-pestañas.
    var distortionWindowOpen by remember(editModeLayerId) { mutableStateOf(false) }
    // --- BUG REAL corregido (reportado con captura: "las ventanas de
    // preset se sobreponen... que respete profesionalmente"): antes las
    // tres ventanas flotantes (Recolor / Color Básico / 3D Básico)
    // compartían el mismo `zIndex(20f)` fijo en el Box de más abajo,
    // donde se instancian — así que el orden entre ellas tres dependía
    // solo del orden en que están ESCRITAS en el código, nunca de cuál
    // tocó el usuario último. El popup de "Presets" de cada una (ver
    // [FloatingWindowPresetsControl]) es un `Popup` real de Android —
    // siempre se dibuja por encima de TODO en la Activity, sin importar
    // ningún `zIndex` de Compose — así que tocar "Presets" en una
    // ventana que el usuario consideraba "de atrás" hacía que su popup
    // tapara a una ventana "de adelante": se veía roto.
    //
    // La corrección real: cada ventana tiene ahora su PROPIO `zIndex`
    // (ver `recolorWindowZIndex`/`colorBasicoWindowZIndex`/
    // `basico3DWindowZIndex`, acá abajo) en vez de uno fijo compartido.
    // `floatingWindowZOrderCounter` es el contador global de "quién fue
    // tocada más recientemente" — CUALQUIER toque sobre una ventana (ver
    // `onInteracted` en [FloatingToolWindow]) le asigna el próximo
    // número de este contador, trayéndola al frente de las otras dos.
    // Arranca en 14f (por encima del mayor valor inicial de las DIEZ
    // ventanas, 9f — Recolor/Color Básico/3D Básico/Contorno/Resplandor/
    // Sombra/Reflejo/Distorsión/Zoom X/Reverb, ver más abajo —
    // el margen sobrante se conserva a propósito) para que la PRIMERA
    // vez que el usuario toque cualquiera de ellas, quede garantizado
    // que pasa al frente —
    // sin ese margen, tocar la que ya nació con el valor inicial más alto
    // no cambiaría nada visible la primera vez.
    var floatingWindowZOrderCounter by remember { mutableStateOf(14f) }
    // Valores iniciales escalonados (0f/1f/2f) para preservar el orden
    // de apilado de siempre — Recolor atrás, Color Básico en el medio,
    // 3D Básico adelante — mientras el usuario no interactuó todavía con
    // ninguna. `remember(editModeLayerId)` (igual que los `*WindowOpen`
    // de acá arriba) para que una capa nueva no herede el orden de
    // apilado de la capa anterior.
    var recolorWindowZIndex by remember(editModeLayerId) { mutableStateOf(0f) }
    var colorBasicoWindowZIndex by remember(editModeLayerId) { mutableStateOf(1f) }
    var basico3DWindowZIndex by remember(editModeLayerId) { mutableStateOf(2f) }
    // Mismo criterio que las tres de arriba, para "Contorno" y
    // "Resplandor" — valores iniciales 3f/4f, siguiendo la cadena
    // (0/1/2 ya ocupados), y el contador global de más abajo
    // (`floatingWindowZOrderCounter`) arranca en 5f en vez de 3f para
    // que siga garantizado que el primer toque a CUALQUIERA de las
    // cinco ventanas la traiga al frente.
    var contornoWindowZIndex by remember(editModeLayerId) { mutableStateOf(3f) }
    var resplandorWindowZIndex by remember(editModeLayerId) { mutableStateOf(4f) }
    // Mismo criterio, para "Sombra" — valor inicial 5f, siguiendo la
    // cadena (0/1/2/3/4 ya ocupados).
    var sombraWindowZIndex by remember(editModeLayerId) { mutableStateOf(5f) }
    // Mismo criterio, para "Reflejo" — valor inicial 6f, siguiendo la
    // cadena (0/1/2/3/4/5 ya ocupados).
    var reflejoWindowZIndex by remember(editModeLayerId) { mutableStateOf(6f) }
    // Mismo criterio, para "Distorsión" — valor inicial 7f, siguiendo la
    // cadena (0/1/2/3/4/5/6 ya ocupados).
    var distortionWindowZIndex by remember(editModeLayerId) { mutableStateOf(7f) }
    // --- "Zoom X" (categoría "Cámara" del cajón de Módulos — ver
    // ZoomXWheel.kt/ZoomXFloatingWindow.kt) — MISMO mecanismo de
    // ventana flotante propia que las ocho de arriba, con UNA diferencia
    // deliberada: `remember(selectedLayer?.id)`, no
    // `remember(editModeLayerId)`. Las ocho ventanas de arriba viven
    // DENTRO del modo "Editar" aislado (`enterEditModeForSelectedLayer`,
    // más arriba) — un modo especial que además resetea `translateX`/
    // `translateY` a 0 al entrar. Zoom X se abre directo desde el cajón
    // de Módulos, con solo tener una capa seleccionada (mismo criterio
    // que el joystick de la pestaña "Control", que tampoco exige ese
    // modo) — exigir "Editar" acá sería una talanquera sin sentido, y
    // heredar el reseteo de translate sería un efecto secundario no
    // pedido. Valor inicial de zIndex, 8f, siguiendo la cadena
    // (0..7f ya ocupados por las otras ocho).
    var zoomXWindowOpen by remember(selectedLayer?.id) { mutableStateOf(false) }
    var zoomXWindowZIndex by remember(selectedLayer?.id) { mutableStateOf(8f) }
    // Preferencia del switch "Transición gradual" (ver ZoomXWheel.kt) —
    // por capa, igual que las dos de arriba: cambiar de capa vuelve a
    // "apagado" en vez de arrastrar la preferencia de la capa anterior.
    var zoomXGradualFadeEnabled by remember(selectedLayer?.id) { mutableStateOf(false) }
    // Ventana del Reverb del CARRIL de audio seleccionado (ver ReverbFloatingWindow.kt). A diferencia de las de arriba, su
    // estado va ligado al carril (`selectedAudioTrackId`), no a la capa: cada carril carga sus propios módulos, y cambiar de
    // carril cierra la ventana en vez de mostrar el Reverb de otro. zIndex inicial 9f: sigue la cadena (0..8f ya ocupados).
    var reverbWindowOpen by remember(selectedAudioTrackId) { mutableStateOf(false) }
    var reverbWindowZIndex by remember(selectedAudioTrackId) { mutableStateOf(9f) }
    // BUG REAL corregido (reportado con captura: las ventanas flotantes y
    // sus íconos minimizados "se pierden" al arrastrarlos hacia los
    // costados). Causa raíz real: ningún arrastre — ni el de la ventana
    // abierta (cabecera) ni el del ícono ya minimizado — tenía jamás un
    // límite en el eje X, y en Y solo existía un piso (`coerceAtLeast(0f)`,
    // para no tapar el header) pero ningún techo. `offsetPx` podía crecer
    // o decrecer sin ningún freno hasta quedar fuera del área visible,
    // sin ninguna forma de recuperarlo arrastrando de vuelta porque el
    // dedo del usuario también sale del área arrastrable.
    //
    // La corrección real mide el tamaño VERDADERO del área donde viven
    // las tres ventanas (el Box exterior de más abajo, después del
    // `padding` del Scaffold) con `onSizeChanged`, y ese tamaño real
    // -no un valor adivinado ni el ancho/alto completo de pantalla,
    // que no coincide con esta área cuando el timeline ocupa parte de
    // ella- es lo que [FloatingToolWindow] usa para recortar `offsetPx`
    // en AMBOS ejes, con techo y piso, en cada arrastre (ventana abierta
    // e ícono minimizado) y al asentarse. Ver `floatingWindowAreaSizePx`
    // pasado a cada llamador, más abajo, y su uso dentro de
    // [FloatingToolWindow].
    var floatingWindowAreaSizePx by remember { mutableStateOf(IntSize.Zero) }
    var effectsWindowOpen by remember(editModeLayerId) { mutableStateOf(false) }
    // BUG REAL evitado a propósito antes de que existiera: "Contorno",
    // "Resplandor", "Sombra" y "Reflejo" (ventanas propias, ver más
    // abajo) y esta ventana compartida (que a esta altura ya solo cubre
    // "Distorsión") arman TODAS el mismo
    // `ImageEffectsParams` — un solo paquete con outline+glow+sombra+
    // reflejo+etc. que se renderiza junto sobre la imagen base cada vez
    // que se hace commit (ver `currentParams()`/`applyLivePreviewAndScheduleCommit`
    // dentro de EffectsPanel). Si cada ventana tuviera su PROPIO
    // `EffectsControlsState` (como tenía `EffectsPanel` antes, con
    // `rememberEffectsControlsState(layer.id)` local), cerrar/abrir o
    // usar dos de estas ventanas a la vez haría que el commit de una
    // "pisara" con sus propios valores por defecto (0) los efectos que
    // la OTRA ya tenía aplicados — por ejemplo, tocar el resplandor
    // borraría el contorno ya puesto, sin que el usuario tocara nada de
    // contorno. Por eso el estado se levanta ACÁ, una sola vez por capa,
    // y se pasa por parámetro a las tres (ver `EffectsPanel`/
    // `LayerColorEditPanel`, más abajo, y las nuevas
    // [ContornoFloatingWindow]/[ResplandorFloatingWindow]) en vez de que
    // cada una cree la suya.
    val effectsCtrl = rememberEffectsControlsState(editModeLayerId)
    // A PEDIDO DEL USUARIO — MULTI-VENTANA: mismo motivo que
    // `effectsCtrl` de acá arriba — ver el comentario grande junto a los
    // parámetros `ctrl`/`liveBitmap`/`fullBitmap` en la firma de
    // [EffectsPanel] para el porqué completo. La base "plana" de la capa
    // (antes decodificada por separado dentro de cada `EffectsPanel`) se
    // carga UNA sola vez acá, apenas se entra en modo edición de esta
    // capa — así "Contorno", "Resplandor", "Sombra", "Reflejo" y la
    // ventana compartida de "Distorsión" parten siempre de la MISMA
    // base, sin importar en qué orden el usuario abra cada una.
    // Las tres versiones de la imagen (chica para arrastrar, de pantalla para reposar, completa para guardar) que
    // comparten TODAS las ventanas de efectos de esta sesión. Ver [EffectSources] y [ProgressiveRenderScheduler].
    val effectsSources = remember(editModeLayerId) { EffectSources<Bitmap>() }
    LaunchedEffect(effectsSources) {
        val layerForEffects = selectedLayer
        if (editModeLayerId != null && layerForEffects != null && editModeLayerId == layerForEffects.id) {
            val sourceUri = layerForEffects.sourceUri
            suspend fun decode(maxDimension: Int): Bitmap? = withContext(Dispatchers.IO) {
                ImageDecoding.decodeSampledFromUri(context.contentResolver, sourceUri, maxDimension = maxDimension)
            }
            effectsSources.publishQuick(decode(EffectsPreviewSizing.QUICK_MAX_DIMENSION))
            EffectSourceLoader.loadRemaining(
                sources = effectsSources,
                displayDecodeLimit = { context.effectsDisplayDecodeLimit(EditViewFit.FILL_FRACTION) },
                onDecodeFailure = ::logEffectDecodeFailure,
                decode = { maxDimension -> decode(maxDimension) }
            )
        } else {
            // Sin capa en edición no habrá imagen completa: quien la espere se libera y no guarda nada.
            effectsSources.completeFull(null)
        }
    }
    // `editImageEffectsCategory`: índice de categoría dentro del
    // mapa de índices de categorías (ver EffectsPanel) — antes vivía adentro de
    // EffectsPanel. A PEDIDO DEL USUARIO — MULTI-VENTANA: ahora que
    // "Contorno" (2), "Resplandor" (3), "Sombra" (4) y "Reflejo" (5)
    // tienen cada una su propia ventana (ver `contornoWindowOpen`/
    // `resplandorWindowOpen`/`sombraWindowOpen`/`reflejoWindowOpen`,
    // arriba), esta variable solo respalda la ventana COMPARTIDA — la
    // única categoría "de sliders sobre `ctrl`" que de verdad la sigue
    // usando es "Fondo" (0); "Color" (1) y "Presets" (6) también tienen
    // su rama en el `when` por compatibilidad de índices pero no se
    // ofrecen desde ningún menú (ver el KDoc de [EditImageEffectsMenu]);
    // "Distorsión" (7) usa esta misma variable para SU chequeo
    // (`selectedEffectsTopCategory == 7`, ver más abajo) pero no rutea
    // por el `when` de sliders — tiene su panel propio completo. El
    // `onSelect` de EditImageEffectsMenu (ver el topBar, más abajo)
    // intercepta 2, 3, 4 y 5 ANTES de llegar acá, así que esta variable
    // nunca vuelve a valer ninguno de esos cuatro.
    //
    // Arranca en 0 ("Fondo") — CORREGIDO en esta misma revisión, por
    // SEGUNDA vez: primero estaba en 4 ("Sombra"), se corrigió a 5
    // ("Reflejo") cuando "Sombra" se mudó a su propia ventana, y ahora
    // "Reflejo" TAMBIÉN se mudó — persiguiendo "la próxima categoría
    // disponible" cada vez que se extrae una más es frágil y se rompe de
    // nuevo con la próxima extracción. "Fondo" (0) es la única opción que
    // NUNCA va a dejar de ser válida acá: es una de las tres
    // (Fondo/Color/Presets) que la arquitectura ya declaró que jamás
    // tendrán ventana propia (ver el KDoc de [EditImageEffectsMenu]) — un
    // valor por defecto elegido así no vuelve a quedar obsoleto con cada
    // categoría nueva que se extraiga.
    var editImageEffectsCategory by remember(editModeLayerId) { mutableStateOf(5) }
    // A PEDIDO DEL USUARIO: el menú NO debe abrir con una opción ya
    // pintada como elegida — "Reflejo" es el valor por defecto que se
    // usa para las sliders de abajo mientras el usuario no tocó nada,
    // pero eso es un detalle interno, no algo que el usuario "eligió".
    // Este flag separa las dos cosas: solo se pone en `true` cuando el
    // usuario de verdad toca una opción del menú (ver EditImageEffectsMenu
    // más abajo) — hasta entonces, el menú se dibuja sin ningún ítem
    // resaltado, aunque `editImageEffectsCategory` ya tenga un valor.
    var editImageEffectsCategoryChosen by remember(editModeLayerId) { mutableStateOf(false) }
    // Si el menú desplegable de "Efecto" (ver EditImageEffectsMenu) está
    // abierto ahora mismo.
    var showEditImageEffectsMenu by remember { mutableStateOf(false) }
    // A PEDIDO DEL USUARIO: mismo movimiento que "Efecto" pero para
    // "Recolor" — el texto "Color" acá arriba, al lado de "Efecto", abre
    // EditImageColorMenu (misma ventana angosta vertical) en vez de
    // dejar "Recolor" como pestaña suelta en EditImageToolsHeader. Esa
    // ventana tiene DOS opciones ahora ("Básico" y "Recolor", ver
    // EditImageColorMenu); el diseño sigue dejando lugar para sumar más
    // sin tocar esto de nuevo. Si el menú desplegable de "Color" está
    // abierto ahora mismo.
    var showEditImageColorMenu by remember { mutableStateOf(false) }
    // A PEDIDO DEL USUARIO: mientras la ventana flotante de "Recolor" está
    // en pantalla (ver RecolorFloatingWindow, mucho más abajo en este archivo — la
    // misma condición exacta que se usa para dibujarlo), el canvas verde
    // debe aprovechar TODO el alto disponible — igual que si se hubiera
    // tocado el botón de pantalla completa — para que el personaje se vea
    // más grande, ocupando el ancho real del canvas, en vez de quedar
    // encogido al 46% de alto de siempre con la línea de tiempo ocupando
    // el resto (que en "Recolor" ya no tiene ningún panel abajo que
    // mostrar). Es una condición DERIVADA, no un `mutableStateOf` propio:
    // se recalcula sola en cada recomposición a partir de las mismas
    // variables que ya deciden si RecolorFloatingWindow se dibuja, así que
    // nunca puede desincronizarse de él.
    val recolorFloatingWindowVisible = editModeLayerId != null && selectedLayer != null &&
        editModeLayerId == selectedLayer.id && recolorWindowOpen
    // Mismo criterio que `recolorFloatingWindowVisible` de acá arriba,
    // pero para la OTRA opción de la pestaña "Color" — "Básico"
    // (Nitidez/Saturación/Brillo/Contraste/Tono, ver
    // [ColorBasicoFloatingWindow] más abajo en este archivo). A PEDIDO
    // DEL USUARIO ya NO son mutuamente excluyentes: cada una tiene su
    // propio flag (`recolorWindowOpen` / `colorBasicoWindowOpen`), así
    // que pueden estar las dos dibujadas juntas en pantalla al mismo
    // tiempo si el usuario abrió ambas.
    val colorBasicoFloatingWindowVisible = editModeLayerId != null && selectedLayer != null &&
        editModeLayerId == selectedLayer.id && colorBasicoWindowOpen
    // El botón de pantalla completa (ver más abajo, `onClick = {
    // isFullscreen = !isFullscreen }`) sigue funcionando igual, tocado a
    // mano, en cualquier pestaña — este flag combinado es solo lo que
    // decide el ALTO del canvas y si la línea de tiempo se dibuja, sin
    // pisar el estado real de `isFullscreen` (así que salir de "Recolor"
    // vuelve exactamente al tamaño que tenía antes, sin haber "activado"
    // pantalla completa de verdad).
    //
    // BUG REAL corregido acá — el que reportó el usuario con captura:
    // antes esta condición solo miraba `recolorFloatingWindowVisible` (que
    // exige, además de estar en la pestaña "Color", haber elegido de
    // verdad una opción adentro). Resultado: al
    // entrar en modo edición aislado (check/lápiz de la manija Editar) y
    // quedarse en "Color" SIN elegir nada todavía, o al pasar a "3D"/
    // "Efecto" (que nunca ponen `recolorFloatingWindowVisible` en `true`,
    // ver su definición arriba), el canvas se quedaba SIEMPRE en su alto
    // chico de siempre (`fillMaxHeight(0.46f)`, ver más abajo) — dejando
    // el timeline (pista "Master" + capas) y la barra Keyframes/Control/
    // Módulos ocupando la mitad de abajo de la pantalla, aunque ya no
    // tuvieran ninguna función real ahí (estás editando UNA imagen
    // aislada, no el proyecto entero). En "3D"/"Efecto" con una opción
    // ya elegida ese hueco quedaba disimulado por el panel opaco de
    // `LayerColorEditPanel` (ver el comentario "tapa TODO lo de abajo",
    // más abajo en este archivo) — pero el canvas de arriba seguía sin
    // crecer para aprovechar ese espacio recién liberado. Ahora
    // `editModeLayerId != null` alcanza por sí solo: estar en modo
    // edición aislado (sin importar en qué pestaña, ni si ya se eligió
    // algo adentro) siempre le da al canvas el 100% del alto disponible.
    val canvasFillsScreen = isFullscreen || recolorFloatingWindowVisible || editModeLayerId != null
    // A PEDIDO DEL USUARIO: mismo movimiento, ahora para "3D" — el texto
    // "3D" acá arriba, al lado de "Color", abre EditImage3DMenu (misma
    // ventana angosta vertical) en vez de dejar "3D" como pestaña suelta
    // en EditImageToolsHeader (que ahora queda vacía de pestañas
    // sueltas). Por ahora esa ventana tiene una sola opción ("Básico");
    // mismo criterio que "Color", deja lugar para sumar más sin
    // tocar esto de nuevo. Si el menú desplegable de "3D" está abierto
    // ahora mismo.
    var showEditImage3DMenu by remember { mutableStateOf(false) }
    // `basico3DWindowOpen` ya se declaró más arriba junto con las demás
    // ventanas (`recolorWindowOpen`, `colorBasicoWindowOpen`,
    // `effectsWindowOpen`) — mismo flag independiente, no se resalta
    // como elegido hasta que el usuario de verdad lo toca acá adentro.
    // Posición y escala que la vista de edición toma prestadas de la capa (ver EditViewSession): se devuelven
    // intactas al salir, para que en el canvas la imagen quede con el mismo tamaño y en el mismo sitio y solo se
    // vea la edición ya aplicada.
    var editViewSession by remember { mutableStateOf<EditViewSession?>(null) }
    // Fondo de la vista de edición: preferencia del usuario (tablero, negro, blanco, color del lienzo…). Solo
    // afecta a la vista previa, nunca al lienzo ni a la exportación — ver PreviewBackdrop.
    val editBackgroundState = rememberEditBackgroundState()
    var showEditBackgroundColorPicker by remember { mutableStateOf(false) }
    val editCheckerCellPx = with(LocalDensity.current) { EDIT_CHECKER_CELL_DP.dp.roundToPx() }
    val editBackdrop = remember(editBackgroundState.settings, editCheckerCellPx) {
        editBackgroundState.settings.toBackdrop(editCheckerCellPx)
    }
    // Fondo que ve el visor según el modo. UNA sola regla (EditViewportBackdrop) para el renderer y para el
    // contenedor que rodea a la superficie GL: así no puede asomar un color distinto al entrar al modo edición.
    val viewportBackdrop = EditViewportBackdrop.effective(isEditing = editModeLayerId != null, editBackdrop = editBackdrop)
    // ARREGLADO junto con `showCancelEditModeConfirm` de abajo: el
    // `sourceUri` que tenía la capa ANTES de entrar en modo edición, para
    // poder devolverla ahí si el usuario cancela con la × en vez de
    // confirmar con el check (ver `enterEditModeForSelectedLayer` y
    // `cancelEditMode`, más abajo). Antes cancelar no guardaba este dato
    // y por lo tanto no podía revertir nada — el recoloreo/3D/efecto ya
    // aplicado por `commitLayerRecolor` quedaba puesto igual.
    var editModeOriginalSourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // A pedido, mismo criterio que `showCancelReorderConfirm`: tocar la ×
    // mientras la capa está en modo edición aislado ya NO sale de una —
    // primero avisa, con un diálogo, que la edición no se va a aplicar
    // (los ajustes de Recolor/3D hechos en esta sesión de edición se
    // pierden al salir así, a diferencia del check de la manija Editar,
    // que sí confirma). El usuario recién sale del modo edición si
    // confirma "Sí, salir"; si toca "No", sigue editando tal cual estaba.
    var showCancelEditModeConfirm by remember { mutableStateOf(false) }
    // BUG REAL corregido acá — el que reportó el usuario: el botón "←"
    // de la barra superior (más abajo, `onClick = { viewModel.saveNow {
    // onBackToProjects() } }`) navegaba SIEMPRE directo a "Mis proyectos"
    // sin importar en qué estaba la pantalla — ni siquiera revisaba si
    // había una capa en modo edición aislado ([editModeLayerId]) abierta
    // encima. Resultado: estando en "modo editar" (el panel Recolor/3D/
    // Efectos de una capa aislada), tocar "←" saltaba TODO —ni siquiera
    // pasaba primero por la pantalla base del proyecto (el timeline con
    // Keyframes/Control/Módulos)— e iba directo a la lista de proyectos,
    // como si "atrás" y "salir del proyecto entero" fueran la misma
    // acción. Ahora ese botón primero revisa `editModeLayerId`: si hay
    // una capa en edición aislada, "←" cierra ESE panel (vuelve a la
    // pantalla base del proyecto, sin abandonarlo) en vez de saltar
    // directo afuera — y si hubo cambios reales en esta sesión, este
    // flag dispara el diálogo de confirmación de abajo antes de cerrar.
    var showBackDuringEditModeConfirm by remember { mutableStateOf(false) }
    // A PEDIDO DEL USUARIO — antes, tocar "←" (o el gesto/botón físico de
    // atrás) estando en la pantalla BASE del proyecto (sin ninguna capa
    // en modo edición aislado encima) guardaba y salía directo a "Mis
    // proyectos" sin preguntar nada — ver `viewModel.saveNow {
    // onBackToProjects() }`, más abajo. Ahora, si hubo cambios reales en
    // esta sesión (`viewModel.hasUnsavedChanges()`), primero aparece este
    // diálogo — tipo aviso profesional, tres opciones claras: "Guardar y
    // salir", "Salir sin guardar" (descarta lo hecho en esta sesión,
    // vuelve el proyecto a como estaba al abrirlo) o "Cancelar" (se queda
    // editando). Si NO hubo cambios, sale directo sin molestar con una
    // pregunta que no tendría sentido.
    var showExitSaveConfirm by remember { mutableStateOf(false) }

    // Puente hacia Extrude3DPanel — ver [Extrude3DGestureBridge]. Un solo
    // objeto estable durante toda la vida de la pantalla (no depende de
    // layer.id ni de nada): quien cambia es qué callbacks tiene adentro,
    // no el objeto en sí.
    val extrude3DBridge = remember { Extrude3DGestureBridge() }
    // Puente hacia DistortionPanel — ver [DistortionGestureBridge].
    val distortionBridge = remember { DistortionGestureBridge() }

    // Sale del modo edición y devuelve la capa a su posición original.
    // Usar SOLO desde la rama de CONFIRMAR (check de la manija Editar):
    // no toca el bitmap/sourceUri de la capa, porque el recoloreo/3D/
    // efecto de esta sesión ya quedó escrito a disco por
    // `commitLayerRecolor` durante la edición — confirmar es simplemente
    // "dejar lo que ya está aplicado y volver al canvas principal".
    fun exitEditMode() {
        val session = editViewSession
        if (session != null) {
            translateX = session.originalTranslateX
            translateY = session.originalTranslateY
            scale = session.originalScale
        }
        editModeLayerId = null
        editViewSession = null
        editModeOriginalSourceUri = null
    }

    // Atrás del sistema (gesto o botón físico de Android — mecanismo
    // TOTALMENTE SEPARADO del IconButton "←" de la barra superior, que
    // tiene su propio onClick más abajo): mismo bug que el usuario ya
    // había reportado para ese botón, y que se arregló ahí — pero no
    // acá. Este BackHandler solo revisaba `isFullscreen`, sin mirar para
    // nada `editModeLayerId`, así que el gesto/botón de atrás del sistema
    // seguía saltando SIEMPRE directo a "Mis proyectos" aunque hubiera
    // una capa en modo edición aislado abierta encima — el mismo salto
    // de pantalla que "se come" la vista base del proyecto, solo que por
    // esta otra puerta en vez de por el botón visual. Ahora sigue
    // exactamente la misma lógica que el botón "←" (ver su comentario
    // grande, más abajo, junto a `showBackDuringEditModeConfirm`): si hay
    // una capa en edición aislada, primero cierra ESE panel (con el mismo
    // diálogo de confirmación si hubo cambios reales) en vez de salir del
    // proyecto. Va DESPUÉS de `exitEditMode()` (y no donde estaba antes,
    // más arriba en el archivo) porque la necesita, junto con
    // `editModeLayerId`/`editModeOriginalSourceUri`/
    // `showBackDuringEditModeConfirm` — Kotlin no permite referenciar en
    // un cuerpo de función una variable/función local declarada más
    // adelante en ese mismo cuerpo, ni siquiera desde dentro de un lambda.
    GuardedBackHandler(enabled = true) {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            val layerBeingEdited = editModeLayerId
            if (layerBeingEdited != null) {
                val currentUri = state.layers.find { it.id == layerBeingEdited }?.sourceUri
                val hasChanges = currentUri != editModeOriginalSourceUri
                if (hasChanges) {
                    showBackDuringEditModeConfirm = true
                } else {
                    exitEditMode()
                }
            } else if (viewModel.hasUnsavedChanges()) {
                showExitSaveConfirm = true
            } else {
                viewModel.saveNow { onBackToProjects() }
            }
        }
    }

    // ARREGLADO A PEDIDO: cancelar (×) ahora sí descarta lo hecho en esta
    // sesión de edición, en vez de compartir la misma función que
    // confirmar (ver el comentario grande en `LayerHandleRole.DELETE`,
    // más abajo, y en `EditorViewModel.revertLayerEditSession`). Devuelve
    // la capa a su `sourceUri` original ANTES de reponer la posición y
    // cerrar el modo edición, para que el diálogo "los cambios no se van
    // a aplicar" sea verdad.
    fun cancelEditMode() {
        val layerId = editModeLayerId
        val originalUri = editModeOriginalSourceUri
        if (layerId != null && originalUri != null) {
            viewModel.revertLayerEditSession(layerId, originalUri)
        }
        exitEditMode()
    }

    // Entra en modo edición para la capa actualmente seleccionada:
    // guarda su posición real, la centra en el canvas (0,0) y activa el
    // aislamiento (solo esa capa visible).
    //
    // BUG REAL ENCONTRADO: esta función se llama desde el gesto de la
    // manija "Editar" dentro de un `Modifier.pointerInput(Unit) { ... }`
    // (ver más abajo, dentro de `hitsRole(LayerHandleRole.EDIT)`). Como
    // ese pointerInput usa `Unit` como key, su corrutina se lanza UNA
    // sola vez y nunca se vuelve a crear mientras la pantalla siga
    // montada — así que el lambda que llama a esta función quedó
    // "congelado" apuntando a la versión de esta función (y de lo que
    // cerraba) que existía en el primer render. Antes, esta función
    // leía `selectedLayer` (el `val` de arriba, NO protegido con
    // `rememberUpdatedState`), y como ese `val` no se actualiza dentro
    // de un closure viejo, terminaba usando SIEMPRE la capa que estaba
    // seleccionada en el momento en que la pantalla se montó, sin
    // importar qué capa esté seleccionada ahora. Resultado: el botón
    // "Editar" tocaba una capa vieja/inexistente y el modo edición se
    // cerraba solo de inmediato (o no pasaba nada si en ese momento no
    // había ninguna capa seleccionada) — el bug de "hay que salir y
    // volver a entrar para que funcione" que reportó el usuario, porque
    // reentrar recompone todo de cero y relanza el pointerInput con un
    // `selectedLayer` fresco.
    //
    // Arreglo: recibir la capa como parámetro en vez de leerla del
    // closure. El único call site (la manija "Editar") ya tiene a mano
    // una versión SIEMPRE fresca vía `latestSelectedLayerForDrag`
    // (`rememberUpdatedState`), así que se le pasa esa en vez de
    // depender de este `selectedLayer` desactualizado.
    fun enterEditModeForSelectedLayer(sel: Layer?) {
        val layer = sel ?: return
        // Valores de origen de la vista de edición: se capturan ANTES de tocar nada para devolverlos al salir.
        val session = EditViewSession(translateX, translateY, scale)
        editModeOriginalSourceUri = layer.sourceUri
        // A PEDIDO DEL USUARIO: guarda el checkpoint de Deshacer/Rehacer
        // "de proyecto" ACÁ, ANTES de aislar la capa — ver el comentario
        // grande en EditorViewModel.beginLayerEditSession. Tiene que ser
        // el primer paso, antes de que cualquier slider de Recolor/3D/
        // Efectos/Distorsión toque `sourceUri`, para que el snapshot
        // capture la imagen ORIGINAL.
        viewModel.beginLayerEditSession(layer.id)
        translateX = 0f
        translateY = 0f
        // Ajuste provisional (imagen "contain" al 90 % del visor, corregida por la profundidad de la capa): evita
        // un frame con la imagen a su tamaño de proyecto mientras el visor, que pasa a ocupar toda la pantalla,
        // aún no se midió. El valor exacto (con estiramiento y rotación) lo aplica el efecto de ajuste al
        // conocer el tamaño real del visor.
        val provisionalFit = EditViewFit.provisionalScale(layer.parallaxFactor)
        scale = provisionalFit
        session.markFitApplied(provisionalFit)
        editViewSession = session
        editModeLayerId = layer.id
    }

    // Cambiar de capa o deshacer/rehacer siempre resincroniza, incluso si
    // en ese instante se está grabando (son ediciones explícitas del
    // usuario, no el mero paso del tiempo).
    LaunchedEffect(state.selectedLayerId, state.undoRedoTick) {
        // Selección de capa y de carril de audio son excluyentes: si el ViewModel dejó una capa seleccionada
        // (p. ej. deshacer/rehacer restaura `selectedLayerId`), el carril de audio deja de estar seleccionado.
        if (state.selectedLayerId != null) selectedAudioTrackId = null
        // Mismo criterio para el modo edición dedicado: es por-capa, así
        // que cambiar de selección (desde el timeline, undo/redo, etc.)
        // lo cierra en vez de dejarlo aislando una capa que ya no está
        // seleccionada. Va ANTES de resincronizar: si la selección cambió,
        // la posición/escala locales de la vista de edición no valen para
        // la capa nueva y deben reponerse desde el modelo.
        if (editModeLayerId != null && editModeLayerId != state.selectedLayerId) {
            editModeLayerId = null
            editViewSession = null
            editModeOriginalSourceUri = null
        }
        syncSlidersFromModel(keepEditViewTransform = editModeLayerId != null)
        // Mismo criterio para el modo reordenar manijas: es por-capa
        // (el "alcance aquí" apunta a la capa que estaba seleccionada
        // cuando se abrió), así que cambiar de selección lo cierra sin
        // aplicar nada, en vez de dejarlo reordenando a ciegas una capa
        // que ya no es la que se ve en pantalla.
        showReorderScopeMenu = false
        reorderMode = false
        reorderScope = null
        reorderDraftOrder = null
        reorderDragFromPosition = null
    }

    // --- ARREGLADO: "selecciono una capa y de inmediato otra, por una
    // fracción de segundo se ve algo raro (un resto de la capa anterior)
    // que desaparece al terminar de cargar la capa seleccionada" — la
    // causa era que seleccionar desde el timeline o el panel de capas
    // solo llamaba a viewModel.selectLayer(id) y dejaba que el
    // LaunchedEffect de arriba resincronizara translateX/Y/scale/etc.
    // LaunchedEffect corre en una corrutina que arranca DESPUÉS de que
    // esta composición ya se dibujó — así que había SIEMPRE un frame de
    // por medio donde selectedLayer ya era la capa NUEVA pero
    // translateX/Y/scale/rotation todavía tenían los valores de la capa
    // VIEJA: el marco de selección y el override en vivo del GLPreview
    // (que combina el id de la capa ya seleccionada con esos valores
    // viejos) dibujaban la capa nueva en la posición/escala de la
    // anterior durante ese único frame — el "flash" reportado. Este
    // wrapper hace exactamente lo mismo que ya hacía el tap directo sobre
    // el preview (más arriba en este archivo): selecciona Y resincroniza
    // en el mismo tramo síncrono, sin esperar a la corrutina.
    fun selectLayerAndSync(layerId: String) {
        selectedAudioTrackId = null
        viewModel.selectLayer(layerId)
        syncSlidersFromModel(keepEditViewTransform = editModeLayerId == layerId)
    }
    // El scrubbing manual del playhead (arrastrar el scrubber o tocar el
    // timeline) resincroniza los sliders SOLO cuando no se está grabando
    // — durante la grabación el playhead avanza solo, 16ms en 16ms, y
    // resincronizar en cada tick reiniciaría el gesto en curso.
    LaunchedEffect(state.playheadMs) {
        if (!state.isRecording) syncSlidersFromModel(keepEditViewTransform = editModeLayerId != null)
    }
    // --- Guías de composición. Modelo GridShape (qué FORMA: rectángulo,
    // diagonal ↗, diagonal ↖, diagonal cruzada, redondo) + GridSpec
    // (columnas/filas, independientes entre sí). Ambos se guardan
    // SIEMPRE el último ajuste elegido — aunque el usuario apague la
    // cuadrícula, la forma y la densidad personalizadas quedan
    // recordadas para la próxima vez que la prenda, igual que Photoshop
    // o Figma recuerdan el espaciado de grilla. `gridEnabled` es el
    // on/off real, separado del valor — así el botón de arriba solo
    // abre/cierra el menú, y activar la cuadrícula pasa por elegir una
    // forma, tocar el switch, o mover cualquiera de los steppers.
    // BUG REAL corregido acá: estos 5 ajustes vivían solo como
    // `remember { mutableStateOf(...) }` — estado puro de composición,
    // nunca pasaba por el ViewModel ni se guardaba en project.json. Por
    // eso, al salir del proyecto (esta composable se descarta) y volver a
    // entrar (se crea una instancia nueva), la cuadrícula volvía siempre
    // a los defaults aunque el usuario la hubiera activado y ajustado.
    // Ahora se leen de `state` (restaurado desde disco al abrir el
    // proyecto, ver EditorViewModel) y cualquier cambio se manda de
    // vuelta con `viewModel.updateGridSettings(...)`, que ya dispara el
    // autoguardado — igual que el resto de los ajustes persistentes del
    // panel "Información del proyecto".
    val gridShape = remember(state.gridShapeName) {
        runCatching { GridShape.valueOf(state.gridShapeName) }.getOrDefault(GridShape.RECTANGLE)
    }
    val gridSpec = GridSpec(state.gridColumns, state.gridRows)
    val gridEnabled = state.gridEnabled
    val gridLineColorEnabled = state.gridLineColorEnabled
    val gridLineHue = state.gridLineHue
    val gridLineThicknessDp = state.gridLineThicknessDp
    val gridLineOpacity = state.gridLineOpacity
    val gridSnapEnabled = state.gridSnapEnabled
    var showGridMenu by remember { mutableStateOf(false) }
    // Instante (epoch ms) en que el Popup se cerró por última vez SOLO.
    // Existe por un problema real de Compose: cuando el usuario toca
    // afuera del Popup para cerrarlo, y ese "afuera" es justo el mismo
    // ícono que lo abrió, el toque dispara DOS cosas en la misma pasada:
    // el auto-dismiss del Popup (onDismissRequest) Y el onClick del
    // IconButton que está debajo — el resultado, sin este guard, es que
    // el menú se cierra y se vuelve a abrir en el mismo instante, y se ve
    // como si "no cerrara nunca" tocando el ícono. El guard: si el
    // Popup se acaba de auto-cerrar hace menos de 200ms, un click del
    // ícono que intente REABRIR se ignora esa única vez — pero cerrar
    // (cuando ya está abierto) nunca se bloquea, así que el ícono
    // siempre funciona como toggle real, comportamiento estándar en
    // cualquier app premium.
    var gridMenuAutoDismissedAtMs by remember { mutableStateOf(0L) }

    // --- Menú "Edición" (al lado del ícono Grabar, barra superior) ---
    // Mismo patrón toggle + guard de auto-dismiss que showGridMenu de
    // arriba: sin el guard, tocar el texto "Edición" para CERRAR el
    // Popup dispara también su propio onClick en la misma pasada (el
    // toque "afuera" que el Popup detecta es justo ese mismo texto) y
    // el menú parece no cerrarse nunca.
    var showEdicionMenu by remember { mutableStateOf(false) }
    var edicionMenuAutoDismissedAtMs by remember { mutableStateOf(0L) }
    // Estado de la única opción del menú por ahora ("Imagen"). Vive acá
    // (no en el ViewModel) porque todavía no dispara ningún efecto real
    // sobre el proyecto — es la casilla en sí, lista para que una
    // próxima actualización la conecte a lo que deba activar.
    var edicionImagenChecked by remember { mutableStateOf(false) }
    // Guarda un keyframe en el instante actual SOLO mientras el modo
    // Grabar está activo (state.isRecording) — igual que cualquier editor
    // profesional de cámara: fuera de grabación, mover la imagen o los
    // sliders NUNCA crea ni toca un keyframe. En cambio, actualiza la pose
    // ESTÁTICA de la capa (CameraTrack.baseFrame) — un concepto totalmente
    // aparte de la animación, que no aparece en la pista de keyframes y
    // que CameraTrack.frameAt() solo usa cuando la capa no tiene ninguna
    // animación armada. Así:
    //  - Capa sin animación: mover/ajustar queda guardado de verdad (no se
    //    pierde al cambiar de capa ni al cerrar el proyecto), sin que
    //    exista NINGÚN keyframe.
    //  - Capa YA animada con Grabar: seguís pudiendo "ensayar" el encuadre
    //    libremente sin grabar — como baseFrame se ignora en cuanto hay
    //    keyframes, tocarlo no tiene ningún efecto visual una vez que
    //    volvés a esa capa, y la animación existente queda intacta.
    fun commitLiveFrame() {
        // OJO: NO usar el `selectedLayer` de más arriba acá — ese es un
        // val "congelado" en el momento en que se compuso esta función,
        // y como el pointerInput(Unit) del preview lanza su corrutina UNA
        // sola vez y la mantiene corriendo para siempre (Compose nunca la
        // reinicia porque su key nunca cambia), la clausura que llega a
        // ejecutar commitLiveFrame() queda anclada a la capa que estaba
        // seleccionada la PRIMERA vez que se compuso la pantalla — sin
        // importar cuántas capas distintas selecciones después. Ese era
        // el bug real: mover una capa, seleccionar otra y arrastrarla en
        // realidad seguía escribiendo la posición sobre la primera capa
        // (que no se veía moverse porque estaba fuera de donde mirabas).
        // viewModel.currentSelectedLayer() lee el estado ACTUAL directo
        // del ViewModel en cada llamada, así que siempre apunta a la capa
        // realmente seleccionada en ese instante.
        val layer = viewModel.currentSelectedLayer() ?: return
        if (layer.locked) return
        // Dentro del modo edición, mover o hacer zoom a la imagen es un cambio de VISTA (para mirarla de cerca),
        // no un cambio de la capa: nunca se escribe al modelo ni se graba como keyframe. Al salir, la capa
        // conserva el tamaño y la posición que tenía en el canvas.
        if (editModeLayerId != null) return
        if (state.isRecording) {
            viewModel.addKeyframeToSelectedLayer(
                translateX, translateY, scale, rotation, alpha, tiltX, tiltY, focusBlur, dollyZoom, scaleX, scaleY, EasingType.EASE_IN_OUT
            )
        } else {
            viewModel.updateBaseFrameForSelectedLayer(
                translateX, translateY, scale, rotation, alpha, tiltX, tiltY, focusBlur, dollyZoom, scaleX, scaleY
            )
        }
    }

    // --- FASE 1 — joystick de Control conectado a movimiento real. -----
    // Toda la física de "vector del stick -> translateX/Y por frame" vive
    // ahora en JoystickLayerMover.kt, desacoplada de esta pantalla — acá
    // solo se conecta ese motor genérico con el estado puntual de ESTA
    // capa/pantalla (translateX/Y, el zoom vigente para el panLimit, si
    // hay capa editable, y cómo confirmar un keyframe). Ver el comentario
    // grande en JoystickLayerMover.kt para el detalle de qué resuelve cada
    // pieza y por qué está separada así.
    val joystickMover = rememberJoystickLayerMover(
        getTranslateX = { translateX },
        setTranslateX = { translateX = it },
        getTranslateY = { translateY },
        setTranslateY = { translateY = it },
        // Mismo `panLimit` proporcional al zoom que ya usan el paneo
        // táctil (línea ~3142) y el slider "Pan X/Y" — así el joystick
        // nunca puede empujar la capa más allá de donde el resto de los
        // gestos la dejarían llegar.
        panLimit = { 2f * maxOf(scale, scaleX, scaleY, 1f) },
        layerIsEditable = { viewModel.currentSelectedLayer()?.locked == false },
        commitFrame = { commitLiveFrame() }
    )

    // NOTA importante sobre el modo grabar: NO hay un temporizador que
    // capture keyframes cada cierto tiempo. El botón de grabar solo
    // "arma" el estado (círculo rojo) y hace avanzar el playhead solo;
    // el keyframe real únicamente se escribe cuando el usuario provoca
    // un cambio de verdad — un gesto sobre el preview (ver
    // pointerInput -> commitLiveFrame() más abajo) o mover un slider de
    // cámara (ver LabeledSlider -> commitLiveFrame() en la sección de
    // Cámara) — Y SOLO si el modo Grabar está activo. Con Grabar apagado,
    // esos mismos gestos y sliders siguen moviendo el preview con total
    // libertad (para ensayar), pero no tocan ningún keyframe.

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Color.White,
        topBar = {
            // Barra superior CUSTOM (no el TopAppBar estándar de Material3):
            // se necesitan tres zonas alineadas contra el ANCHO TOTAL de la
            // pantalla — nombre a la izquierda, Grabar+Play en el centro
            // real de toda la barra (no solo del espacio libre entre
            // navigationIcon y actions, que es donde un TopAppBar de
            // Material3 centraría su title), y undo/redo/exportar a la
            // derecha. Un Box con fillMaxWidth() + Modifier.align() por
            // zona es la única forma de lograr ese centrado verdadero.
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // API 35+/36: borde a borde obligatorio. El `Surface` (con su color) se extiende
                        // DETRÁS de la barra de estado y este padding baja el contenido de la barra por
                        // debajo de ella (y del recorte/notch y de las barras laterales en horizontal).
                        // Va ANTES de `heightIn`: la altura mínima de 64 dp es la de la barra, no la de
                        // la barra + el inset. Scaffold mide la `topBar` ya con este padding, así que el
                        // contenido de abajo no necesita ajuste.
                        .windowInsetsPadding(
                            WindowInsets.systemBars
                                .union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                        )
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    // --- Izquierda: atrás + nombre del proyecto ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        IconButton(onClick = {
                            // Ver el comentario grande junto a
                            // `showBackDuringEditModeConfirm` (arriba, cerca
                            // de `editModeLayerId`) sobre el bug real que
                            // esto corrige: antes SIEMPRE navegaba a "Mis
                            // proyectos" sin mirar en qué estaba la
                            // pantalla.
                            val layerBeingEdited = editModeLayerId
                            if (layerBeingEdited != null) {
                                // Estamos en modo edición aislado (Recolor/
                                // 3D/Efectos de una sola capa) — "←" cierra
                                // ESE panel primero, nunca salta directo a
                                // Mis proyectos. Compara el sourceUri actual
                                // de la capa contra el que tenía ANTES de
                                // entrar (`editModeOriginalSourceUri`) para
                                // saber si de verdad hubo cambios en esta
                                // sesión — Recolor, 3D Y Efectos pasan los
                                // tres por `commitLayerRecolor`, que
                                // reescribe ese sourceUri en disco, así que
                                // esta comparación cubre cualquier ajuste
                                // real hecho en cualquiera de las tres
                                // pestañas, no solo una.
                                val currentUri = state.layers.find { it.id == layerBeingEdited }?.sourceUri
                                val hasChanges = currentUri != editModeOriginalSourceUri
                                if (hasChanges) {
                                    showBackDuringEditModeConfirm = true
                                } else {
                                    // Nada cambió en esta sesión — sale
                                    // directo, sin preguntar nada, a la
                                    // pantalla base del proyecto.
                                    exitEditMode()
                                }
                            } else if (viewModel.hasUnsavedChanges()) {
                                // Ya estábamos en la pantalla base del
                                // proyecto (sin ninguna capa en modo
                                // edición aislado abierta encima) y hubo
                                // cambios reales en esta sesión — antes de
                                // saltar a Mis proyectos, se pregunta.
                                showExitSaveConfirm = true
                            } else {
                                // Sin cambios reales desde que se abrió el
                                // proyecto — sale directo, sin preguntar
                                // nada de más.
                                viewModel.saveNow { onBackToProjects() }
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back),
                                contentDescription = "Volver"
                            )
                        }
                        Column(
                            modifier = Modifier.clickable { showRenameDialog = true }
                        ) {
                            Text(
                                state.projectName,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            SaveStatusLabel(state.saveState)
                        }

                        // --- "Color": A PEDIDO DEL USUARIO, mismo
                        // movimiento que "Efecto" (ver comentario de acá
                        // abajo) pero para "Recolor" — antes era una
                        // pestaña más de EditImageToolsHeader, ahora
                        // vive acá arriba, al lado de "Efecto". Tocarlo
                        // despliega EditImageColorMenu (misma ventana
                        // angosta vertical que EditImageEffectsMenu), con
                        // "Básico" y "Recolor" como opciones — lugar
                        // ya preparado para sumar más sin tocar este
                        // bloque de nuevo.
                        if (editModeLayerId != null) {
                            Spacer(modifier = Modifier.width(14.dp))
                            Box {
                                // A PEDIDO DEL USUARIO — MULTI-VENTANA:
                                // "Color" se resalta (y se "pinta" de
                                // morado con el barrido izq→der de
                                // [PurpleSweepTabLabel]) si el menú está
                                // abierto O si CUALQUIERA de sus dos
                                // ventanas (Recolor / Básico) está
                                // abierta ahora mismo — las dos pueden
                                // estarlo a la vez, así que ya no hace
                                // falta comparar contra una única
                                // "pestaña activa".
                                PurpleSweepTabLabel(
                                    text = "Color",
                                    isActive = showEditImageColorMenu || recolorWindowOpen || colorBasicoWindowOpen,
                                    onClick = { showEditImageColorMenu = !showEditImageColorMenu }
                                )
                                // A PEDIDO DEL USUARIO: la ventana ya NO se
                                // monta/desmonta de golpe con este `if` —
                                // [EditImageColorMenu] se llama siempre y
                                // es ella la que decide, vía
                                // [AnimatedDropdownPopup], cuándo animar su
                                // apertura/cierre "desde el pie" de este
                                // mismo texto "Color".
                                EditImageColorMenu(
                                    visible = showEditImageColorMenu,
                                    // A PEDIDO DEL USUARIO — MULTI-VENTANA:
                                    // ambos ítems se pintan como
                                    // "elegidos" a la vez si el usuario
                                    // abrió las dos ventanas — por eso
                                    // `selected` ahora es un `Set`, no un
                                    // único valor.
                                    selected = buildSet {
                                        if (recolorWindowOpen) add(0)
                                        if (colorBasicoWindowOpen) add(1)
                                    },
                                    // Tocar una opción del menú ABRE esa
                                    // ventana (si ya estaba abierta, no
                                    // hace nada — cerrarla es trabajo de
                                    // la × de la propia ventana, no de
                                    // este menú) SIN tocar el estado de
                                    // la otra opción: así "Básico" y
                                    // "Recolor" pueden coexistir abiertas
                                    // al mismo tiempo, tal como pidió el
                                    // usuario.
                                    onSelect = { option ->
                                        if (option == 0) {
                                            recolorWindowOpen = true
                                        } else {
                                            colorBasicoWindowOpen = true
                                        }
                                    },
                                    onDismiss = { showEditImageColorMenu = false }
                                )
                            }
                        }

                        // --- "3D": mismo movimiento que "Color" y
                        // "Efecto" — antes era la otra pestaña suelta de
                        // EditImageToolsHeader, ahora vive acá arriba,
                        // entre "Color" y "Efecto". Tocarlo despliega
                        // EditImage3DMenu (misma ventana angosta vertical),
                        // con "Básico" como única opción por ahora —
                        // mismo criterio que "Color": lugar ya preparado
                        // para sumar más sin tocar este bloque de nuevo.
                        if (editModeLayerId != null) {
                            Spacer(modifier = Modifier.width(14.dp))
                            Box {
                                // A PEDIDO DEL USUARIO — MULTI-VENTANA:
                                // "3D" se resalta (con el mismo barrido
                                // morado izq→der) si su ventana está
                                // abierta, sin importar si "Color" o
                                // "Efecto" también lo están — ya no son
                                // excluyentes entre sí.
                                PurpleSweepTabLabel(
                                    text = "3D",
                                    isActive = showEditImage3DMenu || basico3DWindowOpen,
                                    onClick = { showEditImage3DMenu = !showEditImage3DMenu }
                                )
                                // Se llama siempre (ver comentario en el
                                // bloque de "Color" de acá arriba) — es
                                // [EditImage3DMenu] quien anima su propia
                                // apertura/cierre "desde el pie" de "3D".
                                EditImage3DMenu(
                                    visible = showEditImage3DMenu,
                                    // "Básico" se pinta como elegido acá
                                    // si su ventana flotante está
                                    // abierta ahora mismo.
                                    selected = if (basico3DWindowOpen) 0 else null,
                                    onSelect = {
                                        basico3DWindowOpen = true
                                    },
                                    onDismiss = { showEditImage3DMenu = false }
                                )
                            }
                        }

                        // --- "Efecto": A PEDIDO DEL USUARIO, movido acá
                        // arriba desde la fila de pestañas de abajo
                        // (Recolor/3D/Efectos, ver EditImageToolsHeader) —
                        // solo tiene sentido, y solo se muestra, DENTRO del
                        // modo edición de imagen aislada de una capa
                        // (editModeLayerId != null). Tocarlo despliega
                        // EditImageEffectsMenu (ventana angosta vertical)
                        // en vez de la fila de chips de antes — ese menú
                        // decide la CATEGORÍA (Contorno/Resplandor/Sombra/
                        // Reflejo/Distorsión); los sliders de cada una
                        // siguen exactamente donde estaban, abajo del
                        // lienzo, sin mover ni un control.
                        if (editModeLayerId != null) {
                            Spacer(modifier = Modifier.width(14.dp))
                            Box {
                                // A PEDIDO DEL USUARIO — MULTI-VENTANA:
                                // "Efecto" se resalta (con el mismo
                                // barrido morado izq→der) si su panel
                                // está abierto, sin importar si "Color" o
                                // "3D" también lo están.
                                PurpleSweepTabLabel(
                                    text = "Efecto",
                                    isActive = showEditImageEffectsMenu || effectsWindowOpen ||
                                        contornoWindowOpen || resplandorWindowOpen || sombraWindowOpen ||
                                        reflejoWindowOpen || distortionWindowOpen,
                                    // A PEDIDO DEL USUARIO — MULTI-VENTANA:
                                    // "Efecto" se resalta si CUALQUIERA de
                                    // sus ventanas está abierta ahora mismo
                                    // — la compartida (que a esta altura no
                                    // cubre NINGUNA categoría ofrecida por
                                    // este menú — solo Fondo/Color/Presets,
                                    // que se acceden por otro lado) o las
                                    // dedicadas de "Contorno"/"Resplandor"/
                                    // "Sombra"/"Reflejo"/"Distorsión" —
                                    // mismo criterio que ya usa "Color" con
                                    // Recolor/Básico.
                                    onClick = { showEditImageEffectsMenu = !showEditImageEffectsMenu }
                                )
                                // Se llama siempre (ver comentario en el
                                // bloque de "Color" de más arriba) — es
                                // [EditImageEffectsMenu] quien anima su
                                // propia apertura/cierre "desde el pie"
                                // de "Efecto".
                                EditImageEffectsMenu(
                                    visible = showEditImageEffectsMenu,
                                    // A PEDIDO DEL USUARIO — MULTI-VENTANA:
                                    // "Contorno", "Resplandor", "Sombra",
                                    // "Reflejo" y "Distorsión" se pintan
                                    // como elegidas según SU PROPIO flag de
                                    // ventana, no según la ventana
                                    // compartida — a esta altura, esa
                                    // compartida ya no cubre NINGUNA
                                    // categoría que este menú ofrezca
                                    // (Fondo/Color/Presets se acceden por
                                    // otro lado, sin pasar por acá).
                                    selected = buildSet {
                                        if (contornoWindowOpen) add(2)
                                        if (resplandorWindowOpen) add(3)
                                        if (sombraWindowOpen) add(4)
                                        if (reflejoWindowOpen) add(5)
                                        if (distortionWindowOpen) add(7)
                                        if (editImageEffectsCategoryChosen && effectsWindowOpen) {
                                            add(editImageEffectsCategory)
                                        }
                                    },
                                    // Tocar "Contorno"/"Resplandor"/
                                    // "Sombra"/"Reflejo"/"Distorsión" ABRE
                                    // su propia ventana dedicada, sin tocar
                                    // la compartida — así pueden convivir
                                    // las seis abiertas a la vez. La
                                    // compartida (Fondo/Color/Presets) ya
                                    // no se accede desde este menú en
                                    // absoluto — se dejó su rama (el
                                    // `else` de más abajo) por si en algún
                                    // momento alguna de esas tres necesita
                                    // volver a ofrecerse acá.
                                    onSelect = { index ->
                                        when (index) {
                                            2 -> contornoWindowOpen = true
                                            3 -> resplandorWindowOpen = true
                                            4 -> sombraWindowOpen = true
                                            5 -> reflejoWindowOpen = true
                                            7 -> distortionWindowOpen = true
                                            else -> {
                                                editImageEffectsCategory = index
                                                editImageEffectsCategoryChosen = true
                                                effectsWindowOpen = true
                                            }
                                        }
                                    },
                                    onDismiss = { showEditImageEffectsMenu = false }
                                )
                            }
                        }
                    }

                    // --- Centro real de la barra: Grabar + Play/Pausa ---
                    // OCULTO por completo en modo edición de imagen aislada
                    // (editModeLayerId != null, panel Recolor/3D/Efectos):
                    // "Edición" + su menú "Imagen", el círculo de Grabar,
                    // "Volver al principio" y Play/Pausa no aplican dentro
                    // de ese modo — se dejan libres para las próximas
                    // opciones de edición de imagen que vayan acá. Nada de
                    // esto se borró: vuelve a aparecer tal cual al salir
                    // del modo edición (editModeLayerId == null).
                    if (editModeLayerId == null) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        // --- "Edición": texto ancla del menú premium con
                        // la opción "Imagen" + su casilla. Envuelto en Box
                        // por la misma razón que el ícono de cuadrícula
                        // más abajo — el Popup necesita un ancla cuyas
                        // coordenadas en pantalla usar para aparecer justo
                        // debajo, centrado. Va ANTES del ícono Grabar (a
                        // su izquierda), tal como en la referencia.
                        Box {
                            Text(
                                "Edición",
                                color = if (showEdicionMenu) BrandPurpleLight else Color.White,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (showEdicionMenu) {
                                            showEdicionMenu = false
                                        } else {
                                            val now = System.currentTimeMillis()
                                            if (now - edicionMenuAutoDismissedAtMs > 200) {
                                                showEdicionMenu = true
                                            }
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                            if (showEdicionMenu) {
                                EdicionMenu(
                                    imagenChecked = edicionImagenChecked,
                                    onImagenToggle = {
                                        edicionImagenChecked = !edicionImagenChecked
                                        // Apagar "Edición > Imagen" oculta las 7 manijas + la de
                                        // reordenar (ver Canvas de dibujo) — si el modo reordenar
                                        // seguía activo, se cierra también, para no dejar el
                                        // canvas "congelado" sin ninguna manija visible con la
                                        // que salir de ese modo.
                                        if (!edicionImagenChecked) {
                                            showReorderScopeMenu = false
                                            reorderMode = false
                                            reorderScope = null
                                            reorderDraftOrder = null
                                            reorderDragFromPosition = null
                                        }
                                    },
                                    onDismiss = {
                                        showEdicionMenu = false
                                        edicionMenuAutoDismissedAtMs = System.currentTimeMillis()
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = { viewModel.toggleRecording() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isCapturing) {
                                        Color(0xFFFF3B30).copy(alpha = recordGlow)
                                    } else {
                                        Color.White.copy(alpha = 0.12f)
                                    }
                                )
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (state.isRecording) R.drawable.ic_record_active else R.drawable.ic_record_idle
                                ),
                                contentDescription = when {
                                    state.isCapturing -> "Detener grabación (grabando)"
                                    state.isRecording -> "Detener grabación (en espera)"
                                    else -> "Grabar movimiento de cámara"
                                },
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(20.dp)
                                    .alpha(if (state.isCapturing) recordBlink else 1f)
                            )
                        }

                        // ARREGLADO: se veía más pegado a "Volver al
                        // principio" que este a "Reproducir" — ambos
                        // Spacers medían 16dp en código, pero el círculo
                        // de Grabar/Retroceder es translúcido (apenas se
                        // nota su borde) mientras que el de Play es
                        // sólido y bien visible, así que a simple vista el
                        // hueco Grabar↔Retroceder se sentía más chico.
                        // 4dp extra acá empareja la sensación visual con
                        // el otro hueco, que ya estaba bien.
                        Spacer(modifier = Modifier.width(20.dp))

                        IconButton(
                            onClick = { viewModel.resetPlaybackState() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_skip_to_start),
                                contentDescription = "Volver al principio",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = { viewModel.togglePlayback() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                painter = painterResource(id = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    }

                    // --- Derecha: deshacer / rehacer / exportar ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        // --- Información del proyecto: título, sinopsis,
                        // créditos, etc. — como la descripción de un video
                        // de YouTube, pero del proyecto entero. Por ahora
                        // solo abre el panel vacío (ver ProjectInfoPanel en
                        // EditorBottomBar.kt); los campos del formulario se
                        // van armando en próximas actualizaciones. A
                        // propósito a la IZQUIERDA de la cuadrícula, como
                        // pediste.
                        //
                        // Igual que el grupo Edición/Grabar/Play de arriba:
                        // portapapeles (Información del proyecto) y
                        // cuadrícula quedan OCULTOS en modo edición de
                        // imagen aislada — no aplican ahí y dejan el hueco
                        // libre para las próximas opciones de ese modo.
                        // Deshacer/Rehacer/Exportar, más abajo, siguen
                        // siempre visibles.
                        if (editModeLayerId == null) {
                        IconButton(onClick = { showProjectInfoPanel = !showProjectInfoPanel }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_project_info),
                                contentDescription = if (showProjectInfoPanel) "Cerrar información del proyecto" else "Información del proyecto",
                                tint = if (showProjectInfoPanel) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        // --- Guías de composición (cuadrícula): al lado del
                        // ícono de deshacer (la flecha "regresar" del
                        // historial), como pediste — no junto a la flecha
                        // de volver a Mis proyectos.
                        //
                        // El ícono solo abre/cierra este menú (GridMenu,
                        // más abajo) — nunca prende ni apaga la cuadrícula
                        // directo al tocarlo. Envuelto en un Box porque el
                        // Popup necesita un "ancla" — un elemento cuyas
                        // coordenadas en pantalla use como referencia para
                        // aparecer justo debajo, centrado.
                        Box {
                            IconButton(onClick = {
                                if (showGridMenu) {
                                    // Ya está abierto: cerrar SIEMPRE
                                    // funciona, sin excepción — es la
                                    // mitad del toggle que nunca hay que
                                    // bloquear.
                                    showGridMenu = false
                                } else {
                                    // Va a abrir: solo se ignora si el
                                    // Popup se auto-cerró hace instantes
                                    // por este mismo toque "afuera" (ver
                                    // comentario de gridMenuAutoDismissedAtMs
                                    // más arriba).
                                    val now = System.currentTimeMillis()
                                    if (now - gridMenuAutoDismissedAtMs > 200) {
                                        showGridMenu = true
                                    }
                                }
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_grid),
                                    contentDescription = if (gridEnabled) "Cambiar cuadrícula de composición" else "Mostrar cuadrícula de composición",
                                    tint = if (gridEnabled) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                            if (showGridMenu) {
                                GridMenu(
                                    enabled = gridEnabled,
                                    shape = gridShape,
                                    spec = gridSpec,
                                    lineColorEnabled = gridLineColorEnabled,
                                    lineHue = gridLineHue,
                                    lineThicknessDp = gridLineThicknessDp,
                                    lineOpacity = gridLineOpacity,
                                    snapEnabled = gridSnapEnabled,
                                    onShapeSelect = { newShape ->
                                        // Elegir una forma NO cierra el
                                        // menú (a diferencia de los viejos
                                        // presets de densidad): después de
                                        // elegir forma, lo más probable es
                                        // que el usuario quiera seguir
                                        // ajustando Columnas/Filas para
                                        // esa forma — cerrar de una lo
                                        // obligaría a reabrir el menú para
                                        // seguir afinando.
                                        viewModel.updateGridSettings(shapeName = newShape.name, enabled = true)
                                    },
                                    onAxisChange = { newSpec ->
                                        // Los steppers +/- son ajuste
                                        // fino: el menú se queda abierto
                                        // para poder seguir tocando sin
                                        // que se cierre en cada toque.
                                        viewModel.updateGridSettings(
                                            columns = newSpec.columns,
                                            rows = newSpec.rows,
                                            enabled = true
                                        )
                                    },
                                    onToggle = { viewModel.updateGridSettings(enabled = !gridEnabled) },
                                    onSnapToggle = { viewModel.updateGridSettings(snapEnabled = !gridSnapEnabled) },
                                    onLineColorToggle = { viewModel.updateGridSettings(lineColorEnabled = !gridLineColorEnabled) },
                                    onLineHueChange = { newHue -> viewModel.updateGridSettings(lineHue = newHue) },
                                    onThicknessChange = { newThickness -> viewModel.updateGridSettings(lineThicknessDp = newThickness) },
                                    onOpacityChange = { newOpacity -> viewModel.updateGridSettings(lineOpacity = newOpacity) },
                                    onDismiss = {
                                        showGridMenu = false
                                        gridMenuAutoDismissedAtMs = System.currentTimeMillis()
                                    }
                                )
                            }
                        }
                        }
                        // --- Papelera de capas: mismo criterio de
                        // visibilidad que Información/Cuadrícula (oculto en
                        // modo edición de imagen aislada) — siempre visible
                        // en el resto de la edición, con badge de cantidad
                        // cuando hay algo adentro. Ver LayerTrashScreen.kt.
                        //
                        // FASE 8-R5: la visibilidad y el número del badge
                        // usan `state.hasTrashedLayers`/`trashBadgeCount`
                        // (unión con `optimisticallyTrashedLayerIds`, ver
                        // su KDoc en EditorUiState) en vez de
                        // `state.trashedLayers` directo — es lo que hace
                        // que el ícono se encienda en el mismo instante en
                        // que la capa desaparece del lienzo, sin esperar
                        // el debounce + I/O del autoguardado. El contenido
                        // del panel (`items` más abajo) SÍ sigue leyendo
                        // `state.trashedLayers` sin cambios: ese dato
                        // recién puede mostrarse cuando está confirmado en
                        // disco (imagen real, tamaño real).
                        //
                        // `|| showLayerTrashDialog` a propósito: el panel
                        // (LayerTrashPanel, ver más abajo) se ancla a ESTE
                        // `Box` — si la papelera se queda vacía mientras
                        // el panel sigue abierto (p. ej. el usuario tocó
                        // "Vaciar papelera" desde adentro), el ícono ancla
                        // NO puede desaparecer de golpe o se llevaría el
                        // panel puesto con él, cortando en seco el estado
                        // "la papelera está vacía" que el panel mismo está
                        // mostrando en ese momento.
                        if (editModeLayerId == null && (state.hasTrashedLayers || showLayerTrashDialog)) {
                            Box {
                                IconButton(onClick = {
                                    viewModel.dismissTrashBanner()
                                    showLayerTrashDialog = true
                                }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_delete),
                                        contentDescription = "Papelera de capas (${state.trashBadgeCount})",
                                        tint = Color.White
                                    )
                                }
                                if (state.hasTrashedLayers) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE05C5C)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // AJUSTE (a pedido, con captura): el número
                                        // se veía desplazado hacia arriba/derecha
                                        // dentro del círculo. `contentAlignment =
                                        // Alignment.Center` del Box ya centraba la
                                        // CAJA del texto correctamente — lo que se
                                        // veía desplazado era el propio glifo del
                                        // dígito DENTRO de esa caja, por el
                                        // "font padding" vertical que Android
                                        // agrega por default a todo texto (espacio
                                        // reservado para descendentes tipo g/y/p
                                        // que un dígito no tiene, así que el
                                        // número visualmente "flota" hacia arriba
                                        // dentro de su propia línea). Se
                                        // desactiva ese padding con
                                        // `PlatformTextStyle(includeFontPadding =
                                        // false)` — el fix estándar de Compose
                                        // para exactamente este caso (números/
                                        // íconos de un solo carácter en badges
                                        // circulares chicos), no un offset manual
                                        // a ojo que se rompería con otro tamaño de
                                        // fuente o de círculo.
                                        Text(
                                            text = if (state.trashBadgeCount > 9) "9+" else state.trashBadgeCount.toString(),
                                            color = Color.White,
                                            style = TextStyle(
                                                fontSize = 9.sp,
                                                lineHeight = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                                            )
                                        )
                                    }
                                }

                                // El panel se declara ACÁ, como hermano del
                                // `IconButton` dentro del mismo `Box` — es lo
                                // que le permite a `Popup` (adentro de
                                // LayerTrashPanel) anclarse exactamente a
                                // este ícono en vez de centrarse en la
                                // pantalla. Ver el comentario de diseño en
                                // LayerTrashPanel, en LayerTrashScreen.kt.
                                if (showLayerTrashDialog) {
                                    LayerTrashPanel(
                                        items = state.trashedLayers,
                                        totalSizeBytes = state.trashSizeBytes,
                                        onRestoreSelected = { ids -> viewModel.restoreLayersFromTrash(ids) },
                                        onDeleteSelected = { ids -> viewModel.deleteLayersFromTrashPermanently(ids) },
                                        onEmptyTrash = { viewModel.emptyTrash() },
                                        onDismiss = { showLayerTrashDialog = false }
                                    )
                                }
                            }
                        }
                        // A PEDIDO DEL USUARIO + ver comentario grande en
                        // `EditorViewModel.beginLayerEditSession`: ahora que
                        // el ↩/↪ general SÍ puede revertir una sesión de
                        // Recolor/3D/Efectos en curso (el checkpoint que
                        // esa función deja al entrar al panel), hay que
                        // evitar que el usuario lo toque MIENTRAS esa
                        // sesión sigue abierta con sliders a medio mover —
                        // eso dejaría la imagen revertida por debajo con
                        // los sliders todavía mostrando valores de la
                        // sesión "fantasma". Así que: dentro de modo
                        // edición aislado, el ↩/↪ general queda
                        // deshabilitado, EXCEPTO en la categoría
                        // "Distorsión", que sí tiene su propio historial
                        // por-trazo pensado para usarse ahí mismo, en
                        // vivo (ver distortionBridge.onUndo/onRedo).
                        val headerUndoEnabled = when {
                            distortionBridge.active -> distortionBridge.canUndo
                            editModeLayerId != null -> false
                            else -> state.undoAvailable
                        }
                        IconButton(
                            onClick = {
                                // A PEDIDO DEL USUARIO: un solo control de
                                // deshacer/rehacer en toda la pantalla — si
                                // la categoría "Distorsión" está activa
                                // ahora mismo (`distortionBridge.active`,
                                // ver DistortionPanel/DistortionGestureBridge),
                                // este botón deshace el ÚLTIMO TRAZO de
                                // distorsión en vez del historial "de
                                // proyecto" (transform/keyframes/orden).
                                // Ya no hay un segundo par de flechas propio
                                // de Distorsión más abajo — ver el bloque
                                // eliminado en DistortionPanel.
                                if (distortionBridge.active) {
                                    distortionBridge.onUndo?.invoke()
                                } else {
                                    viewModel.undo()
                                }
                            },
                            enabled = headerUndoEnabled
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_undo),
                                contentDescription = "Deshacer",
                                // Ícono a color fijo (rojo): `Unspecified` evita que un tinte lo pise. La
                                // atenuación del estado deshabilitado se aplica con alpha, no con el tinte.
                                tint = Color.Unspecified,
                                modifier = Modifier.alpha(if (headerUndoEnabled) 1f else 0.3f)
                            )
                        }
                        val headerRedoEnabled = when {
                            distortionBridge.active -> distortionBridge.canRedo
                            editModeLayerId != null -> false
                            else -> state.redoAvailable
                        }
                        IconButton(
                            onClick = {
                                if (distortionBridge.active) {
                                    distortionBridge.onRedo?.invoke()
                                } else {
                                    viewModel.redo()
                                }
                            },
                            enabled = headerRedoEnabled
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_redo),
                                contentDescription = "Rehacer",
                                // Ícono a color fijo (verde): ver nota en Deshacer.
                                tint = Color.Unspecified,
                                modifier = Modifier.alpha(if (headerRedoEnabled) 1f else 0.3f)
                            )
                        }
                        IconButton(
                            onClick = { showExportDialog = true },
                            enabled = state.layers.isNotEmpty()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_export),
                                contentDescription = "Exportar video",
                                tint = if (state.layers.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
        // --- floatingActionButton eliminado: el "+" (importar imagen) y la
        // "F" (importar fondo) que vivían acá abajo a la derecha ahora están
        // dentro del diálogo "Agregar pista" (opción "Imagen"), para no
        // duplicar puntos de entrada — antes de esto había DOS "+": uno acá
        // y otro en la línea de tiempo, y eso confundía. ---
    ) { padding ->
        // --- Envoltorio nuevo (panel lateral izquierdo de "Recolor"): antes
        // esta Column ERA el contenido completo del Scaffold. Ahora va
        // envuelta en un Box, así el panel angosto de Recolor (ver
        // RecolorFloatingWindow más abajo, y su definición junto a
        // LayerColorEditPanel) puede dibujarse COMO HERMANO de toda esta
        // Column — cubriendo con `fillMaxHeight()` el alto REAL combinado
        // de preview + línea de tiempo, pegado al borde izquierdo de la
        // pantalla, sin desplazar ni recortar nada de lo que ya había.
        Box(
            modifier = Modifier
                .padding(padding)
                // API 36: con borde a borde el teclado ya no redimensiona la ventana, llega como inset IME.
                // `consumeWindowInsets(padding)` evita contar dos veces la barra de navegación (ya incluida
                // en `padding`) y `imePadding()` reproduce el "adjustResize" que esta pantalla tenía antes.
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
                // BUG REAL corregido (ver el comentario grande sobre
                // `floatingWindowAreaSizePx`, junto a su declaración más
                // arriba): este es el área REAL — ya con el `padding` del
                // Scaffold restado — donde viven las tres ventanas
                // flotantes y sus íconos minimizados. Medirla acá, en el
                // mismo Box que las contiene, es lo único que le da a
                // [FloatingToolWindow] un límite verdadero contra el cual
                // recortar `offsetPx`, en vez de un número adivinado.
                .onSizeChanged { floatingWindowAreaSizePx = it }
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // --- Preview FIJO: siempre visible, no se va con el scroll de los controles ---
            // --- Sincronización canvas -> timeline: tocar una imagen en el
            // preview selecciona su capa correspondiente abajo (antes solo
            // funcionaba al revés, tocando la fila del timeline). Va en un
            // pointerInput SEPARADO del de arrastrar/pellizcar de más
            // arriba — ambos conviven sobre el mismo Box sin pisarse,
            // mismo patrón ya usado en el resto de la app (ver
            // ColorWheelPicker en LayerDialogs.kt). Usa rememberUpdatedState
            // para leer siempre la lista de capas y el playhead MÁS
            // RECIENTES: la lambda de detectTapGestures se arma una sola
            // vez (key = Unit) y queda viva mientras dure la pantalla, así
            // que sin esto el hit-test terminaría comparando el toque
            // contra datos viejos — el mismo bug de closure obsoleto que
            // se corrigió antes en la lupa del selector de color.
            //
            // Selección + arrastre en un solo toque continuo, como
            // cualquier editor mobile profesional (CapCut, Canva): tocar
            // una capa DISTINTA la selecciona Y la deja arrastrar/
            // pellizcar/rotar de inmediato, sin soltar el dedo. La caja
            // de hit-test es el margen COMPLETO de la capa (ver
            // hitTestLayerAt), no su contenido pintado — así la selección
            // es siempre predecible sin importar la forma o el tamaño de
            // lo que se ve dentro del PNG. El pan/zoom/rotación de varios
            // dedos se calcula a mano
            // (centroide, distancia promedio, ángulo entre los primeros
            // dos dedos) en vez de reusar detectTransformGestures, porque
            // esa función arma su PROPIO ciclo de "primer toque" por
            // dentro — no se puede encadenar a mitad de un gesto que ya
            // empezamos a procesar nosotros mismos más arriba.
            val latestLayersForHitTest = rememberUpdatedState(state.layers)
            val latestPlayheadForHitTest = rememberUpdatedState(state.playheadMs)
            val latestSelectedLayerId = rememberUpdatedState(selectedLayer?.id)
            val latestSelectedLayerForDrag = rememberUpdatedState(selectedLayer)
            val hitTestBoxSize = remember { mutableStateOf(IntSize.Zero) }
            // Ajuste de la vista de edición: cuando el visor cambia de tamaño (entrar al modo edición, girar la
            // tablet, abrir el teclado) y el usuario aún no hizo zoom a mano, la imagen se reajusta. Si ya hizo
            // zoom, se respeta (ver EditViewSession.shouldApplyFit). No toca la capa del proyecto.
            LaunchedEffect(editModeLayerId, hitTestBoxSize.value, selectedLayer?.widthPx, selectedLayer?.heightPx) {
                val session = editViewSession ?: return@LaunchedEffect
                val layer = viewModel.currentSelectedLayer()?.takeIf { it.id == editModeLayerId }
                    ?: return@LaunchedEffect
                if (!session.shouldApplyFit(scale)) return@LaunchedEffect
                val viewport = hitTestBoxSize.value
                val fit = EditViewFit.fitScale(
                    imageWidthPx = layer.widthPx,
                    imageHeightPx = layer.heightPx,
                    viewportWidthPx = viewport.width,
                    viewportHeightPx = viewport.height,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    rotationDeg = rotation,
                    parallaxFactor = layer.parallaxFactor
                ) ?: return@LaunchedEffect
                translateX = 0f
                translateY = 0f
                scale = fit
                session.markFitApplied(fit)
            }
            // Bandera siempre-actualizada de si el cuentagotas está
            // activo AHORA — necesaria para el guard de más abajo. Va en
            // rememberUpdatedState (no se lee `eyedropperActiveForLayerId`
            // directo) por el mismo motivo de siempre: este pointerInput
            // vive en una corrutina de larga vida (key = Unit) que no se
            // reinicia en cada recomposición, así que leerla directo
            // adentro daría un valor viejo "congelado" del momento en que
            // arrancó la corrutina.
            val latestEyedropperActive = rememberUpdatedState(eyedropperActiveForLayerId != null)
            // Bandera siempre-actualizada de si "Edición > Imagen" está
            // activada (ver menú EdicionMenu, arriba del ícono Grabar).
            // Mismo motivo que latestEyedropperActive: este pointerInput
            // vive en una corrutina de larga vida (key = Unit), así que
            // leer edicionImagenChecked directo adentro daría el valor
            // "congelado" del momento en que arrancó el gesto. Con esto
            // apagado, la capa sigue pudiéndose ARRASTRAR con un dedo
            // (mover de lugar), pero el pellizco de dos dedos NO
            // escala ni rota — queda "estática" salvo por la posición,
            // tal como pediste.
            val latestEdicionImagenEnabled = rememberUpdatedState(edicionImagenChecked)
            // Mismo motivo: mientras el modo reordenar manijas está
            // activo, el canvas queda "congelado" — este gesto de larga
            // vida necesita el valor MÁS RECIENTE de `reorderMode` para
            // bloquear selección/arrastre/pellizco de la capa (ver el
            // guard más abajo, justo antes del hit-test de selección
            // normal).
            val latestReorderModeEnabled = rememberUpdatedState(reorderMode)
            // Callback para "reemplazar imagen" del doble-tap — ver más
            // abajo, cerca de tapSlopPx. rememberUpdatedState por el mismo
            // motivo de siempre: esta lambda vive dentro del pointerInput
            // de larga vida (key = Unit).
            val latestOnReplaceImageClick = rememberUpdatedState(onReplaceImageClick)
            // Igual criterio: GLPreview.getLayers corre en el hilo de GL,
            // así que el filtro de "modo edición" (aislar una sola capa)
            // necesita leer el valor MÁS RECIENTE, no el que tenía la
            // composición cuando se creó el lambda.
            val latestEditModeLayerId = rememberUpdatedState(editModeLayerId)
            // --- Snap magnético a cuadrícula (ver bloque de arrastre más
            // abajo, cerca de "translateX = (translateX + ..."): mismos
            // 3 valores de grilla (gridEnabled/gridShape/gridSpec) que
            // arriba, pero envueltos en rememberUpdatedState porque el
            // pointerInput de este Box es de larga vida (key = Unit, no
            // se reinicia en cada recomposición) — leerlos directo acá
            // adentro daría el valor "congelado" del momento en que
            // arrancó el gesto, igual que con latestEdicionImagenEnabled.
            val latestGridEnabledForSnap = rememberUpdatedState(gridEnabled)
            val latestGridShapeForSnap = rememberUpdatedState(gridShape)
            val latestGridSpecForSnap = rememberUpdatedState(gridSpec)
            val latestGridSnapEnabledForSnap = rememberUpdatedState(gridSnapEnabled)
            // --- ADR-005, Fase F: el "slot" del preview ---
            // Este Box de afuera ocupa EXACTAMENTE el mismo espacio que
            // antes ocupaba el propio canvas (ancho completo + el resto
            // de la Column cuando `canvasFillsScreen`, o el 46% del alto
            // en el modo normal) — ese envoltorio de tamaño NO cambia con
            // esta fase, solo se mueve un nivel hacia afuera. Lo nuevo es
            // que ahora hace de "letterbox": pinta de negro el sobrante
            // cuando la forma real del Canvas del proyecto no llena el
            // slot (por ejemplo, un Canvas cuadrado dentro de un slot más
            // ancho que alto), en vez de dejar que el canvas de adentro
            // se estire fuera de su aspecto real.
            // AJUSTE (a pedido, con captura): el banner de la papelera
            // dejó de ser un ítem más de este Column con su propia fila
            // (eso exponía el fondo oscuro normal de la pantalla a los
            // costados de la pastilla, leyéndose como "otro header" no
            // deseado). Ahora es un HERMANO superpuesto dentro de este
            // mismo Box exterior, alineado arriba — flota directamente
            // sobre el lienzo verde, sin reservar ninguna fila propia. El
            // Box exterior de acá abajo pasó a encargarse únicamente del
            // tamaño (weight/fillMaxHeight, que solo tiene efecto como
            // hijo directo del Column); el que tenía fondo negro +
            // pointerInput de deseleccionar pasó a ser el hijo interno,
            // sin ningún otro cambio de comportamiento.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canvasFillsScreen) Modifier.weight(1f)
                        else Modifier.fillMaxHeight(0.46f)
                    )
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    // --- ARREGLADO: tocar la franja negra de "letterbox"
                    // (fuera del rectángulo del Canvas real, cuando su
                    // aspecto no llena este slot — ver comentario de
                    // ADR-005/Fase F arriba) no hacía NADA: el marco
                    // morado de selección se quedaba pegado igual que el
                    // bug ya arreglado para un hueco vacío DENTRO del
                    // canvas (ver el otro `viewModel.clearSelection()`,
                    // más abajo en el `pointerInput` del Box hijo). Pasaba
                    // porque toda esa lógica de tap-para-deseleccionar
                    // vive en el `pointerInput` del Box de ADENTRO — un
                    // toque en esta franja negra, al caer fuera de sus
                    // límites de layout, nunca llega ahí. Mismo criterio
                    // (toque simple, no arrastre — `detectTapGestures` ya
                    // filtra eso por su cuenta), acá aplicado al slot de
                    // afuera: cualquier toque genuino sobre esta franja
                    // limpia la selección igual que tocar un hueco vacío
                    // adentro del lienzo.
                    .pointerInput(Unit) {
                        detectTapGestures {
                            selectedAudioTrackId = null
                            viewModel.clearSelection()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
            // --- ADR-005, Fase F: el Canvas real ---
            // Antes este Box heredaba el ancho/alto del slot de arriba
            // sin relación con el aspecto elegido al crear el proyecto —
            // el área de edición nunca tuvo, en ningún momento, la forma
            // del Canvas real (ver ADR-005, sección "Contexto"). Ahora
            // `.aspectRatio(state.canvas.aspect.ratio)` fuerza esa forma:
            // como el Box padre de arriba usa `contentAlignment` (no
            // `matchParentSize`), este hijo se mide con restricciones
            // SUELTAS (máximo = tamaño del slot, mínimo = 0), así que
            // `aspectRatio` puede elegir el lado correcto (ancho o alto)
            // para caber ENTERO dentro del slot sin deformarse, y
            // `contentAlignment = Alignment.Center` de arriba lo centra —
            // de ahí sale el letterboxing, sin cálculo manual aparte.
            //
            // Esto es seguro para el hit-test/arrastre de capas y para
            // GLPreview/la cuadrícula: todos ellos ya leen el tamaño real
            // y medido de este Box a través de `hitTestBoxSize`
            // (`.onSizeChanged` de acá abajo), nunca un valor fijo o
            // heredado del slot — así que si el Canvas achica este Box
            // por el letterboxing, hit-test/GLPreview/grid se adaptan
            // solos, sin tocar ese código (ver ADR-005, "Consecuencias").
            Box(
                modifier = Modifier
                    // AUDITORÍA — "carga escalonada / el lienzo cambia de
                    // tamaño solo al reabrir un proyecto" (a pedido
                    // explícito del cliente, con capturas): `state.canvas`
                    // arranca en el default de `EditorUiState`
                    // (1080×1920, formato REELS) y recién se actualiza al
                    // Canvas REAL guardado (p. ej. "IMAX") cuando
                    // `EditorViewModel.init` termina de leer el disco —
                    // ver `isLoadingProject`/`canvas` en el mismo `copy()`
                    // del bloque `init`, se actualizan siempre juntos y a
                    // la vez. Antes, `.aspectRatio(state.canvas.aspect.
                    // ratio)` se aplicaba SIEMPRE, sin condición: este Box
                    // se medía y se dibujaba con la forma del default
                    // (1080×1920) durante toda la carga —el overlay verde
                    // de "cargando" (ver `canvasFullyPainted` más abajo)
                    // tapaba el CONTENIDO, pero no el CONTORNO: el
                    // rectángulo en sí ya tenía la forma equivocada— y
                    // recién tomaba la forma correcta cuando `state.canvas`
                    // se actualizaba, un instante después. Ese cambio de
                    // forma, con el overlay puesto, es exactamente el
                    // "primero carga chico, después grande" que se ve a
                    // simple vista al reabrir un proyecto guardado en un
                    // formato distinto del default (como "IMAX").
                    //
                    // Mientras `isLoadingProject` sigue en `true`, este Box
                    // llena el slot completo (`fillMaxSize()`) en vez de
                    // adoptar una forma específica — ninguna forma
                    // "genérica de carga" sugiere un formato equivocado,
                    // porque no sugiere ningún formato. Apenas
                    // `isLoadingProject` pasa a `false`, `state.canvas` YA
                    // es el real (mismo `copy()`, nunca llega uno sin el
                    // otro) — así que el único cambio de forma que el
                    // usuario puede llegar a percibir es UNO solo, directo
                    // al formato final correcto, nunca "el chico, después
                    // el grande".
                    .let { base ->
                        // En modo edición el verde ya no es "el lienzo" sino el fondo de trabajo: ocupa toda el área
                        // (sin franjas negras) y la imagen se ajusta a ella (ver EditViewFit). Fuera del modo
                        // edición se conserva la forma exacta del lienzo del proyecto.
                        if (state.isLoadingProject || editModeLayerId != null) base.fillMaxSize()
                        else base.aspectRatio(state.canvas.aspect.ratio)
                    }
                    .background(Color(EditViewportBackdrop.behindSurfaceArgb(editModeLayerId != null, editBackdrop)))
                    .onSizeChanged { hitTestBoxSize.value = it }
                    .pointerInput(Unit) {
                        // Doble-tap para reemplazar imagen: estas dos
                        // variables viven ACÁ afuera (no adentro del
                        // bloque de awaitEachGesture, que se reinicia en
                        // cada ciclo de gesto) para poder comparar el
                        // toque de un gesto contra el toque del gesto
                        // ANTERIOR y así detectar el segundo tap. Funciona
                        // con "Edición > Imagen" activada o apagada, tal
                        // como se pidió.
                        var lastImageTapAtMs = 0L
                        var lastImageTapLayerId: String? = null
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // --- ARREGLADO: con el cuentagotas activo, este
                            // mismo toque es del overlay de arriba (ver
                            // "Cuentagotas: overlay..." más abajo en el
                            // árbol), NO de seleccionar/arrastrar una capa.
                            // Antes, como este Box usa
                            // `requireUnconsumed = false` a propósito (para
                            // no pelearse con otros gestos), terminaba
                            // procesando el toque IGUAL aunque el overlay ya
                            // lo hubiera consumido — por eso, al usar el
                            // cuentagotas sobre una imagen, esa imagen
                            // quedaba seleccionada y se arrastraba en vez de
                            // solo tomar el color. Ahora se sale de una,
                            // sin tocar nada del gesto, y se lo deja
                            // enteramente al overlay del cuentagotas.
                            if (latestEyedropperActive.value) return@awaitEachGesture

                            // --- Si hay un mini-menú flotante abierto (Editar /
                            // Restablecer / Solo aquí-Todos), este gesto del
                            // canvas se ignora por completo. Motivo: el
                            // `awaitFirstDown(requireUnconsumed = false)` de
                            // arriba, a propósito, NO chequea si Compose ya
                            // consumió el toque más arriba (para no pelearse
                            // con otros gestos) — así que, sin este freno, un
                            // toque en un elemento de Compose DENTRO de esos
                            // paneles (como el ícono "i" de "Restablecer")
                            // también dispara la detección de manijas de acá
                            // abajo. Si las manijas están muy juntas
                            // (personaje chico / muy alejado en zoom), ese
                            // mismo toque puede caer dentro del radio de una
                            // manija real y alternarla — cerrando el panel
                            // entero en vez de solo tocar el ícono "i" (el bug
                            // reportado: "toco el ícono de descripción y no
                            // sale nada, se cierra la ventana"). Mientras
                            // cualquiera de estos dos paneles esté abierto,
                            // la única forma de interactuar es a través de su
                            // propia UI de Compose, no del canvas de abajo.
                            // (La manija "esquina sup. izquierda" ya no abre
                            // ningún panel — entra directo a modo edición al
                            // tocarla — así que no participa de este freno.)
                            if (showReorderScopeMenu || showRestoreOptionsMenu) {
                                return@awaitEachGesture
                            }

                            // --- Modo orbital 3D (pestaña "3D" del modo
                            // edición dedicado activa — ver
                            // [Extrude3DGestureBridge]): gesto COMPLETAMENTE
                            // aparte del de abajo, con return temprano,
                            // porque acá nunca hay nada más que hacer con el
                            // toque — nunca cambia de capa (el aislamiento
                            // del modo edición ya deja una sola visible),
                            // nunca hay manijas (no se dibujan, ver el
                            // `if` del marco más arriba) y nunca dispara el
                            // doble-tap de reemplazar imagen. Un dedo (o el
                            // centroide de varios) orbita el cuerpo 3D;
                            // desde el segundo dedo en adelante, además,
                            // pellizcar sigue agrandando/achicando la capa
                            // igual que en cualquier otro modo ("que sí
                            // funcione el zoom con los dedos") y el giro
                            // entre los dos dedos mueve la Rotación Z.
                            if (extrude3DBridge.active) {
                                down.consume()
                                var prevCentroid: Offset? = null
                                var prevSpan: Float? = null
                                var prevAngle: Float? = null
                                var prevPressedCount = 0
                                var lastCommitAtMs = 0L
                                var pendingCommit = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break

                                    var sumX = 0f; var sumY = 0f
                                    for (c in pressed) { sumX += c.position.x; sumY += c.position.y }
                                    val centroid = Offset(sumX / pressed.size, sumY / pressed.size)
                                    val fingerCountChanged = pressed.size != prevPressedCount

                                    if (prevCentroid != null && !fingerCountChanged) {
                                        val boxWidth = size.width.toFloat().coerceAtLeast(1f)
                                        val boxHeight = size.height.toFloat().coerceAtLeast(1f)
                                        val panDx = centroid.x - prevCentroid.x
                                        val panDy = centroid.y - prevCentroid.y
                                        // Sensibilidad proporcional al tamaño
                                        // del canvas: recorrerlo entero de
                                        // lado a lado gira 180°, el mismo
                                        // recorrido que el rango completo de
                                        // los sliders — se siente
                                        // "1 a 1" con el dedo en vez de un
                                        // número mágico fijo en px.
                                        val dxDeg = (panDx / boxWidth) * 180f
                                        val dyDeg = (panDy / boxHeight) * 180f
                                        extrude3DBridge.onOrbitDrag?.invoke(dxDeg, dyDeg)

                                        if (pressed.size >= 2) {
                                            var sumDist = 0f
                                            for (c in pressed) sumDist += hypot(c.position.x - centroid.x, c.position.y - centroid.y)
                                            val span = sumDist / pressed.size
                                            val ps = prevSpan
                                            if (ps != null && ps > 1f) {
                                                scale = (scale * (span / ps)).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
                                            }
                                            prevSpan = span

                                            val a = pressed[0].position
                                            val b = pressed[1].position
                                            val angle = Math.toDegrees(
                                                atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
                                            ).toFloat()
                                            val pa = prevAngle
                                            if (pa != null) {
                                                var deltaAngle = angle - pa
                                                if (deltaAngle > 180f) deltaAngle -= 360f
                                                if (deltaAngle < -180f) deltaAngle += 360f
                                                extrude3DBridge.onTwistDrag?.invoke(deltaAngle)
                                            }
                                            prevAngle = angle
                                        } else {
                                            prevSpan = null
                                            prevAngle = null
                                        }

                                        pendingCommit = true
                                        val now = System.currentTimeMillis()
                                        if (now - lastCommitAtMs >= 120L) {
                                            commitLiveFrame(); lastCommitAtMs = now; pendingCommit = false
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } else if (fingerCountChanged) {
                                        prevSpan = null
                                        prevAngle = null
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                    prevCentroid = centroid
                                    prevPressedCount = pressed.size
                                }
                                if (pendingCommit) commitLiveFrame()
                                return@awaitEachGesture
                            }

                            // --- Categoría "Distorsión" activa (dentro de "Efectos"
                            // — ver [DistortionGestureBridge] y DistortionPanel): mismo
                            // criterio de gesto aparte con return temprano que el
                            // bloque de arriba, pero con la mitad opuesta del reparto
                            // de dedos — acá 1 SOLO dedo pinta un trazo (el motor de
                            // deformación necesita un camino continuo, no un
                            // centroide de varios dedos), y desde el 2do dedo en
                            // adelante el gesto pasa a mover/escalar/rotar la capa
                            // con total libertad (pellizco + giro entre dos dedos),
                            // exactamente como en cualquier otro modo — así el
                            // usuario puede alejar el zoom para pintar una zona
                            // grande sin tener que salir del pincel activo.
                            //
                            // ARREGLADO: antes este bloque capturaba CUALQUIER primer
                            // toque como inicio de trazo, sin revisar si cayó sobre una
                            // de las 8 manijas del marco (✓ confirmar, × cancelar,
                            // flechas de resize, restaurar, reordenar) — esas manijas
                            // quedaban invisibles para el gesto (parecían "bloqueadas",
                            // el toque nunca llegaba al bloque de manijas de más abajo).
                            // Ahora se prueban las 8 posiciones PRIMERO (mismo cálculo
                            // de encuadre que usa el marco real, vía
                            // [layerBoundingQuadPx]) y, si el toque cae en alguna, este
                            // bloque no consume nada y deja caer el gesto al manejo de
                            // manijas normal de más abajo — pintar solo se activa si el
                            // toque no fue sobre ninguna manija.
                            if (distortionBridge.active) {
                                val editLayerForHandles = latestSelectedLayerForDrag.value
                                val touchedAHandle = if (editLayerForHandles == null) {
                                    false
                                } else {
                                    val corners = layerBoundingQuadPx(
                                        translateX = translateX,
                                        translateY = translateY,
                                        scaleVal = scale,
                                        rotationDeg = rotation,
                                        parallaxFactor = editLayerForHandles.parallaxFactor,
                                        layerWidthPx = editLayerForHandles.widthPx,
                                        layerHeightPx = editLayerForHandles.heightPx,
                                        boxWidthPx = size.width.toFloat(),
                                        boxHeightPx = size.height.toFloat(),
                                        scaleXVal = scaleX,
                                        scaleYVal = scaleY,
                                        tiltXDeg = tiltX,
                                        tiltYDeg = tiltY,
                                        dollyZoom = dollyZoom
                                    )
                                    if (corners != null && corners.size == 4) {
                                        // Mismo recorte ("clamp") que usan el
                                        // dibujo real y el hit-test principal
                                        // de más abajo (ver [clampedHandleSlots])
                                        // — así una manija que el usuario ve
                                        // pegada al borde de la pantalla se
                                        // detecta acá en su posición VISIBLE
                                        // real, no en la posición geométrica
                                        // cruda de la capa (que puede estar
                                        // fuera de pantalla). MISMA constante
                                        // que el resto de los 3 sitios — ver
                                        // [HANDLE_BADGE_CLAMP_MARGIN_DP].
                                        val marginPx = HANDLE_BADGE_CLAMP_MARGIN_DP.toPx()
                                        val slots = clampedHandleSlots(
                                            corners,
                                            Size(size.width.toFloat(), size.height.toFloat()),
                                            marginPx
                                        ).values
                                        val touchRadiusPx = 20.dp.toPx()
                                        slots.any { (down.position - it).getDistance() <= touchRadiusPx }
                                    } else {
                                        false
                                    }
                                }
                                if (!touchedAHandle) {
                                    down.consume()
                                    var isPainting = false
                                    var prevSpan: Float? = null
                                    var prevAngle: Float? = null
                                    var prevPressedCount = 0
                                    var lastCommitAtMs = 0L
                                    val editLayerForUv = latestSelectedLayerForDrag.value
                                    fun mapToUv(pos: Offset): Offset? {
                                        val l = editLayerForUv ?: return null
                                        return screenPointToLayerUv(
                                            point = pos,
                                            boxWidthPx = size.width.toFloat(),
                                            boxHeightPx = size.height.toFloat(),
                                            translateX = translateX,
                                            translateY = translateY,
                                            scaleVal = scale,
                                            rotationDeg = rotation,
                                            parallaxFactor = l.parallaxFactor,
                                            layerWidthPx = l.widthPx,
                                            layerHeightPx = l.heightPx,
                                            scaleXVal = scaleX,
                                            scaleYVal = scaleY,
                                            tiltXDeg = tiltX,
                                            tiltYDeg = tiltY,
                                            dollyZoom = dollyZoom
                                        )
                                    }
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break

                                        if (pressed.size == 1) {
                                            val uv = mapToUv(pressed[0].position)
                                            if (prevPressedCount != 1) {
                                                // Recién queda (o arranca) un solo dedo:
                                                // se cierra cualquier trazo anterior (por
                                                // si se venía de pellizcar con 2 dedos) y
                                                // arranca uno nuevo.
                                                if (isPainting) distortionBridge.onStrokeEnd?.invoke()
                                                if (uv != null) {
                                                    distortionBridge.onStrokeStart?.invoke(uv)
                                                    isPainting = true
                                                }
                                            } else if (uv != null && isPainting) {
                                                distortionBridge.onStrokeMove?.invoke(uv)
                                            }
                                            prevSpan = null
                                            prevAngle = null
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        } else {
                                            if (isPainting) {
                                                distortionBridge.onStrokeEnd?.invoke()
                                                isPainting = false
                                            }
                                            var sumX = 0f; var sumY = 0f
                                            for (c in pressed) { sumX += c.position.x; sumY += c.position.y }
                                            val centroid = Offset(sumX / pressed.size, sumY / pressed.size)
                                            if (prevPressedCount == pressed.size) {
                                                var sumDist = 0f
                                                for (c in pressed) sumDist += hypot(c.position.x - centroid.x, c.position.y - centroid.y)
                                                val span = sumDist / pressed.size
                                                val ps = prevSpan
                                                if (ps != null && ps > 1f) {
                                                    scale = (scale * (span / ps)).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
                                                }
                                                prevSpan = span

                                                val a = pressed[0].position
                                                val b = pressed[1].position
                                                val angle = Math.toDegrees(
                                                    atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
                                                ).toFloat()
                                                val pa = prevAngle
                                                if (pa != null) {
                                                    var deltaAngle = angle - pa
                                                    if (deltaAngle > 180f) deltaAngle -= 360f
                                                    if (deltaAngle < -180f) deltaAngle += 360f
                                                    rotation += deltaAngle
                                                }
                                                prevAngle = angle

                                                val now = System.currentTimeMillis()
                                                if (now - lastCommitAtMs >= 120L) {
                                                    commitLiveFrame(); lastCommitAtMs = now
                                                }
                                            } else {
                                                prevSpan = null
                                                prevAngle = null
                                            }
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                        prevPressedCount = pressed.size
                                    }
                                    if (isPainting) distortionBridge.onStrokeEnd?.invoke()
                                    commitLiveFrame()
                                    return@awaitEachGesture
                                }
                            }

                            val boxSize = hitTestBoxSize.value
                            // --- ARREGLADO (reemplaza el "congelado" total
                            // de antes): mientras el modo "Editando imagen"
                            // está activo (`editModeLayerId`), el hit-test
                            // de toques se restringe a SOLO esa capa — las
                            // demás (aunque no se dibujen, ver `getLayers`
                            // de GLPreview) quedan afuera de la lista que
                            // se le pasa a `hitTestLayerAt`, así que un
                            // toque nunca puede "encontrar" otra capa ni el
                            // fondo se confunde con una selección real. Con
                            // esto, `hitLayerId` de acá para abajo solo
                            // puede ser la capa editada o `null` (hueco
                            // vacío) — nunca otra capa — así que TODO el
                            // código de selección/arrastre/pellizco/giro de
                            // más abajo puede seguir funcionando SIN
                            // cambios y sin riesgo de saltar a otra capa ni
                            // salir del modo: sigue operando siempre sobre
                            // la misma capa aislada, con la misma libertad
                            // de gestos que una capa normal (pedido
                            // explícito: "esto es muy limitado, devuelve
                            // esa flexibilidad").
                            val editIdForHitTest = latestEditModeLayerId.value
                            val hitTestLayers = if (editIdForHitTest != null) {
                                latestLayersForHitTest.value.filter { it.id == editIdForHitTest }
                            } else {
                                latestLayersForHitTest.value
                            }
                            val hitLayerId = hitTestLayerAt(
                                tapOffset = down.position,
                                boxWidthPx = boxSize.width.toFloat(),
                                boxHeightPx = boxSize.height.toFloat(),
                                layers = hitTestLayers,
                                playheadMs = latestPlayheadForHitTest.value,
                                preferredLayerId = latestSelectedLayerId.value
                            )

                            // --- Manijas de "Edición > Imagen" (6 por capa,
                            // ver EdicionMenu arriba del ícono Grabar): se
                            // prueban ANTES que la selección/arrastre normal
                            // de más abajo, y SOLO contra la capa YA
                            // seleccionada — tocar una manija nunca cambia
                            // de capa, siempre actúa sobre la que se estaba
                            // editando. Si el modo está apagado no hay
                            // manijas dibujadas (ver el Canvas más abajo),
                            // así que tampoco deben responder a toques acá.
                            //
                            // A PEDIDO EXPLÍCITO DEL USUARIO — BUG VISUAL
                            // corregido (reportado con captura): estas 8
                            // manijas son indispensables en el canvas
                            // PRINCIPAL (línea de tiempo) — eso queda 100%
                            // sin tocar. Pero dentro del modo edición
                            // aislado de una capa (`editModeLayerId` —
                            // la ventana de pantalla completa con las
                            // pestañas Color/3D/Efecto), esas mismas 8
                            // manijas se reordenan de otra forma (ver los
                            // paneles flotantes de esa vista) y quedaban
                            // superpuestas sobre el marco, viéndose
                            // "sucias" — el pedido explícito fue: ahí
                            // adentro, marco limpio (solo el rectángulo
                            // azul, ver el Canvas de dibujo más abajo) y
                            // nada de manijas para tocar.
                            // `latestEditModeLayerId.value == null` es
                            // justo "no estoy en esa vista aislada" — con
                            // eso solo, ni se prueban estas 8
                            // manijas ni [hitsRole] de acá abajo puede
                            // dispararse, así que el toque cae directo al
                            // arrastre/pellizco genérico de más abajo,
                            // exactamente como cualquier hueco vacío del
                            // canvas.
                            if (latestEdicionImagenEnabled.value && latestEditModeLayerId.value == null) {
                                val sel = latestSelectedLayerForDrag.value
                                if (sel != null && !sel.locked) {
                                    val handleCorners = layerBoundingQuadPx(
                                        translateX = translateX,
                                        translateY = translateY,
                                        scaleVal = scale,
                                        rotationDeg = rotation,
                                        parallaxFactor = sel.parallaxFactor,
                                        layerWidthPx = sel.widthPx,
                                        layerHeightPx = sel.heightPx,
                                        boxWidthPx = boxSize.width.toFloat(),
                                        boxHeightPx = boxSize.height.toFloat(),
                                        scaleXVal = scaleX,
                                        scaleYVal = scaleY,
                                        tiltXDeg = tiltX,
                                        tiltYDeg = tiltY,
                                        dollyZoom = dollyZoom
                                    )
                                    if (handleCorners != null && handleCorners.size == 4) {
                                        // El centro real de la capa (para las
                                        // matemáticas de rotar/escalar más
                                        // abajo) tiene que seguir siendo el
                                        // geométrico de verdad — NUNCA se
                                        // recorta, a diferencia de las 8
                                        // manijas (ver comentario grande junto
                                        // a [clampedHandleSlots]): rotar/
                                        // escalar necesita el centro real de
                                        // la capa, sea cual sea, esté o no
                                        // visible en pantalla.
                                        val centerPx = Offset(
                                            (handleCorners[0].x + handleCorners[2].x) / 2f,
                                            (handleCorners[0].y + handleCorners[2].y) / 2f
                                        )
                                        val touchRadiusPx = 20.dp.toPx()
                                        fun hits(p: Offset) = (down.position - p).getDistance() <= touchRadiusPx

                                        // --- Único mapa con las 8 posiciones físicas (las 7
                                        // intercambiables de siempre + la que antes era la fija de
                                        // reordenar) — antes había TRES copias de este mismo mapa
                                        // repetidas más abajo (una para el arrastre de reordenar,
                                        // otra para resolver `hitsRole`); unificarlas en una sola
                                        // evita que agregar esta 8va posición dependa de mantener
                                        // sincronizadas copias sueltas.
                                        //
                                        // ARREGLADO A PEDIDO (captura real: manijas que
                                        // "desaparecen" cerca del borde de la pantalla) — ahora pasa
                                        // por [clampedHandleSlots] para que la posición donde se
                                        // detecta el toque coincida SIEMPRE con la posición donde
                                        // realmente se dibuja la manija (ver el Canvas de dibujo,
                                        // más abajo, que usa la misma función Y LA MISMA
                                        // constante — [HANDLE_BADGE_CLAMP_MARGIN_DP] — para el
                                        // margen; antes este sitio usaba solo el "colchón" (16dp)
                                        // sin sumar el radio+anillo real del badge, un desfase
                                        // real entre dónde se ve la manija y dónde había que
                                        // tocarla).
                                        val marginPx = HANDLE_BADGE_CLAMP_MARGIN_DP.toPx()
                                        val slotOffsets = clampedHandleSlots(
                                            handleCorners,
                                            Size(boxSize.width.toFloat(), boxSize.height.toFloat()),
                                            marginPx
                                        )

                                        // Dónde vive AHORA la manija de reordenar (rol REORDER):
                                        // el borrador en vivo si ya se está reordenando, si no el
                                        // orden guardado (por-capa o global). Si por algún motivo
                                        // el orden vigente no tuviera el rol REORDER asignado a
                                        // ninguna posición (no debería pasar, `DEFAULT_HANDLE_ORDER`
                                        // siempre lo incluye), cae a `leftMid` como red de
                                        // seguridad.
                                        val orderForReorderLookup = if (reorderMode) (reorderDraftOrder ?: effectiveHandleOrder(sel.id)) else effectiveHandleOrder(sel.id)
                                        val reorderPos = orderForReorderLookup.entries.firstOrNull { it.value == LayerHandleRole.REORDER }?.key
                                        val reorderHandleOffset = reorderPos?.let { slotOffsets[it] } ?: slotOffsets.getValue(HandlePosition.LEFT_MID)
                                        // Dónde vive AHORA la manija "eliminar capa" (rol DELETE,
                                        // el ícono ×): se usa más abajo para que, SOLO mientras el
                                        // modo reordenar está activo, esta misma × sirva para
                                        // CANCELAR el reordenamiento en vez de borrar la capa (ver
                                        // el bloque justo después del confirmar/abrir de la manija
                                        // de reordenar). Fuera del modo reordenar, la × sigue
                                        // funcionando exactamente igual que siempre — ver
                                        // `hitsRole(LayerHandleRole.DELETE)` más abajo.
                                        val deletePos = orderForReorderLookup.entries.firstOrNull { it.value == LayerHandleRole.DELETE }?.key
                                        val deleteHandleOffset = deletePos?.let { slotOffsets[it] }

                                        // La manija de reordenar es SIEMPRE la primera en probarse
                                        // — dondequiera que esté ahora mismo — y su función cambia
                                        // según el modo:
                                        // - Modo apagado: abre la mini-ventana "Solo aquí"/"Todos".
                                        // - Modo prendido: es una manija MÁS del sistema de
                                        //   intercambio (ver comentario grande más arriba, junto a
                                        //   `leftMid`) — puede recibir un TAP simple (confirma el
                                        //   borrador y sale del modo, ícono check de siempre) o un
                                        //   ARRASTRE (la intercambia de posición con otra manija,
                                        //   sin confirmar ni salir, igual que las otras 7).
                                        //   ARREGLADO A PEDIDO ("activado el reordenamiento, unos
                                        //   milisegundos apretando el ícono Reordenar/check y no se
                                        //   puede arrastrar como los otros"): antes CUALQUIER toque
                                        //   acá confirmaba de una en el `down`, sin esperar a ver si
                                        //   el dedo se movía — por eso nunca llegaba a dispararse el
                                        //   arrastre. Ahora se sigue el MISMO patrón que el bloque de
                                        //   arrastre genérico de más abajo: se espera a soltar el
                                        //   dedo y recién ahí, según si hubo desplazamiento real o
                                        //   no, se decide entre confirmar (tap) o intercambiar
                                        //   (arrastre).
                                        if (hits(reorderHandleOffset)) {
                                            down.consume()
                                            if (reorderMode) {
                                                val startSlot = reorderPos
                                                reorderDragFromPosition = startSlot
                                                reorderDragOffset = down.position
                                                var released = down.position
                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val pr = ev.changes.filter { it.pressed }
                                                    if (pr.isEmpty()) break
                                                    released = pr[0].position
                                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                                    reorderDragOffset = released
                                                }
                                                reorderDragFromPosition = null

                                                if ((released - down.position).getDistance() <= touchRadiusPx) {
                                                    // TAP simple (se soltó donde mismo): confirma el
                                                    // borrador de siempre, comportamiento sin cambios.
                                                    val draft = reorderDraftOrder
                                                    if (draft != null) {
                                                        when (reorderScope) {
                                                            HandleReorderScope.ONLY_HERE -> {
                                                                handleOrderPerLayer = handleOrderPerLayer + (sel.id to draft)
                                                                // Persiste en el proyecto (ver comentario grande
                                                                // junto a encodeHandleOrder/decodeHandleOrder,
                                                                // más arriba) — antes este confirmar solo tocaba
                                                                // la variable local de esta pantalla.
                                                                viewModel.updateHandleOrder("ONLY_HERE", sel.id, encodeHandleOrder(draft))
                                                            }
                                                            HandleReorderScope.ALL -> {
                                                                handleOrderGlobal = draft
                                                                // BUG REAL ENCONTRADO: si esta capa ya tenía
                                                                // su propio orden guardado con "Solo", elegir
                                                                // "Todos" actualizaba el global para las DEMÁS
                                                                // capas, pero esta — la que se estaba editando
                                                                // en ese momento — seguía mostrando su viejo
                                                                // orden "Solo" de antes, porque ese override
                                                                // por-capa sigue ganando por prioridad en
                                                                // `effectiveHandleOrder` (línea ~231). El
                                                                // usuario elegía "Todos" y no veía ningún
                                                                // cambio en la imagen donde estaba parado —
                                                                // parecía que no había funcionado. Se saca el
                                                                // override de ESTA capa para que vuelva a usar
                                                                // el global recién actualizado, y así el
                                                                // cambio se vea de inmediato acá también, no
                                                                // solo en las demás.
                                                                handleOrderPerLayer = handleOrderPerLayer - sel.id
                                                                // Persiste ambos cambios: el nuevo global, y que
                                                                // esta capa deje de tener su propio override
                                                                // (mismo motivo que el BUG REAL de acá arriba).
                                                                viewModel.updateHandleOrder("ALL", null, encodeHandleOrder(draft))
                                                                viewModel.restoreHandleOrder("ONLY_HERE", sel.id)
                                                            }
                                                            null -> {}
                                                        }
                                                    }
                                                    reorderMode = false
                                                    reorderDraftOrder = null
                                                    reorderScope = null
                                                    reorderDragFromPosition = null
                                                } else if (startSlot != null) {
                                                    // ARRASTRE real: intercambia de posición dentro del
                                                    // borrador, igual que el resto de las manijas — el
                                                    // modo reordenar sigue activo, nada se confirma.
                                                    val endSlot = slotOffsets.entries
                                                        .filter { it.key != startSlot }
                                                        .firstOrNull { (released - it.value).getDistance() <= touchRadiusPx }
                                                        ?.key
                                                    if (endSlot != null) {
                                                        val draft = reorderDraftOrder ?: effectiveHandleOrder(sel.id)
                                                        val roleA = draft[startSlot]
                                                        val roleB = draft[endSlot]
                                                        if (roleA != null && roleB != null) {
                                                            reorderDraftOrder = draft + mapOf(startSlot to roleB, endSlot to roleA)
                                                        }
                                                    }
                                                }
                                            } else {
                                                if (showReorderScopeMenu) {
                                                    showReorderScopeMenu = false
                                                } else {
                                                    closeFloatingLayerMenus()
                                                    showReorderScopeMenu = true
                                                }
                                            }
                                            return@awaitEachGesture
                                        }

                                        if (reorderMode) {
                                            // Pedido explícito: mientras el modo reordenar está
                                            // activo, la × que normalmente elimina la capa (rol
                                            // DELETE) pasa a funcionar como CANCELAR. ACTUALIZADO A
                                            // PEDIDO: ya no cancela de una al primer toque — ahora
                                            // solo abre un diálogo de confirmación flotante
                                            // ("¿Cancelar el reordenamiento?", Sí/No). El borrador
                                            // (`reorderDraftOrder`) se descarta recién si el usuario
                                            // confirma (ver diálogo `showCancelReorderConfirm`, más
                                            // abajo); si no confirma, el modo reordenar sigue
                                            // exactamente como estaba, sin perder nada. Se prueba
                                            // ANTES del arrastre genérico de abajo (mismo criterio
                                            // que la manija de reordenar/confirmar, unas líneas
                                            // arriba) para que tocar la × acá sea una acción
                                            // instantánea (abrir el diálogo), no el inicio de un
                                            // arrastre — no tendría sentido "arrastrar" un botón de
                                            // cancelar. Fuera del modo reordenar esta misma posición
                                            // vuelve a su función normal de siempre (ver
                                            // `hitsRole(LayerHandleRole.DELETE)`, más abajo).
                                            if (deleteHandleOffset != null && hits(deleteHandleOffset)) {
                                                down.consume()
                                                showCancelReorderConfirm = true
                                                return@awaitEachGesture
                                            }
                                            // Modo reordenar activo: el canvas queda "congelado" —
                                            // este gesto SOLO puede arrastrar una de las 8 manijas
                                            // (las 7 de siempre + la de reordenar, ahora también
                                            // intercambiable) sobre otra para intercambiarlas de
                                            // lugar (en el borrador `reorderDraftOrder`, todavía sin
                                            // aplicar), nada más — ni mover/rotar/escalar la capa, ni
                                            // cambiar de selección.
                                            val startSlot = slotOffsets.entries.firstOrNull { hits(it.value) }?.key
                                            if (startSlot != null) {
                                                down.consume()
                                                reorderDragFromPosition = startSlot
                                                reorderDragOffset = down.position
                                                var released = down.position
                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val pr = ev.changes.filter { it.pressed }
                                                    if (pr.isEmpty()) break
                                                    released = pr[0].position
                                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                                    reorderDragOffset = released
                                                }
                                                reorderDragFromPosition = null
                                                val endSlot = slotOffsets.entries
                                                    .filter { it.key != startSlot }
                                                    .firstOrNull { (released - it.value).getDistance() <= touchRadiusPx }
                                                    ?.key
                                                if (endSlot != null) {
                                                    val draft = reorderDraftOrder ?: effectiveHandleOrder(sel.id)
                                                    val roleA = draft[startSlot]
                                                    val roleB = draft[endSlot]
                                                    if (roleA != null && roleB != null) {
                                                        reorderDraftOrder = draft + mapOf(startSlot to roleB, endSlot to roleA)
                                                    }
                                                }
                                            }
                                            return@awaitEachGesture
                                        }

                                        // Fuera del modo reordenar: qué manija física hace qué
                                        // función depende del orden vigente (por-capa si existe,
                                        // si no el global — ver [effectiveHandleOrder]). El resto
                                        // del bloque de abajo queda EXACTAMENTE igual que antes —
                                        // solo cambió CÓMO se decide a qué función corresponde cada
                                        // toque, nunca la función en sí.
                                        val liveOrder = effectiveHandleOrder(sel.id)
                                        fun hitsRole(role: LayerHandleRole): Boolean {
                                            val pos = liveOrder.entries.firstOrNull { it.value == role }?.key ?: return false
                                            val offset = slotOffsets[pos] ?: return false
                                            return hits(offset)
                                        }

                                        when {
                                            hitsRole(LayerHandleRole.RESTORE) -> {
                                                // Superior, medio: abre la mini-ventana "Restablecer
                                                // transformación" con las 6 opciones (Todo / Rotación /
                                                // Eje / Zoom / Ancho / Alto) en vez de resetear todo de
                                                // una — así el usuario elige exactamente qué corregir
                                                // (p. ej. enderezar el ángulo sin perder un ancho
                                                // estirado a propósito). La lógica de cada opción vive
                                                // en el diálogo, más abajo en este archivo.
                                                down.consume()
                                                closeFloatingLayerMenus()
                                                showRestoreOptionsMenu = true
                                                return@awaitEachGesture
                                            }
                                            hitsRole(LayerHandleRole.DELETE) -> {
                                                // Esquina sup. derecha (por defecto): eliminar la
                                                // capa. ARREGLADO: antes borraba directo con
                                                // viewModel.removeLayer(sel.id), sin avisar — la
                                                // ÚNICA vía de borrado que no pedía confirmación en
                                                // toda la app, justo la más fácil de tocar sin
                                                // querer (una esquina del marco, en pleno gesto de
                                                // edición). Ahora dispara el mismo diálogo "¿Eliminar
                                                // esta capa?" (Cancelar/Eliminar) que ya usa el
                                                // ícono de borrar de la barra de cada capa, más
                                                // abajo en el timeline — mismo criterio en los dos
                                                // lugares donde se puede borrar una capa.
                                                //
                                                // ACTUALIZADO A PEDIDO: mientras la capa está en
                                                // modo edición aislado (`editModeLayerId == sel.id`,
                                                // ver `enterEditModeForSelectedLayer`), esta misma X
                                                // deja de borrar y pasa a funcionar como "Cancelar"
                                                // — sale del modo edición SIN guardar (a diferencia
                                                // del check de la manija Editar, que sí confirma).
                                                // Fuera de modo edición, la X sigue siendo
                                                // exactamente "eliminar capa", sin cambios.
                                                //
                                                // ACTUALIZADO A PEDIDO (2): igual que la × del modo
                                                // reordenar, ya no sale del modo edición de una al
                                                // primer toque — antes abre un diálogo avisando que
                                                // la edición no se va a aplicar, y recién sale si el
                                                // usuario confirma (ver `showCancelEditModeConfirm`,
                                                // diálogo más abajo).
                                                down.consume()
                                                if (latestEditModeLayerId.value == sel.id) {
                                                    showCancelEditModeConfirm = true
                                                } else {
                                                    layerPendingDelete = sel
                                                }
                                                return@awaitEachGesture
                                            }
                                            hitsRole(LayerHandleRole.EDIT) -> {
                                                // Esquina sup. izquierda: al
                                                // tocarla por primera vez
                                                // entra directo a modo
                                                // edición de la capa (sin
                                                // ventana ni menú intermedio
                                                // — ver comentario anterior).
                                                // ACTUALIZADO a pedido: esta
                                                // misma manija ahora hace
                                                // doble función — mientras
                                                // la capa YA está en modo
                                                // edición, el ícono cambia a
                                                // un check (ver
                                                // `drawGlyphForRole`/
                                                // `isEditingThisLayer` en el
                                                // Canvas de dibujo más abajo)
                                                // y tocarla otra vez CONFIRMA
                                                // y sale del modo — reemplaza
                                                // al chip "Editando imagen X"
                                                // que se sacó de la cabecera
                                                // de la imagen (pedido
                                                // explícito: "en lugar del
                                                // ícono editar cambie a un
                                                // check... para guardar esa
                                                // imagen editada").
                                                down.consume()
                                                closeFloatingLayerMenus()
                                                if (latestEditModeLayerId.value == sel.id) {
                                                    exitEditMode()
                                                } else {
                                                    enterEditModeForSelectedLayer(sel)
                                                }
                                                return@awaitEachGesture
                                            }
                                            hitsRole(LayerHandleRole.ROTATE) -> {
                                                // Esquina inf. izquierda: girar arrastrando alrededor del centro de la capa.
                                                //
                                                // ARREGLADO: la dirección estaba invertida — arrastrar
                                                // hacia un lado giraba la capa hacia el lado contrario.
                                                // Causa real: `angle`/`deltaAngle` se calculan con
                                                // atan2 sobre coordenadas de PANTALLA (Y crece hacia
                                                // abajo), lo que da un ángulo que crece en sentido
                                                // HORARIO al arrastrar el dedo en sentido horario. Pero
                                                // `rotation` se renderiza (LayerDrawer/GLRenderer y el
                                                // propio cálculo del marco de selección más abajo, en
                                                // NDC con Y hacia arriba) con la convención contraria:
                                                // un `rotation` positivo gira la capa en sentido
                                                // ANTIHORARIO en pantalla. Sumar `deltaAngle` directo a
                                                // `rotation` mezclaba ambas convenciones sin
                                                // compensar, y el resultado era un giro en espejo del
                                                // gesto del dedo. La resta (en vez de la suma) es
                                                // exactamente esa compensación: ahora arrastrar en
                                                // sentido horario gira la capa en sentido horario, y
                                                // viceversa — el dedo y la imagen giran para el mismo
                                                // lado, como en cualquier editor (CapCut, Canva, etc.).
                                                down.consume()
                                                var prevAngle = Math.toDegrees(
                                                    atan2((down.position.y - centerPx.y).toDouble(), (down.position.x - centerPx.x).toDouble())
                                                ).toFloat()
                                                var lastCommitAtMs = 0L
                                                var pendingCommit = false
                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val pr = ev.changes.filter { it.pressed }
                                                    if (pr.isEmpty()) break
                                                    val p = pr[0].position
                                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                                    val angle = Math.toDegrees(
                                                        atan2((p.y - centerPx.y).toDouble(), (p.x - centerPx.x).toDouble())
                                                    ).toFloat()
                                                    var deltaAngle = angle - prevAngle
                                                    if (deltaAngle > 180f) deltaAngle -= 360f
                                                    if (deltaAngle < -180f) deltaAngle += 360f
                                                    val rawRotation = normalizeRotationDeg(rotation - deltaAngle)
                                                    // --- Snap de ángulo cada 15° (0°, 15°,
                                                    // 30°...) — mismo estándar que Figma/
                                                    // Sketch/Illustrator, ver
                                                    // [snapRotationToFifteenDegrees]. El HUD
                                                    // numérico (Text flotante junto al resto
                                                    // de HUDs de gesto, más abajo) muestra el
                                                    // múltiplo enganchado mientras dure.
                                                    val (snappedRotation, snappedDeg) = snapRotationToFifteenDegrees(rawRotation)
                                                    rotation = snappedRotation
                                                    activeRotationSnapDeg = snappedDeg
                                                    prevAngle = angle
                                                    pendingCommit = true
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCommitAtMs >= 120L) {
                                                        commitLiveFrame(); lastCommitAtMs = now; pendingCommit = false
                                                    }
                                                }
                                                if (pendingCommit) commitLiveFrame()
                                                activeRotationSnapDeg = null
                                                return@awaitEachGesture
                                            }
                                            hitsRole(LayerHandleRole.RESIZE_UNIFORM) -> {
                                                // Esquina inf. derecha: agrandar/achicar arrastrando (alternativa al
                                                // pellizco de dos dedos, que sigue funcionando exactamente igual que antes).
                                                down.consume()
                                                var prevDist = (down.position - centerPx).getDistance().coerceAtLeast(1f)
                                                var lastCommitAtMs = 0L
                                                var pendingCommit = false
                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val pr = ev.changes.filter { it.pressed }
                                                    if (pr.isEmpty()) break
                                                    val p = pr[0].position
                                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                                    val dist = (p - centerPx).getDistance().coerceAtLeast(1f)
                                                    val rawScale = (scale * (dist / prevDist)).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
                                                    prevDist = dist

                                                    // --- Guías inteligentes al redimensionar
                                                    // (ver [computeAlignmentSnapForResize] en
                                                    // AlignmentGuides.kt): el centro de la capa
                                                    // NO se mueve acá, lo que se engancha es el
                                                    // BORDE. Se prueba cada eje por separado
                                                    // contra el `rawScale` recién calculado —
                                                    // como el resize uniforme escala ambos ejes
                                                    // con el MISMO factor (para no deformar la
                                                    // proporción), si los dos ejes piden un
                                                    // ajuste a la vez se usa el que requiera el
                                                    // cambio más chico de escala.
                                                    val boxWidthPxUniform = boxSize.width.toFloat()
                                                    val boxHeightPxUniform = boxSize.height.toFloat()
                                                    val (halfWPxRaw, halfHPxRaw) = layerAabbHalfExtentsPx(
                                                        imageWidthPx = sel.widthPx,
                                                        imageHeightPx = sel.heightPx,
                                                        scale = rawScale,
                                                        scaleX = scaleX,
                                                        scaleY = scaleY,
                                                        rotationDeg = rotation,
                                                        boxWidthPx = boxWidthPxUniform,
                                                        boxHeightPx = boxHeightPxUniform
                                                    )
                                                    val snapThresholdPxUniform = ALIGNMENT_SNAP_THRESHOLD_DP.dp.toPx()
                                                    val (snappedHalfW, lineX) = computeAlignmentSnapForResize(
                                                        halfWPxRaw, centerPx.x, boxWidthPxUniform, snapThresholdPxUniform
                                                    )
                                                    val (snappedHalfH, lineY) = computeAlignmentSnapForResize(
                                                        halfHPxRaw, centerPx.y, boxHeightPxUniform, snapThresholdPxUniform
                                                    )
                                                    val scaleFromX = if (lineX != null && halfWPxRaw != 0f) rawScale * (snappedHalfW / halfWPxRaw) else null
                                                    val scaleFromY = if (lineY != null && halfHPxRaw != 0f) rawScale * (snappedHalfH / halfHPxRaw) else null
                                                    var finalScale = rawScale
                                                    var guideX: Float? = null
                                                    var guideY: Float? = null
                                                    when {
                                                        scaleFromX != null && scaleFromY != null -> {
                                                            if (kotlin.math.abs(scaleFromX - rawScale) <= kotlin.math.abs(scaleFromY - rawScale)) {
                                                                finalScale = scaleFromX; guideX = lineX
                                                            } else {
                                                                finalScale = scaleFromY; guideY = lineY
                                                            }
                                                        }
                                                        scaleFromX != null -> { finalScale = scaleFromX; guideX = lineX }
                                                        scaleFromY != null -> { finalScale = scaleFromY; guideY = lineY }
                                                    }
                                                    scale = finalScale.coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
                                                    activeSnapGuideXPx = guideX
                                                    activeSnapGuideYPx = guideY

                                                    pendingCommit = true
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCommitAtMs >= 120L) {
                                                        commitLiveFrame(); lastCommitAtMs = now; pendingCommit = false
                                                    }
                                                }
                                                if (pendingCommit) commitLiveFrame()
                                                activeSnapGuideXPx = null
                                                activeSnapGuideYPx = null
                                                return@awaitEachGesture
                                            }
                                            hitsRole(LayerHandleRole.RESIZE_WIDTH) -> {
                                                // Lateral derecha, medio: estirar/apretar solo el ANCHO.
                                                //
                                                // ARREGLADO: antes se trabajaba siempre con
                                                // valores ABSOLUTOS (kotlin.math.abs) de la
                                                // distancia al centro, y el resultado se
                                                // recortaba con .coerceIn(MIN_LAYER_SCALE,
                                                // MAX_LAYER_SCALE) — ambos SIEMPRE positivos.
                                                // Eso significaba que, por más que se arrastrara
                                                // la manija hacia (y más allá) del centro, scaleX
                                                // nunca podía cruzar 0 ni volverse negativo: se
                                                // quedaba pegado en MIN_LAYER_SCALE (una capa
                                                // angosta) sin nunca voltear la imagen. Pedido
                                                // explícito: al ACHICAR (arrastrar la manija hacia
                                                // y más allá del centro), la capa tiene que
                                                // VOLTEARSE horizontalmente (flip), como en
                                                // cualquier editor (CapCut, Canva, etc.) — el motor
                                                // de render ya soporta esto de forma nativa, un
                                                // scaleX negativo en la matriz de escala GL
                                                // produce exactamente un flip horizontal
                                                // (LayerDrawer.kt, Matrix.scaleM).
                                                //
                                                // La solución: en vez de una razón INCREMENTAL
                                                // cuadro-a-cuadro sobre valores absolutos, se usa
                                                // una razón sobre la posición X con signo,
                                                // relativa al punto donde se agarró la manija
                                                // (baseDx, siempre del lado derecho > 0 al
                                                // empezar). Mientras el dedo sigue del lado
                                                // derecho del centro, la razón es positiva
                                                // (estira/achica normal, igual que antes). Al
                                                // cruzar el centro hacia la izquierda, la razón se
                                                // vuelve negativa y scaleX pasa a negativo — ahí
                                                // ocurre el flip. La MAGNITUD sigue recortada
                                                // entre MIN_LAYER_SCALE y MAX_LAYER_SCALE (para
                                                // que el motor nunca reciba una escala 0), solo
                                                // que ahora el SIGNO queda libre.
                                                down.consume()
                                                val scaleXAtGrab = scaleX
                                                val rawBaseDx = down.position.x - centerPx.x
                                                val baseDx = if (kotlin.math.abs(rawBaseDx) < 1f) {
                                                    if (rawBaseDx >= 0f) 1f else -1f
                                                } else rawBaseDx
                                                var lastCommitAtMs = 0L
                                                var pendingCommit = false
                                                val boxWidthPxW = boxSize.width.toFloat()
                                                val boxHeightPxW = boxSize.height.toFloat()
                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val pr = ev.changes.filter { it.pressed }
                                                    if (pr.isEmpty()) break
                                                    val p = pr[0].position
                                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                                    val currentDx = p.x - centerPx.x
                                                    val ratioSigned = currentDx / baseDx
                                                    val rawScaleX = scaleXAtGrab * ratioSigned
                                                    val mag = kotlin.math.abs(rawScaleX).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)

                                                    // --- Guía inteligente al estirar el ancho:
                                                    // el borde que sigue al dedo (derecho o,
                                                    // tras cruzar el centro con el flip,
                                                    // izquierdo — ver `draggedSide`) se engancha
                                                    // al centro/bordes del lienzo. Se resuelve
                                                    // con dos muestras porque, con rotación, el
                                                    // semi-ancho no es puramente proporcional a
                                                    // `mag` — ver [solveScaleMagnitudeForTargetHalfExtent].
                                                    val (halfWAtMag, _) = layerAabbHalfExtentsPx(
                                                        imageWidthPx = sel.widthPx, imageHeightPx = sel.heightPx,
                                                        scale = scale, scaleX = mag, scaleY = scaleY,
                                                        rotationDeg = rotation, boxWidthPx = boxWidthPxW, boxHeightPx = boxHeightPxW
                                                    )
                                                    val probeMagW = mag + 0.05f
                                                    val (halfWAtProbe, _) = layerAabbHalfExtentsPx(
                                                        imageWidthPx = sel.widthPx, imageHeightPx = sel.heightPx,
                                                        scale = scale, scaleX = probeMagW, scaleY = scaleY,
                                                        rotationDeg = rotation, boxWidthPx = boxWidthPxW, boxHeightPx = boxHeightPxW
                                                    )
                                                    val draggedSide = if (currentDx >= 0f) 1f else -1f
                                                    val (snappedHalfExtent, lineX) = computeAlignmentSnapForResize(
                                                        draggedSide * halfWAtMag, centerPx.x, boxWidthPxW, ALIGNMENT_SNAP_THRESHOLD_DP.dp.toPx()
                                                    )
                                                    val finalMag = if (lineX != null) {
                                                        solveScaleMagnitudeForTargetHalfExtent(
                                                            currentMag = mag,
                                                            currentHalfExtentPx = halfWAtMag,
                                                            probeMag = probeMagW,
                                                            probeHalfExtentPx = halfWAtProbe,
                                                            targetHalfExtentPx = kotlin.math.abs(snappedHalfExtent)
                                                        ).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
                                                    } else mag
                                                    scaleX = if (rawScaleX >= 0f) finalMag else -finalMag
                                                    activeSnapGuideXPx = lineX
                                                    activeSnapGuideYPx = null

                                                    pendingCommit = true
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCommitAtMs >= 120L) {
                                                        commitLiveFrame(); lastCommitAtMs = now; pendingCommit = false
                                                    }
                                                }
                                                if (pendingCommit) commitLiveFrame()
                                                activeSnapGuideXPx = null
                                                return@awaitEachGesture
                                            }
                                            hitsRole(LayerHandleRole.RESIZE_HEIGHT) -> {
                                                // Inferior, medio: estirar/apretar solo el ALTO.
                                                // Mismo criterio y mismo motivo que en `rightMid`
                                                // arriba (ver comentario completo ahí): ahora
                                                // ACHICAR más allá del centro voltea la capa
                                                // verticalmente (scaleY negativo = flip vertical
                                                // en el motor GL), en vez de quedar pegado en el
                                                // piso de escala sin voltear nunca.
                                                down.consume()
                                                val scaleYAtGrab = scaleY
                                                val rawBaseDy = down.position.y - centerPx.y
                                                val baseDy = if (kotlin.math.abs(rawBaseDy) < 1f) {
                                                    if (rawBaseDy >= 0f) 1f else -1f
                                                } else rawBaseDy
                                                var lastCommitAtMs = 0L
                                                var pendingCommit = false
                                                val boxWidthPxH = boxSize.width.toFloat()
                                                val boxHeightPxH = boxSize.height.toFloat()
                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val pr = ev.changes.filter { it.pressed }
                                                    if (pr.isEmpty()) break
                                                    val p = pr[0].position
                                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                                    val currentDy = p.y - centerPx.y
                                                    val ratioSigned = currentDy / baseDy
                                                    val rawScaleY = scaleYAtGrab * ratioSigned
                                                    val mag = kotlin.math.abs(rawScaleY).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)

                                                    // --- Guía inteligente al estirar el alto —
                                                    // mismo criterio que RESIZE_WIDTH (ver el
                                                    // comentario completo ahí); acá interesa el
                                                    // segundo valor del par (semi-ALTO), no el
                                                    // primero.
                                                    val halfHAtMag = layerAabbHalfExtentsPx(
                                                        imageWidthPx = sel.widthPx, imageHeightPx = sel.heightPx,
                                                        scale = scale, scaleX = scaleX, scaleY = mag,
                                                        rotationDeg = rotation, boxWidthPx = boxWidthPxH, boxHeightPx = boxHeightPxH
                                                    ).second
                                                    val probeMagH = mag + 0.05f
                                                    val halfHAtProbe = layerAabbHalfExtentsPx(
                                                        imageWidthPx = sel.widthPx, imageHeightPx = sel.heightPx,
                                                        scale = scale, scaleX = scaleX, scaleY = probeMagH,
                                                        rotationDeg = rotation, boxWidthPx = boxWidthPxH, boxHeightPx = boxHeightPxH
                                                    ).second
                                                    val draggedSide = if (currentDy >= 0f) 1f else -1f
                                                    val (snappedHalfExtent, lineY) = computeAlignmentSnapForResize(
                                                        draggedSide * halfHAtMag, centerPx.y, boxHeightPxH, ALIGNMENT_SNAP_THRESHOLD_DP.dp.toPx()
                                                    )
                                                    val finalMag = if (lineY != null) {
                                                        solveScaleMagnitudeForTargetHalfExtent(
                                                            currentMag = mag,
                                                            currentHalfExtentPx = halfHAtMag,
                                                            probeMag = probeMagH,
                                                            probeHalfExtentPx = halfHAtProbe,
                                                            targetHalfExtentPx = kotlin.math.abs(snappedHalfExtent)
                                                        ).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
                                                    } else mag
                                                    scaleY = if (rawScaleY >= 0f) finalMag else -finalMag
                                                    activeSnapGuideYPx = lineY
                                                    activeSnapGuideXPx = null

                                                    pendingCommit = true
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCommitAtMs >= 120L) {
                                                        commitLiveFrame(); lastCommitAtMs = now; pendingCommit = false
                                                    }
                                                }
                                                if (pendingCommit) commitLiveFrame()
                                                activeSnapGuideYPx = null
                                                return@awaitEachGesture
                                            }
                                        }
                                    }
                                }
                            }

                            // --- Canvas "congelado" durante el modo
                            // reordenar manijas: ya se probaron arriba la
                            // manija fija de reordenar/confirmar y las 7
                            // manijas intercambiables (si el toque cayó
                            // sobre alguna, ya se resolvió con su propio
                            // return más arriba). Un toque en cualquier
                            // OTRO lugar del canvas — la imagen, el fondo,
                            // otra capa — no debe hacer nada mientras este
                            // modo sigue activo: nada de seleccionar,
                            // mover, escalar ni rotar, tal como se pidió
                            // ("que solo en esa imagen se pueda configurar
                            // y reorganizar los iconos").
                            if (latestReorderModeEnabled.value) {
                                down.consume()
                                return@awaitEachGesture
                            }

                            // --- BUG REAL ENCONTRADO Y ARREGLADO (versión
                            // anterior de este mismo arreglo): la primera
                            // vez que se atacó este bug, la solución fue
                            // "congelar" el canvas entero mientras
                            // `editModeLayerId != null` (mismo criterio que
                            // el modo reordenar, ver arriba) — bloqueaba
                            // CUALQUIER gesto, incluido mover/pellizcar/
                            // girar la propia capa aislada, dejando solo
                            // las manijas como única forma de tocarla. Eso
                            // sí evitaba "escaparse" del modo edición, pero
                            // sacrificaba toda la libertad de gestos que
                            // pedía el usuario ("muy limitado, devuelve esa
                            // flexibilidad"). Ya NO hace falta ese freno:
                            // ahora que `hitTestLayers` (arriba) restringe
                            // el hit-test a solo la capa aislada, `hitLayerId`
                            // nunca puede apuntar a otra capa mientras este
                            // modo está activo — así que el código de
                            // selección/arrastre/pellizco/giro de más abajo
                            // puede correr TAL CUAL (sin este guard) y sigue
                            // operando siempre sobre la misma capa, nunca
                            // cambia de selección ni sale del modo. Lo único
                            // que sí seguía haciendo falta tapar es el "tocar
                            // un hueco vacío deselecciona todo" (ver
                            // `viewModel.clearSelection()` más abajo, ahora
                            // con el mismo guard `editIdForHitTest == null`).

                            // --- ARREGLADO: antes, tocar una capa DISTINTA
                            // a la seleccionada solo la seleccionaba y
                            // "drenaba" (consumía sin hacer nada) el resto
                            // de ese mismo toque continuo — había que
                            // LEVANTAR el dedo y volver a tocar para recién
                            // ahí poder moverla. En la práctica, un
                            // toque-y-arrastre normal (sin levantar el
                            // dedo) sobre una capa distinta no hacía
                            // NADA visible: ni parecía seleccionarla ni
                            // moverla — exactamente el bug reportado. Ahora
                            // seleccionar y arrastrar viven en el MISMO
                            // toque continuo, como cualquier editor mobile
                            // (CapCut, Canva, etc.): se selecciona la capa
                            // nueva Y ADEMÁS se sincronizan translateX/Y/
                            // scale/rotation/etc. leyendo su keyframe
                            // ACTUAL del modelo ahí mismo (sin esperar al
                            // LaunchedEffect(state.selectedLayerId), que es
                            // asíncrono y llegaría un frame tarde), para
                            // que el arrastre que sigue abajo ya opere
                            // sobre los valores correctos de la capa recién
                            // elegida desde el primer movimiento del dedo.
                            var layer = latestSelectedLayerForDrag.value
                            if (hitLayerId != null && hitLayerId != latestSelectedLayerId.value) {
                                viewModel.selectLayer(hitLayerId)
                                val newLayer = latestLayersForHitTest.value.firstOrNull { it.id == hitLayerId }
                                val newFrame = newLayer?.let { viewModel.frameAt(it, latestPlayheadForHitTest.value) }
                                translateX = newFrame?.translateX ?: 0f
                                translateY = newFrame?.translateY ?: 0f
                                scale = newFrame?.scale ?: 1f
                                rotation = newFrame?.rotationDeg ?: 0f
                                alpha = newFrame?.alpha ?: 1f
                                tiltX = newFrame?.tiltXDeg ?: 0f
                                tiltY = newFrame?.tiltYDeg ?: 0f
                                focusBlur = newFrame?.focusBlur ?: 0f
                                dollyZoom = newFrame?.dollyZoom ?: 0f
                                scaleX = newFrame?.scaleX ?: 1f
                                scaleY = newFrame?.scaleY ?: 1f
                                syncTick++
                                layer = newLayer
                            }

                            // El toque cayó sobre la capa YA seleccionada,
                            // sobre la que se acaba de seleccionar recién
                            // arriba, o no tocó ninguna capa (para poder
                            // pellizcar aunque el dedo arranque en un hueco
                            // vacío del canvas): mover/pellizcar/rotar con
                            // libertad.
                            if (layer != null && layer.locked) return@awaitEachGesture

                            var previousCentroid: Offset? = null
                            var previousSpan: Float? = null
                            var previousAngle: Float? = null
                            var previousPressedCount = 0
                            // --- Snap magnético SIN trabas: `rawTranslateX/Y`
                            // acumulan la posición REAL que pide el dedo,
                            // frame a frame, ignorando por completo si hubo
                            // snap o no — es la misma cuenta de siempre
                            // (translateX + delta), solo que ahora vive
                            // aparte. `translateX`/`translateY` (el estado
                            // real de Compose) pasan a ser el resultado de
                            // aplicarle el snap a ese valor crudo, SOLO
                            // para mostrar/guardar.
                            //
                            // Por qué hacía falta: la primera versión
                            // snappeaba directo sobre `translateX` y volvía
                            // a sumarle el delta del dedo la vuelta
                            // siguiente. Mientras la capa quedaba pegada a
                            // una línea, el dedo se seguía moviendo por
                            // afuera de esa imantación — pero el próximo
                            // delta se sumaba sobre el valor YA imantado,
                            // no sobre dónde el dedo estaba de verdad. Eso
                            // hacía que el desenganche se sintiera tarde,
                            // brusco o directamente no pasara ("no engancha
                            // aunque pase cerca" / "tiembla o salta al
                            // soltar", justo lo reportado). Con la cuenta
                            // cruda aparte, el desenganche se decide SIEMPRE
                            // contra la posición real del dedo, así que es
                            // instantáneo y estable en cualquier dirección.
                            var rawTranslateX = translateX
                            var rawTranslateY = translateY
                            // --- Distancia total recorrida por el dedo desde
                            // que bajó, para poder distinguir un TOQUE real
                            // (deselecciona si cayó en un hueco vacío) de un
                            // pellizco/paneo que arrancó en ese mismo hueco
                            // vacío (no debe deseleccionar nada, solo mover
                            // la capa ya seleccionada). Ver uso más abajo.
                            var totalMovementPx = 0f
                            // --- Menos lag durante el arrastre: escribir
                            // en el ViewModel (que dispara recomposición
                            // de TODA la lista de capas + programa
                            // autoguardado) en CADA evento de movimiento
                            // del dedo es innecesario — el feedback visual
                            // durante el arrastre ya viene de las
                            // variables locales (translateX, etc.) vía
                            // getLiveOverride, no de esta escritura. Se
                            // persiste como mucho cada ~50ms mientras se
                            // mueve, y siempre una vez más al soltar el
                            // dedo, para no perder la posición final.
                            var lastCommitAtMs = 0L
                            var pendingCommit = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                var sumX = 0f
                                var sumY = 0f
                                for (c in pressed) {
                                    sumX += c.position.x
                                    sumY += c.position.y
                                }
                                val centroid = Offset(sumX / pressed.size, sumY / pressed.size)

                                val prevCentroid = previousCentroid
                                // --- ARREGLADO: "al hacer zoom (pellizco) y
                                // soltar, la capa se sube/baja un poco". Al
                                // soltar un pellizco los dos dedos casi
                                // nunca se levantan en el MISMO evento — uno
                                // se levanta un instante antes que el otro.
                                // En ese evento intermedio la cantidad de
                                // dedos apoyados cambia de 2 a 1, y el
                                // centroide se recalcula con un solo punto:
                                // salta de golpe a la posición de ESE dedo
                                // suelto, aunque ningún dedo se haya movido
                                // realmente. Ese salto de centroide se
                                // traducía directo en un salto de
                                // translateX/Y — justo el bug reportado.
                                // Ahora, si la cantidad de dedos cambió
                                // desde el evento anterior, este frame SOLO
                                // actualiza la base (centroide/separación/
                                // ángulo) sin aplicar ningún delta; el
                                // seguimiento normal retoma recién en el
                                // próximo evento, ya con una base
                                // consistente para esa nueva cantidad de
                                // dedos.
                                val fingerCountChanged = pressed.size != previousPressedCount
                                if (prevCentroid != null && !fingerCountChanged) {
                                    val boxWidth = size.width.toFloat().coerceAtLeast(1f)
                                    val boxHeight = size.height.toFloat().coerceAtLeast(1f)
                                    val panDx = centroid.x - prevCentroid.x
                                    val panDy = centroid.y - prevCentroid.y
                                    totalMovementPx += hypot(panDx, panDy)
                                    // --- Base cruda: SIEMPRE sigue al dedo
                                    // 1:1, nunca se ve afectada por un snap
                                    // anterior (ver comentario grande más
                                    // arriba, junto a la declaración de
                                    // rawTranslateX/Y).
                                    //
                                    // CORREGIDO: el límite de paneo (antes
                                    // fijo en ±2f) ahora es proporcional al
                                    // zoom actual de la capa (`scale`,
                                    // `scaleX`, `scaleY`) — con la fórmula
                                    // vieja, una vez la imagen estaba muy
                                    // agrandada (zoom alto), la mitad de la
                                    // imagen quedaba permanentemente fuera de
                                    // alcance: no había forma de arrastrar lo
                                    // suficiente para llegar a ver el borde
                                    // de arriba (o abajo/costados), por más
                                    // que se siguiera arrastrando — el
                                    // `.coerceIn(-2f, 2f)` cortaba el
                                    // recorrido mucho antes de llegar. Con
                                    // `panLimit` creciendo junto con el zoom,
                                    // siempre queda suficiente margen para
                                    // panear hasta cualquier borde de la
                                    // imagen, sin importar cuán zoomeada
                                    // esté. En `scale = 1` (sin zoom, el caso
                                    // de siempre) da exactamente 2f — mismo
                                    // comportamiento que antes, cero cambios
                                    // para el caso normal.
                                    val panLimit = 2f * maxOf(scale, scaleX, scaleY, 1f)
                                    rawTranslateX = (rawTranslateX + (panDx / boxWidth) * 2f).coerceIn(-panLimit, panLimit)
                                    rawTranslateY = (rawTranslateY - (panDy / boxHeight) * 2f).coerceIn(-panLimit, panLimit)

                                    // --- Snap magnético a la cuadrícula (ver
                                    // [snapTranslateToGrid]): solo con una
                                    // capa realmente seleccionada (`layer`,
                                    // no un paneo que arrancó en un hueco
                                    // vacío), la cuadrícula activa Y el
                                    // switch "Snap" del menú prendido —
                                    // ambos, no alcanza con uno solo (ver
                                    // comentario en GridMenu). Si no hay
                                    // snap posible/activo, `translateX/Y`
                                    // (lo que se ve y se guarda) queda
                                    // directo en el valor crudo — exactamente
                                    // el comportamiento de siempre, capa
                                    // pegada al dedo sin ningún desvío.
                                    // Umbral de 14dp — perceptible al ojo
                                    // como "imán", sin sentirse pegajoso ni
                                    // trabar el arrastre libre normal.
                                    if (layer != null) {
                                        // --- AABB real de la capa (con
                                        // rotación/escala actuales) para
                                        // poder ofrecer sus 3 puntos de
                                        // referencia por eje (borde
                                        // inicial, centro, borde final) —
                                        // ver [layerAabbHalfExtentsPx].
                                        // Se calcula UNA vez y se
                                        // reutiliza tanto para las guías
                                        // inteligentes (siempre activas)
                                        // como para la cuadrícula manual
                                        // (si está prendida), justo abajo.
                                        val (halfWidthPx, halfHeightPx) = layerAabbHalfExtentsPx(
                                            imageWidthPx = layer.widthPx,
                                            imageHeightPx = layer.heightPx,
                                            scale = scale,
                                            scaleX = scaleX,
                                            scaleY = scaleY,
                                            rotationDeg = rotation,
                                            boxWidthPx = boxWidth,
                                            boxHeightPx = boxHeight
                                        )

                                        // 1) Guías inteligentes de
                                        // alineación al centro/bordes del
                                        // LIENZO — SIEMPRE activas, sin
                                        // gate de ningún switch (ver
                                        // comentario grande en
                                        // AlignmentGuides.kt sobre por qué
                                        // es independiente de la
                                        // cuadrícula manual de más abajo).
                                        val alignSnap = computeAlignmentSnapForDrag(
                                            translateX = rawTranslateX,
                                            translateY = rawTranslateY,
                                            parallaxFactor = layer.parallaxFactor,
                                            halfWidthPx = halfWidthPx,
                                            halfHeightPx = halfHeightPx,
                                            boxWidthPx = boxWidth,
                                            boxHeightPx = boxHeight,
                                            snapThresholdPx = ALIGNMENT_SNAP_THRESHOLD_DP.dp.toPx()
                                        )
                                        activeSnapGuideXPx = alignSnap.guideLineXPx
                                        activeSnapGuideYPx = alignSnap.guideLineYPx

                                        // 2) Cuadrícula manual (si está
                                        // prendida): mismo comportamiento
                                        // de siempre, ahora aplicada SOBRE
                                        // el resultado ya alineado por (1)
                                        // en vez de sobre el valor crudo —
                                        // así ambos sistemas conviven sin
                                        // pisarse ni pelearse por el
                                        // mismo eje.
                                        if (latestGridEnabledForSnap.value && latestGridSnapEnabledForSnap.value) {
                                            val (snappedX, snappedY) = snapTranslateToGrid(
                                                translateX = alignSnap.translateX,
                                                translateY = alignSnap.translateY,
                                                parallaxFactor = layer.parallaxFactor,
                                                halfWidthPx = halfWidthPx,
                                                halfHeightPx = halfHeightPx,
                                                boxWidthPx = boxWidth,
                                                boxHeightPx = boxHeight,
                                                shape = latestGridShapeForSnap.value,
                                                spec = latestGridSpecForSnap.value,
                                                snapThresholdPx = 14.dp.toPx()
                                            )
                                            translateX = snappedX
                                            translateY = snappedY
                                        } else {
                                            translateX = alignSnap.translateX
                                            translateY = alignSnap.translateY
                                        }
                                    } else {
                                        translateX = rawTranslateX
                                        translateY = rawTranslateY
                                        activeSnapGuideXPx = null
                                        activeSnapGuideYPx = null
                                    }

                                    if (pressed.size >= 2) {
                                        // --- Zoom con dos dedos: pedido
                                        // explícito de que funcione SIEMPRE,
                                        // esté o no activo "Edición >
                                        // Imagen" — antes todo este bloque
                                        // (escala Y rotación) estaba atado a
                                        // `latestEdicionImagenEnabled.value`,
                                        // así que con el modo apagado el
                                        // pellizco no hacía nada más que
                                        // panear. Ahora el ACERCAR/ALEJAR
                                        // con los dedos queda desacoplado de
                                        // ese modo — funciona siempre que
                                        // haya 2+ dedos — mientras que la
                                        // ROTACIÓN con dos dedos sigue
                                        // exclusivamente detrás de "Edición
                                        // > Imagen" activado, tal como se
                                        // pidió ("nada de que jire, solo
                                        // zoom" con el modo apagado). Ningún
                                        // otro gesto (manijas, iconos) se ve
                                        // afectado: siguen dependiendo del
                                        // modo igual que antes.
                                        var sumDist = 0f
                                        for (c in pressed) {
                                            sumDist += hypot(c.position.x - centroid.x, c.position.y - centroid.y)
                                        }
                                        val span = sumDist / pressed.size
                                        val prevSpan = previousSpan
                                        if (prevSpan != null && prevSpan > 1f) {
                                            scale = (scale * (span / prevSpan)).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
                                        }
                                        previousSpan = span

                                        // A PEDIDO EXPLÍCITO DEL USUARIO —
                                        // dentro del modo edición aislado de
                                        // una capa (`editModeLayerId` — ver
                                        // el comentario grande sobre las 8
                                        // manijas, más arriba en este mismo
                                        // `pointerInput`) el pellizco con dos
                                        // dedos tiene que ser SOLO zoom +
                                        // mover, "sin girar ni estirar ni
                                        // nada" — nunca girar, esté o no
                                        // prendida "Edición > Imagen" (esa
                                        // opción es del canvas PRINCIPAL, no
                                        // de esta vista aislada). Se agrega
                                        // `latestEditModeLayerId.value ==
                                        // null` a la condición de siempre —
                                        // si estamos en esa vista, cae
                                        // directo al mismo `else` que ya
                                        // limpiaba `previousAngle` cuando el
                                        // modo está apagado, así que un
                                        // futuro reingreso al canvas
                                        // principal no arrastra un ángulo
                                        // "viejo" de esta vista.
                                        if (latestEdicionImagenEnabled.value && latestEditModeLayerId.value == null) {
                                            val a = pressed[0].position
                                            val b = pressed[1].position
                                            val angle = Math.toDegrees(
                                                atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
                                            ).toFloat()
                                            val prevAngle = previousAngle
                                            if (prevAngle != null) {
                                                var deltaAngle = angle - prevAngle
                                                if (deltaAngle > 180f) deltaAngle -= 360f
                                                if (deltaAngle < -180f) deltaAngle += 360f
                                                rotation = normalizeRotationDeg(rotation + deltaAngle)
                                            }
                                            previousAngle = angle
                                        } else {
                                            // "Edición > Imagen" apagada:
                                            // se limpia la base de ángulo
                                            // para que, si se activa a
                                            // mitad de gesto, no salte con
                                            // un delta viejo — el zoom de
                                            // arriba sigue funcionando
                                            // igual, esto solo afecta a la
                                            // rotación.
                                            previousAngle = null
                                        }
                                    } else {
                                        // Un solo dedo: no se toca escala
                                        // ni rotación, solo se deja avanzar
                                        // el paneo de más arriba. Se limpia
                                        // la base de span/ángulo para que,
                                        // si se agrega un segundo dedo, no
                                        // salte con un delta viejo.
                                        previousSpan = null
                                        previousAngle = null
                                    }

                                    pendingCommit = true
                                    val now = System.currentTimeMillis()
                                    if (now - lastCommitAtMs >= 120L) {
                                        commitLiveFrame()
                                        lastCommitAtMs = now
                                        pendingCommit = false
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (fingerCountChanged) {
                                    // Cambió la cantidad de dedos: solo
                                    // resetear la base de pellizco (span/
                                    // ángulo), nunca aplicar delta acá.
                                    previousSpan = null
                                    previousAngle = null
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                                previousCentroid = centroid
                                previousPressedCount = pressed.size
                            }
                            // Se soltó el dedo: garantizar que la posición
                            // final quede guardada aunque haya caído
                            // dentro de la "ventana" sin persistir.
                            if (pendingCommit) commitLiveFrame()
                            // Se acabó el gesto de arrastre: apagar las
                            // guías de alineación — son un overlay
                            // transitorio, no deben quedar pegadas en
                            // pantalla después de soltar el dedo.
                            activeSnapGuideXPx = null
                            activeSnapGuideYPx = null

                            // --- ARREGLADO: tocar un espacio vacío del
                            // canvas (sin ninguna capa debajo del dedo) no
                            // hacía NADA — el marco de selección se quedaba
                            // ahí pegado hasta elegir otra capa desde el
                            // timeline. Pedido explícito: un toque simple
                            // (no un pellizco/paneo, que sigue funcionando
                            // igual que antes) sobre un hueco vacío tiene
                            // que quitar el marco. Umbral de 8dp de
                            // movimiento total para diferenciar "toque" de
                            // "arrastre que arrancó en un hueco vacío".
                            //
                            // Excepción agregada a pedido: mientras el modo
                            // "Editando imagen" está activo
                            // (`editIdForHitTest != null`, ver arriba), un
                            // toque en un hueco vacío CERCA de la capa
                            // aislada no debe deseleccionarla ni sacar al
                            // usuario del modo — solo la "X"/check de la
                            // manija Editar hace eso (ver `exitEditMode()`
                            // en `hitsRole(LayerHandleRole.EDIT)`).
                            val tapSlopPx = 8.dp.toPx()
                            if (hitLayerId == null && totalMovementPx < tapSlopPx && editIdForHitTest == null) {
                                selectedAudioTrackId = null
                                viewModel.clearSelection()
                            }

                            // --- Doble-tap para reemplazar imagen: un toque
                            // genuino (no arrastre — mismo umbral tapSlopPx
                            // de arriba) sobre una capa, dos veces seguidas
                            // en menos de 300ms, abre el selector de
                            // imágenes para esa capa. Funciona con
                            // "Edición > Imagen" prendida o apagada — se
                            // pidió explícitamente que no dependa de ese
                            // modo. Reusa el mismo selector nativo
                            // (ActivityResultContracts.OpenDocument, ver
                            // MainActivity.onReplaceImageClick) que ya usa
                            // el botón "Reemplazar imagen" del panel de
                            // propiedades — en Android 13+ eso ya abre el
                            // Selector de Fotos moderno de Google (mismo
                            // look que la galería nativa), así que no hace
                            // falta construir una ventana propia para esto.
                            if (hitLayerId != null && totalMovementPx < tapSlopPx) {
                                val tappedLayer = latestLayersForHitTest.value.firstOrNull { it.id == hitLayerId }
                                if (tappedLayer != null && !tappedLayer.locked) {
                                    val nowMs = System.currentTimeMillis()
                                    val isDoubleTap = hitLayerId == lastImageTapLayerId &&
                                        (nowMs - lastImageTapAtMs) < 300L
                                    if (isDoubleTap) {
                                        lastImageTapAtMs = 0L
                                        lastImageTapLayerId = null
                                        latestOnReplaceImageClick.value(hitLayerId)
                                    } else {
                                        lastImageTapAtMs = nowMs
                                        lastImageTapLayerId = hitLayerId
                                    }
                                }
                            }
                        }
                    }
            ) {
                // --- Cuadrícula de composición rasterizada a bitmap (ver
                // comentario completo en [rasterizeGridBitmap]): se
                // recalcula SOLO cuando algo de la cuadrícula (forma,
                // columnas/filas, color, grosor) o el tamaño del lienzo
                // cambian de verdad — las keys de `remember` NO incluyen
                // nada que cambie en cada frame de arrastre de una capa
                // (translateX/Y/scale no son keys acá), así que mover o
                // escalar una capa normal no re-rasteriza nada.
                val gridDensity = androidx.compose.ui.platform.LocalDensity.current
                val gridBitmap = remember(
                    gridEnabled,
                    gridShape,
                    gridSpec,
                    gridLineColorEnabled,
                    gridLineHue,
                    gridLineThicknessDp,
                    gridLineOpacity,
                    hitTestBoxSize.value
                ) {
                    if (!gridEnabled) {
                        null
                    } else {
                        rasterizeGridBitmap(
                            widthPx = hitTestBoxSize.value.width,
                            heightPx = hitTestBoxSize.value.height,
                            shape = gridShape,
                            spec = gridSpec,
                            color = gridLineDrawColor(gridLineColorEnabled, gridLineHue, gridLineOpacity),
                            strokeWidthPx = with(gridDensity) { gridLineThicknessDp.dp.toPx() }
                        )
                    }
                }
                GLPreview(
                    // HOTFIX (bug real reportado: "borro una capa, edito una
                    // imagen, y al volver las OTRAS capas quedan
                    // transparentes"): [getLayers] SIEMPRE tiene que devolver
                    // el set REAL y COMPLETO de capas vivas del proyecto —
                    // NUNCA filtrado por el modo edición aislado. GLRenderer
                    // usa este mismo lambda para decidir qué texturas GPU
                    // "siguen perteneciendo a una capa viva" y podar
                    // (`glDeleteTextures`) las que no (ver FASE 3.1 §4.5,
                    // corrección del memory leak de capas eliminadas). Antes,
                    // este lambda filtraba a SOLO la capa en edición mientras
                    // `editModeLayerId != null` — GLRenderer no tiene forma de
                    // distinguir "esta capa se filtró temporalmente de la
                    // vista aislada" de "esta capa se eliminó del proyecto":
                    // para ese código, ambos casos se ven IDÉNTICOS. Resultado
                    // real: cada vez que se entraba al modo edición aislado,
                    // GLRenderer borraba la textura GPU de TODAS las demás
                    // capas, creyendo que se habían eliminado — y al volver a
                    // la pantalla principal, esas capas quedaban sin textura
                    // (invisibles/"transparentes") hasta que algo más
                    // disparara un re-decode.
                    //
                    // El aislamiento visual real ("que se centre... y que
                    // todas desaparezca que solo quede esa imagen") sigue
                    // intacto: se logra únicamente filtrando
                    // [getRenderSnapshot] (más abajo), que es la lista de la
                    // que GLRenderer.onDrawFrame decide qué DIBUJAR en este
                    // frame — un concepto completamente distinto de "qué
                    // capas existen" (que es lo que debe responder
                    // [getLayers]).
                    getLayers = { viewModel.uiState.value.layers },
                    // FASE 3.1.3-R3 — misma instancia de LayerGpuCommitGate
                    // que EditorViewModel adquiere alrededor de sus
                    // mutaciones relevantes de identidad de Layer (ver el
                    // comentario "FASE 3.1.3-R3" en EditorViewModel.kt y
                    // el KDoc de LayerGpuCommitGate). Referencia estable:
                    // el ViewModel vive más que cualquier recomposición de
                    // esta pantalla, así que no hace falta envolverla en
                    // `remember`/lambda para "seguir" cambios — el objeto
                    // en sí nunca cambia.
                    commitGate = viewModel.layerGpuCommitGate,
                    // FASE 2 / HOTFIX — a diferencia de [getLayers] (arriba,
                    // ahora SIN filtrar a propósito — ver el comentario largo
                    // ahí), ACÁ SÍ corresponde filtrar por el modo edición
                    // aislado: esta lista es la que GLRenderer.onDrawFrame usa
                    // para decidir qué DIBUJAR este frame (el aislamiento
                    // visual real), no qué recursos GPU siguen vivos.
                    getRenderSnapshot = {
                        val full = viewModel.currentRenderSnapshot()
                        val editId = latestEditModeLayerId.value
                        if (editId != null) {
                            full.copy(layers = full.layers.filter { it.id == editId })
                        } else {
                            full
                        }
                    },
                    getPlayheadMs = { viewModel.uiState.value.playheadMs },
                    // La cuadrícula viaja como una textura de fondo más —
                    // GLRenderer la dibuja PRIMERO, antes que las capas
                    // reales, así queda detrás de cualquier imagen (ver
                    // comentario en rasterizeGridBitmap y en
                    // GLRenderer.onDrawFrame).
                    getGridBitmap = { gridBitmap },
                    // Fondo de trabajo solo en modo edición; el resto del tiempo, el verde del lienzo de siempre.
                    getBackdrop = { viewportBackdrop },
                    getLiveOverride = {
                        // ARREGLADO: acá antes se capturaba `selectedLayer`
                        // directo (un `val` normal, congelado en los valores
                        // que tenía la ÚLTIMA composición). GLSurfaceView
                        // corre en su propio hilo de render, en su propio
                        // reloj, completamente desacoplado del hilo de UI/
                        // Compose — puede llamar a este lambda en cualquier
                        // instante, incluso a mitad de un cambio de capa.
                        // Seleccionar una capa nueva actualiza
                        // translateX/Y/scale/... de inmediato (son
                        // MutableState, el cambio es visible al toque), pero
                        // `selectedLayer` (el id de la capa "vieja") solo se
                        // refresca cuando Compose recompone — eso pasa
                        // DESPUÉS, en el próximo frame. Resultado: por uno o
                        // más frames, el hilo de render veía el id de la
                        // capa VIEJA combinado con la transformación de la
                        // capa NUEVA, y dibujaba la capa vieja saltando a la
                        // posición/escala de la nueva — el "flash" de la
                        // otra capa reportado. `latestSelectedLayerForDrag`
                        // (definido más arriba, ya usado para el hit-test de
                        // drag por esta misma razón) es un State cuyo
                        // `.value` se lee en el momento exacto de la
                        // llamada, no al crear el lambda — así el id y la
                        // transformación quedan siempre sincronizados sin
                        // importar en qué instante exacto dispare el hilo
                        // de GL.
                        latestSelectedLayerForDrag.value?.let {
                            it.id to CameraFrame(translateX, translateY, scale, rotation, alpha, tiltX, tiltY, focusBlur, dollyZoom, scaleX, scaleY)
                        }
                    },
                    onRendererReady = { renderApi = RenderApiImpl(it) },
                    onAllVisibleLayersPainted = { canvasFullyPainted = true }
                )
                if (state.isImporting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                // AUDITORÍA — "flash verde de milisegundos al reabrir un
                // proyecto": ver el comentario grande junto a
                // `canvasFullyPainted` más arriba. El overlay verde de carga
                // ahora se sostiene hasta que el renderer confirma el primer
                // pintado real (`canvasFullyPainted`), no solo hasta que el
                // ViewModel termina de decodificar en CPU
                // (`state.isLoadingProject`) — cualquiera de los dos que
                // siga pendiente mantiene el overlay puesto, tapando el
                // `glClearColor` verde de "lienzo vacío" mientras la GPU
                // termina de subir las texturas.
                if (state.isLoadingProject || !canvasFullyPainted) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(ChromaKeyGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // --- Marco de la capa seleccionada: el rectángulo COMPLETO
                // que ocupa (mismo criterio que hitTestLayerAt), sin
                // importar cuánto de eso se vea transparente. Pedido
                // explícito: que quede claro hasta dónde "abarca" la
                // imagen para tocar/arrastrar, no solo lo pintado. Usa los
                // valores EN VIVO (translateX, scale, etc., no el frame
                // guardado del modelo) para que el marco siga el dedo
                // durante el arrastre sin quedar un frame atrás.
                //
                // Excepción a pedido explícito: mientras la pestaña "3D"
                // del modo edición está activa (extrude3DBridge.active,
                // ver [Extrude3DGestureBridge] y Extrude3DPanel), este
                // marco y sus 6 manijas NO se dibujan — el canvas queda
                // limpio, solo la imagen, y el gesto de arrastre/pellizco
                // pasa a orbitar/pellizcar el cuerpo 3D en vez de mover/
                // escalar la capa (ver el branch `if (extrude3DBridge.
                // active)` al principio del pointerInput, más abajo). ---
                if (selectedLayer != null && !selectedLayer.locked && !extrude3DBridge.active) {
                    val boxSize = hitTestBoxSize.value
                    val corners = layerBoundingQuadPx(
                        translateX = translateX,
                        translateY = translateY,
                        scaleVal = scale,
                        rotationDeg = rotation,
                        parallaxFactor = selectedLayer.parallaxFactor,
                        layerWidthPx = selectedLayer.widthPx,
                        layerHeightPx = selectedLayer.heightPx,
                        boxWidthPx = boxSize.width.toFloat(),
                        boxHeightPx = boxSize.height.toFloat(),
                        scaleXVal = scaleX,
                        scaleYVal = scaleY,
                        tiltXDeg = tiltX,
                        tiltYDeg = tiltY,
                        dollyZoom = dollyZoom
                    )
                    if (corners != null && corners.size == 4) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val frameColor = BrandPurpleLight
                            val strokeW = 2.dp.toPx()
                            // BUG REAL corregido — reportado con captura por
                            // el usuario ("los iconos están como que
                            // sueltos"): esta línea de contorno se dibujaba
                            // con las 4 esquinas CRUDAS de [corners], sin
                            // pasar por [clampedHandleSlots] — la misma
                            // función que SÍ usan las manijas (ver más abajo)
                            // para no desaparecer cerca del borde del
                            // Canvas. Con una capa muy escalada/acercada
                            // (esquinas reales bien afuera del viewport),
                            // el resultado eran dos cosas completamente
                            // desconectadas entre sí: una línea recta yendo
                            // directo hacia la esquina real (bien lejos,
                            // fuera de pantalla, cruzando el lienzo entero
                            // en diagonal) y, aparte, un círculo de manija
                            // "clavado" cerca del borde visible — exactamente
                            // el síntoma de la captura. Corrección: la línea
                            // de contorno ahora se traza sobre las MISMAS 4
                            // esquinas ya recortadas ([frameCornerSlots]) que
                            // usan las manijas más abajo — línea y manija
                            // quedan garantizadas coincidentes siempre,
                            // nunca una cosa por un lado y otra por otro.
                            val frameCornerSlots = clampedHandleSlots(
                                corners,
                                size,
                                HANDLE_BADGE_CLAMP_MARGIN_DP.toPx()
                            )
                            val frameCorners = listOf(
                                frameCornerSlots.getValue(HandlePosition.TOP_LEFT),
                                frameCornerSlots.getValue(HandlePosition.TOP_RIGHT),
                                frameCornerSlots.getValue(HandlePosition.BOTTOM_RIGHT),
                                frameCornerSlots.getValue(HandlePosition.BOTTOM_LEFT)
                            )
                            for (i in frameCorners.indices) {
                                val start = frameCorners[i]
                                val end = frameCorners[(i + 1) % frameCorners.size]
                                drawLine(frameColor, start, end, strokeW)
                            }
                            // --- Guías inteligentes de alineación (ver
                            // AlignmentGuides.kt): overlay TRANSITORIO —
                            // solo se dibuja algo mientras
                            // activeSnapGuideXPx/YPx no son null, es
                            // decir, mientras el gesto de arrastre/resize
                            // en curso está enganchado al centro/borde
                            // del lienzo (se limpian a null al soltar el
                            // dedo, ver los `pointerInput` de más arriba).
                            drawAlignmentGuideLines(
                                guideLineXPx = activeSnapGuideXPx,
                                guideLineYPx = activeSnapGuideYPx,
                                strokeWidthPx = 1.5.dp.toPx()
                            )
                            // --- 7 manijas intercambiables + 1 manija fija
                            // de reordenar (lateral izquierda, medio) —
                            // SOLO con "Edición > Imagen" activada (ver
                            // menú EdicionMenu, arriba del ícono Grabar).
                            // Con la opción apagada, la capa sigue
                            // mostrando su marco (para saber hasta dónde
                            // abarca y poder arrastrarla), pero sin
                            // manijas — la imagen queda "estática" salvo
                            // por la posición, tal como se pidió. Mismo
                            // criterio de esquinas que layerBoundingQuadPx:
                            // [0]=arriba-izq [1]=arriba-der [2]=abajo-der
                            // [3]=abajo-izq. Qué función dibuja cada una de
                            // las 7 manijas intercambiables depende del
                            // orden vigente (ver [HandlePosition]/
                            // [LayerHandleRole]/[effectiveHandleOrder]) —
                            // el mismo orden que usa el hit-test de gestos
                            // más arriba, así que el ícono que se ve
                            // siempre corresponde a la función real.
                            //
                            // A PEDIDO EXPLÍCITO DEL USUARIO — BUG VISUAL
                            // corregido (reportado con captura, comparado
                            // contra una vista limpia de referencia): estas
                            // 8 manijas son indispensables en el canvas
                            // PRINCIPAL y ahí siguen exactamente igual, sin
                            // tocar nada — el pedido fue específico sobre
                            // la ventana de modo edición AISLADO de una capa
                            // (`editModeLayerId` — la que muestra las
                            // pestañas Color/3D/Efecto a pantalla completa):
                            // ahí esas mismas 8 manijas se ven "sucias"
                            // superpuestas al marco, porque sus funciones ya
                            // se reordenan de otra forma en esa vista (los
                            // paneles flotantes de Color/3D/Efecto). Con
                            // `editModeLayerId == null` sumado a la
                            // condición de siempre, el marco (las 4 líneas
                            // de arriba, sin condición) se sigue dibujando
                            // en las DOS vistas — es lo único que debe verse
                            // dentro del modo aislado, un rectángulo azul
                            // limpio — pero las manijas en sí solo se
                            // dibujan fuera de él, en el canvas principal.
                            if (edicionImagenChecked && editModeLayerId == null) {
                                val badgeRadius = 11.dp.toPx()
                                val badgeRing = 1.6.dp.toPx()
                                val glyphStroke = 1.6.dp.toPx()

                                fun DrawScope.drawBadge(center: Offset, ringColor: Color, glyph: DrawScope.(Offset, Float, Color, Float) -> Unit) {
                                    drawCircle(Color.White, badgeRadius, center)
                                    drawCircle(ringColor, badgeRadius, center, style = Stroke(width = badgeRing))
                                    glyph(center, badgeRadius, ringColor, glyphStroke)
                                }

                                // Mientras se está reordenando, se dibuja el borrador en vivo
                                // (`reorderDraftOrder`); si no, el orden ya guardado (por-capa
                                // si existe, si no el global).
                                val order = if (reorderMode) (reorderDraftOrder ?: effectiveHandleOrder(selectedLayer.id)) else effectiveHandleOrder(selectedLayer.id)
                                // Esta capa es la que está en modo edición aislado ahora mismo —
                                // determina si la manija EDIT se dibuja como lápiz o como check
                                // (ver [drawGlyphForRole]).
                                val isEditingSelectedLayer = editModeLayerId == selectedLayer.id
                                // Acento de la manija de reordenar (rol REORDER): morado activo
                                // mientras el modo está prendido, el mismo frameColor que el
                                // resto si está apagado — mismo criterio "activo vs. inactivo"
                                // que el resto de la app. Se calcula antes del bucle porque el
                                // rol REORDER ahora se dibuja DENTRO de él (ver más abajo).
                                val reorderAccent = if (reorderMode) BrandPurpleLight else frameColor
                                // --- Único mapa con las 8 posiciones — ANTES `leftMid` (rol
                                // REORDER) vivía afuera de este mapa, en un badge aparte que
                                // nunca participaba del intercambio. ARREGLADO A PEDIDO ("todos
                                // rotan y cambian al arrastrar pero este no se mueve, quiero que
                                // también se reordene"): ahora es una posición más del mismo
                                // mapa, así que se dibuja con el MISMO bucle de abajo — y, al ser
                                // parte del mismo `slotOffsets` que usa el gesto de arrastre (ver
                                // más arriba, en el pointerInput), se puede arrastrar cualquier
                                // otra manija encima suyo (o ella a cualquier otra posición)
                                // exactamente igual que las otras 7.
                                //
                                // ARREGLADO A PEDIDO (captura real: manijas que "desaparecen"
                                // cerca del borde de la pantalla — el `Canvas` recorta cualquier
                                // cosa dibujada fuera de su propio tamaño) — ver el comentario
                                // grande junto a [clampedHandleSlots]. MISMA constante que los
                                // otros 3 sitios — [HANDLE_BADGE_CLAMP_MARGIN_DP] — así el punto
                                // donde se dibuja el badge y el punto donde se detecta el toque
                                // (ver el `pointerInput`, más arriba) son SIEMPRE el mismo. Reusa
                                // [frameCornerSlots] (ya calculado arriba para la línea de
                                // contorno) en vez de volver a llamar a [clampedHandleSlots] con
                                // los mismos argumentos — una sola fuente también a nivel de
                                // instancia, no solo de fórmula.
                                val slotOffsets = frameCornerSlots
                                for ((pos, center) in slotOffsets) {
                                    val role = order[pos] ?: continue
                                    if (reorderMode && pos == reorderDragFromPosition) {
                                        // Manija que se está arrastrando ahora mismo: se deja
                                        // tenue en su lugar de origen — la copia "de verdad" que
                                        // sigue al dedo se dibuja aparte, más abajo.
                                        drawCircle(Color.White.copy(alpha = 0.35f), badgeRadius, center)
                                        drawCircle(frameColor.copy(alpha = 0.35f), badgeRadius, center, style = Stroke(width = badgeRing))
                                    } else if (role == LayerHandleRole.REORDER) {
                                        // Rol especial: no pasa por [drawGlyphForRole] — ícono
                                        // check/swap propio, según el modo, con su propio acento
                                        // (mismo criterio que tenía el badge fijo de antes, ahora
                                        // aplicado a la posición que le toque en cada momento).
                                        drawBadge(center, reorderAccent) { c, r, col, sw ->
                                            if (reorderMode) drawCheckGlyph(c, r, col, sw) else drawSwapGlyph(c, r, col, sw)
                                        }
                                    } else {
                                        // isCancelIntent: mientras el modo reordenar está
                                        // activo, TODAS las manijas visibles (incluida la
                                        // DELETE) están en modo reordenar, así que la manija
                                        // DELETE funciona como "cancelar" (ver × dentro de
                                        // este bucle, hitsRole más arriba) — se dibuja el
                                        // círculo con × en vez de la papelera. Fuera de
                                        // reordenar, sigue el criterio de siempre: cancela
                                        // solo si ESTA capa es la que está en modo edición
                                        // aislado ahora mismo.
                                        val isCancelIntent = reorderMode || isEditingSelectedLayer
                                        drawBadge(center, frameColor) { c, r, col, sw -> drawGlyphForRole(role, c, r, col, sw, isEditingSelectedLayer, isCancelIntent) }
                                    }
                                }

                                // Manija fantasma: mientras se arrastra una manija para
                                // reordenar, sigue al dedo con su mismo ícono, para que se
                                // sienta "agarrada" (incluida la de reordenar/confirmar, ahora
                                // que también se puede arrastrar).
                                if (reorderMode) {
                                    val draggedFrom = reorderDragFromPosition
                                    val draggedRole = if (draggedFrom != null) order[draggedFrom] else null
                                    if (draggedRole == LayerHandleRole.REORDER) {
                                        // Mientras se arrastra, el modo reordenar siempre está
                                        // prendido acá adentro (este bloque entero vive dentro de
                                        // `if (reorderMode)`), así que el ícono en vivo siempre es
                                        // el check.
                                        drawBadge(reorderDragOffset, reorderAccent) { c, r, col, sw -> drawCheckGlyph(c, r, col, sw) }
                                    } else if (draggedRole != null) {
                                        // Mismo criterio que arriba: dentro de este bloque
                                        // `reorderMode` siempre es true, así que si se está
                                        // arrastrando la manija DELETE, la "manija fantasma"
                                        // también debe mostrar el círculo con × (cancelar),
                                        // nunca la papelera.
                                        drawBadge(reorderDragOffset, frameColor) { c, r, col, sw -> drawGlyphForRole(draggedRole, c, r, col, sw, isEditingSelectedLayer, isCancelIntent = true) }
                                    }
                                }
                            }
                        }

                        // --- Panel del mini-menú (manija de función
                        // "menú"): mismo look premium (Surface elevada +
                        // borde sutil) que EdicionMenu/GridMenu, para que
                        // se sienta de la misma familia visual que el
                        // resto de la app. Anclada en px absolutos a
                        // dondequiera que esté la manija con función
                        // "menú" AHORA MISMO (puede no ser ya la esquina
                        // sup. izquierda, si se reordenó — ver
                        // [effectiveHandleOrder]), no a un composable —
                        // porque la manija que la abre vive dentro del
                        // Canvas de arriba, no en el árbol de Compose.
                        // ARREGLADO (4): el ajuste anterior (radio+anillo del
                        // ícono + 10dp) todavía dejaba un hueco visible y
                        // grande en pantalla (confirmado por captura real
                        // del usuario) — el panel se veía "flotando",
                        // separado del ícono, en vez de colgar pegado a su
                        // pie. Se baja el margen extra de 10dp a 3dp (un
                        // hairline, apenas perceptible, para no tapar el
                        // propio ícono) y el eje X pasa de -4dp a
                        // -menuBadgeRadius, para que el ícono quede
                        // asentado justo sobre la esquina sup. izquierda
                        // del panel — "al pie del ícono", como se pidió —
                        // en vez de varios dp a su derecha.
                        // MISMA constante que los otros 3 sitios —
                        // [HANDLE_BADGE_CLAMP_MARGIN_DP] — así el panel
                        // cuelga siempre del pie de la manija tal como se
                        // la ve, nunca de una posición geométrica cruda
                        // que podría estar fuera de pantalla.
                        val anchorDensity = LocalDensity.current
                        val anchorSlots = clampedHandleSlots(
                            corners,
                            Size(boxSize.width.toFloat(), boxSize.height.toFloat()),
                            with(anchorDensity) { HANDLE_BADGE_CLAMP_MARGIN_DP.toPx() }
                        )
                        val topMidAnchor = anchorSlots.getValue(HandlePosition.TOP_MID)
                        val leftMidAnchor = anchorSlots.getValue(HandlePosition.LEFT_MID)
                        val anchorOrder = if (reorderMode) (reorderDraftOrder ?: effectiveHandleOrder(selectedLayer.id)) else effectiveHandleOrder(selectedLayer.id)
                        // Ancla de la manija "Restablecer" (por defecto la
                        // superior, medio — pero puede haberse reordenado),
                        // para que la mini-ventana de Restablecer salga
                        // colgando de su pie, como se pidió, en vez de
                        // aparecer centrada en toda la pantalla.
                        val restoreHandleAnchor = anchorOrder.entries
                            .firstOrNull { it.value == LayerHandleRole.RESTORE }
                            ?.key?.let { anchorSlots[it] } ?: topMidAnchor
                        // Ancla de la manija de reordenar/confirmar (rol REORDER) — igual
                        // criterio que `restoreHandleAnchor`: ya no es siempre `leftMidAnchor`,
                        // porque ahora esa manija también se puede arrastrar a otra posición.
                        // Usada por la mini-ventana "Solo aquí"/"Todos" más abajo.
                        val reorderHandleAnchor = anchorOrder.entries
                            .firstOrNull { it.value == LayerHandleRole.REORDER }
                            ?.key?.let { anchorSlots[it] } ?: leftMidAnchor

                        // --- Scrim invisible para "tocar afuera y
                        // cerrar" los 2 paneles restantes (Restablecer /
                        // Solo aquí-Todos): el freno de más arriba, en el
                        // pointerInput del canvas (`if (showReorderScopeMenu
                        // || showRestoreOptionsMenu) return@awaitEachGesture`),
                        // hace que CUALQUIER toque sobre el canvas se
                        // ignore por completo mientras uno de estos dos
                        // esté abierto — a propósito, para no confundir un
                        // toque cercano con el de una manija real (ver el
                        // comentario de ese freno). El problema real
                        // reportado: eso dejaba SIN NINGUNA forma de
                        // cerrar el panel salvo eligiendo una de sus
                        // propias opciones — tocar "afuera" (que es
                        // exactamente lo que ese freno ignora) no hacía
                        // nada. Este Box, del tamaño de todo el canvas, se
                        // dibuja DESPUÉS del marco/manijas pero ANTES de
                        // los paneles de abajo — en Compose, lo que se
                        // dibuja último es lo que recibe el toque primero,
                        // así que los paneles (dibujados encima de este
                        // scrim) siguen recibiendo sus propios toques con
                        // total normalidad, y cualquier toque que caiga
                        // FUERA de ellos cae en este scrim y los cierra —
                        // mismo comportamiento estándar que "tocar afuera
                        // para cerrar" en cualquier Popup/menú. (La manija
                        // "esquina sup. izquierda" ya no abre ningún panel
                        // — ver [enterEditModeForSelectedLayer] — así que
                        // ya no participa acá.)
                        if (showReorderScopeMenu || showRestoreOptionsMenu) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(showReorderScopeMenu, showRestoreOptionsMenu) {
                                        detectTapGestures {
                                            showReorderScopeMenu = false
                                            showRestoreOptionsMenu = false
                                        }
                                    }
                            )
                        }

                        // --- Mini-ventana "Restablecer" (manija superior,
                        // medio): colgando del pie de su propio ícono en el
                        // marco de edición, con el mismo criterio de anclaje
                        // que el menú de "Editar" (arriba) y "Solo aquí" /
                        // "Todos" (abajo) — nunca centrada en toda la
                        // pantalla. Deja elegir exactamente qué restablecer
                        // en vez de resetear todo de una, con 6 opciones
                        // puntuales:
                        //   • Todo      → rotación + ancho + alto (deja posición y zoom intactos)
                        //   • Rotación  → solo el ángulo
                        //   • Eje       → ancho Y alto juntos, sin tocar la rotación
                        //   • Zoom      → solo el tamaño general (`scale`)
                        //   • Ancho     → solo el estirado horizontal
                        //   • Alto      → solo el estirado vertical
                        // Cada opción cierra la ventana y aplica el cambio de inmediato.
                        if (edicionImagenChecked && showRestoreOptionsMenu) {
                            val anchor = restoreHandleAnchor
                            val menuBadgeRadius = 11.dp
                            val menuBadgeRing = 1.6.dp
                            // CORREGIDO DE RAÍZ (v4): los dos intentos anteriores
                            // calculaban a mano, en píxeles, dónde debía aparecer
                            // la mini-ventana de descripción de cada opción
                            // (con `onGloballyPositioned` + `Popup`) — y las dos
                            // veces terminó mal ubicada, corrida hacia la fila de
                            // arriba, de forma consistente. En vez de seguir
                            // peleando con ese cálculo manual (que depende de
                            // mecanismos internos de Compose difíciles de
                            // predecir en este entorno sin poder probar en un
                            // dispositivo real), la descripción ahora se abre
                            // DENTRO de la propia fila — empujando las filas de
                            // abajo hacia abajo, como un acordeón — en vez de
                            // ser un elemento flotante aparte. Así es
                            // estructuralmente imposible que quede mal ubicada:
                            // no hay coordenadas que calcular, porque la
                            // descripción directamente ES parte del layout de
                            // esa fila en el árbol de Compose.
                            var expandedRestoreInfo by remember { mutableStateOf<String?>(null) }
                            Box(
                                modifier = Modifier
                                    .offset {
                                        // Nace pegado al pie del icono (`anchor.y +
                                        // radio + anillo + 3dp`), con el borde
                                        // izquierdo casi alineado al icono
                                        // (`anchor.x - radio`) y creciendo hacia
                                        // la derecha desde ahí.
                                        IntOffset(
                                            (anchor.x - menuBadgeRadius.toPx()).roundToInt(),
                                            (anchor.y + (menuBadgeRadius + menuBadgeRing).toPx() + 3.dp.toPx()).roundToInt()
                                        )
                                    }
                            ) {
                                // El separador (`HorizontalDivider`) entre filas
                                // por diseño de Material3 SIEMPRE se estira hasta
                                // el ancho máximo disponible, sin importar qué tan
                                // angosto sea el texto de al lado. Con
                                // `Modifier.width(IntrinsicSize.Min)` en el
                                // `Column`, el ancho real del panel pasa a ser el
                                // de su fila más ancha, y RECIÉN dentro de ESE
                                // ancho — no de un máximo arbitrario — es que el
                                // separador y cada fila se estiran. Sin sobrante.
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = SurfaceTintedElevated,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .widthIn(max = 260.dp)
                                        .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .width(IntrinsicSize.Min)
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            "Restablecer",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        RestoreOptionRow(
                                            label = "Todo",
                                            icon = R.drawable.ic_restore_all,
                                            description = "Rotación, ancho y alto — la posición y el zoom no se tocan",
                                            isInfoExpanded = expandedRestoreInfo == "Todo",
                                            onToggleInfo = {
                                                expandedRestoreInfo = if (expandedRestoreInfo == "Todo") null else "Todo"
                                            },
                                            onDismiss = { showRestoreOptionsMenu = false }
                                        ) {
                                            rotation = 0f
                                            scaleX = 1f
                                            scaleY = 1f
                                            commitLiveFrame()
                                        }
                                        RestoreOptionRow(
                                            label = "Rotación",
                                            icon = R.drawable.ic_rotate,
                                            description = "Solo el ángulo de giro",
                                            isInfoExpanded = expandedRestoreInfo == "Rotación",
                                            onToggleInfo = {
                                                expandedRestoreInfo = if (expandedRestoreInfo == "Rotación") null else "Rotación"
                                            },
                                            onDismiss = { showRestoreOptionsMenu = false }
                                        ) {
                                            rotation = 0f
                                            commitLiveFrame()
                                        }
                                        RestoreOptionRow(
                                            label = "Eje",
                                            icon = R.drawable.ic_move_both_axes,
                                            description = "Ancho y alto juntos, sin tocar la rotación",
                                            isInfoExpanded = expandedRestoreInfo == "Eje",
                                            onToggleInfo = {
                                                expandedRestoreInfo = if (expandedRestoreInfo == "Eje") null else "Eje"
                                            },
                                            onDismiss = { showRestoreOptionsMenu = false }
                                        ) {
                                            scaleX = 1f
                                            scaleY = 1f
                                            commitLiveFrame()
                                        }
                                        RestoreOptionRow(
                                            label = "Zoom",
                                            icon = R.drawable.ic_zoom,
                                            description = "Solo el tamaño general de la capa",
                                            isInfoExpanded = expandedRestoreInfo == "Zoom",
                                            onToggleInfo = {
                                                expandedRestoreInfo = if (expandedRestoreInfo == "Zoom") null else "Zoom"
                                            },
                                            onDismiss = { showRestoreOptionsMenu = false }
                                        ) {
                                            scale = 1f
                                            commitLiveFrame()
                                        }
                                        RestoreOptionRow(
                                            label = "Ancho",
                                            icon = R.drawable.ic_resize_width,
                                            description = "Solo el estirado horizontal",
                                            isInfoExpanded = expandedRestoreInfo == "Ancho",
                                            onToggleInfo = {
                                                expandedRestoreInfo = if (expandedRestoreInfo == "Ancho") null else "Ancho"
                                            },
                                            onDismiss = { showRestoreOptionsMenu = false }
                                        ) {
                                            scaleX = 1f
                                            commitLiveFrame()
                                        }
                                        RestoreOptionRow(
                                            label = "Alto",
                                            icon = R.drawable.ic_resize_height,
                                            description = "Solo el estirado vertical",
                                            showDivider = false,
                                            isInfoExpanded = expandedRestoreInfo == "Alto",
                                            onToggleInfo = {
                                                expandedRestoreInfo = if (expandedRestoreInfo == "Alto") null else "Alto"
                                            },
                                            onDismiss = { showRestoreOptionsMenu = false }
                                        ) {
                                            scaleY = 1f
                                            commitLiveFrame()
                                        }
                                        // "Cerrar" (Text clickeable, esquina inferior
                                        // derecha) SACADO: ya no tiene sentido con el
                                        // scrim de "tocar afuera para cerrar" agregado
                                        // en el canvas — dejarlo era un botón
                                        // redundante que ya no cumplía ninguna función
                                        // que tocar afuera no cumpliera también.
                                    }
                                }
                            }
                        }

                        // --- Mini-ventana "Reordenar" (Solo / Todos —
                        // manija de reordenar, lateral izquierda, medio):
                        // ARREGLADO DE RAÍZ — antes cada opción era una
                        // "pill" individual (fondo redondeado propio,
                        // `RoundedCornerShape(20.dp)` + `.background(...)`)
                        // flotando suelta dentro del panel, sin título,
                        // así que se veía como dos botones sueltos en vez
                        // de una ventana de opciones — inconsistente con
                        // "Restablecer" (arriba), que es la referencia de
                        // estilo "premium" real de la app: filas PLANAS
                        // de ancho completo, separadas por un divisor
                        // fino, bajo un título en negrita. Ahora sigue
                        // exactamente ese mismo patrón — mismo tamaño de
                        // Surface/Column/título que Restablecer — para
                        // que ambas ventanas se sientan de la misma
                        // familia visual. "Solo" guarda el nuevo orden
                        // únicamente para esta capa; "Todos" lo guarda
                        // como el orden por defecto de cualquier capa que
                        // no tenga ya uno propio (ver
                        // [handleOrderPerLayer]/[handleOrderGlobal]).
                        // Elegir cualquiera de las dos entra directo al
                        // modo reordenar — no hace falta un paso aparte de
                        // "confirmar el alcance", el check de la manija
                        // más abajo ya cumple ese rol para el REORDEN en
                        // sí. Excepción: con "Restablecer" (3ra opción,
                        // más abajo) armada, "Solo"/"Todos" dejan de
                        // entrar a reordenar a mano y en cambio aplican
                        // el orden default de una — ver
                        // [restoringHandleOrder].
                        if (edicionImagenChecked && showReorderScopeMenu) {
                            // ARREGLADO: antes hardcodeado a `leftMidAnchor` — ahora que la
                            // manija de reordenar también se puede arrastrar a otra posición
                            // (ver arriba), esta mini-ventana tiene que colgar de DONDE ESTÉ
                            // ahora mismo, no siempre del lateral izquierdo.
                            val anchor = reorderHandleAnchor
                            val menuBadgeRadius = 11.dp
                            val menuBadgeRing = 1.6.dp
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (anchor.x - menuBadgeRadius.toPx()).roundToInt(),
                                            (anchor.y + (menuBadgeRadius + menuBadgeRing).toPx() + 3.dp.toPx()).roundToInt()
                                        )
                                    }
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = SurfaceTintedElevated,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .widthIn(max = 260.dp)
                                        .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .width(IntrinsicSize.Min)
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            "Reordenar",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        ReorderScopeOptionRow(
                                            label = "Solo",
                                            // Ícono SVG referenciado a lo que
                                            // hace: una sola imagen, para
                                            // "afecta nada más que la capa
                                            // actual".
                                            icon = R.drawable.ic_image_placeholder,
                                            showDivider = true
                                        ) {
                                            if (restoringHandleOrder) {
                                                // "Restablecer" + "Solo": fuerza
                                                // el orden default SOLO para esta
                                                // capa — un override explícito
                                                // (no solo borrar el override
                                                // existente), porque el global
                                                // puede no ser el default si ya
                                                // se reordenó con "Todos" antes.
                                                handleOrderPerLayer = handleOrderPerLayer + (selectedLayer.id to DEFAULT_HANDLE_ORDER)
                                                viewModel.updateHandleOrder("ONLY_HERE", selectedLayer.id, encodeHandleOrder(DEFAULT_HANDLE_ORDER))
                                                restoringHandleOrder = false
                                                showReorderScopeMenu = false
                                            } else {
                                                reorderDraftOrder = effectiveHandleOrder(selectedLayer.id)
                                                reorderScope = HandleReorderScope.ONLY_HERE
                                                reorderMode = true
                                                showReorderScopeMenu = false
                                            }
                                        }
                                        ReorderScopeOptionRow(
                                            label = "Todos",
                                            // Ícono SVG: varias capas
                                            // apiladas, para "afecta a
                                            // cualquier imagen del canvas,
                                            // no solo esta".
                                            icon = R.drawable.ic_layers,
                                            showDivider = true
                                        ) {
                                            if (restoringHandleOrder) {
                                                // "Restablecer" + "Todos": vuelve
                                                // el global al default Y borra
                                                // cualquier override por-capa —
                                                // un restablecimiento de verdad,
                                                // no solo "de acá en más", para
                                                // que ninguna capa se quede con
                                                // un orden custom viejo dando
                                                // vueltas.
                                                // Persiste el restablecimiento total ANTES de vaciar la
                                                // variable local: se necesitan las claves de
                                                // `handleOrderPerLayer` (capas con override "Solo") para
                                                // borrar también esos overrides del lado del ViewModel —
                                                // [EditorViewModel.restoreHandleOrder] con alcance "ALL"
                                                // solo resetea el global (ver su comentario), así que acá
                                                // se completa el reseteo real capa por capa.
                                                handleOrderPerLayer.keys.forEach { layerId ->
                                                    viewModel.restoreHandleOrder("ONLY_HERE", layerId)
                                                }
                                                viewModel.restoreHandleOrder("ALL", null)
                                                handleOrderGlobal = DEFAULT_HANDLE_ORDER
                                                handleOrderPerLayer = emptyMap()
                                                restoringHandleOrder = false
                                                showReorderScopeMenu = false
                                            } else {
                                                reorderDraftOrder = effectiveHandleOrder(selectedLayer.id)
                                                reorderScope = HandleReorderScope.ALL
                                                reorderMode = true
                                                showReorderScopeMenu = false
                                            }
                                        }
                                        // --- "Restablecer" (3ra opción, pedida
                                        // explícitamente): NO restablece nada
                                        // por sí sola al tocarla — solo "arma"
                                        // el modo restablecer (toggle) y deja un
                                        // mensaje de ayuda pegado debajo pidiendo
                                        // elegir "Solo" o "Todos" arriba, porque
                                        // restablecer el orden necesita el MISMO
                                        // alcance que reordenar a mano: ¿reset
                                        // nada más que esta capa, o el default
                                        // global de todas? Tocarla de nuevo
                                        // desarma el modo (toggle real, no un
                                        // botón de un solo sentido).
                                        ReorderScopeOptionRow(
                                            label = "Restablecer",
                                            icon = R.drawable.ic_restore_all,
                                            showDivider = false,
                                            isActive = restoringHandleOrder
                                        ) {
                                            restoringHandleOrder = !restoringHandleOrder
                                        }
                                        AnimatedVisibility(visible = restoringHandleOrder) {
                                            Text(
                                                "Selecciona Solo o Todos para restablecer el orden por defecto",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Overlay de "congelar zona" de la categoría "Distorsión"
                // (ver [DistortionGestureBridge] y DistortionPanel): mientras
                // el modo de pintar máscara está activo, se pinta un tinte
                // semitransparente sobre la zona protegida — mismo criterio
                // de transformación (encuadre + centro + rotación) que el
                // marco de selección de arriba, para que el tinte quede
                // pegado a la imagen sin importar pan/zoom/rotación. Se
                // dibuja como una grilla de celdas (misma resolución que
                // [DistortionFreezeMask.gridResolution]) en vez de un solo
                // color plano, así el borde difuminado del pincel
                // ("dureza del borde") se nota en el tinte, no solo en el
                // resultado final.
                if (distortionBridge.active && distortionBridge.freezeModeActive && selectedLayer != null) {
                    // Se lee acá (no dentro del Canvas) para que la
                    // recomposición dependa explícitamente del contador de
                    // versión — mutar la máscara in-place no dispara nada
                    // por sí solo, así que se captura en un `val` para que
                    // Compose registre la lectura de este State antes de
                    // entrar al bloque de dibujo.
                    val mask = distortionBridge.freezeMask
                    val maskVersionTick = distortionBridge.freezeMaskVersion
                    if (mask != null) {
                        val boxSize = hitTestBoxSize.value
                        val overlayCorners = layerBoundingQuadPx(
                            translateX = translateX,
                            translateY = translateY,
                            scaleVal = scale,
                            rotationDeg = rotation,
                            parallaxFactor = selectedLayer.parallaxFactor,
                            layerWidthPx = selectedLayer.widthPx,
                            layerHeightPx = selectedLayer.heightPx,
                            boxWidthPx = boxSize.width.toFloat(),
                            boxHeightPx = boxSize.height.toFloat(),
                            scaleXVal = scaleX,
                            scaleYVal = scaleY,
                            tiltXDeg = tiltX,
                            tiltYDeg = tiltY,
                            dollyZoom = dollyZoom
                        )
                        if (overlayCorners != null && overlayCorners.size == 4) {
                            key(maskVersionTick) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val topLeft = overlayCorners[0]
                                val topRight = overlayCorners[1]
                                val bottomRight = overlayCorners[2]
                                val bottomLeft = overlayCorners[3]
                                val res = mask.gridResolution()
                                val tint = Color(0xFFFF3B30) // rojo "protegido" — mismo lenguaje visual que máscaras de otras apps de edición.
                                for (j in 0 until res) {
                                    val v0 = j.toFloat() / res
                                    val v1 = (j + 1).toFloat() / res
                                    for (i in 0 until res) {
                                        val u0 = i.toFloat() / res
                                        val amount = mask.sample((u0 + 0.5f / res), (v0 + 0.5f / res))
                                        if (amount <= 0.02f) continue
                                        val u1 = (i + 1).toFloat() / res
                                        // Bilineal manual entre las 4 esquinas del cuadrilátero de
                                        // la capa (topLeft/topRight/bottomLeft/bottomRight) — mismo
                                        // principio que layerBoundingQuadPx usa para las manijas,
                                        // aplicado acá a cada celda de la máscara en vez de a las
                                        // 4 esquinas de la imagen entera.
                                        fun lerpQuad(u: Float, v: Float): Offset {
                                            val top = androidx.compose.ui.geometry.Offset(
                                                topLeft.x + (topRight.x - topLeft.x) * u,
                                                topLeft.y + (topRight.y - topLeft.y) * u
                                            )
                                            val bottom = androidx.compose.ui.geometry.Offset(
                                                bottomLeft.x + (bottomRight.x - bottomLeft.x) * u,
                                                bottomLeft.y + (bottomRight.y - bottomLeft.y) * u
                                            )
                                            return androidx.compose.ui.geometry.Offset(
                                                top.x + (bottom.x - top.x) * v,
                                                top.y + (bottom.y - top.y) * v
                                            )
                                        }
                                        val p00 = lerpQuad(u0, v0)
                                        val p10 = lerpQuad(u1, v0)
                                        val p11 = lerpQuad(u1, v1)
                                        val p01 = lerpQuad(u0, v1)
                                        val path = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(p00.x, p00.y)
                                            lineTo(p10.x, p10.y)
                                            lineTo(p11.x, p11.y)
                                            lineTo(p01.x, p01.y)
                                            close()
                                        }
                                        drawPath(path, color = tint, alpha = (amount * 0.45f).coerceIn(0f, 0.45f))
                                    }
                                }
                            }
                            }
                        }
                    }
                }


                // --- SACADO A PEDIDO: el chip "Editando imagen X" de la
                // cabecera de la imagen se eliminó. La salida del modo
                // edición aislado ya no depende de este chip — ahora la
                // misma manija "Editar" (esquina donde esté asignada,
                // según [effectiveHandleOrder]) hace de botón "confirmar/
                // guardar": mientras la capa está en modo edición, esa
                // manija dibuja un check en vez del lápiz (ver
                // `isEditingSelectedLayer` en el Canvas de manijas, más
                // arriba, y `drawGlyphForRole`) y tocarla llama a
                // `exitEditMode()` (ver `hitsRole(LayerHandleRole.EDIT)`,
                // más arriba en el gesto del canvas) — un solo ícono que
                // alterna "entrar"/"confirmar y salir", sin texto ni chip
                // aparte tapando la imagen.

                // --- Guías de composición: ahora se dibujan DETRÁS de las
                // capas (ver `gridBitmap`/`getGridBitmap` más arriba y
                // GLRenderer.onDrawFrame) en vez de como overlay de
                // Compose por encima de todo — por eso ya no hay un
                // Canvas acá. Sigue siendo solo del editor, NUNCA se
                // exporta al video (GLRenderer del exportador no recibe
                // getGridBitmap).

                // --- El botón de capas se movió a la esquina inferior
                // izquierda, junto a la barra de tiempo del timeline (ver
                // más abajo, donde está TimelineView) — antes vivía acá
                // arriba tapando parte del preview. ---

                // El hint "Arrastra · pellizca · gira con 2 dedos" se quitó
                // a pedido — quedaba redundante una vez que el usuario ya
                // conoce el gesto. Se conserva el aviso de "Capa bloqueada",
                // que sí es información que cambia y vale la pena mostrar.
                if (selectedLayer != null && selectedLayer.locked) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Capa bloqueada",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }


                // --- Cuentagotas: overlay que aparece ENCIMA de todo el
                // preview mientras se espera el toque. ANTES: un solo tap
                // ya elegía el color, sin poder ver antes qué se estaba
                // tocando ni corregir el dedo si apuntaba mal ("solo hay
                // una oportunidad para seleccionar el color dando un
                // click"). AHORA: al apoyar el dedo aparece una lupa
                // circular pegada arriba del punto tocado que muestra EN
                // VIVO el color de esa posición (se actualiza en cada
                // movimiento, sin soltar); recién al levantar el dedo se
                // confirma ese último color como elegido. Si el dedo se
                // levanta fuera del canvas (offset inválido) o se cancela
                // el gesto, no se confirma nada — solo se sale del modo
                // cuentagotas. ---
                if (eyedropperActiveForLayerId != null) {
                    val targetLayerId = eyedropperActiveForLayerId
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .pointerInput(targetLayerId) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    var lastPos = down.position
                                    eyedropperTouchPos = lastPos
                                    renderApi?.pixelColor?.requestPixelColor(
                                        lastPos.x.roundToInt(),
                                        lastPos.y.roundToInt()
                                    ) { argb -> eyedropperLiveArgb = argb }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (change.pressed) {
                                            change.consume()
                                            lastPos = change.position
                                            eyedropperTouchPos = lastPos
                                            renderApi?.pixelColor?.requestPixelColor(
                                                lastPos.x.roundToInt(),
                                                lastPos.y.roundToInt()
                                            ) { argb -> eyedropperLiveArgb = argb }
                                        } else {
                                            // Dedo levantado: confirma el ÚLTIMO color
                                            // visto en la lupa como el elegido.
                                            val layerId = targetLayerId
                                            if (layerId != null) {
                                                renderApi?.pixelColor?.requestPixelColor(
                                                    lastPos.x.roundToInt(),
                                                    lastPos.y.roundToInt()
                                                ) { argb ->
                                                    eyedropperPickedColor = layerId to argb
                                                    eyedropperActiveForLayerId = null
                                                    eyedropperTouchPos = null
                                                    eyedropperLiveArgb = null
                                                }
                                            } else {
                                                eyedropperActiveForLayerId = null
                                                eyedropperTouchPos = null
                                                eyedropperLiveArgb = null
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                    ) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    if (eyedropperTouchPos == null)
                                        "Tocá y arrastrá sobre la imagen para elegir el color"
                                    else
                                        "Soltá para elegir este color",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Cancelar",
                                    color = BrandPurpleLight,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.clickable {
                                        eyedropperActiveForLayerId = null
                                        eyedropperTouchPos = null
                                        eyedropperLiveArgb = null
                                    }
                                )
                            }
                        }

                        // --- Lupa flotante: sigue al dedo en tiempo real,
                        // desplazada hacia arriba para no quedar tapada por
                        // el dedo/mano. Muestra el color leído AHORA en un
                        // círculo grande + su código hex, con una línea guía
                        // que baja hasta el punto exacto que se está
                        // tocando (para no perder precisión aunque la lupa
                        // esté offset). ---
                        val touchPos = eyedropperTouchPos
                        if (touchPos != null) {
                            val liveColor = eyedropperLiveArgb?.let { Color(it) } ?: Color.Gray
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val loupeOffsetPx = with(density) { 96.dp.toPx() }
                            val loupeCenterY = (touchPos.y - loupeOffsetPx).coerceAtLeast(
                                with(density) { 90.dp.toPx() }
                            )
                            // Línea guía entre la lupa y el punto tocado.
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.8f),
                                    start = androidx.compose.ui.geometry.Offset(touchPos.x, loupeCenterY),
                                    end = touchPos,
                                    strokeWidth = with(density) { 1.5.dp.toPx() }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .offset {
                                        androidx.compose.ui.unit.IntOffset(
                                            (touchPos.x - with(density) { 44.dp.toPx() }).roundToInt(),
                                            (loupeCenterY - with(density) { 44.dp.toPx() }).roundToInt()
                                        )
                                    }
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(liveColor)
                                    .border(3.dp, Color.White, CircleShape)
                            )
                            Surface(
                                modifier = Modifier
                                    .offset {
                                        androidx.compose.ui.unit.IntOffset(
                                            (touchPos.x - with(density) { 50.dp.toPx() }).roundToInt(),
                                            (loupeCenterY + with(density) { 50.dp.toPx() }).roundToInt()
                                        )
                                    },
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "#" + String.format("%06X", (eyedropperLiveArgb ?: 0) and 0xFFFFFF),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // --- HUD de snap de rotación: badge chico arriba al
                // centro del lienzo, SOLO visible mientras la manija
                // ROTATE está enganchada a un múltiplo de 15° (ver
                // [snapRotationToFifteenDegrees] y el `pointerInput` de
                // la manija ROTATE, más arriba, que llena/limpia
                // `activeRotationSnapDeg`). A propósito FUERA del `if
                // (eyedropperActiveForLayerId != null)` de arriba — nada
                // que ver con el cuentagotas, tiene que verse siempre que
                // se esté rotando, cuentagotas activo o no.
                val rotationSnapDeg = activeRotationSnapDeg
                if (rotationSnapDeg != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${rotationSnapDeg.toInt()}°",
                            color = ALIGNMENT_GUIDE_COLOR,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            } // fin Box interior (Canvas real, ver ADR-005 Fase F)
            } // fin Box interior (fondo negro + deseleccionar, antes era el único Box exterior)
            // --- Aviso proactivo de papelera (ver LayerTrashScreen.kt): no
            // bloqueante, aparece una sola vez por episodio (regla completa
            // en EditorUiState.showTrashRecoveryBanner). Hermano superpuesto
            // de los dos Box de arriba, alineado arriba — flota sobre el
            // lienzo en vez de reservar su propia fila (ver el comentario
            // grande más arriba, donde se armó este Box exterior nuevo).
            if (state.showTrashRecoveryBanner) {
                TrashRecoveryBanner(
                    itemCount = state.trashedLayers.size,
                    onView = {
                        viewModel.dismissTrashBanner()
                        showLayerTrashDialog = true
                    },
                    onDismiss = { viewModel.dismissTrashBanner() },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
            // Selector del fondo de trabajo: solo mientras se edita una capa. Va al final del Box para quedar
            // por encima de la imagen y siempre accesible.
            if (editModeLayerId != null) {
                EditBackgroundButton(
                    settings = editBackgroundState.settings,
                    onCycle = editBackgroundState::cycle,
                    onSelect = editBackgroundState::select,
                    onPickCustom = { showEditBackgroundColorPicker = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }
            } // fin Box exterior (solo tamaño: weight/fillMaxHeight, ver ADR-005 Fase F)

            if (!canvasFillsScreen) {

            // --- Timeline visual: una pista por capa con keyframes arrastrables ---
            // ANTES: este Box (y el TimelineView de adentro) tenían un tope
            // FIJO de 220dp (heightIn max=220dp), mientras que el Box
            // "relleno" de más abajo (línea ~807, placeholder del panel
            // Cámara/Look/Audio/Tiempo comentado) usaba weight(1f) — se
            // quedaba con TODO el espacio sobrante de la pantalla sin
            // importar cuánto necesitara realmente el timeline. Resultado:
            // apenas había 3-4 capas, el timeline llegaba a su tope de
            // 220dp y necesitaba scroll interno para llegar al "+", pero
            // como el relleno de abajo es EXACTAMENTE el mismo morado
            // sólido (BrandPurpleDeep), todo ese scroll pendiente se veía
            // como una sola pared uniforme tapando las capas y el "+" —
            // no había ningún panel invisible tapando nada, era solo mal
            // reparto de espacio entre estos dos hermanos.
            //
            // Ahora el timeline usa weight(1f) — se lleva el espacio
            // sobrante real de la pantalla (compartido con el relleno de
            // abajo, ver su propio comentario), así que en la gran mayoría
            // de los casos entran ruler + master + varias capas + el "+"
            // sin necesitar scroll para nada; solo con MUCHAS capas entra a
            // tallar el scroll interno de TimelineView, y en ese caso sí
            // hay contenido real de sobra (no una pared vacía).
            //
            // --- Envoltorio nuevo para el panel "Información del
            // proyecto": antes el Box de acá abajo (con weight(1f)) y
            // EditorBottomBar eran hermanos sueltos dentro de la Column de
            // toda la pantalla — cada uno con su propio pedazo de alto,
            // pero SIN un padre en común que abarcara los dos juntos, así
            // que no había forma de poner un panel que tapara am
            // BOS a la vez de punta a punta (el pedido: "desde la barra de
            // playhead hasta el borde de abajo de la pantalla", ruler +
            // capas + la barra Keyframes/Control/Módulos, todo junto). Ahora
            // ese Box exterior es el padre común: adentro, una Column
            // nueva reproduce EXACTAMENTE el mismo reparto de alto que
            // había antes (timeline con weight(1f) + EditorBottomBar con
            // su alto fijo) — mismo resultado visual cuando el panel está
            // cerrado — y al lado, como hermano de esa Column dentro del
            // mismo Box, va el panel nuevo, que al ocupar fillMaxSize()
            // cubre justo ese alto combinado.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // El timeline ocupa TODO el alto (hasta el borde inferior) y la
            // barra Módulos/Control/Keyframes se SUPERPONE solo sobre el área
            // de pistas: la columna de capas (cabeceras) sigue visible y
            // continúa hasta abajo, en vez de cortarse con un hueco.
            Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                TimelineView(
                    layers = state.layers,
                    // Ver comentario en TimelineView.kt: sin esto, el
                    // reordenamiento por arrastre "regresaba" a su lugar
                    // anterior al soltar el dedo, porque `setLayerZIndex`
                    // muta el zIndex de los mismos objetos Layer sin
                    // cambiar la referencia de forma que Compose lo note.
                    revision = state.revision,
                    selectedLayerId = state.selectedLayerId,
                    playheadMs = state.playheadMs,
                    projectDurationMs = state.projectDurationMs,
                    onSeek = { viewModel.seekTo(it) },
                    onSelectLayer = { selectLayerAndSync(it) },
                    onRetimeKeyframe = { layerId, oldMs, newMs -> viewModel.retimeKeyframe(layerId, oldMs, newMs) },
                    onToggleLayerVisibility = { layerId ->
                        // FASE 4.2-R5 (R5.1 del prompt maestro R5): antes
                        // esto llamaba directo a
                        // `viewModel.toggleLayerVisibility(layerId)`. El
                        // gesto de UI ("tocar el ícono de ojo") sigue
                        // siendo un toggle por diseño — TimelineView no
                        // conoce el valor deseado, solo la intención de
                        // invertir — pero la MUTACIÓN real ahora pasa por
                        // `LayerApi.setVisible(id, valorAbsoluto)`, nunca
                        // por un segundo `toggle*` que duplicaría la
                        // decisión (ver regla de R5.1: "la UI debe conocer
                        // el estado deseado"). El valor absoluto deseado
                        // se resuelve acá mismo, a partir del MISMO
                        // `state.layers` que ya se le pasa a TimelineView
                        // dos líneas más arriba — una sola fuente de
                        // verdad, sin leer nada por otro lado.
                        val current = state.layers.find { it.id == layerId }
                        if (layerApi != null && current != null) {
                            eliNerScope.launch { layerApi.setVisible(layerId, !current.visible) }
                        } else {
                            viewModel.toggleLayerVisibility(layerId)
                        }
                    },
                    onToggleLayerLock = { layerId ->
                        val current = state.layers.find { it.id == layerId }
                        if (layerApi != null && current != null) {
                            eliNerScope.launch { layerApi.setLocked(layerId, !current.locked) }
                        } else {
                            viewModel.toggleLayerLock(layerId)
                        }
                    },
                    onToggleLayerOrderLock = { layerId ->
                        val current = state.layers.find { it.id == layerId }
                        if (layerApi != null && current != null) {
                            eliNerScope.launch { layerApi.setOrderLocked(layerId, !current.orderLocked) }
                        } else {
                            viewModel.toggleLayerOrderLock(layerId)
                        }
                    },
                    onRenameLayer = { layerId, newName -> viewModel.renameLayer(layerId, newName) },
                    onChangeLayerColor = { layerId, colorArgb, useBW -> viewModel.setLayerCustomColor(layerId, colorArgb, useBW) },
                    onChangeLayerGradient = { layerId, startArgb, endArgb, angleDegrees, isRadial, useBW -> viewModel.setLayerGradient(layerId, startArgb, endArgb, angleDegrees, isRadial, useBW) },
                    onResetLayerColor = { layerId -> viewModel.resetLayerColor(layerId) },
                    // "Multicolor": un solo checkpoint de undo para el grupo
                    // entero + degradado repartido entre las capas marcadas
                    // (ver EditorViewModel.setLayersGradient), en vez de
                    // pintar el mismo degradado completo en cada capa por
                    // separado.
                    onChangeMultipleLayersColor = { layerIds, colorArgb, useBW -> viewModel.setLayersCustomColor(layerIds, colorArgb, useBW) },
                    onChangeMultipleLayersGradient = { layerIds, startArgb, endArgb, angleDegrees, isRadial, useBW, colorSpace -> viewModel.setLayersGradient(layerIds, startArgb, endArgb, angleDegrees, isRadial, useBW, colorSpace) },
                    onResetMultipleLayersColor = { layerIds -> viewModel.resetLayersColor(layerIds) },
                    onRequestEyedropper = { layerId -> eyedropperActiveForLayerId = layerId },
                    eyedropperResult = eyedropperPickedColor,
                    onConsumeEyedropperResult = { eyedropperPickedColor = null },
                    // FASE 4.2-R5 (R5.1/R5.18 del prompt maestro R5) —
                    // DECISIÓN DE ALCANCE, no un olvido: se evaluó migrar
                    // esto a `layerApi.reorderLayer(id, newZIndex)`, pero
                    // ese contrato pide un `newZIndex` ABSOLUTO, mientras
                    // que el gesto de arrastre del timeline solo produce
                    // `steps` (un delta relativo) — no existe un
                    // "newZIndex deseado" nativo en este call site. Forzar
                    // la conversión acá (leer `state.layers` para calcular
                    // `newZIndex = current.zIndex - steps` y mandarlo a la
                    // API, que small internamente lo vuelve a convertir a
                    // `steps` para llegar al `reorderTrack` real del
                    // ViewModel — ver `EditorViewModel.setLayerZIndex`)
                    // sería un doble round-trip sobre el mismo estado
                    // vivo, con más riesgo de desfasaje entre el
                    // `state.layers` de esta recomposición y el estado
                    // real en el momento en que la corrutina corre, sin
                    // ningún beneficio real de desacoplamiento — exactamente
                    // el tipo de "wrapper inútil" que R5.18/R5.22 piden
                    // evitar. `reorderTrack(trackId, steps)` ya es, por
                    // diseño, una operación nativa de gesto de UI — se
                    // deja tal cual, apuntando directo al ViewModel.
                    //
                    // Generalizado de `onReorderLayer` (solo capas) a
                    // `onReorderTrack` (capas + audio, mismo `trackId` que
                    // ya usa `TimelineView` — `Layer.id` o `AudioTrack.id`)
                    // — BUG REAL corregido: la fila de audio no participaba
                    // de este gesto en absoluto, quedaba fija en la
                    // playlist mientras las capas de imagen sí se podían
                    // reordenar arrastrando (reportado con capturas de
                    // pantalla).
                    onReorderTrack = { trackId, steps -> viewModel.reorderTrack(trackId, steps) },
                    onDeleteLayerRequest = { layer -> layerPendingDelete = layer },
                    onScrubStart = { viewModel.beginScrub() },
                    // Mismo estado que el botón principal Reproducir/Pausar de la barra superior.
                    isPlaying = state.isPlaying,
                    onScrubEnd = { viewModel.endScrub() },
                    // Toque simple sobre el cabezal de la ventana ampliada del audio: solo reproduce (pausar: botón principal).
                    onPlay = { viewModel.startPlayback() },
                    onAddTrackClick = { showAddTrackDialog = true },
                    audioTracks = state.audioTracks,
                    selectedAudioTrackId = selectedAudioTrackId,
                    onSelectAudioTrack = { trackId ->
                        selectedAudioTrackId = trackId
                        selectedAudioClipId = null
                        viewModel.clearSelection()
                    },
                    // Un clip se soltó en su carril u en otro carril de audio: se mueve y el carril destino y el
                    // clip pasan a ser la selección (el destino pudo ser otro carril, o el de origen dejó de existir).
                    onAudioClipMove = { clipId, destTrackId, startMs ->
                        viewModel.moveAudioClip(clipId, destTrackId, startMs)
                        // Igual que `onSelectAudioTrack`: elegir un carril de audio deja sin elegir la capa de imagen
                        // (el arrastre ya no selecciona al empezar, así que esto es lo único que la limpia).
                        viewModel.clearSelection()
                        selectedAudioTrackId = destTrackId
                        selectedAudioClipId = clipId
                    },
                    // BUG REAL corregido acá (reportado con captura de
                    // pantalla: "elimina directo sin aviso, sin ventana
                    // diciéndote que vas a eliminar la capa, y así es como
                    // funcionan las otras capas"): antes esto borraba el
                    // audio DIRECTO, sin pasar por ningún diálogo — a
                    // diferencia de cualquier capa de imagen, que sí pasa por
                    // `layerPendingDelete` (ver `onDeleteLayerRequest` arriba)
                    // antes de borrar de verdad. Ahora sigue el MISMO patrón
                    // exacto: el ícono de basura solo marca la intención
                    // (`audioTrackPendingDelete = trackId`), y el borrado real
                    // recién ocurre si el usuario confirma en el `AlertDialog`
                    // de más abajo.
                    onRemoveAudioRequest = { trackId -> audioTrackPendingDelete = trackId },
                    // Edición de CLIPS dentro de cada carril (estirar bordes,
                    // copiar/duplicar/eliminar/pegar/cargar) — ver
                    // `AudioClipEditActions`. Una por carril: rejilla,
                    // selección de tiempo y destino de "+Clip"/pegar
                    // dependen del carril.
                    audioClipActionsFor = { track ->
                        AudioClipEditActions(
                            onLengthChange = { clipId, lengthMs -> viewModel.setAudioClipLength(clipId, lengthMs) },
                            onLeftEdgeChange = { clipId, deltaMs -> viewModel.moveAudioClipLeftEdge(clipId, deltaMs) },
                            // Solo el clip elegido de ESTE carril: un clip elegido
                            // en otro carril no debe contar acá.
                            selectedClipId = selectedAudioClipId?.takeIf { track.clip(it) != null },
                            onSelectClip = { clipId -> selectedAudioClipId = clipId },
                            onCopy = { clipId -> viewModel.copyAudioClip(clipId) },
                            gridMs = track.gridStepMs,
                            gridBpm = track.gridBpm,
                            gridStep = track.gridStep,
                            onGridChange = { bpm, step -> viewModel.setAudioGrid(track.id, bpm, step) },
                            timeSelection = state.audioTimeSelection?.takeIf { it.trackId == track.id }?.range,
                            onSelectClipRange = { clipId -> viewModel.selectAudioClipRange(clipId) },
                            onSelectRangeAtPlayhead = { viewModel.selectAudioRangeAtPlayhead(track.id) },
                            onTimeSelectionChange = { s, e -> viewModel.selectAudioTimeRange(track.id, s, e) },
                            onTimeRangeOp = { op -> viewModel.applyAudioTimeRangeOp(op) },
                            onClearTimeSelection = { viewModel.clearAudioTimeSelection() },
                            onCut = { clipId -> viewModel.cutAudioClip(clipId) },
                            onDuplicate = { clipId -> viewModel.duplicateAudioClip(clipId) },
                            onDelete = { clipId -> audioClipPendingDelete = clipId },
                            // "Renombrar" del menú del clip: solo ESE clip (el lápiz de la fila es del carril).
                            onRenameClip = { clipId, newName -> viewModel.renameAudioClip(clipId, newName) },
                            onPasteAtPlayhead = { viewModel.pasteAudioClipAtPlayhead(track.id) },
                            onImportClipAt = { startMs -> onImportAudioClick(startMs, track.id) },
                            onSplit = { clipId -> viewModel.splitAudioClipAtPlayhead(clipId) },
                            onToggleReverse = { clipId -> viewModel.toggleAudioReverse(context, clipId) },
                            onTrimToSelections = { clipId, selections -> viewModel.trimAudioClipToSelections(clipId, selections) },
                            onToggleNormalize = { clipId -> viewModel.toggleAudioNormalize(context, clipId) },
                            onMergeWithNext = { clipId -> viewModel.mergeAudioClipWithNext(clipId) },
                            canMergeWithNext = { clipId -> track.mergeCandidateAfter(clipId) != null },
                            onToggleMute = { clipId -> viewModel.toggleAudioMute(clipId) },
                            onToggleLoop = { clipId -> viewModel.toggleAudioLoop(clipId) },
                            onFadeChange = { clipId, fadeInMs, fadeOutMs -> viewModel.setAudioFade(clipId, fadeInMs, fadeOutMs) },
                            onTrackVolumeIconTap = { trackId -> viewModel.tapAudioTrackVolumeIcon(trackId) },
                            onTrackVolumeChange = { trackId, volume -> viewModel.setAudioTrackVolume(trackId, volume) },
                            onTrackPanChange = { trackId, pan -> viewModel.setAudioTrackPan(trackId, pan) },
                            onReplaceSource = { clipId -> onReplaceAudioSourceClick(clipId) },
                            hasClipboard = state.audioClipClipboard != null,
                            playheadMs = state.playheadMs,
                            sourceStates = audioSourceStates,
                            onRetrySource = { clipId -> viewModel.retryAudioSource(clipId) }
                        )
                    },
                    // --- Las acciones reales del panel de audio (ver
                    // AudioTrackRow.kt/AudioClip.kt) — mismo patrón exacto
                    // que `onRenameLayer`/`onChangeLayerColor`/etc. de
                    // abajo, con el id del carril: nombre/color/orden son
                    // identidad de cada CARRIL.
                    // El lápiz de la fila renombra el CARRIL completo, nunca un clip.
                    onRenameAudio = { trackId, newName -> viewModel.renameAudioTrack(trackId, newName) },
                    onChangeAudioColor = { trackId, colorArgb, useBW -> viewModel.setAudioCustomColor(trackId, colorArgb, useBW) },
                    onChangeAudioGradient = { trackId, startArgb, endArgb, angle, isRadial, useBW ->
                        viewModel.setAudioGradient(trackId, startArgb, endArgb, angle, isRadial, useBW)
                    },
                    onResetAudioColor = { trackId -> viewModel.resetAudioColor(trackId) },
                    onToggleAudioOrderLock = { trackId -> viewModel.toggleAudioOrderLock(trackId) },
                    // --- Ver el comentario grande en TimelineRow/
                    // LayerActionAccordion (TimelineView.kt): mientras
                    // cualquiera de las tres pestañas de abajo (Keyframes/
                    // Control/Módulos) está abierta, el panel de opciones de
                    // cada capa (lápiz/paleta/ojo/candados/basura) tiene
                    // que dejar de abrirse "al costado" — ahí mismo es
                    // donde vive el panel de la pestaña abierta (ver
                    // SectionPlaceholderPanel más abajo) y quedaba tapado.
                    // expandedBottomSection ya es la única fuente de
                    // verdad de "qué pestaña está abierta" (null = ninguna),
                    // así que este booleano es simplemente esa pregunta.
                    isBottomPanelExpanded = expandedBottomSection != null,
                    bottomInset = BOTTOM_BAR_HEIGHT,
                    audioPreview = rememberAudioClipPreviewControls(viewModel, state.audioClipPreview?.loopEnabled == true),
                    onAudioDetailOpenChange = { open ->
                        audioDetailOpen = open
                        // Un panel de Módulos/Control/Keyframes abierto se dibuja POR ENCIMA del timeline
                        // (es su hermano con zIndex 5): taparía la ventana. Se cierra al abrirla.
                        if (open) expandedBottomSection = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                )

                // --- El ícono de capas + flecha (y el menú "Multicolor" que
                // se abre al tocarlo) ya NO vive acá: se movió DENTRO de
                // TimelineView (ver TimelineView.kt), porque esa función
                // necesita leer y modificar el color de TODAS las capas a
                // la vez — algo que TimelineView ya podía hacer con sus
                // propios parámetros (onChangeLayerColor, onChangeLayerGradient,
                // onResetLayerColor), sin tener que subir ese estado hasta
                // acá y volver a bajarlo. Antes vivía en este archivo porque
                // el viejo panel emergente de capas era más simple y no
                // necesitaba nada de eso.

                // --- Panel vacío de Keyframes / Control / Módulos: se
                // superpone al timeline (master + capas) cuando una de las
                // tres pestañas de abajo está activa, PERO nunca tapa la
                // regla de tiempo de arriba ni la barra de pestañas de
                // abajo — el padding(top = RULER_HEIGHT, start =
                // LABEL_COLUMN_WIDTH) recorta exactamente ese hueco, mismos
                // valores que ya usan TimelineView y EditorBottomBar, así
                // los tres quedan perfectamente alineados sin duplicar
                // números a mano. Vive DENTRO de este mismo Box (el que ya
                // tiene el alto real del timeline vía weight(1f)), así su
                // borde inferior cae justo, sin espacio de más, donde
                // empieza EditorBottomBar.
                expandedBottomSection?.let { section ->
                    SectionPlaceholderPanel(
                        section = section,
                        onClose = { expandedBottomSection = null },
                        // --- Ver comentario completo en
                        // SectionPlaceholderPanel/ControlImageOptionsPanel
                        // (EditorBottomBar.kt): por ahora TODAS las capas
                        // son de imagen, así que esto es simplemente "hay
                        // una capa seleccionada" — se deja explícito acá
                        // (en vez de un `true` fijo adentro del panel)
                        // para que el día que existan capas de otro tipo
                        // esto se pueda filtrar por tipo real sin tocar
                        // EditorBottomBar.kt.
                        hasImageLayerSelected = selectedLayer != null,
                        // El cajón de Módulos muestra la lista del tipo de capa seleccionado (audio o imagen).
                        modulesTarget = modulesTargetFor(
                            selectedAudioTrackId = selectedAudioTrackId,
                            selectedLayerId = state.selectedLayerId,
                            existingAudioTrackIds = state.audioTracks.map { it.id }
                        ),
                        onJoystickDirectionChange = { x, y ->
                            joystickMover.setDirection(x, y)
                        },
                        onZoomXModuleClick = { zoomXWindowOpen = true },
                        onReverbModuleClick = {
                            // Cargar el módulo en el carril (si aún no lo tiene) y mostrar su ventana.
                            selectedAudioTrackId?.let { viewModel.addReverb(it) }
                            reverbWindowOpen = true
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = LABEL_COLUMN_WIDTH, top = RULER_HEIGHT, bottom = BOTTOM_BAR_HEIGHT)
                            .zIndex(5f)
                    )
                }
            }

            // --- Cabecera de secciones Keyframes / Control / Módulos ---
            // Antes acá solo había un relleno morado sólido de 16dp para
            // cerrar el borde inferior del timeline. Ahora ese espacio pasa
            // a ser esta barra de navegación entre paneles: arranca justo
            // donde termina la columna de miniaturas de las capas (nunca
            // desde el borde izquierdo de la pantalla — el hueco de la
            // izquierda deja ver el mismo relleno morado de fondo), y ofrece
            // tres secciones — Módulos a la izquierda, Control al medio,
            // Keyframes a la derecha — cada una abre su cajón
            // (Keyframes todavía es un marcador "Próximamente").
            if (!audioDetailOpen) {
                EditorBottomBar(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                    selectedSection = expandedBottomSection,
                    onKeyframesClick = {
                        expandedBottomSection = if (expandedBottomSection == BottomBarSection.KEYFRAMES) null
                            else BottomBarSection.KEYFRAMES
                    },
                    onControlClick = {
                        expandedBottomSection = if (expandedBottomSection == BottomBarSection.CONTROL) null
                            else BottomBarSection.CONTROL
                    },
                    onModulesClick = {
                        expandedBottomSection = if (expandedBottomSection == BottomBarSection.MODULES) null
                            else BottomBarSection.MODULES
                    }
                )
            }
            } // fin del Box interno (timeline + EditorBottomBar superpuesta)

            // --- Panel "Editando imagen": tapa TODO lo de abajo (ruler +
            // capas + la barra Keyframes/Control/Módulos) de punta a punta,
            // mismo patrón que ProjectInfoPanel arriba — Box hermano de la
            // Column (timeline+bottombar) dentro de este mismo Box
            // exterior con weight(1f), así su fillMaxSize() cubre
            // exactamente ese alto combinado completo, sin dejar nada
            // asomado abajo ni arriba. Sin padding, sin borde amarillo
            // (eso era tu marcador en la referencia, no un color real
            // pedido) — mismo morado oscuro sólido que el resto de
            // ventanas de la app (SurfaceTintedElevated).
            // Chequeo explícito con `!= null` (no `selectedLayer?.id`) a
            // propósito acá: LayerColorEditPanel de abajo necesita un
            // Layer no-nulo, y Kotlin solo puede "smart cast" selectedLayer
            // como no-nulo dentro de este bloque si la condición lo
            // verifica de forma DIRECTA — comparar `selectedLayer?.id` no
            // alcanza para que el compilador lo infiera, aunque en la
            // práctica sea imposible que este bloque corra con
            // selectedLayer null (editModeLayerId ya no sería igual a
            // null.id). Este fue justo el error real que rompió el build
            // (`Argument type mismatch: actual type is 'Layer?', but
            // 'Layer' was expected`).
            // A PEDIDO DEL USUARIO — MULTI-VENTANA: "Recolor" y "Básico"
            // (Color) ya NO usan esta ventana de abajo — sus ajustes viven
            // enteros en sendas ventanas flotantes (ver RecolorFloatingWindow
            // y ColorBasicoFloatingWindow, más arriba en este mismo Box).
            // Esta ventana de abajo ahora se controla con su PROPIO flag
            // independiente (`effectsWindowOpen`), igual que las demás —
            // ya no depende de "qué pestaña esté activa", así que puede
            // seguir abierta al mismo tiempo que Recolor/Básico/3D.
            if (editModeLayerId != null && selectedLayer != null && editModeLayerId == selectedLayer.id && effectsWindowOpen) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f),
                    color = SurfaceTintedElevated
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LayerColorEditPanel(
                            layer = selectedLayer,
                            viewModel = viewModel,
                            extrude3DBridge = extrude3DBridge,
                            effectsCategory = editImageEffectsCategory,
                            onEffectsCategoryChange = { editImageEffectsCategory = it },
                            ctrl = effectsCtrl,
                            sources = effectsSources
                        )
                        // A PEDIDO DEL USUARIO: ahora que este panel puede
                        // convivir con Recolor/Básico/3D abiertos a la vez,
                        // necesita su propia forma de cerrarse — antes
                        // "se cerraba" nada más porque el usuario pasaba a
                        // otra pestaña de un slot único que ya no existe.
                        IconButton(
                            onClick = { effectsWindowOpen = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .zIndex(11f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Cerrar Efecto",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // --- Panel "Información del proyecto": tapa TODO lo de abajo
            // (regla, capas, barra Keyframes/Control/Módulos) de punta a
            // punta, con animación de acordeón — se despliega desde arriba
            // hacia abajo al abrir (expandVertically, anclado arriba) y se
            // retrae hacia arriba al cerrar (shrinkVertically, mismo
            // ancla). Dos formas de cerrar, como pediste: la X de adentro
            // del panel (ver ProjectInfoPanel) y tocar de nuevo este mismo
            // ícono en la barra de arriba (ic_project_info) — ambas
            // terminan en lo mismo, showProjectInfoPanel = false, así que
            // ninguna de las dos necesita lógica extra acá.
            // BUG REAL DE COMPILACIÓN corregido (esto es lo que rompió el
            // build en GitHub Actions): `AnimatedVisibility` no es una sola
            // función — Compose declara varias versiones con el mismo
            // nombre (la genérica, y otras que son extensión de
            // ColumnScope/RowScope). Acá este Box está anidado DENTRO de la
            // Column general de toda la pantalla (más arriba en este mismo
            // archivo), así que esa ColumnScope sigue "alcanzable" como
            // receptor implícito aunque el Box esté en el medio. El
            // compilador encontró esa versión de ColumnScope como
            // candidata y no supo decidir automáticamente por la genérica,
            // así que pedía un receptor explícito ("cannot be called in
            // this context with an implicit receiver"). La solución real
            // es nombrar el paquete completo acá, así no hay ninguna
            // ambigüedad posible: SIEMPRE la versión genérica, sea cual
            // sea el anidado de Column/Box a su alrededor.
            androidx.compose.animation.AnimatedVisibility(
                visible = showProjectInfoPanel,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(durationMillis = 320)
                ) + fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(durationMillis = 280)
                ) + fadeOut(animationSpec = tween(durationMillis = 160)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .zIndex(10f)
            ) {
                ProjectInfoPanel(
                    onClose = { showProjectInfoPanel = false },
                    title = state.projectName,
                    onTitleChange = { viewModel.renameProject(it) },
                    releaseYear = state.releaseYear,
                    onReleaseYearChange = { viewModel.updateReleaseYear(it) },
                    genre = state.genre,
                    onGenreChange = { viewModel.updateGenre(it) },
                    durationMinutes = state.infoDurationMinutes,
                    onDurationMinutesChange = { viewModel.updateInfoDurationMinutes(it) },
                    castPhotoFiles = state.castPhotoFiles,
                    onPickCastPhoto = onPickCastPhotoClick,
                    onRemoveCastPhoto = { viewModel.removeCastPhoto(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            } // fin del Box exterior (timeline+bottombar normal, o panel de info encima)

            } // fin if (!canvasFillsScreen)
        } // fin de la Column (preview + línea de tiempo), ahora hija del Box de abajo

        // --- Ventanas flotantes de edición: Recolor / Color Básico / 3D ---
        // A PEDIDO DEL USUARIO — ARREGLO PROFESIONAL DEL BUG REPORTADO CON
        // CAPTURA ("solo permite una sola ventana... y no dos o más"):
        // las tres ventanas de acá abajo son HERMANAS entre sí y de la
        // Column de preview+timeline, dentro de este mismo Box — cada una
        // se dibuja o no según su PROPIO flag independiente
        // (`recolorWindowOpen`, `colorBasicoWindowOpen`,
        // `basico3DWindowOpen`), sin ningún estado compartido de un solo
        // slot que las obligue a excluirse entre sí. Resultado: el
        // usuario puede tener dos, o las tres, abiertas y visibles al
        // mismo tiempo — tantas como vaya tocando —, cada una arrastrable
        // y cerrable por separado con su propia ×, exactamente como un
        // host de plugins de audio flotantes (FL Studio, VST) donde cada
        // plugin es su propia ventana independiente.
        //
        // Para que dos o tres ventanas recién abiertas no nazcan tapándose
        // exactamente en el mismo punto (esquina superior izquierda), cada
        // una arranca con un offset ligeramente escalonado — el usuario
        // puede arrastrar cualquiera a cualquier otra parte del canvas
        // apenas se abre, esto es solo el punto de partida.
        val floatingWindowBaseOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            Offset(20.dp.toPx(), 90.dp.toPx())
        }
        val floatingWindowStaggerPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            Offset(28.dp.toPx(), 28.dp.toPx())
        }
        // Un solo registro, compartido por las tres ventanas (Recolor /
        // Color Básico / 3D Básico) — ver el comentario grande sobre
        // [FloatingWindowMinimizedRegistry] para el porqué: es lo que le
        // permite a cada una, al minimizarse, esquivar a las otras dos
        // en vez de quedar apiladas en el mismo ícono.
        val floatingWindowMinimizedRegistry = remember { FloatingWindowMinimizedRegistry() }

        if (recolorFloatingWindowVisible) {
            // `selectedLayer` es nullable — `recolorFloatingWindowVisible`
            // ya exige que no lo sea (ver su definición, más arriba en
            // este archivo), pero el compilador no puede probarlo a
            // través de un `val` booleano ya calculado, así que se
            // resuelve con `?.let` acá en vez de forzar con `!!`.
            selectedLayer?.let { layer ->
                RecolorFloatingWindow(
                    layer = layer,
                    context = context,
                    viewModel = viewModel,
                    initialOffset = floatingWindowBaseOffsetPx,
                    onClose = { recolorWindowOpen = false },
                    onInteracted = {
                        floatingWindowZOrderCounter += 1f
                        recolorWindowZIndex = floatingWindowZOrderCounter
                    },
                    minimizedRegistry = floatingWindowMinimizedRegistry,
                    containerSizePx = floatingWindowAreaSizePx,
                    modifier = Modifier.zIndex(20f + recolorWindowZIndex)
                )
            }
        }

        if (colorBasicoFloatingWindowVisible) {
            // Mismo motivo que en el bloque de "Recolor" de acá arriba:
            // `?.let` en vez de `!!`. Offset escalonado una vez respecto
            // al de "Recolor" para que, si el usuario abre las dos juntas,
            // no aparezcan exactamente superpuestas.
            selectedLayer?.let { layer ->
                ColorBasicoFloatingWindow(
                    layer = layer,
                    context = context,
                    viewModel = viewModel,
                    initialOffset = floatingWindowBaseOffsetPx + floatingWindowStaggerPx,
                    onClose = { colorBasicoWindowOpen = false },
                    onInteracted = {
                        floatingWindowZOrderCounter += 1f
                        colorBasicoWindowZIndex = floatingWindowZOrderCounter
                    },
                    minimizedRegistry = floatingWindowMinimizedRegistry,
                    containerSizePx = floatingWindowAreaSizePx,
                    modifier = Modifier.zIndex(20f + colorBasicoWindowZIndex)
                )
            }
        }

        if (editModeLayerId != null && selectedLayer != null &&
            editModeLayerId == selectedLayer.id && basico3DWindowOpen
        ) {
            // Offset escalonado dos veces respecto al de "Recolor" — si
            // el usuario abre las tres ventanas juntas, cada una nace en
            // un punto distinto del canvas.
            Basico3DFloatingWindow(
                layer = selectedLayer,
                context = context,
                viewModel = viewModel,
                extrude3DBridge = extrude3DBridge,
                initialOffset = floatingWindowBaseOffsetPx + floatingWindowStaggerPx + floatingWindowStaggerPx,
                onClose = { basico3DWindowOpen = false },
                onInteracted = {
                    floatingWindowZOrderCounter += 1f
                    basico3DWindowZIndex = floatingWindowZOrderCounter
                },
                minimizedRegistry = floatingWindowMinimizedRegistry,
                containerSizePx = floatingWindowAreaSizePx,
                modifier = Modifier.zIndex(20f + basico3DWindowZIndex)
            )
        }

        if (editModeLayerId != null && selectedLayer != null &&
            editModeLayerId == selectedLayer.id && contornoWindowOpen
        ) {
            // Offset escalonado tres veces respecto al de "Recolor" —
            // sigue la misma cadena de las tres ventanas de arriba.
            ContornoFloatingWindow(
                layer = selectedLayer,
                viewModel = viewModel,
                ctrl = effectsCtrl,
                sources = effectsSources,
                initialOffset = floatingWindowBaseOffsetPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx,
                onClose = { contornoWindowOpen = false },
                onInteracted = {
                    floatingWindowZOrderCounter += 1f
                    contornoWindowZIndex = floatingWindowZOrderCounter
                },
                minimizedRegistry = floatingWindowMinimizedRegistry,
                containerSizePx = floatingWindowAreaSizePx,
                modifier = Modifier.zIndex(20f + contornoWindowZIndex)
            )
        }

        if (editModeLayerId != null && selectedLayer != null &&
            editModeLayerId == selectedLayer.id && resplandorWindowOpen
        ) {
            // Offset escalonado cuatro veces — última de la cadena.
            ResplandorFloatingWindow(
                layer = selectedLayer,
                viewModel = viewModel,
                ctrl = effectsCtrl,
                sources = effectsSources,
                initialOffset = floatingWindowBaseOffsetPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx,
                onClose = { resplandorWindowOpen = false },
                onInteracted = {
                    floatingWindowZOrderCounter += 1f
                    resplandorWindowZIndex = floatingWindowZOrderCounter
                },
                minimizedRegistry = floatingWindowMinimizedRegistry,
                containerSizePx = floatingWindowAreaSizePx,
                modifier = Modifier.zIndex(20f + resplandorWindowZIndex)
            )
        }

        if (editModeLayerId != null && selectedLayer != null &&
            editModeLayerId == selectedLayer.id && sombraWindowOpen
        ) {
            // Offset escalonado cinco veces — sigue la misma cadena.
            SombraFloatingWindow(
                layer = selectedLayer,
                viewModel = viewModel,
                ctrl = effectsCtrl,
                sources = effectsSources,
                initialOffset = floatingWindowBaseOffsetPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx,
                onClose = { sombraWindowOpen = false },
                onInteracted = {
                    floatingWindowZOrderCounter += 1f
                    sombraWindowZIndex = floatingWindowZOrderCounter
                },
                minimizedRegistry = floatingWindowMinimizedRegistry,
                containerSizePx = floatingWindowAreaSizePx,
                modifier = Modifier.zIndex(20f + sombraWindowZIndex)
            )
        }

        if (editModeLayerId != null && selectedLayer != null &&
            editModeLayerId == selectedLayer.id && reflejoWindowOpen
        ) {
            // Offset escalonado seis veces — sigue la misma cadena.
            ReflejoFloatingWindow(
                layer = selectedLayer,
                viewModel = viewModel,
                ctrl = effectsCtrl,
                sources = effectsSources,
                initialOffset = floatingWindowBaseOffsetPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx,
                onClose = { reflejoWindowOpen = false },
                onInteracted = {
                    floatingWindowZOrderCounter += 1f
                    reflejoWindowZIndex = floatingWindowZOrderCounter
                },
                minimizedRegistry = floatingWindowMinimizedRegistry,
                containerSizePx = floatingWindowAreaSizePx,
                modifier = Modifier.zIndex(20f + reflejoWindowZIndex)
            )
        }

        if (editModeLayerId != null && selectedLayer != null &&
            editModeLayerId == selectedLayer.id && distortionWindowOpen
        ) {
            // Offset escalonado siete veces — sigue la misma cadena.
            DistortionFloatingWindow(
                layer = selectedLayer,
                context = context,
                viewModel = viewModel,
                distortionBridge = distortionBridge,
                initialOffset = floatingWindowBaseOffsetPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx +
                    floatingWindowStaggerPx,
                onClose = { distortionWindowOpen = false },
                onInteracted = {
                    floatingWindowZOrderCounter += 1f
                    distortionWindowZIndex = floatingWindowZOrderCounter
                },
                minimizedRegistry = floatingWindowMinimizedRegistry,
                containerSizePx = floatingWindowAreaSizePx,
                modifier = Modifier.zIndex(20f + distortionWindowZIndex)
            )
        }

        if (selectedLayer != null && zoomXWindowOpen) {
            // "Zoom X" — a diferencia de las ocho ventanas de arriba, NO
            // depende de `editModeLayerId` (ver el comentario grande
            // donde se declaró `zoomXWindowOpen`, más arriba en este
            // archivo). Lambdas desacopladas apuntando a las MISMAS
            // variables locales `scale`/`alpha`/`commitLiveFrame()` que
            // ya alimentan al joystick y al pellizco de dos dedos — un
            // solo punto de verdad para "cuál es el zoom vigente de la
            // capa", nunca un estado paralelo propio de este módulo (ver
            // el comentario grande en ZoomXFloatingWindow.kt).
            //
            // Offset escalonado ocho veces — sigue la misma cadena que
            // las otras ocho ventanas.
            ZoomXFloatingWindow(
                getScale = { scale },
                setScale = { scale = it },
                getAlpha = { alpha },
                setAlpha = { alpha = it },
                layerIsEditable = { selectedLayer.locked == false },
                commitFrame = { commitLiveFrame() },
                gradualFadeEnabled = zoomXGradualFadeEnabled,
                onGradualFadeEnabledChange = { zoomXGradualFadeEnabled = it },
                initialOffset = floatingWindowBaseOffsetPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx + floatingWindowStaggerPx +
                    floatingWindowStaggerPx + floatingWindowStaggerPx,
                onClose = { zoomXWindowOpen = false },
                onInteracted = {
                    floatingWindowZOrderCounter += 1f
                    zoomXWindowZIndex = floatingWindowZOrderCounter
                },
                minimizedRegistry = floatingWindowMinimizedRegistry,
                containerSizePx = floatingWindowAreaSizePx,
                modifier = Modifier.zIndex(20f + zoomXWindowZIndex)
            )
        }

        // "Reverb" del carril de audio seleccionado. TODA su lógica de visibilidad vive en [ReverbWindowHost] (a propósito: este
        // cuerpo ya es enorme y el verificador de Android rechaza la clase si crece — ver el comentario de ese archivo).
        ReverbWindowHost(
            visible = reverbWindowOpen,
            audioTracks = state.audioTracks,
            selectedTrackId = selectedAudioTrackId,
            onParamsChange = viewModel::setReverbParams,
            onPresetSelected = viewModel::applyReverbPreset,
            onEnabledChange = viewModel::setReverbEnabled,
            onDelete = viewModel::removeReverb,
            initialOffset = floatingWindowBaseOffsetPx + floatingWindowStaggerPx * 9f,
            onClose = { reverbWindowOpen = false },
            onInteracted = {
                floatingWindowZOrderCounter += 1f
                reverbWindowZIndex = floatingWindowZOrderCounter
            },
            minimizedRegistry = floatingWindowMinimizedRegistry,
            containerSizePx = floatingWindowAreaSizePx,
            modifier = Modifier.zIndex(20f + reverbWindowZIndex)
        )

        // A PEDIDO EXPLÍCITO DEL USUARIO: la zona de "Eliminar" — un solo
        // ejemplar, compartido por las ocho ventanas (mismo criterio que
        // `floatingWindowMinimizedRegistry` arriba) — vive acá, como
        // hermana directa de las ocho.
        //
        // BUG REAL corregido (reportado con captura: "la bola flotante
        // al ubicarse encima del ícono eliminar va por detrás, debería
        // ir por encima"): esto tenía `zIndex(1000f)` — a propósito de
        // no quedar tapada por ninguna ventana, pero eso hacía que
        // GANARA incluso contra el propio ícono que se arrastra HACIA
        // ella, justo cuando más importa verlo (a punto de soltar). Se
        // baja a 15f — por debajo del `zIndex` base de las tres
        // ventanas (`20f + orden de toque`, ver dónde se instancian) —
        // y el ícono que se arrastra activamente sube el SUYO muy por
        // encima de esto mientras dura el arrastre (ver
        // `isDraggingThisBadge` dentro de [FloatingToolWindow]), así que
        // siempre se ve por encima de la zona sin importar dónde ande
        // el contador de "orden de toque" de las tres ventanas.
        FloatingWindowDeleteDropZone(
            minimizedRegistry = floatingWindowMinimizedRegistry,
            modifier = Modifier.zIndex(15f)
        )
        } // fin del Box exterior (preview + línea de tiempo, y las ventanas flotantes de Color/Básico/3D encima)
    }

    if (showRenameDialog) {
        RenameProjectDialog(
            initialName = state.projectName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                viewModel.renameProject(newName)
            }
        )
    }

    // El panel de la papelera (LayerTrashPanel) ya NO se declara acá: ahora
    // vive anclado al propio ícono que lo abre, como hermano del
    // `IconButton` dentro de su mismo `Box` — ver ese bloque más arriba, en
    // la sección de íconos del toolbar. Queda este comentario para que
    // nadie, al buscar "showLayerTrashDialog", asuma que falta el call site:
    // no falta, se movió a propósito para que el panel pueda anclarse a su
    // ícono en vez de centrarse en la pantalla.

    // El diálogo queda abierto mientras el usuario lo pidió explícitamente
    // O mientras haya una exportación en curso / un resultado pendiente de
    // ver — así, si se cierra por error mientras exporta, el progreso
    // sigue siendo accesible tocando el ícono de exportar de nuevo.
    if (showExportDialog || state.exportProgress != null) {
        ExportDialog(
            projectName = state.projectName,
            canvas = state.canvas,
            quality = state.exportQuality,
            dimensionsPx = viewModel.currentExportDimensions(),
            onQualityChange = { viewModel.setExportQuality(it) },
            exportProgress = state.exportProgress,
            onStartExport = { fileName -> viewModel.exportVideo(context, fileName) },
            onShare = { outputFile ->
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", outputFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir video"))
            },
            onDismiss = {
                showExportDialog = false
                viewModel.clearExportState()
            },
            onCancelExport = { viewModel.cancelExport() }
        )
    }

    if (showAddTrackDialog) {
        AddTrackDialog(
            onDismiss = { showAddTrackDialog = false },
            onImportImageClick = {
                showAddTrackDialog = false
                onImportClick()
            },
            onImportAudioClick = {
                showAddTrackDialog = false
                // Capa de audio nueva: su primer clip arranca en el cursor.
                onImportAudioClick(state.playheadMs, null)
            }
        )
    }

    layerPendingRename?.let { layer ->
        RenameLayerDialog(
            initialName = layer.name,
            accentColor = effectiveLayerColorStrong(layer),
            onDismiss = { layerPendingRename = null },
            onConfirm = { newName ->
                layerPendingRename = null
                viewModel.renameLayer(layer.id, newName)
            }
        )
    }

    layerPendingColorChange?.let { layer ->
        LayerColorPickerDialog(
            initialColorArgb = layer.customColorArgb,
            initialGradientStartArgb = layer.customGradientStartArgb,
            initialGradientEndArgb = layer.customGradientEndArgb,
            initialUseGradient = layer.useGradientColor,
            initialGradientAngleDegrees = layer.gradientAngleDegrees,
            initialGradientIsRadial = layer.gradientIsRadial,
            initialBlackAndWhiteMode = layer.useBlackAndWhiteMode,
            fallbackColorArgb = layerTrackColor(layer.colorIndex).toArgb(),
            onDismiss = { layerPendingColorChange = null },
            onSelectColor = { colorArgb, useBW ->
                layerPendingColorChange = null
                viewModel.setLayerCustomColor(layer.id, colorArgb, useBW)
            },
            onSelectGradient = { startArgb, endArgb, angleDegrees, isRadial, useBW, _ ->
                layerPendingColorChange = null
                viewModel.setLayerGradient(layer.id, startArgb, endArgb, angleDegrees, isRadial, useBW)
            },
            onReset = { viewModel.resetLayerColor(layer.id) }
        )
    }

    layerPendingDelete?.let { layer ->
        // HALLAZGO DE AUDITORÍA: un proyecto nunca se guarda con 0 capas
        // (ver el guard `if (layers.isEmpty())` en
        // EditorViewModel.persistNow y en ProjectStorage.saveProject,
        // ambos deliberados) — pero hasta ahora este diálogo no lo sabía:
        // dejaba borrar la última capa igual, mostrando "esta acción no
        // se puede deshacer" y cerrando el diálogo como si el borrado
        // hubiera quedado firme. En los hechos, el próximo autoguardado
        // con 0 capas es un no-op silencioso — el usuario ve la capa
        // desaparecer en esta sesión, pero al reabrir el proyecto (otro
        // día, tras un reinicio del proceso) la capa "vuelve sola", sin
        // ningún aviso: la promesa del diálogo ("no se puede deshacer")
        // termina siendo falsa justo en este caso.
        //
        // La corrección se hace ACÁ, en el punto donde el usuario decide,
        // no en el guardado: si es la única capa que queda, se muestra un
        // diálogo informativo (sin opción de "Eliminar") explicando por
        // qué, en vez de dejar avanzar hacia un borrado que después no se
        // sostiene. Mismo criterio que usan editores comparables (nunca
        // dejan el lienzo completamente vacío) y coherente con que
        // "Mis proyectos" ya ofrece "Eliminar proyecto" como la vía real
        // para vaciar todo un proyecto.
        if (state.layers.size <= 1) {
            AlertDialog(
                shape = RectangleShape,
                onDismissRequest = { layerPendingDelete = null },
                title = { Text("No se puede eliminar la última capa") },
                text = {
                    Text("Un proyecto necesita al menos una capa. Si querés vaciarlo por completo, eliminá el proyecto entero desde \"Mis proyectos\".")
                },
                confirmButton = {
                    TextButton(onClick = { layerPendingDelete = null }) {
                        Text("Entendido")
                    }
                }
            )
        } else {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { layerPendingDelete = null },
            title = { Text("¿Eliminar esta capa?") },
            text = {
                Text("\"${layer.name}\" se va a borrar junto con todos sus keyframes y ajustes de look. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeLayer(layer.id)
                    layerPendingDelete = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { layerPendingDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
        }
    }

    // BUG REAL corregido acá (reportado con captura de pantalla: "elimina
    // directo sin aviso, sin ventana diciéndote que vas a eliminar la
    // capa, y así es como funcionan las otras capas") — mismo patrón
    // EXACTO que `layerPendingDelete` arriba, sin la variante de "última
    // capa" (un proyecto SÍ puede quedarse sin audio; a diferencia de las
    // capas de imagen, no hay ningún guard en `ProjectStorage`/
    // `EditorViewModel` que lo impida).
    audioTrackPendingDelete?.let { pendingTrackId ->
        val pendingTrack = state.audioTracks.track(pendingTrackId)
        val firstClipName = pendingTrack?.label ?: "Audio"
        val otherClips = ((pendingTrack?.clips?.size ?: 1) - 1).coerceAtLeast(0)
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { audioTrackPendingDelete = null },
            title = { Text("¿Eliminar esta capa de audio?") },
            text = {
                Text(
                    "\"$firstClipName\"" + (if (otherClips > 0) " y $otherClips clip(s) más" else "") +
                        " se va a borrar del proyecto junto con su volumen, recorte, bucle y desvanecidos " +
                        "configurados. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedAudioTrackId == pendingTrackId) selectedAudioTrackId = null
                    viewModel.removeAudioTrack(pendingTrackId)
                    audioTrackPendingDelete = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { audioTrackPendingDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmación al eliminar UN clip de audio desde su menú (antes borraba
    // directo, a diferencia de las capas). El diálogo vive en su propio
    // composable (`AudioClipDeleteDialog`): `EditorScreen` ya es una función
    // enorme y cada variable local nueva la acerca al límite de registros del
    // compilador de Android (ver ADR-AUDIO-CLIP-EN-CARRIL).
    audioClipPendingDelete?.let { pendingClipId ->
        AudioClipDeleteDialog(
            clipId = pendingClipId,
            audioTracks = state.audioTracks,
            onConfirm = {
                // Si el clip borrado era el seleccionado, se limpia la selección.
                if (selectedAudioClipId == pendingClipId) selectedAudioClipId = null
                viewModel.removeAudioClip(pendingClipId)
                audioClipPendingDelete = null
            },
            onDismiss = { audioClipPendingDelete = null }
        )
    }

    // Diálogo de confirmación para CANCELAR el modo edición (ver la ×
    // dentro del modo edición aislado, más arriba). Avisa antes de volver
    // al canvas principal que la edición de esta sesión no se va a
    // aplicar. Tocar afuera o "No" no toca nada del estado — la capa
    // sigue en modo edición tal cual estaba.
    if (showEditBackgroundColorPicker) {
        ProfessionalColorPickerDialog(
            initialArgb = editBackgroundState.settings.customArgb,
            onDismiss = { showEditBackgroundColorPicker = false },
            onConfirm = { argb ->
                editBackgroundState.setCustom(argb)
                showEditBackgroundColorPicker = false
            }
        )
    }

    if (showCancelEditModeConfirm) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { showCancelEditModeConfirm = false },
            title = { Text("¿Salir sin aplicar la edición?") },
            text = {
                Text("Los cambios que hiciste en esta edición no se van a aplicar y vas a volver al canvas principal.")
            },
            confirmButton = {
                TextButton(onClick = {
                    // ARREGLADO: antes llamaba a exitEditMode(), que NO
                    // revierte nada — dejaba aplicado el recoloreo/3D/
                    // efecto que commitLayerRecolor ya había escrito a
                    // disco durante la sesión, contradiciendo el texto de
                    // este mismo diálogo. Ahora sí descarta de verdad
                    // (ver cancelEditMode más arriba).
                    cancelEditMode()
                    showCancelEditModeConfirm = false
                }) {
                    Text("Sí, salir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelEditModeConfirm = false }) {
                    Text("No")
                }
            }
        )
    }
    // Diálogo de confirmación para el botón "←" de la barra superior
    // MIENTRAS hay una capa en modo edición aislado con cambios reales
    // hechos en esta sesión (ver el bug corregido junto a
    // `showBackDuringEditModeConfirm`, más arriba). A diferencia del
    // diálogo de la × de acá arriba (que SIEMPRE descarta lo hecho en la
    // sesión), los cambios de Recolor/3D/Efectos ya están escritos a
    // disco por `commitLayerRecolor` (autoguardado con debounce de
    // 500ms) — así que "Guardar y salir" simplemente cierra el panel
    // dejando lo ya aplicado (mismo efecto que la manija ✓ de Editar),
    // sin necesidad de "guardar" nada de forma explícita. La otra opción
    // es quedarse editando; NO hay opción de "descartar" acá — para eso
    // ya existe la × dentro del panel, con su propio diálogo específico
    // de arriba.
    if (showBackDuringEditModeConfirm) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { showBackDuringEditModeConfirm = false },
            title = { Text("¿Guardar cambios?") },
            text = {
                Text("Hiciste ajustes en esta edición. Se guardan solos, así que podés volver a la pantalla del proyecto cuando quieras.")
            },
            confirmButton = {
                TextButton(onClick = {
                    exitEditMode()
                    showBackDuringEditModeConfirm = false
                }) {
                    Text("Guardar y salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackDuringEditModeConfirm = false }) {
                    Text("Seguir editando")
                }
            }
        )
    }
    // Diálogo de confirmación para CANCELAR el modo reordenar (ver la ×
    // dentro del modo reordenar, más arriba). Tocar afuera o "No" no
    // toca nada del estado — el reordenamiento sigue activo tal cual
    // estaba, borrador incluido.
    if (showCancelReorderConfirm) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { showCancelReorderConfirm = false },
            title = { Text("¿Cancelar el reordenamiento?") },
            text = {
                Text("Se va a descartar el orden que estabas armando y no se va a guardar ningún cambio.")
            },
            confirmButton = {
                TextButton(onClick = {
                    reorderMode = false
                    reorderDraftOrder = null
                    reorderScope = null
                    reorderDragFromPosition = null
                    showCancelReorderConfirm = false
                }) {
                    Text("Sí, cancelar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelReorderConfirm = false }) {
                    Text("No")
                }
            }
        )
    }

    // A PEDIDO DEL USUARIO — diálogo "¿Guardar los cambios?" al tocar
    // "←"/atrás desde la pantalla BASE del proyecto (no el modo edición
    // aislado, que tiene el suyo propio arriba) habiendo cambios reales
    // en la sesión. Tres acciones bien diferenciadas, ninguna ambigua:
    //  - "Guardar y salir": el guardado de siempre (`saveNow`), después
    //    navega a Mis proyectos.
    //  - "Salir sin guardar": descarta lo hecho en ESTA sesión y vuelve el
    //    proyecto a como estaba al abrirlo (`discardChangesAndExit`),
    //    después navega — en rojo porque, a diferencia de "Cancelar", esta
    //    sí pierde trabajo.
    //  - "Cancelar": cierra el diálogo sin tocar nada, se sigue editando
    //    tal cual estaba.
    if (showExitSaveConfirm) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { showExitSaveConfirm = false },
            title = { Text("¿Guardar los cambios?") },
            text = {
                Text("Hay cambios en este proyecto desde que lo abriste. Podés guardarlos y salir, salir sin guardarlos (se pierden los de esta sesión), o seguir editando.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitSaveConfirm = false
                    viewModel.saveNow { onBackToProjects() }
                }) {
                    Text("Guardar y salir")
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        showExitSaveConfirm = false
                        viewModel.discardChangesAndExit { onBackToProjects() }
                    }) {
                        Text("Salir sin guardar", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showExitSaveConfirm = false }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    // Nota: la mini-ventana "Restablecer" (con las 6 opciones puntuales)
    // ahora se dibuja anclada al pie de su propio ícono, junto con el resto
    // de los menús flotantes del marco de edición — buscar
    // `showRestoreOptionsMenu` más arriba en este archivo, cerca de
    // `restoreHandleAnchor`. La descripción de cada opción puntual ya no es
    // un elemento flotante aparte (ver KDoc de `RestoreOptionRow` más
    // abajo, `isInfoExpanded`) — se abre inline, dentro de la propia fila.
}

/**
 * Una fila de la mini-ventana "Restablecer": un ícono SVG que representa la
 * opción, pegado al nombre, y — pegado justo al lado, sin empujarlo al
 * borde derecho — un icono "i" pequeño. Un separador fino cierra la fila
 * (salvo la última, `showDivider = false`). Tocar el nombre aplica esa
 * opción puntual (`onSelect`) y cierra la ventana (`onDismiss`); tocar el
 * icono "i" expande/colapsa la descripción de ESTA fila (`isInfoExpanded` /
 * `onToggleInfo`), sin cerrar ni aplicar nada.
 *
 * CORREGIDO DE RAÍZ (v4): las dos versiones anteriores calculaban a mano,
 * en píxeles, dónde debía aparecer la descripción como un elemento
 * flotante aparte (primero con `onGloballyPositioned` sobre el ícono,
 * después sobre la fila) — y las dos veces terminó mal ubicada, corrida
 * hacia la fila de arriba, de forma consistente y reproducible. En vez de
 * seguir con cálculos manuales de coordenadas entre distintos
 * composables (frágil, difícil de verificar sin un dispositivo real), la
 * descripción ahora se dibuja DENTRO de esta misma fila cuando
 * `isInfoExpanded` es true — empujando las filas de abajo, como un
 * acordeón — así es estructuralmente imposible que aparezca en el lugar
 * equivocado: no hay ninguna coordenada que calcular, la descripción
 * directamente ES parte del layout de esta fila.
 */
@Composable
private fun RestoreOptionRow(
    label: String,
    description: String,
    // Recurso drawable (uno de los `ic_*.xml` propios de la app), no un
    // `ImageVector` de Material Icons: ya nos quemamos una vez asumiendo
    // que "material-icons-core" trae el catálogo COMPLETO en estilo
    // Filled — no es así, solo trae un subconjunto reducido incluso ahí
    // (`Icons.Filled.RestartAlt` no existía y rompió el build de GitHub
    // Actions). Un ícono SVG propio del proyecto es 100% seguro: no
    // depende de qué versión de qué catálogo externo esté disponible. Sin
    // `@DrawableRes` a propósito — es solo una anotación de lint, no hace
    // falta para compilar, y evita agregar un import de
    // `androidx.annotation` que tampoco puedo verificar desde acá.
    icon: Int,
    showDivider: Boolean = true,
    isInfoExpanded: Boolean,
    onToggleInfo: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onSelect()
                    onDismiss()
                }
                .padding(vertical = 6.dp)
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onToggleInfo,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Qué hace \"$label\"",
                    modifier = Modifier.size(14.dp),
                    tint = if (isInfoExpanded) {
                        BrandPurpleLight
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        // La descripción, cuando está expandida: una franja angosta y
        // horizontal — no una tarjeta aparte — con su propio fondo sutil
        // para distinguirla del resto de la fila, pegada justo debajo del
        // nombre + ícono que la abrió. `AnimatedVisibility` la hace
        // aparecer/desaparecer con una transición suave en vez de un salto
        // brusco, sin agregar espacio muerto cuando está colapsada (altura
        // 0 real, no solo invisible).
        AnimatedVisibility(visible = isInfoExpanded) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }
    }
    if (showDivider) {
        HorizontalDivider()
    }
}

/**
 * Fila de opción para la mini-ventana "Reordenar" (Solo/Todos) — mismo
 * patrón visual que [RestoreOptionRow] (fila plana de ancho completo +
 * divisor fino), pero sin el ícono "i" de info/descripción expandible,
 * que ahí no aplica: "Solo" y "Todos" son autoexplicativos por su propio
 * nombre + ícono, no necesitan una descripción aparte.
 */
@Composable
private fun ReorderScopeOptionRow(
    label: String,
    icon: Int,
    showDivider: Boolean = true,
    // Resalta la fila cuando representa un toggle "armado" (ver
    // "Restablecer" en la mini-ventana Reordenar) — mismo criterio de
    // acento activo/inactivo (BrandPurpleLight) que el resto de la app,
    // para que quede claro que el modo sigue prendido hasta elegir
    // Solo/Todos o tocarla de nuevo.
    isActive: Boolean = false,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isActive) BrandPurpleLight else Color.White.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) BrandPurpleLight else Color.White
        )
    }
    if (showDivider) {
        HorizontalDivider()
    }
}


/**
 * Slider reutilizable con etiqueta y valor formateado arriba. `valueLabel`
 * permite mostrar el valor con un formato distinto al decimal por defecto
 * (p. ej. como mm:ss para duraciones, en los paneles de Audio y Export).
 *
 * Componente ÚNICO y compartido por TODAS las opciones de efectos de la
 * app (Básicos, Contorno, Resplandor, Presets, Sombra, Sombra relleno,
 * etc. en cada capa, más Recolor/3D/Cámara/Audio/Export) — más de 100
 * instancias en total. Por eso las 3 formas de ajuste que se agregaron acá
 * (arrastrar el carril, tocar (-)/(+) de precisión, o escribir el número a
 * mano) aparecen automáticamente en cada slide de cada opción de efecto
 * existente sin tener que tocar cada panel uno por uno.
 */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    valueLabel: (Float) -> String = { "%.2f".format(it) },
    // Paso que avanzan los botones de precisión (-)/(+) en cada extremo
    // del carril con cada toque. null = automático (1% del rango total),
    // que da un paso razonable tanto para rangos chicos (0f..1f -> 0.01)
    // como grandes (-180f..180f -> 3.6) sin que cada uno de los +100
    // llamados existentes tenga que declarar el suyo a mano.
    step: Float? = null,
    // A PEDIDO DEL USUARIO — BUG REAL corregido acá: el campo de edición
    // manual (tocar el número para escribirlo a mano) comparaba lo
    // tipeado contra `range` en la unidad CRUDA interna (p. ej. 0f..1f
    // para casi todo lo que se ve como "%"), pero `valueLabel` le muestra
    // al usuario ese mismo valor YA multiplicado por 100 ("83%"). Como el
    // clamp-en-vivo (ver `onValueChange` del campo, más abajo) se fijaba
    // en el 0..1 crudo, apenas el usuario tipeaba un segundo dígito
    // (p. ej. "8" después del "3" de "83") el número ya superaba 1 y el
    // campo se autocorregía a "1" de una — daba la sensación de que "solo
    // dejaba poner el 1". `displayScale` es el factor que convierte la
    // unidad cruda a la unidad que el usuario ve y tipea (100 para los
    // controles en "%", 1 por defecto para los que van en su propia
    // unidad como grados). El campo de edición, su límite mínimo/máximo y
    // el clamp-en-vivo ahora trabajan TODOS en esa escala visible — nunca
    // en la cruda — para que el máximo que se puede escribir a mano sea
    // siempre el mismo que ve el usuario en la etiqueta de arriba.
    displayScale: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    val resolvedStep = remember(step, range) {
        step ?: (((range.endInclusive - range.start) / 100f).let { if (it > 0f) it else 0.01f })
    }
    // Rango en la escala VISIBLE (la que el usuario tipea a mano) —
    // p. ej. 0f..1f interno con displayScale=100f pasa a ser 0f..100f
    // acá, que es lo que corresponde comparar contra lo que el usuario
    // escribe.
    val displayRange = remember(range, displayScale) {
        (range.start * displayScale)..(range.endInclusive * displayScale)
    }
    var isEditingValue by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf("") }
    val editFocusRequester = remember { FocusRequester() }
    // BUG REAL corregido acá — el que se venía arrastrando hace rato: sin
    // este flag, `onFocusChanged` (más abajo) confundía el aviso INICIAL
    // de "todavía no tengo foco" (que Compose dispara solo con que el
    // campo aparezca en pantalla, antes de que `requestFocus()` llegue a
    // correr) con un "perdí el foco real" — y cerraba el campo al toque,
    // en el mismo frame en que se abría. Con este flag solo se considera
    // "perdió el foco" (y recién ahí se aplica el valor y se cierra) la
    // transición DESPUÉS de haber tenido el foco de verdad; el primer
    // aviso (sin foco todavía) se ignora.
    var hasGainedFocus by remember { mutableStateOf(false) }

    fun commitEditingValue() {
        // Lo tipeado está en escala VISIBLE (displayScale) — hay que
        // volver a la escala cruda interna (dividiendo por displayScale)
        // antes de recortarlo a `range` y mandarlo a `onValueChange`.
        editingText.trim().replace(",", ".").toFloatOrNull()?.let { parsedDisplay ->
            onValueChange((parsedDisplay / displayScale).coerceIn(range))
        }
        isEditingValue = false
        hasGainedFocus = false
    }

    // BUG REAL corregido acá: esta Column no tenía `fillMaxWidth()`, así
    // que en cualquier panel donde el ancho no viniera ya forzado por un
    // padre con weight/fill (como pasa en el panel "Recolor", cuyo
    // contenedor usa CenterHorizontally sin fillMaxWidth explícito) el
    // Slider caía a su ancho mínimo de "wrap content" — se veía cortado
    // a la mitad o menos, en vez de ocupar todo el panel como antes.
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        // --- Título + valor. Tocar acá (tercera forma de ajustar, además
        // de arrastrar el carril y los botones (-)/(+)) abre un campo para
        // escribir el número exacto y aplicarlo de una — en vez de tener
        // que calibrarlo a mano arrastrando.
        if (isEditingValue) {
            // A PEDIDO DEL USUARIO — antes acá solo se avisaba CON TEXTO
            // ("Se ajustará a X") que lo escrito iba a recortarse recién al
            // tocar "aplicar", pero el recuadro seguía mostrando el número
            // crudo (p. ej. "1007" en un control de -180..180) hasta ese
            // momento. Ahora el recuadro se autocorrige en el momento: en
            // cuanto lo tipeado supera el máximo (o baja del mínimo) de ESE
            // control puntual, el propio campo pasa a mostrar el valor
            // límite numérico de una — no hace falta tocar "aplicar" para
            // verlo. `willClamp` ya no puede dispararse en este punto
            // (editingText siempre queda dentro de rango apenas se escribe
            // algo fuera de límite), así que el borde del campo se deja
            // siempre en el violeta normal.
            val fieldBorderColor = BrandPurpleLight
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("$label: ", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = editingText,
                    // BUG REAL corregido acá — el que reportó el usuario
                    // ahora ("054364" en vez de "0.54364"): el campo
                    // aceptaba CUALQUIER texto sin filtrar (`{ editingText
                    // = it }` a secas) y encima usaba KeyboardType.Number,
                    // que en Android es "solo dígitos" — ni siquiera
                    // declara soporte de punto decimal a nivel de
                    // InputType, así que por más que el teclado dibuje una
                    // tecla ".", esa pulsación se descarta silenciosamente
                    // antes de llegar al campo. El usuario apretaba
                    // "0", ".", "5", "4"... y el punto simplemente
                    // desaparecía sin ningún aviso, dejando "054364" — un
                    // número sin sentido que a fin de cuentas se hubiera
                    // aplicado igual (recortado al rango) sin que nadie
                    // notara el error hasta ver el resultado final.
                    // Corregido en dos frentes, no solo el síntoma:
                    // 1) KeyboardType.Decimal en vez de Number — SÍ
                    //    declara soporte de separador decimal.
                    // 2) Un filtro propio acá, que no confía en que el
                    //    teclado del sistema haga lo correcto: solo deja
                    //    pasar dígitos, un signo "-" al principio y un
                    //    único separador decimal (coma o punto, ambos se
                    //    normalizan a punto) — cualquier otra tecla queda
                    //    afuera al instante, sin esperar a que el usuario
                    //    intente aplicar un valor roto.
                    onValueChange = { newText ->
                        val sanitized = buildString {
                            var seenDecimalSeparator = false
                            newText.forEachIndexed { index, c ->
                                when {
                                    c.isDigit() -> append(c)
                                    c == '-' && index == 0 -> append(c)
                                    (c == '.' || c == ',') && !seenDecimalSeparator -> {
                                        append('.')
                                        seenDecimalSeparator = true
                                    }
                                }
                            }
                        }
                        // A PEDIDO DEL USUARIO — acá es donde se corrige lo
                        // que pidió: apenas lo tipeado se pasa del máximo
                        // (o del mínimo) numérico de ESTE control puntual,
                        // el recuadro deja de mostrar el número crudo y
                        // pasa a mostrar directamente el valor límite, ya
                        // recortado. La comparación es contra
                        // `displayRange` (la escala VISIBLE, la misma que
                        // el usuario ve en la etiqueta de arriba — 0-100
                        // para los controles en "%", el rango tal cual
                        // para el resto) — NO contra `range` crudo. Ese
                        // era justo el bug reportado: en un control
                        // 0f..1f interno mostrado como "%", escribir "83"
                        // se comparaba contra el 1 crudo y se autocorregía
                        // al toque a "1", sin dejar poner un segundo
                        // dígito. Los valores DENTRO de rango se dejan tal
                        // cual los tipeó el usuario, sin tocarlos — así
                        // puede seguir editando/borrando dígitos con
                        // libertad mientras no se pase del límite visible.
                        val parsedDisplay = sanitized.toFloatOrNull()
                        editingText = if (parsedDisplay != null &&
                            (parsedDisplay < displayRange.start || parsedDisplay > displayRange.endInclusive)
                        ) {
                            formatEditableNumber(parsedDisplay.coerceIn(displayRange))
                        } else {
                            sanitized
                        }
                    },
                    singleLine = true,
                    // BUG REAL corregido acá (turno anterior) — el que
                    // reportó el usuario ("no se ven los números nada"):
                    // el campo no fijaba ningún color propio, así que
                    // dependía 100% de los valores por defecto de
                    // Material3 para OutlinedTextField — y en este tema
                    // oscuro a medida (OlyzeTheme en Theme.kt, con su
                    // propio darkColorScheme) esos valores por defecto no
                    // estaban dando contraste real contra el fondo: el
                    // texto quedaba ahí, pero prácticamente invisible.
                    // Ahora el color del texto, el cursor y los bordes se
                    // fijan a mano, sin depender de ninguna resolución
                    // automática de tema.
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = BrandPurpleLight,
                        // A PEDIDO DEL USUARIO: antes el borde siempre era
                        // del mismo violeta, aplicaras lo que aplicaras —
                        // escribir un número fuera del rango del slider
                        // (ej. "999" en un control de 0-100) no se veía
                        // distinto de escribir un valor válido, aunque
                        // `commitEditingValue()` lo fuera a recortar en
                        // silencio al aplicar. Ahora el borde se pone
                        // ámbar mientras el número escrito se vaya a
                        // ajustar — antes incluso de tocar "aplicar" — así
                        // nunca es una sorpresa lo que terminó guardado.
                        focusedBorderColor = fieldBorderColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    // Decimal (no Number): Number en Android es
                    // "solo dígitos enteros", sin soporte de separador
                    // decimal a nivel de InputType — con eso, cualquier
                    // slider que necesite decimales (Difuminar, Contorno,
                    // Saturación, Zoom, etc. — la enorme mayoría) era
                    // imposible de escribir bien a mano. Decimal SÍ
                    // declara ese soporte.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitEditingValue() }),
                    modifier = Modifier
                        // BUG REAL corregido acá — el que el usuario venía
                        // reportando como "los números se ven a la mitad":
                        // este campo tenía un `.height(44.dp)` FIJO y
                        // rígido. OutlinedTextField de Material3 necesita
                        // cierto alto mínimo real (su padding vertical
                        // interno + el alto de línea del texto) para
                        // dibujar el contenido completo — al forzarlo a un
                        // alto fijo más chico de lo que necesita, Compose
                        // no "achica" el texto para que quepa: RECORTA lo
                        // que sobra, literal, en píxeles. Por eso se veían
                        // los números cortados por arriba, siempre en la
                        // misma zona — no era un tema de color ni de
                        // fuente, era directamente contenido recortado por
                        // un contenedor demasiado bajo.
                        //
                        // `.heightIn(min = ...)` en vez de `.height(...)`:
                        // le da un piso, pero deja que el campo crezca
                        // hasta lo que en verdad necesite — nunca vuelve a
                        // poder ser más chico que el contenido que tiene
                        // que mostrar.
                        .heightIn(min = 52.dp)
                        .width(100.dp)
                        .focusRequester(editFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                hasGainedFocus = true
                            } else if (hasGainedFocus) {
                                // Recién acá es una pérdida de foco REAL
                                // (tocó afuera, cambió de panel, etc.) —
                                // no el aviso inicial de "todavía sin
                                // foco" que llega antes de requestFocus().
                                commitEditingValue()
                            }
                        }
                )
                // --- Botón "±" para rangos con valores negativos (Pan,
                // Rotación, Tilt, Dolly zoom, Exposición, Temperatura,
                // Tinte, Distorsión de lente, etc.): el teclado numérico
                // del sistema (KeyboardType.Decimal, ver arriba) NO trae
                // tecla de signo menos en la mayoría de los dispositivos
                // Android — sin esto, sería imposible escribir un valor
                // negativo a mano en esos sliders. Invierte el signo del
                // texto ya escrito, sin importar en qué parte del campo
                // esté el cursor.
                if (displayRange.start < 0f) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "±",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                editingText = if (editingText.startsWith("-")) {
                                    editingText.removePrefix("-")
                                } else if (editingText.isNotEmpty()) {
                                    "-$editingText"
                                } else {
                                    editingText
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                // A PEDIDO DEL USUARIO — este par de botones era el que
                // faltaba: antes solo había "aplicar", sin ninguna forma
                // de arrepentirse una vez abierto el campo salvo tocar
                // afuera (que además de por sí YA aplica, por el
                // `onFocusChanged` de arriba) — no había un "no, dejá todo
                // como estaba" explícito. "Cancelar" descarta `editingText`
                // sin tocar el valor real de la capa para nada (no llama
                // a `onValueChange` ni a `commitEditingValue`, solo cierra
                // el campo).
                // A PEDIDO DEL USUARIO — "cancelar" ahora lleva su × igual
                // que "aplicar" lleva su ✓, para que ambas acciones se
                // distingan de un vistazo por el símbolo y no solo por el
                // color/texto.
                Text(
                    "cancelar ✕",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.clickable {
                        isEditingValue = false
                        hasGainedFocus = false
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "aplicar ✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandPurpleLight,
                    modifier = Modifier.clickable { commitEditingValue() }
                )
            }
            // A PEDIDO DEL USUARIO — el aviso de texto que antes vivía acá
            // ("Se ajustará a X (límite del control)") queda de más: ya no
            // hace falta avisar DESPUÉS de tipear que el valor se va a
            // recortar, porque ahora (ver `onValueChange` del campo, más
            // arriba) el propio recuadro se autocorrige al límite numérico
            // de ESTE control en el momento en que se escribe algo fuera
            // de rango — el usuario ya lo ve ahí, sin un mensaje aparte.
            LaunchedEffect(Unit) { editFocusRequester.requestFocus() }
        } else {
            Text(
                "$label: ${valueLabel(value)}",
                style = MaterialTheme.typography.labelSmall,
                // BUG REAL corregido acá: tenía `clickable` ANTES de
                // `padding` invertido — `.padding(...).clickable(...)`
                // deja el padding POR FUERA del área clickeable en
                // Compose (el orden de modificadores importa: el que
                // está a la izquierda envuelve al de la derecha). Con ese
                // orden, el padding solo agregaba espacio visual — no
                // tocable — alrededor de un texto angosto tipo "Nitidez:
                // 0%", así que casi ningún toque real (que cae al lado
                // del texto, no pixel-perfecto sobre él) llegaba a
                // disparar el `clickable`. `clickable` PRIMERO en la
                // cadena hace que envuelva el padding + el texto, o sea
                // que el área tocable real incluye ese margen.
                modifier = Modifier
                    .clickable(enabled = enabled) {
                        // En escala visible (displayScale), no en la cruda —
                        // así lo que aparece al abrir el campo es el mismo
                        // número que el usuario ya está viendo en la etiqueta
                        // (p. ej. "83", no "0.83").
                        editingText = formatEditableNumber(value * displayScale)
                        isEditingValue = true
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SliderStepButton(
                iconRes = R.drawable.ic_remove,
                contentDescription = "Disminuir $label",
                enabled = enabled && value > range.start,
                onClick = { onValueChange((value - resolvedStep).coerceIn(range)) }
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            SliderStepButton(
                iconRes = R.drawable.ic_add,
                contentDescription = "Aumentar $label",
                enabled = enabled && value < range.endInclusive,
                onClick = { onValueChange((value + resolvedStep).coerceIn(range)) }
            )
        }
    }
}

/**
 * Botón chico (-)/(+) en cada punta del carril de [LabeledSlider], para
 * quien prefiera precisión tocando en vez de arrastrar el slide. Círculo
 * translúcido a 28dp (por encima del mínimo de toque cómodo) — no usa
 * [IconButton] de Material3 para no heredar su padding/tamaño por defecto,
 * que en una fila junto al Slider quedaba desbalanceado.
 *
 * Con AUTO-REPETICIÓN al mantener apretado — mismo mecanismo que
 * [GridStepperButton]: el primer toque aplica un paso al instante, y si
 * el dedo se queda apretando más de ~380ms arranca a repetir a velocidad
 * tope constante (45ms entre pasos, sin rampa) hasta soltar. Antes este
 * botón solo usaba `clickable` normal, que dispara un solo paso por toque
 * sin importar cuánto tiempo se mantuviera apretado — mantenerlo NO
 * avanzaba corrido, había que tocar uno por uno.
 */
@Composable
private fun SliderStepButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    // Referencia siempre-actualizada a onClick — el gesto de auto-repeat
    // vive en un pointerInput de vida larga (keyed por `enabled`, no por
    // `onClick`, para que cada paso que dispara onClick() y cambia el
    // valor no reinicie el gesto a mitad de un apretón sostenido).
    val latestOnClick = rememberUpdatedState(onClick)
    var isPressed by remember { mutableStateOf(false) }
    val backgroundAlpha = when {
        !enabled -> 0.03f
        isPressed -> 0.16f
        else -> 0.08f
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha))
            .then(
                if (enabled) {
                    Modifier.pointerInput(enabled) {
                        awaitEachGesture {
                            awaitFirstDown()
                            isPressed = true
                            try {
                                latestOnClick.value()
                                // BUG REAL corregido acá: antes esta pausa
                                // arrancaba en 380ms y se achicaba de a
                                // poco (x0.72 cada vuelta) hasta un piso de
                                // 45ms — son ~7 repeticiones y más de 1s
                                // hasta llegar a velocidad tope, así que
                                // sostener el botón se sentía como si
                                // "fuera tomando impulso" (un lag/rampa),
                                // en vez de repetir liviano de una. Ahora
                                // la única pausa larga es la PRIMERA (para
                                // que un toque simple y rápido dispare UN
                                // solo paso y no una ráfaga) — apenas esa
                                // pausa vence y arranca a repetir, salta
                                // directo al piso de velocidad y se queda
                                // ahí constante, sin rampa intermedia.
                                var waitMs = 380L
                                while (true) {
                                    val released = withTimeoutOrNull(waitMs) { waitForUpOrCancellation() }
                                    if (released != null) break
                                    latestOnClick.value()
                                    waitMs = 45L
                                }
                            } finally {
                                isPressed = false
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 0.9f else 0.35f),
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Calcula las 4 esquinas (en píxeles de pantalla, mismo Box que el
 * preview) del rectángulo COMPLETO que ocupa una capa — el mismo que usa
 * [hitTestLayerAt] para decidir si un toque la alcanza — sin importar
 * cuánto de ese rectángulo se vea realmente opaco. Es la geometría
 * inversa de esa función: en vez de "toque de pantalla -> ¿adentro de la
 * capa?", es "esquinas de la capa en su espacio local -> pantalla".
 *
 * Para qué sirve: mostrar un marco/guía sobre la capa seleccionada, para
 * que quede claro hasta dónde llega el "lienzo" real de esa imagen (con
 * su margen transparente y todo) — no solo lo que se ve pintado. Así el
 * usuario entiende de un vistazo por qué un toque cae "dentro" o "fuera"
 * de una capa, en vez de que sea una caja invisible.
 */
/**
 * Dibuja un par de trazos en forma de "cabeza de flecha" en [tip],
 * apuntando hacia afuera en la dirección [outwardDirRad] (radianes,
 * mismo sistema que atan2: 0 = derecha, PI/2 = abajo en pantalla).
 * Pieza compartida de [drawDoubleArrowGlyph] y [drawRotateGlyph] — así
 * las 3 manijas de flecha doble (reescalar, estirar ancho, estirar
 * alto) y la de girar usan exactamente el mismo trazo, sin duplicar la
 * trigonometría en cada una.
 */
private fun DrawScope.drawArrowheadAt(
    tip: Offset,
    outwardDirRad: Double,
    size: Float,
    color: Color,
    strokeWidthPx: Float
) {
    val spread = Math.toRadians(28.0)
    val backDir1 = outwardDirRad + Math.PI - spread
    val backDir2 = outwardDirRad + Math.PI + spread
    drawLine(
        color, tip,
        Offset(tip.x + size * cos(backDir1).toFloat(), tip.y + size * sin(backDir1).toFloat()),
        strokeWidthPx, cap = StrokeCap.Round
    )
    drawLine(
        color, tip,
        Offset(tip.x + size * cos(backDir2).toFloat(), tip.y + size * sin(backDir2).toFloat()),
        strokeWidthPx, cap = StrokeCap.Round
    )
}

/**
 * Ícono de flecha doble (dos puntas, una a cada lado del centro) sobre
 * el eje [angleRad] — 45° para "reescalar" (esquina inf. derecha), 0°
 * (horizontal) para "estirar ancho" (lateral derecha, medio) y 90°
 * (vertical) para "estirar alto" (inferior, medio). Delgado (grosor
 * mediano, no grueso) a propósito, para que se vea premium/profesional
 * y no como un ícono de sistema genérico.
 */
private fun DrawScope.drawDoubleArrowGlyph(
    center: Offset,
    r: Float,
    angleRad: Double,
    color: Color,
    strokeWidthPx: Float
) {
    val len = r * 0.55f
    val dx = (len * cos(angleRad)).toFloat()
    val dy = (len * sin(angleRad)).toFloat()
    val p1 = Offset(center.x - dx, center.y - dy)
    val p2 = Offset(center.x + dx, center.y + dy)
    drawLine(color, p1, p2, strokeWidthPx, cap = StrokeCap.Round)
    val headLen = r * 0.34f
    drawArrowheadAt(p1, angleRad + Math.PI, headLen, color, strokeWidthPx)
    drawArrowheadAt(p2, angleRad, headLen, color, strokeWidthPx)
}

/**
 * Ícono de girar (esquina inf. izquierda): un rectángulo centrado (el
 * "dispositivo"/la capa) con dos flechas circulares, una a cada lado —
 * mismo lenguaje visual que el ícono estándar de "rotar pantalla"
 * (ver ícono de referencia). Puramente visual: la manija sigue
 * funcionando igual, arrastrarla rota la capa alrededor de su centro.
 */
private fun DrawScope.drawRotateGlyph(center: Offset, r: Float, color: Color, strokeWidthPx: Float) {
    // REDISEÑADO DE NUEVO — el intento anterior (arcRadius=0.58×r, huecos
    // de 30°) seguía viéndose como un anillo casi cerrado pegado al aro:
    // con solo 30° de hueco a cada lado, las dos flechas se leían como
    // una sola pista circular gruesa (casi el mismo grosor que el aro
    // del badge), no como dos flechas separadas — de ahí el reclamo de
    // "se ve horrible/como anillo doble", más allá de que ya no se
    // salieran del botón. Ahora, calcado del ícono de referencia
    // (spinner de "rotar pantalla"): huecos de 50° a cada lado (arcos de
    // 130°, no 150°), radio más chico (0.50×r en vez de 0.58×r) y trazo
    // del arco/flechas MÁS FINO que el aro del badge (0.82× en vez de
    // 1.0×) para que se lea como línea delgada "premium", no como un
    // segundo marco grueso compitiendo con el primero. Con cabeza de
    // flecha y todo, el punto más lejano ahora queda en ~0.64×r — margen
    // amplio (~0.29×r) antes de tocar el aro (0.93×r a 1.07×r).
    val arcRadius = r * 0.50f
    val arcStroke = strokeWidthPx * 0.82f
    val arcRect = Rect(center.x - arcRadius, center.y - arcRadius, center.x + arcRadius, center.y + arcRadius)

    // Flecha circular derecha: arco de 130° (antes 150°) — deja un hueco
    // bien visible de 50° arriba y 50° abajo, para que se lea como dos
    // flechas curvas separadas y no como un anillo continuo.
    val rightStartDeg = -65f
    val rightSweepDeg = 130f
    drawArc(
        color = color,
        startAngle = rightStartDeg,
        sweepAngle = rightSweepDeg,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = arcStroke, cap = StrokeCap.Round)
    )
    val rightEndRad = Math.toRadians((rightStartDeg + rightSweepDeg).toDouble())
    val rightTip = Offset(center.x + arcRadius * cos(rightEndRad).toFloat(), center.y + arcRadius * sin(rightEndRad).toFloat())
    drawArrowheadAt(rightTip, rightEndRad + Math.PI / 2.0, arcRadius * 0.35f, color, arcStroke)

    // Flecha circular izquierda (misma forma, espejada, mismo hueco de
    // 50° respecto a la derecha en ambos extremos).
    val leftStartDeg = 115f
    val leftSweepDeg = 130f
    drawArc(
        color = color,
        startAngle = leftStartDeg,
        sweepAngle = leftSweepDeg,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = arcStroke, cap = StrokeCap.Round)
    )
    val leftEndRad = Math.toRadians((leftStartDeg + leftSweepDeg).toDouble())
    val leftTip = Offset(center.x + arcRadius * cos(leftEndRad).toFloat(), center.y + arcRadius * sin(leftEndRad).toFloat())
    drawArrowheadAt(leftTip, leftEndRad + Math.PI / 2.0, arcRadius * 0.35f, color, arcStroke)

    // Rectángulo centrado (el "dispositivo"/la capa que gira), con un
    // relleno diagonal a dos tonos (igual que la referencia: mitad clara
    // arriba-izquierda, mitad oscura abajo-derecha, simulando el brillo
    // de una pantalla) en vez de quedar vacío por dentro. Proporción
    // rectángulo:círculo igual que antes (0.29× / 0.47× del arcRadius),
    // solo que ahora arcRadius es más chico.
    val rectHalfW = arcRadius * 0.29f
    val rectHalfH = arcRadius * 0.47f
    val rectTopLeft = Offset(center.x - rectHalfW, center.y - rectHalfH)
    val rectSize = Size(rectHalfW * 2f, rectHalfH * 2f)
    val corner = CornerRadius(rectHalfW * 0.34f, rectHalfW * 0.34f)

    // El inset de la "pantalla" interior es en px de trazo, no en
    // fracción de r — con el rectángulo ya más chico (ver arriba) hay
    // que achicar también el inset, si no, no queda casi nada de
    // "pantalla" visible adentro del marco.
    val screenInset = strokeWidthPx * 0.4f
    val screenTopLeft = Offset(rectTopLeft.x + screenInset, rectTopLeft.y + screenInset)
    val screenSize = Size(rectSize.width - screenInset * 2f, rectSize.height - screenInset * 2f)
    val diagonalSplit = Path().apply {
        moveTo(screenTopLeft.x, screenTopLeft.y)
        lineTo(screenTopLeft.x + screenSize.width, screenTopLeft.y)
        lineTo(screenTopLeft.x, screenTopLeft.y + screenSize.height)
        close()
    }
    drawRoundRect(color = color.copy(alpha = 0.16f), topLeft = screenTopLeft, size = screenSize, cornerRadius = corner)
    drawPath(diagonalSplit, color = color.copy(alpha = 0.3f))

    drawRoundRect(
        color = color,
        topLeft = rectTopLeft,
        size = rectSize,
        cornerRadius = corner,
        style = Stroke(width = strokeWidthPx * 0.85f)
    )
}

/**
 * Ícono de restaurar posición (superior, medio — manija nueva): un arco
 * casi completo con una sola cabeza de flecha en la punta (look "premium"
 * de ícono de reset/restore, tipo Material "restore"/"refresh"), con un
 * pequeño rombo centrado adentro para diferenciarlo del ícono de girar
 * (mismo lenguaje visual que el resto de las manijas: arco + flecha).
 */
private fun DrawScope.drawRestoreGlyph(center: Offset, r: Float, color: Color, strokeWidthPx: Float) {
    val radius = r * 0.5f
    val startDeg = -70f
    val sweepDeg = 300f
    val rect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    drawArc(
        color = color,
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )
    val endRad = Math.toRadians((startDeg + sweepDeg).toDouble())
    val tip = Offset(center.x + radius * cos(endRad).toFloat(), center.y + radius * sin(endRad).toFloat())
    val tangentOutward = endRad + Math.PI / 2.0
    drawArrowheadAt(tip, tangentOutward, radius * 0.6f, color, strokeWidthPx)

    // Rombo pequeño en el centro (ver ícono de referencia: flecha circular
    // envolviendo un diamante) — refuerza que es "restaurar forma", no
    // "girar".
    val d = r * 0.24f
    val diamond = Path().apply {
        moveTo(center.x, center.y - d)
        lineTo(center.x + d, center.y)
        lineTo(center.x, center.y + d)
        lineTo(center.x - d, center.y)
        close()
    }
    drawPath(diamond, color, style = Stroke(width = strokeWidthPx * 0.85f))
}

/**
 * Ícono de "reordenar manijas" (lateral izquierda, medio — manija nueva,
 * fija, no participa del intercambio).
 *
 * TERCERA VERSIÓN: reemplazado a pedido por el SVG provisto por el
 * usuario (tres cuadrados redondeados en fila conectados por una línea
 * que sube desde el izquierdo, cruza por arriba con esquinas
 * redondeadas y baja hasta el derecho, terminando en una flecha/chevron
 * — viewBox original 0 0 24 24). Mismo criterio que [drawEditGlyph]:
 * coordenadas EXACTAS del SVG original (los mismos comandos C/V/H/L),
 * re-escaladas acá para caber en el badge de radio [r] a partir del
 * centro real del viewBox (12,12).
 */
private fun DrawScope.drawSwapGlyph(center: Offset, r: Float, color: Color, strokeWidthPx: Float) {
    val cx = 12f
    val cy = 12f
    // Diagonal del viewBox 24x24 original (24×√2 ≈ 33.94) escalada para
    // que el ícono ocupe ~1.5x el radio del badge — mismo peso visual
    // que el resto de las manijas (ver [drawEditGlyph], que usa el mismo
    // criterio de "diagonal del SVG original -> 1.55x r").
    val scale = (1.5f * r) / 33.94f

    fun tf(x: Float, y: Float): Offset = Offset(
        center.x + (x - cx) * scale,
        center.y + (y - cy) * scale
    )

    val sw = strokeWidthPx * 0.62f

    // Los 3 cuadrados redondeados (izquierda, medio, derecha) del SVG:
    // el original los traza con beziers tipo "squircle", acá se
    // aproximan con drawRoundRect — visualmente equivalente a este
    // tamaño real (badge de 22dp).
    fun square(x0: Float, y0: Float, x1: Float, y1: Float) {
        val topLeft = tf(x0, y0)
        val bottomRight = tf(x1, y1)
        val size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = size,
            cornerRadius = CornerRadius(size.minDimension * 0.42f, size.minDimension * 0.42f),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
    }
    square(2f, 15f, 7f, 20f)
    square(9.5f, 15f, 14.5f, 20f)
    square(17f, 15f, 22f, 20f)

    // La línea que conecta: sube desde el cuadrado izquierdo (4.5,15),
    // cruza por arriba con esquinas redondeadas (las mismas curvas C del
    // SVG original) y baja hasta (19.5,12), cerca del cuadrado derecho.
    val link = Path().apply {
        var p = tf(4.5f, 15f); moveTo(p.x, p.y)
        p = tf(4.5f, 9f); lineTo(p.x, p.y)
        var c1 = tf(4.5f, 6.64298f); var c2 = tf(4.5f, 5.46447f); p = tf(5.23223f, 4.73223f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
        c1 = tf(5.96447f, 4f); c2 = tf(7.14298f, 4f); p = tf(9.5f, 4f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
        p = tf(14.5f, 4f); lineTo(p.x, p.y)
        c1 = tf(16.857f, 4f); c2 = tf(18.0355f, 4f); p = tf(18.7678f, 4.73223f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
        c1 = tf(19.5f, 5.46447f); c2 = tf(19.5f, 6.64298f); p = tf(19.5f, 9f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
        p = tf(19.5f, 12f); lineTo(p.x, p.y)
    }
    drawPath(link, color, style = Stroke(width = sw, cap = StrokeCap.Round))

    // Flecha (chevron) en el extremo derecho de la línea: dos trazos
    // cortos desde (19.5,12) hacia (21.5,10) y (17.5,10), igual que las
    // dos subpaths finales del SVG original.
    val apex = tf(19.5f, 12f)
    drawLine(color, apex, tf(21.5f, 10f), sw, cap = StrokeCap.Round)
    drawLine(color, apex, tf(17.5f, 10f), sw, cap = StrokeCap.Round)
}

/** Ícono de check (confirmar reordenamiento): reemplaza al ícono de
 * "reordenar" en la misma manija mientras el modo reordenar está activo. */
private fun DrawScope.drawCheckGlyph(center: Offset, r: Float, color: Color, strokeWidthPx: Float) {
    val p1 = Offset(center.x - r * 0.42f, center.y - r * 0.02f)
    val p2 = Offset(center.x - r * 0.08f, center.y + r * 0.36f)
    val p3 = Offset(center.x + r * 0.46f, center.y - r * 0.34f)
    drawLine(color, p1, p2, strokeWidthPx, cap = StrokeCap.Round)
    drawLine(color, p2, p3, strokeWidthPx, cap = StrokeCap.Round)
}

/** Despacha al glyph correspondiente según la función asignada a una manija — usado para dibujar las 7 manijas intercambiables con el orden vigente (por defecto, por-capa, o el borrador en vivo mientras se están reordenando).
 *
 * [isEditingThisLayer]: true cuando la manija con función EDIT pertenece a
 * la capa que está ACTUALMENTE en modo edición aislado ([editModeLayerId]
 * en EditorScreen == el id de esta capa) — en ese caso se dibuja un check
 * en vez del lápiz, para que la misma manija sirva para "confirmar y
 * salir" (reemplaza al chip "Editando imagen X" que se sacó de la
 * cabecera). Para cualquier otro rol, este parámetro no tiene efecto.
 *
 * [isCancelIntent]: true cuando la manija con función DELETE está
 * funcionando como "cancelar" en vez de "eliminar capa" — mientras el
 * modo reordenar o el modo edición aislado están activos (ver los dos
 * `showCancelReorderConfirm`/`showCancelEditModeConfirm` en
 * EditorScreen). A PEDIDO EXPLÍCITO se dibuja un ícono distinto en cada
 * caso: la papelera de siempre ([drawDeleteGlyph]) solo cuando el toque
 * de verdad va a borrar la capa, y un × dentro de un círculo
 * ([drawCancelGlyph]) cuando el toque va a cancelar — así el usuario ve
 * de un vistazo cuál de las dos funciones tiene esa manija en cada
 * momento, sin que la papelera aparezca nunca en un contexto donde no
 * borra nada. Para cualquier otro rol, este parámetro no tiene efecto. */
private fun DrawScope.drawGlyphForRole(role: LayerHandleRole, center: Offset, r: Float, color: Color, strokeWidthPx: Float, isEditingThisLayer: Boolean = false, isCancelIntent: Boolean = false) {
    when (role) {
        LayerHandleRole.EDIT -> if (isEditingThisLayer) drawCheckGlyph(center, r, color, strokeWidthPx) else drawEditGlyph(center, r, color, strokeWidthPx)
        LayerHandleRole.DELETE -> if (isCancelIntent) drawCancelGlyph(center, r, color, strokeWidthPx) else drawDeleteGlyph(center, r, color, strokeWidthPx)
        LayerHandleRole.ROTATE -> drawRotateGlyph(center, r, color, strokeWidthPx)
        LayerHandleRole.RESIZE_UNIFORM -> drawDoubleArrowGlyph(center, r, Math.toRadians(45.0), color, strokeWidthPx)
        LayerHandleRole.RESIZE_WIDTH -> drawDoubleArrowGlyph(center, r, 0.0, color, strokeWidthPx)
        LayerHandleRole.RESIZE_HEIGHT -> drawDoubleArrowGlyph(center, r, Math.PI / 2.0, color, strokeWidthPx)
        LayerHandleRole.RESTORE -> drawRestoreGlyph(center, r, color, strokeWidthPx)
        // BUG DE BUILD ENCONTRADO Y ARREGLADO: al agregar el rol REORDER (8va
        // manija) el compilador exige que este `when` sea exhaustivo, pero
        // nunca se agregó esta rama — eso rompía `gradle build`
        // (":app:compileDebugKotlin FAILED", "'when' expression must be
        // exhaustive. Add the 'REORDER' branch or an 'else' branch").
        // REORDER nunca llega hasta acá en la práctica: los dos call sites de
        // esta función (Canvas de dibujo, más arriba) lo interceptan antes con
        // `if (role == LayerHandleRole.REORDER)` y dibujan su propio ícono
        // check/swap aparte — por eso esta rama queda vacía a propósito, es
        // solo para satisfacer la exhaustividad del compilador.
        LayerHandleRole.REORDER -> {}
    }
}

/**
 * Ícono de eliminar (papelera): SVG premium provisto por el usuario —
 * balde con tapa + asa arriba y 3 líneas verticales "cortadas" adentro.
 * Reemplaza a la "X" simple de antes. SOLO se dibuja cuando la manija
 * realmente va a borrar la capa (ver [isCancelIntent] en
 * [drawGlyphForRole]) — mientras esa misma manija funciona como
 * "cancelar" se dibuja [drawCancelGlyph] en su lugar, nunca la papelera.
 *
 * Reconstruido con las mismas proporciones del .svg original (viewBox 0
 * 0 268.476 268.476: balde y=71.6..268.5, tapa y=35.8..62.7, asa
 * y=4..35.8), escalado para caber en el badge de radio [r]. Las 3
 * líneas verticales se dibujan en el blanco de fondo del badge (mismo
 * blanco que pinta [drawBadge] detrás de todo glyph) en vez de recortar
 * el path del balde con una operación de diferencia — mismo resultado
 * visual ("líneas cortadas"), más simple y robusto de renderizar sin
 * depender de `Path.op`.
 */
private fun DrawScope.drawDeleteGlyph(center: Offset, r: Float, color: Color, strokeWidthPx: Float) {
    val scale = (1.7f * r) / 268.476f
    val cx = 134.238f
    val cy = 134.238f
    fun tf(x: Float, y: Float): Offset = Offset(center.x + (x - cx) * scale, center.y + (y - cy) * scale)

    // Cuerpo del balde: trapezoide con las dos esquinas inferiores
    // redondeadas (mismas coordenadas que el subpath 1 del SVG original).
    val body = Path().apply {
        var p = tf(63.119f, 71.594f); moveTo(p.x, p.y)
        p = tf(223.730f, 71.594f); lineTo(p.x, p.y)
        p = tf(205.356f, 250.254f); lineTo(p.x, p.y)
        var c1 = tf(205.356f, 259.7f); var c2 = tf(201.4f, 268.476f); p = tf(180.774f, 268.476f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
        p = tf(87.702f, 268.476f); lineTo(p.x, p.y)
        c1 = tf(67.1f, 268.476f); c2 = tf(63.119f, 259.7f); p = tf(63.119f, 250.254f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
        close()
    }
    drawPath(body, color)

    // 3 líneas verticales "cortadas" dentro del balde, extremos
    // redondeados — mismas posiciones x que los 3 subpaths de líneas del
    // SVG original (170.0/178.98, 125.3/134.2, 89.5/98.4, promediados acá
    // al centro de cada franja).
    val lineTopY = 96f
    val lineBottomY = 235f
    val lineStroke = strokeWidthPx * 0.95f
    for (xLine in listOf(89.5f, 134.2f, 179.0f)) {
        val top = tf(xLine, lineTopY)
        val bottom = tf(xLine, lineBottomY)
        drawLine(Color.White, top, bottom, lineStroke, cap = StrokeCap.Round)
    }

    // Tapa (rectángulo redondeado tipo píldora) sobre el balde.
    val lidTopLeft = tf(35.8f, 35.8f)
    val lidBottomRight = tf(232.7f, 62.7f)
    val lidHeight = lidBottomRight.y - lidTopLeft.y
    drawRoundRect(
        color = color,
        topLeft = lidTopLeft,
        size = Size(lidBottomRight.x - lidTopLeft.x, lidHeight),
        cornerRadius = CornerRadius(lidHeight / 2f, lidHeight / 2f)
    )

    // Asa (arco redondeado, solo contorno) sobre la tapa.
    val handleTopLeft = tf(89.5f, 4f)
    val handleBottomRight = tf(179.0f, 35.8f)
    val handleSize = Size(handleBottomRight.x - handleTopLeft.x, handleBottomRight.y - handleTopLeft.y)
    drawRoundRect(
        color = color,
        topLeft = handleTopLeft,
        size = handleSize,
        cornerRadius = CornerRadius(handleSize.height, handleSize.height),
        style = Stroke(width = strokeWidthPx * 0.85f)
    )
}

/**
 * Ícono de cancelar (× dentro de un círculo relleno): SVG premium
 * provisto por el usuario para la misma manija (rol DELETE) MIENTRAS
 * funciona como "cancelar" — modo reordenar activo, o modo edición
 * aislado activo (ver [isCancelIntent] en [drawGlyphForRole]) — en vez
 * de la papelera de [drawDeleteGlyph], que solo se dibuja cuando el
 * toque de verdad va a borrar la capa.
 *
 * Reconstruido como círculo relleno + × gruesa en el blanco de fondo del
 * badge (mismo blanco que [drawDeleteGlyph] usa para sus 3 líneas):
 * visualmente equivalente al relleno-con-hueco del .svg original
 * (viewBox 0 0 96 96, círculo sólido con una × recortada adentro), sin
 * depender de `Path.op`.
 */
private fun DrawScope.drawCancelGlyph(center: Offset, r: Float, color: Color, strokeWidthPx: Float) {
    drawCircle(color, r * 0.92f, center)
    val d = r * 0.36f
    val sw = strokeWidthPx * 1.15f
    drawLine(Color.White, Offset(center.x - d, center.y - d), Offset(center.x + d, center.y + d), sw, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(center.x - d, center.y + d), Offset(center.x + d, center.y - d), sw, cap = StrokeCap.Round)
}

/** Ícono de lápiz (esquina sup. izquierda): entra directo a modo edición
 * de la capa al tocarla — sin ventana ni menú intermedio (antes abría un
 * mini-menú con una sola opción, "Editar"; se sacó ese paso extra por
 * pedido explícito).
 *
 * TERCERA VERSIÓN: las dos anteriores eran aproximaciones a mano (líneas
 * sueltas, después un hexágono relleno) que no convencían. Esta usa las
 * coordenadas EXACTAS del SVG premium provisto por el usuario (lápiz con
 * viruta/papel en la punta + cuerpo + capuchón redondeado con curvas
 * Bézier) — no una reinterpretación: son los mismos 3 subpaths del
 * archivo .svg original (viewBox 0 0 386.375 386.375), re-escalados acá
 * para caber en el badge de radio [r]. Verificado visualmente con un
 * render de referencia contra estas mismas coordenadas antes de este
 * cambio (ver preview_icono_lapiz_svg.png). */
private fun DrawScope.drawEditGlyph(center: Offset, r: Float, color: Color, strokeWidthPx: Float) {
    // Centro real del bounding box de las 3 subpaths del SVG original
    // (incluye los puntos de control de las curvas del capuchón) y el
    // factor de escala para que la diagonal del ícono ocupe ~1.55x el
    // radio del badge — mismo peso visual que el resto de las manijas.
    val cx = 193.1875f
    val cy = 193.1875f
    val scale = (1.55f * r) / 546.35f

    fun tf(x: Float, y: Float): Offset = Offset(
        center.x + (x - cx) * scale,
        center.y + (y - cy) * scale
    )

    // Subpath 1 del SVG: la "viruta"/papelito en la punta del lápiz.
    val flag = Path().apply {
        var p = tf(21.05f, 286.875f); moveTo(p.x, p.y)
        p = tf(97.55f, 363.375f); lineTo(p.x, p.y)
        p = tf(95.65f, 367.175f); lineTo(p.x, p.y)
        p = tf(0.05f, 386.375f); lineTo(p.x, p.y)
        p = tf(19.15f, 290.775f); lineTo(p.x, p.y)
        close()
    }
    drawPath(flag, color = color)

    // Subpath 2 del SVG: el cuerpo del lápiz (paralelogramo).
    val body = Path().apply {
        var p = tf(34.65f, 272.775f); moveTo(p.x, p.y)
        p = tf(111.75f, 349.875f); lineTo(p.x, p.y)
        p = tf(328.15f, 133.476f); lineTo(p.x, p.y)
        p = tf(251.049f, 56.376f); lineTo(p.x, p.y)
        close()
    }
    drawPath(body, color = color)

    // Subpath 3 del SVG: el capuchón redondeado del otro extremo, con
    // las mismas 2 curvas Bézier cúbicas (mismos puntos de control) que
    // el archivo original.
    val cap = Path().apply {
        var p = tf(374.85f, 34.375f); moveTo(p.x, p.y)
        p = tf(351.85f, 11.475f); lineTo(p.x, p.y)
        var c1 = tf(336.55f, -3.825f)
        var c2 = tf(313.651f, -3.825f)
        var end = tf(298.35f, 11.475f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
        p = tf(265.85f, 43.975f); lineTo(p.x, p.y)
        p = tf(342.35f, 120.475f); lineTo(p.x, p.y)
        p = tf(374.85f, 87.975f); lineTo(p.x, p.y)
        c1 = tf(390.15f, 72.675f)
        c2 = tf(390.15f, 49.775f)
        end = tf(374.85f, 34.475f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
        close()
    }
    drawPath(cap, color = color)
}

/**
 * Distancia (al cuadrado, no hace falta la raíz para comparar) entre dos
 * colores ARGB en el espacio RGB — usada por [LayerColorEditPanel] para
 * encontrar, dentro de una paleta recién extraída, el swatch más
 * parecido a un color que se vio en pantalla antes del recargue. No
 * pesa el canal alpha: acá siempre se comparan colores ya opacos
 * (swatches de paleta).
 */
internal fun colorDistanceSquared(a: Int, b: Int): Int {
    val dr = ((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)
    val dg = ((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)
    val db = (a and 0xFF) - (b and 0xFF)
    return dr * dr + dg * dg + db * db
}

/**
 * Panel de "Color" del modo edición dedicado (ver EditorScreen: overlay
 * "Editando imagen"): a la izquierda, un cuadrito por cada color
 * distinto extraído de la imagen de la capa (ColorExtraction.
 * extractPalette); a la derecha, la rueda de color profesional
 * (reutiliza ColorWheelPicker, la misma de "Color de la capa" en
 * LayerDialogs.kt). Tocar un cuadrito lo selecciona (la rueda salta a
 * su matiz/saturación); arrastrar la rueda recolorea EN VIVO solo ese
 * color en el canvas (ColorExtraction.recolor, por cercanía de color,
 * no igualdad exacta de píxel — así agarra también el sombreado/
 * antialiasing de ese color, no un único tono puro).
 *
 * Tres versiones de la imagen a propósito (ver [EffectSources] y [ProgressiveRenderScheduler]):
 *  - una COPIA CHICA (~260 px) mientras se arrastra: cada frame se recalcula y sube a GL sin lag;
 *  - una copia a RESOLUCIÓN DE PANTALLA al soltar (150 ms de pausa): la vista previa queda tan nítida como se verá;
 *  - la imagen ORIGINAL, sin reducir, de la que sale lo que se guarda a disco (500 ms después del último cambio,
 *    para no escribir decenas de archivos por segundo). El guardado jamás se calcula desde una copia reducida.
 */
@Composable
private fun LayerColorEditPanel(
    layer: Layer,
    viewModel: EditorViewModel,
    // NOTA — hallazgo de esta misma revisión, NO tocado a propósito:
    // `extrude3DBridge` ya estaba sin ningún uso adentro de esta función
    // ANTES de que se tocara nada de "Distorsión" — no es consecuencia de
    // este cambio, así que queda fuera del alcance de esta limpieza; se
    // señala acá para que quede documentado y alguien lo decida a
    // propósito, no que se saque de paso sin que nadie lo pida.
    extrude3DBridge: Extrude3DGestureBridge,
    // La categoría de "Efectos" (antes local a EffectsPanel) sube acá
    // porque se elige desde el menú "Efecto" de la barra superior, no
    // desde una fila de chips local.
    effectsCategory: Int,
    onEffectsCategoryChange: (Int) -> Unit,
    // A PEDIDO DEL USUARIO — MULTI-VENTANA: reenviados tal cual hacia
    // [EffectsPanel] — ver el comentario grande junto a esos mismos tres
    // parámetros en la firma de EffectsPanel para el porqué completo de
    // por qué ya no se crean ahí adentro. Este panel (la ventana
    // compartida, que a esta altura ya no cubre ninguna categoría
    // accesible desde el menú "Efecto" — solo Fondo/Color/Presets) es uno
    // de los CINCO lugares que ahora usan este mismo `ctrl` para la misma
    // capa — los otros cuatro son [ContornoFloatingWindow]/
    // [ResplandorFloatingWindow]/[SombraFloatingWindow]/
    // [ReflejoFloatingWindow].
    ctrl: EffectsControlsState,
    sources: EffectSources<Bitmap>,
    modifier: Modifier = Modifier
) {
    // A PEDIDO DEL USUARIO — MULTI-VENTANA: este panel ya NO maneja
    // "Recolor" ni "Básico"/3D — esa lógica vive entera en
    // [RecolorFloatingWindow], [ColorBasicoFloatingWindow] y
    // [Basico3DFloatingWindow], las ventanas flotantes arrastrables (ver
    // EditorScreen, más arriba). El llamador (EditorScreen) solo dibuja
    // este panel cuando `effectsWindowOpen` es `true`, así que acá adentro
    // ya no hace falta ningún `if` de ruteo por pestaña: siempre es
    // "Efectos".
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // CORREGIDO en esta misma revisión: "Distorsión" TAMBIÉN se mudó
        // a su propia ventana (ver [DistortionFloatingWindow]) — este
        // comentario decía "por eso EffectsPanel también recibe
        // distortionBridge", pero [EffectsPanel] ya no lo recibe (ni
        // `context`, que solo existía para reenviarlo junto con
        // `distortionBridge` — ninguno de los dos se usaba para nada más
        // acá adentro). "Efectos" (esta ventana compartida) a esta altura
        // solo cubre Fondo/Color/Presets.
        EffectsPanel(
            layer = layer,
            viewModel = viewModel,
            selectedTopCategory = effectsCategory,
            onSelectedTopCategoryChange = onEffectsCategoryChange,
            ctrl = ctrl,
            sources = sources,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

/** Cuántos pasos de "deshacer por trazo" guarda como máximo [DistortionPanel] — de sobra para una sesión de edición real sin dejar crecer la memoria sin límite. */
private const val DISTORTION_MAX_UNDO_STEPS = 40

/**
 * Un botón del selector de herramientas de [DistortionPanel] — ícono +
 * etiqueta corta, resaltado si es la herramienta activa. Mismo look que
 * el resto de chips seleccionables de la app (borde + fondo tenue
 * cuando está activo).
 */
@Composable
private fun DistortionToolChip(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            lineHeight = 11.sp
        )
    }
}

/**
 * Última categoría de la pestaña "Efectos" del modo "Editando imagen"
 * (ver el mapa de índices de categorías de `EffectsPanel`, y [EditImageToolsHeader]
 * para las 3 pestañas de nivel superior): las 9 herramientas de
 * deformación de malla (Liquify) — ver `engine.distortion.*` para el
 * motor real detrás de cada una.
 *
 * A diferencia del resto de las categorías de "Efectos" (que ajustan la
 * imagen con sliders), acá la edición ocurre pintando directo sobre el
 * lienzo con el dedo — el gesto de UN dedo sobre el canvas principal se
 * redirige hacia acá vía [DistortionGestureBridge] mientras esta
 * categoría está activa (ver el bloque `distortionBridge.active` en el
 * `pointerInput` del canvas, en `EditorScreen`). Cada trazo:
 *  1. Al bajar el dedo ([DistortionGestureBridge.onStrokeStart]), guarda
 *     un snapshot de la malla actual en [undoStack] — "deshacer" restaura
 *     ese snapshot completo, así que cada pasada de dedo es UN paso de
 *     undo, no cada muestra individual.
 *  2. Mientras se arrastra ([onStrokeMove]), cada muestra llama a
 *     `DistortionField.applyStroke` sobre la malla en memoria y dispara
 *     una vista previa en vivo sobre un bitmap CHICO (liviano, sin lag),
 *     con throttle de ~60ms — mismo criterio que ya usa
 *     [Extrude3DPanel]/`LayerColorEditPanel` para no bloquear el hilo de
 *     gestos con trabajo pesado en cada evento.
 *  3. Al soltar el dedo ([onStrokeEnd]), se agenda un recálculo sobre el
 *     bitmap COMPLETO (alta resolución) con 500ms de debounce y se
 *     persiste como archivo nuevo — mismo mecanismo de guardado
 *     no-destructivo que el resto de "Efectos"/Recolor/3D
 *     (`EditorViewModel.previewLayerRecolor`/`commitLayerRecolor`,
 *     genéricos, reusados tal cual).
 *
 * "Restablecer todo" vuelve la malla a la identidad (imagen intacta) y
 * limpia el historial de deshacer/rehacer — no sale de la categoría,
 * solo descarta el trabajo de deformación hecho hasta ahora en esta
 * sesión.
 */
@Composable
internal fun DistortionPanel(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    distortionBridge: DistortionGestureBridge,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    val sources = remember(layer.id) { EffectSources<Bitmap>() }

    // La malla vive en un `mutableStateOf` propio (no `mutableStateMapOf`
    // ni nada por el estilo: es un único objeto mutado en memoria y
    // reasignado por referencia cada vez que cambia, para que Compose
    // detecte la recomposición) — arranca en identidad (imagen intacta)
    // y se reconstruye entera solo si cambia de capa o de imagen base.
    var field by remember(layer.id) { mutableStateOf<DistortionField?>(null) }
    val freezeMask = remember(layer.id) { DistortionFreezeMask() }
    var freezeMaskVersion by remember(layer.id) { mutableStateOf(0) } // fuerza recomposición del overlay al pintar la máscara

    val undoStack = remember(layer.id) { mutableStateListOf<DistortionField>() }
    val redoStack = remember(layer.id) { mutableStateListOf<DistortionField>() }
    var canUndo by remember(layer.id) { mutableStateOf(false) }
    var canRedo by remember(layer.id) { mutableStateOf(false) }
    var hasAnyEdit by remember(layer.id) { mutableStateOf(false) }

    var selectedTool by remember(layer.id) { mutableStateOf(DistortionToolType.WARP) }
    var brushRadiusPercent by remember(layer.id) { mutableStateOf(18f) } // % del lado más corto de la imagen
    var brushHardness by remember(layer.id) { mutableStateOf(0.4f) }
    var brushIntensity by remember(layer.id) { mutableStateOf(0.6f) }
    var twirlClockwise by remember(layer.id) { mutableStateOf(true) }
    var stretchAxis by remember(layer.id) { mutableStateOf(StretchAxis.HORIZONTAL) }
    var bulgeOutward by remember(layer.id) { mutableStateOf(true) }
    var stretchOutward by remember(layer.id) { mutableStateOf(true) }
    var freezeModeActive by remember(layer.id) { mutableStateOf(false) }
    var freezeEraseMode by remember(layer.id) { mutableStateOf(false) }

    // Antes/después: mantener presionado el botón muestra la imagen SIN
    // deformar (la que había al entrar a la pestaña, o la ya guardada de
    // trazos anteriores confirmados) — ver el `Box` con `pointerInput` de
    // más abajo, junto al botón "Comparar".
    var showingBeforeCompare by remember(layer.id) { mutableStateOf(false) }

    // Estado del trazo en curso (vive fuera de los callbacks del bridge
    // para que `onStrokeMove` sepa cuál fue la muestra anterior).
    var strokeActive by remember(layer.id) { mutableStateOf(false) }
    var strokePrevU by remember(layer.id) { mutableStateOf<Float?>(null) }
    var strokePrevV by remember(layer.id) { mutableStateOf<Float?>(null) }
    var strokeAnchorU by remember(layer.id) { mutableStateOf<Float?>(null) }
    var strokeAnchorV by remember(layer.id) { mutableStateOf<Float?>(null) }


    val imageAspect = remember(layer.id, layer.widthPx, layer.heightPx) {
        if (layer.widthPx > 0 && layer.heightPx > 0) layer.widthPx.toFloat() / layer.heightPx.toFloat() else 1f
    }

    // Radio de pincel en UV normalizado por el LADO MÁS CORTO — ver
    // `DistortionBrush.radiusUv`. `brushRadiusPercent` es 0..100 tal
    // como lo ve el usuario en el slider. La conversión real (que
    // corrige por orientación de la imagen) vive en
    // `distortionBrushRadiusUv` (engine/distortion/DistortionModels.kt)
    // para poder testearla con JUnit — antes este cálculo se hacía acá
    // mismo sin ese ajuste, y el pincel quedaba hasta el doble de
    // grande de lo pedido en fotos apaisadas (aspect > 1).
    fun currentRadiusUv(): Float = distortionBrushRadiusUv(brushRadiusPercent, imageAspect)

    fun currentBrush(): DistortionBrush = DistortionBrush(
        tool = selectedTool,
        radiusUv = currentRadiusUv(),
        feather = brushHardness,
        intensity = brushIntensity,
        twirlClockwise = twirlClockwise,
        stretchAxis = stretchAxis,
        bulgeOutward = bulgeOutward,
        stretchOutward = stretchOutward,
        anchorU = strokeAnchorU,
        anchorV = strokeAnchorV
    )

    // Mismo criterio que Extrude3DPanel (ver su comentario grande): la
    // imagen base se decodifica UNA vez al entrar al panel para esta
    // capa (siempre la que estaba guardada al abrir), y la malla se
    // aplica siempre sobre esa MISMA base durante toda la sesión, sin
    // importar cuántas veces `sourceUri` cambie por los commits de
    // fondo (si no, cada commit re-decodificaría "la imagen ya
    // deformada" como si fuera la base, encadenando deformación sobre
    // deformación).
    LaunchedEffect(layer.id) {
        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 640)
        }
        liveBitmap = small
        sources.publishQuick(small)
        field = DistortionField.identity(imageAspect)
        undoStack.clear()
        redoStack.clear()
        canUndo = false
        canRedo = false
        hasAnyEdit = false
        freezeMask.clear()
        freezeMaskVersion = 0
        // Resolución completa para el commit final (lo que se GUARDA de
        // verdad en la capa) — solo `liveBitmap` de arriba se achica, y
        // solo para que arrastrar el slider se sienta fluido en tiempo
        // real. Un límite acá capaba para siempre la calidad de la capa
        // apenas se aplicaba esta herramienta.
        EffectSourceLoader.loadRemaining(
            sources = sources,
            displayDecodeLimit = { context.effectsDisplayDecodeLimit(EditViewFit.FILL_FRACTION) },
            onDecodeFailure = ::logEffectDecodeFailure,
            decode = { maxDimension ->
                withContext(Dispatchers.IO) {
                    ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = maxDimension)
                }
            }
        )
    }

    // Vista previa progresiva y guardado de ESTA ventana (ver [ProgressiveRenderScheduler]): arrastre sobre la copia
    // chica, reposo sobre la de pantalla y guardado SOLO desde la imagen completa.
    val scheduler = rememberProgressiveRenderScheduler(
        sources = sources,
        persistScope = viewModel.viewModelScope,
        render = { source, _ ->
            // Sin campo de distorsión aún no hay nada que mostrar ni guardar (igual que antes: `field ?: return`).
            val currentField = field
            if (currentField == null) null
            else viewModel.renderDistortion(source, currentField.snapshot(), source.width, source.height)
        },
        onPreview = { viewModel.previewLayerRecolor(layer.id, it) },
        onFinal = { viewModel.commitLayerRecolor(layer.id, it, source = "distortion") }
    )


    fun beginStroke(uv: Offset) {
        val currentField = field ?: return
        undoStack.add(currentField.snapshot())
        if (undoStack.size > DISTORTION_MAX_UNDO_STEPS) undoStack.removeAt(0)
        redoStack.clear()
        canUndo = true
        canRedo = false
        strokeActive = true
        strokePrevU = null
        strokePrevV = null
        strokeAnchorU = uv.x
        strokeAnchorV = uv.y

        if (freezeModeActive) {
            if (freezeEraseMode) {
                freezeMask.erase(uv.x, uv.y, currentRadiusUv(), brushHardness, imageAspect)
            } else {
                freezeMask.paint(uv.x, uv.y, currentRadiusUv(), brushHardness, imageAspect)
            }
            freezeMaskVersion++
            // Pintar la máscara no toca la malla — se cierra el "trazo"
            // recién abierto en undoStack sin haberlo usado, para no
            // dejar un paso de deshacer vacío que no cambia nada visual.
            undoStack.removeAt(undoStack.size - 1)
            canUndo = undoStack.isNotEmpty()
            return
        }

        currentField.applyStroke(
            currentBrush(), uv.x, uv.y, null, null, imageAspect,
            if (freezeMask.isEmpty()) null else freezeMask
        )
        strokePrevU = uv.x
        strokePrevV = uv.y
        hasAnyEdit = true
        scheduler.schedulePreview()
    }

    fun continueStroke(uv: Offset) {
        if (!strokeActive) return
        if (freezeModeActive) {
            if (freezeEraseMode) {
                freezeMask.erase(uv.x, uv.y, currentRadiusUv(), brushHardness, imageAspect)
            } else {
                freezeMask.paint(uv.x, uv.y, currentRadiusUv(), brushHardness, imageAspect)
            }
            freezeMaskVersion++
            return
        }
        val currentField = field ?: return
        currentField.applyStroke(
            currentBrush(), uv.x, uv.y, strokePrevU, strokePrevV, imageAspect,
            if (freezeMask.isEmpty()) null else freezeMask
        )
        strokePrevU = uv.x
        strokePrevV = uv.y
        scheduler.schedulePreview()
    }

    fun endStroke() {
        if (!strokeActive) return
        strokeActive = false
        strokePrevU = null
        strokePrevV = null
        strokeAnchorU = null
        strokeAnchorV = null
        if (!freezeModeActive) scheduler.scheduleCommit()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = field ?: return
        redoStack.add(current.snapshot())
        if (redoStack.size > DISTORTION_MAX_UNDO_STEPS) redoStack.removeAt(0)
        val restored = undoStack.removeAt(undoStack.size - 1)
        field = restored
        canUndo = undoStack.isNotEmpty()
        canRedo = true
        hasAnyEdit = !restored.isIdentity() || freezeMask.isEmpty().not()
        scheduler.schedulePreview()
        scheduler.scheduleCommit()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = field ?: return
        undoStack.add(current.snapshot())
        val restored = redoStack.removeAt(redoStack.size - 1)
        field = restored
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        hasAnyEdit = true
        scheduler.schedulePreview()
        scheduler.scheduleCommit()
    }

    fun resetAll() {
        field = DistortionField.identity(imageAspect)
        undoStack.clear()
        redoStack.clear()
        canUndo = false
        canRedo = false
        hasAnyEdit = false
        freezeMask.clear()
        freezeMaskVersion++
        scheduler.schedulePreview()
        scheduler.scheduleCommit()
    }

    // Registro en el puente hacia el gesto del canvas (ver
    // [DistortionGestureBridge]) — mismo criterio que Extrude3DPanel:
    // mientras esta pestaña está en pantalla para esta capa, el canvas
    // de arriba deja de dibujar el marco/manijas normales con 1 dedo y
    // en cambio manda acá las muestras del trazo en UV.
    DisposableEffect(layer.id) {
        distortionBridge.active = true
        distortionBridge.onStrokeStart = { uv -> beginStroke(uv) }
        distortionBridge.onStrokeMove = { uv -> continueStroke(uv) }
        distortionBridge.onStrokeEnd = { endStroke() }
        // A PEDIDO DEL USUARIO: la barra superior (↩/↪) pasa a ser el
        // único control de deshacer/rehacer en pantalla — mientras esta
        // categoría esté activa, invoca DIRECTO a las funciones locales
        // `undo()`/`redo()` de acá arriba (dueñas reales del historial
        // de trazos), en vez de duplicar los botones abajo del panel
        // (ver el bloque eliminado, más abajo, donde antes vivían
        // `DistortionIconActionButton` "Deshacer"/"Rehacer").
        distortionBridge.onUndo = { undo() }
        distortionBridge.onRedo = { redo() }
        distortionBridge.canUndo = canUndo
        distortionBridge.canRedo = canRedo
        // Mismo criterio que onUndo/onRedo de arriba — ver el comentario
        // grande en `onResetRequested`, dentro de [DistortionGestureBridge].
        distortionBridge.onResetRequested = { resetAll() }
        // Publica la máscara para que el overlay del canvas principal
        // (ver el bloque `distortionBridge.freezeModeActive` en
        // EditorScreen) pueda pintarla — ver comentario grande en
        // [DistortionGestureBridge] sobre por qué es referencia +
        // contador de versión en vez de un valor inmutable.
        distortionBridge.freezeMask = freezeMask
        distortionBridge.freezeMaskVersion = freezeMaskVersion
        distortionBridge.freezeModeActive = freezeModeActive
        onDispose {
            distortionBridge.active = false
            distortionBridge.onStrokeStart = null
            distortionBridge.onStrokeMove = null
            distortionBridge.onStrokeEnd = null
            distortionBridge.onUndo = null
            distortionBridge.onRedo = null
            distortionBridge.canUndo = false
            distortionBridge.canRedo = false
            distortionBridge.onResetRequested = null
            distortionBridge.freezeMask = null
            distortionBridge.freezeModeActive = false
        }
    }
    // El `DisposableEffect` de arriba solo corre al entrar/salir de la
    // pestaña (key = layer.id) — estos dos `LaunchedEffect` mantienen el
    // bridge sincronizado en cada cambio posterior de `freezeMaskVersion`
    // (cada trazo de pintar/borrar máscara) y de `freezeModeActive`
    // (prender/apagar el switch "Congelar zona").
    LaunchedEffect(freezeMaskVersion) {
        distortionBridge.freezeMaskVersion = freezeMaskVersion
    }
    LaunchedEffect(freezeModeActive) {
        distortionBridge.freezeModeActive = freezeModeActive
    }
    // Mismo criterio: cada trazo cambia `canUndo`/`canRedo` (ver
    // `beginStroke`/`undo`/`redo`/`resetAll` arriba) y hay que
    // reflejarlo en el bridge para que el botón de la barra superior se
    // habilite/deshabilite en el momento correcto, no recién la próxima
    // vez que se entra/sale de esta categoría.
    LaunchedEffect(canUndo) {
        distortionBridge.canUndo = canUndo
    }
    LaunchedEffect(canRedo) {
        distortionBridge.canRedo = canRedo
    }

    // "Comparar antes/después": mantener presionado fuerza la vista
    // previa de vuelta a la imagen SIN deformar (identidad), soltar
    // vuelve a mostrar el estado actual de la malla.
    fun applyCompareState(showBefore: Boolean) {
        showingBeforeCompare = showBefore
        val small = liveBitmap ?: return
        if (showBefore) {
            coroutineScope.launch {
                val identity = DistortionField.identity(imageAspect)
                val rendered = viewModel.renderDistortion(small, identity, small.width, small.height)
                viewModel.previewLayerRecolor(layer.id, rendered)
            }
        } else {
            scheduler.schedulePreview()
        }
    }

    Column(modifier = modifier) {
        // BUG REAL corregido acá — mismo caso que [ColorBasicoFloatingWindow]/
        // [Basico3DFloatingWindow]/[EffectsPanel] (ver el comentario
        // grande en [ColorBasicoFloatingWindow] para el detalle
        // completo): los chips de herramienta, los toggles y los
        // sliders de acá abajo no dibujan el bitmap ni dependen de él —
        // `imageAspect` (usado para el pincel y para inicializar la
        // malla de distorsión) sale de `layer.widthPx`/`layer.heightPx`,
        // metadata de la capa disponible de una, nunca del bitmap
        // decodificado. Las únicas llamadas que sí usan `liveBitmap`/
        // `fullBitmap` (`scheduleLivePreview`/`scheduleCommit`/
        // `applyCompareState`, todas arriba) ya vuelven temprano si
        // todavía es null — así que sacar el `isLoading` de acá no deja
        // ningún camino sin proteger, solo evita el salto de tamaño en
        // dos pasos que se veía al abrir esta pestaña.
        // CORREGIDO en esta misma revisión, al pasar a vivir dentro de
        // [DistortionFloatingWindow]: este `Column` traía su PROPIO
        // `.weight(1f)` + `.verticalScroll(...)` porque, cuando esto se
        // montaba directo adentro de la ventana compartida de "Efecto"
        // (`EffectsPanel`, ver el comentario grande unas líneas más
        // arriba sobre por qué "Distorsión" era la ÚNICA que no compartía
        // el mismo molde), necesitaba resolver su propio scroll — el
        // contenedor de esa ventana compartida NO envolvía a Distorsión
        // en scroll (ver el `if/else` que existía ahí, ahora eliminado).
        // [FloatingToolWindow] es distinto: SIEMPRE envuelve su `content`
        // en su propio `.verticalScroll(...)` (ver su firma) — anidar
        // OTRO scroll (con `.weight(1f)` encima, que además necesita un
        // padre de altura acotada para tener sentido, y un contenedor con
        // scroll vertical le da altura NO acotada) es exactamente el bug
        // que el comentario original de esta función advertía evitar, solo
        // que en la dirección opuesta. `DistortionPanel` ya no se monta en
        // ningún otro lugar del proyecto (búsqueda hecha antes de este
        // cambio) — así que sacarle el scroll propio acá es seguro: ahora
        // depende enteramente del scroll de [FloatingToolWindow], igual
        // que ya hace cada categoría hoja de Contorno/Resplandor/Sombra/
        // Reflejo.
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Herramienta",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DistortionToolChip(R.drawable.ic_distort_warp, "Cepillo", selectedTool == DistortionToolType.WARP) {
                    selectedTool = DistortionToolType.WARP; freezeModeActive = false
                }
                DistortionToolChip(R.drawable.ic_distort_sphere, "Esferizar", selectedTool == DistortionToolType.SPHERE) {
                    selectedTool = DistortionToolType.SPHERE; freezeModeActive = false
                }
                DistortionToolChip(R.drawable.ic_rotate, "Giro", selectedTool == DistortionToolType.TWIRL) {
                    selectedTool = DistortionToolType.TWIRL; freezeModeActive = false
                }
                DistortionToolChip(R.drawable.ic_distort_bulge, "Protuberancia", selectedTool == DistortionToolType.BULGE_PINCH) {
                    selectedTool = DistortionToolType.BULGE_PINCH; freezeModeActive = false
                }
                DistortionToolChip(R.drawable.ic_distort_splash, "Chapoteo", selectedTool == DistortionToolType.CIRCLE_SPLASH) {
                    selectedTool = DistortionToolType.CIRCLE_SPLASH; freezeModeActive = false
                }
                DistortionToolChip(R.drawable.ic_distort_anchor, "Tramo", selectedTool == DistortionToolType.STRETCH_ANCHOR) {
                    selectedTool = DistortionToolType.STRETCH_ANCHOR; freezeModeActive = false
                }
                DistortionToolChip(
                    if (stretchAxis == StretchAxis.HORIZONTAL) R.drawable.ic_resize_width else R.drawable.ic_resize_height,
                    "Estiramiento",
                    selectedTool == DistortionToolType.STRETCH_AXIS
                ) {
                    if (selectedTool == DistortionToolType.STRETCH_AXIS) {
                        // Tocar de nuevo la misma herramienta alterna el eje —
                        // evita necesitar un control aparte solo para elegir
                        // horizontal/vertical.
                        stretchAxis = if (stretchAxis == StretchAxis.HORIZONTAL) StretchAxis.VERTICAL else StretchAxis.HORIZONTAL
                    } else {
                        selectedTool = DistortionToolType.STRETCH_AXIS
                    }
                    freezeModeActive = false
                }
                DistortionToolChip(R.drawable.ic_distort_mirror, "Espejo", selectedTool == DistortionToolType.MIRROR) {
                    selectedTool = DistortionToolType.MIRROR; freezeModeActive = false
                }
                DistortionToolChip(R.drawable.ic_restore_all, "Reconstruir", selectedTool == DistortionToolType.RECONSTRUCT) {
                    selectedTool = DistortionToolType.RECONSTRUCT; freezeModeActive = false
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- Ajustes específicos de la herramienta activa (giro,
            // protuberancia/pellizco, estiramiento): un toggle chico de
            // dos estados, mismo patrón visual en los tres casos.
            when (selectedTool) {
                DistortionToolType.TWIRL -> {
                    DistortionDirectionToggle(
                        leftLabel = "Antihorario",
                        rightLabel = "Horario",
                        rightSelected = twirlClockwise,
                        onSelectRight = { twirlClockwise = it }
                    )
                }
                DistortionToolType.SPHERE, DistortionToolType.BULGE_PINCH -> {
                    DistortionDirectionToggle(
                        leftLabel = "Pellizco (adentro)",
                        rightLabel = "Protuberancia (afuera)",
                        rightSelected = bulgeOutward,
                        onSelectRight = { bulgeOutward = it }
                    )
                }
                DistortionToolType.STRETCH_AXIS -> {
                    DistortionDirectionToggle(
                        leftLabel = "Afinar",
                        rightLabel = "Alargar",
                        rightSelected = stretchOutward,
                        onSelectRight = { stretchOutward = it }
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(6.dp))

            LabeledSlider(
                label = "Tamaño del pincel",
                value = brushRadiusPercent,
                range = 2f..80f,
                valueLabel = { "${it.roundToInt()}%" }
            ) { brushRadiusPercent = it }

            LabeledSlider(
                label = "Dureza del borde",
                value = brushHardness,
                range = 0f..1f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                displayScale = 100f
            ) { brushHardness = it }

            LabeledSlider(
                label = "Intensidad / presión",
                value = brushIntensity,
                range = 0.02f..1f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                displayScale = 100f
            ) { brushIntensity = it }

            Spacer(modifier = Modifier.height(6.dp))

            // --- Máscara de "congelar zona" ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock_closed),
                    contentDescription = null,
                    tint = if (freezeModeActive) Color.White else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Congelar zona (protege del efecto)",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = freezeModeActive,
                    onCheckedChange = { freezeModeActive = it }
                )
            }
            if (freezeModeActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DistortionSmallActionButton(
                        label = "Pintar",
                        selected = !freezeEraseMode,
                        modifier = Modifier.weight(1f)
                    ) { freezeEraseMode = false }
                    DistortionSmallActionButton(
                        label = "Borrar",
                        selected = freezeEraseMode,
                        modifier = Modifier.weight(1f)
                    ) { freezeEraseMode = true }
                    DistortionSmallActionButton(
                        label = "Limpiar todo",
                        selected = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        freezeMask.clear()
                        freezeMaskVersion++
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- Deshacer/Rehacer de Distorsión ELIMINADOS DE ACÁ A
            // PEDIDO DEL USUARIO: quedaban duplicados con el ↩/↪ de la
            // barra superior (ver el header de EditorScreen y
            // [DistortionGestureBridge].onUndo/onRedo/canUndo/canRedo,
            // más arriba en este archivo) — dos pares de flechas de
            // deshacer/rehacer visibles a la vez en la misma pantalla no
            // es un patrón profesional, así que ahora hay un solo
            // control: el de arriba, que mientras esta categoría esté
            // activa deshace/rehace trazos de Distorsión en vez del
            // historial "de proyecto". `undo()`/`redo()` siguen viviendo
            // acá (son las dueñas reales del historial), solo se
            // invocan desde la barra superior en vez de desde un botón
            // propio de este panel.

            // --- Comparar / restablecer todo ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(layer.id) {
                        detectTapGestures(
                            onPress = {
                                applyCompareState(true)
                                tryAwaitRelease()
                                applyCompareState(false)
                            }
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = if (showingBeforeCompare) 0.22f else 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (showingBeforeCompare) "Mostrando: original" else "Mantener para comparar",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .then(if (hasAnyEdit) Modifier.clickable { resetAll() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore_all),
                        contentDescription = null,
                        tint = if (hasAnyEdit) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Restablecer todo",
                        color = if (hasAnyEdit) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/** Toggle de dos opciones (izquierda/derecha) reutilizado por Giro/Esferizar-Protuberancia/Estiramiento en [DistortionPanel] — un solo componente, tres usos. */
@Composable
private fun DistortionDirectionToggle(
    leftLabel: String,
    rightLabel: String,
    rightSelected: Boolean,
    onSelectRight: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
    ) {
        listOf(leftLabel to false, rightLabel to true).forEach { (label, isRight) ->
            val active = isRight == rightSelected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelectRight(isRight) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) Color.White else Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/** Botón chico de acción (Pintar/Borrar/Limpiar todo de la máscara de congelar en [DistortionPanel]). */
@Composable
private fun DistortionSmallActionButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.4f else 0.14f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Botón de Deshacer/Rehacer de [DistortionPanel] — ELIMINADO A PEDIDO
 * DEL USUARIO junto con el `Row` que lo usaba (ver DistortionPanel, más
 * arriba): quedaba duplicado con el ↩/↪ de la barra superior, que ahora
 * cubre este mismo caso vía [DistortionGestureBridge].onUndo/onRedo. Se
 * deja este comentario en vez de borrar el bloque en silencio para que
 * quien busque "Deshacer" acá encuentre por qué ya no está.
 */

/**
 * Etiqueta de pestaña ("Color" / "3D" / "Efecto" en la barra superior,
 * en modo edición de imagen aislada) con efecto de "pintado" morado:
 * al activarse, el texto blanco se recolorea de IZQUIERDA A DERECHA con
 * el morado de marca, como si un pincel lo pasara por encima, en vez de
 * cambiar de color de golpe. Al desactivarse, el mismo barrido corre en
 * reversa (blanco "tapa" al morado de izquierda a derecha) para que la
 * transición sea simétrica y nunca se sienta un parpadeo.
 *
 * Implementación: dos `Text` idénticos superpuestos en un `Box` — uno
 * blanco de base, y uno morado encima recortado con `drawWithContent` +
 * `clipRect` a un ancho animado (`sweep * size.width`). Nada de
 * `Modifier.graphicsLayer(alpha=...)` ni animar el `Color` del propio
 * `Text`: eso pintaría todo el texto a la vez con un fundido, no un
 * barrido direccional — que es exactamente lo que se pidió.
 */
@Composable
private fun PurpleSweepTabLabel(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sweep by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "tabLabelPurpleSweep"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        // Base blanca — siempre presente debajo del morado.
        Text(
            text,
            color = Color.White,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium
        )
        // Capa morada, recortada de izquierda a derecha según `sweep`.
        // `sweep == 0f` no se dibuja nada (recorte de ancho 0); en
        // `sweep == 1f` cubre el texto entero, igual que antes el color
        // sólido `BrandPurpleLight`.
        if (sweep > 0f) {
            Text(
                text,
                color = BrandPurpleLight,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.drawWithContent {
                    clipRect(right = size.width * sweep) {
                        this@drawWithContent.drawContent()
                    }
                }
            )
        }
    }
}

/**
 * Envoltorio de [Popup] para los tres menús angostos que cuelgan de
 * "Color" / "3D" / "Efecto" (ver [EditImageColorMenu] / [EditImage3DMenu]
 * / [EditImageEffectsMenu]): en vez de aparecer/desaparecer de golpe, la
 * ventana se ABRE "desde el pie" de su propia pestaña — crece hacia abajo
 * (`expandFrom = Alignment.Top`, así el borde de arriba, pegado al texto
 * que la abrió, queda fijo y todo el crecimiento pasa por debajo) y se
 * cierra con el mismo movimiento en reversa.
 *
 * A propósito el `Popup` sigue montado mientras `visibleState` no haya
 * terminado su transición de salida — si se desmontara apenas
 * `visible = false`, la animación de cierre nunca llegaría a verse.
 * Por eso el llamador ya NO debe envolver esto en un `if (show...)`:
 * este composable se llama SIEMPRE y es él mismo quien decide, con
 * `visibleState.currentState`, cuándo de verdad ya no hace falta montar
 * el `Popup`.
 */
@Composable
private fun AnimatedDropdownPopup(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    if (visibleState.currentState || visibleState.targetState) {
        Popup(
            popupPositionProvider = BelowAnchorCenteredPopupPositionProvider(gapPx = 8),
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = false)
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(160)) + expandVertically(
                    animationSpec = tween(240),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(
                    animationSpec = tween(200),
                    shrinkTowards = Alignment.Top
                )
            ) {
                content()
            }
        }
    }
}

/** * "Efectos" era una pestaña más al lado de "Recolor"/"3D" (ver
 * EditImageToolsHeader) con una fila de chips (Fondo/Color/Contorno/...)
 * para elegir categoría, debajo del lienzo. Ahora esa fila desaparece:
 * el mismo selector de categoría vive ACÁ, en esta ventana angosta y
 * vertical que cuelga del texto "Efecto" de arriba — "una barra semi
 * gruesa vertical", como se pidió, con esquinas en punta (RectangleShape,
 * no redondeadas — se pidió explícitamente que no fuera "muy genérico") y
 * un ancho compacto, sin espacio de sobra.
 *
 * Solo se movió el SELECTOR — los sliders/ajustes de cada categoría
 * siguen exactamente donde estaban siempre, debajo del lienzo, dentro de
 * EffectsPanel: ningún control, valor por defecto ni lógica de cálculo
 * se tocó.
 *
 * A PEDIDO EXPLÍCITO: "Fondo", "Color" y "Presets" NO están en esta
 * lista — solo Contorno, Resplandor, Sombra, Reflejo y Distorsión.
 * "Color" (Nitidez/Saturación/Brillo/Contraste/Tono) en particular ya
 * no vive en "Efecto" en absoluto — se mudó entera a la pestaña
 * "Color" de arriba, como su opción "Básico" (ver
 * [ColorBasicoFloatingWindow]/[EditImageColorMenu]); acá abajo
 * `EffectsPanel` conserva su rama de ruteo por compatibilidad interna
 * de índices, pero esta lista nunca la ofrece — inalcanzable desde
 * esta ventana. El número junto a cada etiqueta es su índice REAL
 * dentro del mapa de índices de categorías (ver EffectsPanel) — se preserva
 * tal cual para no tocar el "when" que ya rutea cada categoría a sus
 * sliders.
 */
@Composable
private fun EditImageEffectsMenu(
    // A PEDIDO DEL USUARIO — MULTI-VENTANA: "Contorno", "Resplandor",
    // "Sombra" y "Reflejo" ahora tienen cada una su propia ventana
    // flotante independiente (ver [ContornoFloatingWindow]/
    // [ResplandorFloatingWindow]/[SombraFloatingWindow]/
    // [ReflejoFloatingWindow]), así que más de un ítem de este menú
    // puede estar "activo" al mismo tiempo — por eso `selected` pasa de
    // un único `Int?` a un `Set<Int>`, mismo criterio que ya usa
    // [EditImageColorMenu] para "Recolor"/"Básico". Un ítem se pinta
    // resaltado si su índice está en el set; conjunto vacío = ninguno
    // resaltado (p.ej. recién abierto el menú, sin ninguna ventana de
    // "Efecto" abierta todavía).
    visible: Boolean,
    selected: Set<Int>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val items = remember {
        listOf(
            EditImageEffectMenuItem("Contorno", 2) { tint -> EffectMenuOutlineIcon(tint) },
            EditImageEffectMenuItem("Resplandor", 3) { tint -> EffectMenuGlowIcon(tint) },
            EditImageEffectMenuItem("Sombra", 4) { tint -> EffectMenuShadowIcon(tint) },
            EditImageEffectMenuItem("Reflejo", 5) { tint -> EffectMenuReflectionIcon(tint) },
            EditImageEffectMenuItem("Distorsión", 7) { tint -> EffectMenuDistortionIcon(tint) }
        )
    }
    AnimatedDropdownPopup(visible = visible, onDismissRequest = onDismiss) {
        // A PEDIDO DEL USUARIO: nada de un ancho fijo arbitrario (150dp)
        // que deja hueco de sobra en las etiquetas cortas — mismo
        // criterio que ya usa el resto de los popups angostos de esta
        // pantalla (ver el comentario grande de "Restablecer", más
        // arriba en este archivo): `wrapContentWidth()` en el Surface +
        // `Modifier.width(IntrinsicSize.Min)` en el Column hacen que el
        // ancho real de la ventana sea el de su fila más ancha (ícono +
        // texto + paddings) y NADA más — ninguna fila queda con espacio
        // vacío a la derecha.
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .shadow(elevation = 10.dp, shape = RectangleShape),
            color = SurfaceTintedElevated,
            shape = RectangleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .padding(vertical = 4.dp)
            ) {
                items.forEachIndexed { position, item ->
                    val isActive = item.categoryIndex in selected
                    val tint = if (isActive) BrandPurpleLight else Color.White.copy(alpha = 0.9f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(item.categoryIndex)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        // Ícono PROPIO de esta categoría (ver arriba,
                        // EffectMenuOutlineIcon/EffectMenuGlowIcon/etc.) —
                        // pegado al texto, sin espacio de sobra entre
                        // los dos ni a la derecha de la etiqueta.
                        item.icon(tint)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            item.label,
                            color = tint,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Separador fino entre opciones — no después de la
                    // última, para no dejar un borde suelto pegado al
                    // fondo de la ventana.
                    if (position != items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }
    }
}

/** Un ítem de [EditImageEffectsMenu]: su etiqueta, su índice REAL dentro
 * del mapa de índices de categorías (ver EffectsPanel — se preserva tal cual
 * para no tocar el "when" que ya rutea cada categoría a sus sliders), y
 * su ícono propio. */
private class EditImageEffectMenuItem(
    val label: String,
    val categoryIndex: Int,
    val icon: @Composable (Color) -> Unit
)

/**
 * Menú desplegable del texto "Color" de la barra superior (ver el Row
 * de "Izquierda" del topBar en EditorScreen, justo al lado de "Efecto").
 * A PEDIDO DEL USUARIO: mismo movimiento y MISMO lenguaje visual que
 * [EditImageEffectsMenu] — ventana angosta y vertical, esquinas en punta
 * (RectangleShape), ancho compacto sin espacio de sobra — pero para
 * "Recolor", que antes vivía como pestaña suelta en EditImageToolsHeader.
 * A PEDIDO DEL USUARIO, ahora tiene DOS opciones: "Básico" (Nitidez/
 * Saturación/Brillo/Contraste/Tono — ver [ColorBasicoFloatingWindow],
 * la categoría "Color" que antes vivía adentro de "Efecto") arriba de
 * "Recolor" — mismo criterio y mismo trato visual que [EditImage3DMenu]
 * ya usa para su propio "Básico"; a futuro se le pueden sumar más sin
 * tocar el resto de este composable (mismo criterio de lista de ítems
 * que ya usa EditImageEffectsMenu).
 */
@Composable
private fun EditImageColorMenu(
    // A PEDIDO DEL USUARIO — MULTI-VENTANA: ya NO es un único `Int?` —
    // "Básico" y "Recolor" pueden estar las dos abiertas a la vez, así
    // que hace falta poder pintar las DOS como activas al mismo tiempo.
    // Conjunto vacío = ningún ítem se pinta como activo; cada
    // `categoryIndex` presente en el set se pinta resaltado.
    visible: Boolean,
    selected: Set<Int>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val items = remember {
        listOf(
            // A PEDIDO DEL USUARIO: "Básico" va PRIMERO en la lista,
            // arriba de "Recolor" — mismo orden que pidió para esta
            // ventana. `categoryIndex = 1` es el mismo número que ya usa
            // `editImageColorOption` para esta opción (ver EditorScreen);
            // el orden de la lista es puramente visual y no tiene que
            // coincidir con el valor de `categoryIndex`.
            EditImageColorMenuItem("Básico", 1) { tint -> ColorMenuBasicoIcon(tint) },
            EditImageColorMenuItem("Recolor", 0) { tint -> ColorMenuRecolorIcon(tint) }
        )
    }
    AnimatedDropdownPopup(visible = visible, onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .shadow(elevation = 10.dp, shape = RectangleShape),
            color = SurfaceTintedElevated,
            shape = RectangleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .padding(vertical = 4.dp)
            ) {
                items.forEachIndexed { position, item ->
                    val isActive = selected.contains(item.categoryIndex)
                    val tint = if (isActive) BrandPurpleLight else Color.White.copy(alpha = 0.9f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(item.categoryIndex)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        item.icon(tint)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            item.label,
                            color = tint,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (position != items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }
    }
}

/** Un ítem de [EditImageColorMenu]: su etiqueta, su índice interno (0 =
 * Recolor, 1 = Básico — mismos números que usa `editImageColorOption`
 * en EditorScreen para saber cuál ventana flotante mostrar; YA NO
 * rutea nada dentro de `LayerColorEditPanel` — ese panel dejó de
 * manejar tanto "Recolor" como "Básico" el día que ambos se
 * convirtieron en ventanas flotantes, ver su comentario "este panel ya
 * NO maneja..."), y su ícono propio. */
private class EditImageColorMenuItem(
    val label: String,
    val categoryIndex: Int,
    val icon: @Composable (Color) -> Unit
)

/**
 * Ícono de "Recolor" en [EditImageColorMenu]: una gota de pintura —
 * el glifo universal para "color" en herramientas de edición de imagen
 * (Figma, Procreate, Photoshop) — trazada a mano con Path/DrawScope,
 * mismo criterio que el resto de los glifos de este menú (ver
 * [EffectMenuOutlineIcon]/[EffectMenuGlowIcon]/etc., arriba): nítida en
 * cualquier densidad, sin depender de un drawable extra.
 */
@Composable
internal fun ColorMenuRecolorIcon(tint: Color, iconSize: Dp = 16.dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val drop = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.04f)
            cubicTo(
                size.width * 0.90f, size.height * 0.42f,
                size.width * 0.84f, size.height * 0.78f,
                size.width * 0.5f, size.height * 0.96f
            )
            cubicTo(
                size.width * 0.16f, size.height * 0.78f,
                size.width * 0.10f, size.height * 0.42f,
                size.width * 0.5f, size.height * 0.04f
            )
            close()
        }
        drawPath(drop, color = tint)
        // Brillo interior sutil, cerca de la punta — evita que la gota
        // se vea como una silueta plana, mismo recurso que el resplandor
        // (núcleo + halo) usa en EffectMenuGlowIcon, arriba.
        drawCircle(
            color = Color.Black.copy(alpha = 0.18f),
            radius = size.minDimension * 0.16f,
            center = Offset(size.width * 0.40f, size.height * 0.40f)
        )
    }
}

/**
 * Ícono de "Básico" en [EditImageColorMenu]: tres controles deslizantes
 * horizontales con su perilla — el glifo universal para "ajustes
 * básicos" en herramientas de edición de imagen (mismo concepto que ya
 * usa el propio panel de adentro, [EffectsCategoryColor], con sus 5
 * sliders de Nitidez/Saturación/Brillo/Contraste/Tono), trazado a mano
 * con Path/DrawScope — mismo criterio que el resto de los glifos de
 * este menú (ver [ColorMenuRecolorIcon]/[Menu3DBasicCubeIcon], acá
 * cerca): nítida en cualquier densidad, sin depender de un drawable
 * extra.
 */
@Composable
internal fun ColorMenuBasicoIcon(tint: Color, iconSize: Dp = 16.dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val trackY = listOf(size.height * 0.22f, size.height * 0.5f, size.height * 0.78f)
        // Las tres perillas quedan en posiciones distintas a lo largo de
        // su línea — mismo recurso visual que usa cualquier ecualizador
        // o panel de ajustes rápidos, para que de un vistazo se lea
        // "esto son controles", no una lista genérica de rayas.
        val knobX = listOf(size.width * 0.62f, size.width * 0.34f, size.width * 0.72f)
        val trackAlpha = 0.45f
        val strokeWidth = size.minDimension * 0.09f
        trackY.forEachIndexed { i, y ->
            drawLine(
                color = tint.copy(alpha = trackAlpha),
                start = Offset(size.width * 0.08f, y),
                end = Offset(size.width * 0.92f, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = tint,
                radius = size.minDimension * 0.14f,
                center = Offset(knobX[i], y)
            )
        }
    }
}

/**
 * Menú desplegable del texto "3D" de la barra superior (ver el Row de
 * "Izquierda" del topBar en EditorScreen, entre "Color" y "Efecto").
 * A PEDIDO DEL USUARIO: mismo movimiento y MISMO lenguaje visual que
 * [EditImageColorMenu]/[EditImageEffectsMenu] — ventana angosta y
 * vertical, esquinas en punta (RectangleShape), ancho compacto sin
 * espacio de sobra — pero para la extrusión 3D real (ver
 * [Basico3DFloatingWindow]), que antes vivía como pestaña suelta en
 * EditImageToolsHeader. Por ahora tiene una sola opción ("Básico");
 * a futuro se le pueden sumar más sin tocar el resto de este
 * composable (mismo criterio de lista de ítems que ya usan los otros
 * dos menús).
 */
@Composable
private fun EditImage3DMenu(
    // `null` = ningún ítem se pinta como activo — mismo criterio que
    // [EditImageColorMenu]: el llamador (EditorScreen) solo pasa 0
    // cuando el usuario de verdad tocó "Básico" acá adentro Y la pestaña
    // activa sigue siendo la 1 (ver `editImage3DChosen` en EditorScreen).
    // Con una sola opción no hace falta el índice de categoría que sí
    // necesita EditImageEffectsMenu.
    visible: Boolean,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val items = remember {
        listOf(
            // A PEDIDO DEL USUARIO: "Básico" y no "Basic 3D" — el "3D"
            // ya está implícito en el texto de arriba que abre este
            // menú, repetirlo acá adentro era redundante y además hacía
            // que la etiqueta más larga se recortara ("Bas...") en vez
            // de mostrarse completa.
            EditImage3DMenuItem("Básico", 0) { tint -> Menu3DBasicCubeIcon(tint) }
        )
    }
    AnimatedDropdownPopup(visible = visible, onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .shadow(elevation = 10.dp, shape = RectangleShape),
            color = SurfaceTintedElevated,
            shape = RectangleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .padding(vertical = 4.dp)
            ) {
                items.forEachIndexed { position, item ->
                    val isActive = selected == item.categoryIndex
                    val tint = if (isActive) BrandPurpleLight else Color.White.copy(alpha = 0.9f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(item.categoryIndex)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        item.icon(tint)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            item.label,
                            color = tint,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (position != items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }
    }
}

/** Un ítem de [EditImage3DMenu]: su etiqueta, su índice REAL dentro de
 * `selectedTab` de LayerColorEditPanel (0 = Recolor, 1 = 3D — se
 * preserva tal cual para no tocar el "when" que ya rutea cada pestaña a
 * su panel), y su ícono propio. */
private class EditImage3DMenuItem(
    val label: String,
    val categoryIndex: Int,
    val icon: @Composable (Color) -> Unit
)

/**
 * Ícono de "Básico" en [EditImage3DMenu]: un cubo isométrico con sus
 * 3 caras visibles pintadas a distinta opacidad — misma "luz" ficticia
 * arriba-a-la-derecha que un cubo 3D real proyectaría — para que el
 * glifo se lea como volumen y no como un rombo plano. Trazado a mano
 * con Path/DrawScope, mismo criterio que el resto de los glifos de
 * estos menús (ver [ColorMenuRecolorIcon] arriba, o
 * [EffectMenuOutlineIcon]/etc. más abajo): nítido en cualquier
 * densidad, sin depender de un drawable extra.
 */
@Composable
internal fun Menu3DBasicCubeIcon(tint: Color, iconSize: Dp = 16.dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val top = Offset(w * 0.5f, h * 0.04f)
        val left = Offset(w * 0.08f, h * 0.30f)
        val right = Offset(w * 0.92f, h * 0.30f)
        val center = Offset(w * 0.5f, h * 0.54f)
        val bottomLeft = Offset(w * 0.08f, h * 0.72f)
        val bottomRight = Offset(w * 0.92f, h * 0.72f)
        val bottom = Offset(w * 0.5f, h * 0.96f)

        // Cara superior (rombo) — la más iluminada.
        val topFace = Path().apply {
            moveTo(top.x, top.y)
            lineTo(right.x, right.y)
            lineTo(center.x, center.y)
            lineTo(left.x, left.y)
            close()
        }
        // Cara izquierda — sombreada.
        val leftFace = Path().apply {
            moveTo(left.x, left.y)
            lineTo(center.x, center.y)
            lineTo(bottom.x, bottom.y)
            lineTo(bottomLeft.x, bottomLeft.y)
            close()
        }
        // Cara derecha — tono intermedio.
        val rightFace = Path().apply {
            moveTo(center.x, center.y)
            lineTo(right.x, right.y)
            lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottom.x, bottom.y)
            close()
        }
        drawPath(leftFace, color = tint.copy(alpha = 0.45f))
        drawPath(rightFace, color = tint.copy(alpha = 0.70f))
        drawPath(topFace, color = tint)
    }
}

/**
 * Íconos de [EditImageEffectsMenu] — uno POR CATEGORÍA, no un ícono
 * genérico repetido cinco veces. Dibujados a mano con Canvas/DrawScope,
 * mismo criterio que el resto de los glifos de la app (ver
 * [ShadowModuleIcon]/[ReflectionModuleIcon] en EditorBottomBar.kt, o
 * [drawCheckGlyph]/[drawDeleteGlyph] más arriba en este mismo archivo):
 * nítidos en cualquier densidad, sin depender de un drawable extra.
 * "Sombra" y "Reflejo" repiten EXACTAMENTE el mismo lenguaje visual que
 * ya usan [ShadowModuleIcon]/[ReflectionModuleIcon] del cajón Módulos, para
 * que el mismo concepto se vea igual en toda la app.
 */
@Composable
internal fun EffectMenuOutlineIcon(tint: Color, iconSize: Dp = 16.dp) {
    // "Contorno": un TRAZO (stroke) alrededor de una forma, sin relleno
    // — el borde ES el efecto.
    Canvas(modifier = Modifier.size(iconSize)) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.16f, size.height * 0.16f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.68f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.14f, size.width * 0.14f),
            style = Stroke(width = size.minDimension * 0.16f)
        )
    }
}

@Composable
internal fun EffectMenuGlowIcon(tint: Color, iconSize: Dp = 16.dp) {
    // "Resplandor": núcleo sólido rodeado de anillos concéntricos que se
    // desvanecen hacia afuera — el halo de luz ES el efecto.
    Canvas(modifier = Modifier.size(iconSize)) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        drawCircle(color = tint.copy(alpha = 0.18f), radius = size.minDimension * 0.5f, center = center)
        drawCircle(color = tint.copy(alpha = 0.34f), radius = size.minDimension * 0.34f, center = center)
        drawCircle(color = tint, radius = size.minDimension * 0.18f, center = center)
    }
}

@Composable
internal fun EffectMenuShadowIcon(tint: Color, iconSize: Dp = 16.dp) {
    // "Sombra" — mismo glifo que ShadowModuleIcon en EditorBottomBar.kt:
    // objeto + su sombra proyectada, difusa, debajo.
    Canvas(modifier = Modifier.size(iconSize)) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.28f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f, size.width * 0.08f)
        )
        drawOval(
            color = tint.copy(alpha = 0.35f),
            topLeft = Offset(size.width * 0.08f, size.height * 0.74f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.20f)
        )
    }
}

@Composable
internal fun EffectMenuReflectionIcon(tint: Color, iconSize: Dp = 16.dp) {
    // "Reflejo" — mismo glifo que ReflectionModuleIcon en
    // EditorBottomBar.kt: forma arriba, línea de espejo, y su reflejo
    // invertido y atenuado debajo.
    Canvas(modifier = Modifier.size(iconSize)) {
        val topPath = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.05f)
            lineTo(size.width * 0.85f, size.height * 0.46f)
            lineTo(size.width * 0.15f, size.height * 0.46f)
            close()
        }
        drawPath(topPath, color = tint)
        drawLine(
            color = tint.copy(alpha = 0.5f),
            start = Offset(0f, size.height * 0.52f),
            end = Offset(size.width, size.height * 0.52f),
            strokeWidth = size.minDimension * 0.06f
        )
        val reflectionPath = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.95f)
            lineTo(size.width * 0.85f, size.height * 0.58f)
            lineTo(size.width * 0.15f, size.height * 0.58f)
            close()
        }
        drawPath(reflectionPath, color = tint.copy(alpha = 0.35f))
    }
}

@Composable
internal fun EffectMenuDistortionIcon(tint: Color, iconSize: Dp = 16.dp) {
    // "Distorsión": una línea ondulada (la malla ya deformada) sobre una
    // línea recta y tenue (la malla original, de referencia) — la
    // diferencia entre las dos ES el efecto.
    Canvas(modifier = Modifier.size(iconSize)) {
        val wavePath = Path().apply {
            moveTo(0f, size.height * 0.36f)
            cubicTo(
                size.width * 0.25f, size.height * 0.06f,
                size.width * 0.25f, size.height * 0.62f,
                size.width * 0.5f, size.height * 0.36f
            )
            cubicTo(
                size.width * 0.75f, size.height * 0.10f,
                size.width * 0.75f, size.height * 0.62f,
                size.width, size.height * 0.36f
            )
        }
        drawPath(wavePath, color = tint, style = Stroke(width = size.minDimension * 0.13f))
        drawLine(
            color = tint.copy(alpha = 0.35f),
            start = Offset(0f, size.height * 0.80f),
            end = Offset(size.width, size.height * 0.80f),
            strokeWidth = size.minDimension * 0.10f
        )
    }
}

private fun layerBoundingQuadPx(
    translateX: Float,
    translateY: Float,
    scaleVal: Float,
    rotationDeg: Float,
    parallaxFactor: Float,
    layerWidthPx: Int,
    layerHeightPx: Int,
    boxWidthPx: Float,
    boxHeightPx: Float,
    // Estirado independiente de ancho/alto (manijas "estirar ancho" /
    // "estirar alto" del modo "Edición > Imagen") — 1f = sin estirar,
    // idéntico al comportamiento de antes de que existieran. Ver el
    // comentario completo en CameraFrame.kt.
    scaleXVal: Float = 1f,
    scaleYVal: Float = 1f,
    // BUG REAL corregido (13/sep/2026, capturas "Cr7" — "la imagen sale
    // fuera del marco"): faltaban acá, ver el KDoc completo de
    // [PerspectiveCameraMath.projectQuadCornersNdc]. Default 0f/1.6f/BASE_EYE_Z
    // reproduce el comportamiento previo (cero regresión) para cualquier
    // capa en reposo sin tilt — que es lo único que este marco sabía
    // dibujar antes de este fix.
    tiltXDeg: Float = 0f,
    tiltYDeg: Float = 0f,
    dollyZoom: Float = 0f,
    dollyRange: Float = PerspectiveCameraMath.DOLLY_RANGE,
    baseEyeZ: Float = PerspectiveCameraMath.BASE_EYE_Z
): List<Offset>? {
    if (boxWidthPx <= 0f || boxHeightPx <= 0f || layerWidthPx <= 0 || layerHeightPx <= 0) return null

    // ARREGLADO (se mantiene): scaleXVal/scaleYVal pueden ser negativos
    // (capa volteada) — se toma el valor absoluto para que el marco de
    // selección no cambie de lado al voltear (ver historial de este
    // archivo). El flip real de la imagen lo maneja el UV mapping, no la
    // geometría del marco.
    //
    // BUG REAL corregido — reportado con captura por el usuario: este
    // marco modelaba la capa como un RECTÁNGULO rotado solo en Z. Eso
    // era ya incompleto (ver el fix anterior de `depthCompensation`,
    // ADR-007) pero seguía sin capturar `tiltXDeg`/`tiltYDeg` —
    // [LayerDrawer.drawLayer] SÍ rota en X/Y antes de proyectar con la
    // cámara de perspectiva real. Confirmado numéricamente (no es una
    // suposición): con tilt distinto de 0, las 4 esquinas proyectadas
    // dejan de formar un rectángulo — forman un TRAPECIO (cada esquina
    // quedaba a una profundidad Z distinta tras la rotación, así que la
    // división de perspectiva las escala de forma distinta) — ver el
    // KDoc completo en [PerspectiveCameraMath.projectQuadCornersNdc].
    // Ningún rectángulo rotado en 2D puede representar eso, sin importar
    // qué tan bien calibrado esté `depthCompensation`. La corrección de
    // fondo es reemplazar la aproximación rectangular por la proyección
    // real de las 4 esquinas a través del MISMO pipeline (model → view →
    // proyección) que usa `drawLayer` — es decir, dejar de aproximar y
    // calcular lo mismo que GL calcula.
    val ndcCorners = PerspectiveCameraMath.projectQuadCornersNdc(
        translateX = translateX,
        translateY = translateY,
        scaleVal = scaleVal,
        scaleXVal = kotlin.math.abs(scaleXVal),
        scaleYVal = kotlin.math.abs(scaleYVal),
        rotationDeg = rotationDeg,
        tiltXDeg = tiltXDeg,
        tiltYDeg = tiltYDeg,
        parallaxFactor = parallaxFactor,
        dollyZoom = dollyZoom,
        dollyRange = dollyRange,
        baseEyeZ = baseEyeZ,
        imageWidthPx = layerWidthPx,
        imageHeightPx = layerHeightPx,
        viewportWidthPx = boxWidthPx,
        viewportHeightPx = boxHeightPx
    ) ?: return null
    // [PerspectiveCameraMath.projectQuadCornersNdc] devuelve el mismo
    // orden que `LayerDrawer.quadVertices`: arriba-izq, abajo-izq,
    // arriba-der, abajo-der. Este marco necesita arriba-izq, arriba-der,
    // abajo-der, abajo-izq (para poder dibujar el contorno en un solo
    // trazo) — se reordena acá, no se cambia el orden "de fuente única"
    // en PerspectiveCameraMath.
    val (topLeft, bottomLeft, topRight, bottomRight) = ndcCorners
    val localCorners = listOf(topLeft, topRight, bottomRight, bottomLeft)
    return localCorners.map { ndc ->
        val ndcX = ndc[0]
        val ndcY = ndc[1]
        // NDC (centro, -1..1, Y arriba) -> píxeles de pantalla (origen
        // arriba-izquierda) — el inverso exacto de lo que hace hitTestLayerAt.
        Offset(
            x = (ndcX + 1f) / 2f * boxWidthPx,
            y = (1f - ndcY) / 2f * boxHeightPx
        )
    }
}

/**
 * A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido (reportado con
 * captura: el marco de selección con sus 8 manijas — ✓, ×, flechas de
 * redimensionar, restaurar, reordenar — se dibuja EXACTO en la
 * esquina/borde geométrico real de la capa (ver [layerBoundingQuadPx]).
 * En cuanto la capa se acerca al borde de la pantalla — arrastrada o
 * agrandada — la manija correspondiente queda parcial o totalmente
 * FUERA del `Canvas` que las dibuja, y ese `Canvas` recorta cualquier
 * cosa dibujada más allá de su propio tamaño: la manija no se "acerca"
 * al borde, directamente DESAPARECE (justo lo que se ve en la captura,
 * con la manija de "estirar ancho" prácticamente esfumada en el borde
 * derecho).
 *
 * Esta función recibe las 8 posiciones YA calculadas a partir de las 4
 * esquinas reales (el mismo [layerBoundingQuadPx] de siempre — nada
 * cambia en cómo se calcula el marco/línea de contorno, que sigue
 * mostrando el encuadre real de la capa tal cual es) y devuelve una
 * copia con cada una recortada ("clamp") para que el círculo completo
 * de la manija (radio + el anillo del borde + un pequeño margen) quepa
 * siempre entero dentro del `Canvas` — nunca pegada literalmente al
 * filo, nunca cortada.
 *
 * Se llama desde UN SOLO LUGAR conceptual — el dibujo, el hit-test de
 * gestos (tocar/arrastrar una manija), el intercambio al reordenar, y
 * el ancla de los menús flotantes ("Solo aquí"/"Todos",
 * "¿Restablecer?", etc.) — así los cuatro coinciden siempre en la
 * MISMA posición visible en pantalla. Antes cada uno recalculaba las 8
 * posiciones por su cuenta a partir de las esquinas crudas; ahora todos
 * pasan por acá, así que si una manija se ve en un punto, tocar ESE
 * mismo punto es garantizado que la activa — nunca "se ve acá pero hay
 * que tocar más allá, donde en realidad está el borde real de la capa
 * (posiblemente fuera de pantalla)".
 */
// internal (no private): visibilidad ampliada por el mismo motivo que
// [HandlePosition] — ver [ClampedHandleSlotsTest].
internal fun clampedHandleSlots(
    corners: List<Offset>,
    canvasSize: Size,
    marginPx: Float
): Map<HandlePosition, Offset> {
    val topLeft = corners[0]
    val topRight = corners[1]
    val bottomRight = corners[2]
    val bottomLeft = corners[3]

    // BUG REAL corregido (14/sep/2026, captura "Cr7" con zoom aplicado —
    // "el marco se ve desconectado de la imagen"): esta función clampeaba
    // los 4 PUNTOS MEDIOS (TOP_MID/RIGHT_MID/BOTTOM_MID/LEFT_MID) a partir
    // del promedio de las esquinas CRUDAS (sin recortar), y LUEGO les
    // aplicaba su propio `coerceIn` independiente — el mismo recorte por
    // eje que ya se le aplica a cada esquina por separado, pero calculado
    // sobre datos distintos. Resultado matemático: en cuanto una capa se
    // escala/acerca lo bastante como para que sus esquinas reales queden
    // afuera del Canvas (exactamente lo que pasa al hacer zoom con el
    // pellizco), la esquina y el punto medio de un mismo lado dejan de
    // recortarse hacia el mismo punto — el punto medio deja de caer sobre
    // la línea entre sus dos esquinas ya recortadas, y la manija de ese
    // lado se ve "flotando" separada del contorno, en vez de pegada a él.
    // Con una capa en reposo (esquinas todas dentro del Canvas, el caso de
    // toda la vida) el bug es invisible porque ninguna coordenada llega a
    // tocar el `coerceIn` — por eso no aparecía antes de que el usuario
    // empezara a probar zoom/escala fuerte.
    //
    // Corrección real (no un ajuste de margen): las 4 esquinas se
    // recortan PRIMERO, una sola vez; los 4 puntos medios se calculan
    // DESPUÉS, como el promedio de esas mismas esquinas YA recortadas —
    // nunca a partir de las crudas. Así un punto medio SIEMPRE cae, por
    // construcción geométrica, exactamente a mitad de camino entre las
    // dos manijas de esquina que lo rodean, sin importar cuánto se haya
    // tenido que recortar cada una. Contorno y manijas vuelven a ser,
    // aunque la capa esté muy escalada o fuera de cuadro, una sola figura
    // consistente en vez de dos cálculos independientes que coinciden
    // solo por casualidad cuando no hace falta recortar nada.
    //
    // Si todavía no hay un tamaño de canvas válido (layout sin medir), no
    // hay contra qué recortar — se devuelven las 4 esquinas crudas y sus
    // puntos medios crudos, sin tocar nada.
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) {
        return mapOf(
            HandlePosition.TOP_LEFT to topLeft,
            HandlePosition.TOP_MID to Offset((topLeft.x + topRight.x) / 2f, (topLeft.y + topRight.y) / 2f),
            HandlePosition.TOP_RIGHT to topRight,
            HandlePosition.RIGHT_MID to Offset((topRight.x + bottomRight.x) / 2f, (topRight.y + bottomRight.y) / 2f),
            HandlePosition.BOTTOM_RIGHT to bottomRight,
            HandlePosition.BOTTOM_MID to Offset((bottomRight.x + bottomLeft.x) / 2f, (bottomRight.y + bottomLeft.y) / 2f),
            HandlePosition.BOTTOM_LEFT to bottomLeft,
            HandlePosition.LEFT_MID to Offset((topLeft.x + bottomLeft.x) / 2f, (topLeft.y + bottomLeft.y) / 2f)
        )
    }
    val minX = marginPx
    val maxX = (canvasSize.width - marginPx).coerceAtLeast(minX)
    val minY = marginPx
    val maxY = (canvasSize.height - marginPx).coerceAtLeast(minY)
    fun clampCorner(offset: Offset) = Offset(
        x = offset.x.coerceIn(minX, maxX),
        y = offset.y.coerceIn(minY, maxY)
    )

    val clampedTopLeft = clampCorner(topLeft)
    val clampedTopRight = clampCorner(topRight)
    val clampedBottomRight = clampCorner(bottomRight)
    val clampedBottomLeft = clampCorner(bottomLeft)

    return mapOf(
        HandlePosition.TOP_LEFT to clampedTopLeft,
        HandlePosition.TOP_MID to Offset(
            (clampedTopLeft.x + clampedTopRight.x) / 2f,
            (clampedTopLeft.y + clampedTopRight.y) / 2f
        ),
        HandlePosition.TOP_RIGHT to clampedTopRight,
        HandlePosition.RIGHT_MID to Offset(
            (clampedTopRight.x + clampedBottomRight.x) / 2f,
            (clampedTopRight.y + clampedBottomRight.y) / 2f
        ),
        HandlePosition.BOTTOM_RIGHT to clampedBottomRight,
        HandlePosition.BOTTOM_MID to Offset(
            (clampedBottomRight.x + clampedBottomLeft.x) / 2f,
            (clampedBottomRight.y + clampedBottomLeft.y) / 2f
        ),
        HandlePosition.BOTTOM_LEFT to clampedBottomLeft,
        HandlePosition.LEFT_MID to Offset(
            (clampedTopLeft.x + clampedBottomLeft.x) / 2f,
            (clampedTopLeft.y + clampedBottomLeft.y) / 2f
        )
    )
}

/**
 * Convierte un punto de pantalla (píxeles, origen arriba-izquierda) en
 * coordenadas UV [0,1]x[0,1] LOCALES de una capa — la inversa de
 * [layerBoundingQuadPx]/[hitTestLayerAt]: mismo cálculo de encuadre
 * ("fit") + centro + rotación + estirado que usan esas dos funciones,
 * pero en vez de devolver un booleano o un cuadrilátero, resuelve
 * directamente qué punto de la TEXTURA (0,0 = esquina superior
 * izquierda, 1,1 = esquina inferior derecha) cae bajo el dedo.
 *
 * Único consumidor real: el gesto de pintar de la categoría "Distorsión"
 * (ver [DistortionGestureBridge] y el bloque `distortionBridge.active`
 * en el `pointerInput` principal) — necesita saber, para cada muestra
 * del trazo, sobre qué UV de la imagen cayó el dedo, para poder llamar a
 * `DistortionField.applyStroke` con esas coordenadas. Devuelve `null`
 * si el encuadre todavía no tiene tamaño válido (layout no medido) —
 * nunca si el punto cae fuera de la capa: a diferencia de
 * `hitTestLayerAt`, acá SÍ interesa devolver un UV (recortado a 0..1)
 * aunque el dedo se vaya un poco afuera del marco mientras arrastra,
 * para que el trazo no se "corte" justo en el borde de la imagen.
 */
private fun screenPointToLayerUv(
    point: Offset,
    boxWidthPx: Float,
    boxHeightPx: Float,
    translateX: Float,
    translateY: Float,
    scaleVal: Float,
    rotationDeg: Float,
    parallaxFactor: Float,
    layerWidthPx: Int,
    layerHeightPx: Int,
    scaleXVal: Float = 1f,
    scaleYVal: Float = 1f,
    // Mismo motivo y mismo fix que en [layerBoundingQuadPx]: ver el KDoc
    // completo de [PerspectiveCameraMath.projectQuadCornersNdc].
    tiltXDeg: Float = 0f,
    tiltYDeg: Float = 0f,
    dollyZoom: Float = 0f,
    dollyRange: Float = PerspectiveCameraMath.DOLLY_RANGE,
    baseEyeZ: Float = PerspectiveCameraMath.BASE_EYE_Z
): Offset? {
    if (boxWidthPx <= 0f || boxHeightPx <= 0f || layerWidthPx <= 0 || layerHeightPx <= 0) return null

    // BUG REAL corregido — igual que en [layerBoundingQuadPx]: la fórmula
    // afín anterior (rotar+escalar) solo podía representar un rectángulo.
    // Con `tiltXDeg`/`tiltYDeg` distinto de 0 el cuadrilátero real que
    // dibuja GL es un TRAPECIO (ver el KDoc de
    // [PerspectiveCameraMath.projectQuadCornersNdc]), así que el trazo de
    // distorsión "caía" en el punto equivocado de la textura apenas había
    // tilt. La corrección: proyectar las 4 esquinas reales (mismo cálculo
    // que el marco de selección) y resolver la homografía plana exacta
    // (cuadrado unitario -> ese cuadrilátero) en vez de asumir un
    // rectángulo — ver [QuadHomography].
    val ndcCorners = PerspectiveCameraMath.projectQuadCornersNdc(
        translateX = translateX,
        translateY = translateY,
        scaleVal = scaleVal,
        scaleXVal = kotlin.math.abs(scaleXVal),
        scaleYVal = kotlin.math.abs(scaleYVal),
        rotationDeg = rotationDeg,
        tiltXDeg = tiltXDeg,
        tiltYDeg = tiltYDeg,
        parallaxFactor = parallaxFactor,
        dollyZoom = dollyZoom,
        dollyRange = dollyRange,
        baseEyeZ = baseEyeZ,
        imageWidthPx = layerWidthPx,
        imageHeightPx = layerHeightPx,
        viewportWidthPx = boxWidthPx,
        viewportHeightPx = boxHeightPx
    ) ?: return null
    // Orden de [PerspectiveCameraMath.projectQuadCornersNdc]: arriba-izq,
    // abajo-izq, arriba-der, abajo-der — [QuadHomography.fromUnitSquare]
    // necesita arriba-izq, arriba-der, abajo-der, abajo-izq (UV (0,0),
    // (1,0), (1,1), (0,1) en ese orden).
    val (topLeft, bottomLeft, topRight, bottomRight) = ndcCorners
    val coeffs = QuadHomography.fromUnitSquare(topLeft, topRight, bottomRight, bottomLeft)

    val ndcX = (point.x / boxWidthPx) * 2f - 1f
    val ndcY = 1f - (point.y / boxHeightPx) * 2f
    val uv = QuadHomography.mapPointToUv(coeffs, ndcX, ndcY) ?: return null
    var u = uv[0]
    var v = uv[1]
    // Capa volteada (flip horizontal/vertical, ver comentario sobre el
    // signo de scaleX/scaleY en hitTestLayerAt más abajo): el UV
    // también tiene que reflejarse, si no un trazo en una capa volteada
    // pintaría del lado equivocado de la imagen.
    if (scaleXVal < 0f) u = 1f - u
    if (scaleYVal < 0f) v = 1f - v
    return Offset(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
}

/**
 * Determina qué capa está "debajo" de un toque en el preview, para poder
 * seleccionarla tocándola directamente en el canvas — no solo desde su
 * fila en el timeline.
 *
 * Prueba la CAJA COMPLETA del sprite ya transformada (posición, escala y
 * rotación en el plano) — el mismo margen/rectángulo que dibuja el marco
 * violeta de selección (ver [layerBoundingQuadPx]), replicando la misma
 * geometría que usa el motor GL para el plano z=0. A propósito NO se
 * prueba contra el contenido real (alfa) del PNG: la capa se selecciona
 * por su "lienzo" completo, sin importar el tamaño, forma o cuánto margen
 * transparente tenga la imagen — así el criterio de selección es siempre
 * el mismo rectángulo que el usuario VE como marco, sin sorpresas, y sin
 * el costo (y la demora) de decodificar cada PNG para leer su canal alfa.
 *
 * [preferredLayerId] (la capa ya seleccionada) se revisa PRIMERO: si el
 * toque cae dentro de su caja, se respeta esa selección y se puede
 * mover, sin importar qué otra capa esté encima en ese mismo punto —
 * igual que "arrastrar por el marco" en cualquier editor. Solo si el
 * toque NO alcanza esa caja se hace el barrido normal de más arriba
 * (zIndex mayor) a más abajo, devolviendo la primera capa cuya caja
 * llega hasta ahí.
 *
 * *Nota de arquitectura (Etapa 7):* esta función SÍ llama
 * `layer.cameraTrack.frameAt(...)` directo, a diferencia de los demás
 * lugares de este archivo (que piden `viewModel.frameAt(...)`). Es
 * intencional: `hitTestLayerAt` es una función pura de nivel de archivo,
 * sin `viewModel` en su firma a propósito — recibe `layers` como
 * parámetro y no depende de nada más, lo que la hace fácil de testear
 * sola y barata de llamar en cada movimiento del dedo. Pedirle un
 * `EditorViewModel` solo para interpolar un frame sería acoplarla a algo
 * que no necesita — el objetivo de sacar `engine.*` de la UI es evitar
 * que la UI POSEA o DECIDA sobre objetos del motor (ver Etapa 3), no
 * prohibir que una función pura reciba modelos de dominio inmutables
 * (`Layer`) y calcule con ellos. Esta función no posee nada: recibe,
 * calcula, devuelve.
 */
private fun hitTestLayerAt(
    tapOffset: Offset,
    boxWidthPx: Float,
    boxHeightPx: Float,
    layers: List<Layer>,
    playheadMs: Long,
    preferredLayerId: String? = null
): String? {
    if (boxWidthPx <= 0f || boxHeightPx <= 0f) return null

    // Del punto tocado (píxeles de pantalla, origen arriba-izquierda) a
    // coordenadas NDC (origen centro, rango -1..1, eje Y hacia arriba) —
    // el mismo espacio en el que vive la geometría que arma el GL.
    // (`viewportAspect` ya no hace falta acá arriba: ahora vive DENTRO de
    // [PerspectiveCameraMath.projectQuadCornersNdc], que lo recalcula a
    // partir de `boxWidthPx`/`boxHeightPx` — una sola fuente de verdad
    // para ese cálculo también en el hit-test, no una copia aparte.)
    val ndcX = (tapOffset.x / boxWidthPx) * 2f - 1f
    val ndcY = 1f - (tapOffset.y / boxHeightPx) * 2f

    fun isHit(layer: Layer): Boolean {
        if (!layer.visible || layer.locked || layer.widthPx <= 0 || layer.heightPx <= 0) return false
        val frame = layer.cameraTrack.frameAt(playheadMs)

        // BUG REAL corregido — mismo motivo y mismo fix que en
        // [layerBoundingQuadPx]/[screenPointToLayerUv]: la caja de
        // hit-test anterior era un RECTÁNGULO (rotado en Z, con
        // `depthCompensation` — ver ADR-007), pero `frame.tiltXDeg`/
        // `tiltYDeg` (que sí aplica [LayerDrawer.drawLayer], ver el KDoc
        // de [PerspectiveCameraMath.projectQuadCornersNdc]) convierte el
        // cuadrilátero real en un TRAPECIO — ningún rectángulo puede
        // representarlo, sin importar qué tan bien calibrado esté el
        // resto. La corrección: proyectar las 4 esquinas reales (mismo
        // cálculo que usa el marco violeta) y probar el toque contra ESE
        // cuadrilátero exacto, resolviendo su UV vía homografía plana
        // ([QuadHomography]) — "adentro" es simplemente `u,v` dentro de
        // `[0,1]`, igual de simple que antes pero ahora exacto también
        // con tilt. `frame.scaleX`/`scaleY` se pasan en valor absoluto
        // (ver comentario histórico más abajo, mismo motivo de siempre:
        // el signo solo importa para el flip visual, no para "¿cae
        // adentro?").
        val ndcCorners = PerspectiveCameraMath.projectQuadCornersNdc(
            translateX = frame.translateX,
            translateY = frame.translateY,
            scaleVal = frame.scale,
            scaleXVal = kotlin.math.abs(frame.scaleX),
            scaleYVal = kotlin.math.abs(frame.scaleY),
            rotationDeg = frame.rotationDeg,
            tiltXDeg = frame.tiltXDeg,
            tiltYDeg = frame.tiltYDeg,
            parallaxFactor = layer.parallaxFactor,
            dollyZoom = frame.dollyZoom,
            dollyRange = PerspectiveCameraMath.DOLLY_RANGE,
            baseEyeZ = PerspectiveCameraMath.BASE_EYE_Z,
            imageWidthPx = layer.widthPx,
            imageHeightPx = layer.heightPx,
            viewportWidthPx = boxWidthPx,
            viewportHeightPx = boxHeightPx
        ) ?: return false
        val (topLeft, bottomLeft, topRight, bottomRight) = ndcCorners
        val coeffs = QuadHomography.fromUnitSquare(topLeft, topRight, bottomRight, bottomLeft)
        val uv = QuadHomography.mapPointToUv(coeffs, ndcX, ndcY) ?: return false
        return uv[0] in 0f..1f && uv[1] in 0f..1f
    }

    if (preferredLayerId != null) {
        val preferred = layers.firstOrNull { it.id == preferredLayerId }
        if (preferred != null && isHit(preferred)) return preferred.id
    }

    for (layer in layers.sortedByDescending { it.zIndex }) {
        if (layer.id == preferredLayerId) continue // ya se probó arriba
        if (isHit(layer)) return layer.id
    }
    return null
}

/**
 * Especificación numérica de la cuadrícula de composición: columnas y
 * filas totalmente INDEPENDIENTES entre sí. Se comparte entre TODAS las
 * formas de [GridShape] — cambiar de forma no resetea estos números, así
 * que pasar de "Rectángulo" a "Redondo" mantiene la misma densidad que
 * ya tenías configurada.
 */
private data class GridSpec(val columns: Int, val rows: Int)

/** Rango permitido para columnas y filas en el editor numérico del menú. */
private val GRID_AXIS_RANGE = 2..16

/**
 * Las formas de guía de composición disponibles — estándar real de apps
 * premium de edición (Lightroom, Photoshop y editores de video pro traen
 * variantes parecidas: cuadrícula, diagonal, diagonal cruzada, etc.).
 * Cada forma declara cómo se llaman sus ejes editables:
 *  - `axisXLabel` es el eje principal — columnas, "tamaño" de celda, o la
 *    única cantidad de líneas que tiene sentido en las diagonales de una
 *    sola dirección. Es null cuando la forma NO tiene ningún número que
 *    editar (caso de [CROSS], que siempre es la misma cruz centrada).
 *  - `axisYLabel` es null cuando la forma geométricamente solo tiene UN
 *    grado de libertad (o ninguno) — una diagonal en una sola dirección
 *    no tiene "filas" propias, son las mismas líneas nomás con más o
 *    menos cantidad — en ese caso [GridMenu] oculta el segundo stepper
 *    en vez de mostrar un control que no haría nada, que sería peor UX
 *    que directamente no mostrarlo.
 */
private enum class GridShape(val label: String, val axisXLabel: String?, val axisYLabel: String?) {
    RECTANGLE("Rectángulo", "Columnas", "Filas"),
    DIAGONAL_RIGHT("Diagonal ↗", "Líneas", null),
    DIAGONAL_LEFT("Diagonal ↖", "Líneas", null),
    DIAGONAL_CROSS("Diagonal cruzada", "Columnas", "Filas"),
    ROUND("Redondo", "Columnas", "Filas"),
    // Cuadrícula de celdas cuadradas parejas (estilo grilla de Blender),
    // pero plana/derecha — sin la perspectiva de "piso" del viewport 3D,
    // tal como pediste. Un solo eje ("Tamaño") define el lado de cada
    // celda; al ser cuadrada, ese mismo número gobierna ambas
    // direcciones — no necesita un segundo eje independiente.
    SQUARE("Cuadrado", "Tamaño", null),
    // Cruz de composición centrada — una sola línea vertical y una sola
    // horizontal cruzando el centro exacto del cuadro, sin densidad
    // configurable (no tiene ningún número que editar).
    CROSS("Cruz", null, null)
}

/** Orden en el que aparecen las formas en el carrusel de [GridMenu]. */
private val GRID_SHAPES = GridShape.entries

/**
 * Cuántas formas se ven a la vez en el carrusel — el mismo hueco que
 * antes ocupaban los 3 presets de densidad, para no agrandar el menú ni
 * un dp, tal cual pediste.
 */
// Rango de escala para las manijas de "Edición > Imagen" (esquina inf.
// derecha = escala uniforme, lateral derecha = solo ancho, inferior
// centro = solo alto) y para el pellizco de dos dedos. ARREGLADO: antes
// el rango iba de 0.2x a 5x — un tope bastante bajo que se sentía en
// cualquier uso real (agrandar una capa para un fondo, o achicarla para
// un detalle chico) y que ningún editor premium (CapCut, Canva,
// Photoshop) impone en la práctica. Ahora el rango es lo bastante amplio
// como para no sentirse como un límite en absoluto — solo existe un piso
// y un techo numéricos para que el motor de render nunca reciba una
// escala 0 o negativa (eso sí rompería el dibujo), no para restringir
// ningún uso real.
private const val MIN_LAYER_SCALE = 0.03f
// AMPLIADO de 40f a 1024f — a pedido explícito del usuario, para que el
// nuevo módulo "Zoom X" (Módulos > Cámara, ver ZoomXLevels.kt) pueda
// llegar hasta su última parada (1024×). Se sube ACÁ, el único techo real
// del campo `scale` en todo el editor, en vez de agregarle a Zoom X su
// propio límite paralelo — así el pellizco de dos dedos, las manijas de
// escala y Zoom X comparten SIEMPRE el mismo rango válido para el mismo
// campo, sin que dos mecanismos distintos puedan dejar `scale` en estados
// contradictorios entre sí.
private const val MAX_LAYER_SCALE = 1024f

// --- La velocidad de movimiento del joystick de Control (FASE 1) ya NO
// vive acá: se movió a JoystickLayerMover.kt junto con el resto de la
// física del joystick, desacoplada de este archivo (ver el comentario
// grande en ese archivo para el detalle completo, incluyendo el bug de
// unidades que tenía la versión anterior de esta constante).

// --- Reordenar manijas del marco de edición (manija "lateral izquierda,
// medio", función reordenar/confirmar): qué función hace cada una de las
// 8 manijas (las 7 de siempre + esta) es configurable — el usuario puede
// arrastrar una manija sobre otra para intercambiarlas de lugar.
// [HandlePosition] son los 8 lugares físicos donde puede vivir una
// manija — ANTES eran 7 + 1 posición fija aparte ("lateral izquierda,
// medio", que nunca participaba del intercambio); ARREGLADO A PEDIDO
// ("todos rotan y cambian al arrastrar pero este no se mueve, quiero que
// también se reordene, claro, sin confirmar"): ahora [LEFT_MID] es una
// posición del mapa como cualquier otra, así que también se puede
// arrastrar a cualquier otro lugar (y cualquier otra manija puede
// terminar ahí) — mover la manija de reordenar de lugar solo la
// reposiciona, igual que a las demás, sin confirmar ni salir del modo
// por sí sola (eso sigue siendo un TAP simple sobre la posición que
// tenga asignado el rol [LayerHandleRole.REORDER] en cada momento, ya
// no hardcodeado a "lateral izquierda, medio"). [LayerHandleRole] es qué
// función hace cada una. [DEFAULT_HANDLE_ORDER] es el orden con el que
// arranca cualquier capa nueva o cualquier proyecto que nunca reordenó
// nada.
// Margen COMPLETO de recorte para las 8 manijas del marco de selección
// (ver [clampedHandleSlots], junto a [layerBoundingQuadPx]) — el radio
// visual del badge (11dp) + su anillo de borde (1.6dp) + un colchón
// extra (16dp) para que nunca quede pegado literalmente al filo de la
// pantalla. ÚNICA constante, usada TAL CUAL en las 4 llamadas a
// [clampedHandleSlots] (dibujo, hit-test de gestos, chequeo de pincel
// de distorsión, ancla de menús) — antes cada sitio recalculaba su
// propio margen a mano y dos de ellos (hit-test y chequeo de pincel)
// usaban solo el colchón (16dp) sin sumar el radio+anillo real del
// badge (28.6dp), un desfase real entre dónde se VE la manija y dónde
// hay que TOCARLA para activarla — más chico que el bug original
// (donde directamente desaparecía), pero el mismo tipo de problema:
// dibujo y gesto resolviendo la posición por separado. Con una sola
// constante compartida, ese desfase no puede volver a aparecer.
private val HANDLE_BADGE_CLAMP_MARGIN_DP = 11.dp + 1.6.dp + 16.dp
// internal (no private): [ClampedHandleSlotsTest] necesita poder
// construir el mapa de resultado por nombre de posición para verificar,
// con datos reales, que cada punto medio cae exactamente sobre la línea
// entre sus dos esquinas ya recortadas (ver el fix de 14/sep/2026 en
// [clampedHandleSlots]) — sin este cambio de visibilidad el test no
// puede referirse a estos 8 valores desde fuera de este archivo.
internal enum class HandlePosition { TOP_LEFT, TOP_MID, TOP_RIGHT, RIGHT_MID, BOTTOM_RIGHT, BOTTOM_MID, BOTTOM_LEFT, LEFT_MID }
private enum class LayerHandleRole { EDIT, DELETE, ROTATE, RESIZE_UNIFORM, RESIZE_WIDTH, RESIZE_HEIGHT, RESTORE, REORDER }
// Alcance elegido en la mini-ventana que abre la manija de reordenar:
// "Solo aquí" guarda el nuevo orden únicamente para la capa que se
// estaba editando; "Todos" lo guarda como el orden por defecto de
// cualquier capa que no tenga ya un orden propio guardado.
private enum class HandleReorderScope { ONLY_HERE, ALL }
private val DEFAULT_HANDLE_ORDER: Map<HandlePosition, LayerHandleRole> = mapOf(
    HandlePosition.TOP_LEFT to LayerHandleRole.EDIT,
    HandlePosition.TOP_MID to LayerHandleRole.RESTORE,
    HandlePosition.TOP_RIGHT to LayerHandleRole.DELETE,
    HandlePosition.RIGHT_MID to LayerHandleRole.RESIZE_WIDTH,
    HandlePosition.BOTTOM_RIGHT to LayerHandleRole.RESIZE_UNIFORM,
    HandlePosition.BOTTOM_MID to LayerHandleRole.RESIZE_HEIGHT,
    HandlePosition.BOTTOM_LEFT to LayerHandleRole.ROTATE,
    HandlePosition.LEFT_MID to LayerHandleRole.REORDER
)

// --- Puente entre el orden de manijas de esta pantalla (enums privados
// HandlePosition/LayerHandleRole) y el formato que persiste EditorViewModel/
// ProjectStorage (Map<String, String>, ver [EditorViewModel.updateHandleOrder]).
// Antes de esto no existía ninguna conversión: el orden reordenado vivía
// solo en las variables `remember` de esta pantalla y nunca llegaba al
// ViewModel, así que se perdía al cerrar y reabrir el proyecto. `decode*`
// ignora silenciosamente cualquier clave/valor que no matchee un enum
// vigente (por ejemplo si en el futuro se agrega o saca un rol/posición)
// en vez de romper la carga del proyecto.
private fun encodeHandleOrder(order: Map<HandlePosition, LayerHandleRole>): Map<String, String> =
    order.entries.associate { (position, role) -> position.name to role.name }

private fun decodeHandleOrder(order: Map<String, String>): Map<HandlePosition, LayerHandleRole> =
    order.entries.mapNotNull { (positionName, roleName) ->
        val position = runCatching { HandlePosition.valueOf(positionName) }.getOrNull()
        val role = runCatching { LayerHandleRole.valueOf(roleName) }.getOrNull()
        if (position != null && role != null) position to role else null
    }.toMap()

private fun decodeHandleOrderPerLayer(
    perLayer: Map<String, Map<String, String>>
): Map<String, Map<HandlePosition, LayerHandleRole>> =
    perLayer.mapValues { (_, order) -> decodeHandleOrder(order) }

// --- Rotación libre 360°: antes `rotation` se recortaba con
// .coerceIn(-180f, 180f), lo que hacía que el giro se "trabara" en seco
// al llegar a ±180° (el dedo seguía moviéndose pero la capa ya no
// respondía). Pedido explícito: que gire completo, sin tope, en
// cualquier dirección. En vez de dejar crecer el float sin límite
// (impreciso a largo plazo y feo de mostrar en el slider de -180..180),
// se NORMALIZA a un rango equivalente de -180 a 180 después de cada
// actualización — mismo ángulo visual, mismo comportamiento fluido, sin
// límite de vueltas y sin que el número crezca indefinidamente.
private fun normalizeRotationDeg(angle: Float): Float {
    var a = angle % 360f
    if (a > 180f) a -= 360f
    if (a <= -180f) a += 360f
    return a
}

private const val GRID_CAROUSEL_VISIBLE = 3

/**
 * Posiciones en píxeles (del box del canvas) de las líneas de guía de
 * `shape`/`spec` — SOLO para las formas rectilíneas (líneas rectas
 * horizontales/verticales: [GridShape.RECTANGLE], [GridShape.SQUARE],
 * [GridShape.CROSS]), que son las que tiene sentido "imantar" al
 * arrastrar una capa. Las formas diagonales/redondas ([GridShape.
 * DIAGONAL_RIGHT]/[DIAGONAL_LEFT]/[DIAGONAL_CROSS]/[ROUND]) no tienen
 * líneas rectas paralelas a los ejes, así que no participan del snap —
 * quedan puramente visuales, como antes. Mismas fórmulas exactas que
 * [drawGridGuides] usa para DIBUJAR cada forma, para que el imán caiga
 * siempre justo sobre la línea que el usuario ve, sin desvíos.
 */
private fun gridSnapLinesPx(
    shape: GridShape,
    spec: GridSpec,
    boxWidthPx: Float,
    boxHeightPx: Float
): Pair<List<Float>, List<Float>> {
    val columns = spec.columns.coerceAtLeast(1)
    val rows = spec.rows.coerceAtLeast(1)
    return when (shape) {
        GridShape.RECTANGLE -> {
            val verticalLines = (1 until columns).map { boxWidthPx * it / columns }
            val horizontalLines = (1 until rows).map { boxHeightPx * it / rows }
            verticalLines to horizontalLines
        }
        GridShape.SQUARE -> {
            // Mismo cálculo de celda cuadrada + margen centrado que
            // drawGridGuides (rama SQUARE) — ver ese comentario para el
            // detalle de por qué se reparte el sobrante mitad/mitad.
            val cell = (boxWidthPx / columns).coerceAtLeast(1f)
            val verticalLines = mutableListOf<Float>()
            val colsFit = (boxWidthPx / cell).toInt().coerceAtLeast(1)
            val xMargin = (boxWidthPx - colsFit * cell) / 2f
            var x = xMargin + cell
            val xEnd = boxWidthPx - xMargin - cell * 0.5f
            while (x < xEnd) {
                verticalLines += x
                x += cell
            }
            val horizontalLines = mutableListOf<Float>()
            val rowsFit = (boxHeightPx / cell).toInt().coerceAtLeast(1)
            val yMargin = (boxHeightPx - rowsFit * cell) / 2f
            var y = yMargin + cell
            val yEnd = boxHeightPx - yMargin - cell * 0.5f
            while (y < yEnd) {
                horizontalLines += y
                y += cell
            }
            verticalLines to horizontalLines
        }
        GridShape.CROSS -> listOf(boxWidthPx / 2f) to listOf(boxHeightPx / 2f)
        else -> emptyList<Float>() to emptyList()
    }
}

/**
 * Semi-ancho/alto en píxeles del AABB (bounding box alineado a los ejes)
 * de la capa en pantalla — el mismo criterio de tamaño que usa
 * [hitTestLayerAt] (fitScaleX/Y según el aspect ratio de la imagen vs.
 * el del box, `scale`/`scaleX`/`scaleY` de las manijas), pero además
 * proyectado a través de la rotación: si la capa está girada, su caja
 * VISIBLE (la que el ojo ve como "los bordes de la imagen") es más
 * ancha/alta que su caja local sin rotar — es la fórmula estándar de
 * AABB de un rectángulo rotado (`|w·cosθ| + |h·sinθ|`). Necesario para
 * que el snap de BORDES (ver [snapTranslateToGrid]) enganche donde el
 * usuario realmente ve el borde, no donde estaría si la capa no
 * hubiera girado.
 */
private fun layerAabbHalfExtentsPx(
    imageWidthPx: Int,
    imageHeightPx: Int,
    scale: Float,
    scaleX: Float,
    scaleY: Float,
    rotationDeg: Float,
    boxWidthPx: Float,
    boxHeightPx: Float
): Pair<Float, Float> {
    if (imageWidthPx <= 0 || imageHeightPx <= 0 || boxWidthPx <= 0f || boxHeightPx <= 0f) return 0f to 0f
    val imageAspect = imageWidthPx.toFloat() / imageHeightPx.toFloat()
    val viewportAspect = boxWidthPx / boxHeightPx
    val fitScaleX: Float
    val fitScaleY: Float
    if (imageAspect > viewportAspect) {
        fitScaleX = 2f
        fitScaleY = 2f * viewportAspect / imageAspect
    } else {
        fitScaleY = 2f
        fitScaleX = 2f * imageAspect / viewportAspect
    }
    val localHalfWidthNdc = kotlin.math.abs(0.5f * fitScaleX * scale * scaleX)
    val localHalfHeightNdc = kotlin.math.abs(0.5f * fitScaleY * scale * scaleY)
    val rad = Math.toRadians(rotationDeg.toDouble())
    val cosA = kotlin.math.abs(cos(rad)).toFloat()
    val sinA = kotlin.math.abs(sin(rad)).toFloat()
    val aabbHalfWidthNdc = localHalfWidthNdc * cosA + localHalfHeightNdc * sinA
    val aabbHalfHeightNdc = localHalfWidthNdc * sinA + localHalfHeightNdc * cosA
    return (aabbHalfWidthNdc * boxWidthPx / 2f) to (aabbHalfHeightNdc * boxHeightPx / 2f)
}

/**
 * Snap magnético al arrastrar una capa con la cuadrícula activa — mismo
 * comportamiento estándar de apps premium de edición/diseño (Figma,
 * Photoshop, etc.): mientras CUALQUIERA de los 3 puntos de referencia
 * del objeto en cada eje — borde inicial, centro, o borde final — pasa
 * cerca de una línea de la cuadrícula (dentro de [snapThresholdPx]), el
 * objeto entero se corre lo justo para que ESE punto quede exactamente
 * sobre la línea. Cada eje (X e Y) se evalúa por separado — el objeto
 * puede engancharse por su borde izquierdo en X y por su centro en Y al
 * mismo tiempo, por ejemplo. Si ningún punto de un eje cae lo bastante
 * cerca de ninguna línea de ese eje, ese eje no se toca. Si no hay
 * ninguna línea lo bastante cerca en NINGÚN eje, devuelve
 * `translateX`/`translateY` sin tocar.
 *
 * `halfWidthPx`/`halfHeightPx` — semi-ancho/alto del AABB de la capa en
 * píxeles (ver [layerAabbHalfExtentsPx]) — son los que definen dónde
 * caen sus bordes izq/der/arriba/abajo a partir del centro.
 *
 * `parallaxFactor` entra en la cuenta porque el centro real en pantalla
 * de una capa con paralaje no es `translateX`/`translateY` directo —
 * ver la misma fórmula en [layerBoundingQuadPx] (`centerX = translateX *
 * parallaxFactor`). Con `parallaxFactor == 0` (o el box sin tamaño
 * todavía) no hay snap posible y se devuelve tal cual, para no dividir
 * por cero.
 */
private fun snapTranslateToGrid(
    translateX: Float,
    translateY: Float,
    parallaxFactor: Float,
    halfWidthPx: Float,
    halfHeightPx: Float,
    boxWidthPx: Float,
    boxHeightPx: Float,
    shape: GridShape,
    spec: GridSpec,
    snapThresholdPx: Float
): Pair<Float, Float> {
    if (parallaxFactor == 0f || boxWidthPx <= 0f || boxHeightPx <= 0f) return translateX to translateY
    val (verticalLines, horizontalLines) = gridSnapLinesPx(shape, spec, boxWidthPx, boxHeightPx)

    // Busca, entre los 3 puntos candidatos de un eje (borde inicial,
    // centro, borde final) y todas las líneas de ese eje, el corrimiento
    // (línea - candidato) MÁS CHICO en valor absoluto que además esté
    // dentro del umbral — ese corrimiento, aplicado al centro, es lo que
    // hace falta para que el candidato más cercano quede EXACTO sobre su
    // línea más cercana.
    fun bestShift(candidates: List<Float>, lines: List<Float>): Float? {
        var best: Float? = null
        for (candidate in candidates) {
            val nearestLine = lines.minByOrNull { kotlin.math.abs(it - candidate) } ?: continue
            val diff = nearestLine - candidate
            if (kotlin.math.abs(diff) <= snapThresholdPx && (best == null || kotlin.math.abs(diff) < kotlin.math.abs(best))) {
                best = diff
            }
        }
        return best
    }

    var snappedX = translateX
    if (verticalLines.isNotEmpty()) {
        val centerPxX = (translateX * parallaxFactor + 1f) / 2f * boxWidthPx
        val candidatesX = listOf(centerPxX - halfWidthPx, centerPxX, centerPxX + halfWidthPx)
        val shift = bestShift(candidatesX, verticalLines)
        if (shift != null) {
            val snappedNdcX = (centerPxX + shift) / boxWidthPx * 2f - 1f
            snappedX = (snappedNdcX / parallaxFactor).coerceIn(-2f, 2f)
        }
    }

    var snappedY = translateY
    if (horizontalLines.isNotEmpty()) {
        val centerPxY = (1f - translateY * parallaxFactor) / 2f * boxHeightPx
        val candidatesY = listOf(centerPxY - halfHeightPx, centerPxY, centerPxY + halfHeightPx)
        val shift = bestShift(candidatesY, horizontalLines)
        if (shift != null) {
            val snappedNdcY = 1f - 2f * (centerPxY + shift) / boxHeightPx
            snappedY = (snappedNdcY / parallaxFactor).coerceIn(-2f, 2f)
        }
    }

    return snappedX to snappedY
}

/**
 * Dibuja las guías de `shape` con la densidad de `spec` dentro del
 * DrawScope actual — función ÚNICA y compartida entre el overlay real
 * del canvas del editor y las miniaturas de vista previa del carrusel
 * del menú, para que la vista previa sea SIEMPRE fiel a lo que se ve en
 * el canvas de verdad, sin sorpresas.
 */
private fun DrawScope.drawGridGuides(shape: GridShape, spec: GridSpec, color: Color, strokeWidth: Float) {
    val columns = spec.columns.coerceAtLeast(1)
    val rows = spec.rows.coerceAtLeast(1)
    when (shape) {
        GridShape.RECTANGLE -> {
            for (i in 1 until columns) {
                val x = size.width * i / columns
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth)
            }
            for (i in 1 until rows) {
                val y = size.height * i / rows
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth)
            }
        }
        GridShape.DIAGONAL_RIGHT -> drawDiagonalGuideLines(columns, ascending = true, color, strokeWidth)
        GridShape.DIAGONAL_LEFT -> drawDiagonalGuideLines(columns, ascending = false, color, strokeWidth)
        GridShape.DIAGONAL_CROSS -> {
            // Las dos direcciones juntas — Columnas controla un sentido,
            // Filas el otro, totalmente independientes entre sí, tal
            // como confirmaste que debía ser esta opción (aparte de las
            // diagonales de una sola dirección, no el resultado de
            // "activar las dos a la vez").
            drawDiagonalGuideLines(columns, ascending = true, color, strokeWidth)
            drawDiagonalGuideLines(rows, ascending = false, color, strokeWidth)
        }
        GridShape.ROUND -> {
            val cellW = size.width / columns
            val cellH = size.height / rows
            val radius = minOf(cellW, cellH) / 2f
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    val center = Offset((c + 0.5f) * cellW, (r + 0.5f) * cellH)
                    drawCircle(color = color, radius = radius, center = center, style = Stroke(width = strokeWidth))
                }
            }
        }
        GridShape.SQUARE -> {
            // Celdas realmente CUADRADAS (mismo lado en X e Y), a
            // diferencia de RECTANGLE que reparte columnas/filas
            // independientes y termina con celdas rectangulares si el
            // encuadre no es cuadrado. El lado sale de `columns` sobre el
            // ancho — mismo criterio de una grilla de referencia tipo
            // Blender, pero plana, sin la perspectiva del piso 3D.
            //
            // CENTRADO: el ancho SIEMPRE cae justo (cell = width/columns
            // es una división exacta), pero el mismo tamaño de celda
            // aplicado al alto casi nunca entra un número entero de
            // veces — antes eso dejaba SIEMPRE el "sobrante" pegado
            // contra el borde inferior (arrancaba a dibujar desde arriba
            // sin más), y la cuadrícula se veía corrida/descentrada
            // verticalmente. Ahora el sobrante se reparte MITAD arriba,
            // MITAD abajo (y lo mismo en X, por las dudas de que el
            // ancho no caiga perfecto por redondeo de punto flotante) —
            // igual que una hoja cuadriculada centrada en el marco, en
            // vez de pegada a una esquina.
            val cell = (size.width / columns).coerceAtLeast(1f)

            val colsFit = (size.width / cell).toInt().coerceAtLeast(1)
            val xMargin = (size.width - colsFit * cell) / 2f
            var x = xMargin + cell
            val xEnd = size.width - xMargin - cell * 0.5f
            while (x < xEnd) {
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth)
                x += cell
            }

            val rowsFit = (size.height / cell).toInt().coerceAtLeast(1)
            val yMargin = (size.height - rowsFit * cell) / 2f
            var y = yMargin + cell
            val yEnd = size.height - yMargin - cell * 0.5f
            while (y < yEnd) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth)
                y += cell
            }
        }
        GridShape.CROSS -> {
            // Cruz simple y fija: una línea vertical y una horizontal
            // cruzando el centro exacto del cuadro — sin densidad
            // configurable, por eso no usa `columns`/`rows` para nada.
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(color, Offset(cx, 0f), Offset(cx, size.height), strokeWidth)
            drawLine(color, Offset(0f, cy), Offset(size.width, cy), strokeWidth)
        }
    }
}

/**
 * Rasteriza la cuadrícula de composición a un [Bitmap], reutilizando TAL
 * CUAL la misma función [drawGridGuides] que dibuja cualquiera de las 7
 * formas — no se duplica ni un poco la matemática de cada una, así el
 * resultado es pixel-idéntico a como se veía el overlay antes.
 *
 * BUG/COMPORTAMIENTO REAL corregido: antes la cuadrícula se dibujaba como
 * un Canvas de Compose flotando SIEMPRE por encima de las capas (dentro
 * del mismo Box, pero después del GLPreview en el orden de hijos = más
 * arriba en el z-order) — tapando imágenes, logos, texto, cualquier cosa
 * que hubiera debajo, algo que ningún programa profesional (Photoshop,
 * Lightroom, Premiere, CapCut) hace: en esos programas la guía de
 * composición vive EN el lienzo, detrás de las capas reales, y solo se
 * asoma por donde una capa es transparente o no llega a cubrir.
 *
 * Para lograr eso de verdad (no solo simularlo con transparencia) la
 * cuadrícula se rasteriza acá a un bitmap del mismo tamaño que el lienzo
 * y se sube al motor GL como una textura más — dibujada PRIMERO, antes
 * que cualquier capa real (ver GLRenderer.onDrawFrame). Así, el propio
 * pipeline de composición GL hace que cualquier píxel opaco de una capa
 * tape la cuadrícula donde corresponde, exactamente como en un canvas
 * profesional.
 */
private fun rasterizeGridBitmap(
    widthPx: Int,
    heightPx: Int,
    shape: GridShape,
    spec: GridSpec,
    color: Color,
    strokeWidthPx: Float
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    // NO tocar setPremultiplied acá: android.graphics.Canvas EXIGE que su
    // bitmap destino esté premultiplicado si tiene alpha — usar
    // setPremultiplied(false) antes de dibujar hace que el propio
    // constructor de Canvas tire una RuntimeException ("trying to use a
    // non-premultiplied bitmap") apenas se activa la cuadrícula. El
    // arreglo real del color apagado NO va acá — va en el blend function
    // de OpenGL, en [LayerDrawer], que es quien tiene que saber que el
    // bitmap que le llega viene premultiplicado (ver comentario ahí).
    val androidCanvas = android.graphics.Canvas(bitmap)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = androidx.compose.ui.graphics.Canvas(androidCanvas),
        size = Size(widthPx.toFloat(), heightPx.toFloat())
    ) {
        drawGridGuides(shape, spec, color, strokeWidthPx)
    }
    return bitmap
}

/**
 * Color real con el que se dibujan las líneas de guía en el canvas —
 * blanco de toda la vida (mismo look de siempre) si el usuario no
 * activó un color personalizado, o el matiz elegido en
 * [GridLineColorBar] si lo activó. Se aplica IGUAL sin importar la
 * forma activa (Rectángulo, Cuadrado, Diagonales, etc.), tal como
 * pediste — es un ajuste independiente de la forma y de la densidad.
 * Misma [opacity] en los dos casos (blanco o color), para que activar
 * un color no cambie de golpe qué tan "fuerte" se ve la guía sobre el
 * video — solo cambia el tono, no la intensidad.
 *
 * `opacity` ANTES era un 0.4f fijo a fuego acá adentro, sin control
 * ninguno — con fondos muy saturados (el verde chroma-key por defecto)
 * y un matiz casi-complementario (magenta, violeta), 0.4 de mezcla
 * daba un resultado gris/apagado, matemáticamente correcto pero nada
 * vívido. Ahora [GridOpacitySlider] deja subirlo cuando hace falta más
 * presencia, sin tocar el matiz — 0.4f sigue siendo el default, así
 * que un proyecto guardado antes de este control se ve exactamente
 * igual al reabrirlo.
 */
private fun gridLineDrawColor(colorEnabled: Boolean, hue: Float, opacity: Float): Color {
    val clampedOpacity = opacity.coerceIn(0f, 1f)
    return if (colorEnabled) Color.hsv(hue.coerceIn(0f, 360f), 1f, 1f).copy(alpha = clampedOpacity)
    else Color.White.copy(alpha = clampedOpacity)
}

/**
 * `count` líneas diagonales parejas a 45°, cubriendo todo el ancho y
 * alto del DrawScope de punta a punta (el barrido arranca antes del
 * borde izquierdo y termina después del derecho, para que ninguna
 * esquina quede sin cubrir — por eso el Canvas que llama a esto necesita
 * `clipToBounds()`). `ascending = true` = pendiente "/" (subiendo de
 * izquierda a derecha, la dirección que confirmaste como "Diagonal ↗");
 * `false` = pendiente "\" ("Diagonal ↖", la dirección contraria).
 */
private fun DrawScope.drawDiagonalGuideLines(count: Int, ascending: Boolean, color: Color, strokeWidth: Float) {
    val lines = count.coerceAtLeast(1)
    val span = size.width + size.height
    val step = span / lines
    for (i in 0 until lines) {
        val offset = step * (i + 0.5f) - size.height
        val start: Offset
        val end: Offset
        if (ascending) {
            start = Offset(offset, size.height)
            end = Offset(offset + size.height, 0f)
        } else {
            start = Offset(offset, 0f)
            end = Offset(offset + size.height, size.height)
        }
        drawLine(color, start, end, strokeWidth)
    }
}

/**
 * Los dos ejes de [GridSpec] que se pueden editar manualmente desde
 * [GridAxisInputDialog] — identifica cuál de los dos números está
 * editando el usuario en un momento dado. El TEXTO visible ya no vive
 * fijo acá adentro (antes era siempre "Columnas"/"Filas") — ahora
 * depende de la forma activa (ver [GridShape.axisXLabel] /
 * [GridShape.axisYLabel]), así que se resuelve aparte en [GridMenu].
 */
private enum class GridAxis { COLUMNS, ROWS }

/**
 * Paradas de color para la franja de [GridLineColorBar] — calcadas del
 * recorrido EXACTO de la franja de referencia que mandaste: arranca en
 * magenta, pasa por rojo, naranja, amarillo, verde, y termina en
 * celeste/azul clarito — SIN dar la vuelta completa al círculo de matiz
 * (no pasa por violeta ni azul puro antes de cortar, tal como se ve en
 * tu imagen). Por eso el recorrido no es un simple 0°→360°: arranca en
 * [HUE_START] (magenta, ~300°) y avanza [HUE_SPAN] grados (260°) —
 * suficiente para llegar bien pasado el verde hasta el celeste, pero
 * sin volver a entrar en la zona violeta/azul puro por el otro extremo.
 */
private const val HUE_START = 300f
private const val HUE_SPAN = 260f

/** fracción de la franja (0f–1f) → matiz HSV real (0°–360°), siguiendo el recorrido [HUE_START]→[HUE_START]+[HUE_SPAN]. */
private fun gridBarFractionToHue(fraction: Float): Float {
    val hue = (HUE_START + fraction.coerceIn(0f, 1f) * HUE_SPAN) % 360f
    return if (hue < 0f) hue + 360f else hue
}

/** matiz HSV real (0°–360°) → fracción de la franja (0f–1f), la inversa de [gridBarFractionToHue]. */
private fun gridBarHueToFraction(hue: Float): Float {
    var diff = (hue - HUE_START) % 360f
    if (diff < 0f) diff += 360f
    return (diff / HUE_SPAN).coerceIn(0f, 1f)
}

private val HUE_GRADIENT_STOPS: List<Color> = (0..12).map { step ->
    Color.hsv(gridBarFractionToHue(step / 12f), 1f, 1f)
}

/**
 * Barra de color de las líneas de guía — selector de matiz horizontal
 * calcado del que mostraste de Blender: franja arcoíris de ancho
 * completo con una línea vertical que marca el matiz elegido. Tocar en
 * cualquier punto de la franja O arrastrar el dedo por ella mueve esa
 * línea y actualiza el color EN VIVO — un solo gesto cubre las dos
 * formas de usarla, no hace falta soltar y volver a tocar para
 * "empezar" un arrastre.
 * Es INDEPENDIENTE de la forma activa y de los steppers de arriba — se
 * aplica igual sin importar qué figura esté eligiendo el usuario, tal
 * como pediste — por eso tiene su propio switch "activar/desactivar":
 * apagado, las líneas quedan blancas (el look de toda la vida); prendido,
 * usan el matiz elegido acá. La franja se puede seguir tocando con el
 * switch apagado (para dejar elegido un color de antemano), solo que se
 * ve atenuada mientras tanto.
 */
@Composable
private fun GridLineColorBar(
    enabled: Boolean,
    hue: Float,
    onToggle: () -> Unit,
    onHueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Color de línea",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier
                    .height(20.dp)
                    .scale(0.7f),
                colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
            )
        }

        // Ancho medido de la franja (en px) — necesario para convertir
        // la posición X del dedo en un matiz de 0 a 360.
        var barWidthPx by remember { mutableFloatStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                // Atenuada mientras el switch está apagado — mismo
                // lenguaje visual que un control deshabilitado — pero
                // sigue siendo tocable, para poder dejar el matiz
                // elegido de antemano sin tener que prender el switch
                // primero.
                .alpha(if (enabled) 1f else 0.35f)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(HUE_GRADIENT_STOPS))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    // Gesto único y manual (no detectTapGestures +
                    // detectHorizontalDragGestures por separado) para
                    // que el PRIMER toque ya mueva la línea a esa
                    // posición, y arrastrar desde ahí la siga
                    // actualizando en el mismo gesto — sin esto, un
                    // toque simple (sin arrastre) no movería nada,
                    // porque los detectores de arrastre solo reaccionan
                    // después de cruzar el umbral de movimiento.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        if (barWidthPx > 0f) {
                            onHueChange(gridBarFractionToHue(down.position.x / barWidthPx))
                        }
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) break
                            change.consume()
                            if (barWidthPx > 0f) {
                                onHueChange(gridBarFractionToHue(change.position.x / barWidthPx))
                            }
                        }
                    }
                }
        ) {
            // Línea indicadora — el "handle" que marca el matiz elegido,
            // calcada de la referencia de Blender.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .offset {
                        val x = if (barWidthPx > 0f) gridBarHueToFraction(hue) * barWidthPx else 0f
                        IntOffset((x - 1.dp.toPx()).roundToInt(), 0)
                    }
                    .background(Color.White)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.35f))
            )
        }
    }
}

/** Rango de grosor permitido para las líneas de guía, en dp. */
private const val GRID_THICKNESS_MIN_DP = 0.5f
private const val GRID_THICKNESS_MAX_DP = 6f

/**
 * Slider de grosor de línea — mismo lenguaje visual que [GridLineColorBar]
 * (franja de 26dp, mismo radio y borde, mismo patrón de gesto tap+drag en
 * un solo detector), pero en vez de matiz controla el grosor real que usa
 * [drawGridGuides] (0.5dp a 6dp). Se aplica por igual a CUALQUIER forma
 * activa — por eso vive entre el carrusel de formas y los steppers de
 * Columnas/Filas: un solo control para todas las formas, en vez de
 * repetirlo por cada una.
 */
@Composable
private fun GridThicknessSlider(
    thicknessDp: Float,
    onThicknessChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Grosor",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                // Un decimal alcanza — el usuario arrastra por sensación
                // visual, no tipea un número exacto (a diferencia de los
                // steppers de Columnas/Filas, este control no tiene
                // diálogo numérico).
                "${(thicknessDp * 10).roundToInt() / 10f} dp",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Thumb más chico y prolijo (14dp, antes 18dp) — un thumb de ese
        // tamaño sobre una franja de 26dp de alto se leía "gordo"/poco
        // premium; este tamaño es el mismo lenguaje que sliders nativos
        // de iOS/Android (thumb claramente más chico que el alto total
        // del control táctil).
        val thumbDiameter = 14.dp
        val thumbRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { (thumbDiameter / 2).toPx() }

        var trackWidthPx by remember { mutableFloatStateOf(0f) }
        val fraction = ((thicknessDp - GRID_THICKNESS_MIN_DP) / (GRID_THICKNESS_MAX_DP - GRID_THICKNESS_MIN_DP))
            .coerceIn(0f, 1f)

        // BUG REAL corregido: antes el thumb (18dp) vivía DENTRO de un Box
        // con .clip(RoundedCornerShape(6.dp)) aplicado a esa misma cadena
        // de modifiers — como el thumb se centra sobre la posición X del
        // valor y a fraction=1 su mitad derecha queda más allá del ancho
        // del track, esa mitad se recortaba contra el borde redondeado
        // ("se ve cortado el final del slider"). Ahora el thumb es un
        // Box HERMANO sin clip (nunca se recorta) y el track reserva
        // `thumbDiameter / 2` de padding a cada lado — el mismo patrón
        // que un Slider de Material — así el thumb siempre queda 100%
        // visible, en cualquier extremo, sin salirse tampoco del menú.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = thumbDiameter / 2)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    // Gesto único (no tap + drag por separado): el primer
                    // toque ya mueve el thumb a esa posición, y arrastrar
                    // desde ahí lo sigue actualizando en el mismo gesto —
                    // idéntico patrón que la franja de color de arriba.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        if (trackWidthPx > 0f) {
                            val f = (down.position.x / trackWidthPx).coerceIn(0f, 1f)
                            onThicknessChange(GRID_THICKNESS_MIN_DP + f * (GRID_THICKNESS_MAX_DP - GRID_THICKNESS_MIN_DP))
                        }
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) break
                            change.consume()
                            if (trackWidthPx > 0f) {
                                val f = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                                onThicknessChange(GRID_THICKNESS_MIN_DP + f * (GRID_THICKNESS_MAX_DP - GRID_THICKNESS_MIN_DP))
                            }
                        }
                    }
                }
        ) {
            // Riel delgado (4dp, antes 26dp macizo) — el mismo criterio
            // fino/premium que usan Instagram, CapCut o Lightroom para
            // este tipo de control: una línea sutil, no una barra gruesa.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(2.dp))
            )
            // Relleno hasta el valor actual, sobre el mismo riel delgado.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandPurpleLight)
            )
            // Thumb circular — el "handle" que marca el grosor elegido.
            // Vive FUERA del riel clippeado (ver comentario arriba) para
            // no recortarse nunca, y el padding horizontal del Box padre
            // garantiza que, aun centrado en los extremos, no se salga
            // del menú.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val x = if (trackWidthPx > 0f) fraction * trackWidthPx else 0f
                        IntOffset((x - thumbRadiusPx).roundToInt(), 0)
                    }
                    .size(thumbDiameter)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
            )
        }
    }
}

/** Rango de opacidad permitido para las líneas de guía. 0.05 (no 0) para que
 *  siempre quede algo visible — un slider que llegue a "invisible del
 *  todo" es indistinguible de un bug para el usuario. */
private const val GRID_OPACITY_MIN = 0.05f
private const val GRID_OPACITY_MAX = 1f

/**
 * Slider de opacidad de línea — MISMO lenguaje visual y patrón de gesto
 * que [GridThicknessSlider] (riel de 4dp, thumb de 14dp, tap+drag en un
 * solo detector), pero controla [gridLineDrawColor]'s alpha en vez del
 * grosor. Se agregó porque, con un fondo bien saturado (el verde
 * chroma-key por defecto) y un matiz casi-complementario elegido en
 * [GridLineColorBar] (magenta, violeta), la alpha fija de 0.4 que había
 * antes mezclaba demasiado con el fondo y el color se veía apagado/
 * grisáceo — correcto matemáticamente (es blending, no bug), pero nada
 * vívido. Vive debajo de Grosor (pedido puntual), arriba de los
 * steppers de Columnas/Filas.
 */
@Composable
private fun GridOpacitySlider(
    opacity: Float,
    onOpacityChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Opacidad",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${(opacity * 100).roundToInt()}%",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        val thumbDiameter = 14.dp
        val thumbRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { (thumbDiameter / 2).toPx() }

        var trackWidthPx by remember { mutableFloatStateOf(0f) }
        val fraction = ((opacity - GRID_OPACITY_MIN) / (GRID_OPACITY_MAX - GRID_OPACITY_MIN))
            .coerceIn(0f, 1f)

        // Mismo patrón táctil que [GridThicknessSlider]: gesto único
        // (tap ya mueve el thumb a esa posición, arrastrar lo sigue
        // actualizando en el mismo gesto, sin esperar a cruzar un
        // umbral) y thumb HERMANO del riel clippeado (no adentro), para
        // que nunca se recorte contra el borde redondeado en los
        // extremos.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = thumbDiameter / 2)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        if (trackWidthPx > 0f) {
                            val f = (down.position.x / trackWidthPx).coerceIn(0f, 1f)
                            onOpacityChange(GRID_OPACITY_MIN + f * (GRID_OPACITY_MAX - GRID_OPACITY_MIN))
                        }
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) break
                            change.consume()
                            if (trackWidthPx > 0f) {
                                val f = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                                onOpacityChange(GRID_OPACITY_MIN + f * (GRID_OPACITY_MAX - GRID_OPACITY_MIN))
                            }
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(2.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandPurpleLight)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val x = if (trackWidthPx > 0f) fraction * trackWidthPx else 0f
                        IntOffset((x - thumbRadiusPx).roundToInt(), 0)
                    }
                    .size(thumbDiameter)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
            )
        }
    }
}

/**
 * Ventana premium que abre el texto "Edición" de la barra superior,
 * al lado del ícono Grabar. Por ahora tiene una sola opción, "Imagen",
 * con una casilla cuadrada propia (ic_checkbox_unchecked / _checked —
 * no el Switch/Checkbox default de Material3, para que el check tenga
 * identidad visual propia y consistente con el resto de íconos SVG a
 * medida de la app).
 *
 * Ancho: antes fijo en 168dp (mucho más ancho que "Imagen" + su
 * casilla), así que centrado bajo un ancla angosta como el texto
 * "Edición" sobraba de sobra hacia la derecha, tapando el ícono Grabar
 * que está pegado a su derecha. Ahora envuelve su contenido
 * (wrapContentWidth) con paddings reducidos — la fila queda tan angosta
 * como su propio contenido ("Imagen" + casilla nomás).
 *
 * Posición: [BelowAnchorCenteredPopupPositionProvider], igual que
 * [GridMenu] — centrado bajo el ancla. ARREGLADO: hubo un intento previo
 * de alinearlo por el borde izquierdo en vez de centrarlo, pensando que
 * así se evitaba el solape con Grabar — pero al ya ser angosto
 * (wrapContentWidth), alinear a la izquierda dejaba el popup visualmente
 * corrido hacia la derecha de "Edición" (su borde izquierdo coincidía
 * con el de "Edición", pero como el popup es más ancho que el texto,
 * todo el resto se notaba desplazado). Centrado, con este ancho
 * compacto, el popup ya no llega a alcanzar a Grabar (la diferencia de
 * ancho entre el popup y "Edición" es bastante menor que los 16dp de
 * separación hacia el ícono), así que se puede volver al mismo criterio
 * que el resto de menús de la barra sin reintroducir el bug original.
 */
@Composable
private fun EdicionMenu(
    imagenChecked: Boolean,
    onImagenToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        popupPositionProvider = BelowAnchorCenteredPopupPositionProvider(gapPx = 8),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .shadow(elevation = 10.dp, shape = RectangleShape),
            color = SurfaceTintedElevated,
            shape = RectangleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImagenToggle() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Ícono SVG delante de la opción, referenciado a lo que
                // hace (afecta a la capa de imagen seleccionada) — mismo
                // criterio que el resto de los mini-menús de la app
                // (Editar/Restablecer/Solo-Todos), que ya lo tenían. Este
                // era el único que le faltaba.
                Icon(
                    painter = painterResource(id = R.drawable.ic_image_placeholder),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Imagen",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(
                        id = if (imagenChecked) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
                    ),
                    contentDescription = if (imagenChecked) "Imagen activada" else "Activar Imagen",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Menú que se despliega justo debajo del ícono de cuadrícula de la barra
 * de arriba. Con estándar de apps profesionales premium (Figma,
 * Photoshop, Lightroom, editores de video pro):
 *  - Switch arriba para prender/apagar sin perder el ajuste guardado.
 *  - Carrusel de FORMAS (Rectángulo, Diagonal ↗, Diagonal ↖, Diagonal
 *    cruzada, Redondo, Cuadrado, Cruz) — 3 visibles a la vez, mismo
 *    hueco de siempre, pero con flechas ‹ › a los costados Y arrastrable
 *    con el dedo como una rueda real: el carrusel sigue el dedo 1:1
 *    mientras arrastrás (nada de esperar a cruzar un umbral fijo para
 *    reaccionar) y se asienta con un resorte suave al soltar.
 *  - Debajo, los steppers numéricos de la forma ACTIVA — con las dos
 *    formas de ajustar que ya pediste (– / + y tocar el número). Si la
 *    forma solo tiene un eje con sentido geométrico (las diagonales de
 *    una sola dirección), el segundo stepper directamente no se
 *    muestra.
 *  - Al pie de todo, [GridLineColorBar]: una barra de color INDEPENDIENTE
 *    de la forma/densidad — se aplica igual sin importar qué figura esté
 *    activa — con su propio switch "activar/desactivar" (si está
 *    apagada, las líneas quedan blancas, el look de toda la vida) y la
 *    franja arcoíris arrastrable para elegir el matiz, calcada del
 *    selector de color de Blender que mandaste de referencia.
 * Todo en una Column angosta (no una Row ancha), y el Popup se mantiene
 * en el mismo ancho de siempre (184dp) — no se agranda ni se achica.
 */
@Composable
private fun GridMenu(
    enabled: Boolean,
    shape: GridShape,
    spec: GridSpec,
    lineColorEnabled: Boolean,
    lineHue: Float,
    lineThicknessDp: Float,
    lineOpacity: Float,
    snapEnabled: Boolean,
    onShapeSelect: (GridShape) -> Unit,
    onAxisChange: (GridSpec) -> Unit,
    onToggle: () -> Unit,
    onSnapToggle: () -> Unit,
    onLineColorToggle: () -> Unit,
    onLineHueChange: (Float) -> Unit,
    onThicknessChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    // Qué eje se está escribiendo a mano ahora mismo (o ninguno). El
    // diálogo numérico vive en un Dialog aparte — una ventana propia,
    // focusable, para que el teclado del sistema funcione normal — así
    // que puede convivir sin problema con el Popup no-focusable del menú.
    var editingAxis by remember { mutableStateOf<GridAxis?>(null) }

    // Índice del primer elemento visible del carrusel de formas — va de
    // 0 a (cantidad de formas − formas visibles). Arrastrar con el dedo
    // O tocar las flechas ‹ › mueven este MISMO estado; un solo estado,
    // dos formas de cambiarlo, tal como pediste.
    val maxCarouselStart = (GRID_SHAPES.size - GRID_CAROUSEL_VISIBLE).coerceAtLeast(0)
    // Arranca centrado en la forma YA SELECCIONADA, no siempre en 0 —
    // antes, cada vez que se cerraba y volvía a abrir el menú, el
    // Popup se desmontaba por completo y este estado se perdía, así
    // que el carrusel "saltaba" de vuelta al principio y la forma
    // elegida (si no era de las primeras 3) quedaba fuera de vista,
    // dando la sensación de que "se movía" o se perdía la selección.
    // Ahora, al volver a abrir, el carrusel arranca ya posicionado
    // para que la forma activa se vea de entrada, sin tener que
    // buscarla arrastrando.
    var carouselStart by remember {
        val selectedIndex = GRID_SHAPES.indexOf(shape).coerceAtLeast(0)
        val centeredStart = selectedIndex - GRID_CAROUSEL_VISIBLE / 2
        mutableIntStateOf(centeredStart.coerceIn(0, maxCarouselStart))
    }
    val coroutineScope = rememberCoroutineScope()

    Popup(
        popupPositionProvider = BelowAnchorCenteredPopupPositionProvider(gapPx = 8),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            modifier = Modifier
                .width(184.dp)
                .shadow(elevation = 10.dp, shape = RectangleShape),
            color = SurfaceTintedElevated,
            shape = RectangleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Cuadrícula",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier
                            .height(20.dp)
                            .scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }

                // --- "Snap" (imán a la cuadrícula) — pedido explícito:
                // justo debajo de "Cuadrícula" y su switch, no mezclado
                // con el resto de los controles visuales (grosor,
                // opacidad, color). Es una opción INDEPENDIENTE: con la
                // cuadrícula visible pero el snap apagado, la capa se
                // mueve completamente libre por el canvas (el
                // comportamiento de toda la vida); con el snap prendido,
                // arrastrar cerca de una línea la imanta (ver
                // snapTranslateToGrid). No depende de si `enabled`
                // (Cuadrícula) está prendido o no en esta fila — el
                // gesto de arrastre igual solo aplica el snap real si
                // AMBOS están activos (sin cuadrícula visible no hay
                // contra qué imantar), pero el switch queda siempre
                // visible y tocable acá, para no obligar a abrir el
                // menú dos veces.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Snap",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = snapEnabled,
                        onCheckedChange = { onSnapToggle() },
                        modifier = Modifier
                            .height(20.dp)
                            .scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }

                // --- Carrusel de formas: flecha izquierda, 3 formas
                // visibles (arrastrables), flecha derecha. El gesto de
                // arrastre se detecta sobre TODO el Row del medio (no
                // solo sobre las 3 cajitas individuales) para que
                // arrastrar desde cualquier punto de esa franja funcione,
                // como una rueda de verdad.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GridCarouselArrow(
                        iconRes = R.drawable.ic_chevron_left,
                        contentDescription = "Formas anteriores",
                        enabled = carouselStart > 0,
                        onClick = { if (carouselStart > 0) carouselStart-- }
                    )

                    // Ancho medido del carril (en px) — se usa para saber
                    // cuánto mide cada forma y así poder mover el
                    // carrusel a la MISMA velocidad que el dedo (1:1),
                    // en vez de esperar a cruzar un umbral fijo antes de
                    // reaccionar. Vive en mutableFloatStateOf (no en un
                    // array) porque si cambia (rotación, resize) el
                    // carrusel debe redibujarse con el nuevo ancho.
                    var railWidthPx by remember { mutableFloatStateOf(0f) }
                    // Offset visual EN VIVO del carrusel mientras se
                    // arrastra — a diferencia de la versión anterior
                    // (que solo reaccionaba tras cruzar 26dp de un tirón,
                    // sintiéndose "trabada"/con lag), acá las 3 formas
                    // se deslizan pegadas al dedo desde el primer
                    // milímetro de arrastre, como una rueda de verdad.
                    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .onSizeChanged { railWidthPx = it.width.toFloat() }
                            .pointerInput(maxCarouselStart) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        // Al soltar, vuelve a 0 con un
                                        // resorte suave — el "asentado"
                                        // final típico de un carrusel
                                        // premium (Lightroom/Photos).
                                        val start = dragOffsetPx
                                        coroutineScope.launch {
                                            animate(
                                                initialValue = start,
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ) { value, _ -> dragOffsetPx = value }
                                        }
                                    },
                                    onDragCancel = { dragOffsetPx = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        val itemWidthPx = railWidthPx / GRID_CAROUSEL_VISIBLE
                                        // Si todavía no se midió el carril
                                        // (primer frame), no hay con qué
                                        // calcular el paso — se ignora
                                        // ese evento nomás, el siguiente
                                        // ya va a tener el ancho posta.
                                        if (itemWidthPx > 0f) {
                                            var offset = dragOffsetPx + dragAmount
                                            // Arrastrar a la izquierda
                                            // avanza el carrusel (revela
                                            // formas siguientes); a la
                                            // derecha retrocede. El
                                            // `while` (no `if`) es a
                                            // propósito: un arrastre largo
                                            // en un solo gesto puede pasar
                                            // varias formas de una, como
                                            // una rueda real — pero acá el
                                            // offset visual se ajusta en
                                            // el momento, sin saltos, para
                                            // que las formas nunca
                                            // "salten" de golpe.
                                            while (offset <= -itemWidthPx && carouselStart < maxCarouselStart) {
                                                carouselStart++
                                                offset += itemWidthPx
                                            }
                                            while (offset >= itemWidthPx && carouselStart > 0) {
                                                carouselStart--
                                                offset -= itemWidthPx
                                            }
                                            // Efecto "goma" en los
                                            // extremos: si ya no hay más
                                            // formas para revelar de ese
                                            // lado, el arrastre se frena
                                            // en vez de deslizarse
                                            // libremente al vacío.
                                            val rubberBandLimit = itemWidthPx * 0.35f
                                            if (carouselStart == 0 && offset > rubberBandLimit) offset = rubberBandLimit
                                            if (carouselStart == maxCarouselStart && offset < -rubberBandLimit) offset = -rubberBandLimit
                                            dragOffsetPx = offset
                                        }
                                    }
                                )
                            },
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (slot in 0 until GRID_CAROUSEL_VISIBLE) {
                            val shapeOption = GRID_SHAPES[carouselStart + slot]
                            GridShapeOption(
                                shape = shapeOption,
                                spec = spec,
                                isSelected = enabled && shape == shapeOption,
                                lineColorEnabled = lineColorEnabled,
                                lineHue = lineHue,
                                onClick = { onShapeSelect(shapeOption) },
                                modifier = Modifier
                                    .weight(1f)
                                    .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
                            )
                        }
                    }

                    GridCarouselArrow(
                        iconRes = R.drawable.ic_chevron_right,
                        contentDescription = "Más formas",
                        enabled = carouselStart < maxCarouselStart,
                        onClick = { if (carouselStart < maxCarouselStart) carouselStart++ }
                    )
                }

                // Grosor de línea — INDEPENDIENTE de la forma/densidad, se
                // aplica igual sin importar qué figura esté activa (mismo
                // criterio que [GridLineColorBar] más abajo). Va acá, entre
                // el carrusel de formas y los steppers de Columnas/Filas,
                // tal como pediste.
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                GridThicknessSlider(
                    thicknessDp = lineThicknessDp,
                    onThicknessChange = onThicknessChange
                )

                // Opacidad de línea — pedida explícitamente para ir JUSTO
                // debajo del slider de Grosor (mismo criterio: control
                // independiente de la forma, se aplica igual a
                // cualquiera de las 7). Va ACÁ y no al pie junto a
                // [GridLineColorBar] porque afecta tanto al blanco por
                // defecto como al color elegido — es una propiedad de
                // "cuánto se nota la línea", no del color en sí.
                GridOpacitySlider(
                    opacity = lineOpacity,
                    onOpacityChange = onOpacityChange
                )

                // Los steppers (y el divisor de arriba) solo aparecen si
                // la forma activa tiene algún número que editar — [CROSS]
                // no tiene ninguno (es siempre la misma cruz centrada),
                // así que en ese caso el menú se queda corto, sin dejar
                // un divisor colgado arriba de nada.
                val axisXLabel = shape.axisXLabel
                if (axisXLabel != null) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    GridAxisStepper(
                        label = axisXLabel,
                        value = spec.columns,
                        onValueChange = { onAxisChange(spec.copy(columns = it)) },
                        onValueTap = { editingAxis = GridAxis.COLUMNS }
                    )
                    // El segundo eje solo se muestra si la forma activa
                    // realmente lo usa geométricamente (ver comentario en
                    // GridShape.axisYLabel más arriba).
                    if (shape.axisYLabel != null) {
                        GridAxisStepper(
                            label = shape.axisYLabel,
                            value = spec.rows,
                            onValueChange = { onAxisChange(spec.copy(rows = it)) },
                            onValueTap = { editingAxis = GridAxis.ROWS }
                        )
                    }
                }

                // Color de las líneas — independiente de la forma y de
                // los steppers de arriba: se aplica igual sin importar
                // qué figura esté activa (Rectángulo, Cuadrado,
                // Diagonales, etc.), por eso vive SIEMPRE al pie del
                // menú, incluso con [GridShape.CROSS] que no tiene
                // ningún stepper.
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                GridLineColorBar(
                    enabled = lineColorEnabled,
                    hue = lineHue,
                    onToggle = onLineColorToggle,
                    onHueChange = onLineHueChange
                )
            }
        }
    }

    // El diálogo de entrada manual vive AFUERA del Popup (no anidado
    // adentro) — así es una ventana propia e independiente que se puede
    // enfocar para que el teclado del sistema aparezca sin pelear con las
    // propiedades no-focusable del Popup del menú.
    val axisBeingEdited = editingAxis
    if (axisBeingEdited != null) {
        // shape.axisXLabel solo puede ser null para CROSS, que no tiene
        // stepper ninguno — así que nunca llega acá con editingAxis
        // seteado; el "?:" es solo una red de seguridad para que el
        // compilador no exija un valor no-nulo que en la práctica
        // siempre está presente en este punto del flujo.
        val axisLabel = if (axisBeingEdited == GridAxis.COLUMNS) (shape.axisXLabel ?: "")
            else (shape.axisYLabel ?: shape.axisXLabel ?: "")
        GridAxisInputDialog(
            label = axisLabel,
            initialValue = if (axisBeingEdited == GridAxis.COLUMNS) spec.columns else spec.rows,
            range = GRID_AXIS_RANGE,
            onDismiss = { editingAxis = null },
            onConfirm = { newValue ->
                onAxisChange(
                    if (axisBeingEdited == GridAxis.COLUMNS) spec.copy(columns = newValue)
                    else spec.copy(rows = newValue)
                )
                editingAxis = null
            }
        )
    }
}

/** Flechita chica del carrusel de formas — mismo lenguaje visual que [GridStepperButton], apenas más compacta (20dp) para dejarle espacio a las 3 formas. */
@Composable
private fun GridCarouselArrow(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.03f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 0.9f else 0.3f),
            modifier = Modifier.size(13.dp)
        )
    }
}

/**
 * Diálogo compacto para escribir a mano la cantidad de líneas de la
 * forma activa — la segunda forma de ajustar el valor que pidió el
 * usuario, además de los botones – / +. Mismo patrón visual que
 * [RenameProjectDialog] (Dialog + Surface + OutlinedTextField), para
 * consistencia con el resto de la app. Solo acepta dígitos y solo deja
 * confirmar si el número entra en [range] — mismo límite que ya
 * respetan los botones – / + del stepper, para que nunca haya manera de
 * terminar con una cuadrícula fuera de rango.
 */
@Composable
private fun GridAxisInputDialog(
    label: String,
    initialValue: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialValue.toString()) }
    val parsed = text.toIntOrNull()
    val isValid = parsed != null && parsed in range
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Entre ${range.first} y ${range.last}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new ->
                        // Solo dígitos, máximo 2 caracteres — el rango
                        // permitido (GRID_AXIS_RANGE) nunca llega a 3
                        // cifras, así que no hace falta más.
                        if (new.length <= 2 && new.all { it.isDigit() }) text = new
                    },
                    singleLine = true,
                    isError = text.isNotEmpty() && !isValid,
                    supportingText = {
                        if (text.isNotEmpty() && !isValid) {
                            Text("Ingresá un número entre ${range.first} y ${range.last}")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { parsed?.takeIf { isValid }?.let(onConfirm) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { parsed?.takeIf { isValid }?.let(onConfirm) }, enabled = isValid) { Text("Aplicar") }
                }
            }
        }
    }

    // Foco automático al abrir, para que el teclado aparezca de una sin
    // que el usuario tenga que tocar el campo — mismo criterio "menos
    // toques" que el resto de la app.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Una forma del carrusel del menú — dibuja EN VIVO su propia guía con
 * [drawGridGuides] usando la densidad actual (`spec`) Y el color de
 * línea actual (`lineColorEnabled`/`lineHue`), en vez de un ícono
 * estático o un blanco fijo, para que la vista previa sea EXACTAMENTE
 * fiel a cómo se va a ver en el canvas real si se elige — antes la
 * miniatura ignoraba el color elegido en [GridLineColorBar] y siempre
 * se veía blanca, lo cual contradecía la fidelidad que promete este
 * mismo comentario; quedó corregido. Cuadrada y compacta para que las 3
 * visibles quepan holgadas en un menú angosto.
 */
@Composable
private fun GridShapeOption(
    shape: GridShape,
    spec: GridSpec,
    isSelected: Boolean,
    lineColorEnabled: Boolean,
    lineHue: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) BrandPurpleLight.copy(alpha = 0.28f)
                else Color.White.copy(alpha = 0.06f)
            )
            .then(
                if (isSelected) Modifier.border(1.5.dp, BrandPurpleLight, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val previewAlpha = if (isSelected) 0.95f else 0.7f
            val lineColor = if (lineColorEnabled) {
                Color.hsv(lineHue.coerceIn(0f, 360f), 1f, 1f).copy(alpha = previewAlpha)
            } else {
                Color.White.copy(alpha = previewAlpha)
            }
            drawGridGuides(shape, spec, lineColor, 1.dp.toPx())
        }
    }
}

/**
 * Stepper numérico compacto (– valor +) para un solo eje de la forma
 * activa — así el usuario "manipula los valores en números" como pidió.
 * TRES formas de ajustar el valor, todas disponibles al mismo tiempo:
 *  1. Los botones – / + de siempre, para tocar y afinar de a uno — y
 *     ahora además con AUTO-REPETICIÓN: mantenerlos apretados dispara el
 *     valor en ráfaga, cada vez más rápido, hasta soltar (ver
 *     [GridStepperButton]) — mismo estándar que cualquier stepper
 *     numérico de software profesional (Premiere, los +/- de iOS/macOS).
 *  2. Tocar el número en sí (`onValueTap`) abre [GridAxisInputDialog]
 *     para escribirlo directo con el teclado — pedido puntual del
 *     usuario para poner un valor exacto sin tocar +/- muchas veces.
 *  3. Arrastrar el dedo VERTICALMENTE sobre el número: arriba sube,
 *     abajo baja, a razón de un paso cada pocos dp recorridos — y si el
 *     gesto se suelta con velocidad (un "flick" rápido, no un arrastre
 *     lento) se suma un empujón extra de pasos en esa misma dirección,
 *     así un toque-y-suelta rápido hacia arriba/abajo también mueve el
 *     valor aunque el recorrido haya sido corto, tal como pediste.
 * El número vive en su propia "cajita" con fondo sutil (en vez de texto
 * suelto) para que se vea, a simple vista, que es tocable/arrastrable —
 * mismo lenguaje visual que los botones – / + de al lado.
 * Acotado a [GRID_AXIS_RANGE]; los botones – / + se deshabilitan solos
 * al llegar al límite, mismo criterio visual que el resto de la barra
 * superior (undo/redo atenuados cuando no hay nada que hacer).
 */
@Composable
private fun GridAxisStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueTap: () -> Unit
) {
    // Referencia siempre-actualizada al valor actual, para leerla desde
    // adentro del gesto de arrastre vertical (que vive en un
    // pointerInput(Unit) de vida larga — no se reinicia en cada
    // recomposición, así que leer `value` directo ahí adentro daría un
    // valor viejo "congelado" del primer arranque del gesto).
    val currentValue = rememberUpdatedState(value)
    // Mismo motivo para `onValueChange`: es una FUNCIÓN, no solo un
    // dato, y closures armadas en la composición de más arriba (en
    // GridMenu) capturan el `spec` de ESE momento. Sin este
    // rememberUpdatedState, el gesto de arrastre de abajo llamaría para
    // siempre a la primera versión de `onValueChange` que vio al
    // montarse — con un `spec` viejo adentro — y terminaría PISANDO el
    // otro eje (por ejemplo, arrastrar Columnas después de haber tocado
    // Filas revertiría Filas al valor que tenía cuando se montó este
    // stepper). Bug real, ya corregido acá.
    val latestOnValueChange = rememberUpdatedState(onValueChange)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.weight(1f))
        val canDecrease = value > GRID_AXIS_RANGE.first
        val canIncrease = value < GRID_AXIS_RANGE.last
        GridStepperButton(
            iconRes = R.drawable.ic_remove,
            contentDescription = "Menos $label",
            enabled = canDecrease,
            onClick = { if (currentValue.value > GRID_AXIS_RANGE.first) onValueChange(currentValue.value - 1) }
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onValueTap)
                .pointerInput(Unit) {
                    // Sensibilidad del arrastre: cuántos px de recorrido
                    // vertical equivalen a un paso — compacto a
                    // propósito (la cajita mide apenas 28dp), así que no
                    // hace falta arrastrar muy lejos para notar el
                    // cambio.
                    val pxPerStep = 12.dp.toPx()
                    // Umbral de velocidad (px/s) a partir del cual un
                    // gesto se considera un "flick" rápido en vez de un
                    // arrastre lento — cada tramo de esta velocidad por
                    // encima del umbral suma un paso extra al soltar.
                    val flingPxPerSecondPerStep = 900f
                    var dragAccumulatorPx = 0f
                    var appliedSteps = 0
                    var baseValue = currentValue.value
                    val velocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragAccumulatorPx = 0f
                            appliedSteps = 0
                            baseValue = currentValue.value
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            val flingVelocityY = velocityTracker.calculateVelocity().y
                            if (abs(flingVelocityY) > flingPxPerSecondPerStep) {
                                // Negativo = flick hacia arriba (sube);
                                // positivo hacia abajo (baja) — mismo
                                // sentido que el arrastre normal de más
                                // arriba.
                                val flingSteps = (-flingVelocityY / flingPxPerSecondPerStep).toInt()
                                if (flingSteps != 0) {
                                    val newValue = (currentValue.value + flingSteps).coerceIn(GRID_AXIS_RANGE)
                                    latestOnValueChange.value(newValue)
                                }
                            }
                        },
                        onDragCancel = { }
                    ) { change, dragAmount ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        dragAccumulatorPx += dragAmount
                        // Arriba (recorrido acumulado negativo) sube el
                        // valor; abajo lo baja — mismo sentido "natural"
                        // que arrastrar un fader hacia arriba para subir.
                        val targetSteps = (-dragAccumulatorPx / pxPerStep).toInt()
                        if (targetSteps != appliedSteps) {
                            appliedSteps = targetSteps
                            val newValue = (baseValue + appliedSteps).coerceIn(GRID_AXIS_RANGE)
                            latestOnValueChange.value(newValue)
                        }
                    }
                }
                .width(28.dp)
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                value.toString(),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        GridStepperButton(
            iconRes = R.drawable.ic_add,
            contentDescription = "Más $label",
            enabled = canIncrease,
            onClick = { if (currentValue.value < GRID_AXIS_RANGE.last) onValueChange(currentValue.value + 1) }
        )
    }
}

/**
 * Botón chico y redondo para el stepper — 24dp, para no ensanchar el
 * menú. Con AUTO-REPETICIÓN al mantener apretado: el primer toque aplica
 * un paso al instante (como siempre), y si el dedo se queda apretando
 * más de ~380ms, arranca a repetir a velocidad tope constante (45ms entre
 * pasos, sin rampa) hasta que se suelta. Mismo estándar de cualquier
 * stepper numérico de software profesional (los +/- de iOS/macOS, los
 * steppers de Premiere y Photoshop). El círculo se ve un toque más claro
 * mientras está apretado, para que el auto-repeat tenga feedback visual
 * de que el botón sigue "activo".
 */
@Composable
private fun GridStepperButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    // Referencia siempre-actualizada a onClick — el gesto de auto-repeat
    // vive en un pointerInput de vida larga (keyed por `enabled`, no por
    // `onClick`, para que CADA paso que dispara onClick() y cambia el
    // valor no reinicie el gesto a mitad de un apretón sostenido).
    val latestOnClick = rememberUpdatedState(onClick)
    var isPressed by remember { mutableStateOf(false) }
    val backgroundAlpha = when {
        !enabled -> 0.03f
        isPressed -> 0.18f
        else -> 0.08f
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha))
            .then(
                if (enabled) {
                    Modifier.pointerInput(enabled) {
                        awaitEachGesture {
                            awaitFirstDown()
                            isPressed = true
                            try {
                                latestOnClick.value()
                                // Misma corrección que en SliderStepButton
                                // (EditorScreen.kt): la única pausa larga
                                // es la PRIMERA, para que un toque simple
                                // dispare un solo paso; apenas vence esa
                                // espera, salta directo a velocidad tope
                                // constante — nada de rampa gradual que se
                                // sienta como si "fuera tomando impulso".
                                var waitMs = 380L
                                while (true) {
                                    val released = withTimeoutOrNull(waitMs) { waitForUpOrCancellation() }
                                    if (released != null) break
                                    latestOnClick.value()
                                    waitMs = 45L
                                }
                            } finally {
                                isPressed = false
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 0.9f else 0.3f),
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Centra el Popup horizontalmente bajo su ancla (no alineado al borde
 * izquierdo, como el popup angosto de capa en TimelineView.kt —
 * ese sirve para paneles angostos pegados a un ícono chico; este menú es
 * más ancho que el ícono de cuadrícula que lo abre, así que centrarlo se
 * ve mejor). Recorta contra los bordes de la pantalla para que nunca
 * quede cortado si el ícono está cerca del borde derecho.
 */
internal class BelowAnchorCenteredPopupPositionProvider(
    private val gapPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
        var x = anchorCenterX - popupContentSize.width / 2
        x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = anchorBounds.bottom + gapPx
        return IntOffset(x, y)
    }
}

@Composable
private fun SaveStatusLabel(saveState: SaveState) {
    val (text, color) = when (saveState) {
        is SaveState.Idle -> "" to Color.Transparent
        is SaveState.Saving -> "Guardando…" to MaterialTheme.colorScheme.onSurfaceVariant
        is SaveState.Saved -> "Guardado" to MaterialTheme.colorScheme.primary
        is SaveState.Error -> "No se pudo guardar" to MaterialTheme.colorScheme.error
    }
    if (text.isNotEmpty()) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * Ventana que abre el "+" de la fila de agregar pista, debajo del master
 * en el timeline. El "+" crea CAPAS NUEVAS (como en la playlist de FL Studio
 * Mobile): "Imagen" una capa de imagen y "Audio" una capa de audio, tantas
 * como se quieran. Cargar otro clip en una capa de audio ya existente NO se
 * hace desde acá, sino tocando su carril (botón "+Clip" / doble toque).
 *
 * Ya no dice "Agregar pista" (término de producción musical) — cada opción
 * es directamente la acción en sí, con su propio ícono real. Modelo 3D /
 * Grabar audio se suman como filas nuevas más adelante, en este mismo
 * diálogo.
 */
@Composable
private fun AddTrackDialog(
    onDismiss: () -> Unit,
    onImportImageClick: () -> Unit,
    onImportAudioClick: () -> Unit
) {
    // Tamaño fijo y cuadrado (ancho == alto aprox.), como el popup
    // compacto de FL Studio Mobile — antes usaba fillMaxWidth() y se
    // estiraba al ancho de pantalla completo, quedando una franja
    // horizontal en vez de una ventana cuadrada.
    val dialogSize = 260.dp
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier
                    .width(dialogSize)
                    .height(dialogSize)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Antes decía "Agregar pista" (término de producción
                    // musical, no aplica a esta app de películas) y el "+"
                    // vivía suelto como botón flotante en la esquina de la
                    // pantalla. Ahora "Imagen" es directamente la opción
                    // clickeable, sin ese título — con su ícono real de
                    // imagen (antes tenía el "+" genérico, que ahora queda
                    // reservado como glifo de "agregar pista" en general,
                    // no de esta opción en particular).
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onImportImageClick)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_image_placeholder),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Imagen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // --- "Audio": crea una capa de audio NUEVA. Dispara el
                // selector nativo de archivos (`onImportAudioClick` →
                // `pickAudioLauncher` en MainActivity → `viewModel.importAudio`).
                // Más clips en una capa existente se cargan desde su carril.
                // Mismo ícono (`ic_music_note`) que el reproductor flotante de
                // audio.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onImportAudioClick)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Diálogo compacto para renombrar el proyecto actual desde dentro del editor. */
@Composable
private fun RenameProjectDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Renombrar proyecto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Guardar") }
                }
            }
        }
    }
}
