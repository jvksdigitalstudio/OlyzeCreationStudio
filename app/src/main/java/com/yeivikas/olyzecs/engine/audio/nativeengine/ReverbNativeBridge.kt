package com.yeivikas.olyzecs.engine.audio.nativeengine

import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams

/** Cantidad de campos de [ReverbParams] que cruzan la frontera nativa (`ReverbParams::kFieldCount` en C++). */
internal const val NATIVE_REVERB_FIELD_COUNT = 11

/**
 * Orden de los campos de [ReverbParams] al cruzar a C++. Es el espejo EXACTO de `ReverbParams::toArray`/`fromArray`
 * (`cpp/core/reverb.cpp`): si se agrega o reordena un campo hay que cambiar los dos lados.
 */
internal fun ReverbParams.toNativeArray(): FloatArray = floatArrayOf(
    mix, decaySeconds, preDelayMs, size, dampingHz, lowCutHz, highCutHz, diffusion, modulation, width, earlyLevel
)
