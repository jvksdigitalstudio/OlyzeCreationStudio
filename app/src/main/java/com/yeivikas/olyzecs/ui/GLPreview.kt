package com.yeivikas.olyzecs.ui

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
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
import com.yeivikas.olyzecs.engine.render.GLRenderer
import com.yeivikas.olyzecs.engine.render.RenderSnapshot
import com.yeivikas.olyzecs.engine.scene.Layer

private const val TAG = "GLPreview"

/**
 * Puente entre Compose y el GLSurfaceView clásico de Android.
 *
 * IMPORTANTE: el `factory` de AndroidView se ejecuta UNA sola vez (crea la
 * vista y el renderer una única vez, no en cada recomposición). Si le
 * pasáramos las lambdas recibidas directamente, el GLRenderer quedaría
 * atado para siempre a los valores que existían en el instante exacto de
 * la primera composición. La solución estándar de Compose es
 * [rememberUpdatedState]: envolvemos cada lambda en un State que SÍ se
 * actualiza en cada recomposición, y le pasamos al renderer una lambda
 * estable que simplemente lee `.value` en cada frame.
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
    // sabe que, hoy, la implementación de PixelColorSource es GLRenderer.
    onRendererReady: (PixelColorSource) -> Unit = {}
) {
    val currentGetLayers by rememberUpdatedState(getLayers)
    val currentGetRenderSnapshot by rememberUpdatedState(getRenderSnapshot)
    val currentGetPlayheadMs by rememberUpdatedState(getPlayheadMs)
    val currentGetLiveOverride by rememberUpdatedState(getLiveOverride)
    val currentGetGridBitmap by rememberUpdatedState(getGridBitmap)

    // FASE 3 — Render / GL Lifecycle.
    //
    // BUG REAL encontrado en la auditoría de esta fase: no existía NINGÚN
    // puente entre el ciclo de vida real de Android (Activity) y el
    // ciclo de vida propio de GLSurfaceView (`onPause()`/`onResume()`).
    // Compose NO llama a estos métodos automáticamente — son responsa-
    // bilidad explícita de quien integra la vista, documentada en la
    // propia guía de Android para GLSurfaceView, y acá simplemente no
    // estaba hecha. Con `renderMode = RENDERMODE_CONTINUOUSLY` (ver más
    // abajo) esto significa que el hilo de render de GL seguía
    // dibujando ~60 veces por segundo aunque la app pasara a segundo
    // plano — consumo de batería/GPU innecesario, y riesgo real de
    // llamar a GLES contra una Surface que Android ya invalidó por
    // detrás mientras la app no era visible.
    //
    // Se guarda la referencia a la GLSurfaceView creada en `factory` (que
    // Compose ejecuta una única vez, ver comentario de clase más abajo)
    // para que el observer de abajo pueda llamar a `onPause()`/
    // `onResume()` sobre la instancia real, sin necesitar tocar
    // MainActivity ni pasar la Activity completa hacia acá — mismo
    // patrón (DisposableEffect + LifecycleEventObserver sobre
    // LocalLifecycleOwner) que ya usa MainActivity para el guardado en
    // ON_STOP, así que es consistente con una convención ya establecida
    // en el proyecto, no una técnica nueva.
    val glSurfaceViewRef = remember { mutableStateOf<GLSurfaceView?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val view = glSurfaceViewRef.value ?: return@LifecycleEventObserver
            when (event) {
                // ON_PAUSE (no ON_STOP): es el mismo punto que documenta
                // Android para GLSurfaceView — más temprano que ON_STOP,
                // frena el hilo de GL antes de que la ventana deje de
                // ser interactiva, sin depender de si el resto de la
                // pantalla llega a ON_STOP o no (p.ej. un diálogo del
                // sistema encima ya dispara ON_PAUSE).
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                setZOrderOnTop(false)
                val renderer = GLRenderer(
                    contentResolver = context.contentResolver,
                    getLayers = { currentGetLayers() },
                    getRenderSnapshot = { currentGetRenderSnapshot() },
                    getPlayheadMs = { currentGetPlayheadMs() },
                    getLiveOverride = { currentGetLiveOverride() },
                    getGridBitmap = { currentGetGridBitmap() }
                )
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                onRendererReady(renderer)
                glSurfaceViewRef.value = this
            }
        },
        onRelease = { view ->
            // FASE 3 — GLPreview salió de composición para siempre (por
            // ejemplo: "Mis proyectos" reemplaza al editor entero en el
            // árbol de Compose, ver MainActivity — la navegación de esta
            // app no apila pantallas, las reemplaza). GLSurfaceView no
            // tiene un método "destroy()" propio; `onPause()` es, según
            // la documentación de Android, la forma correcta de frenar
            // su hilo de render de forma limpia y definitiva cuando la
            // vista se descarta — sin esto, con RENDERMODE_CONTINUOUSLY
            // el GLThread de la vista vieja seguía vivo renderizando
            // hacia una Surface que Compose ya desconectó, indefinida-
            // mente, hasta que el recolector de basura decidiera
            // limpiar el objeto (si es que lo hacía: un Thread en
            // ejecución activa es en sí mismo una raíz de GC — fuga real
            // de hilo, no solo de memoria).
            AppLogger.i(TAG, "GLPreview.onRelease: pausando GLSurfaceView antes de descartarla")
            view.onPause()
            if (glSurfaceViewRef.value === view) {
                glSurfaceViewRef.value = null
            }
        }
    )
}
