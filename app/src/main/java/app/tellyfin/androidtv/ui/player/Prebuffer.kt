package app.tellyfin.androidtv.ui.player

import java.util.UUID

/**
 * Timing for the channel-switch preview banner. With pre-buffering on, the countdown gets two
 * extra seconds: the first part (the start delay) is for reading the banner — flicking past a
 * channel never touches a tuner — the rest is spent loading the stream so the switch starts
 * instantly. Jellyfin can take several seconds to open a tuner, so loading gets the lion's share.
 * Both are user-adjustable under Settings → Advanced.
 */
object Prebuffer {
    const val DEFAULT_START_DELAY_MS = 1_000L
    /** Countdown setting meaning "5 s with pre-buffering, 3 s without". */
    const val COUNTDOWN_AUTO = 0L
    val START_DELAY_OPTIONS = listOf(500L, DEFAULT_START_DELAY_MS, 2_000L)
    val COUNTDOWN_OPTIONS = listOf(COUNTDOWN_AUTO, 3_000L, 5_000L, 7_000L)
    private const val COUNTDOWN_MS = 3_000L
    private const val COUNTDOWN_WITH_PREBUFFER_MS = 5_000L

    /** Stored values are only trusted if they're one we offer — anything else is a default. */
    fun sanitizeStartDelay(stored: Long?): Long =
        stored?.takeIf { it in START_DELAY_OPTIONS } ?: DEFAULT_START_DELAY_MS

    fun sanitizeCountdown(stored: Long?): Long =
        stored?.takeIf { it in COUNTDOWN_OPTIONS } ?: COUNTDOWN_AUTO

    fun countdownMs(enabled: Boolean, setting: Long = COUNTDOWN_AUTO): Long = when {
        setting != COUNTDOWN_AUTO -> setting
        enabled -> COUNTDOWN_WITH_PREBUFFER_MS
        else -> COUNTDOWN_MS
    }
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

/** What the preview banner's countdown ring shows about the next channel's preload. */
sealed interface PreloadStatus {
    data object None : PreloadStatus
    data class Loading(val channelId: UUID) : PreloadStatus
    data class Ready(val channelId: UUID) : PreloadStatus

    /**
     * Preload callbacks arrive asynchronously, so an event only applies to the preload it's
     * about — a late "ready" for one that was already cancelled or replaced is ignored.
     */
    fun after(event: PreloadEvent): PreloadStatus = when (event) {
        is PreloadEvent.Started -> Loading(event.channelId)
        is PreloadEvent.Ready -> if (this == Loading(event.channelId)) Ready(event.channelId) else this
        is PreloadEvent.Failed -> if (channelIdOrNull() == event.channelId) None else this
        PreloadEvent.Cleared -> None
    }

    private fun channelIdOrNull(): UUID? = when (this) {
        None -> null
        is Loading -> channelId
        is Ready -> channelId
    }
}

sealed interface PreloadEvent {
    data class Started(val channelId: UUID) : PreloadEvent
    data class Ready(val channelId: UUID) : PreloadEvent
    data class Failed(val channelId: UUID) : PreloadEvent
    data object Cleared : PreloadEvent
}
