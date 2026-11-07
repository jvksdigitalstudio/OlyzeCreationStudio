package com.yeivikas.olyzecs.engine.audio.decode

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Codificaciones de muestra que el pipeline sabe llevar a 16 bits con signo (todo lo demás — recorte, loop, fundidos, encoder — trabaja en 16 bits). */
internal enum class SampleFormat(val bytesPerSample: Int) {
    U8(1), S16(2), S24(3), S32(4), F32(4), F64(8);

    companion object {
        /**
         * Formato de un WAV según su código y su profundidad. Mismo orden de decisión que siempre usó el parser: primero los
         * flotantes de 32/64 bits, luego por profundidad entera; cualquier otra combinación se trata como 16 bits.
         */
        fun ofWav(isFloat: Boolean, bitsPerSample: Int): SampleFormat = when {
            isFloat && bitsPerSample == 32 -> F32
            isFloat && bitsPerSample == 64 -> F64
            bitsPerSample == 8 -> U8
            bitsPerSample == 24 -> S24
            bitsPerSample == 32 -> S32
            else -> S16
        }
    }
}

/** La ÚNICA conversión a 16 bits del proyecto: la usan el WAV, el PCM crudo y la salida de los decodificadores de MediaCodec. */
internal object SampleConverter {
    /**
     * Convierte [byteCount] bytes little-endian de [src] a 16 bits en [dst] y devuelve cuántas muestras escribió. Si [byteCount]
     * no es múltiplo del tamaño de muestra, el resto se ignora. [dst] debe tener sitio para `byteCount / bytesPerSample`.
     */
    fun toInt16(src: ByteArray, byteCount: Int, format: SampleFormat, dst: ShortArray): Int {
        val count = byteCount / format.bytesPerSample
        require(dst.size >= count) { "Destino de ${dst.size} muestras para $count" }
        val bb = ByteBuffer.wrap(src, 0, byteCount).order(ByteOrder.LITTLE_ENDIAN)
        when (format) {
            SampleFormat.F32 -> for (i in 0 until count) {
                dst[i] = (bb.getFloat(i * 4).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            SampleFormat.F64 -> for (i in 0 until count) {
                dst[i] = (bb.getDouble(i * 8).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            SampleFormat.U8 -> for (i in 0 until count) {
                dst[i] = (((src[i].toInt() and 0xFF) - 128) * 256).toShort()
            }
            SampleFormat.S24 -> for (i in 0 until count) {
                val base = i * 3
                val sample24 = (src[base].toInt() and 0xFF) or
                    ((src[base + 1].toInt() and 0xFF) shl 8) or
                    (src[base + 2].toInt() shl 16) // con signo: extiende el bit 23
                dst[i] = (sample24 shr 8).toShort() // baja a 16 bits quedándose con los más significativos
            }
            SampleFormat.S32 -> for (i in 0 until count) {
                dst[i] = (bb.getInt(i * 4) shr 16).toShort()
            }
            SampleFormat.S16 -> for (i in 0 until count) {
                dst[i] = bb.getShort(i * 2)
            }
        }
        return count
    }
}
