package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.os.SystemClock
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import com.yeivikas.olyzecs.engine.audio.nativeengine.NativeAudioEngine
import com.yeivikas.olyzecs.engine.audio.nativeengine.NativeEngineBindings
import kotlin.math.abs

private const val TAG = "AudioPreviewPlayers"

/**
 * Diferencia (ms) entre el cabezal de la UI y el reloj del motor nativo a partir de la cual se considera un SALTO real (el usuario
 * arrastró el cabezal, o el proyecto cambió de punto) y no una desviación a corregir. Por debajo no se hace NADA: el motor no se
 * persigue con seeks; esa persecución era la causa de los cortes del preview anterior.
 */
private const val NATIVE_RESYNC_JUMP_MS = 250L

/** Tiempo (ms) que se espera antes de reintentar el motor en vivo sobre una mezcla seca con la que ya falló (evita un bucle de reproductores a ~20 Hz). */
private const val LIVE_RETRY_AFTER_FAILURE_MS = 3_000L

/**
 * Preview en vivo de TODOS los carriles de audio.
 *
 * CAMINO PRINCIPAL — el MOTOR NATIVO ([NativeAudioEngine], C++/Oboe), para todos los carriles, con o sin reverb. UN solo
 * stream, UN solo reloj y UNA mezcla por muestra de todos los clips de todos los carriles, igual que la exportación (volumen,
 * balance, fundidos, loop, reversa) y con el mismo reverb (port de `StereoReverb`) dentro del grafo del motor. El reloj del
 * proyecto es el del callback de audio: la UI lo lee ([masterPlayheadMs]) y no lo persigue.
 *
 * RESPALDO — si la biblioteca nativa no carga (ABI no soportada) o el dispositivo de salida no se pudo abrir, los carriles
 * suenan como antes, cada uno por su reproductor:
 *  - sin reverb: un [AudioPreviewPlayer] (`MediaPlayer`);
 *  - con reverb: un [LiveReverbTrackPlayer]. Lee la mezcla SECA del carril ([DryTrackCache]: todos sus clips, como la
 *    exportación) y le aplica el Reverb EN VIVO con la misma matemática de la exportación. Mientras la mezcla seca no está
 *    lista (primer render), si falló o si el motor en vivo falló, el carril suena por el `MediaPlayer` con el audio real, sin
 *    reverb: nunca una mezcla desactualizada ([resolveDryMix]). Mientras se renderiza una versión nueva sigue sonando la anterior.
 * La app nunca se queda sin audio.
 *
 * Todo se invoca desde el hilo principal (lo hace el ViewModel).
 */
class AudioPreviewPlayers(private val context: Context, onInterrupted: () -> Unit = {}) {
    /**
     * Protege la sesión de reproducción frente al sistema (llamadas, otras apps de audio, auriculares desconectados): ante una
     * interrupción avisa con [onInterrupted] y el dueño pausa. Empieza al sonar y termina al pausar, en `sync`/`pause`/`release`.
     */
    private val playbackGuard = AudioPlaybackGuard(context, onInterrupted)

    /** Motor de audio nativo (todos los carriles). `null` si la biblioteca nativa no está disponible en este dispositivo. */
    private val nativeEngine: NativeAudioEngine? = if (NativeEngineBindings.isAvailable) NativeAudioEngine(context) else null

    /** El motor nativo si está operativo, `null` si no (entonces todo suena por el respaldo). Se reevalúa en cada uso. */
    private fun activeNativeEngine(): NativeAudioEngine? = nativeEngine?.takeIf { it.isUsable }

    /**
     * Posición (ms) del reloj del motor nativo — lo que se está OYENDO — o `null` si el motor no es quien manda ahora (no hay
     * reproducción nativa en curso, o acaba de recibir un play/seek y todavía no es fiable). El ViewModel la usa para mover el
     * cabezal: el audio es el reloj maestro, la interfaz lo sigue.
     */
    fun masterPlayheadMs(): Long? = activeNativeEngine()?.masterPlayheadMs()

    /** Reproductores `MediaPlayer`: carriles sin Reverb, o con Reverb mientras no hay mezcla seca utilizable. */
    private val players = HashMap<String, AudioPreviewPlayer>()

    /** Reproductores del motor en vivo: carriles con Reverb que ya tienen su mezcla seca. */
    private val livePlayers = HashMap<String, LiveReverbTrackPlayer>()

