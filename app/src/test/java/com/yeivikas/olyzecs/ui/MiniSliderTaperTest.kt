package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.engine.audio.AUDIO_VOLUME_MIN_DB
import com.yeivikas.olyzecs.engine.audio.MAX_AUDIO_VOLUME
import com.yeivikas.olyzecs.engine.audio.UNITY_AUDIO_VOLUME
import com.yeivikas.olyzecs.engine.audio.audioDbToVolume
import com.yeivikas.olyzecs.engine.audio.audioVolumeDbText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Curvas de los deslizadores: la lineal (panorama) no cambia nada respecto a antes, y la de volumen mantiene el
 * tirador sincronizado con el número en dB (defecto original: el tirador llegaba al tope con el número en -45 dB).
 */
class MiniSliderTaperTest {

    private val panRange = -1f..1f
    private val volumeRange = 0f..MAX_AUDIO_VOLUME

    @Test
    fun `la curva lineal reproduce el comportamiento de siempre`() {
        val taper = linearMiniSliderTaper(panRange)
        for (f in listOf(0f, 0.25f, 0.5f, 0.8f, 1f)) {
            assertEquals(audioMiniSliderValueAt(10f + f * 80f, 100f, 10f, panRange), taper.toValue(f), 1e-5f)
            assertEquals(f, taper.toFraction(taper.toValue(f)), 1e-5f)
        }
        assertEquals(0.5f, taper.toFraction(0f), 0f)
    }

    @Test
    fun `la posicion del dedo se acota y sin recorrido da cero`() {
        assertEquals(0f, audioMiniSliderFractionAt(-50f, 100f, 10f), 0f)
        assertEquals(1f, audioMiniSliderFractionAt(500f, 100f, 10f), 0f)
        assertEquals(0.5f, audioMiniSliderFractionAt(50f, 100f, 10f), 1e-6f)
        assertEquals(0f, audioMiniSliderFractionAt(50f, 20f, 10f), 0f)
        assertEquals(0f, audioMiniSliderFractionAt(Float.NaN, 100f, 10f), 0f)
    }

    @Test
    fun `el iman en posicion equivale al iman en valor sobre una curva lineal`() {
        val travel = 80f
        val radius = 5f
        for (v in listOf(-1f, -0.2f, -0.05f, 0f, 0.05f, 0.2f, 1f)) {
            val byValue = audioMiniSliderInSnapZone(v, 0f, travel, radius, panRange)
            val byFraction = audioMiniSliderFractionInSnapZone(
                fraction = audioMiniSliderFraction(v, panRange),
                snapFraction = audioMiniSliderFraction(0f, panRange),
                travelPx = travel,
                radiusPx = radius
            )
            assertEquals("v=$v", byValue, byFraction)
        }
        assertFalse(audioMiniSliderFractionInSnapZone(0.5f, 0.5f, 0f, 5f))
        assertFalse(audioMiniSliderFractionInSnapZone(0.5f, 0.5f, 80f, 0f))
        assertFalse(audioMiniSliderFractionInSnapZone(Float.NaN, 0.5f, 80f, 5f))
    }

    @Test
    fun `el extremo izquierdo del fader de volumen es menos inf y el derecho el maximo`() {
        assertEquals(0f, AudioVolumeFaderTaper.toValue(0f), 0f)
        assertEquals("-inf", audioVolumeDbText(AudioVolumeFaderTaper.toValue(0f)))
        assertEquals(MAX_AUDIO_VOLUME, AudioVolumeFaderTaper.toValue(1f), 0f)
        assertEquals("+3.5", audioVolumeDbText(AudioVolumeFaderTaper.toValue(1f)))
    }

    @Test
    fun `el tirador y el numero siempre avanzan juntos`() {
        // Barrido de posiciones: el valor crece con la posición y el número en dB nunca retrocede.
        var prevVolume = -1f
        var f = 0f
        while (f <= 1f) {
            val v = AudioVolumeFaderTaper.toValue(f)
            assertTrue("f=$f", v > prevVolume)
            prevVolume = v
            f += 0.02f
        }
    }

    @Test
    fun `a -45 dB el tirador esta lejos del extremo y la rayita del 100 por ciento es el volumen original`() {
        val atMinus45 = AudioVolumeFaderTaper.toFraction(audioDbToVolume(-45.6f))
        assertTrue(atMinus45 > 0.1f)
        assertTrue(AudioVolumeFaderTaper.toFraction(audioDbToVolume(AUDIO_VOLUME_MIN_DB + 0.5f)) > 0f)
        val unity = AudioVolumeFaderTaper.toFraction(UNITY_AUDIO_VOLUME)
        assertEquals(0.85f, unity, 1e-4f)
        assertEquals(UNITY_AUDIO_VOLUME, AudioVolumeFaderTaper.toValue(unity), 1e-4f)
        assertTrue(volumeRange.contains(AudioVolumeFaderTaper.toValue(unity)))
    }
}
