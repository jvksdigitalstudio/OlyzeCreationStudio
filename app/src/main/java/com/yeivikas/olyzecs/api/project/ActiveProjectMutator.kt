package com.yeivikas.olyzecs.api.project

import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.Keyframe
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.scene.Layer
import android.content.Context
import com.yeivikas.olyzecs.engine.audio.AudioClip

/**
 * Mutación acotada del proyecto activo.
 *
 * Regla crítica (Fase 1.4, sección 6): cada método, en la implementación
 * real, DEBE terminar pasando por la misma lógica que ya usa
 * `EditorViewModel` para esa mutación — `pushUndoCheckpoint()`,
 * `scheduleAutosave()`, validaciones existentes. Esta interfaz es una
 * puerta de acceso a la máquina de edición que ya existe, no una segunda
 * máquina de edición paralela.
 *
 * No almacena estado, no contiene lógica de ownership, sin `Context`,
 * sin UI. Implementada por `EditorViewModel`; cada `*ApiImpl` de
 * escritura la recibe por constructor.
 *
 * Deliberadamente NO incluye `createLayers`/importar audio (operaciones
 * de I/O — decodificar/leer un archivo externo — distintas de "mutar
 * estado ya en memoria"; ver Fase 1.3, sección 6). Tampoco incluye
 * undo/redo ni el playhead — no forman parte de este contrato.
 *
 * NOTA DE IMPLEMENTACIÓN (hallazgo real de Fase 1.4, no de diseño):
 * los 4 métodos de audio de abajo NO se llaman igual que sus equivalentes
 * ya existentes en `EditorViewModel` (`setAudioVolume`/`setAudioTrimStart`/
 * `setAudioLoop`/`setAudioFade`, todos sin `suspend`, usados hoy por la
 * UI de forma síncrona). Kotlin no permite que una clase tenga dos
 * miembros con el mismo nombre y los mismos parámetros que difieran
 * únicamente en el modificador `suspend` ("conflicting overloads") — así
 * que, para no romper esas funciones existentes (usadas por la capa de
 * UI, fuera
 * de alcance de esta fase) ni obligarlas a volverse `suspend`, esta
 * interfaz usa nombres `applyAudio*` en vez de `setAudio*` para esos 4
 * casos puntuales. Es un ajuste de nomenclatura interno a esta interfaz
 * (recién creada en esta misma fase) — no afecta en nada al contrato ya
 * aprobado de `AudioApi` (que sigue exponiendo `setVolume`/etc. hacia
 * afuera sin cambios).
 *
 * SEGUNDA CORRECCIÓN DE IMPLEMENTACIÓN (Fase 1.4): `LayerApi.createLayers`
 * necesita, además de decodificar la imagen (responsabilidad de
 * `LayerRepository`, fuera de esta interfaz), AGREGAR las capas ya
 * decodificadas al proyecto activo — algo que, hasta este punto, no tenía
 * ningún método acá. Se agrega [addLayers] como el mínimo necesario: el
 * llamador (`LayerApiImpl`) decodifica con `LayerRepository` y le pasa acá
 * el resultado ya armado; esta función solo hace lo que ya hace
 * `EditorViewModel.importImages` con el resultado del repository —
 * anexar a `_uiState.layers` y disparar autosave.
 *
 * TERCERA CORRECCIÓN DE IMPLEMENTACIÓN (Fase 1.4): `AudioApi.playFrom`/
 * `pause`/`seekTo` deben controlar el MISMO `AudioPreviewPlayer` (un
 * `MediaPlayer` real) que ya vive cacheado, perezosamente, dentro de
 * `EditorViewModel` — si `AudioApiImpl` creara su propia instancia, habría
 * dos reproductores compitiendo por el mismo audio (bug de recurso
 * duplicado, no solo de arquitectura). `AudioPreviewPlayer` necesita
 * `Context` para construirse (igual que `AudioProcessor`/`VideoExporter`/
 * `ThumbnailRenderer`, aceptado por ADR-002) — así que, únicamente para
 * estas 3 operaciones de transporte del preview, esta interfaz SÍ recibe
 * `Context` como parámetro de la llamada (nunca almacenado — mismo patrón
 * exacto que ya usa `EditorViewModel.syncAudioPreview(context: Context)`
 * hoy). No contradice la regla general de "sin Context" del resto de la
 * interfaz: ninguna otra operación lo necesita, y esta sí, por la razón
 * concreta de arriba.
 *
 * CUARTA CORRECCIÓN DE IMPLEMENTACIÓN (Fase 1.4): `TimelineDurationManager`
 * (Engine puro, dueño real de la duración) vive privado dentro de
 * `EditorViewModel`. Sus 2 métodos que MUTAN duración
 * (`growIfApproachingEnd`/`ensureCapacityFor`) siempre van seguidos, en el
 * código real, de actualizar `_uiState.projectDurationMs`/`isAtMaxDuration`
 * en el mismo paso — exponer el manager crudo por `ActiveProjectReader`
 * permitiría mutarlo sin esa sincronización (una fuga de estado real, no
 * solo teórica). Se agregan acá, en el mutator, para que la implementación
 * siga exactamente el mismo patrón de 2 pasos que ya usa `EditorViewModel`
 * en sus 3 puntos de llamada actuales. No-suspend (igual que
 * `pushUndoCheckpoint`/`notifyLayersChanged`): son mutaciones síncronas
 * de `_uiState`, sin espera real — coincide con la firma no-suspend ya
 * aprobada de `TimelineApi.growIfApproachingEnd`/`ensureCapacityFor`.
 */
