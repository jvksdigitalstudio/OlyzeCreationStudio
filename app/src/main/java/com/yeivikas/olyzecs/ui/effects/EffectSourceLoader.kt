package com.yeivikas.olyzecs.ui.effects

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Carga las versiones lentas de una imagen en [EffectSources]. Solo orquesta la carga: CÓMO se decodifica lo
 * decide quien llama con [decode], así no depende de Android y se prueba en JVM.
 */
object EffectSourceLoader {

    /** Sin límite de tamaño: la resolución real del archivo. */
    const val NO_LIMIT = Int.MAX_VALUE

    /** Se informa como tamaño de un fallo ocurrido ANTES de saber qué tamaño se iba a decodificar. */
    const val UNKNOWN_SIZE = -1

    /**
     * Decodifica en paralelo la copia de reposo y la imagen completa. La copia rápida la publica quien llama antes
     * (suele necesitarla para otras cosas) con [EffectSources.publishQuick].
     *
     * [displayDecodeLimit] es el LÍMITE que se pasa a [decode] para la copia de reposo, no el tamaño objetivo: ver
     * [EffectsPreviewSizing.decodeLimitFor] (el decodificador solo reduce en potencias de dos).
     *
     * Las dos decodificaciones son INDEPENDIENTES: que falle la copia de pantalla (opcional) no cancela la imagen completa
     * (de la que sale lo que se guarda), ni al revés. Un fallo no cancelatorio deja ese resultado en `null` y se informa a
     * [onDecodeFailure]; la cancelación sí se propaga.
     *
     * Aunque esta carga se cancele a medias (se cerró el modo edición), la imagen completa queda resuelta como
     * `null`: quien espere por ella se libera y no confirma nada, en vez de colgarse.
     */
    suspend fun <B : Any> loadRemaining(
        sources: EffectSources<B>,
        displayDecodeLimit: suspend () -> Int,
        onDecodeFailure: (maxDimension: Int, error: Exception) -> Unit,
        decode: suspend (maxDimension: Int) -> B?
    ) {
        try {
            coroutineScope {
                launch { sources.publishDisplay(decodeOrNull(displayDecodeLimit, onDecodeFailure, decode)) }
                launch { sources.completeFull(decodeOrNull({ NO_LIMIT }, onDecodeFailure, decode)) }
            }
        } finally {
            sources.completeFull(null)
        }
    }

    private suspend fun <B : Any> decodeOrNull(
        maxDimension: suspend () -> Int,
        onDecodeFailure: (maxDimension: Int, error: Exception) -> Unit,
        decode: suspend (Int) -> B?
    ): B? {
        var requested = UNKNOWN_SIZE
        return try {
            requested = maxDimension()
            decode(requested)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onDecodeFailure(requested, failure)
            null
        }
    }
}
