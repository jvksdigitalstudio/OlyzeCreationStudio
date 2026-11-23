package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reglas del CONJUNTO de rangos seleccionados ([AudioClipDetailSelectionSetMath.kt]): puras, sin Compose. */
class AudioClipDetailSelectionSetMathTest {

    private val total = 3_420.0
    private val a = DetailSelection(100.0, 200.0)
    private val b = DetailSelection(1_000.0, 2_000.0)

    // --- Hueco libre ---------------------------------------------------------------------------------------------

    @Test
    fun `sin rangos el hueco es el archivo entero`() {
        assertEquals(DetailFreeSpan(0.0, total), detailFreeSpanAt(emptyList(), 500.0, total))
    }

    @Test
    fun `entre dos rangos el hueco va del fin del anterior al inicio del siguiente`() {
        assertEquals(DetailFreeSpan(200.0, 1_000.0), detailFreeSpanAt(listOf(a, b), 500.0, total))
    }

    @Test
    fun `antes del primero y despues del ultimo el hueco llega al extremo del archivo`() {
        assertEquals(DetailFreeSpan(0.0, 100.0), detailFreeSpanAt(listOf(a, b), 50.0, total))
        assertEquals(DetailFreeSpan(2_000.0, total), detailFreeSpanAt(listOf(a, b), 2_500.0, total))
    }

    @Test
    fun `dentro de un rango no hay hueco`() {
        assertNull(detailFreeSpanAt(listOf(a, b), 150.0, total))
    }

    @Test
    fun `sobre el borde de un rango el hueco queda a un solo lado`() {
        assertEquals(DetailFreeSpan(200.0, 1_000.0), detailFreeSpanAt(listOf(a, b), 200.0, total)) // fin de a
        assertEquals(DetailFreeSpan(0.0, 100.0), detailFreeSpanAt(listOf(a, b), 100.0, total))     // inicio de a
    }

    @Test
    fun `con duracion no valida no hay hueco`() {
        assertNull(detailFreeSpanAt(emptyList(), 10.0, 0.0))
    }

    // --- Borde agarrado entre todos los rangos -------------------------------------------------------------------

    // pxPerMs 0.5, sin desplazamiento: b va de X=500 a X=1000.

    @Test
    fun `se agarra el borde de cualquier rango y se informa de cual`() {
        val far = DetailSelection(3_000.0, 3_200.0) // X=1500 y X=1600
        assertEquals(DetailEdgeGrab(0, DetailSelectionEdge.END), detailSelectionEdgeHitIn(990f, listOf(b, far), 0f, 0.5f, 22f))
        assertEquals(DetailEdgeGrab(1, DetailSelectionEdge.START), detailSelectionEdgeHitIn(1_510f, listOf(b, far), 0f, 0.5f, 22f))
    }

    @Test
    fun `lejos de todos los bordes no se agarra nada`() {
        assertNull(detailSelectionEdgeHitIn(1_200f, listOf(a, b), 0f, 0.5f, 22f))
        assertNull(detailSelectionEdgeHitIn(500f, emptyList(), 0f, 0.5f, 22f))
    }

    @Test
    fun `entre dos bordes cercanos de rangos distintos gana el mas cercano al dedo`() {
        val first = DetailSelection(1_000.0, 1_100.0)   // fin en X=550
        val second = DetailSelection(1_120.0, 1_300.0)  // inicio en X=560
        assertEquals(DetailEdgeGrab(1, DetailSelectionEdge.START), detailSelectionEdgeHitIn(556f, listOf(first, second), 0f, 0.5f, 22f))
        assertEquals(DetailEdgeGrab(0, DetailSelectionEdge.END), detailSelectionEdgeHitIn(551f, listOf(first, second), 0f, 0.5f, 22f))
    }

    // --- Orden, espejo, límite y recorte -------------------------------------------------------------------------

    @Test
    fun `el rango arrastrado se inserta en su lugar por orden de inicio`() {
        val c = DetailSelection(3_000.0, 3_100.0)
        val dragged = DetailSelection(1_000.0, 1_500.0)
        assertEquals(listOf(a, dragged, c), detailSelectionsWith(listOf(a, c), dragged))
        assertEquals(listOf(a, c), detailSelectionsWith(listOf(a, c), null))
    }

    @Test
    fun `el espejo refleja cada rango y reordena el conjunto`() {
        assertEquals(
            listOf(DetailSelection(1_420.0, 2_420.0), DetailSelection(3_220.0, 3_320.0)),
            mirrorDetailSelections(listOf(a, b), total)
        )
    }

    @Test
    fun `el espejo del espejo devuelve el conjunto original`() {
        assertEquals(listOf(a, b), mirrorDetailSelections(mirrorDetailSelections(listOf(a, b), total), total))
    }

    @Test
    fun `al bajar la cantidad se conservan los primeros de izquierda a derecha`() {
        val c = DetailSelection(3_000.0, 3_100.0)
        assertEquals(listOf(a, b), limitDetailSelections(listOf(a, b, c), 2))
        assertEquals(listOf(a), limitDetailSelections(listOf(a, b, c), 1))
        assertEquals(listOf(a, b, c), limitDetailSelections(listOf(a, b, c), 7))
    }

    @Test
    fun `recorte actua sobre todos los rangos de largo suficiente que suenan en el clip`() {
        val all = 0.0 to 10_000.0
        fun trim(list: List<DetailSelection>, min: Double, window: Pair<Double, Double> = all) =
            detailTrimSelections(list, min, window.first, window.second)
        // Uno solo, justo en el minimo.
        assertEquals(listOf(DetailSelection(0.0, 100.0)), trim(listOf(DetailSelection(0.0, 100.0)), 100.0))
        // Varios: el boton se habilita con ellos (antes quedaba apagado).
        assertEquals(listOf(a, b), trim(listOf(a, b), 10.0))
        // Los demasiado cortos se descartan sin invalidar a los demas, y el orden se conserva.
        assertEquals(listOf(b), trim(listOf(a, b), 500.0))
        // Nada que recortar.
        assertEquals(emptyList<DetailSelection>(), trim(listOf(DetailSelection(0.0, 99.0)), 100.0))
        assertEquals(emptyList<DetailSelection>(), trim(emptyList(), 10.0))
    }

    @Test
    fun `recorte ignora lo que se selecciono fuera de lo que suena en el clip abierto`() {
        // El clip solo usa [1000, 2000): `a` (100-200) cae en la parte de otro trozo del mismo archivo.
        val window = 1_000.0 to 2_000.0
        assertEquals(listOf(b), detailTrimSelections(listOf(a, b), 10.0, window.first, window.second))
        // Un rango que apenas roza el tramo no alcanza el minimo: cuenta solo la parte que suena.
        assertEquals(
            emptyList<DetailSelection>(),
            detailTrimSelections(listOf(DetailSelection(900.0, 1_050.0)), 100.0, window.first, window.second)
        )
        assertEquals(
            listOf(DetailSelection(900.0, 1_150.0)),
            detailTrimSelections(listOf(DetailSelection(900.0, 1_150.0)), 100.0, window.first, window.second)
        )
    }
}
