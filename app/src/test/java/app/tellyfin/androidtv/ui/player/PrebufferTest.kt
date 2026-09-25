package app.tellyfin.androidtv.ui.player

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PrebufferTest {

    private val channelA = UUID.randomUUID()
    private val channelB = UUID.randomUUID()

    @Test
    fun `countdown is longer with prebuffering so there is time to preload after the reading pause`() {
        assertEquals(5_000L, Prebuffer.countdownMs(enabled = true))
        assertEquals(3_000L, Prebuffer.countdownMs(enabled = false))
        assertEquals(1_000L, Prebuffer.START_DELAY_MS)
        assertEquals(4_000L, Prebuffer.countdownMs(enabled = true) - Prebuffer.START_DELAY_MS)
    }

    @Test
    fun `a failed preload followed by that channel playing fine means the server cannot do two streams`() {
        val tracker = PrebufferFailureTracker()
        tracker.onPreloadFailed(channelA)
        tracker.onSwitch(channelA)
        assertTrue(tracker.onPlaybackReady())
    }

    @Test
    fun `a channel that fails to play on its own too does not count against prebuffering`() {
        val tracker = PrebufferFailureTracker()
        tracker.onPreloadFailed(channelA)
        tracker.onSwitch(channelA)
        tracker.onPlaybackFailed()
        assertFalse(tracker.onPlaybackReady())
    }

    @Test
    fun `switching to a different channel forgets the failed preload`() {
        val tracker = PrebufferFailureTracker()
        tracker.onPreloadFailed(channelA)
        tracker.onSwitch(channelB)
        assertFalse(tracker.onPlaybackReady())
    }

    @Test
    fun `playback becoming ready without any failed preload never disables`() {
        val tracker = PrebufferFailureTracker()
        tracker.onSwitch(channelA)
        assertFalse(tracker.onPlaybackReady())
    }

    @Test
    fun `the verdict is only reported once`() {
        val tracker = PrebufferFailureTracker()
        tracker.onPreloadFailed(channelA)
        tracker.onSwitch(channelA)
        assertTrue(tracker.onPlaybackReady())
        assertFalse(tracker.onPlaybackReady(), "rebuffering later must not re-trigger")
    }

    @Test
    fun `a preload failure only counts once playback of that channel was actually attempted`() {
        val tracker = PrebufferFailureTracker()
        tracker.onPreloadFailed(channelA)
        // The current channel re-buffering after the failed preload is unrelated evidence.
        assertFalse(tracker.onPlaybackReady())
    }
}
