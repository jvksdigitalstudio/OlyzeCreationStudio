package com.yeivikas.olyzecs.engine.audio.nativeengine

import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CONTRATO con el motor nativo: [ReverbParams] existe en Kotlin y en C++ (`cpp/core/reverb.h`) y sus campos cruzan por un arreglo de 11
 * floats. Un campo intercambiado o un valor por defecto distinto produciría OTRO reverb sin ningún error. Los literales de aquí son los
 * mismos que en `testReverbParamsMatchTheSharedContract` (app/src/test/cpp/engine_tests.cpp).
 */
class ReverbParamsContractTest {

    @Test
    fun `el orden de los campos al cruzar a C++ es el del contrato`() {
        val ordered = ReverbParams(
            mix = 1f, decaySeconds = 2f, preDelayMs = 3f, size = 4f, dampingHz = 5f, lowCutHz = 6f,
            highCutHz = 7f, diffusion = 8f, modulation = 9f, width = 10f, earlyLevel = 11f
        )
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f), ordered.toNativeArray(), 0f)
    }

    @Test
    fun `los valores por defecto coinciden con los del motor nativo`() {
        // mix, decay, preDelay, size, damping, lowCut, highCut, diffusion, modulation, width, earlyLevel
        val defaults = floatArrayOf(0.30f, 2.0f, 20.0f, 0.6f, 8000.0f, 90.0f, 14000.0f, 0.75f, 0.35f, 1.0f, 0.5f)
        assertArrayEquals(defaults, ReverbParams().toNativeArray(), 0f)
    }

    @Test
    fun `la cantidad de campos que cruzan coincide con la del motor nativo`() {
        assertEquals(11, NATIVE_REVERB_FIELD_COUNT)
        assertEquals(NATIVE_REVERB_FIELD_COUNT, ReverbParams().toNativeArray().size)
    }
}
