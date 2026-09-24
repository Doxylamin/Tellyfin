package app.tellyfin.androidtv.data.api

import kotlin.test.assertEquals
import org.junit.Test

class SplashscreenUrlTest {

    @Test
    fun `builds the branding splashscreen path under the server`() {
        assertEquals(
            "https://jellyfin.example.com/Branding/Splashscreen",
            splashscreenUrl("https://jellyfin.example.com")
        )
    }

    @Test
    fun `a trailing slash on the server url is not doubled`() {
        assertEquals(
            "https://jellyfin.example.com/Branding/Splashscreen",
            splashscreenUrl("https://jellyfin.example.com/")
        )
    }
}
