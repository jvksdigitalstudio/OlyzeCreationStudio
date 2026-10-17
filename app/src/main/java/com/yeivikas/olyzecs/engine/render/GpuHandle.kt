package com.yeivikas.olyzecs.engine.render

/**
 * FASE 3 — Render / GL Lifecycle.
 *
 * Un `Int` de OpenGL (texture id, program id, etc.) NUNCA alcanza por sí
 * solo para saber si sigue siendo válido: OpenGL ES reutiliza números de
 * handle libremente, así que un id que era válido en el contexto EGL
 * viejo puede coincidir, por pura casualidad, con un id real del
 * contexto EGL nuevo — usarlo "porque no es -1" puede dibujar basura en
 * vez de fallar ruidosamente. La regla arquitectónica de esta fase
 * (ver informe, sección "GPU resource ownership") es que ningún GL
 * handle es, por sí mismo, la fuente de verdad.
 *
 * [GpuHandle] resuelve esto emparejando el id con la "generación" del
 * contexto EGL bajo la cual se creó (ver [GpuContextGeneration]) — un
 * handle solo se considera válido si esa generación coincide EXACTAMENTE
 * con la generación actual del renderer. Es lógica pura (sin ninguna
 * llamada a GLES), así que es 100% testeable con JUnit normal, sin
 * necesitar un contexto GL real ni Robolectric.
 */
data class GpuHandle(val id: Int, val generation: Int) {

    /** true si [id] es un handle real (>=0) creado bajo exactamente [currentGeneration]. */
    fun isValid(currentGeneration: Int): Boolean = id >= 0 && generation == currentGeneration

    companion object {
        /** Ausencia de recurso — nunca es válido para ninguna generación. */
        val INVALID = GpuHandle(id = -1, generation = -1)
    }
}

/**
 * Contador monótono de "generación" del contexto EGL actual del
 * renderer. Se avanza UNA vez por cada `onSurfaceCreated()` real — es
 * decir, cada vez que Android le entrega al renderer un contexto EGL
 * nuevo (primera vez, recreación tras pérdida de contexto, o reapertura
 * de un proyecto). Todo [GpuHandle] creado bajo una generación anterior
 * queda automáticamente inválido en cuanto la generación avanza, sin
 * necesitar tocar ni recorrer cada recurso uno por uno para invalidarlo
 * a mano.
 *
 * No hay necesidad de sincronización explícita más allá de `@Volatile`:
 * tanto la lectura ([value]) como la escritura ([advance]) ocurren
 * siempre en el hilo de GL (contrato de [android.opengl.GLSurfaceView.Renderer]),
 * nunca en paralelo entre sí.
 */
class GpuContextGeneration {
    @Volatile
    private var current: Int = 0

    /** Generación actual — todo [GpuHandle] con esta generación es potencialmente válido. */
    val value: Int get() = current

    /** Marca el comienzo de un contexto EGL nuevo. Devuelve la nueva generación. */
    fun advance(): Int {
        current += 1
        return current
    }
}
