package com.yeivikas.olyzecs.engine.scene

/**
 * Implementación real de [FormatCatalog] usada por el selector de
 * "Nuevo proyecto" (ver ADR-005, Fase B). Es un `object` con los datos
 * embebidos en código — no una fuente externa (JSON/red/DB) — porque
 * hoy no hay ningún caso de uso que requiera actualizar el catálogo sin
 * publicar una versión nueva de la app (ver ADR-005, decisión D4: evitar
 * resolver un problema que no existe todavía). Si ese caso de uso
 * aparece, cambia la implementación de [FormatCatalog], no su contrato.
 *
 * [PresetCategory.SOCIAL] reemplaza, con las MISMAS dimensiones, a los 3
 * valores de `AspectRatioPreset` (ver `engine.scene.AspectRatioPreset`)
 * — no son una aproximación de diseño:
 *   - `REELS`      -> [socialVerticalReels]/[socialVerticalTiktok]/[socialVerticalStories]/[socialVerticalYoutubeShorts] -> 1080 × 1920 px (9:16)
 *   - `SQUARE`     -> [socialSquare]     -> 1080 × 1080 px (1:1)
 *   - `WIDESCREEN` -> [socialWidescreen] -> 1920 × 1080 px (16:9)
 * Esas tres cifras son exactamente las que devolvía
 * `computeExportDimensions(ExportQuality.HD, aspecto)` para cada valor
 * del enum ANTES de la Fase D (ver `engine.export.ExportQuality`) —
 * verificado en [StaticFormatCatalogTest]. Desde la Fase D esa función ya
 * no recibe el enum (recibe un `CanvasSpec`, con estos mismos números
 * como entrada), así que hoy son simplemente las dimensiones de
 * referencia documentadas acá, no el resultado de una comparación
 * cruzada con otra función. Es intencional de todas formas: la Fase C
 * (migración de proyectos guardados) necesita que el `CanvasSpec`
 * resultante de migrar un proyecto viejo sea IDÉNTICO en píxeles al que
 * ese proyecto ya exportaba, no solo "parecido" (ver ADR-005, plan de
 * fases, Fase C). Si alguna de estas cifras cambia alguna vez, hay
 * que revisar la migración de Fase C en el mismo commit.
 *
 * REELS/TikTok/Stories arrancaron como un solo preset con la etiqueta
 * combinada "Reels · TikTok · Stories" — correcto en cuanto a tamaño
 * (los 3 son 9:16, 1080×1920 en las 3 plataformas), pero confuso en la
 * UI: un usuario que solo conoce una de las tres plataformas no tiene
 * forma de saber, con solo mirar el botón, que le sirve igual. Auditoría
 * posterior a la Fase G: se separan en 3 [FormatPreset] independientes
 * ([socialVerticalReels], [socialVerticalTiktok], [socialVerticalStories]),
 * los 3 apuntando al MISMO [verticalFormat] físico (ver "Formatos
 * físicos" más abajo — no se triplica el tamaño, solo la entrada de
 * catálogo/etiqueta). El id `"social.vertical.reels"` se mantiene sin
 * cambios a propósito — es el que ya usa
 * `LegacyAspectMigration.presetIdByLegacyAspect` para
 * `AspectRatioPreset.REELS` — así la migración de proyectos viejos no
 * necesita ningún cambio. Auditoría posterior (ronda 2): se suma un
 * cuarto preset del mismo grupo, [socialVerticalYoutubeShorts]
 * ("Shorts"), que faltaba — mismo [verticalFormat] físico, id propio sin
 * mapeo legacy porque nunca existió un `AspectRatioPreset` para Shorts.
 *
 * Orden de [presetsByCategory] para SOCIAL (ronda 2 de la auditoría):
 * Reels, Stories, Feed cuadrado, TikTok, Shorts, YouTube horizontal —
 * agrupa primero el bloque Meta/Instagram-Facebook (Reels, Stories,
 * Feed cuadrado comparten ecosistema), después las verticales de otras
 * plataformas (TikTok, Shorts), y cierra con la única horizontal
 * (YouTube clásico). No es orden alfabético ni de alta: es agrupamiento
 * por plataforma, para que el usuario reconozca el bloque de un vistazo.
 *
 * [PresetCategory.GENERAL] cubre relaciones de aspecto clásicas, sin
 * asociación a ninguna plataforma, deliberadamente distintas de las de
 * SOCIAL (4:3 / 3:4 en vez de 16:9 / 9:16 / 1:1) para no duplicar la
 * misma opción con otro nombre.
 *
 * [PresetCategory.CINEMATIC] cubre 3 relaciones de aspecto reales de
 * cine — no aproximaciones de diseño, son las mismas cifras que usan
 * Premiere/DaVinci/CapCut Pro para cada una:
 *   - [cinemaScopeFormat] 2.39:1 "Cinemascope/Anamórfico" — la más
 *     pedida en cualquier editor, el "letterbox" clásico de película.
 *   - [dciFlatFormat] 1.85:1 "DCI Flat" — el estándar de proyección de
 *     cine comercial, más sutil que el anamórfico.
 *   - [imaxFormat] 1.43:1 "IMAX" — la relación real de película IMAX de
 *     70mm (no el 1.90:1 de las salas IMAX digitales, que es un caso
 *     distinto). Es la más nicho de las 3, por eso va última en el
 *     orden de la categoría.
 * Las tres comparten el mismo ancho base (1920px) que [widescreenFormat]
 * — mismo criterio que el resto del catálogo (ver "Formatos físicos" más
 * abajo) — con el alto calculado a partir de la relación real y
 * redondeado al PAR más cercano (los códecs de video exigen dimensiones
 * pares; ver [CanvasLimits]).
 */
