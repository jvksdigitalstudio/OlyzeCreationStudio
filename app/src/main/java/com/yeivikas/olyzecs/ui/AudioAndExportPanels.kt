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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.export.ExportProgress
import com.yeivikas.olyzecs.engine.export.ExportQuality
import com.yeivikas.olyzecs.engine.scene.AspectRatioPreset
import java.io.File

/**
 * Panel de audio de fondo del proyecto (pestaña "Audio"). A diferencia de
 * Cámara/Look, no depende de ninguna capa seleccionada — el audio es una
 * sola pista a nivel de proyecto entero.
 */
@Composable
fun AudioPanel(
    audioClip: AudioClip?,
    isImporting: Boolean,
    onImportClick: () -> Unit,
    onRemove: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onTrimStartChange: (Long) -> Unit,
    onLoopChange: (Boolean) -> Unit,
    onFadeChange: (fadeInMs: Long, fadeOutMs: Long) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (audioClip == null) {
            Text("Música o voz de fondo", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Agregá un audio para mezclarlo en el video exportado. Se recorta o repite en loop hasta completar la duración del proyecto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Button(onClick = onImportClick) { Text("Elegir audio") }
            }
            return@Column
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    audioClip.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Duración original: ${formatTimelineSeconds(audioClip.sourceDurationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onImportClick) { Text("Reemplazar") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = !audioClip.muted, onCheckedChange = { onToggleMute() })
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (audioClip.muted) "Silenciado" else "Sonando", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onRemove) { Text("Quitar audio", color = MaterialTheme.colorScheme.error) }
        }

        val volumeEnabled = !audioClip.muted
        LabeledSlider(
            "Volumen",
            audioClip.volume,
            0f..1.5f,
            enabled = volumeEnabled,
            onValueChange = onVolumeChange
        )

        if (audioClip.sourceDurationMs > 500L) {
            LabeledSlider(
                "Inicio del recorte",
                audioClip.trimStartMs.toFloat(),
                0f..(audioClip.sourceDurationMs - 100L).coerceAtLeast(1L).toFloat(),
                enabled = volumeEnabled,
                valueLabel = { formatTimelineSeconds(it.toLong()) },
                onValueChange = { onTrimStartChange(it.toLong()) }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Switch(checked = audioClip.loop, onCheckedChange = onLoopChange, enabled = volumeEnabled)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Repetir en loop", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Si el audio dura menos que el proyecto, se repite desde el inicio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Transiciones de volumen", style = MaterialTheme.typography.labelMedium)
        LabeledSlider(
            "Fade in",
            audioClip.fadeInMs.toFloat(),
            0f..3000f,
            enabled = volumeEnabled,
            valueLabel = { formatTimelineSeconds(it.toLong()) },
            onValueChange = { onFadeChange(it.toLong(), audioClip.fadeOutMs) }
        )
        LabeledSlider(
            "Fade out",
            audioClip.fadeOutMs.toFloat(),
            0f..3000f,
            enabled = volumeEnabled,
            valueLabel = { formatTimelineSeconds(it.toLong()) },
            onValueChange = { onFadeChange(audioClip.fadeInMs, it.toLong()) }
        )
    }
}

