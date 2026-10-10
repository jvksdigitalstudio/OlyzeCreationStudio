package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val AUDIO_TOGGLE_WINDOW_SIZE = DpSize(220.dp, 150.dp)

/**
 * Ventana flotante GENÉRICA de un solo interruptor on/off — ver el
 * comentario grande de [AudioSliderFloatingWindow] sobre por qué este
 * componente es compartido, en este caso entre "Silencio" y "Loop" (los
 * dos únicos controles de audio de dos estados, sin rango).
 */
@Composable
internal fun AudioToggleFloatingWindow(
    title: String,
    titleIconRes: Int,
    switchLabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    FloatingToolWindow(
        title = title,
        titleIcon = { tint ->
            Icon(
                painter = painterResource(id = titleIconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        },
        initialOffset = initialOffset,
        initialSize = AUDIO_TOGGLE_WINDOW_SIZE,
        onClose = onClose,
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                switchLabel,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
