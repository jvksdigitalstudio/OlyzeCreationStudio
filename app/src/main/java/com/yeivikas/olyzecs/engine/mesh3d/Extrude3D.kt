package com.yeivikas.olyzecs.engine.mesh3d

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * FASE B — reclasificación de paquete: este archivo vivía en `data/`
 * (junto a `ProjectStorage`) a pesar de ser, en los hechos, un motor de
 * mesh 3D completo (trazado de contorno + bisel + generación de malla +
 * rasterización vía [MeshRasterizer]) — la auditoría lo marcó como el
 * caso principal de "Engine mal clasificado fuera de `engine/`". No tenía
 * ninguna dependencia real de `data.*` (solo de `android.graphics` y de
 * `engine.mesh3d.*`, ya en el mismo paquete que este archivo ahora), así
 * que el movimiento es puramente de paquete: comportamiento idéntico,
 * cero cambios de lógica. El único llamador (`EditorScreen`) ahora pasa
 * por `EditorViewModel.renderExtrude3D` en vez de llamar a
 * `Extrude3D.render` directo — ver KDoc de ese método.
 *
 * Extrusión 3D real de una capa (foto, PNG recortado, forma, texto ya
 * rasterizado): el contorno del recorte se convierte en una MALLA de
 * verdad (tapa frontal, tapa trasera y paredes laterales con bisel
 * redondeado, todo como triángulos con normal suave por vértice),
 * texturizada con la propia imagen, rotada en X/Y/Z y rasterizada con
 * z-buffer + iluminación Phong por píxel + supersampling por
 * [MeshRasterizer].
 *
 * Este archivo es solo la "receta" — arma la [Mesh3D] y se la entrega
 * al motor genérico en `engine.mesh3d` (que no sabe nada de este
 * efecto: solo rasteriza mallas con textura y luz). El día que se
 * quiera animar con huesos, el rig solo tiene que mover
 * [Vertex3D.position]/[Vertex3D.normal] antes de rasterizar, sin
 * tocar el rasterizador.
 *
 * Cómo se texturiza cada parte:
 *  - Tapas: UV = posición del píxel en la imagen original.
 *  - Paredes: cada vértice del canto usa el UV del píxel de borde de
 *    la imagen del que salió (el mismo en todas las bandas de
 *    profundidad de esa arista) — el color real de ese borde se
 *    "estira" hacia adentro en vez de un color plano inventado.
 *
 * Sobre las normales de las paredes: no son "una por cara" (eso deja
 * ver las costuras entre bandas del bisel) — cada vértice lleva la
 * normal exacta del perfil curvo del bisel en ese punto (derivada del
 * círculo que aproxima), combinada con la dirección de bisectriz de
 * esa esquina del contorno. El resultado es un bisel que se sombrea
 * como una curva de verdad.
 *
 * Sobre el offset de las paredes: en vez de un offset "ingenuo" que
 * puede cruzarse a sí mismo en zonas angostas de la silueta (dos
 * lados opuestos del contorno más cerca entre sí que lo que el bisel
 * quiere abultar — ahí es donde salía el pico/aguja), cada vértice
 * calcula primero su "espacio libre local" (distancia al resto del
 * contorno, no solo a sus vecinos inmediatos) y el offset nunca puede
 * superar una fracción segura de ese espacio.
 */
object Extrude3D {

    /** Parámetros del efecto, uno a uno con los sliders del panel. */
    data class Params(
        val rotationXDeg: Float = 0f,
        val rotationYDeg: Float = 0f,
        val rotationZDeg: Float = 0f,
        /** 0f..1f, relativo al tamaño propio de la forma. */
        val depth: Float = 0.35f,
        /** 0f..1f, cuánto se redondea el canto. */
        val bevel: Float = 0.5f,
        /** 0f..1f, opacidad general del cuerpo extruido (tapas y paredes). */
        val opacity: Float = 1f
    )

