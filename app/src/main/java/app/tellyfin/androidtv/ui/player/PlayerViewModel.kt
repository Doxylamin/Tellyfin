package app.tellyfin.androidtv.ui.player

import android.app.Application
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.tellyfin.androidtv.BuildConfig
import app.tellyfin.androidtv.data.UpdateChecker
import app.tellyfin.androidtv.data.api.JellyfinRepository
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.data.prefs.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed class Overlay {
    object None : Overlay()
    object ChannelBanner : Overlay()
    object NowPlaying : Overlay()
    object QuickMenu : Overlay()
    object ChannelList : Overlay()
    object Epg : Overlay()
    object Settings : Overlay()
    object Search : Overlay()
    data class ZapInput(val digits: String) : Overlay()
    /** MENU key on EPG row — sidebar with Play / Favorite / Details */
    data class ChannelContext(val channelIndex: Int) : Overlay()
    /** "Details" in ChannelContext — single-channel programme list */
    data class ChannelDetails(val channelIndex: Int) : Overlay()
}

sealed class SearchResult {
    data class ChannelMatch(val channel: Channel) : SearchResult()
    data class ProgramMatch(val program: Program, val channel: Channel) : SearchResult()
}

// homeFocusSection values
const val HOME_SECTION_NAV      = 0
const val HOME_SECTION_CAROUSEL = 1   // For You tab: featured cards
const val HOME_SECTION_EPG      = 2   // Live tab: EPG grid rows

// homeNavTabIndex values
const val NAV_FOR_YOU  = 0
const val NAV_LIVE     = 1
const val NAV_SEARCH   = 2
const val NAV_SETTINGS = 3

data class PlayerUiState(
    val isLoadingChannels: Boolean = true,
    val loadingStatus: String = "Connecting to server…",
    val channels: List<Channel> = emptyList(),
    val currentIndex: Int = 0,
    val highlightedIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val overlay: Overlay = Overlay.None,
    val epgData: Map<String, List<Program>> = emptyMap(),
    val favoriteChannelIds: Set<UUID> = emptySet(),
    val highlightedMenuIndex: Int = 0,
    val maxBitrate: Int? = null,
    val homeFocusSection: Int = HOME_SECTION_EPG,
    val homeNavTabIndex: Int = NAV_LIVE,
    val nowPlayingCardIndex: Int = 0,
    val epgFocusedBlockIndex: Int = 0,
    val channelContextMenuIndex: Int = 0,
    val channelDetailsProgramIndex: Int = 0,
    val username: String = "",
    val logoutRequested: Boolean = false,
    val searchQuery: String = "",
    val searchResultIndex: Int = 0,
    val searchResults: List<SearchResult> = emptyList(),
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val pendingInstallFile: File? = null,
    val bitratePickerOpen: Boolean = false,
    val bitratePickerIndex: Int = 0
) {
    val currentChannel: Channel? get() = channels.getOrNull(currentIndex)
    val highlightedChannel: Channel? get() = channels.getOrNull(highlightedIndex)
    val showFavoritesOnly: Boolean get() = homeNavTabIndex == NAV_FOR_YOU

    fun displayedChannels(): List<Channel> =
        if (showFavoritesOnly) channels.filter { it.id in favoriteChannelIds }
        else channels

    fun sortedChannels(): List<Channel> {
        val favs = channels.filter { it.id in favoriteChannelIds }
        val rest = channels.filter { it.id !in favoriteChannelIds }
        return favs + rest
    }
}

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Available(val version: String) : UpdateStatus()
    data class Downloading(val progress: Int) : UpdateStatus()
    object ReadyToInstall : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

