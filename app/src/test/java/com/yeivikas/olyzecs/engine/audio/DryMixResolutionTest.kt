package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

/**
 * Regla pura [resolveDryMix]: qué mezcla seca suena para un carril con Reverb. Cubre los dos defectos detectados en la
 * auditoría: un carril silenciado que seguía sonando por una mezcla anterior, y un render fallido que dejaba sonando una
 * versión desactualizada.
 */
class DryMixResolutionTest {
    private val current = DryTrackMix(File("current.wav"), 1_000L)
    private val previous = DryTrackMix(File("previous.wav"), 900L)

    @Test
    fun `sin clips audibles no suena ninguna mezcla aunque haya una anterior`() {
        assertNull(resolveDryMix(hasAudibleClips = false, ready = null, lastReady = previous, failed = false))
        assertNull(resolveDryMix(hasAudibleClips = false, ready = current, lastReady = previous, failed = false))
    }

    @Test
    fun `la mezcla vigente tiene prioridad sobre la anterior`() {
        assertSame(current, resolveDryMix(hasAudibleClips = true, ready = current, lastReady = previous, failed = false))
    }

    @Test
    fun `mientras se renderiza sigue sonando la anterior sin cortes`() {
        assertSame(previous, resolveDryMix(hasAudibleClips = true, ready = null, lastReady = previous, failed = false))
    }

    @Test
    fun `si el render de la version actual fallo no suena una mezcla desactualizada`() {
        assertNull(resolveDryMix(hasAudibleClips = true, ready = null, lastReady = previous, failed = true))
    }

    @Test
    fun `la primera vez sin ninguna mezcla suena el carril real`() {
        assertNull(resolveDryMix(hasAudibleClips = true, ready = null, lastReady = null, failed = false))
    }
}
