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
private val dateFmt = DateTimeFormatter.ofPattern("EEE dd.MM").withZone(ZoneId.systemDefault())

// 4 dp per minute → 1 hour = 240 dp, 5-hour window = 1200 dp total
private const val EPG_PX_PER_MIN = 4.0f
private const val EPG_WINDOW_MINUTES = 300  // 5 hours
private val EPG_ROW_HEIGHT = 52.dp
private val EPG_RULER_HEIGHT = 32.dp
private val EPG_CHANNEL_COL_WIDTH = 72.dp

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

        Text(
            stringResource(R.string.home_hint),
            fontSize = 10.sp,
            color = AppColors.OnSurface.copy(alpha = 0.20f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
    }
}

// ── Top Navigation Bar ────────────────────────────────────────────────────────

@Composable
private fun TopNavBar(
    navTabIndex: Int,
    isFocused: Boolean,
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
            Text("T", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.Purple)
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
    val highlightedChannelId = channels.getOrNull(highlightedIndex)?.id
    val focusedChannel = channels.getOrNull(highlightedIndex)
    val focusedPrograms = focusedChannel?.let { epgData[it.id.toString()].orEmpty() } ?: emptyList()
    val focusedProgram = focusedPrograms.getOrNull(epgFocusedBlockIndex)
        ?: focusedPrograms.firstOrNull { it.startTime <= now && it.endTime > now }
        ?: focusedPrograms.firstOrNull()

    Column(modifier = modifier) {
        EpgHeroSection(
            channel = focusedChannel,
            program = focusedProgram,
            now = now,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
        HomeEpgGrid(
            channels = sortedChannels,
            epgData = epgData,
            now = now,
            highlightedChannelId = highlightedChannelId,
            epgFocusedBlockIndex = epgFocusedBlockIndex,
            isSectionFocused = homeFocusSection == HOME_SECTION_EPG,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EpgHeroSection(
    channel: Channel?,
    program: Program?,
    now: Instant,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = channel?.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.96f),
                        0.6f to Color.Black.copy(alpha = 0.80f),
                        1f to Color.Black.copy(alpha = 0.55f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 16.dp)
        ) {
            // Top bar
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.epg_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.epg_ok_hint),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.38f)
                )
                Spacer(Modifier.weight(1f))
                var clockTime by remember { mutableStateOf(timeFmt.format(Instant.now())) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(30_000L)
                        clockTime = timeFmt.format(Instant.now())
                    }
                }
                Text(
                    "${dateFmt.format(now)} · $clockTime",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Program detail card
            if (program != null || channel != null) {
                Column(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        program?.title ?: channel?.name ?: "",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (program != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isLive = program.startTime <= now && program.endTime > now
                            if (isLive) MetadataBadge(stringResource(R.string.live_badge), AppColors.Red)
                            program.genre?.let { if (it.isNotBlank()) MetadataBadge(it) }
                            channel?.let { MetadataBadge(it.name) }
                        }
                        program.description?.let { desc ->
                            if (desc.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    desc,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.60f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        val duration = (program.endTime.epochSecond - program.startTime.epochSecond).coerceAtLeast(1)
                        val elapsed = (now.epochSecond - program.startTime.epochSecond).coerceIn(0, duration)
                        val remaining = ((duration - elapsed) / 60).toInt()
                        val progress = elapsed.toFloat() / duration.toFloat()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${timeFmt.format(program.startTime)} – ${timeFmt.format(program.endTime)}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                            Text(
                                "${remaining} min",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = AppColors.Red,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(label: String, color: Color = Color.White.copy(alpha = 0.45f)) {
    Box(
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ── Inline EPG Grid ───────────────────────────────────────────────────────────

@Composable
private fun HomeEpgGrid(
    channels: List<Channel>,
    epgData: Map<String, List<Program>>,
    now: Instant,
    highlightedChannelId: UUID?,
    epgFocusedBlockIndex: Int,
    isSectionFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val windowStart = remember(now) {
        Instant.ofEpochSecond((now.epochSecond / 1800L) * 1800L - 1800L)
    }
    val windowEndSec = windowStart.epochSecond + EPG_WINDOW_MINUTES * 60L
    val totalWidthDp = (EPG_WINDOW_MINUTES * EPG_PX_PER_MIN).dp

    val nowOffsetDp = ((now.epochSecond - windowStart.epochSecond) / 60f) * EPG_PX_PER_MIN

    val ticks = remember(windowStart) {
        val count = EPG_WINDOW_MINUTES / 30 + 1
        (0 until count).map { i ->
            val xDp = i * 30f * EPG_PX_PER_MIN
            val label = timeFmt.format(Instant.ofEpochSecond(windowStart.epochSecond + i * 1800L))
            Pair(xDp, label)
        }
    }

    val initialScroll = remember { ((nowOffsetDp - 100f).coerceAtLeast(0f)).roundToInt() }
    val hScroll = rememberScrollState(initial = initialScroll)
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { hScroll.scrollTo(initialScroll) }

    // Scroll horizontally to keep the focused program block visible
    LaunchedEffect(epgFocusedBlockIndex, isSectionFocused) {
        if (!isSectionFocused) return@LaunchedEffect
        val programs = epgData[highlightedChannelId?.toString()].orEmpty()
        val program = programs.getOrNull(epgFocusedBlockIndex) ?: return@LaunchedEffect
        val blockStartDp = ((program.startTime.epochSecond - windowStart.epochSecond) / 60f) * EPG_PX_PER_MIN
        val target = (blockStartDp - 80f).coerceAtLeast(0f).roundToInt()
        hScroll.animateScrollTo(target)
    }

    val highlightedDisplayedIndex = channels.indexOfFirst { it.id == highlightedChannelId }
    LaunchedEffect(highlightedDisplayedIndex, isSectionFocused) {
        if (isSectionFocused && channels.isNotEmpty() && highlightedDisplayedIndex >= 0) {
            // +1 because ruler is item 0
            listState.animateScrollToItem((highlightedDisplayedIndex - 1).coerceAtLeast(0) + 1)
        }
    }

    // Convert scroll pixels → dp for the fixed-position "now" line overlay
    val density = LocalDensity.current
    val nowLineX by remember(density, nowOffsetDp) {
        derivedStateOf { with(density) { nowOffsetDp.dp - hScroll.value.toDp() } }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.epg_on_now),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.38f),
                modifier = Modifier.width(EPG_CHANNEL_COL_WIDTH)
            )
            Text(
                dateFmt.format(now),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.28f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            // Single LazyColumn: ruler + channel rows (vertical scroll synced automatically)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // Ruler header
                item {
                    Row(modifier = Modifier.fillMaxWidth().height(EPG_RULER_HEIGHT)) {
                        Spacer(Modifier.width(EPG_CHANNEL_COL_WIDTH))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(hScroll)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(totalWidthDp)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.025f))
                            ) {
                                ticks.forEach { (xDp, label) ->
                                    Text(
                                        label,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.42f),
                                        modifier = Modifier
                                            .offset(x = xDp.dp)
                                            .padding(top = 9.dp, start = 4.dp)
                                    )
                                }
                                // ▼ marker sits inside the scrollable ruler, aligned to content
                                Text(
                                    "▼",
                                    fontSize = 9.sp,
                                    color = AppColors.Red,
                                    modifier = Modifier.offset(x = (nowOffsetDp - 4f).dp, y = 4.dp)
                                )
                            }
                        }
                    }
                }
                // Channel + program rows — same LazyColumn keeps them vertically in sync
                itemsIndexed(channels) { index, channel ->
                    val isHighlighted = isSectionFocused && channel.id == highlightedChannelId
                    val programs = epgData[channel.id.toString()].orEmpty()
                    Row(modifier = Modifier.fillMaxWidth().height(EPG_ROW_HEIGHT)) {
                        EpgChannelCell(channel = channel, isHighlighted = isHighlighted, isAlternate = index % 2 == 0)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(hScroll)
                        ) {
                            HomeEpgRow(
                                programs = programs,
                                isHighlighted = isHighlighted,
                                isAlternate = index % 2 == 0,
                                focusedBlockIndex = if (isHighlighted) epgFocusedBlockIndex else -1,
                                now = now,
                                windowStartSec = windowStart.epochSecond,
                                windowEndSec = windowEndSec,
                                totalWidthDp = totalWidthDp,
                                pxPerMin = EPG_PX_PER_MIN,
                                rowHeight = EPG_ROW_HEIGHT
                            )
                        }
                    }
                }
            }

            // Red "now" line — fixed overlay, correctly offset using px→dp conversion
            Box(
                modifier = Modifier
                    .padding(start = EPG_CHANNEL_COL_WIDTH)
                    .offset(x = nowLineX)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(AppColors.Red.copy(alpha = 0.75f))
            )
        }
    }
}

