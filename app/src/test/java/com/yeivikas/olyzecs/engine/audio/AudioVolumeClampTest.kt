package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioVolumeClampTest {

    @Test
    fun valuesInsideTheRangeAreKept() {
        assertEquals(0f, clampAudioVolume(0f), 0f)
        assertEquals(0.5f, clampAudioVolume(0.5f), 0f)
        assertEquals(1f, clampAudioVolume(1f), 0f)
        assertEquals(MAX_AUDIO_VOLUME, clampAudioVolume(MAX_AUDIO_VOLUME), 0f)
    }

    @Test
    fun valuesOutsideTheRangeAreClamped() {
        assertEquals(0f, clampAudioVolume(-3f), 0f)
        assertEquals(MAX_AUDIO_VOLUME, clampAudioVolume(9f), 0f)
    }

    @Test
    fun nonFiniteFallsBackToOriginalVolume() {
        assertEquals(1f, clampAudioVolume(Float.NaN), 0f)
        assertEquals(1f, clampAudioVolume(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, clampAudioVolume(Float.NEGATIVE_INFINITY), 0f)
    }
}
