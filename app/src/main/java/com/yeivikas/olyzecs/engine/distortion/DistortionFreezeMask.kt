package com.yeivikas.olyzecs.engine.distortion

import kotlin.math.hypot

/**
 * Máscara de "congelar zona" del modo "Distorsión" — pintás una parte de
 * la imagen que ninguna herramienta debe tocar (p. ej. congelar la cara
 * y solo trabajar el cuerpo). Grilla propia, más chica que
 * [DistortionField] (no hace falta tanta resolución para una máscara de
 * pintar/no pintar), e independiente de ella.
 *
 * Pura matemática, sin `Bitmap`: [DistortionField.applyStroke] la
 * consulta para atenuar el peso de CUALQUIER herramienta sobre la zona
 * protegida — así "congelar" funciona automáticamente con las 9
 * herramientas por igual, sin que cada una tenga que saber de máscaras.
 * El overlay visual semitransparente que el usuario ve mientras pinta la
 * máscara vive en `EditorScreen.kt` (bloque `distortionBridge.
 * freezeModeActive` del Box principal del canvas), que lee [sample] y
 * [gridResolution] para dibujarse — [sample] es de sobra rápido (un
 * único acceso a `FloatArray` por celda) como para llamarse decenas de
 * veces por segundo mientras se arrastra el dedo.
 */
class DistortionFreezeMask(private val resolution: Int = 96) {
    private val values = FloatArray(resolution * resolution)

    /** Densidad de la grilla interna — expuesto solo para que la UI pueda dibujar un overlay a la misma resolución sin adivinar el número. */
    fun gridResolution(): Int = resolution

    /**
     * Pinta (acumula, nunca reemplaza) un círculo de radio [radiusUv]
     * centrado en [u]/[v], con caída [distortionFalloff] según
     * [hardness] — mismo criterio de pincel que las 9 herramientas de
     * deformación, para que "congelar" se sienta consistente con el
     * resto del panel. [imageAspect] corrige el radio para que sea un
     * círculo real (ver [DistortionField.identity]).
     */
    fun paint(u: Float, v: Float, radiusUv: Float, hardness: Float, imageAspect: Float) {
        val aspect = imageAspect.takeIf { it.isFinite() && it > 0f } ?: 1f
        val radius = radiusUv.coerceAtLeast(1e-4f)
        val minI = ((u - radius) * resolution).toInt().coerceIn(0, resolution - 1)
        val maxI = ((u + radius) * resolution).toInt().coerceIn(0, resolution - 1)
        val minJ = ((v - radius * aspect) * resolution).toInt().coerceIn(0, resolution - 1)
        val maxJ = ((v + radius * aspect) * resolution).toInt().coerceIn(0, resolution - 1)
        if (minI > maxI || minJ > maxJ) return
        for (j in minJ..maxJ) {
            val cv = (j + 0.5f) / resolution
            for (i in minI..maxI) {
                val cu = (i + 0.5f) / resolution
                val du = cu - u
                val dv = (cv - v) / aspect
                val dist = hypot(du, dv)
                if (dist >= radius) continue
                val w = distortionFalloff(dist / radius, hardness)
                val idx = j * resolution + i
                values[idx] = (values[idx] + w).coerceIn(0f, 1f)
            }
        }
    }

    /** "Desprotege" (resta) — la contraparte del pincel de congelar, para corregir un trazo de más sin tener que limpiar toda la máscara. */
    fun erase(u: Float, v: Float, radiusUv: Float, hardness: Float, imageAspect: Float) {
        val aspect = imageAspect.takeIf { it.isFinite() && it > 0f } ?: 1f
        val radius = radiusUv.coerceAtLeast(1e-4f)
        val minI = ((u - radius) * resolution).toInt().coerceIn(0, resolution - 1)
        val maxI = ((u + radius) * resolution).toInt().coerceIn(0, resolution - 1)
        val minJ = ((v - radius * aspect) * resolution).toInt().coerceIn(0, resolution - 1)
        val maxJ = ((v + radius * aspect) * resolution).toInt().coerceIn(0, resolution - 1)
        if (minI > maxI || minJ > maxJ) return
        for (j in minJ..maxJ) {
            val cv = (j + 0.5f) / resolution
            for (i in minI..maxI) {
                val cu = (i + 0.5f) / resolution
                val du = cu - u
                val dv = (cv - v) / aspect
                val dist = hypot(du, dv)
                if (dist >= radius) continue
                val w = distortionFalloff(dist / radius, hardness)
                val idx = j * resolution + i
                values[idx] = (values[idx] - w).coerceIn(0f, 1f)
            }
        }
    }

    /** Cuánto está "congelado" el punto [u]/[v] (0 = libre, 1 = totalmente protegido) — muestra al vecino más cercano, de sobra para una máscara binaria de esta densidad. */
    fun sample(u: Float, v: Float): Float {
        val fi = (u.coerceIn(0f, 1f) * resolution).toInt().coerceIn(0, resolution - 1)
        val fj = (v.coerceIn(0f, 1f) * resolution).toInt().coerceIn(0, resolution - 1)
        return values[fj * resolution + fi]
    }

    fun clear() { values.fill(0f) }

    fun isEmpty(): Boolean {
        for (value in values) if (value > 0f) return false
        return true
    }

    /** Copia profunda — deshacer/rehacer de la máscara sigue el mismo mecanismo de snapshot que [DistortionField.snapshot]. */
    fun copy(): DistortionFreezeMask {
        val clone = DistortionFreezeMask(resolution)
        values.copyInto(clone.values)
        return clone
    }
}
