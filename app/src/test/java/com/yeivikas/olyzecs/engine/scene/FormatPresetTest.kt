package com.yeivikas.olyzecs.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests de [FormatPreset] — Fase A (ver ADR-005). */
class FormatPresetTest {

    private val someFormat = CanvasFormat(widthPx = 1080, heightPx = 1920)

    @Test
    fun `guarda id, label, categoria y formato tal cual se construyen`() {
        val preset = FormatPreset(
            id = "instagram.reels.vertical",
            label = "Reels",
            subtitle = "Instagram · TikTok · Stories",
            category = PresetCategory.SOCIAL,
            format = someFormat
        )
        assertEquals("instagram.reels.vertical", preset.id)
        assertEquals("Reels", preset.label)
        assertEquals(PresetCategory.SOCIAL, preset.category)
        assertEquals(someFormat, preset.format)
    }

    @Test
    fun `dos presets distintos pueden compartir el mismo CanvasFormat sin ser iguales entre si`() {
        val reels = FormatPreset("instagram.reels.vertical", "Reels", "Instagram", PresetCategory.SOCIAL, someFormat)
        val tiktok = FormatPreset("tiktok.video.vertical", "TikTok", "TikTok", PresetCategory.SOCIAL, someFormat)
        assertEquals(reels.format, tiktok.format)
        assertNotEquals(reels, tiktok)
    }

    @Test
    fun `rechaza id en blanco`() {
        assertThrows(IllegalArgumentException::class.java) {
            FormatPreset("", "Reels", "Instagram", PresetCategory.SOCIAL, someFormat)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FormatPreset("   ", "Reels", "Instagram", PresetCategory.SOCIAL, someFormat)
        }
    }

    @Test
    fun `rechaza label en blanco`() {
        assertThrows(IllegalArgumentException::class.java) {
            FormatPreset("instagram.reels.vertical", "", "Instagram", PresetCategory.SOCIAL, someFormat)
        }
    }
}
