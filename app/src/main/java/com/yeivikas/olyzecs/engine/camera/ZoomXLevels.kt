package com.yeivikas.olyzecs.engine.camera

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Motor puro (sin Android, sin Compose) del módulo "Zoom X" — la rueda de
 * niveles de zoom que vive en Módulos > Cámara (ver [com.yeivikas.olyzecs.ui.ZoomXWheel]
 * para la parte visual y [com.yeivikas.olyzecs.ui.ZoomXFloatingWindow] para
 * la ventana flotante que la envuelve).
 *
 * DISEÑO — por qué "paradas" (stops) y no un slider continuo: un lente de
 * zoom real de cámara profesional no ofrece cualquier valor intermedio
 * arbitrario; ofrece marcas fijas (1×, 2×, 4×...) donde el anillo hace
 * "clic". Acá se replica exactamente ese comportamiento: la rueda solo
 * puede posarse en uno de los [levels], nunca en un valor arbitrario entre
 * dos paradas — eso es lo que la hace sentir "profesional" en vez de un
 * slider genérico disfrazado de rueda.
 *
 * EXTENSIBILIDAD — a pedido explícito del usuario ("que el motor sea
 * configurable para aumentarlo posteriormente"): la secuencia NO está
 * escrita a mano como una lista fija de 11 números. Se GENERA a partir de
 * un solo parámetro, [maxExponent] — la cantidad de duplicaciones desde
 * 1× (2^0) hasta el tope. Subir el rango máximo de zoom en el futuro (por
 * ejemplo, a 2048× o 4096×) es cambiar un solo número acá, no reescribir
 * la rueda ni ningún llamador.
 */
object ZoomXLevels {

    /** 2^10 = 1024× — el tope pedido para esta primera versión. */
    const val DEFAULT_MAX_EXPONENT = 10

    /**
     * La secuencia de paradas 1×, 2×, 4×, ..., 2^[maxExponent]×.
     * Ej.: maxExponent=3 -> [1, 2, 4, 8]. maxExponent=0 -> [1] (un único
     * nivel, sin zoom disponible — caso límite válido, no un error).
     */
    fun levels(maxExponent: Int = DEFAULT_MAX_EXPONENT): List<Float> {
        val clampedExponent = maxExponent.coerceAtLeast(0)
        return (0..clampedExponent).map { exponent -> twoToThe(exponent) }
    }

    /**
     * El índice (dentro de [levels]) de la parada más cercana a [value].
     * Como la escala de zoom es multiplicativa por naturaleza (pasar de
     * 1× a 2× es "el mismo salto perceptual" que pasar de 512× a 1024×),
     * la distancia se mide en espacio LOGARÍTMICO, no lineal — si se
     * midiera en espacio lineal, cualquier valor por encima de la mitad
     * del rango total (ej. 513×) redondearía casi siempre al último
     * escalón (1024×) sin importar qué tan cerca esté en verdad de 512×
     * en términos de "cuántos pasos de zoom" representa.
     */
    fun nearestIndex(value: Float, levels: List<Float> = levels()): Int {
        require(levels.isNotEmpty()) { "levels no puede estar vacío" }
        val safeValue = value.coerceAtLeast(MIN_LEVEL)
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        for (i in levels.indices) {
            val distance = abs(ln(safeValue.toDouble()) - ln(levels[i].toDouble())).toFloat()
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
            }
        }
        return bestIndex
    }

    /** Atajo: el VALOR (no el índice) de la parada más cercana a [value]. */
    fun nearestLevel(value: Float, levels: List<Float> = levels()): Float =
        levels[nearestIndex(value, levels)]

    /**
     * Progreso angular en [0, 1] de [level] dentro de la rueda — 0 en la
     * primera parada, 1 en la última. Pensado para que la parte visual
     * ([com.yeivikas.olyzecs.ui.ZoomXWheel]) ubique la marca/indicador sin
     * tener que conocer el espaciado interno de las paradas: siempre
     * equiespaciadas por ÍNDICE alrededor del arco, nunca por su valor
     * numérico (así 512×->1024× ocupa el mismo arco que 1×->2×, igual que
     * un anillo de lente real, donde las marcas de más zoom NO se
     * amontonan).
     */
    fun progressAt(index: Int, levelCount: Int): Float {
        if (levelCount <= 1) return 0f
        return index.toFloat() / (levelCount - 1).toFloat()
    }

    /**
     * Etiqueta a mostrar para un nivel, ej. "1×", "4×", "1024×" — sin
     * decimales (todas las paradas son enteras por construcción, potencias
     * de 2), formato consistente con el resto de indicadores "Nx" de la
     * app (ver el "3×" de la rueda de velocidad de referencia).
     */
    fun label(level: Float): String = "${level.roundToInt()}×"

    private const val MIN_LEVEL = 1f

    private fun twoToThe(exponent: Int): Float {
        var result = 1f
        repeat(exponent) { result *= 2f }
        return result
    }
}
