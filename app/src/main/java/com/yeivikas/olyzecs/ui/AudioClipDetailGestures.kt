package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/**
 * Detector de gestos de la ventana ampliada del audio (selección, desplazamiento, pellizco y su
 * inercia). Reemplaza a `detectTransformGestures`, que no da la velocidad de
 * salida (sin ella no hay inercia) ni distingue pellizco horizontal de vertical.
 *
 * [onSelectEnd] se llama UNA vez al soltar un gesto que llegó a seleccionar (aunque después pasara a pellizco).
 *
 * Un gesto es de UNA de dos clases, decidida por cuántos dedos intervienen (referencia: WavePad, donde arrastrar
 * sobre la onda selecciona y el zoom es con pellizco):
 *
 *  - **Un solo dedo desde el principio → SELECCIÓN:** al pasar el umbral de arrastre se llama a [onSelectStart] con
 *    la X donde se APOYÓ el dedo (no donde cruzó el umbral) y, con cada movimiento, a [onSelectMove] con la X actual.
 *    Un dedo solo no desplaza la vista (para eso están dos dedos o la barra de vista general).
 *  - **Dos o más dedos en algún momento → TRANSFORMACIÓN** (hasta soltar todos): [onTransform] con `panX` (dos dedos
 *    en paralelo desplazan la vista) y los zooms:
 *     - **Pellizco horizontal** (dedos más separados en X que en Y): `zoomX` ≠ 1 (zoom de tiempo).
 *     - **Pellizco vertical:** `zoomY` ≠ 1 (zoom de amplitud). La orientación se fija al
 *       apoyar el segundo dedo y no cambia hasta soltar, para que el gesto no "salte" de eje.
 *     - **Inercia:** si tras un pellizco/desplazamiento queda un dedo y se suelta, [onEnd] recibe su velocidad (px/s).
 *  Si llega un segundo dedo en pleno arrastre de selección, la selección queda como estaba y el gesto pasa a ser de
 *  transformación (el dedo que queda no vuelve a seleccionar).
 *
 * Los movimientos que pasan el umbral de arrastre se consumen, para que el detector de
 * toques (tocar / doble toque) no los confunda con un toque.
 *
 * @param onStart se llama al apoyar el primer dedo (sirve para frenar una inercia en curso).
 */
internal suspend fun PointerInputScope.detectDetailGestures(
    onStart: () -> Unit,
    onSelectStart: (downX: Float) -> Unit,
    onSelectMove: (x: Float) -> Unit,
    onSelectEnd: () -> Unit,
    onTransform: (centroidX: Float, panX: Float, zoomX: Float, zoomY: Float) -> Unit,
    onEnd: (velocityX: Float) -> Unit
) {
    awaitEachGesture {
        val slop = viewConfiguration.touchSlop
        var pastSlop = false
        var moved = 0f
        var horizontalPinch: Boolean? = null
        var lastCount = 1
        var multiTouch = false
        var selecting = false
        val tracker = VelocityTracker()

        val down = awaitFirstDown(requireUnconsumed = false)
        val downX = down.position.x
        onStart()

        do {
            val event = awaitPointerEvent()
            if (event.changes.none { it.isConsumed }) {
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isNotEmpty()) {
                    if (pressed.size != lastCount) {
                        tracker.resetTracking()
                        lastCount = pressed.size
                    }
                    val n = pressed.size
                    if (n >= 2) multiTouch = true
                    var cx = 0f
                    var cy = 0f
                    var pcx = 0f
                    var pcy = 0f
                    var minX = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    var pMinX = Float.MAX_VALUE
                    var pMaxX = -Float.MAX_VALUE
                    var pMinY = Float.MAX_VALUE
                    var pMaxY = -Float.MAX_VALUE
                    for (c in pressed) {
                        val x = c.position.x
                        val y = c.position.y
                        val px = c.previousPosition.x
                        val py = c.previousPosition.y
                        cx += x
                        cy += y
                        pcx += px
                        pcy += py
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                        if (px < pMinX) pMinX = px
                        if (px > pMaxX) pMaxX = px
                        if (py < pMinY) pMinY = py
                        if (py > pMaxY) pMaxY = py
                    }
                    cx /= n
                    cy /= n
                    pcx /= n
                    pcy /= n
                    val panX = cx - pcx

                    var zoomX = 1f
                    var zoomY = 1f
                    var spanMotion = 0f
                    if (n >= 2) {
                        if (horizontalPinch == null) {
                            horizontalPinch = detailPinchIsHorizontal(pMaxX - pMinX, pMaxY - pMinY)
                        }
                        if (horizontalPinch == true) {
                            zoomX = detailPinchRatio(pMaxX - pMinX, maxX - minX)
                            spanMotion = abs((maxX - minX) - (pMaxX - pMinX))
                        } else {
                            zoomY = detailPinchRatio(pMaxY - pMinY, maxY - minY)
                            spanMotion = abs((maxY - minY) - (pMaxY - pMinY))
                        }
                    }

                    if (!pastSlop) {
                        moved += abs(panX) + abs(cy - pcy) + spanMotion
                        if (moved > slop) pastSlop = true
                    }
                    if (pastSlop) {
                        if (!multiTouch) {
                            // Un solo dedo desde el principio: selección anclada donde se apoyó el dedo.
                            if (!selecting) {
                                selecting = true
                                onSelectStart(downX)
                            }
                            onSelectMove(cx)
                        } else {
                            if (n == 1) tracker.addPosition(pressed[0].uptimeMillis, pressed[0].position)
                            if (panX != 0f || zoomX != 1f || zoomY != 1f) onTransform(cx, panX, zoomX, zoomY)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            }
        } while (event.changes.any { it.pressed })

        if (selecting) onSelectEnd()
        val velocityX = if (pastSlop && multiTouch && lastCount == 1) tracker.calculateVelocity().x else 0f
        onEnd(velocityX)
    }
}
