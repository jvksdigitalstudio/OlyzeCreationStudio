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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.StrokeCap
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
import java.io.File
import java.util.Calendar
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

// --- Barra inferior de secciones (Keyframes / Control / Rack) ---
// Altura chica a propósito ("que no sea tan gruesa"): es una barra de
// navegación entre paneles, no un panel en sí, así que no debe competir
// en peso visual con el timeline de arriba.
private val BOTTOM_BAR_HEIGHT = 40.dp
private val BOTTOM_BAR_ICON_SIZE = 16.dp

/**
 * Cabecera premium con tres secciones — Keyframes / Control / Rack — que vive
 * pegada debajo del timeline. Arranca justo donde termina la columna de
 * miniaturas de las capas (mismo [labelColumnWidth] que usa [TimelineView]
 * para su propia columna), NUNCA desde el borde izquierdo de la pantalla:
 * ese primer tramo queda transparente, dejando ver el relleno morado de
 * fondo, para que la barra se lea alineada con las pistas de la derecha y no
 * con la columna de capas.
 *
 * Por ahora es puramente visual — cada sección no dispara nada todavía,
 * eso llega después con [onKeyframesClick] / [onControlClick] / [onRackClick].
 */
@Composable
fun EditorBottomBar(
    modifier: Modifier = Modifier,
    labelColumnWidth: Dp = LABEL_COLUMN_WIDTH,
    selectedSection: BottomBarSection? = null,
    onKeyframesClick: () -> Unit = {},
    onControlClick: () -> Unit = {},
    onRackClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BOTTOM_BAR_HEIGHT)
    ) {
        // Hueco alineado con la columna de capas de arriba — a propósito
        // SIN fondo propio, así se sigue viendo el relleno morado que ya
        // pinta EditorScreen detrás, y la barra "empieza" visualmente
        // recién donde arrancan las pistas.
        Spacer(modifier = Modifier.width(labelColumnWidth))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(elevation = 6.dp, clip = false)
                .background(
                    Brush.verticalGradient(
                        listOf(SurfaceTintedElevated, SurfaceTintedDark)
                    )
                )
                // Filo superior sutil en el morado vivo de marca — el toque
                // "premium" que separa esta barra del timeline sin un borde
                // duro y plano.
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                BrandPurpleLight.copy(alpha = 0f),
                                BrandPurpleLight.copy(alpha = 0.55f),
                                BrandPurpleLight.copy(alpha = 0f)
                            )
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarTab(
                label = "Keyframes",
                isSelected = selectedSection == BottomBarSection.KEYFRAMES,
                onClick = onKeyframesClick,
                icon = { tint -> KeyframesTabIcon(tint) },
                modifier = Modifier.weight(1f)
            )
            BottomBarDivider()
            BottomBarTab(
                label = "Control",
                isSelected = selectedSection == BottomBarSection.CONTROL,
                onClick = onControlClick,
                icon = { tint -> ControlTabIcon(tint) },
                modifier = Modifier.weight(1f)
            )
            BottomBarDivider()
            BottomBarTab(
                label = "Rack",
                isSelected = selectedSection == BottomBarSection.RACK,
                onClick = onRackClick,
                icon = { tint -> RackTabIcon(tint) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Qué sección de [EditorBottomBar] está activa. Solo visual por ahora. */
enum class BottomBarSection { KEYFRAMES, CONTROL, RACK }

/** Degradado vertical fino entre secciones — mismo lenguaje visual que el
 * separador de columna de capas del timeline (se desvanece arriba/abajo,
 * más marcado al centro) en vez de una línea sólida dura. */
@Composable
private fun BottomBarDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight(0.62f)
            .width(1.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.02f),
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
    )
}

/** Una de las tres pestañas de la barra: ícono chico arriba, nombre abajo
 * en versalitas — mismo patrón que las barras de herramientas de editores
 * de video/audio profesionales (Premiere, FL Studio Mobile). El estado
 * seleccionado se marca con un realce sutil de fondo + texto en el morado
 * vivo de marca, nunca con un color ajeno a la paleta de la app. */
