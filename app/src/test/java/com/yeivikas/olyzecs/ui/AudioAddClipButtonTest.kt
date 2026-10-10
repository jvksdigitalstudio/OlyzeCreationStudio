package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Geometría pura del botón "+Clip" y conversión X del carril → tiempo. */
class AudioAddClipButtonTest {

    // ---------- addClipButtonLeftPx ----------

    @Test
    fun `el boton se centra en el punto tocado`() {
        assertEquals(400f, addClipButtonLeftPx(anchorPx = 436f, laneWidthPx = 1_000f, buttonWidthPx = 72f), 0.001f)
    }

    @Test
    fun `cerca del borde izquierdo no se sale del carril`() {
        assertEquals(0f, addClipButtonLeftPx(anchorPx = 10f, laneWidthPx = 1_000f, buttonWidthPx = 72f), 0.001f)
    }

    @Test
    fun `cerca del borde derecho no se sale del carril`() {
        assertEquals(928f, addClipButtonLeftPx(anchorPx = 995f, laneWidthPx = 1_000f, buttonWidthPx = 72f), 0.001f)
    }

    @Test
    fun `en un carril mas angosto que el boton se alinea a la izquierda`() {
        assertEquals(0f, addClipButtonLeftPx(anchorPx = 20f, laneWidthPx = 50f, buttonWidthPx = 72f), 0.001f)
    }

    // ---------- laneTimeMsAt ----------

    @Test
    fun `la X del carril se convierte a ms segun la escala`() {
        // 0,5 px por ms: 250 px = 500 ms.
        assertEquals(500L, laneTimeMsAt(xPx = 250f, pxPerMs = 0.5f, projectDurationMs = 10_000L))
    }

    @Test
    fun `sin escala no hay tiempo`() {
        assertNull(laneTimeMsAt(xPx = 250f, pxPerMs = 0f, projectDurationMs = 10_000L))
    }

    @Test
    fun `el tiempo se acota a la duracion del proyecto`() {
        assertEquals(10_000L, laneTimeMsAt(xPx = 99_999f, pxPerMs = 0.5f, projectDurationMs = 10_000L))
        assertEquals(0L, laneTimeMsAt(xPx = -30f, pxPerMs = 0.5f, projectDurationMs = 10_000L))
    }

    // ---------- addClipLaneIndexAtY / laneLocalX (arrastre entre carriles de audio) ----------

    // Dos carriles de audio de 36 px separados por capas de imagen (no son carriles de audio).
    private val laneA = AddClipLaneBounds(left = 130f, top = 100f, right = 1_130f, bottom = 136f)
    private val laneB = AddClipLaneBounds(left = 130f, top = 400f, right = 1_130f, bottom = 436f)

    @Test
    fun `el dedo sobre un carril de audio lo elige`() {
        assertEquals(0, addClipLaneIndexAtY(listOf(laneA, laneB), yPx = 110f))
        assertEquals(1, addClipLaneIndexAtY(listOf(laneA, laneB), yPx = 420f))
    }

    @Test
    fun `el dedo sobre una capa que no es de audio no elige ningun carril`() {
        assertNull(addClipLaneIndexAtY(listOf(laneA, laneB), yPx = 250f))
    }

    @Test
    fun `el borde superior cuenta y el inferior no`() {
        assertEquals(0, addClipLaneIndexAtY(listOf(laneA, laneB), yPx = 100f))
        assertNull(addClipLaneIndexAtY(listOf(laneA, laneB), yPx = 136f))
    }

    @Test
    fun `un carril aun sin medir se salta`() {
        assertEquals(1, addClipLaneIndexAtY(listOf(null, laneB), yPx = 420f))
        assertNull(addClipLaneIndexAtY(listOf(null, null), yPx = 420f))
    }

    @Test
    fun `la X del dedo se vuelve relativa al carril`() {
        assertEquals(370f, laneLocalX(xWindowPx = 500f, lane = laneA), 0.001f)
    }

    @Test
    fun `soltar mas alla de los bordes ubica al inicio o al final`() {
        assertEquals(0f, laneLocalX(xWindowPx = 20f, lane = laneA), 0.001f)
        assertEquals(1_000f, laneLocalX(xWindowPx = 5_000f, lane = laneA), 0.001f)
    }
}
