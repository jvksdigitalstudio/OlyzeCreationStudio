package com.yeivikas.olyzecs.engine.audio

/** Desvío máximo (ms) tolerado entre el reloj del MediaPlayer y el cabezal antes de corregir con un seek. */
internal const val DRIFT_TOLERANCE_MS = 300

/**
 * Desvío máximo (ms) con el que el cambio de un clip a otro se considera una
 * CONTINUACIÓN del mismo audio (p. ej. las dos mitades de un clip dividido):
 * el reproductor ya está sonando justo donde el clip nuevo lo necesita, y un
 * seek ahí solo introduciría un corte audible. Es mucho más estricto que
 * [DRIFT_TOLERANCE_MS] a propósito: si los clips usan tramos distintos del
 * archivo, un desvío mayor que la imprecisión de `currentPosition` (~decenas
 * de ms) debe corregirse al instante.
 */
internal const val CLIP_JOIN_TOLERANCE_MS = 80

/**
 * Tiempo (ms) tras completarse un seek durante el cual NO se corrige la deriva dentro del mismo clip: justo después de
 * reposicionar, `currentPosition` aún no es fiable y corregir encadena seeks (cada uno corta el audio y reinicia la espera).
 */
internal const val SEEK_SETTLE_MS = 600L

/** Tiempo (ms) tras el cual un seek sin confirmar se da por perdido (el aviso de fin de seek no llegó) y deja de bloquear al reproductor. */
internal const val SEEK_PENDING_TIMEOUT_MS = 2_000L

/** Tope (ms) de la latencia de seek con la que se adelanta cada reposicionamiento: un valor mayor se tomaría por un tirón puntual, no por la latencia del decodificador. */
internal const val MAX_SEEK_COMPENSATION_MS = 300L

/**
 * Decide si [AudioPreviewPlayer.sync] debe reposicionar el reproductor.
 * Un seek es audible (corte/repetición), así que solo se hace cuando hace falta:
 *  - [forceSeek] o el reproductor NO está sonando ([playing] = `false`: arranque,
 *    o fin natural del archivo, donde el MediaPlayer se detiene solo) — hay
 *    que ubicarlo antes de arrancar;
 *  - está sonando pero a más de la tolerancia de donde el clip activo lo
 *    necesita ([offsetMs]). Al cambiar de clip ([clipChanged]) rige la
 *    tolerancia estricta [CLIP_JOIN_TOLERANCE_MS] (una unión continua, como las
 *    mitades de un clip dividido, no se toca); en régimen, [DRIFT_TOLERANCE_MS],
 *    y solo pasado el [SEEK_SETTLE_MS] del último seek ([msSinceSeek]).
 * Pura (sin Android) para poder probarla con JUnit.
 */
internal fun previewNeedsSeek(
    forceSeek: Boolean,
    playing: Boolean,
    clipChanged: Boolean,
    offsetMs: Int,
    msSinceSeek: Long = Long.MAX_VALUE
): Boolean {
    if (forceSeek || !playing) return true
    if (clipChanged) return offsetMs > CLIP_JOIN_TOLERANCE_MS
    if (msSinceSeek < SEEK_SETTLE_MS) return false
    return offsetMs > DRIFT_TOLERANCE_MS
}

/**
 * Latencia de seek a usar tras medir [measuredMs] (desde ordenar el seek hasta su aviso de fin). Se acota a
 * [MAX_SEEK_COMPENSATION_MS] y se suaviza con la anterior ([previousMs], `null` si es la primera medida): así un tirón
 * puntual de la interfaz no la dispara. Se usa para ordenar el seek un poco más adelante y que, al terminar, el audio caiga
 * donde está el cabezal y no atrasado por lo que tardó el decodificador.
 */
internal fun learnedSeekLatencyMs(previousMs: Long?, measuredMs: Long): Long {
    val measured = measuredMs.coerceIn(0L, MAX_SEEK_COMPENSATION_MS)
    return if (previousMs == null) measured else (previousMs + measured) / 2
}
