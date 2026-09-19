package com.yeivikas.olyzecs.engine.render

import kotlin.math.atan

/**
 * Matemática pura de la cámara de perspectiva que usa [LayerDrawer] —
 * extraída de `drawLayer()` para poder testearla sin depender de
 * `android.opengl.Matrix`/`GLES20` (no disponibles en tests JVM sin
 * Robolectric). `LayerDrawer` sigue siendo el único lugar que arma las
 * matrices reales de OpenGL; delega en estas funciones cada cálculo
 * escalar que puede probarse de forma aislada: la profundidad Z de una
 * capa según su `parallaxFactor`, el FOV vertical del dolly zoom, y la
 * compensación de escala que cancela el encogimiento por perspectiva de
 * cualquier capa fuera del plano de foco (ADR-007 — fix del verde
 * chroma-key asomando en fondos con `parallaxFactor < 1`, incluso en
 * reposo).
 *
 * Función pura en las cuatro funciones: sin estado propio, sin I/O, sin
 * dependencia de Android — seguras de llamar desde cualquier hilo o
 * desde un test JVM plano, mismo criterio que
 * [com.yeivikas.olyzecs.engine.camera.CameraFrameInterpolation] y
 * [com.yeivikas.olyzecs.ui.AlignmentGuides] (ver esos archivos para el
 * mismo patrón de extracción en otras partes del proyecto).
 */
internal object PerspectiveCameraMath {

    /**
     * Constantes REALES de la cámara virtual que arma [LayerDrawer] al
     * dibujar cada capa — [BASE_EYE_Z] es la distancia base de la cámara
     * al plano de foco (z=0) y [MAX_DEPTH] es la distancia (en unidades
     * de mundo) del plano más lejano posible (`parallaxFactor=0`).
     *
     * BUG REAL corregido — reportado con captura por el usuario: el
     * marco de selección con sus manijas (✓, ×, flechas de
     * redimensionar/rotar) se dibujaba desalineado del contenido real de
     * la capa en cuanto ésta tenía `parallaxFactor` distinto de 1.0 (el
     * caso de CUALQUIER fondo importado — ver `importAsBackground`, que
     * usa 0.35 por defecto). Causa raíz: [LayerDrawer.drawLayer] escala
     * el quad real por `fitScale * depthCompensation * frame.scale *
     * frame.scaleX/Y` (ver ADR-007), pero las tres funciones puras que
     * calculan la geometría del overlay 2D en `EditorScreen.kt`
     * (`layerBoundingQuadPx`, `hitTestLayerAt`, `screenPointToLayerUv`)
     * solo aplicaban `fitScale * scale * scaleX/Y` — el factor de
     * `depthCompensation` faltaba por completo. El resultado: para
     * cualquier capa que NO estuviera exactamente en el plano de foco
     * (`parallaxFactor = 1.0`, poco común — la mayoría de las capas
     * tienen algún parallax), el marco/manijas quedaban más chicos y
     * corridos respecto al borde real de la imagen dibujada por GL,
     * exactamente el síntoma de "iconos sueltos"/"el marco se mueve"
     * reportado — y SIEMPRE reproducible, porque la condición
     * (`parallaxFactor != 1.0`) es la norma, no la excepción.
     *
     * Antes de este fix, [LayerDrawer] mantenía su propia copia interna
     * de estos dos números (`private val baseEyeZ = 2.5f` y el literal
     * `maxDepth = 1.8f` pasado directo a [depthZ]) — es decir, ya existía
     * el riesgo de que un valor cambiara en un lado y no en el otro. Al
     * exponerlos acá como la ÚNICA fuente de verdad y hacer que TANTO
     * [LayerDrawer] (el render GL real) COMO el overlay 2D de
     * `EditorScreen.kt` (vía [depthCompensationFor]) lean estos mismos
     * valores, queda estructuralmente imposible que se desincronicen de
     * nuevo.
     */
    const val BASE_EYE_Z = 2.5f
    const val MAX_DEPTH = 1.8f

    /**
     * Cuánto se mueve físicamente la cámara con `dollyZoom = ±1`. Misma
     * historia que [BASE_EYE_Z]/[MAX_DEPTH]: vivía como copia privada en
     * [LayerDrawer] (`dollyRange = 1.6f`); ahora es la única fuente de
     * verdad para que [projectQuadCornersNdc] (el overlay 2D) no pueda
     * desincronizarse del valor real usado por `drawLayer()`.
     */
    const val DOLLY_RANGE = 1.6f

