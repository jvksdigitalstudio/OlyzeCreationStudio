package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reflejo de la selección al pulsar REV ([mirrorDetailSelection]): pura, sin Compose. */
class AudioClipDetailSelectionMirrorTest {

    @Test
    fun mirror_reflects_around_file_center() {
        val m = mirrorDetailSelection(DetailSelection(1_000.0, 1_500.0), 3_420.0)!!
        assertEquals(1_920.0, m.startMs, 1e-9)
        assertEquals(2_420.0, m.endMs, 1e-9)
    }

    @Test
    fun mirror_is_its_own_inverse() {
        val original = DetailSelection(123.4, 2_345.6)
        val back = mirrorDetailSelection(mirrorDetailSelection(original, 3_420.0)!!, 3_420.0)!!
        assertEquals(original.startMs, back.startMs, 1e-9)
        assertEquals(original.endMs, back.endMs, 1e-9)
    }

    @Test
    fun mirror_clamps_and_rejects_invalid_duration() {
        val m = mirrorDetailSelection(DetailSelection(0.0, 4_000.0), 3_420.0)!!
        assertEquals(0.0, m.startMs, 1e-9)
        assertEquals(3_420.0, m.endMs, 1e-9)
        assertNull(mirrorDetailSelection(DetailSelection(0.0, 10.0), 0.0))
    }
}
