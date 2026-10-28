package com.yeivikas.olyzecs.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Guías inteligentes de alineación — mismo estándar que cualquier app de
 * diseño/edición profesional (Figma, Photoshop, Sketch, CapCut, Canva):
 * mientras se arrastra o redimensiona una capa (CUALQUIER tipo — imagen,
 * video, forma 2D, o un modelo 3D ya proyectado a su AABB 2D en pantalla,
 * porque todo esto trabaja sobre el bounding box en pantalla, nunca sobre
 * el contenido), si algún punto de referencia de la capa (borde inicial,
 * centro, o borde final, por eje) pasa cerca del centro o los bordes del
 * LIENZO, la capa se "engancha" exacto a esa línea y aparece una guía
 * magenta cruzando el lienzo entero mientras dure el enganche.
 *
 * A propósito INDEPENDIENTE de la cuadrícula manual del usuario
 * (`gridEnabled`/`gridSnapEnabled` — ver [snapTranslateToGrid] en
 * EditorScreen.kt): esa es una herramienta de composición que el usuario
 * prende/apaga a mano; esto es un asistente automático que SIEMPRE está
 * activo durante el gesto, como en cualquier editor serio — ambos sistemas
 * pueden convivir sin pisarse porque cada uno decide su propio
 * translateX/Y de forma independiente y el `pointerInput` aplica el de
 * cuadrícula DESPUÉS (si está prendida), sobre el resultado de este.
 */

/** Grosor visual (en dp, ver el `strokeWidthPx` que le pasa el caller) y color de la línea guía. */
val ALIGNMENT_GUIDE_COLOR = Color(0xFFFF3DAE)

/**
 * Umbral (en dp) dentro del cual un punto de referencia de la capa
 * "engancha" con el centro/borde del lienzo — mismo orden de magnitud que
 * el umbral de la cuadrícula manual (14dp, ver `snapThresholdPx` en
 * [snapTranslateToGrid]) para que ambos sistemas se sientan igual de
 * "imantados" al dedo.
 */
const val ALIGNMENT_SNAP_THRESHOLD_DP = 10f

/**
 * Resultado de evaluar el snap de alineación para un instante del gesto:
 * las coordenadas ya ajustadas (idénticas a las de entrada si ningún eje
 * enganchó) más, por separado, la posición en px de la línea guía a
 * dibujar en cada eje — null significa "ese eje no está enganchado ahora,
 * no dibujar nada ahí".
 */
data class AlignmentSnapResult(
    val translateX: Float,
    val translateY: Float,
    val guideLineXPx: Float?,
    val guideLineYPx: Float?
)

/**
 * Snap de POSICIÓN (arrastrar/pan) al centro y a los bordes del lienzo.
 * Reutiliza la misma matemática que [snapTranslateToGrid] (candidatos =
 * borde inicial/centro/borde final del AABB de la capa en px, comparados
 * contra un set de líneas objetivo por eje) pero con las líneas FIJAS del
 * lienzo (centro + ambos bordes) en vez de las líneas de una cuadrícula
 * configurable, y SIEMPRE evaluado — sin gate de ningún switch.
 */
