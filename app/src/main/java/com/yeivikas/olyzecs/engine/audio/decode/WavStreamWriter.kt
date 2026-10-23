package com.yeivikas.olyzecs.engine.audio.decode

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Escribe un WAV PCM de 16 bits a medida que llega el audio, sin conocer su largo de antemano: reserva la cabecera y la
 * corrige con los tamaños reales al cerrar. Es el equivalente en flujo de `writeWav16`.
 */
internal class WavStreamWriter(file: File, private val sampleRateHz: Int, private val channels: Int) : Closeable {
    private val output = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var closed = false
    private val block = ByteBuffer.allocate(BLOCK_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)

    init {
        output.setLength(0L)
        output.write(wavHeaderBytes(0L, sampleRateHz, channels))
    }

    /** Frames escritos hasta ahora. */
    val framesWritten: Long get() = dataBytes / (channels * 2L)

    fun write(samples: ShortArray, count: Int) {
        check(!closed) { "WavStreamWriter ya cerrado" }
        var i = 0
        while (i < count) {
            val n = minOf(BLOCK_SAMPLES, count - i)
            block.clear()
            for (k in 0 until n) block.putShort(samples[i + k])
            output.write(block.array(), 0, n * 2)
            i += n
        }
        dataBytes += count.toLong() * 2L
    }

    /** Corrige la cabecera con los tamaños reales y cierra. Idempotente. */
    override fun close() {
        if (closed) return
        closed = true
        try {
            output.seek(0L)
            output.write(wavHeaderBytes(dataBytes, sampleRateHz, channels))
        } finally {
            output.close()
        }
    }

    private companion object {
        const val BLOCK_SAMPLES = 32 * 1024
    }
}
