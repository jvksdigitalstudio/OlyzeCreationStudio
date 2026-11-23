package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regla del toque en el ícono del volumen de la barra flotante ([volumeIconTapOutcome]): silenciar sin mover el
 * nivel, y devolver el sonido exactamente al nivel anterior. Solo primitivos (sin `android.net.Uri`).
 */
class AudioVolumeIconTapTest {

    @Test
    fun `con sonido el primer toque silencia y conserva el nivel`() {
        assertEquals(AudioMuteLevel(muted = true, volume = 0.63f), volumeIconTapOutcome(muted = false, volume = 0.63f))
    }

    @Test
    fun `silenciado el toque reactiva el sonido en el mismo nivel`() {
        assertEquals(AudioMuteLevel(muted = false, volume = 0.63f), volumeIconTapOutcome(muted = true, volume = 0.63f))
    }

    @Test
    fun `dos toques seguidos devuelven el estado original para cualquier nivel`() {
        for (level in listOf(0.01f, 0.25f, 0.5f, 1f, 1.2f, MAX_AUDIO_VOLUME)) {
            val first = volumeIconTapOutcome(muted = false, volume = level)
            val second = volumeIconTapOutcome(first.muted, first.volume)
            assertEquals(AudioMuteLevel(muted = false, volume = level), second)
        }
    }

    @Test
    fun `un nivel por encima del 100 por ciento tambien se conserva`() {
        val muted = volumeIconTapOutcome(muted = false, volume = MAX_AUDIO_VOLUME)
        assertEquals(MAX_AUDIO_VOLUME, muted.volume, 0f)
        assertEquals(MAX_AUDIO_VOLUME, volumeIconTapOutcome(muted.muted, muted.volume).volume, 0f)
    }

    @Test
    fun `con el deslizador en cero el toque restaura el volumen original`() {
        assertEquals(
            AudioMuteLevel(muted = false, volume = UNITY_AUDIO_VOLUME),
            volumeIconTapOutcome(muted = false, volume = 0f)
        )
    }

    @Test
    fun `silenciado y con nivel cero reactivar restaura el volumen original`() {
        assertEquals(
            AudioMuteLevel(muted = false, volume = UNITY_AUDIO_VOLUME),
            volumeIconTapOutcome(muted = true, volume = 0f)
        )
    }

    @Test
    fun `un nivel no finito se trata como cero`() {
        assertEquals(UNITY_AUDIO_VOLUME, volumeIconTapOutcome(muted = false, volume = Float.NaN).volume, 0f)
        assertEquals(UNITY_AUDIO_VOLUME, volumeIconTapOutcome(muted = true, volume = Float.NEGATIVE_INFINITY).volume, 0f)
    }

    @Test
    fun `el toque nunca deja un estado inaudible tras reactivar`() {
        for (level in listOf(0f, 0.3f, 1f, Float.NaN)) {
            val result = volumeIconTapOutcome(muted = true, volume = level)
            assertEquals(false, result.muted)
            assertEquals(true, result.volume > 0f)
        }
    }

    // ---- Control maestro de la capa ----

    private fun level(muted: Boolean, volume: Float) = AudioMuteLevel(muted, volume)

    @Test
    fun `una capa sin clips no se lee como silenciosa`() {
        assertEquals(false, emptyList<AudioMuteLevel>().masterReadsAsSilent())
        assertEquals(emptyList<AudioMuteLevel>(), emptyList<AudioMuteLevel>().afterMasterVolumeIconTap())
    }

    @Test
    fun `la capa suena si algun clip es audible`() {
        val levels = listOf(level(true, 0.8f), level(false, 0.5f))
        assertEquals(false, levels.masterReadsAsSilent())
    }

    @Test
    fun `la capa se lee silenciosa si todos estan silenciados o a cero`() {
        assertEquals(true, listOf(level(true, 0.8f), level(false, 0f)).masterReadsAsSilent())
    }

