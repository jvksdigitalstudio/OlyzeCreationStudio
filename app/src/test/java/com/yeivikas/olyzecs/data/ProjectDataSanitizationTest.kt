package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.engine.timeline.TimelineLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 1 (AUDITORÍA P0/P1) — test OBLIGATORIO de esta fase: [sanitizeProjectData]
 * satura valores numéricos/estructurales de un `project.json` que podría
 * venir corrupto o manipulado a mano, para que no produzcan crashes ni
 * estados imposibles (ver KDoc de la función para el detalle de cada campo).
 *
 * Corre como JVM unit test puro — [ProjectData]/[LayerData] son
 * `@Serializable` sin ninguna dependencia de Android (ver
 * [ProjectDataSerializationTest] para el mismo criterio ya establecido).
 */
class ProjectDataSanitizationTest {

    private fun baseProject(layers: List<LayerData> = emptyList()) = ProjectData(
        id = "p1",
        name = "Test",
        createdAtMs = 0L,
        updatedAtMs = 0L,
        layers = layers
    )

    @Test
    fun `un proyecto ya valido queda exactamente igual tras sanear`() {
        val valid = baseProject(
            layers = listOf(
                LayerData(id = "l1", imageFileName = "l1.png", name = "Capa 1", zIndex = 0, parallaxFactor = 1f)
            )
        ).copy(fps = 30, projectDurationMs = 8_000L, gridColumns = 3, gridRows = 3)

        val sanitized = sanitizeProjectData(valid)

        assertEquals(valid, sanitized)
    }

    @Test
    fun `fps fuera de rango se satura a un rango razonable`() {
        assertEquals(1, sanitizeProjectData(baseProject().copy(fps = 0)).fps)
        assertEquals(1, sanitizeProjectData(baseProject().copy(fps = -50)).fps)
        assertEquals(240, sanitizeProjectData(baseProject().copy(fps = 999_999)).fps)
        assertEquals(30, sanitizeProjectData(baseProject().copy(fps = 30)).fps)
    }

    @Test
    fun `duracion negativa o absurda se satura dentro de limites del timeline`() {
        val negativo = sanitizeProjectData(baseProject().copy(projectDurationMs = -5_000L))
        assertTrue(negativo.projectDurationMs >= 1_000L)

        val absurda = sanitizeProjectData(baseProject().copy(projectDurationMs = Long.MAX_VALUE))
        assertEquals(TimelineLimits.MAX_DURATION_MS, absurda.projectDurationMs)
    }

    @Test
    fun `playhead se recorta para nunca superar la duracion saneada`() {
        val sanitized = sanitizeProjectData(
            baseProject().copy(projectDurationMs = 5_000L, playheadMs = 999_999L)
        )
        assertEquals(5_000L, sanitized.playheadMs)
        assertTrue(sanitized.playheadMs <= sanitized.projectDurationMs)
    }

    @Test
    fun `columnas y filas de la cuadricula en cero no producen division por cero mas adelante`() {
        val sanitized = sanitizeProjectData(baseProject().copy(gridColumns = 0, gridRows = -3))
        assertTrue(sanitized.gridColumns >= 1)
        assertTrue(sanitized.gridRows >= 1)
    }

    @Test
    fun `angulo de degradado NaN o infinito de una capa se reemplaza por un valor finito`() {
        val layer = LayerData(
            id = "l1",
            imageFileName = "l1.png",
            name = "Capa",
            zIndex = 0,
            gradientAngleDegrees = Float.NaN
        )
        val sanitized = sanitizeProjectData(baseProject(listOf(layer)))
        val angle = sanitized.layers.single().gradientAngleDegrees
        assertTrue("El ángulo saneado debe ser finito, fue $angle", angle != null && angle.isFinite())

        val infinito = sanitizeProjectData(
            baseProject(listOf(layer.copy(gradientAngleDegrees = Float.POSITIVE_INFINITY)))
        )
        assertTrue(infinito.layers.single().gradientAngleDegrees!!.isFinite())
    }

    @Test
    fun `colorIndex negativo de una capa se satura a cero`() {
        val layer = LayerData(id = "l1", imageFileName = "l1.png", name = "Capa", zIndex = 0, colorIndex = -7)
        val sanitized = sanitizeProjectData(baseProject(listOf(layer)))
        assertEquals(0, sanitized.layers.single().colorIndex)
    }

    @Test
    fun `zIndex y parallaxFactor absurdos de una capa quedan acotados`() {
        val layer = LayerData(
            id = "l1",
            imageFileName = "l1.png",
            name = "Capa",
            zIndex = Int.MAX_VALUE,
            parallaxFactor = Float.NaN
        )
        val sanitized = sanitizeProjectData(baseProject(listOf(layer))).layers.single()
        assertTrue(sanitized.zIndex <= 100_000)
        assertTrue(sanitized.parallaxFactor.isFinite())
    }
}
