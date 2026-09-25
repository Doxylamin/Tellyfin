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
        // Of several entries starting at the same moment only the first is drawn — it's the one
        // "what's on now" lookups (first match in list order) will focus.
        if (position > 0 && sorted[position - 1].value.startTime == program.startTime) return@mapIndexedNotNull null
        val nextStartSec = sorted.drop(position + 1)
            .firstOrNull { it.value.startTime > program.startTime }
            ?.value?.startTime?.epochSecond ?: Long.MAX_VALUE
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

/**
 * D-pad Left/Right in the guide: steps to the previous/next programme that is actually drawn, so
 * focus never lands on one that ended before the window or on a hidden duplicate. If focus is on
 * such a programme already, it moves to the nearest drawn one in that direction.
 */
fun guideStepFocus(programs: List<Program>, windowStart: Instant, current: Int, delta: Int): Int {
    val blocks = guideBlocks(programs, windowStart)
    if (blocks.isEmpty()) return current
    val position = blocks.indexOfFirst { it.index == current }
    val target = if (position >= 0) {
        (position + delta).coerceIn(0, blocks.lastIndex)
    } else {
        val reference = programs.getOrNull(current)?.startTime
        when {
            reference == null -> 0
            delta > 0 -> blocks.indexOfFirst { it.program.startTime > reference }.takeIf { it >= 0 } ?: blocks.lastIndex
            else -> blocks.indexOfLast { it.program.startTime < reference }.takeIf { it >= 0 } ?: 0
        }
    }
    return blocks[target].index
}
