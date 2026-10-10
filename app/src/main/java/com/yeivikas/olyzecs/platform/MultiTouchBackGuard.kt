package com.yeivikas.olyzecs.platform

import android.view.MotionEvent

/**
 * Arbitraje entre el gesto "atrás" del sistema y los gestos multitáctiles de la app
 * (pellizco de zoom en la ventana ampliada del audio, en el timeline y en el visor).
 *
 * ## Problema
 * Con navegación por gestos, apoyar un dedo en el borde de la pantalla (típico al
 * abrir un pellizco horizontal con los dos pulgares) hace que Android entregue un
 * gesto "atrás" al soltar. Un pellizco largo puede producir varios, y cada uno
 * dispara el `BackHandler` del nivel que esté activo: el primero cierra la ventana
 * ampliada y los siguientes cierran el editor. `systemGestureExclusion` no basta:
 * Android limita la zona reclamable a 200 dp por borde, así que el resto del borde
 * sigue activo.
 *
 * ## Solución
 * La [android.app.Activity] reenvía todos sus eventos táctiles a [onTouchEvent]. Se
 * registra el instante del último evento con 2 o más dedos (y el del `ACTION_CANCEL`
 * que el sistema envía cuando se adueña del gesto, si ocurre durante un pellizco). Un
 * "atrás" que llega dentro de [SUPPRESSION_WINDOW_MS] de ese instante es, con
 * altísima probabilidad, un residuo del pellizco y no una orden del usuario: los
 * `BackHandler` consultan [shouldSuppressBack] y lo ignoran. Un "atrás" deliberado
 * (botón, o gesto sin pellizco previo) no se ve afectado.
 *
 * Es un `object` porque la ventana de supresión es del proceso (una sola pantalla
 * recibe toques a la vez) y los `BackHandler` viven en composables sin acceso a la Activity.
 * La lógica de decisión es pura ([isSuppressed]) para cubrirla con JUnit sin Android.
 */
object MultiTouchBackGuard {

    /** Tiempo (ms) tras el último pellizco durante el cual un "atrás" se considera residuo del gesto. */
    const val SUPPRESSION_WINDOW_MS = 2_000L

    /** Sin pellizco registrado todavía. */
    private const val NEVER = Long.MIN_VALUE

    @Volatile
    private var lastMultiTouchMs: Long = NEVER

    /** Registra un evento táctil de la Activity. Barato: se llama por cada evento. */
    fun onTouchEvent(event: MotionEvent) {
        val multi = event.pointerCount >= 2
        val cancelDuringPinch =
            event.actionMasked == MotionEvent.ACTION_CANCEL && isSuppressed(event.eventTime, lastMultiTouchMs)
        if (multi || cancelDuringPinch) lastMultiTouchMs = event.eventTime
    }

    /** `true` si un "atrás" que llega en [nowMs] (reloj `uptimeMillis`) debe ignorarse. */
    fun shouldSuppressBack(nowMs: Long): Boolean = isSuppressed(nowMs, lastMultiTouchMs)

    /** Olvida todo pellizco previo (para pruebas y al reiniciar la Activity). */
    fun reset() {
        lastMultiTouchMs = NEVER
    }

    /** Decisión pura: ¿[nowMs] cae dentro de la ventana que sigue a [lastMultiTouchMs]? */
    internal fun isSuppressed(nowMs: Long, lastMultiTouchMs: Long): Boolean =
        lastMultiTouchMs != NEVER && nowMs - lastMultiTouchMs in 0..SUPPRESSION_WINDOW_MS
}
