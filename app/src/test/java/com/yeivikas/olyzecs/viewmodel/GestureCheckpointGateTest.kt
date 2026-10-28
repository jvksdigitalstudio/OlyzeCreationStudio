package com.yeivikas.olyzecs.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureCheckpointGateTest {

    private var now = 0L
    private val gate = GestureCheckpointGate(gapMs = 800L, clock = { now })

    @Test
    fun firstChangeOpensANewGesture() {
        assertTrue(gate.begin("volume:a"))
    }

    @Test
    fun continuousDragIsOneGestureNoMatterHowLong() {
        assertTrue(gate.begin("volume:a"))
        repeat(100) {
            now += 50L // 5 s de arrastre, mucho más que una ventana fija de 600 ms
            assertFalse(gate.begin("volume:a"))
        }
    }

    @Test
    fun pauseLongerThanGapOpensANewGesture() {
        assertTrue(gate.begin("pan:a"))
        now += 801L
        assertTrue(gate.begin("pan:a"))
    }

    @Test
    fun pauseExactlyAtGapStillContinues() {
        assertTrue(gate.begin("pan:a"))
        now += 800L
        assertFalse(gate.begin("pan:a"))
    }

    @Test
    fun differentParameterOrClipOpensANewGesture() {
        assertTrue(gate.begin("volume:a"))
        assertTrue(gate.begin("pan:a"))
        assertTrue(gate.begin("pan:b"))
    }

    @Test
    fun resetSeparatesTheNextChange() {
        assertTrue(gate.begin("trim:a"))
        gate.reset()
        assertTrue(gate.begin("trim:a"))
    }
}
