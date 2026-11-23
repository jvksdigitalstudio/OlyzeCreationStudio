package com.yeivikas.olyzecs.ui.effects

import kotlin.math.ceil
import kotlin.math.max

/**
 * De qué tamaño decodificar las copias de vista previa. Matemática pura, sin Android.
 *
 * La copia "de reposo" ([EffectSources.display]) debe tener al menos tantos píxeles como los que la capa ocupa en
 * pantalla; con menos, la vista previa se ve peor que el resultado final aunque este sea perfecto (el síntoma
 * reportado: bordes escalonados al aplicar un efecto).
 *
 * ## Por qué hay que pedir `2·T − 1` y no `T`
 * `BitmapFactory` solo reduce en POTENCIAS DE DOS: elige el menor divisor `k` tal que `lado / k ≤ límite`. Pedir un
 * límite de `T` a una imagen de 2100 px con `T = 2048` da `k = 2` → 1050 px, la mitad de lo necesario (en el peor caso,
 * el 50 %). En cambio, con límite `2T − 1`, el divisor anterior (`k / 2`) superaba `2T − 1`, luego `lado / k ≥ T`: el
 * resultado nunca baja de [T] siempre que el original tenga al menos esos píxeles. Se comprueba en las pruebas contra
 * el modelo exacto de `ImageDecoding.computeSampleSize`.
 */
object EffectsPreviewSizing {

    /** Lado mayor de la copia rápida de arrastre. Ligera a propósito: se recalcula varias veces por segundo. */
    const val QUICK_MAX_DIMENSION = 260

    /**
     * Techo del objetivo de la copia de reposo. Con el límite de decodificación `2·T − 1`, el mayor resultado posible es
     * `2·T` (3072 px → ≈ 38 MB por bitmap cuadrado). Es un tope de memoria, NO medido en la tablet objetivo.
     */
    const val DISPLAY_TARGET_CEILING = 1536

    /** Si aún no se midió la pantalla (primer frame), un objetivo razonable de tablet en vez de uno diminuto. */
    const val DISPLAY_TARGET_FALLBACK = 1280

    /**
     * Cuántos píxeles de lado mayor debería tener la copia de reposo: el de la pantalla por la fracción que ocupa la
     * capa, acotado entre el doble de la copia rápida (para que siempre sea una mejora) y [DISPLAY_TARGET_CEILING].
     *
     * @param fillFraction fracción de la pantalla que ocupa la imagen en la vista de edición (ver `EditViewFit`).
     */
    fun displayTargetPx(viewportWidthPx: Int, viewportHeightPx: Int, fillFraction: Float): Int {
        val longSide = max(viewportWidthPx, viewportHeightPx)
        if (longSide <= 0 || !fillFraction.isFinite() || fillFraction <= 0f) return DISPLAY_TARGET_FALLBACK
        val onScreen = ceil(longSide * fillFraction).toInt()
        return onScreen.coerceIn(QUICK_MAX_DIMENSION * 2, DISPLAY_TARGET_CEILING)
    }

    /**
     * El límite que hay que pasar al decodificador para que la imagen resultante tenga al menos [targetPx] de lado mayor
     * (si el original los tiene). Ver la explicación en la documentación del objeto.
     */
    fun decodeLimitFor(targetPx: Int): Int {
        require(targetPx > 0) { "Objetivo inválido: $targetPx" }
        return targetPx * 2 - 1
    }

    /** Atajo: el límite de decodificación de la copia de reposo para una pantalla dada. */
    fun displayDecodeLimit(viewportWidthPx: Int, viewportHeightPx: Int, fillFraction: Float): Int =
        decodeLimitFor(displayTargetPx(viewportWidthPx, viewportHeightPx, fillFraction))
}
