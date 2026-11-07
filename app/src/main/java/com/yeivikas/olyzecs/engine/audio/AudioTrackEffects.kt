package com.yeivikas.olyzecs.engine.audio

import com.yeivikas.olyzecs.engine.audio.fx.AudioEffect
import com.yeivikas.olyzecs.engine.audio.fx.ReverbEffect
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import com.yeivikas.olyzecs.engine.audio.fx.isActive

// Operaciones puras sobre la cadena de efectos de un carril (`AudioTrack.effects`). Solo transforman datos: el ViewModel
// decide CUÁNDO aplicarlas (y registra el deshacer); el procesamiento de audio vive en `fx.applyEffectChain`.
// Dependencia en un solo sentido: `engine.audio` conoce a `engine.audio.fx`, nunca al revés.

/** Los efectos de este carril que de verdad cambian la señal, en el orden de la cadena. */
val AudioTrack.activeEffects: List<AudioEffect> get() = effects.filter { it.isActive }

/** `true` si algún efecto de este carril cambia la señal: el carril necesita procesarse (exportación) o reproducirse con efecto (vista previa en vivo). */
val AudioTrack.hasActiveEffects: Boolean get() = effects.any { it.isActive }

/** El Reverb de este carril, o `null` si no tiene (un carril lleva como máximo uno). */
val AudioTrack.reverb: ReverbEffect? get() = effects.firstNotNullOfOrNull { it as? ReverbEffect }

/** Carril con [effect] en lugar del efecto de su mismo id; si no existía, se agrega al final de la cadena. */
fun AudioTrack.withEffect(effect: AudioEffect): AudioTrack =
    copy(effects = if (effects.any { it.id == effect.id }) effects.map { if (it.id == effect.id) effect else it } else effects + effect)

/** Carril sin el efecto [effectId]; si no existía, el mismo carril. */
fun AudioTrack.withoutEffect(effectId: String): AudioTrack = copy(effects = effects.filterNot { it.id == effectId })

/**
 * Parámetros con los que el motor en vivo procesa este carril, o `null` si no tiene Reverb (suena por el camino sin efectos).
 *
 * Un carril con el módulo cargado SIEMPRE se reproduce por el motor en vivo, esté activo o en bypass: así activar/desactivar
 * el efecto es un cambio de parámetros (el `mix` baja a 0 con suavizado, y con `mix = 0` la salida es la entrada bit a bit) y
 * no un cambio de reproductor con su corte. Con el efecto en bypass el audio suena igual que sin él.
 */
internal val AudioTrack.liveReverbParams: ReverbParams?
    get() = reverb?.let { if (it.enabled) it.params else it.params.copy(mix = 0f) }
