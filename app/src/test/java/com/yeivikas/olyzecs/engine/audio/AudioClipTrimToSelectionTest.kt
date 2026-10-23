package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regla pura de "Recorte" de la ventana ampliada del audio ([trimClipToSelection]). */
class AudioClipTrimToSelectionTest {

    private fun sel(a: Double, b: Double) = PreviewSourceSelection(a, b)

    @Test
    fun `selecciona un tramo interior - clip nuevo con inicio, largo y recorte del tramo`() {
        // Clip entero de un archivo de 10 s, ubicado en 2 s del proyecto; selección 3 s..7 s del archivo.
        val t = trimClipToSelection(2_000, 10_000, 0, 10_000, sel(3_000.0, 7_000.0))!!
        assertEquals(5_000L, t.timelineStartMs) // 2000 + (3000 - 0)
        assertEquals(4_000L, t.clipLengthMs)
        assertEquals(3_000L, t.trimStartMs)
        assertFalse(t.keepsFadeIn)
        assertFalse(t.keepsFadeOut)
    }

    @Test
    fun `el sonido de cada instante no cambia - la posicion del proyecto sigue a la del archivo`() {
        val clipStart = 1_500L
        val trim = 2_000L
        val t = trimClipToSelection(clipStart, 6_000, trim, 10_000, sel(4_000.0, 5_000.0))!!
        // Un instante p del archivo sonaba en `clipStart + (p - trim)`; tras recortar debe sonar en el mismo lugar.
        for (p in 4_000L..4_999L step 250L) {
            assertEquals(clipStart + (p - trim), t.timelineStartMs + (p - t.trimStartMs))
        }
    }

    @Test
    fun `una seleccion que sobresale por los lados se acota a lo que suena`() {
        // Suena [2000, 8000) del archivo; la selección va de 0 a 9 s.
        val t = trimClipToSelection(0, 6_000, 2_000, 10_000, sel(0.0, 9_000.0))!!
        assertEquals(2_000L, t.trimStartMs)
        assertEquals(6_000L, t.clipLengthMs)
        assertEquals(0L, t.timelineStartMs)
        assertTrue(t.keepsFadeIn)
        assertTrue(t.keepsFadeOut)
    }

    @Test
    fun `solo se conserva el fundido del borde que no se movio`() {
        val onlyStartMoved = trimClipToSelection(0, 10_000, 0, 10_000, sel(1_000.0, 10_000.0))!!
        assertFalse(onlyStartMoved.keepsFadeIn)
        assertTrue(onlyStartMoved.keepsFadeOut)
        val onlyEndMoved = trimClipToSelection(0, 10_000, 0, 10_000, sel(0.0, 9_000.0))!!
        assertTrue(onlyEndMoved.keepsFadeIn)
        assertFalse(onlyEndMoved.keepsFadeOut)
    }

    @Test
    fun `una seleccion fuera de lo que suena no recorta nada`() {
        assertNull(trimClipToSelection(0, 3_000, 5_000, 10_000, sel(0.0, 4_000.0)))   // antes del recorte
        assertNull(trimClipToSelection(0, 3_000, 5_000, 10_000, sel(8_500.0, 9_500.0))) // despues del final audible
    }

    @Test
    fun `con loop el clip largo recorta solo el primer pase audible`() {
        // Archivo de 4 s, clip de 10 s (loop): el primer pase es [0, 4000).
        val t = trimClipToSelection(0, 10_000, 0, 4_000, sel(1_000.0, 3_000.0))!!
        assertEquals(1_000L, t.timelineStartMs)
        assertEquals(2_000L, t.clipLengthMs)
        assertEquals(1_000L, t.trimStartMs)
        assertFalse(t.keepsFadeOut)
    }

    @Test
    fun `el resultado nunca es mas largo ni se sale del clip original`() {
        val t = trimClipToSelection(500, 5_000, 1_000, 20_000, sel(0.0, 20_000.0))!!
        assertTrue(t.timelineStartMs >= 500L)
        assertTrue(t.timelineStartMs + t.clipLengthMs <= 500L + 5_000L)
    }

