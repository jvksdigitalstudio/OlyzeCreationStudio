package com.yeivikas.olyzecs.viewmodel

/**
 * Decide cuándo un cambio continuo (arrastrar un fader, un deslizador…) abre un paso NUEVO del
 * historial de deshacer y cuándo sigue siendo el mismo gesto.
 *
 * Un cambio con la misma [begin] `key` que el anterior, separado de él por no más de [gapMs],
 * continúa el gesto (no genera paso). Una pausa mayor, o una `key` distinta (otro parámetro u otro clip),
 * abre un gesto nuevo. A diferencia de una ventana de tiempo fija desde el último checkpoint, un arrastre
 * largo sigue siendo UN solo paso aunque dure varios segundos.
 *
 * Pura y sin Android: el reloj se inyecta para poder probarla con JUnit. Se usa desde el hilo principal.
 *
 * @param gapMs pausa máxima (ms) entre dos cambios para que cuenten como el mismo gesto.
 * @param clock reloj MONOTÓNICO en ms (inmune a cambios de hora del sistema).
 */
internal class GestureCheckpointGate(
    private val gapMs: Long,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private var activeKey: String? = null
    private var lastChangeMs = 0L

    /** Registra un cambio de [key]; devuelve `true` si abre un gesto nuevo (hay que guardar un paso de deshacer). */
    fun begin(key: String): Boolean {
        val now = clock()
        val continuing = key == activeKey && now - lastChangeMs <= gapMs
        activeKey = key
        lastChangeMs = now
        return !continuing
    }

    /** Cierra el gesto en curso: un checkpoint discreto, deshacer o rehacer separan lo que venga después. */
    fun reset() {
        activeKey = null
    }
}
