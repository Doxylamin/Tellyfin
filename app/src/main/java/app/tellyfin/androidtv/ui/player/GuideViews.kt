package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.ui.theme.AppColors
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

// The programme guide shared by the home screen's Live tab and the in-playback overlay.
// Red only ever means "now" (now-line, now pill, LIVE badge); airing programmes are a lighter
// surface with a thin progress stripe instead of a red fill.

val GUIDE_CHANNEL_COL_WIDTH = 150.dp
val GUIDE_ROW_HEIGHT = 46.dp
private val GUIDE_RULER_HEIGHT = 24.dp

private val guideTimeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private val BlockUpcoming = Color(0xFF1B1B2B)
private val BlockLive = Color(0xFF26263C)
private val BlockEnded = Color(0xFF141420)
private val FocusOutline = Color(0xFF9D7BFF)

// ── Hero ──────────────────────────────────────────────────────────────────────

/** Compact details for the focused programme. [trailing] replaces the description on the right. */
@Composable
fun GuideHero(
    channel: Channel?,
    program: Program?,
    now: Instant,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.padding(horizontal = 48.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoTile(channel?.logoUrl, Modifier.size(56.dp), cornerRadius = 12.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                program?.title ?: channel?.name ?: "",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (program != null) {
                val timing = programTiming(program, now)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (timing is ProgramTiming.Live) GuideBadge(stringResource(R.string.live_badge), AppColors.Red)
                    channel?.let { GuideBadge(it.name) }
                    program.genre?.takeIf { it.isNotBlank() }?.let { GuideBadge(it) }
                    Text(
                        "${guideTimeFmt.format(program.startTime)} – ${guideTimeFmt.format(program.endTime)} · ${timingLabel(timing)}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }
                if (timing is ProgramTiming.Live) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(Modifier.fillMaxWidth(timing.progress).fillMaxHeight().background(AppColors.Red))
                    }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        if (trailing != null) {
            trailing()
        } else {
            program?.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(320.dp)
                )
            }
        }
    }
}

@Composable
private fun timingLabel(timing: ProgramTiming): String = when (timing) {
    is ProgramTiming.Live -> stringResource(R.string.guide_remaining, timing.remainingMin)
    is ProgramTiming.Upcoming -> stringResource(R.string.guide_starts_in, timing.inMin)
    ProgramTiming.Ended -> stringResource(R.string.guide_ended)
}

@Composable
private fun GuideBadge(label: String, color: Color = Color.White.copy(alpha = 0.45f)) {
    Text(
        label,
        fontSize = 10.sp,
        color = color,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun LogoTile(url: String?, modifier: Modifier, cornerRadius: Dp = 6.dp) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(AppColors.Surface)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(4.dp)
        )
    }
}

// ── Grid ──────────────────────────────────────────────────────────────────────

/**
 * Channel rows against a shared, horizontally scrolling timeline. [focusedBlockIndex] null means
 * row focus only (the overlay); on the home screen it's the D-pad-selected programme.
 */
@Composable
fun GuideGrid(
    channels: List<Channel>,
    epgData: Map<String, List<Program>>,
    now: Instant,
    highlightedChannelId: UUID?,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    focusedBlockIndex: Int? = null,
    currentChannelId: UUID? = null
) {
    val density = LocalDensity.current
    val windowStart = remember(now) { guideWindowStart(now) }
    val ticks = remember(windowStart) { guideTicks(windowStart) }
    val totalWidth = (GUIDE_WINDOW_MINUTES * GUIDE_PX_PER_MIN).dp
    val nowX = (guideMinutesSince(windowStart, now) * GUIDE_PX_PER_MIN).dp
    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()

    val highlightedIndex = channels.indexOfFirst { it.id == highlightedChannelId }
    LaunchedEffect(highlightedIndex, isFocused) {
        if (isFocused && highlightedIndex >= 0) {
            listState.animateScrollToItem((highlightedIndex - 2).coerceAtLeast(0))
        }
    }
    // Keep the focused programme in view. The scroll state is in pixels, so convert from dp.
    LaunchedEffect(highlightedChannelId, focusedBlockIndex, isFocused, windowStart) {
        if (!isFocused || focusedBlockIndex == null) return@LaunchedEffect
        val block = guideBlocks(epgData[highlightedChannelId?.toString()].orEmpty(), windowStart)
            .firstOrNull { it.index == focusedBlockIndex } ?: return@LaunchedEffect
        val targetPx = with(density) { ((block.startMin * GUIDE_PX_PER_MIN).dp - 80.dp).toPx() }
        hScroll.animateScrollTo(targetPx.roundToInt().coerceAtLeast(0))
    }

    Column(modifier = modifier) {
        // Ruler: outside the channel list, so it never scrolls away vertically.
        Row(Modifier.fillMaxWidth().height(GUIDE_RULER_HEIGHT)) {
            Spacer(Modifier.width(GUIDE_CHANNEL_COL_WIDTH))
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                Box(Modifier.width(totalWidth).fillMaxHeight()) {
                    ticks.forEach { tick ->
                        Text(
                            guideTimeFmt.format(tick.time),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier
                                .offset(x = (tick.offsetMin * GUIDE_PX_PER_MIN).dp)
                                .padding(start = 4.dp, top = 5.dp)
                        )
                    }
                    Text(
                        guideTimeFmt.format(now),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .offset(x = nowX - 18.dp, y = 3.dp)
                            .background(AppColors.Red, RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    val highlighted = isFocused && channel.id == highlightedChannelId
                    Row(Modifier.fillMaxWidth().height(GUIDE_ROW_HEIGHT)) {
                        GuideChannelCell(
                            channel = channel,
                            highlighted = highlighted,
                            isCurrent = channel.id == currentChannelId,
                            isAlternate = index % 2 == 0
                        )
                        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                            GuideRow(
                                programs = epgData[channel.id.toString()].orEmpty(),
                                windowStart = windowStart,
                                now = now,
                                highlighted = highlighted,
                                focusedBlockIndex = if (highlighted) focusedBlockIndex else null
                            )
                        }
                    }
                }
            }

            // Past shade and now-line, pinned to the scrolled timeline.
            Box(Modifier.padding(start = GUIDE_CHANNEL_COL_WIDTH).fillMaxSize().clipToBounds()) {
                Box(
                    Modifier
                        .offset { IntOffset(-hScroll.value, 0) }
                        .width(nowX)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(Color(0x8C0D0D0D), Color(0x330D0D0D))))
                )
                Box(
                    Modifier
                        .offset { IntOffset(nowX.roundToPx() - hScroll.value, 0) }
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(AppColors.Red)
                )
            }
        }
    }
}

