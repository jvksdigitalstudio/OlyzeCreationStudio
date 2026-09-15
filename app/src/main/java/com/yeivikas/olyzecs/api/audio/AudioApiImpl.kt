package com.yeivikas.olyzecs.api.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.yeivikas.olyzecs.api.model.AudioClipSnapshot
import com.yeivikas.olyzecs.api.project.ActiveProjectMutator
import com.yeivikas.olyzecs.api.project.ActiveProjectReader
import com.yeivikas.olyzecs.data.ProjectStorage
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.audio.AudioClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación real de [AudioApi].
 *
 * [getAudioClip] lee de [reader] y lo convierte a [AudioClipSnapshot]
 * INMUTABLE antes de devolverlo (ver punto 6 de la Fase 4.2 en el KDoc de
 * [AudioApi.getAudioClip]) — nunca se expone la instancia mutable en vivo
 * del motor a través de esta frontera pública. Los 5 setters y
 * [clearAudioClip] delegan a [mutator], que a su vez usa las funciones
 * ya existentes de `EditorViewModel` (mismo autosave, sin undo — el
 * audio queda fuera del undo/redo por diseño ya existente, ver
 * Fase 1.2). [playFrom]/[pause]/[seekTo] controlan el mismo
 * `AudioPreviewPlayer` cacheado dentro de `EditorViewModel` a través de
 * [mutator] (ver nota "TERCERA CORRECCIÓN" en `ActiveProjectMutator.kt`)
 * — nunca crean un reproductor propio.
 *
 * FASE 4.2-R4 (Parte 3/8 del prompt maestro R4 — confinamiento de
 * mutaciones): [clearAudioClip]/[setVolume]/[setMuted]/[setTrimStart]/
 * [setLoop]/[setFade]/[setAudioClip] pasan explícitamente por
 * `withContext(Dispatchers.Main.immediate)` antes de llamar a [mutator]
 * — mismo criterio que `LayerApiImpl`/`CameraApiImpl`, ver su KDoc.
 * [playFrom]/[pause]/[seekTo] quedan sin ese wrapper a propósito: son
 * `fun` NO `suspend` (ver KDoc de `ActiveProjectMutator`, "no hacen IO/
 * espera genuina"), así que `withContext` no aplica — su contrato ya
 * asume que el llamador está en el mismo hilo que controla la UI de
 * transporte, igual que antes de esta fase.
 *
 * [setAudioClip]/[probeDurationMs] son las únicas operaciones de I/O de
 * este dominio (leer metadata de un archivo externo) — fuera de
 * [ActiveProjectMutator] a propósito (mismo criterio que
 * `LayerApi.createLayers`, ver Fase 1.3 sección 6); [context] y
 * [projectStorage] se reciben para eso exclusivamente.
 */
class AudioApiImpl(
    private val context: Context,
    private val reader: ActiveProjectReader,
    private val mutator: ActiveProjectMutator,
    private val projectStorage: ProjectStorage
) : AudioApi {

    override fun getAudioClip(): AudioClipSnapshot? = reader.getAudioClip()?.toSnapshot()

    private fun AudioClip.toSnapshot() = AudioClipSnapshot(
        sourceUri = sourceUri,
        displayName = displayName,
        sourceDurationMs = sourceDurationMs,
        volume = volume,
        muted = muted,
        trimStartMs = trimStartMs,
        loop = loop,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs
    )

    override suspend fun setAudioClip(sourceUri: Uri): Boolean {
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        try {
            resolver.takePersistableUriPermission(sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            AppLogger.i("AudioApiImpl", "El proveedor del audio no soporta permiso persistente (no es grave): ${e.message}")
        }

        val displayName = withContext(Dispatchers.IO) { queryDisplayName(resolver, sourceUri) }
            ?: "audio_${System.currentTimeMillis()}"
        val durationMs = withContext(Dispatchers.IO) { projectStorage.probeAudioDurationMs(sourceUri) }
        if (durationMs <= 0L) {
            // ADR-003: falla RECUPERABLE — se registra y se informa al
            // consumidor vía el `false` de retorno (ver KDoc de
            // AudioApi.setAudioClip); el proyecto queda sin tocar.
            AppLogger.w("AudioApiImpl", "No se pudo importar el audio '$displayName' — no se le pudo leer una duración válida: $sourceUri")
            return false
        }

        withContext(Dispatchers.Main.immediate) {
            mutator.setAudioClipDirect(
                AudioClip(sourceUri = sourceUri, displayName = displayName, sourceDurationMs = durationMs)
            )
        }
        return true
    }

    override suspend fun clearAudioClip() = withContext(Dispatchers.Main.immediate) {
        mutator.clearAudioClip()
    }

    override suspend fun setVolume(volume: Float) = withContext(Dispatchers.Main.immediate) {
        mutator.applyAudioVolume(volume)
    }

    override suspend fun setMuted(muted: Boolean) = withContext(Dispatchers.Main.immediate) {
        mutator.setAudioMuted(muted)
    }

    override suspend fun setTrimStart(trimStartMs: Long) = withContext(Dispatchers.Main.immediate) {
        mutator.applyAudioTrimStart(trimStartMs)
    }

    override suspend fun setLoop(loop: Boolean) = withContext(Dispatchers.Main.immediate) {
        mutator.applyAudioLoop(loop)
    }

    override suspend fun setFade(fadeInMs: Long, fadeOutMs: Long) = withContext(Dispatchers.Main.immediate) {
        mutator.applyAudioFade(fadeInMs, fadeOutMs)
    }

    override suspend fun probeDurationMs(sourceUri: Uri): Long =
        withContext(Dispatchers.IO) { projectStorage.probeAudioDurationMs(sourceUri) }

    override fun playFrom(projectTimeMs: Long) {
        mutator.previewPlayFrom(context, projectTimeMs)
    }

    override fun pause() {
        mutator.previewPause()
    }

    override fun seekTo(projectTimeMs: Long) {
        mutator.previewSeekTo(context, projectTimeMs)
    }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }
}
