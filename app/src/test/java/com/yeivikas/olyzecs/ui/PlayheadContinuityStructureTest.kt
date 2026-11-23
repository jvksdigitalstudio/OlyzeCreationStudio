package com.yeivikas.olyzecs.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda que la línea del playhead se vea CONTINUA entre filas.
 *
 * El playhead no es un overlay: cada fila dibuja su tramo dentro de su propio carril ([PlayheadLaneSegment]). Cualquier
 * elemento hermano de la fila que ocupe altura fuera de ese carril (p. ej. un `HorizontalDivider` encima de la fila de
 * audio) deja un hueco sin línea. Además la fila de audio no debe llevar ningún filete/separador propio: las filas de
 * capa no lo tienen, y uno solo en el audio se ve como una brecha entre filas contiguas.
 *
 * Test ESTRUCTURAL sobre el archivo real (JVM puro), como [AudioAddClipButtonGestureScaleStructureTest].
 */
class PlayheadContinuityStructureTest {

    private val moduleDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "src/main/java").isDirectory }

    private fun ui(name: String): String {
        val f = File(moduleDir, "src/main/java/com/yeivikas/olyzecs/ui/$name")
        assertTrue("Falta ${f.path}", f.isFile)
        return f.readText()
    }

    @Test
    fun `la fila de audio no antepone un divisor hermano que corte el playhead`() {
        assertFalse(
            "AudioTrackRow no debe usar HorizontalDivider como hermano de la fila: corta la línea del playhead",
            "HorizontalDivider(" in ui("AudioTrackRow.kt")
        )
    }

    @Test
    fun `la fila de audio no dibuja filete ni separador propio en su borde`() {
        val src = ui("AudioTrackRow.kt")
        assertFalse("La fila de audio no debe dibujar un filete propio (.drawBehind): se ve como brecha", ".drawBehind {" in src)
        assertFalse("Quedó una constante de filete superior en AudioTrackRow", "AUDIO_ROW_TOP_HAIRLINE" in src)
    }

    @Test
    fun `la fila de audio y la de capa miden lo mismo para quedar contiguas`() {
        val audio = Regex("""AUDIO_TRACK_ROW_HEIGHT\s*=\s*([0-9.]+)\.dp""").find(ui("AudioTrackRow.kt"))?.groupValues?.get(1)
        val layer = Regex("""private val ROW_HEIGHT\s*=\s*([0-9.]+)\.dp""").find(ui("TimelineView.kt"))?.groupValues?.get(1)
        assertTrue("No se encontró AUDIO_TRACK_ROW_HEIGHT", audio != null)
        assertTrue("No se encontró ROW_HEIGHT", layer != null)
        org.junit.Assert.assertEquals("Alto de fila de audio distinto al de capa: las filas dejarían de ser contiguas", layer, audio)
    }
}
