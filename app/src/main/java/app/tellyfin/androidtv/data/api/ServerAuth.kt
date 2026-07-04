package app.tellyfin.androidtv.data.api

import java.net.URI

/**
 * Holds the Jellyfin auth header so image loading (Coil) and video playback
 * (ExoPlayer) can authenticate via request headers instead of putting the
 * access token in URLs, where it would leak into server logs, proxies and
 * browser history on a publicly exposed server.
 */
object ServerAuth {

    @Volatile var serverHost: String? = null
        private set

    @Volatile var authHeader: String? = null
        private set

    fun configure(serverUrl: String, accessToken: String) {
        serverHost = runCatching { URI(serverUrl).host }.getOrNull()
        authHeader = "MediaBrowser Client=\"Tellyfin\", Device=\"AndroidTV\", " +
            "DeviceId=\"tellyfin-androidtv\", Version=\"1.0\", Token=\"$accessToken\""
    }

    fun clear() {
        serverHost = null
        authHeader = null
    }
}
