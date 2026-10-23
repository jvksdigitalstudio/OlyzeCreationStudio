package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.audio.decode.CODEC_DEQUEUE_TIMEOUT_US
import com.yeivikas.olyzecs.engine.audio.decode.GaplessWindow
import com.yeivikas.olyzecs.engine.audio.decode.MediaStreamDecoder
import com.yeivikas.olyzecs.engine.audio.decode.ObservedSink
import com.yeivikas.olyzecs.engine.audio.decode.PcmAccumulator
import com.yeivikas.olyzecs.engine.audio.decode.PcmSink
import com.yeivikas.olyzecs.engine.audio.decode.StreamInfo
import com.yeivikas.olyzecs.engine.audio.decode.WavStreamDecoder
import com.yeivikas.olyzecs.engine.audio.decode.readFully
import com.yeivikas.olyzecs.engine.audio.fx.applyEffectChain
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/** PCM 16-bit sin comprimir, ya decodificado, con su sample rate y cantidad de canales originales. */
// `internal` (no `private`) a propósito: ver comentario en
// `AudioProcessor.buildProjectSamples` — necesario para poder construir
// un `DecodedPcm` sintético desde `AudioProcessorFadeTest` sin decodificar
// un archivo real.
internal data class DecodedPcm(
    val samples: ShortArray,
    val sampleRateHz: Int,
    val channelCount: Int
)

/** Un chunk de audio ya codificado en AAC, listo para escribirse tal cual en el MediaMuxer. */
data class EncodedAudioChunk(val data: ByteArray, val info: MediaCodec.BufferInfo)

/** Resultado completo de encodear la pista de audio del proyecto: el formato del track + todos sus chunks en orden. */
class EncodedAudioTrack(val format: MediaFormat, val chunks: List<EncodedAudioChunk>)

/**
 * Procesa el [AudioClip] elegido por el usuario hasta dejarlo listo para
 * muxear junto al video exportado. El pipeline completo (decode → PCM →
 * encode AAC) corre en memoria de una sola vez — válido porque el audio de
 * fondo de un proyecto de Olyze dura, como mucho, unos pocos minutos.
 *
 * ## Decisión de diseño: se conserva el sample rate/canales originales
 * En vez de resamplear todo a una tasa fija (p. ej. 44.1kHz estéreo),
 * este motor mantiene la tasa y cantidad de canales que ya trae el
 * archivo importado. Evita todo el código de resampleo (una fuente común
 * de artefactos de audio si no se hace con cuidado) y AAC-LC soporta de
 * forma nativa el rango típico de tasas de un archivo de música o voz
 * (8kHz–96kHz), así que no hace falta forzar una tasa común.
 */
object AudioProcessor {

    private const val TAG = "AudioProcessor"
    private const val TAG_TIMEOUT_US = CODEC_DEQUEUE_TIMEOUT_US
    private const val AAC_BIT_RATE = 128_000
    private const val ENCODER_INPUT_FRAME_COUNT = 4096 // muestras por canal por buffer de entrada al encoder AAC

    /** La mezcla de carriles es SIEMPRE estéreo: subir un clip mono a estéreo no pierde nada. */
    private const val MIX_CHANNELS = 2

    // CONTRATO DE ESPERA (decode y encode): tras el EOS de entrada, el codec
    // SIEMPRE termina emitiendo BUFFER_FLAG_END_OF_STREAM; ese flag es la única
    // condición de fin del bucle de vaciado. No hay plazo arbitrario: un plazo
    // fijo no distingue un codec lento (equipo modesto, archivo largo) de uno
    // colgado, y al vencer daba por "completo" un resultado truncado — audio
    // recortado en silencio, sin error. La salida de emergencia es la
    // CANCELACIÓN cooperativa (`isCancelled`, consultada en cada vuelta del
    // bucle): el usuario puede abortar con el botón Cancelar de la exportación
    // y el bucle de análisis se corta al cancelarse su corrutina.

    // Variable puramente interna: sirve para que un fallo de un sub-paso
    // (p. ej. "el parser manual de WAV no pudo leer el header") se pueda
    // mencionar dentro del mensaje de un fallo posterior más arriba en la
    // cadena (p. ej. "no se pudo leer el audio... motivo: <lo anterior>").
    // El "último error para mostrarle al usuario" de verdad NO vive acá —
    // vive en un solo lugar de todo el proyecto: [AppLogger.setLastUserFacingError]
    // / [AppLogger.consumeLastUserFacingError]. Este objeto nunca expone su
    // propia copia pública de ese estado.
    //
    // POR HILO (ThreadLocal), no global: la decodificación es síncrona y el motivo se lee en el mismo hilo que lo escribió,
    // pero el análisis de forma de onda, el render de efectos de la vista previa y la exportación corren a la vez en hilos
    // distintos. Con una variable compartida el motivo de un archivo podía aparecer en el error de OTRO.
    private val lastFailureReasonHolder = ThreadLocal<String?>()
    private var lastFailureReason: String?
        get() = lastFailureReasonHolder.get()
        set(value) = lastFailureReasonHolder.set(value)

    private fun fail(reason: String, e: Throwable? = null): Nothing? {
        lastFailureReason = reason
        if (e != null) AppLogger.e(TAG, reason, e) else AppLogger.e(TAG, reason)
        AppLogger.setLastUserFacingError(reason)
        return null
    }

