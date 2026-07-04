package app.tellyfin.androidtv.ui.player

import app.tellyfin.androidtv.data.model.Channel
import app.tellyfin.androidtv.data.model.Program
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ChannelSearchTest {

    private val now: Instant = Instant.parse("2026-07-04T20:00:00Z")

    private fun channel(name: String, number: Int) = Channel(
        id = UUID.randomUUID(),
        name = name,
        number = number,
        logoUrl = null,
        currentProgram = null
    )

    private fun program(
        channel: Channel,
        title: String,
        startsInMinutes: Long,
        durationMinutes: Long = 60,
        genre: String? = null,
        description: String? = null
    ) = Program(
        id = UUID.randomUUID(),
        channelId = channel.id,
        title = title,
        startTime = now.plusSeconds(startsInMinutes * 60),
        endTime = now.plusSeconds((startsInMinutes + durationMinutes) * 60),
        description = description,
        genre = genre
    )

    private fun epg(vararg entries: Pair<Channel, List<Program>>): Map<String, List<Program>> =
        entries.associate { (ch, progs) -> ch.id.toString() to progs }

    private fun channelNames(results: List<SearchResult>) =
        results.filterIsInstance<SearchResult.ChannelMatch>().map { it.channel.name }

    private fun programTitles(results: List<SearchResult>) =
        results.filterIsInstance<SearchResult.ProgramMatch>().map { it.program.title }

    // ── Normalization ───────────────────────────────────────────────────────

    @Test
    fun `normalize strips umlauts and sharp s`() {
        assertEquals("kuche", ChannelSearch.normalize("Küche"))
        assertEquals("strasse", ChannelSearch.normalize("Straße"))
        assertEquals("uber", ChannelSearch.normalize("Über"))
        assertEquals("cafe", ChannelSearch.normalize("Café"))
    }

    @Test
    fun `umlaut query matches umlaut-free name and vice versa`() {
        val ch = channel("Küchenschlacht TV", 7)
        val byPlain = ChannelSearch.search("kuchen", listOf(ch), emptyMap(), now)
        val byUmlaut = ChannelSearch.search("KÜCHEN", listOf(ch), emptyMap(), now)
        assertEquals(listOf("Küchenschlacht TV"), channelNames(byPlain))
        assertEquals(listOf("Küchenschlacht TV"), channelNames(byUmlaut))
    }

    // ── Channel ranking ─────────────────────────────────────────────────────

    @Test
    fun `exact channel number outranks name matches`() {
        val channels = listOf(
            channel("Channel 3 HD", 30),
            channel("Drei", 3)
        )
        val results = ChannelSearch.search("3", channels, emptyMap(), now)
        assertEquals("Drei", channelNames(results).first())
    }

    @Test
    fun `prefix match outranks substring match`() {
        val channels = listOf(
            channel("Pro ARD", 10),
            channel("ARD", 1),
            channel("ARD Alpha", 2)
        )
        val results = ChannelSearch.search("ard", channels, emptyMap(), now)
        assertEquals(listOf("ARD", "ARD Alpha", "Pro ARD"), channelNames(results))
    }

    @Test
    fun `non-matching channels are excluded`() {
        val channels = listOf(channel("ZDF", 2), channel("RTL", 3))
        val results = ChannelSearch.search("arte", channels, emptyMap(), now)
        assertTrue(results.isEmpty())
    }

    // ── Programme matching ──────────────────────────────────────────────────

    @Test
    fun `live programme ranks before upcoming at same match quality`() {
        val ch = channel("ZDF", 2)
        val live = program(ch, "Tatort: Köln", startsInMinutes = -30)
        val upcoming = program(ch, "Tatort: Berlin", startsInMinutes = 60)
        val results = ChannelSearch.search("tatort", listOf(ch), epg(ch to listOf(upcoming, live)), now)
        assertEquals(listOf("Tatort: Köln", "Tatort: Berlin"), programTitles(results))
    }

    @Test
    fun `ended programmes are excluded`() {
        val ch = channel("ZDF", 2)
        val ended = program(ch, "Tatort: Alt", startsInMinutes = -120, durationMinutes = 60)
        val results = ChannelSearch.search("tatort", listOf(ch), epg(ch to listOf(ended)), now)
        assertTrue(programTitles(results).isEmpty())
    }

    @Test
    fun `genre match is found`() {
        val ch = channel("ZDF", 2)
        val doc = program(ch, "Unsere Erde", startsInMinutes = 0, genre = "Dokumentation")
        val results = ChannelSearch.search("doku", listOf(ch), epg(ch to listOf(doc)), now)
        assertEquals(listOf("Unsere Erde"), programTitles(results))
    }

    @Test
    fun `description only matches for queries of four or more characters`() {
        val ch = channel("ZDF", 2)
        val prog = program(
            ch, "Abendschau", startsInMinutes = 0,
            description = "Bericht über den Zoo in Berlin"
        )
        val data = epg(ch to listOf(prog))
        assertTrue(programTitles(ChannelSearch.search("zoo", listOf(ch), data, now)).isEmpty())
        assertEquals(
            listOf("Abendschau"),
            programTitles(ChannelSearch.search("berlin", listOf(ch), data, now))
        )
    }

    @Test
    fun `duplicate programme ids are deduplicated`() {
        val ch = channel("ZDF", 2)
        val prog = program(ch, "Tatort", startsInMinutes = 0)
        val results = ChannelSearch.search("tatort", listOf(ch), epg(ch to listOf(prog, prog)), now)
        assertEquals(1, programTitles(results).size)
    }

    // ── General behaviour ───────────────────────────────────────────────────

    @Test
    fun `blank query returns nothing`() {
        val ch = channel("ARD", 1)
        assertTrue(ChannelSearch.search("", listOf(ch), emptyMap(), now).isEmpty())
        assertTrue(ChannelSearch.search("   ", listOf(ch), emptyMap(), now).isEmpty())
    }

    @Test
    fun `channel results come before programme results`() {
        val ch = channel("Tatort TV", 9)
        val prog = program(ch, "Tatort: Köln", startsInMinutes = 0)
        val results = ChannelSearch.search("tatort", listOf(ch), epg(ch to listOf(prog)), now)
        assertTrue(results.first() is SearchResult.ChannelMatch)
        assertTrue(results.last() is SearchResult.ProgramMatch)
    }

    @Test
    fun `results are capped at 30`() {
        val channels = (1..40).map { channel("Kanal $it", it + 100) }
        val results = ChannelSearch.search("kanal", channels, emptyMap(), now)
        assertTrue(results.size <= 30)
    }
}