@Composable
private fun GuideChannelCell(channel: Channel, highlighted: Boolean, isCurrent: Boolean, isAlternate: Boolean) {
    Row(
        modifier = Modifier
            .width(GUIDE_CHANNEL_COL_WIDTH)
            .fillMaxHeight()
            .background(
                when {
                    highlighted -> AppColors.Purple.copy(alpha = 0.12f)
                    isCurrent -> Color.White.copy(alpha = 0.05f)
                    isAlternate -> Color.White.copy(alpha = 0.015f)
                    else -> Color.Transparent
                }
            )
            .drawBehind { if (highlighted) drawRect(AppColors.Purple, size = Size(3.dp.toPx(), size.height)) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${channel.number}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            color = if (highlighted) FocusOutline else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.width(28.dp)
        )
        Spacer(Modifier.width(8.dp))
        LogoTile(channel.logoUrl, Modifier.size(width = 40.dp, height = 28.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            channel.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlighted) Color.White else Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun GuideRow(
    programs: List<Program>,
    windowStart: Instant,
    now: Instant,
    highlighted: Boolean,
    focusedBlockIndex: Int?
) {
    val blocks = remember(programs, windowStart) { guideBlocks(programs, windowStart) }
    Box(
        Modifier
            .width((GUIDE_WINDOW_MINUTES * GUIDE_PX_PER_MIN).dp)
            .fillMaxHeight()
            .background(if (highlighted) Color.White.copy(alpha = 0.04f) else Color.Transparent)
    ) {
        if (blocks.isEmpty()) {
            Text(
                stringResource(R.string.no_data),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
            )
        }
        blocks.forEach { block ->
            GuideBlockView(block, programTiming(block.program, now), focused = block.index == focusedBlockIndex)
        }
    }
}

@Composable
private fun GuideBlockView(block: GuideBlock, timing: ProgramTiming, focused: Boolean) {
    val width = ((block.endMin - block.startMin) * GUIDE_PX_PER_MIN).dp
    val shape = RoundedCornerShape(5.dp)
    val live = timing is ProgramTiming.Live
    val ended = timing == ProgramTiming.Ended
    Box(
        modifier = Modifier
            .offset(x = (block.startMin * GUIDE_PX_PER_MIN).dp)
            .width(width.coerceAtLeast(2.dp))
            .fillMaxHeight()
            .padding(2.dp)
            .clip(shape)
            .then(
                if (focused) Modifier
                    .background(Brush.horizontalGradient(listOf(AppColors.Purple.copy(alpha = 0.62f), AppColors.Purple.copy(alpha = 0.42f))))
                    .border(1.5.dp, FocusOutline, shape)
                else Modifier.background(if (live) BlockLive else if (ended) BlockEnded else BlockUpcoming)
            )
    ) {
        if (width >= 24.dp) {
            Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp)) {
                Text(
                    block.program.title,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (focused || live) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        focused || live -> Color.White
                        ended -> Color.White.copy(alpha = 0.35f)
                        else -> Color.White.copy(alpha = 0.75f)
                    }
                )
                if (width >= 110.dp) {
                    val start = guideTimeFmt.format(block.program.startTime)
                    Text(
                        if (live || focused) "$start – ${guideTimeFmt.format(block.program.endTime)}" else start,
                        fontSize = 9.sp,
                        maxLines = 1,
                        color = Color.White.copy(alpha = if (focused) 0.75f else 0.38f)
                    )
                }
            }
        }
        if (timing is ProgramTiming.Live) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(timing.progress)
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.55f))
            )
        }
    }
}

// ── Hints ─────────────────────────────────────────────────────────────────────

/** One line of key hints at the bottom of a guide — never drawn over the grid. */
@Composable
fun GuideHintBar(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(AppColors.Background)
            .drawBehind { drawLine(Color.White.copy(alpha = 0.06f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
    }
}
