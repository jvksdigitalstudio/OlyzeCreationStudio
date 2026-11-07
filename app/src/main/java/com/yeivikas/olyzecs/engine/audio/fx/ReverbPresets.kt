package com.yeivikas.olyzecs.engine.audio.fx

/** Identidad ESTABLE de un preset del Reverb. La interfaz traduce cada id a su nombre; este archivo no conoce textos. */
enum class ReverbPresetId { ROOM, CHAMBER, PLATE, HALL, CATHEDRAL, VOCAL }

/** Un preset de fábrica: un [id] estable y el juego completo de [params] (incluido el mix). */
data class ReverbPreset(val id: ReverbPresetId, val params: ReverbParams)

/** Catálogo de presets de fábrica del Reverb, en el orden en que se ofrecen. */
object ReverbPresets {
    val all: List<ReverbPreset> = listOf(
        ReverbPreset(
            ReverbPresetId.ROOM,
            ReverbParams(mix = 0.25f, decaySeconds = 0.8f, preDelayMs = 5f, size = 0.3f, dampingHz = 6_000f, lowCutHz = 100f,
                highCutHz = 12_000f, diffusion = 0.6f, modulation = 0.2f, width = 0.8f, earlyLevel = 0.7f)
        ),
        ReverbPreset(
            ReverbPresetId.CHAMBER,
            ReverbParams(mix = 0.28f, decaySeconds = 1.4f, preDelayMs = 10f, size = 0.45f, dampingHz = 7_000f, lowCutHz = 100f,
                highCutHz = 13_000f, diffusion = 0.7f, modulation = 0.25f, width = 0.9f, earlyLevel = 0.6f)
        ),
        ReverbPreset(
            ReverbPresetId.PLATE,
            ReverbParams(mix = 0.30f, decaySeconds = 2.2f, preDelayMs = 0f, size = 0.55f, dampingHz = 11_000f, lowCutHz = 120f,
                highCutHz = 16_000f, diffusion = 1f, modulation = 0.45f, width = 1f, earlyLevel = 0.15f)
        ),
        ReverbPreset(
            ReverbPresetId.HALL,
            ReverbParams(mix = 0.30f, decaySeconds = 3.2f, preDelayMs = 25f, size = 0.8f, dampingHz = 6_500f, lowCutHz = 90f,
                highCutHz = 12_000f, diffusion = 0.85f, modulation = 0.4f, width = 1f, earlyLevel = 0.45f)
        ),
        ReverbPreset(
            ReverbPresetId.CATHEDRAL,
            ReverbParams(mix = 0.35f, decaySeconds = 7f, preDelayMs = 40f, size = 1f, dampingHz = 4_500f, lowCutHz = 80f,
                highCutHz = 9_000f, diffusion = 0.9f, modulation = 0.5f, width = 1f, earlyLevel = 0.3f)
        ),
        ReverbPreset(
            ReverbPresetId.VOCAL,
            ReverbParams(mix = 0.22f, decaySeconds = 1.8f, preDelayMs = 30f, size = 0.55f, dampingHz = 9_000f, lowCutHz = 160f,
                highCutHz = 15_000f, diffusion = 0.8f, modulation = 0.35f, width = 0.9f, earlyLevel = 0.35f)
        )
    )

    /** Posición en [all] del preset cuyos parámetros son EXACTAMENTE [params], o `null` si ya se modificó algún valor ("a medida"). */
    fun indexOf(params: ReverbParams): Int? = all.indexOfFirst { it.params == params }.takeIf { it >= 0 }
}
