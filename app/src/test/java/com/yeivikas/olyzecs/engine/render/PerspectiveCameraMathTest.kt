package com.yeivikas.olyzecs.engine.render

import kotlin.math.abs
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [PerspectiveCameraMath] — motor puro extraído de
 * [LayerDrawer.drawLayer]. Además de las funciones sueltas, este archivo
 * fija por REGRESIÓN el fix de ADR-007 (verde chroma-key asomando en
 * fondos con `parallaxFactor < 1`, en reposo, sin animación de cámara
 * corriendo): [simulateEdgeNdc] reimplementa en Kotlin puro la misma
 * matemática que `android.opengl.Matrix.perspectiveM`/`setLookAtM` (no
 * disponibles en un test JVM plano sin Robolectric) para poder verificar,
 * de punta a punta, que el borde de una capa ya ajustada al 100% del
 * lienzo (ver `BackgroundAdjustDialog`) cae exactamente en el borde del
 * NDC (±1) en reposo, para cualquier `parallaxFactor` — no solo que la
 * fórmula de [PerspectiveCameraMath.depthCompensation] "se vea razonable"
 * de forma aislada.
 */
class PerspectiveCameraMathTest {

    private val baseEyeZ = 2.5f
    private val dollyRange = 1.6f
    private val maxDepth = 1.8f

    // ---- depthZ -----------------------------------------------------

    @Test
    fun `depthZ en el plano de foco (parallaxFactor 1) es siempre cero`() {
        assertEquals(0f, PerspectiveCameraMath.depthZ(1f, maxDepth), 1e-6f)
    }

    @Test
    fun `depthZ en el plano mas lejano (parallaxFactor 0) es -maxDepth`() {
        assertEquals(-maxDepth, PerspectiveCameraMath.depthZ(0f, maxDepth), 1e-6f)
    }

    @Test
    fun `depthZ del fondo por defecto (parallaxFactor 0-35, ver importAsBackground)`() {
        // (1 - 0.35) * -1.8 = -1.17 — mismo valor documentado en ADR-007.
        assertEquals(-1.17f, PerspectiveCameraMath.depthZ(0.35f, maxDepth), 1e-4f)
    }

    @Test
    fun `depthZ acota parallaxFactor fuera de rango en vez de extrapolar`() {
        assertEquals(PerspectiveCameraMath.depthZ(0f, maxDepth), PerspectiveCameraMath.depthZ(-5f, maxDepth), 1e-6f)
        assertEquals(PerspectiveCameraMath.depthZ(1f, maxDepth), PerspectiveCameraMath.depthZ(5f, maxDepth), 1e-6f)
    }

    // ---- eyeZ ---------------------------------------------------------

    @Test
    fun `eyeZ en reposo (dollyZoom 0) es exactamente baseEyeZ`() {
        assertEquals(baseEyeZ, PerspectiveCameraMath.eyeZ(baseEyeZ, 0f, dollyRange), 1e-6f)
    }

    @Test
    fun `eyeZ se aleja y acerca linealmente con dollyZoom`() {
        assertEquals(baseEyeZ + dollyRange, PerspectiveCameraMath.eyeZ(baseEyeZ, 1f, dollyRange), 1e-6f)
        assertEquals(baseEyeZ - dollyRange, PerspectiveCameraMath.eyeZ(baseEyeZ, -1f, dollyRange), 1e-6f)
    }

    // ---- fovyDeg --------------------------------------------------------

    @Test
    fun `fovyDeg cumple tan(fovy-2) = 1-eyeZ para varios eyeZ`() {
        for (eyeZ in listOf(0.9f, 1.5f, 2.5f, 4.1f, 10f)) {
            val fovy = PerspectiveCameraMath.fovyDeg(eyeZ)
            val halfFovyRad = Math.toRadians(fovy.toDouble() / 2.0)
            assertEquals(1.0 / eyeZ, tan(halfFovyRad), 1e-4)
        }
    }

    // ---- depthCompensation ---------------------------------------------

    @Test
    fun `depthCompensation en el plano de foco (depthZ 0) es exactamente 1 — cero regresion`() {
        assertEquals(1f, PerspectiveCameraMath.depthCompensation(0f, baseEyeZ), 1e-6f)
    }

