package app.tellyfin.androidtv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.tellyfin.androidtv.data.model.Program
import app.tellyfin.androidtv.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val searchTimeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun SearchOverlay(
    query: String,
    kbRow: Int,
    kbCol: Int,
    inResults: Boolean,
    resultIndex: Int,
    results: List<SearchResult>,
    favoriteChannelIds: Set<UUID>,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.97f),
                        1f to AppColors.Background.copy(alpha = 0.99f)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 28.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        "Suchen",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "← Ergebnisse  → Tastatur  Back schließen",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.28f)
                    )
                }

                // Query display bar
                QueryBar(query = query, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))

                // Two-panel layout: results left, keyboard right
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SearchResultsPanel(
                            query = query,
                            results = results,
                            resultIndex = resultIndex,
                            inResults = inResults,
                            favoriteChannelIds = favoriteChannelIds
                        )
                    }
                    Column(modifier = Modifier.width(520.dp)) {
                        SearchKeyboard(kbRow = kbRow, kbCol = kbCol, isFocused = !inResults)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "↑↓←→ navigieren  OK tippen  ← zu Ergebnissen wechseln",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.22f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryBar(query: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .border(1.dp, AppColors.Purple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔍", fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        if (query.isBlank()) {
            Text(
                "Kanäle und Sendungen suchen…",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.28f)
            )
        } else {
            Text(query, fontSize = 15.sp, color = Color.White)
            Text("│", fontSize = 15.sp, color = AppColors.Purple)
        }
    }
}

@Composable
private fun SearchKeyboard(kbRow: Int, kbCol: Int, isFocused: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SEARCH_KB.forEachIndexed { row, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                keys.forEachIndexed { col, key ->
                    SearchKey(
                        label = key,
                        isFocused = isFocused && row == kbRow && col == kbCol
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchKey(label: String, isFocused: Boolean) {
    val isSpecial = label in listOf("⌫", "_", "✓")
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "keyScale"
    )
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 42.dp)
            .scale(scale)
            .background(
                when {
                    isFocused -> AppColors.Purple
                    isSpecial -> AppColors.Surface.copy(alpha = 0.65f)
                    else -> AppColors.Surface.copy(alpha = 0.4f)
                },
                RoundedCornerShape(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (label) {
                "_" -> "SPC"
                "✓" -> "OK"
                else -> label
            },
            color = if (isFocused) Color.White else Color.White.copy(alpha = if (isSpecial) 0.85f else 0.65f),
            fontSize = if (isSpecial) 11.sp else 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SearchResultsPanel(
    query: String,
    results: List<SearchResult>,
    resultIndex: Int,
    inResults: Boolean,
    favoriteChannelIds: Set<UUID>
) {
    if (query.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", fontSize = 36.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Kanäle und Sendungen suchen",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Buchstaben rechts tippen · Ergebnisse erscheinen hier",
                    color = Color.White.copy(alpha = 0.18f),
                    fontSize = 11.sp
                )
            }
        }
        return
    }

    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Keine Ergebnisse fur \"$query\"",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 14.sp
            )
        }
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(resultIndex, inResults) {
        if (inResults && results.isNotEmpty()) {
            listState.animateScrollToItem((resultIndex - 2).coerceAtLeast(0))
        }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                "${results.size} Ergebnis${if (results.size == 1) "" else "se"}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.35f)
            )
            if (inResults) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(AppColors.Purple.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text("aktiv", fontSize = 9.sp, color = AppColors.Purple)
                }
            }
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(results) { idx, result ->
                val isHighlighted = inResults && idx == resultIndex
                when (result) {
                    is SearchResult.ChannelMatch -> ChannelSearchRow(
                        channel = result.channel,
                        isFavorite = result.channel.id in favoriteChannelIds,
                        isHighlighted = isHighlighted
                    )
                    is SearchResult.ProgramMatch -> ProgramSearchRow(
                        program = result.program,
                        channel = result.channel,
                        isHighlighted = isHighlighted
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelSearchRow(
    channel: Channel,
    isFavorite: Boolean,
    isHighlighted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHighlighted) AppColors.Purple.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (isHighlighted) Modifier.border(1.dp, AppColors.Purple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF1A2A4A), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text("CH", fontSize = 9.sp, color = Color(0xFF82B1FF), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        AsyncImage(
            model = channel.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.07f))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${channel.number}",
            fontSize = 12.sp,
            color = AppColors.Purple,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )
        Text(
            channel.name,
            fontSize = 14.sp,
            color = if (isHighlighted) Color.White else Color.White.copy(alpha = 0.85f),
            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isFavorite) {
            Spacer(Modifier.width(6.dp))
            Text("★", color = AppColors.Purple, fontSize = 13.sp)
        }
        if (isHighlighted) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(AppColors.Purple, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("OK", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProgramSearchRow(
    program: Program,
    channel: Channel,
    isHighlighted: Boolean
) {
    val now = Instant.now()
    val isLive = program.startTime <= now && program.endTime > now

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHighlighted) AppColors.Purple.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (isHighlighted) Modifier.border(1.dp, AppColors.Purple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isLive) Color(0xFF3D1515) else Color(0xFF1C1C1C),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                if (isLive) "LIVE" else "EPG",
                fontSize = 9.sp,
                color = if (isLive) Color(0xFFEF5350) else Color.White.copy(alpha = 0.45f),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        AsyncImage(
            model = channel.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.07f))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                program.title,
                fontSize = 13.sp,
                color = if (isHighlighted) Color.White else Color.White.copy(alpha = 0.85f),
                fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    channel.name,
                    fontSize = 11.sp,
                    color = AppColors.Purple.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 130.dp)
                )
                Text(
                    "${searchTimeFmt.format(program.startTime)}–${searchTimeFmt.format(program.endTime)}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
                if (isLive) {
                    val duration = (program.endTime.epochSecond - program.startTime.epochSecond).coerceAtLeast(1)
                    val elapsed = (now.epochSecond - program.startTime.epochSecond).coerceIn(0, duration)
                    LinearProgressIndicator(
                        progress = { elapsed.toFloat() / duration.toFloat() },
                        modifier = Modifier
                            .width(50.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = AppColors.Purple,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }
        if (isHighlighted) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(AppColors.Purple, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("OK", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
