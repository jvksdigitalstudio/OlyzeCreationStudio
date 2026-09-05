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
    // FASE 2 — [getLayers] queda AHORA reservado exclusivamente a los
    // recursos GPU/CPU de cada capa (`glTextureId`, `pendingBitmap`,
    // `widthPx`/`heightPx`, `sourceUri` para el re-decode de
    // onSurfaceCreated) — campos `@Volatile` en Layer.kt, dueños de este
    // mismo hilo de GL en el caso normal. TODO dato lógico usado para
    // decidir QUÉ y CÓMO dibujar (zIndex, visibilidad, parallax, look,
    // encuadre de cámara) se lee ahora de [getRenderSnapshot] — ver
    // RenderSnapshot.kt para el razonamiento completo de por qué esta
    // separación es la que pide la arquitectura de Fase 2 (UI → ViewModel
    // → RenderSnapshot → GL Renderer) sin necesitar mover los recursos
    // GPU fuera de Layer (lo que sí sería un refactor fuera del alcance
    // de esta fase).
    private val getLayers: () -> List<Layer>,
    private val getRenderSnapshot: () -> RenderSnapshot,
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

    // FASE 3 — Render / GL Lifecycle.
    //
    // Estado explícito del ciclo de vida (ver GLRendererLifecycleState):
    // reemplaza lo que antes era un conjunto implícito de suposiciones
    // ("si onSurfaceCreated ya corrió, entonces...") sin ninguna
    // variable que lo representara. onDrawFrame() ahora puede preguntar
    // con certeza si es seguro dibujar, en vez de asumirlo. @Volatile
    // por disciplina (mismo criterio que el resto de este archivo/Layer.kt),
    // aunque en el contrato real de GLSurfaceView.Renderer los tres
    // callbacks corren siempre en el mismo hilo de GL, nunca en paralelo
    // entre sí.
    @Volatile private var lifecycleState: GLRendererLifecycleState = GLRendererLifecycleState.UNINITIALIZED

    // FASE 3 — "generación" del contexto EGL actual (ver GpuContextGeneration/
    // GpuHandle). Se avanza una vez por cada onSurfaceCreated() real —
    // todo GpuHandle taggeado con una generación anterior queda
    // automáticamente inválido, sin tener que invalidar recurso por
    // recurso a mano.
    private val contextGeneration = GpuContextGeneration()

    // --- Textura de la cuadrícula de composición (ver comentario en
    // getGridBitmap arriba). Se re-sube SOLO cuando el bitmap que llega
    // cambia de identidad (Compose crea un bitmap nuevo únicamente
    // cuando algo de la cuadrícula o el tamaño del lienzo cambian de
    // verdad — ver las keys del `remember` en EditorScreen) O cuando el
    // handle quedó obsoleto por una recreación de contexto EGL — ver
    // GridTextureCacheState (FASE 3 — corrige el bug real de la
    // cuadrícula quedando invisible/corrupta tras volver de segundo
    // plano o reabrir un proyecto, ver comentario de esa clase).
    private var gridTextureWidthPx: Int = 0
    private var gridTextureHeightPx: Int = 0
    private val gridTextureCache = GridTextureCacheState()

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
        // FASE 3 — Render / GL Lifecycle.
        //
        // Este callback significa, SIEMPRE, "hay un contexto EGL nuevo"
        // — nunca solo "la app arrancó" (ver comentario largo más abajo,
        // ya existente desde antes de esta fase, sobre reabrir un
        // proyecto). Por eso el primer paso, sin condición, es: (1)
        // volver el estado del renderer a UNINITIALIZED (cualquier
        // estado previo pertenecía al contexto viejo, ya destruido — es
        // la única transición "hacia atrás" legal, ver
        // GLRendererLifecycleState) y (2) avanzar la generación de
        // contexto ANTES de tocar cualquier recurso GPU, para que todo
        // GpuHandle existente (la textura de la cuadrícula) quede
        // automáticamente marcado inválido por la nueva generación.
        lifecycleState = GLRendererLifecycleState.UNINITIALIZED
        val newGeneration = contextGeneration.advance()

        // BUG REAL corregido en esta fase: antes se llamaba a
        // `drawer.ensureInitialized()`, que solo reconstruye el shader
        // si `shaderProgram == null` — condición que deja de cumplirse a
        // partir de la SEGUNDA vez que este método corre (misma
        // instancia de LayerDrawer, contexto EGL nuevo), dejando el
        // renderer con un `programId` de un contexto ya muerto. Ver el
        // comentario completo en `LayerDrawer.forceReinitialize()`.
        drawer.forceReinitialize()

        // BUG REAL corregido en esta fase: la textura de la cuadrícula
        // de composición (`gridTextureCache`) nunca se invalidaba acá —
        // a diferencia de las texturas de las capas (`layer.glTextureId
        // = -1` más abajo, ESO sí ya estaba correcto desde antes de esta
        // fase). Con el bitmap de la cuadrícula sin cambiar de
        // identidad, `updateGridTextureIfNeeded` seguía pensando que el
        // texture id viejo (de un contexto ya destruido) seguía siendo
        // válido y nunca lo resubía — la cuadrícula quedaba invisible (o
        // mostrando basura de memoria de GPU reciclada) después de
        // cualquier recreación de contexto. Ver GridTextureCacheState.
        gridTextureCache.invalidateForNewContext()

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
        // AUDITORÍA — hallazgo confirmado (a diferencia de un hallazgo
        // similar en VideoExporter que resultó estar mal diagnosticado:
        // ver el comentario de ese archivo): ACÁ SÍ todas las capas quedan
        // decodificadas a resolución completa y en memoria SIMULTÁNEAMENTE
        // — este `for` completo termina de llenar `pendingBitmap` de cada
        // capa antes de que `onDrawFrame` empiece, recién en el frame
        // siguiente, a subirlas a GL y liberarlas una por una. Un proyecto
        // con muchas capas de fotos de cámara moderna puede necesitar un
        // pico real de cientos de MB solo para volver a mostrar el
        // preview tras reabrir un proyecto o volver de segundo plano
        // (motivo real de este re-decode, ver comentario más abajo).
        //
        // Corrección: `pendingBitmap` es, por su propia documentación en
        // `Layer.kt`, una "referencia temporal... SOLO hasta que se sube a
        // GL" — nunca lo usa el pipeline de Efectos/Recolor/3D (confirmado
        // buscando cada uso en el proyecto). Como su único destino es
        // `uploadTextureIfNeeded` → `GpuTextureLimits.clampForTexture` →
        // GPU, decodificarlo ya acotado al límite real de textura de esta
        // GPU (`queryMaxTextureSize()`, disponible acá porque este método
        // corre con el contexto EGL recién creado y activo) produce el
        // MISMO resultado final en pantalla, sin pasar nunca por el pico
        // de memoria de la versión a resolución completa. Esto NO cambia
        // la filosofía de "nunca reducir calidad por decisión de la app"
        // documentada en `ImageDecoding` — sigue siendo, igual que
        // `clampForTexture`, un techo real de hardware, no una elección de
        // calidad; la única diferencia con clampear DESPUÉS de decodificar
        // es que el sampling por potencias de 2 de `inSampleSize` puede
        // quedar un pelo por debajo del límite exacto de la GPU en vez de
        // ajustarse a él con precisión de píxel — una diferencia mínima e
        // irrelevante para un re-decode cuyo único destino es la pantalla.
        val layersAtStart = getLayers()
        val maxTextureDimension = GpuTextureLimits.queryMaxTextureSize()
        var redecodedOk = 0
        var redecodedFailed = 0
        var alreadyHadBitmap = 0
        for (layer in layersAtStart) {
            layer.glTextureId = -1
            if (layer.pendingBitmap == null) {
                val decoded = runCatching {
                    ImageDecoding.decodeSampledFromUri(contentResolver, layer.sourceUri, maxDimension = maxTextureDimension)
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
                "$redecodedFailed fallaron al re-decodificar · generación de contexto=$newGeneration"
        )

        // Contexto + shader + bookkeeping de texturas ya están listos —
        // falta únicamente el viewport real, que llega en
        // onSurfaceChanged (contrato de GLSurfaceView: Android SIEMPRE
        // lo llama después de onSurfaceCreated, antes del primer
        // onDrawFrame).
        transitionTo(GLRendererLifecycleState.SURFACE_READY)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
        transitionTo(GLRendererLifecycleState.VIEWPORT_READY)
    }

    /**
     * Aplica la transición de [GLRendererLifecycleState] validándola
     * contra [GLRendererLifecycleState.canTransitionTo] — si alguna vez
     * Android llamara a los callbacks en un orden fuera de contrato (no
     * debería, pero es una GLSurfaceView de terceros, no código nuestro),
     * esto lo deja registrado en el log de errores en vez de fallar en
     * silencio con un estado contradictorio.
     */
    private fun transitionTo(target: GLRendererLifecycleState) {
        if (!lifecycleState.canTransitionTo(target)) {
            AppLogger.w(TAG, "Transición de lifecycle inesperada: $lifecycleState → $target (se aplica igual)")
        }
        lifecycleState = target
    }

    override fun onDrawFrame(gl: GL10?) {
        // FASE 3 — no dibujar nada real hasta que el viewport asignado
        // por Android sea válido: antes de esto, viewportWidth/Height
        // conservan su valor por defecto (1x1, ver declaración arriba),
        // que produciría una imagen distorsionada por un frame o dos si
        // se llegara a dibujar con él. En la práctica GLSurfaceView
        // respeta el orden onSurfaceCreated → onSurfaceChanged →
        // onDrawFrame, así que esto rara vez frena un frame real — es la
        // red de seguridad explícita que pide el criterio de aceptación
        // de Fase 3 ("onDrawFrame() solo utiliza recursos válidos").
        if (lifecycleState != GLRendererLifecycleState.VIEWPORT_READY) return

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
        val gridTextureId = gridTextureCache.handle.id
        if (gridTextureCache.handle.isValid(contextGeneration.value)) {
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

        // FASE 2 — [renderSnapshot] es la ÚNICA fuente de datos lógicos
        // (orden, visibilidad, parallax, look, cámara) para este frame:
        // una lista inmutable, ya congelada, que nunca puede cambiar bajo
        // los pies de este bucle sin importar qué esté haciendo el hilo
        // principal con `Layer`/`CameraTrack` al mismo tiempo (ver
        // RenderSnapshot.kt). [liveLayersById] se usa EXCLUSIVAMENTE para
        // los campos GPU (`glTextureId`/`pendingBitmap`/`widthPx`/
        // `heightPx`), que siguen viviendo en el propio `Layer` porque su
        // dueño real es este mismo hilo de GL.
        val renderSnapshot = getRenderSnapshot()
        val liveLayersById = getLayers().associateBy { it.id }
        val orderedSnapshotLayers = renderSnapshot.layers.sortedBy { it.zIndex }
        val timeMs = getPlayheadMs()
        val override = getLiveOverride()

        for (snapLayer in orderedSnapshotLayers) {
            val liveLayer = liveLayersById[snapLayer.id] ?: continue
            uploadTextureIfNeeded(liveLayer)
            if (liveLayer.glTextureId < 0 || !snapLayer.visible) continue
            val frame = if (override != null && override.first == snapLayer.id) {
                override.second
            } else {
                snapLayer.frameAt(timeMs)
            }
            // Frame de ~33ms atrás, solo para calcular el vector de
            // movimiento del motion blur — no se usa si la capa no tiene
            // motion blur activado (drawLayer lo ignora en ese caso).
            val previousFrame = if (snapLayer.lookSettings.motionBlurIntensity > 0.001f) {
                snapLayer.frameAt((timeMs - 33L).coerceAtLeast(0L))
            } else null
            drawer.drawLayer(
                textureId = liveLayer.glTextureId,
                imageWidthPx = liveLayer.widthPx,
                imageHeightPx = liveLayer.heightPx,
                frame = frame,
                parallaxFactor = snapLayer.parallaxFactor,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                look = snapLayer.lookSettings,
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
            val detail = orderedSnapshotLayers.joinToString("; ") { snap ->
                val live = liveLayersById[snap.id]
                "'${live?.name ?: snap.id}': textureId=${live?.glTextureId ?: -1}, visible=${snap.visible}, ${live?.widthPx ?: 0}x${live?.heightPx ?: 0}px"
            }
            AppLogger.i(
                TAG,
                "DIAGNÓSTICO onDrawFrame (primer frame): viewport=${viewportWidth}x${viewportHeight} · " +
                    "${orderedSnapshotLayers.size} capa(s) en total → $detail"
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
        val generation = contextGeneration.value

        // FASE 3 — la decisión de si hace falta reconciliar el estado
        // GPU ahora la toma GridTextureCacheState (identidad de bitmap
        // CAMBIÓ, incluido apagarse, o la generación del handle actual
        // ya no es la vigente) en vez de comparar solo la identidad del
        // bitmap — ver el comentario largo de esa clase para el bug real
        // que esto corrige.
        if (!gridTextureCache.needsReconciliation(bitmap, generation)) return

        // Si el handle vigente (antes de reconciliar) sigue siendo válido
        // bajo el contexto ACTUAL, es un recurso GPU real y vivo que ya
        // no hace falta (la identidad del bitmap cambió — nueva forma/
        // color/tamaño de cuadrícula) — hay que liberarlo explícitamente
        // acá, a diferencia del caso de recreación de contexto (donde el
        // id pertenece a un contexto ya muerto y NUNCA se llama
        // glDeleteTexture sobre él, ver GridTextureCacheState.invalidateForNewContext).
        val previousHandle = gridTextureCache.handle
        if (previousHandle.isValid(generation)) {
            drawer.deleteTexture(previousHandle.id)
        }

        if (bitmap == null) {
            gridTextureCache.recordCleared()
            return
        }

        try {
            val newId = drawer.uploadTexture(bitmap)
            gridTextureCache.recordUpload(newId, bitmap, generation)
            gridTextureWidthPx = bitmap.width
            gridTextureHeightPx = bitmap.height
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo subir la textura de la cuadrícula de composición a la GPU — se sigue editando sin cuadrícula visible", t)
            gridTextureCache.recordCleared()
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
        val original = layer.pendingBitmap ?: return
        // El bitmap decodificado ya está a resolución completa (ver
        // [ImageDecoding]). Acá, justo antes de subirlo a GL, es el
        // ÚNICO punto donde se respeta un límite — y es un techo real de
        // hardware (GL_MAX_TEXTURE_SIZE de esta GPU), no una reducción de
        // calidad elegida por la app. `clampForTexture` devuelve el mismo
        // bitmap sin tocar si ya entra en ese límite (el caso normal).
        val bitmap = GpuTextureLimits.clampForTexture(original)
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
