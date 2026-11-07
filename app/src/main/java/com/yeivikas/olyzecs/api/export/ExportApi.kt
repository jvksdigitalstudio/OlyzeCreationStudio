package com.yeivikas.olyzecs.api.export

import com.yeivikas.olyzecs.engine.export.ExportProgress
import com.yeivikas.olyzecs.engine.export.ExportSettings
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Contrato público de EliNer para Export.
 *
 * Respaldo real: `engine.export.VideoExporter.export`. Único ajuste de
 * asincronía respecto de la firma actual (decidido en el diseño
 * aprobado, sección 8): el `onProgress: (ExportProgress) -> Unit`
 * (callback lambda) se expone acá como `Flow<ExportProgress>` — más
 * idiomático desde coroutines/Compose y más portable a futuro que un
 * callback Kotlin puro. [ExportSettings]/[ExportProgress]/
 * `ExportQuality` se reutilizan directos, sin cambios.
 *
 * [outputFile] se agrega en Fase 1.3/1.4 (hallazgo FASE1.3, aprobado):
 * `VideoExporter.export` lo necesita y ni `ExportSettings` ni
 * `ActiveProjectReader` lo resuelven — construirlo requiere
 * `Context.getExternalFilesDir`, la dependencia Android que
 * deliberadamente NO se mete en `ActiveProjectReader`. Se agrega como
 * parámetro del método, no como campo de `ExportSettings` (que queda
 * enfocado en configuración de codificación, no en rutas de archivo).
 *
 * Consumidor real: `EditorViewModel.exportVideo` (viewmodel/
 * EditorViewModel.kt) construye `ExportApiImpl(context, this)`
 * localmente en el momento de exportar (no inyectada por constructor,
 * a diferencia de `Mesh3DApi`/`AnimationApi`: acá el `Context` solo
 * está disponible como parámetro de la función, nunca en tiempo de
 * construcción del ViewModel — ver ADR-002) y colecta el `Flow`
 * resultante. Ver tarea "Export → EliNer".
 *
 * No expone un método `cancel()` explícito en este contrato (decisión ya
 * cerrada, ver ELINER_API_V1_AUDITORIA_DISENO.txt — documento de proceso
 * histórico, no versionado en este repositorio — sección 2): la
 * cancelación es cooperativa, no un método de la interfaz. `VideoExporter.export`
 * SÍ acepta un parámetro `isCancelled: () -> Boolean` y lo consulta
 * durante el procesamiento (frame a frame y en el encoding de audio) —
 * [ExportApiImpl] lo conecta con `isActive` de la corrutina que corre el
 * export, así que `EditorViewModel.cancelExport()` → `Job.cancel()` →
 * cancelación de esa corrutina → `isActive == false` → `isCancelled()`
 * devuelve `true` → `VideoExporter` corta cooperativamente. El contrato
 * de esta interfaz no necesita un método de cancelación propio porque el
 * mecanismo ya existe a nivel de `Flow`/`Job` de Kotlin — agregar uno
 * sería una segunda vía redundante para lo mismo.
 */
interface ExportApi {

    /**
     * Exporta el proyecto activo con [settings] hacia [outputFile]. El
     * [Flow] emite el progreso y termina en [ExportProgress.Done] o
     * [ExportProgress.Failed] (categoría CRÍTICA según ADR-003: un
     * fallo de exportación se propaga como valor terminal del Flow,
     * nunca se traga en un log silencioso).
     */
    fun export(outputFile: File, settings: ExportSettings): Flow<ExportProgress>
}
