package app.tellyfin.androidtv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val CHANNEL_LABEL_WIDTH = 148.dp
private const val MINUTES_PER_PIXEL = 0.5f  // 2px per minute
private val ROW_HEIGHT = 52.dp
private val HEADER_HEIGHT = 28.dp
private const val VISIBLE_HOURS = 4L

@Composable
fun EpgOverlay(
    channels: List<Channel>,
    epgData: Map<String, List<Program>>,
    currentChannelIndex: Int,
    highlightedRow: Int,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val now = remember { Instant.now() }
    val zoneId = remember { ZoneId.systemDefault() }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId) }

    val nowOffsetDp = ((now.epochSecond / 60f) * (1f / MINUTES_PER_PIXEL))
    val gridScrollOffset = (nowOffsetDp - 60f).coerceAtLeast(0f)

    val hScroll = rememberScrollState(initial = gridScrollOffset.roundToInt())
    LaunchedEffect(visible) {
        if (visible) hScroll.scrollTo(gridScrollOffset.roundToInt())
    }

    val channelListState = rememberLazyListState()
    LaunchedEffect(highlightedRow, visible) {
        if (visible && channels.isNotEmpty()) {
            channelListState.animateScrollToItem((highlightedRow - 3).coerceAtLeast(0))
        }
    }

    val startEpoch = now.epochSecond - (now.epochSecond % 1800)
    val ticks = (0..VISIBLE_HOURS * 2).map { i ->
        val tickEpoch = startEpoch + i * 1800
        val xDp = (tickEpoch / 60f) * (1f / MINUTES_PER_PIXEL)
        val label = timeFmt.format(Instant.ofEpochSecond(tickEpoch))
        Pair(xDp, label)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black,
                                1f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Guide",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "·",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        timeFmt.format(now),
                        fontSize = 13.sp,
                        color = AppColors.Purple,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "↑↓ Navigate  ·  OK Watch",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                }

                // Thin purple divider under header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.Purple.copy(alpha = 0.30f))
                )

                // Grid
                Row(modifier = Modifier.weight(1f)) {
                    // Fixed channel label column
                    LazyColumn(
                        state = channelListState,
                        modifier = Modifier.width(CHANNEL_LABEL_WIDTH)
                    ) {
                        item { Spacer(modifier = Modifier.height(HEADER_HEIGHT)) }
                        itemsIndexed(channels) { index, channel ->
                            Box(
                                modifier = Modifier
                                    .width(CHANNEL_LABEL_WIDTH)
                                    .height(ROW_HEIGHT)
                                    .background(
                                        when (index) {
                                            highlightedRow -> AppColors.Purple.copy(alpha = 0.25f)
                                            currentChannelIndex -> Color.White.copy(alpha = 0.05f)
                                            else -> if (index % 2 == 0) Color.White.copy(alpha = 0.02f) else Color.Transparent
                                        }
                                    ),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // Left accent for highlighted row
                                if (index == highlightedRow) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .background(AppColors.Purple)
                                    )
                                }
                                Column(modifier = Modifier.padding(start = 10.dp, end = 8.dp)) {
                                    Text(
                                        text = channel.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (index == highlightedRow) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (index == highlightedRow) Color.White else Color.White.copy(alpha = 0.70f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${channel.number}",
                                        fontSize = 10.sp,
                                        color = AppColors.Purple.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Scrollable program grid
                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(hScroll)
                        ) {
                            // Time ruler
                            Box(
                                modifier = Modifier
                                    .height(HEADER_HEIGHT)
                                    .width(IntrinsicSize.Max)
                                    .background(Color.White.copy(alpha = 0.03f))
                            ) {
                                ticks.forEach { (xDp, label) ->
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .offset(x = xDp.dp)
                                            .padding(top = 6.dp, start = 4.dp)
                                    )
                                }
                                // "Now" tick label
                                Text(
                                    text = "▼",
                                    fontSize = 8.sp,
                                    color = AppColors.Purple,
                                    modifier = Modifier.offset(x = nowOffsetDp.dp - 4.dp, y = 2.dp)
                                )
                            }

                            // Program rows
                            channels.forEachIndexed { index, channel ->
                                val programs = epgData[channel.id.toString()].orEmpty()
                                EpgRow(
                                    programs = programs,
                                    isHighlighted = index == highlightedRow,
                                    isCurrentChannel = index == currentChannelIndex,
                                    isAlternate = index % 2 == 0,
                                    now = now,
                                    nowOffsetDp = nowOffsetDp,
                                    minutesPerPixel = MINUTES_PER_PIXEL,
                                    rowHeight = ROW_HEIGHT
                                )
                            }
                        }

                        // "Now" vertical line overlay on the grid
                        Box(
                            modifier = Modifier
                                .offset(x = (nowOffsetDp - hScroll.value).dp)
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(AppColors.Purple.copy(alpha = 0.60f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgRow(
    programs: List<Program>,
    isHighlighted: Boolean,
    isCurrentChannel: Boolean,
    isAlternate: Boolean,
    now: Instant,
    nowOffsetDp: Float,
    minutesPerPixel: Float,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    val rowBg = when {
        isHighlighted -> AppColors.Purple.copy(alpha = 0.12f)
        isCurrentChannel -> Color.White.copy(alpha = 0.04f)
        isAlternate -> Color.White.copy(alpha = 0.015f)
        else -> Color.Transparent
    }
    Row(modifier = Modifier.height(rowHeight).background(rowBg)) {
        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .width(600.dp)
                    .height(rowHeight)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "No data",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        } else {
            programs.forEach { program ->
                val widthDp = (program.durationMinutes / minutesPerPixel).dp
                val isLive = now.isAfter(program.startTime) && now.isBefore(program.endTime)
                Box(
                    modifier = Modifier
                        .width(widthDp)
                        .height(rowHeight)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isLive -> Brush.horizontalGradient(
                                    listOf(AppColors.Purple.copy(alpha = 0.6f), AppColors.Purple.copy(alpha = 0.35f))
                                )
                                else -> Brush.horizontalGradient(
                                    listOf(Color(0xFF1E1E32), Color(0xFF252538))
                                )
                            }
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column {
                        Text(
                            text = program.title,
                            fontSize = 11.sp,
                            color = if (isLive) Color.White else Color.White.copy(alpha = 0.75f),
                            fontWeight = if (isLive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isLive) {
                            Text(
                                text = "LIVE",
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.60f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
