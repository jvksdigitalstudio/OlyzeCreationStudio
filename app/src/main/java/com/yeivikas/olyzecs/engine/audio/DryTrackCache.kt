package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.os.SystemClock
import com.yeivikas.olyzecs.debug.AppLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "DryTrackCache"

/** Tiempo sin cambios que se espera antes de renderizar: arrastrar un fader de volumen cambia la clave decenas de veces por segundo. */
private const val SETTLE_MS = 350L

/**
 * Cada cuánto (ms) se vuelve a comprobar en disco que la mezcla seca ya lista sigue ahí. El preview pregunta a ~20 Hz por cada
 * carril con Reverb, desde el hilo principal: sin esto cada pregunta hacía un SHA-256 y dos consultas de archivo.
 */
private const val READY_RECHECK_MS = 2_000L

/** Carpeta de caché de la versión anterior del preview (mezcla YA procesada): ya no se usa y se borra una vez. */
private const val LEGACY_CACHE_DIR = "track_bakes"

/**
 * Caché en disco de la mezcla SECA de un carril con Reverb, la entrada del motor en tiempo real
 * ([com.yeivikas.olyzecs.engine.audio.fx.LiveReverbStream]).
 *
 * Por qué existe: el motor en vivo lee el audio del carril por bloques desde un WAV en disco ([WavFilePcmSource]) en vez de
 * decodificarlo entero a memoria en el hilo de audio. Aquí se renderiza ese WAV con la MISMA función de mezcla que la
 * exportación ([AudioProcessor.mixTracks], sin efectos): lo que el motor procesa es exactamente lo que procesa la exportación.
 *
 * Lo esencial: la clave ([dryMixKey]) NO incluye los efectos. Mover una perilla del Reverb, activarlo o ponerlo en bypass no
 * renderiza nada; solo cambiar los clips o su volumen/balance/fundidos lo hace (y entonces sigue sonando la mezcla anterior
 * hasta que la nueva esté lista, ver [resolveDryMix]).
 *
 * Diseño:
 *  - Un único hilo de fondo: un render a la vez (cada uno ocupa la mezcla del carril en memoria).
 *  - Antiguo sustituido: cada carril solo conserva su ÚLTIMA petición; un render en curso de una versión vieja se
 *    cancela solo (la mezcla consulta `isCancelled`) y los pedidos que llegan en ráfaga se agrupan ([SETTLE_MS]).
 *  - Un render que falló no se reintenta solo (el preview consulta a ~20 Hz); cambiar el carril cambia la clave y lo reintenta.
 *  - Guardado atómico y purga de [PreviewWavCache].
 */
object DryTrackCache {
    private val wavs = PreviewWavCache(dirName = "dry_tracks", maxFiles = 4)

    /** Clave del carril por identidad de instancia: el preview pregunta a ~20 Hz con el mismo objeto; se calcula una vez por cambio. */
    private val keyMemo = ConcurrentHashMap<String, Pair<AudioTrack, String>>()

    /** Última clave pedida de cada carril (programada o en curso): lo que ya no coincide con ella está obsoleto. */
    private val pendingKey = ConcurrentHashMap<String, String>()
    private val scheduled = ConcurrentHashMap<String, ScheduledFuture<*>>()

