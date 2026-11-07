package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Operaciones de selección de tiempo: planificación sobre geometría pura. */
class AudioTimeRangeOpsTest {

    private fun span(id: String, start: Long, length: Long) = ClipSpan(id, start, length)

    /** Resultado en forma comparable: (origen, desde, hasta, nuevoInicio, copia). */
    private fun plan(spans: List<ClipSpan>, op: TimeRangeOp, s: Long, e: Long) =
        planTimeRangeOp(spans, op, s, e).map { listOf(it.sourceId, it.fromOffsetMs, it.toOffsetMs, it.newStartMs, it.isCopy) }

    // ---------------- DELETE ----------------

    @Test
    fun `borrar dentro de un clip lo parte en dos y deja el hueco`() {
        // Clip A [0,10000); borrar [3000,5000)
        val r = plan(listOf(span("A", 0, 10_000)), TimeRangeOp.DELETE, 3_000, 5_000)
        assertEquals(listOf(listOf("A", 0L, 3_000L, 0L, false), listOf("A", 5_000L, 10_000L, 5_000L, false)), r)
    }

    @Test
    fun `borrar un clip entero contenido lo elimina`() {
        assertEquals(emptyList<List<Any>>(), plan(listOf(span("A", 4_000, 1_000)), TimeRangeOp.DELETE, 3_000, 6_000))
    }

    @Test
    fun `borrar no toca clips fuera del rango`() {
        val r = plan(listOf(span("A", 0, 1_000), span("B", 9_000, 1_000)), TimeRangeOp.DELETE, 3_000, 6_000)
        assertEquals(listOf(listOf("A", 0L, 1_000L, 0L, false), listOf("B", 0L, 1_000L, 9_000L, false)), r)
    }

    @Test
    fun `borrar recorta el borde derecho de un clip que entra en el rango`() {
        val r = plan(listOf(span("A", 0, 5_000)), TimeRangeOp.DELETE, 3_000, 8_000)
        assertEquals(listOf(listOf("A", 0L, 3_000L, 0L, false)), r)
    }

    @Test
    fun `borrar recorta el borde izquierdo de un clip que sale del rango`() {
        val r = plan(listOf(span("A", 4_000, 6_000)), TimeRangeOp.DELETE, 1_000, 6_000)
        assertEquals(listOf(listOf("A", 2_000L, 6_000L, 6_000L, false)), r)
    }

    // ---------------- DELETE_SPACE ----------------

    @Test
    fun `borrar espacio cierra el hueco desplazando lo posterior`() {
        // A [0,10000) ; B [12000, 14000) ; borrar espacio [3000,5000) (d=2000)
        val r = plan(listOf(span("A", 0, 10_000), span("B", 12_000, 2_000)), TimeRangeOp.DELETE_SPACE, 3_000, 5_000)
        assertEquals(
            listOf(
                listOf("A", 0L, 3_000L, 0L, false),
                listOf("A", 5_000L, 10_000L, 3_000L, false), // la cola de A queda pegada a su cabeza
                listOf("B", 0L, 2_000L, 10_000L, false)
            ),
            r
        )
    }

    @Test
    fun `borrar espacio deja intacto lo anterior a la seleccion`() {
        val r = plan(listOf(span("A", 0, 1_000)), TimeRangeOp.DELETE_SPACE, 5_000, 6_000)
        assertEquals(listOf(listOf("A", 0L, 1_000L, 0L, false)), r)
    }

    // ---------------- INSERT_SPACE ----------------

    @Test
    fun `insertar espacio parte el clip que cruza el inicio y corre la cola`() {
        // A [0,10000); insertar 2000 ms en 3000
        val r = plan(listOf(span("A", 0, 10_000)), TimeRangeOp.INSERT_SPACE, 3_000, 5_000)
        assertEquals(
            listOf(listOf("A", 0L, 3_000L, 0L, false), listOf("A", 3_000L, 10_000L, 5_000L, false)),
            r
        )
    }

    @Test
    fun `insertar espacio corre enteros los clips posteriores y no toca los anteriores`() {
        val r = plan(listOf(span("A", 0, 1_000), span("B", 8_000, 1_000)), TimeRangeOp.INSERT_SPACE, 3_000, 4_500)
        assertEquals(listOf(listOf("A", 0L, 1_000L, 0L, false), listOf("B", 0L, 1_000L, 9_500L, false)), r)
    }

