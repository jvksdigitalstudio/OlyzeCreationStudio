package com.yeivikas.olyzecs.engine.audio

import kotlin.math.abs

// Reglas PURAS (sin Android) de la reproducción en vivo de un carril con Reverb: cuándo arrancar, pausar o reposicionar, y cómo
// leer el contador de posición del `AudioTrack`. Las usa [LiveReverbTrackPlayer] y [AudioPreviewPlayers]; se prueban con JUnit.

/**
 * Tiempo (ms) tras un arranque o reposicionamiento durante el cual NO se corrige el desvío. Un reposicionamiento es audible
 * (corte y calentamiento del reverb) y la posición medida justo después aún no es fiable (el contador del dispositivo avanza en
 * saltos de un búfer): corregir ahí encadena seeks, que se oyen como un audio entrecortado. La tolerancia de desvío es la
 * MISMA que la del `MediaPlayer` ([DRIFT_TOLERANCE_MS]): una sola regla para todos los carriles, y holgada para que un
 * tirón de la interfaz (el reloj del proyecto avanza a lo sumo 200 ms por tick) no provoque seeks falsos.
 */
internal const val LIVE_SETTLE_MS = 600L

/** Plazo máximo (ms) que se conserva el sonido anterior de un carril mientras arranca el que lo reemplaza: pasado, se suelta aunque el nuevo no haya arrancado. */
internal const val HANDOVER_TIMEOUT_MS = 1_500L

/**
 * `true` cuando ya se puede soltar el sonido anterior de un carril que cambia de reproductor (del `MediaPlayer` al motor en
 * vivo, o de una mezcla seca a otra): cuando el reemplazo YA suena ([replacementProducing]), cuando no debe sonar nada
 * ([wantsSound] `false`: pausa, o carril silenciado), o cuando se agotó el plazo ([HANDOVER_TIMEOUT_MS]). Soltar el anterior
 * antes dejaba un hueco de silencio del tamaño de lo que tarda en arrancar el nuevo; esperar solo solapa ambos unos ms.
 */
internal fun handoverComplete(wantsSound: Boolean, replacementProducing: Boolean, waitedMs: Long): Boolean =
    !wantsSound || replacementProducing || waitedMs >= HANDOVER_TIMEOUT_MS

/** Qué debe hacer el reproductor en vivo de un carril en este tick de sincronización. */
internal sealed interface LiveSyncAction {
    /** Callarlo (el proyecto está en pausa, el carril no es el solista, o el cabezal está fuera del audio). */
    data object Pause : LiveSyncAction

    /** Dejarlo como está: ya suena donde debe. */
    data object Keep : LiveSyncAction

    /** Arrancarlo en [frame] (estaba parado). */
    data class StartAt(val frame: Long) : LiveSyncAction

    /** Reposicionarlo en [frame] (suena pero fuera de lugar, o se pidió explícitamente). */
    data class SeekTo(val frame: Long) : LiveSyncAction
}

/**
 * Muestras por canal que avanzó el cabezal del proyecto en [elapsedNanos]. El reproductor las descarta (procesándolas, para
 * que el reverb ya suene) al arrancar o reposicionarse: atender la orden tarda y el cabezal no espera, así que sin esto el
 * audio nacería atrasado por ese tiempo.
 */
internal fun catchUpFrames(elapsedNanos: Long, sampleRateHz: Int): Long =
    elapsedNanos.coerceAtLeast(0L) * sampleRateHz / 1_000_000_000L

/** Muestra por canal que corresponde a [timeMs] del proyecto a [sampleRateHz]: el audio seco empieza en el instante 0. */
internal fun frameAtMs(timeMs: Long, sampleRateHz: Int): Long = timeMs.coerceAtLeast(0L) * sampleRateHz / 1_000L

/**
 * Decide la acción del reproductor en vivo. Un seek es audible, así que solo se hace cuando hace falta:
 *  - [wantsSound] `false` (pausa, o carril silenciado por la vista previa de un clip) o cabezal fuera de `[0, totalFrames)`
 *    (antes de empezar, o pasada la cola del reverb) → [LiveSyncAction.Pause];
 *  - parado → [LiveSyncAction.StartAt] donde está el cabezal;
 *  - [forceSeek] (arranque explícito, vuelta del bucle) → [LiveSyncAction.SeekTo];
 *  - suena a más de [DRIFT_TOLERANCE_MS] de donde debe, pasado el [LIVE_SETTLE_MS] del último reposicionamiento →
 *    [LiveSyncAction.SeekTo];
 *  - si no → [LiveSyncAction.Keep].
 *
 * @param expectedFrame muestra que debería sonar ahora ([frameAtMs] del cabezal).
 * @param totalFrames largo total reproducible (audio + cola del reverb).
 * @param isRunning el reproductor está en marcha (ya se le ordenó arrancar).
 * @param audibleFrame muestra que suena ahora según el reproductor.
 * @param msSinceReposition ms desde el último arranque o reposicionamiento ordenado a este reproductor.
 */
