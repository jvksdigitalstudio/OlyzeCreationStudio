package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yeivikas.olyzecs.engine.export.ExportProgress
import com.yeivikas.olyzecs.engine.export.ExportQuality
import com.yeivikas.olyzecs.engine.scene.CanvasSpec
import com.yeivikas.olyzecs.engine.scene.StaticFormatCatalog
import java.io.File

/**
 * Panel de calidad de exportación (bitrate/resolución en píxeles) — lo
 * único que queda configurable al momento de exportar. La duración del
 * proyecto y el formato de salida (9:16/1:1/16:9) se eligen al CREAR el
 * proyecto (ver `CreateProjectDialog` en `ProjectsScreen.kt`), porque son
 * propiedades del proyecto en sí, no solo del archivo final: afectan todo
 * el timeline, no algo que tenga sentido cambiar recién al exportar.
 */
@Composable
fun ExportQualityPanel(
    canvas: CanvasSpec,
    quality: ExportQuality,
    // Ancho x alto ya calculado por EditorViewModel.currentExportDimensions()
    // — este panel solo lo muestra, no lo calcula (ver Etapa 5).
    dimensionsPx: Pair<Int, Int>,
    onQualityChange: (ExportQuality) -> Unit
) {
    Column {
        Text("Calidad", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(6.dp))
        // Se arma en filas de a 2 (en vez de una sola fila con las 4 juntas)
        // para que cada chip tenga espacio de sobra para su texto —
        // "4K (UHD)" no entraba cómodo compartiendo una fila de 4 en
        // pantallas angostas.
        ExportQuality.entries.chunked(2).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowPresets.forEach { preset ->
                    SelectableChip(
                        label = preset.label,
                        selected = preset == quality,
                        onClick = { onQualityChange(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Si la última fila queda con un solo chip (cantidad impar de
                // presets), se rellena el espacio para que no se estire solo.
                if (rowPresets.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        val dims = dimensionsPx
        // ADR-005, Fase G: antes esta línea mostraba `aspect.label`/
        // `aspect.subtitle` de un AspectRatioPreset que, desde la Fase E,
        // ya no se elige al crear el proyecto y quedaba SIEMPRE en su
        // default ("9:16 · Reels · TikTok · Stories") sin importar el
        // Canvas real elegido — mostraba, por ejemplo, "9:16" para un
        // proyecto Cuadrado o Custom. Ahora se deriva del CanvasSpec real:
        // la proporción sale de `canvas.aspect` (siempre correcta, ver
        // ADR-005), y la descripción entre paréntesis intenta resolver el
        // preset de origen en el catálogo (`canvas.originPresetId`) para
        // mantener el mismo estilo de texto de siempre; si el proyecto es
        // Custom (o el preset de origen ya no existe en el catálogo), cae
        // a "Personalizado" en vez de inventar una descripción.
        val formatDescription = canvas.originPresetId
            ?.let { StaticFormatCatalog.findById(it)?.label }
            ?: "Personalizado"
        Text(
            "${dims.first}×${dims.second}px · ${canvas.aspect} ($formatDescription)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (quality == ExportQuality.UHD_4K) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "4K exporta más lento y pesa bastante más. Algunos dispositivos de gama baja pueden no soportar codificar a esta resolución.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Chip seleccionable simple (radio-button visual), reutilizado por formato/calidad en export y creación de proyecto. */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

/**
 * Diálogo modal de exportación, accesible desde el ícono de exportar en la
 * barra superior del editor — el flujo estándar de cualquier app de video
 * "profesional": tocás Exportar, elegís calidad/formato/duración ahí
 * mismo, confirmás, y ese MISMO diálogo pasa a mostrar el progreso y
 * después el resultado (compartir o cerrar), en vez de tener toda esa
 * configuración siempre a la vista dentro del panel de edición.
 */
@Composable
fun ExportDialog(
    projectName: String,
    canvas: CanvasSpec,
    quality: ExportQuality,
    // Ver ExportQualityPanel: ya calculado por EditorViewModel.currentExportDimensions().
    dimensionsPx: Pair<Int, Int>,
    onQualityChange: (ExportQuality) -> Unit,
    exportProgress: ExportProgress?,
    onStartExport: (String) -> Unit,
    onShare: (File) -> Unit,
    onDismiss: () -> Unit,
    // DISEÑO — auditoría (cancelar exportación en curso): separado a
    // propósito de `onDismiss` — cerrar el diálogo (`onDismiss`) y
    // cancelar la exportación (`onCancelExport`) son dos acciones
    // DISTINTAS que hasta ahora no existían por separado, porque
    // `onDismiss` estaba bloqueado por completo mientras se exportaba
    // (ver `Dialog(onDismissRequest = ...)` más abajo) — no había ninguna
    // forma de salir de acá durante un export, ni cerrando ni cancelando.
    onCancelExport: () -> Unit = {}
) {
    val isExporting = exportProgress is ExportProgress.InProgress
    // Nombre de archivo editable, independiente del nombre del proyecto:
    // arranca sugerido con el nombre del proyecto (que es lo que se espera
    // por default), pero el usuario puede tocarlo y poner otro antes de
    // exportar sin necesidad de renombrar el proyecto entero.
    var fileName by remember(projectName) { mutableStateOf(projectName) }
    // DISEÑO — auditoría: cancelar a mitad de un export largo (varios
    // minutos en un video 4K) tira todo el progreso hecho hasta ese
    // momento — es una acción destructiva y de una sola dirección (no hay
    // "reanudar"), así que exige una confirmación explícita antes de
    // ejecutarla, en vez de que un toque accidental sobre "Cancelar" tire
    // abajo minutos de espera sin aviso.
    var showCancelConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isExporting) onDismiss() }) {
        // Esquina recta ("punta"), no redondeada: es una ventana normal
        // (diálogo de exportación), no uno de los mini-menús flotantes
        // anclados a una manija del lienzo — esos son los únicos que
        // conservan esquina redondeada en toda la app.
        Surface(shape = RectangleShape, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 420.dp)
            ) {
                Text("Exportar \"$projectName\"", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))

                when (exportProgress) {
                    is ExportProgress.InProgress -> {
                        val inAudioPhase = exportProgress.audioPhaseFraction > 0f &&
                            exportProgress.fraction < exportProgress.audioPhaseFraction
                        val label = if (inAudioPhase) "Procesando audio…" else "Exportando video…"
                        Text("$label ${(exportProgress.fraction * 100).toInt()}%")
                        LinearProgressIndicator(
                            progress = { exportProgress.fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showCancelConfirm = true }) {
                                Text("Cancelar exportación", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    is ExportProgress.Done -> {
                        Text("Video listo: ${exportProgress.outputFile.name}", style = MaterialTheme.typography.bodyMedium)
                        if (exportProgress.audioRequested && !exportProgress.hasAudio) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "⚠️ El video se exportó SIN audio.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            val reason = exportProgress.audioFailureReason
                            if (reason != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Motivo: $reason",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                                TextButton(onClick = {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(reason))
                                }) { Text("Copiar motivo") }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onDismiss) { Text("Cerrar") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { onShare(exportProgress.outputFile) }) { Text("Compartir") }
                        }
                    }
                    is ExportProgress.Failed -> {
                        Text(
                            "Error al exportar: ${exportProgress.error.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onDismiss) { Text("Cerrar") }
                        }
                    }
                    ExportProgress.Cancelled -> {
                        Text(
                            "Exportación cancelada. No se guardó ningún archivo.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onDismiss) { Text("Cerrar") }
                        }
                    }
                    null -> {
                        OutlinedTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = { Text("Nombre del archivo") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ExportQualityPanel(
                            canvas = canvas,
                            quality = quality,
                            dimensionsPx = dimensionsPx,
                            onQualityChange = onQualityChange
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onDismiss) { Text("Cancelar") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onStartExport(fileName.ifBlank { projectName }) }
                            ) { Text("Exportar") }
                        }
                    }
                }
            }
        }
    }

    // DISEÑO — auditoría: confirmación explícita, en un diálogo aparte y
    // más chico (patrón estándar de Material — un AlertDialog corto para
    // una decisión binaria puntual), antes de tirar abajo el progreso.
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("¿Cancelar la exportación?") },
            text = { Text("Se perderá todo el progreso hecho hasta ahora. No se va a guardar ningún archivo.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onCancelExport()
                }) { Text("Sí, cancelar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Seguir exportando") }
            }
        )
    }
}
