package com.yeivikas.olyzecs.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
import java.io.File

/**
 * Decodificación de imágenes.
 *
 * Por decisión explícita: la app trabaja SIEMPRE a la resolución máxima/
 * original del archivo importado — igual que cualquier herramienta
 * profesional (Photoshop/Affinity/Premiere), donde el archivo fuente
 * nunca se recorta "por las dudas". [NO_LIMIT] es el default en ambas
 * funciones de acá, así que decodificar una capa (importar, reemplazar,
 * o reabrir un proyecto guardado) nunca reduce calidad por sí solo.
 *
 * La única reducción real que puede seguir existiendo en la app es un
 * techo de hardware, no una elección de software: el tamaño máximo de
 * textura que la GPU del celular puede aceptar (`GL_MAX_TEXTURE_SIZE`).
 * Eso NO se resuelve acá — [GpuTextureLimits] lo aplica justo antes de
 * subir la textura a GL (ver `GLRenderer`/`VideoExporter`), nunca en la
 * decodificación general, para que el bitmap "fuente de verdad" en
 * memoria (el que ven Efectos, Recolor, 3D y el export) sea siempre el
 * original completo.
 */
object ImageDecoding {

    private const val TAG = "ImageDecoding"

    /** Sin límite: decodifica siempre a la resolución real del archivo. */
    const val NO_LIMIT = Int.MAX_VALUE

    /** Decodifica un archivo local a resolución completa (salvo que se pida lo contrario). */
    fun decodeSampledFromFile(file: File, maxDimension: Int = NO_LIMIT): Bitmap? = try {
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
        maxDimension: Int = NO_LIMIT
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
