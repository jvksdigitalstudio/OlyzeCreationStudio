package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.audio.AudioSourceState
import kotlinx.coroutines.delay

/**
 * Cuánto debe durar "Preparando" antes de mostrarse. Casi siempre es una fracción de segundo (un tema corto); mostrarlo al instante haría
 * parpadear una insignia en cada clip que se importa o se abre. Un "reintentando" o un fallo, en cambio, se muestran al momento: ya
 * ocurrió algo que el usuario debe saber.
 */
internal const val SOURCE_BADGE_DELAY_MS = 400L

/** Por debajo de este ancho de clip la insignia muestra solo el ícono, sin texto. */
internal val SOURCE_BADGE_COMPACT_WIDTH = 96.dp

private val BADGE_HEIGHT = 14.dp
private val PREPARING_BACKGROUND = Color.Black.copy(alpha = 0.62f)
private val RETRYING_BACKGROUND = Color(0xFF8A5A00)
private val FAILED_BACKGROUND = Color(0xFFB3261E)

/**
 * Insignia que explica por qué un clip aún no suena: el audio se está preparando, reintentando tras un fallo, o falló del todo. Sin ella,
 * un clip cuya fuente no se pudo preparar quedaba mudo sin ningún motivo visible.
 *
 * - [AudioSourceState.Preparing]: ícono giratorio y "Preparando", tras [SOURCE_BADGE_DELAY_MS].
 * - [AudioSourceState.Retrying]: lo mismo en ámbar, con el número de intento.
 * - [AudioSourceState.Failed]: rojo, con "Reintentar". TODA la insignia (con un margen táctil alrededor) reintenta al tocarla; el resto
 *   del clip conserva sus gestos.
 * - `null`: no dibuja nada (la fuente está lista, o el clip está silenciado).
 *
 * @param compact solo ícono, para clips estrechos.
 */
@Composable
internal fun AudioSourceStatusBadge(
    state: AudioSourceState?,
    compact: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preparingVisible by produceState(initialValue = false, key1 = state is AudioSourceState.Preparing) {
        value = false
        if (state is AudioSourceState.Preparing) {
            delay(SOURCE_BADGE_DELAY_MS)
            value = true
        }
    }

    when (state) {
        null -> Unit
        is AudioSourceState.Preparing -> if (preparingVisible) {
            StatusPill(
                text = stringResource(R.string.audio_source_preparing),
                description = stringResource(R.string.audio_source_preparing_desc),
                compact = compact,
                background = PREPARING_BACKGROUND,
                leading = { Spinner() },
                modifier = modifier.padding(4.dp)
            )
        }
        is AudioSourceState.Retrying -> StatusPill(
            text = stringResource(R.string.audio_source_retrying, state.attempt, state.maxAttempts),
            description = stringResource(R.string.audio_source_retrying_desc, state.attempt, state.maxAttempts),
            compact = compact,
            background = RETRYING_BACKGROUND,
            leading = { Spinner() },
            modifier = modifier.padding(4.dp)
        )
        is AudioSourceState.Failed -> Box(
            // Primero el toque y después el margen: el área táctil incluye el margen (la insignia sola mide 14 dp).
            modifier = modifier
                .clickable(onClickLabel = stringResource(R.string.audio_source_retry_action), onClick = onRetry)
                .padding(6.dp)
        ) {
            StatusPill(
                text = stringResource(R.string.audio_source_failed_retry),
                description = stringResource(R.string.audio_source_failed_desc),
                compact = compact,
                background = FAILED_BACKGROUND,
                leading = { FailedMark() }
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    description: String,
    compact: Boolean,
    background: Color,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(BADGE_HEIGHT)
            .background(background, RoundedCornerShape(BADGE_HEIGHT / 2))
            .padding(horizontal = if (compact) 3.dp else 5.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        leading()
        if (!compact) {
            Text(
                text = text,
                color = Color.White,
                // `labelSmall` trae lineHeight = 16.sp: con 8.sp el texto flotaría en una caja más alta que la insignia.
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(modifier = Modifier.size(8.dp), color = Color.White, strokeWidth = 1.2.dp)
}

/** Círculo blanco con "!": se lee igual sobre cualquier color de pista, sin depender de un ícono de recursos. */
@Composable
private fun FailedMark() {
    Box(modifier = Modifier.size(9.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            text = "!",
            color = FAILED_BACKGROUND,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold)
        )
    }
}
