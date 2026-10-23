package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Volumen en decibelios: conversión de ida y vuelta, texto en vivo, filtrado de lo que se puede escribir y límites.
 * Solo matemática y texto (sin Android ni Compose).
 */
class AudioVolumeDbTest {

    // ---- Conversión ----

    @Test
    fun `el volumen original es cero dB`() {
        assertEquals(0f, audioVolumeToDb(UNITY_AUDIO_VOLUME), 1e-5f)
        assertEquals(UNITY_AUDIO_VOLUME, audioDbToVolume(0f), 1e-6f)
    }

    @Test
    fun `menos tres dB son cerca del 71 por ciento`() {
        assertEquals(0.7079f, audioDbToVolume(-3f), 1e-3f)
        assertEquals(-3f, audioVolumeToDb(audioDbToVolume(-3f)), 1e-3f)
    }

    @Test
    fun `de dB a volumen y de vuelta conserva el nivel en todo el rango`() {
        for (db in listOf(-59f, -40f, -20f, -12f, -6f, -1f, 0f, 1f, 2.5f, 3.5f)) {
            assertEquals(db, audioVolumeToDb(audioDbToVolume(db)), 1e-3f)
        }
    }

    @Test
    fun `el silencio y lo que baja del minimo es volumen cero`() {
        assertEquals(0f, audioDbToVolume(AUDIO_VOLUME_MIN_DB), 0f)
        assertEquals(0f, audioDbToVolume(-200f), 0f)
        assertEquals(Float.NEGATIVE_INFINITY, audioVolumeToDb(0f), 0f)
        assertEquals(Float.NEGATIVE_INFINITY, audioVolumeToDb(Float.NaN), 0f)
    }

    @Test
    fun `por encima del maximo el volumen se acota al maximo del clip`() {
        assertEquals(MAX_AUDIO_VOLUME, audioDbToVolume(20f), 0f)
        assertEquals(MAX_AUDIO_VOLUME, audioDbToVolume(AUDIO_VOLUME_MAX_DB), 1e-4f)
    }

    @Test
    fun `un valor no numerico da el volumen original`() {
        assertEquals(UNITY_AUDIO_VOLUME, audioDbToVolume(Float.NaN), 0f)
    }

    // ---- Texto en vivo ----

    @Test
    fun `el texto en vivo muestra una decima con signo en los positivos`() {
        assertEquals("0.0", audioVolumeDbText(1f))
        assertEquals("-3.0", audioVolumeDbText(audioDbToVolume(-3f)))
        assertEquals("-12.0", audioVolumeDbText(audioDbToVolume(-12f)))
        assertEquals("+2.0", audioVolumeDbText(audioDbToVolume(2f)))
        assertEquals("+3.5", audioVolumeDbText(MAX_AUDIO_VOLUME))
    }

    @Test
    fun `el silencio absoluto se muestra como menos inf`() {
        assertEquals("-inf", audioVolumeDbText(0f))
        assertEquals("-inf", audioVolumeDbText(0.0005f))
    }

    @Test
    fun `nunca aparece menos cero`() {
        assertEquals("0.0", audioVolumeDbText(0.9999f))
    }

    @Test
    fun `el texto inicial del cuadro no lleva mas ni decimal sobrante`() {
        assertEquals("0", audioVolumeDbEditText(1f))
        assertEquals("-3", audioVolumeDbEditText(audioDbToVolume(-3f)))
        assertEquals("-3.5", audioVolumeDbEditText(audioDbToVolume(-3.5f)))
        assertEquals("2.5", audioVolumeDbEditText(audioDbToVolume(2.5f)))
    }

    @Test
    fun `el silencio absoluto se edita como el minimo escribible`() {
        assertEquals("-60", audioVolumeDbEditText(0f))
    }

    // ---- Lo que se puede escribir ----

    @Test
    fun `solo pasan signo inicial digitos y un separador decimal`() {
        assertEquals("-6", sanitizeAudioVolumeDbInput("-6"))
        assertEquals("+2.5", sanitizeAudioVolumeDbInput("+2.5"))
        assertEquals("12", sanitizeAudioVolumeDbInput("12"))
    }

    @Test
    fun `las letras y las unidades se descartan`() {
        assertEquals("-6", sanitizeAudioVolumeDbInput("-6dB"))
        assertEquals("", sanitizeAudioVolumeDbInput("abc"))
        assertEquals("-", sanitizeAudioVolumeDbInput("-inf"))
        assertEquals("", sanitizeAudioVolumeDbInput("∞"))
    }

