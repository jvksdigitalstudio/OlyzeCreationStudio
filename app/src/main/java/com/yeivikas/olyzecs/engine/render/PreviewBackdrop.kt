package com.yeivikas.olyzecs.engine.render

/**
 * Qué se pinta DETRÁS de las capas en la VISTA PREVIA del editor.
 *
 * Es un concepto SOLO de la vista previa: el lienzo del proyecto y la exportación de video siguen usando siempre
 * el fondo de croma ([CANVAS_CHROMA_KEY_ARGB]) porque `VideoExporter` y `ThumbnailRenderer` tienen su propio
 * `LayerDrawer` y nunca reciben un [PreviewBackdrop]. Cambiarlo no toca el proyecto, no entra en el deshacer ni
 * se guarda en `project.json`.
 *
 * Es un modelo de datos puro (sin Android ni OpenGL) para poder probarlo en JVM; quien lo dibuja es
 * [BackdropDrawer].
 */
sealed interface PreviewBackdrop {

    /** El fondo de croma del lienzo — lo que hay desde siempre. Es el valor por defecto de todo el pipeline. */
    data object CanvasColor : PreviewBackdrop

    /** Un color sólido y opaco (negro, gris, blanco o uno elegido por el usuario). */
    data class Solid(val argb: Int) : PreviewBackdrop

    /**
     * Tablero de ajedrez: la forma estándar de los editores profesionales de mostrar la transparencia.
     * [cellPx] es el lado de cada casilla en píxeles de la superficie (la UI lo calcula desde dp para que se vea
     * igual en cualquier densidad).
     */
    data class Checkerboard(val lightArgb: Int, val darkArgb: Int, val cellPx: Int) : PreviewBackdrop {
        init {
            require(cellPx >= MIN_CELL_PX) { "cellPx debe ser >= $MIN_CELL_PX (era $cellPx)" }
        }
    }

    companion object {
        /** Casilla mínima: por debajo el tablero deja de leerse como tablero y se convierte en ruido. */
        const val MIN_CELL_PX = 4
    }
}

/**
 * Verde croma del lienzo (#00B140). `LayerDrawer.ensureInitialized` usa el mismo valor (0, 177, 64) para la
 * exportación y las miniaturas; si alguno cambia, deben cambiar juntos.
 */
const val CANVAS_CHROMA_KEY_ARGB: Int = 0xFF00B140.toInt()

/**
 * Color con el que se limpia el framebuffer antes de dibujar este fondo. Para [PreviewBackdrop.Checkerboard] es el
 * color de la casilla clara: si por cualquier razón el shader del tablero no estuviera disponible, la vista previa
 * queda con un fondo liso razonable en vez de roto.
 */
fun PreviewBackdrop.clearColorArgb(): Int = when (this) {
    PreviewBackdrop.CanvasColor -> CANVAS_CHROMA_KEY_ARGB
    is PreviewBackdrop.Solid -> argb
    is PreviewBackdrop.Checkerboard -> lightArgb
}
