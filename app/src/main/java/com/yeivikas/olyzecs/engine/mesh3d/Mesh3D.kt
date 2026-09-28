package com.yeivikas.olyzecs.engine.mesh3d

/**
 * Un vértice de malla: posición en espacio local del objeto (antes de
 * rotar/proyectar), su NORMAL (también en espacio local — el motor la
 * rota junto con la posición al rasterizar) y su coordenada UV
 * (0f..1f) sobre la textura.
 *
 * Guardar la normal por vértice (no una sola por cara) es lo que
 * permite sombreado Phong suave: [MeshRasterizer] la interpola
 * píxel a píxel dentro de cada triángulo, así que un bisel curvo se
 * ve como una curva de verdad y no como facetas pegadas.
 *
 * [boneIndices]/[boneWeights] quedan reservados sin uso todavía —
 * cuando se agregue un rig de huesos, el paso de "skinning" solo
 * necesita leer estos dos arrays por vértice y mover [position]/
 * [normal]; el resto del motor (rasterizador, cámara) no tiene que
 * cambiar.
 */
data class Vertex3D(
    val position: Vec3,
    val normal: Vec3,
    val u: Float,
    val v: Float,
    val boneIndices: IntArray = EMPTY_INT,
    val boneWeights: FloatArray = EMPTY_FLOAT
) {
    companion object {
        private val EMPTY_INT = IntArray(0)
        private val EMPTY_FLOAT = FloatArray(0)
    }
}

/** Un triángulo — el shading se resuelve por píxel en el rasterizador, no aquí. */
data class Triangle3D(val a: Vertex3D, val b: Vertex3D, val c: Vertex3D)

/** Una malla es, ni más ni menos, una lista de triángulos. */
class Mesh3D {
    val triangles = ArrayList<Triangle3D>()

    fun addQuad(v00: Vertex3D, v01: Vertex3D, v11: Vertex3D, v10: Vertex3D) {
        triangles.add(Triangle3D(v00, v01, v11))
        triangles.add(Triangle3D(v00, v11, v10))
    }
}
