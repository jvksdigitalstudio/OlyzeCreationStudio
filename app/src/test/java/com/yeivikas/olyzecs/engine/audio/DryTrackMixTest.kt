package com.yeivikas.olyzecs.engine.audio

import com.yeivikas.olyzecs.engine.audio.fx.ReverbEffect
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Reglas puras de la mezcla SECA de un carril con Reverb ([DryTrackMix.kt]): cuánto dura y cuándo hay que volver a renderizar.
 * Se prueban con carriles sin clips (un clip necesita un `Uri` de Android); la parte de los clips de la clave se verifica en el
 * dispositivo. La garantía central de esta fase: NADA del efecto entra en la clave, así mover una perilla no renderiza.
 */
class DryTrackMixTest {
    private val reverb = ReverbEffect(id = "r", params = ReverbParams(preDelayMs = 100f, decaySeconds = 3f))
    private val track = AudioTrack(id = "t1").withEffect(reverb)

    @Test
    fun `la duracion seca es el final del carril sin la cola del efecto`() {
        // Sin clips el final es 0: la cola del reverb NO se renderiza (la agrega el motor en vivo al reproducir).
        assertEquals(0L, dryMixDurationMs(track))
        assertEquals(0L, dryMixDurationMs(AudioTrack(id = "t")))
    }

    @Test
    fun `la duracion seca no depende de los parametros del efecto`() {
        val longTail = track.withEffect(reverb.copy(params = reverb.params.copy(decaySeconds = 15f, preDelayMs = 250f)))
        assertEquals(dryMixDurationMs(track), dryMixDurationMs(longTail))
    }

    @Test
    fun `la clave no cambia con ningun parametro del efecto`() {
        val base = dryMixKey(track)
        for (changed in listOf(
            reverb.params.copy(mix = 0.8f), reverb.params.copy(decaySeconds = 4f), reverb.params.copy(preDelayMs = 10f),
            reverb.params.copy(size = 0.9f), reverb.params.copy(dampingHz = 3_000f), reverb.params.copy(lowCutHz = 300f),
            reverb.params.copy(highCutHz = 5_000f), reverb.params.copy(diffusion = 0.1f), reverb.params.copy(modulation = 0f),
            reverb.params.copy(width = 0.2f), reverb.params.copy(earlyLevel = 0.9f)
        )) {
            assertEquals(base, dryMixKey(track.withEffect(reverb.copy(params = changed))))
        }
    }

    @Test
    fun `activar, desactivar o quitar el efecto no cambia la clave`() {
        val base = dryMixKey(track)
        assertEquals(base, dryMixKey(track.copy(effects = listOf(reverb.copy(enabled = false)))))
        assertEquals(base, dryMixKey(track.copy(effects = listOf(reverb.copy(params = reverb.params.copy(mix = 0f))))))
        assertEquals(base, dryMixKey(AudioTrack(id = "t1")))
    }

    @Test
    fun `la clave no cambia con lo que no se oye`() {
        assertEquals(dryMixKey(track), dryMixKey(track.copy(name = "Voces", trackOrder = 3f)))
    }

    @Test
    fun `dos carriles con los mismos clips dan la misma clave aunque sus efectos difieran`() {
        assertEquals(dryMixKey(track), dryMixKey(AudioTrack(id = "otro").withEffect(reverb.copy(id = "x", params = ReverbParams.DEFAULT))))
    }

    @Test
    fun `un carril sin clips no tiene clips audibles`() {
        assertFalse(track.hasAudibleClips)
    }
}
