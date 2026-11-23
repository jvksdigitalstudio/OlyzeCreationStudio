package com.yeivikas.olyzecs.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable

/** Ancho de salida (px) del jpg de portada que se guarda — el alto se deriva de [PROJECT_CARD_ASPECT_RATIO]. */
private const val COVER_OUTPUT_WIDTH_PX = 1000

/**
 * Editor de encuadre de portada: en vez de recortar "a ciegas" el centro de
 * la imagen elegida y cargarla directo, esta pantalla muestra la imagen
 * COMPLETA con la zona que va a quedar fuera de la portada oscurecida, y un
 * recuadro claro en el centro con la relación de aspecto exacta de la
 * tarjeta ([PROJECT_CARD_ASPECT_RATIO]) — el usuario arrastra para mover y
 * pellizca para hacer zoom hasta centrar lo que quiere que se vea, y recién
 * ahí confirma. Nada se guarda hasta tocar "Usar como portada".
 *
 * A partir de ADR-006, este composable es un wrapper delgado sobre
 * [ImageFitDialog]: toda la lógica de gestos/overlay/recorte que antes
 * vivía acá (`CoverAdjustContent`/`cropToOutputBitmap`) se extrajo a un
 * componente genérico, parametrizable por relación de aspecto y
 * resolución de salida, para poder reutilizarla también en
 * [BackgroundAdjustDialog] sin duplicar código. Este wrapper solo fija
 * los cuatro valores propios de portada (proporción 9:14, salida fija de
 * 1000px de ancho, texto de botón "Usar como portada") — la firma
 * pública (`imageUri`, `onDismiss`, `onConfirm`) no cambió, así que
 * ningún llamador existente (`ProjectsScreen.kt`, `ProjectStorage.kt`)
 * necesita tocar una sola línea.
 */
@Composable
fun CoverAdjustDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    ImageFitDialog(
        imageUri = imageUri,
        targetAspectRatio = PROJECT_CARD_ASPECT_RATIO,
        outputWidthPx = COVER_OUTPUT_WIDTH_PX,
        outputHeightPx = (COVER_OUTPUT_WIDTH_PX / PROJECT_CARD_ASPECT_RATIO).toInt(),
        confirmButtonText = "Usar como portada",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
