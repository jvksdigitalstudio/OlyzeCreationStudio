package com.yeivikas.olyzecs.api.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
 * [getAudioClip] lee directo de [reader] (sin copia). Los 5 setters y
 * [clearAudioClip] delegan a [mutator], que a su vez usa las funciones
 * ya existentes de `EditorViewModel` (mismo autosave, sin undo — el
 * audio queda fuera del undo/redo por diseño ya existente, ver
 * Fase 1.2). [playFrom]/[pause]/[seekTo] controlan el mismo
 * `AudioPreviewPlayer` cacheado dentro de `EditorViewModel` a través de
 * [mutator] (ver nota "TERCERA CORRECCIÓN" en `ActiveProjectMutator.kt`)
 * — nunca crean un reproductor propio.
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

    override fun getAudioClip(): AudioClip? = reader.getAudioClip()

    override suspend fun setAudioClip(sourceUri: Uri) {
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
            AppLogger.w("AudioApiImpl", "No se pudo importar el audio '$displayName' — no se le pudo leer una duración válida: $sourceUri")
            return
        }

        mutator.setAudioClipDirect(
            AudioClip(sourceUri = sourceUri, displayName = displayName, sourceDurationMs = durationMs)
        )
    }

    override suspend fun clearAudioClip() {
        mutator.clearAudioClip()
    }

    override suspend fun setVolume(volume: Float) {
        mutator.applyAudioVolume(volume)
    }

    override suspend fun setMuted(muted: Boolean) {
        mutator.setAudioMuted(muted)
    }

    override suspend fun setTrimStart(trimStartMs: Long) {
        mutator.applyAudioTrimStart(trimStartMs)
    }

    override suspend fun setLoop(loop: Boolean) {
        mutator.applyAudioLoop(loop)
    }

    override suspend fun setFade(fadeInMs: Long, fadeOutMs: Long) {
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
