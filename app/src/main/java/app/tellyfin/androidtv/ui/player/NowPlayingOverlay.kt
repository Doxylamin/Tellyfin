package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun NowPlayingOverlay(
    channel: Channel,
    programs: List<Program>,
    modifier: Modifier = Modifier
) {
    val now = Instant.now()
    val current = programs.firstOrNull { it.startTime <= now && it.endTime > now }
    val upcoming = programs.filter { it.startTime > now }.sortedBy { it.startTime }.take(3)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.15f to Color.Black.copy(alpha = 0.7f),
                    1f to Color.Black.copy(alpha = 0.97f)
                )
            )
            .padding(top = 40.dp, bottom = 28.dp, start = 48.dp, end = 48.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Channel header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFCC0000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(stringResource(R.string.live_badge), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    channel.name,
                    color = AppColors.Purple,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "·",
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 14.sp
                )
                Text(
                    stringResource(R.string.now_playing_dismiss_hint),
                    color = Color.White.copy(alpha = 0.30f),
                    fontSize = 12.sp
                )
            }

            if (current != null) {
                CurrentProgramBlock(program = current, now = now)
            } else {
                Text(
                    stringResource(R.string.no_program_info),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp
                )
            }

            if (upcoming.isNotEmpty()) {
                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        stringResource(R.string.up_next),
                        color = Color.White.copy(alpha = 0.40f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(52.dp).padding(top = 14.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        upcoming.forEach { prog ->
                            UpcomingCard(prog)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentProgramBlock(program: Program, now: Instant) {
    val duration = (program.endTime.epochSecond - program.startTime.epochSecond).coerceAtLeast(1)
    val elapsed = (now.epochSecond - program.startTime.epochSecond).coerceIn(0, duration)
    val remaining = (duration - elapsed) / 60

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            program.title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${timeFmt.format(program.startTime)} – ${timeFmt.format(program.endTime)}",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 13.sp
            )
            if (program.genre != null) {
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(program.genre, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                }
            }
        }

        if (program.description != null) {
            Text(
                program.description,
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LinearProgressIndicator(
                progress = { elapsed.toFloat() / duration.toFloat() },
                modifier = Modifier
                    .width(240.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AppColors.Purple,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
            Text(
                stringResource(R.string.minutes_remaining, remaining),
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun UpcomingCard(program: Program) {
    Column(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .width(180.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            timeFmt.format(program.startTime),
            color = AppColors.Purple,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            program.title,
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(R.string.duration_minutes, program.durationMinutes),
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 11.sp
        )
    }
}
