package app.tellyfin.androidtv.data.api

import android.content.Context
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class JellyfinRepository(private val context: Context) {

    private val jellyfin = createJellyfin {
        clientInfo = ClientInfo(name = "JellyTV", version = "1.0.0")
        context = this@JellyfinRepository.context
    }

    private var api: ApiClient? = null
    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var userId: String = ""

    fun configure(serverUrl: String, accessToken: String, userId: String = "") {
        this.serverUrl = serverUrl.trimEnd('/')
        this.accessToken = accessToken
        this.userId = userId
        api = jellyfin.createApi(baseUrl = this.serverUrl, accessToken = accessToken)
    }

    val baseUrl: String get() = serverUrl
    val token: String get() = accessToken

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

    suspend fun getChannels(): List<Channel> {
        val client = api ?: error("Not configured")
        val result = client.liveTvApi.getLiveTvChannels(
            type = org.jellyfin.sdk.model.api.ChannelType.TV,
            enableImages = true,
            addCurrentProgram = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = SortOrder.ASCENDING
        )
        return result.content.items.orEmpty().mapIndexed { index, item ->
            val currentProgram = item.currentProgram?.let { prog ->
                val channelId = item.id ?: return@let null
                Program(
                    id = prog.id ?: UUID.randomUUID(),
                    channelId = channelId,
                    title = prog.name ?: "Unknown",
                    startTime = prog.startDate?.atZone(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
                    endTime = prog.endDate?.atZone(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
                    description = prog.overview,
                    genre = prog.genres?.firstOrNull()
                )
            }
            Channel(
                id = item.id ?: UUID.randomUUID(),
                name = item.name ?: "Channel ${index + 1}",
                number = item.indexNumber ?: (index + 1),
                logoUrl = item.id?.let { id ->
                    "$serverUrl/Items/$id/Images/Primary?api_key=$accessToken"
                },
                currentProgram = currentProgram
            )
        }
    }

    suspend fun getEpgPrograms(channelIds: List<UUID>, hoursAhead: Long = 8): Map<UUID, List<Program>> {
        val client = api ?: return emptyMap()
        val now = LocalDateTime.now()
        return try {
            val userUuid = userId.takeIf { it.isNotBlank() }?.let { UUID.fromString(it) }
            val result = client.liveTvApi.getLiveTvPrograms(
                channelIds = channelIds,
                userId = userUuid,
                minEndDate = now,
                maxStartDate = now.plusHours(hoursAhead),
                enableImages = false,
                limit = channelIds.size * 20
            )
            result.content.items.orEmpty()
                .mapNotNull { item ->
                    val channelId = item.channelId ?: return@mapNotNull null
                    Program(
                        id = item.id ?: UUID.randomUUID(),
                        channelId = channelId,
                        title = item.name ?: "Unknown",
                        startTime = item.startDate?.atZone(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
                        endTime = item.endDate?.atZone(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
                        description = item.overview,
                        genre = item.genres?.firstOrNull()
                    )
                }
                .groupBy { it.channelId }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getChannelPrograms(channelId: UUID): List<Program> {
        val client = api ?: return emptyList()
        val now = LocalDateTime.now()
        return try {
            val userUuid = userId.takeIf { it.isNotBlank() }?.let { UUID.fromString(it) }
            val result = client.liveTvApi.getLiveTvPrograms(
                channelIds = listOf(channelId),
                userId = userUuid,
                minEndDate = now,
                maxStartDate = now.plusHours(8),
                enableImages = false
            )
            result.content.items.orEmpty()
                .mapNotNull { item ->
                    Program(
                        id = item.id ?: UUID.randomUUID(),
                        channelId = item.channelId ?: channelId,
                        title = item.name ?: "Unknown",
                        startTime = item.startDate?.atZone(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
                        endTime = item.endDate?.atZone(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
                        description = item.overview,
                        genre = item.genres?.firstOrNull()
                    )
                }
                .sortedBy { it.startTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getStreamUrl(channelId: UUID, userId: String, maxBitrate: Int? = null): String {
        return buildString {
            append("$serverUrl/Videos/$channelId/stream")
            append("?api_key=$accessToken")
            append("&mediaSourceId=$channelId")
            if (maxBitrate != null) {
                append("&MaxStreamingBitrate=$maxBitrate")
                append("&static=false")
            }
        }
    }

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
