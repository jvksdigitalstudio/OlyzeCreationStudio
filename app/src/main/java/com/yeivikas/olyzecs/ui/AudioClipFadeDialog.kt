package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import kotlin.math.roundToLong

/** Techo absoluto del deslizador de cada fundido: 5 s (los valores por defecto del clip son 400 / 600 ms). */
internal const val AUDIO_FADE_SLIDER_MAX_MS = 5000L

/**
 * Techo REAL del deslizador de un fundido para un clip cuyo tramo audible dura [audibleMs].
 *
 * Es la misma regla que aplica el motor (`effectiveFadesMs`: cada fundido se acota a la MITAD del tramo audible,
 * así entrada y salida nunca se solapan), con el tope absoluto [AUDIO_FADE_SLIDER_MAX_MS]. Así lo que muestra el
 * deslizador coincide con lo que suena y con las rampas dibujadas en el clip: no hay recorrido "muerto" donde se
 * mueve el dedo y no cambia nada. Mínimo 1 para que el rango del deslizador nunca sea vacío (ver
 * [audioFadeControlsEnabled]). Pura.
 */
internal fun audioFadeSliderMaxMs(audibleMs: Long): Long =
    (audibleMs / 2).coerceIn(1L, AUDIO_FADE_SLIDER_MAX_MS)

/** `true` si el tramo audible permite algún fundido (hace falta al menos 2 ms: cada uno vale como máximo la mitad). Pura. */
internal fun audioFadeControlsEnabled(audibleMs: Long): Boolean = audibleMs >= 2L

/** Rótulo de duración de un fundido: "0 ms", "400 ms", "1,5 s". Pura. */
internal fun audioFadeLabel(ms: Long): String =
    if (ms < 1000L) "$ms ms" else "${ms / 1000},${(ms % 1000) / 100} s"

/**
 * Diálogo "Fundidos" del menú del clip (mantener presionado → "Fundidos"): fundido de ENTRADA (fade-in) y de
 * SALIDA (fade-out) del clip, en milisegundos. Reemplaza al antiguo módulo flotante "Fade" del cajón "Módulos".
 *
 * Es un editor EN VIVO, sin Cancelar: cada movimiento llama a [onFadeChange], que es `EditorViewModel.setAudioFade`
 * (un solo paso de deshacer por gesto, ver `pushUndoCheckpointForGesture("fade:<clip>")`), y el clip de atrás dibuja
 * las rampas al instante. Por eso solo hay "Quitar fundidos" (deja los dos en 0) y "Listo".
 *
 * Los valores que se escriben son los ya acotados a [audioFadeSliderMaxMs], para que el modelo no guarde un valor
 * que el deslizador no puede mostrar.
 *
 * @param audibleMs duración del tramo audible del clip (`audibleLengthMs`), que fija el techo de ambos controles.
 * @param accentColor color del carril, igual que el resto de diálogos del clip.
 */
@Composable
internal fun AudioClipFadeDialog(
    fadeInMs: Long,
    fadeOutMs: Long,
    audibleMs: Long,
    accentColor: Color,
    onFadeChange: (fadeInMs: Long, fadeOutMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val maxMs = audioFadeSliderMaxMs(audibleMs)
    val enabled = audioFadeControlsEnabled(audibleMs)
    val shownIn = fadeInMs.coerceIn(0L, maxMs)
    val shownOut = fadeOutMs.coerceIn(0L, maxMs)
    val sliderColors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RectangleShape,
            color = SurfaceTintedElevated,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accentColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Fundidos",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Fundido de entrada: ${audioFadeLabel(shownIn)}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = shownIn.toFloat(),
                    onValueChange = { onFadeChange(it.roundToLong().coerceIn(0L, maxMs), shownOut) },
                    valueRange = 0f..maxMs.toFloat(),
                    enabled = enabled,
                    colors = sliderColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Fundido de salida: ${audioFadeLabel(shownOut)}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = shownOut.toFloat(),
                    onValueChange = { onFadeChange(shownIn, it.roundToLong().coerceIn(0L, maxMs)) },
                    valueRange = 0f..maxMs.toFloat(),
                    enabled = enabled,
                    colors = sliderColors
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { onFadeChange(0L, 0L) },
                        enabled = fadeInMs > 0L || fadeOutMs > 0L
                    ) { Text("Quitar fundidos") }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text("Listo") }
                }
            }
        }
    }
}
