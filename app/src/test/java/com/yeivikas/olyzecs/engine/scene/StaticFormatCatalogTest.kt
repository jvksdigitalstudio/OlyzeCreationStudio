package com.yeivikas.olyzecs.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [StaticFormatCatalog] — Fase B (ver ADR-005).
 *
 * El grupo más importante acá es "paridad con AspectRatioPreset": no es
 * un detalle cosmético, es el requisito que hace posible que la Fase C
 * (migración de proyectos guardados) sea exacta y no aproximada.
 *
 * Desde la Fase D, `computeExportDimensions` ya no recibe
 * `AspectRatioPreset` (recibe [CanvasSpec]) — esta clase de dominio
 * (`engine.scene`) no debe depender de `engine.export` para verificar su
 * propio catálogo, así que la paridad se verifica contra las dimensiones
 * y relaciones de aspecto documentadas en el KDoc de [StaticFormatCatalog]
 * directamente, no contra esa función.
 */
class StaticFormatCatalogTest {

    private val catalog: FormatCatalog = StaticFormatCatalog

    // --- Estructura general del catálogo ---

    @Test
    fun `categories devuelve solo categorias con al menos un preset, en orden`() {
        // Orden Social -> Cine -> General (no alfabético): coincide con
        // el orden de pestañas pedido para el selector de "Nuevo
        // proyecto" — ver KDoc de StaticFormatCatalog.
        assertEquals(
            listOf(PresetCategory.SOCIAL, PresetCategory.CINEMATIC, PresetCategory.GENERAL),
            catalog.categories()
        )
    }

    @Test
    fun `CINEMATIC tiene exactamente 3 presets, uno por cada relacion de aspecto real de cine`() {
        assertEquals(3, catalog.presetsIn(PresetCategory.CINEMATIC).size)
    }

    @Test
    fun `SOCIAL tiene exactamente 6 presets tras sumar Shorts de YouTube al grupo vertical`() {
        // Antes de la auditoría posterior a la Fase G, Reels/TikTok/Stories
        // eran un solo FormatPreset con etiqueta combinada — ver KDoc de
        // StaticFormatCatalog. Se separaron en 3 presets independientes
        // (mismo tamaño físico) + Feed cuadrado + YouTube = 5. Ronda 2:
        // se suma Shorts de YouTube (mismo tamaño físico que Reels/
        // TikTok/Stories) = 6.
        assertEquals(6, catalog.presetsIn(PresetCategory.SOCIAL).size)
    }

    @Test
    fun `GENERAL tiene presets y ninguno coincide en dimensiones con los de SOCIAL`() {
        val socialFormats = catalog.presetsIn(PresetCategory.SOCIAL).map { it.format }.toSet()
        val generalFormats = catalog.presetsIn(PresetCategory.GENERAL).map { it.format }.toSet()
        assertTrue(generalFormats.isNotEmpty())
        assertTrue(socialFormats.intersect(generalFormats).isEmpty())
    }

    @Test
    fun `CINEMATIC no coincide en dimensiones con SOCIAL ni con GENERAL`() {
        val cinematicFormats = catalog.presetsIn(PresetCategory.CINEMATIC).map { it.format }.toSet()
        val socialFormats = catalog.presetsIn(PresetCategory.SOCIAL).map { it.format }.toSet()
        val generalFormats = catalog.presetsIn(PresetCategory.GENERAL).map { it.format }.toSet()
        assertTrue(cinematicFormats.intersect(socialFormats).isEmpty())
        assertTrue(cinematicFormats.intersect(generalFormats).isEmpty())
    }

    // --- Integridad: sin ids duplicados, todo preset resuelve a un formato válido ---

    @Test
    fun `no hay ids duplicados en todo el catalogo`() {
        val allIds = catalog.categories().flatMap { catalog.presetsIn(it) }.map { it.id }
        assertEquals(allIds.size, allIds.toSet().size)
    }

    @Test
    fun `todo preset del catalogo resuelve a un CanvasFormat con dimensiones positivas`() {
        val allPresets = catalog.categories().flatMap { catalog.presetsIn(it) }
        assertTrue(allPresets.isNotEmpty())
        allPresets.forEach { preset ->
            assertTrue(preset.format.widthPx > 0)
            assertTrue(preset.format.heightPx > 0)
        }
    }

    @Test
    fun `findById encuentra cada preset devuelto por presetsIn usando su propio id`() {
        catalog.categories().forEach { category ->
            catalog.presetsIn(category).forEach { preset ->
                assertEquals(preset, catalog.findById(preset.id))
            }
        }
    }

    @Test
    fun `findById devuelve null para un id que no existe en el catalogo`() {
        assertNull(catalog.findById("plataforma.inventada.que.no.existe"))
    }

