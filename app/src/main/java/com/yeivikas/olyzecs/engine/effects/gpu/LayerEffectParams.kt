package com.yeivikas.olyzecs.engine.effects.gpu

import kotlinx.serialization.Serializable

/**
 * Parámetros de efectos NO DESTRUCTIVOS de una capa: viven en la propia capa y se aplican en la GPU, sin tocar nunca
 * el archivo original (ADR-019).
 *
 * Se guarda en el proyecto directamente (`@Serializable`, igual que `LookSettings`). Un proyecto sin este campo carga con
 * parámetros neutros, y uno guardado por una versión futura con bloques que esta no conoce los ignora
 * (`ignoreUnknownKeys`): la evolución es por campos nuevos con valor por defecto. Los datos que vienen del disco se
 * pasan por [sanitized] al cargar.
 *
 * Los rangos y la noción de «neutro» replican EXACTAMENTE los de `ImageEffectsParams` (el camino CPU actual), para que
 * un mismo valor signifique lo mismo en ambos caminos mientras convivan.
 *
 * Hoy solo contiene Color Básico; cada fase del ADR-019 añade su bloque (con valor por defecto neutro, así los
 * proyectos ya guardados siguen cargando).
 */
@Serializable
data class LayerEffectParams(
    val colorBasic: ColorBasicParams = ColorBasicParams()
) {
    /** `true` si no hay nada que aplicar: la capa se dibuja directamente desde su textura, sin etapa de efectos. */
    val isNeutral: Boolean get() = colorBasic.isNeutral

    /** Copia con todos los valores dentro de rango y finitos. Aplicar siempre a datos que vienen de fuera (disco, API). */
    fun sanitized(): LayerEffectParams = copy(colorBasic = colorBasic.sanitized())

    companion object {
        val NEUTRAL = LayerEffectParams()
    }
}

/** Brillo, contraste, saturación, matiz y nitidez. Mismos rangos y valores neutros que `ImageEffectsParams`. */
@Serializable
data class ColorBasicParams(
    /** -1..1 — brillo aditivo por canal. */
    val brightness: Float = 0f,
    /** 0..2 — 1 = neutro. */
    val contrast: Float = 1f,
    /** 0..2 — 0 = blanco y negro, 1 = original. */
    val saturation: Float = 1f,
    /** 0..360 — rotación de matiz; 0 = sin cambio. */
    val hue: Float = 0f,
    /** 0..1 — nitidez (unsharp mask). */
    val sharpen: Float = 0f
) {
    val isNeutral: Boolean
        get() = kotlin.math.abs(brightness) <= NEUTRAL_TOLERANCE &&
            kotlin.math.abs(contrast - 1f) <= NEUTRAL_TOLERANCE &&
            kotlin.math.abs(saturation - 1f) <= NEUTRAL_TOLERANCE &&
            normalizedHue(hue) <= NEUTRAL_TOLERANCE &&
            sharpen <= NEUTRAL_TOLERANCE

    fun sanitized(): ColorBasicParams = ColorBasicParams(
        brightness = brightness.finiteOr(0f).coerceIn(-1f, 1f),
        contrast = contrast.finiteOr(1f).coerceIn(0f, 2f),
        saturation = saturation.finiteOr(1f).coerceIn(0f, 2f),
        hue = normalizedHue(hue.finiteOr(0f)),
        sharpen = sharpen.finiteOr(0f).coerceIn(0f, 1f)
    )

    companion object {
        /** La misma tolerancia que usa `ImageEffectsParams.isNeutral`. */
        const val NEUTRAL_TOLERANCE = 0.001f

        private fun normalizedHue(value: Float): Float = ((value % 360f) + 360f) % 360f
        private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
    }
}
