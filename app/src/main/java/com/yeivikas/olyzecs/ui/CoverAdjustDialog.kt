package com.yeivikas.olyzecs.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yeivikas.olyzecs.data.ImageDecoding
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 */
@Composable
fun CoverAdjustDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoding.decodeSampledFromUri(context.contentResolver, imageUri, maxDimension = 2048)
            }.getOrNull()
        }
        if (decoded != null) sourceBitmap = decoded else loadFailed = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            val bitmap = sourceBitmap
            when {
                loadFailed -> CoverAdjustError(onDismiss)
                bitmap == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                else -> CoverAdjustContent(bitmap = bitmap, onDismiss = onDismiss, onConfirm = onConfirm)
            }
        }
    }
}

@Composable
private fun CoverAdjustError(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No se pudo abrir esa imagen", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDismiss) { Text("Volver") }
    }
}

/**
 * Contenido principal del editor: gestos de pan/zoom sobre la imagen y
 * overlay oscuro con "ventana" clara del tamaño exacto de la portada. El
 * tamaño del área de edición se captura una sola vez con
 * [onGloballyPositioned] y se guarda en [containerSize] — a partir de ahí,
 * el recuadro de portada (frameW/frameH) y la escala aplicada
 * (appliedScale) son valores normales de esta función, visibles tanto para
 * el [Canvas] que dibuja como para el botón "Usar como portada" que
 * recorta — así lo que se guarda es EXACTAMENTE lo que el usuario vio
 * dentro del recuadro, ni un píxel distinto.
 */
@Composable
private fun CoverAdjustContent(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val bitmapW = bitmap.width.toFloat()
    val bitmapH = bitmap.height.toFloat()

    // userZoom >= 1: 1 = el zoom mínimo que ya cubre por completo el
    // recuadro de portada (equivalente a ContentScale.Crop); a partir de
    // ahí el usuario puede acercar más, nunca alejar por debajo de "cubre
    // completo" (si no, aparecerían bordes vacíos dentro de la portada).
    var userZoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isSaving by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val containerWPx = containerSize.width.toFloat()
    val containerHPx = containerSize.height.toFloat()
    val hasSize = containerWPx > 0f && containerHPx > 0f

    // Recuadro de portada: 82% del ancho disponible, salvo que eso lo haga
    // demasiado alto para la pantalla — ahí se limita por alto y se
    // recalcula el ancho a partir de la relación de aspecto, para que el
    // recuadro siempre entre entero.
    var frameW = containerWPx * 0.82f
    var frameH = frameW / PROJECT_CARD_ASPECT_RATIO
    val maxFrameH = containerHPx * 0.82f
    if (hasSize && frameH > maxFrameH) {
        frameH = maxFrameH
        frameW = frameH * PROJECT_CARD_ASPECT_RATIO
    }

    // Zoom mínimo que hace que la imagen cubra el recuadro entero (mismo
    // criterio que ContentScale.Crop, pero contra frameW/frameH en vez de
    // contra todo el canvas).
    val minCoverScale = if (hasSize) max(frameW / bitmapW, frameH / bitmapH) else 1f
    val appliedScale = minCoverScale * userZoom
    val dispW = bitmapW * appliedScale
    val dispH = bitmapH * appliedScale

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates -> containerSize = coordinates.size }
        ) {
            if (hasSize) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap, frameW, frameH) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newUserZoom = (userZoom * zoom).coerceIn(1f, 6f)
                                val newScale = minCoverScale * newUserZoom
                                val newDispW = bitmapW * newScale
                                val newDispH = bitmapH * newScale
                                val newMaxOffsetX = max(0f, (newDispW - frameW) / 2f)
                                val newMaxOffsetY = max(0f, (newDispH - frameH) / 2f)
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-newMaxOffsetX, newMaxOffsetX),
                                    (offset.y + pan.y).coerceIn(-newMaxOffsetY, newMaxOffsetY)
                                )
                                userZoom = newUserZoom
                            }
                        }
                ) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val imageLeft = centerX - dispW / 2f + offset.x
                    val imageTop = centerY - dispH / 2f + offset.y

                    // Imagen completa, en su brillo normal — esto es lo
                    // único que se termina viendo DENTRO del recuadro de
                    // portada.
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(imageLeft.toInt(), imageTop.toInt()),
                        dstSize = IntSize(dispW.toInt().coerceAtLeast(1), dispH.toInt().coerceAtLeast(1))
                    )

                    // Scrim oscuro sobre TODO el canvas, con un agujero
                    // (regla even-odd) exactamente del tamaño/posición del
                    // recuadro de portada — así se ve claro y nítido solo
                    // lo que realmente va a quedar en la portada, y
                    // oscuro/opaco todo lo demás de la imagen que no se va
                    // a ver.
                    val frameLeft = centerX - frameW / 2f
                    val frameTop = centerY - frameH / 2f
                    val scrimPath = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRect(Rect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH))
                    }
                    drawPath(scrimPath, color = Color.Black.copy(alpha = 0.72f))

                    // Borde claro delimitando el recuadro, para que quede
                    // perfectamente nítido dónde termina la portada.
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(frameLeft, frameTop),
                        size = Size(frameW, frameH),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                Text(
                    "Arrastrá para mover · pellizcá para acercar",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.weight(1f)
            ) { Text("Cancelar") }

            Button(
                onClick = {
                    if (isSaving || !hasSize) return@Button
                    isSaving = true
                    // Se recorta acá con los mismos valores vigentes en
                    // pantalla (appliedScale/offset/frameW/frameH,
                    // calculados arriba en esta misma función) — así se
                    // guarda EXACTAMENTE lo que el usuario está viendo
                    // dentro del recuadro claro en este instante.
                    val cropped = cropToOutputBitmap(
                        bitmap = bitmap,
                        appliedScale = appliedScale,
                        offsetX = offset.x,
                        offsetY = offset.y,
                        frameW = frameW,
                        frameH = frameH
                    )
                    isSaving = false
                    onConfirm(cropped)
                },
                enabled = !isSaving && hasSize,
                modifier = Modifier.weight(1f)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Usar como portada")
                }
            }
        }
    }
}

