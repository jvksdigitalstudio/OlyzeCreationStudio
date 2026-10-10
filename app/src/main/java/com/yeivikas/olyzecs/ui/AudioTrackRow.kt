package com.yeivikas.olyzecs.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.TimeRangeOp
import com.yeivikas.olyzecs.engine.audio.MIN_GRID_BPM
import com.yeivikas.olyzecs.engine.audio.MAX_GRID_BPM
import com.yeivikas.olyzecs.engine.audio.AudioTimeSelection
import com.yeivikas.olyzecs.engine.audio.AudioGridStep
import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.audio.AudioWaveform
import com.yeivikas.olyzecs.engine.audio.AudioWaveformAnalyzer
import com.yeivikas.olyzecs.engine.audio.audibleLengthMs
import com.yeivikas.olyzecs.engine.audio.audioSnapTargetsMs
import com.yeivikas.olyzecs.engine.audio.clampClipLengthMs
import com.yeivikas.olyzecs.engine.audio.leftEdgeDeltaRangeMs
import com.yeivikas.olyzecs.engine.audio.effectiveFadesMs
import com.yeivikas.olyzecs.engine.audio.hasClipAtMs
import com.yeivikas.olyzecs.engine.audio.snapMovedClipStartMs
import com.yeivikas.olyzecs.engine.audio.snapNewClipStartMs
import com.yeivikas.olyzecs.engine.audio.snapToTargetsMs
import com.yeivikas.olyzecs.engine.audio.splitAtTimelineMs
import com.yeivikas.olyzecs.engine.audio.withLeftEdgeMovedMs
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import com.yeivikas.olyzecs.ui.theme.effectiveAudioBrush
import com.yeivikas.olyzecs.ui.theme.effectiveAudioColorStrong
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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
 * Orden de apilado (zIndex) dentro del carril de audio. Compose dibuja primero
 * por zIndex y, a igualdad, por orden de declaración; por eso el panel de
 * opciones —declarado al final— quedaba DETRÁS del clip seleccionado.
 * Clips 0f, clip seleccionado 1f, selección de tiempo 2f, panel de opciones 3f.
 * (El botón "+Clip" ya no está en la fila: ver [AudioAddClipOverlay].)
 */
private const val AUDIO_ACTIONS_PANEL_Z = 3f

/**
 * Fila de UN carril de audio del proyecto dentro de la playlist de capas
 * del timeline (`TimelineView`; el proyecto puede tener varios carriles). A PEDIDO EXPLÍCITO DEL USUARIO — tres
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
 *    un toque más despliega el panel — un panel dibujado dentro del
 *    carril de la propia fila ([RowSideActionsPanelHost], igual que en
 *    `TimelineRow`), pegado al borde derecho de esa columna y sincronizado
 *    con el scroll.
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
 * 5. Nombre del audio en la cabecera, en loop (pedido explícito, con
 *    capturas: con una pestaña inferior abierta el usuario solo veía el
 *    ícono de nota y no sabía qué audio era la capa). Cuando el panel de
 *    opciones está al PIE de la capa Y alguna pestaña inferior
 *    (Control/Módulos/Keyframes) está abierta, la cabecera anima
 *    ícono + nombre hacia la izquierda en bucle continuo
 *    ([AudioLabelMarquee]). Con la pestaña cerrada, o con el panel de
 *    opciones cerrado/al costado, la cabecera es el ícono estático de
 *    siempre. Regla en [shouldMarqueeAudioLabel] (testeada).
 * 6. Cargar otro clip en ESTA capa, sin crear una capa nueva (mecánica de la
 *    playlist de FL Studio Mobile), tocando un espacio VACÍO del carril:
 *     - un toque simple muestra, AL INSTANTE, el botón "+Clip" ([AudioAddClipOverlay]) en el punto
 *       tocado (con imán a cursor, bordes de clips y rejilla); al pulsarlo se
 *       abre el selector y el clip arranca en ESE punto;
 *     - doble toque abre el selector directamente y el clip arranca en el
 *       CURSOR (el "+Clip" del primer toque se retira): primero se ubica el cursor del timeline y luego se da el
 *       doble toque.
 *    "+Clip" se puede mantener y ARRASTRAR para reubicarlo (por su carril o a
 *    otro carril de audio; ver [AudioAddClipOverlay]); al soltarlo queda fijo y
 *    un toque abre el selector. Vive en una capa de `TimelineView`; esta fila
 *    solo lo propone y reporta los límites de su carril.
 *    En todos los casos el selector se abre vía [AudioClipEditActions.onImportClipAt].
 *    El "+" del menú de la playlist queda solo para capas nuevas.
 */
