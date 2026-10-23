package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cobertura de la matemática del eje de tiempo: arrastre del cabezal, autodesplazamiento y marcas de la regla. */
class AudioClipDetailTimeAxisMathTest {

    // Clip de referencia: suena [1000, 3000) ms del archivo (10 s), empieza en el 5000 del proyecto.
    private fun target(
        x: Float,
        scrollPx: Float = 0f,
        pxPerMs: Float = 1f,
        totalMs: Double = 10_000.0,
        trimStartMs: Long = 1_000L,
        timelineStartMs: Long = 5_000L,
        clipLengthMs: Long = 2_000L
    ) = detailScrubTarget(x, scrollPx, pxPerMs, totalMs, trimStartMs, timelineStartMs, clipLengthMs)

    // ---- Arrastre del cabezal: dedo → tiempo ----

    @Test
    fun `dentro del clip convierte la X en el instante del archivo y del proyecto`() {
        val t = target(x = 1_500f)!!
        assertEquals(1_500.0, t.sourceMs, 1e-9)
        assertEquals(5_500L, t.projectMs)
    }

    @Test
    fun `el desplazamiento y la escala se tienen en cuenta`() {
        // (300 + 100) px a 2 px/ms = 200 ms de archivo, por debajo del recorte (1000): se acota al inicio.
        assertEquals(1_000.0, target(x = 100f, scrollPx = 300f, pxPerMs = 2f)!!.sourceMs, 1e-9)
        // (3000 + 100) px a 2 px/ms = 1550 ms de archivo.
        val t = target(x = 100f, scrollPx = 3_000f, pxPerMs = 2f)!!
        assertEquals(1_550.0, t.sourceMs, 1e-9)
        assertEquals(5_550L, t.projectMs)
    }

    @Test
    fun `antes del inicio del clip el cabezal se queda en el inicio`() {
        val t = target(x = 200f)!!
        assertEquals(1_000.0, t.sourceMs, 1e-9)
        assertEquals(5_000L, t.projectMs)
    }

    @Test
    fun `pasado el final del clip el cabezal se queda en el final`() {
        val t = target(x = 9_000f)!!
        assertEquals(3_000.0, t.sourceMs, 1e-9)
        // El clip es [5000, 7000): lo último alcanzable es 6999 (en 7000 el cursor ya estaría fuera y se ampliaría el proyecto).
        assertEquals(6_999L, t.projectMs)
    }

    @Test
    fun `un clip mas largo que el archivo no pasa del final del archivo`() {
        // Con loop el clip puede ser más largo que el archivo: arrastrar no se mapea a las repeticiones.
        val t = target(x = 90_000f, trimStartMs = 0L, clipLengthMs = 50_000L)!!
        assertEquals(10_000.0, t.sourceMs, 1e-9)
        // Aquí limita el final del ARCHIVO, no el del clip (que dura 50 s): 15000 cae dentro del clip, no hay recorte.
        assertEquals(15_000L, t.projectMs)
    }

    @Test
    fun `el instante del proyecto se redondea al milisegundo mas cercano y el origen conserva los decimales`() {
        val down = target(x = 1_000.4f)!!
        assertEquals(5_000L, down.projectMs)
        assertEquals(1_000.4, down.sourceMs, 1e-3)
        assertEquals(5_001L, target(x = 1_000.6f)!!.projectMs)
    }

    @Test
    fun `con escalas o clips invalidos no hay destino`() {
        assertNull(target(x = 100f, pxPerMs = 0f))
        assertNull(target(x = 100f, totalMs = 0.0))
        // Recorte más allá del final del archivo: no hay tramo audible.
        assertNull(target(x = 100f, trimStartMs = 12_000L))
    }

    @Test
    fun `el cursor nunca queda en el fin exacto del clip`() {
        // Intervalo semiabierto [5000, 7000): arrastrar al extremo da 6999, no 7000 (que ya estaría fuera y,
        // si el clip termina donde termina el proyecto, ampliaría su duración).
        assertEquals(6_999L, target(x = 3_000f)!!.projectMs)
        assertEquals(6_999L, target(x = 50_000f)!!.projectMs)
        assertEquals(6_998L, target(x = 2_998f)!!.projectMs)
    }

