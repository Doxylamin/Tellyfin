package app.tellyfin.androidtv.ui.player

import java.util.UUID
import kotlin.test.assertEquals
import org.junit.Test

class PreloadStatusTest {

    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `starting a preload shows it loading`() {
        assertEquals(PreloadStatus.Loading(a), PreloadStatus.None.after(PreloadEvent.Started(a)))
    }

    @Test
    fun `enough buffered turns it ready`() {
        assertEquals(PreloadStatus.Ready(a), PreloadStatus.Loading(a).after(PreloadEvent.Ready(a)))
    }

    @Test
    fun `a late ready for a cancelled or replaced preload is ignored`() {
        assertEquals(PreloadStatus.None, PreloadStatus.None.after(PreloadEvent.Ready(a)))
        assertEquals(PreloadStatus.Loading(b), PreloadStatus.Loading(b).after(PreloadEvent.Ready(a)))
    }

    @Test
    fun `failing or clearing goes back to plain`() {
        assertEquals(PreloadStatus.None, PreloadStatus.Loading(a).after(PreloadEvent.Failed(a)))
        assertEquals(PreloadStatus.None, PreloadStatus.Ready(a).after(PreloadEvent.Cleared))
    }

    @Test
    fun `a failure of some other preload leaves the current one alone`() {
        assertEquals(PreloadStatus.Loading(b), PreloadStatus.Loading(b).after(PreloadEvent.Failed(a)))
    }
}
