package com.yeivikas.olyzecs.engine.render

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [PerspectiveCameraMath.projectQuadCornersNdc] y [QuadHomography]
 * — el fix del bug real reportado el 13/sep/2026 con las capturas del
 * proyecto "Cr7" ("la imagen sale fuera del marco"): el overlay 2D de
 * selección (`layerBoundingQuadPx`/`hitTestLayerAt`/`screenPointToLayerUv`
 * en `EditorScreen.kt`) modelaba la capa como un rectángulo rotado solo en
 * Z, sin `tiltXDeg`/`tiltYDeg` — que [LayerDrawer.drawLayer] SÍ aplica
 * antes de proyectar con la cámara de perspectiva real.
 *
 * Estos tests fijan por REGRESIÓN dos cosas:
 * 1. Con tilt = 0, [projectQuadCornersNdc] da EXACTAMENTE lo mismo que la
 *    fórmula rectangular anterior (cero regresión para el único caso que
 *    ya funcionaba).
 * 2. Con tilt != 0, las 4 esquinas proyectadas dejan de formar un
 *    rectángulo — es matemáticamente imposible que un rectángulo rotado
 *    en 2D represente ese cuadrilátero, así que cualquier fix futuro que
 *    vuelva a aproximar con un rectángulo reintroduciría el bug.
 */
class QuadProjectionTest {

    private val baseEyeZ = PerspectiveCameraMath.BASE_EYE_Z
    private val dollyRange = PerspectiveCameraMath.DOLLY_RANGE

    private fun project(
        parallaxFactor: Float,
        tiltXDeg: Float = 0f,
        tiltYDeg: Float = 0f,
        rotationDeg: Float = 0f,
        dollyZoom: Float = 0f
    ) = PerspectiveCameraMath.projectQuadCornersNdc(
        translateX = 0f,
        translateY = 0f,
        scaleVal = 1f,
        scaleXVal = 1f,
        scaleYVal = 1f,
        rotationDeg = rotationDeg,
        tiltXDeg = tiltXDeg,
        tiltYDeg = tiltYDeg,
        parallaxFactor = parallaxFactor,
        dollyZoom = dollyZoom,
        dollyRange = dollyRange,
        baseEyeZ = baseEyeZ,
        imageWidthPx = 1000,
        imageHeightPx = 1000,
        viewportWidthPx = 1000f,
        viewportHeightPx = 1000f
    )

    // ---- Cero regresión: tilt = 0 se comporta como el rectángulo de antes ----

    @Test
    fun `sujeto en reposo sin tilt llena el NDC exacto (igual que antes)`() {
        val corners = project(parallaxFactor = 1f)!!
        // Orden: arriba-izq, abajo-izq, arriba-der, abajo-der.
        assertEquals(-1f, corners[0][0], 1e-4f); assertEquals(1f, corners[0][1], 1e-4f)
        assertEquals(-1f, corners[1][0], 1e-4f); assertEquals(-1f, corners[1][1], 1e-4f)
        assertEquals(1f, corners[2][0], 1e-4f); assertEquals(1f, corners[2][1], 1e-4f)
        assertEquals(1f, corners[3][0], 1e-4f); assertEquals(-1f, corners[3][1], 1e-4f)
    }

    @Test
    fun `fondo (parallax 0,35) sin tilt tambien llena el NDC exacto (ADR-007, sin regresion)`() {
        val corners = project(parallaxFactor = 0.35f)!!
        for (c in corners) {
            assertTrue(abs(abs(c[0]) - 1f) < 1e-3f)
            assertTrue(abs(abs(c[1]) - 1f) < 1e-3f)
        }
    }

    // ---- El bug real: con tilt, el cuadrilátero deja de ser un rectángulo ----

    @Test
    fun `con tiltY distinto de 0, las esquinas ya NO forman un rectangulo`() {
        val corners = project(parallaxFactor = 0.35f, tiltYDeg = 30f)!!
        val (topLeft, bottomLeft, topRight, bottomRight) = corners
        // Tilt en Y (eje vertical) rota la capa alrededor del eje Y: los
        // lados izquierdo y derecho quedan cada uno perfectamente
        // verticales (mismo x arriba y abajo de cada lado), pero a
        // profundidades distintas de la cámara — por eso lo que cambia es
        // el ALTO de cada lado, no el ancho arriba/abajo (eso es lo que
        // distingue al tilt en X, ver el test de abajo).
        val leftHeight = abs(topLeft[1] - bottomLeft[1])
        val rightHeight = abs(topRight[1] - bottomRight[1])
        assertTrue(
            "esperaba un trapecio (altos de los lados verticales distintos), " +
                "pero dio un rectángulo: leftHeight=$leftHeight rightHeight=$rightHeight",
            abs(leftHeight - rightHeight) > 1e-3f
        )
    }

