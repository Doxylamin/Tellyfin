package app.tellyfin.androidtv.data.api

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test

class ServerAuthTest {

    @After
    fun tearDown() {
        ServerAuth.clear()
    }

    @Test
    fun `configure extracts host and builds MediaBrowser header`() {
        ServerAuth.configure("https://jellyfin.example.com:8920", "secret-token")
        assertEquals("jellyfin.example.com", ServerAuth.serverHost)
        val header = ServerAuth.authHeader ?: error("header not set")
        assertTrue(header.startsWith("MediaBrowser "))
        assertTrue(header.contains("Token=\"secret-token\""))
    }

    @Test
    fun `token never appears outside the header value`() {
        ServerAuth.configure("https://jellyfin.example.com", "secret-token")
        // The host must not leak the token (guards against accidental URL-style storage)
        assertEquals("jellyfin.example.com", ServerAuth.serverHost)
    }

    @Test
    fun `clear removes host and header`() {
        ServerAuth.configure("https://jellyfin.example.com", "secret-token")
        ServerAuth.clear()
        assertNull(ServerAuth.serverHost)
        assertNull(ServerAuth.authHeader)
    }
}
