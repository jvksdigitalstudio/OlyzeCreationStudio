package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.data.TrashedLayerInfo
import com.yeivikas.olyzecs.ui.theme.BrandBlueLight
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated

/**
 * Papelera de capas — decisión de producto (ver conversación de diseño):
 * eliminar una capa nunca destruye nada al instante. `EditorViewModel.
 * removeLayer` solo la saca del lienzo; el próximo autoguardado la mueve a
 * `ProjectData.trashedLayers` (ver el comentario grande en
 * `ProjectStorage.saveProject`), donde queda esperando — con su imagen,
 * keyframes y look intactos — hasta que el usuario decide, a propósito,
 * una de tres cosas: restaurarla, eliminarla para siempre, o vaciar la
 * papelera entera. SIN límite de retención automático: se acumula hasta
 * que el usuario mismo actúa, a cambio de que esta pantalla siempre
 * muestre cuánto pesa, para que esa decisión sea informada.
 *
 * Este archivo es autocontenido a propósito (ni un import de
 * `EditorViewModel`): recibe datos + callbacks, igual que
 * `ProjectInfoPanel`/`ErrorLogScreen` — el acoplamiento con el ViewModel
 * vive únicamente en el call site (EditorScreen.kt).
 */

// ============================================================================
// Aviso proactivo (banner)
// ============================================================================

/**
 * Franja NO bloqueante que avisa, una sola vez por episodio (ver
 * `EditorUiState.showTrashRecoveryBanner`), que el proyecto quedó con una
 * sola capa viva y hay algo en la papelera para revisar. A diferencia de
 * un `AlertDialog`, el usuario puede simplemente seguir editando sin
 * tocarla — nunca bloquea el lienzo.
 */
@Composable
fun TrashRecoveryBanner(
    itemCount: Int,
    onView: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(SurfaceTintedElevated, SurfaceTintedDark))
            )
            .clickable(onClick = onView)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_delete),
            contentDescription = null,
            tint = BrandPurpleLight,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (itemCount == 1) {
                "Tenés 1 capa eliminada recientemente · Ver papelera"
            } else {
                "Tenés $itemCount capas eliminadas recientemente · Ver papelera"
            },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Descartar aviso",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ============================================================================
// Pantalla completa de la papelera
// ============================================================================

/**
 * Pantalla completa de la papelera: selección múltiple con "Seleccionar
 * todo", más las dos acciones acordadas — "Restaurar seleccionadas"/
 * "Eliminar seleccionadas" (dependen de la selección) y "Vaciar papelera"
 * (acción de un solo toque, independiente de qué esté tildado).
 *
 * Ambas confirmaciones destructivas ([EmptyTrashConfirmDialog] y
 * [DeleteSelectedFromTrashConfirmDialog]) se disparan desde ACÁ, no en el
 * caller — así ningún futuro call site puede llamar a `onEmptyTrash`/
 * `onDeleteSelected` sin pasar antes por su propio aviso.
 */