    @Test
    fun `depthCompensation crece cuanto mas lejos esta la capa del plano de foco`() {
        val nearBg = PerspectiveCameraMath.depthCompensation(PerspectiveCameraMath.depthZ(0.8f, maxDepth), baseEyeZ)
        val farBg = PerspectiveCameraMath.depthCompensation(PerspectiveCameraMath.depthZ(0.2f, maxDepth), baseEyeZ)
        assertTrue("una capa mas atras necesita mas compensacion que una mas cerca del sujeto", farBg > nearBg)
        assertTrue("toda compensacion para una capa detras del plano de foco debe agrandar, nunca achicar", nearBg > 1f)
    }

    // ---- Simulación de punta a punta de la proyección de perspectiva ----
    // (regresión directa de ADR-007)

    /**
     * Reimplementación en Kotlin puro (sin `android.opengl.Matrix`) de
     * `Matrix.perspectiveM` + `Matrix.setLookAtM` + `Matrix.multiplyMM`
     * tal cual las usa [LayerDrawer.drawLayer], seguida de la
     * transformación de modelo (traslado a `depthZ`, escala por
     * `fitScale * depthCompensation`) sobre la esquina `(0.5, 0.5)` del
     * quad unitario — exactamente el mismo pipeline que arma
     * `drawLayer`, evaluado a mano para poder correrlo en un test JVM.
     * Devuelve la coordenada NDC (x, y) de esa esquina; `1.0` en
     * cualquiera de los dos ejes significa "toca el borde del viewport
     * exacto, cero verde asomando en ese eje".
     */
    private fun simulateEdgeNdc(parallaxFactor: Float, dollyZoom: Float, imageAspect: Float, viewportAspect: Float): Pair<Float, Float> {
        val eyeZ = PerspectiveCameraMath.eyeZ(baseEyeZ, dollyZoom, dollyRange)
        val fovyDeg = PerspectiveCameraMath.fovyDeg(eyeZ)
        val f = 1.0 / tan(Math.toRadians(fovyDeg.toDouble() / 2.0)) // Matrix.perspectiveM con aspect=1f

        val depthZ = PerspectiveCameraMath.depthZ(parallaxFactor, maxDepth)
        val depthCompensation = PerspectiveCameraMath.depthCompensation(depthZ, baseEyeZ)

        val fitScaleX: Double
        val fitScaleY: Double
        if (imageAspect > viewportAspect) {
            fitScaleX = 2.0
            fitScaleY = 2.0 * viewportAspect / imageAspect
        } else {
            fitScaleY = 2.0
            fitScaleX = 2.0 * imageAspect / viewportAspect
        }

        val cornerWorldX = 0.5 * fitScaleX * depthCompensation
        val cornerWorldY = 0.5 * fitScaleY * depthCompensation

        // setLookAtM(eye=(0,0,eyeZ), center=(0,0,0), up=(0,1,0)): en
        // espacio de vista, un punto en world-z=depthZ queda a distancia
        // (eyeZ - depthZ) de la cámara, con view-space z = depthZ - eyeZ.
        val viewZ = depthZ - eyeZ

        // perspectiveM con aspect=1f: clip.x = f*x/(-viewZ), clip.y = f*y/(-viewZ), clip.w = -viewZ.
        val ndcX = (f * cornerWorldX) / (-viewZ)
        val ndcY = (f * cornerWorldY) / (-viewZ)
        return ndcX.toFloat() to ndcY.toFloat()
    }

    @Test
    fun `en reposo, cualquier parallaxFactor llena el cuadro exacto — fix de ADR-007`() {
        for (parallaxFactor in listOf(0f, 0.2f, 0.35f, 0.6f, 1f)) {
            val (ndcX, ndcY) = simulateEdgeNdc(parallaxFactor, dollyZoom = 0f, imageAspect = 1f, viewportAspect = 1f)
            assertEquals("parallaxFactor=$parallaxFactor no llena el ancho — volveria a verse el verde", 1f, ndcX, 1e-4f)
            assertEquals("parallaxFactor=$parallaxFactor no llena el alto — volveria a verse el verde", 1f, ndcY, 1e-4f)
        }
    }