    /**
     * Punto de entrada único que consolida [depthZ] + [depthCompensation]
     * usando las constantes reales de cámara ([BASE_EYE_Z], [MAX_DEPTH]):
     * "cuánto hay que agrandar/achicar esta capa, solo por estar a
     * [parallaxFactor] de profundidad, para que coincida pixel a pixel
     * con lo que dibuja [LayerDrawer]". Cualquier código que necesite
     * replicar en 2D el tamaño real de una capa ya escalada por
     * profundidad (el overlay de selección, el hit-test de toque, el
     * mapeo de un punto de pantalla a UV de textura) DEBE multiplicar su
     * `halfWidth`/`halfHeight` por este factor — igual que [LayerDrawer]
     * multiplica su `fitScale` por él antes de armar la matriz de
     * modelo. Ver [BASE_EYE_Z] para el detalle completo del bug que este
     * punto de entrada único previene.
     */
    fun depthCompensationFor(parallaxFactor: Float): Float =
        depthCompensation(depthZ(parallaxFactor, MAX_DEPTH), BASE_EYE_Z)

    /**
     * Distancia (world Z) del plano donde vive una capa con
     * [parallaxFactor], respecto al plano de foco de la cámara (z=0,
     * `parallaxFactor=1.0`, el "sujeto"). Fondo (parallax bajo) queda
     * más atrás en Z (`depthZ` más negativo); el sujeto siempre da 0.
     * [parallaxFactor] se acota a [0, 1] antes de usarse — valores fuera
     * de ese rango no tienen representación físca válida en esta cámara.
     * [maxDepth] es la distancia (en unidades de mundo) del plano más
     * lejano posible (`parallaxFactor=0`); en [LayerDrawer] es 1.8f.
     */
    fun depthZ(parallaxFactor: Float, maxDepth: Float): Float =
        (1f - parallaxFactor.coerceIn(0f, 1f)) * -maxDepth

    /**
     * Posición Z animada de la cámara virtual: se aleja/acerca del plano
     * de foco con `dollyZoom` (rango [-1, 1]), a razón de [dollyRange]
     * unidades de mundo por unidad de `dollyZoom`.
     */
    fun eyeZ(baseEyeZ: Float, dollyZoom: Float, dollyRange: Float): Float =
        baseEyeZ + dollyZoom * dollyRange

    /**
     * FOV vertical (grados) tal que `tan(fovy/2) = 1/eyeZ` — calibrado
     * para que el plano z=0 (el "sujeto") nunca cambie de tamaño en
     * pantalla sin importar cuánto se anime `dollyZoom`: el warp real del
     * dolly zoom ocurre exclusivamente en las capas fuera de ese plano
     * (ver el comentario de cabecera de [LayerDrawer]).
     */
    fun fovyDeg(eyeZ: Float): Float =
        Math.toDegrees(2.0 * atan(1.0 / eyeZ.toDouble())).toFloat()

    /**
     * ADR-007 — factor de escala que cancela el encogimiento por
     * perspectiva de una capa ubicada en [depthZ] (más lejos de la
     * cámara que el plano de foco, `depthZ < 0`), de forma que — en
     * reposo, con la cámara en su posición base — cualquier capa cuyo
     * bitmap ya cubra el 100% del lienzo (ver `BackgroundAdjustDialog`,
     * ADR-006/ADR-010) siga cubriéndolo al 100% en pantalla, sin importar
     * su `parallaxFactor`.
     *
     * Medido contra [baseEyeZ] — la distancia BASE de la cámara al plano
     * de foco — y NO contra el `eyeZ` ya animado por `dollyZoom`: si se
     * compensara contra el `eyeZ` real, el dolly zoom dejaría de tener
     * efecto visual sobre las capas de fondo, anulando el propósito de
     * tener una cámara de perspectiva real. Compensando siempre contra la
     * distancia base:
     * - En reposo (`depthZ` cualquiera, cámara en `baseEyeZ`): cancela
     *   exactamente el encogimiento — todas las capas llenan el cuadro.
     * - Con `dollyZoom` animado (`eyeZ` real distinto de `baseEyeZ`): la
     *   compensación queda fija en su valor de reposo, así que la capa SÍ
     *   cambia de tamaño relativo al sujeto — el efecto vértigo/parallax
     *   real que la cámara 3D fue diseñada para producir.
     * - En `depthZ = 0` (`parallaxFactor = 1.0`, el sujeto): devuelve
     *   siempre 1 (sin cambio), preservando la garantía de "cero
     *   regresión en el plano de foco".
     */
    fun depthCompensation(depthZ: Float, baseEyeZ: Float): Float =
        (baseEyeZ - depthZ) / baseEyeZ

