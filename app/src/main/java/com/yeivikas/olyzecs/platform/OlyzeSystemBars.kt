package com.yeivikas.olyzecs.platform

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Configuración ÚNICA de las barras del sistema (estado y navegación) de la app.
 *
 * ## Por qué existe
 * Desde Android 15 (API 35) el borde a borde es obligatorio para quien apunta a API 35+, y desde Android 16
 * (API 36) ya no se puede desactivar (`windowOptOutEdgeToEdgeEnforcement` queda deshabilitado). En ese modelo
 * las barras son SIEMPRE transparentes: `android:statusBarColor`, `android:navigationBarColor` y
 * `setDecorFitsSystemWindows(true)` están obsoletos y el sistema los ignora. Lo único que la app decide es el
 * estilo de los iconos (claros u oscuros) y qué dibuja DETRÁS de las barras.
 *
 * ## Contrato
 * - Detrás de las barras se ve el degradado de marca (`OlyzeGradient`, dibujado por la raíz de `MainActivity`).
 * - Los iconos del sistema son CLAROS: toda la app es oscura (morado profundo → azul).
 * - Cada pantalla es responsable de no poner contenido interactivo bajo las barras: `Scaffold` y `TopAppBar` de
 *   Material 3 ya aplican los insets; las barras propias deben usar `windowInsetsPadding(...)` (ver EditorScreen)
 *   y los campos de texto fuera de diálogos `imePadding()`.
 *
 * ## Una sola ruta
 * [apply] se llama desde `MainActivity.onCreate` Y desde el visor inmersivo al cerrarse (ver `FilePreviewDialog`),
 * de modo que el estado "normal" de la ventana es el mismo en ambos casos y no puede divergir con el tiempo.
 *
 * `enableEdgeToEdge` es de `androidx.activity` y es el camino oficial: aplica lo correcto en API 26-36
 * (scrim en API < 29, contraste forzado de la barra de 3 botones en API 29+, transparente con gestos).
 */
object OlyzeSystemBars {

    /**
     * Estilo "oscuro": iconos claros sobre fondo oscuro. `scrim` transparente: el degradado de marca se ve
     * por debajo; en API < 29 (sin barra de gestos) es lo que pinta la barra de navegación.
     */
    private val darkStyle: SystemBarStyle
        get() = SystemBarStyle.dark(Color.TRANSPARENT)

    /**
     * Pone la ventana de [activity] en borde a borde con iconos claros. Idempotente. Debe llamarse ANTES de
     * `super.onCreate()` y de `setContent` la primera vez (requisito de `enableEdgeToEdge`); después puede
     * volver a llamarse en cualquier momento para restaurar el estado base.
     */
    fun apply(activity: ComponentActivity) {
        activity.enableEdgeToEdge(
            statusBarStyle = darkStyle,
            navigationBarStyle = darkStyle
        )
    }
}