/** mm:ss para duraciones de audio; con decisecond cuando dura menos de 10s (más útil para fades cortos). */
private fun formatTimelineSeconds(ms: Long): String {
    return if (ms < 10_000L) {
        "%.1fs".format(ms / 1000f)
    } else {
        val totalSeconds = ms / 1000
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}

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
    aspect: AspectRatioPreset,
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
        Text(
            "${dims.first}×${dims.second}px · ${aspect.label} (${aspect.subtitle})",
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
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

/**
 * Panel de velocidad variable (speed ramping) y freeze frame — pestaña
 * "Tiempo". Igual que Audio, es a nivel de proyecto entero, no depende de
 * ninguna capa seleccionada: la velocidad y los freezes son propiedades
 * del TIMELINE, no de una imagen en particular.
 *
 * Ojo con el modelo: la velocidad/freeze NO reordenan ni comprimen el eje
 * de tiempo donde viven los keyframes de cámara — ese sigue siendo
 * 0..projectDurationMs de siempre. Lo que cambian es qué tan rápido avanza
 * ese tiempo durante la reproducción/exportación real, y por lo tanto
 * cuánto dura el video final (se muestra abajo, "Duración del video final").
 */
@Composable
fun TimeRampPanel(
    playheadMs: Long,
    projectDurationMs: Long,
    speedKeyframes: List<SpeedKeyframe>,
    freezeFrames: List<FreezeFrame>,
    outputDurationMs: Long,
    // Velocidad efectiva en este punto del proyecto, ya calculada por
    // EditorViewModel.speedAtPlayhead() — este panel solo la usa para
    // precargar el campo, no la calcula (ver Etapa 5).
    speedAtPlayhead: Float,
    onSetSpeedHere: (Float) -> Unit,
    onRemoveSpeedHere: () -> Unit,
    onAddFreezeHere: (Long) -> Unit,
    onRemoveFreeze: (String) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    var pendingSpeed by remember(playheadMs) {
        mutableStateOf(
            speedKeyframes.find { it.timeMs == playheadMs }?.speed ?: speedAtPlayhead
        )
    }
    var pendingFreezeHoldMs by remember { mutableStateOf(1000f) }
    val hasSpeedHere = speedKeyframes.any { it.timeMs == playheadMs }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Duración base (edición)", style = MaterialTheme.typography.labelSmall)
                    Text(formatTimelineSeconds(projectDurationMs), fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Duración del video final", style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatTimelineSeconds(outputDurationMs),
                        fontWeight = FontWeight.Medium,
                        color = if (outputDurationMs != projectDurationMs) MaterialTheme.colorScheme.primary else Color.Unspecified
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Velocidad variable", style = MaterialTheme.typography.titleSmall)
        Text(
            "Fijá la velocidad en distintos puntos del timeline para cámara lenta dramática o acelerados — se interpola entre puntos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        LabeledSlider(
            "Velocidad en ${formatTimelineSeconds(playheadMs)}",
            pendingSpeed,
            0.1f..4f,
            valueLabel = { "%.2fx".format(it) }
        ) { pendingSpeed = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(onClick = { onSetSpeedHere(pendingSpeed) }) {
                Text(if (hasSpeedHere) "Actualizar velocidad aquí" else "Fijar velocidad aquí")
            }
            if (hasSpeedHere) {
                OutlinedButton(onClick = onRemoveSpeedHere) { Text("Quitar") }
            }
        }

        if (speedKeyframes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            speedKeyframes.sortedBy { it.timeMs }.forEach { kf ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekTo(kf.timeMs) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatTimelineSeconds(kf.timeMs), style = MaterialTheme.typography.bodySmall)
                    Text("%.2fx".format(kf.speed), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text("Freeze frame", style = MaterialTheme.typography.titleSmall)
        Text(
            "Congela la imagen en el punto actual del timeline durante el tiempo que elijas — clásico cierre de escena.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        LabeledSlider(
            "Duración del freeze",
            pendingFreezeHoldMs,
            200f..5000f,
            valueLabel = { "%.1fs".format(it / 1000f) }
        ) { pendingFreezeHoldMs = it }
        Button(
            onClick = { onAddFreezeHere(pendingFreezeHoldMs.toLong()) },
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("Congelar en ${formatTimelineSeconds(playheadMs)}")
        }

        if (freezeFrames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            freezeFrames.sortedBy { it.atMs }.forEach { freeze ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${formatTimelineSeconds(freeze.atMs)} · sostiene %.1fs".format(freeze.holdMs / 1000f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { onSeekTo(freeze.atMs) }
                    )
                    IconButton(onClick = { onRemoveFreeze(freeze.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            painter = painterResource(id = com.yeivikas.olyzecs.R.drawable.ic_delete),
                            contentDescription = "Quitar freeze",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
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
    aspect: AspectRatioPreset,
    quality: ExportQuality,
    // Ver ExportQualityPanel: ya calculado por EditorViewModel.currentExportDimensions().
    dimensionsPx: Pair<Int, Int>,
    onQualityChange: (ExportQuality) -> Unit,
    exportProgress: ExportProgress?,
    onStartExport: (String) -> Unit,
    onShare: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val isExporting = exportProgress is ExportProgress.InProgress
    // Nombre de archivo editable, independiente del nombre del proyecto:
    // arranca sugerido con el nombre del proyecto (que es lo que se espera
    // por default), pero el usuario puede tocarlo y poner otro antes de
    // exportar sin necesidad de renombrar el proyecto entero.
    var fileName by remember(projectName) { mutableStateOf(projectName) }

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
                            aspect = aspect,
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
}
