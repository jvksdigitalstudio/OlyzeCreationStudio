package com.yeivikas.olyzecs.api.model

import android.net.Uri

/**
 * Representación pública e INMUTABLE del clip de audio de fondo del
 * proyecto, para EliNer API — análoga a [LayerSnapshot] pero para
 * [com.yeivikas.olyzecs.engine.audio.AudioClip].
 *
 * FASE 4.2 (AUDITORÍA — corrección real, ver punto 6 del prompt maestro):
 * antes, `AudioApi.getAudioClip()` devolvía directamente una instancia de
 * `AudioClip`, que es una clase con `var` mutables (`sourceUri`,
 * `displayName`, `sourceDurationMs`, `volume`, `muted`, `trimStartMs`,
 * `loop`, `fadeInMs`, `fadeOutMs`). Cualquier consumidor de la API pública
 * podía mutar esos campos DIRECTO, sin pasar por `AudioApi.setVolume()` /
 * `setMuted()` / `setTrimStart()` / `setLoop()` / `setFade()` — lo cual se
 * salta autosave, undo, notificaciones de cambio y cualquier validación
 * futura que esos setters lleguen a tener. La instancia devuelta por
 * `getAudioClip()` era, además, la MISMA instancia en vivo que usa el
 * motor (`ActiveProjectReader.getAudioClip()` no copia nada) — mutarla
 * desde afuera de la API era indistinguible de mutarla desde dentro.
 *
 * `AudioClipSnapshot` es un `data class` de solo `val`: no hay ningún
 * campo que un consumidor pueda reasignar. La implementación interna
 * ([com.yeivikas.olyzecs.api.audio.AudioApiImpl]) sigue usando `AudioClip`
 * mutable para su propio wiring con el motor — este DTO es exclusivamente
 * la forma en la que ese estado CRUZA la frontera pública de EliNer.
 *
 * Conserva `sourceUri: Uri` sin envolverlo — mismo criterio ya cerrado
 * para [LayerSnapshot.sourceUri] (ADR-002): es un dato de referencia a
 * archivo, no una implementación de Android que haga falta abstraer acá.
 */
data class AudioClipSnapshot(
    val sourceUri: Uri,
    val displayName: String,
    val sourceDurationMs: Long,
    val volume: Float,
    val muted: Boolean,
    val trimStartMs: Long,
    val loop: Boolean,
    val fadeInMs: Long,
    val fadeOutMs: Long
)
