package com.yeivikas.olyzecs.engine.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests puros (sin Bitmap/Canvas, corren con JUnit normal) para
 * [ImageEffects.angleDegToOffsetUnitVector] — la función que reemplaza
 * el `cos`/`sin` inline que antes calculaba mal la dirección de la
 * sombra/light wrap.
 *
 * Bug real corregido: con la fórmula vieja (0°=derecha), el default de
 * 135° (documentado como "la sombra clásica abajo-derecha") en realidad
 * daba abajo-IZQUIERDA. Estos tests fijan la convención correcta
 * (0°=arriba, sentido horario, igual que el dial de "Luz global") para
 * que no se pueda reintroducir por accidente.
 */
class ImageEffectsAngleTest {

    private fun assertRight(dx: Float) = assertTrue("esperaba dx>0 (derecha), fue $dx", dx > 0f)
    private fun assertLeft(dx: Float) = assertTrue("esperaba dx<0 (izquierda), fue $dx", dx < 0f)
    private fun assertDown(dy: Float) = assertTrue("esperaba dy>0 (abajo), fue $dy", dy > 0f)
    private fun assertUp(dy: Float) = assertTrue("esperaba dy<0 (arriba), fue $dy", dy < 0f)

    @Test
    fun `0 grados apunta hacia arriba`() {
        val (dx, dy) = ImageEffects.angleDegToOffsetUnitVector(0f)
        assertEquals(0f, dx, 1e-4f)
        assertUp(dy)
    }

    @Test
    fun `90 grados apunta hacia la derecha`() {
        val (dx, dy) = ImageEffects.angleDegToOffsetUnitVector(90f)
        assertRight(dx)
        assertEquals(0f, dy, 1e-4f)
    }

    @Test
    fun `180 grados apunta hacia abajo`() {
        val (dx, dy) = ImageEffects.angleDegToOffsetUnitVector(180f)
        assertEquals(0f, dx, 1e-4f)
        assertDown(dy)
    }

    @Test
    fun `270 grados apunta hacia la izquierda`() {
        val (dx, dy) = ImageEffects.angleDegToOffsetUnitVector(270f)
        assertLeft(dx)
        assertEquals(0f, dy, 1e-4f)
    }

    @Test
    fun `135 grados, el default de sombra, ahora si da abajo-derecha como promete el comentario`() {
        val (dx, dy) = ImageEffects.angleDegToOffsetUnitVector(135f)
        assertRight(dx)
        assertDown(dy)
    }

    @Test
    fun `315 grados, el default de sombra de relleno, da arriba-derecha (opuesto a 135)`() {
        // fillShadowAngleDeg = 315° por defecto, documentado como
        // "aprox. opuesta a la principal en 135°" — verificamos que el
        // vector resultante es efectivamente el negado del de 135°.
        val (dx135, dy135) = ImageEffects.angleDegToOffsetUnitVector(135f)
        val (dx315, dy315) = ImageEffects.angleDegToOffsetUnitVector(315f)
        assertEquals(-dx135, dx315, 1e-4f)
        assertEquals(-dy135, dy315, 1e-4f)
    }

    @Test
    fun `el vector siempre es unitario`() {
        for (angle in 0..350 step 10) {
            val (dx, dy) = ImageEffects.angleDegToOffsetUnitVector(angle.toFloat())
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            assertEquals("angulo=$angle", 1f, len, 1e-4f)
        }
    }
}