    /**
     * BUG REAL encontrado (13/sep/2026, capturas del proyecto "Cr7" —
     * usuario reporta "la imagen sale fuera del marco"): el overlay 2D
     * de selección en `EditorScreen.kt` (`layerBoundingQuadPx`,
     * `hitTestLayerAt`, `screenPointToLayerUv`) sigue modelando el marco
     * de la capa como un **rectángulo** al que solo se le aplica
     * rotación en Z (`rotationDeg`) — nunca `frame.tiltXDeg`/`tiltYDeg`.
     * [LayerDrawer.drawLayer] sí aplica esas dos rotaciones (alrededor
     * de los ejes X e Y) ANTES de proyectar con la cámara de
     * perspectiva real (`Matrix.perspectiveM`/`setLookAtM`).
     *
     * Confirmado numéricamente (no es una hipótesis): replicando el
     * pipeline completo de `drawLayer` con matrices 4×4 puras, un quad
     * con `tiltYDeg = 30` proyecta sus 4 esquinas a un
     * **trapecio** (los dos lados verticales quedan con largos
     * distintos y no paralelos en profundidad — cada esquina tiene su
     * propia coordenada Z tras la rotación, así que la división de
     * perspectiva las escala de forma distinta). Ningún rectángulo
     * rotado en 2D — por definición, con sus 4 lados paralelos dos a
     * dos — puede coincidir con un trapecio. Por eso el marco de
     * selección se ve "descalzado" del contenido real apenas hay tilt
     * distinto de 0, sin importar cuántas veces se ajuste el factor de
     * profundidad (ese factor ya está bien aplicado — ver
     * [depthCompensationFor] — el problema es otro: falta la rotación en
     * X/Y antes de proyectar).
     *
     * Esta función es la corrección: proyecta las 4 esquinas reales del
     * quad (mismo orden que [LayerDrawer.quadVertices]: arriba-izq,
     * abajo-izq, arriba-der, abajo-der) a través del MISMO pipeline
     * model→view→proyección que usa `drawLayer` — incluyendo
     * `tiltXDeg`/`tiltYDeg` — en vez de aproximarlas como un rectángulo
     * rotado. Sin dependencia de `android.opengl.Matrix`/GLES (testeable
     * en JVM plano, mismo criterio que el resto de este archivo). El
     * resultado son coordenadas NDC (`[-1, 1]`, Y hacia arriba) — quien
     * llama debe convertirlas a píxeles de pantalla exactamente igual
     * que antes (`x_px = (ndcX+1)/2*boxWidthPx`,
     * `y_px = (1-ndcY)/2*boxHeightPx`).
     *
     * Con `tiltXDeg = tiltYDeg = 0`, esta función da EXACTAMENTE el
     * mismo resultado que la fórmula anterior (rectángulo rotado en Z) —
     * cero regresión para el caso, hasta ahora el único soportado, de
     * tilt en reposo.
     */
    fun projectQuadCornersNdc(
        translateX: Float,
        translateY: Float,
        scaleVal: Float,
        scaleXVal: Float,
        scaleYVal: Float,
        rotationDeg: Float,
        tiltXDeg: Float,
        tiltYDeg: Float,
        parallaxFactor: Float,
        dollyZoom: Float,
        dollyRange: Float,
        baseEyeZ: Float,
        imageWidthPx: Int,
        imageHeightPx: Int,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): List<FloatArray>? {
        if (imageWidthPx <= 0 || imageHeightPx <= 0 || viewportWidthPx <= 0f || viewportHeightPx <= 0f) return null

        val eyeZ = eyeZ(baseEyeZ, dollyZoom, dollyRange)
        val fovyRad = Math.toRadians(fovyDeg(eyeZ).toDouble())
        val near = 0.1f
        val far = eyeZ + 20f
        val f = (1.0 / Math.tan(fovyRad / 2.0)).toFloat()

        // BUG REAL corregido (14/sep/2026 — capturas "Cr7", "la punta del
        // arma sale del marco"): acá se dividía `f` por
        // `viewportWidthPx / viewportHeightPx` (el aspecto REAL del
        // lienzo). [LayerDrawer.drawLayer] NUNCA hace esa división — llama
        // a `Matrix.perspectiveM(projectionMatrix, 0, fovyDeg, 1f, 0.1f,
        // eyeZ + 20f)` con el parámetro `aspect` fijo en `1f`, porque la
        // corrección de aspecto real la hace por completo `fitScaleX`/
        // `fitScaleY` en la matriz de MODELO (ver más abajo, `imageAspect`/
        // `viewportAspect`) — no la matriz de proyección. Aplicar acá una
        // segunda división por el aspecto real del viewport duplicaba esa
        // corrección: para cualquier lienzo no cuadrado (9:14, 16:9, etc.
        // — la norma, no la excepción) las esquinas proyectadas del marco
        // 2D quedaban escaladas en X un factor `viewportAspect` distinto
        // de 1 respecto a lo que GL dibuja de verdad. Con `aspect = 1f`
        // (igual que `Matrix.perspectiveM`), `proj[0]` queda simplemente
        // `f` — igual que `proj[5]` — replicando EXACTO el mismo pipeline.
        // Ningún test existente lo detectaba: los 14 casos de
        // [PerspectiveCameraMathTest] cubrían las 4 funciones escalares
        // (`depthZ`/`eyeZ`/`fovyDeg`/`depthCompensation`), pero ninguno
        // ejercitaba `projectQuadCornersNdc` con un viewport no cuadrado —
        // ver los casos nuevos agregados junto a este fix.
        //
        // Proyección (misma convención que Matrix.perspectiveM: column-major).
        val proj = Mat4.identity()
        proj[0] = f
        proj[5] = f
        proj[10] = (far + near) / (near - far)
        proj[11] = -1f
        proj[14] = (2f * far * near) / (near - far)
        proj[15] = 0f

        // View (setLookAtM(0,0,eyeZ -> 0,0,0, up=0,1,0)): cámara mirando -Z.
        val view = Mat4.lookAt(eyeX = 0f, eyeY = 0f, eyeZ = eyeZ, centerX = 0f, centerY = 0f, centerZ = 0f, upX = 0f, upY = 1f, upZ = 0f)

        val depthZVal = depthZ(parallaxFactor, MAX_DEPTH)
        val depthComp = depthCompensation(depthZVal, baseEyeZ)

        val imageAspect = imageWidthPx.toFloat() / imageHeightPx.toFloat()
        val viewportAspect = viewportWidthPx / viewportHeightPx
        val fitScaleX: Float
        val fitScaleY: Float
        if (imageAspect > viewportAspect) {
            fitScaleX = 2f
            fitScaleY = 2f * viewportAspect / imageAspect
        } else {
            fitScaleY = 2f
            fitScaleX = 2f * imageAspect / viewportAspect
        }

        var model = Mat4.identity()
        model = Mat4.multiply(model, Mat4.translate(translateX * parallaxFactor, translateY * parallaxFactor, depthZVal))
        model = Mat4.multiply(model, Mat4.rotate(rotationDeg, 0f, 0f, 1f))
        model = Mat4.multiply(model, Mat4.rotate(tiltXDeg, 1f, 0f, 0f))
        model = Mat4.multiply(model, Mat4.rotate(tiltYDeg, 0f, 1f, 0f))
        model = Mat4.multiply(model, Mat4.scale(
            fitScaleX * depthComp * scaleVal * scaleXVal,
            fitScaleY * depthComp * scaleVal * scaleYVal,
            1f
        ))

        val mvp = Mat4.multiply(Mat4.multiply(proj, view), model)

        // Mismo orden que LayerDrawer.quadVertices: arriba-izq, abajo-izq, arriba-der, abajo-der.
        val localCorners = listOf(
            floatArrayOf(-0.5f, 0.5f, 0f, 1f),
            floatArrayOf(-0.5f, -0.5f, 0f, 1f),
            floatArrayOf(0.5f, 0.5f, 0f, 1f),
            floatArrayOf(0.5f, -0.5f, 0f, 1f)
        )
        return localCorners.map { corner ->
            val clip = Mat4.multiplyVec(mvp, corner)
            val w = if (clip[3] == 0f) 1f else clip[3]
            floatArrayOf(clip[0] / w, clip[1] / w)
        }
    }
}