    @Test
    fun `el signo solo vale en la primera posicion`() {
        assertEquals("6", sanitizeAudioVolumeDbInput("6-"))
        assertEquals("-6", sanitizeAudioVolumeDbInput("--6"))
        assertEquals("1", sanitizeAudioVolumeDbInput("1+"))
    }

    @Test
    fun `la coma pasa a punto y solo cabe un separador`() {
        assertEquals("-2.5", sanitizeAudioVolumeDbInput("-2,5"))
        assertEquals("1.25", sanitizeAudioVolumeDbInput("1.2.5"))
    }

    @Test
    fun `el menos tipografico se normaliza`() {
        assertEquals("-3", sanitizeAudioVolumeDbInput("\u22123"))
    }

    @Test
    fun `el campo no pasa de seis caracteres`() {
        assertEquals(6, sanitizeAudioVolumeDbInput("-1234567890").length)
    }

    // ---- Acotado en vivo ----

    @Test
    fun `un numero que se pasa del maximo muestra el limite`() {
        assertEquals("3.5", limitAudioVolumeDbInput("35"))
        assertEquals("+3.5", limitAudioVolumeDbInput("+9"))
    }

    @Test
    fun `un numero por debajo del minimo muestra el minimo`() {
        assertEquals("-60", limitAudioVolumeDbInput("-600"))
        assertEquals("-60", limitAudioVolumeDbInput("-61"))
    }

    @Test
    fun `los valores dentro del rango y el texto incompleto se dejan tal cual`() {
        assertEquals("-60", limitAudioVolumeDbInput("-60"))
        assertEquals("3.5", limitAudioVolumeDbInput("3.5"))
        assertEquals("-", limitAudioVolumeDbInput("-"))
        assertEquals("+", limitAudioVolumeDbInput("+"))
        assertEquals("", limitAudioVolumeDbInput(""))
        assertEquals("-6", limitAudioVolumeDbInput("-6"))
    }

    // ---- Lectura de lo escrito ----

    @Test
    fun `un texto incompleto aun no es un numero`() {
        assertNull(parseAudioVolumeDbInput(""))
        assertNull(parseAudioVolumeDbInput("-"))
        assertNull(parseAudioVolumeDbInput("+"))
        assertNull(parseAudioVolumeDbInput("."))
        assertNull(parseAudioVolumeDbInput("-."))
        assertNull(parseAudioVolumeDbInput("abc"))
    }

    @Test
    fun `lo escrito se lee como volumen lineal`() {
        assertEquals(0.7079f, parseAudioVolumeDbInput("-3")!!, 1e-3f)
        assertEquals(1f, parseAudioVolumeDbInput("0")!!, 1e-6f)
        assertEquals(1f, parseAudioVolumeDbInput("+0")!!, 1e-6f)
        assertEquals(parseAudioVolumeDbInput("-2.5")!!, parseAudioVolumeDbInput("-2,5")!!, 0f)
    }

    @Test
    fun `lo escrito fuera de rango se acota`() {
        assertEquals(0f, parseAudioVolumeDbInput("-60")!!, 0f)
        assertEquals(0f, parseAudioVolumeDbInput("-999")!!, 0f)
        assertEquals(MAX_AUDIO_VOLUME, parseAudioVolumeDbInput("+99")!!, 0f)
    }

    @Test
    fun `un punto final o inicial es un numero valido`() {
        assertTrue(parseAudioVolumeDbInput("3.") != null)
        assertTrue(parseAudioVolumeDbInput("-.5") != null)
    }

    // ---- Cambio de signo ----

    @Test
    fun `el boton de signo alterna entre menos y mas`() {
        assertEquals("+6", toggleAudioVolumeDbSign("-6"))
        assertEquals("-6", toggleAudioVolumeDbSign("+6"))
        assertEquals("-6", toggleAudioVolumeDbSign("6"))
        assertEquals("-", toggleAudioVolumeDbSign(""))
    }

    // ---- Arrastre vertical sobre el valor numérico ----

