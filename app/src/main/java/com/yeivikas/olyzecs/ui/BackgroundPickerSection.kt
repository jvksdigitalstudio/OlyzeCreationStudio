package com.yeivikas.olyzecs.ui

import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yeivikas.olyzecs.R

/**
 * Verde chroma key (Rosco Digital Green / estándar de la industria para
 * fondos de croma) — PEDIDO EXPLÍCITO del usuario: el fondo por defecto
 * de todo proyecto nuevo, sin que el usuario tenga que tocar "Elige un
 * fondo" para nada. Vive acá (no en [ProfessionalColorPickerDialog], que
 * solo lo referencia) porque este archivo es el dueño conceptual del
 * fondo del proyecto — igual criterio que `PROJECT_CARD_ASPECT_RATIO` en
 * ProjectsScreen.kt, una constante de UN módulo que otro archivo necesita
 * leer.
 */
internal const val CHROMA_KEY_GREEN_ARGB: Int = 0xFF00B140.toInt()

/**
 * Qué eligió el usuario en "Elige un fondo" — vive solo en memoria
 * mientras el diálogo "Nuevo proyecto" está abierto (ver
 * CreateProjectDialog en ProjectsScreen.kt). Nunca se persiste tal cual:
 * al tocar "Crear", SIEMPRE se resuelve a un [Uri] concreto (color sólido
 * generado como bitmap, o el Uri de la imagen/foto elegida) antes de
 * seguir — ver `resolveBackgroundUri` en CreateProjectDialog. Un color
 * sólido no es un caso especial para el resto del proyecto: es una
 * imagen más, indistinguible de cualquier otra en cuanto
 * [EditorViewModel.importAsBackground] la recibe.
 */
internal sealed class BackgroundChoice {
    data class SolidColor(val argb: Int) : BackgroundChoice()
    data class Media(val uri: Uri) : BackgroundChoice()
}

/**
 * Sección "Elige un fondo" del diálogo "Nuevo proyecto" (ver ADR-005 y el
 * pedido del usuario de agregarla entre el nombre y "Formato del
 * lienzo"). Reutiliza el mismo lenguaje visual que ya usa el resto del
 * diálogo (tarjetas redondeadas, `MaterialTheme.colorScheme`) — la
 * pastilla flotante de 3 acciones (Color/Imagen/Cámara) reproduce la
 * referencia que mandó el usuario, sin el 4to ícono ("guardado") que no
 * pidió.
 *
 * El resultado siempre termina como la PRIMERA capa real del proyecto
 * (editable en el timeline, con parallax de fondo) — ver
 * [EditorViewModel.importAsBackground], reutilizado tal cual, nunca
 * reimplementado acá. Este composable es puramente de presentación: no
 * decide qué pasa con la elección, solo la muestra y avisa cuál botón se
 * tocó.
 *
 * Auditoría de septiembre 2026 (ADR-006, bug de fondo verde chroma-key
 * asomando): para Imagen y Cámara, el `Uri` que reciben [onPickImageClick]/
 * [onTakePhotoClick] NO llega directo a `importAsBackground` — antes de
 * eso, `CreateProjectDialog` (en `ProjectsScreen.kt`) lo intercepta y
 * abre `BackgroundAdjustDialog`, que obliga a encuadrar la imagen contra
 * la proporción exacta del lienzo elegido en "Formato del lienzo". Solo
 * el resultado YA ajustado (que cubre el 100% del lienzo, sin excepción)
 * se asigna como [BackgroundChoice.Media] y sigue camino hacia
 * `importAsBackground`. Color sigue sin pasar por ningún ajuste: un
 * bitmap del tamaño exacto del canvas ya cubre el lienzo por
 * construcción (ver `resolveBackgroundUri` en `CreateProjectDialog`).
 */
@Composable
internal fun BackgroundPickerSection(
    choice: BackgroundChoice,
    onPickColorClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Mismo criterio que `android.hardware.opengles.aep` en el manifest:
    // el permiso/feature de cámara es opcional a nivel de sistema — en un
    // equipo sin cámara (tablet de escritorio, algunos emuladores) el
    // botón de cámara directamente no se ofrece, en vez de mostrarse y
    // fallar al tocarlo.
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    Column(modifier = modifier) {
        Text("Elige un fondo", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            when (choice) {
                is BackgroundChoice.SolidColor -> Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(choice.argb))
                )
                is BackgroundChoice.Media -> AsyncImage(
                    model = choice.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                BackgroundPillIcon(
                    drawableRes = R.drawable.ic_layer_color,
                    contentDescription = "Elegir color de fondo",
                    onClick = onPickColorClick,
                    tinted = false
                )
                BackgroundPillIcon(
                    drawableRes = R.drawable.ic_image_placeholder,
                    contentDescription = "Elegir imagen de fondo",
                    onClick = onPickImageClick,
                    tinted = true
                )
                if (hasCamera) {
                    BackgroundPillIcon(
                        drawableRes = R.drawable.ic_camera,
                        contentDescription = "Tomar foto de fondo",
                        onClick = onTakePhotoClick,
                        tinted = true
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundPillIcon(
    drawableRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    // `ic_layer_color` trae sus propios colores fijos (paleta de pintor
    // multicolor) — igual que en RowActionIcon, con tint plano ese
    // detalle desaparece, así que este ícono puntual va con
    // `Color.Unspecified`. Los otros dos son glifos de un solo trazo,
    // pensados para tintarse de blanco como el resto de la pastilla.
    tinted: Boolean
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (tinted) {
            Icon(
                painter = painterResource(id = drawableRes),
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