    @Test
    fun `un clip de largo cero sigue dando un destino valido en su inicio`() {
        val t = target(x = 5_000f, clipLengthMs = 0L)
        assertNotNull(t)
        assertEquals(5_000L, t!!.projectMs)
    }

    // ---- Tramo audible y cabezal en reposo ----

    @Test
    fun `el tramo audible es recorte a recorte mas largo acotado al archivo`() {
        assertEquals(1_000.0 to 3_000.0, detailAudibleWindowMs(1_000L, 2_000L, 10_000.0))
        // Clip más largo que el archivo (loop): no pasa del final del archivo.
        assertEquals(0.0 to 10_000.0, detailAudibleWindowMs(0L, 50_000L, 10_000.0))
    }

    @Test
    fun `sin tramo audible valido no hay ventana`() {
        assertNull(detailAudibleWindowMs(1_000L, 2_000L, 0.0))
        assertNull(detailAudibleWindowMs(12_000L, 2_000L, 10_000.0))
    }

    private fun resting(playheadMs: Long, sourcePositionMs: Long?) =
        detailRestingHead(playheadMs, 5_000L, 2_000L, 1_000.0, 3_000.0, sourcePositionMs)

    @Test
    fun `cursor dentro del clip el cabezal esta en su posicion real`() {
        val h = resting(playheadMs = 5_500L, sourcePositionMs = 1_500L)
        assertEquals(1_500.0, h.sourceMs, 0.0)
        assertTrue(h.insideClip)
    }

    @Test
    fun `cursor antes del clip el cabezal queda en el inicio del tramo y atenuado`() {
        val h = resting(playheadMs = 0L, sourcePositionMs = null)
        assertEquals(1_000.0, h.sourceMs, 0.0)
        assertFalse(h.insideClip)
    }

    @Test
    fun `cursor despues del clip el cabezal queda en el final del tramo y atenuado`() {
        val h = resting(playheadMs = 9_000L, sourcePositionMs = null)
        assertEquals(3_000.0, h.sourceMs, 0.0)
        assertFalse(h.insideClip)
    }

    @Test
    fun `los limites exactos del clip cuentan como dentro`() {
        assertTrue(resting(playheadMs = 5_000L, sourcePositionMs = 1_000L).insideClip)
        assertTrue(resting(playheadMs = 7_000L, sourcePositionMs = 3_000L).insideClip)
    }

    @Test
    fun `dentro del rango pero sin audio en esa posicion se pega al final del tramo`() {
        val h = resting(playheadMs = 6_000L, sourcePositionMs = null)
        assertEquals(3_000.0, h.sourceMs, 0.0)
        assertFalse(h.insideClip)
    }

    // ---- Zona de agarre ----

    @Test
    fun `la zona de agarre incluye sus bordes y excluye lo de fuera`() {
        assertTrue(detailHeadHit(downX = 100f, headX = 100f, halfWidthPx = 28f))
        assertTrue(detailHeadHit(downX = 72f, headX = 100f, halfWidthPx = 28f))
        assertTrue(detailHeadHit(downX = 128f, headX = 100f, halfWidthPx = 28f))
        assertFalse(detailHeadHit(downX = 71.9f, headX = 100f, halfWidthPx = 28f))
        assertFalse(detailHeadHit(downX = 128.1f, headX = 100f, halfWidthPx = 28f))
    }

    @Test
    fun `agarrar el cabezal conserva el desfase y tocar la regla en otro punto no tiene desfase`() {
        // Sobre el cabezal: se conserva la distancia dedo→cabezal (no salta).
        assertEquals(12f, detailRulerGrabOffsetPx(downX = 88f, headX = 100f, halfWidthPx = 28f), 0f)
        assertEquals(-20f, detailRulerGrabOffsetPx(downX = 120f, headX = 100f, halfWidthPx = 28f), 0f)
        assertEquals(0f, detailRulerGrabOffsetPx(downX = 100f, headX = 100f, halfWidthPx = 28f), 0f)
        // Fuera: el cabezal va directamente bajo el dedo.
        assertEquals(0f, detailRulerGrabOffsetPx(downX = 300f, headX = 100f, halfWidthPx = 28f), 0f)
        assertEquals(0f, detailRulerGrabOffsetPx(downX = -10f, headX = 100f, halfWidthPx = 28f), 0f)
    }

