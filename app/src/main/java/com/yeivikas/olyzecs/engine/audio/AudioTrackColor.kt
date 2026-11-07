package com.yeivikas.olyzecs.engine.audio

import com.yeivikas.olyzecs.engine.color.GradientGroupRef

/**
 * Cambios de COLOR de identidad de un [AudioTrack], como transformaciones
 * puras. Los comparten el cambio de color de UN carril y el de varios a la
 * vez ("Multicolor"), para que ambos produzcan exactamente el mismo carril.
 * Son el equivalente exacto de `engine.scene.LayerColor.kt` para capas.
 *
 * Toda transformación que NO sea [withGroupGradient] saca al carril de su
 * degradado de grupo ([AudioTrack.gradientGroup] = null): un color elegido
 * a mano deja de ser parte de aquel reparto.
 */

/** Color sólido elegido a mano. Manda por encima de cualquier degradado activo (mismo criterio que `Layer`). */
fun AudioTrack.withCustomColor(colorArgb: Int, useBlackAndWhiteMode: Boolean): AudioTrack = copy(
    customColorArgb = colorArgb,
    useGradientColor = false,
    useBlackAndWhiteMode = useBlackAndWhiteMode,
    gradientGroup = null
)

/** Degradado propio de dos colores DENTRO de la fila. No toca el color sólido guardado. */
fun AudioTrack.withGradient(
    startArgb: Int,
    endArgb: Int,
    angleDegrees: Float,
    isRadial: Boolean,
    useBlackAndWhiteMode: Boolean
): AudioTrack = copy(
    customGradientStartArgb = startArgb,
    customGradientEndArgb = endArgb,
    useGradientColor = true,
    gradientAngleDegrees = angleDegrees,
    gradientIsRadial = isRadial,
    useBlackAndWhiteMode = useBlackAndWhiteMode,
    gradientGroup = null
)

/**
 * Parte de un degradado repartido entre varias filas: la fila dibuja el
 * color sólido [sampledArgb] que le tocó (`useGradientColor` = false) y
 * conserva los extremos y el [group] solo para poder reabrir el diálogo de
 * color con el reparto original.
 */
fun AudioTrack.withGroupGradient(
    startArgb: Int,
    endArgb: Int,
    angleDegrees: Float,
    isRadial: Boolean,
    useBlackAndWhiteMode: Boolean,
    sampledArgb: Int,
    group: GradientGroupRef
): AudioTrack = copy(
    customColorArgb = sampledArgb,
    customGradientStartArgb = startArgb,
    customGradientEndArgb = endArgb,
    useGradientColor = false,
    gradientAngleDegrees = angleDegrees,
    gradientIsRadial = isRadial,
    useBlackAndWhiteMode = useBlackAndWhiteMode,
    gradientGroup = group
)

/** Restablecer: limpia toda la personalización de color; el carril vuelve a su color de identidad de audio por defecto. */
fun AudioTrack.withDefaultColor(): AudioTrack = copy(
    customColorArgb = null,
    customGradientStartArgb = null,
    customGradientEndArgb = null,
    useGradientColor = false,
    useBlackAndWhiteMode = false,
    gradientGroup = null
)
