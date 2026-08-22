package com.yeivikas.olyzecs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import com.yeivikas.olyzecs.ui.theme.LayerTrackColors
import com.yeivikas.olyzecs.ui.theme.effectiveLayerBrush
import com.yeivikas.olyzecs.ui.theme.gradientBrushFor
import com.yeivikas.olyzecs.ui.theme.effectiveLayerColor
import com.yeivikas.olyzecs.ui.theme.effectiveLayerColorStrong
import com.yeivikas.olyzecs.ui.theme.layerTrackColor
import com.yeivikas.olyzecs.ui.theme.layerTrackColorStrong
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeivikas.olyzecs.data.UserColorPrefs
import com.yeivikas.olyzecs.viewmodel.ColorPrefsViewModel
import com.yeivikas.olyzecs.viewmodel.ColorPrefsViewModelFactory
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.scene.Layer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mini ventana para renombrar una capa, disparada por el ícono de lápiz
 * del panel de acciones de la fila. Mismo patrón visual que
 * RenameProjectDialog (EditorScreen.kt) — campo de texto único + Cancelar/
 * Guardar — pero con un acento del color propio de la capa, para que se
 * lea como parte de la fila que la abrió, no un diálogo genérico.
 */
@Composable
fun RenameLayerDialog(
    initialName: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    Dialog(onDismissRequest = onDismiss) {
        // Esquina recta ("punta"): ventana normal, no un mini-menú
        // flotante anclado a manija.
        Surface(
            shape = RectangleShape,
            color = SurfaceTintedElevated,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Renombrar capa",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(text.trim()) },
                        enabled = text.isNotBlank()
                    ) { Text("Guardar") }
                }
            }
        }
    }
}

/**
 * Foto del estado EN CURSO (todavía sin aplicar) del diálogo de color —
 * necesaria para el cuentagotas: la ventanita se tiene que cerrar del
 * todo para que el usuario pueda tocar la imagen del preview (que vive
 * detrás, en otra ventana/sistema), así que todo lo que llevaba armado
 * hasta ese momento (degradado a medio armar, ángulo elegido, etc.) tiene
 * que guardarse acá afuera para poder reabrir el diálogo EXACTAMENTE donde
 * lo dejó, con el nuevo color ya cargado en el destino que tenía activo.
 */
data class ColorPickerSnapshot(
    val solidArgb: Int,
    val gradientAArgb: Int,
    val gradientBArgb: Int,
    val gradientEnabled: Boolean,
    val activeSlot: String?,
    val gradientAngleDegrees: Float,
    val gradientIsRadial: Boolean,
    val blackAndWhiteMode: Boolean
)

/**
 * Portapapeles de color en memoria — vive mientras el proceso de la app
 * esté vivo (no necesita sobrevivir un reinicio, es un gesto de "copiar
 * en esta capa, pegar en esa otra" dentro de la MISMA sesión de edición,
 * igual que copiar/pegar texto normal). Un simple `object` de Kotlin
 * alcanza: no hace falta levantar estado hasta EditorScreen/ViewModel
 * solo para esto, y así "Copiar"/"Pegar" quedan 100% autocontenidos
 * dentro del diálogo de color, sin tocar nada fuera de esta capa.
 */
private object ColorClipboard {
    var entry: UserColorPrefs.SavedColorEntry? = null
}

/**
 * Mini ventana de color de la capa, disparada por el ícono de paleta del
 * panel de acciones de la fila. Combina varias formas de elegir color,
 * todas sincronizadas entre sí en tiempo real:
 *
 * 1. Rueda de color HSV (matiz por ángulo, saturación por radio) + slider
 *    de brillo + slider de opacidad — cualquier color, sin límite de
 *    cuántos hay, con lupa de precisión mientras se arrastra.
 * 2. Código hexadecimal manual (#RRGGBB, con atajo de 3 dígitos) — para
 *    quien ya sabe el número exacto que quiere.
 * 3. Nombre del color en vivo + armonías automáticas (complementario,
 *    análogos, tríada) + colores "Rápidos", "Recientes" y "Guardados".
 * 4. Degradado opcional de dos colores (A/B) con ángulo libre en grados o
 *    modo radial, más "Copiar"/"Pegar" para llevar un color entero de una
 *    capa a otra.
 * 5. Simulador de daltonismo (solo vista previa, nunca afecta el valor
 *    real guardado).
 */
