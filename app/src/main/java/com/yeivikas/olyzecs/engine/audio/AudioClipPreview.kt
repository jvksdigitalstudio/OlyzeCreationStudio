package com.yeivikas.olyzecs.engine.audio

/**
 * Vista previa en bucle de UN clip de audio mientras su ventana ampliada está abierta.
 *
 * Es estado de SESIÓN: no se guarda con el proyecto, no entra al undo/redo y no toca el audio exportado
 * (el `loop` guardado en el clip, que sí cambia el resultado, es otra cosa y no se modifica nunca desde aquí).
 *
 * Modelo deliberadamente mínimo: solo guarda QUÉ clip se está auditando y si el bucle está activo. Lo demás
 * (en qué carril vive, qué tramo del proyecto ocupa) se resuelve en vivo contra los carriles actuales con
 * [resolvedIn], de modo que si el clip se recorta, se mueve o cambia de velocidad con la ventana abierta, el bucle
 * sigue exactamente su tramo real en lugar de uno copiado y desfasado.
 *
 * @property clipId clip abierto en la ventana ampliada.
 * @property loopEnabled `true` = el reloj de reproducción se confina al tramo del clip y se repite, y solo suena el
 *   carril del clip. `false` = reproducción normal del proyecto (la ventana sigue abierta).
 * @property selection rango que el usuario seleccionó en la ventana (ms del ARCHIVO), o `null`. Con el bucle activo,
 *   solo suena y se repite ESE tramo (ver [resolvedIn]); con el bucle apagado la selección no interviene.
 */
data class AudioClipPreview(
    val clipId: String,
    val loopEnabled: Boolean = true,
    val selection: PreviewSourceSelection? = null
)

/**
 * Rango del ARCHIVO de audio (ms, continuos) que el usuario seleccionó en la ventana ampliada. Mismo eje que la regla, el
 * cabezal y la selección dibujada: se traduce a tiempo del proyecto con [previewSelectionRangeOf] cada vez que se usa,
 * así sigue al clip si se recorta, se mueve o cambia de velocidad con la ventana abierta.
 */
data class PreviewSourceSelection(val startMs: Double, val endMs: Double) {
    init {
        require(startMs >= 0.0 && endMs > startMs) { "Selección inválida: [$startMs, $endMs)" }
    }
}

/**
 * Tramo `[startMs, endMs)` del PROYECTO (ms) sobre el que se repite la vista previa.
 */
data class PreviewLoopRange(val startMs: Long, val endMs: Long) {
    init {
        require(startMs >= 0L && endMs > startMs) { "Tramo de bucle inválido: [$startMs, $endMs)" }
    }

    val lengthMs: Long get() = endMs - startMs

    /**
     * Lleva [ms] al interior del tramo:
     *  - antes del inicio → el inicio (p. ej. se pulsó Play con el cursor fuera del clip);
     *  - en o pasado el final → vuelve al principio CONSERVANDO el sobrepaso del cuadro (`inicio + (ms − inicio) mod largo`),
     *    así el bucle no acumula el desfase de un tick por vuelta y, si un tick fuera más largo que el clip, sigue
     *    cayendo en el lugar correcto;
     *  - dentro → sin cambios.
     */
    fun confine(ms: Long): Long = when {
        ms < startMs -> startMs
        ms >= endMs -> startMs + (ms - startMs) % lengthMs
        else -> ms
    }
}

/** El bucle ya resuelto contra los carriles: el tramo a repetir y el carril que debe quedar solo. */
data class ResolvedPreviewLoop(val trackId: String, val range: PreviewLoopRange)

/**
 * Tramo del proyecto que ocupa un clip, o `null` si no es válido (inicio negativo o largo no positivo). Pura (sin
 * `AudioClip`, que lleva un `Uri` ausente en JUnit puro) para poder probar la regla; es la única fuente del tramo.
 */
internal fun previewLoopRangeOf(timelineStartMs: Long, clipLengthMs: Long): PreviewLoopRange? =
    if (timelineStartMs < 0L || clipLengthMs <= 0L) null
    else PreviewLoopRange(timelineStartMs, timelineStartMs + clipLengthMs)

/**
 * Tramo del PROYECTO que ocupa la [selection] (rango del archivo) dentro de un clip: la misma conversión que usa la
 * ventana para situar el cabezal (`proyecto = inicio del clip + (archivo − recorte)`). Solo cuenta lo que
 * SUENA: se recorta al tramo del clip, así una selección que sobresale por un lado se queda con la parte audible.
 * `null` si el clip no es válido o si la selección no toca nada de lo que suena (entonces el bucle
 * sigue usando el clip entero, en vez de dejar la vista previa en silencio). Pura (sin `AudioClip`) para probarla.
 */
internal fun previewSelectionRangeOf(
    timelineStartMs: Long,
    clipLengthMs: Long,
    trimStartMs: Long,
    selection: PreviewSourceSelection
): PreviewLoopRange? {
    if (clipLengthMs <= 0L || timelineStartMs < 0L) return null
    val trim = trimStartMs.coerceAtLeast(0L).toDouble()
    val clipEndMs = timelineStartMs + clipLengthMs
    val fromMs = timelineStartMs + selection.startMs - trim
    val toMs = timelineStartMs + selection.endMs - trim
    val start = Math.round(maxOf(fromMs, timelineStartMs.toDouble())).coerceIn(timelineStartMs, clipEndMs)
    val end = Math.round(minOf(toMs, clipEndMs.toDouble())).coerceIn(timelineStartMs, clipEndMs)
    return if (end > start) PreviewLoopRange(start, end) else null
}

/**
 * Resuelve la sesión contra los [tracks] actuales. `null` si no hay sesión, el bucle está apagado, el clip ya no existe
 * (borrado, cortado, alta deshecha) o no tiene un tramo válido en el proyecto.
 */
fun AudioClipPreview?.resolvedIn(tracks: List<AudioTrack>): ResolvedPreviewLoop? {
    if (this == null || !loopEnabled) return null
    val track = tracks.trackOfClip(clipId) ?: return null
    val clip = track.clip(clipId) ?: return null
    val clipRange = previewLoopRangeOf(clip.timelineStartMs, clip.clipLengthMs) ?: return null
    // Con una selección, el bucle se estrecha a ese tramo; si no toca nada de lo audible, se queda el clip entero.
    val range = selection?.let {
        previewSelectionRangeOf(clip.timelineStartMs, clip.clipLengthMs, clip.trimStartMs, it)
    } ?: clipRange
    return ResolvedPreviewLoop(track.id, range)
}
