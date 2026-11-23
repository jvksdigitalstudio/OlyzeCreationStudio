package com.yeivikas.olyzecs.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope

/**
 * Adaptador de Compose para [ProgressiveRenderScheduler]: ata su ciclo de vida al de la ventana que lo usa.
 *
 * - Se crea de nuevo cuando cambian las [sources] (p. ej. la capa cambió de archivo y hay que re-decodificar).
 * - Al sustituirse el orquestador (cambió su base) lo pendiente se cancela.
 * - Al SALIR de la composición (se cerró la ventana) lo pendiente se cancela, pero un guardado que aún no se había
 *   ejecutado se vuelca en [persistScope] para no perderlo.
 * - Los lambdas se leen SIEMPRE en su versión más reciente (`rememberUpdatedState`): el orquestador sobrevive a
 *   recomposiciones, pero [render]/[onPreview]/[onFinal] pueden capturar valores que cambian.
 *
 * [persistScope] debe sobrevivir a la ventana (en la app, `viewModel.viewModelScope`).
 *
 * Contrato de [render]: devolver siempre un bitmap NUEVO, jamás el de [sources] (o `null` si no hay nada que renderizar). Lo que se publica pasa a ser de
 * quien lo recibe (la GPU lo recicla tras subirlo) y reciclar una fuente la destruiría.
 */
@Composable
fun <B : Any> rememberProgressiveRenderScheduler(
    sources: EffectSources<B>,
    persistScope: CoroutineScope,
    render: suspend (source: B, tier: RenderTier) -> B?,
    onPreview: (B) -> Unit,
    onFinal: (B) -> Unit
): ProgressiveRenderScheduler<B> {
    val scope = rememberCoroutineScope()
    val latestRender by rememberUpdatedState(render)
    val latestOnPreview by rememberUpdatedState(onPreview)
    val latestOnFinal by rememberUpdatedState(onFinal)
    val scheduler = remember(sources) {
        ProgressiveRenderScheduler(
            scope = scope,
            sources = sources,
            render = { source, tier -> latestRender(source, tier) },
            onPreview = { latestOnPreview(it) },
            onFinal = { latestOnFinal(it) }
        )
    }
    DisposableEffect(scheduler) {
        onDispose { scheduler.cancelPending() }
    }
    // Solo al SALIR de la composición (no al cambiar de orquestador): ahí la base sigue siendo la misma y el guardado vale.
    val latestScheduler by rememberUpdatedState(scheduler)
    DisposableEffect(Unit) {
        onDispose { latestScheduler.flushPendingCommit(persistScope) }
    }
    return scheduler
}
