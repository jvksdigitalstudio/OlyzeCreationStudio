package com.yeivikas.olyzecs.engine.distortion

import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Malla de deformación (mesh warp) que respalda a las 9 herramientas de
 * "Distorsión" por igual — ver `ui/EditorScreen.kt`/`DistortionPanel`
 * para el consumidor real y `api/distortion/DistortionApi` para el
 * puente hacia EliNer.
 *
 * Representación: una grilla de (cols+1) x (rows+1) vértices sobre el
 * espacio UV [0,1]x[0,1] de la imagen. Cada vértice guarda, en vez de un
 * simple desplazamiento, la posición ABSOLUTA (también en UV) de la
 * imagen ORIGINAL de la que hay que tomar el color para ese punto de la
 * imagen distorsionada — un mapeo "hacia atrás" (backward mapping),
 * el mismo principio que usa cualquier motor de deformación de imagen
 * serio (evita huecos en el resultado, a diferencia de mapear "hacia
 * adelante" píxel por píxel). Al construirse con [identity], cada
 * vértice apunta a sí mismo — imagen intacta.
 *
 * Esta clase NO sabe nada de `Bitmap` ni de Android — [DistortionRasterizer]
 * es quien la usa para producir un bitmap real. Separación deliberada:
 * así toda la matemática de las 9 herramientas se puede probar con
 * JUnit puro (ver `DistortionFieldTest`), sin Robolectric ni
 * dispositivo — mismo criterio que ya usa el resto de `engine.*` para
 * su lógica de cálculo (p. ej. `SpeedRampEngine`).
 */
class DistortionField private constructor(
    val cols: Int,
    val rows: Int,
    private val srcU: FloatArray,
    private val srcV: FloatArray
) {
    // Eje de reflejo de "Espejo" (MIRROR), fijado UNA SOLA VEZ por trazo
    // — ver comentario grande en [applyStroke]. `null` significa "sin
    // trazo de Espejo en curso todavía" (recién empezado, o la última
    // herramienta usada no fue Espejo).
    private var mirrorAxisU: Float? = null
    private var mirrorAxisV: Float? = null
    companion object {
        private const val MIN_GRID = 40
        private const val MAX_GRID = 160
        private const val TARGET_VERTICES = 130 * 130

        /**
         * Malla identidad (sin deformar) dimensionada para [imageAspect]
         * (ancho/alto de la imagen real): se reparte para que cada celda
         * sea aproximadamente cuadrada en píxeles reales de la imagen,
         * no en UV crudo — si no, una imagen muy panorámica tendría
         * celdas rectangulares y el pincel dejaría de comportarse como
         * un círculo real sobre la imagen.
         */
        fun identity(imageAspect: Float): DistortionField {
            val aspect = imageAspect.takeIf { it.isFinite() && it > 0f } ?: 1f
            val cols = sqrt(TARGET_VERTICES * aspect).roundToInt().coerceIn(MIN_GRID, MAX_GRID)
            val rows = (cols / aspect).roundToInt().coerceIn(MIN_GRID, MAX_GRID)
            val size = (cols + 1) * (rows + 1)
            val su = FloatArray(size)
            val sv = FloatArray(size)
            for (j in 0..rows) {
                val v = j.toFloat() / rows
                for (i in 0..cols) {
                    val idx = j * (cols + 1) + i
                    su[idx] = i.toFloat() / cols
                    sv[idx] = v
                }
            }
            return DistortionField(cols, rows, su, sv)
        }
    }

    /** Copia profunda — respalda "deshacer por trazo" (ver `DistortionPanel.undoStack`, que guarda una de estas antes de cada trazo nuevo). */
    fun snapshot(): DistortionField = DistortionField(cols, rows, srcU.copyOf(), srcV.copyOf())

    /** true si la malla sigue siendo la identidad (imagen intacta, sin ningún trazo aplicado todavía). */
    fun isIdentity(): Boolean {
        for (j in 0..rows) {
            val v = j.toFloat() / rows
            for (i in 0..cols) {
                val idx = j * (cols + 1) + i
                if (kotlin.math.abs(srcU[idx] - i.toFloat() / cols) > 1e-6f || kotlin.math.abs(srcV[idx] - v) > 1e-6f) {
                    return false
                }
            }
        }
        return true
    }

    fun vertexU(i: Int, j: Int) = srcU[j * (cols + 1) + i]
    fun vertexV(i: Int, j: Int) = srcV[j * (cols + 1) + i]

    /** UV de la grilla IDENTIDAD del vértice (i,j) — su posición antes de cualquier deformación, usada como referencia por varias herramientas (Reconstruir, Espejo). */
    fun gridU(i: Int) = i.toFloat() / cols
    fun gridV(j: Int) = j.toFloat() / rows

    /**
     * Aplica una única muestra de trazo (un punto del dedo mientras se
     * arrastra) sobre esta malla, centrada en [centerU]/[centerV] con
     * los parámetros de [brush]. [prevU]/[prevV] es la muestra ANTERIOR
     * del mismo trazo (o `null` si es la primera) — la usan las
     * herramientas direccionales (Cepillo, Espejo) para saber hacia
     * dónde arrastra el dedo. [imageAspect] (ancho/alto de la imagen)
     * corrige el radio del pincel para que sea un círculo real (ver
     * [identity]). [freezeMask], si no es `null`, protege del efecto a
     * los vértices marcados (ver [DistortionFreezeMask]) — se atenúa el
     * peso de la mezcla, nunca se salta el vértice del todo, así una
     * zona "medio congelada" se sigue viendo suave en el borde de la
     * máscara.
     */
    fun applyStroke(
        brush: DistortionBrush,
        centerU: Float,
        centerV: Float,
        prevU: Float?,
        prevV: Float?,
        imageAspect: Float,
        freezeMask: DistortionFreezeMask? = null
    ) {
        val aspect = imageAspect.takeIf { it.isFinite() && it > 0f } ?: 1f
        // Vector de arrastre en espacio corregido por aspecto — 0,0 si
        // es la primera muestra del trazo (sin punto anterior).
        val dragDu = prevU?.let { centerU - it } ?: 0f
        val dragDv = prevV?.let { (centerV - it) / aspect } ?: 0f

        // `prevU == null` es la misma señal que ya usa el resto del
        // motor para "primera muestra de un trazo nuevo" (ver doc de
        // este método) — se aprovecha acá para soltar cualquier eje de
        // Espejo que hubiera quedado fijado del trazo anterior.
        if (prevU == null) {
            mirrorAxisU = null
            mirrorAxisV = null
        }
        // Espejo (MIRROR): a diferencia del resto de las herramientas
        // direccionales, el eje de reflejo se fija UNA sola vez —en la
        // primera muestra del trazo en la que ya hay arrastre real (la
        // primera, con prevU/prevV null, todavía no tiene dirección)— y
        // se reutiliza sin cambios el resto del trazo. Antes se
        // recalculaba en CADA muestra a partir del micro-movimiento
        // instantáneo del dedo, que nunca es perfectamente derecho: el
        // eje "bailoteaba" trazo a trazo y el reflejo se veía caótico o,
        // con arrastres largos, como una compresión rara en vez de un
        // clon/reflejo legible. Mismo criterio de "fijar al primer
        // contacto" que ya usa Tramo con su punto de anclaje.
        if (brush.tool == DistortionToolType.MIRROR && mirrorAxisU == null) {
            val axisLen = hypot(dragDu, dragDv)
            if (axisLen >= 1e-4f) {
                mirrorAxisU = dragDu / axisLen
                mirrorAxisV = dragDv / axisLen
            }
        }

        val radius = brush.radiusUv.coerceAtLeast(1e-4f)
        // Caja de vértices posiblemente afectados — evita recorrer la
        // malla entera en cada muestra de un trazo largo.
        val minI = ((centerU - radius) * cols).toInt().coerceIn(0, cols)
        val maxI = ((centerU + radius) * cols).toInt().coerceIn(0, cols)
        val minJ = ((centerV - radius * aspect) * rows).toInt().coerceIn(0, rows)
        val maxJ = ((centerV + radius * aspect) * rows).toInt().coerceIn(0, rows)
        if (minI > maxI || minJ > maxJ) return

        for (j in minJ..maxJ) {
            val gv = gridV(j)
            for (i in minI..maxI) {
                val gu = gridU(i)
                val du = gu - centerU
                val dv = (gv - centerV) / aspect
                val dist = hypot(du, dv)
                if (dist >= radius) continue
                val t = dist / radius
                val brushWeight = distortionFalloff(t, brush.feather) * brush.intensity
                if (brushWeight <= 0f) continue

                val frozen = freezeMask?.sample(gu, gv) ?: 0f
                val effectiveWeight = brushWeight * (1f - frozen)
                if (effectiveWeight <= 0f) continue

                val idx = j * (cols + 1) + i
                val oldU = srcU[idx]
                val oldV = srcV[idx]
                val (targetU, targetV) = distortionTargetUv(
                    tool = brush.tool,
                    oldU = oldU, oldV = oldV,
                    gridU = gu, gridV = gv,
                    centerU = centerU, centerV = centerV,
                    du = du, dv = dv, dist = dist, t = t,
                    dragDu = dragDu, dragDv = dragDv,
                    mirrorAxisU = mirrorAxisU, mirrorAxisV = mirrorAxisV,
                    radius = radius, aspect = aspect, brush = brush
                )
                srcU[idx] = (oldU + (targetU - oldU) * effectiveWeight).coerceIn(-0.5f, 1.5f)
                srcV[idx] = (oldV + (targetV - oldV) * effectiveWeight).coerceIn(-0.5f, 1.5f)
            }
        }
    }

    /** Vuelve TODA la malla a la identidad — respalda el botón "Restablecer todo". */
    fun reset() {
        for (j in 0..rows) {
            val v = gridV(j)
            for (i in 0..cols) {
                val idx = j * (cols + 1) + i
                srcU[idx] = gridU(i)
                srcV[idx] = v
            }
        }
    }

    /**
     * Muestra bilineal de la malla en cualquier UV [0,1]x[0,1] (no solo
     * en vértices) — la usa [DistortionRasterizer] para cada píxel de
     * salida, así el resultado es suave incluso en zoom en vez de
     * "escalonar" por celda de grilla.
     */
    fun sampleBilinear(u: Float, v: Float): Pair<Float, Float> {
        val fu = u.coerceIn(0f, 1f) * cols
        val fv = v.coerceIn(0f, 1f) * rows
        val i0 = fu.toInt().coerceIn(0, cols - 1)
        val j0 = fv.toInt().coerceIn(0, rows - 1)
        val i1 = (i0 + 1).coerceAtMost(cols)
        val j1 = (j0 + 1).coerceAtMost(rows)
        val tx = (fu - i0).coerceIn(0f, 1f)
        val ty = (fv - j0).coerceIn(0f, 1f)

        val u00 = vertexU(i0, j0); val v00 = vertexV(i0, j0)
        val u10 = vertexU(i1, j0); val v10 = vertexV(i1, j0)
        val u01 = vertexU(i0, j1); val v01 = vertexV(i0, j1)
        val u11 = vertexU(i1, j1); val v11 = vertexV(i1, j1)

        val uTop = u00 + (u10 - u00) * tx
        val uBot = u01 + (u11 - u01) * tx
        val vTop = v00 + (v10 - v00) * tx
        val vBot = v01 + (v11 - v01) * tx

        return (uTop + (uBot - uTop) * ty) to (vTop + (vBot - vTop) * ty)
    }
}