    /** Última mezcla seca lista de cada carril con Reverb: se sigue usando mientras se renderiza la versión nueva. */
    private val lastDryMixes = HashMap<String, DryTrackMix>()

    /** Mezcla seca (ruta) con la que el motor en vivo falló por carril, y hasta cuándo (`uptimeMillis`) no se reintenta. */
    private val liveFailures = HashMap<String, LiveFailure>()

    private class LiveFailure(val dryPath: String, val retryAtMs: Long)

    /**
     * Reproductores en vivo REEMPLAZADOS (la mezcla seca cambió) que siguen sonando hasta que el nuevo arranca: soltarlos al
     * instante dejaba un hueco de silencio del tamaño de lo que tarda en arrancar el reemplazo ([handoverComplete]).
     */
    private val retiring = HashMap<String, LiveReverbTrackPlayer>()

    /** Desde cuándo (`uptimeMillis`) un carril espera el relevo de su sonido anterior; sirve para vencer el plazo. */
    private val handoverSince = HashMap<String, Long>()

    /**
     * Sincroniza cada carril de [tracks] con el cabezal. Antes, libera los reproductores de carriles que ya no existen (se
     * borró su último clip): un reproductor huérfano retiene recursos de audio.
     *
     * Con [soloTrackId] (vista previa de un clip, ver [AudioClipPreview]) SOLO suena ese carril: los demás se sincronizan
     * como si el proyecto estuviera en PAUSA, que es la vía canónica de cada reproductor para callarse. No se liberan, así al
     * terminar la vista previa vuelven a sonar sin repetir la preparación. `null` = todos los carriles suenan (reproducción
     * normal).
     *
     * [loopPlan]: los tramos que se repiten en la vista previa de un clip. Con el motor nativo es el MOTOR quien los recorre
     * y da la vuelta dentro del callback de audio; en el respaldo lo hace el ViewModel con `forceSeek`. `null` = sin bucle.
     */
    fun sync(
        tracks: List<AudioTrack>,
        projectTimeMs: Long,
        isPlaying: Boolean,
        forceSeek: Boolean = false,
        soloTrackId: String? = null,
        loopPlan: PreviewLoopPlan? = null
    ) {
        if (isPlaying) playbackGuard.start() else playbackGuard.stop()
        activeNativeEngine()?.let { engine ->
            releaseFallbackPlayers()
            syncNative(engine, tracks, projectTimeMs, isPlaying, forceSeek, soloTrackId, loopPlan)
            return
        }
        val liveIds = tracks.mapTo(HashSet()) { it.id }
        players.keys.filter { it !in liveIds }.forEach { players.remove(it)?.release() }
        livePlayers.keys.filter { it !in liveIds }.forEach { livePlayers.remove(it)?.release() }
        retiring.keys.filter { it !in liveIds }.forEach { retiring.remove(it)?.release() }
        handoverSince.keys.retainAll(liveIds)
        lastDryMixes.keys.retainAll(liveIds)
        liveFailures.keys.retainAll(liveIds)
        DryTrackCache.retain(liveIds)
        tracks.forEach { track ->
            val audible = isPlaying && (soloTrackId == null || track.id == soloTrackId)
            val live = livePlayerFor(track)
            val params = track.liveReverbParams
            if (live != null && params != null) {
                syncLive(live, params, projectTimeMs, audible, forceSeek)
                // Relevo sin silencio: el sonido anterior (el `MediaPlayer` del audio real, o el reproductor en vivo
                // reemplazado) sigue hasta que este ya suena, y solo entonces se suelta.
                val hasPredecessor = players.containsKey(track.id) || retiring.containsKey(track.id)
                if (!hasPredecessor) {
                    handoverSince.remove(track.id)
                } else {
                    val now = SystemClock.uptimeMillis()
                    val waitedMs = now - handoverSince.getOrPut(track.id) { now }
                    if (handoverComplete(audible, live.isProducing, waitedMs)) {
                        players.remove(track.id)?.release()
                        retiring.remove(track.id)?.release()
                        handoverSince.remove(track.id)
                    } else {
                        players[track.id]?.sync(track, projectTimeMs, audible, forceSeek)
                    }
                }
            } else {
                retiring.remove(track.id)?.release()
                handoverSince.remove(track.id)
                playerFor(track.id).sync(track, projectTimeMs, audible, forceSeek)
            }
        }
    }

