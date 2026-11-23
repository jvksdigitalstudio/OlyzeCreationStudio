package com.yeivikas.olyzecs.ui.effects

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Calidad con la que se renderiza, de menor a mayor. El orden (`ordinal`) importa: ver [ProgressiveRenderScheduler]. */
enum class RenderTier {
    /** Mientras se arrastra un control: sobre [EffectSources.quick]. */
    DRAG,

    /** Tras una breve pausa: sobre [EffectSources.display], tan nítido como la pantalla. */
    SETTLE,

    /** Lo que se guarda: sobre la imagen completa. */
    FINAL
}

/** Tiempos del orquestador, en milisegundos. */
data class RenderTiming(
    val dragIntervalMs: Long = 60L,
    val settleDelayMs: Long = 150L,
    val commitDelayMs: Long = 500L
)

/**
 * Orquesta la vista previa progresiva y el guardado de UNA ventana de efectos. Sustituye al bloque que estaba
 * copiado en nueve ventanas (throttle + debounce + `fullBitmap ?: liveBitmap`).
 *
 * ## Qué hace
 * 1. **Arrastre** ([schedulePreview]): renderiza [EffectSources.quick] como mucho cada `dragIntervalMs`, y nunca
 *    lanza otro mientras uno sigue en curso (así siempre hay progreso, sin inanición).
 * 2. **Reposo** (`settleDelayMs` tras el último cambio): renderiza [EffectSources.display] — la vista previa nítida.
 *    Si esa copia aún no cargó, usa la rápida.
 * 3. **Confirmar** ([scheduleCommit], `commitDelayMs` tras la última petición): ESPERA la imagen completa
 *    ([EffectSources.awaitFull]) y renderiza desde ella. Si no existe (falló la decodificación) no confirma nada.
 *
 * ## Garantías
 * - **El guardado jamás sale de una copia reducida.** No hay camino de código que lo permita.
 * - **Un resultado de menor calidad nunca pisa a uno mejor** del mismo cambio: un arrastre lento que termina
 *   después de que el reposo ya mostró la imagen nítida se descarta.
 * - Un cambio nuevo cancela el reposo y la confirmación pendientes; sus resultados ya en vuelo se descartan.
 * - **Un guardado pendiente no se pierde en silencio.** Si la ventana se cierra antes de que se ejecute (se programa
 *   500 ms después del último cambio), [flushPendingCommit] lo ejecuta en un ámbito que sobrevive a la ventana. Sin eso,
 *   la vista previa mostraba el efecto pero nunca llegaba a guardarse.
 *
 * ## Un solo renderizado pesado a la vez
 * Los renderizados de reposo y de guardado pueden trabajar sobre imágenes de miles de píxeles y cada efecto reserva varios
 * búferes del tamaño de la imagen. Si se solaparan (el reposo a los 150 ms y el guardado a los 500 ms), el pico de
 * memoria se duplicaría y podría agotar la memoria del dispositivo. Por eso se serializan con un cerrojo; el de arrastre
 * (imagen chica) no participa y nunca espera.
 *
 * ## Hilos
 * Usar siempre desde el despachador de [scope] (en la app, Main). El renderizado pesado lo hace [render], que
 * decide su propio hilo (p. ej. `withContext(Dispatchers.Default)`); aquí solo se coordina.
 *
 * @param render renderiza [source] con los parámetros VIGENTES en ese instante (no una copia vieja). Devuelve `null` si
 *   por ahora no hay nada que renderizar (p. ej. la herramienta aún no tiene datos): no se muestra ni se guarda nada.
 * @param onPreview recibe los resultados de [RenderTier.DRAG] y [RenderTier.SETTLE]: solo se muestran.
 * @param onFinal recibe el de [RenderTier.FINAL]: se guarda y se hace definitivo.
 */
