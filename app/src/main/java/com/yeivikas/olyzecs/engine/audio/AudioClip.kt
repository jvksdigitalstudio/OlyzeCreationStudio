package com.yeivikas.olyzecs.engine.audio

import android.net.Uri
import com.yeivikas.olyzecs.engine.scene.Layer
import java.util.UUID

/**
 * FASE 1 — DE "UN SOLO CLIP" A "CARRIL CON VARIOS CLIPS".
 *
 * A PEDIDO EXPLÍCITO DEL USUARIO (Copiar/Pegar un clip de audio dentro del
 * mismo carril, estirar sus bordes, arrastrarlo libremente): el proyecto
 * ya no admite un único [AudioClip] — admite una lista de ellos dentro de
 * un [AudioTrack]. Confirmado contra el manual oficial de FL Studio antes
 * de tocar una sola línea (Image-Line, "The Playlist" y "Audio Clips -
 * Playlist"): "The Playlist window consists of multi-purpose 'Clip
 * Tracks' that can hold [...] Audio Clips [...] This flexibility allows
 * you to place any Clip type on any track" / "Audio Clips show as
 * waveforms in the Playlist and can be stretched, sliced, and positioned
 * freely" — un carril (`Clip Track`) sostiene VARIOS clips, cada uno
 * independiente. No es una arquitectura improvisada para este pedido: es
 * el modelo estándar de cualquier DAW (FL Studio, Ableton, Logic, Cubase
 * funcionan igual).
 *
 * Esta separación es la misma que ya existe, hace tiempo, entre un
 * [Layer] individual y la lista `EditorUiState.layers` — acá se aplicaba
 * tarde porque hasta ahora solo podía existir UN clip de audio en todo el
 * proyecto. [AudioClip] pasa a ser el equivalente exacto de [Layer] (una
 * región de contenido, con id propio) y [AudioTrack] el equivalente de
 * "la playlist de capas", pero para audio.
 */

/**
 * Un clip (región) de audio individual, colocado en algún punto del
 * carril de audio del proyecto ([AudioTrack.clips]) — análogo a [Layer]
 * pero de audio: antes de esta fase, esta clase representaba el ÚNICO
 * clip que podía existir en todo el proyecto; ahora representa UNO
 * cualquiera de varios.
 *
 * `data class` con TODOS los campos `val` — antes era una clase con
 * campos `var` y un `copy()` escrito a mano (con el argumento explícito,
 * en su momento, de que reemplazar la REFERENCIA completa es lo que
 * garantiza que Compose note el cambio). Con una LISTA de clips, ese
 * mismo argumento aplica con más fuerza todavía: la fuente de la verdad
 * de "esto cambió" pasa a ser reemplazar el elemento correspondiente
 * DENTRO de la lista (ver [AudioTrack.replaceClip]), nunca mutar un
 * campo de una instancia que la lista todavía referencia en otro lado.
 * Convertirla en `data class` (mismo patrón que ya usa [Layer]) hace ese
 * error de categoría irrepresentable: ya no existe ningún `var` que
 * mutar por accidente, y el `copy()` lo genera el compilador, no una
 * copia a mano que se puede desincronizar si se agrega un campo y se
 * olvida un lugar.
 */
data class AudioClip(
    /** Identidad estable de este clip — antes no hacía falta (solo podía haber uno); ahora es indispensable para poder referenciar UNO en particular dentro de la lista (seleccionarlo, editarlo, borrarlo, copiarlo). */
    val id: String = UUID.randomUUID().toString(),
    /** Apunta al archivo ya copiado localmente por `ProjectStorage` una vez guardado, o al Uri de SAF recién elegido antes del primer guardado. */
    val sourceUri: Uri,
    val displayName: String,
    /** Duración total del ARCHIVO de audio original (no la de este clip en el timeline — ver [clipLengthMs]). */
    val sourceDurationMs: Long,
    /**
     * Cuánto ocupa ESTE CLIP en el timeline del proyecto, en ms —
     * ANTES de esta fase no existía este campo por separado: un clip
     * siempre ocupaba exactamente `sourceDurationMs - trimStartMs`, sin
     * ninguna forma de ser más corto o más largo que su propio archivo
     * fuente. Ahora puede ser MENOR (recortado) o MAYOR (estirado más
     * allá del contenido original, ver [loop]) — es el campo que hace
     * posible estirar un borde del clip (mejora pendiente, ver
     * `AudioTrackRow`), sin tocar el archivo fuente en absoluto.
     */
    val clipLengthMs: Long = sourceDurationMs,
    /** 0f = silencio, 1f = volumen original, hasta 1.5f para dar algo de boost. */
    val volume: Float = 1f,
    val muted: Boolean = false,
    /** Punto del archivo original donde arranca a sonar (permite recortar el inicio). */
    val trimStartMs: Long = 0L,
    /**
     * Si [clipLengthMs] es mayor que el audio disponible desde
     * [trimStartMs] hasta el final del archivo, repite esa señal en loop
     * hasta llenar el clip — mismo comportamiento documentado del editor
     * multipista de Adobe Audition ("Drag to extend or shorten the
     * loop... you can make the loop repeat fully or partially") y el
     * mismo que mostró el usuario en capturas de FL Studio Mobile
     * (estirar el borde de un clip más allá de su contenido original
     * REPITE la señal entera, no la deforma con un time-stretch de DSP
     * — eso es "Stretch mode" en FL Studio DESKTOP, una función de
     * procesamiento bastante más pesada y distinta, que nadie pidió
     * acá). Si es `false` y el clip es más largo que el audio
     * disponible, el resto queda en silencio.
     */
    val loop: Boolean = true,
    val fadeInMs: Long = 400L,
    val fadeOutMs: Long = 600L,
    /**
     * Punto del PROYECTO (no del archivo fuente — eso es [trimStartMs])
     * donde arranca a sonar ESTE clip, en milisegundos desde el inicio
     * del timeline. Antes de esta fase solo podía haber un clip, así que
     * este campo bastaba para posicionar "la pista entera"; ahora
     * posiciona a este clip en particular DENTRO del carril compartido
     * — dos clips cuyo rango en el tiempo (desde `timelineStartMs` hasta
     * `timelineStartMs + clipLengthMs`) se solapa sí están permitidos
     * por el modelo (ver KDoc de [AudioTrack]); qué hacer visualmente y
     * al reproducir en vivo cuando eso pasa es una decisión de una fase
     * posterior, no de este campo.
     */
    val timelineStartMs: Long = 0L,
    /**
     * Balance estéreo del clip: -1f = todo a la izquierda, 0f = centro (sin
     * cambios), 1f = todo a la derecha. Ley de BALANCE (no de potencia
     * constante): el centro deja la señal intacta y al desplazarse solo se
     * atenúa el canal opuesto — ver [panGains]. Un clip mono con pan != 0
     * se abre a estéreo al exportar para poder ubicarse.
     */
    val pan: Float = 0f,
    /**
     * Ganancia lineal de NORMALIZACIÓN (1f = sin normalizar), calculada a
     * partir del pico real del audio ([normalizeGainForPeak]). No destructiva
     * — el archivo no se toca — y se multiplica por [volume] tanto al
     * exportar como en el preview, así que el fader de volumen sigue
     * funcionando por encima de ella.
     */
    val normalizeGain: Float = 1f,
    /**
     * `true` = el clip reproduce su archivo fuente INVERTIDO (como "Reverse
     * sample" de FL Studio). El recorte ([trimStartMs]) y el loop operan
     * sobre la versión invertida; al alternar este flag el VM recalcula el
     * recorte para que suene el MISMO tramo ([reversedTrimStartMs]).
     */
    val reversed: Boolean = false
)

