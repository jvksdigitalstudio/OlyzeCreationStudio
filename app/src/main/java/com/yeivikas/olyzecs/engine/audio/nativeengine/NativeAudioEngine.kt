package com.yeivikas.olyzecs.engine.audio.nativeengine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.audio.PreviewLoopPlan
import com.yeivikas.olyzecs.engine.audio.clampAudioVolume
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import com.yeivikas.olyzecs.engine.audio.liveReverbParams

/**
 * Fachada Kotlin del motor de audio nativo. Traduce el modelo del proyecto ([AudioTrack] / [com.yeivikas.olyzecs.engine.audio.AudioClip])
 * a lo que entiende el motor, y le da transporte.
 *
 * Reglas de oro (las del diseño de Ardour / Tracktion):
 *  - El motor tiene UN reloj (el callback de audio). La app lo LEE ([masterPlayheadMs]); no lo persigue con correcciones.
 *  - La línea de tiempo se publica entera y de una vez, y solo cuando algo cambió de verdad ([syncTimeline] puede llamarse
 *    a 20 Hz sin costo: compara y no publica si no hay cambios).
 *  - Se mezclan TODOS los clips de todos los carriles, como la exportación (antes el preview solo hacía sonar el clip de arriba
 *    de cada carril: lo que se oía no era lo que se exportaba).
 *
 * Hilos: se usa desde el principal y desde el hilo de preparación de fuentes; el estado compartido va bajo [lock]. Las
 * llamadas nativas nunca bloquean al callback de audio.
 */
