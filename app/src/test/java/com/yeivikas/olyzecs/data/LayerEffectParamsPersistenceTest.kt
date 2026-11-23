package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.engine.effects.gpu.ColorBasicParams
import com.yeivikas.olyzecs.engine.effects.gpu.LayerEffectParams
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Garantiza que los efectos no destructivos se guardan, se leen y se toleran igual que el resto del proyecto. */
class LayerEffectParamsPersistenceTest {

    // La misma configuración que ProjectStorage.
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val edited = LayerEffectParams(
        ColorBasicParams(brightness = -0.25f, contrast = 1.4f, saturation = 0.6f, hue = 200f, sharpen = 0.35f)
    )

    private fun layerWith(params: LayerEffectParams) =
        LayerData(id = "layer-1", imageFileName = "layer-1.png", name = "Capa", zIndex = 0, effectParams = params)

    @Test
    fun `los efectos de una capa sobreviven a guardar y cargar`() {
        val restored = json.decodeFromString<LayerData>(json.encodeToString(layerWith(edited)))
        assertEquals(edited, restored.effectParams)
    }

    @Test
    fun `una capa sin efectos guarda y carga como neutra`() {
        val restored = json.decodeFromString<LayerData>(json.encodeToString(layerWith(LayerEffectParams())))
        assertTrue(restored.effectParams.isNeutral)
    }

    @Test
    fun `un proyecto guardado antes de existir el campo carga con efectos neutros`() {
        val withField = json.parseToJsonElement(json.encodeToString(layerWith(edited))).jsonObject
        val legacy = JsonObject(withField - "effectParams")
        assertFalse("el JSON de prueba no debe contener el campo", "effectParams" in legacy)
        val restored = json.decodeFromString<LayerData>(legacy.toString())
        assertEquals(LayerEffectParams(), restored.effectParams)
        assertTrue(restored.effectParams.isNeutral)
    }

    @Test
    fun `bloques que esta version no conoce se ignoran sin perder lo que si conoce`() {
        // Un proyecto guardado por una versión futura, con un bloque de efectos y un campo nuevos.
        val future = """{"colorBasic":{"contrast":1.5,"campoDelFuturo":3},"bloqueDelFuturo":{"x":1}}"""
        val restored = json.decodeFromString<LayerEffectParams>(future)
        assertEquals(1.5f, restored.colorBasic.contrast, 0f)
        assertEquals(0f, restored.colorBasic.brightness, 0f)
    }

    @Test
    fun `un bloque parcial completa lo que falta con el valor neutro de cada parametro`() {
        val partial = json.decodeFromString<LayerEffectParams>("""{"colorBasic":{"hue":90}}""")
        assertEquals(ColorBasicParams(hue = 90f), partial.colorBasic)
        assertEquals(1f, partial.colorBasic.contrast, 0f)
        assertEquals(1f, partial.colorBasic.saturation, 0f)
    }

    @Test
    fun `datos corruptos leidos de disco quedan en rango tras sanear`() {
        val corrupt = json.decodeFromString<LayerEffectParams>(
            """{"colorBasic":{"brightness":50,"contrast":-9,"saturation":99,"hue":-30,"sharpen":7}}"""
        ).sanitized()
        assertEquals(1f, corrupt.colorBasic.brightness, 0f)
        assertEquals(0f, corrupt.colorBasic.contrast, 0f)
        assertEquals(2f, corrupt.colorBasic.saturation, 0f)
        assertEquals(330f, corrupt.colorBasic.hue, 1e-3f)
        assertEquals(1f, corrupt.colorBasic.sharpen, 0f)
    }
}
