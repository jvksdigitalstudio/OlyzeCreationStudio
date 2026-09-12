package com.yeivikas.olyzecs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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

// --- Barra inferior de secciones (Keyframes / Control / Módulos) ---
// Altura chica a propósito ("que no sea tan gruesa"): es una barra de
// navegación entre paneles, no un panel en sí, así que no debe competir
// en peso visual con el timeline de arriba.
private val BOTTOM_BAR_HEIGHT = 40.dp
private val BOTTOM_BAR_ICON_SIZE = 16.dp

/**
 * Cabecera premium con tres secciones — Keyframes / Control / Módulos — que vive
 * pegada debajo del timeline. Arranca justo donde termina la columna de
 * miniaturas de las capas (mismo [labelColumnWidth] que usa [TimelineView]
 * para su propia columna), NUNCA desde el borde izquierdo de la pantalla:
 * ese primer tramo queda transparente, dejando ver el relleno morado de
 * fondo, para que la barra se lea alineada con las pistas de la derecha y no
 * con la columna de capas.
 *
 * Por ahora es puramente visual — cada sección no dispara nada todavía,
 * eso llega después con [onKeyframesClick] / [onControlClick] / [onModulesClick].
 */
@Composable
fun EditorBottomBar(
    modifier: Modifier = Modifier,
    labelColumnWidth: Dp = LABEL_COLUMN_WIDTH,
    selectedSection: BottomBarSection? = null,
    onKeyframesClick: () -> Unit = {},
    onControlClick: () -> Unit = {},
    onModulesClick: () -> Unit = {}
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
                label = "Módulos",
                isSelected = selectedSection == BottomBarSection.MODULES,
                onClick = onModulesClick,
                icon = { tint -> ModuleTabIcon(tint) },
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
                label = "Keyframes",
                isSelected = selectedSection == BottomBarSection.KEYFRAMES,
                onClick = onKeyframesClick,
                icon = { tint -> KeyframesTabIcon(tint) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Qué sección de [EditorBottomBar] está activa. Solo visual por ahora. */
enum class BottomBarSection { KEYFRAMES, CONTROL, MODULES }

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

/** Ícono de "Módulos": el glifo premium mdi-view-module
 * entregado por el usuario, cargado como vector drawable
 * ([R.drawable.ic_module_layer]) en vez de dibujarse a mano con [Canvas]
 * — mismo criterio que ya usa el proyecto para [R.drawable.ic_modules_menu]/
 * [R.drawable.ic_close]: XML tintable, el color real se aplica acá vía
 * [tint]. */
@Composable
private fun ModuleTabIcon(tint: Color, iconSize: Dp = BOTTOM_BAR_ICON_SIZE) {
    Icon(
        painter = painterResource(id = R.drawable.ic_module_layer),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(iconSize)
    )
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
 * tres pestañas de [EditorBottomBar] — Keyframes / Control / Módulos. Por
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
    hasImageLayerSelected: Boolean = false,
    // --- Solo relevante para BottomBarSection.MODULES: si YA hay un módulo
    // cargado en este panel. Mismo criterio que [hasImageLayerSelected]
    // arriba — bandera explícita con default `false` (hoy no existe
    // ningún mecanismo real para "cargar" un módulo desde el cajón, ver
    // [ModulesDrawerPanel]/[ModuleRow], así que siempre vale `false` por
    // ahora), no hardcodeado adentro de esta función, para que el día que
    // exista ese mecanismo real esto se pueda conectar sin tocar este
    // archivo. Controla si el ícono + "Módulos de capa" + "Vacío" de más
    // abajo se muestran (solo mientras no hay nada cargado) o se ocultan
    // (una vez que sí hay un módulo real en este panel).
    hasLoadedModule: Boolean = false,
    // --- FASE 1 del joystick de Control: vector normalizado (-1..1 en cada
    // eje, 0,0 = soltado) reenviado tal cual desde [GtaStyleJoystick] (que
    // vive DENTRO de este panel, no en EditorBottomBar) hacia quien de
    // verdad sabe mover una capa (EditorScreen, dueño de translateX/Y y
    // commitLiveFrame()). Este composable NO decide nada de física/velocidad
    // — solo pasa el dato.
    onJoystickDirectionChange: (x: Float, y: Float) -> Unit = { _, _ -> },
    // Reenviado tal cual hacia [ModulesDrawerPanel] — ver el comentario
    // grande en su propio parámetro `onZoomXClick` para el porqué de un
    // callback con nombre explícito en vez de un id de módulo genérico.
    onZoomXModuleClick: () -> Unit = {}
) {
    // `label`: nombre corto de la sección, para el botón ✕ ("Cerrar panel
    // de $label" — necesita leerse bien en esa frase). `panelTitle`: el
    // título grande que se ve dentro del panel — para Módulos son textos
    // distintos a propósito ("Módulos" suena bien como "Cerrar panel de
    // Módulos"; "Módulos de capa" es el título que se pidió para el
    // estado vacío del panel en sí, y "Cerrar panel de Módulos de capa"
    // leería raro).
    val label = when (section) {
        BottomBarSection.KEYFRAMES -> "Keyframes"
        BottomBarSection.CONTROL -> "Control"
        BottomBarSection.MODULES -> "Módulos"
    }
    val panelTitle = when (section) {
        BottomBarSection.MODULES -> "Módulos de capa"
        else -> label
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
        // --- Zona de activación del joystick de Control: NO todo el panel
        // (eso fue un error de una vuelta anterior) — un rectángulo
        // ACOTADO, pegado a la mitad izquierda/inferior, dejando libre
        // arriba (donde vive el menú ☰) y a la derecha (reservado para los
        // próximos íconos pulsables que se sumen a este panel — nada de
        // que el joystick les robe el toque el día de mañana). Ver
        // [CONTROL_JOYSTICK_ZONE_WIDTH_FRACTION] / [_TOP_PADDING] más abajo
        // para los números exactos — son el único lugar que hay que tocar
        // si el límite tiene que moverse.
        //
        // Se declara PRIMERO (antes que el botón ✕ y el menú "Control")
        // a propósito: en un Box de Compose los hijos declarados DESPUÉS
        // quedan por ENCIMA en dibujo Y en prioridad táctil, así que ✕ y
        // el menú — declarados después — siguen consumiendo sus propios
        // toques con normalidad, y cualquier futuro ícono que se agregue
        // en la franja libre de la derecha también podrá declararse
        // después de esto sin que el joystick se lo coma.
        if (section == BottomBarSection.CONTROL) {
            GtaStyleJoystick(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(CONTROL_JOYSTICK_ZONE_WIDTH_FRACTION)
                    .fillMaxHeight()
                    .padding(top = CONTROL_JOYSTICK_ZONE_TOP_PADDING),
                onDirectionChange = onJoystickDirectionChange
            )
        }

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
            // --- El joystick en sí (zona de activación de todo el panel +
            // aro que nace donde tocás) se declaró arriba de todo, antes
            // del botón ✕ — ver el comentario grande ahí. Acá ya no va
            // nada más de él. ---
        } else {
            // --- El ícono + título + "Vacío"/"Próximamente" de abajo son
            // el estado VACÍO de este panel. Para Módulos, a pedido
            // explícito: solo debe verse mientras NO hay ningún módulo
            // cargado en la ventana — apenas exista uno real, este bloque
            // entero (ícono + "Módulos de capa" + "Vacío") debe
            // desaparecer y dejarle el lugar al contenido real del
            // módulo (todavía no implementado, ver [hasLoadedModule]
            // más arriba). Para Keyframes esto no aplica — siempre se ve,
            // como hasta ahora.
            if (section != BottomBarSection.MODULES || !hasLoadedModule) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (section) {
                        BottomBarSection.KEYFRAMES -> KeyframesTabIcon(BrandPurpleLight, iconSize = 36.dp)
                        BottomBarSection.MODULES -> ModuleTabIcon(BrandPurpleLight, iconSize = 36.dp)
                        BottomBarSection.CONTROL -> Unit // no llega acá, ver rama de arriba
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = panelTitle,
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        // Módulos usa "Vacío" (estado real: la ventana está
                        // vacía hasta que se cargue un módulo) — Keyframes
                        // mantiene "Próximamente" (esa sección todavía no
                        // tiene ninguna implementación real detrás).
                        text = if (section == BottomBarSection.MODULES) "Vacío" else "Próximamente",
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // --- Módulos: ícono de menú en la esquina sup. izquierda que
            // despliega un cajón lateral — desliza de izquierda a
            // derecha, ESQUINAS RECTAS (sin redondear) y contenido
            // íntegramente adentro de este panel de Módulos, nunca por
            // fuera de sus bordes.
            //
            // Comportamiento tipo ventana profesional, a pedido
            // explícito:
            // - Al abrir el cajón, el ícono de menú se OCULTA (no
            //   quedan los dos superpuestos) — solo vuelve a aparecer
            // cuando el cajón se cierra.
            // - Tocar CUALQUIER punto del panel de Módulos por fuera del
            //   cajón lo cierra (el "scrim" invisible de abajo, mismo
            //   patrón que un modal/drawer estándar) — tocar DENTRO del
            //   cajón no lo cierra, porque el propio Surface del cajón
            //   ya frena sus toques antes de que lleguen al scrim.
            if (section == BottomBarSection.MODULES) {
                var showModulesDrawer by remember { mutableStateOf(false) }

                // Scrim: solo existe (y solo intercepta toques) mientras
                // el cajón está abierto. Se dibuja ANTES del cajón acá
                // abajo, así el cajón queda arriba en el orden de
                // dibujo/hit-testing y sus propios toques nunca lo
                // atraviesan.
                if (showModulesDrawer) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showModulesDrawer = false }
                            )
                    )
                }

                if (!showModulesDrawer) {
                    ModulesMenuBadge(
                        onClick = { showModulesDrawer = true },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 10.dp)
                    )
                }

                ModulesDrawerPanel(
                    visible = showModulesDrawer,
                    onZoomXClick = onZoomXModuleClick,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                )
            }
        }
    }
}

