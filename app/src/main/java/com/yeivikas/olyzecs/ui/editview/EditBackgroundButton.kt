package com.yeivikas.olyzecs.ui.editview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.data.EditBackgroundChoice
import com.yeivikas.olyzecs.data.EditBackgroundSettings
import com.yeivikas.olyzecs.engine.render.PreviewBackdrop

/** 48 dp: el tamaño táctil mínimo recomendado por Material. */
private val ButtonSize = 48.dp
private val SwatchSize = 34.dp
private val MenuSwatchSize = 24.dp
private val CycleIconSize = 16.dp
private val ButtonShape = RoundedCornerShape(10.dp)
private val SwatchShape = RoundedCornerShape(6.dp)

/**
 * Marco de la miniatura en dos tonos (blanco por fuera, negro translúcido por dentro): se distingue sobre CUALQUIER
 * fondo — verde, negro, blanco o tablero — sin depender del color que tenga la miniatura.
 */
private val SwatchOuterRing = 2.dp
private val SwatchInnerLine = 1.dp
private val SwatchInnerLineColor = Color.Black.copy(alpha = 0.55f)

/** Lado de la marca de esquina que anuncia que hay más opciones con pulsación larga. */
private val MenuHintSize = 9.dp
private val MenuHintInset = 4.dp
private val MenuHintColor = Color.White.copy(alpha = 0.9f)

/** El lado de casilla solo importa al renderer; la miniatura dibuja su propia rejilla, así que vale cualquiera válido. */
private const val SWATCH_CELL_PX = PreviewBackdrop.MIN_CELL_PX

/**
 * Botón del fondo de la vista de edición: muestra el fondo actual y lo cambia.
 *
 * - **Un toque:** pasa al siguiente de la rotación rápida (lienzo → tablero claro → negro → blanco).
 * - **Pulsación larga:** abre la lista completa, incluido el color personalizado.
 *
 * Para que se reconozca como botón con cualquier fondo, la miniatura lleva un marco de dos tonos, un ícono de
 * «cambiar» cuyo color sale de [EditBackdropContrast] y una marca en la esquina que anuncia el menú.
 *
 * Es un componente de presentación: no conoce el renderer ni dónde se guarda la preferencia; recibe el estado y
 * avisa con callbacks. Quien lo usa decide cuándo mostrarlo (solo en modo edición).
 */
@Composable
fun EditBackgroundButton(
    settings: EditBackgroundSettings,
    onCycle: () -> Unit,
    onSelect: (EditBackgroundChoice) -> Unit,
    onPickCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    // El pointerInput(Unit) de abajo se lanza una sola vez: sin rememberUpdatedState quedaría congelado con el
    // `onCycle` de la primera composición (mismo problema documentado en EditorScreen.enterEditModeForSelectedLayer).
    val currentOnCycle by rememberUpdatedState(onCycle)
    val description = "Fondo de edición: ${labelFor(settings.choice)}"

    Box(modifier) {
        Box(
            modifier = Modifier
                .size(ButtonSize)
                .clip(ButtonShape)
                .background(Color(0xCC000000))
                .border(1.dp, Color.White.copy(alpha = 0.35f), ButtonShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { currentOnCycle() },
                        onLongPress = { menuOpen = true }
                    )
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    role = Role.Button
                    onClick(label = "Cambiar fondo") { currentOnCycle(); true }
                    onLongClick(label = "Ver todos los fondos") { menuOpen = true; true }
                },
            contentAlignment = Alignment.Center
        ) {
            val backdrop = settings.toBackdrop(SWATCH_CELL_PX)
            Box(contentAlignment = Alignment.Center) {
                BackdropSwatch(
                    backdrop = backdrop,
                    modifier = Modifier
                        .size(SwatchSize)
                        .clip(SwatchShape)
                        .border(SwatchOuterRing, Color.White, SwatchShape)
                        .padding(SwatchOuterRing)
                        .border(SwatchInnerLine, SwatchInnerLineColor, SwatchShape)
                )
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color(EditBackdropContrast.iconArgbOver(backdrop)),
                    modifier = Modifier.size(CycleIconSize)
                )
            }
            MenuHintCorner(modifier = Modifier.align(Alignment.BottomEnd))
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            EditBackgroundChoice.entries.forEach { choice ->
                val isCustom = choice == EditBackgroundChoice.CUSTOM
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BackdropSwatch(
                                backdrop = settings.copy(choice = choice).toBackdrop(SWATCH_CELL_PX),
                                modifier = Modifier
                                    .size(MenuSwatchSize)
                                    .clip(SwatchShape)
                                    .border(1.dp, Color.Gray, SwatchShape)
                            )
                            Text(
                                text = labelFor(choice),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    },
                    trailingIcon = {
                        if (choice == settings.choice) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = "Seleccionado")
                        }
                    },
                    onClick = {
                        menuOpen = false
                        if (isCustom) onPickCustom() else onSelect(choice)
                    }
                )
            }
        }
    }
}

private fun labelFor(choice: EditBackgroundChoice): String = when (choice) {
    EditBackgroundChoice.CANVAS -> "Color del lienzo"
    EditBackgroundChoice.CHECKER_LIGHT -> "Tablero claro"
    EditBackgroundChoice.CHECKER_DARK -> "Tablero oscuro"
    EditBackgroundChoice.BLACK -> "Negro"
    EditBackgroundChoice.GRAY -> "Gris medio"
    EditBackgroundChoice.WHITE -> "Blanco"
    EditBackgroundChoice.CUSTOM -> "Personalizado…"
}

/**
 * Pequeño triángulo en la esquina inferior derecha: la convención de Android/Material para «aquí hay más opciones
 * con pulsación larga». Se dibuja en blanco porque el botón siempre tiene un fondo oscuro detrás.
 */
@Composable
private fun MenuHintCorner(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.padding(MenuHintInset).size(MenuHintSize)) {
        val corner = Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(corner, MenuHintColor)
    }
}
