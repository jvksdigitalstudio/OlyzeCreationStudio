package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas puras de la reproducción en vivo de un carril con Reverb ([LivePlaybackPolicy.kt]). */
class LivePlaybackPolicyTest {
    private val rate = 44_100
    private val total = rate * 10L // 10 s de audio y cola

    private fun plan(
        wantsSound: Boolean = true,
        expected: Long = rate * 2L,
        isRunning: Boolean = true,
        audible: Long = rate * 2L,
        forceSeek: Boolean = false,
        totalFrames: Long = total,
        msSinceReposition: Long = LIVE_SETTLE_MS
    ) = planLiveSync(wantsSound, expected, totalFrames, isRunning, audible, rate, forceSeek, msSinceReposition)

    // ---------------------------------------------------------------- frameAtMs

    @Test
    fun `el cabezal se convierte a muestras de la tasa del archivo`() {
        assertEquals(0L, frameAtMs(0L, rate))
        assertEquals(44_100L, frameAtMs(1_000L, rate))
        assertEquals(22_050L, frameAtMs(500L, rate))
        assertEquals(48_000L, frameAtMs(1_000L, 48_000))
    }

    @Test
    fun `un cabezal negativo se toma como el inicio y una hora no desborda`() {
        assertEquals(0L, frameAtMs(-5L, rate))
        assertEquals(3_600L * rate, frameAtMs(3_600_000L, rate))
    }

    // ---------------------------------------------------------------- planLiveSync

    @Test
    fun `en pausa se calla`() {
        assertEquals(LiveSyncAction.Pause, plan(wantsSound = false))
        assertEquals(LiveSyncAction.Pause, plan(wantsSound = false, isRunning = false))
    }

    @Test
    fun `fuera del audio y de su cola se calla`() {
        assertEquals(LiveSyncAction.Pause, plan(expected = total))
        assertEquals(LiveSyncAction.Pause, plan(expected = total + 1))
        assertEquals(LiveSyncAction.Pause, plan(expected = -1L))
    }

    @Test
    fun `el ultimo instante audible todavia suena`() {
        assertEquals(LiveSyncAction.Keep, plan(expected = total - 1, audible = total - 1))
    }

    @Test
    fun `parado arranca donde esta el cabezal`() {
        assertEquals(LiveSyncAction.StartAt(rate * 2L), plan(isRunning = false, audible = 0L))
    }

    @Test
    fun `un arranque explicito reposiciona aunque no haya desvio`() {
        assertEquals(LiveSyncAction.SeekTo(rate * 2L), plan(forceSeek = true))
    }

    @Test
    fun `el desvio dentro de la tolerancia no reposiciona`() {
        val within = rate * 2L + rate * DRIFT_TOLERANCE_MS / 1_000L
        assertEquals(LiveSyncAction.Keep, plan(audible = within))
        assertEquals(LiveSyncAction.Keep, plan(audible = rate * 2L - rate * DRIFT_TOLERANCE_MS / 1_000L))
    }

    @Test
    fun `el desvio mayor que la tolerancia reposiciona en cualquier sentido`() {
        val beyond = rate * (DRIFT_TOLERANCE_MS + 20L) / 1_000L
        assertEquals(LiveSyncAction.SeekTo(rate * 2L), plan(audible = rate * 2L + beyond))
        assertEquals(LiveSyncAction.SeekTo(rate * 2L), plan(audible = rate * 2L - beyond))
    }

    @Test
    fun `un reproductor que llego al final con el cabezal aun dentro se reposiciona`() {
        // Cola recortada por un cambio de parámetros: el motor terminó antes de lo que el cabezal esperaba.
        assertEquals(LiveSyncAction.SeekTo(rate * 5L), plan(expected = rate * 5L, audible = rate * 2L))
    }

    @Test
    fun `tras un reposicionamiento no se corrige la deriva hasta que se asienta`() {
        val beyond = rate * (DRIFT_TOLERANCE_MS + 100L) / 1_000L
        assertEquals(LiveSyncAction.Keep, plan(audible = rate * 2L - beyond, msSinceReposition = 0L))
        assertEquals(LiveSyncAction.Keep, plan(audible = rate * 2L - beyond, msSinceReposition = LIVE_SETTLE_MS - 1))
        assertEquals(LiveSyncAction.SeekTo(rate * 2L), plan(audible = rate * 2L - beyond, msSinceReposition = LIVE_SETTLE_MS))
    }

    @Test
    fun `un arranque o seek explicito ignora el asentamiento`() {
        assertEquals(LiveSyncAction.SeekTo(rate * 2L), plan(forceSeek = true, msSinceReposition = 0L))
        assertEquals(LiveSyncAction.StartAt(rate * 2L), plan(isRunning = false, msSinceReposition = 0L))
        assertEquals(LiveSyncAction.Pause, plan(wantsSound = false, msSinceReposition = 0L))
    }

    @Test
    fun `el asentamiento es mayor que la espera de un seek habitual`() {
        assertTrue(LIVE_SETTLE_MS >= SEEK_SETTLE_MS)
    }

