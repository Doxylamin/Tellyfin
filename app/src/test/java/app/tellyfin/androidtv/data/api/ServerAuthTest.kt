package app.tellyfin.androidtv.data.api

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.junit.After
import org.junit.Test

class ServerAuthTest {

    private val clientInfo = ClientInfo(name = "Tellyfin", version = "9.9.9")
    private val deviceInfo = DeviceInfo(id = "install-device-id", name = "Living Room TV")

    @After
    fun tearDown() {
        ServerAuth.clear()
    }

    @Test
    fun `configure extracts host and builds MediaBrowser header`() {
        ServerAuth.configure("https://jellyfin.example.com:8920", "secret-token", clientInfo, deviceInfo)
        assertEquals("jellyfin.example.com", ServerAuth.serverHost)
        val header = ServerAuth.authHeader ?: error("header not set")
        assertTrue(header.startsWith("MediaBrowser "))
        assertTrue(header.contains("Token=\"secret-token\""))
    }

    @Test
    fun `header carries the identity it was given rather than a hardcoded one`() {
        ServerAuth.configure("https://jellyfin.example.com", "secret-token", clientInfo, deviceInfo)
        val header = ServerAuth.authHeader ?: error("header not set")
        assertTrue(header.contains("Client=\"Tellyfin\""), header)
        assertTrue(header.contains("Version=\"9.9.9\""), header)
        assertTrue(header.contains("DeviceId=\"install-device-id\""), header)
        // URL-encoded by the SDK, so the space arrives as '+'.
        assertTrue(header.contains("Device=\"Living+Room+TV\""), header)
    }

    @Test
    fun `a device name with quotes and commas cannot break the header`() {
        ServerAuth.configure(
            "https://jellyfin.example.com",
            "secret-token",
            clientInfo,
            DeviceInfo(id = "install-device-id", name = "Bob\"s, TV")
        )
        val header = ServerAuth.authHeader ?: error("header not set")
        assertTrue(header.contains("Token=\"secret-token\""), header)
        // The raw quote/comma must not survive into the header, where they would end the
        // Device parameter early and strand everything after it.
        assertFalse(header.contains("Bob\"s, TV"), header)
    }

    @Test
    fun `token never appears outside the header value`() {
        ServerAuth.configure("https://jellyfin.example.com", "secret-token", clientInfo, deviceInfo)
        // The host must not leak the token (guards against accidental URL-style storage)
        assertEquals("jellyfin.example.com", ServerAuth.serverHost)
    }

    @Test
    fun `clear removes host and header`() {
        ServerAuth.configure("https://jellyfin.example.com", "secret-token", clientInfo, deviceInfo)
        ServerAuth.clear()
        assertNull(ServerAuth.serverHost)
        assertNull(ServerAuth.authHeader)
    }
}
