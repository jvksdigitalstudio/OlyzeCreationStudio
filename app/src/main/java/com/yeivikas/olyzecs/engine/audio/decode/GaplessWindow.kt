package com.yeivikas.olyzecs.engine.audio.decode

/** Intervalo de frames `[startFrame, endFrame)`. */
internal data class FrameWindow(val startFrame: Long, val endFrame: Long) {
    val frameCount: Long get() = endFrame - startFrame
}

/**
 * La ÚNICA regla del recorte gapless: los codificadores con pérdida (MP3, AAC) anteponen un retardo y añaden un relleno que
 * no son música; quitarlos hace que un loop no deje un hueco en la costura. Si el recorte se comería todo el audio (archivo
 * diminuto o metadatos incoherentes) NO se recorta nada.
 *
 * Opera sobre frames y no sobre muestras, así que sirve igual para un arreglo en memoria que para un archivo en disco.
 */
internal object GaplessWindow {
    fun of(totalFrames: Long, delayFrames: Int, paddingFrames: Int): FrameWindow {
        val head = delayFrames.coerceAtLeast(0)
        val tail = paddingFrames.coerceAtLeast(0)
        if ((head == 0 && tail == 0) || head.toLong() + tail >= totalFrames) return FrameWindow(0L, totalFrames)
        return FrameWindow(head.toLong(), totalFrames - tail)
    }
}
