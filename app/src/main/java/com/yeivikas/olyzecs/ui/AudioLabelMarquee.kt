package com.yeivikas.olyzecs.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * ============================================================================
 *  AudioLabelMarquee — cabecera "ícono + nombre" en loop de la pista de audio
 * ============================================================================
 *
 * PROBLEMA (reportado con capturas): la columna izquierda de la fila de audio
 * mide `LABEL_COLUMN_WIDTH` (72 dp) y solo alcanza para el ícono de nota
 * musical. Cuando el usuario abre una de las pestañas inferiores
 * (Control / Módulos / Keyframes) la fila queda comprimida en el borde de la
 * pista y el panel de opciones pasa al PIE de la capa (acordeón): en ese
 * estado el usuario no tiene forma de ver QUÉ audio es esa capa.
 *
 * SOLUCIÓN: en ese estado —y SOLO en ese estado, ver [shouldMarqueeAudioLabel]—
 * la cabecera deja de ser un ícono estático y pasa a mostrar, en un carrusel
 * continuo hacia la IZQUIERDA y en bucle: primero el ícono de nota musical y
 * a continuación el nombre del audio; al terminar el nombre vuelve a entrar
 * el ícono, y así indefinidamente. Con las pestañas cerradas, o con el panel
 * de opciones cerrado o "al costado", la cabecera es la de siempre.
 *
 * DECISIONES DE INGENIERÍA
 *  - Componente propio y no `Modifier.basicMarquee`: en la versión de
 *    Foundation que fija el BOM del proyecto (2024.06 → 1.6.x) `basicMarquee`
 *    sigue siendo experimental y está pensado para texto; acá hay que mover
 *    un BLOQUE (ícono + texto) con una separación de bucle controlada.
 *  - Bucle SIN saltos: el segmento (ícono + nombre) se compone dos veces,
 *    separado por [AUDIO_LABEL_MARQUEE_GAP]; el desplazamiento recorre
 *    exactamente un período (ancho del segmento + hueco) y vuelve a 0, donde
 *    la imagen es idéntica a la del período anterior.
 *  - Rendimiento: el tiempo transcurrido vive en un `State` que SOLO se lee
 *    en la fase de colocación (`layout { place }`) — cada frame invalida
 *    únicamente el placement, nunca recompone ni vuelve a medir. La corrutina
 *    de animación existe únicamente mientras este composable está en
 *    composición Y el segmento desborda el área visible ([marqueeOverflows]):
 *    un nombre corto que cabe no pide frames, y al cerrar el panel o las
 *    pestañas la corrutina se cancela sola — no queda trabajo en segundo plano.
 *  - Velocidad constante en dp/s ([AUDIO_LABEL_MARQUEE_VELOCITY_DP_PER_SEC]),
 *    independiente de la longitud del nombre y de la densidad de pantalla.
 *  - Pausa inicial ([AUDIO_LABEL_MARQUEE_START_DELAY_MS]) con el principio
 *    del nombre a la vista, para que arranque legible.
 *  - Accesibilidad: si el usuario desactivó las animaciones del sistema
 *    (escala de duración = 0) no se anima: se muestra el inicio del
 *    segmento, estático. Un solo nodo semántico ("Audio: nombre") en vez de
 *    las dos copias visuales.
 *  - Si el segmento cabe completo en el área visible (nombre corto) no hay
 *    nada que desplazar: se muestra centrado y quieto.
 */

/** Velocidad del carrusel, en dp por segundo (lectura cómoda, sin marear). */
internal const val AUDIO_LABEL_MARQUEE_VELOCITY_DP_PER_SEC = 34f

/** Pausa antes de empezar a desplazar, con el inicio del nombre visible. */
internal const val AUDIO_LABEL_MARQUEE_START_DELAY_MS = 700L

/** Hueco entre el final del nombre y la siguiente entrada del ícono. */
private val AUDIO_LABEL_MARQUEE_GAP = 28.dp

