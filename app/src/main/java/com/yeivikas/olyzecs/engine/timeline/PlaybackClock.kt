package com.yeivikas.olyzecs.engine.timeline

/**
 * Reloj del cabezal de reproducción: convierte el tiempo REAL transcurrido
 * entre ticks (nanosegundos de un reloj monótono) en milisegundos enteros de
 * avance, SIN perder la fracción de milisegundo de cada tick.
 *
 * ## Por qué existe
 * El cabezal del proyecto es un `Long` en milisegundos. El bucle de
 * reproducción medía cada tick como `(ahora - anterior) / 1_000_000` y
 * descartaba el resto en cada vuelta. Con un tick de 16,4 ms se avanzaban
 * 16 ms: ~0,4 ms perdidos por tick, o sea un cabezal ~2-3 % más lento que el
 * reloj real a 60 fps y ~6-12 % a 120 fps (ticks de ~8 ms). El audio del
 * preview ([com.yeivikas.olyzecs.engine.audio.AudioPreviewPlayer]) corre en
 * tiempo real, así que se adelantaba al cabezal y, al superar la tolerancia
 * de deriva, el reproductor corregía con un `seekTo`: un salto audible
 * ("parón"/repetición) cada pocos segundos.
 *
 * Aquí el resto sub-milisegundo se ACUMULA ([carryNanos]) y se suma al tick
 * siguiente: el avance total converge exactamente al tiempo real transcurrido.
 *
 * Clase pura (sin Android) para poder verificarla con JUnit.
 */
internal class PlaybackClock(private val maxTickMs: Long) {

    init {
        require(maxTickMs > 0L) { "maxTickMs debe ser positivo (era $maxTickMs)" }
    }

    /** Fracción de milisegundo (0 until 1_000_000 ns) pendiente de aplicar al próximo tick. */
    private var carryNanos = 0L

    /**
     * Milisegundos enteros que debe avanzar el cabezal por [elapsedNanos] de
     * tiempo real. Un tick anormalmente largo (app en segundo plano, hilo
     * bloqueado) se acota a [maxTickMs] para que reanudar no produzca un salto
     * brusco; el exceso se descarta A PROPÓSITO (no se recupera luego), y la
     * fracción pendiente de ticks normales se conserva.
     */
    fun advanceMs(elapsedNanos: Long): Long {
        val cappedNanos = elapsedNanos.coerceIn(0L, maxTickMs * NANOS_PER_MS)
        val totalNanos = cappedNanos + carryNanos
        carryNanos = totalNanos % NANOS_PER_MS
        return totalNanos / NANOS_PER_MS
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
