package com.yeivikas.olyzecs.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.audio.AUDIO_VOLUME_SILENT_TEXT
import com.yeivikas.olyzecs.engine.audio.MAX_AUDIO_VOLUME
import com.yeivikas.olyzecs.engine.audio.audioFaderFractionToVolume
import com.yeivikas.olyzecs.engine.audio.audioVolumeAfterDbDelta
import com.yeivikas.olyzecs.engine.audio.audioVolumeToFaderFraction
import com.yeivikas.olyzecs.engine.audio.audioVolumeDbText
import com.yeivikas.olyzecs.engine.audio.audioVolumeReadsAsZero
import com.yeivikas.olyzecs.engine.audio.DEFAULT_AUDIO_PAN
import com.yeivikas.olyzecs.engine.audio.UNITY_AUDIO_VOLUME
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * Panorama y volumen del audio, como en FL Studio Mobile: dos deslizadores finos en la barra flotante de
 * cada capa de audio (a la derecha de las acciones), sin ventanas aparte.
 *
 * Doble toque en cualquiera de los dos deslizadores: vuelve a su valor por defecto (panorama al centro, volumen
 * al 100 %), como el reseteo rápido de los controles de FL Studio. Ver [AudioMiniSlider] y [MiniSliderTapTracker].
 *
 * Quién edita qué: estos controles son MAESTROS de la capa. Panorama, volumen y el ícono de silencio actúan sobre
 * TODOS los clips del carril a la vez (`setAudioTrackPan` / `setAudioTrackVolume` / `tapAudioTrackVolumeIcon` del
 * ViewModel, cada gesto = un solo paso de deshacer). Los controles por clip son otra cosa y vendrán aparte. Los
 * valores que muestran son los de la capa (`AudioTrack.masterPan` / `masterVolume`), no los de un clip elegido, así
 * que no saltan al cambiar la selección ni el cabezal.
 */

// ---------------------------------------------------------------------------
// Matemática pura (sin Compose; probada en AudioTrackMixSlidersTest)
// ---------------------------------------------------------------------------

/** Posición normalizada (0…1) de [value] dentro de [range]; un rango degenerado o un valor no finito dan 0. */
internal fun audioMiniSliderFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f || !value.isFinite()) return 0f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

/**
 * Valor que corresponde a un toque en [x] (px, desde el borde izquierdo del deslizador) sobre un deslizador de
 * [widthPx] de ancho. El recorrido útil deja [thumbRadiusPx] a cada lado para que el tirador entre entero.
 * Un toque fuera del recorrido se acota a los extremos; sin recorrido (ancho ≤ 2 radios) devuelve el mínimo.
 */
internal fun audioMiniSliderValueAt(
    x: Float,
    widthPx: Float,
    thumbRadiusPx: Float,
    range: ClosedFloatingPointRange<Float>
): Float {
    val fraction = audioMiniSliderFractionAt(x, widthPx, thumbRadiusPx)
    return range.start + fraction * (range.endInclusive - range.start)
}

/**
 * Posición normalizada (0…1) de un toque en [x] (px, desde el borde izquierdo) sobre un deslizador de [widthPx] de
 * ancho, con el mismo recorrido útil que [audioMiniSliderValueAt]. Es la posición del DEDO, independiente de la
 * escala del valor: cada deslizador la convierte a valor con su curva ([MiniSliderTaper]). Sin recorrido o con una
 * posición no finita, 0.
 */
internal fun audioMiniSliderFractionAt(x: Float, widthPx: Float, thumbRadiusPx: Float): Float {
    val travel = widthPx - 2f * thumbRadiusPx
    if (travel <= 0f || !x.isFinite()) return 0f
    return ((x - thumbRadiusPx) / travel).coerceIn(0f, 1f)
}

/**
 * Radio efectivo del imán, en px: [snapRadiusPx] acotado a [MINI_SLIDER_SNAP_MAX_TRAVEL_FRACTION] del [travelPx]
 * (el recorrido útil). Sin recorrido o con un radio no finito, 0 (sin imán).
 */
internal fun audioMiniSliderSnapRadiusPx(snapRadiusPx: Float, travelPx: Float): Float {
    if (travelPx <= 0f || !travelPx.isFinite() || !snapRadiusPx.isFinite() || snapRadiusPx <= 0f) return 0f
    return minOf(snapRadiusPx, travelPx * MINI_SLIDER_SNAP_MAX_TRAVEL_FRACTION)
}

/**
 * `true` si [value] cae dentro del imán de [snapValue]: su distancia, medida en px sobre el recorrido
 * ([travelPx]), es de [radiusPx] o menos. Un rango degenerado, un valor no finito o un radio nulo dan `false`.
 */
internal fun audioMiniSliderInSnapZone(
    value: Float,
    snapValue: Float,
    travelPx: Float,
    radiusPx: Float,
    range: ClosedFloatingPointRange<Float>
): Boolean {
    val span = range.endInclusive - range.start
    if (span <= 0f || travelPx <= 0f || radiusPx <= 0f || !value.isFinite() || !snapValue.isFinite()) return false
    return kotlin.math.abs(value - snapValue) / span * travelPx <= radiusPx
}

