package com.yeivikas.olyzecs.engine.audio

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioInterruptionPolicyTest {
    @Test
    fun `perder el foco de audio pausa la reproduccion`() {
        assertTrue(AudioInterruptionPolicy.pausesPlayback(AudioManager.AUDIOFOCUS_LOSS))
        assertTrue(AudioInterruptionPolicy.pausesPlayback(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
    }

    @Test
    fun `una notificacion con atenuacion o recuperar el foco no la pausan`() {
        assertFalse(AudioInterruptionPolicy.pausesPlayback(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertFalse(AudioInterruptionPolicy.pausesPlayback(AudioManager.AUDIOFOCUS_GAIN))
    }
}
