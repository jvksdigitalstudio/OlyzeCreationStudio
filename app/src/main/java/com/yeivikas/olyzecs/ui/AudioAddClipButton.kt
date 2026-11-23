package com.yeivikas.olyzecs.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Lado de la zona táctil del botón: el alto de la fila de audio, para que nunca se recorte. */
private val ADD_CLIP_BUTTON_TOUCH_SIZE = 36.dp

/** Diámetro del círculo blanco visible (la zona táctil lo rodea). */
private val ADD_CLIP_BUTTON_DISC_SIZE = 28.dp

private val ADD_CLIP_BUTTON_ICON_SIZE = 16.dp

/** Tinta del ícono sobre el círculo blanco: casi negro con matiz violeta, la identidad oscura de la app. */
private val ADD_CLIP_BUTTON_ICON_COLOR = Color(0xFF1B1830)

/** Escala del botón mientras se arrastra: se "levanta" para que se note que está agarrado. */
private const val ADD_CLIP_BUTTON_DRAG_SCALE = 1.2f

/** Umbral (dp) para distinguir arrastre de toque: mínimo, para que el botón siga al dedo casi sin esperar. */
private val ADD_CLIP_BUTTON_DRAG_SLOP = 3.dp

/** Ancho de la marca que indica dónde va a quedar el botón mientras se arrastra. */
private val ADD_CLIP_TARGET_MARK_WIDTH = 2.dp

private const val ADD_CLIP_LIFT_MS = 90
private const val ADD_CLIP_SETTLE_MS = 130

/** Duración del fundido de aparición. Corto: la respuesta es inmediata y un doble toque casi no lo deja ver. */
private const val ADD_CLIP_BUTTON_APPEAR_MS = 110

/** Escala inicial de la aparición (crece hasta 1). */
private const val ADD_CLIP_BUTTON_APPEAR_FROM_SCALE = 0.8f

/**
 * Registro de las ventanas flotantes que cubren el carril (p. ej. el panel de opciones "al costado" de una fila,
 * [RowSideActionsPanelHost]). El botón "+Clip" vive en una capa SOBRE la lista (hermana, no dentro de las filas) y
 * `zIndex` solo ordena entre hermanos: ninguna ventana que viva dentro de una fila puede subir por encima de esa capa.
 * Por eso cada ventana informa aquí su rectángulo (coordenadas de ventana) y el botón se recorta por detrás de él
 * ([AddClipBehindWindowsShape]): ni se ve ni captura toques donde hay una ventana encima.
 */
internal interface AddClipOccluderRegistry {
    /** Publica (o actualiza) el rectángulo de ventana de [key]. Un rectángulo vacío equivale a [removeOccluder]. */
    fun reportOccluder(key: Any, windowBounds: Rect)

    /** La ventana de [key] ya no existe o dejó de ser visible. */
    fun removeOccluder(key: Any)
}

/** Registro vigente del timeline; `null` fuera de él (las ventanas simplemente no se registran). */
internal val LocalAddClipOccluders = compositionLocalOf<AddClipOccluderRegistry?> { null }

/**
 * Estado del botón "+Clip": el carril de audio ([trackId]), el instante en el
 * timeline ([ms]) y los límites del carril en pantalla. Un solo punto a la vez.
 *
 * Es un objeto aparte y NO se lee al componer `TimelineView`: el timeline es
 * enorme y recomponerlo en cada evento del arrastre lo volvía pesado. Solo lo
 * leen el [AudioAddClipOverlay] (al mostrarse/ocultarse) y su `offset`/capa
 * gráfica (fases de layout/dibujo, sin recomposición).
 */
@Stable
internal class AddClipState : AddClipOccluderRegistry {
    var trackId by mutableStateOf<String?>(null)
        private set
    var ms by mutableLongStateOf(0L)
        private set
    var laneBounds by mutableStateOf<Rect?>(null)
        private set
    /** Origen (ventana) de la capa superpuesta que aloja el botón. */
    var overlayOrigin by mutableStateOf(Offset.Zero)
    /** Sube con cada toque que propone un punto (no con el arrastre): reinicia aparición y ventana del doble toque. */
    var tapSerial by mutableIntStateOf(0)
        private set

    private val boundsByTrack = HashMap<String, Rect>()

