package com.yeivikas.olyzecs.ui.effects

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EffectSourceLoaderTest {

    private val failures = mutableListOf<Pair<Int, String>>()
    private val onFailure: (Int, Exception) -> Unit = { size, error -> failures += size to (error.message ?: "") }

    @Test
    fun `carga la copia de pantalla y la imagen completa con los tamanos pedidos`() = runTest {
        val sources = EffectSources<String>()
        val requested = mutableListOf<Int>()
        EffectSourceLoader.loadRemaining(sources, displayDecodeLimit = { 1440 }, onDecodeFailure = onFailure, decode = { requested += it; "img@$it" })
        assertEquals("img@1440", sources.display)
        assertEquals("img@${EffectSourceLoader.NO_LIMIT}", sources.awaitFull())
        assertEquals(setOf(1440, EffectSourceLoader.NO_LIMIT), requested.toSet())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `si la decodificacion devuelve null la imagen completa queda en null y nadie se cuelga`() = runTest {
        val sources = EffectSources<String>()
        EffectSourceLoader.loadRemaining(sources, displayDecodeLimit = { 1440 }, onDecodeFailure = onFailure, decode = { null })
        assertNull(sources.awaitFull())
    }

    @Test
    fun `si falla la copia de pantalla la imagen completa se carga igual`() = runTest {
        val sources = EffectSources<String>()
        EffectSourceLoader.loadRemaining(
            sources, displayDecodeLimit = { 1440 }, onDecodeFailure = onFailure,
            decode = { size -> if (size == 1440) error("sin memoria para la copia de pantalla") else "completa" }
        )
        assertNull(sources.display)
        assertEquals("completa", sources.awaitFull())
        assertEquals(listOf(1440 to "sin memoria para la copia de pantalla"), failures)
    }

    @Test
    fun `si falla la imagen completa la copia de pantalla se conserva y el fallo se informa`() = runTest {
        val sources = EffectSources<String>()
        EffectSourceLoader.loadRemaining(
            sources, displayDecodeLimit = { 1440 }, onDecodeFailure = onFailure,
            decode = { size -> if (size == EffectSourceLoader.NO_LIMIT) error("archivo ilegible") else "pantalla" }
        )
        assertEquals("pantalla", sources.display)
        assertNull("sin imagen completa no se guarda nada", sources.awaitFull())
        assertEquals(listOf(EffectSourceLoader.NO_LIMIT to "archivo ilegible"), failures)
    }

    @Test
    fun `un fallo al calcular el tamano de pantalla tampoco afecta a la imagen completa`() = runTest {
        val sources = EffectSources<String>()
        EffectSourceLoader.loadRemaining(
            sources, displayDecodeLimit = { error("pantalla sin medir") }, onDecodeFailure = onFailure, decode = { "img" }
        )
        assertNull(sources.display)
        assertEquals("img", sources.awaitFull())
        assertEquals("el fallo no se atribuye a ningún tamaño de decodificación", listOf(EffectSourceLoader.UNKNOWN_SIZE), failures.map { it.first })
    }

    @Test
    fun `si la carga se cancela a medias quien espera la imagen completa se libera con null`() = runTest {
        val sources = EffectSources<String>()
        val never = CompletableDeferred<String?>()
        val job = launch {
            EffectSourceLoader.loadRemaining(sources, displayDecodeLimit = { 1440 }, onDecodeFailure = onFailure, decode = { never.await() })
        }
        runCurrent()
        job.cancel()
        advanceUntilIdle()
        assertNull(sources.awaitFull())
        assertTrue("cancelar no es un fallo de decodificación", failures.isEmpty())
    }
}