/**
 * Igual que [audioMiniSliderInSnapZone] pero con posiciones normalizadas (0…1) en vez de valores: la distancia en px
 * entre [fraction] y [snapFraction] sobre el recorrido ([travelPx]) es de [radiusPx] o menos. Es la que usa el gesto,
 * porque el imán debe medirse en lo que se VE (posición del tirador) sea cual sea la curva del deslizador.
 */
internal fun audioMiniSliderFractionInSnapZone(
    fraction: Float,
    snapFraction: Float,
    travelPx: Float,
    radiusPx: Float
): Boolean {
    if (travelPx <= 0f || radiusPx <= 0f || !fraction.isFinite() || !snapFraction.isFinite()) return false
    return abs(fraction - snapFraction) * travelPx <= radiusPx
}

/**
 * Imán: si [value] está en la zona de [snapValue] (la rayita del deslizador: el centro del panorama o el 100 % del
 * volumen) devuelve exactamente [snapValue]; si no, [value] intacto. Así, al acercar el dedo a la marca el tirador
 * se "pega" a ella y para soltarlo hay que alejarse más que el radio.
 */
internal fun audioMiniSliderSnap(
    value: Float,
    snapValue: Float,
    travelPx: Float,
    radiusPx: Float,
    range: ClosedFloatingPointRange<Float>
): Float = if (audioMiniSliderInSnapZone(value, snapValue, travelPx, radiusPx, range)) snapValue else value

/**
 * Curva de un deslizador: cómo se reparte el recorrido (posición 0…1) entre los valores. Panorama: lineal
 * ([linearMiniSliderTaper]). Volumen: curva de fader en dB ([AudioVolumeFaderTaper]), para que el tirador y el número
 * en dB vayan sincronizados. [toFraction] y [toValue] son inversas una de la otra.
 */
internal class MiniSliderTaper(
    val toFraction: (Float) -> Float,
    val toValue: (Float) -> Float
)

/** Curva lineal sobre [range]: la de siempre (panorama). */
internal fun linearMiniSliderTaper(range: ClosedFloatingPointRange<Float>): MiniSliderTaper = MiniSliderTaper(
    toFraction = { audioMiniSliderFraction(it, range) },
    toValue = { range.start + it.coerceIn(0f, 1f) * (range.endInclusive - range.start) }
)

/**
 * Curva del deslizador de volumen: leyes de fader en dB (`AudioVolumeFader`). El extremo izquierdo es «-inf», el
 * 0 dB (rayita) queda al 85 % del recorrido y el extremo derecho es el volumen máximo.
 */
internal val AudioVolumeFaderTaper = MiniSliderTaper(
    toFraction = ::audioVolumeToFaderFraction,
    toValue = ::audioFaderFractionToVolume
)

/**
 * Tramo relleno de la barra como par de fracciones `(desde, hasta)` con `desde <= hasta`. Normal: de la
 * izquierda hasta [fraction]. [centered] (panorama): del centro (0.5) hasta [fraction], hacia el lado al que gira.
 */
internal fun audioMiniSliderFill(fraction: Float, centered: Boolean): Pair<Float, Float> {
    val f = fraction.coerceIn(0f, 1f)
    return if (centered) minOf(0.5f, f) to maxOf(0.5f, f) else 0f to f
}

/** Texto de estado del volumen para accesibilidad (porcentaje del volumen original). */
internal fun audioVolumeLabel(volume: Float): String = "Volumen: ${(volume * 100f).roundToInt()}%"

/**
 * `true` si [volume] se lee como 0 % (el mismo redondeo que [audioVolumeLabel]): ahí el ícono del volumen pasa a
 * «sin sonido». Un valor no finito cuenta como sin sonido.
 */
internal fun audioVolumeIsMuted(volume: Float): Boolean = audioVolumeReadsAsZero(volume)

/** Descripción accesible de la acción del ícono del volumen maestro: lo que HARÁ el próximo toque. */
internal fun audioVolumeIconActionLabel(masterSilent: Boolean): String =
    if (masterSilent) "Activar sonido" else "Silenciar"

/** Descripción accesible del valor numérico del volumen: dice el nivel en decibelios, que al tocarlo se puede escribir otro y que deslizarlo en vertical lo ajusta. */
internal fun audioVolumeReadoutDescription(volume: Float): String {
    val text = audioVolumeDbText(volume)
    return if (text == AUDIO_VOLUME_SILENT_TEXT) {
        "Volumen mínimo, sin sonido. Toca para escribir un valor o desliza hacia arriba o abajo para ajustarlo"
    } else {
        "Volumen $text decibelios. Toca para escribir un valor o desliza hacia arriba o abajo para ajustarlo"
    }
}

/**
 * Eje de un arrastre sobre el valor numérico del volumen, decidido UNA vez al superar la holgura táctil y mantenido
 * hasta soltar (como el bloqueo de dirección de un scroll): [VERTICAL] ajusta el nivel; [HORIZONTAL] se ignora por
 * completo (ni cambia el valor ni cuenta como toque al soltar).
 */
