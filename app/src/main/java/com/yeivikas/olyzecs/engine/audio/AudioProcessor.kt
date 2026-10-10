package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
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
    private const val TAG_TIMEOUT_US = 10_000L
    private const val AAC_BIT_RATE = 128_000
    private const val ENCODER_INPUT_FRAME_COUNT = 4096 // muestras por canal por buffer de entrada al encoder AAC

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
    @Volatile private var lastFailureReason: String? = null

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
        // `EditorViewModel.setAudioTimelineStart` clampea contra
        // `projectDurationMs` en el momento de arrastrar la pista — pero SÍ
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
     * Reusa exactamente el mismo [decodeToPcm] que el export — incluido el
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
    ): DecodedPcm? {
        val previousUserFacingError = AppLogger.peekLastUserFacingError()
        val decoded = decodeToPcm(context, uri, isCancelled)
        if (decoded == null) AppLogger.restoreLastUserFacingError(previousUserFacingError)
        return decoded
    }

    /**
     * Decodifica [uri] a PCM 16-bit. Devuelve `null` si falla (el motivo queda en
     * [lastFailureReason]) o si [isCancelled] pasa a `true` durante el vaciado del
     * decoder; en ese segundo caso NO se registra ningún fallo: el llamador debe
     * consultar [isCancelled] antes de interpretar el `null` como un error real.
     */
    private fun decodeToPcm(context: Context, uri: Uri, isCancelled: () -> Boolean = { false }): DecodedPcm? {
        // El preview en vivo (AudioPreviewPlayer) usa MediaPlayer, que es mucho
        // más tolerante con WAVs "raros" (headers no estándar, chunks extra,
        // WAVE_FORMAT_EXTENSIBLE, típico de audio exportado desde DAWs/herramientas
        // MIDI). MediaExtractor, que es lo que usa este pipeline de export, es
        // bastante más estricto y puede no encontrar ninguna pista en el mismo
        // archivo que MediaPlayer reproduce sin problema — de ahí que suene en
        // el editor pero desaparezca al exportar. Por eso, para WAV, se parsea
        // el header a mano (RIFF/WAVE) en vez de depender del demuxer del
        // sistema. Se detecta por los primeros bytes del archivo (magic number),
        // no por la extensión del nombre, porque esta última puede mentir.
        if (looksLikeWav(context, uri)) {
            val manual = decodeWavManually(context, uri)
            if (manual != null) return manual
            AppLogger.w(TAG, "El archivo tiene cabecera RIFF/WAVE pero el parser manual no pudo leerlo (motivo: $lastFailureReason); se prueba con MediaExtractor como último recurso.")
        }

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                return fail("MediaExtractor no encontró ninguna pista de audio en el archivo (se detectaron ${extractor.trackCount} pistas en total, ninguna de audio)")
            }
            extractor.selectTrack(trackIndex)

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: return fail("La pista de audio no declara mime type (formato corrupto o no soportado)")
            // Formato REAL de salida del decoder: puede diferir del de la
            // pista (p. ej. HE-AAC con SBR/PS declara la mitad de la tasa o
            // menos canales). Usar el de la pista hacía que el export sonara
            // a otra velocidad/tono. Se corrige al recibir
            // INFO_OUTPUT_FORMAT_CHANGED (ver el loop de abajo).
            var outSampleRate = sampleRate
            var outChannelCount = channelCount
            var outPcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT

            // WAV sin comprimir (PCM crudo) llega con mime "audio/raw". Ese mime no
            // tiene decoder porque no hace falta decodificar nada: las muestras ya
            // están en crudo dentro del archivo. Antes el código intentaba crear un
            // decoder igual para este caso, `createDecoderByType` fallaba, la
            // excepción se comía en el catch de abajo y el audio quedaba afuera del
            // export sin ningún aviso. Para este mime se lee directo del extractor.
            if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                val pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                }
                return readRawPcmDirectly(extractor, sampleRate, channelCount, pcmEncoding)
            }

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val chunks = ArrayList<ShortArray>()
            var totalSamples = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                // Una vuelta ≈ un buffer: granularidad suficiente para cortar a tiempo.
                if (isCancelled()) return null
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(TAG_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TAG_TIMEOUT_US)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFormat = decoder.outputFormat
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        outSampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        outChannelCount = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outPcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                } else if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val ordered = outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            val chunk: ShortArray
                            if (outPcmEncoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
                                // Algunos decoders entregan float: se baja a 16-bit.
                                val floatBuffer = ordered.asFloatBuffer()
                                chunk = ShortArray(floatBuffer.remaining())
                                for (i in chunk.indices) {
                                    val f = floatBuffer.get().coerceIn(-1f, 1f)
                                    chunk[i] = (f * Short.MAX_VALUE).toInt().toShort()
                                }
                            } else {
                                val shortBuffer = ordered.asShortBuffer()
                                chunk = ShortArray(shortBuffer.remaining())
                                shortBuffer.get(chunk)
                            }
                            chunks.add(chunk)
                            totalSamples += chunk.size
                        }
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
                // INFO_TRY_AGAIN_LATER (con o sin entrada pendiente): se vuelve a
                // consultar. El vaciado termina SOLO con el EOS del decoder (ver
                // "CONTRATO DE ESPERA"); la cancelación se evalúa al inicio de la vuelta.
            }

            if (totalSamples == 0) return null
            val merged = ShortArray(totalSamples)
            var offset = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, merged, offset, chunk.size)
                offset += chunk.size
            }
            // GAPLESS: los formatos con pérdida (MP3, AAC) llevan un silencio
            // de relleno del codificador al principio y al final. El
            // extractor lo informa como "encoder-delay"/"encoder-padding"
            // (KEY_ENCODER_DELAY/KEY_ENCODER_PADDING en la API 30+: "frames a
            // recortar") pero MediaCodec NO lo recorta al decodificar. El
            // preview (MediaPlayer) sí lo recorta, así que sin esto la
            // exportación quedaba desfasada ~25-50 ms (y con silencio de más
            // al inicio) respecto de lo que se oye al editar.
            val delayFrames = readOptionalIntKey(format, "encoder-delay")
            val paddingFrames = readOptionalIntKey(format, "encoder-padding")
            val trimmed = trimGapless(merged, outChannelCount, delayFrames, paddingFrames)
            DecodedPcm(trimmed, outSampleRate, outChannelCount)
        } catch (e: OutOfMemoryError) {
            // Es un Error (no una Exception): sin este catch un archivo muy
            // largo tumbaba la app en vez de reportar un fallo de audio.
            fail("Sin memoria para decodificar el audio (archivo demasiado largo para este dispositivo)")
        } catch (e: Exception) {
            fail("Error decodificando audio con MediaExtractor/MediaCodec: ${e.javaClass.simpleName}: ${e.message}", e)
        } finally {
            try { decoder?.stop() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo detener el decoder de audio en la limpieza", e) }
            try { decoder?.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el decoder de audio en la limpieza", e) }
            try { extractor.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el MediaExtractor en la limpieza", e) }
        }
    }

    /** Lee una clave entera opcional del [MediaFormat]; 0 si falta o no es un entero (nunca lanza). */
    private fun readOptionalIntKey(format: MediaFormat, key: String): Int =
        try {
            if (format.containsKey(key)) format.getInteger(key) else 0
        } catch (e: Exception) {
            AppLogger.w(TAG, "La clave '$key' del formato de audio no es un entero válido; se ignora", e)
            0
        }

    /**
     * Recorta [delayFrames] frames del INICIO y [paddingFrames] del FINAL de
     * [samples] (relleno del codificador MP3/AAC). Valores negativos se
     * ignoran; si lo pedido no deja al menos 1 frame de audio, se devuelve
     * [samples] sin tocar (un metadato corrupto jamás debe vaciar el audio).
     * Pura (sin Android) para poder testearla.
     */
    internal fun trimGapless(samples: ShortArray, channels: Int, delayFrames: Int, paddingFrames: Int): ShortArray {
        val ch = channels.coerceAtLeast(1)
        val frames = samples.size / ch
        val head = delayFrames.coerceAtLeast(0)
        val tail = paddingFrames.coerceAtLeast(0)
        if ((head == 0 && tail == 0) || head.toLong() + tail >= frames) return samples
        return samples.copyOfRange(head * ch, (frames - tail) * ch)
    }

    /**
     * Lee las muestras de una pista "audio/raw" directo del [MediaExtractor],
     * sin pasar por un `MediaCodec` decoder (no existe uno para este mime).
     * Convierte a 16-bit con signo, sea cual sea la codificación PCM de origen,
     * porque el resto del pipeline (recorte, loop, fades, encoder AAC) trabaja
     * siempre sobre `ShortArray` de 16-bit.
     */
    private fun readRawPcmDirectly(
        extractor: MediaExtractor,
        sampleRate: Int,
        channelCount: Int,
        pcmEncoding: Int
    ): DecodedPcm? {
        val bufferSize = 1 shl 20 // 1MB por lectura, de sobra para un buffer de WAV
        // OJO: MediaExtractor.readSampleData exige un buffer DIRECTO. Con
        // allocate() normal (heap) la lectura falla silenciosamente (excepción
        // atrapada más arriba) y el audio vuelve a desaparecer del export.
        val readBuffer = ByteBuffer.allocateDirect(bufferSize)
        val rawBytes = java.io.ByteArrayOutputStream()
        val tmp = ByteArray(bufferSize)

        while (true) {
            readBuffer.clear()
            val size = extractor.readSampleData(readBuffer, 0)
            if (size < 0) break
            readBuffer.position(0)
            readBuffer.limit(size)
            readBuffer.get(tmp, 0, size)
            rawBytes.write(tmp, 0, size)
            extractor.advance()
        }

        val bytes = rawBytes.toByteArray()
        if (bytes.isEmpty()) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val samples: ShortArray = when (pcmEncoding) {
            android.media.AudioFormat.ENCODING_PCM_8BIT -> {
                // 8-bit WAV es unsigned (0..255, con 128 como silencio); hay que
                // centrarlo en 0 y escalarlo a rango de 16-bit.
                ShortArray(bytes.size) { i ->
                    val unsigned = bytes[i].toInt() and 0xFF
                    ((unsigned - 128) * 256).toShort()
                }
            }
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                val floatCount = bytes.size / 4
                ShortArray(floatCount) { i ->
                    val f = bb.getFloat(i * 4).coerceIn(-1f, 1f)
                    (f * Short.MAX_VALUE).toInt().toShort()
                }
            }
            else -> {
                // ENCODING_PCM_16BIT (el caso normal de WAV) u otro valor no
                // reconocido: se asume 16-bit con signo, que es lo más común.
                val shortCount = bytes.size / 2
                val out = ShortArray(shortCount)
                bb.asShortBuffer().get(out)
                out
            }
        }

        if (samples.isEmpty()) return null
        return DecodedPcm(samples, sampleRate, channelCount.coerceAtLeast(1))
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

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    /** Tope razonable del bloque `fmt ` de un WAV (el real mide 16-40 bytes): un valor mayor es una cabecera corrupta. */
    private const val MAX_WAV_FMT_BYTES = 4096L

    /**
     * Parser manual de WAV: recorre los chunks RIFF a mano (fmt , data, y
     * cualquier otro que se salta por tamaño) leyendo directo del
     * InputStream del content resolver. No depende de MediaExtractor, así
     * que no le importa si el header tiene chunks extra, orden no estándar,
     * o viene en WAVE_FORMAT_EXTENSIBLE — todo lo que MediaExtractor puede
     * rechazar pero que reproductores como MediaPlayer aceptan sin drama.
     */
    private fun decodeWavManually(context: Context, uri: Uri): DecodedPcm? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                decodeWavStream(input) { reason -> fail(reason) }
            }
        } catch (e: OutOfMemoryError) {
            fail("Sin memoria para leer el WAV (archivo demasiado grande para este dispositivo)")
        } catch (e: Exception) {
            fail("decodeWavManually falló: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    /**
     * Núcleo PURO del parser WAV (sin Android: solo un [java.io.InputStream]),
     * separado de [decodeWavManually] para poder probarlo con JUnit. Los
     * motivos de fallo se reportan por [onFailure].
     *
     * Robustez frente a cabeceras reales "raras" (antes cada una de estas
     * podía fallar o incluso tumbar la app):
     *  - tamaños de chunk leídos SIN signo (un `data` de 0xFFFFFFFF, típico de
     *    WAV grabado en streaming, se interpretaba como negativo y se
     *    descartaba el archivo);
     *  - `data` con tamaño 0 o 0xFFFFFFFF = "hasta el final del archivo";
     *  - NUNCA se reserva memoria según el tamaño DECLARADO en la cabecera: se
     *    lee por bloques hasta lo que realmente hay. Antes `ByteArray(chunkSize)`
     *    con una cabecera corrupta de ~2 GB lanzaba OutOfMemoryError (un
     *    Error, no una Exception: el `catch (e: Exception)` no lo atrapaba y la
     *    app se cerraba);
     *  - si los bytes de audio no completan el último frame, se descarta el
     *    resto para no desalinear los canales.
     */
    internal fun decodeWavStream(input: java.io.InputStream, onFailure: (String) -> Unit): DecodedPcm? {
        val riffHeader = ByteArray(12)
        if (readFully(input, riffHeader) != 12) return null
        if (String(riffHeader, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        if (String(riffHeader, 8, 4, Charsets.US_ASCII) != "WAVE") return null

        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 16
        // WAVE_FORMAT_PCM = 1 (entero con signo), WAVE_FORMAT_IEEE_FLOAT = 3.
        // Es clave para el caso de 32 bits: un WAV de 32-bit puede ser
        // entero O float — son layouts binarios totalmente distintos.
        // Leer los bits de un float como si fueran un entero y desplazarlos
        // produce ruido de alta amplitud (la distorsión/saturación reportada).
        var audioFormatCode = 1
        var dataBytes: ByteArray? = null
        val chunkHeader = ByteArray(8)

        // Recorre chunks hasta encontrar "data" (asumiendo que "fmt " ya
        // apareció antes, que es el orden que respeta prácticamente
        // cualquier WAV válido, estándar o no).
        while (dataBytes == null) {
            val got = readFully(input, chunkHeader)
            if (got < 8) break // EOF sin encontrar "data"

            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            // Tamaño SIN signo (uint32 little-endian) en un Long.
            val chunkSize = (chunkHeader[4].toLong() and 0xFFL) or
                ((chunkHeader[5].toLong() and 0xFFL) shl 8) or
                ((chunkHeader[6].toLong() and 0xFFL) shl 16) or
                ((chunkHeader[7].toLong() and 0xFFL) shl 24)

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16L || chunkSize > MAX_WAV_FMT_BYTES) {
                        onFailure("WAV manual: el bloque 'fmt ' declara un tamaño inválido ($chunkSize bytes)")
                        return null
                    }
                    val fmtBytes = ByteArray(chunkSize.toInt())
                    if (readFully(input, fmtBytes) != fmtBytes.size) return null
                    val bb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormatCode = bb.getShort(0).toInt() and 0xFFFF
                    channels = bb.getShort(2).toInt() and 0xFFFF
                    sampleRate = bb.getInt(4)
                    bitsPerSample = bb.getShort(14).toInt() and 0xFFFF
                    // WAVE_FORMAT_EXTENSIBLE (0xFFFE): el subformato real
                    // está en los bytes 24-25 del bloque extendido, con el
                    // mismo código que WAVE_FORMAT_PCM/IEEE_FLOAT.
                    if (audioFormatCode == 0xFFFE && fmtBytes.size >= 26) {
                        audioFormatCode = bb.getShort(24).toInt() and 0xFFFF
                    }
                    if (chunkSize % 2L == 1L) input.skip(1)
                }
                "data" -> {
                    // 0 y 0xFFFFFFFF = tamaño desconocido (WAV en streaming): hasta el EOF.
                    val untilEof = chunkSize == 0L || chunkSize == 0xFFFFFFFFL
                    var remaining = if (untilEof) Long.MAX_VALUE else chunkSize
                    val collected = java.io.ByteArrayOutputStream()
                    val block = ByteArray(1 shl 16)
                    while (remaining > 0L) {
                        val n = input.read(block, 0, minOf(block.size.toLong(), remaining).toInt())
                        if (n < 0) break
                        collected.write(block, 0, n)
                        remaining -= n.toLong()
                    }
                    // Si el archivo viene truncado o el tamaño declarado no coincide
                    // con los bytes reales, se usa lo que efectivamente se leyó.
                    dataBytes = collected.toByteArray()
                }
                else -> {
                    var toSkip = chunkSize
                    while (toSkip > 0L) {
                        val skipped = input.skip(toSkip)
                        if (skipped <= 0L) break
                        toSkip -= skipped
                    }
                    if (chunkSize % 2L == 1L) input.skip(1)
                }
            }
        }

        val data = dataBytes
        if (data == null || data.isEmpty() || sampleRate <= 0 || channels <= 0) {
            onFailure("WAV manual: header incompleto o inválido (sampleRate=$sampleRate, channels=$channels, bytesDeAudioLeidos=${data?.size ?: 0}) — el chunk 'fmt ' o 'data' no se encontró o vino corrupto")
            return null
        }

        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val isFloat = audioFormatCode == 3
        val decodedSamples: ShortArray = when {
            isFloat && bitsPerSample == 32 -> {
                val count = data.size / 4
                ShortArray(count) { i ->
                    val f = bb.getFloat(i * 4).coerceIn(-1f, 1f)
                    (f * Short.MAX_VALUE).toInt().toShort()
                }
            }
            isFloat && bitsPerSample == 64 -> {
                val count = data.size / 8
                ShortArray(count) { i ->
                    val d = bb.getDouble(i * 8).coerceIn(-1.0, 1.0)
                    (d * Short.MAX_VALUE).toInt().toShort()
                }
            }
            bitsPerSample == 8 -> ShortArray(data.size) { i ->
                val unsigned = data[i].toInt() and 0xFF
                ((unsigned - 128) * 256).toShort()
            }
            bitsPerSample == 24 -> {
                val count = data.size / 3
                ShortArray(count) { i ->
                    val base = i * 3
                    val sample24 = (data[base].toInt() and 0xFF) or
                        ((data[base + 1].toInt() and 0xFF) shl 8) or
                        (data[base + 2].toInt() shl 16) // con signo, extiende el bit 23
                    (sample24 shr 8).toShort() // baja a 16-bit quedándose con los bits más significativos
                }
            }
            bitsPerSample == 32 -> { // entero de 32-bit (no float): sí corresponde este shift
                val count = data.size / 4
                ShortArray(count) { i -> (bb.getInt(i * 4) shr 16).toShort() }
            }
            else -> { // 16-bit, el caso normal
                val count = data.size / 2
                val out = ShortArray(count)
                bb.asShortBuffer().get(out)
                out
            }
        }

        // Frames completos solamente: un resto que no cierre un frame desalinearía los canales.
        val alignedCount = decodedSamples.size - (decodedSamples.size % channels)
        if (alignedCount <= 0) return null
        val samples = if (alignedCount == decodedSamples.size) decodedSamples else decodedSamples.copyOf(alignedCount)
        return DecodedPcm(samples, sampleRate, channels)
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
        val trimStartFrame = ((clip.trimStartMs.coerceAtLeast(0L)) * framesPerMs).toInt()
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
        val baseVolume = volume.coerceIn(0f, 1.5f) * gainMultiplier.coerceAtLeast(0f)
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
        // Los carriles no cambian lo que se oye al exportar: son organización
        // de la playlist. Todos los clips de todos los carriles se mezclan
        // juntos, se solapen o no (el orden de [audioTracks] solo decide el
        // orden de la suma saturada).
        val activeClips = audioTracks.flatMap { it.clips }.filter { !it.muted && it.timelineStartMs < projectDurationMs }
        if (activeClips.isEmpty()) {
            AppLogger.i(TAG, "Ningún clip de audio activo para este proyecto (todos muteados, vacíos o fuera de rango) — se exporta sin audio (esperado).")
            return null
        }
        if (activeClips.size == 1) {
            return buildEncodedTrackForProject(context, activeClips.single(), projectDurationMs, isCancelled, onProgress)
        }
        if (projectDurationMs <= 0L) {
            return fail("Duración de proyecto inválida ($projectDurationMs ms)")
        }

        // MEMORIA: antes se armaba un buffer del largo COMPLETO del proyecto por
        // clip, y se conservaban TODOS a la vez (más sus copias remuestreadas y
        // la mezcla): ~2N+1 buffers de proyecto, que en un proyecto largo con
        // varios clips agotaba la memoria. Ahora: (1) se decodifica y prepara
        // solo la FUENTE de cada clip (del tamaño del archivo, no del proyecto),
        // (2) se fija el formato común, y (3) clip por clip se lleva SU fuente
        // a ese formato, se arma su porción de proyecto y se suma en un único
        // acumulador; el buffer de cada clip se descarta antes de pasar al
        // siguiente. Pico: acumulador + 1 buffer de proyecto + fuentes. El
        // resultado es el mismo (suma saturada en el mismo orden de clips).
        val sources = arrayOfNulls<DecodedPcm>(activeClips.size)
        for ((index, clip) in activeClips.withIndex()) {
            if (isCancelled()) return null
            warnIfClipExceedsProject(clip, projectDurationMs)
            val decoded = decodeToPcm(context, clip.sourceUri, isCancelled)?.let { prepareDecoded(it, clip) }
                ?: return if (isCancelled()) null else fail(lastFailureReason ?: "No se pudo leer el audio de \"${clip.displayName}\" (archivo dañado, formato no soportado o sin permiso de lectura)")
            if (decoded.samples.isEmpty()) {
                return fail("El archivo de audio \"${clip.displayName}\" se leyó pero no contiene muestras (posiblemente vacío)")
            }
            sources[index] = decoded
        }
        if (isCancelled()) return null

        // Formato común de mezcla: el sample rate MÁS ALTO entre todos los
        // clips (subir de calidad al remuestrear, nunca bajarla) y SIEMPRE
        // 2 canales (subir un clip mono a estéreo no pierde nada; nunca
        // hace falta bajar a mono, el destino admite estéreo sin problema).
        val targetRate = sources.maxOf { it?.sampleRateHz ?: 0 }
        if (targetRate <= 0) return fail("Sample rate inválido en las fuentes de audio decodificadas")
        val targetChannels = 2
        var mixed: ShortArray? = null
        for ((index, clip) in activeClips.withIndex()) {
            if (isCancelled()) return null
            val decoded = sources[index]
                ?: return fail("Fuente de audio ausente para \"${clip.displayName}\" (estado interno inconsistente)")
            sources[index] = null // libera la fuente apenas se usa
            val resampled = resampleLinear(decoded.samples, decoded.channelCount, decoded.sampleRateHz, targetRate)
            val stereo = remapChannels(resampled, decoded.channelCount, targetChannels)
            val projectPcm = buildProjectSamples(DecodedPcm(stereo, targetRate, targetChannels), clip, projectDurationMs)
            if (projectPcm.isEmpty()) {
                return fail("No se pudo armar el audio de \"${clip.displayName}\" para la duración del proyecto (trim/loop mal calculado)")
            }
            val acc = mixed
            mixed = if (acc == null) projectPcm else mixInto(acc, projectPcm)
        }
        if (isCancelled()) return null
        if (mixed == null || mixed.isEmpty()) {
            return fail("No se pudo mezclar el audio de los ${activeClips.size} clips del carril")
        }

        val mixedPcm: ShortArray = mixed
        val encoded = encodeToAac(mixedPcm, targetRate, targetChannels, isCancelled, onProgress)
        if (encoded == null) {
            if (isCancelled()) return null
            return fail(lastFailureReason ?: "El encoder AAC del dispositivo no pudo procesar la mezcla de ${activeClips.size} clips (sampleRate=$targetRate Hz, canales=$targetChannels)")
        }
        AppLogger.i(TAG, "Audio codificado OK (mezcla de ${activeClips.size} clips): ${encoded.chunks.size} chunks, sampleRate=$targetRate, channels=$targetChannels.")
        return encoded
    }

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
