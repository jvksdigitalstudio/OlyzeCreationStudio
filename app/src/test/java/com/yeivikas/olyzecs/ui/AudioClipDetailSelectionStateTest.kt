package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Estado de la selección de rangos ([AudioClipDetailSelectionState]): arrastre, ajuste de borde, cantidad máxima
 * (x1 … x7), toque y doble toque. Las reglas puras que usa se prueban aparte (`…SelectionMathTest`, `…SelectionSetMathTest`).
 *
 * Escala de los gestos: `pxPerMs = 0.5` sin desplazamiento, así que X = ms / 2 (X=400 → 800 ms) y el archivo mide 3420 ms.
 */
class AudioClipDetailSelectionStateTest {

    private val total = 3_420.0

    private fun state() = AudioClipDetailSelectionState()

    /** Arrastra con un dedo de [fromX] a [toX] (X del eje de tiempo). */
    private fun AudioClipDetailSelectionState.select(fromX: Float, toX: Float) {
        beginDrag(downAxisXPx = fromX, scrollPx = 0f, pxPerMs = 0.5f, totalMs = total, edgeHitHalfWidthPx = 22f)
        drag(axisXPx = toX, scrollPx = 0f, pxPerMs = 0.5f, totalMs = total)
    }

    private val first = DetailSelection(800.0, 1_200.0)   // X 400 … 600
    private val second = DetailSelection(2_400.0, 2_800.0) // X 1200 … 1400

    // --- Un rango (x1 por defecto) -------------------------------------------------------------------------------

    @Test
    fun `la cantidad maxima arranca en x1 y sin rangos`() {
        val s = state()
        assertEquals(DETAIL_SELECT_DEFAULT_COUNT, s.maxCount)
        assertTrue(s.ranges.isEmpty())
    }

    @Test
    fun `un arrastre nuevo crea el rango desde donde se apoyo el dedo`() {
        val s = state()
        s.select(400f, 1_000f)
        assertEquals(listOf(DetailSelection(800.0, 2_000.0)), s.ranges)
    }

    @Test
    fun `agarrar un borde lo mueve sin saltar y deja fijo el opuesto`() {
        val s = state()
        s.select(400f, 1_000f) // 800 … 2000
        // Se agarra el inicio 10 px a su derecha (X=410) y se lleva a X=610: el borde debe ir a X=600 (1200 ms).
        s.select(410f, 610f)
        assertEquals(listOf(DetailSelection(1_200.0, 2_000.0)), s.ranges)
    }

    @Test
    fun `con el cupo lleno un arrastre lejos de los bordes no crea otro rango`() {
        val s = state() // x1
        s.select(400f, 600f)
        s.select(1_200f, 1_400f)
        assertEquals(listOf(first), s.ranges)
    }

    @Test
    fun `con el cupo lleno si se puede ajustar el borde de un rango existente`() {
        val s = state() // x1
        s.select(400f, 1_000f) // 800 … 2000
        s.select(1_000f, 1_200f) // agarra el fin y lo lleva a 2400
        assertEquals(listOf(DetailSelection(800.0, 2_400.0)), s.ranges)
    }

    // --- Varios rangos (x2 … x7) ---------------------------------------------------------------------------------

    @Test
    fun `con x2 se pueden crear dos rangos y quedan ordenados`() {
        val s = state()
        s.changeMaxCount(2)
        s.select(1_200f, 1_400f) // el de la derecha primero
        s.select(400f, 600f)
        assertEquals(listOf(first, second), s.ranges)
    }

    @Test
    fun `con x2 un tercer rango no se crea`() {
        val s = state()
        s.changeMaxCount(2)
        s.select(400f, 600f)
        s.select(1_200f, 1_400f)
        s.select(900f, 1_000f) // hueco libre entre ambos, pero sin cupo
        assertEquals(listOf(first, second), s.ranges)
    }

    @Test
    fun `un rango nuevo no puede cruzar a otro y se detiene en su borde`() {
        val s = state()
        s.changeMaxCount(2)
        s.select(1_000f, 1_200f) // 2000 … 2400
        s.select(400f, 1_500f)   // el dedo llega a 3000 ms, pero el vecino empieza en 2000
        assertEquals(listOf(DetailSelection(800.0, 2_000.0), DetailSelection(2_000.0, 2_400.0)), s.ranges)
    }

