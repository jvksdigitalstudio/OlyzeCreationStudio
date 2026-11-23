package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [AlignmentGuides] (`computeAlignmentSnapForDrag`/
 * `computeAlignmentSnapForResize`/`snapRotationToFifteenDegrees`/
 * `solveScaleMagnitudeForTargetHalfExtent`) — motor puro de la guía de
 * encuadre/posicionamiento (Módulos > editor, arrastrar/redimensionar/
 * rotar una capa contra el centro y los bordes del lienzo). Sin tests
 * hasta esta auditoría, a pesar de ser puro y sin dependencia de Android
 * — mismo criterio de cobertura que ya tienen
 * `engine/camera/ZoomXLevelsTest`/`CameraFrameInterpolation` y, tras esta
 * misma auditoría, `engine/render/PerspectiveCameraMathTest` (ver
 * ADR-007/ADR-010).
 */
class AlignmentGuidesTest {

    private val boxWidth = 1000f
    private val boxHeight = 2000f
    private val threshold = 10f

    // ---- computeAlignmentSnapForDrag ------------------------------------

    @Test
    fun `capa centrada engancha ambos ejes al centro del lienzo`() {
        // Capa 200x200 (halfWidth/halfHeight=100) ya centrada: translateX/Y=0.
        val result = computeAlignmentSnapForDrag(
            translateX = 0f, translateY = 0f, parallaxFactor = 1f,
            halfWidthPx = 100f, halfHeightPx = 100f,
            boxWidthPx = boxWidth, boxHeightPx = boxHeight, snapThresholdPx = threshold
        )
        assertEquals(0f, result.translateX, 1e-4f)
        assertEquals(0f, result.translateY, 1e-4f)
        assertEquals(boxWidth / 2f, result.guideLineXPx)
        assertEquals(boxHeight / 2f, result.guideLineYPx)
    }

    @Test
    fun `capa lejos de cualquier linea no engancha ni dibuja guia`() {
        val result = computeAlignmentSnapForDrag(
            translateX = 0.5f, translateY = 0.5f, parallaxFactor = 1f,
            halfWidthPx = 50f, halfHeightPx = 50f,
            boxWidthPx = boxWidth, boxHeightPx = boxHeight, snapThresholdPx = threshold
        )
        assertEquals(0.5f, result.translateX, 1e-4f)
        assertEquals(0.5f, result.translateY, 1e-4f)
        assertNull(result.guideLineXPx)
        assertNull(result.guideLineYPx)
    }

    @Test
    fun `borde inicial de la capa engancha al borde izquierdo del lienzo`() {
        // Capa de halfWidth=100 con su borde izquierdo justo a 3px de x=0 (dentro del umbral de 10px).
        val translateXNearLeftEdge = ((3f + 100f) / boxWidth * 2f) - 1f
        val result = computeAlignmentSnapForDrag(
            translateX = translateXNearLeftEdge, translateY = 0.5f, parallaxFactor = 1f,
            halfWidthPx = 100f, halfHeightPx = 50f,
            boxWidthPx = boxWidth, boxHeightPx = boxHeight, snapThresholdPx = threshold
        )
        assertEquals(0f, result.guideLineXPx)
        // El borde izquierdo debe quedar EXACTO en x=0 tras el snap.
        val centerPxX = (result.translateX * 1f + 1f) / 2f * boxWidth
        assertEquals(0f, centerPxX - 100f, 1e-3f)
    }

    @Test
    fun `parallaxFactor 0 nunca engancha — division por cero evitada`() {
        val result = computeAlignmentSnapForDrag(
            translateX = 0f, translateY = 0f, parallaxFactor = 0f,
            halfWidthPx = 100f, halfHeightPx = 100f,
            boxWidthPx = boxWidth, boxHeightPx = boxHeight, snapThresholdPx = threshold
        )
        assertEquals(0f, result.translateX, 1e-4f)
        assertNull(result.guideLineXPx)
        assertNull(result.guideLineYPx)
    }

    @Test
    fun `boxWidthPx o boxHeightPx invalidos no enganchan (viewport sin medir todavia)`() {
        val result = computeAlignmentSnapForDrag(
            translateX = 0f, translateY = 0f, parallaxFactor = 1f,
            halfWidthPx = 100f, halfHeightPx = 100f,
            boxWidthPx = 0f, boxHeightPx = boxHeight, snapThresholdPx = threshold
        )
        assertNull(result.guideLineXPx)
        assertNull(result.guideLineYPx)
    }

