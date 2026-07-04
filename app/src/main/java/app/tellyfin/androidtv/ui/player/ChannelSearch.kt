package app.tellyfin.androidtv.ui.player

import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import java.text.Normalizer
import java.time.Instant

/**
 * Pure search logic over channels and EPG data, kept free of Android
 * dependencies so it can be unit-tested on the JVM.
 */
object ChannelSearch {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /** Case- and diacritic-insensitive ("Küche" matches "kuche", "ß" matches "ss"). */
    fun normalize(s: String): String {
        val lower = s.lowercase().replace("ß", "ss")
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
    }

    fun search(
        query: String,
        channels: List<Channel>,
        epgData: Map<String, List<Program>>,
        now: Instant = Instant.now()
    ): List<SearchResult> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return emptyList()

        // Channels: exact / prefix / word-prefix / substring, best matches first
        val channelMatches = channels.mapNotNull { ch ->
            val name = normalize(ch.name)
            val rank = when {
                name == q || ch.number.toString() == q -> 0
                name.startsWith(q) -> 1
                name.split(' ', '-', '.').any { it.startsWith(q) } -> 2
                name.contains(q) || ch.number.toString().startsWith(q) -> 3
                else -> return@mapNotNull null
            }
            rank to SearchResult.ChannelMatch(ch)
        }.sortedBy { it.first }.map { it.second }.take(10)

        // Programmes: title first, then genre; description only for longer queries.
        // Currently-airing results outrank upcoming ones at the same match quality.
        val channelById = channels.associateBy { it.id.toString() }
        val programMatches = epgData.entries.flatMap { (chId, progs) ->
            val ch = channelById[chId] ?: return@flatMap emptyList<Triple<Int, Program, Channel>>()
            progs.asSequence()
                .filter { it.endTime > now }
                .mapNotNull { p ->
                    val title = normalize(p.title)
                    val rank = when {
                        title.startsWith(q) -> 0
                        title.contains(q) -> 1
                        normalize(p.genre.orEmpty()).contains(q) -> 2
                        q.length >= 4 && normalize(p.description.orEmpty()).contains(q) -> 3
                        else -> return@mapNotNull null
                    }
                    Triple(rank, p, ch)
                }
                .sortedWith(compareBy({ it.first }, { it.second.startTime }))
                .take(3)
                .toList()
        }
            .distinctBy { it.second.id }
            .sortedWith(compareBy(
                { it.first },
                { if (it.second.startTime <= now) 0 else 1 },
                { it.second.startTime }
            ))
            .take(20)
            .map { SearchResult.ProgramMatch(it.second, it.third) }

        return (channelMatches + programMatches).take(30)
    }
}