/**
 * Un carril (capa) de audio del proyecto — el proyecto puede tener VARIOS
 * carriles (`EditorUiState.audioTracks`), y cada uno puede sostener VARIOS
 * [AudioClip] (ver KDoc de arriba, "FASE 1"). Separa dos conceptos que
 * antes vivían mezclados dentro de la misma instancia de `AudioClip`
 * cuando solo podía haber una:
 *  - Identidad del CARRIL: su [id], dónde se ubica entre las demás capas
 *    ([trackOrder]), su candado de orden, su color de identidad. Existe
 *    UNA sola vez para todo el carril — no tiene sentido que cada clip
 *    tenga su propio [trackOrder] si comparten la misma fila.
 *  - Contenido de cada CLIP individual ([clips]): archivo, recorte,
 *    volumen, posición — cada uno el suyo.
 *
 * Los clips pueden quedar superpuestos (por arrastre o al pegar uno
 * encima de otro) — el modelo no lo impide. Qué suena en cada caso:
 *  - Al EXPORTAR ([com.yeivikas.olyzecs.engine.audio.AudioProcessor]):
 *    la mezcla real de TODOS los clips que se solapan, sin excepción —
 *    ver KDoc de `AudioProcessor.buildProjectSamples`.
 *  - Al escuchar EN VIVO dentro del editor
 *    ([com.yeivikas.olyzecs.engine.audio.AudioPreviewPlayers]): cada
 *    carril suena con su propio reproductor, así que los carriles suenan
 *    a la vez; DENTRO de un carril solo suena el clip activo bajo el
 *    cabezal en ese instante (no se construye un mezclador en tiempo real
 *    para clips solapados de un mismo carril). El resultado que se
 *    exporta es siempre el autoritativo.
 */