    // ---- Autodesplazamiento en los bordes ----

    private fun auto(x: Float, viewport: Float = 1_000f, zone: Float = 100f, max: Float = 1_000f) =
        detailEdgeAutoScrollPxPerSec(x, viewport, zone, max)

    @Test
    fun `en el centro no hay autodesplazamiento`() {
        assertEquals(0f, auto(500f), 0f)
        assertEquals(0f, auto(100f), 0f) // justo en el límite de la franja izquierda
        assertEquals(0f, auto(900f), 0f) // justo en el límite de la franja derecha
    }

    @Test
    fun `cerca del borde izquierdo retrocede y cerca del derecho avanza`() {
        assertTrue(auto(50f) < 0f)
        assertTrue(auto(950f) > 0f)
    }

    @Test
    fun `la velocidad crece con el cuadrado de lo adentrado y llega al maximo a ras del borde`() {
        assertEquals(-250f, auto(50f), 1e-3f)   // mitad de la franja → 25 %
        assertEquals(250f, auto(950f), 1e-3f)
        assertEquals(-1_000f, auto(0f), 1e-3f)
        assertEquals(1_000f, auto(1_000f), 1e-3f)
    }

    @Test
    fun `fuera de la vista la velocidad no supera el maximo`() {
        assertEquals(-1_000f, auto(-80f), 1e-3f)
        assertEquals(1_000f, auto(1_300f), 1e-3f)
    }

    @Test
    fun `sin espacio para las franjas o con franja nula no hay autodesplazamiento`() {
        assertEquals(0f, auto(10f, viewport = 150f, zone = 100f), 0f)
        assertEquals(0f, auto(10f, zone = 0f), 0f)
    }

    // ---- Marcas de la regla ----

    @Test
    fun `subdivisiones de los pasos de la cuadricula caen en valores redondos`() {
        assertEquals(5, detailRulerSubdivisions(1.0))
        assertEquals(4, detailRulerSubdivisions(2.0))
        assertEquals(5, detailRulerSubdivisions(5.0))
        assertEquals(5, detailRulerSubdivisions(10.0))
        assertEquals(4, detailRulerSubdivisions(20.0))
        assertEquals(5, detailRulerSubdivisions(500.0))
        assertEquals(5, detailRulerSubdivisions(1_000.0))
        assertEquals(4, detailRulerSubdivisions(2_000.0))
        assertEquals(5, detailRulerSubdivisions(10_000.0))
        assertEquals(3, detailRulerSubdivisions(30_000.0))   // 10 s
        assertEquals(6, detailRulerSubdivisions(60_000.0))    // 10 s
        assertEquals(4, detailRulerSubdivisions(120_000.0))   // 30 s
        assertEquals(5, detailRulerSubdivisions(300_000.0))   // 1 min
        assertEquals(10, detailRulerSubdivisions(600_000.0))  // 1 min
        assertEquals(3, detailRulerSubdivisions(900_000.0))   // 5 min
        assertEquals(6, detailRulerSubdivisions(1_800_000.0)) // 5 min
        assertEquals(6, detailRulerSubdivisions(3_600_000.0)) // 10 min
    }

    @Test
    fun `una etiqueta solo se dibuja en el borde derecho si cabe entera`() {
        // Pantalla del tablet: eje de -29 a 1 595 px. Etiqueta de 76 px.
        assertTrue(detailRulerLabelVisible(1_514f, 76f, -29f, 1_595f))   // 0:03.200, cabe
        assertTrue(detailRulerLabelVisible(1_519f, 76f, -29f, 1_595f))   // justo en el borde
        assertFalse(detailRulerLabelVisible(1_577f, 76f, -29f, 1_595f))  // 0:03.400, asomaba ~18 px: ya no
        assertFalse(detailRulerLabelVisible(1_600f, 76f, -29f, 1_595f))  // fuera
    }

