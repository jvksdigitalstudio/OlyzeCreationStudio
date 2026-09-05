package com.yeivikas.olyzecs.ui

import android.content.Intent
import android.graphics.Bitmap
import com.yeivikas.olyzecs.debug.AppLogger
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import com.yeivikas.olyzecs.ui.theme.effectiveLayerColorStrong
import com.yeivikas.olyzecs.ui.theme.layerTrackColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.animation.EasingType
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.core.PixelColorSource
import com.yeivikas.olyzecs.engine.scene.Layer
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
import com.yeivikas.olyzecs.engine.mesh3d.Extrude3D
import com.yeivikas.olyzecs.data.ImageDecoding
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
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
private class Extrude3DGestureBridge {
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
private class DistortionGestureBridge {
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
    onImportAudioClick: () -> Unit,
    // Elegir una foto para una casilla de elenco/personajes (0..3) del
    // panel "Información del proyecto" — mismo patrón que
    // onReplaceImageClick, pero con índice de casilla en vez de layerId.
    onPickCastPhotoClick: (Int) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    // Ventana que abre el "+" debajo del master en el timeline — todavía
    // vacía a propósito, es el punto de entrada para agregar pistas nuevas
    // al playlist una vez que se defina qué tipos va a soportar.
    var showAddTrackDialog by remember { mutableStateOf(false) }
    var layerPendingDelete by remember { mutableStateOf<Layer?>(null) }
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
    // así que el pedido/resultado tiene que pasar por acá — pero la UI
    // solo conoce el contrato [PixelColorSource] (engine/core), no la
    // clase concreta que lo implementa (GLRenderer). ---
    var pixelColorSource by remember { mutableStateOf<PixelColorSource?>(null) }
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

    val selectedLayer = viewModel.currentSelectedLayer()
    val currentFrame = selectedLayer?.let { viewModel.frameAt(it, state.playheadMs) }
    var isFullscreen by remember { mutableStateOf(false) }
    var selectedPanel by remember(state.selectedLayerId) { mutableStateOf(0) }
    // --- Qué panel de la barra Keyframes/Control/Rack está abierto ahora
    // mismo (null = ninguno, se ve el timeline normal). Tocar la pestaña ya
    // abierta la cierra; tocar otra distinta cambia a esa. ---
    var expandedBottomSection by remember { mutableStateOf<BottomBarSection?>(null) }