internal enum class ReadoutDragAxis { UNDECIDED, VERTICAL, HORIZONTAL }

/**
 * Dominio mínimo del movimiento vertical sobre el horizontal para que el gesto cuente como vertical
 * (|dy| > |dx| · este factor, un cono de ≈ ±40° alrededor de la vertical). Un arrastre lateral, aunque el dedo se
 * desvíe un poco hacia arriba o abajo, queda fuera.
 */
internal const val READOUT_DRAG_VERTICAL_DOMINANCE = 1.2f

/**
 * Eje del arrastre cuando el dedo lleva ([dxPx], [dyPx]) desde donde bajó. Mientras no supere [slopPx] (distancia en
 * línea recta) sigue sin decidirse; al superarla se clasifica por dominio ([READOUT_DRAG_VERTICAL_DOMINANCE]).
 * Valores no finitos no deciden nada.
 */
internal fun readoutDragAxis(dxPx: Float, dyPx: Float, slopPx: Float): ReadoutDragAxis {
    if (!dxPx.isFinite() || !dyPx.isFinite() || !slopPx.isFinite()) return ReadoutDragAxis.UNDECIDED
    if (hypot(dxPx, dyPx) <= slopPx) return ReadoutDragAxis.UNDECIDED
    return if (abs(dyPx) > abs(dxPx) * READOUT_DRAG_VERTICAL_DOMINANCE) {
        ReadoutDragAxis.VERTICAL
    } else {
        ReadoutDragAxis.HORIZONTAL
    }
}

/**
 * `true` si [volume] está en un extremo del arrastre: silencio absoluto (mínimo) o volumen máximo. Ahí el gesto
 * re-ancla su origen al dedo, para que al invertir el sentido el valor responda al instante, sin zona muerta.
 */
internal fun audioVolumeIsAtDragLimit(volume: Float): Boolean = volume <= 0f || volume >= MAX_AUDIO_VOLUME

/** Valor por defecto del panorama: centro, sin procesar. */
internal const val AUDIO_PAN_DEFAULT = DEFAULT_AUDIO_PAN

/** Valor por defecto del volumen: el original (100 %), la rayita de referencia del deslizador. */
internal const val AUDIO_VOLUME_DEFAULT = UNITY_AUDIO_VOLUME

/**
 * Memoria del último toque de un deslizador, para reconocer el DOBLE TOQUE que lo resetea a su valor por defecto.
 *
 * Un «toque» es un gesto que termina sin que el dedo se aleje más que la holgura táctil. El segundo toque cuenta
 * como doble si baja dentro de la ventana de [isDoubleTap] (mismo criterio que el resto de la app) y cerca del
 * primero. Un arrastre, una cancelación o un toque que ya completó un doble reinician la memoria, de modo que tres
 * toques seguidos son doble + toque suelto, no doble + doble. Pura (sin Compose): se prueba en JUnit.
 */
internal class MiniSliderTapTracker {
    private var previousUpMs = NO_PREVIOUS_TAP_MS
    private var previousX = 0f
    private var previousY = 0f

    /** `true` si un toque que baja en ([downX], [downY]) a [downMs] completa un doble toque con el anterior. */
    fun completesDoubleTap(
        downMs: Long,
        downX: Float,
        downY: Float,
        minIntervalMs: Long,
        timeoutMs: Long,
        slopPx: Float
    ): Boolean = isDoubleTap(
        previousUpMs = previousUpMs,
        previousX = previousX,
        previousY = previousY,
        downMs = downMs,
        downX = downX,
        downY = downY,
        minIntervalMs = minIntervalMs,
        timeoutMs = timeoutMs,
        slopPx = slopPx
    )

    /** Registra un toque simple terminado en [upMs] (candidato a primer toque de un doble). */
    fun recordTap(upMs: Long, x: Float, y: Float) {
        previousUpMs = upMs
        previousX = x
        previousY = y
    }

    /** Olvida el toque anterior. */
    fun clear() {
        previousUpMs = NO_PREVIOUS_TAP_MS
    }

    private companion object {
        const val NO_PREVIOUS_TAP_MS = -1L
    }
}

/** Texto de estado del panorama para accesibilidad: centro, o el porcentaje hacia cada lado. */
internal fun audioPanLabel(pan: Float): String = when {
    pan == 0f -> "Pan: centro"
    pan < 0f -> "Pan: ${(-pan * 100f).roundToInt()}% izquierda"
    else -> "Pan: ${(pan * 100f).roundToInt()}% derecha"
}

// ---------------------------------------------------------------------------
// Interfaz
// ---------------------------------------------------------------------------

private val MINI_SLIDER_HEIGHT = 26.dp
private val MINI_SLIDER_THUMB_RADIUS = 7.dp
private val MINI_SLIDER_TRACK_THICKNESS = 3.dp

/** Distancia máxima entre los dos toques de un doble toque sobre un deslizador. */
private val MINI_SLIDER_DOUBLE_TAP_SLOP = 48.dp

/** Alcance del imán al valor por defecto: distancia (a cada lado de la marca) dentro de la cual el dedo "encaja". */
private val MINI_SLIDER_SNAP_RADIUS = 5.dp

