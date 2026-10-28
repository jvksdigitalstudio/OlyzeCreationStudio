package com.yeivikas.olyzecs.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.AudioWaveform
import com.yeivikas.olyzecs.engine.audio.AudioWaveformAnalyzer
import com.yeivikas.olyzecs.engine.audio.FineWaveform
import com.yeivikas.olyzecs.engine.audio.MIN_AUDIO_CLIP_LENGTH_MS
import com.yeivikas.olyzecs.engine.audio.PreviewSourceSelection
import com.yeivikas.olyzecs.engine.audio.label
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val DetailBackground = Color(0xFF101010)

/** Frontera UI → motor: el rango de la ventana tal como lo entiende el editor (ms del ARCHIVO). */
private fun DetailSelection.toPreviewSourceSelection() = PreviewSourceSelection(startMs, endMs)

/** Píxeles por muestra a los que llega el zoom máximo con datos finos / con la forma de onda gruesa del carril. */
private const val DETAIL_FINE_PX_PER_SAMPLE_AT_MAX = 14f
private const val DETAIL_COARSE_PX_PER_BUCKET_AT_MAX = 6f

/** Velocidad mínima (px/s) para que soltar un arrastre dispare inercia. */
private const val DETAIL_FLING_MIN_VELOCITY = 250f

/**
 * Alto (dp) de la franja central de los bordes donde se reclaman los gestos al sistema. Android concede como
 * máximo 200 dp por borde EN TOTAL: esta franja (160) + la regla de tiempo ([DETAIL_RULER_HEIGHT_DP] = 36, que
 * también reclama sus bordes para poder arrastrar el cabezal pegado al borde) suman 196 dp, dentro del límite.
 */
private const val DETAIL_GESTURE_EXCLUSION_HEIGHT_DP = 160

/** Guarda el trabajo (inercia / animación de zoom) en curso para poder cancelarlo al tocar de nuevo. */
private class DetailMotion {
    var job: Job? = null
}

