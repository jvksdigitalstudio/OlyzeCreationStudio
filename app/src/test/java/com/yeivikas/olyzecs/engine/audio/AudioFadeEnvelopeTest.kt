package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El preview en vivo ([AudioPreviewPlayer]) aplica los fundidos con
 * [fadeGainAt]; el export, con `AudioProcessor.applyVolumeAndFades`. Estos
 * tests fijan que AMBAS dan la misma curva — antes el preview ignoraba los
 * fundidos (volumen constante) y lo que se oía al editar no coincidía con lo
 * exportado (los clips nuevos traen fade-in 400 ms / fade-out 600 ms).
 */
class AudioFadeEnvelopeTest {

    /** Ganancia por milisegundo según el EXPORT: 1000 Hz mono => 1 frame = 1 ms. */
    private fun exportGains(totalMs: Int, fadeInMs: Long, fadeOutMs: Long): FloatArray {
        val pcm = ShortArray(totalMs) { 10000 }
        AudioProcessor.applyVolumeAndFades(
            pcm = pcm,
            channels = 1,
            sampleRateHz = 1000,
            volume = 1f,
            muted = false,
            fadeInMs = fadeInMs,
            fadeOutMs = fadeOutMs,
            totalFrames = totalMs,
            audibleFrames = totalMs
        )
        return FloatArray(totalMs) { pcm[it].toFloat() / 10000f }
    }

    private fun assertParity(totalMs: Int, fadeInMs: Long, fadeOutMs: Long) {
        val exported = exportGains(totalMs, fadeInMs, fadeOutMs)
        for (ms in 0 until totalMs) {
            val preview = fadeGainAt(totalMs.toLong(), fadeInMs, fadeOutMs, ms.toLong())
            assertEquals("ms=$ms (in=$fadeInMs, out=$fadeOutMs)", exported[ms], preview, 0.002f)
        }
    }

    @Test
    fun `preview y export dan la misma curva con los fundidos por defecto`() {
        assertParity(totalMs = 3000, fadeInMs = 400L, fadeOutMs = 600L)
    }

    @Test
    fun `preview y export coinciden cuando los fundidos exceden la mitad del clip`() {
        assertParity(totalMs = 1000, fadeInMs = 900L, fadeOutMs = 900L)
    }

    @Test
    fun `preview y export coinciden con un solo fundido`() {
        assertParity(totalMs = 2000, fadeInMs = 0L, fadeOutMs = 700L)
        assertParity(totalMs = 2000, fadeInMs = 700L, fadeOutMs = 0L)
    }

    @Test
    fun `sin fundidos la ganancia es 1 dentro del clip`() {
        assertEquals(1f, fadeGainAt(1000L, 0L, 0L, 0L), 0f)
        assertEquals(1f, fadeGainAt(1000L, 0L, 0L, 999L), 0f)
    }

    @Test
    fun `fuera del tramo audible la ganancia es 0`() {
        assertEquals(0f, fadeGainAt(1000L, 100L, 100L, -1L), 0f)
        assertEquals(0f, fadeGainAt(1000L, 100L, 100L, 1000L), 0f)
        assertEquals(0f, fadeGainAt(0L, 100L, 100L, 0L), 0f)
    }

    @Test
    fun `fade-in arranca en silencio y llega a 1`() {
        assertEquals(0f, fadeGainAt(4000L, 400L, 0L, 0L), 0.0001f)
        assertEquals(0.5f, fadeGainAt(4000L, 400L, 0L, 200L), 0.0001f)
        assertEquals(1f, fadeGainAt(4000L, 400L, 0L, 400L), 0.0001f)
    }

    @Test
    fun `fade-out baja hasta casi silencio en el ultimo milisegundo`() {
        assertEquals(1f, fadeGainAt(4000L, 0L, 600L, 3400L), 0.0001f)
        assertEquals(0.5f, fadeGainAt(4000L, 0L, 600L, 3700L), 0.0001f)
        assertEquals(1f / 600f, fadeGainAt(4000L, 0L, 600L, 3999L), 0.0001f)
    }

    @Test
    fun `largo audible - con loop es el clip completo`() {
        assertEquals(5000L, audibleLengthMs(sourceDurationMs = 2000L, trimStartMs = 500L, loop = true, clipLengthMs = 5000L))
    }

    @Test
    fun `largo audible - sin loop lo acota el archivo restante tras el recorte`() {
        assertEquals(1500L, audibleLengthMs(sourceDurationMs = 2000L, trimStartMs = 500L, loop = false, clipLengthMs = 5000L))
        assertEquals(800L, audibleLengthMs(sourceDurationMs = 2000L, trimStartMs = 500L, loop = false, clipLengthMs = 800L))
    }

    @Test
    fun `largo audible - archivo sin duracion valida no suena`() {
        assertEquals(0L, audibleLengthMs(sourceDurationMs = 0L, trimStartMs = 0L, loop = false, clipLengthMs = 1000L))
    }
    @Test
    fun `fundidos efectivos - se acotan a la mitad del tramo audible`() {
        assertEquals(Pair(400L, 500L), effectiveFadesMs(1000L, 400L, 600L))
        assertEquals(Pair(500L, 500L), effectiveFadesMs(1000L, 900L, 900L))
        assertEquals(Pair(0L, 0L), effectiveFadesMs(1000L, -50L, -1L))
    }

    @Test
    fun `fundidos efectivos - tramo audible vacio no tiene fundidos`() {
        assertEquals(Pair(0L, 0L), effectiveFadesMs(0L, 400L, 600L))
        assertEquals(Pair(0L, 0L), effectiveFadesMs(1L, 400L, 600L))
    }
}
