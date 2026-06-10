package app.tellyfin.androidtv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private val ctxTimeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

// ── Channel Context Drawer ────────────────────────────────────────────────────
// Slides in from the right when the user presses MENU on an EPG row.

@Composable
fun ChannelContextDrawer(
    visible: Boolean,
    channel: Channel?,
    isFavorite: Boolean,
    currentProgram: Program?,
    highlightedIndex: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInHorizontally(tween(200)) { it },
        exit = fadeOut(tween(120)) + slideOutHorizontally(tween(160)) { it },
        modifier = modifier
    ) {
        // Dim background
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )

            // Drawer panel
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.65f)
                    .width(300.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to Color(0xFF12121E),
                            1f to Color(0xFF1A1A2E)
                        )
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Channel header
                        if (channel != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(6.dp))
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = channel.logoUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Column {
                                        Text(
                                            channel.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            stringResource(R.string.channel_number, channel.number),
                                            color = AppColors.Purple,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (currentProgram != null) {
                                    val now = remember { Instant.now() }
                                    val duration = (currentProgram.endTime.epochSecond - currentProgram.startTime.epochSecond)
                                        .coerceAtLeast(1)
                                    val elapsed = (now.epochSecond - currentProgram.startTime.epochSecond)
                                        .coerceIn(0, duration)
                                    Text(
                                        currentProgram.title,
                                        color = Color.White.copy(alpha = 0.70f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    LinearProgressIndicator(
                                        progress = { elapsed.toFloat() / duration.toFloat() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp)),
                                        color = AppColors.Red,
                                        trackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                }
                            }
                        }

                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

                        Spacer(Modifier.height(8.dp))

                        // Menu items
                        val menuLabels = listOf(
                            stringResource(R.string.context_play),
                            if (isFavorite) stringResource(R.string.menu_remove_favorite)
                            else stringResource(R.string.menu_add_favorite),
                            stringResource(R.string.context_details)
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            menuLabels.forEachIndexed { index, label ->
                                val isFocused = index == highlightedIndex
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isFocused) AppColors.Purple.copy(alpha = 0.20f)
                                            else Color.Transparent
                                        )
                                        .then(
                                            if (isFocused) Modifier.border(
                                                1.dp,
                                                AppColors.Purple.copy(alpha = 0.50f),
                                                RoundedCornerShape(8.dp)
                                            ) else Modifier
                                        )
                                        .padding(horizontal = 14.dp, vertical = 13.dp)
                                ) {
                                    if (isFocused) {
                                        Box(
                                            Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .background(AppColors.Purple, RoundedCornerShape(2.dp))
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(
                                        label,
                                        color = if (isFocused) Color.White else Color.White.copy(alpha = 0.60f),
                                        fontSize = 14.sp,
                                        fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.context_back_hint),
                        color = Color.White.copy(alpha = 0.20f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

// ── Channel Details Overlay ───────────────────────────────────────────────────
// Full-screen programme list for a single channel. Opened via "Details" in
// the context drawer.

@Composable
fun ChannelDetailsOverlay(
    visible: Boolean,
    channel: Channel?,
    programs: List<Program>,
    focusedIndex: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
        modifier = modifier
    ) {
        val now = remember { Instant.now() }
        val listState = rememberLazyListState()

        LaunchedEffect(focusedIndex) {
            if (programs.isNotEmpty()) {
                listState.animateScrollToItem(
                    (focusedIndex - 2).coerceAtLeast(0).coerceAtMost(programs.size - 1)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 48.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (channel != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                channel.name,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.details_title),
                                color = AppColors.Purple,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.details_hint),
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 11.sp
                    )
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Purple.copy(alpha = 0.30f)))

                if (programs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.no_data),
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 48.dp, vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(programs) { index, program ->
                            val isLive = program.startTime <= now && program.endTime > now
                            val isFocused = index == focusedIndex

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isFocused -> AppColors.Purple.copy(alpha = 0.25f)
                                            isLive -> AppColors.Red.copy(alpha = 0.12f)
                                            index % 2 == 0 -> Color.White.copy(alpha = 0.02f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isFocused) Modifier.border(
                                            1.dp, AppColors.Purple.copy(alpha = 0.50f), RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Time column
                                Column(modifier = Modifier.width(80.dp)) {
                                    Text(
                                        ctxTimeFmt.format(program.startTime),
                                        color = if (isFocused) AppColors.Purple else Color.White.copy(alpha = 0.55f),
                                        fontSize = 13.sp,
                                        fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(
                                        ctxTimeFmt.format(program.endTime),
                                        color = Color.White.copy(alpha = 0.28f),
                                        fontSize = 11.sp
                                    )
                                }

                                // Title + description
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isLive) {
                                            Box(
                                                modifier = Modifier
                                                    .background(AppColors.Red, RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    stringResource(R.string.live_badge),
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            program.title,
                                            color = if (isFocused || isLive) Color.White else Color.White.copy(alpha = 0.82f),
                                            fontSize = 14.sp,
                                            fontWeight = if (isFocused || isLive) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    program.description?.let { desc ->
                                        if (desc.isNotBlank()) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                desc,
                                                color = Color.White.copy(alpha = 0.45f),
                                                fontSize = 12.sp,
                                                maxLines = if (isFocused) 3 else 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Duration badge
                                Text(
                                    "${program.durationMinutes}m",
                                    color = Color.White.copy(alpha = 0.30f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.Top)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
