package com.yeivikas.olyzecs.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
import java.io.File

/**
 * Decodificación de imágenes con reducción de tamaño (downsampling).
 *
 * Una foto de cámara moderna puede pesar 4000x6000px o más — decodificarla
 * a resolución completa para una capa que como mucho se exporta a 1080x1920
 * es un desperdicio de memoria real, y con varias capas en el mismo
 * proyecto (o al reabrir un proyecto guardado, donde TODAS las capas se
 * decodifican de una) es una causa directa de `OutOfMemoryError` en
 * celulares con poca RAM.
 *
 * [MAX_LAYER_DIMENSION_PX] deja bastante margen por encima de la
 * resolución de exportación por defecto (1080x1920) para permitir zoom
 * sin que se note pixelado, sin cargar el original completo a memoria.
 */
object ImageDecoding {

    private const val TAG = "ImageDecoding"

    const val MAX_LAYER_DIMENSION_PX = 2048

    /** Decodifica un archivo local ya reducido, calculando el inSampleSize correcto de antemano. */
    fun decodeSampledFromFile(file: File, maxDimension: Int = MAX_LAYER_DIMENSION_PX): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            AppLogger.w(TAG, "No se pudo leer dimensiones válidas del archivo: ${file.name}")
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
                ?: run { AppLogger.w(TAG, "decodeFile devolvió null para: ${file.name}"); null }
        }
    } catch (t: Throwable) {
        AppLogger.e(TAG, "Error decodificando imagen desde archivo: ${file.name}", t)
        null
    }

    /**
     * Decodifica un Uri (típicamente de un picker del sistema) ya reducido.
     * Requiere abrir el stream dos veces (uno para medir, otro para
     * decodificar), ya que los streams de ContentResolver normalmente no
     * se pueden rebobinar.
     */
    fun decodeSampledFromUri(
        resolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = MAX_LAYER_DIMENSION_PX
    ): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // OJO: decodeStream() con inJustDecodeBounds=true SIEMPRE devuelve null
        // (así funciona el modo "solo medir": llena bounds.outWidth/outHeight
        // como efecto secundario, no como valor de retorno). Por eso acá NO se
        // encadena "?: return null" sobre ese resultado — eso cortaría la
        // función en el 100% de los casos, incluso con una imagen perfecta.
        // Lo único que puede fallar de verdad es no conseguir el stream.
        val firstStream = resolver.openInputStream(uri)
        if (firstStream == null) {
            AppLogger.w(TAG, "No se pudo abrir el stream de entrada para: $uri")
            null
        } else {
            firstStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                AppLogger.w(TAG, "No se pudo leer dimensiones válidas del uri: $uri")
                null
            } else {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: run { AppLogger.w(TAG, "No se pudo reabrir el stream para decodificar: $uri"); null }
            }
        }
    } catch (t: Throwable) {
        AppLogger.e(TAG, "Error decodificando imagen desde uri: $uri", t)
        null
    }

    private fun computeSampleSize(rawWidth: Int, rawHeight: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (rawWidth / sampleSize > maxDimension || rawHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
