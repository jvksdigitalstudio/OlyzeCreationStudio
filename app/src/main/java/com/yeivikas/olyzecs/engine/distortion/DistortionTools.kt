package com.yeivikas.olyzecs.engine.distortion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Curva de caída del pincel — la misma para las 9 herramientas y para
 * [DistortionFreezeMask], así que "dureza del borde" se comporta
 * igual sin importar qué se esté pintando.
 *
 * [t] es la distancia normalizada al centro del pincel (0 = centro,
 * 1 = borde exterior, valores fuera de [0,1] no deberían llegar acá —
 * los que llaman ya filtran `dist >= radius`). [hardness] es la "dureza
 * del borde" tal como la ve el usuario (0..1): hasta `hardness` el
 * pincel pega a fuerza plena (1.0); de ahí al borde cae con una curva
 * "smootherstep" (suave en ambos extremos, sin el quiebre visible de
 * una rampa lineal) hasta 0 justo en el borde — así un pincel con
 * dureza 1 da un círculo de borde nítido y uno con dureza 0 da un
 * degradado completo desde el centro, sin escalones perceptibles ni en
 * zoom.
 */
fun distortionFalloff(t: Float, hardness: Float): Float {
    if (t >= 1f) return 0f
    if (t <= 0f) return 1f
    val h = hardness.coerceIn(0f, 1f)
    if (t <= h) return 1f
    val span = (1f - h).coerceAtLeast(1e-4f)
    val edge = ((t - h) / span).coerceIn(0f, 1f)
    // smootherstep (Ken Perlin): 6t^5 - 15t^4 + 10t^3, invertida (1 en
    // edge=0, 0 en edge=1) para que sea una CAÍDA, no una subida.
    val s = edge
    val eased = s * s * s * (s * (s * 6f - 15f) + 10f)
    return 1f - eased
}

/**
 * Punto de la GRILLA usado para cálculos que necesitan la posición fija
 * del vértice ANTES de cualquier deformación (p. ej. [DistortionToolType.RECONSTRUCT]
 * y [DistortionToolType.MIRROR], que clonan/restauran contra la imagen
 * original, no contra el estado actual del vértice).
 */
internal data class DistortionUv(val u: Float, val v: Float)

/**
 * Calcula, para UN vértice de la malla ya identificado como afectado por
 * el pincel (ver [DistortionField.applyStroke], que filtra por radio
 * antes de llamar acá), el punto de origen (UV, espacio de la imagen
 * ORIGINAL) hacia el que ese vértice debería tender esta muestra —
 * [DistortionField.applyStroke] mezcla el valor actual del vértice hacia
 * este resultado según el peso de caída del pincel, nunca lo reemplaza
 * de una: por eso cada fórmula de acá describe un DESTINO, no un salto
 * directo, y sostener el pincel quieto sigue empujando el efecto más
 * lejos (igual que Liquify de Photoshop) en vez de quedarse pegado tras
 * la primera muestra.
 *
 * Todas las distancias/vectores de entrada ([du], [dv], [dist],
 * [dragDu], [dragDv]) ya vienen en espacio "corregido por aspecto"
 * (1 unidad = el mismo tamaño real en X que en Y, sin importar que la
 * imagen no sea cuadrada) — así el pincel es un círculo real y las
 * rotaciones/reflejos no se ven elípticos. Para volver a UV crudo antes
 * de devolver, la componente V se multiplica de nuevo por [aspect]
 * (ancho/alto de la imagen).
 */