// --- Límite de la zona de activación del joystick dentro del panel
// "Control" — ver el comentario grande donde se llama a [GtaStyleJoystick]
// más arriba. Ancho como FRACCIÓN del panel (no un dp fijo) para que se
// adapte a cualquier tamaño de pantalla. El margen superior deja libre la
// franja donde vive [ControlMenuBadge] (y su desplegable) para que el
// joystick nunca nazca tapado debajo de ese menú.
//
// AJUSTADO a pedido explícito (una vuelta encima del primer recorte): la
// zona abarcaba de más tanto arriba como al costado — el aro llegaba a
// nacer pegado casi contra la franja superior del panel. Se redujo el
// ancho (0.55 → 0.40 del panel) y se subió bastante el margen superior
// (64dp → 140dp) para que la zona quede un rectángulo más chico y más
// abajo, bien despegado del menú ☰ y de la franja derecha reservada para
// futuros íconos.
private const val CONTROL_JOYSTICK_ZONE_WIDTH_FRACTION = 0.40f
private val CONTROL_JOYSTICK_ZONE_TOP_PADDING = 140.dp

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
 * Ícono de menú del panel Módulos (esquina sup. izquierda) — SOLO el glifo
 * vectorial provisto ([R.drawable.ic_modules_menu]), sin ningún fondo,
 * círculo blanco, aro ni sombra alrededor: eso no estaba en el SVG que
 * se pidió agregar, así que no corresponde inventarlo acá.
 */
