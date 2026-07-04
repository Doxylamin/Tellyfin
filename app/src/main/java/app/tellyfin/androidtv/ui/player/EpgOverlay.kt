package app.tellyfin.androidtv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val CHANNEL_LABEL_WIDTH = 148.dp
private const val PX_PER_MIN = 4.0f  // 4 dp per minute → 1 h = 240 dp
private const val WINDOW_MINUTES = 300  // 5 hours
private val ROW_HEIGHT = 52.dp
private val HEADER_HEIGHT = 28.dp

@Composable
fun EpgOverlay(
    channels: List<Channel>,
    epgData: Map<String, List<Program>>,
    currentChannelIndex: Int,
    highlightedRow: Int,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    // Refresh "now" each time the overlay opens and tick while it stays visible
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(visible) {
        while (visible) {
            now = Instant.now()
            kotlinx.coroutines.delay(30_000L)
        }
    }
    val zoneId = remember { ZoneId.systemDefault() }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId) }

    val windowStart = remember(now) {
        Instant.ofEpochSecond((now.epochSecond / 1800L) * 1800L - 1800L)
    }
    val windowEndSec = windowStart.epochSecond + WINDOW_MINUTES * 60L
    val totalWidthDp = (WINDOW_MINUTES * PX_PER_MIN).dp

    val nowOffsetDp = ((now.epochSecond - windowStart.epochSecond) / 60f) * PX_PER_MIN
    val initialScroll = ((nowOffsetDp - 100f).coerceAtLeast(0f)).roundToInt()

    val hScroll = rememberScrollState(initial = initialScroll)
    LaunchedEffect(visible) {
        if (visible) hScroll.scrollTo(initialScroll)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(highlightedRow, visible) {
        if (visible && channels.isNotEmpty()) {
            // +1 because ruler is item 0; scroll so highlighted row has ~2 rows of context above
            listState.animateScrollToItem((highlightedRow - 2).coerceAtLeast(0) + 1)
        }
    }

    val ticks = remember(windowStart) {
        val count = WINDOW_MINUTES / 30 + 1
        (0 until count).map { i ->
            val xDp = i * 30f * PX_PER_MIN
            val label = timeFmt.format(Instant.ofEpochSecond(windowStart.epochSecond + i * 1800L))
            Pair(xDp, label)
        }
    }

    val density = LocalDensity.current
    val nowLineX by remember(density, nowOffsetDp) {
        derivedStateOf { with(density) { nowOffsetDp.dp - hScroll.value.toDp() } }
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
                        stringResource(R.string.guide),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("·", color = Color.White.copy(alpha = 0.25f), fontSize = 16.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        timeFmt.format(now),
                        fontSize = 13.sp,
                        color = AppColors.Purple,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.epg_hint),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.Purple.copy(alpha = 0.30f))
                )

                // Grid — single LazyColumn keeps channel labels and program rows vertically synced
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        // Ruler header
                        item {
                            Row(modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT)) {
                                Spacer(Modifier.width(CHANNEL_LABEL_WIDTH))
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
                                            .background(Color.White.copy(alpha = 0.03f))
                                    ) {
                                        ticks.forEach { (xDp, label) ->
                                            Text(
                                                label,
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.45f),
                                                modifier = Modifier
                                                    .offset(x = xDp.dp)
                                                    .padding(top = 6.dp, start = 4.dp)
                                            )
                                        }
                                        Text(
                                            "▼",
                                            fontSize = 8.sp,
                                            color = AppColors.Purple,
                                            modifier = Modifier.offset(x = (nowOffsetDp - 4f).dp, y = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Channel rows
                        itemsIndexed(channels) { index, channel ->
                            val programs = epgData[channel.id.toString()].orEmpty()
                            Row(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                                EpgChannelLabel(
                                    channel = channel,
                                    index = index,
                                    highlightedRow = highlightedRow,
                                    currentChannelIndex = currentChannelIndex
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .horizontalScroll(hScroll)
                                ) {
                                    EpgRow(
                                        programs = programs,
                                        isHighlighted = index == highlightedRow,
                                        isCurrentChannel = index == currentChannelIndex,
                                        isAlternate = index % 2 == 0,
                                        now = now,
                                        windowStartSec = windowStart.epochSecond,
                                        windowEndSec = windowEndSec,
                                        totalWidthDp = totalWidthDp,
                                        pxPerMin = PX_PER_MIN,
                                        rowHeight = ROW_HEIGHT
                                    )
                                }
                            }
                        }
                    }

                    // "Now" vertical line — fixed overlay, px→dp corrected
                    Box(
                        modifier = Modifier
                            .padding(start = CHANNEL_LABEL_WIDTH)
                            .offset(x = nowLineX)
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(AppColors.Purple.copy(alpha = 0.60f))
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgChannelLabel(
    channel: Channel,
    index: Int,
    highlightedRow: Int,
    currentChannelIndex: Int
) {
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

@Composable
private fun EpgRow(
    programs: List<Program>,
    isHighlighted: Boolean,
    isCurrentChannel: Boolean,
    isAlternate: Boolean,
    now: Instant,
    windowStartSec: Long,
    windowEndSec: Long,
    totalWidthDp: Dp,
    pxPerMin: Float,
    rowHeight: Dp
) {
    val rowBg = when {
        isHighlighted -> AppColors.Purple.copy(alpha = 0.12f)
        isCurrentChannel -> Color.White.copy(alpha = 0.04f)
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
                    .background(Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    stringResource(R.string.no_data),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        } else {
            programs.forEach { program ->
                val startSec = maxOf(program.startTime.epochSecond, windowStartSec)
                val endSec = minOf(program.endTime.epochSecond, windowEndSec)
                if (endSec <= windowStartSec || startSec >= windowEndSec) return@forEach

                val xDp = ((startSec - windowStartSec) / 60f * pxPerMin).dp
                val widthDp = ((endSec - startSec) / 60f * pxPerMin).dp.coerceAtLeast(2.dp)
                val isLive = now.isAfter(program.startTime) && now.isBefore(program.endTime)

                Box(
                    modifier = Modifier
                        .offset(x = xDp)
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
                                text = stringResource(R.string.live_badge),
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
