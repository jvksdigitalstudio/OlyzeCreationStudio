package com.yeivikas.olyzecs.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.yeivikas.olyzecs.engine.mesh3d.Extrude3D
import com.yeivikas.olyzecs.data.ImageDecoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * estado de rotación del panel "3D" (Extrude3DPanel, varios
 * composables más abajo en el árbol, dentro de LayerColorEditPanel).
 *
 * En vez de mover TODO el estado de edición 3D (rotaciones,
 * profundidad, bisel, bitmaps en memoria, debounce de guardado) hacia
 * arriba y rehacer la firma de LayerColorEditPanel/Extrude3DPanel para
 * pasarlo de nuevo hacia abajo, Extrude3DPanel simplemente se
 * "registra" acá con dos callbacks mientras está en pantalla (ver su
 * DisposableEffect) y se da de baja (todo a null/false) al salir de
 * la pestaña "3D" o cambiar de capa. El canvas solo necesita saber
 * SI hay alguien escuchando ([active], para decidir si dibuja el
 * marco normal de "mover/escalar/rotar" o entra en modo orbital sin
 * marco) y, si lo hay, delegarle los grados de arrastre/pellizco —
 * quién los usa y cómo (currentParams, debounce de guardado, etc.) es
 * un detalle que Extrude3DPanel resuelve por su cuenta.
 */
private class Extrude3DGestureBridge {
    var active by mutableStateOf(false)

    /** Arrastre de 1+ dedos: gira el cuerpo 3D (izq/der → Y, arriba/abajo → X), como orbitar una cámara. */
    var onOrbitDrag: ((dxDeg: Float, dyDeg: Float) -> Unit)? = null

    /** Giro de 2 dedos (el mismo gesto que rotaría la capa en 2D): acá gira el eje Z del cuerpo 3D. */
    var onTwistDrag: ((dzDeg: Float) -> Unit)? = null
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