    /** Rectángulos (ventana) de las ventanas que cubren el carril. Lista inmutable que se reemplaza al cambiar. */
    var occluders by mutableStateOf<List<Rect>>(emptyList())
        private set

    private val occluderByKey = LinkedHashMap<Any, Rect>()

    override fun reportOccluder(key: Any, windowBounds: Rect) {
        if (windowBounds.width <= 0f || windowBounds.height <= 0f) {
            removeOccluder(key)
            return
        }
        if (occluderByKey[key] == windowBounds) return
        occluderByKey[key] = windowBounds
        occluders = occluderByKey.values.toList()
    }

    override fun removeOccluder(key: Any) {
        if (occluderByKey.remove(key) != null) occluders = occluderByKey.values.toList()
    }

    /** Un toque propone el punto: el botón aparece (con fundido). */
    fun placeFromTap(trackId: String, ms: Long) {
        this.trackId = trackId
        this.ms = ms
        laneBounds = boundsByTrack[trackId]
        tapSerial++
    }

    /** El arrastre mueve el punto: sin reiniciar la aparición. */
    fun placeFromDrag(trackId: String, ms: Long) {
        this.trackId = trackId
        this.ms = ms
        laneBounds = boundsByTrack[trackId]
    }

    fun clear() {
        trackId = null
        laneBounds = null
    }

    fun owns(trackId: String): Boolean = this.trackId == trackId

    fun boundsOf(trackId: String): Rect? = boundsByTrack[trackId]

    /** Cada carril informa sus límites; si es el dueño del punto, el botón lo sigue (scroll, rotación). */
    fun reportLaneBounds(trackId: String, bounds: Rect) {
        boundsByTrack[trackId] = bounds
        if (this.trackId == trackId) laneBounds = bounds
    }
}

/**
 * Botón "+Clip" de los carriles de audio (mecánica de la playlist de FL Studio
 * Mobile). Aparece AL INSTANTE al tocar un espacio vacío de un carril, centrado
 * en el punto tocado, con un fundido/escala de [ADD_CLIP_BUTTON_APPEAR_MS] ms.
 * Dos gestos:
 *
 *  - **Pulsar** abre el selector de archivos para cargar un clip que arranca en
 *    ese punto ([onClick]).
 *  - **Mantener y arrastrar** lo reubica. Mientras se arrastra el botón SIGUE AL
 *    DEDO 1:1 (sin imanes ni saltos, sin umbral de arranque) y una marca fina
 *    muestra dónde va a quedar (carril vacío más cercano, con imán). Al soltar
 *    se asienta con una animación corta en ese punto y recién entonces se pulsa
 *    para abrir el selector. El arrastre NO abre el selector.
 *
 * Durante la ventana del doble toque (`ViewConfiguration.doubleTapTimeoutMillis`
 * desde que aparece) NO captura toques: el segundo toque de un doble toque cae
 * justo sobre este botón y debe llegar al carril para abrir el selector en el
 * cursor. Pasada la ventana responde con normalidad.
 *
 * Vive en una capa superpuesta del timeline (no dentro de una fila) para que el
 * mismo gesto sobreviva al cambio de carril. No decide qué punto es válido:
 * informa por dónde va el dedo ([onDragTo], coordenadas de ventana) y quien lo
 * aloja actualiza [state]. Nada de esto recompone: la posición se resuelve en
 * la fase de layout y la escala/opacidad en la capa gráfica.
 *
 * @param pxPerMs escala del timeline (px por ms) del carril.
 * @param laneWidthPx ancho del carril; el botón se acota a sus bordes ([addClipButtonLeftPx]).
 * @param onDragEnd el dedo se soltó: el punto válido ya está en [state].
 * @param onSettled terminó la animación de asentado (momento seguro para trabajo pesado, p. ej. seleccionar el carril).
 */
