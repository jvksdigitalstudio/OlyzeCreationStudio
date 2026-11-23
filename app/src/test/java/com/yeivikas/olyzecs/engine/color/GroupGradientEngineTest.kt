package com.yeivikas.olyzecs.engine.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupGradientEngineTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val cyan = 0xFF00FFFF.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun hex(argb: Int) = "0x%08X".format(argb)

    private fun assertColors(expected: List<Int>, actual: IntArray) =
        assertEquals(expected.map { hex(it) }, actual.map { hex(it) })

    @Test
    fun `la posicion reparte de 0 a 1 con extremos exactos`() {
        assertEquals(0.0, GroupGradientEngine.positionOf(0, 5), 0.0)
        assertEquals(0.25, GroupGradientEngine.positionOf(1, 5), 0.0)
        assertEquals(1.0, GroupGradientEngine.positionOf(4, 5), 0.0)
    }

    @Test
    fun `con una sola fila o ninguna la posicion es 0`() {
        assertEquals(0.0, GroupGradientEngine.positionOf(0, 1), 0.0)
        assertEquals(0.0, GroupGradientEngine.positionOf(0, 0), 0.0)
    }

    @Test
    fun `una posicion fuera de rango se acota a la fila valida`() {
        assertEquals(1.0, GroupGradientEngine.positionOf(9, 3), 0.0)
        assertEquals(0.0, GroupGradientEngine.positionOf(-4, 3), 0.0)
    }

    @Test
    fun `con dos filas la primera es el inicio y la ultima el final en todos los espacios`() {
        GradientColorSpace.entries.forEach { space ->
            assertColors(listOf(red, blue), GroupGradientEngine.distribute(red, blue, 2, space))
        }
    }

    @Test
    fun `con una fila recibe el color inicial`() {
        assertColors(listOf(red), GroupGradientEngine.distribute(red, blue, 1, GradientColorSpace.HUE))
    }

    @Test
    fun `sin filas no devuelve nada`() {
        assertEquals(0, GroupGradientEngine.distribute(red, blue, 0, GradientColorSpace.HUE).size)
        assertEquals(0, GroupGradientEngine.distribute(red, blue, -3, GradientColorSpace.RGB).size)
    }

    @Test
    fun `el matiz va de rojo a azul por magenta`() {
        assertColors(
            listOf(red, 0xFFFF00FF.toInt(), blue),
            GroupGradientEngine.distribute(red, blue, 3, GradientColorSpace.HUE)
        )
        assertColors(
            listOf(red, 0xFFFF0080.toInt(), 0xFFFF00FF.toInt(), 0xFF8000FF.toInt(), blue),
            GroupGradientEngine.distribute(red, blue, 5, GradientColorSpace.HUE)
        )
    }

    @Test
    fun `con 180 grados exactos el matiz elige siempre el sentido creciente`() {
        assertColors(
            listOf(red, 0xFF80FF00.toInt(), cyan),
            GroupGradientEngine.distribute(red, cyan, 3, GradientColorSpace.HUE)
        )
    }

    @Test
    fun `un extremo sin color toma el matiz del otro`() {
        assertColors(
            listOf(white, 0xFF8080FF.toInt(), blue),
            GroupGradientEngine.distribute(white, blue, 3, GradientColorSpace.HUE)
        )
    }

    @Test
    fun `de negro a blanco el matiz da grises`() {
        assertColors(
            listOf(black, 0xFF808080.toInt(), white),
            GroupGradientEngine.distribute(black, white, 3, GradientColorSpace.HUE)
        )
    }

    @Test
    fun `rgb mezcla canal a canal y a mitad de recorrido apaga el color`() {
        assertColors(
            listOf(0xFF8000FF.toInt(), 0xFF406080.toInt(), 0xFF00C000.toInt()),
            GroupGradientEngine.distribute(0xFF8000FF.toInt(), 0xFF00C000.toInt(), 3, GradientColorSpace.RGB)
        )
    }

    @Test
    fun `el punto medio perceptual es mas oscuro que el medio aritmetico`() {
        assertColors(
            listOf(black, 0xFF636363.toInt(), white),
            GroupGradientEngine.distribute(black, white, 3, GradientColorSpace.PERCEPTUAL)
        )
        assertColors(
            listOf(red, 0xFF8C53A2.toInt(), blue),
            GroupGradientEngine.distribute(red, blue, 3, GradientColorSpace.PERCEPTUAL)
        )
    }

    @Test
    fun `el resultado es siempre opaco y el alfa de entrada se ignora`() {
        val translucentStart = 0x40FF0000
        val translucentEnd = 0x80003300.toInt()
        assertEquals(hex(0xFF801A00.toInt()), hex(GroupGradientEngine.sample(translucentStart, translucentEnd, 0.5, GradientColorSpace.RGB)))
        assertEquals(hex(0xFFFF0000.toInt()), hex(GroupGradientEngine.sample(translucentStart, translucentEnd, 0.0, GradientColorSpace.HUE)))
        assertEquals(hex(0xFF003300.toInt()), hex(GroupGradientEngine.sample(translucentStart, translucentEnd, 1.0, GradientColorSpace.HUE)))
    }

    @Test
    fun `una posicion fuera de rango o no numerica se acota`() {
        assertEquals(hex(red), hex(GroupGradientEngine.sample(red, blue, -2.0, GradientColorSpace.RGB)))
        assertEquals(hex(blue), hex(GroupGradientEngine.sample(red, blue, 7.0, GradientColorSpace.RGB)))
        assertEquals(hex(red), hex(GroupGradientEngine.sample(red, blue, Double.NaN, GradientColorSpace.RGB)))
    }

    @Test
    fun `los extremos son exactos para cualquier par de colores y espacio`() {
        val random = java.util.Random(1234L)
        repeat(2_000) {
            val a = 0xFF000000.toInt() or random.nextInt(0x1000000)
            val b = 0xFF000000.toInt() or random.nextInt(0x1000000)
            GradientColorSpace.entries.forEach { space ->
                val result = GroupGradientEngine.distribute(a, b, 4, space)
                assertEquals(hex(a), hex(result.first()))
                assertEquals(hex(b), hex(result.last()))
            }
        }
    }

    @Test
    fun `el reparto es determinista`() {
        val first = GroupGradientEngine.distribute(red, blue, 7, GradientColorSpace.HUE)
        val second = GroupGradientEngine.distribute(red, blue, 7, GradientColorSpace.HUE)
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun `el espacio de color se lee tolerante desde el guardado`() {
        assertEquals(GradientColorSpace.RGB, GradientColorSpace.fromStorage("RGB"))
        assertEquals(GradientColorSpace.DEFAULT, GradientColorSpace.fromStorage("inexistente"))
        assertEquals(GradientColorSpace.DEFAULT, GradientColorSpace.fromStorage(null))
    }

    // ------------------------------------------------------------------
    // strongAccent: realce de acento de una fila suelta
    // ------------------------------------------------------------------

    @Test
    fun `el acento de un gris sigue siendo gris y nunca se vuelve rojo`() {
        // Regresion: antes se subia la saturacion de todo color; el matiz de un
        // gris vale 0, asi que el modo Negro y Blanco terminaba en rojo.
        assertEquals(hex(0xFF868686.toInt()), hex(GroupGradientEngine.strongAccent(0xFF808080.toInt())))
        assertEquals(hex(white), hex(GroupGradientEngine.strongAccent(white)))
        assertEquals(hex(black), hex(GroupGradientEngine.strongAccent(black)))
    }

    @Test
    fun `el acento de un color con matiz sube la saturacion al maximo y realza el brillo`() {
        // (128, 64, 64): matiz 0, saturacion 0,5 -> 1, brillo 0,502 -> 0,527 (134).
        assertEquals(hex(0xFF860000.toInt()), hex(GroupGradientEngine.strongAccent(0xFF804040.toInt())))
        assertEquals(hex(red), hex(GroupGradientEngine.strongAccent(red)))
    }

    @Test
    fun `el acento siempre es opaco`() {
        assertEquals(hex(red), hex(GroupGradientEngine.strongAccent(0x80FF0000.toInt())))
    }

    // ------------------------------------------------------------------
    // midpoint: color plano de un degradado de dos extremos
    // ------------------------------------------------------------------

    @Test
    fun `el punto medio es la muestra 0,5 en Oklab`() {
        assertEquals(
            hex(GroupGradientEngine.sample(red, blue, 0.5, GradientColorSpace.PERCEPTUAL)),
            hex(GroupGradientEngine.midpoint(red, blue))
        )
    }

    @Test
    fun `el punto medio de dos colores iguales es ese color y es opaco`() {
        assertEquals(hex(cyan), hex(GroupGradientEngine.midpoint(cyan, cyan)))
        // Mismo cian, uno de ellos con alfa 25 %: el resultado sigue siendo opaco.
        assertEquals(hex(cyan), hex(GroupGradientEngine.midpoint(0x4000FFFF, cyan)))
    }

    @Test
    fun `el punto medio entre negro y blanco es el gris perceptual y no el RGB 128`() {
        // Oklab: L = 0,5 -> sRGB 99, no 128 como daria un promedio RGB.
        assertEquals(hex(0xFF636363.toInt()), hex(GroupGradientEngine.midpoint(black, white)))
    }

    // ------------------------------------------------------------------
    // Contrato medido contra FL Studio (ver ADR-015, addendum 2026-10-17)
    // ------------------------------------------------------------------

    @Test
    fun `el espacio por defecto es RGB como el degradado de FL Studio`() {
        assertEquals(GradientColorSpace.RGB, GradientColorSpace.DEFAULT)
    }

    @Test
    fun `el reparto por defecto reproduce el degradado medido de FL Studio`() {
        // Captura de referencia de FL: 23 pistas, de magenta (110, 0, 87) a
        // menta (42, 249, 159). Colores MEDIDOS en tres pistas; el motor debe
        // quedar a menos de 6 niveles por canal (la captura es un JPEG).
        val start = 0xFF6E0057.toInt()
        val end = 0xFF2AF99F.toInt()
        val rows = GroupGradientEngine.distribute(start, end, 23, GradientColorSpace.DEFAULT)
        val measured = mapOf(
            8 to intArrayOf(86, 88, 111),
            11 to intArrayOf(74, 123, 120),
            16 to intArrayOf(62, 179, 135)
        )
        measured.forEach { (row, rgb) ->
            val actual = rows[row]
            val channels = intArrayOf((actual shr 16) and 0xFF, (actual shr 8) and 0xFF, actual and 0xFF)
            for (c in 0..2) {
                assertTrue(
                    "fila $row canal $c: esperado ~${rgb[c]}, obtenido ${channels[c]}",
                    kotlin.math.abs(channels[c] - rgb[c]) <= 6
                )
            }
        }
    }

    @Test
    fun `en RGB la luminosidad de un reparto azul a lima avanza de forma pareja`() {
        // Con azul -> lima el recorrido por matiz salta casi 0,45 de luminosidad
        // en las primeras filas y queda plano en las del medio; RGB no.
        val blue = 0xFF0003FE.toInt()
        val lime = 0xFFD1FF00.toInt()
        fun steps(space: GradientColorSpace): List<Double> {
            val rows = GroupGradientEngine.distribute(blue, lime, 14, space)
            val lum = rows.map { c ->
                fun lin(v: Int): Double { val x = v / 255.0; return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
                0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
            }
            return lum.zipWithNext { a, b -> b - a }
        }
        val rgbMaxStep = steps(GradientColorSpace.RGB).max()
        val hueMaxStep = steps(GradientColorSpace.HUE).max()
        assertTrue("RGB $rgbMaxStep debe ser menor que Matiz $hueMaxStep", rgbMaxStep < hueMaxStep)
    }
}
