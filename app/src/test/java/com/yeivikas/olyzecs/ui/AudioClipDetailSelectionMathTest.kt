package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioClipDetailSelectionMathTest {

    private val total = 3_420.0

    // --- Selección de un arrastre --------------------------------------------------------------------------------

    @Test
    fun `arrastrar a la derecha selecciona del ancla al dedo`() {
        assertEquals(DetailSelection(883.0, 2_579.0), detailSelectionFromDrag(883.0, 2_579.0, total))
    }

    @Test
    fun `arrastrar a la izquierda tambien selecciona`() {
        assertEquals(DetailSelection(883.0, 2_579.0), detailSelectionFromDrag(2_579.0, 883.0, total))
    }

    @Test
    fun `la seleccion se acota al archivo`() {
        assertEquals(DetailSelection(1_000.0, total), detailSelectionFromDrag(1_000.0, 9_999.0, total))
        assertEquals(DetailSelection(0.0, 1_000.0), detailSelectionFromDrag(1_000.0, -50.0, total))
    }

    @Test
    fun `si el dedo vuelve al ancla no hay seleccion`() {
        assertNull(detailSelectionFromDrag(1_000.0, 1_000.0, total))
    }

    @Test
    fun `con duracion no valida no hay seleccion`() {
        assertNull(detailSelectionFromDrag(0.0, 100.0, 0.0))
    }

    @Test
    fun `pasar el borde opuesto da la vuelta a la seleccion`() {
        // Se agarra el borde inicial de 1000..2000 (ancla = fin, 2000) y se lleva más allá del fin.
        assertEquals(DetailSelection(2_000.0, 3_000.0), detailSelectionFromDrag(2_000.0, 3_000.0, total))
    }

    // --- Agarrar un borde ----------------------------------------------------------------------------------------

    private val selection = DetailSelection(1_000.0, 2_000.0)

    // pxPerMs 0.5 → inicio en X=500, fin en X=1000 (sin desplazamiento)

    @Test
    fun `un toque junto al borde inicial lo agarra`() {
        assertEquals(DetailSelectionEdge.START, detailSelectionEdgeHit(510f, selection, 0f, 0.5f, 22f))
    }

    @Test
    fun `un toque junto al borde final lo agarra`() {
        assertEquals(DetailSelectionEdge.END, detailSelectionEdgeHit(990f, selection, 0f, 0.5f, 22f))
    }

    @Test
    fun `un toque lejos de los bordes no agarra ninguno`() {
        assertNull(detailSelectionEdgeHit(700f, selection, 0f, 0.5f, 22f))
        assertNull(detailSelectionEdgeHit(100f, selection, 0f, 0.5f, 22f))
    }

    @Test
    fun `el limite de la zona de agarre es inclusivo`() {
        assertEquals(DetailSelectionEdge.START, detailSelectionEdgeHit(522f, selection, 0f, 0.5f, 22f))
        assertNull(detailSelectionEdgeHit(522.5f, selection, 0f, 0.5f, 22f))
    }

    @Test
    fun `el agarre tiene en cuenta el desplazamiento`() {
        // Con scroll 300 el inicio queda en X=200.
        assertEquals(DetailSelectionEdge.START, detailSelectionEdgeHit(205f, selection, 300f, 0.5f, 22f))
        assertNull(detailSelectionEdgeHit(505f, selection, 300f, 0.5f, 22f))
    }

    @Test
    fun `en una seleccion minima gana el borde mas cercano al dedo`() {
        val tiny = DetailSelection(1_000.0, 1_010.0) // X=500 y X=505
        assertEquals(DetailSelectionEdge.START, detailSelectionEdgeHit(502f, tiny, 0f, 0.5f, 22f))
        assertEquals(DetailSelectionEdge.END, detailSelectionEdgeHit(504f, tiny, 0f, 0.5f, 22f))
    }

    @Test
    fun `sin escala valida no se agarra ningun borde`() {
        assertNull(detailSelectionEdgeHit(500f, selection, 0f, 0f, 22f))
    }

    @Test
    fun `el borde y su opuesto`() {
        assertEquals(1_000.0, detailSelectionEdgeMs(selection, DetailSelectionEdge.START), 0.0)
        assertEquals(2_000.0, detailSelectionEdgeMs(selection, DetailSelectionEdge.END), 0.0)
        assertEquals(2_000.0, detailSelectionOppositeMs(selection, DetailSelectionEdge.START), 0.0)
        assertEquals(1_000.0, detailSelectionOppositeMs(selection, DetailSelectionEdge.END), 0.0)
    }

    // --- Estado completo (arrastre, ajuste de borde, toque y doble toque) ----------------------------------------

    private fun state() = AudioClipDetailSelectionState()

    @Test
    fun `un arrastre nuevo crea la seleccion desde donde se apoyo el dedo`() {
        val s = state()
        s.beginDrag(downAxisXPx = 400f, scrollPx = 0f, pxPerMs = 0.5f, totalMs = total, edgeHitHalfWidthPx = 22f)
        s.drag(1_000f, 0f, 0.5f, total)
        assertEquals(DetailSelection(800.0, 2_000.0), s.range)
    }

    @Test
    fun `agarrar un borde lo mueve sin saltar y deja fijo el opuesto`() {
        val s = state()
        s.beginDrag(400f, 0f, 0.5f, total, 22f)
        s.drag(1_000f, 0f, 0.5f, total) // 800..2000 (inicio en X=400, fin en X=1000)
        // Se agarra el inicio 10 px a su derecha (X=410) y se lleva a X=610: el borde debe ir a X=600 (1200 ms).
        s.beginDrag(410f, 0f, 0.5f, total, 22f)
        s.drag(610f, 0f, 0.5f, total)
        assertEquals(DetailSelection(1_200.0, 2_000.0), s.range)
    }

    @Test
    fun `un arrastre lejos de los bordes sustituye la seleccion`() {
        val s = state()
        s.beginDrag(400f, 0f, 0.5f, total, 22f)
        s.drag(600f, 0f, 0.5f, total) // 800..1200
        s.beginDrag(1_200f, 0f, 0.5f, total, 22f)
        s.drag(1_400f, 0f, 0.5f, total)
        assertEquals(DetailSelection(2_400.0, 2_800.0), s.range)
    }

    @Test
    fun `un toque deselecciona y un doble toque restituye la seleccion`() {
        val s = state()
        s.beginDrag(400f, 0f, 0.5f, total, 22f)
        s.drag(1_000f, 0f, 0.5f, total)
        val before = s.range
        s.clearForTap()
        assertNull(s.range)
        s.restoreAfterDoubleTap()
        assertEquals(before, s.range)
    }

    @Test
    fun `un doble toque sin seleccion previa no inventa ninguna`() {
        val s = state()
        s.clearForTap()
        s.restoreAfterDoubleTap()
        assertNull(s.range)
    }

    @Test
    fun `restituir dos veces no resucita una seleccion borrada despues`() {
        val s = state()
        s.beginDrag(400f, 0f, 0.5f, total, 22f)
        s.drag(1_000f, 0f, 0.5f, total)
        s.clearForTap()
        s.restoreAfterDoubleTap()
        s.clearForTap()
        s.clearForTap() // segundo toque simple: ya no había nada que restituir
        s.restoreAfterDoubleTap()
        assertNull(s.range)
    }
}
