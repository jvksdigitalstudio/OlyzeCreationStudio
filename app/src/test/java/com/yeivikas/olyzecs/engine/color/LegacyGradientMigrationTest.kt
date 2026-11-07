package com.yeivikas.olyzecs.engine.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyGradientMigrationTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun gradientRow(id: String, start: Int = red, end: Int = blue, angle: Float = 90f, radial: Boolean = false) =
        LegacyGradientRow(id, useGradient = true, startArgb = start, endArgb = end, angleDegrees = angle, isRadial = radial)

    private fun plainRow(id: String) =
        LegacyGradientRow(id, useGradient = false, startArgb = null, endArgb = null, angleDegrees = 90f, isRadial = false)

    private fun counter(): () -> String {
        var next = 0
        return { "g${next++}" }
    }

    @Test
    fun `dos filas consecutivas con el mismo degradado fijan los tonos que se veian`() {
        val result = LegacyGradientMigration.migrate(listOf(gradientRow("a"), gradientRow("b")), counter())
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertEquals(listOf(0xFFC6496D.toInt(), 0xFF5147D2.toInt()), result.map { it.sampledArgb })
        assertEquals(setOf("g0"), result.map { it.group.id }.toSet())
        assertTrue(result.all { it.group.space == GradientColorSpace.PERCEPTUAL })
    }

    @Test
    fun `tres filas usan las posiciones centradas de la regla antigua`() {
        val result = LegacyGradientMigration.migrate(listOf(gradientRow("a"), gradientRow("b"), gradientRow("c")), counter())
        assertEquals(
            listOf(0xFFD93F57.toInt(), 0xFF8C53A2.toInt(), 0xFF3B3DE1.toInt()),
            result.map { it.sampledArgb }
        )
    }

    @Test
    fun `una fila suelta con degradado propio no se migra`() {
        val result = LegacyGradientMigration.migrate(listOf(plainRow("x"), gradientRow("a"), plainRow("y")), counter())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filas no consecutivas no forman grupo`() {
        val result = LegacyGradientMigration.migrate(listOf(gradientRow("a"), plainRow("x"), gradientRow("b")), counter())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `degradados distintos consecutivos forman grupos separados`() {
        val rows = listOf(
            gradientRow("a"), gradientRow("b"),
            gradientRow("c", angle = 45f), gradientRow("d", angle = 45f)
        )
        val result = LegacyGradientMigration.migrate(rows, counter())
        assertEquals(listOf("g0", "g0", "g1", "g1"), result.map { it.group.id })
    }

    @Test
    fun `una fila con degradado activo pero sin extremos corta la racha`() {
        val broken = LegacyGradientRow("m", useGradient = true, startArgb = null, endArgb = null, angleDegrees = 90f, isRadial = false)
        val result = LegacyGradientMigration.migrate(listOf(gradientRow("a"), broken, gradientRow("b")), counter())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `sin filas no hay nada que migrar`() {
        assertTrue(LegacyGradientMigration.migrate(emptyList(), counter()).isEmpty())
    }
}