    // Perfil de calidad "en vivo" (mientras se arrastra el slider o se
    // orbita con el dedo, decenas de veces por segundo — tiene que ser
    // barato) vs. calidad "final" (el commit con debounce de 500ms y
    // la exportación, donde el costo de CPU ya no compite con la
    // fluidez del gesto). Antes ambos casos compartían las mismas
    // constantes: para que el arrastre fuera fluido, la calidad final
    // quedaba tan baja como la del preview. Con dos perfiles, el
    // resultado que el usuario realmente guarda/exporta sale con
    // bisel más redondeado, contorno más fiel y bordes más limpios,
    // sin penalizar la fluidez mientras se está ajustando.
    private const val BEVEL_STEPS_LIVE = 6
    private const val BEVEL_STEPS_FINAL = 12
    private const val CAP_GRID = 32
    private const val MASK_MAX_SIDE_LIVE = 240
    private const val MASK_MAX_SIDE_FINAL = 360
    private const val SUPERSAMPLE_LIVE = 2
    private const val SUPERSAMPLE_FINAL = 3

    /**
     * @param highQuality Perfil de render: `true` (por defecto) usa
     * bisel más redondeado, contorno más fiel a la silueta original y
     * mayor supersampling — pensado para el commit/export. `false` usa
     * un perfil liviano pensado para la vista previa en vivo mientras
     * se arrastra un slider o se orbita con el dedo.
     */
    fun render(source: Bitmap, params: Params, highQuality: Boolean = true): Bitmap {
        val srcW = source.width
        val srcH = source.height
        if (srcW <= 0 || srcH <= 0) return source

        val bevelSteps = if (highQuality) BEVEL_STEPS_FINAL else BEVEL_STEPS_LIVE
        val maskMaxSide = if (highQuality) MASK_MAX_SIDE_FINAL else MASK_MAX_SIDE_LIVE
        val supersample = if (highQuality) SUPERSAMPLE_FINAL else SUPERSAMPLE_LIVE

        val refRadius = max(srcW, srcH) / 2f
        val halfDepth = params.depth.coerceIn(0f, 1f) * refRadius * 0.9f
        val bevelAmount = params.bevel.coerceIn(0f, 1f) * refRadius * 0.22f
        val opacity = params.opacity.coerceIn(0f, 1f)

        // Rotación libre en las 3 ejes: Camera3D es una rotación
        // matricial + proyección en perspectiva pura (ver Camera3D.kt),
        // sin ningún supuesto de "cámara siempre mirando el frente" —
        // y MeshRasterizer resuelve la visibilidad con z-buffer real
        // (no descarta caras traseras por normal), así que la tapa de
        // atrás aparece sola, correctamente, en cuanto la rotación la
        // deja de cara a cámara. No hace falta (ni conviene) recortar
        // X/Y a ±60°: eso es lo que impedía mirar el cuerpo de atrás,
        // de arriba o de abajo. Mismo rango que Z, que nunca tuvo el
        // límite angosto.
        val camera = Camera3D(
            params.rotationXDeg.coerceIn(-180f, 180f).toDouble(),
            params.rotationYDeg.coerceIn(-180f, 180f).toDouble(),
            params.rotationZDeg.coerceIn(-180f, 180f).toDouble(),
            refRadius * 4.2
        )

        val margin = (refRadius * 1.35f).toInt().coerceAtLeast(4)
        val outW = srcW + margin * 2
        val outH = srcH + margin * 2
        val originX = outW / 2f
        val originY = outH / 2f

        // Una sola malla con tapas + paredes: el z-buffer del
        // rasterizador cubre todo junto en una pasada, así que el
        // orden en que se agregan los triángulos no importa.
        val mesh = Mesh3D()
        buildCap(mesh, srcW, srcH, halfDepth, Vec3(0.0, 0.0, 1.0))
        buildCap(mesh, srcW, srcH, -halfDepth, Vec3(0.0, 0.0, -1.0))
        buildWalls(
            mesh,
            traceAlphaBoundaries(source, maskMaxSide),
            srcW, srcH, halfDepth, bevelAmount,
            bevelSteps = bevelSteps,
            extraSmoothPass = highQuality
        )

        val alpha255 = (opacity * 255).roundToInt().coerceIn(0, 255)
        return MeshRasterizer.render(
            mesh, camera, source, outW, outH, originX, originY, alpha255,
            supersample = supersample
        )
    }

