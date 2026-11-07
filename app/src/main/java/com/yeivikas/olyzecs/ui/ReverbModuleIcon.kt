package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Radios (fracción del lado) de los arcos del glifo del Reverb: cada onda más lejos y más tenue, como una cola que se apaga. */
private val REVERB_ICON_WAVE_RADII = floatArrayOf(0.22f, 0.42f, 0.62f)
private const val REVERB_ICON_WAVE_FADE = 0.28f

/**
 * Glifo del módulo "Reverb": una fuente puntual y tres ondas que se alejan y se desvanecen. Dibujado en [Canvas], mismo criterio
 * que [ZoomXModuleIcon] / [ShadowModuleIcon] (sin un recurso .xml extra para un glifo tan simple). Sirve de ícono de la fila del
 * cajón y de la cabecera de la ventana flotante.
 */
@Composable
internal fun ReverbModuleIcon(tint: Color, iconSize: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val strokeWidth = size.minDimension * 0.09f
        val source = Offset(size.width * 0.28f, size.height * 0.5f)
        drawCircle(color = tint, radius = size.minDimension * 0.08f, center = source)
        REVERB_ICON_WAVE_RADII.forEachIndexed { index, fraction ->
            val radius = size.minDimension * fraction
            drawArc(
                color = tint.copy(alpha = tint.alpha * (1f - REVERB_ICON_WAVE_FADE * index)),
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(source.x - radius, source.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
