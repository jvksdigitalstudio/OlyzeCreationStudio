package com.yeivikas.olyzecs.engine.audio.decode

/** Cómo un decodificador comunica un fallo: el motivo (para el usuario/log) y la excepción original si la hubo. */
internal typealias DecodeFailureReporter = (reason: String, cause: Throwable?) -> Unit

/**
 * Qué se sabe del audio decodificado cuando termina el flujo. Los frames de retardo y relleno del codificador (MP3/AAC) solo
 * se conocen al final, por eso no viajan en [PcmSink.onFormat]; quien los consume decide cómo recortarlos ([GaplessWindow]).
 */
internal data class StreamInfo(
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoderDelayFrames: Int = 0,
    val encoderPaddingFrames: Int = 0
)

/**
 * Destino de un decodificador en streaming. El audio llega en bloques pequeños, así que ni el decodificador ni el destino
 * necesitan tener el archivo entero en memoria: un destino a disco lo escribe a medida que llega; uno en memoria
 * ([PcmAccumulator]) lo acumula.
 */
internal interface PcmSink {
    /** Se llama UNA vez, antes de la primera muestra. */
    fun onFormat(sampleRateHz: Int, channelCount: Int)

    /**
     * [count] muestras de 16 bits intercaladas por canal: SIEMPRE un número entero de frames. [samples] se reutiliza en la
     * siguiente llamada; quien quiera conservar el contenido debe copiarlo.
     */
    fun onSamples(samples: ShortArray, count: Int)
}

/** Acumula todo el audio en memoria. Es el destino de los consumidores que necesitan el arreglo completo (análisis, exportación). */
internal class PcmAccumulator : PcmSink {
    private val chunks = ArrayList<ShortArray>()
    private var totalSamples = 0

    override fun onFormat(sampleRateHz: Int, channelCount: Int) = Unit

    override fun onSamples(samples: ShortArray, count: Int) {
        chunks.add(samples.copyOf(count))
        totalSamples += count
    }

    val isEmpty: Boolean get() = totalSamples == 0

    /** Une los bloques en un solo arreglo y suelta los bloques (no se retienen dos copias a la vez más de lo imprescindible). */
    fun toShortArray(): ShortArray {
        val merged = ShortArray(totalSamples)
        var offset = 0
        for (i in chunks.indices) {
            val chunk = chunks[i]
            System.arraycopy(chunk, 0, merged, offset, chunk.size)
            offset += chunk.size
        }
        chunks.clear()
        totalSamples = 0
        return merged
    }
}

/**
 * Envuelve un [PcmSink] y recuerda si ya recibió audio. Sirve para encadenar decodificadores de respaldo: si el primero falló
 * DESPUÉS de entregar muestras, el segundo no puede empezar de nuevo sobre el mismo destino (duplicaría el audio ya entregado).
 */
internal class ObservedSink(private val delegate: PcmSink) : PcmSink {
    var samplesReceived: Long = 0
        private set

    override fun onFormat(sampleRateHz: Int, channelCount: Int) = delegate.onFormat(sampleRateHz, channelCount)

    override fun onSamples(samples: ShortArray, count: Int) {
        samplesReceived += count
        delegate.onSamples(samples, count)
    }
}
