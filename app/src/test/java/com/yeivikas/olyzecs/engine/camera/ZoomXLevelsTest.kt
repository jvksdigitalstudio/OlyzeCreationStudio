package com.yeivikas.olyzecs.engine.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ZoomXLevels] — motor puro del módulo "Zoom X" (Módulos >
 * Cámara). Cubre la secuencia de paradas, su extensibilidad (el motivo de
 * ser de este archivo: "que el motor sea configurable para aumentarlo
 * posteriormente"), el snap logarítmico al nivel más cercano y el
 * progreso angular que usa la rueda para ubicar el indicador.
 */
class ZoomXLevelsTest {

    // ---- levels ---------------------------------------------------------

    @Test
    fun `levels con el maxExponent por defecto llega hasta 1024x`() {
        val levels = ZoomXLevels.levels()
        assertEquals(listOf(1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f, 512f, 1024f), levels)
    }

    @Test
    fun `levels es configurable — un maxExponent distinto cambia el tope sin tocar nada mas`() {
        assertEquals(listOf(1f, 2f, 4f, 8f), ZoomXLevels.levels(maxExponent = 3))
        // Subir el rango a futuro (ej. 2048x) es cambiar un numero, no reescribir la rueda.
        assertEquals(listOf(1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f, 512f, 1024f, 2048f), ZoomXLevels.levels(maxExponent = 11))
    }

    @Test
    fun `levels con maxExponent 0 devuelve un unico nivel, 1x, sin romper`() {
        assertEquals(listOf(1f), ZoomXLevels.levels(maxExponent = 0))
    }

    @Test
    fun `levels con maxExponent negativo se trata como 0, nunca lista vacia`() {
        assertEquals(listOf(1f), ZoomXLevels.levels(maxExponent = -5))
    }

    // ---- nearestIndex / nearestLevel — snap logaritmico ------------------

    @Test
    fun `nearestLevel sobre un valor exacto de la lista devuelve ese mismo valor`() {
        val levels = ZoomXLevels.levels()
        assertEquals(64f, ZoomXLevels.nearestLevel(64f, levels))
    }

    @Test
    fun `nearestLevel redondea al escalon logaritmicamente mas cercano, no al lineal`() {
        val levels = ZoomXLevels.levels(maxExponent = 3) // [1, 2, 4, 8]
        // 513 esta MUY cerca de 512 en escala logaritmica (ratio ~1.002),
        // pero en distancia LINEAL esta mas cerca de 1024 (511 vs 511)...
        // se prueba con un caso mas chico y sin ambiguedad: 3 esta a mitad
        // de camino lineal entre 2 y 4, pero en log2 3 = 1.585, mas cerca
        // de log2(4)=2 que de log2(2)=1 -> debe redondear a 4, no a 2.
        assertEquals(4f, ZoomXLevels.nearestLevel(3f, levels))
        // En cambio 1.4 (log2 = 0.485) esta mas cerca de log2(1)=0 que de
        // log2(2)=1 -> debe redondear a 1, no a 2 (aunque en distancia
        // LINEAL 1.4 este mas cerca de 1 que de 2 tambien, este caso
        // confirma que el criterio logaritmico da el mismo resultado
        // esperado acá, y el de arriba confirma que DIFIERE del lineal
        // donde realmente importa).
        assertEquals(1f, ZoomXLevels.nearestLevel(1.4f, levels))
    }

    @Test
    fun `nearestLevel con un valor por debajo de 1x satura en la primera parada`() {
        val levels = ZoomXLevels.levels(maxExponent = 3)
        assertEquals(1f, ZoomXLevels.nearestLevel(0f, levels))
        assertEquals(1f, ZoomXLevels.nearestLevel(-10f, levels))
    }

    @Test
    fun `nearestLevel con un valor por encima del tope satura en la ultima parada`() {
        val levels = ZoomXLevels.levels(maxExponent = 3) // tope 8x
        assertEquals(8f, ZoomXLevels.nearestLevel(999f, levels))
    }

    // ---- progressAt -------------------------------------------------------

    @Test
    fun `progressAt equiespacia por INDICE, no por valor numerico`() {
        val levels = ZoomXLevels.levels(maxExponent = 3) // [1, 2, 4, 8], 4 paradas
        assertEquals(0f, ZoomXLevels.progressAt(0, levels.size), 0.0001f)
        // 512x->1024x (el ultimo salto) ocupa el MISMO arco que 1x->2x (el
        // primero) — ambos son "un paso" de indice, aunque numericamente
        // uno representa una diferencia de 1 y el otro de 512.
        assertEquals(1f / 3f, ZoomXLevels.progressAt(1, levels.size), 0.0001f)
        assertEquals(2f / 3f, ZoomXLevels.progressAt(2, levels.size), 0.0001f)
        assertEquals(1f, ZoomXLevels.progressAt(3, levels.size), 0.0001f)
    }

    @Test
    fun `progressAt con un unico nivel no divide por cero`() {
        assertEquals(0f, ZoomXLevels.progressAt(0, levelCount = 1))
    }

    // ---- label --------------------------------------------------------

    @Test
    fun `label formatea sin decimales con el simbolo multiplicador`() {
        assertEquals("1×", ZoomXLevels.label(1f))
        assertEquals("4×", ZoomXLevels.label(4f))
        assertEquals("1024×", ZoomXLevels.label(1024f))
    }

    // ---- consistencia interna -------------------------------------------

    @Test
    fun `cada nivel es exactamente el doble del anterior, en toda la secuencia por defecto`() {
        val levels = ZoomXLevels.levels()
        for (i in 1 until levels.size) {
            assertTrue("nivel[$i]=${levels[i]} deberia ser 2x nivel[${i - 1}]=${levels[i - 1]}", levels[i] == levels[i - 1] * 2f)
        }
    }
}
