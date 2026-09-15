package com.yeivikas.olyzecs.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests de [LegacyAspectMigration] — Fase C (ver ADR-005).
 *
 * El grupo "paridad exacta" es el requisito real de la fase: un
 * proyecto viejo migrado tiene que dar el MISMO tamaño en píxeles que
 * ya tenía como referencia, no uno parecido.
 *
 * Desde la Fase D, `computeExportDimensions` ya no recibe
 * `AspectRatioPreset` (recibe el propio [CanvasSpec] — sería circular
 * usarla acá para calcular el valor "esperado" de una migración HACIA
 * [CanvasSpec]). La referencia real e independiente de "cuántos píxeles
 * le corresponden a cada valor legacy" es [StaticFormatCatalog], la
 * misma fuente que ya usa la propia [LegacyAspectMigration] en
 * producción — por eso las dimensiones esperadas se leen de ahí
 * directamente.
 */
@Suppress("DEPRECATION") // Toda la clase testea la frontera de compatibilidad deliberada con AspectRatioPreset (ver ADR-005, Fase G)
class LegacyAspectMigrationTest {

    // --- Paridad exacta con las dimensiones documentadas del catálogo ---

    @Test
    fun `migrar REELS da exactamente las dimensiones del preset social vertical reels`() {
        val expected = StaticFormatCatalog.findById("social.vertical.reels")!!.format
        val spec = LegacyAspectMigration.migrate(AspectRatioPreset.REELS)
        assertEquals(expected.widthPx to expected.heightPx, spec.widthPx to spec.heightPx)
    }

    @Test
    fun `migrar SQUARE da exactamente las dimensiones del preset social square feed`() {
        val expected = StaticFormatCatalog.findById("social.square.feed")!!.format
        val spec = LegacyAspectMigration.migrate(AspectRatioPreset.SQUARE)
        assertEquals(expected.widthPx to expected.heightPx, spec.widthPx to spec.heightPx)
    }

    @Test
    fun `migrar WIDESCREEN da exactamente las dimensiones del preset social widescreen youtube`() {
        val expected = StaticFormatCatalog.findById("social.widescreen.youtube")!!.format
        val spec = LegacyAspectMigration.migrate(AspectRatioPreset.WIDESCREEN)
        assertEquals(expected.widthPx to expected.heightPx, spec.widthPx to spec.heightPx)
    }

    @Test
    fun `los 3 valores de AspectRatioPreset migran a los 3 valores sin excepcion, ninguno queda afuera`() {
        val expectedPresetIdByLegacy = mapOf(
            AspectRatioPreset.REELS to "social.vertical.reels",
            AspectRatioPreset.SQUARE to "social.square.feed",
            AspectRatioPreset.WIDESCREEN to "social.widescreen.youtube"
        )
        AspectRatioPreset.entries.forEach { legacy ->
            val expected = StaticFormatCatalog.findById(expectedPresetIdByLegacy.getValue(legacy))!!.format
            val spec = LegacyAspectMigration.migrate(legacy)
            assertEquals(expected.widthPx to expected.heightPx, spec.widthPx to spec.heightPx)
        }
    }

    // --- El CanvasSpec resultante queda trazado a su preset de origen ---

    @Test
    fun `el CanvasSpec migrado guarda el id del preset de origen, no queda huerfano`() {
        val spec = LegacyAspectMigration.migrate(AspectRatioPreset.REELS)
        assertEquals("social.vertical.reels", spec.originPresetId)
        assertEquals(StaticFormatCatalog.findById(spec.originPresetId!!)!!.format.widthPx, spec.widthPx)
        assertEquals(StaticFormatCatalog.findById(spec.originPresetId!!)!!.format.heightPx, spec.heightPx)
    }

    // --- Catálogo mal configurado: falla explícito, no con un CanvasSpec inventado ---

    @Test
    fun `si el catalogo no tiene el preset esperado, falla explicito en vez de inventar dimensiones`() {
        val catalogVacio = object : FormatCatalog {
            override fun categories(): List<PresetCategory> = emptyList()
            override fun presetsIn(category: PresetCategory): List<FormatPreset> = emptyList()
            override fun findById(id: String): FormatPreset? = null
        }
        assertThrows(IllegalStateException::class.java) {
            LegacyAspectMigration.migrate(AspectRatioPreset.REELS, catalog = catalogVacio)
        }
    }

    // --- closestLegacyAspectFor (dirección inversa, ver ADR-005, Fase G) ---
    //
    // Usada por ProjectStorage.saveProject para completar el ProjectData.aspectRatio
    // legacy en cada guardado — el bug real que corrige esta función es que,
    // antes de la Fase G, ese campo quedaba siempre en "REELS" sin importar
    // el Canvas real (ver KDoc de closestLegacyAspectFor).

    @Test
    fun `un Canvas vertical (portrait) mapea siempre a REELS, sea o no el preset historico`() {
        assertEquals(AspectRatioPreset.REELS, LegacyAspectMigration.closestLegacyAspectFor(CanvasSpec(1080, 1920)))
        // Un Custom vertical que NO es 9:16 (ej. 4:5, un formato típico de
        // feed de Instagram) también debe caer en REELS — closestLegacyAspectFor
        // no busca una coincidencia exacta de proporción, solo la CATEGORÍA
        // de forma (vertical/cuadrado/horizontal), ver su KDoc.
        assertEquals(AspectRatioPreset.REELS, LegacyAspectMigration.closestLegacyAspectFor(CanvasSpec(1080, 1350)))
    }

    @Test
    fun `un Canvas cuadrado mapea siempre a SQUARE`() {
        assertEquals(AspectRatioPreset.SQUARE, LegacyAspectMigration.closestLegacyAspectFor(CanvasSpec(1080, 1080)))
        assertEquals(AspectRatioPreset.SQUARE, LegacyAspectMigration.closestLegacyAspectFor(CanvasSpec(500, 500)))
    }

    @Test
    fun `un Canvas horizontal (landscape) mapea siempre a WIDESCREEN, sea o no el preset historico`() {
        assertEquals(AspectRatioPreset.WIDESCREEN, LegacyAspectMigration.closestLegacyAspectFor(CanvasSpec(1920, 1080)))
        // Un Custom horizontal que NO es 16:9 (ej. 4:3, el preset "Clásico
        // horizontal" del catálogo) también debe caer en WIDESCREEN, por el
        // mismo motivo que el caso vertical de arriba.
        assertEquals(AspectRatioPreset.WIDESCREEN, LegacyAspectMigration.closestLegacyAspectFor(CanvasSpec(1440, 1080)))
    }

    @Test
    fun `migrar y despues volver con closestLegacyAspectFor da el mismo valor original, ida y vuelta consistente`() {
        AspectRatioPreset.entries.forEach { legacy ->
            val spec = LegacyAspectMigration.migrate(legacy)
            assertEquals(legacy, LegacyAspectMigration.closestLegacyAspectFor(spec))
        }
    }
}