/**
 * Matrices 4×4 column-major mínimas (mismo layout que `android.opengl.Matrix`)
 * para poder replicar en JVM puro — sin `GLES20`/`android.opengl.Matrix` — el
 * pipeline de [PerspectiveCameraMath.projectQuadCornersNdc]. Solo las
 * operaciones que ese pipeline necesita; no pretende ser una librería
 * general de álgebra lineal.
 */
internal object Mat4 {
    fun identity(): FloatArray {
        val m = FloatArray(16)
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
        return m
    }

    fun translate(x: Float, y: Float, z: Float): FloatArray {
        val m = identity()
        m[12] = x; m[13] = y; m[14] = z
        return m
    }

    fun scale(x: Float, y: Float, z: Float): FloatArray {
        val m = identity()
        m[0] = x; m[5] = y; m[10] = z
        return m
    }

    /** Rotación por ángulo-eje (grados), fórmula de Rodrigues — mismo resultado que `Matrix.rotateM`. */
    fun rotate(angleDeg: Float, x: Float, y: Float, z: Float): FloatArray {
        val len = kotlin.math.sqrt(x * x + y * y + z * z)
        if (len == 0f) return identity()
        val nx = x / len; val ny = y / len; val nz = z / len
        val rad = Math.toRadians(angleDeg.toDouble())
        val c = Math.cos(rad).toFloat()
        val s = Math.sin(rad).toFloat()
        val ic = 1f - c
        val m = FloatArray(16)
        m[0] = c + nx * nx * ic
        m[1] = ny * nx * ic + nz * s
        m[2] = nz * nx * ic - ny * s
        m[3] = 0f
        m[4] = nx * ny * ic - nz * s
        m[5] = c + ny * ny * ic
        m[6] = nz * ny * ic + nx * s
        m[7] = 0f
        m[8] = nx * nz * ic + ny * s
        m[9] = ny * nz * ic - nx * s
        m[10] = c + nz * nz * ic
        m[11] = 0f
        m[15] = 1f
        return m
    }

