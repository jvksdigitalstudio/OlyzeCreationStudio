package com.yeivikas.olyzecs.engine.animation

import kotlin.math.pow
import kotlinx.serialization.Serializable

/**
 * Tipos de interpolación entre dos keyframes. EASE_IN_OUT es la curva estándar
 * de cine (aceleración suave, desaceleración suave) y la que usa el Ken Burns
 * effect clásico de documentales.
 *
 * Vive en `engine.animation` (no en `engine.camera`) porque es una utilidad
 * de interpolación genérica: hoy la usa [com.yeivikas.olyzecs.engine.camera.Keyframe],
 * pero no depende de nada de cámara y cualquier otro track animado del motor
 * (por ejemplo, un futuro track de opacidad o de un efecto) puede reutilizarla
 * sin arrastrar el paquete de cámara.
 */
@Serializable
enum class EasingType {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_IN_OUT,
    CUSTOM_BEZIER
}

object Easing {
    fun apply(type: EasingType, t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return when (type) {
            EasingType.LINEAR -> clamped
            EasingType.EASE_IN -> clamped * clamped
            EasingType.EASE_OUT -> 1f - (1f - clamped) * (1f - clamped)
            EasingType.EASE_IN_OUT ->
                if (clamped < 0.5f) 2f * clamped * clamped
                else 1f - (-2f * clamped + 2f).pow(2) / 2f
            EasingType.CUBIC_IN_OUT ->
                if (clamped < 0.5f) 4f * clamped.pow(3)
                else 1f - (-2f * clamped + 2f).pow(3) / 2f
            // CUSTOM_BEZIER se resuelve aparte (ver [applyCubicBezier]), ya
            // que necesita los 4 puntos de control del keyframe, no solo t.
            EasingType.CUSTOM_BEZIER -> clamped
        }
    }

    /**
     * Curva de animación personalizada estilo After Effects / CSS
     * cubic-bezier(x1,y1,x2,y2): dos puntos de control definen la
     * aceleración/desaceleración exacta del movimiento. A diferencia de
     * los 5 easings fijos, esto le da control real a quien sabe lo que
     * está ajustando — un "ease" muy pronunciado al inicio, un rebote
     * sutil, arranques bruscos, etc.
     *
     * Resuelve x(u)=t por bisección (la curva de Bezier está parametrizada
     * en u, no en t directamente) y devuelve y(u) como el progreso real.
     */
    fun applyCubicBezier(t: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val target = t.coerceIn(0f, 1f)
        if (target <= 0f) return 0f
        if (target >= 1f) return 1f

        fun bezierComponent(u: Float, p1: Float, p2: Float): Float {
            val v = 1f - u
            return 3f * v * v * u * p1 + 3f * v * u * u * p2 + u * u * u
        }

        var lo = 0f
        var hi = 1f
        var u = target
        // Bisección: 20 iteraciones da precisión más que suficiente para
        // 8 segundos de proyecto a 60fps.
        repeat(20) {
            val x = bezierComponent(u, x1, x2)
            if (x < target) lo = u else hi = u
            u = (lo + hi) / 2f
        }
        return bezierComponent(u, y1, y2)
    }
}
