package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regla pura que decide si un toque completa un doble toque. */
class ImmediateTapGesturesTest {

    // Ventana típica de Android: mínimo 40 ms, máximo 300 ms, 120 px de tolerancia.
    private fun completesDoubleTap(
        previousUpMs: Long = 1_000L,
        downMs: Long = 1_150L,
        previousX: Float = 200f,
        downX: Float = 210f
    ) = isDoubleTap(
        previousUpMs = previousUpMs, previousX = previousX, previousY = 50f,
        downMs = downMs, downX = downX, downY = 55f,
        minIntervalMs = 40L, timeoutMs = 300L, slopPx = 120f
    )

    @Test
    fun `dos toques rapidos y cercanos son un doble toque`() = assertTrue(completesDoubleTap())

    @Test
    fun `sin toque previo no hay doble toque`() = assertFalse(completesDoubleTap(previousUpMs = -1L))

    @Test
    fun `un segundo toque tras vencer la ventana es un toque nuevo`() = assertFalse(completesDoubleTap(downMs = 1_400L))

    @Test
    fun `justo en el limite de la ventana todavia cuenta`() = assertTrue(completesDoubleTap(downMs = 1_300L))

    @Test
    fun `un rebote del dedo bajo el minimo no cuenta`() = assertFalse(completesDoubleTap(downMs = 1_020L))

    @Test
    fun `dos toques lejanos entre si no son un doble toque`() = assertFalse(completesDoubleTap(downX = 500f))

    @Test
    fun `la distancia se mide en los dos ejes`() {
        assertFalse(
            isDoubleTap(1_000L, 0f, 0f, 1_100L, 100f, 100f, minIntervalMs = 40L, timeoutMs = 300L, slopPx = 120f)
        )
    }
}
