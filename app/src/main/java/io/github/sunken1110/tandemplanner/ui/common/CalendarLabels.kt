package io.github.sunken1110.tandemplanner.ui.common

import java.time.LocalDate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.sunken1110.tandemplanner.ui.theme.OnSeasonColor
import io.github.sunken1110.tandemplanner.ui.theme.SaturdaySky
import io.github.sunken1110.tandemplanner.ui.theme.SundayRed
import java.time.DayOfWeek

/** Column headers for a Sunday-first calendar grid. */
internal val WeekdayHeaders = listOf("일", "월", "화", "수", "목", "금", "토")

/** Short Korean weekday name, e.g. "월" for Monday. */
fun DayOfWeek.koreanShort(): String = WeekdayHeaders[value % 7] // Sunday (7) → index 0

/** "11월 9일 (월)". */
fun LocalDate.koreanDayLabel(): String = "${monthValue}월 ${dayOfMonth}일 (${dayOfWeek.koreanShort()})"

/** Sunday red / Saturday sky for a Sunday-first column (0..6), or null on weekdays. */
private fun columnWeekendColor(column: Int): Color? = when (column) {
    0 -> SundayRed
    6 -> SaturdaySky
    else -> null
}

/** Weekend color for a weekday, or null on Monday–Friday. */
fun DayOfWeek.weekendColor(): Color? = columnWeekendColor(value % 7)

/** Header color for a Sunday-first grid column. */
@Composable
internal fun weekdayHeaderColor(column: Int): Color =
    columnWeekendColor(column) ?: MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Day-number color in a Sunday-first grid.
 * Weekends always keep their color; weekdays switch to dark text on filled cells.
 */
@Composable
fun dayNumberColor(column: Int, isPast: Boolean): Color =
    columnWeekendColor(column) ?: if (isPast) OnSeasonColor else MaterialTheme.colorScheme.onSurface
