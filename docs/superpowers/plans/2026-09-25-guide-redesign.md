# Guide Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the home screen's Live-tab guide and the in-playback guide overlay with one shared, calmer guide (sticky ruler, red only for "now", less past, correct programme states, channel names), and let the overlay show the playing channel in a small video slot.

**Architecture:** Pure layout math (`GuideLayout.kt`, unit tested) feeds shared composables (`GuideViews.kt`: `GuideHero`, `GuideGrid`, `GuideHintBar`). `HomeScreen.LiveEpgContent` and `EpgOverlay` become thin compositions of those. The overlay reports its video slot bounds; `PlayerScreen` resizes the same `VideoPlayer` node into that slot while the overlay is open.

**Tech Stack:** Kotlin 2.2, Jetpack Compose, Media3 `PlayerView` (SurfaceView), JUnit 4 + kotlin-test.

**Spec:** `docs/superpowers/specs/2026-09-25-guide-redesign-design.md`

## Global Constraints

- Red (`AppColors.Red`) only for the now-line, the now pill and the LIVE badge / hero live progress bar.
- Block colours: upcoming `#1B1B2B`, live `#26263C` + white 55% progress stripe, ended `#141420`, focused purple gradient + `#9D7BFF` outline.
- Window: starts at the current half hour rounded down, 300 min long, 4 dp/min.
- Key handling of both guides is unchanged.
- Strings in both `values/` and `values-de/`.
- Commit messages: plain imperative sentence, no prefix, no `Co-Authored-By` / AI trailer.
- Unit tests: `./gradlew testSentryDirectDebugUnitTest --tests '<pattern>' -q`.

## Review Focus

- Programmes with identical start times, unsorted input or ones running past the window edges must never render overlapping or zero-width blocks (Task 1, tests `overlapping programmes are clipped to the next start`, `unsorted input is sorted but keeps original indices`, `same-start duplicates collapse to one block`).
- A programme ending exactly now must read "vorbei", not "noch 0 min" (Task 1, test `a programme ending right now has ended`).
- Opening the guide from the home screen (nothing playing) must not cut a hole showing the home screen through the overlay (Task 4: `showVideoSlot = state.isPlaying`; verified on device).
- Closing the overlay must return the video to full screen immediately, not after the fade (Task 4: slot gated on `state.overlay is Overlay.Epg`).
- Horizontal auto-scroll to the focused block must use pixels, not dp values fed to a pixel scroll state (Task 2, `GuideGrid` converts via `LocalDensity`).

---

### Task 1: Guide layout math

**Files:**
- Create: `app/src/main/java/app/tellyfin/androidtv/ui/player/GuideLayout.kt`
- Test: `app/src/test/java/app/tellyfin/androidtv/ui/player/GuideLayoutTest.kt`

**Interfaces:**
- Produces: `GUIDE_PX_PER_MIN: Float` (4f), `GUIDE_WINDOW_MINUTES: Int` (300), `guideWindowStart(now: Instant): Instant`, `data class GuideTick(val offsetMin: Int, val time: Instant)`, `guideTicks(windowStart: Instant): List<GuideTick>`, `guideMinutesSince(windowStart: Instant, now: Instant): Float`, `data class GuideBlock(val program: Program, val index: Int, val startMin: Float, val endMin: Float)`, `guideBlocks(programs: List<Program>, windowStart: Instant, windowMinutes: Int = GUIDE_WINDOW_MINUTES): List<GuideBlock>`, `sealed interface ProgramTiming { Live(remainingMin: Int, progress: Float); Upcoming(inMin: Int); Ended }`, `programTiming(program: Program, now: Instant): ProgramTiming`, `guideHeroProgram(programs: List<Program>, focusedIndex: Int?, now: Instant): Program?`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.tellyfin.androidtv.ui.player

