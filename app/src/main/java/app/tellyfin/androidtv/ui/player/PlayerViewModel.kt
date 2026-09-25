package app.tellyfin.androidtv.ui.player

import android.app.Application
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.PreloadMediaSource
import app.tellyfin.androidtv.BuildConfig
import app.tellyfin.androidtv.data.UpdateChecker
import app.tellyfin.androidtv.data.api.JellyfinRepository
import app.tellyfin.androidtv.data.api.ServerAuth
import app.tellyfin.androidtv.data.api.splashscreenUrl
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.data.prefs.PreferencesRepository
import app.tellyfin.androidtv.diagnostics.CrashReporting
import app.tellyfin.androidtv.diagnostics.ReportLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** Shown on start when a newer app version is available */
    data class UpdatePrompt(val version: String) : Overlay()
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
    val startupError: String? = null,
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
    val prebufferEnabled: Boolean = true,
    /** Set when the app turned pre-buffering off itself, so Settings can explain why. */
    val prebufferAutoDisabled: Boolean = false,
    val prebufferDelayMs: Long = Prebuffer.DEFAULT_START_DELAY_MS,
    /** Prebuffer.COUNTDOWN_AUTO or an explicit countdown in ms. */
    val countdownSettingMs: Long = Prebuffer.COUNTDOWN_AUTO,
    val diagnosticsEnabled: Boolean = false,
    val keybinds: Keybinds = Keybinds(),
    val settings: SettingsState = SettingsState(),
    /** Drives the preview banner's countdown ring: pulsing while loading, green when ready. */
    val preloadStatus: PreloadStatus = PreloadStatus.None,
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
    val searchFieldFocused: Boolean = true,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val pendingInstallFile: File? = null,
    /** 0 = Install now, 1 = Later */
    val updatePromptButtonIndex: Int = 0,
    val serverName: String? = null,
    val splashscreenUrl: String? = null
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

