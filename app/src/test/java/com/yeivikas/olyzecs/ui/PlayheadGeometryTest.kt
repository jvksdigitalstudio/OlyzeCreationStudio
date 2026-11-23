package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import kotlin.math.roundToInt
import org.junit.Test

/**
 * Fija por regresión el bug real reportado (captura del proyecto "Cr7"): la línea
 * del playhead / separador se cortaba a mitad de la lista por un tope fijo de 280dp
 * y no llegaba al final de la última capa.
 */
class PlayheadGeometryTest {

    private val row = 100f

    @Test
    fun `sin filas la altura visible es cero`() {
        assertEquals(0f, visibleRowsHeightPx(0, row, 0, 1000f), 0f)
    }

    @Test
    fun `con 11 filas y viewport amplio llega al final de la ultima fila - sin tope de 280dp`() {
        assertEquals(1100f, visibleRowsHeightPx(11, row, 0, Float.MAX_VALUE), 0f)
        assertEquals(1100f, visibleRowsHeightPx(11, row, 0, 5000f), 0f)
    }

    @Test
    fun `si las filas superan el viewport se acota al viewport`() {
        assertEquals(600f, visibleRowsHeightPx(11, row, 0, 600f), 0f)
    }

    @Test
    fun `al scrollear hasta el final la linea termina en la ultima fila`() {
        // contenido 1100, viewport 600, scroll maximo 500 -> quedan 600 visibles
        assertEquals(600f, visibleRowsHeightPx(11, row, 500, 600f), 0f)
        // viewport mayor que lo restante: termina donde termina la ultima fila
        assertEquals(300f, visibleRowsHeightPx(11, row, 800, 600f), 0f)
    }

    @Test
    fun `valores invalidos no producen alturas negativas`() {
        assertEquals(0f, visibleRowsHeightPx(3, row, 9999, 600f), 0f)
        assertEquals(0f, visibleRowsHeightPx(3, row, 0, -5f), 0f)
        assertEquals(0f, visibleRowsHeightPx(3, 0f, 0, 600f), 0f)
    }

    @Test
    fun `alineacion a pixel entero segun la paridad del grosor`() {
        assertEquals(10f, snapLineCenterPx(10.3f, 2f), 0f)   // grosor par -> borde de pixel
        assertEquals(11f, snapLineCenterPx(10.6f, 2f), 0f)
        assertEquals(10.5f, snapLineCenterPx(10.3f, 3f), 0f) // grosor impar -> centro de pixel
    }

    // ---- Continuidad horizontal de los tramos (regla y filas en la misma columna de píxeles) ----

    @Test
    fun `la zona del playhead se centra en la linea y es entera`() {
        assertEquals(86, playheadHitLeftPx(100f, 28f))
        assertEquals(-14, playheadHitLeftPx(0f, 28f))
        assertEquals(87, playheadHitLeftPx(100.6f, 28f))
    }

    @Test
    fun `la regla coincide con las filas aunque el ancho de la columna sea fraccionario`() {
        // Densidad que da 72 dp = 184.32 px: el carril de las filas empieza en 184 (medida redondeada).
        val labelPx = 184.32f
        val laneStart = labelPx.roundToInt()
        val hit = 71.68f // 28 dp a esa densidad
        for (i in 0..2000) {
            val x = i * 0.37f
            val rows = laneStart + playheadHitLeftPx(x, hit)
            val ruler = playheadRulerHitLeftPx(labelPx, x, hit)
            assertEquals("x=$x", rows, ruler)
        }
    }
}
