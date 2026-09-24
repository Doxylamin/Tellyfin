package app.tellyfin.androidtv.data.api

import kotlin.test.assertEquals
import org.junit.Test

class CandidateServerUrlsTest {

    @Test
    fun `a bare host tries jellyfin's default ports on both schemes, then plain https and http`() {
        assertEquals(
            listOf(
                "https://tv.example.com:8920",
                "http://tv.example.com:8096",
                "https://tv.example.com",
                "http://tv.example.com"
            ),
            candidateServerUrls("tv.example.com")
        )
    }

    @Test
    fun `a scheme means the address is specific, so only that exact url is tried`() {
        assertEquals(listOf("https://tv.example.com"), candidateServerUrls("https://tv.example.com"))
        assertEquals(listOf("http://tv.example.com:8096"), candidateServerUrls("http://tv.example.com:8096"))
    }

    @Test
    fun `an explicit port without a scheme tries both schemes on that exact port`() {
        assertEquals(
            listOf("https://tv.example.com:9999", "http://tv.example.com:9999"),
            candidateServerUrls("tv.example.com:9999")
        )
    }

    @Test
    fun `surrounding whitespace and a trailing slash do not affect candidate generation`() {
        assertEquals(
            listOf(
                "https://tv.example.com:8920",
                "http://tv.example.com:8096",
                "https://tv.example.com",
                "http://tv.example.com"
            ),
            candidateServerUrls("  tv.example.com/  ")
        )
    }

    @Test
    fun `bare ip addresses are expanded the same as hostnames`() {
        assertEquals(
            listOf(
                "https://192.168.1.10:8920",
                "http://192.168.1.10:8096",
                "https://192.168.1.10",
                "http://192.168.1.10"
            ),
            candidateServerUrls("192.168.1.10")
        )
    }
}
