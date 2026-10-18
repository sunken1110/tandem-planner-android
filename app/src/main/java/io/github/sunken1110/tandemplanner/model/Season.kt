package io.github.sunken1110.tandemplanner.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * The four seasons used throughout the app.
 *
 * Mar–May: spring, Jun–Aug: summer, Sep–Nov: autumn, Dec–Feb: winter.
 *
 * Each season has three colors (start, peak, end). Days are colored by
 * interpolating between them, so the palette flows gradually across a season.
 */
enum class Season(val label: String, val colors: List<Color>, val greeting: String) {
    SPRING(
        "봄",
        listOf(Color(0xFFC5F27A), Color(0xFF6FD66B), Color(0xFF2EB872)), // sprout → fresh green → emerald
        "새싹이 돋는 봄이 시작됐어요"
    ),
    SUMMER(
        "여름",
        listOf(Color(0xFF9BE7FF), Color(0xFF4FC3F7), Color(0xFF2196F3)), // pale sky → clear sky → deep sky
        "맑은 하늘의 여름이 시작됐어요"
    ),
    AUTUMN(
        "가을",
        listOf(Color(0xFFFFE066), Color(0xFFFFB627), Color(0xFFF57C2B)), // ginkgo → amber → maple
        "단풍이 물드는 가을이 시작됐어요"
    ),
    WINTER(
        "겨울",
        listOf(Color(0xFFE6EBF1), Color(0xFFAEB9C8), Color(0xFFCBD5E1)), // frost → deep silver → clear silver
        "은빛 겨울이 시작됐어요"
    );

    /** Representative color (the peak color). */
    val color: Color get() = colors[1]

    companion object {
        fun of(month: Int): Season = when (month) {
            in 3..5 -> SPRING
            in 6..8 -> SUMMER
            in 9..11 -> AUTUMN
            else -> WINTER
        }
    }
}

/** First day of the season that [date] belongs to. Winter starts in the previous year for Jan/Feb. */
fun seasonStart(date: LocalDate): LocalDate = when (Season.of(date.monthValue)) {
    Season.SPRING -> LocalDate.of(date.year, 3, 1)
    Season.SUMMER -> LocalDate.of(date.year, 6, 1)
    Season.AUTUMN -> LocalDate.of(date.year, 9, 1)
    Season.WINTER -> LocalDate.of(if (date.monthValue == 12) date.year else date.year - 1, 12, 1)
}

/** Last day of the season that [date] belongs to. Handles leap-year Februaries. */
fun seasonEnd(date: LocalDate): LocalDate = when (Season.of(date.monthValue)) {
    Season.SPRING -> LocalDate.of(date.year, 5, 31)
    Season.SUMMER -> LocalDate.of(date.year, 8, 31)
    Season.AUTUMN -> LocalDate.of(date.year, 11, 30)
    Season.WINTER -> YearMonth.of(if (date.monthValue == 12) date.year + 1 else date.year, 2).atEndOfMonth()
}

/** Color for a single day: start → peak → end of its season, interpolated by position. */
fun dayColor(date: LocalDate): Color {
    val c = Season.of(date.monthValue).colors
    val start = seasonStart(date)
    val length = ChronoUnit.DAYS.between(start, seasonEnd(date)).toFloat()
    val t = ChronoUnit.DAYS.between(start, date) / length // 0.0 .. 1.0
    return if (t < 0.5f) lerp(c[0], c[1], t * 2f) else lerp(c[1], c[2], (t - 0.5f) * 2f)
}

/** True on Mar 1, Jun 1, Sep 1 and Dec 1. */
fun isSeasonFirstDay(date: LocalDate): Boolean =
    date.dayOfMonth == 1 && date.monthValue in listOf(3, 6, 9, 12)
