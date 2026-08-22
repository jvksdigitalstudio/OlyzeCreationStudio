package com.yeivikas.olyzecs.engine.mesh3d

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Rasteriza una [Mesh3D] completa (tapas + paredes, todo junto) con:
 *  - z-buffer real por píxel (no algoritmo del pintor).
 *  - UV y NORMAL interpolados con corrección de perspectiva (se
 *    interpola atributo/w y 1/w por separado y se divide al final —
 *    igual que hace una GPU), así que ni la textura ni el sombreado se
 *    deforman en ángulos de cámara fuertes.
 *  - Iluminación de 3 términos por píxel, acabado "producto" en vez de
 *    un solo foco plano:
 *     - Difusa (Lambert) desde la luz principal.
 *     - Especular (Blinn-Phong, acabado tipo plástico/metal brillante).
 *     - Luz de relleno (fill) tenue desde el lado opuesto, solo
 *       difusa: evita que el lado en sombra caiga a negro puro, que es
 *       lo que delata un render casero — un bisel curvo se ve como una
 *       curva de verdad, sin costuras entre bandas y sin caras muertas.
 *     - Fresnel/rim sutil (más luz rasante en el borde donde la normal
 *       queda casi perpendicular a la cámara): es lo que separa un
 *       bisel "plástico premium" de uno plano, porque el canto se
 *       enciende un poco incluso lejos de la luz principal, igual que
 *       en un render de producto de verdad.
 *  - Supersampling (2x por defecto, configurable) para bordes
 *    anti-aliased de verdad en vez de dientes de sierra.
 *
 * No sabe nada de "Extrude3D" ni de ningún efecto en particular: solo
 * recibe una malla, una cámara, una luz y una textura.
 */
object MeshRasterizer {

    data class Light(
        val direction: Vec3,
        val ambient: Double = 0.16,
        val diffuse: Double = 0.68,
        val specular: Double = 0.42,
        val shininess: Double = 26.0,
        /** Luz de relleno opuesta a [direction], solo difusa, para que las zonas en sombra no caigan a negro puro. */
        val fillDirection: Vec3 = (direction * -1.0 + Vec3(0.0, 0.0, 0.35)).normalized(),
        val fillDiffuse: Double = 0.20,
        /** Intensidad del realce Fresnel/rim en los bordes casi de perfil a cámara. 0 lo desactiva. */
        val rimStrength: Double = 0.16,
        val rimPower: Double = 2.4
    )