    @Test
    fun `arrastrar hacia arriba sube el nivel y hacia abajo lo baja en dB exactos`() {
        assertEquals(2f, audioVolumeToDb(audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, 2f)), 0.06f)
        assertEquals(-5f, audioVolumeToDb(audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, -5f)), 0.06f)
        assertEquals(-9f, audioVolumeToDb(audioVolumeAfterDbDelta(audioDbToVolume(-6f), -3f)), 0.06f)
    }

    @Test
    fun `el arrastre avanza en decimas de dB igual que el texto en pantalla`() {
        val volume = audioVolumeAfterDbDelta(audioDbToVolume(-10f), 0.34f)
        assertEquals("-9.7", audioVolumeDbText(volume))
        assertEquals("-9.6", audioVolumeDbText(audioVolumeAfterDbDelta(audioDbToVolume(-10f), 0.37f)))
    }

    @Test
    fun `sin desplazamiento el volumen de partida se conserva sin redondear`() {
        assertEquals(0.7f, audioVolumeAfterDbDelta(0.7f, 0f), 0f)
        assertEquals(0f, audioVolumeAfterDbDelta(0f, 0f), 0f)
    }

    @Test
    fun `el arrastre es absoluto respecto al inicio y volver al punto de partida devuelve el original`() {
        val start = 0.63f
        val away = audioVolumeAfterDbDelta(start, 7f)
        assertTrue(away > start)
        // El resultado depende solo de (inicio, desplazamiento): regresar a 0 dB de desplazamiento = original exacto.
        assertEquals(start, audioVolumeAfterDbDelta(start, 0f), 0f)
        assertEquals(start, audioVolumeAfterDbDelta(start, Float.NaN), 0f)
    }

    @Test
    fun `arriba del maximo da exactamente el volumen maximo`() {
        assertEquals(MAX_AUDIO_VOLUME, audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, 50f), 0f)
        assertEquals(MAX_AUDIO_VOLUME, audioVolumeAfterDbDelta(MAX_AUDIO_VOLUME, 1f), 0f)
        assertEquals(MAX_AUDIO_VOLUME, audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, AUDIO_VOLUME_MAX_DB), 0f)
    }

    @Test
    fun `debajo del minimo da silencio absoluto`() {
        assertEquals(0f, audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, -80f), 0f)
        assertEquals(0f, audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, -60f), 0f)
        assertEquals(0f, audioVolumeAfterDbDelta(0f, -5f), 0f)
    }

    @Test
    fun `desde el silencio absoluto el arrastre hacia arriba parte de menos 60 dB`() {
        assertEquals(-50f, audioVolumeToDb(audioVolumeAfterDbDelta(0f, 10f)), 0.06f)
        assertEquals(0f, audioVolumeToDb(audioVolumeAfterDbDelta(0f, 60f)), 0.06f)
    }

    @Test
    fun `el iman de cero dB pega el valor al volumen original`() {
        assertEquals(UNITY_AUDIO_VOLUME, audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, 0.1f), 0f)
        assertEquals(UNITY_AUDIO_VOLUME, audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, -0.2f), 0f)
        assertEquals(UNITY_AUDIO_VOLUME, audioVolumeAfterDbDelta(audioDbToVolume(-3f), 3.1f), 0f)
        assertEquals("+0.3", audioVolumeDbText(audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, 0.3f)))
        assertEquals("-0.3", audioVolumeDbText(audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, -0.3f)))
    }

    @Test
    fun `un desplazamiento no finito conserva el volumen de partida`() {
        assertEquals(0.5f, audioVolumeAfterDbDelta(0.5f, Float.NaN), 0f)
        assertEquals(0.5f, audioVolumeAfterDbDelta(0.5f, Float.POSITIVE_INFINITY), 0f)
        assertEquals(0.5f, audioVolumeAfterDbDelta(0.5f, Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun `el resultado siempre es un volumen valido del clip`() {
        for (start in listOf(0f, 0.01f, 0.5f, 1f, MAX_AUDIO_VOLUME, Float.NaN)) {
            for (delta in listOf(-200f, -30f, -0.5f, 0.5f, 3f, 200f)) {
                val result = audioVolumeAfterDbDelta(start, delta)
                assertTrue("start=$start delta=$delta -> $result", result in 0f..MAX_AUDIO_VOLUME)
            }
        }
    }

    @Test
    fun `los pasos de accesibilidad de un dB suben y bajan el nivel`() {
        assertEquals(1f, audioVolumeToDb(audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, 1f)), 0.06f)
        assertEquals(-1f, audioVolumeToDb(audioVolumeAfterDbDelta(UNITY_AUDIO_VOLUME, -1f)), 0.06f)
    }
}
