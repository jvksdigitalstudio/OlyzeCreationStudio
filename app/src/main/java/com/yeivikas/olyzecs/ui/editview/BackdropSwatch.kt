package com.yeivikas.olyzecs.ui.editview

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.yeivikas.olyzecs.engine.render.CANVAS_CHROMA_KEY_ARGB
import com.yeivikas.olyzecs.engine.render.PreviewBackdrop

/** Casillas por lado del tablero en la miniatura (el tablero real se dibuja en la GPU, ver BackdropDrawer). */
private const val SWATCH_CHECKER_CELLS = 4

/**
 * Miniatura de un fondo. Responsabilidad única: pintar el relleno. El marco, el ícono y los gestos son de quien la
 * usa. Lee los MISMOS colores que el renderer (vía [PreviewBackdrop]), así miniatura y vista previa no pueden
 * divergir.
 */
@Composable
internal fun BackdropSwatch(backdrop: PreviewBackdrop, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (backdrop) {
            PreviewBackdrop.CanvasColor -> drawRect(Color(CANVAS_CHROMA_KEY_ARGB))
            is PreviewBackdrop.Solid -> drawRect(Color(backdrop.argb))
            is PreviewBackdrop.Checkerboard -> {
                val cell = size.width / SWATCH_CHECKER_CELLS
                drawRect(Color(backdrop.lightArgb))
                for (row in 0 until SWATCH_CHECKER_CELLS) {
                    for (column in 0 until SWATCH_CHECKER_CELLS) {
                        if ((row + column) % 2 == 1) {
                            drawRect(
                                color = Color(backdrop.darkArgb),
                                topLeft = Offset(column * cell, row * cell),
                                size = Size(cell, cell)
                            )
                        }
                    }
                }
            }
        }
    }
}
