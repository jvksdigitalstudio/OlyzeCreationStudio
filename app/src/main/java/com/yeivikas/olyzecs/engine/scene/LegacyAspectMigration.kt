package com.yeivikas.olyzecs.engine.scene

/**
 * Migra el formato viejo de un proyecto guardado — solo un
 * `AspectRatioPreset` (`ProjectData.aspectRatio`), sin Canvas propio en
 * píxeles — a un [CanvasSpec] real. Ver ADR-005, Fase C.
 *
 * Cada valor de [AspectRatioPreset] mapea al [FormatPreset] de
 * [StaticFormatCatalog] con las MISMAS dimensiones exactas: los 3
 * presets de [PresetCategory.SOCIAL] están garantizados (ver
 * `StaticFormatCatalogTest`, grupo "Paridad exacta con los 3 valores
 * históricos de AspectRatioPreset") para coincidir 1:1 con las
 * dimensiones que `computeExportDimensions(ExportQuality.HD, aspecto)`
 * devolvía para cada valor del enum antes de la Fase D — por eso un
 * proyecto migrado con esta función da EXACTAMENTE el mismo tamaño que
 * ya tenía como referencia, no una aproximación (ver ADR-005, Fase C:
 * "el Canvas migrado debe dar exactamente las mismas dimensiones que
 * hoy calcula `computeExportDimensions` para ese mismo proyecto"). Desde
 * la Fase D, además, ese mismo `CanvasSpec` migrado es literalmente el
 * valor que `computeExportDimensions` recibe como entrada — ya no hay
 * dos caminos de cálculo distintos que deban coincidir por casualidad.
 *
 * Vive en `engine.scene`, sin dependencias de `android.*`/`androidx.*`,
 * para poder testearse como JVM unit test puro — la parte de
 * `ProjectStorage` que sí necesita `Context`/lectura de disco solo
 * LLAMA a esta función, no reimplementa el mapeo.
 *
 * Desde la Fase G, además de la dirección original (legacy -> Canvas,
 * usada al CARGAR un proyecto viejo), este `object` también resuelve la
 * dirección inversa ([closestLegacyAspectFor]: Canvas -> legacy), usada
 * al GUARDAR — ver su KDoc.
 */
@Suppress("DEPRECATION") // Frontera de compatibilidad deliberada con AspectRatioPreset (ver ADR-005, Fase G) — no es código nuevo que deba evitar el tipo.
object LegacyAspectMigration {

    /**
     * Id del [FormatPreset] de [StaticFormatCatalog] al que corresponde
     * cada valor legacy. `private` a propósito: solo esta migración
     * puntual debe conocer este mapeo — nada más en el dominio del
     * Canvas necesita saber que alguna vez existió `AspectRatioPreset`.
     */
    private val presetIdByLegacyAspect: Map<AspectRatioPreset, String> = mapOf(
        AspectRatioPreset.REELS to "social.vertical.reels",
        AspectRatioPreset.SQUARE to "social.square.feed",
        AspectRatioPreset.WIDESCREEN to "social.widescreen.youtube"
    )

    /**
     * Migra un [AspectRatioPreset] guardado en un proyecto viejo a su
     * [CanvasSpec] equivalente.
     *
     * No debería fallar nunca en producción: los 3 valores del enum
     * tienen entrada en [presetIdByLegacyAspect] y esos ids siempre
     * existen en [catalog] (cubierto por
     * `LegacyAspectMigrationTest`) — si [catalog] es una implementación
     * distinta a la real y le falta alguno de esos 3 ids, es un error
     * de programación (catálogo mal configurado), no un caso de borde
     * de datos de usuario, por eso lanza en vez de devolver un
     * `CanvasSpec` inventado.
     */
    fun migrate(
        legacyAspect: AspectRatioPreset,
        catalog: FormatCatalog = StaticFormatCatalog,
        factory: CanvasFactory = DefaultCanvasFactory()
    ): CanvasSpec {
        val presetId = presetIdByLegacyAspect.getValue(legacyAspect)
        val preset = catalog.findById(presetId)
            ?: error("Migración de Canvas rota: el preset '$presetId' para $legacyAspect ya no existe en el catálogo")
        return factory.fromPreset(preset)
    }

    /**
     * Dirección inversa de [migrate]: el valor de [AspectRatioPreset] más
     * cercano a un [CanvasSpec] real, usado por `ProjectStorage.saveProject`
     * para completar `ProjectData.aspectRatio` (String) en CADA guardado
     * (ver ADR-005, Fase G y el KDoc de [AspectRatioPreset]).
     *
     * BUG REAL corregido en esta fase: antes `saveProject` recibía
     * siempre el `AspectRatioPreset` guardado en `EditorUiState.exportAspect`
     * — un campo que, desde la Fase E, ya NO se elige al crear el
     * proyecto (el selector real usa presets del catálogo/Custom, ver
     * `CreateProjectDialog`) y por eso quedaba SIEMPRE en su default
     * (`REELS`). Resultado: todo proyecto creado después de la Fase E
     * guardaba `aspectRatio = "REELS"` en su `project.json` sin importar
     * el Canvas real elegido (Cuadrado, Custom 4:5, etc.) — un dato falso
     * que además se auto-perpetuaba en cada re-guardado. Acá se deriva
     * SIEMPRE del [CanvasSpec] vigente en el momento de guardar, nunca de
     * un valor de estado congelado.
     *
     * No intenta encontrar el preset ORIGEN exacto (para eso está
     * [CanvasSpec.originPresetId], ver [findById] de [FormatCatalog]) —
     * solo la CATEGORÍA de forma (vertical/cuadrado/horizontal) más
     * parecida, que es todo lo que una versión anterior de la app sabría
     * hacer con este dato. Se basa en [CanvasSpec.orientation] — nunca en
     * comparar `widthPx`/`heightPx` a mano — por el mismo motivo que
     * [Orientation] nunca se persiste directo: una sola fuente de verdad
     * para "qué forma tiene este Canvas".
     */
    fun closestLegacyAspectFor(canvas: CanvasSpec): AspectRatioPreset = when (canvas.orientation) {
        Orientation.PORTRAIT -> AspectRatioPreset.REELS
        Orientation.SQUARE -> AspectRatioPreset.SQUARE
        Orientation.LANDSCAPE -> AspectRatioPreset.WIDESCREEN
    }
}