@Composable
private fun ModulesMenuBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_modules_menu),
            contentDescription = "Menú de módulos",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// Angosto a propósito — corregido tras feedback: la primera versión
// (0.34f) ocupaba más de un tercio del panel, demasiado ancho para un
// cajón que todavía no tiene contenido. Este valor se acerca al grosor
// real marcado a mano sobre la captura de referencia.
private val MODULES_DRAWER_WIDTH_FRACTION = 0.20f

/**
 * Cajón lateral del panel Módulos, disparado por [ModulesMenuBadge]. A pedido
 * explícito:
 * - Desliza de izquierda a derecha (entra/sale en horizontal, no un fade
 *   ni un cambio de tamaño en el lugar).
 * - Esquinas RECTAS — nada de [RoundedCornerShape], forma
 *   [RectangleShape] pura, ni siquiera en el borde derecho.
 * - Vive íntegramente DENTRO del panel de Módulos: ocupa el alto completo
 *   del panel (el propio Box de Módulos ya lo recorta a esa zona) y un
 *   ancho fijo como fracción de ese mismo panel — nunca se sale de su
 *   contenedor ni tapa la barra Keyframes/Control/Módulos de más abajo.
 *
 * Contenido, de arriba hacia abajo:
 * - [ModulesSearchField]: buscador FIJO — vive fuera del área con scroll de
 *   abajo, así siempre queda visible sin importar cuántos módulos haya
 *   ni cuánto se baje en la lista.
 * - Lista de módulos (con scroll propio, independiente del buscador). Por
 *   ahora, a pedido explícito, las dos primeras opciones: "Sombra" y
 *   "Reflejo" — el resto de los módulos llega después, en este mismo
 *   listado.
 */