fun computeAlignmentSnapForDrag(
    translateX: Float,
    translateY: Float,
    parallaxFactor: Float,
    halfWidthPx: Float,
    halfHeightPx: Float,
    boxWidthPx: Float,
    boxHeightPx: Float,
    snapThresholdPx: Float
): AlignmentSnapResult {
    if (parallaxFactor == 0f || boxWidthPx <= 0f || boxHeightPx <= 0f) {
        return AlignmentSnapResult(translateX, translateY, null, null)
    }
    val verticalLines = listOf(0f, boxWidthPx / 2f, boxWidthPx)
    val horizontalLines = listOf(0f, boxHeightPx / 2f, boxHeightPx)

    fun bestMatch(candidates: List<Float>, lines: List<Float>): Pair<Float, Float>? {
        var bestDiff: Float? = null
        var bestLine: Float? = null
        for (candidate in candidates) {
            val nearestLine = lines.minByOrNull { kotlin.math.abs(it - candidate) } ?: continue
            val diff = nearestLine - candidate
            if (kotlin.math.abs(diff) <= snapThresholdPx &&
                (bestDiff == null || kotlin.math.abs(diff) < kotlin.math.abs(bestDiff))
            ) {
                bestDiff = diff
                bestLine = nearestLine
            }
        }
        return if (bestDiff != null && bestLine != null) bestDiff to bestLine else null
    }

    var snappedX = translateX
    var guideXPx: Float? = null
    val centerPxX = (translateX * parallaxFactor + 1f) / 2f * boxWidthPx
    val candidatesX = listOf(centerPxX - halfWidthPx, centerPxX, centerPxX + halfWidthPx)
    bestMatch(candidatesX, verticalLines)?.let { (shift, line) ->
        val snappedNdcX = (centerPxX + shift) / boxWidthPx * 2f - 1f
        snappedX = (snappedNdcX / parallaxFactor).coerceIn(-2f, 2f)
        guideXPx = line
    }

    var snappedY = translateY
    var guideYPx: Float? = null
    val centerPxY = (1f - translateY * parallaxFactor) / 2f * boxHeightPx
    val candidatesY = listOf(centerPxY - halfHeightPx, centerPxY, centerPxY + halfHeightPx)
    bestMatch(candidatesY, horizontalLines)?.let { (shift, line) ->
        val snappedNdcY = 1f - 2f * (centerPxY + shift) / boxHeightPx
        snappedY = (snappedNdcY / parallaxFactor).coerceIn(-2f, 2f)
        guideYPx = line
    }

    return AlignmentSnapResult(snappedX, snappedY, guideXPx, guideYPx)
}

/**
 * Snap de TAMAÑO (redimensionar con las manijas de ancho/alto/uniforme):
 * a diferencia del arrastre, el centro de la capa NO se mueve al
 * redimensionar (las 3 manijas de resize escalan alrededor de `centerPx`,
 * ver los 3 `while(true)` de ROTATE/RESIZE_* en EditorScreen.kt) — lo que
 * se engancha acá es el BORDE que el usuario está estirando: si ese borde
 * pasa cerca del centro o de un borde del lienzo, el semi-ancho/alto
 * (y por lo tanto `scale`/`scaleX`/`scaleY`) se ajusta para que el borde
 * quede exacto ahí.
 *
 * `rawHalfExtentPx` es el semi-ancho (eje X) o semi-alto (eje Y) SIN
 * enganchar, ya con el signo real (negativo = la capa está volteada más
 * allá del centro — ver el comentario de RESIZE_WIDTH/HEIGHT sobre el
 * flip). `centerOnAxisPx` es `centerPx.x` o `centerPx.y`. `axisSizePx` es
 * `boxWidthPx` o `boxHeightPx`. Devuelve el semi-extent ajustado (mismo
 * signo que el de entrada) y la línea guía a dibujar, si enganchó.
 */
fun computeAlignmentSnapForResize(
    rawHalfExtentPx: Float,
    centerOnAxisPx: Float,
    axisSizePx: Float,
    snapThresholdPx: Float
): Pair<Float, Float?> {
    if (axisSizePx <= 0f) return rawHalfExtentPx to null
    val targetLines = listOf(0f, axisSizePx / 2f, axisSizePx)
    val sign = if (rawHalfExtentPx >= 0f) 1f else -1f
    val edgePx = centerOnAxisPx + rawHalfExtentPx
    val nearestLine = targetLines.minByOrNull { kotlin.math.abs(it - edgePx) } ?: return rawHalfExtentPx to null
    val diff = kotlin.math.abs(nearestLine - edgePx)
    return if (diff <= snapThresholdPx) {
        val snappedHalfExtent = nearestLine - centerOnAxisPx
        // Nunca cruzar el centro por el snap en sí (eso sería un flip no
        // pedido por el usuario) — se conserva el signo original.
        (if (sign >= 0f) kotlin.math.abs(snappedHalfExtent) else -kotlin.math.abs(snappedHalfExtent)) to nearestLine
    } else {
        rawHalfExtentPx to null
    }
}

