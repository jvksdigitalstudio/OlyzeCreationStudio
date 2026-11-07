package com.yeivikas.olyzecs.engine.audio

import java.io.File

// Mezcla SECA de un carril con Reverb para la vista previa en tiempo real: el WAV con los clips del carril ya mezclados
// (volumen, balance, fundidos, recortes y bucles, igual que la exportación) y SIN efectos. El Reverb se aplica después, en
// vivo, por [com.yeivikas.olyzecs.engine.audio.fx.LiveReverbStream]: por eso este archivo NO depende de los parámetros del
// efecto y mover una perilla nunca lo invalida (ver [DryTrackCache]).
// Este archivo solo tiene las reglas PURAS (clave, duración, cuál mezcla suena); no toca disco ni hilos.

/** Mezcla seca de un carril, lista para reproducir: el WAV y cuánto dura el audio (desde el instante 0 del proyecto, SIN la cola del efecto). */
internal class DryTrackMix(val file: File, val durationMs: Long)

/**
 * Largo del audio seco de [track]: hasta el final de su último clip que SUENA (uno silenciado no entra en la mezcla ni en
 * la clave, así que tampoco alarga el archivo). La cola del reverb NO se renderiza: la agrega el motor en vivo al reproducir
 * (su duración depende de los parámetros vigentes, que cambian sin tocar este archivo).
 */
internal fun dryMixDurationMs(track: AudioTrack): Long =
    track.clips.filter { !it.muted }.maxOfOrNull { it.timelineStartMs + it.clipLengthMs } ?: 0L

/**
 * Identidad del resultado: todo lo que cambia lo que SUENA en el carril SIN efectos (los clips que suenan, con todos sus
 * ajustes de audio) y nada más. En particular NO incluye la cadena de efectos: cambiar, activar o desactivar el Reverb deja la
 * clave intacta y no vuelve a renderizar. Tampoco entran el nombre, el color ni la rejilla. Los clips silenciados no entran:
 * la mezcla tampoco los usa. Dos carriles con la misma clave suenan idéntico en seco.
 */
internal fun dryMixKey(track: AudioTrack): String = buildString {
    for (c in track.clips) {
        if (c.muted) continue
        append(c.sourceUri).append('|').append(c.sourceDurationMs).append('|').append(c.timelineStartMs).append('|')
            .append(c.clipLengthMs).append('|').append(c.trimStartMs).append('|').append(c.loop).append('|')
            .append(c.fadeInMs).append('|').append(c.fadeOutMs).append('|').append(c.volume).append('|')
            .append(c.pan).append('|').append(c.normalizeGain).append('|').append(c.reversed).append(';')
    }
}

/** `true` si al menos un clip del carril suena (no está silenciado): sin ninguno no hay nada que mezclar ni que oír. */
internal val AudioTrack.hasAudibleClips: Boolean get() = clips.any { !it.muted }

/**
 * Qué mezcla seca debe sonar AHORA para un carril con Reverb (regla pura; la aplica [AudioPreviewPlayers]):
 *  - Sin clips audibles → `null`: el carril real (todos silenciados) no suena. Reproducir una mezcla anterior lo haría sonar
 *    aunque el usuario lo haya silenciado.
 *  - Mezcla vigente lista ([ready]) → esa.
 *  - El render de la versión actual FALLÓ ([failed]) → `null`: se oye el carril real. Seguir con una mezcla vieja haría
 *    sonar algo que ya no corresponde al proyecto, sin ninguna señal para el usuario.
 *  - Render en curso → [lastReady], la anterior: es lo que evita cortes mientras se arrastra un fader de volumen.
 * Con `null` el carril suena por el camino de siempre (el `MediaPlayer` con el audio real, sin reverb).
 */
internal fun resolveDryMix(
    hasAudibleClips: Boolean,
    ready: DryTrackMix?,
    lastReady: DryTrackMix?,
    failed: Boolean
): DryTrackMix? = when {
    !hasAudibleClips -> null
    ready != null -> ready
    failed -> null
    else -> lastReady
}
