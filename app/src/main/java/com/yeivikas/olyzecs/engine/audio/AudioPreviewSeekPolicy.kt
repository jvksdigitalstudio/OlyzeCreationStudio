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
 * Decide si [AudioPreviewPlayer.sync] debe reposicionar el reproductor.
 * Un seek es audible (corte/repetición), así que solo se hace cuando hace falta:
 *  - [forceSeek] o el reproductor NO está sonando ([playing] = `false`: arranque,
 *    o fin natural del archivo, donde el MediaPlayer se detiene solo) — hay
 *    que ubicarlo antes de arrancar;
 *  - está sonando pero a más de la tolerancia de donde el clip activo lo
 *    necesita ([offsetMs]). Al cambiar de clip ([clipChanged]) rige la
 *    tolerancia estricta [CLIP_JOIN_TOLERANCE_MS] (una unión continua, como las
 *    mitades de un clip dividido, no se toca); en régimen, [DRIFT_TOLERANCE_MS].
 * Pura (sin Android) para poder probarla con JUnit.
 */
internal fun previewNeedsSeek(forceSeek: Boolean, playing: Boolean, clipChanged: Boolean, offsetMs: Int): Boolean {
    if (forceSeek || !playing) return true
    return offsetMs > (if (clipChanged) CLIP_JOIN_TOLERANCE_MS else DRIFT_TOLERANCE_MS)
}