    // Atrás del sistema: si está en pantalla completa, sale de ahí primero;
    // si no, guarda inmediatamente y vuelve a "Mis proyectos".
    BackHandler(enabled = true) {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            viewModel.saveNow { onBackToProjects() }
        }
    }

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
    var editModeOriginalTranslate by remember { mutableStateOf<Offset?>(null) }
    // A pedido, mismo criterio que `showCancelReorderConfirm`: tocar la ×
    // mientras la capa está en modo edición aislado ya NO sale de una —
    // primero avisa, con un diálogo, que la edición no se va a aplicar
    // (los ajustes de Recolor/3D hechos en esta sesión de edición se
    // pierden al salir así, a diferencia del check de la manija Editar,
    // que sí confirma). El usuario recién sale del modo edición si
    // confirma "Sí, salir"; si toca "No", sigue editando tal cual estaba.
    var showCancelEditModeConfirm by remember { mutableStateOf(false) }

    // Puente hacia Extrude3DPanel — ver [Extrude3DGestureBridge]. Un solo
    // objeto estable durante toda la vida de la pantalla (no depende de
    // layer.id ni de nada): quien cambia es qué callbacks tiene adentro,
    // no el objeto en sí.
    val extrude3DBridge = remember { Extrude3DGestureBridge() }

    // Sale del modo edición y devuelve la capa a su posición original.
    fun exitEditMode() {
        val original = editModeOriginalTranslate
        if (original != null) {
            translateX = original.x
            translateY = original.y
        }
        editModeLayerId = null
        editModeOriginalTranslate = null
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
                        IconButton(onClick = { viewModel.saveNow { onBackToProjects() } }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back),
                                contentDescription = "Volver a Mis proyectos"
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
                    }

                    // --- Centro real de la barra: Grabar + Play/Pausa ---
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
                        IconButton(onClick = { viewModel.undo() }, enabled = state.undoAvailable) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_undo),
                                contentDescription = "Deshacer",
                                tint = if (state.undoAvailable) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = { viewModel.redo() }, enabled = state.redoAvailable) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_redo),
                                contentDescription = "Rehacer",
                                tint = if (state.redoAvailable) Color.White else Color.White.copy(alpha = 0.3f)
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
        Column(
            modifier = Modifier
                .padding(padding)
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
                        if (isFullscreen) Modifier.weight(1f)
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
                            if (latestEdicionImagenEnabled.value) {
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
                                        val topLeft = handleCorners[0]
                                        val topRight = handleCorners[1]
                                        val bottomRight = handleCorners[2]
                                        val bottomLeft = handleCorners[3]
                                        val rightMid = Offset((topRight.x + bottomRight.x) / 2f, (topRight.y + bottomRight.y) / 2f)
                                        val bottomMid = Offset((bottomRight.x + bottomLeft.x) / 2f, (bottomRight.y + bottomLeft.y) / 2f)
                                        // Superior, medio: restaurar la posición/forma de la capa (ver
                                        // manija espejo en el Canvas de dibujo, más abajo, y
                                        // [drawRestoreGlyph]). Pedido explícito: a veces mover/
                                        // redimensionar con los dedos deja la imagen "torcida" (rotada,
                                        // estirada de un lado, etc.) y hace falta un botón para
                                        // devolverla a como estaba centrada, sin rotación ni
                                        // deformación — opcional, una manija más entre las 6 ya
                                        // existentes.
                                        val topMid = Offset((topLeft.x + topRight.x) / 2f, (topLeft.y + topRight.y) / 2f)
                                        // Lateral izquierda, medio: hasta ahora era la posición
                                        // FIJA de la manija de reordenar/confirmar (nunca
                                        // participaba del intercambio con las otras 7).
                                        // ARREGLADO A PEDIDO ("todos rotan y cambian al arrastrar
                                        // pero este no se mueve, quiero que también se
                                        // reordene, claro, sin confirmar"): ahora es una posición
                                        // MÁS (la 8va) del mismo sistema de intercambio, igual que
                                        // las otras 7 — se le puede arrastrar cualquier otra manija
                                        // encima (y ella a cualquier otra posición) sin que eso
                                        // "confirme" nada, solo cambia DÓNDE vive cada función. Lo
                                        // que sigue siendo especial de [LayerHandleRole.REORDER] es
                                        // el TAP simple (sin arrastre) sobre la posición que la
                                        // tenga asignada en cada momento: eso sigue abriendo la
                                        // mini-ventana "Solo aquí"/"Todos" (o confirmando el
                                        // borrador, si el modo ya está prendido) — el mismo
                                        // comportamiento de siempre, solo que la posición ahora se
                                        // resuelve dinámicamente en vez de estar hardcodeada acá.
                                        val leftMid = Offset((topLeft.x + bottomLeft.x) / 2f, (topLeft.y + bottomLeft.y) / 2f)
                                        val centerPx = Offset((topLeft.x + bottomRight.x) / 2f, (topLeft.y + bottomRight.y) / 2f)
                                        val touchRadiusPx = 20.dp.toPx()
                                        fun hits(p: Offset) = (down.position - p).getDistance() <= touchRadiusPx

                                        // --- Único mapa con las 8 posiciones físicas (las 7
                                        // intercambiables de siempre + la que antes era la fija de
                                        // reordenar) — antes había TRES copias de este mismo mapa
                                        // repetidas más abajo (una para el arrastre de reordenar,
                                        // otra para resolver `hitsRole`); unificarlas en una sola
                                        // evita que agregar esta 8va posición dependa de mantener
                                        // sincronizadas copias sueltas.
                                        val slotOffsets = mapOf(
                                            HandlePosition.TOP_LEFT to topLeft,
                                            HandlePosition.TOP_MID to topMid,
                                            HandlePosition.TOP_RIGHT to topRight,
                                            HandlePosition.RIGHT_MID to rightMid,
                                            HandlePosition.BOTTOM_RIGHT to bottomRight,
                                            HandlePosition.BOTTOM_MID to bottomMid,
                                            HandlePosition.BOTTOM_LEFT to bottomLeft,
                                            HandlePosition.LEFT_MID to leftMid
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
                                        val reorderHandleOffset = reorderPos?.let { slotOffsets[it] } ?: leftMid
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

                                        if (latestEdicionImagenEnabled.value) {
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
                            if (edicionImagenChecked) {
                                val topLeft = corners[0]
                                val topRight = corners[1]
                                val bottomRight = corners[2]
                                val bottomLeft = corners[3]
                                val rightMid = Offset((topRight.x + bottomRight.x) / 2f, (topRight.y + bottomRight.y) / 2f)
                                val bottomMid = Offset((bottomRight.x + bottomLeft.x) / 2f, (bottomRight.y + bottomLeft.y) / 2f)
                                val topMid = Offset((topLeft.x + topRight.x) / 2f, (topLeft.y + topRight.y) / 2f)
                                val leftMid = Offset((topLeft.x + bottomLeft.x) / 2f, (topLeft.y + bottomLeft.y) / 2f)

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
                                val slotOffsets = mapOf(
                                    HandlePosition.TOP_LEFT to topLeft,
                                    HandlePosition.TOP_MID to topMid,
                                    HandlePosition.TOP_RIGHT to topRight,
                                    HandlePosition.RIGHT_MID to rightMid,
                                    HandlePosition.BOTTOM_RIGHT to bottomRight,
                                    HandlePosition.BOTTOM_MID to bottomMid,
                                    HandlePosition.BOTTOM_LEFT to bottomLeft,
                                    HandlePosition.LEFT_MID to leftMid
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
                        val topLeftAnchor = corners[0]
                        val topRightAnchor = corners[1]
                        val bottomRightAnchor = corners[2]
                        val bottomLeftAnchor = corners[3]
                        val rightMidAnchor = Offset((topRightAnchor.x + bottomRightAnchor.x) / 2f, (topRightAnchor.y + bottomRightAnchor.y) / 2f)
                        val bottomMidAnchor = Offset((bottomRightAnchor.x + bottomLeftAnchor.x) / 2f, (bottomRightAnchor.y + bottomLeftAnchor.y) / 2f)
                        val topMidAnchor = Offset((topLeftAnchor.x + topRightAnchor.x) / 2f, (topLeftAnchor.y + topRightAnchor.y) / 2f)
                        val leftMidAnchor = Offset((topLeftAnchor.x + bottomLeftAnchor.x) / 2f, (topLeftAnchor.y + bottomLeftAnchor.y) / 2f)
                        val anchorOrder = if (reorderMode) (reorderDraftOrder ?: effectiveHandleOrder(selectedLayer.id)) else effectiveHandleOrder(selectedLayer.id)
                        // Incluye LEFT_MID: ahora que la manija de reordenar participa del
                        // intercambio (ver el Canvas de dibujo y el gesto del pointerInput,
                        // más arriba), CUALQUIER rol —incluido RESTORE— puede terminar
                        // asignado a esa posición, así que este mapa de anclas también
                        // necesita conocerla.
                        val anchorSlots = mapOf(
                            HandlePosition.TOP_LEFT to topLeftAnchor,
                            HandlePosition.TOP_MID to topMidAnchor,
                            HandlePosition.TOP_RIGHT to topRightAnchor,
                            HandlePosition.RIGHT_MID to rightMidAnchor,
                            HandlePosition.BOTTOM_RIGHT to bottomRightAnchor,
                            HandlePosition.BOTTOM_MID to bottomMidAnchor,
                            HandlePosition.BOTTOM_LEFT to bottomLeftAnchor,
                            HandlePosition.LEFT_MID to leftMidAnchor
                        )
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

            if (!isFullscreen) {

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
            if (editModeLayerId != null && selectedLayer != null && editModeLayerId == selectedLayer.id) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f),
                    color = SurfaceTintedElevated
                ) {
                    LayerColorEditPanel(
                        layer = selectedLayer,
                        context = context,
                        viewModel = viewModel,
                        extrude3DBridge = extrude3DBridge
                    )
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

            } // fin if (!isFullscreen)
        }
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
            }
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
                    exitEditMode()
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
 */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    valueLabel: (Float) -> String = { "%.2f".format(it) },
    onValueChange: (Float) -> Unit
) {
    // BUG REAL corregido acá: esta Column no tenía `fillMaxWidth()`, así
    // que en cualquier panel donde el ancho no viniera ya forzado por un
    // padre con weight/fill (como pasa en el panel "Recolor", cuyo
    // contenedor usa CenterHorizontally sin fillMaxWidth explícito) el
    // Slider caía a su ancho mínimo de "wrap content" — se veía cortado
    // a la mitad o menos, en vez de ocupar todo el panel como antes.
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ${valueLabel(value)}", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
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
    context: android.content.Context,
    viewModel: EditorViewModel,
    extrude3DBridge: Extrude3DGestureBridge,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var fullBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var palette by remember(layer.id) { mutableStateOf<List<Int>>(emptyList()) }
    var isLoadingPalette by remember(layer.id) { mutableStateOf(true) }
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

    // Qué pestaña del header ("Recolor" / "3D") está activa — ver
    // EditImageToolsHeader. Por capa: si cambiás de capa seleccionada,
    // vuelve a "Recolor" en vez de arrastrar la pestaña de la capa
    // anterior.
    var selectedTab by remember(layer.id) { mutableStateOf(0) }

    fun selectSwatch(originalColor: Int) {
        selectedOriginal = originalColor
        val effective = remaps[originalColor] ?: originalColor
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(effective, hsv)
        wheelHue = hsv[0]
        wheelSat = hsv[1]
        wheelVal = hsv[2]
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

        isLoadingPalette = true
        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 220)
        }
        liveBitmap = small
        val extracted = small?.let { ColorExtraction.extractPalette(it) } ?: emptyList()
        palette = extracted
        isLoadingPalette = false
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
        toReselect?.let { selectSwatch(it) }
        fullBitmap = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 1024)
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
            viewModel.commitLayerRecolor(layer.id, recoloredFull)
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

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // --- Header de pestañas del modo "Editando imagen": "Recolor"
        // (el panel de esta función, más abajo) y "3D" (Extrude3DPanel)
        // ya están activas/funcionales. Las últimas dos quedan como
        // cuadros vacíos reservados para próximas herramientas.
        EditImageToolsHeader(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
        )

        if (selectedTab == 1) {
            Extrude3DPanel(
                layer = layer,
                context = context,
                viewModel = viewModel,
                extrude3DBridge = extrude3DBridge,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
            return@Column
        }

        if (selectedTab == 2) {
            EffectsPanel(
                layer = layer,
                context = context,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
        // --- Columna izquierda: un cuadrito por color extraído ---
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Color",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            when {
                isLoadingPalette -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(top = 4.dp),
                            color = Color.White.copy(alpha = 0.5f),
                            strokeWidth = 2.dp
                        )
                    }
                }
                palette.isEmpty() -> {
                    Text(
                        "Sin colores",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                else -> {
                    // Cada cuadrito se achica automáticamente si hay
                    // muchos colores, para que TODOS entren en la fila
                    // vertical sin scroll — como se pidió ("mientras más
                    // colores el cuadro más se va ajustando para que
                    // entre en la pantalla").
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        val spacing = 6.dp
                        val count = palette.size
                        val idealSize = if (count > 0) {
                            (maxHeight - spacing * (count - 1).coerceAtLeast(0)) / count
                        } else maxHeight
                        val swatchSize = idealSize.coerceIn(14.dp, 40.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                            palette.forEach { originalColor ->
                                val effectiveColor = remaps[originalColor] ?: originalColor
                                val isSelected = selectedOriginal == originalColor
                                Box(
                                    modifier = Modifier
                                        .size(swatchSize)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(effectiveColor))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectSwatch(originalColor) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(28.dp))

        // --- Columna derecha: sliders profesionales arriba, rueda abajo ---
        Column(
            modifier = Modifier.fillMaxHeight().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedOriginal == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin colores para editar",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { v ->
                    wheelVal = v
                    applyCurrentWheelColor()
                }
                LabeledSlider(
                    label = "Saturación",
                    value = wheelSat,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { o ->
                    recolorOpacity = o
                    applyLivePreviewAndScheduleCommit()
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
                        modifier = Modifier.size(200.dp)
                    )
                }
            }
        }
        }
    }
}

