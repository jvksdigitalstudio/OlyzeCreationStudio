package com.yeivikas.olyzecs.engine.mesh3d

import kotlin.math.sqrt

/**
 * Vector 3D mínimo, sin dependencias de Android ni de ningún otro
 * sistema del proyecto — es la unidad base de todo `engine.mesh3d`.
 * Deliberadamente separado de cualquier otro motor (GL, timeline,
 * etc.) para que este paquete se pueda mover, probar o reemplazar
 * sin arrastrar nada más.
 */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)

    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun length(): Double = sqrt(this dot this)

    fun normalized(): Vec3 {
        val len = length()
        return if (len < 1e-9) this else Vec3(x / len, y / len, z / len)
    }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
    }
}