    // ---------------------------------------------------------------
    // Tapas: grilla de triángulos con UV = posición del píxel en la
    // imagen y normal constante (0,0,±1) — son planas de verdad.
    // ---------------------------------------------------------------
    private fun buildCap(mesh: Mesh3D, srcW: Int, srcH: Int, zLocal: Float, normal: Vec3) {
        val cols = CAP_GRID
        val rows = CAP_GRID
        val halfW = srcW / 2.0
        val halfH = srcH / 2.0
        val verts = Array(rows + 1) { gy ->
            val v = gy.toFloat() / rows
            Array(cols + 1) { gx ->
                val u = gx.toFloat() / cols
                Vertex3D(Vec3(-halfW + u * srcW, -halfH + v * srcH, zLocal.toDouble()), normal, u, v)
            }
        }
        for (gy in 0 until rows) {
            for (gx in 0 until cols) {
                mesh.addQuad(verts[gy][gx], verts[gy][gx + 1], verts[gy + 1][gx + 1], verts[gy + 1][gx])
            }
        }
    }

    // ---------------------------------------------------------------
    // Paredes.
    // ---------------------------------------------------------------

    /**
     * Una banda del perfil de bisel: profundidad [z], cuánto se abre
     * hacia afuera [outward], y la normal del perfil en ese punto de
     * la curva descompuesta en componente radial [normalRadial] (se
     * multiplica por la dirección 2D de cada vértice) y componente
     * axial [normalZ] — juntas dan la normal 3D real del bisel curvo,
     * no una aproximación plana.
     */
    private data class Band(val z: Float, val outward: Float, val normalRadial: Float, val normalZ: Float)

    private fun bevelBands(halfDepth: Float, bevelAmount: Float, steps: Int): List<Band> {
        // El "outward" se calcula sobre `span` (ya recortado por la
        // profundidad disponible), no sobre bevelAmount crudo — si no,
        // con poca profundidad y bisel alto el canto pide más vuelo
        // lateral del que el espesor permite y se desborda.
        val span = min(bevelAmount, halfDepth * 0.9f).coerceAtLeast(0.001f)
        val bands = ArrayList<Band>()
        for (i in 0..steps) {
            val angle = (i.toFloat() / steps) * (Math.PI / 2)
            val outward = (span * sin(angle)).toFloat()
            val zDrop = (span * (1 - cos(angle))).toFloat()
            bands.add(Band(halfDepth - zDrop, outward, sin(angle).toFloat(), cos(angle).toFloat()))
        }
        val midFront = halfDepth - span
        val midBack = -(halfDepth - span)
        if (midFront > midBack + 0.01f) bands.add(Band(midBack, span, 1f, 0f))
        for (i in steps downTo 0) {
            val angle = (i.toFloat() / steps) * (Math.PI / 2)
            val outward = (span * sin(angle)).toFloat()
            val zDrop = (span * (1 - cos(angle))).toFloat()
            bands.add(Band(-(halfDepth - zDrop), outward, sin(angle).toFloat(), -cos(angle).toFloat()))
        }
        return bands
    }

    /** Dirección de offset (bisectriz de las 2 aristas vecinas) por vértice, más el factor de miter. */
    private data class VertexDir(val dx: Float, val dy: Float, val cosHalf: Float)

