package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Simulación determinista (reloj ficticio, sin Android) del lazo de control entre el cabezal del proyecto y un reproductor en
 * vivo: cada 50 ms el ViewModel llama a la sincronización ([planLiveSync]) y el reproductor tarda [startLatencyMs] en
 * empezar a sonar tras arrancar o reposicionarse. Documenta el defecto que se oía como audio entrecortado: SIN alcanzar al
 * cabezal ([catchUpFrames]) el audio nace atrasado por esa latencia, el atraso se toma por deriva y se corrige con seeks que
 * reinician la latencia, en cadena. CON ello el audio nace donde está el cabezal y no hay ni un seek.
 */
class LivePlaybackSimulationTest {
    private val rate = 44_100
    private val tickMs = 50L

    /** Reproductor modelo: [catchUp] decide si, al terminar de arrancar, el audio ya está donde el cabezal o atrasado. */
    private class FakePlayer(val rate: Int, val startLatencyMs: Long, val catchUp: Boolean) {
        var running = false
        var lastRepositionAtMs = Long.MIN_VALUE / 2
        var startedSoundingAtMs = 0L
        var basePosMs = 0L
        var seeks = 0
        var starts = 0

        fun reposition(frame: Long, nowMs: Long, countAs: String) {
            running = true
            lastRepositionAtMs = nowMs
            startedSoundingAtMs = nowMs + startLatencyMs
            // Con alcance, el audio que empieza a sonar tras la latencia es el del instante en que empieza.
            basePosMs = frame * 1_000L / rate + if (catchUp) startLatencyMs else 0L
            if (countAs == "seek") seeks++ else starts++
        }

        /** Posición que suena (muestras): antes de empezar a sonar, la pedida; luego avanza en tiempo real. */
        fun audibleFrame(nowMs: Long): Long {
            val playedMs = (nowMs - startedSoundingAtMs).coerceAtLeast(0L)
            return (basePosMs + playedMs) * rate / 1_000L
        }
    }

    private fun simulate(startLatencyMs: Long, catchUp: Boolean, seconds: Int = 10): FakePlayer {
        val player = FakePlayer(rate, startLatencyMs, catchUp)
        val total = rate * 60L
        var nowMs = 0L
        while (nowMs <= seconds * 1_000L) {
            val expected = nowMs * rate / 1_000L
            val action = planLiveSync(
                wantsSound = true,
                expectedFrame = expected,
                totalFrames = total,
                isRunning = player.running,
                audibleFrame = player.audibleFrame(nowMs),
                sampleRateHz = rate,
                forceSeek = false,
                msSinceReposition = nowMs - player.lastRepositionAtMs
            )
            when (action) {
                is LiveSyncAction.StartAt -> player.reposition(action.frame, nowMs, "start")
                is LiveSyncAction.SeekTo -> player.reposition(action.frame, nowMs, "seek")
                LiveSyncAction.Keep, LiveSyncAction.Pause -> Unit
            }
            nowMs += tickMs
        }
        return player
    }

    @Test
    fun `alcanzando al cabezal no hay seeks aunque el arranque tarde`() {
        for (latency in listOf(0L, 40L, 120L, 250L, 400L, 800L)) {
            val player = simulate(startLatencyMs = latency, catchUp = true)
            assertEquals("Con $latency ms de arranque no debe haber seeks", 0, player.seeks)
            assertEquals(1, player.starts)
        }
    }

    @Test
    fun `el audio alcanzado queda alineado con el cabezal`() {
        val player = simulate(startLatencyMs = 300L, catchUp = true, seconds = 5)
        val driftMs = abs(player.audibleFrame(5_000L) - 5_000L * rate / 1_000L) * 1_000L / rate
        assertTrue("Desvío de $driftMs ms", driftMs < 50L)
    }

    @Test
    fun `sin alcanzar al cabezal un arranque lento se corrige con seeks que se repiten`() {
        // La conducta anterior: tolerancia de 150 ms y sin espera tras reposicionar. Con un arranque de 400 ms el audio
        // nace 400 ms atrasado, supera la tolerancia y se reposiciona una y otra vez (cada vez, otros 400 ms de atraso).
        val storm = simulateLegacy(startLatencyMs = 400L)
        assertTrue("Debía encadenar seeks y solo hizo ${storm}", storm >= 5)
    }

    /** El lazo anterior: sin alcance, tolerancia de 150 ms y sin asentamiento (para contrastar). */
    private fun simulateLegacy(startLatencyMs: Long): Int {
        val player = FakePlayer(rate, startLatencyMs, catchUp = false)
        var nowMs = 0L
        while (nowMs <= 10_000L) {
            val expected = nowMs * rate / 1_000L
            val audible = player.audibleFrame(nowMs)
            val driftMs = abs(audible - expected) * 1_000L / rate
            when {
                !player.running -> player.reposition(expected, nowMs, "start")
                driftMs > 150L -> player.reposition(expected, nowMs, "seek")
            }
            nowMs += tickMs
        }
        return player.seeks
    }
}
