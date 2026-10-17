package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.R

/**
 * Cabecera de la ventana ampliada del audio ([AudioClipDetailPanel]). Ocupa el lugar de la regla y la fila Master (la
 * ventana las tapa) y reúne TODAS las acciones del clip:
 *  - a la izquierda, **cargar o cambiar el audio** del clip;
 *  - a la derecha, **Reversa**, **Repetir** (bucle de vista previa) y **cerrar**.
 *
 * Cada acción es un botón vertical, como en los editores de audio móviles: el ícono arriba y su NOMBRE al pie
 * ([DetailActionTile]). Los nombres y las descripciones de accesibilidad salen de recursos (`strings.xml`), nunca
 * de texto fijo, para que sigan el idioma de la app sin mezclar idiomas.
 *
 * Todos los botones comparten la misma regla visual: SOLO el ícono lleva color (el de identidad de la capa,
 * [accent]); fondo y borde son neutros ([DetailNeutralFill], [DetailNeutralBorder]). Reversa encendida es la
 * excepción deliberada: se rellena con [accent] para que el estado se lea de un vistazo ([detailToggleColors]).
 *
 * No guarda estado: todo sale del clip vigente y cada acción llama a las mismas funciones del ViewModel que ya usa el
 * menú del clip ([AudioClipEditActions]), así que un cambio hecho aquí y uno hecho allí muestran siempre el mismo valor.
 *
 * @param reversed estado de Reversa del clip; @param loopEnabled estado del bucle de vista previa.
 */
@Composable
internal fun AudioClipDetailHeader(
    reversed: Boolean,
    loopEnabled: Boolean,
    accent: Color,
    onReplaceSource: () -> Unit,
    onToggleReverse: () -> Unit,
    onLoopChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DETAIL_HEADER_HEIGHT)
            .background(DetailHeaderBackground)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
        DetailLoadButton(
            accent = accent,
            onClick = onReplaceSource,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailReverseButton(checked = reversed, accent = accent, onToggle = onToggleReverse)
            Spacer(Modifier.width(4.dp))
            DetailLoopButton(enabled = loopEnabled, accent = accent, onChange = onLoopChange)
            Spacer(Modifier.width(4.dp))
            DetailCloseButton(onClose = onClose)
        }
    }
}

/**
 * Botón de cerrar: el icono premium (círculo rojo con X calada) sobre un disco blanco, porque la X es un hueco del
 * vector.
 */
