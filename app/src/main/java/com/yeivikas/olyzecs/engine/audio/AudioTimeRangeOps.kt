package com.yeivikas.olyzecs.engine.audio

import java.util.UUID

/**
 * Selección de tiempo del carril de audio: el rango `[startMs, endMs)` sobre
 * el que actúan las operaciones de [TimeRangeOp] — el equivalente a la
 * "Time selection" de la playlist de FL Studio Mobile (manual, Playlist).
 */
data class AudioTimeSelection(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

/** La selección de tiempo vigente del editor: el [range] y el carril [trackId] sobre el que actúa (hay una sola a la vez, en un solo carril). */
data class AudioTrackTimeSelection(val trackId: String, val range: AudioTimeSelection)

/** Ancho mínimo (ms) de una selección de tiempo. */
const val MIN_TIME_SELECTION_MS = 50L

/**
 * Operaciones sobre una selección de tiempo (manual de FL Studio Mobile,
 * Playlist > Time selection). Todas son NO destructivas con el archivo de
 * audio (solo cortan/mueven clips) y respetan el recorte, el loop y los
 * fundidos de cada clip.
 */
enum class TimeRangeOp(val label: String) {
    /** Abre un hueco de silencio del largo de la selección en su inicio: el audio posterior se corre a la derecha. */
    INSERT_SPACE("Insertar espacio"),

    /** Copia el contenido de la selección justo a continuación de ella; lo posterior se corre a la derecha. */
    DUPLICATE("Duplicar"),

    /** Borra el audio dentro de la selección y deja el hueco (nada se mueve). */
    DELETE("Borrar"),

    /** Borra el audio dentro de la selección Y cierra el hueco: lo posterior se corre a la izquierda. */
    DELETE_SPACE("Borrar espacio"),

    /** Conserva SOLO lo que está dentro de la selección; todo lo de fuera se borra (sin mover nada). */
    TRIM_TO_SELECTION("Recortar a selección")
}

/**
 * Selección que queda tras aplicar [op] a `[startMs, endMs)`: tras duplicar
 * se desplaza a la copia (así repetir "Duplicar" encadena copias), tras
 * "Borrar espacio" desaparece (el rango ya no existe) y en el resto se
 * conserva.
 */
fun selectionAfter(op: TimeRangeOp, startMs: Long, endMs: Long): AudioTimeSelection? = when (op) {
    TimeRangeOp.DUPLICATE -> AudioTimeSelection(endMs, endMs + (endMs - startMs))
    TimeRangeOp.DELETE_SPACE -> null
    else -> AudioTimeSelection(startMs, endMs)
}

/** Geometría de un clip en el timeline — lo único que necesita el planificador (sin Android, sin audio). */
data class ClipSpan(val id: String, val startMs: Long, val lengthMs: Long) {
    val endMs: Long get() = startMs + lengthMs
}

/**
 * Un trozo de un clip ORIGINAL que existe tras la operación: el tramo
 * `[fromOffsetMs, toOffsetMs)` medido desde el inicio de [sourceId], ahora
 * colocado en [newStartMs] del timeline. [isCopy] = es un duplicado (siempre
 * con id nuevo).
 */
data class ClipPiece(
    val sourceId: String,
    val fromOffsetMs: Long,
    val toOffsetMs: Long,
    val newStartMs: Long,
    val isCopy: Boolean = false
) {
    val lengthMs: Long get() = toOffsetMs - fromOffsetMs
}

/** Trozos más cortos que esto (ms) se descartan al planificar: son recortes residuales, inaudibles. */
internal const val MIN_PIECE_MS = 10L

/**
 * Planifica [op] sobre la selección `[startMs, endMs)` y devuelve los trozos
 * de clip resultantes. PURA y sin audio: trabaja solo con geometría
 * ([ClipSpan]), por eso se prueba exhaustivamente con JUnit.
 *
 * Todas las operaciones se reducen a recortar cada clip contra los bordes de
 * la selección y recolocar los trozos:
 *  - un clip que no toca la selección sale entero (movido o no, según la op);
 *  - uno que la cruza se parte en un trozo izquierdo y/o uno derecho;
 *  - uno contenido por completo en ella desaparece (o se copia).
 *
 * @throws IllegalArgumentException si `endMs <= startMs`.
 */
fun planTimeRangeOp(spans: List<ClipSpan>, op: TimeRangeOp, startMs: Long, endMs: Long): List<ClipPiece> {
    require(endMs > startMs) { "La selección debe tener largo positivo" }
    val s = startMs
    val e = endMs
    val d = e - s
    val out = ArrayList<ClipPiece>()

    /** Trozo `[a, b)` (instantes ABSOLUTOS del timeline, dentro del clip) recolocado en [newStart]. */
    fun add(span: ClipSpan, a: Long, b: Long, newStart: Long, isCopy: Boolean = false) {
        if (b - a < MIN_PIECE_MS) return
        out += ClipPiece(span.id, a - span.startMs, b - span.startMs, newStart, isCopy)
    }

    for (c in spans) {
        val cs = c.startMs
        val ce = c.endMs
        when (op) {
            TimeRangeOp.DELETE -> {
                if (cs < s) add(c, cs, minOf(ce, s), cs)
                if (ce > e) add(c, maxOf(cs, e), ce, maxOf(cs, e))
            }
            TimeRangeOp.DELETE_SPACE -> {
                if (cs < s) add(c, cs, minOf(ce, s), cs)
                if (ce > e) add(c, maxOf(cs, e), ce, maxOf(cs, e) - d)
            }
            TimeRangeOp.INSERT_SPACE -> {
                if (cs < s) add(c, cs, minOf(ce, s), cs)
                if (ce > s) add(c, maxOf(cs, s), ce, maxOf(cs, s) + d)
            }
            TimeRangeOp.DUPLICATE -> {
                // Originales: se parte en `e` y lo posterior se corre `d`.
                if (cs < e) add(c, cs, minOf(ce, e), cs)
                if (ce > e) add(c, maxOf(cs, e), ce, maxOf(cs, e) + d)
                // Copias del contenido de la selección, a continuación de ella.
                val a = maxOf(cs, s)
                val b = minOf(ce, e)
                if (b > a) add(c, a, b, a + d, isCopy = true)
            }
            TimeRangeOp.TRIM_TO_SELECTION -> {
                val a = maxOf(cs, s)
                val b = minOf(ce, e)
                if (b > a) add(c, a, b, a)
            }
        }
    }
    return out
}

/**
 * Posición del ARCHIVO (ms) con la que debe arrancar un trozo que empieza
 * [fromOffsetMs] después del inicio de un clip — o `null` si ahí el clip ya
 * no emite nada (archivo agotado y sin loop). Reusa la regla única de
 * [sourcePositionAtProjectMs], por lo que cortar en cualquier punto — también
 * a mitad de la vuelta N de un loop — continúa el audio EXACTAMENTE donde
 * estaba (el valor ya viene envuelto a `[0, duración)`, y el trozo, que
 * conserva `loop`, reinicia en 0 igual que lo haría el clip original).
 */
fun sliceTrimStartMs(sourceDurationMs: Long, trimStartMs: Long, loop: Boolean, fromOffsetMs: Long): Long? =
    sourcePositionAtProjectMs(sourceDurationMs, trimStartMs, loop, 0L, fromOffsetMs)

/**
 * Construye el [piece] como un clip nuevo derivado de este (mismo archivo y
 * ajustes): recorte, inicio y largo propios. El fade-in solo se conserva si
 * el trozo arranca donde arrancaba el clip, y el fade-out solo si termina
 * donde terminaba — un corte nunca deja un fundido a mitad de la señal.
 * `null` si el trozo cae en la cola muda de un clip sin loop.
 */
fun AudioClip.slicePiece(piece: ClipPiece, newId: String): AudioClip? {
    val trim = sliceTrimStartMs(sourceDurationMs, trimStartMs, loop, piece.fromOffsetMs) ?: return null
    return copy(
        id = newId,
        timelineStartMs = piece.newStartMs,
        clipLengthMs = piece.lengthMs,
        trimStartMs = trim,
        fadeInMs = if (piece.fromOffsetMs == 0L) fadeInMs else 0L,
        fadeOutMs = if (piece.toOffsetMs == clipLengthMs) fadeOutMs else 0L
    )
}

/**
 * Aplica [op] a la selección `[startMs, endMs)` de este carril. El primer
 * trozo de cada clip conserva su id (así no se pierde la identidad ni los
 * ajustes de lo que no cambió); los demás y todas las copias reciben un id
 * nuevo de [newId]. Devuelve el carril con los clips ordenados por inicio
 * (puede quedar sin clips).
 */
fun AudioTrack.applyTimeRangeOp(
    op: TimeRangeOp,
    startMs: Long,
    endMs: Long,
    newId: () -> String = { UUID.randomUUID().toString() }
): AudioTrack {
    val byId = clips.associateBy { it.id }
    val pieces = planTimeRangeOp(clips.map { ClipSpan(it.id, it.timelineStartMs, it.clipLengthMs) }, op, startMs, endMs)
    val originalIdTaken = HashSet<String>()
    val result = ArrayList<AudioClip>(pieces.size)
    for (piece in pieces) {
        val source = byId[piece.sourceId] ?: continue
        val id = if (!piece.isCopy && originalIdTaken.add(piece.sourceId)) source.id else newId()
        source.slicePiece(piece, id)?.let { result += it }
    }
    return copy(clips = result.sortedBy { it.timelineStartMs })
}

/** Fin (ms) del último clip del carril — para saber cuánto timeline necesita tras una operación. */
fun AudioTrack.endMs(): Long = clips.maxOfOrNull { it.timelineStartMs + it.clipLengthMs } ?: 0L