    // --- Paridad exacta con los 3 valores históricos de AspectRatioPreset (crítico para Fase C) ---

    @Test
    fun `preset vertical de SOCIAL tiene las dimensiones documentadas 1080x1920`() {
        val preset = catalog.findById("social.vertical.reels")!!
        assertEquals(1080 to 1920, preset.format.widthPx to preset.format.heightPx)
    }

    @Test
    fun `preset cuadrado de SOCIAL tiene las dimensiones documentadas 1080x1080`() {
        val preset = catalog.findById("social.square.feed")!!
        assertEquals(1080 to 1080, preset.format.widthPx to preset.format.heightPx)
    }

    @Test
    fun `preset widescreen de SOCIAL tiene las dimensiones documentadas 1920x1080`() {
        val preset = catalog.findById("social.widescreen.youtube")!!
        assertEquals(1920 to 1080, preset.format.widthPx to preset.format.heightPx)
    }

    @Test
    fun `preset de Shorts de YouTube existe y comparte el formato vertical 1080x1920`() {
        val preset = catalog.findById("social.vertical.youtubeshorts")!!
        assertEquals("Shorts", preset.label)
        assertEquals(1080 to 1920, preset.format.widthPx to preset.format.heightPx)
    }

    @Test
    fun `preset Cinemascope de CINEMATIC tiene las dimensiones documentadas 1920x804`() {
        val preset = catalog.findById("cinema.scope.239")!!
        assertEquals("Cinemascope", preset.label)
        assertEquals(1920 to 804, preset.format.widthPx to preset.format.heightPx)
    }

    @Test
    fun `preset DCI Flat de CINEMATIC tiene las dimensiones documentadas 1920x1038`() {
        val preset = catalog.findById("cinema.flat.185")!!
        assertEquals("DCI Flat", preset.label)
        assertEquals(1920 to 1038, preset.format.widthPx to preset.format.heightPx)
    }

    @Test
    fun `preset IMAX de CINEMATIC tiene las dimensiones documentadas 1920x1342`() {
        val preset = catalog.findById("cinema.imax.143")!!
        assertEquals("IMAX", preset.label)
        assertEquals(1920 to 1342, preset.format.widthPx to preset.format.heightPx)
    }

    @Test
    fun `los presets de SOCIAL cubren exactamente las 3 relaciones de aspecto clasicas 9-16, 1-1 y 16-9`() {
        // Con 5 presets en vez de 3 (ver test de arriba), el set de
        // aspectos ÚNICOS tiene que seguir dando exactamente estos 3 —
        // Reels/TikTok/Stories comparten el mismo 9:16, así que no
        // agregan un cuarto valor al set.
        val socialAspects = catalog.presetsIn(PresetCategory.SOCIAL).map { it.format.aspect }.toSet()
        val legacyAspects = setOf(
            AspectRatio(9, 16),  // REELS
            AspectRatio(1, 1),   // SQUARE
            AspectRatio(16, 9)   // WIDESCREEN
        )
        assertEquals(legacyAspects, socialAspects)
    }

    // --- Reels/TikTok/Stories: mismo tamaño físico, presets independientes ---

    @Test
    fun `Reels TikTok Stories y Shorts son 4 presets independientes con ids y etiquetas distintas`() {
        val reels = catalog.findById("social.vertical.reels")!!
        val tiktok = catalog.findById("social.vertical.tiktok")!!
        val stories = catalog.findById("social.vertical.stories")!!
        val shorts = catalog.findById("social.vertical.youtubeshorts")!!

        val ids = listOf(reels.id, tiktok.id, stories.id, shorts.id)
        assertEquals(ids.size, ids.toSet().size)

        val labels = listOf(reels.label, tiktok.label, stories.label, shorts.label)
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(setOf("Reels", "TikTok", "Stories", "Shorts"), labels.toSet())
    }

    @Test
    fun `Reels TikTok Stories y Shorts comparten exactamente el mismo formato fisico, no son 4 tamanos parecidos`() {
        // Guarda de regresión: si en el futuro alguien "ajusta" el tamaño
        // de uno de los 4 sin querer (por ejemplo, poniendo un
        // CanvasFormat distinto para Shorts), este test lo detecta. Los
        // 4 tienen que dar el MISMO CanvasFormat, no uno "equivalente".
        val reels = catalog.findById("social.vertical.reels")!!
        val tiktok = catalog.findById("social.vertical.tiktok")!!
        val stories = catalog.findById("social.vertical.stories")!!
        val shorts = catalog.findById("social.vertical.youtubeshorts")!!
        assertEquals(reels.format, tiktok.format)
        assertEquals(reels.format, stories.format)
        assertEquals(reels.format, shorts.format)
    }
}
