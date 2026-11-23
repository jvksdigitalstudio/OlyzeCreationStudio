package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.AudioWaveform
import com.yeivikas.olyzecs.engine.audio.audibleLengthMs
import com.yeivikas.olyzecs.engine.audio.effectiveFadesMs
import com.yeivikas.olyzecs.engine.audio.label

/** Alto de la franja con el nombre del clip — cabecera al estilo de la playlist de FL Studio Mobile. */
private val CLIP_HEADER_HEIGHT = 12.dp

/**
 * Diseño de UN clip de audio: cabecera con su nombre y, debajo, el cuerpo oscuro con
 * la forma de onda (repetida si hay loop) y las rampas de fade. Es solo DIBUJO, sin
 * gestos: lo usan el bloque del clip dentro de su carril ([AudioClipBlock]) y la vista
 * previa del arrastre entre carriles ([AudioClipDragOverlay]), así ambos se ven idénticos.
 *
 * @param lengthMs largo del clip en el timeline a mostrar (puede ser una previsualización del estirado).
 * @param trimStartMs punto del archivo donde arranca la señal a mostrar (idem).
 * @param selected dibuja el borde blanco de clip seleccionado.
 * @param lifted aspecto de "agarrado" (tinte más suave y cabecera iluminada), durante el arrastre.
 * @param bodyModifier gestos del cuerpo (la señal), p. ej. toque y menú.
 * @param overlay contenido superpuesto al clip entero (zona de arrastre, menú…), alineado arriba a la izquierda.
 */
@Composable
internal fun AudioClipSurface(
    clip: AudioClip,
    waveform: AudioWaveform?,
    lengthMs: Long,
    trimStartMs: Long,
    pxPerMs: Float,
    trackBrush: Brush,
    waveformSignalColor: Color,
    selected: Boolean,
    lifted: Boolean,
    modifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val clipShape = RoundedCornerShape(4.dp)
    // Sin borde salvo que el clip esté seleccionado (referencia FL Studio Mobile).
    val borderColor = if (selected) Color.White.copy(alpha = 0.85f) else Color.Transparent
    Box(
        modifier = modifier
            .padding(vertical = 2.dp)
            .clip(clipShape)
            // Cuerpo oscuro bajo el tinte de la pista: da contraste a la señal.
            .background(Color.Black.copy(alpha = 0.45f))
            .background(brush = trackBrush, alpha = if (lifted) 0.45f else 0.30f)
            .border(width = 1.dp, color = borderColor, shape = clipShape),
        contentAlignment = Alignment.TopStart
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cabecera VISUAL: nombre del clip.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CLIP_HEADER_HEIGHT)
                    .background(brush = trackBrush, alpha = 0.95f)
                    .background(Color.Black.copy(alpha = if (clip.muted) 0.40f else 0.12f))
                    // Mientras se arrastra, la cabecera se ilumina: se nota que está agarrada.
                    .background(Color.White.copy(alpha = if (lifted) 0.16f else 0f)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = clip.label,
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
            Box(modifier = Modifier.fillMaxWidth().weight(1f).then(bodyModifier)) {
                AudioWaveformCanvas(
                    waveform = waveform,
                    trimStartMs = trimStartMs.toDouble(),
                    pxPerMs = pxPerMs,
                    signalColor = waveformSignalColor,
                    dimmed = clip.muted,
                    loop = clip.loop,
                    modifier = Modifier.fillMaxSize()
                )
                ClipFadeOverlay(
                    fadeInMs = clip.fadeInMs,
                    fadeOutMs = clip.fadeOutMs,
                    audibleMs = audibleLengthMs(clip.sourceDurationMs, trimStartMs, clip.loop, lengthMs),
                    pxPerMs = pxPerMs,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        overlay()
    }
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
