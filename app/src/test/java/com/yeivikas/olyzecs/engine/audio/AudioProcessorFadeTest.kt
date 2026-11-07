package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test OBLIGATORIO de la auditoría completa del proyecto: cubre un bug
 * real encontrado en [AudioProcessor.applyVolumeAndFades] — antes, el
 * fade-out se calculaba siempre relativo a `totalFrames` (la duración
 * COMPLETA del proyecto). Con un clip de audio más corto que el proyecto
 * y sin loop, el tramo de silencio de relleno al final hacía que el
 * fade-out cayera FUERA del audio real — el fundido se aplicaba sobre
 * silencio (sin ningún efecto audible) y el audio real terminaba de
 * golpe, sin ningún fundido — exactamente lo contrario de lo que el
 * usuario configuró.
 *
 * Corre como JVM unit test puro: no construye ningún `AudioClip` (que
 * exigiría un `android.net.Uri` real, no disponible sin Robolectric —
 * ver el resto de tests de este proyecto, que evitan esto a propósito)
 * porque [AudioProcessor.applyVolumeAndFades] se ensanchó de `private`
 * a `internal` y ahora recibe primitivos (`volume`/`muted`/`fadeInMs`/
 * `fadeOutMs`) en vez de un `AudioClip` completo, específicamente para
 * habilitar este test.
 */
class AudioProcessorFadeTest {

    /** PCM sintético: todas las muestras al máximo volumen (Short.MAX_VALUE), un canal, para poder medir la ganancia aplicada por frame directamente del valor resultante. */
    private fun fullVolumePcm(totalFrames: Int): ShortArray = ShortArray(totalFrames) { Short.MAX_VALUE }

    private fun gainAt(pcm: ShortArray, frame: Int): Float =
        pcm[frame].toFloat() / Short.MAX_VALUE.toFloat()

    @Test
    fun `fade-out se aplica sobre el audio real, no sobre el silencio de relleno, cuando el clip es mas corto que el proyecto y no hace loop`() {
        val totalFrames = 1000 // duración total del proyecto (incluye relleno)
        val audibleFrames = 400 // el clip real "suena" solo hasta acá; el resto es silencio de relleno
        val sampleRateHz = 100 // 1 frame = 10ms, para números redondos y legibles
        val fadeOutMs = 1000L // 1000ms = 100 frames de fade-out

        val pcm = fullVolumePcm(totalFrames)
        // Frames >= audibleFrames representan el relleno de silencio: se
        // simulan en 0, como los deja realmente `buildProjectSamples`
        // (un ShortArray recién creado empieza en todo ceros, y el loop de
        // copia nunca llega a escribir ahí si `!loop` y el clip es corto).
        for (i in audibleFrames until totalFrames) pcm[i] = 0

        AudioProcessor.applyVolumeAndFades(
            pcm = pcm,
            channels = 1,
            sampleRateHz = sampleRateHz,
            volume = 1f,
            muted = false,
            fadeInMs = 0L,
            fadeOutMs = fadeOutMs,
            totalFrames = totalFrames,
            audibleFrames = audibleFrames
        )

        // El fade-out debe terminar de acabarse EXACTAMENTE en el último
        // frame audible (audibleFrames - 1), quedando casi en 0 justo ahí
        // — no 600 frames antes (fin del proyecto) sin ningún efecto.
        val gainRightBeforeAudioEnds = gainAt(pcm, audibleFrames - 1)
        assertTrue(
            "el fade-out debería haber bajado la ganancia del último frame audible casi a 0, pero quedó en $gainRightBeforeAudioEnds — el fundido no llegó a tocar el audio real",
            gainRightBeforeAudioEnds < 0.05f
        )

        // Y a mitad de camino del fade-out (dentro de la zona audible) la
        // ganancia debe estar claramente por debajo de 1 (fundiendo), no
        // en volumen pleno. fadeOutMs=1000 a sampleRateHz=100 son 100
        // frames de fade-out (fadeOutFrames = fadeOutMs*sampleRateHz/1000);
        // el fundido arranca en (audibleFrames - fadeOutFrames) = 300 y
        // su punto medio cae en el frame 350.
        val fadeOutFrames = (fadeOutMs * sampleRateHz / 1000L).toInt()
        val midFadeFrame = audibleFrames - fadeOutFrames / 2
        val midFadeGain = gainAt(pcm, midFadeFrame)
        assertTrue(
            "a mitad del fade-out (frame $midFadeFrame) la ganancia debería estar bajando (< 0.8), pero es $midFadeGain",
            midFadeGain < 0.8f
        )
    }

    @Test
    fun `sin fade-out configurado, el audio real permanece a volumen pleno hasta donde termina`() {
        val totalFrames = 1000
        val audibleFrames = 400
        val pcm = fullVolumePcm(totalFrames)
        for (i in audibleFrames until totalFrames) pcm[i] = 0

        AudioProcessor.applyVolumeAndFades(
            pcm = pcm,
            channels = 1,
            sampleRateHz = 100,
            volume = 1f,
            muted = false,
            fadeInMs = 0L,
            fadeOutMs = 0L,
            totalFrames = totalFrames,
            audibleFrames = audibleFrames
        )

        assertEquals(
            "sin fade-out, el último frame audible debería seguir a volumen pleno",
            1f,
            gainAt(pcm, audibleFrames - 1),
            0.01f
        )
    }

    @Test
    fun `cuando el clip llena todo el proyecto (loop o clip largo), el fade-out sigue calculandose contra el final del proyecto como antes`() {
        // audibleFrames == totalFrames: mismo comportamiento que existía
        // antes de esta corrección — no debe haber ninguna regresión para
        // el caso común (clip en loop, o más largo que el proyecto).
        val totalFrames = 1000
        val fadeOutMs = 1000L // 100 frames a 100Hz
        val pcm = fullVolumePcm(totalFrames)

        AudioProcessor.applyVolumeAndFades(
            pcm = pcm,
            channels = 1,
            sampleRateHz = 100,
            volume = 1f,
            muted = false,
            fadeInMs = 0L,
            fadeOutMs = fadeOutMs,
            totalFrames = totalFrames,
            audibleFrames = totalFrames
        )

        val gainAtVeryEnd = gainAt(pcm, totalFrames - 1)
        assertTrue(
            "el fade-out debería llegar casi a 0 en el último frame del proyecto cuando el audio lo llena entero, pero quedó en $gainAtVeryEnd",
            gainAtVeryEnd < 0.05f
        )
    }

    @Test
    fun `muted en true silencia todo el buffer sin importar volumen o fades`() {
        val pcm = fullVolumePcm(200)
        AudioProcessor.applyVolumeAndFades(
            pcm = pcm,
            channels = 1,
            sampleRateHz = 100,
            volume = 1f,
            muted = true,
            fadeInMs = 0L,
            fadeOutMs = 0L,
            totalFrames = 200,
            audibleFrames = 200
        )
        assertTrue("con muted=true todo el buffer debería quedar en 0", pcm.all { it == 0.toShort() })
    }

    @Test
    fun `volume en 0 silencia todo el buffer`() {
        val pcm = fullVolumePcm(200)
        AudioProcessor.applyVolumeAndFades(
            pcm = pcm,
            channels = 1,
            sampleRateHz = 100,
            volume = 0f,
            muted = false,
            fadeInMs = 0L,
            fadeOutMs = 0L,
            totalFrames = 200,
            audibleFrames = 200
        )
        assertTrue("con volume=0 todo el buffer debería quedar en 0", pcm.all { it == 0.toShort() })
    }
}
