package com.yeivikas.olyzecs.ui.editview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.yeivikas.olyzecs.data.EditBackgroundChoice
import com.yeivikas.olyzecs.data.EditBackgroundPrefs
import com.yeivikas.olyzecs.data.EditBackgroundSettings

/**
 * Estado observable del fondo de la vista de edición. Responsabilidad única: mantener la preferencia actual y
 * avisar cuando cambia para persistirla. No sabe de OpenGL, de la pantalla ni de SharedPreferences (recibe la
 * función de guardado), así que se prueba en JVM sin Android.
 */
@Stable
class EditBackgroundState(
    initial: EditBackgroundSettings,
    private val persist: (EditBackgroundSettings) -> Unit
) {
    var settings: EditBackgroundSettings by mutableStateOf(initial)
        private set

    /** Un toque: pasa a la siguiente opción de la rotación rápida. */
    fun cycle() = select(settings.choice.nextInQuickCycle())

    fun select(choice: EditBackgroundChoice) = update(settings.copy(choice = choice))

    /** Fija el color personalizado y lo activa. El alfa se fuerza a opaco: un fondo translúcido no tiene sentido. */
    fun setCustom(argb: Int) = update(
        settings.copy(
            choice = EditBackgroundChoice.CUSTOM,
            customArgb = argb or EditBackgroundSettings.OPAQUE_ALPHA_MASK
        )
    )

    private fun update(new: EditBackgroundSettings) {
        if (new == settings) return
        settings = new
        persist(new)
    }
}

/** Crea el estado una vez por pantalla, leyendo y guardando en [EditBackgroundPrefs]. */
@Composable
fun rememberEditBackgroundState(): EditBackgroundState {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EditBackgroundState(
            initial = EditBackgroundPrefs.load(appContext),
            persist = { EditBackgroundPrefs.save(appContext, it) }
        )
    }
}
