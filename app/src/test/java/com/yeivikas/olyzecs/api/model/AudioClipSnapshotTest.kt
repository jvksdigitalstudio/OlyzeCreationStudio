package com.yeivikas.olyzecs.api.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Test OBLIGATORIO de la Fase 4.2 — punto 6/25.D del prompt maestro:
 * verifica que [AudioClipSnapshot] es de verdad inmutable — ningún campo
 * es reasignable por un consumidor de `AudioApi.getAudioClip()` — a
 * diferencia de `engine.audio.AudioClip`, que sí expone `var` mutables.
 *
 * Deliberadamente por reflexión sobre la propia `Class`, SIN construir
 * ninguna instancia real (evita depender de `android.net.Uri` en runtime,
 * que en un unit test JVM puro sin Robolectric no está inicializado) —
 * esto sigue siendo una verificación real del contrato compilado, no una
 * suposición: si algún campo de [AudioClipSnapshot] pasara a ser `var`,
 * este test falla.
 */
class AudioClipSnapshotTest {

    @Test
    fun `AudioClipSnapshot no expone ningun campo mutable`() {
        val fields = AudioClipSnapshot::class.java.declaredFields.filter { !it.isSynthetic }
        assertTrue("se esperaban campos declarados en AudioClipSnapshot", fields.isNotEmpty())
        fields.forEach { field ->
            assertTrue(
                "El campo '${field.name}' de AudioClipSnapshot debería ser `val` (final) — " +
                    "si es `var`, un consumidor de la API pública puede reasignarlo directo, " +
                    "saltándose setVolume/setMuted/setTrimStart/setLoop/setFade " +
                    "(y con eso, autosave/undo/validación).",
                Modifier.isFinal(field.modifiers)
            )
        }
    }

    @Test
    fun `AudioClipSnapshot cubre los mismos 9 campos mutables que tenia AudioClip expuestos`() {
        // No es una migración 1:1 casual: son exactamente los campos que la
        // auditoría (punto 6) identificó como mutables y expuestos sin
        // control en `engine.audio.AudioClip` — sourceUri, displayName,
        // sourceDurationMs, volume, muted, trimStartMs, loop, fadeInMs,
        // fadeOutMs.
        val expectedFieldNames = setOf(
            "sourceUri", "displayName", "sourceDurationMs", "volume", "muted",
            "trimStartMs", "loop", "fadeInMs", "fadeOutMs"
        )
        val actualFieldNames = AudioClipSnapshot::class.java.declaredFields
            .filter { !it.isSynthetic }
            .map { it.name }
            .toSet()
        assertFalse(actualFieldNames.isEmpty())
        assertTrue(
            "faltan campos esperados en AudioClipSnapshot: ${expectedFieldNames - actualFieldNames}",
            actualFieldNames.containsAll(expectedFieldNames)
        )
    }
}
