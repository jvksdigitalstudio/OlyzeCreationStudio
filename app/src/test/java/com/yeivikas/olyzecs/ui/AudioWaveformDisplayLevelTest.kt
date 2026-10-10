package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

/** Nivel de dibujo de la onda: vista del carril (realzada) vs. señal original. */
class AudioWaveformDisplayLevelTest {

    @Test
    fun `la senal original respeta la amplitud real del archivo`() {
        // Archivo cuyo pico real es 0,25 de escala completa: su máximo normalizado (1.0) se dibuja a 0,25.
        assertEquals(0.25f, waveformDisplayLevel(1f, gamma = 0.8f, sourcePeak = 0.25f, trueAmplitude = true), 1e-6f)
        // Un archivo a escala completa llena la altura.
        assertEquals(1f, waveformDisplayLevel(1f, 0.8f, 1f, true), 1e-6f)
    }

    @Test
    fun `la senal original es lineal, sin curva de realce`() {
        assertEquals(0.5f * 0.8f, waveformDisplayLevel(0.5f, 0.7f, 0.8f, true), 1e-6f)
    }

    @Test
    fun `dos audios distintos no se ven iguales`() {
        val quiet = waveformDisplayLevel(1f, 0.8f, sourcePeak = 0.2f, trueAmplitude = true)
        val loud = waveformDisplayLevel(1f, 0.8f, sourcePeak = 0.9f, trueAmplitude = true)
        assertEquals(0.2f, quiet, 1e-6f)
        assertEquals(0.9f, loud, 1e-6f)
    }

    @Test
    fun `la vista del carril mantiene la curva y la normalizacion`() {
        assertEquals(0.5f.pow(0.8f), waveformDisplayLevel(0.5f, 0.8f, sourcePeak = 0.1f, trueAmplitude = false), 1e-6f)
    }

    @Test
    fun `los valores se acotan a 0 y 1`() {
        assertEquals(1f, waveformDisplayLevel(1.5f, 0.8f, 1f, true), 0f)
        assertEquals(0f, waveformDisplayLevel(-0.2f, 0.8f, 1f, true), 0f)
        assertEquals(0f, waveformDisplayLevel(-0.2f, 0.8f, 1f, false), 0f)
    }
}