    /** Duración total del archivo de audio, para mostrar en la UI (selector de recorte). */
    fun probeDurationMs(context: Context, uri: Uri): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            AppLogger.w(TAG, "No se pudo obtener la duración del audio: $uri", e)
            0L
        } finally {
            retriever.release()
        }
    }

    /**
     * Pipeline completo: decodifica [clip], arma la porción exacta que
     * necesita el proyecto (recorte, loop, fade, volumen) y la re-encodea
     * a AAC. Devuelve `null` si el audio está muteado o si algo falla —
     * el llamador debe seguir exportando el video igual, sin audio, en
     * ese caso (nunca aborta la exportación entera por un problema de
     * audio).
     */
    fun buildEncodedTrackForProject(
        context: Context,
        clip: AudioClip,
        projectDurationMs: Long,
        // AUDITORÍA — hallazgo cerrado: hasta esta revisión, el botón
        // "Cancelar exportación" (ver VideoExporter/EditorViewModel) se
        // mostraba también durante "Procesando audio…", pero esta función
        // no tenía forma de enterarse de una cancelación — un archivo de
        // audio largo podía dejar el botón visible pero sin ningún efecto
        // real hasta que el audio terminara de codificarse solo. Se
        // consulta acá, antes de arrancar, y de nuevo dentro de
        // `encodeToAac` (ver ahí) en cada iteración del loop de
        // codificación — la única fase de esta función con una duración
        // real perceptible.
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null
    ): EncodedAudioTrack? {
        if (clip.muted) {
            AppLogger.i(TAG, "Audio muteado en el proyecto, se exporta sin audio (esperado).")
            return null
        }
        if (isCancelled()) return null
        if (projectDurationMs <= 0L) {
            return fail("Duración de proyecto inválida ($projectDurationMs ms)")
        }
        // BUG REAL corregido acá: si `timelineStartMs` cae en o después del
        // final del proyecto (normalmente imposible porque
        // `EditorViewModel.moveAudioClip` acota contra
        // `projectDurationMs` en el momento de soltar el clip — pero SÍ
        // puede pasar si la duración del proyecto se acorta DESPUÉS de
        // haber posicionado el audio cerca del final), antes esto caía en
        // `buildProjectSamples` devolviendo `ShortArray(0)`, y el chequeo de
        // más abajo (`projectPcm.isEmpty()`) lo reportaba con el mensaje
        // "trim/loop mal calculado" — técnicamente incorrecto (el trim y el
        // loop no tienen nada que ver acá) y alarmante para algo que en
        // realidad es un estado válido: el clip, tal como está posicionado
        // hoy, simplemente no llega a sonar nada dentro de este proyecto.
        // Mismo tratamiento silencioso que `clip.muted` arriba — no es un
        // error, es una configuración legítima.
        if (clip.timelineStartMs >= projectDurationMs) {
            AppLogger.i(
                TAG,
                "El audio empieza en ${clip.timelineStartMs} ms, después del final del proyecto " +
                    "($projectDurationMs ms) — se exporta sin audio (posición válida, no es un error)."
            )
            return null
        }
        warnIfClipExceedsProject(clip, projectDurationMs)
        val decoded = decodeToPcm(context, clip.sourceUri, isCancelled)?.let { prepareDecoded(it, clip) }
            ?: return if (isCancelled()) null else fail(lastFailureReason ?: "No se pudo leer el audio de \"${clip.displayName}\" (archivo dañado, formato no soportado o sin permiso de lectura)")
        if (decoded.samples.isEmpty()) {
            return fail("El archivo de audio \"${clip.displayName}\" se leyó pero no contiene muestras (posiblemente vacío)")
        }
        if (isCancelled()) return null

        val projectPcm = buildProjectSamples(decoded, clip, projectDurationMs)
        if (projectPcm.isEmpty()) {
            return fail("No se pudo armar el audio para la duración del proyecto (trim/loop mal calculado)")
        }

        lastFailureReason = null // el motivo de un paso anterior (p. ej. un WAV que solo el demuxer pudo leer) no explica un fallo del encoder
        val encoded = encodeToAac(projectPcm, decoded.sampleRateHz, decoded.channelCount, isCancelled, onProgress)
        if (encoded == null) {
            // Si el `null` es porque `isCancelled()` se activó a mitad de
            // `encodeToAac`, NO es una falla real del encoder — no
            // corresponde `fail(...)` acá (eso pisaría `lastFailureReason`
            // con un mensaje de error engañoso para algo que el usuario
            // mismo pidió). `VideoExporter` ya distingue este caso
            // consultando `isCancelled()` de nuevo apenas esta función
            // retorna, así que alcanza con devolver `null` en silencio.
            if (isCancelled()) return null
            return fail(lastFailureReason ?: "El encoder AAC del dispositivo no pudo procesar sampleRate=${decoded.sampleRateHz}Hz, canales=${decoded.channelCount}")
        }
        AppLogger.i(TAG, "Audio codificado OK: ${encoded.chunks.size} chunks, sampleRate=${decoded.sampleRateHz}, channels=${decoded.channelCount}.")
        return encoded
    }

    /**
     * El export construye el audio SOLO hasta [projectDurationMs]; lo que un
     * clip tenga más allá se descarta. El editor amplía el timeline para que
     * eso no ocurra (ver `EditorViewModel.ensureTimelineCoversAudio`), pero si
     * igualmente pasara (p. ej. techo de duración del proyecto), el recorte no
     * debe ser silencioso: queda en el log técnico con las cifras exactas.
     */
    private fun warnIfClipExceedsProject(clip: AudioClip, projectDurationMs: Long) {
        val clipEndMs = clip.timelineStartMs + clip.clipLengthMs
        if (clipEndMs > projectDurationMs) {
            AppLogger.w(
                TAG,
                "El clip \"${clip.displayName}\" termina en $clipEndMs ms pero el proyecto dura " +
                    "$projectDurationMs ms: el audio se exporta recortado (${clipEndMs - projectDurationMs} ms descartados)."
            )
        }
    }

    // ============================================================
    // 1. Decode: archivo de audio original -> PCM 16-bit
    // ============================================================

    /**
     * Decodifica [uri] a PCM para ANÁLISIS (forma de onda en el timeline).
     * Reusa exactamente el mismo decodificador que el export — incluido el
     * parser WAV manual, que es lo que hace que WAVs "raros" de DAWs/MIDI
     * también se puedan dibujar — pero, a diferencia de un export, un fallo
     * acá NO es una acción del usuario: [fail] deja su motivo en
     * [AppLogger.setLastUserFacingError], y si quedara ahí un export
     * posterior lo mostraría como propio. Por eso se restaura el estado
     * previo cuando la decodificación falla. El fallo igual queda en el
     * log técnico ([AppLogger.e]).
     */
    internal fun decodeForAnalysis(
        context: Context,
        uri: Uri,
        isCancelled: () -> Boolean = { false }
    ): DecodedPcm? = restoringUserFacingErrorOnFailure { decodeToPcm(context, uri, isCancelled) }

    /**
     * Como [decodeForAnalysis], pero en STREAMING: el audio se entrega a [sink] a medida que se decodifica, sin acumularlo
     * aquí. Es la vía para consumidores que no necesitan (ni pueden permitirse) el arreglo completo en memoria, como la caché
     * de fuentes del motor de audio nativo.
     */
    internal fun streamForAnalysis(
        context: Context,
        uri: Uri,
        sink: PcmSink,
        isCancelled: () -> Boolean = { false }
    ): StreamInfo? = restoringUserFacingErrorOnFailure { decodeStreaming(context, uri, sink, isCancelled) }

    private inline fun <T> restoringUserFacingErrorOnFailure(block: () -> T?): T? {
        val previousUserFacingError = AppLogger.peekLastUserFacingError()
        val result = block()
        if (result == null) AppLogger.restoreLastUserFacingError(previousUserFacingError)
        return result
    }

    /**
     * Decodifica [uri] a PCM 16-bit completo en memoria. Devuelve `null` si falla (el motivo queda en [lastFailureReason]) o
     * si [isCancelled] pasa a `true` durante el vaciado del decoder; en ese segundo caso NO se registra ningún fallo: el
     * llamador debe consultar [isCancelled] antes de interpretar el `null` como un error real.
     */
    private fun decodeToPcm(context: Context, uri: Uri, isCancelled: () -> Boolean = { false }): DecodedPcm? {
        val accumulator = PcmAccumulator()
        val info = decodeStreaming(context, uri, accumulator, isCancelled) ?: return null
        val trimmed = trimGapless(accumulator.toShortArray(), info.channelCount, info.encoderDelayFrames, info.encoderPaddingFrames)
        return DecodedPcm(trimmed, info.sampleRateHz, info.channelCount)
    }

    /**
     * El decodificador ÚNICO del proyecto: WAV por el parser manual (con MediaExtractor como último recurso) y el resto por
     * `MediaExtractor` + `MediaCodec`, entregando el audio a [sink] en bloques. Los metadatos de retardo/relleno del
     * codificador se informan en el [StreamInfo] devuelto; el recorte lo decide el consumidor ([GaplessWindow]).
     * Mismo contrato de fallo y cancelación que [decodeToPcm].
     */
    internal fun decodeStreaming(context: Context, uri: Uri, sink: PcmSink, isCancelled: () -> Boolean = { false }): StreamInfo? {
        lastFailureReason = null // un motivo de una decodificación anterior no puede explicar ésta
        val observed = ObservedSink(sink)
        if (looksLikeWav(context, uri)) {
            val manual = streamWavManually(context, uri, observed)
            if (manual != null) return manual
            // Si el parser manual ya entregó audio antes de fallar, un segundo intento lo duplicaría: no hay respaldo posible.
            if (observed.samplesReceived > 0L) return null
            AppLogger.w(TAG, "El archivo tiene cabecera RIFF/WAVE pero el parser manual no pudo leerlo (motivo: $lastFailureReason); se prueba con MediaExtractor como último recurso.")
        }
        return MediaStreamDecoder.decode(context, uri, observed, isCancelled, ::reportDecodeFailure)
    }

    /** WAV leído directo del `InputStream` del content resolver, sin `MediaExtractor`: acepta cabeceras con chunks extra, orden no estándar o `WAVE_FORMAT_EXTENSIBLE` que `MediaExtractor` puede rechazar. */
    private fun streamWavManually(context: Context, uri: Uri, sink: PcmSink): StreamInfo? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                WavStreamDecoder.decode(input, sink, ::reportDecodeFailure)
            }
        } catch (e: OutOfMemoryError) {
            fail("Sin memoria para leer el WAV (archivo demasiado grande para este dispositivo)")
        } catch (e: Exception) {
            fail("decodeWavManually falló: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun reportDecodeFailure(reason: String, cause: Throwable?) {
        fail(reason, cause)
    }

    /**
     * Lee un WAV completo de [input] a un [DecodedPcm] (el parser vive en [WavStreamDecoder]; esto lo acumula en memoria).
     * `null` si no es un WAV legible; el motivo, si lo hay, va a [onFailure].
     */
    internal fun decodeWavStream(input: java.io.InputStream, onFailure: (String) -> Unit): DecodedPcm? {
        val accumulator = PcmAccumulator()
        val info = WavStreamDecoder.decode(input, accumulator) { reason, _ -> onFailure(reason) } ?: return null
        return DecodedPcm(accumulator.toShortArray(), info.sampleRateHz, info.channelCount)
    }

    /**
     * Recorta [delayFrames] frames del INICIO y [paddingFrames] del FINAL de [samples] (relleno del codificador MP3/AAC).
     * Valores negativos se ignoran; si lo pedido no deja al menos 1 frame de audio, se devuelve [samples] sin tocar (un
     * metadato corrupto jamás debe vaciar el audio). La regla vive en [GaplessWindow]; aquí solo se aplica a un arreglo.
     */
    internal fun trimGapless(samples: ShortArray, channels: Int, delayFrames: Int, paddingFrames: Int): ShortArray {
        val channelCount = channels.coerceAtLeast(1)
        val totalFrames = (samples.size / channelCount).toLong()
        val window = GaplessWindow.of(totalFrames, delayFrames, paddingFrames)
        if (window.startFrame == 0L && window.endFrame == totalFrames) return samples
        return samples.copyOfRange((window.startFrame * channelCount).toInt(), (window.endFrame * channelCount).toInt())
    }

    /** Sniffea los primeros 12 bytes buscando la firma RIFF....WAVE. Más confiable que mirar la extensión del nombre. */
    private fun looksLikeWav(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(12)
                if (readFully(input, header) != 12) return false
                val riff = String(header, 0, 4, Charsets.US_ASCII)
                val wave = String(header, 8, 4, Charsets.US_ASCII)
                riff == "RIFF" && wave == "WAVE"
            } ?: false
        } catch (e: Exception) {
            AppLogger.w(TAG, "No se pudo inspeccionar la cabecera RIFF/WAVE del archivo: $uri", e)
            false
        }
    }


    // ============================================================
    // 2. Recorte + loop + volumen + fade
    // ============================================================

    // `internal` (no `private`) a propósito: permite testear esta lógica
    // de recorte/loop/fade directamente con JUnit puro (mismo módulo de
    // Gradle, `app/src/test` sí puede ver miembros `internal` de
    // `app/src/main`) sin necesitar decodificar un archivo de audio real
    // — ver AudioProcessorFadeTest. Sigue sin ser parte de ningún
    // contrato público fuera de este módulo.
    internal fun buildProjectSamples(decoded: DecodedPcm, clip: AudioClip, projectDurationMs: Long): ShortArray {
        val channels = decoded.channelCount.coerceAtLeast(1)
        val framesPerMs = decoded.sampleRateHz / 1000.0

        val totalFramesInSource = decoded.samples.size / channels
        val trimStartFrame = (clip.trimStartMs.coerceAtLeast(0L) * framesPerMs).toInt()
            .coerceIn(0, max(0, totalFramesInSource - 1))

        val targetFrames = (projectDurationMs * framesPerMs).toInt().coerceAtLeast(1)
        val output = ShortArray(targetFrames * channels)

        // Punto DEL PROYECTO (no del archivo fuente — eso es trimStartFrame,
        // arriba) donde arranca a sonar este clip — ver
        // `AudioClip.timelineStartMs`, fijado arrastrando la pista de audio
        // en `AudioTrackRow`. Antes de este campo el audio SIEMPRE arrancaba
        // en el frame 0 de salida; ahora, desde el frame 0 hasta justo antes
        // de `startFrame`, queda en silencio real (el `ShortArray` ya nace
        // en 0, no hace falta tocarlo) y la escritura real arranca recién
        // en `startFrame`.
        val startFrame = ((clip.timelineStartMs.coerceAtLeast(0L)) * framesPerMs).toInt()
            .coerceIn(0, targetFrames)

        val availableFramesFromTrim = totalFramesInSource - trimStartFrame
        // BUG REAL corregido acá, en el mismo cambio que introduce
        // `AudioClip.clipLengthMs` (ver KDoc de esa clase): antes de este
        // campo, un clip escribía hasta el FINAL DEL PROYECTO sin ningún
        // otro límite — no había forma de que terminara antes. Con varios
        // clips en el mismo carril (Fase 1), eso significaría que un clip
        // A con `loop=true` se comería, en loop, todo el resto del
        // proyecto, tapando cualquier clip B que arrancara después de él.
        // Ahora el tramo escribible queda acotado también por
        // `clipLengthMs` — el clip nunca escribe más allá de su propio
        // largo declarado, sin importar cuánto quede de proyecto después.
        val clipLengthFrames = (clip.clipLengthMs.coerceAtLeast(0L) * framesPerMs).toInt()
        val writableFrames = minOf(targetFrames - startFrame, clipLengthFrames)
        if (availableFramesFromTrim <= 0 || writableFrames <= 0) return ShortArray(0)

        var written = 0
        // Primera pasada: arranca en trimStartFrame, escribiendo a partir de
        // `startFrame` en el buffer de salida. Si hace falta más audio del
        // que queda y loop=true, las vueltas siguientes reinician desde el
        // frame 0 del archivo completo (no vuelven a aplicar el trim), que es
        // el comportamiento esperado de un loop de música de fondo.
        // Se copia en TRAMOS contiguos (hasta el final del archivo o hasta
        // llenar el clip) en vez de un arraycopy por frame: mismo resultado,
        // pero con millones de frames evita millones de llamadas.
        var readFrame = trimStartFrame
        while (written < writableFrames) {
            if (readFrame >= totalFramesInSource) {
                if (!clip.loop) break
                readFrame = 0
                if (readFrame >= totalFramesInSource) break // archivo vacío, corte de seguridad
            }
            val run = minOf(writableFrames - written, totalFramesInSource - readFrame)
            System.arraycopy(
                decoded.samples, readFrame * channels,
                output, (startFrame + written) * channels,
                run * channels
            )
            readFrame += run
            written += run
        }

        // Si no había ni un solo frame disponible (archivo corrupto/vacío) y no
        // se escribió nada, no tiene sentido seguir con el pipeline de audio.
        if (written == 0) return ShortArray(0)

        applyVolumeAndFades(
            output,
            channels,
            decoded.sampleRateHz,
            volume = clip.volume,
            muted = clip.muted,
            fadeInMs = clip.fadeInMs,
            fadeOutMs = clip.fadeOutMs,
            totalFrames = targetFrames,
            audibleFrames = written,
            audibleStartFrame = startFrame,
            gainMultiplier = clip.normalizeGain
        )
        if (clip.pan != 0f && channels == 2) {
            applyBalancePan(output, startFrame, startFrame + written, clip.pan)
        }
        return output
    }

    /**
     * Deja el PCM decodificado de [clip] listo para armar su porción de
     * proyecto: (1) lo INVIERTE por frames si el clip está en reverse — antes
     * de recorte/loop, igual que "Reverse sample" de FL —, y (2) abre a
     * estéreo un clip MONO con balance != 0, para que el pan tenga dos
     * canales entre los que repartirse. Sin reverse ni pan devuelve
     * [decoded] tal cual: ningún proyecto sin estas opciones pasa por código
     * nuevo.
     */
    internal fun prepareDecoded(decoded: DecodedPcm, clip: AudioClip): DecodedPcm {
        var result = decoded
        if (clip.reversed) {
            result = DecodedPcm(reverseFrames(result.samples, result.channelCount), result.sampleRateHz, result.channelCount)
        }
        if (clip.pan != 0f && result.channelCount == 1) {
            result = DecodedPcm(remapChannels(result.samples, 1, 2), result.sampleRateHz, 2)
        }
        return result
    }

    /**
     * Invierte el orden de los FRAMES (no de las muestras sueltas: en
     * estéreo el par L,R viaja junto, o se intercambiarían los canales).
     * Devuelve un array NUEVO; [samples] no se modifica.
     */
    internal fun reverseFrames(samples: ShortArray, channels: Int): ShortArray {
        val ch = channels.coerceAtLeast(1)
        val frames = samples.size / ch
        val out = ShortArray(samples.size)
        for (f in 0 until frames) {
            System.arraycopy(samples, (frames - 1 - f) * ch, out, f * ch, ch)
        }
        // Resto no alineado a frame (no debería ocurrir): se conserva al final.
        val tail = samples.size - frames * ch
        if (tail > 0) System.arraycopy(samples, frames * ch, out, frames * ch, tail)
        return out
    }

    /**
     * Aplica el balance [pan] (ley de [panGains]) a los frames
     * `[fromFrame, toFrame)` de un buffer ESTÉREO intercalado L,R. Fuera de
     * ese rango (silencio previo/posterior del clip) no toca nada.
     */
    internal fun applyBalancePan(pcm: ShortArray, fromFrame: Int, toFrame: Int, pan: Float) {
        val (gainL, gainR) = panGains(pan)
        if (gainL == 1f && gainR == 1f) return
        val start = fromFrame.coerceAtLeast(0)
        val end = toFrame.coerceAtMost(pcm.size / 2)
        for (frame in start until end) {
            val i = frame * 2
            if (gainL != 1f) pcm[i] = (pcm[i] * gainL).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            if (gainR != 1f) pcm[i + 1] = (pcm[i + 1] * gainR).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    // `internal` + parámetros primitivos (no `clip: AudioClip` directo)
    // a propósito: permite testear la lógica real de fade con JUnit puro,
    // sin necesitar construir un `AudioClip` (que exige un `android.net.Uri`
    // real — no disponible en un unit test JVM sin Robolectric, ver
    // AudioProcessorFadeTest). `buildProjectSamples` sigue siendo el único
    // llamador real en producción, y le sigue pasando exactamente los
    // mismos valores que antes tomaba directo de `clip`.
    internal fun applyVolumeAndFades(
        pcm: ShortArray,
        channels: Int,
        sampleRateHz: Int,
        volume: Float,
        muted: Boolean,
        fadeInMs: Long,
        fadeOutMs: Long,
        totalFrames: Int,
        // AUDITORÍA — BUG REAL encontrado: antes, el fade-out se calculaba
        // siempre relativo a `totalFrames` (la duración COMPLETA del
        // proyecto, incluido el relleno de silencio si el clip no llega a
        // cubrirla y no está en loop). Con un clip más corto que el
        // proyecto y sin loop, `fadeOutStartFrame = totalFrames - fadeOutFrames`
        // podía caer DESPUÉS de [audibleFrames] (donde el audio real ya
        // terminó y sigue puro silencio) — el degradado de "fade out" se
        // aplicaba entero sobre silencio (0 * ganancia = 0, sin ningún
        // cambio audible) y el tramo audible real del clip terminaba de
        // golpe, sin ningún fundido, exactamente el efecto contrario al
        // que el usuario configuró. Ahora el fade-out se calcula relativo
        // al último frame con audio real ([audibleFrames], el `written`
        // de [buildProjectSamples]) — si el clip llena todo el proyecto o
        // está en loop, `audibleFrames == totalFrames` y el comportamiento
        // es idéntico al de antes.
        audibleFrames: Int = totalFrames,
        // Frame de salida donde arranca el tramo audible real —
        // ver `AudioClip.timelineStartMs` / `buildProjectSamples`. Default
        // 0 preserva EXACTAMENTE el comportamiento de siempre para
        // cualquier llamador que no lo pase (ver AudioProcessorFadeTest).
        audibleStartFrame: Int = 0,
        // Ganancia lineal EXTRA (normalización por clip), multiplicada
        // DESPUÉS de acotar [volume] a 0..1.5: la normalización puede
        // superar ese tope a propósito (sube un audio grabado bajo).
        gainMultiplier: Float = 1f
    ) {
        if (muted) {
            pcm.fill(0)
            return
        }
        val baseVolume = clampAudioVolume(volume) * gainMultiplier.coerceAtLeast(0f)
        if (baseVolume == 0f) {
            pcm.fill(0)
            return
        }

        val safeStartFrame = audibleStartFrame.coerceIn(0, totalFrames)
        val effectiveAudibleFrames = audibleFrames.coerceIn(0, totalFrames - safeStartFrame)
        val fadeInFrames = ((fadeInMs.coerceAtLeast(0L)) * sampleRateHz / 1000L).toInt()
            .coerceIn(0, effectiveAudibleFrames / 2)
        val fadeOutFrames = ((fadeOutMs.coerceAtLeast(0L)) * sampleRateHz / 1000L).toInt()
            .coerceIn(0, effectiveAudibleFrames / 2)
        val fadeOutStartFrame = safeStartFrame + effectiveAudibleFrames - fadeOutFrames
        val audibleEndFrame = (safeStartFrame + effectiveAudibleFrames).coerceIn(safeStartFrame, totalFrames)

        // El rango recorrido es SOLO el tramo audible real
        // [safeStartFrame, audibleEndFrame) — los frames antes de
        // `safeStartFrame` (silencio previo al punto de inicio en el
        // timeline) y después de `audibleEndFrame` ya son silencio real
        // (el ShortArray nace en 0) y no necesitan ningún cálculo de
        // ganancia.
        for (frame in safeStartFrame until audibleEndFrame) {
            var gain = baseVolume
            val intoAudible = frame - safeStartFrame
            if (fadeInFrames > 0 && intoAudible < fadeInFrames) {
                gain *= intoAudible.toFloat() / fadeInFrames
            }
            if (fadeOutFrames > 0 && frame >= fadeOutStartFrame) {
                val intoFadeOut = frame - fadeOutStartFrame
                gain *= 1f - (intoFadeOut.toFloat() / fadeOutFrames)
            }
            if (gain == 1f) continue // evita el costo de multiplicar cuando no cambia nada

            val base = frame * channels
            for (c in 0 until channels) {
                val idx = base + c
                val amplified = (pcm[idx] * gain)
                pcm[idx] = amplified.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }

    // ============================================================
    // 1.5. Mezcla de VARIOS clips (Fase 1 — Copiar/Pegar, ver AudioClip.kt)
    // ============================================================

    /**
     * Igual que [buildEncodedTrackForProject] de un solo [AudioClip], pero
     * para TODO el carril ([AudioTrack]) — mezcla real de TODOS los clips
     * que suenan en algún momento del proyecto, sin excepción, se solapen
     * o no (ver KDoc de [AudioTrack] en AudioClip.kt: "al exportar, la
     * mezcla real de todos los clips que se solapan, sin excepción" — acá
     * es donde se cumple esa promesa).
     *
     * Con exactamente UN clip activo, delega tal cual en el pipeline de
     * SIEMPRE ([buildEncodedTrackForProject] de un solo `AudioClip`, sin
     * ningún cambio) — el camino de mezcla de acá abajo (que remuestrea)
     * recién entra en juego con 2 o más clips sonando al mismo tiempo, que
     * es la ÚNICA situación real donde hace falta. Ningún proyecto con un
     * solo clip de audio (la enorme mayoría, sobre todo recién lanzada
     * esta mejora) pasa por código nuevo en absoluto.
     *
     * Por qué hace falta remuestrear para 2+: este motor guarda,
     * deliberadamente, el sample rate y la cantidad de canales NATIVOS de
     * cada archivo (ver el KDoc de esta clase, "se conserva el sample
     * rate/canales originales") — no fuerza una tasa común al decodificar.
     * Sumar sample-por-sample dos señales grabadas a tasas distintas (p.
     * ej. 44100Hz y 22050Hz) sin remuestrear antes daría un resultado sin
     * sentido (una de las dos sonaría a la velocidad/tono equivocado). Acá
     * SÍ hace falta resolver eso — es la única parte de todo el pipeline
     * de audio que remuestrea, y solo cuando de verdad hay que sumar dos
     * señales.
     */
    fun buildEncodedTrackForProject(
        context: Context,
        audioTracks: List<AudioTrack>,
        projectDurationMs: Long,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null
    ): EncodedAudioTrack? {
        // Los carriles no cambian lo que se oye al exportar salvo por su cadena de efectos: todos los clips de todos
        // los carriles se mezclan juntos, se solapen o no (el orden de [audioTracks] solo decide el orden de la suma saturada).
        val activeClips = audioTracks.flatMap { it.clips }.filter { !it.muted && it.timelineStartMs < projectDurationMs }
        if (activeClips.isEmpty()) {
            AppLogger.i(TAG, "Ningún clip de audio activo para este proyecto (todos muteados, vacíos o fuera de rango) — se exporta sin audio (esperado).")
            return null
        }
        // Un solo clip y ningún efecto: el pipeline de SIEMPRE, sin ningún cambio.
        if (activeClips.size == 1 && audioTracks.none { it.hasActiveEffects }) {
            return buildEncodedTrackForProject(context, activeClips.single(), projectDurationMs, isCancelled, onProgress)
        }

        val mix = mixTracks(context, audioTracks, projectDurationMs, isCancelled) ?: return null
        lastFailureReason = null
        val encoded = encodeToAac(mix.samples, mix.sampleRateHz, mix.channelCount, isCancelled, onProgress)
        if (encoded == null) {
            if (isCancelled()) return null
            return fail(lastFailureReason ?: "El encoder AAC del dispositivo no pudo procesar la mezcla de ${activeClips.size} clips (sampleRate=${mix.sampleRateHz} Hz, canales=${mix.channelCount})")
        }
        AppLogger.i(TAG, "Audio codificado OK (mezcla de ${activeClips.size} clips): ${encoded.chunks.size} chunks, sampleRate=${mix.sampleRateHz}, channels=${mix.channelCount}.")
        return encoded
    }

    /** Mezcla estéreo de 16 bits lista para codificar o para escribir a disco. */
    internal class MixedPcm(val samples: ShortArray, val sampleRateHz: Int) {
        val channelCount: Int = MIX_CHANNELS
    }

    /**
     * Mezcla de verdad los clips de [audioTracks] en un buffer de [durationMs] de largo: la ÚNICA implementación de la
     * mezcla por carriles, compartida por la exportación y por la vista previa con efectos (así lo que se oye es lo que
     * se exporta).
     *
     * Los clips de un carril SIN efectos activos se suman directo a la mezcla general (la suma saturada de siempre).
     * Los de un carril CON efectos se suman primero en el "bus" de ese carril; cuando terminan todos sus clips, el bus
     * pasa por la cadena de efectos del carril ([applyEffectChain]) y recién entonces se suma a la mezcla general. Así un
     * efecto actúa sobre la mezcla del carril (clips ya con volumen, balance y fundidos) y su cola cruza de un clip al
     * siguiente. Cada carril usa su propia cadena: ninguno hereda efectos de otro.
     *
     * MEMORIA: se decodifica solo la FUENTE de cada clip (del tamaño del archivo, no del proyecto), se fija el formato
     * común, y clip por clip se lleva su fuente a ese formato, se arma su porción de proyecto y se suma en su acumulador;
     * el buffer del clip se descarta antes de pasar al siguiente. Pico: acumuladores + 1 buffer de proyecto + fuentes.
     *
     * Formato común: el sample rate MÁS ALTO entre todos los clips (subir de calidad al remuestrear, nunca bajarla) y
     * SIEMPRE 2 canales (subir un clip mono a estéreo no pierde nada).
     *
     * @return la mezcla, o `null` si se canceló o falló algo (el motivo queda registrado con [fail]).
     */
    internal fun mixTracks(
        context: Context,
        audioTracks: List<AudioTrack>,
        durationMs: Long,
        isCancelled: () -> Boolean = { false }
    ): MixedPcm? {
        if (durationMs <= 0L) return fail("Duración de mezcla inválida ($durationMs ms)")
        val entries = audioTracks
            .flatMap { track -> track.clips.map { clip -> TrackClip(track.id, clip) } }
            .filter { !it.clip.muted && it.clip.timelineStartMs < durationMs }
        if (entries.isEmpty()) return fail("No hay clips de audio activos para mezclar")
        val chains = audioTracks.associate { it.id to it.activeEffects }

        val sources = arrayOfNulls<DecodedPcm>(entries.size)
        for ((index, entry) in entries.withIndex()) {
            if (isCancelled()) return null
            val clip = entry.clip
            warnIfClipExceedsProject(clip, durationMs)
            val decoded = decodeToPcm(context, clip.sourceUri, isCancelled)?.let { prepareDecoded(it, clip) }
                ?: return if (isCancelled()) null else fail(lastFailureReason ?: "No se pudo leer el audio de \"${clip.displayName}\" (archivo dañado, formato no soportado o sin permiso de lectura)")
            if (decoded.samples.isEmpty()) {
                return fail("El archivo de audio \"${clip.displayName}\" se leyó pero no contiene muestras (posiblemente vacío)")
            }
            sources[index] = decoded
        }
        if (isCancelled()) return null

        val targetRate = sources.maxOf { it?.sampleRateHz ?: 0 }
        if (targetRate <= 0) return fail("Sample rate inválido en las fuentes de audio decodificadas")

        var master: ShortArray? = null
        val buses = LinkedHashMap<String, ShortArray>()
        for ((index, entry) in entries.withIndex()) {
            if (isCancelled()) return null
            val clip = entry.clip
            val decoded = sources[index]
                ?: return fail("Fuente de audio ausente para \"${clip.displayName}\" (estado interno inconsistente)")
            sources[index] = null // libera la fuente apenas se usa
            val resampled = resampleLinear(decoded.samples, decoded.channelCount, decoded.sampleRateHz, targetRate)
            val stereo = remapChannels(resampled, decoded.channelCount, MIX_CHANNELS)
            val projectPcm = buildProjectSamples(DecodedPcm(stereo, targetRate, MIX_CHANNELS), clip, durationMs)
            if (projectPcm.isEmpty()) {
                return fail("No se pudo armar el audio de \"${clip.displayName}\" para la duración del proyecto (trim/loop mal calculado)")
            }
            if (chains.getValue(entry.trackId).isEmpty()) {
                master = accumulate(master, projectPcm)
            } else {
                buses[entry.trackId] = accumulate(buses[entry.trackId], projectPcm)
            }
        }
        if (isCancelled()) return null

        // Cada bus pasa por la cadena de SU carril, en el orden de la lista de carriles (determinista).
        for (track in audioTracks) {
            val bus = buses[track.id] ?: continue
            if (isCancelled()) return null
            master = accumulate(master, applyEffectChain(bus, targetRate, chains.getValue(track.id)))
        }
        if (master == null || master.isEmpty()) {
            return fail("No se pudo mezclar el audio de los ${entries.size} clips")
        }
        return MixedPcm(master, targetRate)
    }

    private class TrackClip(val trackId: String, val clip: AudioClip)

    /** Suma [addition] al acumulador [acc] (o la inicia si todavía no hay): la suma saturada de [mixInto]. */
    private fun accumulate(acc: ShortArray?, addition: ShortArray): ShortArray =
        if (acc == null) addition else mixInto(acc, addition)

    /**
     * Reescala [samples] (en [sourceChannels] canales, intercalados, a
     * [sourceRate] Hz) a [targetRate] Hz por interpolación LINEAL entre
     * muestras vecinas. Alcanza y sobra para MEZCLAR varios clips de audio
     * de fondo (no es el estándar para audio "hero"/voz principal, donde
     * un remuestreo tan simple se notaría más) — igual que antes, el
     * resultado se codifica con el mismo encoder AAC de siempre, así que
     * la calidad del archivo final no cambia salvo en el caso, nuevo, de
     * tener 2 o más clips sonando al mismo tiempo con sample rates
     * distintos entre sí.
     */
    internal fun resampleLinear(samples: ShortArray, sourceChannels: Int, sourceRate: Int, targetRate: Int): ShortArray {
        if (sourceRate == targetRate || samples.isEmpty() || sourceChannels <= 0) return samples
        val sourceFrames = samples.size / sourceChannels
        if (sourceFrames <= 1) return samples
        val targetFrames = ((sourceFrames.toLong() * targetRate) / sourceRate).toInt().coerceAtLeast(0)
        val out = ShortArray(targetFrames * sourceChannels)
        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        for (frame in 0 until targetFrames) {
            val srcPos = frame * ratio
            val srcLow = srcPos.toInt().coerceIn(0, sourceFrames - 1)
            val srcHigh = (srcLow + 1).coerceAtMost(sourceFrames - 1)
            val t = (srcPos - srcLow).toFloat()
            for (c in 0 until sourceChannels) {
                val a = samples[srcLow * sourceChannels + c].toInt()
                val b = samples[srcHigh * sourceChannels + c].toInt()
                out[frame * sourceChannels + c] = (a + (b - a) * t).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
        return out
    }

    /**
     * Lleva [samples] (en [sourceChannels] canales) a [targetChannels] —
     * en este proyecto siempre es MONO→ESTÉREO (duplica el canal, sin
     * pérdida) o ESTÉREO→MONO (promedia ambos canales), porque el audio
     * que se puede importar siempre es 1 o 2 canales.
     */
    internal fun remapChannels(samples: ShortArray, sourceChannels: Int, targetChannels: Int): ShortArray {
        if (sourceChannels == targetChannels || sourceChannels <= 0 || targetChannels <= 0) return samples
        val frames = samples.size / sourceChannels
        val out = ShortArray(frames * targetChannels)
        when {
            sourceChannels == 1 && targetChannels == 2 -> {
                for (f in 0 until frames) {
                    val v = samples[f]
                    out[f * 2] = v
                    out[f * 2 + 1] = v
                }
            }
            sourceChannels == 2 && targetChannels == 1 -> {
                for (f in 0 until frames) {
                    val l = samples[f * 2].toInt()
                    val r = samples[f * 2 + 1].toInt()
                    out[f] = ((l + r) / 2).toShort()
                }
            }
            else -> {
                // No debería pasar en este proyecto (solo 1 o 2 canales de
                // origen) — copia los canales en común en vez de fallar.
                val common = minOf(sourceChannels, targetChannels)
                for (f in 0 until frames) {
                    for (c in 0 until common) out[f * targetChannels + c] = samples[f * sourceChannels + c]
                }
            }
        }
        return out
    }

    /**
     * Suma [buffer] sobre [accumulator] saturando en los límites de un `Short`
     * (en vez de desbordar: si se apilan demasiadas voces fuertes satura, no
     * envuelve al otro signo como ruido digital), sin crear un buffer nuevo por
     * cada clip. Devuelve el acumulador: si [buffer] fuera más largo se crece
     * (los tramos sin aporte quedan en silencio). Los buffers ya deben estar en
     * el mismo sample rate y cantidad de canales (ver [resampleLinear] y
     * [remapChannels]).
     */
    internal fun mixInto(accumulator: ShortArray, buffer: ShortArray): ShortArray {
        val target = if (buffer.size > accumulator.size) accumulator.copyOf(buffer.size) else accumulator
        for (i in buffer.indices) {
            val sum = target[i].toInt() + buffer[i].toInt()
            target[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return target
    }

    // ============================================================
    // 3. Encode: PCM procesado -> AAC
    // ============================================================

    private fun encodeToAac(
        pcm: ShortArray,
        sampleRateHz: Int,
        channelCount: Int,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null
    ): EncodedAudioTrack? {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateHz, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
            // Deja explícito cuánto necesitamos como máximo por buffer de entrada
            // (frames de encoder x canales x 2 bytes/muestra), en vez de confiar en
            // que el tamaño por default del fabricante alcance para lo que se manda.
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, ENCODER_INPUT_FRAME_COUNT * channelCount * 2)
        }

        var encoder: MediaCodec? = null
        return try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val chunks = ArrayList<EncodedAudioChunk>()
            var outputFormat: MediaFormat? = null

            val totalFrames = pcm.size / channelCount
            val framesPerBuffer = ENCODER_INPUT_FRAME_COUNT
            var framesFed = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                // Se consulta una vez por vuelta del loop — misma
                // granularidad que el chequeo de cancelación del loop de
                // frames de video en VideoExporter. `return null` acá
                // ejecuta igual el `finally` de abajo (libera el encoder);
                // no es un "fallo", así que no pasa por `fail(...)` — ver
                // el comentario en `buildEncodedTrackForProject` sobre por
                // qué eso importa.
                if (isCancelled()) return null
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(TAG_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                        if (framesFed >= totalFrames) {
                            encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else if (inputBuffer != null) {
                            inputBuffer.clear()
                            // BUG REAL encontrado en logs de producción: framesPerBuffer
                            // (4096 muestras/canal) asumía que el buffer de entrada del
                            // encoder siempre tenía lugar de sobra. Pero AAC trabaja en
                            // frames fijos de 1024 muestras/canal, y el input buffer que
                            // entrega MediaCodec está dimensionado para eso — con
                            // channels=2 alcanzaba para ~1024 muestras, no 4096, así que
                            // el put() de acá abajo desbordaba el buffer con
                            // BufferOverflowException en TODOS los audios estéreo/48kHz.
                            // Ahora se calcula cuántos frames entran de verdad en el
                            // buffer que tocó esta vez, sea cual sea su tamaño real.
                            val maxFramesThatFit = inputBuffer.remaining() / (channelCount * 2)
                            val framesToWrite = min(min(framesPerBuffer, totalFrames - framesFed), maxFramesThatFit)
                            if (framesToWrite <= 0) {
                                // Buffer de entrada demasiado chico incluso para un solo
                                // frame (no debería pasar nunca, pero por las dudas no se
                                // deja un loop infinito): se descarta este buffer vacío.
                                encoder.queueInputBuffer(inputIndex, 0, 0, 0, 0)
                            } else {
                                val shortsToWrite = framesToWrite * channelCount
                                val startShort = framesFed * channelCount
                                inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                    .put(pcm, startShort, shortsToWrite)

                                val presentationTimeUs = framesFed.toLong() * 1_000_000L / sampleRateHz
                                encoder.queueInputBuffer(inputIndex, 0, shortsToWrite * 2, presentationTimeUs, 0)
                                framesFed += framesToWrite
                                // Antes esta fase no reportaba nada: la barra de export se
                                // quedaba "clavada" en 0% mientras se codificaba todo el
                                // audio (podía tardar bastante con archivos largos), dando
                                // la falsa impresión de que la app estaba colgada.
                                if (totalFrames > 0) onProgress?.invoke(framesFed.toFloat() / totalFrames)
                            }
                        }
                    }
                }

                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TAG_TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = encoder.outputFormat
                    }
                    outputIndex >= 0 -> {
                        if (bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val outputBuffer = encoder.getOutputBuffer(outputIndex)
                            if (outputBuffer != null) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val data = ByteArray(bufferInfo.size)
                                outputBuffer.get(data)
                                val infoCopy = MediaCodec.BufferInfo().apply {
                                    set(0, data.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                                }
                                chunks.add(EncodedAudioChunk(data, infoCopy))
                            }
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                    // INFO_TRY_AGAIN_LATER: se reintenta en la próxima vuelta. El fin
                    // del vaciado es SOLO el EOS del encoder (ver "CONTRATO DE ESPERA").
                }
            }

            val finalFormat = outputFormat ?: format
            if (chunks.isEmpty()) fail("El encoder AAC no produjo ningún chunk de salida (sampleRate=$sampleRateHz, channels=$channelCount no soportado por el hardware, o el PCM de entrada estaba vacío)") else EncodedAudioTrack(finalFormat, chunks)
        } catch (e: OutOfMemoryError) {
            fail("Sin memoria para codificar el audio a AAC (proyecto demasiado largo para este dispositivo)")
        } catch (e: Exception) {
            fail("Error codificando a AAC: ${e.javaClass.simpleName}: ${e.message} (sampleRate=$sampleRateHz, channels=$channelCount)", e)
        } finally {
            try { encoder?.stop() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo detener el encoder AAC en la limpieza", e) }
            try { encoder?.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el encoder AAC en la limpieza", e) }
        }
    }
}
