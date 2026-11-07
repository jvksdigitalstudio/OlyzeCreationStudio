package com.yeivikas.olyzecs.engine.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Volumen en decibelios (dB) para el valor numérico del deslizador de volumen y su cuadro de escritura, como los
 * controles de nivel de FL Studio Mobile (0 dB = volumen original).
 *
 * El volumen del clip sigue siendo LINEAL (0…[MAX_AUDIO_VOLUME]); esto solo traduce de ida y vuelta para mostrarlo y
 * para aceptar lo que la persona escribe. Todo es matemática pura (sin Android ni Compose), probada en JUnit
 * (`AudioVolumeDbTest`).
 *
 * Escritura: el campo acepta ÚNICAMENTE un signo «-» o «+» al inicio, dígitos y un separador decimal; nada de letras
 * ni de unidades. Ver [sanitizeAudioVolumeDbInput].
 */

/** Por debajo de este nivel (dB) el volumen se considera silencio absoluto: 0 lineal y «-inf» en pantalla. */
const val AUDIO_VOLUME_MIN_DB = -60f

/** Nivel (dB) del volumen máximo ([MAX_AUDIO_VOLUME], ≈ +3,5 dB). */
val AUDIO_VOLUME_MAX_DB: Float = 20f * log10(MAX_AUDIO_VOLUME)

/** Máximo de caracteres del campo: signo + hasta tres dígitos + separador + un decimal. */
private const val MAX_DB_INPUT_LENGTH = 6

/**
 * Texto que se muestra para el silencio absoluto: «-inf», la convención de los medidores y faders profesionales
 * (Pro Tools, Ableton, Reaper). Se evita el símbolo «∞»: no existe en la fuente del control y se dibujaba con una
 * fuente de respaldo, deforme y de otro grosor a este tamaño.
 */
const val AUDIO_VOLUME_SILENT_TEXT = "-inf"

/** Nivel en dB de un volumen lineal; `-inf` si es 0 (o no es un número finito mayor que cero). */
fun audioVolumeToDb(volume: Float): Float =
    if (!volume.isFinite() || volume <= 0f) Float.NEGATIVE_INFINITY else 20f * log10(volume)

/**
 * Volumen lineal de un nivel en dB, acotado al rango del clip: a [AUDIO_VOLUME_MIN_DB] o menos es silencio (0), y por
 * encima de [AUDIO_VOLUME_MAX_DB] es el máximo. Un valor no numérico da el volumen original.
 */
fun audioDbToVolume(db: Float): Float = when {
    db.isNaN() -> UNITY_AUDIO_VOLUME
    db <= AUDIO_VOLUME_MIN_DB -> 0f
    else -> clampAudioVolume(10.0.pow(db / 20.0).toFloat())
}

/** Décimas de dB (redondeadas) de un volumen que NO es silencio absoluto. */
private fun dbTenths(volume: Float): Int = (audioVolumeToDb(volume) * 10f).roundToInt()

private fun isSilentDb(volume: Float): Boolean = audioVolumeToDb(volume) <= AUDIO_VOLUME_MIN_DB

/**
 * Texto del valor en vivo junto al deslizador: una décima de dB con signo explícito en los positivos («+3.5»,
 * «0.0», «-3.0») y «-inf» en el silencio absoluto.
 */
fun audioVolumeDbText(volume: Float): String {
    if (isSilentDb(volume)) return AUDIO_VOLUME_SILENT_TEXT
    val tenths = dbTenths(volume)
    val sign = when {
        tenths < 0 -> "-"
        tenths > 0 -> "+"
        else -> ""
    }
    val magnitude = abs(tenths)
    return "$sign${magnitude / 10}.${magnitude % 10}"
}

/**
 * Texto inicial del cuadro de escritura para el volumen actual: sin «+», sin decimal sobrante («0», «-3», «-3.5»).
 * El silencio absoluto se muestra como el mínimo escribible, «-60» (no se pueden teclear letras).
 */
fun audioVolumeDbEditText(volume: Float): String {
    if (isSilentDb(volume)) return AUDIO_VOLUME_MIN_DB.roundToInt().toString()
    val tenths = dbTenths(volume)
    val magnitude = abs(tenths)
    val sign = if (tenths < 0) "-" else ""
    val whole = magnitude / 10
    val decimal = magnitude % 10
    return if (decimal == 0) "$sign$whole" else "$sign$whole.$decimal"
}

/**
 * Deja pasar SOLO lo que se puede escribir en el cuadro de volumen: un «-» o «+» en la primera posición (el «−» tipográfico
 * se normaliza a «-»), dígitos del 0 al 9 y un único separador decimal (coma o punto, ambos pasan a punto). Todo lo
 * demás se descarta al instante; el resultado no pasa de [MAX_DB_INPUT_LENGTH] caracteres.
 */
