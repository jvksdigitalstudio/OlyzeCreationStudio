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

private val AUDIO_SLIDER_WINDOW_SIZE = DpSize(230.dp, 190.dp)

/**
 * Ventana flotante GENÉRICA de un solo control deslizante — mismo chrome
 * ([FloatingToolWindow]) que Recolor/Color Básico/3D Básico/Zoom X.
 *
 * A PEDIDO EXPLÍCITO DEL USUARIO: cada control que tenía el viejo panel de
 * audio (`AudioPanel`, en `AudioAndExportPanels.kt`, comentado junto con
 * Cámara/Look/Tiempo — ver el bloque grande de `EditorScreen.kt`) pasa a
 * ser SU PROPIO módulo flotante, igual que Recolor/3D/Zoom X. Este
 * componente es el que comparten "Volumen" y "Recorte" (los dos únicos
 * controles de audio que son un RANGO, no un interruptor de dos estados)
 * — ver el comentario grande en `ModulesDrawerPanel` sobre `onZoomXClick`:
 * *"el día que un segundo módulo necesite el mismo mecanismo es el
 * momento correcto para generalizar esto"* — con 5 módulos de audio
 * necesitándolo a la vez, este es exactamente ese momento, no antes.
 *
 * "Silencio" y "Loop" (dos estados, no un rango) usan
 * [AudioToggleFloatingWindow] en cambio, y "Fade" (dos controles a la
 * vez) tiene su propia ventana dedicada, [AudioFadeFloatingWindow].
 *
 * A diferencia de Zoom X (que integra con el sistema de keyframes/Grabar
 * de una capa vía `commitFrame`), el audio es un dato ÚNICO de proyecto,
 * sin keyframes propios — [onValueChange] llama directo al setter real
 * del ViewModel (`setAudioVolume`/`setAudioTrimStart`, ver el llamador en
 * `EditorScreen.kt`), sin ningún paso intermedio de "vista previa en vivo
 * + commit" como sí necesita la cámara.
 */
@Composable
internal fun AudioSliderFloatingWindow(
    title: String,
    titleIconRes: Int,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
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
        initialSize = AUDIO_SLIDER_WINDOW_SIZE,
        onClose = onClose,
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                valueLabel,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange
            )
        }
    }
}
