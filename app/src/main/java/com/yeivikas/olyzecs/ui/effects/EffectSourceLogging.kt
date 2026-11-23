package com.yeivikas.olyzecs.ui.effects

import com.yeivikas.olyzecs.debug.AppLogger

/**
 * Adaptador de Android para [EffectSourceLoader]: deja constancia en el «Registro de errores» de que una decodificación
 * falló. Importa porque, sin la imagen completa, los efectos NO se guardan (nunca se guarda una copia reducida) y el
 * usuario necesita poder saber por qué.
 */
fun logEffectDecodeFailure(maxDimension: Int, error: Exception) {
    val size = when {
        maxDimension == EffectSourceLoader.UNKNOWN_SIZE -> "(no se llegó a determinar el tamaño)"
        maxDimension == EffectSourceLoader.NO_LIMIT -> "a resolución completa"
        else -> "a $maxDimension px"
    }
    AppLogger.w("EffectSources", "No se pudo decodificar la imagen $size: ${error.message ?: error.javaClass.simpleName}", error)
}