@Composable
internal fun AudioAddClipOverlay(
    state: AddClipState,
    pxPerMs: Float,
    laneWidthPx: Float,
    onClick: (trackId: String, ms: Long) -> Unit,
    onDragTo: (windowPoint: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onSettled: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mostrarse/ocultarse es lo único que recompone: moverse es solo layout.
    val visible by remember(state) { derivedStateOf { state.trackId != null && state.laneBounds != null } }
    if (!visible) return

    val density = LocalDensity.current
    val touchPx = with(density) { ADD_CLIP_BUTTON_TOUCH_SIZE.toPx() }
    val markWidthPx = with(density) { ADD_CLIP_TARGET_MARK_WIDTH.roundToPx() }
    val doubleTapWindowMs = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDragTo by rememberUpdatedState(onDragTo)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnSettled by rememberUpdatedState(onSettled)
    val currentPxPerMs by rememberUpdatedState(pxPerMs)
    val currentLaneWidthPx by rememberUpdatedState(laneWidthPx)

    var dragging by remember { mutableStateOf(false) }
    // Centro del botón bajo el dedo (ventana) mientras dura el arrastre; null si no se arrastra.
    var dragCenter by remember { mutableStateOf<Offset?>(null) }
    // Asentado al soltar: de dónde sale (esquina, ventana) y avance 0..1 hacia el punto válido.
    var settleFrom by remember { mutableStateOf<Offset?>(null) }
    var settleProgress by remember { mutableFloatStateOf(1f) }
    var settleSerial by remember { mutableIntStateOf(0) }
    val lift = animateFloatAsState(
        targetValue = if (dragging) ADD_CLIP_BUTTON_DRAG_SCALE else 1f,
        animationSpec = tween(ADD_CLIP_LIFT_MS),
        label = "addClipLift"
    )
    val interactionSource = remember { MutableInteractionSource() }

    // Aparición: arranca YA en cada toque nuevo. `responds` abre cuando vence la ventana del doble toque.
    val appear = remember { Animatable(0f) }
    var responds by remember { mutableStateOf(false) }
    LaunchedEffect(state.tapSerial) {
        responds = false
        appear.snapTo(0f)
        launch { appear.animateTo(1f, tween(ADD_CLIP_BUTTON_APPEAR_MS, easing = FastOutSlowInEasing)) }
        delay(doubleTapWindowMs)
        responds = true
    }
    LaunchedEffect(settleSerial) {
        if (settleSerial == 0) return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(ADD_CLIP_SETTLE_MS, easing = FastOutSlowInEasing)) { value, _ ->
            settleProgress = value
        }
        settleFrom = null
        currentOnSettled()
    }

    /** Esquina izquierda-superior (ventana) del punto válido actual, o null si aún no hay carril. */
    fun targetTopLeftWindow(): Offset? {
        val bounds = state.laneBounds ?: return null
        val leftPx = addClipButtonLeftPx(state.ms * currentPxPerMs, currentLaneWidthPx, touchPx)
        return Offset(bounds.left + leftPx, bounds.top)
    }

    /** Esquina izquierda-superior (ventana) donde está el botón AHORA: bajo el dedo, asentándose o en su punto. */
    fun currentTopLeftWindow(): Offset? {
        val target = targetTopLeftWindow() ?: return null
        val drag = dragCenter
        val from = settleFrom
        return when {
            drag != null -> Offset(drag.x - touchPx / 2f, drag.y - touchPx / 2f)
            from != null && settleProgress < 1f -> lerp(from, target, settleProgress)
            else -> target
        }
    }

    Box(
        modifier = modifier
            .offset {
                val topLeft = currentTopLeftWindow() ?: return@offset IntOffset.Zero
                IntOffset(
                    (topLeft.x - state.overlayOrigin.x).roundToInt(),
                    (topLeft.y - state.overlayOrigin.y).roundToInt()
                )
            }
            .size(ADD_CLIP_BUTTON_TOUCH_SIZE)
            // Esta capa NO escala: aloja el recorte y los gestos, y dentro de una capa escalada Compose entrega las
            // posiciones del dedo SIN escalar (÷ escala). Con el botón "levantado" (×[ADD_CLIP_BUTTON_DRAG_SCALE]) cada
            // píxel real del dedo contaba 1/1.2 para `positionChange` y el botón se quedaba atrás un 17 % del recorrido:
            // imperceptible en carriles cercanos y muy visible al cruzar muchos. La escala es solo del dibujo (abajo).
            .graphicsLayer {
                alpha = appear.value
                // Recorte del botón: un círculo, menos lo que tapan las ventanas flotantes (ver
                // [AddClipOccluderRegistry]). `clip` con forma también delimita el área TÁCTIL: donde hay una
                // ventana encima el botón no recibe el toque y le llega a la ventana (que va por debajo en el árbol).
                // Se lee aquí (fase de dibujo), así que mover/escalar el botón o una ventana no recompone nada.
                val covered = currentTopLeftWindow()?.let { topLeft ->
                    // La posición REAL de layout es entera (ver `offset` arriba): se usa esa, no la de coma flotante.
                    val layoutTopLeft = Offset(
                        state.overlayOrigin.x + (topLeft.x - state.overlayOrigin.x).roundToInt(),
                        state.overlayOrigin.y + (topLeft.y - state.overlayOrigin.y).roundToInt()
                    )
                    addClipOccludersLocal(state.occluders, layoutTopLeft, touchPx)
                }.orEmpty()
                shape = if (covered.isEmpty()) CircleShape else AddClipBehindWindowsShape(covered)
                clip = true
            }
            .then(
                if (responds) {
                    Modifier
                        // Sin ripple: el destello al presionar se leía como un tirón al empezar a arrastrar.
                        .clickable(interactionSource = interactionSource, indication = null, role = Role.Button) {
                            val id = state.trackId
                            if (id != null) currentOnClick(id, state.ms)
                        }
                        // Después de `clickable`: recibe los eventos primero y, al superar un umbral
                        // mínimo, los consume y cancela el click. Detección propia (no
                        // `detectDragGestures`): el umbral de éste (~8 dp) hacía esperar y luego saltar.
                        .pointerInput(Unit) {
                            val slopPx = ADD_CLIP_BUTTON_DRAG_SLOP.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val topLeft = targetTopLeftWindow() ?: return@awaitEachGesture
                                val startCenter = Offset(topLeft.x + touchPx / 2f, topLeft.y + touchPx / 2f)
                                var moved = Offset.Zero
                                var started = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (started) change.consume()
                                        break
                                    }
                                    if (change.isConsumed && !started) break
                                    // `positionChange` es el desplazamiento real del dedo aunque el botón
                                    // (el nodo) se mueva con él, SIEMPRE que ninguna capa por encima de este
                                    // gesto escale (si no, llega dividido por la escala): ver la capa de arriba.
                                    moved += change.positionChange()
                                    if (!started && moved.getDistance() > slopPx) {
                                        started = true
                                        dragging = true
                                    }
                                    if (started) {
                                        change.consume()
                                        val center = startCenter + moved
                                        dragCenter = center
                                        currentOnDragTo(center)
                                    }
                                }
                                if (started) {
                                    // Asentar desde donde quedó el dedo hasta el punto válido.
                                    val drop = dragCenter
                                    settleFrom = drop?.let { Offset(it.x - touchPx / 2f, it.y - touchPx / 2f) }
                                    settleProgress = 0f
                                    dragCenter = null
                                    dragging = false
                                    settleSerial++
                                    currentOnDragEnd()
                                }
                            }
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Dibujo del botón: aquí (y solo aquí) van la aparición y el "levantado". Cabe en la zona táctil: el disco
        // de 28 dp × 1.2 = 33.6 dp < 36 dp.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val grow = ADD_CLIP_BUTTON_APPEAR_FROM_SCALE +
                        (1f - ADD_CLIP_BUTTON_APPEAR_FROM_SCALE) * appear.value
                    val scale = lift.value * grow
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
        Box(
            modifier = Modifier
                .size(ADD_CLIP_BUTTON_DISC_SIZE)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add_audio_clip),
                contentDescription = "Agregar clip de audio aquí",
                tint = ADD_CLIP_BUTTON_ICON_COLOR,
                modifier = Modifier.size(ADD_CLIP_BUTTON_ICON_SIZE)
            )
        }
        }
    }

    // Marca del punto donde va a quedar (solo mientras se arrastra): el botón sigue al
    // dedo libremente y esto muestra el destino con imán.
    if (dragging) {
        Box(
            modifier = Modifier
                .offset {
                    val target = targetTopLeftWindow() ?: return@offset IntOffset.Zero
                    IntOffset(
                        (target.x - state.overlayOrigin.x + (touchPx - markWidthPx) / 2f).roundToInt(),
                        (target.y - state.overlayOrigin.y).roundToInt()
                    )
                }
                .size(width = ADD_CLIP_TARGET_MARK_WIDTH, height = ADD_CLIP_BUTTON_TOUCH_SIZE)
                .background(Color.White.copy(alpha = 0.75f))
        )
    }
}