@Composable
fun LayerTrashDialog(
    items: List<TrashedLayerInfo>,
    totalSizeBytes: Long,
    onRestoreSelected: (Set<String>) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Un id que ya no está en la papelera (se restauró/eliminó desde otra
    // instancia de este mismo diálogo, o esta lista se refrescó por otro
    // motivo) nunca debe seguir "marcado" — evita que "Restaurar
    // seleccionadas" intente operar sobre algo que ya no existe.
    LaunchedEffect(items) {
        val validIds = items.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in validIds }.toSet()
    }

    val allSelected = items.isNotEmpty() && selectedIds.size == items.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BrandPurpleDeep) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- Encabezado ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(SurfaceTintedElevated, SurfaceTintedDark)))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(BrandPurpleLight, BrandBlueLight))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Papelera de capas", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            Text(
                                if (items.isEmpty()) "Vacía"
                                else "${items.size} ${if (items.size == 1) "capa" else "capas"} · ${formatFileSize(totalSizeBytes)}",
                                fontSize = 12.sp,
                                color = Color(0xFFBFB3E0)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "Cerrar", tint = Color.White)
                        }
                    }
                    if (items.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    selectedIds = if (allSelected) emptySet() else items.map { it.id }.toSet()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (allSelected) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
                                    ),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Seleccionar todo", color = Color(0xFFD6CFEF), fontSize = 13.sp)
                            }
                            // Acción independiente de la selección — a
                            // propósito bien separada visualmente (color de
                            // peligro, sin depender de tildar nada) para que
                            // nunca se confunda con "Eliminar seleccionadas".
                            TextButton(onClick = { showEmptyConfirm = true }) {
                                Text("Vaciar papelera", color = Color(0xFFE05C5C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // --- Lista ---
                if (items.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                tint = Color(0xFF8A7DB8),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("La papelera está vacía", color = Color(0xFFD6CFEF), fontWeight = FontWeight.SemiBold)
                            Text(
                                "Las capas que elimines del lienzo van a aparecer acá, listas para recuperar.",
                                color = Color(0xFF8A7DB8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            TrashLayerRow(
                                item = item,
                                selected = item.id in selectedIds,
                                onToggle = {
                                    selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                                }
                            )
                        }
                    }

                    // --- Barra inferior: acciones sobre la selección ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceTintedElevated)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE05C5C))
                        ) {
                            Text(
                                if (selectedIds.isEmpty()) "Eliminar seleccionadas" else "Eliminar (${selectedIds.size})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                onRestoreSelected(selectedIds)
                                selectedIds = emptySet()
                            },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurpleLight, contentColor = Color.White)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_restore_all),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (selectedIds.isEmpty()) "Restaurar" else "Restaurar (${selectedIds.size})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        EmptyTrashConfirmDialog(
            itemCount = items.size,
            onConfirm = {
                showEmptyConfirm = false
                onEmptyTrash()
            },
            onDismiss = { showEmptyConfirm = false }
        )
    }
    if (showDeleteConfirm) {
        DeleteSelectedFromTrashConfirmDialog(
            count = selectedIds.size,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteSelected(selectedIds)
                selectedIds = emptySet()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun TrashLayerRow(item: TrashedLayerInfo, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BrandPurpleLight.copy(alpha = 0.16f) else SurfaceTintedElevated)
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                id = if (selected) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
            ),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceTintedDark)
        ) {
            if (item.imageFile != null) {
                AsyncImage(
                    model = item.imageFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = Color(0xFF8A7DB8),
                    modifier = Modifier.align(Alignment.Center).size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Eliminada el ${formatFullDate(item.deletedAtMs)} · ${formatFileSize(item.sizeBytes)}",
                color = Color(0xFF8A7DB8),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================================
// Confirmaciones (misma familia visual que el resto de diálogos destructivos
// de la app — ver, por ejemplo, el diálogo "¿Eliminar esta capa?" en
// EditorScreen.kt y "¿Limpiar el registro?" en ErrorLogScreen.kt).
// ============================================================================

/**
 * Confirmación de "Vaciar papelera" — frase acordada explícitamente con el
 * cliente: deja clarísimo que es irreversible y qué se pierde exactamente
 * (imágenes, keyframes, ajustes), no solo "¿estás seguro?" genérico.
 */
@Composable
fun EmptyTrashConfirmDialog(itemCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        containerColor = SurfaceTintedElevated,
        title = { Text("¿Vaciar la papelera?", color = Color.White) },
        text = {
            Text(
                "Esta acción es irreversible. Las $itemCount ${if (itemCount == 1) "capa" else "capas"} de la papelera, " +
                    "junto con sus imágenes, keyframes y ajustes, se eliminarán de forma permanente y no podrán recuperarse.",
                color = Color(0xFFD6CFEF)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Vaciar papelera", color = Color(0xFFE05C5C), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFD6CFEF)) }
        }
    )
}

/** Confirmación de "Eliminar seleccionadas" — mismo criterio que [EmptyTrashConfirmDialog], acotado a la cantidad tildada. */
@Composable
fun DeleteSelectedFromTrashConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        containerColor = SurfaceTintedElevated,
        title = { Text(if (count == 1) "¿Eliminar 1 capa?" else "¿Eliminar $count capas?", color = Color.White) },
        text = {
            Text(
                "Esta acción es irreversible. " +
                    (if (count == 1) "La capa seleccionada" else "Las $count capas seleccionadas") +
                    ", junto con sus imágenes, keyframes y ajustes, se eliminarán de forma permanente y no podrán recuperarse.",
                color = Color(0xFFD6CFEF)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Eliminar", color = Color(0xFFE05C5C), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFD6CFEF)) }
        }
    )
}