    @Test
    fun `en reposo, funciona igual con cualquier combinacion de aspect ratio de imagen y viewport`() {
        val aspects = listOf(9f / 16f, 1f, 16f / 9f, 1920f / 1342f)
        for (viewportAspect in aspects) {
            for (imageAspect in aspects) {
                val (ndcX, ndcY) = simulateEdgeNdc(0.35f, dollyZoom = 0f, imageAspect = imageAspect, viewportAspect = viewportAspect)
                // El eje "corto" (el que definió fitScale=2 en ese eje) es
                // el que debe tocar el borde exacto; el otro puede quedar
                // por debajo de 1 si image/viewport no coinciden en
                // aspecto — eso es esperado (letterbox), no el bug de
                // ADR-007. Alcanza con confirmar que NINGÚN eje se pasa
                // de 1 (desborde) ni el eje "cubierto" queda por debajo.
                assertTrue("ndcX no debe superar 1 (viewportAspect=$viewportAspect, imageAspect=$imageAspect)", ndcX <= 1f + 1e-4f)
                assertTrue("ndcY no debe superar 1 (viewportAspect=$viewportAspect, imageAspect=$imageAspect)", ndcY <= 1f + 1e-4f)
                val expectedFullX = imageAspect > viewportAspect
                if (expectedFullX) {
                    assertEquals(1f, ndcX, 1e-4f)
                } else {
                    assertEquals(1f, ndcY, 1e-4f)
                }
            }
        }
    }

    @Test
    fun `el sujeto (parallaxFactor 1) nunca cambia de tamaño sin importar el dollyZoom`() {
        val rest = simulateEdgeNdc(1f, dollyZoom = 0f, imageAspect = 1f, viewportAspect = 1f)
        for (dollyZoom in listOf(-1f, -0.4f, 0.4f, 1f)) {
            val moved = simulateEdgeNdc(1f, dollyZoom = dollyZoom, imageAspect = 1f, viewportAspect = 1f)
            assertEquals("dollyZoom=$dollyZoom no debe afectar al sujeto (regresion del plano de foco)", rest.first, moved.first, 1e-4f)
            assertEquals("dollyZoom=$dollyZoom no debe afectar al sujeto (regresion del plano de foco)", rest.second, moved.second, 1e-4f)
        }
    }

    @Test
    fun `el fondo SI cambia de tamano con dollyZoom — el warp de perspectiva sigue funcionando`() {
        val atRest = simulateEdgeNdc(0.35f, dollyZoom = 0f, imageAspect = 1f, viewportAspect = 1f).first
        val dollyIn = simulateEdgeNdc(0.35f, dollyZoom = 1f, imageAspect = 1f, viewportAspect = 1f).first
        val dollyOut = simulateEdgeNdc(0.35f, dollyZoom = -1f, imageAspect = 1f, viewportAspect = 1f).first
        assertEquals(1f, atRest, 1e-4f)
        assertTrue("dollyZoom positivo (camara mas lejos) debe agrandar el fondo relativo al sujeto", dollyIn > atRest)
        assertTrue("dollyZoom negativo (camara mas cerca) debe achicar el fondo relativo al sujeto", dollyOut < atRest)
    }

    // ---- depthCompensationFor (punto de entrada único, ver BASE_EYE_Z) --

    @Test
    fun `depthCompensationFor usa las mismas constantes reales que LayerDrawer`() {
        // Fija por regresión que BASE_EYE_Z/MAX_DEPTH no se desincronicen
        // de los valores reales de la cámara (ver KDoc de BASE_EYE_Z):
        // si alguien cambia uno de los dos sin tocar el otro, este test
        // es el primero en fallar.
        assertEquals(baseEyeZ, PerspectiveCameraMath.BASE_EYE_Z, 1e-6f)
        assertEquals(maxDepth, PerspectiveCameraMath.MAX_DEPTH, 1e-6f)
    }

    @Test
    fun `depthCompensationFor coincide exactamente con depthZ+depthCompensation manuales`() {
        for (parallaxFactor in listOf(0f, 0.2f, 0.35f, 0.6f, 1f)) {
            val manual = PerspectiveCameraMath.depthCompensation(
                PerspectiveCameraMath.depthZ(parallaxFactor, maxDepth),
                baseEyeZ
            )
            assertEquals(manual, PerspectiveCameraMath.depthCompensationFor(parallaxFactor), 1e-6f)
        }
    }