    // --- Panel de "Información del proyecto" (título, sinopsis, créditos):
    // reemplaza TODA la zona de abajo (regla + capas + barra
    // Keyframes/Control/Rack), no se superpone parcial como
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
    LaunchedEffect(state.isPlaying, state.audioClip?.sourceUri, state.audioClip?.muted) {
        viewModel.syncAudioPreview(context)
    }
    // El volumen sí se aplica en caliente sin reiniciar la reproducción.
    LaunchedEffect(state.audioClip?.volume) {
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
    fun syncSlidersFromModel() {
        val frame = viewModel.currentSelectedLayer()?.let { viewModel.frameAt(it, state.playheadMs) }
        translateX = frame?.translateX ?: 0f
        translateY = frame?.translateY ?: 0f
        scale = frame?.scale ?: 1f
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
    //  - esa capa se centra en el canvas (translateX/Y se llevan a 0,0;
    //    se guarda la posición original en editModeOriginalTranslate para
    //    devolverla tal cual estaba al salir).
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
    // Arranca en 8f (uno más que el mayor valor inicial de las OCHO
    // ventanas — Recolor/Color Básico/3D Básico/Contorno/Resplandor/
    // Sombra/Reflejo/Distorsión, ver más abajo) para que la PRIMERA vez
    // que el usuario toque cualquiera de las ocho, quede garantizado que
    // pasa al frente — sin ese margen, tocar la que ya nació con el valor
    // inicial más alto no cambiaría nada visible la primera vez.
    var floatingWindowZOrderCounter by remember { mutableStateOf(8f) }
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
    var effectsLiveBitmap by remember(editModeLayerId) { mutableStateOf<Bitmap?>(null) }
    var effectsFullBitmap by remember(editModeLayerId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(editModeLayerId) {
        val layerForEffects = selectedLayer
        if (editModeLayerId != null && layerForEffects != null && editModeLayerId == layerForEffects.id) {
            val small = withContext(Dispatchers.IO) {
                ImageDecoding.decodeSampledFromUri(context.contentResolver, layerForEffects.sourceUri, maxDimension = 260)
            }
            effectsLiveBitmap = small
            effectsFullBitmap = withContext(Dispatchers.IO) {
                ImageDecoding.decodeSampledFromUri(context.contentResolver, layerForEffects.sourceUri, maxDimension = ImageDecoding.NO_LIMIT)
            }
        }
    }
    // `editImageEffectsCategory`: índice de categoría dentro de
    // `effectsTopCategories` (ver EffectsPanel) — antes vivía adentro de
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
    // Rack ocupando la mitad de abajo de la pantalla, aunque ya no
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
    var editModeOriginalTranslate by remember { mutableStateOf<Offset?>(null) }
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
    // Keyframes/Control/Rack)— e iba directo a la lista de proyectos,
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
        val original = editModeOriginalTranslate
        if (original != null) {
            translateX = original.x
            translateY = original.y
        }
        editModeLayerId = null
        editModeOriginalTranslate = null
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
    BackHandler(enabled = true) {
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
        editModeOriginalTranslate = Offset(translateX, translateY)
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
        editModeLayerId = layer.id
    }

    // Cambiar de capa o deshacer/rehacer siempre resincroniza, incluso si
    // en ese instante se está grabando (son ediciones explícitas del
    // usuario, no el mero paso del tiempo).
    LaunchedEffect(state.selectedLayerId, state.undoRedoTick) {
        syncSlidersFromModel()
        // Mismo criterio para el modo edición dedicado: es por-capa, así
        // que cambiar de selección (desde el timeline, undo/redo, etc.)
        // lo cierra en vez de dejarlo aislando una capa que ya no está
        // seleccionada.
        if (editModeLayerId != null && editModeLayerId != state.selectedLayerId) {
            editModeLayerId = null
            editModeOriginalTranslate = null
            editModeOriginalSourceUri = null
        }
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
        viewModel.selectLayer(layerId)
        syncSlidersFromModel()
    }
    // El scrubbing manual del playhead (arrastrar el scrubber o tocar el
    // timeline) resincroniza los sliders SOLO cuando no se está grabando
    // — durante la grabación el playhead avanza solo, 16ms en 16ms, y
    // resincronizar en cada tick reiniciaría el gesto en curso.
    LaunchedEffect(state.playheadMs) {
        if (!state.isRecording) syncSlidersFromModel()
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
                                tint = if (headerUndoEnabled) Color.White else Color.White.copy(alpha = 0.3f)
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
                                tint = if (headerRedoEnabled) Color.White else Color.White.copy(alpha = 0.3f)
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canvasFillsScreen) Modifier.weight(1f)
                        else Modifier.fillMaxHeight(0.46f)
                    )
                    .background(ChromaKeyGreen)
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
                                        scaleYVal = scaleY
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
                                            scaleYVal = scaleY
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
                                        scaleYVal = scaleY
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
                                                    rotation = normalizeRotationDeg(rotation - deltaAngle)
                                                    prevAngle = angle
                                                    pendingCommit = true
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCommitAtMs >= 120L) {
                                                        commitLiveFrame(); lastCommitAtMs = now; pendingCommit = false
                                                    }
                                                }
                                                if (pendingCommit) commitLiveFrame()
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
                                                    scale = (scale * (dist / prevDist)).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
                                                    prevDist = dist
                                                    pendingCommit = true
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCommitAtMs >= 120L) {
                                                        commitLiveFrame(); lastCommitAtMs = now; pendingCommit = false
                                                    }
                                                }
                                                if (pendingCommit) commitLiveFrame()
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
                                                    scaleX = if (rawScaleX >= 0f) mag else -mag
                                                    pendingCommit = true
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCommitAtMs >= 120L) {
                                                        commitLiveFrame(); lastCommitAtMs = now; pendingCommit = false
                                                    }
                                                }
                                                if (pendingCommit) commitLiveFrame()
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
                                                    scaleY = if (rawScaleY >= 0f) mag else -mag
                                                    pendingCommit = true
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCommitAtMs >= 120L) {
                                                        commitLiveFrame(); lastCommitAtMs = now; pendingCommit = false
                                                    }
                                                }
                                                if (pendingCommit) commitLiveFrame()
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
                                    if (layer != null && latestGridEnabledForSnap.value && latestGridSnapEnabledForSnap.value) {
                                        // --- AABB real de la capa (con
                                        // rotación/escala actuales) para
                                        // poder ofrecer sus 3 puntos de
                                        // referencia por eje (borde
                                        // inicial, centro, borde final) —
                                        // ver [layerAabbHalfExtentsPx].
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
                                        val (snappedX, snappedY) = snapTranslateToGrid(
                                            translateX = rawTranslateX,
                                            translateY = rawTranslateY,
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
                                        translateX = rawTranslateX
                                        translateY = rawTranslateY
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
                    getLayers = {
                        val allLayers = viewModel.uiState.value.layers
                        // Modo edición dedicado: solo se dibuja la capa
                        // aislada, el resto "desaparece" del canvas — tal
                        // cual se pidió ("que se centre... y que todas
                        // desaparezca que solo quede esa imagen").
                        val editId = latestEditModeLayerId.value
                        if (editId != null) {
                            allLayers.filter { it.id == editId }
                        } else {
                            allLayers
                        }
                    },
                    // FASE 2 — mismo filtro de modo edición aislada que
                    // [getLayers] de arriba (aplicado también acá para
                    // que GLRenderer nunca dibuje, con datos lógicos de,
                    // una capa que ese mismo frame decidió no subir a GPU).
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
                    onRendererReady = { pixelColorSource = it }
                )
                if (state.isImporting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                if (state.isLoadingProject) {
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
                        scaleYVal = scaleY
                    )
                    if (corners != null && corners.size == 4) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val frameColor = BrandPurpleLight
                            val strokeW = 2.dp.toPx()
                            for (i in corners.indices) {
                                val start = corners[i]
                                val end = corners[(i + 1) % corners.size]
                                drawLine(frameColor, start, end, strokeW)
                            }
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
                                // (ver el `pointerInput`, más arriba) son SIEMPRE el mismo.
                                val slotOffsets = clampedHandleSlots(
                                    corners,
                                    size,
                                    HANDLE_BADGE_CLAMP_MARGIN_DP.toPx()
                                )
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
                            scaleYVal = scaleY
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

                /*
                // --- Barra de reproducción tipo video player, integrada al preview ---
                // Comentado a pedido: esta línea de tiempo (scrubber con
                // timecodes) y el botón de pantalla completa se van a
                // reusar más adelante, reubicados en la pantalla de
                // preview de exportación — no en la pantalla de edición en
                // tiempo real, que ahora queda más limpia. Grabar y
                // Play/Pausa ya se movieron arriba, fijos sobre el preview
                // (ver el Row nuevo antes de este Box).
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    // Línea de tiempo fina con el timecode en cada extremo
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatTimecode(state.playheadMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Slider(
                            value = state.playheadMs.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..state.projectDurationMs.toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            formatTimecode(state.projectDurationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Grabar — Play grande — Pantalla completa
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { viewModel.toggleRecording() },
                            modifier = Modifier
                                .size(40.dp)
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
                                    .size(22.dp)
                                    .alpha(if (state.isCapturing) recordBlink else 1f)
                            )
                        }

                        // Mismo ajuste que en la barra superior: 4dp extra
                        // para emparejar la sensación visual con el hueco
                        // Retroceder↔Play (ver comentario completo arriba).
                        Spacer(modifier = Modifier.width(20.dp))

                        IconButton(
                            onClick = { viewModel.resetPlaybackState() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_skip_to_start),
                                contentDescription = "Volver al principio",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = { viewModel.togglePlayback() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                painter = painterResource(id = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_fullscreen),
                                contentDescription = if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                */

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
                                    pixelColorSource?.requestPixelColor(
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
                                            pixelColorSource?.requestPixelColor(
                                                lastPos.x.roundToInt(),
                                                lastPos.y.roundToInt()
                                            ) { argb -> eyedropperLiveArgb = argb }
                                        } else {
                                            // Dedo levantado: confirma el ÚLTIMO color
                                            // visto en la lupa como el elegido.
                                            val layerId = targetLayerId
                                            if (layerId != null) {
                                                pixelColorSource?.requestPixelColor(
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
            }

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
            // capas + la barra Keyframes/Control/Rack, todo junto). Ahora
            // ese Box exterior es el padre común: adentro, una Column
            // nueva reproduce EXACTAMENTE el mismo reparto de alto que
            // había antes (timeline con weight(1f) + EditorBottomBar con
            // su alto fijo) — mismo resultado visual cuando el panel está
            // cerrado — y al lado, como hermano de esa Column dentro del
            // mismo Box, va el panel nuevo, que al ocupar fillMaxSize()
            // cubre justo ese alto combinado.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                TimelineView(
                    layers = state.layers,
                    // Ver comentario en TimelineView.kt: sin esto, el
                    // reordenamiento por arrastre "regresaba" a su lugar
                    // anterior al soltar el dedo, porque moveLayerUp/Down
                    // mutan el zIndex de los mismos objetos Layer sin
                    // cambiar la referencia de forma que Compose lo note.
                    revision = state.revision,
                    selectedLayerId = state.selectedLayerId,
                    playheadMs = state.playheadMs,
                    projectDurationMs = state.projectDurationMs,
                    onSeek = { viewModel.seekTo(it) },
                    onSelectLayer = { selectLayerAndSync(it) },
                    onRetimeKeyframe = { layerId, oldMs, newMs -> viewModel.retimeKeyframe(layerId, oldMs, newMs) },
                    onToggleLayerVisibility = { viewModel.toggleLayerVisibility(it) },
                    onToggleLayerLock = { viewModel.toggleLayerLock(it) },
                    onToggleLayerOrderLock = { viewModel.toggleLayerOrderLock(it) },
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
                    onChangeMultipleLayersGradient = { layerIds, startArgb, endArgb, angleDegrees, isRadial, useBW -> viewModel.setLayersGradient(layerIds, startArgb, endArgb, angleDegrees, isRadial, useBW) },
                    onResetMultipleLayersColor = { layerIds -> viewModel.resetLayersColor(layerIds) },
                    onRequestEyedropper = { layerId -> eyedropperActiveForLayerId = layerId },
                    eyedropperResult = eyedropperPickedColor,
                    onConsumeEyedropperResult = { eyedropperPickedColor = null },
                    onReorderLayer = { layerId, steps -> viewModel.reorderLayer(layerId, steps) },
                    onDeleteLayerRequest = { layer -> layerPendingDelete = layer },
                    onScrubStart = { viewModel.beginScrub() },
                    onScrubEnd = { viewModel.endScrub() },
                    onAddTrackClick = { showAddTrackDialog = true },
                    // --- Ver el comentario grande en TimelineRow/
                    // LayerActionAccordion (TimelineView.kt): mientras
                    // cualquiera de las tres pestañas de abajo (Keyframes/
                    // Control/Rack) está abierta, el panel de opciones de
                    // cada capa (lápiz/paleta/ojo/candados/basura) tiene
                    // que dejar de abrirse "al costado" — ahí mismo es
                    // donde vive el panel de la pestaña abierta (ver
                    // SectionPlaceholderPanel más abajo) y quedaba tapado.
                    // expandedBottomSection ya es la única fuente de
                    // verdad de "qué pestaña está abierta" (null = ninguna),
                    // así que este booleano es simplemente esa pregunta.
                    isBottomPanelExpanded = expandedBottomSection != null,
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

                // --- Panel vacío de Keyframes / Control / Rack: se
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = LABEL_COLUMN_WIDTH, top = RULER_HEIGHT)
                            .zIndex(5f)
                    )
                }
            }

            // --- Cabecera de secciones Keyframes / Control / Rack ---
            // Antes acá solo había un relleno morado sólido de 16dp para
            // cerrar el borde inferior del timeline. Ahora ese espacio pasa
            // a ser esta barra de navegación entre paneles: arranca justo
            // donde termina la columna de miniaturas de las capas (nunca
            // desde el borde izquierdo de la pantalla — el hueco de la
            // izquierda deja ver el mismo relleno morado de fondo), y ofrece
            // tres secciones — Rack a la derecha, Control al medio,
            // Keyframes a la izquierda — por ahora puramente visuales,
            // cada una recibe su función más adelante.
            EditorBottomBar(
                modifier = Modifier.fillMaxWidth(),
                selectedSection = expandedBottomSection,
                onKeyframesClick = {
                    expandedBottomSection = if (expandedBottomSection == BottomBarSection.KEYFRAMES) null
                        else BottomBarSection.KEYFRAMES
                },
                onControlClick = {
                    expandedBottomSection = if (expandedBottomSection == BottomBarSection.CONTROL) null
                        else BottomBarSection.CONTROL
                },
                onRackClick = {
                    expandedBottomSection = if (expandedBottomSection == BottomBarSection.RACK) null
                        else BottomBarSection.RACK
                }
            )
            } // fin de la Column interna (timeline + EditorBottomBar)

            // --- Panel "Editando imagen": tapa TODO lo de abajo (ruler +
            // capas + la barra Keyframes/Control/Rack) de punta a punta,
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
                            liveBitmap = effectsLiveBitmap,
                            fullBitmap = effectsFullBitmap
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
            // (regla, capas, barra Keyframes/Control/Rack) de punta a
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

            // --- Panel de controles ---
            // COMENTADO A PROPÓSITO (no borrar): Cámara / Look
            // cinematográfico / Audio / Tiempo van a pasar a ser módulos
            // cargables independientes más adelante — por ahora se deja
            // todo el bloque original intacto pero apagado, para no
            // perder nada del comportamiento cuando se ordene.
            /*
            // La cabecera (línea divisoria + pestañas Cámara/Look) queda
            // FIJA; solo el contenido de la pestaña activa hace scroll
            // debajo, como en cualquier app de edición profesional.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.54f)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (state.layers.isNotEmpty()) {
                    TabRow(
                        selectedTabIndex = selectedPanel,
                        modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp))
                    ) {
                        Tab(
                            selected = selectedPanel == 0,
                            onClick = { selectedPanel = 0 },
                            text = { Text("Cámara") }
                        )
                        Tab(
                            selected = selectedPanel == 1,
                            onClick = { selectedPanel = 1 },
                            text = { Text("Look cinematográfico") }
                        )
                        Tab(
                            selected = selectedPanel == 2,
                            onClick = { selectedPanel = 2 },
                            text = { Text("Audio") }
                        )
                        Tab(
                            selected = selectedPanel == 3,
                            onClick = { selectedPanel = 3 },
                            text = { Text("Tiempo") }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {

                if (selectedPanel == 3) {
                    // --- Tiempo: velocidad variable y freeze frame, a nivel de proyecto ---
                    TimeRampPanel(
                        playheadMs = state.playheadMs,
                        projectDurationMs = state.projectDurationMs,
                        speedKeyframes = state.speedKeyframes,
                        freezeFrames = state.freezeFrames,
                        outputDurationMs = viewModel.currentOutputDurationMs(),
                        speedAtPlayhead = viewModel.speedAtPlayhead(),
                        onSetSpeedHere = { viewModel.addOrReplaceSpeedKeyframe(it) },
                        onRemoveSpeedHere = { viewModel.removeSpeedKeyframeAtPlayhead() },
                        onAddFreezeHere = { viewModel.addFreezeFrameAtPlayhead(it) },
                        onRemoveFreeze = { viewModel.removeFreezeFrame(it) },
                        onSeekTo = { viewModel.seekTo(it) }
                    )
                } else if (selectedPanel == 2) {
                    // --- Audio: a nivel de proyecto, no depende de la capa seleccionada ---
                    AudioPanel(
                        audioClip = state.audioClip,
                        isImporting = state.isImportingAudio,
                        onImportClick = onImportAudioClick,
                        onRemove = { viewModel.removeAudio() },
                        onVolumeChange = { viewModel.setAudioVolume(it) },
                        onToggleMute = { viewModel.toggleAudioMute() },
                        onTrimStartChange = { viewModel.setAudioTrimStart(it) },
                        onLoopChange = { viewModel.setAudioLoop(it) },
                        onFadeChange = { fadeIn, fadeOut -> viewModel.setAudioFade(fadeIn, fadeOut) }
                    )
                } else if (selectedLayer != null) {
                    if (selectedPanel == 0) {
                    // --- Cámara: independiente por capa ---
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cámara — ${selectedLayer.name}", style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onReplaceImageClick(selectedLayer.id) }, enabled = !selectedLayer.locked) {
                                    Text("Reemplazar imagen")
                                }
                                IconButton(
                                    onClick = {
                                        translateX = 0f; translateY = 0f; scale = 1f; rotation = 0f; alpha = 1f
                                        tiltX = 0f; tiltY = 0f; focusBlur = 0f; dollyZoom = 0f
                                        scaleX = 1f; scaleY = 1f
                                        commitLiveFrame()
                                    },
                                    enabled = !selectedLayer.locked
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Resetear encuadre")
                                }
                            }
                        }

                        // Mismo límite dinámico que el arrastre con el dedo
                        // en el canvas (ver el comentario grande junto a
                        // `panLimit` más arriba en este archivo): en
                        // `scale = 1` da ±2f, igual que antes; con zoom
                        // alto, crece proporcionalmente para poder llegar a
                        // cualquier borde de la imagen también desde estos
                        // sliders, no solo arrastrando.
                        val panLimit = 2f * maxOf(scale, scaleX, scaleY, 1f)
                        LabeledSlider("Pan X", translateX, -panLimit..panLimit, enabled = !selectedLayer.locked) { translateX = it; commitLiveFrame() }
                        LabeledSlider("Pan Y", translateY, -panLimit..panLimit, enabled = !selectedLayer.locked) { translateY = it; commitLiveFrame() }
                        LabeledSlider("Zoom", scale, 0.2f..5f, enabled = !selectedLayer.locked) { scale = it; commitLiveFrame() }
                        LabeledSlider("Rotación (giro plano)", rotation, -180f..180f, enabled = !selectedLayer.locked) { rotation = it; commitLiveFrame() }

                        Text(
                            "Tilt 3D (cámara real, no giro plano)",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        LabeledSlider("Tilt vertical (arriba/abajo)", tiltX, -45f..45f, enabled = !selectedLayer.locked) { tiltX = it; commitLiveFrame() }
                        LabeledSlider("Tilt horizontal (paneo lateral)", tiltY, -45f..45f, enabled = !selectedLayer.locked) { tiltY = it; commitLiveFrame() }
                        LabeledSlider("Enfoque (rack focus)", focusBlur, 0f..1f, enabled = !selectedLayer.locked) { focusBlur = it; commitLiveFrame() }
                        LabeledSlider("Dolly zoom (efecto Vértigo)", dollyZoom, -1f..1f, enabled = !selectedLayer.locked) { dollyZoom = it; commitLiveFrame() }

                        LabeledSlider("Opacidad", alpha, 0f..1f, enabled = !selectedLayer.locked) { alpha = it; commitLiveFrame() }
                        LabeledSlider(
                            "Parallax (fondo=bajo, sujeto=1.0)",
                            selectedLayer.parallaxFactor,
                            0f..1f,
                            enabled = !selectedLayer.locked
                        ) { viewModel.setParallaxFactor(selectedLayer.id, it) }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.addKeyframeToSelectedLayer(
                                        translateX, translateY, scale, rotation, alpha, tiltX, tiltY, focusBlur, dollyZoom, scaleX, scaleY, EasingType.EASE_IN_OUT
                                    )
                                },
                                enabled = !selectedLayer.locked
                            ) {
                                Text("Fijar keyframe aquí")
                            }
                            OutlinedButton(
                                onClick = { viewModel.removeKeyframeAtPlayhead() },
                                enabled = !selectedLayer.locked
                            ) {
                                Text("Quitar keyframe")
                            }
                        }

                        Text(
                            "Keyframes: ${selectedLayer.cameraTrack.keyframes.joinToString { "${it.timeMs}ms" }}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    } else {
                    // --- Look cinematográfico: independiente por capa ---
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Look cinematográfico — ${selectedLayer.name}", style = MaterialTheme.typography.titleSmall)

                        val look = selectedLayer.lookSettings
                        val lockedNow = selectedLayer.locked

                        Text("Exposición y color", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                        LabeledSlider("Exposición", look.exposure, -1f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(exposure = it))
                        }
                        LabeledSlider("Saturación", look.saturation, 0f..2f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(saturation = it))
                        }
                        LabeledSlider("Contraste", look.contrast, 0.5f..1.8f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(contrast = it))
                        }
                        LabeledSlider("Temperatura (frío/cálido)", look.warmth, -1f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(warmth = it))
                        }
                        LabeledSlider("Tinte (verde/magenta)", look.tint, -1f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(tint = it))
                        }

                        Text("Sombras y luces", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                        LabeledSlider("Levantar sombras", look.shadowsLift, 0f..0.3f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(shadowsLift = it))
                        }
                        LabeledSlider("Suavizar luces altas", look.highlightsRolloff, 0f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(highlightsRolloff = it))
                        }
                        LabeledSlider("Split-tone cine (teal/naranja)", look.splitToneIntensity, 0f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(splitToneIntensity = it))
                        }

                        Text("Efectos de lente y film", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                        LabeledSlider("Viñeta", look.vignetteIntensity, 0f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(vignetteIntensity = it))
                        }
                        LabeledSlider("Grano de película", look.grainIntensity, 0f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(grainIntensity = it))
                        }
                        LabeledSlider("Glow (brillo energía)", look.glowIntensity, 0f..1.5f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(glowIntensity = it))
                        }
                        LabeledSlider("Umbral del glow", look.glowThreshold, 0.3f..0.95f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(glowThreshold = it))
                        }
                        LabeledSlider("Vibración de cámara (handheld)", look.cameraShakeIntensity, 0f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(cameraShakeIntensity = it))
                        }

                        Text("Óptica de lente (nivel estudio)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                        LabeledSlider("Distorsión de lente (cojín/barril)", look.lensDistortion, -1f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(lensDistortion = it))
                        }
                        LabeledSlider("Aberración cromática", look.chromaticAberration, 0f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(chromaticAberration = it))
                        }
                        LabeledSlider("Lens flare anamórfico", look.lensFlareIntensity, 0f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(lensFlareIntensity = it))
                        }
                        LabeledSlider("Bokeh anamórfico (estira el enfoque)", look.anamorphicBokeh, 0f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(anamorphicBokeh = it))
                        }
                        LabeledSlider("Motion blur (según velocidad de cámara)", look.motionBlurIntensity, 0f..1f, enabled = !lockedNow) {
                            viewModel.updateLookSettings(selectedLayer.id, look.copy(motionBlurIntensity = it))
                        }

                        Text("Presets de estudio", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.updateLookSettings(selectedLayer.id, LookSettings()) },
                                enabled = !lockedNow
                            ) {
                                Text("Resetear")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateLookSettings(
                                        selectedLayer.id,
                                        LookSettings(
                                            saturation = 1.15f, contrast = 1.15f, warmth = -0.35f,
                                            vignetteIntensity = 0.55f, grainIntensity = 0.2f,
                                            glowIntensity = 0.8f, glowThreshold = 0.6f
                                        )
                                    )
                                },
                                enabled = !lockedNow
                            ) {
                                Text("Sci-Fi oscuro")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateLookSettings(
                                        selectedLayer.id,
                                        LookSettings(
                                            saturation = 1.1f, contrast = 1.2f,
                                            splitToneIntensity = 0.6f, shadowsLift = 0.03f,
                                            highlightsRolloff = 0.2f, vignetteIntensity = 0.3f
                                        )
                                    )
                                },
                                enabled = !lockedNow
                            ) {
                                Text("Teal & Naranja")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateLookSettings(
                                        selectedLayer.id,
                                        LookSettings(
                                            saturation = 1.3f, contrast = 1.25f, warmth = -0.2f,
                                            glowIntensity = 1.1f, glowThreshold = 0.55f,
                                            vignetteIntensity = 0.4f, splitToneIntensity = 0.3f
                                        )
                                    )
                                },
                                enabled = !lockedNow
                            ) {
                                Text("Neón Cyberpunk")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateLookSettings(
                                        selectedLayer.id,
                                        LookSettings(
                                            saturation = 0.75f, contrast = 0.9f, warmth = 0.3f,
                                            shadowsLift = 0.12f, highlightsRolloff = 0.35f,
                                            grainIntensity = 0.45f, vignetteIntensity = 0.35f
                                        )
                                    )
                                },
                                enabled = !lockedNow
                            ) {
                                Text("Película vintage")
                            }
                        }
                    }
                    }
                } else {
                    Text("Importa imágenes con el botón + para empezar", modifier = Modifier.padding(16.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
            }
            */

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
                liveBitmap = effectsLiveBitmap,
                fullBitmap = effectsFullBitmap,
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
                liveBitmap = effectsLiveBitmap,
                fullBitmap = effectsFullBitmap,
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
                liveBitmap = effectsLiveBitmap,
                fullBitmap = effectsFullBitmap,
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
                liveBitmap = effectsLiveBitmap,
                fullBitmap = effectsFullBitmap,
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

    // El diálogo queda abierto mientras el usuario lo pidió explícitamente
    // O mientras haya una exportación en curso / un resultado pendiente de
    // ver — así, si se cierra por error mientras exporta, el progreso
    // sigue siendo accesible tocando el ícono de exportar de nuevo.
    if (showExportDialog || state.exportProgress != null) {
        ExportDialog(
            projectName = state.projectName,
            aspect = state.exportAspect,
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
            onSelectGradient = { startArgb, endArgb, angleDegrees, isRadial, useBW ->
                layerPendingColorChange = null
                viewModel.setLayerGradient(layer.id, startArgb, endArgb, angleDegrees, isRadial, useBW)
            },
            onReset = { viewModel.resetLayerColor(layer.id) }
        )
    }

    layerPendingDelete?.let { layer ->
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

    // Diálogo de confirmación para CANCELAR el modo edición (ver la ×
    // dentro del modo edición aislado, más arriba). Avisa antes de volver
    // al canvas principal que la edición de esta sesión no se va a
    // aplicar. Tocar afuera o "No" no toca nada del estado — la capa
    // sigue en modo edición tal cual estaba.
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
    fun formatDisplayNumber(v: Float): String =
        ("%.4f".format(v)).trimEnd('0').trimEnd('.')
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
                            formatDisplayNumber(parsedDisplay.coerceIn(displayRange))
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
                        editingText = formatDisplayNumber(value * displayScale)
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

/** Formatea milisegundos como mm:ss, al estilo de cualquier reproductor de video. */
private fun formatTimecode(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
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
private fun colorDistanceSquared(a: Int, b: Int): Int {
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
 * Dos resoluciones de trabajo a propósito:
 *  - [liveBitmap] (chica, ~220px de lado) para que la extracción de
 *    paleta sea instantánea y cada frame de arrastre de la rueda se
 *    recoloree y suba a GL sin lag notable.
 *  - [fullBitmap] (hasta 1024px) que se decodifica aparte, en paralelo,
 *    y es la que de verdad se recolorea y persiste a disco (ver
 *    EditorViewModel.commitLayerRecolor) 500ms después del último
 *    cambio — así arrastrar rápido no dispara decenas de escrituras de
 *    archivo por segundo, pero el resultado final que se guarda es de
 *    mejor calidad que la vista previa liviana del arrastre.
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
    liveBitmap: Bitmap?,
    fullBitmap: Bitmap?,
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
            liveBitmap = liveBitmap,
            fullBitmap = fullBitmap,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

/**
 * Ventana flotante genérica estilo "plugin de audio" — A PEDIDO DEL
 * USUARIO: reemplaza el panel angosto y vertical pegado al borde
 * izquierdo (ver KDoc viejo de [RecolorFloatingWindow], que describía
 * ese layout ya retirado) por una ventanita que vive DENTRO del canvas,
 * se puede arrastrar desde su cabecera a cualquier parte de la pantalla
 * y redimensionar desde su esquina inferior derecha — el mismo patrón
 * que un plugin de audio flotante (FL Studio, VST hosts, etc: cabecera
 * arrastrable arriba, manija de tamaño en la esquina). El usuario pidió
 * explícitamente referenciarse en ese patrón sin copiar ningún plugin en
 * particular.
 *
 * Pensada para reusarse en el resto de herramientas de edición más
 * adelante (por ahora solo "Recolor" la usa — ver [RecolorFloatingWindow]
 * — a pedido explícito: "por mientras después en el resto de opciones se
 * aplicará esto").
 *
 * Primera versión, a pedido explícito del usuario ("por ahora está bien,
 * luego lo pulimos"): solo arrastre por la cabecera + redimensionado por
 * la esquina inferior derecha, sin snapping a los bordes del canvas, sin
 * recordar posición/tamaño entre sesiones y sin límite superior de
 * tamaño — el único límite real es [minSize], para que no se pueda
 * encoger tanto que el contenido deje de ser usable.
 *
 * A PEDIDO EXPLÍCITO DEL USUARIO — controles de cabecera estilo "plugin
 * de audio flotante" (referencia entregada: cabecera de un plugin de FL
 * Studio, "School Piano"), agregados del lado derecho de la cabecera,
 * ANTES del botón de cerrar, sin agrandar la fila de 38.dp de alto:
 * - [FloatingWindowPresetsControl]: "Presets" (o el nombre del preset
 *   activo, una vez elegido). Al tocarlo despliega — todavía vacía, a
 *   pedido explícito ("esto por ahora") — la lista de presets de ESTA
 *   ventana/módulo. El contenido real de esa lista llega después; lo que
 *   se arma ahora es el mecanismo completo (botón + estado de selección
 *   + panel desplegable) para que conectar presets reales más adelante
 *   sea solo llenar `presets`.
 * - Flechas `<` `>` ([ic_chevron_left]/[ic_chevron_right], mismos SVG
 *   premium que ya usa el resto de la app — no se creó ningún ícono
 *   nuevo): retroceden/avanzan un preset dentro de `presets`. Sin
 *   presets cargados quedan atenuadas y sin acción, en vez de romper o
 *   quedar “vivas” sin hacer nada.
 * - `-` ([ic_remove], mismo SVG que ya existe en la app): minimiza la
 *   ventana a solo su ícono ([FloatingWindowMinimizedBadge]) — un toque
 *   sobre ese ícono la restaura tal cual estaba, en el mismo lugar.
 */
private val FLOATING_WINDOW_HEADER_HEIGHT = 38.dp
private val FLOATING_WINDOW_MINIMIZED_BADGE_SIZE = 40.dp
// A PEDIDO EXPLÍCITO DEL USUARIO — imán al pie del header (referencia
// entregada con capturas: franja horizontal completa, pegada
// JUSTO AFUERA del header — no adentro de él —, de punta a punta de la
// pantalla). Reemplaza el intento anterior con los bordes laterales
// (retirado por completo a pedido, sin dejar rastro — ver conversación).
// Esta franja es mucho más confiable que aquella: es un chequeo de Y
// nada más (no de X), y el "techo" de esta zona es el CANVAS mismo —
// el Box que contiene las tres ventanas empieza justo debajo del
// header (con el `padding` del Scaffold ya restándole su alto), así
// que `offsetPx.y` cercano a 0 YA ES, sin necesitar medir nada más,
// "la ventana está pegada al pie del header". Al soltar ahí, se
// minimiza sola a su ícono — reusa el mismo mecanismo que ya existe
// para minimizar a mano (`isMinimized`).
private val FLOATING_WINDOW_TOP_MAGNET_MARGIN = 40.dp

// A PEDIDO EXPLÍCITO DEL USUARIO — "lanzar" la ventana hacia arriba
// desde CUALQUIER punto de la pantalla (no solo cuando ya está cerca
// del pie del header, como el imán de acá arriba) para que se minimice
// sola, igual que tirar una carta sobre un mazo: mantener pulsada la
// cabecera y soltarla con un golpe rápido hacia arriba la manda directo
// a minimizarse, sin tener que arrastrarla físicamente hasta la franja
// imán. Pedido explícito aparte, con el mismo mensaje: un arrastre
// LENTO — aunque cruce toda la pantalla de punta a punta — NO tiene que
// disparar esto, para poder seguir moviendo la ventana con calma sin
// que "se escape" sola hacia arriba. La distinción entre ambos casos es
// pura VELOCIDAD del gesto al soltar (ver `flungUpwardFast`, en el
// `pointerInput` de la cabecera, más abajo): un valor bien por encima
// de lo que un arrastre normal de reubicación alcanza, pero fácil de
// lograr con un golpe de muñeca deliberado.
//
// CALIBRACIÓN CORREGIDA DOS VECES (reportado por el usuario: "por más
// rápido que lanzo, la ventana nunca vuela sola"): 2600.dp/seg era
// irreal para un dedo humano, se bajó a 900.dp/seg — y el usuario
// reportó que SEGUÍA sin dispararse, todavía "muy veloz". La causa
// más probable no es solo el número: con un flick corto (el gesto
// pedido: tocar y lanzar casi sin recorrido) el `VelocityTracker`
// recibe pocas muestras antes del `up`, y su algoritmo de ajuste
// (mínimos cuadrados sobre el historial reciente) tiende a
// SUBESTIMAR la velocidad real cuando hay pocos puntos — así que la
// velocidad calculada queda por debajo de lo que el dedo hizo de
// verdad. Bajar el umbral de nuevo, bastante más, compensa esa
// subestimación y deja el gesto accesible con un flick corto y
// cómodo, sin exigir un golpe violento de muñeca.
private val FLOATING_WINDOW_FLING_MINIMIZE_VELOCITY_DP_PER_SEC = 450.dp
// Duración del "vuelo" animado de la ventana hasta el pie del header
// una vez detectado el lanzamiento — corto a propósito, para que se
// sienta como una respuesta inmediata al gesto y no como una animación
// decorativa de por sí.
private const val FLOATING_WINDOW_FLING_MINIMIZE_ANIMATION_MS = 180

// A PEDIDO EXPLÍCITO DEL USUARIO — ver el comentario grande sobre
// [FloatingWindowMinimizedRegistry.resolveFreeSlot] y sobre
// `settleMinimizedPosition`, unas líneas más abajo: duración del
// deslizamiento animado cuando un ícono recién minimizado tiene que
// correrse al lado de una ventana todavía abierta (o de otro ícono).
// Corto a propósito, igual que [FLOATING_WINDOW_FLING_MINIMIZE_ANIMATION_MS]:
// tiene que sentirse como una respuesta inmediata al gesto — "se corrió
// al lado" — no como una animación decorativa que demora el resultado.
private const val FLOATING_WINDOW_MINIMIZED_SETTLE_ANIMATION_MS = 160

// A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido (reportado con
// captura): al minimizar dos (o las tres) ventanas flotantes con el
// imán del pie del header, todas terminaban en el MISMO punto — el
// imán solo fuerza `offsetPx.y = 0f` (ver `magnetizeIfNearHeaderBottom`,
// más abajo) sin mirar nunca la coordenada X ni si ya había otro ícono
// minimizado ahí. Como las tres ventanas nacen escalonadas pero cerca
// unas de otras (ver `floatingWindowStaggerPx`, donde se instancian),
// arrastrarlas todas a la franja imán las dejaba prácticamente
// superpuestas — exactamente el ícono "doble" de la captura.
//
// Este registro es el mecanismo que lo corrige: cada [FloatingToolWindow]
// (Recolor / Color Básico / 3D Básico) se identifica acá con su propio
// `title` (únicos entre sí — ver los tres llamadores, más abajo) y, ni
// bien queda minimizada, registra su posición actual. Antes de asentarse
// en un punto — sea por el imán o por el botón "-" de la cabecera, o
// por arrastrarse ya minimizada encima de otra — cada ventana consulta
// [resolveFreeSlot] para saber si ese punto colisiona con algún OTRO
// ícono ya minimizado y, si colisiona, se corre a la derecha en línea
// hasta encontrar el primer hueco libre — nunca se apilan dos íconos en
// el mismo lugar.
private val FLOATING_WINDOW_MINIMIZED_BADGE_SPACING = 10.dp

private class FloatingWindowMinimizedRegistry {
    // `mutableStateMapOf` (no un `Map` a secas) a propósito: varias
    // ventanas leen este registro durante la composición de las OTRAS
    // (para calcular su propio hueco libre), así que un cambio acá tiene
    // que disparar recomposición en quien lo esté leyendo.
    private val positions = mutableStateMapOf<String, Offset>()

    // --- BUG REAL corregido (reportado con captura): [resolveFreeSlot]
    // solo esquivaba OTROS íconos ya minimizados (`positions`, acá
    // arriba) — nunca miraba si en ese punto había una ventana TODAVÍA
    // ABIERTA (cabecera + contenido, no solo su ícono). Resultado
    // exacto de la captura: al lanzar/minimizar una ventana hacia la
    // franja de arriba mientras otra ventana de efecto seguía abierta
    // ahí mismo, el ícono nuevo aterrizaba literalmente ENCIMA de esa
    // cabecera (tapando sus controles de Presets/‹/›), en vez de
    // correrse al costado — porque para [resolveFreeSlot] esa zona
    // estaba "libre": no había ningún OTRO ícono minimizado registrado
    // ahí, así que no tenía forma de saber que una ventana abierta
    // ocupaba ese lugar.
    //
    // La corrección agrega un segundo registro, en paralelo al de
    // íconos (`positions`): el rectángulo REAL (posición + tamaño
    // actuales) de cada [FloatingToolWindow] mientras está ABIERTA —
    // ver `registerOpenWindow`/`unregisterOpenWindow`, publicados desde
    // un `SideEffect` en [FloatingToolWindow] (se actualiza solo,
    // siguiendo la ventana en vivo mientras se arrastra o cambia de
    // alto/ancho, y desaparece apenas se minimiza o se cierra). Con eso,
    // [resolveFreeSlot] ahora también comprueba, antes que nada, si el
    // candidato cae ADENTRO del rectángulo de alguna ventana abierta —
    // y si es así, lo corre hasta pasar su borde derecho (más el mismo
    // margen que ya separa a dos íconos entre sí), quedando al LADO de
    // esa ventana en vez de tapada por/tapando a ella.
    private val openWindowRects = mutableStateMapOf<String, Rect>()

    /** Guarda/actualiza dónde está AHORA el ícono minimizado de [id]. */
    fun register(id: String, position: Offset) {
        positions[id] = position
    }

    /** Ventana restaurada (o cerrada): deja de contar para colisiones. */
    fun unregister(id: String) {
        positions.remove(id)
    }

    /**
     * Publica el rectángulo REAL (posición + tamaño, en px) de la
     * ventana [id] mientras está ABIERTA — ver el comentario grande de
     * más arriba. Se llama en cada composición desde un `SideEffect` en
     * [FloatingToolWindow], así que siempre refleja dónde está la
     * ventana AHORA MISMO, no una posición vieja.
     */
    fun registerOpenWindow(id: String, rect: Rect) {
        openWindowRects[id] = rect
    }

    /** Ventana minimizada (o cerrada): deja de contar para colisiones. */
    fun unregisterOpenWindow(id: String) {
        openWindowRects.remove(id)
    }

    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido (reportado con
    // capturas): todo lo de arriba (`resolveFreeSlot`) solo evita que un
    // ícono NUEVO aterrice encima de una ventana ya abierta — pero no
    // hace nada por un ícono que YA estaba minimizado y quieto en un
    // punto cuando OTRA ventana se abre (o crece) justo ENCIMA de él
    // después. Ese ícono quieto se quedaba tapado por la ventana nueva
    // sin moverse un pixel — exactamente el caso de la captura: abrir
    // "Color Básico" con el ícono de "3D Básico" ya minimizado ahí
    // cerca terminaba con la cabecera nueva dibujada literalmente
    // encima del ícono viejo, en vez de empujarlo al costado como un
    // acordeón.
    //
    // Esta función es lo que le permite a cada ícono, en cada
    // composición mientras sigue minimizado, preguntar "¿alguna ventana
    // ABIERTA me está tapando ahora mismo?" — leyendo el mismo
    // `openWindowRects` de arriba. [FloatingToolWindow] usa la
    // respuesta para disparar el mismo `settleMinimizedPosition()` que
    // ya usa al minimizarse/arrastrarse (mismo deslizamiento animado,
    // mismo `resolveFreeSlot` para encontrar el hueco libre más
    // cercano) — así el ícono se corre solo, en vivo, apenas la ventana
    // nueva empieza a taparlo, en vez de quedar tapado para siempre.
    fun overlapsAnyOpenWindow(id: String, position: Offset, badgeSizePx: Float): Boolean {
        val badgeRect = Rect(position, Size(badgeSizePx, badgeSizePx))
        return openWindowRects.filterKeys { it != id }.values.any { it.overlaps(badgeRect) }
    }

    // --- A PEDIDO EXPLÍCITO DEL USUARIO: arrastrar el ÍCONO YA
    // MINIMIZADO ("la bola flotante") hacia una zona de "Eliminar" en la
    // parte de abajo del canvas borra ESE efecto por completo — la
    // ventana y su configuración — no solo la oculta como ya hace el
    // botón "×" de la cabecera (ver `onClose`). A propósito, SOLO el
    // ícono minimizado activa esto: arrastrar la ventana ABIERTA desde
    // su cabecera por toda la pantalla (moverla, acomodarla, etc.)
    // nunca muestra ni activa nada de lo de acá abajo — el usuario lo
    // pidió explícito después de la primera pasada de este mismo
    // mecanismo.
    //
    // Estos tres campos son el canal compartido entre CADA badge (que
    // publica si se está arrastrando y dónde, vía [FloatingToolWindow])
    // y la zona de destino visual (que publica su rectángulo real y
    // consulta si algo la está sobrevolando ahora mismo, ver
    // [FloatingWindowDeleteDropZone] más abajo):
    //
    // - `isMinimizedBadgeDragging`: true mientras CUALQUIERA de los
    //   íconos minimizados está siendo arrastrado con el dedo todavía
    //   abajo — es lo que decide si la zona de "Eliminar" se dibuja o
    //   no (aparece recién al empezar a arrastrar, desaparece al
    //   soltar).
    // - `deleteZoneRect`: el rectángulo REAL (posición + tamaño, en px)
    //   de la zona de "Eliminar" mientras está dibujada — publicado por
    //   ella misma con `onGloballyPositioned`, consultado por cada
    //   badge en cada frame de su propio arrastre para saber si el
    //   dedo ya la superpone.
    // - `isDragOverDeleteZone`: true apenas el badge que se está
    //   arrastrando ahora mismo cae encima de `deleteZoneRect` — la
    //   zona lo usa para animar su propio "encendido" (escala + brillo)
    //   y disparar la vibración de confirmación exactamente una vez al
    //   entrar, no en cada frame que pasa por encima.
    var isMinimizedBadgeDragging by mutableStateOf(false)
    var isDragOverDeleteZone by mutableStateOf(false)
    var deleteZoneRect: Rect? by mutableStateOf(null)

    /**
     * Punto libre más cercano a [desired] que no colisiona ni con
     * ningún OTRO ícono ya registrado ni con el rectángulo de ninguna
     * ventana TODAVÍA ABIERTA (se ignora la propia entrada de [id], si
     * existe en cualquiera de los dos registros — una ventana nunca
     * colisiona contra sí misma). Corre el candidato en línea
     * horizontal, de a un "paso" por vez (diámetro del ícono + margen
     * contra otro ícono; borde derecho + margen contra una ventana
     * abierta), hasta no superponerse con nadie — así, tanto si varios
     * íconos terminan queriendo el mismo punto como si ese punto cae
     * arriba de una ventana abierta, el resultado es quedar en fila
     * prolija al lado, nunca apilado ni tapando/tapado.
     */
    fun resolveFreeSlot(
        id: String,
        desired: Offset,
        badgeSizePx: Float,
        spacingPx: Float
    ): Offset {
        val minDistance = badgeSizePx + spacingPx
        val otherBadges = positions.filterKeys { it != id }.values
        val otherOpenWindows = openWindowRects.filterKeys { it != id }.values
        if (otherBadges.isEmpty() && otherOpenWindows.isEmpty()) return desired
        var candidate = desired
        // Techo de intentos = cantidad total de "obstáculos" (íconos +
        // ventanas abiertas) + 1: correrse para esquivar a uno puede
        // acercar el candidato a un tercero que antes estaba lejos, así
        // que hace falta poder reintentar más de una vez — pero nunca
        // más veces de las que hay obstáculos con los que podría
        // chocar, para no colgarse en un caso raro.
        val maxAttempts = otherBadges.size + otherOpenWindows.size + 1
        var attempts = 0
        while (attempts <= maxAttempts) {
            val badgeRect = Rect(candidate, Size(badgeSizePx, badgeSizePx))
            val windowCollision = otherOpenWindows.firstOrNull { it.overlaps(badgeRect) }
            if (windowCollision != null) {
                // Al lado de la ventana abierta, no encima: se corre
                // hasta pasar su borde derecho real.
                candidate = Offset(windowCollision.right + spacingPx, candidate.y)
                attempts += 1
                continue
            }
            val badgeCollision = otherBadges.firstOrNull { (candidate - it).getDistance() < minDistance }
            if (badgeCollision != null) {
                candidate = Offset(badgeCollision.x + minDistance, candidate.y)
                attempts += 1
                continue
            }
            return candidate
        }
        return candidate
    }
}

// --- Auto-tamaño al primer abrir (A PEDIDO EXPLÍCITO DEL USUARIO):
// antes `windowSize` arrancaba siempre en el `initialSize` fijo que le
// pasaba cada llamador (RECOLOR_FLOATING_WINDOW_DEFAULT_SIZE,
// COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
// BASICO_FLOATING_WINDOW_DEFAULT_SIZE, más abajo en este archivo) — un
// número adivinado a mano que no tiene ninguna relación real con cuánto
// contenido tiene CADA ventana (cabecera + título + el último control de
// abajo). Resultado real reportado por el usuario, con capturas: la
// primera vez que se abre la ventana queda "cortada" (el último ajuste,
// p.ej. "Tono", no se ve completo) y la manija de redimensionar (esquina
// inferior derecha) termina dibujada ENCIMA de ese último control, en vez
// de debajo de él.
//
// La solución: la primera vez que la ventana se compone, no se le
// impone ningún alto — se la deja crecer a su alto NATURAL (envolviendo
// cabecera + contenido) hasta un techo razonable (`maxAutoHeight`, ver
// más abajo — el área REAL disponible, para que una ventana con MUCHO
// contenido no tape todo el canvas), se mide ese
// alto real con `onGloballyPositioned` y recién ahí se "congela" como el
// `windowSize` de verdad — sumándole
// [FLOATING_WINDOW_RESIZE_HANDLE_CLEARANCE] de aire extra abajo para que
// la manija de redimensionar quede SIEMPRE por debajo del último
// control, nunca encima. De ahí en adelante la ventana se comporta
// exactamente como antes: tamaño fijo, redimensionable a mano desde la
// esquina — eso sigue siendo 100% opcional para el usuario, tal como
// pidió explícitamente ("ya es opcional").
//
// BUG REAL corregido (reportado con captura: "no calcula bien el
// ancho, en nombres largos se ve cortante"): el ANCHO seguía siendo el
// `initialSize.width` fijo de cada llamador, sin relación real con
// cuánto necesita la cabecera — y la cabecera no es solo el título:
// también carga el grupo de controles "Presets ‹ › ─ - ×" (ver más
// abajo, [FloatingWindowHeaderRowContent]). Con `initialSize.width` en
// 260.dp, ese grupo de controles por sí solo ya consume casi todo ese
// ancho, así que a la etiqueta de la izquierda (`weight(1f)`) le queda
// casi nada de lugar real — el título terminaba truncado con "..."
// aunque el texto en sí no fuera particularmente largo ("Color Básico"
// → "Color Bá..."). Ya NO se deja esto en manos de la elipsis: el ancho
// se mide igual de en serio que el alto — [FloatingWindowHeaderRowContent]
// se compone una segunda vez, "fantasma" (nunca se dibuja, mide 0×0 en
// pantalla — ver el `Layout` de acá abajo), con el título SIN
// `weight()` para que ocupe su ancho NATURAL sin recortar, y ESE ancho
// real de la cabecera completa (ícono + título + controles) es lo que
// entra a competir con `initialSize.width` para decidir el ancho final:
// gana el que sea más grande, hasta `maxAutoWidth` (ver más abajo — el
// área REAL disponible) como techo (mismo criterio que ya usa el alto,
// para que un preset con un nombre absurdamente largo no dispare la
// ventana fuera de la pantalla). La elipsis del título sigue ahí como
// red de seguridad para ese caso límite — no como el mecanismo principal.
// NOTA: la fase "fill hasta abajo" (alto = `maxAutoHeight` siempre) que
// pasó por acá se revirtió — confirmado por el usuario con captura: daba
// un panel gigante casi vacío, peor que el bug original. Se volvió al
// criterio de wrap-content de siempre, así que
// [FLOATING_WINDOW_RESIZE_HANDLE_CLEARANCE] vuelve a hacer falta, tal
// cual estaba.
private val FLOATING_WINDOW_RESIZE_HANDLE_CLEARANCE = 20.dp

/**
 * Contenido de la fila de cabecera — ícono de arrastre, título y grupo
 * de controles "Presets ‹ › ─ - ×" — factorizado en una sola función
 * para que [FloatingToolWindow] lo componga DOS veces con el mismo
 * código exacto: una vez VISIBLE (título con `weight(1f)`, recortado con
 * elipsis si hace falta) y otra "fantasma", nunca dibujada, solo para
 * medir (título SIN `weight()`, con su ancho natural completo) — ver el
 * comentario grande sobre `autoWidth` en [FloatingToolWindow] para el
 * porqué. Mantener esto en un solo lugar evita que las dos copias se
 * desincronicen si el header cambia más adelante (un ícono nuevo, un
 * botón de menos, etc.): hay un solo lugar para tocar, no dos.
 */
@Composable
private fun RowScope.FloatingWindowHeaderRowContent(
    title: String,
    titleIcon: @Composable (Color) -> Unit,
    titleWeighted: Boolean,
    presets: List<String>,
    selectedPresetIndex: Int?,
    presetsMenuExpanded: Boolean,
    onPresetsTogglePress: () -> Unit,
    onPresetsDismiss: () -> Unit,
    onPresetSelect: (Int) -> Unit,
    onPresetPrev: () -> Unit,
    onPresetNext: () -> Unit,
    onMinimize: () -> Unit,
    onClose: (() -> Unit)?,
    // A PEDIDO DEL USUARIO: botón de menú de la ventana ("los tres
    // puntos" — acá el glifo de tres cuadrados, ver [ic_menu_option]),
    // pegado del lado IZQUIERDO de [titleIcon] — el primer elemento de
    // toda la cabecera. Abre [FloatingWindowOptionsMenuButton] (ver más
    // abajo): una barra vertical angosta, propia de ESTA ventana, todavía vacía
    // a pedido explícito ("por el momento solo ventana vacía") — el
    // lugar donde van a vivir las opciones de cada módulo más adelante.
    menuExpanded: Boolean,
    onMenuTogglePress: () -> Unit,
    onMenuDismiss: () -> Unit
) {
    FloatingWindowOptionsMenuButton(
        expanded = menuExpanded,
        onTogglePress = onMenuTogglePress,
        onDismiss = onMenuDismiss
    )
    Spacer(modifier = Modifier.width(6.dp))
    titleIcon(Color.White.copy(alpha = 0.7f))
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        title,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (titleWeighted) Modifier.weight(1f) else Modifier.wrapContentWidth()
    )

    Spacer(modifier = Modifier.width(6.dp))

    FloatingWindowPresetsControl(
        presets = presets,
        selectedIndex = selectedPresetIndex,
        expanded = presetsMenuExpanded,
        onTogglePress = onPresetsTogglePress,
        onDismiss = onPresetsDismiss,
        onSelect = onPresetSelect
    )

    FloatingWindowHeaderIconButton(
        iconRes = R.drawable.ic_chevron_left,
        contentDescription = "Preset anterior",
        enabled = presets.isNotEmpty(),
        onClick = onPresetPrev
    )
    FloatingWindowHeaderIconButton(
        iconRes = R.drawable.ic_chevron_right,
        contentDescription = "Preset siguiente",
        enabled = presets.isNotEmpty(),
        onClick = onPresetNext
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(16.dp)
            .background(Color.White.copy(alpha = 0.14f))
    )

    FloatingWindowHeaderIconButton(
        iconRes = R.drawable.ic_remove,
        contentDescription = "Minimizar",
        onClick = onMinimize
    )

    if (onClose != null) {
        FloatingWindowHeaderIconButton(
            iconRes = R.drawable.ic_close,
            contentDescription = "Cerrar",
            onClick = onClose
        )
    }
}

@Composable
private fun FloatingToolWindow(
    title: String,
    // Ícono de identidad de ESTA ventana, mostrado en la esquina superior
    // izquierda de la cabecera — el MISMO glifo que ya usa esta misma
    // herramienta en su menú de selección (p.ej. [ColorMenuBasicoIcon]
    // para "Color Básico", [ColorMenuRecolorIcon] para "Recolor",
    // [Menu3DBasicCubeIcon] para "3D Básico" — ver [EditImageColorMenu]/
    // [EditImage3DMenu]). A PEDIDO DEL USUARIO — BUG REAL corregido: acá
    // había un ícono de "mover en los dos ejes" (`ic_move_both_axes`)
    // FIJO para las tres ventanas por igual, sin relación ninguna con
    // cuál era cuál — el usuario lo notó comparando esta cabecera contra
    // el menú de selección, donde cada opción SÍ tiene su propio glifo
    // distinto. Sin valor por defecto a propósito: cada llamador
    // (Recolor / Color Básico / 3D Básico) tiene que pasar el suyo de
    // forma explícita — así, si mañana se agrega una cuarta ventana y se
    // olvida este parámetro, no compila en silencio con el ícono
    // genérico de otra por error.
    titleIcon: @Composable (Color) -> Unit,
    initialOffset: Offset,
    initialSize: DpSize,
    minSize: DpSize = DpSize(220.dp, 260.dp),
    onClose: (() -> Unit)? = null,
    // A PEDIDO EXPLÍCITO DEL USUARIO: distinto de `onClose` (que solo
    // OCULTA la ventana, dejando el efecto ya aplicado tal cual quedó —
    // ver el comentario de `onClose` en cada llamador). Esto BORRA el
    // efecto entero — vuelve la capa a como estaba antes de tocar esta
    // herramienta y recién DESPUÉS cierra la ventana. Se dispara desde
    // un solo lugar: soltar el ícono ya minimizado ("la bola flotante")
    // encima de la zona de "Eliminar" que aparece abajo del canvas
    // mientras ese ícono se arrastra (ver [FloatingWindowDeleteDropZone]
    // y el comentario grande sobre `isMinimizedBadgeDragging` en
    // [FloatingWindowMinimizedRegistry]) — nunca desde arrastrar la
    // ventana ABIERTA, a pedido explícito del usuario. `null` (default)
    // en cualquier llamador que todavía no tenga una forma real de
    // "deshacer" su efecto — el gesto de arrastrar-y-soltar sigue
    // funcionando igual, solo que sin nada que restablecer.
    onDeleteEffect: (() -> Unit)? = null,
    // Lista de presets de ESTE módulo — vacía por ahora en los tres
    // llamadores actuales (Recolor / Color Básico / 3D Básico), a
    // propósito: todavía no existe un modelo de datos de presets real
    // por módulo. El mecanismo de navegación y el panel desplegable ya
    // quedan listos acá, así que conectar presets reales más adelante es
    // solo pasar esta lista — sin tocar la cabecera de nuevo.
    presets: List<String> = emptyList(),
    // --- BUG REAL corregido (reportado con captura): el popup de
    // "Presets" (ver [FloatingWindowPresetsControl]) es un `Popup` real
    // de Android — crea su PROPIA capa de ventana del sistema, aparte de
    // la jerarquía normal de Compose, así que SIEMPRE se dibuja por
    // encima de todo lo demás en la Activity, sin importar ningún
    // `zIndex` de Compose. Eso en sí mismo es correcto (un menú
    // desplegable tiene que verse completo, no tapado) — el problema
    // real era otro: acá nunca existió ningún concepto de "esta ventana
    // está delante/detrás de las otras dos" — Recolor, Color Básico y
    // 3D Básico compartían el mismo `zIndex(20f)` fijo (ver el lugar
    // donde se instancian, en el Box de más arriba), así que el orden
    // entre ellas tres dependía solo del orden en que están ESCRITAS en
    // el código, no de cuál tocó el usuario último. Resultado: tocar
    // "Presets" en una ventana que el usuario considera "de atrás" hacía
    // que su popup tapara a una ventana "de adelante" — se veía roto.
    //
    // La corrección real es traer la ventana al frente de las otras dos
    // (subir su `zIndex` de Compose por encima de las demás) apenas el
    // usuario la toca — así, para cuando el popup de "Presets" se abre,
    // esa ventana YA es, sin ambigüedad, la de adelante, y que su propio
    // popup tape a las otras dos deja de ser un bug: es lo esperado,
    // igual que cualquier host de ventanas flotantes real (FL Studio,
    // Photoshop). `onInteracted` es el aviso hacia el llamador (ver el
    // Box exterior donde se instancian las tres) de "tocaron esta
    // ventana, subila". Se dispara con el PRIMER toque de cada gesto,
    // en TODA la ventana (no solo en la cabecera) — ver el
    // `pointerInput` de acá abajo — sin consumir el evento, así que
    // sliders/botones/arrastre siguen funcionando exactamente igual;
    // esto solo OBSERVA que hubo un toque, nunca lo intercepta.
    onInteracted: () -> Unit = {},
    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido: ver el
    // comentario grande sobre [FloatingWindowMinimizedRegistry], justo
    // arriba de [FLOATING_WINDOW_TOP_MAGNET_MARGIN]. Un solo registro,
    // creado UNA vez donde se instancian las tres ventanas (Recolor /
    // Color Básico / 3D Básico — ver ese `remember` en el Box exterior)
    // y compartido entre ellas acá, es lo que le permite a cada una
    // saber dónde están los OTROS íconos minimizados para no
    // superponerse. Sin valor por defecto a propósito, mismo criterio
    // que [titleIcon]: si mañana se agrega una cuarta ventana y se
    // olvida pasar el registro compartido, no compila en silencio con
    // una que solo se ve a sí misma.
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    // Tamaño REAL (en px) del área donde viven las tres ventanas
    // flotantes — medido con `onSizeChanged` en el Box exterior donde se
    // instancian (ver `floatingWindowAreaSizePx`, junto a ese Box) y
    // reenviado sin cambios por cada envoltorio (Recolor / Color Básico /
    // 3D Básico). Es el límite real contra el que se recortan los
    // arrastres hacia los costados y hacia abajo, más abajo en esta
    // función — ver `clampToSideLimits`/`clampToBottomLimit`.
    containerSizePx: IntSize,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido dos veces
    // seguidas (reportado la primera vez: "ahora ni eso ni nada, no
    // siento nada"; y de nuevo después de la primera corrección: "esa
    // vibración... no funciona, no siento nada"). La causa de fondo, en
    // las dos vueltas, es la MISMA: pedir el pulso más débil que la API
    // de Android permite.
    //
    // Primera vuelta: `LocalHapticFeedback` + `HapticFeedbackType.LongPress`
    // — pensada para acuses de clic MUY sutiles de la propia UI de
    // Compose (selección de texto, por ejemplo), casi imperceptible en
    // la mayoría de los equipos.
    //
    // Segunda vuelta: ya se había cambiado a `Vibrator`/`VibrationEffect`
    // de verdad, pero con `DEFAULT_AMPLITUDE` (le deja al sistema
    // decidir qué tan fuerte vibrar — en muchos equipos, sobre todo con
    // el volumen de "vibración táctil" bajo en Ajustes, eso termina
    // siendo casi nada) y una duración de apenas 35ms — más corta que
    // el tiempo de arranque de muchos motores lineales modernos (~20-30ms),
    // así que en la práctica el motor ni llegaba a moverse antes de que
    // Android le mandara "parar".
    //
    // La corrección: `AMPLITUDE_MAX = amplitud MÁXIMA posible pedida
    // explícita (no "la que decida el sistema"), duraciones más largas
    // (50ms para el pulso de "entraste a la zona", un patrón de DOS
    // golpes de 40ms para el de "confirmado, se borró" — inconfundible
    // incluso si el primero no se llegó a sentir bien) — ver
    // `triggerDeleteZoneHoverPulse`/`triggerDeleteZoneConfirmPulse` más
    // abajo. `minSdk` de este proyecto es 26 (ver app/build.gradle.kts)
    // — exactamente la versión donde se agregó `VibrationEffect` — así
    // que no hace falta ningún camino alternativo para versiones más
    // viejas de Android.
    val hapticContext = LocalContext.current
    // A PEDIDO EXPLÍCITO DEL USUARIO — TERCERA vuelta sobre este mismo
    // bug ("la vibración no funciona"): las dos vueltas anteriores (ver
    // el comentario grande de arriba) atacaron la AMPLITUD/DURACIÓN del
    // pulso, pero había un problema de fondo que ninguna de las dos
    // podía haber revelado nunca: `getVibrator()`/`vibrate()` estaban
    // envueltos en `catch (_: Exception) { }` — CUALQUIER fallo real
    // (equipo sin motor de vibración, `Vibrator` nulo, permiso
    // bloqueado por batería/Do-Not-Disturb en algún fabricante, etc.)
    // se tragaba en silencio, sin dejar ningún rastro en ningún lado.
    // Es imposible saber si "no funciona" es un bug de este código o
    // una vibración real que el equipo de prueba no puede sentir si el
    // fallo nunca queda escrito en ninguna parte.
    //
    // La corrección: seguir sin romper el flujo si algo falla (una
    // vibración fallida no debe tirar abajo un arrastre en curso), pero
    // ahora CADA fallo queda anotado en [AppLogger] — visible en la
    // pantalla de "Registro de errores" de la propia app, sin depender
    // de un cable USB ni de Logcat. También se registra, como aviso
    // (no error), el caso de equipo SIN motor de vibración en absoluto
    // (`hasVibrator() == false`) — la causa más común y más difícil de
    // sospechar de "no siento nada" en emuladores y en algunas tablets.
    fun getVibrator(): android.os.Vibrator? = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val manager = hapticContext.getSystemService(android.os.VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            hapticContext.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    } catch (e: Exception) {
        AppLogger.e("DeleteZoneHaptics", "No se pudo obtener el Vibrator del sistema", e)
        null
    }
    // Pulso corto, al ENTRAR a la zona de "Eliminar" con el ícono
    // arrastrado — un "clic" táctil de aviso, no de confirmación.
    fun triggerDeleteZoneHoverPulse() {
        try {
            val vibrator = getVibrator()
            if (vibrator == null || !vibrator.hasVibrator()) {
                AppLogger.w("DeleteZoneHaptics", "Pulso de entrada omitido: este equipo no reporta motor de vibración")
                return
            }
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(50L, 255))
        } catch (e: Exception) {
            AppLogger.e("DeleteZoneHaptics", "Fallo al disparar el pulso de entrada a la zona de eliminar", e)
        }
    }
    // Pulso de DOS golpes, al SOLTAR y confirmar que el efecto se borró
    // de verdad — a propósito más largo/distinto al de arriba, para que
    // se sienta como un cierre, no como un aviso más.
    fun triggerDeleteZoneConfirmPulse() {
        try {
            val vibrator = getVibrator()
            if (vibrator == null || !vibrator.hasVibrator()) {
                AppLogger.w("DeleteZoneHaptics", "Pulso de confirmación omitido: este equipo no reporta motor de vibración")
                return
            }
            vibrator.vibrate(
                android.os.VibrationEffect.createWaveform(
                    longArrayOf(0L, 40L, 30L, 60L),
                    intArrayOf(0, 255, 0, 255),
                    -1
                )
            )
        } catch (e: Exception) {
            AppLogger.e("DeleteZoneHaptics", "Fallo al disparar el pulso de confirmación de borrado", e)
        }
    }
    var offsetPx by remember { mutableStateOf(initialOffset) }
    // BUG REAL corregido (a pedido explícito del usuario, con captura):
    // ventana e ícono minimizado se "perdían" arrastrando hacia los
    // COSTADOS o hacia ABAJO — ningún lado tenía techo/piso ahí, así que
    // `offsetPx` podía crecer sin límite hasta quedar fuera del área
    // visible. A PEDIDO EXPLÍCITO: esto NO toca el límite de ARRIBA —
    // ese ya tiene su propia lógica (`coerceAtLeast(0f)` + el imán de
    // [FLOATING_WINDOW_TOP_MAGNET_MARGIN], más abajo) y se deja
    // exactamente como está. Estas dos funciones solo agregan techo por
    // la derecha (`clampToSideLimits`) y techo por abajo
    // (`clampToBottomLimit`), contra el tamaño REAL del área medida en
    // `containerSizePx` (ver su declaración junto a donde se instancian
    // las tres ventanas) — si todavía no se midió (0×0, primer frame),
    // no recortan nada para no forzar la ventana a (0,0) por error.
    fun clampToSideLimits(x: Float, itemWidthPx: Float): Float {
        if (containerSizePx.width <= 0) return x
        val maxX = (containerSizePx.width - itemWidthPx).coerceAtLeast(0f)
        return x.coerceIn(0f, maxX)
    }
    fun clampToBottomLimit(y: Float, itemHeightPx: Float): Float {
        if (containerSizePx.height <= 0) return y
        val maxY = (containerSizePx.height - itemHeightPx).coerceAtLeast(0f)
        return y.coerceAtMost(maxY)
    }
    // Asienta esta ventana en un punto que no colisione con ningún otro
    // ícono ya minimizado (ver [FloatingWindowMinimizedRegistry]) y deja
    // esa posición final registrada para que, a su vez, las PRÓXIMAS
    // ventanas que se minimicen esquiven a esta. Un solo punto de
    // entrada para los tres caminos que llevan a "quedar minimizada":
    // el imán del pie del header, el botón "-" de la cabecera, y
    // arrastrar el ícono ya minimizado encima de otro.
    //
    // BUG VISUAL corregido (reportado con captura: "se ve feo, como un
    // bug, aunque no lo sea" — el ícono llegaba pegado al lado de una
    // ventana TODAVÍA ABIERTA, ver [FloatingWindowMinimizedRegistry]
    // más arriba, y en el instante exacto de asentarse "TELETRANSPORTABA"
    // de golpe al hueco libre, sin ningún paso intermedio: un salto seco
    // de un punto a otro en el mismo frame). La corrección de fondo
    // (esquivar la ventana abierta) ya era la correcta — lo que se veía
    // mal era la FALTA de transición: un salto instantáneo siempre lee
    // como un glitch, sin importar que el destino sea el correcto. Acá
    // NO se cambia el destino calculado por [resolveFreeSlot] — se
    // anima el camino hacia él, igual que ya hace el "lanzamiento" hacia
    // arriba (ver [FLOATING_WINDOW_FLING_MINIMIZE_ANIMATION_MS], unas
    // líneas más arriba) para que, en vez de "aparecer" al lado de la
    // ventana abierta, se lo vea DESLIZARSE hasta ahí — una corrección
    // que se lee como intencional, no como un error.
    val settleMinimizedPosition: () -> Unit = {
        val badgeSizePx = with(density) { FLOATING_WINDOW_MINIMIZED_BADGE_SIZE.toPx() }
        val spacingPx = with(density) { FLOATING_WINDOW_MINIMIZED_BADGE_SPACING.toPx() }
        val resolved = minimizedRegistry.resolveFreeSlot(title, offsetPx, badgeSizePx, spacingPx)
        // BUG REAL corregido: al esquivar a otro ícono, [resolveFreeSlot]
        // corre el candidato en línea hacia la DERECHA (ver su código) y
        // puede terminar más allá del borde derecho real de la pantalla
        // si ya hay varios íconos en fila — mismo síntoma de "se pierde"
        // que el arrastre a mano. Solo se recorta el lado (X); el eje Y
        // no lo toca [resolveFreeSlot] nunca, así que no hace falta
        // tocarlo acá tampoco.
        val clamped = resolved.copy(x = clampToSideLimits(resolved.x, badgeSizePx))
        // Se registra el destino FINAL desde ya — antes de que termine
        // de deslizarse hasta ahí — para que cualquier OTRA ventana que se
        // minimice mientras esta animación todavía está en vuelo ya la
        // esquive a ELLA en su punto de llegada, no en el punto de
        // partida (que un instante después iba a quedar libre igual).
        minimizedRegistry.register(title, clamped)
        if (clamped == offsetPx) {
            // Nada que esquivar: no hace falta animar un desplazamiento
            // de cero píxeles.
            offsetPx = clamped
        } else {
            val startOffset = offsetPx
            coroutineScope.launch {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FLOATING_WINDOW_MINIMIZED_SETTLE_ANIMATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) { fraction, _ ->
                    offsetPx = Offset(
                        lerp(startOffset.x, clamped.x, fraction),
                        lerp(startOffset.y, clamped.y, fraction)
                    )
                }
                offsetPx = clamped
            }
        }
    }
    // Red de seguridad: si la ventana se CIERRA del todo (× o
    // `onClose`) mientras estaba minimizada, esta instancia de
    // [FloatingToolWindow] se descompone entera y ningún `onClick`/
    // `onMinimize` de acá arriba llega a correr — sin esto, su entrada
    // quedaría viva para siempre en [minimizedRegistry], "reservando" un
    // hueco que ya no tiene ícono real ahí.
    DisposableEffect(Unit) {
        onDispose {
            minimizedRegistry.unregister(title)
            // Misma red de seguridad que ya existía para el ícono
            // minimizado (línea de arriba), ahora también para el
            // rectángulo de "ventana abierta" (ver [SideEffect] de más
            // arriba y el comentario grande sobre
            // [FloatingWindowMinimizedRegistry]): si la ventana se
            // CIERRA del todo mientras seguía abierta (sin pasar antes
            // por minimizar), ningún `SideEffect` vuelve a correr para
            // limpiarlo — sin esto, quedaría "reservando" ese lugar
            // para siempre contra ventanas que se minimicen después.
            minimizedRegistry.unregisterOpenWindow(title)
        }
    }
    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido: la primera
    // versión de este auto-tamaño medía el alto UNA sola vez, en la
    // primerísima composición, y lo congelaba ahí para siempre. Eso se
    // rompía con contenido que cambia de alto después de esa primera
    // composición — por ejemplo cualquier [LabeledSlider] al abrir su campo de edición
    // manual, que agrega un `OutlinedTextField` más alto que la fila
    // normal. En cualquiera de esos casos, la ventana se congelaba en el
    // alto del contenido CHICO, y cuando el contenido real (más alto)
    // aparecía un instante después ya no había alto reservado — quedaba
    // cortado, exactamente el mismo bug que se pidió corregir.
    //
    // Ahora `windowSize` no se congela por una sola medición: sigue
    // recalculándose contra el alto NATURAL real del contenido en cada
    // composición mientras `userResized` sea `false`. Recién cuando el
    // usuario arrastra la manija de la esquina por primera vez
    // (`userResized = true`, ver la manija más abajo) el tamaño pasa a
    // ser fijo de verdad y queda 100% bajo su control, tal como se pidió
    // ("ya es opcional").
    var windowSize by remember { mutableStateOf<DpSize?>(null) }
    var userResized by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    // --- BUG REAL corregido (reportado con captura: "la ventana debería
    // llegar hasta abajo... no toca, no llega abajo, no aprovecha el
    // tamaño de la pantalla"). El techo del auto-tamaño (`maxAutoHeight`/
    // `maxAutoWidth`) se calculaba contra `configuration.screenHeightDp`/
    // `screenWidthDp` — el tamaño FÍSICO TOTAL del dispositivo, sin
    // descontar el header ni el resto de la interfaz — en vez de contra
    // `containerSizePx`, que es el área REAL donde viven estas tres
    // ventanas (ya medida con `onSizeChanged` en el Box exterior, ver su
    // declaración junto a `floatingWindowAreaSizePx`, y pasada acá mismo
    // como parámetro). Esa base de cálculo estaba mal: es la causa de
    // que la ventana nunca pudiera aprovechar el espacio real disponible
    // ni llegar a tocar el borde inferior verdadero del área de trabajo,
    // aunque hubiera lugar de sobra para eso — se quedaba corta contra un
    // porcentaje de una medida que no es la que importa acá.
    //
    // La corrección usa `containerSizePx` (ya en píxeles reales) como
    // base — con el mismo fallback a la medida de pantalla completa
    // SOLO para el primerísimo frame, antes de que `onSizeChanged` mida
    // nada todavía (`containerSizePx` en 0×0) — y sube el techo a
    // prácticamente el área completa (no ya un 72%/92% arbitrario): la
    // ventana puede ahora crecer hasta tocar el borde real del área de
    // trabajo cuando el contenido lo necesita, sin quedar nunca más
    // corta por una cuenta hecha contra el número equivocado.
    val containerHeightDp = with(density) { containerSizePx.height.toDp() }
    val containerWidthDp = with(density) { containerSizePx.width.toDp() }
    // SEGUNDA CORRECCIÓN REAL sobre el mismo bug (el intento anterior, en
    // el comentario de arriba, cambió la BASE del cálculo pero seguía sin
    // restar la posición vertical de la propia ventana): `containerHeightDp`
    // es el alto TOTAL del área de trabajo medida desde y=0, pero la
    // ventana no arranca en y=0 — arranca en `offsetPx.y` (su posición
    // actual, ya sea la inicial escalonada o donde el usuario la arrastró).
    // Usar el alto total como techo, sin descontar ese offset, es
    // exactamente la cuenta mal hecha que el usuario sigue viendo: le
    // permite a la ventana "pedir" hasta el 100% del área aunque ya esté
    // parada 300dp más abajo del techo real, así que un panel que sí
    // necesita ese alto se queda corto contra el borde inferior real en
    // esa misma medida (300dp de hueco) en vez de tocarlo.
    //
    // La cuenta correcta es el espacio que queda DEBAJO de donde la
    // ventana ya está: `containerHeightDp - offsetY`. Se recalcula en
    // cada composición (no es un `remember`), así que sigue la posición
    // en vivo mientras se arrastra la ventana verticalmente.
    val offsetYDp = with(density) { offsetPx.y.toDp() }
    val maxAutoHeight = (if (containerSizePx.height > 0) (containerHeightDp - offsetYDp) else (configuration.screenHeightDp.dp - offsetYDp))
        .coerceAtLeast(minSize.height)
    val maxAutoWidth = (if (containerSizePx.width > 0) containerWidthDp else configuration.screenWidthDp.dp)
        .coerceAtLeast(minSize.width)
    // Ancho NATURAL real de la cabecera (ícono + título SIN truncar +
    // grupo de controles), medido por la composición "fantasma" de
    // [FloatingWindowHeaderRowContent] de acá abajo — ver el comentario
    // grande sobre el bug de ancho, más arriba. Arranca en 0 (nada
    // medido todavía, primerísimo frame); el `coerceAtLeast` de abajo,
    // al calcular `autoWidth`, ya cubre ese caso sin dejar la ventana en
    // 0.dp por un instante.
    var naturalHeaderWidthPx by remember { mutableStateOf(0) }
    val autoWidth = maxOf(initialSize.width, with(density) { naturalHeaderWidthPx.toDp() })
        .coerceAtMost(maxAutoWidth)
    // Minimizado: colapsa TODA la ventana (cabecera + contenido) a solo
    // su ícono — ver [FloatingWindowMinimizedBadge] más abajo. Vive en
    // el mismo Box con el mismo `.offset`, así que el ícono aparece
    // exactamente donde estaba la ventana, y restaurarla la deja igual
    // de tamaño/posición que antes de minimizarla (ni `windowSize` ni
    // `offsetPx` se tocan al minimizar/restaurar).
    var isMinimized by remember { mutableStateOf(false) }

    // A PEDIDO EXPLÍCITO DEL USUARIO — ver el comentario grande sobre
    // esta variable en el Box raíz de más abajo (donde se usa para
    // subir el `zIndex`): a diferencia de
    // `minimizedRegistry.isMinimizedBadgeDragging` (compartida entre
    // las tres ventanas, dice "se está arrastrando ALGUNO"), esta es
    // LOCAL de esta ventana — dice "soy YO el que se está arrastrando
    // ahora mismo", que es lo único que hace falta saber para decidir
    // si ESTE ícono en particular necesita pasar por encima de todo lo
    // demás.
    var isDraggingThisBadge by remember { mutableStateOf(false) }

    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido (reportado con
    // captura): "cuando abro cualquier icono minimizado al lado del
    // otro se superpone encima del otro, debería empujarlo a un lado
    // como un acordeón". Causa real: [resolveFreeSlot]/
    // `settleMinimizedPosition` (ver el comentario grande sobre
    // [FloatingWindowMinimizedRegistry]) solo corren en el momento en
    // que UN ícono se minimiza o se arrastra — nunca vuelven a correr
    // para un ícono que ya estaba quieto y minimizado cuando es OTRA
    // ventana la que se abre (o crece) encima de él. Se leía el mapa de
    // "ventanas abiertas" para que un ícono NUEVO las esquive al
    // aterrizar, pero un ícono VIEJO ya asentado nunca volvía a
    // preguntar si seguía teniendo el lugar libre.
    //
    // La corrección: mientras este ícono sigue minimizado, en cada
    // composición se pregunta (vía [FloatingWindowMinimizedRegistry.overlapsAnyOpenWindow],
    // definido junto al registro) si alguna ventana ABIERTA lo está
    // tapando ahora mismo — esa lectura de estado compartido dispara
    // recomposición sola cada vez que otra ventana publica su
    // rectángulo en vivo (ver el `SideEffect` más abajo, que registra el
    // rectángulo de ESTA ventana para las demás). Apenas la respuesta
    // pasa a "sí", el `LaunchedEffect` de acá abajo llama al mismo
    // `settleMinimizedPosition()` que ya usa el resto de los caminos
    // hacia "quedar minimizado" — mismo deslizamiento animado, mismo
    // hueco libre calculado — así que el ícono se corre solo al
    // costado, como un acordeón, apenas la ventana nueva empieza a
    // taparlo, en vez de quedar tapado. Tiene que ir DESPUÉS de
    // `isMinimized` (recién declarada arriba) — antes vivía más arriba
    // en la función y rompía la compilación (`Unresolved reference
    // 'isMinimized'`) por leerla antes de que existiera.
    val minimizedBadgeSizePx = with(density) { FLOATING_WINDOW_MINIMIZED_BADGE_SIZE.toPx() }
    val isCoveredByOpenWindow = isMinimized &&
        minimizedRegistry.overlapsAnyOpenWindow(title, offsetPx, minimizedBadgeSizePx)
    LaunchedEffect(isCoveredByOpenWindow) {
        if (isCoveredByOpenWindow) {
            settleMinimizedPosition()
        }
    }

    var selectedPresetIndex by remember { mutableStateOf<Int?>(null) }
    var presetsMenuExpanded by remember { mutableStateOf(false) }
    var optionsMenuExpanded by remember { mutableStateOf(false) }

    // --- BUG REAL corregido (reportado con captura: la ventana de
    // "Color Básico" abriéndose hacia AFUERA de la pantalla, con el
    // panel de sliders cortado por el borde). `clampToSideLimits`/
    // `clampToBottomLimit` (definidos más arriba) solo se ejecutaban
    // dentro de los dos `detectDragGestures` de esta función — es decir,
    // únicamente cuando el USUARIO arrastraba la ventana o el ícono
    // minimizado a mano. El tamaño de la ventana, en cambio, también
    // cambia SOLO, sin ningún arrastre de por medio: `windowSize` se
    // recalcula en cada composición mientras `!userResized` (ver el
    // comentario grande sobre `userResized`, más arriba) para seguir el
    // alto NATURAL real del contenido — por ejemplo, el salto de un
    // estado más chico a los sliders reales de Nitidez/Saturación/
    // Brillo/Contraste/Tono al abrir/expandir el panel. Ese crecimiento
    // nunca volvía a comprobar si la ventana seguía completa dentro del
    // área visible: una ventana ya posicionada cerca del borde derecho
    // o inferior terminaba creciendo hacia AFUERA de la pantalla en vez
    // de hacia adentro, exactamente lo reportado ("se abre pero para
    // afuera y no para dentro... se ve metido eso").
    //
    // La corrección: cada vez que el tamaño REAL de la ventana
    // (`windowSize`, ya sea porque el auto-tamaño lo recalculó o porque
    // el usuario la redimensionó a mano) o el tamaño del área contenedora
    // (`containerSizePx`) cambian, se vuelve a correr el MISMO recorte
    // que ya usa el arrastre — si la ventana ya no entra donde estaba,
    // se corre lo mínimo necesario hacia la izquierda y/o hacia arriba
    // para quedar completa dentro de la pantalla, en vez de dejarla
    // creciendo hacia afuera sin límite. No toca `offsetPx` para nada si
    // la ventana ya entra bien donde está — mismo criterio de "solo lo
    // mínimo necesario" que ya usa `clampToSideLimits`/
    // `clampToBottomLimit` en el arrastre.
    val currentWindowWidthPx = with(density) { (windowSize ?: initialSize).width.toPx() }
    val currentWindowHeightPx = with(density) { (windowSize ?: initialSize).height.toPx() }
    LaunchedEffect(currentWindowWidthPx, currentWindowHeightPx, containerSizePx) {
        val clampedX = clampToSideLimits(offsetPx.x, currentWindowWidthPx)
        val clampedY = clampToBottomLimit(offsetPx.y, currentWindowHeightPx)
        if (clampedX != offsetPx.x || clampedY != offsetPx.y) {
            offsetPx = offsetPx.copy(x = clampedX, y = clampedY)
        }
    }
    // BUG REAL corregido (ver el comentario grande sobre
    // [FloatingWindowMinimizedRegistry], junto a su declaración): otra
    // ventana a punto de minimizarse necesita saber, en el momento
    // exacto de asentarse, si ESTA ventana sigue abierta y dónde —
    // no solo dónde están los OTROS íconos ya minimizados. `SideEffect`
    // (no `LaunchedEffect`: esto es una publicación síncrona de "así
    // estoy AHORA", no una corrutina) corre después de cada composición
    // exitosa, así que el rectángulo publicado sigue a `offsetPx`/
    // `windowSize` en vivo mientras se arrastra o cambia de tamaño, y
    // se retira del registro apenas esta ventana se minimiza (o se
    // cierra, vía el `DisposableEffect` de más arriba) — dejando de
    // "reservar" ese lugar para las que se minimicen después.
    SideEffect {
        if (isMinimized) {
            minimizedRegistry.unregisterOpenWindow(title)
        } else {
            minimizedRegistry.registerOpenWindow(
                title,
                Rect(offsetPx, Size(currentWindowWidthPx, currentWindowHeightPx))
            )
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetPx.x.roundToInt(), offsetPx.y.roundToInt()) }
            // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido
            // (reportado con captura): "la bola flotante al ubicarse
            // encima del ícono eliminar va por detrás, debería ir por
            // encima". Causa real: [FloatingWindowDeleteDropZone] vivía
            // con un `zIndex` fijo altísimo (1000f, ver su
            // instanciación) para no quedar tapada por ninguna ventana —
            // pero eso incluía, sin querer, al PROPIO ícono que se
            // arrastra HACIA ella: como las tres ventanas comparten un
            // `zIndex` base de "20f + orden de toque" (ver el Box donde
            // se instancian las tres), cualquiera de ellas quedaba, sí o
            // sí, por DEBAJO de esos 1000f apenas se superponía
            // visualmente con la zona — el ícono desaparecía "detrás"
            // del círculo de eliminar justo en el momento en que más
            // importa verlo (a punto de soltar).
            //
            // La corrección: mientras ESTE ícono en particular se está
            // arrastrando (`isDraggingThisBadge`, ver el `onDragStart`/
            // `onDragEnd` del badge más abajo — variable LOCAL de esta
            // ventana, no la del registro compartido, que es "está
            // arrastrándose ALGUNO" sin decir cuál), su `zIndex` sube
            // muy por encima de cualquier otra cosa en pantalla
            // (incluida la zona de eliminar, ya bajada a un `zIndex`
            // bajo — ver su instanciación). `Modifier` (vacío) cuando NO
            // se está arrastrando, a propósito: así se respeta el
            // `zIndex` que ya trae `modifier` desde afuera (el orden
            // entre las tres ventanas) en vez de pisarlo con un valor
            // fijo.
            .then(if (isDraggingThisBadge) Modifier.zIndex(5000f) else Modifier)
    ) {
        if (isMinimized) {
            // A PEDIDO EXPLÍCITO DEL USUARIO: minimizado, el ícono tiene
            // que poder arrastrarse por toda la pantalla exactamente
            // igual que la ventana abierta — antes quedaba fijo porque
            // [FloatingWindowMinimizedBadge] no tenía ningún
            // `pointerInput` propio. `onDrag` acá reusa el MISMO
            // `offsetPx` que ya mueve la ventana completa (ver el
            // `.offset` del Box padre, un poco más arriba) — así que
            // restaurarla más tarde la deja EXACTAMENTE donde el usuario
            // soltó el ícono, sin ningún salto de posición.
            FloatingWindowMinimizedBadge(
                title = title,
                titleIcon = titleIcon,
                onClick = {
                    // BUG REAL corregido (reportado: arrastrar el ícono
                    // minimizado hacia un costado y tocar para expandir
                    // abre la ventana "hacia afuera", casi desaparecida,
                    // como si saltara hacia la derecha). Causa real: el
                    // ícono minimizado se arrastra y se recorta contra su
                    // propio tamaño chico (`FLOATING_WINDOW_MINIMIZED_BADGE_SIZE`,
                    // ver `onDrag` acá abajo) — una posición perfectamente
                    // válida para un círculo de ~48dp puede estar a solo
                    // unos pocos dp del borde derecho/inferior real. Al
                    // restaurar, la ventana vuelve a su tamaño COMPLETO
                    // (~260dp o más de ancho) en ESA MISMA posición sin
                    // que nada la revise de nuevo — el `LaunchedEffect`
                    // que sí re-clampa tamaño-vs-posición más abajo solo
                    // se dispara cuando cambian `windowSize`/
                    // `containerSizePx`, y ninguno de los dos cambia al
                    // pasar de minimizado a restaurado (el booleano
                    // `isMinimized` no es una de sus claves) — por eso
                    // nunca corría acá. La corrección: re-clampar acá
                    // mismo, ANTES de restaurar, usando el tamaño REAL con
                    // el que la ventana está por aparecer (el de la fase
                    // "auto" — `autoWidth`/`maxAutoHeight` — o el tamaño
                    // fijo que el usuario ya le haya dado a mano).
                    // `windowSize` ya refleja el tamaño real envuelto al
                    // contenido (medido por `onGloballyPositioned`, ver
                    // más abajo) tanto en fase "auto" como "manual" — se
                    // usa tal cual acá, con `initialSize` como único
                    // fallback para el caso límite de minimizar la
                    // ventana antes de su primer layout medido.
                    val restoredSize = windowSize ?: initialSize
                    val restoredWidthPx = with(density) { restoredSize.width.toPx() }
                    val restoredHeightPx = with(density) { restoredSize.height.toPx() }
                    offsetPx = offsetPx.copy(
                        x = clampToSideLimits(offsetPx.x, restoredWidthPx),
                        y = clampToBottomLimit(offsetPx.y, restoredHeightPx)
                    )
                    isMinimized = false
                    minimizedRegistry.unregister(title)
                },
                onDrag = { dragAmount ->
                    // TECHO DURO (a pedido explícito del usuario, con
                    // captura): nada puede arrastrarse por ENCIMA del
                    // header, ni siquiera el ícono minimizado. `offsetPx.y`
                    // en 0 ya es "pegado al pie del header" (ver el
                    // comentario grande sobre [FLOATING_WINDOW_TOP_MAGNET_MARGIN]
                    // más arriba) — `coerceAtLeast(0f)` evita que un
                    // arrastre rápido hacia arriba lo empuje más allá de
                    // ese límite y termine encima/detrás del header.
                    //
                    // BUG REAL corregido (ver [FloatingWindowMinimizedRegistry]):
                    // arrastrar este ícono ya minimizado ENCIMA de otro
                    // ícono minimizado los dejaba superpuestos, sin
                    // ningún aviso. `settleMinimizedPosition()` corre
                    // cada nueva posición contra el registro compartido
                    // y, si colisiona, la corre al hueco libre más
                    // cercano — el ícono "resbala" al lado del otro en
                    // vez de taparlo.
                    // BUG REAL corregido (a pedido explícito del usuario,
                    // con captura): arrastrar el ícono minimizado hacia
                    // los COSTADOS o hacia ABAJO lo perdía fuera del área
                    // visible — acá solo se agrega el techo por la
                    // derecha/izquierda y por abajo (`clampToSideLimits`/
                    // `clampToBottomLimit`, definidos más arriba); el
                    // límite de ARRIBA sigue siendo, sin tocar nada, el
                    // `coerceAtLeast(0f)` de la línea de acá abajo.
                    val badgeSizePx = with(density) { FLOATING_WINDOW_MINIMIZED_BADGE_SIZE.toPx() }
                    offsetPx = (offsetPx + dragAmount).let {
                        it.copy(
                            x = clampToSideLimits(it.x, badgeSizePx),
                            y = clampToBottomLimit(it.y.coerceAtLeast(0f), badgeSizePx)
                        )
                    }
                    // A PEDIDO EXPLÍCITO DEL USUARIO: mientras se arrastra
                    // este ícono, en cada frame se consulta el rectángulo
                    // REAL de la zona de "Eliminar" (publicado por
                    // [FloatingWindowDeleteDropZone] mientras está en
                    // pantalla — ver `deleteZoneRect` en
                    // [FloatingWindowMinimizedRegistry]) contra el CENTRO
                    // de este ícono, no contra su rectángulo completo: se
                    // siente más preciso soltar "apuntando" con el dedo
                    // que con cualquier borde del círculo. `!=` en vez de
                    // reescribir siempre el mismo valor: la vibración de
                    // abajo tiene que dispararse UNA sola vez al ENTRAR a
                    // la zona, no en cada uno de los muchos frames que el
                    // dedo pasa por encima de ella.
                    val badgeCenter = offsetPx + Offset(badgeSizePx / 2f, badgeSizePx / 2f)
                    val zone = minimizedRegistry.deleteZoneRect
                    val hoveringDeleteZoneNow = zone != null && zone.contains(badgeCenter)
                    if (hoveringDeleteZoneNow != minimizedRegistry.isDragOverDeleteZone) {
                        minimizedRegistry.isDragOverDeleteZone = hoveringDeleteZoneNow
                        if (hoveringDeleteZoneNow) {
                            triggerDeleteZoneHoverPulse()
                        }
                    }
                    settleMinimizedPosition()
                },
                onDragStart = {
                    // A PEDIDO EXPLÍCITO DEL USUARIO: la zona de
                    // "Eliminar" (ver [FloatingWindowDeleteDropZone])
                    // solo puede aparecer mientras se arrastra el ÍCONO
                    // YA MINIMIZADO — nunca al arrastrar la ventana
                    // abierta desde su cabecera. `isMinimizedBadgeDragging`
                    // es la única señal que la muestra/oculta.
                    minimizedRegistry.isMinimizedBadgeDragging = true
                    // Ver el comentario grande sobre `isDraggingThisBadge`,
                    // junto a su declaración: sube el `zIndex` de ESTE
                    // ícono por encima de todo (incluida la zona de
                    // eliminar) mientras dura el arrastre.
                    isDraggingThisBadge = true
                },
                onDragEnd = {
                    // Se decide acá, UNA sola vez al soltar el dedo, en
                    // vez de en cada frame de `onDrag`: evita borrar el
                    // efecto por error si el usuario pasó de refilón por
                    // encima de la zona sin querer soltar ahí — solo
                    // cuenta la posición FINAL, al momento exacto de
                    // soltar.
                    val shouldDelete = minimizedRegistry.isDragOverDeleteZone
                    minimizedRegistry.isMinimizedBadgeDragging = false
                    minimizedRegistry.isDragOverDeleteZone = false
                    isDraggingThisBadge = false
                    if (shouldDelete) {
                        // Pulso de confirmación (más largo y distinto al
                        // de "entraste a la zona", ver su comentario
                        // grande junto a la declaración) — el cierre
                        // táctil del gesto, no un aviso más.
                        triggerDeleteZoneConfirmPulse()
                        onDeleteEffect?.invoke()
                    }
                },
                // A PEDIDO EXPLÍCITO DEL USUARIO: solo ESTE ícono se
                // pinta de rojo — la combinación de "soy yo el que se
                // está arrastrando" (`isDraggingThisBadge`, LOCAL de
                // esta ventana) y "el que se arrastra está sobre la
                // zona" (`minimizedRegistry.isDragOverDeleteZone`,
                // COMPARTIDA entre las tres) es necesaria: sin la
                // primera, CUALQUIER badge minimizado se pintaría de
                // rojo apenas OTRO empezara a sobrevolar la zona.
                isOverDeleteZone = isDraggingThisBadge && minimizedRegistry.isDragOverDeleteZone
            )
            return@Box
        }

        // --- Tamaño de la ventana: dos fases.
        // Fase "auto" (`!userResized`, la de por defecto): ancho Y alto
        // LIBRES — envuelve cabecera + contenido a su tamaño NATURAL,
        // con techo en `maxAutoHeight`/`maxAutoWidth` (el área REAL
        // disponible) para que nunca se salga de pantalla. Se re-mide en
        // CADA composición (no solo la primera vez) para seguirle el
        // paso a contenido que cambia de forma después de abierta la
        // ventana (spinner de carga → sliders reales, cambio de capa
        // seleccionada, etc.) — ver el comentario grande sobre
        // `userResized`, más abajo, con el detalle del bug que esto
        // corrige.
        //
        // CORRECCIÓN REVERTIDA (confirmado por el usuario con captura:
        // "no puede ser, me jodiste el proyecto"): un intento anterior
        // FORZABA el alto de esta fase a `maxAutoHeight` siempre —
        // pensado para que la ventana "llegue hasta abajo" incluso con
        // poco contenido — pero el resultado real fue un panel gigante,
        // casi vacío, tapando media pantalla (los 5 sliders de "Color
        // Básico" arriba del todo y un enorme hueco morado debajo, sin
        // nada). Eso es peor que el bug original. Se vuelve al criterio
        // de siempre: la ventana envuelve su contenido real
        // (`heightIn(max = maxAutoHeight)` — techo, nunca piso), y
        // `maxAutoHeight` en sí SIGUE con su corrección real (más
        // arriba: ahora sí descuenta `offsetPx.y`, la posición vertical
        // de la ventana, algo que antes de esa corrección faltaba y
        // hacía que el techo calculado fuera mayor al espacio real
        // disponible) — así que una ventana con contenido genuinamente
        // alto (que sí necesite acercarse al borde inferior) puede
        // seguir haciéndolo sin recortarse, pero una ventana con poco
        // contenido (como "Color Básico") ya NO se estira de más para
        // "rellenar" un espacio que no necesita.
        // Ninguno de los `fillMaxSize()` de acá abajo se aplica todavía
        // en esta fase — ver `sized` — porque `fillMaxSize()` reclamaría
        // de entrada el techo completo en vez de envolver el contenido
        // real.
        // Fase "manual" (`userResized`, arranca cuando el usuario
        // arrastra la manija por primera vez): tamaño fijo de siempre,
        // exactamente igual que el comportamiento original — desde acá
        // el auto-tamaño no vuelve a tocar `windowSize`.
        val sized = userResized
        Box(
            modifier = Modifier
                .then(
                    if (sized) {
                        Modifier.size(windowSize ?: initialSize)
                    } else {
                        Modifier
                            .width(autoWidth)
                            .heightIn(min = 0.dp, max = maxAutoHeight)
                    }
                )
                // Ver el comentario grande sobre `onInteracted`, en la
                // firma de esta función, para el porqué: observa el
                // primer toque de CADA gesto en TODA la ventana (no solo
                // la cabecera) sin consumirlo (`requireUnconsumed =
                // false` + nunca se llama `change.consume()`), así que
                // esto convive sin pisar el arrastre de la cabecera ni
                // los sliders/botones de adentro — solo avisa "tocaron
                // esta ventana" para que el llamador la traiga al
                // frente de las otras dos.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onInteracted()
                    }
                }
                .onGloballyPositioned { coordinates ->
                    // Mientras el usuario no haya redimensionado a mano,
                    // esto corre en CADA layout — no solo el primero —
                    // así que si el contenido cambia de alto de verdad
                    // (spinner → sliders reales, u otro cambio), la
                    // ventana lo sigue automáticamente en vez de quedar
                    // pegada al alto del primer frame.
                    if (!userResized) {
                        val measuredHeight = with(density) { coordinates.size.height.toDp() }
                        windowSize = DpSize(
                            width = autoWidth,
                            height = (measuredHeight + FLOATING_WINDOW_RESIZE_HANDLE_CLEARANCE)
                                .coerceIn(minSize.height, maxAutoHeight)
                        )
                    }
                }
        ) {
          Surface(
            modifier = Modifier
                .then(if (sized) Modifier.fillMaxSize() else Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top))
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(12.dp)),
            color = SurfaceTintedElevated,
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(
                modifier = if (sized) Modifier.fillMaxSize() else Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top)
            ) {
                // --- Cabecera arrastrable: mismo rol que la barra de
                // título de un plugin flotante — tocar y arrastrar CUALQUIER
                // punto de esta fila reubica toda la ventana dentro del
                // canvas. `change.consume()` evita que el gesto se filtre
                // hacia abajo, al canvas (que interpretaría el mismo
                // arrastre como mover la CAPA de la imagen, no la ventana).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FLOATING_WINDOW_HEADER_HEIGHT)
                        .background(Color.Black.copy(alpha = 0.25f))
                        .pointerInput(Unit) {
                            // A PEDIDO EXPLÍCITO DEL USUARIO — imán al pie
                            // del header (ver el comentario grande sobre
                            // [FLOATING_WINDOW_TOP_MAGNET_MARGIN], más
                            // arriba, con las referencias entregadas).
                            // Se usa `onDragEnd` Y `onDragCancel` con la
                            // MISMA lógica — si algo externo corta el
                            // gesto a mitad de camino, igual se revisa
                            // dónde quedó la ventana en ese instante.
                            val magnetizeIfNearHeaderBottom: () -> Unit = {
                                val marginPx = with(density) {
                                    FLOATING_WINDOW_TOP_MAGNET_MARGIN.toPx()
                                }
                                if (offsetPx.y <= marginPx) {
                                    // Efecto imán de verdad, no solo un
                                    // umbral que dispara: al minimizar
                                    // acá, la ventana queda pegada
                                    // EXACTO al pie del header (y = 0),
                                    // sin importar en qué punto exacto
                                    // de la franja la hayas soltado.
                                    offsetPx = offsetPx.copy(y = 0f)
                                    isMinimized = true
                                    // BUG REAL corregido (ver
                                    // [FloatingWindowMinimizedRegistry]):
                                    // como las tres ventanas nacen cerca
                                    // unas de otras y todas magnetizan al
                                    // mismo `y = 0`, arrastrarlas por
                                    // turno a esta franja las dejaba a
                                    // todas en (casi) el mismo punto —
                                    // el ícono "doble" reportado. Ahora,
                                    // antes de asentarse, se corre al
                                    // hueco libre más cercano en esa
                                    // misma franja si ya hay otro ícono
                                    // ahí.
                                    settleMinimizedPosition()
                                }
                            }
                            // A PEDIDO EXPLÍCITO DEL USUARIO — "lanzar"
                            // la ventana hacia arriba desde cualquier
                            // parte de la pantalla (ver el comentario
                            // grande sobre
                            // [FLOATING_WINDOW_FLING_MINIMIZE_VELOCITY_DP_PER_SEC],
                            // más arriba, con el detalle completo del
                            // pedido). `VelocityTracker` mide qué tan
                            // rápido iba el dedo al soltar — no solo
                            // hacia dónde — que es exactamente lo que
                            // separa "un golpe rápido hacia arriba" de
                            // "un arrastre tranquilo que cruzó toda la
                            // pantalla": ambos pueden terminar recorriendo
                            // la misma distancia, pero solo el primero
                            // llega con velocidad alta al soltar.
                            val flingThresholdPx = with(density) {
                                FLOATING_WINDOW_FLING_MINIMIZE_VELOCITY_DP_PER_SEC.toPx()
                            }
                            // TERCER AJUSTE (reportado: "el primer intento
                            // sí funciona, pero repitiendo la prueba en la
                            // misma ventana cuesta más, a veces hacen
                            // falta varios intentos"): exigir un único
                            // pico de velocidad instantánea por encima de
                            // un umbral fijo es "todo o nada" — un flick
                            // real nunca sale exactamente igual dos veces
                            // seguidas, así que un umbral que hoy se
                            // cruza por poco mañana puede no cruzarse por
                            // poco también, aunque el gesto haya sido
                            // igual de intencional. Se agrega una SEGUNDA
                            // vía de detección, más tolerante: si durante
                            // el gesto la ventana ya recorrió una
                            // distancia real hacia arriba (no solo un
                            // pico de velocidad al soltar) Y terminó con
                            // una velocidad moderada — bastante menos
                            // exigente que el umbral "puro" de arriba —
                            // también cuenta como lanzamiento. Esto cubre
                            // el flick "medio" que antes quedaba en tierra
                            // de nadie: ni lento como para ser un
                            // reacomodo tranquilo, ni con un pico tan
                            // marcado como para cruzar el umbral solo por
                            // velocidad.
                            val flingDistanceThresholdPx = with(density) { 56.dp.toPx() }
                            val flingModerateVelocityThresholdPx = flingThresholdPx * 0.45f
                            val handleHeaderDragEnd: (VelocityTracker, Float) -> Unit =
                                { velocityTracker, totalDragY ->
                                val flingVelocityY = velocityTracker.calculateVelocity().y
                                // Negativo = hacia arriba (mismo sentido
                                // que ya usa el resto de la app para
                                // velocidad de arrastre — ver el
                                // "flick" de [GridAxisStepper], más
                                // abajo en este archivo). Cualquiera de
                                // las dos vías de acá abajo cuenta como
                                // lanzamiento real; si ninguna se cumple,
                                // cae al comportamiento de siempre (el
                                // imán, si ya estaba cerca del pie del
                                // header, o directamente nada).
                                val isFastFlick = flingVelocityY <= -flingThresholdPx
                                val isModerateFlickWithTravel =
                                    totalDragY <= -flingDistanceThresholdPx &&
                                        flingVelocityY <= -flingModerateVelocityThresholdPx
                                if (isFastFlick || isModerateFlickWithTravel) {
                                    val startY = offsetPx.y
                                    coroutineScope.launch {
                                        animate(
                                            initialValue = startY,
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = FLOATING_WINDOW_FLING_MINIMIZE_ANIMATION_MS,
                                                easing = FastOutLinearInEasing
                                            )
                                        ) { value, _ ->
                                            // Solo el alto "vuela" hasta
                                            // el pie del header — el
                                            // ancho (X) se queda donde
                                            // el golpe la soltó, así el
                                            // ícono no salta de lado a
                                            // lado de la pantalla, solo
                                            // sube.
                                            offsetPx = offsetPx.copy(y = value)
                                        }
                                        isMinimized = true
                                        settleMinimizedPosition()
                                    }
                                } else {
                                    magnetizeIfNearHeaderBottom()
                                }
                            }
                            // A PEDIDO EXPLÍCITO DEL USUARIO — SEGUNDO BUG
                            // REAL corregido (reportado: "a veces hay que
                            // lanzar varias veces para que se dispare").
                            // La primera versión de este bloque alimentaba
                            // el `VelocityTracker` con
                            // `addPosition(change.uptimeMillis,
                            // change.position)` — SOLO la posición final
                            // de cada evento. Pero en un gesto rápido,
                            // Android suele agrupar varios movimientos
                            // intermedios del dedo dentro de un mismo
                            // evento (sus muestras "históricas",
                            // expuestas en `change.historical`) en vez de
                            // entregar un evento por cada movimiento real
                            // — así que un flick veloz podía llegar acá
                            // como un solo evento con la posición final,
                            // perdiendo TODO el recorrido intermedio que
                            // definía qué tan rápido fue en verdad el
                            // golpe. Resultado: velocidad calculada baja
                            // e inconsistente según cómo el sistema
                            // decidiera agrupar ESE gesto puntual — de ahí
                            // que a veces disparara y otras veces no, con
                            // el mismo esfuerzo real del dedo.
                            // `VelocityTracker.addPointerInputChange(...)`
                            // es la función que expone Compose
                            // específicamente para esto: procesa también
                            // `change.historical`, así que ningún punto
                            // intermedio del flick se pierde y la
                            // velocidad calculada refleja el gesto
                            // completo, no solo su último instante.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPointerInputChange(down)
                                val pointerId = down.id
                                // Distancia total recorrida en Y durante
                                // ESTE gesto puntual (negativo = hacia
                                // arriba) — alimenta la segunda vía de
                                // detección de `handleHeaderDragEnd`, más
                                // arriba, junto al pico de velocidad.
                                var totalDragY = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null) {
                                        // Gesto cortado desde afuera (mismo
                                        // caso que cubría `onDragCancel`
                                        // antes) — igual se revisa la
                                        // velocidad acumulada hasta acá.
                                        handleHeaderDragEnd(velocityTracker, totalDragY)
                                        break
                                    }
                                    velocityTracker.addPointerInputChange(change)
                                    if (change.changedToUpIgnoreConsumed()) {
                                        change.consume()
                                        handleHeaderDragEnd(velocityTracker, totalDragY)
                                        break
                                    }
                                    val dragAmount = change.positionChange()
                                    change.consume()
                                    totalDragY += dragAmount.y
                                    // Mismo techo duro que en el badge
                                    // minimizado, más arriba — ver ese
                                    // comentario para el porqué. Antes esto
                                    // era `offsetPx += dragAmount` sin ningún
                                    // límite, así que un arrastre hacia arriba
                                    // metía la ventana entera (cabecera
                                    // incluida) por detrás/encima del header
                                    // real de la pantalla. Eso sigue igual,
                                    // sin tocarlo.
                                    //
                                    // BUG REAL corregido (a pedido explícito
                                    // del usuario, con captura): arrastrar la
                                    // ventana ABIERTA hacia los COSTADOS o
                                    // hacia ABAJO la perdía fuera del área
                                    // visible, exactamente igual que al ícono
                                    // minimizado — acá solo se agrega el
                                    // límite de costados/abajo, contra el
                                    // tamaño REAL actual de la ventana
                                    // (`windowSize` mientras se está
                                    // auto-midiendo, o `initialSize` si
                                    // todavía no se midió nada).
                                    val currentWindowSizePx = with(density) {
                                        val size = windowSize ?: initialSize
                                        Offset(size.width.toPx(), size.height.toPx())
                                    }
                                    offsetPx = (offsetPx + dragAmount).let {
                                        it.copy(
                                            x = clampToSideLimits(it.x, currentWindowSizePx.x),
                                            y = clampToBottomLimit(it.y.coerceAtLeast(0f), currentWindowSizePx.y)
                                        )
                                    }
                                }
                            }
                        }
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido:
                    // acá había `weight(1f, fill = false)` en el `Text` del
                    // título, que hacía que NUNCA reclamara el espacio
                    // sobrante de la fila — solo ocupaba lo que su propio
                    // contenido medía. Resultado: al ensanchar la ventana
                    // (arrastrando la manija de la esquina), el grupo de
                    // controles de acá abajo (Presets/‹›/-/×) quedaba
                    // pegado justo después del título, en vez de seguir la
                    // esquina superior derecha del header como corresponde.
                    // `weight(1f)` a secas (fill = true, el default, ver
                    // [FloatingWindowHeaderRowContent] con `titleWeighted =
                    // true` acá abajo) es lo que hace que ESTE Text sea el
                    // que se estira para absorber todo el ancho sobrante —
                    // y como el grupo de controles va inmediatamente
                    // después de él en la Row, eso es lo que lo deja
                    // siempre pegado a la esquina derecha, sin importar
                    // cuánto se ensanche la ventana. La elipsis
                    // (maxLines=1 + TextOverflow.Ellipsis) sigue ahí como
                    // red de seguridad para un preset con nombre
                    // absurdamente largo — ver el comentario grande sobre
                    // `autoWidth`, más arriba, para el porqué el título ya
                    // NO depende de la elipsis en el caso normal.
                    FloatingWindowHeaderRowContent(
                        title = title,
                        titleIcon = titleIcon,
                        titleWeighted = true,
                        presets = presets,
                        selectedPresetIndex = selectedPresetIndex,
                        presetsMenuExpanded = presetsMenuExpanded,
                        onPresetsTogglePress = { presetsMenuExpanded = !presetsMenuExpanded },
                        onPresetsDismiss = { presetsMenuExpanded = false },
                        onPresetSelect = { index ->
                            selectedPresetIndex = index
                            presetsMenuExpanded = false
                        },
                        onPresetPrev = {
                            val current = selectedPresetIndex ?: 0
                            selectedPresetIndex = (current - 1 + presets.size) % presets.size
                        },
                        onPresetNext = {
                            val current = selectedPresetIndex ?: -1
                            selectedPresetIndex = (current + 1) % presets.size
                        },
                        onMinimize = {
                            isMinimized = true
                            // Mismo ajuste anti-superposición que el
                            // imán del pie del header — ver el
                            // comentario grande sobre
                            // [FloatingWindowMinimizedRegistry]: el
                            // botón "-" también puede dejar dos íconos
                            // en el mismo punto si la ventana ya estaba
                            // arrastrada justo encima de donde otra
                            // quedó minimizada antes.
                            settleMinimizedPosition()
                        },
                        onClose = onClose,
                        menuExpanded = optionsMenuExpanded,
                        onMenuTogglePress = { optionsMenuExpanded = !optionsMenuExpanded },
                        onMenuDismiss = { optionsMenuExpanded = false }
                    )
                }

                // --- Medición "fantasma" del ancho NATURAL de la cabecera
                // (ver el comentario grande sobre `autoWidth`, más arriba):
                // compone la MISMA [FloatingWindowHeaderRowContent] de
                // arriba, pero con `titleWeighted = false` (el título toma
                // su ancho real, sin recortar) dentro de un `Row` con el
                // MISMO padding horizontal que la cabecera visible, medido
                // con `Constraints()` — sin límite de ancho, algo que la
                // cabecera VISIBLE nunca podría aceptar sin romperse (un
                // `Row` con un hijo `weight()` no admite un ancho máximo
                // infinito). El resultado de este `Layout` nunca se
                // dibuja — `layout(0, 0) {}` lo deja en tamaño cero, así
                // que no ocupa lugar ni es tocable — solo existe para
                // capturar el ancho real en `naturalHeaderWidthPx`.
                if (!userResized) {
                    Layout(
                        content = {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FloatingWindowHeaderRowContent(
                                    title = title,
                                    titleIcon = titleIcon,
                                    titleWeighted = false,
                                    presets = presets,
                                    selectedPresetIndex = selectedPresetIndex,
                                    presetsMenuExpanded = false,
                                    onPresetsTogglePress = {},
                                    onPresetsDismiss = {},
                                    onPresetSelect = {},
                                    onPresetPrev = {},
                                    onPresetNext = {},
                                    onMinimize = {},
                                    onClose = onClose,
                                    menuExpanded = false,
                                    onMenuTogglePress = {},
                                    onMenuDismiss = {}
                                )
                            }
                        }
                    ) { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        if (placeable.width != naturalHeaderWidthPx) {
                            naturalHeaderWidthPx = placeable.width
                        }
                        layout(0, 0) {}
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Column(
                    modifier = Modifier
                        .then(if (sized) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    content = content
                )
            }
        }

        // --- Manija de redimensionar: esquina inferior derecha, mismo
        // patrón visual "tres rayas diagonales" que usan la mayoría de
        // ventanas flotantes de escritorio.
        //
        // BUG REAL corregido (reportado con captura: la ventana quedaba
        // con texto cortado en el borde derecho — "Nitid", "Satu",
        // "Cont", "Tono" — sin importar dónde se reposicionara).
        // `coerceAtLeast(minSize...)` le ponía un PISO (nunca encogerla
        // tanto que el contenido deje de caber/leerse) pero NUNGÚN
        // TECHO: arrastrando esta manija hacia afuera, la ventana podía
        // crecer más ANCHA (o más ALTA) que el área real disponible.
        // Una vez así de grande, NINGÚN reposicionamiento la arregla —
        // ni el recorte de `offsetPx` de más arriba (ver el
        // `LaunchedEffect` sobre `clampToSideLimits`/`clampToBottomLimit`)
        // puede hacer que una ventana más ancha que la pantalla quepa
        // entera: mover la ventana solo decide QUÉ PARTE se corta, nunca
        // evita que se corte. La causa real estaba acá, en el tamaño
        // mismo, no en la posición.
        //
        // La corrección agrega el techo que faltaba: `coerceIn` en vez
        // de `coerceAtLeast`, con `maxAutoWidth`/`maxAutoHeight` (ya
        // corregidos más arriba para reflejar el área REAL del
        // contenedor, no la pantalla física completa) como límite
        // superior — exactamente el mismo número que ya usa el
        // auto-tamaño, así que el resultado final es consistente sin
        // importar si la ventana llegó a este tamaño sola o porque el
        // usuario arrastró la manija a mano.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaWidth = with(density) { dragAmount.x.toDp() }
                        val deltaHeight = with(density) { dragAmount.y.toDp() }
                        // Primer arrastre de la manija: acá es donde la
                        // ventana pasa de "auto" a "manual" de verdad
                        // (ver `userResized`, más arriba) — de acá en
                        // adelante el auto-tamaño no vuelve a tocar
                        // `windowSize`, es 100% del usuario, dentro del
                        // rango [minSize, maxAutoWidth/maxAutoHeight].
                        val base = windowSize ?: initialSize
                        windowSize = DpSize(
                            width = (base.width + deltaWidth).coerceIn(minSize.width, maxAutoWidth),
                            height = (base.height + deltaHeight).coerceIn(minSize.height, maxAutoHeight)
                        )
                        userResized = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val stroke = Stroke(width = 1.6.dp.toPx())
                val c = Color.White.copy(alpha = 0.55f)
                for (i in 0..2) {
                    val offset = i * 5.dp.toPx()
                    drawLine(
                        color = c,
                        start = androidx.compose.ui.geometry.Offset(size.width, offset),
                        end = androidx.compose.ui.geometry.Offset(offset, size.height),
                        strokeWidth = stroke.width
                    )
                }
            }
        }
        }
    }
}

