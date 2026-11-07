package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.SystemClock
import com.yeivikas.olyzecs.debug.AppLogger

private const val TAG = "AudioPreviewPlayer"

/** Tiempo (ms) que se espera antes de reintentar preparar un archivo que ya falló (evita un bucle de MediaPlayer nuevos a ~20 Hz). */
private const val RETRY_AFTER_FAILURE_MS = 3_000L

/** Cambio mínimo de refuerzo (ganancia lineal) que justifica volver a tocar el LoudnessEnhancer (cada llamada es un viaje por binder). */
private const val BOOST_EPSILON = 0.005f

/** Marca de "ningún seek ha terminado todavía" para [AudioPreviewPlayer.seekCompletedAtMs]. */
private const val NO_SEEK_YET = -1L

/**
 * Reproductor de audio para el PREVIEW en vivo del editor (botón Play),
 * separado por completo del pipeline de exportación (`AudioProcessor` +
 * `VideoExporter`), que es el que realmente mezcla el audio en el `.mp4`
 * final con precisión de sample. Este reproductor es una aproximación en
 * tiempo real con un único `MediaPlayer` para MONITOREAR cómo va a sonar
 * mientras se edita — no pretende ser frame-accurate como el export.
 *
 * MODELO DE CONTROL (reemplaza al anterior "solo al arrancar"): el
 * ViewModel llama a [sync] en CADA tick de reproducción (acotado a unos
 * pocos Hz) y ante cualquier cambio de estado. [sync] decide por sí mismo,
 * con el cabezal real, qué clip debe sonar AHORA ([AudioTrack.activeClipAt]):
 *  - entra un clip (el cabezal cruza su `timelineStartMs`): arranca solo;
 *  - se sale de un clip (cruza su fin) o no hay ninguno: se pausa;
 *  - cambia el clip activo (pasa de uno a otro): reposiciona al nuevo, salvo
 *    que el audio ya esté sonando justo donde el nuevo lo necesita (una unión
 *    continua, p. ej. un clip dividido: sin corte);
 *  - el archivo terminó y `loop = true`: reinicia desde el comienzo;
 *  - el reloj del `MediaPlayer` se desvía del cabezal más que
 *    [DRIFT_TOLERANCE_MS]: se corrige con un único seek.
 * Mientras nada de eso ocurra NO hace seek (un seek por tick sí
 * generaría tartamudeo audible).
 */
class AudioPreviewPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var loadedUri: Uri? = null

    /**
     * `true` solo cuando el [mediaPlayer] actual terminó `prepareAsync()`.
     * Antes la preparación era `prepare()` SÍNCRONO en el hilo principal (el
     * tick de reproducción corre ahí): cada cambio de archivo congelaba el
     * cabezal y la UI mientras se abría/decodificaba la cabecera, y un archivo
     * ilegible repetía ese bloqueo en cada tick. Ahora se prepara en segundo
     * plano y [sync] se queda en silencio hasta que esté listo.
     */
    private var prepared = false

    // Archivo que falló al preparar y hasta cuándo (SystemClock.uptimeMillis) no se reintenta.
    private var failedUri: Uri? = null
    private var failedUntilMs = 0L

    // Refuerzo >1.0 (volumen por encima de 100 % y/o normalización): el
    // MediaPlayer solo atenúa, así que lo que excede 1.0 se aplica con un
    // LoudnessEnhancer atado a la sesión del player actual.
    private var enhancer: LoudnessEnhancer? = null

    /** Último refuerzo aplicado al enhancer: evita reenviar el mismo valor en cada tick. */
    private var lastBoost = 1f

    /** Id del clip que este reproductor está sonando ahora mismo (null = ninguno). Detecta el cambio de clip activo entre ticks. */
    private var playingClipId: String? = null

    /** Último instante de proyecto recibido en [sync]; lo usa [updateGains] para ubicar el fundido. */
    private var lastProjectTimeMs = 0L

    // --- Seek asíncrono -------------------------------------------------------------------------------------------
    // `MediaPlayer.seekTo` NO es inmediato: devuelve al instante y el reposicionamiento termina después (avisa en
    // `OnSeekCompleteListener`). Mientras tanto `currentPosition` aún no es la posición nueva ni el audio está ahí. Por eso:
    //  - NO se arranca hasta que el seek termina (arrancar antes hacía sonar un trozo de la posición vieja);
    //  - NO se mide la deriva ni se pide otro seek con uno pendiente (cada seek cancelaba al anterior y reiniciaba la espera:
    //    el audio quedaba entrecortado hasta que uno por fin terminaba);
    //  - tras terminar, se deja un tiempo de asentamiento ([SEEK_SETTLE_MS]) antes de volver a corregir deriva.

    /** `true` desde que se ordena un seek hasta su aviso de fin (o hasta [SEEK_PENDING_TIMEOUT_MS], por si el aviso no llega). */
    private var seekPending = false
    private var seekIssuedAtMs = 0L

    /** Cuándo (uptime) terminó el último seek; `NO_SEEK_YET` si ninguno. */
    private var seekCompletedAtMs = NO_SEEK_YET

    /** `true` si el último [sync] pidió que suene: al terminar un seek pendiente, el aviso arranca el audio. */
    private var wantsPlayback = false

    /** Lo que tardó el último seek en terminar (suavizado, `null` hasta medir uno): se le suma a la posición pedida para que el audio caiga donde está el cabezal. */
    private var seekLatencyMs: Long? = null

    /**
     * Archivo que realmente se reproduce para [clip]: su fuente, o — si está
     * invertido — el WAV invertido de [ReversedAudioCache]. `null` si ese
     * render todavía no terminó (se pide en segundo plano y el siguiente
     * tick de [sync] lo vuelve a intentar): preferible un instante de
     * silencio a oír el audio al derecho.
     */
    private fun effectiveUri(clip: AudioClip): Uri? {
        if (!clip.reversed) return clip.sourceUri
        val file = ReversedAudioCache.peek(context, clip.sourceUri, clip.sourceDurationMs)
        if (file == null) {
            ReversedAudioCache.ensureAsync(context, clip.sourceUri, clip.sourceDurationMs)
            return null
        }
        return Uri.fromFile(file)
    }

    private fun markFailed(uri: Uri) {
        failedUri = uri
        failedUntilMs = SystemClock.uptimeMillis() + RETRY_AFTER_FAILURE_MS
    }

    /**
     * Garantiza que haya un [MediaPlayer] cargado con la fuente de [clip].
     * Devuelve `true` SOLO si ya está preparado y listo para reproducir; si
     * recién se lanzó la preparación asíncrona (o falló) devuelve `false` y
     * el próximo tick de [sync] vuelve a preguntar.
     */
    private fun ensureLoaded(clip: AudioClip): Boolean {
        val uri = effectiveUri(clip) ?: return false
        if (loadedUri == uri && mediaPlayer != null) return prepared
        if (uri == failedUri && SystemClock.uptimeMillis() < failedUntilMs) return false
        release()
        return try {
            val player = MediaPlayer()
            mediaPlayer = player
            loadedUri = uri
            prepared = false
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setOnPreparedListener { p ->
                if (mediaPlayer === p) prepared = true
            }
            player.setOnSeekCompleteListener { p -> onSeekComplete(p) }
            player.setOnErrorListener { p, what, extra ->
                AppLogger.e(TAG, "Error del MediaPlayer del preview de audio (what=$what, extra=$extra): $uri")
                if (mediaPlayer === p) {
                    markFailed(uri)
                    release()
                }
                true
            }
            player.isLooping = false // el loop del proyecto se maneja "a mano" en sync/seekToProjectTime
            player.setDataSource(context, uri)
            player.prepareAsync()
            false
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo preparar el preview de audio", t)
            markFailed(uri)
            release()
            false
        }
    }

    /**
     * Punto único de decisión del preview — ver KDoc de la clase.
     *
     * [track] es el carril completo (no un clip suelto): el clip activo se
     * evalúa acá con [projectTimeMs] fresco. [forceSeek] fuerza el
     * reposicionamiento aunque no haya deriva (arranque explícito de
     * `AudioApi.playFrom`).
     */
    fun sync(track: AudioTrack?, projectTimeMs: Long, isPlaying: Boolean, forceSeek: Boolean = false) {
        lastProjectTimeMs = projectTimeMs
        val clip = if (isPlaying) track?.activeClipAt(projectTimeMs) else null
        // Posición esperada dentro del ARCHIVO; null = el clip está activo en
        // el timeline pero ya no emite nada (archivo agotado y sin loop).
        val expectedPos = clip?.sourcePositionAt(projectTimeMs)
        if (clip == null || expectedPos == null) {
            pause()
            playingClipId = null
            return
        }
        if (!ensureLoaded(clip)) {
            // Sin fuente lista (WAV invertido aún en render, o el archivo
            // todavía se está preparando en segundo plano): se calla lo que
            // estuviera sonando en vez de dejar el clip anterior.
            pause()
            playingClipId = null
            return
        }
        val player = mediaPlayer ?: return
        try {
            applyGains(player, clip, clip.fadeGainAtProjectMs(projectTimeMs))
            wantsPlayback = true
            val playing = player.isPlaying
            val now = SystemClock.uptimeMillis()
            // Con un seek en curso no se decide nada (ver "Seek asíncrono"); solo un seek EXPLÍCITO (arranque, vuelta del
            // bucle) lo sustituye: es una posición nueva a propósito.
            if (!seekInFlight(now) || forceSeek) {
                val clipChanged = playingClipId != clip.id
                val offsetMs = if (playing) kotlin.math.abs(player.currentPosition - expectedPos.toInt()) else 0
                if (previewNeedsSeek(forceSeek, playing, clipChanged, offsetMs, msSinceSeekCompleted(now))) {
                    // SEEK_CLOSEST: el `seekTo(int)` clásico usa SEEK_PREVIOUS_SYNC,
                    // que en MP3/AAC/M4A cae en el fotograma clave ANTERIOR (hasta
                    // cientos de ms antes) y disparaba la corrección de deriva una
                    // y otra vez. minSdk = 26, así que la variante exacta existe.
                    // Si no suena, el arranque lo hace el aviso de fin de seek ([onSeekComplete]).
                    issueSeek(player, expectedPos, compensateLatency = true)
                }
            }
            playingClipId = clip.id
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo sincronizar el preview de audio", t)
            playingClipId = null
        }
    }

    fun pause() {
        wantsPlayback = false
        val player = mediaPlayer ?: return
        if (!prepared) return
        try {
            if (player.isPlaying) player.pause()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo pausar el preview de audio", t)
        }
    }

    /**
     * Reposiciona el audio para que coincida con [projectTimeMs] del
     * timeline, aplicando el mismo criterio de recorte/loop/posición que usa
     * [AudioProcessor.buildProjectSamples] en la exportación real: primero
     * se descuenta `timelineStartMs` (dónde arranca el clip DENTRO del
     * proyecto); la primera vuelta del archivo arranca en `trimStartMs`; si
     * hace falta más audio del que queda y `loop=true`, las vueltas
     * siguientes reinician desde el comienzo del archivo completo.
     */
    fun seekToProjectTime(clip: AudioClip, projectTimeMs: Long) {
        val player = mediaPlayer ?: return
        if (!prepared) return
        if (clip.sourceDurationMs <= 0L) return
        // Sin loop y con el archivo ya agotado: se deja en el último ms.
        val sourcePos = clip.sourcePositionAt(projectTimeMs) ?: (clip.sourceDurationMs - 1)
        try {
            // Solo suena si ya estaba sonando: parado, el seek solo ubica (y no hay latencia que compensar).
            issueSeek(player, sourcePos.coerceAtLeast(0L), compensateLatency = player.isPlaying)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo reposicionar el preview de audio", t)
        }
    }

    /**
     * Ordena el seek a [positionMs] del archivo y lo marca en curso. Con [compensateLatency] la posición pedida se adelanta lo
     * que tardó el último seek en terminar: al terminar, el cabezal ya avanzó esa cantidad y el audio cae donde está.
     */
    private fun issueSeek(player: MediaPlayer, positionMs: Long, compensateLatency: Boolean) {
        val target = positionMs + if (compensateLatency) seekLatencyMs ?: 0L else 0L
        seekPending = true
        seekIssuedAtMs = SystemClock.uptimeMillis()
        player.seekTo(target.coerceAtLeast(0L), MediaPlayer.SEEK_CLOSEST)
    }

    /** `true` si hay un seek ordenado cuyo aviso de fin aún no llegó (y no venció el plazo). */
    private fun seekInFlight(nowMs: Long): Boolean = seekPending && nowMs - seekIssuedAtMs < SEEK_PENDING_TIMEOUT_MS

    private fun msSinceSeekCompleted(nowMs: Long): Long =
        if (seekCompletedAtMs == NO_SEEK_YET) Long.MAX_VALUE else nowMs - seekCompletedAtMs

    /** Fin de un seek: aprende su latencia, abre el asentamiento y, si debe sonar, arranca el audio YA en la posición nueva. */
    private fun onSeekComplete(player: MediaPlayer) {
        if (mediaPlayer !== player || !prepared) return
        val now = SystemClock.uptimeMillis()
        if (seekPending) seekLatencyMs = learnedSeekLatencyMs(seekLatencyMs, now - seekIssuedAtMs)
        seekPending = false
        seekCompletedAtMs = now
        if (!wantsPlayback) return
        try {
            if (!player.isPlaying) player.start()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo arrancar el preview de audio tras el seek", t)
        }
    }

    /**
     * Aplica volumen y balance del [clip] en caliente (sin seek ni reinicio).
     */
    fun updateGains(clip: AudioClip) {
        val player = mediaPlayer ?: return
        if (!prepared) return
        try {
            applyGains(player, clip, clip.fadeGainAtProjectMs(lastProjectTimeMs))
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo actualizar volumen/balance del preview de audio", t)
        }
    }

    // Ganancia total = volumen × normalización × fundido (el fundido sigue la
    // misma rampa lineal que el export, ver [fadeGainAt]). Hasta 1.0 se
    // resuelve con setVolume (más balance L/R con la misma ley que el
    // export, [panGains]); lo que pase de 1.0 va al LoudnessEnhancer.
    private fun applyGains(player: MediaPlayer, clip: AudioClip, fadeGain: Float) {
        val total = (clip.volume * clip.normalizeGain * fadeGain).coerceAtLeast(0f)
        val vol = total.coerceAtMost(1f)
        val (gainL, gainR) = panGains(clip.pan)
        player.setVolume(vol * gainL, vol * gainR)
        applyBoost(player, if (total > 1f) total else 1f)
    }

    private fun applyBoost(player: MediaPlayer, boost: Float) {
        // Mismo valor que ya está aplicado: no se vuelve a llamar al efecto
        // (sync corre ~20 veces por segundo y cada setTargetGain es un binder call).
        if (kotlin.math.abs(boost - lastBoost) < BOOST_EPSILON) return
        try {
            if (boost <= 1f) {
                enhancer?.enabled = false
                lastBoost = 1f
                return
            }
            val fx = enhancer ?: LoudnessEnhancer(player.audioSessionId).also { enhancer = it }
            fx.setTargetGain((2000.0 * kotlin.math.log10(boost.toDouble())).toInt())
            fx.enabled = true
            lastBoost = boost
        } catch (t: Throwable) {
            // Sin el efecto el preview sigue sonando (solo sin el refuerzo):
            // el export no depende de esto.
            AppLogger.w(TAG, "No se pudo aplicar el refuerzo de ganancia al preview", t)
            enhancer = null
            lastBoost = 1f
        }
    }

    fun release() {
        try {
            enhancer?.release()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo liberar el refuerzo de ganancia del preview", t)
        }
        enhancer = null
        lastBoost = 1f
        try {
            mediaPlayer?.release()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No se pudo liberar el MediaPlayer del preview de audio", t)
        }
        mediaPlayer = null
        loadedUri = null
        prepared = false
        playingClipId = null
        seekPending = false
        seekCompletedAtMs = NO_SEEK_YET
        wantsPlayback = false
        seekLatencyMs = null
    }
}
