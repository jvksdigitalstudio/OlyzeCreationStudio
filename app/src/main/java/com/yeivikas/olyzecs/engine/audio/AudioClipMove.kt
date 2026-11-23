package com.yeivikas.olyzecs.engine.audio

// ============================================================
// Mover un clip de audio — dentro de su carril o a OTRO carril de audio.
//
// Reglas puras (sin Android ni Compose), compartidas por la vista previa del
// arrastre (UI) y el ViewModel: lo que se ve al arrastrar es exactamente lo
// que se aplica al soltar.
//
// Referencia: la playlist de FL Studio Mobile / FL Studio ("you can put any
// Clip type on any Playlist track", los clips pueden solaparse, y el Snap
// determina cómo se mueven los clips respecto a la rejilla). El mismo modelo
// de [AudioTrack] ya admite solapes, así que mover a un carril ocupado no
// reemplaza ni descarta nada: el clip se superpone igual que al pegar.
// ============================================================

/**
 * Mayor inicio (ms) con el que un clip de [clipLengthMs] cabe ENTERO en un
 * proyecto de [projectDurationMs]. `0` si el clip es más largo que el proyecto.
 */
fun maxClipStartMs(clipLengthMs: Long, projectDurationMs: Long): Long =
    (projectDurationMs - clipLengthMs).coerceAtLeast(0L)

/**
 * Inicio final (ms) de un clip de [clipLengthMs] al soltarlo con su borde
 * izquierdo pedido en [rawStartMs]: acotado al proyecto ([maxClipStartMs]) y con
 * imán a [snapTargetsMs] y a la rejilla [gridMs] (0.0 = sin rejilla) — las mismas
 * reglas ([snapMovedClipStartMs]) que el arrastre dentro del carril.
 *
 * Núcleo con primitivos de [AudioTrack.clipDropStartMs]. Pura (sin Android) para
 * poder testearla con JUnit.
 */
fun clipDropStartMs(
    rawStartMs: Long,
    clipLengthMs: Long,
    projectDurationMs: Long,
    snapTargetsMs: List<Long>,
    snapThresholdMs: Long,
    gridMs: Double
): Long {
    val maxStart = maxClipStartMs(clipLengthMs, projectDurationMs)
    val snapped = snapMovedClipStartMs(rawStartMs.coerceIn(0L, maxStart), clipLengthMs, snapTargetsMs, snapThresholdMs, gridMs)
    return snapped.coerceIn(0L, maxStart)
}

/**
 * [clipDropStartMs] para el clip [clipId] (de [clipLengthMs]) soltado sobre este
 * carril: los objetivos del imán son el cursor ([playheadMs]), el inicio y el final
 * del proyecto y los bordes de los OTROS clips del carril — el propio clip se
 * excluye ([AudioTrack.audioSnapTargetsMs]), así no se imanta a sí mismo cuando el
 * destino es su carril de origen — y la rejilla del carril.
 */
fun AudioTrack.clipDropStartMs(
    clipId: String,
    clipLengthMs: Long,
    rawStartMs: Long,
    projectDurationMs: Long,
    playheadMs: Long,
    snapThresholdMs: Long
): Long = clipDropStartMs(
    rawStartMs = rawStartMs,
    clipLengthMs = clipLengthMs,
    projectDurationMs = projectDurationMs,
    snapTargetsMs = audioSnapTargetsMs(projectDurationMs, playheadMs, excludeClipId = clipId),
    snapThresholdMs = snapThresholdMs,
    gridMs = gridStepMs
)

/**
 * Lista de carriles tras mover [clipId] al carril [destTrackId] con su borde
 * izquierdo en [startMs] (acotado con [maxClipStartMs]); `null` si no hay nada
 * que cambiar: el clip o el carril destino no existen, o el clip ya está
 * exactamente ahí.
 *
 *  - Mismo carril: solo cambia el inicio del clip.
 *  - Otro carril: el clip sale de su carril de origen y entra en el destino
 *    HEREDANDO sus ajustes maestros de mezcla ([withClipInheritingMaster]),
 *    como un clip pegado o duplicado: volumen, balance y silencio son del
 *    carril, igual que el fader y el mute de una pista en un DAW. El resto de
 *    ajustes (recorte, fundidos, bucle…) viajan con el clip.
 *  - Si el clip era el único de su carril de origen, ese carril deja de existir
 *    ([withTrack]), igual que al cortar o eliminar su último clip.
 */
fun List<AudioTrack>.withClipMoved(
    clipId: String,
    destTrackId: String,
    startMs: Long,
    projectDurationMs: Long
): List<AudioTrack>? {
    val source = trackOfClip(clipId) ?: return null
    val dest = track(destTrackId) ?: return null
    val clip = source.clip(clipId) ?: return null
    val start = startMs.coerceIn(0L, maxClipStartMs(clip.clipLengthMs, projectDurationMs))
    if (source.id == dest.id) {
        if (start == clip.timelineStartMs) return null
        return withTrack(source.replaceClip(clipId) { it.copy(timelineStartMs = start) })
    }
    return withTrack(source.withoutClip(clipId))
        .withTrack(dest.withClipInheritingMaster(clip.copy(timelineStartMs = start)))
}