    @Test
    fun `por la izquierda la etiqueta se desliza fuera mientras le quede texto`() {
        assertTrue(detailRulerLabelVisible(-60f, 76f, -29f, 1_595f))     // asoma 47 px
        assertFalse(detailRulerLabelVisible(-110f, 76f, -29f, 1_595f))   // ya salió
    }

    @Test
    fun `subdivisiones a nivel de muestra`() {
        assertEquals(5, detailRulerSubdivisions(0.01))
        assertEquals(4, detailRulerSubdivisions(0.02))
        assertEquals(5, detailRulerSubdivisions(0.05))
        assertEquals(5, detailRulerSubdivisions(0.1))
        assertEquals(4, detailRulerSubdivisions(0.2))
        assertEquals(5, detailRulerSubdivisions(0.5))
    }

    @Test
    fun `un paso invalido usa 5 subdivisiones`() {
        assertEquals(5, detailRulerSubdivisions(0.0))
        assertEquals(5, detailRulerSubdivisions(-3.0))
        assertEquals(5, detailRulerSubdivisions(Double.NaN))
        assertEquals(5, detailRulerSubdivisions(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `toda la escalera de la cuadricula produce subdivisiones validas`() {
        // Barrido por todas las escalas de zoom reales: nunca menos de 2 partes ni marcas menores casi pegadas.
        var pxPerMs = 0.0005f
        while (pxPerMs < 2_000f) {
            val step = detailGridStepMs(pxPerMs, 96f)
            val parts = detailRulerSubdivisions(step)
            assertTrue("step=$step parts=$parts", parts >= 2)
            pxPerMs *= 1.37f
        }
    }

    private val totalMsForAxis = 3_420.0

    // --- X ↔ instante del archivo (conversión única del eje) -------------------------------------------------------

    @Test
    fun `la X se convierte en instante del archivo con el desplazamiento`() {
        assertEquals(300.0, detailXToSourceMs(axisXPx = 50f, scrollPx = 100f, pxPerMs = 0.5f, totalMs = totalMsForAxis), 1e-9)
    }

    @Test
    fun `arrastrar mas alla de un extremo se queda en el extremo`() {
        assertEquals(0.0, detailXToSourceMs(-80f, 0f, 0.5f, totalMsForAxis), 0.0)
        assertEquals(totalMsForAxis, detailXToSourceMs(5_000f, 0f, 0.5f, totalMsForAxis), 0.0)
    }

    @Test
    fun `con escala o duracion no validas el instante es cero`() {
        assertEquals(0.0, detailXToSourceMs(10f, 0f, 0f, totalMsForAxis), 0.0)
        assertEquals(0.0, detailXToSourceMs(10f, 0f, 0.5f, 0.0), 0.0)
    }

    @Test
    fun `instante y X son inversas`() {
        val x = detailSourceMsToX(1_234.0, scrollPx = 40f, pxPerMs = 0.458f)
        assertEquals(1_234.0, detailXToSourceMs(x, 40f, 0.458f, totalMsForAxis), 0.01)
    }

    // --- Toque sobre el cabezal = reproducir ---------------------------------------------------------------------

    private fun tap(
        onHead: Boolean = true,
        completed: Boolean = true,
        maxMovePx: Float = 2f,
        pressMs: Long = 90L
    ) = detailIsHeadTap(onHead, completed, maxMovePx, slopPx = 18f, pressMs = pressMs, longPressMs = 400L)

    @Test
    fun `un toque rapido y quieto sobre el cabezal reproduce`() {
        assertTrue(tap())
    }

    @Test
    fun `mantener presionado el cabezal no reproduce`() {
        assertFalse(tap(pressMs = 650L))
        assertFalse(tap(pressMs = 400L)) // el límite ya es una pulsación larga
        assertTrue(tap(pressMs = 399L))
    }

    @Test
    fun `arrastrar el cabezal no reproduce al soltar`() {
        assertFalse(tap(maxMovePx = 60f))
        assertTrue(tap(maxMovePx = 18f)) // dentro del umbral de toque
        assertFalse(tap(maxMovePx = 18.5f))
    }

    @Test
    fun `un toque fuera del cabezal o un gesto cancelado no reproduce`() {
        assertFalse(tap(onHead = false))
        assertFalse(tap(completed = false))
    }
}
