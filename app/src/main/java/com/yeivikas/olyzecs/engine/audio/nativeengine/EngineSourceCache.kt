package com.yeivikas.olyzecs.engine.audio.nativeengine

import android.content.Context
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.audio.AudioProcessor
import com.yeivikas.olyzecs.engine.audio.AudioSourceId
import com.yeivikas.olyzecs.engine.audio.AudioSourceState
import com.yeivikas.olyzecs.engine.audio.INTERMEDIATE_WAV_SUFFIX
import com.yeivikas.olyzecs.engine.audio.PreviewWavCache
import com.yeivikas.olyzecs.engine.audio.decode.GaplessWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Prepara las FUENTES de audio del motor nativo. Cada archivo del proyecto pasa por un pipeline en STREAMING:
 *
 *  1. se decodifica con el decodificador único del proyecto ([AudioProcessor.streamForAnalysis], el mismo que la exportación)
 *     y se escribe a un WAV estéreo intermedio a medida que llega ([StereoWavFileSink]): el audio nunca está entero en la JVM;
 *  2. el motor nativo recorta el retardo/relleno del codificador ([GaplessWindow]), remuestrea a la tasa fija del motor (sinc
 *     de alta calidad) y deja el WAV final en una caché en disco, que el motor MAPEA en memoria.
 *
 * Es el equivalente a las "sources" de Ardour: el clip nunca es el archivo, solo una referencia a esta fuente ya lista.
 *
 * QUIÉN ORDENA EL TRABAJO: [SourcePrepScheduler] (ver docs/adr/ADR-AUDIO-PREPARACION-DE-FUENTES.md). Aquí solo vive lo que sabe de audio: cómo se prepara una fuente
 * ([prepare]), dónde se guarda y a quién se avisa. En resumen: dos carriles (una fuente corta nunca espera a una larga), reintento
 * automático de los fallos transitorios, cancelación de lo que ya ningún clip usa, y un estado consultable por fuente
 * ([states], [retry]) para que la interfaz pueda decir qué pasa en vez de dejar un clip mudo sin explicación.
 */
internal object EngineSourceCache {
    private const val TAG = "EngineSourceCache"
    private const val MAX_FILES = 64

    /**
     * Desde esta duración una fuente va al carril LARGO. Preparar cuesta en proporción a la duración (decodificar + convertir); por debajo
     * de unos minutos es cuestión de segundos y no justifica apartarla. Una duración desconocida (0) se trata como corta.
     */
    internal const val LONG_SOURCE_MS = 5 * 60_000L

    /** Lo que hace falta para preparar una clave, y cómo se llama para la interfaz; viaja por la cola sin que ésta lo interprete. */
    private class Request(val context: Context, val uri: Uri, val ref: AudioSourceId)

    private val wavs = PreviewWavCache("elinerSources", MAX_FILES)

    private val scheduler = SourcePrepScheduler<Request>(
        work = { task -> prepare(task) },
        onSettled = { key, outcome -> settled(key, outcome) },
        onChanged = { publishStates() },
        onInternalError = { key, error -> AppLogger.e(TAG, "Un oyente falló al avisarle de la fuente $key", error) }
    )

    /** Fuentes que cada motor (propietario) usa en este momento; la unión es lo que NO se puede descartar ni purgar. */
    private val ownersLock = Any()
    private val liveKeysByOwner = HashMap<Any, Set<String>>()

    @Volatile
    private var liveKeys: Set<String> = emptySet()

    private val listeners = CopyOnWriteArraySet<(key: String, ok: Boolean) -> Unit>()

    /**
     * Identidad de una fuente. Incluye la tasa del motor: si algún día cambia, las cachés viejas no se reutilizan mal.
     * (`uri#duración` es la misma convención de `ReversedAudioCache`.)
     */
    fun keyOf(uri: Uri, durationMs: Long, engineRateHz: Int): String = "$uri#$durationMs@$engineRateHz"

    /** El WAV listo (a la tasa del motor), o `null` si aún no existe. */
    fun peek(context: Context, key: String): File? = wavs.peek(context.applicationContext, key)

    /** Estado de una fuente (por clave) que aún no está lista (`null` = no pendiente: lista, o nunca pedida). */
    fun status(key: String): SourcePrepStatus? = scheduler.status(key)

    /** `true` si la fuente agotó sus intentos (o falló de forma permanente) y no se reintenta sola. */
    fun hasFailed(key: String): Boolean = status(key) is SourcePrepStatus.Failed

    private val statesFlow = MutableStateFlow<Map<AudioSourceId, AudioSourceState>>(emptyMap())
    private val statesLock = Any()

    /**
     * Qué le pasa al audio de cada clip cuya fuente aún NO está lista (preparándose, reintentando, fallida), para que la interfaz lo
     * explique. Una fuente lista no aparece. Es un flujo de estado: el valor siempre refleja el último cambio (ver [publishStates]).
     */
    val states: StateFlow<Map<AudioSourceId, AudioSourceState>> = statesFlow.asStateFlow()

    /**
     * Publica el estado actual. La lectura y la asignación van bajo el MISMO cerrojo para que dos hilos que avisan a la vez no dejen
     * un estado viejo por encima de uno nuevo: la última llamada en entrar siempre lee la cola ya con todos los cambios anteriores.
     * (Orden de cerrojos: este y luego el del planificador; el planificador nunca toma este.)
     */
    private fun publishStates() {
        synchronized(statesLock) {
            statesFlow.value = scheduler.pending().associate { it.payload.ref to it.status.toAudioSourceState() }
        }
    }

