package com.yeivikas.olyzecs.engine.render

import android.graphics.Bitmap
import android.opengl.GLES20
import com.yeivikas.olyzecs.debug.AppLogger
import kotlin.math.min

private const val TAG = "GpuTextureLimits"

/**
 * Único lugar de la app donde una imagen puede terminar más chica que su
 * archivo original — y no por elección de calidad, sino porque
 * `GL_MAX_TEXTURE_SIZE` es un techo físico del hardware: ninguna GPU
 * (ni Photoshop, ni Premiere, ni ninguna app "profesional") puede subir
 * una textura más grande que lo que su driver reporta acá, sin importar
 * el software. Por eso esto vive separado de [com.yeivikas.olyzecs.data.ImageDecoding],
 * que decodifica siempre a resolución completa: el bitmap "fuente de
 * verdad" (el que usan Efectos/Recolor/3D para su propio procesamiento en
 * CPU, y el archivo original en disco) nunca pasa por acá — esto se
 * aplica recién en el último paso, justo antes de `glTexImage2D`, y solo
 * si hace falta. NOTA (corregida en esta auditoría): el comentario
 * original de este archivo decía que "el export no se ve afectado" por
 * este recorte — eso dejó de ser cierto desde que [com.yeivikas.olyzecs.engine.export.VideoExporter]
 * empezó a llamar [clampForTexture] también para las texturas que sube al
 * codificador de video. El archivo fuente en disco sigue intacto siempre;
 * lo que SÍ puede verse recortado, en ambos casos, es la textura que
 * efectivamente se sube a la GPU de ESE dispositivo puntual — un límite
 * de hardware real, no una decisión de calidad de la app.
 *
 * Debe llamarse con un contexto EGL ya activo (mismo hilo GL que hace
 * el resto del renderizado) — es la única forma de preguntarle al driver
 * cuál es su límite real en este dispositivo.
 */
object GpuTextureLimits {

    @Volatile
    private var cachedMaxSize: Int? = null

    /** Tamaño máximo de textura 2D que soporta la GPU actual, según el driver. */
    fun queryMaxTextureSize(): Int {
        cachedMaxSize?.let { return it }
        val out = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, out, 0)
        // 0 significaría que la consulta falló (sin contexto GL activo, por
        // ejemplo); 4096 es el piso garantizado por la spec de OpenGL ES 2.0
        // en cualquier dispositivo real, así que es un fallback seguro, no
        // un recorte de calidad arbitrario.
        val size = if (out[0] > 0) out[0] else 4096
        cachedMaxSize = size
        return size
    }

    /**
     * Devuelve [bitmap] tal cual si ya entra en el límite real de la GPU.
     * Solo si lo excede, lo reescala (una única vez, con filtrado bilineal)
     * al máximo que el hardware puede aceptar — la alternativa sería que
     * `glTexImage2D` fallara en silencio y la capa ni siquiera se viera.
     * Recicla el bitmap original si tuvo que crear uno nuevo.
     */
    fun clampForTexture(bitmap: Bitmap): Bitmap {
        val maxSize = queryMaxTextureSize()
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxSize) return bitmap

        val scale = maxSize.toFloat() / largestSide.toFloat()
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        AppLogger.w(
            TAG,
            "Imagen de ${bitmap.width}x${bitmap.height} excede el límite de textura de esta GPU " +
                "($maxSize) — se reescala solo para SUBIR A GPU (vista en vivo o export) a ${newWidth}x$newHeight. " +
                "El archivo original en disco nunca se toca ni se sobrescribe por esto — solo la textura que " +
                "efectivamente ve la pantalla o graba el codificador de video en este dispositivo puntual."
        )
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** Para tests/diagnóstico: fuerza a re-consultar el límite en la próxima llamada. */
    fun invalidateCache() {
        cachedMaxSize = null
    }
}