object StaticFormatCatalog : FormatCatalog {

    // --- Formatos físicos (compartidos entre presets que coincidan en tamaño, ver ADR-005 D2) ---

    private val verticalFormat = CanvasFormat(widthPx = 1080, heightPx = 1920)
    private val squareFormat = CanvasFormat(widthPx = 1080, heightPx = 1080)
    private val widescreenFormat = CanvasFormat(widthPx = 1920, heightPx = 1080)
    private val classicLandscapeFormat = CanvasFormat(widthPx = 1440, heightPx = 1080)
    private val classicPortraitFormat = CanvasFormat(widthPx = 1080, heightPx = 1440)

    // 1920 / 2.39 = 803.35 -> 804 (par más cercano).
    private val cinemaScopeFormat = CanvasFormat(widthPx = 1920, heightPx = 804)

    // 1920 / 1.85 = 1037.84 -> 1038 (par más cercano).
    private val dciFlatFormat = CanvasFormat(widthPx = 1920, heightPx = 1038)

    // 1920 / 1.43 = 1342.66 -> 1342 (par más cercano).
    private val imaxFormat = CanvasFormat(widthPx = 1920, heightPx = 1342)

    // --- SOCIAL: paridad exacta 1:1 con AspectRatioPreset × ExportQuality.HD ---

    // Los 3 presets de acá abajo comparten EXACTAMENTE el mismo
    // [verticalFormat] (1080×1920, 9:16) — no son 3 tamaños parecidos,
    // son el mismo tamaño con 3 etiquetas distintas, porque las 3
    // plataformas usan ese tamaño de verdad. Si el día de mañana alguna
    // de las 3 cambia su tamaño recomendado, deja de ser este mismo caso
    // y pasa a necesitar su propio CanvasFormat — no "ajustar" este.
    private val socialVerticalReels = FormatPreset(
        id = "social.vertical.reels",
        label = "Reels",
        subtitle = "9:16 · 1080 × 1920 px",
        category = PresetCategory.SOCIAL,
        format = verticalFormat
    )

    private val socialVerticalTiktok = FormatPreset(
        id = "social.vertical.tiktok",
        label = "TikTok",
        subtitle = "9:16 · 1080 × 1920 px",
        category = PresetCategory.SOCIAL,
        format = verticalFormat
    )

    private val socialVerticalStories = FormatPreset(
        id = "social.vertical.stories",
        label = "Stories",
        subtitle = "9:16 · 1080 × 1920 px",
        category = PresetCategory.SOCIAL,
        format = verticalFormat
    )

