package com.yeivikas.olyzecs.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.ui.theme.BrandBlueLight
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Registro de errores" — pantalla de diagnóstico en tiempo real, equivalente
 * a la pestaña Debug de FL Studio o al log de un job fallido de GitHub
 * Actions: TODO lo que [AppLogger] fue capturando durante el uso de la app
 * (crashes, exportaciones fallidas, audio que no cargó, shaders que no
 * compilaron, etc.), ordenado del más reciente al más antiguo, listo para
 * copiar y pegar en un solo toque.
 *
 * Se muestra como diálogo de pantalla completa (no una nueva "ruta" de
 * navegación) para no tener que tocar la estructura de MainActivity — se
 * abre y cierra igual que cualquier otro diálogo de la app.
 */
@Composable
fun ErrorLogScreen(onClose: () -> Unit) {
    val entries by AppLogger.entries.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            kotlinx.coroutines.delay(1800)
            copiedFeedback = false
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BrandPurpleDeep
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- Encabezado ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(SurfaceTintedElevated, SurfaceTintedDark)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(BrandPurpleLight, BrandBlueLight)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bug_report),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Registro de errores",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                if (entries.isEmpty()) "Sin errores registrados"
                                else "${entries.size} registro(s) en tiempo real",
                                fontSize = 12.sp,
                                color = Color(0xFFBFB3E0)
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = "Cerrar",
                                tint = Color.White
                            )
                        }
                    }
                }

                // --- Lista de registros ---
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bug_report),
                                contentDescription = null,
                                tint = Color(0xFF8A7DB8),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Todo limpio por ahora",
                                color = Color(0xFFD6CFEF),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Los errores que ocurran usando la app van a aparecer acá al instante.",
                                color = Color(0xFF8A7DB8),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries.asReversed()) { entry ->
                            LogEntryCard(entry)
                        }
                    }
                }

                // --- Barra inferior de acciones ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceTintedElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        enabled = entries.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD6CFEF))
                    ) {
                        Text("Limpiar")
                    }
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(AppLogger.formatAllForCopy()))
                            copiedFeedback = true
                            Toast.makeText(context, "Registro copiado — ya lo podés pegar", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandPurpleLight,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (copiedFeedback) "¡Copiado! ✓" else "Copiar todo", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { showClearConfirm = false },
            containerColor = SurfaceTintedElevated,
            title = { Text("¿Limpiar el registro?", color = Color.White) },
            text = {
                Text(
                    "Se van a borrar todos los errores guardados hasta ahora, tanto de esta sesión como de sesiones anteriores.",
                    color = Color(0xFFD6CFEF)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AppLogger.clear()
                    showClearConfirm = false
                }) { Text("Limpiar", color = Color(0xFFE08A3C)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancelar", color = Color(0xFFD6CFEF)) }
            }
        )
    }
}

@Composable
private fun LogEntryCard(entry: AppLogger.LogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val (badgeColor, badgeLabel) = when (entry.level) {
        AppLogger.Level.ERROR -> Color(0xFFE05C5C) to "ERROR"
        AppLogger.Level.WARN -> Color(0xFFE0A83C) to "AVISO"
        AppLogger.Level.INFO -> Color(0xFF4C7FD6) to "INFO"
    }
    val formattedTime = remember(entry.timestampMillis) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMillis))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceTintedElevated)
            .clickable(enabled = entry.stackTrace != null) { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.22f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(badgeLabel, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(entry.tag, color = Color(0xFFBFB3E0), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(formattedTime, color = Color(0xFF8A7DB8), fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(entry.message, color = Color.White, fontSize = 13.sp)

        AnimatedVisibility(visible = expanded && entry.stackTrace != null) {
            Text(
                entry.stackTrace.orEmpty(),
                color = Color(0xFFBFB3E0),
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(BrandPurpleDeep.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }
        if (entry.stackTrace != null) {
            Text(
                if (expanded) "Ocultar detalle ▲" else "Ver detalle completo ▼",
                color = Color(0xFF8A7DB8),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
