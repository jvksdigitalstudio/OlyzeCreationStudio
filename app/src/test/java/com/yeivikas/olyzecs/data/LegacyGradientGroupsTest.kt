package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.color.GradientColorSpace
import com.yeivikas.olyzecs.engine.scene.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migración al abrir proyectos con degradados multicapa del modelo anterior.
 * Solo con carriles de audio: son inmutables y no arrastran recursos de GPU
 * (la lógica de agrupación en sí está probada en `LegacyGradientMigrationTest`).
 */
class LegacyGradientGroupsTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val noLayers = emptyList<Layer>()

    private fun gradientTrack(id: String, order: Float) = AudioTrack(
        id = id,
        trackOrder = order,
        useGradientColor = true,
        customGradientStartArgb = red,
        customGradientEndArgb = blue
    )

    @Test
    fun `dos carriles consecutivos con el mismo degradado quedan en un grupo con color solido`() {
        val result = migrateLegacyGradientGroups(
            noLayers,
            listOf(gradientTrack("a", 1f), gradientTrack("b", 2f)),
            newGroupId = { "grupo" }
        )
        val (a, b) = result.audioTracks
        assertFalse(a.useGradientColor)
        assertFalse(b.useGradientColor)
        assertEquals(0xFFC6496D.toInt(), a.customColorArgb)
        assertEquals(0xFF5147D2.toInt(), b.customColorArgb)
        assertEquals("grupo", a.gradientGroup?.id)
        assertEquals(GradientColorSpace.PERCEPTUAL, a.gradientGroup?.space)
        assertEquals(red, a.customGradientStartArgb)
        assertEquals(blue, a.customGradientEndArgb)
    }

    @Test
    fun `un carril suelto con degradado propio no se toca`() {
        val tracks = listOf(gradientTrack("a", 1f))
        val result = migrateLegacyGradientGroups(noLayers, tracks)
        assertSame(tracks, result.audioTracks)
        assertTrue(result.audioTracks.single().useGradientColor)
        assertNull(result.audioTracks.single().gradientGroup)
    }

    @Test
    fun `la migracion es idempotente`() {
        val first = migrateLegacyGradientGroups(noLayers, listOf(gradientTrack("a", 1f), gradientTrack("b", 2f)))
        val second = migrateLegacyGradientGroups(noLayers, first.audioTracks)
        assertEquals(first.audioTracks, second.audioTracks)
    }

    @Test
    fun `el orden de playlist manda sobre el orden de la lista`() {
        val result = migrateLegacyGradientGroups(
            noLayers,
            listOf(gradientTrack("segundo", 2f), gradientTrack("primero", 1f))
        )
        val byId = result.audioTracks.associateBy { it.id }
        assertEquals(0xFFC6496D.toInt(), byId.getValue("primero").customColorArgb)
        assertEquals(0xFF5147D2.toInt(), byId.getValue("segundo").customColorArgb)
    }
}
