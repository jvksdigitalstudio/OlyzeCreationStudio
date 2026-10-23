package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.engine.audio.MAX_AUDIO_VOLUME
import com.yeivikas.olyzecs.engine.audio.UNITY_AUDIO_VOLUME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda el arrastre sobre el valor numérico del volumen: solo el eje VERTICAL ajusta el nivel; un arrastre lateral
 * se ignora (ni cambia el valor ni cuenta como toque), y en los extremos el gesto se re-ancla al dedo.
 * Matemática pura (JVM, sin Compose).
 */
class VolumeReadoutDragAxisTest {

    private val slop = 8f

    @Test
    fun `dentro de la holgura el eje sigue sin decidirse`() {
        assertEquals(ReadoutDragAxis.UNDECIDED, readoutDragAxis(0f, 0f, slop))
        assertEquals(ReadoutDragAxis.UNDECIDED, readoutDragAxis(5f, 5f, slop))
        assertEquals(ReadoutDragAxis.UNDECIDED, readoutDragAxis(0f, -8f, slop))
    }

    @Test
    fun `un arrastre vertical, hacia arriba o abajo, es vertical`() {
        assertEquals(ReadoutDragAxis.VERTICAL, readoutDragAxis(0f, -20f, slop))
        assertEquals(ReadoutDragAxis.VERTICAL, readoutDragAxis(0f, 20f, slop))
        assertEquals(ReadoutDragAxis.VERTICAL, readoutDragAxis(6f, -20f, slop))
        assertEquals(ReadoutDragAxis.VERTICAL, readoutDragAxis(-6f, 20f, slop))
    }

    @Test
    fun `un arrastre lateral es horizontal aunque el dedo se desvie en vertical`() {
        assertEquals(ReadoutDragAxis.HORIZONTAL, readoutDragAxis(20f, 0f, slop))
        assertEquals(ReadoutDragAxis.HORIZONTAL, readoutDragAxis(-20f, 0f, slop))
        assertEquals(ReadoutDragAxis.HORIZONTAL, readoutDragAxis(20f, -10f, slop))
        assertEquals(ReadoutDragAxis.HORIZONTAL, readoutDragAxis(-20f, 12f, slop))
    }

    @Test
    fun `la diagonal exacta no cuenta como vertical`() {
        assertEquals(ReadoutDragAxis.HORIZONTAL, readoutDragAxis(15f, 15f, slop))
        assertEquals(ReadoutDragAxis.HORIZONTAL, readoutDragAxis(-15f, -15f, slop))
    }

    @Test
    fun `valores no finitos no deciden nada`() {
        assertEquals(ReadoutDragAxis.UNDECIDED, readoutDragAxis(Float.NaN, 20f, slop))
        assertEquals(ReadoutDragAxis.UNDECIDED, readoutDragAxis(0f, Float.POSITIVE_INFINITY, slop))
        assertEquals(ReadoutDragAxis.UNDECIDED, readoutDragAxis(0f, 20f, Float.NaN))
    }

    @Test
    fun `los extremos del arrastre son el silencio y el maximo`() {
        assertTrue(audioVolumeIsAtDragLimit(0f))
        assertTrue(audioVolumeIsAtDragLimit(MAX_AUDIO_VOLUME))
        assertFalse(audioVolumeIsAtDragLimit(UNITY_AUDIO_VOLUME))
        assertFalse(audioVolumeIsAtDragLimit(0.01f))
    }
}
