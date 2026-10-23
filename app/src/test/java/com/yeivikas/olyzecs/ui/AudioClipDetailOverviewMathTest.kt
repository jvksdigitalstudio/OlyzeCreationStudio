package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Matemática de la barra de vista general ([detailOverviewWindow] y compañeras): pura, sin Compose. */
class AudioClipDetailOverviewMathTest {
    private val viewport = 1_000f

    @Test
    fun window_at_1x_covers_the_whole_axis() {
        val w = detailOverviewWindow(scrollPx = 0f, viewportPx = viewport, zoom = 1f)!!
        assertEquals(0f, w.leftPx, 1e-4f)
        assertEquals(viewport, w.rightPx, 1e-4f)
    }

    @Test
    fun window_width_is_inversely_proportional_to_zoom() {
        assertEquals(500f, detailOverviewWindow(0f, viewport, 2f)!!.widthPx, 1e-3f)
        assertEquals(100f, detailOverviewWindow(0f, viewport, 10f)!!.widthPx, 1e-3f)
    }

    @Test
    fun window_follows_the_scroll_and_stays_inside() {
        // Zoom 4×: ancho zoomeado 4000 px; scroll 1500 → recuadro [375, 625].
        val w = detailOverviewWindow(1_500f, viewport, 4f)!!
        assertEquals(375f, w.leftPx, 1e-3f)
        assertEquals(625f, w.rightPx, 1e-3f)
        // Un scroll fuera de rango no saca el recuadro de la barra.
        val over = detailOverviewWindow(99_999f, viewport, 4f)!!
        assertEquals(viewport, over.rightPx, 1e-3f)
        assertEquals(0f, detailOverviewWindow(-50f, viewport, 4f)!!.leftPx, 1e-3f)
    }

    @Test
    fun invalid_scale_gives_no_window() {
        assertNull(detailOverviewWindow(0f, 0f, 2f))
        assertNull(detailOverviewWindow(0f, viewport, 0f))
    }

    @Test
    fun window_and_scroll_round_trip() {
        val zoom = 6.5f
        val scroll = 2_345f
        val w = detailOverviewWindow(scroll, viewport, zoom)!!
        assertEquals(scroll, detailOverviewMoveScrollPx(w.leftPx, 0f, viewport, zoom), 1e-2f)
    }

    @Test
    fun x_of_a_source_instant_maps_the_whole_file_onto_the_axis() {
        assertEquals(0f, detailOverviewX(0.0, 3_420.0, viewport), 1e-4f)
        assertEquals(500f, detailOverviewX(1_710.0, 3_420.0, viewport), 1e-3f)
        assertEquals(viewport, detailOverviewX(9_999.0, 3_420.0, viewport), 1e-4f)
        assertEquals(0f, detailOverviewX(10.0, 0.0, viewport), 0f)
    }

    // --- Agarre -------------------------------------------------------------------------------------------------

    private val wide = DetailOverviewWindow(300f, 700f)

    @Test
    fun grab_edges_when_resizable() {
        val start = detailOverviewGrab(310f, wide, edgeZonePx = 20f, minGrabPx = 44f, canResize = true)
        assertEquals(DetailOverviewTarget.START_EDGE, start.target)
        assertEquals(-10f, start.offsetPx, 1e-4f)
        val end = detailOverviewGrab(695f, wide, 20f, 44f, true)
        assertEquals(DetailOverviewTarget.END_EDGE, end.target)
        assertEquals(5f, end.offsetPx, 1e-4f)
    }

    @Test
    fun grab_body_keeps_the_grab_offset() {
        val g = detailOverviewGrab(500f, wide, 20f, 44f, true)
        assertEquals(DetailOverviewTarget.BODY, g.target)
        assertEquals(-200f, g.offsetPx, 1e-4f)
    }

    @Test
    fun no_edges_when_resize_is_not_possible_or_window_is_narrow() {
        assertEquals(DetailOverviewTarget.BODY, detailOverviewGrab(302f, wide, 20f, 44f, canResize = false).target)
        val narrow = DetailOverviewWindow(300f, 340f) // 40 < 3 × 20
        assertEquals(DetailOverviewTarget.BODY, detailOverviewGrab(302f, narrow, 20f, 44f, true).target)
    }

    @Test
    fun narrow_window_is_still_grabbable_with_the_minimum_touch_width() {
        val narrow = DetailOverviewWindow(500f, 504f)
        val near = detailOverviewGrab(515f, narrow, 20f, 44f, true) // dentro de ±22 del centro (502)
        assertEquals(500f - 515f, near.offsetPx, 1e-4f)
    }

    @Test
    fun touch_outside_centers_the_window_under_the_finger() {
        val g = detailOverviewGrab(900f, wide, 20f, 44f, true)
        assertEquals(DetailOverviewTarget.BODY, g.target)
        assertEquals(-200f, g.offsetPx, 1e-4f)
        // Aplicarlo en 900 coloca el recuadro [700, 1100] → se acota a [600, 1000].
        val scroll = detailOverviewMoveScrollPx(900f, g.offsetPx, viewport, zoom = 2.5f)
        val w = detailOverviewWindow(scroll, viewport, 2.5f)!!
        assertEquals(600f, w.leftPx, 1e-2f)
        assertEquals(1_000f, w.rightPx, 1e-2f)
    }

    // --- Mover y redimensionar ----------------------------------------------------------------------------------

