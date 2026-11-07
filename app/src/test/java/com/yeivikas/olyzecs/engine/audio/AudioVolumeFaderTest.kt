package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Curva del fader de volumen en dB: extremos, volumen original, monotonía y ida y vuelta exacta. */
class AudioVolumeFaderTest {

    private val eps = 1e-4f

    @Test
    fun `el silencio es el extremo izquierdo y el maximo el derecho`() {
        assertEquals(0f, audioVolumeToFaderFraction(0f), 0f)
        assertEquals(0f, audioVolumeToFaderFraction(Float.NaN), 0f)
        // Por debajo del mínimo escribible (-60 dB ≈ 0.001) todo es silencio y el tirador va al extremo.
        assertEquals(0f, audioVolumeToFaderFraction(0.0005f), 0f)
        assertEquals(1f, audioVolumeToFaderFraction(MAX_AUDIO_VOLUME), 0f)
        assertEquals(0f, audioFaderFractionToVolume(0f), 0f)
        assertEquals(MAX_AUDIO_VOLUME, audioFaderFractionToVolume(1f), 0f)
    }

    @Test
    fun `el volumen original queda en la posicion de la rayita`() {
        assertEquals(AUDIO_VOLUME_FADER_UNITY_FRACTION, audioVolumeToFaderFraction(UNITY_AUDIO_VOLUME), eps)
        assertEquals(UNITY_AUDIO_VOLUME, audioFaderFractionToVolume(AUDIO_VOLUME_FADER_UNITY_FRACTION), eps)
    }

    @Test
    fun `el tirador solo llega al extremo con el numero en menos inf`() {
        // El defecto original: a -45.6 dB el tirador (lineal) parecía en el tope. Con la curva en dB sigue lejos.
        val f = audioVolumeToFaderFraction(audioDbToVolume(-45.6f))
        assertTrue("a -45.6 dB el tirador debe estar claramente despegado del extremo: $f", f > 0.1f)
        assertTrue(f < 0.25f)
    }

    @Test
    fun `la curva es estrictamente creciente`() {
        var prev = -1f
        var f = 0f
        while (f <= 1f) {
            val v = audioFaderFractionToVolume(f)
            assertTrue("f=$f v=$v prev=$prev", v > prev)
            prev = v
            f += 0.01f
        }
    }

    @Test
    fun `ida y vuelta devuelve el mismo volumen`() {
        for (db in listOf(-59f, -45.6f, -30f, -20f, -12f, -6f, -3f, 0f, 1.5f, 3f)) {
            val v = audioDbToVolume(db)
            val back = audioFaderFractionToVolume(audioVolumeToFaderFraction(v))
            assertEquals("db=$db", v, back, v * 1e-3f)
        }
    }

    @Test
    fun `fuera de rango se acota`() {
        assertEquals(0f, audioFaderFractionToVolume(-1f), 0f)
        assertEquals(0f, audioFaderFractionToVolume(Float.NaN), 0f)
        assertEquals(MAX_AUDIO_VOLUME, audioFaderFractionToVolume(2f), 0f)
        assertEquals(1f, audioVolumeToFaderFraction(MAX_AUDIO_VOLUME * 2f), 0f)
    }
}
