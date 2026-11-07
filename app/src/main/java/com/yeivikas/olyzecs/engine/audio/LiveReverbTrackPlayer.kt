package com.yeivikas.olyzecs.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.audio.fx.LiveReverbStream
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

private const val TAG = "LiveReverbTrackPlayer"

/** Muestras por canal que procesa y escribe el hilo de audio por vuelta (~23 ms a 44.1 kHz). */
private const val BLOCK_FRAMES = LiveReverbStream.DEFAULT_MAX_BLOCK_FRAMES

private const val CHANNELS = 2
private const val BYTES_PER_FRAME = CHANNELS * 2

/**
 * Tamaño inicial del búfer del `AudioTrack` en bloques (~116 ms a 44.1 kHz): lo que tarda en oírse un cambio de parámetros y
 * el margen contra cortes. Si el dispositivo aun así se queda sin audio ([AudioTrack.getUnderrunCount]) crece de un bloque
 * en un bloque hasta [MAX_BUFFER_BLOCKS]: la latencia mínima que ESTE dispositivo sostiene sin entrecortarse.
 */
private const val INITIAL_BUFFER_BLOCKS = 5

/** Capacidad del `AudioTrack` en bloques (~278 ms): el techo al que puede crecer el búfer. */
private const val MAX_BUFFER_BLOCKS = 12

/** Bloques que se escriben ANTES de `play()` para que el arranque no empiece sin margen (se acota al búfer real: ver [prefillBlocks]). */
private const val PREFILL_BLOCKS = 3

/** Cada cuánto despierta el hilo de audio, ya sin audio que entregar (pasó el final y la cola), para atender órdenes nuevas. */
private const val IDLE_WAKE_NANOS = 20_000_000L

/**
 * Calentamiento del motor al crearse el reproductor: el hilo de audio procesa hasta este tiempo / bloques de audio real en
 * silencio, para que el entorno de ejecución compile el DSP ANTES del primer Play. Frío, el DSP se interpreta y puede tardar
 * más que lo que dura un bloque: los primeros bloques tras el primer Play llegarían tarde y se oiría entrecortado.
 */
private const val WARM_UP_MAX_NANOS = 400_000_000L
private const val WARM_UP_BLOCKS = 96

/**
 * Tope de tiempo del calentamiento del reverb al arrancar o reposicionarse. El audio no puede empezar hasta terminarlo, así que
 * en un dispositivo lento ([LiveReverbStream.seekTo] mide su velocidad) se calienta solo el tramo previo que cabe: el audio entra
 * a tiempo, con algo menos de cola al principio, en vez de entrar tarde por esperar un calentamiento completo.
 */
private const val SEEK_WARMUP_BUDGET_NANOS = 120_000_000L

/** Tope de bloques que se descartan al arrancar para alcanzar al cabezal (~4.6 s): si el motor no es más rápido que el tiempo real, no se persigue indefinidamente. */
private const val MAX_CATCH_UP_BLOCKS = 200

/** Atraso (ms) con el que se da por alcanzado al cabezal: bajo el umbral de lo audible entre carriles, y por encima de lo que cuesta un bloque corto. */
private const val CATCH_UP_TOLERANCE_MS = 4

/** Bits de la posición publicada: la muestra ocupa los bajos (2⁴⁴ muestras ≈ 12 000 años a 44.1 kHz), el número de orden los altos. */
private const val FRAME_BITS = 44
private const val FRAME_MASK = (1L shl FRAME_BITS) - 1L
private const val SEQUENCE_MASK = (1L shl (Long.SIZE_BITS - FRAME_BITS)) - 1L

