package com.yeivikas.olyzecs.engine.audio

/**
 * Cambios de COLOR de identidad de un [AudioTrack], como transformaciones
 * puras. Los comparten el cambio de color de UN carril y el de varios a la
 * vez ("Multicolor"), para que ambos produzcan exactamente el mismo carril.
 */

/** Color sólido elegido a mano. Manda por encima de cualquier degradado activo (mismo criterio que `Layer`). */
fun AudioTrack.withCustomColor(colorArgb: Int, useBlackAndWhiteMode: Boolean): AudioTrack = copy(
    customColorArgb = colorArgb,
    useGradientColor = false,
    useBlackAndWhiteMode = useBlackAndWhiteMode
)

/** Degradado de dos colores. [sampledColorArgb] es el tono sólido ya muestreado del grupo (Multicolor); sin él conserva el color sólido actual. */
fun AudioTrack.withGradient(
    startArgb: Int,
    endArgb: Int,
    angleDegrees: Float,
    isRadial: Boolean,
    useBlackAndWhiteMode: Boolean,
    sampledColorArgb: Int? = null
): AudioTrack = copy(
    customGradientStartArgb = startArgb,
    customGradientEndArgb = endArgb,
    useGradientColor = true,
    gradientAngleDegrees = angleDegrees,
    gradientIsRadial = isRadial,
    customColorArgb = sampledColorArgb ?: customColorArgb,
    useBlackAndWhiteMode = useBlackAndWhiteMode
)

/**
 * Vuelve al color de fábrica (`null` = sin personalizar, ver `AUDIO_TRACK_COLOR`
 * en `AudioTrackRow`). Un clip de audio no tiene píxeles de los que extraer un
 * color dominante, así que "restablecer" siempre es limpiar la personalización.
 */
fun AudioTrack.withDefaultColor(): AudioTrack = copy(
    customColorArgb = null,
    customGradientStartArgb = null,
    customGradientEndArgb = null,
    useGradientColor = false,
    useBlackAndWhiteMode = false
)
