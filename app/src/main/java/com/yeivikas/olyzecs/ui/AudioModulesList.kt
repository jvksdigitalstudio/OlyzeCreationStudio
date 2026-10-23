package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.R

/**
 * Lista de módulos del cajón "Módulos" cuando hay un carril de audio seleccionado ([ModulesTarget.AUDIO]).
 *
 * Es el ÚNICO punto de registro de los módulos de audio: agrupados por categoría con [ModulesCategoryHeader] y una
 * [ModuleRow] por módulo (el mismo patrón que [LayerModulesList]), cada uno con su lógica en archivos propios — este archivo
 * solo REGISTRA la fila y reenvía el toque. Cada módulo se carga en el carril seleccionado y es opcional: cada carril lleva
 * los suyos y ninguno hereda los de otro. Los módulos de imagen/cámara ([LayerModulesList]: Sombra, Reflejo, Animación,
 * Zoom X) no aplican sobre audio y siguen apareciendo al seleccionar una capa de imagen (o ninguna).
 *
 * Hoy: categoría "Espacio" → Reverb ([ReverbFloatingWindow]).
 */
@Composable
internal fun ColumnScope.AudioModulesList(onReverbClick: () -> Unit) {
    ModulesCategoryHeader(
        title = stringResource(R.string.audio_module_category_space),
        modifier = Modifier.padding(bottom = 2.dp)
    )
    ModuleRow(
        label = stringResource(R.string.reverb_title),
        icon = { ReverbModuleIcon(tint = Color.White.copy(alpha = 0.82f)) },
        showChevron = false,
        onClick = onReverbClick
    )
}
