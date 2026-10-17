package com.yeivikas.olyzecs.engine.camera

import com.yeivikas.olyzecs.engine.animation.Easing
import com.yeivikas.olyzecs.engine.animation.EasingType

/**
 * FASE 2 — extraído de [CameraTrack.frameAt] para que
 * `RenderLayerSnapshot.frameAt` (engine.render, ver RenderSnapshot.kt)
 * pueda reutilizar EXACTAMENTE la misma lógica de interpolación sobre una
 * lista de keyframes ya congelada, sin duplicar la fórmula en dos
 * archivos (y sin que ambas puedan divergir con el tiempo). Función pura:
 * sin estado propio, sin I/O, sin dependencia de Android — segura de
 * llamar desde cualquier hilo.
 */
internal object CameraFrameInterpolation {

    /**
     * Devuelve el encuadre interpolado en [timeMs] a partir de una lista
     * de [keyframes] YA INMUTABLE (el llamador es responsable de que no
     * cambie mientras dure esta llamada — [CameraTrack] y
     * [com.yeivikas.olyzecs.engine.render.RenderLayerSnapshot] lo
     * garantizan cada uno a su manera). Si no hay keyframes, devuelve
     * [baseFrame].
     */
    fun at(keyframes: List<Keyframe>, baseFrame: CameraFrame, timeMs: Long): CameraFrame {
        if (keyframes.isEmpty()) {
            return baseFrame
        }
        if (keyframes.size == 1) {
            val k = keyframes.first()
            return CameraFrame(k.translateX, k.translateY, k.scale, k.rotationDeg, k.alpha, k.tiltXDeg, k.tiltYDeg, k.focusBlur, k.dollyZoom, k.scaleX, k.scaleY)
        }

        val first = keyframes.first()
        val last = keyframes.last()
        if (timeMs <= first.timeMs) {
            return CameraFrame(first.translateX, first.translateY, first.scale, first.rotationDeg, first.alpha, first.tiltXDeg, first.tiltYDeg, first.focusBlur, first.dollyZoom, first.scaleX, first.scaleY)
        }
        if (timeMs >= last.timeMs) {
            return CameraFrame(last.translateX, last.translateY, last.scale, last.rotationDeg, last.alpha, last.tiltXDeg, last.tiltYDeg, last.focusBlur, last.dollyZoom, last.scaleX, last.scaleY)
        }

        // Encontrar el par de keyframes que rodea a timeMs
        var lower = first
        var upper = last
        for (i in 0 until keyframes.size - 1) {
            val a = keyframes[i]
            val b = keyframes[i + 1]
            if (timeMs >= a.timeMs && timeMs <= b.timeMs) {
                lower = a
                upper = b
                break
            }
        }

        val span = (upper.timeMs - lower.timeMs).coerceAtLeast(1)
        val rawT = (timeMs - lower.timeMs).toFloat() / span.toFloat()
        val t = if (upper.easing == EasingType.CUSTOM_BEZIER) {
            Easing.applyCubicBezier(rawT, upper.bezierX1, upper.bezierY1, upper.bezierX2, upper.bezierY2)
        } else {
            Easing.apply(upper.easing, rawT)
        }

        return CameraFrame(
            translateX = lerp(lower.translateX, upper.translateX, t),
            translateY = lerp(lower.translateY, upper.translateY, t),
            scale = lerp(lower.scale, upper.scale, t),
            rotationDeg = lerp(lower.rotationDeg, upper.rotationDeg, t),
            alpha = lerp(lower.alpha, upper.alpha, t),
            tiltXDeg = lerp(lower.tiltXDeg, upper.tiltXDeg, t),
            tiltYDeg = lerp(lower.tiltYDeg, upper.tiltYDeg, t),
            focusBlur = lerp(lower.focusBlur, upper.focusBlur, t),
            dollyZoom = lerp(lower.dollyZoom, upper.dollyZoom, t),
            scaleX = lerp(lower.scaleX, upper.scaleX, t),
            scaleY = lerp(lower.scaleY, upper.scaleY, t)
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
