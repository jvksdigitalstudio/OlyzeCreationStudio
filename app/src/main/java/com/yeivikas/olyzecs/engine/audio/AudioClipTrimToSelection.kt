package com.yeivikas.olyzecs.engine.audio

/**
 * Regla pura de "Recorte": deja un clip SOLO con los tramos que el usuario seleccionó en la ventana ampliada del audio
 * (el botón "Recorte" de la cabecera, junto a Reversa). Con UN rango el clip se acorta a él; con VARIOS (Seleccionar x2 … x7)
 * el clip se reparte en un clip por rango. Sin Android ni Compose: la comparten el ViewModel (que la aplica) y sus pruebas.
 *
 * ## Modelo
 *  - La [PreviewSourceSelection] está en ms del ARCHIVO sobre el mismo eje que dibuja la ventana: con Reversa, el archivo
 *    invertido. `trimStartMs` se mide sobre ese mismo eje (ver [reversedTrimStartMs]), así que no hace falta convertir nada.
 *  - Lo recortable es el PRIMER pase audible del clip: `[trimStartMs, min(duración, trimStartMs + largo))`. Las repeticiones de
 *    un loop no tienen posición propia en el archivo, así que no se pueden seleccionar ni cambian este tramo.
 *  - Un clip nunca baja de [MIN_AUDIO_CLIP_LENGTH_MS] (regla de todo el proyecto, igual que al estirar o dividir): un tramo
 *    más corto no se aplica.
 *  - El resultado es no destructivo (el archivo fuente no se toca): cambia el inicio en el timeline, el largo y el recorte.
 *    Ningún resultado sale del tramo que ocupaba el clip, así que nunca puede invadir a un vecino.
 *  - Con varios rangos, cada tramo conserva EXACTAMENTE su posición en el proyecto (lo que suena en un instante no
 *    cambia): lo no seleccionado queda como hueco, no se corren los demás (no es un "ripple"). Un rango que no alcanza
 *    el largo mínimo o que no toca lo que suena se descarta sin invalidar a los demás; si no queda ninguno, no hay recorte.
 *  - Los fundidos describen los extremos del clip ORIGINAL: el fade-in solo lo conserva el primer tramo y solo si su
 *    inicio no cambió; el fade-out, el último y solo si su final no cambió. Los tramos intermedios no llevan fundidos.
 */
internal data class ClipTrim(
    val timelineStartMs: Long,
    val clipLengthMs: Long,
    val trimStartMs: Long,
    /** `true` si el inicio del clip no cambió: solo entonces se conserva su fade-in. */
    val keepsFadeIn: Boolean,
    /** `true` si el final del clip no cambió: solo entonces se conserva su fade-out. */
    val keepsFadeOut: Boolean
)

/**
 * Recorte del clip a cada rango de [selections], en orden de aparición en el archivo: un [ClipTrim] por rango que deja
 * al menos [MIN_AUDIO_CLIP_LENGTH_MS] de lo que suena. Lista VACÍA si ningún rango lo logra (nada que recortar).
 *
 * [selections] se ordena por inicio y los extremos se redondean al ms más cercano antes de intersectar con el tramo
 * audible. Si dos rangos se solaparan (la ventana nunca lo produce) se fusionan, para no generar clips que suenen dos veces.
 */