    // Shorts de YouTube — mismo [verticalFormat] físico que Reels/TikTok/
    // Stories (9:16, 1080×1920 px), el cuarto preset que faltaba en este
    // grupo (auditoría de UX post-Fase G: el selector ofrecía TikTok e
    // Instagram/Facebook Stories pero no la versión corta de YouTube, la
    // tercera plataforma grande con formato vertical de video). Id nuevo
    // y propio (`social.vertical.youtubeshorts`) — no hay ningún valor
    // legacy de `AspectRatioPreset` que migre a este preset, así que no
    // hace falta tocar `LegacyAspectMigration`.
    private val socialVerticalYoutubeShorts = FormatPreset(
        id = "social.vertical.youtubeshorts",
        label = "Shorts",
        subtitle = "9:16 · 1080 × 1920 px",
        category = PresetCategory.SOCIAL,
        format = verticalFormat
    )

    private val socialSquare = FormatPreset(
        id = "social.square.feed",
        label = "Feed cuadrado",
        subtitle = "1:1 · 1080 × 1080 px",
        category = PresetCategory.SOCIAL,
        format = squareFormat
    )

    private val socialWidescreen = FormatPreset(
        id = "social.widescreen.youtube",
        label = "YouTube · horizontal",
        subtitle = "16:9 · 1920 × 1080 px",
        category = PresetCategory.SOCIAL,
        format = widescreenFormat
    )

    // --- GENERAL: relaciones clásicas sin marca de plataforma ---

    private val generalLandscape = FormatPreset(
        id = "general.classic.landscape",
        label = "Clásico horizontal",
        subtitle = "4:3 · 1440 × 1080 px",
        category = PresetCategory.GENERAL,
        format = classicLandscapeFormat
    )

    private val generalPortrait = FormatPreset(
        id = "general.classic.portrait",
        label = "Clásico vertical",
        subtitle = "3:4 · 1080 × 1440 px",
        category = PresetCategory.GENERAL,
        format = classicPortraitFormat
    )

    // --- CINEMATIC: relaciones de aspecto reales de cine (ver KDoc de la clase) ---

    private val cinematicScope = FormatPreset(
        id = "cinema.scope.239",
        label = "Cinemascope",
        subtitle = "2.39:1 · 1920 × 804 px",
        category = PresetCategory.CINEMATIC,
        format = cinemaScopeFormat
    )

    private val cinematicFlat = FormatPreset(
        id = "cinema.flat.185",
        label = "DCI Flat",
        subtitle = "1.85:1 · 1920 × 1038 px",
        category = PresetCategory.CINEMATIC,
        format = dciFlatFormat
    )

    private val cinematicImax = FormatPreset(
        id = "cinema.imax.143",
        label = "IMAX",
        subtitle = "1.43:1 · 1920 × 1342 px",
        category = PresetCategory.CINEMATIC,
        format = imaxFormat
    )

    // --- Índice del catálogo ---

    private val presetsByCategory: LinkedHashMap<PresetCategory, List<FormatPreset>> = linkedMapOf(
        // Orden acordado (ver auditoría de UX post-Fase G): primero el
        // bloque Meta/Instagram-Facebook, que comparte ecosistema
        // (Reels, Stories, Feed cuadrado), después las verticales de
        // otras plataformas (TikTok, Shorts de YouTube), y al final la
        // única horizontal (YouTube clásico) — no es un orden
        // alfabético ni por fecha de alta, es un agrupamiento por
        // plataforma/familia pensado para que el usuario reconozca de
        // un vistazo "esto es para Instagram/Facebook" vs "esto es para
        // otra red" sin tener que leer cada etiqueta.
        PresetCategory.SOCIAL to listOf(
            socialVerticalReels, socialVerticalStories, socialSquare,
            socialVerticalTiktok, socialVerticalYoutubeShorts, socialWidescreen
        ),
        // Orden dentro de CINEMATIC (ver KDoc de la clase): de la más
        // pedida en un editor a la más nicho — Cinemascope primero, IMAX
        // al final.
        PresetCategory.CINEMATIC to listOf(
            cinematicScope, cinematicFlat, cinematicImax
        ),
        PresetCategory.GENERAL to listOf(generalLandscape, generalPortrait)
    )

    private val presetsById: Map<String, FormatPreset> =
        presetsByCategory.values.flatten().associateBy { it.id }

    override fun categories(): List<PresetCategory> = presetsByCategory.keys.toList()

    override fun presetsIn(category: PresetCategory): List<FormatPreset> =
        presetsByCategory[category].orEmpty()

    override fun findById(id: String): FormatPreset? = presetsById[id]
}
