package com.yeivikas.olyzecs.engine.export

import com.yeivikas.olyzecs.engine.scene.AspectRatioPreset

/**
 * Preset de calidad de exportación: define bitrate y el lado corto del
 * video en píxeles. Los bitrates apuntan a calidad de "master" profesional
 * (bastante por encima del mínimo que pide YouTube para subir, más cerca
 * de lo que graba nativamente una cámara flagship en H.264), no al mínimo
 * aceptable — para exportar con el mejor detalle que el hardware permita,
 * no solo "que se vea bien en la compresión de una plataforma". Se
 * re-escalan a más fps en tiempo de export (ver EditorViewModel.exportVideo).
 *
 * FULL_HD conserva su nombre de constante por compatibilidad con el único
 * lugar que la referencia por nombre (el default en EditorUiState), pero
 * representa 2K/QHD (1440p) — la resolución que YA usaba antes, solo que
 * estaba mal etiquetada como "Full HD+" (eso es 1080p).
 */
enum class ExportQuality(val label: String, val shortSidePx: Int, val bitRate: Int) {
    DRAFT("Borrador", 720, 8_000_000),
    HD("Full HD", 1080, 20_000_000),
    FULL_HD("2K (QHD)", 1440, 35_000_000),
    UHD_4K("4K (UHD)", 2160, 80_000_000)
}

/** Calcula (widthPx, heightPx) a partir de calidad + aspecto elegidos. */
fun computeExportDimensions(quality: ExportQuality, aspect: AspectRatioPreset): Pair<Int, Int> {
    val shortSide = quality.shortSidePx
    // Redondeado a múltiplo de 2 (requisito común de encoders AVC).
    val longSide = (shortSide * 16 / 9) and 1.inv()
    return when (aspect) {
        AspectRatioPreset.REELS -> shortSide to longSide       // vertical: angosto x alto
        AspectRatioPreset.SQUARE -> shortSide to shortSide
        AspectRatioPreset.WIDESCREEN -> longSide to shortSide  // horizontal: ancho x bajo
    }
}