fun sanitizeAudioVolumeDbInput(raw: String): String {
    var seenSeparator = false
    return buildString {
        raw.forEachIndexed { index, c ->
            when {
                c in '0'..'9' -> append(c)
                index == 0 && (c == '-' || c == '\u2212' || c == '\u2013') -> append('-')
                index == 0 && c == '+' -> append('+')
                (c == '.' || c == ',') && !seenSeparator -> {
                    append('.')
                    seenSeparator = true
                }
            }
        }
    }.take(MAX_DB_INPUT_LENGTH)
}

/**
 * Acota en vivo lo escrito (ya filtrado con [sanitizeAudioVolumeDbInput]): si el número se pasa del máximo o del mínimo
 * del control, el campo muestra directamente el límite («+3.5» / «-60»), respetando el «+» si la persona lo escribió. Un
 * texto incompleto («-», «+», «.», vacío) se deja tal cual para poder seguir escribiendo.
 */
fun limitAudioVolumeDbInput(sanitized: String): String {
    val db = sanitized.toFloatOrNull() ?: return sanitized
    return when {
        db > AUDIO_VOLUME_MAX_DB -> (if (sanitized.startsWith("+")) "+" else "") + "3.5"
        db < AUDIO_VOLUME_MIN_DB -> AUDIO_VOLUME_MIN_DB.roundToInt().toString()
        else -> sanitized
    }
}

/**
 * Volumen lineal que corresponde a lo escrito en el cuadro, o `null` si todavía no es un número (vacío, solo un signo
 * o solo un punto). Acota al rango del control ([audioDbToVolume]).
 */
fun parseAudioVolumeDbInput(text: String): Float? {
    val sanitized = sanitizeAudioVolumeDbInput(text)
    val body = sanitized.removePrefix("-").removePrefix("+")
    if (body.isEmpty() || body == ".") return null
    val db = sanitized.toFloatOrNull() ?: return null
    return audioDbToVolume(db)
}

/** Invierte el signo de lo escrito: «-» pasa a «+», «+» a «-», y sin signo se antepone «-» (el teclado suele no traerlo). */
fun toggleAudioVolumeDbSign(text: String): String = when {
    text.startsWith("-") -> "+" + text.removePrefix("-")
    text.startsWith("+") -> "-" + text.removePrefix("+")
    else -> "-$text"
}.take(MAX_DB_INPUT_LENGTH)

/**
 * Zona magnética (dB, a cada lado de 0 dB) del arrastre vertical sobre el valor numérico: al pasar por el volumen
 * original el valor se «pega» a 0.0 dB, igual que el imán de la rayita del deslizador. Es menor que una décima de
 * paso útil del control, así que no estorba al ajuste fino en otros niveles.
 */
const val AUDIO_VOLUME_DRAG_ZERO_SNAP_DB = 0.2f

/**
 * Volumen lineal resultante de mover [deltaDb] decibelios el nivel de [startVolume] (positivo sube, negativo baja).
 *
 * Es ABSOLUTA respecto al inicio del gesto: el resultado depende solo de `(startVolume, deltaDb)`, no de pasos
 * intermedios, así que volver con el dedo al punto de partida devuelve EXACTAMENTE el volumen original.
 *  - Sin desplazamiento (`deltaDb == 0`) o con uno no finito: [startVolume] acotado, sin redondear.
 *  - Un volumen de partida en silencio absoluto («-inf») arranca desde [AUDIO_VOLUME_MIN_DB].
 *  - Acota a [AUDIO_VOLUME_MIN_DB] (silencio, 0) y a [AUDIO_VOLUME_MAX_DB] (exactamente [MAX_AUDIO_VOLUME]).
 *  - Entre medias, avanza en décimas de dB (lo mismo que se ve en pantalla) con el imán de 0 dB
 *    ([AUDIO_VOLUME_DRAG_ZERO_SNAP_DB]).
 */
fun audioVolumeAfterDbDelta(startVolume: Float, deltaDb: Float): Float {
    if (deltaDb == 0f || !deltaDb.isFinite()) return clampAudioVolume(startVolume)
    val startDb = if (isSilentDb(startVolume)) AUDIO_VOLUME_MIN_DB else audioVolumeToDb(startVolume)
    val rawDb = startDb + deltaDb
    if (rawDb >= AUDIO_VOLUME_MAX_DB) return MAX_AUDIO_VOLUME
    if (rawDb <= AUDIO_VOLUME_MIN_DB) return 0f
    val tenthsDb = (rawDb * 10f).roundToInt() / 10f
    val snappedDb = if (abs(tenthsDb) <= AUDIO_VOLUME_DRAG_ZERO_SNAP_DB) 0f else tenthsDb
    return audioDbToVolume(snappedDb)
}
