package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.engine.color.GradientColorSpace
import com.yeivikas.olyzecs.engine.color.GradientGroupRef
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El degradado de grupo viaja en `project.json` como dos campos opcionales
 * por capa y por carril de audio. Los proyectos guardados antes de que
 * existieran no los traen y tienen que abrir sin grupo.
 */
class ProjectDataGradientGroupTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun `una capa conserva su grupo de degradado en el round-trip`() {
        val original = LayerData(
            id = "l1",
            imageFileName = "l1.png",
            name = "Capa 1",
            zIndex = 0,
            customColorArgb = 0xFF336699.toInt(),
            gradientGroupId = "g1",
            gradientGroupSpace = GradientColorSpace.RGB.name
        )
        val decoded = json.decodeFromString<LayerData>(json.encodeToString(original))
        assertEquals("g1", decoded.gradientGroupId)
        assertEquals("RGB", decoded.gradientGroupSpace)
    }

    @Test
    fun `un carril de audio conserva su grupo de degradado en el round-trip`() {
        val original = AudioTrackData(id = "a1", gradientGroupId = "g9", gradientGroupSpace = "PERCEPTUAL")
        val decoded = json.decodeFromString<AudioTrackData>(json.encodeToString(original))
        assertEquals("g9", decoded.gradientGroupId)
        assertEquals("PERCEPTUAL", decoded.gradientGroupSpace)
    }

    @Test
    fun `un proyecto antiguo sin los campos abre sin grupo`() {
        val legacyLayer = """{"id":"l1","imageFileName":"l1.png","name":"Capa 1","zIndex":0,"useGradientColor":true}"""
        val decoded = json.decodeFromString<LayerData>(legacyLayer)
        assertNull(decoded.gradientGroupId)
        assertNull(decoded.gradientGroupSpace)
        assertNull(gradientGroupFromStorage(decoded.gradientGroupId, decoded.gradientGroupSpace))
    }

    @Test
    fun `el grupo se reconstruye desde los campos guardados`() {
        assertEquals(
            GradientGroupRef("g1", GradientColorSpace.HUE),
            gradientGroupFromStorage("g1", "HUE")
        )
    }

    @Test
    fun `un espacio desconocido o ausente cae en el valor por defecto`() {
        assertEquals(GradientColorSpace.DEFAULT, gradientGroupFromStorage("g1", "inexistente")?.space)
        assertEquals(GradientColorSpace.DEFAULT, gradientGroupFromStorage("g1", null)?.space)
    }

    @Test
    fun `sin identificador o en blanco no hay grupo`() {
        assertNull(gradientGroupFromStorage(null, "RGB"))
        assertNull(gradientGroupFromStorage("   ", "RGB"))
    }
}
