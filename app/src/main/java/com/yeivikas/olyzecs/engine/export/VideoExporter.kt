package com.yeivikas.olyzecs.engine.export

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.animation.SpeedRampEngine
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.audio.AudioProcessor
import com.yeivikas.olyzecs.engine.render.GpuTextureLimits
import com.yeivikas.olyzecs.engine.render.LayerDrawer
import com.yeivikas.olyzecs.engine.scene.Layer
import java.io.File
import java.nio.ByteBuffer

private const val TAG = "VideoExporter"

/** Fracción de la barra de progreso reservada para la fase de audio (decode+encode AAC), antes de que arranque el loop de video. */
private const val AUDIO_PHASE_FRACTION = 0.15f

/**
 * Umbral de AVISO (no de límite duro) para la memoria de GPU combinada que
 * ocupan todas las texturas de un export a la vez. 512MB es deliberadamente
 * conservador: en un dispositivo con arquitectura de memoria unificada
 * (la gran mayoría de GPUs móviles, donde VRAM y RAM del sistema son el
 * mismo pool físico), cruzar este número junto con el resto de la app y el
 * propio sistema operativo activos empieza a ser una zona real de riesgo
 * de quedarse sin memoria — sin ser todavía, en la mayoría de equipos de
 * gama media/alta de hoy, un límite que vaya a fallar seguro. Por eso es
 * un AVISO en el registro, no un recorte automático de calidad: bajar
 * resolución sin que el usuario lo decida sería una call de producto, no
 * una de ingeniería, y no corresponde tomarla en silencio acá.
 */
private const val GPU_TEXTURE_BUDGET_WARNING_BYTES = 512L * 1024L * 1024L

/**
 * Parámetros de exportación. [widthPx]/[heightPx] normalmente se calculan
 * con [computeExportDimensions] a partir de [quality] y un aspecto elegido
 * en la UI, pero quedan como campos propios para no atar el motor a esos
 * enums si en el futuro hace falta un tamaño custom.
 */
data class ExportSettings(
    val widthPx: Int = 1080,
    val heightPx: Int = 1920,
    val fps: Int = 30,
    val bitRate: Int = 8_000_000,
    val durationMs: Long = 8000L
)

sealed class ExportProgress {
    data class InProgress(val fraction: Float, val audioPhaseFraction: Float = 0f) : ExportProgress()
    data class Done(
        val outputFile: File,
        /** Si el proyecto tenía un audioClip activo (no muteado) al exportar. */
        val audioRequested: Boolean = false,
        /** Si ese audio efectivamente terminó muxeado en el archivo final. */
        val hasAudio: Boolean = false,
        /** Motivo legible de por qué el audio no se pudo incluir, si audioRequested && !hasAudio. */
        val audioFailureReason: String? = null
    ) : ExportProgress()
    data class Failed(val error: Throwable) : ExportProgress()
    /**
     * DISEÑO — auditoría: el usuario canceló una exportación en curso
     * (ver [isCancelled] en [VideoExporter.export]). Estado propio,
     * distinto de [Failed]: cancelar a pedido del usuario no es un error
     * del motor, y tratarlo como tal en la UI mostraría un mensaje de
     * "Error al exportar" engañoso para algo que el usuario mismo pidió.
     */
    data object Cancelled : ExportProgress()
}

/**
 * Exportador offline: recorre el timeline completo del proyecto, dibuja
 * cada frame con el mismo [LayerDrawer] que usa el preview en vivo, y lo
 * entrega a un MediaCodec configurado con una Surface de entrada (el
 * patrón estándar y más eficiente de codificación en Android: GL escribe
 * directo a la superficie que el encoder consume, sin copias por CPU).
 *
 * No depende de los bitmaps ya subidos en el contexto GL del preview
 * (que vive en otro contexto EGL y puede estar reciclado); vuelve a
 * decodificar cada imagen desde su [Layer.sourceUri] para el export.
 */
