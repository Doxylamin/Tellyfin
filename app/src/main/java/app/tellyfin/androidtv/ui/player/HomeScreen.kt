package app.tellyfin.androidtv.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())


@Composable
fun HomeScreen(
    channels: List<Channel>,
    highlightedIndex: Int,
    epgData: Map<String, List<Program>>,
    favoriteChannelIds: Set<UUID>,
    homeNavTabIndex: Int,
    homeFocusSection: Int,
    nowPlayingCardIndex: Int,
    epgFocusedBlockIndex: Int,
    serverName: String? = null,
    modifier: Modifier = Modifier
) {
    // Ticking "now" so progress bars, LIVE states and the now-line stay fresh
    // while the home screen sits open.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = Instant.now()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Purple)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TopNavBar(
                    navTabIndex = homeNavTabIndex,
                    isFocused = homeFocusSection == HOME_SECTION_NAV,
                    serverName = serverName,
                    modifier = Modifier.fillMaxWidth()
                )

                when (homeNavTabIndex) {
                    NAV_FOR_YOU -> ForYouContent(
                        channels = channels,
                        epgData = epgData,
                        favoriteChannelIds = favoriteChannelIds,
                        now = now,
                        homeFocusSection = homeFocusSection,
                        nowPlayingCardIndex = nowPlayingCardIndex,
                        modifier = Modifier.weight(1f)
                    )
                    else -> LiveEpgContent(
                        channels = channels,
                        epgData = epgData,
                        favoriteChannelIds = favoriteChannelIds,
                        now = now,
                        homeFocusSection = homeFocusSection,
                        highlightedIndex = highlightedIndex,
                        epgFocusedBlockIndex = epgFocusedBlockIndex,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ── Top Navigation Bar ────────────────────────────────────────────────────────

@Composable
private fun TopNavBar(
    navTabIndex: Int,
    isFocused: Boolean,
    serverName: String?,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        stringResource(R.string.nav_for_you),
        stringResource(R.string.nav_live),
        stringResource(R.string.nav_search),
        stringResource(R.string.nav_settings)
    )

    Row(
        modifier = modifier.padding(horizontal = 48.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AppColors.Purple.copy(alpha = 0.25f))
                .border(1.dp, AppColors.Purple.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                (serverName?.firstOrNull() ?: 'T').uppercaseChar().toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Purple
            )
        }

        Spacer(Modifier.width(20.dp))

        // Tab pills
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { idx, label ->
                val isActive = idx == navTabIndex
                val isTabFocused = isFocused && idx == navTabIndex
                Box(
                    modifier = Modifier
                        .background(
                            when {
                                isTabFocused -> AppColors.Purple.copy(alpha = 0.25f)
                                isActive -> Color(0xFF242424)
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(20.dp)
                        )
                        .then(
                            if (isActive || isTabFocused) Modifier.border(
                                1.dp,
                                if (isTabFocused) AppColors.Purple else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(20.dp)
                            ) else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        color = when {
                            isTabFocused || isActive -> Color.White
                            else -> Color.White.copy(alpha = 0.38f)
                        },
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Ticking clock
        var clockTime by remember { mutableStateOf(timeFmt.format(Instant.now())) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000L)
                clockTime = timeFmt.format(Instant.now())
            }
        }
        Text(
            clockTime,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.45f),
            fontWeight = FontWeight.Medium
        )
    }
}

// ── For You Tab ───────────────────────────────────────────────────────────────

@Composable
private fun ForYouContent(
    channels: List<Channel>,
    epgData: Map<String, List<Program>>,
    favoriteChannelIds: Set<UUID>,
    now: Instant,
    homeFocusSection: Int,
    nowPlayingCardIndex: Int,
    modifier: Modifier = Modifier
) {
    val favorites = channels.filter { it.id in favoriteChannelIds }

    if (favorites.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("☆", fontSize = 36.sp, color = Color.White.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.no_favorites_title),
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.no_favorites_hint),
                    color = Color.White.copy(alpha = 0.18f),
                    fontSize = 11.sp
                )
            }
        }
    } else {
        Column(modifier = modifier) {
            NowPlayingRow(
                channels = favorites,
                epgData = epgData,
                now = now,
                focusedCardIndex = nowPlayingCardIndex,
                isSectionFocused = homeFocusSection == HOME_SECTION_CAROUSEL
            )
        }
    }
}