    @Test
    fun `parallaxFactor menor a 1 (capa de fondo) tambien engancha al centro`() {
        val result = computeAlignmentSnapForDrag(
            translateX = 0f, translateY = 0f, parallaxFactor = 0.35f,
            halfWidthPx = 100f, halfHeightPx = 100f,
            boxWidthPx = boxWidth, boxHeightPx = boxHeight, snapThresholdPx = threshold
        )
        assertEquals(0f, result.translateX, 1e-4f)
        assertEquals(boxWidth / 2f, result.guideLineXPx)
    }

    // ---- computeAlignmentSnapForResize -----------------------------------

    @Test
    fun `resize engancha el borde estirado al centro del eje`() {
        // Centro en 400px, borde a 502px (a 2px del centro de un eje de 1000px = 500px).
        val (halfExtent, guide) = computeAlignmentSnapForResize(
            rawHalfExtentPx = 102f, centerOnAxisPx = 400f, axisSizePx = boxWidth, snapThresholdPx = threshold
        )
        assertEquals(100f, halfExtent, 1e-4f) // 500 - 400
        assertEquals(500f, guide)
    }

    @Test
    fun `resize conserva el signo del semi-extent al enganchar (sin flip)`() {
        // Centro en 400px, capa "volteada" (semi-extent negativo) con su
        // borde a 2px del borde izquierdo del lienzo (linea 0).
        val (halfExtent, guide) = computeAlignmentSnapForResize(
            rawHalfExtentPx = -398f, centerOnAxisPx = 400f, axisSizePx = boxWidth, snapThresholdPx = threshold
        )
        assertTrue("el signo original (volteado) debe conservarse", halfExtent < 0f)
        assertEquals(-400f, halfExtent, 1e-4f) // 0 - 400, con el signo negativo preservado
        assertEquals(0f, guide)
    }

    @Test
    fun `resize fuera de umbral no engancha`() {
        val (halfExtent, guide) = computeAlignmentSnapForResize(
            rawHalfExtentPx = 150f, centerOnAxisPx = 400f, axisSizePx = boxWidth, snapThresholdPx = threshold
        )
        assertEquals(150f, halfExtent, 1e-4f)
        assertNull(guide)
    }

    @Test
    fun `resize con axisSizePx invalido no engancha`() {
        val (halfExtent, guide) = computeAlignmentSnapForResize(
            rawHalfExtentPx = 102f, centerOnAxisPx = 400f, axisSizePx = 0f, snapThresholdPx = threshold
        )
        assertEquals(102f, halfExtent, 1e-4f)
        assertNull(guide)
    }

    // ---- snapRotationToFifteenDegrees -----------------------------------

    @Test
    fun `rotacion cerca de un multiplo de 15 engancha`() {
        val (angle, snap) = snapRotationToFifteenDegrees(46f)
        assertEquals(45f, angle, 1e-4f)
        assertEquals(45f, snap)
    }

    @Test
    fun `rotacion lejos de cualquier multiplo no engancha`() {
        val (angle, snap) = snapRotationToFifteenDegrees(37f)
        assertEquals(37f, angle, 1e-4f)
        assertNull(snap)
    }

    @Test
    fun `rotacion negativa engancha igual que la positiva`() {
        val (angle, snap) = snapRotationToFifteenDegrees(-46f)
        assertEquals(-45f, angle, 1e-4f)
        assertEquals(-45f, snap)
    }

    @Test
    fun `rotacion en 0 grados exactos engancha a 0, no a -0`() {
        val (angle, snap) = snapRotationToFifteenDegrees(0f)
        assertEquals(0f, angle, 1e-4f)
        assertEquals(0f, snap)
    }

    // ---- solveScaleMagnitudeForTargetHalfExtent --------------------------

    @Test
    fun `relacion lineal simple (sin rotacion) resuelve la magnitud exacta`() {
        // halfExtent = mag * 50 (capa de 100px de ancho base, sin rotar).
        val result = solveScaleMagnitudeForTargetHalfExtent(
            currentMag = 1f, currentHalfExtentPx = 50f,
            probeMag = 1.01f, probeHalfExtentPx = 50.5f,
            targetHalfExtentPx = 100f
        )
        assertEquals(2f, result, 1e-2f)
    }

    @Test
    fun `pendiente cero (extent no responde a la magnitud) devuelve la magnitud actual sin dividir por cero`() {
        val result = solveScaleMagnitudeForTargetHalfExtent(
            currentMag = 1f, currentHalfExtentPx = 50f,
            probeMag = 1.01f, probeHalfExtentPx = 50f,
            targetHalfExtentPx = 200f
        )
        assertEquals(1f, result, 1e-4f)
    }
}
