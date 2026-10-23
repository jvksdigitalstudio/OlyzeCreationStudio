package com.yeivikas.olyzecs.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import com.yeivikas.olyzecs.engine.audio.fx.ReverbRanges
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import kotlin.math.roundToInt

/** Pasa de Hz a texto legible: "850 Hz", "1.2 kHz", "14 kHz" (el separador decimal sigue el idioma del dispositivo). */
internal fun formatReverbHz(hz: Float): String {
    if (hz < 1_000f) return "${hz.roundToInt()} Hz"
    val kilohertz = (hz / 100f).roundToInt() / 10f
    return if (kilohertz == kilohertz.roundToInt().toFloat()) "${kilohertz.roundToInt()} kHz" else "%.1f kHz".format(kilohertz)
}

/** Fracción 0..1 como porcentaje: 0.35 → "35%". */
internal fun formatReverbPercent(fraction: Float): String = "${(fraction * 100f).roundToInt()}%"

/**
 * Contenido del módulo "Reverb": interruptor de activación y los controles de [ReverbParams] agrupados por lo que hacen
 * (sala, tono, textura). Es lo que [ReverbFloatingWindow] usa como `content` de [FloatingToolWindow]; solo dibuja y reenvía
 * cambios — no conoce ni el ViewModel ni el DSP.
 *
 * @param onParamsChange recibe los parámetros COMPLETOS con el valor tocado ya cambiado.
 */
@Composable
internal fun ReverbModulePanel(
    params: ReverbParams,
    enabled: Boolean,
    onParamsChange: (ReverbParams) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // SIN `verticalScroll` propio, a propósito: [FloatingToolWindow] ya envuelve su `content` en un `Column` con
    // `verticalScroll` (altura NO acotada para sus hijos). Un segundo scroll vertical aquí recibe `maxHeight = Infinity`
    // y Compose lanza IllegalStateException al medirlo ("Vertically scrollable component was measured with an infinity
    // maximum height constraints") — la app se cerraba al tocar el módulo Reverb. El scroll vive en la ventana, igual que
    // en Zoom X, Recolor, Color Básico, 3D Básico, Sombra, Reflejo y Distorsión.
    Column(modifier = modifier.fillMaxWidth()) {
        ReverbActiveToggle(enabled = enabled, onEnabledChange = onEnabledChange)

        ReverbSlider(R.string.reverb_mix, params.mix, ReverbRanges.MIX, ::formatReverbPercent, displayScale = 100f) {
            onParamsChange(params.copy(mix = it))
        }

        ReverbGroupTitle(R.string.reverb_group_room)
        ReverbSlider(R.string.reverb_decay, params.decaySeconds, ReverbRanges.DECAY_SECONDS, { "%.1f s".format(it) }) {
            onParamsChange(params.copy(decaySeconds = it))
        }
        ReverbSlider(R.string.reverb_pre_delay, params.preDelayMs, ReverbRanges.PRE_DELAY_MS, { "${it.roundToInt()} ms" }) {
            onParamsChange(params.copy(preDelayMs = it))
        }
        ReverbSlider(R.string.reverb_size, params.size, ReverbRanges.SIZE, ::formatReverbPercent, displayScale = 100f) {
            onParamsChange(params.copy(size = it))
        }

        ReverbGroupTitle(R.string.reverb_group_tone)
        ReverbSlider(R.string.reverb_damping, params.dampingHz, ReverbRanges.DAMPING_HZ, ::formatReverbHz) {
            onParamsChange(params.copy(dampingHz = it))
        }
        ReverbSlider(R.string.reverb_low_cut, params.lowCutHz, ReverbRanges.LOW_CUT_HZ, ::formatReverbHz) {
            onParamsChange(params.copy(lowCutHz = it))
        }
        ReverbSlider(R.string.reverb_high_cut, params.highCutHz, ReverbRanges.HIGH_CUT_HZ, ::formatReverbHz) {
            onParamsChange(params.copy(highCutHz = it))
        }

        ReverbGroupTitle(R.string.reverb_group_texture)
        ReverbSlider(R.string.reverb_diffusion, params.diffusion, ReverbRanges.DIFFUSION, ::formatReverbPercent, displayScale = 100f) {
            onParamsChange(params.copy(diffusion = it))
        }
        ReverbSlider(R.string.reverb_modulation, params.modulation, ReverbRanges.MODULATION, ::formatReverbPercent, displayScale = 100f) {
            onParamsChange(params.copy(modulation = it))
        }
        ReverbSlider(R.string.reverb_early, params.earlyLevel, ReverbRanges.EARLY_LEVEL, ::formatReverbPercent, displayScale = 100f) {
            onParamsChange(params.copy(earlyLevel = it))
        }
        ReverbSlider(R.string.reverb_width, params.width, ReverbRanges.WIDTH, ::formatReverbPercent, displayScale = 100f) {
            onParamsChange(params.copy(width = it))
        }
    }
}

/** Un control del Reverb: el [LabeledSlider] común de la app con su rótulo traducido. */
@Composable
private fun ReverbSlider(
    @StringRes label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    displayScale: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    LabeledSlider(
        label = stringResource(label),
        value = value,
        range = range,
        valueLabel = valueLabel,
        displayScale = displayScale,
        onValueChange = onValueChange
    )
}

@Composable
private fun ReverbGroupTitle(@StringRes title: Int) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = stringResource(title),
        color = Color.White.copy(alpha = 0.55f),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

/** Bypass del efecto: el mismo interruptor reducido que usa el resto de módulos ([ZoomXGradualFadeToggle]). */
@Composable
private fun ReverbActiveToggle(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.reverb_active),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = stringResource(R.string.reverb_active_hint),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier
                .height(20.dp)
                .scale(0.7f),
            colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
        )
    }
}
