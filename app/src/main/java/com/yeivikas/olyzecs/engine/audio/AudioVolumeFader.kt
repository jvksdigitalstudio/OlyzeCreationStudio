package com.yeivikas.olyzecs.engine.audio

/*
 * Curva («ley») del fader de volumen: posición del tirador (0…1) ↔ volumen lineal, definida en DECIBELIOS como la de
 * los faders de las estaciones de audio profesionales.
 *
 * Por qué existe: el volumen del clip es LINEAL (0…[MAX_AUDIO_VOLUME]) pero el número que ve la persona está en dB
 * (logarítmico, ver `AudioVolumeDb`). Con un deslizador lineal en volumen, todo el tramo de -60 a -45 dB cae en menos
 * del 0,5 % del recorrido: el tirador parece pegado al extremo mientras el número sigue cambiando, y deslizador y
 * número dejan de estar sincronizados. Aquí el recorrido se reparte POR dB (más fino cerca de 0 dB, donde se mezcla),
 * de modo que el tirador llega al extremo izquierdo justo cuando el número llega a «-inf», y al derecho en el máximo.
 *
 * La curva es lineal por tramos entre las anclas de [FADER_DB_ANCHORS] / [FADER_FRACTION_ANCHORS]: exacta, sin
 * iteraciones, estrictamente creciente (invertible) y probada en JUnit (`AudioVolumeFaderTest`). Matemática pura (sin
 * Android ni Compose).
 *
 *  - 0 (extremo izquierdo)  = silencio absoluto (volumen 0, «-inf»). Todo nivel ≤ [AUDIO_VOLUME_MIN_DB] cae aquí.
 *  - 0,85                   = 0 dB (volumen original, donde va la rayita del deslizador).
 *  - 1 (extremo derecho)    = volumen máximo ([MAX_AUDIO_VOLUME], ≈ +3,5 dB).
 */

/** Niveles (dB) de las anclas de la curva, de menor a mayor. */
private val FADER_DB_ANCHORS = floatArrayOf(AUDIO_VOLUME_MIN_DB, -30f, -12f, 0f, AUDIO_VOLUME_MAX_DB)

/** Posición del tirador (0…1) de cada ancla de [FADER_DB_ANCHORS]; mismo orden y tamaño. */
private val FADER_FRACTION_ANCHORS = floatArrayOf(0f, 0.25f, 0.55f, 0.85f, 1f)

/** Posición del tirador (0…1) del volumen original (0 dB): donde se dibuja la rayita del deslizador. */
val AUDIO_VOLUME_FADER_UNITY_FRACTION: Float = FADER_FRACTION_ANCHORS[3]

/**
 * Posición del tirador (0…1) de un volumen lineal. Silencio (0, negativo, no finito o ≤ [AUDIO_VOLUME_MIN_DB]) → 0;
 * volumen máximo o más → 1; entre medias, interpolación lineal en dB entre las anclas.
 */
fun audioVolumeToFaderFraction(volume: Float): Float {
    if (!volume.isFinite() || volume <= 0f) return 0f
    val db = audioVolumeToDb(volume)
    if (db <= FADER_DB_ANCHORS.first()) return 0f
    if (db >= FADER_DB_ANCHORS.last()) return 1f
    val i = segmentOf(FADER_DB_ANCHORS, db)
    return interpolate(db, FADER_DB_ANCHORS[i], FADER_DB_ANCHORS[i + 1], FADER_FRACTION_ANCHORS[i], FADER_FRACTION_ANCHORS[i + 1])
}

/**
 * Volumen lineal de una posición del tirador (0…1, acotada; no numérica → 0). 0 → silencio absoluto; 1 →
 * [MAX_AUDIO_VOLUME]; entre medias, el inverso exacto de [audioVolumeToFaderFraction].
 */
fun audioFaderFractionToVolume(fraction: Float): Float {
    if (fraction.isNaN() || fraction <= 0f) return 0f
    if (fraction >= 1f) return MAX_AUDIO_VOLUME
    val i = segmentOf(FADER_FRACTION_ANCHORS, fraction)
    val db = interpolate(fraction, FADER_FRACTION_ANCHORS[i], FADER_FRACTION_ANCHORS[i + 1], FADER_DB_ANCHORS[i], FADER_DB_ANCHORS[i + 1])
    return audioDbToVolume(db)
}

/** Índice `i` del tramo `[anchors[i], anchors[i + 1])` que contiene [x] (x ya está dentro del rango de las anclas). */
private fun segmentOf(anchors: FloatArray, x: Float): Int {
    var i = 0
    while (i < anchors.size - 2 && x >= anchors[i + 1]) i++
    return i
}

/** Interpolación lineal de [x] del tramo `[x0, x1]` al tramo `[y0, y1]`. */
private fun interpolate(x: Float, x0: Float, x1: Float, y0: Float, y1: Float): Float =
    y0 + (x - x0) / (x1 - x0) * (y1 - y0)