internal fun trimClipToSelections(
    timelineStartMs: Long,
    clipLengthMs: Long,
    trimStartMs: Long,
    sourceDurationMs: Long,
    selections: List<PreviewSourceSelection>
): List<ClipTrim> {
    if (clipLengthMs <= 0L || sourceDurationMs <= 0L || selections.isEmpty()) return emptyList()
    val trim = trimStartMs.coerceIn(0L, sourceDurationMs)
    val audibleEnd = minOf(sourceDurationMs, trim + clipLengthMs)

    // Tramos [from, to) en ms enteros, acotados a lo que suena, ordenados y sin solaparse ni tocarse.
    val spans = ArrayList<AudibleSpan>(selections.size)
    for (selection in selections.sortedBy { it.startMs }) {
        val from = maxOf(Math.round(selection.startMs), trim)
        val to = minOf(Math.round(selection.endMs), audibleEnd)
        if (to <= from) continue
        val last = spans.lastOrNull()
        if (last != null && from <= last.to) {
            spans[spans.lastIndex] = AudibleSpan(last.from, maxOf(last.to, to))
        } else {
            spans += AudibleSpan(from, to)
        }
    }

    val kept = spans.filter { it.to - it.from >= MIN_AUDIO_CLIP_LENGTH_MS }
    return kept.mapIndexed { index, span ->
        ClipTrim(
            timelineStartMs = timelineStartMs + (span.from - trim),
            clipLengthMs = span.to - span.from,
            trimStartMs = span.from,
            keepsFadeIn = index == 0 && span.from == trim,
            keepsFadeOut = index == kept.lastIndex && span.to == trim + clipLengthMs
        )
    }
}

/** Tramo semiabierto `[from, to)` del archivo, en ms: resultado intermedio de [trimClipToSelections]. */
private data class AudibleSpan(val from: Long, val to: Long)

/**
 * Recorte del clip a UN solo rango [selection], o `null` si el tramo que suena dentro de ella es más corto que
 * [MIN_AUDIO_CLIP_LENGTH_MS] o no existe. Caso particular de [trimClipToSelections].
 */
internal fun trimClipToSelection(
    timelineStartMs: Long,
    clipLengthMs: Long,
    trimStartMs: Long,
    sourceDurationMs: Long,
    selection: PreviewSourceSelection
): ClipTrim? =
    trimClipToSelections(timelineStartMs, clipLengthMs, trimStartMs, sourceDurationMs, listOf(selection)).singleOrNull()

/** Fade-in del tramo: el del clip original si este tramo conserva su inicio, y 0 si no (un fundido nunca queda a mitad de la señal). */
internal fun ClipTrim.resolvedFadeInMs(originalFadeInMs: Long): Long = if (keepsFadeIn) originalFadeInMs else 0L

/** Fade-out del tramo: el del clip original si este tramo conserva su final, y 0 si no. */
internal fun ClipTrim.resolvedFadeOutMs(originalFadeOutMs: Long): Long = if (keepsFadeOut) originalFadeOutMs else 0L

/**
 * `true` si [trims] deja el clip exactamente como está (un único tramo con el mismo inicio y largo): aplicarlo no
 * cambiaría nada, así que no debe escribir estado ni ensuciar el historial de deshacer.
 */
internal fun List<ClipTrim>.leavesClipUnchanged(timelineStartMs: Long, clipLengthMs: Long): Boolean {
    val only = singleOrNull() ?: return false
    return only.timelineStartMs == timelineStartMs && only.clipLengthMs == clipLengthMs
}

/**
 * Los clips que resultan de recortar este clip a [selections]: uno por tramo, en orden, o `null` si no hay nada que
 * aplicar (ningún tramo válido o el resultado coincide con el clip). El primero conserva el id; los demás usan
 * [newClipId]. Heredan el resto de ajustes (volumen, pan, nombre, reversa…) y los fundidos según [resolvedFadeInMs] /
 * [resolvedFadeOutMs]. Es la contraparte de [splitAtTimelineMs]: la usa el ViewModel como único punto de armado.
 */
internal fun AudioClip.trimmedToSelections(
    selections: List<PreviewSourceSelection>,
    newClipId: () -> String
): List<AudioClip>? {
    val trims = trimClipToSelections(timelineStartMs, clipLengthMs, trimStartMs, sourceDurationMs, selections)
    if (trims.isEmpty() || trims.leavesClipUnchanged(timelineStartMs, clipLengthMs)) return null
    return trims.mapIndexed { index, trim ->
        copy(
            id = if (index == 0) id else newClipId(),
            timelineStartMs = trim.timelineStartMs,
            clipLengthMs = trim.clipLengthMs,
            trimStartMs = trim.trimStartMs,
            fadeInMs = trim.resolvedFadeInMs(fadeInMs),
            fadeOutMs = trim.resolvedFadeOutMs(fadeOutMs)
        )
    }
}