@Composable
fun LayerColorPickerDialog(
    initialColorArgb: Int?,
    initialGradientStartArgb: Int?,
    initialGradientEndArgb: Int?,
    initialUseGradient: Boolean,
    initialGradientAngleDegrees: Float,
    initialGradientIsRadial: Boolean,
    // Modo Negro & Blanco con el que quedó armado el color/degradado
    // ACTUAL de la capa (ver Layer.useBlackAndWhiteMode) — antes esto NO
    // se leía de vuelta acá, así que el switch SIEMPRE arrancaba apagado
    // sin importar cómo se hubiera aplicado el color la última vez. Ese
    // era el bug real reportado: "aplico en blanco y negro, reabro el
    // diálogo y el switch aparece apagado (aunque el color sigue gris)".
    initialBlackAndWhiteMode: Boolean = false,
    // Qué destino retomar como activo al abrir (null/"A"/"B") — usado al
    // reabrir después del cuentagotas, para volver EXACTAMENTE a donde
    // estaba (no siempre "A" por defecto). Cuando no viene de una
    // reanudación, null hace que se calcule como siempre (A si el
    // degradado ya viene activo, si no ninguno).
    initialActiveSlot: String? = null,
    fallbackColorArgb: Int,
    onDismiss: () -> Unit,
    // El segundo parámetro (Boolean) es el modo Negro & Blanco con el que
    // se armó este color — quien recibe el callback debe guardarlo junto
    // con el color (ver Layer.useBlackAndWhiteMode) para poder
    // restaurarlo la próxima vez que se abra este mismo diálogo.
    onSelectColor: (Int, Boolean) -> Unit,
    onSelectGradient: (startArgb: Int, endArgb: Int, angleDegrees: Float, isRadial: Boolean, useBlackAndWhiteMode: Boolean) -> Unit,
    onReset: () -> Unit,
    // Dispara el modo cuentagotas: entrega una foto de TODO lo armado
    // hasta ahora para que quien llama (TimelineRow) la guarde y pueda
    // reabrir este mismo diálogo, ya con el color tomado del preview
    // cargado en el destino que estaba activo. null (por defecto) = sin
    // cuentagotas disponible acá — así el panel legado "Capas" en
    // EditorScreen.kt, que no tiene el mecanismo de reanudación armado,
    // simplemente no muestra el botón en vez de mostrar uno roto.
    onRequestEyedropper: ((ColorPickerSnapshot) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Ya no se llama a UserColorPrefs (data/) directo — encontrado en la
    // segunda auditoría previa a EliNer API. El diálogo solo conoce este
    // ViewModel, igual que ProjectsScreen solo conoce ProjectsViewModel.
    val colorPrefsViewModel: ColorPrefsViewModel =
        viewModel(factory = ColorPrefsViewModelFactory(context))
    var savedColors by remember { mutableStateOf(colorPrefsViewModel.loadSavedColors()) }
    var recentColors by remember { mutableStateOf(colorPrefsViewModel.loadRecentColors()) }
    // Vista de daltonismo: solo afecta la VISTA PREVIA (círculo de arriba
    // y cuadrado del degradado) — nunca el color real que se guarda. Sirve
    // para chequear cómo se vería el color elegido antes de aplicarlo,
    // igual criterio que el simulador de accesibilidad de Figma.
    var colorBlindMode by remember { mutableStateOf(ColorBlindMode.NORMAL) }
    // Ángulo/tipo del degradado — 90° = arriba→abajo (el comportamiento
    // original), editable con precisión de grado o con atajos de
    // dirección; Radial lo ignora por completo.
    var gradientAngleDegrees by remember { mutableStateOf(initialGradientAngleDegrees) }
    var gradientIsRadial by remember { mutableStateOf(initialGradientIsRadial) }

    // Arranca con el color actual de la capa (el personalizado si ya tiene
    // uno; si no, el automático de la paleta) — no siempre desde cero.
    val startSolidArgb = initialColorArgb ?: fallbackColorArgb
    var solidArgb by remember { mutableStateOf(startSolidArgb) }
    // Los dos extremos del degradado se mantienen SIEMPRE en memoria
    // (aunque el modo esté apagado), para no perder lo armado si el
    // usuario prende/apaga el switch varias veces dentro del mismo diálogo.
    var gradientAArgb by remember { mutableStateOf(initialGradientStartArgb ?: startSolidArgb) }
    var gradientBArgb by remember { mutableStateOf(initialGradientEndArgb ?: startSolidArgb) }
    var gradientEnabled by remember { mutableStateOf(initialUseGradient) }

    // Modo Negro & Blanco: opcional, independiente del degradado — cuando
    // está prendido, la rueda de color entera se dibuja en escala de
    // grises (negro en el borde, blanco en el centro) y arrastrar dentro
    // de ella elige un gris puro (misma saturación=0 para R=G=B) según
    // qué tan lejos del centro se suelte el dedo, en vez de matiz/
    // saturación real. [grayRadius] es la posición del "dedo" dentro de
    // ese eje blanco↔negro — se mantiene SEPARADA de [saturation] (la
    // real, la que arma el color en modo color) para que activar/
    // desactivar este modo no pise ni sea pisado por el color real de
    // fondo cuando no está activo.
    var blackAndWhiteMode by remember { mutableStateOf(initialBlackAndWhiteMode) }
    var grayRadius by remember { mutableStateOf(0f) }

    // Qué "destino" recibe ahora mismo los cambios de la rueda/hex/brillo:
    // null = el color sólido; "A"/"B" = ese extremo del degradado.
    var activeSlot by remember {
        mutableStateOf(initialActiveSlot ?: (if (initialUseGradient) "A" else null as String?))
    }

    fun argbForSlot(slot: String?): Int = when (slot) {
        "A" -> gradientAArgb
        "B" -> gradientBArgb
        else -> solidArgb
    }

    // Estado HSV(A) de la rueda — siempre refleja el color del destino activo.
    var hue by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(0f) }
    var brightness by remember { mutableStateOf(1f) }
    // Opacidad (alpha) — cuarta dimensión del color, independiente de
    // matiz/saturación/brillo. Antes el motor SIEMPRE forzaba alfa=100%
    // (HSVToColor de Android no admite alfa salvo que se lo pidas
    // explícito) — cualquier capa quedaba sin forma de ser semitransparente.
    var alpha by remember { mutableStateOf(1f) }

    // Recarga hue/sat/brillo/alfa del destino activo cada vez que cambia
    // (p. ej. al tocar el cuadradito B) — así la rueda "salta" a mostrar
    // ESE color en vez de quedarse en el anterior. SIN piso artificial: un
    // color que en verdad es #000000 (negro puro) tiene que poder cargarse
    // y mostrarse tal cual, no "corregido" a un gris oscuro — forzar un
    // piso acá era un motor de color mintiendo sobre el valor real que el
    // usuario había guardado.
    LaunchedEffect(activeSlot) {
        val argb = argbForSlot(activeSlot)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
        alpha = android.graphics.Color.alpha(argb) / 255f
        // Sincroniza también la posición del "dedo" en el eje blanco↔negro
        // con el brillo real del color que se acaba de cargar — así si se
        // activa Negro & Blanco justo después de cambiar de destino (A/B),
        // el círculo arranca en el punto que de verdad corresponde a ese
        // color, no en 0 a secas.
        grayRadius = 1f - hsv[2]
    }

    fun applyToActiveTarget(newHue: Float, newSaturation: Float, newBrightness: Float, newAlpha: Float = alpha) {
        hue = newHue; saturation = newSaturation; brightness = newBrightness; alpha = newAlpha
        // HSVToColor(FloatArray) de Android siempre devuelve alfa=255 — hay
        // que armar el ARGB final a mano para que la opacidad elegida en
        // el slider de abajo realmente quede guardada en el color.
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(newHue, newSaturation, newBrightness))
        val alphaByte = (newAlpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val argb = (alphaByte shl 24) or (rgb and 0x00FFFFFF)
        when (activeSlot) {
            "A" -> gradientAArgb = argb
            "B" -> gradientBArgb = argb
            else -> solidArgb = argb
        }
    }

    /**
     * Variante de [applyToActiveTarget] para el modo Negro & Blanco: fuerza
     * saturación=0 (R=G=B, gris puro) y usa [gray] directo como brillo —
     * el matiz (hue) queda fuera de la cuenta por completo, a propósito,
     * porque en escala de grises no significa nada. La opacidad elegida se
     * conserva igual que en el modo color.
     */
    fun applyGrayToActiveTarget(gray: Float) {
        val clampedGray = gray.coerceIn(0f, 1f)
        saturation = 0f
        brightness = clampedGray
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, clampedGray))
        val alphaByte = (alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val argb = (alphaByte shl 24) or (rgb and 0x00FFFFFF)
        when (activeSlot) {
            "A" -> gradientAArgb = argb
            "B" -> gradientBArgb = argb
            else -> solidArgb = argb
        }
    }

    /**
     * Igual que [applyToActiveTarget] pero a partir de un color ya armado
     * (un preset, uno guardado) en vez de HSV suelto — SIN pisos
     * artificiales: cualquiera sea el matiz/saturación/brillo real de
     * [argb], se cargan exactos. La opacidad NO se toca acá (los presets
     * de la paleta y los guardados no traen una opacidad "correcta" que
     * imponer) — se mantiene la que ya estaba puesta en el slider.
     */
    fun applyArgbToActiveTarget(argb: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        applyToActiveTarget(hsv[0], hsv[1], hsv[2], alpha)
    }

    /**
     * Variante de [applyArgbToActiveTarget] que SÍ respeta la opacidad
     * propia de [argb] en vez de conservar la que estaba puesta — para
     * "Guardados": ahí el usuario armó un color completo (matiz +
     * saturación + brillo + opacidad) a propósito y lo guardó así; cargarlo
     * de vuelta debe devolver EXACTAMENTE eso, no solo el matiz con la
     * opacidad de lo que se estaba editando antes de tocarlo.
     */
    fun applyExactArgbToActiveTarget(argb: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        val exactAlpha = android.graphics.Color.alpha(argb) / 255f
        applyToActiveTarget(hsv[0], hsv[1], hsv[2], exactAlpha)
    }

    /**
     * Aplica una entrada de "Guardados": si es un degradado, carga AMBOS
     * extremos y prende el modo degradado (con A como destino activo) —
     * un degradado guardado es un estado completo de dos colores, no un
     * color suelto para pisar el destino activo actual. Si es un color
     * sólido, se comporta como cualquier preset: solo pisa el destino
     * activo (sólido, o el extremo A/B que esté seleccionado).
     */
    fun applySavedEntry(entry: UserColorPrefs.SavedColorEntry) {
        if (entry.isGradient && entry.gradientStartArgb != null && entry.gradientEndArgb != null) {
            gradientAArgb = entry.gradientStartArgb
            gradientBArgb = entry.gradientEndArgb
            gradientEnabled = true
            activeSlot = "A"
            applyExactArgbToActiveTarget(entry.gradientStartArgb)
        } else if (entry.colorArgb != null) {
            applyExactArgbToActiveTarget(entry.colorArgb)
        }
    }

    // Antes esto forzaba alfa=255 (HSVToColor de Android no admite alfa),
    // así que la opacidad elegida en el slider nunca se reflejaba ni en la
    // vista previa en vivo ni en la miniatura del campo hex — quedaba
    // guardada bien en el dato pero invisible en la UI mientras se armaba.
    val currentTargetColor = remember(hue, saturation, brightness, alpha) {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        val alphaByte = (alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        Color((alphaByte shl 24) or (rgb and 0x00FFFFFF))
    }

    // Código hex del destino activo — se mantiene sincronizado desde el
    // color MIENTRAS el campo no tiene el foco (si el usuario está
    // escribiendo, su tipeo manda y no se lo pisa a mitad de camino).
    var hexFieldFocused by remember { mutableStateOf(false) }
    var hexText by remember(activeSlot) {
        mutableStateOf(String.format("#%06X", 0xFFFFFF and currentTargetColor.toArgb()))
    }
    LaunchedEffect(currentTargetColor, hexFieldFocused) {
        if (!hexFieldFocused) {
            hexText = String.format("#%06X", 0xFFFFFF and currentTargetColor.toArgb())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RectangleShape,
            color = SurfaceTintedElevated,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    // 360dp → 400dp: el panel de al lado de la rueda de
                    // color (GradientSidePanel) volvió a poner las
                    // opciones Degradado / Negro & Blanco a la DERECHA de
                    // la barra y los cuadritos A/B (en vez de debajo) — a
                    // 360dp no entraban las tres columnas sin cortar el
                    // nombre de las opciones; 400dp les da el aire que
                    // necesitan sin volverse un diálogo gigante.
                    .widthIn(max = 400.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Vista previa en vivo: si el degradado está prendido,
                // muestra AMBOS colores con su ángulo/tipo REAL (no
                // aplanado a vertical) en el mismo círculo. Si el modo de
                // daltonismo no es "Normal", el color/degradado que se ve
                // acá pasa por [simulateColorBlindness] — es pura vista
                // previa, NUNCA toca el valor real guardado. Se calcula acá
                // arriba porque el header (título + círculo) la necesita
                // de entrada.
                val previewColorA = remember(gradientAArgb, colorBlindMode) {
                    simulateColorBlindness(Color(gradientAArgb), colorBlindMode)
                }
                val previewColorB = remember(gradientBArgb, colorBlindMode) {
                    simulateColorBlindness(Color(gradientBArgb), colorBlindMode)
                }
                val previewSolid = remember(currentTargetColor, colorBlindMode) {
                    simulateColorBlindness(currentTargetColor, colorBlindMode)
                }

                // --- Encabezado: título a la izquierda, vista previa en
                // vivo (el círculo) a la DERECHA, a la misma altura del
                // título y pegada al borde derecho real del diálogo — el
                // mismo borde donde terminan los sliders de Brillo/Opacidad
                // más abajo. Antes el círculo vivía metido adentro del
                // panel de degradado, mucho más abajo y descolgado del
                // título; ahora el Row ocupa el ancho completo (fillMaxWidth)
                // y el círculo queda anclado arriba a la derecha, tal como
                // en el boceto de referencia.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Color de la capa",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                brush = if (gradientEnabled) {
                                    gradientBrushFor(previewColorA, previewColorB, gradientAngleDegrees, gradientIsRadial)
                                } else {
                                    SolidColor(previewSolid)
                                }
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- Fila principal: rueda de color a la izquierda, panel
                // de degradado a la derecha. fillMaxWidth() acá es lo que
                // hace que GradientSidePanel (que tiene weight(1f)) se
                // estire de verdad hasta el borde derecho real del diálogo
                // — sin esto el Row solo ocupaba el ancho mínimo de su
                // contenido y el panel quedaba corto, sin llegar hasta
                // donde terminan los sliders de abajo.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    ColorWheelPicker(
                        hue = hue,
                        // En modo Negro & Blanco la rueda usa [grayRadius]
                        // (el eje blanco↔negro) en vez de la saturación
                        // real como posición del "dedo" — son cosas
                        // distintas a propósito, ver el comentario junto a
                        // la declaración de grayRadius más arriba.
                        saturation = if (blackAndWhiteMode) grayRadius else saturation,
                        brightness = brightness,
                        blackAndWhiteMode = blackAndWhiteMode,
                        onColorChange = { h, s ->
                            if (blackAndWhiteMode) {
                                hue = h
                                grayRadius = s
                                applyGrayToActiveTarget(1f - s)
                            } else {
                                applyToActiveTarget(h, s, brightness)
                            }
                        },
                        modifier = Modifier.size(200.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    GradientSidePanel(
                        gradientEnabled = gradientEnabled,
                        onToggleGradient = { enabled ->
                            gradientEnabled = enabled
                            activeSlot = if (enabled) "A" else null
                        },
                        blackAndWhiteMode = blackAndWhiteMode,
                        onToggleBlackAndWhite = { enabled -> blackAndWhiteMode = enabled },
                        colorA = Color(gradientAArgb),
                        colorB = Color(gradientBArgb),
                        previewColorA = previewColorA,
                        previewColorB = previewColorB,
                        angleDegrees = gradientAngleDegrees,
                        isRadial = gradientIsRadial,
                        activeSlot = activeSlot,
                        onSelectSlot = { slot -> activeSlot = slot },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // Controles de dirección del degradado — SOLO tienen
                // sentido con el modo prendido, por eso ni aparecen si no.
                if (gradientEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    GradientDirectionControls(
                        angleDegrees = gradientAngleDegrees,
                        isRadial = gradientIsRadial,
                        onAngleChange = { gradientAngleDegrees = it },
                        onRadialChange = { gradientIsRadial = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Brillo: tercera dimensión del HSV que la rueda sola no
                // cubre (esa solo mueve matiz+saturación a brillo fijo).
                Text(
                    "Brillo · ${(brightness * 100).roundToInt()}%",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = brightness,
                    onValueChange = { applyToActiveTarget(hue, saturation, it) },
                    // Rango completo 0f..1f: antes arrancaba en 0.12f, o
                    // sea que el slider ni SIQUIERA dejaba llegar al negro
                    // puro (#000000) — una limitación real para un motor
                    // de color que se precie de profesional.
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentTargetColor,
                        activeTrackColor = currentTargetColor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Opacidad: cuarta dimensión del color, antes inexistente
                // en el motor — sin esto, ninguna capa podía ser
                // semitransparente, solo 100% opaca u "oculta" del todo.
                Text(
                    "Opacidad · ${(alpha * 100).roundToInt()}%",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = alpha,
                    onValueChange = { applyToActiveTarget(hue, saturation, brightness, it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentTargetColor,
                        activeTrackColor = currentTargetColor
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Código hex manual — funciona igual en modo sólido o en
                // modo degradado (edita SIEMPRE el destino activo: sólido,
                // o el extremo A/B que esté seleccionado a la derecha).
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        val raw = text.removePrefix("#").trim()
                        // Soporta el atajo "#FFF" (3 dígitos, cada uno se
                        // duplica) además del "#FFFFFF" completo — atajo
                        // estándar en cualquier motor de color serio.
                        val isHexChar = { c: Char -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }
                        val expanded = when {
                            raw.length == 3 && raw.all(isHexChar) -> raw.map { "$it$it" }.joinToString("")
                            raw.length == 6 && raw.all(isHexChar) -> raw
                            else -> null
                        }
                        if (expanded != null) {
                            val parsedArgb = runCatching { android.graphics.Color.parseColor("#$expanded") }.getOrNull()
                            if (parsedArgb != null) {
                                // El hex controla SOLO el color RGB — la
                                // opacidad es una dimensión aparte (el
                                // slider de arriba), igual convención que
                                // Figma/Sketch: no se pisa la opacidad
                                // actual al escribir un hex de 6 dígitos.
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsedArgb, hsv)
                                applyToActiveTarget(hsv[0], hsv[1], hsv[2], alpha)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { hexFieldFocused = it.isFocused },
                    singleLine = true,
                    label = { Text("Código de color (RGB)") },
                    placeholder = { Text("#FFFFFF") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(currentTargetColor)
                        )
                    }
                )

                // Nombre aproximado del color, calculado en vivo — toque
                // premium que da contexto rápido sin tener que memorizar
                // hex, igual criterio que Procreate/Affinity.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        colorDisplayName(hue, saturation, brightness),
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .padding(top = 4.dp, start = 2.dp)
                            .weight(1f)
                    )
                    // Cuentagotas: toma el color EXACTO de un pixel del
                    // preview en vivo (glReadPixels sobre lo que dibuja
                    // GLRenderer) — no aparece si quien abrió este diálogo
                    // no armó el mecanismo de reanudación (ver
                    // ColorPickerSnapshot), como el panel legado "Capas".
                    if (onRequestEyedropper != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable {
                                    onRequestEyedropper(
                                        ColorPickerSnapshot(
                                            solidArgb = solidArgb,
                                            gradientAArgb = gradientAArgb,
                                            gradientBArgb = gradientBArgb,
                                            gradientEnabled = gradientEnabled,
                                            activeSlot = activeSlot,
                                            gradientAngleDegrees = gradientAngleDegrees,
                                            gradientIsRadial = gradientIsRadial,
                                            blackAndWhiteMode = blackAndWhiteMode
                                        )
                                    )
                                }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_eyedropper),
                                contentDescription = "Cuentagotas",
                                tint = BrandPurpleLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cuentagotas", color = BrandPurpleLight, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Armonías automáticas (complementario/análogos/tríada) a
                // partir del matiz actual — como Adobe Color/Coolors. Cada
                // chip conserva la saturación/brillo/opacidad de lo que se
                // está editando, solo cambia el matiz.
                Text("Armonías", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    harmonyHues(hue).forEach { (_, harmonicHue) ->
                        // BUG: antes esto ignoraba colorBlindMode por
                        // completo — "Vista con daltonismo" solo cambiaba
                        // el circulito de arriba a la derecha, y estos
                        // chips (que son justo donde el usuario COMPARA
                        // colores entre sí) se quedaban siempre en su
                        // versión "normal". Ahora también pasan por
                        // simulateColorBlindness cuando el modo no es
                        // Normal, igual que el resto del panel.
                        val harmonicColor = remember(harmonicHue, saturation, brightness, colorBlindMode) {
                            simulateColorBlindness(
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(harmonicHue, saturation, brightness))),
                                colorBlindMode
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(harmonicColor)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable { applyToActiveTarget(harmonicHue, saturation, brightness) }
                        )
                    }
                }

                // "Recientes": se llena SOLO cada vez que se toca
                // "Aplicar" — no hace falta acordarse de guardar nada a
                // mano para tener a mano lo último que se usó.
                if (recentColors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Recientes", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        recentColors.take(9).forEach { entry ->
                            // Mismo fix que Armonías/Rápidos: la vista
                            // previa respeta colorBlindMode; el color
                            // REAL que se aplica al tocar (applySavedEntry)
                            // no se toca.
                            val swatchBrush = if (entry.isGradient && entry.gradientStartArgb != null && entry.gradientEndArgb != null) {
                                Brush.verticalGradient(
                                    listOf(
                                        simulateColorBlindness(Color(entry.gradientStartArgb), colorBlindMode),
                                        simulateColorBlindness(Color(entry.gradientEndArgb), colorBlindMode)
                                    )
                                )
                            } else {
                                SolidColor(simulateColorBlindness(Color(entry.colorArgb ?: Color.Gray.toArgb()), colorBlindMode))
                            }
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(brush = swatchBrush)
                                    .combinedClickableColorSwatch(
                                        onClick = { applySavedEntry(entry) },
                                        onLongClick = {
                                            recentColors = colorPrefsViewModel.removeRecentEntry(entry)
                                        }
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Rápidos", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LayerTrackColors.forEach { presetColor ->
                        val displayColor = remember(presetColor, colorBlindMode) {
                            simulateColorBlindness(presetColor, colorBlindMode)
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(displayColor)
                                .clickable { applyArgbToActiveTarget(presetColor.toArgb()) }
                        )
                    }
                }

                // "Guardados": solo aparece si el usuario ya guardó algo —
                // no tiene sentido un título vacío la primera vez que abre
                // el diálogo.
                if (savedColors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Guardados", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        savedColors.take(9).forEach { entry ->
                            // Un guardado de degradado se pinta CON su
                            // degradado real (no aplanado a un tono) —
                            // así el usuario ve de un vistazo cuál de sus
                            // guardados es sólido y cuál es degradado.
                            // También respeta colorBlindMode (mismo fix
                            // que Armonías/Rápidos/Recientes de arriba).
                            val swatchBrush = if (entry.isGradient && entry.gradientStartArgb != null && entry.gradientEndArgb != null) {
                                Brush.verticalGradient(
                                    listOf(
                                        simulateColorBlindness(Color(entry.gradientStartArgb), colorBlindMode),
                                        simulateColorBlindness(Color(entry.gradientEndArgb), colorBlindMode)
                                    )
                                )
                            } else {
                                SolidColor(simulateColorBlindness(Color(entry.colorArgb ?: Color.Gray.toArgb()), colorBlindMode))
                            }
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(brush = swatchBrush)
                                    .combinedClickableColorSwatch(
                                        onClick = { applySavedEntry(entry) },
                                        onLongClick = {
                                            savedColors = colorPrefsViewModel.removeSavedEntry(entry)
                                        }
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Guardar el color/extremo actual en "Guardados" — un
                // atajo chico de texto en vez de otro botón grande, para
                // no competir visualmente con Restablecer/Cancelar/Aplicar.
                // Guarda el ESTADO COMPLETO actual: si el degradado está
                // prendido, guarda el degradado entero (A + B); si no,
                // guarda el color sólido — nunca aplana uno a lo otro.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (gradientEnabled) "+ Guardar este degradado" else "+ Guardar este color",
                        color = BrandPurpleLight,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable {
                                savedColors = if (gradientEnabled) {
                                    colorPrefsViewModel.addSavedGradient(gradientAArgb, gradientBArgb)
                                } else {
                                    colorPrefsViewModel.addSavedColor(solidArgb)
                                }
                            }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Copiar/Pegar: llevar un color (o degradado) completo de
                // ESTA capa a OTRA capa — abrís el diálogo de la otra
                // capa y tocás "Pegar color". Portapapeles en memoria
                // ([ColorClipboard]), autocontenido en este diálogo.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Copiar color",
                        color = BrandPurpleLight,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable {
                            ColorClipboard.entry = if (gradientEnabled) {
                                UserColorPrefs.SavedColorEntry.gradient(gradientAArgb, gradientBArgb)
                            } else {
                                UserColorPrefs.SavedColorEntry.solid(solidArgb)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    val clipboardEntry = ColorClipboard.entry
                    Text(
                        "Pegar color",
                        color = if (clipboardEntry != null) BrandPurpleLight else Color.White.copy(alpha = 0.25f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.then(
                            if (clipboardEntry != null) {
                                Modifier.clickable { applySavedEntry(clipboardEntry) }
                            } else Modifier
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Simulador de daltonismo: cambia la VISTA PREVIA de todo
                // el panel (círculo de arriba, degradado, Armonías,
                // Recientes, Rápidos, Guardados), nunca el color real que
                // se termina guardando — es para chequear legibilidad/
                // distinguibilidad de la paleta entera antes de aplicar,
                // no un filtro que se aplica a la capa.
                Text("Vista con daltonismo", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorBlindMode.entries.forEach { mode ->
                        DirectionModeChip(
                            label = mode.label,
                            selected = colorBlindMode == mode,
                            onClick = { colorBlindMode = mode }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        onReset()
                        onDismiss()
                    }) { Text("Restablecer") }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(onClick = {
                            if (gradientEnabled) {
                                onSelectGradient(gradientAArgb, gradientBArgb, gradientAngleDegrees, gradientIsRadial, blackAndWhiteMode)
                                colorPrefsViewModel.recordRecentGradient(gradientAArgb, gradientBArgb)
                            } else {
                                onSelectColor(solidArgb, blackAndWhiteMode)
                                colorPrefsViewModel.recordRecentColor(solidArgb)
                            }
                            onDismiss()
                        }) { Text("Aplicar") }
                    }
                }
            }
        }
    }
}

/**
 * Panel lateral del picker — barra de degradado, cuadraditos A/B y las
 * opciones a activar/desactivar, TODO en una sola fila horizontal, en ese
 * orden de izquierda a derecha:
 *
 *   [ barra ] [A]  (●) ← vista previa en vivo
 *   [ alta  ] [B]  [Ⓢ] Degradado
 *              [Ⓢ] Negro & Blanco
 *
 * (la barra y los A/B miden lo mismo de alto que la rueda de color, 200dp
 * — ver Modifier.size(200.dp) en el punto donde se llaman ambos, en
 * LayerColorPickerDialog — así los dos controles principales del diálogo
 * pesan igual visualmente). Cada opción es un switch con su nombre
 * COMPLETO al pie, centrado, en vez de al lado: puesto al lado, en un
 * panel angosto, "Negro & Blanco" no entraba y se veía cortado a
 * "Negro &"; puesto debajo, el ancho disponible para el texto ya no
 * depende de cuánto mide el switch, así que el nombre entero entra
 * cómodo en dos líneas cortas.
 *
 * BUG REAL corregido acá: la vista previa en vivo (el círculo chico) vivía
 * en la esquina superior derecha del diálogo ENTERO (en el header, fuera
 * de este panel) — terminaba más a la derecha que las opciones de abajo,
 * sin relación visual con ellas. Ahora vive DENTRO de la misma Column que
 * Degradado/Negro & Blanco, arriba de todo — alineada con ellas por
 * construcción (misma Column, mismo ancho fijo MODE_ICON_SIZE), no por
 * coincidencia de medidas calculadas a mano en dos lugares distintos.
 */
@Composable
private fun GradientSidePanel(
    gradientEnabled: Boolean,
    onToggleGradient: (Boolean) -> Unit,
    blackAndWhiteMode: Boolean,
    onToggleBlackAndWhite: (Boolean) -> Unit,
    colorA: Color,
    colorB: Color,
    previewColorA: Color,
    previewColorB: Color,
    angleDegrees: Float,
    isRadial: Boolean,
    activeSlot: String?,
    onSelectSlot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeight = 200.dp

    // El bloque [barra + A/B] queda CENTRADO entre la rueda de color (a la
    // izquierda de este panel, fuera de este Row) y la columna de íconos
    // Degradado/Negro & Blanco (pineada al borde derecho, mismo borde que
    // el círculo de vista previa del encabezado y los sliders de abajo).
    // Antes todo el panel (barra + A/B + íconos) iba empaquetado junto y
    // pegado entero al borde derecho — la barra y los cuadritos A/B
    // quedaban comprimidos justo al lado de los íconos, sin nada de aire
    // entre ambos bloques. Ahora dos Spacer(weight(1f)) — uno antes de la
    // barra, otro entre A/B y los íconos — reparten el espacio sobrante en
    // partes iguales a los dos lados del bloque [barra + A/B], así flota
    // centrado en el hueco libre a la izquierda de los íconos, mientras
    // los íconos se quedan fijos en su borde derecho de siempre.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Barra del degradado REAL (respeta ángulo/radial elegido abajo en
        // GradientDirectionControls) — delgada en ancho, tan alta como la
        // rueda de color.
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    brush = if (gradientEnabled) {
                        gradientBrushFor(previewColorA, previewColorB, angleDegrees, isRadial)
                    } else {
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (gradientEnabled) 0.35f else 0.12f),
                    shape = RoundedCornerShape(10.dp)
                )
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Cuadraditos A / B, centrados dentro de esa misma altura — solo
        // tocables con el degradado prendido; el seleccionado queda con
        // anillo blanco.
        Column(
            modifier = Modifier.height(barHeight),
            verticalArrangement = Arrangement.Center
        ) {
            GradientSlotSwatch(
                label = "A",
                color = colorA,
                enabled = gradientEnabled,
                isSelected = activeSlot == "A",
                onClick = { onSelectSlot("A") }
            )
            Spacer(modifier = Modifier.height(18.dp))
            GradientSlotSwatch(
                label = "B",
                color = colorB,
                enabled = gradientEnabled,
                isSelected = activeSlot == "B",
                onClick = { onSelectSlot("B") }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Las dos opciones a activar/desactivar, a la DERECHA de A/B — en
        // una Column de ancho fijo (MODE_ICON_SIZE), así quedan alineadas
        // entre sí por construcción. Se pueden combinar entre sí: un
        // degradado en escala de grises es un caso válido.
        //
        // La vista previa en vivo (el círculo) YA NO vive acá — se movió
        // al encabezado del diálogo, junto al título. Con el círculo
        // afuera:
        //  - verticalArrangement pasó de Center a Top: antes el bloque
        //    quedaba centrado verticalmente en barHeight, lo que lo
        //    empujaba bien abajo del tope real de la barra/círculo; ahora
        //    arranca justo a esa misma altura, como en el boceto.
        //  - "Degradado" y "Negro & Blanco" ya NO son un Switch con texto
        //    al pie: son dos ÍCONOS cuadrados clickeables autoexplicativos
        //    (ver [GradientModeIcon] y [BlackAndWhiteModeIcon] más abajo),
        //    apilados en el mismo orden de siempre — arriba Degradado,
        //    abajo Negro & Blanco — sin título, porque el propio diseño
        //    del ícono ya deja claro qué hace cada uno.
        Column(
            modifier = Modifier
                .height(barHeight)
                .width(MODE_ICON_SIZE),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            GradientModeIcon(
                checked = gradientEnabled,
                onCheckedChange = onToggleGradient
            )
            Spacer(modifier = Modifier.height(10.dp))
            BlackAndWhiteModeIcon(
                checked = blackAndWhiteMode,
                onCheckedChange = onToggleBlackAndWhite
            )
        }
    }
}

// Tamaño fijo compartido por [GradientModeIcon] y [BlackAndWhiteModeIcon] —
// así los dos quedan en la misma columna vertical y con el mismo peso
// visual que el círculo de vista previa del encabezado (28.dp, ver el Row
// del título más arriba) y el borde derecho de los sliders de Brillo/
// Opacidad, en vez de salir del ancho variable de un texto.
private val MODE_ICON_SIZE = 34.dp

// Acento usado en el aro de ambos íconos cuando su modo está ACTIVADO —
// mismo morado vivo de marca que ya se usa en el resto del picker (switch
// prendido, selección, etc.), para que el "encendido" se lea consistente
// en toda la pantalla.
private val MODE_ICON_ACTIVE_RING = BrandPurpleLight

/**
 * Ícono premium y clickeable para el modo "Degradado": un cuadrado de
 * esquinas redondeadas relleno con un degradado REAL de arriba hacia abajo
 * — azul → violeta de marca → magenta → ámbar — más un barniz de brillo
 * diagonal sutil arriba-izquierda para que se lea como un ícono con
 * acabado de vidrio/plástico premium en vez de un rectángulo de color
 * plano (esto es justo lo que la referencia del usuario pedía mejorar: el
 * degradado del ícono se ve más realista con ese barniz que sin él).
 *
 * Reemplaza al viejo Switch + texto "Degradado" — ya no hace falta título
 * porque el propio ícono (cuadrado degradado + pictograma de switch al
 * centro) ya comunica qué controla de un vistazo.
 *
 * Al centro lleva [ModeSwitchGlyph], que además de decorar dobla como
 * INDICADOR DE ESTADO: con [checked]=true el aro del ícono se tiñe del
 * acento morado de marca y la manija del pictograma se corre a la
 * derecha rellena; con [checked]=false el aro queda apagado (blanco muy
 * tenue) y la manija vuelve a la izquierda, hueca — se nota "activado"
 * vs "desactivado" sin necesidad de leer ninguna palabra.
 */
@Composable
private fun GradientModeIcon(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor by animateColorAsState(
        targetValue = if (checked) MODE_ICON_ACTIVE_RING else Color.White.copy(alpha = 0.16f),
        label = "gradientIconRing"
    )
    Box(
        modifier = modifier
            .size(MODE_ICON_SIZE)
            .shadow(
                elevation = if (checked) 5.dp else 0.dp,
                shape = RoundedCornerShape(10.dp),
                clip = false
            )
            .clip(RoundedCornerShape(10.dp))
            .background(PREMIUM_GRADIENT_ICON_BRUSH)
            .drawBehind {
                // Barniz de brillo: un reflejo diagonal semitransparente
                // desde la esquina superior izquierda — sin esto el
                // degradado se ve plano/impreso; con esto se lee como una
                // superficie con volumen, tipo ícono de app premium.
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width * 0.7f, size.height * 0.8f)
                    )
                )
            }
            .border(
                width = if (checked) 1.6.dp else 1.dp,
                color = ringColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        ModeSwitchGlyph(on = checked)
    }
}

// Degradado premium de arriba hacia abajo usado como fondo de
// [GradientModeIcon] — cuatro paradas (azul, violeta de marca, magenta,
// ámbar) en vez de dos, para que se vea rico/realista y no un simple
// degradado de dos colores como el del boceto de referencia.
private val PREMIUM_GRADIENT_ICON_BRUSH = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF3E8BFF),
        BrandPurpleLight,
        Color(0xFFE0479E),
        Color(0xFFFFB13C)
    )
)

/**
 * Ícono premium y clickeable para el modo "Negro & Blanco": el mismo
 * cuadrado de esquinas redondeadas que [GradientModeIcon], pero partido
 * en dos mitades verticales — negra a la izquierda, blanca a la derecha,
 * separadas por una línea sutil — en vez de degradado de color, siguiendo
 * el ícono de referencia. Mismo pictograma [ModeSwitchGlyph] al centro,
 * que funciona igual como indicador de estado (aro de acento morado +
 * manija a la derecha cuando está activado).
 */
@Composable
private fun BlackAndWhiteModeIcon(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor by animateColorAsState(
        targetValue = if (checked) MODE_ICON_ACTIVE_RING else Color.White.copy(alpha = 0.16f),
        label = "bwIconRing"
    )
    Box(
        modifier = modifier
            .size(MODE_ICON_SIZE)
            .shadow(
                elevation = if (checked) 5.dp else 0.dp,
                shape = RoundedCornerShape(10.dp),
                clip = false
            )
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                val half = size.width / 2f
                drawRect(color = Color(0xFF16151C), size = Size(half, size.height))
                drawRect(
                    color = Color(0xFFF3F3F6),
                    topLeft = Offset(half, 0f),
                    size = Size(size.width - half, size.height)
                )
                // Línea divisoria sutil — para que el corte en dos mitades
                // se lea como una decisión de diseño, no como un glitch.
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(half, 0f),
                    end = Offset(half, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .border(
                width = if (checked) 1.6.dp else 1.dp,
                color = ringColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        ModeSwitchGlyph(on = checked)
    }
}

/**
 * Pictograma de switch (píldora + manija circular) dibujado a mano con
 * [Canvas] — se usa al centro de [GradientModeIcon] y
 * [BlackAndWhiteModeIcon] por igual. Cumple doble función: es parte del
 * diseño del ícono Y es el indicador de estado on/off, para que ninguno
 * de los dos íconos necesite texto al lado.
 *
 * Con [on]=true: la píldora se rellena de blanco sólido y la manija se
 * corre al extremo derecho, oscura sobre el fondo blanco — "prendido".
 * Con [on]=false: el relleno blanco se desvanece a 0, la píldora queda
 * solo con contorno (hueca) y la manija vuelve al extremo izquierdo,
 * blanca sobre fondo hueco — "apagado". Ambos estados se animan con
 * [animateFloatAsState] para que el toque se sienta como un switch real
 * y no un cambio brusco de dibujo.
 */
@Composable
private fun ModeSwitchGlyph(on: Boolean, modifier: Modifier = Modifier) {
    val thumbProgress by animateFloatAsState(targetValue = if (on) 1f else 0f, label = "glyphThumb")
    val trackFillAlpha by animateFloatAsState(targetValue = if (on) 0.95f else 0f, label = "glyphTrackFill")
    Canvas(modifier = modifier.size(width = 19.dp, height = 10.dp)) {
        val trackRadius = size.height / 2f
        val outline = Color.Black.copy(alpha = 0.8f)
        val corner = CornerRadius(trackRadius, trackRadius)

        if (trackFillAlpha > 0f) {
            drawRoundRect(
                color = Color.White.copy(alpha = trackFillAlpha),
                size = size,
                cornerRadius = corner
            )
        }
        drawRoundRect(
            color = outline,
            size = size,
            cornerRadius = corner,
            style = Stroke(width = 1.3.dp.toPx())
        )

        val thumbRadius = trackRadius - 1.5.dp.toPx()
        val thumbCenterX = trackRadius + thumbProgress * (size.width - size.height)
        val thumbCenter = Offset(thumbCenterX, trackRadius)
        drawCircle(
            color = if (trackFillAlpha > 0.4f) outline else Color.White,
            radius = thumbRadius,
            center = thumbCenter
        )
        drawCircle(
            color = outline,
            radius = thumbRadius,
            center = thumbCenter,
            style = Stroke(width = 1.1.dp.toPx())
        )
    }
}

@Composable
private fun GradientSlotSwatch(
    label: String,
    color: Color,
    enabled: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // --- BUG REAL: antes, "apagado" era `color.copy(alpha = 0.25f)`,
        // que compone al 25% sobre el fondo OSCURO del diálogo (Surface
        // morado casi negro) — el resultado matemático de mezclar un
        // naranja brillante al 25% con ese fondo es un marrón/vino apagado
        // que no se parece en nada al color real de la capa (ver captura:
        // "swatches A/B muestran un color viejo marrón"). No era un color
        // viejo cacheado: era el MISMO color de siempre, pero
        // matemáticamente irreconocible por el alpha-blend. Ahora, en vez
        // de bajar el alpha (lo que tiñe todo hacia el color de fondo), se
        // mezcla el color real hacia un gris neutro mantenido a opacidad
        // completa — se ve "apagado"/deshabilitado igual, pero el matiz
        // real sigue siendo reconocible de un vistazo.
        val disabledTint = remember(color) { lerp(color, Color(0xFF6B6B78), 0.55f) }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (enabled) color else disabledTint)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(6.dp)
                )
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        )
        Text(
            label,
            color = Color.White.copy(alpha = if (enabled) 0.85f else 0.35f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Controles de dirección del degradado: alternar Lineal/Radial, y si es
 * Lineal, el ángulo exacto en grados — con 4 atajos de dirección (↓ → ↘ ↙)
 * para los casos comunes de un toque, más un slider fino para cualquier
 * ángulo intermedio. Antes el degradado SIEMPRE iba de arriba hacia abajo
 * sin excepción — esto lo vuelve un degradado de verdad "direccionable",
 * como el de Figma/Photoshop.
 */
@Composable
private fun GradientDirectionControls(
    angleDegrees: Float,
    isRadial: Boolean,
    onAngleChange: (Float) -> Unit,
    onRadialChange: (Boolean) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tipo:", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(8.dp))
            DirectionModeChip("Lineal", selected = !isRadial, onClick = { onRadialChange(false) })
            Spacer(modifier = Modifier.width(6.dp))
            DirectionModeChip("Radial", selected = isRadial, onClick = { onRadialChange(true) })
        }

        // El ángulo no tiene sentido en modo Radial (es circular desde el
        // centro, no tiene "dirección") — se oculta entero en vez de
        // mostrarlo deshabilitado, para no confundir con un control muerto.
        if (!isRadial) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dirección:", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(8.dp))
                DirectionShortcutButton("↓", angleValue = 90f, current = angleDegrees, onClick = onAngleChange)
                Spacer(modifier = Modifier.width(6.dp))
                DirectionShortcutButton("→", angleValue = 0f, current = angleDegrees, onClick = onAngleChange)
                Spacer(modifier = Modifier.width(6.dp))
                DirectionShortcutButton("↘", angleValue = 45f, current = angleDegrees, onClick = onAngleChange)
                Spacer(modifier = Modifier.width(6.dp))
                DirectionShortcutButton("↙", angleValue = 135f, current = angleDegrees, onClick = onAngleChange)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Ángulo · ${angleDegrees.roundToInt()}°",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = angleDegrees,
                onValueChange = onAngleChange,
                valueRange = 0f..359f
            )
        }
    }
}

@Composable
private fun DirectionModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) BrandPurpleLight.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (selected) BrandPurpleLight else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DirectionShortcutButton(symbol: String, angleValue: Float, current: Float, onClick: (Float) -> Unit) {
    val isActive = kotlin.math.abs(current - angleValue) < 0.5f
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) BrandPurpleLight.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (isActive) BrandPurpleLight else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick(angleValue) },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

/** Tap normal = elegir ese color guardado; mantener presionado = borrarlo de "Guardados". */
private fun Modifier.combinedClickableColorSwatch(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = this.pointerInput(Unit) {
    detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
}

/**
 * Rueda de color HSV real: matiz (hue) según el ángulo alrededor del
 * centro, saturación según la distancia al centro (0 = blanco, borde =
 * saturación plena) — igual criterio que la rueda de referencia que
 * mandó el usuario. El brillo (value) NO se controla acá — vive en el
 * slider aparte de [LayerColorPickerDialog] — pero sí se usa para pintar
 * la rueda con el brillo actual, así el usuario ve en todo momento cómo
 * se ve su color real, no una versión siempre a brillo máximo.
 *
 * Tocar o arrastrar en cualquier punto DENTRO del círculo mueve el punto
 * de selección ahí mismo; arrastrar fuera del borde se recorta al borde
 * (satura al máximo en esa dirección) en vez de perder el gesto.
 */
// Piso de brillo SOLO para el dibujo del anillo de matices de
// ColorWheelPicker (ver comentario junto a displayBrightness ahí abajo) —
// por debajo de este valor el matiz deja de distinguirse a simple vista,
// así que no tiene sentido bajar más el brillo de DIBUJO aunque el color
// real elegido sea más oscuro todavía.
private const val MIN_WHEEL_DISPLAY_BRIGHTNESS = 0.35f

@Composable
internal fun ColorWheelPicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    // Modo Negro & Blanco: la rueda entera se vuelve escala de grises —
    // negro en TODO el borde, blanco hacia el centro (mismo degradado
    // blanco→transparente que ya se dibuja encima para la saturación, así
    // que reutilizarlo es gratis: basta con pintar la base del círculo de
    // negro en vez del arcoíris de matices). El resto de la interacción
    // (arrastrar, la lupa, el thumb) no cambia — solo cambia qué colores
    // se ven y qué significa la posición dentro del círculo, que decide
    // el llamador vía [onColorChange].
    blackAndWhiteMode: Boolean = false,
    onColorChange: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // 24 muestras alrededor del círculo alcanza para una transición de
    // matiz visualmente continua (sweepGradient interpola entre ellas).
    //
    // BUG REAL corregido acá: el anillo de matices se pintaba con
    // HSVToColor(h, 1, brightness) usando el brillo REAL del color
    // elegido — con brightness=0 (negro puro, MUY común: es justo lo que
    // deja seleccionado el modo Negro & Blanco al soltar en el borde),
    // TODO matiz da negro (V=0 anula el matiz en HSV). Resultado: con
    // cualquier color casi negro, el círculo se veía sólido negro con el
    // centro blanco — visualmente IDÉNTICO al modo Negro & Blanco — así
    // que apagar Negro & Blanco parecía no hacer nada. displayBrightness
    // es un PISO mínimo solo para EL DIBUJO del anillo (nunca para el
    // color que en verdad se aplica, que sigue usando `brightness` tal
    // cual) — así el anillo de matices siempre se ve utilizable, y recién
    // el slider de Brillo de abajo oscurece el resultado final.
    val displayBrightness = brightness.coerceAtLeast(MIN_WHEEL_DISPLAY_BRIGHTNESS)
    val hueColors = remember(displayBrightness, blackAndWhiteMode) {
        if (blackAndWhiteMode) {
            // Un solo color (negro) repetido: sweepGradient con una lista
            // de colores todos iguales pinta un círculo sólido de ese
            // color, sin transición de matiz — exactamente lo que se
            // pidió: "todo el borde negro".
            List(25) { Color.Black }
        } else {
            (0..24).map { step ->
                val h = step * 15f
                Color(android.graphics.Color.HSVToColor(floatArrayOf(h % 360f, 1f, displayBrightness)))
            }
        }
    }

    // --- Lupa de precisión (como el selector de color nativo de iOS/
    // Android): mientras el dedo arrastra sobre la rueda, TAPA justo el
    // punto que se está mirando — sin esto, elegir un tono preciso en una
    // pantalla de celular es adivinar a ciegas debajo del propio dedo. Se
    // guarda la posición Y el color exacto de ESE punto (no el color
    // "confirmado" del estado, que puede ir un frame atrás) para que la
    // lupa sea 1:1 con donde está tocando el dedo en este instante.
    //
    // BUG REAL (reportado): el offset de la lupa se calculaba SOLO en
    // base a la posición del dedo, sin acotarla nunca al tamaño de la
    // propia rueda — y como Compose NO recorta a un hijo contra los
    // límites de su padre a menos que se pida explícitamente, apenas el
    // dedo tocaba cerca del borde (arriba/derecha/etc.) la lupa terminaba
    // flotando por ENCIMA de controles del diálogo que no tienen nada que
    // ver (el campo hex, los swatches A/B, el slider de Brillo) — "se
    // pasea por toda la ventana". Los selectores nativos (iOS/Android)
    // nunca hacen eso: la lupa se queda pegada al control, y si no hay
    // lugar arriba del dedo, se voltea para aparecer abajo en su lugar.
    // Eso es justo lo que hace [wheelSizePx] + el clamp/flip de abajo.
    var loupePosition by remember { mutableStateOf<Offset?>(null) }
    var loupeColor by remember { mutableStateOf(Color.White) }
    var wheelSizePx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val loupeDiameterPx = with(density) { 64.dp.toPx() }
    val loupeGapPx = with(density) { 46.dp.toPx() }

    // --- BUG REAL (reportado): el gesto de arrastre vive en un
    // `pointerInput(Unit)` — la clave es fija (Unit), así que esa
    // corrutina se lanza UNA sola vez y queda corriendo en segundo plano
    // durante toda la vida del composable, sin volver a ejecutarse en
    // cada recomposición. `updateFromOffset` (acá abajo) leía el
    // parámetro `brightness` directo — pero como es una función local
    // redefinida en cada recomposición, la versión que la corrutina
    // capturó al arrancar quedó "congelada" con el brillo que había EN
    // ESE MOMENTO. Si después bajabas el slider de Brillo y volvías a
    // arrastrar sobre la rueda, la corrutina seguía viva desde antes y
    // seguía usando aquel brillo viejo — por eso la lupa se veía
    // saturada/clara aunque el slider ya estuviera en 21%, mientras que
    // el anillo de la rueda (pintado por `hueColors`, que SÍ está en un
    // `remember(brightness)` que se recalcula solo) sí se veía oscurecido
    // correctamente. `rememberUpdatedState` resuelve esto: da un `State`
    // cuyo `.value` siempre es el más reciente, sin importar cuándo se
    // lanzó la corrutina que lo lee.
    val latestBrightness = rememberUpdatedState(brightness)
    // Mismo motivo que latestBrightness: blackAndWhiteMode es un parámetro
    // suelto (no un State), y el gesto de abajo vive en un pointerInput(Unit)
    // que se lanza una sola vez — sin este wrapper, prender/apagar Negro &
    // Blanco a mitad de un arrastre dejaría la lupa mostrando el modo
    // viejo hasta el próximo toque.
    val latestBlackAndWhiteMode = rememberUpdatedState(blackAndWhiteMode)

    fun updateFromOffset(offset: Offset, canvasSize: IntSize) {
        val (h, s) = hueSaturationFromOffset(offset, canvasSize)
        onColorChange(h, s)
        loupePosition = offset
        wheelSizePx = canvasSize
        // En modo Negro & Blanco la lupa debe mostrar el gris que en
        // verdad se va a aplicar (negro en el borde, blanco en el
        // centro) — no el color con matiz que saldría de tratar (h, s)
        // como HSV normal.
        loupeColor = if (latestBlackAndWhiteMode.value) {
            val gray = (1f - s).coerceIn(0f, 1f)
            Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, gray)))
        } else {
            Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, latestBrightness.value)))
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> updateFromOffset(offset, size) },
                        onDragEnd = { loupePosition = null },
                        onDragCancel = { loupePosition = null }
                    ) { change, _ ->
                        change.consume()
                        updateFromOffset(change.position, size)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            updateFromOffset(offset, size)
                            tryAwaitRelease()
                            loupePosition = null
                        }
                    )
                }
        ) {
            val radius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Anillo de matices a saturación/brillo plenos.
            drawCircle(brush = Brush.sweepGradient(hueColors), radius = radius, center = center)
            // Degradado blanco→transparente encima, del centro hacia el borde:
            // desatura hacia el centro (blanco), deja el borde en saturación
            // plena — así la distancia al centro pasa a representar saturación.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            // Borde sutil, para que se note el límite del círculo tocable.
            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = radius,
                center = center,
                style = Stroke(width = 2f)
            )

            // Manija: posición actual de hue/saturation dentro del círculo.
            val angleRad = Math.toRadians(hue.toDouble())
            val r = saturation.coerceIn(0f, 1f) * radius
            val thumb = Offset(
                center.x + (r * cos(angleRad)).toFloat(),
                center.y + (r * sin(angleRad)).toFloat()
            )
            drawCircle(color = Color.White, radius = 11f, center = thumb, style = Stroke(width = 3f))
            drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 11f, center = thumb, style = Stroke(width = 1f))
        }

        // La lupa en sí: un círculo flotante grande con el color exacto,
        // centrado arriba del dedo (nunca tapado por él). Solo visible
        // MIENTRAS se toca/arrastra — el resto del tiempo no ocupa espacio
        // ni distrae. Ahora SIEMPRE se mantiene dentro del ancho de la
        // rueda (clamp en X) y, si tocar cerca del borde superior no deja
        // lugar para mostrarla arriba, se voltea para aparecer debajo del
        // dedo en su lugar (clamp + flip en Y) — nunca se sale a
        // superponerse con otros controles del diálogo.
        loupePosition?.let { pos ->
            val halfLoupe = loupeDiameterPx / 2f
            val minX = halfLoupe
            val maxX = (wheelSizePx.width - halfLoupe).coerceAtLeast(minX)
            val clampedCenterX = pos.x.coerceIn(minX, maxX)

            // BUG REAL (el que reportaste): acá solo se acotaba UN lado de
            // cada rama —
            //  - Rama "hay lugar arriba" (spaceAbove >= 0) se usaba TAL
            //    CUAL, sin techo: si el dedo se arrastraba MUY por debajo
            //    de la rueda (el dedo sigue mandando posiciones aunque se
            //    salga del área chica de 200dp, Compose no lo corta), acá
            //    "arriba" seguía siendo un número enorme y la lupa
            //    terminaba flotando bien abajo, superpuesta con Brillo/
            //    Opacidad/el campo hex (ver tu segunda captura).
            //  - Rama "volteada abajo" (flip) tenía coerceAtMost (piso de
            //    abajo) pero NUNCA un coerceAtLeast(0f) — si el dedo se
            //    arrastraba MUY por arriba de la rueda (hacia el título
            //    "Color de la capa"), pos.y quedaba negativo y esa resta
            //    daba un resultado negativo igual, así que la lupa se iba
            //    de cabeza contra el título (tu primera captura).
            // Ahora AMBAS ramas quedan acotadas con coerceIn(0f, maxTop) —
            // pase lo que pase con la posición cruda del dedo, la lupa
            // nunca puede salir del rectángulo de la propia rueda.
            val maxTop = (wheelSizePx.height - loupeDiameterPx).coerceAtLeast(0f)
            val spaceAbove = pos.y - loupeGapPx - loupeDiameterPx
            val loupeTop = if (spaceAbove >= 0f) {
                spaceAbove.coerceIn(0f, maxTop)
            } else {
                // No hay lugar arriba: se voltea abajo del dedo, acotada a
                // no salirse tampoco por ningún borde de la rueda.
                (pos.y + loupeGapPx).coerceIn(0f, maxTop)
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (clampedCenterX - halfLoupe).roundToInt(),
                            loupeTop.roundToInt()
                        )
                    }
                    .size(with(density) { loupeDiameterPx.toDp() })
                    .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(loupeColor)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