/**
 * Reproduce UN carril con Reverb en tiempo real: lee su mezcla SECA ([DryTrackMix]) por bloques desde disco
 * ([WavFilePcmSource]), la pasa por el motor ([LiveReverbStream], la misma matemática que la exportación) y la entrega a un
 * `AudioTrack` en streaming. Cambiar un parámetro del Reverb ([setParams]) se oye en una fracción de segundo y no renderiza
 * nada. Está atado a UN archivo seco: si la mezcla seca cambia, [AudioPreviewPlayers] crea otro reproductor.
 *
 * HILOS. Todo el trabajo de audio vive en UN hilo propio de prioridad de audio (no el principal): es el único que toca el
 * `AudioTrack`, el [LiveReverbStream] y el archivo, así que ninguno necesita ser seguro entre hilos. El hilo principal
 * (desde donde se llama TODA esta API) se comunica con él sin bloquearlo: órdenes en referencias atómicas, de las que gana
 * la última, y `LockSupport` para despertarlo mientras está en pausa. La posición que suena la publica el hilo de audio.
 *
 * ÓRDENES. [start] y [pause] son idempotentes y [seekTo] solo actúa si está en marcha: quien sincroniza con el cabezal
 * ([planLiveSync]) puede llamarlas en cada tick. Arrancar SIEMPRE posiciona (descarta lo que había en el búfer): no hay
 * "reanudar" sin posición, y así no se oye audio viejo al volver de una pausa.
 *
 * SINCRONÍA. Atender un arranque o un seek tarda (calentar el reverb con el audio previo, llenar el búfer) y el cabezal del
 * proyecto no espera. Por eso cada orden lleva la hora a la que se dio y, tras posicionar, el hilo de audio DESCARTA (procesa
 * sin entregar) lo que el cabezal ya avanzó ([catchUpFrames]): el audio nace donde está el cabezal, no atrasado por lo que
 * costó arrancar. Sin esto el atraso se tomaba por deriva y se corregía con seeks en cadena, que se oían como cortes.
 *
 * ROBUSTEZ. El motor se calienta al crearse ([warmUp]); el `AudioTrack` usa el modo estándar (no `LOW_LATENCY`: aquí importa
 * no cortarse, no la latencia mínima, y los carriles rápidos del sistema son escasos); el búfer crece solo si el dispositivo
 * demuestra que lo necesita.
 *
 * FALLOS. Cualquier error del hilo de audio (dispositivo, E/S) lo detiene, libera todo y deja [hasFailed] en `true`; quien
 * lo creó decide cómo seguir (el carril cae al reproductor de siempre). El constructor lanza si el WAV no es válido.
 *
 * @param dryFile WAV de la mezcla seca ([WavFilePcmSource]).
 * @param initialParams parámetros iniciales del Reverb.
 */
internal class LiveReverbTrackPlayer(val dryFile: File, initialParams: ReverbParams) {
    private val source = WavFilePcmSource(dryFile)

    /** Tasa del audio seco (Hz): la del `AudioTrack` y la de la conversión cabezal → muestra ([frameAtMs]). */
    val sampleRateHz: Int = source.sampleRateHz

    private val stream: LiveReverbStream = try {
        LiveReverbStream(source, sampleRateHz, initialParams, BLOCK_FRAMES)
    } catch (t: Throwable) {
        source.close()
        throw t
    }

    // --- Órdenes del hilo principal al de audio (la última gana) -----------------------------------------------------
    private sealed interface Command {
        /** Arrancar (o reiniciar) en [frame]. [sequence] identifica la orden; [issuedAtNanos] es cuándo se dio (para alcanzar al cabezal). */
        class Start(val sequence: Long, val frame: Long, val issuedAtNanos: Long) : Command
        class Seek(val sequence: Long, val frame: Long, val issuedAtNanos: Long) : Command
        data object Pause : Command
    }

    private val command = AtomicReference<Command?>(null)
    private val pendingParams = AtomicReference<ReverbParams?>(null)

    // --- Estado del hilo principal ---------------------------------------------------------------------------------
    private var requestedParams = initialParams
    private var issuedSequence = 0L
    private var optimisticFrame = 0L
    private var commandedRunning = false
    private var repositionedAtNanos = System.nanoTime()

    // --- Publicado por el hilo de audio -----------------------------------------------------------------------------
    @Volatile private var released = false
    @Volatile private var failed = false
    @Volatile private var totalFramesValue = stream.totalFrames

    /** `true` desde que el dispositivo recibió audio y arrancó, hasta que se calla o se libera (ver [isProducing]). */
    @Volatile private var producing = false