data class AudioTrack(
    /**
     * Identidad estable de este carril: la usan la selección, el
     * reordenamiento vertical, el modo "Multicolor" y el estado
     * expandido/contraído del timeline, que comparten espacio de ids con
     * [Layer.id] (ambos UUID, nunca colisionan). Un carril migrado de un
     * proyecto guardado antes de existir varios carriles conserva
     * [LEGACY_AUDIO_TRACK_ID].
     */
    val id: String = UUID.randomUUID().toString(),
    val clips: List<AudioClip> = emptyList(),
    /**
     * Posición de este carril dentro de la playlist de capas del
     * timeline (`TimelineView`) — ver el KDoc extenso que tenía este
     * campo en `AudioClip` antes de esta fase (misma semántica exacta,
     * solo que ahora vive acá porque pertenece al carril, no a un clip
     * en particular): mismo espacio de valores que `Layer.zIndex`,
     * orden DESCENDENTE, se fija una sola vez al crear el carril (ver
     * [nextAudioTrackOrder]) y no cambia al agregar/quitar/reemplazar
     * clips dentro de él.
     */
    val trackOrder: Float = 0f,
    /** Bloquea el arrastre VERTICAL de reordenamiento de este carril en la playlist — ver [Layer.orderLocked]. Sin equivalente del candado de "canvas" de Layer: el audio no se dibuja ni se manipula en ningún canvas. */
    val orderLocked: Boolean = false,
    /** Color sólido elegido a mano para este carril — null = sin personalizar, usa el color de identidad de audio por defecto (ver AUDIO_TRACK_COLOR en AudioTrackRow.kt). */
    val customColorArgb: Int? = null,
    val customGradientStartArgb: Int? = null,
    val customGradientEndArgb: Int? = null,
    val useGradientColor: Boolean = false,
    val gradientAngleDegrees: Float = 90f,
    val gradientIsRadial: Boolean = false,
    val useBlackAndWhiteMode: Boolean = false,
    /** Tempo de la rejilla del carril (pulsos por minuto). Solo tiene efecto con [gridStep] != [AudioGridStep.OFF]. */
    val gridBpm: Float = 120f,
    /** División de la rejilla a la que se pega el imán de clips y selecciones; `OFF` = sin rejilla. */
    val gridStep: AudioGridStep = AudioGridStep.OFF
) {
    /** El clip con este [id], o `null` si no existe (ya se borró, o nunca existió). */
    fun clip(id: String): AudioClip? = clips.find { it.id == id }

    /**
     * Reemplaza el clip con este [id] por el resultado de aplicarle
     * [transform] — no-op (devuelve `this` tal cual) si ningún clip
     * tiene ese id, para que un llamador que perdió la referencia a un
     * clip ya borrado (p. ej. una corrutina de análisis de forma de onda
     * que termina tarde) no resucite nada por error.
     */
    fun replaceClip(id: String, transform: (AudioClip) -> AudioClip): AudioTrack {
        var changed = false
        val updated = clips.map { if (it.id == id) { changed = true; transform(it) } else it }
        return if (changed) copy(clips = updated) else this
    }

    /** Quita el clip con este [id] — no-op si no existe. */
    fun withoutClip(id: String): AudioTrack = copy(clips = clips.filterNot { it.id == id })

    /** Agrega un clip nuevo al final de la lista (el orden de [clips] no implica posición en el tiempo — eso lo decide `timelineStartMs` de cada uno). */
    fun withClip(newClip: AudioClip): AudioTrack = copy(clips = clips + newClip)

    /**
     * El clip que debería sonar en el PREVIEW EN VIVO del editor
     * ([com.yeivikas.olyzecs.engine.audio.AudioPreviewPlayer], un solo
     * `MediaPlayer`) si el cabezal estuviera en [projectTimeMs] — `null`
     * si ninguno cubre ese instante o todos los que lo cubren están
     * muteados.
     *
     * Con dos clips solapados en ese instante, gana el ÚLTIMO de
     * [clips] que lo cubre (no el primero): [clips] no tiene ningún
     * orden de apilado explícito propio, así que se usa el orden de
     * inserción como una pila simple — el clip agregado más
     * recientemente (ver [withClip]: siempre al final) queda "arriba",
     * mismo criterio intuitivo que cualquier editor donde lo último que
     * colocás tapa a lo que ya estaba. Decisión acordada explícitamente
     * con el usuario: el preview en vivo NO mezcla varios clips sonando
     * a la vez (eso exigiría varios `MediaPlayer` sincronizados, un
     * motor de audio en tiempo real que no se pidió) — solo uno suena,
     * el de arriba. La EXPORTACIÓN real
     * ([com.yeivikas.olyzecs.engine.audio.AudioProcessor]) sí mezcla
     * TODOS sin excepción; este método no se usa ahí.
     */
    fun activeClipAt(projectTimeMs: Long): AudioClip? =
        clips.lastOrNull { clip ->
            !clip.muted &&
                projectTimeMs >= clip.timelineStartMs &&
                projectTimeMs < clip.timelineStartMs + clip.clipLengthMs
        }
}

/**
 * Cuánto dura en el timeline ([AudioClip.clipLengthMs]) un clip recién
 * creado: EXACTAMENTE lo que dura su archivo ([sourceDurationMs]) — mismo
 * criterio que la playlist de FL Studio Mobile, donde un audio cargado
 * ocupa el largo de su sample y no se estira solo hasta el final del
 * proyecto. Para que cubra más tiempo (o se repita en loop) el usuario
 * estira el borde derecho del clip (ver `AudioClipBlock`) o lo
 * duplica/pega.
 *
 * Antes este valor llenaba todo el resto del proyecto
 * (`projectDurationMs - timelineStartMs`) con `loop = true`, por eso la
 * barra ocupaba todo el carril aunque el archivo durara pocos segundos.
 * Los parámetros [projectDurationMs] y [timelineStartMs] se conservan
 * para no cambiar a los llamadores (`EditorViewModel.importAudio`,
 * `AudioApiImpl.setAudioClip`) ni los proyectos ya guardados.
 *
 * Única función que debe calcular este valor para un clip nuevo.
 */
@Suppress("UNUSED_PARAMETER")
fun defaultClipLengthMs(projectDurationMs: Long, timelineStartMs: Long, sourceDurationMs: Long): Long =
    sourceDurationMs.coerceAtLeast(MIN_AUDIO_CLIP_LENGTH_MS)

/** Largo mínimo (ms) que puede tener un clip en el timeline al estirarlo/acortarlo. */
const val MIN_AUDIO_CLIP_LENGTH_MS = 100L

/**
 * Largo (ms) que queda tras estirar/acortar el borde DERECHO a [requestedMs].
 *
 * REGLA DE LOOP (pedida por el usuario, igual que FL Studio Mobile): solo un
 * clip con [loop] activo se puede estirar o acortar arrastrando su borde; sin
 * loop el clip conserva su largo ORIGINAL ([clipLengthMs]) y esta función
 * devuelve siempre ese mismo valor.
 *
 * Con loop: mínimo [MIN_AUDIO_CLIP_LENGTH_MS]; máximo lo que queda de proyecto
 * desde [timelineStartMs] (nunca menos que el largo actual, para no acortar un
 * clip que ya pasaba el final del proyecto con solo tocarlo).
 *
 * Pura (sin Android). Una única regla compartida: la vista previa del arrastre
 * y el `EditorViewModel` usan ESTA función (vía [clampClipLengthMs]), así lo
 * que se ve al arrastrar es exactamente lo que se aplica al soltar.
 */
fun clipLengthAfterRightEdgeMs(
    loop: Boolean,
    clipLengthMs: Long,
    timelineStartMs: Long,
    projectDurationMs: Long,
    requestedMs: Long
): Long {
    if (!loop) return clipLengthMs
    val maxLength = (projectDurationMs - timelineStartMs).coerceAtLeast(clipLengthMs)
        .coerceAtLeast(MIN_AUDIO_CLIP_LENGTH_MS)
    return requestedMs.coerceIn(MIN_AUDIO_CLIP_LENGTH_MS, maxLength)
}