    fun lookAt(eyeX: Float, eyeY: Float, eyeZ: Float, centerX: Float, centerY: Float, centerZ: Float, upX: Float, upY: Float, upZ: Float): FloatArray {
        var fx = centerX - eyeX; var fy = centerY - eyeY; var fz = centerZ - eyeZ
        val fLen = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fLen; fy /= fLen; fz /= fLen

        // s = f x up (normalizado)
        var sx = fy * upZ - fz * upY
        var sy = fz * upX - fx * upZ
        var sz = fx * upY - fy * upX
        val sLen = kotlin.math.sqrt(sx * sx + sy * sy + sz * sz)
        sx /= sLen; sy /= sLen; sz /= sLen

        // u = s x f
        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx

        val m = FloatArray(16)
        m[0] = sx; m[4] = sy; m[8] = sz; m[12] = 0f
        m[1] = ux; m[5] = uy; m[9] = uz; m[13] = 0f
        m[2] = -fx; m[6] = -fy; m[10] = -fz; m[14] = 0f
        m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f

        val translation = translate(-eyeX, -eyeY, -eyeZ)
        return multiply(m, translation)
    }

    /** result = a * b (column-major, mismo orden que Matrix.multiplyMM(result, 0, a, 0, b, 0)). */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += a[k * 4 + row] * b[col * 4 + k]
                }
                r[col * 4 + row] = sum
            }
        }
        return r
    }

    fun multiplyVec(m: FloatArray, v: FloatArray): FloatArray {
        val r = FloatArray(4)
        for (row in 0..3) {
            var sum = 0f
            for (k in 0..3) {
                sum += m[k * 4 + row] * v[k]
            }
            r[row] = sum
        }
        return r
    }
}

