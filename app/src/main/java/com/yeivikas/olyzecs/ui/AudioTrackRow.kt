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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
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
 * 4. Panel de acciones rápidas "al costado" (NUEVO — bug real reportado
 *    con capturas de pantalla: "todas las capas [de imagen] tienen esta
 *    ventana flotante... esta última implementada de audio no, falta").
 *    Mismo gesto de dos toques que la miniatura de cualquier capa de
 *    imagen en `TimelineRow`: el primer toque sobre la columna
 *    ícono+nombre solo SELECCIONA la fila; con la fila ya seleccionada,
 *    un toque más despliega el panel — un `Popup` de Compose posicionado
 *    con el mismo [BesideAnchorPopupPositionProvider] que usa
 *    `TimelineRow`, pegado al borde derecho de esa columna. Como el audio
 *    no tiene "nombre editable"/"color" propios (a diferencia de una capa
 *    de imagen), las 6 acciones del panel son otras: las MISMAS 5 que ya
 *    existían como módulos flotantes en la pestaña "Módulos" — Volumen/
 *    Silenciar/Recorte/Bucle/Desvanecidos, ver `AudioModuleId` en
 *    EditorBottomBar.kt — más Eliminar (antes un botón "X" siempre
 *    visible en la fila; se consolida acá adentro del panel, mismo
 *    criterio que cualquier capa de imagen: eliminar SOLO vive dentro de
 *    su panel de acciones, no como botón aparte compitiendo por espacio
 *    en la fila).
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
    // `onOpenModule`: literalmente el mismo callback que ya recibe
    // `ModulesDrawerPanel` (`onAudioModuleClick` en EditorScreen.kt) — ver
    // KDoc de `TimelineView.onAudioModuleClick`.
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    isBottomPanelExpanded: Boolean = false,
    onOpenModule: (AudioModuleId) -> Unit = {}
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
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(isExpanded) { visibleState.targetState = isExpanded }
    val showActionsPanel = visibleState.currentState || visibleState.targetState

    // Deliberadamente SIN `remember`: a diferencia de `layerActions` en
    // TimelineRow (que sí memoiza, con las 3 banderas de la capa como
    // key), acá los 6 lambdas de `onClick` no dependen de ningún campo de
    // `audioClip` — memoizar con una key que nunca cambia (o sin key)
    // correría el riesgo real de quedarse con una versión vieja y
    // congelada de `onOpenModule`/`onRemoveRequest` si el caller alguna
    // vez deja de pasar la misma instancia de lambda entre recomposiciones
    // (exactamente el bug de "closure viejo" que ya se documentó y evitó
    // arriba con `rememberUpdatedState` para el arrastre). Recalcular la
    // lista en cada recomposición es un costo insignificante (6 objetos
    // chicos) frente a ese riesgo.
    val audioActions = listOf(
        LayerAction(
            iconRes = R.drawable.ic_volume,
            contentDescription = "Volumen del audio",
            onClick = { onOpenModule(AudioModuleId.VOLUME) }
        ),
        LayerAction(
            iconRes = R.drawable.ic_mute,
            contentDescription = "Silenciar / activar audio",
            onClick = { onOpenModule(AudioModuleId.MUTE) }
        ),
        LayerAction(
            iconRes = R.drawable.ic_trim,
            contentDescription = "Recorte de inicio del audio",
            onClick = { onOpenModule(AudioModuleId.TRIM) }
        ),
        LayerAction(
            iconRes = R.drawable.ic_loop,
            contentDescription = "Repetir en loop",
            onClick = { onOpenModule(AudioModuleId.LOOP) }
        ),
        LayerAction(
            iconRes = R.drawable.ic_fade,
            contentDescription = "Desvanecidos de entrada/salida",
            onClick = { onOpenModule(AudioModuleId.FADE) }
        ),
        LayerAction(
            iconRes = R.drawable.ic_delete,
            contentDescription = "Eliminar audio del proyecto",
            onClick = onRemoveRequest,
            tint = Color(0xFFFF6B6B)
        )
    )
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
                .background(if (isSelected) AUDIO_TRACK_COLOR.copy(alpha = 0.22f) else Color.Transparent)
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
                    .padding(start = 6.dp, end = 2.dp)
                    .clickable {
                        if (isSelected) {
                            onToggleExpand()
                        } else {
                            onSelect()
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { currentOnReorderDragStart() },
                            onDragEnd = { currentOnReorderDragEnd() },
                            onDragCancel = { currentOnReorderDragCancel() }
                        ) { change, dragAmount ->
                            change.consume()
                            currentOnReorderDrag(dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_music_note),
                    contentDescription = "Audio",
                    tint = AUDIO_TRACK_COLOR,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    audioClip.displayName,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // --- Panel "al costado" — mismo mecanismo EXACTO que el panel
            // de cualquier capa de imagen (ver el bloque equivalente en
            // TimelineRow, dentro de TimelineView.kt): un `Popup` sin
            // ancla explícita se posiciona a partir de SU PROPIO lugar en
            // la composición, así que tiene que declararse acá, hermano
            // directo de la columna ícono+nombre de arriba (para que
            // `BesideAnchorPopupPositionProvider` calcule "pegado a su
            // borde derecho" correctamente) — no más abajo, no más arriba.
            // Se suspende por completo mientras `isBottomPanelExpanded`
            // (alguna de las tres pestañas de abajo abierta) — mismo
            // criterio que TimelineRow, para no superponerse con el panel
            // de esa pestaña; a diferencia de las capas de imagen, esta
            // fila no tiene un acordeón "de abajo" equivalente para ese
            // caso (alcance deliberadamente acotado a lo pedido).
            if (showActionsPanel && !isBottomPanelExpanded) {
                Popup(
                    popupPositionProvider = BesideAnchorPopupPositionProvider(gapPx),
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
                                .background(color = AUDIO_TRACK_COLOR, shape = RectangleShape)
                                .border(
                                    width = 1.dp,
                                    color = AUDIO_TRACK_COLOR.copy(alpha = 0.5f),
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
                        .background(AUDIO_TRACK_COLOR.copy(alpha = if (isTimelineStartDragging) 0.55f else 0.40f))
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
                        }
                )
            }

            // --- Botón de renombrar (SOLO VISUAL por ahora, A PEDIDO
            // EXPLÍCITO DEL USUARIO) --------------------------------------
            // Ocupa el mismo lugar (fijo, al final de la fila) donde antes
            // vivía el botón "X" de quitar audio — ese ya se consolidó
            // adentro del panel "al costado" de arriba (acción "Eliminar
            // audio del proyecto"). Reusa el MISMO ícono SVG que ya usa
            // "Renombrar capa" para cualquier capa de imagen
            // (`ic_layer_rename`, ver `layerActions` en TimelineView.kt),
            // para que se lea como el mismo lenguaje visual de siempre —
            // no un ícono improvisado aparte.
            //
            // NO HACE NADA todavía — es intencional, no un olvido: el
            // usuario pidió explícitamente que por ahora sea solo visual,
            // sin ninguna función real conectada, para implementar el
            // renombrado del audio (diálogo, persistencia en
            // `AudioClip.displayName`, etc.) en una pasada aparte más
            // adelante. Se deja el `onClick` vacío y documentado así, en
            // vez de omitir el botón entero, precisamente para que sea
            // fácil de encontrar y cablear cuando llegue esa pasada — no
            // es una IconButton "muerta" por descuido, es un placeholder
            // deliberado y señalizado.
            IconButton(
                onClick = { /* TODO(audio-rename): sin función todavía — ver el comentario de arriba */ },
                modifier = Modifier.fillMaxHeight().width(32.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_layer_rename),
                    contentDescription = "Renombrar audio (próximamente)",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
