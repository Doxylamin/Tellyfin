package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import app.tellyfin.androidtv.ui.theme.AppColors

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onLogOut: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            // Settings full-screen overlay (can be reached from QuickMenu or home)
            state.overlay is Overlay.Settings -> {
                SettingsScreen(
                    serverUrl = viewModel.jellyfinRepo.baseUrl,
                    currentBitrate = state.maxBitrate,
                    highlightedIndex = state.highlightedMenuIndex,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Home / channel browser (not yet playing)
            !state.isPlaying -> {
                HomeScreen(
                    channels = state.channels,
                    highlightedIndex = state.highlightedIndex,
                    epgData = state.epgData,
                    favoriteChannelIds = state.favoriteChannelIds,
                    filterTab = state.filterTab,
                    homeFocusSection = state.homeFocusSection,
                    nowPlayingCardIndex = state.nowPlayingCardIndex
                )
            }

            // Playback view
            else -> {
                VideoPlayer(viewModel = viewModel)

                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = AppColors.Purple,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.error?.let { err ->
                    Text(
                        text = err,
                        color = Color.Red,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp)
                    )
                }

                // Channel banner: shows the preview channel while current stream keeps
                // playing. isPreview drives the countdown arc; false for INFO-button banner.
                val isBannerVisible = state.overlay is Overlay.ChannelBanner
                val isPreview = isBannerVisible && state.highlightedIndex != state.currentIndex
                val bannerChannel = state.channels.getOrNull(state.highlightedIndex)
                    ?: state.currentChannel
                bannerChannel?.let { channel ->
                    ChannelBanner(
                        channel = channel,
                        visible = isBannerVisible,
                        isPreview = isPreview,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Numeric zap input
                if (state.overlay is Overlay.ZapInput) {
                    ZapInputDisplay(
                        digits = (state.overlay as Overlay.ZapInput).digits,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Channel list slide-in
                ChannelListOverlay(
                    channels = state.channels,
                    currentIndex = state.currentIndex,
                    highlightedIndex = state.highlightedIndex,
                    favoriteChannelIds = state.favoriteChannelIds,
                    visible = state.overlay is Overlay.ChannelList
                )

                // Now Playing info panel (OK button while watching)
                state.currentChannel?.let { channel ->
                    if (state.overlay is Overlay.NowPlaying) {
                        val programs = state.epgData[channel.id.toString()].orEmpty()
                        NowPlayingOverlay(
                            channel = channel,
                            programs = programs,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        )
                    }
                }

                // Quick menu slide-in from right (Menu button)
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    val ch = state.currentChannel
                    val prog = ch?.let { state.epgData[it.id.toString()]?.firstOrNull { p ->
                        val now = java.time.Instant.now()
                        p.startTime <= now && p.endTime > now
                    } ?: it.currentProgram }
                    QuickMenuOverlay(
                        visible = state.overlay is Overlay.QuickMenu,
                        highlightedIndex = state.highlightedMenuIndex,
                        isFavorite = ch?.id?.let { it in state.favoriteChannelIds } ?: false,
                        channel = ch,
                        currentProgram = prog
                    )
                }
            }
        }

        // EPG overlay is accessible from any state
        EpgOverlay(
            channels = state.channels,
            epgData = state.epgData,
            currentChannelIndex = state.currentIndex,
            highlightedRow = state.highlightedIndex,
            visible = state.overlay is Overlay.Epg
        )

        // Search overlay is accessible from any state
        SearchOverlay(
            query = state.searchQuery,
            resultIndex = state.searchResultIndex,
            results = state.searchResults,
            favoriteChannelIds = state.favoriteChannelIds,
            visible = state.overlay is Overlay.Search,
            onQueryChange = { viewModel.updateSearchQuery(it) }
        )
    }
}

@Composable
private fun VideoPlayer(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = viewModel.exoPlayer
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    DisposableEffect(Unit) {
        onDispose { /* player lifecycle managed by ViewModel */ }
    }
}

@Composable
private fun ZapInputDisplay(digits: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digits,
            fontSize = 64.sp,
            color = Color.White
        )
    }
}
