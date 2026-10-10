package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de colocación del panel de acciones de una fila
 * ([rowCrossesBandTop], [rowCrossesBandBottom], [rowFitsVisibleBand],
 * [nextFootMode] y [RowActionsPanelPlacement]): cuándo el panel va al costado
 * (dibujado dentro de la fila, [RowSideActionsPanelHost]) y cuándo al pie
 * (acordeón, [LayerActionAccordion]).
 *
 * Regresiones cubiertas:
 *  - el panel de la última capa se superponía a la barra inferior;
 *  - al bajar con scroll, el panel de una capa abierta más arriba se montaba
 *    sobre la barra Módulos/Control/Keyframes en vez de pasar al pie;
 *  - al SUBIR con scroll hasta que la fila vuelve a caber, el panel se
 *    quedaba al pie en vez de volver al costado (el modo era un pestillo de
 *    un solo sentido); ahora sigue la geometría en vivo con histéresis
 *    ([nextFootMode]) y sin bucle costado ⇄ pie.
 *
 * (El desfase del panel respecto a su fila durante el scroll ya no es
 * testeable aquí: desapareció al dejar de ser una ventana `Popup` aparte.)
 */
class LastRowActionsPanelPlacementTest {

    // Zona visible típica: el Master termina en 1257, la barra inferior empieza en 2225.
    private val band = ListVisibleBand(topPx = 1257, bottomPx = 2225)
    private val row = 64        // alto de una fila en px
    private val accordion = 100 // alto del acordeón al pie en px

    private fun placement() = RowActionsPanelPlacement(tolerancePx = 0, accordionHeightPx = accordion)

    // --- funciones puras ---

    @Test
    fun `fila en medio de la zona - cabe y no cruza ningun borde`() {
        assertTrue(rowFitsVisibleBand(1500, 1500 + row, band))
        assertFalse(rowCrossesBandTop(1500, band))
        assertFalse(rowCrossesBandBottom(1500 + row, band))
    }

    @Test
    fun `fila pegada al borde superior - cabe sin tolerancia`() {
        assertTrue(rowFitsVisibleBand(1257, 1257 + row, band))
    }

    @Test
    fun `fila medio escondida bajo el Master - cruza por arriba`() {
        assertTrue(rowCrossesBandTop(1257 - row / 2, band))
        assertFalse(rowFitsVisibleBand(1257 - row / 2, 1257 + row / 2, band))
    }

    @Test
    fun `fila que termina dentro de la franja de la barra - cruza por abajo`() {
        assertTrue(rowCrossesBandBottom(2262, band))
        assertFalse(rowFitsVisibleBand(2205, 2262, band))
    }

    @Test
    fun `fila que termina justo en el borde de la barra - cabe`() {
        assertTrue(rowFitsVisibleBand(2225 - row, 2225, band))
    }

    @Test
    fun `la tolerancia absorbe el redondeo en ambos bordes`() {
        assertTrue(rowCrossesBandTop(1255, band, tolerancePx = 0))
        assertFalse(rowCrossesBandTop(1255, band, tolerancePx = 4))
        assertTrue(rowCrossesBandBottom(2228, band, tolerancePx = 0))
        assertFalse(rowCrossesBandBottom(2228, band, tolerancePx = 4))
    }

    @Test
    fun `sin banda medida - se asume que cabe`() {
        assertTrue(rowFitsVisibleBand(-5000, 5000, band = null))
        assertFalse(rowCrossesBandTop(-5000, band = null))
        assertFalse(rowCrossesBandBottom(5000, band = null))
    }

    @Test
    fun `nextFootMode - sin banda medida conserva el modo`() {
        assertTrue(nextFootMode(true, 0, 10, null, 0, accordion))
        assertFalse(nextFootMode(false, 0, 10, null, 0, accordion))
    }

    // --- RowActionsPanelPlacement: modo de partida al abrir ---

    @Test
    fun `al abrir - la ultima fila pegada a la barra va al pie`() {
        val p = placement()
        p.onRowPositioned(2225 - row / 2, 2225 + row / 2, band)
        p.latch(band)
        assertTrue(p.footLatched)
    }

    @Test
    fun `al abrir - la ultima fila que cabe entera va al costado`() {
        val p = placement()
        p.onRowPositioned(1500, 1500 + row, band) // lista corta: nada la empuja bajo la barra
        p.latch(band)
        assertFalse(p.footLatched)
    }

    @Test
    fun `al abrir - fila bajo la barra va al pie`() {
        val p = placement()
        p.onRowPositioned(2205, 2262, band)
        p.latch(band)
        assertTrue(p.footLatched)
    }

