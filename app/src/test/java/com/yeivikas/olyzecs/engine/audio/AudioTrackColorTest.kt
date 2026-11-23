package com.yeivikas.olyzecs.engine.audio

import com.yeivikas.olyzecs.engine.color.GradientColorSpace
import com.yeivikas.olyzecs.engine.color.GradientGroupRef
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
    fun `el degradado de grupo dibuja el tono muestreado como color solido y guarda sus extremos`() {
        val group = GradientGroupRef("g1", GradientColorSpace.RGB)
        val g = base.withGroupGradient(10, 20, 90f, isRadial = false, useBlackAndWhiteMode = false, sampledArgb = 15, group = group)
        assertEquals(15, g.customColorArgb)
        assertFalse(g.useGradientColor)
        assertEquals(10, g.customGradientStartArgb)
        assertEquals(20, g.customGradientEndArgb)
        assertEquals(group, g.gradientGroup)
    }

    @Test
    fun `cualquier otro cambio de color saca al carril de su grupo`() {
        val group = GradientGroupRef("g1", GradientColorSpace.HUE)
        val grouped = base.withGroupGradient(10, 20, 90f, false, false, sampledArgb = 15, group = group)
        assertNull(grouped.withCustomColor(7, useBlackAndWhiteMode = false).gradientGroup)
        assertNull(grouped.withGradient(1, 2, 45f, isRadial = false, useBlackAndWhiteMode = false).gradientGroup)
        assertNull(grouped.withDefaultColor().gradientGroup)
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