interface ActiveProjectMutator {

    // --- Layer ---
    /** Agrega capas ya decodificadas (por [com.yeivikas.olyzecs.data.LayerRepository]) al proyecto activo. */
    suspend fun addLayers(layers: List<Layer>)
    suspend fun setLayerVisible(layerId: String, visible: Boolean)
    suspend fun setLayerLocked(layerId: String, locked: Boolean)
    suspend fun setLayerOrderLocked(layerId: String, orderLocked: Boolean)
    suspend fun setLayerZIndex(layerId: String, newZIndex: Int)
    suspend fun setLayerLookSettings(layerId: String, look: LookSettings)
    suspend fun deleteLayer(layerId: String)

    // --- Camera ---
    suspend fun setCameraKeyframe(layerId: String, keyframe: Keyframe)
    suspend fun removeCameraKeyframe(layerId: String, timeMs: Long)
    suspend fun setCameraBaseFrame(layerId: String, frame: CameraFrame)

    // --- Audio (nombres "apply*", ver nota de implementación arriba) ---
    suspend fun applyAudioVolume(volume: Float)
    suspend fun setAudioMuted(muted: Boolean)
    suspend fun applyAudioTrimStart(trimStartMs: Long)
    suspend fun applyAudioLoop(loop: Boolean)
    suspend fun applyAudioFade(fadeInMs: Long, fadeOutMs: Long)
    suspend fun clearAudioClip()
    /** Fija/reemplaza el clip de audio del proyecto (ya armado por el llamador — ver AudioApiImpl.setAudioClip). */
    suspend fun setAudioClipDirect(clip: AudioClip)

    // --- Audio: transporte del preview en vivo (Context por parámetro, ver
    // nota de cabecera). No-suspend a propósito: AudioApi.playFrom/pause/
    // seekTo ya están declaradas sin suspend en el contrato aprobado, y la
    // operación real (llamar a MediaPlayer) no hace IO/espera genuina. ---
    fun previewPlayFrom(context: Context, projectTimeMs: Long)
    fun previewPause()
    fun previewSeekTo(context: Context, projectTimeMs: Long)

    // --- Timeline: las 2 únicas operaciones de TimelineDurationManager
    // que MUTAN duración — ver nota "CUARTA CORRECCIÓN" más abajo. ---
    /** Expande la duración un tramo si el playhead está por terminarse; devuelve la nueva duración. */
    fun growTimelineIfApproachingEnd(playheadMs: Long): Long
    /** Asegura que la duración alcance para llegar a [targetMs]; devuelve la nueva duración. */
    fun ensureTimelineCapacityFor(targetMs: Long): Long
}
