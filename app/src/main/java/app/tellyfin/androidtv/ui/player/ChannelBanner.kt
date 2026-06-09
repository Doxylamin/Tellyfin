package app.tellyfin.androidtv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun ChannelBanner(
    channel: Channel,
    visible: Boolean,
    isPreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 2 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Gradient fades into video above; solid near the bottom edge
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.25f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.93f)
                    )
                )
                .padding(top = 36.dp, bottom = 24.dp, start = 48.dp, end = 48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Channel logo
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
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

                Spacer(Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Number + name
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${channel.number}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Purple
                        )
                        Text(
                            text = channel.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    channel.currentProgram?.let { prog ->
                        val now = Instant.now()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = prog.title,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.80f),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${timeFormat.format(prog.startTime)} – ${timeFormat.format(prog.endTime)}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.40f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { prog.progressFraction(now) },
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = AppColors.Purple,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                            val remaining = ((prog.endTime.epochSecond - now.epochSecond) / 60).coerceAtLeast(0)
                            Text(
                                text = stringResource(R.string.minutes_left, remaining),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.40f)
                            )
                        }
                    }

                    if (isPreview) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.banner_preview_hint),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }

                // Countdown arc — only shown in preview mode
                if (isPreview) {
                    Spacer(Modifier.width(28.dp))
                    CountdownArc(
                        durationMs = 3000,
                        channelKey = channel.id,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownArc(
    durationMs: Int,
    channelKey: Any,
    modifier: Modifier = Modifier
) {
    val sweep = remember(channelKey) { Animatable(1f) }
    LaunchedEffect(channelKey) {
        sweep.snapTo(1f)
        sweep.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
        )
    }

    val secondsLeft = ceil(sweep.value * (durationMs / 1000f)).toInt().coerceIn(0, durationMs / 1000)
    val purple = AppColors.Purple
    val trackColor = Color.White.copy(alpha = 0.15f)
    val bgColor = Color.Black.copy(alpha = 0.5f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Background fill
            drawCircle(color = bgColor)

            // Track ring
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Countdown arc
            if (sweep.value > 0f) {
                drawArc(
                    color = purple,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text = "$secondsLeft",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
