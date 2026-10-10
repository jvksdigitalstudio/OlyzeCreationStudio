package com.yeivikas.olyzecs.engine.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackClockTest {

    private val ms = 1_000_000L

    @Test
    fun `un tick exacto en milisegundos avanza esos milisegundos`() {
        val clock = PlaybackClock(maxTickMs = 200L)
        assertEquals(16L, clock.advanceMs(16 * ms))
    }

    @Test
    fun `la fraccion de milisegundo no se pierde entre ticks`() {
        val clock = PlaybackClock(maxTickMs = 200L)
        // 1000 ticks de 16,4 ms = 16 400 ms reales. El cálculo anterior
        // (división entera por tick) avanzaba 16 000 ms: 2,4 % de deriva.
        var total = 0L
        repeat(1000) { total += clock.advanceMs(16_400_000L) }
        assertEquals(16_400L, total)
    }

    @Test
    fun `a 120 fps la deriva es cero (ticks de 8,3 ms)`() {
        val clock = PlaybackClock(maxTickMs = 200L)
        var total = 0L
        repeat(1200) { total += clock.advanceMs(8_300_000L) }
        // 1200 * 8,3 ms = 9 960 ms
        assertEquals(9_960L, total)
    }

    @Test
    fun `un tick sub-milisegundo no avanza pero acumula`() {
        val clock = PlaybackClock(maxTickMs = 200L)
        assertEquals(0L, clock.advanceMs(600_000L))
        assertEquals(1L, clock.advanceMs(600_000L)) // 0,6 + 0,6 = 1,2 ms
        assertEquals(0L, clock.advanceMs(700_000L)) // resto 0,2 + 0,7 = 0,9 ms
        assertEquals(1L, clock.advanceMs(100_000L)) // 0,9 + 0,1 = 1,0 ms
    }

    @Test
    fun `un tick anormalmente largo se acota y no arrastra el exceso`() {
        val clock = PlaybackClock(maxTickMs = 200L)
        assertEquals(200L, clock.advanceMs(5_000 * ms))
        assertEquals(10L, clock.advanceMs(10 * ms))
    }

    @Test
    fun `tiempo negativo no hace retroceder el cabezal`() {
        val clock = PlaybackClock(maxTickMs = 200L)
        assertEquals(0L, clock.advanceMs(-5 * ms))
    }

    @Test
    fun `maxTickMs no positivo es invalido`() {
        assertThrows(IllegalArgumentException::class.java) { PlaybackClock(0L) }
    }
}
