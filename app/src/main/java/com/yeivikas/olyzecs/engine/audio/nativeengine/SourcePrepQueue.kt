package com.yeivikas.olyzecs.engine.audio.nativeengine

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resultado de UN intento de preparar una fuente de audio para el motor nativo.
 *
 * La distinción entre [Transient] y [Permanent] es la que decide si merece la pena volver a intentarlo: antes cualquier fallo
 * condenaba la fuente hasta cerrar la app, también los que pasan solos (disco lleno un momento, memoria justa, E/S interrumpida).
 */
internal sealed interface PrepOutcome {
    /** La fuente quedó lista en disco. */
    object Success : PrepOutcome

    /** Falló, pero otro intento puede salir bien (E/S, poco espacio, poca memoria). Se reintenta con espera y con tope. */
    data class Transient(val reason: String) : PrepOutcome

    /** Falló y repetirlo daría lo mismo (sin permiso, error de programación). No se reintenta sola; solo [SourcePrepQueue.retry] la reabre. */
    data class Permanent(val reason: String) : PrepOutcome

    /** Ya nadie la quiere (clip borrado, editor cerrado). No es un fallo y no deja rastro. */
    object Cancelled : PrepOutcome
}

/**
 * Estado observable de una fuente que AÚN no está lista. "Lista" no es un estado de aquí: lo dice el archivo en disco
 * ([EngineSourceCache.peek]); una fuente lista no ocupa memoria en la cola.
 */
internal sealed interface SourcePrepStatus {
    /** En cola o preparándose. [attempt] empieza en 1; un valor mayor es un reintento. [running] distingue "en curso" de "esperando turno". */
    data class Preparing(val attempt: Int, val running: Boolean) : SourcePrepStatus

    /** Falló de forma transitoria y espera a [retryAtMs] (reloj de la cola) para el intento número [attempt]. */
    data class Retrying(val attempt: Int, val retryAtMs: Long, val reason: String) : SourcePrepStatus

    /** Sin más reintentos automáticos tras [attempts] intentos. Solo [SourcePrepQueue.retry] (el usuario) la reabre. */
    data class Failed(val attempts: Int, val reason: String) : SourcePrepStatus
}

/**
 * Carril de preparación. Preparar una fuente cuesta en proporción a su duración, y un solo hilo en cola hacía que un archivo largo
 * retrasara TODOS los siguientes (incluido el clip que el usuario acababa de soltar). Con dos carriles, una fuente corta nunca espera a
 * una larga, y a la vez nunca hay más de UNA preparación larga en curso: los decodificadores compiten por CPU y memoria con la UI y la
 * exportación, así que el paralelismo está acotado a propósito (ver docs/adr/ADR-AUDIO-PREPARACION-DE-FUENTES.md).
 */
internal enum class PrepLane { SHORT, LONG }

/** Intentos totales (el primero incluido) antes de dar una fuente por fallida. */
internal const val SOURCE_PREP_MAX_ATTEMPTS = 3

/** Espera antes del reintento número `failures + 1`: 2 s tras el primer fallo, 6 s tras los siguientes. */
internal fun defaultSourceBackoffMs(failures: Int): Long = if (failures <= 1) 2_000L else 6_000L

/** Traduce una excepción inesperada a un [PrepOutcome]: qué merece reintento, qué no, y qué es solo una cancelación. */
internal fun classifyPrepFailure(error: Throwable): PrepOutcome = when (error) {
    is java.util.concurrent.CancellationException, is InterruptedException -> PrepOutcome.Cancelled
    is OutOfMemoryError -> PrepOutcome.Transient("Sin memoria para preparar el audio")
    is java.io.IOException -> PrepOutcome.Transient("Error de lectura o escritura: ${error.message ?: error.javaClass.simpleName}")
    is SecurityException -> PrepOutcome.Permanent("Sin permiso para leer el archivo")
    else -> PrepOutcome.Permanent(error.message?.let { "${error.javaClass.simpleName}: $it" } ?: error.javaClass.simpleName)
}