@Composable
private fun BottomBarTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (isSelected) BrandPurpleLight else Color.White.copy(alpha = 0.72f)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) BrandPurpleLight.copy(alpha = 0.10f) else Color.Transparent
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon(tint)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Ícono de "Keyframes": el mismo rombo que marca cada keyframe en las
 * pistas de arriba — a propósito el mismo símbolo, para que se lea de
 * un vistazo que esta pestaña lleva a esos mismos keyframes. */
@Composable
private fun KeyframesTabIcon(tint: Color, iconSize: Dp = BOTTOM_BAR_ICON_SIZE) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        drawPath(path, color = tint)
    }
}

/** Ícono de "Control": tres deslizadores horizontales con su perilla, al
 * estilo de un panel de parámetros/mezclador — nada de un ícono genérico
 * de engranaje que no diga nada sobre lo que hay adentro. */
@Composable
private fun ControlTabIcon(tint: Color, iconSize: Dp = BOTTOM_BAR_ICON_SIZE) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val knobXs = floatArrayOf(0.65f, 0.35f, 0.55f)
        val rowYs = floatArrayOf(0.18f, 0.5f, 0.82f)
        val strokeW = size.width * 0.09f
        rowYs.forEachIndexed { index, yFrac ->
            val y = size.height * yFrac
            drawLine(
                color = tint.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeW
            )
            drawCircle(
                color = tint,
                radius = size.width * 0.11f,
                center = Offset(size.width * knobXs[index], y)
            )
        }
    }
}

/** Ícono de "Rack": tres módulos apilados, como las unidades de un rack de
 * efectos/hardware de audio — coherente con lo que va a vivir en ese panel
 * (los módulos/efectos cargados). */
@Composable
private fun RackTabIcon(tint: Color, iconSize: Dp = BOTTOM_BAR_ICON_SIZE) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val unitHeight = size.height * 0.22f
        val gap = size.height * 0.17f
        val cornerRadius = unitHeight * 0.3f
        var y = 0f
        repeat(3) {
            drawRoundRect(
                color = tint.copy(alpha = if (it == 1) 1f else 0.6f),
                topLeft = Offset(0f, y),
                size = androidx.compose.ui.geometry.Size(size.width, unitHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )
            y += unitHeight + gap
        }
    }
}

/**
 * BUG REAL corregido (reportado por el usuario): los paneles de abajo
 * (este [SectionPlaceholderPanel] y [ProjectInfoPanel]) se DIBUJAN encima
 * del timeline/canvas, pero un `Box` con solo `.background(...)` no es
 * "opaco" para los toques en Compose — si no tiene ningún modifier de
 * puntero propio, el sistema de hit-testing lo salta por completo y el
 * toque le llega directo a lo que esté DETRÁS (capas, canvas), aunque
 * visualmente el panel tape todo. Por eso al tocar el espacio vacío de
 * estos paneles se seguían seleccionando capas de atrás.
 *
 * SEGUNDO BUG REAL corregido (el de la ronda anterior): la primera
 * versión de este modifier consumía el toque a mano con
 * `awaitPointerEvent(PointerEventPass.Initial)` — el pase Initial viaja
 * de afuera hacia adentro (padre antes que hijo), así que el panel
 * (padre) le "robaba" el toque al botón ✕ de cerrar (hijo) ANTES de que
 * el propio `clickable()` del botón —que escucha en el pase Main,
 * adentro hacia afuera— llegara siquiera a enterarse. Resultado: dejó de
 * poder cerrarse tocando la ✕.
 *
 * La solución real es no reinventar la detección de gestos a mano: usar
 * el mismo `clickable()` que ya usa el botón ✕. Compose ya resuelve
 * clickables anidados correctamente (como una Card clickable con un
 * Button clickable adentro) — el más interno/de adelante (la ✕) gana
 * SIEMPRE para los toques que caen sobre él, y este clickable de acá
 * (más externo, cubre todo el panel) solo actúa como red para cualquier
 * otro toque en el resto del panel — sin action visible (`indication =
 * null`) porque no navega a ningún lado, solo evita que el toque
 * atraviese hacia atrás.
 */
@Composable
private fun Modifier.blockTouchesFromPassingThrough(): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = {}
)