/**
 * Límites (px, coordenadas de ventana) de un carril de audio para ubicar el
 * dedo al arrastrar "+Clip". Pura, para JUnit.
 */
internal class AddClipLaneBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Índice del primer carril de [lanes] cuya franja vertical contiene [yPx], o
 * `null` si el dedo no está sobre ninguno (p. ej. sobre una capa de imagen).
 * Solo se mira el eje Y: horizontalmente el dedo puede pasarse de los bordes
 * (ver [laneLocalX]). Los `null` de la lista (carril aún sin medir) se saltan.
 * Pura, para JUnit.
 */
internal fun addClipLaneIndexAtY(lanes: List<AddClipLaneBounds?>, yPx: Float): Int? {
    for (i in lanes.indices) {
        val lane = lanes[i] ?: continue
        if (yPx >= lane.top && yPx < lane.bottom) return i
    }
    return null
}

/**
 * X (px) del dedo [xWindowPx] relativa al carril [lane], acotada a `0..ancho`:
 * soltar más allá del borde izquierdo ubica el punto al inicio, y más allá del
 * derecho al final. Pura, para JUnit.
 */
internal fun laneLocalX(xWindowPx: Float, lane: AddClipLaneBounds): Float =
    (xWindowPx - lane.left).coerceIn(0f, (lane.right - lane.left).coerceAtLeast(0f))

