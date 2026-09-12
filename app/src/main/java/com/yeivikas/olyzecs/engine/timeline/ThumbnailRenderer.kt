package com.yeivikas.olyzecs.engine.timeline

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.render.LayerDrawer
import com.yeivikas.olyzecs.engine.scene.Layer
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "ThumbnailRenderer"

/**
 * Renderiza un frame del proyecto —con todo el look cinematográfico ya
 * aplicado (grading, viñeta, grano, glow...)— a un Bitmap chico, usando el
 * mismo [LayerDrawer] que el preview en vivo y el exportador. Así la
 * miniatura que se ve en "Mis proyectos" es fiel de verdad al resultado
 * final, no solo la foto cruda de la capa de arriba.
 *
 * Corre en su PROPIO contexto EGL aislado, con una superficie "pbuffer"
 * (un framebuffer fuera de pantalla, sin ventana ni encoder detrás) — el
 * mismo motivo que [VideoExporter]: las texturas GL no se comparten entre
 * contextos EGL distintos, así que siempre se vuelve a decodificar cada
 * imagen desde su Uri en vez de reusar texturas que pudieran estar subidas
 * en el contexto del preview en vivo.
 */
object ThumbnailRenderer {

    fun render(
        context: Context,
        layers: List<Layer>,
        timeMs: Long = 0L,
        widthPx: Int = 360,
        heightPx: Int = 640
    ): Bitmap? {
        val visibleLayers = layers.filter { it.visible }
        if (visibleLayers.isEmpty()) return null

        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        val drawer = LayerDrawer()
        val uploadedTextures = mutableListOf<Int>()

        return try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                AppLogger.e(TAG, "No se pudo obtener el EGLDisplay por defecto para renderizar la miniatura")
                return null
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                AppLogger.e(TAG, "eglInitialize falló al preparar el renderizado de miniatura")
                return null
            }

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            val config = configs[0] ?: run {
                AppLogger.e(TAG, "No se encontró un EGLConfig compatible para renderizar la miniatura")
                return null
            }

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                AppLogger.e(TAG, "No se pudo crear el contexto EGL para renderizar la miniatura")
                return null
            }

            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, widthPx, EGL14.EGL_HEIGHT, heightPx, EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, config, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                AppLogger.e(TAG, "No se pudo crear la superficie pbuffer para renderizar la miniatura")
                return null
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                AppLogger.e(TAG, "eglMakeCurrent falló al renderizar la miniatura")
                return null
            }

            GLES20.glViewport(0, 0, widthPx, heightPx)
            drawer.ensureInitialized()
            drawer.clear()

            val resolver = context.contentResolver
            visibleLayers.sortedBy { it.zIndex }.forEach { layer ->
                val bitmap = decodeDownsampled(resolver, layer.sourceUri, widthPx, heightPx) ?: return@forEach
                val textureId = drawer.uploadTexture(bitmap)
                uploadedTextures.add(textureId)
                val w = bitmap.width
                val h = bitmap.height
                bitmap.recycle()

                val frame = layer.cameraTrack.frameAt(timeMs)
                drawer.drawLayer(
                    textureId = textureId,
                    imageWidthPx = w,
                    imageHeightPx = h,
                    frame = frame,
                    parallaxFactor = layer.parallaxFactor,
                    viewportWidth = widthPx,
                    viewportHeight = heightPx,
                    look = layer.lookSettings,
                    timeSeconds = timeMs / 1000f
                )
            }

            val buffer = ByteBuffer.allocateDirect(widthPx * heightPx * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, widthPx, heightPx, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            buffer.rewind()

            val raw = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)
            // glReadPixels entrega el buffer con el origen abajo-izquierda; hay que voltearlo
            // verticalmente para que la miniatura se vea con la orientación correcta.
            val flipped = Bitmap.createBitmap(raw, 0, 0, widthPx, heightPx, Matrix().apply { preScale(1f, -1f) }, true)
            if (flipped !== raw) raw.recycle()
            flipped
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error generando miniatura del proyecto", t)
            null
        } finally {
            uploadedTextures.forEach { drawer.deleteTexture(it) }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
        }
    }

    /** Decodifica solo a la resolución necesaria para la miniatura — rápido incluso con fotos de 12MP+. */
    private fun decodeDownsampled(resolver: ContentResolver, uri: Uri, targetW: Int, targetH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
            .onFailure {
                AppLogger.w(TAG, "No se pudo medir la imagen para la miniatura: $uri", it)
                return null
            }

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetW && bounds.outHeight / (sampleSize * 2) >= targetH) {
            sampleSize *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.onFailure { AppLogger.w(TAG, "No se pudo decodificar la imagen para la miniatura: $uri", it) }
            .getOrNull()
    }
}
