package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.engine.render.PreviewBackdrop
import com.yeivikas.olyzecs.engine.render.clearColorArgb

/**
 * Qué fondo ve el usuario en el visor según el modo, y de qué color es lo que hay DETRÁS de la superficie GL.
 *
 * ## Por qué existe
 * La regla «en modo edición se ve el fondo de trabajo elegido; fuera, el color del lienzo de siempre» la necesitan
 * DOS consumidores: el renderer (qué pinta) y el contenedor de Compose que rodea a la superficie GL (de qué color
 * es lo que asoma antes de que el primer frame GL llegue). Si cada uno la escribiera por su cuenta, podrían
 * divergir — y al entrar al modo edición se vería un frame del color equivocado (el verde del lienzo asomando
 * antes de un fondo negro o blanco). Una sola función, usada por ambos, lo hace imposible.
 *
 * Lógica pura (sin Android ni Compose): se prueba en JVM. Este objeto no dibuja ni guarda nada.
 */
object EditViewportBackdrop {

    /** El fondo que debe pintar el renderer: el de trabajo solo mientras se edita una capa. */
    fun effective(isEditing: Boolean, editBackdrop: PreviewBackdrop): PreviewBackdrop =
        if (isEditing) editBackdrop else PreviewBackdrop.CanvasColor

    /**
     * Color (ARGB) del contenedor detrás de la superficie GL. Es exactamente el color con el que el renderer limpia
     * el frame con [effective] — para un tablero, el de su casilla clara, igual que en `BackdropDrawer` — de modo
     * que el paso del contenedor al primer frame GL no produce ningún cambio de color visible.
     */
    fun behindSurfaceArgb(isEditing: Boolean, editBackdrop: PreviewBackdrop): Int =
        effective(isEditing, editBackdrop).clearColorArgb()
}
