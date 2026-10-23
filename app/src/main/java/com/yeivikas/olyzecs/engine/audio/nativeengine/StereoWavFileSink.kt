package com.yeivikas.olyzecs.engine.audio.nativeengine

import com.yeivikas.olyzecs.engine.audio.AudioProcessor
import com.yeivikas.olyzecs.engine.audio.decode.PcmSink
import com.yeivikas.olyzecs.engine.audio.decode.WavStreamWriter
import java.io.Closeable
import java.io.File

/**
 * Destino de un decodificador que escribe el audio a un WAV ESTÉREO de 16 bits en disco a medida que llega: ni el archivo
 * completo ni siquiera uno de sus canales viven en memoria. Pasa cualquier número de canales a estéreo con el mismo criterio
 * que la exportación ([AudioProcessor.remapChannels]); la tasa de muestreo no se toca (de eso se encarga el motor nativo).
 *
 * Cerrarlo es lo que corrige la cabecera del WAV con el largo real: hasta entonces el archivo no es válido.
 */
internal class StereoWavFileSink(private val file: File) : PcmSink, Closeable {
    private var writer: WavStreamWriter? = null
    private var sourceChannels = 0

    /** Frames estéreo escritos hasta ahora. */
    val framesWritten: Long get() = writer?.framesWritten ?: 0L

    override fun onFormat(sampleRateHz: Int, channelCount: Int) {
        sourceChannels = channelCount
        writer = WavStreamWriter(file, sampleRateHz, STEREO)
    }

    override fun onSamples(samples: ShortArray, count: Int) {
        val out = checkNotNull(writer) { "onSamples sin onFormat previo" }
        if (sourceChannels == STEREO) {
            out.write(samples, count)
        } else {
            val stereo = AudioProcessor.remapChannels(samples.copyOf(count), sourceChannels, STEREO)
            out.write(stereo, stereo.size)
        }
    }

    override fun close() {
        writer?.close()
    }

    private companion object {
        const val STEREO = 2
    }
}
