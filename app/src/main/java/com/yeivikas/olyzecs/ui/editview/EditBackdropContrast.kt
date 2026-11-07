package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.engine.render.CANVAS_CHROMA_KEY_ARGB
import com.yeivikas.olyzecs.engine.render.PreviewBackdrop
import kotlin.math.pow

/**
 * Qué color debe tener lo que se dibuja ENCIMA de la miniatura de un fondo para que se lea siempre.
 *
 * ## Por qué existe
 * La miniatura del botón de fondo muestra el fondo elegido; si encima va un ícono de un color fijo, desaparece
 * con el fondo que coincida con él (ícono claro sobre blanco, oscuro sobre negro). Aquí se elige negro o blanco
 * según cuál contraste más con el fondo, con la fórmula de contraste de WCAG 2.x.
 *
 * Es matemática pura (sin Android ni Compose, trabaja con enteros ARGB): se prueba en JVM. Quien dibuja
 * ([EditBackgroundButton]) solo pregunta [iconArgbOver].
 */
object EditBackdropContrast {

    private const val DARK_ARGB: Int = 0xFF000000.toInt()
    private const val LIGHT_ARGB: Int = 0xFFFFFFFF.toInt()

    private const val CHANNEL_MAX = 255.0
    private const val LINEAR_THRESHOLD = 0.03928
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_DIVISOR = 1.055
    private const val GAMMA_EXPONENT = 2.4
    private const val WEIGHT_RED = 0.2126
    private const val WEIGHT_GREEN = 0.7152
    private const val WEIGHT_BLUE = 0.0722
    private const val CONTRAST_OFFSET = 0.05

    /**
     * Color con el que se ve el fondo "en conjunto": el sólido tal cual; el verde croma para el color del lienzo;
     * para un tablero, el promedio de sus dos casillas (es lo que percibe el ojo a distancia de miniatura).
     */
    fun representativeArgb(backdrop: PreviewBackdrop): Int = when (backdrop) {
        PreviewBackdrop.CanvasColor -> CANVAS_CHROMA_KEY_ARGB
        is PreviewBackdrop.Solid -> backdrop.argb
        is PreviewBackdrop.Checkerboard -> average(backdrop.lightArgb, backdrop.darkArgb)
    }

    /** Negro o blanco opacos: el que más contraste da sobre [backdrop]. */
    fun iconArgbOver(backdrop: PreviewBackdrop): Int {
        val background = representativeArgb(backdrop)
        return if (contrastRatio(DARK_ARGB, background) >= contrastRatio(LIGHT_ARGB, background)) DARK_ARGB else LIGHT_ARGB
    }

    /** Luminancia relativa WCAG de un color ARGB (el alfa se ignora: los fondos son siempre opacos). */
    fun relativeLuminance(argb: Int): Double {
        val red = linearChannel((argb shr 16) and 0xFF)
        val green = linearChannel((argb shr 8) and 0xFF)
        val blue = linearChannel(argb and 0xFF)
        return WEIGHT_RED * red + WEIGHT_GREEN * green + WEIGHT_BLUE * blue
    }

    /** Relación de contraste WCAG entre dos colores: de 1 (iguales) a 21 (negro sobre blanco). */
    fun contrastRatio(firstArgb: Int, secondArgb: Int): Double {
        val first = relativeLuminance(firstArgb)
        val second = relativeLuminance(secondArgb)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
    }

    private fun linearChannel(value: Int): Double {
        val normalized = value / CHANNEL_MAX
        return if (normalized <= LINEAR_THRESHOLD) normalized / LINEAR_DIVISOR
        else ((normalized + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA_EXPONENT)
    }

    private fun average(firstArgb: Int, secondArgb: Int): Int {
        fun channel(shift: Int): Int = ((((firstArgb shr shift) and 0xFF) + ((secondArgb shr shift) and 0xFF)) / 2) and 0xFF
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
