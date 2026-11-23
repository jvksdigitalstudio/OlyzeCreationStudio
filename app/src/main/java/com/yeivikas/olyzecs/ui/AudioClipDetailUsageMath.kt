package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.audio.audibleSourceRangeMs

/**
 * Matemática pura del MAPA DE USO del archivo en la ventana ampliada del audio: qué partes del archivo suenan en el
 * clip abierto, cuáles en otros clips del mismo carril que usan ESE MISMO archivo (p. ej. los trozos que deja un
 * Recorte con varios rangos, o un clip copiado) y cuáles no las usa nadie. Sin Compose ni Android, para cubrirla con JUnit.
 *
 * La ventana edita UN clip, así que dibuja tres niveles: tramo del clip abierto (sin sombra: es lo que se edita), tramos
 * de sus hermanos (sombra suave: suenan, pero son otros clips) y lo que nadie usa (sombra fuerte).
 */

/** Tramo semiabierto `[startMs, endMs)` del ARCHIVO, en ms enteros. */
internal data class DetailSourceSpan(val startMs: Long, val endMs: Long) {
    init {
        require(startMs >= 0L && endMs >= startMs) { "Tramo inválido: [$startMs, $endMs)" }
    }

    val lengthMs: Long get() = endMs - startMs
}

/** Une los tramos que se solapan o se tocan; devuelve la lista ordenada por inicio, sin tramos vacíos. */
internal fun mergeDetailSpans(spans: List<DetailSourceSpan>): List<DetailSourceSpan> {
    val merged = ArrayList<DetailSourceSpan>(spans.size)
    for (span in spans.filter { it.lengthMs > 0L }.sortedBy { it.startMs }) {
        val last = merged.lastOrNull()
        if (last != null && span.startMs <= last.endMs) {
            merged[merged.lastIndex] = DetailSourceSpan(last.startMs, maxOf(last.endMs, span.endMs))
        } else {
            merged += span
        }
    }
    return merged
}

/** Partes de `[0, totalMs)` que NINGÚN tramo de [used] cubre: lo que nadie usa del archivo. */
internal fun detailUnusedSpans(used: List<DetailSourceSpan>, totalMs: Long): List<DetailSourceSpan> {
    if (totalMs <= 0L) return emptyList()
    val gaps = ArrayList<DetailSourceSpan>()
    var cursor = 0L
    for (span in mergeDetailSpans(used)) {
        val start = span.startMs.coerceAtMost(totalMs)
        if (start > cursor) gaps += DetailSourceSpan(cursor, start)
        cursor = maxOf(cursor, span.endMs.coerceAtMost(totalMs))
    }
    if (cursor < totalMs) gaps += DetailSourceSpan(cursor, totalMs)
    return gaps
}

/** Partes que usan los hermanos y NO el clip abierto: lo que se dibuja con la sombra suave. */
internal fun detailSiblingOnlySpans(open: DetailSourceSpan, siblings: List<DetailSourceSpan>): List<DetailSourceSpan> {
    val result = ArrayList<DetailSourceSpan>()
    for (sibling in mergeDetailSpans(siblings)) {
        if (sibling.startMs < open.startMs) {
            result += DetailSourceSpan(sibling.startMs, minOf(sibling.endMs, open.startMs))
        }
        if (sibling.endMs > open.endMs) {
            result += DetailSourceSpan(maxOf(sibling.startMs, open.endMs), sibling.endMs)
        }
    }
    return mergeDetailSpans(result)
}

/**
 * Tramos que usan los OTROS clips de este carril sobre el mismo archivo que [clip] y en el mismo eje (misma Reversa:
 * con la Reversa distinta el recorte de cada uno se mide sobre el archivo invertido, y no son comparables).
 */
internal fun AudioTrack.detailSiblingSpans(clip: AudioClip): List<DetailSourceSpan> =
    clips.asSequence()
        .filter { it.id != clip.id && it.sourceUri == clip.sourceUri && it.reversed == clip.reversed && it.sourceDurationMs > 0L }
        .map { audibleSourceRangeMs(it.sourceDurationMs, it.trimStartMs, it.loop, it.clipLengthMs) }
        .filter { (start, end) -> end > start }
        .map { (start, end) -> DetailSourceSpan(start, end) }
        .toList()
