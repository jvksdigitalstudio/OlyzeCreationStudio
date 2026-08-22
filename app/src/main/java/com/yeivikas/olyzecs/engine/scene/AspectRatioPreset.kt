package com.yeivikas.olyzecs.engine.scene

/**
 * Preset de formato/aspecto del canvas del proyecto, pensado para las
 * plataformas donde se sube el video. Vive en `engine.scene` (y no en
 * `engine.export`) porque describe el LIENZO del proyecto en sí — se
 * elige al crear el proyecto (ver `ProjectsScreen`) y queda guardado con
 * él — no solo un parámetro de exportación puntual. La exportación (ver
 * `engine.export.computeExportDimensions`) lo toma como entrada, no lo
 * define.
 */
enum class AspectRatioPreset(val label: String, val subtitle: String) {
    REELS("9:16", "Reels · TikTok · Stories"),
    SQUARE("1:1", "Feed cuadrado"),
    WIDESCREEN("16:9", "YouTube · horizontal")
}
