package app.tellyfin.androidtv.data.api

import java.net.URI
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo

/**
 * Holds the Jellyfin auth header so image loading (Coil) and video playback
 * (ExoPlayer) can authenticate via request headers instead of putting the
 * access token in URLs, where it would leak into server logs, proxies and
 * browser history on a publicly exposed server.
 *
 * The client and device identity are passed in rather than hardcoded here: they have to be
 * the same ones the SDK sends on its own calls, or the server treats these requests as a
 * separate session from the one doing the playback reporting.
 */
object ServerAuth {

    @Volatile var serverHost: String? = null
        private set

    @Volatile var authHeader: String? = null
        private set

    fun configure(
        serverUrl: String,
        accessToken: String,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo
    ) {
        serverHost = runCatching { URI(serverUrl).host }.getOrNull()
        // Built by the SDK rather than by hand so the escaping matches its own header exactly.
        // Device names are user-settable and can contain quotes or commas, which would
        // otherwise split the header and take the token parameter with them.
        authHeader = AuthorizationHeaderBuilder.buildHeader(
            clientName = clientInfo.name,
            clientVersion = clientInfo.version,
            deviceId = deviceInfo.id,
            deviceName = deviceInfo.name,
            accessToken = accessToken
        )
    }

    fun clear() {
        serverHost = null
        authHeader = null
    }
}