/**
 * Ventana ampliada de UN clip de audio (referencia: FL Studio Mobile, doble
 * toque sobre la onda de un clip). Cubre TODO el timeline (regla, Master, carriles y
 * pestañas inferiores; ver [AudioClipDetailHost]) y muestra la SEÑAL ORIGINAL del archivo
 * a todo el ancho, con zoom de nivel profesional.
 *
 * ## Datos
 *  - **Forma de onda fina** ([FineWaveform]): muestras reales + pirámide de picos,
 *    cargada al abrir la ventana. Permite llegar hasta nivel de muestra con picos
 *    exactos (sin bloques ni desaparición de transitorios). Mientras carga (y si el
 *    audio es muy largo para conservarla) se usa la forma de onda del carril, con un
 *    zoom máximo acorde a su resolución.
 *  - Siempre el archivo COMPLETO, una sola vez, sin las repeticiones del loop ni el
 *    recorte del clip; lo que queda fuera del tramo que suena se ve atenuado. Con
 *    Reverse se ve invertida (el recorte se mide sobre esa versión).
 *
 * ## Gestos (sobre la onda)
 *  - **Pellizco horizontal:** zoom de tiempo anclado a los dedos, hasta nivel de muestra.
 *  - **Pellizco vertical:** zoom de amplitud (1×–[DETAIL_MAX_AMP_ZOOM]×).
 *  - **Arrastre con un dedo:** SELECCIONA un rango del archivo (referencia: WavePad): el rango va de donde se apoyó
 *    el dedo a donde está, también hacia la izquierda. Se pueden marcar hasta tantos rangos como diga el botón
 *    "Seleccionar" de la cabecera (x1 … x7), sin solaparse; con el cupo lleno, un arrastre nuevo no crea otro. Un
 *    arrastre que empieza sobre un borde de CUALQUIER rango existente ajusta ese borde. Ver
 *    [AudioClipDetailSelectionState]; no modifica el audio: con el bucle activo solo acota la vista previa a esos
 *    rangos, que suenan en orden de izquierda a derecha.
 *  - **Arrastre con dos dedos:** desplaza la vista, con inercia al soltar.
 *  - **Tocar la onda:** deselecciona todos los rangos; el cursor NO se mueve desde la onda.
 *  - **Regla de tiempo** (la franja de números, arriba): tocar en cualquier punto lleva ahí el cursor y su cabezal
 *    al instante; mantener y arrastrar lo recorre, desplegando la línea guía hacia abajo. Con zoom, llevarlo
 *    a un borde desplaza la vista sola. Ver [AudioClipDetailPlayhead].
 *  - **Vista general** (la barra del pie, [AudioClipDetailOverview]): el archivo entero con un recuadro de lo que muestra
 *    la onda; arrastrar el recuadro desplaza, tocar fuera salta a esa zona y arrastrar un borde acerca o aleja. Con un solo
 *    dedo, a diferencia de la onda (donde un dedo selecciona y desplazar pide dos).
 *  - **Doble toque:** zoom animado hacia ese punto ([DETAIL_DOUBLE_TAP_ZOOM]×) o, si ya
 *    hay zoom, vuelve animado al archivo completo.
 *
 * ## Eje de tiempo
 *  - La onda, la cuadrícula y la regla ([AudioClipDetailRuler]) miden desde dentro de un margen lateral de
 *    [DETAIL_TIME_GUTTER_DP] dp: el cabezal en 0:00 y al final de la señal se ve entero, no cortado por el borde.
 *    Las X de los gestos son del lienzo completo; se les resta el margen antes de convertirlas en tiempo.
 *
 * ## Vista previa en bucle
 *  - Al abrirse, el cursor del proyecto ya está al inicio de la señal del clip y el bucle ACTIVADO (botón de la
 *    cabecera, junto a la X): al dar Play solo suena ese clip y, al acabar, vuelve a empezar. Nunca suena solo.
 *    Desactivarlo devuelve la reproducción normal. Es estado de sesión del motor (`AudioClipPreview`): no se
 *    guarda, no entra al undo/redo y no toca el `loop` del clip, que sí cambia el audio exportado.
 *
 * ## Robustez
 *  - Reclama al sistema los gestos de borde en una franja central de cada lado
 *    (`systemGestureExclusion`) y en la franja de la regla: pellizcar con los pulgares cerca de los bordes
 *    ya no dispara el gesto "atrás" de Android (que cerraba la ventana y luego el editor).
 *  - Árbitro de "atrás" ([GuardedBackHandler] + `MultiTouchBackGuard`): un "atrás" que llega
 *    dentro de 2 s de un pellizco se ignora (la ventana NO se cierra ni se sale del editor).
 *    Cubre los bordes que `systemGestureExclusion` no puede reclamar (límite de 200 dp por borde)
 *    y los gestos "atrás" repetidos de un pellizco largo.
 *  - Su propio `BackHandler` se registra al abrir la ventana, así que siempre tiene
 *    prioridad sobre el del editor: "atrás" cierra primero la ventana ([onBackClose]).
 *
 * Abrirla y cerrarla no modifica el clip ni el proyecto (salvo mover el cursor), así que cerrarla
 * deja todo como estaba; las acciones del clip (reemplazar audio, Recorte, REV, bucle) están en la cabecera ([AudioClipDetailHeader]).
 *
 * @param signalColor color de la señal (el mismo del clip en el carril).
 * @param playheadMs cursor del proyecto (ms); se dibuja si cae dentro del clip.
 * @param onSeek mueve el cursor del proyecto a ese instante (ms de proyecto).
 * @param onClose cierra la ventana (botón X).
 * @param onBackClose cierra la ventana por "atrás" del sistema (el llamador puede además
 *   tragarse un "atrás" residual).
 * @param actions acciones del clip que usa la cabecera (reemplazar el audio, Recorte y REV, [AudioClipDetailHeader]).
 * @param loopEnabled estado del bucle de vista previa del clip (botón de la cabecera; el motor lo aplica, ver
 *   `AudioClipPreview`); @param onLoopChange el usuario lo activa o desactiva.
 * @param onScrubStart al agarrar el cabezal: pausa la reproducción para que el reloj no pise el cursor que mueve el
 *   dedo; @param onScrubEnd al soltarlo: la retoma si estaba sonando (el mismo par que usa el playhead del timeline).
 * @param onSelectionChange los rangos seleccionados cambiaron (al TERMINAR el gesto, no en cada movimiento) o se
 *   descartaron (lista vacía); ordenados de izquierda a derecha y sin solaparse: con el bucle activo, la vista previa
 *   suena y se repite solo en esos tramos, en orden.
 * @param onPlay toque simple (rápido y quieto) sobre el icono del cabezal: inicia la reproducción; nunca la pausa.
 * @param siblingSpans partes del archivo que usan OTROS clips del carril sobre el mismo archivo (p. ej. los trozos que deja
 *   Recorte con varios rangos): la regla las dibuja con sombra suave en vez de darlas por "fuera" ([detailSiblingOnlySpans]).
 */