// --- Reporte del usuario, con captura de "Color Básico": "al
// expandirse se ve como que se expande una parte y luego el resto...
// trabado". Historial de intentos:
// 1) Se intentó envolver el `if (isLoading)` de
//    [ColorBasicoFloatingWindow]/[Basico3DFloatingWindow] en
//    `AnimatedContent` + `SizeTransform` — ESO INTRODUJO UNA REGRESIÓN
//    PEOR, confirmada por el usuario con otra captura: texto de los
//    sliders (Nitidez/Saturación/Brillo/Contraste/Tono) superpuesto y
//    mezclado, ilegible. Revertido por completo en su momento.
// 2) Diagnóstico real, confirmado después: el salto de tamaño no era un
//    problema de ANIMACIÓN — era que el `isLoading` en sí no tenía
//    motivo de existir ahí. Los sliders de estas ventanas (y los de
//    [EffectsPanel]/[DistortionPanel], mismo patrón exacto) no dibujan
//    ni dependen del bitmap para nada — solo leen/escriben estado en
//    memoria (`ctrl`, o variables locales como `rotationX`). El bitmap
//    que `isLoading` esperaba es SOLO para la vista previa en vivo
//    sobre el canvas, algo que las funciones que la disparan
//    (`applyLivePreviewAndScheduleCommit`/`scheduleLivePreview`, etc.)
//    ya tratan como opcional (`if (small != null ...)` / `?: return`).
//    La corrección final fue sacar el `if (isLoading) spinner else
//    contenido` de las cuatro ventanas y mostrar los controles directo
//    — sin animación de por medio, porque ya no hay dos tamaños
//    distintos entre los que saltar. Ver el comentario grande dentro de
//    [ColorBasicoFloatingWindow], justo antes de su `EffectsCategoryColor(...)`,
//    para el detalle completo.
// 3) [RecolorFloatingWindow] quedó AFUERA de la corrección del punto 2 —
//    a diferencia de las otras cuatro, sus sliders SÍ dependen de un
//    dato real que hay que leer del bitmap (la paleta de colores), así
//    que en un primer intento se le agregó su propio spinner de carga
//    (`isLoadingPalette`) más un estado "atenuado/deshabilitado" para el
//    bloque de sliders+rueda mientras no había color elegido. Confirmado
//    por el usuario con otra captura: eso se veía PEOR que el bug
//    original, dos señales de "cargando" a la vez y nada instantáneo
//    como en Color Básico/3D Básico. Corrección final, ahora sí igual de
//    radical que en el punto 2: se sacó el spinner Y el atenuado. La fila
//    de paleta reserva su alto fijo desde el primer frame (34.dp, tenga
//    0 o 10 colores) y los sliders + la rueda están SIEMPRE visibles y
//    habilitados — arrancan en su valor default (blanco a pleno brillo)
//    y `selectSwatch` los actualiza solo apenas la paleta real llega, sin
//    ningún salto de tamaño ni ningún estado visual "cargando" de por
//    medio. Ver el bloque `Box(Modifier...height(34.dp))` dentro de
//    [RecolorFloatingWindow] para el detalle.
//
/**
 * Botón compacto de cabecera (flechas `‹`/`›` y `-`) — mismo estilo para
 * los tres, a propósito: círculo invisible de 20.dp (área de toque),
 * ícono SVG premium de 11.dp ya existente en `res/drawable` (nunca uno
 * nuevo, tal como se pidió explícitamente). `enabled = false` atenúa el
 * ícono y desactiva el toque, en vez de ocultar el botón — así la
 * cabecera no "salta" de ancho cuando `presets` está vacía.
 */
