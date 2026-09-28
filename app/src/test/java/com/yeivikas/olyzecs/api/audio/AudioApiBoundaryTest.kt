package com.yeivikas.olyzecs.api.audio

import com.yeivikas.olyzecs.api.model.AudioClipSnapshot
import com.yeivikas.olyzecs.engine.audio.AudioClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Test OBLIGATORIO de la Fase 4.2 — punto 6/25.D del prompt maestro:
 * verifica, sobre el propio contrato compilado de [AudioApi], que
 * `getAudioClip()` ya NO devuelve `engine.audio.AudioClip` (mutable, la
 * misma instancia en vivo del motor) sino [AudioClipSnapshot] (inmutable).
 *
 * Por reflexión sobre la interfaz, no sobre una instancia — no hace falta
 * ningún `Context`/`Uri` real para esta verificación.
 */
class AudioApiBoundaryTest {

    @Test
    fun `getAudioClip() de AudioApi devuelve AudioClipSnapshot, nunca AudioClip mutable`() {
        val method = AudioApi::class.java.getDeclaredMethod("getAudioClip")
        assertEquals(AudioClipSnapshot::class.java, method.returnType)
        assertNotEquals(AudioClip::class.java, method.returnType)
    }
}
