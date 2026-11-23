package com.yeivikas.olyzecs.engine.scene

/**
 * Un formato de canvas presentable en el selector: [id] estable +
 * metadata de UI ([label], [subtitle], [category]) sobre un
 * [CanvasFormat] físico.
 *
 * [id] es jerárquico y NO se basa en dimensiones (ej.
 * `"instagram.reels.vertical"`, no `"1080x1920"`) — ver ADR-005 y el
 * punto 14 del prompt maestro original: dos plataformas pueden
 * compartir exactamente el mismo [CanvasFormat] sin ser la misma
 * entidad conceptual, así que el id no puede derivarse del tamaño.
 *
 * Este [id] es también el que persiste [CanvasSpec.originPresetId] en
 * un proyecto guardado — por eso debe ser estable entre versiones del
 * catálogo: cambiar el [CanvasFormat] al que apunta un preset existente
 * en una versión futura de la app NO debe alterar proyectos ya
 * guardados, porque el [CanvasSpec] persistido ya tiene sus propias
 * dimensiones (ver ADR-005, consecuencias).
 */
data class FormatPreset(
    val id: String,
    val label: String,
    val subtitle: String,
    val category: PresetCategory,
    val format: CanvasFormat
) {
    init {
        require(id.isNotBlank()) { "id no puede estar vacío" }
        require(label.isNotBlank()) { "label no puede estar vacío" }
    }
}