    @Test
    fun `depthCompensationFor en el sujeto (parallaxFactor 1) es exactamente 1`() {
        assertEquals(1f, PerspectiveCameraMath.depthCompensationFor(1f), 1e-6f)
    }

    @Test
    fun `depthCompensationFor del fondo por defecto (parallaxFactor 0-35) agranda, nunca achica`() {
        // Regresión directa del BUG REAL reportado por el usuario (marco
        // de selección/manijas desalineadas del contenido real de la
        // capa): el overlay 2D de EditorScreen.kt (layerBoundingQuadPx,
        // hitTestLayerAt, screenPointToLayerUv) multiplica su
        // halfWidth/halfHeight por este mismo valor — si acá diera 1
        // (como pasaba ANTES del fix, al no llamarlo en absoluto), el
        // marco quedaría más chico que la capa real dibujada por GL para
        // cualquier fondo (parallaxFactor=0.35 por defecto).
        val compensation = PerspectiveCameraMath.depthCompensationFor(0.35f)
        assertTrue("un fondo (parallaxFactor < 1) siempre necesita agrandarse, nunca al revés", compensation > 1f)
    }

    @Test
    fun `sin depthCompensation el bug de ADR-007 se reproduce — la capa queda mas chica que el viewport`() {
        // Réplica deliberada del código PRE-fix (fitScale sin multiplicar
        // por depthCompensation) para dejar documentado, de forma
        // ejecutable, cuál era exactamente el bug que ADR-007 corrigió —
        // si algún refactor futuro vuelve a "olvidar" depthCompensation,
        // el test de arriba (`en reposo, cualquier parallaxFactor llena
        // el cuadro exacto`) es el que debe fallar primero.
        val parallaxFactor = 0.35f
        val depthZ = PerspectiveCameraMath.depthZ(parallaxFactor, maxDepth)
        val eyeZ = baseEyeZ
        val fovyDeg = PerspectiveCameraMath.fovyDeg(eyeZ)
        val f = 1.0 / tan(Math.toRadians(fovyDeg.toDouble() / 2.0))
        val viewZ = depthZ - eyeZ
        val ndcEdgeWithoutFix = (f * 1.0) / (-viewZ) // fitScale=2 (mitad del quad=1.0) SIN depthCompensation
        assertTrue(
            "este test documenta el bug pre-ADR-007: sin compensar, el borde queda por dentro del viewport (< 1)",
            ndcEdgeWithoutFix < 1.0 - 1e-3
        )
        assertEquals(0.6813 /* eyeZ/(eyeZ - depthZ) */, ndcEdgeWithoutFix, 1e-3)
    }

    // ---- projectQuadCornersNdc (la función REAL que usa el overlay 2D,
    // ver `layerBoundingQuadPx`/`hitTestLayerAt` en EditorScreen.kt) ------
    //
    // GAP DE COBERTURA encontrado (14/sep/2026, junto con el bug de abajo):
    // todos los tests de arriba usan [simulateEdgeNdc], una reimplementación
    // A MANO del pipeline que vive SOLO en este archivo de test — nunca
    // llaman a [PerspectiveCameraMath.projectQuadCornersNdc], la función
    // REAL que ejecuta la app. Los dos hardcodean el mismo `f =
    // 1/tan(fovy/2)` SIN dividir por ningún aspecto (replicando
    // `Matrix.perspectiveM(..., aspect=1f, ...)`, que es lo que
    // [LayerDrawer.drawLayer] llama de verdad) — así que, por más casos que
    // pasen, [simulateEdgeNdc] nunca podía detectar un bug que viviera dentro
    // de [projectQuadCornersNdc] y no en su propia copia. Los tests de acá
    // abajo llaman a la función real y punto.
    @Test
    fun `BUG REAL encontrado y corregido — projectQuadCornersNdc dividia una segunda vez por el aspecto del viewport`() {
        // Reproduce EXACTO el caso de las capturas "Cr7": un lienzo
        // vertical no cuadrado (9:14) con una capa en reposo, tilt=0,
        // rotation=0, parallaxFactor=1 (el "sujeto", sin compensación de
        // profundidad de por medio, para aislar el bug de la proyección).
        // ANTES del fix, `proj[0]` era `f / (viewportWidthPx/viewportHeightPx)`
        // — con viewport 9:14 (aspecto ≈0.643), eso multiplicaba el NDC.x
        // real por ~1.556 (1/0.643), corriendo el borde del marco bien
        // afuera de donde GL dibuja de verdad.
        val corners = PerspectiveCameraMath.projectQuadCornersNdc(
            translateX = 0f, translateY = 0f,
            scaleVal = 1f, scaleXVal = 1f, scaleYVal = 1f,
            rotationDeg = 0f, tiltXDeg = 0f, tiltYDeg = 0f,
            parallaxFactor = 1f,
            dollyZoom = 0f, dollyRange = dollyRange, baseEyeZ = baseEyeZ,
            imageWidthPx = 900, imageHeightPx = 1400, // mismo aspecto 9:14 que el viewport -> "cover" exacto
            viewportWidthPx = 900f, viewportHeightPx = 1400f
        )!!
        // Orden real: arriba-izq, abajo-izq, arriba-der, abajo-der.
        val maxAbsX = corners.maxOf { kotlin.math.abs(it[0]) }
        val maxAbsY = corners.maxOf { kotlin.math.abs(it[1]) }
        assertEquals(
            "con imageAspect==viewportAspect el borde X debe tocar exactamente ±1, no ±1.556 (bug pre-fix)",
            1f, maxAbsX, 1e-4f
        )
        assertEquals(
            "con imageAspect==viewportAspect el borde Y debe tocar exactamente ±1",
            1f, maxAbsY, 1e-4f
        )
    }