/** Tope del imán como fracción del recorrido: en un deslizador corto (panorama) no puede comerse medio recorrido. */
internal const val MINI_SLIDER_SNAP_MAX_TRAVEL_FRACTION = 0.1f

/** Ancho de cada deslizador cuando van en fila (panel lateral): panorama corto, volumen largo, como en FL. */
private val PAN_SLIDER_WIDTH = 52.dp

/**
 * Separación entre cada oído y el deslizador del panorama en el panel lateral. El panorama es UNA sola función (oído L,
 * deslizador, oído R), así que va compacto: el hueco visible entre oído y pista ya incluye el margen del tirador
 * ([MINI_SLIDER_THUMB_RADIUS], 7 dp), por eso aquí no se suma más espacio. Subirlo separa el grupo.
 */
private val PAN_GROUP_SPACING = 0.dp

/**
 * Separación entre el grupo del panorama y el del volumen en el panel lateral. Mayor que la de dentro de cada grupo
 * ([PAN_GROUP_SPACING], [VOLUME_GROUP_SPACING]) a propósito: así se leen como dos funciones distintas, sin gastar ancho.
 */
private val MIX_GROUPS_SPACING = 4.dp

/** Separación entre ícono de volumen, deslizador y valor numérico en el panel lateral (el deslizador ya trae 7 dp de margen). */
private val VOLUME_GROUP_SPACING = 0.dp
private val VOLUME_SLIDER_WIDTH = 80.dp

/** Alto de la fila del volumen en el bloque apilado del pie (columna de capas, de ancho fijo). */
private val STACKED_VOLUME_HEIGHT = 22.dp

/** Alto de la fila de oídos y del deslizador del panorama en el bloque apilado: en 64 dp de ancho no caben los oídos a los lados. */
private val STACKED_EARS_HEIGHT = 22.dp
private val STACKED_PAN_SLIDER_HEIGHT = 20.dp

/** Separación vertical entre filas del bloque apilado. */
private val STACKED_SPACING = 4.dp

/** Tamaño del ícono del volumen junto a su deslizador: en fila (panel lateral) y en el bloque apilado del pie. */
private val VOLUME_ICON_SIZE = 16.dp
private val STACKED_VOLUME_ICON_SIZE = 12.dp

/** Área tocable del ícono del volumen (el dibujo es más chico): en fila y en el bloque apilado del pie. */
private val VOLUME_TOUCH_SIZE = 36.dp
private val STACKED_VOLUME_TOUCH_SIZE = 22.dp

/** Opacidad del deslizador de volumen mientras el clip está silenciado (el nivel se conserva, solo se atenúa). */
private const val MUTED_SLIDER_ALPHA = 0.45f

/** Tamaño del ícono del oído: en fila (panel lateral, con letra debajo) y en el bloque apilado del pie. */
private val EAR_ICON_SIZE = 14.dp
private val STACKED_EAR_ICON_SIZE = 11.dp

/** Valor numérico del volumen: caja a la derecha del deslizador (panel lateral) o fila propia bajo él (bloque apilado). */
private val VOLUME_READOUT_WIDTH = 36.dp
private val VOLUME_READOUT_HEIGHT = 20.dp
private val STACKED_VOLUME_READOUT_HEIGHT = 20.dp

/**
 * Sensibilidad del arrastre vertical sobre el valor numérico del volumen: dp de recorrido del dedo por cada dB.
 * 4 dp/dB recorre todo el rango (≈ 63,5 dB) en ≈ 254 dp —cabe en un teléfono— y deja las décimas de dB al alcance
 * del dedo (≈ 0,4 dp por décima).
 */
private val VOLUME_READOUT_DRAG_DP_PER_DB = 4.dp

/** Salto (dB) de las acciones de accesibilidad «Subir»/«Bajar» del valor numérico del volumen. */
private const val VOLUME_READOUT_A11Y_STEP_DB = 1f

/** Alto del bloque apilado ([AudioTrackMixSliders] con `stacked = true`), sin su margen. */
internal val AUDIO_MIX_STACK_HEIGHT =
    STACKED_EARS_HEIGHT + STACKED_PAN_SLIDER_HEIGHT + STACKED_VOLUME_HEIGHT + STACKED_VOLUME_READOUT_HEIGHT +
        STACKED_SPACING * 3

/** Margen vertical del bloque apilado dentro del acordeón del pie (arriba y abajo). */
internal val AUDIO_MIX_STRIP_PADDING = 4.dp

/**
 * Deslizador fino con tirador redondo. Responde desde el primer toque (sin umbral de arrastre): un toque coloca el
 * valor donde cae el dedo y arrastrar lo sigue. El alto lo fija quien lo usa por [modifier].
 *
 * @param centered rellena desde el centro hacia el valor (panorama) en vez de desde la izquierda.
 * @param markerValue valor de referencia que se marca con una rayita (el 100 % del volumen); `null` = sin marca.
 * @param taper curva que reparte el recorrido entre los valores; `null` = lineal sobre [valueRange]. El volumen usa
 *   [AudioVolumeFaderTaper] (en dB) para que tirador y número coincidan.
 * @param defaultValue valor al que vuelve con un doble toque (y con la acción de accesibilidad «Restablecer»), y al que
 *   el deslizador se pega como un imán cuando el dedo se acerca a su rayita ([audioMiniSliderSnap]).
 *   Un doble toque NO deja pasar el primer toque como salto: el segundo toque se resuelve como reseteo, salvo que
 *   el dedo se arrastre, en cuyo caso se comporta como un arrastre normal.
 */
