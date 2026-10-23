package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yeivikas.olyzecs.engine.audio.AUDIO_VOLUME_MAX_DB
import com.yeivikas.olyzecs.engine.audio.AUDIO_VOLUME_MIN_DB
import com.yeivikas.olyzecs.engine.audio.audioVolumeDbEditText
import com.yeivikas.olyzecs.engine.audio.limitAudioVolumeDbInput
import com.yeivikas.olyzecs.engine.audio.parseAudioVolumeDbInput
import com.yeivikas.olyzecs.engine.audio.sanitizeAudioVolumeDbInput
import com.yeivikas.olyzecs.engine.audio.toggleAudioVolumeDbSign
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import kotlin.math.roundToInt

/** Atajos de nivel (dB) del cuadro: los que más se usan al mezclar. */
private val VOLUME_DB_PRESETS = listOf("-12", "-6", "-3", "0")

/**
 * Ventana para ESCRIBIR el volumen maestro de la capa, que se abre al tocar el valor numérico junto al deslizador.
 *
 * El campo acepta solo un «-» o «+» al inicio y el valor numérico (ver `sanitizeAudioVolumeDbInput`): sin letras ni
 * unidades; la unidad, «dB», es una etiqueta fija a su lado. Lo escrito se acota en vivo al rango del control. Como
 * muchos teclados no traen el signo menos, un botón «±» lo cambia. Debajo se ve el equivalente en porcentaje del
 * volumen original y unos atajos (-12, -6, -3, 0). «Aceptar» se habilita solo con un número válido; aplica el valor
 * a TODA la capa por el mismo camino que el deslizador (un solo paso de deshacer).
 *
 * @param currentVolume volumen lineal actual de la capa (deslizador maestro).
 * @param accentColor color de la capa: acento del borde, el cursor y el botón principal.
 * @param onConfirm recibe el volumen LINEAL ya acotado al rango del control.
 */
@Composable
internal fun AudioVolumeInputDialog(
    currentVolume: Float,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    val initialText = remember { audioVolumeDbEditText(currentVolume) }
    var field by remember { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    val focusRequester = remember { FocusRequester() }
    val parsedVolume = parseAudioVolumeDbInput(field.text)
    val isInvalid = parsedVolume == null && field.text.isNotEmpty()

    fun setText(text: String) {
        field = TextFieldValue(text, TextRange(text.length))
    }

    fun confirm() {
        parsedVolume?.let(onConfirm)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RectangleShape,
            color = SurfaceTintedElevated,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(32.dp)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Volumen",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Se aplica a todos los clips de esta capa",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = field,
                        onValueChange = { typed ->
                            val limited = limitAudioVolumeDbInput(sanitizeAudioVolumeDbInput(typed.text))
                            field = if (limited == typed.text) typed else TextFieldValue(limited, TextRange(limited.length))
                        },
                        singleLine = true,
                        isError = isInvalid,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            errorTextColor = Color.White,
                            cursorColor = accentColor,
                            errorCursorColor = accentColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            errorBorderColor = Color(0xFFFF6B6B)
                        ),
                        modifier = Modifier
                            .width(160.dp)
                            .focusRequester(focusRequester)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "dB",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when {
                        parsedVolume != null -> "≈ ${(parsedVolume * 100f).roundToInt()} % del volumen original"
                        else -> "Escribe + o - y el valor, por ejemplo -6 o +2.5"
                    },
                    color = if (isInvalid) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Rango: ${AUDIO_VOLUME_MIN_DB.roundToInt()} a +${"%.1f".format(AUDIO_VOLUME_MAX_DB)} dB · 0 dB = volumen original",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DbShortcutChip(label = "±", accentColor = accentColor) { setText(toggleAudioVolumeDbSign(field.text)) }
                    for (preset in VOLUME_DB_PRESETS) {
                        DbShortcutChip(label = preset, accentColor = accentColor) { setText(preset) }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(alpha = 0.8f)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { confirm() },
                        enabled = parsedVolume != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.12f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        )
                    ) { Text("Aceptar") }
                }
            }
        }
    }
}

/** Atajo táctil del cuadro de volumen: ocupa el mismo ancho que sus hermanos y pone su texto en el campo al tocarlo. */
@Composable
private fun RowScope.DbShortcutChip(label: String, accentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
