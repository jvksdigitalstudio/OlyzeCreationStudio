package com.yeivikas.olyzecs.api.export

import android.content.Context
import com.yeivikas.olyzecs.api.project.ActiveProjectReader
import com.yeivikas.olyzecs.engine.export.ExportProgress
import com.yeivikas.olyzecs.engine.export.ExportSettings
import com.yeivikas.olyzecs.engine.export.VideoExporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Implementación real de [ExportApi]: `ExportApiImpl → ActiveProjectReader`,
 * NUNCA `ExportApiImpl → EditorViewModel` (ver Fase 1.4, sección 12).
 *
 * Resuelve FASE1-001 (Fase 1, Fase 1.1): arma los 7 parámetros reales de
 * `VideoExporter.export` leyendo [reader] en el momento de arrancar el
 * export — `layers`/`audioTrack`/`speedKeyframes`/`freezeFrames` quedan
 * fijados (snapshot congelado, por valor) en ESE instante, exactamente
 * como ya funciona `VideoExporter.export` hoy (recibe listas por valor,
 * no referencias observables) — así, ediciones posteriores mientras el
 * export está en curso no lo contaminan (regla del brief, sección 12).
 *
 * `onProgress` (callback del Engine) se adapta a `Flow<ExportProgress>`
 * con `callbackFlow` — el único ajuste de asincronía del contrato,
 * decidido en el diseño aprobado.
 */
class ExportApiImpl(
    private val context: Context,
    private val reader: ActiveProjectReader
) : ExportApi {

    override fun export(outputFile: File, settings: ExportSettings): Flow<ExportProgress> = callbackFlow {
        // Snapshot explícito ACÁ, no dentro de VideoExporter: aunque las
        // listas ya viajan por valor (List<T> inmutable en la firma de
        // Kotlin), leerlas todas juntas en este único punto, antes de
        // lanzar la corrutina de export, es lo que garantiza que
        // representan el estado del proyecto en el instante en que se
        // pidió exportar — no un instante posterior a mitad del loop.
        val layers = reader.getLayers()
        // TODOS los carriles (todos sus clips), no `getAudioClip()` (que
        // deliberadamente solo ve el primer clip del carril principal — ver
        // KDoc de ambos métodos en `ActiveProjectReader`): el export real
        // tiene que mezclar todos los clips de todos los carriles, se
        // solapen o no.
        val audioTracks = reader.getAudioTracks()
        val speedKeyframes = reader.getSpeedKeyframes()
        val freezeFrames = reader.getFreezeFrames()
        val baseDurationMs = reader.getBaseDurationMs()

        withContext(Dispatchers.Default) {
            VideoExporter(context).export(
                layers = layers,
                settings = settings,
                outputFile = outputFile,
                audioTracks = audioTracks,
                baseDurationMs = baseDurationMs,
                speedKeyframes = speedKeyframes,
                freezeFrames = freezeFrames,
                // DISEÑO — auditoría: `export()` no es una función suspend
                // (ver el comentario grande en su firma) y corre entera de
                // forma síncrona en ESTE `withContext` — así que la única
                // forma real de que un `Job.cancel()` de afuera (ver
                // `EditorViewModel.cancelExport`) interrumpa el loop de
                // frames a mitad de camino es pasarle este chequeo
                // explícito. `isActive` acá es el de ESTE `CoroutineScope`
                // (el que crea `withContext`), que queda cancelado de
                // inmediato en cuanto se cancela cualquier corrutina padre
                // en su jerarquía — no hace falta esperar a ningún punto
                // de suspensión para que el valor cambie.
                isCancelled = { !isActive },
                onProgress = { progress -> trySend(progress) }
            )
        }
        close()
        awaitClose { }
        // conflate(): `trySend` desde el exportador (hilo de CPU) NO suspende; con el
        // buffer finito por defecto y un colector lento, el evento terminal
        // (Done/Failed/Cancelled) podía descartarse y la UI quedaba en "exportando"
        // para siempre. Conflar conserva siempre el ÚLTIMO valor emitido.
    }.conflate()
}
