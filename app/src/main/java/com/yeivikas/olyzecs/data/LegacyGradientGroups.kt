package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.color.LegacyGradientMigration
import com.yeivikas.olyzecs.engine.color.LegacyGradientRow
import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.engine.timeline.playlistOrder
import java.util.UUID

/** Capas y carriles de audio de un proyecto, ya con los colores migrados. */
internal data class MigratedProjectColors(
    val layers: List<Layer>,
    val audioTracks: List<AudioTrack>
)

/**
 * Aplica al abrir un proyecto la migración ÚNICA de los degradados
 * multicapa del modelo anterior (ver [LegacyGradientMigration]): las filas
 * que formaban un grupo deducido al dibujar pasan a guardar su color sólido
 * y su pertenencia al grupo, con el mismo aspecto que tenían.
 *
 * Un proyecto sin grupos antiguos (o ya migrado y guardado) se devuelve
 * tal cual. Las capas migradas se modifican en el mismo objeto; los carriles
 * de audio, que son inmutables, se sustituyen por una copia.
 */
internal fun migrateLegacyGradientGroups(
    layers: List<Layer>,
    audioTracks: List<AudioTrack>,
    newGroupId: () -> String = { UUID.randomUUID().toString() }
): MigratedProjectColors {
    // Mismo orden visual que el timeline: capas y carriles juntos, por orden
    // de playlist (el sort es estable: a igual orden, primero las capas).
    val rowsInPlaylistOrder = (
        layers.sortedBy { it.playlistOrder }.map { it.playlistOrder.toDouble() to it.toLegacyGradientRow() } +
            audioTracks.map { it.playlistOrder.toDouble() to it.toLegacyGradientRow() }
        ).sortedBy { it.first }.map { it.second }

    val migratedById = LegacyGradientMigration.migrate(rowsInPlaylistOrder, newGroupId).associateBy { it.id }
    if (migratedById.isEmpty()) return MigratedProjectColors(layers, audioTracks)

    // Las capas se modifican EN SU SITIO, no con copy(): el cargador ya dejó el
    // bitmap decodificado dentro de cada Layer (pendingBitmap) y una copia
    // nueva lo perdería. Sus campos de color son `var`.
    layers.forEach { layer ->
        migratedById[layer.id]?.let {
            layer.customColorArgb = it.sampledArgb
            layer.useGradientColor = false
            layer.gradientGroup = it.group
        }
    }
    return MigratedProjectColors(
        layers = layers,
        audioTracks = audioTracks.map { track ->
            migratedById[track.id]?.let {
                track.copy(customColorArgb = it.sampledArgb, useGradientColor = false, gradientGroup = it.group)
            } ?: track
        }
    )
}

private fun Layer.toLegacyGradientRow() = LegacyGradientRow(
    id = id,
    useGradient = useGradientColor,
    startArgb = customGradientStartArgb,
    endArgb = customGradientEndArgb,
    angleDegrees = gradientAngleDegrees,
    isRadial = gradientIsRadial
)

private fun AudioTrack.toLegacyGradientRow() = LegacyGradientRow(
    id = id,
    useGradient = useGradientColor,
    startArgb = customGradientStartArgb,
    endArgb = customGradientEndArgb,
    angleDegrees = gradientAngleDegrees,
    isRadial = gradientIsRadial
)
