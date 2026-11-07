package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioClipDetailSelectionMathTest {

    private val total = 3_420.0

    /** Arrastre sobre el archivo entero (`0 … total`), sin otros rangos que lo acoten. */
    private fun dragOverFile(anchorMs: Double, currentMs: Double, totalMs: Double) =
        detailSelectionFromDragIn(DetailFreeSpan(0.0, totalMs), anchorMs, currentMs)

    // --- Selección de un arrastre --------------------------------------------------------------------------------

    @Test
    fun `arrastrar a la derecha selecciona del ancla al dedo`() {
        assertEquals(DetailSelection(883.0, 2_579.0), dragOverFile(883.0, 2_579.0, total))
    }

    @Test
    fun `arrastrar a la izquierda tambien selecciona`() {
        assertEquals(DetailSelection(883.0, 2_579.0), dragOverFile(2_579.0, 883.0, total))
    }

    @Test
    fun `la seleccion se acota al archivo`() {
        assertEquals(DetailSelection(1_000.0, total), dragOverFile(1_000.0, 9_999.0, total))
        assertEquals(DetailSelection(0.0, 1_000.0), dragOverFile(1_000.0, -50.0, total))
    }

    @Test
    fun `si el dedo vuelve al ancla no hay seleccion`() {
        assertNull(dragOverFile(1_000.0, 1_000.0, total))
    }

    @Test
    fun `con duracion no valida no hay seleccion`() {
        assertNull(dragOverFile(0.0, 100.0, 0.0))
    }

    @Test
    fun `pasar el borde opuesto da la vuelta a la seleccion`() {
        // Se agarra el borde inicial de 1000..2000 (ancla = fin, 2000) y se lleva más allá del fin.
        assertEquals(DetailSelection(2_000.0, 3_000.0), dragOverFile(2_000.0, 3_000.0, total))
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

    // --- Arrastre acotado a un hueco -----------------------------------------------------------------------------

    @Test
    fun `un arrastre acotado a un hueco no sale de el`() {
        val span = DetailFreeSpan(1_000.0, 2_000.0)
        assertEquals(DetailSelection(1_200.0, 2_000.0), detailSelectionFromDragIn(span, 1_200.0, 3_000.0))
        assertEquals(DetailSelection(1_000.0, 1_200.0), detailSelectionFromDragIn(span, 1_200.0, 0.0))
    }

    @Test
    fun `un hueco vacio no da seleccion`() {
        assertNull(detailSelectionFromDragIn(DetailFreeSpan(500.0, 500.0), 500.0, 900.0))
    }
}