    /** Posición que suena, empaquetada con el número de la orden que la produjo (ver [audibleFrame]). */
    @Volatile private var positionWord = 0L

    private val thread = Thread(::renderLoop, "OlyzeLiveReverb")

    init {
        thread.isDaemon = true
        thread.start()
    }

    /** Largo total reproducible: el audio seco más la cola del reverb con los parámetros vigentes. */
    val totalFrames: Long get() = totalFramesValue

    /** `true` si se le ordenó arrancar y no se pausó (aunque el hilo de audio aún esté atendiendo la orden). */
    val isRunning: Boolean get() = commandedRunning && !failed

    /** `true` si el hilo de audio terminó por un error: este reproductor ya no sirve y debe soltarse. */
    val hasFailed: Boolean get() = failed

    /**
     * `true` mientras el dispositivo está entregando el audio de este reproductor. Es lo que espera quien lo REEMPLAZA a otro
     * (o al `MediaPlayer`) para soltar el sonido anterior: soltarlo antes dejaba un hueco de silencio del tamaño de lo que
     * tarda en arrancar el nuevo ([handoverComplete]).
     */
    val isProducing: Boolean get() = producing && !failed

    /** Milisegundos desde el último arranque o reposicionamiento ordenado (alimenta la espera de [planLiveSync]). */
    val msSinceReposition: Long get() = (System.nanoTime() - repositionedAtNanos) / 1_000_000L

    /**
     * Muestra que suena ahora. Entre una orden de posicionamiento y la primera posición real del hilo de audio devuelve la
     * muestra pedida: nunca una posición anterior a la orden, que se tomaría por desvío y provocaría otro seek.
     */
    val audibleFrame: Long
        get() {
            val word = positionWord
            return if ((word ushr FRAME_BITS) == (issuedSequence and SEQUENCE_MASK)) word and FRAME_MASK else optimisticFrame
        }

    /** Cambia los parámetros del Reverb en vivo (también en pausa: quedan aplicados para el próximo arranque). */
    fun setParams(params: ReverbParams) {
        if (params == requestedParams) return
        requestedParams = params
        pendingParams.set(params)
        wake()
    }

    /** Arranca en [frame] (muestra por canal), descartando lo que hubiera en el búfer. */
    fun start(frame: Long) {
        if (failed) return
        val target = frame.coerceAtLeast(0L)
        val sequence = issue(target)
        commandedRunning = true
        command.set(Command.Start(sequence, target, repositionedAtNanos))
        wake()
    }

    /** Reposiciona en [frame] SI está en marcha (parado no hace nada: el próximo [start] ya posiciona). */
    fun seekTo(frame: Long) {
        if (!commandedRunning || failed) return
        val target = frame.coerceAtLeast(0L)
        val sequence = issue(target)
        val issuedAt = repositionedAtNanos
        // Un arranque aún sin atender sigue siendo un arranque, ahora en la nueva muestra: convertirlo en seek lo perdería.
        command.updateAndGet { pending ->
            if (pending is Command.Start) Command.Start(sequence, target, issuedAt) else Command.Seek(sequence, target, issuedAt)
        }
        wake()
    }

    /** Lo calla (descarta el búfer). Idempotente. */
    fun pause() {
        if (!commandedRunning) return
        commandedRunning = false
        command.set(Command.Pause)
        wake()
    }

    /** Detiene el hilo de audio y libera el `AudioTrack` y el archivo (lo hace el propio hilo al salir). Idempotente; no bloquea. */
    fun release() {
        released = true
        commandedRunning = false
        producing = false
        wake()
    }

    /** Registra una orden de posicionamiento en [targetFrame] y devuelve su número. */
    private fun issue(targetFrame: Long): Long {
        issuedSequence++
        optimisticFrame = targetFrame
        repositionedAtNanos = System.nanoTime()
        return issuedSequence
    }

    private fun wake() = LockSupport.unpark(thread)

    // ==============================================================================================================
    // HILO DE AUDIO
    // ==============================================================================================================

