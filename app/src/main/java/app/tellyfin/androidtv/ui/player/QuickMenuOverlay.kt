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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private data class QuickMenuItem(val icon: String, val label: String, val description: String)

private val MENU_ITEMS_BASE = listOf(
    QuickMenuItem("★", "Favorite", "Add or remove from favorites"),
    QuickMenuItem("↺", "Refresh", "Reload stream"),
    QuickMenuItem("⚙", "Settings", "Bandwidth & preferences")
)

@Composable
fun QuickMenuOverlay(
    visible: Boolean,
    highlightedIndex: Int,
    isFavorite: Boolean,
    channel: Channel? = null,
    currentProgram: Program? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInHorizontally(tween(200)) { it },
        exit = fadeOut(tween(120)) + slideOutHorizontally(tween(160)) { it },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.80f),
                        1f to Color.Black.copy(alpha = 0.98f)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 0.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Channel info header
                    if (channel != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
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
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Channel ${channel.number}",
                                        color = AppColors.Purple,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (currentProgram != null) {
                                val now = Instant.now()
                                val remaining = ((currentProgram.endTime.epochSecond - now.epochSecond) / 60).coerceAtLeast(0)
                                Text(
                                    currentProgram.title,
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${remaining}m remaining",
                                    color = Color.White.copy(alpha = 0.30f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.07f))
                    )

                    Spacer(Modifier.height(12.dp))

                    // Menu items
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MENU_ITEMS_BASE.forEachIndexed { index, item ->
                            val isFocused = index == highlightedIndex
                            val label = if (index == 0) {
                                if (isFavorite) "★  Remove from favorites" else "☆  Add to favorites"
                            } else {
                                "${item.icon}  ${item.label}"
                            }

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
                                    .padding(horizontal = 16.dp, vertical = 13.dp)
                            ) {
                                // Left accent line for focused item
                                if (isFocused) {
                                    Box(
                                        modifier = Modifier
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

                // Bottom hint
                Text(
                    "↑↓ Navigate  ·  OK Select  ·  ← Close",
                    color = Color.White.copy(alpha = 0.20f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }
    }
}
