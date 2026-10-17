package com.yeivikas.olyzecs.ui

import kotlin.math.abs

/**
 * Matemática pura de la barra de vista general de la ventana ampliada del audio ([AudioClipDetailOverview]).
 * Sin Compose ni Android, para cubrirla con JUnit.
 *
 * Modelo (mismo que la vista general de WavePad: la ventana superior muestra el archivo SIEMPRE completo y la
 * ventana inferior su zoom): la barra reduce el eje de tiempo ENTERO a [viewportPx] píxeles, y un recuadro marca la
 * parte que muestra la onda grande. Con zoom `z` y desplazamiento `s` (ambos de la ventana principal) el recuadro
 * va de `s / z` a `s / z + viewportPx / z` — por eso su ancho es inversamente proporcional al zoom y siempre está
 * sincronizado con él.
 *
 * Todas las X están en el EJE de la barra: 0 = inicio del archivo, [viewportPx] = final (sin el margen lateral).
 */

/** Alto (dp) de la barra completa, divisor superior incluido. */
internal const val DETAIL_OVERVIEW_HEIGHT_DP = 64

/** Semiancho (dp) de la zona que agarra cada borde del recuadro para acercar/alejar. */
internal const val DETAIL_OVERVIEW_EDGE_ZONE_DP = 20

/** Ancho táctil mínimo (dp) del recuadro: con zoom muy alto el dibujado mide pocos píxeles pero se agarra igual. */
internal const val DETAIL_OVERVIEW_MIN_GRAB_DP = 44

/**
 * Posición de las dos agarraderas de arista, en X del eje de la barra (borde IZQUIERDO de cada una): [startLeftPx] es la
 * de la arista izquierda del recuadro y [endLeftPx] la de la derecha.
 */
internal data class DetailOverviewGripLefts(val startLeftPx: Float, val endLeftPx: Float)

/** Recuadro visible dentro de la barra: `[leftPx, rightPx]` en el eje de la barra. */
internal data class DetailOverviewWindow(val leftPx: Float, val rightPx: Float) {
    val widthPx: Float get() = rightPx - leftPx
    val centerPx: Float get() = (leftPx + rightPx) / 2f
}

/** Zoom y desplazamiento (px) de la ventana principal que resultan de un gesto sobre la barra. */
internal data class DetailOverviewView(val zoom: Float, val scrollPx: Float)

/** Qué parte del recuadro agarró el dedo. */
internal enum class DetailOverviewTarget { BODY, START_EDGE, END_EDGE }

/**
 * Agarre de un gesto: [target] y el desfase [offsetPx] entre el punto de la barra que se agarra (borde izquierdo si
 * es [DetailOverviewTarget.BODY], el borde correspondiente si es una arista) y el dedo, para que agarrar el recuadro
 * por un lado no lo haga saltar.
 */
internal data class DetailOverviewGrab(val target: DetailOverviewTarget, val offsetPx: Float)

/**
 * Recuadro de la parte visible para el zoom [zoom] y el desplazamiento [scrollPx] de la ventana principal.
 * `null` si la escala no es válida. A zoom 1× ocupa todo el eje; siempre queda dentro de `0 … viewportPx`.
 */
internal fun detailOverviewWindow(scrollPx: Float, viewportPx: Float, zoom: Float): DetailOverviewWindow? {
    if (viewportPx <= 0f || zoom <= 0f) return null
    val width = (viewportPx / zoom).coerceAtMost(viewportPx)
    val left = (scrollPx / zoom).coerceIn(0f, viewportPx - width)
    return DetailOverviewWindow(left, left + width)
}

/** X (eje de la barra) de un instante del archivo: el eje entero `0 … totalMs` mide [viewportPx]. */
internal fun detailOverviewX(sourceMs: Double, totalMs: Double, viewportPx: Float): Float {
    if (totalMs <= 0.0 || viewportPx <= 0f) return 0f
    return (sourceMs / totalMs * viewportPx).toFloat().coerceIn(0f, viewportPx)
}

/**
 * Posición de las agarraderas de arista del recuadro `[leftPx, rightPx]`: van FUERA del recuadro, pegadas a su marco
 * (la izquierda ocupa `leftPx - gripWidthPx … leftPx`; la derecha, `rightPx … rightPx + gripWidthPx`), como las asas de
 * recorte de un editor de audio: señalan hacia dónde se ensancha la ventana y no tapan la onda que enmarca el recuadro.
 *
 * Nunca se salen del lienzo: [gutterPx] (margen lateral de la barra) es lo máximo que pueden sobresalir del eje, así que
 * con el recuadro en el extremo del archivo la agarradera queda dentro del margen en vez de recortarse.
 */