internal fun planLiveSync(
    wantsSound: Boolean,
    expectedFrame: Long,
    totalFrames: Long,
    isRunning: Boolean,
    audibleFrame: Long,
    sampleRateHz: Int,
    forceSeek: Boolean,
    msSinceReposition: Long
): LiveSyncAction {
    if (!wantsSound || expectedFrame < 0L || expectedFrame >= totalFrames) return LiveSyncAction.Pause
    if (!isRunning) return LiveSyncAction.StartAt(expectedFrame)
    if (forceSeek) return LiveSyncAction.SeekTo(expectedFrame)
    if (msSinceReposition < LIVE_SETTLE_MS) return LiveSyncAction.Keep
    val driftMs = abs(audibleFrame - expectedFrame) * 1_000L / sampleRateHz
    return if (driftMs > DRIFT_TOLERANCE_MS) LiveSyncAction.SeekTo(expectedFrame) else LiveSyncAction.Keep
}

/**
 * Lee el contador de posición del `AudioTrack` (`playbackHeadPosition`): un entero de 32 bits que debe interpretarse SIN signo,
 * que da la vuelta cada ~27 h a 44.1 kHz y que `flush()`/`stop()` ponen a cero. Entrega siempre las muestras reproducidas
 * desde el último [reset], acumuladas en 64 bits.
 *
 * Un descenso del contador es una vuelta completa solo si venía de la mitad alta del rango y cae en la baja; cualquier otro
 * descenso es un reinicio del dispositivo (un `flush` cuyo efecto se vio después de [reset]) y se toma como tal: así una
 * lectura rezagada nunca se confunde con una vuelta (que sumaría 2³² muestras de golpe).
 */
internal class HeadPositionCounter {
    private var lastRaw = 0L
    private var wraps = 0L

    /** Vuelve a cero: llamarlo cuando se hace `flush()` del `AudioTrack`. */
    fun reset() {
        lastRaw = 0L
        wraps = 0L
    }

    /** Muestras reproducidas desde el último [reset], dado el valor crudo de `playbackHeadPosition`. */
    fun update(rawHeadPosition: Int): Long {
        val raw = rawHeadPosition.toLong() and UNSIGNED_INT_MASK
        if (raw < lastRaw) {
            wraps = if (lastRaw >= HALF_RANGE && raw < HALF_RANGE) wraps + FULL_RANGE else 0L
        }
        lastRaw = raw
        return wraps + raw
    }

    private companion object {
        const val UNSIGNED_INT_MASK = 0xFFFF_FFFFL
        const val HALF_RANGE = 1L shl 31
        const val FULL_RANGE = 1L shl 32
    }
}

/**
 * Medidor de la carga del DSP: relación entre el tiempo que tarda el motor en producir un bloque y la duración de audio de
 * ese bloque (1.0 = justo tiempo real; por encima el dispositivo se queda sin audio). Acumula y avisa cada [reportEveryMs] de
 * audio producido con la media y el pico de ese tramo, para saber con datos si un corte es de CPU o de otra cosa. Pura y sin
 * Android.
 */
internal class DspLoadMeter(
    private val reportEveryMs: Long = 5_000L,
    private val report: (average: Double, peak: Double) -> Unit
) {
    private var spentNanos = 0L
    private var producedNanos = 0L
    private var peak = 0.0

    fun record(spentNanosForBlock: Long, frames: Int, sampleRateHz: Int) {
        if (frames <= 0 || sampleRateHz <= 0) return
        val blockNanos = frames * 1_000_000_000L / sampleRateHz
        spentNanos += spentNanosForBlock
        producedNanos += blockNanos
        peak = maxOf(peak, spentNanosForBlock.toDouble() / blockNanos)
        if (producedNanos >= reportEveryMs * 1_000_000L) {
            report(spentNanos.toDouble() / producedNanos, peak)
            spentNanos = 0L
            producedNanos = 0L
            peak = 0.0
        }
    }
}