internal class NativeAudioEngine(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var handle = 0L
    val rateHz: Int

    // Fuentes: clave estable → id numérico para el motor. `loaded` = ya mapeadas en el motor.
    private val sourceIds = HashMap<String, Long>()
    private val loadedKeys = HashSet<String>()
    private var nextSourceId = 1L
    private var sourcesVersion = 0L

    // Carriles: id de cadena → id numérico estable para el motor (el reverb conserva su cola por este id).
    private val trackIds = HashMap<String, Long>()
    private var nextTrackId = 1L
    // Últimos parámetros de reverb enviados al motor, por carril: permite distinguir un giro de perilla (llamada directa) de un
    // cambio de estructura (republicar la línea de tiempo).
    private val sentReverb = HashMap<Long, ReverbParams>()

    // Última entrada de sincronización: se conserva para republicar cuando una fuente termina de prepararse.
    private var lastTracks: List<AudioTrack> = emptyList()
    private var lastSoloTrackId: String? = null
    private val projectEndFrame = 0L  // sin límite: ver [syncTimeline]
    private var publishedSignature: TimelineSignature? = null

    private var sentLoopFrames: List<Long> = emptyList()  // [inicio0, fin0, inicio1, fin1, …] en frames

    private var playRequested = false
    private var lastCommandAtMs = 0L

    private val stopStreamWhenIdle = Runnable {
        synchronized(lock) {
            if (!playRequested && handle != 0L) NativeEngineBindings.nativeStopStream(handle)
        }
    }

    init {
        rateHz = if (NativeEngineBindings.isAvailable) NativeEngineBindings.nativeEngineRateHz() else 48_000
    }

    /** `false` si no hay biblioteca nativa o el dispositivo de salida no pudo abrirse: quien llama usa el reproductor anterior. */
    @Volatile
    var isUsable: Boolean = NativeEngineBindings.isAvailable
        private set

    // ------------------------------------------------------------------ línea de tiempo

    /**
     * Pone el motor al día con [tracks]: TODOS los carriles, también los que tienen reverb (el reverb vive en el grafo del
     * motor y comparte su reloj) y, con [soloTrackId], solo ese carril.
     *
     * NO se acotan los clips al final del proyecto ni el motor se detiene solo en él: el final lo decide el ViewModel, que
     * puede AMPLIAR la duración mientras reproduce (`growIfApproachingEnd`); un tope fijo aquí cortaría el audio en el borde
     * viejo. (El núcleo nativo sí soporta ese acotado, con paridad probada contra la exportación, para cuando se necesite.)
     */
    fun syncTimeline(tracks: List<AudioTrack>, soloTrackId: String?) {
        synchronized(lock) {
            if (!ensureCreatedLocked()) return
            lastTracks = tracks
            lastSoloTrackId = soloTrackId
            publishLocked()
        }
    }

    /** Republica con el último solo conocido (cambió un volumen/balance/fundido sin que cambie el transporte). */
    fun refreshTimeline(tracks: List<AudioTrack>) {
        synchronized(lock) {
            if (!ensureCreatedLocked()) return
            lastTracks = tracks
            publishLocked()
        }
    }

    /**
     * Descarga del motor las fuentes que ningún clip del proyecto referencia ya (un archivo que se quitó): sin esto el mapa de
     * fuentes del motor crecería con cada archivo importado durante la sesión. Se cuenta TODO el proyecto, no solo los carriles
     * audibles: con un solo (vista previa de un clip) los demás carriles siguen siendo del proyecto y no deben recargarse después.
     */
    private fun releaseUnusedSourcesLocked() {
        val inUse = lastTracks.flatMapTo(HashSet<String>()) { track ->
            track.clips.map { EngineSourceCache.keyOf(it.sourceUri, it.sourceDurationMs, rateHz) }
        }
        val unused = loadedKeys.filter { it !in inUse }
        for (key in unused) {
            sourceIds[key]?.let { NativeEngineBindings.nativeUnloadSource(handle, it) }
            loadedKeys -= key
        }
    }

    private fun publishLocked() {
        releaseUnusedSourcesLocked()
        val audible = lastTracks.filter { lastSoloTrackId == null || it.id == lastSoloTrackId }
        val protectedKeys = ArrayList<String>()
        val trackSigs = ArrayList<TrackSignature>()
        val reverbByTrack = HashMap<Long, ReverbParams>()
        val clipSigs = ArrayList<ClipSignature>()

        for (track in audible) {
            val clips = track.clips.filter { !it.muted && it.clipLengthMs > 0L }
            if (clips.isEmpty()) continue
            val trackId = trackIds.getOrPut(track.id) { nextTrackId++ }
            val reverb = track.liveReverbParams
            if (reverb != null) reverbByTrack[trackId] = reverb
            trackSigs += TrackSignature(trackId, reverb != null, reverb?.size ?: 0f, reverb?.preDelayMs ?: 0f)

            for (clip in clips) {
                val key = EngineSourceCache.keyOf(clip.sourceUri, clip.sourceDurationMs, rateHz)
                protectedKeys += key
                val id = sourceIds.getOrPut(key) { nextSourceId++ }
                if (key !in loadedKeys && !EngineSourceCache.hasFailed(key)) ensureSourceLocked(clip.sourceUri, clip.sourceDurationMs, key, id)
                clipSigs += ClipSignature(
                    trackId = trackId,
                    sourceId = id,
                    startFrame = framesOf(clip.timelineStartMs),
                    lengthFrames = framesOf(clip.clipLengthMs),
                    trimFrame = framesOf(clip.trimStartMs),
                    fadeInFrames = framesOf(clip.fadeInMs),
                    fadeOutFrames = framesOf(clip.fadeOutMs),
                    loop = clip.loop,
                    reversed = clip.reversed,
                    // Misma fórmula que la exportación (AudioProcessor.applyVolumeAndFades).
                    gain = clampAudioVolume(clip.volume) * clip.normalizeGain.coerceAtLeast(0f),
                    pan = clip.pan
                )
            }
        }

        // La firma contiene solo lo ESTRUCTURAL de los reverbs (existencia, tamaño, pre-retardo): las perillas no la cambian.
        val signature = TimelineSignature(trackSigs, clipSigs, projectEndFrame, sourcesVersion)
        if (signature == publishedSignature) {
            applyReverbKnobsLocked(reverbByTrack)
            return
        }

        val clipLongs = LongArray(clipSigs.size * LONGS_PER_CLIP)
        val clipFloats = FloatArray(clipSigs.size * FLOATS_PER_CLIP)
        clipSigs.forEachIndexed { i, s ->
            val l = i * LONGS_PER_CLIP
            clipLongs[l] = s.trackId
            clipLongs[l + 1] = s.sourceId
            clipLongs[l + 2] = s.startFrame
            clipLongs[l + 3] = s.lengthFrames
            clipLongs[l + 4] = s.trimFrame
            clipLongs[l + 5] = s.fadeInFrames
            clipLongs[l + 6] = s.fadeOutFrames
            clipLongs[l + 7] = (if (s.loop) 1L else 0L) or (if (s.reversed) 2L else 0L)
            clipFloats[i * FLOATS_PER_CLIP] = s.gain
            clipFloats[i * FLOATS_PER_CLIP + 1] = s.pan
        }
        val trackLongs = LongArray(trackSigs.size * LONGS_PER_TRACK)
        val trackFloats = FloatArray(trackSigs.size * NATIVE_REVERB_FIELD_COUNT)
        trackSigs.forEachIndexed { i, t ->
            trackLongs[i * LONGS_PER_TRACK] = t.trackId
            trackLongs[i * LONGS_PER_TRACK + 1] = if (t.hasReverb) 1L else 0L
            // Sin reverb los 11 valores no se usan; los valores por defecto mantienen el arreglo bien formado.
            reverbByTrack[t.trackId].orDefault().toNativeArray().copyInto(trackFloats, i * NATIVE_REVERB_FIELD_COUNT)
        }

        val pending = NativeEngineBindings.nativeSetTimeline(
            handle, clipLongs, clipFloats, clipSigs.size, trackLongs, trackFloats, trackSigs.size, projectEndFrame
        )
        if (pending < 0) {
            AppLogger.e(TAG, "El motor rechazó la línea de tiempo (formato inválido)")
            return
        }
        publishedSignature = signature
        sentReverb.clear()
        sentReverb.putAll(reverbByTrack)
    }

    /** Perillas del reverb: llamada directa al motor, sin republicar nada ni cortar la cola. */
    private fun applyReverbKnobsLocked(reverbByTrack: Map<Long, ReverbParams>) {
        for ((trackId, params) in reverbByTrack) {
            if (sentReverb[trackId] == params) continue
            if (NativeEngineBindings.nativeSetReverbParams(handle, trackId, params.toNativeArray())) {
                sentReverb[trackId] = params
            } else {
                // El motor no tiene esa instancia (o el cambio fue estructural): se republica la línea de tiempo completa.
                publishedSignature = null
                publishLocked()
                return
            }
        }
    }

    /** Carga la fuente en el motor si su WAV ya está listo; si no, pide prepararla en segundo plano. */
    private fun ensureSourceLocked(uri: android.net.Uri, durationMs: Long, key: String, id: Long) {
        val file = EngineSourceCache.peek(appContext, key)
        if (file != null) {
            val error = NativeEngineBindings.nativeLoadSource(handle, id, file.path)
            if (error == null) {
                loadedKeys += key
                sourcesVersion++
            } else {
                AppLogger.w(TAG, "No se pudo cargar la fuente $key: $error")
            }
            return
        }
        val protectedNow = sourceIds.keys.toList()
        EngineSourceCache.request(appContext, uri, durationMs, rateHz, protectedNow) { _, ok ->
            // Hilo de preparación: al terminar, se reevalúa la línea de tiempo para que el clip empiece a sonar.
            if (ok) synchronized(lock) { if (handle != 0L) publishLocked() }
        }
    }

    /**
     * Pone el plan de bucle de la vista previa de un clip (`null` = sin bucle). Solo viaja al motor si cambió: es barato llamarlo
     * en cada sincronización. El motor recorre los tramos en orden y da la vuelta dentro del callback de audio.
     */
    fun setLoopPlan(plan: PreviewLoopPlan?) {
        synchronized(lock) {
            if (!ensureCreatedLocked()) return
            val frames = plan?.ranges?.flatMap { listOf(framesOf(it.startMs), framesOf(it.endMs)) } ?: emptyList()
            if (frames == sentLoopFrames) return
            NativeEngineBindings.nativeSetLoopPlan(handle, frames.toLongArray(), frames.size / 2)
            sentLoopFrames = frames
        }
    }

    // ------------------------------------------------------------------ transporte

    /** Arranca (o reanuda) desde [fromMs]. */
    fun play(fromMs: Long) {
        synchronized(lock) {
            if (!ensureCreatedLocked()) return
            mainHandler.removeCallbacks(stopStreamWhenIdle)
            if (!NativeEngineBindings.nativeStartStream(handle)) {
                AppLogger.e(TAG, "No se pudo abrir la salida de audio; se usa el reproductor de respaldo")
                isUsable = false
                return
            }
            NativeEngineBindings.nativeSeek(handle, framesOf(fromMs))
            NativeEngineBindings.nativePlay(handle)
            playRequested = true
            lastCommandAtMs = SystemClock.uptimeMillis()
        }
    }

    fun pause() {
        synchronized(lock) {
            if (handle == 0L || !playRequested) return
            NativeEngineBindings.nativePause(handle)
            playRequested = false
            lastCommandAtMs = SystemClock.uptimeMillis()
            // La salida se mantiene abierta unos segundos: reanudar es instantáneo. Pasado ese tiempo se libera.
            mainHandler.removeCallbacks(stopStreamWhenIdle)
            mainHandler.postDelayed(stopStreamWhenIdle, IDLE_STREAM_CLOSE_MS)
        }
    }

    fun seekMs(ms: Long) {
        synchronized(lock) {
            if (!ensureCreatedLocked()) return
            NativeEngineBindings.nativeSeek(handle, framesOf(ms))
            lastCommandAtMs = SystemClock.uptimeMillis()
        }
    }

    val isPlayRequested: Boolean get() = synchronized(lock) { playRequested }

    // ------------------------------------------------------------------ reloj maestro

    /** Posición del motor en ms (la que se está OYENDO, ya descontada la latencia de salida), o `null` si aún no es fiable. */
    fun masterPlayheadMs(): Long? {
        synchronized(lock) {
            if (handle == 0L || !playRequested) return null
            // Justo tras un play/seek el motor tarda hasta un bloque de audio en aplicarlo: durante ese instante la posición que
            // devuelve es la anterior. Se deja mandar al reloj de la UI para que el cabezal no dé un tirón hacia atrás.
            if (SystemClock.uptimeMillis() - lastCommandAtMs < COMMAND_SETTLE_MS) return null
            if (!NativeEngineBindings.nativeIsStreamRunning(handle)) return null
            return NativeEngineBindings.nativeAudiblePositionFrames(handle) * 1000L / rateHz
        }
    }

    /** [underruns, latenciaFrames, framesPorRáfaga, tamañoBúfer, bajaLatencia(0/1)] para diagnóstico. */
    fun streamStats(): LongArray {
        val out = LongArray(5)
        synchronized(lock) { if (handle != 0L) NativeEngineBindings.nativeStreamStats(handle, out) }
        return out
    }

    // ------------------------------------------------------------------ ciclo de vida

    fun release() {
        synchronized(lock) {
            mainHandler.removeCallbacks(stopStreamWhenIdle)
            if (handle != 0L) {
                NativeEngineBindings.nativeDestroy(handle)
                handle = 0L
            }
            playRequested = false
            sourceIds.clear()
            loadedKeys.clear()
            trackIds.clear()
            sentReverb.clear()
            sentLoopFrames = emptyList()
            publishedSignature = null
        }
    }

    private fun ensureCreatedLocked(): Boolean {
        if (!isUsable) return false
        if (handle == 0L) {
            handle = NativeEngineBindings.nativeCreate()
            if (handle == 0L) {
                isUsable = false
                return false
            }
        }
        return true
    }

    /** ms → frames con truncamiento hacia cero: idéntico a la exportación. */
    private fun framesOf(ms: Long): Long = ms.coerceAtLeast(0L) * rateHz / 1000L

    private data class TrackSignature(val trackId: Long, val hasReverb: Boolean, val reverbSize: Float, val reverbPreDelayMs: Float)

    private data class ClipSignature(
        val trackId: Long,
        val sourceId: Long,
        val startFrame: Long,
        val lengthFrames: Long,
        val trimFrame: Long,
        val fadeInFrames: Long,
        val fadeOutFrames: Long,
        val loop: Boolean,
        val reversed: Boolean,
        val gain: Float,
        val pan: Float
    )

    private data class TimelineSignature(
        val tracks: List<TrackSignature>,
        val clips: List<ClipSignature>,
        val projectEndFrame: Long,
        val sourcesVersion: Long
    )

    private fun ReverbParams?.orDefault(): ReverbParams = this ?: ReverbParams()

    private companion object {
        const val TAG = "NativeAudioEngine"
        const val LONGS_PER_CLIP = 8
        const val FLOATS_PER_CLIP = 2
        const val LONGS_PER_TRACK = 2
        const val IDLE_STREAM_CLOSE_MS = 15_000L
        const val COMMAND_SETTLE_MS = 60L
    }
}
