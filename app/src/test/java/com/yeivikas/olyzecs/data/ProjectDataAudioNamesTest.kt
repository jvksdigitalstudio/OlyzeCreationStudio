package com.yeivikas.olyzecs.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Persistencia de los DOS niveles de nombre del audio: el del carril
 * (`AudioTrackData.name`) y el de cada clip (`AudioClipData.customName`), con la
 * misma configuración de [Json] que `ProjectStorage` (ver `ProjectDataSerializationTest`).
 *
 * Cubre lo que importa a producción: la vuelta completa, la lectura de proyectos
 * guardados ANTES de que existieran los nombres, y el saneo de un `project.json`
 * corrupto o importado (`sanitizeProjectData`, la frontera de confianza).
 */
class ProjectDataAudioNamesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun clip(id: String, customName: String? = null) = AudioClipData(
        id = id,
        audioFileName = "$id.m4a",
        displayName = "Kick 01.wav",
        sourceDurationMs = 1_000L,
        clipLengthMs = 1_000L,
        customName = customName
    )

    private fun project(track: AudioTrackData) =
        ProjectData(id = "p", name = "P", createdAtMs = 0L, updatedAtMs = 0L, audioTracks = listOf(track))

    @Test
    fun `los nombres de carril y de clip sobreviven la vuelta de serializacion`() {
        val original = project(
            AudioTrackData(
                id = "t1",
                name = "Percusión",
                clips = listOf(clip("c1", customName = "Bombo"), clip("c2"))
            )
        )

        val restored = json.decodeFromString<ProjectData>(json.encodeToString(original))

        assertEquals(original, restored)
        assertEquals("Percusión", restored.audioTracks[0].name)
        assertEquals("Bombo", restored.audioTracks[0].clips[0].customName)
        assertNull(restored.audioTracks[0].clips[1].customName)
    }

    @Test
    fun `el nombre del archivo fuente no se ve afectado por los nombres personalizados`() {
        val restored = json.decodeFromString<ProjectData>(
            json.encodeToString(project(AudioTrackData(id = "t1", name = "X", clips = listOf(clip("c1", "Y")))))
        )
        assertEquals("Kick 01.wav", restored.audioTracks[0].clips[0].displayName)
        assertEquals("c1.m4a", restored.audioTracks[0].clips[0].audioFileName)
    }

    @Test
    fun `un project json anterior a los nombres se lee como sin personalizar`() {
        val legacyJson = """
            {"id":"old","name":"Viejo","createdAtMs":0,"updatedAtMs":0,
             "audioTracks":[{"id":"t","clips":[{"id":"c","audioFileName":"a.m4a",
             "displayName":"A.wav","sourceDurationMs":1000}]}]}
        """.trimIndent()

        val restored = json.decodeFromString<ProjectData>(legacyJson)

        assertNull(restored.audioTracks[0].name)
        assertNull(restored.audioTracks[0].clips[0].customName)
    }

    @Test
    fun `el saneo deja intactos los nombres validos`() {
        val data = project(AudioTrackData(id = "t1", name = "Percusión", clips = listOf(clip("c1", "Bombo"))))

        val sanitized = sanitizeProjectData(data)

        assertEquals("Percusión", sanitized.audioTracks[0].name)
        assertEquals("Bombo", sanitized.audioTracks[0].clips[0].customName)
    }

    @Test
    fun `el saneo acota, limpia y descarta los nombres corruptos`() {
        val data = project(
            AudioTrackData(
                id = "t1",
                name = "a".repeat(500),
                clips = listOf(
                    clip("c1", customName = "Línea 1\nLínea 2"),
                    clip("c2", customName = "     "),
                    clip("c3", customName = "")
                )
            )
        )

        val sanitized = sanitizeProjectData(data)

        assertEquals(60, sanitized.audioTracks[0].name!!.length)
        assertEquals("Línea 1 Línea 2", sanitized.audioTracks[0].clips[0].customName)
        assertNull(sanitized.audioTracks[0].clips[1].customName)
        assertNull(sanitized.audioTracks[0].clips[2].customName)
    }
}