    /** Última mezcla seca encontrada lista por carril, con la clave que la identifica y cuándo se comprobó en disco por última vez. */
    private class ReadyMix(val key: String, val mix: DryTrackMix, val checkedAtMs: Long)
    private val readyMemo = ConcurrentHashMap<String, ReadyMix>()
    /** Clave que falló por carril: acotado por el número de carriles (un conjunto de claves crecía con cada edición fallida). */
    private val failedKey = ConcurrentHashMap<String, String>()
    private val legacyCleaned = AtomicBoolean(false)
    private val executor = ScheduledThreadPoolExecutor(1) { r ->
        Thread(r, TAG).apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    /**
     * La mezcla seca de [track] si ya está lista; si no, `null` y se pide en segundo plano (idempotente: llamarla en
     * cada tick mientras falte no multiplica el trabajo). Quien llama sigue con lo que tenía hasta que esté.
     */
    internal fun dryMixFor(context: Context, track: AudioTrack): DryTrackMix? {
        // Sin ningún clip que suene no hay nada que mezclar (la mezcla fallaría con "no hay clips activos"): se queda como está.
        if (!track.hasAudibleClips) return null
        val app = context.applicationContext
        deleteLegacyCacheOnce(app)
        val key = keyOf(track)
        val nowMs = SystemClock.elapsedRealtime()
        val known = readyMemo[track.id]
        if (known != null && known.key == key && nowMs - known.checkedAtMs < READY_RECHECK_MS) return known.mix
        val file = wavs.peek(app, key)
        if (file != null) {
            val mix = DryTrackMix(file, dryMixDurationMs(track))
            readyMemo[track.id] = ReadyMix(key, mix, nowMs)
            return mix
        }
        readyMemo.remove(track.id)
        if (failedKey[track.id] != key && pendingKey[track.id] != key) schedule(app, track, key)
        return null
    }

    /** `true` si el render de la versión ACTUAL de [track] falló (una versión posterior reintenta: la clave cambia). */
    internal fun hasFailed(track: AudioTrack): Boolean = failedKey[track.id] == keyOf(track)

    /**
     * Descarta todo lo que tenga que ver con carriles que ya no existen (se borraron o se cerró el editor): su render
     * pendiente o en curso se cancela (la mezcla consulta `pendingKey`) y su WAV deja de estar protegido de la purga.
     */
    internal fun retain(liveTrackIds: Set<String>) {
        keyMemo.keys.retainAll(liveTrackIds)
        readyMemo.keys.retainAll(liveTrackIds)
        failedKey.keys.retainAll(liveTrackIds)
        pendingKey.keys.retainAll(liveTrackIds)
        scheduled.keys.filter { it !in liveTrackIds }.forEach { scheduled.remove(it)?.cancel(false) }
    }

    private fun keyOf(track: AudioTrack): String {
        val memo = keyMemo[track.id]
        if (memo != null && memo.first === track) return memo.second
        return dryMixKey(track).also { keyMemo[track.id] = track to it }
    }

    private fun schedule(context: Context, track: AudioTrack, key: String) {
        pendingKey[track.id] = key
        scheduled.remove(track.id)?.cancel(false)
        scheduled[track.id] = executor.schedule(Runnable { renderIfCurrent(context, track, key) }, SETTLE_MS, TimeUnit.MILLISECONDS)
    }

    private fun renderIfCurrent(context: Context, track: AudioTrack, key: String) {
        if (pendingKey[track.id] != key) return
        var rendered = false
        try {
            rendered = render(context, track, key)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo renderizar la mezcla seca del carril", t)
        } finally {
            // Solo es un fallo si nadie lo sustituyó: un render cancelado por una versión más nueva no falló.
            if (!rendered && pendingKey[track.id] == key) failedKey[track.id] = key
            pendingKey.remove(track.id, key)
        }
    }

    private fun render(context: Context, track: AudioTrack, key: String): Boolean {
        val startedMs = SystemClock.elapsedRealtime()
        // Un fallo del render de la vista previa NO es una acción del usuario: la mezcla lo registra como "último error visible"
        // y, si quedara ahí, la siguiente exportación lo mostraría como propio (mismo criterio que `decodeForAnalysis`).
        val previousUserFacingError = AppLogger.peekLastUserFacingError()
        // SIN efectos: el Reverb lo aplica el motor en vivo al reproducir. El resto de la mezcla es la de la exportación.
        val dryTrack = track.copy(effects = emptyList())
        val mix = AudioProcessor.mixTracks(context, listOf(dryTrack), dryMixDurationMs(track)) { pendingKey[track.id] != key }
        if (mix == null) {
            AppLogger.restoreLastUserFacingError(previousUserFacingError)
            return false
        }
        // La mezcla vigente de CADA carril está en uso (suena o suena la anterior mientras se renderiza la nueva): la purga
        // por antigüedad no puede borrarla, o con más carriles con Reverb que archivos en caché se borrarían entre sí sin fin.
        wavs.store(context, key, mix.samples, mix.sampleRateHz, mix.channelCount, protectedKeys = keyMemo.values.map { it.second })
        AppLogger.i(TAG, "Mezcla seca del carril renderizada: ${dryMixDurationMs(track)} ms de audio en ${SystemClock.elapsedRealtime() - startedMs} ms")
        return true
    }

    /** Borra, una sola vez por proceso y en segundo plano, la caché de la versión anterior (mezclas ya procesadas, hasta 4 WAV grandes). */
    private fun deleteLegacyCacheOnce(context: Context) {
        if (!legacyCleaned.compareAndSet(false, true)) return
        executor.execute {
            try {
                File(context.cacheDir, LEGACY_CACHE_DIR).deleteRecursively()
            } catch (t: Throwable) {
                AppLogger.w(TAG, "No se pudo borrar la caché anterior del preview", t)
            }
        }
    }
}
