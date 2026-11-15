package dev.mediamanager.mobile.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModesTest {

    @Test
    fun `repeat cycles off to all to one and back`() {
        assertEquals(Player.REPEAT_MODE_ALL, nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextRepeatMode(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `labels match the mode`() {
        assertEquals("Repeat off", repeatLabel(Player.REPEAT_MODE_OFF))
        assertEquals("Repeat all", repeatLabel(Player.REPEAT_MODE_ALL))
        assertEquals("Repeat one", repeatLabel(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `speed labels drop the decimal for whole numbers`() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.5×", speedLabel(1.5f))
    }
}