/** Traduce un punto tocado/arrastrado dentro del canvas de la rueda a (hue en grados 0-360, saturation 0-1). */
private fun hueSaturationFromOffset(offset: Offset, canvasSize: IntSize): Pair<Float, Float> {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val radius = min(canvasSize.width, canvasSize.height) / 2f
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val distance = sqrt(dx * dx + dy * dy)
    val angleDeg = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
    val saturation = if (radius > 0f) (distance / radius).coerceIn(0f, 1f) else 0f
    return angleDeg to saturation
}

/**
 * Nombre aproximado en español de un color HSV, calculado en vivo. Usa
 * los mismos 10 sectores de 36° que la rueda de referencia que mandó el
 * usuario (Amarillo, Amarillo Verdoso, Verde, Azul Verdoso, Azul,
 * Violeta, Rojo Violeta, Rojo, Rojo Naranja, Naranja), con un prefijo
 * según qué tan clara/oscura/apagada esté la muestra puntual. No es una
 * base de datos de nombres "oficiales" (Pantone, etc.) — es una
 * aproximación legible para dar contexto rápido mientras se arma un
 * color, igual que hacen Procreate o Affinity con su lector de nombre.
 */
/**
 * Modos de simulación de daltonismo para la vista previa del color/
 * degradado — NUNCA se aplica al valor real guardado, solo a cómo se
 * pinta el círculo de vista previa y el cuadrado del degradado, para que
 * el usuario pueda chequear legibilidad antes de aplicar.
 */