/**
 * Snap de ÁNGULO al rotar con la manija dedicada: estándar de Figma/
 * Sketch/Illustrator — incrementos de 15° (0°, 15°, 30°… 345°), sin
 * necesidad de mantener ninguna tecla/modificador apretado (acá no hay
 * teclado físico, así que el snap va siempre activo, con un umbral chico
 * para no sentirse "pegajoso" en rotación libre). Devuelve el ángulo
 * ajustado (dentro de [-180, 180], mismo rango que ya usa `rotation` —
 * ver [normalizeRotationDeg]) y, si enganchó, el múltiplo de 15° al que
 * quedó pegado (para el HUD numérico).
 */
fun snapRotationToFifteenDegrees(rotationDeg: Float, snapThresholdDeg: Float = 4f): Pair<Float, Float?> {
    val nearestStep = (rotationDeg / 15f).let { if (it >= 0f) kotlin.math.floor(it + 0.5f) else kotlin.math.ceil(it - 0.5f) } * 15f
    val diff = kotlin.math.abs(nearestStep - rotationDeg)
    return if (diff <= snapThresholdDeg) nearestStep to nearestStep else rotationDeg to null
}

/**
 * Resuelve qué magnitud de escala (scaleX o scaleY, sin signo) hace que
 * el semi-extent del AABB en ESE eje caiga exacto en
 * [targetHalfExtentPx] — usado por las manijas RESIZE_WIDTH/RESIZE_HEIGHT
 * (ver [computeAlignmentSnapForResize] arriba, que da el semi-extent
 * OBJETIVO; esta función traduce ese objetivo de vuelta a una magnitud de
 * escala). No asume que el semi-extent sea directamente proporcional a la
 * magnitud — con rotación, [layerAabbHalfExtentsPx] mezcla ancho y alto
 * (`cosθ`/`sinθ`), así que la relación real es afín, no lineal por el
 * origen. Se resuelve la recta con dos muestras (`currentMag`/
 * `currentHalfExtentPx` y `probeMag`/`probeHalfExtentPx`, evaluadas por
 * el caller) en vez de dividir directo.
 */
fun solveScaleMagnitudeForTargetHalfExtent(
    currentMag: Float,
    currentHalfExtentPx: Float,
    probeMag: Float,
    probeHalfExtentPx: Float,
    targetHalfExtentPx: Float
): Float {
    val slope = (probeHalfExtentPx - currentHalfExtentPx) / (probeMag - currentMag)
    if (kotlin.math.abs(slope) < 1e-4f) return currentMag
    return currentMag + (targetHalfExtentPx - currentHalfExtentPx) / slope
}

/**
 * Dibuja, dentro del `DrawScope` del canvas de edición, la(s) línea(s)
 * guía activa(s) — cruzan el lienzo ENTERO en su eje (vertical para
 * `guideLineXPx`, horizontal para `guideLineYPx`), trazo punteado fino
 * para distinguirlas de cualquier trazo sólido de la capa/cuadrícula.
 * Se llama SIEMPRE (cada frame mientras el Box es visible); si ambos
 * parámetros son null no dibuja nada — el costo en el frame "quieto" es
 * cero.
 */
fun DrawScope.drawAlignmentGuideLines(
    guideLineXPx: Float?,
    guideLineYPx: Float?,
    strokeWidthPx: Float
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(strokeWidthPx * 3f, strokeWidthPx * 3f), 0f)
    guideLineXPx?.let { x ->
        drawLine(
            color = ALIGNMENT_GUIDE_COLOR,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokeWidthPx,
            pathEffect = dashEffect
        )
    }
    guideLineYPx?.let { y ->
        drawLine(
            color = ALIGNMENT_GUIDE_COLOR,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidthPx,
            pathEffect = dashEffect
        )
    }
}
