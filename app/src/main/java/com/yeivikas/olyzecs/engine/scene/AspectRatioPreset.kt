package com.yeivikas.olyzecs.engine.scene

/**
 * Preset de formato/aspecto del canvas del proyecto — enum fijo de 3
 * valores, reemplazado por [CanvasSpec] (el Canvas real del proyecto, en
 * píxeles) como fuente de verdad del lienzo (ver ADR-005).
 *
 * Desde la Fase D, `engine.export.computeExportDimensions` ya no recibe
 * este enum (recibe un [CanvasSpec]). Desde la Fase E, `CreateProjectDialog`
 * (`ProjectsScreen.kt`) ya no lo usa para crear proyectos nuevos — ofrece
 * el catálogo real de presets ([StaticFormatCatalog]) + Custom. Fase G:
 * queda `@Deprecated`, con un único rol restante — frontera de
 * compatibilidad hacia atrás con `project.json` guardados por versiones
 * de la app anteriores a ADR-005:
 * - Lectura: `ProjectStorage.loadProject` lo usa (`AspectRatioPreset.valueOf`
 *   sobre `ProjectData.aspectRatio`) solo cuando `canvasSchemaVersion == 0`
 *   (proyecto guardado antes de esta mejora, sin Canvas propio todavía),
 *   y lo traduce a un [CanvasSpec] real vía [LegacyAspectMigration].
 * - Escritura: `ProjectStorage.saveProject` sigue completando
 *   `ProjectData.aspectRatio` (String) en cada guardado — no por ser la
 *   fuente de verdad (lo es [CanvasSpec]), sino para que una versión
 *   anterior de la app (downgrade) siga encontrando ahí un valor
 *   coherente con el Canvas real, en vez de un campo vacío o inventado.
 *   Ese valor se deriva del [CanvasSpec] vigente en el momento de guardar
 *   vía [LegacyAspectMigration.closestLegacyAspectFor] — nunca de una
 *   instancia de este enum guardada en el estado del editor.
 *
 * No se elimina el tipo en sí: `runCatching { AspectRatioPreset.valueOf(...) }`
 * sobre un proyecto ya guardado dejaría de compilar. Ningún código nuevo
 * fuera de esa frontera de compatibilidad debería instanciar o consumir
 * este enum — usar [CanvasSpec]/[AspectRatio] en su lugar.
 */
@Deprecated(
    message = "Reemplazado por CanvasSpec (ver ADR-005). Se conserva solo " +
        "como frontera de compatibilidad con project.json guardados por " +
        "versiones anteriores — no usar en código nuevo.",
    replaceWith = ReplaceWith("CanvasSpec")
)
enum class AspectRatioPreset(val label: String, val subtitle: String) {
    REELS("9:16", "Reels · TikTok · Stories"),
    SQUARE("1:1", "Feed cuadrado"),
    WIDESCREEN("16:9", "YouTube · horizontal")
}
