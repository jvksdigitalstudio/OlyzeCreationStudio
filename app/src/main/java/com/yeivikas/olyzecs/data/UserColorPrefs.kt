package com.yeivikas.olyzecs.data

import android.content.Context

/**
 * Colores personalizados que el usuario decide GUARDAR desde la rueda de
 * color (botón "Guardar este color" en LayerColorPickerDialog, ver
 * TimelineView.kt). A propósito NO viven dentro de un `project.json`
 * puntual: son una preferencia del usuario/dispositivo — igual que los
 * "colores recientes/favoritos" de cualquier editor de imagen — así que
 * están disponibles en TODOS los proyectos, no solo en el que estaba
 * abierto cuando se guardó el color.
 *
 * Cada entrada puede ser un color SÓLIDO o un DEGRADADO de dos colores —
 * lo que estuviera activo en el diálogo al momento de guardar. Guardar un
 * degradado y que la miniatura de "Guardados" lo muestre aplanado a un
 * solo tono sería mentirle al usuario sobre qué guardó, por eso
 * [SavedColorEntry] conserva el tipo completo.
 */
object UserColorPrefs {
    private const val PREFS_NAME = "olyze_user_colors"
    private const val KEY_SAVED_COLORS = "saved_colors_v2"
    private const val KEY_RECENT_COLORS = "recent_colors_v1"

    // Tope razonable: pasado esto, la fila de "Guardados" deja de ser un
    // atajo rápido de un vistazo y empieza a scrollear sin fin. Al llegar
    // al tope, guardar uno nuevo descarta el más viejo (los recientes
    // importan más que el historial completo).
    private const val MAX_SAVED_COLORS = 20
    // "Recientes" es más chico y más volátil que "Guardados" (ese es
    // manual, a propósito; este se llena solo) — con 10 alcanza para dar
    // contexto de "en qué estuve trabajando último" sin saturar la fila.
    private const val MAX_RECENT_COLORS = 10

    /**
     * Una entrada guardada: o bien [colorArgb] (sólido), o bien
     * [gradientStartArgb]/[gradientEndArgb] (degradado) — nunca ambos a
     * la vez. [isGradient] evita tener que adivinar cuál caso es por
     * cuáles campos son null.
     */
    data class SavedColorEntry(
        val isGradient: Boolean,
        val colorArgb: Int? = null,
        val gradientStartArgb: Int? = null,
        val gradientEndArgb: Int? = null
    ) {
        /** Serializa a un token de una línea: "S:<argb>" o "G:<start>:<end>". */
        fun encode(): String = if (isGradient) {
            "G:$gradientStartArgb:$gradientEndArgb"
        } else {
            "S:$colorArgb"
        }

        companion object {
            fun solid(argb: Int) = SavedColorEntry(isGradient = false, colorArgb = argb)
            fun gradient(startArgb: Int, endArgb: Int) =
                SavedColorEntry(isGradient = true, gradientStartArgb = startArgb, gradientEndArgb = endArgb)

            /** Decodifica un token de [encode]; null si el token está corrupto/es de un formato viejo desconocido. */
            fun decode(token: String): SavedColorEntry? {
                val parts = token.split(":")
                return when {
                    parts.size == 2 && parts[0] == "S" -> parts[1].toIntOrNull()?.let { solid(it) }
                    parts.size == 3 && parts[0] == "G" -> {
                        val start = parts[1].toIntOrNull()
                        val end = parts[2].toIntOrNull()
                        if (start != null && end != null) gradient(start, end) else null
                    }
                    else -> null
                }
            }
        }
    }

    fun loadSavedColors(context: Context): List<SavedColorEntry> = loadEntries(context, KEY_SAVED_COLORS)

    /** Guarda un color sólido como el más reciente (al frente de la lista) y devuelve la lista actualizada. */
    fun addSavedColor(context: Context, colorArgb: Int): List<SavedColorEntry> =
        addEntry(context, KEY_SAVED_COLORS, MAX_SAVED_COLORS, SavedColorEntry.solid(colorArgb))

    /** Guarda un degradado de dos colores como el más reciente y devuelve la lista actualizada. */
    fun addSavedGradient(context: Context, startArgb: Int, endArgb: Int): List<SavedColorEntry> =
        addEntry(context, KEY_SAVED_COLORS, MAX_SAVED_COLORS, SavedColorEntry.gradient(startArgb, endArgb))

    /** Quita [entry] de "Guardados" (mantener presionado un color guardado) y devuelve la lista actualizada. */
    fun removeSavedEntry(context: Context, entry: SavedColorEntry): List<SavedColorEntry> =
        removeEntry(context, KEY_SAVED_COLORS, entry)

    // --- "Recientes": a diferencia de "Guardados" (manual, a propósito),
    // esto se llena SOLO cada vez que el usuario aplica un color/degradado
    // — sin que tenga que acordarse de tocar "guardar" — igual criterio
    // que los "colores usados recientemente" de cualquier editor de
    // imagen profesional (Photoshop, Procreate, Figma). ---

    fun loadRecentColors(context: Context): List<SavedColorEntry> = loadEntries(context, KEY_RECENT_COLORS)

    /** Registra un color sólido recién aplicado. Se llama automáticamente al tocar "Aplicar" con el modo sólido activo. */
    fun recordRecentColor(context: Context, colorArgb: Int): List<SavedColorEntry> =
        addEntry(context, KEY_RECENT_COLORS, MAX_RECENT_COLORS, SavedColorEntry.solid(colorArgb))

    /** Registra un degradado recién aplicado. Se llama automáticamente al tocar "Aplicar" con el degradado activo. */
    fun recordRecentGradient(context: Context, startArgb: Int, endArgb: Int): List<SavedColorEntry> =
        addEntry(context, KEY_RECENT_COLORS, MAX_RECENT_COLORS, SavedColorEntry.gradient(startArgb, endArgb))

    /** Quita [entry] de "Recientes" (mantener presionado) y devuelve la lista actualizada. */
    fun removeRecentEntry(context: Context, entry: SavedColorEntry): List<SavedColorEntry> =
        removeEntry(context, KEY_RECENT_COLORS, entry)

    // --- Implementación compartida entre "Guardados" y "Recientes": las
    // dos son, en el fondo, la misma lista-de-tokens-en-SharedPreferences
    // con un tope distinto, así que no tiene sentido duplicar el código
    // de leer/escribir/deduplicar para cada una. ---

    private fun loadEntries(context: Context, key: String): List<SavedColorEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(key, null) ?: return emptyList()
        return raw.split(",").mapNotNull { SavedColorEntry.decode(it.trim()) }
    }

    private fun persist(context: Context, key: String, entries: List<SavedColorEntry>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, entries.joinToString(",") { it.encode() }).apply()
    }

    private fun addEntry(context: Context, key: String, maxSize: Int, entry: SavedColorEntry): List<SavedColorEntry> {
        val current = loadEntries(context, key).toMutableList()
        current.removeAll { it == entry } // si ya estaba, lo saca de su posición vieja...
        current.add(0, entry) // ...y lo vuelve a poner primero (más reciente = más visible)
        val trimmed = current.take(maxSize)
        persist(context, key, trimmed)
        return trimmed
    }

    private fun removeEntry(context: Context, key: String, entry: SavedColorEntry): List<SavedColorEntry> {
        val current = loadEntries(context, key).toMutableList()
        current.removeAll { it == entry }
        persist(context, key, current)
        return current
    }
}
