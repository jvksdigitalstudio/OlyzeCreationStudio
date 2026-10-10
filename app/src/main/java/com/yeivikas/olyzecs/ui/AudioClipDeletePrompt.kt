package com.yeivikas.olyzecs.ui

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.yeivikas.olyzecs.engine.audio.AudioTrack
import com.yeivikas.olyzecs.engine.audio.trackOfClip

/**
 * Texto del diálogo de confirmación al eliminar UN clip de audio desde su
 * menú contextual ("Eliminar").
 *
 * Función pura (sin Compose ni `Uri`) para poder cubrirla con JUnit
 * puro: el diálogo en sí vive en `EditorScreen` y solo resuelve el clip
 * (por id) y delega acá el mensaje.
 *
 * Reglas del mensaje:
 *  - Siempre nombra el clip y avisa qué se pierde junto con él (volumen,
 *    recorte, bucle y desvanecidos).
 *  - Si es el ÚNICO clip del carril, avisa que la capa de audio también
 *    desaparece (`EditorViewModel.removeAudioClip` no deja carriles vacíos).
 *  - Siempre aclara que se puede recuperar con Deshacer
 *    (`removeAudioClip` registra un checkpoint de undo).
 *
 * @param clipName nombre visible del clip (se muestra entre comillas).
 * @param clipsInTrack cantidad de clips que tiene hoy el carril, incluido
 *   el que se va a borrar.
 */
internal fun audioClipDeleteMessage(clipName: String, clipsInTrack: Int): String =
    "\"$clipName\" se va a borrar del proyecto junto con su volumen, " +
        "recorte, bucle y desvanecidos configurados." +
        (if (clipsInTrack <= 1) " Es el único clip de la capa, así que la capa de audio también desaparece." else "") +
        " Podés recuperarlo con Deshacer."

/**
 * Diálogo "¿Eliminar este clip de audio?" (Cancelar / Eliminar), mismo estilo
 * que los de eliminar capa y eliminar carril de audio.
 *
 * Si el clip [clipId] ya no existe (p. ej. se deshizo o se borró por otra vía)
 * no hay nada que confirmar: se descarta solo vía [onDismiss].
 *
 * @param onConfirm borra el clip y limpia el estado del llamador.
 */
@Composable
internal fun AudioClipDeleteDialog(
    clipId: String,
    audioTracks: List<AudioTrack>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val track = audioTracks.trackOfClip(clipId)
    val clip = track?.clip(clipId)
    if (track == null || clip == null) {
        LaunchedEffect(clipId) { onDismiss() }
        return
    }
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        title = { Text("¿Eliminar este clip de audio?") },
        text = { Text(audioClipDeleteMessage(clip.displayName, track.clips.size)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
