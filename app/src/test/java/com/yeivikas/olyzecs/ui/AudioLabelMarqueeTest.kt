package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lógica pura de la cabecera "ícono + nombre" en loop de la pista de audio
 * ([shouldMarqueeAudioLabel], [marqueeOffsetPx], [marqueeSlots]).
 *
 * Requisito de producto cubierto: el nombre del audio se desplaza en bucle
 * SOLO cuando el panel de opciones está al pie de la capa Y hay una pestaña
 * inferior (Control/Módulos/Keyframes) abierta; en cualquier otro estado la
 * cabecera es el ícono estático de siempre.
 */
class AudioLabelMarqueeTest {

    // --- Regla de activación -------------------------------------------------

    @Test
    fun `anima solo con panel al pie Y pestana inferior abierta`() {
        assertTrue(shouldMarqueeAudioLabel(footAccordionVisible = true, bottomPanelExpanded = true))
    }

    @Test
    fun `no anima con la pestana inferior cerrada`() {
        // Panel al pie por scroll (fila que no cabe), pero sin pestaña abierta.
        assertFalse(shouldMarqueeAudioLabel(footAccordionVisible = true, bottomPanelExpanded = false))
    }

    @Test
    fun `no anima con la pestana abierta pero el panel de opciones cerrado o al costado`() {
        assertFalse(shouldMarqueeAudioLabel(footAccordionVisible = false, bottomPanelExpanded = true))
    }

    @Test
    fun `no anima con todo cerrado`() {
        assertFalse(shouldMarqueeAudioLabel(footAccordionVisible = false, bottomPanelExpanded = false))
    }

    // --- Desplazamiento ------------------------------------------------------

    @Test
    fun `offset es cero al inicio o con parametros invalidos`() {
        assertEquals(0f, marqueeOffsetPx(0L, 100f, 200f), 0f)
        assertEquals(0f, marqueeOffsetPx(-5L, 100f, 200f), 0f)
        assertEquals(0f, marqueeOffsetPx(1_000_000_000L, 0f, 200f), 0f)
        assertEquals(0f, marqueeOffsetPx(1_000_000_000L, 100f, 0f), 0f)
    }

    @Test
    fun `offset avanza a velocidad constante`() {
        // 100 px/s durante 0,5 s = 50 px (período 200 → sin vuelta).
        assertEquals(50f, marqueeOffsetPx(500_000_000L, 100f, 200f), 0.001f)
        assertEquals(100f, marqueeOffsetPx(1_000_000_000L, 100f, 200f), 0.001f)
    }

    @Test
    fun `offset da la vuelta exacta al completar el periodo`() {
        // 2 s a 100 px/s = 200 px = un período completo → vuelve a 0.
        assertEquals(0f, marqueeOffsetPx(2_000_000_000L, 100f, 200f), 0.001f)
        // 2,5 s → 250 px → 50 px dentro del segundo período.
        assertEquals(50f, marqueeOffsetPx(2_500_000_000L, 100f, 200f), 0.001f)
    }

    @Test
    fun `offset nunca sale de cero a periodo ni siquiera tras horas abierto`() {
        val threeHoursNanos = 3L * 60 * 60 * 1_000_000_000L
        val period = 173.5f
        val offset = marqueeOffsetPx(threeHoursNanos, 34f * 2.75f, period)
        assertTrue("offset=$offset", offset >= 0f && offset < period)
    }

    // --- Desborde ------------------------------------------------------------

    @Test
    fun `desborda solo si el segmento es estrictamente mas ancho que el area visible`() {
        assertTrue(marqueeOverflows(segmentPx = 161, viewportPx = 160))
        assertFalse(marqueeOverflows(segmentPx = 160, viewportPx = 160))
        assertFalse(marqueeOverflows(segmentPx = 100, viewportPx = 160))
    }

    // --- Posiciones de las copias -------------------------------------------

    @Test
    fun `si el segmento cabe se centra y queda quieto sin segunda copia`() {
        val slots = marqueeSlots(segmentPx = 100, viewportPx = 200, gapPx = 40, offsetPx = 77f, animate = true)
        assertEquals(50, slots.firstX)
        assertNull(slots.secondX)
    }

    @Test
    fun `si no cabe y se anima, la segunda copia va un periodo a la derecha`() {
        val slots = marqueeSlots(segmentPx = 300, viewportPx = 160, gapPx = 40, offsetPx = 25f, animate = true)
        assertEquals(-25, slots.firstX)
        assertEquals(-25 + 300 + 40, slots.secondX)
    }

    @Test
    fun `en offset cero la primera copia esta alineada al inicio con icono visible`() {
        val slots = marqueeSlots(segmentPx = 300, viewportPx = 160, gapPx = 40, offsetPx = 0f, animate = true)
        assertEquals(0, slots.firstX)
    }

    @Test
    fun `al final del periodo la segunda copia ocupa el lugar exacto de la primera`() {
        // offset = período − ε ≈ período: copia 2 casi en x=0, igual que la copia 1 en offset 0 → sin salto.
        val segment = 300
        val gap = 40
        val period = segment + gap
        val slots = marqueeSlots(segment, 160, gap, (period - 0.4f), animate = true)
        assertEquals(0, slots.secondX)
    }

    @Test
    fun `sin animaciones del sistema no cabe - se muestra el inicio estatico`() {
        val slots = marqueeSlots(segmentPx = 300, viewportPx = 160, gapPx = 40, offsetPx = 90f, animate = false)
        assertEquals(0, slots.firstX)
        assertNull(slots.secondX)
    }
}
