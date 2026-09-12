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
    // recursos GPU/CPU de cada capa (`pendingBitmap`, `widthPx`/
    // `heightPx`, `sourceUri` para el re-decode de onSurfaceCreated) —
    // campos `@Volatile`/`@Transient` en Layer.kt, dueños de este mismo
    // hilo de GL en el caso normal (desde la Fase 3.1, el texture id GL
    // en sí ya ni vive en `Layer` — ver [layerTextures] más abajo). TODO
    // dato lógico usado para decidir QUÉ y CÓMO dibujar (zIndex,
    // visibilidad, parallax, look, encuadre de cámara) se lee ahora de
    // [getRenderSnapshot] — ver RenderSnapshot.kt para el razonamiento
    // completo de por qué esta separación es la que pide la arquitectura
    // de Fase 2 (UI → ViewModel → RenderSnapshot → GL Renderer) sin
    // necesitar mover los recursos GPU fuera de Layer (lo que sí sería un
    // refactor fuera del alcance de esa fase).
    private val getLayers: () -> List<Layer>,
    private val getRenderSnapshot: () -> RenderSnapshot,
    private val getPlayheadMs: () -> Long,
    // FASE 3.1.3-R3 — Real Atomic GPU Commit Gate. Instancia ÚNICA,
    // compartida con `EditorViewModel` (misma referencia — ver cómo se
    // conecta en GLPreview.kt/EditorScreen.kt), usada para serializar la
    // validación final + commit GPU (acá, en `uploadTextureIfNeeded`)
    // contra las mutaciones de `EditorViewModel` que puedan invalidar la
    // identidad que se está validando (`replaceLayer`/`replaceLayers`/
    // `removeLayer`/`updateContentIdentity`, ver el detalle completo en
    // `LayerGpuCommitGate` y en el comentario "FASE 3.1.3-R3" de
    // `EditorViewModel`). Sin default: recibirla es obligatorio a
    // propósito — un `GLRenderer` sin gate real no puede cumplir la
    // garantía que documenta esta fase, así que no existe una forma
    // "cómoda" de olvidarse de conectarlo.
    private val commitGate: LayerGpuCommitGate,
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

    // FASE 3.1 — Cierre y hardening de ownership de GPU.
    //
    // Registro de texturas GL por capa: reemplaza el `Layer.glTextureId`
    // mutable público que existía hasta la Fase 3 (ver comentario largo
    // en `Layer.kt` sobre el bug real de ownership que esto corrige).
    // Clave = `Layer.id` (estable a través de cualquier `.copy()` del
    // ViewModel — un `.copy()` nunca cambia el id), valor = el texture id
    // GL real, válido bajo el contexto EGL actual. Es un `private val`
    // de ESTA clase: ningún código fuera de `GLRenderer` tiene, siquiera,
    // una referencia a este mapa ni a ningún texture id real — la
    // garantía de "GL thread ownership" queda dada por construcción, no
    // por disciplina ni por un modificador como `@Volatile`.
    //
    // No necesita envolver cada valor en un `GpuHandle` con generación
    // propia: este mapa completo se vacía una vez por cada
    // `onSurfaceCreated()` real (ver más abajo), así que cualquier
    // entrada que sigue presente pertenece, por construcción, siempre al
    // contexto EGL vigente.
    //
    // FASE 3.1.2 — el valor ya NO es un `Int` (texture id) suelto: es un
    // [LayerTextureRecord] que ADEMÁS guarda con qué `contentRevision`
    // (ver Layer.kt) se subió esa textura — es la identidad real que
    // pide el informe de esta fase ("Layer ID + Content Revision" en vez
    // de solo Layer ID). El id GL solo no alcanza para responder "¿esta
    // textura sigue representando el contenido ACTUAL de la capa?" — un
    // pedido de invalidación (`requestTextureInvalidation`) sigue siendo
    // la vía normal para saberlo, pero esta comparación de revisión es
    // una segunda línea de defensa real: si algún flujo futuro llegara a
    // cambiar `sourceUri`/`contentRevision` sin pedir la invalidación
    // correspondiente (un descuido humano real, no hipotético — ver la
    // auditoría completa en FASE_3_1_2_CONTENT_REVISION.md), esta
    // comparación de todas formas evita mostrar la textura vieja para
    // siempre.
    //
    // FASE 3.1.3 — no incluye `contextGeneration` como campo propio a
    // pesar de que el informe de esta fase lo sugiere como parte de la
    // identidad completa del recurso ("layerId + contentRevision +
    // contextGeneration"): es redundante acá porque este mapa ENTERO se
    // vacía por completo en cada `onSurfaceCreated()` (ver más abajo)
    // ANTES de que pueda insertarse ninguna entrada nueva para el
    // contexto recién creado — por construcción, toda entrada que exista
    // en este mapa en un momento dado pertenece siempre a la generación
    // EGL vigente, sin necesitar guardarlo por registro. La validación de
    // generación sí se hace, explícitamente, en el protocolo de commit
    // (ver `uploadTextureIfNeeded`) — justo antes de decidir si insertar
    // una entrada nueva, no como un campo más para comparar después.
    private data class LayerTextureRecord(val glTextureId: Int, val contentRevision: Int)
    private val layerTextures = mutableMapOf<String, LayerTextureRecord>()

    // FASE 3.1.1 — HOTFIX post-cierre (bug real reportado: "el undo no
    // trae de vuelta la imagen anterior" / "las capas se pusieron
    // transparentes editando").
    //
    // Registra, por `Layer.id`, qué capas ya intentaron un re-decode
    // perezoso desde `sourceUri` (ver [uploadTextureIfNeeded]) y
    // fallaron — para NO reintentar sin parar, 60 veces por segundo, un
    // decode que ya sabemos que va a volver a fallar (uri revocada,
    // archivo borrado, etc.). Se limpia una entrada en cuanto la capa
    // recibe un nuevo pedido de invalidación real (nueva oportunidad,
    // puede que el motivo del fallo ya no aplique) o cuando el contexto
    // EGL se recrea por completo.
    private val failedRedecodeIds = mutableSetOf<String>()

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

        // FASE 3.1 — mismo criterio que gridTextureCache.invalidateForNewContext()
        // de arriba: todo texture id que quedara en [layerTextures]
        // pertenece al contexto EGL viejo, ya destruido — llamar a
        // glDeleteTextures sobre un id de un contexto muerto es, en el
        // mejor caso, un no-op y en el peor, comportamiento indefinido
        // según el driver, así que simplemente se descarta el registro
        // completo sin intentar liberar nada. Reemplaza el
        // `layer.glTextureId = -1` por capa que hacía este mismo trabajo
        // antes de que el registro existiera.
        layerTextures.clear()

        // FASE 3.1.1 — mismo criterio que layerTextures.clear() de
        // arriba: un fallo de re-decode registrado en el contexto EGL
        // viejo no dice nada sobre si va a volver a fallar en el
        // contexto nuevo (de hecho, este mismo bucle está a punto de
        // reintentar el decode para TODAS las capas, líneas más abajo).
        failedRedecodeIds.clear()

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
        var redecodedStale = 0
        var alreadyHadBitmap = 0
        for (layer in layersAtStart) {
            if (layer.pendingBitmap.peek() != null) {
                alreadyHadBitmap++
                continue
            }
            // FASE 3.1.2 — PROBLEMA 3 del informe: se captura la
            // identidad/versión ANTES de decodificar. El hilo principal
            // corre en PARALELO REAL (no interleaved) mientras este
            // decode síncrono se ejecuta acá — nada impide que, mientras
            // tanto, el ViewModel reemplace, revierta o elimine
            // exactamente esta misma capa (el usuario deshaciendo/
            // reemplazando justo al volver de segundo plano, por
            // ejemplo). Publicar el resultado sin volver a validar
            // podría resucitar contenido viejo sobre una capa que ya
            // cambió — exactamente lo que esta fase tiene que impedir.
            // FASE 3.1.3-R2 — se captura [Layer.contentIdentity] como UNA
            // sola lectura atómica (en vez de `sourceUri`/`contentRevision`
            // por separado) — ver el KDoc de `currentLayerIfStillRequested`.
            val requestedIdentity = layer.contentIdentity
            val decoded = runCatching {
                ImageDecoding.decodeSampledFromUri(contentResolver, requestedIdentity.sourceUri, maxDimension = maxTextureDimension)
            }.getOrNull()
            if (decoded == null) {
                redecodedFailed++
                continue
            }
            val stillCurrent = currentLayerIfStillRequested(layer.id, requestedIdentity, expectedInstance = layer)
            if (stillCurrent == null) {
                // Obsoleto: la capa cambió (o desapareció) mientras se
                // decodificaba. Se descarta el bitmap acá mismo — NUNCA
                // se publica ni se deja "por si acaso".
                decoded.recycle()
                redecodedStale++
                continue
            }
            // `publish` nunca descarta en silencio: si por cualquier
            // motivo ya había algo pendiente (no debería, se filtró con
            // el `continue` de arriba, pero el hilo principal pudo haber
            // publicado algo nuevo en el intervalo), se recicla acá.
            stillCurrent.pendingBitmap.publish(decoded, requestedIdentity.revision)?.recycle()
            redecodedOk++
        }
        AppLogger.i(
            TAG,
            "DIAGNÓSTICO onSurfaceCreated: ${layersAtStart.size} capa(s) vistas · " +
                "$alreadyHadBitmap ya tenían bitmap listo · $redecodedOk se re-decodificaron OK · " +
                "$redecodedFailed fallaron al re-decodificar · $redecodedStale se descartaron por quedar " +
                "obsoletas mientras decodificaban · generación de contexto=$newGeneration"
        )

        // Contexto + shader + bookkeeping de texturas ya están listos —
        // falta únicamente el viewport real, que llega en
        // onSurfaceChanged (contrato de GLSurfaceView: Android SIEMPRE
        // lo llama después de onSurfaceCreated, antes del primer
        // onDrawFrame).
        transitionTo(GLRendererLifecycleState.SURFACE_READY)
    }

    /**
     * FASE 3.1.2/3.1.3 — punto único de validación de identidad/versión.
     *
     * FASE 3.1.3-R1 — HOTFIX: hasta esta corrección, esta función (y el
     * commit atómico de `uploadTextureIfNeeded`, que no la usaba en
     * absoluto) validaban SOLO `layerId + sourceUri + contentRevision`.
     * Eso es insuficiente para demostrar que la INSTANCIA concreta de
     * [Layer] que inició un trabajo (decode o upload) sigue siendo la
     * instancia viva del proyecto — un defecto real, no hipotético:
     *
     * - `removeLayer()` (ver EditorViewModel) NUNCA muta los campos del
     *   objeto `Layer` que elimina — simplemente lo saca de la lista.
     *   Si `GLRenderer` ya tenía una referencia capturada a ESE objeto
     *   (por un upload en curso), releer `capturedLayer.sourceUri`/
     *   `capturedLayer.contentRevision` sobre esa MISMA referencia
     *   siempre iba a dar los mismos valores que se capturaron — la
     *   validación pasaba trivialmente, comparando un objeto contra sí
     *   mismo, sin darse cuenta de que ese objeto ya no pertenece al
     *   proyecto.
     * - Un reemplazo de instancia con el mismo `sourceUri`/`contentRevision`
     *   por coincidencia (poco común, pero posible) tampoco se detectaba.
     *
     * La corrección: SIEMPRE volver a buscar la capa VIVA por `layerId`
     * en `getLayers()` — nunca reutilizar directamente los campos de la
     * referencia capturada — y, cuando el llamador tiene esa referencia
     * a mano (`expectedInstance`), exigir además identidad REFERENCIAL
     * (`===`) contra ella. `restoreSnapshot` (undo/redo) muta la MISMA
     * instancia en el lugar (ver su propio código) — por eso la identidad
     * referencial es compatible con undo/redo: la instancia sigue siendo
     * la misma, son sus campos (`sourceUri`/`contentRevision`) los que ya
     * habrán cambiado, y esos se siguen comparando por valor como
     * siempre. `removeLayer`/un reemplazo real vía `.copy()` sí cambian
     * la instancia — y son, precisamente, los casos que esta comparación
     * adicional tiene que rechazar.
     *
     * FASE 3.1.3-R2 — [requestedIdentity] ahora es un único
     * [Layer.ContentIdentity] capturado por el llamador (una lectura
     * atómica de `layer.contentIdentity`), no `sourceUri`/`contentRevision`
     * por separado: comparar dos campos leídos en dos instantes distintos
     * podía dar, en teoría, un resultado "entrecortado" (ver el KDoc de
     * `Layer.contentIdentity` para el detalle completo). Comparar un
     * único objeto ya capturado (`it.contentIdentity == requestedIdentity`)
     * cierra esa clase de problema por construcción.
     */
    private fun currentLayerIfStillRequested(
        layerId: String,
        requestedIdentity: Layer.ContentIdentity,
        expectedInstance: Layer? = null
    ): Layer? =
        getLayers().firstOrNull { it.id == layerId }
            ?.takeIf { it.contentIdentity == requestedIdentity }
            ?.takeIf { expectedInstance == null || it === expectedInstance }

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
        // los campos GPU/CPU (`pendingBitmap`/`widthPx`/`heightPx`, y el
        // pedido de invalidación de textura) que siguen viviendo en el
        // propio `Layer` porque su dueño real es este mismo hilo de GL —
        // el texture id GL en sí ya no vive acá, ver [layerTextures].
        val renderSnapshot = getRenderSnapshot()
        val liveLayersById = getLayers().associateBy { it.id }
        val orderedSnapshotLayers = renderSnapshot.layers.sortedBy { it.zIndex }
        val timeMs = getPlayheadMs()
        val override = getLiveOverride()

        // FASE 3.1 — no crear memory leaks: cuando el ViewModel elimina
        // una capa (ver EditorViewModel.removeLayer), esa capa
        // simplemente deja de aparecer en [getLayers()] — nunca había,
        // hasta esta fase, ningún punto donde se llamara
        // `glDeleteTextures` para la textura GPU que esa capa tenía
        // subida (el `Layer` en sí se vuelve basura para el GC, pero el
        // GC no sabe nada de recursos GPU). Con el registro centralizado
        // acá es un chequeo trivial: cualquier entrada cuya capa ya no
        // esté viva se libera explícitamente.
        if (layerTextures.isNotEmpty()) {
            val staleLayerIds = layerTextures.keys - liveLayersById.keys
            for (staleId in staleLayerIds) {
                layerTextures.remove(staleId)?.let { drawer.deleteTexture(it.glTextureId) }
            }
        }
        // FASE 3.1.1 — poda análoga para el registro de re-decodes
        // fallidos: una capa eliminada no debe seguir ocupando una
        // entrada acá para siempre.
        if (failedRedecodeIds.isNotEmpty()) {
            failedRedecodeIds.retainAll(liveLayersById.keys)
        }

        for (snapLayer in orderedSnapshotLayers) {
            val liveLayer = liveLayersById[snapLayer.id] ?: continue
            // FASE 3.1 — único punto donde se atiende un pedido de
            // invalidación de textura hecho por el hilo principal (ver
            // Layer.requestTextureInvalidation): el pedido en sí es solo
            // una bandera.
            //
            // FASE 3.1.3 — PROBLEMA 12 del informe: a diferencia de las
            // fases anteriores, ACÁ YA NO se borra la textura vigente de
            // la capa. Ese borrado ahora es responsabilidad EXCLUSIVA de
            // `uploadTextureIfNeeded`/`performLazyRedecodeIfNeeded`, que
            // solo lo hacen DESPUÉS de confirmar (validación final,
            // post-upload) que hay un reemplazo válido y ya comprometido.
            // Motivo real, no cosmético: borrar la textura vieja ACÁ, de
            // forma eager, apenas se pide la invalidación —incluso antes
            // de que el reemplazo esté listo (el decode puede tardar
            // frames)— dejaba a la capa sin NINGUNA textura válida
            // durante esa ventana: exactamente el síntoma "capas
            // transparentes" que esta familia de fases viene cerrando.
            // Con el borrado diferido, la capa sigue mostrando su
            // textura anterior (ligeramente desactualizada, pero válida)
            // hasta que la nueva está lista y confirmada.
            //
            // La segunda línea de defensa de Fase 3.1.2 (revisión
            // desincronizada sin pedido explícito) tampoco hace falta acá
            // como bloque aparte: `performLazyRedecodeIfNeeded` la
            // detecta por su cuenta (ver su propio comentario) y
            // `uploadTextureIfNeeded` es quien decide, con el protocolo
            // completo de commit/rollback, si corresponde reemplazar el
            // registro — nunca este bucle directamente.
            if (liveLayer.consumeTextureInvalidationRequest()) {
                // Un pedido de invalidación NUEVO es una oportunidad
                // nueva de re-decode, sin importar si el intento anterior
                // (de una invalidación previa) había fallado.
                failedRedecodeIds.remove(liveLayer.id)
            }
            uploadTextureIfNeeded(liveLayer)
            val liveTextureId = layerTextures[liveLayer.id]?.glTextureId ?: -1
            if (liveTextureId < 0 || !snapLayer.visible) continue
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
                textureId = liveTextureId,
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
        // subir su textura: si terminó con una textura GL válida, si está
        // marcada visible, y las dimensiones del viewport. Esto dice
        // exactamente cuál de las tres cosas (capas vacías, texturas sin
        // subir, o capas ocultas) es la que está dejando el lienzo en
        // blanco.
        if (!hasLoggedFirstFrame) {
            hasLoggedFirstFrame = true
            val detail = orderedSnapshotLayers.joinToString("; ") { snap ->
                val live = liveLayersById[snap.id]
                val textureId = live?.let { layerTextures[it.id]?.glTextureId } ?: -1
                "'${live?.name ?: snap.id}': textureId=$textureId, visible=${snap.visible}, ${live?.widthPx ?: 0}x${live?.heightPx ?: 0}px"
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
        }
        // FASE 3.1 — BUG REAL DE OWNERSHIP encontrado en la auditoría:
        // acá ANTES había un `finally { bitmap.recycle() }`, con el mismo
        // criterio (equivocado, para este caso) que uploadTextureIfNeeded
        // usa para los bitmaps de capa. La diferencia real es quién es el
        // DUEÑO del bitmap en cada caso:
        //   - el bitmap de una capa (`Layer.pendingBitmap`) es un valor
        //     de un solo uso: se decodifica, se sube, y nadie más vuelve
        //     a necesitar ESA instancia — reciclarlo ahí es correcto.
        //   - este bitmap de la cuadrícula lo crea y CONSERVA Compose en
        //     un `remember(...)` (ver EditorScreen — la cuadrícula), con
        //     la MISMA identidad reutilizada en cada frame mientras nada
        //     cambie, JUSTAMENTE para no volver a rasterizarla. Compose,
        //     no GLRenderer, es el dueño real de esta instancia.
        // El bug concreto que esto producía: al recrearse el contexto EGL
        // (volver de segundo plano, reabrir el proyecto), `updateGridTextureIfNeeded`
        // vuelve a ejecutar con el MISMO objeto `bitmap` de Compose (su
        // `remember` no cambió) — pero esa MISMA instancia ya había sido
        // reciclada la vez anterior. `drawer.uploadTexture(bitmap)` sobre
        // un bitmap ya reciclado explota (`IllegalStateException: Can't
        // use a recycled bitmap`), tirando abajo el hilo de GL. Este es
        // exactamente el caso que describe la sección "Context loss y
        // grid": el Bitmap CPU debe seguir disponible para reconstruir la
        // textura tras una recreación de contexto — reciclarlo acá se lo
        // impedía. Estrategia elegida (opción A del informe): Compose/UI
        // sigue siendo dueño del Bitmap, GL solo lo USA para subir la
        // textura y nunca lo recicla. No hay copia extra de por medio —
        // sigue siendo la misma única instancia, solo que ya no se
        // destruye del lado equivocado.
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
        performLazyRedecodeIfNeeded(layer)

        // FASE 3.1.3 — PROBLEMA CENTRAL de esta fase (TOCTOU real, no
        // hipotético): todo lo que sigue tiene que usar la identidad de
        // versión capturada EN ESTE INSTANTE — antes de consumir el
        // bitmap, antes de subirlo a GPU — y NUNCA volver a leer
        // `layer.contentRevision`/`layer.sourceUri` directamente después
        // de este punto. `drawer.uploadTexture(bitmap)` más abajo no es
        // instantáneo (clamp + `glTexImage2D` + `glFinish()` — hay un
        // `glFinish()` real, ver su propio comentario) y el hilo
        // principal corre en PARALELO REAL mientras tanto: puede
        // reemplazar la imagen de esta misma capa (bump de
        // `contentRevision`) en ese lapso. La Fase 3.1.2 cerró la mitad
        // del problema (que un bitmap VIEJO se suba); esta fase cierra la
        // otra mitad: que la METADATA del registro (`LayerTextureRecord`)
        // se etiquete con una revisión que YA NO es la del bitmap que
        // efectivamente se subió — antes de este fix, la línea de commit
        // hacía `layer.contentRevision` (una relectura, potencialmente ya
        // avanzada) en vez de reusar la revisión con la que en realidad
        // se validó y consumió el bitmap.
        //
        // FASE 3.1.3-R2 — `capturedIdentity` reemplaza los antiguos
        // `capturedRevision`/`capturedSourceUri` capturados por separado:
        // ver el KDoc de `Layer.contentIdentity` para el defecto real
        // (lectura entrecortada de un par de campos `@Volatile`
        // independientes) que esa única lectura atómica cierra.
        val capturedIdentity = layer.contentIdentity
        // El mismo criterio para el contexto EGL (ver PROBLEMA 10/11 del
        // informe): en esta arquitectura (GLSurfaceView.Renderer)
        // `onSurfaceCreated`/`onDrawFrame` corren siempre en el MISMO
        // hilo de GL y nunca se solapan entre sí — así que
        // `contextGeneration.value` no puede, hoy, cambiar en medio de
        // esta función. Se captura y se vuelve a comparar de todas
        // formas como red de seguridad explícita (INVARIANTE 2/8), no
        // porque exista una ventana real conocida.
        val capturedContextGeneration = contextGeneration.value

        // FASE 3.1.2 — `takeIfCurrent` sigue siendo la única operación
        // atómica de consumo, ahora validada contra la revisión YA
        // CAPTURADA arriba.
        val original = when (val consumption = layer.pendingBitmap.takeIfCurrent(capturedIdentity.revision)) {
            is SingleResourceHandoff.Consumption.Ready -> consumption.value
            is SingleResourceHandoff.Consumption.Stale -> {
                AppLogger.i(
                    TAG,
                    "Se descarta un bitmap pendiente OBSOLETO de la capa '${layer.name}' (llegó tarde: ya no " +
                        "corresponde a la contentRevision=${capturedIdentity.revision} vigente) — se recicla sin subir a GPU"
                )
                consumption.discarded.recycle()
                return
            }
            SingleResourceHandoff.Consumption.Empty -> return
        }
        // El bitmap decodificado ya está a resolución completa (ver
        // [ImageDecoding]). Acá, justo antes de subirlo a GL, es el
        // ÚNICO punto donde se respeta un límite — y es un techo real de
        // hardware (GL_MAX_TEXTURE_SIZE de esta GPU), no una reducción de
        // calidad elegida por la app. `clampForTexture` devuelve el mismo
        // bitmap sin tocar si ya entra en ese límite (el caso normal).
        //
        // FASE 3.1.3-R1 — HOTFIX (PROBLEMA 17 del informe): antes, esta
        // llamada corría FUERA de cualquier `try`, así que una excepción
        // acá (`Bitmap.createScaledBitmap` puede lanzar, p. ej. sin
        // memoria) escapaba de esta función entera SIN reciclar
        // `original` (ya único dueño de ese bitmap desde `takeIfCurrent`
        // más arriba — Bitmap es un recurso nativo pesado, esto era un
        // leak real) Y rompía la garantía documentada más abajo de "cada
        // capa se sube de forma aislada, si una falla las demás se
        // siguen dibujando": la excepción escapaba hasta el llamador de
        // `onDrawFrame`, no se quedaba contenida en esta capa.
        val bitmap = try {
            GpuTextureLimits.clampForTexture(original)
        } catch (t: Throwable) {
            AppLogger.e(
                TAG,
                "No se pudo preparar (clamp) el bitmap de la capa '${layer.name}' para subir a GPU — se recicla y se salta esta capa",
                t
            )
            original.recycle()
            return
        }
        try {
            // FASE 3.1.3 — PROBLEMA 12 del informe: a diferencia de la
            // versión anterior, la textura VIEJA (si la había) NO se
            // borra todavía acá. Se sube primero la nueva; solo si pasa
            // la validación final de abajo se la compromete al registro
            // Y RECIÉN AHÍ se borra la vieja. Dos motivos reales, no
            // cosméticos: (1) si `uploadTexture` lanza una excepción (el
            // `catch` de abajo), la capa NO se queda sin ninguna textura
            // — sigue mostrando la vieja, ligeramente desactualizada,
            // en vez de quedar invisible hasta el próximo redecode; (2)
            // si el resultado termina siendo STALE (ver más abajo), la
            // textura vieja — que sigue siendo válida para lo que la capa
            // representa AHORA MISMO — nunca se tocó.
            val previousRecord = layerTextures[layer.id]
            val newTextureId = drawer.uploadTexture(bitmap)

            // FASE 3.1.3-R1/R2 — validación de identidad (instancia +
            // contenido + contexto EGL), reutilizada tal cual por el gate
            // real de R3 más abajo. Ver `currentLayerIfStillRequested`
            // para el detalle de qué compara exactamente.
            fun stillAuthorizedToCommit(): Boolean =
                currentLayerIfStillRequested(layer.id, capturedIdentity, expectedInstance = layer) != null &&
                    contextGeneration.value == capturedContextGeneration

            // ROLLBACK TEMPRANO (barato, FUERA del gate a propósito): si
            // la capa ya quedó obsoleta apenas terminó el upload, ni hace
            // falta competir por el gate para confirmarlo — esto es
            // exactamente lo que la sección 9 del informe de esta fase
            // permite como optimización ("CPU/Bitmap work → GPU upload →
            // acquire commit gate → final validation → commit → release
            // gate"): nada obliga a adquirir el gate para un commit que
            // de todas formas se va a rechazar. Esta comprobación NO
            // reemplaza ni sustituye la validación real dentro del gate
            // — es puramente un atajo de rendimiento.
            if (!stillAuthorizedToCommit()) {
                drawer.deleteTexture(newTextureId)
                AppLogger.i(
                    TAG,
                    "Se descarta una textura recién subida para la capa '${layer.name}' — la capa cambió " +
                        "(identidad de contenido/contexto EGL) mientras se subía a GPU; NO se compromete al " +
                        "registro (rollback temprano, fuera del commit gate)"
                )
                return
            }

            // FASE 3.1.3-R3 — REAL ATOMIC GPU COMMIT GATE.
            //
            // Esto reemplaza la "compuerta en dos etapas" de R2 (dos
            // lecturas consecutivas de `stillAuthorizedToCommit()`, la
            // segunda como última expresión antes de escribir) — esa
            // técnica reduce la ventana `validation → commit` al mínimo
            // estructural, pero sigue siendo, literalmente, dos lecturas
            // sin ninguna primitiva de exclusión mutua entre ellas: nada
            // le impedía al hilo principal ejecutar una mutación
            // relevante de `Layer` justo entre esa segunda lectura y la
            // escritura de `layerTextures[layer.id]`. R2 documentaba esa
            // limitación explícitamente y nunca afirmó "race-free" —
            // correctamente: esa ventana formal seguía abierta.
            //
            // La corrección real: la validación FINAL que autoriza el
            // commit corre DENTRO de la misma sección crítica que la
            // escritura del registro — `acquireGate() → validate() →
            // commit() → releaseGate()`, nunca `validateOutsideGate() →
            // acquireGate() → commit()` (sección 10 del informe: esta
            // segunda forma sigue siendo vulnerable, porque la mutación
            // podría colarse entre "validar" y "adquirir"). Mientras
            // `commitGate` está adquirido acá, ninguna mutación relevante
            // de identidad de Layer (`replaceLayer`/`replaceLayers`/
            // `removeLayer`/`updateContentIdentity`, ver
            // `EditorViewModel` sección "FASE 3.1.3-R3") puede completarse
            // — esas mutaciones adquieren EXACTAMENTE el mismo
            // `LayerGpuCommitGate` (misma instancia, inyectada por
            // referencia) alrededor de sí mismas. O la mutación ya
            // terminó antes de que este bloque logre adquirir el gate
            // (en cuyo caso `stillAuthorizedToCommit()` la va a ver y va
            // a rechazar el commit acá abajo, correctamente) o el commit
            // ya terminó — con o sin éxito — antes de que la mutación
            // pueda siquiera empezar a adquirir el gate. Nunca un
            // resultado intercalado.
            //
            // La sección crítica es deliberadamente MINÚSCULA: ni el
            // decode, ni `drawer.uploadTexture` (ya terminaron arriba),
            // ni el borrado de la textura anterior, ni la actualización
            // de `widthPx`/`heightPx` viven dentro de este bloque — solo
            // la comparación de identidad y la escritura del `Map` en
            // memoria (ver el KDoc de `LayerGpuCommitGate` para la
            // justificación completa de qué queda adentro/afuera).
            var authorizedInsideGate = false
            commitGate.withGate {
                if (stillAuthorizedToCommit()) {
                    authorizedInsideGate = true
                    // ATOMIC COMMIT — la metadata sale ÍNTEGRA de lo
                    // capturado al principio de esta función
                    // (`capturedIdentity.revision`), nunca de una
                    // relectura de `layer.contentRevision` en este punto
                    // (ese era exactamente el bug real de Fase 3.1.3).
                    layerTextures[layer.id] =
                        LayerTextureRecord(glTextureId = newTextureId, contentRevision = capturedIdentity.revision)
                }
            }

            if (!authorizedInsideGate) {
                drawer.deleteTexture(newTextureId)
                AppLogger.i(
                    TAG,
                    "Se descarta una textura recién subida para la capa '${layer.name}' — la capa cambió " +
                        "justo entre la validación temprana y la validación final (ventana cerrada por el " +
                        "commit gate real de Fase 3.1.3-R3, no por una segunda lectura optimista); NO se " +
                        "compromete al registro"
                )
                return
            }

            // Efectos del commit ya confirmado — fuera del gate a
            // propósito (ver arriba): ninguno de los dos participa en la
            // decisión de autorización, así que no necesitan la misma
            // exclusión mutua. El borrado de la textura anterior es un
            // recurso GL que solo el propio hilo de GL toca en cualquier
            // punto (nunca hay una segunda escritura concurrente posible
            // acá); `widthPx`/`heightPx` son `@Volatile` para la
            // visibilidad hacia el hilo principal, no para exclusión.
            if (previousRecord != null && previousRecord.glTextureId != newTextureId) {
                drawer.deleteTexture(previousRecord.glTextureId)
            }
            layer.widthPx = bitmap.width
            layer.heightPx = bitmap.height
        } catch (t: Throwable) {
            AppLogger.e(TAG, "No se pudo subir la textura de la capa '${layer.name}' a la GPU — esa capa no se va a dibujar", t)
        } finally {
            // Se libera/descarta el bitmap SIEMPRE, haya salido bien, mal,
            // o terminado en rollback — si falló o quedó stale, reintentar
            // con el mismo bitmap en el próximo frame solo repetiría el
            // mismo resultado para siempre; el bitmap en sí ya cumplió su
            // función (CPU-side), lo que importa de acá en más es la
            // textura GPU (o su ausencia).
            bitmap.recycle()
        }
    }

    /**
     * FASE 3.1.1 — HOTFIX post-cierre de un BUG REAL introducido por la
     * Fase 3.1 (reportado por el usuario: "reemplazo la imagen y si
     * retrocedo [undo] no vuelve la anterior" / "estuve editando y las
     * capas se pusieron transparentes"), endurecido en FASE 3.1.2 con
     * validación real de identidad/versión (PROBLEMA 3 del informe).
     *
     * Causa raíz original (Fase 3.1.1): varios lugares del hilo principal
     * — `restoreSnapshot` (undo/redo de un cambio de `sourceUri`),
     * `LayerContentState.applyTo` (usado por `discardChangesAndExit`),
     * `revertLayerEditSession` y `revertLayerToUri` — hacen, a propósito,
     * `layer.pendingBitmap.clear()` seguido de
     * `layer.requestTextureInvalidation()`. La invalidación SÍ borra la
     * textura GL vigente (correcto: la imagen cambió), pero limpiar el
     * pendiente sin publicar uno nuevo significa que nada dispara un
     * redecode. La documentación de esos mismos sitios afirmaba que "el
     * motor GL decodifica de nuevo desde `sourceUri` la primera vez que
     * encuentra una capa sin textura ni bitmap pendiente" — pero ese
     * redecode SOLO existía en `onSurfaceCreated` (una vez por cada
     * recreación de contexto EGL), nunca acá. Este método es el que hace,
     * de verdad, ese redecode perezoso — con la MISMA validación de
     * identidad/versión que [onSurfaceCreated] (ver
     * [currentLayerIfStillRequested]): el hilo principal corre en
     * paralelo real mientras este decode síncrono se ejecuta, así que
     * puede reemplazar/revertir esta misma capa OTRA VEZ mientras
     * decodifica — publicar sin revalidar podría resucitar contenido
     * viejo.
     *
     * Se intenta como máximo una vez por cada invalidación real (ver
     * [failedRedecodeIds] / `onDrawFrame`) para no reintentar sin parar,
     * 60 veces por segundo, un decode que ya falló.
     */
    private fun performLazyRedecodeIfNeeded(layer: Layer) {
        if (layer.pendingBitmap.peek() != null) return
        // FASE 3.1.3 — reemplaza el gate anterior (`layerTextures[layer.id] != null`
        // a secas). Con el borrado de texturas ahora DIFERIDO hasta el commit
        // (ver el comentario grande en `onDrawFrame` y en `uploadTextureIfNeeded`
        // sobre PROBLEMA 12), una capa puede perfectamente TENER una entrada en
        // `layerTextures` que ya está OBSOLETA (de una revisión vieja, todavía sin
        // reemplazar) — el gate viejo se quedaría conforme con esa textura vieja y
        // jamás dispararía el redecode, dejando a la capa mostrando contenido
        // incorrecto para siempre. El chequeo correcto es "¿la textura que YA
        // tengo, si tengo alguna, es la de la revisión VIGENTE?" — si no lo es
        // (o no hay ninguna), corresponde intentar el redecode.
        val existingRecord = layerTextures[layer.id]
        if (existingRecord != null && existingRecord.contentRevision == layer.contentIdentity.revision) return
        if (layer.id in failedRedecodeIds) return

        // FASE 3.1.3-R2 — una sola lectura atómica de la identidad de
        // contenido (ver el KDoc de `Layer.contentIdentity`), en vez de
        // `sourceUri`/`contentRevision` por separado.
        val requestedIdentity = layer.contentIdentity
        val maxTextureDimension = GpuTextureLimits.queryMaxTextureSize()
        val redecoded = runCatching {
            ImageDecoding.decodeSampledFromUri(contentResolver, requestedIdentity.sourceUri, maxDimension = maxTextureDimension)
        }.getOrNull()
        if (redecoded == null) {
            AppLogger.e(
                TAG,
                "No se pudo re-decodificar la capa '${layer.name}' desde sourceUri tras una invalidación " +
                    "(undo/redo, revertir edición, o descartar cambios) — queda sin textura hasta la próxima " +
                    "invalidación o recreación de contexto"
            )
            failedRedecodeIds.add(layer.id)
            return
        }
        val stillCurrent = currentLayerIfStillRequested(layer.id, requestedIdentity, expectedInstance = layer)
        if (stillCurrent == null) {
            redecoded.recycle()
            AppLogger.i(TAG, "Descartado un redecode perezoso de la capa '${layer.name}' — cambió mientras decodificaba")
            return
        }
        stillCurrent.pendingBitmap.publish(redecoded, requestedIdentity.revision)?.recycle()
    }
}
