package com.yeivikas.olyzecs.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.audio.fx.ReverbEffect
import com.yeivikas.olyzecs.engine.audio.fx.ReverbParams
import com.yeivikas.olyzecs.engine.audio.fx.ReverbPresetId
import com.yeivikas.olyzecs.engine.audio.fx.ReverbPresets
import com.yeivikas.olyzecs.engine.audio.reverb
import com.yeivikas.olyzecs.engine.audio.track

// ============================================================================
// MÓDULO "REVERB" — ventana flotante de un carril de audio. Mismo marco ([FloatingToolWindow]) que Zoom X y los demás
// módulos: solo cambia su contenido ([ReverbModulePanel]). Esta pieza conoce el marco y el catálogo de presets, y habla con
// el editor SOLO por lambdas (ni el ViewModel ni el DSP): quien la instancia (EditorScreen.kt) las apunta a las operaciones
// de Reverb del carril seleccionado. Cada carril lleva su propio Reverb, con su propia ventana.
// ============================================================================

private val REVERB_FLOATING_WINDOW_DEFAULT_SIZE = DpSize(300.dp, 440.dp)

/**
 * Decide si se muestra la ventana del Reverb y de QUÉ carril, y la instancia. Existe aparte de `EditorScreen` a propósito:
 * el cuerpo de esa pantalla es una única lambda gigantesca y el verificador de Android (ART) rechaza la clase —
 * `VerifyError`, la app se cierra al abrir el editor — si crece demasiado. Todo bloque nuevo de ventana debe vivir en su
 * propio composable, no en línea dentro de esa lambda.
 *
 * La fuente de verdad es el proyecto (`AudioTrack.effects`): la ventana se cierra sola si el carril seleccionado deja de
 * tener Reverb (al quitarlo arrastrando su bola a "Eliminar", o al deshacer su carga). Cambiar de carril cambia [selectedTrackId].
 *
 * @param visible el usuario abrió la ventana desde el cajón (estado del editor, ligado al carril).
 * @param onParamsChange / [onPresetSelected] / [onEnabledChange] / [onDelete] reciben el id del carril: así el editor las
 *   apunta directo a las operaciones del ViewModel sin lambdas intermedias.
 */
@Composable
internal fun ReverbWindowHost(
    visible: Boolean,
    audioTracks: List<AudioTrack>,
    selectedTrackId: String?,
    onParamsChange: (trackId: String, ReverbParams) -> Unit,
    onPresetSelected: (trackId: String, ReverbParams) -> Unit,
    onEnabledChange: (trackId: String, Boolean) -> Unit,
    onDelete: (trackId: String) -> Unit,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit,
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val effect = selectedTrackId?.let { audioTracks.track(it)?.reverb }
    if (visible && selectedTrackId != null && effect != null) {
        ReverbFloatingWindow(
            trackId = selectedTrackId,
            effect = effect,
            onParamsChange = { onParamsChange(selectedTrackId, it) },
            onPresetSelected = { onPresetSelected(selectedTrackId, it) },
            onEnabledChange = { onEnabledChange(selectedTrackId, it) },
            onDelete = { onDelete(selectedTrackId) },
            initialOffset = initialOffset,
            onClose = onClose,
            onInteracted = onInteracted,
            minimizedRegistry = minimizedRegistry,
            containerSizePx = containerSizePx,
            modifier = modifier
        )
    }
}

/**
 * Ventana flotante del Reverb del carril [trackId].
 *
 * @param effect el Reverb actual del carril (la fuente de verdad es el proyecto; la ventana no guarda copia).
 * @param onParamsChange cambio de un parámetro (un gesto de slider = un paso de deshacer).
 * @param onPresetSelected se eligió un preset: llegan sus parámetros completos.
 * @param onDelete arrastrar la bola minimizada a "Eliminar": quita el Reverb del carril.
 */
@Composable
internal fun ReverbFloatingWindow(
    trackId: String,
    effect: ReverbEffect,
    onParamsChange: (ReverbParams) -> Unit,
    onPresetSelected: (ReverbParams) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val presetNames = ReverbPresets.all.map { stringResource(reverbPresetNameRes(it.id)) }

    FloatingToolWindow(
        title = stringResource(R.string.reverb_title),
        // Un Reverb por carril: la identidad ante el registro de minimizadas incluye el carril para no confundir dos ventanas.
        windowId = "reverb:$trackId",
        titleIcon = { tint -> ReverbModuleIcon(tint = tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = REVERB_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        onInteracted = onInteracted,
        onDeleteEffect = onDelete,
        presets = presetNames,
        // `null` = los valores ya no coinciden con ningún preset ("a medida"): la cabecera muestra "Presets".
        selectedPresetIndex = ReverbPresets.indexOf(effect.params),
        onPresetSelected = { index -> onPresetSelected(ReverbPresets.all[index].params) },
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
        ReverbModulePanel(
            params = effect.params,
            enabled = effect.enabled,
            onParamsChange = onParamsChange,
            onEnabledChange = onEnabledChange,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

/** Nombre traducido de cada preset; `when` exhaustivo: agregar un preset sin su texto no compila. */
@StringRes
internal fun reverbPresetNameRes(id: ReverbPresetId): Int = when (id) {
    ReverbPresetId.ROOM -> R.string.reverb_preset_room
    ReverbPresetId.CHAMBER -> R.string.reverb_preset_chamber
    ReverbPresetId.PLATE -> R.string.reverb_preset_plate
    ReverbPresetId.HALL -> R.string.reverb_preset_hall
    ReverbPresetId.CATHEDRAL -> R.string.reverb_preset_cathedral
    ReverbPresetId.VOCAL -> R.string.reverb_preset_vocal
}