/** Atajo de [clipLengthAfterRightEdgeMs] para un [AudioClip] concreto. */
fun AudioClip.clampClipLengthMs(requestedMs: Long, projectDurationMs: Long): Long =
    clipLengthAfterRightEdgeMs(loop, clipLengthMs, timelineStartMs, projectDurationMs, requestedMs)

/**
 * Rango permitido (ms) del desplazamiento del borde IZQUIERDO: positivo =
 * acortar por la izquierda, negativo = estirar hacia atrás (repitiendo el
 * archivo en loop). `0L..0L` significa "este borde no se mueve".
 *
 * Solo un clip con [loop] activo y duración de archivo válida puede moverlo
 * (misma regla que [clipLengthAfterRightEdgeMs]): sin loop el clip queda en su
 * largo original. Con loop, hacia atrás se puede estirar hasta el inicio del
 * proyecto (`-timelineStartMs`) y hacia adelante acortar hasta dejar el mínimo
 * ([MIN_AUDIO_CLIP_LENGTH_MS]). Pura (sin Android).
 */
fun leftEdgeDeltaRangeMs(
    loop: Boolean,
    sourceDurationMs: Long,
    timelineStartMs: Long,
    clipLengthMs: Long
): LongRange {
    if (!loop || sourceDurationMs <= 0L) return 0L..0L
    val maxExtendLeft = timelineStartMs.coerceAtLeast(0L)
    val maxShrink = (clipLengthMs - MIN_AUDIO_CLIP_LENGTH_MS).coerceAtLeast(0L)
    return -maxExtendLeft..maxShrink
}

/** Arranque en el timeline, fase de archivo y largo de un clip tras mover su borde izquierdo ([moveLeftEdge]). */
data class LeftEdgeMove(
    val timelineStartMs: Long,
    val trimStartMs: Long,
    val clipLengthMs: Long
)

/**
 * Mueve el borde IZQUIERDO [deltaMs] (acotado a [leftEdgeDeltaRangeMs]).
 *
 * El audio NO se corre de lugar: en cada instante del timeline que el clip ya
 * cubría suena exactamente lo mismo. Como el loop es periódico (período =
 * [sourceDurationMs]), basta con mover la FASE de arranque:
 *
 *     timelineStart' = timelineStart + d
 *     trimStart'     = (trimStart + d) mod sourceDuration     // siempre en [0, D)
 *     clipLength'    = clipLength - d
 *
 * Con `d < 0` (estirar) las repeticiones que aparecen a la izquierda son las
 * vueltas anteriores del archivo; con `d > 0` (acortar) se descartan las
 * primeras. [sourcePositionAtProjectMs] y el export (`buildProjectSamples`)
 * resuelven `trimStart'` con la misma cuenta, así que preview, forma de onda y
 * archivo exportado coinciden. Pura (sin Android) para testearla con JUnit.
 */
fun moveLeftEdge(
    loop: Boolean,
    sourceDurationMs: Long,
    trimStartMs: Long,
    timelineStartMs: Long,
    clipLengthMs: Long,
    deltaMs: Long
): LeftEdgeMove {
    val range = leftEdgeDeltaRangeMs(loop, sourceDurationMs, timelineStartMs, clipLengthMs)
    val d = deltaMs.coerceIn(range.first, range.last)
    if (d == 0L) return LeftEdgeMove(timelineStartMs, trimStartMs, clipLengthMs)
    return LeftEdgeMove(
        timelineStartMs = timelineStartMs + d,
        trimStartMs = Math.floorMod(trimStartMs + d, sourceDurationMs),
        clipLengthMs = clipLengthMs - d
    )
}

/** Atajo de [leftEdgeDeltaRangeMs] para un [AudioClip] concreto. */
fun AudioClip.leftEdgeDeltaRangeMs(): LongRange =
    leftEdgeDeltaRangeMs(loop, sourceDurationMs, timelineStartMs, clipLengthMs)

/** El clip tras mover su borde IZQUIERDO [deltaMs] — ver [moveLeftEdge]. */
fun AudioClip.withLeftEdgeMovedMs(deltaMs: Long): AudioClip {
    val moved = moveLeftEdge(loop, sourceDurationMs, trimStartMs, timelineStartMs, clipLengthMs, deltaMs)
    if (moved.timelineStartMs == timelineStartMs && moved.clipLengthMs == clipLengthMs) return this
    return copy(
        timelineStartMs = moved.timelineStartMs,
        trimStartMs = moved.trimStartMs,
        clipLengthMs = moved.clipLengthMs
    )
}

/**
 * Posición (ms) DENTRO DEL ARCHIVO FUENTE que corresponde al instante
 * [projectTimeMs] del timeline para un clip con estos parámetros — o
 * `null` si en ese instante el clip no emite nada del archivo (el
 * archivo ya se acabó y [loop] es `false`, o el archivo no tiene
 * duración válida).
 *
 * Misma regla EXACTA que `AudioProcessor.buildProjectSamples` (export):
 * la primera pasada arranca en [trimStartMs]; si hace falta más audio y
 * [loop] es `true`, las vueltas siguientes reinician desde el frame 0 del
 * archivo completo. Antes esta cuenta vivía privada dentro de
 * `AudioPreviewPlayer.seekToProjectTime`; se extrae acá (sin Android) para
 * que el preview en vivo, la API y los tests compartan una sola fuente de
 * verdad.
 */
fun sourcePositionAtProjectMs(
    sourceDurationMs: Long,
    trimStartMs: Long,
    loop: Boolean,
    timelineStartMs: Long,
    projectTimeMs: Long
): Long? {
    if (sourceDurationMs <= 0L) return null
    val intoClipMs = (projectTimeMs - timelineStartMs).coerceAtLeast(0L)
    val rawPos = trimStartMs.coerceAtLeast(0L) + intoClipMs
    return when {
        rawPos < sourceDurationMs -> rawPos
        loop -> (rawPos - sourceDurationMs) % sourceDurationMs
        else -> null
    }
}

