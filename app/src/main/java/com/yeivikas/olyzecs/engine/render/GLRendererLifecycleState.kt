package com.yeivikas.olyzecs.engine.render

/**
 * FASE 3 — Render / GL Lifecycle.
 *
 * Estado explícito del ciclo de vida de [GLRenderer], pensado para
 * reemplazar el patrón de banderas booleanas implícitas
 * (`isInitialized = true`, `isReleased = false`, `isContextReady = ...`)
 * que puede terminar en combinaciones contradictorias si dos de esas
 * banderas se desincronizan. Acá solo existe UN estado válido a la vez.
 *
 * Mapeo directo con los callbacks reales de [android.opengl.GLSurfaceView.Renderer]:
 *
 * ```
 * UNINITIALIZED
 *      │  onSurfaceCreated() — contexto EGL nuevo, shader/texturas base
 *      │  recién (re)creados, pero el viewport todavía no se conoce.
 *      ▼
 * SURFACE_READY
 *      │  onSurfaceChanged() — viewport real ya asignado.
 *      ▼
 * VIEWPORT_READY  ──┐
 *      ▲            │ onSurfaceChanged() de nuevo (resize/rotación):
 *      └────────────┘ se queda en el mismo estado, solo cambia el viewport.
 * ```
 *
 * Desde CUALQUIER estado, `onSurfaceCreated()` vuelve a [UNINITIALIZED]
 * momentáneamente antes de reconstruir — un contexto EGL nuevo invalida
 * por definición todo lo que había antes (ver `GLRenderer.onSurfaceCreated`
 * y el informe de Fase 3, sección "Context loss"), sin importar en qué
 * estado estuviera el renderer previamente. Esa es la única transición
 * "hacia atrás" que existe, y siempre es legal.
 *
 * `onDrawFrame()` (dibujo real) solo debe ejecutarse en [VIEWPORT_READY]
 * — dibujar en [SURFACE_READY] usaría un viewport todavía no asignado
 * por Android (valor por defecto arbitrario), y dibujar en
 * [UNINITIALIZED] no tiene ni shader ni contexto garantizado.
 */
enum class GLRendererLifecycleState {
    /** `onSurfaceCreated()` todavía no corrió para el contexto EGL actual. */
    UNINITIALIZED,

    /** Contexto + recursos GPU base recién (re)creados; viewport aún desconocido. */
    SURFACE_READY,

    /** Viewport ya asignado por `onSurfaceChanged()` — dibujar es seguro. */
    VIEWPORT_READY;

    /**
     * true si pasar de este estado a [target] es una transición legal
     * según el contrato real de `GLSurfaceView.Renderer`. Lógica pura,
     * sin ninguna dependencia de Android/GLES — testeable en JVM normal.
     */
    fun canTransitionTo(target: GLRendererLifecycleState): Boolean {
        // Un contexto EGL nuevo siempre puede empezar de cero, sin
        // importar en qué estado estaba el renderer con el contexto
        // (ahora muerto) anterior.
        if (target == UNINITIALIZED) return true
        return when (this) {
            UNINITIALIZED -> target == SURFACE_READY
            SURFACE_READY -> target == VIEWPORT_READY
            VIEWPORT_READY -> target == VIEWPORT_READY
        }
    }
}
