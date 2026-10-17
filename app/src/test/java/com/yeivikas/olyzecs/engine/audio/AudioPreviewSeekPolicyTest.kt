package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPreviewSeekPolicyTest {

    @Test
    fun `arranque y seek explicito siempre reposicionan`() {
        assertTrue(previewNeedsSeek(forceSeek = true, playing = true, clipChanged = false, offsetMs = 0))
        assertTrue(previewNeedsSeek(forceSeek = false, playing = false, clipChanged = false, offsetMs = 0))
    }

    @Test
    fun `en regimen una deriva pequena no provoca seek`() {
        assertFalse(previewNeedsSeek(forceSeek = false, playing = true, clipChanged = false, offsetMs = 120))
        assertFalse(previewNeedsSeek(forceSeek = false, playing = true, clipChanged = false, offsetMs = 300))
    }

    @Test
    fun `en regimen una deriva mayor a la tolerancia corrige con seek`() {
        assertTrue(previewNeedsSeek(forceSeek = false, playing = true, clipChanged = false, offsetMs = 301))
    }

    @Test
    fun `una union continua entre clips no hace seek`() {
        // Mitades de un clip dividido: el audio ya suena donde el segundo lo necesita.
        assertFalse(previewNeedsSeek(forceSeek = false, playing = true, clipChanged = true, offsetMs = 25))
        assertFalse(previewNeedsSeek(forceSeek = false, playing = true, clipChanged = true, offsetMs = 80))
    }

    @Test
    fun `un cambio de clip con tramo distinto del archivo reposiciona`() {
        assertTrue(previewNeedsSeek(forceSeek = false, playing = true, clipChanged = true, offsetMs = 81))
        assertTrue(previewNeedsSeek(forceSeek = false, playing = true, clipChanged = true, offsetMs = 5_000))
    }
}
