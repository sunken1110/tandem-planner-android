package io.github.sunken1110.tandemplanner.ai

import io.github.sunken1110.tandemplanner.data.EventDraft
import io.github.sunken1110.tandemplanner.data.MAX_EVENT_DAYS
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Lightweight Korean date/time extraction with regular expressions.
 *
 * Understands, for example:
 * - Dates: "오늘", "내일", "모레", "금요일", "다음 주 화요일", "11월 3일", "15일"
 * - Times: "7시", "저녁 7시", "오후 3시 반", "9시 20분", "18:30", "점심" (no hour → typical time)
 * - Periods (multi-day, all-day events):
 *   - two dates: "3일부터 5일까지", "11월 30일 ~ 12월 2일", "금요일부터 일요일까지",
 *     "내일부터 모레까지", "12월 24일-26일"
 *   - a compact range: "3~5일", "11월 3-6일"
 *   - a start and a length: "내일부터 3일간", "금요일부터 2박 3일", "11월 3일 4일 동안"
 *     (a length alone, as in "2박3일 제주", starts today)
 *   The period phrases are removed from the title too ("3일부터 5일까지 제주 여행" → "제주 여행").
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
    // "(?!간)" / "(?!\s*동안)" keep durations like "3일간 여행" from being read as a date.
    private val DayOnly = Regex("""(?<![\d월])(\d{1,2})\s*일(?!간)(?!\s*동안)$PARTICLE""")

    // ── Periods ──
    // "11월 3~6일", "3-5일까지": one month (optional), two days, a single "일".
    private val CompactRange = Regex("""(?<![\d월])(?:(\d{1,2})\s*월\s*)?(\d{1,2})\s*[~\-–]\s*(\d{1,2})\s*일(?:까지|간|동안)?""")
    // "2박 3일", "3일간", "4일 동안", "2박": the length of a period.
    private val Length = Regex("""(\d{1,2})\s*박\s*(\d{1,2})\s*일(?:\s*(?:간|동안))?|(\d{1,2})\s*일\s*(?:간|동안)|(\d{1,2})\s*박(?!\s*\d)""")
    // Between the two dates of "A부터 B까지" / "A ~ B": spaces and an optional range sign.
    private val RangeGap = Regex("""\s*([~\-–])?\s*""")

    private val ColonTime = Regex("""$PERIOD?\s*(\d{1,2}):(\d{2})$PARTICLE""")
    private val KoreanTime = Regex("""$PERIOD?\s*(\d{1,2})\s*시(?!간)(?:\s*(\d{1,2})\s*분|\s*(반)(?!대|찬|값|년|팔|복|지))?$PARTICLE""")
    private val PeriodOnly = Regex("""아침|점심|저녁""")

    private val Weekdays = "월화수목금토일"

    /** Synchronous version, handy for tests and live previews. */
    fun parseNow(text: String, today: LocalDate): EventDraft {
        val removed = mutableListOf<IntRange>()

        // ── Period phrases that aren't dates themselves ──
        var endDate: LocalDate? = null
        val compact = CompactRange.find(text)
        val length = Length.find(text)?.also { removed.add(it.range) }
        val lengthDays: Int? = length?.groupValues?.let { g ->
            when {
                g[2].isNotEmpty() -> g[2].toInt()          // "2박 3일" → 3 days
                g[3].isNotEmpty() -> g[3].toInt()          // "3일간"   → 3 days
                else -> g[4].toInt() + 1                   // "2박"     → 3 days
            }
        }
        fun overlapsRemoved(r: IntRange) = removed.any { it.first <= r.last && r.first <= it.last }

        // ── Date: first matching rule wins ──
        var date = today
        // Where the start date was written, to look for an end date right after it.
        var dateMatch: MatchResult? = null
        // "오늘부터 11월 5일까지": the date marked "부터" starts the period, even when a
        // date type that normally wins (here "11월 5일") comes later in the text.
        val markedStart = if (compact == null) explicitStart(text, today, ::overlapsRemoved) else null
        if (compact != null) {
            // "11월 3~6일": the month (or this/next month, as for "15일") and both days.
            val month = compact.groupValues[1].toIntOrNull()
            val from = compact.groupValues[2].toInt()
            val to = compact.groupValues[3].toInt()
            val start = if (month != null) {
                monthDayNearest(today, month, from)
            } else {
                dayThisOrNextMonth(today, from)
            }
            if (start != null) {
                date = start
                endDate = dayOnOrAfter(start, to)
                removed.add(compact.range)
            }
        } else if (markedStart != null) {
            date = markedStart.first
            removed.add(markedStart.second.range)
            dateMatch = markedStart.second
        } else MonthDay.find(text)?.let { m ->
            val parsed = monthDayNearest(today, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            if (parsed != null) { date = parsed; removed.add(m.range); dateMatch = m }
        } ?: Relative.find(text)?.let { m ->
            date = today.plusDays(relativeOffset(m))
            removed.add(m.range)
            dateMatch = m
        } ?: Weekday.find(text)?.let { m ->
            date = weekdayDate(m, today, from = today)
            removed.add(m.range)
            dateMatch = m
        } ?: DayOnly.findAll(text).firstOrNull { !overlapsRemoved(it.range) }?.let { m ->
            // This month if the day is still ahead, otherwise next month.
            val parsed = dayThisOrNextMonth(today, m.groupValues[1].toInt())
            if (parsed != null) { date = parsed; removed.add(m.range); dateMatch = m }
        }

        // "A부터 B까지" / "A ~ B": an end date written right after the start date.
        dateMatch?.let { first ->
            val gap = RangeGap.matchAt(text, first.range.last + 1) ?: return@let
            // Only a real range: the start said "부터", or a range sign sits between.
            if (!first.value.trimEnd().endsWith("부터") && gap.groupValues[1].isEmpty()) return@let
            endOfRange(text, gap.range.last + 1, start = date, today = today)?.let { (end, range) ->
                endDate = end
                removed.add(first.range.first..range.last)
            }
        }
        // "내일부터 3일간": a start and a length.
        if (endDate == null && lengthDays != null && lengthDays > 1) endDate = date.plusDays(lengthDays - 1L)
        // Keep only sensible periods; anything else stays a one-day event.
        endDate = endDate?.takeIf { it.isAfter(date) && !it.isAfter(date.plusDays(MAX_EVENT_DAYS - 1L)) }

        // A weekday next to an explicit date ("11월 3일 금요일 회의") is redundant;
        // drop it from the title. (When the weekday itself set the date, it's already removed.)
        Weekday.find(text)?.let { m ->
            if (m.groupValues[1].isBlank() && removed.none { it.first <= m.range.last && m.range.first <= it.last }) {
                removed.add(m.range)
            }
        }

        // ── Time (none for multi-day events: they are all-day) ──
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
                if (endDate == null && adjacent && (period == "밤" || period == "저녁") && resolved < 5) date = date.plusDays(1)
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
        // Overlapping phrases (a period and the dates inside it) are merged first, so no
        // character is deleted twice.
        val merged = removed.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
            val last = acc.lastOrNull()
            if (last != null && r.first <= last.last + 1) acc[acc.lastIndex] = last.first..maxOf(last.last, r.last)
            else acc.add(r)
            acc
        }
        val title = StringBuilder(text).apply {
            merged.asReversed().forEach { delete(it.first, it.last + 1) }
        }.toString()
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '·')
            .ifEmpty { text.trim() }

        return if (endDate != null) {
            EventDraft(date = date, time = null, title = title, endDate = endDate)
        } else {
            EventDraft(date = date, time = time, title = title)
        }
    }

    /**
     * The first date phrase marked "부터" ("내일부터", "11월 3일부터"), as the start of
     * a period, with its resolved date. Null when there is none.
     */
    private fun explicitStart(
        text: String,
        today: LocalDate,
        taken: (IntRange) -> Boolean
    ): Pair<LocalDate, MatchResult>? {
        val candidates = listOf(MonthDay, Relative, Weekday, DayOnly).flatMap { regex ->
            regex.findAll(text).filter { it.value.trimEnd().endsWith("부터") && !taken(it.range) }.map { regex to it }
        }
        val (regex, m) = candidates.minByOrNull { it.second.range.first } ?: return null
        val date = when (regex) {
            MonthDay -> monthDayNearest(today, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            Relative -> today.plusDays(relativeOffset(m))
            Weekday -> weekdayDate(m, today, from = today)
            else -> dayThisOrNextMonth(today, m.groupValues[1].toInt())
        } ?: return null
        return date to m
    }

    /** Days from today for "오늘", "내일", "모레", "내일모레", "글피". */
    private fun relativeOffset(m: MatchResult): Long = when (m.groupValues[1].replace(" ", "")) {
        "오늘" -> 0L
        "내일" -> 1L
        "모레", "내일모레" -> 2L
        else -> 3L // 글피
    }

    /**
     * The date for a weekday match ("금요일", "다음 주 화요일"). Without "이번/다음 주"
     * it is the first such weekday on or after [from].
     */
    private fun weekdayDate(m: MatchResult, today: LocalDate, from: LocalDate): LocalDate {
        val dow = DayOfWeek.of(Weekdays.indexOf(m.groupValues[2]) + 1)
        val monday = today.minusDays(today.dayOfWeek.value - 1L)
        val qualifier = m.groupValues[1].replace(" ", "")
        return when {
            qualifier.startsWith("다음") || qualifier == "담주" -> monday.plusWeeks(1).plusDays(dow.value - 1L)
            qualifier.startsWith("이번") -> monday.plusDays(dow.value - 1L)
            else -> from.with(TemporalAdjusters.nextOrSame(dow))
        }
    }

    /**
     * A month/day written without a year, in whichever year puts it closest to
     * today (a tie goes to the future):
     * - "10월 28일" typed on Nov 2 → this year (5 days ago, e.g. logging a past event);
     * - "1월 5일" typed in October → next year (3 months ahead, not 9 months ago).
     */
    private fun monthDayNearest(today: LocalDate, month: Int, day: Int): LocalDate? {
        val candidates = listOf(today.year - 1, today.year, today.year + 1)
            .mapNotNull { y -> runCatching { LocalDate.of(y, month, day) }.getOrNull() }
        return candidates.minWithOrNull(
            compareBy<LocalDate>({ kotlin.math.abs(ChronoUnit.DAYS.between(today, it)) }, { if (it.isBefore(today)) 1 else 0 })
        )
    }

    /** Day [day] of this month if it is still ahead, otherwise of next month. */
    private fun dayThisOrNextMonth(today: LocalDate, day: Int): LocalDate? {
        val candidate = runCatching { today.withDayOfMonth(day) }.getOrNull()
        return if (candidate != null && !candidate.isBefore(today)) candidate
        else runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
    }

    /** Day [day] in [start]'s month if that isn't before [start], otherwise in the next month. */
    private fun dayOnOrAfter(start: LocalDate, day: Int): LocalDate? {
        val same = runCatching { start.withDayOfMonth(day) }.getOrNull()
        return if (same != null && !same.isBefore(start)) same
        else runCatching { start.plusMonths(1).withDayOfMonth(day) }.getOrNull()
    }

    /**
     * The end date of a period, written at [index] (right after the start date and
     * any "~"): "5일까지", "12월 2일", "일요일까지", "모레까지". Partial dates are read
     * relative to [start] ("3일부터 5일까지" → the 5th after the 3rd).
     * Returns the date and where it was written.
     */
    private fun endOfRange(text: String, index: Int, start: LocalDate, today: LocalDate): Pair<LocalDate, IntRange>? {
        MonthDay.matchAt(text, index)?.let { m ->
            val parsed = runCatching {
                LocalDate.of(start.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            }.getOrNull() ?: return null
            return (if (parsed.isBefore(start)) parsed.plusYears(1) else parsed) to m.range
        }
        Relative.matchAt(text, index)?.let { m -> return today.plusDays(relativeOffset(m)) to m.range }
        Weekday.matchAt(text, index)?.let { m -> return weekdayDate(m, today, from = start) to m.range }
        DayOnly.matchAt(text, index)?.let { m ->
            return (dayOnOrAfter(start, m.groupValues[1].toInt()) ?: return null) to m.range
        }
        return null
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