import app.tellyfin.androidtv.data.model.Program
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class GuideLayoutTest {

    private val base: Instant = Instant.parse("2026-09-24T12:00:00Z")
    private fun at(min: Long): Instant = base.plusSeconds(min * 60)
    private val channelId = UUID.randomUUID()
    private fun program(title: String, from: Long, to: Long) = Program(
        id = UUID.randomUUID(),
        channelId = channelId,
        title = title,
        startTime = at(from),
        endTime = at(to),
        description = null,
        genre = null
    )

    @Test
    fun `the window starts at the current half hour`() {
        assertEquals(at(0), guideWindowStart(at(24)))
        assertEquals(at(30), guideWindowStart(at(30)))
        assertEquals(at(30), guideWindowStart(at(59)))
    }

    @Test
    fun `ticks every half hour across the whole window`() {
        val ticks = guideTicks(at(0))
        assertEquals(11, ticks.size)
        assertEquals(GuideTick(0, at(0)), ticks.first())
        assertEquals(GuideTick(300, at(300)), ticks.last())
    }

    @Test
    fun `minutes since the window start`() {
        assertEquals(24.5f, guideMinutesSince(at(0), base.plusSeconds(24 * 60 + 30)))
    }

    @Test
    fun `blocks are clipped to the window`() {
        val blocks = guideBlocks(listOf(program("a", -40, 20), program("b", 20, 400), program("c", 400, 460)), at(0))
        assertEquals(listOf("a" to (0f to 20f), "b" to (20f to 300f)), blocks.map { it.program.title to (it.startMin to it.endMin) })
    }

    @Test
    fun `overlapping programmes are clipped to the next start`() {
        val blocks = guideBlocks(listOf(program("a", 10, 40), program("b", 25, 60)), at(0))
        assertEquals(listOf(10f to 25f, 25f to 60f), blocks.map { it.startMin to it.endMin })
    }

    @Test
    fun `unsorted input is sorted but keeps original indices`() {
        val blocks = guideBlocks(listOf(program("late", 30, 60), program("early", 0, 30)), at(0))
        assertEquals(listOf("early" to 1, "late" to 0), blocks.map { it.program.title to it.index })
    }

    @Test
    fun `same-start duplicates collapse to one block`() {
        val blocks = guideBlocks(listOf(program("a", 10, 40), program("a again", 10, 40)), at(0))
        assertEquals(1, blocks.size)
    }

    @Test
    fun `zero-length programmes are dropped`() {
        assertEquals(emptyList(), guideBlocks(listOf(program("blip", 10, 10)), at(0)))
    }

    @Test
    fun `live programmes report minutes left rounded up and progress`() {
        val timing = programTiming(program("a", 0, 60), base.plusSeconds(15 * 60 + 30))
        assertEquals(ProgramTiming.Live(remainingMin = 45, progress = (15 * 60 + 30) / 3600f), timing)
    }

    @Test
    fun `upcoming programmes report minutes until start rounded up`() {
        assertEquals(ProgramTiming.Upcoming(inMin = 20), programTiming(program("a", 30, 60), base.plusSeconds(10 * 60 + 5)))
    }

    @Test
    fun `a programme ending right now has ended`() {
        assertEquals(ProgramTiming.Ended, programTiming(program("a", 0, 60), at(60)))
    }

    @Test
    fun `hero shows the focused programme while it hasn't ended`() {
        val programs = listOf(program("past", 0, 30), program("now", 30, 60), program("next", 60, 90))
        assertEquals("next", guideHeroProgram(programs, focusedIndex = 2, now = at(40))?.title)
    }

    @Test
    fun `hero falls back from an ended focus to what is on now, then what is next`() {
        val programs = listOf(program("past", 0, 30), program("now", 30, 60), program("next", 60, 90))
        assertEquals("now", guideHeroProgram(programs, focusedIndex = 0, now = at(40))?.title)
        assertEquals("now", guideHeroProgram(programs, focusedIndex = null, now = at(40))?.title)
        val gap = listOf(program("past", 0, 30), program("later", 60, 90))
        assertEquals("later", guideHeroProgram(gap, focusedIndex = null, now = at(40))?.title)
        assertNull(guideHeroProgram(emptyList(), focusedIndex = null, now = at(40)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testSentryDirectDebugUnitTest --tests '*GuideLayoutTest' -q`
Expected: compile failure, `Unresolved reference 'guideWindowStart'`.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.tellyfin.androidtv.ui.player

import app.tellyfin.androidtv.data.model.Program
import java.time.Instant

// Pure layout math shared by the home screen's Live tab and the in-playback guide overlay.
// GuideViews.kt only draws what these functions compute.

const val GUIDE_PX_PER_MIN = 4f          // dp per minute: 1 h = 240 dp
const val GUIDE_WINDOW_MINUTES = 300     // 5 h
private const val HALF_HOUR_SEC = 1_800L

/** The current half hour, rounded down — so at most 30 min of the past is ever shown. */
fun guideWindowStart(now: Instant): Instant =
    Instant.ofEpochSecond(now.epochSecond / HALF_HOUR_SEC * HALF_HOUR_SEC)

data class GuideTick(val offsetMin: Int, val time: Instant)

fun guideTicks(windowStart: Instant): List<GuideTick> =
    (0..GUIDE_WINDOW_MINUTES step 30).map { GuideTick(it, windowStart.plusSeconds(it * 60L)) }

fun guideMinutesSince(windowStart: Instant, now: Instant): Float =
    (now.epochSecond - windowStart.epochSecond) / 60f

/** [index] points into the list passed to [guideBlocks] — the same index focus state uses. */
data class GuideBlock(val program: Program, val index: Int, val startMin: Float, val endMin: Float)

/**
 * One block per programme that is visible in the window. Some EPG feeds hand back overlapping or
 * duplicate entries; each block is clipped to where the next one starts, so titles never render
 * on top of each other, and anything left with no width is dropped.
 */
fun guideBlocks(
    programs: List<Program>,
    windowStart: Instant,
    windowMinutes: Int = GUIDE_WINDOW_MINUTES
): List<GuideBlock> {
    val windowStartSec = windowStart.epochSecond
    val windowEndSec = windowStartSec + windowMinutes * 60L
    val sorted = programs.withIndex().sortedBy { it.value.startTime }
    return sorted.mapIndexedNotNull { position, (index, program) ->
        val nextStartSec = sorted.getOrNull(position + 1)?.value?.startTime?.epochSecond ?: Long.MAX_VALUE
        val startSec = maxOf(program.startTime.epochSecond, windowStartSec)
        val endSec = minOf(program.endTime.epochSecond, windowEndSec, nextStartSec)
        if (endSec <= startSec) null
        else GuideBlock(program, index, (startSec - windowStartSec) / 60f, (endSec - windowStartSec) / 60f)
    }
}

sealed interface ProgramTiming {
    data class Live(val remainingMin: Int, val progress: Float) : ProgramTiming
    data class Upcoming(val inMin: Int) : ProgramTiming
    data object Ended : ProgramTiming
}

fun programTiming(program: Program, now: Instant): ProgramTiming {
    val start = program.startTime.epochSecond
    val end = program.endTime.epochSecond
    val nowSec = now.epochSecond
    return when {
        nowSec >= end -> ProgramTiming.Ended
        nowSec < start -> ProgramTiming.Upcoming(ceilMinutes(start - nowSec))
        else -> ProgramTiming.Live(
            remainingMin = ceilMinutes(end - nowSec),
            progress = (nowSec - start).toFloat() / (end - start).coerceAtLeast(1)
        )
    }
}

private fun ceilMinutes(seconds: Long): Int = ((seconds + 59) / 60).toInt()

/**
 * What the hero shows: the focused programme while it hasn't ended — otherwise it would freeze on
 * a stale block once time moves past it — then whatever is on now, then the next one.
 */
fun guideHeroProgram(programs: List<Program>, focusedIndex: Int?, now: Instant): Program? =
    focusedIndex?.let { programs.getOrNull(it) }?.takeIf { it.endTime > now }
        ?: programs.firstOrNull { it.startTime <= now && it.endTime > now }
        ?: programs.firstOrNull { it.startTime > now }
        ?: programs.firstOrNull()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testSentryDirectDebugUnitTest --tests '*GuideLayoutTest' -q`
Expected: PASS.

Note: the last assertion group expects `null` only for an empty list; `guideHeroProgram(gap…)` returns "later".

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tellyfin/androidtv/ui/player/GuideLayout.kt app/src/test/java/app/tellyfin/androidtv/ui/player/GuideLayoutTest.kt
git commit -m "Add shared layout math for the programme guide"
```

---

### Task 2: Shared guide views and strings

**Files:**
- Create: `app/src/main/java/app/tellyfin/androidtv/ui/player/GuideViews.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`

**Interfaces:**
- Consumes: Task 1.
- Produces: `GuideHero(channel: Channel?, program: Program?, now: Instant, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null)`, `GuideGrid(channels: List<Channel>, epgData: Map<String, List<Program>>, now: Instant, highlightedChannelId: UUID?, isFocused: Boolean, modifier: Modifier = Modifier, focusedBlockIndex: Int? = null, currentChannelId: UUID? = null)`, `GuideHintBar(text: String, modifier: Modifier = Modifier)`; strings `guide_remaining`, `guide_starts_in`, `guide_ended`, `guide_home_hint`.

No unit tests (Compose UI); verified by compiling here and on device via Task 5.

- [ ] **Step 1: Strings.** In `values/strings.xml` add after `epg_on_now`:

```xml
    <string name="guide_remaining">%d min left</string>
    <string name="guide_starts_in">in %d min</string>
    <string name="guide_ended">ended</string>
    <string name="guide_home_hint">OK Watch  ·  MENU Options  ·  ← → Time  ·  ↑↓ Channels</string>
```

and in `values-de/strings.xml` after `epg_on_now`:

```xml
    <string name="guide_remaining">noch %d min</string>
    <string name="guide_starts_in">in %d min</string>
    <string name="guide_ended">vorbei</string>
    <string name="guide_home_hint">OK Ansehen  ·  MENU Optionen  ·  ← → Zeit  ·  ↑↓ Kanäle</string>
```

- [ ] **Step 2: Create `GuideViews.kt`:**

```kotlin
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
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleSentryDirectDebug -q`
Expected: build succeeds (the new composables are unused until Tasks 3–4).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tellyfin/androidtv/ui/player/GuideViews.kt app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml
git commit -m "Add shared guide hero, grid and hint bar"
```

---

### Task 3: Home Live tab on the shared guide

**Files:**
- Modify: `app/src/main/java/app/tellyfin/androidtv/ui/player/HomeScreen.kt`

**Interfaces:**
- Consumes: `GuideHero`, `GuideGrid`, `GuideHintBar` (Task 2), `guideHeroProgram` (Task 1), string `guide_home_hint`.

- [ ] **Step 1: Replace `LiveEpgContent`'s body** (everything from `val highlightedChannelId = …` to the end of its `Column { … }`) with:

```kotlin
    val focusedChannel = channels.getOrNull(highlightedIndex)
    val focusedProgram = focusedChannel?.let {
        guideHeroProgram(epgData[it.id.toString()].orEmpty(), epgFocusedBlockIndex, now)
    }

    Column(modifier = modifier) {
        GuideHero(
            channel = focusedChannel,
            program = focusedProgram,
            now = now,
            modifier = Modifier.fillMaxWidth().height(112.dp)
        )
        GuideGrid(
            channels = sortedChannels,
            epgData = epgData,
            now = now,
            highlightedChannelId = focusedChannel?.id,
            isFocused = homeFocusSection == HOME_SECTION_EPG,
            modifier = Modifier.weight(1f),
            focusedBlockIndex = epgFocusedBlockIndex
        )
        GuideHintBar(stringResource(R.string.guide_home_hint))
    }
```

- [ ] **Step 2: Delete the old guide code** in `HomeScreen.kt`: the functions `EpgHeroSection`, `MetadataBadge`, `HomeEpgGrid`, `EpgChannelCell`, `HomeEpgRow` (from `@Composable\nprivate fun EpgHeroSection` to the end of `HomeEpgRow`), the constants `EPG_PX_PER_MIN`, `EPG_WINDOW_MINUTES`, `EPG_ROW_HEIGHT`, `EPG_RULER_HEIGHT`, `EPG_CHANNEL_COL_WIDTH` with their comment, and `dateFmt` if nothing else uses it (check with `grep -n dateFmt HomeScreen.kt`).

- [ ] **Step 3: Remove the now-unused strings** `epg_ok_hint` and `epg_on_now` from both `strings.xml` files, after confirming no references: `grep -rn "epg_ok_hint\|epg_on_now" app/src/main/java` → no output.

- [ ] **Step 4: Build and test**

Run: `./gradlew assembleSentryDirectDebug testSentryDirectDebugUnitTest lintSentryDirectDebug -q`
Expected: succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tellyfin/androidtv/ui/player/HomeScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml
git commit -m "Move the home screen's Live tab onto the shared guide"
```

---

### Task 4: Overlay on the shared guide, with the video slot

**Files:**
- Rewrite: `app/src/main/java/app/tellyfin/androidtv/ui/player/EpgOverlay.kt`
- Modify: `app/src/main/java/app/tellyfin/androidtv/ui/player/PlayerScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml` (`epg_hint`, remove `guide`)

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: `EpgOverlay(channels, epgData, currentChannelIndex, highlightedRow, visible, showVideoSlot: Boolean, onVideoSlotChanged: (Rect?) -> Unit, modifier)`.

- [ ] **Step 1: Replace the whole of `EpgOverlay.kt`** with:

```kotlin
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
    val heroProgram = highlightedChannel?.let { guideHeroProgram(epgData[it.id.toString()].orEmpty(), null, now) }
    var slot by remember { mutableStateOf<Rect?>(null) }

    // Fades rather than slides: the slot must not move while the video is sized into it.
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        DisposableEffect(Unit) {
            onDispose {
                slot = null
                onVideoSlotChanged(null)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val s = slot
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
                currentChannelId = channels.getOrNull(currentChannelIndex)?.id
            )
            GuideHintBar(stringResource(R.string.epg_hint))
        }
    }
}
```

- [ ] **Step 2: Update `PlayerScreen.kt`.**

Add imports:

```kotlin
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
```

After `val state by viewModel.uiState.collectAsState()` add:

```kotlin
    // Where the guide overlay wants the playing video (null = full screen).
    var videoSlot by remember { mutableStateOf<Rect?>(null) }
```

Replace `VideoPlayer(viewModel = viewModel)` with:

```kotlin
                // Same composable node either way, so the player view is resized, never re-attached.
                VideoPlayer(viewModel = viewModel, slot = if (state.overlay is Overlay.Epg) videoSlot else null)
```

Replace the `EpgOverlay(...)` call with:

```kotlin
        EpgOverlay(
            channels = state.channels,
            epgData = state.epgData,
            currentChannelIndex = state.currentIndex,
            highlightedRow = state.highlightedIndex,
            visible = state.overlay is Overlay.Epg,
            showVideoSlot = state.isPlaying,
            onVideoSlotChanged = { videoSlot = it }
        )
```

Replace the `VideoPlayer` function's signature and `AndroidView` modifier:

```kotlin
@Composable
private fun VideoPlayer(viewModel: PlayerViewModel, slot: Rect?) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val placement = if (slot == null) Modifier.fillMaxSize() else with(density) {
        Modifier
            .offset { IntOffset(slot.left.roundToInt(), slot.top.roundToInt()) }
            .size(slot.width.toDp(), slot.height.toDp())
    }
    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = viewModel.exoPlayer
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
            }
        },
        modifier = placement
    )