    /** Un caso de prueba imagen/viewport/profundidad para el cross-check de abajo. */
    private data class AspectCase(
        val imageWidthPx: Int,
        val imageHeightPx: Int,
        val viewportWidthPx: Float,
        val viewportHeightPx: Float,
        val parallaxFactor: Float
    )

    @Test
    fun `projectQuadCornersNdc coincide con la simulacion de referencia para viewports no cuadrados`() {
        // Cross-check contra [simulateEdgeNdc] (que SÍ replica fielmente
        // `Matrix.perspectiveM(..., aspect=1f, ...)`) para varias
        // combinaciones reales de aspecto de imagen/viewport, incluyendo
        // los no cuadrados que el bug afectaba y el test anterior
        // (cuadrado, imageAspect==viewportAspect) no alcanzaba a cubrir.
        val cases = listOf(
            AspectCase(imageWidthPx = 900, imageHeightPx = 1400, viewportWidthPx = 900f, viewportHeightPx = 1400f, parallaxFactor = 1f),    // 9:14, imagen == viewport (sujeto)
            AspectCase(imageWidthPx = 900, imageHeightPx = 1400, viewportWidthPx = 900f, viewportHeightPx = 1400f, parallaxFactor = 0.35f),  // 9:14, fondo por defecto
            AspectCase(imageWidthPx = 1920, imageHeightPx = 1080, viewportWidthPx = 1920f, viewportHeightPx = 1080f, parallaxFactor = 1f),   // 16:9 horizontal
            AspectCase(imageWidthPx = 1080, imageHeightPx = 1920, viewportWidthPx = 1920f, viewportHeightPx = 1080f, parallaxFactor = 1f),   // imagen vertical en viewport horizontal (letterbox)
            AspectCase(imageWidthPx = 1000, imageHeightPx = 1000, viewportWidthPx = 900f, viewportHeightPx = 1400f, parallaxFactor = 1f)     // imagen cuadrada en viewport vertical (letterbox)
        )
        for (case in cases) {
            val imageAspect = case.imageWidthPx.toFloat() / case.imageHeightPx.toFloat()
            val viewportAspect = case.viewportWidthPx / case.viewportHeightPx
            val (expectedNdcX, expectedNdcY) = simulateEdgeNdc(
                case.parallaxFactor, dollyZoom = 0f, imageAspect = imageAspect, viewportAspect = viewportAspect
            )

            val corners = PerspectiveCameraMath.projectQuadCornersNdc(
                translateX = 0f, translateY = 0f,
                scaleVal = 1f, scaleXVal = 1f, scaleYVal = 1f,
                rotationDeg = 0f, tiltXDeg = 0f, tiltYDeg = 0f,
                parallaxFactor = case.parallaxFactor,
                dollyZoom = 0f, dollyRange = dollyRange, baseEyeZ = baseEyeZ,
                imageWidthPx = case.imageWidthPx, imageHeightPx = case.imageHeightPx,
                viewportWidthPx = case.viewportWidthPx, viewportHeightPx = case.viewportHeightPx
            )!!
            val actualNdcX = corners.maxOf { kotlin.math.abs(it[0]) }
            val actualNdcY = corners.maxOf { kotlin.math.abs(it[1]) }
            assertEquals(
                "$case: NDC.x no coincide con la simulacion de referencia",
                expectedNdcX, actualNdcX, 1e-3f
            )
            assertEquals(
                "$case: NDC.y no coincide con la simulacion de referencia",
                expectedNdcY, actualNdcY, 1e-3f
            )
        }
    }