/**
 * Panel vacío (placeholder) que EditorScreen muestra al tocar una de las
 * tres pestañas de [EditorBottomBar] — Keyframes / Control / Rack. Por
 * ahora solo confirma visualmente cuál pestaña quedó activa (ícono + nombre
 * + "Próximamente"); el contenido real de cada sección llega después.
 *
 * A propósito NO define su propio tamaño ni posición acá — EditorScreen lo
 * recorta exactamente entre el pie de la regla de tiempo y el borde de la
 * columna de capas (mismos [RULER_HEIGHT] / [LABEL_COLUMN_WIDTH] que ya usa
 * el resto del timeline), así siempre queda alineado sin duplicar números.
 */
@Composable
fun SectionPlaceholderPanel(
    section: BottomBarSection,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // --- Solo relevante para BottomBarSection.CONTROL: si la capa
    // actualmente seleccionada es una capa de imagen (por ahora, TODAS
    // las capas de este proyecto lo son — ver Layer.kt, "una imagen PNG
    // ... más su propia pista de cámara" — pero se deja como bandera
    // explícita, no un `true` fijo, para que el día que existan capas de
    // otro tipo (video, texto, forma) esto siga siendo correcto sin
    // tocar este archivo). Controla si la opción "Imagen" del nuevo menú
    // (ver [ControlImageOptionsPanel] más abajo) aparece marcada como
    // sincronizada con la capa activa.
    hasImageLayerSelected: Boolean = false
) {
    val label = when (section) {
        BottomBarSection.KEYFRAMES -> "Keyframes"
        BottomBarSection.CONTROL -> "Control"
        BottomBarSection.RACK -> "Rack"
    }
    Box(
        modifier = modifier
            .blockTouchesFromPassingThrough()
            .background(
                Brush.verticalGradient(listOf(SurfaceTintedDark, SurfaceTintedElevated))
            )
            // Mismo filo superior sutil en morado vivo que EditorBottomBar —
            // así el panel se lee como una extensión de la misma pestaña que
            // lo abrió, no como un elemento suelto y ajeno.
            .drawBehind {
                drawLine(
                    color = BrandPurpleLight.copy(alpha = 0.35f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Cerrar panel de $label",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp)
            )
        }

        if (section == BottomBarSection.CONTROL) {
            // --- Control YA tiene contenido real (el joystick) — nada de
            // tapar media pantalla con el cartel genérico de "Próximamente"
            // acá; en cambio, solo un rótulo chico arriba a la izquierda
            // para que se siga leyendo en qué sección está parado. ---
            //
            // ARREGLADO/AMPLIADO: ese rótulo chico ahora es un menú real
            // (ver [ControlMenuBadge] + [ControlImageOptionsPanel] más
            // abajo) — pedido explícito: un ícono de menú premium en la
            // esquina sup. izquierda de ESTE panel (mismo lugar donde
            // antes solo había el texto "Control"), que al tocarlo
            // despliega una ventana estilo "barra gruesa" (mismo lenguaje
            // visual — Surface elevada, borde sutil, esquinas
            // redondeadas — que el mini-menú de la esquina de la capa en
            // el canvas, ver EditorScreen.kt). Por ahora esa ventana
            // desplegable tiene una sola fila, "Imagen", que se
            // sincroniza con la capa activa: como TODAS las capas de
            // este proyecto son de imagen (ver comentario en
            // [hasImageLayerSelected]), en la práctica queda marcada
            // como sincronizada apenas hay una capa seleccionada — y se
            // desmarca sola si no hay ninguna.
            var showImageOptionsMenu by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 10.dp)
            ) {
                ControlMenuBadge(
                    onClick = { showImageOptionsMenu = !showImageOptionsMenu }
                )
                if (showImageOptionsMenu) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ControlImageOptionsPanel(imageSynced = hasImageLayerSelected)
                }
            }

            // --- Joystick estilo GTA San Andreas (versión Android): mismo
            // rincón inferior izquierdo donde vive el de movimiento en ese
            // juego — para controlar personajes/modelos más adelante. Por
            // ahora es puramente el control en sí (arrastrable, con su
            // resorte al soltar), todavía sin conectar a ningún personaje. ---
            GtaStyleJoystick(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = JOYSTICK_MARGIN, bottom = JOYSTICK_MARGIN)
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (section) {
                    BottomBarSection.KEYFRAMES -> KeyframesTabIcon(BrandPurpleLight, iconSize = 36.dp)
                    BottomBarSection.RACK -> RackTabIcon(BrandPurpleLight, iconSize = 36.dp)
                    BottomBarSection.CONTROL -> Unit // no llega acá, ver rama de arriba
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Próximamente",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private val JOYSTICK_OUTER_SIZE = 108.dp
private val JOYSTICK_INNER_SIZE = 64.dp
private val JOYSTICK_MARGIN = 20.dp

/**
 * Ícono de menú premium (esquina sup. izquierda del panel "Control"):
 * mismo lenguaje visual que las manijas del marco de selección en el
 * canvas (círculo blanco, anillo en el morado de marca, glyph de
 * "hamburguesa" de tres líneas) — un SVG vectorial dibujado a mano con
 * [Canvas]/[DrawScope] en vez de un recurso .xml, para que escale nítido
 * a cualquier densidad sin depender de un drawable extra.
 */
@Composable
private fun ControlMenuBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .shadow(elevation = 4.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.4.dp, BrandPurpleLight, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val halfW = size.width * 0.46f
            val gap = size.height * 0.32f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val strokeW = 1.6.dp.toPx()
            for (i in -1..1) {
                val y = cy + i * gap
                drawLine(
                    color = BrandPurpleDeep,
                    start = Offset(cx - halfW, y),
                    end = Offset(cx + halfW, y),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Ventana desplegable ("barra gruesa") del menú de [ControlMenuBadge] —
 * mismo look premium (Surface elevada + borde sutil + sombra) que el
 * mini-menú de la esquina de la capa en el canvas (ver EditorScreen.kt,
 * el panel que se abre desde la manija "≡" del marco de selección), para
 * que se sienta de la misma familia visual.
 *
 * Por ahora tiene una sola fila, "Imagen" — primera y única opción a
 * pedido explícito. Cada capa del proyecto queda vinculada/sincronizada
 * a esta opción (ver [hasImageLayerSelected] en [SectionPlaceholderPanel]):
 * el punto de estado a la derecha se pinta en el morado de marca (activo)
 * apenas hay una capa seleccionada, y vuelve a gris (inactivo) si no la
 * hay — sin que el usuario tenga que tocar nada más.
 */
@Composable
private fun ControlImageOptionsPanel(
    imageSynced: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(168.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(14.dp)),
        color = SurfaceTintedElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Imagen",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (imageSynced) BrandPurpleLight else Color.White.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/**
 * Panel "Información del proyecto": título, sinopsis/resumen, créditos,
 * etc. — a pedido del usuario, algo como la descripción de un video de
 * YouTube pero del proyecto/película entero.
 *
 * A diferencia de [SectionPlaceholderPanel] (que se recorta DENTRO del
 * hueco del timeline, respetando la regla de tiempo y la barra
 * Keyframes/Control/Rack, que siguen visibles), este panel reemplaza esa
 * zona ENTERA de punta a punta — EditorScreen lo superpone con
 * `fillMaxSize()` sobre un envoltorio que abarca timeline + EditorBottomBar
 * juntos, así que no queda ni un pedazo de la barra de pestañas ni de la
 * regla asomando. Fondo sólido (no degradado) a pedido puntual — "morado
 * oscuro puro".
 *
 * El cuerpo se divide en dos mitades lado a lado ("1" izquierda / "2"
 * derecha, separadas por una línea vertical). Por ahora SOLO el lado
 * izquierdo tiene contenido real (ficha del proyecto: título, año /
 * categoría / duración, y hasta 4 fotos de elenco) — el lado derecho queda
 * vacío a propósito, para una próxima actualización.
 */
@Composable
fun ProjectInfoPanel(
    onClose: () -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    releaseYear: Int?,
    onReleaseYearChange: (Int) -> Unit,
    genre: String?,
    onGenreChange: (String) -> Unit,
    durationMinutes: Int?,
    onDurationMinutesChange: (Int) -> Unit,
    // Siempre 4 elementos (uno por casilla); null = casilla vacía.
    castPhotoFiles: List<File?>,
    onPickCastPhoto: (Int) -> Unit,
    onRemoveCastPhoto: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showYearPicker by remember { mutableStateOf(false) }
    var showGenrePicker by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .blockTouchesFromPassingThrough()
            .background(BrandPurpleDeep)
            // Mismo filo superior sutil que SectionPlaceholderPanel, para
            // que se lea como parte de la misma familia de paneles pese a
            // ocupar mucho más espacio.
            .drawBehind {
                drawLine(
                    color = BrandPurpleLight.copy(alpha = 0.35f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Text(
            text = "Información del proyecto",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 14.dp)
        )

        // --- Cerrar: dos caminos, como pediste. Este botón, O tocar de
        // nuevo el mismo ícono de la libreta en la barra de arriba (ver
        // EditorScreen.kt) — ambos terminan bajando el mismo booleano,
        // ninguno sabe del otro y no hace falta que se enteren. ---
        //
        // Sin .background() a propósito — antes tenía un chip circular de
        // fondo (Color.White alpha 0.08) que la hacía parecer un botón; la
        // ✕ del panel de Keyframes/Control/Rack (SectionPlaceholderPanel,
        // más arriba en este mismo archivo) nunca tuvo ese fondo, así que
        // se ve como una ✕ suelta, no como un botón — mismo criterio acá
        // ahora, para que las dos ✕ se vean y se sientan igual.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Cerrar información del proyecto",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }

        // --- Cuerpo: dos mitades lado a lado, separadas por una línea
        // vertical fina — igual que la marca que pediste ("1" / "2") sobre
        // la captura del panel vacío. Debajo del título "Información del
        // proyecto" y de la ✕, con padding propio.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 46.dp)
        ) {
            // --- Lado 1 (izquierda): ficha del proyecto ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Título — grande y destacado, con placeholder semi-
                // transparente "Título" que el usuario reemplaza al
                // escribir (ver referencia: "Los Minions" en Google).
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    placeholder = {
                        Text(
                            "Título",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurpleLight.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        cursorColor = BrandPurpleLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tres opciones al pie del título: año / categoría / duración
                // — más chicas que el título, cada una abre su propia rueda
                // de selección (ver diálogos más abajo).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ProjectInfoOptionChip(
                        label = releaseYear?.toString() ?: "Año",
                        modifier = Modifier.weight(1f),
                        onClick = { showYearPicker = true }
                    )
                    ProjectInfoOptionChip(
                        label = genre ?: "Categoría",
                        modifier = Modifier.weight(1f),
                        onClick = { showGenrePicker = true }
                    )
                    ProjectInfoOptionChip(
                        label = formatInfoDuration(durationMinutes) ?: "Duración",
                        modifier = Modifier.weight(1f),
                        onClick = { showDurationPicker = true }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    "Elenco / personajes",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 4 casillas de imagen (2x2) con ícono "+" para cargar,
                // mismo estilo redondeado que la portada del proyecto en
                // "Mis proyectos".
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ProjectInfoPhotoSlot(
                            file = castPhotoFiles.getOrNull(0),
                            onPick = { onPickCastPhoto(0) },
                            onRemove = { onRemoveCastPhoto(0) },
                            modifier = Modifier.weight(1f)
                        )
                        ProjectInfoPhotoSlot(
                            file = castPhotoFiles.getOrNull(1),
                            onPick = { onPickCastPhoto(1) },
                            onRemove = { onRemoveCastPhoto(1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ProjectInfoPhotoSlot(
                            file = castPhotoFiles.getOrNull(2),
                            onPick = { onPickCastPhoto(2) },
                            onRemove = { onRemoveCastPhoto(2) },
                            modifier = Modifier.weight(1f)
                        )
                        ProjectInfoPhotoSlot(
                            file = castPhotoFiles.getOrNull(3),
                            onPick = { onPickCastPhoto(3) },
                            onRemove = { onRemoveCastPhoto(3) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // --- Línea divisoria vertical entre "1" y "2" ---
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )

            // --- Lado 2 (derecha): vacío a propósito por ahora — próxima
            // actualización. ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { }
        }
    }

    if (showYearPicker) {
        ProjectInfoYearPickerDialog(
            initialYear = releaseYear,
            onDismiss = { showYearPicker = false },
            onConfirm = { year -> showYearPicker = false; onReleaseYearChange(year) }
        )
    }
    if (showGenrePicker) {
        ProjectInfoGenrePickerDialog(
            initialGenre = genre,
            onDismiss = { showGenrePicker = false },
            onConfirm = { g -> showGenrePicker = false; onGenreChange(g) }
        )
    }
    if (showDurationPicker) {
        ProjectInfoDurationPickerDialog(
            initialMinutes = durationMinutes,
            onDismiss = { showDurationPicker = false },
            onConfirm = { minutes -> showDurationPicker = false; onDurationMinutesChange(minutes) }
        )
    }
}

/** "1h 31m" (u "31m" si dura menos de una hora), formato ficha de película — ver referencia (Google "minions"). */
private fun formatInfoDuration(totalMinutes: Int?): String? {
    if (totalMinutes == null) return null
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Chip chico y tocable para cada una de las tres opciones al pie del título (año / categoría / duración). */
@Composable
private fun ProjectInfoOptionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp)
        )
    }
}

/** Una de las 4 casillas de foto de elenco/personajes: imagen + botón quitar si tiene, o "+" para cargar si está vacía. */
@Composable
private fun ProjectInfoPhotoSlot(
    file: File?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .then(if (file == null) Modifier.clickable(onClick = onPick) else Modifier)
    ) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = "Foto de elenco",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Quitar foto",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = "Agregar foto",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center).size(26.dp)
            )
        }
    }
}

// ============================================================
// Ruedas de selección (año / categoría / duración) — mismo look en los
// tres: tarjeta oscura centrada, título, rueda(s), Cancelar/Guardar.
// ============================================================

private val MOVIE_GENRES = listOf(
    "Acción", "Animación", "Aventura", "Bélica", "Biográfica", "Ciencia ficción",
    "Comedia", "Crimen", "Documental", "Drama", "Familiar", "Fantasía",
    "Misterio", "Musical", "Romance", "Suspenso", "Terror", "Western"
)

private fun currentCalendarYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

@Composable
private fun ProjectInfoYearPickerDialog(
    initialYear: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val years = remember { (1950..(currentCalendarYear() + 3)).toList() }
    var selectedIndex by remember {
        mutableStateOf(years.indexOf(initialYear ?: currentCalendarYear()).let { if (it >= 0) it else years.lastIndex })
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape, tonalElevation = 6.dp, color = SurfaceTintedElevated) {
            Column(modifier = Modifier.padding(20.dp).width(260.dp)) {
                Text(
                    "Año de lanzamiento",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                ProjectInfoWheelPicker(
                    items = years,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedIndex = it },
                    label = { it.toString() }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(years[selectedIndex]) }) { Text("Guardar") }
                }
            }
        }
    }
}

@Composable
private fun ProjectInfoGenrePickerDialog(
    initialGenre: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedIndex by remember {
        mutableStateOf(MOVIE_GENRES.indexOf(initialGenre).let { if (it >= 0) it else 0 })
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape, tonalElevation = 6.dp, color = SurfaceTintedElevated) {
            Column(modifier = Modifier.padding(20.dp).width(260.dp)) {
                Text(
                    "Categoría",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                ProjectInfoWheelPicker(
                    items = MOVIE_GENRES,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedIndex = it },
                    label = { it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(MOVIE_GENRES[selectedIndex]) }) { Text("Guardar") }
                }
            }
        }
    }
}