@Composable
private fun AudioMiniSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    contentDescription: String,
    stateText: String,
    defaultValue: Float,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    markerValue: Float? = null,
    taper: MiniSliderTaper? = null,
    color: Color = Color.White
) {
    val activeTaper = taper ?: remember(valueRange) { linearMiniSliderTaper(valueRange) }
    val thumbRadiusPx = with(LocalDensity.current) { MINI_SLIDER_THUMB_RADIUS.toPx() }
    val thickness = with(LocalDensity.current) { MINI_SLIDER_TRACK_THICKNESS.toPx() }
    val doubleTapSlopPx = with(LocalDensity.current) { MINI_SLIDER_DOUBLE_TAP_SLOP.toPx() }
    val snapRadiusPx = with(LocalDensity.current) { MINI_SLIDER_SNAP_RADIUS.toPx() }
    val haptic = LocalHapticFeedback.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentDefaultValue by rememberUpdatedState(defaultValue.coerceIn(valueRange))
    val tapTracker = remember { MiniSliderTapTracker() }

    Box(
        modifier = modifier
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
                setProgress { target ->
                    currentOnValueChange(target.coerceIn(valueRange))
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Restablecer al valor por defecto") {
                        currentOnValueChange(currentDefaultValue)
                        true
                    }
                )
            }
            .pointerInput(valueRange, activeTaper, thumbRadiusPx, doubleTapSlopPx, snapRadiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val widthPx = size.width.toFloat()
                    val touchSlopPx = viewConfiguration.touchSlop
                    // Imán al valor por defecto (la rayita): el valor se pega a ella cuando el dedo se acerca, y
                    // vibra suave al encajar (solo al ENTRAR en la zona, no mientras se queda dentro).
                    val travelPx = widthPx - 2f * thumbRadiusPx
                    val radiusPx = audioMiniSliderSnapRadiusPx(snapRadiusPx, travelPx)
                    var inSnapZone = false
                    fun placeAt(x: Float) {
                        // El dedo da una POSICIÓN (0…1); la curva del deslizador la vuelve valor. El imán se mide en
                        // posición (lo que se ve), no en valor, para que funcione igual con cualquier curva.
                        val rawFraction = audioMiniSliderFractionAt(x, widthPx, thumbRadiusPx)
                        val snapValue = currentDefaultValue
                        val nowInZone = audioMiniSliderFractionInSnapZone(
                            fraction = rawFraction,
                            snapFraction = activeTaper.toFraction(snapValue),
                            travelPx = travelPx,
                            radiusPx = radiusPx
                        )
                        if (nowInZone && !inSnapZone) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        inSnapZone = nowInZone
                        currentOnValueChange(if (nowInZone) snapValue else activeTaper.toValue(rawFraction))
                    }
                    val isSecondTap = tapTracker.completesDoubleTap(
                        downMs = down.uptimeMillis,
                        downX = down.position.x,
                        downY = down.position.y,
                        minIntervalMs = viewConfiguration.doubleTapMinTimeMillis,
                        timeoutMs = viewConfiguration.doubleTapTimeoutMillis,
                        slopPx = doubleTapSlopPx
                    )
                    // Primer toque (o toque suelto): coloca el valor donde cae el dedo, como siempre. Segundo toque
                    // de un posible doble: se espera a ver si es reseteo (suelta sin moverse) o arrastre.
                    if (!isSecondTap) {
                        placeAt(down.position.x)
                    }
                    var moved = false
                    val completed = drag(down.id) { change ->
                        change.consume()
                        if (!moved &&
                            hypot(change.position.x - down.position.x, change.position.y - down.position.y) > touchSlopPx
                        ) {
                            moved = true
                        }
                        if (!isSecondTap || moved) {
                            placeAt(change.position.x)
                        }
                    }
                    when {
                        // Soltó sin moverse: es un toque.
                        completed && !moved && isSecondTap -> {
                            tapTracker.clear()
                            currentOnValueChange(currentDefaultValue)
                        }
                        completed && !moved -> {
                            val upMs = currentEvent.changes.firstOrNull { it.id == down.id }?.uptimeMillis
                                ?: down.uptimeMillis
                            tapTracker.recordTap(upMs, down.position.x, down.position.y)
                        }
                        // Arrastre o cancelación: no cuenta como toque.
                        else -> tapTracker.clear()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val travel = size.width - 2f * thumbRadiusPx
            if (travel <= 0f) return@Canvas
            val centerY = size.height / 2f
            val trackTop = centerY - thickness / 2f
            val corner = CornerRadius(thickness / 2f, thickness / 2f)

            // Pista completa, tenue.
            drawRoundRect(
                color = color.copy(alpha = 0.28f),
                topLeft = Offset(thumbRadiusPx, trackTop),
                size = Size(travel, thickness),
                cornerRadius = corner
            )

            // Tramo relleno.
            val fraction = activeTaper.toFraction(value)
            val (fillFrom, fillTo) = audioMiniSliderFill(fraction, centered)
            val fillWidth = (fillTo - fillFrom) * travel
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(thumbRadiusPx + fillFrom * travel, trackTop),
                    size = Size(fillWidth, thickness),
                    cornerRadius = corner
                )
            }

            // Marca de referencia (100 % del volumen) o, en el panorama, la del centro.
            val markerFraction = if (centered) 0.5f else markerValue?.let { activeTaper.toFraction(it) }
            if (markerFraction != null) {
                val markerX = thumbRadiusPx + markerFraction * travel
                drawLine(
                    color = color.copy(alpha = 0.7f),
                    start = Offset(markerX, centerY - 6.dp.toPx()),
                    end = Offset(markerX, centerY + 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Tirador.
            drawCircle(
                color = color,
                radius = thumbRadiusPx,
                center = Offset(thumbRadiusPx + fraction * travel, centerY)
            )
        }
    }
}