    @Test
    fun `el primer toque silencia todos los clips sin tocar ningun nivel`() {
        val levels = listOf(level(false, 0.3f), level(false, 1f), level(true, 0.9f))
        assertEquals(
            listOf(level(true, 0.3f), level(true, 1f), level(true, 0.9f)),
            levels.afterMasterVolumeIconTap()
        )
    }

    @Test
    fun `el segundo toque reactiva todos y cada clip conserva su nivel`() {
        val original = listOf(level(false, 0.3f), level(false, 1f), level(false, 1.4f))
        val silenced = original.afterMasterVolumeIconTap()
        assertEquals(original, silenced.afterMasterVolumeIconTap())
    }

    @Test
    fun `al reactivar los clips a cero vuelven al volumen original`() {
        val levels = listOf(level(true, 0.6f), level(true, 0f))
        assertEquals(
            listOf(level(false, 0.6f), level(false, UNITY_AUDIO_VOLUME)),
            levels.afterMasterVolumeIconTap()
        )
    }

    @Test
    fun `con clips en estados mezclados el toque los deja a todos en el mismo estado`() {
        val mixed = listOf(level(true, 0.5f), level(false, 0.5f))
        assertEquals(true, mixed.afterMasterVolumeIconTap().all { it.muted })
    }

    @Test
    fun `tras reactivar ningun clip queda inaudible`() {
        val silent = listOf(level(true, 0f), level(true, 0.004f), level(false, 0f), level(true, 0.7f))
        val result = silent.afterMasterVolumeIconTap()
        assertEquals(true, result.none { it.muted })
        assertEquals(true, result.none { audioVolumeReadsAsZero(it.volume) })
    }

    // ---- Nivel que se lee como cero y balance ----

    @Test
    fun `un nivel se lee como cero con el mismo redondeo del rotulo de porcentaje`() {
        assertEquals(true, audioVolumeReadsAsZero(0f))
        assertEquals(true, audioVolumeReadsAsZero(0.004f))
        assertEquals(false, audioVolumeReadsAsZero(0.005f))
        assertEquals(true, audioVolumeReadsAsZero(Float.NaN))
    }

    @Test
    fun `el balance se acota y se encaja en el centro`() {
        assertEquals(0f, snapAudioPan(0.01f), 0f)
        assertEquals(0f, snapAudioPan(-0.019f), 0f)
        assertEquals(0.5f, snapAudioPan(0.5f), 0f)
        assertEquals(1f, snapAudioPan(9f), 0f)
        assertEquals(-1f, snapAudioPan(-9f), 0f)
        assertEquals(DEFAULT_AUDIO_PAN, snapAudioPan(Float.NaN), 0f)
    }

    // ---- Herencia de los ajustes maestros por los clips nuevos ----

    private fun mix(volume: Float, pan: Float, muted: Boolean) = AudioMixState(volume, pan, muted)

    @Test
    fun `una capa sin clips no tiene ajustes que heredar`() {
        assertEquals(null, emptyList<AudioMixState>().masterMix())
    }

    @Test
    fun `los ajustes maestros son los del primer clip`() {
        val mixes = listOf(mix(0.4f, -0.5f, false), mix(0.9f, 0.7f, false))
        assertEquals(mix(0.4f, -0.5f, false), mixes.masterMix())
    }

    @Test
    fun `el silencio maestro solo se hereda si todos los clips estan silenciados`() {
        assertEquals(true, listOf(mix(1f, 0f, true), mix(1f, 0f, true)).masterMix()?.muted)
        assertEquals(false, listOf(mix(1f, 0f, true), mix(1f, 0f, false)).masterMix()?.muted)
    }

    @Test
    fun `una capa de un solo clip hereda exactamente los ajustes de ese clip`() {
        assertEquals(mix(0.25f, 0.3f, true), listOf(mix(0.25f, 0.3f, true)).masterMix())
    }
}