/** Atajo de [sourcePositionAtProjectMs] para un [AudioClip] concreto. */
fun AudioClip.sourcePositionAt(projectTimeMs: Long): Long? =
    sourcePositionAtProjectMs(sourceDurationMs, trimStartMs, loop, timelineStartMs, projectTimeMs)

/**
 * Primer inicio (ms del timeline) >= [desiredStartMs] donde un clip de
 * [lengthMs] cabe SIN pisar ninguno de los [occupied] (pares
 * `inicio to fin`, fin exclusivo).
 *
 * Regla de "cargar un audio nuevo" del carril: el clip nace en el
 * punto pedido ([desiredStartMs]: el cursor con doble toque, o el punto
 * tocado con "+Clip"); si ahí ya hay otro clip, se coloca justo a
 * continuación del que estorba — nunca encima y NUNCA reemplazando ni
 * borrando los existentes. Pura (sin Android) para poder testearla con
 * JUnit.
 */
fun firstFreeStartMs(occupied: List<Pair<Long, Long>>, desiredStartMs: Long, lengthMs: Long): Long {
    var start = desiredStartMs.coerceAtLeast(0L)
    val length = lengthMs.coerceAtLeast(1L)
    for ((clipStart, clipEnd) in occupied.sortedBy { it.first }) {
        if (start < clipEnd && start + length > clipStart) start = clipEnd
    }
    return start
}

/** [firstFreeStartMs] aplicado a los clips de este carril. */
fun AudioTrack.firstFreeStartMs(desiredStartMs: Long, lengthMs: Long): Long =
    firstFreeStartMs(clips.map { it.timelineStartMs to (it.timelineStartMs + it.clipLengthMs) }, desiredStartMs, lengthMs)

// ============================================================
// Unir clips ("Combine") — inverso exacto de [splitAtTimelineMs]
// ============================================================

/**
 * `true` si un clip B (que arranca en [nextStartMs] y su archivo en
 * [nextTrimStartMs]) es la CONTINUACIÓN EXACTA, sin hueco ni salto, de un
 * clip A (sus parámetros, a continuación): B empieza justo donde A termina
 * en el timeline Y el punto del archivo que A estaría leyendo en ese
 * instante es precisamente el que B arranca leyendo.
 *
 * Es la condición que cumplen las dos mitades que produce
 * [splitAtTimelineMs] — por eso unir después de dividir devuelve el clip
 * original sin que se oiga ninguna diferencia. Pura (sin Android) para
 * poder testearla con JUnit.
 */
fun isExactContinuation(
    sourceDurationMs: Long,
    trimStartMs: Long,
    loop: Boolean,
    startMs: Long,
    lengthMs: Long,
    nextStartMs: Long,
    nextTrimStartMs: Long
): Boolean {
    if (nextStartMs != startMs + lengthMs) return false
    val posAtEnd = sourcePositionAtProjectMs(sourceDurationMs, trimStartMs, loop, startMs, startMs + lengthMs)
        ?: return false
    return posAtEnd == nextTrimStartMs
}

/**
 * `true` si [next] se puede fundir con este clip en UNO solo sin cambiar
 * nada de lo que suena: mismo archivo, continuación exacta en tiempo y en
 * archivo ([isExactContinuation]) y mismos ajustes de reproducción (volumen,
 * mute, loop, invertido, balance y normalización — [mergedWith] conserva los
 * de ESTE clip, así que si difirieran, el tramo de [next] cambiaría de
 * sonido al unirlos). Los fundidos NO se exigen iguales: al unir, el clip resultante
 * conserva el fade-in del primero y el fade-out del segundo (los extremos
 * reales del tramo).
 */
fun AudioClip.canMergeWith(next: AudioClip): Boolean =
    id != next.id &&
        sourceUri == next.sourceUri &&
        sourceDurationMs == next.sourceDurationMs &&
        volume == next.volume &&
        muted == next.muted &&
        loop == next.loop &&
        reversed == next.reversed &&
        pan == next.pan &&
        normalizeGain == next.normalizeGain &&
        isExactContinuation(sourceDurationMs, trimStartMs, loop, timelineStartMs, clipLengthMs, next.timelineStartMs, next.trimStartMs)

/** Fusiona este clip con [next] (ver [canMergeWith]); conserva el id y el fade-in de este y el fade-out de [next]. */
fun AudioClip.mergedWith(next: AudioClip): AudioClip =
    copy(clipLengthMs = clipLengthMs + next.clipLengthMs, fadeOutMs = next.fadeOutMs)

/** El clip del carril que continúa exactamente a [clipId] y se puede unir con él, o `null` si no hay ninguno. */
fun AudioTrack.mergeCandidateAfter(clipId: String): AudioClip? {
    val base = clip(clipId) ?: return null
    return clips.firstOrNull { base.canMergeWith(it) }
}

/** Pico objetivo de "Normalizar": -1 dBFS (≈0.891), deja un margen mínimo contra la saturación. */
const val NORMALIZE_TARGET_PEAK = 0.8913f

/** Tope de ganancia al normalizar (+24 dB): evita disparar el ruido de un archivo casi mudo. */
const val NORMALIZE_MAX_GAIN = 16f

/**
 * Ganancia lineal que lleva un [peak] absoluto (0..1) hasta
 * [targetPeak], acotada a [maxGain]. `null` si el audio es prácticamente
 * silencio (no hay nada que normalizar).
 */
fun normalizeGainForPeak(
    peak: Float,
    targetPeak: Float = NORMALIZE_TARGET_PEAK,
    maxGain: Float = NORMALIZE_MAX_GAIN
): Float? = if (peak < 1e-4f) null else (targetPeak / peak).coerceAtMost(maxGain)

