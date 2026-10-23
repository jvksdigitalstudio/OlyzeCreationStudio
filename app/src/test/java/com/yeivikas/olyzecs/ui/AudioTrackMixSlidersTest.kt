package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matemática pura de los deslizadores de panorama y volumen de la barra flotante de la capa de audio
 * ([audioMiniSliderFraction], [audioMiniSliderValueAt], [audioMiniSliderFill]) y sus rótulos de accesibilidad.
 */
class AudioTrackMixSlidersTest {

    private val volumeRange = 0f..1.5f
    private val panRange = -1f..1f

    // ---- Fracción ----

    @Test
    fun `la fraccion normaliza el valor dentro del rango`() {
        assertEquals(0f, audioMiniSliderFraction(0f, volumeRange), 0f)
        assertEquals(0.5f, audioMiniSliderFraction(0.75f, volumeRange), 1e-6f)
        assertEquals(1f, audioMiniSliderFraction(1.5f, volumeRange), 0f)
        assertEquals(0.5f, audioMiniSliderFraction(0f, panRange), 1e-6f)
    }

    @Test
    fun `la fraccion se acota y tolera valores no finitos o rangos degenerados`() {
        assertEquals(1f, audioMiniSliderFraction(9f, volumeRange), 0f)
        assertEquals(0f, audioMiniSliderFraction(-3f, volumeRange), 0f)
        assertEquals(0f, audioMiniSliderFraction(Float.NaN, volumeRange), 0f)
        assertEquals(0f, audioMiniSliderFraction(Float.POSITIVE_INFINITY, volumeRange), 0f)
        assertEquals(0f, audioMiniSliderFraction(1f, 1f..1f), 0f)
    }

    // ---- Del toque al valor ----

    @Test
    fun `un toque en los extremos del recorrido da el minimo y el maximo`() {
        // Ancho 100, tirador de radio 10: el recorrido útil va de x = 10 a x = 90.
        assertEquals(0f, audioMiniSliderValueAt(10f, 100f, 10f, volumeRange), 1e-6f)
        assertEquals(1.5f, audioMiniSliderValueAt(90f, 100f, 10f, volumeRange), 1e-6f)
        assertEquals(0.75f, audioMiniSliderValueAt(50f, 100f, 10f, volumeRange), 1e-6f)
    }

    @Test
    fun `un toque fuera del recorrido se acota a los extremos`() {
        assertEquals(-1f, audioMiniSliderValueAt(-40f, 100f, 10f, panRange), 0f)
        assertEquals(1f, audioMiniSliderValueAt(400f, 100f, 10f, panRange), 0f)
        assertEquals(-1f, audioMiniSliderValueAt(0f, 100f, 10f, panRange), 0f)
    }

    @Test
    fun `el centro del recorrido del panorama es el centro`() {
        assertEquals(0f, audioMiniSliderValueAt(50f, 100f, 10f, panRange), 1e-6f)
    }

    @Test
    fun `sin recorrido o con un toque no finito devuelve el minimo`() {
        assertEquals(0f, audioMiniSliderValueAt(5f, 20f, 10f, volumeRange), 0f)
        assertEquals(0f, audioMiniSliderValueAt(5f, 0f, 10f, volumeRange), 0f)
        assertEquals(-1f, audioMiniSliderValueAt(Float.NaN, 100f, 10f, panRange), 0f)
    }

    @Test
    fun `valor y fraccion son inversos`() {
        for (f in listOf(0f, 0.1f, 0.33f, 0.5f, 0.9f, 1f)) {
            val x = 10f + f * 80f
            val value = audioMiniSliderValueAt(x, 100f, 10f, volumeRange)
            assertEquals(f, audioMiniSliderFraction(value, volumeRange), 1e-5f)
        }
    }

    // ---- Tramo relleno ----

    @Test
    fun `el relleno normal va de la izquierda hasta el valor`() {
        assertEquals(0f to 0.3f, audioMiniSliderFill(0.3f, centered = false))
        assertEquals(0f to 1f, audioMiniSliderFill(1f, centered = false))
        assertEquals(0f to 0f, audioMiniSliderFill(0f, centered = false))
    }

    @Test
    fun `el relleno centrado va del centro hacia el lado al que gira`() {
        assertEquals(0.5f to 0.8f, audioMiniSliderFill(0.8f, centered = true))
        assertEquals(0.2f to 0.5f, audioMiniSliderFill(0.2f, centered = true))
        assertEquals(0.5f to 0.5f, audioMiniSliderFill(0.5f, centered = true))
    }

    @Test
    fun `el relleno acota fracciones fuera de rango`() {
        assertEquals(0f to 1f, audioMiniSliderFill(7f, centered = false))
        assertEquals(0.5f to 1f, audioMiniSliderFill(7f, centered = true))
        assertEquals(0f to 0.5f, audioMiniSliderFill(-7f, centered = true))
    }

    // ---- Rótulos ----

