package com.yeivikas.olyzecs.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del contraste automático de los íconos del panel de opciones ([rowIconPalette]).
 * Regresión: sobre un panel cian/amarillo los íconos blancos y el rojo de "Eliminar" se perdían, y
 * sobre rosa/magenta el rojo desaparecía; los colores estaban fijos sin mirar el fondo.
 */
class RowIconContrastTest {

    private val dark = SurfaceTintedDark
    private val cyan = Color(0xFF00FFE0)
    private val hotPink = Color(0xFFFF0080)
    private val chromaGreen = Color(0xFF00B140)

    @Test
    fun `luminancia y contraste de referencia WCAG`() {
        assertEquals(1f, relativeLuminance(Color.White), 1e-4f)
        assertEquals(0f, relativeLuminance(Color.Black), 1e-4f)
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 1e-2f)
        assertEquals(1f, contrastRatio(cyan, cyan), 1e-4f)
    }

    @Test
    fun `sobre fondo oscuro los iconos base siguen blancos`() {
        val palette = rowIconPalette(listOf(dark))
        assertEquals(Color.White, palette.content)
        assertFalse(palette.invertOriginalTones)
    }

    @Test
    fun `sobre el rosa fuerte se conserva el blanco que ya se leia bien`() {
        assertEquals(Color.White, rowIconPalette(listOf(hotPink)).content)
    }

    @Test
    fun `sobre fondos claros el icono base pasa al tinte oscuro`() {
        for (background in listOf(cyan, chromaGreen, Color(0xFFFFEB3B))) {
            val palette = rowIconPalette(listOf(background))
            assertEquals(RowIconInk, palette.content)
            assertTrue(palette.invertOriginalTones)
        }
    }

    @Test
    fun `el icono eliminar se ve sobre oscuro, rosa y cian y conserva su rojo donde ya contrasta`() {
        assertEquals(Color(0xFFFF6B6B), rowIconPalette(listOf(dark)).danger)
        for (background in listOf(hotPink, cyan, Color(0xFFE91E63))) {
            val danger = rowIconPalette(listOf(background)).danger
            assertTrue(
                "Eliminar no contrasta sobre $background: ${contrastRatio(danger, background)}",
                contrastRatio(danger, background) >= ICON_MIN_CONTRAST
            )
        }
    }

    @Test
    fun `ensureContrast no toca un color que ya contrasta`() {
        val base = Color(0xFFFF6B6B)
        assertEquals(base, ensureContrast(base, listOf(dark)))
    }

    @Test
    fun `cualquier fondo opaco deja todos los iconos con contraste minimo`() {
        val steps = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
        for (r in steps) for (g in steps) for (b in steps) {
            val background = Color(r, g, b)
            val palette = rowIconPalette(listOf(background))
            for ((name, color) in listOf(
                "content" to palette.content,
                "danger" to palette.danger,
                "lockOn" to palette.lockOn,
                "orderLockOn" to palette.orderLockOn
            )) {
                assertTrue(
                    "$name sin contraste sobre $background: ${contrastRatio(color, background)}",
                    contrastRatio(color, background) >= ICON_MIN_CONTRAST
                )
            }
        }
    }

    @Test
    fun `con degradado se evalua el peor extremo`() {
        val palette = rowIconPalette(listOf(cyan, dark))
        val worst = listOf(cyan, dark)
        assertTrue(worstContrast(palette.content, worst) >= worstContrast(Color.White, worst) ||
            worstContrast(palette.content, worst) >= worstContrast(RowIconInk, worst))
    }

    @Test
    fun `los dibujos con colores propios no se tiñen`() {
        assertEquals(Color.Unspecified, rowIconPalette(listOf(dark)).colorFor(ActionTone.Original))
    }

    @Test
    fun `las filas usan la paleta y no colores fijos para sus acciones`() {
        val ui = File(generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "src/main/java").isDirectory }, "src/main/java/com/yeivikas/olyzecs/ui")
        for (name in listOf("TimelineView.kt", "AudioTrackRow.kt")) {
            val src = File(ui, name).readText()
            assertFalse("$name aún fija el rojo de eliminar", "tint = Color(0xFFFF6B6B)" in src)
            assertFalse("$name aún usa tint en LayerAction", "action.tint" in src)
            assertTrue("$name debe calcular rowIconPalette", "rowIconPalette(" in src)
        }
    }
}
