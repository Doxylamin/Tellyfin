package app.tellyfin.androidtv.data.api

import android.content.Context
import app.tellyfin.androidtv.BuildConfig
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.brandingApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.BrandingOptionsDto
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.min

data class QuickConnectAuth(val serverUrl: String, val accessToken: String, val userId: String, val username: String)

class JellyfinRepository(private val context: Context) {

    // One identity for the whole app. The SDK signs its own calls with these, and the raw
    // image/stream requests made outside it have to present the same pair, or the server books
    // playback reporting and the playback itself as two unrelated sessions. The device id comes
    // from the SDK's Android helper (ANDROID_ID), so it is per-install rather than shared by
    // every copy of the app.
    private val appClientInfo = ClientInfo(name = "Tellyfin", version = BuildConfig.VERSION_NAME)
    private val appDeviceInfo = androidDevice(context)

    private val jellyfin = createJellyfin {
        clientInfo = appClientInfo
        deviceInfo = appDeviceInfo
        context = this@JellyfinRepository.context
    }

    // Resolved once: systemDefault() is a non-trivial lookup and the mappers below
    // call it once per programme.
    private val zone: ZoneId = ZoneId.systemDefault()

    private var api: ApiClient? = null
    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var userId: String = ""

    fun configure(serverUrl: String, accessToken: String, userId: String = "") {
        this.serverUrl = serverUrl.trimEnd('/')
        this.accessToken = accessToken
        this.userId = userId
        api = jellyfin.createApi(baseUrl = this.serverUrl, accessToken = accessToken)
        ServerAuth.configure(this.serverUrl, accessToken, appClientInfo, appDeviceInfo)
    }

    val baseUrl: String get() = serverUrl
    val token: String get() = accessToken

    /** Confirms the address is actually a reachable Jellyfin server, before asking for credentials. */
    suspend fun probeServer(serverUrl: String): PublicSystemInfo {
        val tempApi = jellyfin.createApi(baseUrl = serverUrl.trimEnd('/'))
        return tempApi.systemApi.getPublicSystemInfo().content
    }

    /** Best-effort: callers should treat a failure here as "no branding", not a fatal error. */
    suspend fun getBrandingOptions(serverUrl: String): BrandingOptionsDto {
        val tempApi = jellyfin.createApi(baseUrl = serverUrl.trimEnd('/'))
        return tempApi.brandingApi.getBrandingOptions().content
    }

    suspend fun authenticate(serverUrl: String, username: String, password: String): Triple<String, String, String> {
        val url = serverUrl.trimEnd('/')
        val tempApi = jellyfin.createApi(baseUrl = url)
        val result = tempApi.userApi.authenticateUserByName(
            data = AuthenticateUserByName(username = username, pw = password)
        )
        val token = result.content.accessToken ?: error("No access token returned")
        val uid = result.content.user?.id?.toString() ?: error("No user ID returned")
        return Triple(url, token, uid)
    }

    suspend fun isQuickConnectEnabled(serverUrl: String): Boolean {
        val tempApi = jellyfin.createApi(baseUrl = serverUrl.trimEnd('/'))
        return tempApi.quickConnectApi.getQuickConnectEnabled().content
    }

    suspend fun initiateQuickConnect(serverUrl: String): QuickConnectResult {
        val tempApi = jellyfin.createApi(baseUrl = serverUrl.trimEnd('/'))
        return tempApi.quickConnectApi.initiateQuickConnect().content
    }

    suspend fun getQuickConnectState(serverUrl: String, secret: String): QuickConnectResult {
        val tempApi = jellyfin.createApi(baseUrl = serverUrl.trimEnd('/'))
        return tempApi.quickConnectApi.getQuickConnectState(secret).content
    }

    suspend fun authenticateWithQuickConnect(serverUrl: String, secret: String): QuickConnectAuth {
        val url = serverUrl.trimEnd('/')
        val tempApi = jellyfin.createApi(baseUrl = url)
        val result = tempApi.userApi.authenticateWithQuickConnect(QuickConnectDto(secret = secret))
        val token = result.content.accessToken ?: error("No access token returned")
        val uid = result.content.user?.id?.toString() ?: error("No user ID returned")
        // Unlike password login, there's no typed username to fall back on here — it has to
        // come from the auth response, or the account ends up saved with an empty name.
        val username = result.content.user?.name ?: ""
        return QuickConnectAuth(url, token, uid, username)
    }

    suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val client = api ?: error("Not configured")
        val result = client.liveTvApi.getLiveTvChannels(
            type = org.jellyfin.sdk.model.api.ChannelType.TV,
            enableImages = true,
            addCurrentProgram = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = SortOrder.ASCENDING
        )
        result.content.items.orEmpty().mapIndexed { index, item ->
            val currentProgram = item.currentProgram?.let { prog ->
                val channelId = item.id ?: return@let null
                Program(
                    id = prog.id ?: UUID.randomUUID(),
                    channelId = channelId,
                    title = prog.name ?: "Unknown",
                    startTime = prog.startDate?.atZone(zone)?.toInstant() ?: Instant.now(),
                    endTime = prog.endDate?.atZone(zone)?.toInstant() ?: Instant.now(),
                    description = prog.overview,
                    genre = prog.genres?.firstOrNull()
                )
            }
            Channel(
                id = item.id ?: UUID.randomUUID(),
                name = item.name ?: "Channel ${index + 1}",
                number = item.indexNumber ?: (index + 1),
                // No api_key in the URL — Coil sends the Authorization header (see TellyfinApp)
                logoUrl = item.id?.let { id ->
                    "$serverUrl/Items/$id/Images/Primary"
                },
                currentProgram = currentProgram
            )
        }
    }

    /** Keyed by channel id as a String, matching PlayerUiState.epgData. */
    suspend fun getEpgPrograms(
        channelIds: List<UUID>,
        hoursAhead: Long = 8
    ): Map<String, List<Program>> = withContext(Dispatchers.IO) {
        val client = api ?: return@withContext emptyMap()
        val now = LocalDateTime.now()
        val userUuid = userId.takeIf { it.isNotBlank() }?.let { UUID.fromString(it) }

        // Batch into groups of 50 to stay well under HTTP GET URL length limits.
        // Each UUID is 36 chars; 50 × ~37 ≈ 1,850 chars — safe for all servers.
        // async inherits this IO context, so batches genuinely parse in parallel
        // rather than queueing behind each other on whichever thread called us.
        channelIds.chunked(50).map { batch ->
            async {
                try {
                    val result = client.liveTvApi.getLiveTvPrograms(
                        channelIds = batch,
                        userId = userUuid,
                        // The EPG grid's window starts up to an hour in the past (so the "now"
                        // line has room to sit mid-screen) — minEndDate = now would silently
                        // exclude exactly that history from ever being fetched.
                        minEndDate = now.minusHours(1),
                        maxStartDate = now.plusHours(hoursAhead),
                        enableImages = false,
                        limit = batch.size * 20
                    )
                    result.content.items.orEmpty().mapNotNull { item ->
                        val channelId = item.channelId ?: return@mapNotNull null
                        Program(
                            id = item.id ?: UUID.randomUUID(),
                            channelId = channelId,
                            title = item.name ?: "Unknown",
                            startTime = item.startDate?.atZone(zone)?.toInstant() ?: Instant.now(),
                            endTime = item.endDate?.atZone(zone)?.toInstant() ?: Instant.now(),
                            description = item.overview,
                            genre = item.genres?.firstOrNull()
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
            .awaitAll()
            .flatten()
            .distinctBy { it.id }
            .groupBy { it.channelId }
            // Done here rather than at the call site so the rebuild stays off the main thread.
            // Sorted so consumers (the EPG grid) can rely on chronological order within a
            // channel — the server doesn't guarantee it, and out-of-order entries render as
            // overlapping blocks.
            .mapValues { (_, programs) -> programs.sortedBy { it.startTime } }
            .mapKeys { it.key.toString() }
    }

    suspend fun getChannelPrograms(channelId: UUID): List<Program> = withContext(Dispatchers.IO) {
        val client = api ?: return@withContext emptyList()
        val now = LocalDateTime.now()
        try {
            val userUuid = userId.takeIf { it.isNotBlank() }?.let { UUID.fromString(it) }
            val result = client.liveTvApi.getLiveTvPrograms(
                channelIds = listOf(channelId),
                userId = userUuid,
                minEndDate = now.minusHours(1),
                maxStartDate = now.plusHours(8),
                enableImages = false
            )
            result.content.items.orEmpty()
                .mapNotNull { item ->
                    Program(
                        id = item.id ?: UUID.randomUUID(),
                        channelId = item.channelId ?: channelId,
                        title = item.name ?: "Unknown",
                        startTime = item.startDate?.atZone(zone)?.toInstant() ?: Instant.now(),
                        endTime = item.endDate?.atZone(zone)?.toInstant() ?: Instant.now(),
                        description = item.overview,
                        genre = item.genres?.firstOrNull()
                    )
                }
                .sortedBy { it.startTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Auth is sent via the Authorization header on the player's HTTP data source,
    // never as an api_key query parameter (the server is publicly exposed).
    suspend fun getStreamUrl(channelId: UUID, userId: String, maxBitrate: Int? = null): String =
        "$serverUrl/Videos/$channelId/stream" + buildStreamQuery(channelId, maxBitrate)

    suspend fun reportPlaybackStart(channelId: UUID) {
        val client = api ?: return
        try {
            client.playStateApi.reportPlaybackStart(
                data = PlaybackStartInfo(
                    itemId = channelId,
                    mediaSourceId = channelId.toString(),
                    canSeek = false,
                    isPaused = false,
                    isMuted = false,
                    playMethod = PlayMethod.DIRECT_STREAM,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                    positionTicks = 0L
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun reportPlaybackProgress(channelId: UUID) {
        val client = api ?: return
        try {
            client.playStateApi.reportPlaybackProgress(
                data = PlaybackProgressInfo(
                    itemId = channelId,
                    mediaSourceId = channelId.toString(),
                    canSeek = false,
                    isPaused = false,
                    isMuted = false,
                    playMethod = PlayMethod.DIRECT_STREAM,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                    positionTicks = 0L
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun reportPlaybackStopped(channelId: UUID) {
        val client = api ?: return
        try {
            client.playStateApi.reportPlaybackStopped(
                data = PlaybackStopInfo(
                    itemId = channelId,
                    mediaSourceId = channelId.toString(),
                    failed = false,
                    positionTicks = 0L
                )
            )
        } catch (_: Exception) {}
    }
}

/** Publicly accessible even pre-login, same as the official clients' login-screen backdrop. */
internal fun splashscreenUrl(serverUrl: String): String = "${serverUrl.trimEnd('/')}/Branding/Splashscreen"

// Jellyfin's own install defaults, tried when the user didn't specify a port themselves.
private const val JELLYFIN_DEFAULT_HTTPS_PORT = 8920
private const val JELLYFIN_DEFAULT_HTTP_PORT = 8096

/**
 * Expands a bare address like "tv.example.com" into the candidate URLs worth probing, in the
 * order to try them. A scheme makes the address specific — it's used as-is. Without one, an
 * explicit port still narrows it to that port on both schemes; with neither, Jellyfin's own
 * default ports are tried before falling back to the scheme's own default port.
 */
internal fun candidateServerUrls(input: String): List<String> {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return listOf(trimmed)
    }
    return if (Regex(""":\d+$""").containsMatchIn(trimmed)) {
        listOf("https://$trimmed", "http://$trimmed")
    } else {
        listOf(
            "https://$trimmed:$JELLYFIN_DEFAULT_HTTPS_PORT",
            "http://$trimmed:$JELLYFIN_DEFAULT_HTTP_PORT",
            "https://$trimmed",
            "http://$trimmed"
        )
    }
}

/**
 * Share of the bitrate budget set aside for audio; video gets the rest. Capped at a quarter of
 * the budget so the smallest option still leaves video the bulk of it.
 */
private const val AUDIO_BITRATE_BUDGET = 192_000

/**
 * Query string for the live stream URL.
 *
 * `/Videos/{id}/stream` has no `MaxStreamingBitrate` parameter — it reads `videoBitRate` and
 * `audioBitRate`, so a cap has to be spelled out as an explicit split. Stream copy also has to
 * be refused: left allowed, the server may hand back the source untouched at its original
 * bitrate and the cap does nothing.
 */
internal fun buildStreamQuery(channelId: UUID, maxBitrate: Int?): String = buildString {
    append("?mediaSourceId=$channelId")
    if (maxBitrate != null) {
        val audioBitrate = min(AUDIO_BITRATE_BUDGET, maxBitrate / 4)
        append("&videoBitRate=${maxBitrate - audioBitrate}")
        append("&audioBitRate=$audioBitrate")
        append("&allowVideoStreamCopy=false")
        append("&static=false")
    }
}
