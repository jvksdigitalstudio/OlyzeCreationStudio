package com.yeivikas.olyzecs

import android.app.Application
import com.yeivikas.olyzecs.debug.AppLogger

/**
 * Punto de entrada de la aplicación.
 *
 * Lo primero que se hace en [onCreate], antes que cualquier otra cosa, es
 * inicializar [AppLogger]: instala el handler global de crashes y carga
 * el historial de errores persistido — así, sin importar en qué momento
 * de la vida de la app pase algo, queda registrado desde el instante cero.
 */
class OlyzeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
    }
}