val BITRATE_OPTIONS = listOf(
    null to "Auto",
    2_000_000 to "2 Mbps",
    4_000_000 to "4 Mbps",
    8_000_000 to "8 Mbps",
    12_000_000 to "12 Mbps",
    20_000_000 to "20 Mbps",
    40_000_000 to "40 Mbps"
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val jellyfinRepo = JellyfinRepository(application)
    private val updateChecker = UpdateChecker(application)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build().also { player ->
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _uiState.value = _uiState.value.copy(isBuffering = state == Player.STATE_BUFFERING)
            }
        })
        player.playWhenReady = true
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var bannerDismissJob: Job? = null
    private var zapDismissJob: Job? = null
    private var progressReportJob: Job? = null
    private var userId: String = ""

    init {
        viewModelScope.launch {
            val token = prefsRepo.accessToken.first() ?: return@launch
            val url = prefsRepo.serverUrl.first() ?: return@launch
            userId = prefsRepo.userId.first() ?: return@launch
            val lastIndex = prefsRepo.lastChannelIndex.first()
            val maxBitrate = prefsRepo.maxBitrate.first()
            val favIds = prefsRepo.favoriteIds.first()
                .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                .toSet()

            val username = prefsRepo.username.first()
            jellyfinRepo.configure(url, token, userId)
            _uiState.value = _uiState.value.copy(
                maxBitrate = maxBitrate,
                favoriteChannelIds = favIds,
                username = username,
                loadingStatus = "Loading channels…"
            )
            loadChannels(startIndex = lastIndex)
        }
    }

    private suspend fun loadChannels(startIndex: Int) {
        try {
            val channels = jellyfinRepo.getChannels()
            val safeIndex = startIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
            // Keep splash visible (isLoadingChannels stays true) until EPG also finishes
            _uiState.value = _uiState.value.copy(
                channels = channels,
                currentIndex = safeIndex,
                highlightedIndex = safeIndex,
                nowPlayingCardIndex = safeIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0)),
                isPlaying = false,
                loadingStatus = "Loading programme guide…"
            )
            loadEpg()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoadingChannels = false,
                error = "Failed to load channels: ${e.message}"
            )
        }
    }

    private fun loadEpg() {
        viewModelScope.launch {
            try {
                val ids = _uiState.value.channels.map { it.id }
                val programs = jellyfinRepo.getEpgPrograms(ids)
                _uiState.value = _uiState.value.copy(
                    epgData = programs.mapKeys { it.key.toString() },
                    isLoadingChannels = false
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingChannels = false)
            }
        }
    }

    private fun loadEpgForChannel(channelId: UUID) {
        val key = channelId.toString()
        if (_uiState.value.epgData[key]?.isNotEmpty() == true) return
        viewModelScope.launch {
            val programs = jellyfinRepo.getChannelPrograms(channelId)
            if (programs.isNotEmpty()) {
                val updated = _uiState.value.epgData.toMutableMap()
                updated[key] = programs
                _uiState.value = _uiState.value.copy(epgData = updated)
            }
        }
    }

    // ── Key dispatch ────────────────────────────────────────────────────────

    fun handleKeyEvent(keyCode: Int): Boolean {
        val state = _uiState.value

        if (keyCode == KeyEvent.KEYCODE_BACK) return handleBack(state)

        if (keyCode == KeyEvent.KEYCODE_SEARCH && state.overlay !is Overlay.Search) {
            openSearch(); return true
        }

        if (state.channels.isEmpty()) return false

        return when {
            state.overlay is Overlay.Search -> handleSearchKeys(keyCode, state)
            state.overlay is Overlay.ZapInput -> handleZapKeys(keyCode, state)
            state.overlay is Overlay.ChannelList -> handleChannelListKeys(keyCode, state)
            state.overlay is Overlay.Epg -> handleEpgKeys(keyCode, state)
            state.overlay is Overlay.NowPlaying -> handleNowPlayingKeys(keyCode, state)
            state.overlay is Overlay.QuickMenu -> handleQuickMenuKeys(keyCode, state)
            state.overlay is Overlay.Settings -> handleSettingsKeys(keyCode, state)
            state.overlay is Overlay.ChannelContext -> handleChannelContextKeys(keyCode, state)
            state.overlay is Overlay.ChannelDetails -> handleChannelDetailsKeys(keyCode, state)
            !state.isPlaying -> handleHomeKeys(keyCode, state)
            else -> handlePlayerKeys(keyCode, state)
        }
    }

    private fun handleBack(state: PlayerUiState): Boolean {
        return when {
            state.bitratePickerOpen -> {
                _uiState.value = state.copy(bitratePickerOpen = false)
                true
            }
            state.overlay is Overlay.Search -> { clearSearch(); true }
            state.overlay is Overlay.ChannelDetails -> {
                val chIdx = (state.overlay as Overlay.ChannelDetails).channelIndex
                _uiState.value = state.copy(overlay = Overlay.ChannelContext(chIdx), channelContextMenuIndex = 2)
                true
            }
            state.overlay is Overlay.ChannelContext && state.isPlaying -> {
                _uiState.value = state.copy(overlay = Overlay.ChannelList)
                true
            }
            state.overlay is Overlay.ChannelBanner -> {
                bannerDismissJob?.cancel()
                _uiState.value = state.copy(overlay = Overlay.None, highlightedIndex = state.currentIndex)
                true
            }
            state.overlay !is Overlay.None -> { dismissOverlay(); true }
            state.isPlaying -> {
                stopProgressReporting(state.currentChannel?.id)
                exoPlayer.stop()
                _uiState.value = state.copy(
                    isPlaying = false,
                    overlay = Overlay.None,
                    highlightedIndex = state.currentIndex,
                    homeFocusSection = HOME_SECTION_EPG,
                    nowPlayingCardIndex = state.currentIndex.coerceIn(0, (state.channels.size - 1).coerceAtLeast(0))
                )
                true
            }
            state.homeFocusSection != HOME_SECTION_NAV -> {
                _uiState.value = state.copy(homeFocusSection = HOME_SECTION_NAV)
                true
            }
            else -> false
        }
    }

    private fun handleHomeKeys(keyCode: Int, state: PlayerUiState): Boolean {
        return when (state.homeFocusSection) {
            HOME_SECTION_NAV -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val n = (state.homeNavTabIndex - 1).coerceAtLeast(0)
                    _uiState.value = state.copy(homeNavTabIndex = n, nowPlayingCardIndex = 0)
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val n = (state.homeNavTabIndex + 1).coerceAtMost(NAV_SETTINGS)
                    _uiState.value = state.copy(homeNavTabIndex = n, nowPlayingCardIndex = 0)
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val next = if (state.homeNavTabIndex == NAV_FOR_YOU) HOME_SECTION_CAROUSEL else HOME_SECTION_EPG
                    _uiState.value = state.copy(homeFocusSection = next)
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    when (state.homeNavTabIndex) {
                        NAV_SEARCH -> openSearch()
                        NAV_SETTINGS -> openSettings()
                        else -> {
                            val next = if (state.homeNavTabIndex == NAV_FOR_YOU) HOME_SECTION_CAROUSEL else HOME_SECTION_EPG
                            _uiState.value = state.copy(homeFocusSection = next)
                        }
                    }
                    true
                }
                KeyEvent.KEYCODE_SEARCH -> { openSearch(); true }
                else -> false
            }
            HOME_SECTION_CAROUSEL -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    _uiState.value = state.copy(homeFocusSection = HOME_SECTION_NAV)
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val displayed = state.displayedChannels()
                    if (displayed.isEmpty()) return false
                    val n = (state.nowPlayingCardIndex - 1).coerceAtLeast(0)
                    val chIdx = state.channels.indexOf(displayed[n])
                    _uiState.value = state.copy(
                        nowPlayingCardIndex = n,
                        highlightedIndex = if (chIdx >= 0) chIdx else state.highlightedIndex
                    )
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val displayed = state.displayedChannels()
                    if (displayed.isEmpty()) return false
                    val n = (state.nowPlayingCardIndex + 1).coerceAtMost(displayed.size - 1)
                    val chIdx = state.channels.indexOf(displayed[n])
                    _uiState.value = state.copy(
                        nowPlayingCardIndex = n,
                        highlightedIndex = if (chIdx >= 0) chIdx else state.highlightedIndex
                    )
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val displayed = state.displayedChannels()
                    val ch = displayed.getOrNull(state.nowPlayingCardIndex) ?: return false
                    val idx = state.channels.indexOf(ch)
                    if (idx >= 0) startPlaying(idx)
                    true
                }
                KeyEvent.KEYCODE_SEARCH -> { openSearch(); true }
                else -> false
            }
            HOME_SECTION_EPG -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    val sorted = state.sortedChannels()
                    val cur = sorted.indexOf(state.channels.getOrNull(state.highlightedIndex))
                    if (cur <= 0) {
                        _uiState.value = state.copy(homeFocusSection = HOME_SECTION_NAV)
                    } else {
                        val newCh = sorted[cur - 1]
                        _uiState.value = state.copy(
                            highlightedIndex = state.channels.indexOf(newCh),
                            epgFocusedBlockIndex = currentProgramIndex(newCh.id, state.epgData)
                        )
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val sorted = state.sortedChannels()
                    val cur = sorted.indexOf(state.channels.getOrNull(state.highlightedIndex))
                    if (cur < sorted.size - 1) {
                        val newCh = sorted[cur + 1]
                        _uiState.value = state.copy(
                            highlightedIndex = state.channels.indexOf(newCh),
                            epgFocusedBlockIndex = currentProgramIndex(newCh.id, state.epgData)
                        )
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val n = (state.epgFocusedBlockIndex - 1).coerceAtLeast(0)
                    _uiState.value = state.copy(epgFocusedBlockIndex = n)
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    _uiState.value = state.copy(epgFocusedBlockIndex = state.epgFocusedBlockIndex + 1)
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    startPlaying(state.highlightedIndex); true
                }
                KeyEvent.KEYCODE_MENU -> {
                    _uiState.value = state.copy(
                        overlay = Overlay.ChannelContext(state.highlightedIndex),
                        channelContextMenuIndex = 0
                    )
                    true
                }
                KeyEvent.KEYCODE_SEARCH -> { openSearch(); true }
                else -> false
            }
            else -> false
        }
    }

    private fun handleChannelContextKeys(keyCode: Int, state: PlayerUiState): Boolean {
        val chIdx = (state.overlay as Overlay.ChannelContext).channelIndex
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                _uiState.value = state.copy(
                    channelContextMenuIndex = (state.channelContextMenuIndex - 1 + 3) % 3
                )
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                _uiState.value = state.copy(
                    channelContextMenuIndex = (state.channelContextMenuIndex + 1) % 3
                )
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when (state.channelContextMenuIndex) {
                    0 -> startPlaying(chIdx)
                    1 -> {
                        val ch = state.channels.getOrNull(chIdx)
                        if (ch != null) {
                            val updated = if (ch.id in state.favoriteChannelIds)
                                state.favoriteChannelIds - ch.id
                            else
                                state.favoriteChannelIds + ch.id
                            val nextOverlay = if (state.isPlaying) Overlay.ChannelList else Overlay.None
                            _uiState.value = state.copy(favoriteChannelIds = updated, overlay = nextOverlay)
                            viewModelScope.launch {
                                prefsRepo.saveFavoriteIds(updated.map { it.toString() }.toSet())
                            }
                        }
                    }
                    2 -> {
                        _uiState.value = state.copy(
                            overlay = Overlay.ChannelDetails(chIdx),
                            channelDetailsProgramIndex = 0
                        )
                        state.channels.getOrNull(chIdx)?.id?.let { loadEpgForChannel(it) }
                    }
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val nextOverlay = if (state.isPlaying) Overlay.ChannelList else Overlay.None
                _uiState.value = state.copy(overlay = nextOverlay)
                true
            }
            else -> false
        }
    }

    private fun handleChannelDetailsKeys(keyCode: Int, state: PlayerUiState): Boolean {
        val chIdx = (state.overlay as Overlay.ChannelDetails).channelIndex
        val programs = state.channels.getOrNull(chIdx)
            ?.let { state.epgData[it.id.toString()].orEmpty() } ?: emptyList()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                val n = (state.channelDetailsProgramIndex - 1).coerceAtLeast(0)
                _uiState.value = state.copy(channelDetailsProgramIndex = n)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val n = (state.channelDetailsProgramIndex + 1)
                    .coerceAtMost((programs.size - 1).coerceAtLeast(0))
                _uiState.value = state.copy(channelDetailsProgramIndex = n)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                startPlaying(chIdx); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                _uiState.value = state.copy(
                    overlay = Overlay.ChannelContext(chIdx),
                    channelContextMenuIndex = 2
                )
                true
            }
            else -> false
        }
    }

    private fun handlePlayerKeys(keyCode: Int, state: PlayerUiState): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { previewChannelUp(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { previewChannelDown(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (state.overlay is Overlay.ChannelBanner) { confirmChannelSwitch(); true }
                else { openNowPlaying(); true }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { openChannelList(); true }
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> { openQuickMenu(); true }
            KeyEvent.KEYCODE_INFO -> { showChannelBanner(); true }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { onZapDigit(keyCode - KeyEvent.KEYCODE_0); true }
            else -> false
        }
    }

    private fun handleNowPlayingKeys(keyCode: Int, state: PlayerUiState): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { channelUp(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { channelDown(); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                dismissOverlay(); true
            }
            else -> false
        }
    }

    private fun handleChannelListKeys(keyCode: Int, state: PlayerUiState): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                val n = (state.highlightedIndex - 1 + state.channels.size) % state.channels.size
                _uiState.value = state.copy(highlightedIndex = n); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val n = (state.highlightedIndex + 1) % state.channels.size
                _uiState.value = state.copy(highlightedIndex = n); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                selectChannel(state.highlightedIndex); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                _uiState.value = state.copy(overlay = Overlay.None, highlightedIndex = state.currentIndex)
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                _uiState.value = state.copy(
                    overlay = Overlay.ChannelContext(state.highlightedIndex),
                    channelContextMenuIndex = 0
                )
                true
            }
            else -> false
        }
    }

    private fun handleEpgKeys(keyCode: Int, state: PlayerUiState): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                val n = (state.highlightedIndex - 1 + state.channels.size) % state.channels.size
                _uiState.value = state.copy(highlightedIndex = n); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val n = (state.highlightedIndex + 1) % state.channels.size
                _uiState.value = state.copy(highlightedIndex = n); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                selectChannel(state.highlightedIndex); true
            }
            else -> false
        }
    }

    private val QUICK_MENU_SIZE = 3

    private fun handleQuickMenuKeys(keyCode: Int, state: PlayerUiState): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                _uiState.value = state.copy(
                    highlightedMenuIndex = (state.highlightedMenuIndex - 1 + QUICK_MENU_SIZE) % QUICK_MENU_SIZE
                )
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                _uiState.value = state.copy(
                    highlightedMenuIndex = (state.highlightedMenuIndex + 1) % QUICK_MENU_SIZE
                )
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                executeMenuAction(state.highlightedMenuIndex, state); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { dismissOverlay(); true }
            else -> false
        }
    }

    private fun executeMenuAction(index: Int, state: PlayerUiState) {
        val channelId = state.currentChannel?.id ?: return
        when (index) {
            0 -> toggleFavorite(channelId)
            1 -> refreshStream()
            2 -> openSettings()
        }
    }

    private fun handleSettingsKeys(keyCode: Int, state: PlayerUiState): Boolean {
        // Rows: 0 = logout, 1 = update, 2 = bandwidth
        if (state.bitratePickerOpen) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    _uiState.value = state.copy(
                        bitratePickerIndex = (state.bitratePickerIndex - 1 + BITRATE_OPTIONS.size) % BITRATE_OPTIONS.size
                    )
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    _uiState.value = state.copy(
                        bitratePickerIndex = (state.bitratePickerIndex + 1) % BITRATE_OPTIONS.size
                    )
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    setMaxBitrate(BITRATE_OPTIONS[state.bitratePickerIndex].first)
                    _uiState.value = _uiState.value.copy(bitratePickerOpen = false)
                    true
                }
                else -> false
            }
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                _uiState.value = state.copy(
                    highlightedMenuIndex = (state.highlightedMenuIndex - 1 + 3) % 3
                )
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                _uiState.value = state.copy(
                    highlightedMenuIndex = (state.highlightedMenuIndex + 1) % 3
                )
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when (state.highlightedMenuIndex) {
                    0 -> logOut()
                    1 -> when (state.updateStatus) {
                        is UpdateStatus.Available -> downloadUpdate((state.updateStatus as UpdateStatus.Available).version)
                        UpdateStatus.ReadyToInstall -> triggerInstall()
                        else -> Unit
                    }
                    2 -> {
                        val currentIdx = BITRATE_OPTIONS.indexOfFirst { it.first == state.maxBitrate }
                            .takeIf { it >= 0 } ?: 0
                        _uiState.value = state.copy(bitratePickerOpen = true, bitratePickerIndex = currentIdx)
                    }
                }
                true
            }
            else -> false
        }
    }

    private fun currentProgramIndex(channelId: UUID, epgData: Map<String, List<Program>>): Int {
        val now = java.time.Instant.now()
        val programs = epgData[channelId.toString()].orEmpty()
        val idx = programs.indexOfFirst { it.startTime <= now && it.endTime > now }
        return if (idx >= 0) idx else 0
    }

    private fun openSettings() {
        _uiState.value = _uiState.value.copy(overlay = Overlay.Settings, highlightedMenuIndex = 0, bitratePickerOpen = false)
        checkForUpdate()
    }

    private fun checkForUpdate() {
        if (_uiState.value.updateStatus is UpdateStatus.Checking ||
            _uiState.value.updateStatus is UpdateStatus.Downloading) return
        _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Checking)
        viewModelScope.launch {
            val remote = updateChecker.fetchLatestVersion()
            _uiState.value = if (remote == null) {
                _uiState.value.copy(updateStatus = UpdateStatus.Error("Could not reach update server"))
            } else if (updateChecker.isNewer(remote, BuildConfig.VERSION_NAME)) {
                _uiState.value.copy(updateStatus = UpdateStatus.Available(remote))
            } else {
                _uiState.value.copy(updateStatus = UpdateStatus.UpToDate)
            }
        }
    }

    private fun downloadUpdate(version: String) {
        _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Downloading(0))
        viewModelScope.launch {
            val file = updateChecker.downloadApk(version) { progress ->
                _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Downloading(progress))
            }
            _uiState.value = if (file != null) {
                _uiState.value.copy(updateStatus = UpdateStatus.ReadyToInstall, pendingInstallFile = file)
            } else {
                _uiState.value.copy(updateStatus = UpdateStatus.Error("Download failed"))
            }
        }
    }

    private fun triggerInstall() {
        // pendingInstallFile is already set; PlayerScreen observes it and launches the intent
    }

    fun clearPendingInstall() {
        _uiState.value = _uiState.value.copy(pendingInstallFile = null)
    }

    fun logOut() {
        viewModelScope.launch {
            prefsRepo.clearSession()
            _uiState.value = _uiState.value.copy(logoutRequested = true)
        }
    }

    private fun handleZapKeys(keyCode: Int, state: PlayerUiState): Boolean {
        return when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { onZapDigit(keyCode - KeyEvent.KEYCODE_0); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                zapDismissJob?.cancel()
                val digits = (state.overlay as? Overlay.ZapInput)?.digits ?: return false
                zapToNumber(digits.toIntOrNull() ?: return false)
                true
            }
            else -> false
        }
    }

    private fun handleSearchKeys(keyCode: Int, state: PlayerUiState): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (state.searchResultIndex > 0)
                    _uiState.value = state.copy(searchResultIndex = state.searchResultIndex - 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (state.searchResultIndex < state.searchResults.size - 1)
                    _uiState.value = state.copy(searchResultIndex = state.searchResultIndex + 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val result = state.searchResults.getOrNull(state.searchResultIndex) ?: return false
                val channelId = when (result) {
                    is SearchResult.ChannelMatch -> result.channel.id
                    is SearchResult.ProgramMatch -> result.channel.id
                }
                val idx = state.channels.indexOfFirst { it.id == channelId }
                if (idx >= 0) { clearSearch(); startPlaying(idx) }
                true
            }
            else -> false
        }
    }

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        // Update the query immediately so the text field stays responsive
        _uiState.value = _uiState.value.copy(searchQuery = query, searchResultIndex = 0)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val results = withContext(Dispatchers.Default) {
                computeSearchResults(query, _uiState.value)
            }
            _uiState.value = _uiState.value.copy(searchResults = results)
        }
    }

    private fun computeSearchResults(query: String, state: PlayerUiState): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase().trim()
        val channelMatches: List<SearchResult> = state.channels
            .filter { ch -> ch.name.lowercase().contains(q) || ch.number.toString().contains(q) }
            .map { SearchResult.ChannelMatch(it) }

        val programMatches: List<SearchResult.ProgramMatch> = state.epgData.entries.flatMap { (chId, progs) ->
            val ch = state.channels.firstOrNull { it.id.toString() == chId }
                ?: return@flatMap emptyList<SearchResult.ProgramMatch>()
            progs.filter { p -> p.title.lowercase().contains(q) }
                .sortedBy { it.startTime }
                .take(3)
                .map { SearchResult.ProgramMatch(it, ch) }
        }.sortedBy { it.program.startTime }.take(15)

        return (channelMatches + programMatches).take(30)
    }

    fun openSearch() {
        _uiState.value = _uiState.value.copy(
            overlay = Overlay.Search,
            searchQuery = "",
            searchResultIndex = 0,
            searchResults = emptyList()
        )
    }

    private fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            overlay = Overlay.None,
            searchQuery = "",
            searchResults = emptyList(),
            searchResultIndex = 0
        )
    }

    // ── Channel operations ──────────────────────────────────────────────────

    fun startPlaying(index: Int) {
        _uiState.value = _uiState.value.copy(isPlaying = true, overlay = Overlay.None)
        switchToChannel(index)
    }

    private fun selectChannel(index: Int) {
        _uiState.value = _uiState.value.copy(overlay = Overlay.None, isPlaying = true)
        switchToChannel(index)
    }

    private fun switchToChannel(index: Int) {
        bannerDismissJob?.cancel()
        _uiState.value = _uiState.value.copy(currentIndex = index, highlightedIndex = index, overlay = Overlay.None)
        playChannel(index)
    }

    private fun playChannel(index: Int) {
        val channel = _uiState.value.channels.getOrNull(index) ?: return
        stopProgressReporting(channel.id)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBuffering = true, error = null)
            try {
                val url = jellyfinRepo.getStreamUrl(channel.id, userId, _uiState.value.maxBitrate)
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
                prefsRepo.saveLastChannelIndex(index)
                jellyfinRepo.reportPlaybackStart(channel.id)
                startProgressReporting(channel.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Stream error: ${e.message}")
            }
        }
    }

    fun channelUp() {
        val state = _uiState.value
        if (state.channels.isEmpty()) return
        switchToChannel((state.currentIndex - 1 + state.channels.size) % state.channels.size)
    }

    fun channelDown() {
        val state = _uiState.value
        if (state.channels.isEmpty()) return
        switchToChannel((state.currentIndex + 1) % state.channels.size)
    }

    private fun previewChannelUp() {
        val state = _uiState.value
        if (state.channels.isEmpty()) return
        previewChannel((state.highlightedIndex - 1 + state.channels.size) % state.channels.size)
    }

    private fun previewChannelDown() {
        val state = _uiState.value
        if (state.channels.isEmpty()) return
        previewChannel((state.highlightedIndex + 1) % state.channels.size)
    }

    private fun previewChannel(index: Int) {
        bannerDismissJob?.cancel()
        _uiState.value = _uiState.value.copy(highlightedIndex = index, overlay = Overlay.ChannelBanner)
        bannerDismissJob = viewModelScope.launch {
            delay(3_000)
            if (_uiState.value.overlay is Overlay.ChannelBanner) confirmChannelSwitch()
        }
    }

    private fun confirmChannelSwitch() {
        val state = _uiState.value
        if (state.highlightedIndex != state.currentIndex) switchToChannel(state.highlightedIndex)
        else _uiState.value = state.copy(overlay = Overlay.None)
    }

    private fun openChannelList() {
        _uiState.value = _uiState.value.copy(
            overlay = Overlay.ChannelList,
            highlightedIndex = _uiState.value.currentIndex
        )
    }

    private fun openNowPlaying() {
        bannerDismissJob?.cancel()
        val channelId = _uiState.value.currentChannel?.id
        _uiState.value = _uiState.value.copy(overlay = Overlay.NowPlaying)
        if (channelId != null) loadEpgForChannel(channelId)
        bannerDismissJob = viewModelScope.launch {
            delay(8_000)
            if (_uiState.value.overlay is Overlay.NowPlaying) {
                _uiState.value = _uiState.value.copy(overlay = Overlay.None)
            }
        }
    }

    private fun openQuickMenu() {
        _uiState.value = _uiState.value.copy(overlay = Overlay.QuickMenu, highlightedMenuIndex = 0)
    }

    fun dismissOverlay() {
        _uiState.value = _uiState.value.copy(overlay = Overlay.None, bitratePickerOpen = false)
    }

    fun showChannelBanner() {
        bannerDismissJob?.cancel()
        _uiState.value = _uiState.value.copy(
            overlay = Overlay.ChannelBanner,
            highlightedIndex = _uiState.value.currentIndex
        )
        bannerDismissJob = viewModelScope.launch {
            delay(3_000)
            if (_uiState.value.overlay is Overlay.ChannelBanner) {
                _uiState.value = _uiState.value.copy(overlay = Overlay.None)
            }
        }
    }

    fun refreshStream() {
        val state = _uiState.value
        _uiState.value = state.copy(overlay = Overlay.None)
        playChannel(state.currentIndex)
    }

    fun toggleFavorite(channelId: UUID) {
        val current = _uiState.value.favoriteChannelIds
        val updated = if (channelId in current) current - channelId else current + channelId
        _uiState.value = _uiState.value.copy(favoriteChannelIds = updated, overlay = Overlay.None)
        viewModelScope.launch { prefsRepo.saveFavoriteIds(updated.map { it.toString() }.toSet()) }
    }

    private fun toggleFavoriteNoClose(channelId: UUID) {
        val current = _uiState.value.favoriteChannelIds
        val updated = if (channelId in current) current - channelId else current + channelId
        _uiState.value = _uiState.value.copy(favoriteChannelIds = updated)
        viewModelScope.launch { prefsRepo.saveFavoriteIds(updated.map { it.toString() }.toSet()) }
    }

    fun setMaxBitrate(bitrate: Int?) {
        _uiState.value = _uiState.value.copy(maxBitrate = bitrate)
        viewModelScope.launch { prefsRepo.saveMaxBitrate(bitrate) }
    }

    private fun startProgressReporting(channelId: UUID) {
        progressReportJob?.cancel()
        progressReportJob = viewModelScope.launch {
            while (true) {
                delay(10_000)
                jellyfinRepo.reportPlaybackProgress(channelId)
            }
        }
    }

    private fun stopProgressReporting(channelId: UUID?) {
        progressReportJob?.cancel()
        progressReportJob = null
        if (channelId != null) {
            viewModelScope.launch { jellyfinRepo.reportPlaybackStopped(channelId) }
        }
    }

    private fun onZapDigit(digit: Int) {
        val current = _uiState.value.overlay
        val digits = if (current is Overlay.ZapInput) current.digits else ""
        val newDigits = (digits + digit.toString()).takeLast(4)
        _uiState.value = _uiState.value.copy(overlay = Overlay.ZapInput(newDigits))
        zapDismissJob?.cancel()
        zapDismissJob = viewModelScope.launch {
            delay(2_000)
            val s = _uiState.value.overlay
            if (s is Overlay.ZapInput) zapToNumber(s.digits.toIntOrNull() ?: return@launch)
        }
    }

    private fun zapToNumber(channelNumber: Int) {
        val index = _uiState.value.channels.indexOfFirst { it.number == channelNumber }
        if (index >= 0) selectChannel(index)
        else _uiState.value = _uiState.value.copy(overlay = Overlay.None)
    }

    override fun onCleared() {
        stopProgressReporting(_uiState.value.currentChannel?.id)
        exoPlayer.release()
    }
}
