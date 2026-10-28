package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark

/**
 * Apariencia de una fila del timeline (capa de imagen o pista de audio) en reposo y mientras se
 * reordena arrastrando su miniatura. Es la ÚNICA fuente de este estilo: [TimelineRow] y
 * [AudioTrackRow] lo aplican con [layerRowSurface], así ambas se ven y se comportan igual.
 *
 * Diseño de la fila "levantada" (patrón de DAW/editor profesional: la tarjeta que sostienes se ve
 * MÁS sólida y MÁS viva que el resto, nunca más apagada):
 *  1. Base OPACA ([SurfaceTintedDark]): nada de lo que pasa por debajo (vecinas, fondo) se
 *     transparenta a través de la fila.
 *  2. El color de identidad de la capa ([LIFTED_WASH_ALPHA], el doble de intenso que en reposo),
 *     para que la fila se siga reconociendo de un vistazo mientras se mueve.
 *  3. Un velo blanco leve ([LIFTED_HIGHLIGHT_ALPHA]) que la "ilumina" frente a las demás.
 *  4. Dos filetes blancos, arriba y abajo ([LIFTED_EDGE_ALPHA]), que delimitan la tarjeta.
 *  5. Sombra de elevación ([LIFTED_ELEVATION]) y zIndex por encima de las vecinas.
 *
 * Causa del defecto anterior: la fila arrastrada pintaba un fondo opaco gris-azulado
 * (`SurfaceTintedElevated`) ENCIMA del color de la capa, lo tapaba y la dejaba como una losa
 * oscura sin identidad que se leía como "transparente" frente a las filas vecinas coloreadas.
 */
internal const val REST_WASH_ALPHA = 0.30f
internal const val LIFTED_WASH_ALPHA = 0.60f
internal const val LIFTED_HIGHLIGHT_ALPHA = 0.08f
internal const val LIFTED_EDGE_ALPHA = 0.55f
internal const val LIFTED_Z_INDEX = 10f
internal const val SLOT_FILL_ALPHA = 0.10f
internal const val SLOT_EDGE_ALPHA = 0.35f
private val LIFTED_ELEVATION = 10.dp
private val LIFTED_EDGE_WIDTH = 1.dp
private val SLOT_EDGE_WIDTH = 1.dp
private val REORDER_BADGE_ICON_SIZE = 14.dp
private val REORDER_BADGE_END_MARGIN = 2.dp
private val REORDER_BADGE_CORNER = 4.dp
internal const val REORDER_BADGE_CHIP_ALPHA = 0.35f

/**
 * Superficie de la fila: lavado suave del color de la capa en reposo; tarjeta opaca, saturada y
 * elevada cuando [isDragging]. Debe ir DESPUÉS del `.offset` de la fila y ANTES de sus hijos.
 */
internal fun Modifier.layerRowSurface(trackBrush: Brush, isDragging: Boolean): Modifier =
    if (!isDragging) {
        background(brush = trackBrush, alpha = REST_WASH_ALPHA)
    } else {
        zIndex(LIFTED_Z_INDEX)
            .shadow(elevation = LIFTED_ELEVATION, shape = RectangleShape)
            .background(SurfaceTintedDark)
            .background(brush = trackBrush, alpha = LIFTED_WASH_ALPHA)
            .background(Color.White.copy(alpha = LIFTED_HIGHLIGHT_ALPHA))
            .drawBehind {
                val edge = Color.White.copy(alpha = LIFTED_EDGE_ALPHA)
                val stroke = LIFTED_EDGE_WIDTH.toPx()
                val top = stroke / 2f
                val bottom = size.height - stroke / 2f
                drawLine(edge, Offset(0f, top), Offset(size.width, top), stroke)
                drawLine(edge, Offset(0f, bottom), Offset(size.width, bottom), stroke)
            }
    }

/**
 * Borde superior (px, en el contenido de la lista) del hueco donde caería la fila arrastrada si se
 * soltara ahora: la fila de origen desplazada [steps] filas. `null` si no hay arrastre activo
 * ([draggingIndex] < 0). Las filas miden todas lo mismo ([rowHeightPx]).
 */
internal fun dragTargetSlotTopPx(draggingIndex: Int, steps: Int, rowHeightPx: Float): Float? =
    if (draggingIndex < 0) null else (draggingIndex + steps) * rowHeightPx

/**
 * Marca el hueco destino de un reordenamiento: un recuadro tenue con filetes, dibujado DETRÁS de las
 * filas (la fila arrastrada, opaca, lo cubre cuando coincide). Sin él, el lugar que dejan las vecinas
 * al correrse se ve como un agujero del fondo morado. Debe ir después de `verticalScroll` para que
 * [slotTopPx] se mida en coordenadas del contenido y acompañe al scroll.
 */
internal fun Modifier.dragTargetSlot(slotTopPx: Float?, slotHeightPx: Float): Modifier =
    if (slotTopPx == null) {
        this
    } else {
        drawBehind {
            drawRect(
                color = Color.White.copy(alpha = SLOT_FILL_ALPHA),
                topLeft = Offset(0f, slotTopPx),
                size = Size(size.width, slotHeightPx)
            )
            val edge = Color.White.copy(alpha = SLOT_EDGE_ALPHA)
            val stroke = SLOT_EDGE_WIDTH.toPx()
            drawLine(edge, Offset(0f, slotTopPx + stroke / 2f), Offset(size.width, slotTopPx + stroke / 2f), stroke)
            val bottom = slotTopPx + slotHeightPx - stroke / 2f
            drawLine(edge, Offset(0f, bottom), Offset(size.width, bottom), stroke)
        }
    }

/**
 * Indicador de reordenar: dos flechas arriba/abajo ([R.drawable.ic_reorder_arrows]) pegadas al borde
 * DERECHO y centradas en vertical de la columna izquierda de la fila, sobre un chip oscuro tenue para
 * que se lean sobre cualquier color de capa. Solo se dibuja mientras [isDragging]; en reposo no
 * agrega nada. Se dibuja en el mismo nodo (sin tocar el layout), así que no mueve miniatura ni texto.
 * Debe ir ANTES del `.padding` de la columna, para medirse contra su ancho completo.
 */
@Composable
internal fun Modifier.reorderArrowsBadge(isDragging: Boolean): Modifier {
    if (!isDragging) return this
    val painter = painterResource(id = R.drawable.ic_reorder_arrows)
    return drawWithContent {
        drawContent()
        val icon = REORDER_BADGE_ICON_SIZE.toPx()
        val left = size.width - icon - REORDER_BADGE_END_MARGIN.toPx()
        val top = (size.height - icon) / 2f
        translate(left = left, top = top) {
            drawRoundRect(
                color = Color.Black.copy(alpha = REORDER_BADGE_CHIP_ALPHA),
                size = Size(icon, icon),
                cornerRadius = CornerRadius(REORDER_BADGE_CORNER.toPx())
            )
            with(painter) { draw(size = Size(icon, icon)) }
        }
    }
}
