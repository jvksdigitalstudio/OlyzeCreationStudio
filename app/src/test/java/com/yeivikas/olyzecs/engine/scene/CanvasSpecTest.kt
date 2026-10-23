package com.yeivikas.olyzecs.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests de [CanvasSpec] — Fase A (ver ADR-005). */
class CanvasSpecTest {

    @Test
    fun `originPresetId es null por defecto (canvas Custom)`() {
        val spec = CanvasSpec(widthPx = 1234, heightPx = 987)
        assertNull(spec.originPresetId)
    }

    @Test
    fun `originPresetId se conserva cuando el canvas viene de un preset`() {
        val spec = CanvasSpec(widthPx = 1080, heightPx = 1920, originPresetId = "instagram.reels.vertical")
        assertEquals("instagram.reels.vertical", spec.originPresetId)
    }

    @Test
    fun `aspect y orientation se derivan correctamente de las dimensiones reales`() {
        val spec = CanvasSpec(widthPx = 1080, heightPx = 1920)
        assertEquals(AspectRatio(9, 16), spec.aspect)
        assertEquals(Orientation.PORTRAIT, spec.orientation)
    }

    @Test
    fun `constructor rechaza dimensiones no positivas`() {
        assertThrows(IllegalArgumentException::class.java) { CanvasSpec(widthPx = 0, heightPx = 1920) }
        assertThrows(IllegalArgumentException::class.java) { CanvasSpec(widthPx = 1080, heightPx = 0) }
    }
}