/**
 * Panorama y volumen MAESTROS de una capa de audio, listos para su barra flotante. Reciben los valores de la capa
 * (ver `AudioTrack.masterPan` / `masterVolume`) y avisan los cambios; quien los usa los aplica a todos los clips.
 *
 * El panorama lleva un oído a cada extremo —izquierdo con «L», derecho con «R», debajo del ícono—, que dicen a qué
 * canal va cada lado de la barra.
 *
 * @param masterSilent la capa se lee como «sin sonido» (todos sus clips silenciados o a 0 %, ver
 *   `masterReadsAsSilent`): el ícono del volumen muestra el altavoz con cruz y el deslizador se atenúa.
 * @param onToggleMute toque en el ícono del volumen: alterna el silencio de TODA la capa sin mover el deslizador;
 *   el segundo toque devuelve el sonido al nivel que tenía cada clip (ver `afterMasterVolumeIconTap`).
 * @param onOpenVolumeDialog toque en el valor numérico: pide abrir la ventana para escribir el volumen. La ventana NO vive
 *   acá: la aloja quien muestra esta barra (la fila del carril), porque la barra se reconstruye al pasar de costado a
 *   pie y su estado interno se perdería con ella, cerrando la ventana a mitad de edición.
 * @param stacked `false`: uno junto al otro (panel lateral, como la referencia de FL). `true`: uno sobre otro,
 *   ocupando el ancho que se le dé (acordeón del pie, columna angosta de capas): los oídos arriba del panorama
 *   y el volumen con su ícono.
 */
@Composable
internal fun AudioTrackMixSliders(
    pan: Float,
    volume: Float,
    masterSilent: Boolean,
    onPanChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOpenVolumeDialog: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    color: Color = Color.White
) {
    val panSlider: @Composable (Modifier) -> Unit = { sliderModifier ->
        AudioMiniSlider(
            value = pan,
            valueRange = -1f..1f,
            onValueChange = onPanChange,
            contentDescription = "Panorama del audio",
            stateText = audioPanLabel(pan),
            defaultValue = AUDIO_PAN_DEFAULT,
            modifier = sliderModifier,
            centered = true,
            color = color
        )
    }
    val volumeSlider: @Composable (Modifier) -> Unit = { sliderModifier ->
        AudioMiniSlider(
            value = volume,
            valueRange = 0f..MAX_AUDIO_VOLUME,
            onValueChange = onVolumeChange,
            contentDescription = "Volumen del audio",
            stateText = audioVolumeLabel(volume),
            defaultValue = AUDIO_VOLUME_DEFAULT,
            modifier = sliderModifier,
            markerValue = 1f,
            taper = AudioVolumeFaderTaper,
            color = if (masterSilent) color.copy(alpha = MUTED_SLIDER_ALPHA) else color
        )
    }

    if (stacked) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(STACKED_SPACING)) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(STACKED_EARS_HEIGHT),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StereoEar(right = false, iconSize = STACKED_EAR_ICON_SIZE, color = color)
                    StereoEar(right = true, iconSize = STACKED_EAR_ICON_SIZE, color = color)
                }
                panSlider(Modifier.fillMaxWidth().height(STACKED_PAN_SLIDER_HEIGHT))
            }
            StackedVolumeRow(masterSilent, color, onToggleMute, volumeSlider)
            VolumeReadout(
                volume = volume,
                color = color,
                showUnit = true,
                onClick = onOpenVolumeDialog,
                onVolumeChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth().height(STACKED_VOLUME_READOUT_HEIGHT)
            )
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MIX_GROUPS_SPACING)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PAN_GROUP_SPACING)
            ) {
                StereoEar(right = false, iconSize = EAR_ICON_SIZE, color = color)
                panSlider(Modifier.width(PAN_SLIDER_WIDTH).height(MINI_SLIDER_HEIGHT))
                StereoEar(right = true, iconSize = EAR_ICON_SIZE, color = color)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VOLUME_GROUP_SPACING)
            ) {
                VolumeGlyph(
                    masterSilent = masterSilent,
                    iconSize = VOLUME_ICON_SIZE,
                    touchSize = VOLUME_TOUCH_SIZE,
                    color = color,
                    onClick = onToggleMute
                )
                volumeSlider(Modifier.width(VOLUME_SLIDER_WIDTH).height(MINI_SLIDER_HEIGHT))
                VolumeReadout(
                    volume = volume,
                    color = color,
                    showUnit = false,
                    onClick = onOpenVolumeDialog,
                    onVolumeChange = onVolumeChange,
                    modifier = Modifier.width(VOLUME_READOUT_WIDTH).height(VOLUME_READOUT_HEIGHT)
                )
            }
        }
    }
}

