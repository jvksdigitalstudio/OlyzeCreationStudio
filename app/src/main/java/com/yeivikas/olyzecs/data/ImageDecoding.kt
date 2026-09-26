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
 * o reabrir un proyecto guardado) nunca reduce calidad por sí solo EN
 * CONDICIONES NORMALES DE MEMORIA.
 *
 * La única reducción real que puede seguir existiendo en la app es un
 * techo de hardware, no una elección de software: el tamaño máximo de
 * textura que la GPU del celular puede aceptar (`GL_MAX_TEXTURE_SIZE`).
 * Eso NO se resuelve acá — [GpuTextureLimits] lo aplica justo antes de
 * subir la textura a GL (ver `GLRenderer`/`VideoExporter`), nunca en la
 * decodificación general, para que el bitmap "fuente de verdad" en
 * memoria (el que ven Efectos, Recolor, 3D y el export) sea siempre el
 * original completo.
 *
 * BUG REAL corregido acá — AUDITORÍA "fondo verde intermitente al
 * reabrir un proyecto": ambas funciones envolvían la decodificación en
 * `catch (t: Throwable)`, lo cual en Kotlin/JVM también atrapa
 * `OutOfMemoryError` (es un `Error`, y `Error` hereda de `Throwable`).
 * Combinado con que `ProjectStorage.loadProject()` decodifica TODAS las
 * capas de un proyecto EN PARALELO y a resolución completa (varias
 * decodificaciones de varios megapíxeles corriendo a la vez, ver ese
 * archivo), el pico de memoria en el instante de reabrir un proyecto es
 * la SUMA de todas las capas, no una por una. Si en ese instante el
 * dispositivo tenía poca RAM libre (otra app en segundo plano, heap
 * fragmentado, etc.), una decodificación —incluida la del fondo— podía
 * lanzar `OutOfMemoryError`, quedar atrapada en silencio por ese
 * `catch (t: Throwable)`, y esa capa simplemente no se dibujaba nunca:
 * sin ninguna capa cubriendo el lienzo, se veía el verde chroma-key por
 * defecto (`CHROMA_KEY_GREEN_ARGB`, ver `LayerDrawer.ensureInitialized`).
 * Nada de esto dejaba rastro visible para el usuario — solo un log
 * interno. Por eso era intermitente: dependía pura y exclusivamente del
 * estado de memoria del teléfono en ese instante exacto, no del
 * proyecto ni de cómo se guardó.
 *
 * La corrección real, no cosmética: un `OutOfMemoryError` durante la
 * decodificación ya NO se trata como "esta imagen está rota" (que es lo
 * que significa cualquier otra excepción acá) sino como "no hay memoria
 * AHORA para decodificarla a resolución completa" — se reintenta varias
 * veces reduciendo la resolución a la mitad en cada intento
 * ([MAX_OOM_RETRIES] veces), liberando explícitamente cualquier bitmap
 * parcial antes de reintentar. Con esto, el peor caso posible pasa de
 * "la capa desaparece sin avisar" a "la capa se ve con menos resolución
 * de la ideal, pero se ve" — degradación elegante en vez de una capa
 * fantasma. Solo si ni siquiera el intento más chico entra en memoria
 * se devuelve `null` (con un log explícito, distinto del de "archivo
 * corrupto", para que quede claro en el Registro de errores cuál de los
 * dos pasó).
 */
object ImageDecoding {

    private const val TAG = "ImageDecoding"

    /** Sin límite: decodifica siempre a la resolución real del archivo. */
    const val NO_LIMIT = Int.MAX_VALUE

    /**
     * Cuántas veces se reintenta, a mitad de resolución cada vez, ante un
     * OutOfMemoryError real durante la decodificación. 4 intentos cubre
     * hasta 1/16 de la resolución original (inSampleSize 1→2→4→8→16) antes
     * de rendirse — de sobra para superar un pico de memoria transitorio
     * sin terminar entregando una imagen irreconociblemente chica.
     */
    private const val MAX_OOM_RETRIES = 4

