package app.tellyfin.androidtv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.tellyfin.androidtv.data.model.Channel
import androidx.compose.ui.res.stringResource
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.ui.theme.AppColors
import java.time.Instant
import java.util.UUID

@Composable
fun ChannelListOverlay(
    channels: List<Channel>,
    currentIndex: Int,
    highlightedIndex: Int,
    favoriteChannelIds: Set<UUID>,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val now = remember { Instant.now() }
    val listState = rememberLazyListState()
    LaunchedEffect(highlightedIndex, visible) {
        if (visible && channels.isNotEmpty()) {
            listState.animateScrollToItem((highlightedIndex - 3).coerceAtLeast(0))
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { -it },
        exit = fadeOut() + slideOutHorizontally { -it },
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dim the video behind the panel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )

            // Panel
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.98f),
                            1f to Color.Black.copy(alpha = 0.80f)
                        )
                    )
            ) {
                // Header
                Column(
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.channels),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        stringResource(R.string.channel_list_hint, channels.size),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.30f)
                    )
                }

                // Top fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.5f),
                                1f to Color.Transparent
                            )
                        )
                )

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(channels) { index, channel ->
                            ChannelListRow(
                                channel = channel,
                                isHighlighted = index == highlightedIndex,
                                isCurrent = index == currentIndex,
                                isFavorite = channel.id in favoriteChannelIds,
                                now = now
                            )
                        }
                    }

                    // Bottom fade
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.9f)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelListRow(
    channel: Channel,
    isHighlighted: Boolean,
    isCurrent: Boolean,
    isFavorite: Boolean,
    now: Instant
) {
    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "rowScale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(
                when {
                    isHighlighted -> AppColors.Purple.copy(alpha = 0.18f)
                    isCurrent -> Color.White.copy(alpha = 0.04f)
                    else -> Color.Transparent
                }
            )
    ) {
        // Left accent bar for focused item
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(52.dp)
                    .align(Alignment.CenterStart)
                    .background(AppColors.Purple, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
            )
        }

        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel number
            Text(
                text = "${channel.number}",
                fontSize = 13.sp,
                color = if (isHighlighted) AppColors.Purple else Color.White.copy(alpha = 0.35f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(34.dp)
            )

            // Logo
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        fontSize = 14.sp,
                        color = if (isHighlighted) Color.White else Color.White.copy(alpha = 0.75f),
                        fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Text("★", color = AppColors.Purple, fontSize = 10.sp)
                    }
                }
                channel.currentProgram?.let { prog ->
                    Text(
                        text = prog.title,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = if (isHighlighted) 0.55f else 0.35f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    LinearProgressIndicator(
                        progress = { prog.progressFraction(now) },
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = AppColors.Purple.copy(alpha = 0.6f),
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }
            }

            // "NOW" indicator for current channel
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .background(AppColors.Purple.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.now_badge), color = AppColors.Purple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