    @Test
    fun `con tiltX distinto de 0, las esquinas ya NO forman un rectangulo`() {
        val corners = project(parallaxFactor = 0.35f, tiltXDeg = 25f)!!
        val (topLeft, bottomLeft, topRight, bottomRight) = corners
        // Tilt en X (eje horizontal) rota la capa alrededor del eje X: los
        // lados de arriba y de abajo quedan cada uno perfectamente
        // horizontales (misma altura y profundidad en sus dos esquinas),
        // pero arriba y abajo terminan a profundidades distintas de la
        // cámara — por eso lo que cambia es el ANCHO arriba vs abajo, no
        // el alto de los lados (eso es lo que distingue al tilt en Y, ver
        // el test de más arriba). Verificado numéricamente antes de
        // escribir esta aserción — no es una suposición.
        val topWidth = abs(topRight[0] - topLeft[0])
        val bottomWidth = abs(bottomRight[0] - bottomLeft[0])
        assertTrue(
            "esperaba un trapecio (anchos arriba/abajo distintos), " +
                "pero dio un rectángulo: topWidth=$topWidth bottomWidth=$bottomWidth",
            abs(topWidth - bottomWidth) > 1e-3f
        )
    }

    @Test
    fun `sujeto (parallax 1, plano de foco) con tilt tambien se deforma en pantalla`() {
        // El plano de foco solo está garantizado a "no cambiar de tamaño"
        // frente al dolly zoom (ver depthCompensation) — el tilt lo
        // deforma igual que a cualquier otra capa, porque lo rota en 3D
        // ANTES de la proyección de perspectiva. Este test documenta ese
        // comportamiento esperado (no es un bug: es la cámara 3D real).
        val corners = project(parallaxFactor = 1f, tiltYDeg = 30f)!!
        val (topLeft, bottomLeft, topRight, bottomRight) = corners
        // Mismo motivo que en el test de tiltY de más arriba: el eje Y
        // deforma el ALTO de cada lado vertical, no el ancho arriba/abajo.
        val leftHeight = abs(topLeft[1] - bottomLeft[1])
        val rightHeight = abs(topRight[1] - bottomRight[1])
        assertTrue(abs(leftHeight - rightHeight) > 1e-3f)
    }

    // ---- QuadHomography: el mapeo que reemplaza al rectángulo afín ----

    @Test
    fun `homografia de un rectangulo reproduce las 4 esquinas exactas`() {
        val tl = floatArrayOf(-1f, 1f); val tr = floatArrayOf(1f, 1f)
        val br = floatArrayOf(1f, -1f); val bl = floatArrayOf(-1f, -1f)
        val coeffs = QuadHomography.fromUnitSquare(tl, tr, br, bl)
        assertArrayEqualsApprox(tl, QuadHomography.mapUvToPoint(coeffs, 0f, 0f))
        assertArrayEqualsApprox(tr, QuadHomography.mapUvToPoint(coeffs, 1f, 0f))
        assertArrayEqualsApprox(br, QuadHomography.mapUvToPoint(coeffs, 1f, 1f))
        assertArrayEqualsApprox(bl, QuadHomography.mapUvToPoint(coeffs, 0f, 1f))
    }

    @Test
    fun `homografia de un trapecio es invertible ida y vuelta (forward luego inverse)`() {
        val corners = project(parallaxFactor = 0.35f, tiltYDeg = 30f)!!
        val (topLeft, bottomLeft, topRight, bottomRight) = corners
        val coeffs = QuadHomography.fromUnitSquare(topLeft, topRight, bottomRight, bottomLeft)
        // Cualquier (u,v) de prueba: proyectar y volver a resolver debe
        // devolver el mismo (u,v) — es la garantía de que el hit-test/UV
        // mapping (la inversa) es matemáticamente consistente con el
        // dibujo del marco (la directa), a diferencia de la aproximación
        // rectangular anterior, que ni siquiera podía representar un
        // trapecio en primer lugar.
        val testUvs = listOf(0f to 0f, 1f to 0f, 0.5f to 0.5f, 0.2f to 0.8f, 1f to 1f)
        for ((u, v) in testUvs) {
            val point = QuadHomography.mapUvToPoint(coeffs, u, v)
            val backToUv = QuadHomography.mapPointToUv(coeffs, point[0], point[1])
            assertNotNull(backToUv)
            assertEquals(u, backToUv!![0], 1e-3f)
            assertEquals(v, backToUv[1], 1e-3f)
        }
    }

    @Test
    fun `homografia de un rectangulo se reduce al caso afin (esquinas fuera del cuadrado dan uv fuera del rango)`() {
        val tl = floatArrayOf(-1f, 1f); val tr = floatArrayOf(1f, 1f)
        val br = floatArrayOf(1f, -1f); val bl = floatArrayOf(-1f, -1f)
        val coeffs = QuadHomography.fromUnitSquare(tl, tr, br, bl)
        val outside = QuadHomography.mapPointToUv(coeffs, 2f, 2f)!! // fuera del rectángulo
        assertTrue(outside[0] !in 0f..1f || outside[1] !in 0f..1f)
        val inside = QuadHomography.mapPointToUv(coeffs, 0f, 0f)!! // centro
        assertEquals(0.5f, inside[0], 1e-4f)
        assertEquals(0.5f, inside[1], 1e-4f)
    }

    private fun assertArrayEqualsApprox(expected: FloatArray, actual: FloatArray, tolerance: Float = 1e-3f) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertTrue(
                "esperaba ${expected.toList()}, dio ${actual.toList()}",
                abs(expected[i] - actual[i]) < tolerance
            )
        }
    }
}
