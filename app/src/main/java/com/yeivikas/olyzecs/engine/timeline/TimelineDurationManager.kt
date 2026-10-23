package com.yeivikas.olyzecs.engine.timeline

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dueño único del ciclo de vida de la duración de un proyecto.
 *
 * Encapsula TODA la lógica de expansión automática (cuándo crecer, cuánto
 * y cuándo frenar en el techo) para que [com.yeivikas.olyzecs.viewmodel.EditorViewModel]
 * no necesite conocer tramos, ventanas de disparo ni ningún otro detalle
 * de la política — solo le consulta a esto la duración vigente y, en los
 * puntos donde el playhead se mueve, le pide que evalúe si corresponde
 * expandir.
 *
 * Vive con el scope de UN proyecto (una instancia por
 * [com.yeivikas.olyzecs.viewmodel.EditorViewModel]); no es un
 * singleton compartido entre proyectos, porque cada proyecto tiene su
 * propia duración y su propio recorrido de expansión.
 */
class TimelineDurationManager(initialDurationMs: Long = TimelineLimits.INITIAL_DURATION_MS) {

    private val _state = MutableStateFlow(snapshotFor(initialDurationMs.coerceIn(1L, TimelineLimits.MAX_DURATION_MS)))
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimelineEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TimelineEvent> = _events.asSharedFlow()

    // Evita reemitir MaxDurationReached en cada tick mientras el playhead
    // sigue pegado al techo — se avisa una sola vez por CADA vez que se
    // entra en ese estado, no en bucle en cada frame de reproducción.
    private var maxReachedNotified = false

    fun currentDurationMs(): Long = _state.value.durationMs

    /**
     * Atajo de solo lectura para [TimelineState.isAtMaxLimit] del estado
     * vigente. Evita que cada punto de llamada (EditorViewModel) repita la
     * cadena `state.value.isAtMaxLimit` cada vez que necesita reflejar el
     * límite en su propio UI state tras una expansión.
     */
    val isAtMaxLimit: Boolean
        get() = _state.value.isAtMaxLimit

    /**
     * Arranca un proyecto NUEVO desde cero: siempre 1 minuto, un valor que
     * nunca se le pide ni se le muestra al usuario (ver [TimelineLimits.INITIAL_DURATION_MS]).
     */
    fun startNewProject() {
        maxReachedNotified = false
        _state.value = snapshotFor(TimelineLimits.INITIAL_DURATION_MS)
    }

    /**
     * Restaura la duración de un proyecto EXISTENTE al reabrirlo. A
     * diferencia de [startNewProject], no pasa por la política de
     * expansión ni dispara eventos: solo adopta el valor ya guardado en
     * disco, saneado dentro de un rango válido por si viniera de una
     * versión anterior de la app con otros límites.
     */
    fun restore(savedDurationMs: Long) {
        maxReachedNotified = false
        _state.value = snapshotFor(savedDurationMs.coerceIn(1L, TimelineLimits.MAX_DURATION_MS))
    }

    /**
     * Se llama en cada avance "natural" del playhead (reproducción,
     * grabación). Si [playheadMs] entró en la ventana de "acercándose al
     * final" (ver [TimelineExpansionPolicy.isApproachingEnd]), expande un
     * tramo de una vez; si ya estaba en el techo, notifica el límite (una
     * sola vez por transición).
     *
     * Devuelve la duración vigente DESPUÉS de evaluar la expansión, para
     * que quien llama pueda comparar/clampear el playhead contra el valor
     * correcto sin necesitar otra vuelta de estado.
     */
    fun growIfApproachingEnd(playheadMs: Long): Long {
        val current = _state.value.durationMs
        when {
            TimelineExpansionPolicy.isApproachingEnd(playheadMs, current) -> expandOneStep()
            playheadMs >= current && TimelineExpansionPolicy.isAtMax(current) -> notifyMaxIfNeeded()
            else -> maxReachedNotified = false
        }
        return _state.value.durationMs
    }

    /**
     * Se llama cuando el usuario pide ir DIRECTO a un instante que puede
     * estar más allá del final actual (soltar el playhead lejos de un
     * arrastre, mover un keyframe al fondo del timeline). A diferencia de
     * [growIfApproachingEnd], expande tantos tramos como haga falta de una
     * sola vez en vez de esperar a que el próximo tick lo note.
     *
     * Devuelve la duración vigente DESPUÉS de evaluar la expansión.
     */
    fun ensureCapacityFor(targetMs: Long): Long {
        val current = _state.value.durationMs
        val expanded = TimelineExpansionPolicy.expandToFit(current, targetMs)
        if (expanded != current) {
            maxReachedNotified = false
            _state.value = snapshotFor(expanded)
        }
        if (targetMs >= expanded && TimelineExpansionPolicy.isAtMax(expanded)) {
            notifyMaxIfNeeded()
        } else if (expanded == current) {
            maxReachedNotified = false
        }
        return _state.value.durationMs
    }

    private fun expandOneStep() {
        val current = _state.value.durationMs
        val next = TimelineExpansionPolicy.nextDuration(current)
        if (next != current) {
            maxReachedNotified = false
            _state.value = snapshotFor(next)
        } else if (TimelineExpansionPolicy.isAtMax(current)) {
            notifyMaxIfNeeded()
        }
    }

    private fun notifyMaxIfNeeded() {
        if (!maxReachedNotified) {
            maxReachedNotified = true
            _events.tryEmit(TimelineEvent.MaxDurationReached)
        }
    }

    private fun snapshotFor(durationMs: Long) =
        TimelineState(durationMs = durationMs, isAtMaxLimit = TimelineExpansionPolicy.isAtMax(durationMs))
}
