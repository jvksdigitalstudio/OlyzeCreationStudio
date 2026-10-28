package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vista previa en bucle de un clip: región, selección traducida al proyecto y resolución contra los carriles. El
 * confinamiento del cursor a los tramos se prueba en [PreviewLoopPlanTest].
 * Pura (sin `Uri`): los carriles se arman sin clips, igual que en `AudioTrackListTest`.
 */
class AudioClipPreviewTest {

    private val range = PreviewLoopRange(startMs = 5_000L, endMs = 7_000L)

    // ---- Región ----

    @Test
    fun `el largo de la region es fin menos inicio`() {
        assertEquals(2_000L, range.lengthMs)
    }

    @Test
    fun `una region invalida no se puede crear`() {
        assertThrows(IllegalArgumentException::class.java) { PreviewLoopRange(-1L, 10L) }
        assertThrows(IllegalArgumentException::class.java) { PreviewLoopRange(10L, 10L) }
        assertThrows(IllegalArgumentException::class.java) { PreviewLoopRange(10L, 5L) }
    }

    // ---- previewLoopRangeOf ----

    @Test
    fun `el tramo de un clip es su inicio y su largo en el proyecto`() {
        assertEquals(PreviewLoopRange(5_000L, 7_000L), previewLoopRangeOf(5_000L, 2_000L))
        assertEquals(PreviewLoopRange(0L, 3_420L), previewLoopRangeOf(0L, 3_420L))
    }

    @Test
    fun `un clip sin largo o con inicio negativo no tiene tramo`() {
        assertNull(previewLoopRangeOf(5_000L, 0L))
        assertNull(previewLoopRangeOf(5_000L, -10L))
        assertNull(previewLoopRangeOf(-1L, 2_000L))
    }

    // ---- resolvedIn ----

    private val tracks = listOf(AudioTrack(id = "t1", trackOrder = -1f))

    @Test
    fun `sin sesion no hay bucle`() {
        val sinSesion: AudioClipPreview? = null
        assertNull(sinSesion.resolvedIn(tracks))
    }

    @Test
    fun `con el bucle apagado no hay bucle aunque el clip exista`() {
        assertNull(AudioClipPreview("c1", loopEnabled = false).resolvedIn(tracks))
    }

    @Test
    fun `si el clip ya no existe no hay bucle`() {
        assertNull(AudioClipPreview("fantasma").resolvedIn(tracks))
        assertNull(AudioClipPreview("fantasma").resolvedIn(emptyList()))
    }

    @Test
    fun `la sesion nueva arranca con el bucle activado`() {
        assertTrue(AudioClipPreview("c1").loopEnabled)
    }

    // ---- Selección de rango: del archivo al tramo del proyecto ----

    private fun selectionRange(
        selStart: Double,
        selEnd: Double,
        trim: Long = 0L,
        start: Long = 5_000L,
        length: Long = 2_000L
    ) = previewSelectionRangeOf(start, length, trim, PreviewSourceSelection(selStart, selEnd))

    @Test
    fun `una seleccion dentro del clip se traduce al tiempo del proyecto`() {
        assertEquals(PreviewLoopRange(5_500L, 6_500L), selectionRange(500.0, 1_500.0))
    }

    @Test
    fun `el recorte del clip desplaza la seleccion`() {
        // El clip arranca en el ms 1000 del archivo: el ms 1500 del archivo cae 500 ms después del inicio del clip.
        assertEquals(PreviewLoopRange(5_500L, 6_500L), selectionRange(1_500.0, 2_500.0, trim = 1_000L))
    }

    @Test
    fun `una seleccion que sobresale por el inicio se queda con la parte que suena`() {
        assertEquals(PreviewLoopRange(5_000L, 5_500L), selectionRange(200.0, 1_500.0, trim = 1_000L))
    }

    @Test
    fun `una seleccion que sobresale por el final se queda con la parte que suena`() {
        assertEquals(PreviewLoopRange(6_500L, 7_000L), selectionRange(1_500.0, 9_000.0))
    }

    @Test
    fun `una seleccion fuera de lo que suena no da tramo`() {
        assertNull(selectionRange(3_000.0, 4_000.0))               // después del final del clip
        assertNull(selectionRange(0.0, 400.0, trim = 1_000L))       // antes del recorte
    }

    @Test
    fun `con un clip invalido no hay tramo de seleccion`() {
        assertNull(selectionRange(100.0, 200.0, length = 0L))
    }

    @Test
    fun `una seleccion invalida no se puede crear`() {
        assertThrows(IllegalArgumentException::class.java) { PreviewSourceSelection(-1.0, 10.0) }
        assertThrows(IllegalArgumentException::class.java) { PreviewSourceSelection(10.0, 10.0) }
        assertThrows(IllegalArgumentException::class.java) { PreviewSourceSelection(10.0, 5.0) }
    }

    @Test
    fun `sin seleccion la vista previa no trae ningun rango`() {
        assertTrue(AudioClipPreview("c1").selections.isEmpty())
    }
}
