package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.engine.audio.effectiveFadesMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas puras del diálogo "Fundidos" del menú del clip: techo de los deslizadores, habilitación y rótulos. */
class AudioClipFadeDialogTest {

    @Test
    fun `el techo es la mitad del tramo audible, igual que el motor`() {
        // Misma regla que effectiveFadesMs: lo que el deslizador permite es lo que realmente se aplica.
        for (audible in listOf(2L, 3L, 1000L, 4000L, 9999L, 10_000L)) {
            val max = audioFadeSliderMaxMs(audible)
            val (fi, fo) = effectiveFadesMs(audible, max, max)
            assertEquals("entrada con audible=$audible", max, fi)
            assertEquals("salida con audible=$audible", max, fo)
        }
    }

    @Test
    fun `el techo nunca pasa de 5 s ni baja de 1 ms`() {
        assertEquals(AUDIO_FADE_SLIDER_MAX_MS, audioFadeSliderMaxMs(60_000L))
        assertEquals(1L, audioFadeSliderMaxMs(0L))
        assertEquals(1L, audioFadeSliderMaxMs(-50L))
        assertEquals(1L, audioFadeSliderMaxMs(1L))
    }

    @Test
    fun `el techo de un clip corto es la mitad de su duracion`() {
        assertEquals(1500L, audioFadeSliderMaxMs(3000L))
    }

    @Test
    fun `los controles se habilitan solo si hay tramo audible suficiente`() {
        assertFalse(audioFadeControlsEnabled(0L))
        assertFalse(audioFadeControlsEnabled(1L))
        assertTrue(audioFadeControlsEnabled(2L))
        assertTrue(audioFadeControlsEnabled(30_000L))
    }

    @Test
    fun `rotulos de duracion`() {
        assertEquals("0 ms", audioFadeLabel(0L))
        assertEquals("400 ms", audioFadeLabel(400L))
        assertEquals("999 ms", audioFadeLabel(999L))
        assertEquals("1,0 s", audioFadeLabel(1000L))
        assertEquals("1,5 s", audioFadeLabel(1500L))
        assertEquals("5,0 s", audioFadeLabel(5000L))
    }
}