    @Test
    fun `projectQuadCornersNdc con tilt en reposo sobre viewport no cuadrado proyecta el trapecio exacto`() {
        // CORRECCIÓN (14/sep/2026) — el fallo de CI #494-#498 no era del
        // fix de aspecto (BUG REAL de arriba), sino de ESTE test: el
        // límite `maxAbsX <= 1.05f` era un número inventado a ojo, no
        // derivado del pipeline real. Con tilt distinto de cero, la
        // esquina que rota hacia la cámara SÍ debe proyectar más allá de
        // ±1 — eso no es un bug, es el trapecio real que produce la
        // perspectiva (la esquina que se acerca al ojo se agranda; la que
        // se aleja se achica). Ya a tiltY=10° el valor exacto es ~1.058,
        // por encima del 1.05 que el test exigía — así que el test
        // fallaba por sí solo, no por una regresión de projectQuadCornersNdc.
        //
        // Fórmula cerrada, derivada a mano del mismo pipeline model→view→
        // proyección (parallaxFactor=1 → depthZ=0, sin translate, sin
        // rotationDeg/tiltXDeg — solo rotación pura en Y de un quad plano
        // centrado en el origen; imageAspect==viewportAspect → fitScaleX
        // = 2, half-width tras el scale = 1; y f = eyeZ, propiedad de
        // [PerspectiveCameraMath.fovyDeg] por construcción):
        //
        //   ndcX(esquina) = eyeZ·cos(θ)·sign / (eyeZ + sin(θ)·sign)
        //
        // con θ = tiltYDeg y sign = ±1 según la esquina. El máximo en
        // valor absoluto ocurre en la esquina cuyo signo hace mínimo el
        // denominador (la que el tilt acerca a la cámara):
        //
        //   maxAbsX(θ) = eyeZ·cos(θ) / (eyeZ − |sin(θ)|)
        //
        // Verificada numéricamente contra la función real para los 5
        // casos de abajo: coincide a precisión de máquina (ver commit de
        // este fix). Cero número inventado — si algún día esta fórmula y
        // la función real difieren, es porque projectQuadCornersNdc
        // cambió, no porque el tolerance esté mal calibrado.
        for (tiltY in listOf(-25f, -10f, 0f, 10f, 25f)) {
            val corners = PerspectiveCameraMath.projectQuadCornersNdc(
                translateX = 0f, translateY = 0f,
                scaleVal = 1f, scaleXVal = 1f, scaleYVal = 1f,
                rotationDeg = 0f, tiltXDeg = 0f, tiltYDeg = tiltY,
                parallaxFactor = 1f,
                dollyZoom = 0f, dollyRange = dollyRange, baseEyeZ = baseEyeZ,
                imageWidthPx = 900, imageHeightPx = 1400,
                viewportWidthPx = 900f, viewportHeightPx = 1400f
            )!!
            val maxAbsX = corners.maxOf { kotlin.math.abs(it[0]) }

            val thetaRad = Math.toRadians(tiltY.toDouble())
            val expectedMaxAbsX = (baseEyeZ * kotlin.math.cos(thetaRad)) /
                (baseEyeZ - kotlin.math.abs(kotlin.math.sin(thetaRad)))

            assertEquals(
                "tiltY=$tiltY: el borde X no coincide con la proyección exacta del trapecio (ver fórmula cerrada arriba)",
                expectedMaxAbsX.toFloat(), maxAbsX, 1e-3f
            )
        }
    }
}
