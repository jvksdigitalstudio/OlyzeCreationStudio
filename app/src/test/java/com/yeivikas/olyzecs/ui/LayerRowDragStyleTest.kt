package com.yeivikas.olyzecs.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato visual de la fila arrastrada ([layerRowSurface], LayerRowDragStyle.kt).
 *
 * Regresión: al reordenar, la fila arrastrada pintaba un fondo opaco gris-azulado encima del color
 * de la capa y se veía "transparente"/apagada frente a las vecinas. La fila levantada debe ser MÁS
 * intensa que en reposo, y las dos clases de fila deben compartir UN solo estilo.
 */
class LayerRowDragStyleTest {

    private val moduleDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "src/main/java").isDirectory }

    private fun ui(name: String): String {
        val f = File(moduleDir, "src/main/java/com/yeivikas/olyzecs/ui/$name")
        assertTrue("Falta ${f.path}", f.isFile)
        return f.readText()
    }

    @Test
    fun `la fila levantada es mas intensa que en reposo y nunca translucida`() {
        assertTrue(LIFTED_WASH_ALPHA > REST_WASH_ALPHA)
        assertEquals(0.30f, REST_WASH_ALPHA, 1e-6f)
        assertTrue(LIFTED_EDGE_ALPHA in 0f..1f && LIFTED_EDGE_ALPHA > 0f)
    }

    @Test
    fun `la fila levantada queda por encima de las vecinas`() {
        assertTrue(LIFTED_Z_INDEX > 0f)
    }

    @Test
    fun `capa de imagen y pista de audio usan el mismo estilo compartido`() {
        for (file in listOf("TimelineView.kt", "AudioTrackRow.kt")) {
            assertTrue("$file debe usar layerRowSurface", ".layerRowSurface(trackBrush, isDragging)" in ui(file))
        }
    }

    @Test
    fun `no queda el fondo opaco viejo que tapaba el color de la fila arrastrada`() {
        for (file in listOf("TimelineView.kt", "AudioTrackRow.kt")) {
            assertFalse(
                "$file no debe volver a tapar el color con SurfaceTintedElevated al arrastrar",
                Regex("""if \(isDragging\)\s*\{\s*Modifier\s*\.shadow""").containsMatchIn(ui(file))
            )
        }
    }

    @Test
    fun `el hueco destino sigue a la fila de origen desplazada por los pasos`() {
        assertEquals(null, dragTargetSlotTopPx(draggingIndex = -1, steps = 3, rowHeightPx = 100f))
        assertEquals(200f, dragTargetSlotTopPx(2, 0, 100f)!!, 1e-4f)
        assertEquals(500f, dragTargetSlotTopPx(2, 3, 100f)!!, 1e-4f)
        assertEquals(0f, dragTargetSlotTopPx(2, -2, 100f)!!, 1e-4f)
    }

    @Test
    fun `la lista dibuja el hueco destino con el helper unico`() {
        assertTrue(".dragTargetSlot(dragTargetSlotTopPx(" in ui("TimelineView.kt"))
    }

    @Test
    fun `el indicador de reordenar lo usan ambas filas y solo se dibuja arrastrando`() {
        for (file in listOf("TimelineView.kt", "AudioTrackRow.kt")) {
            assertTrue("$file debe aplicar reorderArrowsBadge", ".reorderArrowsBadge(isDragging)" in ui(file))
        }
        assertTrue("sin arrastre no debe dibujar nada", "if (!isDragging) return this" in ui("LayerRowDragStyle.kt"))
    }

    @Test
    fun `el icono de reordenar trae las dos flechas del SVG`() {
        val f = File(moduleDir, "src/main/res/drawable/ic_reorder_arrows.xml")
        assertTrue("Falta ${f.path}", f.isFile)
        val xml = f.readText()
        assertTrue("M18,10 l-6,-6 l-6,6 h12" in xml)
        assertTrue("M18,14 l-6,6 l-6,-6 h12" in xml)
    }
}
