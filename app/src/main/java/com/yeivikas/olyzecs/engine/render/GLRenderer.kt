package com.yeivikas.olyzecs.engine.render

import android.content.ContentResolver
import android.graphics.Bitmap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.yeivikas.olyzecs.data.ImageDecoding
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.core.PixelColorSource
import com.yeivikas.olyzecs.engine.scene.Layer

private const val TAG = "GLRenderer"

/**
 * Renderer del preview en vivo. Toda la lógica real de dibujo vive en
 * [LayerDrawer] (compartida con el exportador de video offline); aquí solo
 * se resuelve el ciclo de vida GLSurfaceView y la subida perezosa de
 * texturas desde los bitmaps pendientes de cada capa.
 *
 * El look cinematográfico (grading, viñeta, grano, glow) es propio de
 * cada [Layer] — se lee directo de `layer.lookSettings`, no hay un ajuste
 * global compartido entre capas.
 */
class GLRenderer(
    private val contentResolver: ContentResolver,
    private val getLayers: () -> List<Layer>,
    private val getPlayheadMs: () -> Long,
    private val getLiveOverride: () -> Pair<String, CameraFrame>? = { null },
    // Cuadrícula de composición ya rasterizada a bitmap por EditorScreen
    // (o null si está apagada) — ver comentario completo en
    // EditorScreen.rasterizeGridBitmap. Se sube como una textura más y se
    // dibuja PRIMERO en cada frame, antes que las capas reales, para que
    // quede DETRÁS de ellas (comportamiento de canvas profesional) en vez
    // de flotar siempre encima como un overlay de Compose.
    private val getGridBitmap: () -> Bitmap? = { null }
) : GLSurfaceView.Renderer, PixelColorSource {

    private val drawer = LayerDrawer()
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1

    // --- Textura de la cuadrícula de composición (ver comentario en
    // getGridBitmap arriba). Se re-sube SOLO cuando el bitmap que llega
    // cambia de identidad (Compose crea un bitmap nuevo únicamente
    // cuando algo de la cuadrícula o el tamaño del lienzo cambian de
    // verdad — ver las keys del `remember` en EditorScreen) — no en
    // cada uno de los ~60 frames por segundo, para no reventar el
    // rendimiento subiendo la misma textura una y otra vez.
    private var gridTextureId: Int = -1
    private var gridTextureWidthPx: Int = 0
    private var gridTextureHeightPx: Int = 0
    private var lastGridBitmapIdentity: Bitmap? = null

    // --- Cuentagotas: pedido pendiente de leer el color de un pixel
    // exacto de lo que se está dibujando. @Volatile porque se escribe
    // desde el hilo de UI (requestPixelColor, llamado por un tap en
    // Compose) y se lee/consume desde el hilo de render de GL
    // (onDrawFrame) — sin @Volatile, el hilo de GL podría no ver nunca
    // la escritura hecha desde el otro hilo. ---
    @Volatile private var pendingPixelRequest: PixelReadRequest? = null

    private class PixelReadRequest(val xPx: Int, val yPx: Int, val callback: (argbColor: Int) -> Unit)

    /**
     * Pide leer el color EXACTO del pixel en ([xPx], [yPx]) — coordenadas
     * de vista (origen arriba-izquierda, igual que un tap de Compose), NO
     * coordenadas de GL (que tienen el origen abajo-izquierda; el flip Y
     * se hace acá adentro, quien llama no tiene que pensarlo). El
     * resultado llega por [callback] ya en el hilo principal (no en el
     * hilo de GL), listo para usar directo en actualizar estado de
     * Compose sin saltar de hilo a mano.
     */
    override fun requestPixelColor(xPx: Int, yPx: Int, callback: (argbColor: Int) -> Unit) {
        pendingPixelRequest = PixelReadRequest(xPx, yPx, callback)
    }

    // --- DIAGNÓSTICO TEMPORAL: registra en el log de errores de la app
    // (Registro de errores → revisable sin cable ni computadora) qué ve
    // realmente este renderer al arrancar y en su primer frame — así se
    // sabe con certeza en qué paso se corta el lienzo en vivo, en vez de
    // seguir adivinando a ciegas. Se puede borrar más adelante una vez
    // encontrada la causa real. ---
    private var hasLoggedFirstFrame = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        drawer.ensureInitialized()
        hasLoggedFirstFrame = false

        // IMPORTANTE: esto se llama cada vez que se crea un contexto EGL
        // nuevo — no solo la primera vez. Eso pasa, por ejemplo, al volver
        // a "Mis proyectos" y reabrir el mismo proyecto: el ViewModel (y
        // sus capas) se reutiliza, pero el GLSurfaceView y su contexto GL
        // se destruyen y se recrean desde cero. Cualquier `glTextureId` ya
        // asignado pertenece al contexto VIEJO (ya destruido) y ya no es
        // válido en este — usarlo tal cual dejaba el preview en negro. Acá
        // se invalida esa textura y, si el bitmap en memoria ya se había
        // liberado (caso normal: se libera apenas se sube a GL), se
        // vuelve a decodificar desde la copia local de la capa para
        // poder subirla de nuevo.
        val layersAtStart = getLayers()
        var redecodedOk = 0
        var redecodedFailed = 0
        var alreadyHadBitmap = 0
        for (layer in layersAtStart) {
            layer.glTextureId = -1
            if (layer.pendingBitmap == null) {
                val decoded = runCatching {
                    ImageDecoding.decodeSampledFromUri(contentResolver, layer.sourceUri)
                }.getOrNull()
                layer.pendingBitmap = decoded
                if (decoded != null) redecodedOk++ else redecodedFailed++
            } else {
                alreadyHadBitmap++
            }
        }
        AppLogger.i(
            TAG,
            "DIAGNÓSTICO onSurfaceCreated: ${layersAtStart.size} capa(s) vistas · " +
                "$alreadyHadBitmap ya tenían bitmap listo · $redecodedOk se re-decodificaron OK · " +
                "$redecodedFailed fallaron al re-decodificar"
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        drawer.clear()

        // --- Cuadrícula de composición: se dibuja PRIMERO, inmediatamente
        // después de limpiar el lienzo y ANTES que cualquier capa real —
        // así, el propio pipeline de composición (painter's algorithm: lo
        // que se dibuja después tapa lo que se dibujó antes) hace que
        // cualquier píxel opaco de una capa oculte la cuadrícula donde
        // corresponde, y solo se vea en los huecos — exactamente como en
        // un canvas profesional (Photoshop/Lightroom/Premiere), no
        // siempre flotando encima de todo.
        updateGridTextureIfNeeded()
        if (gridTextureId >= 0) {
            drawer.drawLayer(
                textureId = gridTextureId,
                imageWidthPx = gridTextureWidthPx,
                imageHeightPx = gridTextureHeightPx,
                // Encuadre neutro/identidad y parallaxFactor=1 (plano de
                // foco, z=0): la cuadrícula es una guía fija del lienzo
                // completo, no se mueve ni se tuerce con el paneo/zoom/
                // tilt de ninguna capa — ver comentario de
                // "z=0 con dollyZoom=0 se ve IDÉNTICA al render 2D
                // original" en LayerDrawer.
                frame = CameraFrame(0f, 0f, 1f, 0f, 1f),
                parallaxFactor = 1f,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight
            )
        }

        val layers = getLayers().sortedBy { it.zIndex }
        val timeMs = getPlayheadMs()
        val override = getLiveOverride()

        for (layer in layers) {
            uploadTextureIfNeeded(layer)
            if (layer.glTextureId < 0 || !layer.visible) continue
            val frame = if (override != null && override.first == layer.id) {
                override.second
            } else {
                layer.cameraTrack.frameAt(timeMs)
            }
            // Frame de ~33ms atrás, solo para calcular el vector de
            // movimiento del motion blur — no se usa si la capa no tiene
            // motion blur activado (drawLayer lo ignora en ese caso).
            val previousFrame = if (layer.lookSettings.motionBlurIntensity > 0.001f) {
                layer.cameraTrack.frameAt((timeMs - 33L).coerceAtLeast(0L))
            } else null
            drawer.drawLayer(
                textureId = layer.glTextureId,
                imageWidthPx = layer.widthPx,
                imageHeightPx = layer.heightPx,
                frame = frame,
                parallaxFactor = layer.parallaxFactor,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                look = layer.lookSettings,
                timeSeconds = timeMs / 1000f,
                previousFrame = previousFrame
            )
        }

        // DIAGNÓSTICO TEMPORAL: una sola vez por cada vez que se crea la
        // superficie (no los 60 frames por segundo — inundaría el log),
        // deja registrado el estado real de cada capa DESPUÉS de intentar
        // subir su textura: si terminó con un glTextureId válido, si está
        // marcada visible, y las dimensiones del viewport. Esto dice
        // exactamente cuál de las tres cosas (capas vacías, texturas sin
        // subir, o capas ocultas) es la que está dejando el lienzo en
        // blanco.
        if (!hasLoggedFirstFrame) {
            hasLoggedFirstFrame = true
            val detail = layers.joinToString("; ") { l ->
                "'${l.name}': textureId=${l.glTextureId}, visible=${l.visible}, ${l.widthPx}x${l.heightPx}px"
            }
            AppLogger.i(
                TAG,
                "DIAGNÓSTICO onDrawFrame (primer frame): viewport=${viewportWidth}x${viewportHeight} · " +
                    "${layers.size} capa(s) en total → $detail"
            )
        }

        // Cuentagotas: si hay un pedido pendiente, ESTE es el único
        // momento seguro para leerlo — justo después de terminar de
        // dibujar el frame completo, antes de que GLSurfaceView haga el
        // swap de buffers. Leer en cualquier otro momento arriesga traer
        // el frame anterior a medio dibujar.
        val request = pendingPixelRequest
        if (request != null) {
            pendingPixelRequest = null
            val buffer = java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder())
            // GL tiene el origen (0,0) abajo-izquierda; una coordenada de
            // vista/tap de Compose tiene el origen arriba-izquierda — hay
            // que invertir el eje Y para leer el pixel que el usuario
            // realmente tocó en pantalla.
            val glX = request.xPx.coerceIn(0, (viewportWidth - 1).coerceAtLeast(0))
            val glY = (viewportHeight - 1 - request.yPx).coerceIn(0, (viewportHeight - 1).coerceAtLeast(0))
            GLES20.glReadPixels(glX, glY, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            val r = buffer.get(0).toInt() and 0xFF
            val g = buffer.get(1).toInt() and 0xFF
            val b = buffer.get(2).toInt() and 0xFF
            val a = buffer.get(3).toInt() and 0xFF
            val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
            // El callback puede terminar mutando estado de Compose —
            // tiene que correr en el hilo principal, no en el hilo de GL
            // en el que está parado onDrawFrame ahora mismo.
            android.os.Handler(android.os.Looper.getMainLooper()).post { request.callback(argb) }
        }
    }

    /**
     * Sube (o quita) la textura de la cuadrícula de composición SOLO
     * cuando el bitmap que llega de [getGridBitmap] cambió de identidad
     * desde el frame anterior — comparación por referencia (`!==`), no
     * por contenido: como el bitmap viene de un `remember` de Compose con
     * keys puntuales (forma, columnas/filas, color, grosor, tamaño del
     * lienzo), es el MISMO objeto en cada frame mientras nada de eso
     * cambie, así que en el caso normal (cuadrícula quieta mientras se
     * arrastra una capa) esto no hace nada, ningún re-upload de más.
     */
    private fun updateGridTextureIfNeeded() {
        val bitmap = getGridBitmap()
        if (bitmap === lastGridBitmapIdentity) return
        lastGridBitmapIdentity = bitmap

        if (gridTextureId >= 0) {
            drawer.deleteTexture(gridTextureId)
            gridTextureId = -1
        }
        if (bitmap == null) return

        try {
            gridTextureId = drawer.uploadTexture(bitmap)
            gridTextureWidthPx = bitmap.width
            gridTextureHeightPx = bitmap.height
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo subir la textura de la cuadrícula de composición a la GPU — se sigue editando sin cuadrícula visible", t)
            gridTextureId = -1
        } finally {
            // El contenido ya quedó subido a la GPU (o falló y se
            // descarta igual) — se libera acá mismo, mismo criterio que
            // uploadTextureIfNeeded() usa para los bitmaps de las capas.
            // La referencia sigue siendo válida para la comparación por
            // identidad de arriba aunque el bitmap ya esté reciclado.
            bitmap.recycle()
        }
    }

    // BUG REAL corregido: esto corría SIN try/catch, adentro del mismo
    // `for (layer in layers)` de onDrawFrame. Si UNA sola capa fallaba al
    // subir su textura a la GPU (bitmap corrupto, formato no soportado,
    // lo que sea), la excepción cortaba el resto del bucle — de golpe, TODAS
    // las capas después de esa en el orden de z-index se quedaban sin
    // textura, frame tras frame, para siempre (la próxima llamada a
    // onDrawFrame vuelve a intentar subir la MISMA capa rota primero, así
    // que nunca se avanzaba). En pantalla eso se ve exactamente como "no
    // aparece ninguna imagen": solo queda el verde chroma-key de fondo
    // (el color por defecto del lienzo cuando no hay nada dibujado encima,
    // ver ensureInitialized más arriba — NO es una capa de imagen).
    //
    // Ahora cada capa se sube de forma aislada: si una falla, se loguea el
    // motivo real (revisable en el log de errores de la app) y esa capa
    // se salta — pero el resto de las capas SÍ se siguen subiendo y
    // dibujando con normalidad en el mismo frame.
    private fun uploadTextureIfNeeded(layer: Layer) {
        val bitmap = layer.pendingBitmap ?: return
        try {
            if (layer.glTextureId >= 0) {
                // Ya había una textura (caso "reemplazar imagen"): liberarla antes de subir la nueva.
                drawer.deleteTexture(layer.glTextureId)
            }
            layer.glTextureId = drawer.uploadTexture(bitmap)
            layer.widthPx = bitmap.width
            layer.heightPx = bitmap.height
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo subir la textura de la capa '${layer.name}' a la GPU — esa capa no se va a dibujar", t)
            layer.glTextureId = -1
        } finally {
            // Se libera/descarta el bitmap SIEMPRE, haya salido bien o mal
            // la subida — si falló, reintentar con el mismo bitmap roto en
            // el próximo frame solo repetiría el mismo error para siempre.
            bitmap.recycle()
            layer.pendingBitmap = null
        }
    }
}
