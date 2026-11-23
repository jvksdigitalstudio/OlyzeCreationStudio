package com.yeivikas.olyzecs.engine.color

/**
 * Datos de degradado de una fila (capa o carril de audio) tal como los
 * guardaban los proyectos anteriores al degradado de grupo persistente.
 */
data class LegacyGradientRow(
    val id: String,
    val useGradient: Boolean,
    val startArgb: Int?,
    val endArgb: Int?,
    val angleDegrees: Float,
    val isRadial: Boolean
)

/** Resultado de migrar una fila que formaba parte de un grupo antiguo. */
data class MigratedGradientRow(
    val id: String,
    val sampledArgb: Int,
    val group: GradientGroupRef
)

/**
 * Migración ÚNICA de proyectos guardados con el modelo anterior.
 *
 * Antes, el "grupo" de un degradado multicapa no se guardaba: la UI lo
 * DEDUCÍA al dibujar, agrupando filas CONSECUTIVAS de la playlist con el
 * mismo degradado activo (mismos extremos, ángulo y forma; mínimo 2), y
 * le daba a la fila `k` de `n` el tono de la posición `(k + 0.5) / n`
 * interpolado en Oklab. Con el modelo nuevo cada fila dibuja un color
 * sólido guardado, así que al abrir un proyecto antiguo hay que fijar esos
 * mismos tonos: esta función reproduce la deducción antigua UNA vez y
 * devuelve el color sólido y el grupo de cada fila afectada.
 *
 * - Solo se migran las filas que pertenecían a un grupo (rachas de 2 o
 *   más). Una fila suelta con degradado propio no se toca.
 * - Es idempotente: tras aplicar el resultado las filas dejan de tener el
 *   degradado activo, así que una segunda pasada no encuentra nada.
 * - Mantiene el aspecto que se veía; no adopta el reparto nuevo
 *   `k / (n - 1)`. Ese solo se usa al volver a aplicar el degradado.
 */
object LegacyGradientMigration {

    /**
     * @param rowsInPlaylistOrder TODAS las filas (capas y audio) en el
     *   mismo orden en que se ven en el timeline.
     * @param newGroupId fábrica de identificadores; se llama una vez por
     *   grupo encontrado.
     */
    fun migrate(
        rowsInPlaylistOrder: List<LegacyGradientRow>,
        newGroupId: () -> String
    ): List<MigratedGradientRow> {
        val migrated = ArrayList<MigratedGradientRow>()
        var runStart = 0
        while (runStart < rowsInPlaylistOrder.size) {
            val first = rowsInPlaylistOrder[runStart]
            val start = first.startArgb
            val end = first.endArgb
            if (!first.useGradient || start == null || end == null) {
                runStart++
                continue
            }
            var runEnd = runStart
            while (runEnd < rowsInPlaylistOrder.size && sharesGradient(rowsInPlaylistOrder[runEnd], first)) {
                runEnd++
            }
            val runSize = runEnd - runStart
            if (runSize >= 2) {
                val group = GradientGroupRef(newGroupId(), GradientColorSpace.PERCEPTUAL)
                for (index in runStart until runEnd) {
                    val position = ((index - runStart) + 0.5) / runSize
                    migrated += MigratedGradientRow(
                        id = rowsInPlaylistOrder[index].id,
                        sampledArgb = GroupGradientEngine.sample(start, end, position, GradientColorSpace.PERCEPTUAL),
                        group = group
                    )
                }
            }
            runStart = runEnd
        }
        return migrated
    }

    private fun sharesGradient(row: LegacyGradientRow, reference: LegacyGradientRow): Boolean =
        row.useGradient &&
            row.startArgb == reference.startArgb &&
            row.endArgb == reference.endArgb &&
            row.angleDegrees == reference.angleDegrees &&
            row.isRadial == reference.isRadial
}
