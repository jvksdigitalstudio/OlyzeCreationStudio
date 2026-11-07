package com.yeivikas.olyzecs.data

/**
 * Las opciones de fondo de la VISTA DE EDICIÓN de una capa (la pantalla con Color / 3D / Efecto).
 *
 * Es el vocabulario de una PREFERENCIA DEL USUARIO, no del proyecto: no se guarda en `project.json`, no entra en
 * el deshacer y no afecta al lienzo ni a la exportación. Cómo se dibuja cada opción lo decide la UI
 * (`ui.editview.toBackdrop`); aquí solo se define qué existe y cómo se persiste.
 *
 * Los nombres (`name`) son el formato de almacenamiento: NO renombrar una opción sin migrar lo guardado.
 */
enum class EditBackgroundChoice {
    /** El color del lienzo (verde croma): lo que se ve es lo que saldrá en el video. Es el valor por defecto. */
    CANVAS,

    /** Tablero claro: la forma estándar de mostrar transparencia (ver bordes de un PNG recortado). */
    CHECKER_LIGHT,

    /** Tablero oscuro: transparencia sobre fondo oscuro, para recortes claros. */
    CHECKER_DARK,

    /** Negro: para juzgar brillos y contraste. */
    BLACK,

    /** Gris medio: neutro, para juzgar colores sin que el fondo los contamine. */
    GRAY,

    /** Blanco: para revisar sombras y bordes oscuros. */
    WHITE,

    /** Un color elegido por el usuario (ver [EditBackgroundSettings.customArgb]). */
    CUSTOM
}

/**
 * Preferencia completa: qué opción está activa y cuál es el color personalizado (que se conserva aunque se
 * cambie a otra opción, para que volver a "Personalizado" no obligue a elegirlo de nuevo).
 *
 * Modelo puro (sin Android) para poder probarlo en JVM. La lectura/escritura real está en [EditBackgroundPrefs].
 */
data class EditBackgroundSettings(
    val choice: EditBackgroundChoice = EditBackgroundChoice.CANVAS,
    val customArgb: Int = DEFAULT_CUSTOM_ARGB
) {

    /** Formato de una línea: `"<NOMBRE_OPCIÓN>:<argb>"`, p. ej. `"CHECKER_LIGHT:-14671840"`. */
    fun encode(): String = "${choice.name}:$customArgb"

    companion object {
        /** Gris casi negro: un punto de partida neutro para el selector de color la primera vez. */
        const val DEFAULT_CUSTOM_ARGB: Int = 0xFF1E1E1E.toInt()

        /** Los fondos son siempre opacos: el alfa se fuerza a 255 al guardar y al leer. */
        const val OPAQUE_ALPHA_MASK: Int = 0xFF000000.toInt()

        val DEFAULT = EditBackgroundSettings()

        /**
         * Lee lo guardado. NUNCA lanza ni devuelve null: un valor ausente, corrupto o de una versión
         * desconocida equivale a "el usuario no ha elegido nada" y devuelve [DEFAULT] — la preferencia es
         * cosmética y no debe poder impedir que el editor abra.
         */
        fun decode(raw: String?): EditBackgroundSettings {
            if (raw.isNullOrBlank()) return DEFAULT
            val separator = raw.indexOf(':')
            if (separator <= 0) return DEFAULT

            val choice = EditBackgroundChoice.entries.firstOrNull { it.name == raw.substring(0, separator) }
                ?: return DEFAULT
            val customArgb = raw.substring(separator + 1).toIntOrNull() ?: return DEFAULT

            return EditBackgroundSettings(choice = choice, customArgb = customArgb or OPAQUE_ALPHA_MASK)
        }
    }
}