    @Test
    fun `al abrir - fila medio escondida arriba va al pie y no deja un toque muerto`() {
        val p = placement()
        p.onRowPositioned(1257 - row / 2, 1257 + row / 2, band)
        p.latch(band)
        assertTrue(p.footLatched)
    }

    @Test
    fun `al abrir - fila del medio va al costado`() {
        val p = placement()
        p.onRowPositioned(1500, 1500 + row, band)
        p.latch(band)
        assertFalse(p.footLatched)
    }

    @Test
    fun `reabrir recalcula el modo con la posicion nueva`() {
        val p = placement()
        p.onRowPositioned(2205, 2262, band)
        p.latch(band)
        assertTrue(p.footLatched)

        p.onRowPositioned(1500, 1500 + row, band) // scroll antes de reabrir
        p.latch(band)
        assertFalse(p.footLatched)
    }

    // --- panel ya abierto, el scroll lo mueve ---

    @Test
    fun `abierto al costado - mientras la fila asoma parte bajo el Master NO cambia de modo`() {
        val p = placement()
        p.onRowPositioned(1500, 1500 + row, band)
        p.latch(band)

        p.onRowPositioned(1257 - row / 2, 1257 + row / 2, band)
        assertFalse(p.footLatched)

        p.onRowPositioned(1257 - row, 1257, band) // totalmente bajo el Master
        assertFalse(p.footLatched)
    }

    @Test
    fun `abierto al costado - al bajar a la barra pasa solo al pie`() {
        val p = placement()
        p.onRowPositioned(2000, 2000 + row, band)
        p.latch(band)
        assertFalse(p.footLatched)

        p.onRowPositioned(2205, 2262, band) // scroll hacia abajo: entra en la franja
        assertTrue(p.footLatched)
    }

    @Test
    fun `abierto al pie - al subir hasta que la fila cabe con margen vuelve al costado`() {
        val p = placement()
        p.onRowPositioned(2000, 2000 + row, band)
        p.latch(band)
        p.onRowPositioned(2205, 2262, band) // al bajar: pasa a pie
        assertTrue(p.footLatched)

        // Al subir con el dedo: la fila queda entera y sobra sitio de acordeón.
        p.onRowPositioned(2000, 2000 + row, band)
        assertFalse(p.footLatched)
    }

    @Test
    fun `abierto al pie - fila pegada a la barra NO vuelve al costado (sin bucle con el auto-scroll)`() {
        val p = placement()
        p.onRowPositioned(2205, 2262, band)
        p.latch(band)
        assertTrue(p.footLatched)

        // El auto-scroll del acordeón deja la fila justo sobre la barra:
        // cabe, pero SIN margen para el acordeón -> sigue al pie.
        p.onRowPositioned(2225 - row, 2225, band)
        assertTrue(p.footLatched)
        p.onRowPositioned(2225 - row - accordion + 5, 2225 - accordion + 5, band)
        assertTrue(p.footLatched)
    }

    @Test
    fun `abierto al pie - umbral exacto de regreso al costado`() {
        val p = placement()
        p.onRowPositioned(2205, 2262, band)
        p.latch(band)
        // bottom + acordeón == borde de la barra: justo cabe -> costado.
        p.onRowPositioned(2225 - accordion - row, 2225 - accordion, band)
        assertFalse(p.footLatched)
    }

    @Test
    fun `sin bucle - tras volver al costado el scroll que se acomoda no lo devuelve al pie`() {
        val p = placement()
        p.onRowPositioned(2205, 2262, band)
        p.latch(band)
        val bottom = 2225 - accordion
        p.onRowPositioned(bottom - row, bottom, band)
        assertFalse(p.footLatched)
        // Quitar el acordeón encoge el contenido y la fila baja como mucho
        // `accordion` px: sigue cabiendo, así que se queda al costado.
        p.onRowPositioned(bottom - row + accordion, bottom + accordion, band)
        assertFalse(p.footLatched)
    }

    @Test
    fun `abierto al pie - fila a medias bajo el Master no vuelve al costado`() {
        val p = placement()
        p.onRowPositioned(2205, 2262, band)
        p.latch(band)
        p.onRowPositioned(1257 - row / 2, 1257 + row / 2, band)
        assertTrue(p.footLatched)
    }

    @Test
    fun `cerrar y reabrir reinicia el cambio a pie`() {
        val p = placement()
        p.onRowPositioned(2205, 2262, band)
        assertTrue(p.footLatched)

        p.onRowPositioned(1500, 1500 + row, band)
        p.latch(band) // reapertura
        assertFalse(p.footLatched)
    }
}
