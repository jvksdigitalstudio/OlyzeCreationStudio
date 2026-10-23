package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.engine.audio.fx.AudioEffect
import com.yeivikas.olyzecs.engine.audio.fx.ReverbEffect
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import com.yeivikas.olyzecs.engine.audio.fx.ReverbPresets
import com.yeivikas.olyzecs.engine.audio.fx.ReverbRanges
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistencia de los efectos de un carril: la traducción dominio ⇄ archivo ([AudioEffectCodec]) y el saneo del
 * `project.json` al cargar. Mismo [Json] que `ProjectStorage`.
 */
class AudioEffectCodecTest {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun roundTrip(effects: List<AudioEffect>): List<AudioEffect> {
        val data = AudioEffectCodec.toDataList(effects)
        val restored = json.decodeFromString<List<AudioEffectData>>(json.encodeToString(data))
        return AudioEffectCodec.fromDataList(restored)
    }

    @Test
    fun `el reverb sobrevive la vuelta completa de guardado y carga, parametro por parametro`() {
        val effect = ReverbEffect(
            id = "r1", enabled = false,
            params = ReverbParams(
                mix = 0.37f, decaySeconds = 4.25f, preDelayMs = 33f, size = 0.81f, dampingHz = 5_432f, lowCutHz = 123f,
                highCutHz = 9_876f, diffusion = 0.42f, modulation = 0.17f, width = 0.64f, earlyLevel = 0.29f
            )
        )
        assertEquals(listOf<AudioEffect>(effect), roundTrip(listOf(effect)))
    }

    @Test
    fun `un preset guardado sigue reconocido como preset al volver a cargar`() {
        for ((index, preset) in ReverbPresets.all.withIndex()) {
            val restored = roundTrip(listOf(ReverbEffect(id = "r", params = preset.params))).single() as ReverbEffect
            assertEquals(index, ReverbPresets.indexOf(restored.params))
        }
    }

    @Test
    fun `el orden de la cadena se conserva`() {
        val chain = listOf(ReverbEffect(id = "a"), ReverbEffect(id = "b"), ReverbEffect(id = "c"))
        assertEquals(chain.map { it.id }, roundTrip(chain).map { it.id })
    }

    @Test
    fun `un parametro ausente toma su valor de fabrica`() {
        val data = AudioEffectData(id = "r", type = AudioEffectCodec.TYPE_REVERB, params = mapOf("mix" to 0.9f))
        val effect = AudioEffectCodec.fromDataList(listOf(data)).single() as ReverbEffect
        assertEquals(0.9f, effect.params.mix, 0f)
        assertEquals(ReverbParams.DEFAULT.copy(mix = 0.9f), effect.params)
    }

    @Test
    fun `un parametro fuera de rango o corrupto se acota al cargar`() {
        val data = AudioEffectData(
            id = "r", type = AudioEffectCodec.TYPE_REVERB,
            params = mapOf("mix" to 9f, "decay" to -1f, "size" to Float.NaN, "lowCut" to Float.POSITIVE_INFINITY)
        )
        val p = (AudioEffectCodec.fromDataList(listOf(data)).single() as ReverbEffect).params
        assertEquals(ReverbRanges.MIX.endInclusive, p.mix, 0f)
        assertEquals(ReverbRanges.DECAY_SECONDS.start, p.decaySeconds, 0f)
        assertEquals(ReverbParams.DEFAULT.size, p.size, 0f)
        assertEquals(ReverbRanges.LOW_CUT_HZ.endInclusive, p.lowCutHz, 0f)
    }

    @Test
    fun `un efecto de un tipo desconocido se omite sin romper la carga de los demas`() {
        val data = listOf(
            AudioEffectData(id = "a", type = AudioEffectCodec.TYPE_REVERB),
            AudioEffectData(id = "x", type = "delay-del-futuro", params = mapOf("time" to 1f)),
            AudioEffectData(id = "b", type = AudioEffectCodec.TYPE_REVERB)
        )
        assertEquals(listOf("a", "b"), AudioEffectCodec.fromDataList(data).map { it.id })
    }

