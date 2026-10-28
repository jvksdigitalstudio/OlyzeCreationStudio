package com.yeivikas.olyzecs.engine.scene

import com.yeivikas.olyzecs.engine.color.GradientGroupRef

/**
 * Cambios de COLOR de identidad de una [Layer], como transformaciones
 * puras. Los comparten el cambio de color de UNA capa y el de varias a la
 * vez ("Multicolor"), y son el equivalente exacto de
 * `engine.audio.AudioTrackColor.kt` para carriles de audio, para que una
 * capa y un carril de la misma selección terminen con el mismo color.
 *
 * Toda transformación que NO sea [withGroupGradient] saca a la capa de su
 * degradado de grupo ([Layer.gradientGroup] = null): un color elegido a
 * mano deja de ser parte de aquel reparto.
 */

/**
 * Color sólido elegido a mano. Manda por encima de cualquier degradado que
 * hubiera quedado activo: color y degradado son modos excluyentes (los
 * extremos del degradado se conservan por si el usuario vuelve a prenderlo,
 * pero dejan de pintarse). Guarda también el modo Negro y Blanco con el que
 * se armó, para que el interruptor del diálogo se reabra en el mismo estado.
 */
fun Layer.withCustomColor(colorArgb: Int, useBlackAndWhiteMode: Boolean): Layer = copy(
    customColorArgb = colorArgb,
    useGradientColor = false,
    useBlackAndWhiteMode = useBlackAndWhiteMode,
    gradientGroup = null
)

/** Degradado propio de dos colores DENTRO de la fila. No toca el color sólido guardado. */
fun Layer.withGradient(
    startArgb: Int,
    endArgb: Int,
    angleDegrees: Float,
    isRadial: Boolean,
    useBlackAndWhiteMode: Boolean
): Layer = copy(
    customGradientStartArgb = startArgb,
    customGradientEndArgb = endArgb,
    useGradientColor = true,
    gradientAngleDegrees = angleDegrees,
    gradientIsRadial = isRadial,
    useBlackAndWhiteMode = useBlackAndWhiteMode,
    gradientGroup = null
)

/**
 * Parte de un degradado repartido entre varias filas: la capa dibuja el
 * color sólido [sampledArgb] que le tocó (`useGradientColor` = false) y
 * conserva los extremos y el [group] solo para poder reabrir el diálogo de
 * color con el reparto original.
 */
fun Layer.withGroupGradient(
    startArgb: Int,
    endArgb: Int,
    angleDegrees: Float,
    isRadial: Boolean,
    useBlackAndWhiteMode: Boolean,
    sampledArgb: Int,
    group: GradientGroupRef
): Layer = copy(
    customColorArgb = sampledArgb,
    customGradientStartArgb = startArgb,
    customGradientEndArgb = endArgb,
    useGradientColor = false,
    gradientAngleDegrees = angleDegrees,
    gradientIsRadial = isRadial,
    useBlackAndWhiteMode = useBlackAndWhiteMode,
    gradientGroup = group
)

/**
 * Restablecer: vuelve al color de fábrica de la capa, el dominante de su
 * propia imagen al importarla ([Layer.importedDefaultColorArgb]) — no a un
 * color genérico de la paleta. Limpia degradado, modo Negro y Blanco y grupo.
 */
fun Layer.withDefaultColor(): Layer = copy(
    customColorArgb = importedDefaultColorArgb,
    customGradientStartArgb = null,
    customGradientEndArgb = null,
    useGradientColor = false,
    useBlackAndWhiteMode = false,
    gradientGroup = null
)
