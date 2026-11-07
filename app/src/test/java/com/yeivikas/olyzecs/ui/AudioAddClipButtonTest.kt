package com.yeivikas.olyzecs.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---------- Oclusión por ventanas flotantes (el botón queda DETRÁS) ----------

    private val button = Offset(100f, 50f) // esquina del botón en la ventana
    private val size = 36f

    @Test
    fun occluder_is_translated_into_the_button_local_space() {
        // Panel que cubre desde x=0 hasta x=110 (10 px del botón) a toda la altura de la fila.
        val local = addClipOccludersLocal(listOf(Rect(0f, 40f, 110f, 100f)), button, size)
        assertEquals(1, local.size)
        assertEquals(-100f, local[0].left, 1e-4f)
        assertEquals(10f, local[0].right, 1e-4f) // 110 - 100
        assertEquals(-10f, local[0].top, 1e-4f)  // 40 - 50
        assertEquals(50f, local[0].bottom, 1e-4f)
    }

    @Test
    fun occluders_that_do_not_touch_the_button_are_dropped() {
        val away = listOf(
            Rect(0f, 0f, 90f, 500f),      // a la izquierda
            Rect(140f, 0f, 400f, 500f),   // a la derecha
            Rect(0f, 0f, 500f, 40f),      // arriba
            Rect(0f, 90f, 500f, 200f)     // abajo
        )
        assertTrue(addClipOccludersLocal(away, button, size).isEmpty())
    }

    @Test
    fun degenerate_input_gives_no_occluders() {
        val r = listOf(Rect(0f, 0f, 500f, 500f))
        assertTrue(addClipOccludersLocal(emptyList(), button, size).isEmpty())
        assertTrue(addClipOccludersLocal(r, button, 0f).isEmpty())
        assertTrue(addClipOccludersLocal(listOf(Rect(110f, 60f, 110f, 90f)), button, size).isEmpty())
    }

    @Test
    fun state_tracks_reported_windows_and_forgets_removed_ones() {
        val state = AddClipState()
        val a = Any()
        val b = Any()
        assertTrue(state.occluders.isEmpty())
        state.reportOccluder(a, Rect(0f, 0f, 10f, 10f))
        state.reportOccluder(b, Rect(20f, 0f, 30f, 10f))
        assertEquals(2, state.occluders.size)
        // Actualizar una ventana reemplaza su rectángulo, no añade otro.
        state.reportOccluder(a, Rect(0f, 0f, 15f, 10f))
        assertEquals(2, state.occluders.size)
        assertTrue(state.occluders.contains(Rect(0f, 0f, 15f, 10f)))
        state.removeOccluder(a)
        assertEquals(listOf(Rect(20f, 0f, 30f, 10f)), state.occluders)
        // Quitar una ventana desconocida no hace nada.
        state.removeOccluder(Any())
        assertEquals(1, state.occluders.size)
    }

    @Test
    fun an_empty_report_removes_the_window() {
        // Un panel scrolleado fuera de la lista informa un rectángulo vacío: deja de tapar.
        val state = AddClipState()
        val key = Any()
        state.reportOccluder(key, Rect(0f, 0f, 10f, 10f))
        state.reportOccluder(key, Rect.Zero)
        assertTrue(state.occluders.isEmpty())
    }

    @Test
    fun reporting_the_same_bounds_again_does_not_replace_the_list() {
        val state = AddClipState()
        val key = Any()
        state.reportOccluder(key, Rect(0f, 0f, 10f, 10f))
        val before = state.occluders
        state.reportOccluder(key, Rect(0f, 0f, 10f, 10f))
        assertTrue(before === state.occluders) // sin cambio no se invalida a quien lo lee
    }

    @Test
    fun behind_windows_shape_compares_by_content() {
        val r = Rect(0f, 0f, 10f, 10f)
        assertEquals(AddClipBehindWindowsShape(listOf(r)), AddClipBehindWindowsShape(listOf(Rect(0f, 0f, 10f, 10f))))
        assertNotEquals(AddClipBehindWindowsShape(listOf(r)), AddClipBehindWindowsShape(listOf(Rect(0f, 0f, 11f, 10f))))
    }
}
