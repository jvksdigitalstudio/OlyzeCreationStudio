package com.yeivikas.olyzecs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.core.PixelColorSource
import com.yeivikas.olyzecs.engine.render.AndroidGLRenderHost
import com.yeivikas.olyzecs.engine.render.LayerGpuCommitGate
import com.yeivikas.olyzecs.engine.render.RenderSnapshot
import com.yeivikas.olyzecs.engine.scene.Layer

private const val TAG = "GLPreview"

/**
 * Puente entre Compose y el backend de render Android (OpenGL ES) de
 * este proyecto.
 *
 * FASE 4.3 (RENDER BOUNDARY — subfase "GLPreview / Android Render Host
 * Boundary"): esta función ya NO crea el `GLSurfaceView` ni el
 * `GLRenderer` directamente — eso se movió a
 * [com.yeivikas.olyzecs.engine.render.AndroidGLRenderHost] (ver su KDoc
 * para el razonamiento completo: es una extracción de responsabilidad,
 * no una reescritura — mismas llamadas, mismo orden, cero cambio de
 * comportamiento). `GLPreview` sigue siendo responsable exclusivamente
 * de la parte que SÍ es genuinamente suya:
 *
 * - envolver los parámetros recibidos en `rememberUpdatedState` (para
 *   que el `factory` de `AndroidView`, que Compose ejecuta una única
 *   vez, no quede atado a los valores del primer frame);
 * - decidir CUÁNDO llamar a `pause()`/`resume()`/`release()` del host
 *   (a través de `DisposableEffect` + `LifecycleEventObserver` sobre
 *   `LocalLifecycleOwner`, y del callback `onRelease` de `AndroidView`);
 * - devolver la `View` que `AndroidView` espera.
 *
 * El host decide CÓMO hacerlo — `GLPreview` ya no sabe (ni necesita
 * saber) que por debajo hay un `GLSurfaceView`/EGL/`Handler` de hilo
 * principal: eso es exactamente la separación que pide esta
 * intervención (UI/Compose ≠ Android Render Host ≠ backend GL).
 *
 * IMPORTANTE (sin cambios respecto a la versión anterior): el `factory`
 * de `AndroidView` se ejecuta UNA sola vez (crea el host una única vez,
 * no en cada recomposición). Si le pasáramos las lambdas recibidas
 * directamente, el host quedaría atado para siempre a los valores que
 * existían en el instante exacto de la primera composición. La solución
 * estándar de Compose es [rememberUpdatedState]: envolvemos cada lambda
 * en un State que SÍ se actualiza en cada recomposición, y le pasamos al
 * host una lambda estable que simplemente lee `.value` en cada frame.
 */