@Composable
private fun DetailCloseButton(onClose: () -> Unit) {
    val closeLabel = stringResource(R.string.audio_detail_close_desc)
    Box(
        modifier = Modifier
            .size(DETAIL_CLOSE_TOUCH_DP.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
        Image(
            painter = painterResource(id = R.drawable.ic_close_premium),
            contentDescription = null,
            modifier = Modifier.size(27.dp)
        )
    }
}

/**
 * Cargar o cambiar el audio del clip. La zona táctil ocupa todo el alto de la cabecera; el botón dibujado
 * ([DetailActionTile]) es más pequeño y queda centrado (recortado antes de `clickable` para que el ripple sea redondeado).
 */
@Composable
private fun DetailLoadButton(accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.audio_detail_load_desc)
    Box(
        modifier = modifier
            .height(DETAIL_HEADER_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        DetailActionTile(
            iconRes = R.drawable.ic_load_audio_premium,
            label = stringResource(R.string.audio_detail_load),
            iconTint = accent,
            labelColor = DetailNeutralLabel,
            fillTop = DetailNeutralFill,
            fillBottom = DetailNeutralFill,
            border = DetailNeutralBorder
        )
    }
}

/**
 * Reversa del clip: apagado = contorno neutro con el ícono en el color de la capa; encendido = relleno con ese color
 * ([detailToggleColors]). Interruptor accesible (`Role.Switch`): los lectores de pantalla anuncian "activado/desactivado".
 */
@Composable
private fun DetailReverseButton(checked: Boolean, accent: Color, onToggle: () -> Unit) {
    val colors = detailToggleColors(checked, accent)
    val description = stringResource(R.string.audio_detail_reverse_desc)
    Box(
        modifier = Modifier
            .height(DETAIL_HEADER_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        DetailActionTile(
            iconRes = R.drawable.ic_reverse_audio,
            label = stringResource(R.string.audio_detail_reverse),
            iconTint = colors.iconTint,
            labelColor = colors.labelColor,
            fillTop = colors.fillTop,
            fillBottom = colors.fillBottom,
            border = colors.border
        )
    }
}

/**
 * Botón de Repetir (bucle de la vista previa del clip): SOLO el ícono lleva color — el de la capa ([accent]) al estar
 * activado, blanco al estar apagado — y el contenedor y el nombre son neutros en ambos estados. La ventana lo abre ya
 * ACTIVADO. Interruptor accesible (`Role.Switch`).
 */
@Composable
private fun DetailLoopButton(enabled: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    val description = stringResource(R.string.audio_detail_repeat_desc)
    Box(
        modifier = Modifier
            .height(DETAIL_HEADER_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        DetailActionTile(
            iconRes = R.drawable.ic_loop_preview,
            label = stringResource(R.string.audio_detail_repeat),
            iconTint = if (enabled) accent else Color.White,
            labelColor = DetailNeutralLabel,
            fillTop = DetailNeutralFill,
            fillBottom = DetailNeutralFill,
            border = DetailNeutralBorder
        )
    }
}

/**
 * Botón vertical de la cabecera: el ícono arriba y su nombre al pie, todo centrado. Es SOLO dibujo: el toque, el
 * estado y la accesibilidad los pone quien lo envuelve (su zona táctil es todo el alto de la cabecera). El nombre va
 * en una línea y sin partir palabras; el ancho mínimo ([DETAIL_TILE_MIN_WIDTH]) iguala los botones y crece con
 * el texto si un idioma lo pide más largo.
 */
@Composable
private fun DetailActionTile(
    iconRes: Int,
    label: String,
    iconTint: Color,
    labelColor: Color,
    fillTop: Color,
    fillBottom: Color,
    border: Color
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .height(DETAIL_TILE_HEIGHT)
            .widthIn(min = DETAIL_TILE_MIN_WIDTH)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(fillTop, fillBottom)))
            .border(BorderStroke(1.2.dp, border), shape)
            .padding(horizontal = DETAIL_TILE_HORIZONTAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(DETAIL_TILE_ICON_SIZE)
        )
        Text(
            text = label,
            color = labelColor,
            fontSize = DETAIL_TILE_LABEL_SIZE,
            lineHeight = DETAIL_TILE_LABEL_LINE_HEIGHT,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Colores de un botón de dos estados ([detailToggleColors]). */
internal data class DetailToggleColors(
    val fillTop: Color,
    val fillBottom: Color,
    val border: Color,
    val iconTint: Color,
    val labelColor: Color
)

/**
 * Colores del botón de dos estados (Reversa) para el [accent] de la capa; función pura.
 *  - **apagado**: ícono en `accent`; etiqueta, fondo y borde neutros (blanco tenue);
 *  - **encendido**: relleno en degradado de `accent`, borde `accent` y ícono + etiqueta oscuros ([DetailOnAccent]).
 */
internal fun detailToggleColors(checked: Boolean, accent: Color): DetailToggleColors =
    if (checked) {
        DetailToggleColors(
            fillTop = accent,
            fillBottom = accent.copy(alpha = 0.72f),
            border = accent,
            iconTint = DetailOnAccent,
            labelColor = DetailOnAccent
        )
    } else {
        DetailToggleColors(
            fillTop = DetailNeutralFill,
            fillBottom = DetailNeutralFill,
            border = DetailNeutralBorder,
            iconTint = accent,
            labelColor = DetailNeutralLabel
        )
    }

private val DetailHeaderBackground = Color(0xFF17142A)
private val DetailOnAccent = Color(0xFF101014)

/** Fondo y borde neutros de los botones de la ventana: no dependen del color de la capa. */
private val DetailNeutralFill = Color.White.copy(alpha = 0.06f)
private val DetailNeutralBorder = Color.White.copy(alpha = 0.18f)

/** Color del nombre de un botón apagado o neutro. */
private val DetailNeutralLabel = Color.White.copy(alpha = 0.82f)

/** Alto de la cabecera: delgada, como la de un editor profesional (la onda gana el resto). NO crece por los botones: ellos se adaptan a este alto. */
private val DETAIL_HEADER_HEIGHT = 40.dp
private const val DETAIL_CLOSE_TOUCH_DP = 36

/**
 * Medidas de los botones verticales (ícono arriba, nombre al pie), SIEMPRE derivadas del alto de la cabecera: el botón
 * dibujado deja [DETAIL_TILE_VERTICAL_MARGIN] arriba y abajo, y su contenido (ícono + nombre) cabe en lo que queda.
 * Todo botón actual o futuro usa [DetailActionTile] y hereda estas medidas, así que ninguno obliga a agrandar la
 * cabecera. Su zona táctil sigue siendo todo el alto de la cabecera.
 */
private val DETAIL_TILE_VERTICAL_MARGIN = 4.dp
private val DETAIL_TILE_HEIGHT = DETAIL_HEADER_HEIGHT - DETAIL_TILE_VERTICAL_MARGIN * 2
private val DETAIL_TILE_MIN_WIDTH = 48.dp
private val DETAIL_TILE_HORIZONTAL_PADDING = 6.dp
private val DETAIL_TILE_ICON_SIZE = 16.dp
private val DETAIL_TILE_LABEL_SIZE = 9.sp
private val DETAIL_TILE_LABEL_LINE_HEIGHT = 10.sp
