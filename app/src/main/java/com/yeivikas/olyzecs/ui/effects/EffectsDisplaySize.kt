package com.yeivikas.olyzecs.ui.effects

import android.content.Context

/**
 * Adaptador de Android para [EffectsPreviewSizing]: lee el tamaño de la pantalla y devuelve el límite que hay que pasar
 * al decodificador para la copia de reposo. La pantalla es una cota correcta (y estable, sin depender de que el visor
 * ya se haya medido) de cuántos píxeles puede ocupar la imagen.
 *
 * @param fillFraction fracción de la pantalla que ocupa la imagen en la vista de edición.
 */
fun Context.effectsDisplayDecodeLimit(fillFraction: Float): Int {
    val metrics = resources.displayMetrics
    return EffectsPreviewSizing.displayDecodeLimit(metrics.widthPixels, metrics.heightPixels, fillFraction)
}