/**
 * Hace que el elemento CONSUMA los toques que caen sobre él sin disparar ninguna acción.
 *
 * Por qué existe: Compose amplía el área de impacto de todo elemento con `pointerInput`/`clickable` hasta un mínimo
 * de 48 dp (`ViewConfiguration.minimumTouchTargetSize`). Los botones de la barra miden 26 dp, así que su área
 * efectiva invade a los vecinos: un elemento vecino SIN receptor de toques (los oídos del panorama) quedaba dentro
 * del área ampliada de «Eliminar» —o del ícono de silencio, en el caso del oído derecho— y el toque se le entregaba
 * a ese botón. Con un receptor propio, el elemento gana la prueba de impacto sobre su propia superficie (distancia
 * 0) y el toque muere ahí. Mismo idioma que `RowSideActionsPanelHost` para el hueco entre íconos.
 */
private fun Modifier.absorbTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/**
 * Oído de un extremo del panorama con su letra debajo: «L» (`ic_ear_left`) o «R» (`ic_ear_right`). Cada lado
 * tiene su recurso propio y AMBOS miran hacia adentro, hacia el centro de la barra (la concha del oído abre hacia
 * el panorama), como una cabeza vista de frente. Nada se voltea al dibujar: la orientación vive en el archivo.
 * Decorativo para accesibilidad: el deslizador ya anuncia su estado.
 *
 * Es SOLO visual: no ejecuta ninguna acción. Aun así absorbe sus toques ([absorbTouches]); ver ese modificador
 * para el porqué (sin esto, tocar el oído izquierdo disparaba «Eliminar» y el derecho el silencio).
 */
/** Recurso del oído de cada extremo del panorama: derecho `ic_ear_right`, izquierdo `ic_ear_left`. */
@androidx.annotation.DrawableRes
internal fun earDrawableRes(right: Boolean): Int =
    if (right) R.drawable.ic_ear_right else R.drawable.ic_ear_left