    @Test
    fun `los extremos se redondean al milisegundo mas cercano`() {
        val t = trimClipToSelection(0, 10_000, 0, 10_000, sel(1_000.4, 2_000.6))!!
        assertEquals(1_000L, t.trimStartMs)
        assertEquals(1_001L, t.clipLengthMs)
    }

    @Test
    fun `un tramo menor al largo minimo del clip no se aplica y exactamente el minimo si`() {
        assertNull(trimClipToSelection(0, 10_000, 0, 10_000, sel(1_000.0, 1_000.0 + MIN_AUDIO_CLIP_LENGTH_MS - 1)))
        val t = trimClipToSelection(0, 10_000, 0, 10_000, sel(1_000.0, 1_000.0 + MIN_AUDIO_CLIP_LENGTH_MS))!!
        assertEquals(MIN_AUDIO_CLIP_LENGTH_MS, t.clipLengthMs)
    }

    @Test
    fun `lo audible dentro de la seleccion cuenta, no la seleccion entera`() {
        // Selección de 2 s pero solo 50 ms caen en lo que suena [2000, 8000): no alcanza el mínimo.
        assertNull(trimClipToSelection(0, 6_000, 2_000, 10_000, sel(0.0, 2_050.0)))
    }

    @Test
    fun `clip o archivo vacio no tienen nada que recortar`() {
        assertNull(trimClipToSelection(0, 0, 0, 10_000, sel(0.0, 1_000.0)))
        assertNull(trimClipToSelection(0, 5_000, 0, 0, sel(0.0, 1_000.0)))
    }

    // ---------------------------------------------------------------- varios rangos (Seleccionar x2 … x7)

    private fun trims(clipStart: Long, len: Long, trim: Long, src: Long, vararg r: Pair<Double, Double>) =
        trimClipToSelections(clipStart, len, trim, src, r.map { sel(it.first, it.second) })

    @Test
    fun `varios rangos dan un recorte por rango en el orden del archivo`() {
        val t = trims(0, 10_000, 0, 10_000, 1_000.0 to 2_000.0, 4_000.0 to 5_500.0, 8_000.0 to 9_000.0)
        assertEquals(listOf(1_000L, 4_000L, 8_000L), t.map { it.trimStartMs })
        assertEquals(listOf(1_000L, 1_500L, 1_000L), t.map { it.clipLengthMs })
    }

    @Test
    fun `cada tramo conserva su posicion en el proyecto - lo no seleccionado queda como hueco`() {
        val clipStart = 3_000L
        val trim = 500L
        val t = trims(clipStart, 9_000, trim, 20_000, 1_000.0 to 2_000.0, 6_000.0 to 7_000.0)
        for (piece in t) {
            for (p in piece.trimStartMs until piece.trimStartMs + piece.clipLengthMs step 250L) {
                assertEquals(clipStart + (p - trim), piece.timelineStartMs + (p - piece.trimStartMs))
            }
        }
        // Hueco entre ambos tramos: el primero termina antes de que empiece el segundo.
        assertTrue(t[0].timelineStartMs + t[0].clipLengthMs < t[1].timelineStartMs)
    }

    @Test
    fun `los tramos nunca se solapan ni salen del clip original`() {
        val t = trims(500, 5_000, 1_000, 20_000, 0.0 to 2_000.0, 2_500.0 to 3_000.0, 5_000.0 to 20_000.0)
        for (i in t.indices) {
            assertTrue(t[i].timelineStartMs >= 500L)
            assertTrue(t[i].timelineStartMs + t[i].clipLengthMs <= 500L + 5_000L)
            if (i > 0) assertTrue(t[i - 1].timelineStartMs + t[i - 1].clipLengthMs <= t[i].timelineStartMs)
        }
    }

