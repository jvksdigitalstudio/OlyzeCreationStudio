package com.yeivikas.olyzecs.engine.export

import com.yeivikas.olyzecs.engine.scene.CanvasSpec
import com.yeivikas.olyzecs.engine.scene.Orientation

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

/**
 * Calcula (widthPx, heightPx) del video final a partir de la calidad
 * elegida y el [CanvasSpec] real del proyecto (ver ADR-005, Fase D).
 *
 * Antes recibía un `AspectRatioPreset` (3 valores fijos) y reinventaba el
 * aspecto internamente con un `when` exhaustivo hardcodeado a 16:9. Ahora
 * escala el aspecto REAL del Canvas del proyecto — que puede ser
 * cualquiera de los presets del catálogo (Fase B) o, a futuro, un tamaño
 * Custom (Fase E) — a la calidad elegida, sin conocer de antemano qué
 * relación de aspecto puede llegar a tener un Canvas.
 *
 * El lado corto de salida es siempre [ExportQuality.shortSidePx]; el lado
 * largo se deriva de la proporción real ancho/alto del Canvas (no de una
 * relación reducida a "unidades bonitas" como [com.yeivikas.olyzecs.engine.scene.AspectRatio],
 * para no introducir ningún redondeo de más: se usa directamente
 * `widthPx`/`heightPx` del Canvas). Redondeado a múltiplo de 2, igual que
 * antes, por ser un requisito común de encoders AVC.
 *
 * Para los 3 formatos que reemplazan a los valores históricos de
 * `AspectRatioPreset` (1080×1920, 1080×1080, 1920×1080, ver
 * `StaticFormatCatalog`), esta función da exactamente los mismos números
 * en píxeles que daba la versión anterior — no es una aproximación, es la
 * misma aritmética generalizada a un Canvas arbitrario en vez de a 3
 * casos fijos.
 */
fun computeExportDimensions(quality: ExportQuality, canvas: CanvasSpec): Pair<Int, Int> {
    val shortSide = quality.shortSidePx
    val canvasShortPx = minOf(canvas.widthPx, canvas.heightPx)
    val canvasLongPx = maxOf(canvas.widthPx, canvas.heightPx)
    // Redondeado a múltiplo de 2 (requisito común de encoders AVC).
    val longSide = (shortSide * canvasLongPx / canvasShortPx) and 1.inv()
    return when (canvas.orientation) {
        Orientation.PORTRAIT -> shortSide to longSide       // vertical: angosto x alto
        Orientation.SQUARE -> shortSide to shortSide
        Orientation.LANDSCAPE -> longSide to shortSide      // horizontal: ancho x bajo
    }
}
