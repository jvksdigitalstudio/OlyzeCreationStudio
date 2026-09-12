package com.yeivikas.olyzecs.engine.animation

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Un punto de velocidad en el timeline BASE del proyecto (el mismo eje de
 * tiempo donde viven los keyframes de cámara). Entre dos [SpeedKeyframe]
 * consecutivos, la velocidad se interpola linealmente — a diferencia de
 * la cámara, acá no hace falta un easing elaborado: la sensación de "rampa
 * de velocidad" ya viene de la propia transición gradual entre valores.
 */
@Serializable
data class SpeedKeyframe(
    val timeMs: Long,
    val speed: Float = 1f // 0.1 = muy lento, 1 = normal, 4 = muy rápido
)

/**
 * Congela el timeline BASE en [atMs] durante [holdMs] de tiempo real de
 * reproducción/exportación — la técnica clásica de "freeze frame" para
 * cerrar una escena o enfatizar un instante.
 */
@Serializable
data class FreezeFrame(
    val id: String = UUID.randomUUID().toString(),
    val atMs: Long,
    val holdMs: Long = 1000L
)

/** Estado de ejecución de un freeze en curso; se resetea al reiniciar la reproducción. */
data class FreezeRuntimeState(
    val remainingMs: Long = 0L,
    val consumedFreezeIds: Set<String> = emptySet()
)

/**
 * Motor puro (sin estado propio salvo el que se le pasa explícitamente)
 * de velocidad variable y freeze frame. Se usa TANTO en la reproducción en
 * vivo (tick a tick, en [com.yeivikas.olyzecs.viewmodel.EditorViewModel])
 * como en la exportación offline (de una sola pasada, ver [VideoExporter]) —
 * exactamente la misma lógica en los dos lugares, para que lo que se ve en
 * el preview sea fiel al video final.
 *
 * ## Diseño: el timeline BASE no cambia
 * Los keyframes de cámara se siguen editando y mostrando sobre el mismo
 * eje "base" de siempre (0..projectDurationMs) — acelerar o poner en
 * cámara lenta NO reordena ni comprime visualmente ese eje en el editor.
 * Lo único que cambia es qué tan rápido avanza ese tiempo base durante la
 * reproducción/exportación real, y cuánto dura el video final resultante.
 * Es una decisión de alcance a propósito: evita rehacer toda la UI del
 * timeline como un "timeline elástico" al estilo Premiere, mientras
 * entrega el valor real (cámara lenta, acelerado, freeze) sin medias tintas.
 */
object SpeedRampEngine {

    private const val MIN_SPEED = 0.1f
    private const val MAX_SPEED = 4f

    fun speedAt(speedKeyframes: List<SpeedKeyframe>, baseTimeMs: Long): Float {
        if (speedKeyframes.isEmpty()) return 1f
        val sorted = speedKeyframes.sortedBy { it.timeMs }
        val first = sorted.first()
        val last = sorted.last()
        if (baseTimeMs <= first.timeMs) return first.speed.coerceIn(MIN_SPEED, MAX_SPEED)
        if (baseTimeMs >= last.timeMs) return last.speed.coerceIn(MIN_SPEED, MAX_SPEED)

        var lower = first
        var upper = last
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (baseTimeMs >= a.timeMs && baseTimeMs <= b.timeMs) {
                lower = a
                upper = b
                break
            }
        }
        val span = (upper.timeMs - lower.timeMs).coerceAtLeast(1)
        val t = (baseTimeMs - lower.timeMs).toFloat() / span.toFloat()
        val speed = lower.speed + (upper.speed - lower.speed) * t
        return speed.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    /**
     * Avanza UN tick de tiempo real (de duración [tickMs]) y devuelve el
     * nuevo tiempo base + el estado de freeze actualizado. Es la unidad
     * mínima que reutilizan tanto la reproducción en vivo como el export.
     */
    fun step(
        currentBaseMs: Long,
        tickMs: Long,
        freezeState: FreezeRuntimeState,
        baseDurationMs: Long,
        speedKeyframes: List<SpeedKeyframe>,
        freezeFrames: List<FreezeFrame>
    ): Pair<Long, FreezeRuntimeState> {
        if (freezeState.remainingMs > 0) {
            val remaining = (freezeState.remainingMs - tickMs).coerceAtLeast(0)
            return currentBaseMs to freezeState.copy(remainingMs = remaining)
        }

        val freeze = freezeFrames.firstOrNull {
            it.id !in freezeState.consumedFreezeIds && currentBaseMs >= it.atMs
        }
        if (freeze != null) {
            val next = FreezeRuntimeState(
                remainingMs = freeze.holdMs,
                consumedFreezeIds = freezeState.consumedFreezeIds + freeze.id
            )
            return freeze.atMs to next
        }

        val speed = speedAt(speedKeyframes, currentBaseMs)
        val next = (currentBaseMs + (tickMs * speed).toLong()).coerceIn(0L, baseDurationMs)
        return next to freezeState
    }

