package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.engine.camera.ZoomXLevels
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// MÓDULO "ZOOM X" — ARCHIVO PROPIO Y AUTOCONTENIDO, A PEDIDO EXPLÍCITO DEL
// USUARIO ("que no combine funciones, solo para este módulo... para que sea
// mantenible a futuro, editable y actualizable sin tener que rehacer todo").
//
// Todo lo que este archivo necesita del resto de la app es:
//   - [ZoomXLevels] (engine/camera) — la secuencia de paradas, pura, sin UI.
//   - [BrandPurpleLight] (ui/theme) — el color de acento de marca, para que
//     el indicador de la rueda combine con el resto de la app (el switch de
//     abajo, el cursor del buscador de Módulos, etc.) en vez de un color
//     inventado que desentone.
//
// Nada de acá adentro conoce EditorViewModel, Layer, ni ninguna otra pieza
// del motor — todo controlado desde afuera vía parámetros (mismo criterio
// que JoystickLayerMover: la parte visual reporta, nunca decide). Quien
// integra esto con el resto del editor es [ZoomXFloatingWindow.kt] — otro
// archivo propio y separado, la ÚNICA pieza que sí conoce EditorViewModel/
// Layer. Esta separación en tres capas (motor puro → presentación pura →
// integración) es la misma que ya usan Joystick.kt/JoystickLayerMover.kt/
// SpeedRampEngine — no una convención nueva inventada para este módulo.
// ============================================================================

/** Progreso angular \[0,1] → grado real dentro del arco visible de la rueda. */
private const val ZOOM_WHEEL_START_DEG = -90f // 12 en punto girado a las 9 — arranca arriba
private const val ZOOM_WHEEL_SWEEP_DEG = 180f // barre por la derecha hasta abajo — semicírculo derecho, igual que la referencia

/** Sub-marcas puramente decorativas entre cada parada real — solo densidad visual, sin valor propio (la rueda SOLO puede posarse en una parada real, nunca en una sub-marca). */
private const val ZOOM_WHEEL_MINOR_TICKS_PER_LEVEL = 3

/**
 * Rueda de niveles de "Zoom X" — un anillo de paradas fijas (ver
 * [ZoomXLevels]), nunca un slider continuo: arrastrar el dedo por el arco
 * selecciona la parada más cercana al ÁNGULO tocado, con un pequeño golpe
 * háptico cada vez que cruza a una parada distinta — la sensación de un
 * anillo de zoom de cámara real, no un slider genérico disfrazado de rueda.
 *
 * @param levels la secuencia de paradas a mostrar (ver [ZoomXLevels.levels]
 *   — normalmente el resultado de esa función tal cual, pasado por quien
 *   integra la rueda, nunca calculado acá adentro).
 * @param selectedIndex índice (dentro de [levels]) de la parada activa.
 * @param onLevelSelected se dispara SOLO cuando el arrastre cruza a una
 *   parada distinta de la actual — nunca en cada píxel de movimiento, para
 *   que quien integra la rueda (ver [ZoomXFloatingWindow]) pueda commitear
 *   cada cambio de nivel sin necesidad de su propio throttle.
 */
