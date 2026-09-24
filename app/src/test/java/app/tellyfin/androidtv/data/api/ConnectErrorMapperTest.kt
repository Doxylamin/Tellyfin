package app.tellyfin.androidtv.data.api

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException as SdkTimeoutException
import org.junit.Test

class ConnectErrorMapperTest {

    @Test
    fun `unknown host maps to an address hint`() {
        assertEquals(
            "Can't find that server — check the address",
            UnknownHostException("jellyfin.example.com").toUserMessage()
        )
    }

    @Test
    fun `connect refused maps to a reachability hint`() {
        assertEquals(
            "Couldn't reach the server — check it's running and reachable on this network",
            ConnectException("Connection refused").toUserMessage()
        )
    }

    @Test
    fun `socket timeout maps to a reachability hint`() {
        assertEquals(
            "Couldn't reach the server — check it's running and reachable on this network",
            SocketTimeoutException("timeout").toUserMessage()
        )
    }

    @Test
    fun `sdk timeout maps to a reachability hint`() {
        assertEquals(
            "Couldn't reach the server — check it's running and reachable on this network",
            SdkTimeoutException("timeout", null).toUserMessage()
        )
    }

    @Test
    fun `ssl failure maps to a tls hint`() {
        assertEquals(
            "Secure connection failed — check the server's HTTPS setup",
            SSLException("handshake failed").toUserMessage()
        )
    }

    @Test
    fun `sdk secure connection failure maps to a tls hint`() {
        assertEquals(
            "Secure connection failed — check the server's HTTPS setup",
            SecureConnectionException("tls error", null).toUserMessage()
        )
    }

    @Test
    fun `401 status maps to a credentials hint`() {
        assertEquals(
            "Incorrect username or password",
            InvalidStatusException(401, RuntimeException("Unauthorized")).toUserMessage()
        )
    }

    @Test
    fun `other status codes surface the http status`() {
        assertEquals(
            "Server returned an error (HTTP 500)",
            InvalidStatusException(500, RuntimeException("Internal Server Error")).toUserMessage()
        )
    }

    @Test
    fun `unrecognized errors fall back to a generic message`() {
        assertEquals(
            "Couldn't connect to the server",
            IOException("Unknown IO error occured!").toUserMessage()
        )
    }

    @Test
    fun `a clean server-returned status is not worth reporting`() {
        assertFalse(InvalidStatusException(401, RuntimeException("Unauthorized")).isReportable())
        assertFalse(InvalidStatusException(500, RuntimeException("Internal Server Error")).isReportable())
    }

    @Test
    fun `connectivity and unrecognized errors are worth reporting`() {
        assertTrue(UnknownHostException("jellyfin.example.com").isReportable())
        assertTrue(ConnectException("Connection refused").isReportable())
        assertTrue(SocketTimeoutException("timeout").isReportable())
        assertTrue(SdkTimeoutException("timeout", null).isReportable())
        assertTrue(SSLException("handshake failed").isReportable())
        assertTrue(SecureConnectionException("tls error", null).isReportable())
        assertTrue(IOException("Unknown IO error occured!").isReportable())
    }
}