    private fun renderLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var audioTrack: AudioTrack? = null
        try {
            val output = createAudioTrack(sampleRateHz).also { audioTrack = it }
            val head = HeadPositionCounter()
            val block = ShortArray(BLOCK_FRAMES * CHANNELS)
            var running = false
            var appliedSequence = 0L
            var baseFrame = 0L
            var writtenFrames = 0L
            var reportedUnderruns = 0
            val load = DspLoadMeter { average, peak ->
                AppLogger.i(
                    TAG,
                    "Carga del DSP del carril con Reverb: media ${Math.round(average * 100)} %, pico ${Math.round(peak * 100)} % " +
                        "(100 % = no alcanza el tiempo real y habrá cortes)"
                )
            }

            warmUp(block)

            /** Calla el dispositivo y descarta el búfer; la posición del contador vuelve a cero con el `flush`. */
            fun silence() {
                producing = false
                output.pause()
                output.flush()
                head.reset()
            }

            /** Entrega [frames] muestras de [block] al dispositivo y las cuenta (la posición que suena nunca pasa de lo escrito). */
            fun deliver(frames: Int) {
                writeFully(output, block, frames * CHANNELS)
                writtenFrames += frames
            }

            /** Muestra que suena: el contador del dispositivo, acotado por lo escrito desde el último posicionamiento (tras un `flush` algunos dispositivos devuelven un instante el valor viejo). */
            fun audibleNow(): Long = baseFrame + minOf(head.update(output.playbackHeadPosition), writtenFrames)

            /** Si el dispositivo se quedó sin audio, agranda el búfer un bloque (hasta su capacidad) y lo registra. */
            fun adaptToUnderruns() {
                val total = output.underrunCount
                if (total < reportedUnderruns) reportedUnderruns = total // el `flush` reinició el contador del dispositivo
                if (total > reportedUnderruns) {
                    val size = output.setBufferSizeInFrames(minOf(output.bufferCapacityInFrames, output.bufferSizeInFrames + BLOCK_FRAMES))
                    AppLogger.w(TAG, "Cortes de audio en el carril con Reverb: ${total - reportedUnderruns}; búfer ahora de $size muestras (bloque de $BLOCK_FRAMES)")
                    reportedUnderruns = total
                }
            }

            /** Posiciona el motor en [frame], alcanza al cabezal, llena el inicio del búfer y arranca el dispositivo. */
            fun begin(sequence: Long, frame: Long, issuedAtNanos: Long) {
                silence()
                stream.seekTo(frame, SEEK_WARMUP_BUDGET_NANOS)
                catchUp(frame, issuedAtNanos, block)
                baseFrame = stream.positionFrames
                writtenFrames = 0L
                appliedSequence = sequence
                repeat(prefillBlocks(output)) {
                    val n = stream.read(block, BLOCK_FRAMES)
                    if (n > 0) deliver(n)
                }
                output.play()
                running = true
                producing = true
            }

            while (!released) {
                pendingParams.getAndSet(null)?.let { params ->
                    stream.setParams(params)
                    totalFramesValue = stream.totalFrames
                }
                when (val order = command.getAndSet(null)) {
                    is Command.Start -> begin(order.sequence, order.frame, order.issuedAtNanos)
                    is Command.Seek -> if (running) begin(order.sequence, order.frame, order.issuedAtNanos)
                    Command.Pause -> if (running) {
                        silence()
                        running = false
                    }
                    null -> Unit
                }
                if (!running) {
                    LockSupport.park(this)
                    continue
                }
                val readStartedAt = System.nanoTime()
                val frames = stream.read(block, BLOCK_FRAMES)
                if (frames > 0) load.record(System.nanoTime() - readStartedAt, frames, sampleRateHz)
                if (frames == 0) {
                    // Pasó el final del audio y su cola: no hay nada que entregar; se espera una orden (o que el cabezal lo pause).
                    publishPosition(appliedSequence, audibleNow())
                    LockSupport.parkNanos(this, IDLE_WAKE_NANOS)
                    continue
                }
                deliver(frames)
                publishPosition(appliedSequence, audibleNow())
                adaptToUnderruns()
            }
        } catch (t: Throwable) {
            failed = true
            producing = false
            AppLogger.e(TAG, "El reproductor en vivo del carril con Reverb se detuvo: ${dryFile.name}", t)
        } finally {
            released = true
            producing = false
            audioTrack?.let { output ->
                try {
                    output.pause()
                    output.flush()
                } catch (t: Throwable) {
                    AppLogger.w(TAG, "No se pudo detener el AudioTrack del reproductor en vivo", t)
                }
                output.release()
            }
            source.close()
        }
    }

    /**
     * Procesa en silencio audio real del inicio del archivo para que el DSP quede compilado antes del primer Play, y deja el
     * motor en el inicio. Cede en cuanto llega una orden (el usuario ya pulsó Play: lo suyo va primero) o pasa el tiempo.
     */
    private fun warmUp(block: ShortArray) {
        val deadline = System.nanoTime() + WARM_UP_MAX_NANOS
        var blocks = 0
        while (blocks < WARM_UP_BLOCKS && !released && command.get() == null && System.nanoTime() < deadline) {
            if (stream.read(block, BLOCK_FRAMES) == 0) break
            blocks++
        }
        stream.seekTo(0L)
    }

    /**
     * Tras posicionar el motor en [frame], descarta (procesando, para que el reverb ya suene) lo que el cabezal avanzó desde
     * que se dio la orden a [issuedAtNanos]: así el audio que se entregue a continuación es el de ESTE instante. Acotado a
     * [MAX_CATCH_UP_BLOCKS] por si el motor no fuera más rápido que el tiempo real.
     */
    private fun catchUp(frame: Long, issuedAtNanos: Long, block: ShortArray) {
        val toleranceFrames = (sampleRateHz * CATCH_UP_TOLERANCE_MS / 1_000).coerceAtLeast(1)
        repeat(MAX_CATCH_UP_BLOCKS) {
            val behind = frame + catchUpFrames(System.nanoTime() - issuedAtNanos, sampleRateHz) - stream.positionFrames
            if (behind <= toleranceFrames) return
            if (stream.read(block, minOf(behind, BLOCK_FRAMES.toLong()).toInt()) == 0) return
        }
    }

    /** Bloques que se escriben antes de `play()`: [PREFILL_BLOCKS], sin llegar nunca a la capacidad real del búfer (escribir de más con el dispositivo parado bloquearía el hilo). */
    private fun prefillBlocks(output: AudioTrack): Int =
        minOf(PREFILL_BLOCKS, maxOf(1, output.bufferSizeInFrames / BLOCK_FRAMES - 1))

    /** Publica la muestra que suena, acotada al largo total, con el número de la orden vigente. */
    private fun publishPosition(sequence: Long, frame: Long) {
        val bounded = frame.coerceIn(0L, totalFramesValue)
        positionWord = ((sequence and SEQUENCE_MASK) shl FRAME_BITS) or (bounded and FRAME_MASK)
    }

    /** Escribe todos los [valueCount] valores de [block] (bloqueando hasta que el dispositivo los acepte). */
    private fun writeFully(output: AudioTrack, block: ShortArray, valueCount: Int) {
        var offset = 0
        while (offset < valueCount) {
            val written = output.write(block, offset, valueCount - offset, AudioTrack.WRITE_BLOCKING)
            check(written >= 0) { "AudioTrack.write falló con el código $written" }
            offset += written
        }
    }

    private fun createAudioTrack(sampleRateHz: Int): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBytes > 0) { "El dispositivo no admite audio estéreo de 16 bits a $sampleRateHz Hz (código $minBytes)" }
        val capacityBytes = maxOf(minBytes, MAX_BUFFER_BLOCKS * BLOCK_FRAMES * BYTES_PER_FRAME)
        val output = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(capacityBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
            .build()
        if (output.state != AudioTrack.STATE_INITIALIZED) {
            output.release()
            error("El AudioTrack no se pudo inicializar a $sampleRateHz Hz")
        }
        // La capacidad es el techo; el búfer en uso empieza corto (latencia baja) y crece solo si hace falta.
        output.setBufferSizeInFrames(maxOf(INITIAL_BUFFER_BLOCKS * BLOCK_FRAMES, minBytes / BYTES_PER_FRAME))
        return output
    }
}
