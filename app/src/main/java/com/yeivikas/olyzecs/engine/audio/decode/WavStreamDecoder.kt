package com.yeivikas.olyzecs.engine.audio.decode

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lector de WAV en streaming: recorre los bloques RIFF, interpreta `fmt ` y entrega el contenido de `data` en bloques de
 * 64 KB a un [PcmSink], sin tener nunca el archivo entero en memoria. Admite PCM de 8/16/24/32 bits, flotante de 32/64 bits,
 * cabecera `WAVE_FORMAT_EXTENSIBLE` y un `data` de largo 0 o 0xFFFFFFFF (hasta el fin del archivo).
 *
 * Es puro (no usa Android): se prueba con un `ByteArrayInputStream`.
 */
internal object WavStreamDecoder {
    /** Tope razonable del bloque `fmt ` (el real mide 16-40 bytes): un valor mayor es una cabecera corrupta. */
    private const val MAX_FMT_BYTES = 4096L
    private const val READ_BLOCK_BYTES = 1 shl 16

    /** `null` si el flujo no es un WAV legible; en ese caso, salvo que sea simplemente otro formato, informa el motivo a [onFailure]. */
    fun decode(input: InputStream, sink: PcmSink, onFailure: DecodeFailureReporter): StreamInfo? {
        val riffHeader = ByteArray(12)
        if (readFully(input, riffHeader) != 12) return null
        if (String(riffHeader, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        if (String(riffHeader, 8, 4, Charsets.US_ASCII) != "WAVE") return null

        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 16
        var audioFormatCode = 1
        val chunkHeader = ByteArray(8)

        while (true) {
            if (readFully(input, chunkHeader) < 8) break // fin del archivo sin encontrar "data"
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = (chunkHeader[4].toLong() and 0xFFL) or
                ((chunkHeader[5].toLong() and 0xFFL) shl 8) or
                ((chunkHeader[6].toLong() and 0xFFL) shl 16) or
                ((chunkHeader[7].toLong() and 0xFFL) shl 24)

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16L || chunkSize > MAX_FMT_BYTES) {
                        onFailure("WAV manual: el bloque 'fmt ' declara un tamaño inválido ($chunkSize bytes)", null)
                        return null
                    }
                    val fmtBytes = ByteArray(chunkSize.toInt())
                    if (readFully(input, fmtBytes) != fmtBytes.size) return null
                    val bb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormatCode = bb.getShort(0).toInt() and 0xFFFF
                    channels = bb.getShort(2).toInt() and 0xFFFF
                    sampleRate = bb.getInt(4)
                    bitsPerSample = bb.getShort(14).toInt() and 0xFFFF
                    // WAVE_FORMAT_EXTENSIBLE: el código real de formato va en los primeros 2 bytes del GUID del subformato.
                    if (audioFormatCode == 0xFFFE && fmtBytes.size >= 26) {
                        audioFormatCode = bb.getShort(24).toInt() and 0xFFFF
                    }
                    if (chunkSize % 2L == 1L) input.skip(1)
                }
                "data" -> return streamData(input, chunkSize, sampleRate, channels, bitsPerSample, audioFormatCode, sink, onFailure)
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
        onFailure(incompleteHeaderMessage(sampleRate, channels), null)
        return null
    }

    private fun streamData(
        input: InputStream,
        chunkSize: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        audioFormatCode: Int,
        sink: PcmSink,
        onFailure: DecodeFailureReporter
    ): StreamInfo? {
        if (sampleRate <= 0 || channels <= 0) {
            onFailure(incompleteHeaderMessage(sampleRate, channels), null)
            return null
        }
        val emitter = PcmEmitter(sink, sampleRate, channels, SampleFormat.ofWav(audioFormatCode == 3, bitsPerSample))
        val untilEof = chunkSize == 0L || chunkSize == 0xFFFFFFFFL
        var remaining = if (untilEof) Long.MAX_VALUE else chunkSize
        var bytesRead = 0L
        val block = ByteArray(READ_BLOCK_BYTES)
        while (remaining > 0L) {
            val n = input.read(block, 0, minOf(block.size.toLong(), remaining).toInt())
            if (n < 0) break
            emitter.write(block, n)
            bytesRead += n
            remaining -= n.toLong()
        }
        if (bytesRead == 0L) {
            onFailure(incompleteHeaderMessage(sampleRate, channels), null)
            return null
        }
        if (emitter.framesEmitted == 0L) return null // menos de un frame completo
        return StreamInfo(sampleRate, channels)
    }

    private fun incompleteHeaderMessage(sampleRate: Int, channels: Int): String =
        "WAV manual: header incompleto o inválido (sampleRate=$sampleRate, channels=$channels, bytesDeAudioLeidos=0) — " +
            "el chunk 'fmt ' o 'data' no se encontró o vino corrupto"
}
