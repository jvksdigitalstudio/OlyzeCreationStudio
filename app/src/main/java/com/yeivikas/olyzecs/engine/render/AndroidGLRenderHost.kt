package com.yeivikas.olyzecs.engine.render

import android.content.Context
import android.content.ContentResolver
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.core.PixelColorSource
import com.yeivikas.olyzecs.engine.scene.Layer

/**
 * FASE 4.3 (RENDER BOUNDARY — subfase "GLPreview / Android Render Host
 * Boundary"): la responsabilidad B de la separación de tres capas que
 * pide esta intervención —
 *
 * ```
 * GLPreview (UI/Compose)  →  AndroidGLRenderHost (este archivo)  →  GLRenderer (backend GL)
 * ```
 *
 * Antes de esta clase, `GLPreview.kt` (un `@Composable`) mezclaba dos
 * responsabilidades reales y distintas dentro del mismo bloque
 * `factory = { context -> ... }` de `AndroidView`:
 *
 * 1. Integración Compose (leer `Modifier`, envolver lambdas con
 *    `rememberUpdatedState`, devolver la `View` que `AndroidView`
 *    espera) — sigue siendo responsabilidad de `GLPreview`, no se tocó.
 * 2. Crear/configurar el `GLSurfaceView` (versión de contexto EGL,
 *    formato de color, `Z-order`), construir el `GLRenderer` con todas
 *    sus dependencias, conectar el renderer a la vista, elegir el
 *    `renderMode`, puentear el callback `onAllVisibleLayersPainted`
 *    (que llega del hilo de GL) de vuelta al hilo principal, y frenar/
 *    reanudar/liberar ese mismo `GLSurfaceView` según el lifecycle real
 *    de Android — esto es, íntegramente, responsabilidad de "Android
 *    Render Host" (sección 5-B del prompt maestro de esta
 *    intervención), no de UI. Ahora vive acá.
 *
 * Esto es una EXTRACCIÓN, no una reescritura: cada línea de este
 * archivo es exactamente la misma llamada, en el mismo orden, que ya
 * existía dentro de `GLPreview.kt` — mismo `GLSurfaceView`, mismo
 * `GLRenderer`, mismos parámetros, mismo `Handler(Looper.getMainLooper())`
 * para el puente de hilo, mismo criterio de `onPause()` tanto para
 * pausar como para liberar definitivamente (ver KDoc de [release]).
 * Cero cambio de comportamiento; solo cambió QUIÉN es dueño de ese
 * código.
 *
 * No reemplaza ni duplica ningún mecanismo de ownership de GPU — no
 * toca `LayerGpuCommitGate`/`SingleResourceHandoff`/`GpuContextGeneration`/
 * `RenderSnapshot`, todos siguen exactamente donde estaban, simplemente
 * pasando a través de este host camino a `GLRenderer` igual que antes.
 *
 * `GLPreview` sigue siendo el único creador real de esta clase (a través
 * de [AndroidGLRenderHost.create], llamado una única vez dentro del
 * `factory` de `AndroidView` — mismo punto de composición que antes) y
 * el único que le reenvía eventos de lifecycle — este host no se
 * autoconstruye ni se autogestiona, es un objeto plano sin ningún
 * lifecycle propio de Android (no extiende `LifecycleObserver` ni nada
 * equivalente): sigue siendo `GLPreview`, con su `DisposableEffect`
 * sobre `LocalLifecycleOwner`, quien decide CUÁNDO llamar a [pause]/
 * [resume]/[release] — este host solo sabe CÓMO hacerlo.
 */
class AndroidGLRenderHost private constructor(
    /**
     * La vista Android real. `GLPreview` la necesita para devolverla
     * desde el `factory` de `AndroidView` — sigue siendo, como antes de
     * esta extracción, el único punto del proyecto donde se construye
     * un `GLSurfaceView`.
     */
    val view: GLSurfaceView,
    /**
     * El `GLRenderer` real, para que `GLPreview` pueda seguir llamando
     * a `onRendererReady(host.renderer)` — mismo contrato exacto que
     * antes (`onRendererReady: (PixelColorSource) -> Unit`, ver KDoc de
     * `GLPreview`: nunca se entrega la clase concreta `GLRenderer` hacia
     * afuera de `GLPreview`, solo el contrato `PixelColorSource`).
     */
    val renderer: GLRenderer
) {

    /** Ver `Lifecycle.Event.ON_PAUSE` en `GLPreview.kt` — mismo motivo exacto, sin cambios. */
    fun pause() = view.onPause()

    /** Ver `Lifecycle.Event.ON_RESUME` en `GLPreview.kt` — mismo motivo exacto, sin cambios. */
    fun resume() = view.onResume()

    /**
     * `GLSurfaceView` no tiene un método `destroy()` propio;
     * `onPause()` es, según la documentación de Android, la forma
     * correcta de frenar su hilo de render de forma limpia y definitiva
     * cuando la vista se descarta — mismo razonamiento y misma llamada
     * que ya usaba `GLPreview.onRelease` antes de esta extracción (ver
     * ese comentario, preservado ahí, para el detalle completo del bug
     * de fuga de hilo que esto evita).
     */
    fun release() = view.onPause()

    companion object {
        /**
         * Construye el host real: mismo `GLSurfaceView`/`GLRenderer` y
         * exactamente la misma secuencia de configuración
         * (`setEGLContextClientVersion` → `setEGLConfigChooser` →
         * `holder.setFormat` → `setZOrderOnTop` → construir
         * `GLRenderer` → conectar `onAllVisibleLayersPainted` → `setRenderer`
         * → `renderMode`) que existía antes dentro del `factory` de
         * `GLPreview`.
         *
         * [onAllVisibleLayersPainted] se recibe ya listo para ejecutarse
         * en el hilo que corresponda — el puente de hilo GL→Main
         * ([mainHandler]) se resuelve ACÁ, no en `GLPreview`, porque es
         * responsabilidad del host garantizar que sus callbacks salgan
         * en un hilo seguro para quien los conecte, sin que el llamador
         * necesite saber que el renderer real vive en un hilo de GL.
         */
        fun create(
            context: Context,
            contentResolver: ContentResolver,
            getLayers: () -> List<Layer>,
            getRenderSnapshot: () -> RenderSnapshot,
            getPlayheadMs: () -> Long,
            commitGate: LayerGpuCommitGate,
            getLiveOverride: () -> Pair<String, CameraFrame>?,
            getGridBitmap: () -> Bitmap?,
            onAllVisibleLayersPainted: () -> Unit
        ): AndroidGLRenderHost {
            val mainHandler = Handler(Looper.getMainLooper())
            val renderer = GLRenderer(
                contentResolver = contentResolver,
                getLayers = getLayers,
                getRenderSnapshot = getRenderSnapshot,
                getPlayheadMs = getPlayheadMs,
                commitGate = commitGate,
                getLiveOverride = getLiveOverride,
                getGridBitmap = getGridBitmap
            )
            renderer.onAllVisibleLayersPainted = {
                mainHandler.post { onAllVisibleLayersPainted() }
            }
            val view = GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                setZOrderOnTop(false)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
            return AndroidGLRenderHost(view, renderer)
        }
    }
}
