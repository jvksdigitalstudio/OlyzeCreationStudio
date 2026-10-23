package com.yeivikas.olyzecs.engine.scene

/**
 * Agrupación del catálogo de [FormatPreset] para la UI del selector
 * (ver ADR-005, §16 "UI del selector"). [CINEMATIC] arranca poblada
 * con 3 relaciones de aspecto reales de cine (ver [StaticFormatCatalog]
 * para el detalle de cada una) — deja de estar diferida (Fase 2, ADR-005
 * D4) a partir de la ronda de auditoría que agrega esta categoría al
 * selector, entre "Redes sociales" y "Formatos generales".
 */
enum class PresetCategory(val label: String) {
    SOCIAL("Redes sociales"),
    CINEMATIC("Cine"),
    GENERAL("Formatos generales")
}
