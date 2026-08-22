package com.yeivikas.olyzecs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import com.yeivikas.olyzecs.ui.theme.LayerTrackColors
import com.yeivikas.olyzecs.ui.theme.effectiveLayerBrush
import com.yeivikas.olyzecs.ui.theme.gradientBrushFor
import com.yeivikas.olyzecs.ui.theme.effectiveLayerColor
import com.yeivikas.olyzecs.ui.theme.effectiveLayerColorStrong
import com.yeivikas.olyzecs.ui.theme.layerTrackColor
import com.yeivikas.olyzecs.ui.theme.layerTrackColorStrong
import com.yeivikas.olyzecs.data.UserColorPrefs
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.scene.Layer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Públicos (sin `private`) porque EditorScreen los necesita para alinear el
// panel de sección (Keyframes/Control/Rack) exactamente con el pie de la
// regla y el borde de la columna de capas — mismos números, una sola fuente
// de verdad, en vez de duplicar los valores a mano en otro archivo.
val LABEL_COLUMN_WIDTH = 72.dp
private val ROW_HEIGHT = 36.dp
val RULER_HEIGHT = 20.dp
private val DIAMOND_SIZE = 14.dp
private const val TAP_SLOP_PX = 6f

// --- Id "falso" para pedidos de cuentagotas desde el diálogo de
// MULTISELECCIÓN de color (no hay una capa puntual a la que atribuirle el
// pedido, ya que aplica a varias a la vez) — ver el LaunchedEffect(eyedropperResult)
// más abajo en TimelineView, que lo usa para reconocer "esto era para el
// panel de multiselección" y no un id real de capa. No puede colisionar
// con un id real: los ids de capa son UUID.randomUUID().toString().
private const val MULTICOLOR_EYEDROPPER_ID = "__multicolor_batch__"

// --- Master: pista fija/permanente, siempre al pie de las pistas de capas
// (nunca hace scroll con ellas) — igual que el master del playlist de FL
// Studio Mobile. Debajo va la fila para agregar pistas nuevas. ---
private val MASTER_ROW_HEIGHT = 36.dp
private val ADD_TRACK_ROW_HEIGHT = 44.dp
private val ADD_TRACK_BUTTON_SIZE = 30.dp

// Playhead estilo editor profesional (After Effects / Premiere): manija
// triangular arriba + línea vertical fina abajo, en el morado de marca de
// la app (no un color ajeno que compita visualmente). PLAYHEAD_HIT_WIDTH es
// más ancho que lo que se ve — así hay margen cómodo para agarrarlo con el
// dedo sin tener que acertarle al píxel exacto de la línea.
private val PLAYHEAD_HANDLE_WIDTH = 16.dp
private val PLAYHEAD_HANDLE_HEIGHT = 12.dp
private val PLAYHEAD_LINE_WIDTH = 2.dp
private val PLAYHEAD_HIT_WIDTH = 28.dp

/**
 * Timeline visual profesional: una pista por capa (mismo orden que el
 * panel de capas — arriba = más al frente), con un diamante por keyframe
 * que se puede arrastrar para retocar el timing sin usar los sliders, y
 * un playhead compartido que abarca todas las pistas a la vez.
 *
 * Requiere una altura fija del llamador (ver uso en [EditorScreen]) — así
 * el playhead, que se dibuja como overlay superpuesto a las pistas, sabe
 * hasta dónde estirarse sin ambigüedad de constraints.
 */
