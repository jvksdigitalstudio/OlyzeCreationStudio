package com.yeivikas.olyzecs.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * El texto que se pone en el campo de edición manual de los deslizadores no puede depender del idioma del dispositivo. Con coma decimal
 * (español, portugués, alemán, francés…) el formato anterior dejaba una coma colgando: `83` salía como `83,`.
 */
class EditableNumberFormatTest {

    private lateinit var original: Locale

    @Before
    fun rememberLocale() {
        original = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    private val locales = listOf("en-US", "es-AR", "es-ES", "de-DE", "fr-FR", "pt-BR", "ar-EG", "hi-IN")

    private fun inEveryLocale(expected: String, value: Float) {
        for (tag in locales) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            assertEquals("$value en $tag", expected, formatEditableNumber(value))
        }
    }

    @Test
    fun `un entero no deja separador decimal colgando en ningun idioma`() {
        inEveryLocale("83", 83f)
        inEveryLocale("0", 0f)
        inEveryLocale("100", 100f)
        inEveryLocale("180", 180f)
    }

    @Test
    fun `los decimales usan siempre punto y digitos ASCII`() {
        inEveryLocale("0.5", 0.5f)
        inEveryLocale("0.1234", 0.1234f)
        inEveryLocale("-12.5", -12.5f)
        inEveryLocale("3.1416", 3.14159f)
        inEveryLocale("0.3", 0.3f)
    }

    @Test
    fun `un valor que se redondea a cero se escribe 0 y nunca -0`() {
        inEveryLocale("0", 0.00004f)
        inEveryLocale("0", -0.00001f)
        inEveryLocale("0", -0f)
    }

    @Test
    fun `lo que se escribe se puede leer de vuelta tal cual`() {
        // El campo interpreta lo tecleado con punto (la coma se normaliza a punto): el texto generado debe sobrevivir ese viaje.
        for (tag in locales) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            for (value in listOf(0f, 83f, 100f, 0.5f, -12.5f, 3.1416f)) {
                assertEquals("$value en $tag", value, formatEditableNumber(value).toFloat(), 0f)
            }
        }
    }
}