class ProgressiveRenderScheduler<B : Any>(
    private val scope: CoroutineScope,
    private val sources: EffectSources<B>,
    private val render: suspend (source: B, tier: RenderTier) -> B?,
    private val onPreview: (B) -> Unit,
    private val onFinal: (B) -> Unit,
    private val timing: RenderTiming = RenderTiming(),
    private val nowMs: () -> Long = ::monotonicMillis
) {
    private val heavyRender = Mutex()
    private var dragJob: Job? = null
    private var settleJob: Job? = null
    private var finalJob: Job? = null
    private var lastDragStartedAtMs: Long = Long.MIN_VALUE / 2
    private var bestPublishedRank: Int = NOTHING_PUBLISHED

    /**
     * `true` desde que se programa un guardado hasta que se ejecuta (o se comprueba que no hay nada que guardar).
     * [cancelPending] NO lo borra: cancelar el temporizador no significa renunciar al guardado.
     */
    var hasPendingCommit: Boolean = false
        private set

    /** El contenido cambió: actualiza la vista previa (arrastre y reposo). NO guarda. */
    fun schedulePreview() {
        bestPublishedRank = NOTHING_PUBLISHED
        startDragIfIdle()
        scheduleSettle()
    }

    /** Programa el guardado definitivo del estado vigente, tras `commitDelayMs` sin más peticiones. */
    fun scheduleCommit() {
        hasPendingCommit = true
        finalJob?.cancel()
        finalJob = scope.launch {
            delay(timing.commitDelayMs)
            renderAndCommit()
        }
    }

    /** Atajo para ventanas donde cada cambio es a la vez vista previa y candidato a guardado. */
    fun onParametersChanged() {
        schedulePreview()
        scheduleCommit()
    }

    /** Cancela lo pendiente (p. ej. al cerrar la ventana). Lo que ya esté renderizándose se descarta al terminar. */
    fun cancelPending() {
        dragJob?.cancel()
        settleJob?.cancel()
        finalJob?.cancel()
    }

    /**
     * Si queda un guardado pendiente que se iba a perder porque la ventana se cierra, lo ejecuta YA en [persistScope], que
     * debe sobrevivir a la ventana (en la app, el `viewModelScope`). No hace nada si no hay guardado pendiente.
     */
    fun flushPendingCommit(persistScope: CoroutineScope) {
        if (!hasPendingCommit) return
        // Se reclama aquí, de forma síncrona: dos volcados seguidos no pueden guardar dos veces lo mismo.
        hasPendingCommit = false
        persistScope.launch { commitNow() }
    }

    /**
     * Guarda YA el estado vigente, sin esperar el retardo. Para acciones puntuales (p. ej. «quitar efecto») que
     * deben persistir aunque la ventana se cierre: llamar desde un scope que sobreviva a la ventana.
     */
    suspend fun commitNow() {
        cancelPending()
        // Quien empieza a guardar RECLAMA el guardado pendiente: si la ventana se cierra en el mismo gesto (p. ej. «quitar
        // efecto»), el volcado no debe guardar por segunda vez el mismo estado.
        hasPendingCommit = false
        renderAndCommit()
    }

    private suspend fun renderAndCommit() {
        val full = sources.awaitFull()
        val rendered = if (full == null) null else renderExclusively(full, RenderTier.FINAL)
        if (rendered == null) {
            // Nada que guardar (sin imagen completa, o la herramienta no tiene datos): ya no hay guardado pendiente.
            hasPendingCommit = false
            return
        }
        // Un renderizado no cancelable puede terminar después de que la petición quedó obsoleta: no se guarda.
        currentCoroutineContext().ensureActive()
        hasPendingCommit = false
        onFinal(rendered)
    }

    private fun startDragIfIdle() {
        val quick = sources.quick ?: return
        if (dragJob?.isActive == true) return
        val now = nowMs()
        if (now - lastDragStartedAtMs < timing.dragIntervalMs) return
        lastDragStartedAtMs = now
        dragJob = scope.launch {
            val rendered = render(quick, RenderTier.DRAG) ?: return@launch
            ensureActive()
            publishPreview(RenderTier.DRAG, rendered)
        }
    }

    private fun scheduleSettle() {
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(timing.settleDelayMs)
            val source = sources.display ?: sources.quick ?: return@launch
            val rendered = renderExclusively(source, RenderTier.SETTLE) ?: return@launch
            ensureActive()
            publishPreview(RenderTier.SETTLE, rendered)
        }
    }

    /**
     * Un renderizado pesado, de uno en uno. Tras esperar el cerrojo se vuelve a comprobar que la petición sigue vigente: si
     * mientras esperaba llegó un cambio nuevo, no se arranca un cálculo de segundos cuyo resultado ya se descartaría.
     */
    private suspend fun renderExclusively(source: B, tier: RenderTier): B? = heavyRender.withLock {
        currentCoroutineContext().ensureActive()
        render(source, tier)
    }

    private fun publishPreview(tier: RenderTier, rendered: B) {
        if (tier.ordinal < bestPublishedRank) return
        bestPublishedRank = tier.ordinal
        onPreview(rendered)
    }

    private companion object {
        const val NOTHING_PUBLISHED = -1
    }
}

/**
 * Milisegundos de un reloj MONOTÓNICO: solo avanza. Se usa para el límite de frecuencia del arrastre; con el reloj de pared
 * (`currentTimeMillis`) un ajuste de hora hacia atrás haría negativa la diferencia y congelaría la vista previa hasta que el
 * reloj recuperase el valor anterior.
 */
private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