class VideoExporter(private val context: Context) {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun export(
        layers: List<Layer>,
        settings: ExportSettings,
        outputFile: File,
        audioClip: AudioClip? = null,
        // Duración del timeline BASE (donde viven los keyframes de
        // cámara) antes de aplicar rampas de velocidad/freezes. Por
        // defecto igual a settings.durationMs — o sea, sin rampas, el
        // comportamiento es IDÉNTICO al de siempre.
        baseDurationMs: Long = settings.durationMs,
        speedKeyframes: List<SpeedKeyframe> = emptyList(),
        freezeFrames: List<FreezeFrame> = emptyList(),
        // DISEÑO — auditoría: `export()` es una función síncrona de larga
        // duración que corre sobre un solo hilo, SIN ningún punto de
        // suspensión (`suspend`) adentro — así que la cancelación normal
        // de corrutinas de Kotlin (que solo interrumpe en un punto de
        // suspensión) NUNCA la alcanzaría por sí sola: un `Job.cancel()`
        // de afuera no tendría ningún efecto hasta que este método
        // terminara solo, por su cuenta, sin importar cuánto durara. Por
        // eso este chequeo es EXPLÍCITO y cooperativo: quien llama pasa un
        // lambda que en cualquier momento puede empezar a devolver `true`
        // (ver `ExportApiImpl`, que lo conecta al `isActive` real de la
        // corrutina de export) y acá se lo consulta una vez por frame — la
        // única granularidad con sentido, ya que cancelar a mitad de un
        // solo frame no ahorra nada perceptible.
        isCancelled: () -> Boolean = { false },
        onProgress: (ExportProgress) -> Unit
    ) {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        val drawer = LayerDrawer()
        val uploadedTextures = mutableMapOf<String, Triple<Int, Int, Int>>() // layerId -> (textureId, w, h)
        // Se marca en `true` SOLO si `isCancelled()` disparó durante el
        // loop — el `finally` de más abajo lo usa para decidir si borrar
        // el archivo de salida a medio escribir (nunca dejar un .mp4
        // incompleto, sin trailer válido, dando vueltas como si fuera un
        // video utilizable).
        var wasCancelled = false

        try {
            // El audio se procesa ANTES de tocar nada de video: si falla, se seguirá
            // exportando el video solo (buildEncodedTrackForProject nunca lanza —
            // atrapa sus propios errores y devuelve null — así un audio corrupto o
            // un formato no soportado no puede tirar abajo la exportación entera).
            // El procesamiento de audio (decode + encode a AAC) puede tardar bastante
            // con archivos largos, y antes no reportaba nada — la barra se quedaba
            // "clavada" en 0% dando la sensación de que la app estaba colgada.
            // Se le reserva el primer AUDIO_PHASE_FRACTION de la barra y se reporta
            // progreso real (frames codificados / total) durante esa fase.
            val hasAudioToProcess = audioClip != null && !audioClip.muted
            val audioPhaseFraction = if (hasAudioToProcess) AUDIO_PHASE_FRACTION else 0f
            val encodedAudio = audioClip?.let {
                AudioProcessor.buildEncodedTrackForProject(context, it, settings.durationMs, isCancelled = isCancelled) { audioFraction ->
                    onProgress(ExportProgress.InProgress(audioFraction * audioPhaseFraction, audioPhaseFraction = audioPhaseFraction))
                }
            }
            // AUDITORÍA — hallazgo cerrado: el botón "Cancelar" está
            // visible en la UI también durante "Procesando audio…" (ver
            // ExportDialog), pero hasta esta revisión `isCancelled` solo
            // se consultaba dentro del loop de frames de VIDEO, más
            // abajo — con un audioClip largo, cancelar durante esa fase no
            // tenía ningún efecto real hasta que el audio terminaba solo.
            // Ahora `buildEncodedTrackForProject` (y `encodeToAac` adentro)
            // también consultan `isCancelled` durante su propio trabajo, y
            // ACÁ se corta la exportación entera apenas la fase de audio
            // vuelve — sea porque terminó normal o porque se cortó a mitad
            // de camino — en vez de seguir camino y arrancar a codificar
            // video que de todos modos se va a descartar.
            if (isCancelled()) {
                AppLogger.i(TAG, "Exportación cancelada por el usuario durante el procesamiento de audio")
                wasCancelled = true
                onProgress(ExportProgress.Cancelled)
                return
            }
            val audioFailureReason = if (hasAudioToProcess && encodedAudio == null) {
                AppLogger.consumeLastUserFacingError().also {
                    AppLogger.w(TAG, "Había audioClip activo pero no se pudo incluir en el export: $it")
                }
            } else null
            var audioTrackIndex = -1

            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, settings.widthPx, settings.heightPx
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, settings.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, settings.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val inputSurface = codec.createInputSurface()
            codec.start()

            setupEgl(inputSurface, settings.widthPx, settings.heightPx)
            drawer.ensureInitialized()

            // AUDITORÍA — hallazgo corregido: la primera versión de este
            // comentario decía que "todas las capas quedan decodificadas en
            // RAM a la vez antes de exportar" — ESO ERA INCORRECTO, y se
            // corrige acá para que el comentario no mienta sobre lo que el
            // código realmente hace: cada capa se decodifica, se sube a GPU
            // y se recicla (`bitmap.recycle()`) EN LA MISMA iteración, antes
            // de pasar a la siguiente — el pico de RAM de CPU es de UNA sola
            // imagen a la vez, nunca de todas juntas.
            //
            // Lo que sí es un riesgo real, y no estaba antes cubierto: una
            // vez subidas, las texturas de TODAS las capas quedan
            // residentes en memoria de GPU durante el export completo —
            // necesario, porque cada frame dibuja todas las capas visibles.
            // `GpuTextureLimits.clampForTexture` acota el tamaño de CADA
            // textura individual al máximo que soporta la GPU, pero no
            // suma el total combinado entre capas. `estimatedGpuBytes`
            // lleva ese total y avisa (sin cambiar ningún comportamiento
            // visual ni bajar calidad por decisión propia) si un proyecto
            // con muchas capas pesadas se acerca a una zona de riesgo real
            // de memoria de GPU compartida — para que, si algún día un
            // export así falla en un equipo real, quede escrito por qué en
            // vez de un crash mudo de `glTexImage2D` o del driver.
            var estimatedGpuBytes = 0L
            for (layer in layers) {
                val original = context.contentResolver.openInputStream(layer.sourceUri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: continue
                val bitmap = GpuTextureLimits.clampForTexture(original)
                val textureId = drawer.uploadTexture(bitmap)
                uploadedTextures[layer.id] = Triple(textureId, bitmap.width, bitmap.height)
                estimatedGpuBytes += bitmap.width.toLong() * bitmap.height.toLong() * 4L
                bitmap.recycle()
            }
            if (estimatedGpuBytes > GPU_TEXTURE_BUDGET_WARNING_BYTES) {
                AppLogger.w(
                    TAG,
                    "Memoria de GPU estimada para este export: ${estimatedGpuBytes / (1024 * 1024)}MB " +
                        "en ${uploadedTextures.size} textura(s) residentes a la vez — por encima del umbral " +
                        "de aviso (${GPU_TEXTURE_BUDGET_WARNING_BYTES / (1024 * 1024)}MB). No se reduce " +
                        "calidad automáticamente; esto queda como diagnóstico para investigar si el export " +
                        "falla o el dispositivo se queda sin memoria en proyectos con muchas capas pesadas."
                )
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            val timeMapping = SpeedRampEngine.buildTimeMapping(
                baseDurationMs = baseDurationMs,
                fps = settings.fps,
                speedKeyframes = speedKeyframes,
                freezeFrames = freezeFrames
            )
            val totalFrames = timeMapping.size
            val sortedLayers = layers.sortedBy { it.zIndex }

            for (frameIndex in 0 until totalFrames) {
                if (isCancelled()) {
                    // Corta ACÁ, antes de dibujar/codificar un frame más —
                    // no se llama a `signalEndOfInputStream()` ni se
                    // escribe el audio pendiente: el archivo va a quedar
                    // sin trailer válido de MP4, así que se lo borra en el
                    // `finally` (ver `wasCancelled` más abajo), en vez de
                    // dejarlo tirado como si fuera un video reproducible.
                    AppLogger.i(TAG, "Exportación cancelada por el usuario en el frame $frameIndex de $totalFrames")
                    wasCancelled = true
                    onProgress(ExportProgress.Cancelled)
                    return
                }
                val timeMs = timeMapping[frameIndex]

                drawer.clear()
                for (layer in sortedLayers) {
                    if (!layer.visible) continue
                    val (textureId, w, h) = uploadedTextures[layer.id] ?: continue
                    val frame = layer.cameraTrack.frameAt(timeMs)
                    val previousFrame = if (layer.lookSettings.motionBlurIntensity > 0.001f) {
                        layer.cameraTrack.frameAt((timeMs - 33L).coerceAtLeast(0L))
                    } else null
                    drawer.drawLayer(
                        textureId = textureId,
                        imageWidthPx = w,
                        imageHeightPx = h,
                        frame = frame,
                        parallaxFactor = layer.parallaxFactor,
                        viewportWidth = settings.widthPx,
                        viewportHeight = settings.heightPx,
                        look = layer.lookSettings,
                        timeSeconds = timeMs / 1000f,
                        previousFrame = previousFrame
                    )
                }

                val presentationTimeNs = frameIndex.toLong() * 1_000_000_000L / settings.fps
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                muxerStarted = drainEncoder(
                    codec, muxer, bufferInfo, endOfStream = false,
                    muxerTrackIndexRef = { muxerTrackIndex },
                    setTrackIndex = { muxerTrackIndex = it },
                    muxerStarted = muxerStarted,
                    beforeMuxerStart = {
                        if (encodedAudio != null && audioTrackIndex < 0) {
                            audioTrackIndex = muxer?.addTrack(encodedAudio.format) ?: -1
                        }
                    }
                )

                val videoFraction = (frameIndex + 1).toFloat() / totalFrames
                onProgress(ExportProgress.InProgress(audioPhaseFraction + (1f - audioPhaseFraction) * videoFraction))
            }

            codec.signalEndOfInputStream()
            drainEncoder(
                codec, muxer, bufferInfo, endOfStream = true,
                muxerTrackIndexRef = { muxerTrackIndex },
                setTrackIndex = { muxerTrackIndex = it },
                muxerStarted = muxerStarted,
                beforeMuxerStart = {
                    if (encodedAudio != null && audioTrackIndex < 0) {
                        audioTrackIndex = muxer?.addTrack(encodedAudio.format) ?: -1
                    }
                }
            )

            // El audio ya está completamente pre-codificado en memoria (ver
            // AudioProcessor): se escribe entero recién acá, después del video.
            // Es seguro porque son tracks independientes dentro del mismo
            // contenedor .mp4 — el reproductor sincroniza ambos por su propio
            // presentationTimeUs, no por el orden en que se escribieron.
            if (encodedAudio != null && audioTrackIndex >= 0) {
                for (chunk in encodedAudio.chunks) {
                    val buffer = ByteBuffer.wrap(chunk.data)
                    muxer?.writeSampleData(audioTrackIndex, buffer, chunk.info)
                }
            }

            onProgress(ExportProgress.Done(
                outputFile,
                audioRequested = hasAudioToProcess,
                hasAudio = encodedAudio != null && audioTrackIndex >= 0,
                audioFailureReason = audioFailureReason
            ))
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error exportando video", t)
            onProgress(ExportProgress.Failed(t))
        } finally {
            for ((textureId, _, _) in uploadedTextures.values) {
                drawer.deleteTexture(textureId)
            }
            try { muxer?.stop() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo detener el MediaMuxer en la limpieza", e) }
            try { muxer?.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el MediaMuxer en la limpieza", e) }
            try { codec?.stop() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo detener el codec de video en la limpieza", e) }
            try { codec?.release() } catch (e: Exception) { AppLogger.w(TAG, "No se pudo liberar el codec de video en la limpieza", e) }
            releaseEgl()
            // Recién ACÁ, después de que muxer/codec ya soltaron sus
            // handles del archivo (arriba), es seguro borrar el .mp4
            // parcial de una cancelación — nunca antes, para no arriesgar
            // ninguna duda de orden con el sistema de archivos del equipo.
            if (wasCancelled) {
                if (outputFile.exists()) {
                    val deleted = outputFile.delete()
                    AppLogger.i(TAG, "Archivo parcial de exportación cancelada ${if (deleted) "borrado" else "NO se pudo borrar"}: ${outputFile.name}")
                } else {
                    // La cancelación pudo haber ocurrido durante la fase de
                    // audio, ANTES de que el MediaMuxer llegara siquiera a
                    // crear el archivo — no es un fallo de borrado, no hay
                    // nada que borrar.
                    AppLogger.i(TAG, "Exportación cancelada antes de que existiera ningún archivo de salida que borrar.")
                }
            }
        }
    }

    /**
     * Extrae los buffers codificados disponibles del encoder y los escribe
     * al muxer. Devuelve si el muxer ya fue iniciado (necesario porque el
     * muxer solo puede arrancar tras recibir el MediaFormat real del
     * encoder, disponible en el primer INFO_OUTPUT_FORMAT_CHANGED).
     */
    private fun drainEncoder(
        codec: MediaCodec?,
        muxer: MediaMuxer?,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        muxerTrackIndexRef: () -> Int,
        setTrackIndex: (Int) -> Unit,
        muxerStarted: Boolean,
        beforeMuxerStart: () -> Unit = {}
    ): Boolean {
        if (codec == null || muxer == null) return muxerStarted
        var started = muxerStarted

        while (true) {
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            when {
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return started
                }
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    setTrackIndex(muxer.addTrack(newFormat))
                    beforeMuxerStart()
                    muxer.start()
                    started = true
                }
                outputBufferId >= 0 -> {
                    val encodedData: ByteBuffer = codec.getOutputBuffer(outputBufferId)
                        ?: throw IllegalStateException("outputBuffer nulo en índice $outputBufferId")

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && started) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(muxerTrackIndexRef(), encodedData, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputBufferId, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return started
                    }
                }
            }
            if (!endOfStream && outputBufferId < 0) return started
        }
    }

    // --- Configuración manual de EGL apuntando a la Surface del encoder ---

    private fun setupEgl(inputSurface: android.view.Surface, width: Int, height: Int) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("No se pudo obtener EGLDisplay")

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("No se pudo inicializar EGL")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        val config = configs[0] ?: throw RuntimeException("Sin config EGL compatible")

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) throw RuntimeException("No se pudo crear el contexto EGL")

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, inputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) throw RuntimeException("No se pudo crear la superficie EGL")

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent falló")
        }

        android.opengl.GLES20.glViewport(0, 0, width, height)
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}
