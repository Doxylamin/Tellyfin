package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun HomeScreen(
    channels: List<Channel>,
    highlightedIndex: Int,
    epgData: Map<String, List<Program>>,
    favoriteChannelIds: Set<UUID>,
    showFavoritesOnly: Boolean,
    homeFocusSection: Int,
    nowPlayingCardIndex: Int,
    modifier: Modifier = Modifier
) {
    val now = remember { Instant.now() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 28.dp, end = 48.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "JellyTV",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Purple
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Live TV",
                    fontSize = 13.sp,
                    color = AppColors.OnSurface.copy(alpha = 0.4f)
                )
            }

            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Purple)
                }
                return@Column
            }

            Spacer(Modifier.height(20.dp))

            // ── Section 1: "Läuft jetzt" horizontal card row ─────────────────
            val displayedChannels = if (showFavoritesOnly)
                channels.filter { it.id in favoriteChannelIds }
            else
                channels

            val cardRowFocused = homeFocusSection == HOME_SECTION_CARDS

            NowPlayingRow(
                channels = displayedChannels,
                epgData = epgData,
                now = now,
                focusedCardIndex = nowPlayingCardIndex,
                isSectionFocused = cardRowFocused
            )

            Spacer(Modifier.height(24.dp))

            // ── Section 2: Filter tabs ────────────────────────────────────────
            val filterFocused = homeFocusSection == HOME_SECTION_FILTER
            FilterTabs(
                showFavoritesOnly = showFavoritesOnly,
                isSectionFocused = filterFocused,
                favoriteCount = channels.count { it.id in favoriteChannelIds }
            )

            Spacer(Modifier.height(16.dp))

            // ── Section 3: Channel/EPG list ───────────────────────────────────
            val channelListFocused = homeFocusSection == HOME_SECTION_CHANNELS
            val sortedChannels = buildList {
                addAll(channels.filter { it.id in favoriteChannelIds })
                addAll(channels.filter { it.id !in favoriteChannelIds })
            }.let { sorted ->
                if (showFavoritesOnly) channels.filter { it.id in favoriteChannelIds } else sorted
            }

            ChannelEpgList(
                channels = sortedChannels,
                epgData = epgData,
                favoriteChannelIds = favoriteChannelIds,
                now = now,
                highlightedChannelId = channels.getOrNull(highlightedIndex)?.id,
                isSectionFocused = channelListFocused,
                modifier = Modifier.weight(1f)
            )
        }

        // Bottom hint bar
        Text(
            "↑↓ Navigate    OK Watch    ← → Cards    Menu Quick Actions",
            fontSize = 10.sp,
            color = AppColors.OnSurface.copy(alpha = 0.25f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
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
            "Läuft jetzt",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val rowState = rememberLazyListState()
        LaunchedEffect(focusedCardIndex) {
            if (channels.isNotEmpty()) {
                rowState.animateScrollToItem(focusedCardIndex.coerceIn(0, channels.size - 1))
            }
        }

        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
    val cardWidth = 220.dp
    val cardHeight = 130.dp // ~16:9

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
        // Background channel logo blurred/dimmed as hero image
        AsyncImage(
            model = channel.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.18f,
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient overlay bottom-up
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
        )

        // LIVE badge top-left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color(0xFFCC0000), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        // Channel logo top-right
        AsyncImage(
            model = channel.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f))
        )

        // Bottom info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (program != null) {
                Text(
                    program.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val duration = (program.endTime.epochSecond - program.startTime.epochSecond).coerceAtLeast(1)
                val elapsed = (now.epochSecond - program.startTime.epochSecond).coerceIn(0, duration)
                val remaining = (duration - elapsed) / 60
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { elapsed.toFloat() / duration.toFloat() },
                        modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = AppColors.Purple,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Text("${remaining}m", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                }
            } else {
                Text(
                    channel.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FilterTabs(
    showFavoritesOnly: Boolean,
    isSectionFocused: Boolean,
    favoriteCount: Int
) {
    Row(
        modifier = Modifier.padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterTab(
            label = "Alle Kanäle",
            isSelected = !showFavoritesOnly,
            isFocused = isSectionFocused && !showFavoritesOnly
        )
        FilterTab(
            label = "Lieblingskanäle ($favoriteCount)",
            isSelected = showFavoritesOnly,
            isFocused = isSectionFocused && showFavoritesOnly
        )
    }
}

@Composable
private fun FilterTab(
    label: String,
    isSelected: Boolean,
    isFocused: Boolean
) {
    val bg = when {
        isFocused -> AppColors.Purple.copy(alpha = 0.3f)
        isSelected -> AppColors.Purple.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val border = when {
        isFocused -> AppColors.Purple
        isSelected -> AppColors.Purple.copy(alpha = 0.5f)
        else -> AppColors.OnSurface.copy(alpha = 0.2f)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (isSelected || isFocused) Color.White else AppColors.OnSurface.copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ChannelEpgList(
    channels: List<Channel>,
    epgData: Map<String, List<Program>>,
    favoriteChannelIds: Set<UUID>,
    now: Instant,
    highlightedChannelId: UUID?,
    isSectionFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val highlightedIdx = channels.indexOfFirst { it.id == highlightedChannelId }
    LaunchedEffect(highlightedIdx, isSectionFocused) {
        if (isSectionFocused && highlightedIdx >= 0) {
            listState.animateScrollToItem((highlightedIdx - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 48.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(channels) { _, channel ->
            val isHighlighted = isSectionFocused && channel.id == highlightedChannelId
            val isFavorite = channel.id in favoriteChannelIds
            val programs = epgData[channel.id.toString()].orEmpty()
            val current = programs.firstOrNull { it.startTime <= now && it.endTime > now }
                ?: channel.currentProgram
            val upcoming = programs.filter { it.startTime > now }.sortedBy { it.startTime }.take(2)

            ChannelEpgRow(
                channel = channel,
                currentProgram = current,
                upcomingPrograms = upcoming,
                isFavorite = isFavorite,
                isHighlighted = isHighlighted,
                now = now
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun ChannelEpgRow(
    channel: Channel,
    currentProgram: Program?,
    upcomingPrograms: List<Program>,
    isFavorite: Boolean,
    isHighlighted: Boolean,
    now: Instant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHighlighted) AppColors.Purple.copy(alpha = 0.18f)
                else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (isHighlighted) Modifier.border(1.dp, AppColors.Purple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel number
        Text(
            "${channel.number}",
            color = AppColors.Purple,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )

        // Channel logo
        AsyncImage(
            model = channel.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
        )

        Spacer(Modifier.width(12.dp))

        // Channel name
        Column(modifier = Modifier.width(140.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    channel.name,
                    color = if (isHighlighted) Color.White else AppColors.OnSurface.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isFavorite) {
                    Text("★", color = AppColors.Purple, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // Current program with progress
        if (currentProgram != null) {
            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    currentProgram.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${timeFmt.format(currentProgram.startTime)}–${timeFmt.format(currentProgram.endTime)}",
                        color = AppColors.OnSurface.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                    LinearProgressIndicator(
                        progress = { currentProgram.progressFraction(now) },
                        modifier = Modifier.width(60.dp).height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = AppColors.Purple,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1.5f))
        }

        Spacer(Modifier.width(12.dp))

        // Upcoming programs
        Row(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            upcomingPrograms.forEach { prog ->
                Column(modifier = Modifier.width(150.dp)) {
                    Text(
                        timeFmt.format(prog.startTime),
                        color = AppColors.Purple.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    Text(
                        prog.title,
                        color = AppColors.OnSurface.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
