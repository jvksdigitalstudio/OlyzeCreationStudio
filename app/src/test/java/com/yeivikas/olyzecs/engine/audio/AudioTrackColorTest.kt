package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackColorTest {

    private val base = AudioTrack(id = "t1")

    @Test
    fun `un color solido desactiva el degradado activo`() {
        val gradient = base.withGradient(1, 2, 45f, isRadial = false, useBlackAndWhiteMode = false)
        val solid = gradient.withCustomColor(0xFF112233.toInt(), useBlackAndWhiteMode = true)
        assertFalse(solid.useGradientColor)
        assertEquals(0xFF112233.toInt(), solid.customColorArgb)
        assertTrue(solid.useBlackAndWhiteMode)
    }

    @Test
    fun `el degradado guarda sus extremos, angulo y forma`() {
        val g = base.withGradient(10, 20, 135f, isRadial = true, useBlackAndWhiteMode = false)
        assertTrue(g.useGradientColor)
        assertEquals(10, g.customGradientStartArgb)
        assertEquals(20, g.customGradientEndArgb)
        assertEquals(135f, g.gradientAngleDegrees, 0f)
        assertTrue(g.gradientIsRadial)
    }

    @Test
    fun `el degradado de grupo conserva el tono muestreado como color solido`() {
        val g = base.withGradient(10, 20, 90f, isRadial = false, useBlackAndWhiteMode = false, sampledColorArgb = 15)
        assertEquals(15, g.customColorArgb)
    }

    @Test
    fun `el degradado sin tono muestreado no pisa el color solido previo`() {
        val g = base.withCustomColor(7, useBlackAndWhiteMode = false)
            .withGradient(10, 20, 90f, isRadial = false, useBlackAndWhiteMode = false)
        assertEquals(7, g.customColorArgb)
    }

    @Test
    fun `restablecer limpia toda la personalizacion`() {
        val reset = base.withGradient(10, 20, 90f, isRadial = false, useBlackAndWhiteMode = true)
            .withCustomColor(5, useBlackAndWhiteMode = true)
            .withDefaultColor()
        assertNull(reset.customColorArgb)
        assertNull(reset.customGradientStartArgb)
        assertNull(reset.customGradientEndArgb)
        assertFalse(reset.useGradientColor)
        assertFalse(reset.useBlackAndWhiteMode)
    }
}
