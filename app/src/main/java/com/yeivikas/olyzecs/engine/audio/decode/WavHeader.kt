package com.yeivikas.olyzecs.engine.audio.decode

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Tamaño de la cabecera canónica de un WAV PCM. */
internal const val WAV_HEADER_BYTES = 44

/**
 * Cabecera canónica (44 bytes, PCM 16 bits) de un WAV con [dataBytes] bytes de audio. La comparten `writeWav16` (arreglo
 * completo) y [WavStreamWriter] (flujo): un solo lugar define el formato que lee también el motor nativo (`MappedWav`).
 */
internal fun wavHeaderBytes(dataBytes: Long, sampleRateHz: Int, channels: Int): ByteArray =
    ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt((36L + dataBytes).coerceAtMost(0xFFFFFFFFL).toInt())
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)                                   // tamaño del bloque fmt
        putShort(1)                                  // PCM
        putShort(channels.toShort())
        putInt(sampleRateHz)
        putInt(sampleRateHz * channels * 2)          // byte rate
        putShort((channels * 2).toShort())           // block align
        putShort(16)                                 // bits por muestra
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(dataBytes.coerceAtMost(0xFFFFFFFFL).toInt())
    }.array()
