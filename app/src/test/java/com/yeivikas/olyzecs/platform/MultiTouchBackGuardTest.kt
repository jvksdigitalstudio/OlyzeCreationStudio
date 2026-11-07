package com.yeivikas.olyzecs.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTouchBackGuardTest {

    private val window = MultiTouchBackGuard.SUPPRESSION_WINDOW_MS

    @Test
    fun `sin pellizco previo nunca suprime`() {
        assertFalse(MultiTouchBackGuard.isSuppressed(5_000L, Long.MIN_VALUE))
    }

    @Test
    fun `atras justo despues del pellizco se suprime`() {
        assertTrue(MultiTouchBackGuard.isSuppressed(10_000L, 10_000L))
        assertTrue(MultiTouchBackGuard.isSuppressed(10_000L + window, 10_000L))
    }

    @Test
    fun `atras pasada la ventana funciona normal`() {
        assertFalse(MultiTouchBackGuard.isSuppressed(10_000L + window + 1, 10_000L))
    }

    @Test
    fun `reloj anterior al pellizco no suprime`() {
        assertFalse(MultiTouchBackGuard.isSuppressed(9_000L, 10_000L))
    }

    @Test
    fun `reset olvida el pellizco`() {
        MultiTouchBackGuard.reset()
        assertFalse(MultiTouchBackGuard.shouldSuppressBack(0L))
    }
}