@Composable
private fun NowPlayingRow(
    channels: List<Channel>,
    epgData: Map<String, List<Program>>,
    now: Instant,
    focusedCardIndex: Int,
    isSectionFocused: Boolean
) {
    Column(modifier = Modifier.padding(start = 48.dp)) {
        Text(
            stringResource(R.string.now_playing),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val rowState = rememberLazyListState()
        LaunchedEffect(focusedCardIndex) {
            if (channels.isNotEmpty()) {
                rowState.animateScrollToItem(focusedCardIndex.coerceIn(0, channels.size - 1))
            }
        }

        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 48.dp)
        ) {
            itemsIndexed(channels) { index, channel ->
                val programs = epgData[channel.id.toString()].orEmpty()
                val current = programs.firstOrNull { it.startTime <= now && it.endTime > now }
                    ?: channel.currentProgram
                NowPlayingCard(
                    channel = channel,
                    program = current,
                    now = now,
                    isFocused = isSectionFocused && index == focusedCardIndex
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    channel: Channel,
    program: Program?,
    now: Instant,
    isFocused: Boolean
) {
    val cardWidth = 280.dp
    val cardHeight = 160.dp
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )

    Column(
        modifier = Modifier
            .width(cardWidth)
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.Surface)
                .then(
                    if (isFocused) Modifier.border(2.dp, AppColors.Purple, RoundedCornerShape(10.dp))
                    else Modifier
                )
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.20f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Black.copy(alpha = 0.25f),
                            1f to Color.Black.copy(alpha = 0.80f)
                        )
                    )
            )
            // LIVE badge (red)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(AppColors.Red, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    stringResource(R.string.live_badge),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Channel logo top-right
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            )
            // Progress bar + remaining time at card bottom
            if (program != null) {
                val duration = (program.endTime.epochSecond - program.startTime.epochSecond).coerceAtLeast(1)
                val elapsed = (now.epochSecond - program.startTime.epochSecond).coerceIn(0, duration)
                val remaining = (duration - elapsed) / 60
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { elapsed.toFloat() / duration.toFloat() },
                        modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = AppColors.Red,
                        trackColor = Color.White.copy(alpha = 0.20f)
                    )
                    Text("${remaining}m", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                }
            }
        }

        // Title and channel name below the card
        Spacer(Modifier.height(6.dp))
        Text(
            program?.title ?: channel.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(cardWidth)
        )
        Text(
            channel.name,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(cardWidth)
        )
    }
}

// ── Live Tab (full-page EPG) ──────────────────────────────────────────────────

@Composable
private fun LiveEpgContent(
    channels: List<Channel>,
    epgData: Map<String, List<Program>>,
    favoriteChannelIds: Set<UUID>,
    now: Instant,
    homeFocusSection: Int,
    highlightedIndex: Int,
    epgFocusedBlockIndex: Int,
    modifier: Modifier = Modifier
) {
    val sortedChannels = remember(channels, favoriteChannelIds) {
        buildList {
            addAll(channels.filter { it.id in favoriteChannelIds })
            addAll(channels.filter { it.id !in favoriteChannelIds })
        }
    }
    val focusedChannel = channels.getOrNull(highlightedIndex)
    val focusedProgram = focusedChannel?.let {
        guideHeroProgram(epgData[it.id.toString()].orEmpty(), epgFocusedBlockIndex, now)
    }

    Column(modifier = modifier) {
        GuideHero(
            channel = focusedChannel,
            program = focusedProgram,
            now = now,
            modifier = Modifier.fillMaxWidth().height(112.dp)
        )
        GuideGrid(
            channels = sortedChannels,
            epgData = epgData,
            now = now,
            highlightedChannelId = focusedChannel?.id,
            isFocused = homeFocusSection == HOME_SECTION_EPG,
            modifier = Modifier.weight(1f),
            focusedBlockIndex = epgFocusedBlockIndex
        )
        GuideHintBar(stringResource(R.string.guide_home_hint))
    }
}
