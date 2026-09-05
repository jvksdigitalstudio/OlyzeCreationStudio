package com.yeivikas.olyzecs.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.scene.Layer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Responsable de convertir URIs (elegidas por el usuario vía Storage
 * Access Framework) en objetos [Layer] con su bitmap decodificado y listo
 * para subir a GPU, o en bitmaps sueltos para reemplazar la imagen de una
 * capa ya existente sin perder sus keyframes.
 */
class LayerRepository(private val context: Context) {

    private val TAG = "LayerRepository"

    data class DecodedImage(val bitmap: Bitmap, val displayName: String)

    suspend fun importAsLayers(
        uris: List<Uri>,
        startingZIndex: Int,
        // Separado de startingZIndex a propósito: el fondo (importAsBackground)
        // usa un zIndex NEGATIVO/decreciente para quedar detrás de todo, pero
        // el color debe seguir el orden de CREACIÓN de capas (0,1,2...), no
        // el zIndex, para que la paleta se recorra de forma prolija y sin
        // repetirse enseguida. El llamador pasa el total de capas existentes
        // en ese momento (ver EditorViewModel).
        startingColorIndex: Int
    ): List<Layer> =
        withContext(Dispatchers.IO) {
            uris.mapIndexedNotNull { index, uri ->
                val decoded = decode(uri) ?: return@mapIndexedNotNull null
                val dominant = ColorExtraction.dominantColor(decoded.bitmap)
                Layer(
                    sourceUri = uri,
                    name = decoded.displayName,
                    zIndex = startingZIndex + index,
                    colorIndex = startingColorIndex + index,
                    // La barra de la capa arranca pintada con el color
                    // dominante/más vivo de la propia imagen (en vez del
                    // color cíclico automático de la paleta) — así cada
                    // capa se distingue de un vistazo por lo que realmente
                    // contiene. colorIndex se sigue guardando igual, por si
                    // el usuario alguna vez restablece el color a
                    // "automático" desde el diálogo de color de la capa.
                    customColorArgb = dominant,
                    // Se guarda ADEMÁS como default permanente de fábrica
                    // (ver Layer.importedDefaultColorArgb) — este valor no
                    // se vuelve a tocar aunque el usuario cambie el color
                    // más adelante, para que "Restablecer" siempre pueda
                    // volver acá.
                    importedDefaultColorArgb = dominant,
                    widthPx = decoded.bitmap.width,
                    heightPx = decoded.bitmap.height
                ).apply {
                    pendingBitmap = decoded.bitmap
                }
            }
        }

    /** Decodifica una sola imagen; usado tanto para "importar fondo" como para "reemplazar imagen". */
    suspend fun decode(uri: Uri): DecodedImage? = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Algunos proveedores no soportan permisos persistentes; no es fatal.
            AppLogger.i(TAG, "El proveedor del uri no soporta permiso persistente (no es grave): ${e.message}")
        }

        // Resolución completa del original — sin límite artificial. Ver
        // el comentario de cabecera de [ImageDecoding]: cualquier recorte
        // de tamaño real (techo de hardware de la GPU) se aplica más
        // adelante y por separado, justo antes de subir a GL, nunca acá.
        val bitmap = ImageDecoding.decodeSampledFromUri(resolver, uri, maxDimension = ImageDecoding.NO_LIMIT)
        if (bitmap == null) {
            AppLogger.w(TAG, "No se pudo decodificar la imagen elegida: $uri")
            return@withContext null
        }

        val displayName = queryDisplayName(resolver, uri) ?: "imagen_${System.currentTimeMillis()}"
        DecodedImage(bitmap, displayName)
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    /**
     * Escribe [bitmap] como PNG dentro del almacenamiento privado de la
     * app (`filesDir/edited_images/`) y devuelve un [Uri] `file://` que
     * apunta ahí. Usado por el recoloreo del modo edición dedicado (ver
     * EditorViewModel.commitLayerRecolor): en vez de inventar un nuevo
     * tipo de dato "imagen con remaps de color" que ProjectStorage, el
     * exportador y el generador de miniaturas tendrían que aprender a
     * entender por separado, esto genera un archivo de imagen real y lo
     * asigna como `sourceUri` de la capa — para el resto de la app es
     * indistinguible de cualquier otra imagen importada, así que todo lo
     * que ya funciona (guardar, reabrir, exportar, miniaturas) sigue
     * funcionando sin tocar una línea más.
     */
    fun saveBitmapAsLocalUri(bitmap: Bitmap, prefix: String): Uri? {
        return try {
            val dir = java.io.File(context.filesDir, "edited_images").apply { mkdirs() }
            val file = java.io.File(dir, "${prefix}_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(file)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error guardando bitmap recoloreado a disco", t)
            null
        }
    }
}
