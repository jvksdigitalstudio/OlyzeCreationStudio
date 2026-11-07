package com.yeivikas.olyzecs.engine.audio.fx

/**
 * Origen de audio PCM 16-bit ESTÉREO intercalado (L,R,L,R…) de acceso aleatorio, del que el motor en tiempo real lee por
 * bloques ([LiveReverbStream]). Abstrae de dónde viene (memoria, archivo en disco) para que el DSP no conozca Android ni E/S
 * y se pruebe con JUnit. Un hilo a la vez: la implementación no tiene que ser segura entre hilos.
 */
internal interface PcmSource {
    /** Cantidad total de muestras por canal. */
    val frames: Long

    /**
     * Copia a [out] (desde su índice 0) hasta [count] muestras por canal a partir de [startFrame] y devuelve cuántas copió:
     * menos que [count] solo al final del origen; 0 si [startFrame] está en o más allá del final.
     */
    fun read(startFrame: Long, out: ShortArray, count: Int): Int
}

/** [PcmSource] sobre un arreglo en memoria ([samples] estéreo intercalado). */
internal class ShortArrayPcmSource(private val samples: ShortArray) : PcmSource {
    init {
        require(samples.size % 2 == 0) { "El PCM debe ser estéreo intercalado (tamaño par): ${samples.size}" }
    }

    override val frames: Long = samples.size / 2L

    override fun read(startFrame: Long, out: ShortArray, count: Int): Int {
        if (startFrame < 0L || startFrame >= frames) return 0
        val n = minOf(count.toLong(), frames - startFrame).toInt()
        System.arraycopy(samples, (startFrame * 2L).toInt(), out, 0, n * 2)
        return n
    }
}
