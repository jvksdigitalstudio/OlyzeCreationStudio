package com.yeivikas.olyzecs.engine.effects

import kotlinx.serialization.Serializable

/**
 * Ajustes de "look" cinematográfico aplicados en el mismo pase de shader
 * que dibuja cada capa. Son propios de cada capa (ver [Layer.lookSettings]),
 * para que cada imagen pueda tener su propia corrección de color.
 *
 * Todos los valores están pensados para moverse alrededor de un "neutro"
 * (sin efecto) en 0 o 1 según corresponda, de forma que el usuario pueda
 * partir de la imagen original y ajustar con sliders.
 */
@Serializable
data class LookSettings(
    val saturation: Float = 1f,     // 0 = blanco y negro, 1 = original, >1 = más saturado
    val contrast: Float = 1f,       // 1 = original
    val warmth: Float = 0f,         // -1 = frío (azulado), 0 = neutro, 1 = cálido (anaranjado)
    val exposure: Float = 0f,       // -1..1, compensación de exposición (multiplicativa)
    val tint: Float = 0f,           // -1 = verde, 0 = neutro, 1 = magenta
    val shadowsLift: Float = 0f,    // 0..0.3, levanta el negro (look "película desteñida")
    val highlightsRolloff: Float = 0f, // 0..1, suaviza/comprime las luces altas (evita quemados)
    val splitToneIntensity: Float = 0f, // 0..1, split-tone cine (sombras frías / luces cálidas)
    val vignetteIntensity: Float = 0f, // 0 = sin viñeta, 1 = viñeta fuerte
    val grainIntensity: Float = 0f,    // 0 = sin grano, 1 = grano fuerte
    val glowIntensity: Float = 0f,     // 0 = sin glow, 1 = glow fuerte en zonas brillantes
    val glowThreshold: Float = 0.7f,    // luminancia a partir de la cual algo "brilla"
    val cameraShakeIntensity: Float = 0f, // 0 = cámara fija, 1 = temblor handheld notorio
    val lensDistortion: Float = 0f,     // -1 = cojín, 0 = sin distorsión, 1 = barril (gran angular)
    val chromaticAberration: Float = 0f, // 0..1, desfase de color en los bordes (look lente real)
    val lensFlareIntensity: Float = 0f,  // 0..1, destello anamórfico horizontal en zonas brillantes
    val anamorphicBokeh: Float = 0f,     // 0..1, estira el desenfoque de profundidad horizontalmente
    val motionBlurIntensity: Float = 0f  // 0..1, borroneo direccional automático según la velocidad de la cámara
)