    private fun computeVertexDirs(loop: List<PointF>): List<VertexDir> {
        val n = loop.size
        val out = ArrayList<VertexDir>(n)
        for (i in 0 until n) {
            val prev = loop[(i - 1 + n) % n]
            val curr = loop[i]
            val next = loop[(i + 1) % n]

            val d0x = curr.x - prev.x; val d0y = curr.y - prev.y
            val len0 = hypot(d0x.toDouble(), d0y.toDouble()).toFloat().let { if (it < 1e-4f) 1f else it }
            val n0x = d0y / len0; val n0y = -d0x / len0

            val d1x = next.x - curr.x; val d1y = next.y - curr.y
            val len1 = hypot(d1x.toDouble(), d1y.toDouble()).toFloat().let { if (it < 1e-4f) 1f else it }
            val n1x = d1y / len1; val n1y = -d1x / len1

            var mx = n0x + n1x; var my = n0y + n1y
            val mlen = hypot(mx.toDouble(), my.toDouble()).toFloat()
            if (mlen < 1e-4f) {
                out.add(VertexDir(n0x, n0y, 1f))
            } else {
                mx /= mlen; my /= mlen
                val cosHalf = (mx * n0x + my * n0y).coerceIn(0.15f, 1f)
                out.add(VertexDir(mx, my, cosHalf))
            }
        }
        return out
    }

    // Offset con join (miter, con límite) usando direcciones ya
    // calculadas y una distancia MÁXIMA POR VÉRTICE (ver
    // `localClearances`) — así nunca se cruza a sí mismo en partes
    // angostas de la silueta.
    private fun offsetPolygon(loop: List<PointF>, dirs: List<VertexDir>, distances: FloatArray): List<PointF> {
        val n = loop.size
        return List(n) { i ->
            val d = distances[i]
            if (d <= 0.0001f) return@List loop[i]
            val dir = dirs[i]
            val miterLen = min(d / dir.cosHalf, d * 4f)
            PointF(loop[i].x + dir.dx * miterLen, loop[i].y + dir.dy * miterLen)
        }
    }

    // "Espacio libre local" por vértice: distancia mínima a cualquier
    // otra arista del MISMO lazo (ignorando sus 2 vecinas inmediatas,
    // que son las que naturalmente están cerca por definición). En
    // formas angostas (un dedo, una punta, un asa fina) esto detecta
    // que el otro lado del contorno está cerca y evita que el offset
    // lo atraviese — la causa real del pico/aguja que se veía antes.
    private fun localClearances(loop: List<PointF>): FloatArray {
        val n = loop.size
        val clear = FloatArray(n) { Float.MAX_VALUE }
        for (i in 0 until n) {
            val p = loop[i]
            for (j in 0 until n) {
                val fwd = (j - i + n) % n
                val bwd = (i - j + n) % n
                if (min(fwd, bwd) <= 2) continue // arista propia y vecinas inmediatas
                val a = loop[j]; val b = loop[(j + 1) % n]
                val d = pointSegmentDistance(p, a, b)
                if (d < clear[i]) clear[i] = d
            }
        }
        return smoothClearances(clear)
    }

    /**
     * Filtro de mínimo (erosión) circular, ventana de 5, sobre el
     * "espacio libre local" crudo. Sin esto, el ancho del bisel podía
     * saltar de golpe de un vértice al siguiente cada vez que
     * `localClearances` encontraba una arista lejana apenas un poco
     * más cerca (frecuente en siluetas con ruido de contorno, como
     * dedos, pliegues o bordes finos) — ese salto se veía como un
     * "escalón"/costura puntual en el canto en vez de un bisel parejo.
     *
     * Se usa MÍNIMO y no promedio a propósito: el promedio puede subir
     * el valor justo en el vértice más angosto (el que da la
     * protección real contra el pico/aguja) si sus vecinos tienen más
     * espacio, reabriendo el mismo bug que `localClearances` evita. El
     * mínimo por ventana nunca puede superar el valor crudo, así que
     * solo puede volverse MÁS conservador — sigue siendo seguro contra
     * auto-intersección — y de paso extiende el angostamiento de un
     * único vértice ruidoso a sus vecinos inmediatos, que es
     * exactamente lo que lo convierte en una transición suave en vez
     * de una muesca puntual.
     */
    private fun smoothClearances(clear: FloatArray): FloatArray {
        val n = clear.size
        if (n < 5) return clear
        val fallback = clear.filter { it.isFinite() }.minOrNull() ?: 0f
        val finite = FloatArray(n) { if (clear[it].isFinite()) clear[it] else fallback }
        val out = FloatArray(n)
        for (i in 0 until n) {
            var m = Float.MAX_VALUE
            for (k in -2..2) m = min(m, finite[(i + k + n) % n])
            out[i] = m
        }
        return out
    }

