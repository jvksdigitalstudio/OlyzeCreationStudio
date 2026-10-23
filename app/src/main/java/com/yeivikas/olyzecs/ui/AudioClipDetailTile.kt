package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Marco visual COMPARTIDO de los botones verticales de la cabecera de la ventana ampliada del audio
 * ([AudioClipDetailHeader]): contenido arriba y nombre al pie. Vive aparte para que la cabecera, que solo coloca los
 * botones, y cada botón con comportamiento propio (p. ej. [DetailSelectButton]) dependan de UN solo dibujo, sin copiarlo.
 */

/** Fondo y borde neutros de los botones de la ventana: no dependen del color de la capa. */
internal val DetailNeutralFill = Color.White.copy(alpha = 0.06f)
internal val DetailNeutralBorder = Color.White.copy(alpha = 0.18f)

/** Color del nombre de un botón apagado o neutro. */
internal val DetailNeutralLabel = Color.White.copy(alpha = 0.82f)

/** Opacidad de un botón (o parte de él) sin nada que aplicar: Recorte sin selección, una flecha de Seleccionar en su límite. */
internal const val DETAIL_DISABLED_ALPHA = 0.4f

/**
 * Medidas de los botones verticales (contenido arriba, nombre al pie), SIEMPRE derivadas del alto de la cabecera
 * ([DETAIL_HEADER_HEIGHT]): el botón dibujado deja [DETAIL_TILE_VERTICAL_MARGIN] arriba y abajo, y su contenido cabe en
 * lo que queda. Todo botón actual o futuro usa [DetailTileFrame] y hereda estas medidas, así que ninguno obliga a
 * agrandar la cabecera. Su zona táctil sigue siendo todo el alto de la cabecera.
 */
private val DETAIL_TILE_VERTICAL_MARGIN = 4.dp
private val DETAIL_TILE_HEIGHT = DETAIL_HEADER_HEIGHT - DETAIL_TILE_VERTICAL_MARGIN * 2
private val DETAIL_TILE_MIN_WIDTH = 48.dp
private val DETAIL_TILE_HORIZONTAL_PADDING = 6.dp
private val DETAIL_TILE_ICON_SIZE = 16.dp
private val DETAIL_TILE_LABEL_SIZE = 9.sp
private val DETAIL_TILE_LABEL_LINE_HEIGHT = 10.sp

/**
 * Marco de un botón vertical: el [top] arriba y el nombre ([label]) al pie, todo centrado. Es SOLO dibujo: el toque, el
 * estado y la accesibilidad los pone quien lo envuelve (su zona táctil es todo el alto de la cabecera). El nombre va en
 * una línea y sin partir palabras; el ancho mínimo ([DETAIL_TILE_MIN_WIDTH]) iguala los botones y crece con el
 * contenido si hace falta (p. ej. un idioma con el nombre más largo).
 */
@Composable
internal fun DetailTileFrame(
    label: String,
    labelColor: Color,
    fillTop: Color,
    fillBottom: Color,
    border: Color,
    top: @Composable () -> Unit
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
        top()
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

/**
 * Botón vertical con un ÍCONO arriba ([DetailTileFrame] + el ícono). Con `iconTint = null` el ícono se dibuja con sus
 * propios colores (p. ej. el de Recorte): un tinte lo aplanaría a un solo color.
 */
@Composable
internal fun DetailActionTile(
    iconRes: Int,
    label: String,
    iconTint: Color?,
    labelColor: Color,
    fillTop: Color,
    fillBottom: Color,
    border: Color
) {
    DetailTileFrame(
        label = label,
        labelColor = labelColor,
        fillTop = fillTop,
        fillBottom = fillBottom,
        border = border
    ) {
        if (iconTint != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(DETAIL_TILE_ICON_SIZE)
            )
        } else {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(DETAIL_TILE_ICON_SIZE)
            )
        }
    }
}