@Composable
private fun FloatingWindowHeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 0.75f else 0.25f),
            modifier = Modifier.size(11.dp)
        )
    }
}

// --- Panel de OPCIONES de la ventana (A PEDIDO DEL USUARIO — rediseño
// v2, con referencias reales entregadas: el menú de opciones de un
// plugin de FL Studio, tipo "FL Keys" — ver capturas). La versión
// anterior era una "bandeja lateral" angosta pegada al costado de la
// ventana; el usuario pidió explícitamente que en vez de eso sea un
// desplegable NORMAL, como cualquier menú de aplicación: aparece DEBAJO
// de su ícono (el mismo lenguaje que ya usa [FloatingWindowPresetsControl]
// con "Presets"), pegado cerca de la ventana — no lanzado lejos hacia
// un costado — y con un ancho de menú real (no una barra angostísima de
// 64.dp, pensada para eso ya no aplica ahora que el contenido va a ser
// texto en filas, como en la referencia de FL Studio).
private val OPTIONS_MENU_PANEL_WIDTH = 216.dp
private val OPTIONS_MENU_PANEL_CORNER_RADIUS = 14.dp
private val OPTIONS_MENU_PANEL_ACCENT_HEIGHT = 3.dp

/**
 * Botón de cabecera que abre [FloatingWindowOptionsMenuButton] — el
 * glifo de tres cuadrados ([R.drawable.ic_menu_option], el SVG premium
 * entregado por el usuario, sin modificar) en el mismo botón circular
 * de 22.dp que ya usan `‹`/`›`/`-` (ver [FloatingWindowHeaderIconButton]),
 * pegado como el PRIMER elemento de la cabecera — a la izquierda de
 * [titleIcon] — tal como se pidió explícitamente ("al lado izquierdo del
 * icono del efecto arriba superior izquierda del header").
 */
@Composable
private fun FloatingWindowOptionsMenuButton(
    expanded: Boolean,
    onTogglePress: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val edgeMarginPx = with(density) { 8.dp.roundToPx() }
    val verticalGapPx = with(density) { 6.dp.roundToPx() }
    val panelWidthPx = with(density) { OPTIONS_MENU_PANEL_WIDTH.roundToPx() }
    // A PEDIDO EXPLÍCITO DEL USUARIO — referencias entregadas (menú de
    // opciones de un plugin de FL Studio, ver capturas): el panel ahora
    // se abre como un desplegable NORMAL, DEBAJO de este ícono — igual
    // que [FloatingWindowPresetsControl] con "Presets" — en vez de la
    // "bandeja lateral" de la versión anterior. Sigue midiendo la
    // posición real del botón en pantalla (`onGloballyPositioned` +
    // `positionInWindow()`, ver comentario de más abajo sobre el bug de
    // la v1), pero ahora solo para mantenerlo PEGADO cerca de la
    // ventana ("no muy afuera", pedido textual): si abrirlo pegado al
    // borde izquierdo del ícono lo saca de la pantalla por la derecha,
    // se corre lo mínimo necesario hacia la izquierda para que quede
    // completo — nunca lo lanza lejos hacia un costado como antes.
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    var anchorXInWindowPx by remember { mutableStateOf(0f) }
    var anchorYInWindowPx by remember { mutableStateOf(0f) }
    var anchorHeightPx by remember { mutableStateOf(0) }
    var panelHeightPx by remember { mutableStateOf(0) }

    // Corrimiento horizontal mínimo para que el panel no se salga de la
    // pantalla por la derecha — si entra completo abierto pegado al
    // ícono (el caso normal, ventana no pegada al borde), este valor es
    // 0 y el panel queda exactamente debajo del ícono, sin correrse.
    val horizontalOverflowPx = (anchorXInWindowPx.toInt() + panelWidthPx + edgeMarginPx) - screenWidthPx
    val offsetXPx = if (horizontalOverflowPx > 0) -horizontalOverflowPx else 0

    // Igual que arriba pero en vertical: si no entra hacia abajo (ventana
    // pegada cerca del borde inferior de la pantalla), se abre hacia
    // ARRIBA del ícono en su lugar — nunca queda cortado por abajo.
    val opensUpward = panelHeightPx > 0 &&
        anchorYInWindowPx + anchorHeightPx + verticalGapPx + panelHeightPx + edgeMarginPx > screenHeightPx
    val offsetYPx = if (opensUpward) {
        -(panelHeightPx + verticalGapPx)
    } else {
        anchorHeightPx + verticalGapPx
    }

    Box {
        Box(
            modifier = Modifier
                .size(22.dp)
                .onGloballyPositioned { coordinates ->
                    val positionInWindow = coordinates.positionInWindow()
                    anchorXInWindowPx = positionInWindow.x
                    anchorYInWindowPx = positionInWindow.y
                    anchorHeightPx = coordinates.size.height
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePress
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_menu_option),
                contentDescription = "Opciones de la ventana",
                tint = Color.White.copy(alpha = if (expanded) 0.95f else 0.75f),
                modifier = Modifier.size(12.dp)
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = offsetXPx, y = offsetYPx),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .width(OPTIONS_MENU_PANEL_WIDTH)
                        .onGloballyPositioned { coordinates ->
                            panelHeightPx = coordinates.size.height
                        }
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(OPTIONS_MENU_PANEL_CORNER_RADIUS),
                            clip = false
                        ),
                    color = SurfaceTintedDark,
                    shape = RoundedCornerShape(OPTIONS_MENU_PANEL_CORNER_RADIUS),
                    border = BorderStroke(1.dp, BrandPurpleLight.copy(alpha = 0.4f))
                ) {
                    Column {
                        // Misma franja de acento que [FloatingWindowPresetsControl]
                        // — la firma visual que identifica esto como un panel
                        // propio de la app — ahora horizontal, como en ese
                        // panel, porque el desplegable en sí es ancho y bajo
                        // (un menú normal), no angosto y alto.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(OPTIONS_MENU_PANEL_ACCENT_HEIGHT)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = OPTIONS_MENU_PANEL_CORNER_RADIUS,
                                        topEnd = OPTIONS_MENU_PANEL_CORNER_RADIUS
                                    )
                                )
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(BrandPurpleLight, BrandPurpleDeep)
                                    )
                                )
                        )
                        // Estado vacío — mismo lenguaje que el estado vacío
                        // de [FloatingWindowPresetsControl] (ícono atenuado +
                        // texto corto), en vez del espacio en blanco liso de
                        // la versión anterior: todavía no hay opciones
                        // reales para listar (el contenido de este menú —
                        // qué comandos van adentro, en qué secciones — lo
                        // define el usuario en un paso siguiente), pero
                        // mientras tanto se ve como un panel terminado, no
                        // como un rectángulo vacío sin explicación.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_menu_option),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Todavía no hay opciones disponibles",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// A PEDIDO EXPLÍCITO DEL USUARIO: el panel de presets tenía el MISMO
// color que la ventana principal (SurfaceTintedElevated) y prácticamente
// se perdía contra ella — sin sombra propia, sin borde, sin ninguna
// marca de identidad. Estas constantes son SU paleta propia, un
// escalón más oscuro que la ventana que lo contiene (mismo criterio que
// ya usa el resto de la app para "un panel flotando sobre otro" — ver
// SurfaceTintedDark vs. SurfaceTintedElevated en Theme.kt), más un
// acento de marca (la franja superior) para que se identifique de un
// vistazo como una ventana propia, no como una continuación de la de
// abajo.
private val PRESETS_PANEL_WIDTH = 224.dp
private val PRESETS_PANEL_CORNER_RADIUS = 14.dp
private val PRESETS_PANEL_ACCENT_HEIGHT = 3.dp

/**
 * Control "Presets" de la cabecera — a pedido explícito:
 * - Sin preset elegido: muestra la etiqueta genérica "Presets".
 * - Con un preset elegido (ver [FloatingToolWindow.selectedPresetIndex]):
 *   pasa a mostrar el NOMBRE de ese preset en su lugar.
 * - Tocarlo despliega el panel de presets de este módulo.
 *
 * A PEDIDO EXPLÍCITO DEL USUARIO — el panel ahora tiene identidad visual
 * PROPIA en vez de mimetizarse con la ventana que lo contiene:
 * - Color propio, un escalón más oscuro que la ventana (`SurfaceTintedDark`
 *   vs. el `SurfaceTintedElevated` de la ventana — ver constantes de
 *   arriba), en vez de compartir exactamente el mismo tono.
 * - Borde sutil en el morado de marca ([BrandPurpleLight]) — la misma
 *   marca visual que ya usan otras superficies elevadas de la app (ver
 *   [FloatingWindowMinimizedBadge]) para separarse de lo que hay debajo.
 * - Franja de acento en la parte superior, en degradé de marca — la
 *   "firma" que identifica esto como el panel de Presets de un vistazo,
 *   sin necesidad de leer el título.
 * - Sombra propia, más pronunciada que la de la ventana (24.dp vs.
 *   16.dp) — reforzando que este panel "flota" POR ENCIMA de la
 *   ventana, no que es parte de ella.
 *
 * Técnicamente, se armó con [Popup] en vez de [DropdownMenu]: así el
 * panel es 100% nuestro (color, forma, sombra, franja de acento) sin
 * pelear contra el `Surface` interno por defecto que trae `DropdownMenu`
 * — y sigue sin recortarse contra los bordes redondeados de la ventana
 * (mismo motivo por el que `DropdownMenu` también usa un `Popup` por
 * debajo: un panel que puede sobresalir de una ventana angosta o
 * pequeña no puede vivir DENTRO del `Surface` que la recorta).
 */