private enum class ColorBlindMode(val label: String) {
    NORMAL("Normal"),
    PROTANOPIA("Protanopia"),
    DEUTERANOPIA("Deuteranopia"),
    TRITANOPIA("Tritanopia")
}

/**
 * Aproximación estándar (matrices de Brettel/Viénot, las mismas que usan
 * la mayoría de los simuladores web de daltonismo) de cómo se vería
 * [color] para alguien con el tipo de daltonismo [mode]. Es una
 * aproximación de propósito general, no un diagnóstico clínico — sirve
 * para dar una idea rápida de contraste/legibilidad, no para certificar
 * accesibilidad.
 */
private fun simulateColorBlindness(color: Color, mode: ColorBlindMode): Color {
    if (mode == ColorBlindMode.NORMAL) return color
    val r = color.red; val g = color.green; val b = color.blue
    val (nr, ng, nb) = when (mode) {
        ColorBlindMode.PROTANOPIA -> Triple(
            0.567f * r + 0.433f * g + 0.000f * b,
            0.558f * r + 0.442f * g + 0.000f * b,
            0.000f * r + 0.242f * g + 0.758f * b
        )
        ColorBlindMode.DEUTERANOPIA -> Triple(
            0.625f * r + 0.375f * g + 0.000f * b,
            0.700f * r + 0.300f * g + 0.000f * b,
            0.000f * r + 0.300f * g + 0.700f * b
        )
        ColorBlindMode.TRITANOPIA -> Triple(
            0.950f * r + 0.050f * g + 0.000f * b,
            0.000f * r + 0.433f * g + 0.567f * b,
            0.000f * r + 0.475f * g + 0.525f * b
        )
        ColorBlindMode.NORMAL -> Triple(r, g, b)
    }
    return Color(nr.coerceIn(0f, 1f), ng.coerceIn(0f, 1f), nb.coerceIn(0f, 1f), color.alpha)
}