    private fun pointSegmentDistance(p: PointF, a: PointF, b: PointF): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-6f) return hypot((p.x - a.x).toDouble(), (p.y - a.y).toDouble()).toFloat()
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        val px = a.x + t * dx; val py = a.y + t * dy
        return hypot((p.x - px).toDouble(), (p.y - py).toDouble()).toFloat()
    }

    // Suaviza el contorno (Chaikin, corta esquinas) antes de generar
    // las paredes — el trazador por marching squares deja micro
    // escalones en diagonales; sin este paso esos escalones se
    // vuelven esquinas filosas de más para el offset con miter.
    private fun smoothLoop(points: List<PointF>): List<PointF> {
        if (points.size < 4) return points
        val n = points.size
        val out = ArrayList<PointF>(n * 2)
        for (i in 0 until n) {
            val p0 = points[i]
            val p1 = points[(i + 1) % n]
            out.add(PointF(p0.x * 0.75f + p1.x * 0.25f, p0.y * 0.75f + p1.y * 0.25f))
            out.add(PointF(p0.x * 0.25f + p1.x * 0.75f, p0.y * 0.25f + p1.y * 0.75f))
        }
        return out
    }

    private fun buildWalls(
        mesh: Mesh3D,
        loopsPx: List<List<PointF>>,
        srcW: Int,
        srcH: Int,
        halfDepth: Float,
        bevelAmount: Float,
        bevelSteps: Int,
        extraSmoothPass: Boolean
    ) {
        if (loopsPx.isEmpty() || halfDepth <= 0.01f) return
        val halfW = srcW / 2f
        val halfH = srcH / 2f
        val bands = bevelBands(halfDepth, bevelAmount, bevelSteps)

        for (rawLoop in loopsPx) {
            if (rawLoop.size < 3) continue
            // Coordenadas de píxel de `source` (para UV) tras suavizar.
            // En calidad final se aplica una segunda pasada de Chaikin:
            // el trazador por marching squares deja micro-escalones de
            // grilla que una sola pasada no termina de limar del todo,
            // sobre todo en partes finas de la silueta (dedos, patas,
            // bordes casi diagonales) — ahí es donde se veía el canto
            // "dentado" en vez de una curva limpia.
            var loop = smoothLoop(rawLoop)
            if (extraSmoothPass) loop = smoothLoop(loop)
            val n = loop.size
            // Mismas coordenadas pero centradas en el origen (para la
            // geometría 3D, que vive centrada como las tapas).
            val flatLoop = loop.map { PointF(it.x - halfW, it.y - halfH) }

            val dirs = computeVertexDirs(flatLoop)
            val clearances = localClearances(flatLoop)
            // Offset con join calculado UNA vez por banda para todo el
            // lazo (vértices compartidos entre aristas vecinas
            // coinciden exactamente) y acotado por el espacio libre
            // local de cada vértice.
            val bandPolys = bands.map { b ->
                val distances = FloatArray(n) { i -> min(b.outward, clearances[i] * 0.45f) }
                offsetPolygon(flatLoop, dirs, distances)
            }
            val us = loop.map { (it.x / srcW).coerceIn(0f, 1f) }
            val vs = loop.map { (it.y / srcH).coerceIn(0f, 1f) }

            for (e in 0 until n) {
                val e2 = (e + 1) % n
                // El UV de esta arista es siempre el del píxel de
                // borde original — se reutiliza igual en todas las
                // bandas de profundidad, por eso el color real del
                // borde queda "estirado" hacia adentro del canto.
                val u0 = us[e]; val v0 = vs[e]
                val u1 = us[e2]; val v1 = vs[e2]
                val dir0 = dirs[e]; val dir1 = dirs[e2]

                for (bi in 0 until bands.size - 1) {
                    val b0 = bands[bi]; val b1 = bands[bi + 1]
                    val p00 = bandPolys[bi][e]; val p01 = bandPolys[bi][e2]
                    val p11 = bandPolys[bi + 1][e2]; val p10 = bandPolys[bi + 1][e]

                    val pos00 = Vec3(p00.x.toDouble(), p00.y.toDouble(), b0.z.toDouble())
                    val pos01 = Vec3(p01.x.toDouble(), p01.y.toDouble(), b0.z.toDouble())
                    val pos11 = Vec3(p11.x.toDouble(), p11.y.toDouble(), b1.z.toDouble())
                    val pos10 = Vec3(p10.x.toDouble(), p10.y.toDouble(), b1.z.toDouble())

                    // Normal real del perfil curvo del bisel en cada
                    // vértice (bisectriz 2D del contorno combinada con
                    // el ángulo del perfil) — no una normal plana por
                    // cara, así el sombreado no muestra costuras entre
                    // bandas.
                    val norm00 = profileNormal(dir0, b0)
                    val norm01 = profileNormal(dir1, b0)
                    val norm11 = profileNormal(dir1, b1)
                    val norm10 = profileNormal(dir0, b1)

                    mesh.addQuad(
                        Vertex3D(pos00, norm00, u0, v0),
                        Vertex3D(pos01, norm01, u1, v1),
                        Vertex3D(pos11, norm11, u1, v1),
                        Vertex3D(pos10, norm10, u0, v0)
                    )
                }
            }
        }
    }

    private fun profileNormal(dir: VertexDir, band: Band): Vec3 = Vec3(
        (dir.dx * band.normalRadial).toDouble(),
        (dir.dy * band.normalRadial).toDouble(),
        band.normalZ.toDouble()
    ).normalized()

    // ---------------------------------------------------------------
    // Contorno del canal alpha por marching squares — devuelve uno o
    // más lazos cerrados en coordenadas de píxel de `source`. Sirve
    // igual para una forma sólida, una foto recortada, o texto (cada
    // letra/agujero sale como su propio lazo, sin lógica especial).
    // ---------------------------------------------------------------
    private fun traceAlphaBoundaries(source: Bitmap, maskMaxSide: Int): List<List<PointF>> {
        val srcW = source.width
        val srcH = source.height
        val scale = maskMaxSide.toFloat() / max(srcW, srcH)
        val useScale = scale < 1f
        val mw = if (useScale) max(1, (srcW * scale).roundToInt()) else srcW
        val mh = if (useScale) max(1, (srcH * scale).roundToInt()) else srcH
        val sampled = if (useScale) {
            runCatching { Bitmap.createScaledBitmap(source, mw, mh, true) }.getOrNull() ?: source
        } else source

        val pixels = IntArray(mw * mh)
        sampled.getPixels(pixels, 0, mw, 0, 0, mw, mh)
        fun opaque(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x >= mw || y >= mh) return false
            return ((pixels[y * mw + x] ushr 24) and 0xFF) >= 24
        }

        data class Seg(val a: PointF, val b: PointF)
        val segs = ArrayList<Seg>()
        for (cy in -1 until mh) {
            for (cx in -1 until mw) {
                val tl = opaque(cx, cy)
                val tr = opaque(cx + 1, cy)
                val br = opaque(cx + 1, cy + 1)
                val bl = opaque(cx, cy + 1)
                val code = (if (tl) 8 else 0) or (if (tr) 4 else 0) or (if (br) 2 else 0) or (if (bl) 1 else 0)
                if (code == 0 || code == 15) continue
                val top = PointF(cx + 0.5f, cy.toFloat())
                val bottom = PointF(cx + 0.5f, cy + 1f)
                val left = PointF(cx.toFloat(), cy + 0.5f)
                val right = PointF(cx + 1f, cy + 0.5f)
                when (code) {
                    1 -> segs.add(Seg(left, bottom))
                    2 -> segs.add(Seg(bottom, right))
                    3 -> segs.add(Seg(left, right))
                    4 -> segs.add(Seg(right, top))
                    5 -> { segs.add(Seg(top, left)); segs.add(Seg(bottom, right)) }
                    6 -> segs.add(Seg(bottom, top))
                    7 -> segs.add(Seg(left, top))
                    8 -> segs.add(Seg(top, left))
                    9 -> segs.add(Seg(top, bottom))
                    10 -> { segs.add(Seg(right, top)); segs.add(Seg(left, bottom)) }
                    11 -> segs.add(Seg(top, right))
                    12 -> segs.add(Seg(right, left))
                    13 -> segs.add(Seg(right, bottom))
                    14 -> segs.add(Seg(bottom, left))
                }
            }
        }

        fun key(p: PointF) = ((p.x * 4).roundToInt().toLong() shl 32) xor (p.y * 4).roundToInt().toLong()
        val byStart = HashMap<Long, MutableList<Int>>()
        segs.forEachIndexed { i, s -> byStart.getOrPut(key(s.a)) { ArrayList() }.add(i) }
        val used = BooleanArray(segs.size)
        val loops = ArrayList<List<PointF>>()
        for (startIdx in segs.indices) {
            if (used[startIdx]) continue
            val loop = ArrayList<PointF>()
            val startKey = key(segs[startIdx].a)
            var idx = startIdx
            var guard = 0
            while (guard++ <= segs.size + 2) {
                if (used[idx]) break
                used[idx] = true
                val seg = segs[idx]
                loop.add(seg.a)
                val nk = key(seg.b)
                if (nk == startKey) break
                val next = byStart[nk]?.firstOrNull { !used[it] } ?: break
                idx = next
            }
            if (loop.size >= 3) loops.add(loop)
        }

        val gx = srcW.toFloat() / mw
        val gy = srcH.toFloat() / mh
        val tolerance = max(srcW, srcH) * 0.006f
        val result = loops
            .map { loop -> simplifyLoop(loop.map { PointF(it.x * gx, it.y * gy) }, tolerance) }
            .filter { it.size >= 3 }
        if (useScale && sampled !== source) sampled.recycle()
        return result
    }

    private fun simplifyLoop(points: List<PointF>, tolerance: Float): List<PointF> {
        if (points.size <= 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        fun dp(lo: Int, hi: Int) {
            if (hi <= lo + 1) return
            var maxDist = -1f
            var maxIdx = -1
            val a = points[lo]; val b = points[hi]
            val dx = b.x - a.x; val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            for (i in lo + 1 until hi) {
                val p = points[i]
                val d = if (len2 < 1e-6f) {
                    hypot((p.x - a.x).toDouble(), (p.y - a.y).toDouble()).toFloat()
                } else {
                    val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2
                    val projX = a.x + t * dx
                    val projY = a.y + t * dy
                    hypot((p.x - projX).toDouble(), (p.y - projY).toDouble()).toFloat()
                }
                if (d > maxDist) { maxDist = d; maxIdx = i }
            }
            if (maxDist > tolerance && maxIdx >= 0) {
                keep[maxIdx] = true
                dp(lo, maxIdx)
                dp(maxIdx, hi)
            }
        }
        dp(0, points.size - 1)
        return points.filterIndexed { i, _ -> keep[i] }
    }
}
