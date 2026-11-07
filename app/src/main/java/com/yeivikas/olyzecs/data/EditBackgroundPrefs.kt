package com.yeivikas.olyzecs.data

import android.content.Context

/**
 * Lectura y escritura de [EditBackgroundSettings] en `SharedPreferences`.
 *
 * Misma convención que [UserColorPrefs]: una preferencia del usuario/dispositivo (no de un proyecto), así que
 * vale para todos los proyectos. Esta clase es SOLO el adaptador de Android; el formato y la validación viven en
 * [EditBackgroundSettings] (puro y probado en JVM).
 */
object EditBackgroundPrefs {
    private const val PREFS_NAME = "olyze_edit_view"
    private const val KEY_EDIT_BACKGROUND = "edit_background_v1"

    fun load(context: Context): EditBackgroundSettings =
        EditBackgroundSettings.decode(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_EDIT_BACKGROUND, null)
        )

    fun save(context: Context, settings: EditBackgroundSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EDIT_BACKGROUND, settings.encode())
            .apply()
    }
}
