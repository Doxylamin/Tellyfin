package app.tellyfin.androidtv.ui.player

/**
 * Tracks whether the app is in the foreground and decides what playback should do
 * across those transitions. Kept free of Android dependencies so it can be unit-tested
 * on the JVM.
 *
 * Live TV has no meaningful pause point, and Android reclaims the hardware video
 * decoder from backgrounded apps, so playback is torn down on the way out and
 * re-prepared from the live edge on the way back in.
 */
class PlaybackLifecycle {

    /** False between onStop and the next onStart. */
    var isForeground: Boolean = true
        private set

    private var resumeOnForeground = false

    /** @return true if the caller should tear playback down. */
    fun onBackground(isPlaying: Boolean): Boolean {
        if (!isForeground) return false
        isForeground = false
        resumeOnForeground = isPlaying
        return isPlaying
    }

    /** @return true if the caller should re-prepare the channel it was playing. */
    fun onForeground(): Boolean {
        if (isForeground) return false
        isForeground = true
        return resumeOnForeground.also { resumeOnForeground = false }
    }
}
