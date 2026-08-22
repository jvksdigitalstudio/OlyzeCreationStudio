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
private data class DecodedPcm(
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
        onProgress: ((Float) -> Unit)? = null
    ): EncodedAudioTrack? {
        if (clip.muted) {
            AppLogger.i(TAG, "Audio muteado en el proyecto, se exporta sin audio (esperado).")
            return null
        }
        if (projectDurationMs <= 0L) {
            return fail("Duración de proyecto inválida ($projectDurationMs ms)")
        }
        val decoded = decodeToPcm(context, clip.sourceUri)
            ?: return fail(lastFailureReason ?: "No se pudo leer el audio de \"${clip.displayName}\" (archivo dañado, formato no soportado o sin permiso de lectura)")
        if (decoded.samples.isEmpty()) {
            return fail("El archivo de audio \"${clip.displayName}\" se leyó pero no contiene muestras (posiblemente vacío)")
        }

        val projectPcm = buildProjectSamples(decoded, clip, projectDurationMs)
        if (projectPcm.isEmpty()) {
            return fail("No se pudo armar el audio para la duración del proyecto (trim/loop mal calculado)")
        }

        val encoded = encodeToAac(projectPcm, decoded.sampleRateHz, decoded.channelCount, onProgress)
        if (encoded == null) {
            return fail(lastFailureReason ?: "El encoder AAC del dispositivo no pudo procesar sampleRate=${decoded.sampleRateHz}Hz, canales=${decoded.channelCount}")
        }
        AppLogger.i(TAG, "Audio codificado OK: ${encoded.chunks.size} chunks, sampleRate=${decoded.sampleRateHz}, channels=${decoded.channelCount}.")
        return encoded
    }

    // ============================================================
    // 1. Decode: archivo de audio original -> PCM 16-bit
    // ============================================================

    private fun decodeToPcm(context: Context, uri: Uri): DecodedPcm? {
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
            val mime = format.getString(MediaFormat.KEY_MIME)!!

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
            var emptyRetriesAfterInputDone = 0

            while (!outputDone) {
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
                if (outputIndex >= 0) {
                    emptyRetriesAfterInputDone = 0
                    if (bufferInfo.size > 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val chunk = ShortArray(shortBuffer.remaining())
                            shortBuffer.get(chunk)
                            chunks.add(chunk)
                            totalSamples += chunk.size
                        }
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    // Ya no queda nada para alimentar; si el decoder tampoco entrega
                    // salida tras varios reintentos seguidos, se corta ahí — evita un
                    // loop infinito sin descartar el/los últimos buffers, que suelen
                    // tardar un par de polls en aparecer tras el EOS de entrada.
                    emptyRetriesAfterInputDone++
                    if (emptyRetriesAfterInputDone > 50) outputDone = true
                }
            }

            if (totalSamples == 0) return null
            val merged = ShortArray(totalSamples)
            var offset = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, merged, offset, chunk.size)
                offset += chunk.size
            }
            DecodedPcm(merged, sampleRate, channelCount)
        } catch (e: Exception) {
            fail("Error decodificando audio con MediaExtractor/MediaCodec: ${e.javaClass.simpleName}: ${e.message}", e)
        } finally {
            try { decoder?.stop() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo detener el decoder de audio en la limpieza", e) }
            try { decoder?.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el decoder de audio en la limpieza", e) }
            try { extractor.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el MediaExtractor en la limpieza", e) }
        }
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
                // Antes se asumía siempre entero, y bastantes exportadores de
                // audio (típicamente los que vienen de MIDI/DAW) generan WAV de
                // 32-bit FLOAT: leer los bits de un float como si fueran un
                // entero y desplazarlos produce ruido de alta amplitud — eso es
                // exactamente la distorsión/saturación reportada.
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
                    val chunkSize = (chunkHeader[4].toInt() and 0xFF) or
                        ((chunkHeader[5].toInt() and 0xFF) shl 8) or
                        ((chunkHeader[6].toInt() and 0xFF) shl 16) or
                        ((chunkHeader[7].toInt() and 0xFF) shl 24)
                    if (chunkSize < 0) break // tamaño corrupto/absurdo, corta acá

                    when (chunkId) {
                        "fmt " -> {
                            val fmtBytes = ByteArray(chunkSize)
                            if (readFully(input, fmtBytes) != chunkSize) return null
                            val bb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                            if (fmtBytes.size >= 16) {
                                audioFormatCode = bb.getShort(0).toInt() and 0xFFFF
                                channels = bb.getShort(2).toInt() and 0xFFFF
                                sampleRate = bb.getInt(4)
                                bitsPerSample = bb.getShort(14).toInt() and 0xFFFF
                            }
                            // WAVE_FORMAT_EXTENSIBLE (0xFFFE): el subformato real
                            // está en los bytes 24-25 del bloque extendido, con el
                            // mismo código que WAVE_FORMAT_PCM/IEEE_FLOAT.
                            if (audioFormatCode == 0xFFFE && fmtBytes.size >= 26) {
                                audioFormatCode = bb.getShort(24).toInt() and 0xFFFF
                            }
                            if (chunkSize % 2 == 1) input.skip(1)
                        }
                        "data" -> {
                            val bytes = ByteArray(chunkSize)
                            val readCount = readFully(input, bytes)
                            // Si el archivo viene truncado o el tamaño declarado en el
                            // header no coincide con los bytes reales disponibles, se
                            // usa lo que efectivamente se pudo leer en vez de fallar.
                            dataBytes = if (readCount == chunkSize) bytes else bytes.copyOf(readCount)
                        }
                        else -> {
                            var toSkip = chunkSize.toLong()
                            while (toSkip > 0) {
                                val skipped = input.skip(toSkip)
                                if (skipped <= 0) break
                                toSkip -= skipped
                            }
                            if (chunkSize % 2 == 1) input.skip(1)
                        }
                    }
                }

                val data = dataBytes
                if (data == null || data.isEmpty() || sampleRate <= 0 || channels <= 0) {
                    return fail("WAV manual: header incompleto o inválido (sampleRate=$sampleRate, channels=$channels, bytesDeAudioLeidos=${data?.size ?: 0}) — el chunk 'fmt ' o 'data' no se encontró o vino corrupto")
                }

                val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                val isFloat = audioFormatCode == 3
                val samples: ShortArray = when {
                    isFloat && bitsPerSample == 32 -> {
                        val frameCount = data.size / 4
                        ShortArray(frameCount) { i ->
                            val f = bb.getFloat(i * 4).coerceIn(-1f, 1f)
                            (f * Short.MAX_VALUE).toInt().toShort()
                        }
                    }
                    isFloat && bitsPerSample == 64 -> {
                        val frameCount = data.size / 8
                        ShortArray(frameCount) { i ->
                            val d = bb.getDouble(i * 8).coerceIn(-1.0, 1.0)
                            (d * Short.MAX_VALUE).toInt().toShort()
                        }
                    }
                    bitsPerSample == 8 -> ShortArray(data.size) { i ->
                        val unsigned = data[i].toInt() and 0xFF
                        ((unsigned - 128) * 256).toShort()
                    }
                    bitsPerSample == 24 -> {
                        val frameCount = data.size / 3
                        ShortArray(frameCount) { i ->
                            val base = i * 3
                            val sample24 = (data[base].toInt() and 0xFF) or
                                ((data[base + 1].toInt() and 0xFF) shl 8) or
                                (data[base + 2].toInt() shl 16) // con signo, extiende el bit 23
                            (sample24 shr 8).toShort() // baja a 16-bit quedándose con los bits más significativos
                        }
                    }
                    bitsPerSample == 32 -> { // entero de 32-bit (no float): sí corresponde este shift
                        val frameCount = data.size / 4
                        ShortArray(frameCount) { i -> (bb.getInt(i * 4) shr 16).toShort() }
                    }
                    else -> { // 16-bit, el caso normal
                        val shortCount = data.size / 2
                        val out = ShortArray(shortCount)
                        bb.asShortBuffer().get(out)
                        out
                    }
                }

                if (samples.isEmpty()) return null
                DecodedPcm(samples, sampleRate, channels)
            }
        } catch (e: Exception) {
            fail("decodeWavManually falló: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    // ============================================================
    // 2. Recorte + loop + volumen + fade
    // ============================================================

    private fun buildProjectSamples(decoded: DecodedPcm, clip: AudioClip, projectDurationMs: Long): ShortArray {
        val channels = decoded.channelCount.coerceAtLeast(1)
        val framesPerMs = decoded.sampleRateHz / 1000.0

        val totalFramesInSource = decoded.samples.size / channels
        val trimStartFrame = ((clip.trimStartMs.coerceAtLeast(0L)) * framesPerMs).toInt()
            .coerceIn(0, max(0, totalFramesInSource - 1))

        val targetFrames = (projectDurationMs * framesPerMs).toInt().coerceAtLeast(1)
        val output = ShortArray(targetFrames * channels)

        val availableFramesFromTrim = totalFramesInSource - trimStartFrame
        if (availableFramesFromTrim <= 0) return ShortArray(0)

        var written = 0
        // Primera pasada: arranca en trimStartFrame. Si hace falta más audio del
        // que queda y loop=true, las vueltas siguientes reinician desde el frame
        // 0 del archivo completo (no vuelven a aplicar el trim), que es el
        // comportamiento esperado de un loop de música de fondo.
        var readFrame = trimStartFrame
        while (written < targetFrames) {
            if (readFrame >= totalFramesInSource) {
                if (!clip.loop) break
                readFrame = 0
                if (readFrame >= totalFramesInSource) break // archivo vacío, corte de seguridad
            }
            val srcIndex = readFrame * channels
            val dstIndex = written * channels
            System.arraycopy(decoded.samples, srcIndex, output, dstIndex, channels)
            readFrame++
            written++
        }

        // Si no había ni un solo frame disponible (archivo corrupto/vacío) y no
        // se escribió nada, no tiene sentido seguir con el pipeline de audio.
        if (written == 0) return ShortArray(0)

        applyVolumeAndFades(output, channels, decoded.sampleRateHz, clip, targetFrames)
        return output
    }

    private fun applyVolumeAndFades(
        pcm: ShortArray,
        channels: Int,
        sampleRateHz: Int,
        clip: AudioClip,
        totalFrames: Int
    ) {
        val baseVolume = clip.volume.coerceIn(0f, 1.5f)
        if (baseVolume == 0f) {
            pcm.fill(0)
            return
        }

        val fadeInFrames = ((clip.fadeInMs.coerceAtLeast(0L)) * sampleRateHz / 1000L).toInt()
            .coerceIn(0, totalFrames / 2)
        val fadeOutFrames = ((clip.fadeOutMs.coerceAtLeast(0L)) * sampleRateHz / 1000L).toInt()
            .coerceIn(0, totalFrames / 2)
        val fadeOutStartFrame = totalFrames - fadeOutFrames

        for (frame in 0 until totalFrames) {
            var gain = baseVolume
            if (fadeInFrames > 0 && frame < fadeInFrames) {
                gain *= frame.toFloat() / fadeInFrames
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
    // 3. Encode: PCM procesado -> AAC
    // ============================================================

    private fun encodeToAac(
        pcm: ShortArray,
        sampleRateHz: Int,
        channelCount: Int,
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
            var emptyRetriesAfterInputDone = 0

            while (!outputDone) {
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
                        emptyRetriesAfterInputDone = 0
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
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone -> {
                        emptyRetriesAfterInputDone++
                        if (emptyRetriesAfterInputDone > 50) outputDone = true
                    }
                }
            }

            val finalFormat = outputFormat ?: format
            if (chunks.isEmpty()) fail("El encoder AAC no produjo ningún chunk de salida (sampleRate=$sampleRateHz, channels=$channelCount no soportado por el hardware, o el PCM de entrada estaba vacío)") else EncodedAudioTrack(finalFormat, chunks)
        } catch (e: Exception) {
            fail("Error codificando a AAC: ${e.javaClass.simpleName}: ${e.message} (sampleRate=$sampleRateHz, channels=$channelCount)", e)
        } finally {
            try { encoder?.stop() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo detener el encoder AAC en la limpieza", e) }
            try { encoder?.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el encoder AAC en la limpieza", e) }
        }
    }
}
