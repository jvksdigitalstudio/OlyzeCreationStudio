package com.yeivikas.olyzecs.engine.effects.gpu

import com.yeivikas.olyzecs.engine.effects.ImageEffectsParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerEffectParamsTest {

    @Test
    fun `por defecto es neutro`() {
        assertTrue(LayerEffectParams().isNeutral)
        assertTrue(LayerEffectParams.NEUTRAL.isNeutral)
        assertEquals(LayerEffectParams(), LayerEffectParams.NEUTRAL)
    }

    @Test
    fun `cualquier cambio de color por encima de la tolerancia deja de ser neutro`() {
        assertFalse(ColorBasicParams(brightness = 0.1f).isNeutral)
        assertFalse(ColorBasicParams(contrast = 1.1f).isNeutral)
        assertFalse(ColorBasicParams(saturation = 0.9f).isNeutral)
        assertFalse(ColorBasicParams(hue = 10f).isNeutral)
        assertFalse(ColorBasicParams(sharpen = 0.2f).isNeutral)
    }

    @Test
    fun `un cambio por debajo de la tolerancia sigue siendo neutro`() {
        assertTrue(ColorBasicParams(brightness = 0.0005f, contrast = 1.0005f, saturation = 0.9995f, hue = 0.0005f, sharpen = 0.0005f).isNeutral)
    }

    @Test
    fun `360 grados de matiz equivalen a ninguno`() {
        assertTrue(ColorBasicParams(hue = 360f).isNeutral)
        assertTrue(ColorBasicParams(hue = -360f).isNeutral)
        assertFalse(ColorBasicParams(hue = 180f).isNeutral)
    }

    @Test
    fun `sanitized acota cada valor a su rango`() {
        val clean = ColorBasicParams(brightness = 9f, contrast = -3f, saturation = 50f, hue = 725f, sharpen = 4f).sanitized()
        assertEquals(1f, clean.brightness, 0f)
        assertEquals(0f, clean.contrast, 0f)
        assertEquals(2f, clean.saturation, 0f)
        assertEquals(5f, clean.hue, 1e-3f)
        assertEquals(1f, clean.sharpen, 0f)
    }

    @Test
    fun `sanitized convierte valores no finitos en el neutro de cada parametro`() {
        val clean = ColorBasicParams(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, Float.NaN).sanitized()
        assertEquals(ColorBasicParams(), clean)
        assertTrue(clean.isNeutral)
    }

    @Test
    fun `sanitized no altera valores ya validos`() {
        val ok = ColorBasicParams(brightness = -0.4f, contrast = 1.3f, saturation = 0.2f, hue = 90f, sharpen = 0.6f)
        assertEquals(ok, ok.sanitized())
    }

    @Test
    fun `los rangos y la neutralidad coinciden con los del camino CPU actual`() {
        // Mientras convivan GPU y CPU, el mismo valor tiene que significar lo mismo en ambos.
        val probes = listOf(-3f, -1f, -0.4f, 0f, 0.0005f, 0.5f, 1f, 1.0005f, 1.5f, 2f, 7f, 360f, 725f)
        for (v in probes) {
            val cpu = ImageEffectsParams(brightness = v, contrast = v, saturation = v, hue = v, sharpen = v).sanitized()
            val gpu = ColorBasicParams(brightness = v, contrast = v, saturation = v, hue = v, sharpen = v).sanitized()
            assertEquals("brightness $v", cpu.brightness, gpu.brightness, 1e-4f)
            assertEquals("contrast $v", cpu.contrast, gpu.contrast, 1e-4f)
            assertEquals("saturation $v", cpu.saturation, gpu.saturation, 1e-4f)
            assertEquals("hue $v", cpu.hue, gpu.hue, 1e-3f)
            assertEquals("sharpen $v", cpu.sharpen, gpu.sharpen, 1e-4f)
        }
        for (v in listOf(0f, 0.0005f, 0.5f)) {
            assertEquals(
                "neutro brightness=$v",
                ImageEffectsParams(brightness = v).isNeutral,
                ColorBasicParams(brightness = v).isNeutral
            )
        }
        assertEquals(ImageEffectsParams(hue = 360f).isNeutral, ColorBasicParams(hue = 360f).isNeutral)
        assertEquals(ImageEffectsParams(sharpen = 0.0005f).isNeutral, ColorBasicParams(sharpen = 0.0005f).isNeutral)
    }

    @Test
    fun `dos instancias iguales son iguales y tienen el mismo hash, requisito de la clave de cache`() {
        val a = LayerEffectParams(ColorBasicParams(contrast = 1.2f))
        val b = LayerEffectParams(ColorBasicParams(contrast = 1.2f))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