```

(keep the `DisposableEffect` for `keepScreenOn` unchanged).

- [ ] **Step 3: Strings.** In `values/strings.xml` change `epg_hint` to `↑↓ Channels  ·  OK Watch  ·  BACK Close` and delete `<string name="guide">…</string>`; in `values-de/strings.xml` change `epg_hint` to `↑↓ Kanäle  ·  OK Ansehen  ·  ZURÜCK Schließen` and delete `guide`. Confirm first: `grep -rn "R.string.guide\b" app/src/main/java` → no output.

- [ ] **Step 4: Build every variant, test, lint**

Run: `./gradlew assembleSentryDirectDebug assembleNoSentryDirectDebug assembleSentryStoreDebug testSentryDirectDebugUnitTest lintSentryDirectDebug -q`
Expected: succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tellyfin/androidtv/ui/player/EpgOverlay.kt app/src/main/java/app/tellyfin/androidtv/ui/player/PlayerScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml
git commit -m "Rebuild the Live guide overlay on the shared guide with a video preview"
```

---

### Task 5: Beta build

- [ ] **Step 1:** Build a debug-key-signed beta (never push a `v*` tag):

```bash
KEYSTORE_PATH=$HOME/.android/debug.keystore KEYSTORE_PASSWORD=android KEY_ALIAS=androiddebugkey KEY_PASSWORD=android \
  ./gradlew assembleSentryDirectRelease -PversionCode=51 -PversionName=2.0.0-beta.8 -q
```

Expected: `aapt2 dump badging` shows `versionCode='51' versionName='2.0.0-beta.8'`. Upload only if the user asks.
