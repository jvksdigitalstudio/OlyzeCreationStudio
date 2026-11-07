package com.yeivikas.olyzecs.ui

import java.util.Locale

/**
 * Texto de un número para el campo de edición manual de los deslizadores (`LabeledSlider`): hasta 4 decimales, sin ceros ni separador
 * sobrantes (`83`, `0.5`, `-12.25`).
 *
 * SIEMPRE con punto decimal y dígitos ASCII, sea cual sea el idioma del dispositivo. El campo ya trabaja así: al teclear, el filtro de
 * entrada convierte la coma en punto, y al confirmar se interpreta con punto. Antes se formateaba con el idioma del dispositivo
 * (`"%.4f".format(v)`), y con coma decimal (es, pt, de, fr…) `83` salía como `83,0000`, y recortar los ceros y el punto dejaba una coma
 * colgando: el campo mostraba `83,` en vez de `83`. En árabe o hindi, además, `%f` escribe dígitos no ASCII que no se pueden leer de vuelta.
 */
internal fun formatEditableNumber(value: Float): String {
    val text = String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    // Un negativo tan pequeño que se redondea a cero ("-0.00001") quedaba como "-0".
    return if (text == "-0") "0" else text
}