internal fun distortionTargetUv(
    tool: DistortionToolType,
    oldU: Float,
    oldV: Float,
    gridU: Float,
    gridV: Float,
    centerU: Float,
    centerV: Float,
    du: Float,
    dv: Float,
    dist: Float,
    t: Float,
    dragDu: Float,
    dragDv: Float,
    /**
     * Eje de reflejo de [DistortionToolType.MIRROR], ya normalizado
     * (largo 1) y fijado una sola vez por trazo por
     * [DistortionField.applyStroke] — `null` si el trazo actual todavía
     * no tiene arrastre suficiente para definir un eje. A diferencia de
     * [dragDu]/[dragDv] (que sí cambian muestra a muestra y las siguen
     * usando el resto de las herramientas direccionales), este par se
     * mantiene CONSTANTE durante todo el trazo de Espejo — ver el
     * comentario grande en `applyStroke` para el porqué.
     */
    mirrorAxisU: Float?,
    mirrorAxisV: Float?,
    radius: Float,
    aspect: Float,
    brush: DistortionBrush
): Pair<Float, Float> {
    return when (tool) {
        DistortionToolType.WARP -> {
            // Mapeo "hacia atrás": si el contenido se arrastra en la
            // dirección del dedo, el punto que hay que muestrear de la
            // imagen original se corre en la dirección CONTRARIA.
            oldU - dragDu to oldV - dragDv * aspect
        }

        DistortionToolType.SPHERE, DistortionToolType.BULGE_PINCH -> {
            // Radio actual del vértice respecto del CENTRO DEL PINCEL,
            // en el punto donde ya está (oldU/oldV), no en su posición
            // de grilla — así los pases sucesivos de la sesión se
            // acumulan sobre el resultado anterior en vez de partir
            // siempre de la imagen intacta.
            val ou = oldU - centerU
            val ov = (oldV - centerV) / aspect
            val od = hypot(ou, ov)
            if (od < 1e-6f) {
                oldU to oldV
            } else {
                val profile = if (tool == DistortionToolType.SPHERE) {
                    // Perfil de "domo" esférico — más redondeado, la
                    // firma visual de "burbuja" que pide Esferizar.
                    sqrt((1f - t * t).coerceAtLeast(0f))
                } else {
                    // Perfil lineal — protuberancia/pellizco más
                    // "directo", sin la curvatura de la esfera.
                    1f - t
                }
                val sign = if (brush.bulgeOutward) 1f else -1f
                // Fracción del radio actual que se comprime/expande
                // esta muestra — acotado bien por debajo de 1 para que
                // sostener el pincel quieto no pueda invertir el punto
                // sobre el centro (0.6 es el techo con el que el efecto
                // sigue siendo controlable pase a pase).
                val k = (sign * profile * 0.6f).coerceIn(-0.9f, 0.9f)
                val newOd = (od * (1f - k)).coerceAtLeast(0f)
                val nu = centerU + (ou / od) * newOd
                val nv = centerV + (ov / od) * newOd * aspect
                nu to nv
            }
        }

        DistortionToolType.TWIRL -> {
            val ou = oldU - centerU
            val ov = (oldV - centerV) / aspect
            // Perfil centrado: el remolino pega más fuerte cerca del
            // centro del pincel y se apaga hacia el borde — mismo
            // criterio que cualquier filtro de remolino real, si no el
            // borde del círculo quedaría con un quiebre visible.
            val profile = (1f - t * t).coerceAtLeast(0f)
            val direction = if (brush.twirlClockwise) -1f else 1f
            // ~10° máximo por muestra: sostener el pincel quieto sigue
            // girando el contenido en pasadas sucesivas, tal como se
            // espera de esta herramienta.
            val angle = direction * profile * 0.17f
            val cosA = cos(angle.toDouble()).toFloat()
            val sinA = sin(angle.toDouble()).toFloat()
            val nu = ou * cosA - ov * sinA
            val nv = ou * sinA + ov * cosA
            (centerU + nu) to (centerV + nv * aspect)
        }

        DistortionToolType.CIRCLE_SPLASH -> {
            val ou = oldU - centerU
            val ov = (oldV - centerV) / aspect
            val od = hypot(ou, ov)
            if (od < 1e-6f) {
                oldU to oldV
            } else {
                // Envolvente que se apaga hacia el borde del pincel
                // (para que la onda no corte de golpe en el límite del
                // círculo) modulada por una onda seno en función de la
                // distancia — varios anillos concéntricos dentro del
                // mismo pincel, como ondas de agua reales.
                val envelope = (1f - t)
                val waveCycles = 3.2f
                val ripple = sin((t * waveCycles * 2f * PI).toFloat())
                val amount = envelope * ripple * radius * 0.22f
                val newOd = (od + amount).coerceAtLeast(0f)
                val nu = centerU + (ou / od) * newOd
                val nv = centerV + (ov / od) * newOd * aspect
                nu to nv
            }
        }

        DistortionToolType.STRETCH_ANCHOR -> {
            val anchorU = brush.anchorU
            val anchorV = brush.anchorV
            if (anchorU == null || anchorV == null) {
                oldU to oldV
            } else {
                val profile = (1f - t)
                val pull = profile * 0.5f
                (oldU + (anchorU - oldU) * pull) to (oldV + (anchorV - oldV) * pull)
            }
        }

        DistortionToolType.STRETCH_AXIS -> {
            val sign = if (brush.stretchOutward) 1f else -1f
            val profile = (1f - t)
            val k = (sign * profile * 0.5f).coerceIn(-0.9f, 0.9f)
            when (brush.stretchAxis) {
                StretchAxis.HORIZONTAL -> {
                    val ou = oldU - centerU
                    val nu = centerU + ou * (1f - k)
                    nu to oldV
                }
                StretchAxis.VERTICAL -> {
                    val ov = (oldV - centerV) / aspect
                    val nv = centerV + ov * (1f - k) * aspect
                    oldU to nv
                }
            }
        }

        DistortionToolType.MIRROR -> {
            val ax = mirrorAxisU
            val ay = mirrorAxisV
            if (ax == null || ay == null) {
                // Todavía sin eje fijado (toque quieto, o primera
                // muestra del trazo sin arrastre previo) — no hay
                // dirección que defina el espejo, igual que el criterio
                // real de esta herramienta (necesita saber hacia dónde
                // se está "peinando" el reflejo). Una vez fijado por
                // [DistortionField.applyStroke], se mantiene constante
                // el resto del trazo en vez de recalcularse acá.
                oldU to oldV
            } else {
                // Se refleja la posición de GRILLA (identidad), no la
                // actual: "Espejo" clona directo de la imagen ORIGINAL,
                // no encadena reflejos de reflejos.
                val gu = gridU - centerU
                val gv = (gridV - centerV) / aspect
                val proj = gu * ax + gv * ay
                val perp = -gu * ay + gv * ax
                val ru = proj * ax + perp * ay
                val rv = proj * ay - perp * ax
                (centerU + ru) to (centerV + rv * aspect)
            }
        }

        DistortionToolType.RECONSTRUCT -> gridU to gridV
    }
}
