package com.yeivikas.olyzecs.engine.scene

/**
 * Un par (ancho, alto) en píxeles, sin nombre de plataforma ni
 * metadata de presentación — solo el dato físico. Existe separado de
 * [FormatPreset] a propósito (ver ADR-005, decisión D2): varios
 * presets de plataformas distintas pueden compartir exactamente el
 * mismo [CanvasFormat] (ej. Instagram Reel, TikTok y YouTube Shorts
 * comparten 1080×1920) sin duplicar el número en cada uno — cada
 * preset solo referencia el `CanvasFormat` que le corresponde.
 */
data class CanvasFormat(val widthPx: Int, val heightPx: Int) {

    init {
        require(widthPx > 0) { "widthPx debe ser positivo (recibido: $widthPx)" }
        require(heightPx > 0) { "heightPx debe ser positivo (recibido: $heightPx)" }
    }

    val aspect: AspectRatio
        get() = AspectRatio.of(widthPx, heightPx)

    val orientation: Orientation
        get() = Orientation.of(widthPx, heightPx)
}