/**
 * Máquina de estados de las fuentes pendientes. PURA: sin hilos, sin Android, sin reloj propio (el tiempo entra como parámetro), y por
 * eso determinista y comprobable al milímetro. NO es segura entre hilos: quien la use ([SourcePrepScheduler]) la protege con su cerrojo.
 *
 * Ciclo de vida de una clave: `request` → en cola (QUEUED) → `next` la entrega a un hilo (RUNNING) → `complete`:
 *  - éxito o cancelación: la clave desaparece;
 *  - fallo transitorio: vuelve a la cola con una espera, hasta [maxAttempts] intentos; agotados, queda FAILED;
 *  - fallo permanente: queda FAILED de inmediato.
 * FAILED no se entrega nunca más hasta [retry]; se descarta con [retain] cuando ya ningún clip usa la fuente.
 *
 * @param P lo que el trabajador necesita para preparar la clave (contexto y URI); la cola solo lo transporta.
 */
internal class SourcePrepQueue<P>(
    private val maxAttempts: Int = SOURCE_PREP_MAX_ATTEMPTS,
    private val backoffMs: (failures: Int) -> Long = ::defaultSourceBackoffMs
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts debe ser al menos 1" }
    }

    /** Un intento entregado a un hilo. [isCancelled] es seguro de consultar desde cualquier hilo, sin cerrojo. */
    class Task<P> internal constructor(
        val key: String,
        /** Número de este intento, desde 1. */
        val attempt: Int,
        val payload: P,
        private val cancelFlag: AtomicBoolean
    ) {
        fun isCancelled(): Boolean = cancelFlag.get()
    }

    /** Una fuente pendiente tal como la ve quien consulta la cola: su clave, lo que se pidió con ella y su estado. */
    class Pending<P> internal constructor(val key: String, val payload: P, val status: SourcePrepStatus)

    private enum class Phase { QUEUED, RUNNING, FAILED }

    private class Entry<P>(
        val key: String,
        val payload: P,
        val lane: PrepLane,
        val seq: Long,
        var notBeforeMs: Long
    ) {
        var phase: Phase = Phase.QUEUED
        var failures = 0
        var reason = ""
        val cancelFlag = AtomicBoolean(false)
    }

    private val entries = LinkedHashMap<String, Entry<P>>()
    private var nextSeq = 0L

    /**
     * Pide preparar [key]. Devuelve `true` si es una clave nueva (hay trabajo nuevo que despertar). Si ya existe no cambia nada: ni
     * se adelanta en la cola (la vista previa pregunta una y otra vez; reordenar en cada pregunta daría un orden arbitrario) ni se
     * reabre una fallida (eso es [retry]). Única excepción: si estaba EN CURSO y se la estaba cancelando, volver a pedirla anula la
     * cancelación (el usuario deshizo el borrado del clip).
     */
    fun request(key: String, payload: P, lane: PrepLane, nowMs: Long): Boolean {
        val existing = entries[key]
        if (existing == null) {
            entries[key] = Entry(key, payload, lane, nextSeq++, nowMs)
            return true
        }
        if (existing.phase == Phase.RUNNING) existing.cancelFlag.set(false)
        return false
    }

    /**
     * Reintento pedido por el usuario: reabre una fuente FALLIDA, o adelanta una que espera su reintento automático. Reinicia la cuenta de
     * intentos. Devuelve `true` si algo cambió.
     */
    fun retry(key: String, nowMs: Long): Boolean {
        val entry = entries[key] ?: return false
        if (entry.phase == Phase.RUNNING) return false
        if (entry.phase == Phase.QUEUED && entry.notBeforeMs <= nowMs) return false // ya es elegible: nada que adelantar
        entry.phase = Phase.QUEUED
        entry.failures = 0
        entry.reason = ""
        entry.notBeforeMs = nowMs
        return true
    }

    /**
     * El siguiente intento para un hilo del carril [worker], o `null` si no hay ninguno elegible AHORA. El carril corto solo toma fuentes
     * cortas; el largo prefiere las largas y, si no hay, ayuda con las cortas. Dentro de un carril, por orden de petición.
     */
    fun next(worker: PrepLane, nowMs: Long): Task<P>? {
        var best: Entry<P>? = null
        for (entry in entries.values) {
            if (entry.phase != Phase.QUEUED || entry.notBeforeMs > nowMs || !accepts(worker, entry.lane)) continue
            if (best == null || precedes(worker, entry, best)) best = entry
        }
        val chosen = best ?: return null
        chosen.phase = Phase.RUNNING
        chosen.cancelFlag.set(false)
        return Task(chosen.key, chosen.failures + 1, chosen.payload, chosen.cancelFlag)
    }

    /**
     * Cuánto esperar (ms) hasta que haya algo elegible para [worker], o `null` si no hay nada en cola para él (dormir hasta que lleguen
     * peticiones nuevas). 0 significa que ya hay algo elegible.
     */
    fun nextWakeDelayMs(worker: PrepLane, nowMs: Long): Long? {
        var soonest: Long? = null
        for (entry in entries.values) {
            if (entry.phase != Phase.QUEUED || !accepts(worker, entry.lane)) continue
            val wait = (entry.notBeforeMs - nowMs).coerceAtLeast(0L)
            if (soonest == null || wait < soonest) soonest = wait
        }
        return soonest
    }

    /** Registra el resultado del intento de [key]. Ignora claves que no están en curso (p. ej. ya retiradas). */
    fun complete(key: String, outcome: PrepOutcome, nowMs: Long) {
        val entry = entries[key] ?: return
        if (entry.phase != Phase.RUNNING) return
        // Si ya nadie la quiere, ni un fallo merece quedar registrado: se descarta sin más.
        if (outcome is PrepOutcome.Success || outcome is PrepOutcome.Cancelled || entry.cancelFlag.get()) {
            entries.remove(key)
            return
        }
        when (outcome) {
            is PrepOutcome.Transient -> {
                entry.failures++
                entry.reason = outcome.reason
                if (entry.failures >= maxAttempts) {
                    entry.phase = Phase.FAILED
                } else {
                    entry.phase = Phase.QUEUED
                    entry.notBeforeMs = nowMs + backoffMs(entry.failures)
                }
            }
            is PrepOutcome.Permanent -> {
                entry.failures++
                entry.reason = outcome.reason
                entry.phase = Phase.FAILED
            }
            else -> entries.remove(key) // inalcanzable: Success y Cancelled ya se trataron arriba
        }
    }

    /**
     * Deja solo las claves de [live] (las que algún clip del proyecto sigue usando). Las que esperan o ya fallaron se descartan (así la
     * memoria no crece con cada archivo que se importó y se quitó, y volver a importarlo da un intento limpio). Las que están EN CURSO no
     * se pueden quitar a la fuerza: se marcan canceladas y el trabajador las suelta en cuanto lo comprueba ([Task.isCancelled]).
     *
     * @return `true` si se descartó alguna clave (cambió lo que ve [pending]); marcar una en curso como cancelada no cuenta, porque su
     *   estado visible sigue siendo el mismo hasta que el trabajador la suelta.
     */
    fun retain(live: Set<String>): Boolean {
        var removedAny = false
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key in live) continue
            if (entry.phase == Phase.RUNNING) {
                entry.cancelFlag.set(true)
            } else {
                iterator.remove()
                removedAny = true
            }
        }
        return removedAny
    }

    fun status(key: String, nowMs: Long): SourcePrepStatus? = entries[key]?.toStatus(nowMs)

    /** Foto de todas las fuentes pendientes, con lo que se pidió con cada una (para la interfaz). */
    fun pending(nowMs: Long): List<Pending<P>> = entries.values.map { Pending(it.key, it.payload, it.toStatus(nowMs)) }

    private fun Entry<P>.toStatus(nowMs: Long): SourcePrepStatus = when (phase) {
        Phase.RUNNING -> SourcePrepStatus.Preparing(failures + 1, running = true)
        Phase.FAILED -> SourcePrepStatus.Failed(failures, reason)
        Phase.QUEUED ->
            if (failures > 0 && notBeforeMs > nowMs) SourcePrepStatus.Retrying(failures + 1, notBeforeMs, reason)
            else SourcePrepStatus.Preparing(failures + 1, running = false)
    }

    private fun accepts(worker: PrepLane, lane: PrepLane): Boolean = worker == PrepLane.LONG || lane == PrepLane.SHORT

    /** ¿[a] va antes que [b] para [worker]? El carril largo prefiere lo largo; en lo demás, el orden de petición. */
    private fun precedes(worker: PrepLane, a: Entry<P>, b: Entry<P>): Boolean {
        if (worker == PrepLane.LONG && a.lane != b.lane) return a.lane == PrepLane.LONG
        return a.seq < b.seq
    }
}