@Composable
private fun StereoEar(right: Boolean, iconSize: Dp, color: Color) {
    Column(
        modifier = Modifier.absorbTouches(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = earDrawableRes(right)),
            contentDescription = null,
            tint = color.copy(alpha = 0.9f),
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = if (right) "R" else "L",
            color = color.copy(alpha = 0.9f),
            fontSize = 8.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Ícono del volumen al inicio de su deslizador, y a la vez BOTÓN de silencio: un toque alterna entre el altavoz
 * con ondas (con sonido) y el altavoz con una cruz (capa silenciosa, [masterSilent]); otro toque devuelve el
 * sonido al nivel que tenía cada clip, y el deslizador nunca se mueve con el silencio.
 *
 * Los dos dibujos ocupan EXACTAMENTE el mismo lugar —uno sobre otro— y el cambio es un fundido corto. El área
 * tocable ([touchSize]) es mayor que el dibujo ([iconSize]) para que acertar con el dedo sea cómodo sin alterar el
 * diseño. Accesibilidad: se anuncia como botón con la acción que hará el siguiente toque.
 */
@Composable
private fun VolumeGlyph(
    masterSilent: Boolean,
    iconSize: Dp,
    touchSize: Dp,
    color: Color,
    onClick: () -> Unit
) {
    val actionLabel = audioVolumeIconActionLabel(masterSilent)
    Box(
        modifier = Modifier
            .size(touchSize)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = actionLabel
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = masterSilent,
            animationSpec = tween(durationMillis = 120),
            label = "volumeGlyph"
        ) { off ->
            Icon(
                painter = painterResource(id = if (off) R.drawable.ic_volume_off else R.drawable.ic_volume),
                contentDescription = null,
                tint = color.copy(alpha = 0.9f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Valor numérico del volumen en decibelios, EN VIVO: se recompone con cada movimiento del deslizador. Una cajita con
 * borde tenue lo distingue de un simple rótulo. [showUnit] agrega « dB» (donde hay ancho, en el bloque apilado).
 *
 * Dos gestos, como los campos numéricos de FL Studio Mobile:
 *  - **Toque**: abre la ventana para escribir el valor exacto ([AudioVolumeInputDialog], vía [onClick]).
 *  - **Pulsar y arrastrar en vertical**: hacia arriba sube el nivel y hacia abajo lo baja, en vivo, a
 *    [VOLUME_READOUT_DRAG_DP_PER_DB] dp por dB y en décimas de dB. El movimiento se mide desde donde el dedo superó la
 *    holgura táctil (sin salto inicial) y es absoluto respecto al nivel de partida ([audioVolumeAfterDbDelta]): volver
 *    al punto de partida devuelve el volumen original. Acota al rango del control y vibra suave al pasar por 0 dB y
 *    al llegar a un extremo. Cada arrastre es un solo paso de deshacer (el ViewModel agrupa los cambios continuos).
 *  - **Eje bloqueado**: el eje se decide una vez, al superar la holgura ([readoutDragAxis]). Solo el vertical ajusta
 *    el nivel (y entonces el movimiento horizontal se ignora); un arrastre lateral no cambia nada ni abre la escritura.
 *  - **Sin zona muerta en los extremos**: al llegar al mínimo (silencio) o al máximo, el origen del gesto se re-ancla
 *    al dedo ([audioVolumeIsAtDragLimit]); pasarse con el dedo no obliga a «deshacer» ese recorrido al volver.
 *
 * Mientras está pulsado se resalta la caja. Accesibilidad: botón que abre la escritura, más acciones «Subir» y
 * «Bajar» de [VOLUME_READOUT_A11Y_STEP_DB] dB para quien no puede arrastrar.
 */
@Composable
private fun VolumeReadout(
    volume: Float,
    color: Color,
    showUnit: Boolean,
    onClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    val valueText = audioVolumeDbText(volume)
    val description = audioVolumeReadoutDescription(volume)
    val pxPerDb = with(LocalDensity.current) { VOLUME_READOUT_DRAG_DP_PER_DB.toPx() }
    val haptic = LocalHapticFeedback.current
    val currentVolume by rememberUpdatedState(volume)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = if (pressed) 0.24f else 0.12f), shape)
            .border(0.5.dp, color.copy(alpha = if (pressed) 0.6f else 0.35f), shape)
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick(label = "Escribir un valor") {
                    currentOnClick()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Subir 1 decibelio") {
                        currentOnVolumeChange(audioVolumeAfterDbDelta(currentVolume, VOLUME_READOUT_A11Y_STEP_DB))
                        true
                    },
                    CustomAccessibilityAction("Bajar 1 decibelio") {
                        currentOnVolumeChange(audioVolumeAfterDbDelta(currentVolume, -VOLUME_READOUT_A11Y_STEP_DB))
                        true
                    }
                )
            }
            .pointerInput(pxPerDb) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    try {
                        // Origen del gesto: volumen y posición Y de referencia. Se re-anclan al tocar un extremo.
                        var baseVolume = currentVolume
                        val touchSlopPx = viewConfiguration.touchSlop
                        var anchorY = down.position.y
                        var axis = ReadoutDragAxis.UNDECIDED
                        var lastVolume = baseVolume
                        val completed = drag(down.id) { change ->
                            if (axis == ReadoutDragAxis.UNDECIDED) {
                                axis = readoutDragAxis(
                                    dxPx = change.position.x - down.position.x,
                                    dyPx = change.position.y - down.position.y,
                                    slopPx = touchSlopPx
                                )
                                // Pasó de toque a arrastre vertical: el origen del movimiento es AQUÍ (sin salto).
                                if (axis == ReadoutDragAxis.VERTICAL) anchorY = change.position.y
                            }
                            // Horizontal (o aún sin decidir): no hace nada; solo el eje vertical ajusta el nivel.
                            if (axis == ReadoutDragAxis.VERTICAL) {
                                change.consume()
                                val dragUpPx = anchorY - change.position.y
                                val next = audioVolumeAfterDbDelta(baseVolume, dragUpPx / pxPerDb)
                                val atLimit = audioVolumeIsAtDragLimit(next)
                                if (next != lastVolume) {
                                    val reachedUnity = next == UNITY_AUDIO_VOLUME && lastVolume != UNITY_AUDIO_VOLUME
                                    if (reachedUnity || atLimit) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    lastVolume = next
                                    currentOnVolumeChange(next)
                                }
                                if (atLimit) {
                                    baseVolume = next
                                    anchorY = change.position.y
                                }
                            }
                        }
                        // Soltó sin que el dedo superara la holgura: es un toque y abre la escritura del valor.
                        // Un arrastre lateral (o vertical) NO lo es.
                        if (completed && axis == ReadoutDragAxis.UNDECIDED) currentOnClick()
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (showUnit) "$valueText dB" else valueText,
            color = color.copy(alpha = 0.95f),
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun StackedVolumeRow(
    masterSilent: Boolean,
    color: Color,
    onToggleMute: () -> Unit,
    slider: @Composable (Modifier) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(STACKED_VOLUME_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VolumeGlyph(
            masterSilent = masterSilent,
            iconSize = STACKED_VOLUME_ICON_SIZE,
            touchSize = STACKED_VOLUME_TOUCH_SIZE,
            color = color,
            onClick = onToggleMute
        )
        slider(Modifier.weight(1f).fillMaxHeight().padding(start = 2.dp))
    }
}
