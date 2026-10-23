package com.yeivikas.olyzecs.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.yeivikas.olyzecs.platform.MultiTouchBackGuard

/**
 * [BackHandler] que ignora el "atrás" del sistema cuando es un residuo de un
 * pellizco multitáctil (ver [MultiTouchBackGuard]). Se traga el evento: no cierra
 * la ventana ampliada, ni el visor, ni sale del editor. Un "atrás" deliberado,
 * sin pellizco reciente, llega a [onBack] igual que con un `BackHandler` normal.
 *
 * Todos los niveles de navegación con gestos de zoom usan este en vez de `BackHandler`,
 * para que la decisión sea la misma en cada uno y no dependa de temporizadores locales.
 */
@Composable
internal fun GuardedBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled) {
        if (!MultiTouchBackGuard.shouldSuppressBack(SystemClock.uptimeMillis())) onBack()
    }
}
