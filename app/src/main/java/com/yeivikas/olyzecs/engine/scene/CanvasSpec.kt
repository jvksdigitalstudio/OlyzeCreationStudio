package com.yeivikas.olyzecs.engine.scene

/**
 * El espacio de composición REAL de un proyecto — ancho y alto propios
 * en píxeles, no una relación abstracta que recién se convierte en
 * píxeles al exportar (ver ADR-005, contexto: así funciona hoy
 * `AspectRatioPreset` + `computeExportDimensions`, y es exactamente lo
 * que este tipo reemplaza).
 *
 * [originPresetId] es puramente informativo ("este canvas se creó
 * desde el preset Instagram Reel") — NUNCA una dependencia funcional.
 * Un [CanvasSpec] ya tiene sus propias `widthPx`/`heightPx`; si el
 * preset de origen cambia o desaparece del catálogo en una versión
 * futura de la app, este [CanvasSpec] no se ve afectado en absoluto
 * (ver ADR-005, punto 17 del prompt maestro original).
 */
data class CanvasSpec(
    val widthPx: Int,
    val heightPx: Int,
    val originPresetId: String? = null
) {

    init {
        require(widthPx > 0) { "widthPx debe ser positivo (recibido: $widthPx)" }
        require(heightPx > 0) { "heightPx debe ser positivo (recibido: $heightPx)" }
    }

    val aspect: AspectRatio
        get() = AspectRatio.of(widthPx, heightPx)

    val orientation: Orientation
        get() = Orientation.of(widthPx, heightPx)
}