    @Test
    fun `un archivo con ids repetidos no duplica el modulo`() {
        val data = listOf(
            AudioEffectData(id = "dup", type = AudioEffectCodec.TYPE_REVERB, params = mapOf("mix" to 0.1f)),
            AudioEffectData(id = "dup", type = AudioEffectCodec.TYPE_REVERB, params = mapOf("mix" to 0.9f))
        )
        val restored = AudioEffectCodec.fromDataList(data)
        assertEquals(1, restored.size)
        assertEquals(0.1f, (restored.single() as ReverbEffect).params.mix, 0f)
    }

    @Test
    fun `un proyecto guardado antes de que existieran los efectos se carga sin efectos`() {
        val legacy = json.decodeFromString<AudioTrackData>("""{"id":"t1"}""")
        assertTrue(legacy.effects.isEmpty())
        assertTrue(AudioEffectCodec.fromDataList(legacy.effects).isEmpty())
    }

    @Test
    fun `los efectos viajan dentro del carril y las claves desconocidas se ignoran`() {
        val track = AudioTrackData(id = "t1", effects = AudioEffectCodec.toDataList(listOf(ReverbEffect(id = "r1"))))
        val text = json.encodeToString(track).replace("\"id\": \"t1\"", "\"id\": \"t1\",\n    \"campoDelFuturo\": 123")
        val restored = json.decodeFromString<AudioTrackData>(text)
        assertEquals(track, restored)
    }

    // ---------------------------------------------------------------- saneo del project.json

    @Test
    fun `el saneo limita la cantidad de efectos por carril`() {
        val many = List(500) { AudioEffectData(id = "e$it", type = AudioEffectCodec.TYPE_REVERB) }
        val sanitized = AudioEffectCodec.sanitize(many)
        assertEquals(AudioEffectCodec.MAX_EFFECTS_PER_TRACK, sanitized.size)
        assertEquals("e0", sanitized.first().id)
    }

    @Test
    fun `el saneo descarta efectos sin id y acota ids, tipos y parametros gigantes`() {
        val huge = AudioEffectData(
            id = "i".repeat(10_000),
            type = "t".repeat(10_000),
            params = (0 until 500).associate { ("k".repeat(1_000) + it) to 1f }
        )
        val sanitized = AudioEffectCodec.sanitize(listOf(AudioEffectData(id = "  ", type = AudioEffectCodec.TYPE_REVERB), huge))
        assertEquals(1, sanitized.size)
        assertTrue(sanitized.single().id.length <= 64)
        assertTrue(sanitized.single().type.length <= 32)
        assertTrue(sanitized.single().params.size <= 32)
        assertTrue(sanitized.single().params.keys.all { it.length <= 32 })
    }

    @Test
    fun `el saneo deja intacto un proyecto legitimo`() {
        val legit = AudioEffectCodec.toDataList(listOf(ReverbEffect(id = "r1", params = ReverbPresets.all[3].params)))
        assertEquals(legit, AudioEffectCodec.sanitize(legit))
    }

    @Test
    fun `sanitizeProjectData aplica el saneo a los efectos de cada carril`() {
        val many = List(100) { AudioEffectData(id = "e$it", type = AudioEffectCodec.TYPE_REVERB) }
        val data = ProjectData(
            id = "p", name = "P", createdAtMs = 0L, updatedAtMs = 0L,
            audioTracks = listOf(AudioTrackData(id = "t1", effects = many), AudioTrackData(id = "t2"))
        )
        val sanitized = sanitizeProjectData(data)
        assertEquals(AudioEffectCodec.MAX_EFFECTS_PER_TRACK, sanitized.audioTracks[0].effects.size)
        assertTrue(sanitized.audioTracks[1].effects.isEmpty())
    }
}