    @Test
    fun `rotulo del volumen en porcentaje`() {
        assertEquals("Volumen: 100%", audioVolumeLabel(1f))
        assertEquals("Volumen: 0%", audioVolumeLabel(0f))
        assertEquals("Volumen: 150%", audioVolumeLabel(1.5f))
        assertEquals("Volumen: 43%", audioVolumeLabel(0.43f))
    }

    // ---- Ícono del volumen ----

    @Test
    fun `el icono pasa a sin sonido solo cuando el volumen se lee como cero`() {
        assertEquals(true, audioVolumeIsMuted(0f))
        assertEquals(true, audioVolumeIsMuted(0.004f))
        assertEquals(false, audioVolumeIsMuted(0.006f))
        assertEquals(false, audioVolumeIsMuted(1f))
        assertEquals(false, audioVolumeIsMuted(1.5f))
    }

    @Test
    fun `un volumen no finito o negativo cuenta como sin sonido`() {
        assertEquals(true, audioVolumeIsMuted(Float.NaN))
        assertEquals(true, audioVolumeIsMuted(Float.NEGATIVE_INFINITY))
        assertEquals(true, audioVolumeIsMuted(-0.2f))
    }

    @Test
    fun `el icono sin sonido coincide con el rotulo de cero por ciento`() {
        for (v in listOf(0f, 0.001f, 0.004f, 0.006f, 0.01f, 0.5f)) {
            assertEquals(audioVolumeLabel(v) == "Volumen: 0%", audioVolumeIsMuted(v))
        }
    }

    @Test
    fun `rotulo del panorama con centro y lados`() {
        assertEquals("Pan: centro", audioPanLabel(0f))
        assertEquals("Pan: 35% izquierda", audioPanLabel(-0.35f))
        assertEquals("Pan: 100% derecha", audioPanLabel(1f))
    }

    // ---- Ícono del volumen como botón de silencio ----

    @Test
    fun `la accion anunciada es la que hara el siguiente toque`() {
        assertEquals("Silenciar", audioVolumeIconActionLabel(masterSilent = false))
        assertEquals("Activar sonido", audioVolumeIconActionLabel(masterSilent = true))
    }

    // ---- Doble toque: valor por defecto ----

    @Test
    fun `el valor por defecto del panorama es el centro y el del volumen el cien por ciento`() {
        assertEquals(0f, AUDIO_PAN_DEFAULT, 0f)
        assertEquals(1f, AUDIO_VOLUME_DEFAULT, 0f)
        assertEquals(0.5f, audioMiniSliderFraction(AUDIO_PAN_DEFAULT, panRange), 1e-6f)
    }

    @Test
    fun `los valores por defecto caen dentro del rango de cada deslizador`() {
        assertEquals(AUDIO_PAN_DEFAULT, AUDIO_PAN_DEFAULT.coerceIn(panRange), 0f)
        assertEquals(AUDIO_VOLUME_DEFAULT, AUDIO_VOLUME_DEFAULT.coerceIn(volumeRange), 0f)
    }

    private val minInterval = 40L
    private val timeout = 300L
    private val slop = 48f

    @Test
    fun `sin toque previo no hay doble toque`() {
        val tracker = MiniSliderTapTracker()
        assertEquals(false, tracker.completesDoubleTap(1000L, 10f, 10f, minInterval, timeout, slop))
    }

    @Test
    fun `dos toques seguidos y cercanos forman un doble toque`() {
        val tracker = MiniSliderTapTracker()
        tracker.recordTap(upMs = 1000L, x = 10f, y = 10f)
        assertEquals(true, tracker.completesDoubleTap(1150L, 14f, 12f, minInterval, timeout, slop))
    }

    @Test
    fun `un segundo toque tardio o lejano no es doble toque`() {
        val tracker = MiniSliderTapTracker()
        tracker.recordTap(upMs = 1000L, x = 10f, y = 10f)
        assertEquals(false, tracker.completesDoubleTap(1400L, 10f, 10f, minInterval, timeout, slop))
        assertEquals(false, tracker.completesDoubleTap(1100L, 200f, 10f, minInterval, timeout, slop))
    }

    @Test
    fun `el rebote del dedo no cuenta como doble toque`() {
        val tracker = MiniSliderTapTracker()
        tracker.recordTap(upMs = 1000L, x = 10f, y = 10f)
        assertEquals(false, tracker.completesDoubleTap(1010L, 10f, 10f, minInterval, timeout, slop))
    }

    @Test
    fun `tras un doble toque la memoria se reinicia y el tercero no encadena otro`() {
        val tracker = MiniSliderTapTracker()
        tracker.recordTap(upMs = 1000L, x = 10f, y = 10f)
        assertEquals(true, tracker.completesDoubleTap(1150L, 10f, 10f, minInterval, timeout, slop))
        tracker.clear()
        assertEquals(false, tracker.completesDoubleTap(1300L, 10f, 10f, minInterval, timeout, slop))
    }

    @Test
    fun `un arrastre intermedio reinicia la memoria`() {
        val tracker = MiniSliderTapTracker()
        tracker.recordTap(upMs = 1000L, x = 10f, y = 10f)
        tracker.clear()
        assertEquals(false, tracker.completesDoubleTap(1100L, 10f, 10f, minInterval, timeout, slop))
    }

