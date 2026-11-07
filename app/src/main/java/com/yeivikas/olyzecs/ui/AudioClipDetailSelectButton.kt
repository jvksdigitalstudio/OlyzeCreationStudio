package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.R

/**
 * Botón "Seleccionar" de la cabecera de la ventana ampliada del audio: cuántos rangos se pueden marcar sobre la onda.
 * Arriba lleva el número (`x1` por defecto, hasta `x7`) con una flecha a cada lado; al pie, el nombre.
 *
 * El número se cambia de DOS formas, que ambas terminan en [onCountChange]:
 *  1. las flechas de los lados: la izquierda resta y la derecha suma de uno en uno (se apagan en el límite);
 *  2. pulsar el número y arrastrar sin soltar hacia ARRIBA (suma) o hacia ABAJO (resta): cada [DETAIL_SELECT_DRAG_STEP_DP]
 *     de recorrido cambia una unidad, medido desde donde se apoyó el dedo ([detailSelectCountFromDrag]).
 *
 * Solo dibuja y traduce gestos a una cantidad: los límites los pone [stepDetailSelectCount] y qué hace la cantidad con
 * los rangos lo decide [AudioClipDetailSelectionState]. Comparte el marco de los demás botones ([DetailTileFrame]).
 *
 * @param count cantidad actual (`x1 … x7`); @param onCountChange pide una cantidad nueva, ya acotada.
 */
@Composable
internal fun DetailSelectButton(count: Int, onCountChange: (Int) -> Unit) {
    val lessDescription = stringResource(R.string.audio_detail_select_less_desc)
    val moreDescription = stringResource(R.string.audio_detail_select_more_desc)
    // La zona táctil de cada pieza es solo la suya (flechas y número): el marco no es un botón entero, porque un toque
    // en el borde no debe contar como cambio.
    Box(
        modifier = Modifier.height(DETAIL_HEADER_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        DetailTileFrame(
            label = stringResource(R.string.audio_detail_select),
            labelColor = DetailNeutralLabel,
            fillTop = DetailNeutralFill,
            fillBottom = DetailNeutralFill,
            border = DetailNeutralBorder
        ) {
            Row(
                modifier = Modifier.height(SELECT_ROW_HEIGHT),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectCountArrow(
                    pointsRight = false,
                    enabled = count > DETAIL_SELECT_MIN_COUNT,
                    description = lessDescription,
                    onClick = { onCountChange(stepDetailSelectCount(count, -1)) }
                )
                SelectCountReadout(count = count, onCountChange = onCountChange)
                SelectCountArrow(
                    pointsRight = true,
                    enabled = count < DETAIL_SELECT_MAX_COUNT,
                    description = moreDescription,
                    onClick = { onCountChange(stepDetailSelectCount(count, +1)) }
                )
            }
        }
    }
}

/** Flecha de un lado del número: un chevrón dibujado (sin recurso), atenuado y sin respuesta al tocar en el límite. */
@Composable
private fun SelectCountArrow(pointsRight: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    Canvas(
        modifier = Modifier
            .size(width = SELECT_ARROW_WIDTH, height = SELECT_ROW_HEIGHT)
            .alpha(if (enabled) 1f else DETAIL_DISABLED_ALPHA)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        val halfHeight = SELECT_ARROW_HALF_HEIGHT.toPx()
        val halfDepth = SELECT_ARROW_HALF_DEPTH.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        // La punta apunta al lado de la flecha; las dos patas arrancan del lado contrario.
        val tipX = if (pointsRight) centerX + halfDepth else centerX - halfDepth
        val baseX = if (pointsRight) centerX - halfDepth else centerX + halfDepth
        val chevron = Path().apply {
            moveTo(baseX, centerY - halfHeight)
            lineTo(tipX, centerY)
            lineTo(baseX, centerY + halfHeight)
        }
        drawPath(
            path = chevron,
            color = Color.White,
            style = Stroke(width = SELECT_ARROW_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * El número (`x1`…) y su arrastre vertical. Se arrastra hacia arriba para sumar y hacia abajo para restar; cada gesto
 * se mide contra la cantidad que había al apoyar el dedo, no contra la anterior, así no se acumula error y volver al
 * punto de partida devuelve la cantidad de origen.
 */
@Composable
private fun SelectCountReadout(count: Int, onCountChange: (Int) -> Unit) {
    // El gesto vive en un `pointerInput` que no se reinicia al cambiar la cantidad (sería cortar el arrastre en curso):
    // lee siempre el valor y la lambda vigentes a través de estos estados.
    val currentCount by rememberUpdatedState(count)
    val currentOnCountChange by rememberUpdatedState(onCountChange)
    val stepPx = with(LocalDensity.current) { DETAIL_SELECT_DRAG_STEP_DP.dp.toPx() }
    val description = stringResource(R.string.audio_detail_select_count_desc, count)
    Box(
        modifier = Modifier
            .size(width = SELECT_READOUT_WIDTH, height = SELECT_ROW_HEIGHT)
            .pointerInput(stepPx) {
                var startCount = currentCount
                var draggedUpPx = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        startCount = currentCount
                        draggedUpPx = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // `dragAmount` es positivo hacia abajo; la cantidad sube al ir hacia arriba.
                        draggedUpPx -= dragAmount
                        val next = detailSelectCountFromDrag(startCount, draggedUpPx, stepPx)
                        if (next != currentCount) currentOnCountChange(next)
                    }
                )
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = detailSelectCountLabel(count),
            color = Color.White,
            fontSize = SELECT_READOUT_SIZE,
            lineHeight = SELECT_READOUT_LINE_HEIGHT,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Medidas de la fila superior del botón (flecha · número · flecha). La altura cabe en el espacio que el marco deja sobre
 * el nombre ([DetailTileFrame]: la altura del botón menos una línea de nombre).
 */
private val SELECT_ROW_HEIGHT = 18.dp
private val SELECT_ARROW_WIDTH = 19.dp
private val SELECT_READOUT_WIDTH = 24.dp
private val SELECT_ARROW_HALF_HEIGHT = 4.5.dp
private val SELECT_ARROW_HALF_DEPTH = 2.5.dp
private val SELECT_ARROW_STROKE = 1.8.dp
private val SELECT_READOUT_SIZE = 12.sp
private val SELECT_READOUT_LINE_HEIGHT = 14.sp