@Composable
fun ZoomXWheel(
    levels: List<Float>,
    selectedIndex: Int,
    onLevelSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(levels.isNotEmpty()) { "ZoomXWheel necesita al menos una parada" }
    val safeSelectedIndex = selectedIndex.coerceIn(0, levels.lastIndex)
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(levels) {
                // `levels` como key: si la secuencia de paradas cambia en
                // caliente (motor configurable — ver el comentario grande
                // de [ZoomXLevels] sobre extensibilidad), este gesto se
                // vuelve a armar con la lista nueva en vez de seguir
                // resolviendo índices contra una lista vieja ya
                // descartada.
                var lastReportedIndex = safeSelectedIndex
                fun angleToIndex(pos: Offset): Int {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = pos.x - center.x
                    val dy = pos.y - center.y
                    // atan2(dy, dx) en grados: 0°=derecha, 90°=abajo (Y
                    // crece hacia abajo en pantalla) — mismo criterio de
                    // "ángulo matemático con Y invertida" que ya usa
                    // Joystick.kt para su propia geometría.
                    val rawDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    val clampedDeg = rawDeg.coerceIn(ZOOM_WHEEL_START_DEG, ZOOM_WHEEL_START_DEG + ZOOM_WHEEL_SWEEP_DEG)
                    val progress = (clampedDeg - ZOOM_WHEEL_START_DEG) / ZOOM_WHEEL_SWEEP_DEG
                    return Math.round(progress * (levels.size - 1)).coerceIn(0, levels.lastIndex)
                }
                detectDragGestures(
                    onDragStart = { pos ->
                        val idx = angleToIndex(pos)
                        // Mismo criterio que `onDrag`, acá abajo: solo se
                        // reporta (y solo vibra) si el toque cae en una
                        // parada DISTINTA de la ya activa — tocar
                        // directamente sobre el nivel actual no dispara un
                        // commit redundante.
                        if (idx != lastReportedIndex) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastReportedIndex = idx
                            onLevelSelected(idx)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val idx = angleToIndex(change.position)
                        if (idx != lastReportedIndex) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastReportedIndex = idx
                            onLevelSelected(idx)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = size.minDimension * 0.012f
            val outerR = size.minDimension * 0.46f
            val majorTickLen = size.minDimension * 0.07f
            val minorTickLen = size.minDimension * 0.03f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            fun pointAt(degrees: Float, radius: Float): Offset {
                val rad = degrees * (PI / 180.0)
                return Offset(
                    center.x + (radius * cos(rad)).toFloat(),
                    center.y + (radius * sin(rad)).toFloat()
                )
            }

            // Arco base — la "pista" completa por la que se puede arrastrar.
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = ZOOM_WHEEL_START_DEG,
                sweepAngle = ZOOM_WHEEL_SWEEP_DEG,
                useCenter = false,
                topLeft = Offset(center.x - outerR, center.y - outerR),
                size = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Marcas MENORES puramente decorativas entre cada parada real
            // (ver comentario grande de [ZOOM_WHEEL_MINOR_TICKS_PER_LEVEL])
            // — dan la densidad de "anillo de lente" de la referencia sin
            // que representen ningún valor propio seleccionable.
            if (levels.size > 1) {
                for (i in 0 until levels.size - 1) {
                    val fromDeg = ZOOM_WHEEL_START_DEG + ZoomXLevels.progressAt(i, levels.size) * ZOOM_WHEEL_SWEEP_DEG
                    val toDeg = ZOOM_WHEEL_START_DEG + ZoomXLevels.progressAt(i + 1, levels.size) * ZOOM_WHEEL_SWEEP_DEG
                    for (m in 1..ZOOM_WHEEL_MINOR_TICKS_PER_LEVEL) {
                        val t = m.toFloat() / (ZOOM_WHEEL_MINOR_TICKS_PER_LEVEL + 1)
                        val deg = fromDeg + (toDeg - fromDeg) * t
                        drawLine(
                            color = Color.White.copy(alpha = 0.18f),
                            start = pointAt(deg, outerR - minorTickLen),
                            end = pointAt(deg, outerR),
                            strokeWidth = strokeW * 0.7f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Marcas MAYORES — una por cada parada real de [levels]. La
            // parada activa se dibuja en el color de acento y más gruesa;
            // el resto, blanco tenue.
            levels.forEachIndexed { index, _ ->
                val deg = ZOOM_WHEEL_START_DEG + ZoomXLevels.progressAt(index, levels.size) * ZOOM_WHEEL_SWEEP_DEG
                val isActive = index == safeSelectedIndex
                drawLine(
                    color = if (isActive) BrandPurpleLight else Color.White.copy(alpha = 0.55f),
                    start = pointAt(deg, outerR - majorTickLen),
                    end = pointAt(deg, outerR),
                    strokeWidth = if (isActive) strokeW * 2.2f else strokeW,
                    cap = StrokeCap.Round
                )
            }
        }

        // Etiquetas de texto — Composables `Text` normales posicionados por
        // trigonometría en Dp reales (gracias a `BoxWithConstraints`,
        // que expone `maxWidth` — el diámetro real de la rueda, cualquiera
        // sea el tamaño que le tocó dentro de la ventana flotante
        // redimensionable — ver [ZoomXFloatingWindow]) en vez de texto
        // dibujado a mano en el Canvas de arriba: tipografía/tamaño
        // consistentes con el resto de la app, sin manejar Paint/
        // nativeCanvas a mano.
        val diameter = maxWidth
        levels.forEachIndexed { index, level ->
            val deg = ZOOM_WHEEL_START_DEG + ZoomXLevels.progressAt(index, levels.size) * ZOOM_WHEEL_SWEEP_DEG
            val isActive = index == safeSelectedIndex
            val rad = deg * (PI / 180.0)
            // labelRadiusFraction > 0.5 (el radio del arco en sí, ver
            // `outerR` arriba) para que la etiqueta quede AFUERA del anillo
            // de marcas, nunca superpuesta con los tics.
            val labelRadiusFraction = 0.365f
            val labelRadius = diameter * labelRadiusFraction
            Text(
                text = ZoomXLevels.label(level),
                color = if (isActive) BrandPurpleLight else Color.White.copy(alpha = 0.6f),
                fontSize = if (isActive) 12.sp else 10.sp,
                fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = labelRadius * cos(rad).toFloat(),
                        y = labelRadius * sin(rad).toFloat()
                    )
            )
        }
    }
}

/**
 * Fila "Transición gradual" — el switch que activa/desactiva el efecto de
 * desvanecer-y-aparecer mientras se cambia de nivel de zoom (ver el
 * comentario grande sobre `alpha` en [ZoomXFloatingWindow] para el detalle
 * de qué anima exactamente). Mismo componente y mismos colores que
 * cualquier otro switch de la app (ver, por ejemplo, "Vincular a Luz
 * global" en `EffectsCategoryShadow`, EditorScreen.kt) — nada nuevo
 * inventado para este módulo.
 */
@Composable
fun ZoomXGradualFadeToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Transición gradual",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "Desvanece y reaparece al cambiar de nivel",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            // Mismo tamaño reducido (`height` + `scale`) que cualquier
            // otro switch de la app — ver "Vincular a Luz global" en
            // EditorScreen.kt, mismo criterio exacto, nada nuevo acá.
            modifier = Modifier.height(20.dp).scale(0.7f),
            colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
        )
    }
}

/**
 * Contenido completo del módulo — rueda + valor activo + switch. Esto es
 * lo que [ZoomXFloatingWindow] usa como `content` de [FloatingToolWindow];
 * separado en su propio composable (en vez de armado inline adentro de la
 * ventana) para poder previsualizarlo o reutilizarlo suelto sin arrastrar
 * toda la maquinaria de la ventana flotante.
 */
@Composable
fun ZoomXModulePanel(
    levels: List<Float>,
    selectedIndex: Int,
    onLevelSelected: (Int) -> Unit,
    gradualFadeEnabled: Boolean,
    onGradualFadeEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIndex = remember(selectedIndex, levels) { selectedIndex.coerceIn(0, levels.lastIndex) }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZoomXWheel(
            levels = levels,
            selectedIndex = safeIndex,
            onLevelSelected = onLevelSelected,
            modifier = Modifier
                .width(180.dp)
                .padding(top = 6.dp, bottom = 4.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        ZoomXGradualFadeToggle(
            enabled = gradualFadeEnabled,
            onEnabledChange = onGradualFadeEnabledChange,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * Glifo del módulo "Zoom X" — lupa con marcas de "+"/"-" radiales, mismo
 * criterio de ícono dibujado a mano en [Canvas] que [ShadowModuleIcon]/
 * [ReflectionModuleIcon] (EditorBottomBar.kt) y [SearchGlyph] — sin agregar
 * un recurso .xml nuevo para un glifo tan simple.
 */
@Composable
fun ZoomXModuleIcon(tint: Color, iconSize: androidx.compose.ui.unit.Dp = 18.dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val strokeW = size.minDimension * 0.09f
        val lensRadius = size.minDimension * 0.32f
        val lensCenter = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color = tint, radius = lensRadius, center = lensCenter, style = Stroke(width = strokeW))
        // Mango de la lupa, mismo trazo diagonal que [SearchGlyph].
        drawLine(
            color = tint,
            start = Offset(lensCenter.x + lensRadius * 0.7071f, lensCenter.y + lensRadius * 0.7071f),
            end = Offset(size.width * 0.94f, size.height * 0.94f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        // Cruz "+" adentro del lente — lo que distingue este glifo de una
        // lupa de búsqueda genérica: es, específicamente, una lupa de ZOOM.
        val crossHalf = lensRadius * 0.5f
        drawLine(
            color = tint,
            start = Offset(lensCenter.x - crossHalf, lensCenter.y),
            end = Offset(lensCenter.x + crossHalf, lensCenter.y),
            strokeWidth = strokeW * 0.8f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(lensCenter.x, lensCenter.y - crossHalf),
            end = Offset(lensCenter.x, lensCenter.y + crossHalf),
            strokeWidth = strokeW * 0.8f,
            cap = StrokeCap.Round
        )
    }
}