@Composable
internal fun AudioTrackRow(
    // FASE 1 (carril con varios clips, ver AudioClip.kt): antes recibía
    // un único `audioClip: AudioClip` — ahora recibe el CARRIL completo,
    // y dibuja un bloque arrastrable por cada elemento de
    // `audioTrack.clips` (ver `AudioClipBlock`, al final de este
    // archivo). El color/orden/nombre-de-archivo externo y el panel de
    // acciones operan sobre la identidad del CARRIL o, cuando hace falta
    // un clip puntual (renombrar), sobre el PRIMERO — mismo criterio de
    // simplificación que ya usa la EliNer API pública (ver
    // `ActiveProjectReader.getAudioClip`), consistente en todo el
    // proyecto para esta fase.
    audioTrack: AudioTrack,
    isSelected: Boolean,
    trackWidthPx: Float,
    projectDurationMs: Long,
    onSelect: () -> Unit,
    // Por-clip: el llamador (`TimelineView`) ya sabe a qué `clipId`
    // corresponde cada bloque — ver `AudioClipBlock`, que recibe este
    // callback ya "cerrado" sobre su propio clip.
    onTimelineStartChange: (clipId: String, newStartMs: Long) -> Unit,
    onRemoveRequest: () -> Unit,
    // Edición de clips dentro del carril (estirar bordes, copiar/
    // duplicar/eliminar/pegar) — ver [AudioClipEditActions].
    clipActions: AudioClipEditActions = AudioClipEditActions(),
    // Doble toque sobre un clip (en su cuerpo, donde está la señal): abre su ventana ampliada.
    onOpenClipDetail: (clipId: String) -> Unit = {},
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
    // mismo `expandedLayerIds` (con `AudioTrack.id`) que ya
    // usa para cualquier capa de imagen, así que esta fila no necesita su
    // propio Set aparte. `isBottomPanelExpanded`: cuando alguna de las
    // tres pestañas de abajo (Keyframes/Control/Módulos) está abierta, el
    // panel no se abre "al costado" (quedaría tapado por el panel de esa
    // pestaña) sino como acordeón al pie ([LayerActionAccordion]) — mismo
    // criterio que `TimelineRow`.
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    isBottomPanelExpanded: Boolean = false,
    // Mismo contrato que en TimelineRow ([RowActionsPanelPlacement]).
    visibleBand: ListVisibleBand? = null,
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
    // --- "+Clip" (ver KDoc, punto 6): el botón NO vive en la fila, vive en una
    // capa superpuesta de `TimelineView` para poder arrastrarse entre carriles.
    // La fila solo conoce si el punto elegido está en SU carril, lo propone
    // con un toque y reporta los límites de su carril en pantalla.
    /** `true` si el punto de "+Clip" está en ESTE carril. Se consulta bajo demanda (no se lee al componer). */
    ownsAddClipAnchor: () -> Boolean = { false },
    /** Un toque propone el punto (ms) de "+Clip" en este carril; `null` lo retira. */
    onAddClipAnchorChange: (anchorMs: Long?) -> Unit = {},
    /** Límites del carril (sin la columna de cabecera) en coordenadas de ventana. */
    onLaneBoundsChange: (Rect) -> Unit = {},
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
    val playheadCoverBleedPx = rememberPlayheadCoverBleedPx()
    val visibleState = remember { MutableTransitionState(false) }
    val placement = rememberRowActionsPanelPlacement(isExpanded, visibleBand, visibleState)
    val showActionsPanel = visibleState.currentState || visibleState.targetState
    val useFootPanel = isBottomPanelExpanded || (showActionsPanel && placement.footLatched)

    // --- Cabecera con nombre en loop (punto 5 del KDoc de esta función) ---
    // SOLO cuando el panel de opciones está al PIE de la capa (acordeón) Y
    // hay una pestaña inferior (Control/Módulos/Keyframes) abierta — regla
    // única y testeada en [shouldMarqueeAudioLabel]. Fuera de ese estado la
    // cabecera es el ícono estático de siempre.
    val labelMarqueeActive = shouldMarqueeAudioLabel(
        footAccordionVisible = showActionsPanel && useFootPanel,
        bottomPanelExpanded = isBottomPanelExpanded
    )
    val audioLabelName = audioTrack.clips.firstOrNull()?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: AUDIO_LABEL_FALLBACK_NAME

    // --- Color/degradado de identidad REAL de esta pista — mismo patrón
    // exacto que `trackColor`/`trackColorStrong`/`trackBrush` en
    // `TimelineRow` (TimelineView.kt), aplicado acá vía
    // [effectiveAudioColor]/[effectiveAudioBrush] (Theme.kt): si el
    // usuario personalizó un color sólido o un degradado desde el panel
    // de acciones, la fila entera lo refleja de verdad; si no, cae al
    // "de fábrica" [AUDIO_TRACK_COLOR] — exactamente como se veía antes
    // de esta mejora.
    val ownTrackColorStrong = remember(
        audioTrack.customColorArgb, audioTrack.useGradientColor,
        audioTrack.customGradientStartArgb, audioTrack.customGradientEndArgb
    ) { effectiveAudioColorStrong(audioTrack, AUDIO_TRACK_COLOR) }
    val ownTrackBrush = remember(
        audioTrack.customColorArgb, audioTrack.useGradientColor,
        audioTrack.customGradientStartArgb, audioTrack.customGradientEndArgb,
        audioTrack.gradientAngleDegrees, audioTrack.gradientIsRadial
    ) { effectiveAudioBrush(audioTrack, AUDIO_TRACK_COLOR) }
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

    // --- Forma de onda de CADA clip (estilo FL Studio / DAW profesional)
    // — FASE 1: ya no se decodifica acá a nivel de fila (solo podía haber
    // un clip); ahora vive en `AudioClipBlock`, una vez por cada elemento
    // de `audioTrack.clips`, cada uno con su propio `produceState` (con
    // caché compartida, ver `AudioWaveformAnalyzer`).
    // Color de la señal: el tono de identidad de la pista (incluido el
    // tono repartido de "Multicolor") aclarado hacia blanco — sobre el
    // fondo tenue de la barra la señal resalta como en FL, y sigue el
    // color que el usuario le puso a la pista.
    val waveformSignalColor = remember(trackColorStrong) { lerp(trackColorStrong, Color.White, 0.55f) }

    // --- Mini ventanas de renombrar/color, mismo patrón exacto que
    // `TimelineRow` (ver el comentario grande ahí sobre por qué viven acá,
    // por fila, en vez de subir como estado al padre). ---
    var showRenameDialog by remember { mutableStateOf(false) }
    // Menú "Pegar en el cursor" del carril vacío (mantener presionado).
    var showLaneMenu by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Punto del timeline (ms) donde se ofrece "+Clip", o null si no se muestra.
    // En ms y no en px: sobrevive a un cambio de zoom/duración del proyecto.
    val currentOwnsAddClipAnchor by rememberUpdatedState(ownsAddClipAnchor)
    val currentOnAddClipAnchorChange by rememberUpdatedState(onAddClipAnchorChange)
    // El `pointerInput` del carril se arma una sola vez por sus keys, pero el
    // cursor, la rejilla y la duración cambian todo el tiempo: se leen siempre
    // frescos a través de estas referencias.
    val currentClipActions by rememberUpdatedState(clipActions)
    val currentProjectDurationMs by rememberUpdatedState(projectDurationMs)
    val snapThresholdPx = with(LocalDensity.current) { SNAP_THRESHOLD.toPx() }
    // "+Clip" solo tiene sentido mientras el carril siga igual de vacío en ese
    // punto, ningún clip esté seleccionado y esta capa siga seleccionada. Cada
    // efecto retira el botón solo ante el cambio que lo invalida: tocar el
    // carril vacío selecciona la capa y DESELECCIONA el clip, y ese cambio no
    // debe borrar el botón que ese mismo toque acaba de mostrar.
    // Solo el carril que POSEE el punto lo retira (si no, cada fila borraría el
    // punto de otro carril al componerse).
    LaunchedEffect(audioTrack.clips) { if (currentOwnsAddClipAnchor()) currentOnAddClipAnchorChange(null) }
    LaunchedEffect(clipActions.selectedClipId) {
        if (clipActions.selectedClipId != null && currentOwnsAddClipAnchor()) currentOnAddClipAnchorChange(null)
    }
    LaunchedEffect(isSelected) { if (!isSelected && currentOwnsAddClipAnchor()) currentOnAddClipAnchorChange(null) }
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

    // Memoizado con `audioTrack.orderLocked` como key (el único campo que
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
    val audioActions = remember(audioTrack.orderLocked) {
        listOf(
            LayerAction(R.drawable.ic_layer_rename, "Renombrar audio", { showRenameDialog = true }),
            LayerAction(
                R.drawable.ic_layer_color,
                "Cambiar color del audio",
                { showColorPickerDialog = true },
                tint = Color.Unspecified
            ),
            LayerAction(
                if (audioTrack.orderLocked) R.drawable.ic_order_lock_closed else R.drawable.ic_order_lock_open,
                if (audioTrack.orderLocked) "Desbloquear orden del audio" else "Bloquear orden del audio (no reordenar)",
                onToggleOrderLock,
                tint = if (audioTrack.orderLocked) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.85f),
                iconSize = 20.dp
            ),
            LayerAction(R.drawable.ic_delete, "Eliminar audio del proyecto", onRemoveRequest, tint = Color(0xFFFF6B6B))
        )
    }
    // FASE 1: la posición/ancho/arrastre de cada clip (antes calculados
    // acá, a nivel de fila, porque solo podía haber uno) ahora viven
    // dentro de `AudioClipBlock` — un `remember`/estado de arrastre por
    // cada clip, independiente del resto.

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
                    .onGloballyPositioned {
                        // positionInWindow: posición REAL, ver TimelineRow.
                        val top = it.positionInWindow().y.roundToInt()
                        placement.onRowPositioned(top, top + it.size.height, visibleBand)
                    }
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
                        if (audioTrack.orderLocked) {
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
                // que el usuario le ponga a la pista. El NOMBRE estático vive
                // dentro de la barra del clip (en esta columna angosta se
                // cortaba como "Apocal..."); solo con el panel de opciones al
                // pie y una pestaña inferior abierta se muestra TAMBIÉN acá,
                // en loop, vía [AudioLabelMarquee] (ver más abajo).
                // Caja del alto de la fila, igual que la miniatura de una capa
                // de imagen: el ícono va centrado y el distintivo de candado
                // (abajo) se ancla a SU esquina inferior derecha.
                //
                // Con el panel de opciones al PIE y una pestaña inferior
                // abierta ([labelMarqueeActive]) la cabecera pasa a mostrar
                // ícono + nombre en loop hacia la izquierda
                // ([AudioLabelMarquee]); el resto del tiempo, el ícono
                // estático de siempre. El distintivo de candado de orden
                // queda fijo en ambos modos.
                if (labelMarqueeActive) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(AUDIO_TRACK_ROW_HEIGHT)
                    ) {
                        AudioLabelMarquee(
                            name = audioLabelName,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (audioTrack.orderLocked) {
                            AudioOrderLockBadge(modifier = Modifier.align(Alignment.BottomEnd))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.size(AUDIO_TRACK_ROW_HEIGHT),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_audio_premium),
                            contentDescription = "Audio",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(AUDIO_LABEL_ICON_SIZE)
                        )
                        if (audioTrack.orderLocked) {
                            AudioOrderLockBadge(modifier = Modifier.align(Alignment.BottomEnd))
                        }
                    }
                }

            // El panel "al costado" ya NO vive aquí: se dibuja dentro del carril de
            // esta misma fila (ver más abajo, [RowSideActionsPanelHost]), así scrollea
            // exactamente con ella. Mientras `isBottomPanelExpanded` (alguna de las
            // tres pestañas de abajo abierta) las acciones se acomodan al pie como
            // acordeón ([LayerActionAccordion]) para no pisar el panel de esa pestaña.
            } // cierra la Row de la columna label (ícono + nombre)

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
                    .onGloballyPositioned { onLaneBoundsChange(it.boundsInWindow()) }
                    .pointerInput(audioTrack.clips, pxPerMs) {
                        // Solo el carril VACÍO (no un clip: cada clip tiene sus
                        // propios gestos) ofrece cargar o pegar.
                        fun isEmptyLaneAt(xPx: Float): Boolean {
                            val ms = laneTimeMsAt(xPx, pxPerMs, currentProjectDurationMs) ?: return false
                            return !audioTrack.hasClipAtMs(ms)
                        }
                        // Toque simple AL INSTANTE (sin esperar la ventana del
                        // doble toque): ver `detectImmediateTapGestures`.
                        detectImmediateTapGestures(
                            // Un toque selecciona la capa y, sobre carril vacío,
                            // ofrece "+Clip" en ESE punto (con imán).
                            onTap = { offset ->
                                currentOnSelect()
                                currentOnAddClipAnchorChange(if (isEmptyLaneAt(offset.x)) {
                                    laneTimeMsAt(offset.x, pxPerMs, currentProjectDurationMs)?.let { rawMs ->
                                        audioTrack.snapNewClipStartMs(
                                            rawMs = rawMs,
                                            projectDurationMs = currentProjectDurationMs,
                                            playheadMs = currentClipActions.playheadMs,
                                            thresholdMs = (snapThresholdPx / pxPerMs).toLong(),
                                            gridMs = currentClipActions.gridMs
                                        )
                                    }
                                } else {
                                    null
                                })
                            },
                            // Doble toque en carril vacío: carga un audio nuevo
                            // en el CURSOR (que el usuario ubicó antes). El primer
                            // toque ya mostró "+Clip": se retira antes de abrir.
                            onDoubleTap = { offset ->
                                if (isEmptyLaneAt(offset.x)) {
                                    currentOnAddClipAnchorChange(null)
                                    currentClipActions.onImportClipAt(currentClipActions.playheadMs)
                                }
                            },
                            // Mantener presionado sobre carril vacío ofrece pegar.
                            onLongPress = { offset ->
                                if (isEmptyLaneAt(offset.x)) {
                                    currentOnAddClipAnchorChange(null)
                                    showLaneMenu = true
                                }
                            }
                        )
                    }
            ) {
                if (showLaneMenu) {
                    ClipPopupMenu(
                        items = listOf(
                            ClipMenuItem("Pegar en el cursor", enabled = clipActions.hasClipboard) { clipActions.onPasteAtPlayhead() },
                            ClipMenuItem("Seleccionar rango en el cursor") { clipActions.onSelectRangeAtPlayhead() },
                            ClipMenuItem("Rejilla…") { showGridDialog = true }
                        ),
                        onDismiss = { showLaneMenu = false }
                    )
                }
                if (showGridDialog) {
                    AudioGridDialog(
                        bpm = clipActions.gridBpm,
                        step = clipActions.gridStep,
                        onConfirm = { bpm, step -> clipActions.onGridChange(bpm, step) },
                        onDismiss = { showGridDialog = false }
                    )
                }
                // Líneas de la rejilla musical, DETRÁS de los clips.
                if (clipActions.gridMs > 0.0) {
                    AudioGridLines(
                        stepMs = clipActions.gridMs,
                        barMs = 60_000.0 / clipActions.gridBpm.toDouble().coerceAtLeast(1.0) * 4.0,
                        pxPerMs = pxPerMs,
                        durationMs = projectDurationMs,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // FASE 1 (carril con varios clips, ver AudioClip.kt): un
                // bloque arrastrable por CADA clip del carril, no uno solo
                // — `key(clip.id)` para que el estado de arrastre/forma de
                // onda de cada `AudioClipBlock` (`remember`, `produceState`)
                // quede atado a SU clip y no se mezcle con el de otro si la
                // lista cambia de orden o de tamaño entre recomposiciones.
                for (clip in audioTrack.clips) {
                    key(clip.id) {
                        AudioClipBlock(
                            clip = clip,
                            trackWidthPx = trackWidthPx,
                            pxPerMs = pxPerMs,
                            projectDurationMs = projectDurationMs,
                            trackBrush = trackBrush,
                            trackColorStrong = trackColorStrong,
                            waveformSignalColor = waveformSignalColor,
                            isTrackSelected = isSelected,
                            actions = clipActions,
                            // Objetivos del imán: inicio del proyecto, su final, el
                            // cursor y los bordes de los DEMÁS clips del carril.
                            snapTargetsMs = audioTrack.audioSnapTargetsMs(
                                projectDurationMs, clipActions.playheadMs, excludeClipId = clip.id
                            ),
                            onSelect = onSelect,
                            onOpenDetail = { onOpenClipDetail(clip.id) },
                            onTimelineStartChange = { newStartMs -> onTimelineStartChange(clip.id, newStartMs) }
                        )
                    }
                }
                // Selección de tiempo: ENCIMA de los clips (mientras exista, su
                // zona captura los toques; se quita desde su propio menú).
                clipActions.timeSelection?.let { selection ->
                    // zIndex 2f: por encima incluso del clip seleccionado (zIndex 1f).
                    Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                        AudioTimeSelectionOverlay(
                            selection = selection,
                            pxPerMs = pxPerMs,
                            snapTargetsMs = audioTrack.audioSnapTargetsMs(projectDurationMs, clipActions.playheadMs),
                            gridMs = clipActions.gridMs,
                            onChange = clipActions.onTimeSelectionChange,
                            onOp = clipActions.onTimeRangeOp,
                            onClear = clipActions.onClearTimeSelection
                        )
                    }
                }
            
                // --- Panel de opciones "al costado": DENTRO de la fila (no en una ventana
                // aparte), para que scrollee con ella sin desfase. Ver [RowSideActionsPanelHost].
                // Estar al final del Box NO basta para quedar encima: Compose ordena primero por
                // zIndex, y el clip seleccionado (1f) y la selección de tiempo (2f) lo tapaban.
                // 3f = por encima de todo lo que lleva zIndex dentro del carril.
                if (showActionsPanel && !useFootPanel) {
                    RowSideActionsPanelHost(
                        visibleState = visibleState,
                        bleedPx = playheadCoverBleedPx,
                        modifier = Modifier.zIndex(AUDIO_ACTIONS_PANEL_Z)
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
        if (showActionsPanel && useFootPanel) {
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
            // Simplificación explícita (ver KDoc de la función, igual criterio
            // que la EliNer API): renombra el PRIMER clip del carril. Con
            // un solo clip (el único caso posible hasta que exista
            // Copiar/Pegar) es, lisa y llanamente, "renombrar el audio".
            initialName = audioTrack.clips.firstOrNull()?.displayName.orEmpty(),
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
            initialColorArgb = snap?.solidArgb ?: audioTrack.customColorArgb,
            initialGradientStartArgb = snap?.gradientAArgb ?: audioTrack.customGradientStartArgb,
            initialGradientEndArgb = snap?.gradientBArgb ?: audioTrack.customGradientEndArgb,
            initialUseGradient = snap?.gradientEnabled ?: audioTrack.useGradientColor,
            initialGradientAngleDegrees = snap?.gradientAngleDegrees ?: audioTrack.gradientAngleDegrees,
            initialGradientIsRadial = snap?.gradientIsRadial ?: audioTrack.gradientIsRadial,
            initialBlackAndWhiteMode = snap?.blackAndWhiteMode ?: audioTrack.useBlackAndWhiteMode,
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

/**
 * Mini-candado de orden sobre la cabecera de la pista (BUG REAL corregido,
 * reportado con captura: "en las capas de imagen, al activar el candado de
 * orden aparece un distintivo en la miniatura; en audio no"). Mismo diseño
 * exacto que en `TimelineRow`: chip oscuro semitransparente, esquina inferior
 * derecha, ícono `ic_order_lock_closed` de 9dp en el mismo celeste 0xFF4FC3F7
 * que el ícono grande del panel — el estado de bloqueo se ve DE UN VISTAZO
 * sin abrir el panel. Solo el de ORDEN: el candado de canvas no existe para
 * audio (ver KDoc de `AudioClip`).
 *
 * Extraído a su propia función para compartirlo entre la cabecera estática y
 * la cabecera en loop ([AudioLabelMarquee]) sin duplicar el dibujo.
 */
@Composable
private fun AudioOrderLockBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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

/** Alto de la franja con el nombre del clip — cabecera al estilo de la playlist de FL Studio Mobile. */
private val CLIP_HEADER_HEIGHT = 12.dp

/**
 * Alto de la zona táctil de ARRASTRE del clip: la cabecera visible
 * ([CLIP_HEADER_HEIGHT]) más un pequeño margen del cuerpo, para que agarrarla
 * con el dedo no exija atinarle a una franja de 12 dp. Cubre TODO el ancho del
 * clip. Es la única zona que mueve el clip; el resto del cuerpo (la señal)
 * abre el menú con un toque largo.
 */
private val CLIP_DRAG_ZONE_HEIGHT = 16.dp

/** Grosor de la línea de loop (estilo FL Studio Mobile: gruesa pero discreta). */
private val LOOP_HANDLE_BAR_WIDTH = 5.dp

/** Separación entre la línea de loop y el borde del clip (casi pegada). */
private val LOOP_HANDLE_GAP = 2.dp

/** Ancho de la zona táctil de cada línea de loop; va TODA por fuera del clip, así no le quita área a la cabecera. */
private val LOOP_HANDLE_TOUCH_WIDTH = 24.dp

/** Distancia (en pantalla) a la que un borde del clip se "pega" a un objetivo del imán. */
internal val SNAP_THRESHOLD = 8.dp

/** Tope de repeticiones del archivo que se ofrecen como objetivo del imán al estirar con loop. */
private const val MAX_LOOP_SNAP_TARGETS = 64

/**
 * Acciones de edición de CLIPS dentro del carril de audio — mecánica de la
 * playlist de FL Studio Mobile:
 *  - estirar/acortar los bordes del clip SOLO si tiene loop activo y está
 *    seleccionado ([onLengthChange] el derecho, [onLeftEdgeChange] el
 *    izquierdo; repiten el archivo en loop). Sin loop el clip conserva su
 *    largo original;
 *  - seleccionar un clip ([selectedClipId] / [onSelectClip]): la selección es
 *    por CLIP, no por carril;
 *  - doble toque sobre un clip: ventana ampliada de su forma de onda (parámetro `onOpenClipDetail` de [AudioTrackRow]);
 *  - mantener presionado un clip: [onCopy] / [onCut] / [onDuplicate] /
 *    [onSplit] / [onMergeWithNext] / [onDelete];
 *  - mantener presionado el carril vacío: pegar ([onPasteAtPlayhead], si
 *    [hasClipboard]), crear una selección de tiempo ([onSelectRangeAtPlayhead])
 *    y abrir los ajustes de rejilla;
 *  - tocar el carril vacío: botón "+Clip"; doble toque: cargar un audio en el
 *    cursor — ambos vía [onImportClipAt];
 *  - selección de tiempo: dos manijas con imán ([timeSelection]) y un menú con
 *    [TimeRangeOp] (insertar espacio, duplicar, borrar, borrar espacio, recortar).
 *
 * Los valores que llegan a [onLengthChange]/[onLeftEdgeChange] son PEDIDOS
 * en bruto: el `EditorViewModel` los acota con las mismas reglas
 * (`clampClipLengthMs`/`withLeftEdgeMovedMs`) que usa la vista previa del
 * arrastre.
 */
class AudioClipEditActions(
    val onLengthChange: (clipId: String, newLengthMs: Long) -> Unit = { _, _ -> },
    /** Mueve el borde izquierdo (positivo = acorta, negativo = estira en loop). Solo con loop. */
    val onLeftEdgeChange: (clipId: String, deltaMs: Long) -> Unit = { _, _ -> },
    /** Clip seleccionado dentro del carril (`null` = ninguno). Los tiradores de loop solo existen en el seleccionado. */
    val selectedClipId: String? = null,
    val onSelectClip: (clipId: String) -> Unit = {},
    val onCopy: (clipId: String) -> Unit = {},
    val onCut: (clipId: String) -> Unit = {},
    val onDuplicate: (clipId: String) -> Unit = {},
    val onDelete: (clipId: String) -> Unit = {},
    val onPasteAtPlayhead: () -> Unit = {},
    /** Abre el selector de archivos para cargar un clip NUEVO en este carril, arrancando en [startMs]. */
    val onImportClipAt: (startMs: Long) -> Unit = {},
    val onSplit: (clipId: String) -> Unit = {},
    /** Une el clip con su continuación exacta (inverso de [onSplit]). */
    val onMergeWithNext: (clipId: String) -> Unit = {},
    /** `true` si el clip tiene una continuación exacta con la que se puede unir. */
    val canMergeWithNext: (clipId: String) -> Boolean = { false },
    /** Invierte el audio del clip (Reverse) o lo restaura. */
    val onToggleReverse: (clipId: String) -> Unit = {},
    /** Normaliza el clip a -1 dBFS (o quita la normalización si ya la tiene). */
    val onToggleNormalize: (clipId: String) -> Unit = {},
    val onToggleMute: (clipId: String) -> Unit = {},
    val hasClipboard: Boolean = false,
    /** Cursor de reproducción (ms): objetivo del imán y punto de corte de "Dividir en el cursor". */
    val playheadMs: Long = 0L,
    // --- Rejilla musical del carril ---
    /** Paso de la rejilla en ms (0.0 = sin rejilla): se suma al imán de clips y selecciones. */
    val gridMs: Double = 0.0,
    val gridBpm: Float = 120f,
    val gridStep: AudioGridStep = AudioGridStep.OFF,
    val onGridChange: (bpm: Float, step: AudioGridStep) -> Unit = { _, _ -> },
    // --- Selección de tiempo (FL Studio Mobile: Playlist > Time selection) ---
    val timeSelection: AudioTimeSelection? = null,
    /** Selecciona exactamente el rango de un clip. */
    val onSelectClipRange: (clipId: String) -> Unit = {},
    /** Crea una selección de unos segundos desde el cursor. */
    val onSelectRangeAtPlayhead: () -> Unit = {},
    val onTimeSelectionChange: (startMs: Long, endMs: Long) -> Unit = { _, _ -> },
    val onTimeRangeOp: (TimeRangeOp) -> Unit = {},
    val onClearTimeSelection: () -> Unit = {}
)

private class ClipMenuItem(
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    /** `true` = al tocarlo NO se cierra el menú (p. ej. "Más…", que cambia de nivel). */
    val keepsMenuOpen: Boolean = false,
    val onClick: () -> Unit
)

/** Alto de cada fila del menú contextual (compacto, como FL Studio Mobile). */
private val CONTEXT_MENU_ITEM_HEIGHT = 32.dp
private val CONTEXT_MENU_MIN_WIDTH = 132.dp
private val CONTEXT_MENU_MAX_WIDTH = 220.dp
private val CONTEXT_MENU_GAP = 6.dp
private val CONTEXT_MENU_SCREEN_MARGIN = 8.dp

/**
 * Posición (esquina superior izquierda, en px de ventana) de un menú
 * contextual de [menuSize] respecto al [anchor] (el clip / la selección).
 *
 * Reglas (equivalentes al menú de clip de FL Studio Mobile: una ventana
 * chica pegada al clip, nunca una hoja a pantalla completa):
 *  1. Se despliega AL COSTADO DERECHO del ancla; si no entra, al costado
 *     izquierdo.
 *  2. Si no entra a ninguno de los dos lados (ancla casi tan ancha como la
 *     pantalla, p. ej. el carril vacío), se centra en el ancla visible y se
 *     despliega DEBAJO; si debajo no entra, ARRIBA.
 *  3. Siempre queda dentro de la ventana, con [margin] de respiro.
 *
 * El ancla se recorta primero a la parte VISIBLE de la ventana: un clip
 * parcialmente fuera de pantalla (timeline desplazado) no empuja el menú
 * fuera de la vista.
 *
 * Pura y sin dependencias de Compose en runtime → testeada en
 * `ContextMenuPositionTest`.
 */
internal fun computeContextMenuOffset(
    anchor: IntRect,
    windowSize: IntSize,
    menuSize: IntSize,
    gap: Int,
    margin: Int
): IntOffset {
    val visibleLeft = anchor.left.coerceAtLeast(0)
    val visibleRight = anchor.right.coerceAtMost(windowSize.width)
    val maxX = (windowSize.width - menuSize.width - margin).coerceAtLeast(margin)
    val maxY = (windowSize.height - menuSize.height - margin).coerceAtLeast(margin)

    val rightX = visibleRight + gap
    val leftX = visibleLeft - gap - menuSize.width

    return when {
        rightX <= maxX ->
            IntOffset(rightX, anchor.top.coerceIn(margin, maxY))
        leftX >= margin ->
            IntOffset(leftX, anchor.top.coerceIn(margin, maxY))
        else -> {
            val centerX = (visibleLeft + visibleRight) / 2 - menuSize.width / 2
            val belowY = anchor.bottom + gap
            val aboveY = anchor.top - gap - menuSize.height
            val y = when {
                belowY <= maxY -> belowY
                aboveY >= margin -> aboveY
                else -> anchor.top.coerceIn(margin, maxY)
            }
            IntOffset(centerX.coerceIn(margin, maxX), y)
        }
    }
}

private class ContextMenuPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = computeContextMenuOffset(
        anchor = anchorBounds,
        windowSize = windowSize,
        menuSize = popupContentSize,
        gap = gapPx,
        margin = marginPx
    )
}

/**
 * Menú contextual de clip/carril. Armado con [Popup] (y no `DropdownMenu`)
 * por el mismo motivo documentado en el resto de la app: así usa los
 * colores propios (`SurfaceTintedElevated`) sin pelear con el `Surface`
 * interno de Material.
 *
 * BUG REAL corregido (reportado con captura: "ventana exageradamente
 * grande, ocupa espacio en vano, debe desplegarse al lado del clip"): un
 * `Popup` recibe como restricción máxima el tamaño de la VENTANA completa,
 * y cada ítem usaba `fillMaxWidth()` sin que el contenedor tuviera un
 * ancho propio → el menú se estiraba a todo el ancho de la pantalla (y,
 * con filas de ~42 dp × 11 ítems, ocupaba media pantalla de alto). Ahora:
 *  - el ancho es el del ítem más largo ([IntrinsicSize.Max]) acotado a
 *    [CONTEXT_MENU_MIN_WIDTH]..[CONTEXT_MENU_MAX_WIDTH];
 *  - filas compactas de [CONTEXT_MENU_ITEM_HEIGHT];
 *  - el alto se acota al de la pantalla y, si no entra, hace scroll;
 *  - se posiciona AL COSTADO del ancla con [computeContextMenuOffset].
 */
@Composable
private fun ClipPopupMenu(items: List<ClipMenuItem>, onDismiss: () -> Unit) {
    val menuShape = RoundedCornerShape(8.dp)
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val positionProvider = remember(density) {
        with(density) {
            ContextMenuPositionProvider(
                gapPx = CONTEXT_MENU_GAP.roundToPx(),
                marginPx = CONTEXT_MENU_SCREEN_MARGIN.roundToPx()
            )
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .shadow(elevation = 8.dp, shape = menuShape)
                .clip(menuShape)
                .background(SurfaceTintedElevated)
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.12f), shape = menuShape)
                .widthIn(min = CONTEXT_MENU_MIN_WIDTH, max = CONTEXT_MENU_MAX_WIDTH)
                .width(IntrinsicSize.Max)
                .heightIn(max = screenHeightDp - CONTEXT_MENU_SCREEN_MARGIN * 2)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 2.dp)
        ) {
            items.forEach { item ->
                val textColor = when {
                    !item.enabled -> Color.White.copy(alpha = 0.38f)
                    item.destructive -> Color(0xFFFF6B6B)
                    else -> Color.White
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CONTEXT_MENU_ITEM_HEIGHT)
                        .clickable(enabled = item.enabled) {
                            if (!item.keepsMenuOpen) onDismiss()
                            item.onClick()
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = item.label,
                        color = textColor,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private val TIME_SELECTION_COLOR = Color(0xFF4FC3F7)
private val SELECTION_HANDLE_WIDTH = 22.dp

/**
 * Selección de tiempo sobre el carril: un rango sombreado con dos manijas
 * arrastrables y un toque en su cuerpo que abre el menú de [TimeRangeOp].
 * Las manijas usan el MISMO imán que los clips (cursor, bordes de clips,
 * inicio/fin del proyecto y la rejilla musical). El arrastre acumula el
 * movimiento "en crudo" aparte del valor ajustado, para que el imán no
 * "se coma" el gesto ni lo haga saltar.
 */
@Composable
private fun AudioTimeSelectionOverlay(
    selection: AudioTimeSelection,
    pxPerMs: Float,
    snapTargetsMs: List<Long>,
    gridMs: Double,
    onChange: (startMs: Long, endMs: Long) -> Unit,
    onOp: (TimeRangeOp) -> Unit,
    onClear: () -> Unit
) {
    if (pxPerMs <= 0f) return
    val density = LocalDensity.current
    val currentSelection by rememberUpdatedState(selection)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentTargets by rememberUpdatedState(snapTargetsMs)
    val currentGridMs by rememberUpdatedState(gridMs)
    var menuOpen by remember { mutableStateOf(false) }
    val thresholdMs = (with(density) { SNAP_THRESHOLD.toPx() } / pxPerMs).toLong()

    val leftPx = selection.startMs * pxPerMs
    val widthPx = (selection.lengthMs * pxPerMs).coerceAtLeast(1f)

    Box(modifier = Modifier.fillMaxSize()) {
        // Cuerpo: sombreado + duración; un toque abre el menú.
        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), 0) }
                .width(with(density) { widthPx.toDp() })
                .fillMaxHeight()
                .background(TIME_SELECTION_COLOR.copy(alpha = 0.26f))
                .border(1.dp, TIME_SELECTION_COLOR.copy(alpha = 0.9f))
                .pointerInput(Unit) { detectTapGestures(onTap = { menuOpen = true }) },
            contentAlignment = Alignment.Center
        ) {
            if (widthPx > with(density) { 44.dp.toPx() }) {
                Text(
                    text = String.format(java.util.Locale.getDefault(), "%.2f s", selection.lengthMs / 1000f),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            if (menuOpen) {
                ClipPopupMenu(
                    items = TimeRangeOp.values().map { op -> ClipMenuItem(op.label) { onOp(op) } } +
                        ClipMenuItem("Quitar selección") { onClear() },
                    onDismiss = { menuOpen = false }
                )
            }
        }
        // Manijas de inicio y fin.
        for (isStart in booleanArrayOf(true, false)) {
            val edgePx = (if (isStart) selection.startMs else selection.endMs) * pxPerMs
            Box(
                modifier = Modifier
                    .offset { IntOffset((edgePx - with(density) { SELECTION_HANDLE_WIDTH.toPx() } / 2f).roundToInt(), 0) }
                    .width(SELECTION_HANDLE_WIDTH)
                    .fillMaxHeight()
                    .pointerInput(isStart, pxPerMs) {
                        var rawMs = 0.0
                        detectHorizontalDragGestures(
                            onDragStart = {
                                rawMs = (if (isStart) currentSelection.startMs else currentSelection.endMs).toDouble()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                rawMs += dragAmount / pxPerMs
                                val snapped = snapToTargetsMs(rawMs.roundToLong(), currentTargets, thresholdMs, currentGridMs)
                                if (isStart) currentOnChange(snapped, currentSelection.endMs)
                                else currentOnChange(currentSelection.startMs, snapped)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(TIME_SELECTION_COLOR, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/**
 * Líneas de la rejilla musical: una por paso, más marcada en cada COMPÁS
 * (4 pulsos). Si a este zoom las líneas quedan a menos de 5 px una de otra
 * no se dibuja nada (sería una mancha ilegible); al acercar reaparecen.
 */
@Composable
private fun AudioGridLines(stepMs: Double, barMs: Double, pxPerMs: Float, durationMs: Long, modifier: Modifier = Modifier) {
    if (stepMs <= 0.0 || pxPerMs <= 0f || stepMs * pxPerMs < 5.0) return
    Canvas(modifier = modifier) {
        val endPx = (durationMs * pxPerMs).coerceAtMost(size.width)
        var i = 0
        while (true) {
            val ms = i * stepMs
            val x = (ms * pxPerMs).toFloat()
            if (x > endPx) break
            val isBar = barMs > 0.0 && kotlin.math.abs(Math.round(ms / barMs) * barMs - ms) < 0.5
            drawLine(
                color = Color.White.copy(alpha = if (isBar) 0.24f else 0.09f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = if (isBar) 1.5f else 1f
            )
            i++
        }
    }
}

/** Ajustes de la rejilla del carril: tempo (BPM) y división. */
@Composable
private fun AudioGridDialog(
    bpm: Float,
    step: AudioGridStep,
    onConfirm: (bpm: Float, step: AudioGridStep) -> Unit,
    onDismiss: () -> Unit
) {
    var draftBpm by remember { mutableStateOf(bpm.coerceIn(MIN_GRID_BPM, MAX_GRID_BPM).roundToInt().toFloat()) }
    var draftStep by remember { mutableStateOf(step) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceTintedElevated,
        title = { Text("Rejilla", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Tempo: ${draftBpm.roundToInt()} BPM", color = Color.White, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { draftBpm = (draftBpm - 1f).coerceAtLeast(MIN_GRID_BPM) }) { Text("−1") }
                    Slider(
                        value = draftBpm,
                        onValueChange = { draftBpm = it.roundToInt().toFloat() },
                        valueRange = MIN_GRID_BPM..MAX_GRID_BPM,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { draftBpm = (draftBpm + 1f).coerceAtMost(MAX_GRID_BPM) }) { Text("+1") }
                }
                Text("División", color = Color.White, fontSize = 14.sp)
                AudioGridStep.values().toList().chunked(3).forEach { rowSteps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowSteps.forEach { option ->
                            val selected = option == draftStep
                            Text(
                                text = option.label,
                                color = if (selected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) TIME_SELECTION_COLOR else Color.White.copy(alpha = 0.10f))
                                    .clickable { draftStep = option }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draftBpm, draftStep); onDismiss() }) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Rampas de fade del clip: un triángulo oscurecido sobre la parte
 * atenuada y una línea que marca la curva de ganancia — fade-in a la
 * izquierda, fade-out a la derecha — como en la playlist de un DAW.
 */
@Composable
private fun ClipFadeOverlay(
    fadeInMs: Long,
    fadeOutMs: Long,
    audibleMs: Long,
    pxPerMs: Float,
    modifier: Modifier = Modifier
) {
    if (pxPerMs <= 0f || audibleMs <= 0L || (fadeInMs <= 0L && fadeOutMs <= 0L)) return
    Canvas(modifier = modifier) {
        val shade = Color.Black.copy(alpha = 0.38f)
        val curve = Color.White.copy(alpha = 0.55f)
        // Se dibuja lo que REALMENTE suena (misma regla que el export): cada
        // fundido acotado a la mitad del tramo audible, y el fade-out termina
        // donde acaba el audio — no necesariamente en el borde del clip si,
        // sin loop, el archivo es más corto que el clip.
        val (effectiveIn, effectiveOut) = effectiveFadesMs(audibleMs, fadeInMs, fadeOutMs)
        val audiblePx = (audibleMs * pxPerMs).coerceIn(0f, size.width)
        val fadeInPx = (effectiveIn * pxPerMs).coerceIn(0f, audiblePx)
        if (fadeInPx > 1f) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(fadeInPx, 0f)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path = path, color = shade)
            drawLine(color = curve, start = Offset(0f, size.height), end = Offset(fadeInPx, 0f), strokeWidth = 1f)
        }
        val fadeOutPx = (effectiveOut * pxPerMs).coerceIn(0f, audiblePx)
        if (fadeOutPx > 1f) {
            val path = Path().apply {
                moveTo(audiblePx - fadeOutPx, 0f)
                lineTo(audiblePx, 0f)
                lineTo(audiblePx, size.height)
                close()
            }
            drawPath(path = path, color = shade)
            drawLine(color = curve, start = Offset(audiblePx - fadeOutPx, 0f), end = Offset(audiblePx, size.height), strokeWidth = 1f)
        }
    }
}

/**
 * Línea de LOOP de un borde del clip (FL Studio Mobile): una barra vertical
 * gruesa de puntas redondeadas pegada al clip POR FUERA. Arrastrarla estira o
 * acorta el clip repitiendo el archivo en loop hacia ese lado.
 *
 * Solo se compone para el clip SELECCIONADO y con loop activo (ver
 * `AudioClipBlock`). La zona táctil (`modifier`, ancho [LOOP_HANDLE_TOUCH_WIDTH])
 * es bastante más ancha que la barra visible y queda toda fuera del clip.
 * Solo reacciona a movimientos HORIZONTALES: el scroll vertical del timeline
 * no se bloquea.
 *
 * @param isLeft `true` = línea izquierda (la barra se pega al borde derecho de su zona).
 * @param barShiftPx desplazamiento de la barra dentro de su zona (con signo) para
 *   que nunca se salga del carril cuando el clip está pegado a un extremo.
 * @param active `true` mientras se arrastra (se ve más brillante).
 */
@Composable
private fun LoopStretchHandle(
    isLeft: Boolean,
    barShiftPx: Float,
    active: Boolean,
    modifier: Modifier,
    onDragStart: () -> Unit,
    onDrag: (dxPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    // `rememberUpdatedState`: el gesto es una corrutina larga; así siempre
    // llama a la versión MÁS RECIENTE de cada callback y no a una copia vieja.
    val currentStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentCancel by rememberUpdatedState(onDragCancel)
    Box(
        modifier = modifier
            .fillMaxHeight()
            // Un toque (o toque largo) SIN arrastrar sobre la línea no debe
            // llegar al carril: lo tomaría como "tocar el carril vacío" y
            // deseleccionaría el clip (o abriría el menú del carril).
            .pointerInput(Unit) { detectTapGestures(onTap = { }) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { currentStart() },
                    onDragEnd = { currentEnd() },
                    onDragCancel = { currentCancel() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        currentDrag(dragAmount)
                    }
                )
            },
        contentAlignment = if (isLeft) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(
                    start = if (isLeft) 0.dp else LOOP_HANDLE_GAP,
                    end = if (isLeft) LOOP_HANDLE_GAP else 0.dp
                )
                .offset { IntOffset(barShiftPx.roundToInt(), 0) }
                .padding(vertical = 2.dp)
                .width(LOOP_HANDLE_BAR_WIDTH)
                .fillMaxHeight()
                .clip(RoundedCornerShape(LOOP_HANDLE_BAR_WIDTH / 2))
                .background(Color.White.copy(alpha = if (active) 1f else 0.85f))
        )
    }
}

/**
 * Bloque visual y arrastrable de UN [AudioClip] dentro del carril — mismo
 * diseño que un clip de audio en la playlist de FL Studio Mobile:
 *
 *  - CABECERA con el nombre del archivo (franja de color de la pista) y,
 *    debajo, el CUERPO oscuro con la forma de onda.
 *  - Con `loop`, la señal se REPITE a lo ancho del clip (misma regla que
 *    el export) y cada repetición se marca con un divisor fino.
 *  - Rampas de fade-in/fade-out sobre el cuerpo.
 *  - SELECCIÓN por clip: tocar el clip lo selecciona (borde blanco); los
 *    demás clips quedan sin borde. Tocar el carril vacío lo deselecciona.
 *  - MOVER: solo arrastrando la CABECERA (horizontal, en cualquier punto de
 *    ella, ver [CLIP_DRAG_ZONE_HEIGHT]). El cuerpo NO mueve el clip.
 *  - MENÚ: mantener presionado el CUERPO (la señal de audio) abre el menú
 *    del clip, que se despliega al costado del clip.
 *  - ESTIRAR EN LOOP: solo con el clip SELECCIONADO y con loop activo
 *    aparecen dos líneas gruesas ([LoopStretchHandle]) pegadas por fuera de
 *    cada lado; arrastrar cualquiera estira (o acorta) el clip repitiendo el
 *    archivo hacia ese lado. Sin loop el clip conserva su largo original.
 *
 * Los arrastres se previsualizan en vivo con estado local y se
 * confirman SOLO al soltar (un evento al ViewModel por gesto, no uno por
 * píxel). La vista previa usa las mismas reglas de acotado que el
 * ViewModel ([clampClipLengthMs], [withLeftEdgeMovedMs]); el desplazamiento
 * acumulado se acota al rango permitido MIENTRAS se arrastra, así no hay
 * "zona muerta" al pasarse de un límite y volver.
 *
 * Cada clip tiene su propio estado y su propia forma de onda; el
 * llamador lo envuelve en `key(clip.id)`.
 *
 * @param trackWidthPx ancho total del carril (todo el timeline).
 * @param projectDurationMs duración del proyecto: tope al estirar el borde derecho.
 */
@Composable
private fun AudioClipBlock(
    clip: AudioClip,
    trackWidthPx: Float,
    pxPerMs: Float,
    projectDurationMs: Long,
    trackBrush: Brush,
    trackColorStrong: Color,
    waveformSignalColor: Color,
    isTrackSelected: Boolean,
    actions: AudioClipEditActions,
    snapTargetsMs: List<Long>,
    onSelect: () -> Unit,
    onOpenDetail: () -> Unit,
    onTimelineStartChange: (newStartMs: Long) -> Unit
) {
    val density = LocalDensity.current
    val appContext = LocalContext.current.applicationContext
    // Los gestos son corrutinas largas con claves fijas: leen siempre la
    // versión MÁS RECIENTE de todo lo que cambia entre recomposiciones.
    val currentActions by rememberUpdatedState(actions)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTimelineStartChange by rememberUpdatedState(onTimelineStartChange)
    val currentClip by rememberUpdatedState(clip)
    val currentOnOpenDetail by rememberUpdatedState(onOpenDetail)
    val currentPxPerMs by rememberUpdatedState(pxPerMs)
    val currentTrackWidthPx by rememberUpdatedState(trackWidthPx)
    val currentProjectDurationMs by rememberUpdatedState(projectDurationMs)

    // Selección por CLIP: la fila del carril tiene que estar seleccionada Y
    // ser este el clip elegido.
    val clipSelected = isTrackSelected && actions.selectedClipId == clip.id
    val currentClipSelected by rememberUpdatedState(clipSelected)
    val haptic = LocalHapticFeedback.current
    // Primero la fila (que limpia el clip elegido) y después este clip.
    val selectThisClip: () -> Unit = {
        currentOnSelect()
        currentActions.onSelectClip(currentClip.id)
    }

    // Forma de onda de ESTE clip — decodificada una vez por archivo, con
    // caché compartida (ver `AudioWaveformAnalyzer`). Resetea a null al
    // cambiar de archivo para no mostrar la señal del anterior.
    val waveform by produceState<AudioWaveform?>(initialValue = null, clip.sourceUri, clip.sourceDurationMs, clip.reversed) {
        value = null
        // Clip invertido: se dibuja la forma de onda recorrida al revés (la
        // caché de análisis sigue siendo por archivo, no por orientación).
        value = AudioWaveformAnalyzer.load(appContext, clip.sourceUri, clip.sourceDurationMs)
            ?.let { if (clip.reversed) it.reversedCopy() else it }
    }

    // Desplazamientos en vivo (px) de cada gesto; se traducen a ms y se
    // reportan SOLO al soltar. Estados ESTABLES (sin `remember(claves)`): los
    // gestos viven en corrutinas de clave fija y capturan la instancia; cada
    // uno los deja en 0 al terminar o cancelarse.
    var moveDragPx by remember { mutableStateOf(0f) }
    var leftDragPx by remember { mutableStateOf(0f) }
    var rightDragPx by remember { mutableStateOf(0f) }
    var isMoving by remember { mutableStateOf(false) }
    var leftHandleActive by remember { mutableStateOf(false) }
    var rightHandleActive by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Segundo nivel del menú ("Más…"), igual que FL Studio Mobile.
    var menuMore by remember { mutableStateOf(false) }

    // --- Geometría previsualizada (misma regla que el ViewModel) ---
    val leftDeltaMs: Long =
        if (pxPerMs > 0f && leftDragPx != 0f) (leftDragPx / pxPerMs).roundToInt().toLong() else 0L
    // El clip tal como quedaría con el borde izquierdo en la posición del dedo
    // (la MISMA función que aplica el ViewModel al soltar).
    val leftShaped: AudioClip = if (leftDeltaMs != 0L) clip.withLeftEdgeMovedMs(leftDeltaMs) else clip

    // --- Imán: al mover o estirar, los bordes se pegan a los objetivos
    // cercanos (cursor, bordes de otros clips, 0, final del proyecto) y,
    // al estirar con loop, a cada repetición COMPLETA del archivo. La
    // misma regla corre en la vista previa y al soltar (ver
    // `snapMovedClipStartMs`/`snapToTargetsMs`).
    val snapThresholdMs: Long =
        if (pxPerMs > 0f) (with(density) { SNAP_THRESHOLD.toPx() } / pxPerMs).toLong() else 0L
    val loopEdgesMs: List<Long> =
        if (clip.loop && clip.sourceDurationMs > 0L) {
            val firstPassMs = (clip.sourceDurationMs - clip.trimStartMs).coerceAtLeast(0L)
            buildList {
                var edge = clip.timelineStartMs + firstPassMs
                var count = 0
                while (count < MAX_LOOP_SNAP_TARGETS && edge <= projectDurationMs) {
                    add(edge)
                    edge += clip.sourceDurationMs
                    count++
                }
            }
        } else emptyList()
    val snapStart: (Long) -> Long = { rawStartMs ->
        snapMovedClipStartMs(rawStartMs, clip.clipLengthMs, snapTargetsMs, snapThresholdMs, actions.gridMs)
    }
    val snapLength: (Long) -> Long = { requestedLengthMs ->
        snapToTargetsMs(clip.timelineStartMs + requestedLengthMs, snapTargetsMs + loopEdgesMs, snapThresholdMs, actions.gridMs) -
            clip.timelineStartMs
    }
    // Los gestos son corrutinas largas: leen siempre la versión más reciente.
    val currentSnapStart by rememberUpdatedState(snapStart)
    val currentSnapLength by rememberUpdatedState(snapLength)

    val lengthMs: Long =
        if (pxPerMs > 0f && rightDragPx != 0f) {
            clip.clampClipLengthMs(
                snapLength(clip.clipLengthMs + (rightDragPx / pxPerMs).roundToInt().toLong()),
                projectDurationMs
            )
        } else {
            leftShaped.clipLengthMs
        }
    val trimPreviewMs = leftShaped.trimStartMs

    val moveStartMs: Long =
        if (pxPerMs > 0f && moveDragPx != 0f) {
            snapStart(clip.timelineStartMs + (moveDragPx / pxPerMs).roundToInt().toLong())
        } else {
            leftShaped.timelineStartMs
        }
    val displayStartPx = (moveStartMs * pxPerMs).coerceIn(
        0f,
        (trackWidthPx - lengthMs * pxPerMs).coerceAtLeast(0f)
    )
    val clipWidthPx = (lengthMs * pxPerMs).coerceAtMost(
        (trackWidthPx - displayStartPx).coerceAtLeast(0f)
    )

    // --- Líneas de loop: solo con el clip SELECCIONADO y con loop activo ---
    // El espacio libre a cada lado se mide con la geometría CONFIRMADA (no la
    // previsualizada): si dependiera del arrastre en curso, la línea podría
    // desaparecer a mitad del gesto y cancelarlo.
    val barPx = with(density) { LOOP_HANDLE_BAR_WIDTH.toPx() }
    val gapPx = with(density) { LOOP_HANDLE_GAP.toPx() }
    val touchPx = with(density) { LOOP_HANDLE_TOUCH_WIDTH.toPx() }
    val loopHandlesEnabled = clipSelected && clip.loop && clip.sourceDurationMs > 0L && pxPerMs > 0f
    val leftRoomPx = clip.timelineStartMs * pxPerMs
    val rightRoomPx = (trackWidthPx - (clip.timelineStartMs + clip.clipLengthMs) * pxPerMs).coerceAtLeast(0f)
    val minRoomPx = barPx + gapPx
    // Sin lugar por fuera (clip pegado al borde del carril) esa línea no se ofrece.
    // En píxeles ENTEROS: el contenedor se corre a la izquierda `leftZonePx` y el
    // clip interior se compensa en la misma cantidad, así seleccionar/deseleccionar
    // no mueve el clip ni 1 px por redondeos distintos.
    val leftZonePx =
        if (loopHandlesEnabled && leftRoomPx >= minRoomPx) minOf(touchPx, leftRoomPx).toInt().toFloat() else 0f
    val rightZonePx =
        if (loopHandlesEnabled && rightRoomPx >= minRoomPx) minOf(touchPx, rightRoomPx).toInt().toFloat() else 0f
    // Durante el arrastre el clip puede acercarse al borde del carril: la barra
    // se corre hacia adentro de su zona para no salirse de la pantalla.
    val leftBarShiftPx = (minRoomPx - displayStartPx).coerceAtLeast(0f)
    val rightBarShiftPx = -((minRoomPx - (trackWidthPx - (displayStartPx + clipWidthPx))).coerceAtLeast(0f))
    val leftZoneDp = with(density) { leftZonePx.toDp() }
    val rightZoneDp = with(density) { rightZonePx.toDp() }

    val clipShape = RoundedCornerShape(4.dp)
    // Sin borde salvo que el clip esté seleccionado (referencia FL Studio Mobile).
    val borderColor = if (clipSelected) Color.White.copy(alpha = 0.85f) else Color.Transparent

    // Contenedor SIN recorte que incluye las zonas táctiles de las líneas de
    // loop (están dentro de sus límites, no dependen de tocar fuera del padre).
    Box(
        modifier = Modifier
            .offset { IntOffset((displayStartPx - leftZonePx).roundToInt(), 0) }
            .width(with(density) { (leftZonePx + clipWidthPx + rightZonePx).toDp() })
            .fillMaxHeight()
            // El clip seleccionado queda por encima de sus vecinos: sus líneas de
            // loop sobresalen por fuera y no deben quedar tapadas. `zIndex` solo cambia
            // el orden de dibujo/toque (no mueve nodos): es seguro en mitad de un gesto.
            .zIndex(if (clipSelected) 1f else 0f)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(leftZonePx.roundToInt(), 0) }
                .width(with(density) { clipWidthPx.toDp() })
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .clip(clipShape)
                // Cuerpo oscuro bajo el tinte de la pista: da contraste a la señal.
                .background(Color.Black.copy(alpha = 0.45f))
                .background(brush = trackBrush, alpha = if (isMoving) 0.45f else 0.30f)
                .border(width = 1.dp, color = borderColor, shape = clipShape),
            contentAlignment = Alignment.TopStart
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Cabecera VISUAL: nombre del clip. Su gesto vive en la zona de
                // arrastre de más abajo (ocupa este mismo lugar, encima).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CLIP_HEADER_HEIGHT)
                        .background(brush = trackBrush, alpha = 0.95f)
                        .background(Color.Black.copy(alpha = if (clip.muted) 0.40f else 0.12f))
                        // Mientras se arrastra, la cabecera se ilumina: se nota que está agarrada.
                        .background(Color.White.copy(alpha = if (isMoving) 0.16f else 0f)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = clip.displayName,
                        color = Color.White.copy(alpha = if (clip.muted) 0.6f else 1f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            // `labelSmall` trae lineHeight = 16.sp: con fontSize = 9.sp
                            // el texto flotaría en una caja más alta que la cabecera.
                            lineHeight = 11.sp,
                            shadow = AudioNameShadow
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp)
                    )
                }
                // Cuerpo: forma de onda (repetida si hay loop) + rampas de fade.
                // Toque = seleccionar el clip; toque LARGO = menú del clip.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(clip.id) {
                            // Toque simple AL INSTANTE (selecciona, como siempre);
                            // doble toque abre la ventana ampliada del clip (ver
                            // `detectImmediateTapGestures`: el primer toque ya
                            // seleccionó, y seleccionar es idempotente).
                            detectImmediateTapGestures(
                                onTap = { selectThisClip() },
                                onDoubleTap = {
                                    selectThisClip()
                                    currentOnOpenDetail()
                                },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectThisClip()
                                    menuOpen = true
                                }
                            )
                        }
                ) {
                    AudioWaveformCanvas(
                        waveform = waveform,
                        trimStartMs = trimPreviewMs,
                        pxPerMs = pxPerMs,
                        signalColor = waveformSignalColor,
                        dimmed = clip.muted,
                        loop = clip.loop,
                        modifier = Modifier.fillMaxSize()
                    )
                    ClipFadeOverlay(
                        fadeInMs = clip.fadeInMs,
                        fadeOutMs = clip.fadeOutMs,
                        audibleMs = audibleLengthMs(clip.sourceDurationMs, trimPreviewMs, clip.loop, lengthMs),
                        pxPerMs = pxPerMs,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Zona de ARRASTRE: TODA la cabecera (de borde a borde del clip). Es
            // lo único que mueve el clip, y solo en horizontal — el movimiento
            // vertical queda libre para el scroll del timeline. Tocarla también
            // selecciona el clip.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(CLIP_DRAG_ZONE_HEIGHT)
                    .pointerInput(clip.id) {
                        detectTapGestures(onTap = { selectThisClip() })
                    }
                    .pointerInput(clip.id) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                if (!currentClipSelected) selectThisClip()
                                isMoving = true
                            },
                            onDragEnd = {
                                isMoving = false
                                val ppm = currentPxPerMs
                                if (ppm > 0f) {
                                    val c = currentClip
                                    val maxStartPx = (currentTrackWidthPx - c.clipLengthMs * ppm).coerceAtLeast(0f)
                                    val rawStartMs = c.timelineStartMs + (moveDragPx / ppm).roundToInt().toLong()
                                    val startPx = (currentSnapStart(rawStartMs) * ppm).coerceIn(0f, maxStartPx)
                                    currentOnTimelineStartChange((startPx / ppm).roundToInt().toLong().coerceAtLeast(0L))
                                }
                                moveDragPx = 0f
                            },
                            onDragCancel = {
                                isMoving = false
                                moveDragPx = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                // Se acota MIENTRAS se acumula: pasarse del inicio o del
                                // final del carril no crea una "zona muerta" que haya
                                // que deshacer con el dedo antes de que el clip responda.
                                val c = currentClip
                                val ppm = currentPxPerMs
                                val startPx = c.timelineStartMs * ppm
                                val maxStartPx = (currentTrackWidthPx - c.clipLengthMs * ppm).coerceAtLeast(0f)
                                moveDragPx = (moveDragPx + dragAmount).coerceIn(-startPx, maxStartPx - startPx)
                            }
                        )
                    }
            )

            if (menuOpen) {
                // "Dividir" solo se ofrece si el cursor cae dentro del clip.
                val canSplit = clip.splitAtTimelineMs(actions.playheadMs, "") != null
                // "Unir" solo se ofrece si existe una continuación exacta.
                val canMerge = actions.canMergeWithNext(clip.id)
                ClipPopupMenu(
                    items = if (!menuMore) listOf(
                        ClipMenuItem("Copiar") { currentActions.onCopy(clip.id) },
                        ClipMenuItem("Cortar") { currentActions.onCut(clip.id) },
                        ClipMenuItem("Duplicar") { currentActions.onDuplicate(clip.id) },
                        ClipMenuItem("Dividir en el cursor", enabled = canSplit) { currentActions.onSplit(clip.id) },
                        ClipMenuItem("Eliminar", destructive = true) { currentActions.onDelete(clip.id) },
                        ClipMenuItem("Más…", keepsMenuOpen = true) { menuMore = true }
                    ) else listOf(
                        ClipMenuItem("Seleccionar clip") { currentActions.onSelectClipRange(clip.id) },
                        ClipMenuItem("Unir con el siguiente", enabled = canMerge) { currentActions.onMergeWithNext(clip.id) },
                        ClipMenuItem("Pegar en el cursor", enabled = actions.hasClipboard) { currentActions.onPasteAtPlayhead() },
                        ClipMenuItem(if (clip.reversed) "Quitar inversión" else "Invertir (Reverse)") { currentActions.onToggleReverse(clip.id) },
                        ClipMenuItem(if (clip.normalizeGain != 1f) "Quitar normalización" else "Normalizar") { currentActions.onToggleNormalize(clip.id) },
                        ClipMenuItem(if (clip.muted) "Activar sonido" else "Silenciar") { currentActions.onToggleMute(clip.id) }
                    ),
                    onDismiss = {
                        menuOpen = false
                        menuMore = false
                    }
                )
            }
        }

        // Línea de loop IZQUIERDA: estira (o acorta) el clip hacia atrás repitiendo el archivo.
        if (leftZonePx > 0f) {
            LoopStretchHandle(
                isLeft = true,
                barShiftPx = leftBarShiftPx,
                active = leftHandleActive,
                modifier = Modifier.align(Alignment.CenterStart).width(leftZoneDp),
                onDragStart = { leftHandleActive = true },
                onDrag = { dx ->
                    val ppm = currentPxPerMs
                    if (ppm > 0f) {
                        val range = currentClip.leftEdgeDeltaRangeMs()
                        leftDragPx = (leftDragPx + dx).coerceIn(range.first * ppm, range.last * ppm)
                    }
                },
                onDragEnd = {
                    leftHandleActive = false
                    val ppm = currentPxPerMs
                    if (ppm > 0f) {
                        val delta = (leftDragPx / ppm).roundToInt().toLong()
                        if (delta != 0L) currentActions.onLeftEdgeChange(currentClip.id, delta)
                    }
                    leftDragPx = 0f
                },
                onDragCancel = {
                    leftHandleActive = false
                    leftDragPx = 0f
                }
            )
        }
        // Línea de loop DERECHA: estira (o acorta) el clip hacia adelante repitiendo el archivo.
        if (rightZonePx > 0f) {
            LoopStretchHandle(
                isLeft = false,
                barShiftPx = rightBarShiftPx,
                active = rightHandleActive,
                modifier = Modifier.align(Alignment.CenterEnd).width(rightZoneDp),
                onDragStart = { rightHandleActive = true },
                onDrag = { dx ->
                    val ppm = currentPxPerMs
                    if (ppm > 0f) {
                        val c = currentClip
                        val pd = currentProjectDurationMs
                        val minDelta = c.clampClipLengthMs(0L, pd) - c.clipLengthMs
                        val maxDelta = c.clampClipLengthMs(Long.MAX_VALUE / 2, pd) - c.clipLengthMs
                        rightDragPx = (rightDragPx + dx).coerceIn(minDelta * ppm, maxDelta * ppm)
                    }
                },
                onDragEnd = {
                    rightHandleActive = false
                    val ppm = currentPxPerMs
                    if (ppm > 0f) {
                        val delta = (rightDragPx / ppm).roundToInt().toLong()
                        if (delta != 0L) {
                            currentActions.onLengthChange(currentClip.id, currentSnapLength(currentClip.clipLengthMs + delta))
                        }
                    }
                    rightDragPx = 0f
                },
                onDragCancel = {
                    rightHandleActive = false
                    rightDragPx = 0f
                }
            )
        }
    }
}