    @Test
    fun `empezar un arrastre dentro de un rango existente no crea otro`() {
        val s = state()
        s.changeMaxCount(2)
        s.select(400f, 1_000f) // 800 … 2000
        s.select(700f, 900f)   // empieza dentro, lejos de los bordes
        assertEquals(listOf(DetailSelection(800.0, 2_000.0)), s.ranges)
    }

    @Test
    fun `ajustar un borde no puede invadir al rango vecino`() {
        val s = state()
        s.changeMaxCount(2)
        s.select(400f, 600f)
        s.select(1_200f, 1_400f)
        s.select(1_200f, 300f) // agarra el inicio del segundo y lo lleva más allá del final del primero
        assertEquals(listOf(first, DetailSelection(1_200.0, 2_800.0)), s.ranges)
    }

    // --- Cantidad máxima ------------------------------------------------------------------------------------------

    @Test
    fun `la cantidad maxima se acota a x1 y x7`() {
        val s = state()
        s.changeMaxCount(99)
        assertEquals(DETAIL_SELECT_MAX_COUNT, s.maxCount)
        s.changeMaxCount(0)
        assertEquals(DETAIL_SELECT_MIN_COUNT, s.maxCount)
        s.changeMaxCount(-3)
        assertEquals(DETAIL_SELECT_MIN_COUNT, s.maxCount)
    }

    @Test
    fun `al bajar la cantidad se conservan los primeros rangos de izquierda a derecha`() {
        val s = state()
        s.changeMaxCount(3)
        s.select(200f, 300f)   // 400 … 600
        s.select(500f, 600f)   // 1000 … 1200
        s.select(900f, 1_000f) // 1800 … 2000
        assertEquals(3, s.ranges.size)
        s.changeMaxCount(2)
        assertEquals(listOf(DetailSelection(400.0, 600.0), DetailSelection(1_000.0, 1_200.0)), s.ranges)
        s.changeMaxCount(1)
        assertEquals(listOf(DetailSelection(400.0, 600.0)), s.ranges)
    }

    @Test
    fun `subir la cantidad no toca los rangos que ya hay`() {
        val s = state()
        s.select(400f, 600f)
        s.changeMaxCount(4)
        assertEquals(listOf(first), s.ranges)
    }

    // --- Toque, doble toque, reflejo y descarte --------------------------------------------------------------------

    @Test
    fun `un toque deselecciona todos los rangos y un doble toque los restituye`() {
        val s = state()
        s.changeMaxCount(2)
        s.select(400f, 600f)
        s.select(1_200f, 1_400f)
        s.clearForTap()
        assertTrue(s.ranges.isEmpty())
        s.restoreAfterDoubleTap()
        assertEquals(listOf(first, second), s.ranges)
    }

    @Test
    fun `un doble toque sin seleccion previa no inventa ninguna`() {
        val s = state()
        s.clearForTap()
        s.restoreAfterDoubleTap()
        assertTrue(s.ranges.isEmpty())
    }

    @Test
    fun `restituir dos veces no resucita una seleccion borrada despues`() {
        val s = state()
        s.select(400f, 1_000f)
        s.clearForTap()
        s.restoreAfterDoubleTap()
        s.clearForTap()
        s.clearForTap() // segundo toque simple: ya no había nada que restituir
        s.restoreAfterDoubleTap()
        assertTrue(s.ranges.isEmpty())
    }

    @Test
    fun `descartar tras una accion no deja nada que restituir`() {
        val s = state()
        s.select(400f, 1_000f)
        s.clearForTap()
        s.clear()
        s.restoreAfterDoubleTap()
        assertTrue(s.ranges.isEmpty())
    }

    @Test
    fun `reflejar con REV refleja todos los rangos y los deja ordenados`() {
        val s = state()
        s.changeMaxCount(2)
        s.select(400f, 600f)
        s.select(1_200f, 1_400f)
        s.mirror(total)
        assertEquals(listOf(DetailSelection(620.0, 1_020.0), DetailSelection(2_220.0, 2_620.0)), s.ranges)
    }
}
