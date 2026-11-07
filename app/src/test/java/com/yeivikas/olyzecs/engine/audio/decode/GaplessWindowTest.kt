package com.yeivikas.olyzecs.engine.audio.decode

import org.junit.Assert.assertEquals
import org.junit.Test

class GaplessWindowTest {
    @Test
    fun `recorta el retardo del inicio y el relleno del final`() {
        assertEquals(FrameWindow(1105, 9_000 - 576), GaplessWindow.of(9_000, 1105, 576))
    }

    @Test
    fun `sin metadatos no recorta`() {
        assertEquals(FrameWindow(0, 500), GaplessWindow.of(500, 0, 0))
    }

    @Test
    fun `valores negativos se ignoran`() {
        assertEquals(FrameWindow(0, 500), GaplessWindow.of(500, -5, -9))
    }

    @Test
    fun `si el recorte se comeria todo el audio no se recorta nada`() {
        assertEquals(FrameWindow(0, 100), GaplessWindow.of(100, 60, 40)) // 60 + 40 == 100: no queda ni un frame
        assertEquals(FrameWindow(0, 100), GaplessWindow.of(100, 600, 400))
    }

    @Test
    fun `un solo frame de audio sobrante basta para recortar`() {
        assertEquals(FrameWindow(60, 61), GaplessWindow.of(100, 60, 39))
        assertEquals(1L, GaplessWindow.of(100, 60, 39).frameCount)
    }
}