    /**
     * Reintento pedido por el usuario sobre la fuente de [source] (si falló, o si espera su reintento automático). `true` si había algo que
     * reabrir. Al terminar bien, los oyentes ([addListener]) republican la línea de tiempo y el clip empieza a sonar.
     */
    fun retry(source: AudioSourceId): Boolean {
        val key = scheduler.pending().firstOrNull { it.payload.ref == source }?.key ?: return false
        return scheduler.retry(key)
    }

    /**
     * Se avisa a [listener] cada vez que termina un intento: `ok = true` si la fuente quedó lista; `false` si falló (consultar [status]: puede
     * estar esperando un reintento o dada por perdida). Se invoca en un hilo de fondo y SIN ningún cerrojo de esta clase tomado.
     */
    fun addListener(listener: (key: String, ok: Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (key: String, ok: Boolean) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Declara que [owner] usa exactamente [keys] y ninguna otra. Lo que ningún propietario usa ya se descarta de la cola (también las
     * fallidas: volver a importar el archivo da un intento limpio) y, si estaba en curso, se cancela. Con varios propietarios, la unión.
     */
    fun retain(owner: Any, keys: Set<String>) {
        applyLiveKeys { liveKeysByOwner[owner] = keys }
    }

    /** [owner] ya no usa nada (cierre del motor): se cancela lo que solo él necesitaba. */
    fun release(owner: Any) {
        applyLiveKeys { liveKeysByOwner.remove(owner) }
    }

    private inline fun applyLiveKeys(change: () -> Unit) {
        val union = synchronized(ownersLock) {
            change()
            liveKeysByOwner.values.flatMapTo(HashSet<String>()) { it }
        }
        liveKeys = union
        scheduler.retain(union)
    }

    /**
     * Pide preparar [uri] en segundo plano si hace falta; no hace nada si ya está lista, ya está pedida o agotó sus intentos. El aviso de que
     * terminó llega por los oyentes ([addListener]).
     */
    fun request(context: Context, uri: Uri, durationMs: Long, engineRateHz: Int) {
        val app = context.applicationContext
        val key = keyOf(uri, durationMs, engineRateHz)
        if (wavs.peek(app, key) != null) return
        val lane = if (durationMs >= LONG_SOURCE_MS) PrepLane.LONG else PrepLane.SHORT
        scheduler.request(key, Request(app, uri, AudioSourceId.of(uri, durationMs)), lane)
    }

    // ------------------------------------------------------------------ trabajo (hilos de fondo)

    private fun prepare(task: SourcePrepQueue.Task<Request>): PrepOutcome {
        val context = task.payload.context
        val uri = task.payload.uri
        return try {
            prepareFile(context, uri, task)
        } catch (error: Throwable) {
            val outcome = classifyPrepFailure(error)
            if (outcome !is PrepOutcome.Cancelled) AppLogger.e(TAG, "No se pudo preparar la fuente de audio: $uri", error)
            outcome
        }
    }

    private fun prepareFile(context: Context, uri: Uri, task: SourcePrepQueue.Task<Request>): PrepOutcome {
        val key = task.key
        // Un intento anterior pudo dejar el archivo listo antes de fallar al avisar, o la misma fuente se pidió desde otra sesión.
        if (wavs.peek(context, key) != null) return PrepOutcome.Success

        val target = wavs.targetFile(context, key)
        val intermediate = File(target.parentFile, target.name + INTERMEDIATE_WAV_SUFFIX)
        try {
            val sink = StereoWavFileSink(intermediate)
            val info = sink.use { AudioProcessor.streamForAnalysis(context, uri, it, task::isCancelled) }
            // `null` con la cancelación activa NO es un fallo (el decodificador no registra nada en ese caso).
            if (task.isCancelled()) return PrepOutcome.Cancelled
            if (info == null) {
                return PrepOutcome.Transient("No se pudo decodificar el audio (archivo dañado, formato no soportado o sin permiso de lectura)")
            }
            val window = GaplessWindow.of(sink.framesWritten, info.encoderDelayFrames, info.encoderPaddingFrames)
            val error = NativeEngineBindings.nativeConvertWavToEngineRate(
                intermediate.path, target.path, window.startFrame, window.frameCount
            )
            if (error != null) return PrepOutcome.Transient("Conversión de tasa fallida: $error")
        } finally {
            intermediate.delete()
        }
        // Protegidas: las fuentes que algún motor sigue usando (la purga LRU jamás debe borrar una que suena) y la recién escrita.
        wavs.finishExternalWrite(context, key, liveKeys)
        return PrepOutcome.Success
    }

    private fun settled(key: String, outcome: PrepOutcome) {
        when (outcome) {
            is PrepOutcome.Transient, is PrepOutcome.Permanent -> logFailure(key, outcome)
            else -> Unit
        }
        val ok = outcome is PrepOutcome.Success
        for (listener in listeners) listener(key, ok)
    }

    private fun logFailure(key: String, outcome: PrepOutcome) {
        val reason = (outcome as? PrepOutcome.Transient)?.reason ?: (outcome as? PrepOutcome.Permanent)?.reason.orEmpty()
        when (val status = scheduler.status(key)) {
            is SourcePrepStatus.Retrying -> AppLogger.w(TAG, "Fuente $key: intento fallido ($reason); se reintenta (intento ${status.attempt}).")
            is SourcePrepStatus.Failed -> AppLogger.w(TAG, "Fuente $key: dada por perdida tras ${status.attempts} intento(s): $reason")
            else -> AppLogger.w(TAG, "Fuente $key: intento fallido ($reason).")
        }
    }
}