/**
 * Tramo `[inicio, fin)` del ARCHIVO que realmente suena en un clip: si el
 * clip repite (loop) y es más largo que lo que queda del archivo tras el
 * recorte, suena el archivo ENTERO (las vueltas siguientes arrancan en 0);
 * si no, solo `[trim, trim + largo)` acotado al archivo.
 */
fun audibleSourceRangeMs(sourceDurationMs: Long, trimStartMs: Long, loop: Boolean, clipLengthMs: Long): Pair<Long, Long> {
    val trim = trimStartMs.coerceIn(0L, sourceDurationMs.coerceAtLeast(0L))
    val firstPass = sourceDurationMs - trim
    return if (loop && clipLengthMs > firstPass) {
        0L to sourceDurationMs
    } else {
        trim to minOf(sourceDurationMs, trim + clipLengthMs)
    }
}

/**
 * Nuevo `trimStartMs` al alternar "invertir" para que el clip siga sonando
 * el MISMO tramo del audio (ahora al revés). El tramo audible original es
 * `[trim, trim + largo)` del archivo (acotado a su final); en el archivo
 * invertido ese mismo tramo es `[D - fin, D - trim)`, así que el recorte
 * nuevo es `D - fin`. Alternar dos veces devuelve el recorte original
 * mientras el tramo no excediera el archivo. Pura (sin Android).
 */
fun reversedTrimStartMs(sourceDurationMs: Long, trimStartMs: Long, clipLengthMs: Long): Long {
    val end = minOf(sourceDurationMs, trimStartMs.coerceAtLeast(0L) + clipLengthMs.coerceAtLeast(0L))
    return (sourceDurationMs - end).coerceIn(0L, (sourceDurationMs - 100L).coerceAtLeast(0L))
}

/**
 * División de la rejilla musical del carril de audio. Se expresa en PULSOS
 * (negras) por paso, para derivar el paso real en ms de un tempo
 * ([gridStepMs]).
 */
enum class AudioGridStep(val beatsPerStep: Double, val label: String) {
    OFF(0.0, "Sin rejilla"),
    BAR(4.0, "Compás"),
    BEAT(1.0, "1/4"),
    EIGHTH(0.5, "1/8"),
    SIXTEENTH(0.25, "1/16");

    companion object {
        /** Resuelve el nombre guardado en `project.json`; un valor desconocido (versión futura) cae a [OFF], nunca rompe la carga. */
        fun fromName(name: String?): AudioGridStep = values().firstOrNull { it.name == name } ?: OFF
    }
}

/** Rango válido del tempo de la rejilla. */
const val MIN_GRID_BPM = 30f
const val MAX_GRID_BPM = 300f

/**
 * Paso de la rejilla en ms como `Double` (0.0 = sin rejilla). Se mantiene
 * FRACCIONARIO a propósito: a 128 BPM un pulso dura 468.75 ms, y redondear a
 * entero acumularía ~0.25 ms de error por pulso (25 ms tras 100 pulsos) —
 * [snapToTargetsMs] multiplica el paso exacto y recién entonces redondea.
 */
fun gridStepMs(bpm: Float, step: AudioGridStep): Double =
    if (step == AudioGridStep.OFF || bpm <= 0f) 0.0
    else 60_000.0 / bpm.toDouble().coerceIn(MIN_GRID_BPM.toDouble(), MAX_GRID_BPM.toDouble()) * step.beatsPerStep

/** Paso de la rejilla de este carril en ms ([gridStepMs]). */
val AudioTrack.gridStepMs: Double get() = gridStepMs(gridBpm, gridStep)

/**
 * Largo (ms) del tramo que REALMENTE suena de un clip: el clip completo si
 * repite ([loop], el archivo se reinicia hasta llenarlo) o, sin loop, lo que
 * dura el archivo desde [trimStartMs] acotado por [clipLengthMs]. Es el mismo
 * "tramo audible" sobre el que `AudioProcessor.applyVolumeAndFades` calcula
 * los fundidos al exportar. Pura (sin Android).
 */
