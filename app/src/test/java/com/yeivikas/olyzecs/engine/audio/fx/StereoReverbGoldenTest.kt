package com.yeivikas.olyzecs.engine.audio.fx

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Referencia de PARIDAD con el reverb nativo (`app/src/main/cpp/core/reverb.cpp`).
 *
 * El motor de audio nativo trae un port de [StereoReverb]. Que el port cumpla las propiedades de un reverb (RT60, estabilidad,
 * bypass) lo demuestran las pruebas de C++; que suene IGUAL que este, no: eso solo se demuestra comparando salidas. Esta prueba
 * renderiza una señal fija con este reverb y guarda el resultado; `OLYZE_REVERB_GOLDEN=<archivo> make -C app/src/test/cpp test`
 * (paso de CI posterior a los tests unitarios) renderiza lo mismo en C++ y exige un desvío máximo < 2e-5.
 *
 * El escenario ([Scenario]) está duplicado a propósito en `testReverbMatchesKotlinGolden` (engine_tests.cpp): señal de entrada,
 * parámetros y momento del cambio de parámetros deben ser idénticos en los dos lados. Si se edita uno, se edita el otro.
 *
 * Cubre además el camino de suavizado de parámetros (`updateParams` a mitad del audio), no solo el estado estacionario.
 */
class StereoReverbGoldenTest {

    private object Scenario {
        const val SAMPLE_RATE = 48_000
        const val FRAMES = 48_000
        const val CHANGE_AT = 20_000

        val before = ReverbParams(
            mix = 0.45f, decaySeconds = 1.7f, preDelayMs = 35f, size = 0.8f, dampingHz = 6_000f, lowCutHz = 120f,
            highCutHz = 12_000f, diffusion = 0.6f, modulation = 0.5f, width = 0.8f, earlyLevel = 0.4f
        )
        // Mismo tamaño y pre-retardo (estructurales), el resto cambia: ejercita todo el suavizado.
        val after = before.copy(
            mix = 0.7f, decaySeconds = 3.1f, dampingHz = 4_000f, lowCutHz = 200f, highCutHz = 9_000f, diffusion = 0.9f,
            modulation = 0.2f, width = 1.0f, earlyLevel = 0.7f
        )

        /** Generador congruencial lineal con aritmética de 32 bits: idéntico bit a bit a `lcgNext` de C++. */
        fun lcgSignal(frames: Int, seed: Int): FloatArray {
            var state = seed
            return FloatArray(frames) {
                state = state * 1664525 + 1013904223
                ((state ushr 8).toFloat() / 8388608f - 1f) * 0.4f
            }
        }
    }

    @Test
    fun `escribe la salida de referencia del reverb para la comparacion con el port nativo`() {
        val inL = Scenario.lcgSignal(Scenario.FRAMES, 12345)
        val inR = Scenario.lcgSignal(Scenario.FRAMES, 54321)
        inL[0] += 0.9f
        inR[0] += 0.9f
        val outL = FloatArray(Scenario.FRAMES)
        val outR = FloatArray(Scenario.FRAMES)

        val reverb = StereoReverb(Scenario.SAMPLE_RATE, Scenario.before)
        // Primer tramo con los parámetros iniciales; luego el cambio; luego el resto (como en C++: `setTarget` entre dos `process`).
        reverb.process(inL, inR, outL, outR, Scenario.CHANGE_AT)
        assertTrue("El cambio de parámetros debe ser compatible", reverb.updateParams(Scenario.after))
        val tailIn = Scenario.FRAMES - Scenario.CHANGE_AT
        val restInL = inL.copyOfRange(Scenario.CHANGE_AT, Scenario.FRAMES)
        val restInR = inR.copyOfRange(Scenario.CHANGE_AT, Scenario.FRAMES)
        val restOutL = FloatArray(tailIn)
        val restOutR = FloatArray(tailIn)
        reverb.process(restInL, restInR, restOutL, restOutR, tailIn)
        restOutL.copyInto(outL, Scenario.CHANGE_AT)
        restOutR.copyInto(outR, Scenario.CHANGE_AT)

        assertTrue("La referencia no puede contener NaN ni infinitos", (outL + outR).all { it.isFinite() })

        // Binario little-endian, estéreo intercalado: lo que lee `testReverbMatchesKotlinGolden`.
        val buffer = ByteBuffer.allocate(Scenario.FRAMES * 2 * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until Scenario.FRAMES) {
            buffer.putFloat(outL[i])
            buffer.putFloat(outR[i])
        }
        val target = File(System.getProperty("user.dir"), "build/native-golden/reverb_golden.f32")
        target.parentFile?.mkdirs()
        target.writeBytes(buffer.array())
        assertTrue("No se pudo escribir la referencia en $target", target.length() == buffer.array().size.toLong())
    }
}
