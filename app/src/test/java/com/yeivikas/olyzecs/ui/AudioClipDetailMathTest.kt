package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioClipDetailMathTest {

    // ---- Cuadrícula y formato ----

    @Test
    fun `el paso de la regla crece con el ancho exigido por las etiquetas`() {
        // Misma escala; una etiqueta más ancha (más decimales / fuente mayor / pantalla más densa) pide un paso mayor.
        val narrow = detailRulerStepMs(pxPerMs = 0.5f) { 40f }
        val wide = detailRulerStepMs(pxPerMs = 0.5f) { 160f }
        assertTrue("narrow=$narrow wide=$wide", wide > narrow)
        assertTrue(narrow * 0.5f >= 40f)
        assertTrue(wide * 0.5f >= 160f)
    }

    @Test
    fun `el paso de la regla admite una exigencia que depende del paso`() {
        // Los pasos de menos de 1 s llevan decimales (etiqueta ancha); desde 1 s la etiqueta es corta (m:ss).
        val step = detailRulerStepMs(pxPerMs = 0.1f) { s -> if (s < 1_000.0) 200f else 50f }
        assertEquals(1_000.0, step, 0.0)
    }

    @Test
    fun `el paso de la regla sin escala valida es el mayor`() {
        assertEquals(3_600_000.0, detailRulerStepMs(0f) { 10f }, 0.0)
        assertEquals(3_600_000.0, detailRulerStepMs(1e-9f) { 10f }, 0.0)
    }

    @Test
    fun `el paso de cuadricula respeta la separacion minima`() {
        // 0,1 px/ms: 500 ms = 50 px, 1000 ms = 100 px.
        assertEquals(1_000.0, detailGridStepMs(pxPerMs = 0.1f, minSpacingPx = 80f), 0.0)
        // 10 px/ms muy ampliado: alcanza con 10 ms (100 px).
        assertEquals(10.0, detailGridStepMs(pxPerMs = 10f, minSpacingPx = 80f), 0.0)
    }

    @Test
    fun `a nivel de muestra hay pasos sub-milisegundo`() {
        // 500 px/ms (≈ 11 px por muestra a 44,1 kHz): 0,2 ms = 100 px.
        assertEquals(0.2, detailGridStepMs(pxPerMs = 500f, minSpacingPx = 96f), 1e-12)
    }

    @Test
    fun `sin escala valida el paso es el mayor`() {
        assertEquals(3_600_000.0, detailGridStepMs(pxPerMs = 0f, minSpacingPx = 80f), 0.0)
        assertEquals(3_600_000.0, detailGridStepMs(pxPerMs = 0.0000001f, minSpacingPx = 80f), 0.0)
    }

    @Test
    fun `un audio de 3 horas en 1x no pisa los numeros de la regla`() {
        // Límite del proyecto (180 min) en un eje de 1 566 px con números anchos (fuente grande: 120 px entre marcas).
        // Con el tope antiguo (10 min) solo había 87 px entre marcas y los números se solapaban; ahora el paso crece
        // (15 min o más) hasta cumplir la separación exigida.
        val totalMs = 180.0 * 60_000.0
        val pxPerMs = (1_566.0 / totalMs).toFloat()
        val required = 120f
        val step = detailRulerStepMs(pxPerMs) { required }
        assertTrue("step=$step", step * pxPerMs >= required)
        assertTrue("step=$step", step >= 900_000.0)
    }

    @Test
    fun `la escalera cubre archivos de hasta 12 horas en 1x`() {
        val totalMs = 12.0 * 3_600_000.0
        val pxPerMs = (1_566.0 / totalMs).toFloat()
        val step = detailRulerStepMs(pxPerMs) { 87f }
        assertTrue("step=$step", step * pxPerMs >= 87f)
    }

    @Test
    fun `formato de tiempo entero`() {
        assertEquals("0:00", formatDetailTimeMs(0, false))
        assertEquals("1:05", formatDetailTimeMs(65_432, false))
        assertEquals("1:05.432", formatDetailTimeMs(65_432, true))
        assertEquals("0:00.007", formatDetailTimeMs(7, true))
        assertEquals("0:00", formatDetailTimeMs(-50, false))
    }

    @Test
    fun `la etiqueta de la cuadricula sigue al paso`() {
        assertEquals("1:05", formatDetailTimeAt(65_432.0, stepMs = 5_000.0))
        assertEquals("1:05.432", formatDetailTimeAt(65_432.0, stepMs = 2.0))
        assertEquals("0:00.0012", formatDetailTimeAt(1.2, stepMs = 0.2))
        assertEquals("0:00.00125", formatDetailTimeAt(1.25, stepMs = 0.05))
        assertEquals("0:00.000", formatDetailTimeAt(-3.0, stepMs = 1.0))
        assertEquals("3:00:00", formatDetailTimeAt(10_800_000.0, stepMs = 3_600_000.0))
    }

    @Test
    fun `la etiqueta usa los decimales justos del paso sin ceros de relleno`() {
        assertEquals("0:00.5", formatDetailTimeAt(500.0, stepMs = 500.0))
        assertEquals("0:03.2", formatDetailTimeAt(3_200.0, stepMs = 200.0))
        assertEquals("0:01.1", formatDetailTimeAt(1_100.0, stepMs = 100.0))
        assertEquals("0:00.05", formatDetailTimeAt(50.0, stepMs = 50.0))
        assertEquals("0:00.02", formatDetailTimeAt(20.0, stepMs = 20.0))
        assertEquals("0:00.01", formatDetailTimeAt(10.0, stepMs = 10.0))
        assertEquals("0:00.005", formatDetailTimeAt(5.0, stepMs = 5.0))
        assertEquals("0:02", formatDetailTimeAt(2_000.0, stepMs = 1_000.0))
    }

    @Test
    fun `los decimales de la etiqueta de cada paso de la escalera`() {
        assertEquals(0, detailLabelDecimals(1_000.0))
        assertEquals(0, detailLabelDecimals(30_000.0))
        assertEquals(1, detailLabelDecimals(500.0))
        assertEquals(1, detailLabelDecimals(200.0))
        assertEquals(1, detailLabelDecimals(100.0))
        assertEquals(2, detailLabelDecimals(50.0))
        assertEquals(2, detailLabelDecimals(20.0))
        assertEquals(2, detailLabelDecimals(10.0))
        assertEquals(3, detailLabelDecimals(5.0))
        assertEquals(3, detailLabelDecimals(2.0))
        assertEquals(3, detailLabelDecimals(1.0))
        assertEquals(4, detailLabelDecimals(0.5))
        assertEquals(4, detailLabelDecimals(0.2))
        assertEquals(4, detailLabelDecimals(0.1))
        assertEquals(5, detailLabelDecimals(0.05))
        assertEquals(5, detailLabelDecimals(0.02))
        assertEquals(5, detailLabelDecimals(0.01))
        assertEquals(5, detailLabelDecimals(0.0))
        assertEquals(5, detailLabelDecimals(Double.NaN))
    }

    @Test
    fun `desde una hora la etiqueta usa horas minutos y segundos`() {
        assertEquals("59:59", formatDetailTimeAt(3_599_000.0, stepMs = 1_000.0))
        assertEquals("1:00:00", formatDetailTimeAt(3_600_000.0, stepMs = 3_600_000.0))
        assertEquals("1:01:01", formatDetailTimeAt(3_661_000.0, stepMs = 1_000.0))
        assertEquals("2:30:00", formatDetailTimeAt(9_000_000.0, stepMs = 1_800_000.0))
        assertEquals("1:00:00.5", formatDetailTimeAt(3_600_500.0, stepMs = 500.0))
    }

    @Test
    fun `la etiqueta redondea sin desbordar el segundo`() {
        // 999,9996 ms con 3 decimales redondea a 1,000 s => 0:01.000, no 0:00.1000.
        assertEquals("0:01.000", formatDetailTimeAt(999.9996, stepMs = 1.0))
    }

    // ---- Zoom ----

    @Test
    fun `el zoom maximo deja la muestra en los pixeles pedidos`() {
        // 3,42 s a 44,1 kHz en 2400 px: 14 px/muestra => 14 * 44,1 * 3420 / 2400 ≈ 880×.
        assertEquals(879.8f, detailMaxZoom(3_420.0, 2_400f, 44_100.0, 14f), 0.5f)
    }

    @Test
    fun `el zoom maximo con la onda gruesa es mucho menor`() {
        // Cubetas de 5 ms (200/s), 6 px por cubeta, 3,42 s en 2400 px => ≈ 1,7×.
        assertEquals(1.71f, detailMaxZoom(3_420.0, 2_400f, 200.0, 6f), 0.02f)
    }

    @Test
    fun `el zoom maximo se acota`() {
        assertEquals(DETAIL_MIN_ZOOM, detailMaxZoom(0.0, 2_400f, 44_100.0, 14f), 0f)
        assertEquals(DETAIL_MIN_ZOOM, detailMaxZoom(1_000.0, 0f, 44_100.0, 14f), 0f)
        assertEquals(DETAIL_MIN_ZOOM, detailMaxZoom(1.0, 2_400f, 44_100.0, 14f), 0f)
        assertEquals(DETAIL_ABSOLUTE_MAX_ZOOM, detailMaxZoom(3_600_000.0, 1_000f, 192_000.0, 14f), 0f)
    }

    @Test
    fun `la interpolacion de zoom es geometrica`() {
        assertEquals(1f, detailInterpolateZoom(1f, 16f, 0f), 1e-5f)
        assertEquals(4f, detailInterpolateZoom(1f, 16f, 0.5f), 1e-4f)
        assertEquals(16f, detailInterpolateZoom(1f, 16f, 1f), 1e-4f)
        assertEquals(1f, detailInterpolateZoom(16f, 1f, 1f), 1e-4f)
        assertEquals(16f, detailInterpolateZoom(1f, 16f, 7f), 1e-4f) // t se acota a 1
    }

    @Test
    fun `el scroll se acota al contenido`() {
        assertEquals(0f, clampDetailScrollPx(-10f, 1_000f, 4f), 0f)
        assertEquals(3_000f, clampDetailScrollPx(99_999f, 1_000f, 4f), 0f)
        assertEquals(0f, clampDetailScrollPx(500f, 1_000f, 1f), 0f)
    }

    @Test
    fun `el zoom mantiene el instante bajo el dedo`() {
        // Zoom 1→2 con el dedo en x=400 y sin scroll: el instante bajo el dedo (400/1) pasa a 800/2.
        val s = detailScrollAfterZoom(scrollPx = 0f, oldZoom = 1f, newZoom = 2f, focusPx = 400f, panPx = 0f, viewportPx = 1_000f)
        assertEquals(400f, s, 0.001f)
    }

    @Test
    fun `arrastrar a la derecha disminuye el scroll`() {
        val s = detailScrollAfterZoom(scrollPx = 500f, oldZoom = 4f, newZoom = 4f, focusPx = 300f, panPx = 120f, viewportPx = 1_000f)
        assertEquals(380f, s, 0.001f)
    }

    // ---- Pellizco ----

    @Test
    fun `la orientacion del pellizco sigue al eje con mas separacion`() {
        assertTrue(detailPinchIsHorizontal(spanX = 300f, spanY = 100f))
        assertFalse(detailPinchIsHorizontal(spanX = 100f, spanY = 300f))
        assertTrue(detailPinchIsHorizontal(spanX = 200f, spanY = 200f))
    }

    @Test
    fun `la razon del pellizco se acota y ignora dedos muy juntos`() {
        assertEquals(1.1f, detailPinchRatio(prevSpan = 200f, curSpan = 220f), 1e-5f)
        assertEquals(2f, detailPinchRatio(prevSpan = 100f, curSpan = 900f), 0f)
        assertEquals(0.5f, detailPinchRatio(prevSpan = 400f, curSpan = 10f), 0f)
        assertEquals(1f, detailPinchRatio(prevSpan = 10f, curSpan = 300f), 0f)
        assertEquals(1f, detailPinchRatio(prevSpan = 300f, curSpan = 0f), 0f)
    }
}