fun audibleLengthMs(sourceDurationMs: Long, trimStartMs: Long, loop: Boolean, clipLengthMs: Long): Long {
    val length = clipLengthMs.coerceAtLeast(0L)
    if (loop && sourceDurationMs > 0L) return length
    val available = (sourceDurationMs - trimStartMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    return minOf(length, available)
}

/**
 * Fundidos que REALMENTE se aplican (fade-in, fade-out) en ms: cada uno se
 * acota a la mitad del tramo audible [audibleMs] (nunca se solapan). Es la
 * regla única que comparten el export, el preview ([fadeGainAt]) y el dibujo
 * de las rampas en el timeline; antes la UI dibujaba el valor pedido sin
 * acotar y mostraba rampas que no coincidían con lo que sonaba. Pura.
 */
fun effectiveFadesMs(audibleMs: Long, fadeInMs: Long, fadeOutMs: Long): Pair<Long, Long> {
    val maxFadeMs = (audibleMs / 2).coerceAtLeast(0L)
    return Pair(fadeInMs.coerceIn(0L, maxFadeMs), fadeOutMs.coerceIn(0L, maxFadeMs))
}

/**
 * Ganancia (0f..1f) del FUNDIDO de un clip en el instante [intoAudibleMs]
 * (ms desde el inicio del clip) para un tramo audible de [audibleMs].
 *
 * Misma regla EXACTA que `AudioProcessor.applyVolumeAndFades` (export):
 * cada fundido se acota a la mitad del tramo audible y la rampa es lineal
 * (fade-in de 0 a 1 al inicio, fade-out de 1 a 0 sobre los últimos
 * [fadeOutMs]). Existe para que el PREVIEW en vivo suene igual que lo que
 * se exporta: antes el preview ignoraba los fundidos (volumen constante)
 * mientras el export los aplicaba, y los clips por defecto traen 400/600 ms.
 * Pura (sin Android) para poder probarla contra el export.
 */
fun fadeGainAt(audibleMs: Long, fadeInMs: Long, fadeOutMs: Long, intoAudibleMs: Long): Float {
    if (audibleMs <= 0L || intoAudibleMs < 0L || intoAudibleMs >= audibleMs) return 0f
    val (fadeIn, fadeOut) = effectiveFadesMs(audibleMs, fadeInMs, fadeOutMs)
    var gain = 1f
    if (fadeIn > 0L && intoAudibleMs < fadeIn) {
        gain *= intoAudibleMs.toFloat() / fadeIn.toFloat()
    }
    val fadeOutStartMs = audibleMs - fadeOut
    if (fadeOut > 0L && intoAudibleMs >= fadeOutStartMs) {
        gain *= 1f - (intoAudibleMs - fadeOutStartMs).toFloat() / fadeOut.toFloat()
    }
    return gain.coerceIn(0f, 1f)
}

/** [fadeGainAt] para un [AudioClip] concreto en el instante [projectTimeMs] del proyecto. */
fun AudioClip.fadeGainAtProjectMs(projectTimeMs: Long): Float =
    fadeGainAt(
        audibleMs = audibleLengthMs(sourceDurationMs, trimStartMs, loop, clipLengthMs),
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs,
        intoAudibleMs = projectTimeMs - timelineStartMs
    )

/**
 * Ganancias (izquierda, derecha) de un [pan] en -1f..1f con ley de balance:
 * el canal del lado hacia el que se desplaza queda intacto y el opuesto baja
 * linealmente hasta silencio en el extremo. Única fuente de verdad para el
 * export (`AudioProcessor.applyBalancePan`) y el preview
 * (`AudioPreviewPlayer`): lo que se oye al editar es lo que se exporta.
 */
fun panGains(pan: Float): Pair<Float, Float> {
    val p = pan.coerceIn(-1f, 1f)
    return (1f - maxOf(0f, p)) to (1f - maxOf(0f, -p))
}

/**
 * Clip sobre el que operan los módulos flotantes de audio (Volumen, Pan,
 * Silencio…): el que está bajo el cabezal — SIN excluir los silenciados,
 * para poder reactivarlos — o, si el cabezal cae en un hueco, el más
 * cercano por inicio. Antes los módulos editaban siempre el PRIMER clip,
 * sin importar cuál se estuviera mirando.
 */
fun AudioTrack.moduleTargetClip(playheadMs: Long): AudioClip? = clips.moduleTargetClipAt(playheadMs)

/** Núcleo de [moduleTargetClip] sobre cualquier conjunto de clips (los de un carril, o los de todos). */
fun List<AudioClip>.moduleTargetClipAt(playheadMs: Long): AudioClip? =
    firstOrNull { playheadMs >= it.timelineStartMs && playheadMs < it.timelineStartMs + it.clipLengthMs }
        ?: minByOrNull { kotlin.math.abs(it.timelineStartMs - playheadMs) }

/**
 * Igual que [moduleTargetClip] pero respetando la SELECCIÓN por clip: si hay un
 * clip elegido ([selectedClipId]) y existe en el carril, los módulos actúan
 * sobre ÉL; si no, sobre el que está bajo el cabezal. Así el interruptor de
 * Loop, el volumen, etc. cambian el mismo clip que se ve seleccionado (y cuyas
 * líneas de loop aparecen).
 */
fun AudioTrack.moduleTargetClip(playheadMs: Long, selectedClipId: String?): AudioClip? =
    clips.firstOrNull { it.id == selectedClipId } ?: moduleTargetClip(playheadMs)

/**
 * [AudioTrack.id] que recibe el carril de un proyecto guardado ANTES de que
 * existieran varios carriles de audio (aquel único carril no tenía id). No
 * puede colisionar con un id real de capa ni de clip: son
 * `UUID.randomUUID().toString()`, nunca esta cadena fija.
 */
const val LEGACY_AUDIO_TRACK_ID = "__audio_track__"

// ============================================================
// Imán (snap) y división de clips — reglas puras, sin Android/Compose,
// compartidas por la vista previa del arrastre (UI) y el ViewModel para
// que lo que se ve al arrastrar sea exactamente lo que se aplica.
// ============================================================

/**
 * Devuelve el objetivo de [targetsMs] más cercano a [valueMs] si está a
 * [thresholdMs] o menos ("imán"); si ninguno está en rango devuelve
 * [valueMs] sin cambios.
 */
fun snapToTargetsMs(valueMs: Long, targetsMs: List<Long>, thresholdMs: Long, gridMs: Double = 0.0): Long {
    var best = valueMs
    var bestDistance = Long.MAX_VALUE
    for (target in targetsMs) {
        val distance = kotlin.math.abs(valueMs - target)
        if (distance <= thresholdMs && distance < bestDistance) {
            best = target
            bestDistance = distance
        }
    }
    // Rejilla musical: la línea más cercana compite con los objetivos por
    // distancia; ante un empate gana el objetivo (borde de clip/cursor),
    // porque `<` estricto no desplaza al que ya estaba.
    if (gridMs > 0.0) {
        val line = (Math.round(valueMs / gridMs) * gridMs).let { Math.round(it) }
        val distance = kotlin.math.abs(valueMs - line)
        if (distance <= thresholdMs && distance < bestDistance) {
            best = line
        }
    }
    return best
}

/**
 * Imán al MOVER un clip entero: prueba pegar su BORDE IZQUIERDO y su
 * BORDE DERECHO (`startMs + lengthMs`) a los [targetsMs] y se queda con el
 * ajuste más chico (el borde que esté más cerca de un objetivo). Devuelve
 * el nuevo inicio, o [startMs] si ningún borde está dentro de [thresholdMs].
 */
fun snapMovedClipStartMs(startMs: Long, lengthMs: Long, targetsMs: List<Long>, thresholdMs: Long, gridMs: Double = 0.0): Long {
    val snappedStart = snapToTargetsMs(startMs, targetsMs, thresholdMs, gridMs)
    val snappedEnd = snapToTargetsMs(startMs + lengthMs, targetsMs, thresholdMs, gridMs) - lengthMs
    val startShift = kotlin.math.abs(snappedStart - startMs)
    val endShift = kotlin.math.abs(snappedEnd - startMs)
    return when {
        startShift == 0L && endShift == 0L -> startMs
        startShift == 0L -> snappedEnd
        endShift == 0L -> snappedStart
        startShift <= endShift -> snappedStart
        else -> snappedEnd
    }
}

/**
 * Objetivos del imán del carril: inicio y final del proyecto, el cursor
 * ([playheadMs]) y los bordes de cada clip de [clipRanges] (pares inicio/fin
 * en el timeline). Única fuente de estos objetivos para el arrastre de clips,
 * la selección de tiempo y el punto de "+Clip". Pura (sin Android) para poder
 * testearla con JUnit.
 */
fun audioSnapTargetsMs(clipRanges: List<Pair<Long, Long>>, projectDurationMs: Long, playheadMs: Long): List<Long> =
    buildList {
        add(0L)
        add(projectDurationMs)
        add(playheadMs)
        clipRanges.forEach { (start, end) ->
            add(start)
            add(end)
        }
    }

/**
 * [audioSnapTargetsMs] con los clips de este carril, menos [excludeClipId]
 * (el clip que se está moviendo no se imanta a sí mismo).
 */
fun AudioTrack.audioSnapTargetsMs(projectDurationMs: Long, playheadMs: Long, excludeClipId: String? = null): List<Long> =
    audioSnapTargetsMs(
        clips.filter { it.id != excludeClipId }.map { it.timelineStartMs to (it.timelineStartMs + it.clipLengthMs) },
        projectDurationMs,
        playheadMs
    )

/**
 * `true` si [timeMs] cae sobre algún rango de [clipRanges] (bordes incluidos).
 * Un toque en el carril solo es "espacio vacío" —y por tanto ofrece "+Clip",
 * doble toque para cargar o menú de pegado— cuando esto es `false`. Pura.
 */
fun isTimeOnAnyClip(clipRanges: List<Pair<Long, Long>>, timeMs: Long): Boolean =
    clipRanges.any { (start, end) -> timeMs in start..end }

/** [isTimeOnAnyClip] aplicado a los clips de este carril. */
fun AudioTrack.hasClipAtMs(timeMs: Long): Boolean =
    isTimeOnAnyClip(clips.map { it.timelineStartMs to (it.timelineStartMs + it.clipLengthMs) }, timeMs)

/**
 * Inicio (ms) de un clip nuevo pedido tocando el carril en [rawMs]: se imanta a
 * [targetsMs] (cursor, bordes de clips) y a la rejilla [gridMs] con el mismo
 * [snapToTargetsMs] del arrastre de clips, y se acota al rango del proyecto.
 * Pura: la usa el botón "+Clip" y la cubren los tests.
 */
fun snapNewClipStartMs(
    rawMs: Long,
    targetsMs: List<Long>,
    thresholdMs: Long,
    gridMs: Double,
    projectDurationMs: Long
): Long = snapToTargetsMs(
    valueMs = rawMs,
    targetsMs = targetsMs,
    thresholdMs = thresholdMs,
    gridMs = gridMs
).coerceIn(0L, projectDurationMs.coerceAtLeast(0L))

/** [snapNewClipStartMs] con los objetivos de este carril. */
fun AudioTrack.snapNewClipStartMs(
    rawMs: Long,
    projectDurationMs: Long,
    playheadMs: Long,
    thresholdMs: Long,
    gridMs: Double
): Long = snapNewClipStartMs(
    rawMs = rawMs,
    targetsMs = audioSnapTargetsMs(projectDurationMs, playheadMs),
    thresholdMs = thresholdMs,
    gridMs = gridMs,
    projectDurationMs = projectDurationMs
)

/**
 * Parte este clip en dos en el instante de TIMELINE [splitMs] (el
 * cursor de reproducción) — la "cuchilla" de la playlist de un DAW.
 *
 *  - El primero conserva el id, termina en [splitMs] y pierde el fade-out.
 *  - El segundo ([newSecondClipId]) arranca en [splitMs], continúa el
 *    audio EXACTAMENTE donde el primero lo dejó (misma regla de loop que
 *    el export: primera pasada desde `trimStartMs`, las siguientes desde
 *    el frame 0) y pierde el fade-in.
 *
 * `null` si el corte no es posible: fuera del clip, a menos de
 * [MIN_AUDIO_CLIP_LENGTH_MS] de un borde, o pasada la primera pasada de
 * un clip SIN loop (ahí solo hay silencio; no hay nada que dividir).
 */
fun AudioClip.splitAtTimelineMs(splitMs: Long, newSecondClipId: String): Pair<AudioClip, AudioClip>? {
    val offsetMs = splitMs - timelineStartMs
    if (offsetMs < MIN_AUDIO_CLIP_LENGTH_MS || clipLengthMs - offsetMs < MIN_AUDIO_CLIP_LENGTH_MS) return null
    val firstPassMs = (sourceDurationMs - trimStartMs).coerceAtLeast(0L)
    val secondTrimMs = when {
        offsetMs < firstPassMs -> trimStartMs + offsetMs
        loop && sourceDurationMs > 0L -> (offsetMs - firstPassMs) % sourceDurationMs
        else -> return null
    }.coerceAtMost((sourceDurationMs - 100L).coerceAtLeast(0L))
    val first = copy(clipLengthMs = offsetMs, fadeOutMs = 0L)
    val second = copy(
        id = newSecondClipId,
        timelineStartMs = splitMs,
        trimStartMs = secondTrimMs,
        clipLengthMs = clipLengthMs - offsetMs,
        fadeInMs = 0L
    )
    return first to second
}