    @Test
    fun `los fundidos solo se conservan en los extremos del clip original`() {
        // Tres tramos; el primero empieza en el inicio del clip y el ultimo termina en su final.
        val t = trims(0, 10_000, 0, 10_000, 0.0 to 1_000.0, 4_000.0 to 5_000.0, 9_000.0 to 10_000.0)
        assertEquals(listOf(true, false, false), t.map { it.keepsFadeIn })
        assertEquals(listOf(false, false, true), t.map { it.keepsFadeOut })
        // Si el primero NO arranca en el inicio, nadie conserva fade-in.
        val moved = trims(0, 10_000, 0, 10_000, 500.0 to 1_500.0, 4_000.0 to 5_000.0)
        assertEquals(listOf(false, false), moved.map { it.keepsFadeIn })
        assertEquals(listOf(false, false), moved.map { it.keepsFadeOut })
    }

    @Test
    fun `un rango demasiado corto o fuera de lo audible se descarta sin invalidar a los demas`() {
        val short = 2_000.0 + MIN_AUDIO_CLIP_LENGTH_MS - 1
        val t = trims(0, 6_000, 2_000, 10_000, 0.0 to 1_000.0, 2_000.0 to short, 3_000.0 to 4_000.0, 9_000.0 to 9_900.0)
        assertEquals(listOf(3_000L), t.map { it.trimStartMs })
    }

    @Test
    fun `sin ningun rango valido no hay recorte`() {
        assertTrue(trimClipToSelections(0, 10_000, 0, 10_000, emptyList()).isEmpty())
        assertTrue(trims(0, 3_000, 5_000, 10_000, 0.0 to 4_000.0, 8_500.0 to 9_500.0).isEmpty())
    }

    @Test
    fun `el orden de entrada no importa y los rangos que se tocan se fusionan`() {
        val t = trims(0, 10_000, 0, 10_000, 5_000.0 to 6_000.0, 1_000.0 to 2_000.0, 2_000.0 to 3_000.0)
        assertEquals(listOf(1_000L, 5_000L), t.map { it.trimStartMs })
        assertEquals(listOf(2_000L, 1_000L), t.map { it.clipLengthMs })
    }

    @Test
    fun `un solo rango en la version plural coincide con la version singular`() {
        val single = trimClipToSelection(2_000, 10_000, 0, 10_000, sel(3_000.0, 7_000.0))
        val plural = trimClipToSelections(2_000, 10_000, 0, 10_000, listOf(sel(3_000.0, 7_000.0)))
        assertEquals(listOf(single), plural)
    }

    // ---------------------------------------------------------------- reglas que arma el clip resultante

    @Test
    fun `el fundido de un tramo es el original solo en el extremo que conserva`() {
        val keeps = ClipTrim(0, 1_000, 0, keepsFadeIn = true, keepsFadeOut = false)
        assertEquals(300L, keeps.resolvedFadeInMs(300L))
        assertEquals(0L, keeps.resolvedFadeOutMs(400L))
        val keepsOut = ClipTrim(0, 1_000, 0, keepsFadeIn = false, keepsFadeOut = true)
        assertEquals(0L, keepsOut.resolvedFadeInMs(300L))
        assertEquals(400L, keepsOut.resolvedFadeOutMs(400L))
    }

    @Test
    fun `un unico tramo igual al clip no cambia nada pero uno distinto o varios si`() {
        val same = trimClipToSelections(2_000, 10_000, 0, 10_000, listOf(sel(0.0, 10_000.0)))
        assertTrue(same.leavesClipUnchanged(2_000, 10_000))
        val shorter = trimClipToSelections(2_000, 10_000, 0, 10_000, listOf(sel(0.0, 9_000.0)))
        assertFalse(shorter.leavesClipUnchanged(2_000, 10_000))
        val two = trimClipToSelections(2_000, 10_000, 0, 10_000, listOf(sel(0.0, 4_000.0), sel(5_000.0, 10_000.0)))
        assertFalse(two.leavesClipUnchanged(2_000, 10_000))
        assertFalse(emptyList<ClipTrim>().leavesClipUnchanged(2_000, 10_000))
    }
}
