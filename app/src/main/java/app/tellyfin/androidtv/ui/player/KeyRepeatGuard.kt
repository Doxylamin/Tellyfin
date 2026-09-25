package app.tellyfin.androidtv.ui.player

import android.os.SystemClock

/**
 * Right after a button is bound, the user is usually still holding it — and its auto-repeats
 * would immediately fire the action it was just bound to (e.g. Search, which leaves Settings).
 * This swallows that button until it has been quiet for [QUIET_MS], i.e. released.
 */
class KeyRepeatGuard(private val now: () -> Long = SystemClock::uptimeMillis) {
    private var keyCode: Int? = null
    private var quietUntil = 0L

    fun arm(keyCode: Int) {
        this.keyCode = keyCode
        quietUntil = now() + QUIET_MS
    }

    fun shouldSwallow(keyCode: Int): Boolean {
        if (keyCode != this.keyCode) return false
        val time = now()
        if (time > quietUntil) {
            this.keyCode = null
            return false
        }
        // Still repeating, so still held: keep waiting for it to go quiet.
        quietUntil = time + QUIET_MS
        return true
    }

    companion object {
        // Longer than the gap between repeats (~50 ms), and than the initial repeat delay (~500 ms).
        const val QUIET_MS = 750L
    }
}