@Composable
private fun FloatingWindowPresetsControl(
    presets: List<String>,
    selectedIndex: Int?,
    expanded: Boolean,
    onTogglePress: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val label = selectedIndex?.let { presets.getOrNull(it) } ?: "Presets"
    val hasSelection = selectedIndex != null && presets.getOrNull(selectedIndex) != null
    val density = LocalDensity.current
    // Alto real del botón "Presets" — se mide una sola vez con
    // `onGloballyPositioned` para poder ubicar el panel emergente
    // pegado justo debajo, sin un margen "a ojo" fijo que quede mal si
    // el texto del botón cambia de tamaño (p. ej. al pasar de "Presets"
    // a un nombre de preset más alto).
    var anchorHeightPx by remember { mutableStateOf(0) }
    val verticalGapPx = with(density) { 6.dp.roundToPx() }

    Box {
        Row(
            modifier = Modifier
                .onGloballyPositioned { coordinates -> anchorHeightPx = coordinates.size.height }
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePress
                )
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .widthIn(max = 84.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = if (hasSelection) 0.92f else 0.45f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (hasSelection) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = anchorHeightPx + verticalGapPx),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .width(PRESETS_PANEL_WIDTH)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(PRESETS_PANEL_CORNER_RADIUS),
                            clip = false
                        ),
                    color = SurfaceTintedDark,
                    shape = RoundedCornerShape(PRESETS_PANEL_CORNER_RADIUS),
                    border = BorderStroke(1.dp, BrandPurpleLight.copy(alpha = 0.4f))
                ) {
                    Column {
                        // --- Franja de acento: la "firma" visual propia
                        // de este panel, en degradé de marca. Va PEGADA
                        // arriba, recortada a las esquinas redondeadas
                        // del panel (mismo `RoundedCornerShape` que el
                        // Surface que la contiene) para que no se vean
                        // picos cuadrados asomando por detrás del borde
                        // curvo.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PRESETS_PANEL_ACCENT_HEIGHT)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = PRESETS_PANEL_CORNER_RADIUS,
                                        topEnd = PRESETS_PANEL_CORNER_RADIUS
                                    )
                                )
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(BrandPurpleLight, BrandPurpleDeep)
                                    )
                                )
                        )
                        Text(
                            text = "PRESETS",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            // BUG REAL DE COMPILACIÓN corregido — esto es lo
                            // que rompió el build en GitHub Actions:
                            // `Modifier.padding(horizontal, top, bottom)`
                            // NO es ninguna de las sobrecargas válidas de
                            // `padding()` (solo existen: `all`, `horizontal`
                            // + `vertical`, `start/top/end/bottom` los
                            // cuatro juntos, o `PaddingValues`) — mezclar
                            // `horizontal` con `top`/`bottom` sueltos no
                            // resuelve a ninguna. Usando los cuatro lados
                            // explícitos (`start`/`top`/`end`/`bottom`) en
                            // vez de `horizontal` es lo que sí compila.
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 6.dp)
                        )
                        if (presets.isEmpty()) {
                            // Estado vacío — a pedido explícito ("esto por
                            // ahora"): el panel ya tiene su propia
                            // identidad visual y ya existe el lugar donde
                            // van a vivir los presets de este módulo, pero
                            // todavía no hay ninguno cargado.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_image_placeholder),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Todavía no hay presets guardados",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                presets.forEachIndexed { index, presetName ->
                                    val isSelected = index == selectedIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onSelect(index) }
                                            )
                                            .background(
                                                if (isSelected) BrandPurpleLight.copy(alpha = 0.16f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = presetName,
                                            color = if (isSelected) Color.White
                                                else Color.White.copy(alpha = 0.75f),
                                            fontWeight = if (isSelected) FontWeight.SemiBold
                                                else FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ventana minimizada — a pedido explícito: al minimizar, la ventana
 * flotante entera se "recoge" a solo este ícono circular (mismo glifo
 * que ya usa la cabecera como identificador de la ventana, ver
 * [FloatingToolWindow] — no se dibuja ningún ícono nuevo), en la misma
 * posición donde estaba. Un solo toque la restaura tal cual — mismo
 * tamaño y lugar que tenía antes de minimizarla.
 *
 * A PEDIDO EXPLÍCITO DEL USUARIO — corregido: este ícono ahora es
 * arrastrable por TODA la pantalla, igual que la cabecera de la ventana
 * cuando está abierta (mismo mecanismo — `detectDragGestures` +
 * `change.consume()` — para que el gesto no se filtre hacia el canvas de
 * atrás). `onDrag` reporta cada delta hacia [FloatingToolWindow], que lo
 * suma al mismo `offsetPx` que ya posiciona la ventana completa, así que
 * restaurarla después deja la ventana exactamente donde quedó el ícono.
 *
 * BUG REAL corregido (reportado con captura, comparando la cabecera
 * contra el badge minimizado): este badge dibujaba SIEMPRE
 * `ic_move_both_axes` fijo, sin relación con qué ventana era —
 * Recolor, Color Básico y 3D Básico quedaban con el mismo ícono
 * genérico de "mover" apenas se minimizaban, aunque cada una ya tenía
 * su propio glifo distinto en la cabecera abierta ([titleIcon], pasado
 * por cada llamador — ver [FloatingToolWindow]). Ahora este badge
 * recibe ese mismo `titleIcon` y lo reutiliza tal cual, así el ícono
 * minimizado es siempre el mismo que el de la cabecera de esa ventana,
 * nunca el de otra.
 */
@Composable
private fun FloatingWindowMinimizedBadge(
    title: String,
    titleIcon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    onDrag: (Offset) -> Unit,
    // A PEDIDO EXPLÍCITO DEL USUARIO: la zona de "Eliminar" (ver
    // [FloatingWindowDeleteDropZone]) solo tiene que aparecer MIENTRAS
    // el dedo sigue abajo arrastrando este ícono — no antes, no
    // después. `detectDragGestures` ya distingue estos tres momentos
    // por separado (a diferencia del `onDrag` de arriba, que solo
    // reporta el DELTA de cada frame, sin avisar cuándo empieza o
    // termina el gesto completo) — `onDragCancel` cuenta como "terminó"
    // igual que soltar, para que la zona nunca quede pegada en pantalla
    // si el sistema cancela el gesto a mitad de camino (una llamada
    // entrante, un scroll del sistema, etc.).
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido (reportado con
    // captura): "al ubicarse arriba [de la zona de Eliminar] todas las
    // bolas o íconos flotantes deben ponerse o pintarse rojo". Antes
    // SOLO la zona de destino reaccionaba (crecía + se ponía roja) — el
    // ícono que se está arrastrando se quedaba con su mismo color
    // morado de siempre, sin ninguna señal propia de "esto se va a
    // borrar". Es exactamente al revés de cómo se comporta cualquier
    // "arrastrar para eliminar" serio (el Dock de macOS, por ejemplo):
    // el elemento que ESTÁS SOSTENIENDO tiene que avisar por sí mismo,
    // no depender solo de mirar el blanco de abajo.
    //
    // `true` mientras este ícono en particular está sobrevolando la
    // zona (ver `isDraggingThisBadge && minimizedRegistry.isDragOverDeleteZone`
    // en [FloatingToolWindow] — a propósito la combinación de ambas, no
    // solo la segunda: esa es compartida entre las tres ventanas, así
    // que sin la primera CUALQUIER badge se pintaría de rojo apenas
    // OTRO empezara a sobrevolar la zona).
    isOverDeleteZone: Boolean = false
) {
    val deleteRed = Color(0xFFFF3B30)
    // Mismo criterio de color/duración que la propia zona de "Eliminar"
    // (ver [FloatingWindowDeleteDropZone]) — que ambos lados del gesto
    // "hablen el mismo idioma visual" en el momento de la confirmación.
    val badgeBackgroundColor by animateColorAsState(
        targetValue = if (isOverDeleteZone) deleteRed else SurfaceTintedElevated,
        animationSpec = tween(160),
        label = "minimizedBadgeDeleteBackground"
    )
    val badgeBorderColor by animateColorAsState(
        targetValue = if (isOverDeleteZone) Color.White.copy(alpha = 0.85f) else BrandPurpleLight.copy(alpha = 0.45f),
        animationSpec = tween(160),
        label = "minimizedBadgeDeleteBorder"
    )
    val badgeIconColor by animateColorAsState(
        targetValue = if (isOverDeleteZone) Color.White else Color.White.copy(alpha = 0.85f),
        animationSpec = tween(160),
        label = "minimizedBadgeDeleteIconTint"
    )
    Box(
        modifier = Modifier
            .size(FLOATING_WINDOW_MINIMIZED_BADGE_SIZE)
            .shadow(
                elevation = if (isOverDeleteZone) 18.dp else 10.dp,
                shape = CircleShape,
                ambientColor = if (isOverDeleteZone) deleteRed else Color.Black,
                spotColor = if (isOverDeleteZone) deleteRed else Color.Black
            )
            .clip(CircleShape)
            .background(badgeBackgroundColor)
            .border(if (isOverDeleteZone) 2.dp else 1.dp, badgeBorderColor, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = "Restaurar $title" },
        contentAlignment = Alignment.Center
    ) {
        titleIcon(badgeIconColor)
    }
}

// A PEDIDO EXPLÍCITO DEL USUARIO — tamaño de reposo de la zona de
// "Eliminar", más grande que un badge normal (ver
// FLOATING_WINDOW_MINIMIZED_BADGE_SIZE) a propósito: tiene que leerse
// como un blanco fácil de acertar al soltar, no como un botón chico más.
private val FLOATING_WINDOW_DELETE_ZONE_SIZE = 68.dp

/**
 * Zona de "Eliminar" — aparece flotando en la parte de ABAJO del
 * canvas, centrada, únicamente MIENTRAS se arrastra el ÍCONO YA
 * MINIMIZADO de cualquiera de las tres ventanas de efectos (Recolor /
 * Color Básico / 3D Básico) — nunca al arrastrar la ventana ABIERTA
 * desde su cabecera, a pedido explícito del usuario (ver
 * `isMinimizedBadgeDragging` en [FloatingWindowMinimizedRegistry], el
 * único interruptor que la muestra/oculta). Soltar el ícono encima de
 * esta zona borra ese efecto por completo — no solo lo minimiza ni lo
 * cierra — ver `onDeleteEffect` en [FloatingToolWindow].
 *
 * Estados visuales, de reposo a confirmación, pensados para leerse como
 * un gesto "premium" real (referencia: la papelera de macOS/iOS al
 * arrastrar un ícono del Dock) y no como un simple ícono estático:
 *
 * 1. REPOSO (apenas empieza el arrastre): A PEDIDO EXPLÍCITO DEL
 *    USUARIO — la zona entera "sube" desde abajo del canvas (no desde
 *    su propio centro): entra deslizándose verticalmente desde una
 *    posición inicial igual a su propia altura (`slideInVertically`,
 *    `initialOffsetY = { fullHeight -> fullHeight }`) MÁS un fundido y
 *    un "pop" de escala leve (entra un poco más chica que su tamaño
 *    final y se asienta, en vez de aparecer ya a tamaño completo — se
 *    siente más vivo, menos un simple `visible = true/false`). Mientras
 *    nadie la sobrevuela, respira sola con un pulso muy sutil de escala
 *    (`idlePulse`, infinito) — una invitación pasiva a "soltá acá", no
 *    una animación decorativa que grite.
 * 2. SOBREVOLADA (el ícono arrastrado entra en su rectángulo, ver
 *    `isDragOverDeleteZone`): el pulso de reposo se apaga (ver más
 *    abajo, `idleScale`) y la zona entera "salta" a una escala mayor
 *    con resorte (`spring`, no `tween` — un resorte se siente como una
 *    reacción física real al contacto, un tween se siente animado a
 *    propósito) + el anillo y el fondo se intensifican a rojo sólido +
 *    la sombra se agranda (más "elevación" = más foco). Junto con esto
 *    se dispara la vibración de confirmación (una sola vez al entrar,
 *    ver el `onDrag` del badge en [FloatingToolWindow]).
 * 3. SALIDA (se deja de arrastrar, con o sin soltar encima): el mismo
 *    viaje que la entrada pero al revés — se hunde de nuevo hacia abajo
 *    (`slideOutVertically`, mismo `targetOffsetY`) + fundido + reducción
 *    de escala, simétrico a como apareció. Que entre y salga por el
 *    mismo camino (subir/bajar, no aparecer/desaparecer en el lugar) es
 *    lo que la hace leerse como un objeto físico real entrando al
 *    cuadro, no como un ícono que un `if` prende y apaga.
 */
@Composable
private fun BoxScope.FloatingWindowDeleteDropZone(
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    modifier: Modifier = Modifier
) {
    val isDragging = minimizedRegistry.isMinimizedBadgeDragging
    val isHovering = minimizedRegistry.isDragOverDeleteZone

    AnimatedVisibility(
        visible = isDragging,
        // A PEDIDO EXPLÍCITO DEL USUARIO: entra/sale deslizándose desde
        // ABAJO del canvas (no un fundido+escala que "aparece" en el
        // lugar) — `initialOffsetY`/`targetOffsetY` reciben la altura YA
        // MEDIDA de este propio composable (`fullHeight`, en píxeles) y
        // devuelven esa misma altura completa como desplazamiento
        // inicial/final, así el punto de partida real es "un cuerpo
        // entero por debajo de su posición de reposo" sin necesidad de
        // adivinar ningún valor fijo en dp. El fundido + "pop" de escala
        // quedan encima del slide (no lo reemplazan) para que la llegada
        // se sienta amortiguada, no un golpe seco al llegar a destino.
        enter = slideInVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            initialOffsetY = { fullHeight -> fullHeight }
        ) + fadeIn(animationSpec = tween(200)) +
            scaleIn(initialScale = 0.82f, animationSpec = tween(260, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(
            animationSpec = tween(180, easing = FastOutSlowInEasing),
            targetOffsetY = { fullHeight -> fullHeight }
        ) + fadeOut(animationSpec = tween(150)) +
            scaleOut(targetScale = 0.82f, animationSpec = tween(150, easing = FastOutSlowInEasing)),
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 28.dp)
            // Publica el rectángulo REAL de esta zona (posición + tamaño,
            // en el MISMO sistema de coordenadas que `offsetPx` de cada
            // badge — ambos son hijos directos del mismo Box exterior,
            // ver `floatingWindowAreaSizePx` donde se mide ese Box) para
            // que [FloatingToolWindow] pueda comparar la posición del
            // ícono arrastrado contra ella en cada frame.
            .onGloballyPositioned { coordinates ->
                minimizedRegistry.deleteZoneRect = Rect(
                    coordinates.positionInParent(),
                    Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                )
            }
    ) {
        // Pulso de reposo, infinito y sutil — se congela apenas
        // `isHovering` pasa a true (ver `finalScale` más abajo) para no
        // competir contra el "salto" de escala de la confirmación.
        val infiniteTransition = rememberInfiniteTransition(label = "deleteZoneIdlePulse")
        val idlePulse by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(780, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "deleteZoneIdlePulseValue"
        )
        val hoverScale by animateFloatAsState(
            targetValue = if (isHovering) 1.22f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "deleteZoneHoverScale"
        )
        val finalScale = if (isHovering) hoverScale else hoverScale * idlePulse

        val deleteRed = Color(0xFFFF3B30)
        val ringColor by animateColorAsState(
            targetValue = if (isHovering) deleteRed else deleteRed.copy(alpha = 0.5f),
            animationSpec = tween(160),
            label = "deleteZoneRingColor"
        )
        val backgroundColor by animateColorAsState(
            targetValue = if (isHovering) deleteRed.copy(alpha = 0.28f) else SurfaceTintedElevated,
            animationSpec = tween(160),
            label = "deleteZoneBackgroundColor"
        )
        val shadowElevation by animateFloatAsState(
            targetValue = if (isHovering) 24f else 10f,
            animationSpec = tween(160),
            label = "deleteZoneShadowElevation"
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = finalScale
                    scaleY = finalScale
                }
                .size(FLOATING_WINDOW_DELETE_ZONE_SIZE)
                .shadow(
                    elevation = shadowElevation.dp,
                    shape = CircleShape,
                    ambientColor = deleteRed,
                    spotColor = deleteRed
                )
                .clip(CircleShape)
                .background(backgroundColor)
                .border(2.dp, ringColor, CircleShape)
                .semantics { contentDescription = "Soltar acá para eliminar el efecto" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete_effect_trash),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Ventana flotante de "Recolor": paleta de colores extraídos + Brillo/
 * Saturación/Opacidad + la rueda de color. A PEDIDO DEL USUARIO: ya NO
 * es un panel angosto pegado al borde izquierdo de la pantalla — ahora
 * es una ventana flotante DENTRO del canvas (ver [FloatingToolWindow]),
 * que se puede arrastrar desde su cabecera y redimensionar desde su
 * esquina inferior derecha, igual que un plugin de audio flotante.
 *
 * Misma lógica de siempre por debajo — nada de esto cambió, solo dónde
 * se dibuja: [liveBitmap] (chica, ~220px de lado) para que la extracción
 * de paleta sea instantánea y cada frame de arrastre de la rueda se
 * recoloree y suba a GL sin lag notable; [fullBitmap] (hasta 1024px) que
 * se decodifica aparte, en paralelo, y es la que de verdad se recolorea
 * y persiste a disco (ver EditorViewModel.commitLayerRecolor) 500ms
 * después del último cambio.
 */
private val RECOLOR_FLOATING_WINDOW_DEFAULT_SIZE = DpSize(240.dp, 460.dp)

@Composable
private fun RecolorFloatingWindow(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    initialOffset: Offset,
    onClose: () -> Unit,
    // Ver el comentario grande sobre `onInteracted` en [FloatingToolWindow]
    // y sobre `floatingWindowZOrderCounter` donde se instancian las tres
    // ventanas — esto solo se reenvía tal cual, sin lógica propia acá.
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    // Ver el comentario grande sobre `floatingWindowAreaSizePx` donde se
    // instancian las tres ventanas, y su uso dentro de [FloatingToolWindow]
    // — esto solo se reenvía tal cual, sin lógica propia acá.
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var fullBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var palette by remember(layer.id) { mutableStateOf<List<Int>>(emptyList()) }
    var selectedOriginal by remember(layer.id) { mutableStateOf<Int?>(null) }
    val remaps = remember(layer.id) { mutableStateMapOf<Int, Int>() }
    var wheelHue by remember(layer.id) { mutableStateOf(0f) }
    var wheelSat by remember(layer.id) { mutableStateOf(0f) }
    var wheelVal by remember(layer.id) { mutableStateOf(1f) }
    // "Opacidad" del panel: cuánto pisa el color recoloreado al color
    // original de la imagen (1 = recolor a pleno, 0 = imagen intacta) —
    // ver el parámetro `intensity` de ColorExtraction.recolor. Empieza en
    // 1 (comportamiento de siempre) para no sorprender con un resultado
    // "apagado" apenas se entra al panel.
    var recolorOpacity by remember(layer.id) { mutableStateOf(1f) }
    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }

    // A PEDIDO EXPLÍCITO DEL USUARIO — soporte de "Eliminar efecto"
    // (arrastrar el ícono ya minimizado a la zona de "Eliminar", ver
    // `onDeleteEffect` en [FloatingToolWindow]): el `Uri` (NO el
    // contenido) que la capa tenía ANTES de que esta ventana la
    // tocara, capturado UNA sola vez al entrar — `remember(layer.id)`
    // ejecuta este lambda una única vez, en la primerísima composición
    // de esta ventana para esta capa, así que congela el `sourceUri`
    // que había EN ESE MOMENTO, antes de cualquier commit de esta
    // sesión.
    //
    // BUG REAL corregido (reportado con captura: el lienzo quedó
    // COMPLETAMENTE VACÍO después de "Eliminar"): la primera versión de
    // esto volvía a DECODIFICAR de forma asíncrona (en un hilo de IO
    // aparte) el `sourceUri` que la capa tenía al abrir la ventana,
    // para guardar el Bitmap resultante y commitearlo de vuelta al
    // eliminar. Esa decodificación no tenía ninguna garantía de
    // terminar ANTES de que otro commit de esta misma capa (de esta
    // ventana o de OTRA abierta en paralelo sobre la misma capa)
    // sobreescribiera el archivo que estaba leyendo a mitad de
    // lectura — una carrera real de archivo. En la práctica, terminó
    // decodificando un archivo corrupto/a medio escribir y commiteando
    // ESO de vuelta, que es justamente lo que borró la imagen entera.
    //
    // La corrección: no decodificar NADA de nuevo — guardar solo el
    // `Uri` (un string, sin IO) y, al eliminar, pisar `sourceUri` de
    // vuelta a ese mismo Uri exacto vía `EditorViewModel.revertLayerToUri`
    // (ver su comentario grande, en el ViewModel, para la explicación
    // completa). Sin decodificación de por medio no hay ninguna carrera
    // posible.
    val originalSourceUriBeforeSession = remember(layer.id) { layer.sourceUri }

    // BUG REAL corregido acá, de raíz — los dos intentos anteriores
    // (animar el "salto" de blanco a color real, primero solo en la
    // apertura, después en cada re-selección) seguían mostrando el
    // valor FALSO en pantalla, aunque sea por una fracción de segundo o
    // suavizado — y ESO es lo que se percibe como bug, no la velocidad
    // de la transición. La única forma de que nunca se vea un valor
    // incorrecto es no dibujarlo nunca: los controles (sliders + rueda)
    // quedan invisibles (alpha 0, mismo alto reservado, sin saltos de
    // layout) hasta que `selectSwatch` los cargó con el color REAL por
    // primera vez para esta capa — recién ahí aparecen, ya con el valor
    // correcto puesto, y con un fundido corto para que no sea un pop
    // brusco de "nada" a "controles". Ninguna versión con datos falsos
    // llega a pintarse en pantalla, ni un frame.
    var hasRealPaletteData by remember(layer.id) { mutableStateOf(false) }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (hasRealPaletteData) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "recolorControlsAlpha"
    )

    fun selectSwatch(originalColor: Int) {
        selectedOriginal = originalColor
        val effective = remaps[originalColor] ?: originalColor
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(effective, hsv)
        wheelHue = hsv[0]
        wheelSat = hsv[1]
        wheelVal = hsv[2]
        hasRealPaletteData = true
    }

    LaunchedEffect(layer.id, layer.sourceUri) {
        // BUG REAL corregido acá: `remaps` vive en remember(layer.id) — NO
        // se reinicia cuando `layer.sourceUri` cambia. Y sí cambia: apenas
        // pasan los 500ms de debounce, commitLayerRecolor guarda el
        // resultado en un ARCHIVO NUEVO y actualiza sourceUri a esa nueva
        // ruta (ver comentario de commitLayerRecolor en EditorViewModel).
        // Ese archivo nuevo YA tiene el cambio anterior horneado en los
        // píxeles — pero la entrada vieja de `remaps` (color original de
        // ANTES → el color que se eligió) seguía viva en memoria. Si el
        // usuario volvía a tocar la rueda para elegir OTRO color, el mapa
        // de recolor terminaba con DOS entradas compitiendo por los mismos
        // píxeles: la vieja (que en muchos casos seguía siendo "válida"
        // porque su color de origen todavía estaba cerca del color actual)
        // y la nueva recién elegida — y la mezcla ponderada de recolor()
        // terminaba tirando el resultado de vuelta hacia el color viejo,
        // por más que se arrastrara la rueda a un tono distinto. Con una
        // imagen de un solo color plano esto era especialmente notorio:
        // la entrada vieja competía cabeza a cabeza contra la nueva en
        // CASI todos los píxeles a la vez, así que el cambio nuevo casi no
        // se notaba — "se queda pegado en el mismo verde".
        //
        // Ahora, cada vez que se carga una imagen nueva desde disco (osea,
        // cada vez que el commit anterior YA quedó guardado), se limpia
        // `remaps` entero: el color base que se acaba de cargar YA ES el
        // resultado final del cambio anterior, así que cualquier delta
        // viejo relativo a un color-de-origen que ya no existe en el
        // archivo debe descartarse, no acumularse para siempre.
        // BUG REAL #2 corregido acá: antes de limpiar el estado, guardamos
        // qué color se veía en pantalla para el swatch que el usuario
        // tenía elegido (su remap si lo tenía, si no el original). Este
        // LaunchedEffect no corre solo una vez al entrar al panel: corre
        // CADA VEZ que cambia `layer.sourceUri`, y sourceUri cambia solo
        // 500ms después de CADA pausa al arrastrar un slider o la rueda
        // (ver commitLayerRecolor). O sea que este bloque se re-ejecutaba
        // en medio de una sesión de edición normal, no solo al abrir el
        // panel.
        val previousEffectiveColor = selectedOriginal?.let { remaps[it] ?: it }

        remaps.clear()
        selectedOriginal = null

        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 220)
        }
        liveBitmap = small
        val extracted = small?.let { ColorExtraction.extractPalette(it) } ?: emptyList()
        palette = extracted
        // BUG REAL #2, la parte que de verdad se sentía como "se
        // restablece solo": acá SIEMPRE se auto-seleccionaba
        // `extracted.firstOrNull()`, sin importar qué color tenía
        // elegido el usuario. Como este efecto se repite en cada pausa
        // de edición (no solo al abrir el panel), si el usuario estaba
        // ajustando el 2do o 3er color de la paleta, la selección saltaba
        // de vuelta al primero cada medio segundo — el recolor SÍ se
        // aplicaba y se guardaba bien, pero el panel visualmente
        // "olvidaba" en qué color estabas parado, así que mover un
        // slider parecía no hacer nada o quedar limitado.
        //
        // Ahora, si había un color seleccionado antes, se busca en la
        // paleta nueva el más parecido (por distancia RGB) a como se
        // veía ese color en pantalla, y se re-selecciona ESE — la
        // selección "sigue" al mismo color a través de los recargues
        // automáticos. Solo si no había nada elegido todavía (primera
        // vez que se abre el panel para esta capa) se cae al
        // comportamiento original de elegir el más presente.
        val toReselect = previousEffectiveColor
            ?.let { target -> extracted.minByOrNull { candidate -> colorDistanceSquared(candidate, target) } }
            ?: extracted.firstOrNull()
        if (toReselect != null) {
            selectSwatch(toReselect)
        } else {
            // BUG REAL corregido acá: si `extracted` viene vacía (imagen
            // 100% transparente, o falló la decodificación — ambos casos
            // ya contemplados arriba con su propio manejo), `toReselect`
            // es null y `selectSwatch` nunca se llamaba. Como
            // `hasRealPaletteData` solo se ponía en `true` DENTRO de
            // `selectSwatch`, los sliders y la rueda (atados a
            // `controlsAlpha`) se quedaban en alpha 0 para siempre: ni un
            // control visible, ni ningún mensaje de qué pasó. No hay
            // color real que cargar, pero el panel igual tiene que
            // mostrarse — así que no queda ningún color seleccionado
            // (la fila de paleta cae en su estado "Sin colores") y los
            // sliders/rueda se muestran con su valor default (blanco a
            // pleno brillo), igual que se ven un instante antes de que
            // `selectSwatch` los actualice en el caso normal.
            selectedOriginal = null
            hasRealPaletteData = true
        }
        fullBitmap = withContext(Dispatchers.IO) {
            // El commit final (lo que se GUARDA de verdad como nuevo
            // sourceUri de la capa, ver commitLayerRecolor) siempre a
            // resolución completa — solo `liveBitmap` de arriba se achica,
            // y solo para que arrastrar el slider/rueda se sienta fluido
            // en tiempo real. Antes acá había un límite de 1024px que
            // terminaba "horneado" para siempre en la capa apenas se
            // tocaba cualquier color — la calidad perdida en Recolor no
            // era un efecto de la herramienta en sí, era este límite.
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = ImageDecoding.NO_LIMIT)
        }
    }

    fun applyLivePreviewAndScheduleCommit() {
        liveBitmap?.let { small ->
            val recoloredSmall = ColorExtraction.recolor(small, remaps.toMap(), intensity = recolorOpacity)
            viewModel.previewLayerRecolor(layer.id, recoloredSmall)
        }
        commitJob?.cancel()
        commitJob = coroutineScope.launch {
            delay(500)
            val source = fullBitmap ?: liveBitmap ?: return@launch
            val remapsSnapshot = remaps.toMap()
            val opacitySnapshot = recolorOpacity
            val recoloredFull = withContext(Dispatchers.Default) {
                ColorExtraction.recolor(source, remapsSnapshot, intensity = opacitySnapshot)
            }
            viewModel.commitLayerRecolor(layer.id, recoloredFull, source = "recolor")
        }
    }

    // Compartida por la rueda Y por los sliders de Brillo/Saturación: sea
    // cual sea el control que se mueva, todos terminan en el mismo lugar
    // — arman el color HSV completo, lo guardan como remap del color
    // seleccionado, y disparan la vista previa en vivo + el guardado con
    // debounce. Sin esto, cada control tendría que repetir esa misma
    // secuencia de 3 pasos por separado.
    fun applyCurrentWheelColor() {
        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(wheelHue, wheelSat, wheelVal))
        selectedOriginal?.let { remaps[it] = newColor }
        applyLivePreviewAndScheduleCommit()
    }

    // --- Ventana flotante, arrastrable y redimensionable ---
    // A PEDIDO DEL USUARIO: reemplaza el panel angosto y vertical pegado
    // al borde izquierdo por [FloatingToolWindow] — cabecera arrastrable
    // con el título "Recolor" (así que no hace falta repetirlo acá
    // adentro) + botón de cerrar, y una manija de redimensionar en la
    // esquina inferior derecha. El contenido mantiene scroll vertical
    // propio (heredado de FloatingToolWindow), así que con la ventana
    // achicada al mínimo (o con muchos colores en la paleta) nada queda
    // cortado — simplemente se puede deslizar en vez de que algo
    // desaparezca.
    FloatingToolWindow(
        title = "Recolor",
        // Mismo glifo (gota de pintura) que ya usa "Recolor" en
        // [EditImageColorMenu] — ver [ColorMenuRecolorIcon].
        titleIcon = { tint -> ColorMenuRecolorIcon(tint = tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = RECOLOR_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // A PEDIDO EXPLÍCITO DEL USUARIO: soltar el ícono minimizado
        // sobre la zona de "Eliminar" (ver [FloatingWindowDeleteDropZone])
        // borra el efecto entero, no solo cierra la ventana — vuelve la
        // capa a `originalSourceUriBeforeSession` (el Uri que tenía
        // ANTES de que esta ventana tocara nada, ver su comentario
        // grande de más arriba para el porqué de usar el Uri y no un
        // Bitmap decodificado) y RECIÉN AHÍ cierra, con `onClose()` tal
        // cual. `commitJob?.cancel()` primero: si había un commit con
        // debounce en camino (de un cambio de hace menos de 500ms),
        // tiene que descartarse — si llegara a correr DESPUÉS de este
        // reset, pisaría el original de vuelta con el remap viejo.
        onDeleteEffect = {
            commitJob?.cancel()
            viewModel.revertLayerToUri(layer.id, originalSourceUriBeforeSession)
            onClose()
        },
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
            // BUG REAL corregido acá — el mismo diagnóstico que ya se
            // había aplicado en [ColorBasicoFloatingWindow]/
            // [Basico3DFloatingWindow]/[EffectsPanel]/[DistortionPanel]
            // (ver el comentario grande justo después de
            // [FloatingToolWindow], más arriba), pero que en la primera
            // pasada por Recolor quedó aplicado A MEDIAS: acá SEGUÍA
            // habiendo un `CircularProgressIndicator` girando mientras
            // `isLoadingPalette` era `true`, que se reemplazaba de golpe
            // por la fila de colores apenas terminaba de cargar — la
            // MISMA clase de salto que ya se había eliminado en las otras
            // tres ventanas, nada más que acá sobrevivió escondido en la
            // paleta en vez de en los sliders. Encima, la primera
            // corrección le sumó un estado "atenuado/deshabilitado" a los
            // sliders y la rueda mientras tanto — dos señales de "cargando"
            // a la vez (el spinner arriba Y los controles grises abajo),
            // peor que antes.
            //
            // La corrección real, ahora sí completa: nada de spinner, nada
            // de atenuado. La fila de paleta reserva su alto fijo desde el
            // primer frame (mismo alto tenga 0 o 10 colores, ver `height`
            // acá abajo) y los sliders + la rueda están SIEMPRE visibles,
            // SIEMPRE habilitados, exactamente igual que en Color Básico
            // — la única diferencia real es que sus valores arrancan en el
            // default (blanco a pleno brillo) hasta que la paleta llega
            // (típicamente un instante después) y `selectSwatch` los
            // actualiza solo. Sin `if` que decida si el bloque grande
            // existe: existe siempre, con el mismo alto siempre.
            Text(
                "Color",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
                if (palette.isEmpty()) {
                    Text(
                        "Sin colores",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palette.forEach { originalColor ->
                            val effectiveColor = remaps[originalColor] ?: originalColor
                            val isSelected = selectedOriginal == originalColor
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(effectiveColor))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectSwatch(originalColor) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(4.dp))

            // BUG REAL corregido acá: todo este bloque (sliders + rueda)
            // se dibuja con `alpha = controlsAlpha`, que arranca en 0 y
            // recién sube a 1 cuando `selectSwatch` ya cargó el color
            // REAL por primera vez para esta capa (ver `hasRealPaletteData`
            // más arriba). El alto se sigue reservando igual esté
            // visible o no — solo cambia la opacidad, nunca el layout —
            // así que no hay salto de tamaño de ventana, y tampoco hay
            // ningún frame donde se vea Brillo 100%/Saturación 0% (el
            // valor de mentira): esos valores existen en memoria un
            // instante, pero nunca llegan a pintarse en pantalla.
            Column(modifier = Modifier.graphicsLayer(alpha = controlsAlpha)) {
            // Brillo y Saturación son la MISMA información que ya
            // controla la posición del dedo en la rueda (radio =
            // saturación, y el brillo pinta el anillo) — tenerlos
            // también como sliders no es redundante en un panel
            // profesional: permite un ajuste fino de precisión
            // (0.01 en vez de depender del pulso del dedo en una
            // pantalla chica) sin perder la rueda como forma rápida
            // de elegir el matiz. Los tres controles escriben al
            // mismo estado (wheelHue/wheelSat/wheelVal), así que
            // mover un slider también mueve el punto de la rueda, y
            // viceversa — quedan siempre sincronizados.
            LabeledSlider(
                label = "Brillo",
                value = wheelVal,
                range = 0f..1f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                // `displayScale` acá, escribir a mano "83" en un control interno
                // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                // `displayScale = 100f` hace que el campo de edición manual (y su
                // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                // y tipea (0-100, o -100..100), no en la escala cruda interna.
                displayScale = 100f
            ) { v ->
                wheelVal = v
                applyCurrentWheelColor()
            }
            LabeledSlider(
                label = "Saturación",
                value = wheelSat,
                range = 0f..1f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                // `displayScale` acá, escribir a mano "83" en un control interno
                // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                // `displayScale = 100f` hace que el campo de edición manual (y su
                // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                // y tipea (0-100, o -100..100), no en la escala cruda interna.
                displayScale = 100f
            ) { s ->
                wheelSat = s
                applyCurrentWheelColor()
            }
            // "Opacidad" en vez de un slider de "suavizado": es el
            // control que de verdad tiene sentido para el usuario acá
            // — cuánto pisa el color nuevo al original — en vez de
            // exponer un parámetro técnico interno (radio de mezcla)
            // que no se entiende sin leer el código. Ver el
            // parámetro `intensity` de ColorExtraction.recolor.
            LabeledSlider(
                label = "Opacidad",
                value = recolorOpacity,
                range = 0f..1f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                // `displayScale` acá, escribir a mano "83" en un control interno
                // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                // `displayScale = 100f` hace que el campo de edición manual (y su
                // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                // y tipea (0-100, o -100..100), no en la escala cruda interna.
                displayScale = 100f
            ) { o ->
                recolorOpacity = o
                applyLivePreviewAndScheduleCommit()
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rueda de color, centrada — su ancho fijo (168dp) entra
            // cómodo incluso con la ventana flotante en su tamaño
            // mínimo (ver `minSize` en FloatingToolWindow).
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ColorWheelPicker(
                    hue = wheelHue,
                    saturation = wheelSat,
                    brightness = wheelVal,
                    onColorChange = { h, s ->
                                wheelHue = h
                        wheelSat = s
                        applyCurrentWheelColor()
                    },
                    modifier = Modifier.size(168.dp)
                )
            }
            } // fin de la Column con controlsAlpha

            Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Ventana flotante de "Básico" (pestaña "Color", ARRIBA de "Recolor" en
 * [EditImageColorMenu]): los 5 ajustes de corrección de color global
 * del sujeto — Nitidez, Saturación, Brillo, Contraste, Tono — que antes
 * vivían adentro de "Efecto", como su propia categoría "Color" (ver
 * [EffectsCategoryColor], reutilizada tal cual acá abajo, sin tocar un
 * solo slider ni valor por defecto). A PEDIDO DEL USUARIO: esa
 * categoría se saca de "Efecto" y se muda a la pestaña "Color", porque
 * es un ajuste de COLOR, no un efecto — mismo criterio, mismo trato
 * visual y mismo mecanismo que [RecolorFloatingWindow]/
 * [Basico3DFloatingWindow]: ventana flotante DENTRO del canvas (ver
 * [FloatingToolWindow]), arrastrable desde su cabecera y redimensionable
 * desde su esquina inferior derecha.
 *
 * Importante — por qué NO se reinventa el guardado acá: "Color"
 * (sharpen/saturation/brightness/contrast/hue) es apenas una porción de
 * [com.yeivikas.olyzecs.engine.effects.ImageEffectsParams], un objeto
 * ÚNICO que agrupa TODOS los efectos de la capa (Fondo, Contorno,
 * Resplandor, Sombra, Reflejo, Presets, Distorsión incluidos) — el
 * mismo que ya arma/persiste [EffectsPanel] para la pestaña "Efecto".
 * Estos efectos NO quedan guardados como números editables en la capa:
 * cada commit los "hornea" directo sobre los píxeles y reemplaza
 * `layer.sourceUri` (igual que "Recolor"/"3D"). Por eso, en cada
 * apertura de ESTA ventana (igual que en cada apertura de "Efecto"), el
 * resto de las ~60 propiedades de `ImageEffectsParams` arranca en su
 * valor NEUTRO (los mismos valores por defecto que ya trae la propia
 * clase — 0 de intensidad, 1 de escala — ver su declaración): eso
 * significa "no agregar NADA nuevo de esas otras categorías en esta
 * sesión", no "borrar lo que ya estaba horneado en la imagen" — los
 * efectos ya aplicados en commits anteriores siguen ahí, en los
 * píxeles. Es EXACTAMENTE lo mismo que ya pasaba cuando "Color" vivía
 * adentro de "Efecto": ni un bit de este comportamiento cambia, solo
 * dónde se abre la ventana.
 */
private val COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE = DpSize(260.dp, 420.dp)

@Composable
private fun ColorBasicoFloatingWindow(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    initialOffset: Offset,
    onClose: () -> Unit,
    // Ver el comentario grande sobre `onInteracted` en [FloatingToolWindow]
    // y sobre `floatingWindowZOrderCounter` donde se instancian las tres
    // ventanas — esto solo se reenvía tal cual, sin lógica propia acá.
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    // Ver el comentario grande sobre `floatingWindowAreaSizePx` donde se
    // instancian las tres ventanas, y su uso dentro de [FloatingToolWindow]
    // — esto solo se reenvía tal cual, sin lógica propia acá.
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var fullBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }

    // Mismo `EffectsControlsState`/`EffectsCategoryColor` que usa
    // [EffectsPanel] para esta misma categoría — cada `remember` con la
    // misma key crea su propio objeto por sitio de llamada, así que
    // nunca comparten la instancia en memoria.
    val ctrl = rememberEffectsControlsState(layer.id)

    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var liveRenderJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var lastLiveRenderAtMs by remember(layer.id) { mutableStateOf(0L) }

    // A PEDIDO EXPLÍCITO DEL USUARIO — mismo mecanismo que
    // [RecolorFloatingWindow] (ver su comentario grande sobre
    // `originalSourceUriBeforeSession`, junto a su declaración, para el
    // porqué completo de guardar el Uri y no un Bitmap): el Uri que
    // tenía la capa ANTES de que esta ventana la tocara, capturado UNA
    // sola vez al entrar, sin IO, sin corrutina — nada que pueda entrar
    // en carrera con los commits de esta u otra ventana editando la
    // misma capa.
    val originalSourceUriBeforeSession = remember(layer.id) { layer.sourceUri }

    // A PEDIDO DEL USUARIO — MULTI-VENTANA: esta ventana puede convivir
    // en pantalla con "Recolor" (y con "3D"/"Efecto") editando la MISMA
    // capa al mismo tiempo, cada una escribiendo su resultado en
    // `layer.sourceUri` con debounce (ver commitLayerRecolor). Si esta
    // ventana decodificara su base UNA sola vez al entrar y se quedara
    // con esa copia fija toda la sesión, un commit hecho por OTRA
    // ventana mientras ésta sigue abierta quedaría pisado por el
    // próximo commit de ÉSTA (que partiría de una base vieja, sin el
    // cambio ajeno) — pérdida silenciosa de trabajo. Por eso, igual que
    // [RecolorFloatingWindow], la key incluye `layer.sourceUri`: cada vez
    // que CUALQUIER ventana (incluida esta) actualiza el archivo de la
    // capa, esta ventana vuelve a decodificar la base fresca y sigue
    // aplicando sus propios sliders (que no se resetean — viven en
    // `remember(layer.id)`, sin `sourceUri`) sobre el resultado más
    // reciente, en vez de sobre uno desactualizado.
    LaunchedEffect(layer.id, layer.sourceUri) {
        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 260)
        }
        liveBitmap = small
        fullBitmap = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = ImageDecoding.NO_LIMIT)
        }
    }

    // Ídem `currentParams()` de [EffectsPanel], pero solo con los 5
    // campos que esta ventana de verdad expone — el resto queda en su
    // valor por defecto (ver el comentario grande de más arriba: eso es
    // "no tocar esas otras categorías", no "borrarlas").
    fun currentParams() = com.yeivikas.olyzecs.engine.effects.ImageEffectsParams(
        sharpen = ctrl.sharpen,
        saturation = ctrl.saturation,
        brightness = ctrl.brightness,
        contrast = ctrl.contrast,
        hue = ctrl.hue
    )

    fun applyLivePreviewAndScheduleCommit() {
        val small = liveBitmap
        val liveParams = currentParams()
        val now = System.currentTimeMillis()
        if (small != null && now - lastLiveRenderAtMs >= 60L) {
            lastLiveRenderAtMs = now
            liveRenderJob?.cancel()
            liveRenderJob = coroutineScope.launch {
                val rendered = viewModel.applyImageEffects(small, liveParams)
                viewModel.previewLayerRecolor(layer.id, rendered)
            }
        }

        commitJob?.cancel()
        commitJob = coroutineScope.launch {
            delay(500)
            val source = fullBitmap ?: liveBitmap ?: return@launch
            val params = currentParams()
            val rendered = viewModel.applyImageEffects(source, params)
            viewModel.commitLayerRecolor(layer.id, rendered, source = "colorBasico")
        }
    }

    // --- Ventana flotante, arrastrable y redimensionable ---
    // Mismo patrón exacto que [RecolorFloatingWindow]/
    // [Basico3DFloatingWindow] — cabecera arrastrable con el título
    // "Color Básico" + botón de cerrar, y una manija de redimensionar en
    // la esquina inferior derecha.
    FloatingToolWindow(
        title = "Color Básico",
        // Mismo glifo (tres sliders) que ya usa "Básico" en
        // [EditImageColorMenu] — ver [ColorMenuBasicoIcon].
        titleIcon = { tint -> ColorMenuBasicoIcon(tint = tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // A PEDIDO EXPLÍCITO DEL USUARIO — mismo mecanismo que
        // [RecolorFloatingWindow]: soltar el ícono minimizado sobre la
        // zona de "Eliminar" vuelve la capa a `originalSourceUriBeforeSession`
        // (el Uri que tenía ANTES de que esta ventana tocara nada, sin
        // importar cuántos commits hizo esta sesión de Nitidez/
        // Saturación/Brillo/Contraste/Tono) y RECIÉN AHÍ cierra.
        // `commitJob`/`liveRenderJob` se cancelan primero por la misma
        // razón: un commit con debounce en camino de un cambio de hace
        // menos de 500ms no puede pisar el original de vuelta después.
        onDeleteEffect = {
            commitJob?.cancel()
            liveRenderJob?.cancel()
            viewModel.revertLayerToUri(layer.id, originalSourceUriBeforeSession)
            onClose()
        },
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
        // BUG REAL corregido acá — reportado con captura ("al expandirse
        // se ve una parte y luego el resto, como si tuviera lag"): antes
        // TODA esta ventana se quedaba atrás de `isLoading` (un spinner
        // de 120dp) hasta que `liveBitmap` terminaba de decodificarse en
        // segundo plano — aunque los 5 sliders de acá abajo NO necesitan
        // ese bitmap para nada, solo leen/escriben `ctrl` (en memoria,
        // disponible de una). Como la decodificación tarda apenas unos
        // milisegundos, lo que el usuario veía era la ventana abriendo
        // en su tamaño chico de spinner y, uno o dos frames después,
        // saltando de golpe a su alto real de sliders — un cambio de
        // tamaño en dos pasos que se lee como una animación entrecortada
        // o un lag de renderizado, nada profesional.
        //
        // `liveBitmap`/`fullBitmap` siguen cargándose exactamente igual
        // en el `LaunchedEffect` de arriba — lo único que cambia es que
        // los sliders ya no esperan a que terminen: `applyLivePreviewAndScheduleCommit()`
        // ya comprobaba `if (small != null ...)` antes de esto, así que
        // si el usuario mueve un slider en el primerísimo instante (antes
        // de que la decodificación de ~milisegundos termine) simplemente
        // no hay vista previa en vivo para ESE tick puntual — el commit
        // final 500ms después sigue esperando a `fullBitmap`/`liveBitmap`
        // como siempre. Ningún dato se pierde, la ventana solo deja de
        // fingir una carga que en los hechos no bloquea nada visible.
        EffectsCategoryColor(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
    }
}


/**
 * forma o texto ya rasterizado por igual, no hace falta que sea un
 * "sticker". A PEDIDO DEL USUARIO: mismo tratamiento que "Recolor" (ver
 * [RecolorFloatingWindow], acá arriba) — ya NO es un panel fijo pegado
 * abajo de la pantalla; ahora es una ventana flotante DENTRO del canvas
 * (ver [FloatingToolWindow]), arrastrable desde su cabecera y
 * redimensionable desde su esquina inferior derecha, igual que un
 * plugin de audio flotante. Mismo patrón de vista previa en vivo +
 * guardado con debounce de 500ms que [RecolorFloatingWindow] usa para
 * "Recolor": cada movimiento de slider recalcula sobre una copia chica
 * (liviano, sin lag) y sube esa vista previa; medio segundo después del
 * último movimiento, se recalcula sobre la copia grande y se persiste
 * como archivo nuevo (reusa EditorViewModel.previewLayerRecolor/
 * commitLayerRecolor tal cual — son genéricos, no hacen nada específico
 * de "recolorear").
 *
 * Nota honesta: como el cuerpo extruido puede sobresalir del cuadro
 * original de la imagen (por la rotación y la profundidad), el bitmap
 * resultante es más grande que el original con un margen alrededor —
 * evita que el efecto se vea recortado, a costa de que el tamaño en
 * píxeles de la capa cambie al aplicar el efecto.
 */
private val BASICO_FLOATING_WINDOW_DEFAULT_SIZE = DpSize(260.dp, 480.dp)

@Composable
private fun Basico3DFloatingWindow(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    extrude3DBridge: Extrude3DGestureBridge,
    initialOffset: Offset,
    onClose: () -> Unit,
    // Ver el comentario grande sobre `onInteracted` en [FloatingToolWindow]
    // y sobre `floatingWindowZOrderCounter` donde se instancian las tres
    // ventanas — esto solo se reenvía tal cual, sin lógica propia acá.
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    // Ver el comentario grande sobre `floatingWindowAreaSizePx` donde se
    // instancian las tres ventanas, y su uso dentro de [FloatingToolWindow]
    // — esto solo se reenvía tal cual, sin lógica propia acá.
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var fullBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }

    var rotationX by remember(layer.id) { mutableStateOf(0f) }
    var rotationY by remember(layer.id) { mutableStateOf(0f) }
    var rotationZ by remember(layer.id) { mutableStateOf(0f) }
    var depth by remember(layer.id) { mutableStateOf(0.35f) }
    var bevel by remember(layer.id) { mutableStateOf(0.5f) }
    var materialOpacity by remember(layer.id) { mutableStateOf(1f) }
    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    // Throttle de la vista previa en vivo — ver el porqué completo en
    // el comentario de applyLivePreviewAndScheduleCommit más abajo.
    var liveRenderJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var lastLiveRenderAtMs by remember(layer.id) { mutableStateOf(0L) }

    // A PEDIDO EXPLÍCITO DEL USUARIO — mismo mecanismo que
    // [RecolorFloatingWindow]/[ColorBasicoFloatingWindow] (ver el
    // comentario grande sobre `originalSourceUriBeforeSession`, junto a
    // su declaración en [RecolorFloatingWindow], para el porqué
    // completo de usar el Uri y no un Bitmap decodificado): el Uri que
    // tenía la capa ANTES de que esta ventana la tocara (plana, sin
    // extruir), capturado UNA sola vez al entrar, sin IO, sin
    // corrutina. Acá importa todavía más que en las otras dos: el
    // estado "por defecto" de los sliders (depth=0.35, bevel=0.5) YA es
    // un efecto 3D visible, no un neutro — así que "Eliminar" no puede
    // simplemente resetear los sliders a esos valores de apertura, que
    // seguirían extruyendo la imagen. Tiene que volver directo a este
    // Uri plano, sin pasar de nuevo por Extrude3D.render.
    val originalSourceUriBeforeSession = remember(layer.id) { layer.sourceUri }

    // BUG REAL corregido acá: este efecto estaba keyeado en
    // (layer.id, layer.sourceUri), copiando el patrón de
    // LayerColorEditPanel — pero ahí ese patrón funciona porque
    // `remaps` guarda un DELTA de color que se limpia en cada recarga
    // (el remap ya quedó horneado en el archivo nuevo, así que
    // arrancar de cero es correcto). Acá `currentParams()` no es un
    // delta: son ángulos y profundidad ABSOLUTOS que Extrude3D.render
    // aplica sobre la imagen que se le pase como si fuera plana.
    //
    // commitLayerRecolor (reusado tal cual para el 3D) escribe el
    // RESULTADO YA EXTRUIDO a un archivo nuevo y actualiza
    // `layer.sourceUri` para apuntar ahí, 500ms después de cada pausa
    // al mover un slider. Con el key viejo, ese cambio de sourceUri
    // volvía a disparar este LaunchedEffect, que recargaba
    // `liveBitmap`/`fullBitmap` desde ese archivo YA EXTRUIDO — o sea,
    // la imagen "plana" que Extrude3D.render usaba de ahí en más ya
    // no era plana, era el cuerpo 3D anterior aplastado a 2D otra vez.
    // El siguiente movimiento de slider extruía ESE resultado de
    // vuelta (re-extruyendo un extruido), y como cada pausa dispara
    // un commit nuevo, mover varios sliders con pausas entre medio
    // (uso normal) iba acumulando esa distorsión — de ahí que la
    // figura se achicara/corriera cada vez más con el uso.
    //
    // Ahora el efecto solo depende de `layer.id`: la imagen base se
    // decodifica UNA vez al entrar al panel para esta capa (siempre
    // la plana original, o la que estaba guardada al abrir), y
    // `currentParams()` se sigue aplicando sobre esa MISMA base fija
    // durante toda la sesión de edición, sin importar cuántas veces
    // sourceUri cambie por los commits de fondo. Cambiar de capa (o
    // volver a entrar más tarde) sí recarga desde el sourceUri vigente
    // en ese momento, que es el comportamiento esperado.
    LaunchedEffect(layer.id) {
        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 260)
        }
        liveBitmap = small
        // Resolución completa para el commit final (lo que se GUARDA de
        // verdad en la capa) — solo `liveBitmap` de arriba se achica, y
        // solo para que arrastrar el slider se sienta fluido en tiempo
        // real. Un límite acá capaba para siempre la calidad de la capa
        // apenas se aplicaba esta herramienta.
        fullBitmap = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = ImageDecoding.NO_LIMIT)
        }
    }

    fun currentParams() = Extrude3D.Params(
        rotationXDeg = rotationX,
        rotationYDeg = rotationY,
        rotationZDeg = rotationZ,
        depth = depth,
        bevel = bevel,
        opacity = materialOpacity
    )

    fun applyLivePreviewAndScheduleCommit() {
        // BUG REAL corregido acá: "se hace ruido/se ve entrecortado
        // mientras giro (sliders o dedo), y a los pocos milisegundos
        // de soltar se ve perfecto". Antes, esta función llamaba a
        // Extrude3D.render() (armar la malla + rasterizar con
        // z-buffer + luz Phong por píxel + supersample 2x) DIRECTO
        // acá, síncrono, en el hilo de UI — y esto se dispara en
        // CADA evento: cada tick de slider, o con el gesto orbital
        // del canvas (ver Extrude3DGestureBridge), cada movimiento
        // del dedo, que son decenas de eventos por segundo. Ese
        // trabajo de CPU pesado bloqueaba una y otra vez el mismo
        // hilo que procesa los toques y recompone la UI: los eventos
        // se empezaban a amontonar y procesar en ráfagas en vez de
        // fluido, y como cada ráfaga termina subiendo una textura a
        // mitad de la siguiente, se veía "ruidoso"/tembloroso
        // mientras se arrastraba. Apenas se soltaba el dedo/slider,
        // dejaban de llegar eventos nuevos, el hilo se ponía al día,
        // y como el commit final (bloque de abajo, con debounce de
        // 500ms) YA corría en Dispatchers.Default, ese sí se veía
        // limpio — de ahí la sensación de "se restaura la calidad
        // sola" a los pocos milisegundos.
        //
        // Ahora la vista previa en vivo también corre en
        // Dispatchers.Default (nunca bloquea el hilo de gestos/UI) y
        // se acota a como mucho ~16 renders por segundo (throttle de
        // 60ms) — de sobra para que el ojo lo vea fluido, pero sin
        // intentar renderizar CADA evento crudo. Un pedido nuevo
        // mientras uno anterior sigue en camino cancela el anterior
        // (liveRenderJob?.cancel()), así que nunca hay dos vistas
        // previas viejas compitiendo por subirse fuera de orden.
        val small = liveBitmap
        val liveParams = currentParams()
        val now = System.currentTimeMillis()
        if (small != null && now - lastLiveRenderAtMs >= 60L) {
            lastLiveRenderAtMs = now
            liveRenderJob?.cancel()
            liveRenderJob = coroutineScope.launch {
                // highQuality = false acá a propósito: este es el
                // render "en vivo" de cada tick de slider/gesto (ver
                // nota de arriba), pensado para ser barato y fluido.
                // El commit de abajo sí usa el perfil de calidad final
                // (bisel más redondeado, contorno más fiel), que es lo
                // que realmente queda guardado/exportado.
                //
                // FASE B: ya no se llama a Extrude3D.render() directo
                // acá — pasa por viewModel.renderExtrude3D (mismo
                // Dispatchers.Default de siempre, ahora encapsulado
                // adentro del wrapper del ViewModel en vez de en la UI).
                val rendered = viewModel.renderExtrude3D(small, liveParams, highQuality = false)
                viewModel.previewLayerRecolor(layer.id, rendered)
            }
        }

        commitJob?.cancel()
        commitJob = coroutineScope.launch {
            delay(500)
            val source = fullBitmap ?: liveBitmap ?: return@launch
            val params = currentParams()
            val rendered = viewModel.renderExtrude3D(source, params)
            viewModel.commitLayerRecolor(layer.id, rendered, source = "3d")
        }
    }

    // Registro en el puente hacia el gesto del canvas (ver
    // [Extrude3DGestureBridge]): mientras esta pestaña está en
    // pantalla para esta capa, el canvas de arriba deja de dibujar el
    // marco/manijas normales y en cambio manda acá los grados de
    // arrastre/pellizco — acá se traducen a rotaciones absolutas
    // (mismos campos que ya mueven los sliders, así que sliders y
    // dedo quedan siempre sincronizados) y se dispara la MISMA vista
    // previa en vivo + guardado con debounce de cualquier otro
    // cambio. `keys(layer.id)` en el DisposableEffect: si se cambia
    // de capa sin salir de la pestaña "3D", primero se da de baja el
    // registro viejo (onDispose) y se crea uno nuevo ya atado a la
    // capa correcta — así el gesto nunca termina rotando por error la
    // capa anterior.
    DisposableEffect(layer.id) {
        extrude3DBridge.active = true
        extrude3DBridge.onOrbitDrag = { dxDeg, dyDeg ->
            // Izquierda/derecha del dedo → Rotación Y (mismo eje que
            // el slider "Rotación Y"); arriba/abajo del dedo →
            // Rotación X. El signo de X va invertido (se resta el
            // delta vertical) para que arrastrar HACIA ARRIBA incline
            // el cuerpo como si se lo estuviera mirando desde abajo
            // subiendo la vista — la convención habitual en apps de
            // modelado 3D con un dedo (arrastrar arriba = la cámara
            // "sube" alrededor del objeto).
            rotationY = (rotationY + dxDeg).coerceIn(-180f, 180f)
            rotationX = (rotationX - dyDeg).coerceIn(-180f, 180f)
            applyLivePreviewAndScheduleCommit()
        }
        extrude3DBridge.onTwistDrag = { dzDeg ->
            rotationZ = (rotationZ + dzDeg).coerceIn(-180f, 180f)
            applyLivePreviewAndScheduleCommit()
        }
        onDispose {
            extrude3DBridge.active = false
            extrude3DBridge.onOrbitDrag = null
            extrude3DBridge.onTwistDrag = null
        }
    }

    // --- Ventana flotante, arrastrable y redimensionable ---
    // A PEDIDO DEL USUARIO: mismo patrón exacto que [RecolorFloatingWindow]
    // — cabecera arrastrable con el título "Básico" + botón de cerrar, y
    // una manija de redimensionar en la esquina inferior derecha. El
    // contenido mantiene scroll vertical propio (heredado de
    // [FloatingToolWindow]), así que con la ventana achicada al mínimo
    // nada queda cortado — simplemente se puede deslizar.
    FloatingToolWindow(
        title = "3D Básico",
        // Mismo glifo (cubo) que ya usa "Básico" en [EditImage3DMenu] —
        // ver [Menu3DBasicCubeIcon].
        titleIcon = { tint -> Menu3DBasicCubeIcon(tint = tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // A PEDIDO EXPLÍCITO DEL USUARIO — mismo mecanismo que
        // [RecolorFloatingWindow]/[ColorBasicoFloatingWindow], con una
        // diferencia importante (ver el comentario grande sobre
        // `originalSourceUriBeforeSession` más arriba en esta función):
        // acá NO alcanza con resetear rotationX/Y/Z, depth, bevel y
        // materialOpacity a sus valores de apertura, porque esos valores
        // de apertura YA extruyen la imagen — "Eliminar" tiene que
        // volver directo al Uri plano original, sin pasar de nuevo por
        // Extrude3D.render.
        onDeleteEffect = {
            commitJob?.cancel()
            liveRenderJob?.cancel()
            viewModel.revertLayerToUri(layer.id, originalSourceUriBeforeSession)
            onClose()
        },
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
        // BUG REAL corregido acá — mismo caso que [ColorBasicoFloatingWindow]
        // (ver el comentario grande de acá arriba, en esa función, para
        // el detalle completo): estos 6 sliders son ángulos/porcentajes
        // en memoria (`rotationX`/`rotationY`/.../`materialOpacity`), no
        // dependen del bitmap para nada — el spinner de `isLoading` solo
        // agregaba un salto de tamaño visible (spinner chico → panel
        // real) apenas unos milisegundos después de abrir, que es
        // exactamente el "se expande por partes" reportado. `liveBitmap`/
        // `fullBitmap` se siguen cargando igual en el `LaunchedEffect` de
        // arriba; `applyLivePreviewAndScheduleCommit()` ya los trata como
        // opcionales (`liveBitmap` puede ser null en el primerísimo tick).
                LabeledSlider(
                    label = "Rotación X (arriba/abajo)",
                    value = rotationX,
                    range = -180f..180f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { rotationX = it; applyLivePreviewAndScheduleCommit() }

                LabeledSlider(
                    label = "Rotación Y (izquierda/derecha)",
                    value = rotationY,
                    range = -180f..180f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { rotationY = it; applyLivePreviewAndScheduleCommit() }

                LabeledSlider(
                    label = "Rotación Z (giro plano)",
                    value = rotationZ,
                    range = -180f..180f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { rotationZ = it; applyLivePreviewAndScheduleCommit() }

                LabeledSlider(
                    label = "Profundidad",
                    value = depth,
                    range = 0.05f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { depth = it; applyLivePreviewAndScheduleCommit() }

                LabeledSlider(
                    label = "Bisel (borde redondeado)",
                    value = bevel,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { bevel = it; applyLivePreviewAndScheduleCommit() }

                LabeledSlider(
                    label = "Opacidad del material",
                    value = materialOpacity,
                    range = 0.2f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { materialOpacity = it; applyLivePreviewAndScheduleCommit() }
    }
}

/**
 * Ventana flotante dedicada de "Contorno" — A PEDIDO EXPLÍCITO DEL
 * USUARIO: "Contorno" y "Resplandor" (ver [ResplandorFloatingWindow])
 * pasan a tener cada una su PROPIA ventana, con el mismo patrón "plugin
 * de audio flotante" que ya usan [RecolorFloatingWindow]/
 * [ColorBasicoFloatingWindow]/[Basico3DFloatingWindow] — arrastrable,
 * redimensionable, minimizable, con su propio zIndex. ACLARACIÓN
 * EXPLÍCITA DEL USUARIO, para no repetir el error de "Recolor"/"Básico"
 * con la pestaña "Color": esto NO saca "Contorno" del menú desplegable
 * "Efecto" de la barra superior — sigue siendo un ítem de ESE menú (ver
 * [EditImageEffectsMenu]), tal cual "Recolor"/"Básico" lo son del menú
 * "Color". Tocarlo simplemente dispara ESTA ventana en vez de la
 * compartida (que, a esta altura, ya no cubre ninguna categoría
 * accesible desde este menú).
 *
 * BUG REAL corregido acá (reportado con captura: la ventana abría pero
 * se veía VACÍA, solo la cabecera) — la primera versión envolvía
 * [EffectsPanel] entero como contenido, y EffectsPanel arma por dentro
 * su propia columna con `Modifier.weight(1f)`. Eso funciona en
 * [LayerColorEditPanel] (la ventana compartida) porque ahí el padre es
 * un `Column(Modifier.fillMaxSize())` liso, de altura ACOTADA. Pero acá
 * el slot `content` de [FloatingToolWindow] YA es, él mismo, una columna
 * con scroll — altura NO acotada a propósito. `weight()` sin una altura
 * acotada de la que tomar parte se resuelve en CERO: la ventana "vacía".
 *
 * La corrección real, siguiendo EXACTAMENTE el mismo patrón que
 * [ColorBasicoFloatingWindow] (que nunca tuvo este problema): en vez de
 * envolver el panel completo, esta ventana llama DIRECTO al composable
 * hoja de la categoría ([EffectsCategoryContorno]) como contenido — sin
 * ninguna columna ni `weight()` de por medio, tal cual
 * [ColorBasicoFloatingWindow] llama a `EffectsCategoryColor` — y trae su
 * PROPIO `commitJob`/`liveRenderJob` locales, igual que las otras tres
 * ventanas ya hacían. La única diferencia real con
 * [ColorBasicoFloatingWindow] es que acá el commit renderiza el stack
 * COMPLETO de "Efecto" (`buildFullEffectsParams`) y no solo 5 campos —
 * necesario porque "Contorno" comparte el mismo `ctrl`/`liveBitmap`/
 * `fullBitmap` "de base congelada" que "Resplandor" y la ventana
 * compartida (ver el comentario grande en la firma de [EffectsPanel]):
 * si acá se aplicara solo el contorno, el próximo commit apagaría
 * cualquier resplandor/sombra ya puesto por las otras dos.
 */
@Composable
private fun ContornoFloatingWindow(
    layer: Layer,
    viewModel: EditorViewModel,
    ctrl: EffectsControlsState,
    liveBitmap: Bitmap?,
    fullBitmap: Bitmap?,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var liveRenderJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var lastLiveRenderAtMs by remember(layer.id) { mutableStateOf(0L) }

    fun applyLivePreviewAndScheduleCommit() {
        val small = liveBitmap
        val liveParams = buildFullEffectsParams(ctrl)
        val now = System.currentTimeMillis()
        if (small != null && now - lastLiveRenderAtMs >= 60L) {
            lastLiveRenderAtMs = now
            liveRenderJob?.cancel()
            liveRenderJob = coroutineScope.launch {
                val rendered = viewModel.applyImageEffects(small, liveParams)
                viewModel.previewLayerRecolor(layer.id, rendered)
            }
        }

        commitJob?.cancel()
        commitJob = coroutineScope.launch {
            delay(500)
            val source = fullBitmap ?: liveBitmap ?: return@launch
            val params = buildFullEffectsParams(ctrl)
            val rendered = viewModel.applyImageEffects(source, params)
            viewModel.commitLayerRecolor(layer.id, rendered, source = "contorno")
        }
    }

    FloatingToolWindow(
        title = "Contorno",
        // Mismo glifo que ya usa "Contorno" en EditImageEffectsMenu —
        // ver [EffectMenuOutlineIcon].
        titleIcon = { tint -> EffectMenuOutlineIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // BUG REAL corregido acá (reportado con captura: arrastrar el
        // ícono minimizado hasta la zona de "Eliminar" y soltar no hacía
        // nada) — a esta ventana y a [ResplandorFloatingWindow] les
        // faltaba directamente el parámetro `onDeleteEffect`; sin él,
        // [FloatingToolWindow] igual muestra el pulso de confirmación al
        // soltar (por eso SE VEÍA como si funcionara) pero
        // `onDeleteEffect?.invoke()` no tiene nada que invocar.
        //
        // El mecanismo de [ColorBasicoFloatingWindow] (volver la CAPA
        // ENTERA a `originalSourceUriBeforeSession`) no sirve tal cual
        // acá: esta ventana comparte un solo `ctrl`/stack de efectos con
        // [ResplandorFloatingWindow]/[SombraFloatingWindow]/
        // [ReflejoFloatingWindow] y la ventana compartida (que a esta
        // altura ya no cubre ninguna categoría accesible desde el menú
        // "Efecto", ver el comentario grande en la firma de
        // [EffectsPanel]) — revertir TODA la capa borraría también
        // cualquier resplandor/sombra ya aplicado, no solo el contorno.
        // En vez de eso: se resetean a su valor por defecto SOLO los
        // campos de "Contorno" dentro del `ctrl` compartido, y se
        // vuelve a renderizar+guardar con el stack COMPLETO — así el
        // contorno desaparece del archivo persistido sin tocar nada más.
        //
        // `viewModel.viewModelScope`, NO el `coroutineScope` de acá
        // arriba, a propósito: ese muere apenas `onClose()` saca esta
        // ventana de composición, y `applyImageEffects` es `suspend` —
        // lanzado en el scope local, el commit nunca llegaría a
        // ejecutarse porque la corrutina se cancela a mitad de camino.
        onDeleteEffect = {
            commitJob?.cancel()
            liveRenderJob?.cancel()
            ctrl.outlineIntensity = 0f
            ctrl.outlineColor = android.graphics.Color.WHITE
            ctrl.outlineColor2 = android.graphics.Color.WHITE
            ctrl.outlineGradientEnabled = false
            ctrl.outlineFeather = 0f
            ctrl.outlinePosition = com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.OUTSIDE
            viewModel.viewModelScope.launch {
                val source = fullBitmap ?: liveBitmap ?: return@launch
                val rendered = viewModel.applyImageEffects(source, buildFullEffectsParams(ctrl))
                viewModel.commitLayerRecolor(layer.id, rendered, source = "contorno")
            }
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        // `EffectsCategoryContorno` es el mismo composable hoja que
        // usaba EffectsPanel para esta categoría — llamado DIRECTO como
        // contenido, sin ninguna columna ni `weight()` de por medio,
        // igual que [ColorBasicoFloatingWindow] hace con
        // `EffectsCategoryColor`. Ver el KDoc de esta función para el
        // porqué completo.
        EffectsCategoryContorno(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
    }
}

/**
 * Ventana flotante dedicada de "Resplandor" — hermana exacta de
 * [ContornoFloatingWindow] de acá arriba, mismo patrón y mismas
 * aclaraciones (ver su KDoc para el detalle completo). Único cambio
 * real: usa [EffectsCategoryResplandor] y su propio glifo
 * ([EffectMenuGlowIcon]).
 */
@Composable
private fun ResplandorFloatingWindow(
    layer: Layer,
    viewModel: EditorViewModel,
    ctrl: EffectsControlsState,
    liveBitmap: Bitmap?,
    fullBitmap: Bitmap?,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var liveRenderJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var lastLiveRenderAtMs by remember(layer.id) { mutableStateOf(0L) }

    fun applyLivePreviewAndScheduleCommit() {
        val small = liveBitmap
        val liveParams = buildFullEffectsParams(ctrl)
        val now = System.currentTimeMillis()
        if (small != null && now - lastLiveRenderAtMs >= 60L) {
            lastLiveRenderAtMs = now
            liveRenderJob?.cancel()
            liveRenderJob = coroutineScope.launch {
                val rendered = viewModel.applyImageEffects(small, liveParams)
                viewModel.previewLayerRecolor(layer.id, rendered)
            }
        }

        commitJob?.cancel()
        commitJob = coroutineScope.launch {
            delay(500)
            val source = fullBitmap ?: liveBitmap ?: return@launch
            val params = buildFullEffectsParams(ctrl)
            val rendered = viewModel.applyImageEffects(source, params)
            viewModel.commitLayerRecolor(layer.id, rendered, source = "resplandor")
        }
    }

    FloatingToolWindow(
        title = "Resplandor",
        titleIcon = { tint -> EffectMenuGlowIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // Ver el comentario grande sobre `onDeleteEffect` en
        // [ContornoFloatingWindow] — mismo bug, misma corrección, acá
        // con los campos de "Resplandor" en vez de "Contorno".
        onDeleteEffect = {
            commitJob?.cancel()
            liveRenderJob?.cancel()
            ctrl.glowIntensity = 0f
            ctrl.glowBlur = 0.5f
            ctrl.glowColor = android.graphics.Color.WHITE
            ctrl.glowColor2 = android.graphics.Color.WHITE
            ctrl.glowGradientEnabled = false
            ctrl.glowBlendMode = com.yeivikas.olyzecs.engine.effects.GlowBlendMode.NORMAL
            ctrl.glowSpread = 0f
            ctrl.glowDistance = 0f
            ctrl.glowAngle = 135f
            viewModel.viewModelScope.launch {
                val source = fullBitmap ?: liveBitmap ?: return@launch
                val rendered = viewModel.applyImageEffects(source, buildFullEffectsParams(ctrl))
                viewModel.commitLayerRecolor(layer.id, rendered, source = "resplandor")
            }
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        EffectsCategoryResplandor(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
    }
}

/**
 * Ventana flotante dedicada de "Sombra" — hermana de [ContornoFloatingWindow]/
 * [ResplandorFloatingWindow] de acá arriba, MISMO patrón de fondo (ver su
 * KDoc para el detalle completo de por qué existe cada pieza). Única
 * diferencia estructural real: "Sombra" agrupa 3 variantes (Sombra /
 * Sombra relleno / Sombra contacto — antes 3 chips sueltos, después 3
 * sub-pestañas dentro de la ventana compartida, ver el comentario grande
 * en [EffectsPanel]) — así que esta ventana trae su PROPIO segundo nivel
 * de sub-pestañas adentro (`sombraSubCategories`/`selectedSombraSub`,
 * antes vivían en `EffectsPanel`, ver ahí por qué se sacaron de esa
 * función), en vez de una sola categoría hoja como Contorno/Resplandor.
 */
@Composable
private fun SombraFloatingWindow(
    layer: Layer,
    viewModel: EditorViewModel,
    ctrl: EffectsControlsState,
    liveBitmap: Bitmap?,
    fullBitmap: Bitmap?,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var liveRenderJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var lastLiveRenderAtMs by remember(layer.id) { mutableStateOf(0L) }
    val sombraSubCategories = remember { listOf("Sombra", "Sombra relleno", "Sombra contacto") }
    var selectedSombraSub by remember(layer.id) { mutableStateOf(0) }

    fun applyLivePreviewAndScheduleCommit() {
        val small = liveBitmap
        val liveParams = buildFullEffectsParams(ctrl)
        val now = System.currentTimeMillis()
        if (small != null && now - lastLiveRenderAtMs >= 60L) {
            lastLiveRenderAtMs = now
            liveRenderJob?.cancel()
            liveRenderJob = coroutineScope.launch {
                val rendered = viewModel.applyImageEffects(small, liveParams)
                viewModel.previewLayerRecolor(layer.id, rendered)
            }
        }

        commitJob?.cancel()
        commitJob = coroutineScope.launch {
            delay(500)
            val source = fullBitmap ?: liveBitmap ?: return@launch
            val params = buildFullEffectsParams(ctrl)
            val rendered = viewModel.applyImageEffects(source, params)
            viewModel.commitLayerRecolor(layer.id, rendered, source = "sombra")
        }
    }

    FloatingToolWindow(
        title = "Sombra",
        titleIcon = { tint -> EffectMenuShadowIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // Ver el comentario grande sobre `onDeleteEffect` en
        // [ContornoFloatingWindow] — mismo bug, misma corrección. Acá con
        // una diferencia real respecto a Contorno/Resplandor: "Sombra"
        // agrupa TRES variantes (Sombra / Sombra relleno / Sombra
        // contacto), así que "Eliminar" resetea los campos de las TRES —
        // no solo la sub-pestaña que estuviera visible en ese momento —
        // porque el ícono que se arrastra a la papelera representa la
        // categoría "Sombra" completa (así la ve/la eligió el usuario
        // desde `EditImageEffectsMenu`), no una sola de sus variantes.
        //
        // EXCEPCIÓN DELIBERADA: `groundWallBreak` NO se resetea acá, a
        // pesar de pertenecer a `EffectsCategorySombra`, porque ese mismo
        // campo (mismo nombre, mismo estado subyacente en
        // `EffectsControlsState`) también lo usa `EffectsCategoryReflejo`
        // — resetearlo desde acá cambiaría en silencio algo de la ventana
        // de Reflejo sin que el usuario lo haya tocado ni pedido. Si algún
        // día se quiere borrar del todo, que sea una decisión explícita
        // del usuario sobre CUÁL de las dos ventanas, no un efecto
        // colateral de la otra.
        onDeleteEffect = {
            commitJob?.cancel()
            liveRenderJob?.cancel()
            // Sombra (base)
            ctrl.shadowIntensity = 0f
            ctrl.shadowBlur = 0.5f
            ctrl.shadowSpread = 0f
            ctrl.shadowScale = 1f
            ctrl.shadowNoise = 0f
            ctrl.shadowDistance = 0.35f
            ctrl.shadowAngle = 135f
            ctrl.shadowColor = android.graphics.Color.BLACK
            ctrl.shadowSkew = 0f
            ctrl.shadowPerspectiveAmount = 0f
            ctrl.shadowFadeByDistance = 0f
            ctrl.shadowOpacityCurve = 0.5f
            ctrl.shadowBlendMultiply = true
            ctrl.shadowContactHardening = 0f
            ctrl.linkShadowToGlobalLight = false
            ctrl.linkShadowColorToGlobalLight = false
            // Sombra relleno
            ctrl.fillShadowIntensity = 0f
            ctrl.fillShadowBlur = 0.7f
            ctrl.fillShadowDistance = 0.2f
            ctrl.fillShadowAngle = 315f
            ctrl.fillShadowColor = android.graphics.Color.rgb(30, 34, 48)
            ctrl.fillShadowScale = 1f
            ctrl.linkFillShadowToGlobalLight = false
            // Sombra contacto
            ctrl.contactShadowIntensity = 0f
            ctrl.contactShadowSize = 0.5f
            ctrl.contactShadowBlur = 0.4f
            ctrl.contactShadowColor = android.graphics.Color.BLACK
            ctrl.contactShadowFalloff = 0.5f
            ctrl.contactShadowNoise = 0f
            ctrl.groundOcclusionIntensity = 0f
            viewModel.viewModelScope.launch {
                val source = fullBitmap ?: liveBitmap ?: return@launch
                val rendered = viewModel.applyImageEffects(source, buildFullEffectsParams(ctrl))
                viewModel.commitLayerRecolor(layer.id, rendered, source = "sombra")
            }
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EffectsCategoryTabs(
                categories = sombraSubCategories,
                selected = selectedSombraSub,
                onSelected = { selectedSombraSub = it }
            )
            when (selectedSombraSub) {
                0 -> EffectsCategorySombra(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                1 -> EffectsCategorySombraRelleno(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                2 -> EffectsCategorySombraContacto(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
            }
        }
    }
}

/**
 * Ventana flotante dedicada de "Reflejo" — hermana de [SombraFloatingWindow]
 * de acá arriba, MISMO patrón (ver su KDoc para el detalle completo):
 * agrupa 3 variantes (Reflejo / Light wrap / Luz global — antes 3 chips
 * sueltos, después 3 sub-pestañas dentro de la ventana compartida), así
 * que trae su propio segundo nivel de sub-pestañas adentro
 * (`reflejoSubCategories`/`selectedReflejoSub`, antes vivían en
 * `EffectsPanel`, ver ahí por qué se sacaron de esa función).
 *
 * DECISIÓN DELIBERADA sobre `onDeleteEffect` — mismo espíritu que la
 * excepción de `groundWallBreak` en [SombraFloatingWindow], acá va un
 * paso más allá: "Eliminar Reflejo" SOLO resetea los campos de
 * [EffectsCategoryReflejo] (sin `groundWallBreak`, compartido con
 * Sombra — mismo motivo exacto) y de [EffectsCategoryLightWrap]. NO
 * resetea nada de la sub-pestaña "Luz global" — reviné cada uno de sus
 * campos (`globalLightAngle`, `linkShadowToGlobalLight`,
 * `linkFillShadowToGlobalLight`, `shadowAngle`, `fillShadowAngle`,
 * `globalLightColor`, `linkShadowColorToGlobalLight`, `shadowColor`) y
 * NINGUNO tiene prefijo `reflection*` — son todos campos de SOMBRA (ya
 * cubiertos por el `onDeleteEffect` de [SombraFloatingWindow]) o
 * genuinamente globales (`globalLightAngle`/`globalLightColor`, sin
 * dueño de un solo efecto). "Luz global" vive como sub-pestaña de esta
 * ventana por comodidad de edición, pero borrar "Reflejo" no puede
 * andar tocando en silencio configuración que es, en los hechos, de
 * "Sombra" — la misma regla de no pisar entre ventanas que ya se aplicó
 * con `groundWallBreak`, llevada hasta el final.
 */
@Composable
private fun ReflejoFloatingWindow(
    layer: Layer,
    viewModel: EditorViewModel,
    ctrl: EffectsControlsState,
    liveBitmap: Bitmap?,
    fullBitmap: Bitmap?,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var liveRenderJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var lastLiveRenderAtMs by remember(layer.id) { mutableStateOf(0L) }
    val reflejoSubCategories = remember { listOf("Reflejo", "Light wrap", "Luz global") }
    var selectedReflejoSub by remember(layer.id) { mutableStateOf(0) }

    fun applyLivePreviewAndScheduleCommit() {
        val small = liveBitmap
        val liveParams = buildFullEffectsParams(ctrl)
        val now = System.currentTimeMillis()
        if (small != null && now - lastLiveRenderAtMs >= 60L) {
            lastLiveRenderAtMs = now
            liveRenderJob?.cancel()
            liveRenderJob = coroutineScope.launch {
                val rendered = viewModel.applyImageEffects(small, liveParams)
                viewModel.previewLayerRecolor(layer.id, rendered)
            }
        }

        commitJob?.cancel()
        commitJob = coroutineScope.launch {
            delay(500)
            val source = fullBitmap ?: liveBitmap ?: return@launch
            val params = buildFullEffectsParams(ctrl)
            val rendered = viewModel.applyImageEffects(source, params)
            viewModel.commitLayerRecolor(layer.id, rendered, source = "reflejo")
        }
    }

    FloatingToolWindow(
        title = "Reflejo",
        titleIcon = { tint -> EffectMenuReflectionIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // Ver el comentario grande sobre `onDeleteEffect` en
        // [ContornoFloatingWindow] (por qué existe) y el KDoc de esta
        // misma función (por qué NO incluye "Luz global" ni
        // `groundWallBreak`).
        onDeleteEffect = {
            commitJob?.cancel()
            liveRenderJob?.cancel()
            // Reflejo (base) — SIN groundWallBreak, ver KDoc de la función.
            ctrl.reflectionIntensity = 0f
            ctrl.reflectionGap = 0f
            ctrl.reflectionLength = 1f
            ctrl.reflectionBlur = 0f
            ctrl.reflectionNoise = 0f
            ctrl.reflectionSkew = 0f
            ctrl.reflectionTintIntensity = 0f
            ctrl.reflectionTintColor = android.graphics.Color.rgb(58, 110, 150)
            ctrl.reflectionEdgeFade = 0f
            ctrl.reflectionRippleIntensity = 0f
            ctrl.reflectionRippleScale = 0.5f
            ctrl.reflectionOpacityCurve = 0.5f
            ctrl.reflectionPerspective = 1f
            ctrl.reflectionProgressiveBlur = 0f
            ctrl.reflectionFresnel = 0f
            // Light wrap
            ctrl.lightWrapIntensity = 0f
            ctrl.lightWrapColor = android.graphics.Color.rgb(255, 244, 214)
            ctrl.lightWrapWidth = 0.4f
            ctrl.lightWrapAngle = 90f
            ctrl.lightWrapDirectionality = 0f
            viewModel.viewModelScope.launch {
                val source = fullBitmap ?: liveBitmap ?: return@launch
                val rendered = viewModel.applyImageEffects(source, buildFullEffectsParams(ctrl))
                viewModel.commitLayerRecolor(layer.id, rendered, source = "reflejo")
            }
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EffectsCategoryTabs(
                categories = reflejoSubCategories,
                selected = selectedReflejoSub,
                onSelected = { selectedReflejoSub = it }
            )
            when (selectedReflejoSub) {
                0 -> EffectsCategoryReflejo(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                1 -> EffectsCategoryLightWrap(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                2 -> EffectsCategoryLuzGlobal(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
            }
        }
    }
}

/**
 * Ventana flotante dedicada de "Distorsión" — hermana de
 * [SombraFloatingWindow]/[ReflejoFloatingWindow] de acá arriba, MISMO
 * patrón de chrome (título/cerrar/eliminar/arrastre/minimizado, ver el
 * KDoc de [ContornoFloatingWindow] para el detalle completo de fondo),
 * pero MÁS SIMPLE por dentro: a diferencia de las otras cuatro,
 * "Distorsión" no es un grupo de sliders sobre el `ctrl`/
 * `EffectsControlsState` compartido — es un motor de deformación de
 * malla completo y autocontenido ([DistortionPanel], con su propio
 * `liveBitmap`/`fullBitmap`, su propia malla, su propio historial de
 * deshacer/rehacer por trazo). Por eso esta ventana NO recibe `ctrl` ni
 * bitmaps — solo envuelve [DistortionPanel] tal cual, sin tocar su
 * interior en absoluto.
 *
 * `onDeleteEffect` no puede llamar a `resetAll()` directo — es una
 * función LOCAL adentro de [DistortionPanel], y esta ventana vive
 * afuera de ese composable. Se dispara a través de
 * `distortionBridge.onResetRequested` (ver el comentario grande en
 * [DistortionGestureBridge]), el mismo mecanismo de "bridge" que ya usa
 * esta pantalla para deshacer/rehacer — no un mecanismo nuevo inventado
 * para este caso puntual.
 */
@Composable
private fun DistortionFloatingWindow(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    distortionBridge: DistortionGestureBridge,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    FloatingToolWindow(
        title = "Distorsión",
        titleIcon = { tint -> EffectMenuDistortionIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        onDeleteEffect = {
            distortionBridge.onResetRequested?.invoke()
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        DistortionPanel(
            layer = layer,
            context = context,
            viewModel = viewModel,
            distortionBridge = distortionBridge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Pestaña "Efectos" del modo "Editando imagen" (al lado de "3D", ver
 * EditImageToolsHeader): difuminado de fondo, nitidez, saturación,
 * brillo, contraste, tono, contorno, resplandor, sombra proyectada y
 * reflejo — nivel "premium": ni el difuminado, ni el contorno, ni el
 * resplandor, ni la sombra, ni el reflejo son un slider suelto, cada uno
 * trae sus propios sub-controles (suavizado de contorno para el
 * difuminado; grosor+color para el contorno; intensidad+difuminado+color
 * para el resplandor; intensidad+suavizado+distancia+ángulo+color para
 * la sombra), como en cualquier herramienta de edición profesional
 * (estilo Photoshop/Affinity).
 *
 * "Difuminar" difumina el FONDO alrededor del sujeto (estilo retrato/
 * profundidad de campo), NUNCA al sujeto ni a su sombra — ver el
 * comentario grande de [com.yeivikas.olyzecs.engine.effects.ImageEffects.
 * applyBackgroundBlur] para el detalle técnico completo de por qué (y
 * cómo) se protege al sujeto. El contorno y el resplandor comparten el
 * mismo criterio de independencia: ambos se calculan siempre a partir
 * del alfa del bitmap ORIGINAL (ver [com.yeivikas.olyzecs.engine.effects.
 * ImageEffects.compositeWithEffects]), nunca del resultado de otro
 * control, así que no se "acumulan" pases entre sí.
 *
 * Los colores (sombra/contorno/resplandor) usan [ColorSwatchPickerButton]
 * — una muestra circular que abre un mini-popup con [ColorWheelPicker],
 * la MISMA rueda de color que ya usa la pestaña "Recolor" (ver
 * [LayerColorEditPanel]), reutilizada tal cual en vez de crear un
 * selector nuevo.
 *
 * Mismo patrón de vista previa en vivo + guardado con debounce de 500ms
 * que [Extrude3DPanel]/[LayerColorEditPanel]: los sliders "normales"
 * se aplican sobre una copia CHICA en cada tick (liviano, throttleado a
 * ~16/s para no saturar el hilo de UI) y sobre la copia GRANDE 500ms
 * después de la última pausa, que es la que de verdad se persiste como
 * archivo nuevo (mismo previewLayerRecolor/commitLayerRecolor genéricos
 * que ya usan las otras dos pestañas — no hacen nada específico de
 * "recolorear", solo suben/guardan el bitmap que se les pase).
 */
private class EffectsControlsState {
    var blur by mutableStateOf(0f)
    // BUG REAL corregido acá: este default estaba en 0.3f, mientras que
    // el motor ([ImageEffectsParams.edgeFeather]) usa 0f como neutro —
    // única inconsistencia encontrada entre los 66 campos de la UI y el
    // motor (todos los demás coinciden exactamente). Efecto práctico:
    // cualquier capa nueva que solo tocara "Difuminar (fondo)" sin tocar
    // "Suavizado de contorno" salía con un 30% de suavizado no pedido en
    // vez del comportamiento neutro documentado (transición dura,
    // protección exacta al alfa del sujeto). Alineado a 0f para que el
    // estado inicial del panel reproduzca fielmente `ImageEffectsParams()`.
    var edgeFeather by mutableStateOf(0f)
    var sharpen by mutableStateOf(0f)
    var saturation by mutableStateOf(1f)
    var brightness by mutableStateOf(0f)
    var contrast by mutableStateOf(1f)
    var hue by mutableStateOf(0f)
    var outlineIntensity by mutableStateOf(0f)
    var outlineColor by mutableStateOf(android.graphics.Color.WHITE)
    var outlineColor2 by mutableStateOf(android.graphics.Color.WHITE)
    var outlineGradientEnabled by mutableStateOf(false)
    var outlineFeather by mutableStateOf(0f)
    var outlinePosition by mutableStateOf(com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.OUTSIDE)
    var glowIntensity by mutableStateOf(0f)
    var glowBlur by mutableStateOf(0.5f)
    var glowColor by mutableStateOf(android.graphics.Color.WHITE)
    var glowColor2 by mutableStateOf(android.graphics.Color.WHITE)
    var glowGradientEnabled by mutableStateOf(false)
    var glowBlendMode by mutableStateOf(com.yeivikas.olyzecs.engine.effects.GlowBlendMode.NORMAL)
    var glowSpread by mutableStateOf(0f)
    var glowDistance by mutableStateOf(0f)
    var glowAngle by mutableStateOf(135f)
    var shadowIntensity by mutableStateOf(0f)
    var shadowBlur by mutableStateOf(0.5f)
    var shadowSpread by mutableStateOf(0f)
    var shadowScale by mutableStateOf(1f)
    var shadowNoise by mutableStateOf(0f)
    var shadowDistance by mutableStateOf(0.35f)
    var shadowAngle by mutableStateOf(135f)
    var shadowColor by mutableStateOf(android.graphics.Color.BLACK)
    var shadowSkew by mutableStateOf(0f)
    var shadowPerspectiveAmount by mutableStateOf(0f)
    var shadowFadeByDistance by mutableStateOf(0f)
    var shadowOpacityCurve by mutableStateOf(0.5f)
    var shadowBlendMultiply by mutableStateOf(true)
    var groundWallBreak by mutableStateOf(0f)
    var shadowContactHardening by mutableStateOf(0f)
    var fillShadowIntensity by mutableStateOf(0f)
    var fillShadowBlur by mutableStateOf(0.7f)
    var fillShadowDistance by mutableStateOf(0.2f)
    var fillShadowAngle by mutableStateOf(315f)
    var fillShadowColor by mutableStateOf(android.graphics.Color.rgb(30, 34, 48))
    var fillShadowScale by mutableStateOf(1f)
    var reflectionIntensity by mutableStateOf(0f)
    var reflectionGap by mutableStateOf(0f)
    var reflectionLength by mutableStateOf(1f)
    var reflectionBlur by mutableStateOf(0f)
    var reflectionNoise by mutableStateOf(0f)
    var reflectionSkew by mutableStateOf(0f)
    var reflectionTintIntensity by mutableStateOf(0f)
    var reflectionTintColor by mutableStateOf(android.graphics.Color.rgb(58, 110, 150))
    var reflectionEdgeFade by mutableStateOf(0f)
    var reflectionRippleIntensity by mutableStateOf(0f)
    var reflectionRippleScale by mutableStateOf(0.5f)
    var reflectionOpacityCurve by mutableStateOf(0.5f)
    var reflectionPerspective by mutableStateOf(1f)
    var reflectionProgressiveBlur by mutableStateOf(0f)
    var reflectionFresnel by mutableStateOf(0f)
    var globalLightAngle by mutableStateOf(135f)
    var linkShadowToGlobalLight by mutableStateOf(false)
    var linkFillShadowToGlobalLight by mutableStateOf(false)
    // Vínculo de COLOR con la luz global: cuando está activo, el color
    // de la sombra proyectada se recalcula automáticamente como el
    // complementario de [globalLightColor] — ver
    // [com.yeivikas.olyzecs.engine.effects.ImageEffects.deriveComplementaryShadowColor].
    var globalLightColor by mutableStateOf(android.graphics.Color.rgb(255, 244, 214))
    var linkShadowColorToGlobalLight by mutableStateOf(false)
    var contactShadowIntensity by mutableStateOf(0f)
    var contactShadowSize by mutableStateOf(0.5f)
    var contactShadowBlur by mutableStateOf(0.4f)
    var contactShadowColor by mutableStateOf(android.graphics.Color.BLACK)
    var contactShadowFalloff by mutableStateOf(0.5f)
    var contactShadowNoise by mutableStateOf(0f)
    var groundOcclusionIntensity by mutableStateOf(0f)
    // Puntos de apoyo adicionales para la sombra de contacto — lista
    // vacía = una sola mancha centrada (clásico). Ver
    // [com.yeivikas.olyzecs.engine.effects.ImageEffectsParams.contactShadowPoints].
    val contactShadowPoints = mutableStateListOf<com.yeivikas.olyzecs.engine.effects.ContactShadowPoint>()
    var lightWrapIntensity by mutableStateOf(0f)
    var lightWrapColor by mutableStateOf(android.graphics.Color.rgb(255, 244, 214))
    var lightWrapWidth by mutableStateOf(0.4f)
    var lightWrapAngle by mutableStateOf(90f)
    var lightWrapDirectionality by mutableStateOf(0f)
}

/**
 * Arma el `ImageEffectsParams` COMPLETO (blur/color/contorno/resplandor/
 * sombra/reflejo — todo el stack no-destructivo de "Efecto") a partir de
 * un [EffectsControlsState] — extraído de lo que antes era el
 * `currentParams()` local de [EffectsPanel] para que TAMBIÉN lo puedan
 * usar [ContornoFloatingWindow]/[ResplandorFloatingWindow]: las tres
 * comparten el mismo `ctrl` (ver el comentario grande en la firma de
 * [EffectsPanel]) y tienen que re-renderizar el stack COMPLETO en cada
 * commit, no solo su propia categoría — si cada una renderizara nada
 * más que "su" pedazo, el commit de una apagaría lo que la otra ya tenía
 * aplicado. Una sola función, un solo lugar para tocar si mañana se
 * agrega un campo nuevo.
 */
private fun buildFullEffectsParams(ctrl: EffectsControlsState) =
    com.yeivikas.olyzecs.engine.effects.ImageEffectsParams(
        blur = ctrl.blur,
        edgeFeather = ctrl.edgeFeather,
        sharpen = ctrl.sharpen,
        saturation = ctrl.saturation,
        brightness = ctrl.brightness,
        contrast = ctrl.contrast,
        hue = ctrl.hue,
        outlineIntensity = ctrl.outlineIntensity,
        outlineColor = ctrl.outlineColor,
        outlineColor2 = ctrl.outlineColor2,
        outlineGradientEnabled = ctrl.outlineGradientEnabled,
        outlineFeather = ctrl.outlineFeather,
        outlinePosition = ctrl.outlinePosition,
        glowIntensity = ctrl.glowIntensity,
        glowBlur = ctrl.glowBlur,
        glowColor = ctrl.glowColor,
        glowColor2 = ctrl.glowColor2,
        glowGradientEnabled = ctrl.glowGradientEnabled,
        glowBlendMode = ctrl.glowBlendMode,
        glowSpread = ctrl.glowSpread,
        glowDistance = ctrl.glowDistance,
        glowAngleDeg = ctrl.glowAngle,
        shadowIntensity = ctrl.shadowIntensity,
        shadowBlur = ctrl.shadowBlur,
        shadowSpread = ctrl.shadowSpread,
        shadowScale = ctrl.shadowScale,
        shadowNoise = ctrl.shadowNoise,
        shadowDistance = ctrl.shadowDistance,
        shadowAngleDeg = ctrl.shadowAngle,
        shadowColor = ctrl.shadowColor,
        shadowSkewDegrees = ctrl.shadowSkew,
        shadowPerspectiveAmount = ctrl.shadowPerspectiveAmount,
        shadowFadeByDistance = ctrl.shadowFadeByDistance,
        shadowOpacityCurve = ctrl.shadowOpacityCurve,
        shadowBlendMultiply = ctrl.shadowBlendMultiply,
        groundWallBreak = ctrl.groundWallBreak,
        shadowContactHardening = ctrl.shadowContactHardening,
        fillShadowIntensity = ctrl.fillShadowIntensity,
        fillShadowBlur = ctrl.fillShadowBlur,
        fillShadowDistance = ctrl.fillShadowDistance,
        fillShadowAngleDeg = ctrl.fillShadowAngle,
        fillShadowColor = ctrl.fillShadowColor,
        fillShadowScale = ctrl.fillShadowScale,
        reflectionIntensity = ctrl.reflectionIntensity,
        reflectionGap = ctrl.reflectionGap,
        reflectionLength = ctrl.reflectionLength,
        reflectionBlur = ctrl.reflectionBlur,
        reflectionNoise = ctrl.reflectionNoise,
        reflectionSkewDegrees = ctrl.reflectionSkew,
        reflectionTintIntensity = ctrl.reflectionTintIntensity,
        reflectionTintColor = ctrl.reflectionTintColor,
        reflectionEdgeFade = ctrl.reflectionEdgeFade,
        reflectionRippleIntensity = ctrl.reflectionRippleIntensity,
        reflectionRippleScale = ctrl.reflectionRippleScale,
        reflectionOpacityCurve = ctrl.reflectionOpacityCurve,
        reflectionPerspective = ctrl.reflectionPerspective,
        reflectionProgressiveBlur = ctrl.reflectionProgressiveBlur,
        reflectionFresnel = ctrl.reflectionFresnel,
        contactShadowIntensity = ctrl.contactShadowIntensity,
        contactShadowSize = ctrl.contactShadowSize,
        contactShadowBlur = ctrl.contactShadowBlur,
        contactShadowColor = ctrl.contactShadowColor,
        contactShadowFalloff = ctrl.contactShadowFalloff,
        contactShadowNoise = ctrl.contactShadowNoise,
        groundOcclusionIntensity = ctrl.groundOcclusionIntensity,
        contactShadowPoints = ctrl.contactShadowPoints.toList(),
        lightWrapIntensity = ctrl.lightWrapIntensity,
        lightWrapColor = ctrl.lightWrapColor,
        lightWrapWidth = ctrl.lightWrapWidth,
        lightWrapAngleDeg = ctrl.lightWrapAngle,
        lightWrapDirectionality = ctrl.lightWrapDirectionality
    )

@Composable
private fun rememberEffectsControlsState(key: Any?): EffectsControlsState =
    remember(key) { EffectsControlsState() }

@Composable
private fun EffectsPanel(
    layer: Layer,
    viewModel: EditorViewModel,
    // A PEDIDO DEL USUARIO: la categoría activa ("Fondo"/"Color"/
    // "Contorno"/... — ver `effectsTopCategories` acá abajo) ya NO es
    // local a este panel: se elige desde EditImageEffectsMenu, el menú
    // que cuelga del texto "Efecto" de la barra superior (ver topBar en
    // EditorScreen). Sube como parámetro por la misma razón que
    // `selectedTab` subió en LayerColorEditPanel — el botón que la
    // cambia y el panel que la consume viven en composables distintos.
    selectedTopCategory: Int,
    onSelectedTopCategoryChange: (Int) -> Unit,
    // A PEDIDO DEL USUARIO — MULTI-VENTANA ("Contorno"/"Resplandor"/
    // "Sombra"/"Reflejo" con ventana propia, ver
    // [ContornoFloatingWindow]/[ResplandorFloatingWindow]/
    // [SombraFloatingWindow]/[ReflejoFloatingWindow]; "Distorsión"
    // también tiene la suya, [DistortionFloatingWindow], pero esa no
    // toca `ctrl` en absoluto — es un motor de malla autocontenido, ver
    // su propio KDoc): `ctrl`, `liveBitmap` y `fullBitmap` suben como
    // parámetros en vez de crearse acá adentro con `remember(layer.id)`.
    // Motivo real, no solo prolijidad: aunque EffectsPanel en sí ya solo
    // se monta en UN lugar (la ventana compartida, que a esta altura ya
    // no cubre ninguna categoría accesible desde el menú "Efecto" — solo
    // Fondo/Color/Presets, inalcanzables desde ahí — las otras cuatro
    // llaman DIRECTO a sus composables hoja,
    // [EffectsCategoryContorno]/[EffectsCategoryResplandor]/etc., sin
    // pasar por acá, ver el KDoc de esas cuatro ventanas para el
    // porqué), las CINCO siguen leyendo y escribiendo el MISMO
    // `EffectsControlsState`/bitmaps de base — levantados una sola vez
    // por capa en EditorScreen y reenviados tal cual a las cinco. Si
    // cada una tuviera su propia copia, (a) cada una arrancaría con sus
    // propios sliders en 0 aunque OTRA ventana ya tenga ese efecto
    // aplicado, y el próximo commit de esa ventana
    // "borraría" el efecto ajeno sin que el usuario tocara nada de esa
    // categoría; y (b) cada una decodificaría su "base plana" en un
    // momento distinto — si para entonces `layer.sourceUri` ya cambió
    // por el commit de otra ventana, esa nueva base ya NO sería plana, y
    // el próximo commit la re-renderizaría por encima, duplicando el
    // efecto. Levantar los tres (ctrl/liveBitmap/fullBitmap) en el
    // llamador común elimina ambos problemas de raíz, sin importar
    // cuántas ventanas terminen compartiéndolos.
    ctrl: EffectsControlsState,
    liveBitmap: Bitmap?,
    fullBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Sub-categorías de la pestaña "Efectos" — antes todos los
    // controles vivían apilados en una sola columna larga con scroll;
    // ahora se agrupan por categoría y solo se muestran los controles
    // de la categoría seleccionada. Ningún control, valor por defecto
    // ni lógica de aplicación se modifica acá: es puramente una
    // reorganización visual de los mismos sliders/botones de siempre.
    //
    // A PEDIDO DEL USUARIO — reordenado en dos niveles, definido charla
    // por charla, categoría por categoría, ANTES de tocar código:
    //  - "Básicos" se separó en dos categorías reales sin relación entre
    //    sí: "Fondo" (difuminar/suavizado — solo toca el fondo) y "Color"
    //    (nitidez/saturación/brillo/contraste/tono — solo toca el
    //    sujeto). Antes vivían mezcladas bajo un solo botón engañoso.
    //    ACTUALIZACIÓN posterior: "Color" ya no vive acá — se mudó
    //    entera a la pestaña "Color" de arriba como su opción "Básico"
    //    (ver [ColorBasicoFloatingWindow]); "Fondo" sigue siendo una
    //    categoría de "Efecto", tal cual quedó descrito acá.
    //  - "Contorno" y "Resplandor" quedan cada una como categoría propia,
    //    al mismo nivel que el resto — NO son sub-ítems de "Sombra" (se
    //    descartó esa idea en la charla: contorno es un borde sólido,
    //    resplandor es un halo de luz hacia afuera, ninguno de los dos
    //    es una sombra).
    //  - "Sombra" (antes 3 chips sueltos: Sombra / Sombra relleno /
    //    Sombra contacto) y "Reflejo" (antes 3 chips sueltos: Reflejo /
    //    Light wrap / Luz global) SÍ agrupan de verdad varias variantes
    //    de un mismo concepto, así que esas dos quedan con un segundo
    //    nivel de sub-pestañas propio — ACTUALIZACIÓN posterior: ese
    //    segundo nivel ya no vive acá abajo, "anidado" dentro de esta
    //    función; se mudó junto con cada una a su propia ventana (ver
    //    [SombraFloatingWindow]/[ReflejoFloatingWindow]) cuando pasaron a
    //    tener ventana propia, en vez de ocupar 3 lugares cada una en la
    //    fila de arriba.
    //  - "Presets" queda tal cual está, al final — pendiente de una
    //    revisión propia que todavía no se hizo.
    //  - "Distorsión" (las 9 herramientas de deformación de malla,
    //    ver DistortionPanel) se agrega acá como la ÚLTIMA categoría de
    //    la cadena, no como pestaña propia al lado de "Efectos" — es un
    //    efecto de imagen más, así que vive adentro de "Efectos" igual
    //    que el resto, no como ventana independiente.
    // Ni un slider, ni un valor por defecto, ni una función de cálculo
    // cambiaron con este reordenamiento — es 100% reorganización visual.
    val effectsTopCategories = remember {
        listOf("Fondo", "Color", "Contorno", "Resplandor", "Sombra", "Reflejo", "Presets", "Distorsión")
    }
    // A PEDIDO DEL USUARIO: ya no es un `remember` local — viene de
    // arriba (ver el parámetro `selectedTopCategory` de esta función).
    // Se mantienen los MISMOS índices de siempre dentro de
    // `effectsTopCategories` (Fondo=0 ... Distorsión=7): el menú de
    // arriba (EditImageEffectsMenu) solo deja de OFRECER Fondo/Color/
    // Presets como opciones, pero el "when" de acá abajo que rutea cada
    // índice a sus sliders no cambió ni un poco.
    val selectedEffectsTopCategory = selectedTopCategory

    // "Sombra" y "Reflejo" ya no tienen sub-pestañas acá — se mudaron
    // adentro de [SombraFloatingWindow]/[ReflejoFloatingWindow] junto con
    // sus propias ventanas dedicadas (ver el comentario grande más abajo,
    // donde antes vivía el `when` de índices 4 y 5).

    var blur by ctrl::blur
    var edgeFeather by ctrl::edgeFeather
    var sharpen by ctrl::sharpen
    var saturation by ctrl::saturation
    var brightness by ctrl::brightness
    var contrast by ctrl::contrast
    var hue by ctrl::hue
    var outlineIntensity by ctrl::outlineIntensity
    var outlineColor by ctrl::outlineColor
    var outlineFeather by ctrl::outlineFeather
    var outlinePosition by ctrl::outlinePosition
    var glowIntensity by ctrl::glowIntensity
    var glowBlur by ctrl::glowBlur
    var glowColor by ctrl::glowColor
    var glowSpread by ctrl::glowSpread
    var glowDistance by ctrl::glowDistance
    var glowAngle by ctrl::glowAngle
    var shadowIntensity by ctrl::shadowIntensity
    var shadowBlur by ctrl::shadowBlur
    var shadowSpread by ctrl::shadowSpread
    var shadowScale by ctrl::shadowScale
    var shadowNoise by ctrl::shadowNoise
    var shadowDistance by ctrl::shadowDistance
    var shadowAngle by ctrl::shadowAngle
    var shadowColor by ctrl::shadowColor
    var shadowSkew by ctrl::shadowSkew
    var shadowPerspectiveAmount by ctrl::shadowPerspectiveAmount
    var shadowFadeByDistance by ctrl::shadowFadeByDistance
    var shadowOpacityCurve by ctrl::shadowOpacityCurve
    var shadowBlendMultiply by ctrl::shadowBlendMultiply
    var groundWallBreak by ctrl::groundWallBreak
    var shadowContactHardening by ctrl::shadowContactHardening
    var fillShadowIntensity by ctrl::fillShadowIntensity
    var fillShadowBlur by ctrl::fillShadowBlur
    var fillShadowDistance by ctrl::fillShadowDistance
    var fillShadowAngle by ctrl::fillShadowAngle
    var fillShadowColor by ctrl::fillShadowColor
    var fillShadowScale by ctrl::fillShadowScale
    var reflectionIntensity by ctrl::reflectionIntensity
    var reflectionGap by ctrl::reflectionGap
    var reflectionLength by ctrl::reflectionLength
    var reflectionBlur by ctrl::reflectionBlur
    var reflectionNoise by ctrl::reflectionNoise
    var reflectionSkew by ctrl::reflectionSkew
    var reflectionTintIntensity by ctrl::reflectionTintIntensity
    var reflectionTintColor by ctrl::reflectionTintColor
    var reflectionEdgeFade by ctrl::reflectionEdgeFade
    var reflectionRippleIntensity by ctrl::reflectionRippleIntensity
    var reflectionRippleScale by ctrl::reflectionRippleScale
    var reflectionOpacityCurve by ctrl::reflectionOpacityCurve
    var reflectionPerspective by ctrl::reflectionPerspective
    var reflectionProgressiveBlur by ctrl::reflectionProgressiveBlur
    var reflectionFresnel by ctrl::reflectionFresnel
    // "Luz global" — posición del sol/fuente de luz única que puede
    // gobernar el ángulo de la sombra proyectada y de la sombra de
    // relleno a la vez, para que nunca queden mirando para lados
    // distintos por error (el problema típico de tener ángulos
    // totalmente sueltos por sombra). Por defecto DESVINCULADO (false):
    // el comportamiento de cada slider de ángulo sigue siendo 100% el
    // de siempre hasta que el usuario decide activarlo a propósito.
    var globalLightAngle by ctrl::globalLightAngle
    var linkShadowToGlobalLight by ctrl::linkShadowToGlobalLight
    var linkFillShadowToGlobalLight by ctrl::linkFillShadowToGlobalLight
    var globalLightColor by ctrl::globalLightColor
    var linkShadowColorToGlobalLight by ctrl::linkShadowColorToGlobalLight
    var contactShadowIntensity by ctrl::contactShadowIntensity
    var contactShadowSize by ctrl::contactShadowSize
    var contactShadowBlur by ctrl::contactShadowBlur
    var contactShadowColor by ctrl::contactShadowColor
    var contactShadowFalloff by ctrl::contactShadowFalloff
    var contactShadowNoise by ctrl::contactShadowNoise
    var groundOcclusionIntensity by ctrl::groundOcclusionIntensity
    // Alias directo a la lista mutable de `ctrl` (no un delegado `var`:
    // una `SnapshotStateList` ya es la referencia mutable en sí misma,
    // así que no hace falta — y no se puede — envolverla en `by`).
    val contactShadowPoints = ctrl.contactShadowPoints
    var lightWrapIntensity by ctrl::lightWrapIntensity
    var lightWrapColor by ctrl::lightWrapColor
    var lightWrapWidth by ctrl::lightWrapWidth
    var lightWrapAngle by ctrl::lightWrapAngle
    var lightWrapDirectionality by ctrl::lightWrapDirectionality

    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    // Throttle de la vista previa en vivo — mismo motivo/valores que en
    // Extrude3DPanel.applyLivePreviewAndScheduleCommit: sin esto, cada
    // tick crudo del Slider dispararía un box-blur de 3 pasadas en el
    // hilo de UI y se vería entrecortado mientras se arrastra.
    var liveRenderJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var lastLiveRenderAtMs by remember(layer.id) { mutableStateOf(0L) }

    // La carga de la base "plana" (sin efectos todavía) ya NO vive acá
    // — ver el comentario grande junto a los parámetros `liveBitmap`/
    // `fullBitmap`, arriba: se hace UNA sola vez por capa en
    // EditorScreen (ver `effectsLiveBitmap`/`effectsFullBitmap` +
    // su `LaunchedEffect(editModeLayerId)`), y se reenvía tal cual acá.
    // Ningún slider, valor por defecto ni lógica de render cambió con
    // este movimiento.
    fun currentParams() = buildFullEffectsParams(ctrl)

    fun applyLivePreviewAndScheduleCommit() {
        val small = liveBitmap
        val liveParams = currentParams()
        val now = System.currentTimeMillis()
        if (small != null && now - lastLiveRenderAtMs >= 60L) {
            lastLiveRenderAtMs = now
            liveRenderJob?.cancel()
            liveRenderJob = coroutineScope.launch {
                val rendered = viewModel.applyImageEffects(small, liveParams)
                viewModel.previewLayerRecolor(layer.id, rendered)
            }
        }

        commitJob?.cancel()
        commitJob = coroutineScope.launch {
            delay(500)
            val source = fullBitmap ?: liveBitmap ?: return@launch
            val params = currentParams()
            val rendered = viewModel.applyImageEffects(source, params)
            viewModel.commitLayerRecolor(layer.id, rendered, source = "effects")
        }
    }

    // "Luz global" — mientras cada vínculo esté activo, el ángulo de
    // esa sombra sigue en vivo a [globalLightAngle]; la sombra de
    // relleno se calcula como el opuesto (+180°), imitando luz
    // rebotada desde el lado contrario a la fuente principal, un
    // criterio de iluminación de 3 puntos estándar en compositing
    // profesional. Declarado DESPUÉS de [applyLivePreviewAndScheduleCommit]
    // a propósito, para no depender de una referencia hacia adelante a
    // una función local.
    LaunchedEffect(globalLightAngle, linkShadowToGlobalLight) {
        if (linkShadowToGlobalLight && shadowAngle != globalLightAngle) {
            shadowAngle = globalLightAngle
            applyLivePreviewAndScheduleCommit()
        }
    }
    LaunchedEffect(globalLightAngle, linkFillShadowToGlobalLight) {
        val opposite = (globalLightAngle + 180f) % 360f
        if (linkFillShadowToGlobalLight && fillShadowAngle != opposite) {
            fillShadowAngle = opposite
            applyLivePreviewAndScheduleCommit()
        }
    }
    // Vínculo de COLOR: mientras esté activo, el color de la sombra
    // proyectada se recalcula como el complementario de
    // [globalLightColor] cada vez que ese color cambia — ver
    // [ImageEffects.deriveComplementaryShadowColor]. Igual criterio que
    // los vínculos de ángulo de arriba: el usuario puede seguir
    // moviendo el color de luz libremente y la sombra lo sigue en vivo.
    LaunchedEffect(globalLightColor, linkShadowColorToGlobalLight) {
        if (linkShadowColorToGlobalLight) {
            val derived = com.yeivikas.olyzecs.engine.effects.ImageEffects.deriveComplementaryShadowColor(globalLightColor)
            if (shadowColor != derived) {
                shadowColor = derived
                applyLivePreviewAndScheduleCommit()
            }
        }
    }

    Column(modifier = modifier) {
            // BUG REAL corregido acá — mismo caso que [ColorBasicoFloatingWindow]/
            // [Basico3DFloatingWindow] (ver el comentario grande en esa
            // primera función para el detalle completo): TODA esta
            // pestaña ("Fondo"/"Color"/"Contorno"/"Resplandor"/"Sombra"/
            // "Reflejo"/"Presets") vivía atrás de este mismo `isLoading`
            // de `liveBitmap`, aunque ninguna de esas categorías dibuja
            // ni depende de ese bitmap — todas leen/escriben `ctrl`, en
            // memoria, disponible desde el primer frame. Resultado: cada
            // vez que se abría "Efecto" se veía el mismo salto de tamaño
            // en dos pasos (spinner chico → panel real unos milisegundos
            // después) que el usuario reportó como "se expande por
            // partes/con lag". `liveBitmap`/`fullBitmap` se siguen
            // cargando igual en el `LaunchedEffect` de arriba;
            // `applyLivePreviewAndScheduleCommit()` ya trata ese bitmap
            // como opcional. "Distorsión" (índice 7, más abajo) tenía el
            // mismo `isLoading` propio y se corrigió igual — ver
            // [DistortionPanel], mismo motivo exacto: sus chips de
            // herramienta y sliders tampoco dependen del bitmap.
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // A PEDIDO DEL USUARIO: la fila de chips de categoría
                // (Fondo/Color/Contorno/.../Distorsión) que vivía ACÁ se
                // sacó por completo — la categoría ahora se elige desde
                // EditImageEffectsMenu, el menú que cuelga del texto
                // "Efecto" de la barra superior. `effectsTopCategories`
                // sigue existiendo, pero a esta altura es solo
                // documentación de índices (Fondo=0 ... Distorsión=7,
                // ver su declaración más arriba) para no romper los
                // índices de siempre en el "when" de esta función — ya
                // no se renderiza como fila de chips acá, y tampoco
                // respalda ningún segundo nivel de sub-pestañas (las de
                // Sombra/Reflejo se mudaron junto con esas dos a sus
                // propias ventanas, ver [SombraFloatingWindow]/
                // [ReflejoFloatingWindow]).
                //
                // "Distorsión" (índice 7) también se mudó a su propia
                // ventana (ver [DistortionFloatingWindow]) — esta función
                // ya NO monta [DistortionPanel] en absoluto, así que el
                // `if/else` que antes elegía entre "el panel completo de
                // Distorsión" y "la columna con scroll de sliders sobre
                // `ctrl`" (necesario en su momento porque anidar un
                // `verticalScroll` dentro de otro rompe el layout) dejó
                // de tener sentido: ya no hay dos casos que elegir entre
                // — todo lo que queda acá (Fondo/Color/Presets) SIEMPRE
                // usa el molde de sliders con scroll.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                when (selectedEffectsTopCategory) {
                    0 -> EffectsCategoryFondo(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                    1 -> EffectsCategoryColor(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                    // "Contorno" (2), "Resplandor" (3), "Sombra" (4),
                    // "Reflejo" (5) y "Distorsión" (7) YA NO se rutean
                    // acá — [EditImageEffectsMenu] dejó de abrir esta
                    // ventana compartida para esos cinco índices; cada
                    // una llama directo a su propia función/ventana
                    // dedicada ([ContornoFloatingWindow]/
                    // [ResplandorFloatingWindow]/[SombraFloatingWindow]/
                    // [ReflejoFloatingWindow]/[DistortionFloatingWindow]).
                    // `selectedTopCategory` solo llega acá con 0, 1 o 6 —
                    // de ahí que estos cinco casos ya no tengan sentido en
                    // este `when` y se sacaron, junto con el segundo
                    // nivel de sub-pestañas que ya no lo necesita ninguna
                    // categoría que quede acá.
                    6 -> EffectsCategoryPresets(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }
                }
            }
    }
}
@Composable
private fun EffectsCategorySombra(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var shadowIntensity by ctrl::shadowIntensity
    var shadowBlur by ctrl::shadowBlur
    var shadowSpread by ctrl::shadowSpread
    var shadowScale by ctrl::shadowScale
    var shadowDistance by ctrl::shadowDistance
    var shadowAngle by ctrl::shadowAngle
    var shadowColor by ctrl::shadowColor
    var shadowSkew by ctrl::shadowSkew
    var shadowPerspectiveAmount by ctrl::shadowPerspectiveAmount
    var shadowFadeByDistance by ctrl::shadowFadeByDistance
    var shadowOpacityCurve by ctrl::shadowOpacityCurve
    var shadowNoise by ctrl::shadowNoise
    var shadowBlendMultiply by ctrl::shadowBlendMultiply
    var groundWallBreak by ctrl::groundWallBreak
    var shadowContactHardening by ctrl::shadowContactHardening
    var globalLightAngle by ctrl::globalLightAngle
    var linkShadowToGlobalLight by ctrl::linkShadowToGlobalLight
    var linkShadowColorToGlobalLight by ctrl::linkShadowColorToGlobalLight

                Text(
                    "Sombra",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )

                LabeledSlider(
                    label = "Intensidad",
                    value = shadowIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowIntensity = it; onChanged() }

                // Los siguientes tres controles "premium" de la sombra solo
                // importan mientras la sombra es visible — deshabilitados
                // (en vez de ocultos) mientras Intensidad está en 0, así el
                // usuario siempre ve que existen sin que parezcan hacer
                // nada todavía.
                val shadowControlsEnabled = shadowIntensity > 0.001f

                LabeledSlider(
                    label = "Difuminado de la sombra",
                    value = shadowBlur,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowBlur = it; onChanged() }

                LabeledSlider(
                    label = "Expansión (Spread)",
                    value = shadowSpread,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowSpread = it; onChanged() }
                Text(
                    "Agranda el núcleo de la sombra y ajusta su borde — el control \"Spread\" clásico de Photoshop, distinto de solo difuminar más",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Distancia de la sombra",
                    value = shadowDistance,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowDistance = it; onChanged() }

                LabeledSlider(
                    label = "Escala de la sombra",
                    value = shadowScale,
                    range = 0.4f..2f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowScale = it; onChanged() }
                Text(
                    "Agranda o achica la sombra de forma independiente del sujeto, pivotando siempre desde su punto de apoyo — sombras reales rara vez son 1:1 con el objeto",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Ángulo de la sombra",
                    value = shadowAngle,
                    range = 0f..360f,
                    enabled = shadowControlsEnabled && !linkShadowToGlobalLight,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { shadowAngle = it; onChanged() }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Vincular a Luz global",
                        color = if (shadowControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkShadowToGlobalLight,
                        onCheckedChange = {
                            linkShadowToGlobalLight = it
                            if (it) { shadowAngle = globalLightAngle }
                            onChanged()
                        },
                        enabled = shadowControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "Con esto activo, el ángulo lo maneja la sub-categoría \"Luz global\" — para que esta sombra y la de relleno nunca miren para lados distintos",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                ColorSwatchPickerButton(
                    label = "Color de la sombra",
                    colorArgb = shadowColor,
                    enabled = shadowControlsEnabled && !linkShadowColorToGlobalLight
                ) { c -> shadowColor = c; onChanged() }
                if (linkShadowColorToGlobalLight) {
                    Text(
                        "Vinculado al color de la Luz global (complementario) — desactivá el vínculo en esa sub-categoría para elegirlo manualmente",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                LabeledSlider(
                    label = "Inclinación (perspectiva)",
                    value = shadowSkew,
                    range = -45f..45f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { shadowSkew = it; onChanged() }

                LabeledSlider(
                    label = "Perspectiva real (punto de fuga)",
                    value = shadowPerspectiveAmount,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowPerspectiveAmount = it; onChanged() }
                Text(
                    "Se suma a la inclinación de arriba: en vez de solo inclinar la sombra, la hace converger de verdad hacia un punto de fuga — el pie queda fijo y el extremo lejano se angosta, como proyectada sobre un piso real en ángulo",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Desvanecer con la distancia",
                    value = shadowFadeByDistance,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowFadeByDistance = it; onChanged() }

                LabeledSlider(
                    label = "Curva del desvanecimiento",
                    value = shadowOpacityCurve,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled && shadowFadeByDistance > 0.001f,
                    valueLabel = { if (abs(it - 0.5f) < 0.02f) "Lineal" else if (it < 0.5f) "Rápida" else "Sostenida" }
                ) { shadowOpacityCurve = it; onChanged() }
                Text(
                    "Solo afecta con \"Desvanecer con la distancia\" activo: cambia la FORMA de la caída, no cuánto se desvanece",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Grano / textura",
                    value = shadowNoise,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowNoise = it; onChanged() }
                Text(
                    "Rompe la uniformidad perfecta de la sombra con una textura fina — evita el look \"digital\"/plano sobre superficies reales",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Penumbra progresiva (contact-hardening)",
                    value = shadowContactHardening,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowContactHardening = it; onChanged() }
                Text(
                    "Se suma al Difuminado de arriba: crece hacia el extremo lejano del punto de apoyo, quedando nítida junto al sujeto — bajo luz de área (no un foco puntual ideal), EL efecto que distingue una sombra fotorrealista de una plana",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Quiebre piso/pared",
                    value = groundWallBreak,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { if (it < 0.02f) "Piso infinito" else "${(it * 100).roundToInt()}%" }
                ) { groundWallBreak = it; onChanged() }
                Text(
                    "Simula un fondo de estudio \"infinito\": el piso se dobla hacia una pared vertical en vez de estirarse para siempre — comparte este mismo control con la sección Reflejo. Mientras está activo, reemplaza a \"Perspectiva real (punto de fuga)\" de arriba",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Modo multiplicar",
                        color = if (shadowControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = shadowBlendMultiply,
                        onCheckedChange = { shadowBlendMultiply = it; onChanged() },
                        enabled = shadowControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "Oscurece de forma natural donde la sombra proyectada, la de relleno y la de contacto se superponen entre sí, en vez de mezclarse como un gris translúcido — el estándar profesional",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )
}
@Composable
private fun EffectsCategoryReflejo(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var reflectionIntensity by ctrl::reflectionIntensity
    var reflectionGap by ctrl::reflectionGap
    var reflectionLength by ctrl::reflectionLength
    var reflectionBlur by ctrl::reflectionBlur
    var reflectionNoise by ctrl::reflectionNoise
    var reflectionSkew by ctrl::reflectionSkew
    var reflectionTintIntensity by ctrl::reflectionTintIntensity
    var reflectionTintColor by ctrl::reflectionTintColor
    var reflectionEdgeFade by ctrl::reflectionEdgeFade
    var reflectionRippleIntensity by ctrl::reflectionRippleIntensity
    var reflectionRippleScale by ctrl::reflectionRippleScale
    var reflectionOpacityCurve by ctrl::reflectionOpacityCurve
    var reflectionPerspective by ctrl::reflectionPerspective
    var reflectionProgressiveBlur by ctrl::reflectionProgressiveBlur
    var reflectionFresnel by ctrl::reflectionFresnel
    var groundWallBreak by ctrl::groundWallBreak

                Text(
                    "Reflejo",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Copia volteada del sujeto, con opacidad decreciente hacia abajo",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = reflectionIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionIntensity = it; onChanged() }

                // Controles "premium" del reflejo: solo importan mientras
                // el reflejo es visible — deshabilitados (no ocultos)
                // mientras Intensidad está en 0, mismo criterio que ya
                // usan los controles extra de la sombra.
                val reflectionControlsEnabled = reflectionIntensity > 0.001f

                LabeledSlider(
                    label = "Distancia (separación del pie)",
                    value = reflectionGap,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionGap = it; onChanged() }

                LabeledSlider(
                    label = "Largo del reflejo",
                    value = reflectionLength,
                    range = 0.1f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionLength = it; onChanged() }

                LabeledSlider(
                    label = "Difuminado del reflejo",
                    value = reflectionBlur,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionBlur = it; onChanged() }

                LabeledSlider(
                    label = "Difuminado progresivo por distancia",
                    value = reflectionProgressiveBlur,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionProgressiveBlur = it; onChanged() }
                Text(
                    "Se suma al difuminado de arriba: en vez de un desenfoque parejo, crece cuanto más lejos del pie — como un piso pulido real, más nítido cerca y más difuso hacia el fondo",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Inclinación (perspectiva)",
                    value = reflectionSkew,
                    range = -45f..45f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { reflectionSkew = it; onChanged() }

                LabeledSlider(
                    label = "Tinte del reflejo",
                    value = reflectionTintIntensity,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionTintIntensity = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color del tinte",
                    colorArgb = reflectionTintColor,
                    enabled = reflectionControlsEnabled && reflectionTintIntensity > 0.001f
                ) { c -> reflectionTintColor = c; onChanged() }

                LabeledSlider(
                    label = "Desvanecer bordes",
                    value = reflectionEdgeFade,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionEdgeFade = it; onChanged() }

                LabeledSlider(
                    label = "Ondulación (agua)",
                    value = reflectionRippleIntensity,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionRippleIntensity = it; onChanged() }

                LabeledSlider(
                    label = "Densidad de las ondas",
                    value = reflectionRippleScale,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled && reflectionRippleIntensity > 0.001f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionRippleScale = it; onChanged() }

                LabeledSlider(
                    label = "Grano / textura",
                    value = reflectionNoise,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionNoise = it; onChanged() }
                Text(
                    "Micro-imperfecciones de la superficie (polvo, rayones) — un piso pulido real casi nunca es un espejo perfecto. Es una textura fija, no se mueve con la ondulación de arriba",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Curva de opacidad",
                    value = reflectionOpacityCurve,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionOpacityCurve = it; onChanged() }
                Text(
                    "50% = degradado lineal clásico. Bajo = se apaga casi enseguida, alto = se sostiene fuerte y corta de golpe al final",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Perspectiva (compresión)",
                    value = reflectionPerspective,
                    range = 0.3f..1.5f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionPerspective = it; onChanged() }
                Text(
                    "Aplana o estira el reflejo verticalmente sin tocar su ancho — un reflejo real sobre un piso en ángulo casi nunca es 1:1 con el sujeto",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Fresnel (refuerzo hacia el horizonte)",
                    value = reflectionFresnel,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionFresnel = it; onChanged() }
                Text(
                    "Física real de reflexión: cualquier superficie refleja MÁS en ángulo rasante (lejos, cerca del horizonte) que justo debajo del pie. Se suma sobre la curva de opacidad de arriba, contrarrestándola hacia el extremo lejano",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Quiebre piso/pared",
                    value = groundWallBreak,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { if (it < 0.02f) "Piso infinito" else "${(it * 100).roundToInt()}%" }
                ) { groundWallBreak = it; onChanged() }
                Text(
                    "Mismo control que en la sección Sombra (fondo de estudio \"infinito\"): el reflejo deja de estirarse en línea recta y \"sube\" hacia una pared vertical a partir de este punto",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )
}
@Composable
private fun EffectsCategoryLuzGlobal(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var globalLightAngle by ctrl::globalLightAngle
    var linkShadowToGlobalLight by ctrl::linkShadowToGlobalLight
    var linkFillShadowToGlobalLight by ctrl::linkFillShadowToGlobalLight
    var shadowAngle by ctrl::shadowAngle
    var fillShadowAngle by ctrl::fillShadowAngle
    var globalLightColor by ctrl::globalLightColor
    var linkShadowColorToGlobalLight by ctrl::linkShadowColorToGlobalLight
    var shadowColor by ctrl::shadowColor

                Text(
                    "Luz global",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Una sola \"posición del sol\" que puede gobernar el ángulo de la sombra proyectada y de la sombra de relleno a la vez — evita el error típico de tener cada sombra mirando hacia un lado distinto. No afecta nada mientras ambos vínculos abajo estén apagados.",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )

                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Indicador visual de "posición del sol": un punto
                    // que gira alrededor del centro siguiendo
                    // [globalLightAngle] — puramente decorativo/de
                    // referencia, no participa del cálculo.
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer { rotationZ = globalLightAngle }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BrandPurpleLight)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                }

                LabeledSlider(
                    label = "Ángulo de la luz global",
                    value = globalLightAngle,
                    range = 0f..360f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) {
                    globalLightAngle = it
                    if (linkShadowToGlobalLight) shadowAngle = it
                    if (linkFillShadowToGlobalLight) fillShadowAngle = (it + 180f) % 360f
                    onChanged()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        "Vincular sombra proyectada",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkShadowToGlobalLight,
                        onCheckedChange = {
                            linkShadowToGlobalLight = it
                            if (it) { shadowAngle = globalLightAngle }
                            onChanged()
                        },
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    Text(
                        "Vincular sombra de relleno (opuesto)",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkFillShadowToGlobalLight,
                        onCheckedChange = {
                            linkFillShadowToGlobalLight = it
                            if (it) { fillShadowAngle = (globalLightAngle + 180f) % 360f }
                            onChanged()
                        },
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "La sombra de contacto no tiene ángulo propio (siempre queda centrada bajo el pie), así que no participa de este vínculo",
                    color = Color.White.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                ColorSwatchPickerButton(
                    label = "Color de la luz",
                    colorArgb = globalLightColor,
                    enabled = true
                ) { c ->
                    globalLightColor = c
                    if (linkShadowColorToGlobalLight) {
                        shadowColor = com.yeivikas.olyzecs.engine.effects.ImageEffects.deriveComplementaryShadowColor(c)
                    }
                    onChanged()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        "Vincular color de sombra (complementario)",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkShadowColorToGlobalLight,
                        onCheckedChange = {
                            linkShadowColorToGlobalLight = it
                            if (it) {
                                shadowColor = com.yeivikas.olyzecs.engine.effects.ImageEffects.deriveComplementaryShadowColor(globalLightColor)
                            }
                            onChanged()
                        },
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "Recalcula el color de la sombra proyectada como el complementario de \"Color de la luz\" — una luz cálida siempre produce sombras que se leen frías, y viceversa: el mismo contraste de temperatura de cualquier set de fotografía real",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
}
@Composable
private fun EffectsCategorySombraRelleno(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var fillShadowIntensity by ctrl::fillShadowIntensity
    var fillShadowBlur by ctrl::fillShadowBlur
    var fillShadowDistance by ctrl::fillShadowDistance
    var fillShadowScale by ctrl::fillShadowScale
    var fillShadowAngle by ctrl::fillShadowAngle
    var fillShadowColor by ctrl::fillShadowColor
    var linkFillShadowToGlobalLight by ctrl::linkFillShadowToGlobalLight
    var globalLightAngle by ctrl::globalLightAngle

                Text(
                    "Sombra de relleno",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Una segunda sombra, más suave y en otro ángulo, que simula la luz rebotada/ambiente — evita que el sujeto se vea con una sola sombra dura y plana",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = fillShadowIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { fillShadowIntensity = it; onChanged() }

                val fillShadowControlsEnabled = fillShadowIntensity > 0.001f

                LabeledSlider(
                    label = "Difuminado",
                    value = fillShadowBlur,
                    range = 0f..1f,
                    enabled = fillShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { fillShadowBlur = it; onChanged() }

                LabeledSlider(
                    label = "Distancia",
                    value = fillShadowDistance,
                    range = 0f..1f,
                    enabled = fillShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { fillShadowDistance = it; onChanged() }

                LabeledSlider(
                    label = "Escala",
                    value = fillShadowScale,
                    range = 0.4f..2f,
                    enabled = fillShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { fillShadowScale = it; onChanged() }

                LabeledSlider(
                    label = "Ángulo",
                    value = fillShadowAngle,
                    range = 0f..360f,
                    enabled = fillShadowControlsEnabled && !linkFillShadowToGlobalLight,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { fillShadowAngle = it; onChanged() }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Vincular a Luz global (opuesto)",
                        color = if (fillShadowControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkFillShadowToGlobalLight,
                        onCheckedChange = {
                            linkFillShadowToGlobalLight = it
                            if (it) { fillShadowAngle = (globalLightAngle + 180f) % 360f }
                            onChanged()
                        },
                        enabled = fillShadowControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "Sigue automáticamente el lado opuesto a la Luz global (+180°) — simula luz rebotada, criterio de iluminación de 3 puntos",
                    color = Color.White.copy(alpha = if (fillShadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                ColorSwatchPickerButton(
                    label = "Color de relleno",
                    colorArgb = fillShadowColor,
                    enabled = fillShadowControlsEnabled
                ) { c -> fillShadowColor = c; onChanged() }
}
@Composable
private fun EffectsCategorySombraContacto(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var contactShadowIntensity by ctrl::contactShadowIntensity
    var contactShadowSize by ctrl::contactShadowSize
    var contactShadowBlur by ctrl::contactShadowBlur
    var contactShadowFalloff by ctrl::contactShadowFalloff
    var contactShadowColor by ctrl::contactShadowColor
    var contactShadowNoise by ctrl::contactShadowNoise
    var groundOcclusionIntensity by ctrl::groundOcclusionIntensity

                Text(
                    "Sombra de contacto",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Mancha corta y suave justo en el pie del sujeto, para \"anclarlo\" al piso — independiente de la sombra proyectada de arriba",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = contactShadowIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowIntensity = it; onChanged() }

                val contactShadowControlsEnabled = contactShadowIntensity > 0.001f

                LabeledSlider(
                    label = "Tamaño",
                    value = contactShadowSize,
                    range = 0.1f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowSize = it; onChanged() }

                LabeledSlider(
                    label = "Difuminado",
                    value = contactShadowBlur,
                    range = 0f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowBlur = it; onChanged() }

                LabeledSlider(
                    label = "Curva de caída (falloff)",
                    value = contactShadowFalloff,
                    range = 0f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowFalloff = it; onChanged() }
                Text(
                    "Bajo = caída lenta y extendida, alto = núcleo denso que corta rápido — controla la FORMA del degradado, no solo su difuminado",
                    color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Grano / textura",
                    value = contactShadowNoise,
                    range = 0f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowNoise = it; onChanged() }
                Text(
                    "Misma idea que el grano de la sombra proyectada y del reflejo, aplicada acá: rompe la mancha de contacto perfectamente lisa",
                    color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                ColorSwatchPickerButton(
                    label = "Color de contacto",
                    colorArgb = contactShadowColor,
                    enabled = contactShadowControlsEnabled
                ) { c -> contactShadowColor = c; onChanged() }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Oclusión ambiental (sobre el sujeto)",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "A diferencia de todo lo de arriba, esto oscurece al SUJETO mismo (no a su sombra) justo donde toca el piso — lo \"ancla\" en vez de dejarlo flotando",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = groundOcclusionIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { groundOcclusionIntensity = it; onChanged() }
                Text(
                    "Detecta el punto de apoyo real de cada columna del sujeto (no un borde parejo) — funciona igual de bien con un contorno irregular",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Puntos de apoyo múltiples",
                        color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.55f else 0.3f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = contactShadowControlsEnabled) {
                                ctrl.contactShadowPoints.add(
                                    com.yeivikas.olyzecs.engine.effects.ContactShadowPoint(xOffset = 0f)
                                )
                                onChanged()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.75f else 0.3f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Agregar punto",
                            color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.75f else 0.3f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    "Para objetos con varios puntos de apoyo (una silla, un trípode) — cada punto agrega SU PROPIA mancha, con posición, tamaño e intensidad independientes. Sin puntos: una única mancha centrada (comportamiento clásico)",
                    color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                ctrl.contactShadowPoints.forEachIndexed { index, point ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Punto ${index + 1}",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = {
                                    ctrl.contactShadowPoints.removeAt(index)
                                    onChanged()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = "Quitar punto",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        LabeledSlider(
                            label = "Posición horizontal",
                            value = point.xOffset,
                            range = -1.5f..1.5f,
                            enabled = contactShadowControlsEnabled,
                            valueLabel = { "${(it * 100).roundToInt()}%" },
                            // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                            // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                            // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                            // `displayScale` acá, escribir a mano "83" en un control interno
                            // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                            // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                            // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                            // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                            // `displayScale = 100f` hace que el campo de edición manual (y su
                            // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                            // y tipea (0-100, o -100..100), no en la escala cruda interna.
                            displayScale = 100f
                        ) { v ->
                            ctrl.contactShadowPoints[index] = point.copy(xOffset = v)
                            onChanged()
                        }
                        LabeledSlider(
                            label = "Tamaño (relativo)",
                            value = point.sizeScale,
                            range = 0.2f..2f,
                            enabled = contactShadowControlsEnabled,
                            valueLabel = { "${(it * 100).roundToInt()}%" },
                            // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                            // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                            // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                            // `displayScale` acá, escribir a mano "83" en un control interno
                            // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                            // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                            // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                            // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                            // `displayScale = 100f` hace que el campo de edición manual (y su
                            // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                            // y tipea (0-100, o -100..100), no en la escala cruda interna.
                            displayScale = 100f
                        ) { v ->
                            ctrl.contactShadowPoints[index] = point.copy(sizeScale = v)
                            onChanged()
                        }
                        LabeledSlider(
                            label = "Intensidad (relativa)",
                            value = point.intensityScale,
                            range = 0f..1.5f,
                            enabled = contactShadowControlsEnabled,
                            valueLabel = { "${(it * 100).roundToInt()}%" },
                            // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                            // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                            // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                            // `displayScale` acá, escribir a mano "83" en un control interno
                            // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                            // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                            // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                            // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                            // `displayScale = 100f` hace que el campo de edición manual (y su
                            // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                            // y tipea (0-100, o -100..100), no en la escala cruda interna.
                            displayScale = 100f
                        ) { v ->
                            ctrl.contactShadowPoints[index] = point.copy(intensityScale = v)
                            onChanged()
                        }
                    }
                }
}
@Composable
private fun EffectsCategoryLightWrap(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var lightWrapIntensity by ctrl::lightWrapIntensity
    var lightWrapWidth by ctrl::lightWrapWidth
    var lightWrapAngle by ctrl::lightWrapAngle
    var lightWrapDirectionality by ctrl::lightWrapDirectionality
    var lightWrapColor by ctrl::lightWrapColor

                Text(
                    "Light wrap",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Filtra un color de luz ambiente hacia adentro del borde del sujeto, para que se sienta integrado y no \"pegado\" encima del fondo",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = lightWrapIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { lightWrapIntensity = it; onChanged() }

                val lightWrapControlsEnabled = lightWrapIntensity > 0.001f

                LabeledSlider(
                    label = "Ancho",
                    value = lightWrapWidth,
                    range = 0f..1f,
                    enabled = lightWrapControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { lightWrapWidth = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color de la luz",
                    colorArgb = lightWrapColor,
                    enabled = lightWrapControlsEnabled
                ) { c -> lightWrapColor = c; onChanged() }

                LabeledSlider(
                    label = "Direccionalidad",
                    value = lightWrapDirectionality,
                    range = 0f..1f,
                    enabled = lightWrapControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { lightWrapDirectionality = it; onChanged() }
                Text(
                    "0% = envuelve todo el contorno por igual (clásico). Al subirlo, se concentra en el lado que mira hacia el ángulo de abajo y se atenúa del lado contrario — como una luz de ambiente real, que solo ilumina el lado que la enfrenta",
                    color = Color.White.copy(alpha = if (lightWrapControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Ángulo de la luz",
                    value = lightWrapAngle,
                    range = 0f..360f,
                    enabled = lightWrapControlsEnabled && lightWrapDirectionality > 0.001f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { lightWrapAngle = it; onChanged() }
}
@Composable
private fun EffectsCategoryPresets(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var shadowIntensity by ctrl::shadowIntensity
    var shadowBlur by ctrl::shadowBlur
    var shadowScale by ctrl::shadowScale
    var shadowNoise by ctrl::shadowNoise
    var shadowDistance by ctrl::shadowDistance
    var shadowAngle by ctrl::shadowAngle
    var shadowSkew by ctrl::shadowSkew
    var shadowPerspectiveAmount by ctrl::shadowPerspectiveAmount
    var shadowFadeByDistance by ctrl::shadowFadeByDistance
    var shadowBlendMultiply by ctrl::shadowBlendMultiply
    var fillShadowIntensity by ctrl::fillShadowIntensity
    var fillShadowBlur by ctrl::fillShadowBlur
    var fillShadowDistance by ctrl::fillShadowDistance
    var fillShadowAngle by ctrl::fillShadowAngle
    var fillShadowColor by ctrl::fillShadowColor
    var fillShadowScale by ctrl::fillShadowScale
    var contactShadowIntensity by ctrl::contactShadowIntensity
    var contactShadowSize by ctrl::contactShadowSize
    var contactShadowBlur by ctrl::contactShadowBlur
    var contactShadowColor by ctrl::contactShadowColor
    var contactShadowFalloff by ctrl::contactShadowFalloff
    var reflectionIntensity by ctrl::reflectionIntensity
    var reflectionGap by ctrl::reflectionGap
    var reflectionLength by ctrl::reflectionLength
    var reflectionBlur by ctrl::reflectionBlur
    var reflectionNoise by ctrl::reflectionNoise
    var reflectionSkew by ctrl::reflectionSkew
    var reflectionTintIntensity by ctrl::reflectionTintIntensity
    var reflectionTintColor by ctrl::reflectionTintColor
    var reflectionEdgeFade by ctrl::reflectionEdgeFade
    var reflectionRippleIntensity by ctrl::reflectionRippleIntensity
    var reflectionRippleScale by ctrl::reflectionRippleScale
    var reflectionOpacityCurve by ctrl::reflectionOpacityCurve
    var reflectionPerspective by ctrl::reflectionPerspective
    var reflectionProgressiveBlur by ctrl::reflectionProgressiveBlur
    var lightWrapIntensity by ctrl::lightWrapIntensity
    var lightWrapWidth by ctrl::lightWrapWidth
    var lightWrapColor by ctrl::lightWrapColor
    var lightWrapAngle by ctrl::lightWrapAngle
    var lightWrapDirectionality by ctrl::lightWrapDirectionality

                Text(
                    "Preset profesional",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Combina sombra proyectada + sombra de contacto + reflejo + light wrap con valores balanceados de estudio, en un toque",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        shadowIntensity = 0.55f
                        shadowBlur = 0.5f
                        shadowDistance = 0.15f
                        shadowAngle = 90f
                        shadowSkew = 0f
                        shadowFadeByDistance = 0.25f
                        shadowBlendMultiply = true
                        fillShadowIntensity = 0.25f
                        fillShadowBlur = 0.7f
                        fillShadowDistance = 0.2f
                        fillShadowAngle = 315f
                        fillShadowColor = android.graphics.Color.rgb(30, 34, 48)
                        contactShadowIntensity = 0.6f
                        contactShadowSize = 0.42f
                        contactShadowBlur = 0.35f
                        ctrl.contactShadowPoints.clear()
                        reflectionIntensity = 0.45f
                        reflectionGap = 0.02f
                        reflectionLength = 0.55f
                        reflectionBlur = 0.22f
                        reflectionSkew = 0f
                        reflectionTintIntensity = 0.18f
                        reflectionTintColor = android.graphics.Color.rgb(58, 110, 150)
                        reflectionEdgeFade = 0.3f
                        reflectionRippleIntensity = 0f
                        reflectionRippleScale = 0.5f
                        lightWrapIntensity = 0.35f
                        lightWrapWidth = 0.4f
                        lightWrapColor = android.graphics.Color.rgb(255, 244, 214)
                        onChanged()
                    }) {
                        Text("Piso reflectante ✨")
                    }
                    OutlinedButton(onClick = {
                        // Estudio dramático — luz de contraste alto, estilo
                        // "rim light" de un solo lado. Sombra proyectada
                        // larga y dura con perspectiva de punto de fuga
                        // real (no shear), fill mínimo para maximizar el
                        // contraste, sombra de contacto concentrada y sin
                        // reflejo de piso (un estudio dramático suele
                        // tener piso mate, no pulido). El light wrap se
                        // concentra en un solo borde (direccionalidad
                        // alta) simulando un solo foco detrás/lateral del
                        // sujeto, en tono frío para reforzar el drama.
                        shadowIntensity = 0.78f
                        shadowBlur = 0.32f
                        shadowScale = 1.08f
                        shadowNoise = 0f
                        shadowDistance = 0.3f
                        shadowAngle = 115f
                        shadowSkew = 0f
                        shadowPerspectiveAmount = 0.6f
                        shadowFadeByDistance = 0.15f
                        shadowBlendMultiply = true
                        fillShadowIntensity = 0.08f
                        fillShadowBlur = 0.85f
                        fillShadowDistance = 0.14f
                        fillShadowAngle = 295f
                        fillShadowColor = android.graphics.Color.rgb(18, 20, 32)
                        fillShadowScale = 1f
                        contactShadowIntensity = 0.72f
                        contactShadowSize = 0.32f
                        contactShadowBlur = 0.22f
                        contactShadowColor = android.graphics.Color.BLACK
                        contactShadowFalloff = 0.8f
                        ctrl.contactShadowPoints.clear()
                        reflectionIntensity = 0f
                        lightWrapIntensity = 0.55f
                        lightWrapWidth = 0.22f
                        lightWrapColor = android.graphics.Color.rgb(198, 218, 255)
                        lightWrapAngle = 45f
                        lightWrapDirectionality = 0.85f
                        onChanged()
                    }) {
                        Text("Estudio dramático 🎭")
                    }
                    OutlinedButton(onClick = {
                        // Luz suave de producto — envolvente y pareja,
                        // sin sombras duras ni ángulos marcados. Pensado
                        // para fotografía de producto tipo softbox: sombra
                        // corta y muy difusa con leve perspectiva de piso,
                        // fill cálido generoso para levantar las sombras,
                        // sombra de contacto ancha y gradual (nunca
                        // concentrada), reflejo sutil con difuminado
                        // progresivo (más nítido cerca del pie, más suave
                        // lejos, como una mesa de estudio) y light wrap
                        // parejo en todo el contorno, sin direccionalidad,
                        // en tono cálido neutro.
                        shadowIntensity = 0.32f
                        shadowBlur = 0.8f
                        shadowScale = 1f
                        shadowNoise = 0f
                        shadowDistance = 0.09f
                        shadowAngle = 90f
                        shadowSkew = 0f
                        shadowPerspectiveAmount = 0.25f
                        shadowFadeByDistance = 0.35f
                        shadowBlendMultiply = true
                        fillShadowIntensity = 0.3f
                        fillShadowBlur = 0.9f
                        fillShadowDistance = 0.1f
                        fillShadowAngle = 270f
                        fillShadowColor = android.graphics.Color.rgb(58, 52, 44)
                        fillShadowScale = 1f
                        contactShadowIntensity = 0.4f
                        contactShadowSize = 0.58f
                        contactShadowBlur = 0.62f
                        contactShadowColor = android.graphics.Color.BLACK
                        contactShadowFalloff = 0.22f
                        ctrl.contactShadowPoints.clear()
                        reflectionIntensity = 0.28f
                        reflectionGap = 0f
                        reflectionLength = 0.4f
                        reflectionBlur = 0.5f
                        reflectionNoise = 0.35f
                        reflectionSkew = 0f
                        reflectionTintIntensity = 0f
                        reflectionEdgeFade = 0.5f
                        reflectionRippleIntensity = 0f
                        reflectionRippleScale = 0.5f
                        reflectionOpacityCurve = 0.5f
                        reflectionPerspective = 1f
                        reflectionProgressiveBlur = 0.6f
                        lightWrapIntensity = 0.4f
                        lightWrapWidth = 0.5f
                        lightWrapColor = android.graphics.Color.rgb(255, 248, 235)
                        lightWrapAngle = 90f
                        lightWrapDirectionality = 0f
                        onChanged()
                    }) {
                        Text("Luz suave de producto 💡")
                    }
                    OutlinedButton(onClick = {
                        shadowIntensity = 0f
                        fillShadowIntensity = 0f
                        contactShadowIntensity = 0f
                        ctrl.contactShadowPoints.clear()
                        reflectionIntensity = 0f
                        reflectionNoise = 0f
                        lightWrapIntensity = 0f
                        onChanged()
                    }) {
                        Text("Quitar sombra/reflejo")
                    }
                }
}
@Composable
private fun EffectsCategoryFondo(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var blur by ctrl::blur
    var edgeFeather by ctrl::edgeFeather

                // "Difuminar": difumina el FONDO alrededor del sujeto
                // (estilo retrato/profundidad de campo) — el sujeto y su
                // sombra quedan siempre nítidos, sin importar el valor de
                // este slider. Ver el comentario grande de
                // ImageEffects.applyBackgroundBlur para el detalle
                // técnico completo.
                Text(
                    "Difuminar afecta solo el fondo — el sujeto y su sombra quedan siempre nítidos",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Difuminar (fondo)",
                    value = blur,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { blur = it; onChanged() }

                LabeledSlider(
                    label = "Suavizado de contorno",
                    value = edgeFeather,
                    range = 0f..1f,
                    enabled = blur > 0.001f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { edgeFeather = it; onChanged() }
}

@Composable
// A PEDIDO DEL USUARIO — "Básicos" mezclaba dos funciones sin relación
// entre sí en la misma ventana: difuminar el FONDO (arriba, en
// [EffectsCategoryFondo]) y corrección de color del SUJETO (acá). Se
// separaron en dos categorías propias porque, tal como quedó claro en la
// charla, no tienen nada que ver una con la otra — ninguno de estos 5
// sliders toca el fondo, los 5 son ajustes sobre el sujeto mismo (ver los
// comentarios de `sharpen`/`hue` en `ImageEffectsParams`: "nitidez del
// sujeto", "rotación de matiz sobre TODO el sujeto").
//
// ACTUALIZACIÓN posterior: esta función YA NO se llama solo desde
// EffectsPanel (pestaña "Efecto", donde quedó inalcanzable — ver
// EditImageEffectsMenu) — ahora vive principalmente en
// [ColorBasicoFloatingWindow], colgada de la opción "Básico" del menú
// "Color". Sigue siendo el MISMO composable, sin un solo cambio acá
// adentro: solo cambió quién la llama y desde dónde.
private fun EffectsCategoryColor(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var sharpen by ctrl::sharpen
    var saturation by ctrl::saturation
    var brightness by ctrl::brightness
    var contrast by ctrl::contrast
    var hue by ctrl::hue

                LabeledSlider(
                    label = "Nitidez",
                    value = sharpen,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { sharpen = it; onChanged() }

                LabeledSlider(
                    label = "Saturación",
                    value = saturation,
                    range = 0f..2f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { saturation = it; onChanged() }

                LabeledSlider(
                    label = "Brillo",
                    value = brightness,
                    range = -1f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%".let { s -> if (it > 0f) "+$s" else s } },
                    displayScale = 100f
                ) { brightness = it; onChanged() }

                LabeledSlider(
                    label = "Contraste",
                    value = contrast,
                    range = 0f..2f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contrast = it; onChanged() }

                LabeledSlider(
                    label = "Tono",
                    value = hue,
                    range = 0f..360f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { hue = it; onChanged() }
}

@Composable
private fun EffectsCategoryContorno(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var outlineIntensity by ctrl::outlineIntensity
    var outlineColor by ctrl::outlineColor
    var outlineColor2 by ctrl::outlineColor2
    var outlineGradientEnabled by ctrl::outlineGradientEnabled
    var outlineFeather by ctrl::outlineFeather
    var outlinePosition by ctrl::outlinePosition

                Text(
                    "Contorno",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )

                LabeledSlider(
                    label = "Grosor",
                    value = outlineIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { outlineIntensity = it; onChanged() }

                val outlineControlsEnabled = outlineIntensity > 0.001f

                // Posición del trazo — mismo control "Position" de
                // cualquier Stroke profesional (Photoshop/Affinity/
                // Figma): antes SOLO existía "Afuera".
                Text(
                    "Posición",
                    color = Color.White.copy(alpha = if (outlineControlsEnabled) 0.55f else 0.25f),
                    style = MaterialTheme.typography.labelSmall
                )
                ThreeWayToggle(
                    labels = listOf("Afuera", "Centro", "Adentro"),
                    selectedIndex = when (outlinePosition) {
                        com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.OUTSIDE -> 0
                        com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.CENTER -> 1
                        com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.INSIDE -> 2
                    },
                    enabled = outlineControlsEnabled
                ) { index ->
                    outlinePosition = when (index) {
                        0 -> com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.OUTSIDE
                        1 -> com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.CENTER
                        else -> com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.INSIDE
                    }
                    onChanged()
                }

                LabeledSlider(
                    label = "Difuminado del borde",
                    value = outlineFeather,
                    range = 0f..1f,
                    enabled = outlineControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    displayScale = 100f
                ) { outlineFeather = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color del contorno",
                    colorArgb = outlineColor,
                    enabled = outlineControlsEnabled
                ) { c -> outlineColor = c; onChanged() }

                // Degradado del contorno (dos colores) — A PEDIDO DEL
                // USUARIO. Mismo patrón de `Switch` que "Vincular a Luz
                // global" en Sombra: en apagado (default) el segundo
                // color queda oculto y el contorno sale sólido de
                // [outlineColor], EXACTO como antes.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Degradado (2 colores)",
                        color = if (outlineControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = outlineGradientEnabled,
                        onCheckedChange = {
                            outlineGradientEnabled = it
                            onChanged()
                        },
                        enabled = outlineControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                if (outlineGradientEnabled) {
                    ColorSwatchPickerButton(
                        label = "Segundo color (punta del trazo)",
                        colorArgb = outlineColor2,
                        enabled = outlineControlsEnabled
                    ) { c -> outlineColor2 = c; onChanged() }
                }
}

/**
 * Selector de 3 opciones en una sola fila (mismo estilo visual que
 * [DistortionDirectionToggle], generalizado a N etiquetas) — usado por
 * "Posición" del contorno (Afuera/Centro/Adentro).
 */
@Composable
private fun ThreeWayToggle(
    labels: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active && enabled) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = when {
                        !enabled -> Color.White.copy(alpha = 0.25f)
                        active -> Color.White
                        else -> Color.White.copy(alpha = 0.5f)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EffectsCategoryResplandor(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var glowIntensity by ctrl::glowIntensity
    var glowBlur by ctrl::glowBlur
    var glowColor by ctrl::glowColor
    var glowColor2 by ctrl::glowColor2
    var glowGradientEnabled by ctrl::glowGradientEnabled
    var glowBlendMode by ctrl::glowBlendMode
    var glowSpread by ctrl::glowSpread
    var glowDistance by ctrl::glowDistance
    var glowAngle by ctrl::glowAngle

                Text(
                    "Resplandor",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )

                LabeledSlider(
                    label = "Intensidad",
                    value = glowIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { glowIntensity = it; onChanged() }

                val glowControlsEnabled = glowIntensity > 0.001f

                LabeledSlider(
                    label = "Difuminado del resplandor",
                    value = glowBlur,
                    range = 0f..1f,
                    enabled = glowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { glowBlur = it; onChanged() }

                // Spread/Choke — mismo control "Spread" que ya tiene
                // "Sombra" (ver ImageEffectsParams.shadowSpread), ahora
                // también acá: núcleo más sólido/definido y caída más
                // corta, en vez de un halo parejo de punta a punta.
                LabeledSlider(
                    label = "Expansión (spread)",
                    value = glowSpread,
                    range = 0f..1f,
                    enabled = glowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    displayScale = 100f
                ) { glowSpread = it; onChanged() }

                // Distancia/ángulo — antes el halo salía SIEMPRE
                // centrado; con esto se puede correr hacia un lado
                // (mismo mecanismo que la sombra proyectada).
                LabeledSlider(
                    label = "Distancia",
                    value = glowDistance,
                    range = 0f..1f,
                    enabled = glowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    displayScale = 100f
                ) { glowDistance = it; onChanged() }

                LabeledSlider(
                    label = "Ángulo",
                    value = glowAngle,
                    range = 0f..360f,
                    enabled = glowControlsEnabled && glowDistance > 0.001f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { glowAngle = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color del resplandor",
                    colorArgb = glowColor,
                    enabled = glowControlsEnabled
                ) { c -> glowColor = c; onChanged() }

                // Degradado del resplandor (dos colores) — A PEDIDO DEL
                // USUARIO. Mismo patrón EXACTO de `Switch` que "Degradado
                // (2 colores)" en Contorno (ver [EffectsCategoryContorno]):
                // apagado (default) = un solo color sólido, sin cambios.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Degradado (2 colores)",
                        color = if (glowControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = glowGradientEnabled,
                        onCheckedChange = {
                            glowGradientEnabled = it
                            onChanged()
                        },
                        enabled = glowControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                if (glowGradientEnabled) {
                    ColorSwatchPickerButton(
                        label = "Segundo color (punta del halo)",
                        colorArgb = glowColor2,
                        enabled = glowControlsEnabled
                    ) { c -> glowColor2 = c; onChanged() }
                }

                // Modo de mezcla — A PEDIDO DEL USUARIO ("le falta... una
                // opción de blend mode (Screen/Add/Lighten) — hoy solo
                // hay un modo de mezcla"). "Normal" preserva el
                // comportamiento de siempre; los otros tres son los
                // modos estándar de cualquier "Outer Glow" profesional
                // (ver [com.yeivikas.olyzecs.engine.effects.GlowBlendMode]
                // para el detalle matemático de cada uno). Reutiliza
                // [ThreeWayToggle] (ya generalizado a N etiquetas, pese al
                // nombre) para no duplicar el selector segmentado que ya
                // usa "Posición" en Contorno.
                Text(
                    "Modo de mezcla",
                    color = Color.White.copy(alpha = if (glowControlsEnabled) 0.55f else 0.25f),
                    style = MaterialTheme.typography.labelSmall
                )
                ThreeWayToggle(
                    labels = listOf("Normal", "Screen", "Add", "Lighten"),
                    selectedIndex = when (glowBlendMode) {
                        com.yeivikas.olyzecs.engine.effects.GlowBlendMode.NORMAL -> 0
                        com.yeivikas.olyzecs.engine.effects.GlowBlendMode.SCREEN -> 1
                        com.yeivikas.olyzecs.engine.effects.GlowBlendMode.ADD -> 2
                        com.yeivikas.olyzecs.engine.effects.GlowBlendMode.LIGHTEN -> 3
                    },
                    enabled = glowControlsEnabled
                ) { index ->
                    glowBlendMode = when (index) {
                        0 -> com.yeivikas.olyzecs.engine.effects.GlowBlendMode.NORMAL
                        1 -> com.yeivikas.olyzecs.engine.effects.GlowBlendMode.SCREEN
                        2 -> com.yeivikas.olyzecs.engine.effects.GlowBlendMode.ADD
                        else -> com.yeivikas.olyzecs.engine.effects.GlowBlendMode.LIGHTEN
                    }
                    onChanged()
                }
}









/**
 * Fila de sub-pestañas (chips) de la pestaña "Efectos" — una por cada
 * categoría de controles (Básicos, Contorno, Resplandor, Presets,
 * Sombra, Sombra relleno, Sombra contacto, Reflejo, Light wrap).
 * Puramente de navegación: no toca ningún valor de efecto, solo decide
 * qué grupo de controles se muestra debajo. Deslizable horizontalmente
 * porque no entran todas las categorías en el ancho de pantalla.
 */
@Composable
private fun EffectsCategoryTabs(
    categories: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEachIndexed { index, label ->
            val isActive = index == selected
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        if (isActive) BrandPurpleLight.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isActive) BrandPurpleLight else Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(17.dp)
                    )
                    .clickable { onSelected(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Botón compacto de "muestra de color" (swatch circular + etiqueta) que
 * abre, al tocarlo, un mini-popup con [ColorWheelPicker] — mismo
 * componente que ya usa la pestaña "Recolor" (ver [LayerColorEditPanel]),
 * reutilizado tal cual acá para no duplicar la rueda de color. Pensado
 * para colores "secundarios" de un efecto (sombra, contorno, resplandor)
 * donde una rueda grande siempre visible ocuparía demasiado espacio del
 * panel — a diferencia de "Recolor", donde el color ES el efecto
 * principal y por eso ahí la rueda queda fija y grande.
 *
 * El HSV interno del popup se deriva de [colorArgb] cada vez que ese
 * color cambia desde afuera (`remember(colorArgb)`) — la fuente de
 * verdad para el resto de la app sigue siendo siempre el Int ARGB en
 * [ImageEffectsParams], nunca un HSV propio de este botón que pudiera
 * desincronizarse.
 */
@Composable
private fun ColorSwatchPickerButton(
    label: String,
    colorArgb: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onColorChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val initialHsv = remember(colorArgb) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(colorArgb, out)
        out
    }
    var wheelHue by remember(colorArgb) { mutableStateOf(initialHsv[0]) }
    var wheelSat by remember(colorArgb) { mutableStateOf(initialHsv[1]) }
    var wheelVal by remember(colorArgb) { mutableStateOf(initialHsv[2]) }

    fun applyCurrentColor() {
        onColorChange(android.graphics.Color.HSVToColor(floatArrayOf(wheelHue, wheelSat, wheelVal)))
    }

    // BUG REAL corregido acá: la mayoría de estos colores arrancan en
    // NEGRO por defecto (shadowColor, contactShadowColor, etc.), y negro
    // en HSV es Value=0 — con V=0, HSVToColor(h, s, 0) da negro sin
    // importar qué matiz/saturación se elija (V=0 anula todo lo demás).
    // Resultado: tocar la rueda de color no cambiaba nada visualmente
    // hasta que el usuario ADEMÁS se acordaba de subir "Brillo" a mano,
    // cada vez, en cada uno de estos selectores. Cualquier selector de
    // color nativo (iOS/Android) evita esto: si el brillo está "atascado"
    // en el piso cuando se toca el anillo de matiz/saturación, lo levanta
    // solo a un valor utilizable — acá se replica ese comportamiento.
    fun onWheelHueSatChange(h: Float, s: Float) {
        wheelHue = h
        wheelSat = s
        if (wheelVal < 0.05f) {
            wheelVal = 1f
        }
        applyCurrentColor()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = if (enabled) 0.85f else 0.35f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(colorArgb))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .alpha(if (enabled) 1f else 0.35f)
                .then(
                    if (enabled) Modifier.clickable { expanded = true } else Modifier
                )
        )
    }

    if (expanded && enabled) {
        Popup(
            popupPositionProvider = BelowAnchorCenteredPopupPositionProvider(gapPx = 8),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true)
        ) {
            Surface(
                modifier = Modifier
                    .width(216.dp)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp)),
                color = SurfaceTintedElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ColorWheelPicker(
                            hue = wheelHue,
                            saturation = wheelSat,
                            brightness = wheelVal,
                            onColorChange = { h, s -> onWheelHueSatChange(h, s) },
                            modifier = Modifier.size(160.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LabeledSlider(
                        label = "Brillo",
                        value = wheelVal,
                        range = 0f..1f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                        // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                        // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                        // `displayScale` acá, escribir a mano "83" en un control interno
                        // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                        // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                        // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                        // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                        // `displayScale = 100f` hace que el campo de edición manual (y su
                        // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                        // y tipea (0-100, o -100..100), no en la escala cruda interna.
                        displayScale = 100f
                    ) { v ->
                        wheelVal = v
                        applyCurrentColor()
                    }
                }
            }
        }
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
 * (ver `EffectsPanel.effectsTopCategories`, y [EditImageToolsHeader]
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
private fun DistortionPanel(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    distortionBridge: DistortionGestureBridge,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var fullBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }

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

    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var liveRenderJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var lastLiveRenderAtMs by remember(layer.id) { mutableStateOf(0L) }

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
        fullBitmap = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = ImageDecoding.NO_LIMIT)
        }
    }

    fun scheduleLivePreview() {
        val small = liveBitmap
        val currentField = field
        if (small == null || currentField == null) return
        val now = System.currentTimeMillis()
        if (now - lastLiveRenderAtMs < 60L) return
        lastLiveRenderAtMs = now
        liveRenderJob?.cancel()
        val snapshot = currentField.snapshot()
        liveRenderJob = coroutineScope.launch {
            val rendered = viewModel.renderDistortion(small, snapshot, small.width, small.height)
            viewModel.previewLayerRecolor(layer.id, rendered)
        }
    }

    fun scheduleCommit() {
        val currentField = field ?: return
        commitJob?.cancel()
        commitJob = coroutineScope.launch {
            delay(500)
            val source = fullBitmap ?: liveBitmap ?: return@launch
            val snapshot = currentField.snapshot()
            val rendered = viewModel.renderDistortion(source, snapshot, source.width, source.height)
            viewModel.commitLayerRecolor(layer.id, rendered, source = "distortion")
        }
    }

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
        scheduleLivePreview()
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
        scheduleLivePreview()
    }

    fun endStroke() {
        if (!strokeActive) return
        strokeActive = false
        strokePrevU = null
        strokePrevV = null
        strokeAnchorU = null
        strokeAnchorV = null
        if (!freezeModeActive) scheduleCommit()
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
        scheduleLivePreview()
        scheduleCommit()
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
        scheduleLivePreview()
        scheduleCommit()
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
        scheduleLivePreview()
        scheduleCommit()
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
            scheduleLivePreview()
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
 * dentro de `effectsTopCategories` (ver EffectsPanel) — se preserva
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
 * de `effectsTopCategories` (ver EffectsPanel — se preserva tal cual
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
private fun ColorMenuRecolorIcon(tint: Color, iconSize: Dp = 16.dp) {
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
private fun ColorMenuBasicoIcon(tint: Color, iconSize: Dp = 16.dp) {
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
private fun Menu3DBasicCubeIcon(tint: Color, iconSize: Dp = 16.dp) {
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
 * ya usan [ShadowModuleIcon]/[ReflectionModuleIcon] del cajón Rack, para
 * que el mismo concepto se vea igual en toda la app.
 */
@Composable
private fun EffectMenuOutlineIcon(tint: Color, iconSize: Dp = 16.dp) {
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
private fun EffectMenuGlowIcon(tint: Color, iconSize: Dp = 16.dp) {
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
private fun EffectMenuShadowIcon(tint: Color, iconSize: Dp = 16.dp) {
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
private fun EffectMenuReflectionIcon(tint: Color, iconSize: Dp = 16.dp) {
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
private fun EffectMenuDistortionIcon(tint: Color, iconSize: Dp = 16.dp) {
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
    scaleYVal: Float = 1f
): List<Offset>? {
    if (boxWidthPx <= 0f || boxHeightPx <= 0f || layerWidthPx <= 0 || layerHeightPx <= 0) return null
    val viewportAspect = boxWidthPx / boxHeightPx
    val imageAspect = layerWidthPx.toFloat() / layerHeightPx.toFloat()

    val fitScaleX: Float
    val fitScaleY: Float
    if (imageAspect > viewportAspect) {
        fitScaleX = 2f
        fitScaleY = 2f * viewportAspect / imageAspect
    } else {
        fitScaleY = 2f
        fitScaleX = 2f * imageAspect / viewportAspect
    }
    // ARREGLADO: mismo motivo que en `hitTestLayerAt` — scaleXVal/scaleYVal
    // ahora pueden ser negativos (capa volteada). Si no se toma el
    // valor absoluto acá, un flip invertía qué esquina del marco de
    // selección caía "a la izquierda" o "a la derecha" en pantalla,
    // haciendo que el botón de eliminar/menú/manijas de estirar
    // saltaran de lado cada vez que la capa se voltea — confuso y nada
    // profesional. El marco de selección (y sus manijas) tiene que
    // quedarse SIEMPRE en el mismo lugar relativo a la pantalla,
    // volteada o no la imagen de adentro.
    val halfWidth = kotlin.math.abs(0.5f * fitScaleX * scaleVal * scaleXVal)
    val halfHeight = kotlin.math.abs(0.5f * fitScaleY * scaleVal * scaleYVal)
    val centerX = translateX * parallaxFactor
    val centerY = translateY * parallaxFactor
    val angleRad = Math.toRadians(rotationDeg.toDouble())
    val cosA = cos(angleRad).toFloat()
    val sinA = sin(angleRad).toFloat()

    // Esquinas en espacio local (sin rotar), orden: arriba-izq, arriba-der,
    // abajo-der, abajo-izq — para poder dibujar el contorno en un solo trazo.
    val localCorners = listOf(
        Offset(-halfWidth, halfHeight),
        Offset(halfWidth, halfHeight),
        Offset(halfWidth, -halfHeight),
        Offset(-halfWidth, -halfHeight)
    )
    return localCorners.map { local ->
        // Rotar (espacio local -> NDC ya rotado) y trasladar al centro real.
        val ndcX = local.x * cosA - local.y * sinA + centerX
        val ndcY = local.x * sinA + local.y * cosA + centerY
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
private fun clampedHandleSlots(
    corners: List<Offset>,
    canvasSize: Size,
    marginPx: Float
): Map<HandlePosition, Offset> {
    val topLeft = corners[0]
    val topRight = corners[1]
    val bottomRight = corners[2]
    val bottomLeft = corners[3]
    val raw = mapOf(
        HandlePosition.TOP_LEFT to topLeft,
        HandlePosition.TOP_MID to Offset((topLeft.x + topRight.x) / 2f, (topLeft.y + topRight.y) / 2f),
        HandlePosition.TOP_RIGHT to topRight,
        HandlePosition.RIGHT_MID to Offset((topRight.x + bottomRight.x) / 2f, (topRight.y + bottomRight.y) / 2f),
        HandlePosition.BOTTOM_RIGHT to bottomRight,
        HandlePosition.BOTTOM_MID to Offset((bottomRight.x + bottomLeft.x) / 2f, (bottomRight.y + bottomLeft.y) / 2f),
        HandlePosition.BOTTOM_LEFT to bottomLeft,
        HandlePosition.LEFT_MID to Offset((topLeft.x + bottomLeft.x) / 2f, (topLeft.y + bottomLeft.y) / 2f)
    )
    // Si todavía no hay un tamaño de canvas válido (layout sin medir),
    // se devuelven las posiciones crudas tal cual — no hay contra qué
    // recortar.
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return raw
    val minX = marginPx
    val maxX = (canvasSize.width - marginPx).coerceAtLeast(minX)
    val minY = marginPx
    val maxY = (canvasSize.height - marginPx).coerceAtLeast(minY)
    return raw.mapValues { (_, offset) ->
        Offset(
            x = offset.x.coerceIn(minX, maxX),
            y = offset.y.coerceIn(minY, maxY)
        )
    }
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
    scaleYVal: Float = 1f
): Offset? {
    if (boxWidthPx <= 0f || boxHeightPx <= 0f || layerWidthPx <= 0 || layerHeightPx <= 0) return null
    val viewportAspect = boxWidthPx / boxHeightPx
    val imageAspect = layerWidthPx.toFloat() / layerHeightPx.toFloat()

    val fitScaleX: Float
    val fitScaleY: Float
    if (imageAspect > viewportAspect) {
        fitScaleX = 2f
        fitScaleY = 2f * viewportAspect / imageAspect
    } else {
        fitScaleY = 2f
        fitScaleX = 2f * imageAspect / viewportAspect
    }
    val halfWidth = kotlin.math.abs(0.5f * fitScaleX * scaleVal * scaleXVal)
    val halfHeight = kotlin.math.abs(0.5f * fitScaleY * scaleVal * scaleYVal)
    if (halfWidth <= 1e-6f || halfHeight <= 1e-6f) return null

    val ndcX = (point.x / boxWidthPx) * 2f - 1f
    val ndcY = 1f - (point.y / boxHeightPx) * 2f
    val centerX = translateX * parallaxFactor
    val centerY = translateY * parallaxFactor
    val dx = ndcX - centerX
    val dy = ndcY - centerY
    val angleRad = Math.toRadians(-rotationDeg.toDouble())
    val cosA = cos(angleRad).toFloat()
    val sinA = sin(angleRad).toFloat()
    val localX = dx * cosA - dy * sinA
    val localY = dx * sinA + dy * cosA

    var u = (localX / halfWidth) * 0.5f + 0.5f
    var v = 0.5f - (localY / halfHeight) * 0.5f
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
    val viewportAspect = boxWidthPx / boxHeightPx

    // Del punto tocado (píxeles de pantalla, origen arriba-izquierda) a
    // coordenadas NDC (origen centro, rango -1..1, eje Y hacia arriba) —
    // el mismo espacio en el que vive la geometría que arma el GL.
    val ndcX = (tapOffset.x / boxWidthPx) * 2f - 1f
    val ndcY = 1f - (tapOffset.y / boxHeightPx) * 2f

    fun isHit(layer: Layer): Boolean {
        if (!layer.visible || layer.locked || layer.widthPx <= 0 || layer.heightPx <= 0) return false
        val frame = layer.cameraTrack.frameAt(playheadMs)
        val imageAspect = layer.widthPx.toFloat() / layer.heightPx.toFloat()

        val fitScaleX: Float
        val fitScaleY: Float
        if (imageAspect > viewportAspect) {
            fitScaleX = 2f
            fitScaleY = 2f * viewportAspect / imageAspect
        } else {
            fitScaleY = 2f
            fitScaleX = 2f * imageAspect / viewportAspect
        }
        // El quad base del GL mide 1x1 (-0.5..0.5) antes de escalar, así
        // que el semi-ancho/alto real en NDC es la mitad de
        // fitScale*scale*scaleX/scaleY (scaleX/scaleY = estirado
        // independiente de las manijas "estirar ancho"/"estirar alto",
        // 1f si no se tocaron — ver CameraFrame.kt).
        //
        // ARREGLADO: `frame.scaleX`/`frame.scaleY` ahora pueden ser
        // NEGATIVOS (capa volteada/flip horizontal o vertical — ver
        // manijas "rightMid"/"bottomMid" más arriba). Sin el abs() de
        // acá, un halfWidth/halfHeight negativo hacía que
        // `abs(localX) <= halfWidth` fuera SIEMPRE falso (un valor
        // absoluto nunca es ≤ a un número negativo), es decir: una capa
        // volteada quedaba imposible de tocar/seleccionar en el lienzo.
        // El signo de scaleX/scaleY solo importa para el flip visual
        // (motor GL); para el test de "¿el toque cae dentro de la
        // caja?" siempre hace falta la MAGNITUD.
        val halfWidth = kotlin.math.abs(0.5f * fitScaleX * frame.scale * frame.scaleX)
        val halfHeight = kotlin.math.abs(0.5f * fitScaleY * frame.scale * frame.scaleY)

        val centerX = frame.translateX * layer.parallaxFactor
        val centerY = frame.translateY * layer.parallaxFactor

        // Deshacer la rotación de la capa para probar el punto en su
        // espacio local (sin rotar): rota el vector (toque - centro) por
        // el ángulo OPUESTO al de la capa.
        val dx = ndcX - centerX
        val dy = ndcY - centerY
        val angleRad = Math.toRadians(-frame.rotationDeg.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        val localX = dx * cosA - dy * sinA
        val localY = dx * sinA + dy * cosA

        return kotlin.math.abs(localX) <= halfWidth && kotlin.math.abs(localY) <= halfHeight
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
private const val MAX_LAYER_SCALE = 40f

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
private enum class HandlePosition { TOP_LEFT, TOP_MID, TOP_RIGHT, RIGHT_MID, BOTTOM_RIGHT, BOTTOM_MID, BOTTOM_LEFT, LEFT_MID }
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

private fun encodeHandleOrderPerLayer(
    perLayer: Map<String, Map<HandlePosition, LayerHandleRole>>
): Map<String, Map<String, String>> =
    perLayer.mapValues { (_, order) -> encodeHandleOrder(order) }

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
                    keyboardActions = KeyboardActions(onDone = { if (isValid) onConfirm(parsed!!) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { if (isValid) onConfirm(parsed!!) }, enabled = isValid) { Text("Aplicar") }
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
private class BelowAnchorCenteredPopupPositionProvider(
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
 * en el timeline. Ya no dice "Agregar pista" (término de producción
 * musical) — la opción "+ Imagen" es el título/acción en sí. Audio /
 * Modelo 3D / Grabar audio se suman como filas nuevas más adelante.
 */
@Composable
private fun AddTrackDialog(onDismiss: () -> Unit, onImportImageClick: () -> Unit) {
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
                    // pantalla. Ahora "+ Imagen" es directamente la opción
                    // clickeable, sin ese título — a futuro, Audio / Modelo
                    // 3D / Grabar audio se agregan como filas debajo de esta.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onImportImageClick)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add),
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
