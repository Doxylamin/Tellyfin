package app.tellyfin.androidtv.ui.player

import java.util.UUID

/**
 * Timing for the channel-switch preview banner. With pre-buffering on, the countdown gets two
 * extra seconds: the first [START_DELAY_MS] are for reading the banner (flicking past a channel
 * never touches a tuner), the rest is spent loading the stream so the switch starts instantly.
 * Jellyfin can take several seconds to open a tuner, so the loading window gets the lion's share.
 */
object Prebuffer {
    const val START_DELAY_MS = 1_000L
    private const val COUNTDOWN_MS = 3_000L
    private const val COUNTDOWN_WITH_PREBUFFER_MS = 5_000L

    fun countdownMs(enabled: Boolean): Long =
        if (enabled) COUNTDOWN_WITH_PREBUFFER_MS else COUNTDOWN_MS
}

/**
 * Decides when pre-buffering should switch itself off. A failed preload alone proves nothing
 * (the channel might just be broken), but a failed preload of a channel that then plays fine
 * once the previous stream is gone means the server couldn't serve both at once — typically a
 * single-tuner setup — so every future preload would just waste a request.
 */
class PrebufferFailureTracker {
    private var failedPreload: UUID? = null
    private var switchedToFailedPreload = false

    fun onPreloadFailed(channelId: UUID) {
        failedPreload = channelId
        switchedToFailedPreload = false
    }

    fun onSwitch(channelId: UUID) {
        switchedToFailedPreload = failedPreload == channelId
        if (!switchedToFailedPreload) failedPreload = null
    }

    /** Playback gave up for good (retries exhausted) — the channel itself is at fault. */
    fun onPlaybackFailed() = reset()

    /** @return true exactly once, when pre-buffering should be turned off. */
    fun onPlaybackReady(): Boolean {
        val verdict = switchedToFailedPreload
        reset()
        return verdict
    }

    private fun reset() {
        failedPreload = null
        switchedToFailedPreload = false
    }
}