@Composable
internal fun AudioClipDetailPanel(
    clip: AudioClip,
    signalColor: Color,
    playheadMs: Long,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onBackClose: () -> Unit,
    actions: AudioClipEditActions,
    loopEnabled: Boolean,
    onLoopChange: (Boolean) -> Unit,
    onSelectionChange: (List<PreviewSourceSelection>) -> Unit = {},
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    onPlay: () -> Unit = {},
    isPlaying: Boolean = false,
    siblingSpans: List<DetailSourceSpan> = emptyList(),
    modifier: Modifier = Modifier
) {
    // Registrado al abrir la ventana => es el último en el despachador => tiene prioridad.
    GuardedBackHandler(onBack = onBackClose)

    val appContext = LocalContext.current.applicationContext
    val waveform by produceState<AudioWaveform?>(initialValue = null, clip.sourceUri, clip.sourceDurationMs, clip.reversed) {
        value = null
        value = AudioWaveformAnalyzer.load(appContext, clip.sourceUri, clip.sourceDurationMs)
            ?.let { if (clip.reversed) it.reversedCopy() else it }
    }
    val fine by produceState<FineWaveform?>(initialValue = null, clip.sourceUri, clip.sourceDurationMs) {
        value = null
        value = AudioWaveformAnalyzer.loadFine(appContext, clip.sourceUri, clip.sourceDurationMs)
    }

    var zoom by remember(clip.id, clip.sourceUri) { mutableFloatStateOf(DETAIL_MIN_ZOOM) }
    var scrollPx by remember(clip.id, clip.sourceUri) { mutableFloatStateOf(0f) }
    var ampZoom by remember(clip.id, clip.sourceUri) { mutableFloatStateOf(DETAIL_MIN_AMP_ZOOM) }
    val scope = rememberCoroutineScope()
    val motion = remember { DetailMotion() }
    // Selección de rangos (estado de sesión de esta ventana: se descarta al cambiar de clip o cerrar).
    // Al pulsar REV NO se descarta: se refleja sobre el centro del archivo para seguir abarcando el mismo fragmento de señal
    // (ver el `LaunchedEffect(clip.reversed)` de más abajo).
    // INVARIANTE: sus claves deben ser SIEMPRE un superconjunto de las de `zoom`, `scrollPx` y `ampZoom` (ver
    // `gestureStateKey` más abajo): su identidad es la clave que reinicia los gestos cuando esos estados se recrean.
    val selection = remember(clip.id, clip.sourceUri) { AudioClipDetailSelectionState() }
    // Comunica la selección al editor (que la usa para acotar la reproducción). Solo al TERMINAR un gesto: avisar en cada
    // movimiento del dedo escribiría el estado de toda la pantalla decenas de veces por segundo.
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    fun commitSelection() {
        currentOnSelectionChange(selection.ranges.map { it.toPreviewSourceSelection() })
    }
    // Al descartarse esta selección (otro clip, otro archivo, o la ventana se cierra) el editor deja de acotar la reproducción.
    DisposableEffect(selection) { onDispose { currentOnSelectionChange(emptyList()) } }
    // Rangos sobre los que actúa "Recorte": todos los seleccionados que duran al menos el largo mínimo de un clip.
    // Solo cuentan los que tocan lo que SUENA en este clip (no la parte de otro trozo del mismo archivo).
    fun trimSelectionsNow(): List<DetailSelection> {
        val (from, to) = detailAudibleWindowMs(clip.trimStartMs, clip.clipLengthMs, clip.sourceDurationMs.toDouble()) ?: return emptyList()
        return detailTrimSelections(selection.ranges, MIN_AUDIO_CLIP_LENGTH_MS.toDouble(), from, to)
    }
    // Habilita "Recorte". `derivedStateOf` de un BOOLEANO: solo recompone la ventana cuando cambia el sí/no, no en cada
    // movimiento del dedo al arrastrar la selección (los rangos en sí solo se leen al pulsar el botón). Se rehace si el
    // tramo del clip cambia (un Recorte, estirarlo, otro archivo): `clip` no es estado observable por sí mismo.
    val canTrim by remember(selection, clip.trimStartMs, clip.clipLengthMs, clip.sourceDurationMs) {
        derivedStateOf { trimSelectionsNow().isNotEmpty() }
    }
    val density = LocalDensity.current

    // CLAVE DE LOS GESTOS DE LA ONDA. `pointerInput` ejecuta su bloque UNA vez y solo lo reinicia cuando cambia una clave;
    // ese bloque captura en su cierre los objetos de estado `zoom`, `scrollPx`, `ampZoom` y `selection`, que se RECREAN
    // cuando cambia (clip, archivo). Con claves que no cubrieran eso, tras "Reemplazar el audio" el pellizco, el
    // desplazamiento y el doble toque seguían escribiendo en estados ya descartados (la ventana dejaba de responder).
    // `selection` se recuerda con las MISMAS claves que los otros tres estados (invariante de arriba), así que su identidad
    // cambia siempre que cambia cualquiera de ellos: es la única clave que hace falta.
    val gestureStateKey: Any = selection

    BoxWithConstraints(
        modifier = modifier
            .background(DetailBackground)
            .clipToBounds()
            // Bloqueo de toques: en Compose un elemento SIN manejo de toques no los detiene — pasan a los
            // hermanos que quedan debajo (aquí: la lista de capas, el menú "Color", el playhead). Con un
            // `pointerInput` en la raíz, la ventana entera (huecos de la cabecera, onda, barra de vista
            // general) captura todo lo que cae dentro de ella. Los hijos con sus propios gestos siguen
            // funcionando: reciben el evento antes (hit-test de hijo a padre).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            }
    ) {
        // Ancho del EJE DE TIEMPO: el de la ventana menos un margen a cada lado (ver DETAIL_TIME_GUTTER_DP). Todo lo
        // que mide tiempo (zoom, desplazamiento, onda, regla, cabezal) trabaja con este ancho; las X de los gestos son
        // del lienzo completo, así que se les resta `gutterPx` antes de convertirlas en tiempo.
        val gutterPx = with(density) { DETAIL_TIME_GUTTER_DP.dp.toPx() }
        val viewportPx = (with(density) { maxWidth.toPx() } - 2f * gutterPx).coerceAtLeast(0f)
        val fineNow = fine
        val waveformNow = waveform
        // Duración del ARCHIVO completo: es lo que ocupa el ancho a 1×.
        val totalMs: Double = when {
            fineNow != null -> fineNow.durationMs
            clip.sourceDurationMs > 0L -> clip.sourceDurationMs.toDouble()
            else -> clip.clipLengthMs.coerceAtLeast(1L).toDouble()
        }
        // Zoom máximo = el que deja una muestra (o una cubeta, con la onda gruesa) en unos píxeles.
        val maxZoom = when {
            fineNow != null ->
                detailMaxZoom(totalMs, viewportPx, fineNow.sampleRateHz.toDouble(), DETAIL_FINE_PX_PER_SAMPLE_AT_MAX)
            waveformNow != null && waveformNow.bucketDurationMs > 0.0 ->
                detailMaxZoom(totalMs, viewportPx, 1_000.0 / waveformNow.bucketDurationMs, DETAIL_COARSE_PX_PER_BUCKET_AT_MAX)
            else -> detailMaxZoom(totalMs, viewportPx, 200.0, DETAIL_COARSE_PX_PER_BUCKET_AT_MAX)
        }
        val currentMaxZoom by rememberUpdatedState(maxZoom)
        // Los gestos viven en `pointerInput` con claves que no incluyen la duración: la leen siempre fresca desde aquí.
        val totalMsNow by rememberUpdatedState(totalMs)

        // REV: la onda se refleja y la selección con ella (mismo fragmento de señal, nueva posición) en lugar de perderse.
        // Se refleja solo ante un CAMBIO real de `reversed` (no al abrir ni al recomponer) y se avisa al editor para que el
        // bucle siga acotado al mismo fragmento.
        var shownReversed by remember(selection) { mutableStateOf(clip.reversed) }
        LaunchedEffect(clip.reversed) {
            if (shownReversed != clip.reversed) {
                shownReversed = clip.reversed
                selection.mirror(totalMsNow)
                commitSelection()
            }
        }

        val pxPerMs = if (viewportPx > 0f && totalMs > 0.0) (viewportPx * zoom / totalMs).toFloat() else 0f
        val scrollMs: Double = if (pxPerMs > 0f) scrollPx / pxPerMs.toDouble() else 0.0

        // Ventana unificada: cabecera con las acciones del clip, la onda (con su cuadrícula y datos) ocupando todo el alto
        // disponible y, fija al pie de la ventana, la barra de vista general. Una sola ventana, un solo clip, un solo estado.
        Column(modifier = Modifier.fillMaxSize()) {
            AudioClipDetailHeader(
                reversed = clip.reversed,
                loopEnabled = loopEnabled,
                canTrim = canTrim,
                selectCount = selection.maxCount,
                accent = signalColor,
                onReplaceSource = { actions.onReplaceSource(clip.id) },
                onSelectCountChange = { count ->
                    selection.changeMaxCount(count)
                    commitSelection()
                },
                onToggleReverse = { actions.onToggleReverse(clip.id) },
                onTrim = {
                    val ranges = trimSelectionsNow()
                    // Si se aplicó, la selección ya cumplió su función: se descarta y el editor deja de acotar la vista previa a ella.
                    if (ranges.isNotEmpty() && actions.onTrimToSelections(clip.id, ranges.map { it.toPreviewSourceSelection() })) {
                        selection.clear()
                        commitSelection()
                    }
                },
                onLoopChange = onLoopChange,
                onClose = onClose
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // La onda con zoom de amplitud se dibuja más alta que su zona: sin este recorte
                    // invadía la cabecera (Compose no recorta a los hijos por defecto).
                    .clipToBounds()
                    // Reclama al sistema los gestos de borde en una franja central de la zona de la onda
                    // (los pulgares de un pellizco ya no disparan "atrás" ahí). La barra de abajo no la necesita.
                    .systemGestureExclusion { coordinates ->
                        val bandPx = with(density) { DETAIL_GESTURE_EXCLUSION_HEIGHT_DP.dp.toPx() }
                        val heightPx = coordinates.size.height.toFloat()
                        val bandHeight = minOf(bandPx, heightPx)
                        val top = (heightPx - bandHeight) / 2f
                        Rect(0f, top, coordinates.size.width.toFloat(), top + bandHeight)
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Toques sobre la onda: el simple deselecciona y el doble hace zoom animado. Ninguno mueve el
                        // cursor: el cursor y su cabezal se gobiernan únicamente desde la regla de tiempo (ver
                        // AudioClipDetailPlayhead).
                        .pointerInput(gestureStateKey, viewportPx, gutterPx) {
                            detectImmediateTapGestures(
                                // Un toque simple deselecciona (como en WavePad, donde tocar solo sitúa la posición).
                                onTap = {
                                    selection.clearForTap()
                                    commitSelection()
                                },
                                onDoubleTap = { offset ->
                                    // El primer toque del doble toque ya deseleccionó: era el zoom, se restituye.
                                    selection.restoreAfterDoubleTap()
                                    commitSelection()
                                    motion.job?.cancel()
                                    val startZoom = zoom
                                    val zoomingOut = startZoom > 1.05f
                                    val target = if (zoomingOut) DETAIL_MIN_ZOOM else minOf(DETAIL_DOUBLE_TAP_ZOOM, currentMaxZoom)
                                    if (zoomingOut) ampZoom = DETAIL_MIN_AMP_ZOOM
                                    motion.job = scope.launch {
                                        var last = startZoom
                                        Animatable(0f).animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing)) {
                                            val z = detailInterpolateZoom(startZoom, target, value)
                                            scrollPx = detailScrollAfterZoom(scrollPx, last, z, offset.x - gutterPx, 0f, viewportPx)
                                            last = z
                                            zoom = z
                                        }
                                    }
                                }
                            )
                        }
                        // Selección (un dedo), pellizco (tiempo / amplitud), desplazamiento (dos dedos) e inercia.
                        .pointerInput(gestureStateKey, viewportPx, gutterPx) {
                            val edgeHitHalfWidthPx = DETAIL_SELECTION_EDGE_HIT_HALF_WIDTH_DP.dp.toPx()
                            // Escala viva (zoom y duración cambian durante el gesto); X del lienzo → X del eje de tiempo.
                            fun livePxPerMs(): Float =
                                if (viewportPx > 0f && totalMsNow > 0.0) (viewportPx * zoom / totalMsNow).toFloat() else 0f
                            detectDetailGestures(
                                onStart = {
                                    motion.job?.cancel()
                                    motion.job = null
                                },
                                onSelectStart = { downX ->
                                    selection.beginDrag(downX - gutterPx, scrollPx, livePxPerMs(), totalMsNow, edgeHitHalfWidthPx)
                                },
                                onSelectMove = { x ->
                                    selection.drag(x - gutterPx, scrollPx, livePxPerMs(), totalMsNow)
                                },
                                onSelectEnd = { commitSelection() },
                                onTransform = { centroidX, panX, zoomX, zoomY ->
                                    val oldZoom = zoom
                                    val newZoom = (oldZoom * zoomX).coerceIn(DETAIL_MIN_ZOOM, currentMaxZoom)
                                    scrollPx = detailScrollAfterZoom(scrollPx, oldZoom, newZoom, centroidX - gutterPx, panX, viewportPx)
                                    zoom = newZoom
                                    if (zoomY != 1f) {
                                        ampZoom = (ampZoom * zoomY).coerceIn(DETAIL_MIN_AMP_ZOOM, DETAIL_MAX_AMP_ZOOM)
                                    }
                                },
                                onEnd = { velocityX ->
                                    if (abs(velocityX) > DETAIL_FLING_MIN_VELOCITY && zoom > DETAIL_MIN_ZOOM + 0.01f) {
                                        val maxScroll = (viewportPx * zoom - viewportPx).coerceAtLeast(0f)
                                        motion.job = scope.launch {
                                            val scroller = Animatable(scrollPx)
                                            scroller.updateBounds(0f, maxScroll)
                                            // El contenido sigue al dedo: dedo a la derecha => el scroll disminuye.
                                            scroller.animateDecay(-velocityX, exponentialDecay<Float>()) {
                                                scrollPx = value
                                            }
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // Señal ORIGINAL: archivo completo, sin loop, desde el inicio del archivo.
                    if (fineNow != null) {
                        AudioFineWaveformCanvas(
                            fine = fineNow,
                            reversed = clip.reversed,
                            scrollMs = scrollMs,
                            pxPerMs = pxPerMs,
                            ampZoom = ampZoom,
                            signalColor = signalColor,
                            dimmed = clip.muted,
                            modifier = Modifier.fillMaxSize().padding(DetailWaveformPadding)
                        )
                    } else {
                        // Mientras carga la onda fina (o si el audio es muy largo): la onda del carril, con el MISMO
                        // desplazamiento continuo y el mismo zoom de amplitud que la fina (si no, el pellizco vertical
                        // no tenía efecto con audios largos y la onda saltaba hasta 1 px al desplazarla).
                        AudioWaveformCanvas(
                            waveform = waveformNow,
                            trimStartMs = scrollMs,
                            pxPerMs = pxPerMs,
                            signalColor = signalColor,
                            dimmed = clip.muted,
                            loop = false,
                            trueAmplitude = true,
                            amplitudeZoom = ampZoom,
                            modifier = Modifier.fillMaxSize().padding(DetailWaveformPadding)
                        )
                    }
                    // Regla de tiempo (estática) con el tramo usado por el clip y la cuadrícula; encima, el cabezal.
                    // Ya NO hay una línea permanente del cursor: en reposo vive solo el cabezal en la regla, y la
                    // línea se despliega hacia abajo únicamente mientras se sostiene (ver AudioClipDetailPlayhead).
                    AudioClipDetailRuler(
                        clip = clip,
                        signalColor = signalColor,
                        totalMs = totalMs,
                        scrollPx = scrollPx,
                        pxPerMs = pxPerMs,
                        viewportPx = viewportPx,
                        gutterPx = gutterPx,
                        siblingSpans = siblingSpans
                    )
                    // Rangos seleccionados: relleno sobre la onda, banda sobre la regla y bordes (translúcida, encima de la regla y
                    // bajo el cabezal). Solo repinta esta capa al arrastrar.
                    AudioClipDetailSelectionLayer(
                        state = selection,
                        scrollPx = scrollPx,
                        pxPerMs = pxPerMs,
                        viewportPx = viewportPx,
                        gutterPx = gutterPx
                    )
                    AudioClipDetailPlayhead(
                        clip = clip,
                        playheadMs = playheadMs,
                        totalMs = totalMs,
                        zoom = zoom,
                        scrollPx = scrollPx,
                        pxPerMs = pxPerMs,
                        viewportPx = viewportPx,
                        gutterPx = gutterPx,
                        onSeek = onSeek,
                        onScrollPxChange = { scrollPx = it },
                        onScrubStart = onScrubStart,
                        onScrubEnd = onScrubEnd,
                        onPlay = onPlay,
                        isPlaying = isPlaying
                    )
                }

                // Nombre y datos, pequeños y translúcidos (abajo a la izquierda).
                Text(
                    text = buildString {
                        append(clip.label)
                        append(" · ")
                        append(formatDetailTimeMs(totalMs.toLong(), withMillis = true))
                        if (clip.loop) append(" · Loop")
                        if (clip.reversed) append(" · Reverse")
                        if (clip.muted) append(" · Silenciado")
                        append(" · Zoom ")
                        append(if (zoom < 10f) String.format(Locale.US, "%.1f", zoom) else String.format(Locale.US, "%.0f", zoom))
                        append("×")
                        if (ampZoom > 1.05f) append(String.format(Locale.US, " · Amp %.1f×", ampZoom))
                    },
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 6.dp, end = 10.dp)
                )
            }
            // Vista general: el archivo entero con el recuadro de lo que muestra la onda; mueve y acerca con un dedo.
            val restingHead = detailRestingHeadOf(clip, playheadMs, totalMs)
            AudioClipDetailOverview(
                waveform = waveformNow,
                totalMs = totalMs,
                zoom = zoom,
                scrollPx = scrollPx,
                viewportPx = viewportPx,
                gutterPx = gutterPx,
                maxZoom = maxZoom,
                selection = selection,
                playheadSourceMs = restingHead?.sourceMs,
                playheadInsideClip = restingHead?.insideClip ?: false,
                accent = signalColor,
                onGestureStart = {
                    motion.job?.cancel()
                    motion.job = null
                },
                onViewChange = { newZoom, newScrollPx ->
                    zoom = newZoom
                    scrollPx = newScrollPx
                }
            )
        }
    }
}

/**
 * Franja inferior reservada al rótulo «nombre · duración · zoom» (11 sp + 6 dp de margen). Sin ella el rótulo se
 * dibujaba SOBRE la parte baja de la onda y la escala completa (0 dBFS) tocaba el borde: un master a tope parecía cortado.
 */
private const val DETAIL_INFO_STRIP_DP = 24

/** La onda ocupa el eje de tiempo: bajo la regla, sobre el rótulo y dentro de los márgenes laterales. */
private val DetailWaveformPadding = PaddingValues(
    start = DETAIL_TIME_GUTTER_DP.dp,
    top = DETAIL_RULER_HEIGHT_DP.dp,
    end = DETAIL_TIME_GUTTER_DP.dp,
    bottom = DETAIL_INFO_STRIP_DP.dp
)
