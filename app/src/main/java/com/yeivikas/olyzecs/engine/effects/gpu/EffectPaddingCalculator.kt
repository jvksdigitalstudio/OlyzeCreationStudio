package com.yeivikas.olyzecs.engine.effects.gpu

import kotlin.math.max

/** Margen, en píxeles, que un plan añade a cada lado de la imagen. Nunca negativo. */
data class EffectPadding(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) { "El margen no puede ser negativo: $this" }
    }

    val isZero: Boolean get() = left == 0 && top == 0 && right == 0 && bottom == 0

    fun maxWith(other: EffectPadding): EffectPadding =
        EffectPadding(max(left, other.left), max(top, other.top), max(right, other.right), max(bottom, other.bottom))

    companion object {
        val ZERO = EffectPadding(0, 0, 0, 0)
    }
}

/**
 * Cuánto se extiende un plan más allá de la imagen (sombras, resplandor, contorno y reflejo salen del rectángulo
 * original). Pura. La textura resultante mide `ancho + izquierda + derecha` por `alto + arriba + abajo`, y el quad de
 * la capa se amplía en la misma proporción para que el sujeto conserve su tamaño y posición (ADR-019 §4.1).
 *
 * El `when` sobre [EffectPassKind] es EXHAUSTIVO a propósito: añadir un tipo de pase sin declarar su margen no compila.
 */
object EffectPaddingCalculator {

    fun calculate(plan: EffectPassPlan, imageWidth: Int, imageHeight: Int): EffectPadding {
        require(imageWidth > 0 && imageHeight > 0) { "Dimensiones inválidas: ${imageWidth}x$imageHeight" }
        return plan.passes.fold(EffectPadding.ZERO) { total, pass ->
            total.maxWith(paddingOf(pass, imageWidth, imageHeight))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun paddingOf(kind: EffectPassKind, imageWidth: Int, imageHeight: Int): EffectPadding = when (kind) {
        EffectPassKind.IDENTITY -> EffectPadding.ZERO
        EffectPassKind.COLOR_BASIC -> EffectPadding.ZERO
    }
}
