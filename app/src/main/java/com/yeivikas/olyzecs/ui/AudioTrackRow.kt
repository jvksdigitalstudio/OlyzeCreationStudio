package com.yeivikas.olyzecs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.AudioWaveform
import com.yeivikas.olyzecs.engine.audio.AudioWaveformAnalyzer
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import com.yeivikas.olyzecs.ui.theme.effectiveAudioBrush
import com.yeivikas.olyzecs.ui.theme.effectiveAudioColorStrong
import kotlin.math.roundToInt

/**
 * Alto de esta fila — MISMO valor que [ROW_HEIGHT] (la fila de una capa de
 * imagen) para que la playlist se lea pareja, fila tras fila. Definido acá
 * (no reutilizando la constante de `TimelineView.kt`) porque esa es
 * `private` a ese archivo — este es el único costo real de que
 * `AudioTrackRow` viva en su propio archivo, a propósito, sin mezclar
 * responsabilidades con `TimelineView.kt` (que solo la invoca).
 */
private val AUDIO_TRACK_ROW_HEIGHT = 36.dp

/** Color de identidad de la pista de audio en el timeline — distinto del
 * de cualquier capa de imagen (esas usan su propio color cíclico/dominante,
 * ver `effectiveLayerColor`), así se reconoce de un vistazo que esta fila
 * es audio y no una capa más. */
private val AUDIO_TRACK_COLOR = Color(0xFF26A69A)

/**
 * Fila de la pista de audio del proyecto dentro de la playlist de capas
 * del timeline (`TimelineView`). A PEDIDO EXPLÍCITO DEL USUARIO — tres
 * comportamientos reales, no cosméticos:
 *
 * 1. Se ordena junto a las capas de imagen respetando el orden real de
 *    carga (ver `AudioClip.trackOrder` y el mezclado en `TimelineView`),
 *    nunca fija arriba ni abajo de todo.
 * 2. El bloque de color de esta fila (la señal/rango de audio, distinto
 *    del "Inicio del recorte" que ya existía — ese recorta el ARCHIVO
 *    fuente, este posiciona el clip DENTRO del proyecto) se puede
 *    arrastrar a izquierda o derecha, DENTRO de su propio carril, para
 *    fijar en qué punto del timeline empieza a sonar — igual referencia
 *    que un clip de audio en cualquier DAW (Cubase/FL Studio/Audition):
 *    el clip entero se desliza como un bloque, guiándose por la regla de
 *    tiempo de arriba. Este arrastre es horizontal y vive enteramente
 *    ACÁ, en el carril de la derecha — no comparte mecanismo con el
 *    arrastre VERTICAL del punto 3.
 * 3. La columna izquierda (ícono + nombre) se puede mantener presionada
 *    y arrastrar arriba/abajo para REORDENAR esta fila dentro de la
 *    playlist, exactamente igual que la miniatura de cualquier capa de
 *    imagen (ver el mismo gesto en `TimelineRow`, dentro de
 *    `TimelineView.kt`). BUG REAL corregido acá, reportado con capturas
 *    de pantalla: antes esta columna no tenía NINGÚN gesto de arrastre
 *    vertical — la fila de audio quedaba fija en la playlist mientras
 *    todas las de imagen sí se podían reordenar arrastrando. El cálculo
 *    de a dónde mover la fila (cuántas posiciones cruzó el dedo, qué
 *    filas vecinas hay que correr para hacerle espacio mientras tanto)
 *    vive en `TimelineView` — igual que el de cualquier capa —, porque
 *    hace falta ver la playlist completa para eso, no solo esta fila;
 *    acá solo se detecta el gesto (long-press + arrastre) y se avisa
 *    hacia arriba con [onReorderDragStart]/[onReorderDrag]/
 *    [onReorderDragEnd]/[onReorderDragCancel] — mismo contrato que ya
 *    usa `TimelineRow`, así `TimelineView` no necesita distinguir de
 *    qué tipo de fila viene el aviso.
 * 4. Panel de acciones rápidas "al costado" (bug real reportado con
 *    capturas de pantalla: "todas las capas [de imagen] tienen esta
 *    ventana flotante... esta última implementada de audio no, falta").
 *    Mismo gesto de dos toques que la miniatura de cualquier capa de
 *    imagen en `TimelineRow`: el primer toque sobre la columna
 *    ícono+nombre solo SELECCIONA la fila; con la fila ya seleccionada,
 *    un toque más despliega el panel — un `Popup` de Compose posicionado
 *    con el mismo [BesideAnchorPopupPositionProvider] que usa
 *    `TimelineRow`, pegado al borde derecho de esa columna.
 *
 *    A PEDIDO EXPLÍCITO DEL USUARIO, este panel usa el MISMO set genérico
 *    de acciones que cualquier capa de imagen — Renombrar/Cambiar
 *    color/Bloqueo de orden/Eliminar — en vez del set de 5 módulos de
 *    audio (Volumen/Silenciar/Recorte/Bucle/Desvanecidos) que este panel
 *    tuvo en una versión anterior. SIN el ícono de "ojo" (visibilidad)
 *    NI el candado de "bloquear en el canvas" (`Layer.locked`): a
 *    diferencia de una capa de imagen, el audio no se dibuja ni se
 *    manipula en el canvas, así que ni un toggle de visibilidad ni un
 *    bloqueo de movimiento en el canvas tienen nada que hacer acá — 4
 *    acciones, no 6. (El candado de canvas llegó a existir brevemente en
 *    esta fila, bloqueando el arrastre horizontal dentro del carril; se
 *    quitó por completo — modelo, persistencia, ViewModel y UI — a
 *    pedido explícito del usuario, ver el KDoc grande "SIN el
 *    equivalente de Layer.locked" en `AudioClip.kt`.) El candado de
 *    ORDEN sí se queda: bloquear el reordenamiento vertical en la
 *    playlist aplica igual a audio que a cualquier capa.
 *
 *    SEGUNDA CORRECCIÓN REAL (reportada con captura de pantalla: "no
 *    funciona ningún ícono excepto eliminar" + "se ve opaco/feo"): la
 *    versión anterior de esta fila dejaba 4 de esas acciones
 *    (Renombrar/Color/Bloqueo de orden/Bloqueo de capa) como
 *    placeholders con `onClick` vacío y tinte apagado a propósito —
 *    `AudioClip` todavía no tenía `orderLocked`/color propio. Ahora los
 *    tiene (ver el bloque grande "Paridad con Layer" en `AudioClip.kt`)
 *    y las acciones están conectadas de verdad, con el
 *    MISMO diálogo de renombrar ([RenameLayerDialog]) y de color
 *    ([LayerColorPickerDialog]) que ya usa cualquier capa de imagen —
 *    reutilizados tal cual, no reinventados para audio, así la calidad y
 *    el comportamiento (sólido, degradado, cuentagotas del preview,
 *    cuadrícula de daltonismo, todo) quedan IDÉNTICOS. El panel entero
 *    (fondo, borde) también dejó de usar el color fijo [AUDIO_TRACK_COLOR]
 *    a secas y ahora pinta el color/degradado REAL elegido para esta
 *    pista (ver [effectiveAudioBrush]/[effectiveAudioColorStrong]),
 *    cayendo a [AUDIO_TRACK_COLOR] solo mientras no se personalizó nada
 *    — mismo criterio exacto que usa `TimelineRow` con
 *    `effectiveLayerBrush`/`layerTrackColor`.
 */
