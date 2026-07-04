package app.tellyfin.androidtv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val context: Context) {

    companion object {
        private const val VERSION_URL = "https://tellyfin.app/.version.txt"
        private const val DOWNLOAD_URL = "https://tellyfin.app/download"

        fun isNewer(remote: String, current: String): Boolean {
            fun parts(v: String) = v.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
            val r = parts(remote)
            val c = parts(current)
            for (i in 0 until maxOf(r.size, c.size)) {
                val diff = (r.getOrElse(i) { 0 }) - (c.getOrElse(i) { 0 })
                if (diff != 0) return diff > 0
            }
            return false
        }
    }

    suspend fun fetchLatestVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.inputStream.bufferedReader().readLine()?.trim()
        } catch (_: Exception) { null }
    }

    suspend fun downloadApk(
        @Suppress("UNUSED_PARAMETER") version: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "apk_updates").also { it.mkdirs() }
            val dest = File(dir, "tellyfin-update.apk")
            val conn = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.instanceFollowRedirects = true
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(16_384)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }
            dest
        } catch (_: Exception) { null }
    }
}