/** Tamaño del ícono de la cabecera de audio: único valor, compartido por la cabecera estática y el carrusel. */
internal val AUDIO_LABEL_ICON_SIZE = 28.dp

/**
 * Sombra del nombre del audio, compartida por la cabecera del clip y el
 * carrusel: el texto se lee sobre CUALQUIER color/degradado elegido para la pista.
 */
internal val AudioNameShadow = Shadow(
    color = Color.Black.copy(alpha = 0.65f),
    offset = Offset(0f, 1f),
    blurRadius = 3f
)

/** Prefijo de la descripción de accesibilidad del carrusel. */
private const val AUDIO_LABEL_SEMANTICS_PREFIX = "Audio"

/** Texto mostrado si el clip no trae nombre. */
internal const val AUDIO_LABEL_FALLBACK_NAME = "Audio"

/**
 * Regla única de cuándo la cabecera de la pista de audio anima su nombre.
 *
 * @param footAccordionVisible `true` si el panel de opciones de la fila está
 *   desplegado AL PIE de la capa (acordeón), no "al costado".
 * @param bottomPanelExpanded `true` si alguna pestaña inferior
 *   (Control / Módulos / Keyframes) está abierta.
 *
 * Deben cumplirse AMBAS: con el panel al pie pero sin pestaña abierta (la
 * fila bajó por scroll) o con la pestaña abierta pero el panel cerrado, la
 * cabecera se mantiene estática como siempre.
 */
internal fun shouldMarqueeAudioLabel(
    footAccordionVisible: Boolean,
    bottomPanelExpanded: Boolean
): Boolean = footAccordionVisible && bottomPanelExpanded

/**
 * Desplazamiento (px, siempre en `[0, periodPx)`) del carrusel tras
 * [elapsedNanos] de animación a [velocityPxPerSec]. Módulo del período: el
 * bucle es continuo y el valor nunca crece sin límite. Se calcula en doble
 * precisión para que un carrusel que lleve horas abierto no pierda
 * resolución subpíxel.
 */
internal fun marqueeOffsetPx(elapsedNanos: Long, velocityPxPerSec: Float, periodPx: Float): Float {
    if (elapsedNanos <= 0L || velocityPxPerSec <= 0f || periodPx <= 0f) return 0f
    val traveledPx = elapsedNanos / 1_000_000_000.0 * velocityPxPerSec
    return (traveledPx % periodPx.toDouble()).toFloat()
}

/** `true` si el segmento no cabe en el área visible (única definición de "desborda"). */
internal fun marqueeOverflows(segmentPx: Int, viewportPx: Int): Boolean = segmentPx > viewportPx

/**
 * Posiciones X (px) de las copias del segmento dentro del área visible.
 * [secondX] es `null` cuando no hay segunda copia que dibujar.
 */
internal class MarqueeSlots(val firstX: Int, val secondX: Int?)

/**
 * Calcula dónde colocar las copias del segmento:
 *  - cabe entero → centrado y quieto (no hay nada que desplazar);
 *  - no cabe y no se anima (animaciones del sistema desactivadas) → pegado al
 *    inicio, recortado;
 *  - no cabe y se anima → copia 1 desplazada `-offsetPx`, copia 2 un período
 *    más a la derecha ([segmentPx] + [gapPx]).
 */
internal fun marqueeSlots(
    segmentPx: Int,
    viewportPx: Int,
    gapPx: Int,
    offsetPx: Float,
    animate: Boolean
): MarqueeSlots {
    if (!marqueeOverflows(segmentPx, viewportPx)) return MarqueeSlots((viewportPx - segmentPx) / 2, null)
    if (!animate) return MarqueeSlots(0, null)
    val first = -offsetPx.roundToInt()
    return MarqueeSlots(first, first + segmentPx + gapPx)
}

