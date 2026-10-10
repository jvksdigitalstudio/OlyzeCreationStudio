package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yeivikas.olyzecs.data.UserColorPrefs

/**
 * Selector de color profesional para "Elige un fondo" (ver
 * BackgroundPickerSection.kt) — PEDIDO EXPLÍCITO del usuario: "la paleta
 * más profesional y completa", no un puñado fijo de swatches sueltos.
 * Combina 3 formas de elegir, las mismas que trae cualquier selector de
 * color serio (Photoshop, Figma, el picker nativo de iOS):
 *   1. Rueda HSV de precisión + slider de brillo — el control fino.
 *   2. Campo HEX libre — para quien ya sabe el código exacto que quiere.
 *   3. Paleta curada (escala de grises + rueda de matices completa a
 *      máxima saturación/brillo) + "Guardados", que reutiliza
 *      [UserColorPrefs] TAL CUAL — los mismos colores que el usuario ya
 *      guardó desde la rueda de color de una capa (ver
 *      LayerColorPickerDialog en LayerDialogs.kt) aparecen acá, y
 *      viceversa: es una sola preferencia de color por usuario/dispositivo,
 *      no una lista aparte que hay que llenar de cero para el fondo.
 *
 * Reutiliza [ColorWheelPicker] (LayerDialogs.kt, `internal`, mismo
 * módulo) en vez de reimplementar una rueda de color nueva — es la MISMA
 * pieza ya pulida (lupa de precisión, modo Negro y Blanco no aplica acá
 * así que no se expone) que ya usa el diálogo de color de una capa, así
 * que el usuario encuentra una interacción idéntica en los dos lugares
 * de la app donde elige un color.
 */
@Composable
fun ProfessionalColorPickerDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (argb: Int) -> Unit
) {
    val context = LocalContext.current

    val initialHsv = remember(initialArgb) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialArgb, hsv)
        hsv
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }

    fun currentArgb(): Int =
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))

    // Campo HEX: texto libre, independiente de hue/saturation/brightness
    // mientras el usuario está escribiendo (para no pelearse con el
    // cursor en cada tecla) — recién se aplica a la rueda cuando el texto
    // ya forma un color válido de 6 dígitos.
    var hexText by remember(initialArgb) {
        mutableStateOf(String.format("%06X", 0xFFFFFF and initialArgb))
    }
    var hexError by remember { mutableStateOf(false) }

    fun applyHex(text: String) {
        val clean = text.removePrefix("#").uppercase()
        if (clean.length == 6 && clean.all { it.isDigit() || it in 'A'..'F' }) {
            val argb = (0xFF000000).toInt() or clean.toInt(16)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, hsv)
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
            hexError = false
        } else {
            hexError = true
        }
    }

    val savedColors = remember { UserColorPrefs.loadSavedColors(context) }
    val recentColors = remember { UserColorPrefs.loadRecentColors(context) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 420.dp)
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Elegir color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))

                ColorWheelPicker(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onColorChange = { h, s ->
                        hue = h; saturation = s
                        hexText = String.format("%06X", 0xFFFFFF and currentArgb())
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(220.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Brillo", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        hexText = String.format("%06X", 0xFFFFFF and currentArgb())
                    },
                    valueRange = 0f..1f
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Vista previa del color actual junto al campo HEX —
                    // así se ve el resultado real incluso si el usuario
                    // llega acá escribiendo un HEX a mano, sin tocar la
                    // rueda para nada.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(currentArgb()))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = {
                            hexText = it
                            applyHex(it)
                        },
                        label = { Text("HEX") },
                        prefix = { Text("#") },
                        singleLine = true,
                        isError = hexError,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Paleta", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                val paletteScroll = rememberScrollState()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalFadingEdges(paletteScroll)
                        .horizontalScroll(paletteScroll)
                ) {
                    PROFESSIONAL_PALETTE.forEach { argb ->
                        ColorSwatch(
                            argb = argb,
                            selected = currentArgb() == argb,
                            onClick = {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb, hsv)
                                hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                                hexText = String.format("%06X", 0xFFFFFF and argb)
                            }
                        )
                    }
                }

                if (savedColors.isNotEmpty() || recentColors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Guardados y recientes", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val savedScroll = rememberScrollState()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .horizontalFadingEdges(savedScroll)
                            .horizontalScroll(savedScroll)
                    ) {
                        // Degradados guardados no aplican acá (el fondo del
                        // proyecto es un color sólido) — se filtran, no se
                        // aplanan a un tono cualquiera que traicionaría lo
                        // que el usuario guardó.
                        (savedColors + recentColors)
                            .filter { !it.isGradient }
                            .mapNotNull { it.colorArgb }
                            .distinct()
                            .forEach { argb ->
                                ColorSwatch(
                                    argb = argb,
                                    selected = currentArgb() == argb,
                                    onClick = {
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(argb, hsv)
                                        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                                        hexText = String.format("%06X", 0xFFFFFF and argb)
                                    }
                                )
                            }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val argb = currentArgb()
                        UserColorPrefs.recordRecentColor(context, argb)
                        onConfirm(argb)
                    }) { Text("Usar este color") }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

/**
 * Paleta curada: escala de grises (6 pasos, negro a blanco) + rueda de
 * matices completa a máxima saturación/brillo (12 pasos, cada 30°) — la
 * misma cobertura de color que trae cualquier selector "básico" de un
 * editor profesional antes de que el usuario toque la rueda fina. El
 * verde chroma key (ver [CHROMA_KEY_GREEN_ARGB] en
 * BackgroundPickerSection.kt) va primero, marcado aparte, porque es el
 * default real del fondo — tiene que ser el más fácil de volver a elegir
 * si el usuario se fue a probar otro color y se arrepiente.
 */
private val PROFESSIONAL_PALETTE: List<Int> = buildList {
    add(CHROMA_KEY_GREEN_ARGB)
    // Escala de grises.
    listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f).forEach { v ->
        add(android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, v)))
    }
    // Rueda de matices completa, saturación y brillo máximos.
    (0 until 12).forEach { step ->
        add(android.graphics.Color.HSVToColor(floatArrayOf(step * 30f, 1f, 1f)))
    }
}
