package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.data.EditBackgroundChoice
import com.yeivikas.olyzecs.data.EditBackgroundSettings
import com.yeivikas.olyzecs.engine.render.PreviewBackdrop

/**
 * Paleta fija de los fondos de la vista de edición. Un solo lugar para los colores: el botón del selector
 * (swatches) y el renderer (vía [toBackdrop]) leen los mismos valores, así que lo que se ve en el botón es
 * exactamente lo que se pinta.
 */
object EditBackdropPalette {
    const val CHECKER_LIGHT_A: Int = 0xFFFFFFFF.toInt()
    const val CHECKER_LIGHT_B: Int = 0xFFCCCCCC.toInt()
    const val CHECKER_DARK_A: Int = 0xFF4D4D4D.toInt()
    const val CHECKER_DARK_B: Int = 0xFF333333.toInt()
    const val BLACK: Int = 0xFF000000.toInt()
    const val GRAY: Int = 0xFF808080.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
}

/** Lado de cada casilla del tablero, en dp. Se convierte a píxeles con la densidad real de la pantalla. */
const val EDIT_CHECKER_CELL_DP = 12

/**
 * Traduce la preferencia del usuario a lo que debe pintar el renderer.
 *
 * @param checkerCellPx lado de la casilla en píxeles (la UI lo calcula desde [EDIT_CHECKER_CELL_DP]); se limita
 *   al mínimo legible por si una densidad rara produjera un valor degenerado.
 */
fun EditBackgroundSettings.toBackdrop(checkerCellPx: Int): PreviewBackdrop {
    val cell = checkerCellPx.coerceAtLeast(PreviewBackdrop.MIN_CELL_PX)
    return when (choice) {
        EditBackgroundChoice.CANVAS -> PreviewBackdrop.CanvasColor
        EditBackgroundChoice.CHECKER_LIGHT ->
            PreviewBackdrop.Checkerboard(EditBackdropPalette.CHECKER_LIGHT_A, EditBackdropPalette.CHECKER_LIGHT_B, cell)
        EditBackgroundChoice.CHECKER_DARK ->
            PreviewBackdrop.Checkerboard(EditBackdropPalette.CHECKER_DARK_A, EditBackdropPalette.CHECKER_DARK_B, cell)
        EditBackgroundChoice.BLACK -> PreviewBackdrop.Solid(EditBackdropPalette.BLACK)
        EditBackgroundChoice.GRAY -> PreviewBackdrop.Solid(EditBackdropPalette.GRAY)
        EditBackgroundChoice.WHITE -> PreviewBackdrop.Solid(EditBackdropPalette.WHITE)
        EditBackgroundChoice.CUSTOM -> PreviewBackdrop.Solid(customArgb or EditBackgroundSettings.OPAQUE_ALPHA_MASK)
    }
}

/**
 * Opciones a las que da acceso un solo toque (rotan en este orden). Las demás (tablero oscuro, gris,
 * personalizado) están en el menú de pulsación larga: ofrecer siete opciones por toque obligaría a pasar por
 * todas para llegar a la que se busca.
 */
val EDIT_BACKGROUND_QUICK_CYCLE: List<EditBackgroundChoice> = listOf(
    EditBackgroundChoice.CANVAS,
    EditBackgroundChoice.CHECKER_LIGHT,
    EditBackgroundChoice.BLACK,
    EditBackgroundChoice.WHITE
)

/** La opción que sigue a [this] en la rotación rápida; desde una opción que no está en ella, vuelve al color del lienzo. */
fun EditBackgroundChoice.nextInQuickCycle(): EditBackgroundChoice {
    val index = EDIT_BACKGROUND_QUICK_CYCLE.indexOf(this)
    return if (index < 0) EDIT_BACKGROUND_QUICK_CYCLE.first()
    else EDIT_BACKGROUND_QUICK_CYCLE[(index + 1) % EDIT_BACKGROUND_QUICK_CYCLE.size]
}
