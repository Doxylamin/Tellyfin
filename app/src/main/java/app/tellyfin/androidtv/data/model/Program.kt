package app.tellyfin.androidtv.data.model

import java.time.Instant
import java.util.UUID

data class Program(
    val id: UUID,
    val channelId: UUID,
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val description: String?,
    val genre: String?
) {
    val durationMinutes: Long
        get() = (endTime.epochSecond - startTime.epochSecond) / 60

    fun progressFraction(now: Instant): Float {
        val total = endTime.epochSecond - startTime.epochSecond
        if (total <= 0) return 0f
        val elapsed = now.epochSecond - startTime.epochSecond
        return (elapsed.toFloat() / total).coerceIn(0f, 1f)
    }
}
