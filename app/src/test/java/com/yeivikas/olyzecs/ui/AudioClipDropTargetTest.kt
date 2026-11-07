package com.yeivikas.olyzecs.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.yeivikas.olyzecs.engine.audio.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Geometría pura del arrastre de un clip entre carriles: carril destino, inicio y auto-scroll. */
class AudioClipDropTargetTest {

    private val tracks = listOf(AudioTrack(id = "a"), AudioTrack(id = "b"))

    // Dos carriles contiguos de 36 px de alto; el carril empieza en x = 72 y mide 1_000 px.
    private val lanes = mapOf(
        "a" to Rect(72f, 100f, 1_072f, 136f),
        "b" to Rect(72f, 136f, 1_072f, 172f)
    )

    private fun resolve(
        finger: Offset,
        grabOffsetPx: Float = 0f,
        pxPerMs: Float = 0.1f, // 1_000 px = 10_000 ms
        laneBounds: Map<String, Rect?> = lanes
    ) = resolveClipDropTarget(
        tracks = tracks,
        laneBoundsOf = { laneBounds[it] },
        clipId = "c",
        clipLengthMs = 1_000L,
        fingerWindow = finger,
        grabOffsetPx = grabOffsetPx,
        pxPerMs = pxPerMs,
        projectDurationMs = 10_000L,
        playheadMs = -1L,
        snapThresholdMs = 0L
    )

    @Test
    fun `el carril destino es el que tiene el dedo en su franja vertical`() {
        assertEquals("a", resolve(Offset(500f, 110f))?.trackId)
        assertEquals("b", resolve(Offset(500f, 150f))?.trackId)
    }

    @Test
    fun `el inicio sale de la X del dedo menos donde agarro el clip`() {
        // X local = 572 - 72 - 20 = 480 px → 4_800 ms.
        assertEquals(4_800L, resolve(Offset(572f, 110f), grabOffsetPx = 20f)?.startMs)
    }

    @Test
    fun `soltar mas alla del borde izquierdo ubica el clip al inicio`() {
        assertEquals(0L, resolve(Offset(10f, 110f))?.startMs)
    }

    @Test
    fun `soltar mas alla del borde derecho deja el clip entero dentro del proyecto`() {
        assertEquals(9_000L, resolve(Offset(5_000f, 110f))?.startMs)
    }

    @Test
    fun `el destino lleva los limites del carril para dibujar la vista previa`() {
        assertEquals(lanes["b"], resolve(Offset(500f, 150f))?.laneBounds)
    }

    @Test
    fun `sin carril de audio bajo el dedo no hay destino`() {
        assertNull(resolve(Offset(500f, 300f)))
    }

    @Test
    fun `un carril aun sin medir no captura el dedo`() {
        assertEquals("b", resolve(Offset(500f, 150f), laneBounds = mapOf("a" to null, "b" to lanes["b"]))?.trackId)
        assertNull(resolve(Offset(500f, 110f), laneBounds = mapOf("a" to null, "b" to lanes["b"])))
    }

    @Test
    fun `sin escala no hay destino`() {
        assertNull(resolve(Offset(500f, 110f), pxPerMs = 0f))
    }

    @Test
    fun `un carril parcialmente recortado por el scroll entrega sus limites visibles`() {
        val clipped = Rect(72f, 100f, 1_072f, 118f)
        assertEquals(clipped, resolve(Offset(500f, 110f), laneBounds = mapOf("a" to clipped, "b" to lanes["b"]))?.laneBounds)
    }

    // ---------- edgeAutoScrollDeltaPx ----------

    private fun scroll(pointerY: Float) =
        edgeAutoScrollDeltaPx(pointerY, viewportTopPx = 200f, viewportBottomPx = 800f, edgePx = 40f, maxStepPx = 12f)

    @Test
    fun `en el interior de la zona visible no se desplaza`() {
        assertEquals(0f, scroll(500f), 0f)
    }

    @Test
    fun `al entrar en la franja inferior se desplaza hacia abajo en proporcion`() {
        assertEquals(6f, scroll(780f), 0.001f) // mitad de la franja
        assertEquals(12f, scroll(800f), 0.001f) // en el borde
    }

    @Test
    fun `al entrar en la franja superior se desplaza hacia arriba en proporcion`() {
        assertEquals(-6f, scroll(220f), 0.001f)
        assertEquals(-12f, scroll(200f), 0.001f)
    }

    @Test
    fun `fuera de la zona visible la velocidad no pasa del maximo`() {
        assertEquals(12f, scroll(1_500f), 0f)
        assertEquals(-12f, scroll(-300f), 0f)
    }

    @Test
    fun `sin franja o sin zona visible no se desplaza`() {
        assertEquals(0f, edgeAutoScrollDeltaPx(790f, 200f, 800f, edgePx = 0f, maxStepPx = 12f), 0f)
        assertEquals(0f, edgeAutoScrollDeltaPx(790f, 800f, 800f, edgePx = 40f, maxStepPx = 12f), 0f)
    }
}
