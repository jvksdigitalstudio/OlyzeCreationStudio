package com.yeivikas.olyzecs.engine.audio.nativeengine

import com.yeivikas.olyzecs.engine.audio.AudioSourceId
import com.yeivikas.olyzecs.engine.audio.AudioSourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Cómo el estado INTERNO de la cola se traduce al que ve la interfaz. */
class AudioSourceStateMappingTest {

    @Test
    fun `el primer intento, en cola o en curso, es simplemente preparando`() {
        assertEquals(AudioSourceState.Preparing, SourcePrepStatus.Preparing(attempt = 1, running = false).toAudioSourceState())
        assertEquals(AudioSourceState.Preparing, SourcePrepStatus.Preparing(attempt = 1, running = true).toAudioSourceState())
    }

    @Test
    fun `un segundo intento en curso se muestra como reintentando, igual que su espera`() {
        // Así el aviso no parpadea entre "esperando el reintento" y "ejecutándolo".
        val waiting = SourcePrepStatus.Retrying(attempt = 2, retryAtMs = 100, reason = "disco").toAudioSourceState(maxAttempts = 3)
        val running = SourcePrepStatus.Preparing(attempt = 2, running = true).toAudioSourceState(maxAttempts = 3)
        assertEquals(AudioSourceState.Retrying(2, 3), waiting)
        assertEquals(waiting, running)
    }

    @Test
    fun `un fallo definitivo conserva su motivo`() {
        assertEquals(
            AudioSourceState.Failed("Sin permiso para leer el archivo"),
            SourcePrepStatus.Failed(attempts = 1, reason = "Sin permiso para leer el archivo").toAudioSourceState()
        )
    }

    @Test
    fun `el maximo de intentos por defecto es el de la cola`() {
        val state = SourcePrepStatus.Retrying(attempt = 2, retryAtMs = 0, reason = "x").toAudioSourceState()
        assertEquals(AudioSourceState.Retrying(2, SOURCE_PREP_MAX_ATTEMPTS), state)
    }

    @Test
    fun `la identidad de una fuente distingue archivo y duracion`() {
        val a = AudioSourceId("content://audio/1", 1_000L)
        assertEquals(a, AudioSourceId("content://audio/1", 1_000L)) // dos clips del mismo archivo comparten estado
        assertNotEquals(a, AudioSourceId("content://audio/1", 2_000L))
        assertNotEquals(a, AudioSourceId("content://audio/2", 1_000L))
    }
}
