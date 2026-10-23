package com.yeivikas.olyzecs.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.yeivikas.olyzecs.ui.theme.RowIconInk

/**
 * Inversión de tonos para los íconos con colores propios ([com.yeivikas.olyzecs.ui.theme.ActionTone.Original])
 * sobre fondos CLAROS: esos dibujos son blancos con detalles casi negros, y sobre un cian o un amarillo
 * el blanco se pierde. Por canal, `salida = blanco + (tinte oscuro − blanco) · entrada`: blanco → [RowIconInk],
 * negro → blanco; la opacidad no cambia. Un dibujo de dos tonos (ojo tachado) queda igual de legible con
 * los papeles invertidos. La traslación de [ColorMatrix] va en escala 0–255.
 */
internal fun inkToneColorFilter(): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            RowIconInk.red - 1f, 0f, 0f, 0f, 255f,
            0f, RowIconInk.green - 1f, 0f, 0f, 255f,
            0f, 0f, RowIconInk.blue - 1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)
