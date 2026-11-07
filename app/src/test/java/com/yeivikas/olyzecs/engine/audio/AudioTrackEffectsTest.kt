package com.yeivikas.olyzecs.engine.audio

import com.yeivikas.olyzecs.engine.audio.fx.ReverbEffect
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Operaciones puras sobre la cadena de efectos de un carril ([AudioTrackEffects.kt]). */
class AudioTrackEffectsTest {
    private val track = AudioTrack(id = "t1")

    @Test
    fun `un carril nuevo no tiene efectos y suena como siempre`() {
        assertTrue(track.effects.isEmpty())
        assertFalse(track.hasActiveEffects)
        assertTrue(track.activeEffects.isEmpty())
        assertNull(track.reverb)
    }

    @Test
    fun `withEffect agrega al final y deja el carril original intacto`() {
        val reverb = ReverbEffect(id = "r1")
        val updated = track.withEffect(reverb)
        assertEquals(listOf(reverb), updated.effects)
        assertTrue(track.effects.isEmpty())
        assertEquals(reverb, updated.reverb)
        assertTrue(updated.hasActiveEffects)
    }

    @Test
    fun `withEffect con un id existente reemplaza en su sitio sin duplicar ni mover`() {
        val a = ReverbEffect(id = "a")
        val b = ReverbEffect(id = "b")
        val edited = a.copy(params = ReverbParams(mix = 0.9f))
        val updated = track.withEffect(a).withEffect(b).withEffect(edited)
        assertEquals(listOf(edited, b), updated.effects)
    }

    @Test
    fun `withoutEffect quita solo ese efecto y si no existe devuelve el mismo contenido`() {
        val a = ReverbEffect(id = "a")
        val b = ReverbEffect(id = "b")
        val both = track.withEffect(a).withEffect(b)
        assertEquals(listOf(b), both.withoutEffect("a").effects)
        assertEquals(both.effects, both.withoutEffect("no-existe").effects)
        assertTrue(both.withoutEffect("a").withoutEffect("b").effects.isEmpty())
    }

    @Test
    fun `un efecto desactivado o con mix cero no cuenta como activo pero se conserva`() {
        val bypassed = ReverbEffect(id = "a", enabled = false)
        val silent = ReverbEffect(id = "b", params = ReverbParams(mix = 0f))
        val updated = track.withEffect(bypassed).withEffect(silent)
        assertFalse(updated.hasActiveEffects)
        assertTrue(updated.activeEffects.isEmpty())
        assertEquals(2, updated.effects.size)
        assertEquals(bypassed, updated.reverb)
    }

    @Test
    fun `activeEffects respeta el orden de la cadena y deja fuera los inactivos`() {
        val a = ReverbEffect(id = "a")
        val off = ReverbEffect(id = "off", enabled = false)
        val c = ReverbEffect(id = "c")
        assertEquals(listOf(a, c), track.withEffect(a).withEffect(off).withEffect(c).activeEffects)
    }

    @Test
    fun `cada carril lleva su propia cadena sin heredar la de otro`() {
        val other = AudioTrack(id = "t2")
        val withReverb = track.withEffect(ReverbEffect(id = "r"))
        assertTrue(withReverb.hasActiveEffects)
        assertFalse(other.hasActiveEffects)
        assertSame(other.effects, other.copy(name = "otro").effects)
    }

    // ---------------------------------------------------------------- liveReverbParams

    @Test
    fun `un carril sin Reverb no usa el motor en vivo`() {
        assertNull(track.liveReverbParams)
    }

    @Test
    fun `con el Reverb activo el motor en vivo usa sus parametros tal cual`() {
        val params = ReverbParams(mix = 0.5f, decaySeconds = 4f)
        assertEquals(params, track.withEffect(ReverbEffect(id = "r", params = params)).liveReverbParams)
    }

    @Test
    fun `con el Reverb en bypass sigue en el motor en vivo con mix cero y el resto intacto`() {
        val params = ReverbParams(mix = 0.5f, decaySeconds = 4f, size = 0.8f)
        val bypassed = track.withEffect(ReverbEffect(id = "r", enabled = false, params = params))
        assertEquals(params.copy(mix = 0f), bypassed.liveReverbParams)
        assertFalse(bypassed.hasActiveEffects)
    }
}
