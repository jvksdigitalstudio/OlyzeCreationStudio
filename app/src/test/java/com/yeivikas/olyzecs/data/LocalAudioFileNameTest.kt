package com.yeivikas.olyzecs.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nombre del archivo local de audio: único por clip Y por fuente (ver [localAudioFileName]). */
class LocalAudioFileNameTest {

    @Test
    fun `la misma fuente del mismo clip da siempre el mismo nombre`() {
        val a = localAudioFileName("clip-1", "content://docs/audio/42", "wav")
        val b = localAudioFileName("clip-1", "content://docs/audio/42", "wav")
        assertEquals(a, b)
    }

    @Test
    fun `reemplazar la fuente de un clip cambia el nombre aunque la extension sea la misma`() {
        val old = localAudioFileName("clip-1", "content://docs/audio/42", "wav")
        val new = localAudioFileName("clip-1", "content://docs/audio/43", "wav")
        assertNotEquals(old, new)
    }

    @Test
    fun `clips distintos con la misma fuente no comparten archivo`() {
        val a = localAudioFileName("clip-1", "content://docs/audio/42", "wav")
        val b = localAudioFileName("clip-2", "content://docs/audio/42", "wav")
        assertNotEquals(a, b)
    }

    @Test
    fun `el nombre es seguro y conserva extension`() {
        val name = localAudioFileName("../evil/id", "content://x", "mp3")
        assertTrue(name.matches(Regex("audio_[A-Za-z0-9_\\-]+_[0-9a-f]{8}\\.mp3")))
    }
}