/**
 * Ícono de nota musical + [name] desplazándose hacia la izquierda en bucle.
 * El llamador le da un área de ancho ACOTADO (`Modifier.fillMaxSize()` dentro
 * de un contenedor con tamaño/peso): lo que sobresale se recorta.
 */
@Composable
internal fun AudioLabelMarquee(name: String, modifier: Modifier = Modifier) {
    val animate = rememberSystemAnimationsEnabled()
    // Se reinicia al cambiar el nombre: la animación siempre arranca en el
    // principio del texto nuevo.
    val elapsedNanos = remember(name) { mutableLongStateOf(0L) }
    // `true` si el segmento NO cabe en el área visible (lo informa el Layout al
    // medir). Sin desborde no hay nada que desplazar: ni se compone la segunda
    // copia ni corre el bucle de frames (no se mantiene el vsync ocupado en vano).
    var overflows by remember(name) { mutableStateOf(false) }
    val running = animate && overflows

    LaunchedEffect(running, name) {
        elapsedNanos.longValue = 0L
        if (!running) return@LaunchedEffect
        delay(AUDIO_LABEL_MARQUEE_START_DELAY_MS)
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                elapsedNanos.longValue += now - last
                last = now
            }
        }
    }

    val segment: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_audio_premium),
                // La semántica se declara una sola vez en el Layout (abajo).
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(AUDIO_LABEL_ICON_SIZE)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = name,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    // `labelSmall` trae lineHeight = 16.sp: se acota para que
                    // el texto no flote en una caja más alta que su fuente.
                    lineHeight = 13.sp,
                    shadow = AudioNameShadow
                )
            )
        }
    }

    Layout(
        content = {
            segment()
            if (running) segment()
        },
        modifier = modifier
            .clipToBounds()
            .clearAndSetSemantics { contentDescription = "$AUDIO_LABEL_SEMANTICS_PREFIX: $name" }
    ) { measurables, constraints ->
        // Cada copia se mide SIN tope de ancho: tiene que poder ser más
        // ancha que el área visible para que haya algo que desplazar.
        val unbounded = Constraints(maxWidth = Constraints.Infinity, maxHeight = constraints.maxHeight)
        val first = measurables[0].measure(unbounded)
        val second = if (running && measurables.size > 1) measurables[1].measure(unbounded) else null

        val viewportW = constraints.constrainWidth(if (constraints.hasBoundedWidth) constraints.maxWidth else first.width)
        val viewportH = constraints.constrainHeight(if (constraints.hasBoundedHeight) constraints.maxHeight else first.height)
        val gapPx = AUDIO_LABEL_MARQUEE_GAP.roundToPx()
        val velocityPx = AUDIO_LABEL_MARQUEE_VELOCITY_DP_PER_SEC.dp.toPx()
        // Solo se escribe el estado si cambia (evita invalidaciones en cadena).
        val nowOverflows = marqueeOverflows(first.width, viewportW)
        if (nowOverflows != overflows) overflows = nowOverflows

        layout(viewportW, viewportH) {
            // El `State` se lee ACÁ (placement), no arriba: cada frame
            // invalida solo la colocación, sin remedir ni recomponer.
            val offset = marqueeOffsetPx(
                elapsedNanos = elapsedNanos.longValue,
                velocityPxPerSec = velocityPx,
                periodPx = (first.width + gapPx).toFloat()
            )
            val slots = marqueeSlots(first.width, viewportW, gapPx, offset, animate)
            first.placeRelative(slots.firstX, (viewportH - first.height) / 2)
            val secondX = slots.secondX
            if (second != null && secondX != null) {
                second.placeRelative(secondX, (viewportH - second.height) / 2)
            }
        }
    }
}

/**
 * `true` si las animaciones del sistema están habilitadas (Ajustes →
 * Accesibilidad → "Quitar animaciones" las pone en escala 0). Se lee una vez
 * por composición del contexto.
 */
@Composable
private fun rememberSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }
}
