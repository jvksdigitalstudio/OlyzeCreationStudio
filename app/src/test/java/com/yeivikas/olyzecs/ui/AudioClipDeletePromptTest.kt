package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Texto de la confirmación al eliminar un clip de audio. */
class AudioClipDeletePromptTest {

    private val lastClipNotice = "la capa de audio también desaparece"

    @Test
    fun `nombra el clip entre comillas`() {
        assertTrue(audioClipDeleteMessage("Test 01.wav", clipsInTrack = 3).startsWith("\"Test 01.wav\" se va a borrar"))
    }

    @Test
    fun `con varios clips no avisa que desaparece la capa`() {
        assertFalse(audioClipDeleteMessage("a", clipsInTrack = 2).contains(lastClipNotice))
    }

    @Test
    fun `con un solo clip avisa que la capa de audio tambien desaparece`() {
        assertTrue(audioClipDeleteMessage("a", clipsInTrack = 1).contains(lastClipNotice))
    }

    @Test
    fun `un carril sin clips se trata como ultimo clip`() {
        assertTrue(audioClipDeleteMessage("a", clipsInTrack = 0).contains(lastClipNotice))
    }

    @Test
    fun `siempre informa que se puede deshacer`() {
        assertTrue(audioClipDeleteMessage("a", clipsInTrack = 1).endsWith("Podés recuperarlo con Deshacer."))
        assertTrue(audioClipDeleteMessage("a", clipsInTrack = 5).endsWith("Podés recuperarlo con Deshacer."))
    }

    @Test
    fun `el mensaje con varios clips es exacto`() {
        assertEquals(
            "\"x\" se va a borrar del proyecto junto con su volumen, recorte, bucle y desvanecidos configurados. Podés recuperarlo con Deshacer.",
            audioClipDeleteMessage("x", clipsInTrack = 4)
        )
    }
}
