package com.yeivikas.olyzecs.engine.audio.fx

import java.util.UUID

/**
 * Un módulo de efecto de audio cargado en un carril. Es el contrato común de la cadena de efectos de [AudioTrack]:
 * cada tipo nuevo (Delay, EQ, compresor…) es un subtipo de esta interfaz con sus propios parámetros, y se enchufa en
 * [applyEffectChain] y en `AudioEffectCodec` sin tocar al resto de efectos.
 */
sealed interface AudioEffect {
    /** Identidad estable de esta instancia dentro del carril (UUID). */
    val id: String

    /** Bypass: con `false` el efecto conserva sus parámetros pero no procesa la señal. */
    val enabled: Boolean
}

/** Reverb de estudio ([StereoReverb]) como módulo de un carril. */
data class ReverbEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val enabled: Boolean = true,
    val params: ReverbParams = ReverbParams.DEFAULT
) : AudioEffect

/** `true` si procesar con este efecto CAMBIA la señal: está activado y sus parámetros no lo dejan neutro (p. ej. Reverb con `mix = 0`). */
val AudioEffect.isActive: Boolean
    get() = enabled && when (this) {
        is ReverbEffect -> params.mix > 0f
    }

/** Segundos que el efecto sigue sonando después de que termina la señal que lo excita (la cola de un reverb); 0 si no tiene. */
val AudioEffect.tailSeconds: Double
    get() = when (this) {
        is ReverbEffect -> params.tailSeconds()
    }