// Never leave the user staring at the splash forever: give the saved-session restore +
// channel/EPG load this long in total before surfacing an error with a way out.
private const val STARTUP_TIMEOUT_MS = 20_000L

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val jellyfinRepo = JellyfinRepository(application)
    private val updateChecker = UpdateChecker(application)
    val settingsContext = SettingsContext(
        selfUpdateEnabled = BuildConfig.SELF_UPDATE_ENABLED,
        diagnosticsAvailable = CrashReporting.SUPPORTS_DIAGNOSTICS
    )

    // Streams authenticate via Authorization header (set once prefs are read in init),
    // keeping the access token out of URLs on the publicly exposed server.
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Tellyfin")

    // Live TV: keep start-up buffering short so channel zapping feels instant
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 15_000,
            /* maxBufferMs = */ 50_000,
            /* bufferForPlaybackMs = */ 1_500,
            /* bufferForPlaybackAfterRebufferMs = */ 3_000
        )
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        .build()
        .also { player ->
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        switchStartedAtMs?.let {
                            diag("switch ready after ${SystemClock.elapsedRealtime() - it}ms " +
                                "(preloaded=$switchUsedPreload, buffered=${player.totalBufferedDuration}ms)")
                        }
                        switchStartedAtMs = null
                        streamRetryCount = 0
                        if (prebufferFailures.onPlaybackReady()) disablePrebufferAutomatically()
                    }
                    _uiState.value = _uiState.value.copy(
                        isBuffering = state == Player.STATE_BUFFERING,
                        error = if (state == Player.STATE_READY) null else _uiState.value.error
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    onStreamError(error)
                }
            })
            player.playWhenReady = true
        }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val lifecycle = PlaybackLifecycle()

    private var bannerDismissJob: Job? = null
    private var zapDismissJob: Job? = null
    private var progressReportJob: Job? = null
    private var streamRetryJob: Job? = null
    private var streamRetryCount = 0
    private var userId: String = ""
    private var preloadStartJob: Job? = null
    private val keyRepeatGuard = KeyRepeatGuard()
    // Diagnostics only: how long a switch takes to reach STATE_READY, logged under TellyfinPreload.
    private var switchStartedAtMs: Long? = null
    private var switchUsedPreload = false
    private val prebufferFailures = PrebufferFailureTracker()
    private val preloader = ChannelPreloader(
        context = application,
        player = exoPlayer,
        allocator = loadControl.allocator,
        dataSourceFactory = httpDataSourceFactory,
        onPreloadFailed = { channelId ->
            prebufferFailures.onPreloadFailed(channelId)
            onPreloadEvent(PreloadEvent.Failed(channelId))
        },
        onPreloadReady = { channelId -> onPreloadEvent(PreloadEvent.Ready(channelId)) }
    )

    init {
        breadcrumb("PlayerViewModel constructed")

        // A truly independent coroutine, not a withTimeoutOrNull wrapped around the sequence
        // below: if that sequence is stuck in a suspend call that doesn't actually honor
        // cancellation (some blocking-I/O-under-a-suspend-facade calls don't), cancelling its
        // Job never resumes it, and a timeout *wrapping* it would never return either. A plain
        // delay() on its own coroutine always fires on schedule regardless of what else hangs.
        val watchdog = viewModelScope.launch {
            delay(STARTUP_TIMEOUT_MS)
            val message = "Startup timed out after ${STARTUP_TIMEOUT_MS}ms restoring the saved session"
            breadcrumb(message)
            CrashReporting.captureMessage(message, ReportLevel.ERROR)
            _uiState.value = _uiState.value.copy(
                isLoadingChannels = false,
                startupError = "This is taking longer than expected. You can try signing in again."
            )
        }

        viewModelScope.launch {
            // finally, not a call at the end of the happy path: an early return@launch (no saved
            // session — shouldn't normally happen since this ViewModel is only ever constructed
            // once already logged in, but was the actual bug before that was fixed) used to leave
            // the watchdog running, so it fired 20s later and poisoned state that a much later,
            // real login would then immediately show as a stale error with no loading at all.
            try {
                breadcrumb("init coroutine started")
                val token = prefsRepo.accessToken.first() ?: return@launch
                breadcrumb("accessToken read")
                val url = prefsRepo.serverUrl.first() ?: return@launch
                breadcrumb("serverUrl read")
                userId = prefsRepo.userId.first() ?: return@launch
                breadcrumb("userId read")
                val lastIndex = prefsRepo.lastChannelIndex.first()
                val maxBitrate = prefsRepo.maxBitrate.first()
                val prebufferEnabled = prefsRepo.prebufferEnabled.first()
                val prebufferAutoDisabled = prefsRepo.prebufferAutoDisabled.first()
                val keybinds = Keybinds.parse(prefsRepo.keybinds.first())
                val prebufferDelayMs = Prebuffer.sanitizeStartDelay(prefsRepo.prebufferDelayMs.first())
                val countdownSettingMs = Prebuffer.sanitizeCountdown(prefsRepo.countdownMs.first())
                val diagnosticsEnabled = prefsRepo.diagnosticsEnabled.first()
                CrashReporting.setDiagnosticsEnabled(diagnosticsEnabled)
                val favIds = prefsRepo.favoriteIds.first()
                    .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                    .toSet()
                val username = prefsRepo.username.first()
                breadcrumb("remaining prefs read")
                jellyfinRepo.configure(url, token, userId)
                breadcrumb("jellyfinRepo.configure() done")
                ServerAuth.authHeader?.let { header ->
                    httpDataSourceFactory.setDefaultRequestProperties(mapOf("Authorization" to header))
                }
                _uiState.value = _uiState.value.copy(
                    maxBitrate = maxBitrate,
                    prebufferEnabled = prebufferEnabled,
                    prebufferAutoDisabled = prebufferAutoDisabled,
                    keybinds = keybinds,
                    prebufferDelayMs = prebufferDelayMs,
                    countdownSettingMs = countdownSettingMs,
                    diagnosticsEnabled = diagnosticsEnabled,
                    favoriteChannelIds = favIds,
                    username = username,
                    loadingStatus = "Loading channels…"
                )
                loadChannels(startIndex = lastIndex)
            } finally {
                watchdog.cancel()
            }
        }
        promptForUpdateIfAvailable()
        loadBranding()
        // A preload only makes sense while its preview banner is up. Confirming takes it
        // before the banner closes; any other way the banner goes away (Back, opening
        // another overlay) leaves it stale, so free the server-side stream right away.
        viewModelScope.launch {
            _uiState.map { it.overlay is Overlay.ChannelBanner }
                .distinctUntilChanged()
                .collect { bannerUp -> if (!bannerUp) cancelPreload() }
        }
    }

    /** Timestamped breadcrumb — visible in any event captured during this session, on sentry.io,
     *  without needing a live adb session to reproduce and capture locally. No-op on the
     *  no-Sentry flavor. */
    private fun breadcrumb(message: String) = CrashReporting.addBreadcrumb(message, "startup")

    /** Best-effort and separate from the channel/EPG load: a slow or failed branding fetch
     *  must never hold up (or be blamed for) the app actually becoming usable. */
    private fun loadBranding() {
        viewModelScope.launch {
            val url = prefsRepo.serverUrl.first() ?: return@launch
            breadcrumb("loadBranding() calling probeServer()/getBrandingOptions()")
            val info = runCatching { jellyfinRepo.probeServer(url) }.getOrNull()
            val branding = runCatching { jellyfinRepo.getBrandingOptions(url) }.getOrNull()
            breadcrumb("loadBranding() finished")
            _uiState.value = _uiState.value.copy(
                serverName = info?.serverName?.takeIf { it.isNotBlank() },
                splashscreenUrl = branding?.takeIf { it.splashscreenEnabled }?.let { splashscreenUrl(url) }
            )
        }
    }

    /** On start: once the splash is gone, offer to install a newer version. */
    private fun promptForUpdateIfAvailable() {
        if (!BuildConfig.SELF_UPDATE_ENABLED) return
        viewModelScope.launch {
            val remote = updateChecker.fetchLatestVersion() ?: return@launch
            if (!UpdateChecker.isNewer(remote, BuildConfig.VERSION_NAME)) return@launch
            uiState.first { !it.isLoadingChannels }
            val state = _uiState.value
            // Don't interrupt if the user already navigated somewhere
            if (state.channels.isEmpty() || state.overlay !is Overlay.None || state.isPlaying) return@launch
            _uiState.value = state.copy(
                overlay = Overlay.UpdatePrompt(remote),
                updateStatus = UpdateStatus.Available(remote),
                updatePromptButtonIndex = 0
            )
        }
    }

    /** Called from within the init sequence's own timeout window, so getChannels()/
     *  getEpgPrograms() hanging is caught by the same watchdog as the prefs reads. */
    private suspend fun loadChannels(startIndex: Int) {
        breadcrumb("loadChannels() calling getChannels()")
        try {
            val channels = jellyfinRepo.getChannels()
            breadcrumb("getChannels() returned ${channels.size} channels")
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
            breadcrumb("getChannels() failed: ${e.message}")
            _uiState.value = _uiState.value.copy(
                isLoadingChannels = false,
                error = "Failed to load channels: ${e.message}"
            )
        }
    }

    private suspend fun loadEpg() {
        breadcrumb("loadEpg() calling getEpgPrograms()")
        try {
            val ids = _uiState.value.channels.map { it.id }
            val programs = jellyfinRepo.getEpgPrograms(ids)
            breadcrumb("getEpgPrograms() returned")
            _uiState.value = _uiState.value.copy(
                epgData = programs,
                isLoadingChannels = false
            )
        } catch (e: Exception) {
            breadcrumb("getEpgPrograms() failed: ${e.message}")
            _uiState.value = _uiState.value.copy(isLoadingChannels = false)
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

    fun handleKeyEvent(rawKeyCode: Int): Boolean {
        val state = _uiState.value

        // The capture dialog needs the button exactly as pressed (including Back and Search);
        // everywhere else a user-added button stands in for its action's standard one.
        if (state.overlay is Overlay.Settings && state.settings.capture != null) {
            return handleSettingsKeys(rawKeyCode, state)
        }
        if (keyRepeatGuard.shouldSwallow(rawKeyCode)) return true
        // Holding OK / a Quick-menu button: the quick menu while watching, MENU anywhere else.
        val inPlayer = state.isPlaying && (state.overlay is Overlay.None || state.overlay is Overlay.ChannelBanner)
        val keyCode = routeQuickMenu(state.keybinds.resolve(rawKeyCode), inPlayer)

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
            state.overlay is Overlay.UpdatePrompt -> handleUpdatePromptKeys(keyCode, state)
            !state.isPlaying -> handleHomeKeys(keyCode, state)
            else -> handlePlayerKeys(keyCode, state)
        }
    }

    private fun handleBack(state: PlayerUiState): Boolean {
        return when {
            state.overlay is Overlay.Settings -> handleSettingsKeys(KeyEvent.KEYCODE_BACK, state)
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
                streamRetryJob?.cancel()
                exoPlayer.stop()
                _uiState.value = state.copy(
                    isPlaying = false,
                    overlay = Overlay.None,
                    error = null,
                    highlightedIndex = state.currentIndex,
                    homeFocusSection = HOME_SECTION_EPG,
                    nowPlayingCardIndex = state.currentIndex.coerceIn(0, (state.channels.size - 1).coerceAtLeast(0)),
                    epgFocusedBlockIndex = state.currentChannel
                        ?.let { currentProgramIndex(it.id, state.epgData) } ?: 0
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
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { stepGuideFocus(keyCode, state); true }
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

    private fun handleUpdatePromptKeys(keyCode: Int, state: PlayerUiState): Boolean {
        val version = (state.overlay as Overlay.UpdatePrompt).version
        val isDownloading = state.updateStatus is UpdateStatus.Downloading
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isDownloading) {
                    _uiState.value = state.copy(updatePromptButtonIndex = 1 - state.updatePromptButtonIndex)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when {
                    isDownloading -> Unit
                    state.updatePromptButtonIndex == 0 -> downloadUpdate(version)
                    else -> dismissOverlay()
                }
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
            // MENU opens the guide while watching; the quick menu moved to holding OK.
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> { openEpg(); true }
            KEYCODE_QUICK_MENU -> { openQuickMenu(); true }
            KeyEvent.KEYCODE_INFO -> { showChannelBanner(); true }
            // Same countdown-preview behavior as D-pad UP/DOWN — instant switching here
            // used to feel inconsistent with every other zap path, and the preview window
            // is what gives the new stream time to pre-buffer ahead of the actual switch.
            KeyEvent.KEYCODE_CHANNEL_UP -> { previewChannelUp(); true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { previewChannelDown(); true }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { onZapDigit(keyCode - KeyEvent.KEYCODE_0); true }
            else -> false
        }
    }

    private fun handleNowPlayingKeys(keyCode: Int, state: PlayerUiState): Boolean {
        return when (keyCode) {
            // Same countdown-preview behavior as every other zap path, D-pad or hardware
            // CHANNEL+/-.
            KeyEvent.KEYCODE_DPAD_UP -> { previewChannelUp(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { previewChannelDown(); true }
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
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                val step = if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                val n = (state.highlightedIndex + step + state.channels.size) % state.channels.size
                // A new row starts on what's on now, like the home screen's guide.
                val focus = state.channels.getOrNull(n)?.let { currentProgramIndex(it.id, state.epgData) } ?: 0
                _uiState.value = state.copy(highlightedIndex = n, epgFocusedBlockIndex = focus); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { stepGuideFocus(keyCode, state); true }
            // OK switches to the channel whichever programme is focused — it's live TV.
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
        val result = SettingsNavigator.onKey(
            state.settings,
            keyCode,
            settingsContext,
            SettingsValues(state.maxBitrate, state.prebufferDelayMs, state.countdownSettingMs, state.keybinds)
        )
        _uiState.value = _uiState.value.copy(settings = result.state)
        result.command?.let(::runSettingsCommand)
        return result.handled
    }

    private fun runSettingsCommand(command: SettingsCommand) {
        val state = _uiState.value
        when (command) {
            SettingsCommand.Close ->
                _uiState.value = state.copy(overlay = Overlay.None, settings = SettingsState())
            SettingsCommand.TogglePrebuffer -> setPrebufferEnabled(!state.prebufferEnabled)
            SettingsCommand.ToggleDiagnostics -> setDiagnosticsEnabled(!state.diagnosticsEnabled)
            SettingsCommand.ActivateUpdate -> when (val status = state.updateStatus) {
                is UpdateStatus.Available -> downloadUpdate(status.version)
                UpdateStatus.ReadyToInstall -> triggerInstall()
                else -> Unit
            }
            SettingsCommand.SignOut -> logOut()
            SettingsCommand.RestoreDefaults -> restoreAdvancedDefaults()
            is SettingsCommand.PickBitrate -> setMaxBitrate(command.bitrate)
            is SettingsCommand.PickPrebufferDelay -> {
                _uiState.value = state.copy(prebufferDelayMs = command.ms)
                viewModelScope.launch { prefsRepo.savePrebufferDelayMs(command.ms) }
            }
            is SettingsCommand.PickCountdown -> {
                _uiState.value = state.copy(countdownSettingMs = command.ms)
                viewModelScope.launch { prefsRepo.saveCountdownMs(command.ms) }
            }
            is SettingsCommand.SetExtraKey -> {
                command.keyCode?.let(keyRepeatGuard::arm)
                val keybinds = state.keybinds.withExtra(command.action, command.keyCode)
                _uiState.value = state.copy(keybinds = keybinds)
                viewModelScope.launch { prefsRepo.saveKeybinds(keybinds.serialize()) }
            }
        }
    }

    private fun setDiagnosticsEnabled(enabled: Boolean) {
        CrashReporting.setDiagnosticsEnabled(enabled)
        _uiState.value = _uiState.value.copy(diagnosticsEnabled = enabled)
        viewModelScope.launch { prefsRepo.saveDiagnosticsEnabled(enabled) }
    }

    /** Settings → Advanced → Restore defaults; bandwidth, pre-buffer on/off and account stay. */
    private fun restoreAdvancedDefaults() {
        CrashReporting.setDiagnosticsEnabled(false)
        _uiState.value = _uiState.value.copy(
            prebufferDelayMs = Prebuffer.DEFAULT_START_DELAY_MS,
            countdownSettingMs = Prebuffer.COUNTDOWN_AUTO,
            diagnosticsEnabled = false,
            keybinds = Keybinds()
        )
        viewModelScope.launch { prefsRepo.clearAdvancedSettings() }
    }

    private fun currentProgramIndex(channelId: UUID, epgData: Map<String, List<Program>>): Int {
        val now = java.time.Instant.now()
        val programs = epgData[channelId.toString()].orEmpty()
        val idx = programs.indexOfFirst { it.startTime <= now && it.endTime > now }
        return if (idx >= 0) idx else 0
    }

    private fun openSettings() {
        _uiState.value = _uiState.value.copy(overlay = Overlay.Settings, settings = SettingsState())
        checkForUpdate()
    }

    private fun checkForUpdate() {
        if (!BuildConfig.SELF_UPDATE_ENABLED) return
        if (_uiState.value.updateStatus is UpdateStatus.Checking ||
            _uiState.value.updateStatus is UpdateStatus.Downloading) return
        _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Checking)
        viewModelScope.launch {
            val remote = updateChecker.fetchLatestVersion()
            _uiState.value = if (remote == null) {
                _uiState.value.copy(updateStatus = UpdateStatus.Error("Could not reach update server"))
            } else if (UpdateChecker.isNewer(remote, BuildConfig.VERSION_NAME)) {
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
        val state = _uiState.value
        _uiState.value = state.copy(
            pendingInstallFile = null,
            // The system installer took over — drop the startup prompt behind it
            overlay = if (state.overlay is Overlay.UpdatePrompt) Overlay.None else state.overlay
        )
    }

    fun logOut() {
        viewModelScope.launch {
            prefsRepo.clearSession()
            ServerAuth.clear()
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
                // Already at the top result (or no results at all) — send focus back to the
                // text field instead of doing nothing, so the query is actually editable again.
                _uiState.value = if (state.searchResultIndex > 0)
                    state.copy(searchResultIndex = state.searchResultIndex - 1)
                else
                    state.copy(searchFieldFocused = true)
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
            delay(200)
            val state = _uiState.value
            val results = withContext(Dispatchers.Default) {
                ChannelSearch.search(query, state.channels, state.epgData)
            }
            _uiState.value = _uiState.value.copy(searchResults = results)
        }
    }

    fun openSearch() {
        _uiState.value = _uiState.value.copy(
            overlay = Overlay.Search,
            searchQuery = "",
            searchResultIndex = 0,
            searchResults = emptyList(),
            searchFieldFocused = true
        )
    }

    fun onSearchFieldFocusLeft() {
        _uiState.value = _uiState.value.copy(searchFieldFocused = false)
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
        // Taken before the banner closes: closing it releases any pending preload, and with
        // Main.immediate that observer can run inline during the state update below.
        // Switches that skip the banner (EPG, channel list, zap) simply find nothing here.
        val preloaded = _uiState.value.channels.getOrNull(index)
            ?.let { preloader.take(it.id, _uiState.value.maxBitrate) }
        _uiState.value = _uiState.value.copy(currentIndex = index, highlightedIndex = index, overlay = Overlay.None)
        playChannel(index, preloaded)
    }

    /** Transient live-stream hiccups are common; retry quietly before surfacing an error. */
    private fun onStreamError(error: PlaybackException) {
        if (!_uiState.value.isPlaying) return
        // Losing the decoder on the way to the background is not a stream fault, and
        // retrying it would race the teardown; onEnterForeground re-prepares instead.
        if (!lifecycle.isForeground) return
        if (streamRetryCount < 2) {
            streamRetryCount++
            _uiState.value = _uiState.value.copy(isBuffering = true, error = null)
            streamRetryJob?.cancel()
            streamRetryJob = viewModelScope.launch {
                delay(2_000L * streamRetryCount)
                exoPlayer.prepare()
            }
        } else {
            prebufferFailures.onPlaybackFailed()
            _uiState.value = _uiState.value.copy(
                isBuffering = false,
                error = "Stream error: ${error.errorCodeName}"
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun playChannel(index: Int, preloaded: PreloadMediaSource? = null) {
        val channel = _uiState.value.channels.getOrNull(index) ?: return
        stopProgressReporting(channel.id)
        streamRetryJob?.cancel()
        streamRetryCount = 0
        prebufferFailures.onSwitch(channel.id)
        cancelPreload()
        switchStartedAtMs = SystemClock.elapsedRealtime()
        switchUsedPreload = preloaded != null
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBuffering = true, error = null)
            try {
                setPlayerSource(preloaded) {
                    jellyfinRepo.getStreamUrl(channel.id, userId, _uiState.value.maxBitrate)
                }
                exoPlayer.prepare()
                prefsRepo.saveLastChannelIndex(index)
                jellyfinRepo.reportPlaybackStart(channel.id)
                startProgressReporting(channel.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Stream error: ${e.message}")
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun setPlayerSource(preloaded: PreloadMediaSource?, streamUrl: suspend () -> String) {
        if (preloaded != null) exoPlayer.setMediaSource(preloaded)
        else exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl()))
        preloader.onPlayerSourceSet(preloaded)
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
        cancelPreload()
        _uiState.value = _uiState.value.copy(highlightedIndex = index, overlay = Overlay.ChannelBanner)
        val state = _uiState.value
        bannerDismissJob = viewModelScope.launch {
            delay(Prebuffer.countdownMs(state.prebufferEnabled, state.countdownSettingMs))
            if (_uiState.value.overlay is Overlay.ChannelBanner) confirmChannelSwitch()
        }
        val channel = state.channels.getOrNull(index) ?: return
        // Browsing back onto the channel already playing needs no second stream.
        if (!state.prebufferEnabled || index == state.currentIndex) return
        preloadStartJob = viewModelScope.launch {
            // Only once the highlight has rested: flicking past channels never opens a tuner.
            delay(state.prebufferDelayMs)
            val maxBitrate = _uiState.value.maxBitrate
            preloader.start(channel.id, maxBitrate, jellyfinRepo.getStreamUrl(channel.id, userId, maxBitrate))
            onPreloadEvent(PreloadEvent.Started(channel.id))
        }
    }

    private fun cancelPreload() {
        preloadStartJob?.cancel()
        preloadStartJob = null
        preloader.cancel()
        onPreloadEvent(PreloadEvent.Cleared)
    }

    private fun onPreloadEvent(event: PreloadEvent) {
        val state = _uiState.value
        val status = state.preloadStatus.after(event)
        if (status != state.preloadStatus) _uiState.value = state.copy(preloadStatus = status)
    }

    private fun confirmChannelSwitch() {
        val state = _uiState.value
        if (state.highlightedIndex != state.currentIndex) switchToChannel(state.highlightedIndex)
        else _uiState.value = state.copy(overlay = Overlay.None)
    }

    private fun openEpg() {
        val state = _uiState.value
        _uiState.value = state.copy(
            overlay = Overlay.Epg,
            highlightedIndex = state.currentIndex,
            epgFocusedBlockIndex = state.currentChannel?.let { currentProgramIndex(it.id, state.epgData) } ?: 0
        )
    }

    /**
     * Left/Right in either guide. Only ever steps between programmes the guide actually draws —
     * ones that ended before its window, or hidden duplicates, would take focus somewhere invisible.
     */
    private fun stepGuideFocus(keyCode: Int, state: PlayerUiState) {
        val programs = state.highlightedChannel?.let { state.epgData[it.id.toString()] }.orEmpty()
        val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
        _uiState.value = state.copy(
            epgFocusedBlockIndex = guideStepFocus(
                programs, guideWindowStart(java.time.Instant.now()), state.epgFocusedBlockIndex, delta
            )
        )
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
        _uiState.value = _uiState.value.copy(overlay = Overlay.None, settings = SettingsState())
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

    private fun setPrebufferEnabled(enabled: Boolean) {
        if (!enabled) cancelPreload()
        _uiState.value = _uiState.value.copy(prebufferEnabled = enabled, prebufferAutoDisabled = false)
        viewModelScope.launch { prefsRepo.savePrebuffer(enabled, autoDisabled = false) }
    }

    /** The server couldn't serve a second stream alongside the current one (see [PrebufferFailureTracker]). */
    private fun disablePrebufferAutomatically() {
        CrashReporting.addBreadcrumb("Pre-buffering turned off automatically after a failed preload", "playback")
        cancelPreload()
        _uiState.value = _uiState.value.copy(prebufferEnabled = false, prebufferAutoDisabled = true)
        viewModelScope.launch { prefsRepo.savePrebuffer(enabled = false, autoDisabled = true) }
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

    // ── App lifecycle ───────────────────────────────────────────────────────

    /**
     * Called from the activity's onStop. Leaves [PlayerUiState.isPlaying] set so the
     * player screen is still showing on return, and lets [onEnterForeground] re-prepare.
     */
    fun onEnterBackground() {
        if (!lifecycle.onBackground(_uiState.value.isPlaying)) return
        streamRetryJob?.cancel()
        streamRetryCount = 0
        stopProgressReporting(_uiState.value.currentChannel?.id)
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        preloader.release()
        _uiState.value = _uiState.value.copy(isBuffering = false, error = null)
    }

    /** Called from the activity's onStart; re-prepares the channel from the live edge. */
    fun onEnterForeground() {
        if (!lifecycle.onForeground()) return
        playChannel(_uiState.value.currentIndex)
    }

    override fun onCleared() {
        stopProgressReporting(_uiState.value.currentChannel?.id)
        preloader.release()
        exoPlayer.release()
    }
}
