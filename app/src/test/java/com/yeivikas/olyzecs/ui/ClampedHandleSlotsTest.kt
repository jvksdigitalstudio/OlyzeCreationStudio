package com.yeivikas.olyzecs.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [clampedHandleSlots] — fija por regresión el bug real
 * corregido el 14/sep/2026 (reportado con captura del proyecto "Cr7"
 * tras hacer zoom/pellizco sobre una capa): los 4 puntos medios
 * (TOP_MID/RIGHT_MID/BOTTOM_MID/LEFT_MID) se calculaban a partir de las
 * esquinas CRUDAS (sin recortar) y LUEGO se recortaban por su cuenta —
 * en reposo (esquinas dentro del canvas) el bug es invisible porque el
 * `coerceIn` nunca llega a actuar, pero en cuanto una capa se escala lo
 * bastante como para que sus esquinas reales queden fuera del canvas
 * (el caso exacto de la captura), el punto medio deja de caer sobre la
 * línea entre sus dos esquinas ya recortadas — la manija de ese lado se
 * ve "suelta", separada del contorno real.
 */
class ClampedHandleSlotsTest {

    private val canvas = Size(1000f, 1000f)
    private val margin = 20f

    @Test
    fun `sin recorte necesario, cada punto medio es el promedio exacto de sus 2 esquinas`() {
        // Cuadrado 400x400 centrado en el canvas: ninguna esquina toca el margen.
        val corners = listOf(
            Offset(300f, 300f), // topLeft
            Offset(700f, 300f), // topRight
            Offset(700f, 700f), // bottomRight
            Offset(300f, 700f)  // bottomLeft
        )
        val slots = clampedHandleSlots(corners, canvas, margin)

        assertEquals(corners[0], slots.getValue(HandlePosition.TOP_LEFT))
        assertEquals(corners[1], slots.getValue(HandlePosition.TOP_RIGHT))
        assertEquals(corners[2], slots.getValue(HandlePosition.BOTTOM_RIGHT))
        assertEquals(corners[3], slots.getValue(HandlePosition.BOTTOM_LEFT))
        assertEquals(Offset(500f, 300f), slots.getValue(HandlePosition.TOP_MID))
        assertEquals(Offset(700f, 500f), slots.getValue(HandlePosition.RIGHT_MID))
        assertEquals(Offset(500f, 700f), slots.getValue(HandlePosition.BOTTOM_MID))
        assertEquals(Offset(300f, 500f), slots.getValue(HandlePosition.LEFT_MID))
    }

    @Test
    fun `capa muy escalada (esquinas reales fuera del canvas) - cada punto medio queda EXACTO a mitad de camino entre sus 2 esquinas ya recortadas`() {
        // Capa "zoomeada" bien más allá del canvas por los 4 lados -
        // exactamente lo que reportó el usuario al hacer pellizco/zoom.
        val corners = listOf(
            Offset(-4000f, -3000f), // topLeft, muy afuera arriba-izq
            Offset(5000f, -2500f),  // topRight, muy afuera arriba-der
            Offset(4500f, 6000f),   // bottomRight, muy afuera abajo-der
            Offset(-3500f, 5500f)   // bottomLeft, muy afuera abajo-izq
        )
        val slots = clampedHandleSlots(corners, canvas, margin)

        val topLeft = slots.getValue(HandlePosition.TOP_LEFT)
        val topRight = slots.getValue(HandlePosition.TOP_RIGHT)
        val bottomRight = slots.getValue(HandlePosition.BOTTOM_RIGHT)
        val bottomLeft = slots.getValue(HandlePosition.BOTTOM_LEFT)

        // Las 4 esquinas quedan dentro del margen del canvas (recorte esperado).
        listOf(topLeft, topRight, bottomRight, bottomLeft).forEach {
            assertTrue(it.x in margin..(canvas.width - margin))
            assertTrue(it.y in margin..(canvas.height - margin))
        }

        // LA REGRESIÓN REAL: cada punto medio tiene que ser el promedio
        // EXACTO de sus 2 esquinas YA RECORTADAS - no un valor recortado
        // por su cuenta a partir de las esquinas crudas.
        assertEquals((topLeft.x + topRight.x) / 2f, slots.getValue(HandlePosition.TOP_MID).x, 1e-4f)
        assertEquals((topLeft.y + topRight.y) / 2f, slots.getValue(HandlePosition.TOP_MID).y, 1e-4f)

        assertEquals((topRight.x + bottomRight.x) / 2f, slots.getValue(HandlePosition.RIGHT_MID).x, 1e-4f)
        assertEquals((topRight.y + bottomRight.y) / 2f, slots.getValue(HandlePosition.RIGHT_MID).y, 1e-4f)

        assertEquals((bottomRight.x + bottomLeft.x) / 2f, slots.getValue(HandlePosition.BOTTOM_MID).x, 1e-4f)
        assertEquals((bottomRight.y + bottomLeft.y) / 2f, slots.getValue(HandlePosition.BOTTOM_MID).y, 1e-4f)

        assertEquals((topLeft.x + bottomLeft.x) / 2f, slots.getValue(HandlePosition.LEFT_MID).x, 1e-4f)
        assertEquals((topLeft.y + bottomLeft.y) / 2f, slots.getValue(HandlePosition.LEFT_MID).y, 1e-4f)
    }

    @Test
    fun `sin recorte necesario en un eje, con recorte en el otro - los puntos medios de ese lado siguen exactos`() {
        // Solo el eje Y se sale del canvas (arriba/abajo), el eje X queda
        // adentro - caso mixto, para confirmar que el fix no depende de
        // que los 4 lados se salgan a la vez.
        val corners = listOf(
            Offset(400f, -2000f), // topLeft
            Offset(600f, -2000f), // topRight
            Offset(600f, 3000f),  // bottomRight
            Offset(400f, 3000f)   // bottomLeft
        )
        val slots = clampedHandleSlots(corners, canvas, margin)

        val topLeft = slots.getValue(HandlePosition.TOP_LEFT)
        val topRight = slots.getValue(HandlePosition.TOP_RIGHT)
        // X no necesitaba recorte: se preserva tal cual.
        assertEquals(400f, topLeft.x, 1e-4f)
        assertEquals(600f, topRight.x, 1e-4f)
        // Y sí se recorta al margen superior.
        assertEquals(margin, topLeft.y, 1e-4f)

        val topMid = slots.getValue(HandlePosition.TOP_MID)
        assertEquals((topLeft.x + topRight.x) / 2f, topMid.x, 1e-4f)
        assertEquals((topLeft.y + topRight.y) / 2f, topMid.y, 1e-4f)
    }

    @Test
    fun `sin tamano de canvas medido, devuelve las posiciones crudas sin recortar`() {
        val corners = listOf(
            Offset(-100f, -100f),
            Offset(200f, -100f),
            Offset(200f, 200f),
            Offset(-100f, 200f)
        )
        val slots = clampedHandleSlots(corners, Size(0f, 0f), margin)

        assertEquals(corners[0], slots.getValue(HandlePosition.TOP_LEFT))
        assertEquals(Offset(50f, -100f), slots.getValue(HandlePosition.TOP_MID))
    }
}