    fun render(
        mesh: Mesh3D,
        camera: Camera3D,
        texture: Bitmap,
        outW: Int,
        outH: Int,
        originX: Float,
        originY: Float,
        globalAlpha255: Int,
        light: Light = Light(Vec3(-0.35, -0.55, 0.76).normalized()),
        supersample: Int = 2
    ): Bitmap {
        val ss = supersample.coerceIn(1, 4)
        val rw = outW * ss
        val rh = outH * ss
        val pixels = IntArray(rw * rh)
        val depthBuf = FloatArray(rw * rh) { Float.NEGATIVE_INFINITY }

        val texW = texture.width
        val texH = texture.height
        val texPixels = IntArray(texW * texH)
        texture.getPixels(texPixels, 0, texW, 0, 0, texW, texH)

        // Vista aproximada: cámara lejana y fija mirando hacia +Z en
        // espacio de cámara (ver Camera3D.project) — suficiente para
        // un realce especular convincente sin tener que recalcular la
        // dirección exacta por píxel (estándar en renderers de bajo
        // costo con FOV moderado).
        val viewDir = Vec3(0.0, 0.0, 1.0)
        val halfVec = (light.direction + viewDir).normalized()

        for (tri in mesh.triangles) {
            val ra = camera.rotate(tri.a.position); val rna = camera.rotate(tri.a.normal).normalized()
            val rb = camera.rotate(tri.b.position); val rnb = camera.rotate(tri.b.normal).normalized()
            val rc = camera.rotate(tri.c.position); val rnc = camera.rotate(tri.c.normal).normalized()
            val wa = camera.w(ra.z); val wb = camera.w(rb.z); val wc = camera.w(rc.z)
            if (wa <= 1e-6 || wb <= 1e-6 || wc <= 1e-6) continue // detrás de la cámara

            val pa = camera.project(ra); val pb = camera.project(rb); val pc = camera.project(rc)
            rasterizeTriangle(
                originX * ss + pa[0] * ss, originY * ss + pa[1] * ss, wa, tri.a.u, tri.a.v, rna,
                originX * ss + pb[0] * ss, originY * ss + pb[1] * ss, wb, tri.b.u, tri.b.v, rnb,
                originX * ss + pc[0] * ss, originY * ss + pc[1] * ss, wc, tri.c.u, tri.c.v, rnc,
                texPixels, texW, texH,
                pixels, depthBuf, rw, rh, globalAlpha255,
                light, halfVec
            )
        }

        if (ss == 1) return Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)
        return downsample(pixels, rw, rh, ss, outW, outH)
    }

    private fun downsample(src: IntArray, rw: Int, rh: Int, ss: Int, outW: Int, outH: Int): Bitmap {
        val out = IntArray(outW * outH)
        val norm = 1f / (ss * ss)
        for (oy in 0 until outH) {
            val sy0 = oy * ss
            for (ox in 0 until outW) {
                val sx0 = ox * ss
                var a = 0; var r = 0; var g = 0; var b = 0
                for (dy in 0 until ss) {
                    val row = (sy0 + dy) * rw
                    for (dx in 0 until ss) {
                        val c = src[row + sx0 + dx]
                        a += (c ushr 24) and 0xFF
                        r += (c ushr 16) and 0xFF
                        g += (c ushr 8) and 0xFF
                        b += c and 0xFF
                    }
                }
                val ai = (a * norm).toInt().coerceIn(0, 255)
                val ri = (r * norm).toInt().coerceIn(0, 255)
                val gi = (g * norm).toInt().coerceIn(0, 255)
                val bi = (b * norm).toInt().coerceIn(0, 255)
                out[oy * outW + ox] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        return Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888)
    }

    private fun rasterizeTriangle(
        x0: Float, y0: Float, w0: Double, u0: Float, v0: Float, n0: Vec3,
        x1: Float, y1: Float, w1: Double, u1: Float, v1: Float, n1: Vec3,
        x2: Float, y2: Float, w2: Double, u2: Float, v2: Float, n2: Vec3,
        texPixels: IntArray, texW: Int, texH: Int,
        pixels: IntArray, depthBuf: FloatArray, outW: Int, outH: Int, globalAlpha255: Int,
        light: Light, halfVec: Vec3
    ) {
        val minX = max(0, floor(min(x0, min(x1, x2))).toInt())
        val maxX = min(outW - 1, ceil(max(x0, max(x1, x2))).toInt())
        val minY = max(0, floor(min(y0, min(y1, y2))).toInt())
        val maxY = min(outH - 1, ceil(max(y0, max(y1, y2))).toInt())
        if (minX > maxX || minY > maxY) return

        val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if (abs(area) < 1e-4f) return
        val invArea = 1f / area

        // 1/w por vértice: base de toda la interpolación perspective-correct.
        val iw0 = 1.0 / w0; val iw1 = 1.0 / w1; val iw2 = 1.0 / w2
        val u0w = u0 * iw0.toFloat(); val v0w = v0 * iw0.toFloat()
        val u1w = u1 * iw1.toFloat(); val v1w = v1 * iw1.toFloat()
        val u2w = u2 * iw2.toFloat(); val v2w = v2 * iw2.toFloat()
        val nx0w = (n0.x * iw0).toFloat(); val ny0w = (n0.y * iw0).toFloat(); val nz0w = (n0.z * iw0).toFloat()
        val nx1w = (n1.x * iw1).toFloat(); val ny1w = (n1.y * iw1).toFloat(); val nz1w = (n1.z * iw1).toFloat()
        val nx2w = (n2.x * iw2).toFloat(); val ny2w = (n2.y * iw2).toFloat(); val nz2w = (n2.z * iw2).toFloat()

        for (py in minY..maxY) {
            val cy = py + 0.5f
            val rowBase = py * outW
            for (px in minX..maxX) {
                val cx = px + 0.5f
                val a = ((x1 - cx) * (y2 - cy) - (x2 - cx) * (y1 - cy)) * invArea
                val b = ((x2 - cx) * (y0 - cy) - (x0 - cx) * (y2 - cy)) * invArea
                val c = 1f - a - b
                if (a < -1e-4f || b < -1e-4f || c < -1e-4f) continue

                val invW = a * iw0 + b * iw1 + c * iw2
                val i = rowBase + px
                val depthMetric = invW.toFloat()
                if (depthMetric <= depthBuf[i]) continue

                val u = (a * u0w + b * u1w + c * u2w) / invW.toFloat()
                val v = (a * v0w + b * v1w + c * v2w) / invW.toFloat()
                val texColor = sampleBilinear(texPixels, texW, texH, u, v)
                val ta = (texColor ushr 24) and 0xFF
                if (ta == 0) continue

                val nX = (a * nx0w + b * nx1w + c * nx2w) / invW.toFloat()
                val nY = (a * ny0w + b * ny1w + c * ny2w) / invW.toFloat()
                val nZ = (a * nz0w + b * nz1w + c * nz2w) / invW.toFloat()
                val nLen = sqrt((nX * nX + nY * nY + nZ * nZ).toDouble()).let { if (it < 1e-9) 1.0 else it }
                val ndx = nX / nLen; val ndy = nY / nLen; val ndz = nZ / nLen

                val diffDot = (ndx * light.direction.x + ndy * light.direction.y + ndz * light.direction.z).coerceIn(0.0, 1.0)
                val specDot = (ndx * halfVec.x + ndy * halfVec.y + ndz * halfVec.z).coerceIn(0.0, 1.0)
                val spec = specDot.pow(light.shininess) * light.specular
                val fillDot = (ndx * light.fillDirection.x + ndy * light.fillDirection.y + ndz * light.fillDirection.z).coerceIn(0.0, 1.0)
                // Fresnel/rim: 1 - |N·V|, así se enciende cuando la
                // normal queda casi de canto respecto de la cámara
                // (silueta y crestas del bisel), no en las caras
                // planas de frente — ese contraste puntual es lo que
                // lee como "plástico/metal premium" en vez de mate liso.
                val facing = abs(ndz).coerceIn(0.0, 1.0)
                val rim = (1.0 - facing).pow(light.rimPower) * light.rimStrength
                val lightAmt = light.ambient + light.diffuse * diffDot + light.fillDiffuse * fillDot
                val highlight = spec + rim

                val tr = (texColor ushr 16) and 0xFF
                val tg = (texColor ushr 8) and 0xFF
                val tb = texColor and 0xFF

                val outA = (ta * globalAlpha255) / 255
                val outR = (tr * lightAmt + 255.0 * highlight).coerceIn(0.0, 255.0).toInt()
                val outG = (tg * lightAmt + 255.0 * highlight).coerceIn(0.0, 255.0).toInt()
                val outB = (tb * lightAmt + 255.0 * highlight).coerceIn(0.0, 255.0).toInt()

                depthBuf[i] = depthMetric
                pixels[i] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
    }

    private fun sampleBilinear(texPixels: IntArray, texW: Int, texH: Int, u: Float, v: Float): Int {
        val fx = (u.coerceIn(0f, 1f) * (texW - 1))
        val fy = (v.coerceIn(0f, 1f) * (texH - 1))
        val x0 = fx.toInt().coerceIn(0, texW - 1)
        val y0 = fy.toInt().coerceIn(0, texH - 1)
        val x1 = (x0 + 1).coerceAtMost(texW - 1)
        val y1 = (y0 + 1).coerceAtMost(texH - 1)
        val tx = fx - x0
        val ty = fy - y0

        val c00 = texPixels[y0 * texW + x0]
        val c10 = texPixels[y0 * texW + x1]
        val c01 = texPixels[y1 * texW + x0]
        val c11 = texPixels[y1 * texW + x1]

        fun lerpChan(c0: Int, c1: Int, t: Float, shift: Int): Float {
            val v0 = (c0 ushr shift) and 0xFF
            val v1 = (c1 ushr shift) and 0xFF
            return v0 + (v1 - v0) * t
        }

        val aTop = lerpChan(c00, c10, tx, 24); val aBot = lerpChan(c01, c11, tx, 24)
        val rTop = lerpChan(c00, c10, tx, 16); val rBot = lerpChan(c01, c11, tx, 16)
        val gTop = lerpChan(c00, c10, tx, 8); val gBot = lerpChan(c01, c11, tx, 8)
        val bTop = lerpChan(c00, c10, tx, 0); val bBot = lerpChan(c01, c11, tx, 0)

        val a = (aTop + (aBot - aTop) * ty).toInt().coerceIn(0, 255)
        val r = (rTop + (rBot - rTop) * ty).toInt().coerceIn(0, 255)
        val g = (gTop + (gBot - gTop) * ty).toInt().coerceIn(0, 255)
        val bl = (bTop + (bBot - bTop) * ty).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