internal fun detailOverviewGripLefts(
    leftPx: Float,
    rightPx: Float,
    gripWidthPx: Float,
    viewportPx: Float,
    gutterPx: Float
): DetailOverviewGripLefts {
    val minLeft = -gutterPx
    val maxLeft = (viewportPx + gutterPx - gripWidthPx).coerceAtLeast(minLeft)
    return DetailOverviewGripLefts(
        startLeftPx = (leftPx - gripWidthPx).coerceIn(minLeft, maxLeft),
        endLeftPx = rightPx.coerceIn(minLeft, maxLeft)
    )
}

/**
 * Qué agarra un dedo que baja en [downX]:
 *  - sobre un borde (±[edgeZonePx]) → esa arista, para acercar o alejar; solo si [canResize] y el recuadro es lo
 *    bastante ancho para que las dos zonas no se pisen y quede cuerpo entre ellas (`ancho ≥ 3 × edgeZonePx`);
 *  - dentro del recuadro, ampliado hasta [minGrabPx] si es más estrecho (con zoom alto) → el cuerpo, para desplazar;
 *  - fuera → el cuerpo con el recuadro CENTRADO bajo el dedo: tocar la barra salta a esa zona (y seguir arrastrando
 *    continúa desde ahí), como en la vista general de WavePad.
 */
internal fun detailOverviewGrab(
    downX: Float,
    window: DetailOverviewWindow,
    edgeZonePx: Float,
    minGrabPx: Float,
    canResize: Boolean
): DetailOverviewGrab {
    if (canResize && window.widthPx >= 3f * edgeZonePx) {
        if (abs(downX - window.leftPx) <= edgeZonePx) {
            return DetailOverviewGrab(DetailOverviewTarget.START_EDGE, window.leftPx - downX)
        }
        if (abs(downX - window.rightPx) <= edgeZonePx) {
            return DetailOverviewGrab(DetailOverviewTarget.END_EDGE, window.rightPx - downX)
        }
    }
    val halfHit = maxOf(window.widthPx, minGrabPx) / 2f
    return if (abs(downX - window.centerPx) <= halfHit) {
        DetailOverviewGrab(DetailOverviewTarget.BODY, window.leftPx - downX)
    } else {
        DetailOverviewGrab(DetailOverviewTarget.BODY, -window.widthPx / 2f)
    }
}

/**
 * Desplazamiento (px) de la ventana principal al llevar el recuadro agarrado a que el dedo esté en [x]: el recuadro
 * conserva su ancho (zoom constante) y no sale de la barra.
 */
internal fun detailOverviewMoveScrollPx(x: Float, grabOffsetPx: Float, viewportPx: Float, zoom: Float): Float {
    if (viewportPx <= 0f || zoom <= 0f) return 0f
    val width = (viewportPx / zoom).coerceAtMost(viewportPx)
    val left = (x + grabOffsetPx).coerceIn(0f, viewportPx - width)
    return clampDetailScrollPx(left * zoom, viewportPx, zoom)
}

/**
 * Zoom y desplazamiento al arrastrar una arista del recuadro ([window] = el recuadro al empezar el gesto) hasta que el
 * dedo esté en [x]. La arista OPUESTA queda fija (el instante que marcaba sigue marcándolo): arrastrar hacia dentro
 * acerca, hacia fuera aleja. El ancho mínimo del recuadro es el que corresponde a [maxZoom]; el máximo, la barra entera
 * (zoom 1×). [target] debe ser una arista, no el cuerpo (ese se desplaza con [detailOverviewMoveScrollPx]).
 */
internal fun detailOverviewResize(
    target: DetailOverviewTarget,
    x: Float,
    grabOffsetPx: Float,
    window: DetailOverviewWindow,
    viewportPx: Float,
    maxZoom: Float
): DetailOverviewView {
    if (viewportPx <= 0f) return DetailOverviewView(DETAIL_MIN_ZOOM, 0f)
    val limit = maxZoom.coerceAtLeast(DETAIL_MIN_ZOOM)
    val minWidth = viewportPx / limit
    val edge = x + grabOffsetPx
    val left: Float
    val right: Float
    when (target) {
        DetailOverviewTarget.START_EDGE -> {
            right = window.rightPx
            left = edge.coerceIn(0f, right - minWidth)
        }
        DetailOverviewTarget.END_EDGE -> {
            left = window.leftPx
            right = edge.coerceIn(left + minWidth, viewportPx)
        }
        DetailOverviewTarget.BODY -> throw IllegalArgumentException("El cuerpo se desplaza (detailOverviewMoveScrollPx), no se redimensiona")
    }
    val zoom = (viewportPx / (right - left)).coerceIn(DETAIL_MIN_ZOOM, limit)
    return DetailOverviewView(zoom, clampDetailScrollPx(left * zoom, viewportPx, zoom))
}
