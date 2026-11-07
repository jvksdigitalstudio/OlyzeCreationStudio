package com.yeivikas.olyzecs.engine.audio.decode

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
import java.nio.ByteBuffer

/** Espera máxima (µs) al pedir un búfer de entrada/salida a un `MediaCodec`: lo bastante corta para atender la cancelación. */
internal const val CODEC_DEQUEUE_TIMEOUT_US = 10_000L

/**
 * Decodificador de audio del sistema (`MediaExtractor` + `MediaCodec`) en STREAMING: cada bloque que sale del códec se
 * convierte y se entrega a un [PcmSink] al instante, así que ni los trozos ni el archivo completo se acumulan aquí. Cubre
 * los formatos comprimidos (MP3, AAC, OGG, FLAC…) y el PCM crudo que el extractor expone sin códec.
 *
 * No recorta el retardo/relleno del codificador: los informa en [StreamInfo] y quien consume decide ([GaplessWindow]).
 */
internal object MediaStreamDecoder {
    private const val TAG = "MediaStreamDecoder"
    private const val RAW_READ_BYTES = 1 shl 20 // 1 MB por lectura: de sobra para un búfer de WAV

    /** `null` si no hay audio que entregar o falló (en ese caso, con motivo en [onFailure]); o si [isCancelled] devolvió `true`. */
    fun decode(
        context: Context,
        uri: Uri,
        sink: PcmSink,
        isCancelled: () -> Boolean,
        onFailure: DecodeFailureReporter
    ): StreamInfo? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                onFailure(
                    "MediaExtractor no encontró ninguna pista de audio en el archivo " +
                        "(se detectaron ${extractor.trackCount} pistas en total, ninguna de audio)",
                    null
                )
                return null
            }
            extractor.selectTrack(trackIndex)

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime == null) {
                onFailure("La pista de audio no declara mime type (formato corrupto o no soportado)", null)
                return null
            }

            if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    AudioFormat.ENCODING_PCM_16BIT
                }
                return streamRawPcm(extractor, PcmEmitter(sink, sampleRate, channelCount, formatOf(encoding)), sampleRate, channelCount, isCancelled)
            }

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            streamCompressed(extractor, decoder, format, sink, isCancelled)
        } catch (e: OutOfMemoryError) {
            onFailure("Sin memoria para decodificar el audio (archivo demasiado largo para este dispositivo)", e)
            null
        } catch (e: Exception) {
            onFailure("Error decodificando audio con MediaExtractor/MediaCodec: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        } finally {
            try { decoder?.stop() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo detener el decoder de audio en la limpieza", e) }
            try { decoder?.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el decoder de audio en la limpieza", e) }
            try { extractor.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el MediaExtractor en la limpieza", e) }
        }
    }

    /** PCM que el extractor entrega sin códec (típicamente un WAV que el parser manual no pudo leer). */
    private fun streamRawPcm(
        extractor: MediaExtractor,
        emitter: PcmEmitter,
        sampleRate: Int,
        channelCount: Int,
        isCancelled: () -> Boolean
    ): StreamInfo? {
        val readBuffer = ByteBuffer.allocateDirect(RAW_READ_BYTES)
        val bytes = ByteArray(RAW_READ_BYTES)
        while (true) {
            if (isCancelled()) return null
            readBuffer.clear()
            val size = extractor.readSampleData(readBuffer, 0)
            if (size < 0) break
            readBuffer.position(0)
            readBuffer.limit(size)
            readBuffer.get(bytes, 0, size)
            emitter.write(bytes, size)
            extractor.advance()
        }
        return if (emitter.framesEmitted == 0L) null else StreamInfo(sampleRate, channelCount)
    }

    private fun streamCompressed(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        inputFormat: MediaFormat,
        sink: PcmSink,
        isCancelled: () -> Boolean
    ): StreamInfo? {
        val bufferInfo = MediaCodec.BufferInfo()
        var outSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var outChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var outEncoding = AudioFormat.ENCODING_PCM_16BIT
        // El formato de salida real puede diferir del de entrada: el emisor se crea con el primer bloque, ya con el formato definitivo.
        var emitter: PcmEmitter? = null
        var scratch = ByteArray(0)
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (isCancelled()) return null
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(CODEC_DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_DEQUEUE_TIMEOUT_US)
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outFormat = decoder.outputFormat
                if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outSampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outChannelCount = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) outEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else if (outputIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        if (scratch.size < bufferInfo.size) scratch = ByteArray(bufferInfo.size)
                        outputBuffer.get(scratch, 0, bufferInfo.size)
                        val target = emitter ?: PcmEmitter(sink, outSampleRate, outChannelCount, formatOf(outEncoding)).also { emitter = it }
                        target.write(scratch, bufferInfo.size)
                    }
                }
                decoder.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }

        if ((emitter?.framesEmitted ?: 0L) == 0L) return null
        return StreamInfo(
            sampleRateHz = outSampleRate,
            channelCount = outChannelCount,
            encoderDelayFrames = readOptionalIntKey(inputFormat, "encoder-delay"),
            encoderPaddingFrames = readOptionalIntKey(inputFormat, "encoder-padding")
        )
    }

    /** Codificación de Android → formato de muestra. El PCM de 16 bits (y cualquier otro valor) se trata como 16 bits. */
    private fun formatOf(pcmEncoding: Int): SampleFormat = when (pcmEncoding) {
        AudioFormat.ENCODING_PCM_8BIT -> SampleFormat.U8
        AudioFormat.ENCODING_PCM_FLOAT -> SampleFormat.F32
        else -> SampleFormat.S16
    }

    /** Lee una clave entera opcional del [MediaFormat]; 0 si falta o no es un entero (nunca lanza). */
    private fun readOptionalIntKey(format: MediaFormat, key: String): Int =
        try {
            if (format.containsKey(key)) format.getInteger(key) else 0
        } catch (e: Exception) {
            AppLogger.w(TAG, "La clave '$key' del formato de audio no es un entero válido; se ignora", e)
            0
        }
}
