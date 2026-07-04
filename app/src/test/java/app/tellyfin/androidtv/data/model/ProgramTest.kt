package app.tellyfin.androidtv.data.model

import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.Test

class ProgramTest {

    private val start: Instant = Instant.parse("2026-07-04T20:00:00Z")

    private fun program(durationMinutes: Long) = Program(
        id = UUID.randomUUID(),
        channelId = UUID.randomUUID(),
        title = "Test",
        startTime = start,
        endTime = start.plusSeconds(durationMinutes * 60),
        description = null,
        genre = null
    )

    @Test
    fun `durationMinutes is derived from start and end`() {
        assertEquals(90, program(90).durationMinutes)
    }

    @Test
    fun `progressFraction is halfway mid-programme`() {
        val p = program(60)
        assertEquals(0.5f, p.progressFraction(start.plusSeconds(30 * 60)))
    }

    @Test
    fun `progressFraction clamps before start and after end`() {
        val p = program(60)
        assertEquals(0f, p.progressFraction(start.minusSeconds(600)))
        assertEquals(1f, p.progressFraction(start.plusSeconds(2 * 60 * 60)))
    }

    @Test
    fun `zero-length programme reports zero progress`() {
        val p = program(0)
        assertEquals(0f, p.progressFraction(start))
    }
}