    /**
     * Simula el timeline completo (para exportación, donde el fps de
     * salida es fijo) y devuelve la lista de tiempos BASE correspondientes
     * a cada frame de salida, desde 0 hasta que se alcanza [baseDurationMs].
     *
     * Trabaja en precisión doble internamente (solo redondea a Long al
     * leer cada entrada) para que, con velocidad 1 y sin freezes en ningún
     * punto, el resultado sea EXACTAMENTE igual al mapeo lineal de siempre
     * (`frameIndex * 1000 / fps`) — si acumuláramos en enteros el
     * redondeo de cada tick (1000/30 = 33.33... truncado a 33), el error
     * se iría sumando cuadro a cuadro y el video terminaría con un ligero
     * desfase de tiempo incluso sin usar rampas. Cero regresión para
     * proyectos existentes.
     */
    fun buildTimeMapping(
        baseDurationMs: Long,
        fps: Int,
        speedKeyframes: List<SpeedKeyframe>,
        freezeFrames: List<FreezeFrame>
    ): List<Long> {
        if (baseDurationMs <= 0L || fps <= 0) return listOf(0L)

        val tickMs = 1000.0 / fps
        val mapping = mutableListOf<Long>()
        var baseTimeMs = 0.0
        var freezeRemainingMs = 0.0
        val consumedFreezeIds = mutableSetOf<String>()

        // Límite de seguridad: nunca más de 20x los ticks "normales" —
        // protege contra un loop infinito si alguna configuración de
        // velocidad quedara mal armada (en la práctica MIN_SPEED=0.1 ya lo
        // evita, pero más vale prevenir).
        val maxTicks = ((baseDurationMs / tickMs).toLong() + 1) * 20 + 1000
        var ticks = 0L

        while (baseTimeMs < baseDurationMs && ticks < maxTicks) {
            mapping.add(baseTimeMs.toLong().coerceIn(0L, baseDurationMs))
            ticks++

            if (freezeRemainingMs > 0.0) {
                freezeRemainingMs = (freezeRemainingMs - tickMs).coerceAtLeast(0.0)
            } else {
                val freeze = freezeFrames.firstOrNull {
                    it.id !in consumedFreezeIds && baseTimeMs >= it.atMs
                }
                if (freeze != null) {
                    consumedFreezeIds += freeze.id
                    baseTimeMs = freeze.atMs.toDouble()
                    freezeRemainingMs = freeze.holdMs.toDouble()
                } else {
                    val speed = speedAt(speedKeyframes, baseTimeMs.toLong())
                    baseTimeMs = (baseTimeMs + tickMs * speed).coerceIn(0.0, baseDurationMs.toDouble())
                }
            }
        }
        mapping.add(baseDurationMs)
        return mapping
    }

    /** Duración total del video final (en tiempo real) tras aplicar rampas y freezes. */
    fun computeOutputDurationMs(
        baseDurationMs: Long,
        speedKeyframes: List<SpeedKeyframe>,
        freezeFrames: List<FreezeFrame>,
        fps: Int = 30
    ): Long {
        if (speedKeyframes.isEmpty() && freezeFrames.isEmpty()) return baseDurationMs
        val mapping = buildTimeMapping(baseDurationMs, fps, speedKeyframes, freezeFrames)
        return (mapping.size * 1000.0 / fps).toLong()
    }
}
