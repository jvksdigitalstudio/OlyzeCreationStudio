package com.yeivikas.olyzecs.engine.audio.fx

/**
 * Rangos válidos de cada parámetro del Reverb: la ÚNICA fuente de verdad. Los usan [ReverbParams.coerced], la
 * serialización del proyecto y los sliders de la ventana, así que ningún valor fuera de rango puede llegar al DSP.
 */
object ReverbRanges {
    val MIX = 0f..1f
    val DECAY_SECONDS = 0.1f..15f
    val PRE_DELAY_MS = 0f..250f
    val SIZE = 0.1f..1f
    val DAMPING_HZ = 1_000f..20_000f
    val LOW_CUT_HZ = 20f..500f
    val HIGH_CUT_HZ = 2_000f..20_000f
    val DIFFUSION = 0f..1f
    val MODULATION = 0f..1f
    val WIDTH = 0f..1f
    val EARLY_LEVEL = 0f..1f
}

/**
 * Parámetros del Reverb de estudio ([StereoReverb]). Inmutables y en unidades físicas, no en "perillas" abstractas.
 *
 * @property mix balance seco/húmedo de potencia constante: 0 = señal original EXACTA, 1 = solo reverb.
 * @property decaySeconds tiempo de reverberación (RT60: lo que tarda la cola en caer 60 dB).
 * @property preDelayMs silencio entre el sonido directo y el inicio del reverb.
 * @property size tamaño de la sala: escala el espaciado de las reflexiones tempranas y de las líneas de la cola.
 * @property dampingHz frecuencia sobre la cual la cola decae más rápido (el aire y las paredes absorben agudos).
 * @property lowCutHz filtro pasa-altos de la ENTRADA del reverb: evita que los graves enturbien la cola.
 * @property highCutHz filtro pasa-bajos de la SALIDA del reverb.
 * @property diffusion densidad de la cola: baja = ecos separados, alta = nube lisa.
 * @property modulation vibrato lento de las líneas de retardo: rompe el timbre metálico de las colas largas.
 * @property width ancho estéreo del reverb: 0 = mono, 1 = estéreo completo.
 * @property earlyLevel nivel de las reflexiones tempranas (las que delatan el tamaño de la sala).
 */
data class ReverbParams(
    val mix: Float = 0.30f,
    val decaySeconds: Float = 2.0f,
    val preDelayMs: Float = 20f,
    val size: Float = 0.6f,
    val dampingHz: Float = 8_000f,
    val lowCutHz: Float = 90f,
    val highCutHz: Float = 14_000f,
    val diffusion: Float = 0.75f,
    val modulation: Float = 0.35f,
    val width: Float = 1f,
    val earlyLevel: Float = 0.5f
) {
    /** Copia con cada valor dentro de su rango; un NaN (dato corrupto) vuelve al valor de fábrica de ese parámetro. */
    fun coerced(): ReverbParams = ReverbParams(
        mix = mix.within(ReverbRanges.MIX, DEFAULT.mix),
        decaySeconds = decaySeconds.within(ReverbRanges.DECAY_SECONDS, DEFAULT.decaySeconds),
        preDelayMs = preDelayMs.within(ReverbRanges.PRE_DELAY_MS, DEFAULT.preDelayMs),
        size = size.within(ReverbRanges.SIZE, DEFAULT.size),
        dampingHz = dampingHz.within(ReverbRanges.DAMPING_HZ, DEFAULT.dampingHz),
        lowCutHz = lowCutHz.within(ReverbRanges.LOW_CUT_HZ, DEFAULT.lowCutHz),
        highCutHz = highCutHz.within(ReverbRanges.HIGH_CUT_HZ, DEFAULT.highCutHz),
        diffusion = diffusion.within(ReverbRanges.DIFFUSION, DEFAULT.diffusion),
        modulation = modulation.within(ReverbRanges.MODULATION, DEFAULT.modulation),
        width = width.within(ReverbRanges.WIDTH, DEFAULT.width),
        earlyLevel = earlyLevel.within(ReverbRanges.EARLY_LEVEL, DEFAULT.earlyLevel)
    )

    /**
     * Cuánto sigue sonando el reverb después de que termina la señal que lo excita: pre-delay + RT60 + el tramo de
     * reflexiones tempranas. Define cuánto silencio hay que agregar al final de un render para no cortar la cola.
     */
    fun tailSeconds(): Double = preDelayMs / 1000.0 + decaySeconds + EARLY_REFLECTIONS_SPAN_SECONDS

    companion object {
        val DEFAULT = ReverbParams()

        /** Las reflexiones tempranas más tardías caen a ~0.12 s con la sala más grande ([StereoReverb]). */
        private const val EARLY_REFLECTIONS_SPAN_SECONDS = 0.12
    }
}

private fun Float.within(range: ClosedFloatingPointRange<Float>, fallback: Float): Float =
    if (isNaN()) fallback else coerceIn(range)