@Composable
private fun ModulesDrawerPanel(
    visible: Boolean,
    // Se dispara al tocar la fila "Zoom X" (categoría "Cámara" — ver más
    // abajo). A diferencia de Sombra/Reflejo (que todavía solo alternan
    // su propia flecha ▾/▴, sin contenido real detrás — ver el
    // comentario grande en su declaración), Zoom X SÍ tiene una ventana
    // real: [ZoomXFloatingWindow]. Esta fila es la primera de este cajón
    // en usar ese mecanismo — de ahí el nombre explícito del callback en
    // vez de un genérico "onModuleClick(id)": todavía no existe una
    // lista de módulos con id/registro genérico en este cajón (Sombra/
    // Reflejo no lo necesitan aún), así que inventar esa abstracción
    // genérica ahora, para un solo caso real, sería sobre-diseño. El día
    // que un segundo módulo necesite el mismo mecanismo es el momento
    // correcto para generalizar esto — no antes.
    onZoomXClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // `Box`/`AnimatedVisibility` NO recortan a sus hijos por sí solos: como
    // `slideInHorizontally` mueve el contenido con `translationX` (no
    // cambia su tamaño de layout), sin un `clipToBounds()` en un
    // contenedor FIJO (que no se mueva junto con el cajón) el cajón se
    // pinta por fuera de esta ventana mientras se desliza para entrar —
    // se ve "salir" desde la columna de capas / el borde de la app en vez
    // de nacer limpio en el borde izquierdo de ESTA ventana. Este Box
    // envolvente es ese límite fijo: mismo tamaño que el panel de Módulos,
    // nunca se mueve, y recorta cualquier cosa (contenido + sombra) que
    // intente pintarse fuera de él en cualquier frame de la animación.
    Box(
        modifier = modifier.clipToBounds()
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                animationSpec = tween(durationMillis = 220),
                initialOffsetX = { fullWidth -> -fullWidth }
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = 200),
                targetOffsetX = { fullWidth -> -fullWidth }
            ) + fadeOut(animationSpec = tween(durationMillis = 160))
        ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(MODULES_DRAWER_WIDTH_FRACTION)
                .fillMaxHeight()
                // clip = true (antes false): la sombra también debe
                // quedar contenida en el rectángulo del cajón — con
                // clip = false se pintaba por fuera de sus límites
                // durante el deslizamiento, agravando el mismo problema.
                .shadow(elevation = 12.dp, shape = RectangleShape, clip = true)
                // Frenamos acá cualquier toque que caiga en el cajón para
                // que no atraviese hacia el scrim/canvas de atrás — mismo
                // criterio que [blockTouchesFromPassingThrough]. Esto es
                // también lo que hace que tocar DENTRO del cajón no lo
                // cierre (solo lo cierra tocar afuera, sobre el scrim).
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            color = SurfaceTintedElevated,
            shape = RectangleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Filo de marca en el borde derecho — el mismo recurso
                // visual (línea en BrandPurpleLight) que separa a los
                // demás paneles del resto de la UI, acá vertical porque
                // este cajón se abre en horizontal.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(BrandPurpleLight.copy(alpha = 0.35f))
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 10.dp)
                ) {
                    var query by remember { mutableStateOf("") }
                    ModulesSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ModulesCategoryHeader(
                            title = "Efectos",
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        // Estado de acordeón por fila — independiente
                        // entre sí (abrir "Sombra" no afecta a
                        // "Reflejo"). Por ahora SOLO controla la
                        // dirección de la flecha ▾/▴ (a pedido explícito:
                        // todavía sin el contenido de módulos real
                        // debajo — eso llega después).
                        var sombraExpanded by remember { mutableStateOf(false) }
                        var reflejoExpanded by remember { mutableStateOf(false) }
                        ModuleRow(
                            label = "Sombra",
                            icon = { ShadowModuleIcon(tint = Color.White.copy(alpha = 0.82f)) },
                            expanded = sombraExpanded,
                            onClick = { sombraExpanded = !sombraExpanded }
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        ModuleRow(
                            label = "Reflejo",
                            icon = { ReflectionModuleIcon(tint = Color.White.copy(alpha = 0.82f)) },
                            expanded = reflejoExpanded,
                            onClick = { reflejoExpanded = !reflejoExpanded }
                        )

                        // --- Separador FINO entre categorías — a pedido
                        // explícito, distinto del divisor entre filas de
                        // arriba (ese es casi invisible, alpha 0.06f,
                        // solo separa ítems DENTRO de la misma
                        // categoría). Este marca el corte de categoría:
                        // más visible (alpha 0.16f) y con su propio
                        // margen vertical para que respire antes del
                        // siguiente título. ---
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.padding(vertical = 10.dp)
                        )

                        ModulesCategoryHeader(
                            title = "Animación",
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        // Sin módulos todavía debajo de "Animación" —
                        // solo el título de la categoría por ahora, tal
                        // cual se pidió.

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.padding(vertical = 10.dp)
                        )

                        // --- Categoría "Cámara" — a pedido explícito,
                        // debajo de "Animación". Por ahora un solo
                        // módulo, "Zoom X" (ver ZoomXWheel.kt/
                        // ZoomXFloatingWindow.kt — archivos propios, no
                        // mezclados acá: este bloque solo REGISTRA la
                        // fila y reenvía el toque, ninguna lógica del
                        // módulo vive en este archivo). ---
                        ModulesCategoryHeader(
                            title = "Cámara",
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        ModuleRow(
                            label = "Zoom X",
                            icon = { ZoomXModuleIcon(tint = Color.White.copy(alpha = 0.82f)) },
                            showChevron = false,
                            onClick = onZoomXClick
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Buscador fijo del cajón Módulos — filtra los módulos de la lista de abajo
 * (por ahora "Sombra" / "Reflejo", crece con el resto de módulos). Look
 * "premium" consistente con el resto del panel: superficie oscura sutil,
 * borde tenue, lupa dibujada a mano ([SearchGlyph], mismo criterio que
 * [KeyframesTabIcon] — Canvas en vez de un recurso .xml extra — a
 * diferencia de [ModuleTabIcon], que sí es un vector .xml por ser un
 * glifo entregado ya armado, no dibujado a mano acá), cursor en el
 * morado de marca.
 */
@Composable
private fun ModulesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = SurfaceTintedDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchGlyph(tint = Color.White.copy(alpha = 0.5f), iconSize = 14.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Buscar módulos",
                        color = Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(BrandPurpleLight),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Lupa dibujada a mano para [ModulesSearchField] — círculo + mango en diagonal. */
@Composable
private fun SearchGlyph(tint: Color, iconSize: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        val strokeW = size.minDimension * 0.14f
        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = Stroke(width = strokeW)
        )
        val dirX = 0.7071f // 45°
        drawLine(
            color = tint,
            start = Offset(center.x + radius * dirX, center.y + radius * dirX),
            end = Offset(size.width * 0.94f, size.height * 0.94f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Título de categoría dentro del listado del cajón Módulos (p. ej. "Efectos",
 * "Animación") — texto más grande y grueso que las filas de módulos de
 * abajo, a propósito, para que se lea claramente como encabezado de
 * sección y no como una fila más de la lista.
 */
@Composable
private fun ModulesCategoryHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.95f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

/**
 * Fila de un módulo dentro del listado del cajón Módulos — ícono + nombre +
 * indicador de acordeón a la derecha, con realce sutil al presionar
 * (mismo criterio "sin ripple genérico de Material" que el resto de los
 * controles custom de este archivo, pero con feedback táctil real vía
 * [MutableInteractionSource]).
 *
 * [expanded] SOLO controla la dirección del indicador (▾ cerrado / ▴
 * abierto) por ahora — el contenido real que se despliega debajo de cada
 * módulo (a pedido explícito, todavía no) llega en una próxima ronda;
 * esta es la mecánica visual/de estado nada más.
 */
@Composable
private fun ModuleRow(
    label: String,
    icon: @Composable () -> Unit,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
    // Default `true` a propósito — preserva a Sombra/Reflejo (los únicos
    // llamadores existentes hasta ahora) sin tocarlos. `false` es para
    // filas que NO se expanden in-place — abren su propia ventana
    // flotante en su lugar (ver "Zoom X", categoría "Cámara" más abajo)
    // — mostrar un acordeón ▾/▴ ahí confundiría, prometiendo un
    // despliegue que nunca ocurre en el mismo lugar.
    showChevron: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isPressed) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            ModuleAccordionChevron(expanded = expanded)
        }
    }
}

/**
 * Indicador de acordeón (▾ cerrado / ▴ abierto) de [ModuleRow] —
 * triángulo sólido con esquinas levemente redondeadas, mismo lenguaje
 * visual que el ícono de referencia provisto (glifo tipo "play" macizo,
 * sin contorno), dibujado a mano con [Canvas]/[DrawScope] — igual
 * criterio que el resto de los glifos de este archivo, así queda nítido
 * en cualquier densidad sin depender de un drawable extra.
 *
 * En reposo (cerrado) apunta hacia ABAJO; al expandirse rota 180° en
 * tiempo real (animado) y queda apuntando hacia ARRIBA — exactamente el
 * comportamiento pedido.
 */
@Composable
private fun ModuleAccordionChevron(
    expanded: Boolean,
    tint: Color = Color.White.copy(alpha = 0.55f),
    iconSize: Dp = 11.dp
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "moduleAccordionChevronRotation"
    )
    Canvas(
        modifier = Modifier
            .size(iconSize)
            .rotate(rotation)
    ) {
        val path = Path().apply {
            val cornerInset = size.minDimension * 0.08f
            moveTo(cornerInset, size.height * 0.22f)
            lineTo(size.width - cornerInset, size.height * 0.22f)
            lineTo(size.width * 0.5f, size.height * 0.86f)
            close()
        }
        drawPath(path, color = tint)
    }
}

/** Ícono del módulo "Sombra": objeto + su sombra proyectada, difusa, debajo. */
@Composable
private fun ShadowModuleIcon(tint: Color, iconSize: Dp = 16.dp) {
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

/** Ícono del módulo "Reflejo": forma arriba, línea de espejo, y su reflejo invertido y atenuado abajo. */
@Composable
private fun ReflectionModuleIcon(tint: Color, iconSize: Dp = 16.dp) {
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

/**
 * Panel "Información del proyecto": título, sinopsis/resumen, créditos,
 * etc. — a pedido del usuario, algo como la descripción de un video de
 * YouTube pero del proyecto/película entero.
 *
 * A diferencia de [SectionPlaceholderPanel] (que se recorta DENTRO del
 * hueco del timeline, respetando la regla de tiempo y la barra
 * Keyframes/Control/Módulos, que siguen visibles), este panel reemplaza esa
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
        // ✕ del panel de Keyframes/Control/Módulos (SectionPlaceholderPanel,
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

// --- GtaStyleJoystick vive ahora en su propio archivo: Joystick.kt (mismo
// paquete `com.yeivikas.olyzecs.ui`, así que se usa acá abajo sin import
// extra). Separado a propósito de este archivo — EditorBottomBar.kt es
// sobre la BARRA y sus paneles; el joystick es un control de entrada
// genérico y reutilizable que no tiene por qué vivir mezclado con eso. ---