@Composable
private fun ProjectInfoDurationPickerDialog(
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val hoursList = remember { (0..5).toList() }
    val minutesList = remember { (0..59).toList() }
    val initial = initialMinutes ?: 90
    var hourIndex by remember { mutableStateOf((initial / 60).coerceIn(0, 5)) }
    var minuteIndex by remember { mutableStateOf((initial % 60).coerceIn(0, 59)) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape, tonalElevation = 6.dp, color = SurfaceTintedElevated) {
            Column(modifier = Modifier.padding(20.dp).width(280.dp)) {
                Text(
                    "Duración (${formatInfoDuration(hourIndex * 60 + minuteIndex)})",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Horas", color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ProjectInfoWheelPicker(
                            items = hoursList,
                            selectedIndex = hourIndex,
                            onSelectedIndexChange = { hourIndex = it },
                            label = { "${it}h" }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Minutos", color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ProjectInfoWheelPicker(
                            items = minutesList,
                            selectedIndex = minuteIndex,
                            onSelectedIndexChange = { minuteIndex = it },
                            label = { "${it}m" }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(hourIndex * 60 + minuteIndex) }) { Text("Guardar") }
                }
            }
        }
    }
}

/**
 * Rueda de selección estilo iOS: arrastrás/deslizás una lista vertical con
 * snap, el elemento centrado queda destacado (más grande, blanco sólido) y
 * el resto se ve más chico y semi-transparente. Tocar cualquier elemento
 * también lo selecciona y centra con una animación.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun <T> ProjectInfoWheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    itemHeight: androidx.compose.ui.unit.Dp = 40.dp,
    visibleCount: Int = 5
) {
    if (items.isEmpty()) return
    val clampedInitial = selectedIndex.coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = clampedInitial)
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val scope = rememberCoroutineScope()

    // Detecta cuál quedó centrado tras cada scroll/fling y avisa al padre.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val centered = if (offset > itemHeightPx / 2) index + 1 else index
                val clamped = centered.coerceIn(0, items.lastIndex)
                if (clamped != selectedIndex) onSelectedIndexChange(clamped)
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            onSelectedIndexChange(index)
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(item),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                        fontSize = if (isSelected) 19.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        // Guías arriba/abajo de la fila central, mismo criterio visual que
        // un date/time picker nativo.
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.align(Alignment.Center).offset(y = -(itemHeight / 2))
        )
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.align(Alignment.Center).offset(y = (itemHeight / 2))
        )
    }
}

/**
 * Joystick táctil estilo GTA San Andreas (versión Android): un anillo fijo
 * más un mando interior que el dedo arrastra dentro de ese anillo — al
 * soltar, el mando vuelve solo al centro con una animación de resorte,
 * igual que el joystick de movimiento de ese juego.
 *
 * Por ahora expone [onDirectionChange] con el vector normalizado (-1..1 en
 * cada eje, 0,0 = centro/soltado) para cuando se conecte a un personaje o
 * modelo real — todavía sin esa conexión, es el control en sí nada más.
 */
