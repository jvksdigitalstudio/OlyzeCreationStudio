package com.yeivikas.olyzecs.ui

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.core.PixelColorSource
import com.yeivikas.olyzecs.engine.render.GLRenderer
import com.yeivikas.olyzecs.engine.scene.Layer

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
    val currentGetPlayheadMs by rememberUpdatedState(getPlayheadMs)
    val currentGetLiveOverride by rememberUpdatedState(getLiveOverride)
    val currentGetGridBitmap by rememberUpdatedState(getGridBitmap)

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
                    getPlayheadMs = { currentGetPlayheadMs() },
                    getLiveOverride = { currentGetLiveOverride() },
                    getGridBitmap = { currentGetGridBitmap() }
                )
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                onRendererReady(renderer)
            }
        }
    )
}
