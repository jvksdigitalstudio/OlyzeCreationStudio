package com.yeivikas.olyzecs.engine.audio.nativeengine

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Reloj monótono en milisegundos, inmune a cambios de la hora del sistema. */
internal fun monotonicMs(): Long = System.nanoTime() / 1_000_000L

/**
 * Ejecuta las preparaciones de fuentes que ordena [SourcePrepQueue]: dos hilos de fondo, uno por [PrepLane] (ver ese tipo por qué).
 * No sabe nada de audio ni de Android: recibe el trabajo ([work]) y avisa del resultado ([onSettled]); por eso se prueba con hilos reales
 * y trabajos de mentira.
 *
 * Reglas que mantiene (cada una fue un fallo posible del diseño anterior, de un solo hilo y con una lista de fallidas que nunca se vaciaba):
 *  - Una preparación larga no retrasa a las cortas.
 *  - Un fallo transitorio se reintenta solo, con espera creciente y con tope; uno permanente, no.
 *  - Lo que ya nadie usa se descarta ([retain]); lo que está en curso se cancela cooperativamente ([SourcePrepQueue.Task.isCancelled]).
 *  - [onSettled] y [work] se llaman SIEMPRE sin el cerrojo tomado: quien escucha puede tomar sus propios cerrojos sin riesgo de
 *    interbloqueo (el motor de audio toma el suyo y luego pide fuentes; aquí es al revés, pero nunca a la vez).
 *
 * Los hilos son demonio y se crean al primer [request]: no cuestan nada mientras no haya audio que preparar.
 */
internal class SourcePrepScheduler<P>(
    private val clock: () -> Long = ::monotonicMs,
    maxAttempts: Int = SOURCE_PREP_MAX_ATTEMPTS,
    backoffMs: (failures: Int) -> Long = ::defaultSourceBackoffMs,
    private val threadNamePrefix: String = "eliner-source-prep",
    /** Prepara la fuente de la tarea. Debe devolver el resultado, no lanzarlo (si lanza, se clasifica con [classifyPrepFailure]). */
    private val work: (SourcePrepQueue.Task<P>) -> PrepOutcome,
    /** Se invoca tras cada intento, en el hilo trabajador y sin el cerrojo tomado. */
    private val onSettled: (key: String, outcome: PrepOutcome) -> Unit = { _, _ -> },
    /**
     * Se invoca cada vez que cambia lo que ve [pending] (una fuente nueva, un intento terminado, un reintento pedido, una fuente
     * descartada), siempre sin el cerrojo tomado y desde el hilo que provocó el cambio. Basta con volver a leer [pending]: no dice qué cambió.
     */
    private val onChanged: () -> Unit = {},
    /** Un fallo del propio [onSettled] u [onChanged]: no debe matar al hilo, pero tampoco pasar en silencio. */
    private val onInternalError: (key: String, error: Throwable) -> Unit = { _, _ -> }
) {
    private val queue = SourcePrepQueue<P>(maxAttempts, backoffMs)
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var started = false
    private var closed = false

    /** Pide preparar [key]. No hace nada si ya está pedida, en curso o fallida (ver [SourcePrepQueue.request]). */
    fun request(key: String, payload: P, lane: PrepLane) {
        val added = lock.withLock {
            if (closed) {
                false
            } else {
                startThreadsLocked()
                val isNew = queue.request(key, payload, lane, clock())
                if (isNew) changed.signalAll()
                isNew
            }
        }
        if (added) notifyChanged(key)
    }

    /** Reintento pedido por el usuario. Devuelve `true` si había algo que reabrir o adelantar. */
    fun retry(key: String): Boolean {
        val reopened = lock.withLock {
            val didReopen = queue.retry(key, clock())
            if (didReopen) changed.signalAll()
            didReopen
        }
        if (reopened) notifyChanged(key)
        return reopened
    }

    /** Descarta lo que ya nadie usa y cancela lo que está en curso (ver [SourcePrepQueue.retain]). */
    fun retain(live: Set<String>) {
        val removedAny = lock.withLock { queue.retain(live) }
        if (removedAny) notifyChanged("")
    }

    fun status(key: String): SourcePrepStatus? = lock.withLock { queue.status(key, clock()) }

    /** Foto de las fuentes pendientes, con lo que se pidió con cada una. */
    fun pending(): List<SourcePrepQueue.Pending<P>> = lock.withLock { queue.pending(clock()) }

    /** Detiene los hilos tras su intento actual y cancela lo que esté en curso. Para pruebas y para apagar el proceso con orden. */
    fun close() {
        lock.withLock {
            closed = true
            queue.retain(emptySet())
            changed.signalAll()
        }
    }

    private fun startThreadsLocked() {
        if (started) return
        started = true
        for (lane in PrepLane.values()) {
            val thread = Thread({ workerLoop(lane) }, "$threadNamePrefix-${lane.name.lowercase()}")
            thread.isDaemon = true
            thread.start()
        }
    }

    private fun workerLoop(lane: PrepLane) {
        while (true) {
            val task = awaitTask(lane) ?: return
            val outcome = try {
                work(task)
            } catch (error: Throwable) {
                classifyPrepFailure(error)
            }
            // El resultado se registra y se despierta a TODOS los hilos: un reintento programado cambia cuánto debe esperar el otro carril.
            lock.withLock {
                queue.complete(task.key, outcome, clock())
                changed.signalAll()
            }
            notifyChanged(task.key)
            try {
                onSettled(task.key, outcome)
            } catch (error: Throwable) {
                onInternalError(task.key, error)
            }
        }
    }

    /** Avisa de un cambio sin el cerrojo tomado; un oyente roto se informa pero no rompe a quien provocó el cambio. */
    private fun notifyChanged(key: String) {
        try {
            onChanged()
        } catch (error: Throwable) {
            onInternalError(key, error)
        }
    }

    /** Bloquea hasta que haya un intento elegible para [lane] (o hasta que se cierre: `null`). */
    private fun awaitTask(lane: PrepLane): SourcePrepQueue.Task<P>? = lock.withLock {
        while (!closed) {
            val now = clock()
            queue.next(lane, now)?.let { return@withLock it }
            val delayMs = queue.nextWakeDelayMs(lane, now)
            if (delayMs == null) changed.await() else changed.await(delayMs, TimeUnit.MILLISECONDS)
        }
        null
    }
}