@Composable
fun GtaStyleJoystick(
    modifier: Modifier = Modifier,
    onDirectionChange: (x: Float, y: Float) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current
    val outerRadiusPx = with(density) { (JOYSTICK_OUTER_SIZE / 2).toPx() }
    val innerRadiusPx = with(density) { (JOYSTICK_INNER_SIZE / 2).toPx() }
    // Tope de recorrido del mando: hasta el borde del anillo exterior, menos
    // un poquito del propio mando — así el mando nunca se sale del anillo
    // ni queda "flotando" a mitad de camino cuando llega al límite.
    val maxDragPx = (outerRadiusPx - innerRadiusPx * 0.35f).coerceAtLeast(1f)

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun snapBackToCenter() {
        scope.launch { offsetX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        scope.launch { offsetY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        onDirectionChange(0f, 0f)
    }

    Box(
        modifier = modifier.size(JOYSTICK_OUTER_SIZE),
        contentAlignment = Alignment.Center
    ) {
        // Anillo exterior fijo — relleno oscuro translúcido + borde claro,
        // mismo lenguaje visual que el joystick de movimiento de GTA SA
        // Android (nunca un color ajeno a esa referencia).
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = size.minDimension / 2f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.38f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        // Mando interior, arrastrable dentro del anillo.
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(JOYSTICK_INNER_SIZE)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { snapBackToCenter() },
                        onDragCancel = { snapBackToCenter() }
                    ) { change, dragAmount ->
                        change.consume()
                        val nextX = offsetX.value + dragAmount.x
                        val nextY = offsetY.value + dragAmount.y
                        val dist = kotlin.math.sqrt(nextX * nextX + nextY * nextY)
                        val scaleToLimit = if (dist > maxDragPx) maxDragPx / dist else 1f
                        val clampedX = nextX * scaleToLimit
                        val clampedY = nextY * scaleToLimit
                        scope.launch { offsetX.snapTo(clampedX) }
                        scope.launch { offsetY.snapTo(clampedY) }
                        onDirectionChange(
                            (clampedX / maxDragPx).coerceIn(-1f, 1f),
                            (clampedY / maxDragPx).coerceIn(-1f, 1f)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.55f))
                    )
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}
