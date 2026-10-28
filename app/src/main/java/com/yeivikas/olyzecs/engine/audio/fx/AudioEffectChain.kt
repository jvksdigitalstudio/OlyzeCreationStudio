package com.yeivikas.olyzecs.engine.audio.fx

/**
 * Pasa [pcm] (PCM 16-bit ESTÉREO intercalado L,R,L,R…) por los efectos activos de [effects], en orden, y devuelve el
 * resultado en 16 bits (con saturación, igual que la suma de clips). Si ningún efecto cambia la señal devuelve [pcm]
 * tal cual (la misma instancia): un carril sin efectos no paga nada ni cambia un solo bit.
 *
 * El procesamiento interno es en simple precisión flotante: la cadena no recorta entre efecto y efecto, solo al final.
 * La longitud del resultado es la de [pcm]; la cola de un reverb dentro de ese largo cae sobre el silencio que ya
 * tenga el buffer al final de la señal (quien llama decide cuánto silencio de cola reservar).
 *
 * @param sampleRateHz tasa del PCM (los largos y frecuencias de cada efecto se reescalan a ella).
 */
internal fun applyEffectChain(pcm: ShortArray, sampleRateHz: Int, effects: List<AudioEffect>): ShortArray {
    val active = effects.filter { it.isActive }
    if (active.isEmpty()) return pcm
    require(pcm.size % 2 == 0) { "El PCM debe ser estéreo intercalado (tamaño par): ${pcm.size}" }

    val frames = pcm.size / 2
    val left = FloatArray(frames)
    val right = FloatArray(frames)
    for (i in 0 until frames) {
        left[i] = pcm[2 * i] / PCM16_SCALE
        right[i] = pcm[2 * i + 1] / PCM16_SCALE
    }

    for (effect in active) {
        when (effect) {
            is ReverbEffect -> StereoReverb(sampleRateHz, effect.params).process(left, right, left, right, frames)
        }
    }

    val processed = ShortArray(pcm.size)
    for (i in 0 until frames) {
        processed[2 * i] = floatToPcm16(left[i])
        processed[2 * i + 1] = floatToPcm16(right[i])
    }
    return processed
}

private const val PCM16_SCALE = 32768f

/** Muestra flotante (−1..1) a 16 bits: redondea y SATURA fuera de rango en vez de desbordar (un desborde invierte el signo: estruendo). */
internal fun floatToPcm16(sample: Float): Short =
    Math.round(sample * PCM16_SCALE).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
