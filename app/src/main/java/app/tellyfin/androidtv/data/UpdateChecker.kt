package app.tellyfin.androidtv.data

import android.content.Context
import app.tellyfin.androidtv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val context: Context) {

    companion object {
        private const val UPDATE_URL = "https://tellyfin.app/update.php"

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

    private data class ReleaseInfo(val version: String, val url: String)

    // One endpoint returns both flavors; we just read our own key out of it by BuildConfig.FLAVOR
    // rather than asking the server to pick — that way an update can never install a different
    // telemetry variant than the one currently running.
    private suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(UPDATE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val body = conn.inputStream.bufferedReader().readText()
            val entry = JSONObject(body).getJSONObject(BuildConfig.FLAVOR.lowercase())
            ReleaseInfo(entry.getString("version"), entry.getString("url"))
        } catch (_: Exception) { null }
    }

    suspend fun fetchLatestVersion(): String? = fetchLatestRelease()?.version

    suspend fun downloadApk(
        @Suppress("UNUSED_PARAMETER") version: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val release = fetchLatestRelease() ?: return@withContext null
            val dir = File(context.cacheDir, "apk_updates").also { it.mkdirs() }
            val dest = File(dir, "tellyfin-update.apk")
            val conn = URL(release.url).openConnection() as HttpURLConnection
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
