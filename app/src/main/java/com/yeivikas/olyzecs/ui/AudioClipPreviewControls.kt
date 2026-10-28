package com.yeivikas.olyzecs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.yeivikas.olyzecs.engine.audio.PreviewSourceSelection
import com.yeivikas.olyzecs.viewmodel.EditorViewModel

/**
 * Lo que la ventana ampliada del audio necesita para la vista previa en bucle de su clip, empaquetado en un
 * solo valor: así `TimelineView` (una de las funciones tan grandes que cada parámetro o variable nueva la acerca
 * al límite de registros del verificador de Android) recibe UN parámetro y no cuatro. Es público porque
 * `TimelineView` lo es y no puede exponer un tipo `internal` en su firma (igual que `AudioClipEditActions`).
 *
 * @property loopEnabled estado actual del bucle (lo que muestra el botón de la cabecera).
 * @property onEnter se llama al ABRIR la ventana del clip (en el mismo instante del doble toque, antes de que la
 *   ventana se componga: así el botón y el cabezal nacen ya en su estado final y no parpadean): coloca el cursor
 *   al inicio del clip y activa el bucle.
 * @property onLoopChange el usuario activa o desactiva el bucle.
 * @property onSelectionChange el usuario terminó de cambiar los rangos seleccionados del archivo (lista vacía =
 *   deseleccionó; ordenados y sin solaparse): con el bucle activo, la vista previa suena y se repite solo en esos
 *   tramos, en orden.
 * @property onExit la ventana se cerró (o el clip desapareció): termina la sesión.
 */
@Immutable
class AudioClipPreviewControls(
    val loopEnabled: Boolean,
    val onEnter: (clipId: String) -> Unit,
    val onLoopChange: (enabled: Boolean) -> Unit,
    val onSelectionChange: (selections: List<PreviewSourceSelection>) -> Unit,
    val onExit: () -> Unit
) {
    companion object {
        /** Sin vista previa conectada (previews de Compose, usos que no la necesitan): todo es inerte. */
        val None = AudioClipPreviewControls(
            loopEnabled = false, onEnter = {}, onLoopChange = {}, onSelectionChange = {}, onExit = {}
        )
    }
}

/**
 * Controles de la vista previa conectados al [viewModel]. Se recuerdan por (`viewModel`, [loopEnabled]): las
 * lambdas llaman a métodos del ViewModel y no capturan nada más, así el objeto solo cambia cuando cambia el
 * botón y no provoca recomposiciones de la ventana en cada tick de reproducción.
 */
@Composable
internal fun rememberAudioClipPreviewControls(viewModel: EditorViewModel, loopEnabled: Boolean): AudioClipPreviewControls =
    remember(viewModel, loopEnabled) {
        AudioClipPreviewControls(
            loopEnabled = loopEnabled,
            onEnter = viewModel::enterAudioClipPreview,
            onLoopChange = viewModel::setAudioClipPreviewLoop,
            onSelectionChange = viewModel::setAudioClipPreviewSelections,
            onExit = viewModel::exitAudioClipPreview
        )
    }
