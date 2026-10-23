package com.yeivikas.olyzecs.engine.audio.decode

/**
 * Convierte bytes crudos little-endian de CUALQUIER tamaño en bloques de frames completos de 16 bits y los entrega a un
 * [PcmSink]. Es la única lógica de "alineación a frame + conversión + entrega" del proyecto: los lectores (WAV, PCM crudo,
 * MediaCodec) solo le pasan los bytes que reciben, sin preocuparse de dónde caen los cortes.
 *
 * Un frame partido entre dos escrituras se completa con los bytes de la siguiente; un frame incompleto al final se descarta.
 */
internal class PcmEmitter(
    private val sink: PcmSink,
    private val sampleRateHz: Int,
    private val channelCount: Int,
    private val format: SampleFormat
) {
    init {
        require(channelCount > 0) { "Canales inválidos: $channelCount" }
    }

    private val frameBytes = channelCount * format.bytesPerSample
    private val pendingFrame = ByteArray(frameBytes)
    private var pendingBytes = 0
    private var samples = ShortArray(0)
    private var announced = false

    /** Frames completos entregados hasta ahora. */
    var framesEmitted: Long = 0
        private set

    fun write(bytes: ByteArray, length: Int) {
        var offset = 0
        if (pendingBytes > 0) {
            val take = minOf(frameBytes - pendingBytes, length)
            System.arraycopy(bytes, 0, pendingFrame, pendingBytes, take)
            pendingBytes += take
            offset = take
            if (pendingBytes < frameBytes) return
            emit(pendingFrame, frameBytes)
            pendingBytes = 0
        }
        val usable = ((length - offset) / frameBytes) * frameBytes
        if (usable > 0) {
            // Se convierte desde `offset`: si hay desplazamiento se copia al inicio de un búfer propio solo en ese caso.
            if (offset == 0) {
                emit(bytes, usable)
            } else {
                emit(bytes.copyOfRange(offset, offset + usable), usable)
            }
        }
        val leftover = length - offset - usable
        if (leftover > 0) {
            System.arraycopy(bytes, offset + usable, pendingFrame, 0, leftover)
            pendingBytes = leftover
        }
    }

    private fun emit(bytes: ByteArray, byteCount: Int) {
        val needed = byteCount / format.bytesPerSample
        if (samples.size < needed) samples = ShortArray(needed)
        val count = SampleConverter.toInt16(bytes, byteCount, format, samples)
        if (!announced) {
            sink.onFormat(sampleRateHz, channelCount)
            announced = true
        }
        sink.onSamples(samples, count)
        framesEmitted += count / channelCount
    }
}
