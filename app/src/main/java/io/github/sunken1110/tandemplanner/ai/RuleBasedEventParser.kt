package io.github.sunken1110.tandemplanner.ai

import io.github.sunken1110.tandemplanner.data.EventDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Lightweight Korean date/time extraction with regular expressions.
 *
 * Understands, for example:
 * - Dates: "오늘", "내일", "모레", "금요일", "다음 주 화요일", "11월 3일", "15일"
 * - Times: "7시", "저녁 7시", "오후 3시 반", "9시 20분", "18:30", "점심" (no hour → typical time)
 *
 * Matched date/time phrases are removed from the title; a bare "저녁"/"점심"
 * without an hour is kept in the title ("민지랑 저녁") and only hints the time
 * (or AM/PM when an hour is given elsewhere, as in "7시 민지랑 저녁").
 * Anything it can't handle is left to the user in the editor sheet.
 */
object RuleBasedEventParser : EventParser {

    override suspend fun parse(text: String, today: LocalDate): EventDraft = parseNow(text, today)

    private const val PERIOD = "(오전|오후|아침|점심|낮|저녁|밤|새벽)"
    // Longer forms first ("에는" before "에"); 은/는/도 cover "내일은", "금요일도".
    private const val PARTICLE = "(?:에는|에|부터|까지|쯤|날|은|는|도)?"

    private val MonthDay = Regex("""(\d{1,2})\s*월\s*(\d{1,2})\s*일$PARTICLE""")
    // "내일모레" (the day after tomorrow) must come before "내일".
    private val Relative = Regex("""(내일\s*모레|오늘|내일|모레|글피)$PARTICLE""")
    private val Weekday = Regex("""(이번\s*주|다음\s*주|담주)?\s*([월화수목금토일])요일$PARTICLE""")
    // "(?!간)" keeps durations like "3일간 여행" from being read as a date.
    private val DayOnly = Regex("""(?<![\d월])(\d{1,2})\s*일(?!간)$PARTICLE""")

    private val ColonTime = Regex("""$PERIOD?\s*(\d{1,2}):(\d{2})$PARTICLE""")
    private val KoreanTime = Regex("""$PERIOD?\s*(\d{1,2})\s*시(?!간)(?:\s*(\d{1,2})\s*분|\s*(반)(?!대|찬|값|년|팔|복|지))?$PARTICLE""")
    private val PeriodOnly = Regex("""아침|점심|저녁""")

    private val Weekdays = "월화수목금토일"

    /** Synchronous version, handy for tests and live previews. */
    fun parseNow(text: String, today: LocalDate): EventDraft {
        val removed = mutableListOf<IntRange>()

        // ── Date: first matching rule wins ──
        var date = today
        MonthDay.find(text)?.let { m ->
            val parsed = runCatching {
                LocalDate.of(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            }.getOrNull()
            // A month/day already past this year means next year ("1월 5일" typed in October).
            if (parsed != null) { date = if (parsed.isBefore(today)) parsed.plusYears(1) else parsed; removed.add(m.range) }
        } ?: Relative.find(text)?.let { m ->
            val offset = when (m.groupValues[1].replace(" ", "")) {
                "오늘" -> 0L
                "내일" -> 1L
                "모레", "내일모레" -> 2L
                else -> 3L // 글피
            }
            date = today.plusDays(offset)
            removed.add(m.range)
        } ?: Weekday.find(text)?.let { m ->
            val dow = DayOfWeek.of(Weekdays.indexOf(m.groupValues[2]) + 1)
            val monday = today.minusDays(today.dayOfWeek.value - 1L)
            val qualifier = m.groupValues[1].replace(" ", "")
            date = when {
                qualifier.startsWith("다음") || qualifier == "담주" -> monday.plusWeeks(1).plusDays(dow.value - 1L)
                qualifier.startsWith("이번") -> monday.plusDays(dow.value - 1L)
                else -> today.with(TemporalAdjusters.nextOrSame(dow))
            }
            removed.add(m.range)
        } ?: DayOnly.find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            // This month if the day is still ahead, otherwise next month.
            val candidate = runCatching { today.withDayOfMonth(day) }.getOrNull()
            val parsed = if (candidate != null && !candidate.isBefore(today)) candidate
            else runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
            if (parsed != null) { date = parsed; removed.add(m.range) }
        }
        // A weekday next to an explicit date ("11월 3일 금요일 회의") is redundant;
        // drop it from the title. (When the weekday itself set the date, it's already removed.)
        Weekday.find(text)?.let { m ->
            if (m.groupValues[1].isBlank() && removed.none { it.first <= m.range.last && m.range.first <= it.last }) {
                removed.add(m.range)
            }
        }

        // ── Time ──
        var time: LocalTime? = null
        val timeMatch = ColonTime.find(text) ?: KoreanTime.find(text)
        if (timeMatch != null) {
            val g = timeMatch.groupValues
            // No period next to the hour? Borrow a meal word elsewhere ("7시 민지랑 저녁" → 19:00).
            // A borrowed "저녁" only means "afternoon/evening": "12시 민지랑 저녁" stays noon.
            val adjacent = g[1].isNotEmpty()
            val period = if (adjacent) g[1] else when (val meal = PeriodOnly.find(text)?.value) {
                "저녁" -> "오후"
                else -> meal
            }
            val hour = g[2].toInt()
            val minute = when {
                timeMatch.value.contains(':') -> g[3].toInt()
                g[4] == "반" -> 30
                else -> g[3].toIntOrNull() ?: 0
            }
            if (hour in 0..24 && minute in 0..59) {
                val resolved = resolveHour(period, hour)
                time = LocalTime.of(resolved, minute)
                removed.add(timeMatch.range)
                // "밤 1시" / "저녁 12시" are after midnight: the night of the stated day
                // continues into the next calendar day.
                if (adjacent && (period == "밤" || period == "저녁") && resolved < 5) date = date.plusDays(1)
            }
        } else {
            // A meal word without an hour hints a typical time but stays in the title.
            time = when (PeriodOnly.find(text)?.value) {
                "아침" -> LocalTime.of(8, 0)
                "점심" -> LocalTime.of(12, 0)
                "저녁" -> LocalTime.of(19, 0)
                else -> null
            }
        }

        // ── Title: what's left after removing date/time phrases ──
        val title = StringBuilder(text).apply {
            removed.sortedByDescending { it.first }.forEach { delete(it.first, it.last + 1) }
        }.toString()
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '·')
            .ifEmpty { text.trim() }

        return EventDraft(date = date, time = time, title = title)
    }

    /**
     * 24-hour value for a spoken hour.
     * Without a period word, 1–6 are read as afternoon ("3시" → 15:00) since
     * people rarely schedule events at 3 AM.
     */
    private fun resolveHour(period: String?, hour: Int): Int = when (period) {
        "오전", "아침", "새벽" -> if (hour == 12) 0 else hour
        "밤" -> when (hour) {
            12 -> 0
            in 1..4 -> hour          // "밤 1시" is 1 AM
            in 5..11 -> hour + 12
            else -> hour
        }
        "오후" -> if (hour < 12) hour + 12 else hour
        "저녁" -> when (hour) {
            12 -> 0                  // "저녁 12시" is midnight
            in 1..11 -> hour + 12
            else -> hour
        }
        "점심", "낮" -> if (hour in 1..5) hour + 12 else hour
        else -> if (hour in 1..6) hour + 12 else hour
    } % 24
}