    // ---------------------------------------------------------------- catchUpFrames

    @Test
    fun `el cabezal avanzado durante el arranque se convierte en muestras`() {
        assertEquals(0L, catchUpFrames(0L, rate))
        assertEquals(rate.toLong(), catchUpFrames(1_000_000_000L, rate))
        assertEquals(4_410L, catchUpFrames(100_000_000L, rate))
        assertEquals(4_800L, catchUpFrames(100_000_000L, 48_000))
    }

    @Test
    fun `un tiempo negativo no retrocede y una hora no desborda`() {
        assertEquals(0L, catchUpFrames(-5L, rate))
        assertEquals(3_600L * rate, catchUpFrames(3_600L * 1_000_000_000L, rate))
    }

    // ---------------------------------------------------------------- HeadPositionCounter

    @Test
    fun `el contador entrega las muestras reproducidas`() {
        val counter = HeadPositionCounter()
        assertEquals(0L, counter.update(0))
        assertEquals(1_024L, counter.update(1_024))
        assertEquals(50_000L, counter.update(50_000))
    }

    @Test
    fun `un flush vuelve el contador a cero`() {
        val counter = HeadPositionCounter()
        counter.update(88_200)
        counter.reset()
        assertEquals(0L, counter.update(0))
        assertEquals(4_096L, counter.update(4_096))
    }

    @Test
    fun `una lectura rezagada tras el reset no se confunde con una vuelta del contador`() {
        val counter = HeadPositionCounter()
        counter.update(88_200)
        counter.reset()
        // El dispositivo aún devuelve el valor viejo un instante, y luego el cero del flush.
        assertEquals(88_200L, counter.update(88_200))
        assertEquals(0L, counter.update(0))
        assertEquals(1_024L, counter.update(1_024))
    }

    @Test
    fun `el contador de 32 bits se lee sin signo y suma la vuelta completa`() {
        val counter = HeadPositionCounter()
        // 0x7FFFFFFF es el último positivo; el siguiente valor crudo (Int.MIN_VALUE) es 2^31 sin signo.
        assertEquals(0x7FFF_FFFFL, counter.update(Int.MAX_VALUE))
        assertEquals(0x8000_0000L, counter.update(Int.MIN_VALUE))
        assertEquals(0xFFFF_FFFFL, counter.update(-1))
        // Vuelta: el crudo cae a 5 y el total sigue creciendo en 2^32.
        assertEquals((1L shl 32) + 5L, counter.update(5))
        assertEquals((1L shl 32) + 1_000L, counter.update(1_000))
    }

    @Test
    fun `un flush tras una vuelta del contador vuelve a cero sin arrastrar la vuelta`() {
        val counter = HeadPositionCounter()
        counter.update(-1)
        counter.update(10)
        counter.reset()
        assertEquals(0L, counter.update(0))
    }

    // ---------------------------------------------------------------- handoverComplete

    @Test
    fun `el sonido anterior se suelta cuando el reemplazo ya suena`() {
        assertEquals(true, handoverComplete(wantsSound = true, replacementProducing = true, waitedMs = 0L))
    }

    @Test
    fun `mientras el reemplazo no suena se conserva el anterior para no dejar un hueco`() {
        assertEquals(false, handoverComplete(wantsSound = true, replacementProducing = false, waitedMs = 0L))
        assertEquals(false, handoverComplete(wantsSound = true, replacementProducing = false, waitedMs = HANDOVER_TIMEOUT_MS - 1))
    }

    @Test
    fun `en pausa el relevo es inmediato y vencido el plazo se suelta aunque el nuevo no suene`() {
        assertEquals(true, handoverComplete(wantsSound = false, replacementProducing = false, waitedMs = 0L))
        assertEquals(true, handoverComplete(wantsSound = true, replacementProducing = false, waitedMs = HANDOVER_TIMEOUT_MS))
    }

    // ---------------------------------------------------------------- medidor de carga del DSP

    @Test
    fun `el medidor de carga informa media y pico por tramo y reinicia`() {
        val reports = ArrayList<Pair<Double, Double>>()
        val meter = DspLoadMeter(reportEveryMs = 100L) { average, peak -> reports.add(average to peak) }
        // Bloques de 10 ms (441 muestras a 44,1 kHz): 5 ms de cálculo (50 %) y uno de 20 ms (200 %).
        repeat(9) { meter.record(5_000_000L, 441, 44_100) }
        assertTrue(reports.isEmpty())
        meter.record(20_000_000L, 441, 44_100)
        assertEquals(1, reports.size)
        assertEquals((9 * 5.0 + 20.0) / 100.0, reports[0].first, 1e-9)
        assertEquals(2.0, reports[0].second, 1e-9)
        repeat(10) { meter.record(1_000_000L, 441, 44_100) }
        assertEquals(2, reports.size)
        assertEquals(0.1, reports[1].first, 1e-9)
        assertEquals(0.1, reports[1].second, 1e-9)
    }
}