    /** Decodifica un archivo local a resolución completa (salvo que se pida lo contrario). */
    fun decodeSampledFromFile(file: File, maxDimension: Int = NO_LIMIT): Bitmap? {
        val bounds = try {
            BitmapFactory.Options().apply { inJustDecodeBounds = true }
                .also { BitmapFactory.decodeFile(file.absolutePath, it) }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error leyendo dimensiones del archivo: ${file.name}", t)
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            AppLogger.w(TAG, "No se pudo leer dimensiones válidas del archivo: ${file.name}")
            return null
        }

        val baseSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        repeat(MAX_OOM_RETRIES + 1) { attempt ->
            val sampleSize = baseSampleSize shl attempt // *1, *2, *4, *8, *16
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                if (decoded == null) {
                    AppLogger.w(TAG, "decodeFile devolvió null para: ${file.name}")
                    return null
                }
                if (attempt > 0) {
                    AppLogger.w(
                        TAG,
                        "Memoria insuficiente para decodificar '${file.name}' a resolución completa — " +
                            "se decodificó a 1/${sampleSize / baseSampleSize} de esa resolución tras $attempt " +
                            "reintento(s) por OutOfMemoryError. La capa se ve, pero con menos detalle del ideal " +
                            "hasta que el dispositivo libere memoria y el proyecto se vuelva a abrir."
                    )
                }
                return decoded
            } catch (oom: OutOfMemoryError) {
                System.gc()
                if (attempt == MAX_OOM_RETRIES) {
                    AppLogger.e(
                        TAG,
                        "Sin memoria para decodificar '${file.name}' incluso a 1/${sampleSize * 2 / baseSampleSize} " +
                            "de la resolución original tras $MAX_OOM_RETRIES reintentos — se descarta esta imagen",
                        oom
                    )
                    return null
                }
                // Reintenta con el doble de inSampleSize (mitad de resolución) en la próxima vuelta.
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Error decodificando imagen desde archivo: ${file.name}", t)
                return null
            }
        }
        return null
    }

    /**
     * Decodifica un Uri (típicamente de un picker del sistema) ya reducido.
     * Requiere abrir el stream dos veces (uno para medir, otro para
     * decodificar), ya que los streams de ContentResolver normalmente no
     * se pueden rebobinar. Mismo protocolo de reintento ante
     * OutOfMemoryError que [decodeSampledFromFile] — ver el KDoc de clase
     * para el detalle completo de la causa raíz que esto corrige.
     */
    fun decodeSampledFromUri(
        resolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = NO_LIMIT
    ): Bitmap? {
        val bounds = try {
            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { options ->
                val firstStream = resolver.openInputStream(uri)
                    ?: run { AppLogger.w(TAG, "No se pudo abrir el stream de entrada para: $uri"); return null }
                // decodeStream() con inJustDecodeBounds=true SIEMPRE devuelve null (así funciona el
                // modo "solo medir": llena bounds.outWidth/outHeight como efecto secundario, no como
                // valor de retorno) — por eso acá no se valida el resultado, solo bounds después.
                firstStream.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error leyendo dimensiones del uri: $uri", t)
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            AppLogger.w(TAG, "No se pudo leer dimensiones válidas del uri: $uri")
            return null
        }

        val baseSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        repeat(MAX_OOM_RETRIES + 1) { attempt ->
            val sampleSize = baseSampleSize shl attempt
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                if (decoded == null) {
                    AppLogger.w(TAG, "No se pudo reabrir el stream para decodificar: $uri")
                    return null
                }
                if (attempt > 0) {
                    AppLogger.w(
                        TAG,
                        "Memoria insuficiente para decodificar '$uri' a resolución completa — " +
                            "se decodificó a 1/${sampleSize / baseSampleSize} de esa resolución tras $attempt " +
                            "reintento(s) por OutOfMemoryError."
                    )
                }
                return decoded
            } catch (oom: OutOfMemoryError) {
                System.gc()
                if (attempt == MAX_OOM_RETRIES) {
                    AppLogger.e(
                        TAG,
                        "Sin memoria para decodificar '$uri' incluso a 1/${sampleSize * 2 / baseSampleSize} " +
                            "de la resolución original tras $MAX_OOM_RETRIES reintentos — se descarta esta imagen",
                        oom
                    )
                    return null
                }
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Error decodificando imagen desde uri: $uri", t)
                return null
            }
        }
        return null
    }

    private fun computeSampleSize(rawWidth: Int, rawHeight: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (rawWidth / sampleSize > maxDimension || rawHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