/**
 * Homografía plana ("mapeo cuadrado unitario → cuadrilátero arbitrario") —
 * fórmula clásica de mapeo/warp de texturas (ver Heckbert, "Fundamentals of
 * Texture Mapping and Image Warping", 1989). La necesitan
 * `hitTestLayerAt`/`screenPointToLayerUv` en `EditorScreen.kt` para
 * invertir correctamente el punto de pantalla → UV local de una capa
 * CUANDO el cuadrilátero proyectado no es un rectángulo (tilt distinto de
 * 0 — ver [PerspectiveCameraMath.projectQuadCornersNdc]). Con un
 * rectángulo (tilt=0), esta homografía se reduce exactamente a la
 * fórmula afín (rotar+escalar+trasladar) que esas dos funciones usaban
 * antes — cero regresión para ese caso.
 *
 * [p0]/[p1]/[p2]/[p3] son las 4 esquinas DESTINO (en el mismo espacio
 * que se necesite trabajar — NDC o píxeles, da igual, es lineal-
 * proyectivo) correspondientes, EN ORDEN, a las esquinas UV
 * (0,0)=arriba-izq, (1,0)=arriba-der, (1,1)=abajo-der, (0,1)=abajo-izq
 * del cuadrado unitario — mismo orden que [PerspectiveCameraMath.projectQuadCornersNdc]
 * ya reordenado (ver `layerBoundingQuadPx`).
 */
internal object QuadHomography {
    /** a,b,c,d,e,f,g,h de la matriz 3x3 [[a,b,c],[d,e,f],[g,h,1]]. */
    data class Coeffs(
        val a: Float, val b: Float, val c: Float,
        val d: Float, val e: Float, val f: Float,
        val g: Float, val h: Float
    )

    fun fromUnitSquare(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray): Coeffs {
        val dx1 = p1[0] - p2[0]; val dx2 = p3[0] - p2[0]; val dx3 = p0[0] - p1[0] + p2[0] - p3[0]
        val dy1 = p1[1] - p2[1]; val dy2 = p3[1] - p2[1]; val dy3 = p0[1] - p1[1] + p2[1] - p3[1]
        val denom = dx1 * dy2 - dy1 * dx2
        // dx3 == dy3 == 0 (paralelogramo — incluye el caso rectángulo sin
        // tilt) es un caso límite VÁLIDO, no un error: la porción
        // proyectiva (g, h) simplemente es 0 y el mapeo queda afín puro.
        val g: Float
        val h: Float
        if (kotlin.math.abs(denom) < 1e-9f) {
            g = 0f; h = 0f
        } else {
            g = (dx3 * dy2 - dy3 * dx2) / denom
            h = (dx1 * dy3 - dy1 * dx3) / denom
        }
        val a = p1[0] - p0[0] + g * p1[0]
        val b = p3[0] - p0[0] + h * p3[0]
        val c = p0[0]
        val d = p1[1] - p0[1] + g * p1[1]
        val e = p3[1] - p0[1] + h * p3[1]
        val f = p0[1]
        return Coeffs(a, b, c, d, e, f, g, h)
    }

    /** UV [0,1]x[0,1] -> punto en el espacio destino (mismo espacio que p0..p3). */
    fun mapUvToPoint(coeffs: Coeffs, u: Float, v: Float): FloatArray {
        val w = coeffs.g * u + coeffs.h * v + 1f
        val safeW = if (w == 0f) 1f else w
        return floatArrayOf(
            (coeffs.a * u + coeffs.b * v + coeffs.c) / safeW,
            (coeffs.d * u + coeffs.e * v + coeffs.f) / safeW
        )
    }

    /**
     * Punto en el espacio destino -> UV [0,1]x[0,1] (la inversa exacta de
     * [mapUvToPoint], resolviendo el sistema lineal 2×2 que resulta de
     * despejar u,v — no hace falta invertir la matriz 3×3 completa).
     * `null` solo en el caso degenerado (cuadrilátero de área cero).
     */
    fun mapPointToUv(coeffs: Coeffs, x: Float, y: Float): FloatArray? {
        val a11 = coeffs.a - x * coeffs.g
        val a12 = coeffs.b - x * coeffs.h
        val a21 = coeffs.d - y * coeffs.g
        val a22 = coeffs.e - y * coeffs.h
        val b1 = x - coeffs.c
        val b2 = y - coeffs.f
        val det = a11 * a22 - a12 * a21
        if (kotlin.math.abs(det) < 1e-9f) return null
        val u = (b1 * a22 - a12 * b2) / det
        val v = (a11 * b2 - b1 * a21) / det
        return floatArrayOf(u, v)
    }
}
