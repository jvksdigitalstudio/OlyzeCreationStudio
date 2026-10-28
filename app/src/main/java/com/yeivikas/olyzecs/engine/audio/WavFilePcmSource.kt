package com.yeivikas.olyzecs.engine.audio

import com.yeivikas.olyzecs.engine.audio.decode.WAV_HEADER_BYTES
import com.yeivikas.olyzecs.engine.audio.fx.PcmSource
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [PcmSource] sobre un WAV PCM 16-bit estéreo en disco, el formato canónico de 44 bytes que escribe [writeWav16]. Lee solo
 * los bloques que se piden (acceso aleatorio): el audio del carril NO vive entero en RAM, y un seek es una lectura, no una
 * decodificación. Un hilo a la vez (el hilo de audio del reproductor); [close] al terminar.
 *
 * @throws IllegalArgumentException si el archivo no es un WAV PCM 16-bit estéreo (cabecera ausente o distinta).
 */
internal class WavFilePcmSource(file: File) : PcmSource, Closeable {
    private val raf = RandomAccessFile(file, "r")
    /** Bytes de trabajo reutilizados entre lecturas (crece una vez hasta el bloque máximo). */
    private var scratch = ByteArray(0)

    /** Lo que declara la cabecera (validada): el largo y la tasa de muestreo. */
    private class WavFormat(val frames: Long, val sampleRateHz: Int)

    private val format: WavFormat = try {
        validatedFormat(file)
    } catch (t: Throwable) {
        raf.close()
        throw t
    }

    override val frames: Long = format.frames

    /** Tasa de muestreo del archivo (Hz): la que debe usar quien lo reproduzca. */
    val sampleRateHz: Int = format.sampleRateHz

    /** Valida la cabecera y devuelve el formato: las muestras por canal que hay tras ella y la tasa. */
    private fun validatedFormat(file: File): WavFormat {
        require(raf.length() >= WAV_HEADER_BYTES) { "Archivo demasiado corto para ser un WAV: ${file.name}" }
        val header = ByteArray(WAV_HEADER_BYTES)
        raf.seek(0)
        raf.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        require(tag(header, 0) == "RIFF" && tag(header, 8) == "WAVE" && tag(header, 12) == "fmt " && tag(header, 36) == "data") {
            "Cabecera WAV inválida: ${file.name}"
        }
        val encoding = buffer.getShort(20).toInt()
        val channels = buffer.getShort(22).toInt()
        val bits = buffer.getShort(34).toInt()
        val rate = buffer.getInt(24)
        require(encoding == 1 && channels == 2 && bits == 16) {
            "Se esperaba PCM 16-bit estéreo y es formato=$encoding, canales=$channels, bits=$bits: ${file.name}"
        }
        require(rate > 0) { "Tasa de muestreo inválida ($rate Hz): ${file.name}" }
        return WavFormat((raf.length() - WAV_HEADER_BYTES) / BYTES_PER_FRAME, rate)
    }

    override fun read(startFrame: Long, out: ShortArray, count: Int): Int {
        if (startFrame < 0L || startFrame >= frames) return 0
        val n = minOf(count.toLong(), frames - startFrame).toInt()
        val bytes = n * BYTES_PER_FRAME
        if (scratch.size < bytes) scratch = ByteArray(bytes)
        raf.seek(WAV_HEADER_BYTES + startFrame * BYTES_PER_FRAME)
        raf.readFully(scratch, 0, bytes)
        ByteBuffer.wrap(scratch, 0, bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out, 0, n * 2)
        return n
    }

    override fun close() = raf.close()

    private fun tag(bytes: ByteArray, offset: Int): String = String(bytes, offset, 4, Charsets.US_ASCII)

    private companion object {
        const val BYTES_PER_FRAME = 4 // 2 canales × 16 bits
    }
}
