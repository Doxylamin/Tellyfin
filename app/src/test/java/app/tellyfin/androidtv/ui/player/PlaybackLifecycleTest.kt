package app.tellyfin.androidtv.ui.player

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PlaybackLifecycleTest {

    @Test
    fun `backgrounding while playing tears playback down`() {
        val lifecycle = PlaybackLifecycle()
        assertTrue(lifecycle.onBackground(isPlaying = true))
    }

    @Test
    fun `backgrounding while on the home screen does nothing`() {
        val lifecycle = PlaybackLifecycle()
        assertFalse(lifecycle.onBackground(isPlaying = false))
        assertFalse(lifecycle.onForeground(), "must not start playing on return")
    }

    @Test
    fun `returning after backgrounding mid-playback resumes`() {
        val lifecycle = PlaybackLifecycle()
        lifecycle.onBackground(isPlaying = true)
        assertTrue(lifecycle.onForeground())
    }

    @Test
    fun `the initial onStart does not trigger a resume`() {
        val lifecycle = PlaybackLifecycle()
        assertFalse(lifecycle.onForeground())
    }

    @Test
    fun `the resume is consumed so a later onStart does not replay it`() {
        val lifecycle = PlaybackLifecycle()
        lifecycle.onBackground(isPlaying = true)
        assertTrue(lifecycle.onForeground())
        assertFalse(lifecycle.onForeground())
    }

    @Test
    fun `a repeated onStop does not clobber the pending resume`() {
        val lifecycle = PlaybackLifecycle()
        assertTrue(lifecycle.onBackground(isPlaying = true))
        // isPlaying is held true across the background, but a second teardown must
        // neither re-report nor reset the flag that drives the resume.
        assertFalse(lifecycle.onBackground(isPlaying = true))
        assertTrue(lifecycle.onForeground())
    }

    @Test
    fun `foreground flag tracks visibility so backgrounded stream errors are ignored`() {
        val lifecycle = PlaybackLifecycle()
        assertTrue(lifecycle.isForeground)
        lifecycle.onBackground(isPlaying = true)
        assertFalse(lifecycle.isForeground, "decoder teardown must not be retried")
        lifecycle.onForeground()
        assertTrue(lifecycle.isForeground)
    }
}