@Composable
internal fun AudioTrackRow(
    audioClip: AudioClip,
    isSelected: Boolean,
    trackWidthPx: Float,
    projectDurationMs: Long,
    onSelect: () -> Unit,
    onTimelineStartChange: (Long) -> Unit,
    onRemoveRequest: () -> Unit,
    // --- Arrastre VERTICAL de reordenamiento (punto 3 del KDoc de
    // arriba) — mismo contrato exacto que `TimelineRow.onReorderDragStart`
    // y compañía: `TimelineView` es quien de verdad calcula/aplica el
    // reordenamiento, esta fila solo reporta el gesto crudo.
    isDragging: Boolean = false,
    visualOffsetPx: Float = 0f,
    onReorderDragStart: () -> Unit = {},
    onReorderDrag: (deltaY: Float) -> Unit = {},
    onReorderDragEnd: () -> Unit = {},
    onReorderDragCancel: () -> Unit = {},
    // --- Panel de acciones rápidas "al costado" (punto 4 del KDoc de
    // arriba). `isExpanded`/`onToggleExpand`: mismo contrato que
    // `TimelineRow.isExpanded`/`onToggleExpand` — `TimelineView` reusa el
    // mismo `expandedLayerIds` (con el sentinela `AUDIO_TRACK_ID`) que ya
    // usa para cualquier capa de imagen, así que esta fila no necesita su
    // propio Set aparte. `isBottomPanelExpanded`: cuando alguna de las
    // tres pestañas de abajo (Keyframes/Control/Módulos) está abierta, el
    // panel se suspende por completo en vez de abrirse "al costado"
    // (quedaría tapado por el panel de esa pestaña) — mismo criterio que
    // `TimelineRow`, sin construir un acordeón "de abajo" equivalente
    // para esta fila: alcance deliberadamente acotado a lo pedido (el
    // panel lateral), no una reimplementación completa del acordeón.
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    isBottomPanelExpanded: Boolean = false,
    // --- Las acciones que antes eran placeholders, ahora reales — ver
    // el KDoc de arriba, "SEGUNDA CORRECCIÓN REAL". Mismos contratos
    // exactos que sus equivalentes de `TimelineRow`
    // (`onRenameRequest`/`onChangeColor`/`onChangeGradient`/
    // `onResetColor`/`onToggleOrderLock`/`onRequestEyedropper`/
    // `pickedEyedropperColor`/`onConsumeEyedropperResult`), para poder
    // reutilizar tal cual [RenameLayerDialog] y [LayerColorPickerDialog]
    // sin ninguna adaptación de firma. SIN `onToggleLock` (candado de
    // canvas): no aplica a audio, ver KDoc punto 4.
    onRenameRequest: (newName: String) -> Unit = {},
    onChangeColor: (colorArgb: Int, useBlackAndWhiteMode: Boolean) -> Unit = { _, _ -> },
    onChangeGradient: (startArgb: Int, endArgb: Int, angleDegrees: Float, isRadial: Boolean, useBlackAndWhiteMode: Boolean) -> Unit = { _, _, _, _, _ -> },
    onResetColor: () -> Unit = {},
    onToggleOrderLock: () -> Unit = {},
    onRequestEyedropper: () -> Unit = {},
    pickedEyedropperColor: Int? = null,
    onConsumeEyedropperResult: () -> Unit = {},
    // --- "Multicolor" (ícono capas+flecha del timeline): mismo contrato que
    // `TimelineRow.multiColorSelectActive`/`isMultiColorSelected`/
    // `onToggleMultiColorSelect`/`labelColumnColorOverride`. BUG REAL
    // corregido (reportado con capturas): la pista de audio no tenía punto
    // de selección, no entraba en "Seleccionar todo" y el degradado
    // repartido del grupo se cortaba antes de su fila.
    multiColorSelectActive: Boolean = false,
    isMultiColorSelected: Boolean = false,
    onToggleMultiColorSelect: () -> Unit = {},
    labelColumnColorOverride: Color? = null
) {
    val safeDuration = projectDurationMs.coerceAtLeast(1L)
    val pxPerMs = if (safeDuration > 0L) trackWidthPx / safeDuration else 0f
    val density = LocalDensity.current

    // Referencias siempre-al-día para el pointerInput de más abajo: el
    // lambda de detectDragGesturesAfterLongPress se arma UNA sola vez
    // (la key del pointerInput no cambia entre recomposiciones) pero
    // necesita llamar siempre a la versión MÁS RECIENTE de estos
    // callbacks — mismo patrón que ya usa `TimelineRow` para el gesto
    // equivalente de las capas de imagen.
    val currentOnReorderDragStart by rememberUpdatedState(onReorderDragStart)
    val currentOnReorderDrag by rememberUpdatedState(onReorderDrag)
    val currentOnReorderDragEnd by rememberUpdatedState(onReorderDragEnd)
    val currentOnReorderDragCancel by rememberUpdatedState(onReorderDragCancel)

    // --- Panel de acciones "al costado" (punto 4 del KDoc de arriba) ---
    // Misma animación de entrada/salida (fade + scale desde la esquina
    // superior-izquierda, 150ms) que ya usa `TimelineRow` para el panel
    // de cualquier capa de imagen — `MutableTransitionState` en vez de
    // un simple `if (isExpanded)` para que la salida (al colapsar) TAMBIÉN
    // se anime en vez de desaparecer de un salto.
    // Borde derecho real (coords. de ventana) de la columna de etiqueta —
    // donde nace la línea del playhead. Ver KDoc de
    // BesideAnchorPopupPositionProvider.columnRightPx (mismo bug, mismo fix
    // que en TimelineRow: el Popup se anclaba al contenido interior, sin
    // el padding de la columna).
    var labelColumnRightPx by remember { mutableStateOf<Int?>(null) }
    val playheadCoverBleedPx = rememberPlayheadCoverBleedPx()
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(isExpanded) { visibleState.targetState = isExpanded }
    val showActionsPanel = visibleState.currentState || visibleState.targetState

    // --- Color/degradado de identidad REAL de esta pista — mismo patrón
    // exacto que `trackColor`/`trackColorStrong`/`trackBrush` en
    // `TimelineRow` (TimelineView.kt), aplicado acá vía
    // [effectiveAudioColor]/[effectiveAudioBrush] (Theme.kt): si el
    // usuario personalizó un color sólido o un degradado desde el panel
    // de acciones, la fila entera lo refleja de verdad; si no, cae al
    // "de fábrica" [AUDIO_TRACK_COLOR] — exactamente como se veía antes
    // de esta mejora.
    val ownTrackColorStrong = remember(
        audioClip.customColorArgb, audioClip.useGradientColor,
        audioClip.customGradientStartArgb, audioClip.customGradientEndArgb
    ) { effectiveAudioColorStrong(audioClip, AUDIO_TRACK_COLOR) }
    val ownTrackBrush = remember(
        audioClip.customColorArgb, audioClip.useGradientColor,
        audioClip.customGradientStartArgb, audioClip.customGradientEndArgb,
        audioClip.gradientAngleDegrees, audioClip.gradientIsRadial
    ) { effectiveAudioBrush(audioClip, AUDIO_TRACK_COLOR) }
    // Con override (esta pista forma parte de un grupo de "Multicolor"), el
    // tono sólido que le toca dentro del degradado repartido entre TODO el
    // grupo — mismo criterio y misma prioridad que `labelColumnBrush`/
    // `rowBodyBrush`/`effectiveTrackColorStrong` en TimelineRow: miniatura,
    // barra, panel y diálogo de una misma fila muestran siempre el mismo
    // tono. Sin override, exactamente lo de siempre.
    val trackColorStrong = labelColumnColorOverride ?: ownTrackColorStrong
    val trackBrush = remember(labelColumnColorOverride, ownTrackBrush) {
        labelColumnColorOverride?.let { SolidColor(it) } ?: ownTrackBrush
    }

    // --- Forma de onda del clip (estilo FL Studio / DAW profesional).
    // Se decodifica UNA vez por archivo fuera del hilo principal (con caché,
    // ver `AudioWaveformAnalyzer`) y mientras carga la barra muestra solo su
    // línea central. Se resetea a null al cambiar de archivo para no mostrar
    // por un instante la señal del audio anterior.
    val appContext = LocalContext.current.applicationContext
    val waveform by produceState<AudioWaveform?>(
        initialValue = null,
        audioClip.sourceUri,
        audioClip.sourceDurationMs
    ) {
        value = null
        value = AudioWaveformAnalyzer.load(appContext, audioClip.sourceUri, audioClip.sourceDurationMs)
    }
    // Color de la señal: el tono de identidad de la pista (incluido el
    // tono repartido de "Multicolor") aclarado hacia blanco — sobre el
    // fondo tenue de la barra la señal resalta como en FL, y sigue el
    // color que el usuario le puso a la pista.
    val waveformSignalColor = remember(trackColorStrong) { lerp(trackColorStrong, Color.White, 0.55f) }

    // --- Mini ventanas de renombrar/color, mismo patrón exacto que
    // `TimelineRow` (ver el comentario grande ahí sobre por qué viven acá,
    // por fila, en vez de subir como estado al padre). ---
    var showRenameDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var colorPickerResumeSnapshot by remember { mutableStateOf<ColorPickerSnapshot?>(null) }

    // Cuando llega un color tomado con el cuentagotas PARA ESTA PISTA,
    // mismo manejo exacto que `TimelineRow` — ver ese comentario para el
    // detalle completo del porqué.
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

    // Memoizado con `audioClip.orderLocked` como key (el único campo que
    // cambia el ícono/tinte de una de las acciones), mismo criterio
    // exacto que `layerActions` en TimelineRow con sus banderas.
    //
    // Mismo set genérico que `layerActions` en TimelineRow, MENOS el ojo
    // Y MENOS el candado de canvas (ver KDoc de esta función, punto 4,
    // para el porqué de ambos): el audio no se dibuja ni se manipula en
    // el canvas, así que ni "visibilidad" ni "bloquear movimiento en el
    // canvas" tienen nada que hacer acá. 4 acciones reales — ya no hay
    // ningún placeholder ni tinte apagado a propósito, las 4 se ven y se
    // comportan con la misma "potencia" visual que el panel de cualquier
    // capa de imagen.
    val audioActions = remember(audioClip.orderLocked) {
        listOf(
            LayerAction(R.drawable.ic_layer_rename, "Renombrar audio", { showRenameDialog = true }),
            LayerAction(
                R.drawable.ic_layer_color,
                "Cambiar color del audio",
                { showColorPickerDialog = true },
                tint = Color.Unspecified
            ),
            LayerAction(
                if (audioClip.orderLocked) R.drawable.ic_order_lock_closed else R.drawable.ic_order_lock_open,
                if (audioClip.orderLocked) "Desbloquear orden del audio" else "Bloquear orden del audio (no reordenar)",
                onToggleOrderLock,
                tint = if (audioClip.orderLocked) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.85f),
                iconSize = 20.dp
            ),
            LayerAction(R.drawable.ic_delete, "Eliminar audio del proyecto", onRemoveRequest, tint = Color(0xFFFF6B6B))
        )
    }
    // Mismo valor (0) que usa `TimelineRow` para su propio Popup — el
    // padding visual entre la columna y el panel ya lo da el propio
    // `Card` del panel (ver `RowActionIcon`/el `Row` de abajo), no hace
    // falta gap extra acá.
    val gapPx = 0

    // Duración real del clip DENTRO del proyecto: lo que queda del archivo
    // fuente después del recorte de inicio (`trimStartMs`), acotado a lo
    // que sobra de proyecto desde `timelineStartMs` en adelante — el
    // bloque nunca se dibuja más largo que el tramo de timeline que le
    // queda disponible.
    val clipDurationMs = remember(audioClip.sourceDurationMs, audioClip.trimStartMs) {
        (audioClip.sourceDurationMs - audioClip.trimStartMs).coerceAtLeast(0L)
    }

    // Desplazamiento en vivo durante el arrastre — se acumula en píxeles
    // mientras el dedo se mueve y SOLO se traduce a milisegundos (y se
    // reporta a `onTimelineStartChange`) al soltar. Evita bombardear al
    // ViewModel con un evento por cada píxel arrastrado; mismo criterio
    // que ya usa el reordenamiento vertical de capas (`dragOffsetPx`) en
    // `TimelineView`.
    var dragOffsetPx by remember(audioClip.timelineStartMs) { mutableStateOf(0f) }
    var isTimelineStartDragging by remember { mutableStateOf(false) }

    val baseStartPx = audioClip.timelineStartMs * pxPerMs
    val displayStartPx = (baseStartPx + dragOffsetPx).coerceIn(
        0f,
        (trackWidthPx - (clipDurationMs * pxPerMs)).coerceAtLeast(0f)
    )
    val clipWidthPx = (clipDurationMs * pxPerMs).coerceAtMost(
        (trackWidthPx - displayStartPx).coerceAtLeast(0f)
    )

    Column {
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AUDIO_TRACK_ROW_HEIGHT)
                .offset { IntOffset(0, visualOffsetPx.roundToInt()) }
                // BUG REAL corregido acá también (ver KDoc, "SEGUNDA
                // CORRECCIÓN REAL"): antes esto solo pintaba un color
                // PLANO fijo (AUDIO_TRACK_COLOR) y encima nada más que
                // "seleccionada sí/no" — un degradado elegido en el panel
                // de color jamás se veía acá. Ahora usa el mismo `trackBrush`
                // real (sólido o degradado) que el panel de acciones,
                // como wash suave de toda la fila — mismo criterio de
                // `rowBodyBrush` en TimelineRow.
                .background(brush = trackBrush, alpha = 0.30f)
                // Mientras se arrastra para reordenar, esta fila se eleva
                // por encima de las vecinas por las que va pasando (zIndex)
                // y se le da una sombra + fondo propio — MISMO tratamiento
                // visual que `TimelineRow` aplica a una capa de imagen
                // arrastrada, para que se sienta como una sola mecánica
                // consistente en toda la playlist, sin importar el tipo de
                // fila.
                .zIndex(if (isDragging) 10f else 0f)
                .then(
                    if (isDragging) {
                        Modifier
                            .shadow(elevation = 6.dp, shape = RectangleShape)
                            .background(SurfaceTintedElevated)
                    } else {
                        Modifier
                    }
                )
        ) {
            // --- Columna izquierda: ícono + nombre del archivo — mismo
            // ancho (`LABEL_COLUMN_WIDTH`) que la columna de miniatura de
            // las capas de imagen, para que la playlist se alinee prolija
            // en una sola grilla vertical.
            //
            // Dos toques, MISMO criterio exacto que la miniatura de
            // cualquier capa de imagen (ver el bloque equivalente en
            // TimelineRow): el primer toque solo SELECCIONA esta fila; con
            // la fila ya seleccionada, un toque más despliega el panel de
            // acciones "al costado" (punto 4 del KDoc de esta función).
            // Antes el `onClick = onSelect` vivía en la Row de AFUERA (la
            // fila entera) — se saca de ahí porque ahora ese toque tiene
            // que distinguir seleccionar de expandir, algo que una fila
            // entera no puede decidir por sí sola sin saber cuál de sus
            // columnas se tocó.
            //
            // Mantener presionada y arrastrar arriba/abajo reordena esta
            // fila dentro de la playlist — MISMO gesto (long-press +
            // arrastre) que usa la miniatura de cualquier capa de imagen
            // en `TimelineRow`, ver el punto 3 del KDoc de esta función.
            // Un toque normal y rápido sigue disparando el `clickable` de
            // arriba sin pisarse con el arrastre, porque
            // detectDragGesturesAfterLongPress exige el long-press antes
            // de tomar el gesto.
            Row(
                modifier = Modifier
                    .width(LABEL_COLUMN_WIDTH)
                    .fillMaxHeight()
                    .onGloballyPositioned { labelColumnRightPx = it.boundsInWindow().right.roundToInt() }
                    // BUG REAL corregido acá (ver KDoc): esta columna se
                    // quedaba Transparent y dejaba ver solo el wash suave
                    // de la fila por detrás — el color/degradado elegido
                    // se leía "lavado" en vez de tan saturado como el
                    // panel de opciones. Ahora usa el mismo `trackColorStrong`
                    // que el panel, con el blanco de selección dibujado
                    // ENCIMA (no en reemplazo) — mismo criterio exacto que
                    // la columna de miniatura en TimelineRow.
                    .background(trackColorStrong)
                    .background(if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .padding(horizontal = 6.dp)
                    .clickable {
                        // Modo Multicolor: el toque marca/desmarca esta pista
                        // para el color en conjunto (mismo criterio que la
                        // miniatura de una capa de imagen) en vez de
                        // seleccionar/expandir.
                        if (multiColorSelectActive) {
                            onToggleMultiColorSelect()
                        } else if (isSelected) {
                            onToggleExpand()
                        } else {
                            onSelect()
                        }
                    }
                    // BUG REAL corregido acá (ver KDoc, punto 3): ahora
                    // respeta el candado de ORDEN de la propia pista —
                    // mismo criterio exacto que `TimelineRow` (que gatea
                    // este mismo gesto con `!layer.orderLocked`). Con el
                    // candado puesto, el long-press simplemente no arma
                    // ningún detector de arrastre: la fila se puede seguir
                    // tocando (seleccionar/expandir) pero no reordenar.
                    .then(
                        if (audioClip.orderLocked) {
                            Modifier
                        } else {
                            Modifier.pointerInput(Unit) {
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
                    ),
                // Centrado como la miniatura de cualquier capa de imagen en
                // `TimelineRow` (columna de ícono, sin texto).
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Punto de selección de "Multicolor": mismo que la miniatura
                // de una capa de imagen (relleno si está marcada, solo
                // contorno si no), a la izquierda del ícono.
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
                // BUG REAL corregido (reportado con captura: "el ícono no se
                // ve"): antes era una nota monocromática con
                // `tint = trackColorStrong` dibujada sobre un fondo que es
                // EXACTAMENTE `trackColorStrong` (ver el `.background` de
                // esta misma Row) — mismo color sobre mismo color, o sea
                // invisible. Ahora es el ícono premium de audio (ver
                // `ic_audio_premium.xml`), con sus colores propios
                // (`Color.Unspecified` = sin tinte, mismo criterio que
                // `ic_layer_color`): se lee sobre CUALQUIER color de fondo
                // que el usuario le ponga a la pista. El NOMBRE ya no vive
                // en esta columna angosta (se cortaba como "Apocal...") sino
                // dentro de la barra del clip, como el nombre de archivo en
                // las capas de imagen.
                // Caja del alto de la fila, igual que la miniatura de una capa
                // de imagen: el ícono va centrado y el distintivo de candado
                // (abajo) se ancla a SU esquina inferior derecha.
                Box(
                    modifier = Modifier.size(AUDIO_TRACK_ROW_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_audio_premium),
                        contentDescription = "Audio",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )

                    // --- Mini-candado de orden sobre el ícono: BUG REAL
                    // corregido (reportado con captura: "en las capas de
                    // imagen, al activar el candado de orden aparece un
                    // distintivo en la miniatura; en audio no"). Mismo
                    // diseño exacto que en `TimelineRow` (chip oscuro
                    // semitransparente, esquina inferior derecha, ícono
                    // `ic_order_lock_closed` de 9dp en el mismo celeste
                    // 0xFF4FC3F7 que el ícono grande del panel): así el
                    // estado de bloqueo se ve DE UN VISTAZO sin abrir el
                    // panel, y el usuario asocia el candadito chico con el
                    // grande que lo prendió. Solo el de ORDEN: el candado de
                    // canvas no existe para audio (ver KDoc de `AudioClip`).
                    if (audioClip.orderLocked) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(1.5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 1.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_order_lock_closed),
                                contentDescription = "Orden bloqueado",
                                tint = Color(0xFF4FC3F7),
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }

            // --- Panel "al costado" — BUG REAL corregido acá, reportado
            // con captura de pantalla: el panel se abría pegado al borde
            // DERECHO de toda la pantalla en vez de al costado de esta
            // columna (borde izquierdo), como pasa en cualquier capa de
            // imagen. Causa real: un `Popup` de Compose sin ancla
            // explícita calcula su posición a partir de DÓNDE en el árbol
            // de composición queda ubicado — no alcanza con que sea
            // hermano de esta columna en la Row de AFUERA (que ocupa el
            // ancho COMPLETO de la pantalla, por eso terminaba pegado a
            // SU borde derecho); tiene que ser hijo de ESTA Row angosta
            // (`LABEL_COLUMN_WIDTH`, ~72dp), exactamente como vive el
            // Popup equivalente dentro de la columna de miniatura de
            // cualquier capa de imagen en `TimelineRow` — mismo lugar
            // exacto en el árbol, no solo mismo mecanismo. Por eso este
            // bloque quedó ACÁ ADENTRO en vez de después de que esta Row
            // cierra.
            //
            // `BesideAnchorPopupPositionProvider` calcula "pegado a su
            // borde derecho" correctamente) — no más abajo, no más arriba.
            // Este Popup "al costado" se suspende mientras
            // `isBottomPanelExpanded` (alguna de las tres pestañas de abajo
            // abierta) — mismo criterio que TimelineRow, para no
            // superponerse con el panel de esa pestaña. En ese modo las
            // acciones NO desaparecen: se acomodan al pie de esta misma
            // fila como acordeón ([LayerActionAccordion], ver el bloque
            // después de la Row de más abajo) — BUG REAL corregido
            // (reportado con capturas: "en la capa de audio no aparece
            // nada, el resto de capas sí"): antes esta fila no tenía ese
            // equivalente y con la pestaña abierta se quedaba sin ningún
            // acceso a sus acciones.
            if (showActionsPanel && !isBottomPanelExpanded) {
                Popup(
                    popupPositionProvider = BesideAnchorPopupPositionProvider(gapPx, labelColumnRightPx?.minus(playheadCoverBleedPx)),
                    onDismissRequest = onToggleExpand,
                    // dismissOnClickOutside = false por el mismo motivo
                    // documentado en TimelineRow: tocar la propia columna
                    // (que técnicamente queda "afuera" del contenido del
                    // Popup) no debe disparar un cierre-y-reapertura en el
                    // mismo toque — solo el `clickable` de la columna
                    // decide abrir/cerrar.
                    properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
                ) {
                    AnimatedVisibility(
                        visibleState = visibleState,
                        enter = fadeIn(tween(140)) +
                            scaleIn(tween(140), initialScale = 0.85f, transformOrigin = TransformOrigin(0f, 0.5f)),
                        exit = fadeOut(tween(110)) +
                            scaleOut(tween(110), targetScale = 0.85f, transformOrigin = TransformOrigin(0f, 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .shadow(elevation = 12.dp, shape = RectangleShape, clip = false)
                                // BUG REAL corregido acá (ver KDoc): antes
                                // pintaba SIEMPRE el color plano fijo
                                // AUDIO_TRACK_COLOR, sin importar qué haya
                                // elegido el usuario en "Cambiar color" —
                                // ahora usa el Brush real (sólido o
                                // degradado, ver `trackBrush`), mismo
                                // criterio que el panel de cualquier capa
                                // de imagen en TimelineRow.
                                .background(brush = trackBrush, shape = RectangleShape)
                                .border(
                                    width = 1.dp,
                                    color = trackColorStrong.copy(alpha = 0.5f),
                                    shape = RectangleShape
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                for (action in audioActions) {
                                    RowActionIcon(
                                        action.iconRes,
                                        action.contentDescription,
                                        action.onClick,
                                        tint = action.tint,
                                        iconSize = action.iconSize
                                    )
                                }
                            }
                        }
                    }
                }
            }
            } // cierra la Row de la columna label (ícono + nombre + Popup)

            // --- Carril de audio: el bloque de color (la "señal") se
            // arrastra horizontalmente dentro de este Box para fijar dónde
            // arranca a sonar — ver el comentario grande de la función.
            // Un toque simple (sin arrastre) en cualquier parte del
            // carril también SELECCIONA la fila — mismo criterio que el
            // carril de cualquier capa de imagen en TimelineRow (que
            // además hace seek; acá no aplica un seek propio del audio,
            // así que solo selecciona).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures { onSelect() }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(displayStartPx.roundToInt(), 0) }
                        .width(with(density) { clipWidthPx.toDp() })
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        // Antes AUDIO_TRACK_COLOR fijo — ahora refleja el
                        // color/degradado real elegido para esta pista
                        // (ver KDoc, "SEGUNDA CORRECCIÓN REAL").
                        // Base oscura bajo el tinte de la pista: da contraste a
                        // la señal (mismo recurso que el fondo de clip de FL).
                        .background(Color.Black.copy(alpha = 0.30f))
                        .background(brush = trackBrush, alpha = if (isTimelineStartDragging) 0.55f else 0.40f)
                        // Arrastre HORIZONTAL siempre activo: ya no hay
                        // ningún candado que lo bloquee — el candado de
                        // "canvas" (`locked`) se quitó por completo de
                        // audio a pedido explícito del usuario, ver el
                        // KDoc grande en `AudioClip.kt` y el punto 4 del
                        // KDoc de esta función. Mover dónde arranca a
                        // sonar la pista sobre el carril es edición de
                        // TIMELINE, no de canvas: se puede hacer siempre.
                        .pointerInput(audioClip.timelineStartMs, pxPerMs) {
                            detectDragGestures(
                                onDragStart = { isTimelineStartDragging = true },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetPx += dragAmount.x
                                },
                                onDragEnd = {
                                    isTimelineStartDragging = false
                                    if (pxPerMs > 0f) {
                                        // BUG REAL corregido acá: esto calculaba
                                        // `(baseStartPx + dragOffsetPx) / pxPerMs`
                                        // — el acumulado CRUDO del gesto, sin el
                                        // límite superior que sí aplica
                                        // `displayStartPx` (arriba, la que
                                        // efectivamente se ve en pantalla
                                        // mientras se arrastra). Si el dedo
                                        // seguía moviéndose a la derecha después
                                        // de que el bloque ya había tocado su
                                        // límite visual, `dragOffsetPx` seguía
                                        // acumulando de más sin que se viera en
                                        // el bloque — y al soltar, se confirmaba
                                        // esa posición de más, distinta a la que
                                        // el usuario realmente vio. Usar
                                        // `displayStartPx` (ya clampeado) como
                                        // única fuente de verdad garantiza que lo
                                        // que se confirma es EXACTAMENTE lo que
                                        // se vio en pantalla al soltar, sin
                                        // sorpresas.
                                        val newStartMs = (displayStartPx / pxPerMs)
                                            .roundToInt()
                                            .toLong()
                                            .coerceAtLeast(0L)
                                        onTimelineStartChange(newStartMs)
                                    }
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    isTimelineStartDragging = false
                                    dragOffsetPx = 0f
                                }
                            )
                        },
                    contentAlignment = Alignment.TopStart
                ) {
                    // Forma de onda a todo el alto de la barra, al fondo.
                    // Sin ningún handler de puntero: el arrastre horizontal
                    // del Box padre sigue funcionando sobre la señal.
                    AudioWaveformCanvas(
                        waveform = waveform,
                        trimStartMs = audioClip.trimStartMs,
                        pxPerMs = pxPerMs,
                        signalColor = waveformSignalColor,
                        dimmed = audioClip.muted,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Nombre del audio DENTRO de la barra, arriba a la
                    // izquierda, como FL Studio y como el nombre de archivo
                    // de las capas de imagen. Chico y con sombra sutil para
                    // leerse encima de la señal sin tapar su forma. Una sola
                    // línea con elipsis: nunca desborda aunque el clip sea corto.
                    Text(
                        text = audioClip.displayName,
                        color = waveformSignalColor,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.65f),
                                offset = Offset(0f, 1f),
                                blurRadius = 3f
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp, top = 1.dp, end = 6.dp)
                    )
                }
            }

            // --- Botón de renombrar externo — se mantiene en su mismo
            // lugar fijo (el usuario pidió expresamente NO removerlo en
            // una pasada anterior, ver historial), pero ya no es
            // decorativo: ahora abre el MISMO diálogo real que la acción
            // "Renombrar" del panel de arriba (mismo `showRenameDialog`,
            // sin estado duplicado) — dos atajos distintos al mismo
            // resultado, en vez de un botón que "se ve activo pero no
            // hace nada" (justo el bug reportado).
            IconButton(
                onClick = { showRenameDialog = true },
                modifier = Modifier.fillMaxHeight().width(32.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_layer_rename),
                    contentDescription = "Renombrar audio",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // --- Acordeón "de abajo": mismo mecanismo exacto que TimelineRow
        // (ver ahí y el KDoc de [LayerActionAccordion]). Es HERMANO de la
        // Row de arriba dentro de esta Column — a propósito NO un Popup:
        // un Popup flota y no empuja nada, mientras que contenido real de
        // la Column hace que la fila siguiente se corra hacia abajo sola,
        // sin taparla. Ancho atado a LABEL_COLUMN_WIDTH para no invadir
        // el panel de la pestaña abierta justo a la derecha de la columna.
        // Reusa `visibleState` (la misma animación de entrada/salida que
        // el panel "al costado") y `audioActions` (la MISMA lista de
        // acciones reales: el acordeón las recorre de a una con sus
        // flechitas). `layerVisible = true`: el audio no tiene ojo, así
        // que nunca se atenúa como una capa de imagen oculta.
        if (showActionsPanel && isBottomPanelExpanded) {
            LayerActionAccordion(
                visibleState = visibleState,
                trackBrush = SolidColor(trackColorStrong),
                borderColor = trackColorStrong,
                layerVisible = true,
                actions = audioActions
            )
        }
    }

    if (showRenameDialog) {
        RenameLayerDialog(
            initialName = audioClip.displayName,
            accentColor = trackColorStrong,
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
            // la pista — mismo criterio exacto que TimelineRow.
            initialColorArgb = snap?.solidArgb ?: audioClip.customColorArgb,
            initialGradientStartArgb = snap?.gradientAArgb ?: audioClip.customGradientStartArgb,
            initialGradientEndArgb = snap?.gradientBArgb ?: audioClip.customGradientEndArgb,
            initialUseGradient = snap?.gradientEnabled ?: audioClip.useGradientColor,
            initialGradientAngleDegrees = snap?.gradientAngleDegrees ?: audioClip.gradientAngleDegrees,
            initialGradientIsRadial = snap?.gradientIsRadial ?: audioClip.gradientIsRadial,
            initialBlackAndWhiteMode = snap?.blackAndWhiteMode ?: audioClip.useBlackAndWhiteMode,
            initialActiveSlot = snap?.activeSlot,
            fallbackColorArgb = AUDIO_TRACK_COLOR.toArgb(),
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
                // Mismo mecanismo exacto que TimelineRow: guarda todo lo
                // armado hasta ahora, cierra ESTE diálogo (tiene que
                // desaparecer del todo para poder tocar el preview, que
                // vive detrás en otra ventana) y avisa hacia arriba que
                // ESTA pista quiere un color del cuentagotas.
                colorPickerResumeSnapshot = snapshot
                showColorPickerDialog = false
                onRequestEyedropper()
            }
        )
    }
}