private fun colorDisplayName(hue: Float, saturation: Float, brightness: Float): String {
    if (brightness < 0.06f) return "Negro"
    if (saturation < 0.08f) {
        return when {
            brightness > 0.92f -> "Blanco"
            brightness > 0.65f -> "Gris claro"
            brightness > 0.35f -> "Gris"
            else -> "Gris oscuro"
        }
    }
    val sectorNames = listOf(
        "Rojo", "Rojo Naranja", "Naranja", "Amarillo", "Amarillo Verdoso",
        "Verde", "Azul Verdoso", "Azul", "Violeta", "Rojo Violeta"
    )
    // +18 centra el sector 0 (Rojo) en hue=0 en vez de arrancar ahí — así
    // 350°-10° caen bien en "Rojo" en vez de partirse entre el primer y
    // el último sector.
    val sectorIndex = (((hue + 18f) % 360f) / 36f).toInt().coerceIn(0, 9)
    val base = sectorNames[sectorIndex]
    val prefix = when {
        brightness < 0.35f -> "Oscuro "
        saturation < 0.35f -> "Pálido "
        brightness > 0.9f && saturation > 0.75f -> "Brillante "
        else -> ""
    }
    return prefix + base
}

/**
 * Armonías de color estándar (como Adobe Color / Coolors) a partir del
 * matiz actual — cada una devuelve el nombre de la relación + el matiz
 * resultante (0-360°); la saturación/brillo/opacidad los toma el llamador
 * del color que se está editando ahora mismo, para que la armonía se vea
 * "de la misma familia visual" en vez de siempre a saturación plena.
 */
private fun harmonyHues(baseHue: Float): List<Pair<String, Float>> {
    fun wrap(h: Float) = ((h % 360f) + 360f) % 360f
    return listOf(
        "Complementario" to wrap(baseHue + 180f),
        "Análogo" to wrap(baseHue + 30f),
        "Análogo" to wrap(baseHue - 30f),
        "Tríada" to wrap(baseHue + 120f),
        "Tríada" to wrap(baseHue + 240f)
    )
}
