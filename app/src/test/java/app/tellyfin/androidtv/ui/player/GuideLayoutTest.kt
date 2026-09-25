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