@Composable
fun TimelineView(
    layers: List<Layer>,
    // --- CORRECCIÓN DEL BUG DE REORDENAR ARRASTRANDO: `layers` sigue
    // siendo la MISMA instancia de List de principio a fin del arrastre
    // (moveLayerUp/moveLayerDown en el ViewModel mutan el `zIndex` de los
    // objetos Layer QUE YA ESTÁN en la lista, no crean objetos Layer
    // nuevos ni cambian el orden del array interno). Como Layer es una
    // data class, `remember(layers)` compara la lista vieja contra la
    // nueva elemento por elemento — pero como son literalmente los MISMOS
    // objetos (misma referencia), cada comparación da igual a sí mismo
    // aunque el zIndex haya cambiado. Compose ve "la lista es igual que
    // antes" y NO recalcula `sortedLayers`, así que el orden visual se
    // queda congelado en el de ANTES de soltar el dedo: eso es exactamente
    // el "regresa a su lugar inicial" que se ve al soltar.
    //
    // `revision` es un contador que el ViewModel ya incrementa en TODA
    // mutación de capas (incluida moveLayerUp/moveLayerDown) — usarlo
    // como llave adicional de `remember` fuerza el recálculo de
    // `sortedLayers` exactamente cuando el zIndex cambió de verdad, sin
    // andar reescribiendo el resto del pipeline de estado. ---
    revision: Int,
    selectedLayerId: String?,
    playheadMs: Long,
    projectDurationMs: Long,
    onSeek: (Long) -> Unit,
    onSelectLayer: (String) -> Unit,
    onRetimeKeyframe: (layerId: String, oldTimeMs: Long, newTimeMs: Long) -> Unit,
    // --- Acciones por capa: antes solo vivían en el panel flotante de
    // arriba a la derecha; ahora cada fila de acá abajo las dispara
    // también, al tocar su miniatura (el master queda afuera de esto). ---
    onToggleLayerVisibility: (String) -> Unit,
    onToggleLayerLock: (String) -> Unit,
    // --- Candado independiente del de arriba: bloquea SOLO el
    // reordenamiento por arrastre en esta columna (ver Layer.orderLocked).
    // No afecta el canvas para nada — pensado justo para el caso de "quiero
    // que esta capa no se mueva de orden pero sigo queriendo tocarla en
    // el preview".
    onToggleLayerOrderLock: (String) -> Unit,
    // --- Reemplazan a las viejas onMoveLayerUp/onMoveLayerDown: el
    // reordenamiento de un paso ya no hace falta como botón porque cada
    // capa se puede arrastrar directamente (ver onReorderLayer más abajo),
    // así que ese espacio del panel ahora dispara renombrar y cambiar de
    // color en su lugar. ---
    onRenameLayer: (layerId: String, newName: String) -> Unit,
    // colorArgb: color ARGB elegido a mano en la rueda de color (ver
    // ColorWheelPicker más abajo) — ya no un índice fijo de paleta, puede
    // ser CUALQUIER color.
    onChangeLayerColor: (layerId: String, colorArgb: Int, useBlackAndWhiteMode: Boolean) -> Unit,
    // Degradado de dos colores (A arriba, B abajo) — panel de degradado
    // dentro de la rueda de color (ver LayerColorPickerDialog).
    onChangeLayerGradient: (layerId: String, startArgb: Int, endArgb: Int, angleDegrees: Float, isRadial: Boolean, useBlackAndWhiteMode: Boolean) -> Unit,
    // Vuelve el color de la capa a automático (el cíclico de la paleta,
    // según colorIndex) — botón "Restablecer" del diálogo de la rueda.
    onResetLayerColor: (layerId: String) -> Unit,
    // --- Versiones "bulk" de las tres de arriba, para "Multicolor"
    // (aplicar a VARIAS capas marcadas de un tirón, ver el diálogo de
    // Multicolor más abajo en este archivo). Por defecto caen a llamar
    // la versión de una sola capa en loop, así ningún caller existente
    // se rompe si no las provee explícitas — pero EditorScreen SÍ las
    // conecta a las funciones `bulk` reales del ViewModel (un solo
    // checkpoint de undo + degradado repartido entre el grupo, en vez de
    // que cada capa marcada reciba el mismo degradado completo repetido
    // — ver comentario largo en EditorViewModel.setLayersGradient). ---
    onChangeMultipleLayersColor: (layerIds: Collection<String>, colorArgb: Int, useBlackAndWhiteMode: Boolean) -> Unit =
        { layerIds, colorArgb, useBW -> layerIds.forEach { onChangeLayerColor(it, colorArgb, useBW) } },
    onChangeMultipleLayersGradient: (layerIds: Collection<String>, startArgb: Int, endArgb: Int, angleDegrees: Float, isRadial: Boolean, useBlackAndWhiteMode: Boolean) -> Unit =
        { layerIds, startArgb, endArgb, angleDegrees, isRadial, useBW ->
            layerIds.forEach { onChangeLayerGradient(it, startArgb, endArgb, angleDegrees, isRadial, useBW) }
        },
    onResetMultipleLayersColor: (layerIds: Collection<String>) -> Unit =
        { layerIds -> layerIds.forEach { onResetLayerColor(it) } },
    // --- Cuentagotas (ver ColorPickerSnapshot / GLRenderer.requestPixelColor):
    // onRequestEyedropper avisa que ESA capa quiere tomar un color del
    // preview en vivo; eyedropperResult trae (layerId, colorArgb) cuando
    // ya está listo — cada fila se fija si el layerId es el suyo antes de
    // usarlo; onConsumeEyedropperResult limpia el resultado ya usado. ---
    onRequestEyedropper: (layerId: String) -> Unit = {},
    eyedropperResult: Pair<String, Int>? = null,
    onConsumeEyedropperResult: () -> Unit = {},
    // --- Reordenamiento por ARRASTRE (distinto de los botones de un paso
    // de arriba): se dispara UNA sola vez al soltar el dedo, con el total
    // de posiciones cruzadas de un tirón. Ver comentario largo en
    // finishReorderDrag() sobre por qué esto ya NO reusa onMoveLayerUp/
    // onMoveLayerDown en un loop. ---
    onReorderLayer: (layerId: String, steps: Int) -> Unit,
    onDeleteLayerRequest: (Layer) -> Unit,
    modifier: Modifier = Modifier,
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    // Se dispara al tocar el "+" de la fila vacía debajo del master —
    // por ahora abre una ventana vacía (ver EditorScreen), a futuro será
    // el punto de entrada para agregar pistas nuevas al playlist.
    onAddTrackClick: () -> Unit = {}
) {
    val sortedLayers = remember(layers, revision) { layers.sortedByDescending { it.zIndex } }
    val density = LocalDensity.current
    val safeDuration = projectDurationMs.coerceAtLeast(1L)

    // --- Reordenar capas arrastrando su miniatura: estado hoisteado ACÁ
    // (no en cada TimelineRow) porque mover una fila tiene que correr a
    // las VECINAS de lugar visualmente mientras se arrastra — y para eso
    // hace falta ver la lista completa, no solo la fila propia.
    //
    // LA CAUSA DEL BUG ANTERIOR (parpadeo/glitch al arrastrar): se estaba
    // llamando a onMoveUp/onMoveDown — que mutan la lista REAL de capas —
    // en cada frame mientras el dedo se movía. Eso forzaba recomposición
    // y relayout de todo el timeline muchas veces por segundo, y ESO es
    // lo que se ve como parpadeo.
    //
    // Ahora: dragOffsetPx acumula el movimiento crudo del dedo (una var
    // simple, sin tocar `layers`). Cada fila calcula su desplazamiento
    // visual a partir de este valor — la fila arrastrada sigue al dedo
    // continuo, y las filas que va "pasando" se corren un alto de fila
    // completo para hacerle espacio, todo puramente visual (offset de
    // dibujo, cero recomposición de la lista). El reordenamiento REAL
    // (onMoveUp/onMoveDown) se aplica UNA SOLA VEZ, al soltar el dedo,
    // así es como lo hacen las apps profesionales. ---
    var draggingLayerId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }

    // --- ARREGLADO: "al soltar la capa se mueve/salta un poco" — al
    // soltar el dedo, ANTES esto reseteaba dragOffsetPx a 0 y aplicaba el
    // reordenamiento real en el MISMO instante, sin transición: la fila
    // pasaba de estar siguiendo al dedo a aparecer de golpe en su casilla
    // final (o volvía de un salto a su posición original si el arrastre no
    // llegó a cruzar media fila) — ese salto sin animar es justo lo que se
    // reportó como "se desplaza un tanto arriba y no se queda donde lo
    // muevo". Ahora, al soltar, el asentamiento final se anima suavemente
    // (settleAnim) en vez de aplicarse de un tirón: la fila arrastrada
    // sigue el mismo camino visual, solo que ahora se desliza hasta su
    // posición real en ~180ms en vez de "teletransportarse". Mientras
    // isSettling es true, el resto de filas ya están en su posición REAL
    // definitiva (el reordenamiento en el ViewModel ya se aplicó), así que
    // solo la fila que se soltó necesita corrección visual.
    val scope = rememberCoroutineScope()
    var isSettling by remember { mutableStateOf(false) }
    val settleAnim = remember { Animatable(0f) }

    val draggingIndex = if (draggingLayerId != null) {
        sortedLayers.indexOfFirst { it.id == draggingLayerId }
    } else {
        -1
    }
    val dragTargetSteps = if (draggingIndex >= 0 && !isSettling) {
        (dragOffsetPx / rowHeightPx).roundToInt()
            .coerceIn(-draggingIndex, sortedLayers.size - 1 - draggingIndex)
    } else {
        0
    }

    // Aplica el reordenamiento real UNA vez (se llama solo al soltar o
    // cancelar el gesto) y limpia el estado de arrastre. Lee todo en vivo
    // al momento de ejecutarse — layers/sortedLayers no cambian mientras
    // se arrastra (a propósito, ver arriba), así que no hay riesgo de
    // usar datos viejos por más que el gesto lleve rato corriendo.
    //
    // ANTES esto llamaba a onMoveLayerUp/onMoveLayerDown en un `repeat`,
    // UNA vez por cada posición cruzada. Cada una de esas llamadas, en el
    // ViewModel, toma su propio checkpoint de undo — y ese checkpoint
    // clona TODOS los keyframes de TODAS las capas del proyecto. Arrastrar
    // una capa de abajo hacia arriba varias posiciones de un tirón (justo
    // el caso de "traer al frente") dispara varios de esos clones completos
    // en la misma fracción de segundo: eso era la demora/traba que se
    // sentía más al subir que al bajar (subir de golpe suele cruzar más
    // filas que bajar una sola). Ahora se manda el total de "steps" de una
    // sola vez a `onReorderLayer`, que en el ViewModel aplica el
    // reordenamiento completo con un ÚNICO checkpoint y una única
    // notificación de cambio — sin importar cuántas filas se hayan
    // cruzado, siempre es una sola operación atómica. ---
    fun finishReorderDrag(layerId: String) {
        val idx = sortedLayers.indexOfFirst { it.id == layerId }
        val startOffset = dragOffsetPx
        if (idx >= 0) {
            val steps = (startOffset / rowHeightPx).roundToInt()
                .coerceIn(-idx, sortedLayers.size - 1 - idx)
            if (steps != 0) onReorderLayer(layerId, steps)
        }
        // No resetear dragOffsetPx/draggingLayerId de golpe: la fila queda
        // "enganchada" (draggingLayerId sigue apuntando a ella) mientras
        // settleAnim la desliza suavemente desde donde el dedo la dejó
        // hasta 0 — que ya corresponde a su casilla real definitiva, porque
        // onReorderLayer (arriba) ya aplicó el nuevo orden ANTES de arrancar
        // la animación.
        isSettling = true
        scope.launch {
            settleAnim.snapTo(startOffset)
            settleAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
            // Por si ya arrancó un arrastre NUEVO (de otra capa) antes de
            // que esta animación terminara: no pisar ese estado más nuevo.
            if (draggingLayerId == layerId) {
                isSettling = false
                draggingLayerId = null
                dragOffsetPx = 0f
            }
        }
    }

    // Cada capa abre/cierra su panel de forma INDEPENDIENTE de las demás
    // (nunca el master, que queda fijo y sin esta función) — tocar la
    // miniatura de otra capa YA NO cierra el panel de la anterior; pueden
    // quedar varios abiertos al mismo tiempo, igual que los canales del
    // mixer de FL Studio Mobile. Antes era un solo String? (acordeón: solo
    // una capa expandida a la vez); ahora es un Set con el id de cada capa
    // que el usuario dejó abierta a propósito.
    var expandedLayerIds by remember { mutableStateOf(setOf<String>()) }

    // --- "Multicolor" (inspirado en FL Studio Mobile/PC: pintar varias
    // pistas/canales de un tirón con el mismo color o degradado) — ver el
    // ícono capas+flecha en la esquina de la regla, más abajo.
    //
    // Flujo: tocar el ícono abre un menú angosto con la única opción
    // "Multicolor" -> tocarla cierra el menú, cambia el ícono a una
    // paleta y activa el modo selección (aparece un punto al lado de
    // cada miniatura) -> con al menos una capa marcada, tocar la paleta
    // abre el diálogo de color/degradado YA existente (LayerColorPickerDialog,
    // reutilizado tal cual) para aplicarlo a TODAS las marcadas de una
    // sola vez -> al cerrar ese diálogo (aplicar, restablecer o cancelar)
    // el modo se apaga solo: los puntos desaparecen y el ícono vuelve a
    // ser capas+flecha. ---
    var multiColorMenuExpanded by remember { mutableStateOf(false) }
    // --- Submenú de segundo nivel, se abre al tocar "Color" en el menú de
    // arriba (ver más abajo) — flota a la DERECHA del ícono capas+flecha
    // (reutiliza BesideAnchorPopupPositionProvider, la misma clase que ya
    // usa el panel de acciones de cada fila) con dos opciones:
    // "Seleccionar todo" (marca TODAS las capas de una — modo lote/batch)
    // y "Manual" (activa el modo selección pero sin marcar ninguna, para
    // que el usuario las vaya tocando una por una — el comportamiento que
    // "Multicolor" ya tenía antes de este cambio). Antes tocar la única
    // opción del menú activaba el modo selección directo; ahora ese primer
    // toque solo abre ESTE segundo paso, que es el que de verdad decide
    // "todo" vs "manual".
    var colorModeSubmenuExpanded by remember { mutableStateOf(false) }
    var multiColorSelectMode by remember { mutableStateOf(false) }
    var multiColorSelectedIds by remember { mutableStateOf(setOf<String>()) }
    var showMultiColorDialog by remember { mutableStateOf(false) }
    // --- Cuentagotas para el diálogo de MULTISELECCIÓN: mismo mecanismo
    // que ya usa cada capa individual (colorPickerResumeSnapshot dentro de
    // TimelineRow, ver más abajo), pero acá arriba porque el diálogo de
    // multiselección vive a nivel de TimelineView, no de una fila. Como
    // este diálogo no tiene una capa puntual (aplica a VARIAS a la vez),
    // el pedido de cuentagotas viaja con un id "falso" (MULTICOLOR_
    // EYEDROPPER_ID) en vez del id real de una capa — EditorScreen no le
    // presta atención a qué id es, solo lo devuelve tal cual en el
    // resultado (ver eyedropperResult), así que sirve igual de bien como
    // etiqueta para "este pedido era del panel de multiselección, no de
    // una fila puntual".
    var multiColorPickerResumeSnapshot by remember { mutableStateOf<ColorPickerSnapshot?>(null) }
    LaunchedEffect(eyedropperResult) {
        val (pickedForId, argb) = eyedropperResult ?: return@LaunchedEffect
        if (pickedForId != MULTICOLOR_EYEDROPPER_ID) return@LaunchedEffect
        val snap = multiColorPickerResumeSnapshot
        if (snap != null) {
            multiColorPickerResumeSnapshot = when (snap.activeSlot) {
                "A" -> snap.copy(gradientAArgb = argb)
                "B" -> snap.copy(gradientBArgb = argb)
                else -> snap.copy(solidArgb = argb)
            }
        }
        showMultiColorDialog = true
        onConsumeEyedropperResult()
    }

    // --- BUG REAL (reportado): tocar el ícono capas+flecha una SEGUNDA vez
    // con el menú "Multicolor" ya abierto no lo cerraba — solo cerraba
    // tocando afuera del todo. Causa: el Popup de abajo es una ventana de
    // Android aparte que vive POR ENCIMA de todo el árbol de Compose, sin
    // importar zIndex; el ícono queda fuera de los límites de esa ventana
    // (el menú se dibuja unos px más abajo), así que un toque sobre el
    // ícono cuenta como "afuera" para el propio Popup: primero dispara
    // onDismissRequest (cierra el menú) y RECIÉN DESPUÉS Android entrega
    // ese mismo toque a la vista de abajo, donde vive el .clickable de
    // este ícono — que en ese instante ve multiColorMenuExpanded ya en
    // false y lo vuelve a poner en true. Las dos cosas pasan en el mismo
    // gesto: se cierra y se reabre sin que se note, como si el segundo
    // toque no hiciera nada.
    //
    // Arreglo: onDismissRequest deja una "bandera" de que el cierre vino
    // de un toque afuera; el .clickable del ícono, si ve esa bandera
    // prendida, la apaga y no vuelve a togglear — así ese mismo toque
    // solo cierra, nunca reabre. Un LaunchedEffect apaga la bandera sola
    // pasado un instante por si el toque fue realmente afuera de TODO
    // (ícono incluido) y nunca llega a este .clickable, para que no deje
    // "atascado" el próximo toque real sobre el ícono.
    var suppressNextLayersIconTap by remember { mutableStateOf(false) }
    LaunchedEffect(suppressNextLayersIconTap) {
        if (suppressNextLayersIconTap) {
            delay(300)
            suppressNextLayersIconTap = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Antes usaba MaterialTheme.colorScheme.surface, un morado
            // LIGERAMENTE distinto al BrandPurpleDeep del relleno que va
            // debajo (en EditorScreen) — esa diferencia de tono era la
            // "costura" visible. Ahora los dos usan el mismo color exacto,
            // así de la última capa para abajo queda todo morado uniforme.
            .background(BrandPurpleDeep)
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val labelWidthPx = with(density) { LABEL_COLUMN_WIDTH.toPx() }
        val trackWidthPx = (totalWidthPx - labelWidthPx).coerceAtLeast(1f)

        // --- Posición del playhead: se calcula ACÁ (antes de la regla) para
        // que el número de tiempo flotante y el triángulo/línea del playhead
        // usen exactamente la misma coordenada X — antes el número vivía fijo
        // en el borde izquierdo de la regla y por eso nunca coincidía con
        // dónde estaba realmente el playhead. ---
        val hitWidthPx = with(density) { PLAYHEAD_HIT_WIDTH.toPx() }
        val currentPlayheadLocalX = (playheadMs.toFloat() / safeDuration) * trackWidthPx
        var dragLocalX by remember { mutableStateOf<Float?>(null) }
        val displayLocalX = dragLocalX ?: currentPlayheadLocalX
        val dragState = rememberDraggableState { delta ->
            val base = dragLocalX ?: currentPlayheadLocalX
            val next = (base + delta).coerceIn(0f, trackWidthPx)
            dragLocalX = next
            onSeek(((next / trackWidthPx) * safeDuration).toLong())
        }

        // El panel de acciones de cada capa es un Popup de Compose de
        // verdad (ver TimelineRow más abajo): un Popup vive en su propia
        // superficie por ENCIMA de cualquier otro composable, playhead
        // incluido, sin importar el orden de hermanos ni zIndex. Por eso
        // este Column YA NO necesita subir su zIndex cuando hay capas
        // expandidas — antes eso "funcionaba" para que el panel se viera
        // por delante, pero como efecto secundario también ponía TODO el
        // bloque de filas (ruler + master + capas) por delante del Box
        // que capta el arrastre del playhead, y Compose reparte los toques
        // según ese mismo orden: con cualquier panel abierto, el Column
        // interceptaba el toque en TODA la barra de tiempo antes de que
        // llegara al playhead, dejándolo sin poder arrastrarse. Sin este
        // zIndex, el playhead vuelve a recibir el toque normalmente,
        // esté expandida una capa o no.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            // --- Regla de tiempo: toca en cualquier punto para mover el playhead.
            // Dibuja marcas de graduación (ticks) + timecodes periódicos, al
            // estilo de un editor profesional (FL Studio / Premiere), en vez
            // de solo dos números sueltos en las puntas — así se lee como una
            // regla delgada y "con textura" en vez de una franja sólida. ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RULER_HEIGHT)
                    .pointerInput(safeDuration, trackWidthPx) {
                        detectTapGestures { offset ->
                            val localX = (offset.x - labelWidthPx).coerceIn(0f, trackWidthPx)
                            onSeek(((localX / trackWidthPx) * safeDuration).toLong())
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRulerTicks(
                        labelWidthPx = labelWidthPx,
                        trackWidthPx = trackWidthPx,
                        durationMs = safeDuration
                    )
                }
                Text(
                    // Timecode del playhead en vivo — ahora pegado al
                    // triángulo/línea real (displayLocalX), no fijo a la
                    // izquierda, así siempre cae exactamente donde está el
                    // playhead de verdad, coincida o no con un tick entero.
                    formatTimelineTimecode(playheadMs),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((labelWidthPx + displayLocalX + 6f).roundToInt(), 0) }
                )
            }


            // --- Master: fijo, permanente, siempre primero debajo de la
            // regla de tiempo — nunca después de las capas que se vayan
            // agregando. Las capas cargadas (imagen/modelo/audio) se apilan
            // DEBAJO del master a medida que se suman. ---
            MasterTrackRow()

            if (sortedLayers.isEmpty()) {
                // Antes usaba weight(1f) para el hueco vacío en sí (sin
                // capas todavía), que estiraba este espacio para ocupar
                // TODO el resto del Column, empujando el botón "+" muy
                // abajo. Ahora tiene una altura chica y fija, así el "+"
                // queda pegado justo debajo del master (como en la
                // referencia de FL Studio Mobile).
                Spacer(modifier = Modifier.height(40.dp))
                AddTrackRow(onClick = onAddTrackClick)
            } else {
                // --- FIX: el botón "+" (AddTrackRow) antes vivía AFUERA de
                // este Column con scroll, apilado justo debajo en el Column
                // padre. Mientras el proyecto tenía pocas capas, entraba
                // todo dentro del heightIn(max=220dp) que le daba
                // EditorScreen al TimelineView entero y se veía bien — pero
                // apenas se agregaban más capas, la suma de alturas (regla
                // + master + capas + separador + "+") superaba ese máximo,
                // y como el Column exterior NO hace scroll, ese sobrante (el
                // "+" y a veces alguna capa) quedaba dibujado más allá del
                // borde visible, inalcanzable — el bug reportado ("el +
                // desaparece detrás del resto de capas").
                //
                // Ahora el "+" vive DENTRO de este mismo Column con
                // verticalScroll, justo después de la última capa cargada:
                // así, sin importar cuántas capas haya, el botón siempre
                // termina el listado y se llega a él con un scroll — nunca
                // queda fijo ni se pierde fuera del área visible.
                //
                // Y en vez de un heightIn(max=164dp) FIJO en dp (que además
                // resultó ser el causante del "tapado": ese tope era más
                // chico que lo que EditorScreen le da ahora al timeline
                // completo, dejando una franja vacía que se confundía con
                // el relleno morado de más abajo), acá se usa weight(1f):
                // este Column se lleva TODO el espacio que sobra dentro del
                // TimelineView (regla y master ya ocupan lo suyo arriba,
                // fijo), sea cual sea la altura real disponible en cada
                // pantalla — y solo entra a tallar scroll cuando el
                // contenido de verdad no entra, nunca antes de tiempo. ---
                // --- BUG REAL (reportado): "Multicolor" aplica el MISMO
                // degradado a varias capas de un tirón, y ya se ve bien en
                // el cuerpo ancho de cada fila (más abajo, rowBodyBrush lo
                // fuerza horizontal — de punta a punta EN CADA fila, con
                // los mismos dos colores siempre, así todas se leen
                // idénticas/uniformes una debajo de otra). Pero la columna
                // angosta de miniaturas (recuadro de 30dp + el fondo de
                // toda esa columna) usa effectiveLayerBrush tal cual, que
                // dibuja el degradado COMPLETO propio de cada capa dentro
                // de su propio recuadro chico — en unos pocos dp de alto,
                // eso se ve como cada miniatura ciclando su propio
                // arcoíris suelto, sin ninguna relación con la de al lado.
                //
                // Acá se arma la versión que sí pidió el usuario
                // (referencia: la columna de pistas de FL Studio de
                // escritorio, un solo tramo de color cayendo a través de
                // TODAS las pistas seguidas): las capas CONSECUTIVAS de
                // sortedLayers que comparten exactamente el mismo
                // degradado (mismo origen — una sola aplicación de
                // "Multicolor" sobre todas ellas) se agrupan, y a cada una
                // se le asigna un tono SÓLIDO propio, muestreado del punto
                // que le toca dentro del degradado completo del grupo —
                // así, leídas de arriba a abajo, las miniaturas del grupo
                // se ven como un único degradado repartido entre todas,
                // en vez de cada una mostrando el ciclo entero por su
                // cuenta. Una capa con degradado propio (no compartido con
                // su vecina) no entra en ningún grupo y sigue mostrando su
                // degradado real de siempre, sin cambios.
                val labelColumnColorOverrides = remember(sortedLayers) {
                    buildMap<String, Color> {
                        var i = 0
                        while (i < sortedLayers.size) {
                            val layer = sortedLayers[i]
                            val start = layer.customGradientStartArgb
                            val end = layer.customGradientEndArgb
                            if (layer.useGradientColor && start != null && end != null) {
                                var j = i
                                while (
                                    j < sortedLayers.size &&
                                    sortedLayers[j].useGradientColor &&
                                    sortedLayers[j].customGradientStartArgb == start &&
                                    sortedLayers[j].customGradientEndArgb == end &&
                                    sortedLayers[j].gradientAngleDegrees == layer.gradientAngleDegrees &&
                                    sortedLayers[j].gradientIsRadial == layer.gradientIsRadial
                                ) {
                                    j++
                                }
                                val runSize = j - i
                                if (runSize >= 2) {
                                    val colorA = Color(start)
                                    val colorB = Color(end)
                                    for (k in i until j) {
                                        val fraction = (k - i + 0.5f) / runSize
                                        put(sortedLayers[k].id, lerp(colorA, colorB, fraction))
                                    }
                                }
                                i = j
                            } else {
                                i++
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    sortedLayers.forEachIndexed { index, layer ->
                        key(layer.id) {
                            // Desplazamiento visual de ESTA fila durante el
                            // arrastre (0 si no hay ningún arrastre activo o
                            // esta fila no está involucrada):
                            // - La fila arrastrada sigue al dedo de forma
                            //   continua (dragOffsetPx crudo, acotado a lo
                            //   que puede viajar dentro de la lista).
                            // - Las filas que quedan "en el medio" entre la
                            //   posición original y la posición actual del
                            //   arrastre se corren un alto de fila completo
                            //   para abrir/cerrar el hueco — así se ve el
                            //   clásico intercambio de un editor profesional.
                            val visualOffsetPx = when {
                                layer.id == draggingLayerId && isSettling -> settleAnim.value
                                layer.id == draggingLayerId -> dragOffsetPx.coerceIn(
                                    -draggingIndex * rowHeightPx,
                                    (sortedLayers.size - 1 - draggingIndex) * rowHeightPx
                                )
                                isSettling || draggingIndex < 0 -> 0f
                                dragTargetSteps > 0 && index in (draggingIndex + 1)..(draggingIndex + dragTargetSteps) -> -rowHeightPx
                                dragTargetSteps < 0 && index in (draggingIndex + dragTargetSteps) until draggingIndex -> rowHeightPx
                                else -> 0f
                            }
                            TimelineRow(
                                layer = layer,
                                labelColumnColorOverride = labelColumnColorOverrides[layer.id],
                                isSelected = layer.id == selectedLayerId,
                                isExpanded = layer.id in expandedLayerIds,
                                isDragging = layer.id == draggingLayerId,
                                visualOffsetPx = visualOffsetPx,
                                trackWidthPx = trackWidthPx,
                                projectDurationMs = safeDuration,
                                onSelect = { onSelectLayer(layer.id) },
                                onToggleExpand = {
                                    expandedLayerIds = if (layer.id in expandedLayerIds) {
                                        expandedLayerIds - layer.id
                                    } else {
                                        expandedLayerIds + layer.id
                                    }
                                },
                                onSeek = onSeek,
                                onRetimeKeyframe = { old, new -> onRetimeKeyframe(layer.id, old, new) },
                                onToggleVisibility = { onToggleLayerVisibility(layer.id) },
                                onToggleLock = { onToggleLayerLock(layer.id) },
                                onToggleOrderLock = { onToggleLayerOrderLock(layer.id) },
                                onRenameRequest = { newName -> onRenameLayer(layer.id, newName) },
                                onChangeColor = { colorArgb, useBW -> onChangeLayerColor(layer.id, colorArgb, useBW) },
                                onChangeGradient = { startArgb, endArgb, angleDegrees, isRadial, useBW -> onChangeLayerGradient(layer.id, startArgb, endArgb, angleDegrees, isRadial, useBW) },
                                onResetColor = { onResetLayerColor(layer.id) },
                                onRequestEyedropper = { onRequestEyedropper(layer.id) },
                                pickedEyedropperColor = eyedropperResult?.takeIf { it.first == layer.id }?.second,
                                onConsumeEyedropperResult = onConsumeEyedropperResult,
                                onDeleteRequest = { onDeleteLayerRequest(layer) },
                                onReorderDragStart = {
                                    isSettling = false
                                    draggingLayerId = layer.id
                                    dragOffsetPx = 0f
                                },
                                onReorderDrag = { deltaY -> dragOffsetPx += deltaY },
                                onReorderDragEnd = { finishReorderDrag(layer.id) },
                                onReorderDragCancel = {
                                    // Mismo asentamiento animado que al soltar
                                    // normalmente, pero sin aplicar ningún
                                    // reordenamiento — el gesto se abortó
                                    // (ej. se sumó/quitó otro dedo), así que
                                    // la fila vuelve suavemente a su lugar
                                    // original en vez de saltar de golpe.
                                    val startOffset = dragOffsetPx
                                    val cancelledLayerId = layer.id
                                    isSettling = true
                                    scope.launch {
                                        settleAnim.snapTo(startOffset)
                                        settleAnim.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                        )
                                        if (draggingLayerId == cancelledLayerId) {
                                            isSettling = false
                                            draggingLayerId = null
                                            dragOffsetPx = 0f
                                        }
                                    }
                                },
                                multiColorSelectActive = multiColorSelectMode,
                                isMultiColorSelected = layer.id in multiColorSelectedIds,
                                onToggleMultiColorSelect = {
                                    multiColorSelectedIds = if (layer.id in multiColorSelectedIds) {
                                        multiColorSelectedIds - layer.id
                                    } else {
                                        multiColorSelectedIds + layer.id
                                    }
                                }
                            )
                        }
                    }

                    // --- Pequeño respiro para que el "+" no quede pegado
                    // contra la última capa cargada, y el botón en sí —
                    // ambos DENTRO del scroll, ver comentario arriba. ---
                    Spacer(modifier = Modifier.height(8.dp))
                    AddTrackRow(onClick = onAddTrackClick)
                }
            }
        }

        // --- Playhead: manija triangular + línea vertical, arrastrable con el
        // dedo desde cualquier punto (estilo After Effects/Premiere), en el
        // morado de marca en vez de un color que compita visualmente. ---
        val playheadColor = MaterialTheme.colorScheme.primary

        // Antes la línea usaba fillMaxHeight() y se estiraba por TODO el
        // panel, incluso por debajo de la última capa cargada (invadiendo
        // el espacio vacío de abajo). Ahora se calcula la altura exacta:
        // regla + master + lo que ocupan las capas. Con el proyecto recién
        // creado (sin capas todavía), la línea termina justo en el borde
        // del master — nada de espacio vacío de más abajo.
        val contentHeight = RULER_HEIGHT + MASTER_ROW_HEIGHT + if (sortedLayers.isEmpty()) {
            0.dp
        } else {
            // Con la expansión ahora horizontal (no vertical), todas las
            // filas miden lo mismo siempre — ya no hace falta distinguir
            // la capa expandida acá.
            (ROW_HEIGHT * sortedLayers.size).coerceAtMost(280.dp)
        }

        // --- Línea separadora entre la columna de miniaturas y el resto
        // del playlist (las pistas a la derecha) — antes no había ningún
        // límite visual claro entre ambas zonas. Es un degradado sutil
        // (se desvanece arriba y abajo, más marcado al centro) en vez de
        // una línea sólida plana, que es como se ven los separadores en
        // editores premium (Figma, Premiere) en vez de un simple borde
        // de 1px a full opacidad. Mismo alto que el playhead — arranca
        // en la regla y termina justo donde termina la última capa. ---
        Box(
            modifier = Modifier
                .offset { IntOffset(labelWidthPx.roundToInt(), 0) }
                .width(1.dp)
                .height(contentHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.White.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .offset { IntOffset((labelWidthPx + displayLocalX - hitWidthPx / 2f).roundToInt(), 0) }
                .width(PLAYHEAD_HIT_WIDTH)
                .height(contentHeight)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        dragLocalX = currentPlayheadLocalX
                        // Pausa la reproducción ANTES de que el primer delta
                        // mueva un solo píxel — si no, el frame que arrastra
                        // el dedo compite con el loop de reproducción tratando
                        // de avanzar por su cuenta al mismo tiempo (visible
                        // como un tironeo/bug raro mientras se sostiene el
                        // playhead sin soltarlo).
                        onScrubStart()
                    },
                    onDragStopped = {
                        dragLocalX = null
                        // Si estaba reproduciendo antes de arrastrar, retoma
                        // la reproducción desde donde se soltó el dedo; si no
                        // estaba reproduciendo, se queda pausado ahí — nunca
                        // arranca solo porque sí.
                        onScrubEnd()
                    }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                drawLine(
                    color = playheadColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = PLAYHEAD_LINE_WIDTH.toPx()
                )
                val handleHalfW = PLAYHEAD_HANDLE_WIDTH.toPx() / 2f
                val handleH = PLAYHEAD_HANDLE_HEIGHT.toPx()
                val handlePath = Path().apply {
                    moveTo(centerX - handleHalfW, 0f)
                    lineTo(centerX + handleHalfW, 0f)
                    lineTo(centerX, handleH)
                    close()
                }
                drawPath(handlePath, color = playheadColor)
            }
        }

        // --- Ícono de capas + flecha (esquina superior izquierda, sobre la
        // columna de miniaturas de la regla): suelto, sin fondo ni círculo,
        // centrado en el ancho Y alto exactos de esa columna
        // (LABEL_COLUMN_WIDTH x RULER_HEIGHT). Tocarlo abre/cierra un menú
        // angosto (mismo ancho que la columna) con la única opción
        // "Multicolor" por ahora.
        //
        // Al tocar "Multicolor": el menú se cierra Y el ícono se transforma
        // a una paleta de color (ver `multiColorSelectMode` más abajo) — a
        // la vez aparece un punto de selección al lado de cada miniatura de
        // capa (dentro de TimelineRow) para marcar varias de un tirón.
        //
        // Con al menos una capa marcada, tocar la paleta abre el MISMO
        // diálogo de color/degradado que ya usa cada capa individual
        // (LayerColorPickerDialog, reutilizado tal cual, ver más abajo) —
        // "Aplicar" ahí pinta TODAS las capas marcadas de una sola vez, en
        // vez de una por una. Al cerrarse ese diálogo (aplicar, restablecer
        // o cancelar), el modo se apaga solo: los puntos desaparecen y el
        // ícono vuelve a ser capas+flecha. Tocar la paleta sin nada marcado
        // también sale del modo, como vía de escape para arrepentirse sin
        // tener que aplicar ningún color.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(LABEL_COLUMN_WIDTH)
                .height(RULER_HEIGHT)
                // Mismo criterio que el resto de overlays de acá arriba: por
                // encima de cualquier zIndex interno del timeline (que como
                // mucho llega a 2f) para no perder el toque contra la regla
                // o el playhead.
                .zIndex(12f)
                .clickable {
                    if (suppressNextLayersIconTap) {
                        // Este toque es el mismo gesto que ya cerró el menú
                        // vía onDismissRequest (ver bandera arriba) — se
                        // consume acá sin togglear nada más.
                        suppressNextLayersIconTap = false
                    } else if (multiColorSelectMode) {
                        if (multiColorSelectedIds.isEmpty()) {
                            multiColorSelectMode = false
                        } else {
                            showMultiColorDialog = true
                        }
                    } else {
                        multiColorMenuExpanded = !multiColorMenuExpanded
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (multiColorSelectMode) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_layer_color),
                    contentDescription = "Aplicar color a las capas marcadas",
                    // Unspecified = respeta los colores propios del drawable
                    // (la paleta ya trae sus manchas de color fijas), mismo
                    // criterio que en el panel de opciones de cada capa.
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_layers),
                        contentDescription = "Capas",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_down),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // --- BUG REAL corregido: "Color" y su submenú "Seleccionar
            // todo"/"Manual" vivían en DOS Popups (ventanas de Android)
            // separadas. Al tocar "Color" se cerraba esa primera ventana
            // (multiColorMenuExpanded = false) para recién abrir la
            // segunda — el usuario veía desaparecer "Color" apenas
            // aparecía el submenú, algo que ninguna app/software
            // profesional hace: un menú en cascada (File > Export, por
            // ejemplo) mantiene SIEMPRE visible el menú padre mientras el
            // hijo está abierto, para que quede claro de dónde salió y el
            // usuario pueda volver con solo mover el dedo/mouse un poco a
            // la izquierda. Ahora ambos viven en el MISMO Popup (misma
            // ventana), uno al lado del otro en una Row: "Color" nunca se
            // oculta al abrir el submenú, solo aparece la segunda tarjeta
            // a su derecha. Un solo Popup también evita el problema real
            // de tener dos ventanas de Android superpuestas: cada Popup
            // tiene su propio listener de "toque afuera", así que tocar
            // el submenú (que cae FUERA de los límites del primer Popup)
            // terminaba disparando el onDismissRequest de "Color" sin
            // querer.
            if (multiColorMenuExpanded) {
                Popup(
                    popupPositionProvider = BelowAnchorPopupPositionProvider(gapPx = 4),
                    onDismissRequest = {
                        multiColorMenuExpanded = false
                        colorModeSubmenuExpanded = false
                        suppressNextLayersIconTap = true
                    },
                    properties = PopupProperties(focusable = false)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Surface(
                            modifier = Modifier
                                .width(LABEL_COLUMN_WIDTH)
                                .shadow(elevation = 8.dp, shape = RectangleShape),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RectangleShape
                        ) {
                            Text(
                                text = "Color",
                                color = Color.White,
                                // Fuente chica a propósito: 72dp de ancho de
                                // columna es angosto para la palabra completa —
                                // este tamaño es el que la deja entrar en una
                                // sola línea, centrada, sin cortarla con "…".
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Ya NO activa el modo selección acá
                                        // directo — solo abre el submenú de la
                                        // derecha, que es el que decide entre
                                        // "Seleccionar todo" y "Manual" (ver
                                        // doc de colorModeSubmenuExpanded más
                                        // arriba). "Color" se queda abierto:
                                        // ver nota de arriba sobre por qué el
                                        // padre no se cierra al abrir el hijo.
                                        colorModeSubmenuExpanded = true
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }

                        // --- Submenú "Seleccionar todo" / "Manual": aparece a
                        // la DERECHA de "Color", DENTRO del mismo Popup/misma
                        // ventana — como una tarjeta angosta con dos filas con
                        // ícono, pensada para que esta misma ventanita, más
                        // adelante, también cubra otros modos de selección
                        // por lote (ver pedido del usuario: "esta ventana...
                        // estará pensada en cubrir multi, lotes o también
                        // manual").
                        if (colorModeSubmenuExpanded) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                modifier = Modifier
                                    .width(168.dp)
                                    .shadow(elevation = 10.dp, shape = RectangleShape),
                                color = SurfaceTintedElevated,
                                shape = RectangleShape,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                            ) {
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    ColorModeSubmenuItem(
                                        // Antes reutilizaba ic_grid — el
                                        // MISMO ícono de la cuadrícula de
                                        // guías de la barra superior. A
                                        // pedido del usuario, ahora tiene
                                        // uno propio (ver ic_select_all_checks.xml)
                                        // para que no se confundan.
                                        iconRes = R.drawable.ic_select_all_checks,
                                        // Antes usaba BrandPurpleLight acá
                                        // (recuadro morado, distinto a
                                        // "Manual") — a pedido del usuario,
                                        // mismo tratamiento neutro gris que
                                        // "Manual" para los dos, blanco
                                        // deseleccionado por default.
                                        iconTint = Color.White.copy(alpha = 0.85f),
                                        iconSize = 21.dp,
                                        label = "Seleccionar todo",
                                        description = "Autoseleccionar",
                                        onClick = {
                                            multiColorMenuExpanded = false
                                            colorModeSubmenuExpanded = false
                                            multiColorSelectMode = true
                                            multiColorSelectedIds = sortedLayers.map { it.id }.toSet()
                                        }
                                    )
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.08f),
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                    ColorModeSubmenuItem(
                                        iconRes = R.drawable.ic_edit,
                                        iconTint = Color.White.copy(alpha = 0.85f),
                                        label = "Manual",
                                        description = "Elegí una por una",
                                        onClick = {
                                            multiColorMenuExpanded = false
                                            colorModeSubmenuExpanded = false
                                            multiColorSelectMode = true
                                            multiColorSelectedIds = emptySet()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Diálogo de color/degradado para "Multicolor": es el MISMO
        // composable que ya usa cada capa individual (LayerColorPickerDialog
        // más abajo en este archivo) — no una copia ni una versión reducida.
        // La diferencia entera está en los callbacks: onSelectColor/
        // onSelectGradient/onReset de acá no reciben un layerId puntual,
        // reparten la acción a TODAS las capas marcadas de un tirón.
        if (showMultiColorDialog) {
            // Semilla del diálogo: si TODAS las capas marcadas comparten
            // exactamente el mismo color personalizado, arranca desde ese
            // color (para "afinar" un tono ya aplicado a todas). Si difieren
            // entre sí, o alguna todavía usa el color automático de la
            // paleta, arranca sin preseleccionar nada — así no se le impone
            // a todo el grupo el color de una sola capa al azar.
            val markedLayers = sortedLayers.filter { it.id in multiColorSelectedIds }
            val seedColorArgb = markedLayers.map { it.customColorArgb }.distinct().singleOrNull()

            // BUG REAL corregido: esta semilla ignoraba por completo el
            // degradado ya aplicado — siempre mandaba
            // initialUseGradient = false + start/end en null, sin importar
            // que las capas marcadas tuvieran el MISMO degradado puesto.
            // Resultado: reabrir el panel con exactamente el mismo grupo de
            // capas al que se le aplicó un degradado lo mostraba "apagado"
            // en vez de restaurado — parecía que el degradado no se
            // guardaba, cuando en realidad sí quedaba guardado en cada
            // Layer (useGradientColor/customGradientStartArgb/
            // customGradientEndArgb/gradientAngleDegrees/gradientIsRadial),
            // solo que el diálogo nunca lo leía de vuelta para este caso
            // multicapa.
            //
            // Mismo criterio que ya se usaba para el color sólido: el
            // degradado se restaura ÚNICAMENTE si TODAS las capas
            // marcadas — ni una más, ni una menos que las que lo
            // recibieron — comparten exactamente el mismo degradado
            // (mismo A, mismo B, mismo ángulo, mismo radial/lineal). Con
            // una selección distinta (otra cantidad u otras capas, o un
            // grupo con degradados mezclados/parcial) arranca apagado y
            // sin preseleccionar, tal como corresponde a un grupo que no
            // comparte ese estado.
            val allMarkedUseGradient = markedLayers.isNotEmpty() && markedLayers.all { it.useGradientColor }
            val seedGradientStartArgb = if (allMarkedUseGradient) {
                markedLayers.map { it.customGradientStartArgb }.distinct().singleOrNull()
            } else null
            val seedGradientEndArgb = if (allMarkedUseGradient) {
                markedLayers.map { it.customGradientEndArgb }.distinct().singleOrNull()
            } else null
            val seedGradientAngle = if (allMarkedUseGradient) {
                markedLayers.map { it.gradientAngleDegrees }.distinct().singleOrNull()
            } else null
            val seedGradientIsRadial = if (allMarkedUseGradient) {
                markedLayers.map { it.gradientIsRadial }.distinct().singleOrNull()
            } else null
            // Consistente de verdad solo si TODOS los campos del degradado
            // coinciden a la vez — un solo campo distinto (por ejemplo el
            // ángulo) ya significa que no es "el mismo" degradado para
            // todo el grupo.
            val gradientFullyConsistent = allMarkedUseGradient &&
                seedGradientStartArgb != null &&
                seedGradientEndArgb != null &&
                seedGradientAngle != null &&
                seedGradientIsRadial != null

            // Mismo criterio que el color/degradado de arriba: el modo
            // Negro & Blanco se restaura ÚNICAMENTE si TODAS las capas
            // marcadas lo tienen prendido — un grupo mixto (algunas en
            // B&W, otras no) arranca con el switch apagado, sin imponerle
            // a todo el grupo el estado de una sola capa al azar.
            val allMarkedUseBlackAndWhite = markedLayers.isNotEmpty() &&
                markedLayers.all { it.useBlackAndWhiteMode }

            // Si hay un snapshot pendiente (reabierto después del
            // cuentagotas), manda ESO por encima de la semilla calculada
            // del grupo — mismo criterio que ya usa el diálogo de capa
            // individual, para retomar exactamente donde se dejó con el
            // color nuevo ya adentro.
            val snap = multiColorPickerResumeSnapshot

            LayerColorPickerDialog(
                initialColorArgb = snap?.solidArgb ?: seedColorArgb,
                initialGradientStartArgb = snap?.gradientAArgb
                    ?: (if (gradientFullyConsistent) seedGradientStartArgb else null),
                initialGradientEndArgb = snap?.gradientBArgb
                    ?: (if (gradientFullyConsistent) seedGradientEndArgb else null),
                initialUseGradient = snap?.gradientEnabled ?: gradientFullyConsistent,
                initialGradientAngleDegrees = snap?.gradientAngleDegrees ?: (seedGradientAngle ?: 90f),
                initialGradientIsRadial = snap?.gradientIsRadial ?: (seedGradientIsRadial ?: false),
                initialBlackAndWhiteMode = snap?.blackAndWhiteMode ?: allMarkedUseBlackAndWhite,
                initialActiveSlot = snap?.activeSlot,
                fallbackColorArgb = seedColorArgb ?: Color.White.toArgb(),
                onDismiss = {
                    // Cerrar este diálogo por CUALQUIER vía (Aplicar,
                    // Restablecer o Cancelar) apaga el modo Multicolor
                    // entero: los puntos desaparecen y el ícono vuelve a
                    // ser capas+flecha, tal como se pidió para el caso de
                    // "Aplicar" — se extiende igual a las otras dos salidas
                    // porque dejar el modo activo con el diálogo ya cerrado
                    // sería un estado a medias, no un comportamiento nuevo
                    // que el usuario haya pedido evitar.
                    showMultiColorDialog = false
                    multiColorPickerResumeSnapshot = null
                    multiColorSelectMode = false
                    multiColorSelectedIds = emptySet()
                },
                // BUG REAL corregido: esto antes hacía
                // `multiColorSelectedIds.forEach { onChangeLayerGradient(id, mismoStart, mismoEnd, ...) }`
                // — pintaba el degradado A→B COMPLETO adentro de CADA capa
                // marcada por separado (todas mostrando el mismo degradado
                // entero, cada una ciclando el suyo propio), en vez de UN
                // SOLO degradado repartido a lo largo de las capas del
                // grupo — que es justo el resultado de la referencia de FL
                // Studio de escritorio. Las versiones bulk (ver
                // EditorViewModel.setLayersGradient) reparten un tono por
                // posición dentro del grupo y toman UN solo checkpoint de
                // undo para la acción completa.
                onSelectColor = { argb, useBW ->
                    multiColorPickerResumeSnapshot = null
                    onChangeMultipleLayersColor(multiColorSelectedIds, argb, useBW)
                },
                onSelectGradient = { startArgb, endArgb, angleDegrees, isRadial, useBW ->
                    multiColorPickerResumeSnapshot = null
                    onChangeMultipleLayersGradient(multiColorSelectedIds, startArgb, endArgb, angleDegrees, isRadial, useBW)
                },
                onReset = {
                    multiColorPickerResumeSnapshot = null
                    onResetMultipleLayersColor(multiColorSelectedIds)
                },
                // --- Cuentagotas en multiselección: EXACTAMENTE lo que
                // pediste — guarda todo lo armado hasta ahora, cierra ESTE
                // diálogo (tiene que desaparecer para que el usuario pueda
                // tocar el preview, que vive detrás en otra ventana) y
                // avisa hacia arriba con el id "falso" de multiselección
                // (ver MULTICOLOR_EYEDROPPER_ID) en vez del id de una capa
                // puntual — no hay una sola capa a la que atribuirle este
                // pedido, aplica al grupo entero marcado.
                onRequestEyedropper = { snapshot ->
                    multiColorPickerResumeSnapshot = snapshot
                    showMultiColorDialog = false
                    onRequestEyedropper(MULTICOLOR_EYEDROPPER_ID)
                }
            )
        }
    }
}

/**
 * Fila del "master": permanente, fija y predeterminada — siempre está
 * presente sin importar cuántas capas tenga el proyecto, siempre justo
 * debajo de la regla de tiempo (ANTES de las capas cargadas, nunca
 * después), y nunca hace scroll junto con ellas (por eso vive fuera del
 * Column con verticalScroll, como hermano suyo). Es una fila de
 * referencia visual por ahora — sin keyframes propios — pensada como
 * anclaje para lo que se sume después (volumen/mezcla global, etc.).
 */
@Composable
private fun MasterTrackRow() {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MASTER_ROW_HEIGHT)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        ) {
            Text(
                "Master",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(LABEL_COLUMN_WIDTH)
                    .fillMaxHeight()
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(start = 8.dp, end = 4.dp)
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * Fila vacía debajo del master con un ícono "+" al centro (referencia:
 * playlist de FL Studio Mobile). Al tocarlo dispara [onClick] — hoy abre
 * una ventana todavía vacía (ver [EditorScreen]), lista para definirse
 * más adelante.
 */
@Composable
private fun AddTrackRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ADD_TRACK_ROW_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        // Antes era un IconButton de Material3, que impone un tamaño
        // mínimo de toque de 48dp por debajo — eso peleaba con el
        // .size(30dp) explícito y recortaba el círculo (se veía con la
        // base plana en vez de redondo). Un Box + clickable a mano no
        // tiene ese mínimo oculto, así el círculo queda perfecto.
        Box(
            modifier = Modifier
                .size(ADD_TRACK_BUTTON_SIZE)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = "Agregar pista",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TimelineRow(
    layer: Layer,
    // --- Ver el bloque grande de comentario en TimelineView, justo antes
    // del Column con las filas: no-null cuando esta capa forma parte de
    // un grupo de "Multicolor" (varias capas consecutivas con el MISMO
    // degradado) — en ese caso la columna de miniaturas usa este tono
    // sólido en vez del degradado completo local de la capa, para que el
    // grupo entero se lea como un solo degradado cayendo a través de
    // todas. Null = comportamiento de siempre (degradado real de la capa,
    // sin tocar nada).
    labelColumnColorOverride: Color?,
    isSelected: Boolean,
    isExpanded: Boolean,
    isDragging: Boolean,
    visualOffsetPx: Float,
    trackWidthPx: Float,
    projectDurationMs: Long,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onSeek: (Long) -> Unit,
    onRetimeKeyframe: (oldTimeMs: Long, newTimeMs: Long) -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleOrderLock: () -> Unit,
    // --- Reemplazan a las viejas flechas subir/bajar: como reordenar ya
    // se hace arrastrando la miniatura (ver pointerInput más abajo), esos
    // dos botones quedaron libres para dos acciones que antes no tenían
    // atajo directo desde la fila: renombrar la capa y cambiarle el color
    // de identidad. ---
    onRenameRequest: (newName: String) -> Unit,
    onChangeColor: (colorArgb: Int, useBlackAndWhiteMode: Boolean) -> Unit,
    onChangeGradient: (startArgb: Int, endArgb: Int, angleDegrees: Float, isRadial: Boolean, useBlackAndWhiteMode: Boolean) -> Unit,
    onResetColor: () -> Unit,
    // --- Cuentagotas: ver comentario largo en ColorPickerSnapshot. onRequestEyedropper
    // avisa "esta capa quiere tomar un color del preview en vivo";
    // pickedEyedropperColor llega con el resultado cuando ya está listo
    // (solo si fue ESTA capa la que lo pidió — EditorScreen filtra por
    // layerId antes de pasarlo). onConsumeEyedropperResult limpia el
    // resultado para que no se vuelva a aplicar en la próxima recomposición. ---
    onRequestEyedropper: () -> Unit,
    pickedEyedropperColor: Int?,
    onConsumeEyedropperResult: () -> Unit,
    onDeleteRequest: () -> Unit,
    onReorderDragStart: () -> Unit,
    onReorderDrag: (deltaY: Float) -> Unit,
    onReorderDragEnd: () -> Unit,
    onReorderDragCancel: () -> Unit,
    // --- "Multicolor": ver el ícono capas+flecha en TimelineView. Cuando
    // multiColorSelectActive es true, esta fila muestra un punto de
    // selección al lado de su miniatura (relleno si isMultiColorSelected,
    // solo el contorno si no) — tocarlo dispara onToggleMultiColorSelect
    // en vez del tap normal de seleccionar/expandir la capa. ---
    multiColorSelectActive: Boolean = false,
    isMultiColorSelected: Boolean = false,
    onToggleMultiColorSelect: () -> Unit = {}
) {
    // Se lee directo de cameraTrack.keyframes: EditorScreen ya fuerza
    // recomposición vía `revision` cada vez que cambian, así que esta fila
    // siempre ve la lista al día sin necesitar su propio estado.
    val keyframes = layer.cameraTrack.keyframes

    // --- LA CAUSA REAL DEL BUG "sube pero al soltar vuelve abajo":
    // `.pointerInput(layer.id) { ... }` más abajo arranca su corrutina de
    // gesto UNA sola vez para cada layer.id, y la mantiene viva de
    // recomposición en recomposición (Compose NO la reinicia si la key no
    // cambia). El problema es que ese bloque, la PRIMERA vez que se arma,
    // capturaba directamente los parámetros onReorderDragStart/Drag/
    // DragEnd/DragCancel de ESA composición puntual — y como la corrutina
    // nunca se reinicia, TODOS los arrastres futuros sobre esa misma fila
    // seguían llamando a esas mismas lambdas "congeladas" de la primera
    // vez, con `sortedLayers` de aquel momento (antes de cualquier
    // reordenamiento). Por eso el PRIMER arrastre de cada capa funcionaba
    // bien (bajar la capa naranja, por ejemplo) pero el SIGUIENTE arrastre
    // sobre esa misma capa (subirla de vuelta) calculaba los límites y los
    // "steps" con el orden VIEJO, y terminaba devolviéndola a donde estaba
    // antes en vez de a donde se soltó el dedo.
    //
    // `rememberUpdatedState` es el patrón estándar de Compose para esto:
    // envuelve cada callback en un State que SÍ se actualiza en cada
    // recomposición, y la corrutina de pointerInput lee `.value` en el
    // momento de disparar el evento — siempre la versión más reciente,
    // nunca una congelada. ---
    val currentOnReorderDragStart by rememberUpdatedState(onReorderDragStart)
    val currentOnReorderDrag by rememberUpdatedState(onReorderDrag)
    val currentOnReorderDragEnd by rememberUpdatedState(onReorderDragEnd)
    val currentOnReorderDragCancel by rememberUpdatedState(onReorderDragCancel)

    // --- Reordenar capas arrastrando su miniatura (mantener presionado +
    // arrastrar arriba/abajo), estilo editor profesional. El estado real
    // del arrastre (dragOffsetPx, a qué capa se está arrastrando) vive en
    // el TimelineView padre — acá solo se recibe el resultado ya
    // calculado (visualOffsetPx) y se avisa hacia arriba de los eventos
    // del gesto (empezar / mover / soltar / cancelar). Así una sola fuente
    // de verdad decide cómo se corren TODAS las filas, no cada una por su
    // cuenta. ---
    // --- Color de identidad de la capa (ver Theme.kt/layerTrackColor): se
    // pinta como un lavado de color de fondo en TODA la fila (miniatura +
    // pista), constante mientras la capa exista, para distinguir cada
    // capa de un vistazo igual que los canales de FL Studio Mobile. Va
    // DEBAJO del overlay blanco de selección/arrastre (que se sigue
    // dibujando encima, sin cambios) para que "seleccionada" siga
    // notándose incluso sobre el color propio de la capa. ---
    // BUG REAL: effectiveLayerColor/effectiveLayerColorStrong SÍ leen
    // useGradientColor/customGradientStartArgb/customGradientEndArgb por
    // dentro (para aplanar el degradado a un solo tono cuando hace
    // falta), pero el remember() de acá abajo NO los tenía como
    // dependencia — si el usuario prendía el degradado o cambiaba sus
    // extremos SIN tocar customColorArgb en la misma acción, este valor
    // quedaba pegado al color viejo hasta que algo más disparara una
    // recomposición. Ahora estas dos dependen de exactamente los mismos
    // campos que trackBrush de abajo.
    val trackColor = remember(layer.colorIndex, layer.customColorArgb, layer.useGradientColor, layer.customGradientStartArgb, layer.customGradientEndArgb) { effectiveLayerColor(layer) }
    // --- Versión "fuerte"/saturada del mismo color, solo para el recuadro
    // de la miniatura y el panel de opciones desplegado — el resto de la
    // barra (arriba, trackColor.copy(alpha=0.30f)) se queda con el color
    // suave tal cual ya estaba. Mismo matiz en toda la fila, distinta
    // intensidad según la zona. ---
    val trackColorStrong = remember(layer.colorIndex, layer.customColorArgb, layer.useGradientColor, layer.customGradientStartArgb, layer.customGradientEndArgb) { effectiveLayerColorStrong(layer) }
    // --- Versión Brush (en vez de Color plano) de lo mismo: si la capa
    // tiene un DEGRADADO activo (ver rueda de color → panel de degradado),
    // esto entrega el degradado real de dos colores en vez de aplanarlo a
    // un solo tono — así el degradado se ve de verdad en la fila, no solo
    // queda guardado sin mostrarse. Si la capa no tiene degradado, es
    // exactamente el mismo color sólido de siempre (envuelto en Brush). ---
    val trackBrush = remember(layer.colorIndex, layer.customColorArgb, layer.useGradientColor, layer.customGradientStartArgb, layer.customGradientEndArgb, layer.gradientAngleDegrees, layer.gradientIsRadial) {
        effectiveLayerBrush(layer)
    }
    // --- Igual que trackBrush, pero SOLO para la columna de miniaturas
    // (fondo de la columna + recuadro de 30dp) — ver labelColumnColorOverride
    // arriba. Con override (grupo de "Multicolor"), es el tono sólido que
    // le toca a esta fila dentro del degradado del grupo; sin override
    // (capa suelta, degradado propio), es exactamente trackBrush, sin
    // ningún cambio de comportamiento respecto a antes.
    val labelColumnBrush = remember(labelColumnColorOverride, trackBrush) {
        labelColumnColorOverride?.let { SolidColor(it) } ?: trackBrush
    }
    // --- EL BUG REAL (la corrección anterior tampoco alcanzaba): existían
    // DOS lugares separados pintando el color de identidad de la fila —
    // labelColumnBrush (arriba, para la miniatura de 30dp) y este
    // rowBodyBrush de acá, para la barra ANCHA de toda la fila. La
    // corrección anterior only tocó labelColumnBrush con el override de
    // grupo (tono sólido repartido entre las capas del "Multicolor"), pero
    // rowBodyBrush seguía leyendo layer.customGradientStartArgb/EndArgb
    // DIRECTO de la capa y armando SU PROPIO degradado A→B de punta a
    // punta — que es justo la barra ancha que más se nota en pantalla (el
    // fondo rojizo/morado/azul detrás del nombre del archivo en cada
    // fila). Por eso, aunque la miniatura chica ya se veía bien agrupada,
    // la barra ancha —lo que el usuario en realidad está mirando y
    // comparando contra la columna de FL Studio— seguía mostrando cada
    // capa ciclando su propio degradado suelto, sin ninguna relación con
    // la de al lado.
    //
    // Ahora rowBodyBrush usa EXACTAMENTE la misma prioridad que
    // labelColumnBrush: si esta fila forma parte de un grupo de
    // "Multicolor" (labelColumnColorOverride no-null), se pinta con el
    // tono sólido que le toca dentro del degradado repartido entre TODO
    // el grupo — ni más ni menos que labelColumnBrush — así la miniatura
    // y la barra ancha de una misma fila SIEMPRE muestran el mismo tono,
    // y leídas de arriba a abajo el grupo entero se lee como un solo
    // degradado continuo cayendo a través de todas las pistas, igual que
    // en la referencia de FL Studio de escritorio. Una capa suelta (sin
    // grupo, degradado propio de una sola capa vía el diálogo individual)
    // sigue mostrando su degradado real A→B horizontal como siempre, sin
    // ningún cambio de comportamiento.
    val rowBodyBrush = remember(labelColumnColorOverride, layer.colorIndex, layer.customColorArgb, layer.useGradientColor, layer.customGradientStartArgb, layer.customGradientEndArgb, layer.gradientIsRadial, layer.gradientAngleDegrees) {
        val override = labelColumnColorOverride
        when {
            override != null -> SolidColor(override)
            layer.useGradientColor -> {
                val start = layer.customGradientStartArgb
                val end = layer.customGradientEndArgb
                if (start != null && end != null) {
                    // BUG REAL corregido: acá el ángulo estaba hardcodeado a
                    // 0f (siempre horizontal, izquierda→derecha) sin importar
                    // lo que el usuario eligiera en el diálogo. La miniatura
                    // de al lado (trackBrush/labelColumnBrush) SÍ usaba
                    // layer.gradientAngleDegrees — con el ángulo por defecto
                    // de 90° (arriba→abajo), la miniatura y la barra ancha de
                    // la MISMA fila terminaban mostrando el degradado en dos
                    // direcciones distintas, lo que se lee como "dos capas de
                    // color" superpuestas en vez de una sola.
                    gradientBrushFor(Color(start), Color(end), angleDegrees = layer.gradientAngleDegrees, isRadial = layer.gradientIsRadial)
                } else {
                    trackBrush
                }
            }
            else -> trackBrush
        }
    }
    // --- Mismo criterio para el tono "fuerte" usado en el borde del panel
    // desplegado y el diálogo de renombrar (ver más abajo, trackColorStrong
    // → accentColor / border): si la fila pertenece a un grupo de
    // "Multicolor", esos acentos también deben leer el tono sólido del
    // grupo, no el degradado propio (aplanado) de la capa suelta — así
    // TODO lo que identifica visualmente a esta fila (miniatura, barra
    // ancha, panel, diálogo) concuerda entre sí.
    val effectiveTrackColorStrong = remember(labelColumnColorOverride, trackColorStrong) {
        labelColumnColorOverride ?: trackColorStrong
    }

    // --- Mini ventanas de las dos acciones nuevas del panel (lápiz =
    // renombrar, paleta = color) — viven acá, por fila, en vez de subir
    // como estado al TimelineView padre: cada fila solo necesita saber si
    // SU PROPIO diálogo está abierto, no tiene sentido compartir ese
    // estado con las demás capas. ---
    var showRenameDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    // Foto de lo que había armado en el diálogo justo antes de pedir el
    // cuentagotas — null = abrir el diálogo "fresco" desde el color
    // actual de la capa (caso normal). No-null = reabrirlo retomando
    // exactamente ese estado, con el color tomado ya cargado adentro.
    var colorPickerResumeSnapshot by remember { mutableStateOf<ColorPickerSnapshot?>(null) }

    // Cuando llega un color tomado con el cuentagotas PARA ESTA CAPA, lo
    // mete en el destino que estaba activo en el snapshot pendiente y
    // reabre el diálogo — el usuario nunca pierde lo que ya tenía armado
    // (otro extremo del degradado, ángulo elegido, etc.), solo se agrega
    // el color nuevo en el lugar exacto que estaba tocando.
    LaunchedEffect(pickedEyedropperColor) {
        val picked = pickedEyedropperColor ?: return@LaunchedEffect
        val snap = colorPickerResumeSnapshot
        if (snap != null) {
            colorPickerResumeSnapshot = when (snap.activeSlot) {
                "A" -> snap.copy(gradientAArgb = picked)
                "B" -> snap.copy(gradientBArgb = picked)
                else -> snap.copy(solidArgb = picked)
            }
        }
        showColorPickerDialog = true
        onConsumeEyedropperResult()
    }


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .offset { IntOffset(0, visualOffsetPx.roundToInt()) }
            .background(brush = rowBodyBrush, alpha = 0.30f)
            // Mientras se arrastra, esta fila se eleva por encima de las
            // vecinas por las que va pasando (zIndex) y se le da una
            // sombra + fondo propio, para que se sienta como una
            // "tarjeta" que se levanta del playlist — no un simple
            // parpadeo de posición.
            .zIndex(if (isDragging) 10f else 0f)
            .then(
                if (isDragging) {
                    Modifier
                        .shadow(elevation = 6.dp, shape = androidx.compose.ui.graphics.RectangleShape)
                        .background(SurfaceTintedElevated)
                } else {
                    Modifier
                }
            )
    ) {
        // --- Columna izquierda: la miniatura de la capa. Primer toque en
        // cualquier parte de esta columna (miniatura incluida) SOLO
        // selecciona la capa; recién con la capa ya seleccionada, un toque
        // más sobre la miniatura despliega sus controles (subir, bajar,
        // ojo, candado, eliminar) — dos pasos separados, no uno solo. La
        // miniatura tiene SIEMPRE el mismo tamaño y la misma posición,
        // expandida o no, y el panel de íconos es un Popup de Compose de
        // verdad: un Popup vive en su propia superficie por ENCIMA de
        // cualquier otro composable (el playhead incluido), así que no
        // hace falta ningún truco de zIndex ni de fondo opaco para que
        // quede delante — siempre gana. El master no tiene nada de esto,
        // queda tal cual estaba.
        Row(
            modifier = Modifier
                .width(LABEL_COLUMN_WIDTH)
                .fillMaxHeight()
                // Antes esta columna se quedaba Transparent y dejaba ver
                // el color SUAVE de la fila por detrás (trackColor al 30%)
                // — por eso la miniatura se veía "lavada" en vez de tan
                // saturada como el panel de opciones. Ahora toda la
                // columna de la miniatura usa el mismo trackColorStrong
                // que el panel, y el blanco de selección se dibuja ENCIMA
                // (no en reemplazo), así "seleccionada" se sigue notando.
                .background(labelColumnBrush)
                .background(if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                // Tocar cualquier parte de esta columna que no sea la
                // miniatura (el padding alrededor) solo SELECCIONA — nunca
                // abre el panel. La miniatura tiene su propio clickable más
                // abajo con la lógica de dos pasos.
                //
                // En modo Multicolor (multiColorSelectActive), este mismo
                // toque pasa a marcar/desmarcar la capa para el color en
                // conjunto en vez de seleccionarla para editar — toda la
                // columna es área de toque válida, no solo el punto chico
                // que se dibuja como indicador visual.
                .clickable {
                    if (multiColorSelectActive) onToggleMultiColorSelect() else onSelect()
                }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // --- Punto de selección de "Multicolor": solo visible mientras
            // multiColorSelectActive es true. Relleno + tilde cuando esta
            // capa ya está marcada, solo el contorno si no — mismo lenguaje
            // visual que un checkbox circular. Vive ANTES de la miniatura
            // (a su izquierda), tal como se pidió. ---
            if (multiColorSelectActive) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (isMultiColorSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f)
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isMultiColorSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .clickable { onToggleMultiColorSelect() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isMultiColorSelected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    // 30dp → 34dp → 36dp: la fila (ROW_HEIGHT) tiene
                    // exactamente 36dp de alto y el Row padre no tiene
                    // padding vertical (solo horizontal), así que la
                    // miniatura puede llenar el alto COMPLETO de su fila
                    // sin recortarse ni solaparse con la de al lado — antes
                    // sobraban 2dp de "aire" sin usar en vez de
                    // aprovecharse como recuadro.
                    .size(ROW_HEIGHT)
                    .clip(RoundedCornerShape(4.dp))
                    // Recuadro de la miniatura: mismo matiz que la barra pero
                    // más saturado/potente (ver trackColorStrong arriba), en
                    // vez de quedar transparente — así se distingue de un
                    // vistazo incluso donde la imagen de la capa tenga zonas
                    // transparentes.
                    .background(labelColumnBrush)
                    .clickable {
                        // Modo Multicolor activo: cualquier toque acá marca/
                        // desmarca esta capa para el color en conjunto — se
                        // suspende el paso normal de seleccionar/expandir
                        // mientras se está eligiendo el grupo.
                        if (multiColorSelectActive) {
                            onToggleMultiColorSelect()
                            return@clickable
                        }
                        // Dos pasos, como FL Studio Mobile y cualquier
                        // editor "pro": el primer toque sobre la capa
                        // (esta miniatura incluida) solo la SELECCIONA.
                        // Recién con la capa YA seleccionada, un toque más
                        // sobre esta misma miniatura dispara su panel de
                        // opciones. Antes ambas cosas pasaban en el mismo
                        // toque — por eso siempre se abría el panel apenas
                        // tocabas para elegir la capa, sin darte chance de
                        // solo seleccionar.
                        if (isSelected) {
                            onToggleExpand()
                        } else {
                            onSelect()
                        }
                    }
                    // --- Mantener presionada la miniatura y arrastrar
                    // arriba/abajo reordena esa capa dentro del playlist
                    // (con TODO su contenido: keyframes, nombre, etc, ya
                    // que se reordena la capa entera, no solo el dibujo).
                    // detectDragGesturesAfterLongPress espera el long-press
                    // antes de tomar el gesto, así que un toque normal y
                    // rápido sigue siendo tap (lo maneja el .clickable de
                    // arriba) sin pisarse con el arrastre.
                    //
                    // A diferencia de antes, ACÁ NO se llama a
                    // onMoveUp/onMoveDown mientras el dedo se mueve — eso
                    // era justo lo que causaba el parpadeo (mutaba la
                    // lista real en cada frame). Ahora cada movimiento solo
                    // avisa hacia arriba (onReorderDrag) para que el padre
                    // acumule el desplazamiento visual; el reordenamiento
                    // real se dispara UNA vez, en onReorderDragEnd.
                    //
                    // BLOQUEO DE ORDEN (candado triángulo): SOLO
                    // [orderLocked] engancha o no este pointerInput.
                    // BUG REAL corregido acá — antes [locked] (el candado
                    // de canvas) TAMBIÉN bloqueaba el arrastre para
                    // reordenar, mezclando las dos funciones que el
                    // usuario pidió explícitamente MANTENER separadas: el
                    // candado de canvas bloquea el canvas, PUNTO — nada de
                    // reordenamiento, para eso está el otro candado. Cada
                    // uno hace UNA sola cosa, ninguno "hereda" la función
                    // del otro.
                    .then(
                        if (layer.orderLocked) {
                            Modifier
                        } else {
                            Modifier.pointerInput(layer.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { currentOnReorderDragStart() },
                                    onDragEnd = { currentOnReorderDragEnd() },
                                    onDragCancel = { currentOnReorderDragCancel() }
                                ) { change, dragAmount ->
                                    change.consume()
                                    currentOnReorderDrag(dragAmount.y)
                                }
                            }
                        }
                    )
            ) {
                // REVERTIDO: había una transformación acá
                // (TrimTransparentPaddingTransformation) que recortaba el
                // margen transparente sobrante de la imagen antes de
                // mostrarla — la idea era que el contenido real aprovechara
                // más el recuadro de 36dp. En la práctica, el bounding box
                // que calculaba (a partir de una grilla muestreada en baja
                // resolución) quedaba mal ajustado en varias imágenes y
                // terminaba cortando parte del logo real, no solo el aire
                // transparente — un defecto mucho peor que el problema
                // original que intentaba resolver. Se saca por completo:
                // mejor una miniatura con algo de margen de sobra que una
                // con el contenido mutilado. Vuelve a leer layer.sourceUri
                // tal cual, sin ninguna transformación de por medio.
                AsyncImage(
                    model = layer.sourceUri,
                    contentDescription = layer.name,
                    // Crop -> Fit: antes llenaba el recuadro de punta a
                    // punta recortando lo que no entraba (mal para

                    // imágenes verticales/horizontales no cuadradas, y
                    // engañoso para imágenes con fondo transparente, que
                    // se veían "cortadas" en vez de completas). Con Fit se
                    // ve la imagen ENTERA, tal cual se ve en el canvas
                    // principal — proporción real, sin recortar — y el
                    // fondo de color de la capa (labelColumnBrush, ya
                    // pintado en el Box de este Row) actúa de marco visible
                    // en las franjas que sobran o donde la imagen es
                    // transparente.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        // Antes tenía su .background(colorScheme.surface)
                        // propio acá — con Crop nunca se veía (la foto
                        // llenaba el recuadro entero). Con Fit sí se vería,
                        // como un gris genérico tapando el color de
                        // identidad de la capa en las franjas que sobran.
                        // Se saca para que el marco/letterbox use el color
                        // de la capa (labelColumnBrush), que el Row padre
                        // ya pinta detrás de este Box — un solo color de
                        // marco coherente, no dos superpuestos.
                        //
                        // 0.35f (medio transparente) -> 0f (invisible del
                        // todo): pedido puntual del usuario — la miniatura
                        // tiene que OCULTARSE por completo cuando la capa
                        // está oculta, no solo atenuarse, para que el ojo
                        // tachado grande de más abajo se vea sobre el marco
                        // de color de la capa (labelColumnBrush) y no sobre
                        // la foto de fondo semi-visible.
                        .alpha(if (layer.visible) 1f else 0f),
                    onError = { state ->
                        AppLogger.w("TimelineView", "No se pudo cargar la miniatura de la capa '${layer.name}' en el timeline", state.result.throwable)
                    }
                )
                // --- BUG REAL (el de verdad, la corrección anterior no
                // alcanzaba): esta miniatura es una FOTO real de la capa
                // (AsyncImage con ContentScale.Crop, llena el recuadro de
                // punta a punta) — no un simple color. El .background(...)
                // del Box de afuera queda DETRÁS de esa foto, así que en
                // cualquier imagen sin zonas transparentes (el logo de
                // OnePlus, la tablet, etc.) el color de identidad quedaba
                // 100% tapado — de ahí que la corrección anterior (mover
                // el color a un tono sólido coherente por grupo) no se
                // notara para nada: el tono coherente SÍ se calculaba
                // bien, pero nunca llegaba a pintarse encima de la foto.
                //
                // BUG REAL corregido: acá había una veladura de color
                // ENCIMA de la foto (Box con .background(color, alpha=0.62))
                // para las capas agrupadas por Multicolor — con varias capas
                // agrupadas (o TODAS, como en el caso reportado), cada
                // miniatura quedaba con ese velo semitransparente tapando la
                // foto real, así que se leía como "la imagen quedó opaca,
                // detrás del color". Se reemplaza por un BORDE de color
                // alrededor de la miniatura: la foto queda 100% nítida y
                // visible, y el color de identidad del grupo se sigue
                // distinguiendo de un vistazo — igual que un editor
                // profesional marca el color de un canal/pista con un chip o
                // borde, nunca pintando encima del contenido real.
                if (labelColumnColorOverride != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(2.5.dp, labelColumnColorOverride, RoundedCornerShape(4.dp))
                    )
                }
                if (!layer.visible) {
                    // --- Reemplaza el intento anterior (imagen atenuada +
                    // raya diagonal encima): ahora la miniatura se oculta
                    // POR COMPLETO (ver el .alpha(0f) de arriba) y en su
                    // lugar aparece este ojo tachado grande, centrado,
                    // ocupando casi todo el recuadro — el MISMO ícono
                    // (ic_eye_off) que ya se usa en el panel flotante de
                    // opciones de esta fila, no uno nuevo ni parecido, para
                    // que sea inequívocamente "el mismo ojo, más grande".
                    // Al volver a tocar el ojo desde el panel de opciones
                    // (layer.visible pasa a true), este bloque entero deja
                    // de dibujarse y la miniatura real vuelve a verse.
                    Icon(
                        painter = painterResource(id = R.drawable.ic_eye_off),
                        contentDescription = "Capa oculta",
                        // Unspecified = respeta los colores propios del
                        // drawable (ic_eye_off ya trae la raya diagonal en
                        // dos tonos para que se note siempre) — mismo
                        // criterio que ya se usa para este ícono en el
                        // panel de opciones, ver RowActionIcon más abajo.
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(0.62f)
                    )
                }

                // --- Mini-candados dentro de la miniatura: para que se vea
                // DE UN VISTAZO, sin tener que abrir el panel de opciones,
                // si una capa está bloqueada — y en cuál de los dos
                // sentidos (canvas, orden, o ambos a la vez). Mismo diseño
                // exacto que sus íconos grandes correspondientes (uno para
                // cada candado, ver comentario de ic_order_lock_closed más
                // abajo sobre por qué el diseño es distinto a propósito),
                // solo que en miniatura — así el usuario asocia el
                // candadito chico con el ícono grande que lo prendió, sin
                // aprender un símbolo nuevo. Esquina inferior derecha, con
                // un fondo oscuro semitransparente atrás para que se lea
                // encima de cualquier imagen (clara, oscura, lo que sea).
                if (layer.locked || layer.orderLocked) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(1.5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 1.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (layer.locked) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lock_closed),
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(9.dp)
                            )
                        }
                        if (layer.orderLocked) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_order_lock_closed),
                                contentDescription = null,
                                tint = Color(0xFF4FC3F7),
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }
            }

            // --- El panel no se monta/desmonta de golpe: se mantiene la
            // Popup viva mientras dure la animación de salida también (si
            // no, al cerrar desaparecía en seco, sin transición — se sentía
            // "tosco"). visibleState.targetState sigue a isExpanded, pero el
            // Popup en sí sigue existiendo hasta que currentState también
            // llegue a false (animación de cierre terminada). ---
            val visibleState = remember { MutableTransitionState(false) }
            LaunchedEffect(isExpanded) { visibleState.targetState = isExpanded }

            if (visibleState.currentState || visibleState.targetState) {
                // --- Se dispara AL LADO DERECHO de la miniatura, nunca
                // debajo ni encima: BesideAnchorPopupPositionProvider calcula
                // la posición real (borde derecho del anchor + gap chico),
                // centrado verticalmente con la miniatura — misma barra/fila,
                // se extiende hacia el costado. ---
                val gapPx = 0
                Popup(
                    popupPositionProvider = BesideAnchorPopupPositionProvider(gapPx),
                    onDismissRequest = onToggleExpand,
                    // dismissOnClickOutside = false: por defecto, tocar
                    // "afuera" (la miniatura incluida, ya que técnicamente
                    // queda fuera del contenido del Popup) dispara el
                    // cierre automático Y DEJA PASAR el mismo toque hacia
                    // abajo — así el clickable de la miniatura lo recibía
                    // también y volvía a abrirlo en el mismo instante (se
                    // cerraba y reabría tan rápido que parecía "no hacer
                    // nada" al tocar para cerrar). Ahora solo el toggle de
                    // la miniatura decide abrir/cerrar, sin ese cierre
                    // paralelo compitiendo por el mismo toque.
                    properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
                ) {
                    AnimatedVisibility(
                        visibleState = visibleState,
                        // Nace/muere desde el borde izquierdo (pegado a la
                        // miniatura), no desde el centro — así se lee como
                        // que "sale" de la miniatura en vez de aparecer de
                        // la nada flotando en el aire.
                        enter = fadeIn(tween(140)) +
                            scaleIn(tween(140), initialScale = 0.85f, transformOrigin = TransformOrigin(0f, 0.5f)),
                        exit = fadeOut(tween(110)) +
                            scaleOut(tween(110), targetScale = 0.85f, transformOrigin = TransformOrigin(0f, 0.5f))
                    ) {
                        // Antes esto era un Surface(color = trackColorStrong,
                        // ...) — un Material3 Surface solo acepta un Color
                        // plano, no un Brush, así que un degradado activo se
                        // guardaba bien pero JAMÁS se veía acá (quedaba
                        // siempre aplanado a un solo tono). Se reemplaza por
                        // un Box con Modifier.background(brush = trackBrush),
                        // que si soporta Brush — así el panel de opciones
                        // ahora sí refleja el degradado real de la capa,
                        // igual que ya pasa en su miniatura y su barra.
                        Box(
                            modifier = Modifier
                                .shadow(elevation = 12.dp, shape = androidx.compose.ui.graphics.RectangleShape, clip = false)
                                .background(brush = labelColumnBrush, shape = androidx.compose.ui.graphics.RectangleShape)
                                // Oscurece el panel cuando la capa está oculta
                                // (ojo cerrado) — mismo criterio visual que
                                // antes (compositeOver Black), pero como una
                                // capa translúcida encima en vez de mezclar el
                                // Brush a mano color por color.
                                .then(
                                    if (!layer.visible) {
                                        Modifier.background(Color.Black.copy(alpha = 0.4f), androidx.compose.ui.graphics.RectangleShape)
                                    } else Modifier
                                )
                                .border(
                                    width = 1.dp,
                                    color = effectiveTrackColorStrong.copy(alpha = 0.5f),
                                    shape = androidx.compose.ui.graphics.RectangleShape
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                RowActionIcon(
                                    R.drawable.ic_layer_rename,
                                    "Renombrar capa",
                                    { showRenameDialog = true }
                                )
                                RowActionIcon(
                                    R.drawable.ic_layer_color,
                                    "Cambiar color de la capa",
                                    { showColorPickerDialog = true },
                                    // Unspecified = respeta los colores propios del
                                    // drawable (la paleta ya trae sus manchas de
                                    // color fijas, igual criterio que ic_eye_off).
                                    tint = Color.Unspecified
                                )
                                RowActionIcon(
                                    if (layer.visible) R.drawable.ic_eye else R.drawable.ic_eye_off,
                                    if (layer.visible) "Ocultar capa" else "Mostrar capa",
                                    onToggleVisibility,
                                    // Unspecified = respeta los colores propios del
                                    // drawable (ic_eye_off ya trae la raya diagonal
                                    // en dos tonos para que se note siempre); un
                                    // tint plano la tapaba/lavaba.
                                    tint = Color.Unspecified
                                )
                                // --- Candado de ORDEN (nuevo, independiente
                                // del de canvas): a propósito a la IZQUIERDA
                                // del candado de siempre, como pidió el
                                // usuario — y con un diseño distinto (flechas
                                // arriba/abajo en vez del ojo de cerradura)
                                // para que de un vistazo se note que hace
                                // otra cosa. Bloquea SOLO el arrastre para
                                // reordenar esta fila en la columna de capas
                                // (ver el .pointerInput con
                                // detectDragGesturesAfterLongPress más abajo);
                                // no toca el canvas para nada.
                                RowActionIcon(
                                    if (layer.orderLocked) R.drawable.ic_order_lock_closed else R.drawable.ic_order_lock_open,
                                    if (layer.orderLocked) "Desbloquear orden de la capa" else "Bloquear orden de la capa (no reordenar)",
                                    onToggleOrderLock,
                                    tint = if (layer.orderLocked) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.85f),
                                    // 15dp -> 20dp: ver comentario en RowActionIcon
                                    // sobre por qué la flecha quedaba ilegible.
                                    iconSize = 20.dp
                                )
                                RowActionIcon(
                                    if (layer.locked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open,
                                    if (layer.locked) "Desbloquear capa" else "Bloquear capa",
                                    onToggleLock,
                                    tint = if (layer.locked) Color(0xFFFFC107) else Color.White.copy(alpha = 0.85f),
                                    iconSize = 20.dp
                                )
                                RowActionIcon(R.drawable.ic_delete, "Eliminar capa", onDeleteRequest, tint = Color(0xFFFF6B6B))
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (isSelected) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                .pointerInput(layer.id, trackWidthPx, projectDurationMs) {
                    detectTapGestures {
                        onSelect()
                        val localX = it.x.coerceIn(0f, trackWidthPx)
                        onSeek(((localX / trackWidthPx) * projectDurationMs).toLong())
                    }
                }
        ) {
            // --- Nombre de la capa (el original del archivo o el que el
            // usuario haya puesto con "Renombrar"), pegado abajo a la
            // izquierda del cuerpo de la barra — arranca justo donde
            // arranca el playhead en 0:00, mismo criterio que FL Studio
            // Mobile (ver referencia que mandó el usuario), solo que ahí
            // el nombre va ARRIBA de cada pista y acá va ABAJO, como pidió
            // expresamente. Es texto puro sobre el Box, sin su propio
            // pointerInput — no compite por el toque con el
            // detectTapGestures de arriba (seek/seleccionar) ni con los
            // diamantes de keyframe. Una sola línea con elipsis si el
            // nombre no entra (archivo con nombre largo).
            Text(
                text = layer.name,
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, bottom = 2.dp)
            )

            keyframes.forEach { kf ->
                key(kf.timeMs) {
                    KeyframeDiamond(
                        timeMs = kf.timeMs,
                        trackWidthPx = trackWidthPx,
                        projectDurationMs = projectDurationMs,
                        isSelected = isSelected,
                        onTap = {
                            onSelect()
                            onSeek(kf.timeMs)
                        },
                        onRetime = { newTimeMs -> onRetimeKeyframe(kf.timeMs, newTimeMs) }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameLayerDialog(
            initialName = layer.name,
            accentColor = effectiveTrackColorStrong,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onRenameRequest(newName)
            }
        )
    }

    if (showColorPickerDialog) {
        val snap = colorPickerResumeSnapshot
        LayerColorPickerDialog(
            // Si hay un snapshot pendiente (reabierto después del
            // cuentagotas), manda ESO por encima del valor persistido de
            // la capa — así se retoma exactamente donde se dejó, con el
            // color nuevo ya adentro, en vez de resetear todo al último
            // color guardado de verdad.
            initialColorArgb = snap?.solidArgb ?: layer.customColorArgb,
            initialGradientStartArgb = snap?.gradientAArgb ?: layer.customGradientStartArgb,
            initialGradientEndArgb = snap?.gradientBArgb ?: layer.customGradientEndArgb,
            initialUseGradient = snap?.gradientEnabled ?: layer.useGradientColor,
            initialGradientAngleDegrees = snap?.gradientAngleDegrees ?: layer.gradientAngleDegrees,
            initialGradientIsRadial = snap?.gradientIsRadial ?: layer.gradientIsRadial,
            initialBlackAndWhiteMode = snap?.blackAndWhiteMode ?: layer.useBlackAndWhiteMode,
            initialActiveSlot = snap?.activeSlot,
            fallbackColorArgb = layerTrackColor(layer.colorIndex).toArgb(),
            onDismiss = {
                showColorPickerDialog = false
                colorPickerResumeSnapshot = null
            },
            onSelectColor = { colorArgb, useBW ->
                showColorPickerDialog = false
                colorPickerResumeSnapshot = null
                onChangeColor(colorArgb, useBW)
            },
            onSelectGradient = { startArgb, endArgb, angleDegrees, isRadial, useBW ->
                showColorPickerDialog = false
                colorPickerResumeSnapshot = null
                onChangeGradient(startArgb, endArgb, angleDegrees, isRadial, useBW)
            },
            onReset = {
                colorPickerResumeSnapshot = null
                onResetColor()
            },
            onRequestEyedropper = { snapshot ->
                // Guarda todo lo armado hasta ahora, cierra ESTE diálogo
                // (tiene que desaparecer del todo para que el usuario
                // pueda tocar el preview, que vive detrás en otra
                // ventana) y avisa hacia arriba que ESTA capa quiere un
                // color del cuentagotas — EditorScreen se encarga de
                // mostrar el overlay sobre el preview y, cuando el
                // usuario toque, el resultado vuelve acá por
                // pickedEyedropperColor.
                colorPickerResumeSnapshot = snapshot
                showColorPickerDialog = false
                onRequestEyedropper()
            }
        )
    }
}

/**
 * Una fila del submenú "Seleccionar todo" / "Manual" (ver
 * colorModeSubmenuExpanded en TimelineView) — ícono en una placa
 * redondeada con tinte de color a la izquierda, título + descripción
 * corta a la derecha, todo dentro de un toque completo (fillMaxWidth). El
 * mismo patrón visual (placa de ícono + texto de dos líneas) que usan los
 * menús de apps premium tipo Notion/Linear, en vez de una lista de texto
 * plano — para que se note que es una elección real entre dos modos, no
 * solo un botón más.
 */
@Composable
private fun ColorModeSubmenuItem(
    iconRes: Int,
    iconTint: Color,
    label: String,
    description: String,
    onClick: () -> Unit,
    // --- BUG REAL corregido (reportado): "Seleccionar todo" usaba el
    // MISMO color (iconTint = BrandPurpleLight) para el fondo del
    // recuadrito Y para el glifo de adentro — un morado sobre un morado
    // más tenue, casi sin contraste, así que las tres tildes no se
    // notaban. Estos dos parámetros nuevos separan una cosa de la otra:
    // [iconTint] sigue siendo el morado del recuadro (el "acento" de que
    // esta es la opción principal/destacada), e [iconGlyphTint] es el
    // color del glifo en sí, que ahora puede ser blanco — mismo criterio
    // de contraste que ya usa "Manual" (blanco sobre semitransparente).
    // Por default es igual a [iconTint], así que "Manual" y cualquier otro
    // uso futuro que no pase este parámetro se comporta EXACTAMENTE igual
    // que antes.
    iconGlyphTint: Color = iconTint,
    iconSize: Dp = 16.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconGlyphTint,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Posicionador manual del panel de acciones de capa: lo pega justo al lado
 * derecho de la miniatura (borde derecho del anchor + gapPx), centrado
 * verticalmente con esa misma miniatura — o sea, misma "barra"/altura de la
 * fila, extendiéndose hacia el costado, nunca hacia abajo ni tapándola.
 *
 * OJO: usar Popup(alignment = Alignment.CenterEnd, ...) NO logra esto — esa
 * alineación hace coincidir la MISMA esquina del anchor y del panel, así que
 * el panel termina dibujado hacia atrás, montado encima de la miniatura, en
 * vez de a su derecha. Por eso hace falta este PopupPositionProvider a mano:
 * acá se calcula la posición real, borde derecho del anchor + gap, y se
 * ignora esa lógica de "esquina compartida".
 */
private class BesideAnchorPopupPositionProvider(
    private val gapPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.right + gapPx
        val y = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
        return IntOffset(x, y)
    }
}

/**
 * Posiciona un Popup pegado al borde IZQUIERDO del anchor, arrancando justo
 * debajo de su borde inferior (+ un gap chico) — el menú "Multicolor" del
 * ícono capas+flecha nace así, como una ventana angosta que baja desde el
 * ícono, del mismo ancho que la columna de miniaturas (LABEL_COLUMN_WIDTH),
 * en vez de flotar centrado o desalineado respecto a esa columna.
 */
private class BelowAnchorPopupPositionProvider(
    private val gapPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left
        val y = anchorBounds.bottom + gapPx
        return IntOffset(x, y)
    }
}

/**
 * Botón de ícono chiquito para los controles desplegados de una capa (subir,
 * bajar, ojo, candado, eliminar). Un Box + clickable a mano en vez de
 * IconButton — IconButton de Material3 impone un mínimo de toque de 48dp
 * que no entra en los 72dp de ancho de la columna repartidos en 2-3
 * íconos por fila (fue justo el bug que recortaba el "+" de agregar pista).
 */
@Composable
private fun RowActionIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White.copy(alpha = 0.85f),
    // --- BUG REAL encontrado en revisión: los candados (ic_lock_* /
    // ic_order_lock_*) ahora traen una flecha chica además del cuerpo del
    // candado, dentro del MISMO viewport de 24 unidades que antes. El
    // ícono siempre se dibuja a 15dp acá — con la flecha ocupando ~3.6 de
    // esas 24 unidades, el alto real en pantalla queda en ~2.25dp (~7px
    // en una pantalla de 3x), invisible en la práctica aunque en el editor
    // se vea bien a tamaño completo. Los otros íconos (lápiz, paleta, ojo,
    // basura) son un solo glifo simple y sí se leen bien a 15dp; estos dos
    // candados cargan más detalle y necesitan más espacio real. En vez de
    // agrandar TODOS los íconos de la fila (que los separaría de más y
    // rompería el balance visual con lápiz/paleta/ojo/basura), se deja un
    // override puntual solo para quien lo necesite.
    iconSize: Dp = 15.dp
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Un keyframe individual en la pista: diamante que se puede tocar (selecciona
 * la capa y salta a ese instante) o arrastrar horizontalmente (retoca el
 * timing). Se distingue tap de arrastre por distancia recorrida, no por dos
 * detectores de gestos separados — evitar que compitan por los mismos
 * eventos de puntero es justamente lo que se evita haciéndolo así.
 */
@Composable
private fun KeyframeDiamond(
    timeMs: Long,
    trackWidthPx: Float,
    projectDurationMs: Long,
    isSelected: Boolean,
    onTap: () -> Unit,
    onRetime: (newTimeMs: Long) -> Unit
) {
    var dragOffsetPx by remember(timeMs) { mutableStateOf(0f) }
    val baseX = (timeMs.toFloat() / projectDurationMs) * trackWidthPx
    val sizePx = with(LocalDensity.current) { DIAMOND_SIZE.toPx() }
    val markerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White

    Box(
        modifier = Modifier
            .offset { IntOffset((baseX + dragOffsetPx - sizePx / 2f).roundToInt(), 0) }
            .size(DIAMOND_SIZE)
            .pointerInput(timeMs, trackWidthPx, projectDurationMs) {
                var totalMovement = 0f
                detectDragGestures(
                    onDragStart = { totalMovement = 0f },
                    onDragEnd = {
                        if (totalMovement < TAP_SLOP_PX) {
                            onTap()
                        } else {
                            val newTimeMs = (((baseX + dragOffsetPx) / trackWidthPx) * projectDurationMs)
                                .toLong()
                                .coerceIn(0L, projectDurationMs)
                            if (newTimeMs != timeMs) onRetime(newTimeMs)
                        }
                        dragOffsetPx = 0f
                    },
                    onDragCancel = { dragOffsetPx = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    totalMovement += abs(dragAmount.x)
                    dragOffsetPx = (dragOffsetPx + dragAmount.x).coerceIn(-baseX, trackWidthPx - baseX)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val color = markerColor
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width / 2f, size.height)
                lineTo(0f, size.height / 2f)
                close()
            }
            drawPath(path, color = color)
            drawPath(path, color = Color.Black.copy(alpha = 0.35f), style = Stroke(width = 1.dp.toPx()))
        }
    }
}


/** Formato mm:ss local, sin depender de EditorScreen (esa función es privada de ese archivo). */
private fun formatTimelineTimecode(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Dibuja la regla graduada: una marca corta por segundo y una marca alta +
 * timecode cada [labelIntervalSec] segundos. El intervalo entre labels se
 * elige dinámicamente según cuánto dura el proyecto, para que nunca queden
 * los números encimados ni demasiado separados (como una regla real de
 * editor profesional, no una franja vacía con dos números en las puntas).
 */
private fun DrawScope.drawRulerTicks(
    labelWidthPx: Float,
    trackWidthPx: Float,
    durationMs: Long
) {
    // Antes "pxPerSecond" se calculaba con totalSeconds truncado a Int
    // (durationMs / 1000L), mientras que la posición del playhead se
    // calcula con la duración real en ms sin truncar — esa diferencia
    // de precisión es justo lo que hacía que el playhead no cayera
    // exacto sobre la marca de cada segundo. Ahora ambos usan la MISMA
    // fórmula en float, así coinciden siempre, segundo a segundo.
    val totalSecondsFloat = (durationMs / 1000f).coerceAtLeast(1f)
    val pxPerSecond = trackWidthPx / totalSecondsFloat
    val totalSecondsForTicks = kotlin.math.ceil(totalSecondsFloat).toInt()

    // Candidatos de intervalo "redondos" para las marcas más altas.
    val niceIntervals = intArrayOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600)
    val minPxBetweenTallTicks = 56f
    val tallIntervalSec = niceIntervals.firstOrNull { interval ->
        pxPerSecond * interval >= minPxBetweenTallTicks
    } ?: niceIntervals.last()

    val tickColor = Color.White.copy(alpha = 0.25f)
    val tallTickColor = Color.White.copy(alpha = 0.5f)
    val shortTickTopPx = size.height * 0.62f
    val tallTickTopPx = size.height * 0.35f
    val baselineY = size.height

    // Solo líneas — el número exacto ya lo muestra el timecode flotante
    // que sigue al playhead en vivo, así que repetirlo fijo acá abajo de
    // cada segundo era redundante (dos "0:05" al mismo tiempo).
    var second = 0
    while (second <= totalSecondsForTicks) {
        val x = labelWidthPx + second * pxPerSecond
        val isTallTick = second % tallIntervalSec == 0
        drawLine(
            color = if (isTallTick) tallTickColor else tickColor,
            start = Offset(x, if (isTallTick) tallTickTopPx else shortTickTopPx),
            end = Offset(x, baselineY),
            strokeWidth = 1f
        )
        second += 1
    }
}