@Composable
fun GLPreview(
    modifier: Modifier = Modifier,
    getLayers: () -> List<Layer>,
    // FASE 2 — snapshot inmutable de los datos LÓGICOS de cada capa
    // (zIndex/visible/parallax/look/cámara), capturado de forma segura
    // ante concurrencia — ver RenderSnapshot.kt y el comentario grande en
    // GLRenderer sobre por qué esto va separado de [getLayers].
    getRenderSnapshot: () -> RenderSnapshot,
    getPlayheadMs: () -> Long,
    // FASE 3.1.3-R3 — misma instancia de [LayerGpuCommitGate] que usa
    // `EditorViewModel` para las mutaciones relevantes de identidad de
    // Layer (ver el KDoc de esa clase y el comentario "FASE 3.1.3-R3" en
    // `EditorViewModel.kt`). Se pasa por referencia estable (no envuelta
    // en un lambda/`rememberUpdatedState` como el resto de los
    // parámetros de arriba): a diferencia de `getLayers`/`getRenderSnapshot`/
    // etc. (que leen un `StateFlow` que SÍ cambia en cada recomposición),
    // el propio objeto gate nunca se reemplaza durante la vida del
    // `EditorViewModel` — es un componente de sincronización, no un dato,
    // así que no tiene sentido "actualizarlo" en cada frame.
    commitGate: LayerGpuCommitGate,
    getLiveOverride: () -> Pair<String, CameraFrame>? = { null },
    // Cuadrícula de composición ya rasterizada a bitmap (o null si está
    // apagada) — ver comentario completo en EditorScreen.rasterizeGridBitmap.
    // GLRenderer la sube como una textura más y la dibuja PRIMERO, antes
    // que las capas reales, para que quede DETRÁS de ellas (comportamiento
    // de canvas profesional) en vez de flotar siempre encima como un
    // overlay de Compose.
    getGridBitmap: () -> Bitmap? = { null },
    // Entrega SOLO el contrato de lectura de pixel (PixelColorSource), no
    // la clase concreta GLRenderer — quien llama (EditorScreen) puede
    // guardarla y usarla más tarde para el cuentagotas sin conocer nada
    // más del renderer real. GLPreview es el único punto del proyecto que
    // sabe que, hoy, la implementación de PixelColorSource es GLRenderer
    // (indirectamente, a través del host — ver AndroidGLRenderHost).
    onRendererReady: (PixelColorSource) -> Unit = {},
    // AUDITORÍA — "flash verde de milisegundos al reabrir un proyecto":
    // ver el KDoc de `GLRenderer.onAllVisibleLayersPainted` para la causa
    // raíz completa. Este callback llega desde el hilo de GL — el host
    // (AndroidGLRenderHost) ya lo reenvía al hilo principal antes de
    // invocarlo, porque quien lo conecta (EditorScreen) va a tocar un
    // `MutableState` de Compose, y Compose solo es seguro desde el hilo
    // principal.
    onAllVisibleLayersPainted: () -> Unit = {}
) {
    val currentGetLayers by rememberUpdatedState(getLayers)
    val currentGetRenderSnapshot by rememberUpdatedState(getRenderSnapshot)
    val currentGetPlayheadMs by rememberUpdatedState(getPlayheadMs)
    val currentGetLiveOverride by rememberUpdatedState(getLiveOverride)
    val currentGetGridBitmap by rememberUpdatedState(getGridBitmap)
    val currentOnAllVisibleLayersPainted by rememberUpdatedState(onAllVisibleLayersPainted)

    // FASE 3 — Render / GL Lifecycle.
    //
    // BUG REAL encontrado en la auditoría de esa fase: no existía NINGÚN
    // puente entre el ciclo de vida real de Android (Activity) y el
    // ciclo de vida propio de GLSurfaceView (`onPause()`/`onResume()`).
    // Compose NO llama a estos métodos automáticamente — son responsa-
    // bilidad explícita de quien integra la vista, documentada en la
    // propia guía de Android para GLSurfaceView, y acá simplemente no
    // estaba hecha. Con `renderMode = RENDERMODE_CONTINUOUSLY` (ver
    // AndroidGLRenderHost) esto significa que el hilo de render de GL
    // seguía dibujando ~60 veces por segundo aunque la app pasara a
    // segundo plano — consumo de batería/GPU innecesario, y riesgo real
    // de llamar a GLES contra una Surface que Android ya invalidó por
    // detrás mientras la app no era visible.
    //
    // Se guarda la referencia al host creado en `factory` (que Compose
    // ejecuta una única vez, ver KDoc de la función) para que el
    // observer de abajo pueda llamar a `pause()`/`resume()` sobre la
    // instancia real, sin necesitar tocar MainActivity ni pasar la
    // Activity completa hacia acá — mismo patrón (DisposableEffect +
    // LifecycleEventObserver sobre LocalLifecycleOwner) que ya usa
    // MainActivity para el guardado en ON_STOP, así que es consistente
    // con una convención ya establecida en el proyecto, no una técnica
    // nueva.
    val renderHostRef = remember { mutableStateOf<AndroidGLRenderHost?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val host = renderHostRef.value ?: return@LifecycleEventObserver
            when (event) {
                // ON_PAUSE (no ON_STOP): es el mismo punto que documenta
                // Android para GLSurfaceView — más temprano que ON_STOP,
                // frena el hilo de GL antes de que la ventana deje de
                // ser interactiva, sin depender de si el resto de la
                // pantalla llega a ON_STOP o no (p.ej. un diálogo del
                // sistema encima ya dispara ON_PAUSE).
                Lifecycle.Event.ON_PAUSE -> host.pause()
                Lifecycle.Event.ON_RESUME -> host.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val host = AndroidGLRenderHost.create(
                context = context,
                contentResolver = context.contentResolver,
                getLayers = { currentGetLayers() },
                getRenderSnapshot = { currentGetRenderSnapshot() },
                getPlayheadMs = { currentGetPlayheadMs() },
                // FASE 3.1.3-R3 — misma instancia recibida arriba, sin
                // pasar por `rememberUpdatedState` (ver el comentario en
                // el parámetro de la función): el `factory` de
                // `AndroidView` corre una sola vez, y el gate no
                // necesita "actualizarse" entre recomposiciones.
                commitGate = commitGate,
                getLiveOverride = { currentGetLiveOverride() },
                getGridBitmap = { currentGetGridBitmap() },
                onAllVisibleLayersPainted = { currentOnAllVisibleLayersPainted() }
            )
            onRendererReady(host.renderer)
            renderHostRef.value = host
            host.view
        },
        onRelease = { view ->
            // FASE 3 — GLPreview salió de composición para siempre (por
            // ejemplo: "Mis proyectos" reemplaza al editor entero en el
            // árbol de Compose, ver MainActivity — la navegación de esta
            // app no apila pantallas, las reemplaza). GLSurfaceView no
            // tiene un método "destroy()" propio; `onPause()` (a través
            // de `host.release()`) es, según la documentación de
            // Android, la forma correcta de frenar su hilo de render de
            // forma limpia y definitiva cuando la vista se descarta —
            // sin esto, con RENDERMODE_CONTINUOUSLY el GLThread de la
            // vista vieja seguía vivo renderizando hacia una Surface que
            // Compose ya desconectó, indefinidamente, hasta que el
            // recolector de basura decidiera limpiar el objeto (si es
            // que lo hacía: un Thread en ejecución activa es en sí mismo
            // una raíz de GC — fuga real de hilo, no solo de memoria).
            //
            // La comprobación de identidad de abajo (`renderHostRef.value?.view === view`)
            // es la MISMA guarda que existía antes de esta extracción
            // (`if (glSurfaceViewRef.value === view)`) — preservada tal
            // cual: solo se limpia/libera la referencia guardada si de
            // verdad corresponde a la vista que Compose está liberando
            // ahora, nunca una referencia más nueva que ya la haya
            // reemplazado.
            if (renderHostRef.value?.view === view) {
                AppLogger.i(TAG, "GLPreview.onRelease: pausando el render host antes de descartarlo")
                renderHostRef.value?.release()
                renderHostRef.value = null
            }
        }
    )
}

