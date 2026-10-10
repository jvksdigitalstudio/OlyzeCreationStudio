package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/**
 * Detector de gestos de la ventana ampliada del audio (arrastre, pellizco y su
 * inercia). Reemplaza a `detectTransformGestures`, que no da la velocidad de
 * salida (sin ella no hay inercia) ni distingue pellizco horizontal de vertical.
 *
 *  - **Arrastre con un dedo:** [onTransform] con `panX` (desplaza la vista).
 *  - **Pellizco horizontal** (dedos más separados en X que en Y): `zoomX` ≠ 1 (zoom de tiempo).
 *  - **Pellizco vertical:** `zoomY` ≠ 1 (zoom de amplitud). La orientación se fija al
 *    apoyar el segundo dedo y no cambia hasta soltar, para que el gesto no "salte" de eje.
 *  - **Inercia:** al soltar un arrastre de un dedo, [onEnd] recibe la velocidad (px/s) del
 *    dedo; con pellizco o sin movimiento recibe 0.
 *
 * Los movimientos que pasan el umbral de arrastre se consumen, para que el detector de
 * toques (tocar / doble toque) no los confunda con un toque.
 *
 * @param onStart se llama al apoyar el primer dedo (sirve para frenar una inercia en curso).
 */
internal suspend fun PointerInputScope.detectDetailGestures(
    onStart: () -> Unit,
    onTransform: (centroidX: Float, panX: Float, zoomX: Float, zoomY: Float) -> Unit,
    onEnd: (velocityX: Float) -> Unit
) {
    awaitEachGesture {
        val slop = viewConfiguration.touchSlop
        var pastSlop = false
        var moved = 0f
        var horizontalPinch: Boolean? = null
        var lastCount = 1
        val tracker = VelocityTracker()

        awaitFirstDown(requireUnconsumed = false)
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
                        if (n == 1) tracker.addPosition(pressed[0].uptimeMillis, pressed[0].position)
                        if (panX != 0f || zoomX != 1f || zoomY != 1f) onTransform(cx, panX, zoomX, zoomY)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            }
        } while (event.changes.any { it.pressed })

        val velocityX = if (pastSlop && lastCount == 1) tracker.calculateVelocity().x else 0f
        onEnd(velocityX)
    }
}