/**
 * Panel de la pestaña "3D": extrusión real (ver Extrude3D) de la capa
 * completa — sirve para foto, PNG recortado, forma o texto ya
 * rasterizado por igual, no hace falta que sea un "sticker". Mismo
 * patrón de vista previa en vivo + guardado con debounce de 500ms que
 * [LayerColorEditPanel] usa para "Recolor": cada movimiento de slider
 * recalcula sobre una copia chica (liviano, sin lag) y sube esa vista
 * previa; medio segundo después del último movimiento, se recalcula
 * sobre la copia grande y se persiste como archivo nuevo (reusa
 * EditorViewModel.previewLayerRecolor/commitLayerRecolor tal cual —
 * son genéricos, no hacen nada específico de "recolorear").
 *
 * Nota honesta: como el cuerpo extruido puede sobresalir del cuadro
 * original de la imagen (por la rotación y la profundidad), el bitmap
 * resultante es más grande que el original con un margen alrededor —
 * evita que el efecto se vea recortado, a costa de que el tamaño en
 * píxeles de la capa cambie al aplicar el efecto.
 */
@Composable
private fun Extrude3DPanel(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    extrude3DBridge: Extrude3DGestureBridge,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var fullBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(layer.id) { mutableStateOf(true) }

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
        isLoading = true
        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 260)
        }
        liveBitmap = small
        isLoading = false
        fullBitmap = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 1024)
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
            viewModel.commitLayerRecolor(layer.id, rendered)
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

    Column(modifier = modifier) {
        if (isLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White.copy(alpha = 0.5f),
                    strokeWidth = 2.dp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { depth = it; applyLivePreviewAndScheduleCommit() }

                LabeledSlider(
                    label = "Bisel (borde redondeado)",
                    value = bevel,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { bevel = it; applyLivePreviewAndScheduleCommit() }

                LabeledSlider(
                    label = "Opacidad del material",
                    value = materialOpacity,
                    range = 0.2f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { materialOpacity = it; applyLivePreviewAndScheduleCommit() }
            }
        }
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
    var edgeFeather by mutableStateOf(0.3f)
    var sharpen by mutableStateOf(0f)
    var saturation by mutableStateOf(1f)
    var brightness by mutableStateOf(0f)
    var contrast by mutableStateOf(1f)
    var hue by mutableStateOf(0f)
    var outlineIntensity by mutableStateOf(0f)
    var outlineColor by mutableStateOf(android.graphics.Color.WHITE)
    var glowIntensity by mutableStateOf(0f)
    var glowBlur by mutableStateOf(0.5f)
    var glowColor by mutableStateOf(android.graphics.Color.WHITE)
    var shadowIntensity by mutableStateOf(0f)
    var shadowBlur by mutableStateOf(0.5f)
    var shadowSpread by mutableStateOf(0f)
    var shadowScale by mutableStateOf(1f)
    var shadowNoise by mutableStateOf(0f)
    var shadowDistance by mutableStateOf(0.35f)
    var shadowAngle by mutableStateOf(135f)
    var shadowColor by mutableStateOf(android.graphics.Color.BLACK)
    var shadowSkew by mutableStateOf(0f)
    var shadowFadeByDistance by mutableStateOf(0f)
    var shadowBlendMultiply by mutableStateOf(true)
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
    var reflectionSkew by mutableStateOf(0f)
    var reflectionTintIntensity by mutableStateOf(0f)
    var reflectionTintColor by mutableStateOf(android.graphics.Color.rgb(58, 110, 150))
    var reflectionEdgeFade by mutableStateOf(0f)
    var reflectionRippleIntensity by mutableStateOf(0f)
    var reflectionRippleScale by mutableStateOf(0.5f)
    var reflectionOpacityCurve by mutableStateOf(0.5f)
    var reflectionPerspective by mutableStateOf(1f)
    var globalLightAngle by mutableStateOf(135f)
    var linkShadowToGlobalLight by mutableStateOf(false)
    var linkFillShadowToGlobalLight by mutableStateOf(false)
    var contactShadowIntensity by mutableStateOf(0f)
    var contactShadowSize by mutableStateOf(0.5f)
    var contactShadowBlur by mutableStateOf(0.4f)
    var contactShadowColor by mutableStateOf(android.graphics.Color.BLACK)
    var contactShadowFalloff by mutableStateOf(0.5f)
    var lightWrapIntensity by mutableStateOf(0f)
    var lightWrapColor by mutableStateOf(android.graphics.Color.rgb(255, 244, 214))
    var lightWrapWidth by mutableStateOf(0.4f)
}

@Composable
private fun rememberEffectsControlsState(key: Any?): EffectsControlsState =
    remember(key) { EffectsControlsState() }

@Composable
private fun EffectsPanel(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var fullBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(layer.id) { mutableStateOf(true) }

    // Sub-categorías de la pestaña "Efectos" — antes todos los
    // controles vivían apilados en una sola columna larga con scroll;
    // ahora se agrupan por categoría y solo se muestran los controles
    // de la categoría seleccionada. Ningún control, valor por defecto
    // ni lógica de aplicación se modifica acá: es puramente una
    // reorganización visual de los mismos sliders/botones de siempre.
    val effectsCategories = remember {
        listOf(
            "Básicos",
            "Contorno",
            "Resplandor",
            "Presets",
            "Sombra",
            "Sombra relleno",
            "Sombra contacto",
            "Reflejo",
            "Light wrap",
            "Luz global"
        )
    }
    var selectedEffectsCategory by remember(layer.id) { mutableStateOf(0) }

    val ctrl = rememberEffectsControlsState(layer.id)
    var blur by ctrl::blur
    var edgeFeather by ctrl::edgeFeather
    var sharpen by ctrl::sharpen
    var saturation by ctrl::saturation
    var brightness by ctrl::brightness
    var contrast by ctrl::contrast
    var hue by ctrl::hue
    var outlineIntensity by ctrl::outlineIntensity
    var outlineColor by ctrl::outlineColor
    var glowIntensity by ctrl::glowIntensity
    var glowBlur by ctrl::glowBlur
    var glowColor by ctrl::glowColor
    var shadowIntensity by ctrl::shadowIntensity
    var shadowBlur by ctrl::shadowBlur
    var shadowSpread by ctrl::shadowSpread
    var shadowScale by ctrl::shadowScale
    var shadowNoise by ctrl::shadowNoise
    var shadowDistance by ctrl::shadowDistance
    var shadowAngle by ctrl::shadowAngle
    var shadowColor by ctrl::shadowColor
    var shadowSkew by ctrl::shadowSkew
    var shadowFadeByDistance by ctrl::shadowFadeByDistance
    var shadowBlendMultiply by ctrl::shadowBlendMultiply
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
    var reflectionSkew by ctrl::reflectionSkew
    var reflectionTintIntensity by ctrl::reflectionTintIntensity
    var reflectionTintColor by ctrl::reflectionTintColor
    var reflectionEdgeFade by ctrl::reflectionEdgeFade
    var reflectionRippleIntensity by ctrl::reflectionRippleIntensity
    var reflectionRippleScale by ctrl::reflectionRippleScale
    var reflectionOpacityCurve by ctrl::reflectionOpacityCurve
    var reflectionPerspective by ctrl::reflectionPerspective
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
    var contactShadowIntensity by ctrl::contactShadowIntensity
    var contactShadowSize by ctrl::contactShadowSize
    var contactShadowBlur by ctrl::contactShadowBlur
    var contactShadowColor by ctrl::contactShadowColor
    var contactShadowFalloff by ctrl::contactShadowFalloff
    var lightWrapIntensity by ctrl::lightWrapIntensity
    var lightWrapColor by ctrl::lightWrapColor
    var lightWrapWidth by ctrl::lightWrapWidth

    var commitJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    // Throttle de la vista previa en vivo — mismo motivo/valores que en
    // Extrude3DPanel.applyLivePreviewAndScheduleCommit: sin esto, cada
    // tick crudo del Slider dispararía un box-blur de 3 pasadas en el
    // hilo de UI y se vería entrecortado mientras se arrastra.
    var liveRenderJob by remember(layer.id) { mutableStateOf<Job?>(null) }
    var lastLiveRenderAtMs by remember(layer.id) { mutableStateOf(0L) }

    // Igual que Extrude3DPanel: la base "plana" (sin efectos todavía) se
    // decodifica UNA vez al entrar a esta pestaña para esta capa y
    // `currentParams()` se aplica siempre sobre esa MISMA base durante
    // toda la sesión — así los commits de fondo (que reescriben
    // layer.sourceUri con el resultado YA CON efectos) nunca terminan
    // re-aplicando el difuminado/sombra sobre su propio resultado
    // anterior.
    LaunchedEffect(layer.id) {
        isLoading = true
        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 260)
        }
        liveBitmap = small
        isLoading = false
        fullBitmap = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 1024)
        }
    }

    fun currentParams() = com.yeivikas.olyzecs.engine.effects.ImageEffectsParams(
        blur = blur,
        edgeFeather = edgeFeather,
        sharpen = sharpen,
        saturation = saturation,
        brightness = brightness,
        contrast = contrast,
        hue = hue,
        outlineIntensity = outlineIntensity,
        outlineColor = outlineColor,
        glowIntensity = glowIntensity,
        glowBlur = glowBlur,
        glowColor = glowColor,
        shadowIntensity = shadowIntensity,
        shadowBlur = shadowBlur,
        shadowSpread = shadowSpread,
        shadowScale = shadowScale,
        shadowNoise = shadowNoise,
        shadowDistance = shadowDistance,
        shadowAngleDeg = shadowAngle,
        shadowColor = shadowColor,
        shadowSkewDegrees = shadowSkew,
        shadowFadeByDistance = shadowFadeByDistance,
        shadowBlendMultiply = shadowBlendMultiply,
        fillShadowIntensity = fillShadowIntensity,
        fillShadowBlur = fillShadowBlur,
        fillShadowDistance = fillShadowDistance,
        fillShadowAngleDeg = fillShadowAngle,
        fillShadowColor = fillShadowColor,
        fillShadowScale = fillShadowScale,
        reflectionIntensity = reflectionIntensity,
        reflectionGap = reflectionGap,
        reflectionLength = reflectionLength,
        reflectionBlur = reflectionBlur,
        reflectionSkewDegrees = reflectionSkew,
        reflectionTintIntensity = reflectionTintIntensity,
        reflectionTintColor = reflectionTintColor,
        reflectionEdgeFade = reflectionEdgeFade,
        reflectionRippleIntensity = reflectionRippleIntensity,
        reflectionRippleScale = reflectionRippleScale,
        reflectionOpacityCurve = reflectionOpacityCurve,
        reflectionPerspective = reflectionPerspective,
        contactShadowIntensity = contactShadowIntensity,
        contactShadowSize = contactShadowSize,
        contactShadowBlur = contactShadowBlur,
        contactShadowColor = contactShadowColor,
        contactShadowFalloff = contactShadowFalloff,
        lightWrapIntensity = lightWrapIntensity,
        lightWrapColor = lightWrapColor,
        lightWrapWidth = lightWrapWidth
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
            viewModel.commitLayerRecolor(layer.id, rendered)
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

    Column(modifier = modifier) {
        if (isLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White.copy(alpha = 0.5f),
                    strokeWidth = 2.dp
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                EffectsCategoryTabs(
                    categories = effectsCategories,
                    selected = selectedEffectsCategory,
                    onSelected = { selectedEffectsCategory = it }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                if (selectedEffectsCategory == 0) {
                    EffectsCategoryBasicos(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }


                if (selectedEffectsCategory == 1) {
                    EffectsCategoryContorno(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }


                if (selectedEffectsCategory == 2) {
                    EffectsCategoryResplandor(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }


                if (selectedEffectsCategory == 3) {
                    EffectsCategoryPresets(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }


                if (selectedEffectsCategory == 4) {
                    EffectsCategorySombra(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }


                if (selectedEffectsCategory == 5) {
                    EffectsCategorySombraRelleno(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }


                if (selectedEffectsCategory == 6) {
                    EffectsCategorySombraContacto(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }


                if (selectedEffectsCategory == 7) {
                    EffectsCategoryReflejo(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }


                if (selectedEffectsCategory == 8) {
                    EffectsCategoryLightWrap(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }


                if (selectedEffectsCategory == 9) {
                    EffectsCategoryLuzGlobal(ctrl, onChanged = { applyLivePreviewAndScheduleCommit() })
                }

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
    var shadowFadeByDistance by ctrl::shadowFadeByDistance
    var shadowNoise by ctrl::shadowNoise
    var shadowBlendMultiply by ctrl::shadowBlendMultiply
    var globalLightAngle by ctrl::globalLightAngle
    var linkShadowToGlobalLight by ctrl::linkShadowToGlobalLight

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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { shadowBlur = it; onChanged() }

                LabeledSlider(
                    label = "Expansión (Spread)",
                    value = shadowSpread,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { shadowDistance = it; onChanged() }

                LabeledSlider(
                    label = "Escala de la sombra",
                    value = shadowScale,
                    range = 0.4f..2f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
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
                    enabled = shadowControlsEnabled
                ) { c -> shadowColor = c; onChanged() }

                LabeledSlider(
                    label = "Inclinación (perspectiva)",
                    value = shadowSkew,
                    range = -45f..45f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { shadowSkew = it; onChanged() }

                LabeledSlider(
                    label = "Desvanecer con la distancia",
                    value = shadowFadeByDistance,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { shadowFadeByDistance = it; onChanged() }

                LabeledSlider(
                    label = "Grano / textura",
                    value = shadowNoise,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { shadowNoise = it; onChanged() }
                Text(
                    "Rompe la uniformidad perfecta de la sombra con una textura fina — evita el look \"digital\"/plano sobre superficies reales",
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
    var reflectionSkew by ctrl::reflectionSkew
    var reflectionTintIntensity by ctrl::reflectionTintIntensity
    var reflectionTintColor by ctrl::reflectionTintColor
    var reflectionEdgeFade by ctrl::reflectionEdgeFade
    var reflectionRippleIntensity by ctrl::reflectionRippleIntensity
    var reflectionRippleScale by ctrl::reflectionRippleScale
    var reflectionOpacityCurve by ctrl::reflectionOpacityCurve
    var reflectionPerspective by ctrl::reflectionPerspective

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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { reflectionGap = it; onChanged() }

                LabeledSlider(
                    label = "Largo del reflejo",
                    value = reflectionLength,
                    range = 0.1f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { reflectionLength = it; onChanged() }

                LabeledSlider(
                    label = "Difuminado del reflejo",
                    value = reflectionBlur,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { reflectionBlur = it; onChanged() }

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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { reflectionEdgeFade = it; onChanged() }

                LabeledSlider(
                    label = "Ondulación (agua)",
                    value = reflectionRippleIntensity,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { reflectionRippleIntensity = it; onChanged() }

                LabeledSlider(
                    label = "Densidad de las ondas",
                    value = reflectionRippleScale,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled && reflectionRippleIntensity > 0.001f,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { reflectionRippleScale = it; onChanged() }

                LabeledSlider(
                    label = "Curva de opacidad",
                    value = reflectionOpacityCurve,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { reflectionPerspective = it; onChanged() }
                Text(
                    "Aplana o estira el reflejo verticalmente sin tocar su ancho — un reflejo real sobre un piso en ángulo casi nunca es 1:1 con el sujeto",
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { fillShadowIntensity = it; onChanged() }

                val fillShadowControlsEnabled = fillShadowIntensity > 0.001f

                LabeledSlider(
                    label = "Difuminado",
                    value = fillShadowBlur,
                    range = 0f..1f,
                    enabled = fillShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { fillShadowBlur = it; onChanged() }

                LabeledSlider(
                    label = "Distancia",
                    value = fillShadowDistance,
                    range = 0f..1f,
                    enabled = fillShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { fillShadowDistance = it; onChanged() }

                LabeledSlider(
                    label = "Escala",
                    value = fillShadowScale,
                    range = 0.4f..2f,
                    enabled = fillShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { contactShadowIntensity = it; onChanged() }

                val contactShadowControlsEnabled = contactShadowIntensity > 0.001f

                LabeledSlider(
                    label = "Tamaño",
                    value = contactShadowSize,
                    range = 0.1f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { contactShadowSize = it; onChanged() }

                LabeledSlider(
                    label = "Difuminado",
                    value = contactShadowBlur,
                    range = 0f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { contactShadowBlur = it; onChanged() }

                LabeledSlider(
                    label = "Curva de caída (falloff)",
                    value = contactShadowFalloff,
                    range = 0f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { contactShadowFalloff = it; onChanged() }
                Text(
                    "Bajo = caída lenta y extendida, alto = núcleo denso que corta rápido — controla la FORMA del degradado, no solo su difuminado",
                    color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                ColorSwatchPickerButton(
                    label = "Color de contacto",
                    colorArgb = contactShadowColor,
                    enabled = contactShadowControlsEnabled
                ) { c -> contactShadowColor = c; onChanged() }
}
@Composable
private fun EffectsCategoryLightWrap(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var lightWrapIntensity by ctrl::lightWrapIntensity
    var lightWrapWidth by ctrl::lightWrapWidth
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { lightWrapIntensity = it; onChanged() }

                val lightWrapControlsEnabled = lightWrapIntensity > 0.001f

                LabeledSlider(
                    label = "Ancho",
                    value = lightWrapWidth,
                    range = 0f..1f,
                    enabled = lightWrapControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { lightWrapWidth = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color de la luz",
                    colorArgb = lightWrapColor,
                    enabled = lightWrapControlsEnabled
                ) { c -> lightWrapColor = c; onChanged() }
}
@Composable
private fun EffectsCategoryPresets(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var shadowIntensity by ctrl::shadowIntensity
    var shadowBlur by ctrl::shadowBlur
    var shadowDistance by ctrl::shadowDistance
    var shadowAngle by ctrl::shadowAngle
    var shadowSkew by ctrl::shadowSkew
    var shadowFadeByDistance by ctrl::shadowFadeByDistance
    var shadowBlendMultiply by ctrl::shadowBlendMultiply
    var fillShadowIntensity by ctrl::fillShadowIntensity
    var fillShadowBlur by ctrl::fillShadowBlur
    var fillShadowDistance by ctrl::fillShadowDistance
    var fillShadowAngle by ctrl::fillShadowAngle
    var fillShadowColor by ctrl::fillShadowColor
    var contactShadowIntensity by ctrl::contactShadowIntensity
    var contactShadowSize by ctrl::contactShadowSize
    var contactShadowBlur by ctrl::contactShadowBlur
    var reflectionIntensity by ctrl::reflectionIntensity
    var reflectionGap by ctrl::reflectionGap
    var reflectionLength by ctrl::reflectionLength
    var reflectionBlur by ctrl::reflectionBlur
    var reflectionSkew by ctrl::reflectionSkew
    var reflectionTintIntensity by ctrl::reflectionTintIntensity
    var reflectionTintColor by ctrl::reflectionTintColor
    var reflectionEdgeFade by ctrl::reflectionEdgeFade
    var reflectionRippleIntensity by ctrl::reflectionRippleIntensity
    var reflectionRippleScale by ctrl::reflectionRippleScale
    var lightWrapIntensity by ctrl::lightWrapIntensity
    var lightWrapWidth by ctrl::lightWrapWidth
    var lightWrapColor by ctrl::lightWrapColor

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
                        shadowIntensity = 0f
                        fillShadowIntensity = 0f
                        contactShadowIntensity = 0f
                        reflectionIntensity = 0f
                        lightWrapIntensity = 0f
                        onChanged()
                    }) {
                        Text("Quitar sombra/reflejo")
                    }
                }
}
@Composable
private fun EffectsCategoryBasicos(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var blur by ctrl::blur
    var edgeFeather by ctrl::edgeFeather
    var sharpen by ctrl::sharpen
    var saturation by ctrl::saturation
    var brightness by ctrl::brightness
    var contrast by ctrl::contrast
    var hue by ctrl::hue

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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { blur = it; onChanged() }

                LabeledSlider(
                    label = "Suavizado de contorno",
                    value = edgeFeather,
                    range = 0f..1f,
                    enabled = blur > 0.001f,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { edgeFeather = it; onChanged() }

                LabeledSlider(
                    label = "Nitidez",
                    value = sharpen,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { sharpen = it; onChanged() }

                LabeledSlider(
                    label = "Saturación",
                    value = saturation,
                    range = 0f..2f,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { saturation = it; onChanged() }

                LabeledSlider(
                    label = "Brillo",
                    value = brightness,
                    range = -1f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%".let { s -> if (it > 0f) "+$s" else s } }
                ) { brightness = it; onChanged() }

                LabeledSlider(
                    label = "Contraste",
                    value = contrast,
                    range = 0f..2f,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { outlineIntensity = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color del contorno",
                    colorArgb = outlineColor,
                    enabled = outlineIntensity > 0.001f
                ) { c -> outlineColor = c; onChanged() }
}

@Composable
private fun EffectsCategoryResplandor(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var glowIntensity by ctrl::glowIntensity
    var glowBlur by ctrl::glowBlur
    var glowColor by ctrl::glowColor

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
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { glowIntensity = it; onChanged() }

                val glowControlsEnabled = glowIntensity > 0.001f

                LabeledSlider(
                    label = "Difuminado del resplandor",
                    value = glowBlur,
                    range = 0f..1f,
                    enabled = glowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { glowBlur = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color del resplandor",
                    colorArgb = glowColor,
                    enabled = glowControlsEnabled
                ) { c -> glowColor = c; onChanged() }
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
                        valueLabel = { "${(it * 100).roundToInt()}%" }
                    ) { v ->
                        wheelVal = v
                        applyCurrentColor()
                    }
                }
            }
        }
    }
}

/**
 * Header de 4 pestañas del modo "Editando imagen" (overlay sobre el
 * lienzo). "Recolor", "3D" y "Efectos" ya están implementadas y
 * responden al toque; la última sigue como cuadro reservado para
 * próximas herramientas (deshabilitado, sin acción al tocarlo).
 */
@Composable
private fun EditImageToolsHeader(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Recolor", "3D", "Efectos", "Próximamente")
    val enabledCount = 3
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val isEnabled = index < enabledCount
            val isActive = isEnabled && index == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isActive) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isActive) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .then(
                        // Los cuadros todavía sin implementar quedan sin
                        // `clickable` para que no den feedback de "tocado"
                        // prometiendo algo que aún no hace nada.
                        if (isEnabled) Modifier.clickable { onTabSelected(index) } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
            }
        }
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
 * más de ~380ms, arranca a repetir solo — cada repetición un poco más
 * rápido que la anterior (hasta un piso de ~45ms entre pasos) — hasta
 * que se suelta. Mismo estándar de cualquier stepper numérico de
 * software profesional (los +/- de iOS/macOS, los steppers de Premiere y
 * Photoshop). El círculo se ve un toque más claro mientras está
 * apretado, para que el auto-repeat tenga feedback visual de que el
 * botón sigue "activo".
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
                                // Espera antes de empezar a repetir —
                                // así un toque simple y rápido dispara
                                // UN solo paso, no una ráfaga.
                                var delayMs = 380L
                                while (true) {
                                    val released = withTimeoutOrNull(delayMs) { waitForUpOrCancellation() }
                                    if (released != null) break
                                    latestOnClick.value()
                                    // Acelera un poco en cada repetición,
                                    // con un piso para que nunca quede
                                    // descontrolado.
                                    delayMs = (delayMs * 0.72f).toLong().coerceAtLeast(45L)
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
