package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.R

/**
 * Cabecera de la ventana ampliada del audio ([AudioClipDetailPanel]). Ocupa el lugar de la regla y la fila Master (la
 * ventana las tapa) y reúne TODAS las acciones del clip:
 *  - a la izquierda, **reemplazar el audio** del clip por otro archivo;
 *  - a la derecha, **Seleccionar** (cuántos rangos se pueden marcar sobre la onda, x1 … x7, [DetailSelectButton]),
 *    **Recorte** (deja el clip solo con los rangos seleccionados: uno o varios), **Reversa**, **Repetir** (bucle de vista previa) y
 *    **cerrar**.
 *
 * Cada acción es un botón vertical, como en los editores de audio móviles: el ícono arriba y su NOMBRE al pie
 * ([DetailTileFrame]). Los nombres y las descripciones de accesibilidad salen de recursos (`strings.xml`), nunca
 * de texto fijo, para que sigan el idioma de la app sin mezclar idiomas.
 *
 * Los botones comparten la misma regla visual: SOLO el ícono lleva color (el de identidad de la capa,
 * [accent]; la excepción es Recorte, cuyo ícono premium es multicolor y se dibuja tal cual); fondo y borde son neutros ([DetailNeutralFill], [DetailNeutralBorder]). Reversa encendida es la
 * excepción deliberada: se rellena con [accent] para que el estado se lea de un vistazo ([detailToggleColors]).
 *
 * No guarda estado: todo sale del clip vigente y cada acción llama a las mismas funciones del ViewModel que ya usa el
 * menú del clip ([AudioClipEditActions]), así que un cambio hecho aquí y uno hecho allí muestran siempre el mismo valor.
 *
 * @param reversed estado de Reversa del clip; @param loopEnabled estado del bucle de vista previa; @param canTrim hay al menos
 * un rango seleccionado que alcanza el largo mínimo de un clip, así que Recorte tiene algo que aplicar (sin él, el botón se ve apagado y no responde);
 * @param selectCount cuántos rangos se pueden seleccionar a la vez (x1 … x7); @param onSelectCountChange el usuario cambió esa cantidad.
 */
@Composable
internal fun AudioClipDetailHeader(
    reversed: Boolean,
    loopEnabled: Boolean,
    canTrim: Boolean,
    selectCount: Int,
    accent: Color,
    onReplaceSource: () -> Unit,
    onSelectCountChange: (Int) -> Unit,
    onTrim: () -> Unit,
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
        DetailReplaceButton(
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
            DetailSelectButton(count = selectCount, onCountChange = onSelectCountChange)
            Spacer(Modifier.width(4.dp))
            DetailTrimButton(enabled = canTrim, onClick = onTrim)
            Spacer(Modifier.width(4.dp))
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
 * Reemplazar el audio del clip por otro archivo (conserva su posición y ajustes). La zona táctil ocupa todo el alto de la cabecera; el botón dibujado
 * ([DetailActionTile]) es más pequeño y queda centrado (recortado antes de `clickable` para que el ripple sea redondeado).
 */
@Composable
private fun DetailReplaceButton(accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.audio_detail_replace_desc)
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
            label = stringResource(R.string.audio_detail_replace),
            iconTint = accent,
            labelColor = DetailNeutralLabel,
            fillTop = DetailNeutralFill,
            fillBottom = DetailNeutralFill,
            border = DetailNeutralBorder
        )
    }
}

/**
 * Recorte: deja el clip solo con los rangos seleccionados sobre la onda (uno: el clip se acorta; varios: un clip por rango).
 * Sin ningún rango seleccionado no hay nada que recortar: el botón se ve atenuado y no responde (`enabled = false` también lo marca como deshabilitado para los lectores de pantalla).
 * Su ícono premium es multicolor, por eso no lleva tinte ([DetailActionTile] con `iconTint = null`).
 */
@Composable
private fun DetailTrimButton(enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.audio_detail_trim_desc)
    Box(
        modifier = Modifier
            .height(DETAIL_HEADER_HEIGHT)
            .alpha(if (enabled) 1f else DETAIL_DISABLED_ALPHA)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        DetailActionTile(
            iconRes = R.drawable.ic_trim_premium,
            label = stringResource(R.string.audio_detail_trim),
            iconTint = null,
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

/** Alto de la cabecera: delgada, como la de un editor profesional (la onda gana el resto). NO crece por los botones: ellos se adaptan a este alto (ver `AudioClipDetailTile.kt`). */
internal val DETAIL_HEADER_HEIGHT = 40.dp
private const val DETAIL_CLOSE_TOUCH_DP = 36
