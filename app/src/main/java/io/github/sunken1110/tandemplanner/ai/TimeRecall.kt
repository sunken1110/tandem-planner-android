package io.github.sunken1110.tandemplanner.ai

import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.ui.common.parseStoredTime
import java.time.LocalTime

/**
 * A time suggested from past events with a similar title.
 *
 * @property time   the suggested time
 * @property basis  title of the most recent matching event, shown to the user as the reason
 * @property count  how many matching past events used this time
 */
data class TimeSuggestion(val time: LocalTime, val basis: String, val count: Int)

/**
 * Suggests a time by recalling past events with the same or a similar title,
 * e.g. "필라테스" → the time of earlier "필라테스" events.
 *
 * Deliberately conservative — a wrong suggestion costs more trust than a missing one:
 * 1. Exact title match (after normalization): one earlier event is enough.
 * 2. Word overlap ("민지랑 저녁" ~ "민지와 저녁 먹기"): needs at least two matching events.
 * In both cases most of the matches must agree on the same time; if the
 * history is inconsistent (19:00, 14:00, 21:00…) nothing is suggested.
 *
 * Pure Kotlin with no Android dependencies, so it is cheap to run on every
 * (debounced) title change and easy to unit test.
 */
object TimeRecall {

    /** Only the most recent matches count, so changed habits win over old ones. */
    private const val MAX_MATCHES = 10

    /** Share of matches that must agree on the suggested time. */
    private const val MIN_AGREEMENT = 0.6

    /** Minimum Jaccard similarity of word sets for a "similar" title. */
    private const val MIN_OVERLAP = 0.5

    /** Common trailing particles, longest first so "이랑" is stripped before "랑". */
    private val Particles = listOf(
        "이랑", "에서", "하고", "으로", "까지", "부터",
        "랑", "와", "과", "에", "을", "를", "이", "가", "은", "는", "도", "의", "로"
    )

    private val NonWord = Regex("""[^\p{L}\p{N}\s]""")
    private val Spaces = Regex("""\s+""")

    fun suggest(title: String, history: List<Event>): TimeSuggestion? {
        val query = normalize(title)
        if (query.length < 2) return null
        val queryWords = words(query)

        // Newest first; only events that have a real time.
        val timed = history
            .mapNotNull { e -> parseStoredTime(e.time)?.let { e to it } }
            .sortedByDescending { (e, t) -> e.date.atTime(t) }

        val exact = timed.filter { (e, _) -> normalize(e.title) == query }.take(MAX_MATCHES)
        if (exact.isNotEmpty()) return agree(exact, minMatches = 1)

        if (queryWords.isEmpty()) return null
        val similar = timed
            .filter { (e, _) -> overlap(queryWords, words(normalize(e.title))) >= MIN_OVERLAP }
            .take(MAX_MATCHES)
        return agree(similar, minMatches = 2)
    }

    /** The time most matches agree on, or null if there are too few or they disagree. */
    private fun agree(matches: List<Pair<Event, LocalTime>>, minMatches: Int): TimeSuggestion? {
        if (matches.size < minMatches) return null
        val (time, count) = matches.groupingBy { it.second }.eachCount().maxBy { it.value }
        if (count < minMatches || count.toDouble() / matches.size < MIN_AGREEMENT) return null
        // matches are newest first, so this is the latest event that used the time.
        val basis = matches.first { it.second == time }.first.title
        return TimeSuggestion(time, basis, count)
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(NonWord, " ").replace(Spaces, " ").trim()

    /** Words with trailing particles removed: "민지랑 저녁을" → {민지, 저녁}. */
    private fun words(normalized: String): Set<String> =
        normalized.split(' ')
            .map { w -> Particles.firstOrNull { w.length > it.length + 1 && w.endsWith(it) }?.let { w.dropLast(it.length) } ?: w }
            .filter { it.isNotEmpty() }
            .toSet()

    /** Jaccard similarity: shared words / all words. */
    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return (a intersect b).size.toDouble() / (a union b).size
    }
}