    /** Con el motor nativo activo ningún reproductor de respaldo debe seguir sonando (ni ocupando memoria). */
    private fun releaseFallbackPlayers() {
        if (players.isEmpty() && livePlayers.isEmpty() && retiring.isEmpty()) return
        players.values.forEach { it.release() }
        livePlayers.values.forEach { it.release() }
        retiring.values.forEach { it.release() }
        players.clear()
        livePlayers.clear()
        retiring.clear()
        handoverSince.clear()
        lastDryMixes.clear()
        liveFailures.clear()
        DryTrackCache.retain(emptySet())
    }

    /**
     * Pone el motor nativo al día. La línea de tiempo se publica solo si cambió (es barato llamarlo a 20 Hz) y el transporte
     * solo recibe órdenes cuando hay un CAMBIO REAL: arrancar, pausar, o un salto del cabezal. No hay correcciones continuas.
     */
    private fun syncNative(
        engine: NativeAudioEngine,
        tracks: List<AudioTrack>,
        projectTimeMs: Long,
        isPlaying: Boolean,
        forceSeek: Boolean,
        soloTrackId: String?,
        loopPlan: PreviewLoopPlan?
    ) {
        engine.syncTimeline(tracks, soloTrackId)
        engine.setLoopPlan(loopPlan) // antes del transporte: el primer bloque que suene ya respeta los tramos
        if (!isPlaying) {
            engine.pause()
            return
        }
        if (!engine.isPlayRequested) {
            engine.play(projectTimeMs)
            return
        }
        if (forceSeek) {
            engine.seekMs(projectTimeMs)
            return
        }
        // Con un plan de bucle el motor es dueño del tiempo (vuelve atrás A PROPÓSITO al terminar cada tramo): una diferencia con
        // el cabezal de la UI en ese instante no es un salto que corregir.
        if (loopPlan != null) return
        val master = engine.masterPlayheadMs() ?: return
        if (abs(master - projectTimeMs) > NATIVE_RESYNC_JUMP_MS) engine.seekMs(projectTimeMs)
    }

    /** Reposiciona el audio de cada carril en [projectTimeMs] (arrastre del cabezal con el audio ya preparado). */
    fun seekToProjectTime(tracks: List<AudioTrack>, projectTimeMs: Long) {
        activeNativeEngine()?.let {
            it.refreshTimeline(tracks)  // conserva el solo vigente (vista previa de clip) en vez de pisarlo
            it.seekMs(projectTimeMs)
            return
        }
        tracks.forEach { track ->
            val live = livePlayers[track.id]
            if (live != null) {
                // Parado no hace nada: el próximo `sync` lo arranca donde esté el cabezal.
                live.seekTo(frameAtMs(projectTimeMs, live.sampleRateHz))
                return@forEach
            }
            val clip = track.activeClipAt(projectTimeMs) ?: return@forEach
            playerFor(track.id).seekToProjectTime(clip, projectTimeMs)
        }
    }

    /**
     * Aplica en caliente volumen y balance del clip activo de cada carril del `MediaPlayer` (sin seek ni reinicio). Los
     * carriles del motor en vivo no lo necesitan ni lo admiten: su volumen, balance y fundidos ya van dentro de la mezcla
     * seca (como en la exportación), que se renderiza de nuevo al cambiarlos.
     */
    fun updateGains(tracks: List<AudioTrack>, projectTimeMs: Long) {
        // Motor nativo: volumen, balance y fundidos viajan en la línea de tiempo; publicarla (si cambió) los aplica a la vez, sin seek.
        activeNativeEngine()?.let {
            it.refreshTimeline(tracks)
            return
        }
        tracks.forEach { track ->
            if (livePlayers.containsKey(track.id)) return@forEach
            val clip = track.activeClipAt(projectTimeMs) ?: return@forEach
            playerFor(track.id).updateGains(clip)
        }
    }

    fun pause() {
        playbackGuard.stop()
        nativeEngine?.pause()
        players.values.forEach { it.pause() }
        livePlayers.values.forEach { it.pause() }
        retiring.values.forEach { it.release() }
        retiring.clear()
        handoverSince.clear()
    }

    fun release() {
        playbackGuard.stop()
        nativeEngine?.release()
        players.values.forEach { it.release() }
        players.clear()
        livePlayers.values.forEach { it.release() }
        livePlayers.clear()
        retiring.values.forEach { it.release() }
        retiring.clear()
        handoverSince.clear()
        lastDryMixes.clear()
        liveFailures.clear()
        DryTrackCache.retain(emptySet())
    }

