package com.yeivikas.olyzecs.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yeivikas.olyzecs.data.UserColorPrefs

/**
 * Dueño de los colores "Guardados" y "Recientes" del cuentagotas.
 *
 * Encontrado en la segunda auditoría (previa a construir EliNer API):
 * `LayerColorPickerDialog` (en `ui/LayerDialogs.kt`) llamaba a
 * `UserColorPrefs` — un `object` respaldado por `SharedPreferences` en
 * `data/` — directo, en 10 lugares. Mismo patrón que `ProjectStorage`
 * antes de la Etapa 3, solo que de menor escala (síncrono, sin recursos
 * vivos que liberar). Se corrige con el mismo criterio: la UI ya no
 * conoce que existe `UserColorPrefs`, solo este ViewModel.
 *
 * A diferencia de [EditorViewModel] (que se recrea por proyecto) y
 * [ProjectsViewModel], los colores guardados son una preferencia del
 * USUARIO/DISPOSITIVO — vale para todos los proyectos, no solo el que
 * está abierto. Por eso este ViewModel es un simple traductor sin estado
 * propio (no cachea nada en un `StateFlow`): cada llamada delega directo
 * en `UserColorPrefs` y devuelve la lista actualizada, igual que hacía el
 * `object` — el diálogo sigue guardando esa lista en su propio
 * `remember`, que es lo correcto para un estado que solo le importa a UN
 * diálogo mientras está abierto.
 */
class ColorPrefsViewModel(private val appContext: Context) : ViewModel() {

    fun loadSavedColors(): List<UserColorPrefs.SavedColorEntry> =
        UserColorPrefs.loadSavedColors(appContext)

    fun loadRecentColors(): List<UserColorPrefs.SavedColorEntry> =
        UserColorPrefs.loadRecentColors(appContext)

    fun addSavedColor(colorArgb: Int): List<UserColorPrefs.SavedColorEntry> =
        UserColorPrefs.addSavedColor(appContext, colorArgb)

    fun addSavedGradient(startArgb: Int, endArgb: Int): List<UserColorPrefs.SavedColorEntry> =
        UserColorPrefs.addSavedGradient(appContext, startArgb, endArgb)

    fun removeSavedEntry(entry: UserColorPrefs.SavedColorEntry): List<UserColorPrefs.SavedColorEntry> =
        UserColorPrefs.removeSavedEntry(appContext, entry)

    fun removeRecentEntry(entry: UserColorPrefs.SavedColorEntry): List<UserColorPrefs.SavedColorEntry> =
        UserColorPrefs.removeRecentEntry(appContext, entry)

    fun recordRecentColor(colorArgb: Int): List<UserColorPrefs.SavedColorEntry> =
        UserColorPrefs.recordRecentColor(appContext, colorArgb)

    fun recordRecentGradient(startArgb: Int, endArgb: Int): List<UserColorPrefs.SavedColorEntry> =
        UserColorPrefs.recordRecentGradient(appContext, startArgb, endArgb)
}

class ColorPrefsViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ColorPrefsViewModel(appContext) as T
    }
}
