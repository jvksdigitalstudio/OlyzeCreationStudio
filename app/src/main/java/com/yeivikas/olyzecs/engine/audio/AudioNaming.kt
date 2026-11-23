package com.yeivikas.olyzecs.engine.audio

// ============================================================
// Nombres de carriles y clips de audio.
//
// Hay DOS niveles de nombre, independientes entre sí:
//
//  - Nombre del CARRIL ([AudioTrack.name]): el de la "capa" de audio en la
//    playlist. Lo edita el lápiz del panel de acciones de la fila y se dibuja
//    en la propia fila, igual que el nombre de una capa de imagen.
//  - Nombre del CLIP ([AudioClip.customName]): el de UN clip concreto. Se
//    edita desde el menú del clip (mantener presionado → "Renombrar") y se
//    dibuja en la cabecera del clip.
//
// El nombre del ARCHIVO fuente ([AudioClip.displayName]) no forma parte de
// esta jerarquía: nunca se edita, porque de él se deriva la extensión de la
// copia local del audio (ver `safeAudioExtension` en `ProjectStorage`).
//
// Todo lo de este archivo es puro (sin Android) para poder probarlo con
// JUnit; el saneo de texto es la ÚNICA regla compartida por la UI, el
// ViewModel, la EliNer API y `sanitizeProjectData` (frontera de confianza
// del `project.json`).
// ============================================================

/** Tope de caracteres (code points, no unidades UTF-16) de un nombre de carril o de clip. */
const val AUDIO_NAME_MAX_LENGTH = 60

/** Texto de último recurso cuando ni el carril ni el archivo del primer clip aportan un nombre. */
const val AUDIO_TRACK_FALLBACK_LABEL = "Audio"

/**
 * Normaliza un nombre escrito por el usuario (o leído de un `project.json`,
 * que puede venir corrupto, editado a mano o de un `.olycs` importado):
 *
 *  - los caracteres de control (saltos de línea, tabuladores, NUL…) pasan a
 *    espacio: el nombre se dibuja en UNA línea y no debe poder romper el
 *    layout ni el log;
 *  - se colapsan los espacios repetidos y se recortan los extremos;
 *  - se acota a [AUDIO_NAME_MAX_LENGTH] code points SIN partir un emoji (un
 *    `take(n)` cuenta unidades UTF-16 y dejaría un carácter roto al final).
 *
 * Devuelve `null` si no queda texto: "sin nombre personalizado" es siempre
 * `null`, nunca una cadena vacía (así la UI sabe cuándo caer al nombre del
 * archivo).
 */
fun normalizeAudioName(raw: String?): String? {
    if (raw == null) return null
    val noControls = buildString(raw.length) {
        var index = 0
        while (index < raw.length) {
            val codePoint = raw.codePointAt(index)
            index += Character.charCount(codePoint)
            if (Character.isISOControl(codePoint)) append(' ') else appendCodePoint(codePoint)
        }
    }
    val collapsed = noControls.trim().replace(WHITESPACE_RUN, " ")
    if (collapsed.isEmpty()) return null
    val capped = if (collapsed.codePointCount(0, collapsed.length) <= AUDIO_NAME_MAX_LENGTH) {
        collapsed
    } else {
        collapsed.substring(0, collapsed.offsetByCodePoints(0, AUDIO_NAME_MAX_LENGTH)).trimEnd()
    }
    return capped.takeIf { it.isNotEmpty() }
}

private val WHITESPACE_RUN = Regex("\\s{2,}")

/**
 * Nombre visible de un clip: el que le puso el usuario o, sin personalizar, el
 * nombre de su archivo. Núcleo con primitivos de [AudioClip.label].
 */
fun resolveAudioClipLabel(customName: String?, fileName: String): String =
    normalizeAudioName(customName) ?: fileName

/**
 * Nombre visible de un carril: el que le puso el usuario o, sin personalizar, el
 * nombre del ARCHIVO del primer clip (no el nombre personalizado de ese clip:
 * renombrar un clip no debe cambiar el nombre del carril). Sin ninguno de los
 * dos, [AUDIO_TRACK_FALLBACK_LABEL]. Núcleo con primitivos de [AudioTrack.label].
 */
fun resolveAudioTrackLabel(trackName: String?, firstClipFileName: String?): String =
    normalizeAudioName(trackName)
        ?: firstClipFileName?.takeIf { it.isNotBlank() }
        ?: AUDIO_TRACK_FALLBACK_LABEL

/** Nombre visible de este clip (ver [resolveAudioClipLabel]). */
val AudioClip.label: String get() = resolveAudioClipLabel(customName, displayName)

/** Nombre visible de este carril (ver [resolveAudioTrackLabel]). */
val AudioTrack.label: String get() = resolveAudioTrackLabel(name, clips.firstOrNull()?.displayName)

/**
 * Núcleo con primitivos de [AudioClip.withCustomName] y [AudioTrack.withName]: el
 * nombre personalizado que queda tras pedir [requested] cuando el nombre "de
 * fábrica" es [fallback] (el archivo del clip; el del primer clip, en un carril).
 *
 *  - Se normaliza ([normalizeAudioName]); `null` o sin contenido = sin personalizar.
 *  - Pedir exactamente el nombre de fábrica ES no personalizar: se guarda `null`.
 *    Así abrir el diálogo y pulsar "Guardar" sin editar nada no crea un nombre
 *    "propio" idéntico al del archivo (ni un paso de deshacer vacío), y escribir
 *    de vuelta el nombre del archivo equivale a "Restablecer".
 */
fun resolveCustomAudioName(requested: String?, fallback: String?): String? =
    normalizeAudioName(requested)?.takeUnless { it == fallback }

/**
 * Copia de este clip con el nombre [newName] (ver [resolveCustomAudioName]); `null`
 * o un texto vacío devuelve el clip a su nombre de archivo. Devuelve LA MISMA
 * instancia si el nombre resultante no cambia: Compose y el historial comparan por
 * referencia, y quien llama usa `===` para saber que no hay nada que registrar.
 */
fun AudioClip.withCustomName(newName: String?): AudioClip {
    val resolved = resolveCustomAudioName(newName, fallback = displayName)
    return if (resolved == customName) this else copy(customName = resolved)
}

/**
 * Igual que [AudioClip.withCustomName] pero para el nombre del carril, cuyo nombre
 * de fábrica es el archivo del primer clip. Mismo contrato de identidad.
 */
fun AudioTrack.withName(newName: String?): AudioTrack {
    val resolved = resolveCustomAudioName(newName, fallback = clips.firstOrNull()?.displayName)
    return if (resolved == name) this else copy(name = resolved)
}
