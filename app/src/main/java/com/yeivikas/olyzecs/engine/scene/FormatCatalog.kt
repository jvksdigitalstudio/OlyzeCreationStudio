package com.yeivikas.olyzecs.engine.scene

/**
 * Catálogo de [FormatPreset] disponibles en el selector de "Nuevo
 * proyecto". Es una interfaz — no una lista fija embebida en el
 * dominio — a propósito: el punto 6 del prompt maestro original pide
 * que el catálogo pueda actualizarse (agregar/quitar presets, ajustar
 * dimensiones recomendadas de una plataforma) sin tocar el núcleo del
 * Canvas. Ver ADR-005.
 *
 * La implementación con los datos reales de presets (Instagram,
 * YouTube, TikTok, formatos generales, etc.) es [StaticFormatCatalog]
 * (Fase B) — no responsabilidad de este contrato.
 */
interface FormatCatalog {

    /** Categorías con al menos un preset disponible, en el orden en que deben mostrarse. */
    fun categories(): List<PresetCategory>

    /** Presets de una categoría, en el orden en que deben mostrarse. Lista vacía si la categoría no tiene presets todavía. */
    fun presetsIn(category: PresetCategory): List<FormatPreset>

    /** Busca un preset por su [FormatPreset.id] estable, o `null` si no existe (ej. un id guardado en un proyecto viejo que ya no está en el catálogo actual). */
    fun findById(id: String): FormatPreset?
}