@Composable
private fun EpgChannelCell(
    channel: Channel,
    isHighlighted: Boolean,
    isAlternate: Boolean
) {
    Box(
        modifier = Modifier
            .width(EPG_CHANNEL_COL_WIDTH)
            .height(EPG_ROW_HEIGHT)
            .background(
                when {
                    isHighlighted -> Color.White.copy(alpha = 0.10f)
                    isAlternate -> Color.White.copy(alpha = 0.015f)
                    else -> Color.Transparent
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (isHighlighted) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(AppColors.Purple))
        }
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "${channel.number}",
                fontSize = 9.sp,
                color = if (isHighlighted) AppColors.Purple else AppColors.Purple.copy(alpha = 0.55f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(20.dp)
            )
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }
    }
}

@Composable
private fun HomeEpgRow(
    programs: List<Program>,
    isHighlighted: Boolean,
    isAlternate: Boolean,
    focusedBlockIndex: Int,
    now: Instant,
    windowStartSec: Long,
    windowEndSec: Long,
    totalWidthDp: Dp,
    pxPerMin: Float,
    rowHeight: Dp
) {
    val rowBg = when {
        isHighlighted -> Color.White.copy(alpha = 0.07f)
        isAlternate -> Color.White.copy(alpha = 0.015f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .height(rowHeight)
            .width(totalWidthDp)
            .background(rowBg)
    ) {
        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.03f)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    stringResource(R.string.no_data),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        } else {
            programs.forEachIndexed { blockIdx, program ->
                // Clip to visible window
                val startSec = maxOf(program.startTime.epochSecond, windowStartSec)
                val endSec = minOf(program.endTime.epochSecond, windowEndSec)
                if (endSec <= windowStartSec || startSec >= windowEndSec) return@forEachIndexed

                val xDp = ((startSec - windowStartSec) / 60f * pxPerMin).dp
                val widthDp = ((endSec - startSec) / 60f * pxPerMin).dp.coerceAtLeast(2.dp)

                val isLive = now.isAfter(program.startTime) && now.isBefore(program.endTime)
                val isBlockFocused = isHighlighted && blockIdx == focusedBlockIndex

                Box(
                    modifier = Modifier
                        .offset(x = xDp)
                        .width(widthDp)
                        .height(rowHeight)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isBlockFocused -> Brush.horizontalGradient(
                                    listOf(AppColors.Purple.copy(alpha = 0.55f), AppColors.Purple.copy(alpha = 0.38f))
                                )
                                isLive -> Brush.horizontalGradient(
                                    listOf(AppColors.Red.copy(alpha = 0.30f), AppColors.Red.copy(alpha = 0.15f))
                                )
                                else -> Brush.horizontalGradient(
                                    listOf(Color(0xFF1E1E32), Color(0xFF252538))
                                )
                            }
                        )
                        .then(
                            if (isBlockFocused) Modifier.border(
                                1.dp, AppColors.Purple.copy(alpha = 0.55f), RoundedCornerShape(4.dp)
                            ) else Modifier
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLive) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.Red)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            program.title,
                            fontSize = 11.sp,
                            color = if (isBlockFocused || isLive) Color.White else Color.White.copy(alpha = 0.72f),
                            fontWeight = if (isBlockFocused || isLive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