    @Test
    fun move_keeps_zoom_and_clamps_to_the_bar() {
        val zoom = 4f // ancho del recuadro 250
        assertEquals(0f, detailOverviewMoveScrollPx(-500f, 0f, viewport, zoom), 1e-3f)
        val maxScroll = viewport * zoom - viewport
        assertEquals(maxScroll, detailOverviewMoveScrollPx(5_000f, 0f, viewport, zoom), 1e-2f)
        assertEquals(1_000f, detailOverviewMoveScrollPx(250f, 0f, viewport, zoom), 1e-2f) // 250 × 4
    }

    @Test
    fun resize_start_edge_keeps_the_right_edge_fixed() {
        val before = DetailOverviewWindow(300f, 700f)
        val view = detailOverviewResize(DetailOverviewTarget.START_EDGE, 500f, 0f, before, viewport, maxZoom = 100f)
        val after = detailOverviewWindow(view.scrollPx, viewport, view.zoom)!!
        assertEquals(500f, after.leftPx, 1e-2f)
        assertEquals(700f, after.rightPx, 1e-2f)
        assertEquals(5f, view.zoom, 1e-3f)
    }

    @Test
    fun resize_end_edge_keeps_the_left_edge_fixed() {
        val before = DetailOverviewWindow(300f, 700f)
        val view = detailOverviewResize(DetailOverviewTarget.END_EDGE, 400f, 0f, before, viewport, maxZoom = 100f)
        val after = detailOverviewWindow(view.scrollPx, viewport, view.zoom)!!
        assertEquals(300f, after.leftPx, 1e-2f)
        assertEquals(400f, after.rightPx, 1e-2f)
        assertEquals(10f, view.zoom, 1e-3f)
    }

    @Test
    fun resize_outward_zooms_out_and_stops_at_1x() {
        val before = DetailOverviewWindow(300f, 700f)
        val view = detailOverviewResize(DetailOverviewTarget.START_EDGE, -400f, 0f, before, viewport, maxZoom = 100f)
        assertEquals(700f / viewport, 1f / view.zoom, 1e-3f) // [0, 700] → zoom 1/0.7
        val full = detailOverviewResize(DetailOverviewTarget.END_EDGE, 5_000f, 0f, DetailOverviewWindow(0f, 700f), viewport, 100f)
        assertEquals(1f, full.zoom, 1e-4f)
        assertEquals(0f, full.scrollPx, 1e-4f)
    }

    @Test
    fun resize_is_limited_by_the_max_zoom() {
        val before = DetailOverviewWindow(300f, 700f)
        val view = detailOverviewResize(DetailOverviewTarget.END_EDGE, 300f, 0f, before, viewport, maxZoom = 8f)
        assertEquals(8f, view.zoom, 1e-3f)
        val w = detailOverviewWindow(view.scrollPx, viewport, view.zoom)!!
        assertEquals(300f, w.leftPx, 1e-2f)
        assertTrue(w.widthPx >= viewport / 8f - 1e-3f)
        assertNotNull(w)
    }

    @Test(expected = IllegalArgumentException::class)
    fun resize_rejects_the_body_target() {
        detailOverviewResize(DetailOverviewTarget.BODY, 0f, 0f, wide, viewport, 10f)
    }

    // ---- Agarraderas de arista: fuera del recuadro, pegadas al marco ----

    @Test
    fun grips_sit_outside_the_frame_flush_against_it() {
        val g = detailOverviewGripLefts(leftPx = 300f, rightPx = 700f, gripWidthPx = 4f, viewportPx = viewport, gutterPx = 16f)
        // Izquierda: ocupa [296, 300] (termina donde empieza el marco); derecha: [700, 704] (empieza donde acaba).
        assertEquals(296f, g.startLeftPx, 1e-4f)
        assertEquals(300f, g.startLeftPx + 4f, 1e-4f)
        assertEquals(700f, g.endLeftPx, 1e-4f)
    }

    @Test
    fun grips_never_overlap_the_framed_waveform() {
        val w = detailOverviewWindow(1_500f, viewport, 4f)!!
        val g = detailOverviewGripLefts(w.leftPx, w.rightPx, 4f, viewport, 16f)
        assertTrue(g.startLeftPx + 4f <= w.leftPx + 1e-4f)
        assertTrue(g.endLeftPx >= w.rightPx - 1e-4f)
    }

    @Test
    fun grips_at_the_file_edges_stay_inside_the_side_margin() {
        // Recuadro a todo el ancho (zoom 1×): las asas caen en el margen lateral, no se recortan.
        val g = detailOverviewGripLefts(0f, viewport, 4f, viewport, gutterPx = 16f)
        assertEquals(-4f, g.startLeftPx, 1e-4f)
        assertEquals(viewport, g.endLeftPx, 1e-4f)
        assertTrue(g.startLeftPx >= -16f)
        assertTrue(g.endLeftPx + 4f <= viewport + 16f)
    }

    @Test
    fun grips_are_clamped_to_the_canvas_with_a_tiny_margin() {
        val g = detailOverviewGripLefts(0f, viewport, gripWidthPx = 4f, viewportPx = viewport, gutterPx = 2f)
        assertEquals(-2f, g.startLeftPx, 1e-4f)
        assertEquals(viewport - 2f, g.endLeftPx, 1e-4f)
    }
}