    @Test
    fun `insertar espacio justo en el borde de un clip lo corre sin partirlo`() {
        val r = plan(listOf(span("B", 3_000, 1_000)), TimeRangeOp.INSERT_SPACE, 3_000, 4_000)
        assertEquals(listOf(listOf("B", 0L, 1_000L, 4_000L, false)), r)
    }

    // ---------------- DUPLICATE ----------------

    @Test
    fun `duplicar un clip contenido lo copia a continuacion de la seleccion`() {
        // B [3000,4000) dentro de la seleccion [2000,5000) (d=3000): copia en [6000,7000)
        val r = plan(listOf(span("B", 3_000, 1_000)), TimeRangeOp.DUPLICATE, 2_000, 5_000)
        assertEquals(
            listOf(listOf("B", 0L, 1_000L, 3_000L, false), listOf("B", 0L, 1_000L, 6_000L, true)),
            r
        )
    }

    @Test
    fun `duplicar corre lo posterior el largo de la seleccion`() {
        val r = plan(listOf(span("C", 6_000, 1_000)), TimeRangeOp.DUPLICATE, 2_000, 5_000)
        assertEquals(listOf(listOf("C", 0L, 1_000L, 9_000L, false)), r)
    }

    @Test
    fun `duplicar un clip que cruza el borde final parte el original y copia solo lo seleccionado`() {
        // A [0,8000); seleccion [2000,5000), d=3000.
        val r = plan(listOf(span("A", 0, 8_000)), TimeRangeOp.DUPLICATE, 2_000, 5_000)
        assertEquals(
            listOf(
                listOf("A", 0L, 5_000L, 0L, false),      // original hasta e
                listOf("A", 5_000L, 8_000L, 8_000L, false), // cola corrida d
                listOf("A", 2_000L, 5_000L, 5_000L, true)   // copia de [2000,5000) en [5000,8000)
            ),
            r
        )
    }

    @Test
    fun `la copia ocupa exactamente el hueco que abre la insercion`() {
        val r = planTimeRangeOp(listOf(span("A", 0, 8_000)), TimeRangeOp.DUPLICATE, 2_000, 5_000)
        val copy = r.single { it.isCopy }
        assertEquals(5_000L, copy.newStartMs)
        assertEquals(3_000L, copy.lengthMs)
    }

    // ---------------- TRIM ----------------

    @Test
    fun `recortar a seleccion conserva solo lo de dentro sin moverlo`() {
        val r = plan(listOf(span("A", 0, 10_000), span("B", 20_000, 1_000)), TimeRangeOp.TRIM_TO_SELECTION, 3_000, 5_000)
        assertEquals(listOf(listOf("A", 3_000L, 5_000L, 3_000L, false)), r)
    }

    // ---------------- Invariantes ----------------

