package com.yeivikas.olyzecs.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [DefaultCanvasFactory] — Fase A (ver ADR-005). Los límites
 * probados son los reales de [CanvasLimits] (100–4096 px), no valores
 * inventados para el test.
 */
class CanvasFactoryTest {

    private val factory: CanvasFactory = DefaultCanvasFactory()

    // --- fromPreset ---

    @Test
    fun `fromPreset copia las dimensiones del preset y guarda su id como origen`() {
        val preset = FormatPreset(
            id = "instagram.reels.vertical",
            label = "Reels",
            subtitle = "Instagram",
            category = PresetCategory.SOCIAL,
            format = CanvasFormat(widthPx = 1080, heightPx = 1920)
        )

        val spec = factory.fromPreset(preset)

        assertEquals(1080, spec.widthPx)
        assertEquals(1920, spec.heightPx)
        assertEquals("instagram.reels.vertical", spec.originPresetId)
    }

    // --- custom: límite inferior (100 px) ---

    @Test
    fun `custom rechaza un ancho de 99px, un pixel por debajo del minimo`() {
        val result = factory.custom(widthPx = 99, heightPx = 500)
        assertTrue(result.isFailure)
    }

    @Test
    fun `custom acepta un ancho de exactamente 100px, el minimo permitido`() {
        val result = factory.custom(widthPx = 100, heightPx = 500)
        assertTrue(result.isSuccess)
        assertEquals(100, result.getOrThrow().widthPx)
    }

    @Test
    fun `custom rechaza una altura de 99px, un pixel por debajo del minimo`() {
        val result = factory.custom(widthPx = 500, heightPx = 99)
        assertTrue(result.isFailure)
    }

    // --- custom: límite superior (4096 px) ---

    @Test
    fun `custom acepta un ancho de exactamente 4096px, el maximo permitido`() {
        val result = factory.custom(widthPx = 4096, heightPx = 2000)
        assertTrue(result.isSuccess)
        assertEquals(4096, result.getOrThrow().widthPx)
    }

    @Test
    fun `custom rechaza un ancho de 4097px, un pixel por encima del maximo`() {
        val result = factory.custom(widthPx = 4097, heightPx = 2000)
        assertTrue(result.isFailure)
    }

    @Test
    fun `custom rechaza una altura de 4097px, un pixel por encima del maximo`() {
        val result = factory.custom(widthPx = 2000, heightPx = 4097)
        assertTrue(result.isFailure)
    }

    // --- custom: caso válido típico + shape del resultado ---

    @Test
    fun `custom con dimensiones validas no asigna originPresetId (no viene de ningun preset)`() {
        val result = factory.custom(widthPx = 3000, heightPx = 3000)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().originPresetId)
    }

    @Test
    fun `custom con dimensiones validas tipicas (2048x1536) devuelve exito con esas dimensiones exactas`() {
        val result = factory.custom(widthPx = 2048, heightPx = 1536)
        assertTrue(result.isSuccess)
        val spec = result.getOrThrow()
        assertEquals(2048, spec.widthPx)
        assertEquals(1536, spec.heightPx)
    }

    @Test
    fun `custom devuelve un mensaje de error explicito, no generico, cuando el ancho esta fuera de rango`() {
        val result = factory.custom(widthPx = 50, heightPx = 500)
        val message = result.exceptionOrNull()?.message
        assertTrue(message != null && message.contains("ancho", ignoreCase = true))
    }

    @Test
    fun `custom devuelve un mensaje de error explicito, no generico, cuando la altura esta fuera de rango`() {
        val result = factory.custom(widthPx = 500, heightPx = 50)
        val message = result.exceptionOrNull()?.message
        assertTrue(message != null && message.contains("altura", ignoreCase = true))
    }
}
