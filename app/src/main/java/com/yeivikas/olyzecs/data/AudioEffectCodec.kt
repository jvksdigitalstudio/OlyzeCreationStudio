package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.engine.audio.fx.AudioEffect
import com.yeivikas.olyzecs.engine.audio.fx.ReverbEffect
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams

/**
 * Traduce los efectos de un carril entre el modelo de dominio ([AudioEffect]) y su forma guardada ([AudioEffectData]).
 * Es la ÚNICA pieza que conoce los nombres de los campos del archivo del proyecto: ni el DSP ni el ViewModel los ven.
 *
 * Compatibilidad: al leer, un parámetro ausente toma su valor de fábrica y uno fuera de rango se acota ([ReverbParams.coerced]);
 * un efecto de tipo desconocido (guardado por una versión más nueva de la app) se OMITE en vez de romper la carga del proyecto.
 */
internal object AudioEffectCodec {
    const val TYPE_REVERB = "reverb"

    private object ReverbKeys {
        const val MIX = "mix"
        const val DECAY = "decay"
        const val PRE_DELAY = "preDelay"
        const val SIZE = "size"
        const val DAMPING = "damping"
        const val LOW_CUT = "lowCut"
        const val HIGH_CUT = "highCut"
        const val DIFFUSION = "diffusion"
        const val MODULATION = "modulation"
        const val WIDTH = "width"
        const val EARLY = "early"
    }

    /** Máximo de efectos por carril que se aceptan al leer un proyecto (la interfaz ofrece uno por tipo: sobra margen). */
    const val MAX_EFFECTS_PER_TRACK = 16
    private const val MAX_ID_LENGTH = 64
    private const val MAX_TYPE_LENGTH = 32
    private const val MAX_PARAMS_PER_EFFECT = 32
    private const val MAX_PARAM_NAME_LENGTH = 32

    fun toDataList(effects: List<AudioEffect>): List<AudioEffectData> = effects.map { toData(it) }

    /**
     * Frontera de confianza al leer el proyecto (`sanitizeProjectData`): un `project.json` corrupto o importado no puede
     * traer miles de efectos, ids o nombres de parámetros gigantes ni efectos sin id. El VALOR de cada parámetro se
     * sanea después, al convertir ([fromDataList] → [ReverbParams.coerced]).
     */
    fun sanitize(data: List<AudioEffectData>): List<AudioEffectData> =
        data.asSequence()
            .filter { it.id.isNotBlank() }
            .map { effect ->
                effect.copy(
                    id = effect.id.take(MAX_ID_LENGTH),
                    type = effect.type.take(MAX_TYPE_LENGTH),
                    params = effect.params.entries.asSequence()
                        .take(MAX_PARAMS_PER_EFFECT)
                        .associate { it.key.take(MAX_PARAM_NAME_LENGTH) to it.value }
                )
            }
            .take(MAX_EFFECTS_PER_TRACK)
            .toList()

    /** Los efectos reconocidos, en su orden, sin ids repetidos (un archivo manipulado no puede duplicar un módulo). */
    fun fromDataList(data: List<AudioEffectData>): List<AudioEffect> =
        data.mapNotNull { fromData(it) }.distinctBy { it.id }

    private fun toData(effect: AudioEffect): AudioEffectData = when (effect) {
        is ReverbEffect -> AudioEffectData(
            id = effect.id,
            type = TYPE_REVERB,
            enabled = effect.enabled,
            params = reverbToMap(effect.params)
        )
    }

    private fun fromData(data: AudioEffectData): AudioEffect? = when (data.type) {
        TYPE_REVERB -> ReverbEffect(id = data.id, enabled = data.enabled, params = reverbFromMap(data.params))
        else -> null
    }

    private fun reverbToMap(p: ReverbParams): Map<String, Float> = mapOf(
        ReverbKeys.MIX to p.mix,
        ReverbKeys.DECAY to p.decaySeconds,
        ReverbKeys.PRE_DELAY to p.preDelayMs,
        ReverbKeys.SIZE to p.size,
        ReverbKeys.DAMPING to p.dampingHz,
        ReverbKeys.LOW_CUT to p.lowCutHz,
        ReverbKeys.HIGH_CUT to p.highCutHz,
        ReverbKeys.DIFFUSION to p.diffusion,
        ReverbKeys.MODULATION to p.modulation,
        ReverbKeys.WIDTH to p.width,
        ReverbKeys.EARLY to p.earlyLevel
    )

    private fun reverbFromMap(m: Map<String, Float>): ReverbParams {
        val d = ReverbParams.DEFAULT
        return ReverbParams(
            mix = m[ReverbKeys.MIX] ?: d.mix,
            decaySeconds = m[ReverbKeys.DECAY] ?: d.decaySeconds,
            preDelayMs = m[ReverbKeys.PRE_DELAY] ?: d.preDelayMs,
            size = m[ReverbKeys.SIZE] ?: d.size,
            dampingHz = m[ReverbKeys.DAMPING] ?: d.dampingHz,
            lowCutHz = m[ReverbKeys.LOW_CUT] ?: d.lowCutHz,
            highCutHz = m[ReverbKeys.HIGH_CUT] ?: d.highCutHz,
            diffusion = m[ReverbKeys.DIFFUSION] ?: d.diffusion,
            modulation = m[ReverbKeys.MODULATION] ?: d.modulation,
            width = m[ReverbKeys.WIDTH] ?: d.width,
            earlyLevel = m[ReverbKeys.EARLY] ?: d.earlyLevel
        ).coerced()
    }
}