    // ---- Valor numérico del volumen ----

    @Test
    fun `la descripcion del valor numerico dice el nivel en decibelios`() {
        assertEquals("Volumen 0.0 decibelios. Toca para escribir un valor o desliza hacia arriba o abajo para ajustarlo", audioVolumeReadoutDescription(1f))
        assertEquals("Volumen -6.0 decibelios. Toca para escribir un valor o desliza hacia arriba o abajo para ajustarlo", audioVolumeReadoutDescription(0.501f))
    }

    @Test
    fun `la descripcion del silencio no lee menos infinito`() {
        assertEquals(
            "Volumen mínimo, sin sonido. Toca para escribir un valor o desliza hacia arriba o abajo para ajustarlo",
            audioVolumeReadoutDescription(0f)
        )
    }

    // ---- Imán al valor por defecto ----

    @Test
    fun `el radio del iman se acota a una fraccion del recorrido`() {
        // Panorama corto: 50 px de recorrido -> tope de 5 px aunque se pidan 20.
        assertEquals(5f, audioMiniSliderSnapRadiusPx(20f, 50f), 1e-4f)
        // Recorrido largo: manda el radio pedido.
        assertEquals(15f, audioMiniSliderSnapRadiusPx(15f, 400f), 1e-4f)
        // Sin recorrido, radio nulo o no finito: sin iman.
        assertEquals(0f, audioMiniSliderSnapRadiusPx(10f, 0f), 0f)
        assertEquals(0f, audioMiniSliderSnapRadiusPx(0f, 100f), 0f)
        assertEquals(0f, audioMiniSliderSnapRadiusPx(Float.NaN, 100f), 0f)
    }

    @Test
    fun `el panorama se pega al centro cuando el dedo esta cerca`() {
        val travel = 100f
        val radius = 5f // 5 px de 100 px de recorrido sobre -1..1 = 0,1 de panorama
        assertEquals(0f, audioMiniSliderSnap(0.08f, 0f, travel, radius, panRange), 0f)
        assertEquals(0f, audioMiniSliderSnap(-0.09f, 0f, travel, radius, panRange), 0f)
        // Fuera de la zona el valor no se toca.
        assertEquals(0.2f, audioMiniSliderSnap(0.2f, 0f, travel, radius, panRange), 0f)
        assertEquals(-0.5f, audioMiniSliderSnap(-0.5f, 0f, travel, radius, panRange), 0f)
    }

    @Test
    fun `el volumen se pega al cien por ciento por ambos lados`() {
        val travel = 150f
        val radius = 5f // 5 px de 150 px sobre 0..1,5 = 0,05 de volumen
        assertEquals(1f, audioMiniSliderSnap(0.96f, 1f, travel, radius, volumeRange), 0f)
        assertEquals(1f, audioMiniSliderSnap(1.04f, 1f, travel, radius, volumeRange), 0f)
        assertEquals(0.9f, audioMiniSliderSnap(0.9f, 1f, travel, radius, volumeRange), 0f)
        assertEquals(1.2f, audioMiniSliderSnap(1.2f, 1f, travel, radius, volumeRange), 0f)
    }

    @Test
    fun `el borde de la zona del iman es inclusivo y justo fuera no encaja`() {
        // 0,125 / 2 * 80 px = 5 px exactos (valores binarios exactos: sin error de redondeo).
        assertTrue(audioMiniSliderInSnapZone(0.125f, 0f, 80f, 5f, panRange))
        assertFalse(audioMiniSliderInSnapZone(0.126f, 0f, 80f, 5f, panRange))
    }

    @Test
    fun `sin recorrido, sin radio o con valores no finitos el iman no actua`() {
        assertFalse(audioMiniSliderInSnapZone(0f, 0f, 0f, 5f, panRange))
        assertFalse(audioMiniSliderInSnapZone(0f, 0f, 100f, 0f, panRange))
        assertFalse(audioMiniSliderInSnapZone(Float.NaN, 0f, 100f, 5f, panRange))
        assertFalse(audioMiniSliderInSnapZone(0f, Float.POSITIVE_INFINITY, 100f, 5f, panRange))
        assertFalse(audioMiniSliderInSnapZone(0f, 0f, 100f, 5f, 1f..1f))
        assertEquals(0.3f, audioMiniSliderSnap(0.3f, 0f, 0f, 5f, panRange), 0f)
    }

    @Test
    fun `un toque justo sobre la rayita del panorama cae exactamente en cero`() {
        // Deslizador de 64 px con tirador de 7 px: recorrido 50 px, centro en x = 32.
        val raw = audioMiniSliderValueAt(33f, 64f, 7f, panRange) // ~0,04: casi centro, no exacto
        val radius = audioMiniSliderSnapRadiusPx(15f, 50f)
        assertEquals(0f, audioMiniSliderSnap(raw, 0f, 50f, radius, panRange), 0f)
    }
}
