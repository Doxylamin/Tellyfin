package app.tellyfin.androidtv.data.api

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class StreamUrlTest {

    private val channelId = UUID.fromString("00000000-0000-0000-0000-0000000000ab")

    @Test
    fun `auto leaves the choice to the server`() {
        assertEquals("?mediaSourceId=$channelId", buildStreamQuery(channelId, null))
    }

    @Test
    fun `a cap uses parameters the stream endpoint actually reads`() {
        val query = buildStreamQuery(channelId, 4_000_000)
        // /Videos/{id}/stream has no MaxStreamingBitrate parameter; it was silently ignored.
        assertFalse(query.contains("MaxStreamingBitrate"), query)
        assertTrue(query.contains("&videoBitRate="), query)
        assertTrue(query.contains("&audioBitRate="), query)
    }

    @Test
    fun `video and audio budgets add up to the requested cap`() {
        for (cap in listOf(2_000_000, 4_000_000, 8_000_000, 12_000_000, 20_000_000, 40_000_000)) {
            val query = buildStreamQuery(channelId, cap)
            val video = Regex("videoBitRate=(\\d+)").find(query)!!.groupValues[1].toInt()
            val audio = Regex("audioBitRate=(\\d+)").find(query)!!.groupValues[1].toInt()
            assertEquals(cap, video + audio, "budget split for $cap")
            assertTrue(video > audio, "video must keep the bulk of the budget at $cap")
        }
    }

    @Test
    fun `stream copy is refused so the cap actually binds`() {
        assertTrue(buildStreamQuery(channelId, 2_000_000).contains("allowVideoStreamCopy=false"))
    }
}
