package com.yeivikas.olyzecs.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import com.yeivikas.olyzecs.engine.scene.CanvasSpec

/**
 * Editor de encuadre para "Elige un fondo" → Imagen/Cámara, en el diálogo
 * "Nuevo proyecto" (ver ADR-006 y la auditoría del bug de fondo verde
 * chroma-key asomando). Antes de este diálogo, una imagen elegida se
 * importaba a su tamaño natural sin relación con el lienzo del proyecto
 * (ver [CanvasSpec]), así que cualquier imagen que no cubriera el 100%
 * del lienzo dejaba ver el chroma-key verde de zona vacía alrededor. Este
 * diálogo obliga a encuadrar la imagen contra la proporción exacta del
 * lienzo elegido ANTES de confirmarla como fondo, generando una salida
 * que tapa el lienzo completo sin excepción.
 *
 * Wrapper delgado sobre [ImageFitDialog] — misma lógica de gestos/overlay/
 * recorte que ya usa [CoverAdjustDialog] para portada, aquí parametrizada
 * con la proporción y resolución REALES de [canvas] en vez de valores
 * fijos: a diferencia de portada (siempre 9:14, salida fija de 1000px),
 * el fondo puede necesitar cualquier proporción y hasta la resolución
 * completa del lienzo (ej. 1920×1342 en un formato IMAX), porque tiene
 * que cubrirlo al 100% sin reescalarse después.
 *
 * Aplica igual a Imagen y a Cámara: ambos caminos convergen en un mismo
 * [Uri] antes de llegar acá (ver `CreateProjectDialog` en
 * `ProjectsScreen.kt`), así que no hace falta lógica separada según el
 * origen de la imagen.
 */
@Composable
fun BackgroundAdjustDialog(
    imageUri: Uri,
    canvas: CanvasSpec,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    ImageFitDialog(
        imageUri = imageUri,
        targetAspectRatio = canvas.widthPx.toFloat() / canvas.heightPx.toFloat(),
        outputWidthPx = canvas.widthPx,
        outputHeightPx = canvas.heightPx,
        confirmButtonText = "Usar como fondo",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
