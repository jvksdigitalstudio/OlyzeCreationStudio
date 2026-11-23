package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Recorte gapless (encoder-delay / encoder-padding) del PCM decodificado de MP3/AAC. */
class AudioGaplessTrimTest {

    @Test
    fun `recorta retardo al inicio y relleno al final en mono`() {
        val pcm = shortArrayOf(0, 0, 1, 2, 3, 0)
        assertArrayEquals(shortArrayOf(1, 2, 3), AudioProcessor.trimGapless(pcm, 1, 2, 1))
    }

    @Test
    fun `en estereo se recortan frames completos`() {
        // 4 frames: (0,0) (1,10) (2,20) (0,0)
        val pcm = shortArrayOf(0, 0, 1, 10, 2, 20, 0, 0)
        assertArrayEquals(shortArrayOf(1, 10, 2, 20), AudioProcessor.trimGapless(pcm, 2, 1, 1))
    }

    @Test
    fun `sin metadatos devuelve el mismo array`() {
        val pcm = shortArrayOf(1, 2, 3)
        assertSame(pcm, AudioProcessor.trimGapless(pcm, 1, 0, 0))
    }

    @Test
    fun `metadato corrupto que vaciaria el audio se ignora`() {
        val pcm = shortArrayOf(1, 2, 3)
        assertSame(pcm, AudioProcessor.trimGapless(pcm, 1, 2, 1))   // 2+1 == 3 frames
        assertSame(pcm, AudioProcessor.trimGapless(pcm, 1, 5000, 0))
    }

    @Test
    fun `valores negativos se tratan como cero`() {
        val pcm = shortArrayOf(1, 2, 3)
        assertSame(pcm, AudioProcessor.trimGapless(pcm, 1, -4, -1))
    }

    @Test
    fun `solo retardo sin relleno`() {
        assertArrayEquals(shortArrayOf(3, 4), AudioProcessor.trimGapless(shortArrayOf(1, 2, 3, 4), 1, 2, 0))
    }
}
