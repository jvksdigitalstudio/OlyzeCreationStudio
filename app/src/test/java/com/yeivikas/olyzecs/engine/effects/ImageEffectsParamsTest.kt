package com.yeivikas.olyzecs.engine.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug real corregido: [ImageEffectsParams.isNeutral] no incluía
 * [ImageEffectsParams.groundOcclusionIntensity], así que activar SOLO
 * ese slider (con todo lo demás en su valor por defecto) hacía que
 * [ImageEffects.apply] cortara camino en la primera línea y el efecto
 * nunca se aplicara — en silencio, sin ningún error.
 */
class ImageEffectsParamsTest {

    @Test
    fun `oclusion ambiental sola ya NO se considera neutral`() {
        val params = ImageEffectsParams(groundOcclusionIntensity = 0.5f)
        assertFalse(
            "groundOcclusionIntensity>0 con todo lo demas en default debe romper isNeutral",
            params.isNeutral
        )
    }

    @Test
    fun `sin tocar nada, los parametros por defecto siguen siendo neutrales`() {
        // Control de que el fix no rompió el caso base (nada tocado).
        assertTrue(ImageEffectsParams().isNeutral)
    }

    @Test
    fun `oclusion ambiental en cero sigue siendo neutral`() {
        assertTrue(ImageEffectsParams(groundOcclusionIntensity = 0f).isNeutral)
    }

    // --- Nuevos sub-parámetros de Contorno/Resplandor: mismo criterio
    // que shadowBlur/shadowSpread ya establecido arriba — son
    // sub-parámetros de un efecto que solo importa si su propio
    // "intensity"/"Grosor" ya está arriba de 0, así que NO necesitan su
    // propia entrada en [ImageEffectsParams.isNeutral].

    @Test
    fun `difuminado o posicion de contorno solos, sin grosor, siguen siendo neutral`() {
        val params = ImageEffectsParams(
            outlineFeather = 0.6f,
            outlinePosition = OutlineStrokePosition.INSIDE
        )
        assertTrue(
            "outlineFeather/outlinePosition sin outlineIntensity>0 no deben producir ningun efecto visible",
            params.isNeutral
        )
    }

    @Test
    fun `expansion o distancia de resplandor solas, sin intensidad, siguen siendo neutral`() {
        val params = ImageEffectsParams(glowSpread = 0.5f, glowDistance = 0.5f, glowAngleDeg = 45f)
        assertTrue(
            "glowSpread/glowDistance/glowAngleDeg sin glowIntensity>0 no deben producir ningun efecto visible",
            params.isNeutral
        )
    }

    @Test
    fun `sanitized recorta outlineFeather y glowSpread-distancia al rango 0 a 1`() {
        val params = ImageEffectsParams(
            outlineIntensity = 0.5f,
            outlineFeather = 4f,
            glowIntensity = 0.5f,
            glowSpread = -2f,
            glowDistance = 8f
        ).sanitized()
        assertEquals(1f, params.outlineFeather, 1e-4f)
        assertEquals(0f, params.glowSpread, 1e-4f)
        assertEquals(1f, params.glowDistance, 1e-4f)
    }

    @Test
    fun `sanitized envuelve glowAngleDeg a 0 hasta 360, igual que el resto de los angulos`() {
        val params = ImageEffectsParams(glowAngleDeg = 405f).sanitized()
        assertEquals(45f, params.glowAngleDeg, 1e-4f)
    }

    // --- Degradado (2 colores) y Blend Mode del Resplandor — A PEDIDO
    // DEL USUARIO. Mismo criterio que outlineColor2/outlineGradientEnabled/
    // outlinePosition (arriba): son sub-parámetros de "Resplandor" que
    // solo importan si glowIntensity ya está arriba de 0, así que NO
    // necesitan su propia entrada en [ImageEffectsParams.isNeutral], y no
    // son numéricos así que [sanitized] no tiene nada que recortarles —
    // deben pasar intactos.

    @Test
    fun `por defecto el resplandor sigue siendo un solo color solido en modo Normal`() {
        val params = ImageEffectsParams()
        assertEquals(android.graphics.Color.WHITE, params.glowColor2)
        assertFalse(params.glowGradientEnabled)
        assertEquals(GlowBlendMode.NORMAL, params.glowBlendMode)
    }

    @Test
    fun `degradado o blend mode de resplandor solos, sin intensidad, siguen siendo neutral`() {
        val params = ImageEffectsParams(
            glowGradientEnabled = true,
            glowColor2 = android.graphics.Color.CYAN,
            glowBlendMode = GlowBlendMode.SCREEN
        )
        assertTrue(
            "glowGradientEnabled/glowColor2/glowBlendMode sin glowIntensity>0 no deben producir ningun efecto visible",
            params.isNeutral
        )
    }

    @Test
    fun `sanitized no altera glowColor2, glowGradientEnabled ni glowBlendMode`() {
        val params = ImageEffectsParams(
            glowIntensity = 0.7f,
            glowGradientEnabled = true,
            glowColor2 = android.graphics.Color.MAGENTA,
            glowBlendMode = GlowBlendMode.ADD
        ).sanitized()
        assertTrue(params.glowGradientEnabled)
        assertEquals(android.graphics.Color.MAGENTA, params.glowColor2)
        assertEquals(GlowBlendMode.ADD, params.glowBlendMode)
    }
}