    /**
     * El reproductor del motor en vivo que debe sonar para [track], o `null` si el carril suena por el `MediaPlayer`
     * (sin Reverb, sin mezcla seca utilizable, o con el motor en vivo en fallo reciente). Crea el reproductor la primera vez
     * y lo reemplaza cuando la mezcla seca cambia de archivo.
     */
    private fun livePlayerFor(track: AudioTrack): LiveReverbTrackPlayer? {
        val params = track.liveReverbParams
        if (params == null) {
            lastDryMixes.remove(track.id)
            livePlayers.remove(track.id)?.release()
            return null
        }
        val failedPlayer = livePlayers[track.id]?.takeIf { it.hasFailed }
        if (failedPlayer != null) {
            livePlayers.remove(track.id)?.release()
            liveFailures[track.id] = LiveFailure(failedPlayer.dryFile.path, SystemClock.uptimeMillis() + LIVE_RETRY_AFTER_FAILURE_MS)
        }
        val dry = resolvedDryMix(track)?.takeUnless { isLiveBlocked(track.id, it) }
        if (dry == null) {
            livePlayers.remove(track.id)?.release()
            return null
        }
        val current = livePlayers[track.id]
        if (current != null && current.dryFile == dry.file) return current
        val created = try {
            LiveReverbTrackPlayer(dry.file, params)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo abrir el motor en vivo del carril con Reverb: ${dry.file.name}", t)
            liveFailures[track.id] = LiveFailure(dry.file.path, SystemClock.uptimeMillis() + LIVE_RETRY_AFTER_FAILURE_MS)
            livePlayers.remove(track.id)?.release()
            return null
        }
        livePlayers[track.id] = created
        if (current != null) retire(track.id, current)
        return created
    }

    /** Aparta [old], reemplazado por otro reproductor, hasta que el nuevo suene; si no estaba sonando, se suelta ya. */
    private fun retire(trackId: String, old: LiveReverbTrackPlayer) {
        retiring.remove(trackId)?.release()
        if (old.isProducing) retiring[trackId] = old else old.release()
    }

    /** La mezcla seca que debe sonar ahora ([resolveDryMix]); recuerda la última válida y olvida la descartada. */
    private fun resolvedDryMix(track: AudioTrack): DryTrackMix? {
        val dry = resolveDryMix(
            hasAudibleClips = track.hasAudibleClips,
            ready = DryTrackCache.dryMixFor(context, track),
            lastReady = lastDryMixes[track.id],
            failed = DryTrackCache.hasFailed(track)
        )
        // Solo se recuerda una mezcla que sigue siendo válida: si la regla la descartó, no debe resucitar después.
        if (dry != null) lastDryMixes[track.id] = dry else lastDryMixes.remove(track.id)
        return dry
    }

    /** `true` si el motor en vivo falló hace poco con ESTA mezcla seca (otra mezcla, o pasado el plazo, se intenta de nuevo). */
    private fun isLiveBlocked(trackId: String, dry: DryTrackMix): Boolean {
        val failure = liveFailures[trackId] ?: return false
        if (failure.dryPath == dry.file.path && SystemClock.uptimeMillis() < failure.retryAtMs) return true
        liveFailures.remove(trackId)
        return false
    }

    private fun syncLive(
        player: LiveReverbTrackPlayer,
        params: ReverbParams,
        projectTimeMs: Long,
        audible: Boolean,
        forceSeek: Boolean
    ) {
        player.setParams(params)
        val action = planLiveSync(
            wantsSound = audible,
            expectedFrame = frameAtMs(projectTimeMs, player.sampleRateHz),
            totalFrames = player.totalFrames,
            isRunning = player.isRunning,
            audibleFrame = player.audibleFrame,
            sampleRateHz = player.sampleRateHz,
            forceSeek = forceSeek,
            msSinceReposition = player.msSinceReposition
        )
        when (action) {
            LiveSyncAction.Pause -> player.pause()
            LiveSyncAction.Keep -> Unit
            is LiveSyncAction.StartAt -> player.start(action.frame)
            is LiveSyncAction.SeekTo -> player.seekTo(action.frame)
        }
    }

    private fun playerFor(trackId: String): AudioPreviewPlayer =
        players.getOrPut(trackId) { AudioPreviewPlayer(context) }
}
