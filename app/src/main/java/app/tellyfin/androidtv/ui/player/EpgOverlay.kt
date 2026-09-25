package app.tellyfin.androidtv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val overlayTimeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * The guide opened while watching — the same shared guide as the home screen's Live tab. The
 * channel being watched keeps playing in a slot at the top right: the overlay reports that slot's
 * bounds (root coordinates — the overlay fills the screen from the root's origin) so PlayerScreen
 * can size the video into it, and paints its background around the slot instead of over it.
 */
@Composable
fun EpgOverlay(
    channels: List<Channel>,
    epgData: Map<String, List<Program>>,
    currentChannelIndex: Int,
    highlightedRow: Int,
    focusedBlockIndex: Int,
    visible: Boolean,
    showVideoSlot: Boolean,
    onVideoSlotChanged: (Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(visible) {
        while (visible) {
            now = Instant.now()
            delay(30_000L)
        }
    }

    val highlightedChannel = channels.getOrNull(highlightedRow)
    val heroProgram = highlightedChannel?.let { guideHeroProgram(epgData[it.id.toString()].orEmpty(), focusedBlockIndex, now) }
    var slot by remember { mutableStateOf<Rect?>(null) }

    // Fades rather than slides: the slot must not move while the video is sized into it.
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        DisposableEffect(Unit) {
            onDispose {
                slot = null
                onVideoSlotChanged(null)
            }
        }
        // Only cut the video's hole while it's actually in there: on close the video returns to
        // full screen at once, while this still fades out on top of it.
        val holeActive = visible && showVideoSlot
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val s = if (holeActive) slot else null
                    val bg = AppColors.Background
                    if (s == null) {
                        drawRect(bg)
                    } else {
                        drawRect(bg, Offset.Zero, Size(size.width, s.top))
                        drawRect(bg, Offset(0f, s.bottom), Size(size.width, size.height - s.bottom))
                        drawRect(bg, Offset(0f, s.top), Size(s.left, s.height))
                        drawRect(bg, Offset(s.right, s.top), Size(size.width - s.right, s.height))
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.epg_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(overlayTimeFmt.format(now), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Purple)
            }
            GuideHero(
                channel = highlightedChannel,
                program = heroProgram,
                now = now,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                trailing = if (showVideoSlot) {
                    {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(16f / 9f)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInRoot()
                                    if (bounds != slot) {
                                        slot = bounds
                                        onVideoSlotChanged(bounds)
                                    }
                                }
                        )
                    }
                } else null
            )
            GuideGrid(
                channels = channels,
                epgData = epgData,
                now = now,
                highlightedChannelId = highlightedChannel?.id,
                isFocused = true,
                modifier = Modifier.weight(1f),
                focusedBlockIndex = focusedBlockIndex,
                currentChannelId = channels.getOrNull(currentChannelIndex)?.id
            )
            GuideHintBar(stringResource(R.string.epg_hint))
        }
    }
}
