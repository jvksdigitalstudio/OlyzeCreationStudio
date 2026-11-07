package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Mapa de uso del archivo en la ventana ampliada del audio ([AudioClipDetailUsageMath.kt]): puro, sin Compose ni Android. */
class AudioClipDetailUsageMathTest {

    private fun s(a: Long, b: Long) = DetailSourceSpan(a, b)

    @Test
    fun `un tramo invalido no se puede construir`() {
        assertThrows(IllegalArgumentException::class.java) { s(-1, 10) }
        assertThrows(IllegalArgumentException::class.java) { s(10, 5) }
    }

    @Test
    fun `mergeDetailSpans une lo que se solapa o se toca y ordena`() {
        assertEquals(listOf(s(0, 300), s(500, 600)), mergeDetailSpans(listOf(s(500, 600), s(0, 200), s(150, 300))))
        assertEquals(listOf(s(0, 400)), mergeDetailSpans(listOf(s(0, 200), s(200, 400))))
        assertEquals(emptyList<DetailSourceSpan>(), mergeDetailSpans(listOf(s(100, 100))))
    }

    @Test
    fun `lo que nadie usa es el complemento de la union de los tramos`() {
        assertEquals(
            listOf(s(0, 300), s(800, 1_200), s(1_800, 3_420)),
            detailUnusedSpans(listOf(s(300, 800), s(1_200, 1_800)), 3_420)
        )
    }

    @Test
    fun `sin hermanos el unico tramo usado deja dos huecos como antes`() {
        assertEquals(listOf(s(0, 300), s(800, 3_420)), detailUnusedSpans(listOf(s(300, 800)), 3_420))
    }

    @Test
    fun `un tramo pegado al inicio o al final no deja hueco vacio`() {
        assertEquals(listOf(s(500, 1_000)), detailUnusedSpans(listOf(s(0, 500)), 1_000))
        assertEquals(listOf(s(0, 500)), detailUnusedSpans(listOf(s(500, 1_000)), 1_000))
        assertEquals(emptyList<DetailSourceSpan>(), detailUnusedSpans(listOf(s(0, 1_000)), 1_000))
    }

    @Test
    fun `un tramo mas alla del archivo se acota y duracion invalida no tiene huecos`() {
        assertEquals(listOf(s(0, 400)), detailUnusedSpans(listOf(s(400, 5_000)), 1_000))
        assertEquals(emptyList<DetailSourceSpan>(), detailUnusedSpans(listOf(s(0, 10)), 0))
    }

    @Test
    fun `sin tramos usados todo el archivo es hueco`() {
        assertEquals(listOf(s(0, 1_000)), detailUnusedSpans(emptyList(), 1_000))
    }

    @Test
    fun `los hermanos solo cuentan por lo que NO cubre el clip abierto`() {
        val open = s(300, 800)
        // Un hermano a cada lado y otro que se solapa con el clip abierto.
        assertEquals(
            listOf(s(100, 300), s(800, 1_000), s(1_400, 1_600)),
            detailSiblingOnlySpans(open, listOf(s(100, 300), s(700, 1_000), s(1_400, 1_600)))
        )
        // Un hermano totalmente dentro del abierto no aporta nada; sin hermanos, nada.
        assertEquals(emptyList<DetailSourceSpan>(), detailSiblingOnlySpans(open, listOf(s(400, 600))))
        assertEquals(emptyList<DetailSourceSpan>(), detailSiblingOnlySpans(open, emptyList()))
    }

    @Test
    fun `un hermano que envuelve al clip abierto deja sus dos lados`() {
        assertEquals(listOf(s(0, 300), s(800, 1_000)), detailSiblingOnlySpans(s(300, 800), listOf(s(0, 1_000))))
    }

    @Test
    fun `caso del Recorte x3 - el clip abierto es el primer trozo y los otros dos se ven como hermanos`() {
        // Tres tramos de un archivo de 3420 ms; el abierto es el primero.
        val first = s(300, 800)
        val others = listOf(s(1_200, 1_800), s(2_100, 2_600))
        assertEquals(others, detailSiblingOnlySpans(first, others))
        assertEquals(
            listOf(s(0, 300), s(800, 1_200), s(1_800, 2_100), s(2_600, 3_420)),
            detailUnusedSpans(listOf(first) + others, 3_420)
        )
    }
}
