package com.yeivikas.olyzecs.engine.effects.gpu

/** Cada tipo de pase que la etapa de efectos GPU sabe (o sabrá) ejecutar. Un shader por tipo. */
enum class EffectPassKind {
    /** Copia la textura sin modificarla. Solo para verificar la tubería (FBO, pool, caché); nunca sale de [LayerEffectParams]. */
    IDENTITY,

    /** Brillo, contraste, saturación, matiz y nitidez. */
    COLOR_BASIC
}

/** Lista ordenada de pases a ejecutar para unos parámetros. Vacía = no hay etapa de efectos. */
data class EffectPassPlan(val passes: List<EffectPassKind>) {
    val isEmpty: Boolean get() = passes.isEmpty()

    companion object {
        val EMPTY = EffectPassPlan(emptyList())

        /** Un único pase identidad: lo usa el autotest para comprobar que la tubería GPU no altera la imagen. */
        val IDENTITY_ONLY = EffectPassPlan(listOf(EffectPassKind.IDENTITY))
    }
}

/**
 * Traduce parámetros a un plan de pases. Pura: no sabe nada de GL. Los parámetros neutros producen un plan VACÍO, de
 * modo que una capa sin efectos no paga ningún coste ni se aparta de su camino de dibujo normal.
 */
object EffectPassPlanner {

    fun plan(params: LayerEffectParams): EffectPassPlan {
        val clean = params.sanitized()
        if (clean.isNeutral) return EffectPassPlan.EMPTY
        return EffectPassPlan(
            buildList {
                if (!clean.colorBasic.isNeutral) add(EffectPassKind.COLOR_BASIC)
            }
        )
    }
}
