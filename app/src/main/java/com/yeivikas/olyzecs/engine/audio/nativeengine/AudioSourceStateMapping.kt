package com.yeivikas.olyzecs.engine.audio.nativeengine

import com.yeivikas.olyzecs.engine.audio.AudioSourceState

/**
 * Traduce el estado INTERNO de la cola de preparación al que ve la interfaz. Una función pura y aparte para poder comprobarla sin hilos.
 *
 * Un segundo intento en curso se muestra como "reintentando" (igual que la espera previa a él): así el aviso no parpadea entre
 * "esperando el reintento" y "ejecutándolo".
 */
internal fun SourcePrepStatus.toAudioSourceState(maxAttempts: Int = SOURCE_PREP_MAX_ATTEMPTS): AudioSourceState = when (this) {
    is SourcePrepStatus.Preparing ->
        if (attempt <= 1) AudioSourceState.Preparing else AudioSourceState.Retrying(attempt, maxAttempts)
    is SourcePrepStatus.Retrying -> AudioSourceState.Retrying(attempt, maxAttempts)
    is SourcePrepStatus.Failed -> AudioSourceState.Failed(reason)
}
