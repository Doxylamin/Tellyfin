package app.tellyfin.androidtv

import android.app.Application
import app.tellyfin.androidtv.data.api.ServerAuth
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

class TellyfinApp : Application(), ImageLoaderFactory {

    /**
     * App-wide Coil loader that authenticates against the Jellyfin server with
     * an Authorization header. The header is only attached to requests going to
     * our own server host, never to third-party hosts.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val header = ServerAuth.authHeader
                        val proceed = if (header != null && request.url.host == ServerAuth.serverHost) {
                            request.newBuilder().header("Authorization", header).build()
                        } else {
                            request
                        }
                        chain.proceed(proceed)
                    }
                    .build()
            }
            .crossfade(true)
            .build()
}
