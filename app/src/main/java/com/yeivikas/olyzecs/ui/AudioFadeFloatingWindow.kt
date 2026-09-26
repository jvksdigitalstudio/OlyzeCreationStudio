package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.R
import kotlin.math.roundToLong

private val AUDIO_FADE_WINDOW_SIZE = DpSize(240.dp, 270.dp)

/** Techo del slider de cada fade — 5 segundos, mismo orden de magnitud
 * que los defaults ya existentes en `AudioClip` (fadeInMs=400L,
 * fadeOutMs=600L) multiplicado con margen de sobra para fundidos largos. */
private const val MAX_FADE_MS = 5000f

/**
 * Ventana flotante del módulo "Fade" — el ÚNICO módulo de audio con DOS
 * controles a la vez (fade-in / fade-out), tal como ya se presentaban
 * juntos, uno debajo del otro, en el viejo `AudioPanel` (comentado). No
 * comparte componente con [AudioSliderFloatingWindow] (ese es de UN solo
 * control) — instanciarlo dos veces adentro de acá sería más código y más
 * indirección que este panel chico y dedicado.
 */
@Composable
internal fun AudioFadeFloatingWindow(
    fadeInMs: Long,
    fadeOutMs: Long,
    onFadeChange: (fadeInMs: Long, fadeOutMs: Long) -> Unit,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    FloatingToolWindow(
        title = "Fade",
        titleIcon = { tint ->
            Icon(
                painter = painterResource(id = R.drawable.ic_fade),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        },
        initialOffset = initialOffset,
        initialSize = AUDIO_FADE_WINDOW_SIZE,
        onClose = onClose,
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                "Fade in: ${fadeInMs} ms",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = fadeInMs.toFloat().coerceIn(0f, MAX_FADE_MS),
                onValueChange = { onFadeChange(it.roundToLong(), fadeOutMs) },
                valueRange = 0f..MAX_FADE_MS
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Fade out: ${fadeOutMs} ms",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = fadeOutMs.toFloat().coerceIn(0f, MAX_FADE_MS),
                onValueChange = { onFadeChange(fadeInMs, it.roundToLong()) },
                valueRange = 0f..MAX_FADE_MS
            )
        }
    }
}
