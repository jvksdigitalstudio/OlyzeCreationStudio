package com.yeivikas.olyzecs.engine.mesh3d

import kotlin.math.cos
import kotlin.math.sin

/**
 * Rotación XYZ + proyección en perspectiva simple (cámara mirando
 * hacia -Z, distancia fija [camDist]). Autocontenida: no sabe nada de
 * bitmaps, Canvas ni de qué efecto la está usando.
 */
class Camera3D(rxDeg: Double, ryDeg: Double, rzDeg: Double, private val camDist: Double) {
    private val m: Array<DoubleArray>

    init {
        val rx = Math.toRadians(rxDeg)
        val ry = Math.toRadians(ryDeg)
        val rz = Math.toRadians(rzDeg)
        val cx = cos(rx); val sx = sin(rx)
        val cy = cos(ry); val sy = sin(ry)
        val cz = cos(rz); val sz = sin(rz)
        val rxM = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, cx, -sx),
            doubleArrayOf(0.0, sx, cx)
        )
        val ryM = arrayOf(
            doubleArrayOf(cy, 0.0, sy),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(-sy, 0.0, cy)
        )
        val rzM = arrayOf(
            doubleArrayOf(cz, -sz, 0.0),
            doubleArrayOf(sz, cz, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0)
        )
        m = matMul(matMul(rzM, ryM), rxM)
    }

    fun rotate(p: Vec3): Vec3 = Vec3(
        m[0][0] * p.x + m[0][1] * p.y + m[0][2] * p.z,
        m[1][0] * p.x + m[1][1] * p.y + m[1][2] * p.z,
        m[2][0] * p.x + m[2][1] * p.y + m[2][2] * p.z
    )

    /** camDist - z del punto ya rotado: "w" de la proyección en perspectiva (siempre > 0 en el rango de uso). */
    fun w(rotatedZ: Double): Double = camDist - rotatedZ

    /** Proyecta un punto YA rotado a coordenadas de pantalla (antes de sumar el origen del lienzo). */
    fun project(rotated: Vec3): FloatArray {
        val f = camDist / w(rotated.z)
        return floatArrayOf((rotated.x * f).toFloat(), (rotated.y * f).toFloat())
    }

    companion object {
        private fun matMul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
            val r = Array(3) { DoubleArray(3) }
            for (i in 0..2) for (j in 0..2) {
                var s = 0.0
                for (k in 0..2) s += a[i][k] * b[k][j]
                r[i][j] = s
            }
            return r
        }
    }
}