/**
 * Recorta [bitmap] al rectángulo que el recuadro de portada está mostrando
 * en pantalla en este instante (dado su [appliedScale] y desplazamiento
 * [offsetX]/[offsetY] respecto al centro de la imagen), y lo reescala a
 * [COVER_OUTPUT_WIDTH_PX] de ancho manteniendo [PROJECT_CARD_ASPECT_RATIO]
 * — resolución fija y liviana para portada, sin depender del tamaño
 * original de la foto elegida.
 */
private fun cropToOutputBitmap(
    bitmap: Bitmap,
    appliedScale: Float,
    offsetX: Float,
    offsetY: Float,
    frameW: Float,
    frameH: Float
): Bitmap {
    val dispW = bitmap.width * appliedScale
    val dispH = bitmap.height * appliedScale

    // Distancia entre el borde del recuadro y el borde de la imagen
    // desplegada, convertida de vuelta a píxeles reales del bitmap
    // original (dividiendo por la escala aplicada en pantalla).
    val srcX = (((dispW - frameW) / 2f) - offsetX) / appliedScale
    val srcY = (((dispH - frameH) / 2f) - offsetY) / appliedScale
    val srcW = frameW / appliedScale
    val srcH = frameH / appliedScale

    val safeX = srcX.coerceIn(0f, (bitmap.width - srcW).coerceAtLeast(0f))
    val safeY = srcY.coerceIn(0f, (bitmap.height - srcH).coerceAtLeast(0f))
    val safeW = srcW.coerceAtMost(bitmap.width - safeX).coerceAtLeast(1f)
    val safeH = srcH.coerceAtMost(bitmap.height - safeY).coerceAtLeast(1f)

    val cropped = Bitmap.createBitmap(
        bitmap,
        safeX.toInt(),
        safeY.toInt(),
        safeW.toInt().coerceAtLeast(1),
        safeH.toInt().coerceAtLeast(1)
    )

    val outW = COVER_OUTPUT_WIDTH_PX
    val outH = (outW / PROJECT_CARD_ASPECT_RATIO).toInt()
    val scaled = Bitmap.createScaledBitmap(cropped, outW, outH, true)
    if (scaled !== cropped) cropped.recycle()
    return scaled
}