    @Test
    fun `trozos residuales menores al minimo se descartan`() {
        // El clip sobrepasa el rango apenas 5 ms: ese resto no debe sobrevivir.
        val r = plan(listOf(span("A", 0, 5_005)), TimeRangeOp.DELETE, 0, 5_000)
        assertTrue(r.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rango vacio es invalido`() {
        planTimeRangeOp(emptyList(), TimeRangeOp.DELETE, 5_000, 5_000)
    }

    @Test
    fun `borrar y despues insertar el mismo largo es neutro para el largo total de audio`() {
        // Audio total ANTES = 10000; borrar espacio 2000 deja 8000.
        val spans = listOf(span("A", 0, 10_000))
        val after = planTimeRangeOp(spans, TimeRangeOp.DELETE_SPACE, 3_000, 5_000)
        assertEquals(8_000L, after.sumOf { it.lengthMs })
    }

    // ---------------- selectionAfter ----------------

    @Test
    fun `tras duplicar la seleccion pasa a la copia`() {
        assertEquals(AudioTimeSelection(5_000, 8_000), selectionAfter(TimeRangeOp.DUPLICATE, 2_000, 5_000))
    }

    @Test
    fun `tras borrar espacio no hay seleccion`() {
        assertNull(selectionAfter(TimeRangeOp.DELETE_SPACE, 2_000, 5_000))
    }

    @Test
    fun `tras el resto de operaciones la seleccion se conserva`() {
        for (op in listOf(TimeRangeOp.INSERT_SPACE, TimeRangeOp.DELETE, TimeRangeOp.TRIM_TO_SELECTION)) {
            assertEquals(AudioTimeSelection(2_000, 5_000), selectionAfter(op, 2_000, 5_000))
        }
    }

    // ---------------- sliceTrimStartMs ----------------

    @Test
    fun `rebanar dentro de la primera pasada suma el recorte`() {
        assertEquals(3_500L, sliceTrimStartMs(10_000L, 2_000L, true, 1_500L))
    }

    @Test
    fun `rebanar tras la vuelta del loop continua desde el frame correcto`() {
        // Archivo 1000, trim 200, loop: a los 1700 ms (raw 1900) -> 900.
        assertEquals(900L, sliceTrimStartMs(1_000L, 200L, true, 1_700L))
    }

    @Test
    fun `rebanar la cola muda de un clip sin loop da null`() {
        assertNull(sliceTrimStartMs(1_000L, 0L, false, 1_500L))
    }
}

/** Rejilla musical: paso exacto y snap. */
class AudioGridSnapTest {

    @Test
    fun `a 120 BPM un pulso dura 500 ms y la semicorchea 125`() {
        assertEquals(500.0, gridStepMs(120f, AudioGridStep.BEAT), 1e-9)
        assertEquals(125.0, gridStepMs(120f, AudioGridStep.SIXTEENTH), 1e-9)
        assertEquals(2_000.0, gridStepMs(120f, AudioGridStep.BAR), 1e-9)
    }

    @Test
    fun `sin rejilla el paso es cero`() {
        assertEquals(0.0, gridStepMs(120f, AudioGridStep.OFF), 0.0)
    }

    @Test
    fun `el paso es fraccionario y no acumula error`() {
        // 128 BPM: 468.75 ms por pulso -> 100 pulsos = 46875 ms exactos.
        val step = gridStepMs(128f, AudioGridStep.BEAT)
        assertEquals(46_875L, snapToTargetsMs(46_880L, emptyList(), 20L, step))
    }

    @Test
    fun `se pega a la linea de rejilla dentro del umbral`() {
        assertEquals(1_000L, snapToTargetsMs(1_030L, emptyList(), 50L, 500.0))
    }

    @Test
    fun `fuera del umbral no se mueve`() {
        assertEquals(1_200L, snapToTargetsMs(1_200L, emptyList(), 50L, 500.0))
    }

    @Test
    fun `un objetivo mas cercano gana a la rejilla`() {
        // Rejilla en 1000 (dist 30) vs borde de clip en 1010 (dist 20).
        assertEquals(1_010L, snapToTargetsMs(1_030L, listOf(1_010L), 50L, 500.0))
    }

    @Test
    fun `ante empate gana el objetivo sobre la rejilla`() {
        assertEquals(1_010L, snapToTargetsMs(1_005L, listOf(1_010L), 50L, 1_000.0))
    }

    @Test
    fun `sin rejilla el comportamiento anterior no cambia`() {
        assertEquals(2_000L, snapToTargetsMs(1_980L, listOf(2_000L), 50L))
        assertEquals(1_980L, snapToTargetsMs(1_980L, emptyList(), 50L))
    }

    @Test
    fun `mover un clip pega su borde derecho a la rejilla`() {
        // Clip de 900 ms que arranca en 80; su fin (980) esta a 20 de la linea 1000.
        assertEquals(100L, snapMovedClipStartMs(80L, 900L, emptyList(), 30L, 1_000.0))
    }

    @Test
    fun `tempo fuera de rango se acota`() {
        assertEquals(gridStepMs(MAX_GRID_BPM, AudioGridStep.BEAT), gridStepMs(9_999f, AudioGridStep.BEAT), 1e-9)
    }

    @Test
    fun `un nombre guardado desconocido cae a sin rejilla`() {
        assertEquals(AudioGridStep.OFF, AudioGridStep.fromName("QUINTUPLET"))
        assertEquals(AudioGridStep.OFF, AudioGridStep.fromName(null))
        assertEquals(AudioGridStep.EIGHTH, AudioGridStep.fromName("EIGHTH"))
    }
}