/**
 * X (px) del borde izquierdo del botón para que quede centrado en [anchorPx]
 * sin salirse del carril de ancho [laneWidthPx]. Si el carril es más angosto
 * que el botón, se alinea al borde izquierdo. Pura, para JUnit.
 */
internal fun addClipButtonLeftPx(anchorPx: Float, laneWidthPx: Float, buttonWidthPx: Float): Float {
    if (laneWidthPx <= buttonWidthPx) return 0f
    return (anchorPx - buttonWidthPx / 2f).coerceIn(0f, laneWidthPx - buttonWidthPx)
}

/**
 * Instante del timeline (ms) bajo la coordenada [xPx] del carril, o `null` si
 * todavía no hay escala ([pxPerMs] <= 0). Acotado a `0..projectDurationMs`.
 * Pura, para JUnit.
 */
internal fun laneTimeMsAt(xPx: Float, pxPerMs: Float, projectDurationMs: Long): Long? {
    if (pxPerMs <= 0f) return null
    return (xPx / pxPerMs).roundToLong().coerceIn(0L, projectDurationMs.coerceAtLeast(0L))
}

/**
 * Rectángulos [occluders] (coordenadas de ventana) expresados en el espacio LOCAL del botón: origen en su esquina
 * izquierda-superior ([buttonTopLeft]), lado [buttonSizePx], que es donde actúa el recorte de su capa gráfica. La capa
 * del recorte no escala (la escala es solo del dibujo interior), así que es una simple traslación. Solo se devuelven
 * los rectángulos que realmente tocan al botón. Pura, para JUnit.
 */
internal fun addClipOccludersLocal(
    occluders: List<Rect>,
    buttonTopLeft: Offset,
    buttonSizePx: Float
): List<Rect> {
    if (occluders.isEmpty() || buttonSizePx <= 0f) return emptyList()
    val result = ArrayList<Rect>(occluders.size)
    for (o in occluders) {
        if (o.width <= 0f || o.height <= 0f) continue
        val local = o.translate(-buttonTopLeft.x, -buttonTopLeft.y)
        if (local.right > 0f && local.left < buttonSizePx && local.bottom > 0f && local.top < buttonSizePx) {
            result += local
        }
    }
    return result
}

/**
 * Forma del botón "+Clip" cuando hay ventanas flotantes encima: el círculo completo MENOS los rectángulos [covered]
 * (espacio local del botón, ver [addClipOccludersLocal]). Visualmente el botón queda DETRÁS de la ventana, y como el
 * recorte también acota el área táctil, los toques sobre la ventana no llegan al botón.
 *
 * Igualdad por contenido: la capa gráfica solo vuelve a calcular el contorno si el recorte realmente cambió.
 */
internal class AddClipBehindWindowsShape(private val covered: List<Rect>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val disc = Path().apply { addOval(Rect(Offset.Zero, size)) }
        val cover = Path().apply { covered.forEach { addRect(it) } }
        return Outline.Generic(Path.combine(PathOperation.Difference, disc, cover))
    }

    override fun equals(other: Any?): Boolean = other is AddClipBehindWindowsShape && other.covered == covered

    override fun hashCode(): Int = covered.hashCode()
}
